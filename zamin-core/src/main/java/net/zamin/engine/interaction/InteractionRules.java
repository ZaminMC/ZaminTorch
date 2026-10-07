package net.zamin.engine.interaction;

import net.zamin.api.BlockPosition;
import net.zamin.api.Position;

/**
 * Interaction rules of the current compatibility behavior (creative, 1.8-scale
 * reach). Centralized so the values and their reasons live in one place (§593).
 */
final class InteractionRules {

    static final double CREATIVE_REACH_SQ = 6.0 * 6.0;
    static final double EYE_HEIGHT = 1.62;
    static final double PLAYER_HALF_WIDTH = 0.3;
    static final double PLAYER_HEIGHT = 1.8;

    private InteractionRules() {
    }

    /** @return whether the position is within creative reach of the player's eye. */
    static boolean withinReach(Position playerPosition, BlockPosition target) {
        double eyeX = playerPosition.x();
        double eyeY = playerPosition.y() + EYE_HEIGHT;
        double eyeZ = playerPosition.z();
        double dx = (target.x() + 0.5) - eyeX;
        double dy = (target.y() + 0.5) - eyeY;
        double dz = (target.z() + 0.5) - eyeZ;
        return dx * dx + dy * dy + dz * dz <= CREATIVE_REACH_SQ;
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
