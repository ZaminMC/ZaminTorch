package net.zamin.engine.net;

/**
 * The engine's handle to a client connection, implemented by the protocol adapter.
 *
 * <p>The engine only pushes engine-decided lifecycle actions through this link
 * (kick). It never sees packets, frames or transport objects. isActive() must be
 * safe to call from any thread.</p>
 */
public interface ClientLink {

    /** @return whether the underlying connection is still open. */
    boolean isActive();

    /**
     * Disconnects the client with the given engine-decided reason.
     * Must be safe to call multiple times; must never block.
     */
    void kick(String reason);
}
