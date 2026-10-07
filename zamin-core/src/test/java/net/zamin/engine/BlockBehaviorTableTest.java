package net.zamin.engine.block;

import net.zamin.api.Identifier;
import net.zamin.engine.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the embedded block behavior data against the values of the community
 * dataset it was generated from (PrismarineJS/minecraft-data pc/1.8, MIT), and
 * the derived historical break-timing math including tool speeds.
 */
class BlockBehaviorTableTest {

    @Test
    void stoneBehaviorMatchesCommunityDataset() {
        BlockBehavior stone = BlockBehaviorTable.of(Identifier.parse("minecraft:stone")).orElseThrow();
        assertEquals(1.5, stone.hardness());
        assertTrue(stone.requiresTool(), "stone requires a pickaxe to yield drops");
        assertEquals(1, stone.harvestLevel(), "stone is a tier-1 harvest (any pickaxe material)");
        assertEquals(1, stone.drops().size());
        assertEquals(Identifier.parse("minecraft:cobblestone"), stone.drops().get(0).item());
        assertEquals("rock", stone.material());
    }

    @Test
    void grassBlockDropsDirt() {
        BlockBehavior grass = BlockBehaviorTable.of(Identifier.parse("minecraft:grass_block")).orElseThrow();
        assertEquals(0.6, grass.hardness());
        assertEquals(Identifier.parse("minecraft:dirt"), grass.drops().get(0).item());
        assertFalse(grass.requiresTool());
    }

    @Test
    void oreLadderMatchesCommunityDataset() {
        // blocks.json: coal_ore 16 / iron_ore 15 / diamond_ore 56, hardness 3,
        // material rock, harvestTools minimum tier 1 / 2 / 3, drops 263 / self / 264.
        BlockBehavior coal = BlockBehaviorTable.of(Identifier.parse("minecraft:coal_ore")).orElseThrow();
        assertEquals(3.0, coal.hardness());
        assertEquals(1, coal.harvestLevel());
        assertEquals(Identifier.parse("minecraft:coal"), coal.drops().get(0).item());

        BlockBehavior iron = BlockBehaviorTable.of(Identifier.parse("minecraft:iron_ore")).orElseThrow();
        assertEquals(2, iron.harvestLevel(), "iron ore needs a stone-tier pickaxe");
        assertEquals(Identifier.parse("minecraft:iron_ore"), iron.drops().get(0).item(),
                "iron ore drops itself historically (needs smelting)");

        BlockBehavior diamond = BlockBehaviorTable.of(Identifier.parse("minecraft:diamond_ore")).orElseThrow();
        assertEquals(3, diamond.harvestLevel(), "diamond ore needs an iron-tier pickaxe");
        assertEquals(Identifier.parse("minecraft:diamond"), diamond.drops().get(0).item());
    }

    @Test
    void bedrockIsUnbreakable() {
        BlockBehavior bedrock = BlockBehaviorTable.of(Identifier.parse("minecraft:bedrock")).orElseThrow();
        assertEquals(BlockBehavior.unbreakable(), bedrock);
        assertEquals(Integer.MAX_VALUE, bedrock.breakTicks(1.0, true));
    }

    @Test
    void breakTimingsMatchHistoricalValues() {
        BlockBehavior dirt = BlockBehaviorTable.of(Identifier.parse("minecraft:dirt")).orElseThrow();
        // dirt: hardness 0.5, no tool required -> 15 ticks (0.75s) by hand
        assertEquals(15, dirt.breakTicks(1.0, true));

        BlockBehavior stone = BlockBehaviorTable.of(Identifier.parse("minecraft:stone")).orElseThrow();
        // stone by hand: not harvestable -> 150 ticks (7.5s), yields nothing
        assertEquals(150, stone.breakTicks(1.0, false));
        // stone with a proper tool at hand speed would be 45 ticks (2.25s)
        assertEquals(45, stone.breakTicks(1.0, true));
    }

    @Test
    void toolSpeedsShortenBreakTime() {
        BlockBehavior stone = BlockBehaviorTable.of(Identifier.parse("minecraft:stone")).orElseThrow();
        // 1.5 * 30 / 2 = 22.5 -> 23 ticks (~1.15s) with a wooden pickaxe
        assertEquals(23, stone.breakTicks(2.0, true));
        // 1.5 * 30 / 8 = 5.625 -> 6 ticks with a diamond pickaxe
        assertEquals(6, stone.breakTicks(8.0, true));
        // Speed applies to non-harvestable digs too (fast but yields nothing):
        // 1.5 * 100 / 8 = 18.75 -> 19 ticks instead of 150.
        assertEquals(19, stone.breakTicks(8.0, false));
    }

    @Test
    void unregisteredIdentifierYieldsEmpty() {
        assertTrue(BlockBehaviorTable.of(Identifier.parse("minecraft:does_not_exist")).isEmpty());
    }

