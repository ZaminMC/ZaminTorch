package net.minecraft.entity.projectile;

import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Dispensable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public abstract class ThrownEntity extends Entity implements Dispensable {
    private int blockX = -1;
    private int blockY = -1;
    private int blockZ = -1;
    private Block inBlock;
    protected boolean inGround;
    public int shake;
    private LivingEntity thrower;
    private String throwerName;
    private int ticksInBlock;
    private int ticksInAir;

    public ThrownEntity(World world) {
        super(world);
        this.setSize(0.25F, 0.25F);
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

    public ThrownEntity(World world, LivingEntity thrower) {
        super(world);
        this.thrower = thrower;
        this.setSize(0.25F, 0.25F);
        this.setPositionAndAngles(thrower.x, thrower.y + thrower.getEyeHeight(), thrower.z, thrower.yaw, thrower.pitch);
        this.x = this.x - MathHelper.cos(this.yaw / 180.0F * (float) Math.PI) * 0.16F;
        this.y -= 0.1F;
        this.z = this.z - MathHelper.sin(this.yaw / 180.0F * (float) Math.PI) * 0.16F;
        this.setPosition(this.x, this.y, this.z);
        float f = 0.4F;
        this.velocityX = -MathHelper.sin(this.yaw / 180.0F * (float) Math.PI) * MathHelper.cos(this.pitch / 180.0F * (float) Math.PI) * f;
        this.velocityZ = MathHelper.cos(this.yaw / 180.0F * (float) Math.PI) * MathHelper.cos(this.pitch / 180.0F * (float) Math.PI) * f;
        this.velocityY = -MathHelper.sin((this.pitch + this.getStartPitchOffset()) / 180.0F * (float) Math.PI) * f;
        this.dispense(this.velocityX, this.velocityY, this.velocityZ, this.getPower(), 1.0F);
    }

    public ThrownEntity(World world, double x, double y, double z) {
        super(world);
        this.ticksInBlock = 0;
        this.setSize(0.25F, 0.25F);
        this.setPosition(x, y, z);
    }

    protected float getPower() {
        return 1.5F;
    }

    protected float getStartPitchOffset() {
        return 0.0F;
    }

    @Override
    public void dispense(double velocityX, double velocityY, double velocityZ, float min, float scale) {
        float f = MathHelper.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        velocityX /= f;
        velocityY /= f;
        velocityZ /= f;
        velocityX += this.random.nextGaussian() * 0.0075F * scale;
        velocityY += this.random.nextGaussian() * 0.0075F * scale;
        velocityZ += this.random.nextGaussian() * 0.0075F * scale;
        velocityX *= min;
        velocityY *= min;
        velocityZ *= min;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        float f1 = MathHelper.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        this.lastYaw = this.yaw = (float)(MathHelper.fastAtan2(velocityX, velocityZ) * 180.0 / (float) Math.PI);
        this.lastPitch = this.pitch = (float)(MathHelper.fastAtan2(velocityY, f1) * 180.0 / (float) Math.PI);
        this.ticksInBlock = 0;
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
        }
    }

    @Override
    public void tick() {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;
        super.tick();
        if (this.shake > 0) {
            this.shake--;
        }

        if (this.inGround) {
            if (this.world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ)).getBlock() == this.inBlock) {
                this.ticksInBlock++;
                if (this.ticksInBlock == 1200) {
                    this.remove();
                }

                return;
            }

            this.inGround = false;
            this.velocityX = this.velocityX * (this.random.nextFloat() * 0.2F);
            this.velocityY = this.velocityY * (this.random.nextFloat() * 0.2F);
            this.velocityZ = this.velocityZ * (this.random.nextFloat() * 0.2F);
            this.ticksInBlock = 0;
            this.ticksInAir = 0;
        } else {
            this.ticksInAir++;
        }

        Vec3d vec3d = new Vec3d(this.x, this.y, this.z);
        Vec3d vec3d1 = new Vec3d(this.x + this.velocityX, this.y + this.velocityY, this.z + this.velocityZ);
        HitResult hitresult = this.world.rayTrace(vec3d, vec3d1);
        vec3d = new Vec3d(this.x, this.y, this.z);
        vec3d1 = new Vec3d(this.x + this.velocityX, this.y + this.velocityY, this.z + this.velocityZ);
        if (hitresult != null) {
            vec3d1 = new Vec3d(hitresult.facePos.x, hitresult.facePos.y, hitresult.facePos.z);
        }

        if (!this.world.isClient) {
            Entity entity = null;
            List<Entity> list = this.world.getEntities(this, this.getShape().expanded(this.velocityX, this.velocityY, this.velocityZ).grown(1.0, 1.0, 1.0));
            double d0 = 0.0;
            LivingEntity livingentity = this.getThrower();

            for (int j = 0; j < list.size(); j++) {
                Entity entity1 = list.get(j);
                if (entity1.hasCollision() && (entity1 != livingentity || this.ticksInAir >= 5)) {
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
        }

        if (hitresult != null) {
            if (hitresult.type == HitResult.Type.BLOCK && this.world.getBlockState(hitresult.getPos()).getBlock() == Blocks.NETHER_PORTAL) {
                this.onPortalCollision(hitresult.getPos());
            } else {
                this.onCollision(hitresult);
            }
        }

        this.x = this.x + this.velocityX;
        this.y = this.y + this.velocityY;
        this.z = this.z + this.velocityZ;
        float f1 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
        this.yaw = (float)(MathHelper.fastAtan2(this.velocityX, this.velocityZ) * 180.0 / (float) Math.PI);
        this.pitch = (float)(MathHelper.fastAtan2(this.velocityY, f1) * 180.0 / (float) Math.PI);

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
        float f2 = 0.99F;
        float f3 = this.getGravity();
        if (this.isInWater()) {
            for (int i = 0; i < 4; i++) {
                float f4 = 0.25F;
                this.world
                    .addParticle(
                        ParticleType.WATER_BUBBLE,
                        this.x - this.velocityX * f4,
                        this.y - this.velocityY * f4,
                        this.z - this.velocityZ * f4,
                        this.velocityX,
                        this.velocityY,
                        this.velocityZ
                    );
            }

            f2 = 0.8F;
        }

        this.velocityX *= f2;
        this.velocityY *= f2;
        this.velocityZ *= f2;
        this.velocityY -= f3;
        this.setPosition(this.x, this.y, this.z);
    }

    protected float getGravity() {
        return 0.03F;
    }

    protected abstract void onCollision(HitResult result);

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putShort("xTile", (short)this.blockX);
        nbt.putShort("yTile", (short)this.blockY);
        nbt.putShort("zTile", (short)this.blockZ);
        Identifier identifier = Block.REGISTRY.getKey(this.inBlock);
        nbt.putString("inTile", identifier == null ? "" : identifier.toString());
        nbt.putByte("shake", (byte)this.shake);
        nbt.putByte("inGround", (byte)(this.inGround ? 1 : 0));
        if ((this.throwerName == null || this.throwerName.length() == 0) && this.thrower instanceof PlayerEntity) {
            this.throwerName = this.thrower.getName();
        }

        nbt.putString("ownerName", this.throwerName == null ? "" : this.throwerName);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        this.blockX = nbt.getShort("xTile");
        this.blockY = nbt.getShort("yTile");
        this.blockZ = nbt.getShort("zTile");
        if (nbt.contains("inTile", 8)) {
            this.inBlock = Block.byKey(nbt.getString("inTile"));
        } else {
            this.inBlock = Block.byId(nbt.getByte("inTile") & 255);
        }

        this.shake = nbt.getByte("shake") & 255;
        this.inGround = nbt.getByte("inGround") == 1;
        this.thrower = null;
        this.throwerName = nbt.getString("ownerName");
        if (this.throwerName != null && this.throwerName.length() == 0) {
            this.throwerName = null;
        }

        this.thrower = this.getThrower();
    }

    public LivingEntity getThrower() {
        if (this.thrower == null && this.throwerName != null && this.throwerName.length() > 0) {
            this.thrower = this.world.getPlayer(this.throwerName);
            if (this.thrower == null && this.world instanceof ServerWorld) {
                try {
                    Entity entity = ((ServerWorld)this.world).getEntity(UUID.fromString(this.throwerName));
                    if (entity instanceof LivingEntity) {
                        this.thrower = (LivingEntity)entity;
                    }
                } catch (Throwable throwable) {
                    this.thrower = null;
                }
            }
        }

        return this.thrower;
    }
}
