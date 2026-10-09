package net.minecraft.util.math;

import com.google.common.collect.AbstractIterator;
import java.util.Iterator;
import net.minecraft.entity.Entity;

public class BlockPos extends Vec3i {
    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);
    private static final int OFFSET_X = 1 + MathHelper.log2(MathHelper.smallestEncompassingPowerOfTwo(30000000));
    private static final int OFFSET_Z = OFFSET_X;
    private static final int OFFSET_Y = 64 - OFFSET_X - OFFSET_Z;
    private static final int SIZE_Y = 0 + OFFSET_Z;
    private static final int SIZE_X = SIZE_Y + OFFSET_Y;
    private static final long MASK_X = (1L << OFFSET_X) - 1L;
    private static final long MASK_Y = (1L << OFFSET_Y) - 1L;
    private static final long MASK_Z = (1L << OFFSET_Z) - 1L;

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPos(double x, double y, double z) {
        super(x, y, z);
    }

    public BlockPos(Entity entity) {
        this(entity.x, entity.y, entity.z);
    }

    public BlockPos(Vec3d vec) {
        this(vec.x, vec.y, vec.z);
    }

    public BlockPos(Vec3i vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    public BlockPos add(double x, double y, double z) {
        return x == 0.0 && y == 0.0 && z == 0.0 ? this : new BlockPos(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public BlockPos add(int x, int y, int z) {
        return x == 0 && y == 0 && z == 0 ? this : new BlockPos(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public BlockPos add(Vec3i vec) {
        return vec.getX() == 0 && vec.getY() == 0 && vec.getZ() == 0
            ? this
            : new BlockPos(this.getX() + vec.getX(), this.getY() + vec.getY(), this.getZ() + vec.getZ());
    }

    public BlockPos subtract(Vec3i vec) {
        return vec.getX() == 0 && vec.getY() == 0 && vec.getZ() == 0
            ? this
            : new BlockPos(this.getX() - vec.getX(), this.getY() - vec.getY(), this.getZ() - vec.getZ());
    }

    public BlockPos up() {
        return this.up(1);
    }

    public BlockPos up(int d) {
        return this.offset(Direction.UP, d);
    }

    public BlockPos down() {
        return this.down(1);
    }

    public BlockPos down(int d) {
        return this.offset(Direction.DOWN, d);
    }

    public BlockPos north() {
        return this.north(1);
    }

    public BlockPos north(int d) {
        return this.offset(Direction.NORTH, d);
    }

    public BlockPos south() {
        return this.south(1);
    }

    public BlockPos south(int d) {
        return this.offset(Direction.SOUTH, d);
    }

    public BlockPos west() {
        return this.west(1);
    }

    public BlockPos west(int d) {
        return this.offset(Direction.WEST, d);
    }

    public BlockPos east() {
        return this.east(1);
    }

    public BlockPos east(int d) {
        return this.offset(Direction.EAST, d);
    }

    public BlockPos offset(Direction dir) {
        return this.offset(dir, 1);
    }

    public BlockPos offset(Direction dir, int d) {
        return d == 0 ? this : new BlockPos(this.getX() + dir.getOffsetX() * d, this.getY() + dir.getOffsetY() * d, this.getZ() + dir.getOffsetZ() * d);
    }

    public BlockPos cross(Vec3i vec) {
        return new BlockPos(
            this.getY() * vec.getZ() - this.getZ() * vec.getY(),
            this.getZ() * vec.getX() - this.getX() * vec.getZ(),
            this.getX() * vec.getY() - this.getY() * vec.getX()
        );
    }

    public long toLong() {
        return (this.getX() & MASK_X) << SIZE_X | (this.getY() & MASK_Y) << SIZE_Y | (this.getZ() & MASK_Z) << 0;
    }

    public static BlockPos fromLong(long pos) {
        int i = (int)(pos << 64 - SIZE_X - OFFSET_X >> 64 - OFFSET_X);
        int j = (int)(pos << 64 - SIZE_Y - OFFSET_Y >> 64 - OFFSET_Y);
        int k = (int)(pos << 64 - OFFSET_Z >> 64 - OFFSET_Z);
        return new BlockPos(i, j, k);
    }

    public static Iterable<BlockPos> iterateRegion(BlockPos pos1, BlockPos pos2) {
        final BlockPos blockpos = new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        final BlockPos blockpos1 = new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
        return new Iterable<BlockPos>() {
            @Override
            public Iterator<BlockPos> iterator() {
                return new AbstractIterator<BlockPos>() {
                    private BlockPos pos = null;

                    protected BlockPos computeNext() {
                        if (this.pos == null) {
                            this.pos = blockpos;
                            return this.pos;
                        }

                        if (this.pos.equals(blockpos1)) {
                            return this.endOfData();
                        }

                        int i = this.pos.getX();
                        int j = this.pos.getY();
                        int k = this.pos.getZ();
                        if (i < blockpos1.getX()) {
                            i++;
                        } else if (j < blockpos1.getY()) {
                            i = blockpos.getX();
                            j++;
                        } else if (k < blockpos1.getZ()) {
                            i = blockpos.getX();
                            j = blockpos.getY();
                            k++;
                        }

                        this.pos = new BlockPos(i, j, k);
                        return this.pos;
                    }
                };
            }
        };
    }

    public static Iterable<BlockPos.Mutable> iterateRegionMutable(BlockPos pos1, BlockPos pos2) {
        final BlockPos blockpos = new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        final BlockPos blockpos1 = new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
        return new Iterable<BlockPos.Mutable>() {
            @Override
            public Iterator<BlockPos.Mutable> iterator() {
                return new AbstractIterator<BlockPos.Mutable>() {
                    private BlockPos.Mutable pos = null;

                    protected BlockPos.Mutable computeNext() {
                        if (this.pos == null) {
                            this.pos = new BlockPos.Mutable(blockpos.getX(), blockpos.getY(), blockpos.getZ());
                            return this.pos;
                        }

                        if (this.pos.equals(blockpos1)) {
                            return this.endOfData();
                        }

                        int i = this.pos.getX();
                        int j = this.pos.getY();
                        int k = this.pos.getZ();
                        if (i < blockpos1.getX()) {
                            i++;
                        } else if (j < blockpos1.getY()) {
                            i = blockpos.getX();
                            j++;
                        } else if (k < blockpos1.getZ()) {
                            i = blockpos.getX();
                            j = blockpos.getY();
                            k++;
                        }

                        this.pos.posX = i;
                        this.pos.posY = j;
                        this.pos.posZ = k;
                        return this.pos;
                    }
                };
            }
        };
    }

    public static final class Mutable extends BlockPos {
        private int posX;
        private int posY;
        private int posZ;

        public Mutable() {
            this(0, 0, 0);
        }

        public Mutable(int x, int y, int z) {
            super(0, 0, 0);
            this.posX = x;
            this.posY = y;
            this.posZ = z;
        }

        @Override
        public int getX() {
            return this.posX;
        }

        @Override
        public int getY() {
            return this.posY;
        }

        @Override
        public int getZ() {
            return this.posZ;
        }

        public BlockPos.Mutable set(int x, int y, int z) {
            this.posX = x;
            this.posY = y;
            this.posZ = z;
            return this;
        }
    }
}
