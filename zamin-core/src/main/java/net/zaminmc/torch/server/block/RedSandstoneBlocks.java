package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The red sandstone family (Slice 52 — 1.8's own four, 1:1): the block 179
 * over the reference's {@code RedSandstoneBlock} — the TYPE id whole in the
 * metadata nibble (getStateFromMetadata's own band: DEFAULT 0, CHISELED 1,
 * SMOOTH 2), Material.STONE at {@code setStrength(0.8F)} with the STONE
 * sounds; the red sand itself (the reference's {@code SandBlock.Variant.
 * RED_SAND} — sand 12 with the variant id 1 in the nibble, {@code
 * setStrength(0.5F)}); the stairs 180 ({@code new StairsBlock(block.
 * defaultState().set(TYPE, SMOOTH))} — the SMOOTH base); the slab pair 182
 * ({@code SingleRedSandstoneSlabBlock}: the HALF bit 8 over the RED_SANDSTONE
 * variant 0, {@code setStrength(2.0F).setBlastResistance(10.0F)}) and the
 * double 181 ({@code DoubleRedSandstoneSlabBlock}: the SEAMLESS bit never
 * settable by gameplay — the seamless skin rides the metadata-variants
 * slice).
 *
 * <p>The family item (the reference's own {@code VariantBlockItem} over 179)
 * carries the TYPE id in its damage field exactly like the reference's
 * {@code Item.java} row; the sand item's damage 1 is the red sand (one wire
 * item, the damage domain — there is no separate red sand item id in 1.8.8).</p>
 */
public final class RedSandstoneBlocks {

    private RedSandstoneBlocks() {
    }

    /** The block's own row (Block.java line 1379). */
    public static final int WIRE_ID = 179;
    /** The sand variant's own row (Block.java's sand 12, variant 1). */
    public static final int RED_SAND_WIRE_ID = 12;
    /** The stairs' own row (Block.java line 1382). */
    public static final int STAIRS_WIRE_ID = 180;
    /** The double slab's own row (Block.java line 1387). */
    public static final int DOUBLE_SLAB_WIRE_ID = 181;
    /** The single slab's own row (Block.java line 1390). */
    public static final int SLAB_WIRE_ID = 182;

    /**
     * The TYPE band (RedSandstoneBlock.Type's own ids — the reference's keys
     * red_sandstone/chiseled_red_sandstone/smooth_red_sandstone over the
     * engine's flat names).
     */
    static final List<String> TYPES = List.of(
            "red_sandstone", "chiseled_red_sandstone", "smooth_red_sandstone");

    /** The display names (the reference's own default/chiseled/smooth names). */
    static final List<String> DISPLAY = List.of(
            "Red Sandstone", "Chiseled Red Sandstone", "Smooth Red Sandstone");

    /** The stairs' four facing states (the Bukkit band E=0 W=1 S=2 N=3). */
    static final List<String> STAIR_FACINGS = List.of(
            "east", "west", "south", "north");

    /** The family item (Item.java's own VariantBlockItem row over 179). */
    public static final Identifier FAMILY_ITEM = Identifier.parse("minecraft:red_sandstone");

    /** The red sand state (SandBlock.Variant.RED_SAND — sand 12, nibble 1). */
    public static final Identifier RED_SAND = Identifier.parse("minecraft:red_sand");

    /** One TYPE state's identifier. */
    static Identifier stateId(int type) {
        return Identifier.parse("minecraft:" + TYPES.get(type));
    }

    /** One TYPE state (the TYPE id whole in the nibble). */
    public static EngineBlockType state(int type) {
        return new EngineBlockType(stateId(type), DISPLAY.get(type), type);
    }

    /** The red sand state. */
    public static EngineBlockType redSand() {
        return new EngineBlockType(RED_SAND, "Red Sand", 1);
    }

    /** One stairs state (the facing band, E=0 W=1 S=2 N=3). */
    public static EngineBlockType stairsState(int facing) {
        return new EngineBlockType(
                Identifier.parse("minecraft:red_sandstone_stairs_" + STAIR_FACINGS.get(facing)),
                "Red Sandstone Stairs", facing);
    }

    /** Every registration this family owns (the red sand + the three TYPE states + the four stairs). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        blocks.add(redSand());
        for (int type = 0; type < TYPES.size(); type++) {
            blocks.add(state(type));
        }
        for (int facing = 0; facing < STAIR_FACINGS.size(); facing++) {
            blocks.add(stairsState(facing));
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the identifier is one of the three TYPE states. */
    public static boolean isRedSandstone(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        for (String type : TYPES) {
            if (value.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /** @return the state's TYPE id (0 default, 1 chiseled, 2 smooth), or -1. */
    public static int typeOf(Identifier id) {
        if (id == null) {
            return -1;
        }
        String value = id.value();
        for (int i = 0; i < TYPES.size(); i++) {
            if (value.equals(TYPES.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** @return whether the identifier is the red sand state. */
    public static boolean isRedSand(Identifier id) {
        return id != null && id.namespace().equals("minecraft")
                && id.value().equals("red_sand");
    }

    /** @return whether the identifier is one of the stairs' facing states. */
    public static boolean isStairs(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        for (String facing : STAIR_FACINGS) {
            if (value.equals("red_sandstone_stairs_" + facing)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The placement consult (the family item's damage domain over the
     * VariantBlockItem, Item.java's own row): the red sandstone stack's
     * damage field resolves the TYPE state; the sand stack's damage 1 is
     * the red sand (SandBlock.Variant.byId's out-of-band clamp — damage
     * past the band folds to the DEFAULT form exactly like the reference's
     * {@code RedSandstoneBlock.Type.byId}).
     */
    public static Optional<Identifier> placementBlockOf(Identifier item, int damage) {
        if (item == null || !item.namespace().equals("minecraft")) {
            return Optional.empty();
        }
        String value = item.value();
        if (value.equals("red_sandstone")) {
            int type = damage < 0 || damage >= TYPES.size() ? 0 : damage;
            return Optional.of(stateId(type));
        }
        if (value.equals("sand") && damage == 1) {
            return Optional.of(RED_SAND);
        }
        return Optional.empty();
    }
}
