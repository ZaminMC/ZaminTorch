package net.zaminmc.torch.server.concurrent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
 * <p>Negative coordinates floor-divide (chunk -1 sits in region -1, sharing
 * its region with chunks -8..-1 for R=8), so the grid is seamless across
 * the world origin — the mapping is pure arithmetic, stable for the
 * engine's whole run, and identical on every JVM.</p>
 *
 * <p><b>THE SUBDIVISION LAYER (Phase 8, Wave MIG-3 — the split/merge
 * executors' routing surface)</b>: a region whose domain stayed sustainedly
 * hot may carry an active {@link Subdivision} — an axis-aligned split of
 * the region's chunk grid at one chunk coordinate. The base domain keeps
 * the lower half (chunks with a split-axis coordinate below the boundary);
 * a fresh half domain owns the upper half. The subdivision is <em>the
 * migration's transfer payload</em>: it installs only inside
 * {@link OwnershipMigration}'s step-4 state move (the {@link
 * MigrationExecutors} own the call sites), never a raw map update — the
 * design's §9 law that a transfer is a protocol, not a map write. The
 * layer is single-level by construction: a subdivided region's base never
 * splits again (the executor's own refusal), and a merge removes the
 * subdivision whole, returning the upper half's chunks to the base. The
 * retired half domain is never re-used — a later re-split mints a fresh
 * name through the partition's own serial, so a scheduler queue or a
 * compute ticket can never alias a retired authority.</p>
 */
public final class RegionPartition {

    private final int regionSizeChunks;
    private final String worldName;
    private final Map<Long, OwnershipDomain> regions = new ConcurrentHashMap<>();
    /**
     * The active per-region subdivisions (Phase 8's routing layer). The map
     * key is the region id; the {@link Subdivision} is immutable once
     * installed. Reads ride {@link #domainFor(int, int)} from any thread;
     * writes happen only inside a migration's transfer step (the executor's
     * contract).
     */
    private final Map<Long, Subdivision> subdivisions = new ConcurrentHashMap<>();
    /** The fresh-half serial: every split ever mints a unique domain name. */
    private final AtomicLong halfSerial = new AtomicLong();

    /**
     * One region's active axis-aligned split (Phase 8's routing payload):
     * the base domain keeps the chunks with a split-axis coordinate below
     * {@code atChunk}; the {@code half} domain owns the rest of the region.
     * Immutable once installed.
     */
    public static final class Subdivision {
        /** The split axis: 0 splits along chunk X, 1 along chunk Z. */
        public static final int AXIS_X = 0;
        public static final int AXIS_Z = 1;

        private final long regionId;
        private final int axis;
        private final int atChunk;
        private final OwnershipDomain half;

        Subdivision(long regionId, int axis, int atChunk, OwnershipDomain half) {
            this.regionId = regionId;
            this.axis = axis;
            this.atChunk = atChunk;
            this.half = half;
        }

        /** @return the subdivided region's packed id. */
        public long regionId() {
            return regionId;
        }

        /** @return the split axis ({@link #AXIS_X} or {@link #AXIS_Z}). */
        public int axis() {
            return axis;
        }

        /** @return the boundary chunk coordinate (base owns coord &lt; atChunk). */
        public int atChunk() {
            return atChunk;
        }

        /** @return the upper half's owning domain (the split's fresh half). */
        public OwnershipDomain half() {
            return half;
        }

        @Override
        public String toString() {
            return "Subdivision[region=" + regionId + " axis=" + (axis == AXIS_X ? 'x' : 'z')
                    + " at=" + atChunk + " half=" + half.name() + "]";
        }
    }

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
     * The ownership domain for the chunk's region — the subdivision-aware
     * answer (Phase 8): a chunk inside an active subdivision's upper half
     * resolves to the half domain, everything else to the region's base.
     * Created on first touch and stable afterwards until a migration moves
     * the boundary — the same object identity the assertions, the
     * scheduler, and the router all key on.
     */
    public OwnershipDomain domainFor(int chunkX, int chunkZ) {
        long id = regionId(chunkX, chunkZ);
        Subdivision subdivision = subdivisions.get(id);
        if (subdivision != null) {
            int coordinate = subdivision.axis == Subdivision.AXIS_X ? chunkX : chunkZ;
            if (coordinate >= subdivision.atChunk) {
                return subdivision.half;
            }
        }
        return baseDomainFor(id);
    }

    /**
     * The region's BASE domain — the whole-region authority the
     * subdivision layer splits (the lower half's owner while a subdivision
     * stands). Pure resolution; never consults the subdivision routing.
     */
    public OwnershipDomain baseDomainFor(long regionId) {
        return regions.computeIfAbsent(regionId, id -> OwnershipDomain.create("region:"
                + worldName + ":" + regionXOf(id) + ":" + regionZOf(id)));
    }

    /** @return whether the given domain is the chunk's region authority. */
    public boolean owns(int chunkX, int chunkZ, OwnershipDomain domain) {
        return domainFor(chunkX, chunkZ) == domain;
    }

    /** @return the region's active subdivision, or null when whole. */
    public Subdivision subdivisionOf(long regionId) {
        return subdivisions.get(regionId);
    }

    /**
     * Mints the split's fresh half domain (the unique-name law: every split
     * ever carries its own serial, so a retired half can never alias a
     * scheduler queue or a compute ticket). Package-private — the
     * {@link MigrationExecutors} own the call.
     */
    OwnershipDomain createHalfDomain(long regionId) {
        long serial = halfSerial.incrementAndGet();
        return OwnershipDomain.create("region:" + worldName + ":"
                + regionXOf(regionId) + ":" + regionZOf(regionId) + "#h" + serial);
    }

    /**
     * Installs the region's subdivision — THE MIGRATION'S TRANSFER PAYLOAD
     * (§9 step 4: the routing change is the state move, and it happens
     * inside the old owner's context, between the drain and the publish).
     * Refuses a second subdivision (the single-level law) and a boundary
     * outside the region's chunk span (an empty half is not a split).
     * Package-private — the executors own the call site.
     */
    void installSubdivision(long regionId, int axis, int atChunk, OwnershipDomain half) {
        java.util.Objects.requireNonNull(half, "half");
        if (axis != Subdivision.AXIS_X && axis != Subdivision.AXIS_Z) {
            throw new IllegalArgumentException("unknown split axis: " + axis);
        }
        int origin = (axis == Subdivision.AXIS_X
                ? regionXOf(regionId) : regionZOf(regionId)) * regionSizeChunks;
        if (atChunk <= origin || atChunk >= origin + regionSizeChunks) {
            throw new IllegalArgumentException("the split boundary must sit inside the region: at="
                    + atChunk + " span=[" + origin + ", " + (origin + regionSizeChunks) + ")");
        }
        if (subdivisions.putIfAbsent(regionId, new Subdivision(regionId, axis, atChunk, half))
                != null) {
            throw new IllegalStateException("region " + regionId
                    + " already carries a subdivision — the layer is single-level");
        }
    }

    /**
     * Removes the region's subdivision (the merge's transfer payload — the
     * same §9 step-4 contract). Refuses a mismatched half: only the
     * subdivision's own half domain retires it. Package-private — the
     * executors own the call site.
     */
    void removeSubdivision(long regionId, OwnershipDomain expectedHalf) {
        java.util.Objects.requireNonNull(expectedHalf, "expectedHalf");
        Subdivision active = subdivisions.get(regionId);
        if (active == null) {
            throw new IllegalStateException("region " + regionId + " carries no subdivision");
        }
        if (active.half() != expectedHalf) {
            throw new IllegalStateException("the merge's half does not own region "
                    + regionId + "'s subdivision: expected " + active.half().name()
                    + ", got " + expectedHalf.name());
        }
        subdivisions.remove(regionId);
    }

    /**
     * @return the region id whose BASE domain answers the name, or null
     * when the name is not a base of this partition (the balancer's
     * decision resolution).
     */
    public Long regionIdOfBase(String baseName) {
        java.util.Objects.requireNonNull(baseName, "baseName");
        for (Map.Entry<Long, OwnershipDomain> entry : regions.entrySet()) {
            if (entry.getValue().name().equals(baseName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * @return the active subdivision whose HALF domain answers the name, or
     * null (the balancer's merge resolution).
     */
    public Subdivision subdivisionOfHalf(String halfName) {
        java.util.Objects.requireNonNull(halfName, "halfName");
        for (Subdivision subdivision : subdivisions.values()) {
            if (subdivision.half().name().equals(halfName)) {
                return subdivision;
            }
        }
        return null;
    }

    /**
     * Every created region domain — the bases and every active
     * subdivision's half — in stable name order, the deterministic walk
     * order the scheduler and the drains ride (design invariant 9).
     */
    public List<OwnershipDomain> domains() {
        List<OwnershipDomain> all = new ArrayList<>(regions.values());
        for (Subdivision subdivision : subdivisions.values()) {
            all.add(subdivision.half());
        }
        all.sort(Comparator.comparing(OwnershipDomain::name));
        return all;
    }

    /**
     * Every domain owning at least one chunk of the chunk box (inclusive
     * corners), in the global (packed-region-id, base-before-half) order —
     * the deterministic total order the boundary scope's enter/exit walk
     * rides (design invariant: one global acquisition order means two
     * concurrent scopes can never deadlock AB-BA; a subdivided region
     * contributes its base and its half as adjacent entries, the base
     * first, so the order stays a prefix of one global sequence whatever
     * the box). Regions surface on first touch, so a box over unloaded
     * space still resolves its domains (the partition is pure arithmetic);
     * a subdivided region contributes only the halves the box actually
     * touches.
     */
    public List<OwnershipDomain> domainsBetween(int chunkX1, int chunkZ1,
                                                int chunkX2, int chunkZ2) {
        int minRx = Math.floorDiv(Math.min(chunkX1, chunkX2), regionSizeChunks);
        int maxRx = Math.floorDiv(Math.max(chunkX1, chunkX2), regionSizeChunks);
        int minRz = Math.floorDiv(Math.min(chunkZ1, chunkZ2), regionSizeChunks);
        int maxRz = Math.floorDiv(Math.max(chunkZ1, chunkZ2), regionSizeChunks);
        List<Long> ids = new ArrayList<>();
        for (int rx = minRx; rx <= maxRx; rx++) {
            for (int rz = minRz; rz <= maxRz; rz++) {
                ids.add(((long) rx << 32) | (rz & 0xFFFFFFFFL));
            }
        }
        java.util.Collections.sort(ids); // the packed id: (rx, rz) lexicographic
        int minChunkX = Math.min(chunkX1, chunkX2);
        int maxChunkX = Math.max(chunkX1, chunkX2);
        int minChunkZ = Math.min(chunkZ1, chunkZ2);
        int maxChunkZ = Math.max(chunkZ1, chunkZ2);
        List<OwnershipDomain> domains = new ArrayList<>(ids.size());
        for (long id : ids) {
            Subdivision subdivision = subdivisions.get(id);
            if (subdivision == null) {
                // The region's own origin chunk resolves the base (pure
                // arithmetic — the same object baseDomainFor would return).
                domains.add(baseDomainFor(id));
                continue;
            }
            // The subdivided region: the base owns the lower half, the
            // fresh half the upper — the box contributes each side it
            // actually touches (a lower-half box never enters the half's
            // authority, an upper-half box never enters the base's).
            if (subdivision.axis == Subdivision.AXIS_X) {
                if (minChunkX < subdivision.atChunk) {
                    domains.add(baseDomainFor(id));
                }
                if (maxChunkX >= subdivision.atChunk) {
                    domains.add(subdivision.half());
                }
            } else {
                if (minChunkZ < subdivision.atChunk) {
                    domains.add(baseDomainFor(id));
                }
                if (maxChunkZ >= subdivision.atChunk) {
                    domains.add(subdivision.half());
                }
            }
        }
        return domains;
    }

    /** @return how many region domains exist (telemetry). */
    public int regionCount() {
        return regions.size();
    }

    /** @return how many active subdivisions exist (the balancer's telemetry). */
    public int subdivisionCount() {
        return subdivisions.size();
    }
}
