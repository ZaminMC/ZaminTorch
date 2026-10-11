package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The piston family's block types (Slice 9e — the flattened state model,
 * reference/1.8.8 block/PistonBaseBlock.java + PistonHeadBlock.java +
 * MovingBlock.java).
 *
 * <p><b>The base</b> (legacy 33 plain / 29 sticky): six FACING values with an
 * EXTENDED pair — {@code piston_<facing>} and {@code piston_<facing>_extended}
 * (+ the {@code sticky_piston_} prefix). The nibble is the reference's
 * metadata: {@code facing | (extended ? 8 : 0)} (PistonBaseBlock lines
 * 357-370). The FACING is the direction the arm extends toward.</p>
 *
 * <p><b>The head</b> (legacy 34): six FACING values with a STICKY pair —
 * {@code piston_head_<facing>} and {@code piston_head_<facing>_sticky}, the
 * nibble {@code facing | (sticky ? 8 : 0)} (PistonHeadBlock lines 181-194).</p>
 *
 * <p><b>The moving block</b> (legacy 36): six FACING values with a STICKY
 * pair — {@code moving_piston_<facing>} / {@code moving_piston_<facing>_sticky},
 * the same nibble (MovingBlock lines 149-158). The moving block is the
 * in-flight carrier: the block the piston is pushing (or the retracting body
 * itself) exists as this type for the two ticks of travel, with the real
 * state riding the piston system's per-position record (the reference's
 * MovingBlockEntity).</p>
 */
public final class PistonBlocks {

    /** The six reference direction names in FACING-id order (down up north south west east). */
    static final String[] DIRECTION_KEYS = {"down", "up", "north", "south", "west", "east"};

    private static final EngineBlockType[][][] BASE = buildBase();
    private static final EngineBlockType[][] HEAD = buildHead();
    private static final EngineBlockType[][] MOVING = buildMoving();

    private PistonBlocks() {
    }

    // ------------------------------------------------------------------
    // The base (legacy 33 / 29)
    // ------------------------------------------------------------------

    /** @return whether the type is any piston base state (plain or sticky). */
    public static boolean isPiston(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        String value = type.identifier().value();
        if (value.startsWith("piston_head_") || value.startsWith("moving_piston_")) {
            return false; // the head and the carrier are not bases
        }
        return value.startsWith("piston_") || value.startsWith("sticky_piston_");
    }

    /** @return whether the base is the sticky kind (legacy 29). */
    public static boolean pistonSticky(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().startsWith("sticky_piston_");
    }

    /** @return whether the base is the extended pair. */
    public static boolean pistonExtended(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().endsWith("_extended");
    }

    /**
     * The base's FACING as the reference's Direction id (0=down 1=up 2=north
     * 3=south 4=west 5=east — the arm's extend direction).
     */
    public static int pistonFacing(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        String rest = value.startsWith("sticky_") ? value.substring("sticky_piston_".length())
                : value.substring("piston_".length());
        String facing = rest.endsWith("_extended")
                ? rest.substring(0, rest.length() - "_extended".length()) : rest;
        return directionIdOf(facing);
    }

    /** The base state for a facing + extended pair + sticky kind. */
    public static EngineBlockType pistonOf(int facing, boolean extended, boolean sticky) {
        return BASE[sticky ? 1 : 0][facing & 7][extended ? 1 : 0];
    }

    /** The same base state with the extended pair swapped. */
    public static EngineBlockType pistonOfState(net.zaminmc.torch.block.BlockType type, boolean extended) {
        return pistonOf(pistonFacing(type), extended, pistonSticky(type));
    }

    // ------------------------------------------------------------------
    // The head (legacy 34) + the moving block (legacy 36)
    // ------------------------------------------------------------------

    /** @return whether the type is any piston head state. */
    public static boolean isPistonHead(net.zaminmc.torch.block.BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("piston_head_");
    }

    /** The head's FACING (the reference Direction id — the side the head sits on). */
    public static int headFacing(net.zaminmc.torch.block.BlockType type) {
        String rest = type.identifier().value().substring("piston_head_".length());
        String facing = rest.endsWith("_sticky")
                ? rest.substring(0, rest.length() - "_sticky".length()) : rest;
        return directionIdOf(facing);
    }

