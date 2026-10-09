package net.minecraft.util.math;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.util.StringSerializable;

public enum Direction implements StringSerializable {
    DOWN(0, 1, -1, "down", Direction.AxisDirection.NEGATIVE, Direction.Axis.Y, new Vec3i(0, -1, 0)),
    UP(1, 0, -1, "up", Direction.AxisDirection.POSITIVE, Direction.Axis.Y, new Vec3i(0, 1, 0)),
    NORTH(2, 3, 2, "north", Direction.AxisDirection.NEGATIVE, Direction.Axis.Z, new Vec3i(0, 0, -1)),
    SOUTH(3, 2, 0, "south", Direction.AxisDirection.POSITIVE, Direction.Axis.Z, new Vec3i(0, 0, 1)),
    WEST(4, 5, 1, "west", Direction.AxisDirection.NEGATIVE, Direction.Axis.X, new Vec3i(-1, 0, 0)),
    EAST(5, 4, 3, "east", Direction.AxisDirection.POSITIVE, Direction.Axis.X, new Vec3i(1, 0, 0));

    private final int id;
    private final int opposite;
    private final int idHorizontal;
    private final String key;
    private final Direction.Axis axis;
    private final Direction.AxisDirection axisDir;
    private final Vec3i normal;
    private static final Direction[] ALL = new Direction[6];
    private static final Direction[] BY_ID_HORIZONTAL = new Direction[4];
    private static final Map<String, Direction> BY_KEY = Maps.newHashMap();

    Direction(int id, int opposite, int idHorizontal, String key, Direction.AxisDirection axisDir, Direction.Axis axis, Vec3i normal) {
        this.id = id;
        this.idHorizontal = idHorizontal;
        this.opposite = opposite;
        this.key = key;
        this.axis = axis;
        this.axisDir = axisDir;
        this.normal = normal;
    }

    public int getId() {
        return this.id;
    }

    public int getIdHorizontal() {
        return this.idHorizontal;
    }

    public Direction.AxisDirection getAxisDirection() {
        return this.axisDir;
    }

    public Direction getOpposite() {
        return byId(this.opposite);
    }

    public Direction clockwise(Direction.Axis axis) {
        switch (axis) {
            case X:
                if (this != WEST && this != EAST) {
                    return this.clockwiseX();
                }

                return this;
            case Y:
                if (this != UP && this != DOWN) {
                    return this.clockwiseY();
                }

                return this;
            case Z:
                if (this != NORTH && this != SOUTH) {
                    return this.clockwiseZ();
                }

                return this;
            default:
                throw new IllegalStateException("Unable to get CW facing for axis " + axis);
        }
    }

    public Direction clockwiseY() {
        switch (this) {
            case NORTH:
                return EAST;
            case EAST:
                return SOUTH;
            case SOUTH:
                return WEST;
            case WEST:
                return NORTH;
            default:
                throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
        }
    }

    private Direction clockwiseX() {
        switch (this) {
            case NORTH:
                return DOWN;
            case EAST:
            case WEST:
            default:
                throw new IllegalStateException("Unable to get X-rotated facing of " + this);
            case SOUTH:
                return UP;
            case UP:
                return NORTH;
            case DOWN:
                return SOUTH;
        }
    }

    private Direction clockwiseZ() {
        switch (this) {
            case EAST:
                return DOWN;
            case SOUTH:
            default:
                throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
            case WEST:
                return UP;
            case UP:
                return EAST;
            case DOWN:
                return WEST;
        }
    }

    public Direction counterClockwiseY() {
        switch (this) {
            case NORTH:
                return WEST;
            case EAST:
                return NORTH;
            case SOUTH:
                return EAST;
            case WEST:
                return SOUTH;
            default:
                throw new IllegalStateException("Unable to get CCW facing of " + this);
        }
    }

    public int getOffsetX() {
        return this.axis == Direction.Axis.X ? this.axisDir.getOffset() : 0;
    }

    public int getOffsetY() {
        return this.axis == Direction.Axis.Y ? this.axisDir.getOffset() : 0;
    }

    public int getOffsetZ() {
        return this.axis == Direction.Axis.Z ? this.axisDir.getOffset() : 0;
    }

    public String getKey() {
        return this.key;
    }

    public Direction.Axis getAxis() {
        return this.axis;
    }

