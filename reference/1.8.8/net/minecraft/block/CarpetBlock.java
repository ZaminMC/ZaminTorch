package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class CarpetBlock extends Block {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.of("color", DyeColor.class);

    protected CarpetBlock() {
        super(Material.CARPET);
        this.setDefaultState(this.stateDefinition.any().set(COLOR, DyeColor.WHITE));
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.0625F, 1.0F);
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
        this.updateShape(0);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(COLOR).getMapColor();
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public void resetShape() {
        this.updateShape(0);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.updateShape(0);
    }

    protected void updateShape(int metadata) {
        int i = 0;
        float f = 1 * (1 + i) / 16.0F;
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, f, 1.0F);
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return super.canBePlaced(world, pos) && this.canSurvive(world, pos);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        this.canSurviveOrBreak(world, pos, state);
    }

    private boolean canSurviveOrBreak(World world, BlockPos pos, BlockState state) {
        if (!this.canSurvive(world, pos)) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
            return false;
        } else {
            return true;
        }
    }

    private boolean canSurvive(World world, BlockPos pos) {
        return !world.isAir(pos.down());
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return face == Direction.UP || super.shouldRenderFace(world, pos, face);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(COLOR).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (int i = 0; i < 16; i++) {
            inventory.add(new ItemStack(item, 1, i));
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(COLOR, DyeColor.byId(metadata));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(COLOR).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, COLOR);
    }
}
