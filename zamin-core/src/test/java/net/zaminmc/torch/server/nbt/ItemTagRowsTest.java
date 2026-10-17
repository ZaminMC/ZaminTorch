package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item-tag compound's model arms (Slice 92 — the C3 envelope's third
 * landing): {@code tag.display.Name} (the hover string) and
 * {@code tag.ench} (the {id, lvl} short pairs — the reference's
 * addEnchantment shape) round-trip through the engine's ItemStack model;
 * Slice 94's Wave G lands the four model-less arms (Unbreakable,
 * RepairCost, HideFlags, display.Lore) onto the same model — the
 * still-model-less arms (CanDestroy/CanPlace, BlockEntityTag, the
 * attribute modifiers) parse into nothing and are never written.
 */
class ItemTagRowsTest {

    @Test
    void theNamedEnchantedSwordRoundTripsItsTag() {
        // The reference's shapes: display.Name the hover string, ench the
        // {id, lvl} pairs (writeNbt lines 121-129 + addEnchantment
        // 590-603).
        Map<Integer, Integer> ench = new LinkedHashMap<>();
        ench.put(16, 3); // sharpness
        ench.put(34, 1); // fire aspect
        ItemStack saved = ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                .withName("§6Emberfang")
                .withEnchantments(ench);

        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        ItemNbt.write(saved, rows);
        assertEquals("minecraft:diamond_sword", NbtOps.getString(rows, "id"));

        Map<String, Snbt.Tag> tag = NbtOps.getCompound(rows, "tag");
        assertFalse(tag.isEmpty(), "the tag compound rides the stack");
        Map<String, Snbt.Tag> display = NbtOps.getCompound(tag, "display");
        assertEquals("§6Emberfang", NbtOps.getString(display, "Name"));
        List<Snbt.Tag> enchRows = NbtOps.getList(tag, "ench", Snbt.Tag.Type.COMPOUND);
        assertEquals(2, enchRows.size());
        Map<String, Snbt.Tag> first = enchRows.get(0).asCompound();
        assertEquals(16, NbtOps.getShort(first, "id"));
        assertEquals(3, NbtOps.getShort(first, "lvl"));

        ItemStack loaded = ItemNbt.read(rows);
        assertNotNull(loaded);
        assertEquals("§6Emberfang", loaded.displayName());
        assertEquals(2, loaded.enchantments().size());
        assertEquals(3, loaded.enchantmentLevel(16));
        assertEquals(1, loaded.enchantmentLevel(34));
    }

    @Test
    void thePlainStackWritesNoTag() {
        // A bare stack carries no tag compound (writeNbt's null arm).
        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        ItemNbt.write(ItemStack.of(BuiltinItems.STONE, 3), rows);
        assertTrue(NbtOps.getCompound(rows, "tag").isEmpty());
    }

    @Test
    void theTagReadLandsTheModelArms() {
        // The read (readNbt lines 150-155): display.Name + the ench list
        // land on the model; the Wave G arms (Slice 94) land too —
        // Unbreakable over getBoolean, RepairCost over its int row.
        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        NbtOps.putString(rows, "id", "minecraft:iron_pickaxe");
        NbtOps.putByte(rows, "Count", (byte) 1);
        NbtOps.putShort(rows, "Damage", (short) 12);

        Map<String, Snbt.Tag> tag = new java.util.LinkedHashMap<>();
        Map<String, Snbt.Tag> display = new java.util.LinkedHashMap<>();
        NbtOps.putString(display, "Name", "Digger");
        NbtOps.putCompound(tag, "display", display);
        List<Snbt.Tag> ench = List.of(
                Snbt.Tag.ofCompound(tagRow(32, 4)), // efficiency
                Snbt.Tag.ofCompound(tagRow(70, 1))); // unbreaking
        NbtOps.putList(tag, "ench", ench);
        NbtOps.putBoolean(tag, "Unbreakable", true);
        NbtOps.putInt(tag, "RepairCost", 3);
        NbtOps.putCompound(rows, "tag", tag);

        ItemStack loaded = ItemNbt.read(rows);
        assertNotNull(loaded);
        assertEquals("Digger", loaded.displayName());
        assertEquals(4, loaded.enchantmentLevel(32));
        assertEquals(1, loaded.enchantmentLevel(70));
        assertEquals(12, loaded.damage());
        assertTrue(loaded.unbreakable(), "the Unbreakable row lands on the model");
        assertEquals(3, loaded.repairCost(), "the RepairCost row lands on the model");
        assertEquals(0, loaded.hideFlags(), "an absent HideFlags keeps the default");
        assertNull(loaded.lore(), "an absent Lore keeps the default");
    }

