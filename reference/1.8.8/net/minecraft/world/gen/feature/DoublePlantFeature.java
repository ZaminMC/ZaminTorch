package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DoublePlantFeature extends Feature {
    private DoublePlantBlock.Variant variant;

    public void setVariant(DoublePlantBlock.Variant variant) {
        this.variant = variant;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        boolean flag = false;

        for (int i = 0; i < 64; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
            if (world.isAir(blockpos) && (!world.dimension.hasNoSky() || blockpos.getY() < 254) && Blocks.DOUBLE_PLANT.canBePlaced(world, blockpos)) {
                Blocks.DOUBLE_PLANT.place(world, blockpos, this.variant, 2);
                flag = true;
            }
        }

        return flag;
    }
}
