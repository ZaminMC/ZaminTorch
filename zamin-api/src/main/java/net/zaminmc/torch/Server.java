package net.zaminmc.torch;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.event.EventBus;

import java.util.Collection;

/**
 * The public view of a running server. Deliberately minimal: plugins observe and
 * interact through this interface; everything else is engine-internal.
 */
public interface Server {

    ServerState state();

    /**
     * @return the runtime difficulty id (0 peaceful, 1 easy, 2 normal,
     *         3 hard — the 1.8.8 difficulty ids). Safe from any thread;
     *         the value is a stable int by design (the difficulty API
     *         surface rides its own slice for the set arm).
     */
    int difficulty();

    /** All currently loaded worlds. Read-only view. */
    Collection<World> worlds();

    /** All currently active (connected) players. Read-only snapshot. */
    Collection<Player> players();

    /** Blocks until the server stopped. Safe to call from any thread. */
    void awaitShutdown();

    /**
     * The public event bus. Registration is safe from any thread; handlers
     * run on the engine context that fired each event.
     */
    EventBus events();
}
