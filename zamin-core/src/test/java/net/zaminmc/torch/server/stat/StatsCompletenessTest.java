package net.zaminmc.torch.server.stat;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stats-completeness fold (Slice L4 — StatList.initMineStats 1:1):
 * vanilla keys the mine stats per registry BLOCK (one row each, the
 * 1.8.8 registry names), and the 1.8.8 client DROPS every unknown key
 * (StatList.getOneStat's null walk). Every registered engine block's
 * mine key must therefore resolve into the registry's own name set —
 * this test walks the whole registry and asserts it, so a new state
 * family without its fold fails here rather than counting on a key no
 * client resolves.
 */
class StatsCompletenessTest {

    @Test
    void everyRegisteredBlockCountsOnARegistryRow() {
        FrozenBlockRegistry registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        List<String> unresolved = new ArrayList<>();
        for (var type : registry.all()) {
            String value = type.identifier().value();
            if (value.equals("air")) {
                continue; // air has no mine row (getEnableStats' own gate)
            }
            if (!type.identifier().namespace().equals("minecraft")) {
                continue; // the plugin/mod namespace carries its own scheme
            }
            String key = EngineStats.mineBlockOf(type.identifier());
            // stat.mineBlock.minecraft.<row> — the row must be one of the
            // 1.8.8 registry's own names (the set oracle, not the consult:
            // the row is already the folded output).
            String row = key.substring("stat.mineBlock.minecraft.".length());
            if (!EngineStats.blockRegistryNames().contains(row)) {
                unresolved.add(value + " -> " + key);
            }
        }
        assertTrue(unresolved.isEmpty(),
                "blocks counting on rows the 1.8.8 client drops: " + unresolved);
    }

    @Test
    void everyRegisteredItemCountsOnARegistryRow() {
        // The item rows resolve over the UNION: the pure items (256+)
        // plus the block items (the block registry's names below 256).
        java.util.List<String> unresolved = new java.util.ArrayList<>();
        for (var item : net.zaminmc.torch.server.item.BuiltinItems.all()) {
            if (!item.identifier().namespace().equals("minecraft")) {
                continue;
            }
            String key = EngineStats.useItemOf(item.identifier());
            String row = key.substring("stat.useItem.minecraft.".length());
            if (!EngineStats.itemRowSet().contains(row)) {
                unresolved.add(item.identifier().value() + " -> " + key);
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(unresolved.isEmpty(),
                "items counting on rows the 1.8.8 client drops: " + unresolved);
    }

    @Test
    void theRegistryOracleCarriesThe197Rows() {
        // The reference's own registrations: ids 1-197, one name each
        // (id 0 is air, unregistered).
        List<String> registry = EngineStats.blockRegistryNames();
        assertEquals(197, registry.size());
        assertEquals("stone", registry.get(0));    // id 1
        assertEquals("grass", registry.get(1));    // id 2
        assertEquals("dark_oak_door", registry.get(196)); // id 197
    }

    @Test
    void theStripFoldsTheStateFamiliesOntoTheirRows() {
        // The depth walks: the suffix chains down to the registry base.
        assertEquals("stat.mineBlock.minecraft.oak_stairs",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:oak_stairs_east")));
        assertEquals("stat.mineBlock.minecraft.oak_stairs",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:oak_stairs_east_top")));
        assertEquals("stat.mineBlock.minecraft.furnace",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:furnace_north")));
        assertEquals("stat.mineBlock.minecraft.lit_furnace",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:furnace_lit_north")));
        assertEquals("stat.mineBlock.minecraft.chest",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:chest_north")));
        assertEquals("stat.mineBlock.minecraft.wheat",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:wheat_stage7")));
        assertEquals("stat.mineBlock.minecraft.wooden_door",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:oak_door_east_top")));
        assertEquals("stat.mineBlock.minecraft.wooden_slab",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:oak_slab_top")));
        assertEquals("stat.mineBlock.minecraft.stone_slab",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:stone_slab_top")));
        // The prefix families through the name table.
        assertEquals("stat.mineBlock.minecraft.monster_egg",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:infested_stone")));
        assertEquals("stat.mineBlock.minecraft.red_flower",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:blue_orchid")));
        assertEquals("stat.mineBlock.minecraft.skull",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:skull_north")));
    }

    @Test
    void theLiteralFormsKeepTheirOwnRegistryRows() {
        // The old scheme's merges are GONE — vanilla keeps its own rows
        // for every registry Block: the grass, the farmland, the
        // doubles, the lit and powered forms, the flowing fluids, the
        // redstone torch (76 is the lit form's own name).
        assertEquals("stat.mineBlock.minecraft.grass",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:grass_block")));
        assertEquals("stat.mineBlock.minecraft.farmland",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:farmland")));
        assertEquals("stat.mineBlock.minecraft.double_stone_slab",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:double_stone_slab")));
        assertEquals("stat.mineBlock.minecraft.lit_furnace",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:furnace_lit")));
        assertEquals("stat.mineBlock.minecraft.lit_redstone_ore",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:lit_redstone_ore")));
        assertEquals("stat.mineBlock.minecraft.powered_repeater",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:powered_repeater")));
        assertEquals("stat.mineBlock.minecraft.flowing_water",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:flowing_water_2")));
        assertEquals("stat.mineBlock.minecraft.redstone_torch",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:redstone_torch")));
        assertEquals("stat.mineBlock.minecraft.web",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:cobweb")));
        assertEquals("stat.mineBlock.minecraft.melon_block",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:melon")));
    }
}
