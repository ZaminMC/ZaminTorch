package net.minecraft.block.dispenser;

import net.minecraft.block.DispenserBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.IPosition;
import net.minecraft.world.IBlockSource;
import net.minecraft.world.World;

public class DispenseItemBehavior implements DispenseBehavior {
    @Override
    public final ItemStack dispense(IBlockSource source, ItemStack item) {
        ItemStack itemstack = this.dispenseItem(source, item);
        this.playSound(source);
        this.doWorldEvent(source, DispenserBlock.getDirection(source.getBlockMetadata()));
        return itemstack;
    }

    protected ItemStack dispenseItem(IBlockSource source, ItemStack item) {
        Direction direction = DispenserBlock.getDirection(source.getBlockMetadata());
        IPosition iposition = DispenserBlock.getDispensePos(source);
        ItemStack itemstack = item.split(1);
        spawnItem(source.getWorld(), itemstack, 6, direction, iposition);
        return item;
    }

    public static void spawnItem(World world, ItemStack item, int offset, Direction facing, IPosition pos) {
        double d0 = pos.getX();
        double d1 = pos.getY();
        double d2 = pos.getZ();
        if (facing.getAxis() == Direction.Axis.Y) {
            d1 -= 0.125;
        } else {
            d1 -= 0.15625;
        }

        ItemEntity itementity = new ItemEntity(world, d0, d1, d2, item);
        double d3 = world.random.nextDouble() * 0.1 + 0.2;
        itementity.velocityX = facing.getOffsetX() * d3;
        itementity.velocityY = 0.2F;
        itementity.velocityZ = facing.getOffsetZ() * d3;
        itementity.velocityX = itementity.velocityX + world.random.nextGaussian() * 0.0075F * offset;
        itementity.velocityY = itementity.velocityY + world.random.nextGaussian() * 0.0075F * offset;
        itementity.velocityZ = itementity.velocityZ + world.random.nextGaussian() * 0.0075F * offset;
        world.addEntity(itementity);
    }

    protected void playSound(IBlockSource source) {
        source.getWorld().doEvent(1000, source.getPos(), 0);
    }

    protected void doWorldEvent(IBlockSource source, Direction facing) {
        source.getWorld().doEvent(2000, source.getPos(), this.toInt(facing));
    }

    private int toInt(Direction dir) {
        return dir.getOffsetX() + 1 + (dir.getOffsetZ() + 1) * 3;
    }
}
