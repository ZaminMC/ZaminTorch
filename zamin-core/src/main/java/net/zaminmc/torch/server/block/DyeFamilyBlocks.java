package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * The dye-coloring world families (Slice 31 — the reference's
 * {@code DyeRecipes.java} + the Block registrations 35/95/159/160/171/172):
 * every family is ONE vanilla item whose damage field carries the
 * {@code DyeColor} id, expanded on the engine side into flattened per-color
 * block ids (the registry's own model for wire nibbles — the same shape as
 * the infested eggs and the dispenser states).
 *
 * <p>The color domain is the reference's {@code DyeColor.getId()} (the BLOCK
 * metadata order: 0 white .. 15 black — the dye ITEM's own metadata is the
 * inverted domain, 15 - id, and never appears here).</p>
 *
 * <p>Families and their wire ids:</p>
 * <ul>
 *   <li><b>wool</b> (35 — {@code minecraft:wool} the item, damage = color;
 *       the white variant keeps the plain {@code minecraft:wool} block id,
 *       the other fifteen flatten to {@code <color>_wool});</li>
 *   <li><b>stained glass</b> (95 — the item {@code minecraft:stained_glass},
 *       blocks {@code stained_glass_<color>});</li>
 *   <li><b>stained glass panes</b> (160 — the item
 *       {@code minecraft:stained_glass_pane}, blocks
 *       {@code stained_glass_pane_<color>});</li>
 *   <li><b>carpets</b> (171 — the item {@code minecraft:carpet}, blocks
 *       {@code carpet_<color>});</li>
 *   <li><b>stained hardened clay</b> (159 — the item
 *       {@code minecraft:stained_hardened_clay}, blocks
 *       {@code stained_clay_<color>}; the temple slice's orange/yellow pair
 *       joins the full sixteen).</li>
 * </ul>
 */
public final class DyeFamilyBlocks {

    private DyeFamilyBlocks() {
    }

