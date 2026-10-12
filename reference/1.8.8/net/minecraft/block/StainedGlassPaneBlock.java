package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class StainedGlassPaneBlock extends PaneBlock {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.of("color", DyeColor.class);

    public StainedGlassPaneBlock() {
        super(Material.GLASS, false);
        this.setDefaultState(this.stateDefinition.any().set(NORTH, false).set(EAST, false).set(SOUTH, false).set(WEST, false).set(COLOR, DyeColor.WHITE));
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(COLOR).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (int i = 0; i < DyeColor.values().length; i++) {
            inventory.add(new ItemStack(item, 1, i));
        }
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(COLOR).getMapColor();
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.TRANSLUCENT;
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
        return new StateDefinition(this, NORTH, EAST, WEST, SOUTH, COLOR);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            BeaconBlock.updateBeam(world, pos);
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            BeaconBlock.updateBeam(world, pos);
        }
    }
}
