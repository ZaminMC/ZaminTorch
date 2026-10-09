package net.minecraft.world.gen.feature;

import com.google.common.base.Predicate;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.predicate.BlockPredicate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class VeinFeature extends Feature {
    private final BlockState state;
    private final int size;
    private final Predicate<BlockState> replaceable;

    public VeinFeature(BlockState state, int size) {
        this(state, size, BlockPredicate.of(Blocks.STONE));
    }

    public VeinFeature(BlockState state, int size, Predicate<BlockState> replaceable) {
        this.state = state;
        this.size = size;
        this.replaceable = replaceable;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        float f = random.nextFloat() * (float) Math.PI;
        double d0 = pos.getX() + 8 + MathHelper.sin(f) * this.size / 8.0F;
        double d1 = pos.getX() + 8 - MathHelper.sin(f) * this.size / 8.0F;
        double d2 = pos.getZ() + 8 + MathHelper.cos(f) * this.size / 8.0F;
        double d3 = pos.getZ() + 8 - MathHelper.cos(f) * this.size / 8.0F;
        double d4 = pos.getY() + random.nextInt(3) - 2;
        double d5 = pos.getY() + random.nextInt(3) - 2;

        for (int i = 0; i < this.size; i++) {
            float f1 = (float)i / this.size;
            double d6 = d0 + (d1 - d0) * f1;
            double d7 = d4 + (d5 - d4) * f1;
            double d8 = d2 + (d3 - d2) * f1;
            double d9 = random.nextDouble() * this.size / 16.0;
            double d10 = (MathHelper.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0;
            double d11 = (MathHelper.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0;
            int j = MathHelper.floor(d6 - d10 / 2.0);
            int k = MathHelper.floor(d7 - d11 / 2.0);
            int l = MathHelper.floor(d8 - d10 / 2.0);
            int i1 = MathHelper.floor(d6 + d10 / 2.0);
            int j1 = MathHelper.floor(d7 + d11 / 2.0);
            int k1 = MathHelper.floor(d8 + d10 / 2.0);

            for (int l1 = j; l1 <= i1; l1++) {
                double d12 = (l1 + 0.5 - d6) / (d10 / 2.0);
                if (d12 * d12 < 1.0) {
                    for (int i2 = k; i2 <= j1; i2++) {
                        double d13 = (i2 + 0.5 - d7) / (d11 / 2.0);
                        if (d12 * d12 + d13 * d13 < 1.0) {
                            for (int j2 = l; j2 <= k1; j2++) {
                                double d14 = (j2 + 0.5 - d8) / (d10 / 2.0);
                                if (d12 * d12 + d13 * d13 + d14 * d14 < 1.0) {
                                    BlockPos blockpos = new BlockPos(l1, i2, j2);
                                    if (this.replaceable.apply(world.getBlockState(blockpos))) {
                                        world.setBlockState(blockpos, this.state, 2);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
