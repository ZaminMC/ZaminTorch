package net.zaminmc.torch;

import net.zaminmc.torch.entity.Player;

import java.util.Collection;

/**
 * The public view of a running server. Deliberately minimal: plugins observe and
 * interact through this interface; everything else is engine-internal.
 */
public interface Server {

    ServerState state();

    /** All currently loaded worlds. Read-only view. */
    Collection<World> worlds();

    /** All currently active (connected) players. Read-only snapshot. */
    Collection<Player> players();

    /** Blocks until the server stopped. Safe to call from any thread. */
    void awaitShutdown();
}
