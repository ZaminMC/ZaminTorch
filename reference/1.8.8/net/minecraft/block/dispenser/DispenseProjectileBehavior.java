package net.minecraft.block.dispenser;

import net.minecraft.block.DispenserBlock;
import net.minecraft.entity.Dispensable;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.IPosition;
import net.minecraft.world.IBlockSource;
import net.minecraft.world.World;

public abstract class DispenseProjectileBehavior extends DispenseItemBehavior {
    @Override
    public ItemStack dispenseItem(IBlockSource source, ItemStack item) {
        World world = source.getWorld();
        IPosition iposition = DispenserBlock.getDispensePos(source);
        Direction direction = DispenserBlock.getDirection(source.getBlockMetadata());
        Dispensable dispensable = this.createProjectile(world, iposition);
        dispensable.dispense(direction.getOffsetX(), direction.getOffsetY() + 0.1F, direction.getOffsetZ(), this.getForce(), this.getVariation());
        world.addEntity((Entity)dispensable);
        item.split(1);
        return item;
    }

    @Override
    protected void playSound(IBlockSource source) {
        source.getWorld().doEvent(1002, source.getPos(), 0);
    }

    protected abstract Dispensable createProjectile(World world, IPosition pos);

    protected float getVariation() {
        return 6.0F;
    }

    protected float getForce() {
        return 1.1F;
    }
}
