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
}
