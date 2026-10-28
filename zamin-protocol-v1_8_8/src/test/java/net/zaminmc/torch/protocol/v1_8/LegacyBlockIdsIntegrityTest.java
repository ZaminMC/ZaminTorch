package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

/**
 * The legacy id table's static-init integrity (the Slice 16c and 18d
 * lessons): a doubled Map.entry inside {@code Map.ofEntries} throws
 * IllegalArgumentException at the table's class initialization — a failure
 * that only surfaces when a join flow first serializes a chunk, killing
 * every integration test twenty seconds in. This test forces the static
 * init directly, so a duplicate row fails here in milliseconds with the
 * offending key in the message instead of deep inside a Netty callback.
 */
class LegacyBlockIdsIntegrityTest {

    @Test
    void theLegacyTablesInitializeWithoutADuplicateRow() {
        // Touching any member forces <clinit>; the duplicate key (if any)
        // names itself in the IllegalArgumentException.
        assertDoesNotThrow(() -> {
            for (Field field : LegacyBlockIds.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    field.get(null); // the read forces the initializer
                }
            }
            for (Method method : LegacyBlockIds.class.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    method.invoke(null);
                }
            }
        }, "a doubled row or broken initializer must fail HERE, not in a join flow");
        // The table must answer at least the foundational members — the
        // map is live, not an empty fallback.
        assertTrue(LegacyBlockIds.legacyId(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:stone"))
                .isPresent(), "the stone row answers");
    }

    /**
     * The dropper's wire repair (Slice 32): the reference registers the
     * dropper as legacy block 158 (Block.java line 1340) and its BlockItem
     * shares that id (Item.register(block) reads Block.getId). The original
     * Slice 11a table carried 125 — the double wooden slab's id — so a real
     * 1.8.8 client rendered slabs where a dropper stood, in chunks and
     * block-change packets alike. The whole family (the six facings and
     * their triggered pairs) must carry 158.
     */
    @Test
    void theDropperFamilyCarriesLegacy158NotTheWoodenSlabId() {
        for (String identifier : new String[]{
                "minecraft:dropper", "minecraft:dropper_down", "minecraft:dropper_up",
                "minecraft:dropper_north", "minecraft:dropper_south", "minecraft:dropper_west",
                "minecraft:dropper_east", "minecraft:dropper_east_triggered",
                "minecraft:dropper_down_triggered"}) {
            assertEquals(Optional.of(158), LegacyBlockIds.legacyId(
                            net.zaminmc.torch.util.Identifier.parse(identifier)),
                    identifier + " must ride legacy 158");
        }
        // The dispenser keeps its own id (Block.java line 1041) — the repair
        // must not have swept the family's other member.
        assertEquals(Optional.of(23), LegacyBlockIds.legacyId(
                net.zaminmc.torch.util.Identifier.parse("minecraft:dispenser")));
        assertEquals(Optional.of(23), LegacyBlockIds.legacyId(
                net.zaminmc.torch.util.Identifier.parse("minecraft:dispenser_east_triggered")));
    }

    /**
     * The nether bricks' wire repair (Slice 32): the reference registers the
     * fortress block as legacy 112 (Block.java's register(112, "nether_brick")
     * — the extraction's own row). The original Slice 12d table carried 40 —
     * the red mushroom's id — so real clients rendered mushrooms for fortress
     * walls. The item form shares 112 (Item.java's register(Blocks.NETHER_BRICKS)
     * reads Block.getId), while the nether brick MATERIAL item (the smelted
     * brick, Items.NETHERBRICK) is 405.
     */
    @Test
    void theNetherBricksBlockCarriesLegacy112NotTheMushroomId() {
        assertEquals(Optional.of(112), LegacyBlockIds.legacyId(
                net.zaminmc.torch.util.Identifier.parse("minecraft:nether_bricks")),
                "the fortress block is legacy 112");
        assertEquals(Optional.of(405), LegacyBlockIds.legacyId(
                net.zaminmc.torch.util.Identifier.parse("minecraft:nether_brick")),
                "the material item is legacy 405");
        // The mineral vault family's wire rows (Slice 32): every id is the
        // reference's own (blocks 21/129/22/152/173/155/45/82/170/165/121/
        // 174, the materials 337/336).
        for (String[] row : new String[][]{
                {"minecraft:lapis_ore", "21"}, {"minecraft:emerald_ore", "129"},
                {"minecraft:lapis_block", "22"}, {"minecraft:redstone_block", "152"},
                {"minecraft:coal_block", "173"}, {"minecraft:quartz_block", "155"},
                {"minecraft:brick_block", "45"}, {"minecraft:clay", "82"},
                {"minecraft:hay_block", "170"}, {"minecraft:slime_block", "165"},
                {"minecraft:end_stone", "121"}, {"minecraft:packed_ice", "174"},
                {"minecraft:clay_ball", "337"}, {"minecraft:brick", "336"}}) {
            assertEquals(Optional.of(Integer.parseInt(row[1])), LegacyBlockIds.legacyId(
                            net.zaminmc.torch.util.Identifier.parse(row[0])),
                    row[0] + " must ride legacy " + row[1]);
        }
    }

    /**
     * The complete door families' wire rows (Slice 34 — DoorBlock.java's
     * own registrations): the six wood doors 64/193/194/195/196/197 and the
     * iron door 71, every state of every family, with the reference's exact
     * metadata nibbles — the lower's facing band | open bit 4, the upper's
     * half 8 | hinge 1 | powered 2 with the engine's open flag 4 (a bit the
     * 1.8.8 client ignores on upper halves). The oak's manual-open upper is
     * corrected from the historical 9 (a right-hinge claim) to 12.
     */
    @Test
    void theDoorFamiliesCarryTheReferenceIdsAndMetadataNibbles() {
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "iron"};
        int[] ids = {64, 193, 194, 195, 196, 197, 71};
        String[][] lowerSuffixes = {
                {""}, {"_north"}, {"_east"}, {"_south"},
                {"_open"}, {"_open_north"}, {"_open_east"}, {"_open_south"}};
        int[] lowerMetas = {0, 1, 2, 3, 4, 5, 6, 7};
        String[][] upperSuffixes = {
                {"_upper", "_upper"}, {"_upper_right", "_upper_right"},
                {"_upper_powered", "_upper_powered"},
                {"_upper_right_powered", "_upper_right_powered"},
                {"_upper_open", "_upper_open"},
                {"_upper_right_open", "_upper_right_open"},
                {"_upper_powered_open", "_upper_powered_open"},
                {"_upper_right_powered_open", "_upper_right_powered_open"}};
        int[] upperMetas = {8, 9, 10, 11, 12, 13, 14, 15};
        for (int w = 0; w < woods.length; w++) {
            String wood = woods[w];
            boolean iron = wood.equals("iron");
            for (int s = 0; s < lowerSuffixes.length; s++) {
                String suffix = lowerSuffixes[s][0];
                if (iron && (suffix.isEmpty() || suffix.equals("_open"))) {
                    suffix += "_west"; // the iron door's shipped meta-0 name
                }
                String identifier = "minecraft:" + wood + "_door" + suffix;
                assertEquals(Optional.of(ids[w]), LegacyBlockIds.legacyId(
                                net.zaminmc.torch.util.Identifier.parse(identifier)),
                        identifier + " must ride legacy " + ids[w]);
                assertEquals(lowerMetas[s], LegacyBlockIds.metadataOf(
                                net.zaminmc.torch.util.Identifier.parse(identifier)),
                        identifier + " must carry metadata " + lowerMetas[s]);
            }
            for (int u = 0; u < upperSuffixes.length; u++) {
                String identifier = "minecraft:" + wood + "_door" + upperSuffixes[u][0];
                assertEquals(Optional.of(ids[w]), LegacyBlockIds.legacyId(
                                net.zaminmc.torch.util.Identifier.parse(identifier)),
                        identifier + " must ride legacy " + ids[w]);
                assertEquals(upperMetas[u], LegacyBlockIds.metadataOf(
                                net.zaminmc.torch.util.Identifier.parse(identifier)),
                        identifier + " must carry metadata " + upperMetas[u]);
            }
        }
    }

    /**
     * The fence-gate families' wire rows (Slice 37 — FenceGateBlock.java's
     * own registrations): the oak gate 107 and the five 1.7 woods' gates
     * (spruce 183, birch 184, jungle 185, dark_oak 186, acacia 187), every
     * state of every family with the reference's exact metadata nibble
     * (getMetadataFromState lines 136-153: the horizontal facing S=0 W=1
     * N=2 E=3 | open 4 | powered 8 — the bare meta-0 form keeps the plain
     * identifier the family's item shares).
     */
    @Test
    void theFenceGateFamiliesCarryTheReferenceIdsAndMetadataNibbles() {
        String[] woods = {"oak", "spruce", "birch", "jungle", "dark_oak", "acacia"};
        int[] ids = {107, 183, 184, 185, 186, 187};
        String[] facings = {"", "_west", "_north", "_east"}; // south is the bare facing
        for (int w = 0; w < woods.length; w++) {
            String wood = woods[w];
            for (int facing = 0; facing < 4; facing++) {
                for (int open = 0; open <= 1; open++) {
                    for (int powered = 0; powered <= 1; powered++) {
                        int meta = facing | (open == 1 ? 4 : 0) | (powered == 1 ? 8 : 0);
                        StringBuilder name = new StringBuilder("minecraft:")
                                .append(wood).append("_fence_gate");
                        if (powered == 1) {
                            name.append("_powered");
                        }
                        if (open == 1) {
                            name.append("_open");
                        }
                        name.append(facings[facing]);
                        String identifier = name.toString();
                        assertEquals(Optional.of(ids[w]), LegacyBlockIds.legacyId(
                                        net.zaminmc.torch.util.Identifier.parse(identifier)),
                                identifier + " must ride legacy " + ids[w]);
                        assertEquals(meta, LegacyBlockIds.metadataOf(
                                        net.zaminmc.torch.util.Identifier.parse(identifier)),
                                identifier + " must carry metadata " + meta);
                    }
                }
            }
        }
    }

    /**
     * The red sandstone family (Slice 52 — 1.8's own four, the reference's
     * Block.java rows 1379-1390): the whole family must ride its own wire
     * ids with the reference's exact metadata nibbles — the red sand on 12
     * variant 1 (SandBlock.Variant.RED_SAND), the TYPE band on 179, the
     * stairs' Bukkit facing band on 180, the double 181 and the slab pair
     * 182 with the HALF bit 8. The family items share the block ids
     * (Item.java's register(Blocks.X) reads Block.getId).
     */
    @Test
    void theRedSandstoneFamilyCarriesItsOwnWireRows() {
        assertEquals(Optional.of(12), LegacyBlockIds.legacyId(
                net.zaminmc.torch.util.Identifier.parse("minecraft:red_sand")));
        assertEquals(Optional.of(12), LegacyBlockIds.legacyId(
                net.zaminmc.torch.util.Identifier.parse("minecraft:sand")));
        assertEquals(Optional.of(1), Optional.of(
                LegacyBlockIds.metadataOf(net.zaminmc.torch.util.Identifier.parse("minecraft:red_sand"))));
        for (String[] row : new String[][] {
                {"minecraft:red_sandstone", "179", "0"},
                {"minecraft:chiseled_red_sandstone", "179", "1"},
                {"minecraft:smooth_red_sandstone", "179", "2"},
                {"minecraft:red_sandstone_stairs", "180", "0"},
                {"minecraft:red_sandstone_stairs_west", "180", "1"},
                {"minecraft:red_sandstone_stairs_south", "180", "2"},
                {"minecraft:red_sandstone_stairs_north", "180", "3"},
                {"minecraft:red_sandstone_stairs_east", "180", "0"},
                {"minecraft:double_stone_slab2", "181", "0"},
                {"minecraft:stone_slab2", "182", "0"},
                {"minecraft:stone_slab2_top", "182", "8"}}) {
            assertEquals(Optional.of(Integer.parseInt(row[1])), LegacyBlockIds.legacyId(
                            net.zaminmc.torch.util.Identifier.parse(row[0])),
                    row[0] + " must ride legacy " + row[1]);
            assertEquals(Integer.parseInt(row[2]), LegacyBlockIds.metadataOf(
                            net.zaminmc.torch.util.Identifier.parse(row[0])),
                    row[0] + " must carry metadata " + row[2]);
        }
    }

    /**
     * The pumpkin pair's rotated states (the metadata slice — PumpkinBlock
     * lines 138-148): the nibble is the HORIZONTAL id (Direction.java
     * lines 14-17's own idHorizontal assignments — 0 south, 1 west, 2
     * north, 3 east), a DIFFERENT domain than the chest family's
     * Direction-id band 2-5. The bare forms carry 0 (the south states,
     * the dye family's white-wool precedent — every pumpkin in an
     * existing world stays valid).
     */
    @Test
    void thePumpkinFamilyCarriesTheHorizontalIdBand() {
        for (String[] row : new String[][] {
                {"minecraft:pumpkin", "86", "0"},
                {"minecraft:pumpkin_west", "86", "1"},
                {"minecraft:pumpkin_north", "86", "2"},
                {"minecraft:pumpkin_east", "86", "3"},
                {"minecraft:lit_pumpkin", "91", "0"},
                {"minecraft:lit_pumpkin_west", "91", "1"},
                {"minecraft:lit_pumpkin_north", "91", "2"},
                {"minecraft:lit_pumpkin_east", "91", "3"}}) {
            assertEquals(Optional.of(Integer.parseInt(row[1])), LegacyBlockIds.legacyId(
                            net.zaminmc.torch.util.Identifier.parse(row[0])),
                    row[0] + " must ride legacy " + row[1]);
            assertEquals(Integer.parseInt(row[2]), LegacyBlockIds.metadataOf(
                            net.zaminmc.torch.util.Identifier.parse(row[0])),
                    row[0] + " must carry the horizontal id " + row[2]);
        }
    }
}
