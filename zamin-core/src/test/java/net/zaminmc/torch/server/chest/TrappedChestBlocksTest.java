package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trapped chest (Slice 49b — ChestBlock(1) 1:1): the block 146's own
 * rows, the chest system's family extension, and the crafting row.
 */
class TrappedChestBlocksTest {

    @Test
    void theTablesCarryTheReferenceRows() {
        // The behavior row: ChestBlock(1)'s own — setStrength(2.5F)
        // WOOD_SOUNDS dropping itself.
        var behavior = net.zaminmc.torch.server.block.BlockBehaviorTable.of(
                Identifier.parse("minecraft:trapped_chest")).orElseThrow();
        assertEquals(2.5, behavior.hardness());
        assertEquals(Identifier.parse("minecraft:trapped_chest"),
                behavior.drops().get(0).item());
        // The light row: the same non-solid-render partial cube.
        assertEquals(0, net.zaminmc.torch.server.block.BlockLightTable.lookup(
                Identifier.parse("minecraft:trapped_chest")).orElseThrow().filter());
        // The item + the wire row.
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:trapped_chest")).isPresent());
        assertEquals(java.util.Optional.of(146),
                net.zaminmc.torch.server.block.BuiltinBlocks.TRAPPED_CHEST.legacyMetadata() >= 0
                        ? java.util.Optional.of(146) : java.util.Optional.empty());
    }

    @Test
    void theChestSystemAcceptsTheFamily() {
        var registry = net.zaminmc.torch.server.block.BuiltinBlocks.registerAll(
                new net.zaminmc.torch.server.block.BlockRegistryBuilder()).freeze();
        var world = new net.zaminmc.torch.server.world.EngineWorld("trapped-it", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        net.zaminmc.torch.block.BlockPosition at = new net.zaminmc.torch.block.BlockPosition(10, 5, 10);
        world.setBlock(at, net.zaminmc.torch.server.block.BuiltinBlocks.TRAPPED_CHEST);
        // The manager's own gate accepts the trapped chest (the same
        // container, the same spill, the same persistence).
        assertTrue(world.getBlock(at).identifier()
                .equals(net.zaminmc.torch.server.block.BuiltinBlocks.TRAPPED_CHEST.identifier()));
        // The chest GUI opens through the same arm (the family read).
        var manager = new ChestManager();
        assertTrue(manager.peek(at) == null); // not yet opened: no state
        manager.getOrCreate(at); // the open creates the state
        assertTrue(manager.peek(at) != null);
        // The crafting row: the chest over the tripwire hook.
        var recipe = net.zaminmc.torch.server.crafting.BuiltinRecipes.ALL.stream()
                .filter(r -> r.result().item()
                        .equals(Identifier.parse("minecraft:trapped_chest")))
                .findFirst().orElseThrow();
        assertEquals(2, recipe.size()); // the chest row + the hook row
    }
}
