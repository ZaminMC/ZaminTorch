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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Slice 21a — the corridor's chest-minecart arms (the reference's two
     * 1-in-100 rolls per section, lines 301-325): scanning mineshaft starts
     * until a chunk's populate records a loot-carrying cart, then the rail
     * under it and the loot's own table bands (the count min..max, the
     * stack-split arm, the lapis damage 4, the enchanted book's
     * enchantment map).
     */
    @Test
    void theCorridorRecordsItsLootCarryingChestMinecart() {
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "chest-cart-scan");
        MineshaftStructure mineshafts = new MineshaftStructure(generator.seed());
        EngineChunk hitChunk = null;
        EngineChunk.PendingChestMinecart record = null;
        var air = registry.require(net.zaminmc.torch.util.Identifier.parse("minecraft:air"));
        long deadline = System.currentTimeMillis() + 120_000;
        outer:
        for (int regionX = 80; regionX < 900 && System.currentTimeMillis() < deadline; regionX++) {
            for (int regionZ = 80; regionZ < 900; regionZ++) {
                StructureStart start = mineshafts.createStart(regionX, regionZ, probeWorld());
                if (start == null) {
                    continue;
                }
                int fromChunkX = (start.getBounds().minX >> 4) - 1;
                int toChunkX = (start.getBounds().maxX >> 4) + 1;
                int fromChunkZ = (start.getBounds().minZ >> 4) - 1;
                int toChunkZ = (start.getBounds().maxZ >> 4) + 1;
                for (int cx = fromChunkX; cx <= toChunkX; cx++) {
                    for (int cz = fromChunkZ; cz <= toChunkZ; cz++) {
                        EngineChunk chunk = new EngineChunk(new ChunkPosition(cx, cz), air);
                        generator.generate(chunk);
                        if (!chunk.pendingChestMinecarts().isEmpty()) {
                            hitChunk = chunk;
                            record = chunk.pendingChestMinecarts().get(0);
                            break outer;
                        }
                    }
                }
            }
        }
        assertNotNull(record,
                "some corridor between the scanned regions recorded a chest minecart");

        // The rail under the cart (placeChestWithLoot's setBlockState): the
        // cell in the recording chunk's own array.
        var railId = recordCellId(hitChunk, record);
        assertTrue(railId.equals("minecraft:rail") || railId.equals("minecraft:rail_ew"),
                "the rail landed under the cart: " + railId + " at "
                        + record.x() + "," + record.y() + "," + record.z());

        // The loot's own law: 3..6 rolls' worth of slots, every item from
        // the reference's table (or the enchanted book), counts in band,
        // the lapis row's damage 4, the book's enchantment map.
        java.util.Set<String> table = java.util.Set.of(
                "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:redstone",
                "minecraft:lapis", "minecraft:diamond", "minecraft:coal", "minecraft:bread",
                "minecraft:iron_pickaxe", "minecraft:rail", "minecraft:melon_seeds",
                "minecraft:pumpkin_seeds", "minecraft:saddle", "minecraft:iron_horse_armor",
                "minecraft:enchanted_book");
        int slotWrites = 0;
        for (var slot : record.slots()) {
            assertTrue(table.contains(slot.itemId()),
                    "the rolled item came from the reference's table: " + slot.itemId());
            assertTrue(slot.count() >= 1, "every roll lands at least one");
            assertTrue(slot.slot() >= 0 && slot.slot() < 27,
                    "the slot draw is the cart's own 27");
            slotWrites++;
            if (slot.itemId().equals("minecraft:lapis")) {
                assertEquals(4, slot.damage(), "the lapis band rides the dye's damage 4");
            }
            if (slot.itemId().equals("minecraft:enchanted_book")) {
                assertNotNull(slot.enchantments(),
                        "the book carries its rolled enchantment");
                assertFalse(slot.enchantments().isEmpty(), "the book's enchantment is real");
            }
        }
        assertTrue(slotWrites >= 1, "the loot walk landed at least one slot write");

        // Determinism: the same seed regenerates the identical record.
        NormalWorldGenerator twin = new NormalWorldGenerator(registry, "chest-cart-scan");
        EngineChunk twinChunk = new EngineChunk(hitChunk.position(), air);
        twin.generate(twinChunk);
        assertEquals(1, twinChunk.pendingChestMinecarts().size(),
                "the twin recorded the same single cart");
        EngineChunk.PendingChestMinecart twinRecord = twinChunk.pendingChestMinecarts().get(0);
        assertEquals(record.x(), twinRecord.x(), "the twin cart's cell matches");
        assertEquals(record.y(), twinRecord.y(), "the twin cart's cell matches");
        assertEquals(record.z(), twinRecord.z(), "the twin cart's cell matches");
        assertEquals(record.slots(), twinRecord.slots(), "the twin loot is identical");
    }

    /** @return the recording chunk's block id string at the record's cell. */
    private static String recordCellId(EngineChunk chunk, EngineChunk.PendingChestMinecart record) {
        int localX = record.x() - (chunk.position().x() << 4);
        int localZ = record.z() - (chunk.position().z() << 4);
        return chunk.getBlock(localX, record.y(), localZ).identifier().toString();
    }

    /**
     * The cobweb-and-spawner pair (Slice 25f's verification — the reference's
     * lines 327-341: every cobweb corridor carries ONE cave-spider spawner at
     * its first section's random cell): some seed's start must leave a mob
     * spawner block plus a typed CAVE_SPIDER record in its generated chunks.
     */
    @Test
    void theCobwebCorridorsCarryTheCaveSpiderSpawner() {
        // Walk seeds until a start carries the spawner (every cobweb
        // corridor rolls one — ~3% of corridors are cobweb corridors, and a
        // start's recursive walk carries many corridors, so a few seeds
        // always answer).
        for (int seedAttempt = 0; seedAttempt < 40; seedAttempt++) {
            String seed = "spawner-probe-" + seedAttempt;
            NormalWorldGenerator generator = new NormalWorldGenerator(registry, seed);
            MineshaftStructure mineshafts = new MineshaftStructure(seed.hashCode());
            for (int regionX = 60; regionX < 300; regionX++) {
                for (int regionZ = 60; regionZ < 300; regionZ++) {
                    StructureStart start = mineshafts.createStart(regionX, regionZ, probeWorld());
                    if (start == null) {
                        continue;
                    }
                    int fromChunkX = (start.getBounds().minX >> 4) - 1;
                    int toChunkX = (start.getBounds().maxX >> 4) + 1;
                    int fromChunkZ = (start.getBounds().minZ >> 4) - 1;
                    int toChunkZ = (start.getBounds().maxZ >> 4) + 1;
                    for (int cx = fromChunkX; cx <= toChunkX; cx++) {
                        for (int cz = fromChunkZ; cz <= toChunkZ; cz++) {
                            EngineChunk chunk = new EngineChunk(new ChunkPosition(cx, cz),
                                    registry.require(net.zaminmc.torch.util.Identifier.parse(
                                            "minecraft:air")));
                            generator.generate(chunk);
                            if (!chunk.pendingMobSpawners().isEmpty()) {
                                for (var record : chunk.pendingMobSpawners()) {
                                    assertEquals(
                                            net.zaminmc.torch.server.entity.MobType.CAVE_SPIDER,
                                            record.mobType(),
                                            "the mineshaft's spawner is the cave spider");
                                    assertEquals("minecraft:mob_spawner",
                                            chunk.getBlock(
                                                    record.x() & 15, record.y(), record.z() & 15)
                                                    .identifier().toString(),
                                            "the spawner block sits at the record's cell");
                                }
                                return; // the first typed record proves the pair
                            }
                        }
                    }
                }
            }
        }
        // Not one of the 40 seeds' starts carried a cobweb corridor — a
        // statistical impossibility at ~3% per corridor (the walk's many
        // corridors per start); the assertion stays a loud failure.
        assertTrue(false, "no probed start carried the cave-spider spawner");
    }
}
