package net.minecraft.entity.living.mob.monster.boss;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EnderCrystalEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.EntityDamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.Monster;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EnderDragonEntity extends MobEntity implements Boss, EnderDragon, Monster {
    public double targetX;
    public double targetY;
    public double targetZ;
    /**
     * Contains 64 sets of (yaw, y) corresponding to the last 64 Y positions and the
     * last 63 yaws. This is used for animations.
     */
    public double[][] circularSegmentBuffer = new double[64][3];
    /**
     * Increments by 1 each tick. This integer corresponds to the position in the circular buffer
     * and loops back to 0 when it reached the end to the buffer. this way the buffer always stores the last
     * 64 (yaw, y) sets where the latest one is at this index.
     */
    public int latestSegment = -1;
    public EnderDragonPart[] parts;
    public EnderDragonPart head;
    public EnderDragonPart body;
    public EnderDragonPart tailBase;
    public EnderDragonPart tailMiddle;
    public EnderDragonPart tailEnd;
    public EnderDragonPart rightWing;
    public EnderDragonPart leftWing;
    public float lastWingPosition;
    public float wingPosition;
    public boolean needsNewTarget;
    /**
     * Is set to true if the dragon is colliding with obsidian, end stone or bedrock, which slows down the dragon
     */
    public boolean restrictMovement;
    private Entity target;
    public int ticksSinceDeath;
    public EnderCrystalEntity connectedCrystal;

    public EnderDragonEntity(World world) {
        super(world);
        this.parts = new EnderDragonPart[]{
            this.head = new EnderDragonPart(this, "head", 6.0F, 6.0F),
            this.body = new EnderDragonPart(this, "body", 8.0F, 8.0F),
            this.tailBase = new EnderDragonPart(this, "tail", 4.0F, 4.0F),
            this.tailMiddle = new EnderDragonPart(this, "tail", 4.0F, 4.0F),
            this.tailEnd = new EnderDragonPart(this, "tail", 4.0F, 4.0F),
            this.rightWing = new EnderDragonPart(this, "wing", 4.0F, 4.0F),
            this.leftWing = new EnderDragonPart(this, "wing", 4.0F, 4.0F)
        };
        this.setHealth(this.getMaxHealth());
        this.setSize(16.0F, 8.0F);
        this.noClip = true;
        this.immuneToFire = true;
        this.targetY = 100.0;
        this.ignoreCameraFrustum = true;
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(200.0);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
    }

    public double[] getSegmentProperties(int segment, float tickDelta) {
        if (this.getHealth() <= 0.0F) {
            tickDelta = 0.0F;
        }

        tickDelta = 1.0F - tickDelta;
        int i = this.latestSegment - segment * 1 & 63;
        int j = this.latestSegment - segment * 1 - 1 & 63;
        double[] adouble = new double[3];
        double d0 = this.circularSegmentBuffer[i][0];
        double d1 = MathHelper.wrapDegrees(this.circularSegmentBuffer[j][0] - d0);
        adouble[0] = d0 + d1 * tickDelta;
        d0 = this.circularSegmentBuffer[i][1];
        d1 = this.circularSegmentBuffer[j][1] - d0;
        adouble[1] = d0 + d1 * tickDelta;
        adouble[2] = this.circularSegmentBuffer[i][2] + (this.circularSegmentBuffer[j][2] - this.circularSegmentBuffer[i][2]) * tickDelta;
        return adouble;
    }

    @Override
    public void mobTick() {
        if (this.world.isClient) {
            float f = MathHelper.cos(this.wingPosition * (float) Math.PI * 2.0F);
            float f1 = MathHelper.cos(this.lastWingPosition * (float) Math.PI * 2.0F);
            if (f1 <= -0.3F && f >= -0.3F && !this.isSilent()) {
                this.world.playSound(this.x, this.y, this.z, "mob.enderdragon.wings", 5.0F, 0.8F + this.random.nextFloat() * 0.3F, false);
            }
        }

        this.lastWingPosition = this.wingPosition;
        if (this.getHealth() <= 0.0F) {
            float f11 = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f13 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f14 = (this.random.nextFloat() - 0.5F) * 8.0F;
            this.world.addParticle(ParticleType.EXPLOSION_LARGE, this.x + f11, this.y + 2.0 + f13, this.z + f14, 0.0, 0.0, 0.0);
        } else {
            this.tickEndCrystalInteraction();
            float f10 = 0.2F / (MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ) * 10.0F + 1.0F);
            f10 *= (float)Math.pow(2.0, this.velocityY);
            if (this.restrictMovement) {
                this.wingPosition += f10 * 0.5F;
            } else {
                this.wingPosition += f10;
            }

            this.yaw = MathHelper.wrapDegrees(this.yaw);
            if (this.isNoAi()) {
                this.wingPosition = 0.5F;
            } else {
                if (this.latestSegment < 0) {
                    for (int i = 0; i < this.circularSegmentBuffer.length; i++) {
                        this.circularSegmentBuffer[i][0] = this.yaw;
                        this.circularSegmentBuffer[i][1] = this.y;
                    }
                }

                if (++this.latestSegment == this.circularSegmentBuffer.length) {
                    this.latestSegment = 0;
                }

                this.circularSegmentBuffer[this.latestSegment][0] = this.yaw;
                this.circularSegmentBuffer[this.latestSegment][1] = this.y;
                if (this.world.isClient) {
                    if (this.lerpSteps > 0) {
                        double d10 = this.x + (this.lerpX - this.x) / this.lerpSteps;
                        double d0 = this.y + (this.lerpY - this.y) / this.lerpSteps;
                        double d1 = this.z + (this.lerpZ - this.z) / this.lerpSteps;
                        double d2 = MathHelper.wrapDegrees(this.lerpYaw - this.yaw);
                        this.yaw = (float)(this.yaw + d2 / this.lerpSteps);
                        this.pitch = (float)(this.pitch + (this.lerpPitch - this.pitch) / this.lerpSteps);
                        this.lerpSteps--;
                        this.setPosition(d10, d0, d1);
                        this.setRotation(this.yaw, this.pitch);
                    }
                } else {
                    double d11 = this.targetX - this.x;
                    double d12 = this.targetY - this.y;
                    double d13 = this.targetZ - this.z;
                    double d14 = d11 * d11 + d12 * d12 + d13 * d13;
                    if (this.target != null) {
                        this.targetX = this.target.x;
                        this.targetZ = this.target.z;
                        double d3 = this.targetX - this.x;
                        double d5 = this.targetZ - this.z;
                        double d7 = Math.sqrt(d3 * d3 + d5 * d5);
                        double d8 = 0.4F + d7 / 80.0 - 1.0;
                        if (d8 > 10.0) {
                            d8 = 10.0;
                        }

                        this.targetY = this.target.getShape().minY + d8;
                    } else {
                        this.targetX = this.targetX + this.random.nextGaussian() * 2.0;
                        this.targetZ = this.targetZ + this.random.nextGaussian() * 2.0;
                    }

                    if (this.needsNewTarget || d14 < 100.0 || d14 > 22500.0 || this.collidingHorizontally || this.collidingVertically) {
                        this.chooseTarget();
                    }

                    d12 /= MathHelper.sqrt(d11 * d11 + d13 * d13);
                    float f17 = 0.6F;
                    d12 = MathHelper.clamp(d12, -f17, f17);
                    this.velocityY += d12 * 0.1F;
                    this.yaw = MathHelper.wrapDegrees(this.yaw);
                    double d4 = 180.0 - MathHelper.fastAtan2(d11, d13) * 180.0 / (float) Math.PI;
                    double d6 = MathHelper.wrapDegrees(d4 - this.yaw);
                    if (d6 > 50.0) {
                        d6 = 50.0;
                    }

                    if (d6 < -50.0) {
                        d6 = -50.0;
                    }

                    Vec3d vec3d = new Vec3d(this.targetX - this.x, this.targetY - this.y, this.targetZ - this.z).normalize();
                    double d15 = -MathHelper.cos(this.yaw * (float) Math.PI / 180.0F);
                    Vec3d vec3d1 = new Vec3d(MathHelper.sin(this.yaw * (float) Math.PI / 180.0F), this.velocityY, d15).normalize();
                    float f5 = ((float)vec3d1.dot(vec3d) + 0.5F) / 1.5F;
                    if (f5 < 0.0F) {
                        f5 = 0.0F;
                    }

                    this.rotationSpeed *= 0.8F;
                    float f6 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ) * 1.0F + 1.0F;
                    double d9 = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ) * 1.0 + 1.0;
                    if (d9 > 40.0) {
                        d9 = 40.0;
                    }

                    this.rotationSpeed = (float)(this.rotationSpeed + d6 * (0.7F / d9 / f6));
                    this.yaw = this.yaw + this.rotationSpeed * 0.1F;
                    float f7 = (float)(2.0 / (d9 + 1.0));
                    float f8 = 0.06F;
                    this.updateVelocity(0.0F, -1.0F, f8 * (f5 * f7 + (1.0F - f7)));
                    if (this.restrictMovement) {
                        this.move(this.velocityX * 0.8F, this.velocityY * 0.8F, this.velocityZ * 0.8F);
                    } else {
                        this.move(this.velocityX, this.velocityY, this.velocityZ);
                    }

                    Vec3d vec3d2 = new Vec3d(this.velocityX, this.velocityY, this.velocityZ).normalize();
                    float f9 = ((float)vec3d2.dot(vec3d1) + 1.0F) / 2.0F;
                    f9 = 0.8F + 0.15F * f9;
                    this.velocityX *= f9;
                    this.velocityZ *= f9;
                    this.velocityY *= 0.91F;
                }

                this.bodyYaw = this.yaw;
                this.head.width = this.head.height = 3.0F;
                this.tailBase.width = this.tailBase.height = 2.0F;
                this.tailMiddle.width = this.tailMiddle.height = 2.0F;
                this.tailEnd.width = this.tailEnd.height = 2.0F;
                this.body.height = 3.0F;
                this.body.width = 5.0F;
                this.rightWing.height = 2.0F;
                this.rightWing.width = 4.0F;
                this.leftWing.height = 3.0F;
                this.leftWing.width = 4.0F;
                float f12 = (float)(this.getSegmentProperties(5, 1.0F)[1] - this.getSegmentProperties(10, 1.0F)[1]) * 10.0F / 180.0F * (float) Math.PI;
                float f2 = MathHelper.cos(f12);
                float f15 = -MathHelper.sin(f12);
                float f3 = this.yaw * (float) Math.PI / 180.0F;
                float f16 = MathHelper.sin(f3);
                float f4 = MathHelper.cos(f3);
                this.body.tick();
                this.body.setPositionAndAngles(this.x + f16 * 0.5F, this.y, this.z - f4 * 0.5F, 0.0F, 0.0F);
                this.rightWing.tick();
                this.rightWing.setPositionAndAngles(this.x + f4 * 4.5F, this.y + 2.0, this.z + f16 * 4.5F, 0.0F, 0.0F);
                this.leftWing.tick();
                this.leftWing.setPositionAndAngles(this.x - f4 * 4.5F, this.y + 2.0, this.z - f16 * 4.5F, 0.0F, 0.0F);
                if (!this.world.isClient && this.damagedTimer == 0) {
                    this.flingMobs(this.world.getEntities(this, this.rightWing.getShape().grown(4.0, 2.0, 4.0).moved(0.0, -2.0, 0.0)));
                    this.flingMobs(this.world.getEntities(this, this.leftWing.getShape().grown(4.0, 2.0, 4.0).moved(0.0, -2.0, 0.0)));
                    this.damageMobs(this.world.getEntities(this, this.head.getShape().grown(1.0, 1.0, 1.0)));
                }

                double[] adouble1 = this.getSegmentProperties(5, 1.0F);
                double[] adouble = this.getSegmentProperties(0, 1.0F);
                float f18 = MathHelper.sin(this.yaw * (float) Math.PI / 180.0F - this.rotationSpeed * 0.01F);
                float f19 = MathHelper.cos(this.yaw * (float) Math.PI / 180.0F - this.rotationSpeed * 0.01F);
                this.head.tick();
                this.head
                    .setPositionAndAngles(
                        this.x + f18 * 5.5F * f2, this.y + (adouble[1] - adouble1[1]) * 1.0 + f15 * 5.5F, this.z - f19 * 5.5F * f2, 0.0F, 0.0F
                    );

                for (int j = 0; j < 3; j++) {
                    EnderDragonPart enderdragonpart = null;
                    if (j == 0) {
                        enderdragonpart = this.tailBase;
                    }

                    if (j == 1) {
                        enderdragonpart = this.tailMiddle;
                    }

                    if (j == 2) {
                        enderdragonpart = this.tailEnd;
                    }

                    double[] adouble2 = this.getSegmentProperties(12 + j * 2, 1.0F);
                    float f20 = this.yaw * (float) Math.PI / 180.0F + this.wrapAngle(adouble2[0] - adouble1[0]) * (float) Math.PI / 180.0F * 1.0F;
                    float f21 = MathHelper.sin(f20);
                    float f22 = MathHelper.cos(f20);
                    float f23 = 1.5F;
                    float f24 = (j + 1) * 2.0F;
                    enderdragonpart.tick();
                    enderdragonpart.setPositionAndAngles(
                        this.x - (f16 * f23 + f21 * f24) * f2,
                        this.y + (adouble2[1] - adouble1[1]) * 1.0 - (f24 + f23) * f15 + 1.5,
                        this.z + (f4 * f23 + f22 * f24) * f2,
                        0.0F,
                        0.0F
                    );
                }

                if (!this.world.isClient) {
                    this.restrictMovement = this.destroyBlocks(this.head.getShape()) | this.destroyBlocks(this.body.getShape());
                }
            }
        }
    }

    /**
     * End crystal related actions that should be performed every tick.
     *     - Disconnect from the crystal if it is removed
     *     - If connected to a crystal, heal with a 1/10 chance each tick
     *       (on average this means it heals every 10 ticks)
     *     - Search for the nearest crystal and connect to it if valid
     */
    private void tickEndCrystalInteraction() {
        if (this.connectedCrystal != null) {
            if (this.connectedCrystal.removed) {
                if (!this.world.isClient) {
                    this.takeDamage(this.head, DamageSource.explosion(null), 10.0F);
                }

                this.connectedCrystal = null;
            } else if (this.ticks % 10 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.setHealth(this.getHealth() + 1.0F);
            }
        }

        if (this.random.nextInt(10) == 0) {
            float f = 32.0F;
            List<EnderCrystalEntity> list = this.world.getEntitiesOfType(EnderCrystalEntity.class, this.getShape().grown(f, f, f));
            EnderCrystalEntity endercrystalentity = null;
            double d0 = Double.MAX_VALUE;

            for (EnderCrystalEntity endercrystalentity1 : list) {
                double d1 = endercrystalentity1.squaredDistanceTo(this);
                if (d1 < d0) {
                    d0 = d1;
                    endercrystalentity = endercrystalentity1;
                }
            }

            this.connectedCrystal = endercrystalentity;
        }
    }

    private void flingMobs(List<Entity> entities) {
        double d0 = (this.body.getShape().minX + this.body.getShape().maxX) / 2.0;
        double d1 = (this.body.getShape().minZ + this.body.getShape().maxZ) / 2.0;

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity) {
                double d2 = entity.x - d0;
                double d3 = entity.z - d1;
                double d4 = d2 * d2 + d3 * d3;
                entity.addVelocity(d2 / d4 * 4.0, 0.2F, d3 / d4 * 4.0);
            }
        }
    }

    private void damageMobs(List<Entity> entities) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (entity instanceof LivingEntity) {
                entity.takeDamage(DamageSource.mob(this), 10.0F);
                this.damageEntity(this, entity);
            }
        }
    }

    private void chooseTarget() {
        this.needsNewTarget = false;
        List<PlayerEntity> list = Lists.newArrayList(this.world.players);
        Iterator<PlayerEntity> iterator = list.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().isSpectator()) {
                iterator.remove();
            }
        }

        if (this.random.nextInt(2) == 0 && !list.isEmpty()) {
            this.target = list.get(this.random.nextInt(list.size()));
        } else {
            boolean flag;
            do {
                this.targetX = 0.0;
                this.targetY = 70.0F + this.random.nextFloat() * 50.0F;
                this.targetZ = 0.0;
                this.targetX = this.targetX + (this.random.nextFloat() * 120.0F - 60.0F);
                this.targetZ = this.targetZ + (this.random.nextFloat() * 120.0F - 60.0F);
                double d0 = this.x - this.targetX;
                double d1 = this.y - this.targetY;
                double d2 = this.z - this.targetZ;
                flag = d0 * d0 + d1 * d1 + d2 * d2 > 100.0;
            } while (!flag);

            this.target = null;
        }
    }

    /**
     * Forces an angle to be between -180° and 180° by adding/subtracting until it is in that interval
     */
    private float wrapAngle(double angle) {
        return (float)MathHelper.wrapDegrees(angle);
    }

    private boolean destroyBlocks(Box bounds) {
        int i = MathHelper.floor(bounds.minX);
        int j = MathHelper.floor(bounds.minY);
        int k = MathHelper.floor(bounds.minZ);
        int l = MathHelper.floor(bounds.maxX);
        int i1 = MathHelper.floor(bounds.maxY);
        int j1 = MathHelper.floor(bounds.maxZ);
        boolean flag = false;
        boolean flag1 = false;

        for (int k1 = i; k1 <= l; k1++) {
            for (int l1 = j; l1 <= i1; l1++) {
                for (int i2 = k; i2 <= j1; i2++) {
                    BlockPos blockpos = new BlockPos(k1, l1, i2);
                    Block block = this.world.getBlockState(blockpos).getBlock();
                    if (block.getMaterial() != Material.AIR) {
                        if (block != Blocks.BARRIER
                            && block != Blocks.OBSIDIAN
                            && block != Blocks.END_STONE
                            && block != Blocks.BEDROCK
                            && block != Blocks.COMMAND_BLOCK
                            && this.world.getGameRules().getBoolean("mobGriefing")) {
                            flag1 = this.world.removeBlock(blockpos) || flag1;
                        } else {
                            flag = true;
                        }
                    }
                }
            }
        }

        if (flag1) {
            double d0 = bounds.minX + (bounds.maxX - bounds.minX) * this.random.nextFloat();
            double d1 = bounds.minY + (bounds.maxY - bounds.minY) * this.random.nextFloat();
            double d2 = bounds.minZ + (bounds.maxZ - bounds.minZ) * this.random.nextFloat();
            this.world.addParticle(ParticleType.EXPLOSION_LARGE, d0, d1, d2, 0.0, 0.0, 0.0);
        }

        return flag;
    }

    @Override
    public boolean takeDamage(EnderDragonPart part, DamageSource source, float amount) {
        if (part != this.head) {
            amount = amount / 4.0F + 1.0F;
        }

        float f = this.yaw * (float) Math.PI / 180.0F;
        float f1 = MathHelper.sin(f);
        float f2 = MathHelper.cos(f);
        this.targetX = this.x + f1 * 5.0F + (this.random.nextFloat() - 0.5F) * 2.0F;
        this.targetY = this.y + this.random.nextFloat() * 3.0F + 1.0;
        this.targetZ = this.z - f2 * 5.0F + (this.random.nextFloat() - 0.5F) * 2.0F;
        this.target = null;
        if (source.getAttacker() instanceof PlayerEntity || source.isExplosive()) {
            this.damageDragon(source, amount);
        }

        return true;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (source instanceof EntityDamageSource && ((EntityDamageSource)source).hasThorns()) {
            this.damageDragon(source, amount);
        }

        return false;
    }

    /**
     * A wrapper around the damage method. This calls the damage method from the super class {@link net.minecraft.entity.living.LivingEntity LivingEntity}.
     * Above there is a method overriding the damage method from {@link net.minecraft.entity.Entity Entity}. Always use this when damaging the dragon.
     */
    protected boolean damageDragon(DamageSource soure, float amount) {
        return super.takeDamage(soure, amount);
    }

    @Override
    public void discard() {
        this.remove();
    }

    @Override
    protected void tickPostDeath() {
        this.ticksSinceDeath++;
        if (this.ticksSinceDeath >= 180 && this.ticksSinceDeath <= 200) {
            float f = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;
            this.world.addParticle(ParticleType.EXPLOSION_HUGE, this.x + f, this.y + 2.0 + f1, this.z + f2, 0.0, 0.0, 0.0);
        }

        boolean flag = this.world.getGameRules().getBoolean("doMobLoot");
        if (!this.world.isClient) {
            if (this.ticksSinceDeath > 150 && this.ticksSinceDeath % 5 == 0 && flag) {
                int i = 1000;

                while (i > 0) {
                    int k = ExperienceOrbEntity.roundSize(i);
                    i -= k;
                    this.world.addEntity(new ExperienceOrbEntity(this.world, this.x, this.y, this.z, k));
                }
            }

            if (this.ticksSinceDeath == 1) {
                this.world.doGlobalEvent(1018, new BlockPos(this), 0);
            }
        }

        this.move(0.0, 0.1F, 0.0);
        this.bodyYaw = this.yaw += 20.0F;
        if (this.ticksSinceDeath == 200 && !this.world.isClient) {
            if (flag) {
                int j = 2000;

                while (j > 0) {
                    int l = ExperienceOrbEntity.roundSize(j);
                    j -= l;
                    this.world.addEntity(new ExperienceOrbEntity(this.world, this.x, this.y, this.z, l));
                }
            }

            this.createPortal(new BlockPos(this.x, 64.0, this.z));
            this.remove();
        }
    }

    /**
     * Creates the portal after defeating the dragon. This is created around this.x and this.z.
     */
    private void createPortal(BlockPos pos) {
        int i = 4;
        double d0 = 12.25;
        double d1 = 6.25;

        for (int j = -1; j <= 32; j++) {
            for (int k = -4; k <= 4; k++) {
                for (int l = -4; l <= 4; l++) {
                    double d2 = k * k + l * l;
                    if (!(d2 > 12.25)) {
                        BlockPos blockpos = pos.add(k, j, l);
                        if (j < 0) {
                            if (d2 <= 6.25) {
                                this.world.setBlockState(blockpos, Blocks.BEDROCK.defaultState());
                            }
                        } else if (j > 0) {
                            this.world.setBlockState(blockpos, Blocks.AIR.defaultState());
                        } else if (d2 > 6.25) {
                            this.world.setBlockState(blockpos, Blocks.BEDROCK.defaultState());
                        } else {
                            this.world.setBlockState(blockpos, Blocks.END_PORTAL.defaultState());
                        }
                    }
                }
            }
        }

        this.world.setBlockState(pos, Blocks.BEDROCK.defaultState());
        this.world.setBlockState(pos.up(), Blocks.BEDROCK.defaultState());
        BlockPos blockpos1 = pos.up(2);
        this.world.setBlockState(blockpos1, Blocks.BEDROCK.defaultState());
        this.world.setBlockState(blockpos1.west(), Blocks.TORCH.defaultState().set(TorchBlock.FACING, Direction.EAST));
        this.world.setBlockState(blockpos1.east(), Blocks.TORCH.defaultState().set(TorchBlock.FACING, Direction.WEST));
        this.world.setBlockState(blockpos1.north(), Blocks.TORCH.defaultState().set(TorchBlock.FACING, Direction.SOUTH));
        this.world.setBlockState(blockpos1.south(), Blocks.TORCH.defaultState().set(TorchBlock.FACING, Direction.NORTH));
        this.world.setBlockState(pos.up(3), Blocks.BEDROCK.defaultState());
        this.world.setBlockState(pos.up(4), Blocks.DRAGON_EGG.defaultState());
    }

    @Override
    protected void checkDespawn() {
    }

    @Override
    public Entity[] getParts() {
        return this.parts;
    }

    @Override
    public boolean hasCollision() {
        return false;
    }

    @Override
    public World getWorld() {
        return this.world;
    }

    @Override
    protected String getAmbientSound() {
        return "mob.enderdragon.growl";
    }

    @Override
    protected String getHurtSound() {
        return "mob.enderdragon.hit";
    }

    @Override
    protected float getSoundVolume() {
        return 5.0F;
    }
}
