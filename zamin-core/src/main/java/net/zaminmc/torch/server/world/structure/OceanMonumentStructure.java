package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.server.world.Biome;

import java.util.Random;

/**
 * The ocean monument placement (Slice 12e — the reference port of
 * gen/structure/OceanMonumentStructure.java + the Generator/StructureFeature
 * drivers): the per-chunk region scan with the reference's own seed
 * derivation.
 *
 * <p><b>The placement math (isFeatureChunk lines 43-64, 1:1):</b> the
 * spacing/separation pair (32/5 — 16-chunk-biome-cell spacing), the
 * negative-coordinate floor walk, the {@code setRandomSeed(k, l, 10387313)}
 * derivation, and the pick offset — the reference's own TWO-roll average
 * {@code (nextInt(spacing - separation) + nextInt(spacing - separation)) / 2}
 * per axis. The pick chunk then faces the biome gates: the center
 * (the chunk's +8) must be DEEP_OCEAN, and every biome cell in the ±29
 * window must be one of the water family (the reference's isBiomeWithin
 * — the engine carries OCEAN + DEEP_OCEAN; the frozen pair and the river
 * absent, the envelope note).</p>
 *
 * <p><b>The start (Start lines 89-113):</b> the reference's own seed
 * derivation — two longs drawn from the world seed, the start random
 * seeded {@code chunkX * firstLong ^ chunkZ * secondLong ^ worldSeed}
 * — then the 58x58 monument piece at the {@code -29} anchor (the
 * structure's own origin), the random horizontal facing, the layout
 * graph, and the bounds union. No vertical re-base: the monument's y band
 * is fixed (39..61) exactly like the reference.</p>
 *
 * <p><b>The engine's deterministic contract</b> (the same envelope as
 * every structure slice): starts recompute per chunk from pure (seed,
 * region) inputs — no cross-chunk cache, no shared mutable statics, the
 * parallel chunk pipeline stays lock-free. The reference's
 * {@code placedMonuments} per-chunk dedup set is subsumed by the engine's
 * one-populate-per-chunk model (each chunk's postProcess writes exactly
 * once — the ledger's envelope note). The per-chunk postProcess random
 * seeds with the reference's own populate pair
 * {@code chunkX * 341873128712L + chunkZ * 132897987541L}.</p>
 */
public final class OceanMonumentStructure {

    /** The reference's spacing/separation pair (lines 16-17). */
    static final int SPACING = 32;
    static final int SEPARATION = 5;

    /** The region window (Generator's own range field). */
    static final int RANGE = 8;

    private final long worldSeed;

