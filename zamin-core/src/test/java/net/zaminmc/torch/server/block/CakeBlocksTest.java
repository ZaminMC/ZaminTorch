package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cake family (Slice 46 — CakeBlock 1:1): the seven BITES states with
 * the reference's exact metadata band, the eat's nutrition write, the
 * analog read, and the family tables.
 */
class CakeBlocksTest {

    @Test
    void theSevenStatesCarryTheReferenceBand() {
        assertEquals("minecraft:cake", CakeBlocks.stateId(0).toString());
        for (int bites = 0; bites <= 6; bites++) {
            EngineBlockType state = CakeBlocks.state(bites);
            assertEquals(bites, state.legacyMetadata());
            assertEquals(bites, CakeBlocks.bitesOf(state.identifier()));
            assertTrue(CakeBlocks.isCake(state.identifier()));
            // The behavior rows: the 0.0 plant, NO drops (getDropItem's
            // null + getBaseDropCount's 0 — the slices come only from the
            // eat arm).
            BlockBehavior behavior = BlockBehaviorTable.of(state.identifier()).orElseThrow();
            assertEquals(0.0, behavior.hardness());
            assertTrue(behavior.drops().isEmpty());
            // The light rows: the CUTOUT partial slab.
            assertEquals(0, BlockLightTable.lookup(state.identifier()).orElseThrow().filter());
        }
        // The analog read: the whole cake's 14 down to the last crumb's 2.
        assertEquals(14, CakeBlocks.analogSignal(Identifier.parse("minecraft:cake")));
        assertEquals(10, CakeBlocks.analogSignal(Identifier.parse("minecraft:cake_bite2")));
        assertEquals(4, CakeBlocks.analogSignal(Identifier.parse("minecraft:cake_bite5")));
        assertEquals(2, CakeBlocks.analogSignal(Identifier.parse("minecraft:cake_bite6")));
        // The eat's own write: bites + 1, air at the seventh.
        assertEquals("minecraft:cake_bite1", CakeBlocks.afterBite(0).toString());
        assertEquals("minecraft:cake_bite6", CakeBlocks.afterBite(5).toString());
        assertEquals(null, CakeBlocks.afterBite(6));
        // The wire rows: the seven states at 92 with the BITES nibble.
        assertEquals(92, CakeBlocks.WIRE_ID);
    }

    @Test
    void theSupportAndCraftReadsCarryTheReferenceRules() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        var world = new net.zaminmc.torch.server.world.EngineWorld("cake-it", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        // The support read: the block below must be SOLID (stone carries,
        // air after a removal pops the cake with no drop).
        net.zaminmc.torch.block.BlockPosition at = new net.zaminmc.torch.block.BlockPosition(10, 6, 10);
        world.setBlock(at.offset(0, -1, 0), registry.require(Identifier.parse("minecraft:stone")));
        world.setBlock(at, CakeBlocks.state(0));
        assertTrue(CakeBlocks.isSupported(world, at));
        // The cake recipe + the milk remainder (Item.getRecipeRemainder's
        // own consult — the cake's three empty buckets come back).
        assertEquals(1, net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:milk_bucket")).isPresent() ? 1 : 0);
        net.zaminmc.torch.item.ItemStack milk = new net.zaminmc.torch.item.ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.MILK_BUCKET, 1, 0);
        net.zaminmc.torch.item.ItemStack remainder =
                net.zaminmc.torch.server.crafting.CraftingRemainders.remainderOf(milk);
        assertEquals("minecraft:bucket", remainder.type().identifier().toString());
        assertEquals(null, net.zaminmc.torch.server.crafting.CraftingRemainders.remainderOf(
                new net.zaminmc.torch.item.ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.WHEAT, 1, 0)));
    }
}
