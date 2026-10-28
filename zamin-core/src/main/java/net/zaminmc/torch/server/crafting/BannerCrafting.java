package net.zaminmc.torch.server.crafting;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.banner.BannerManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The banner family's two dynamic recipes (BannerRecipes' AddPattern and
 * DuplicatePattern, lines 29-297 — the NBT-transforming class the static
 * recipe table cannot carry): both consult the LIVE grid stacks, and their
 * results copy the input banner's whole tag.
 *
 * <p>These ride AHEAD of the static list in the engine's result consult
 * exactly like the map family's pair (their ingredient sets — banners and
 * dyes — are disjoint from every static row, so the position never crosses
 * a static match); between themselves the vanilla size order stands
 * (AddPattern 10 before DuplicatePattern 2).</p>
 */
public final class BannerCrafting {

    private BannerCrafting() {
    }

    /**
     * One applicable pattern (BannerBlockEntity.Pattern's own rows, the
     * reference's enum order preserved — the FIRST matching shape wins the
     * AddPattern consult). A dye-shape pattern carries its three rows
     * ('#' the dye cells); an item pattern carries its catalyst stack.
     */
    private record Pattern(String hash, String[] rows, Identifier item, int itemDamage) {

        boolean hasItem() {
            return item != null;
        }
    }

    /** The reference's own enum order (the match-order law). */
    private static final List<Pattern> PATTERNS = List.of(
            new Pattern("bl", new String[]{"   ", "   ", "#  "}, null, 0),
            new Pattern("br", new String[]{"   ", "   ", "  #"}, null, 0),
            new Pattern("tl", new String[]{"#  ", "   ", "   "}, null, 0),
            new Pattern("tr", new String[]{"  #", "   ", "   "}, null, 0),
            new Pattern("bs", new String[]{"   ", "   ", "###"}, null, 0),
            new Pattern("ts", new String[]{"###", "   ", "   "}, null, 0),
            new Pattern("ls", new String[]{"#  ", "#  ", "#  "}, null, 0),
            new Pattern("rs", new String[]{"  #", "  #", "  #"}, null, 0),
            new Pattern("cs", new String[]{" # ", " # ", " # "}, null, 0),
            new Pattern("ms", new String[]{"   ", "###", "   "}, null, 0),
            new Pattern("drs", new String[]{"#  ", " # ", "  #"}, null, 0),
            new Pattern("dls", new String[]{"  #", " # ", "#  "}, null, 0),
            new Pattern("ss", new String[]{"# #", "# #", "   "}, null, 0),
            new Pattern("cr", new String[]{"# #", " # ", "# #"}, null, 0),
            new Pattern("sc", new String[]{" # ", "###", " # "}, null, 0),
            new Pattern("bt", new String[]{"   ", " # ", "# #"}, null, 0),
            new Pattern("tt", new String[]{"# #", " # ", "   "}, null, 0),
            new Pattern("bts", new String[]{"   ", "# #", " # "}, null, 0),
            new Pattern("tts", new String[]{" # ", "# #", "   "}, null, 0),
            new Pattern("ld", new String[]{"## ", "#  ", "   "}, null, 0),
            new Pattern("rd", new String[]{"   ", "  #", " ##"}, null, 0),
            new Pattern("lud", new String[]{"   ", "#  ", "## "}, null, 0),
            new Pattern("rud", new String[]{" ##", "  #", "   "}, null, 0),
            new Pattern("mc", new String[]{"   ", " # ", "   "}, null, 0),
            new Pattern("mr", new String[]{" # ", "# #", " # "}, null, 0),
            new Pattern("vh", new String[]{"## ", "## ", "## "}, null, 0),
            new Pattern("hh", new String[]{"###", "###", "   "}, null, 0),
            new Pattern("vhr", new String[]{" ##", " ##", " ##"}, null, 0),
            new Pattern("hhb", new String[]{"   ", "###", "###"}, null, 0),
            new Pattern("bo", new String[]{"###", "# #", "###"}, null, 0),
            // The item patterns (the catalyst stacks, the reference's own
            // rows: the vine, the creeper skull 4, the bricks block, the
            // wither skull 1, the oxeye daisy 8, the enchanted apple 1).
            new Pattern("cbo", null,
                    Identifier.parse("minecraft:vine"), 0),
            new Pattern("cre", null,
                    Identifier.parse("minecraft:skull"), 4),
            new Pattern("gra", new String[]{"# #", " # ", " # "}, null, 0),
            new Pattern("gru", new String[]{" # ", " # ", "# #"}, null, 0),
            new Pattern("bri", null,
                    Identifier.parse("minecraft:brick_block"), 0),
            new Pattern("sku", null,
                    Identifier.parse("minecraft:skull"), 1),
            new Pattern("flo", null,
                    Identifier.parse("minecraft:red_flower"), 8),
            new Pattern("moj", null,
                    Identifier.parse("minecraft:golden_apple"), 1));

