package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import java.util.Objects;

/**
 * The full-world terrain generator (the historical "default" level-type):
 * value-noise heightmap with seas, beaches, dirt skins, stone cores, noise
 * caves, the depth-banded ore ladder, and oak trees on the grass.
 *
 * <p>Determinism contract (the same one FlatWorldGenerator keeps): the seed
 * is fixed per world name, so every boot rebuilds identical terrain without
 * storing generated chunks. Adopted in shape from the community independent
 * 1.8 server implementations (BlueDragonMC/Server, credited in
 * COMMUNITY_REFERENCES.md); the noise core is our own.</p>
 */
public final class NormalWorldGenerator implements WorldGenerator {

    /** The historical sea level: water fills every column up to here. */
    public static final int SEA_LEVEL = 62;

    private final BlockRegistry registry;
    private final long seed;

    private final BlockType air;
    private final BlockType bedrock;
    private final BlockType stone;
    private final BlockType dirt;
    private final BlockType grass;
    private final BlockType sand;
    private final BlockType gravel;
    private final BlockType water;
    private final BlockType coalOre;
    private final BlockType ironOre;
    private final BlockType goldOre;
    private final BlockType redstoneOre;
    private final BlockType diamondOre;
    private final BlockType oakLog;
    private final BlockType oakLeaves;
    private final BlockType tallGrass;
    private final BlockType deadBush;
    private final BlockType dandelion;
    private final BlockType poppy;
    private final BlockType sandstone;

