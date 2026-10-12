package net.minecraft.world.gen.feature;

import com.google.common.base.Predicates;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.SandBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StoneSlabBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.predicate.BlockStatePredicate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class DesertWellFeature extends Feature {
    private static final BlockStatePredicate REPLACEABLE = BlockStatePredicate.of(Blocks.SAND)
        .with(SandBlock.VARIANT, Predicates.equalTo(SandBlock.Variant.SAND));
    private final BlockState base = Blocks.STONE_SLAB
        .defaultState()
        .set(StoneSlabBlock.VARIANT, StoneSlabBlock.Variant.SAND)
        .set(SlabBlock.HALF, SlabBlock.Half.BOTTOM);
    private final BlockState slab = Blocks.SANDSTONE.defaultState();
    private final BlockState liquid = Blocks.FLOWING_WATER.defaultState();

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        while (world.isAir(pos) && pos.getY() > 2) {
            pos = pos.down();
        }

        if (!REPLACEABLE.apply(world.getBlockState(pos))) {
            return false;
        }

        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (world.isAir(pos.add(i, -1, j)) && world.isAir(pos.add(i, -2, j))) {
                    return false;
                }
            }
        }

        for (int l = -1; l <= 0; l++) {
            for (int l1 = -2; l1 <= 2; l1++) {
                for (int k = -2; k <= 2; k++) {
                    world.setBlockState(pos.add(l1, l, k), this.slab, 2);
                }
            }
        }

        world.setBlockState(pos, this.liquid, 2);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            world.setBlockState(pos.offset(direction), this.liquid, 2);
        }

        for (int i1 = -2; i1 <= 2; i1++) {
            for (int i2 = -2; i2 <= 2; i2++) {
                if (i1 == -2 || i1 == 2 || i2 == -2 || i2 == 2) {
                    world.setBlockState(pos.add(i1, 1, i2), this.slab, 2);
                }
            }
        }

        world.setBlockState(pos.add(2, 1, 0), this.base, 2);
        world.setBlockState(pos.add(-2, 1, 0), this.base, 2);
        world.setBlockState(pos.add(0, 1, 2), this.base, 2);
        world.setBlockState(pos.add(0, 1, -2), this.base, 2);

        for (int j1 = -1; j1 <= 1; j1++) {
            for (int j2 = -1; j2 <= 1; j2++) {
                if (j1 == 0 && j2 == 0) {
                    world.setBlockState(pos.add(j1, 4, j2), this.slab, 2);
                } else {
                    world.setBlockState(pos.add(j1, 4, j2), this.base, 2);
                }
            }
        }

        for (int k1 = 1; k1 <= 3; k1++) {
            world.setBlockState(pos.add(-1, k1, -1), this.slab, 2);
            world.setBlockState(pos.add(-1, k1, 1), this.slab, 2);
            world.setBlockState(pos.add(1, k1, -1), this.slab, 2);
            world.setBlockState(pos.add(1, k1, 1), this.slab, 2);
        }

        return true;
    }
}
