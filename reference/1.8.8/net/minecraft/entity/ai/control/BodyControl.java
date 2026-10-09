package net.minecraft.entity.ai.control;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.MathHelper;

public class BodyControl {
    private LivingEntity entity;
    private int activeTicks;
    private float lastHeadYaw;

    public BodyControl(LivingEntity entity) {
        this.entity = entity;
    }

    public void tick() {
        double d0 = this.entity.x - this.entity.lastX;
        double d1 = this.entity.z - this.entity.lastZ;
        if (d0 * d0 + d1 * d1 > 2.5000003E-7F) {
            this.entity.bodyYaw = this.entity.yaw;
            this.entity.headYaw = this.clampAndWrapAngle(this.entity.bodyYaw, this.entity.headYaw, 75.0F);
            this.lastHeadYaw = this.entity.headYaw;
            this.activeTicks = 0;
        } else {
            float f = 75.0F;
            if (Math.abs(this.entity.headYaw - this.lastHeadYaw) > 15.0F) {
                this.activeTicks = 0;
                this.lastHeadYaw = this.entity.headYaw;
            } else {
                this.activeTicks++;
                int i = 10;
                if (this.activeTicks > 10) {
                    f = Math.max(1.0F - (this.activeTicks - 10) / 10.0F, 0.0F) * 75.0F;
                }
            }

            this.entity.bodyYaw = this.clampAndWrapAngle(this.entity.headYaw, this.entity.bodyYaw, f);
        }
    }

    /**
     * @param maxOffset The maximum offset that is allowed between the yaw1 and the yaw2
     */
    private float clampAndWrapAngle(float yaw1, float yaw2, float maxOffset) {
        float f = MathHelper.wrapDegrees(yaw1 - yaw2);
        if (f < -maxOffset) {
            f = -maxOffset;
        }

        if (f >= maxOffset) {
            f = maxOffset;
        }

        return yaw1 - f;
    }
}
