package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The rail trio (Slice 38 — AbstractRailBlock/PoweredRailBlock/
 * DetectorRailBlock over the reference's registrations): the powered
 * golden rail 27, the detector rail 28, and the activator rail 157 (the
 * reference's own twin of the golden rail — register(157, "activator_rail",
 * new PoweredRailBlock()...) — every one
 * {@code setStrength(0.7F)} METAL_SOUNDS over Material.DECORATION).
 *
 * <p>The state vocabulary is the reference's own
 * {@code getStateFromMetadata} band: the six straight shapes over
 * metadata 0-5 (north_south, east_west, ascending_east/west/north/south
 * — Shape.byId, the curves are the plain rail's own family) with the
 * POWERED bit 8 — twelve states per family. The shape's axis is the
 * minecart's rail walk read; the POWERED bit is the golden family's
 * boost/brake and the detector's own emission.</p>
 */
public final class RailBlocks {

    private RailBlocks() {
    }

    /** The three families: name -> wire id (Block.java's own rows). */
    public static final Map<String, Integer> RAILS = buildRails();

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

    /** The placement state (the historical ItemRail): the look's own axis, unpowered. */
    public static net.zaminmc.torch.block.BlockType placementState(String family, boolean eastWest) {
        return state(family, eastWest ? 1 : 0, false);
    }
}
