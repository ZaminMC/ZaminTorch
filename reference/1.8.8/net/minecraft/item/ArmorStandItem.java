package net.minecraft.item;

import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Rotation;
import net.minecraft.world.World;

public class ArmorStandItem extends Item {
    public ArmorStandItem() {
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (face == Direction.DOWN) {
            return false;
        }

        boolean flag = world.getBlockState(pos).getBlock().canBeReplaced(world, pos);
        BlockPos blockpos = flag ? pos : pos.offset(face);
        if (!player.canUseItemOn(blockpos, face, stack)) {
            return false;
        }

        BlockPos blockpos1 = blockpos.up();
        boolean flag1 = !world.isAir(blockpos) && !world.getBlockState(blockpos).getBlock().canBeReplaced(world, blockpos);
        flag1 |= !world.isAir(blockpos1) && !world.getBlockState(blockpos1).getBlock().canBeReplaced(world, blockpos1);
        if (flag1) {
            return false;
        }

        double d0 = blockpos.getX();
        double d1 = blockpos.getY();
        double d2 = blockpos.getZ();
        List<Entity> list = world.getEntities(null, Box.of(d0, d1, d2, d0 + 1.0, d1 + 2.0, d2 + 1.0));
        if (list.size() > 0) {
            return false;
        }

        if (!world.isClient) {
            world.removeBlock(blockpos);
            world.removeBlock(blockpos1);
            ArmorStandEntity armorstandentity = new ArmorStandEntity(world, d0 + 0.5, d1, d2 + 0.5);
            float f = MathHelper.floor((MathHelper.wrapDegrees(player.yaw - 180.0F) + 22.5F) / 45.0F) * 45.0F;
            armorstandentity.setPositionAndAngles(d0 + 0.5, d1, d2 + 0.5, f, 0.0F);
            this.pickRotation(armorstandentity, world.random);
            NbtCompound nbtcompound = stack.getNbt();
            if (nbtcompound != null && nbtcompound.contains("EntityTag", 10)) {
                NbtCompound nbtcompound1 = new NbtCompound();
                armorstandentity.writeNbtIfNotMount(nbtcompound1);
                nbtcompound1.merge(nbtcompound.getCompound("EntityTag"));
                armorstandentity.readNbt(nbtcompound1);
            }

            world.addEntity(armorstandentity);
        }

        stack.size--;
        return true;
    }

    private void pickRotation(ArmorStandEntity armorStand, Random random) {
        Rotation rotation = armorStand.getHeadRotation();
        float f = random.nextFloat() * 5.0F;
        float f1 = random.nextFloat() * 20.0F - 10.0F;
        Rotation rotation1 = new Rotation(rotation.getPitch() + f, rotation.getYaw() + f1, rotation.getRoll());
        armorStand.setHeadRotation(rotation1);
        rotation = armorStand.getBodyRotation();
        f = random.nextFloat() * 10.0F - 5.0F;
        rotation1 = new Rotation(rotation.getPitch(), rotation.getYaw() + f, rotation.getRoll());
        armorStand.setBodyRotation(rotation1);
    }
}
