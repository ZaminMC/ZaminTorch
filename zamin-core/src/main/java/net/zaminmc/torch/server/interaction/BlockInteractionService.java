package net.zaminmc.torch.server.interaction;

import net.zaminmc.torch.util.Position;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineTicker;
import net.zaminmc.torch.server.block.BlockBehavior;
import net.zaminmc.torch.server.block.BlockBehaviorTable;
import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.HashMap;
import java.util.List;
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

    // The anti-cheat budgets (the NCP shapes, server-lag simplified to
    // server-side clocks): the rolling break window and the fastplace band.
    static final long BREAK_WINDOW_NANOS = 1_000_000_000L; // 20 ticks
    static final int SURVIVAL_BREAK_BUDGET = 25;  // finishes per window (NCP 45/s with headroom)
    static final int CREATIVE_BREAK_BUDGET = 45;  // the historical creative pace
    static final int NUKER_KICK_VIOLATIONS = 10;
    static final long PLACE_WINDOW_NANOS = 500_000_000L; // 10 ticks
    static final int PLACE_BUDGET = 6;            // places per 10 ticks (NCP FastPlace)
    static final int FASTPLACE_KICK_VIOLATIONS = 10;

    /** Nominal length of one simulation tick in nanoseconds (from the ticker's rate). */
    private static final long TICK_NANOS = 50_000_000L;

    private final EngineWorld world;
    private final EngineTicker ticker;
    private final Consumer<BlockChange> publisher;
    /** Per-player game mode resolver: the session's own mode decides the rule. */
    private final java.util.function.Function<PlayerSession, GameMode> gameMode;
    private final DropService dropService;
    private final ItemEntityManager itemEntities;
    private final java.util.function.Function<net.zaminmc.torch.item.ItemType,
            java.util.Optional<BlockType>> blockItemResolver;
    private final java.util.function.Consumer<PlayerSession> inventorySync;
    /** Post-break hook (furnace spill, future block entities). Tick-thread context. */
    private volatile java.util.function.BiConsumer<BlockPosition, net.zaminmc.torch.block.BlockType> blockBrokenListener;
    /** The survival-break XP hook (mining awards; creative breaks never fire it). */
    private volatile SurvivalXpHook survivalXpListener;
    /** The world-mutation feedback hook (break/place FX); null until registered. */
    private volatile java.util.function.Consumer<Commit> commitFeedbackListener;

    /** Active survival mining sessions, keyed by player. Tick-thread confined. */
    private final Map<UUID, MiningSession> miningSessions = new HashMap<>();

    /** A committed block change, published to observers (adapter sync). */
    public record BlockChange(BlockPosition position, BlockType newType) {
    }

    /** One in-progress survival dig: target and server-received start time. */
    private record MiningSession(BlockPosition target, long startedNanos) {
    }

    public BlockInteractionService(EngineWorld world, EngineTicker ticker,
                                   Consumer<BlockChange> publisher, java.util.function.Function<PlayerSession, GameMode> gameMode,
                                   DropService dropService, ItemEntityManager itemEntities,
                                   java.util.function.Function<net.zaminmc.torch.item.ItemType,
                                           java.util.Optional<BlockType>> blockItemResolver,
                                   java.util.function.Consumer<PlayerSession> inventorySync) {
        this.world = Objects.requireNonNull(world, "world");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
        this.dropService = Objects.requireNonNull(dropService, "dropService");
        this.itemEntities = Objects.requireNonNull(itemEntities, "itemEntities");
        this.blockItemResolver = Objects.requireNonNull(blockItemResolver, "blockItemResolver");
        this.inventorySync = Objects.requireNonNull(inventorySync, "inventorySync");
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
     * Requests placement from the player's authoritative inventory (§430): the
     * held stack must be a registered block item; one unit is consumed
     * atomically with the commit (§431/§432). Safe from any thread.
     */
    public void submitSurvivalPlace(PlayerSession player, BlockPosition clicked, int face) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(clicked, "clicked");
        ticker.submit(() -> survivalPlaceOnTick(player, clicked, face));
    }

    /**
     * Requests placement against the clicked block and face, using the item the
     * client claims to hold (validated against the registry). Creative path.
     * Safe from any thread.
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
        // The nuker guard runs creative too (the higher creative budget).
        long nowNanos = System.nanoTime();
        if (!player.violations().recordBreak(nowNanos, BREAK_WINDOW_NANOS, CREATIVE_BREAK_BUDGET)
                || player.violations().isMultiBreak(nowNanos, position)) {
            player.violations().addNukerViolation();
            resync(position);
            LOGGER.warning(() -> "Nuker violation (creative) for " + player.name()
                    + " (vl " + player.violations().nukerViolations() + ")");
            if (player.violations().nukerViolations() >= NUKER_KICK_VIOLATIONS) {
                player.link().kick("Nuker");
            }
            return;
        }
        player.violations().noteBreakTarget(nowNanos, position);
        BlockType previous = world.getBlock(position);
        commit(position, world.airType());
        // The break hook fires in creative too: door halves die as a unit and
        // container contents spill (the historical creative-break behavior).
        var listener = blockBrokenListener;
        if (listener != null) {
            listener.accept(position, previous);
        }
    }

    private void miningStartOnTick(PlayerSession player, BlockPosition target) {
        BlockBehavior behavior = BlockBehaviorTable.of(world.getBlock(target).identifier()).orElse(null);
        if (behavior == null || !behavior.diggable() || behavior.hardness() < 0) {
            return; // air or unbreakable: no session (bedrock never opens one)
        }
        if (!InteractionRules.withinSurvivalReach(player.position(), target)) {
            return;
        }
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
        // The nuker guard (the NCP Frequency shape): the rolling 20-tick
        // budget plus the same-tick different-target signature (Grim's
        // MultiBreak). Over budget: the dig reverses, the ladder counts.
        long nowNanos = System.nanoTime();
        boolean overBudget = !player.violations().recordBreak(nowNanos, BREAK_WINDOW_NANOS, SURVIVAL_BREAK_BUDGET);
        boolean multiBreak = player.violations().isMultiBreak(nowNanos, target);
        if (overBudget || multiBreak) {
            player.violations().addNukerViolation();
            resync(target);
            LOGGER.warning(() -> "Nuker violation for " + player.name() + " (vl "
                    + player.violations().nukerViolations() + ") overBudget=" + overBudget
                    + " multiBreak=" + multiBreak + " target=" + target);
            if (player.violations().nukerViolations() >= NUKER_KICK_VIOLATIONS) {
                player.link().kick("Nuker");
            }
            return;
        }
        player.violations().noteBreakTarget(nowNanos, target);
        BlockBehavior behavior = BlockBehaviorTable.of(current.identifier()).orElse(null);
        if (behavior == null || !behavior.diggable()) {
            resync(target);
            return;
        }
        long elapsedNanos = System.nanoTime() - session.startedNanos();
        net.zaminmc.torch.item.ItemStack heldStack = player.inventory().held();
        ItemType held = heldStack.isEmpty() ? null : heldStack.type();
        boolean canHarvest = BlockBehaviorTable.canHarvest(behavior, held);
        double speedMultiplier = BlockBehaviorTable.speedMultiplier(behavior, held);
        int requiredTicks = behavior.breakTicks(speedMultiplier, canHarvest);
        long minimumNanos = (long) (requiredTicks * TICK_NANOS * MINING_TIMING_LENIENCY);
        if (elapsedNanos < minimumNanos) {
            resync(target); // too fast: undo the client's local prediction (§441 spirit)
            LOGGER.fine(() -> "Rejected mining finish (too fast: " + (elapsedNanos / 1_000_000)
                    + "ms < " + (minimumNanos / 1_000_000) + "ms) by " + player.name());
            return;
        }
        commit(target, world.airType());
        publishDrops(target, current, held);
        wearHeldTool(player, behavior);
        SurvivalXpHook xp = survivalXpListener;
        if (xp != null) {
            xp.onSurvivalBreak(player, current, target); // the award sees the broken block
        }
        var listener = blockBrokenListener;
        if (listener != null) {
            listener.accept(target, current); // container spill runs after the block is gone
        }
    }

    /**
     * Historical durability wear: one successful dig of a block with hardness
     * costs a tool one durability unit (swords cost 2 against entities only,
     * which is the combat slice's concern). A tool at its limit breaks and
     * leaves the hand. Creative players never wear tools.
     */
    private void wearHeldTool(PlayerSession player, BlockBehavior behavior) {
        GameMode mode = gameMode.apply(player);
        if (mode == GameMode.CREATIVE || behavior.hardness() <= 0) {
            return;
        }
        if (player.inventory().damageHeld(1)) {
            // Worn or broken: the client's held slot is now stale, re-sync it.
            inventorySync.accept(player);
        }
    }

    /**
     * Registers a post-break hook invoked on the tick thread after a break
     * commits (the broken block's position and its pre-break type). Used by
     * the engine to spill container contents (furnace slots) into the world
     * and to invalidate bed spawns.
     */
    public void setBlockBrokenListener(
            java.util.function.BiConsumer<BlockPosition, net.zaminmc.torch.block.BlockType> listener) {
        this.blockBrokenListener = listener;
    }

    /**
     * The survival-break XP hook: invoked on the tick thread after a survival
     * mining finish commits, with the broken block's type and position.
     * Creative breaks never fire it (the historical no-XP rule).
     */
    public interface SurvivalXpHook {
        void onSurvivalBreak(PlayerSession player, BlockType broken, BlockPosition position);
    }

    /**
     * Registers the survival-break XP hook (the engine's mining award wiring).
     * Tick-thread context.
     */
    public void setSurvivalXpListener(SurvivalXpHook listener) {
        this.survivalXpListener = listener;
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
        // The vanilla body rule: a block WITH a collision box never places
        // inside the player; collision-less blocks (torches, flora, rails)
        // place freely inside any entity.
        if (WorldSolidity.isSolid(held)
                && InteractionRules.intersectsPlayer(player.position(), target)) {
            LOGGER.fine(() -> "Rejected placement (inside player) by " + player.name());
            return;
        }
        // The fastplace guard runs the creative path too (the same budget —
        // the creative 1.8 client cannot legitimately outpace it either).
        if (!player.violations().recordPlace(System.nanoTime(), PLACE_WINDOW_NANOS, PLACE_BUDGET)) {
            player.violations().addFastPlaceViolation();
            resync(target);
            LOGGER.warning(() -> "Fastplace violation (creative) for " + player.name()
                    + " (vl " + player.violations().fastPlaceViolations() + ")");
            if (player.violations().fastPlaceViolations() >= FASTPLACE_KICK_VIOLATIONS) {
                player.link().kick("Fastplace");
            }
            return;
        }
        commit(target, held);
    }

    private void survivalPlaceOnTick(PlayerSession player, BlockPosition clicked, int face) {
        net.zaminmc.torch.item.ItemStack heldStack = player.inventory().held();
        if (heldStack.isEmpty()) {
            return; // nothing held: no-op
        }
        BlockType heldBlock = blockItemResolver.apply(heldStack.type()).orElse(null);
        if (heldBlock == null) {
            LOGGER.fine(() -> "Rejected survival placement of non-block item by " + player.name());
            return;
        }
        BlockPosition target = InteractionRules.offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())
                || !InteractionRules.withinSurvivalReach(player.position(), target)
                || (WorldSolidity.isSolid(heldBlock)
                        && InteractionRules.intersectsPlayer(player.position(), target))) {
            if (target == null) {
                LOGGER.fine(() -> "Rejected survival placement (bad face) by " + player.name());
            } else if (world.getBlock(clicked).equals(world.airType())) {
                LOGGER.fine(() -> "Rejected survival placement (clicked air) by " + player.name());
            } else if (!world.getBlock(target).equals(world.airType())) {
                LOGGER.fine(() -> "Rejected survival placement (target not air: "
                        + world.getBlock(target).identifier() + " at " + target + ") by " + player.name());
            } else if (!InteractionRules.withinSurvivalReach(player.position(), target)) {
                LOGGER.fine(() -> "Rejected survival placement (out of reach) by " + player.name());
            } else {
                LOGGER.fine(() -> "Rejected survival placement (inside player) by " + player.name());
            }
            if (target != null) {
                // Real clients predict the placement locally; an unanswered
                // reject leaves the ghost block until the next chunk sync.
                // The authoritative cell re-sync drops the prediction.
                resync(target);
            }
            return;
        }
        // The fastplace guard (the NCP shape): the rolling 10-tick budget;
        // over budget the placement refuses and the cell re-syncs (the
        // client's prediction drops).
        if (!player.violations().recordPlace(System.nanoTime(), PLACE_WINDOW_NANOS, PLACE_BUDGET)) {
            player.violations().addFastPlaceViolation();
            resync(target);
            LOGGER.warning(() -> "Fastplace violation for " + player.name() + " (vl "
                    + player.violations().fastPlaceViolations() + ")");
            if (player.violations().fastPlaceViolations() >= FASTPLACE_KICK_VIOLATIONS) {
                player.link().kick("Fastplace");
            }
            return;
        }
        commit(target, heldBlock);
        player.inventory().consumeHeld(1); // atomic with the commit above (§431)
        inventorySync.accept(player);
    }

    /**
     * The placement path of the engine's use-on-block dispatch, run on the
     * caller's (simulation) context: creative places the client-claimed block,
     * survival consumes from the authoritative inventory. The engine calls this
     * from its own tick task after the container check (crafting table) has
     * ruled out a block GUI. Creative uses with no mappable held block are a
     * no-op, matching the direct creative path.
     */
    public void placeFromUseOnTick(PlayerSession player, BlockPosition clicked, int face,
                                   java.util.Optional<BlockType> creativeHeld) {
        if (creativeHeld.isPresent()) {
            placeOnTick(player, clicked, face, creativeHeld.get());
        } else if (gameMode.apply(player) == GameMode.SURVIVAL) {
            survivalPlaceOnTick(player, clicked, face);
        }
        // creative with an unmapped/empty held item: a no-op use
    }

    /**
     * The single semantic commit: the world's setBlock dispatches the change
     * event itself (§208 — the world is the source of truth; player- and
     * engine-driven changes reach neighbor updates and client syncs through
     * the same listener path). The feedback hook (break/place sounds and
     * particles) fires after the mutation, before the caller continues.
     */
    private void commit(BlockPosition position, BlockType type) {
        BlockType previous = world.getBlock(position);
        world.setBlock(position, type);
        java.util.function.Consumer<Commit> feedback = commitFeedbackListener;
        if (feedback != null && !previous.equals(type)) {
            feedback.accept(new Commit(position, previous, type));
        }
    }

    /** One world mutation with its before/after block states. */
    public record Commit(BlockPosition position, BlockType previous, BlockType now) {
    }

    /**
     * Registers the commit feedback hook (the engine's FX bus wiring). The
     * consumer runs on the tick thread, directly after the world mutation.
     */
    public void setCommitFeedbackListener(java.util.function.Consumer<Commit> listener) {
        this.commitFeedbackListener = listener;
    }

    /** Drop calculation (§434): committed break -&gt; drops -&gt; item entities. */
    private void publishDrops(BlockPosition broken, BlockType brokenType, ItemType heldTool) {
        List<ItemStack> drops = dropService.dropsFor(brokenType, heldTool);
        if (drops.isEmpty()) {
            return;
        }
        // The manager positions drops around the block's center from its min corner.
        var blockOrigin = new net.zaminmc.torch.util.Position(broken.x(), broken.y(), broken.z());
        for (ItemStack drop : drops) {
            itemEntities.spawnDropAtBlock(blockOrigin, drop, ItemEntity.PICKUP_DELAY_DROP_TICKS);
        }
    }

    /** Re-publishes the authoritative current state so clients drop predictions. */
    private void resync(BlockPosition position) {
        publisher.accept(new BlockChange(position, world.getBlock(position)));
    }
}
