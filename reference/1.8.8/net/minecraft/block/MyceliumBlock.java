package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class MyceliumBlock extends Block {
    public static final BooleanProperty SNOWY = BooleanProperty.of("snowy");

    protected MyceliumBlock() {
        super(Material.GRASS, MapColor.PURPLE);
        this.setDefaultState(this.stateDefinition.any().set(SNOWY, false));
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        Block block = world.getBlockState(pos.up()).getBlock();
        return state.set(SNOWY, block == Blocks.SNOW || block == Blocks.SNOW_LAYER);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            if (world.getRawBrightness(pos.up()) < 4 && world.getBlockState(pos.up()).getBlock().getOpacity() > 2) {
                world.setBlockState(pos, Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.DIRT));
            } else {
                if (world.getRawBrightness(pos.up()) >= 9) {
                    for (int i = 0; i < 4; i++) {
                        BlockPos blockpos = pos.add(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
                        BlockState blockstate = world.getBlockState(blockpos);
                        Block block = world.getBlockState(blockpos.up()).getBlock();
                        if (blockstate.getBlock() == Blocks.DIRT
                            && blockstate.get(DirtBlock.VARIANT) == DirtBlock.Variant.DIRT
                            && world.getRawBrightness(blockpos.up()) >= 4
                            && block.getOpacity() <= 2) {
                            world.setBlockState(blockpos, this.defaultState());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        super.randomDisplayTick(world, pos, state, random);
        if (random.nextInt(10) == 0) {
            world.addParticle(ParticleType.TOWN_AURA, pos.getX() + random.nextFloat(), pos.getY() + 1.1F, pos.getZ() + random.nextFloat(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Blocks.DIRT.getDropItem(Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.DIRT), random, fortuneLevel);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return 0;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, SNOWY);
    }
}
