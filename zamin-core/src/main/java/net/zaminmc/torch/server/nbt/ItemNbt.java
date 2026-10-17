package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;

import java.util.Map;

/**
 * The ItemStack NBT rows (Slice 89 — the reference's
 * {@code ItemStack.writeNbt}/{@code fromNbt} over the engine's item
 * model): the id STRING (the engine's identifier form, bare or
 * namespaced), the Count byte, the Damage short.
 *
 * <p><b>The tag compound</b> (Slice 92 — the model's own arms, the
 * reference's shapes): {@code tag.display.Name} (the hover string) and
 * {@code tag.ench} (the {id, lvl} short pairs) round-trip through the
 * engine's ItemStack model. The model-less arms — RepairCost,
 * Unbreakable, CanDestroy/CanPlace, HideFlags, BlockEntityTag, the
 * attribute modifiers, Lore — parse into nothing and are never written
 * (the engine's standing envelope, ledgered).</p>
 */
public final class ItemNbt {

    private ItemNbt() {
    }

    /**
     * The reference's writeNbt: {@code {id:"minecraft:x", Count:Nb,
     * Damage:Ms}} — the id in the engine's identifier form, the count as a
     * byte, the damage as a short. An empty stack writes the air row.
     */
    public static void write(ItemStack stack, Map<String, Snbt.Tag> rows) {
        if (stack == null || stack.isEmpty()) {
            // The reference's empty arm: {id:"minecraft:air"} alone.
            NbtOps.putString(rows, "id", "minecraft:air");
            return;
        }
        ItemType type = stack.type();
        NbtOps.putString(rows, "id", type.identifier().toString());
        NbtOps.putByte(rows, "Count", (byte) stack.count());
        NbtOps.putShort(rows, "Damage", (short) stack.damage());
        // The tag compound (Slice 92 — writeNbt line 126-128: the whole
        // tag rides when the stack carries one): the engine's model arms
        // in the reference's own shapes — display.Name the hover string,
        // ench the {id, lvl} pairs (addEnchantment lines 590-603).
        boolean hasDisplay = stack.displayName() != null;
        boolean hasEnch = stack.enchantments() != null && !stack.enchantments().isEmpty();
        if (hasDisplay || hasEnch) {
            Map<String, Snbt.Tag> tag = new java.util.LinkedHashMap<>();
            if (hasDisplay) {
                Map<String, Snbt.Tag> display = new java.util.LinkedHashMap<>();
                NbtOps.putString(display, "Name", stack.displayName());
                NbtOps.putCompound(tag, "display", display);
            }
            if (hasEnch) {
                java.util.List<Snbt.Tag> ench = new java.util.ArrayList<>();
                for (Map.Entry<Integer, Integer> pair : stack.enchantments().entrySet()) {
                    Map<String, Snbt.Tag> row = new java.util.LinkedHashMap<>();
                    NbtOps.putShort(row, "id", (short) pair.getKey().intValue());
                    NbtOps.putShort(row, "lvl", (short) pair.getValue().intValue());
                    ench.add(Snbt.Tag.ofCompound(row));
                }
                NbtOps.putList(tag, "ench", ench);
            }
            NbtOps.putCompound(rows, "tag", tag);
        }
    }

    /**
     * The reference's fromNbt: the id STRING row (a known item — the bare
     * name gets the minecraft: namespace, the same /give surface), the
     * Count byte (default 1, clamped at the type's stack ceiling), the
     * Damage short (clamped at MAX_DAMAGE). An unknown or missing id, or
     * the air row, answers null (the reference's own null form).
     */
    public static ItemStack read(Map<String, Snbt.Tag> rows) {
        if (rows == null) {
            return null;
        }
        String rawId = NbtOps.getString(rows, "id");
        if (rawId == null || rawId.isEmpty() || rawId.equals("minecraft:air")) {
            return null;
        }
        String withNamespace = rawId.contains(":") ? rawId : "minecraft:" + rawId;
        ItemType type = BuiltinItems.lookup(Identifier.parse(withNamespace)).orElse(null);
        if (type == null) {
            return null;
        }
        int count = NbtOps.getByte(rows, "Count");
        if (count < 1) {
            count = 1;
        }
        count = Math.min(type.maxStackSize(), count);
        ItemStack stack = ItemStack.of(type, count);
        int damage = NbtOps.getShort(rows, "Damage");
        if (damage > 0) {
            stack = stack.withDamage(Math.min(ItemStack.MAX_DAMAGE, damage));
        }
        // The tag compound (Slice 92 — readNbt lines 150-155): the
        // display.Name row lands the hover string (getHoverName lines
        // 371-377), the ench list lands the pairs (getEnchantments line
        // 363); the model-less arms parse into nothing — the engine's
        // standing envelope.
        Map<String, Snbt.Tag> tag = NbtOps.getCompound(rows, "tag");
        if (!tag.isEmpty()) {
            Map<String, Snbt.Tag> display = NbtOps.getCompound(tag, "display");
            String name = NbtOps.getString(display, "Name");
            if (name != null && !name.isEmpty()) {
                stack = stack.withName(name);
            }
            java.util.List<Snbt.Tag> ench = NbtOps.getList(tag, "ench", Snbt.Tag.Type.COMPOUND);
            if (!ench.isEmpty()) {
                Map<Integer, Integer> pairs = new java.util.LinkedHashMap<>();
                for (Snbt.Tag row : ench) {
                    Map<String, Snbt.Tag> compound = row != null ? row.asCompound() : null;
                    if (compound == null) {
                        continue;
                    }
                    int id = NbtOps.getShort(compound, "id");
                    int lvl = NbtOps.getShort(compound, "lvl");
                    if (id >= 0 && lvl >= 1) {
                        pairs.put(id, lvl);
                    }
                }
                if (!pairs.isEmpty()) {
                    stack = stack.withEnchantments(pairs);
                }
            }
        }
        return stack;
    }

    /**
     * The Items-list row form (the reference's per-slot compound): the
     * stack's own rows plus the Slot byte —
     * {@code {Slot:0b, id:"minecraft:x", Count:Nb, Damage:Ms}}.
     */
    public static Map<String, Snbt.Tag> slotRows(int slot, ItemStack stack) {
        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        NbtOps.putByte(rows, "Slot", (byte) slot);
        write(stack, rows);
        return rows;
    }
}
