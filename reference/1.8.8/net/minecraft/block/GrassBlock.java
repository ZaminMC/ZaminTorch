package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.world.color.BiomeColors;
import net.minecraft.client.world.color.GrassColors;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class GrassBlock extends Block implements Fertilizable {
    public static final BooleanProperty SNOWY = BooleanProperty.of("snowy");

    protected GrassBlock() {
        super(Material.GRASS);
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
    public int getColor() {
        return GrassColors.getColor(0.5, 1.0);
    }

    @Override
    public int getColor(BlockState state) {
        return this.getColor();
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        return BiomeColors.getGrassColor(world, pos);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            if (world.getRawBrightness(pos.up()) < 4 && world.getBlockState(pos.up()).getBlock().getOpacity() > 2) {
                world.setBlockState(pos, Blocks.DIRT.defaultState());
            } else {
                if (world.getRawBrightness(pos.up()) >= 9) {
                    for (int i = 0; i < 4; i++) {
                        BlockPos blockpos = pos.add(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
                        Block block = world.getBlockState(blockpos.up()).getBlock();
                        BlockState blockstate = world.getBlockState(blockpos);
                        if (blockstate.getBlock() == Blocks.DIRT
                            && blockstate.get(DirtBlock.VARIANT) == DirtBlock.Variant.DIRT
                            && world.getRawBrightness(blockpos.up()) >= 4
                            && block.getOpacity() <= 2) {
                            world.setBlockState(blockpos, Blocks.GRASS.defaultState());
                        }
                    }
                }
            }
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Blocks.DIRT.getDropItem(Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.DIRT), random, fortuneLevel);
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, BlockState state, boolean isClient) {
        return true;
    }

    @Override
    public boolean canBeFertilized(World world, Random rand, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, BlockState state) {
        BlockPos blockpos = pos.up();

        label38:
        for (int i = 0; i < 128; i++) {
            BlockPos blockpos1 = blockpos;

            for (int j = 0; j < i / 16; j++) {
                blockpos1 = blockpos1.add(rand.nextInt(3) - 1, (rand.nextInt(3) - 1) * rand.nextInt(3) / 2, rand.nextInt(3) - 1);
                if (world.getBlockState(blockpos1.down()).getBlock() != Blocks.GRASS || world.getBlockState(blockpos1).getBlock().isSolid()) {
                    continue label38;
                }
            }

            if (world.getBlockState(blockpos1).getBlock().material == Material.AIR) {
                if (rand.nextInt(8) == 0) {
                    FlowerBlock.Type flowerblock$type = world.getBiome(blockpos1).getRandomFlower(rand, blockpos1);
                    FlowerBlock flowerblock = flowerblock$type.getGroup().getBlock();
                    BlockState blockstate = flowerblock.defaultState().set(flowerblock.getTypeProperty(), flowerblock$type);
                    if (flowerblock.canSurvive(world, blockpos1, blockstate)) {
                        world.setBlockState(blockpos1, blockstate, 3);
                    }
                } else {
                    BlockState blockstate1 = Blocks.TALLGRASS.defaultState().set(TallPlantBlock.TYPE, TallPlantBlock.Type.GRASS);
                    if (Blocks.TALLGRASS.canSurvive(world, blockpos1, blockstate1)) {
                        world.setBlockState(blockpos1, blockstate1, 3);
                    }
                }
            }
        }
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT_MIPPED;
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
