package net.minecraft.entity.decoration;

import net.minecraft.block.FenceBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class LeadKnotEntity extends DecorationEntity {
    public LeadKnotEntity(World world) {
        super(world);
    }

    public LeadKnotEntity(World world, BlockPos blockPos) {
        super(world, blockPos);
        this.setPosition(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        float f = 0.125F;
        float f1 = 0.1875F;
        float f2 = 0.25F;
        this.setShape(new Box(this.x - 0.1875, this.y - 0.25 + 0.125, this.z - 0.1875, this.x + 0.1875, this.y + 0.25 + 0.125, this.z + 0.1875));
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
    }

    @Override
    public void setDirection(Direction dir) {
    }

    @Override
    public int getWidth() {
        return 9;
    }

    @Override
    public int getHeight() {
        return 9;
    }

    @Override
    public float getEyeHeight() {
        return -0.0625F;
    }

    @Override
    public boolean shouldRender(double squaredDistanceToCamera) {
        return squaredDistanceToCamera < 1024.0;
    }

    @Override
    public void onAttack(Entity entity) {
    }

    @Override
    public boolean writeNbtIfNotMount(NbtCompound nbt) {
        return false;
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
    }

    @Override
    public boolean interact(PlayerEntity player) {
        ItemStack itemstack = player.getDisplayItemInHand();
        boolean flag = false;
        if (itemstack != null && itemstack.getItem() == Items.LEAD && !this.world.isClient) {
            double d0 = 7.0;

            for (MobEntity mobentity : this.world
                .getEntitiesOfType(MobEntity.class, new Box(this.x - d0, this.y - d0, this.z - d0, this.x + d0, this.y + d0, this.z + d0))) {
                if (mobentity.isLeashed() && mobentity.getLeashHolder() == player) {
                    mobentity.attachLeash(this, true);
                    flag = true;
                }
            }
        }

        if (!this.world.isClient && !flag) {
            this.remove();
            if (player.abilities.creativeMode) {
                double d1 = 7.0;

                for (MobEntity mobentity1 : this.world
                    .getEntitiesOfType(MobEntity.class, new Box(this.x - d1, this.y - d1, this.z - d1, this.x + d1, this.y + d1, this.z + d1))) {
                    if (mobentity1.isLeashed() && mobentity1.getLeashHolder() == this) {
                        mobentity1.detachLeash(true, false);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public boolean canSurvive() {
        return this.world.getBlockState(this.pos).getBlock() instanceof FenceBlock;
    }

    public static LeadKnotEntity attatch(World world, BlockPos pos) {
        LeadKnotEntity leadknotentity = new LeadKnotEntity(world, pos);
        leadknotentity.teleporting = true;
        world.addEntity(leadknotentity);
        return leadknotentity;
    }

    public static LeadKnotEntity getOrCreate(World world, BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        for (LeadKnotEntity leadknotentity : world.getEntitiesOfType(LeadKnotEntity.class, new Box(i - 1.0, j - 1.0, k - 1.0, i + 1.0, j + 1.0, k + 1.0))) {
            if (leadknotentity.getBlockPos().equals(pos)) {
                return leadknotentity;
            }
        }

        return null;
    }
}
