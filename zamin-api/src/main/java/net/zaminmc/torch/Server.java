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

    /**
     * The item registry (the §27.3 registry family): the frozen item-type
     * set — enumeration and lookups by canonical identifier. Frozen
     * before gameplay begins; every call is safe from any thread.
     */
    net.zaminmc.torch.item.ItemRegistry items();

    /**
     * The safe asynchronous-computation surface (§27.3's compute
     * family): pure calculations on the bounded pool, results validated
     * and published on the simulation owner's context.
     */
    net.zaminmc.torch.concurrent.AsyncCompute compute();

    /**
     * The cross-owner request surface (§28.2's default nonblocking
     * API): a mutation request toward the owner of a world position —
     * the target applies it in its own validated context at a tick
     * boundary; the submitter never blocks.
     */
    net.zaminmc.torch.concurrent.CrossOwner crossOwner();

    /**
     * The blocking-I/O surface (§28.1's fourth category — the storage
     * lane): database, filesystem, HTTP, and other potentially blocking
     * work on its own bounded pool, completions validated and published
     * on the simulation owner's context.
     */
    net.zaminmc.torch.concurrent.BlockingIo blockingIo();

    /**
     * The world's game-rule table (§27.3 — the 1.8.8 gamerule family):
     * the fifteen vanilla rules plus any custom additions. One shared
     * table for the whole server (the reference's own shape — every
     * dimension's derived data delegates to the overworld's rules).
     * Reads are safe from any thread; {@code set} is an owner-context
     * mutation (§27.6).
     */
    net.zaminmc.torch.gamerule.GameRules gameRules();

    /** All currently loaded worlds. Read-only view. */
    Collection<World> worlds();

    /** All currently active (connected) players. Read-only snapshot. */
    Collection<Player> players();

    /**
     * The tab-list header/footer (Slice 87 — Player List Header/Footer
     * 0x47, TabListS2CPacket's two-text walk): the serialized components
     * (the {@code {"text":...}} JSON form) for every online player, with
     * later joins receiving the current pair at their own join burst.
     * A null argument clears to the client's default (the wire's own
     * null-text form). The vanilla 1.8.8 server never sends this packet
     * (the reference wires only the client handler) — this is the native
     * API surface, not a vanilla behavior. Safe to call from any thread;
     * the wire never blocks.
     */
    void setTabListHeaderFooter(String headerJson, String footerJson);

    /** Blocks until the server stopped. Safe to call from any thread. */
    void awaitShutdown();

    /**
     * The public event bus. Registration is safe from any thread; handlers
     * run on the engine context that fired each event.
     */
    EventBus events();

    /**
     * The plugin host (architecture §28.5/§28.6): the plugin lifecycle
     * state machine, the per-plugin resource scopes, and the managed
     * task quotas. Programmatic registration now (the discovery and
     * classloading wave follows §27.1's order); the lifecycle verbs run
     * on the control context (boot or console) and must not be called
     * from inside a lifecycle callback.
     */
    net.zaminmc.torch.plugin.PluginHost plugins();

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

    /**
     * The village-census query surface (§27.3's world-state family — the
     * VIII10 exposure): the founded villages with their door rows,
     * center/radius bands, population and golem counts, reputation
     * tables, and the mating window, plus the reference's own
     * nearest-village consult. Every answer is an immutable
     * point-in-time snapshot — safe from any thread.
     */
    default net.zaminmc.torch.village.Villages villages() {
        return net.zaminmc.torch.village.Villages.EMPTY;
    }

    /**
     * The riding seat-row query surface (§27.3's riding family — the
     * §29 VII5/VIII1 exposure): the exotic pairs' seat reads beside the
     * vehicle family's own typed {@code Vehicle.riderEntityId()} — a
     * mount's seated-rider row, a rider's mount row, and the live seat
     * census, answering whichever store carries the seat (the
     * generic-mount registry, the mob mounts' rows, the vehicle seats).
     * The per-id consults are live reads — simulation context (§27.6,
     * the documented owner path is {@code World.execute(runnable)});
     * the census answers a point-in-time copy, consumable from any
     * thread once obtained. Worlds without live seats answer empty.
     */
    default net.zaminmc.torch.entity.Vehicle.SeatRows seatRows() {
        return net.zaminmc.torch.entity.Vehicle.SeatRows.EMPTY;
    }

    /**
     * The validated server configuration (§27.3's server-lifecycle read
     * surface): the frozen boot-time settings as an immutable value
     * snapshot. Safe from any thread; the configuration never changes
     * at runtime (a change requires a restart — the historical rule).
     */
    net.zaminmc.torch.config.ServerConfig config();

    /**
     * The Anvil import verb (§31.4's explicit-import surface — the Wave IX
     * exposure): one explicit, read-only import of an original 1.8.8 world
     * folder ({@code level.dat} + {@code region/*.mca}) into the live
     * world, riding the SAME one-import gate and the SAME I/O lanes as
     * the operator's {@code /anvilimport} — the one authoritative route,
     * never a parallel import path. Per §31.4 this is never a startup
     * detection; the caller's explicit action is the only entry.
     *
     * <p><b>THE RETURN (the ledger report):</b> the walk's own final
     * answer row, the same report the operator command prints — no second
     * report format exists. On success: {@code "Anvil import finished: "}
     * carrying the §31.6 counted-refusal census (the installed/skipped/
     * unoccupied chunk rows, the imported entity/tile/tile-tick counts,
     * every isolation by its exact reason with its first examples, the
     * unknown block states, the present-but-not-imported format rows).
     * On a refused folder: {@code "Anvil import refused: ..."} — a missing
     * {@code level.dat} or {@code region/} is a loud refusal, never a
     * default world. On a concurrent call while a walk holds the
     * one-import gate: {@code "An Anvil import is already running; wait
     * for its report"} — answered immediately, never queued, never racing
     * the in-flight walk. A mid-walk simulation-generation change answers
     * the abandonment row.</p>
     *
     * <p><b>THE THREADING LAW (§28.1's fourth category, the blocking-I/O
     * discipline):</b> the calling thread BLOCKS until the walk's final
     * drain answers. Every file read rides the blocking-I/O lane; every
     * world mutation (the level rows' apply, the region bytes' parse and
     * the per-chunk installs) lands on the owning lane between ticks —
     * the gameplay lane NEVER blocks (the simulation keeps ticking while
     * the caller waits; the walk's phases deliver at tick heads). The
     * verb must NOT be called from the simulation lane itself (a tick
     * body or a scheduled task — the walk's phases deliver there, so a
     * parked tick thread would starve its own drains); that context is
     * refused loudly instead of deadlocking. A server stop mid-walk is
     * the one arm no drain answers (the lanes go down without a stale
     * generation); the parked caller then receives the abandonment row
     * rather than hanging on a dying server.</p>
     */
    default String anvilImport(java.nio.file.Path worldFolder) {
        java.util.Objects.requireNonNull(worldFolder, "worldFolder");
        return "The anvil import verb is not implemented by this server";
    }

    /**
     * The achievement-table query surface (§27.3's progression family —
     * the VIII6 exposure): the reference 1.8.8's own 34-row table (every
     * row's key, name, page column/row, icon, parent chain, challenge
     * flag and local row — the give-all walk's own declaration order)
     * plus the per-player unlocked read over the live stats store. The
     * table is a frozen registration — those reads are pure, safe from
     * any thread; the per-player read is live-authoritative, consistent
     * on the player's own simulation context (the same contract as the
     * {@link Player} body reads).
     */
    default net.zaminmc.torch.stat.Achievements achievements() {
        return net.zaminmc.torch.stat.Achievements.EMPTY;
    }
}
