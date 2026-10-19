package net.zaminmc.torch.server.interaction;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;

/**
 * Interaction rules of the current compatibility behavior (creative, 1.8-scale
 * reach). Centralized so the values and their reasons live in one place (§593).
 */
final class InteractionRules {

    static final double CREATIVE_REACH_SQ = 6.0 * 6.0;
    /** Historical survival block-interaction distance from the eye. */
    static final double SURVIVAL_REACH_SQ = 4.5 * 4.5;
    /**
     * The dig handler's own reach band (ServerPlayNetworkHandler
     * .handlePlayerDig lines 424-433, 1:1): squared 6.0 — the dig's own
     * band, shorter than the use walk's 64.0 — measured from the feet
     * raised by the reference's own {@code + 1.5} (the dig band's eye,
     * never the 1.62 standing eye) to the target cell's CENTRE
     * (x + 0.5, y + 0.5, z + 0.5). {@code d3 > 36.0} answers NOTHING:
     * no dig opens, no finish breaks, no abort stops.
     */
    static final double DIG_REACH_SQ = 6.0 * 6.0;
    /** The dig band's own eye offset: the reference's {@code + 1.5}, not 1.62. */
    static final double DIG_EYE = 1.5;
    static final double EYE_HEIGHT = 1.62;
    static final double PLAYER_HALF_WIDTH = 0.3;
    static final double PLAYER_HEIGHT = 1.8;

    private InteractionRules() {
    }

    /** @return whether the position is within creative reach of the player's eye. */
    static boolean withinReach(Position playerPosition, BlockPosition target) {
        return withinReachSquared(playerPosition, target, CREATIVE_REACH_SQ);
    }

    /** @return whether the position is within survival reach of the player's eye. */
    static boolean withinSurvivalReach(Position playerPosition, BlockPosition target) {
        return withinReachSquared(playerPosition, target, SURVIVAL_REACH_SQ);
    }

    /**
     * @return whether the target is within the DIG handler's own band
     * (handlePlayerDig lines 424-433, 1:1): from the feet raised by the
     * reference's own {@code + 1.5} to the target cell's centre, squared
     * distance at most 36.0 — the band every dig action (START, FINISH,
     * ABORT — and the creative break's ride through the same case band)
     * sits inside, exactly like the reference's {@code d3 > 36.0} gate
     * refusing before any interactionManager call.
     */
    static boolean withinDigReach(Position playerPosition, BlockPosition target) {
        double dx = playerPosition.x() - (target.x() + 0.5);
        double dy = playerPosition.y() + DIG_EYE - (target.y() + 0.5);
        double dz = playerPosition.z() - (target.z() + 0.5);
        return dx * dx + dy * dy + dz * dz <= DIG_REACH_SQ;
    }

    private static boolean withinReachSquared(Position playerPosition, BlockPosition target,
                                              double reachSquared) {
        double eyeX = playerPosition.x();
        double eyeY = playerPosition.y() + EYE_HEIGHT;
        double eyeZ = playerPosition.z();
        double dx = (target.x() + 0.5) - eyeX;
        double dy = (target.y() + 0.5) - eyeY;
        double dz = (target.z() + 0.5) - eyeZ;
        return dx * dx + dy * dy + dz * dz <= reachSquared;
    }

    /** @return whether the block AABB intersects the player's bounding box. */
    static boolean intersectsPlayer(Position playerPosition, BlockPosition block) {
        double minX = playerPosition.x() - PLAYER_HALF_WIDTH;
        double maxX = playerPosition.x() + PLAYER_HALF_WIDTH;
        double minY = playerPosition.y();
        double maxY = playerPosition.y() + PLAYER_HEIGHT;
        double minZ = playerPosition.z() - PLAYER_HALF_WIDTH;
        double maxZ = playerPosition.z() + PLAYER_HALF_WIDTH;
        return block.x() + 1 > minX && block.x() < maxX
                && block.y() + 1 > minY && block.y() < maxY
                && block.z() + 1 > minZ && block.z() < maxZ;
    }

    /** Historical face encoding translated to offsets; null for invalid faces. */
    static BlockPosition offsetByFace(BlockPosition position, int face) {
        return switch (face) {
            case 0 -> position.offset(0, -1, 0);
            case 1 -> position.offset(0, 1, 0);
            case 2 -> position.offset(0, 0, -1);
            case 3 -> position.offset(0, 0, 1);
            case 4 -> position.offset(-1, 0, 0);
            case 5 -> position.offset(1, 0, 0);
            default -> null;
        };
    }
}
