package net.zamin.engine.interaction;

import net.zamin.api.BlockPosition;
import net.zamin.api.BlockType;
import net.zamin.api.ItemType;
import net.zamin.engine.EngineTicker;
import net.zamin.engine.block.BlockBehavior;
import net.zamin.engine.block.BlockBehaviorTable;
import net.zamin.engine.config.GameMode;
import net.zamin.engine.player.PlayerSession;
import net.zamin.engine.world.EngineWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The single semantic entry point for player-driven block changes (§208/§215/§216).
 *
 * <p>Flow: intent (from a protocol adapter) -&gt; tick-thread task -&gt; validate -&gt;
 * commit -&gt; publish. All validation and mutation runs on the world's owning
 * thread, so no torn reads are possible and ordering matches the simulation.
 * Interaction latency is therefore at most one tick, matching tick-based
 * historical behavior (§214).</p>
 *
 * <p>Modes (§415 behavior first): creative interaction is instant and
 * inventory-free. Survival mining is server-authoritative: the client proposes
 * start/abort/finish, the server validates reach, diggability and elapsed time
 * before committing; too-fast finishes are rejected and re-synced so clients
 * never keep ghost blocks.</p>
 */
public final class BlockInteractionService {

    private static final Logger LOGGER = Logger.getLogger(BlockInteractionService.class.getName());

    /**
     * Historical leniency factor for finish timing: network jitter and tick
     * quantization make exact-duration equality wrong; a finished dig may
     * complete at ~70% of the nominal duration without being a cheat.
     */
    static final double MINING_TIMING_LENIENCY = 0.7;

    /** Nominal length of one simulation tick in nanoseconds (from the ticker's rate). */
    private static final long TICK_NANOS = 50_000_000L;

    private final EngineWorld world;
    private final EngineTicker ticker;
    private final Consumer<BlockChange> publisher;
    private final GameMode gameMode;

    /** Active survival mining sessions, keyed by player. Tick-thread confined. */
    private final Map<UUID, MiningSession> miningSessions = new HashMap<>();

    /** A committed block change, published to observers (adapter sync). */
    public record BlockChange(BlockPosition position, BlockType newType) {
    }

    /** One in-progress survival dig: target and server-received start time. */
    private record MiningSession(BlockPosition target, long startedNanos) {
    }

    public BlockInteractionService(EngineWorld world, EngineTicker ticker,
                                   Consumer<BlockChange> publisher, GameMode gameMode) {
        this.world = Objects.requireNonNull(world, "world");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
    }

