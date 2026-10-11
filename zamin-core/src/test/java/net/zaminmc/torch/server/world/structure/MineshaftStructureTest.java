package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.NormalWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mineshaft placement (Slice 12a — the reference port of
 * MineshaftStructure + MineshaftStart + MineshaftPieces over the
 * engine's deterministic generator): the structures appear under the
 * terrain far from spawn, the material stays below sea level, and the
 * same seed regenerates identical chunks (the engine's determinism
 * contract replacing the reference's cross-chunk start cache).
 */
class MineshaftStructureTest {

    private static FrozenBlockRegistry registry;

    @BeforeAll
    static void setUp() {
        // The engine's own boot assembly (EngineServer's freeze chain):
        // the fluid families carry the water/lava the terrain needs.
        registry = net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new BlockRegistryBuilder())))))))).freeze();
    }

    /** The seed-only world view the start probe needs (seaLevel alone). */
    private static StructureWorld probeWorld() {
        return new StructureWorld() {
            @Override
            public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
                return null;
            }

            @Override
            public boolean isLiquid(net.zaminmc.torch.block.BlockType type) {
                return false;
            }

            @Override
            public void setBlock(int x, int y, int z, net.zaminmc.torch.block.BlockType type) {
            }

            @Override
            public int seaLevel() {
                return NormalWorldGenerator.SEA_LEVEL;
            }

            @Override
            public StructurePalette palette() {
                return null;
            }

            @Override
            public boolean isOpaque(net.zaminmc.torch.block.BlockType type) {
                return true;
            }
        };
    }

    @Test
    void mineshaftsAppearBelowTheTerrainAroundAHitRegion() {
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "mineshaft-scan");
        MineshaftStructure mineshafts = new MineshaftStructure(generator.seed());
        // Walk regions far from spawn (the distance gate always passes
        // beyond 80 chunks) until one carries a mineshaft start.
        StructureStart hit = null;
        int hitX = 0;
        int hitZ = 0;
        for (int regionX = 80; regionX < 400 && hit == null; regionX++) {
            for (int regionZ = 80; regionZ < 400; regionZ++) {
                StructureStart start = mineshafts.createStart(regionX, regionZ, probeWorld());
                if (start != null) {
                    hit = start;
                    hitX = regionX;
                    hitZ = regionZ;
                    break;
                }
            }
        }
        assertNotNull(hit, "some region between 80 and 400 must carry a start (0.004 chance)");
        assertTrue(!hit.getPieces().isEmpty(), "the start carries pieces");
        assertTrue(hit.getBounds().maxY < NormalWorldGenerator.SEA_LEVEL - 8,
                "the re-base put the whole structure below the sea floor: "
                        + hit.getBounds().minY + ".." + hit.getBounds().maxY);

        // Generate the chunks over the start's actual piece extents: the
        // material (fences, planks, rails, cobwebs) must appear below the
        // terrain.
        int fromChunkX = (hit.getBounds().minX >> 4) - 1;
        int toChunkX = (hit.getBounds().maxX >> 4) + 1;
        int fromChunkZ = (hit.getBounds().minZ >> 4) - 1;
        int toChunkZ = (hit.getBounds().maxZ >> 4) + 1;
        int found = 0;
        for (int cx = fromChunkX; cx <= toChunkX; cx++) {
            for (int cz = fromChunkZ; cz <= toChunkZ; cz++) {
                EngineChunk chunk = new EngineChunk(new ChunkPosition(cx, cz),
                        registry.require(net.zaminmc.torch.util.Identifier.parse("minecraft:air")));
                generator.generate(chunk);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 1; y < NormalWorldGenerator.SEA_LEVEL - 2; y++) {
                            var id = chunk.getBlock(lx, y, lz).identifier().toString();
                            if (id.equals("minecraft:oak_fence") || id.equals("minecraft:oak_planks")
                                    || id.equals("minecraft:rail") || id.equals("minecraft:rail_ew")
                                    || id.equals("minecraft:cobweb") || id.equals("minecraft:dirt")
                                    && y < hit.getBounds().minY + 2) {
                                found++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(found > 0,
                "the start at region " + hitX + "," + hitZ + " left material in its own bounds: "
                        + found + " cells");
    }

    @Test
    void theSameSeedRegeneratesIdenticalChunks() {
        NormalWorldGenerator one = new NormalWorldGenerator(registry, "twin-world");
        NormalWorldGenerator two = new NormalWorldGenerator(registry, "twin-world");
        ChunkPosition position = new ChunkPosition(300, 300);
        EngineChunk chunkA = new EngineChunk(position,
                registry.require(net.zaminmc.torch.util.Identifier.parse("minecraft:air")));
        EngineChunk chunkB = new EngineChunk(position,
                registry.require(net.zaminmc.torch.util.Identifier.parse("minecraft:air")));
        one.generate(chunkA);
        two.generate(chunkB);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = 0; y < 256; y++) {
                    assertEquals(chunkA.getBlock(lx, y, lz), chunkB.getBlock(lx, y, lz),
                            "cell " + lx + "," + y + "," + lz);
                }
            }
        }
    }
}
