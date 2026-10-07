package net.zamin.engine.net;

import net.zamin.engine.player.PlayerSession;

import java.util.UUID;

/**
 * The engine-facing port a protocol adapter calls into. This is the single
 * boundary between wire handling and gameplay: everything the engine learns
 * from a client arrives as one of these calls.
 *
 * <p>Implementations must be thread-safe (called from network event loops);
 * gameplay mutation is internally routed onto the simulation context.</p>
 */
public interface EngineBridge {

    /**
     * A client completed login start and requests gameplay identity.
     *
     * @return accepted session, or a rejection reason (the adapter owns how the
     *         rejection is represented on the wire)
     */
    JoinResult joinRequest(ClientLink link, String username, UUID offlineUuid);

    /**
     * The adapter finished its join sequence (version-specific spawn
     * synchronization); the player is now fully in play state.
     */
    void joinCompleted(PlayerSession session);

    /**
     * A movement proposal (position, look, or combined). Validated server-side;
     * applied on the simulation context.
     */
    void movementProposal(PlayerSession session, net.zamin.api.Position position,
                          net.zamin.api.Rotation rotation, boolean onGround);

    /** The client disconnected (cleanly or by error). Engine-side cleanup follows. */
    void clientDisconnected(PlayerSession session, String reason);

    /**
     * A window click in an open window (window 0 = player inventory, or the
     * session's open container): the adapter reports the raw click (historical
     * wire slot, button, mode, action number); the engine applies the semantic
     * operation on the simulation context and reports the verdict through
     * {@code result} (accepted / rejected), after which the adapter confirms
     * the action number and re-syncs the affected slots.
     */
    void windowClick(PlayerSession session, int windowId, int wireSlot, int button, int mode,
                     java.util.function.Consumer<Boolean> result);

    /**
     * A right-click use on a block (§215 family). The engine decides on the
     * simulation context: a block with a container interface (the crafting
     * table) opens its window and reports the id through {@code onTableOpened};
     * otherwise the use degrades to a placement proposal — survival consumes
     * from the authoritative inventory, creative places the client-claimed
     * block (empty optional = no block held).
     */
    void useItemOnBlock(PlayerSession session, net.zamin.api.BlockPosition clicked, int face,
                        java.util.Optional<net.zamin.api.BlockType> creativeHeld,
                        java.util.function.IntConsumer onTableOpened);

    /**
     * The client closed a window (player inventory, or the open container).
     * Carried window state returns to the inventory; overflow is thrown into
     * the world so nothing is lost.
     */
    void closeWindow(PlayerSession session, int windowId);

    /** Result of {@link #joinRequest}. */
    sealed interface JoinResult permits Accepted, Rejected {
    }

    record Accepted(PlayerSession session) implements JoinResult {
    }

    record Rejected(String reason) implements JoinResult {
    }
}
