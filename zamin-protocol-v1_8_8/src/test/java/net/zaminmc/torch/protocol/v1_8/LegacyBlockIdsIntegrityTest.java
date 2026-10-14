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
}
