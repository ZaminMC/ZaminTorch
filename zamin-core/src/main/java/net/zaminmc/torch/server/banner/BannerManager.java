package net.zaminmc.torch.server.banner;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The banner tile store (BannerBlockEntity 1:1): every placed banner's
 * base color and pattern rows, keyed by position.
 *
 * <p>The base rides the banner item's damage field (the DYE metadata
 * domain, inverted from wool: damage 0 is black, 15 is white — the
 * reference's {@code DyeColor.getMetadata()}, BannerItem's own item
 * domain). The patterns are the ordered (hash, color) rows the reference
 * keeps as the raw {@code Patterns} NBT list; the engine models them as
 * the typed row list, translated to the boxed NBT tree at the boundaries
 * (the item's BlockEntityTag, the wire tile, the anvil rows).</p>
 *
 * <p>The static item-side consults ({@code BannerBlockEntity.getBaseColor},
 * {@code getPatternCount}, {@code removeLastPattern}) live here too — they
 * read the stack's BlockEntityTag directly, exactly like the reference's
 * statics.</p>
 *
 * <p>State is simulation-owned (tick thread only); persistence rides the
 * engine's save-all through {@link BannerDataStore}.</p>
 */
public final class BannerManager {

    /** The maximum pattern rows a banner carries (BannerRecipes' own gate). */
    public static final int MAX_PATTERNS = 6;

    /** One applied pattern (the Patterns list's row: the hash + the color). */
    public record Pattern(String hash, int color) {
    }

    /** One placed banner's tile (the base + the ordered pattern rows). */
    public record BannerTile(int base, List<Pattern> patterns) {

        public BannerTile {
            patterns = patterns == null ? List.of() : List.copyOf(patterns);
        }
    }

    /** Events the protocol adapter translates into the 0x35 action-6 tile packet. */
    public interface Listener {
        /** A banner landed (or its tile changed): the tile re-publishes. */
        default void onBannerTileChanged(BlockPosition position, BannerTile tile) {
        }

        /** A banner left (the break, the support drop). */
        default void onBannerRemoved(BlockPosition position) {
        }
    }

    private final Map<BlockPosition, BannerTile> tiles = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The tile at the cell, or null. */
    public BannerTile tileAt(BlockPosition position) {
        return tiles.get(position);
    }

    /** The tiles in the chunk (the wire's chunk-send replay). */
    public List<Map.Entry<BlockPosition, BannerTile>> tilesIn(long packedChunk) {
        List<Map.Entry<BlockPosition, BannerTile>> found = new ArrayList<>();
        for (Map.Entry<BlockPosition, BannerTile> entry : tiles.entrySet()) {
            if (entry.getKey().chunkPosition().packed() == packedChunk) {
                found.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        return found;
    }

    /** The live snapshot (the save-all's input). */
    public Map<BlockPosition, BannerTile> snapshot() {
        return new LinkedHashMap<>(tiles);
    }

    /** The load's restore (the store's read-back). */
    public void restoreAll(Map<BlockPosition, BannerTile> restored) {
        tiles.putAll(restored);
    }

    /**
     * The placement's tile write (BannerItem.useOn lines 58-61 ->
     * BannerBlockEntity.set, lines 23-44): the stack's BlockEntityTag
     * supplies the Patterns copy and, when present, the Base row; an
     * absent Base falls back to the stack's own damage (the item's color
     * domain); a tagless stack reads its damage alone.
     */
    public void place(BlockPosition position, ItemStack stack) {
        int base = stack.damage() & 0xF;
        List<Pattern> patterns = new ArrayList<>();
        Map<String, Object> tag = stack.blockEntityTag();
        if (tag != null) {
            Object rawBase = tag.get("Base");
            if (rawBase instanceof Number number) {
                base = number.intValue() & 0xF;
            }
            Object rawPatterns = tag.get("Patterns");
            if (rawPatterns instanceof List<?> rows) {
                for (Object row : rows) {
                    Pattern pattern = patternFromBoxed(row);
                    if (pattern != null) {
                        patterns.add(pattern);
                    }
                }
            }
        }
        BannerTile tile = new BannerTile(base, patterns);
        tiles.put(position, tile);
        for (Listener listener : listeners) {
            listener.onBannerTileChanged(position, tile);
        }
    }

    /** The tile write the anvil import rides (the read-back's restore arm). */
    public void place(BlockPosition position, BannerTile tile) {
        tiles.put(position, tile);
        for (Listener listener : listeners) {
            listener.onBannerTileChanged(position, tile);
        }
    }

    /** The break/support-drop cleanup (the drop consult reads the tile first). */
    public BannerTile remove(BlockPosition position) {
        BannerTile removed = tiles.remove(position);
        if (removed != null) {
            for (Listener listener : listeners) {
                listener.onBannerRemoved(position);
            }
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // The drop stack (BannerBlock.dropItems lines 86-101): the banner item
    // at the tile's base damage carrying the BlockEntityTag (Base always,
    // Patterns when any) — the shape both the break and the support drop
    // pay, and the placement's set() reads back.
    // ------------------------------------------------------------------

    /**
     * @return the dropped stack for one tile: the banner item at the base
     *         damage with the tag ({@code writeNbt(nbt, base, patternsNbt)}
     *         — Base always, Patterns only when the tile carries rows).
     */
    public static ItemStack dropStack(BannerTile tile) {
        ItemStack stack = new ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.BANNER, 1, tile.base());
        return stack.withBlockEntityTag(tagOf(tile));
    }

    /** The fallback drop for a banner cell with no tile (the plain item, damage 0). */
    public static ItemStack dropStackWithoutTile() {
        return new ItemStack(net.zaminmc.torch.server.item.BuiltinItems.BANNER, 1, 0);
    }

    /** The tile's boxed tag: Base always, Patterns when the tile carries rows. */
    public static Map<String, Object> tagOf(BannerTile tile) {
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("Base", tile.base());
        if (!tile.patterns().isEmpty()) {
            List<Object> rows = new ArrayList<>();
            for (Pattern pattern : tile.patterns()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Pattern", pattern.hash());
                row.put("Color", pattern.color());
                rows.add(row);
            }
            tag.put("Patterns", rows);
        }
        return tag;
    }

    /** One boxed Patterns row back into the typed form (null when malformed). */
    private static Pattern patternFromBoxed(Object row) {
        if (!(row instanceof Map<?, ?> map)) {
            return null;
        }
        Object hash = map.get("Pattern");
        Object color = map.get("Color");
        if (!(hash instanceof String hashString) || !(color instanceof Number colorNumber)) {
            return null;
        }
        return new Pattern(hashString, colorNumber.intValue() & 0xF);
    }

    // ------------------------------------------------------------------
    // The item-side statics (BannerBlockEntity lines 81-89 + 136-150)
    // ------------------------------------------------------------------

    /**
     * The stack's base color ({@code getBaseColor}, lines 81-84): the tag's
     * Base row when present, the stack's own damage otherwise.
     */
    public static int baseColorOf(ItemStack stack) {
        Map<String, Object> tag = stack.blockEntityTag();
        if (tag != null && tag.get("Base") instanceof Number number) {
            return number.intValue() & 0xF;
        }
        return stack.damage() & 0xF;
    }

    /**
     * The stack's pattern row count ({@code getPatternCount}, lines 86-89):
     * the tag's Patterns list size, 0 when absent.
     */
    public static int patternCountOf(ItemStack stack) {
        Map<String, Object> tag = stack.blockEntityTag();
        if (tag != null && tag.get("Patterns") instanceof List<?> rows) {
            return rows.size();
        }
        return 0;
    }

    /**
     * The cauldron wash's stack edit ({@code removeLastPattern}, lines
     * 136-150): the LAST row leaves the copy's Patterns list; the emptied
     * tag clears entirely (and an emptied root tag nulls out — the engine's
     * withBlockEntityTag clears on null/empty, the same normalization).
     */
    public static ItemStack withoutLastPattern(ItemStack stack) {
        Map<String, Object> tag = stack.blockEntityTag();
        if (tag == null || !(tag.get("Patterns") instanceof List<?> rows) || rows.isEmpty()) {
            return stack;
        }
        Map<String, Object> next = new LinkedHashMap<>(tag);
        List<Object> kept = new ArrayList<>(rows);
        kept.remove(kept.size() - 1);
        if (kept.isEmpty()) {
            next.remove("Patterns");
        } else {
            next.put("Patterns", List.copyOf(kept));
        }
        return stack.withBlockEntityTag(next.isEmpty() ? null : next);
    }
}