    // ------------------------------------------------------------------
    // The pattern-add recipe (AddPattern, lines 33-199)
    // ------------------------------------------------------------------

    /**
     * The add-pattern match (matches lines 38-57): exactly ONE banner,
     * its pattern count below six, and a pattern the grid names — a
     * dye-shape pattern (the 3x3 table alone, the banner sitting on a
     * blank cell) or an item pattern (any grid: one banner + one dye +
     * the catalyst).
     */
    public static boolean addPatternMatches(ItemStack[] grid, int cols) {
        ItemStack banner = null;
        for (ItemStack at : grid) {
            if (!at.isEmpty() && at.type().equals(BuiltinItems.BANNER)) {
                if (banner != null) {
                    return false; // the second banner refuses
                }
                banner = at;
            }
        }
        return banner != null
                && BannerManager.patternCountOf(banner) < BannerManager.MAX_PATTERNS
                && patternOf(grid, cols) != null;
    }

    /**
     * The add-pattern result (getResult lines 60-100): the banner's copy
     * carrying one more pattern row — the matched pattern's hash and the
     * FIRST dye's color.
     */
    public static ItemStack addPatternResult(ItemStack[] grid, int cols) {
        ItemStack banner = null;
        for (ItemStack at : grid) {
            if (!at.isEmpty() && at.type().equals(BuiltinItems.BANNER)) {
                banner = at;
                break;
            }
        }
        if (banner == null) {
            return ItemStack.EMPTY;
        }
        Pattern pattern = patternOf(grid, cols);
        if (pattern == null) {
            return ItemStack.EMPTY;
        }
        int color = 0;
        for (ItemStack at : grid) {
            if (!at.isEmpty() && at.type().equals(BuiltinItems.INK_SAC)) {
                color = at.damage();
                break;
            }
        }
        ItemStack result = banner.withCount(1);
        Map<String, Object> tag = new LinkedHashMap<>(
                banner.blockEntityTag() == null ? Map.of() : banner.blockEntityTag());
        List<Object> rows = new ArrayList<>();
        Object existing = tag.get("Patterns");
        if (existing instanceof List<?> list) {
            rows.addAll(list);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Pattern", pattern.hash());
        row.put("Color", color & 0xF);
        rows.add(row);
        tag.put("Patterns", List.copyOf(rows));
        return result.withBlockEntityTag(tag);
    }

    /**
     * The pattern the grid names (getPattern lines 126-198): the FIRST
     * pattern in the reference's enum order that fits — the item patterns
     * demand one banner + one dye + one catalyst and nothing else; the
     * dye-shape patterns demand the 3x3 table with the dyes on the '#'
     * cells (one consistent color) and the banner on a blank cell.
     */
    private static Pattern patternOf(ItemStack[] grid, int cols) {
        for (Pattern pattern : PATTERNS) {
            if (pattern.hasItem()) {
                if (matchesItemPattern(grid, pattern)) {
                    return pattern;
                }
            } else if (grid.length == 9
                    && matchesShapePattern(grid, pattern)) {
                return pattern;
            }
        }
        return null;
    }

    /**
     * The item-pattern walk (lines 130-157): every non-banner cell is
     * either the ONE dye or the ONE catalyst (the exact item + damage).
     */
    private static boolean matchesItemPattern(ItemStack[] grid, Pattern pattern) {
        boolean catalyst = false;
        boolean dye = false;
        for (ItemStack at : grid) {
            if (at.isEmpty() || at.type().equals(BuiltinItems.BANNER)) {
                continue;
            }
            if (at.type().equals(BuiltinItems.INK_SAC)) {
                if (dye) {
                    return false; // a second dye refuses
                }
                dye = true;
            } else {
                if (catalyst || !at.type().identifier().equals(pattern.item())
                        || at.damage() != pattern.itemDamage()) {
                    return false;
                }
                catalyst = true;
            }
        }
        return catalyst;
    }

    /**
     * The dye-shape walk (lines 158-189): every '#' cell holds a dye of
     * ONE color, every blank cell holds the banner or nothing.
     */
    private static boolean matchesShapePattern(ItemStack[] grid, Pattern pattern) {
        int color = -1;
        for (int k = 0; k < grid.length; k++) {
            int row = k / 3;
            int col = k % 3;
            ItemStack at = grid[k];
            if (!at.isEmpty() && !at.type().equals(BuiltinItems.BANNER)) {
                if (!at.type().equals(BuiltinItems.INK_SAC)) {
                    return false; // not a dye
                }
                if (color != -1 && color != at.damage()) {
                    return false; // mixed dye colors refuse
                }
                if (pattern.rows()[row].charAt(col) == ' ') {
                    return false; // a dye on a blank cell
                }
                color = at.damage();
            } else if (pattern.rows()[row].charAt(col) != ' ') {
                return false; // the banner (or the emptiness) on a dye cell
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The duplicate recipe (DuplicatePattern, lines 201-296)
    // ------------------------------------------------------------------

    /**
     * The duplicate match (matches lines 206-252): exactly two banners of
     * the SAME base color, one carrying patterns and one plain.
     */
    public static boolean duplicateMatches(ItemStack[] grid, int cols) {
        ItemStack carrier = null;
        ItemStack plain = null;
        for (ItemStack at : grid) {
            if (at.isEmpty()) {
                continue;
            }
            if (!at.type().equals(BuiltinItems.BANNER)) {
                return false;
            }
            boolean hasPatterns = BannerManager.patternCountOf(at) > 0;
            if (carrier != null) {
                if (hasPatterns) {
                    return false; // a second carrier refuses
                }
                if (BannerManager.baseColorOf(at) != BannerManager.baseColorOf(carrier)) {
                    return false;
                }
                plain = at;
            } else if (plain != null) {
                if (!hasPatterns) {
                    return false; // a second plain refuses
                }
                if (BannerManager.baseColorOf(at) != BannerManager.baseColorOf(plain)) {
                    return false;
                }
                carrier = at;
            } else if (hasPatterns) {
                carrier = at;
            } else {
                plain = at;
            }
        }
        return carrier != null && plain != null;
    }

    /**
     * The duplicate result (getResult lines 255-266): a copy of the
     * pattern-carrying banner.
     */
    public static ItemStack duplicateResult(ItemStack[] grid, int cols) {
        for (ItemStack at : grid) {
            if (!at.isEmpty() && at.type().equals(BuiltinItems.BANNER)
                    && BannerManager.patternCountOf(at) > 0) {
                return at.withCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The duplicate's remainder (getRemainder lines 279-295): the carrier
     * SURVIVES the craft — the grid's one-unit consumption empties its
     * cell like every other, and the mold writes one unit back. Call
     * AFTER {@code consumeOne} with the PRE-consumption snapshot (the
     * carrier's identity and cell index ride it).
     */
    public static void restoreCarrierMold(
            net.zaminmc.torch.server.player.CraftingGrid grid,
            ItemStack[] preConsume) {
        for (int i = 0; i < preConsume.length && i < grid.size(); i++) {
            ItemStack at = preConsume[i];
            if (!at.isEmpty() && at.type().equals(BuiltinItems.BANNER)
                    && BannerManager.patternCountOf(at) > 0) {
                grid.setCell(i, at.withCount(1));
                return;
            }
        }
    }
}
