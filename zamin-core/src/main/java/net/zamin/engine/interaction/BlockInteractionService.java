package net.zamin.engine.interaction;

import net.zamin.api.BlockPosition;
import net.zamin.api.BlockType;
import net.zamin.engine.EngineTicker;
import net.zamin.engine.player.PlayerSession;
import net.zamin.engine.world.EngineWorld;

import java.util.Objects;
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
 * <p>Slice #2 scope: creative-mode instant breaking, creative placement without
 * inventory consumption (the client's creative inventory remains client-side).
 * Survival mechanics, drops and item entities are later slices.</p>
 */
public final class BlockInteractionService {

    private static final Logger LOGGER = Logger.getLogger(BlockInteractionService.class.getName());

    private final EngineWorld world;
    private final EngineTicker ticker;
    private final Consumer<BlockChange> publisher;

    /** A committed block change, published to observers (adapter sync). */
    public record BlockChange(BlockPosition position, BlockType newType) {
    }

    public BlockInteractionService(EngineWorld world, EngineTicker ticker,
                                   Consumer<BlockChange> publisher) {
        this.world = Objects.requireNonNull(world, "world");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    /** Requests an instant (creative) break. Safe from any thread. */
    public void submitBreak(PlayerSession player, BlockPosition position) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(position, "position");
        ticker.submit(() -> breakOnTick(player, position));
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

    private void breakOnTick(PlayerSession player, BlockPosition position) {
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
}
