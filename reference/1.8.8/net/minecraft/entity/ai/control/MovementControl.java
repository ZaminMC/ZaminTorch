package net.minecraft.entity.ai.control;

import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.util.math.MathHelper;

public class MovementControl {
    protected MobEntity mob;
    protected double x;
    protected double y;
    protected double z;
    protected double speed;
    protected boolean moving;

    public MovementControl(MobEntity mob) {
        this.mob = mob;
        this.x = mob.x;
        this.y = mob.y;
        this.z = mob.z;
    }

    public boolean isMoving() {
        return this.moving;
    }

    public double getSpeed() {
        return this.speed;
    }

    public void update(double x, double y, double z, double speed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.speed = speed;
        this.moving = true;
    }

    public void tick() {
        this.mob.setForwardsSpeed(0.0F);
        if (this.moving) {
            this.moving = false;
            int i = MathHelper.floor(this.mob.getShape().minY + 0.5);
            double d0 = this.x - this.mob.x;
            double d1 = this.z - this.mob.z;
            double d2 = this.y - i;
            double d3 = d0 * d0 + d2 * d2 + d1 * d1;
            if (!(d3 < 2.5000003E-7F)) {
                float f = (float)(MathHelper.fastAtan2(d1, d0) * 180.0 / (float) Math.PI) - 90.0F;
                this.mob.yaw = this.clampAndWrapAngle(this.mob.yaw, f, 30.0F);
                this.mob.setSpeed((float)(this.speed * this.mob.getAttribute(EntityAttributes.MOVEMENT_SPEED).get()));
                if (d2 > 0.0 && d0 * d0 + d1 * d1 < 1.0) {
                    this.mob.getJumpControl().setActive();
                }
            }
        }
    }

    /**
     * @param maxOffset The maximum offset that is allowed between the yaw1 and yaw2
     */
    protected float clampAndWrapAngle(float yaw1, float yaw2, float maxOffset) {
        float f = MathHelper.wrapDegrees(yaw2 - yaw1);
        if (f > maxOffset) {
            f = maxOffset;
        }

        if (f < -maxOffset) {
            f = -maxOffset;
        }

        float f1 = yaw1 + f;
        if (f1 < 0.0F) {
            f1 += 360.0F;
        } else if (f1 > 360.0F) {
            f1 -= 360.0F;
        }

        return f1;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }
}
