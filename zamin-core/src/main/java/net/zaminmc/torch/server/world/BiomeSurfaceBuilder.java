package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.DyeFamilyBlocks;
import net.zaminmc.torch.util.Identifier;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

/**
 * The depth-dependent surface builders (VIII14's surface slice — the
 * reference's {@code Biome.prepareAndBuildSurfaces} whole over the
 * engine's per-cell column model): the core {@code buildSurfaces} walk
 * (Biome.java lines 452-508 — the k formula, the sea-band pair reset,
 * the subsurface band, the sand-family's sandstone extension, the deep
 * gravel arm) plus the four family overrides — the extreme hills'
 * gravel/stone switches (ExtremeHillsBiome lines 59-71), the mega
 * taiga's podzol/coarse switch (TaigaBiome lines 88-101), the
 * shattered savanna's stone switch (SavannaBiome lines 61-71), and the
 * mesa family's whole clay-layer stack (MesaBiome's own walk — the
 * 64-row seeded clay table, the cos band, the 64-127 orange band, the
 * has-trees 86+2k gate, the Bryce pillars).
 *
 * <p>THE DEPTH STAND-IN (the named adaptation): the reference feeds
 * {@code perlinNoise3} — a {@code PerlinSimplexNoise(worldRandom, 4)}
 * sampled at the 1/16 block scale (OverworldChunkGenerator line 160's
 * {@code 0.03125 * 2} region scale). The engine salts the same shape
 * off its own world seed (the reference's ctor consumes the world
 * random at a fixed position in a seven-noise sequence the engine's
 * fbm terrain stands in for — the stand-in documented here, the
 * distribution the same 4-octave 1/16 band).
 *
 * <p>THE COLUMN RANDOM (the second named adaptation): the reference's
 * k formula and the sandstone extension roll from the chunk's shared
 * population random (the walk's own column order); the engine derives
 * a per-column hash double — deterministic per (seed, x, z), the same
 * family the generator's ores ride.
 */
public final class BiomeSurfaceBuilder {

    /**
     * The reference's sea level (63 — the walk's own constant; the
     * engine's {@code NormalWorldGenerator.SEA_LEVEL} 62 is the top
     * water cell, the same line in the reference's coordinates).
     */
    private static final int SEA = 63;

    /** The builder's own salt (the depth noise's derivation). */
    private static final long DEPTH_SALT = 0x535246414345L;

    private final long seed;
    private final BlockRegistry registry;

    /**
     * The depth octave stack (the reference's {@code perlinNoise3}'s own
     * four SimplexNoise levels — the getRegion walk below samples them
     * directly).
     */
    private final net.zaminmc.torch.server.world.noise.SimplexNoise[] depthLevels;

    // ---------------------------------------------------------- the materials

    private final BlockType stone;
    private final BlockType dirt;
    private final BlockType grass;
    private final BlockType sand;
    private final BlockType gravel;
    private final BlockType sandstone;
    private final BlockType redSand;
    private final BlockType redSandstone;
    private final BlockType hardenedClay;
    private final BlockType podzol;
    private final BlockType coarseDirt;
    /** The stained clay family by dye color index (the 1.8 metadata order). */
    private final BlockType[] stainedClay = new BlockType[16];

    // ------------------------------------------------------- the mesa's table

    /** The mesa's 64-row clay layer table (placeClayLayers 1:1). */
    private final BlockType[] clayLayers = new BlockType[64];
    /** The clay band offset noise (getClayLayer's x-only consult). */
    private final net.zaminmc.torch.server.world.noise.PerlinSimplexNoise clayOffsetNoise;
    /** The Bryce pillar noise (clayPillarNoise — 4 octaves). */
    private final net.zaminmc.torch.server.world.noise.PerlinSimplexNoise clayPillarNoise;
    /** The Bryce pillar cap noise (claySurfaceNoise — 1 octave). */
    private final net.zaminmc.torch.server.world.noise.PerlinSimplexNoise claySurfaceNoise;

