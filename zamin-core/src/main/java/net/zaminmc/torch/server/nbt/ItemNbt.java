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
 * engine's ItemStack model. <b>Slice 94's Wave G</b> lands the four
 * model-less arms: {@code tag.Unbreakable} (the byte over isDamageable
 * line 163), {@code tag.RepairCost} (the anvil's int, lines 634-643),
 * {@code tag.HideFlags} (the tooltip's mask, lines 442-443), and
 * {@code tag.display.Lore} (the tooltip's string list, lines 474-475).
 * <b>Wave H2</b> lands the raw compound — {@code tag.BlockEntityTag}
 * (BlockItem lines 76-97): carried VERBATIM on the model's arm in its
 * boxed form, never interpreted at the item layer — the placement
 * consult merges it into the freshly-placed tile's rows. The remaining
 * arms — CanDestroy/CanPlace, the attribute modifiers, display.color —
 * parse into nothing and are never written (the engine's standing
 * envelope, ledgered).</p>
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
        // display.Lore the tooltip lines (getTooltip lines 474-480), ench
        // the {id, lvl} pairs (addEnchantment lines 590-603), and Slice
        // 94's Wave G arms — Unbreakable the byte, RepairCost the anvil
        // int (setRepairCost line 638-643), HideFlags the tooltip mask
        // (lines 442-443). Each row rides only when the model carries
        // it (the reference's absent-means-default convention).
        boolean hasName = stack.displayName() != null;
        boolean hasLore = stack.lore() != null && !stack.lore().isEmpty();
        boolean hasEnch = stack.enchantments() != null && !stack.enchantments().isEmpty();
        boolean unbreakable = stack.unbreakable();
        int repairCost = stack.repairCost();
        int hideFlags = stack.hideFlags();
        boolean hasBlockEntityTag = stack.blockEntityTag() != null
                && !stack.blockEntityTag().isEmpty();
        if (hasName || hasLore || hasEnch || unbreakable || repairCost > 0 || hideFlags > 0
                || hasBlockEntityTag) {
            Map<String, Snbt.Tag> tag = new java.util.LinkedHashMap<>();
            if (hasName || hasLore) {
                Map<String, Snbt.Tag> display = new java.util.LinkedHashMap<>();
                if (hasName) {
                    NbtOps.putString(display, "Name", stack.displayName());
                }
                if (hasLore) {
                    java.util.List<Snbt.Tag> lines = new java.util.ArrayList<>();
                    for (String line : stack.lore()) {
                        lines.add(Snbt.Tag.ofString(line));
                    }
                    NbtOps.putList(display, "Lore", lines);
                }
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
            if (unbreakable) {
                NbtOps.putBoolean(tag, "Unbreakable", true);
            }
            if (repairCost > 0) {
                NbtOps.putInt(tag, "RepairCost", repairCost);
            }
            if (hideFlags > 0) {
                NbtOps.putInt(tag, "HideFlags", hideFlags);
            }
            // The raw compound (Wave H2 — BlockItem's setTileNbt reads the
            // row with contains("BlockEntityTag", 10): the whole subtree
            // written verbatim, every family of the tree's own.
            if (hasBlockEntityTag) {
                NbtOps.putCompound(tag, "BlockEntityTag",
                        NbtOps.fromBoxed(stack.blockEntityTag()));
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
        // The Damage row reads first (the reference's own row order) but
        // APPLIES after the tag arms — the Unbreakable exemption's own
        // order: a flagged stack's damage row may ride past the max, and
        // the model only accepts it once the flag has landed.
        int damage = NbtOps.getShort(rows, "Damage");
        // The tag compound (Slice 92 — readNbt lines 150-155): the
        // display.Name row lands the hover string (getHoverName lines
        // 371-377), the display.Lore list lands the tooltip lines
        // (lines 474-480 — the reference reads the raw strings, the
        // formatting prefix is the client's own render step), the ench
        // list lands the pairs (getEnchantments line 363), and Slice 94's
        // Wave G arms land their model rows — Unbreakable the byte
        // (getBoolean over the tag), RepairCost the int (contains(..., 3)
        // gate, line 634-635), HideFlags the mask (lines 442-443); the
        // still-model-less arms parse into nothing — the standing
        // envelope.
        Map<String, Snbt.Tag> tag = NbtOps.getCompound(rows, "tag");
        stack = readTagInto(stack, tag);
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

    /**
     * The tag compound's arm walk, shared by the row read and the /give
     * dataTag arm (Wave H2 — the reference's GiveCommand sets the WHOLE
     * tag compound: {@code stack.setNbt(SnbtParser.parse(s))}, the same
     * arms this walk reads). An empty or null compound returns the stack
     * unchanged.
     */
    public static ItemStack readTagInto(ItemStack stack, Map<String, Snbt.Tag> tag) {
        if (stack == null || stack.isEmpty() || tag == null || tag.isEmpty()) {
            return stack;
        }
        Map<String, Snbt.Tag> display = NbtOps.getCompound(tag, "display");
        String name = NbtOps.getString(display, "Name");
        if (name != null && !name.isEmpty()) {
            stack = stack.withName(name);
        }
        java.util.List<Snbt.Tag> loreRows = NbtOps.getList(display, "Lore", Snbt.Tag.Type.STRING);
        if (!loreRows.isEmpty()) {
            java.util.List<String> lines = new java.util.ArrayList<>(loreRows.size());
            for (Snbt.Tag row : loreRows) {
                String line = row != null ? row.asString() : null;
                if (line != null && !line.isEmpty()) {
                    lines.add(line);
                }
            }
            if (!lines.isEmpty()) {
                stack = stack.withLore(lines);
            }
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
        if (NbtOps.getBoolean(tag, "Unbreakable")) {
            stack = stack.withUnbreakable(true);
        }
        int repairCost = NbtOps.getInt(tag, "RepairCost");
        if (repairCost > 0) {
            stack = stack.withRepairCost(repairCost);
        }
        int hideFlags = NbtOps.getInt(tag, "HideFlags");
        if (hideFlags > 0) {
            stack = stack.withHideFlags(hideFlags);
        }
        Map<String, Snbt.Tag> blockEntityTag = NbtOps.getCompound(tag, "BlockEntityTag");
        if (!blockEntityTag.isEmpty()) {
            stack = stack.withBlockEntityTag(NbtOps.toBoxed(blockEntityTag));
        }
        return stack;
    }
}