    /** The reference's DyeColor.getId() order (the block-metadata domain). */
    public static final List<String> COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "silver", "cyan", "purple", "blue", "brown", "green",
            "red", "black");

    /** The display names (DyeColor's own capitalization). */
    private static final List<String> DISPLAY = List.of(
            "White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink",
            "Gray", "Silver", "Cyan", "Purple", "Blue", "Brown", "Green",
            "Red", "Black");

    // ------------------------------------------------------------- the items

    public static final Identifier WOOL_ITEM = Identifier.parse("minecraft:wool");
    public static final Identifier STAINED_GLASS_ITEM = Identifier.parse("minecraft:stained_glass");
    public static final Identifier STAINED_GLASS_PANE_ITEM =
            Identifier.parse("minecraft:stained_glass_pane");
    public static final Identifier CARPET_ITEM = Identifier.parse("minecraft:carpet");
    public static final Identifier STAINED_HARDENED_CLAY_ITEM =
            Identifier.parse("minecraft:stained_hardened_clay");

    /** The five damage-domain family items (the placement consult). */
    public static final List<Identifier> FAMILY_ITEMS = List.of(
            WOOL_ITEM, STAINED_GLASS_ITEM, STAINED_GLASS_PANE_ITEM,
            CARPET_ITEM, STAINED_HARDENED_CLAY_ITEM);

    // ------------------------------------------------------------- the blocks

    /**
     * The block id of a family member: wool flattens only the fifteen
     * non-white colors (white keeps the plain {@code minecraft:wool} id —
     * every placed white wool already in the world stays valid), the other
     * families flatten all sixteen.
     */
    public static Identifier blockOf(String familyPrefix, int color) {
        String colorName = COLORS.get(color & 0xF);
        // white wool keeps the vanilla plain id (the block IS wool 0).
        if (color == 0 && familyPrefix.equals("wool")) {
            return WOOL_ITEM;
        }
        return Identifier.parse("minecraft:" + colorName + "_" + familyPrefix);
    }

    /** The stained glass family's block id shape ({@code stained_glass_<color>}). */
    public static Identifier stainedGlassBlock(int color) {
        return Identifier.parse("minecraft:stained_glass_" + COLORS.get(color & 0xF));
    }

    /** The stained pane family's block id shape ({@code stained_glass_pane_<color>}). */
    public static Identifier stainedGlassPaneBlock(int color) {
        return Identifier.parse("minecraft:stained_glass_pane_" + COLORS.get(color & 0xF));
    }

    /** The carpet family's block id shape ({@code carpet_<color>}). */
    public static Identifier carpetBlock(int color) {
        return Identifier.parse("minecraft:carpet_" + COLORS.get(color & 0xF));
    }

    /** The stained clay family's block id shape ({@code stained_clay_<color>}). */
    public static Identifier stainedClayBlock(int color) {
        return Identifier.parse("minecraft:stained_clay_" + COLORS.get(color & 0xF));
    }

    /** The display name of a family member for the registry's constants. */
    public static String displayName(String familyDisplay, int color) {
        return DISPLAY.get(color & 0xF) + " " + familyDisplay;
    }

    // ------------------------------------------------------------- predicates

    /** @return whether the id is one of the sixteen wool blocks. */
    public static boolean isWool(Identifier id) {
        return id.namespace().equals("minecraft") && (id.value().equals("wool")
                || (id.value().endsWith("_wool") && colorIndexOf(id, "_wool") >= 0));
    }

    /** @return whether the id is one of the sixteen stained glass blocks. */
    public static boolean isStainedGlass(Identifier id) {
        return id.namespace().equals("minecraft")
                && id.value().startsWith("stained_glass_")
                && colorIndexOf(id, "stained_glass_") >= 0;
    }

    /** @return whether the id is one of the sixteen stained pane blocks. */
    public static boolean isStainedGlassPane(Identifier id) {
        return id.namespace().equals("minecraft")
                && id.value().startsWith("stained_glass_pane_")
                && colorIndexOf(id, "stained_glass_pane_") >= 0;
    }

    /** @return whether the id is one of the sixteen carpet blocks. */
    public static boolean isCarpet(Identifier id) {
        return id.namespace().equals("minecraft")
                && id.value().startsWith("carpet_")
                && colorIndexOf(id, "carpet_") >= 0;
    }

    /** @return whether the id is one of the sixteen stained clay blocks. */
    public static boolean isStainedClay(Identifier id) {
        return id.namespace().equals("minecraft")
                && id.value().startsWith("stained_clay_")
                && colorIndexOf(id, "stained_clay_") >= 0;
    }

    /** @return whether the id is any member of the five color families. */
    public static boolean isColorFamily(Identifier id) {
        return isWool(id) || isStainedGlass(id) || isStainedGlassPane(id)
                || isCarpet(id) || isStainedClay(id);
    }

    // ------------------------------------------------------------- resolution

    /**
     * The color id of a family block (the DyeColor getId domain, 0..15), or
     * -1 when the id is not a family member.
     */
    public static int colorOf(Identifier id) {
        if (id.value().equals("wool")) {
            return 0;
        }
        int wool = colorIndexOf(id, "_wool");
        if (wool >= 0) {
            return wool;
        }
        int glass = colorIndexOf(id, "stained_glass_");
        if (glass >= 0 && !id.value().startsWith("stained_glass_pane")) {
            return glass;
        }
        int pane = colorIndexOf(id, "stained_glass_pane_");
        if (pane >= 0) {
            return pane;
        }
        int carpet = colorIndexOf(id, "carpet_");
        if (carpet >= 0) {
            return carpet;
        }
        int clay = colorIndexOf(id, "stained_clay_");
        if (clay >= 0) {
            return clay;
        }
        return -1;
    }

    /**
     * The family item a family block drops as (the single vanilla item whose
     * damage field carries the color), or empty when the id is not a member.
     */
    public static Optional<Identifier> dropItemOf(Identifier id) {
        if (isWool(id)) {
            return Optional.of(WOOL_ITEM);
        }
        if (isStainedGlass(id)) {
            return Optional.of(STAINED_GLASS_ITEM);
        }
        if (isStainedGlassPane(id)) {
            return Optional.of(STAINED_GLASS_PANE_ITEM);
        }
        if (isCarpet(id)) {
            return Optional.of(CARPET_ITEM);
        }
        if (isStainedClay(id)) {
            return Optional.of(STAINED_HARDENED_CLAY_ITEM);
        }
        return Optional.empty();
    }

    /**
     * The damage-aware placement resolution ({@code ItemBlock.onItemUse} →
     * {@code getStateFromMeta(stack.getMetadata())}): a family item with
     * damage i places the family's color-i block. Non-family items answer
     * empty and the caller falls through to the plain identifier lookup.
     */
    public static Optional<Identifier> placementBlockOf(Identifier item, int damage) {
        if (!item.namespace().equals("minecraft")) {
            return Optional.empty();
        }
        String value = item.value();
        if (value.equals("wool")) {
            return Optional.of(blockOf("wool", damage));
        }
        if (value.equals("stained_glass")) {
            return Optional.of(stainedGlassBlock(damage));
        }
        if (value.equals("stained_glass_pane")) {
            return Optional.of(stainedGlassPaneBlock(damage));
        }
        if (value.equals("carpet")) {
            return Optional.of(carpetBlock(damage));
        }
        if (value.equals("stained_hardened_clay")) {
            return Optional.of(stainedClayBlock(damage));
        }
        return Optional.empty();
    }

    /**
     * The color suffix of a family block id under the given prefix: the
     * suffix must be one of the sixteen DyeColor names, else -1 (the guard
     * that keeps {@code carpet_floor} or a future {@code stained_glass_x}
     * from aliasing the family).
     */
    private static int colorIndexOf(Identifier id, String prefixOrSuffix) {
        String value = id.value();
        String color;
        if (prefixOrSuffix.startsWith("_")) {
            // the "_wool" suffix form
            if (!value.endsWith(prefixOrSuffix)) {
                return -1;
            }
            color = value.substring(0, value.length() - prefixOrSuffix.length());
        } else {
            if (!value.startsWith(prefixOrSuffix)) {
                return -1;
            }
            color = value.substring(prefixOrSuffix.length());
        }
        int index = COLORS.indexOf(color);
        return index;
    }
}
