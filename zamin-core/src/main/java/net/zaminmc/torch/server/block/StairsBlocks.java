package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

/**
 * The stairs family's own parse (the two-box collision slice): the
 * thirteen stair families — the six woods, the cobblestone, the brick,
 * the stone brick, the nether brick, the sandstone, the quartz and the
 * red sandstone — share one flat naming law,
 * {@code {family}_stairs_{facing}[_top]}, the facing segment carrying
 * the reference's own {@code FACING} (the tall back's side, the
 * placement's look direction) and the {@code _top} suffix the
 * {@code HALF} bit (the upside-down form, meta +4).
 *
 * <p>The reference resolves the corner {@code SHAPE} (the
 * inner/outer forms) virtually — never on the wire, always from the
 * neighbours at read time ({@code resolveVirtualProperties}, the
 * client's own {@code getActualState} walk). The collision walk
 * resolves it the same way here, through the flat identifiers the
 * neighbour probe reads.</p>
 */
public final class StairsBlocks {

    private StairsBlocks() {
    }

    /** The four facing segments in the metadata band's own order. */
    private static final String[] FACINGS = {"east", "west", "south", "north"};

    /** @return whether the identifier is one of the stair state forms. */
    public static boolean isStair(Identifier id) {
        return id != null && id.namespace().equals("minecraft")
                && isStairState(id.value());
    }

    /**
     * @return whether the value is a stair STATE — one of the eight
     * wire forms. The bare family items ({@code oak_stairs}) carry no
     * facing segment and never match: a world cell holds a state.
     */
    public static boolean isStairState(String value) {
        return facingOf(value) != null;
    }

    /**
     * The state's facing segment — {@code "east"/"west"/"south"/"north"}
     * — or null when the value is no stair state (the parse is the
     * family-agnostic truth: any {@code _stairs_} family parses, the
     * red sandstone included).
     */
    public static String facingOf(String value) {
        int at = value.indexOf("_stairs_");
        if (at < 0) {
            return null;
        }
        String suffix = value.substring(at + "_stairs_".length());
        boolean top = suffix.endsWith("_top");
        String facing = top
                ? suffix.substring(0, suffix.length() - "_top".length())
                : suffix;
        for (String candidate : FACINGS) {
            if (facing.equals(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The state's HALF bit: the {@code _top} suffix is the upside-down
     * form (the reference's meta +4 fold). Call on a verified stair
     * state only.
     */
    public static boolean isTop(String value) {
        int at = value.indexOf("_stairs_");
        return at >= 0 && value.substring(at + "_stairs_".length()).endsWith("_top");
    }

    /**
     * The reference's {@code isStairSameHalfSameFacing} read: whether
     * the value names a stair of the SAME half AND the SAME facing as
     * the queried state — any family (the reference's own
     * {@code instanceof StairsBlock}, never a same-block check).
     */
    public static boolean sameHalfSameFacing(String value, String query) {
        String facing = facingOf(value);
        String queryFacing = facingOf(query);
        return facing != null && queryFacing != null
                && facing.equals(queryFacing)
                && isTop(value) == isTop(query);
    }
}
