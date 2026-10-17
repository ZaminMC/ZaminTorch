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
 * <p><b>The engine's no-item-tag envelope</b> (the long-ledgered row): the
 * reference's {@code tag} compound (enchantments, display names, ...) has
 * no landing in the engine's item model — the row parses on read and is
 * simply never written. Ledgered.</p>
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
