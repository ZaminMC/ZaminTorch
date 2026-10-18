package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The furnace family's horizontal FACING states (Slice L3 — the
 * metadata-variants slice, reference/1.8.8 block/FurnaceBlock.java): the
 * plain furnace over legacy 61 and the lit furnace over 62, four states
 * each. The nibble is the reference's metadata {@code FACING.getId()}
 * (FurnaceBlock lines 114-123: getStateFromMetadata reads the Direction
 * id, getMetadataFromState writes it back — the same band the ender
 * chest shipped in Slice 29a and the chest pair in Slice L2).
 *
 * <p>The placement law (FurnaceBlock.getPlacementState line 133 +
 * onPlaced lines 138-149): the placing player's horizontal facing
 * OPPOSITE — the front faces the player — carried through the lit swap
 * verbatim (AbstractFurnaceBlock's own write keeps the FACING).</p>
 */
public final class FurnaceFacingBlocks {

    /** The reference direction names in FACING-id order (the horizontal plane). */
    static final int[] FACINGS = {2, 3, 4, 5};
    private static final String[] DIRECTION_KEYS = {"down", "up", "north", "south", "west", "east"};

    private static final EngineBlockType[] PLAIN_STATES = buildStates("furnace", "Furnace");
    private static final EngineBlockType[] LIT_STATES = buildStates("furnace_lit", "Furnace");

    private FurnaceFacingBlocks() {
    }

    /**
     * @return whether the type is any plain-furnace facing state. The
     * lit stem shares the {@code furnace_} prefix — the family's own
     * trap — so the lit arm is excluded explicitly (the chest pair's
     * {@code trapped_chest_} prefix never had this overlap).
     */
    public static boolean isPlainFamily(BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        String value = type.identifier().value();
        return value.startsWith("furnace_") && !value.startsWith("furnace_lit");
    }

    /** @return whether the type is any lit-furnace facing state. */
    public static boolean isLitFamily(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("furnace_lit_");
    }

    /** @return whether the type is either furnace family's facing state. */
    public static boolean isAnyFacing(BlockType type) {
        return isPlainFamily(type) || isLitFamily(type);
    }

    /** The furnace state's FACING as the reference Direction id. */
    public static int facingOf(BlockType type) {
        String value = type.identifier().value();
        String stem = value.startsWith("furnace_lit_") ? "furnace_lit_" : "furnace_";
        String facing = value.substring(stem.length());
        for (int i = 0; i < DIRECTION_KEYS.length; i++) {
            if (DIRECTION_KEYS[i].equals(facing)) {
                return i;
            }
        }
        return 2; // a Y axis clamps to NORTH
    }

    /** The plain furnace state for a reference Direction id (Y clamps NORTH). */
    public static EngineBlockType plainOf(int facing) {
        return stateOf(PLAIN_STATES, facing);
    }

    /** The lit furnace state for a reference Direction id (Y clamps NORTH). */
    public static EngineBlockType litOf(int facing) {
        return stateOf(LIT_STATES, facing);
    }

    /**
     * The lit swap's own consult (AbstractFurnaceBlock's updateState: the
     * burning write keeps the FACING whole): the same facing's partner
     * state, or the bare form's own id for the pre-L3 placements.
     */
    public static EngineBlockType partnerOf(BlockType current, boolean burning) {
        if (isAnyFacing(current)) {
            if (isLitFamily(current) == burning) {
                return null; // already in the target form — no write
            }
            return burning ? litOf(facingOf(current)) : plainOf(facingOf(current));
        }
        // The bare forms (the structure-placed and pre-L3 worlds).
        boolean lit = current.identifier().value().equals("furnace_lit");
        if (lit == burning) {
            return null; // already in the target form
        }
        return burning ? net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE_LIT
                : net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE;
    }

    /**
     * The look-opposite consult ({@code getHorizontalFacing().getOpposite()}
     * over the engine's look band 0=S 1=W 2=N 3=E) — the front faces the
     * placer, the chest family's own rule.
     */
    public static int lookOppositeOf(int lookBand) {
        return switch (lookBand) {
            case 0 -> 2; // looking south, the front faces north
            case 1 -> 5; // looking west, the front faces east
            case 2 -> 3; // looking north, the front faces south
            default -> 4; // looking east, the front faces west
        };
    }

    private static EngineBlockType stateOf(EngineBlockType[] states, int facing) {
        for (int i = 0; i < FACINGS.length; i++) {
            if (FACINGS[i] == (facing & 7)) {
                return states[i];
            }
        }
        return states[0]; // the Y axis clamps to NORTH
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

    /** Registers every furnace facing state into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : PLAIN_STATES) {
            builder.register(type);
        }
        for (EngineBlockType type : LIT_STATES) {
            builder.register(type);
        }
        return builder;
    }
}
