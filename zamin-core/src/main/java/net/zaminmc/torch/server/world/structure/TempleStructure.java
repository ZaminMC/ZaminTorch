package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.server.world.Biome;

import java.util.Random;

/**
 * The temple placement (Slice 12f — the reference port of
 * gen/structure/TempleStructure.java + the Generator/StructureFeature
 * drivers): the per-chunk region scan with the reference's own seed
 * derivation.
 *
 * <p><b>The placement math (isFeatureChunk lines 45-67, 1:1):</b> the
 * distance/minDistance pair (32/8 — the same grid as the village), the
 * negative-coordinate floor walk, the {@code setRandomSeed(k, l, 14357617)}
 * derivation, and the pick offset — the temple's own
 * {@code nextInt(distance - minDistance)} pair per axis (one roll per
 * axis, NOT the village's burned-draw order — the reference's own
 * quirk). The pick chunk then faces the biome gate: the chunk's +8
 * center must be one of the valid family — the engine's {DESERT,
 * JUNGLE} (the desert hills + the jungle hills + the swampland absent,
 * the envelope note).</p>
 *
 * <p><b>The start (Start lines 85-104):</b> the biome dispatch — the
 * jungle temple (JUNGLE), the witch hut (SWAMPLAND — never fires, no
 * swampland), the desert pyramid (DESERT) — each at the chunk's own
 * origin ({@code chunkX * 16, chunkZ * 16} — no +2 corner, the
 * reference's own anchor), the random facing inside the piece, and the
 * bounds union. No vertical re-base (the pieces carry their own y-64
 * anchor + the updateHPos re-base).</p>
 *
 * <p><b>The engine's deterministic contract</b> (the same envelope as
 * every structure slice): starts recompute per chunk from pure (seed,
 * region) inputs. The per-chunk postProcess random seeds with the
 * reference's own populate pair
 * {@code chunkX * 341873128712L + chunkZ * 132897987541L}.</p>
 */
public final class TempleStructure {

    /** The reference's distance/minDistance pair (lines 19-20). */
    static final int DISTANCE = 32;
    static final int MIN_DISTANCE = 8;

    /** The region window (Generator's own range field). */
    static final int RANGE = 8;

    /** The two longs the region seeds fold (drawn once from the seed). */
    private final long firstLong;
    private final long secondLong;
    private final long worldSeed;

    public TempleStructure(long worldSeed) {
        Random random = new Random(worldSeed);
        this.firstLong = random.nextLong();
        this.secondLong = random.nextLong();
        this.worldSeed = worldSeed;
    }

    /**
     * The feature gate (isFeatureChunk lines 45-67, 1:1): the
     * negative-coordinate floor walk over the distance grid, the
     * setRandomSeed(14357617) derivation, the pick pair and the biome
     * gate at the chunk's +8 center.
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
        // world.setRandomSeed(k, l, 14357617) — World.java lines 2637-2641.
        Random random = new Random(cellX * 341873128712L + cellZ * 132897987541L
                + this.worldSeed + 14357617L);
        int pickX = cellX * DISTANCE + random.nextInt(DISTANCE - MIN_DISTANCE);
        int pickZ = cellZ * DISTANCE + random.nextInt(DISTANCE - MIN_DISTANCE);
        if (chunkX != pickX || chunkZ != pickZ) {
            return false;
        }
        Biome biome = world.biomeAt(chunkX * 16 + 8, chunkZ * 16 + 8);
        // The engine's valid family: the desert + the jungle (the hills
        // pair + the swampland absent — the envelope note).
        return biome == Biome.DESERT || biome == Biome.JUNGLE;
    }

    /**
     * The region's start, or null when the region carries no temple.
     * Deterministic in (regionX, regionZ, worldSeed) alone.
     */
    StructureStart createStart(int regionX, int regionZ, StructureWorld world) {
        if (!isFeatureChunk(regionX, regionZ, world)) {
            return null;
        }
        // The StructureFeature's own region random (the burned draw after
        // the seed fold) — the piece's constructor random (the reference's
        // createStructure receives this.random).
        Random random = new Random(regionX * this.firstLong ^ regionZ * this.secondLong
                ^ this.worldSeed);
        random.nextInt(); // StructureFeature.place's own burned draw
        return new TempleStructureStart(random, regionX, regionZ, world);
    }

    /** The temple start (Start lines 85-104): the biome dispatch. */
    static final class TempleStructureStart extends StructureStart {

        TempleStructureStart(Random random, int chunkX, int chunkZ, StructureWorld world) {
            super(chunkX, chunkZ);
            Biome biome = world.biomeAt(chunkX * 16 + 8, chunkZ * 16 + 8);
            if (biome == Biome.JUNGLE) {
                this.pieces.add(new TemplePieces.JungleTemple(random, chunkX * 16, chunkZ * 16));
            } else if (biome == Biome.DESERT) {
                this.pieces.add(new TemplePieces.DesertPyramid(random, chunkX * 16, chunkZ * 16));
            }
            // The swampland arm (the witch hut) never fires — the engine
            // carries no swampland biome (the ledger's envelope note).
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