    public static Direction byKey(String key) {
        return key == null ? null : BY_KEY.get(key.toLowerCase());
    }

    public static Direction byId(int id) {
        return ALL[MathHelper.abs(id % ALL.length)];
    }

    public static Direction byIdHorizontal(int id) {
        return BY_ID_HORIZONTAL[MathHelper.abs(id % BY_ID_HORIZONTAL.length)];
    }

    public static Direction byRotation(double rotation) {
        return byIdHorizontal(MathHelper.floor(rotation / 90.0 + 0.5) & 3);
    }

    public static Direction pick(Random random) {
        return values()[random.nextInt(values().length)];
    }

    public static Direction getNearest(float dx, float dy, float dz) {
        Direction direction = NORTH;
        float f = Float.MIN_VALUE;

        for (Direction direction1 : values()) {
            float f1 = dx * direction1.normal.getX() + dy * direction1.normal.getY() + dz * direction1.normal.getZ();
            if (f1 > f) {
                f = f1;
                direction = direction1;
            }
        }

        return direction;
    }

    @Override
    public String toString() {
        return this.key;
    }

    @Override
    public String serializeToString() {
        return this.key;
    }

    public static Direction of(Direction.AxisDirection axisDir, Direction.Axis axis) {
        for (Direction direction : values()) {
            if (direction.getAxisDirection() == axisDir && direction.getAxis() == axis) {
                return direction;
            }
        }

        throw new IllegalArgumentException("No such direction: " + axisDir + " " + axis);
    }

    public Vec3i getNormal() {
        return this.normal;
    }

    static {
        for (Direction direction : values()) {
            ALL[direction.id] = direction;
            if (direction.getAxis().isHorizontal()) {
                BY_ID_HORIZONTAL[direction.idHorizontal] = direction;
            }

            BY_KEY.put(direction.getKey().toLowerCase(), direction);
        }
    }

    public enum Axis implements Predicate<Direction>, StringSerializable {
        X("x", Direction.Plane.HORIZONTAL),
        Y("y", Direction.Plane.VERTICAL),
        Z("z", Direction.Plane.HORIZONTAL);

        private static final Map<String, Direction.Axis> BY_NAME = Maps.newHashMap();
        private final String name;
        private final Direction.Plane plane;

        Axis(String name, Direction.Plane plane) {
            this.name = name;
            this.plane = plane;
        }

        public static Direction.Axis byName(String name) {
            return name == null ? null : BY_NAME.get(name.toLowerCase());
        }

        public String getName() {
            return this.name;
        }

        public boolean isVertical() {
            return this.plane == Direction.Plane.VERTICAL;
        }

        public boolean isHorizontal() {
            return this.plane == Direction.Plane.HORIZONTAL;
        }

        @Override
        public String toString() {
            return this.name;
        }

        public boolean apply(Direction direction) {
            return direction != null && direction.getAxis() == this;
        }

        public Direction.Plane getPlane() {
            return this.plane;
        }

        @Override
        public String serializeToString() {
            return this.name;
        }

        static {
            for (Direction.Axis direction$axis : values()) {
                BY_NAME.put(direction$axis.getName().toLowerCase(), direction$axis);
            }
        }
    }

    public enum AxisDirection {
        POSITIVE(1, "Towards positive"),
        NEGATIVE(-1, "Towards negative");

        private final int offset;
        private final String description;

        AxisDirection(int offset, String description) {
            this.offset = offset;
            this.description = description;
        }

        public int getOffset() {
            return this.offset;
        }

        @Override
        public String toString() {
            return this.description;
        }
    }

    public enum Plane implements Predicate<Direction>, Iterable<Direction> {
        HORIZONTAL,
        VERTICAL;

        public Direction[] get() {
            switch (this) {
                case HORIZONTAL:
                    return new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                case VERTICAL:
                    return new Direction[]{Direction.UP, Direction.DOWN};
                default:
                    throw new Error("Someone's been tampering with the universe!");
            }
        }

        public Direction pick(Random random) {
            Direction[] adirection = this.get();
            return adirection[random.nextInt(adirection.length)];
        }

        public boolean apply(Direction direction) {
            return direction != null && direction.getAxis().getPlane() == this;
        }

        @Override
        public Iterator<Direction> iterator() {
            return Iterators.forArray(this.get());
        }
    }
}
