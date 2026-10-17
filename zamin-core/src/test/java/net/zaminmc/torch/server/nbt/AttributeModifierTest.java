package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.item.AttributeModifier;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.item.Tools;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave J — the attribute modifiers (the reference's getAttributeModifiers
 * lines 646-670 over EntityAttributes.fromNbt 95-106): the item-tag row's
 * element shape, the BOTH-uuid-arms-non-zero keep-gate, the fromNbt
 * catch-and-skip of invalid rows, the PRESENCE branch (an explicitly
 * present-but-emptied list never falls back to the item's defaults — the
 * attribute-wipe), and the melee fold itself
 * ({@code Tools.heldDamageOf} — ModifiableEntityAttributeInstance
 * .computeValue's own order over the player's registered base 1.0).
 */
class AttributeModifierTest {

    private static final UUID GOOD = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

    /** One element's SNBT compound, the reference's own row shape. */
    private static Map<String, Snbt.Tag> element(String attributeName, String name,
                                                 double amount, int operation,
                                                 long most, long least) {
        Map<String, Snbt.Tag> row = new LinkedHashMap<>();
        NbtOps.putString(row, "AttributeName", attributeName);
        NbtOps.putString(row, "Name", name);
        NbtOps.putDouble(row, "Amount", amount);
        NbtOps.putInt(row, "Operation", operation);
        NbtOps.putLong(row, "UUIDMost", most);
        NbtOps.putLong(row, "UUIDLeast", least);
        return row;
    }

    /** A stack's tag compound carrying the given modifier elements. */
    private static Map<String, Snbt.Tag> tagWith(Map<String, Snbt.Tag>... elements) {
        Map<String, Snbt.Tag> tag = new LinkedHashMap<>();
        NbtOps.putList(tag, "AttributeModifiers", List.of(
                java.util.Arrays.stream(elements)
                        .map(Snbt.Tag::ofCompound)
                        .toArray(Snbt.Tag[]::new)));
        return tag;
    }

