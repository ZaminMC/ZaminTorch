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
        assertEquals(707, registry.all().size()); // Slice 48 (707 = 693 + the flower-pot family's fourteen states) // Slice 47 (693 = 691 + the wall family's two VARIANT states) // Slice 46 (691 = 684 + the cake family's seven BITES states) // Slice 45 (684 = 672 + the double-plant family's twelve states) // Slice 44 (672 = 664 + the flower family's eight variants; the stale 443 from Slice 36 repaired with it — Slices 37-43 had not re-pinned it: the fence gates, the rail trio, the trapdoor pair, the mushroom pair, the melon stems, the cocoa) // Slice 36 (443) + the building shapes (Slice 36: 8 stair families x 4 facings + 5 fences + 5 wooden-slab pairs) // Slice 34's doors (375) + the wood-variant foundation (Slice 35: the five new woods' planks/log/leaves + all six saplings) // built-ins + ore ladder + craftable + smelting/gravity blocks + the fluid-contact pair + the biome flora + the farming stages + the sign facings + the wall signs + the door/ladder/fence states + planks/wool/fire + cane/cactus/lit furnace + the slab/stair collision states + the two flat rails + the bookshelf + the enchanting table + the two nether-portal axis planes + the nether material set (netherrack, soul sand, glowstone, quartz ore, the mushrooms) + the TNT block + the cobweb + the village set (the panes/bars, the double stone slab, the smooth sandstone, the sandstone stairs, the carrots/potatoes stages) + the stronghold set (the stone-brick trio + the infested egg + the brick slab + the spawner + the iron door's five states + the end portal frame's eight) + the fortress set (the nether bricks, the fence, the stairs' four, the wart's four) + the monument set (the prismarine trio, the sea lantern, the sponge pair, the gold block) + the temple set (the chiseled sandstone, the clay pair, the mossy cobblestone, the vine, the chiseled stone bricks) + the golem set (the snow pair, the iron block, the pumpkin pair) + the ice (Slice 21c) + the note block (Slice 27a) + the jukebox pair (Slice 27b: the plain block and its HAS_RECORD variant) + the beacon pyramid's diamond/emerald blocks (Slice 29b) + the dye-coloring world families (Slice 31: the fifteen non-white wool members, the stained glass sixteen, the stained pane sixteen, the carpet sixteen, the stained clay's fourteen new members + hardened clay) + the mineral vault (Slice 32: the lapis/emerald ores, the lapis/redstone/coal/quartz/brick storage blocks, the clay, the hay bale, the slime pad, the end stone, the packed ice) + the complete door families (Slice 34: the five new woods' sixteen states each, the oak's hinge/powered upper grid, the iron door's open band and upper grid)
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
