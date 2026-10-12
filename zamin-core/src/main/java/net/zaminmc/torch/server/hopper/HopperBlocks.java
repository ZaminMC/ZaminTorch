package net.zaminmc.torch.server.hopper;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The hopper family's block types (Slice 10b — the flattened state model,
 * reference/1.8.8 block/HopperBlock.java): legacy 154, five FACING values
 * — everything except UP, the reference's own FACING predicate
 * {@code direction != Direction.UP} — with an ENABLED pair per facing.
 * The nibble is the reference's metadata
 * {@code facing | (disabled ? 8 : 0)} (HopperBlock lines 153-190:
 * getFacing reads {@code metadata & 7}, isEnabled reads
 * {@code (metadata & 8) != 8}).
 *
 * <p>The FACING is the OUTPUT side — the hopper pushes toward it. The
 * placement points it opposite the clicked face (an UP outcome clamps to
 * DOWN, the reference's own getPlacementState arm), and a neighbor signal
 * flips the DISABLED pair (a powered hopper stops transferring — the
 * updateEnabled walk).</p>
 */
public final class HopperBlocks {

    /** The reference direction names in FACING-id order (no UP — the hopper never faces up). */
    static final int[] FACINGS = {0, 2, 3, 4, 5};
    private static final String[] DIRECTION_KEYS = {"down", "up", "north", "south", "west", "east"};

    private static final EngineBlockType[][] STATES = buildStates();

    private HopperBlocks() {
    }

    /** @return whether the type is any hopper state. */
    public static boolean isHopper(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("hopper_");
    }

    /** The hopper's FACING as the reference Direction id (the output side). */
    public static int hopperFacing(BlockType type) {
        String value = type.identifier().value();
        String rest = value.endsWith("_disabled")
                ? value.substring(0, value.length() - "_disabled".length()) : value;
        String facing = rest.substring(rest.lastIndexOf('_') + 1);
        for (int i = 0; i < DIRECTION_KEYS.length; i++) {
            if (DIRECTION_KEYS[i].equals(facing)) {
                return i;
            }
        }
        return 0;
    }

    /** @return whether the hopper is the ENABLED pair (not powered off). */
    public static boolean hopperEnabled(BlockType type) {
        return !type.identifier().value().endsWith("_disabled");
    }

    /** The hopper state for a facing + enabled pair. */
    public static EngineBlockType hopperOf(int facing, boolean enabled) {
        for (int i = 0; i < FACINGS.length; i++) {
            if (FACINGS[i] == (facing & 7)) {
                return STATES[i][enabled ? 1 : 0];
            }
        }
        return STATES[0][enabled ? 1 : 0]; // UP clamps to DOWN (the FACING predicate)
    }

    /** The same hopper state with the enabled pair swapped. */
    public static EngineBlockType hopperOfState(BlockType type, boolean enabled) {
        return hopperOf(hopperFacing(type), enabled);
    }

    private static EngineBlockType[][] buildStates() {
        EngineBlockType[][] all = new EngineBlockType[FACINGS.length][2];
        for (int i = 0; i < FACINGS.length; i++) {
            for (int enabled = 0; enabled <= 1; enabled++) {
                all[i][enabled] = new EngineBlockType(
                        Identifier.parse("minecraft:hopper_" + DIRECTION_KEYS[FACINGS[i]]
                                + (enabled == 0 ? "_disabled" : "")),
                        "Hopper", FACINGS[i] | (enabled == 0 ? 8 : 0));
            }
        }
        return all;
    }

    /** Registers every hopper type into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType[] facing : STATES) {
            for (EngineBlockType type : facing) {
                builder.register(type);
            }
        }
        return builder;
    }
}
