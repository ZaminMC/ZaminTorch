package net.zaminmc.torch.server.concurrent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The spatial partition model (the permanent architecture's Phase 4 second
 * unit — the design's §2 "introduce spatial ownership only after cross-owner
 * protocols exist and have tests"): the world's chunk space divides into a
 * fixed grid of R×R-chunk regions, every chunk belongs to exactly one
 * region by deterministic floor-division, and each region carries one
 * {@link OwnershipDomain} — the region is the authoritative mutation unit
 * for the chunks and entities inside it (the design's §4 state ownership
 * map: "chunk block states and block entities → domain owning the chunk").
 *
 * <p>The partition is <em>static</em>: no auto-balancing, no re-mapping —
 * the rollout's Phase 4 explicitly excludes dynamic auto-balancing, and the
 * master spec's §19 warns against player-distance ownership checks. Region
 * size is a constructor parameter because the exact granularity is
 * determined experimentally (the master spec §19), not by doctrine.</p>
 *
 * <p>Negative coordinates floor-divide (chunk -1 sits in region -1, sharing
 * its region with chunks -8..-1 for R=8), so the grid is seamless across
 * the world origin — the mapping is pure arithmetic, stable for the
 * engine's whole run, and identical on every JVM.</p>
 */
public final class RegionPartition {

    private final int regionSizeChunks;
    private final String worldName;
    private final Map<Long, OwnershipDomain> regions = new ConcurrentHashMap<>();

    public RegionPartition(String worldName, int regionSizeChunks) {
        if (regionSizeChunks < 1) {
            throw new IllegalArgumentException("regionSizeChunks must be positive");
        }
        this.worldName = java.util.Objects.requireNonNull(worldName, "worldName");
        this.regionSizeChunks = regionSizeChunks;
    }

    /** The region's grid size in chunks per axis. */
    public int regionSizeChunks() {
        return regionSizeChunks;
    }

    /**
     * The deterministic region id for the chunk: floor-divided grid
     * coordinates packed into one long. Pure arithmetic — no state, no
     * locks, identical on every call from every thread.
     */
    public long regionId(int chunkX, int chunkZ) {
        long rx = Math.floorDiv(chunkX, regionSizeChunks);
        long rz = Math.floorDiv(chunkZ, regionSizeChunks);
        return (rx << 32) | (rz & 0xFFFFFFFFL);
    }

    /** The region's grid x (packed id → coordinates). */
    public static int regionXOf(long regionId) {
        return (int) (regionId >> 32);
    }

    /** The region's grid z (packed id → coordinates). */
    public static int regionZOf(long regionId) {
        return (int) regionId;
    }

    /**
     * The ownership domain for the chunk's region, created on first touch
     * and stable afterwards — the same region resolves to the same domain
     * object for the engine's whole run (the ownership identity the
     * assertions, the scheduler, and the router all key on).
     */
    public OwnershipDomain domainFor(int chunkX, int chunkZ) {
        return regions.computeIfAbsent(regionId(chunkX, chunkZ),
                id -> OwnershipDomain.create("region:" + worldName + ":"
                        + regionXOf(id) + ":" + regionZOf(id)));
    }

    /** @return whether the given domain is the chunk's region authority. */
    public boolean owns(int chunkX, int chunkZ, OwnershipDomain domain) {
        return domainFor(chunkX, chunkZ) == domain;
    }

    /**
     * Every created region domain, in stable name order — the deterministic
     * walk order the scheduler and the drains ride (design invariant 9).
     */
    public List<OwnershipDomain> domains() {
        List<OwnershipDomain> all = new ArrayList<>(regions.values());
        all.sort(Comparator.comparing(OwnershipDomain::name));
        return all;
    }

    /** @return how many region domains exist (telemetry). */
    public int regionCount() {
        return regions.size();
    }
}
