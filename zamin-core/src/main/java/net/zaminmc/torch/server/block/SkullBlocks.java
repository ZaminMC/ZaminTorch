package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

/**
 * The skull family (Slice 60 — SkullBlock 1:1, block 144): the six
 * facing states over the shared identifier — the floor skull (FACING UP,
 * the base {@code minecraft:skull} name), the four wall facings, and the
 * ceiling mount. The facing rides the metadata nibble exactly the
 * reference's {@code getMetadataFromState} ({@code facing | nodrop << 3})
 * — the NODROP bit is server-only bookkeeping and never lands on a
 * placed skull's wire row. The TYPE (skeleton 0 .. dragon 5) and the
 * floor rotation live in the tile, not the block — the manager's store
 * carries them and the wire's 0x35 packet publishes them.
 */
public final class SkullBlocks {

    private SkullBlocks() {
    }

    /** @return whether the identifier names a skull state. */
    public static boolean isSkull(Identifier identifier) {
        String value = identifier.value();
        return value.equals("skull") || value.startsWith("skull_");
    }

    /**
     * The wall/floor facing of the state (the reference's FACING property,
     * the metadata nibble 0-5): 1 the floor mount, 2-5 the wall facings,
     * 0 the ceiling.
     */
    public static int facingOf(net.zaminmc.torch.block.BlockType type) {
        return switch (type.identifier().value()) {
            case "skull" -> 1;            // UP: the floor skull
            case "skull_north" -> 2;
            case "skull_south" -> 3;
            case "skull_west" -> 4;
            case "skull_east" -> 5;
            default -> 0;                 // skull_down: the ceiling mount
        };
    }

    /** The state for a placement face (the item's own rule: FACING = face). */
    public static net.zaminmc.torch.block.BlockType forState(int face) {
        return switch (face) {
            case 2 -> BuiltinBlocks.SKULL_NORTH;
            case 3 -> BuiltinBlocks.SKULL_SOUTH;
            case 4 -> BuiltinBlocks.SKULL_WEST;
            case 5 -> BuiltinBlocks.SKULL_EAST;
            case 0 -> BuiltinBlocks.SKULL_DOWN;
            default -> BuiltinBlocks.SKULL;
        };
    }
}