    public BiomeSurfaceBuilder(long seed, BlockRegistry registry) {
        this.seed = seed;
        this.registry = Objects.requireNonNull(registry, "registry");
        // The depth stack (the reference's ctor: PerlinSimplexNoise(random, 4)
        // — four SimplexNoise levels off the one Random, in order).
        Random depthRandom = new Random(seed ^ DEPTH_SALT);
        this.depthLevels = new net.zaminmc.torch.server.world.noise.SimplexNoise[4];
        for (int level = 0; level < 4; level++) {
            depthLevels[level] = new net.zaminmc.torch.server.world.noise.SimplexNoise(
                    depthRandom);
        }

        this.stone = registry.require(Identifier.parse("minecraft:stone"));
        this.dirt = registry.require(Identifier.parse("minecraft:dirt"));
        this.grass = registry.require(Identifier.parse("minecraft:grass_block"));
        this.sand = registry.require(Identifier.parse("minecraft:sand"));
        this.gravel = registry.require(Identifier.parse("minecraft:gravel"));
        this.sandstone = registry.require(Identifier.parse("minecraft:sandstone"));
        this.redSand = registry.require(Identifier.parse("minecraft:red_sand"));
        this.redSandstone = registry.require(Identifier.parse("minecraft:red_sandstone"));
        this.hardenedClay = registry.require(Identifier.parse("minecraft:hardened_clay"));
        this.podzol = registry.require(Identifier.parse("minecraft:podzol"));
        this.coarseDirt = registry.require(Identifier.parse("minecraft:coarse_dirt"));
        for (int color = 0; color < 16; color++) {
            stainedClay[color] = registry.require(DyeFamilyBlocks.stainedClayBlock(color));
        }

        // The mesa's clay table (placeClayLayers 1:1 — the SAME Random
        // feeds the offset noise first, then the layer walks).
        Random clayRandom = new Random(seed);
        this.clayOffsetNoise = new net.zaminmc.torch.server.world.noise.PerlinSimplexNoise(
                clayRandom, 1);
        buildClayLayers(clayRandom);
        // The pillar pair (prepareAndBuildSurfaces' own fresh Random —
        // the 4-octave pillar noise first, then the 1-octave cap noise).
        Random pillarRandom = new Random(seed);
        this.clayPillarNoise = new net.zaminmc.torch.server.world.noise.PerlinSimplexNoise(
                pillarRandom, 4);
        this.claySurfaceNoise = new net.zaminmc.torch.server.world.noise.PerlinSimplexNoise(
                pillarRandom, 1);
    }

    // ------------------------------------------------------------------
    // The per-cell consult (the engine's blockAt seam)
    // ------------------------------------------------------------------

    /**
     * The surface-builder material at one cell of the column.
     *
     * @param x the block x
     * @param y the block y
     * @param z the block z
     * @param h the column's terrain height (the first stone cell of the
     *        base terrain)
     * @param biome the column's biome (the registration row)
     * @return the builder's material, or null when the cell is not the
     *         builder's — the core stone the ore ladder owns, or the
     *         air/water above the terrain (the caller's own answers)
     */
    public BlockType blockAt(int x, int y, int z, int h, Biome biome) {
        double depth = depthAt(x, z);
        if (biome.family() == Biome.Family.MESA) {
            return mesaBlockAt(x, y, z, h, depth, biome);
        }
        return coreBlockAt(x, y, z, h, depth, biome);
    }

    // ------------------------------------------------------------------
    // The core walk (Biome.buildSurfaces lines 452-508 + the family
    // overrides' pair switches)
    // ------------------------------------------------------------------

    private BlockType coreBlockAt(int x, int y, int z, int h, double depth, Biome biome) {
        // k (line 460): depth/3 + 3 + a 0.25-wide roll.
        int k = kOf(depth, x, z);
        if (k <= 0) {
            // The k <= 0 arm (lines 466-468): no surface, stone below —
            // dead at the depth noise's range but the law rides.
            return null;
        }
        if (y > h) {
            return null; // above the terrain: the caller's air/water
        }
        // The family pair switch (the overrides' per-column mutations).
        BlockType surface = surfaceOf(biome, depth);
        BlockType subsurface = subsurfaceOf(biome, depth);

        int skinDepth = h - y;
        if (skinDepth == 0) {
            // The first stone cell (lines 470-497).
            if (h < SEA - 7 - k) {
                // The deep arm (lines 489-492): one gravel cell over the
                // stone banding — the deep ocean floor.
                return gravel;
            }
            if (h >= SEA - 1) {
                // The surface cap (line 485) — the sea-band reset (lines
                // 472-475) re-reads the same switched pair, a no-op here.
                return surface;
            }
            // Between the lines (lines 493-496): the shallow underwater
            // floor carries the SUBSURFACE (a depth-1 floor keeps the
            // pair's cap; deeper floors show the dirt).
            return subsurface;
        }
        // The band below the cap (lines 498-507).
        if (skinDepth <= k) {
            return subsurface;
        }
        if (subsurface == sand || subsurface == redSand) {
            // The sandstone extension (lines 501-507): when the band's
            // counter hits zero on a sand subsurface, the extension
            // rolls nextInt(4) + max(0, y-63) more layers of the
            // matching sandstone.
            int extension = (int) (columnDouble(x, z, 0x5A17) * 4.0)
                    + Math.max(0, (h - k) - SEA);
            if (skinDepth <= k + extension) {
                return subsurface == redSand ? redSandstone : sandstone;
            }
        }
        return null; // the core stone — the ore ladder's own
    }