    /** @return whether the head is the sticky pair. */
    public static boolean headSticky(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().endsWith("_sticky");
    }

    /** The head state for a facing + sticky pair. */
    public static EngineBlockType headOf(int facing, boolean sticky) {
        return HEAD[facing & 7][sticky ? 1 : 0];
    }

    /** @return whether the type is any moving piston state (the in-flight carrier). */
    public static boolean isMovingPiston(net.zaminmc.torch.block.BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("moving_piston_");
    }

    /** The moving block's FACING (the reference Direction id). */
    public static int movingFacing(net.zaminmc.torch.block.BlockType type) {
        String rest = type.identifier().value().substring("moving_piston_".length());
        String facing = rest.endsWith("_sticky")
                ? rest.substring(0, rest.length() - "_sticky".length()) : rest;
        return directionIdOf(facing);
    }

    /** @return whether the moving block is the sticky pair (the client's arm texture). */
    public static boolean movingSticky(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().endsWith("_sticky");
    }

    /** The moving block state for a facing + sticky pair. */
    public static EngineBlockType movingOf(int facing, boolean sticky) {
        return MOVING[facing & 7][sticky ? 1 : 0];
    }

    /** @return whether the type belongs to any of the three piston families. */
    public static boolean isPistonFamily(net.zaminmc.torch.block.BlockType type) {
        return isPiston(type) || isPistonHead(type) || isMovingPiston(type);
    }

    /** The reference direction id of a direction key (down up north south west east). */
    static int directionIdOf(String key) {
        for (int i = 0; i < DIRECTION_KEYS.length; i++) {
            if (DIRECTION_KEYS[i].equals(key)) {
                return i;
            }
        }
        return 1;
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    private static EngineBlockType[][][] buildBase() {
        EngineBlockType[][][] all = new EngineBlockType[2][6][2];
        for (int sticky = 0; sticky <= 1; sticky++) {
            for (int facing = 0; facing < 6; facing++) {
                for (int extended = 0; extended <= 1; extended++) {
                    String prefix = sticky == 1 ? "sticky_piston_" : "piston_";
                    all[sticky][facing][extended] = new EngineBlockType(
                            Identifier.parse("minecraft:" + prefix + DIRECTION_KEYS[facing]
                                    + (extended == 1 ? "_extended" : "")),
                            sticky == 1 ? "Sticky Piston" : "Piston",
                            facing | (extended << 3));
                }
            }
        }
        return all;
    }

    private static EngineBlockType[][] buildHead() {
        EngineBlockType[][] all = new EngineBlockType[6][2];
        for (int facing = 0; facing < 6; facing++) {
            for (int sticky = 0; sticky <= 1; sticky++) {
                all[facing][sticky] = new EngineBlockType(
                        Identifier.parse("minecraft:piston_head_" + DIRECTION_KEYS[facing]
                                + (sticky == 1 ? "_sticky" : "")),
                        "Piston Head", facing | (sticky << 3));
            }
        }
        return all;
    }

    private static EngineBlockType[][] buildMoving() {
        EngineBlockType[][] all = new EngineBlockType[6][2];
        for (int facing = 0; facing < 6; facing++) {
            for (int sticky = 0; sticky <= 1; sticky++) {
                all[facing][sticky] = new EngineBlockType(
                        Identifier.parse("minecraft:moving_piston_" + DIRECTION_KEYS[facing]
                                + (sticky == 1 ? "_sticky" : "")),
                        "Piston Moving", facing | (sticky << 3));
            }
        }
        return all;
    }

    /** Registers every piston type into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType[][] kind : BASE) {
            for (EngineBlockType[] facing : kind) {
                for (EngineBlockType type : facing) {
                    builder.register(type);
                }
            }
        }
        for (EngineBlockType[] facing : HEAD) {
            for (EngineBlockType type : facing) {
                builder.register(type);
            }
        }
        for (EngineBlockType[] facing : MOVING) {
            for (EngineBlockType type : facing) {
                builder.register(type);
            }
        }
        return builder;
    }
}
