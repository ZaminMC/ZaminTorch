package net.minecraft.block.pattern;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

public class BlockPattern {
    private final Predicate<BlockPointer>[][][] pattern;
    private final int depth;
    private final int width;
    private final int height;

    public BlockPattern(Predicate<BlockPointer>[][][] pattern) {
        this.pattern = pattern;
        this.depth = pattern.length;
        if (this.depth > 0) {
            this.width = pattern[0].length;
            if (this.width > 0) {
                this.height = pattern[0][0].length;
            } else {
                this.height = 0;
            }
        } else {
            this.width = 0;
            this.height = 0;
        }
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    private BlockPattern.Match matches(BlockPos pos, Direction forward, Direction up, LoadingCache<BlockPos, BlockPointer> cache) {
        for (int i = 0; i < this.height; i++) {
            for (int j = 0; j < this.width; j++) {
                for (int k = 0; k < this.depth; k++) {
                    if (!this.pattern[k][j][i].apply(cache.getUnchecked(transform(pos, forward, up, i, j, k)))) {
                        return null;
                    }
                }
            }
        }

        return new BlockPattern.Match(pos, forward, up, cache, this.height, this.width, this.depth);
    }

    public BlockPattern.Match find(World world, BlockPos pos) {
        LoadingCache<BlockPos, BlockPointer> loadingcache = createBlockCache(world, false);
        int i = Math.max(Math.max(this.height, this.width), this.depth);

        for (BlockPos blockpos : BlockPos.iterateRegion(pos, pos.add(i - 1, i - 1, i - 1))) {
            for (Direction direction : Direction.values()) {
                for (Direction direction1 : Direction.values()) {
                    if (direction1 != direction && direction1 != direction.getOpposite()) {
                        BlockPattern.Match blockpattern$match = this.matches(blockpos, direction, direction1, loadingcache);
                        if (blockpattern$match != null) {
                            return blockpattern$match;
                        }
                    }
                }
            }
        }

        return null;
    }

    public static LoadingCache<BlockPos, BlockPointer> createBlockCache(World world, boolean loadChunks) {
        return CacheBuilder.newBuilder().build(new BlockPattern.BlockCacheLoader(world, loadChunks));
    }

    protected static BlockPos transform(BlockPos pos, Direction forward, Direction up, int dx, int dy, int dz) {
        if (forward != up && forward != up.getOpposite()) {
            Vec3i vec3i = new Vec3i(forward.getOffsetX(), forward.getOffsetY(), forward.getOffsetZ());
            Vec3i vec3i1 = new Vec3i(up.getOffsetX(), up.getOffsetY(), up.getOffsetZ());
            Vec3i vec3i2 = vec3i.cross(vec3i1);
            return pos.add(
                vec3i1.getX() * -dy + vec3i2.getX() * dx + vec3i.getX() * dz,
                vec3i1.getY() * -dy + vec3i2.getY() * dx + vec3i.getY() * dz,
                vec3i1.getZ() * -dy + vec3i2.getZ() * dx + vec3i.getZ() * dz
            );
        } else {
            throw new IllegalArgumentException("Invalid forwards & up combination");
        }
    }

    static class BlockCacheLoader extends CacheLoader<BlockPos, BlockPointer> {
        private final World world;
        private final boolean loadChunks;

        public BlockCacheLoader(World world, boolean loadChunks) {
            this.world = world;
            this.loadChunks = loadChunks;
        }

        public BlockPointer load(BlockPos blockPos) throws Exception {
            return new BlockPointer(this.world, blockPos, this.loadChunks);
        }
    }

    public static class Match {
        private final BlockPos topLeftFront;
        private final Direction forward;
        private final Direction up;
        private final LoadingCache<BlockPos, BlockPointer> cache;
        private final int width;
        private final int height;
        private final int depth;

        public Match(BlockPos topLeftFront, Direction forward, Direction up, LoadingCache<BlockPos, BlockPointer> cache, int width, int height, int depth) {
            this.topLeftFront = topLeftFront;
            this.forward = forward;
            this.up = up;
            this.cache = cache;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        public BlockPos getTopLeftFront() {
            return this.topLeftFront;
        }

        public Direction getForward() {
            return this.forward;
        }

        public Direction getUp() {
            return this.up;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }

        public BlockPointer getBlock(int dx, int dy, int dz) {
            return this.cache.getUnchecked(BlockPattern.transform(this.topLeftFront, this.getForward(), this.getUp(), dx, dy, dz));
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("up", this.up).add("forwards", this.forward).add("frontTopLeft", this.topLeftFront).toString();
        }
    }
}
