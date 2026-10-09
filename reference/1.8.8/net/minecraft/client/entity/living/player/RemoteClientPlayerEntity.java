package net.minecraft.client.entity.living.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class RemoteClientPlayerEntity extends ClientPlayerEntity {
    private boolean usingItem;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;

    public RemoteClientPlayerEntity(World world, GameProfile gameProfile) {
        super(world, gameProfile);
        this.stepHeight = 0.0F;
        this.noClip = true;
        this.sleepingCameraOffsetY = 0.25F;
        this.viewDistanceScaling = 10.0;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        return true;
    }

    @Override
    public void lerpPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpPitch = pitch;
        this.lerpSteps = steps;
    }

    @Override
    public void tick() {
        this.sleepingCameraOffsetY = 0.0F;
        super.tick();
        this.lastWalkAnimationSpeed = this.walkAnimationSpeed;
        double d0 = this.x - this.lastX;
        double d1 = this.z - this.lastZ;
        float f = MathHelper.sqrt(d0 * d0 + d1 * d1) * 4.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }

        this.walkAnimationSpeed = this.walkAnimationSpeed + (f - this.walkAnimationSpeed) * 0.4F;
        this.walkAnimationProgress = this.walkAnimationProgress + this.walkAnimationSpeed;
        if (!this.usingItem && this.isUsingItem() && this.inventory.items[this.inventory.selectedSlot] != null) {
            ItemStack itemstack = this.inventory.items[this.inventory.selectedSlot];
            this.setItemInUse(this.inventory.items[this.inventory.selectedSlot], itemstack.getItem().getUseDuration(itemstack));
            this.usingItem = true;
        } else if (this.usingItem && !this.isUsingItem()) {
            this.clearItemInUse();
            this.usingItem = false;
        }
    }

    @Override
    public void mobTick() {
        if (this.lerpSteps > 0) {
            double d0 = this.x + (this.lerpX - this.x) / this.lerpSteps;
            double d1 = this.y + (this.lerpY - this.y) / this.lerpSteps;
            double d2 = this.z + (this.lerpZ - this.z) / this.lerpSteps;
            double d3 = this.lerpYaw - this.yaw;

            while (d3 < -180.0) {
                d3 += 360.0;
            }

            while (d3 >= 180.0) {
                d3 -= 360.0;
            }

            this.yaw = (float)(this.yaw + d3 / this.lerpSteps);
            this.pitch = (float)(this.pitch + (this.lerpPitch - this.pitch) / this.lerpSteps);
            this.lerpSteps--;
            this.setPosition(d0, d1, d2);
            this.setRotation(this.yaw, this.pitch);
        }

        this.lastBob = this.bob;
        this.updateArmSwing();
        float f1 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
        float f = (float)Math.atan(-this.velocityY * 0.2F) * 15.0F;
        if (f1 > 0.1F) {
            f1 = 0.1F;
        }

        if (!this.onGround || this.getHealth() <= 0.0F) {
            f1 = 0.0F;
        }

        if (this.onGround || this.getHealth() <= 0.0F) {
            f = 0.0F;
        }

        this.bob = this.bob + (f1 - this.bob) * 0.4F;
        this.tilt = this.tilt + (f - this.tilt) * 0.8F;
    }

    @Override
    public void setEquipment(int slot, ItemStack item) {
        if (slot == 0) {
            this.inventory.items[this.inventory.selectedSlot] = item;
        } else {
            this.inventory.armor[slot - 1] = item;
        }
    }

    @Override
    public void sendMessage(Text message) {
        Minecraft.getInstance().gui.getChat().addMessage(message);
    }

    @Override
    public boolean canUseCommand(int permissionLevel, String command) {
        return false;
    }

    @Override
    public BlockPos getCommandSourceBlockPos() {
        return new BlockPos(this.x + 0.5, this.y + 0.5, this.z + 0.5);
    }
}