    @Test
    void handCanNeverHarvestToolGatedBlocks() {
        BlockBehavior cobble = BlockBehaviorTable.of(Identifier.parse("minecraft:cobblestone")).orElseThrow();
        assertFalse(BlockBehaviorTable.canHarvest(cobble, null));
        BlockBehavior dirt = BlockBehaviorTable.of(Identifier.parse("minecraft:dirt")).orElseThrow();
        assertTrue(BlockBehaviorTable.canHarvest(dirt, null));
    }

    @Test
    void pickaxeTiersGateTheOreLadder() {
        BlockBehavior stone = BlockBehaviorTable.of(Identifier.parse("minecraft:stone")).orElseThrow();
        BlockBehavior coal = BlockBehaviorTable.of(Identifier.parse("minecraft:coal_ore")).orElseThrow();
        BlockBehavior iron = BlockBehaviorTable.of(Identifier.parse("minecraft:iron_ore")).orElseThrow();
        BlockBehavior diamond = BlockBehaviorTable.of(Identifier.parse("minecraft:diamond_ore")).orElseThrow();

        // Tier 1 (wood) harvests stone and coal, not iron or diamond ore.
        assertTrue(BlockBehaviorTable.canHarvest(stone, BuiltinItems.WOODEN_PICKAXE));
        assertTrue(BlockBehaviorTable.canHarvest(coal, BuiltinItems.WOODEN_PICKAXE));
        assertFalse(BlockBehaviorTable.canHarvest(iron, BuiltinItems.WOODEN_PICKAXE));
        assertFalse(BlockBehaviorTable.canHarvest(diamond, BuiltinItems.WOODEN_PICKAXE));

        // Tier 2 (stone) adds iron ore.
        assertTrue(BlockBehaviorTable.canHarvest(iron, BuiltinItems.STONE_PICKAXE));
        assertFalse(BlockBehaviorTable.canHarvest(diamond, BuiltinItems.STONE_PICKAXE));

        // Tier 3 (iron) adds diamond ore; gold stays tier 1 (fast but weak).
        assertTrue(BlockBehaviorTable.canHarvest(diamond, BuiltinItems.IRON_PICKAXE));
        assertTrue(BlockBehaviorTable.canHarvest(diamond, BuiltinItems.DIAMOND_PICKAXE));
        assertFalse(BlockBehaviorTable.canHarvest(diamond, BuiltinItems.GOLDEN_PICKAXE),
                "golden material is tier 1, historically unable to harvest diamond ore");
    }

    @Test
    void wrongToolClassNeverHarvestsOrSpeeds() {
        BlockBehavior stone = BlockBehaviorTable.of(Identifier.parse("minecraft:stone")).orElseThrow();
        // A shovel or axe is no pickaxe: full hand timing, no harvest.
        assertFalse(BlockBehaviorTable.canHarvest(stone, BuiltinItems.DIAMOND_SHOVEL));
        assertFalse(BlockBehaviorTable.canHarvest(stone, BuiltinItems.DIAMOND_AXE));
        assertEquals(1.0, BlockBehaviorTable.speedMultiplier(stone, BuiltinItems.DIAMOND_SHOVEL));

        BlockBehavior dirt = BlockBehaviorTable.of(Identifier.parse("minecraft:dirt")).orElseThrow();
        assertEquals(8.0, BlockBehaviorTable.speedMultiplier(dirt, BuiltinItems.DIAMOND_SHOVEL),
                "shovels accelerate dirt");
        assertEquals(1.0, BlockBehaviorTable.speedMultiplier(dirt, BuiltinItems.WOODEN_PICKAXE),
                "pickaxes do not accelerate dirt");

        BlockBehavior log = BlockBehaviorTable.of(Identifier.parse("minecraft:oak_log")).orElseThrow();
        assertEquals(4.0, BlockBehaviorTable.speedMultiplier(log, BuiltinItems.STONE_AXE),
                "axes accelerate wood");
    }

    @Test
    void pickaxeSpeedsMatchTheDataset() {
        BlockBehavior stone = BlockBehaviorTable.of(Identifier.parse("minecraft:stone")).orElseThrow();
        assertEquals(2.0, BlockBehaviorTable.speedMultiplier(stone, BuiltinItems.WOODEN_PICKAXE));
        assertEquals(4.0, BlockBehaviorTable.speedMultiplier(stone, BuiltinItems.STONE_PICKAXE));
        assertEquals(6.0, BlockBehaviorTable.speedMultiplier(stone, BuiltinItems.IRON_PICKAXE));
        assertEquals(8.0, BlockBehaviorTable.speedMultiplier(stone, BuiltinItems.DIAMOND_PICKAXE));
        assertEquals(12.0, BlockBehaviorTable.speedMultiplier(stone, BuiltinItems.GOLDEN_PICKAXE));
    }
}
