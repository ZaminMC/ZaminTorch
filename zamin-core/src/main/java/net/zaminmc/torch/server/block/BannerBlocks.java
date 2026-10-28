package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

/**
 * The banner family (BannerBlock 1:1, blocks 176 standing / 177 wall):
 * the sign family's own shape — the standing pair flattened to the four
 * cardinal rotations (the engine's sign model over the reference's
 * ROTATION 0-15 band; the cardinals sit at 0/4/8/12 = S/W/N/E) and the
 * wall pair's four FACING states (the metadata 2-5 naming the direction
 * the banner faces — away from the wall it hangs on). The base color and
 * the pattern rows live in the tile, not the block — the banner manager
 * carries them and the wire's 0x35 action-6 packet publishes them.
 */
public final class BannerBlocks {

    private BannerBlocks() {
    }

    /** @return whether the identifier names a banner state (either pair). */
    public static boolean isBanner(Identifier identifier) {
        String value = identifier.value();
        return value.equals("standing_banner") || value.startsWith("standing_banner_")
                || value.equals("wall_banner") || value.startsWith("wall_banner_");
    }

    /** @return whether the identifier names a standing banner state. */
    public static boolean isStanding(Identifier identifier) {
        String value = identifier.value();
        return value.equals("standing_banner") || value.startsWith("standing_banner_");
    }

    /**
     * The standing state for a placement rotation (BannerItem.useOn line
     * 51's {@code floor((yaw + 180) * 16 / 360 + 0.5) & 15} result): the
     * engine's sign model keeps the four cardinals alone — the 0-15 band
     * folds to the nearest quarter (0/4/8/12).
     */
    public static net.zaminmc.torch.block.BlockType standingFor(int rotation) {
        return switch (((rotation + 2) >> 2) & 3) {
            case 1 -> BuiltinBlocks.STANDING_BANNER_WEST;   // 2..5 -> 4
            case 2 -> BuiltinBlocks.STANDING_BANNER_NORTH;  // 6..9 -> 8
            case 3 -> BuiltinBlocks.STANDING_BANNER_EAST;   // 10..13 -> 12
            default -> BuiltinBlocks.STANDING_BANNER;       // 0..1, 14..15 -> 0
        };
    }

    /** The wall state for a placement face (the face the banner hangs on). */
    public static net.zaminmc.torch.block.BlockType wallFor(int face) {
        return switch (face) {
            case 2 -> BuiltinBlocks.WALL_BANNER_NORTH;
            case 3 -> BuiltinBlocks.WALL_BANNER_SOUTH;
            case 4 -> BuiltinBlocks.WALL_BANNER_WEST;
            default -> BuiltinBlocks.WALL_BANNER_EAST;
        };
    }

    /**
     * The wall state's FACING (the metadata 2-5) — the direction the
     * banner faces, i.e. the neighbor cell its support law consults is
     * the OPPOSITE of this. The north state carries the family's bare
     * identifier (the sign family's own convention: WALL_SIGN_NORTH is
     * {@code minecraft:wall_sign}).
     */
    public static int facingOf(net.zaminmc.torch.block.BlockType type) {
        return switch (type.identifier().value()) {
            case "wall_banner_south" -> 3;
            case "wall_banner_west" -> 4;
            case "wall_banner_east" -> 5;
            default -> 2; // wall_banner: the north state's bare name
        };
    }
}
