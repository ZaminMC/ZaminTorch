package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Identifier;

import java.util.Random;

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
        return type != null && isVineFamily(type.identifier());
    }

    /**
     * @return whether the identifier names any vine state — the identifier
     *         form for the material consults ({@code WorldSolidity
     *         .isReplaceable}'s own read, the REPLACEABLE_PLANT material).
     */
    public static boolean isVineFamily(Identifier id) {
        return id != null && id.namespace().equals("minecraft")
                && (id.value().equals("vine") || id.value().startsWith("vine_"));
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

    /**
     * @return whether a vine can grow on the type's face (VineBlock's own
     *         {@code canGrowOn} lines 146-148: {@code block.isCube() &&
     *         block.material.blocksMovement()} — the RENDER-cube consult,
     *         never the collision box). The GLASS family (GlassBlock +
     *         StainedGlassBlock's own isCube overrides, lines 33-36 + 10-13)
     *         carries a FULL collision cube but never a vine; the engine's
     *         collision-shape consult answers the full box for it, so the
     *         exclusion is carried here. Everything else the consult
     *         passes IS a grow surface in the reference: the glowstone and
     *         the sea lantern (GlowstoneBlock/SeaLanternBlock carry no
     *         isCube override over Material.GLASS — vines DO hang from
     *         both), the ice, the TNT and every wood's leaves (the
     *         AbstractLeavesBlock family never overrides isCube — the
     *         jungle canopy's own law, vines hang from leaves); with Wave
     *         VN-A's collision exclusion a vine itself is never a support.
     */
    public static boolean isGrowableSupport(BlockType type) {
        return CollisionShapes.isFullCube(type)
                && !isGlassFamily(type.identifier());
    }

    /**
     * The glass family's render-cube exclusion (GlassBlock.isCube lines
     * 33-36 + StainedGlassBlock lines 10-13 both override to false over a
     * full collision box — the one full-box family the vine never grows
     * on; the panes fall to their partial shapes, the beacon and the
     * pistons are not registered kinds in the engine at all).
     */
    private static boolean isGlassFamily(Identifier id) {
        String value = id.value();
        return id.namespace().equals("minecraft")
                && (value.equals("glass") || value.startsWith("stained_glass"));
    }

    // ------------------------------------------------------------------
    // The placement pair (Wave VN-B — canBePlaced lines 131-143 +
    // getPlacementState lines 305-308)
    // ------------------------------------------------------------------

    /**
     * The placement's face gate (VineBlock.canBePlaced lines 131-143):
     * a bottom-face click never places (the reference's own default
     * arm); an UP-face click reads the cell ABOVE the placement — the
     * ceiling the faceless form hangs under, NOT the floor below (the
     * reference's own law: a vine never plants on open ground by a top
     * click, only under an overhang two cells up); a horizontal click
     * reads the cell OPPOSITE the clicked face — the clicked block
     * itself, the wall the vine hangs on.
     *
     * @param target the placement cell (the face-offset of the click)
     * @param face   the clicked face (0=down, 1=up, 2=north, 3=south,
     *               4=west, 5=east — the reference's own Direction order)
     */
    public static boolean canPlaceAt(EngineWorld world, BlockPosition target, int face) {
        switch (face) {
            case 1: // UP: the ceiling above the placement (pos.up())
                return target.y() < BlockPosition.MAX_Y
                        && isGrowableSupport(world.getBlock(target.offset(0, 1, 0)));
            case 2: // NORTH: the clicked block (pos.offset(face.getOpposite()))
                return isGrowableSupport(world.getBlock(target.offset(0, 0, 1)));
            case 3: // SOUTH
                return isGrowableSupport(world.getBlock(target.offset(0, 0, -1)));
            case 4: // WEST
                return isGrowableSupport(world.getBlock(target.offset(1, 0, 0)));
            case 5: // EAST
                return isGrowableSupport(world.getBlock(target.offset(-1, 0, 0)));
            default:
                return false; // DOWN never places (the reference's default arm)
        }
    }

    /**
     * The placement's state (VineBlock.getPlacementState lines 305-308): a
     * horizontal click places the OPPOSITE-face state — the vine hung on
     * the clicked wall carries the face bit pointing back at it — and a
     * vertical click the FACELESS default (metadata 0, {@code vine_none},
     * the transient form the decay pops at the first neighbour change).
     */
    public static EngineBlockType placementStateFor(int face) {
        return switch (face) {
            case 2 -> stateFor(BIT_SOUTH); // clicked the wall's north face: hang facing south
            case 3 -> stateFor(BIT_NORTH);
            case 4 -> stateFor(BIT_EAST);
            case 5 -> stateFor(BIT_WEST);
            default -> stateFor(0); // the vertical arm's faceless default
        };
    }

    // ------------------------------------------------------------------
    // The decay consult (Wave VN-B — hasConnections lines 145-168 over
    // neighborChanged lines 172-178)
    // ------------------------------------------------------------------

    /**
     * The vine's own survival consult (VineBlock.hasConnections lines
     * 145-168): each horizontal face bit whose face-neighbour is no
     * longer a growable support is CLEARED — unless the vine ABOVE
     * carries the same bit, the hanging chain that outlives its wall.
     * Every bit gone ({@code getConnectionCount(state) == 0} — the UP
     * property is virtual and always false server-side, so the count IS
     * the horizontal bits) answers FALSE: the caller pops the cell. The
     * bit-clearing write is the reference's flag-2 form (the client
     * sync alone); the engine's single write path (§208) carries it —
     * idempotent at every neighbour it wakes in a settled world.
     */
    public static boolean hasConnections(EngineWorld world, BlockPosition pos, BlockType type) {
        int bits = faceBitsOf(type);
        int original = bits;
        for (int[] dir : HORIZONTAL_BITS) {
            if ((bits & dir[0]) != 0
                    && !isGrowableSupport(world.getBlock(pos.offset(dir[1], 0, dir[2])))) {
                // The chain consult (lines 152-157): the vine above
                // carrying the same bit keeps this one alive.
                BlockType above = pos.y() < BlockPosition.MAX_Y
                        ? world.getBlock(pos.offset(0, 1, 0)) : world.airType();
                if (!isVineFamily(above) || (faceBitsOf(above) & dir[0]) == 0) {
                    bits &= ~dir[0];
                }
            }
        }
        if (bits == 0) {
            return false;
        }
        if (bits != original) {
            world.setBlock(pos, stateFor(bits)); // the reference's flag-2 state write
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The random-tick growth (Wave VN-B — VineBlock.tick lines 180-304
    // over the reference's randomTick -> tick delegation)
    // ------------------------------------------------------------------

    /**
     * The face-bit table in the reference's own iteration order —
     * {@code Direction.Plane.HORIZONTAL}: NORTH, EAST, SOUTH, WEST (the
     * coin loops' RNG stream order). Each row: the bit, dx, dz.
     */
    private static final int[][] HORIZONTAL_BITS = {
            {BIT_NORTH, 0, -1},  // north: bit 4, -Z
            {BIT_EAST, 1, 0},    // east:  bit 8, +X
            {BIT_SOUTH, 0, 1},   // south: bit 1, +Z
            {BIT_WEST, -1, 0}};  // west:  bit 2, -X

    /** The horizontal face bit for a face id (2-5). */
    private static int bitOfFace(int face) {
        return switch (face) {
            case 2 -> BIT_NORTH;
            case 3 -> BIT_SOUTH;
            case 4 -> BIT_WEST;
            default -> BIT_EAST;
        };
    }

    /** The x/z unit offset for a face id (2-5). */
    private static int[] xzOfFace(int face) {
        return switch (face) {
            case 2 -> new int[]{0, -1};
            case 3 -> new int[]{0, 1};
            case 4 -> new int[]{-1, 0};
            default -> new int[]{1, 0};
        };
    }

    /** The reference's {@code clockwiseY} (lines 81-94): N->E->S->W->N. */
    private static int clockwiseY(int face) {
        return switch (face) {
            case 2 -> 5; // north -> east
            case 5 -> 3; // east -> south
            case 3 -> 4; // south -> west
            default -> 2; // west -> north
        };
    }

    /** The reference's {@code counterClockwiseY} (lines 129-141): N->W->S->E->N. */
    private static int counterClockwiseY(int face) {
        return switch (face) {
            case 2 -> 4; // north -> west
            case 4 -> 3; // west -> south
            case 3 -> 5; // south -> east
            default -> 2; // east -> north
        };
    }

    /** The face opposite an id (down/up, north/south, west/east). */
    private static int oppositeFace(int face) {
        return switch (face) {
            case 0 -> 1;
            case 1 -> 0;
            case 2 -> 3;
            case 3 -> 2;
            case 4 -> 5;
            default -> 4;
        };
    }

    /**
     * The growth's whole tick (VineBlock.tick lines 180-304, 1:1): the
     * 1-in-4 roll, the 9x9x3 family census (five vines — the anchor
     * counted — refuse the UP and SIDE arms; the DOWN arm ignores it,
     * the reference's own asymmetry), and the three spread arms over
     * {@code Direction.pick}'s uniform six (the reference's own values
     * order: DOWN, UP, NORTH, SOUTH, WEST, EAST — the engine's face ids
     * are the same numbers).
     */
    public static void growthTick(EngineWorld world, BlockPosition pos, BlockType type,
                                  Random random) {
        // Lines 184-186: the 25% roll.
        if (random.nextInt(4) != 0) {
            return;
        }
        // Lines 187-205: the density census — i = 4 (the +/- 4 box), j = 5
        // (the budget), the early break when the fifth family cell lands.
        // The out-of-bounds y rows are the reference's own air reads
        // (World.getBlockState's empty answer above 255 and below 0).
        boolean dense = false;
        int budget = 5;
        int low = pos.y() > BlockPosition.MIN_Y ? -1 : 0;
        int high = pos.y() < BlockPosition.MAX_Y ? 1 : 0;
        census:
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = low; dy <= high; dy++) {
                    if (isVineFamily(world.getBlock(pos.offset(dx, dy, dz)))) {
                        if (--budget <= 0) {
                            dense = true;
                            break census;
                        }
                    }
                }
            }
        }
        // Line 206: Direction.pick — values()[nextInt(6)], the values
        // order DOWN, UP, NORTH, SOUTH, WEST, EAST (the engine's face ids).
        int direction2 = random.nextInt(6);
        // THE UP ARM (lines 207-223): a vine above, only into air, only
        // under the 255 cap, only outside the dense band — the birth's
        // faces roll from the ANCHOR's own bits (each kept only where the
        // coin lands false AND the beside-above wall grows).
        if (direction2 == 1 && pos.y() < BlockPosition.MAX_Y
                && world.getBlock(pos.offset(0, 1, 0)).equals(world.airType())) {
            if (!dense) {
                BlockPosition above = pos.offset(0, 1, 0);
                int bits = faceBitsOf(type);
                for (int[] dir : HORIZONTAL_BITS) {
                    if (random.nextBoolean()
                            || !isGrowableSupport(world.getBlock(above.offset(dir[1], 0, dir[2])))) {
                        bits &= ~dir[0];
                    }
                }
                if (bits != 0) {
                    world.setBlock(above, stateFor(bits));
                }
            }
        } else if (direction2 <= 1 // DOWN — or UP with no air above (the !isHorizontal arm)
                || (faceBitsOf(type) & bitOfFace(direction2)) != 0) {
            // THE DOWN ARM (lines 224-260): no census gate — the reference
            // grows vines DOWN regardless of the neighbourhood's density.
            if (pos.y() > 1) {
                BlockPosition below = pos.offset(0, -1, 0);
                BlockType belowType = world.getBlock(below);
                if (belowType.equals(world.airType())) {
                    // The extend (lines 232-245): the copy below starts
                    // from the ANCHOR's bits, each thinned by its own coin.
                    int bits = faceBitsOf(type);
                    for (int[] dir : HORIZONTAL_BITS) {
                        if (random.nextBoolean()) {
                            bits &= ~dir[0];
                        }
                    }
                    if (bits != 0) {
                        world.setBlock(below, stateFor(bits));
                    }
                } else if (isVineFamily(belowType)) {
                    // The merge (lines 246-260): the vine below GAINS the
                    // anchor's bits — each only where the coin lands and
                    // the anchor carries it (an OR, never a clear).
                    int bits = faceBitsOf(belowType);
                    for (int[] dir : HORIZONTAL_BITS) {
                        if (random.nextBoolean() && (faceBitsOf(type) & dir[0]) != 0) {
                            bits |= dir[0];
                        }
                    }
                    if (bits != 0) {
                        world.setBlock(below, stateFor(bits));
                    }
                }
            }
        } else if (!dense) {
            // THE SIDE ARM (lines 261-303): the growth direction's face
            // bit is ABSENT on the anchor (the DOWN arm's else) — the
            // spread wraps the corner or caps the wall.
            int[] step = xzOfFace(direction2);
            BlockPosition beside = pos.offset(step[0], 0, step[1]);
            BlockType besideType = world.getBlock(beside);
            if (besideType.equals(world.airType())) {
                int clockwise = clockwiseY(direction2);
                int counter = counterClockwiseY(direction2);
                boolean cwBit = (faceBitsOf(type) & bitOfFace(clockwise)) != 0;
                boolean ccwBit = (faceBitsOf(type) & bitOfFace(counter)) != 0;
                int[] cwStep = xzOfFace(clockwise);
                int[] ccwStep = xzOfFace(counter);
                BlockPosition cwBeside = beside.offset(cwStep[0], 0, cwStep[1]);
                BlockPosition ccwBeside = beside.offset(ccwStep[0], 0, ccwStep[1]);
                if (cwBit && isGrowableSupport(world.getBlock(cwBeside))) {
                    // The side birth (lines 277-279): the target hangs on
                    // its clockwise wall.
                    world.setBlock(beside, stateFor(bitOfFace(clockwise)));
                } else if (ccwBit && isGrowableSupport(world.getBlock(ccwBeside))) {
                    world.setBlock(beside, stateFor(bitOfFace(counter)));
                } else if (cwBit && world.getBlock(cwBeside).equals(world.airType())
                        && isGrowableSupport(world.getBlock(pos.offset(cwStep[0], 0, cwStep[1])))) {
                    // The corner wrap (lines 280-282): the vine wraps
                    // around the anchor's own clockwise wall.
                    world.setBlock(cwBeside, stateFor(bitOfFace(oppositeFace(direction2))));
                } else if (ccwBit && world.getBlock(ccwBeside).equals(world.airType())
                        && isGrowableSupport(world.getBlock(pos.offset(ccwStep[0], 0, ccwStep[1])))) {
                    world.setBlock(ccwBeside, stateFor(bitOfFace(oppositeFace(direction2))));
                } else if (pos.y() < BlockPosition.MAX_Y
                        && isGrowableSupport(world.getBlock(beside.offset(0, 1, 0)))) {
                    // The top cap (lines 285-287): the FACELESS form under
                    // the target's ceiling — the transient metadata-0 vine.
                    world.setBlock(beside, stateFor(0));
                }
            } else if (takesFaceGain(besideType)) {
                // The face gain (lines 289-291): a solid opaque cube in
                // the growth direction GAINS the bit — the vine wraps
                // onto the wall's face.
                world.setBlock(pos, stateFor(faceBitsOf(type) | bitOfFace(direction2)));
            }
        }
    }

    /**
     * The face-gain consult (line 288's {@code block.material
     * .isSolidBlocking() && block.isCube()}) — a GROWABLE support whose
     * material is not translucent: the leaves, the glass family, the
     * glowstone, the sea lantern, the TNT and the ice all GROW vines
     * ({@code canGrowOn} reads blocksMovement alone) but never TAKE a
     * face bit toward one (Material.isSolidBlocking = !translucent &&
     * blocksMovement, Material.java lines 100-101) — only the opaque
     * cubes (the stone, the packed ice) wrap the vine around.
     */
    private static boolean takesFaceGain(BlockType type) {
        return isGrowableSupport(type)
                && !MaterialSolidity.isTranslucentMaterial(type.identifier());
    }

    /** Registers the fifteen new states (the bare form is BuiltinBlocks' own). */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (int bits = 0; bits <= 14; bits++) {
            builder.register(STATES[bits]);
        }
        return builder;
    }
}
