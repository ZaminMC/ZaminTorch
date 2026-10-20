package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.server.world.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The stronghold placement (Slice 12c — the reference port of
 * gen/structure/StrongholdStructure.java + the Generator/StructureFeature
 * drivers): the three-world-positions table and the per-chunk region scan.
 *
 * <p><b>The positions (isFeatureChunk lines 51-77, 1:1):</b> drawn once
 * from the world seed — the spiral walk from a random angle: each ring's
 * distance {@code (1.25 * i + nextDouble()) * distance * i} around the
 * angle, the position snapped to a valid biome through the reference's
 * findBiome reservoir scan (the ±112-block window at 4-block steps, every
 * valid cell kept with the {@code nextInt(k+1) == 0} uniform pick), then
 * the angle advances {@code 2π * i / spread} and the ring grows at the
 * spread boundary ({@code i += 2 + nextInt(5)}, {@code spread += 1 +
 * nextInt(2)}). The reference's valid-biome set is every biome with
 * {@code baseHeight > 0} — the engine's three overworld biomes all
 * qualify (the plains 0.125, the forest, the desert).</p>
 *
 * <p><b>The start (StrongholdStructure.Start lines 112-133 + the
 * createStructure retry loop lines 101-110):</b> the weights reset, the
 * source staircase at the position chunk's {@code +2, +2} corner, the
 * children queue drained in random order, the bounds union, the
 * moveBelowSeaLevel(10) re-base — regenerated until the walk leaves
 * pieces AND an end portal room (the reference's own retry gate).</p>
 *
 * <p><b>The engine's deterministic contract</b> (the same envelope as the
 * mineshaft and the village): the positions derive from the world seed
 * alone (computed once at construction); the starts recompute per chunk
 * from pure (seed, region) inputs — no cross-chunk cache, no shared
 * mutable statics, the parallel chunk pipeline stays lock-free. The
 * per-chunk postProcess random seeds with the reference's own populate
 * pair {@code chunkX * 341873128712L + chunkZ * 132897987541L} (no
 * world-seed fold — the reference's own quirk).</p>
 */
public final class StrongholdStructure {

    /** The reference's own defaults (lines 17-19): 3 positions, 32 distance, 3 spread. */
    private static final int POSITION_COUNT = 3;
    private static final double DISTANCE = 32.0;
    private static final int SPREAD = 3;

    /** The two longs the region seeds fold (drawn once from the seed). */
    private final long firstLong;
    private final long secondLong;
    private final long worldSeed;

    /** The three position chunks (the reference's own positions table). */
    private final int[][] positions;

    /** The region window (Generator's own range field). */
    static final int RANGE = 8;

    public StrongholdStructure(long worldSeed, StructureWorld world) {
        Random random = new Random(worldSeed);
        this.firstLong = random.nextLong();
        this.secondLong = random.nextLong();
        this.worldSeed = worldSeed;
        this.positions = findPositions(worldSeed, world);
    }

    /**
     * The positions walk (isFeatureChunk's one-time block, lines 52-76,
     * 1:1): the spiral ring walk with the biome snap.
     */
    private static int[][] findPositions(long worldSeed, StructureWorld world) {
        Random random = new Random();
        random.setSeed(worldSeed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int ring = 1;
        int spread = SPREAD;
        int[][] found = new int[POSITION_COUNT][];

        for (int i = 0; i < POSITION_COUNT; i++) {
            double ringDistance = (1.25 * ring + random.nextDouble()) * (DISTANCE * ring);
            int chunkX = (int) Math.round(Math.cos(angle) * ringDistance);
            int chunkZ = (int) Math.round(Math.sin(angle) * ringDistance);
            // The biome snap (findBiome lines 177-196): the ±112 window
            // at 4-block steps, the reservoir pick among the valid cells.
            int[] snapped = findBiome(world, (chunkX << 4) + 8, (chunkZ << 4) + 8, 112, random);
            if (snapped != null) {
                chunkX = snapped[0] >> 4;
                chunkZ = snapped[1] >> 4;
            }
            found[i] = new int[]{chunkX, chunkZ};
            angle += (Math.PI * 2) * ring / spread;
            if (i == spread) {
                ring += 2 + random.nextInt(5);
                spread = spread + 1 + random.nextInt(2);
            }
        }
        return found;
    }

    /**
     * The reference's BiomeSource.findBiome (lines 177-196, 1:1): the
     * scan over the {@code range} window at 4-block steps — every valid
     * cell kept with the reservoir rule ({@code nextInt(k + 1) == 0}),
     * answering the last kept cell's block position. The engine's valid
     * set is every overworld biome (the baseHeight &gt; 0 gate — all three
     * qualify).
     */
    private static int[] findBiome(StructureWorld world, int x, int z, int range, Random random) {
        int minX = x - range >> 2;
        int minZ = z - range >> 2;
        int maxX = x + range >> 2;
        int maxZ = z + range >> 2;
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        int foundX = 0;
        int foundZ = 0;
        int kept = 0;
        for (int i = 0; i < spanX * spanZ; i++) {
            int cellX = minX + i % spanX << 2;
            int cellZ = minZ + i / spanX << 2;
            Biome biome = world.biomeAt(cellX, cellZ);
            if (isValidBiome(biome) && (kept == 0 || random.nextInt(kept + 1) == 0)) {
                foundX = cellX;
                foundZ = cellZ;
                kept++;
            }
        }
        return kept > 0 ? new int[]{foundX, foundZ} : null;
    }

    /** The baseHeight &gt; 0 gate (the engine's three overworld biomes qualify). */
    private static boolean isValidBiome(Biome biome) {
        return biome == Biome.PLAINS || biome == Biome.FOREST || biome == Biome.DESERT;
    }

    /**
     * The feature gate (isFeatureChunk lines 79-85): the chunk must BE
     * one of the three positions.
     */
    private boolean isFeatureChunk(int chunkX, int chunkZ) {
        for (int[] position : this.positions) {
            if (chunkX == position[0] && chunkZ == position[1]) {
                return true;
            }
        }
        return false;
    }

    /** The three position chunks (diagnostics + tests). */
    List<int[]> positions() {
        return List.of(this.positions);
    }

    /**
     * The raw position table (the eye's target consult — the import-families
     * slice): the three chunk positions in seed order, package-external
     * consumers read it through the generator's own nearest consult.
     */
    public int[][] positionTable() {
        return this.positions;
    }

    /**
     * The region's start, or null when the region carries no stronghold.
     * Deterministic in (regionX, regionZ, worldSeed) alone.
     */
    StructureStart createStart(int regionX, int regionZ, StructureWorld world) {
        if (!isFeatureChunk(regionX, regionZ)) {
            return null;
        }
        Random random = new Random(regionX * this.firstLong ^ regionZ * this.secondLong
                ^ this.worldSeed);
        random.nextInt(); // StructureFeature.place's own burned draw
        // createStructure's retry loop (lines 101-110): regenerate until
        // the walk leaves pieces AND an end portal room.
        StrongholdStructureStart start = new StrongholdStructureStart(random, regionX, regionZ, world);
        while (start.getPieces().isEmpty() || start.endPortalRoom() == null) {
            start = new StrongholdStructureStart(random, regionX, regionZ, world);
        }
        return start;
    }

    /** The stronghold start (StrongholdStructure.Start): the walk + the retry gate. */
    static final class StrongholdStructureStart extends StructureStart {
        private StrongholdPieces.StrongholdStart startPiece;

        StrongholdStructureStart(Random random, int chunkX, int chunkZ, StructureWorld world) {
            super(chunkX, chunkZ);
            StrongholdPieces.StrongholdStart start = new StrongholdPieces.StrongholdStart(
                    0, random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
            this.startPiece = start;
            this.pieces.add(start);
            start.addChildren(start, this.pieces, random);
            List<StructurePiece> children = start.children;
            while (!children.isEmpty()) {
                int i = random.nextInt(children.size());
                StructurePiece piece = children.remove(i);
                piece.addChildren(start, this.pieces, random);
            }
            this.findBounds();
            this.moveBelowSeaLevel(world, random, 10);
        }

        /** The end-portal-room pointer (the retry gate reads it). */
        StrongholdPieces.EndPortalRoom endPortalRoom() {
            return this.startPiece != null ? this.startPiece.endPortalRoom : null;
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
