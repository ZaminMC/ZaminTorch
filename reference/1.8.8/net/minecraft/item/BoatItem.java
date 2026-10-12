package net.minecraft.item;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class BoatItem extends Item {
    public BoatItem() {
        this.maxStackSize = 1;
        this.setCreativeModeTab(CreativeModeTab.TRANSPORTATION);
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        float f = 1.0F;
        float f1 = player.lastPitch + (player.pitch - player.lastPitch) * f;
        float f2 = player.lastYaw + (player.yaw - player.lastYaw) * f;
        double d0 = player.lastX + (player.x - player.lastX) * f;
        double d1 = player.lastY + (player.y - player.lastY) * f + player.getEyeHeight();
        double d2 = player.lastZ + (player.z - player.lastZ) * f;
        Vec3d vec3d = new Vec3d(d0, d1, d2);
        float f3 = MathHelper.cos(-f2 * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f4 = MathHelper.sin(-f2 * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f5 = -MathHelper.cos(-f1 * (float) (Math.PI / 180.0));
        float f6 = MathHelper.sin(-f1 * (float) (Math.PI / 180.0));
        float f7 = f4 * f5;
        float f8 = f6;
        float f9 = f3 * f5;
        double d3 = 5.0;
        Vec3d vec3d1 = vec3d.add(f7 * d3, f8 * d3, f9 * d3);
        HitResult hitresult = world.rayTrace(vec3d, vec3d1, true);
        if (hitresult == null) {
            return stack;
        }

        Vec3d vec3d2 = player.getRotationVec(f);
        boolean flag = false;
        float f10 = 1.0F;
        List<Entity> list = world.getEntities(player, player.getShape().expanded(vec3d2.x * d3, vec3d2.y * d3, vec3d2.z * d3).grown(f10, f10, f10));

        for (int i = 0; i < list.size(); i++) {
            Entity entity = list.get(i);
            if (entity.hasCollision()) {
                float f11 = entity.getPickRadius();
                Box box = entity.getShape().grown(f11, f11, f11);
                if (box.contains(vec3d)) {
                    flag = true;
                }
            }
        }

        if (flag) {
            return stack;
        }

        if (hitresult.type == HitResult.Type.BLOCK) {
            BlockPos blockpos = hitresult.getPos();
            if (world.getBlockState(blockpos).getBlock() == Blocks.SNOW_LAYER) {
                blockpos = blockpos.down();
            }

            BoatEntity boatentity = new BoatEntity(world, blockpos.getX() + 0.5F, blockpos.getY() + 1.0F, blockpos.getZ() + 0.5F);
            boatentity.yaw = ((MathHelper.floor(player.yaw * 4.0F / 360.0F + 0.5) & 3) - 1) * 90;
            if (!world.getCollisions(boatentity, boatentity.getShape().grown(-0.1, -0.1, -0.1)).isEmpty()) {
                return stack;
            }

            if (!world.isClient) {
                world.addEntity(boatentity);
            }

            if (!player.abilities.creativeMode) {
                stack.size--;
            }

            player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
        }

        return stack;
    }
}
