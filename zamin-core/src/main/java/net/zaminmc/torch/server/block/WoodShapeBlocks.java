package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The building-shape families over the wood-variant foundation (Slice 36 —
 * the reference's own registrations): the stair families' four facings
 * (the six wood stairs over PlanksBlock.Variant's domain plus brick 108,
 * stone brick 109, and quartz 156), the five new wood fences (188-192),
 * and the wooden slab pairs over the single-slab id 126 (the variant in
 * the metadata nibble, the top half's bit 8 — the shipped oak pair's own
 * encoding).
 *
 * <p>The stair facing band follows the engine's shipped oak shape (the
 * four ascending states over metadata 0-3, {@code east} bare); the wire
 * metadata each state carries is the byte the 1.8.8 client decodes for
 * the ascending direction.</p>
 */
public final class WoodShapeBlocks {

    private WoodShapeBlocks() {
    }

    /** The stair families: name -> wire id (Block.java's own rows). */
    public static final Map<String, Integer> STAIRS = buildStairs();

    /** The five new wood fences: name -> wire id. */
    public static final Map<String, Integer> FENCES = buildFences();

    private static Map<String, Integer> buildStairs() {
        Map<String, Integer> stairs = new LinkedHashMap<>();
        stairs.put("spruce", 134);
        stairs.put("birch", 135);
        stairs.put("jungle", 136);
        stairs.put("acacia", 163);
        stairs.put("dark_oak", 164);
        stairs.put("brick", 108);
        stairs.put("stone_brick", 109);
        stairs.put("quartz", 156);
        return Map.copyOf(stairs);
    }

    private static Map<String, Integer> buildFences() {
        Map<String, Integer> fences = new LinkedHashMap<>();
        fences.put("spruce", 188);
        fences.put("birch", 189);
        fences.put("jungle", 190);
        fences.put("dark_oak", 191);
        fences.put("acacia", 192);
        return Map.copyOf(fences);
    }

    /** The stair facing suffixes over metadata 0-3 (the shipped band). */
    private static final List<String> FACINGS = List.of("east", "west", "south", "north");

    /** The display names of the stair families. */
    private static String displayOf(String family) {
        String[] parts = family.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return name.append("Stairs").toString().trim();
    }

    /** The four facing states of a stair family. */
    public static List<EngineBlockType> stairsStates(String family) {
        return FACINGS.stream()
                .map(facing -> new EngineBlockType(
                        Identifier.parse("minecraft:" + family + "_stairs_" + facing),
                        displayOf(family), FACINGS.indexOf(facing)))
                .toList();
    }

    /** The fence block of a wood (the item keeps the bare id). */
    public static EngineBlockType fenceState(String wood) {
        String display = woodDisplay(wood) + " Fence";
        return new EngineBlockType(
                Identifier.parse("minecraft:" + wood + "_fence"), display);
    }

    /** The slab pair of a wood over the single-slab id 126. */
    public static List<EngineBlockType> slabStates(int variant) {
        String wood = WoodVariantBlocks.WOODS.get(variant);
        String display = woodDisplay(wood) + " Slab";
        return List.of(
                new EngineBlockType(Identifier.parse("minecraft:" + wood + "_slab"),
                        display, variant),
                new EngineBlockType(Identifier.parse("minecraft:" + wood + "_slab_top"),
                        display, variant | 8));
    }

    /**
     * The wood's DOUBLE slab state (Slice 58 — the reference's own 125
     * family: the planks variant id whole in the nibble; the oak carries
     * the wire's own double_wooden_slab name, the other woods their own
     * double_{wood}_slab — the family merges in the stats table).
     */
    public static EngineBlockType doubleSlabState(int variant) {
        String wood = WoodVariantBlocks.WOODS.get(variant);
        String name = variant == 0 ? "double_wooden_slab" : "double_" + wood + "_slab";
        return new EngineBlockType(Identifier.parse("minecraft:" + name),
                woodDisplay(wood) + " Slab", variant);
    }

    private static String woodDisplay(String wood) {
        String[] parts = wood.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return name.toString().trim();
    }

    /** Every registration this family owns (the new ids only). */
    public static List<EngineBlockType> familyBlocks() {
        java.util.ArrayList<EngineBlockType> blocks = new java.util.ArrayList<>();
        for (String family : STAIRS.keySet()) {
            blocks.addAll(stairsStates(family));
        }
        for (String wood : FENCES.keySet()) {
            blocks.add(fenceState(wood));
        }
        for (int variant = 1; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            blocks.addAll(slabStates(variant));
        }
        return List.copyOf(blocks);
    }

    /** Whether the value is any stair family's item id (the bare name). */
    public static boolean isStairsItem(String value) {
        return value.endsWith("_stairs");
    }

    /** Whether the value is any stair family's facing state. */
    public static boolean isStairsState(String value) {
        return value.endsWith("_stairs_east") || value.endsWith("_stairs_west")
                || value.endsWith("_stairs_south") || value.endsWith("_stairs_north");
    }

    /** Whether the value is a wood fence block (the shipped oak included). */
    public static boolean isFence(String value) {
        if (!value.endsWith("_fence")) {
            return false;
        }
        if (value.equals("oak_fence") || value.equals("nether_brick_fence")) {
            return true;
        }
        for (String wood : FENCES.keySet()) {
            if (value.equals(wood + "_fence")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The wooden-slab family of a slab identifier (item or per-state
     * block): {bottom, top, the parent material} — the double's
     * parent-material stand-in (the reference's own double_wooden_slab is
     * visually the wood's full cube).
     */
    public static net.zaminmc.torch.block.BlockType[] woodenSlabFamilyOf(
            String value, java.util.function.IntFunction<Identifier> planksLookup) {
        String bare = value.endsWith("_slab_top") || value.endsWith("_slab")
                ? value.substring(0, value.lastIndexOf("_slab")) : null;
        if (bare == null) {
            return null;
        }
        int variant = -1;
        for (int v = 0; v < WoodVariantBlocks.WOODS.size(); v++) {
            if (bare.equals(WoodVariantBlocks.WOODS.get(v))) {
                variant = v;
                break;
            }
        }
        if (variant < 0) {
            return null;
        }
        return new net.zaminmc.torch.block.BlockType[]{
                slabStates(variant).get(0), slabStates(variant).get(1),
                null /* the parent resolves through the registry by the caller */};
    }
}
