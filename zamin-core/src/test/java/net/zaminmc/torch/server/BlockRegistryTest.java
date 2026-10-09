package net.zaminmc.torch.server;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegistryTest {

    @Test
    void frozenRegistryResolvesBuiltins() {
        FrozenBlockRegistry registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        assertTrue(registry.lookup(Identifier.parse("minecraft:stone")).isPresent());
        assertTrue(registry.lookup(Identifier.parse("minecraft:air")).isPresent());
        assertEquals(87, registry.all().size()); // built-ins + ore ladder + craftable + smelting/gravity blocks + the fluid-contact pair + the biome flora + the farming stages + the sign facings + the door/ladder/fence states + planks/wool/fire + cane/cactus/lit furnace + the slab/stair collision states + the two flat rails
    }

    @Test
    void duplicateRegistrationIsRejected() {
        BlockRegistryBuilder builder = new BlockRegistryBuilder();
        builder.register(BuiltinBlocks.STONE);
        assertThrows(IllegalStateException.class, () -> builder.register(BuiltinBlocks.STONE));
    }

    @Test
    void registrationIsClosedAfterFreeze() {
        BlockRegistryBuilder builder = new BlockRegistryBuilder();
        builder.register(BuiltinBlocks.STONE);
        FrozenBlockRegistry registry = builder.freeze();
        assertThrows(IllegalStateException.class, () -> builder.register(BuiltinBlocks.DIRT));
        // The frozen view still works and never mutates.
        assertEquals(1, registry.all().size());
    }
}
