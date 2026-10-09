package net.minecraft.block;

import net.minecraft.block.dispenser.DispenseBehavior;
import net.minecraft.block.dispenser.DispenseItemBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockSource;
import net.minecraft.world.World;

public class DropperBlock extends DispenserBlock {
    private final DispenseBehavior dispenseBehavior = new DispenseItemBehavior();

    @Override
    protected DispenseBehavior getDispenseBehavior(ItemStack item) {
        return this.dispenseBehavior;
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new DropperBlockEntity();
    }

    @Override
    protected void dispense(World world, BlockPos pos) {
        BlockSource blocksource = new BlockSource(world, pos);
        DispenserBlockEntity dispenserblockentity = blocksource.getBlockEntity();
        if (dispenserblockentity != null) {
            int i = dispenserblockentity.pickNonEmptySlot();
            if (i < 0) {
                world.doEvent(1001, pos, 0);
            } else {
                ItemStack itemstack = dispenserblockentity.getItem(i);
                if (itemstack != null) {
                    Direction direction = world.getBlockState(pos).get(FACING);
                    BlockPos blockpos = pos.offset(direction);
                    Inventory inventory = HopperBlockEntity.getInventoryAt(world, blockpos.getX(), blockpos.getY(), blockpos.getZ());
                    ItemStack itemstack1;
                    if (inventory == null) {
                        itemstack1 = this.dispenseBehavior.dispense(blocksource, itemstack);
                        if (itemstack1 != null && itemstack1.size <= 0) {
                            itemstack1 = null;
                        }
                    } else {
                        itemstack1 = HopperBlockEntity.pushItem(inventory, itemstack.copy().split(1), direction.getOpposite());
                        if (itemstack1 == null) {
                            itemstack1 = itemstack.copy();
                            if (--itemstack1.size <= 0) {
                                itemstack1 = null;
                            }
                        } else {
                            itemstack1 = itemstack.copy();
                        }
                    }

                    dispenserblockentity.setItem(i, itemstack1);
                }
            }
        }
    }
}
