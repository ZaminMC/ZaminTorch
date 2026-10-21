package net.zaminmc.torch.item;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A concrete, possibly-empty quantity of one {@link ItemType} (§426).
 *
 * <p>An item stack never carries slot semantics: it does not know where it is
 * stored. Inventories, item entities and containers hold stacks; the stacks
 * themselves stay pure values.</p>
 *
 * <p>Empty state is canonical (§427): exactly one representation
 * ({@link #EMPTY}) exists; every subsystem must treat emptiness through
 * {@link #isEmpty()}, never by inventing null conventions.</p>
 *
 * <p>The {@code damage} value is the historical 1.8 stack field (vanilla NBT
 * names it {@code Damage}) and is dual-role, exactly like the wire that carries
 * it as one i16 per slot and per item entity: on durability-bound items it is
 * accumulated wear (a worn pickaxe and a fresh one are different stacks), on
 * everything else it is the item's variant metadata (charcoal is coal with
 * damage 1, red sand is sand with damage 1 — the client resolves the variant
 * name itself from the same field). A stack of coal and a stack of charcoal
 * are therefore different stacks and never merge, matching the historical
 * {@code ItemStack.areItemStacksEqual} rules.</p>
 *
 * <p>The optional {@code displayName} is a slice of historical item NBT the
 * engine models (vanilla {@code tag.display.Name}, the anvil rename): it rides
 * the slot encoding's NBT compound on the wire, survives every container, and
 * participates in identity — a named sword and an unnamed sword never merge
 * ({@link #mergeable}), exactly like the historical NBT-aware comparison.</p>
 *
 * <p>The optional {@code enchantments} is the next slice of that NBT (vanilla
 * {@code tag.ench}, the {@code id}/{@code lvl} short-pair list): it rides the
 * same wire compound, participates in identity the same way, and is the
 * storage the enchantment math ({@code server/enchantment}) reads and the
 * enchanting table writes. Null means unenchanted; the map is defensively
 * immutable and keeps insertion order (the historical tooltip order).</p>
 *
 * @param type        the item identity; {@code null} only for the canonical empty stack
 * @param count       units held; {@code 0} only for the canonical empty stack
 * @param damage      durability wear (durable items) or variant metadata (all other
 *                    items); always {@code 0} for empty stacks. Non-negative i16
 *                    range, further bounded by durability where one exists
 * @param displayName the optional custom name (null = none); never blank, never
 *                    longer than {@link #MAX_NAME_LENGTH}, control characters stripped
 * @param enchantments the optional enchantment map (null = none): legacy 1.8
 *                    enchantment id to level, both positive shorts, insertion order kept
 * @param unbreakable the tag's Unbreakable byte (Slice 93/G1 — isDamageable's own
 *                    gate, ItemStack.java line 163): a true flag holds the durability
 *                    at whatever it carries
 * @param repairCost  the tag's RepairCost int (the anvil's own row; 0 = absent)
 * @param hideFlags   the tag's HideFlags int (the tooltip's own mask; 0 = absent)
 * @param lore        the tag's display.Lore string list (the tooltip lines;
 *                    null = absent, immutable, every line non-blank)
 * @param blockEntityTag the tag's BlockEntityTag compound (Wave H2 — BlockItem's
 *                    setTileNbt row, carried VERBATIM): the raw NBT subtree in
 *                    its boxed form — one {@code Map<String, Object>} whose values
 *                    are {@code Byte}/{@code Short}/{@code Integer}/{@code Long}/
 *                    {@code Float}/{@code Double}/{@code String}/{@code int[]}/
 *                    {@code List<Object>} (homogeneous, ordered) / {@code Map<String, Object>}
 *                    (nested, insertion-ordered). Null = absent. Deeply immutable;
 *                    the reference's NbtCompound.equals semantics (unordered keys,
 *                    ordered lists) are exactly {@code Map}/{@code List} equals over
 *                    the boxed tree — the merge identity never needs a custom walk
 * @param canDestroy  the tag's CanDestroy string list (Wave I — the adventure
 *                    mine gate, a TAG_LIST of TAG_STRING over the block keys):
 *                    null = absent, immutable, every entry a normalized block
 *                    identifier (bare names gain the minecraft: prefix)
 * @param canPlaceOn  the tag's CanPlaceOn string list (the adventure place
 *                    gate, the same shape); null = absent, immutable
 * @param displayColor the tag's display.color int (the leather tint, a
 *                    contains(...,3) INT row); null = absent (the reference's
 *                    getColor default 10511680 is the render model's own —
 *                    the engine stores the row alone)
 * @param attributeModifiers the tag's AttributeModifiers list (Wave J — the
 *                    reference's getAttributeModifiers lines 646-670, a
 *                    TAG_LIST of TAG_COMPOUND): null = absent (the item's
 *                    own defaults answer), a NON-NULL list — even an empty
 *                    one — is the tag's own form (an empty list strips the
 *                    item's default modifiers, the attribute-wipe: the
 *                    reference builds the empty multimap whenever the tag
 *                    carries the row, never falling back to the defaults).
 *                    Immutable; every element is a valid row (the read walk
 *                    skips invalid elements and zero-uuid rows before the
 *                    model ever sees them)
 * @param skullOwner the tag's SkullOwner row (Wave K — SkullItem.use lines
 *                    70-77, the player-head placement consult): null =
 *                    absent (a damage-3 stack places the plain Steve head),
 *                    else the parsed profile — the STRING form
 *                    (a name-only profile) and the COMPOUND form
 *                    (readProfile's shape) both parse here; the engine
 *                    writes the compound form back (the reference never
 *                    writes the row at all — it only reads)
 * @param fireworks the tag's Fireworks compound (the firework-rocket
 *                    family — FireworksItem/FireworksEntity's own row:
 *                    {@code {Flight:b, Explosions:[...]}}): null = absent
 *                    (the fuse roll's plain i=1 band — the reference's
 *                    ctor line 39 {@code i += nbtcompound1.getByte(
 *                    "Flight")} reads 0 for a missing row, so the default
 *                    fuse is 10*i + rand(6) + rand(7) with i=1), else the
 *                    whole boxed subtree carried verbatim (the Explosions
 *                    list rides the synced slot-8 item to the client —
 *                    the burst's own render reads it) and the Flight byte
 *                    names the fuse band (each unit +10 ticks). Deeply
 *                    immutable, the same boxed-tree convention as
 *                    blockEntityTag
 * @param bookPages the tag's pages list (the book family — VIII12,
 *                    BookAndQuillItem.isValid's own row: a TAG_LIST of
 *                    TAG_STRING, plain text on a book &amp; quill, the
 *                    JSON text forms on a signed book): null = absent,
 *                    immutable, every page carried VERBATIM (the
 *                    per-page length is the channel gate's own law, the
 *                    count at most {@link #MAX_BOOK_PAGES} — the
 *                    defensive bound over a foreign payload, the
 *                    reference's model caps nothing)
 * @param bookAuthor the tag's author row (WrittenBookItem.isValid's STRING
 *                    row — the signing player's own name, authoritative
 *                    from the server): null = absent
 * @param bookTitle the tag's title row (the 32-character cap row of the
 *                    same isValid law — the client's trimmed title): null =
 *                    absent, carried verbatim
 * @param bookResolved the tag's resolved byte (WrittenBookItem.
 *                    resolvePages' own marker — the read gate's once-only
 *                    walk): false = absent
 * @param bookGeneration the tag's generation int (CloneBookRecipe's copy
 *                    counter, 0 = the reference's absent-row default
 *                    "Original"); carried verbatim for the round trip
 */
