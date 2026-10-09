package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.AcaciaTreeFeature;
import net.minecraft.world.gen.feature.BirchTreeFeature;
import net.minecraft.world.gen.feature.DarkOakTreeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.GiantJungleTreeFeature;
import net.minecraft.world.gen.feature.GiantSpruceTreeFeature;
import net.minecraft.world.gen.feature.LargeOakTreeFeature;
import net.minecraft.world.gen.feature.SpruceTreeFeature;
import net.minecraft.world.gen.feature.TreeFeature;

public class SaplingBlock extends PlantBlock implements Fertilizable {
    public static final EnumProperty<PlanksBlock.Variant> TYPE = EnumProperty.of("type", PlanksBlock.Variant.class);
    public static final IntegerProperty STAGE = IntegerProperty.of("stage", 0, 1);

    protected SaplingBlock() {
        this.setDefaultState(this.stateDefinition.any().set(TYPE, PlanksBlock.Variant.OAK).set(STAGE, 0));
        float f = 0.4F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f * 2.0F, 0.5F + f);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public String getName() {
        return I18n.translate(this.getTranslationKey() + "." + PlanksBlock.Variant.OAK.getName() + ".name");
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            super.tick(world, pos, state, random);
            if (world.getRawBrightness(pos.up()) >= 9 && random.nextInt(7) == 0) {
                this.tryGrow(world, pos, state, random);
            }
        }
    }

    public void tryGrow(World world, BlockPos pos, BlockState state, Random random) {
        if (state.get(STAGE) == 0) {
            world.setBlockState(pos, state.next(STAGE), 4);
        } else {
            this.grow(world, pos, state, random);
        }
    }

    public void grow(World world, BlockPos pos, BlockState state, Random random) {
        Feature feature = random.nextInt(10) == 0 ? new LargeOakTreeFeature(true) : new TreeFeature(true);
        int i = 0;
        int j = 0;
        boolean flag = false;
        switch ((PlanksBlock.Variant)state.get(TYPE)) {
            case SPRUCE:
                label68:
                for (i = 0; i >= -1; i--) {
                    for (j = 0; j >= -1; j--) {
                        if (this.isSapling(world, pos, i, j, PlanksBlock.Variant.SPRUCE)) {
                            feature = new GiantSpruceTreeFeature(false, random.nextBoolean());
                            flag = true;
                            break label68;
                        }
                    }
                }

                if (!flag) {
                    j = 0;
                    i = 0;
                    feature = new SpruceTreeFeature(true);
                }
                break;
            case BIRCH:
                feature = new BirchTreeFeature(true, false);
                break;
            case JUNGLE:
                BlockState blockstate = Blocks.LOG.defaultState().set(LogBlock.VARIANT, PlanksBlock.Variant.JUNGLE);
                BlockState blockstate1 = Blocks.LEAVES
                    .defaultState()
                    .set(LeavesBlock.VARIANT, PlanksBlock.Variant.JUNGLE)
                    .set(AbstractLeavesBlock.CHECK_DECAY, false);

                label82:
                for (i = 0; i >= -1; i--) {
                    for (j = 0; j >= -1; j--) {
                        if (this.isSapling(world, pos, i, j, PlanksBlock.Variant.JUNGLE)) {
                            feature = new GiantJungleTreeFeature(true, 10, 20, blockstate, blockstate1);
                            flag = true;
                            break label82;
                        }
                    }
                }

                if (!flag) {
                    j = 0;
                    i = 0;
                    feature = new TreeFeature(true, 4 + random.nextInt(7), blockstate, blockstate1, false);
                }
                break;
            case ACACIA:
                feature = new AcaciaTreeFeature(true);
                break;
            case DARK_OAK:
                label96:
                for (i = 0; i >= -1; i--) {
                    for (j = 0; j >= -1; j--) {
                        if (this.isSapling(world, pos, i, j, PlanksBlock.Variant.DARK_OAK)) {
                            feature = new DarkOakTreeFeature(true);
                            flag = true;
                            break label96;
                        }
                    }
                }

                if (!flag) {
                    return;
                }
            case OAK:
        }

        BlockState blockstate2 = Blocks.AIR.defaultState();
        if (flag) {
            world.setBlockState(pos.add(i, 0, j), blockstate2, 4);
            world.setBlockState(pos.add(i + 1, 0, j), blockstate2, 4);
            world.setBlockState(pos.add(i, 0, j + 1), blockstate2, 4);
            world.setBlockState(pos.add(i + 1, 0, j + 1), blockstate2, 4);
        } else {
            world.setBlockState(pos, blockstate2, 4);
        }

        if (!feature.place(world, random, pos.add(i, 0, j))) {
            if (flag) {
                world.setBlockState(pos.add(i, 0, j), state, 4);
                world.setBlockState(pos.add(i + 1, 0, j), state, 4);
                world.setBlockState(pos.add(i, 0, j + 1), state, 4);
                world.setBlockState(pos.add(i + 1, 0, j + 1), state, 4);
            } else {
                world.setBlockState(pos, state, 4);
            }
        }
    }

    private boolean isSapling(World world, BlockPos pos, int dx, int dz, PlanksBlock.Variant variant) {
        return this.isSapling(world, pos.add(dx, 0, dz), variant)
            && this.isSapling(world, pos.add(dx + 1, 0, dz), variant)
            && this.isSapling(world, pos.add(dx, 0, dz + 1), variant)
            && this.isSapling(world, pos.add(dx + 1, 0, dz + 1), variant);
    }

    public boolean isSapling(World world, BlockPos pos, PlanksBlock.Variant variant) {
        BlockState blockstate = world.getBlockState(pos);
        return blockstate.getBlock() == this && blockstate.get(TYPE) == variant;
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(TYPE).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (PlanksBlock.Variant planksblock$variant : PlanksBlock.Variant.values()) {
            inventory.add(new ItemStack(item, 1, planksblock$variant.getId()));
        }
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, BlockState state, boolean isClient) {
        return true;
    }

    @Override
    public boolean canBeFertilized(World world, Random rand, BlockPos pos, BlockState state) {
        return world.random.nextFloat() < 0.45;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, BlockState state) {
        this.tryGrow(world, pos, state, rand);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(TYPE, PlanksBlock.Variant.byId(metadata & 7)).set(STAGE, (metadata & 8) >> 3);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(TYPE).getId();
        return i | state.get(STAGE) << 3;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, TYPE, STAGE);
    }
}