    @Test
    void theModifierListRoundTripsTheTagRows() {
        // The reference's element shape — AttributeName (the multimap
        // key), Name, Amount (double), Operation (int), the two uuid
        // longs — written and read back through the model.
        ItemStack saved = ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                .withAttributeModifiers(List.of(
                        new AttributeModifier("generic.attackDamage",
                                "Weapon modifier", 7.0, 0, GOOD),
                        new AttributeModifier("generic.movementSpeed",
                                "Speedy", 0.1, 2,
                                UUID.fromString("FA233E1C-4180-4865-B59B-A0BCAB99C05F"))));
        Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
        ItemNbt.write(saved, rows);
        Map<String, Snbt.Tag> tag = NbtOps.getCompound(rows, "tag");
        List<Snbt.Tag> list = NbtOps.getList(tag, "AttributeModifiers", Snbt.Tag.Type.COMPOUND);
        assertEquals(2, list.size());
        Map<String, Snbt.Tag> first = list.get(0).asCompound();
        assertEquals("generic.attackDamage", NbtOps.getString(first, "AttributeName"));
        assertEquals("Weapon modifier", NbtOps.getString(first, "Name"));
        assertEquals(7.0, NbtOps.getDouble(first, "Amount"), 0.0);
        assertEquals(0, NbtOps.getInt(first, "Operation"));
        assertEquals(GOOD.getMostSignificantBits(), NbtOps.getLong(first, "UUIDMost"));
        assertEquals(GOOD.getLeastSignificantBits(), NbtOps.getLong(first, "UUIDLeast"));

        ItemStack loaded = ItemNbt.read(rows);
        assertNotNull(loaded.attributeModifiers());
        assertEquals(2, loaded.attributeModifiers().size());
        assertEquals(saved.attributeModifiers(), loaded.attributeModifiers());
        // mergeable over the whole row (a different list never merges).
        ItemStack other = ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                .withAttributeModifiers(List.of(
                        new AttributeModifier("generic.attackDamage",
                                "Weapon modifier", 8.0, 0, GOOD)));
        assertFalse(ItemStack.mergeable(saved, other), "a different amount never merges");
        assertTrue(ItemStack.mergeable(saved, loaded), "the round trip keeps identity");
        assertTrue(ItemStack.mergeable(
                        ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1),
                        ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)),
                "the bare stacks still merge");
    }

    @Test
    void theZeroUuidRowsAreSkippedOnRead() {
        // The keep-gate (lines 655-659): BOTH uuid arms non-zero — either
        // arm's zero drops the row (the plan's "LSB-zero skip" is really
        // the reference's both-arms conjunction).
        Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
        NbtOps.putString(rows, "id", "minecraft:diamond_sword");
        NbtOps.putCompound(rows, "tag", tagWith(
                element("generic.attackDamage", "Zero least", 5.0, 0, 1L, 0L),
                element("generic.attackDamage", "Zero most", 6.0, 0, 0L, 2L),
                element("generic.attackDamage", "Kept", 7.0, 0, 1L, 2L)));
        ItemStack loaded = ItemNbt.read(rows);
        assertEquals(1, loaded.attributeModifiers().size(),
                "only the both-arms-non-zero row survived");
        assertEquals("Kept", loaded.attributeModifiers().get(0).name());
    }

    @Test
    void theInvalidRowsAreSkippedOnRead() {
        // EntityAttributes.fromNbt's catch: an empty Name (Validate
        // .notEmpty) or an operation outside 0..2 (Validate
        // .inclusiveBetween) answers null — the element skips.
        Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
        NbtOps.putString(rows, "id", "minecraft:diamond_sword");
        NbtOps.putCompound(rows, "tag", tagWith(
                element("generic.attackDamage", "", 5.0, 0, 1L, 2L),
                element("generic.attackDamage", "Op 3", 6.0, 3, 1L, 2L),
                element("generic.attackDamage", "Op -1", 6.0, -1, 1L, 2L),
                element("generic.attackDamage", "Kept", 7.0, 2, 1L, 2L)));
        ItemStack loaded = ItemNbt.read(rows);
        assertEquals(1, loaded.attributeModifiers().size());
        assertEquals("Kept", loaded.attributeModifiers().get(0).name());
        // The api record validates the same pair (the reference's own
        // constructor exceptions — a record that exists is a valid row).
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeModifier("generic.attackDamage", "", 1.0, 0, GOOD));
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeModifier("generic.attackDamage", "x", 1.0, 3, GOOD));
    }

    @Test
    void theFoldRunsComputeValuesOwnOrder() {
        // ModifiableEntityAttributeInstance.computeValue (lines 137-155):
        // d0 = base + op-0 adds; d1 = d0 (the snapshot); op-1 adds
        // amount * d0 (the POST-op-0 base — the reference's subtle
        // order); op-2 multiplies 1 + amount; the 0..2048 clamp closes.
        List<AttributeModifier> mixed = List.of(
                new AttributeModifier("generic.attackDamage", "add 3", 3.0, 0, GOOD),
                new AttributeModifier("generic.attackDamage", "add 4", 4.0, 0,
                        UUID.fromString("00000000-0000-0001-0001-000000000001")),
                new AttributeModifier("generic.attackDamage", "scale half", 0.5, 1,
                        UUID.fromString("00000000-0000-0002-0002-000000000002")),
                new AttributeModifier("generic.attackDamage", "double", 1.0, 2,
                        UUID.fromString("00000000-0000-0003-0003-000000000003")));
        // base 1 + 3 + 4 = 8; op-1: 8 + 8*0.5 = 12; op-2: 12 * 2 = 24.
        ItemStack stack = ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                .withAttributeModifiers(mixed);
        assertEquals(24.0f, Tools.heldDamageOf(stack), 0.0001f,
                "computeValue's own order over the player's base 1.0");
        // A non-attackDamage key rides unconsulted (the movement-speed
        // row above never joins the damage fold).
        List<AttributeModifier> wrongKey = List.of(
                new AttributeModifier("generic.movementSpeed", "fast", 9.0, 0, GOOD));
        ItemStack speedBoots = ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                .withAttributeModifiers(wrongKey);
        assertEquals(1.0f, Tools.heldDamageOf(speedBoots), 0.0001f,
                "a wrong-key list folds the bare base");
        // The clamp: a huge op-2 stack pins at the attribute's own max.
        List<AttributeModifier> huge = List.of(
                new AttributeModifier("generic.attackDamage", "huge", 10000.0, 2, GOOD));
        assertEquals(2048.0f, Tools.heldDamageOf(
                ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1).withAttributeModifiers(huge)),
                0.0001f, "the RangedEntityAttribute's own 2048 ceiling");
        // A negative fold pins at the floor 0.
        List<AttributeModifier> negative = List.of(
                new AttributeModifier("generic.attackDamage", "drain", -50.0, 0, GOOD));
        assertEquals(0.0f, Tools.heldDamageOf(
                ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1).withAttributeModifiers(negative)),
                0.0001f, "the attribute's own 0 floor");
    }

    @Test
    void theTaglessStackFallsToTheTypeTable() {
        // The plan's fall-back: no list on the tag — the item's own
        // defaults answer (the engine's ledgered type table).
        assertEquals(Tools.attackDamageOf(BuiltinItems.DIAMOND_SWORD),
                Tools.heldDamageOf(ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)),
                0.0001f);
        assertNull(ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1).attributeModifiers());
        // The empty hand folds the bare base (the registered 1.0).
        assertEquals(1.0f, Tools.heldDamageOf(ItemStack.EMPTY), 0.0001f);
        assertEquals(1.0f, Tools.heldDamageOf(null), 0.0001f);
    }

    @Test
    void thePresentEmptyListIsTheAttributeWipe() {
        // The reference's own branch: the tag CARRIES the row — the
        // multimap builds empty and the defaults never answer. An
        // all-skipped list (the zero-uuid elements) reads as
        // present-but-empty; an explicit withAttributeModifiers(List.of())
        // is the same state at the api.
        Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
        NbtOps.putString(rows, "id", "minecraft:diamond_sword");
        NbtOps.putCompound(rows, "tag", tagWith(
                element("generic.attackDamage", "Zeroed", 99.0, 0, 0L, 0L)));
        ItemStack wiped = ItemNbt.read(rows);
        assertNotNull(wiped.attributeModifiers(), "the row's PRESENCE survives the skip walk");
        assertTrue(wiped.attributeModifiers().isEmpty(), "the zero-uuid row dropped");
        assertEquals(1.0f, Tools.heldDamageOf(wiped), 0.0001f,
                "the wipe folds the bare fist — never the sword's defaults");
        // The api's own empty-list form is the same state.
        ItemStack explicit = ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                .withAttributeModifiers(List.of());
        assertNotNull(explicit.attributeModifiers());
        assertTrue(explicit.attributeModifiers().isEmpty());
        assertEquals(1.0f, Tools.heldDamageOf(explicit), 0.0001f);
        // The wipe writes back as a present row (the empty list rides).
        Map<String, Snbt.Tag> out = new LinkedHashMap<>();
        ItemNbt.write(explicit, out);
        Map<String, Snbt.Tag> tag = NbtOps.getCompound(out, "tag");
        assertTrue(NbtOps.contains(tag, "AttributeModifiers", Snbt.Tag.Type.LIST),
                "the present-but-empty list still writes the row");
        // And the two presence states never merge (different tags).
        assertFalse(ItemStack.mergeable(explicit,
                ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)));
    }

    @Test
    void theGiveDataTagFeedsTheModifiersThroughTheReadWalk() {
        // The /give dataTag arm rides readTagInto (the same walk): the
        // parsed compound's row lands the model arm — the flagship's
        // model half, the live melee is the protocol suite's own row.
        Map<String, Snbt.Tag> parsed = Snbt.parseCompound(
                "{AttributeModifiers:[{AttributeName:\"generic.attackDamage\","
                        + "Name:\"Weapon modifier\",Amount:10.0d,Operation:0,"
                        + "UUIDMost:1L,UUIDLeast:2L}]}");
        ItemStack granted = ItemNbt.readTagInto(
                ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1), parsed);
        assertNotNull(granted.attributeModifiers());
        assertEquals(1, granted.attributeModifiers().size());
        assertEquals(11.0f, Tools.heldDamageOf(granted), 0.0001f,
                "the fold over the granted stack (1.0 base + 10.0 op-0)");
        // The element-family mismatch never lands (getList(...,10)'s own
        // gate): a string list reads as the wipe only when the row is a
        // LIST — the reference's contains(...,9) decides presence.
        Map<String, Snbt.Tag> stringList = Snbt.parseCompound(
                "{AttributeModifiers:[\"generic.attackDamage\"]}");
        ItemStack mismatch = ItemNbt.readTagInto(
                ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1), stringList);
        assertNotNull(mismatch.attributeModifiers(),
                "a present LIST row of the wrong element family is still the wipe");
        assertTrue(mismatch.attributeModifiers().isEmpty());
    }

    @Test
    void theModifierRecordCarriesTheWholeRowIdentity() {
        // The tag-compare identity (areItemStacksEqual's whole-tag walk):
        // two rows differing ONLY in name or uuid are different tags —
        // the reference's uuid-only equals belongs to the live attribute
        // instance set, never the stored row.
        AttributeModifier one = new AttributeModifier(
                "generic.attackDamage", "Weapon modifier", 7.0, 0, GOOD);
        AttributeModifier renamed = new AttributeModifier(
                "generic.attackDamage", "Other modifier", 7.0, 0, GOOD);
        assertFalse(one.equals(renamed), "the whole row is the identity");
        assertThrows(NullPointerException.class,
                () -> new AttributeModifier("generic.attackDamage", "x", 1.0, 0, null));
        assertThrows(NullPointerException.class,
                () -> new AttributeModifier(null, "x", 1.0, 0, GOOD));
        // The melee consult's own key.
        assertTrue(one.isAttackDamage());
        assertFalse(new AttributeModifier("generic.maxHealth", "hp", 1.0, 0, GOOD)
                .isAttackDamage());
        // The list copy is immutable (List.copyOf's own contract).
        List<AttributeModifier> input = new ArrayList<>();
        input.add(one);
        ItemStack stack = ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                .withAttributeModifiers(input);
        assertThrows(UnsupportedOperationException.class,
                () -> stack.attributeModifiers().add(one));
        // A null list clears to absent (the item's own defaults again).
        ItemStack cleared = stack.withAttributeModifiers(null);
        assertNull(cleared.attributeModifiers());
    }
}