    public NormalWorldGenerator(BlockRegistry registry, String worldName) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.seed = 0x5A4D494E5C00L ^ worldName.hashCode() * 0x9E3779B97F4L;
        this.air = registry.require(Identifier.parse("minecraft:air"));
        this.bedrock = registry.require(Identifier.parse("minecraft:bedrock"));
        this.stone = registry.require(Identifier.parse("minecraft:stone"));
        this.dirt = registry.require(Identifier.parse("minecraft:dirt"));
        this.grass = registry.require(Identifier.parse("minecraft:grass_block"));
        this.sand = registry.require(Identifier.parse("minecraft:sand"));
        this.gravel = registry.require(Identifier.parse("minecraft:gravel"));
        this.water = registry.require(Identifier.parse("minecraft:water"));
        this.coalOre = registry.require(Identifier.parse("minecraft:coal_ore"));
        this.ironOre = registry.require(Identifier.parse("minecraft:iron_ore"));
        this.goldOre = registry.require(Identifier.parse("minecraft:gold_ore"));
        this.redstoneOre = registry.require(Identifier.parse("minecraft:redstone_ore"));
        this.diamondOre = registry.require(Identifier.parse("minecraft:diamond_ore"));
        this.oakLog = registry.require(Identifier.parse("minecraft:oak_log"));
        this.oakLeaves = registry.require(Identifier.parse("minecraft:oak_leaves"));
        this.tallGrass = registry.require(Identifier.parse("minecraft:tall_grass"));
        this.deadBush = registry.require(Identifier.parse("minecraft:dead_bush"));
        this.dandelion = registry.require(Identifier.parse("minecraft:dandelion"));
        this.poppy = registry.require(Identifier.parse("minecraft:poppy"));
        this.sandstone = registry.require(Identifier.parse("minecraft:sandstone"));
    }

    /**
     * The column's biome: the low-frequency climate pair (temperature decides
     * desert-ness, moisture decides forest-ness) with the plains remainder —
     * the historical climate-noise shape the community generators use.
     */
    public Biome biomeAt(int x, int z) {
        double temperature = fbm2(x * 0.0016 + 4_000, z * 0.0016 - 4_000, 2);
        double moisture = fbm2(x * 0.0022 - 8_000, z * 0.0022 + 8_000, 2);
        if (temperature > 0.66 && moisture < 0.42) {
            return Biome.DESERT;
        }
        if (moisture > 0.56) {
            return Biome.FOREST;
        }
        return Biome.PLAINS;
    }

    @Override
    public int groundLevel() {
        return spawnColumnHeight;
    }

    @Override
    public net.zaminmc.torch.util.Position spawnPosition() {
        return new net.zaminmc.torch.util.Position(spawnX + 0.5, spawnColumnHeight + 1.0, spawnZ + 0.5);
    }

    /** The first dry column on the +x diagonal (deterministic spawn anchor). */
    private int spawnX;
    private int spawnZ;
    private int spawnColumnHeight = scanSpawnColumn();

    private int scanSpawnColumn() {
        for (int r = 0; r < 64; r += 4) {
            int x = r;
            int z = r;
            int h = heightAt(x, z);
            if (h > SEA_LEVEL + 1 && h < 100) {
                spawnX = x;
                spawnZ = z;
                return h;
            }
        }
        spawnX = 0;
        spawnZ = 0;
        return Math.max(heightAt(0, 0), SEA_LEVEL + 1);
    }

    @Override
    public void generate(EngineChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        net.zaminmc.torch.block.ChunkPosition at = chunk.position();
        int baseX = at.x() << 4;
        int baseZ = at.z() << 4;
        int[][] heights = new int[16][16];
        Biome[][] biomes = new Biome[16][16];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                heights[lx][lz] = heightAt(baseX + lx, baseZ + lz);
                biomes[lx][lz] = biomeAt(baseX + lx, baseZ + lz);
                chunk.setBiome(lx, lz, (byte) biomes[lx][lz].legacyId());
            }
        }
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = baseX + lx;
                int worldZ = baseZ + lz;
                int h = heights[lx][lz];
                boolean underwater = h < SEA_LEVEL;
                for (int y = 0; y <= Math.max(h, SEA_LEVEL); y++) {
                    chunk.setBlock(lx, y, lz, blockAt(worldX, y, worldZ, h, underwater,
                            biomes[lx][lz]));
                }
            }
        }
        plantTrees(chunk, baseX, baseZ, heights, biomes);
        plantFlora(chunk, baseX, baseZ, heights, biomes);
    }

    /** The block for one column cell: core, skin, water, cave or ore. */
    private BlockType blockAt(int x, int y, int z, int h, boolean underwater, Biome biome) {
        if (y == 0) {
            return bedrock;
        }
        if (y > h) {
            return y <= SEA_LEVEL ? water : air;
        }
        // Caves carve the core below the skin; the surface stays intact.
        if (y > 4 && y < h - 3 && isCave(x, y, z)) {
            return air;
        }
        int skinDepth = h - y;
        if (biome == Biome.DESERT && !underwater) {
            // The desert's skin: three sand over a sandstone band, the
            // historical sand-sandstone column shape.
            if (skinDepth == 0 || skinDepth <= 2) {
                return sand;
            }
            if (skinDepth <= 6) {
                return sandstone;
            }
            return oreOrStone(x, y, z);
        }
        if (skinDepth == 0) {
            if (underwater) {
                return seaFloor(x, z);
            }
            return h <= SEA_LEVEL + 1 ? sand : grass; // the beach band
        }
        if (skinDepth <= 3) {
            return underwater ? sand : dirt;
        }
        return oreOrStone(x, y, z);
    }

    private BlockType seaFloor(int x, int z) {
        return hash2(x, z, 0x9E37) % 5 == 0 ? gravel : sand;
    }

    /** The depth-banded ore ladder (community blocks.json distributions). */
    private BlockType oreOrStone(int x, int y, int z) {
        long roll = hash3(x, y, z, 0x51ED);
        if (y <= 13 && roll % 10_000 < 12) {
            return diamondOre;
        }
        if (y <= 16 && roll % 10_000 < 22) {
            return redstoneOre;
        }
        if (y <= 30 && roll % 10_000 < 20) {
            return goldOre;
        }
        if (y <= 54 && roll % 10_000 < 60) {
            return ironOre;
        }
        if (y <= 60 && roll % 10_000 < 80) {
            return coalOre;
        }
        return stone;
    }

    /** The column height: fBm plains plus a squared mountain octave. */
    public int heightAt(int x, int z) {
        double rolling = fbm2(x * 0.0085, z * 0.0085, 3);
        double mountains = fbm2(x * 0.0035 + 1_000, z * 0.0035 - 1_000, 2);
        double hill = (rolling - 0.5) * 12.0;
        double ridge = Math.max(0.0, mountains - 0.58) * 90.0; // only the high band rises
        int h = 60 + (int) Math.round(hill + ridge);
        return Math.max(24, Math.min(120, h));
    }

    /**
     * The world block of one column cell (the diagnostics view of the
     * generation surface: biome-aware, no chunk allocation).
     */
    public BlockType surfaceBlockAt(int x, int y, int z) {
        int h = heightAt(x, z);
        return blockAt(x, y, z, h, h < SEA_LEVEL, biomeAt(x, z));
    }

    private boolean isCave(int x, int y, int z) {
        double worm = fbm3(x * 0.045, y * 0.09, z * 0.045);
        return worm > 0.74;
    }

    // ------------------------------------------------------------------ noise core

    /** 2D value noise fBm: three octaves, quintic-smoothed bilinear lattice. */
    private double fbm2(double x, double z, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double norm = 0.0;
        for (int o = 0; o < octaves; o++) {
            sum += valueNoise2(x * frequency, z * frequency) * amplitude;
            norm += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / norm;
    }

    private double fbm3(double x, double y, double z) {
        return (valueNoise3(x, y, z) * 0.65
                + valueNoise3(x * 2.03 + 31.7, y * 2.03, z * 2.03 - 17.3) * 0.35);
    }

    private double valueNoise2(double x, double z) {
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        double tx = smooth(x - xi);
        double tz = smooth(z - zi);
        double v00 = lattice2(xi, zi);
        double v10 = lattice2(xi + 1, zi);
        double v01 = lattice2(xi, zi + 1);
        double v11 = lattice2(xi + 1, zi + 1);
        return lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), tz);
    }

    private double valueNoise3(double x, double y, double z) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double tx = smooth(x - xi);
        double ty = smooth(y - yi);
        double tz = smooth(z - zi);
        double c000 = lattice3(xi, yi, zi);
        double c100 = lattice3(xi + 1, yi, zi);
        double c010 = lattice3(xi, yi + 1, zi);
        double c110 = lattice3(xi + 1, yi + 1, zi);
        double c001 = lattice3(xi, yi, zi + 1);
        double c101 = lattice3(xi + 1, yi, zi + 1);
        double c011 = lattice3(xi, yi + 1, zi + 1);
        double c111 = lattice3(xi + 1, yi + 1, zi + 1);
        return lerp(
                lerp(lerp(c000, c100, tx), lerp(c010, c110, tx), ty),
                lerp(lerp(c001, c101, tx), lerp(c011, c111, tx), ty),
                tz);
    }

    private double lattice2(int x, int z) {
        long h = seed ^ (x * 0x27D4EB2DL) ^ (z * 0x165667B1L);
        h = (h ^ (h >>> 15)) * 0x2C1B3C6DL;
        h = (h ^ (h >>> 12)) * 0x297F2D39L;
        return ((h ^ (h >>> 15)) & 0xFFFFFFL) / (double) 0xFFFFFFL;
    }

    private double lattice3(int x, int y, int z) {
        long h = seed ^ (x * 0x27D4EB2DL) ^ (y * 0x9E3779B1L) ^ (z * 0x165667B1L);
        h = (h ^ (h >>> 15)) * 0x2C1B3C6DL;
        h = (h ^ (h >>> 13)) * 0x297F2D39L;
        return ((h ^ (h >>> 16)) & 0xFFFFFFL) / (double) 0xFFFFFFL;
    }

    private long hash2(int x, int z, int salt) {
        long h = seed ^ (salt * 0x2545F491L) ^ (x * 0x27D4EB2DL) ^ (z * 0x165667B1L);
        return (h ^ (h >>> 15)) * 0x2C1B3C6DL ^ (h >>> 7);
    }

    private long hash3(int x, int y, int z, int salt) {
        long h = seed ^ (salt * 0x2545F491L) ^ (x * 0x27D4EB2DL)
                ^ (y * 0x9E3779B1L) ^ (z * 0x165667B1L);
        return (h ^ (h >>> 15)) * 0x2C1B3C6DL ^ (h >>> 7);
    }

    private static double smooth(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------ trees

    /**
     * Plants 0-6 oak trees per chunk on grass: the biome decides the density
     * (forest 2-5, plains 0-2, desert none). Positions stay inside the 2..13
     * margin so the two-radius canopy never leaks across the chunk border
     * (the cheap deterministic answer to cross-chunk structures).
     */
    private void plantTrees(EngineChunk chunk, int baseX, int baseZ, int[][] heights,
                            Biome[][] biomes) {
        net.zaminmc.torch.block.ChunkPosition at = chunk.position();
        long chunkSeed = seed ^ (at.x() * 0x5DEECE66DL) ^ (at.z() * 0x2545F4914F6CDD1DL);
        java.util.Random random = new java.util.Random(chunkSeed);
        Biome chunkBiome = biomes[8][8];
        int trees;
        switch (chunkBiome) {
            case FOREST -> trees = 2 + random.nextInt(4);
            case PLAINS -> trees = random.nextInt(3);
            default -> trees = 0; // deserts never grow oaks
        }
        for (int i = 0; i < trees; i++) {
            int lx = 2 + random.nextInt(12);
            int lz = 2 + random.nextInt(12);
            int h = heights[lx][lz];
            if (h <= SEA_LEVEL + 1 || h > 110) {
                continue; // no trees on beaches or in water or on peaks
            }
            if (biomes[lx][lz] == Biome.DESERT || chunk.getBlock(lx, h, lz).equals(sand)) {
                continue;
            }
            int trunk = 4 + random.nextInt(3);
            for (int y = 1; y <= trunk; y++) {
                chunk.setBlock(lx, h + y, lz, oakLog);
            }
            for (int dy = trunk - 2; dy <= trunk + 1; dy++) {
                int radius = dy >= trunk ? 1 : 2;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx == 0 && dz == 0 && dy <= trunk) {
                            continue; // the trunk cell stays wood
                        }
                        if (Math.abs(dx) == radius && Math.abs(dz) == radius
                                && random.nextBoolean()) {
                            continue; // the historical rounded corners
                        }
                        int leafX = lx + dx;
                        int leafZ = lz + dz;
                        int leafY = h + dy;
                        if (leafY > 127 || !chunk.getBlock(leafX, leafY, leafZ).equals(air)) {
                            continue;
                        }
                        chunk.setBlock(leafX, leafY, leafZ, oakLeaves);
                    }
                }
            }
        }
    }

    /**
     * The biome flora rolls: grass and the two flowers on the plains and the
     * forest floor, dead bushes on the desert sand. All placements target
     * air cells directly above the surface skin (never underwater), the
     * historical decoration pass's shape.
     */
    private void plantFlora(EngineChunk chunk, int baseX, int baseZ, int[][] heights,
                            Biome[][] biomes) {
        net.zaminmc.torch.block.ChunkPosition at = chunk.position();
        long chunkSeed = seed ^ (at.x() * 0x4FA3F0B1L) ^ (at.z() * 0x9E3779B97F4L);
        java.util.Random random = new java.util.Random(chunkSeed);
        Biome chunkBiome = biomes[8][8];
        if (chunkBiome == Biome.DESERT) {
            // Dead bushes: four rolls per desert chunk on the sand skin.
            for (int i = 0; i < 4; i++) {
                int lx = random.nextInt(16);
                int lz = random.nextInt(16);
                int h = heights[lx][lz];
                if (h <= SEA_LEVEL || !chunk.getBlock(lx, h, lz).equals(sand)) {
                    continue;
                }
                if (chunk.getBlock(lx, h + 1, lz).equals(air)) {
                    chunk.setBlock(lx, h + 1, lz, deadBush);
                }
            }
            return;
        }
        // Grass: eight rolls, the meadow's main texture.
        for (int i = 0; i < 8; i++) {
            int lx = random.nextInt(16);
            int lz = random.nextInt(16);
            int h = heights[lx][lz];
            if (h <= SEA_LEVEL || !chunk.getBlock(lx, h, lz).equals(grass)) {
                continue;
            }
            if (chunk.getBlock(lx, h + 1, lz).equals(air)) {
                chunk.setBlock(lx, h + 1, lz, tallGrass);
            }
        }
        // Flowers: two rolls, poppy or dandelion (the historical pair).
        for (int i = 0; i < 2; i++) {
            int lx = random.nextInt(16);
            int lz = random.nextInt(16);
            int h = heights[lx][lz];
            if (h <= SEA_LEVEL || !chunk.getBlock(lx, h, lz).equals(grass)) {
                continue;
            }
            if (chunk.getBlock(lx, h + 1, lz).equals(air)) {
                chunk.setBlock(lx, h + 1, lz, random.nextBoolean() ? poppy : dandelion);
            }
        }
    }
}
