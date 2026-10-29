package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The vine family's connection states (Wave VN-A — the F-VINE family
 * pass's first slice, reference/1.8.8 block/VineBlock.java): the 1.8
 * wire form is legacy 106 with a metadata nibble packing FOUR persisted
 * face bits — {@code SOUTH=1, WEST=2, NORTH=4, EAST=8}
 * (getStateFromMetadata lines 330-333 / getMetadataFromState lines
 * 337-352, the reference's own write order). The FIFTH property (UP)
 * is virtual — {@code resolveVirtualProperties} computes it from the
 * block above at render time, never persisted, never on the wire — so
 * the server-side state space is the sixteen horizontal combinations.
 *
 * <p>The bare {@code minecraft:vine} keeps the ALL-FACES form —
 * metadata 15, the nibble the historical engine already carries on the
 * wire row and the anvil import's own round trip ({@code state(106, 15,
 * "minecraft:vine")} — the bare-name convention: one state of the
 * family keeps the plain name, existing worlds stay valid). The other
 * fifteen states name themselves in the reference's own bit order
 * (south, west, north, east): the all-clear form (metadata 0, a vine
 * placed on TOP of a block — getPlacementState's vertical arm returns
 * the faceless default) is {@code vine_none}, the faceful forms
 * {@code vine_south}, {@code vine_south_west}, …
 * {@code vine_south_west_north} (fourteen faces short of the bare
 * all-faces form).</p>
 *
 * <p>The family's registrations ride the reference's own row
 * (Block.java line 1212: {@code register(106, "vine", new VineBlock()
 * .setStrength(0.2F).setSounds(GRASS_SOUNDS))}) — the grass sound
 * family, the 0.2 strength, the REPLACEABLE_PLANT material (the
 * FOLIAGE map color, walk-through, liquid-replaceable).</p>
 */
public final class VineBlocks {

    /** The face bits (the reference's own metadata packing order). */
    public static final int BIT_SOUTH = 1;
    public static final int BIT_WEST = 2;
    public static final int BIT_NORTH = 4;
    public static final int BIT_EAST = 8;

    /** The bare all-faces form (metadata 15 — BuiltinBlocks' own row). */
    public static final EngineBlockType VINE = BuiltinBlocks.VINE;

    /** The sixteen states, indexed by their metadata nibble 0-15. */
    private static final EngineBlockType[] STATES = buildStates();

    private static EngineBlockType[] buildStates() {
        EngineBlockType[] states = new EngineBlockType[16];
        states[15] = VINE; // the bare name keeps the all-faces form
        // The all-clear form (metadata 0): the reference's default state —
        // the top-placed vine carries no side face.
        states[0] = new EngineBlockType(
                Identifier.parse("minecraft:vine_none"), "Vines", 0);
        for (int bits = 1; bits <= 14; bits++) {
            states[bits] = new EngineBlockType(
                    Identifier.parse("minecraft:vine" + suffix(bits)),
                    "Vines", bits);
        }
        return states;
    }

    /** The state's name suffix: the set faces in the reference's bit order. */
    private static String suffix(int bits) {
        StringBuilder value = new StringBuilder();
        if ((bits & BIT_SOUTH) != 0) {
            value.append("_south");
        }
        if ((bits & BIT_WEST) != 0) {
            value.append("_west");
        }
        if ((bits & BIT_NORTH) != 0) {
            value.append("_north");
        }
        if ((bits & BIT_EAST) != 0) {
            value.append("_east");
        }
        return value.toString();
    }

    private VineBlocks() {
    }

    /** @return whether the type is any vine state (the bare form included). */
    public static boolean isVineFamily(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().equals("vine")
                        || type.identifier().value().startsWith("vine_"));
    }

    /**
     * The state's persisted face bits (the wire metadata nibble,
     * {@code getMetadataFromState}'s packing) — 0 for every non-vine
     * type; the bare all-faces form answers its own 15.
     */
    public static int faceBitsOf(BlockType type) {
        if (!isVineFamily(type)) {
            return 0;
        }
        return ((EngineBlockType) type).legacyMetadata() & 0xF;
    }

    /**
     * The state for a face-bit mask (0-15): the placement's opposite-face
     * law, the decay's face clearing, and the growth's spread arms all
     * consult this — the family's one state vocabulary.
     */
    public static EngineBlockType stateFor(int faceBits) {
        return STATES[faceBits & 0xF];
    }

    /** @return whether a vine can grow on the type's side (VineBlock's
     *         own {@code canGrowOn}: {@code isCube() &&
     *         material.blocksMovement()} — CollisionShapes' full-cube
     *         consult, the documented equivalent; with Wave VN-A's
     *         collision exclusion a vine itself is never a support).
     */
    public static boolean isGrowableSupport(BlockType type) {
        return CollisionShapes.isFullCube(type);
    }

    /** Registers the fifteen new states (the bare form is BuiltinBlocks' own). */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (int bits = 0; bits <= 14; bits++) {
            builder.register(STATES[bits]);
        }
        return builder;
    }
}
