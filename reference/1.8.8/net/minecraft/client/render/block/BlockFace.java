package net.minecraft.client.render.block;

import net.minecraft.util.math.Direction;

public enum BlockFace {
    DOWN(
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MAX_Z),
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MIN_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MIN_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MAX_Z)
    ),
    UP(
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MIN_Z),
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MAX_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MAX_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MIN_Z)
    ),
    NORTH(
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MIN_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MIN_Z),
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MIN_Z),
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MIN_Z)
    ),
    SOUTH(
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MAX_Z),
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MAX_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MAX_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MAX_Z)
    ),
    WEST(
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MIN_Z),
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MIN_Z),
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MAX_Z),
        new BlockFace.Vertex(BlockFace.Constants.MIN_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MAX_Z)
    ),
    EAST(
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MAX_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MAX_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MIN_Y, BlockFace.Constants.MIN_Z),
        new BlockFace.Vertex(BlockFace.Constants.MAX_X, BlockFace.Constants.MAX_Y, BlockFace.Constants.MIN_Z)
    );

    private static final BlockFace[] ALL = new BlockFace[6];
    private final BlockFace.Vertex[] vertices;

    public static BlockFace byDirection(Direction dir) {
        return ALL[dir.getId()];
    }

    BlockFace(BlockFace.Vertex... vertices) {
        this.vertices = vertices;
    }

    public BlockFace.Vertex getVertex(int index) {
        return this.vertices[index];
    }

    static {
        ALL[BlockFace.Constants.MIN_Y] = DOWN;
        ALL[BlockFace.Constants.MAX_Y] = UP;
        ALL[BlockFace.Constants.MIN_Z] = NORTH;
        ALL[BlockFace.Constants.MAX_Z] = SOUTH;
        ALL[BlockFace.Constants.MIN_X] = WEST;
        ALL[BlockFace.Constants.MAX_X] = EAST;
    }

    public static final class Constants {
        public static final int MAX_Z = Direction.SOUTH.getId();
        public static final int MAX_Y = Direction.UP.getId();
        public static final int MAX_X = Direction.EAST.getId();
        public static final int MIN_Z = Direction.NORTH.getId();
        public static final int MIN_Y = Direction.DOWN.getId();
        public static final int MIN_X = Direction.WEST.getId();
    }

    public static class Vertex {
        public final int x;
        public final int y;
        public final int z;

        private Vertex(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
