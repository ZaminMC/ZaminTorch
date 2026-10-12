package net.zaminmc.torch.server.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the embedded tool data against the community dataset
 * (PrismarineJS/minecraft-data pc/1.8 materials.json + items.json, MIT) and
 * the lookup contract the gameplay rules rely on.
 */
class ToolsTest {

    @Test
    void toolMaterialsMatchTheDataset() {
        // materials.json speed multipliers, blocks.json harvest tiers, items.json durability.
        assertEquals(2.0, ToolMaterial.WOOD.speedMultiplier());
        assertEquals(4.0, ToolMaterial.STONE.speedMultiplier());
        assertEquals(6.0, ToolMaterial.IRON.speedMultiplier());
        assertEquals(8.0, ToolMaterial.DIAMOND.speedMultiplier());
        assertEquals(12.0, ToolMaterial.GOLD.speedMultiplier());

        assertEquals(1, ToolMaterial.WOOD.harvestLevel());
        assertEquals(2, ToolMaterial.STONE.harvestLevel());
        assertEquals(3, ToolMaterial.IRON.harvestLevel());
        assertEquals(4, ToolMaterial.DIAMOND.harvestLevel());
        assertEquals(1, ToolMaterial.GOLD.harvestLevel());

        assertEquals(59, ToolMaterial.WOOD.maxDurability());
        assertEquals(131, ToolMaterial.STONE.maxDurability());
        assertEquals(250, ToolMaterial.IRON.maxDurability());
        assertEquals(1561, ToolMaterial.DIAMOND.maxDurability());
        assertEquals(32, ToolMaterial.GOLD.maxDurability());
    }

    @Test
    void everyToolItemCarriesItsSpec() {
        assertEquals(ToolClass.PICKAXE, Tools.specOf(BuiltinItems.WOODEN_PICKAXE).orElseThrow().toolClass());
        assertEquals(ToolMaterial.WOOD, Tools.specOf(BuiltinItems.WOODEN_PICKAXE).orElseThrow().material());
        assertEquals(ToolClass.AXE, Tools.specOf(BuiltinItems.GOLDEN_AXE).orElseThrow().toolClass());
        assertEquals(ToolMaterial.GOLD, Tools.specOf(BuiltinItems.GOLDEN_AXE).orElseThrow().material());
        assertEquals(ToolClass.SHOVEL, Tools.specOf(BuiltinItems.IRON_SHOVEL).orElseThrow().toolClass());
        assertEquals(ToolClass.SWORD, Tools.specOf(BuiltinItems.DIAMOND_SWORD).orElseThrow().toolClass());
        assertEquals(ToolClass.SHEARS, Tools.specOf(BuiltinItems.SHEARS).orElseThrow().toolClass());
        assertEquals(ToolMaterial.IRON, Tools.specOf(BuiltinItems.SHEARS).orElseThrow().material());
    }

    @Test
    void toolsStackToOneAndCarryDatasetDurability() {
        assertEquals(1, BuiltinItems.WOODEN_PICKAXE.maxStackSize());
        assertEquals(59, BuiltinItems.WOODEN_PICKAXE.maxDurability());
        assertEquals(1561, BuiltinItems.DIAMOND_PICKAXE.maxDurability());
        assertEquals(238, BuiltinItems.SHEARS.maxDurability(), "shears use their own dataset limit");
    }

    @Test
    void nonToolItemsHaveNoSpec() {
        assertTrue(Tools.specOf(BuiltinItems.DIRT).isEmpty());
        assertTrue(Tools.specOf(null).isEmpty());
    }
}
