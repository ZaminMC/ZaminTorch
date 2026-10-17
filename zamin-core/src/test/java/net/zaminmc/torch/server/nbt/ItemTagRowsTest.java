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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item-tag compound's model arms (Slice 92 — the C3 envelope's third
 * landing): {@code tag.display.Name} (the hover string) and
 * {@code tag.ench} (the {id, lvl} short pairs — the reference's
 * addEnchantment shape) round-trip through the engine's ItemStack model;
 * the model-less arms (RepairCost, Unbreakable, HideFlags, ...) parse
 * into nothing and are never written.
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
        // land on the model; the model-less arms (Unbreakable,
        // RepairCost, HideFlags) parse into nothing.
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
        NbtOps.putBoolean(tag, "Unbreakable", true); // the model-less arm
        NbtOps.putInt(tag, "RepairCost", 3);
        NbtOps.putCompound(rows, "tag", tag);

        ItemStack loaded = ItemNbt.read(rows);
        assertNotNull(loaded);
        assertEquals("Digger", loaded.displayName());
        assertEquals(4, loaded.enchantmentLevel(32));
        assertEquals(1, loaded.enchantmentLevel(70));
        assertEquals(12, loaded.damage());
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
}
