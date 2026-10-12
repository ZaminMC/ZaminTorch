package net.zaminmc.torch.server.world.structure;

import java.util.Random;

/**
 * The horizontal facing (Slice 12a — the reference's {@code Direction}
 * subset the structure pieces use): the four horizontals with the
 * reference's own horizontal ids (SOUTH 0, WEST 1, NORTH 2, EAST 3 —
 * Direction.java lines 14-17's third ctor field) and the opposite
 * mapping.
 *
 * <p>Slice 12b adds the reference's own Y-rotations (clockwiseY /
 * counterClockwiseY — the lamp post's torch facings and the door's
 * placement turn) and the horizontal pick (Direction.Plane.HORIZONTAL's
 * own array order NORTH/EAST/SOUTH/WEST, the well's random facing).</p>
 */
public enum StructureFacing {
    SOUTH(0),
    WEST(1),
    NORTH(2),
    EAST(3);

    private final int horizontalId;

    StructureFacing(int horizontalId) {
        this.horizontalId = horizontalId;
    }

    /** The reference's getIdHorizontal. */
    public int horizontalId() {
        return horizontalId;
    }

    /** The reference's byIdHorizontal (unknown ids answer NORTH). */
    public static StructureFacing byHorizontalId(int id) {
        for (StructureFacing facing : values()) {
            if (facing.horizontalId == id) {
                return facing;
            }
        }
        return NORTH;
    }

    /** The reference's getOpposite. */
    public StructureFacing opposite() {
        return switch (this) {
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case NORTH -> SOUTH;
            case EAST -> WEST;
        };
    }

    /** The reference's clockwiseY (N→E→S→W→N). */
    public StructureFacing clockwiseY() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
        };
    }

    /** The reference's counterClockwiseY (N→W→S→E→N). */
    public StructureFacing counterClockwiseY() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
        };
    }

    /**
     * The reference's Direction.Plane.HORIZONTAL.pick (the plane's own
     * array order NORTH/EAST/SOUTH/WEST — the well's random facing draw).
     */
    public static StructureFacing pickHorizontal(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> NORTH;
            case 1 -> EAST;
            case 2 -> SOUTH;
            default -> WEST;
        };
    }
}
