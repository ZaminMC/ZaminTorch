package net.minecraft.entity.ai.control;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.util.math.MathHelper;

public class LookControl {
    private MobEntity mob;
    private float yaw;
    private float pitch;
    private boolean active;
    private double lookX;
    private double lookY;
    private double lookZ;

    public LookControl(MobEntity mob) {
        this.mob = mob;
    }

    public void setLookatValues(Entity entity, float yaw, float pitch) {
        this.lookX = entity.x;
        if (entity instanceof LivingEntity) {
            this.lookY = entity.y + entity.getEyeHeight();
        } else {
            this.lookY = (entity.getShape().minY + entity.getShape().maxY) / 2.0;
        }

        this.lookZ = entity.z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.active = true;
    }

    public void lookAt(double x, double y, double z, float yaw, float pitch) {
        this.lookX = x;
        this.lookY = y;
        this.lookZ = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.active = true;
    }

    public void tick() {
        this.mob.pitch = 0.0F;
        if (this.active) {
            this.active = false;
            double d0 = this.lookX - this.mob.x;
            double d1 = this.lookY - (this.mob.y + this.mob.getEyeHeight());
            double d2 = this.lookZ - this.mob.z;
            double d3 = MathHelper.sqrt(d0 * d0 + d2 * d2);
            float f = (float)(MathHelper.fastAtan2(d2, d0) * 180.0 / (float) Math.PI) - 90.0F;
            float f1 = (float)(-(MathHelper.fastAtan2(d1, d3) * 180.0 / (float) Math.PI));
            this.mob.pitch = this.clampAndWrapAngle(this.mob.pitch, f1, this.pitch);
            this.mob.headYaw = this.clampAndWrapAngle(this.mob.headYaw, f, this.yaw);
        } else {
            this.mob.headYaw = this.clampAndWrapAngle(this.mob.headYaw, this.mob.bodyYaw, 10.0F);
        }

        float f2 = MathHelper.wrapDegrees(this.mob.headYaw - this.mob.bodyYaw);
        if (!this.mob.getNavigation().isDone()) {
            if (f2 < -75.0F) {
                this.mob.headYaw = this.mob.bodyYaw - 75.0F;
            }

            if (f2 > 75.0F) {
                this.mob.headYaw = this.mob.bodyYaw + 75.0F;
            }
        }
    }

    /**
     * @param maxOffset The maximum offset that is allowed between the yaw1 and yaw2
     */
    private float clampAndWrapAngle(float yaw1, float yaw2, float maxOffset) {
        float f = MathHelper.wrapDegrees(yaw2 - yaw1);
        if (f > maxOffset) {
            f = maxOffset;
        }

        if (f < -maxOffset) {
            f = -maxOffset;
        }

        return yaw1 + f;
    }

    public boolean isLookingAtSpecificPosition() {
        return this.active;
    }

    public double getLookX() {
        return this.lookX;
    }

    public double getLookY() {
        return this.lookY;
    }

    public double getLookZ() {
        return this.lookZ;
    }
}
