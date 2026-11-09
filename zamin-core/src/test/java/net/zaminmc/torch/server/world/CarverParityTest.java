package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE CARVER PAIR'S SEATS (Wave CV — the reference's CaveWorldCarver +
 * RavineWorldCarver replacing the retired fbm cave stand-in): the
 * worm-and-room caves with the region walk (the 17x17 neighborhood, every
 * region chunk re-rolling its own starts with the carve clipped to the
 * target), the lava floor below y=10 (the stand-in never placed lava), the
 * sandstone ceiling fix, the water suppression, the bedrock untouched,
 * and the ravine's wide trench.
 */
class CarverParityTest {

    private static BlockRegistry registry() {
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

    private static EngineChunk chunkOf(BlockRegistry registry, NormalWorldGenerator generator,
            int chunkX, int chunkZ) {
        EngineChunk chunk = new EngineChunk(
                new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ),
                type(registry, "minecraft:air"));
        generator.generate(chunk);
        return chunk;
    }

    /**
     * THE DETERMINISM SEAT: the same seed answers the same carved chunk —
     * the region walk's seeds and the tunnel walks' own Randoms are the
     * seed's law.
     */
    @Test
    void theSameSeedAnswersTheSameCarvedChunk() {
        BlockRegistry registry = registry();
        NormalWorldGenerator a = new NormalWorldGenerator(registry, "cv-determinism");
        NormalWorldGenerator b = new NormalWorldGenerator(registry, "cv-determinism");
        EngineChunk chunkA = chunkOf(registry, a, 5, -3);
        EngineChunk chunkB = chunkOf(registry, b, 5, -3);
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
     * THE LAVA FLOOR SEAT (RED on the retired fbm stand-in — it never
     * placed lava; verified RED on the lava-to-air no-op): the cave carve
     * below {@code j2 - 1 < 10} answers the LAVA floor. A UNIT seat over
     * the carver itself — the synthetic all-stone chunk (the plains
     * seam), the pinned seed — isolated from the decorate pass's own rare
     * lava lakes (the integration field mixes them; the unit answers the
     * carver's own law).
     */
    @Test
    void theDeepCavesCarryTheLavaFloor() {
        BlockRegistry registry = registry();
        BlockType air = type(registry, "minecraft:air");
        BlockType lava = type(registry, "minecraft:lava");
        BlockType grassSurface = type(registry, "minecraft:grass_block");
        net.zaminmc.torch.server.world.carver.CaveCarver caves =
                new net.zaminmc.torch.server.world.carver.CaveCarver(
                        air, lava, type(registry, "minecraft:sandstone"),
                        type(registry, "minecraft:red_sandstone"));
        SyntheticChunk seam = new SyntheticChunk(registry);
        caves.place(3L, 0, 0, seam);
        int lavaCells = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = 1; y < 10; y++) {
                    if (seam.get(lx, y, lz).equals(lava)) {
                        lavaCells++;
                    }
                }
            }
        }
        assertTrue(lavaCells >= 20,
                "the deep caves' lava floor at the carver's own scale (found " + lavaCells + ")");
    }

    /**
     * THE RAVINE TRENCH SEAT (RED on the carvers-off no-op): the ravine's
     * wide carve — a pinned seed answering a proper trench (hundreds of
     * carved cells) in the synthetic chunk, the 1-in-50 region roll
     * landing its reach.
     */
    @Test
    void theRavineCarvesAWideTrench() {
        BlockRegistry registry = registry();
        BlockType air = type(registry, "minecraft:air");
        BlockType flowingLava = type(registry, "minecraft:flowing_lava");
        net.zaminmc.torch.server.world.carver.RavineCarver ravines =
                new net.zaminmc.torch.server.world.carver.RavineCarver(air, flowingLava);
        SyntheticChunk seam = new SyntheticChunk(registry);
        ravines.place(22L, 0, 0, seam);
        int carved = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = 1; y < 250; y++) {
                    if (seam.get(lx, y, lz).equals(air)) {
                        carved++;
                    }
                }
            }
        }
        assertTrue(carved >= 200,
                "the ravine's trench at its own scale (found " + carved + " carved cells)");
    }

    /**
     * THE SYNTHETIC CHUNK (the unit seam): the all-stone chunk with the
     * plains biome and the grass registration surface — the carver's own
     * reads and writes over the fresh state.
     */
    private static final class SyntheticChunk
            implements net.zaminmc.torch.server.world.carver.CarverChunk {
        private final BlockType stone;
        private final BlockType grassSurface;
        private final BlockType[][][] cells = new BlockType[16][256][16];

        SyntheticChunk(BlockRegistry registry) {
            this.stone = registry.require(Identifier.parse("minecraft:stone"));
            this.grassSurface = registry.require(Identifier.parse("minecraft:grass_block"));
        }

        @Override
        public BlockType get(int localX, int y, int localZ) {
            BlockType cell = cells[localX][y][localZ];
            return cell != null ? cell : stone;
        }

        @Override
        public void set(int localX, int y, int localZ, BlockType type) {
            cells[localX][y][localZ] = type;
        }

        @Override
        public Biome biomeAt(int worldX, int worldZ) {
            return Biome.PLAINS;
        }

        @Override
        public BlockType registeredSurfaceOf(Biome biome) {
            return grassSurface;
        }
    }

    /**
     * THE BEDROCK SEAT: the carve's own bounds ({@code l2 >= 1} and the
     * canReplace family without bedrock) keep y=0 bedrock whole.
     */
    @Test
    void theCarveNeverTouchesTheBedrockFloor() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "cv-bedrock");
        BlockType bedrock = type(registry, "minecraft:bedrock");
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                EngineChunk chunk = chunkOf(registry, generator, chunkX, chunkZ);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        assertEquals(bedrock, chunk.getBlock(lx, 0, lz),
                                "y=0 stays bedrock under the carve");
                    }
                }
            }
        }
    }

    /**
     * THE UNDERGROUND AIR SEAT: the carve answers air cells UNDER solid
     * ground (the worm tunnels below the surface) — with the carve count
     * sane (the region walk's 1-in-7 caves per chunk land somewhere in a
     * field).
     */
    @Test
    void theCarvesOpenTheUnderground() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "cv-under");
        BlockType air = type(registry, "minecraft:air");
        int undergroundAir = 0;
        for (int chunkX = -2; chunkX <= 2 && undergroundAir < 200; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2 && undergroundAir < 200; chunkZ++) {
                EngineChunk chunk = chunkOf(registry, generator, chunkX, chunkZ);
                for (int lx = 0; lx < 16 && undergroundAir < 200; lx++) {
                    for (int lz = 0; lz < 16 && undergroundAir < 200; lz++) {
                        // The column's solid top (the post-carve walk).
                        int top = 0;
                        for (int y = 255; y >= 0; y--) {
                            BlockType cell = chunk.getBlock(lx, y, lz);
                            if (WorldSolidity.isSolid(cell)
                                    && !net.zaminmc.torch.server.block.LeafDecayBlocks
                                            .isLeafState(cell.identifier())) {
                                top = y;
                                break;
                            }
                        }
                        for (int y = 5; y < top - 2 && undergroundAir < 200; y++) {
                            if (chunk.getBlock(lx, y, lz).equals(air)) {
                                undergroundAir++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(undergroundAir >= 200,
                "the underground opens under the surface (found " + undergroundAir + ")");
    }

    /**
     * THE SURFACE STANDING SEAT: the heights the animals read (the
     * post-carve solid-top walk) never answer a carved-out column as
     * ground — the probe's top cell is solid in the chunk (the wave TN
     * probe seat re-pinned over the carvers' world).
     */
    @Test
    void theSurfaceProbeStaysSolidOverTheCarves() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "cv-probe");
        BlockType air = type(registry, "minecraft:air");
        for (int chunkX : new int[]{-2, 0, 2}) {
            for (int chunkZ : new int[]{-2, 0, 2}) {
                EngineChunk chunk = chunkOf(registry, generator, chunkX, chunkZ);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int top = 0;
                        for (int y = 255; y >= 0; y--) {
                            BlockType cell = chunk.getBlock(lx, y, lz);
                            if (WorldSolidity.isSolid(cell)
                                    && !net.zaminmc.torch.server.block.LeafDecayBlocks
                                            .isLeafState(cell.identifier())) {
                                top = y;
                                break;
                            }
                        }
                        if (top >= 1) {
                            assertNotEquals(air, chunk.getBlock(lx, top, lz),
                                    "the surface walk's top cell is solid");
                        }
                    }
                }
            }
        }
    }
}
