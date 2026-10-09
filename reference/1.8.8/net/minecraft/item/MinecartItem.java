package net.minecraft.item;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenseBehavior;
import net.minecraft.block.dispenser.DispenseItemBehavior;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.IBlockSource;
import net.minecraft.world.World;

public class MinecartItem extends Item {
    private static final DispenseBehavior DISPENSE_BEHAVIOR = new DispenseItemBehavior() {
        private final DispenseItemBehavior fallback = new DispenseItemBehavior();

        @Override
        public ItemStack dispenseItem(IBlockSource source, ItemStack item) {
            Direction direction = DispenserBlock.getDirection(source.getBlockMetadata());
            World world = source.getWorld();
            double d0 = source.getX() + direction.getOffsetX() * 1.125;
            double d1 = Math.floor(source.getY()) + direction.getOffsetY();
            double d2 = source.getZ() + direction.getOffsetZ() * 1.125;
            BlockPos blockpos = source.getPos().offset(direction);
            BlockState blockstate = world.getBlockState(blockpos);
            AbstractRailBlock.Shape abstractrailblock$shape = blockstate.getBlock() instanceof AbstractRailBlock
                ? blockstate.get(((AbstractRailBlock)blockstate.getBlock()).getShapeProperty())
                : AbstractRailBlock.Shape.NORTH_SOUTH;
            double d3;
            if (AbstractRailBlock.isRail(blockstate)) {
                if (abstractrailblock$shape.isAscending()) {
                    d3 = 0.6;
                } else {
                    d3 = 0.1;
                }
            } else {
                if (blockstate.getBlock().getMaterial() != Material.AIR || !AbstractRailBlock.isRail(world.getBlockState(blockpos.down()))) {
                    return this.fallback.dispense(source, item);
                }

                BlockState blockstate1 = world.getBlockState(blockpos.down());
                AbstractRailBlock.Shape abstractrailblock$shape1 = blockstate1.getBlock() instanceof AbstractRailBlock
                    ? blockstate1.get(((AbstractRailBlock)blockstate1.getBlock()).getShapeProperty())
                    : AbstractRailBlock.Shape.NORTH_SOUTH;
                if (direction != Direction.DOWN && abstractrailblock$shape1.isAscending()) {
                    d3 = -0.4;
                } else {
                    d3 = -0.9;
                }
            }

            MinecartEntity minecartentity = MinecartEntity.create(world, d0, d1 + d3, d2, ((MinecartItem)item.getItem()).type);
            if (item.hasCustomHoverName()) {
                minecartentity.setCustomName(item.getHoverName());
            }

            world.addEntity(minecartentity);
            item.split(1);
            return item;
        }

        @Override
        protected void playSound(IBlockSource source) {
            source.getWorld().doEvent(1000, source.getPos(), 0);
        }
    };
    private final MinecartEntity.Type type;

    public MinecartItem(MinecartEntity.Type variant) {
        this.maxStackSize = 1;
        this.type = variant;
        this.setCreativeModeTab(CreativeModeTab.TRANSPORTATION);
        DispenserBlock.BEHAVIORS.put(this, DISPENSE_BEHAVIOR);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        BlockState blockstate = world.getBlockState(pos);
        if (AbstractRailBlock.isRail(blockstate)) {
            if (!world.isClient) {
                AbstractRailBlock.Shape abstractrailblock$shape = blockstate.getBlock() instanceof AbstractRailBlock
                    ? blockstate.get(((AbstractRailBlock)blockstate.getBlock()).getShapeProperty())
                    : AbstractRailBlock.Shape.NORTH_SOUTH;
                double d0 = 0.0;
                if (abstractrailblock$shape.isAscending()) {
                    d0 = 0.5;
                }

                MinecartEntity minecartentity = MinecartEntity.create(world, pos.getX() + 0.5, pos.getY() + 0.0625 + d0, pos.getZ() + 0.5, this.type);
                if (stack.hasCustomHoverName()) {
                    minecartentity.setCustomName(stack.getHoverName());
                }

                world.addEntity(minecartentity);
            }

            stack.size--;
            return true;
        } else {
            return false;
        }
    }
}
