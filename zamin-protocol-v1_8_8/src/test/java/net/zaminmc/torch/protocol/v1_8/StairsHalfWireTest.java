package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The stairs' HALF bit on the wire (Slice L1): every family's
 * upside-down states resolve over the family's own legacy id with the
 * reference's nibble — {@code (HALF == TOP ? 4 : 0) | (5 -
 * FACING.getId())} (StairsBlock.getMetadataFromState lines 511-521) —
 * and the packed state id the chunk serializer and the block-change
 * writer share carries the same fold.
 */
class StairsHalfWireTest {

    @Test
    void theTopStatesCarryTheHalfBitNibble() {
        // The hand-declared families: id and nibble.
        assertRow("minecraft:oak_stairs_east_top", 53, 4);
        assertRow("minecraft:oak_stairs_west_top", 53, 5);
        assertRow("minecraft:oak_stairs_south_top", 53, 6);
        assertRow("minecraft:oak_stairs_north_top", 53, 7);
        assertRow("minecraft:cobblestone_stairs_north_top", 67, 7);
        assertRow("minecraft:sandstone_stairs_south_top", 128, 6);
        assertRow("minecraft:nether_brick_stairs_west_top", 114, 5);
        assertRow("minecraft:red_sandstone_stairs_east_top", 180, 4);
        assertRow("minecraft:red_sandstone_stairs_north_top", 180, 7);
        // The eight loop families.
        assertRow("minecraft:spruce_stairs_south_top", 134, 6);
        assertRow("minecraft:birch_stairs_east_top", 135, 4);
        assertRow("minecraft:jungle_stairs_north_top", 136, 7);
        assertRow("minecraft:acacia_stairs_west_top", 163, 5);
        assertRow("minecraft:dark_oak_stairs_east_top", 164, 4);
        assertRow("minecraft:brick_stairs_south_top", 108, 6);
        assertRow("minecraft:stone_brick_stairs_north_top", 109, 7);
        assertRow("minecraft:quartz_stairs_west_top", 156, 5);
    }

    /**
     * THE AUDIT'S CATCH: the shipped village slice (Slice 21's sandstone
     * stairs) never registered the four facing nibble rows — every
     * sandstone stair carried metadata 0 (the east form) on the wire, so
     * a real client rendered all four facings as the east-ascending form.
     * The band lands with the HALF bit's rows.
     */
    @Test
    void theSandstoneFacingsCarryTheirOwnNibbles() {
        assertRow("minecraft:sandstone_stairs_east", 128, 0);
        assertRow("minecraft:sandstone_stairs_west", 128, 1);
        assertRow("minecraft:sandstone_stairs_south", 128, 2);
        assertRow("minecraft:sandstone_stairs_north", 128, 3);
    }

    private void assertRow(String id, int legacy, int nibble) {
        Identifier identifier = Identifier.parse(id);
        assertEquals(legacy, LegacyBlockIds.legacyId(identifier).orElseThrow(),
                id + " legacy id");
        assertEquals(nibble, LegacyBlockIds.metadataOf(identifier), id + " metadata nibble");
        // The packed state id (the chunk and block-change writer's form):
        // (legacy << 4) | nibble.
        assertEquals((legacy << 4) | nibble,
                ChunkSerializer18.packedStateId(
                        new net.zaminmc.torch.server.block.EngineBlockType(identifier, "Test",
                                nibble)),
                id + " packed state id");
    }
}
