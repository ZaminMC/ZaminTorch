package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCRATCH PROBE (the terrain-family audit): boots the default-name generator
 * and inspects the fresh-player spawn column against the ACTUAL generated
 * chunk — the engine's join law consults the generator's stored height only,
 * never the generated chunk, so this measures what a fresh player lands in.
 */
class TerrainSpawnProbeTest {

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

    @Test
    void probeTheSpawnColumn() {
        NormalWorldGenerator generator = new NormalWorldGenerator(registry(), "world");
        Position spawn = generator.spawnPosition();
        System.out.println("PROBE seed(world) = " + generator.seed());
        System.out.println("PROBE spawn = " + spawn);
        System.out.println("PROBE biome@spawn = " + generator.biomeAt(
                spawn.toBlockPosition().x(), spawn.toBlockPosition().z()));
        System.out.println("PROBE heightAt@spawn = " + generator.heightAt(
                spawn.toBlockPosition().x(), spawn.toBlockPosition().z()));
        int sx = spawn.toBlockPosition().x();
        int sz = spawn.toBlockPosition().z();
        int chunkX = sx >> 4;
        int chunkZ = sz >> 4;
        int lx = sx - (chunkX << 4);
        int lz = sz - (chunkZ << 4);
        EngineChunk chunk = new EngineChunk(
                new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ), BuiltinBlocks.AIR);
        generator.generate(chunk);
        // The column scan: topmost solid cell at the spawn column.
        int topSolid = -1;
        for (int y = 255; y >= 0; y--) {
            net.zaminmc.torch.block.BlockType block = chunk.getBlock(lx, y, lz);
            if (net.zaminmc.torch.server.block.WorldSolidity.isSolid(block)) {
                topSolid = y;
                break;
            }
        }
        System.out.println("PROBE topSolid@spawnColumn = " + topSolid + " ("
                + chunk.getBlock(lx, topSolid, lz).identifier().value() + ")");
        int feetY = spawn.toBlockPosition().y();
        System.out.println("PROBE feetCell(spawnY=" + feetY + ") = "
                + chunk.getBlock(lx, feetY, lz).identifier().value());
        System.out.println("PROBE groundCell(spawnY-1=" + (feetY - 1) + ") = "
                + chunk.getBlock(lx, feetY - 1, lz).identifier().value());
        System.out.println("PROBE aboveCell(spawnY+1=" + (feetY + 1) + ") = "
                + chunk.getBlock(lx, feetY + 1, lz).identifier().value());
        System.out.println("PROBE buried? feetCell solid = "
                + net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                        chunk.getBlock(lx, feetY, lz)));
        System.out.println("PROBE gap feet->topSolid = " + (topSolid - feetY));

        // The seed determinism probe: the same name, twice.
        NormalWorldGenerator again = new NormalWorldGenerator(registry(), "world");
        System.out.println("PROBE seed(world) again = " + again.seed());
        NormalWorldGenerator other = new NormalWorldGenerator(registry(), "world2");
        System.out.println("PROBE seed(world2) = " + other.seed());
        assertTrue(generator.seed() == again.seed(),
                "the name-derived seed is the same across fresh boots (the finding)");
    }

    @Test
    void probeTheSpawnBurialCensus() {
        // The reference's ServerPlayerEntity ctor resolves the fresh player's
        // cell against the ACTUAL chunk (getSurfaceHeight + the unstuck walk).
        // The engine consults the generator's stored height alone — any
        // decoration (a tree, a cactus...) at the spawn column buries the
        // feet. Census over several world names: how often does the stored
        // spawn cell stand inside a solid block?
        String[] names = {"world", "test", "New World", "myworld", "server",
                "survival", "creative", "map1", "abc", "xyz", "hello", "terra"};
        int buried = 0;
        for (String name : names) {
            NormalWorldGenerator gen = new NormalWorldGenerator(registry(), name);
            Position spawn = gen.spawnPosition();
            int sx = spawn.toBlockPosition().x();
            int sz = spawn.toBlockPosition().z();
            int chunkX = sx >> 4;
            int chunkZ = sz >> 4;
            int lx = sx - (chunkX << 4);
            int lz = sz - (chunkZ << 4);
            EngineChunk chunk = new EngineChunk(
                    new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ), BuiltinBlocks.AIR);
            gen.generate(chunk);
            int feetY = spawn.toBlockPosition().y();
            String feet = chunk.getBlock(lx, feetY, lz).identifier().value();
            String head = chunk.getBlock(lx, feetY + 1, lz).identifier().value();
            boolean feetSolid = net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                    chunk.getBlock(lx, feetY, lz));
            boolean headSolid = net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                    chunk.getBlock(lx, feetY + 1, lz));
            if (feetSolid || headSolid) {
                buried++;
            }
            System.out.println("PROBE census[" + name + "] spawn=" + spawn
                    + " feet=" + feet + (feetSolid ? "(SOLID)" : "")
                    + " head=" + head + (headSolid ? "(SOLID)" : "")
                    + " biome=" + gen.biomeAt(sx, sz));
        }
        System.out.println("PROBE census buriedCount=" + buried + "/" + names.length);
    }

    @Test
    void probeTheOreAndCaveCensus() {
        // The reference's ores are VEIN features (per-chunk Random rolls:
        // coal 20x17@0-128, iron 20x9@0-64, gold 2x9@0-32, redstone 8x8@0-16,
        // diamond 1x8@0-16, lapis 1x7@16-16 + dirt 10x33, gravel 8x33,
        // granite/diorite 10x33@0-80). The engine writes per-BLOCK hash
        // scatter. Census: ore counts over an 8x8 chunk area on the default
        // world, plus the cave cell count below the surface.
        NormalWorldGenerator gen = new NormalWorldGenerator(registry(), "world");
        java.util.Map<String, Integer> oreCounts = new java.util.TreeMap<>();
        int caveCells = 0;
        int solidCells = 0;
        int chunks = 0;
        for (int cx = 10; cx < 14 && chunks < 16; cx++) {
            for (int cz = 10; cz < 14 && chunks < 16; cz++, chunks++) {
                EngineChunk chunk = new EngineChunk(
                        new net.zaminmc.torch.block.ChunkPosition(cx, cz), BuiltinBlocks.AIR);
                gen.generate(chunk);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 1; y < 255; y++) {
                            String id = chunk.getBlock(lx, y, lz).identifier().value();
                            if (id.endsWith("_ore")) {
                                oreCounts.merge(id, 1, Integer::sum);
                            }
                            if (id.equals("air")) {
                                // below the column's height = a carve or a cave
                                // (the fill only writes air below h through caves)
                                caveCells++;
                            } else if (!id.equals("bedrock")) {
                                solidCells++;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("PROBE ore census over " + chunks + " chunks: " + oreCounts);
        System.out.println("PROBE air-below-surface cells = " + caveCells
                + ", solid = " + solidCells);
    }
}