    /**
     * The switched surface material (the registration pair + the three
     * family overrides' depth switches).
     */
    private BlockType surfaceOf(Biome biome, double depth) {
        switch (biome) {
            // ExtremeHillsBiome lines 59-71: the mutated variant (2 — the
            // M pair) gravels at the depth extremes; every variant but the
            // more-trees form (1 — the edge and the plus) stones past 1.0.
            case EXTREME_HILLS_M, EXTREME_HILLS_PLUS_M -> {
                if (depth < -1.0 || depth > 2.0) {
                    return gravel;
                }
                return depth > 1.0 ? stone : registeredSurface(biome);
            }
            case EXTREME_HILLS -> {
                return depth > 1.0 ? stone : registeredSurface(biome);
            }
            // TaigaBiome lines 88-101 (the mega variants): coarse dirt on
            // the high depth, podzol through the middle band, grass below.
            case MEGA_TAIGA, MEGA_TAIGA_HILLS, MEGA_SPRUCE_TAIGA,
                    REDWOOD_TAIGA_HILLS_M -> {
                if (depth > 1.75) {
                    return coarseDirt;
                }
                return depth > -0.95 ? podzol : grass;
            }
            // SavannaBiome.ShatteredSavannaBiome lines 61-71 (the
            // shattered pair): stone on the high depth, coarse dirt
            // through the middle, grass below.
            case SAVANNA_M, SAVANNA_PLATEAU_M -> {
                if (depth > 1.75) {
                    return stone;
                }
                return depth > -0.5 ? coarseDirt : grass;
            }
            default -> {
                return registeredSurface(biome);
            }
        }
    }

    /** The switched subsurface (the overrides' second member). */
    private BlockType subsurfaceOf(Biome biome, double depth) {
        switch (biome) {
            case EXTREME_HILLS_M, EXTREME_HILLS_PLUS_M -> {
                if (depth < -1.0 || depth > 2.0) {
                    return gravel;
                }
                return depth > 1.0 ? stone : registeredSubsurface(biome);
            }
            case EXTREME_HILLS -> {
                return depth > 1.0 ? stone : registeredSubsurface(biome);
            }
            // The mega taiga and the shattered savanna keep dirt under
            // every switch (the overrides never touch the second member).
            default -> {
                return registeredSubsurface(biome);
            }
        }
    }

    /** The registration pair's surface (the surfaceLegacyId consult). */
    private BlockType registeredSurface(Biome biome) {
        return switch (biome.surfaceLegacyId()) {
            case 1 -> stone; // the stone beach
            case 12 -> sand; // the desert/beach family
            case 80 -> Objects.requireNonNull(
                    registry.lookup(Identifier.parse("minecraft:snow"))
                            .orElse(grass), "snow"); // the ice spikes
            case 110 -> Objects.requireNonNull(
                    registry.lookup(Identifier.parse("minecraft:mycelium"))
                            .orElse(grass), "mycelium"); // the mushroom isle
            case 179 -> redSand; // the mesa family (the engine's own id)
            default -> grass; // the temperate default
        };
    }

    /** The registration pair's subsurface (the subsurfaceLegacyId consult). */
    private BlockType registeredSubsurface(Biome biome) {
        return switch (biome.subsurfaceLegacyId()) {
            case 1 -> stone; // the stone beach's stone
            case 12 -> sand; // the desert/beach sand
            case 159 -> stainedClay[1]; // the mesa's stained clay (plain
            // — the walk's own arm re-sets the orange banding)
            default -> dirt;
        };
    }

