package net.minecraft.entity.ai.goal;

import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.ai.pathing.WalkNodeEvaluator;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class PlayerControlGoal extends Goal {
    private final MobEntity mob;
    private final float maxVelocity;
    private float speed;
    private boolean moving;
    private int movingTicks;
    private int maxMovingTicks;

    public PlayerControlGoal(MobEntity mob, float maxVelocity) {
        this.mob = mob;
        this.maxVelocity = maxVelocity;
        this.setControls(7);
    }

    @Override
    public void start() {
        this.speed = 0.0F;
    }

    @Override
    public void stop() {
        this.moving = false;
        this.speed = 0.0F;
    }

    @Override
    public boolean canStart() {
        return this.mob.isAlive() && this.mob.rider != null && this.mob.rider instanceof PlayerEntity && (this.moving || this.mob.canBeControlledByRider());
    }

    @Override
    public void tick() {
        PlayerEntity playerentity = (PlayerEntity)this.mob.rider;
        PathFinderMobEntity pathfindermobentity = (PathFinderMobEntity)this.mob;
        float f = MathHelper.wrapDegrees(playerentity.yaw - this.mob.yaw) * 0.5F;
        if (f > 5.0F) {
            f = 5.0F;
        }

        if (f < -5.0F) {
            f = -5.0F;
        }

        this.mob.yaw = MathHelper.wrapDegrees(this.mob.yaw + f);
        if (this.speed < this.maxVelocity) {
            this.speed = this.speed + (this.maxVelocity - this.speed) * 0.01F;
        }

        if (this.speed > this.maxVelocity) {
            this.speed = this.maxVelocity;
        }

        int i = MathHelper.floor(this.mob.x);
        int j = MathHelper.floor(this.mob.y);
        int k = MathHelper.floor(this.mob.z);
        float f1 = this.speed;
        if (this.moving) {
            if (this.movingTicks++ > this.maxMovingTicks) {
                this.moving = false;
            }

            f1 += f1 * 1.15F * MathHelper.sin((float)this.movingTicks / this.maxMovingTicks * (float) Math.PI);
        }

        float f2 = 0.91F;
        if (this.mob.onGround) {
            f2 = this.mob.world.getBlockState(new BlockPos(MathHelper.floor(i), MathHelper.floor(j) - 1, MathHelper.floor(k))).getBlock().slipperiness * 0.91F;
        }

        float f3 = 0.16277136F / (f2 * f2 * f2);
        float f4 = MathHelper.sin(pathfindermobentity.yaw * (float) Math.PI / 180.0F);
        float f5 = MathHelper.cos(pathfindermobentity.yaw * (float) Math.PI / 180.0F);
        float f6 = pathfindermobentity.getSpeed() * f3;
        float f7 = Math.max(f1, 1.0F);
        f7 = f6 / f7;
        float f8 = f1 * f7;
        float f9 = -(f8 * f4);
        float f10 = f8 * f5;
        if (MathHelper.abs(f9) > MathHelper.abs(f10)) {
            if (f9 < 0.0F) {
                f9 -= this.mob.width / 2.0F;
            }

            if (f9 > 0.0F) {
                f9 += this.mob.width / 2.0F;
            }

            f10 = 0.0F;
        } else {
            f9 = 0.0F;
            if (f10 < 0.0F) {
                f10 -= this.mob.width / 2.0F;
            }

            if (f10 > 0.0F) {
                f10 += this.mob.width / 2.0F;
            }
        }

        int l = MathHelper.floor(this.mob.x + f9);
        int i1 = MathHelper.floor(this.mob.z + f10);
        int j1 = MathHelper.floor(this.mob.width + 1.0F);
        int k1 = MathHelper.floor(this.mob.height + playerentity.height + 1.0F);
        int l1 = MathHelper.floor(this.mob.width + 1.0F);
        if (i != l || k != i1) {
            Block block = this.mob.world.getBlockState(new BlockPos(i, j, k)).getBlock();
            boolean flag = !this.canStepOnto(block)
                && (block.getMaterial() != Material.AIR || !this.canStepOnto(this.mob.world.getBlockState(new BlockPos(i, j - 1, k)).getBlock()));
            if (flag
                && 0 == WalkNodeEvaluator.getBlockingType(this.mob.world, this.mob, l, j, i1, j1, k1, l1, false, false, true)
                && 1 == WalkNodeEvaluator.getBlockingType(this.mob.world, this.mob, i, j + 1, k, j1, k1, l1, false, false, true)
                && 1 == WalkNodeEvaluator.getBlockingType(this.mob.world, this.mob, l, j + 1, i1, j1, k1, l1, false, false, true)) {
                pathfindermobentity.getJumpControl().setActive();
            }
        }

        if (!playerentity.abilities.creativeMode && this.speed >= this.maxVelocity * 0.5F && this.mob.getRandom().nextFloat() < 0.006F && !this.moving) {
            ItemStack itemstack = playerentity.getDisplayItemInHand();
            if (itemstack != null && itemstack.getItem() == Items.CARROT_ON_A_STICK) {
                itemstack.takeDamageAndBreak(1, playerentity);
                if (itemstack.size == 0) {
                    ItemStack itemstack1 = new ItemStack(Items.FISHING_ROD);
                    itemstack1.setNbt(itemstack.getNbt());
                    playerentity.inventory.items[playerentity.inventory.selectedSlot] = itemstack1;
                }
            }
        }

        this.mob.moveRelative(0.0F, f1);
    }

    private boolean canStepOnto(Block block) {
        return block instanceof StairsBlock || block instanceof SlabBlock;
    }

    public boolean isMoving() {
        return this.moving;
    }

    public void startMoving() {
        this.moving = true;
        this.movingTicks = 0;
        this.maxMovingTicks = this.mob.getRandom().nextInt(841) + 140;
    }

    public boolean canStartMoving() {
        return !this.isMoving() && this.speed > this.maxVelocity * 0.3F;
    }
}
