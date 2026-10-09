package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.Property;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class SlabItem extends BlockItem {
    private final SlabBlock singleSlab;
    private final SlabBlock doubleSlab;

    public SlabItem(Block block, SlabBlock singleSlab, SlabBlock doubleSlab) {
        super(block);
        this.singleSlab = singleSlab;
        this.doubleSlab = doubleSlab;
        this.setMaxDamage(0);
        this.setHasCustomData(true);
    }

    @Override
    public int getBlockMetadata(int metadata) {
        return metadata;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return this.singleSlab.getName(stack.getMetadata());
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (stack.size == 0) {
            return false;
        }

        if (!player.canUseItemOn(pos.offset(face), face, stack)) {
            return false;
        }

        Object object = this.singleSlab.getVariant(stack);
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() == this.singleSlab) {
            Property property = this.singleSlab.getVariantProperty();
            Comparable comparable = blockstate.get(property);
            SlabBlock.Half slabblock$half = blockstate.get(SlabBlock.HALF);
            if ((face == Direction.UP && slabblock$half == SlabBlock.Half.BOTTOM || face == Direction.DOWN && slabblock$half == SlabBlock.Half.TOP)
                && comparable == object) {
                BlockState blockstate1 = this.doubleSlab.defaultState().set(property, comparable);
                if (world.isUnobstructed(this.doubleSlab.getCollisionShape(world, pos, blockstate1)) && world.setBlockState(pos, blockstate1, 3)) {
                    world.playSound(
                        pos.getX() + 0.5F,
                        pos.getY() + 0.5F,
                        pos.getZ() + 0.5F,
                        this.doubleSlab.sounds.getPlacing(),
                        (this.doubleSlab.sounds.getVolume() + 1.0F) / 2.0F,
                        this.doubleSlab.sounds.getPitch() * 0.8F
                    );
                    stack.size--;
                }

                return true;
            }
        }

        return this.combineSlabs(stack, world, pos.offset(face), object) || super.useOn(stack, player, world, pos, face, faceX, faceY, faceZ);
    }

    @Override
    public boolean onPlace(World world, BlockPos pos, Direction dir, PlayerEntity player, ItemStack stack) {
        BlockPos blockpos = pos;
        Property property = this.singleSlab.getVariantProperty();
        Object object = this.singleSlab.getVariant(stack);
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() == this.singleSlab) {
            boolean flag = blockstate.get(SlabBlock.HALF) == SlabBlock.Half.TOP;
            if ((dir == Direction.UP && !flag || dir == Direction.DOWN && flag) && object == blockstate.get(property)) {
                return true;
            }
        }

        pos = pos.offset(dir);
        BlockState blockstate1 = world.getBlockState(pos);
        return blockstate1.getBlock() == this.singleSlab && object == blockstate1.get(property) || super.onPlace(world, blockpos, dir, player, stack);
    }

    private boolean combineSlabs(ItemStack item, World player, BlockPos world, Object variant) {
        BlockState blockstate = player.getBlockState(world);
        if (blockstate.getBlock() == this.singleSlab) {
            Comparable comparable = blockstate.get((Property<Comparable>)this.singleSlab.getVariantProperty());
            if (comparable == variant) {
                BlockState blockstate1 = this.doubleSlab.defaultState().set(this.singleSlab.getVariantProperty(), comparable);
                if (player.isUnobstructed(this.doubleSlab.getCollisionShape(player, world, blockstate1)) && player.setBlockState(world, blockstate1, 3)) {
                    player.playSound(
                        world.getX() + 0.5F,
                        world.getY() + 0.5F,
                        world.getZ() + 0.5F,
                        this.doubleSlab.sounds.getPlacing(),
                        (this.doubleSlab.sounds.getVolume() + 1.0F) / 2.0F,
                        this.doubleSlab.sounds.getPitch() * 0.8F
                    );
                    item.size--;
                }

                return true;
            }
        }

        return false;
    }
}