    public OceanMonumentStructure(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    /**
     * The feature gate (isFeatureChunk lines 43-64, 1:1): the
     * negative-coordinate floor walk over the spacing grid, the
     * setRandomSeed(10387313) derivation, the two-roll averaged pick, and
     * the biome gates — the center must be DEEP_OCEAN and the ±29 window
     * all water family (the reference's isBiomeWithin).
     */
    private boolean isFeatureChunk(int chunkX, int chunkZ, StructureWorld world) {
        long pick = pickOfCell(cellOf(chunkX), cellOf(chunkZ));
        if (chunkX != unpackX(pick) || chunkZ != unpackZ(pick)) {
            return false;
        }
        if (world.biomeAt(chunkX * 16 + 8, chunkZ * 16 + 8) != Biome.DEEP_OCEAN) {
            return false;
        }
        return isBiomeWithin(world, chunkX * 16 + 8, chunkZ * 16 + 8, 29);
    }

    /** @return the spacing-cell coordinate of a chunk (the negative-aware floor). */
    private static int cellOf(int chunk) {
        int floor = chunk;
        if (chunk < 0) {
            floor -= SPACING - 1;
        }
        return floor / SPACING;
    }

    /**
     * The cell's own pick chunk (isFeatureChunk's seed walk, lines 71-81):
     * {@code setRandomSeed(k, l, 10387313)} then the reference's own
     * two-roll average {@code (nextInt(spacing - separation) +
     * nextInt(spacing - separation)) / 2} per axis — deterministic in
     * (cellX, cellZ, worldSeed) alone.
     */
    private long pickOfCell(int cellX, int cellZ) {
        Random random = new Random(cellX * 341873128712L + cellZ * 132897987541L
                + this.worldSeed + 10387313L);
        int pickX = cellX * SPACING
                + (random.nextInt(SPACING - SEPARATION) + random.nextInt(SPACING - SEPARATION)) / 2;
        int pickZ = cellZ * SPACING
                + (random.nextInt(SPACING - SEPARATION) + random.nextInt(SPACING - SEPARATION)) / 2;
        return ((long) pickX << 32) | (pickZ & 0xFFFFFFFFL);
    }

    /** @return the packed pick's x chunk. */
    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    /** @return the packed pick's z chunk. */
    private static int unpackZ(long packed) {
        return (int) packed;
    }

    /**
     * The reference's isInsideBounds walk (StructureFeature lines 98-107,
     * over the loaded starts — Slice 17a): whether a monument start's
     * bounds contain the position, the spawn entries' monument arm. The
     * engine's deterministic stand-in: the nine surrounding 32-chunk
     * biome cells each derive their own pick chunk (the same seed walk
     * isFeatureChunk runs), the horizontal 58-block box filter running
     * before the biome gates so the common no-monument cell costs two
     * longs.
     */
    public boolean insideBounds(StructureWorld world, int x, int y, int z) {
        int cellX = cellOf((int) Math.floor((double) x / (SPACING * 16.0)));
        int cellZ = cellOf((int) Math.floor((double) z / (SPACING * 16.0)));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long pick = pickOfCell(cellX + dx, cellZ + dz);
                int pickX = unpackX(pick);
                int pickZ = unpackZ(pick);
                // The 58-block piece anchored at the -29 offset: the pick
                // chunk's own [16*pick-21, 16*pick+36] window.
                if (x < pickX * 16 - 21 || x > pickX * 16 + 36) {
                    continue;
                }
                if (z < pickZ * 16 - 21 || z > pickZ * 16 + 36) {
                    continue;
                }
                StructureStart start = createStart(pickX, pickZ, world);
                if (start != null && start.getBounds() != null
                        && start.getBounds().contains(x, y, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The water-family window (BiomeSource.isBiomeWithin lines 158-179,
     * 1:1): every biome cell in the ±range window at 4-block steps must be
     * one of the valid set — the engine's {OCEAN, DEEP_OCEAN} (the frozen
     * pair + the river absent, the envelope note).
     */
    private static boolean isBiomeWithin(StructureWorld world, int x, int z, int range) {
        int minX = x - range >> 2;
        int minZ = z - range >> 2;
        int maxX = x + range >> 2;
        int maxZ = z + range >> 2;
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        for (int i = 0; i < spanX * spanZ; i++) {
            int cellX = minX + i % spanX << 2;
            int cellZ = minZ + i / spanX << 2;
            Biome biome = world.biomeAt(cellX, cellZ);
            if (biome != Biome.OCEAN && biome != Biome.DEEP_OCEAN) {
                return false;
            }
        }
        return true;
    }

    /**
     * The region's start, or null when the region carries no monument.
     * Deterministic in (regionX, regionZ, worldSeed) alone.
     */
    StructureStart createStart(int regionX, int regionZ, StructureWorld world) {
        if (!isFeatureChunk(regionX, regionZ, world)) {
            return null;
        }
        return new OceanMonumentStructureStart(this.worldSeed, regionX, regionZ);
    }

    /** The monument start (Start lines 89-113): the seed walk + the piece. */
    static final class OceanMonumentStructureStart extends StructureStart {

        OceanMonumentStructureStart(long worldSeed, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            Random random = new Random(worldSeed);
            long firstLong = random.nextLong();
            long secondLong = random.nextLong();
            random.setSeed(chunkX * firstLong ^ chunkZ * secondLong ^ worldSeed);
            int anchorX = chunkX * 16 + 8 - 29;
            int anchorZ = chunkZ * 16 + 8 - 29;
            StructureFacing facing = StructureFacing.pickHorizontal(random);
            this.pieces.add(new OceanMonumentPieces.OceanMonument(random, anchorX, anchorZ, facing));
            this.findBounds();
        }
    }

    /**
     * The per-chunk structure pass (the reference's populate hook: the
     * ±8 region scan, then the postProcess of every start whose pieces
     * touch the chunk's clip box — the 4-field clip keeps the reference's
     * own minY 1 / maxY 512 shape).
     */
    public void populate(int chunkX, int chunkZ, StructureWorld world) {
        StructureBox clip = new StructureBox(chunkX << 4, chunkZ << 4,
                (chunkX << 4) + 15, (chunkZ << 4) + 15);
        Random postRandom = new Random(chunkX * 341873128712L + chunkZ * 132897987541L);
        for (int regionX = chunkX - RANGE; regionX <= chunkX + RANGE; regionX++) {
            for (int regionZ = chunkZ - RANGE; regionZ <= chunkZ + RANGE; regionZ++) {
                StructureStart start = createStart(regionX, regionZ, world);
                if (start == null) {
                    continue;
                }
                if (start.getBounds() != null && start.getBounds().intersects(clip)) {
                    start.postProcess(world, postRandom, clip);
                }
            }
        }
    }
}
