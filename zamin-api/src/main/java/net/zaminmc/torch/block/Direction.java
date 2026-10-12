package net.zaminmc.torch.block;

/**
 * The six block facings — the reference's {@code Direction} ids, kept in
 * the historical ordinal order (0 down … 5 east) so wire encodings and
 * engine tables address them by {@link #id()} without translation.
 *
 * <p>Safe from any thread (a frozen value type).</p>
 */
public enum Direction {

    DOWN(0, 0, -1, 0),
    UP(1, 0, 1, 0),
    NORTH(2, 0, 0, -1),
    SOUTH(3, 0, 0, 1),
    WEST(4, -1, 0, 0),
    EAST(5, 1, 0, 0);

    private final int id;
    private final int dx;
    private final int dy;
    private final int dz;

    Direction(int id, int dx, int dy, int dz) {
        this.id = id;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    /** The historical wire id (the engine's facing nibble value). */
    public int id() {
        return id;
    }

    /** The facing's unit step along x. */
    public int offsetX() {
        return dx;
    }

    /** The facing's unit step along y. */
    public int offsetY() {
        return dy;
    }

    /** The facing's unit step along z. */
    public int offsetZ() {
        return dz;
    }

    /** The opposite facing (a dispenser pushes east, its cell lies east). */
    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /** @return the facing for the historical id, or null when out of range. */
    public static Direction byId(int id) {
        if (id < 0 || id >= values().length) {
            return null;
        }
        return values()[id];
    }
}
