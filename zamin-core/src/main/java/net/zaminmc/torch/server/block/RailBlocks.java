package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The rail quartet (Slice 38 — AbstractRailBlock/PoweredRailBlock/
 * DetectorRailBlock over the reference's registrations): the plain
 * rail 66 (the thirteenth verifier pass — the reference's own
 * RailBlock, alwaysStraight FALSE, the four curves its family alone),
 * the powered golden rail 27, the detector rail 28, and the activator
 * rail 157 (the reference's own twin of the golden rail —
 * register(157, "activator_rail", new PoweredRailBlock()...) — every
 * one {@code setStrength(0.7F)} METAL_SOUNDS over Material.DECORATION).
 *
 * <p>The state vocabulary is the reference's own
 * {@code getStateFromMetadata} band: the plain rail's TEN shapes over
 * metadata 0-9 (the six straight + the four curves — Shape.byId over
 * the full enum; the historical {@code rail_ew} keeps shape 1's name
 * for existing worlds, the eight new states the reference's own keys)
 * with NO POWERED bit; the trio's six straight shapes over metadata
 * 0-5 with the POWERED bit 8 — twelve states per family. The shape's
 * axis is the minecart's rail walk read; the POWERED bit is the golden
 * family's boost/brake and the detector's own emission.</p>
 */
public final class RailBlocks {

    private RailBlocks() {
    }

    /** The three powered families: name -> wire id (Block.java's own rows). */
    public static final Map<String, Integer> RAILS = buildRails();

    /** The plain rail's own family name (RailBlock's register row). */
    public static final String PLAIN_FAMILY = "rail";

    /** The plain rail's wire id (Block.java's row 66). */
    public static final int PLAIN_WIRE_ID = 66;

    private static Map<String, Integer> buildRails() {
        java.util.LinkedHashMap<String, Integer> rails = new java.util.LinkedHashMap<>();
        rails.put("golden_rail", 27);
        rails.put("detector_rail", 28);
        rails.put("activator_rail", 157);
        return Map.copyOf(rails);
    }

    /** The six straight shapes over metadata 0-5 (the reference's Shape ids). */
    static final List<String> SHAPES = List.of(
            "north_south", "east_west",
            "ascending_east", "ascending_west", "ascending_north", "ascending_south");

    /**
     * The plain rail's TEN shape suffixes over metadata 0-9 (the
     * reference's Shape enum, AbstractRailBlock lines 479-489): the
     * bare north_south 0, the historical east-west name 1 (the
     * pre-audit engine's own rail_ew, kept for existing worlds), the
     * four ascents, then the four curves (the alwaysStraight-gated
     * family's own).
     */
    static final List<String> PLAIN_SHAPES = List.of(
            "", "_ew",
            "_ascending_east", "_ascending_west", "_ascending_north", "_ascending_south",
            "_south_east", "_south_west", "_north_west", "_north_east");

    /** One state's identifier: the bare north_south unpowered form keeps the
     *  plain name (the family item shares it — Item.java's own
     *  register(Blocks.POWERED_RAIL) rows). */
    private static Identifier stateId(String family, int shape, boolean powered) {
        StringBuilder value = new StringBuilder(family);
        if (powered) {
            value.append("_powered");
        }
        if (shape != 0) {
            value.append('_').append(SHAPES.get(shape));
        }
        return Identifier.parse("minecraft:" + value);
    }

    /** One state of a family (the metadata band's exact member). */
    public static EngineBlockType state(String family, int shape, boolean powered) {
        return new EngineBlockType(stateId(family, shape, powered),
                displayOf(family), metaOf(shape, powered));
    }

    /** The reference's getMetadataFromState: shape | powered 8. */
    public static int metaOf(int shape, boolean powered) {
        return (shape & 7) | (powered ? 8 : 0);
    }

    /** The display name of a family. */
    private static String displayOf(String family) {
        String[] parts = family.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return name.append("Rail").toString().trim();
    }

    /** Every registration this family owns (the 36 states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (String family : RAILS.keySet()) {
            for (int shape = 0; shape < 6; shape++) {
                for (boolean powered : new boolean[] {false, true}) {
                    blocks.add(state(family, shape, powered));
                }
            }
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The plain rail's ten-state band (the thirteenth verifier pass —
    // RailBlock.getStateFromMetadata lines 31-33: Shape.byId over the
    // FULL ten-shape enum, the curves this family's own alone)
    // ------------------------------------------------------------------

    /** One plain-rail state's identifier (the bare north_south keeps the
     *  item's own name — Item.java's register(Blocks.RAIL) row). */
    private static Identifier plainStateId(int shape) {
        return Identifier.parse("minecraft:" + PLAIN_FAMILY + PLAIN_SHAPES.get(shape));
    }

    /**
     * One plain-rail state over the shape id's own nibble (the bare
     * north_south and the historical {@code rail_ew} keep their
     * registered constants — existing worlds stay valid).
     */
    public static EngineBlockType plainState(int shape) {
        if (shape <= 0) {
            return BuiltinBlocks.RAIL;
        }
        if (shape == 1) {
            return BuiltinBlocks.RAIL_EW;
        }
        return new EngineBlockType(plainStateId(shape), "Rail", shape);
    }

    /** @return whether the type is the plain rail's own state. */
    public static boolean isPlainRail(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        String value = type.identifier().value();
        return value.equals(PLAIN_FAMILY) || value.startsWith(PLAIN_FAMILY + "_");
    }

    /** @return whether the type is any rail family's state (the
     *  reference's own isRail, AbstractRailBlock lines 22-29 — all four
     *  families, the plain rail with its curves included). */
    public static boolean isRailFamily(net.zaminmc.torch.block.BlockType type) {
        return isPlainRail(type) || isTrioRail(type);
    }

    /** The family's own straight-flag (the AbstractRailBlock ctor's
     *  parameter): FALSE for the plain rail (the four curves are its own
     *  family alone), TRUE for the trio. */
    public static boolean alwaysStraightOf(net.zaminmc.torch.block.BlockType type) {
        return !isPlainRail(type) && isTrioRail(type);
    }

    /** The state's shape over its family's own whole band (the plain
     *  rail 0-9, the trio 0-5 — the walk's own read). */
    public static int familyShapeOf(net.zaminmc.torch.block.BlockType type) {
        return isPlainRail(type) ? plainShapeOf(type) : shapeOf(type);
    }

    /** The plain rail's own shape read (the nibble's inverse). */
    public static int plainShapeOf(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        if (!value.startsWith(PLAIN_FAMILY)) {
            return 0;
        }
        String tail = value.substring(PLAIN_FAMILY.length());
        for (int i = 0; i < PLAIN_SHAPES.size(); i++) {
            if (tail.equals(PLAIN_SHAPES.get(i))) {
                return i;
            }
        }
        return 0; // the bare north_south
    }

    /**
     * The state's rewrite with a new shape (the RailNode walk's own
     * {@code state.set(SHAPE, ...)} — the trio's POWERED bit kept, the
     * plain rail's nibble the shape id itself).
     */
    public static net.zaminmc.torch.block.BlockType withShape(
            net.zaminmc.torch.block.BlockType type, int shape) {
        if (isPlainRail(type)) {
            return plainState(shape);
        }
        String family = familyOf(type);
        if (family == null) {
            return type;
        }
        return state(family, Math.min(shape, 5), isPowered(type));
    }

    /** The state's own family item (dropItems' drop): the plain rail's
     *  66 item, the trio's family items. */
    public static Identifier familyItemOf(net.zaminmc.torch.block.BlockType type) {
        if (isPlainRail(type)) {
            return Identifier.parse("minecraft:" + PLAIN_FAMILY);
        }
        return railItem(familyOf(type));
    }

    /**
     * The plain rail's eight NEW states (the four ascents + the four
     * curves — the bare north_south and the historical east_west ride
     * the registered constants).
     */
    public static List<EngineBlockType> plainFamilyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (int shape = 2; shape < PLAIN_SHAPES.size(); shape++) {
            blocks.add(plainState(shape));
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads (the family helpers the engine's arms consult)
    // ------------------------------------------------------------------

    /** @return the rail family of a type (item or state), or null. */
    public static String familyOf(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return null;
        }
        String value = type.identifier().value();
        for (String family : RAILS.keySet()) {
            if (value.equals(family) || value.startsWith(family + "_")) {
                return family;
            }
        }
        return null;
    }

    /** @return whether the type is any rail-trio family's state. */
    public static boolean isTrioRail(net.zaminmc.torch.block.BlockType type) {
        return familyOf(type) != null;
    }

    /** The state's shape band (0-5; the bare form's north_south 0). */
    public static int shapeOf(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        String family = familyOf(type);
        if (family == null) {
            return 0;
        }
        String tail = value.substring(family.length());
        if (tail.startsWith("_powered")) {
            tail = tail.substring("_powered".length());
        }
        for (int i = 0; i < SHAPES.size(); i++) {
            if (tail.equals("_" + SHAPES.get(i))) {
                return i;
            }
        }
        return 0; // the bare north_south
    }

    /** The state's POWERED bit. */
    public static boolean isPowered(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        return value.contains("_powered");
    }

    /** The shape's axis: 0 = the X axis (east-west), 1 = the Z axis (north-south). */
    public static boolean axisIsX(int shape) {
        return shape == 1 || shape == 2 || shape == 3; // east_west + the east/west ascents
    }

    /** The shape's ascending direction (Shape.isAscending). */
    public static boolean isAscending(int shape) {
        return shape >= 2 && shape <= 5;
    }

    /**
     * The state's rewrite with the POWERED bit (updateState's
     * setBlockState(state.set(POWERED, flag1), 3) — the shape kept).
     */
    public static net.zaminmc.torch.block.BlockType withPowered(
            net.zaminmc.torch.block.BlockType type, boolean powered) {
        String family = familyOf(type);
        if (family == null) {
            return type;
        }
        return state(family, shapeOf(type), powered);
    }

    /** @return whether the identifier string is a trio family's item (the bare name). */
    public static boolean isRailItem(String identifier) {
        if (identifier == null) {
            return false;
        }
        String value = identifier.startsWith("minecraft:")
                ? identifier.substring("minecraft:".length()) : identifier;
        return RAILS.containsKey(value);
    }

    /** The family's item identifier. */
    public static Identifier railItem(String family) {
        return Identifier.parse("minecraft:" + family);
    }

    /**
     * The family's placement default (RailBlock line 15 /
     * PoweredRailBlock line 29: the default state is NORTH_SOUTH, the
     * look NEVER consulted anywhere in the family — the thirteenth
     * pass's own retirement of the pre-audit look law; the connection
     * walk re-orients the landing).
     */
    public static net.zaminmc.torch.block.BlockType defaultState(String family) {
        return state(family, 0, false);
    }
}
