package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The chest family's horizontal FACING states (Slice L2 — the
 * metadata-variants slice, reference/1.8.8 block/ChestBlock.java): the
 * plain chest over legacy 54 and the trapped chest over 146, four states
 * each. The nibble is the reference's metadata {@code FACING.getId()}
 * (ChestBlock lines 114-127: getStateFromMetadata reads the Direction id
 * — the horizontal plane alone covers 2/3/4/5, the same band the ender
 * chest shipped in Slice 29a).
 *
 * <p>The placement law (ChestBlock.onPlaced lines 84-135): the yaw band's
 * OPPOSITE is the facing — {@code byIdHorizontal(floor(yaw * 4 / 360 +
 * 0.5) & 3).getOpposite()} — and the neighbour re-facing walk aligns a
 * same-kind pair: the perpendicular neighbour adopts the new chest's
 * state so both fronts match. The walk's own corner: when the facing
 * axis RUNS ALONG the only same-kind neighbour (a north-south look with
 * a north/south neighbour, an east-west look with a west/east one) the
 * placement keeps getPlacementState's NON-opposite state — the vanilla
 * quirk, ported verbatim.</p>
 */
public final class ChestFacingBlocks {

    /** The reference direction names in FACING-id order (the horizontal plane). */
    static final int[] FACINGS = {2, 3, 4, 5};
    private static final String[] DIRECTION_KEYS = {"down", "up", "north", "south", "west", "east"};

    private static final EngineBlockType[] PLAIN_STATES = buildStates("chest", "Chest");
    private static final EngineBlockType[] TRAPPED_STATES = buildStates("trapped_chest", "Trapped Chest");

    private ChestFacingBlocks() {
    }

    /** @return whether the type is any plain-chest facing state. */
    public static boolean isPlainFamily(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("chest_");
    }

    /** @return whether the type is any trapped-chest facing state. */
    public static boolean isTrappedFamily(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("trapped_chest_");
    }

    /** @return whether the type is either chest family's facing state. */
    public static boolean isAnyFacing(BlockType type) {
        return isPlainFamily(type) || isTrappedFamily(type);
    }

    /** The chest state's FACING as the reference Direction id. */
    public static int facingOf(BlockType type) {
        String value = type.identifier().value();
        String facing = value.substring(value.lastIndexOf('_') + 1);
        for (int i = 0; i < DIRECTION_KEYS.length; i++) {
            if (DIRECTION_KEYS[i].equals(facing)) {
                return i;
            }
        }
        return 2; // the bare forms and a Y axis clamp to NORTH
    }

    /** The plain chest state for a reference Direction id (Y clamps NORTH). */
    public static EngineBlockType plainOf(int facing) {
        return stateOf(PLAIN_STATES, facing);
    }

    /** The trapped chest state for a reference Direction id (Y clamps NORTH). */
    public static EngineBlockType trappedOf(int facing) {
        return stateOf(TRAPPED_STATES, facing);
    }

    /** The facing state for a chest kind (plain or trapped) and a Direction id. */
    public static EngineBlockType stateFor(boolean trapped, int facing) {
        return trapped ? trappedOf(facing) : plainOf(facing);
    }

    private static EngineBlockType stateOf(EngineBlockType[] states, int facing) {
        for (int i = 0; i < FACINGS.length; i++) {
            if (FACINGS[i] == (facing & 7)) {
                return states[i];
            }
        }
        return states[0]; // the Y axis clamps to NORTH (the horizontal predicate)
    }

    /**
     * The look-opposite consult ({@code getHorizontalFacing().getOpposite()}
     * over the engine's look band 0=S 1=W 2=N 3=E): the chest's FRONT faces
     * the placer exactly like the ender chest's Slice 29a walk.
     */
    public static int lookOppositeOf(int lookBand) {
        return switch (lookBand) {
            case 0 -> 2; // looking south, the front faces north
            case 1 -> 5; // looking west, the front faces east
            case 2 -> 3; // looking north, the front faces south
            default -> 4; // looking east, the front faces west
        };
    }

    /**
     * The look's own Direction id (getHorizontalFacing without the
     * opposite — the quirk arm's state when the walk leaves the placement
     * un-rewritten).
     */
    public static int lookOf(int lookBand) {
        return switch (lookBand) {
            case 0 -> 3; // south
            case 1 -> 4; // west
            case 2 -> 2; // north
            default -> 5; // east
        };
    }

    private static EngineBlockType[] buildStates(String stem, String display) {
        EngineBlockType[] all = new EngineBlockType[FACINGS.length];
        for (int i = 0; i < FACINGS.length; i++) {
            all[i] = new EngineBlockType(
                    Identifier.parse("minecraft:" + stem + "_" + DIRECTION_KEYS[FACINGS[i]]),
                    display, FACINGS[i]);
        }
        return all;
    }

    /** Registers every chest facing state into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : PLAIN_STATES) {
            builder.register(type);
        }
        for (EngineBlockType type : TRAPPED_STATES) {
            builder.register(type);
        }
        return builder;
    }
}
