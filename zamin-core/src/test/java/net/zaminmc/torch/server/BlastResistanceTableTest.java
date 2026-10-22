package net.zaminmc.torch.server;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlastResistanceTable;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blast-resistance table's census and spot pins: every registered block
 * resolves a READ value (the fold never leaves a registered family behind —
 * the same census convention the registry's own 896-row pin carries), and
 * the known vanilla values stand (the extraction's verified set).
 */
class BlastResistanceTableTest {

    private static FrozenBlockRegistry registry() {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    @Test
    void everyRegisteredBlockResolvesABlastValue() {
        FrozenBlockRegistry registry = registry();
        int covered = 0;
        StringBuilder missing = new StringBuilder();
        for (var type : registry.all()) {
            Float value = BlastResistanceTable.read(type.identifier());
            if (value == null) {
                missing.append(' ').append(type.identifier().value());
            } else {
                assertTrue(value >= 0.0f && value < 4000000.1f,
                        "the value band: " + type.identifier().value() + " -> " + value);
                covered++;
            }
        }
        assertEquals("", missing.toString(),
                "the fold must cover every registered identifier (missing:" + missing + ")");
        assertEquals(896, covered, "the census rides the registry's own 896-row pin");
    }

    @Test
    void theKnownVanillaValuesStand() {
        // The extraction's verified set (scripts/extract-blast-resistance.py
        // — the reference's Block.init() chains): the READ values the
        // explosion pays per crossing.
        assertEquals(6.0f, BlastResistanceTable.read(Identifier.parse("minecraft:stone")), 1e-6f);
        assertEquals(3600000.0f, BlastResistanceTable.read(Identifier.parse("minecraft:bedrock")), 1e-3f);
        assertEquals(1200.0f, BlastResistanceTable.read(Identifier.parse("minecraft:obsidian")), 1e-3f);
        assertEquals(3.0f, BlastResistanceTable.read(Identifier.parse("minecraft:oak_planks")), 1e-6f);
        assertEquals(6.0f, BlastResistanceTable.read(Identifier.parse("minecraft:cobblestone")), 1e-6f);
        assertEquals(0.5f, BlastResistanceTable.read(Identifier.parse("minecraft:dirt")), 1e-6f);
        assertEquals(0.8f, BlastResistanceTable.read(Identifier.parse("minecraft:sandstone")), 1e-6f);
        assertEquals(2.0f, BlastResistanceTable.read(Identifier.parse("minecraft:log")), 1e-6f);
        assertEquals(0.2f, BlastResistanceTable.read(Identifier.parse("minecraft:leaves")), 1e-6f);
        assertEquals(9.0f, BlastResistanceTable.read(Identifier.parse("minecraft:end_stone")), 1e-6f);
        assertEquals(3.5f, BlastResistanceTable.read(Identifier.parse("minecraft:lit_furnace")), 1e-6f);
        assertEquals(100.0f, BlastResistanceTable.read(Identifier.parse("minecraft:water")), 1e-6f);
        assertEquals(100.0f, BlastResistanceTable.read(Identifier.parse("minecraft:flowing_lava")), 1e-6f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:fire")), 1e-6f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:tnt")), 1e-6f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:torch")), 1e-6f);
        // The state fold: the flattened forms resolve onto their families.
        assertEquals(3.5f, BlastResistanceTable.read(Identifier.parse("minecraft:furnace_lit_east")), 1e-6f);
        assertEquals(100.0f, BlastResistanceTable.read(Identifier.parse("minecraft:still_water_15")), 1e-6f);
        assertEquals(6.0f, BlastResistanceTable.read(Identifier.parse("minecraft:stone_slab_top")), 1e-6f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:fire_age7")), 1e-6f);
        // The renames: the engine's pre-flattening bases.
        assertEquals(0.6f, BlastResistanceTable.read(Identifier.parse("minecraft:grass_block")), 1e-6f);
        assertEquals(1200.0f, BlastResistanceTable.read(Identifier.parse("minecraft:anvil")), 1e-3f);
    }
}
