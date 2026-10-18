package net.zaminmc.torch.server.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The random-tick pick computation (the Phase-6 lane's first adoption —
 * the compute-then-apply region lane): the PURE, per-region sampling
 * that drives {@code RandomTickSystem}'s walk, split out so the picks
 * can be computed IN PARALLEL (each region an independent job on the
 * domain worker pool) and applied SEQUENTIALLY on the tick lane —
 * §6.2's one authoritative writer keeps every world write, and the
 * compute side never touches world state at all (Phase 5's immutable-
 * input contract: the region id, the tick, the chunk positions, the
 * picks-per-section band IN — the picked positions OUT).
 *
 * <p>THE DERIVED STREAM: each region carries its own {@code Random}
 * seeded from {@code (baseSeed, tick, regionId)} — the picks are
 * deterministic PER REGION regardless of the lane mode (sequential or
 * parallel) or the region processing order, so the two modes produce
 * IDENTICAL picks and the equivalence is testable by construction
 * (the lane's own proof obligation, §22 Phase 6's evidence gate).</p>
 *
 * <p>THE PICK ORDER inside a region follows the historical walk's
 * shape: the region's chunks in their given (stable) order, each
 * chunk's sections in order, each section's {@code picksPerSection}
 * samples drawing x/y/z — one stream per region, no cross-region
 * coupling anywhere in the computation.</p>
 */
public final class RandomTickPicks {

    private RandomTickPicks() {
    }

    /** The stream-mixing primes (the derivation's own constants). */
    private static final long TICK_PRIME = 0x9E3779B97F4A7C15L;
    private static final long REGION_PRIME = 0xBF58476D1CE4E5B9L;

    /**
     * The per-region stream: {@code new Random(baseSeed ^ tick * P1 ^
     * regionId * P2)} — every (tick, region) pair its own
     * deterministic sequence, stable across lane modes and restarts.
     */
    public static Random streamFor(long baseSeed, long tick, long regionId) {
        return new Random(baseSeed ^ (tick * TICK_PRIME) ^ (regionId * REGION_PRIME));
    }

    /**
     * One region's picks: for each chunk (in the given order), each of
     * the 16 sections, {@code picksPerSection} samples of the cell
     * band. Pure — no world read, no shared state, no ordering
     * dependency on any other region.
     *
     * @param baseSeed       the world's seed (the stream derivation's
     *                       root)
     * @param tick           the tick the picks belong to
     * @param regionId       the region the chunks belong to
     * @param chunkPositions the region's chunk origins ({@code [x, z]}
     *                       pairs, chunk coords), in the walk's stable
     *                       order
     * @param picksPerSection the {@code randomTickSpeed} band (0 picks
     *                       answers empty — the rule's own gate)
     * @return the picked cells in draw order, world coordinates
     */
    public static List<long[]> picksForRegion(long baseSeed, long tick, long regionId,
            List<long[]> chunkPositions, int picksPerSection) {
        List<long[]> picks = new ArrayList<>();
        if (picksPerSection <= 0) {
            return picks;
        }
        Random stream = streamFor(baseSeed, tick, regionId);
        for (long[] chunk : chunkPositions) {
            for (int section = 0; section < 16; section++) {
                for (int pick = 0; pick < picksPerSection; pick++) {
                    int x = ((int) chunk[0]) * 16 + stream.nextInt(16);
                    int y = section * 16 + stream.nextInt(16);
                    int z = ((int) chunk[1]) * 16 + stream.nextInt(16);
                    picks.add(new long[]{x, y, z});
                }
            }
        }
        return picks;
    }
}
