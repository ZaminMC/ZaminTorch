package net.minecraft.server.world;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.PortalBlock;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.Long2ObjectHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class PortalForcer {
    private final ServerWorld world;
    private final Random random;
    private final Long2ObjectHashMap<PortalForcer.PortalPos> portalCache = new Long2ObjectHashMap<>();
    private final List<Long> portalCacheKeys = Lists.newArrayList();

    public PortalForcer(ServerWorld world) {
        this.world = world;
        this.random = new Random(world.getSeed());
    }

    public void onDimensionChanged(Entity entity, float yaw) {
        if (this.world.dimension.getId() != 1) {
            if (!this.findNetherPortal(entity, yaw)) {
                this.generateNetherPortal(entity);
                this.findNetherPortal(entity, yaw);
            }
        } else {
            int i = MathHelper.floor(entity.x);
            int j = MathHelper.floor(entity.y) - 1;
            int k = MathHelper.floor(entity.z);
            int l = 1;
            int i1 = 0;

            for (int j1 = -2; j1 <= 2; j1++) {
                for (int k1 = -2; k1 <= 2; k1++) {
                    for (int l1 = -1; l1 < 3; l1++) {
                        int i2 = i + k1 * l + j1 * i1;
                        int j2 = j + l1;
                        int k2 = k + k1 * i1 - j1 * l;
                        boolean flag = l1 < 0;
                        this.world.setBlockState(new BlockPos(i2, j2, k2), flag ? Blocks.OBSIDIAN.defaultState() : Blocks.AIR.defaultState());
                    }
                }
            }

            entity.setPositionAndAngles(i, j, k, entity.yaw, 0.0F);
            entity.velocityX = entity.velocityY = entity.velocityZ = 0.0;
        }
    }

    public boolean findNetherPortal(Entity entity, float yaw) {
        int i = 128;
        double d0 = -1.0;
        int j = MathHelper.floor(entity.x);
        int k = MathHelper.floor(entity.z);
        boolean flag = true;
        BlockPos blockpos = BlockPos.ORIGIN;
        long l = ChunkPos.toLong(j, k);
        if (this.portalCache.contains(l)) {
            PortalForcer.PortalPos portalforcer$portalpos = this.portalCache.get(l);
            d0 = 0.0;
            blockpos = portalforcer$portalpos;
            portalforcer$portalpos.lastUseTime = this.world.getTime();
            flag = false;
        } else {
            BlockPos blockpos3 = new BlockPos(entity);

            for (int i1 = -128; i1 <= 128; i1++) {
                for (int j1 = -128; j1 <= 128; j1++) {
                    BlockPos blockpos1 = blockpos3.add(i1, this.world.getDimensionHeight() - 1 - blockpos3.getY(), j1);

                    while (blockpos1.getY() >= 0) {
                        BlockPos blockpos2 = blockpos1.down();
                        if (this.world.getBlockState(blockpos1).getBlock() == Blocks.NETHER_PORTAL) {
                            while (this.world.getBlockState(blockpos2 = blockpos1.down()).getBlock() == Blocks.NETHER_PORTAL) {
                                blockpos1 = blockpos2;
                            }

                            double d1 = blockpos1.squaredDistanceTo(blockpos3);
                            if (d0 < 0.0 || d1 < d0) {
                                d0 = d1;
                                blockpos = blockpos1;
                            }
                        }

                        blockpos1 = blockpos2;
                    }
                }
            }
        }

        if (d0 >= 0.0) {
            if (flag) {
                this.portalCache.put(l, new PortalForcer.PortalPos(blockpos, this.world.getTime()));
                this.portalCacheKeys.add(l);
            }

            double d5 = blockpos.getX() + 0.5;
            double d6 = blockpos.getY() + 0.5;
            double d7 = blockpos.getZ() + 0.5;
            BlockPattern.Match blockpattern$match = Blocks.NETHER_PORTAL.findPortalShape(this.world, blockpos);
            boolean flag1 = blockpattern$match.getForward().clockwiseY().getAxisDirection() == Direction.AxisDirection.NEGATIVE;
            double d2 = blockpattern$match.getForward().getAxis() == Direction.Axis.X
                ? blockpattern$match.getTopLeftFront().getZ()
                : blockpattern$match.getTopLeftFront().getX();
            d6 = blockpattern$match.getTopLeftFront().getY() + 1 - entity.getLastPortalOffset().y * blockpattern$match.getHeight();
            if (flag1) {
                d2++;
            }

            if (blockpattern$match.getForward().getAxis() == Direction.Axis.X) {
                d7 = d2
                    + (1.0 - entity.getLastPortalOffset().x)
                        * blockpattern$match.getWidth()
                        * blockpattern$match.getForward().clockwiseY().getAxisDirection().getOffset();
            } else {
                d5 = d2
                    + (1.0 - entity.getLastPortalOffset().x)
                        * blockpattern$match.getWidth()
                        * blockpattern$match.getForward().clockwiseY().getAxisDirection().getOffset();
            }

            float f = 0.0F;
            float f1 = 0.0F;
            float f2 = 0.0F;
            float f3 = 0.0F;
            if (blockpattern$match.getForward().getOpposite() == entity.getLastPortalFacing()) {
                f = 1.0F;
                f1 = 1.0F;
            } else if (blockpattern$match.getForward().getOpposite() == entity.getLastPortalFacing().getOpposite()) {
                f = -1.0F;
                f1 = -1.0F;
            } else if (blockpattern$match.getForward().getOpposite() == entity.getLastPortalFacing().clockwiseY()) {
                f2 = 1.0F;
                f3 = -1.0F;
            } else {
                f2 = -1.0F;
                f3 = 1.0F;
            }

            double d3 = entity.velocityX;
            double d4 = entity.velocityZ;
            entity.velocityX = d3 * f + d4 * f3;
            entity.velocityZ = d3 * f2 + d4 * f1;
            entity.yaw = yaw - entity.getLastPortalFacing().getOpposite().getIdHorizontal() * 90 + blockpattern$match.getForward().getIdHorizontal() * 90;
            entity.setPositionAndAngles(d5, d6, d7, entity.yaw, entity.pitch);
            return true;
        } else {
            return false;
        }
    }

    public boolean generateNetherPortal(Entity entity) {
        int i = 16;
        double d0 = -1.0;
        int j = MathHelper.floor(entity.x);
        int k = MathHelper.floor(entity.y);
        int l = MathHelper.floor(entity.z);
        int i1 = j;
        int j1 = k;
        int k1 = l;
        int l1 = 0;
        int i2 = this.random.nextInt(4);
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int j2 = j - i; j2 <= j + i; j2++) {
            double d1 = j2 + 0.5 - entity.x;

            for (int l2 = l - i; l2 <= l + i; l2++) {
                double d2 = l2 + 0.5 - entity.z;

                label296:
                for (int j3 = this.world.getDimensionHeight() - 1; j3 >= 0; j3--) {
                    if (this.world.isAir(blockpos$mutable.set(j2, j3, l2))) {
                        while (j3 > 0 && this.world.isAir(blockpos$mutable.set(j2, j3 - 1, l2))) {
                            j3--;
                        }

                        for (int k3 = i2; k3 < i2 + 4; k3++) {
                            int l3 = k3 % 2;
                            int i4 = 1 - l3;
                            if (k3 % 4 >= 2) {
                                l3 = -l3;
                                i4 = -i4;
                            }

                            for (int j4 = 0; j4 < 3; j4++) {
                                for (int k4 = 0; k4 < 4; k4++) {
                                    for (int l4 = -1; l4 < 4; l4++) {
                                        int i5 = j2 + (k4 - 1) * l3 + j4 * i4;
                                        int j5 = j3 + l4;
                                        int k5 = l2 + (k4 - 1) * i4 - j4 * l3;
                                        blockpos$mutable.set(i5, j5, k5);
                                        if (l4 < 0 && !this.world.getBlockState(blockpos$mutable).getBlock().getMaterial().isSolid()
                                            || l4 >= 0 && !this.world.isAir(blockpos$mutable)) {
                                            continue label296;
                                        }
                                    }
                                }
                            }

                            double d5 = j3 + 0.5 - entity.y;
                            double d7 = d1 * d1 + d5 * d5 + d2 * d2;
                            if (d0 < 0.0 || d7 < d0) {
                                d0 = d7;
                                i1 = j2;
                                j1 = j3;
                                k1 = l2;
                                l1 = k3 % 4;
                            }
                        }
                    }
                }
            }
        }

        if (d0 < 0.0) {
            for (int l5 = j - i; l5 <= j + i; l5++) {
                double d3 = l5 + 0.5 - entity.x;

                for (int k6 = l - i; k6 <= l + i; k6++) {
                    double d4 = k6 + 0.5 - entity.z;

                    label233:
                    for (int j7 = this.world.getDimensionHeight() - 1; j7 >= 0; j7--) {
                        if (this.world.isAir(blockpos$mutable.set(l5, j7, k6))) {
                            while (j7 > 0 && this.world.isAir(blockpos$mutable.set(l5, j7 - 1, k6))) {
                                j7--;
                            }

                            for (int l7 = i2; l7 < i2 + 2; l7++) {
                                int k8 = l7 % 2;
                                int k9 = 1 - k8;

                                for (int k10 = 0; k10 < 4; k10++) {
                                    for (int k11 = -1; k11 < 4; k11++) {
                                        int k12 = l5 + (k10 - 1) * k8;
                                        int j13 = j7 + k11;
                                        int k13 = k6 + (k10 - 1) * k9;
                                        blockpos$mutable.set(k12, j13, k13);
                                        if (k11 < 0 && !this.world.getBlockState(blockpos$mutable).getBlock().getMaterial().isSolid()
                                            || k11 >= 0 && !this.world.isAir(blockpos$mutable)) {
                                            continue label233;
                                        }
                                    }
                                }

                                double d6 = j7 + 0.5 - entity.y;
                                double d8 = d3 * d3 + d6 * d6 + d4 * d4;
                                if (d0 < 0.0 || d8 < d0) {
                                    d0 = d8;
                                    i1 = l5;
                                    j1 = j7;
                                    k1 = k6;
                                    l1 = l7 % 2;
                                }
                            }
                        }
                    }
                }
            }
        }

        int i6 = l1;
        int j6 = i1;
        int k2 = j1;
        int l6 = k1;
        int i7 = i6 % 2;
        int i3 = 1 - i7;
        if (i6 % 4 >= 2) {
            i7 = -i7;
            i3 = -i3;
        }

        if (d0 < 0.0) {
            j1 = MathHelper.clamp(j1, 70, this.world.getDimensionHeight() - 10);
            k2 = j1;

            for (int k7 = -1; k7 <= 1; k7++) {
                for (int i8 = 1; i8 < 3; i8++) {
                    for (int l8 = -1; l8 < 3; l8++) {
                        int l9 = j6 + (i8 - 1) * i7 + k7 * i3;
                        int l10 = k2 + l8;
                        int l11 = l6 + (i8 - 1) * i3 - k7 * i7;
                        boolean flag = l8 < 0;
                        this.world.setBlockState(new BlockPos(l9, l10, l11), flag ? Blocks.OBSIDIAN.defaultState() : Blocks.AIR.defaultState());
                    }
                }
            }
        }

        BlockState blockstate = Blocks.NETHER_PORTAL.defaultState().set(PortalBlock.AXIS, i7 != 0 ? Direction.Axis.X : Direction.Axis.Z);

        for (int j8 = 0; j8 < 4; j8++) {
            for (int i9 = 0; i9 < 4; i9++) {
                for (int i10 = -1; i10 < 4; i10++) {
                    int i11 = j6 + (i9 - 1) * i7;
                    int i12 = k2 + i10;
                    int l12 = l6 + (i9 - 1) * i3;
                    boolean flag1 = i9 == 0 || i9 == 3 || i10 == -1 || i10 == 3;
                    this.world.setBlockState(new BlockPos(i11, i12, l12), flag1 ? Blocks.OBSIDIAN.defaultState() : blockstate, 2);
                }
            }

            for (int j9 = 0; j9 < 4; j9++) {
                for (int j10 = -1; j10 < 4; j10++) {
                    int j11 = j6 + (j9 - 1) * i7;
                    int j12 = k2 + j10;
                    int i13 = l6 + (j9 - 1) * i3;
                    BlockPos blockpos = new BlockPos(j11, j12, i13);
                    this.world.updateNeighbors(blockpos, this.world.getBlockState(blockpos).getBlock());
                }
            }
        }

        return true;
    }

    public void tick(long time) {
        if (time % 100L == 0L) {
            Iterator<Long> iterator = this.portalCacheKeys.iterator();
            long i = time - 300L;

            while (iterator.hasNext()) {
                Long olong = iterator.next();
                PortalForcer.PortalPos portalforcer$portalpos = this.portalCache.get(olong);
                if (portalforcer$portalpos == null || portalforcer$portalpos.lastUseTime < i) {
                    iterator.remove();
                    this.portalCache.remove(olong);
                }
            }
        }
    }

    public class PortalPos extends BlockPos {
        public long lastUseTime;

        public PortalPos(BlockPos x, long y) {
            super(x.getX(), x.getY(), x.getZ());
            this.lastUseTime = y;
        }
    }
}
