package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VANILLA TERRAIN NOISE CORE'S SEATS (Wave TN — the owner's report
 * "terrain is not vanilla like... biomes... all sucks" closed at the
 * density-stack root): the reference's 4-noise density model replaces the
 * engine's own value-noise column heightmap, the reference's own surface
 * column walk (the chunk random's k roll, the bedrock band, the sandstone
 * extension) replaces the pure per-cell consult at the fill, the surface
 * depth consult rides the generator's own perlinNoise3 (the construction
 * stream's fourth — the retired salt derivation answered a different field
 * at the same seed), the surface consults carry the reference's own
 * [j + i*16] MIRROR, and the sea level carries the reference's own 63.
 *
 * <p>THE RED SEATS (verified RED on the pre-fix engine): the overhang law
 * (a stone-air-stone density column — the column model's monotone h could
 * never), the bedrock band (the roll places bedrock above y=0 — the old
 * flat y=0 floor), the mirrored surface consult (the old un-mirrored
 * consult answered the column's OWN biome).</p>
 */
class TerrainNoiseCoreParityTest {

    private static BlockRegistry registry() {
        // The full boot chain (EngineServer's freeze order — the same
        // chain BiomeWorldTest carries).
        return net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new net.zaminmc.torch.server.block.BlockRegistryBuilder()))))))))
                .freeze();
    }

    private static BlockType type(BlockRegistry registry, String name) {
        return registry.require(Identifier.parse(name));
    }

    /** Generates one chunk (the air type off the frozen registry). */
    private static EngineChunk chunkOf(BlockRegistry registry, NormalWorldGenerator generator,
            int chunkX, int chunkZ) {
        EngineChunk chunk = new EngineChunk(
                new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ),
                type(registry, "minecraft:air"));
        generator.generate(chunk);
        return chunk;
    }

    /**
     * THE DETERMINISM SEAT: the same seed answers the same terrain — two
     * generators over the same chunk place identical cells (the noise
     * construction stream is the seed's own law; the fresh-buffer
     * parallel walk changes nothing).
     */
    @Test
    void theSameSeedAnswersTheSameTerrain() {
        BlockRegistry registry = registry();
        NormalWorldGenerator a = new NormalWorldGenerator(registry, "tn-determinism");
        NormalWorldGenerator b = new NormalWorldGenerator(registry, "tn-determinism");
        EngineChunk chunkA = chunkOf(registry, a, 3, -7);
        EngineChunk chunkB = chunkOf(registry, b, 3, -7);
        for (int lx = 0; lx < 16; lx++) {
            for (int y = 0; y < 256; y++) {
                for (int lz = 0; lz < 16; lz++) {
                    assertEquals(chunkA.getBlock(lx, y, lz), chunkB.getBlock(lx, y, lz),
                            "cell (" + lx + ", " + y + ", " + lz + ") identical");
                }
            }
        }
    }

    /**
     * THE OVERHANG SEAT (RED on the pre-fix engine): the density field
     * carries stone-above-air columns — the height map's own 33-step walk
     * inverts (a positive step, a negative step below it, a positive step
     * below THAT), the shape the retired column model's monotone h could
     * never answer. Probed on the core itself with the HIGH-VARIATION
     * kernel (the extreme hills' registration pair — the band where the
     * vertical wiggle beats the base-size line's slope; the plains' tight
     * 0.05 variation never inverts, exactly the vanilla law: the
     * overhangs live in the hills).
     */
    @Test
    void theDensityFieldCarriesOverhangColumns() {
        int overhangColumns = 0;
        int columns = 0;
        for (long seed : new long[]{1L, 42L, -987654321L, 20261109L}) {
            OverworldDensityCore core = new OverworldDensityCore(seed);
            Biome[] uniform = new Biome[100];
            java.util.Arrays.fill(uniform, Biome.EXTREME_HILLS);
            for (int area = 0; area < 24; area++) {
                int ax = (area % 5) * 7 - 14;
                int az = (area / 5) * 7 - 14;
                double[] heightMap = core.generateHeightMap(ax, 0, az, uniform);
                // The 25 columns of the 5x5 grid: index = x*165 + z*33 + y.
                for (int column = 0; column < 25; column++) {
                    int base = column * 33;
                    columns++;
                    // Walk the steps top-down; a positive step with a negative
                    // step BELOW it and a positive step below that (any gap) is
                    // the overhang-bearing column. The l1 > 29 fade band (the
                    // hard -10 fade to the void ceiling) excluded — it is the
                    // fade's own shape, not the noise's.
                    boolean positive = false;
                    boolean negative = false;
                    for (int step = 28; step >= 0; step--) {
                        double density = heightMap[base + step];
                        if (density > 0.0) {
                            if (negative) {
                                overhangColumns++;
                                break;
                            }
                            positive = true;
                        } else if (positive) {
                            negative = true;
                        }
                    }
                }
            }
        }
        assertTrue(overhangColumns > 0,
                "the density field inverts somewhere (found " + overhangColumns
                        + " overhang-bearing columns of " + columns + ")");
    }

    /**
     * THE SEA BAND SEAT: the fill's water rides the reference's own law —
     * {@code y < 63} (the top water cell 62, the first air cell 63) — over
     * an ocean floor column.
     */
    @Test
    void theSeaBandRidesTheReferenceLevel() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "tn-seaband");
        BlockType water = type(registry, "minecraft:water");
        BlockType air = type(registry, "minecraft:air");
        // Find an ocean column (the biome ocean, the terrain below the sea).
        int oceanX = -1;
        int oceanZ = -1;
        for (int x = -1024; x < 1024 && oceanX < 0; x += 32) {
            for (int z = -1024; z < 1024 && oceanX < 0; z += 32) {
                if (generator.biomeAt(x, z).family() == Biome.Family.OCEAN
                        && generator.heightAt(x, z) < 60) {
                    oceanX = x;
                    oceanZ = z;
                }
            }
        }
        assertTrue(oceanX >= 0, "an ocean column exists in the sweep");
        EngineChunk chunk = chunkOf(registry, generator, oceanX >> 4, oceanZ >> 4);
        int lx = oceanX & 15;
        int lz = oceanZ & 15;
        // The water band: y = 62 is water, y = 63 is air (the reference's
        // strict <). The floor sits below 60 by the sweep's own gate.
        assertEquals(water, chunk.getBlock(lx, 62, lz), "y=62 carries water");
        assertEquals(air, chunk.getBlock(lx, 63, lz), "y=63 carries air");
        // And the floor is stone-family (the fill's own core) — solid below
        // the water, no floating water cells under the floor.
        boolean solidFloor = false;
        for (int y = 61; y >= 1; y--) {
            BlockType cell = chunk.getBlock(lx, y, lz);
            if (!cell.equals(water) && !cell.equals(air)) {
                solidFloor = true;
                break;
            }
        }
        assertTrue(solidFloor, "the ocean column finds its floor");
    }

    /**
     * THE BEDROCK BAND SEAT (RED on the pre-fix engine — the old flat y=0
     * floor): the walk's own roll — {@code y <= nextInt(5)} per cell —
     * keeps y=0 ALWAYS bedrock, never places bedrock above y=4, and DOES
     * place bedrock above y=0 somewhere in a field (the 4-in-5-per-layer
     * odds).
     */
    @Test
    void theBedrockBandRollsLikeTheReference() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "tn-bedrock");
        BlockType bedrock = type(registry, "minecraft:bedrock");
        boolean raised = false;
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                EngineChunk chunk = chunkOf(registry, generator, chunkX, chunkZ);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        assertEquals(bedrock, chunk.getBlock(lx, 0, lz),
                                "y=0 is always bedrock");
                        for (int y = 5; y < 40; y++) {
                            assertNotEquals(bedrock, chunk.getBlock(lx, y, lz),
                                    "no bedrock above the roll band");
                        }
                        for (int y = 1; y <= 4; y++) {
                            raised |= chunk.getBlock(lx, y, lz).equals(bedrock);
                        }
                    }
                }
            }
        }
        assertTrue(raised, "the roll places bedrock above y=0 somewhere in the field");
    }

    /**
     * THE MIRRORED CONSULT SEAT (RED on the pre-fix engine — the old
     * un-mirrored per-cell consult): the reference's own transposition —
     * the surface pass reads {@code biomes[j + i*16]} for the column
     * (i, j), the CHUNK-LOCAL MIRRORED cell of the x-fastest array (the
     * cell (lz, lx) of the same chunk — never the global x/z swap). At a
     * desert boundary the sand tops follow the mirrored biome map, so a
     * sand-topped dry column exists whose OWN biome is not the desert
     * family while its mirrored cell's is (the transposition's own proof
     * — the un-mirrored law would answer grass there), and an own-desert
     * column whose mirror refused it stays un-sanded.
     */
    @Test
    void theSurfaceConsultReadsTheMirroredBiome() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "tn-mirror");
        BlockType sand = type(registry, "minecraft:sand");
        BlockType redSand = type(registry, "minecraft:red_sand");
        boolean mirroredSand = false;
        boolean ownDesertNotSanded = false;
        int dryColumns = 0;
        for (int chunkX = -48; chunkX < 48 && !(mirroredSand && ownDesertNotSanded); chunkX++) {
            for (int chunkZ = -48; chunkZ < 48 && !(mirroredSand && ownDesertNotSanded); chunkZ++) {
                // The chunk-local mirror needs the biome pair consulted
                // per column first — skip the chunk generation entirely
                // when no boundary column lives in it.
                boolean anyBoundary = false;
                for (int lx = 0; lx < 16 && !anyBoundary; lx++) {
                    for (int lz = 0; lz < 16 && !anyBoundary; lz++) {
                        int x = (chunkX << 4) + lx;
                        int z = (chunkZ << 4) + lz;
                        boolean own = generator.biomeAt(x, z).family() == Biome.Family.DESERT;
                        boolean mirror = generator.biomeAt((chunkX << 4) + lz, (chunkZ << 4) + lx)
                                .family() == Biome.Family.DESERT;
                        if (own != mirror) {
                            anyBoundary = true;
                        }
                    }
                }
                if (!anyBoundary) {
                    continue;
                }
                EngineChunk chunk = chunkOf(registry, generator, chunkX, chunkZ);
                for (int lx = 0; lx < 16 && !(mirroredSand && ownDesertNotSanded); lx++) {
                    for (int lz = 0; lz < 16 && !(mirroredSand && ownDesertNotSanded); lz++) {
                        int x = (chunkX << 4) + lx;
                        int z = (chunkZ << 4) + lz;
                        int top = generator.heightAt(x, z);
                        if (top < 68 || top > 110) {
                            continue; // the underwater floors and k<=0 tails carry no sand cap
                        }
                        dryColumns++;
                        BlockType cap = chunk.getBlock(lx, top, lz);
                        boolean mirrorDesert = generator.biomeAt((chunkX << 4) + lz, (chunkZ << 4) + lx)
                                .family() == Biome.Family.DESERT;
                        boolean ownDesert = generator.biomeAt(x, z).family() == Biome.Family.DESERT;
                        // The grass-capped gate (the registration's surface
                        // legacy id 2): the proof only counts columns whose
                        // OWN law would answer grass — the beach's own sand
                        // would mask the mirror's desert cap.
                        boolean ownGrassCapped = !ownDesert
                                && generator.biomeAt(x, z).surfaceLegacyId() == 2;
                        boolean mirrorGrassCapped = !mirrorDesert
                                && generator.biomeAt((chunkX << 4) + lz, (chunkZ << 4) + lx)
                                        .surfaceLegacyId() == 2;
                        if (mirrorDesert && ownGrassCapped) {
                            // The transposition's proof cell: the mirrored
                            // cell carried the desert in — the fill answers
                            // the desert's sand cap (grass under the old law).
                            mirroredSand |= cap.equals(sand) || cap.equals(redSand);
                        }
                        if (ownDesert && mirrorGrassCapped) {
                            // The converse: the column's own desert never
                            // sands when the mirrored cell refused it.
                            ownDesertNotSanded |= !cap.equals(sand) && !cap.equals(redSand);
                        }
                    }
                }
            }
        }
        assertTrue(dryColumns > 20, "the sweep found boundary columns (" + dryColumns + ")");
        assertTrue(mirroredSand,
                "a mirrored-desert column carries the desert's sand cap (the [j + i*16] law)");
        assertTrue(ownDesertNotSanded,
                "an own-desert column whose mirror refused it stays un-sanded");
    }

    /**
     * THE PLAINS BAND SEAT: the walk's own band law over a plains column —
     * the grass cap, the dirt run under it (the k roll's own 3-to-4 depth
     * band).
     */
    @Test
    void thePlainsColumnWearsGrassOverDirt() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "tn-plains");
        BlockType grass = type(registry, "minecraft:grass_block");
        BlockType dirt = type(registry, "minecraft:dirt");
        // Find a dry plains column whose cap is the grass (the trees' own
        // cells skipped — their trunks answer oak, a different lane).
        int px = -1;
        int pz = -1;
        for (int chunkX = 0; chunkX < 64 && px < 0; chunkX++) {
            for (int chunkZ = 0; chunkZ < 64 && px < 0; chunkZ++) {
                EngineChunk chunk = chunkOf(registry, generator, chunkX, chunkZ);
                for (int lx = 0; lx < 16 && px < 0; lx += 2) {
                    for (int lz = 0; lz < 16 && px < 0; lz += 2) {
                        int x = (chunkX << 4) + lx;
                        int z = (chunkZ << 4) + lz;
                        int top = generator.heightAt(x, z);
                        if (generator.biomeAt(z, x) == Biome.PLAINS
                                && top > 66 && top < 90
                                && chunk.getBlock(lx, top, lz).equals(grass)) {
                            px = x;
                            pz = z;
                            // The dirt run: at least 2 dirt cells directly
                            // under the cap.
                            int dirtRun = 0;
                            for (int y = top - 1; y > top - 6; y--) {
                                if (chunk.getBlock(lx, y, lz).equals(dirt)) {
                                    dirtRun++;
                                } else {
                                    break;
                                }
                            }
                            assertTrue(dirtRun >= 2,
                                    "the dirt run under the grass cap (found " + dirtRun + ")");
                        }
                    }
                }
            }
        }
        assertTrue(px >= 0, "a grass-capped plains column exists in the sweep");
    }

    /**
     * THE CAVE STAND-IN SEAT (the documented adaptation — the reference's
     * carver pair rides its own lane): the worm carve still opens cells
     * below the surface band over a field.
     */
    @Test
    void theCaveStandInStillCarvesBelowTheSurface() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "tn-caves");
        BlockType air = type(registry, "minecraft:air");
        int carved = 0;
        for (int chunkX = -3; chunkX <= 3 && carved == 0; chunkX++) {
            for (int chunkZ = -3; chunkZ <= 3 && carved == 0; chunkZ++) {
                EngineChunk chunk = chunkOf(registry, generator, chunkX, chunkZ);
                for (int lx = 0; lx < 16 && carved == 0; lx++) {
                    for (int lz = 0; lz < 16 && carved == 0; lz++) {
                        int top = generator.heightAt((chunkX << 4) + lx, (chunkZ << 4) + lz);
                        for (int y = 8; y < top - 6 && carved == 0; y++) {
                            if (chunk.getBlock(lx, y, lz).equals(air)) {
                                carved++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(carved > 0, "the worm carve opens cells below the surface band");
    }

    /**
     * THE PROBE SEAT: the column probe (the consults' own heightAt) and
     * the fill agree — the probe's top is filled in the generated chunk
     * (the surface pass rewrites the material, never the top).
     */
    @Test
    void theColumnProbeAgreesWithTheFilledTerrain() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "tn-probe");
        BlockType air = type(registry, "minecraft:air");
        for (int chunkX : new int[]{-2, 0, 2}) {
            for (int chunkZ : new int[]{-2, 0, 2}) {
                EngineChunk chunk = chunkOf(registry, generator, chunkX, chunkZ);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int top = generator.heightAt((chunkX << 4) + lx, (chunkZ << 4) + lz);
                        assertTrue(top >= 0 && top <= 255, "the probe answers in range");
                        if (top >= 1) {
                            assertNotEquals(air, chunk.getBlock(lx, top, lz),
                                    "the probe's top cell is filled");
                        }
                    }
                }
            }
        }
    }
}
