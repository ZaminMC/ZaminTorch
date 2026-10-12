package net.zaminmc.torch.server.world.structure;

import java.util.List;
import java.util.Random;

/**
 * The fortress placement (Slice 12d — the reference port of
 * gen/structure/FortressStructure.java + the Generator/StructureFeature
 * drivers): the per-chunk region scan with the reference's own seed
 * derivation.
 *
 * <p><b>The placement math (isFeatureChunk lines 41-47, 1:1):</b> the
 * region's cell is {@code (chunkX >> 4, chunkZ >> 4)} (16 chunks per
 * cell); the random seeds {@code cellX ^ (cellZ << 4) ^ worldSeed}
 * (the reference's own precedence), burns one draw, then the
 * {@code nextInt(3) == 0} gate AND the pick identity — the chunk must BE
 * {@code (cellX << 4) + 4 + nextInt(8)} on BOTH axes (the two nextInt(8)
 * rolls short-circuit: the z roll only fires when the x check passes).</p>
 *
 * <p><b>The start (Start lines 58-77 + FortressPieces.Start lines
 * 1382-1414):</b> the 19x19 BridgeCrossing shape at the pick chunk's own
 * origin (the random facing, the y-64 anchor), the children queue drained
 * in random order, the bounds union, and the
 * {@code moveBetweenYCoords(48, 70)} band re-base — the fortress's own
 * re-base (the reference's StructureStart.moveBetweenYCoords).</p>
 *
 * <p><b>The engine's deterministic contract</b> (the mineshaft/village/
 * stronghold envelope): starts recompute per chunk from pure (seed,
 * region) inputs — no cross-chunk cache, no shared mutable statics, the
 * parallel chunk pipeline stays lock-free. The per-chunk postProcess
 * random seeds with the reference's own populate pair
 * {@code chunkX * 341873128712L + chunkZ * 132897987541L} (no world-seed
 * fold — the reference's own quirk).</p>
 */
public final class FortressStructure {

    /** The region window (Generator's own range field). */
    static final int RANGE = 8;

    private final long worldSeed;

    public FortressStructure(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    /**
     * The feature gate (isFeatureChunk lines 41-47, 1:1): the cell's own
     * seed derivation, the burned draw, the 1-in-3 gate and the pick
     * identity on both axes.
     */
    private boolean isFeatureChunk(Random random, int chunkX, int chunkZ) {
        int cellX = chunkX >> 4;
        int cellZ = chunkZ >> 4;
        random.setSeed(cellX ^ (cellZ << 4) ^ this.worldSeed);
        random.nextInt(); // StructureFeature.place's own burned draw
        return random.nextInt(3) == 0
                && chunkX == (cellX << 4) + 4 + random.nextInt(8)
                && chunkZ == (cellZ << 4) + 4 + random.nextInt(8);
    }

    /**
     * The region's start, or null when the region carries no fortress.
     * Deterministic in (regionX, regionZ, worldSeed) alone.
     */
    StructureStart createStart(int regionX, int regionZ, StructureWorld world) {
        Random random = new Random();
        if (!isFeatureChunk(random, regionX, regionZ)) {
            return null;
        }
        return new FortressStructureStart(random, regionX, regionZ, world);
    }

    /** The fortress start (FortressStructure.Start): the walk + the band re-base. */
    static final class FortressStructureStart extends StructureStart {

        FortressStructureStart(Random random, int chunkX, int chunkZ, StructureWorld world) {
            super(chunkX, chunkZ);
            // The reference's own anchor: the +2 corner of the pick chunk.
            FortressPieces.FortressStart start = new FortressPieces.FortressStart(
                    random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
            this.pieces.add(start);
            start.addChildren(start, this.pieces, random);
            List<StructurePiece> children = start.children;
            while (!children.isEmpty()) {
                int i = random.nextInt(children.size());
                StructurePiece piece = children.remove(i);
                piece.addChildren(start, this.pieces, random);
            }
            this.findBounds();
            this.moveBetweenYCoords(random, 48, 70);
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
