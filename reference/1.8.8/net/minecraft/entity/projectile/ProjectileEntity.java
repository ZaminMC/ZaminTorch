package net.minecraft.entity.projectile;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public abstract class ProjectileEntity extends Entity {
    private int blockX = -1;
    private int blockY = -1;
    private int blockZ = -1;
    private Block block;
    private boolean inGround;
    public LivingEntity shooter;
    private int inBlockTicks;
    private int inAirTicks;
    public double accelerationX;
    public double accelerationY;
    public double accelerationZ;

    public ProjectileEntity(World world) {
        super(world);
        this.setSize(1.0F, 1.0F);
    }

    @Override
    protected void registerSyncedData() {
    }

    @Override
    public boolean shouldRender(double squaredDistanceToCamera) {
        double d0 = this.getShape().getAverageSideLength() * 4.0;
        if (Double.isNaN(d0)) {
            d0 = 4.0;
        }

        d0 *= 64.0;
        return squaredDistanceToCamera < d0 * d0;
    }

    public ProjectileEntity(World world, double x, double y, double z, double accelerationX, double accelerationY, double accelerationZ) {
        super(world);
        this.setSize(1.0F, 1.0F);
        this.setPositionAndAngles(x, y, z, this.yaw, this.pitch);
        this.setPosition(x, y, z);
        double d0 = MathHelper.sqrt(accelerationX * accelerationX + accelerationY * accelerationY + accelerationZ * accelerationZ);
        this.accelerationX = accelerationX / d0 * 0.1;
        this.accelerationY = accelerationY / d0 * 0.1;
        this.accelerationZ = accelerationZ / d0 * 0.1;
    }

    public ProjectileEntity(World world, LivingEntity shooter, double accelerationX, double accelerationY, double accelerationZ) {
        super(world);
        this.shooter = shooter;
        this.setSize(1.0F, 1.0F);
        this.setPositionAndAngles(shooter.x, shooter.y, shooter.z, shooter.yaw, shooter.pitch);
        this.setPosition(this.x, this.y, this.z);
        this.velocityX = this.velocityY = this.velocityZ = 0.0;
        accelerationX += this.random.nextGaussian() * 0.4;
        accelerationY += this.random.nextGaussian() * 0.4;
        accelerationZ += this.random.nextGaussian() * 0.4;
        double d0 = MathHelper.sqrt(accelerationX * accelerationX + accelerationY * accelerationY + accelerationZ * accelerationZ);
        this.accelerationX = accelerationX / d0 * 0.1;
        this.accelerationY = accelerationY / d0 * 0.1;
        this.accelerationZ = accelerationZ / d0 * 0.1;
    }

    @Override
    public void tick() {
        if (this.world.isClient || (this.shooter == null || !this.shooter.removed) && this.world.isChunkLoaded(new BlockPos(this))) {
            super.tick();
            this.setOnFireFor(1);
            if (this.inGround) {
                if (this.world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ)).getBlock() == this.block) {
                    this.inBlockTicks++;
                    if (this.inBlockTicks == 600) {
                        this.remove();
                    }

                    return;
                }

                this.inGround = false;
                this.velocityX = this.velocityX * (this.random.nextFloat() * 0.2F);
                this.velocityY = this.velocityY * (this.random.nextFloat() * 0.2F);
                this.velocityZ = this.velocityZ * (this.random.nextFloat() * 0.2F);
                this.inBlockTicks = 0;
                this.inAirTicks = 0;
            } else {
                this.inAirTicks++;
            }

            Vec3d vec3d = new Vec3d(this.x, this.y, this.z);
            Vec3d vec3d1 = new Vec3d(this.x + this.velocityX, this.y + this.velocityY, this.z + this.velocityZ);
            HitResult hitresult = this.world.rayTrace(vec3d, vec3d1);
            vec3d = new Vec3d(this.x, this.y, this.z);
            vec3d1 = new Vec3d(this.x + this.velocityX, this.y + this.velocityY, this.z + this.velocityZ);
            if (hitresult != null) {
                vec3d1 = new Vec3d(hitresult.facePos.x, hitresult.facePos.y, hitresult.facePos.z);
            }

            Entity entity = null;
            List<Entity> list = this.world.getEntities(this, this.getShape().expanded(this.velocityX, this.velocityY, this.velocityZ).grown(1.0, 1.0, 1.0));
            double d0 = 0.0;

            for (int i = 0; i < list.size(); i++) {
                Entity entity1 = list.get(i);
                if (entity1.hasCollision() && (!entity1.is(this.shooter) || this.inAirTicks >= 25)) {
                    float f = 0.3F;
                    Box box = entity1.getShape().grown(f, f, f);
                    HitResult hitresult1 = box.clip(vec3d, vec3d1);
                    if (hitresult1 != null) {
                        double d1 = vec3d.squaredDistanceTo(hitresult1.facePos);
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

            if (hitresult != null) {
                this.onHit(hitresult);
            }

            this.x = this.x + this.velocityX;
            this.y = this.y + this.velocityY;
            this.z = this.z + this.velocityZ;
            float f1 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
            this.yaw = (float)(MathHelper.fastAtan2(this.velocityZ, this.velocityX) * 180.0 / (float) Math.PI) + 90.0F;
            this.pitch = (float)(MathHelper.fastAtan2(f1, this.velocityY) * 180.0 / (float) Math.PI) - 90.0F;

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
            float f2 = this.getDrag();
            if (this.isInWater()) {
                for (int j = 0; j < 4; j++) {
                    float f3 = 0.25F;
                    this.world
                        .addParticle(
                            ParticleType.WATER_BUBBLE,
                            this.x - this.velocityX * f3,
                            this.y - this.velocityY * f3,
                            this.z - this.velocityZ * f3,
                            this.velocityX,
                            this.velocityY,
                            this.velocityZ
                        );
                }

                f2 = 0.8F;
            }

            this.velocityX = this.velocityX + this.accelerationX;
            this.velocityY = this.velocityY + this.accelerationY;
            this.velocityZ = this.velocityZ + this.accelerationZ;
            this.velocityX *= f2;
            this.velocityY *= f2;
            this.velocityZ *= f2;
            this.world.addParticle(ParticleType.SMOKE_NORMAL, this.x, this.y + 0.5, this.z, 0.0, 0.0, 0.0);
            this.setPosition(this.x, this.y, this.z);
        } else {
            this.remove();
        }
    }

    protected float getDrag() {
        return 0.95F;
    }

    protected abstract void onHit(HitResult hit);

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putShort("xTile", (short)this.blockX);
        nbt.putShort("yTile", (short)this.blockY);
        nbt.putShort("zTile", (short)this.blockZ);
        Identifier identifier = Block.REGISTRY.getKey(this.block);
        nbt.putString("inTile", identifier == null ? "" : identifier.toString());
        nbt.putByte("inGround", (byte)(this.inGround ? 1 : 0));
        nbt.put("direction", this.toNbtList(this.velocityX, this.velocityY, this.velocityZ));
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        this.blockX = nbt.getShort("xTile");
        this.blockY = nbt.getShort("yTile");
        this.blockZ = nbt.getShort("zTile");
        if (nbt.contains("inTile", 8)) {
            this.block = Block.byKey(nbt.getString("inTile"));
        } else {
            this.block = Block.byId(nbt.getByte("inTile") & 255);
        }

        this.inGround = nbt.getByte("inGround") == 1;
        if (nbt.contains("direction", 9)) {
            NbtList nbtlist = nbt.getList("direction", 6);
            this.velocityX = nbtlist.getDouble(0);
            this.velocityY = nbtlist.getDouble(1);
            this.velocityZ = nbtlist.getDouble(2);
        } else {
            this.remove();
        }
    }

    @Override
    public boolean hasCollision() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 1.0F;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        this.markDamaged();
        if (source.getAttacker() != null) {
            Vec3d vec3d = source.getAttacker().getLookVector();
            if (vec3d != null) {
                this.velocityX = vec3d.x;
                this.velocityY = vec3d.y;
                this.velocityZ = vec3d.z;
                this.accelerationX = this.velocityX * 0.1;
                this.accelerationY = this.velocityY * 0.1;
                this.accelerationZ = this.velocityZ * 0.1;
            }

            if (source.getAttacker() instanceof LivingEntity) {
                this.shooter = (LivingEntity)source.getAttacker();
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public float getBrightness(float tickDelta) {
        return 1.0F;
    }

    @Override
    public int getLightLevel(float tickDelta) {
        return 15728880;
    }
}
