package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

/**
 * The banner family (BannerBlock 1:1, blocks 176 standing / 177 wall):
 * the sign family's own shape — the standing pair carries the reference's
 * ROTATION 0-15 band WHOLE (BannerBlock.Standing's own IntegerProperty,
 * getStateFromMetadata passing the wire nibble straight through — the
 * compass-16 states the sign family's twelfth-pass restoration registered,
 * the cardinals at 0/4/8/12 = S/W/N/E keeping their historical names) and
 * the wall pair's four FACING states (the metadata 2-5 naming the direction
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
     * reference's own band WHOLE — every 22.5-degree step its own state
     * (BannerBlock.Standing's ROTATION 0-15, the compass-16 the sign
     * family's twelfth-pass restoration registered; the pre-fix engine
     * folded the band to the four cardinals and every diagonal look
     * rendered the banner at the wrong angle).
     */
    public static net.zaminmc.torch.block.BlockType standingFor(int rotation) {
        return switch (rotation & 15) {
            case 1 -> BuiltinBlocks.STANDING_BANNER_SSW;
            case 2 -> BuiltinBlocks.STANDING_BANNER_SW;
            case 3 -> BuiltinBlocks.STANDING_BANNER_WSW;
            case 4 -> BuiltinBlocks.STANDING_BANNER_WEST;
            case 5 -> BuiltinBlocks.STANDING_BANNER_WNW;
            case 6 -> BuiltinBlocks.STANDING_BANNER_NW;
            case 7 -> BuiltinBlocks.STANDING_BANNER_NNW;
            case 8 -> BuiltinBlocks.STANDING_BANNER_NORTH;
            case 9 -> BuiltinBlocks.STANDING_BANNER_NNE;
            case 10 -> BuiltinBlocks.STANDING_BANNER_NE;
            case 11 -> BuiltinBlocks.STANDING_BANNER_ENE;
            case 12 -> BuiltinBlocks.STANDING_BANNER_EAST;
            case 13 -> BuiltinBlocks.STANDING_BANNER_ESE;
            case 14 -> BuiltinBlocks.STANDING_BANNER_SE;
            case 15 -> BuiltinBlocks.STANDING_BANNER_SSE;
            default -> BuiltinBlocks.STANDING_BANNER;   // 0: south
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
