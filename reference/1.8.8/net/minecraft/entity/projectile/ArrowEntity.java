package net.minecraft.entity.projectile;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Dispensable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.GameEventS2CPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class ArrowEntity extends Entity implements Dispensable {
    private int blockX = -1;
    private int blockY = -1;
    private int blockZ = -1;
    private Block block;
    private int blockData;
    private boolean inGround;
    public int pickup;
    public int shake;
    public Entity shooter;
    private int inBlockTicks;
    private int inAirTicks;
    private double damage = 2.0;
    private int punchLevel;

    public ArrowEntity(World world) {
        super(world);
        this.viewDistanceScaling = 10.0;
        this.setSize(0.5F, 0.5F);
    }

    public ArrowEntity(World world, double x, double y, double z) {
        super(world);
        this.viewDistanceScaling = 10.0;
        this.setSize(0.5F, 0.5F);
        this.setPosition(x, y, z);
    }

    public ArrowEntity(World world, LivingEntity shooter, LivingEntity target, float speed, float divergence) {
        super(world);
        this.viewDistanceScaling = 10.0;
        this.shooter = shooter;
        if (shooter instanceof PlayerEntity) {
            this.pickup = 1;
        }

        this.y = shooter.y + shooter.getEyeHeight() - 0.1F;
        double d0 = target.x - shooter.x;
        double d1 = target.getShape().minY + target.height / 3.0F - this.y;
        double d2 = target.z - shooter.z;
        double d3 = MathHelper.sqrt(d0 * d0 + d2 * d2);
        if (!(d3 < 1.0E-7)) {
            float f = (float)(MathHelper.fastAtan2(d2, d0) * 180.0 / (float) Math.PI) - 90.0F;
            float f1 = (float)(-(MathHelper.fastAtan2(d1, d3) * 180.0 / (float) Math.PI));
            double d4 = d0 / d3;
            double d5 = d2 / d3;
            this.setPositionAndAngles(shooter.x + d4, this.y, shooter.z + d5, f, f1);
            float f2 = (float)(d3 * 0.2F);
            this.dispense(d0, d1 + f2, d2, speed, divergence);
        }
    }

    public ArrowEntity(World world, LivingEntity shooter, float speed) {
        super(world);
        this.viewDistanceScaling = 10.0;
        this.shooter = shooter;
        if (shooter instanceof PlayerEntity) {
            this.pickup = 1;
        }

        this.setSize(0.5F, 0.5F);
        this.setPositionAndAngles(shooter.x, shooter.y + shooter.getEyeHeight(), shooter.z, shooter.yaw, shooter.pitch);
        this.x = this.x - MathHelper.cos(this.yaw / 180.0F * (float) Math.PI) * 0.16F;
        this.y -= 0.1F;
        this.z = this.z - MathHelper.sin(this.yaw / 180.0F * (float) Math.PI) * 0.16F;
        this.setPosition(this.x, this.y, this.z);
        this.velocityX = -MathHelper.sin(this.yaw / 180.0F * (float) Math.PI) * MathHelper.cos(this.pitch / 180.0F * (float) Math.PI);
        this.velocityZ = MathHelper.cos(this.yaw / 180.0F * (float) Math.PI) * MathHelper.cos(this.pitch / 180.0F * (float) Math.PI);
        this.velocityY = -MathHelper.sin(this.pitch / 180.0F * (float) Math.PI);
        this.dispense(this.velocityX, this.velocityY, this.velocityZ, speed * 1.5F, 1.0F);
    }

    @Override
    protected void registerSyncedData() {
        this.syncedData.register(16, (byte)0);
    }

    @Override
    public void dispense(double velocityX, double velocityY, double velocityZ, float min, float scale) {
        float f = MathHelper.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        velocityX /= f;
        velocityY /= f;
        velocityZ /= f;
        velocityX += this.random.nextGaussian() * (this.random.nextBoolean() ? -1 : 1) * 0.0075F * scale;
        velocityY += this.random.nextGaussian() * (this.random.nextBoolean() ? -1 : 1) * 0.0075F * scale;
        velocityZ += this.random.nextGaussian() * (this.random.nextBoolean() ? -1 : 1) * 0.0075F * scale;
        velocityX *= min;
        velocityY *= min;
        velocityZ *= min;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        float f1 = MathHelper.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        this.lastYaw = this.yaw = (float)(MathHelper.fastAtan2(velocityX, velocityZ) * 180.0 / (float) Math.PI);
        this.lastPitch = this.pitch = (float)(MathHelper.fastAtan2(velocityY, f1) * 180.0 / (float) Math.PI);
        this.inBlockTicks = 0;
    }

    @Override
    public void lerpPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        this.setPosition(x, y, z);
        this.setRotation(yaw, pitch);
    }

    @Override
    public void lerpVelocity(double velocityX, double velocityY, double velocityZ) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        if (this.lastPitch == 0.0F && this.lastYaw == 0.0F) {
            float f = MathHelper.sqrt(velocityX * velocityX + velocityZ * velocityZ);
            this.lastYaw = this.yaw = (float)(MathHelper.fastAtan2(velocityX, velocityZ) * 180.0 / (float) Math.PI);
            this.lastPitch = this.pitch = (float)(MathHelper.fastAtan2(velocityY, f) * 180.0 / (float) Math.PI);
            this.lastPitch = this.pitch;
            this.lastYaw = this.yaw;
            this.setPositionAndAngles(this.x, this.y, this.z, this.yaw, this.pitch);
            this.inBlockTicks = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.lastPitch == 0.0F && this.lastYaw == 0.0F) {
            float f = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
            this.lastYaw = this.yaw = (float)(MathHelper.fastAtan2(this.velocityX, this.velocityZ) * 180.0 / (float) Math.PI);
            this.lastPitch = this.pitch = (float)(MathHelper.fastAtan2(this.velocityY, f) * 180.0 / (float) Math.PI);
        }

        BlockPos blockpos = new BlockPos(this.blockX, this.blockY, this.blockZ);
        BlockState blockstate = this.world.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        if (block.getMaterial() != Material.AIR) {
            block.updateShape(this.world, blockpos);
            Box box = block.getCollisionShape(this.world, blockpos, blockstate);
            if (box != null && box.contains(new Vec3d(this.x, this.y, this.z))) {
                this.inGround = true;
            }
        }

        if (this.shake > 0) {
            this.shake--;
        }

        if (this.inGround) {
            int j = block.getMetadataFromState(blockstate);
            if (block == this.block && j == this.blockData) {
                this.inBlockTicks++;
                if (this.inBlockTicks >= 1200) {
                    this.remove();
                }
            } else {
                this.inGround = false;
                this.velocityX = this.velocityX * (this.random.nextFloat() * 0.2F);
                this.velocityY = this.velocityY * (this.random.nextFloat() * 0.2F);
                this.velocityZ = this.velocityZ * (this.random.nextFloat() * 0.2F);
                this.inBlockTicks = 0;
                this.inAirTicks = 0;
            }
        } else {
            this.inAirTicks++;
            Vec3d vec3d1 = new Vec3d(this.x, this.y, this.z);
            Vec3d vec3d = new Vec3d(this.x + this.velocityX, this.y + this.velocityY, this.z + this.velocityZ);
            HitResult hitresult = this.world.rayTrace(vec3d1, vec3d, false, true, false);
            vec3d1 = new Vec3d(this.x, this.y, this.z);
            vec3d = new Vec3d(this.x + this.velocityX, this.y + this.velocityY, this.z + this.velocityZ);
            if (hitresult != null) {
                vec3d = new Vec3d(hitresult.facePos.x, hitresult.facePos.y, hitresult.facePos.z);
            }

            Entity entity = null;
            List<Entity> list = this.world.getEntities(this, this.getShape().expanded(this.velocityX, this.velocityY, this.velocityZ).grown(1.0, 1.0, 1.0));
            double d0 = 0.0;

            for (int i = 0; i < list.size(); i++) {
                Entity entity1 = list.get(i);
                if (entity1.hasCollision() && (entity1 != this.shooter || this.inAirTicks >= 5)) {
                    float f1 = 0.3F;
                    Box box1 = entity1.getShape().grown(f1, f1, f1);
                    HitResult hitresult1 = box1.clip(vec3d1, vec3d);
                    if (hitresult1 != null) {
                        double d1 = vec3d1.squaredDistanceTo(hitresult1.facePos);
                        if (d1 < d0 || d0 == 0.0) {
                            entity = entity1;
                            d0 = d1;
                        }
                    }
                }
            }

            if (entity != null) {
                hitresult = new HitResult(entity);
            }

            if (hitresult != null && hitresult.entity != null && hitresult.entity instanceof PlayerEntity) {
                PlayerEntity playerentity = (PlayerEntity)hitresult.entity;
                if (playerentity.abilities.invulnerable || this.shooter instanceof PlayerEntity && !((PlayerEntity)this.shooter).canAttack(playerentity)) {
                    hitresult = null;
                }
            }

            if (hitresult != null) {
                if (hitresult.entity != null) {
                    float f2 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityY * this.velocityY + this.velocityZ * this.velocityZ);
                    int l = MathHelper.ceil(f2 * this.damage);
                    if (this.isCritical()) {
                        l += this.random.nextInt(l / 2 + 2);
                    }

                    DamageSource damagesource;
                    if (this.shooter == null) {
                        damagesource = DamageSource.arrow(this, this);
                    } else {
                        damagesource = DamageSource.arrow(this, this.shooter);
                    }

                    if (this.isOnFire() && !(hitresult.entity instanceof EndermanEntity)) {
                        hitresult.entity.setOnFireFor(5);
                    }

                    if (hitresult.entity.takeDamage(damagesource, l)) {
                        if (hitresult.entity instanceof LivingEntity) {
                            LivingEntity livingentity = (LivingEntity)hitresult.entity;
                            if (!this.world.isClient) {
                                livingentity.setStuckArrows(livingentity.getStuckArrows() + 1);
                            }

                            if (this.punchLevel > 0) {
                                float f7 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
                                if (f7 > 0.0F) {
                                    hitresult.entity
                                        .addVelocity(this.velocityX * this.punchLevel * 0.6F / f7, 0.1, this.velocityZ * this.punchLevel * 0.6F / f7);
                                }
                            }

                            if (this.shooter instanceof LivingEntity) {
                                EnchantmentHelper.applyProtectionWildcard(livingentity, this.shooter);
                                EnchantmentHelper.applyDamageWildcard((LivingEntity)this.shooter, livingentity);
                            }

                            if (this.shooter != null
                                && hitresult.entity != this.shooter
                                && hitresult.entity instanceof PlayerEntity
                                && this.shooter instanceof ServerPlayerEntity) {
                                ((ServerPlayerEntity)this.shooter).networkHandler.sendPacket(new GameEventS2CPacket(6, 0.0F));
                            }
                        }

                        this.playSound("random.bowhit", 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
                        if (!(hitresult.entity instanceof EndermanEntity)) {
                            this.remove();
                        }
                    } else {
                        this.velocityX *= -0.1F;
                        this.velocityY *= -0.1F;
                        this.velocityZ *= -0.1F;
                        this.yaw += 180.0F;
                        this.lastYaw += 180.0F;
                        this.inAirTicks = 0;
                    }
                } else {
                    BlockPos blockpos1 = hitresult.getPos();
                    this.blockX = blockpos1.getX();
                    this.blockY = blockpos1.getY();
                    this.blockZ = blockpos1.getZ();
                    BlockState blockstate1 = this.world.getBlockState(blockpos1);
                    this.block = blockstate1.getBlock();
                    this.blockData = this.block.getMetadataFromState(blockstate1);
                    this.velocityX = (float)(hitresult.facePos.x - this.x);
                    this.velocityY = (float)(hitresult.facePos.y - this.y);
                    this.velocityZ = (float)(hitresult.facePos.z - this.z);
                    float f5 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityY * this.velocityY + this.velocityZ * this.velocityZ);
                    this.x = this.x - this.velocityX / f5 * 0.05F;
                    this.y = this.y - this.velocityY / f5 * 0.05F;
                    this.z = this.z - this.velocityZ / f5 * 0.05F;
                    this.playSound("random.bowhit", 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
                    this.inGround = true;
                    this.shake = 7;
                    this.setCritical(false);
                    if (this.block.getMaterial() != Material.AIR) {
                        this.block.onEntityCollision(this.world, blockpos1, blockstate1, this);
                    }
                }
            }

            if (this.isCritical()) {
                for (int k = 0; k < 4; k++) {
                    this.world
                        .addParticle(
                            ParticleType.CRIT,
                            this.x + this.velocityX * k / 4.0,
                            this.y + this.velocityY * k / 4.0,
                            this.z + this.velocityZ * k / 4.0,
                            -this.velocityX,
                            -this.velocityY + 0.2,
                            -this.velocityZ
                        );
                }
            }

            this.x = this.x + this.velocityX;
            this.y = this.y + this.velocityY;
            this.z = this.z + this.velocityZ;
            float f3 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
            this.yaw = (float)(MathHelper.fastAtan2(this.velocityX, this.velocityZ) * 180.0 / (float) Math.PI);
            this.pitch = (float)(MathHelper.fastAtan2(this.velocityY, f3) * 180.0 / (float) Math.PI);

            while (this.pitch - this.lastPitch < -180.0F) {
                this.lastPitch -= 360.0F;
            }

            while (this.pitch - this.lastPitch >= 180.0F) {
                this.lastPitch += 360.0F;
            }

            while (this.yaw - this.lastYaw < -180.0F) {
                this.lastYaw -= 360.0F;
            }

            while (this.yaw - this.lastYaw >= 180.0F) {
                this.lastYaw += 360.0F;
            }

            this.pitch = this.lastPitch + (this.pitch - this.lastPitch) * 0.2F;
            this.yaw = this.lastYaw + (this.yaw - this.lastYaw) * 0.2F;
            float f4 = 0.99F;
            float f6 = 0.05F;
            if (this.isInWater()) {
                for (int i1 = 0; i1 < 4; i1++) {
                    float f8 = 0.25F;
                    this.world
                        .addParticle(
                            ParticleType.WATER_BUBBLE,
                            this.x - this.velocityX * f8,
                            this.y - this.velocityY * f8,
                            this.z - this.velocityZ * f8,
                            this.velocityX,
                            this.velocityY,
                            this.velocityZ
                        );
                }

                f4 = 0.6F;
            }

            if (this.isInWaterOrRain()) {
                this.extinguish();
            }

            this.velocityX *= f4;
            this.velocityY *= f4;
            this.velocityZ *= f4;
            this.velocityY -= f6;
            this.setPosition(this.x, this.y, this.z);
            this.checkBlockCollisions();
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putShort("xTile", (short)this.blockX);
        nbt.putShort("yTile", (short)this.blockY);
        nbt.putShort("zTile", (short)this.blockZ);
        nbt.putShort("life", (short)this.inBlockTicks);
        Identifier identifier = Block.REGISTRY.getKey(this.block);
        nbt.putString("inTile", identifier == null ? "" : identifier.toString());
        nbt.putByte("inData", (byte)this.blockData);
        nbt.putByte("shake", (byte)this.shake);
        nbt.putByte("inGround", (byte)(this.inGround ? 1 : 0));
        nbt.putByte("pickup", (byte)this.pickup);
        nbt.putDouble("damage", this.damage);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        this.blockX = nbt.getShort("xTile");
        this.blockY = nbt.getShort("yTile");
        this.blockZ = nbt.getShort("zTile");
        this.inBlockTicks = nbt.getShort("life");
        if (nbt.contains("inTile", 8)) {
            this.block = Block.byKey(nbt.getString("inTile"));
        } else {
            this.block = Block.byId(nbt.getByte("inTile") & 255);
        }

        this.blockData = nbt.getByte("inData") & 255;
        this.shake = nbt.getByte("shake") & 255;
        this.inGround = nbt.getByte("inGround") == 1;
        if (nbt.contains("damage", 99)) {
            this.damage = nbt.getDouble("damage");
        }

        if (nbt.contains("pickup", 99)) {
            this.pickup = nbt.getByte("pickup");
        } else if (nbt.contains("player", 99)) {
            this.pickup = nbt.getBoolean("player") ? 1 : 0;
        }
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        if (!this.world.isClient && this.inGround && this.shake <= 0) {
            boolean flag = this.pickup == 1 || this.pickup == 2 && player.abilities.creativeMode;
            if (this.pickup == 1 && !player.inventory.addItem(new ItemStack(Items.ARROW, 1))) {
                flag = false;
            }

            if (flag) {
                this.playSound("random.pop", 0.2F, ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                player.sendPickup(this, 1);
                this.remove();
            }
        }
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public double getDamage() {
        return this.damage;
    }

    public void setPunchLevel(int level) {
        this.punchLevel = level;
    }

    @Override
    public boolean canBePunched() {
        return false;
    }

    @Override
    public float getEyeHeight() {
        return 0.0F;
    }

    public void setCritical(boolean critical) {
        byte b0 = this.syncedData.getByte(16);
        if (critical) {
            this.syncedData.update(16, (byte)(b0 | 1));
        } else {
            this.syncedData.update(16, (byte)(b0 & -2));
        }
    }

    public boolean isCritical() {
        byte b0 = this.syncedData.getByte(16);
        return (b0 & 1) != 0;
    }
}
