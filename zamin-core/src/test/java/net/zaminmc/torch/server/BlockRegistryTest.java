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
        assertEquals(275, registry.all().size()); // built-ins + ore ladder + craftable + smelting/gravity blocks + the fluid-contact pair + the biome flora + the farming stages + the sign facings + the wall signs + the door/ladder/fence states + planks/wool/fire + cane/cactus/lit furnace + the slab/stair collision states + the two flat rails + the bookshelf + the enchanting table + the two nether-portal axis planes + the nether material set (netherrack, soul sand, glowstone, quartz ore, the mushrooms) + the TNT block + the cobweb + the village set (the panes/bars, the double stone slab, the smooth sandstone, the sandstone stairs, the carrots/potatoes stages) + the stronghold set (the stone-brick trio + the infested egg + the brick slab + the spawner + the iron door's five states + the end portal frame's eight) + the fortress set (the nether bricks, the fence, the stairs' four, the wart's four) + the monument set (the prismarine trio, the sea lantern, the sponge pair, the gold block) + the temple set (the chiseled sandstone, the clay pair, the mossy cobblestone, the vine, the chiseled stone bricks) + the golem set (the snow pair, the iron block, the pumpkin pair) + the ice (Slice 21c) + the note block (Slice 27a) + the jukebox pair (Slice 27b: the plain block and its HAS_RECORD variant) + the beacon pyramid's diamond/emerald blocks (Slice 29b) + the dye-coloring world families (Slice 31: the fifteen non-white wool members, the stained glass sixteen, the stained pane sixteen, the carpet sixteen, the stained clay's fourteen new members + hardened clay) + the mineral vault (Slice 32: the lapis/emerald ores, the lapis/redstone/coal/quartz/brick storage blocks, the clay, the hay bale, the slime pad, the end stone, the packed ice)
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
