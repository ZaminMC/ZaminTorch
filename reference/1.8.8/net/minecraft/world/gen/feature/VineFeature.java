package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.VineBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class VineFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        while (pos.getY() < 128) {
            if (world.isAir(pos)) {
                for (Direction direction : Direction.Plane.HORIZONTAL.get()) {
                    if (Blocks.VINE.canBePlaced(world, pos, direction)) {
                        BlockState blockstate = Blocks.VINE
                            .defaultState()
                            .set(VineBlock.NORTH, direction == Direction.NORTH)
                            .set(VineBlock.EAST, direction == Direction.EAST)
                            .set(VineBlock.SOUTH, direction == Direction.SOUTH)
                            .set(VineBlock.WEST, direction == Direction.WEST);
                        world.setBlockState(pos, blockstate, 2);
                        break;
                    }
                }
            } else {
                pos = pos.add(random.nextInt(4) - random.nextInt(4), 0, random.nextInt(4) - random.nextInt(4));
            }

            pos = pos.up();
        }

        return true;
    }
}
