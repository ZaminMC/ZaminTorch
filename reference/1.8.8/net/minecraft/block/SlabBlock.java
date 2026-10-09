package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.Property;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public abstract class SlabBlock extends Block {
    public static final EnumProperty<SlabBlock.Half> HALF = EnumProperty.of("half", SlabBlock.Half.class);

    public SlabBlock(Material material) {
        super(material);
        if (this.isDouble()) {
            this.opaqueCube = true;
        } else {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.5F, 1.0F);
        }

        this.setOpacity(255);
    }

    @Override
    protected boolean hasSilkTouchDrops() {
        return false;
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        if (this.isDouble()) {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        } else {
            BlockState blockstate = world.getBlockState(pos);
            if (blockstate.getBlock() == this) {
                if (blockstate.get(HALF) == SlabBlock.Half.TOP) {
                    this.setShape(0.0F, 0.5F, 0.0F, 1.0F, 1.0F, 1.0F);
                } else {
                    this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.5F, 1.0F);
                }
            }
        }
    }

    @Override
    public void resetShape() {
        if (this.isDouble()) {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        } else {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.5F, 1.0F);
        }
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        this.updateShape(world, pos);
        super.addCollisions(world, pos, state, shape, collisions, entity);
    }

    @Override
    public boolean isSolidRender() {
        return this.isDouble();
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        BlockState blockstate = super.getPlacementState(world, pos, dir, dx, dy, dz, metadata, entity).set(HALF, SlabBlock.Half.BOTTOM);
        if (this.isDouble()) {
            return blockstate;
        } else {
            return dir != Direction.DOWN && (dir == Direction.UP || !(dy > 0.5)) ? blockstate : blockstate.set(HALF, SlabBlock.Half.TOP);
        }
    }

    @Override
    public int getBaseDropCount(Random random) {
        return this.isDouble() ? 2 : 1;
    }

    @Override
    public boolean isCube() {
        return this.isDouble();
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        if (this.isDouble()) {
            return super.shouldRenderFace(world, pos, face);
        }

        if (face != Direction.UP && face != Direction.DOWN && !super.shouldRenderFace(world, pos, face)) {
            return false;
        }

        BlockPos blockpos = pos.offset(face.getOpposite());
        BlockState blockstate = world.getBlockState(pos);
        BlockState blockstate1 = world.getBlockState(blockpos);
        boolean flag = isSingleSlab(blockstate.getBlock()) && blockstate.get(HALF) == SlabBlock.Half.TOP;
        boolean flag1 = isSingleSlab(blockstate1.getBlock()) && blockstate1.get(HALF) == SlabBlock.Half.TOP;
        return flag1
            ? face == Direction.DOWN || face == Direction.UP && super.shouldRenderFace(world, pos, face) || !isSingleSlab(blockstate.getBlock()) || !flag
            : face == Direction.UP || face == Direction.DOWN && super.shouldRenderFace(world, pos, face) || !isSingleSlab(blockstate.getBlock()) || flag;
    }

    protected static boolean isSingleSlab(Block block) {
        return block == Blocks.STONE_SLAB || block == Blocks.WOODEN_SLAB || block == Blocks.RED_SANDSTONE_SLAB;
    }

    public abstract String getName(int variant);

    @Override
    public int getPickItemMetadata(World world, BlockPos pos) {
        return super.getPickItemMetadata(world, pos) & 7;
    }

    public abstract boolean isDouble();

    public abstract Property<?> getVariantProperty();

    public abstract Object getVariant(ItemStack item);

    public enum Half implements StringSerializable {
        TOP("top"),
        BOTTOM("bottom");

        private final String key;

        Half(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return this.key;
        }

        @Override
        public String serializeToString() {
            return this.key;
        }
    }
}
