package net.zaminmc.torch.server.world.structure;

import java.util.List;
import java.util.Random;

/**
 * The village placement (Slice 12b — the reference port of
 * gen/structure/VillageStructure.java + the Generator/StructureFeature
 * drivers): the per-chunk region scan with the reference's own seed
 * derivations.
 *
 * <p><b>The placement math (VillageStructure.isFeatureChunk lines
 * 40-68 + Generator.place + StructureFeature.place, 1:1):</b> every
 * region chunk in the ±8 window seeds its own {@code Random} with
 * {@code regionX * firstLong ^ regionZ * secondLong ^ worldSeed} (the
 * two longs drawn once from the world seed) and burns one draw. The
 * village's own gate then derives its grid cell — the negative-coordinate
 * floor walk {@code (chunk - 31) / 32} — and rolls a SECOND random
 * seeded {@code cellX * 341873128712L + cellZ * 132897987541L +
 * worldSeed + 10387312} (the reference's {@code world.setRandomSeed(k,
 * l, 10387312)}): the cell's pick chunk is {@code cellX * 32 +
 * nextInt(24)} / {@code cellZ * 32 + nextInt(24)} — the region hits only
 * when it IS that pick, then the biome gate at the chunk's +8 center
 * answers the last word (every sampled 4-block cell must be plains or
 * desert; the engine carries no savanna — the ledger's envelope note).</p>
 *
 * <p><b>The start (VillageStructure.Start lines 75-132 + VillagePieces
 * lines 1083-1121):</b> the piece-weight table from the region random
 * (post-burn), the well at the region's {@code +2, +2} corner, the
 * road/building queue drain until both empty, the bounds union, and the
 * valid gate — more than two non-road pieces or the village never
 * writes.</p>
 *
 * <p><b>The engine's deterministic contract:</b> starts recompute per
 * chunk from pure (seed, region) inputs — no cross-chunk cache, so the
 * parallel chunk pipeline stays lock-free (the same envelope as the
 * mineshaft slice). The per-chunk postProcess random seeds with the
 * reference's own populate pair
 * {@code chunkX * 341873128712L + chunkZ * 132897987541L} (no world-seed
 * fold — the reference's own quirk).</p>
 */
public final class VillageStructure {

    /** The reference's distance / min-distance pair (lines 16-17). */
    static final int DISTANCE = 32;
    static final int MIN_DISTANCE = 8;

    /** The two longs the region seeds fold (drawn once from the seed). */
    private final long firstLong;
    private final long secondLong;
    private final long worldSeed;

    /** The region window (Generator's own range field). */
    static final int RANGE = 8;

    public VillageStructure(long worldSeed) {
        Random random = new Random(worldSeed);
        this.firstLong = random.nextLong();
        this.secondLong = random.nextLong();
        this.worldSeed = worldSeed;
    }

    /**
     * The village's feature gate (isFeatureChunk lines 40-68, 1:1): the
     * grid-cell derivation with the negative-coordinate floor walk, the
     * setRandomSeed(10387312) offset pair, the pick-chunk identity and
     * the biome gate at the chunk's center.
     */
    private boolean isFeatureChunk(int chunkX, int chunkZ, StructureWorld world) {
        int floorX = chunkX;
        int floorZ = chunkZ;
        if (chunkX < 0) {
            floorX -= DISTANCE - 1;
        }
        if (chunkZ < 0) {
            floorZ -= DISTANCE - 1;
        }
        int cellX = floorX / DISTANCE;
        int cellZ = floorZ / DISTANCE;
        // world.setRandomSeed(k, l, 10387312) — World.java lines 2637-2641.
        Random gateRandom = new Random(cellX * 341873128712L + cellZ * 132897987541L
                + this.worldSeed + 10387312L);
        int pickX = cellX * DISTANCE + gateRandom.nextInt(DISTANCE - MIN_DISTANCE);
        int pickZ = cellZ * DISTANCE + gateRandom.nextInt(DISTANCE - MIN_DISTANCE);
        if (chunkX != pickX || chunkZ != pickZ) {
            return false;
        }
        return VillagePieces.biomeWithin(world, chunkX * 16 + 8, chunkZ * 16 + 8, 0);
    }

    /**
     * The region's start, or null when the region carries no village.
     * Deterministic in (regionX, regionZ, worldSeed) alone.
     */
    StructureStart createStart(int regionX, int regionZ, StructureWorld world) {
        Random regionRandom = new Random(regionX * this.firstLong ^ regionZ * this.secondLong
                ^ this.worldSeed);
        regionRandom.nextInt(); // StructureFeature.place's own burned draw
        if (!isFeatureChunk(regionX, regionZ, world)) {
            return null;
        }
        return new VillageStructureStart(regionRandom, regionX, regionZ, world);
    }

    /** The village start (VillageStructure.Start): the walk + the valid gate. */
    static final class VillageStructureStart extends StructureStart {
        private final boolean valid;

        VillageStructureStart(Random random, int chunkX, int chunkZ, StructureWorld world) {
            super(chunkX, chunkZ);
            List<VillagePieces.VillagePieceWeight> weights =
                    VillagePieces.getPieceWeights(random, 0);
            VillagePieces.VillageStart start = new VillagePieces.VillageStart(
                    world, 0, random, (chunkX << 4) + 2, (chunkZ << 4) + 2, weights, 0);
            this.pieces.add(start);
            start.addChildren(start, this.pieces, random);
            List<StructurePiece> roadPieces = start.roadPieces;
            List<StructurePiece> buildingPieces = start.buildingPieces;
            while (!roadPieces.isEmpty() || !buildingPieces.isEmpty()) {
                if (roadPieces.isEmpty()) {
                    int i = random.nextInt(buildingPieces.size());
                    StructurePiece piece = buildingPieces.remove(i);
                    piece.addChildren(start, this.pieces, random);
                } else {
                    int j = random.nextInt(roadPieces.size());
                    StructurePiece piece = roadPieces.remove(j);
                    piece.addChildren(start, this.pieces, random);
                }
            }
            this.findBounds();
            int buildingCount = 0;
            for (StructurePiece piece : this.pieces) {
                if (!(piece instanceof VillagePieces.RoadPiece)) {
                    buildingCount++;
                }
            }
            this.valid = buildingCount > 2;
        }

        /** The reference's own valid gate (the Start's isValid override). */
        public boolean isValid() {
            return this.valid;
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
