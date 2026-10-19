package net.zaminmc.torch.server.concurrent;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The save pipeline's ordering half (Slice P7 — the Phase-5 serialization
 * adoption): the per-storage lock orders the concurrent publications of
 * one storage file (§31.6's single-writer rule — two workers never rewrite
 * one file at the same instant), and the monotonic per-storage generation
 * stamps discard the late publication of an OLDER snapshot (§31.6's
 * stale-write rule — a save G1 still in flight when G2 published the same
 * file must never replace the newer persisted state).
 *
 * <p>THE CONTRACT: the caller captures every immutable snapshot on the
 * owning lane (the engine's own snapshot walk), wraps each store's write
 * as a {@link Unit} bound to those snapshots only, and hands the list to
 * {@link #publishAll(List, long)} — either INLINE (the historical walk,
 * the gate OFF) or from inside an I/O-lane job (the gate ON). The units
 * run in the given order; a unit whose storage already published a
 * strictly newer generation SKIPS (its snapshot is obsolete); every other
 * unit runs exactly once under its storage's own lock.</p>
 *
 * <p>THE LIVENESS: one storage lock is held at a time and never nested,
 * and a unit only performs file I/O — no deadlock surface. A failing unit
 * propagates to the caller (persistence failures are loud, §54); the
 * remaining units of that walk do not run, exactly like the historical
 * inline walk's exception behavior.</p>
 */
public final class StorageSavePipeline {

    /** One publication unit: the storage's identity + its snapshot-bound write. */
    public record Unit(String storage, Runnable publication) {
        public Unit {
            if (storage == null || storage.isBlank()) {
                throw new IllegalArgumentException("storage identity required");
            }
            java.util.Objects.requireNonNull(publication, "publication");
        }
    }

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastPublished = new ConcurrentHashMap<>();
    private final AtomicLong staleSkips = new AtomicLong();
    private final AtomicLong publishedUnits = new AtomicLong();

    /**
     * Publishes the units in order under the per-storage locks.
     *
     * @param units      the walk's units, in the save walk's stable order
     * @param generation the save request's monotonic generation
     * @return the number of units actually published (the stale skips excluded)
     */
    public int publishAll(List<Unit> units, long generation) {
        if (generation < 1) {
            throw new IllegalArgumentException("generation must be positive: " + generation);
        }
        int published = 0;
        for (Unit unit : units) {
            Object lock = locks.computeIfAbsent(unit.storage(), key -> new Object());
            synchronized (lock) {
                long last = lastPublished.getOrDefault(unit.storage(), 0L);
                if (last >= generation) {
                    // A newer request already published this storage from a
                    // newer snapshot — the older write would replace newer
                    // state (§31.6's stale-write rule): it never lands.
                    staleSkips.incrementAndGet();
                    continue;
                }
                unit.publication().run();
                lastPublished.put(unit.storage(), generation);
            }
            publishedUnits.incrementAndGet();
            published++;
        }
        return published;
    }

    /** @return how many stale units the stamps discarded (telemetry). */
    public long staleSkipCount() {
        return staleSkips.get();
    }

    /** @return how many units published since boot (telemetry). */
    public long publishedUnitCount() {
        return publishedUnits.get();
    }
}