    /** Requests an instant (creative) break. Safe from any thread. */
    public void submitCreativeBreak(PlayerSession player, BlockPosition position) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(position, "position");
        ticker.submit(() -> creativeBreakOnTick(player, position));
    }

    /** Reports the start of a survival dig. Safe from any thread. */
    public void submitMiningStart(PlayerSession player, BlockPosition target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        ticker.submit(() -> miningStartOnTick(player, target));
    }

    /** Reports an aborted survival dig (client moved away or released). Safe from any thread. */
    public void submitMiningAborted(PlayerSession player) {
        Objects.requireNonNull(player, "player");
        ticker.submit(() -> miningSessions.remove(player.uuid()));
    }

    /** Reports the completion of a survival dig. Safe from any thread. */
    public void submitMiningFinished(PlayerSession player, BlockPosition target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        ticker.submit(() -> miningFinishOnTick(player, target));
    }

    /**
     * Requests placement against the clicked block and face, using the item the
     * client claims to hold (validated against the registry). Safe from any thread.
     */
    public void submitPlace(PlayerSession player, BlockPosition clicked, int face, BlockType held) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(clicked, "clicked");
        Objects.requireNonNull(held, "held");
        if (held.equals(world.airType())) {
            return; // placing "nothing" is a no-op, not an error
        }
        ticker.submit(() -> placeOnTick(player, clicked, face, held));
    }

    // ---- tick-thread execution ---------------------------------------------

    private void creativeBreakOnTick(PlayerSession player, BlockPosition position) {
        BlockType current = world.getBlock(position);
        if (current.equals(world.airType())) {
            return; // nothing to break: no-op, no error
        }
        if (!InteractionRules.withinReach(player.position(), position)) {
            LOGGER.fine(() -> "Rejected break (out of reach) by " + player.name());
            return;
        }
        commit(position, world.airType());
    }

    private void miningStartOnTick(PlayerSession player, BlockPosition target) {
        BlockBehavior behavior = BlockBehaviorTable.of(world.getBlock(target).identifier()).orElse(null);
        if (behavior == null || !behavior.diggable() || behavior.hardness() < 0) {
            return; // air or unbreakable: no session (bedrock never opens one)
        }
        if (!InteractionRules.withinSurvivalReach(player.position(), target)) {
            LOGGER.fine(() -> "Rejected mining start (out of reach) by " + player.name());
            return;
        }
        // (Re)starts the dig; repeated starts restart progress, matching lenient history.
        miningSessions.put(player.uuid(), new MiningSession(target, System.nanoTime()));
    }

    private void miningFinishOnTick(PlayerSession player, BlockPosition target) {
        MiningSession session = miningSessions.remove(player.uuid());
        BlockType current = world.getBlock(target);
        if (current.equals(world.airType())) {
            return; // already gone (race or double finish): no-op
        }
        if (session == null || !session.target().equals(target)
                || !InteractionRules.withinSurvivalReach(player.position(), target)) {
            resync(target);
            LOGGER.fine(() -> "Rejected mining finish (no session/wrong target/out of reach) by "
                    + player.name());
            return;
        }
        BlockBehavior behavior = BlockBehaviorTable.of(current.identifier()).orElse(null);
        if (behavior == null || !behavior.diggable()) {
            resync(target);
            return;
        }
        long elapsedNanos = System.nanoTime() - session.startedNanos();
        boolean canHarvest = BlockBehaviorTable.canHarvest(behavior, heldItemOf(player));
        int requiredTicks = behavior.breakTicks(canHarvest);
        long minimumNanos = (long) (requiredTicks * TICK_NANOS * MINING_TIMING_LENIENCY);
        if (elapsedNanos < minimumNanos) {
            resync(target); // too fast: undo the client's local prediction (§441 spirit)
            LOGGER.fine(() -> "Rejected mining finish (too fast: " + (elapsedNanos / 1_000_000)
                    + "ms < " + (minimumNanos / 1_000_000) + "ms) by " + player.name());
            return;
        }
        commit(target, world.airType());
    }

    private void placeOnTick(PlayerSession player, BlockPosition clicked, int face, BlockType held) {
        BlockPosition target = InteractionRules.offsetByFace(clicked, face);
        if (target == null) {
            return; // invalid face value
        }
        if (world.getBlock(clicked).equals(world.airType())) {
            return; // must click an existing block face (§215)
        }
        if (!world.getBlock(target).equals(world.airType())) {
            return; // target must be air
        }
        if (!InteractionRules.withinReach(player.position(), target)) {
            LOGGER.fine(() -> "Rejected placement (out of reach) by " + player.name());
            return;
        }
        if (InteractionRules.intersectsPlayer(player.position(), target)) {
            LOGGER.fine(() -> "Rejected placement (inside player) by " + player.name());
            return;
        }
        commit(target, held);
    }

    private void commit(BlockPosition position, BlockType type) {
        world.setBlock(position, type);
        publisher.accept(new BlockChange(position, type));
    }

    /** Re-publishes the authoritative current state so clients drop predictions. */
    private void resync(BlockPosition position) {
        publisher.accept(new BlockChange(position, world.getBlock(position)));
    }

    /**
     * The item the player currently holds. The inventory model lands with the
     * drops slice; until then the engine reads null (bare hand) directly.
     */
    private ItemType heldItemOf(PlayerSession player) {
        return null;
    }
}
