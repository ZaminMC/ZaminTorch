package net.zamin.engine.block;

import net.zamin.api.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the embedded block behavior data against the values of the community
 * dataset it was generated from (PrismarineJS/minecraft-data pc/1.8, MIT), and
 * the derived historical break-timing math.
 */
class BlockBehaviorTableTest {

    @Test
    void stoneBehaviorMatchesCommunityDataset() {
        BlockBehavior stone = BlockBehaviorTable.of(Identifier.parse("minecraft:stone")).orElseThrow();
        assertEquals(1.5, stone.hardness());
        assertTrue(stone.requiresTool(), "stone requires a pickaxe to yield drops");
        assertEquals(1, stone.drops().size());
        assertEquals(Identifier.parse("minecraft:cobblestone"), stone.drops().get(0).item());
        assertEquals("rock", stone.material());
    }

    @Test
    void grassBlockDropsDirt() {
        BlockBehavior grass = BlockBehaviorTable.of(Identifier.parse("minecraft:grass_block")).orElseThrow();
        assertEquals(0.6, grass.hardness());
        assertEquals(Identifier.parse("minecraft:dirt"), grass.drops().get(0).item());
        assertTrue(!grass.requiresTool());
    }

    @Test
    void bedrockIsUnbreakable() {
        BlockBehavior bedrock = BlockBehaviorTable.of(Identifier.parse("minecraft:bedrock")).orElseThrow();
        assertEquals(BlockBehavior.unbreakable(), bedrock);
        assertEquals(Integer.MAX_VALUE, bedrock.breakTicks(true));
    }

    @Test
    void breakTimingsMatchHistoricalValues() {
        BlockBehavior dirt = BlockBehaviorTable.of(Identifier.parse("minecraft:dirt")).orElseThrow();
        // dirt: hardness 0.5, no tool required -> 15 ticks (0.75s) by hand
        assertEquals(15, dirt.breakTicks(true));

        BlockBehavior stone = BlockBehaviorTable.of(Identifier.parse("minecraft:stone")).orElseThrow();
        // stone by hand: not harvestable -> 150 ticks (7.5s), yields nothing
        assertEquals(150, stone.breakTicks(false));
        // stone with an (unimplemented yet) tool would be 45 ticks (2.25s)
        assertEquals(45, stone.breakTicks(true));
    }

    @Test
    void unregisteredIdentifierYieldsEmpty() {
        assertTrue(BlockBehaviorTable.of(Identifier.parse("minecraft:does_not_exist")).isEmpty());
    }

    @Test
    void handCanNeverHarvestToolGatedBlocks() {
        BlockBehavior cobble = BlockBehaviorTable.of(Identifier.parse("minecraft:cobblestone")).orElseThrow();
        assertTrue(BlockBehaviorTable.canHarvest(cobble, null) == false);
        BlockBehavior dirt = BlockBehaviorTable.of(Identifier.parse("minecraft:dirt")).orElseThrow();
        assertTrue(BlockBehaviorTable.canHarvest(dirt, null));
    }
}
