package net.zaminmc.torch.server.world.layer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The layer stack's int-array allocator — the reference's IntCache shape
 * (the push/pop discipline over the live allocations) with THE ENGINE'S
 * UNIFIED POOL (the named adaptation): one size-aware band instead of
 * the reference's two.
 *
 * <p>WHY THE ADAPTATION: the reference's two-band pool (the ≤256 band
 * and the grow-on-demand large band) never returns the small band's
 * arrays — {@code tcache} feeds only from the large band's discards,
 * and the large pool is always drained by the next query's own pushes
 * before {@code pop} ever sees it — a leak that holds at the
 * reference's own query volume (the BiomeCache's 30-second window
 * serves most reads, so full chain walks are rare) but grows without
 * bound at the engine's per-chunk sampling volume (the bounded LRU
 * churns every chunk; the biome sweep's 16k queries leak ~200k arrays
 * and OOM the worker). The unified pool recycles EVERY array — one
 * band, all arrays sized to the running maximum, {@code pop} returning
 * the whole live set — the answers byte-identical, the memory bounded.
 */
public final class IntCache {

    private static int num = 256;
    private static final Deque<int[]> POOL = new ArrayDeque<>();
    private static final Deque<int[]> LIVE = new ArrayDeque<>();
    private static long allocations;

    private IntCache() {
    }

    /**
     * One live array of at least {@code size} ints: the pool's recycled
     * arrays first — the scan hands out only an array LONG ENOUGH (the
     * pool holds a mix of sizes: the growth branch re-sizes the
     * maximum, but the live set's older small arrays still return at
     * the boundary) — a fresh allocation when the pool is dry, and a
     * re-size of the whole pool when the request outgrows the maximum
     * (the growth is monotone — the discard happens once per new
     * maximum, never in the steady state).
     */
    public static synchronized int[] push(int size) {
        if (size > num) {
            num = size;
            POOL.clear();
            allocations++;
            int[] grown = new int[num];
            LIVE.add(grown);
            return grown;
        }
        for (java.util.Iterator<int[]> it = POOL.iterator(); it.hasNext();) {
            int[] candidate = it.next();
            if (candidate.length >= size) {
                it.remove();
                LIVE.add(candidate);
                return candidate;
            }
        }
        allocations++;
        int[] fresh = new int[Math.max(size, 256)];
        LIVE.add(fresh);
        return fresh;
    }

    /**
     * The query boundary: the whole live set returns to the pool (the
     * reference's own discipline — one pop per top-level query, the
     * caller holding only CLONED results past the boundary).
     */
    public static synchronized void pop() {
        while (!LIVE.isEmpty()) {
            POOL.push(LIVE.pop());
        }
    }

    /** The diagnostics face (the reference's own statistics surface). */
    public static synchronized String getStatistics() {
        return "cache: " + POOL.size() + ", allocated: " + LIVE.size()
                + ", num: " + num + ", totalAllocations: " + allocations;
    }
}