public record ItemStack(ItemType type, int count, int damage, String displayName,
                        Map<Integer, Integer> enchantments,
                        boolean unbreakable, int repairCost, int hideFlags,
                        List<String> lore,
                        Map<String, Object> blockEntityTag,
                        List<String> canDestroy, List<String> canPlaceOn,
                        Integer displayColor,
                        List<AttributeModifier> attributeModifiers,
                        SkullProfile skullOwner,
                        Map<String, Object> fireworks,
                        List<String> bookPages, String bookAuthor, String bookTitle,
                        boolean bookResolved, int bookGeneration) {

    /** The wire's i16 damage/metadata ceiling (the historical field width). */
    public static final int MAX_DAMAGE = 0x7FFF;

    /**
     * The custom-name ceiling. Generous beyond the historical anvil field's 35
     * characters (the semantic rename entry is a command, not a GUI text box),
     * but bounded so the slot NBT payload stays small.
     */
    public static final int MAX_NAME_LENGTH = 64;

    /** The single canonical empty stack (§427). */
    public static final ItemStack EMPTY = new ItemStack(null, 0);

    /** The wire's short-bound level/id ceiling (the historical NBT width). */
    public static final int MAX_ENCHANTMENT_LEVEL = 0x7FFF;

    /** The lore list's line ceiling (the tooltip's own practical bound). */
    public static final int MAX_LORE_LINES = 16;

    /** The book family's per-page cap (BookAndQuillItem.isValid line 38). */
    public static final int MAX_BOOK_PAGE_CHARS = 32767;

    /**
     * The book family's page-count ceiling (the defensive bound — the
     * reference's model caps nothing; the vanilla client writes at most 50,
     * the bound holds a modded payload honest without rejecting the stack).
     */
    public static final int MAX_BOOK_PAGES = 100;

    public ItemStack {
        if (type == null) {
            if (count != 0 || damage != 0 || displayName != null || enchantments != null
                    || unbreakable || repairCost != 0 || hideFlags != 0 || lore != null
                    || blockEntityTag != null || canDestroy != null || canPlaceOn != null
                    || displayColor != null || attributeModifiers != null
                    || skullOwner != null || fireworks != null
                    || bookPages != null || bookAuthor != null || bookTitle != null
                    || bookResolved || bookGeneration != 0) {
                throw new IllegalArgumentException("A stack without a type must be empty (count "
                        + count + ", damage " + damage + ")");
            }
        } else {
            if (count < 1) {
                throw new IllegalArgumentException("Stack count must be positive: " + count);
            }
            if (count > type.maxStackSize()) {
                throw new IllegalArgumentException("Stack count " + count
                        + " exceeds max stack size " + type.maxStackSize()
                        + " of " + type.identifier());
            }
            if (damage < 0 || damage > MAX_DAMAGE) {
                throw new IllegalArgumentException("Stack damage/metadata out of i16 range: " + damage);
            }
            int maxDurability = type.maxDurability();
            // The Unbreakable exemption (Slice 94/G1 — the reference's
            // readNbt lines 143-145: only the negative clamps; a flagged
            // stack carries its damage row freely past the max, never
            // wears, never counts as damaged — isDamaged line 171's own
            // isDamageable consult).
            if (maxDurability > 0 && damage > maxDurability && !unbreakable) {
                throw new IllegalArgumentException("Stack damage " + damage
                        + " exceeds max durability " + maxDurability + " of " + type.identifier());
            }
            displayName = sanitizeName(displayName);
            enchantments = sanitizeEnchantments(enchantments);
            if (repairCost < 0) {
                repairCost = 0;
            }
            if (hideFlags < 0) {
                hideFlags = 0;
            }
            lore = sanitizeLore(lore);
            blockEntityTag = sanitizeBlockEntityTag(blockEntityTag);
            canDestroy = sanitizeBlockKeyList(canDestroy);
            canPlaceOn = sanitizeBlockKeyList(canPlaceOn);
            displayColor = displayColor == null ? null : displayColor;
            attributeModifiers = sanitizeAttributeModifiers(attributeModifiers);
            fireworks = sanitizeFireworks(fireworks);
            bookPages = sanitizeBookPages(bookPages);
            bookAuthor = sanitizeBookText(bookAuthor, "bookAuthor");
            bookTitle = sanitizeBookText(bookTitle, "bookTitle");
            if (bookGeneration < 0) {
                bookGeneration = 0;
            }
        }
    }

    /** The pre-G1 five-arg shape (the compatibility constructor over the new arms' defaults). */
    public ItemStack(ItemType type, int count, int damage, String displayName,
                     Map<Integer, Integer> enchantments) {
        this(type, count, damage, displayName, enchantments, false, 0, 0, null, null);
    }

    /** The pre-H2 nine-arg shape (the compatibility constructor over the new arm's default). */
    public ItemStack(ItemType type, int count, int damage, String displayName,
                     Map<Integer, Integer> enchantments, boolean unbreakable,
                     int repairCost, int hideFlags, List<String> lore) {
        this(type, count, damage, displayName, enchantments, unbreakable,
                repairCost, hideFlags, lore, null);
    }

    /** The pre-I1 ten-arg shape (the compatibility constructor over the new arms' defaults). */
    public ItemStack(ItemType type, int count, int damage, String displayName,
                     Map<Integer, Integer> enchantments, boolean unbreakable,
                     int repairCost, int hideFlags, List<String> lore,
                     Map<String, Object> blockEntityTag) {
        this(type, count, damage, displayName, enchantments, unbreakable,
                repairCost, hideFlags, lore, blockEntityTag, null, null, null);
    }

    /** The pre-color twelve-arg shape (the compatibility constructor). */
    public ItemStack(ItemType type, int count, int damage, String displayName,
                     Map<Integer, Integer> enchantments, boolean unbreakable,
                     int repairCost, int hideFlags, List<String> lore,
                     Map<String, Object> blockEntityTag,
                     List<String> canDestroy, List<String> canPlaceOn) {
        this(type, count, damage, displayName, enchantments, unbreakable,
                repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn, null);
    }

    /** The pre-J1 thirteen-arg shape (the compatibility constructor). */
    public ItemStack(ItemType type, int count, int damage, String displayName,
                     Map<Integer, Integer> enchantments, boolean unbreakable,
                     int repairCost, int hideFlags, List<String> lore,
                     Map<String, Object> blockEntityTag,
                     List<String> canDestroy, List<String> canPlaceOn,
                     Integer displayColor) {
        this(type, count, damage, displayName, enchantments, unbreakable,
                repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn,
                displayColor, null, null);
    }

    /** The pre-K1 fourteen-arg shape (the compatibility constructor). */
    public ItemStack(ItemType type, int count, int damage, String displayName,
                     Map<Integer, Integer> enchantments, boolean unbreakable,
                     int repairCost, int hideFlags, List<String> lore,
                     Map<String, Object> blockEntityTag,
                     List<String> canDestroy, List<String> canPlaceOn,
                     Integer displayColor,
                     List<AttributeModifier> attributeModifiers) {
        this(type, count, damage, displayName, enchantments, unbreakable,
                repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn,
                displayColor, attributeModifiers, null, null);
    }

    /** The pre-fireworks fifteen-arg shape (the compatibility constructor). */
    public ItemStack(ItemType type, int count, int damage, String displayName,
                     Map<Integer, Integer> enchantments, boolean unbreakable,
                     int repairCost, int hideFlags, List<String> lore,
                     Map<String, Object> blockEntityTag,
                     List<String> canDestroy, List<String> canPlaceOn,
                     Integer displayColor,
                     List<AttributeModifier> attributeModifiers,
                     SkullProfile skullOwner) {
        this(type, count, damage, displayName, enchantments, unbreakable,
                repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn,
                displayColor, attributeModifiers, skullOwner, null);
    }

    /** The pre-book sixteen-arg shape (VIII12's compatibility constructor). */
    public ItemStack(ItemType type, int count, int damage, String displayName,
                     Map<Integer, Integer> enchantments, boolean unbreakable,
                     int repairCost, int hideFlags, List<String> lore,
                     Map<String, Object> blockEntityTag,
                     List<String> canDestroy, List<String> canPlaceOn,
                     Integer displayColor,
                     List<AttributeModifier> attributeModifiers,
                     SkullProfile skullOwner,
                     Map<String, Object> fireworks) {
        this(type, count, damage, displayName, enchantments, unbreakable,
                repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn,
                displayColor, attributeModifiers, skullOwner, fireworks,
                null, null, null, false, 0);
    }

    /** A fresh stack with no damage and no custom name. */
    public ItemStack(ItemType type, int count) {
        this(type, count, 0, null);
    }

    /** A stack with explicit damage and no custom name (the common existing shape). */
    public ItemStack(ItemType type, int count, int damage) {
        this(type, count, damage, null);
    }

    /** A stack with a custom name (the anvil shape). */
    public ItemStack(ItemType type, int count, int damage, String displayName) {
        this(type, count, damage, displayName, null);
    }

    public static ItemStack of(ItemType type, int count) {
        Objects.requireNonNull(type, "type");
        return new ItemStack(type, count);
    }

    public static ItemStack of(ItemType type) {
        return of(type, 1);
    }

    public boolean isEmpty() {
        return type == null || count <= 0;
    }

    /**
     * @return a stack of the same type, damage and name with {@code newCount}
     *         units, or the canonical empty stack when it would hold nothing.
     */
    public ItemStack withCount(int newCount) {
        if (newCount <= 0) {
            return isEmpty() ? this : EMPTY;
        }
        return new ItemStack(type, newCount, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn,
                displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /**
     * @return a stack of the same type, count and name with the given damage
     *         value. Dual-role like the field itself: validated durability wear
     *         on durable items, variant metadata otherwise (charcoal, red sand).
     *         Breaking (damage reaching the limit) is an inventory decision,
     *         not a value transformation.
     */
    public ItemStack withDamage(int newDamage) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, newDamage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn,
                displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /** @return the same stack carrying {@code newName} (null clears the name). */
    public ItemStack withName(String newName) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, newName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn,
                displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /**
     * @return the same stack carrying {@code newEnchantments} (null or empty
     *         clears the enchantments — the vanilla setEnchantments rule).
     */
    public ItemStack withEnchantments(Map<Integer, Integer> newEnchantments) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, newEnchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn,
                displayColor, attributeModifiers, skullOwner, fireworks);
    }

    // ---------------------------------------------------------------
    // The G1 arms' builders (the tag compound's remaining rows).
    // ---------------------------------------------------------------

    /** @return the same stack with the Unbreakable flag (the durability gate). */
    public ItemStack withUnbreakable(boolean flag) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                flag, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /** @return the same stack carrying the anvil's RepairCost (0 clears). */
    public ItemStack withRepairCost(int cost) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, Math.max(0, cost), hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /** @return the same stack carrying the tooltip's HideFlags mask (0 clears). */
    public ItemStack withHideFlags(int flags) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, Math.max(0, flags), lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /** @return the same stack carrying the display.Lore lines (null clears). */
    public ItemStack withLore(List<String> lines) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lines, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /**
     * @return the same stack carrying the tag's BlockEntityTag compound in its
     *         boxed form (Wave H2 — BlockItem.setTileNbt's row, merged into the
     *         freshly-placed tile's rows at placement; null or empty clears).
     */
    public ItemStack withBlockEntityTag(Map<String, Object> rows) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, rows, canDestroy, canPlaceOn,
                displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /**
     * @return the same stack carrying the tag's CanDestroy list (Wave I —
     *         the adventure mine gate's block keys; null or empty clears).
     */
    public ItemStack withCanDestroy(List<String> keys) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                keys, canPlaceOn, displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /**
     * @return the same stack carrying the tag's CanPlaceOn list (the
     *         adventure place gate's block keys; null or empty clears).
     */
    public ItemStack withCanPlaceOn(List<String> keys) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, keys, displayColor, attributeModifiers, skullOwner, fireworks);
    }

    /**
     * @return the same stack carrying the tag's display.color int (the
     *         leather tint, a contains(...,3) INT row; null clears).
     */
    public ItemStack withDisplayColor(Integer color) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, color, attributeModifiers);
    }

    /**
     * @return the same stack carrying the tag's AttributeModifiers list
     *         (Wave J): null clears to absent (the item's own defaults
     *         answer again), a non-null list — empty included — is the
     *         tag's own form (the attribute-wipe: an explicitly empty
     *         list strips the item's default modifiers, exactly the
     *         reference's present-row branch over the defaults).
     */
    public ItemStack withAttributeModifiers(List<AttributeModifier> modifiers) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, modifiers, skullOwner, fireworks);
    }

    /**
     * @return the same stack carrying the tag's SkullOwner profile (Wave
     *         K — the player-head placement consult's own row; null
     *         clears to the plain Steve head).
     */
    public ItemStack withSkullOwner(SkullProfile profile) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, profile, fireworks);
    }

    /**
     * @return the same stack carrying the tag's Fireworks compound (the
     *         firework-rocket family — the fuse band's Flight byte + the
     *         Explosions list the client's burst reads; null or empty
     *         clears to the plain i=1 band).
     */
    public ItemStack withFireworks(Map<String, Object> compound) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner,
                compound, bookPages, bookAuthor, bookTitle, bookResolved, bookGeneration);
    }

    /**
     * @return the same stack carrying the tag's pages list (the book
     *         family — VIII12): null or empty clears to absent.
     */
    public ItemStack withBookPages(List<String> pages) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner,
                fireworks, pages, bookAuthor, bookTitle, bookResolved, bookGeneration);
    }

    /**
     * @return the same stack carrying the tag's author row (the signing
     *         player's own name — MC|BSign's server-authoritative write).
     */
    public ItemStack withBookAuthor(String author) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner,
                fireworks, bookPages, author, bookTitle, bookResolved, bookGeneration);
    }

    /** @return the same stack carrying the tag's title row. */
    public ItemStack withBookTitle(String title) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner,
                fireworks, bookPages, bookAuthor, title, bookResolved, bookGeneration);
    }

    /**
     * @return the same stack carrying the tag's resolved byte (the read
     *         gate's once-only marker — resolvePages line 83).
     */
    public ItemStack withBookResolved(boolean resolved) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner,
                fireworks, bookPages, bookAuthor, bookTitle, resolved, bookGeneration);
    }

    /** @return the same stack carrying the tag's generation int (the copy counter). */
    public ItemStack withBookGeneration(int generation) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag,
                canDestroy, canPlaceOn, displayColor, attributeModifiers, skullOwner,
                fireworks, bookPages, bookAuthor, bookTitle, bookResolved, generation);
    }

    /**
     * @return the stack with one enchantment set (level 0 or below removes —
     *         the vanilla addEnchantment path stores, it never stacks).
     */
    public ItemStack withEnchantment(int id, int level) {
        if (isEmpty()) {
            return this;
        }
        java.util.LinkedHashMap<Integer, Integer> next = new java.util.LinkedHashMap<>();
        if (enchantments != null) {
            next.putAll(enchantments);
        }
        if (level <= 0) {
            next.remove(id);
        } else {
            next.put(id, level);
        }
        return withEnchantments(next.isEmpty() ? null : next);
    }

    /** @return the level of an enchantment on this stack (0 when absent). */
    public int enchantmentLevel(int id) {
        return enchantments == null ? 0 : enchantments.getOrDefault(id, 0);
    }

    /**
     * @return the read-only enchantment map, null when unenchanted (the
     *         {@code tag.ench} presence check the enchanting table's
     *         {@code isEnchantable} gate reads — an already-enchanted stack
     *         refuses re-enchanting, exactly like the reference).
     */
    public Map<Integer, Integer> enchantments() {
        return enchantments;
    }

    /**
     * Validates the enchantment map: shorts for id and level, levels >= 1,
     * insertion order kept (the historical tooltip order), defensively
     * immutable. Null/empty normalizes to null (unenchanted).
     */
    private static Map<Integer, Integer> sanitizeEnchantments(Map<Integer, Integer> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        java.util.LinkedHashMap<Integer, Integer> validated = new java.util.LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : input.entrySet()) {
            int id = entry.getKey() == null ? -1 : entry.getKey();
            int level = entry.getValue() == null ? -1 : entry.getValue();
            if (id < 0 || id > MAX_ENCHANTMENT_LEVEL) {
                // Vanilla 1.8.8 enchantment ids start at 0 (protection is id
                // 0) — the historical tag.ench shorts carry them as-is.
                throw new IllegalArgumentException("Enchantment id out of short range: " + id);
            }
            if (level < 1 || level > MAX_ENCHANTMENT_LEVEL) {
                throw new IllegalArgumentException("Enchantment level out of short range: " + level);
            }
            validated.put(id, level);
        }
        return java.util.Collections.unmodifiableMap(validated);
    }

    /**
     * @return a stack holding up to {@code amount} units taken from the top of
     *         this stack (used by validated pickup/split operations), and this
     *         stack reduced accordingly is produced via {@link #withCount}.
     *         Damage and the custom name carry over: splitting a worn named
     *         sword yields the same worn named sword.
     */
    public ItemStack split(int amount) {
        if (isEmpty() || amount <= 0) {
            return EMPTY;
        }
        int taken = Math.min(Math.min(amount, count), type.maxStackSize());
        return new ItemStack(type, taken, damage, displayName, enchantments,
                unbreakable, repairCost, hideFlags, lore, blockEntityTag, canDestroy, canPlaceOn,
                displayColor, attributeModifiers, skullOwner, fireworks,
                bookPages, bookAuthor, bookTitle, bookResolved, bookGeneration);
    }

    /**
     * The merge rule every container and the item-entity system play: same
     * type, same damage field (wear or variant), same custom name and same
     * enchantments — the historical {@code areItemStacksEqual} including its
     * NBT comparison. Two differently named swords never combine; neither do
     * a worn and a fresh pickaxe, nor two differently enchanted ones.
     */
    public static boolean mergeable(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        // The Wave G arms ride the equality (the reference's
        // areItemStacksEqual compares the whole tag compound — a flagged
        // stack never merges into an unflagged one, a lore-carrying stack
        // never merges into a bare one).
        return a.type.equals(b.type)
                && a.damage == b.damage
                && Objects.equals(a.displayName, b.displayName)
                && Objects.equals(a.enchantments, b.enchantments)
                && a.unbreakable == b.unbreakable
                && a.repairCost == b.repairCost
                && a.hideFlags == b.hideFlags
                && Objects.equals(a.lore, b.lore)
                && blockEntityTagEqual(a.blockEntityTag, b.blockEntityTag)
                && Objects.equals(a.canDestroy, b.canDestroy)
                && Objects.equals(a.canPlaceOn, b.canPlaceOn)
                && Objects.equals(a.displayColor, b.displayColor)
                && Objects.equals(a.attributeModifiers, b.attributeModifiers)
                && Objects.equals(a.skullOwner, b.skullOwner)
                && blockEntityTagEqual(a.fireworks, b.fireworks)
                && Objects.equals(a.bookPages, b.bookPages)
                && Objects.equals(a.bookAuthor, b.bookAuthor)
                && Objects.equals(a.bookTitle, b.bookTitle)
                && a.bookResolved == b.bookResolved
                && a.bookGeneration == b.bookGeneration;
    }

    /**
     * The BlockEntityTag arm's own equality: the reference's whole-tag
     * compare is unordered keys + ordered lists + deep values — exactly
     * {@code Map}/{@code List} equals over the boxed tree, except Java's
     * array identity: {@code int[]} needs {@code Arrays.equals} for the
     * NBT INT_ARRAY's value equality.
     */
    private static boolean blockEntityTagEqual(Map<String, Object> a, Map<String, Object> b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, Object> row : a.entrySet()) {
            Object other = b.get(row.getKey());
            if (other == null) {
                return false; // a missing key (null values never exist in a sanitized tree)
            }
            if (!boxedValueEqual(row.getValue(), other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * One boxed NBT value's deep equality (the reference's whole-tag
     * compare): unordered keys, ordered lists, deep values — and the
     * {@code int[]} NBT INT_ARRAY compares by VALUE at every nesting
     * depth (Java's array identity never suffices; the sanitizer's deep
     * copies give every read its own instances).
     */
    private static boolean boxedValueEqual(Object a, Object b) {
        if (a instanceof int[] array) {
            return b instanceof int[] otherArray && Arrays.equals(array, otherArray);
        }
        if (a instanceof Map<?, ?> map) {
            if (!(b instanceof Map<?, ?> otherMap) || map.size() != otherMap.size()) {
                return false;
            }
            for (Map.Entry<?, ?> row : map.entrySet()) {
                Object other = otherMap.get(row.getKey());
                if (other == null) {
                    return false;
                }
                if (!boxedValueEqual(row.getValue(), other)) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof List<?> list) {
            if (!(b instanceof List<?> otherList) || list.size() != otherList.size()) {
                return false;
            }
            for (int i = 0; i < list.size(); i++) {
                if (!boxedValueEqual(list.get(i), otherList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    /**
     * Normalizes a custom name: control characters (including the historical
     * newline exploits) are stripped, the result is trimmed and bounded to
     * {@link #MAX_NAME_LENGTH}; blank input means "no name". Formatting codes
     * ({@code §}-sequences) are kept — the historical rename could carry them
     * and the client renders them.
     */
    private static String sanitizeName(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder cleaned = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 0x20 && c != 0x7F) {
                cleaned.append(c);
            }
        }
        String trimmed = cleaned.toString().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_NAME_LENGTH
                ? trimmed.substring(0, MAX_NAME_LENGTH)
                : trimmed;
    }

    /**
     * The lore list's own sanitize (Slice 93/G1 — the display compound's
     * string list): null or empty parses to absent, every line rides the
     * name's own control-strip and bound, blank lines drop, the list
     * caps at {@code MAX_LORE_LINES} (the tooltip's own practical
     * ceiling), the answer immutable.
     */
    private static List<String> sanitizeLore(List<String> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        java.util.List<String> cleaned = new java.util.ArrayList<>(input.size());
        for (String line : input) {
            String sanitized = sanitizeName(line);
            if (sanitized != null && cleaned.size() < MAX_LORE_LINES) {
                cleaned.add(sanitized);
            }
        }
        return cleaned.isEmpty() ? null : List.copyOf(cleaned);
    }

    /**
     * The pages list's own sanitize (the book family — VIII12): null or
     * empty parses to absent; every page non-null and carried VERBATIM
     * (the reference's own model — a page's length is
     * {@code BookAndQuillItem.isValid}'s channel law, never the model's;
     * the over-length book is REFUSED at the gate, never silently
     * clamped), the count at the defensive {@link #MAX_BOOK_PAGES}
     * bound (a foreign file's honest bound — the reference's model caps
     * nothing, the vanilla client writes at most 50), defensively
     * immutable.
     */
    private static List<String> sanitizeBookPages(List<String> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        java.util.List<String> cleaned = new java.util.ArrayList<>(Math.min(input.size(),
                MAX_BOOK_PAGES));
        for (String page : input) {
            if (page == null || cleaned.size() >= MAX_BOOK_PAGES) {
                continue; // a null row cannot ride the NBT string list; the
                          // count bound holds a foreign payload honest
            }
            cleaned.add(page);
        }
        return cleaned.isEmpty() ? null : List.copyOf(cleaned);
    }

    /**
     * The author/title text rows' sanitize: null or empty parses to absent,
     * otherwise carried VERBATIM (the reference stores the signing player's
     * raw name and the client's raw trimmed title — the 32-character title
     * cap and the author-presence gate are {@code WrittenBookItem.isValid}'s
     * channel law, never the model's).
     */
    private static String sanitizeBookText(String input, String field) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        if (input.length() > 256) {
            throw new IllegalArgumentException(field + " too long: " + input.length());
        }
        return input;
    }

    /**
     * The BlockEntityTag compound's own sanitize (Wave H2 — the reference's
     * whole tag carried verbatim, never interpreted at the model layer):
     * null or empty parses to absent, the walk validates every value
     * against the NBT element families in their boxed form
     * (Byte/Short/Integer/Long/Float/Double/String/int[]/List/Map — nulls
     * and any other type refused), and the answer is a DEEP immutable
     * copy: unmodifiable maps in insertion order, immutable lists, cloned
     * arrays. The reference's NbtCompound keeps any key (empty included) —
     * so does the sanitizer; the SNBT/NBT round trip preserves them.
     */
    private static Map<String, Object> sanitizeBlockEntityTag(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        return (Map<String, Object>) sanitizeBoxedValue(input, "BlockEntityTag");
    }

    /**
     * The Fireworks compound's own sanitize (the firework-rocket family —
     * {@code {Flight:b, Explosions:[...]}}): the same boxed-tree walk as
     * BlockEntityTag, the row carried verbatim (the Flight byte names the
     * fuse band, the Explosions ride the synced slot-8 item to the
     * client's own burst render).
     */
    private static Map<String, Object> sanitizeFireworks(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        return (Map<String, Object>) sanitizeBoxedValue(input, "Fireworks");
    }

    /**
     * The adventure gates' own sanitize (Wave I — CanDestroy/CanPlaceOn,
     * the TAG_LIST of TAG_STRING over the block keys): null or empty
     * parses to absent, every entry trimmed non-blank, a bare name gains
     * the minecraft: prefix (the engine's id convention — "stone" and
     * "minecraft:stone" name the same block), the order kept (the
     * reference's NbtList is ordered), the answer immutable.
     */
    private static List<String> sanitizeBlockKeyList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        java.util.List<String> cleaned = new java.util.ArrayList<>(input.size());
        for (String key : input) {
            if (key == null) {
                continue;
            }
            String trimmed = key.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String normalized = trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
            cleaned.add(normalized);
        }
        return cleaned.isEmpty() ? null : List.copyOf(cleaned);
    }

    /**
     * The attribute modifiers' own sanitize (Wave J): null parses to
     * absent, but a NON-NULL list keeps its presence — even an empty
     * one (the attribute-wipe: the tag carried the row, so the item's
     * default modifiers never answer — the reference's own branch,
     * never the every-other-arm empty-to-null convention). The elements
     * validate themselves (the AttributeModifier record's own
     * constructor — the reference's Validate pair); the answer is an
     * immutable copy in the given order (the NbtList is ordered).
     */
    private static List<AttributeModifier> sanitizeAttributeModifiers(
            List<AttributeModifier> input) {
        if (input == null) {
            return null;
        }
        return List.copyOf(input);
    }

    /** One boxed NBT value's validation + deep immutable copy (recursive). */
    private static Object sanitizeBoxedValue(Object value, String where) {
        if (value == null) {
            throw new IllegalArgumentException("BlockEntityTag carries a null value at " + where);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof String) {
            return value;
        }
        if (value instanceof int[] array) {
            return array.clone();
        }
        if (value instanceof List<?> list) {
            java.util.List<Object> copied = new java.util.ArrayList<>(list.size());
            for (Object element : list) {
                copied.add(sanitizeBoxedValue(element, where + "[list]"));
            }
            return List.copyOf(copied);
        }
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> copied = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> row : map.entrySet()) {
                Object key = row.getKey();
                if (!(key instanceof String keyString)) {
                    throw new IllegalArgumentException("BlockEntityTag carries a non-string key at " + where);
                }
                copied.put(keyString, sanitizeBoxedValue(row.getValue(), where + "." + keyString));
            }
            return java.util.Collections.unmodifiableMap(copied);
        }
        throw new IllegalArgumentException("BlockEntityTag carries an unsupported value type "
                + value.getClass().getName() + " at " + where);
    }
}
