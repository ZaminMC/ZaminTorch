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

    /**
     * The block registry (Slice 14b): the frozen block-type set with the
     * survival-data reads. Frozen before gameplay begins; every call is
     * safe from any thread.
     */
    net.zaminmc.torch.block.BlockRegistry blocks();

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

    /**
     * The public task scheduler (§27.3): the time dimension over the
     * owner-context execution. Submissions are safe from any thread;
     * every body runs on the simulation owner's context.
     */
    net.zaminmc.torch.scheduler.Scheduler scheduler();

    /**
     * The rolling average ticks per second over roughly the given window
     * (the Paper 1m/5m/15m report's windows); 20.0 before enough data
     * accumulates. Safe from any thread; the read is a best-effort
     * snapshot of the completed-tick window.
     */
    double tps(int windowSeconds);

    /**
     * The dispenser's behavior registry (§27.3's registration surface):
     * register and reset per-item dispense behaviors. The verbs are safe
     * from any thread (admitted onto the owner lane); {@code behaviorOf}
     * confirms.
     */
    net.zaminmc.torch.dispenser.DispenseBehaviors dispenserBehaviors();

    /**
     * The protocol hooks (§27.3's packet interception surface): observe,
     * cancel, and re-payload the outbound frames of the active protocol
     * version. Registration is safe from any thread; the listeners run on
     * each recipient's wire thread.
     */
    net.zaminmc.torch.protocol.ProtocolHooks protocolHooks();

    /**
     * The recipe registry (§27.3's crafting read surface): the frozen
     * recipe set with the reference's own matching semantics. Every read
     * is pure — safe from any thread.
     */
    net.zaminmc.torch.crafting.Recipes recipes();

    /**
     * The enchantment registry (§27.3's registry read surface): the
     * frozen enchantment set (the reference's Enchantment.ALL table).
     * Every read is pure — safe from any thread.
     */
    net.zaminmc.torch.enchantment.EnchantmentRegistry enchantments();
}