    // ------------------------------------------------------------------
    // The mesa walk (MesaBiome.prepareAndBuildSurfaces 1:1)
    // ------------------------------------------------------------------

    private BlockType mesaBlockAt(int x, int y, int z, int h, double depth, Biome biome) {
        boolean bryce = biome == Biome.MESA_BRYCE;
        boolean hasTrees = biome == Biome.MESA_PLATEAU_F || biome == Biome.MESA_PLATEAU_F_M;

        // The first stone cell: the terrain, or the Bryce pillar's top
        // when the pillar stands above it (the air-fill arm's stone rises
        // to (int) d4 — the cap consult rides the pillar's own top).
        int firstStone = h;
        if (bryce) {
            int top = (int) brycePillarTop(x, z, depth) - 1;
            if (top > h) {
                firstStone = top;
            }
        }
        if (y > firstStone) {
            return null; // above the pillar (or the terrain): the caller's own
        }

        int k = kOf(depth, x, z);
        if (k <= 0) {
            return null; // the dead arm, the law kept
        }
        // flag (line 651): the cos band over the depth's thirds.
        boolean clayBand = Math.cos(depth / 3.0 * Math.PI) > 0.0;
        // The mesa's band length (line 660): k + max(0, y - sea) — the
        // above-sea bonus the base walk never carries.
        int band = k + Math.max(0, firstStone - SEA);

        int skinDepth = firstStone - y;
        if (skinDepth == 0) {
            // The first stone cell (lines 662-711).
            if (firstStone >= SEA - 1) {
                if (!hasTrees || firstStone <= 86 + k * 2) {
                    if (firstStone > SEA + 3 + k) {
                        // The tall clay band (lines 687-697): orange
                        // outside 64-127, the cos band's hardened clay,
                        // the seeded layer table between.
                        if (firstStone < 64 || firstStone > 127) {
                            return stainedClay[1]; // orange
                        }
                        if (clayBand) {
                            return hardenedClay;
                        }
                        return clayLayerAt(x, firstStone, z);
                    }
                    // The surface cap (lines 698-700): the red sand —
                    // flag1 marks the orange banding below.
                    return redSand;
                }
                // The has-trees arm (lines 701-705): the plateau F's
                // coarse/grass tops above 86 + 2k.
                return clayBand ? coarseDirt : grass;
            }
            // The underwater floor (lines 706-711): the subsurface clay
            // re-set orange (the plain stained clay always re-sets).
            return stainedClay[1];
        }
        // The band below the cap (lines 712-721): flag1 (the red-sand
        // cap) banding orange, the layer table otherwise.
        if (skinDepth <= band) {
            boolean redSandCap = firstStone >= SEA - 1
                    && (!hasTrees || firstStone <= 86 + k * 2)
                    && firstStone <= SEA + 3 + k;
            if (redSandCap) {
                return stainedClay[1]; // orange
            }
            return clayLayerAt(x, y, z);
        }
        if (y > h) {
            // The Bryce pillar's body below its band — the air-fill arm's
            // plain stone (never the ore ladder's).
            return stone;
        }
        return null; // the core stone
    }

    /**
     * The Bryce pillar's stone ceiling (prepareAndBuildSurfaces lines
     * 630-653 1:1): the swapped (x, z) pillar coordinates, the
     * min(|depth|, pillarNoise) gate, the squared-and-scaled height, the
     * cap over the surface noise, and the +64 lift. Zero when the gate
     * refuses (no pillar).
     */
    private double brycePillarTop(int x, int z, double depth) {
        int i = (x & -16) + (z & 15);
        int j = (z & -16) + (x & 15);
        double d0 = Math.min(Math.abs(depth),
                clayPillarNoise.getValue(i * 0.25, j * 0.25));
        if (d0 <= 0.0) {
            return 0.0;
        }
        double d4 = d0 * d0 * 2.5;
        double d3 = Math.ceil(Math.abs(
                claySurfaceNoise.getValue(i * 0.001953125, j * 0.001953125)) * 50.0) + 14.0;
        if (d4 > d3) {
            d4 = d3;
        }
        return d4 + 64.0;
    }

