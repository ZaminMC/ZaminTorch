package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DaylightDetectorBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class DaylightDetectorBlock extends BlockWithBlockEntity {
    public static final IntegerProperty POWER = IntegerProperty.of("power", 0, 15);
    private final boolean inverted;

    public DaylightDetectorBlock(boolean inverted) {
        super(Material.WOOD);
        this.inverted = inverted;
        this.setDefaultState(this.stateDefinition.any().set(POWER, 0));
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.375F, 1.0F);
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
        this.setStrength(0.2F);
        this.setSounds(WOOD_SOUNDS);
        this.setKey("daylightDetector");
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.375F, 1.0F);
    }

    @Override
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return state.get(POWER);
    }

    public void updateOutputState(World world, BlockPos pos) {
        if (!world.dimension.hasNoSky()) {
            BlockState blockstate = world.getBlockState(pos);
            int i = world.getLight(LightType.SKY, pos) - world.getAmbientDarkness();
            float f = world.getSunAngle(1.0F);
            float f1 = f < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
            f += (f1 - f) * 0.2F;
            i = Math.round(i * MathHelper.cos(f));
            i = MathHelper.clamp(i, 0, 15);
            if (this.inverted) {
                i = 15 - i;
            }

            if (blockstate.get(POWER) != i) {
                world.setBlockState(pos, blockstate.set(POWER, i), 3);
            }
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (player.canModifyWorld()) {
            if (world.isClient) {
                return true;
            }

            if (this.inverted) {
                world.setBlockState(pos, Blocks.DAYLIGHT_DETECTOR.defaultState().set(POWER, state.get(POWER)), 4);
                Blocks.DAYLIGHT_DETECTOR.updateOutputState(world, pos);
            } else {
                world.setBlockState(pos, Blocks.INVERTED_DAYLIGHT_DETECTOR.defaultState().set(POWER, state.get(POWER)), 4);
                Blocks.INVERTED_DAYLIGHT_DETECTOR.updateOutputState(world, pos);
            }

            return true;
        } else {
            return super.use(world, pos, state, player, face, faceX, faceY, faceZ);
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.DAYLIGHT_DETECTOR);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(Blocks.DAYLIGHT_DETECTOR);
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 3;
    }

    @Override
    public boolean isSignalSource() {
        return true;
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new DaylightDetectorBlockEntity();
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(POWER, metadata);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(POWER);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, POWER);
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        if (!this.inverted) {
            super.addToCreativeMenu(item, tab, inventory);
        }
    }
}
