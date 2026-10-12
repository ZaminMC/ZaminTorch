package net.minecraft.block;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.I18n;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class SpongeBlock extends Block {
    public static final BooleanProperty WET = BooleanProperty.of("wet");

    protected SpongeBlock() {
        super(Material.SPONGE);
        this.setDefaultState(this.stateDefinition.any().set(WET, false));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public String getName() {
        return I18n.translate(this.getTranslationKey() + ".dry.name");
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(WET) ? 1 : 0;
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        this.getWet(world, pos, state);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        this.getWet(world, pos, state);
        super.neighborChanged(world, pos, state, neighborBlock);
    }

    protected void getWet(World world, BlockPos pos, BlockState state) {
        if (!state.get(WET) && this.removeWater(world, pos)) {
            world.setBlockState(pos, state.set(WET, true), 2);
            world.doEvent(2001, pos, Block.getId(Blocks.WATER));
        }
    }

    private boolean removeWater(World world, BlockPos pos) {
        Queue<Pair<BlockPos, Integer>> queue = Lists.newLinkedList();
        ArrayList<BlockPos> arraylist = Lists.newArrayList();
        queue.add(new Pair<>(pos, 0));
        int i = 0;

        while (!queue.isEmpty()) {
            Pair<BlockPos, Integer> pair = queue.poll();
            BlockPos blockpos = pair.getLeft();
            int j = pair.getRight();

            for (Direction direction : Direction.values()) {
                BlockPos blockpos1 = blockpos.offset(direction);
                if (world.getBlockState(blockpos1).getBlock().getMaterial() == Material.WATER) {
                    world.setBlockState(blockpos1, Blocks.AIR.defaultState(), 2);
                    arraylist.add(blockpos1);
                    i++;
                    if (j < 6) {
                        queue.add(new Pair<>(blockpos1, j + 1));
                    }
                }
            }

            if (i > 64) {
                break;
            }
        }

        for (BlockPos blockpos2 : arraylist) {
            world.updateNeighbors(blockpos2, Blocks.AIR);
        }

        return i > 0;
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(item, 1, 0));
        inventory.add(new ItemStack(item, 1, 1));
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(WET, (metadata & 1) == 1);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(WET) ? 1 : 0;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, WET);
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        if (state.get(WET)) {
            Direction direction = Direction.pick(random);
            if (direction != Direction.UP && !World.hasSolidTop(world, pos.offset(direction))) {
                double d0 = pos.getX();
                double d1 = pos.getY();
                double d2 = pos.getZ();
                if (direction == Direction.DOWN) {
                    d1 -= 0.05;
                    d0 += random.nextDouble();
                    d2 += random.nextDouble();
                } else {
                    d1 += random.nextDouble() * 0.8;
                    if (direction.getAxis() == Direction.Axis.X) {
                        d2 += random.nextDouble();
                        if (direction == Direction.EAST) {
                            d0 += 1.1;
                        } else {
                            d0 += 0.05;
                        }
                    } else {
                        d0 += random.nextDouble();
                        if (direction == Direction.SOUTH) {
                            d2 += 1.1;
                        } else {
                            d2 += 0.05;
                        }
                    }
                }

                world.addParticle(ParticleType.DRIP_WATER, d0, d1, d2, 0.0, 0.0, 0.0);
            }
        }
    }
}
