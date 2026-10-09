package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.PlantBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlantFeature extends Feature {
    private PlantBlock plant;

    public PlantFeature(PlantBlock plant) {
        this.plant = plant;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        for (int i = 0; i < 64; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
            if (world.isAir(blockpos)
                && (!world.dimension.hasNoSky() || blockpos.getY() < 255)
                && this.plant.canSurvive(world, blockpos, this.plant.defaultState())) {
                world.setBlockState(blockpos, this.plant.defaultState(), 2);
            }
        }

        return true;
    }
}
