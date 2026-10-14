package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The ender chest family's block types (Slice 29a — the flattened state
 * model, reference/1.8.8 block/EnderChestBlock.java): legacy 130, four
 * horizontal FACING states. The nibble is the reference's metadata
 * {@code FACING.getId()} (EnderChestBlock lines 114-127:
 * getStateFromMetadata reads the Direction id and clamps a Y axis to
 * NORTH, getMetadataFromState writes the id back — the horizontal plane
 * alone covers 2/3/4/5).
 *
 * <p>The FACING is cosmetic (the front texture) plus the open gate's
 * neighbor: the chest's contents are NOT here — every player looks into
 * their own private 27-slot inventory (the reference's
 * {@code player.getEnderChestInventory()} through the block's use arm),
 * so the family carries no world block-entity state at all.</p>
 */
public final class EnderChestBlocks {

    /** The reference direction names in FACING-id order (the horizontal plane). */
    static final int[] FACINGS = {2, 3, 4, 5};
    private static final String[] DIRECTION_KEYS = {"down", "up", "north", "south", "west", "east"};

    private static final EngineBlockType[] STATES = buildStates();

    private EnderChestBlocks() {
    }

    /** @return whether the type is any ender chest state. */
    public static boolean isFamily(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("ender_chest_");
    }

    /** The ender chest's FACING as the reference Direction id. */
    public static int facingOf(BlockType type) {
        String value = type.identifier().value();
        String facing = value.substring(value.lastIndexOf('_') + 1);
        for (int i = 0; i < DIRECTION_KEYS.length; i++) {
            if (DIRECTION_KEYS[i].equals(facing)) {
                return i;
            }
        }
        return 2; // a Y axis clamps to NORTH (getStateFromMetadata's own arm)
    }

    /** The ender chest state for a reference Direction id (Y clamps NORTH). */
    public static EngineBlockType enderChestOf(int facing) {
        for (int i = 0; i < FACINGS.length; i++) {
            if (FACINGS[i] == (facing & 7)) {
                return STATES[i];
            }
        }
        return STATES[0]; // the Y axis clamps to NORTH (the horizontal predicate)
    }

    private static EngineBlockType[] buildStates() {
        EngineBlockType[] all = new EngineBlockType[FACINGS.length];
        for (int i = 0; i < FACINGS.length; i++) {
            all[i] = new EngineBlockType(
                    Identifier.parse("minecraft:ender_chest_" + DIRECTION_KEYS[FACINGS[i]]),
                    "Ender Chest", FACINGS[i]);
        }
        return all;
    }

    /** Registers every ender chest state into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : STATES) {
            builder.register(type);
        }
        return builder;
    }
}
