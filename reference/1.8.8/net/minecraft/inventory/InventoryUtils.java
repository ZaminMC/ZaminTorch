package net.minecraft.inventory;

import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class InventoryUtils {
    private static final Random RANDOM = new Random();

    public static void dropItems(World world, BlockPos pos, Inventory inventory) {
        dropItems(world, pos.getX(), pos.getY(), pos.getZ(), inventory);
    }

    public static void dropItems(World world, Entity owner, Inventory inventory) {
        dropItems(world, owner.x, owner.y, owner.z, inventory);
    }

    private static void dropItems(World world, double x, double y, double z, Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack itemstack = inventory.getItem(i);
            if (itemstack != null) {
                dropItem(world, x, y, z, itemstack);
            }
        }
    }

    private static void dropItem(World world, double x, double y, double z, ItemStack item) {
        float f = RANDOM.nextFloat() * 0.8F + 0.1F;
        float f1 = RANDOM.nextFloat() * 0.8F + 0.1F;
        float f2 = RANDOM.nextFloat() * 0.8F + 0.1F;

        while (item.size > 0) {
            int i = RANDOM.nextInt(21) + 10;
            if (i > item.size) {
                i = item.size;
            }

            item.size -= i;
            ItemEntity itementity = new ItemEntity(world, x + f, y + f1, z + f2, new ItemStack(item.getItem(), i, item.getMetadata()));
            if (item.hasNbt()) {
                itementity.getItem().setNbt((NbtCompound)item.getNbt().copy());
            }

            float f3 = 0.05F;
            itementity.velocityX = RANDOM.nextGaussian() * f3;
            itementity.velocityY = RANDOM.nextGaussian() * f3 + 0.2F;
            itementity.velocityZ = RANDOM.nextGaussian() * f3;
            world.addEntity(itementity);
        }
    }
}
