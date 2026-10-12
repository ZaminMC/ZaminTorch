package net.zaminmc.torch.util;

import net.zaminmc.torch.block.BlockPosition;

/**
 * A double-precision position in world space.
 *
 * <p>Positions are plain value types; validity (NaN/infinity checks) is enforced
 * by the systems that accept them as proposals, not by the type itself, because
 * raw math often produces intermediate invalid values legitimately.</p>
 */
public record Position(double x, double y, double z) {

    public static final Position ZERO = new Position(0.0, 0.0, 0.0);

    public static final double EPSILON = 1.0E-7;

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public BlockPosition toBlockPosition() {
        return new BlockPosition(floor(x), floor(y), floor(z));
    }

    public double distanceSquared(Position other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int floor(double value) {
        int asInt = (int) value;
        return value < asInt ? asInt - 1 : asInt;
    }
}
