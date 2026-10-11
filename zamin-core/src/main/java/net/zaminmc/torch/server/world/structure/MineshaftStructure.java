package net.zaminmc.torch.server.world.structure;

import java.util.Random;

/**
 * The mineshaft placement (Slice 12a — the reference port of
 * gen/structure/MineshaftStructure.java + MineshaftStart.java + the
 * Generator/StructureFeature drivers): the per-chunk region scan with
 * the reference's own seed derivation.
 *
 * <p><b>The placement math (Generator.place lines 13-28 + StructureFeature
 * lines 27-60, 1:1):</b> two longs drawn once from the world seed; every
 * region chunk in the ±8 window seeds its own {@code Random} with
 * {@code regionX * firstLong ^ regionZ * secondLong ^ worldSeed}, burns
 * one draw, then rolls the feature gate — the 0.004 chance AND the
 * {@code nextInt(80) < max(|x|, |z|)} distance-from-spawn rule (the
 * reference's own "not near spawn" gate).</p>
 *
 * <p><b>The start (MineshaftStart lines 10-17):</b> the Room at the
 * region's {@code +2, +2} corner, the recursive piece walk, the bounds
 * union, the moveBelowSeaLevel(10) re-base.</p>
 *
 * <p><b>The engine's deterministic contract:</b> starts recompute per
 * chunk from pure (seed, region) inputs — no cross-chunk cache, so the
 * parallel chunk pipeline stays lock-free; the reference's
 * load-order-dependent piece removal stays per-chunk instead (the
 * ledger's envelope note). The per-chunk postProcess random seeds with
 * the reference's own populate pair
 * {@code chunkX * 341873128712L + chunkZ * 132897987541L} (no world-seed
 * fold — the reference's own quirk, OverworldChunkGenerator line
 * 172).</p>
 */
public final class MineshaftStructure {

    /** The reference's chance (MineshaftStructure line 8). */
    static final double SPAWN_CHANCE = 0.004;

    /** The two longs the region seeds fold (drawn once from the seed). */
    private final long firstLong;
    private final long secondLong;
    private final long worldSeed;

    /** The region window (Generator's own range field). */
    static final int RANGE = 8;

    public MineshaftStructure(long worldSeed) {
        Random random = new Random(worldSeed);
        this.firstLong = random.nextLong();
        this.secondLong = random.nextLong();
        this.worldSeed = worldSeed;
    }

    /**
     * The feature gate (isFeatureChunk line 27-29): the 0.004 chance AND
     * the nextInt(80) &lt; max(|x|, |z|) distance rule. The reference's
     * random consumes exactly these draws in this order (after the one
     * burned draw in StructureFeature.place).
     */
    private boolean isFeatureChunk(Random random, int chunkX, int chunkZ) {
        return random.nextDouble() < SPAWN_CHANCE
                && random.nextInt(80) < Math.max(Math.abs(chunkX), Math.abs(chunkZ));
    }

    /**
     * The region's start, or null when the region carries no mineshaft.
     * Deterministic in (regionX, regionZ, worldSeed) alone.
     */
    StructureStart createStart(int regionX, int regionZ, StructureWorld world) {
        Random random = new Random(regionX * this.firstLong ^ regionZ * this.secondLong
                ^ this.worldSeed);
        random.nextInt(); // StructureFeature.place's own burned draw
        if (!isFeatureChunk(random, regionX, regionZ)) {
            return null;
        }
        return new MineshaftStart(random, regionX, regionZ, world);
    }

    /** The start (MineshaftStart): the room, the walk, the re-base. */
    static final class MineshaftStart extends StructureStart {
        MineshaftStart(Random random, int chunkX, int chunkZ, StructureWorld world) {
            super(chunkX, chunkZ);
            MineshaftPieces.Room room = new MineshaftPieces.Room(0, random,
                    (chunkX << 4) + 2, (chunkZ << 4) + 2);
            this.pieces.add(room);
            room.addChildren(room, this.pieces, random);
            this.findBounds();
            this.moveBelowSeaLevel(world, random, 10);
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