    /**
     * The clay layer at the y (getClayLayer 1:1): the offset noise's
     * x-ONLY consult (the reference passes x for both coordinates — its
     * own quirk, kept) over the 64-row table.
     */
    private BlockType clayLayerAt(int x, int y, int z) {
        int offset = (int) Math.round(
                clayOffsetNoise.getValue(x * 1.0 / 512.0, x * 1.0 / 512.0) * 2.0);
        return clayLayers[(y + offset + 64) % 64];
    }

    /**
     * The 64-row clay table (placeClayLayers 1:1): the hardened clay
     * fill, the orange sparse walk, then the yellow/brown/red runs and
     * the white/silver pairs.
     */
    private void buildClayLayers(Random random) {
        Arrays.fill(clayLayers, hardenedClay);
        for (int y = 0; y < 64; y++) {
            y += random.nextInt(5) + 1;
            if (y < 64) {
                clayLayers[y] = stainedClay[1]; // orange
            }
        }
        int yellowRuns = random.nextInt(4) + 2;
        for (int run = 0; run < yellowRuns; run++) {
            int length = random.nextInt(3) + 1;
            int start = random.nextInt(64);
            for (int l = 0; start + l < 64 && l < length; l++) {
                clayLayers[start + l] = stainedClay[4]; // yellow
            }
        }
        int brownRuns = random.nextInt(4) + 2;
        for (int run = 0; run < brownRuns; run++) {
            int length = random.nextInt(3) + 2;
            int start = random.nextInt(64);
            for (int l = 0; start + l < 64 && l < length; l++) {
                clayLayers[start + l] = stainedClay[12]; // brown
            }
        }
        int redRuns = random.nextInt(4) + 2;
        for (int run = 0; run < redRuns; run++) {
            int length = random.nextInt(3) + 1;
            int start = random.nextInt(64);
            for (int l = 0; start + l < 64 && l < length; l++) {
                clayLayers[start + l] = stainedClay[14]; // red
            }
        }
        int whiteRuns = random.nextInt(3) + 3;
        int walk = 0;
        for (int run = 0; run < whiteRuns; run++) {
            walk += random.nextInt(16) + 4;
            if (walk < 64) {
                clayLayers[walk] = stainedClay[0]; // white
                if (walk > 1 && random.nextBoolean()) {
                    clayLayers[walk - 1] = stainedClay[8]; // silver
                }
                if (walk < 63 && random.nextBoolean()) {
                    clayLayers[walk + 1] = stainedClay[8]; // silver
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // The shared derivations
    // ------------------------------------------------------------------

    /**
     * The depth value at the column (the reference's depthBuffer consult
     * — getRegion's own law: OverworldChunkGenerator line 160 samples
     * the four levels at the 1/16 base scale HALVING per level, each
     * weighted 0.55 DOUBLING per level — the add tail's {@code 70.0 ×
     * corners × noiseScale} accumulation).
     */
    public double depthAt(int x, int z) {
        double sum = 0.0;
        double frequency = 0.0625;
        double amplitude = 0.55;
        for (int level = 0; level < depthLevels.length; level++) {
            net.zaminmc.torch.server.world.noise.SimplexNoise noise = depthLevels[level];
            // The add path's own coordinates (line 122): the block
            // position scaled, then the instance's own offset.
            sum += noise.getValue(x * frequency + noise.offsetX,
                    z * frequency + noise.offsetY) * amplitude;
            frequency *= 0.5;
            amplitude *= 2.0;
        }
        return sum;
    }

    /**
     * The k formula (line 460): {@code depth / 3 + 3 + a 0.25-wide
     * roll} — the roll a per-column hash double (the named adaptation).
     */
    private int kOf(double depth, int x, int z) {
        return (int) (depth / 3.0 + 3.0 + columnDouble(x, z, 0x4B1D) * 0.25);
    }

    /** A uniform per-column double off the seed hash (the roll source). */
    private double columnDouble(int x, int z, int salt) {
        long h = seed ^ (salt * 0x2545F491L) ^ (x * 0x27D4EB2DL) ^ (z * 0x165667B1L);
        h = (h ^ (h >>> 15)) * 0x2C1B3C6DL;
        h ^= h >>> 7;
        h = (h ^ (h >>> 13)) * 0x297F2D39L;
        return ((h ^ (h >>> 16)) & 0xFFFFFFL) / (double) 0xFFFFFFL;
    }
}
