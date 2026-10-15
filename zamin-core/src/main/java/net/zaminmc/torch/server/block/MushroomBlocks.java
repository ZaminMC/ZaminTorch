package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The huge-mushroom pair (Slice 40 — MushroomBlock 1:1 over the
 * reference's registrations): the brown mushroom block 99 and the red
 * mushroom block 100 (both Material.WOOD, {@code setStrength(0.2F)}
 * WOOD_SOUNDS, the plant item their drops walk).
 *
 * <p>The state vocabulary is the reference's own VARIANT band
 * (getStateFromMetadata: the fourteen ids — 0 all_inside, 1-9 the
 * directional pores and corners, 10 stem, 14 all_outside, 15 all_stem).
 * The bare identifier carries the ALL_OUTSIDE form — the reference's own
 * defaultState and getPlacementState (the item's placed shape and the
 * creative pick's {@code getPickItem} twin).</p>
 */
public final class MushroomBlocks {

    private MushroomBlocks() {
    }

    /** The pair: name -> wire id (Block.java's own rows). */
    public static final Map<String, Integer> MUSHROOMS = Map.of(
            "brown_mushroom_block", 99,
            "red_mushroom_block", 100);

    /** One VARIANT band member: the metadata id with its key. */
    public record Variant(int id, String key) { }

    /** The VARIANT band: the reference's own fourteen ids. */
    public static final List<Variant> VARIANTS = List.of(
            new Variant(0, "all_inside"),
            new Variant(1, "north_west"),
            new Variant(2, "north"),
            new Variant(3, "north_east"),
            new Variant(4, "west"),
            new Variant(5, "center"),
            new Variant(6, "east"),
            new Variant(7, "south_west"),
            new Variant(8, "south"),
            new Variant(9, "south_east"),
            new Variant(10, "stem"),
            new Variant(14, "all_outside"),
            new Variant(15, "all_stem"));

    /** The plant item each family's drop walk yields (getDropItem's plant). */
    public static Identifier plantOf(String family) {
        return Identifier.parse(family.equals("brown_mushroom_block")
                ? "minecraft:brown_mushroom" : "minecraft:red_mushroom");
    }

    /** The family's item identifier. */
    public static Identifier blockItem(String family) {
        return Identifier.parse("minecraft:" + family);
    }

    private static String displayOf(String family) {
        return family.equals("brown_mushroom_block")
                ? "Brown Mushroom Block" : "Red Mushroom Block";
    }

    /** One state's identifier: the bare name carries all_outside (the
     *  defaultState), every other variant its own suffix. */
    private static Identifier stateId(String family, int variant) {
        if (variant == 14) {
            return Identifier.parse("minecraft:" + family);
        }
        for (Variant row : VARIANTS) {
            if (row.id() == variant) {
                return Identifier.parse("minecraft:" + family + "_" + row.key());
            }
        }
        return Identifier.parse("minecraft:" + family);
    }

    /** One state of a family (the metadata band's exact member). */
    public static EngineBlockType state(String family, int variant) {
        return new EngineBlockType(stateId(family, variant),
                displayOf(family), variant);
    }

    /** Every registration this family owns (the 26 states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (String family : MUSHROOMS.keySet()) {
            for (Variant row : VARIANTS) {
                blocks.add(state(family, row.id()));
            }
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return the mushroom family of a type (item or state), or null. */
    public static String familyOf(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return null;
        }
        String value = type.identifier().value();
        for (String family : MUSHROOMS.keySet()) {
            if (value.equals(family) || value.startsWith(family + "_")) {
                return family;
            }
        }
        return null;
    }

    /** @return whether the type is a huge-mushroom family's state. */
    public static boolean isMushroomBlock(net.zaminmc.torch.block.BlockType type) {
        return familyOf(type) != null;
    }

    /** @return whether the identifier string is a family's item. */
    public static boolean isMushroomItem(String identifier) {
        if (identifier == null) {
            return false;
        }
        String value = identifier.startsWith("minecraft:")
                ? identifier.substring("minecraft:".length()) : identifier;
        return MUSHROOMS.containsKey(value);
    }

    /**
     * The hand drop count (getBaseDropCount line 27:
     * {@code max(0, nextInt(10) - 7)}) — 0 to 2 of the plant.
     */
    public static int baseDropCount(java.util.Random random) {
        return Math.max(0, random.nextInt(10) - 7);
    }
}
