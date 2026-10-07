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

    /** Result of {@link #joinRequest}. */
    sealed interface JoinResult permits Accepted, Rejected {
    }

    record Accepted(PlayerSession session) implements JoinResult {
    }

    record Rejected(String reason) implements JoinResult {
    }
}
