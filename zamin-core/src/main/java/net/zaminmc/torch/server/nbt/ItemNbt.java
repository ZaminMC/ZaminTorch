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
 * consult merges it into the freshly-placed tile's rows. <b>Wave I</b>
 * lands the adventure gates — CanDestroy/CanPlace. <b>Wave J</b> lands
 * the last arm — {@code tag.AttributeModifiers} (the reference's
 * getAttributeModifiers lines 646-670): a TAG_LIST of TAG_COMPOUND, each
 * element EntityAttributes.fromNbt's own shape, the zero-uuid rows
 * skipped on read, an explicitly-present-but-emptied list stripping the
 * item's default modifiers (the attribute-wipe — never the defaults'
 * fall-back).
 * <b>The book family (VIII12)</b> lands the written-book flow's own five
 * rows — {@code tag.pages} (a TAG_LIST of TAG_STRING, plain text on a
 * book &amp; quill, the JSON text forms on a signed book),
 * {@code tag.author} and {@code tag.title} (the STRING rows the MC|BSign
 * conversion writes, the author server-authoritative),
 * {@code tag.resolved} (the read gate's once-only byte) and
 * {@code tag.generation} (the copy counter — the reference's
 * CloneBookRecipe row, round-tripped verbatim).
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
        boolean hasCanDestroy = stack.canDestroy() != null && !stack.canDestroy().isEmpty();
        boolean hasCanPlaceOn = stack.canPlaceOn() != null && !stack.canPlaceOn().isEmpty();
        // The modifiers arm keeps its PRESENCE alone as the gate (Wave J):
        // an explicitly empty list is the tag's own form (the wipe) and
        // must ride the compound like any other row.
        boolean hasAttributeModifiers = stack.attributeModifiers() != null;
        boolean hasFireworks = stack.fireworks() != null;
        boolean hasBookPages = stack.bookPages() != null && !stack.bookPages().isEmpty();
        boolean hasBookAuthor = stack.bookAuthor() != null;
        boolean hasBookTitle = stack.bookTitle() != null;
        if (hasName || hasLore || hasEnch || unbreakable || repairCost > 0 || hideFlags > 0
                || hasBlockEntityTag || hasCanDestroy || hasCanPlaceOn
                || stack.displayColor() != null || hasAttributeModifiers
                || stack.skullOwner() != null || hasFireworks
                || hasBookPages || hasBookAuthor || hasBookTitle
                || stack.bookResolved() || stack.bookGeneration() != 0) {
            Map<String, Snbt.Tag> tag = new java.util.LinkedHashMap<>();
            if (hasName || hasLore || stack.displayColor() != null) {
                Map<String, Snbt.Tag> display = new java.util.LinkedHashMap<>();
                if (hasName) {
                    NbtOps.putString(display, "Name", stack.displayName());
                }
                if (stack.displayColor() != null) {
                    // display.color (the leather tint, a contains(...,3)
                    // INT row — ArmorItem.getColor's own gate).
                    NbtOps.putInt(display, "color", stack.displayColor());
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
            // The adventure gates' lists (Wave I — contains(...,9) +
            // getList(...,8): a TAG_LIST of TAG_STRING over the block
            // keys), written only when the model carries them.
            if (stack.canDestroy() != null && !stack.canDestroy().isEmpty()) {
                java.util.List<Snbt.Tag> keys = new java.util.ArrayList<>();
                for (String key : stack.canDestroy()) {
                    keys.add(Snbt.Tag.ofString(key));
                }
                NbtOps.putList(tag, "CanDestroy", keys);
            }
            if (stack.canPlaceOn() != null && !stack.canPlaceOn().isEmpty()) {
                java.util.List<Snbt.Tag> keys = new java.util.ArrayList<>();
                for (String key : stack.canPlaceOn()) {
                    keys.add(Snbt.Tag.ofString(key));
                }
                NbtOps.putList(tag, "CanPlaceOn", keys);
            }
            // The attribute modifiers (Wave J — the reference's
            // getAttributeModifiers read family): a TAG_LIST of
            // TAG_COMPOUND, written whenever the model carries the arm —
            // an explicitly empty list included (the wipe rides the
            // compound as the empty list).
            if (hasAttributeModifiers) {
                NbtOps.putList(tag, "AttributeModifiers",
                        modifierRows(stack.attributeModifiers()));
            }
            // The player head's SkullOwner (Wave K — the placement
            // consult's own row): the COMPOUND form (the reference never
            // writes the row; the engine round-trips what it parsed).
            if (stack.skullOwner() != null) {
                TileNbt.writeProfile(tag, "SkullOwner", stack.skullOwner());
            }
            // The Fireworks compound (the firework-rocket family —
            // FireworksEntity ctor line 39's own read family: the Flight
            // byte names the fuse band, the Explosions list rides the
            // synced slot-8 item to the client's own burst render):
            // carried VERBATIM in its boxed form, the same convention as
            // BlockEntityTag (the item layer never interprets it).
            if (hasFireworks) {
                NbtOps.putCompound(tag, "Fireworks",
                        NbtOps.fromBoxed(stack.fireworks()));
            }
            // The book family's rows (VIII12 — the reference's own shapes:
            // pages a TAG_LIST of TAG_STRING read back by getList("pages", 8),
            // author/title the STRING rows, resolved the BYTE marker,
            // generation the INT counter — each riding only when the model
            // carries it, the absent-means-default convention).
            if (hasBookPages) {
                java.util.List<Snbt.Tag> pageRows = new java.util.ArrayList<>(
                        stack.bookPages().size());
                for (String page : stack.bookPages()) {
                    pageRows.add(Snbt.Tag.ofString(page));
                }
                NbtOps.putList(tag, "pages", pageRows);
            }
            if (hasBookAuthor) {
                NbtOps.putString(tag, "author", stack.bookAuthor());
            }
            if (hasBookTitle) {
                NbtOps.putString(tag, "title", stack.bookTitle());
            }
            if (stack.bookResolved()) {
                NbtOps.putBoolean(tag, "resolved", true);
            }
            if (stack.bookGeneration() != 0) {
                NbtOps.putInt(tag, "generation", stack.bookGeneration());
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
        // display.color (the contains(...,3) INT gate — a non-int row
        // never lands; getInt answers 0 for a missing row, so the gate
        // rides containsNumeric).
        if (NbtOps.containsNumeric(display, "color")) {
            stack = stack.withDisplayColor(NbtOps.getInt(display, "color"));
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
        // The Fireworks compound (the firework-rocket family — the
        // reference's own read: getCompound("Fireworks") in the
        // FireworksEntity ctor): the whole subtree carried verbatim in
        // its boxed form (the Flight byte + the Explosions list); the
        // fuse consult reads the Flight row at the launch.
        Map<String, Snbt.Tag> fireworksRows = NbtOps.getCompound(tag, "Fireworks");
        if (!fireworksRows.isEmpty()) {
            stack = stack.withFireworks(NbtOps.toBoxed(fireworksRows));
        }
        // The book family's rows (VIII12 — getList("pages", 8)'s own gate:
        // an element-family mismatch answers empty, the reference's own
        // refusal; author/title the STRING rows, resolved the BYTE, the
        // generation the INT — each landing only when the row carries a
        // value the reference's reader would accept).
        java.util.List<Snbt.Tag> pageRows = NbtOps.getList(tag, "pages", Snbt.Tag.Type.STRING);
        if (!pageRows.isEmpty()) {
            java.util.List<String> pages = new java.util.ArrayList<>(pageRows.size());
            for (Snbt.Tag row : pageRows) {
                pages.add(row != null ? row.asString() : "");
            }
            stack = stack.withBookPages(pages);
        }
        String author = NbtOps.getString(tag, "author");
        if (author != null && !author.isEmpty()) {
            stack = stack.withBookAuthor(author);
        }
        String title = NbtOps.getString(tag, "title");
        if (title != null && !title.isEmpty()) {
            stack = stack.withBookTitle(title);
        }
        if (NbtOps.getBoolean(tag, "resolved")) {
            stack = stack.withBookResolved(true);
        }
        int generation = NbtOps.getInt(tag, "generation");
        if (generation > 0) {
            stack = stack.withBookGeneration(generation);
        }
        // The adventure gates' lists (Wave I — getList(...,8): an
        // element-family mismatch answers empty, the reference's own gate).
        java.util.List<Snbt.Tag> canDestroy = NbtOps.getList(tag, "CanDestroy", Snbt.Tag.Type.STRING);
        if (!canDestroy.isEmpty()) {
            java.util.List<String> keys = new java.util.ArrayList<>(canDestroy.size());
            for (Snbt.Tag key : canDestroy) {
                String entry = key != null ? key.asString() : null;
                if (entry != null && !entry.isEmpty()) {
                    keys.add(entry);
                }
            }
            if (!keys.isEmpty()) {
                stack = stack.withCanDestroy(keys);
            }
        }
        java.util.List<Snbt.Tag> canPlaceOn = NbtOps.getList(tag, "CanPlaceOn", Snbt.Tag.Type.STRING);
        if (!canPlaceOn.isEmpty()) {
            java.util.List<String> keys = new java.util.ArrayList<>(canPlaceOn.size());
            for (Snbt.Tag key : canPlaceOn) {
                String entry = key != null ? key.asString() : null;
                if (entry != null && !entry.isEmpty()) {
                    keys.add(entry);
                }
            }
            if (!keys.isEmpty()) {
                stack = stack.withCanPlaceOn(keys);
            }
        }
        // The attribute modifiers (Wave J — the reference's own gate:
        // contains("AttributeModifiers", 9) decides PRESENCE, the walk
        // then builds the list — possibly empty — and never falls back
        // to the item's defaults when the row exists: an all-skipped or
        // explicitly empty list is the attribute-wipe).
        if (NbtOps.contains(tag, "AttributeModifiers", Snbt.Tag.Type.LIST)) {
            stack = stack.withAttributeModifiers(
                    readAttributeModifiers(tag));
        }
        // The player head's SkullOwner row (Wave K — SkullItem.use lines
        // 70-77): the COMPOUND form (readProfile's shape) first, else the
        // STRING form (a non-empty name births a name-only profile — the
        // reference's own two-branch consult).
        Map<String, Snbt.Tag> skullOwnerRows = NbtOps.getCompound(tag, "SkullOwner");
        if (!skullOwnerRows.isEmpty()) {
            net.zaminmc.torch.item.SkullProfile owner = TileNbt.readProfile(skullOwnerRows);
            if (owner != null) {
                stack = stack.withSkullOwner(owner);
            }
        } else if (NbtOps.contains(tag, "SkullOwner", Snbt.Tag.Type.STRING)
                && !NbtOps.getString(tag, "SkullOwner").isEmpty()) {
            stack = stack.withSkullOwner(
                    net.zaminmc.torch.item.SkullProfile.ofName(
                            NbtOps.getString(tag, "SkullOwner")));
        }
        return stack;
    }

    /**
     * The AttributeModifiers list's element walk (Wave J — the reference's
     * getAttributeModifiers lines 646-670): each element parsed through
     * the fromNbt shape ({@link #parseModifier}), the invalid rows and
     * the zero-uuid rows skipped. The caller has already established the
     * row's PRESENCE — an emptied answer is the wipe, not an absence.
     */
    public static java.util.List<net.zaminmc.torch.item.AttributeModifier> readAttributeModifiers(
            Map<String, Snbt.Tag> tag) {
        java.util.List<net.zaminmc.torch.item.AttributeModifier> parsed = new java.util.ArrayList<>();
        for (Snbt.Tag row : NbtOps.getList(tag, "AttributeModifiers", Snbt.Tag.Type.COMPOUND)) {
            Map<String, Snbt.Tag> compound = row != null ? row.asCompound() : null;
            if (compound == null) {
                continue;
            }
            net.zaminmc.torch.item.AttributeModifier modifier = parseModifier(compound);
            if (modifier == null) {
                continue; // the reference's fromNbt catch: an invalid row skips
            }
            // The keep-gate (lines 655-659): BOTH uuid arms non-zero —
            // either arm's zero drops the row.
            if (modifier.uuid().getLeastSignificantBits() == 0L
                    || modifier.uuid().getMostSignificantBits() == 0L) {
                continue;
            }
            parsed.add(modifier);
        }
        return parsed;
    }

    /**
     * The element's own fromNbt (the reference's EntityAttributes.fromNbt
     * lines 95-106): the uuid from the two longs, the name/amount/operation
     * rows, an invalid shape answering null — the reference's catch into
     * a skipped element. The multimap's key (AttributeName) rides the
     * compound, read by the caller's walk.
     */
    public static net.zaminmc.torch.item.AttributeModifier parseModifier(
            Map<String, Snbt.Tag> compound) {
        String attributeName = NbtOps.getString(compound, "AttributeName");
        String name = NbtOps.getString(compound, "Name");
        double amount = NbtOps.getDouble(compound, "Amount");
        int operation = NbtOps.getInt(compound, "Operation");
        java.util.UUID uuid = new java.util.UUID(
                NbtOps.getLong(compound, "UUIDMost"),
                NbtOps.getLong(compound, "UUIDLeast"));
        try {
            return new net.zaminmc.torch.item.AttributeModifier(
                    attributeName, name, amount, operation, uuid);
        } catch (IllegalArgumentException invalid) {
            return null; // the reference's own catch-and-skip
        }
    }

    /**
     * The AttributeModifiers list's write form (Wave J): each element the
     * reference's toNbt shape plus the AttributeName key the item-tag
     * form carries — the whole list re-readable by the walk above.
     */
    private static java.util.List<Snbt.Tag> modifierRows(
            java.util.List<net.zaminmc.torch.item.AttributeModifier> modifiers) {
        java.util.List<Snbt.Tag> rows = new java.util.ArrayList<>(modifiers.size());
        for (net.zaminmc.torch.item.AttributeModifier modifier : modifiers) {
            Map<String, Snbt.Tag> element = new java.util.LinkedHashMap<>();
            NbtOps.putString(element, "AttributeName", modifier.attributeName());
            NbtOps.putString(element, "Name", modifier.name());
            NbtOps.putDouble(element, "Amount", modifier.amount());
            NbtOps.putInt(element, "Operation", modifier.operation());
            NbtOps.putLong(element, "UUIDMost", modifier.uuid().getMostSignificantBits());
            NbtOps.putLong(element, "UUIDLeast", modifier.uuid().getLeastSignificantBits());
            rows.add(Snbt.Tag.ofCompound(element));
        }
        return rows;
    }
}