    @Test
    void theWaveGArmsRoundTrip() {
        // Slice 94's Wave G: the four arms ride the tag compound in the
        // reference's own shapes — the Unbreakable byte (isDamageable line
        // 163), the RepairCost int (lines 634-643), the HideFlags mask
        // (lines 442-443), the display.Lore string list (lines 474-480).
        ItemStack saved = ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                .withUnbreakable(true)
                .withRepairCost(7)
                .withHideFlags(6)
                .withLore(List.of("Forged in the deep", "Never dulls"));

        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        ItemNbt.write(saved, rows);
        Map<String, Snbt.Tag> tag = NbtOps.getCompound(rows, "tag");
        assertFalse(tag.isEmpty());
        assertTrue(NbtOps.getBoolean(tag, "Unbreakable"));
        assertEquals(7, NbtOps.getInt(tag, "RepairCost"));
        assertEquals(6, NbtOps.getInt(tag, "HideFlags"));
        Map<String, Snbt.Tag> display = NbtOps.getCompound(tag, "display");
        List<Snbt.Tag> lore = NbtOps.getList(display, "Lore", Snbt.Tag.Type.STRING);
        assertEquals(2, lore.size());
        assertEquals("Forged in the deep", lore.get(0).asString());
        assertEquals("Never dulls", lore.get(1).asString());

        ItemStack loaded = ItemNbt.read(rows);
        assertTrue(loaded.unbreakable());
        assertEquals(7, loaded.repairCost());
        assertEquals(6, loaded.hideFlags());
        assertEquals(List.of("Forged in the deep", "Never dulls"), loaded.lore());
        assertEquals(saved, loaded, "the whole stack equals itself through the rows");
    }

    @Test
    void theAbsentWaveGRowsKeepTheDefaultsAndAreNeverWritten() {
        // The absent-means-default convention: a stack carrying none of
        // the Wave G arms writes none of their rows (the plain Slice 92
        // shape) and loads with the model's defaults.
        Map<Integer, Integer> ench = new LinkedHashMap<>();
        ench.put(16, 2);
        ItemStack saved = ItemStack.of(BuiltinItems.IRON_SWORD, 1)
                .withName("Plain Steel")
                .withEnchantments(ench);
        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        ItemNbt.write(saved, rows);
        Map<String, Snbt.Tag> tag = NbtOps.getCompound(rows, "tag");
        assertFalse(NbtOps.contains(tag, "Unbreakable", Snbt.Tag.Type.BYTE));
        assertFalse(NbtOps.containsNumeric(tag, "RepairCost"));
        assertFalse(NbtOps.containsNumeric(tag, "HideFlags"));
        assertTrue(NbtOps.getList(NbtOps.getCompound(tag, "display"),
                "Lore", Snbt.Tag.Type.STRING).isEmpty());

        ItemStack loaded = ItemNbt.read(rows);
        assertFalse(loaded.unbreakable());
        assertEquals(0, loaded.repairCost());
        assertEquals(0, loaded.hideFlags());
        assertNull(loaded.lore());
    }

    @Test
    void theFlaggedStackCarriesItsDamageRowPastTheMax() {
        // The constructor's Unbreakable exemption (readNbt lines 143-145:
        // only the negative clamps — a flagged stack's Damage row rides
        // past the durability, never wears, never counts as damaged).
        ItemStack flagged = ItemStack.of(BuiltinItems.WOODEN_PICKAXE)
                .withUnbreakable(true)
                .withDamage(500); // the pickaxe's own max is 59
        assertEquals(500, flagged.damage());

        // The same row on an unflagged stack stays the engine's own bound.
        assertThrows(IllegalArgumentException.class,
                () -> ItemStack.of(BuiltinItems.WOODEN_PICKAXE).withDamage(500));

        // The rows keep the exemption through a save/load: the flagged
        // sword's out-of-band damage row round-trips.
        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        ItemNbt.write(flagged, rows);
        ItemStack loaded = ItemNbt.read(rows);
        assertEquals(500, loaded.damage());
        assertTrue(loaded.unbreakable());
    }

    @Test
    void theZeroLevelPairIsDropped() {
        // The sanitizer's own gate: a lvl-0 pair never lands (the model's
        // levels are >= 1).
        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        NbtOps.putString(rows, "id", "minecraft:bow");
        NbtOps.putByte(rows, "Count", (byte) 1);
        Map<String, Snbt.Tag> tag = new java.util.LinkedHashMap<>();
        NbtOps.putList(tag, "ench", List.of(
                Snbt.Tag.ofCompound(tagRow(48, 0)), // power 0 — dropped
                Snbt.Tag.ofCompound(tagRow(50, 2)))); // flame 2
        NbtOps.putCompound(rows, "tag", tag);

        ItemStack loaded = ItemNbt.read(rows);
        assertEquals(0, loaded.enchantmentLevel(48));
        assertEquals(2, loaded.enchantmentLevel(50));
    }

