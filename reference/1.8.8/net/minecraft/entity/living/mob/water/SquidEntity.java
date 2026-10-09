package net.minecraft.entity.living.mob.water;

import net.minecraft.block.material.Material;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class SquidEntity extends WaterMobEntity {
    public float bodyPitch;
    public float lastBodyPitch;
    public float bodyRoll;
    public float lastBodyRoll;
    public float tentacleMovement;
    public float lastTentacleMovement;
    public float tentacleRotation;
    public float lastTentacleRotation;
    private float speed;
    private float tentacleSpeed;
    private float rotationSpeed;
    private float targetX;
    private float targetY;
    private float targetZ;

    public SquidEntity(World world) {
        super(world);
        this.setSize(0.95F, 0.95F);
        this.random.setSeed(1 + this.getNetworkId());
        this.tentacleSpeed = 1.0F / (this.random.nextFloat() + 1.0F) * 0.2F;
        this.goalSelector.addGoal(0, new SquidEntity.SwimAroundGoal(this));
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(10.0);
    }

    @Override
    public float getEyeHeight() {
        return this.height * 0.5F;
    }

    @Override
    protected String getAmbientSound() {
        return null;
    }

    @Override
    protected String getHurtSound() {
        return null;
    }

    @Override
    protected String getDeathSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected Item getDropItem() {
        return null;
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(3 + lootingMultiplier) + 1;

        for (int j = 0; j < i; j++) {
            this.dropItem(new ItemStack(Items.DYE, 1, DyeColor.BLACK.getMetadata()), 0.0F);
        }
    }

    @Override
    public boolean isInWater() {
        return this.world.applyLiquidDrag(this.getShape().grown(0.0, -0.6F, 0.0), Material.WATER, this);
    }

    @Override
    public void mobTick() {
        super.mobTick();
        this.lastBodyPitch = this.bodyPitch;
        this.lastBodyRoll = this.bodyRoll;
        this.lastTentacleMovement = this.tentacleMovement;
        this.lastTentacleRotation = this.tentacleRotation;
        this.tentacleMovement = this.tentacleMovement + this.tentacleSpeed;
        if (this.tentacleMovement > Math.PI * 2) {
            if (this.world.isClient) {
                this.tentacleMovement = (float) (Math.PI * 2);
            } else {
                this.tentacleMovement = (float)(this.tentacleMovement - (Math.PI * 2));
                if (this.random.nextInt(10) == 0) {
                    this.tentacleSpeed = 1.0F / (this.random.nextFloat() + 1.0F) * 0.2F;
                }

                this.world.doEntityEvent(this, (byte)19);
            }
        }

        if (this.inWater) {
            if (this.tentacleMovement < (float) Math.PI) {
                float f = this.tentacleMovement / (float) Math.PI;
                this.tentacleRotation = MathHelper.sin(f * f * (float) Math.PI) * (float) Math.PI * 0.25F;
                if (f > 0.75) {
                    this.speed = 1.0F;
                    this.rotationSpeed = 1.0F;
                } else {
                    this.rotationSpeed *= 0.8F;
                }
            } else {
                this.tentacleRotation = 0.0F;
                this.speed *= 0.9F;
                this.rotationSpeed *= 0.99F;
            }

            if (!this.world.isClient) {
                this.velocityX = this.targetX * this.speed;
                this.velocityY = this.targetY * this.speed;
                this.velocityZ = this.targetZ * this.speed;
            }

            float f1 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
            this.bodyYaw = this.bodyYaw + (-((float)MathHelper.fastAtan2(this.velocityX, this.velocityZ)) * 180.0F / (float) Math.PI - this.bodyYaw) * 0.1F;
            this.yaw = this.bodyYaw;
            this.bodyRoll = (float)(this.bodyRoll + Math.PI * this.rotationSpeed * 1.5);
            this.bodyPitch = this.bodyPitch + (-((float)MathHelper.fastAtan2(f1, this.velocityY)) * 180.0F / (float) Math.PI - this.bodyPitch) * 0.1F;
        } else {
            this.tentacleRotation = MathHelper.abs(MathHelper.sin(this.tentacleMovement)) * (float) Math.PI * 0.25F;
            if (!this.world.isClient) {
                this.velocityX = 0.0;
                this.velocityY -= 0.08;
                this.velocityY *= 0.98F;
                this.velocityZ = 0.0;
            }

            this.bodyPitch = (float)(this.bodyPitch + (-90.0F - this.bodyPitch) * 0.02);
        }
    }

    @Override
    public void moveRelative(float sideways, float forwards) {
        this.move(this.velocityX, this.velocityY, this.velocityZ);
    }

    @Override
    public boolean canSpawn() {
        return this.y > 45.0 && this.y < this.world.getSeaLevel() && super.canSpawn();
    }

    @Override
    public void doEvent(byte event) {
        if (event == 19) {
            this.tentacleMovement = 0.0F;
        } else {
            super.doEvent(event);
        }
    }

    public void setTarget(float x, float y, float z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    public boolean hasTarget() {
        return this.targetX != 0.0F || this.targetY != 0.0F || this.targetZ != 0.0F;
    }

    static class SwimAroundGoal extends Goal {
        private SquidEntity squid;

        public SwimAroundGoal(SquidEntity squid) {
            this.squid = squid;
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public void tick() {
            int i = this.squid.getDespawnTimer();
            if (i > 100) {
                this.squid.setTarget(0.0F, 0.0F, 0.0F);
            } else if (this.squid.getRandom().nextInt(50) == 0 || !this.squid.inWater || !this.squid.hasTarget()) {
                float f = this.squid.getRandom().nextFloat() * (float) Math.PI * 2.0F;
                float f1 = MathHelper.cos(f) * 0.2F;
                float f2 = -0.1F + this.squid.getRandom().nextFloat() * 0.2F;
                float f3 = MathHelper.sin(f) * 0.2F;
                this.squid.setTarget(f1, f2, f3);
            }
        }
    }
}