    @Test
    void theSlotRowCarriesTheTag() {
        // The per-slot form (the container rows): the tag rides the slot
        // compound like any other row.
        Map<Integer, Integer> ench = new LinkedHashMap<>();
        ench.put(19, 2); // protection
        ItemStack saved = ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1)
                .withEnchantments(ench);
        Map<String, Snbt.Tag> slotRows = ItemNbt.slotRows(3, saved);
        assertEquals(3, NbtOps.getByte(slotRows, "Slot"));
        Map<String, Snbt.Tag> tag = NbtOps.getCompound(slotRows, "tag");
        assertEquals(1, NbtOps.getList(tag, "ench", Snbt.Tag.Type.COMPOUND).size());
    }

    private static Map<String, Snbt.Tag> tagRow(int id, int lvl) {
        Map<String, Snbt.Tag> row = new java.util.LinkedHashMap<>();
        NbtOps.putShort(row, "id", (short) id);
        NbtOps.putShort(row, "lvl", (short) lvl);
        return row;
    }

    @Test
    void theDisplayColorRoundTripsTheLeatherTint() {
        // The leather tint (ArmorItem.getColor's own row): display.color
        // an INT inside the display compound, the contains(...,3) gate on
        // read (a non-int row never lands), the wire + the tails riding.
        var leather = BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:leather_helmet")).orElseThrow();
        ItemStack dyed = ItemStack.of(leather, 1).withDisplayColor(0x4C7F99);
        Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
        ItemNbt.write(dyed, rows);
        // The row lands inside the display compound.
        Map<String, Snbt.Tag> tag = NbtOps.getCompound(rows, "tag");
        Map<String, Snbt.Tag> display = NbtOps.getCompound(tag, "display");
        assertTrue(NbtOps.containsNumeric(display, "color"));
        assertEquals(0x4C7F99, NbtOps.getInt(display, "color"));
        // The read lands it back; a non-int color row never lands.
        ItemStack read = ItemNbt.read(rows);
        assertEquals(Integer.valueOf(0x4C7F99), read.displayColor());
        NbtOps.putString(display, "color", "not an int");
        read = ItemNbt.read(rows);
        assertNull(read.displayColor(), "a string-typed color row never lands");
        // A stack without the color writes no row and reads none.
        Map<String, Snbt.Tag> bare = new LinkedHashMap<>();
        ItemNbt.write(ItemStack.of(leather, 1), bare);
        assertFalse(NbtOps.containsNumeric(
                NbtOps.getCompound(NbtOps.getCompound(bare, "tag"), "display"), "color"));
    }
    @Test
    void theSkullOwnerRidesTheTagInBothForms() {
        // Wave K — the player head's placement consult row: the STRING
        // form (a name-only profile) and the COMPOUND form (the full
        // profile) both land; the engine writes the compound form back.
        var head = BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:skull")).orElseThrow();
        java.util.UUID notchId = java.util.UUID.fromString(
                "b50ad385-829d-4e11-a963-e6321040d9e1");
        // The string form parses to the name-only profile.
        Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
        NbtOps.putString(rows, "id", "minecraft:skull");
        Map<String, Snbt.Tag> tag = new LinkedHashMap<>();
        NbtOps.putString(tag, "SkullOwner", "Notch");
        NbtOps.putCompound(rows, "tag", tag);
        ItemStack named = ItemNbt.read(rows);
        assertNotNull(named.skullOwner());
        assertEquals("Notch", named.skullOwner().name());
        assertNull(named.skullOwner().id());
        // The compound form parses the whole profile.
        Map<String, Snbt.Tag> full = new LinkedHashMap<>();
        NbtOps.putString(full, "id", "minecraft:skull");
        Map<String, Snbt.Tag> fullTag = new LinkedHashMap<>();
        Map<String, Snbt.Tag> owner = new LinkedHashMap<>();
        NbtOps.putString(owner, "Name", "Notch");
        NbtOps.putString(owner, "Id", notchId.toString());
        Map<String, Snbt.Tag> properties = new LinkedHashMap<>();
        NbtOps.putList(properties, "textures", List.of(
                Snbt.Tag.ofCompound(new java.util.LinkedHashMap<>(
                        Map.of("Value", Snbt.Tag.ofString("skin=="))))));
        NbtOps.putCompound(owner, "Properties", properties);
        NbtOps.putCompound(fullTag, "SkullOwner", owner);
        NbtOps.putCompound(full, "tag", fullTag);
        ItemStack profiled = ItemNbt.read(full);
        assertNotNull(profiled.skullOwner());
        assertEquals(notchId, profiled.skullOwner().id());
        assertEquals(1, profiled.skullOwner().properties().get("textures").size());
        // The write round-trips the compound form (both forms write the
        // same compound — the ledgered merge-identity corner).
        Map<String, Snbt.Tag> out = new LinkedHashMap<>();
        ItemNbt.write(profiled, out);
        Map<String, Snbt.Tag> outTag = NbtOps.getCompound(out, "tag");
        assertEquals("Notch", NbtOps.getString(
                NbtOps.getCompound(outTag, "SkullOwner"), "Name"));
        ItemStack reloaded = ItemNbt.read(out);
        assertEquals(profiled.skullOwner(), reloaded.skullOwner());
        // The two forms of the same head never merge with a bare stack.
        assertFalse(ItemStack.mergeable(named, ItemStack.of(head, 1)));
    }
}
