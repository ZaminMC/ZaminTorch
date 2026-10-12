package net.minecraft.entity.living.mob.ambient;

import java.util.Calendar;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class BatEntity extends AmbientMobEntity {
    private BlockPos hangPos;

    public BatEntity(World world) {
        super(world);
        this.setSize(0.5F, 0.9F);
        this.setRoosting(true);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, new Byte((byte)0));
    }

    @Override
    protected float getSoundVolume() {
        return 0.1F;
    }

    @Override
    protected float getSoundPitch() {
        return super.getSoundPitch() * 0.95F;
    }

    @Override
    protected String getAmbientSound() {
        return this.isRoosting() && this.random.nextInt(4) != 0 ? null : "mob.bat.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.bat.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.bat.death";
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    protected void pushAwayCollidingEntities() {
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(6.0);
    }

    public boolean isRoosting() {
        return (this.syncedData.getByte(16) & 1) != 0;
    }

    public void setRoosting(boolean roosting) {
        byte b0 = this.syncedData.getByte(16);
        if (roosting) {
            this.syncedData.update(16, (byte)(b0 | 1));
        } else {
            this.syncedData.update(16, (byte)(b0 & -2));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRoosting()) {
            this.velocityX = this.velocityY = this.velocityZ = 0.0;
            this.y = MathHelper.floor(this.y) + 1.0 - this.height;
        } else {
            this.velocityY *= 0.6F;
        }
    }

    @Override
    protected void mobAiTick() {
        super.mobAiTick();
        BlockPos blockpos = new BlockPos(this);
        BlockPos blockpos1 = blockpos.up();
        if (this.isRoosting()) {
            if (!this.world.getBlockState(blockpos1).getBlock().isSolid()) {
                this.setRoosting(false);
                this.world.doEvent(null, 1015, blockpos, 0);
            } else {
                if (this.random.nextInt(200) == 0) {
                    this.headYaw = this.random.nextInt(360);
                }

                if (this.world.getNearestPlayer(this, 4.0) != null) {
                    this.setRoosting(false);
                    this.world.doEvent(null, 1015, blockpos, 0);
                }
            }
        } else {
            if (this.hangPos != null && (!this.world.isAir(this.hangPos) || this.hangPos.getY() < 1)) {
                this.hangPos = null;
            }

            if (this.hangPos == null || this.random.nextInt(30) == 0 || this.hangPos.squaredDistanceTo((int)this.x, (int)this.y, (int)this.z) < 4.0) {
                this.hangPos = new BlockPos(
                    (int)this.x + this.random.nextInt(7) - this.random.nextInt(7),
                    (int)this.y + this.random.nextInt(6) - 2,
                    (int)this.z + this.random.nextInt(7) - this.random.nextInt(7)
                );
            }

            double d0 = this.hangPos.getX() + 0.5 - this.x;
            double d1 = this.hangPos.getY() + 0.1 - this.y;
            double d2 = this.hangPos.getZ() + 0.5 - this.z;
            this.velocityX = this.velocityX + (Math.signum(d0) * 0.5 - this.velocityX) * 0.1F;
            this.velocityY = this.velocityY + (Math.signum(d1) * 0.7F - this.velocityY) * 0.1F;
            this.velocityZ = this.velocityZ + (Math.signum(d2) * 0.5 - this.velocityZ) * 0.1F;
            float f = (float)(MathHelper.fastAtan2(this.velocityZ, this.velocityX) * 180.0 / (float) Math.PI) - 90.0F;
            float f1 = MathHelper.wrapDegrees(f - this.yaw);
            this.forwardSpeed = 0.5F;
            this.yaw += f1;
            if (this.random.nextInt(100) == 0 && this.world.getBlockState(blockpos1).getBlock().isSolid()) {
                this.setRoosting(true);
            }
        }
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
    }

    @Override
    protected void checkFallDamage(double dy, boolean landed, Block block, BlockPos pos) {
    }

    @Override
    public boolean canAvoidTraps() {
        return true;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (!this.world.isClient && this.isRoosting()) {
            this.setRoosting(false);
        }

        return super.takeDamage(source, amount);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.syncedData.update(16, nbt.getByte("BatFlags"));
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putByte("BatFlags", this.syncedData.getByte(16));
    }

    @Override
    public boolean canSpawn() {
        BlockPos blockpos = new BlockPos(this.x, this.getShape().minY, this.z);
        if (blockpos.getY() >= this.world.getSeaLevel()) {
            return false;
        }

        int i = this.world.getRawBrightness(blockpos);
        int j = 4;
        if (this.isTodayAroundHalloween(this.world.getCalendar())) {
            j = 7;
        } else if (this.random.nextBoolean()) {
            return false;
        }

        return i <= this.random.nextInt(j) && super.canSpawn();
    }

    private boolean isTodayAroundHalloween(Calendar calendar) {
        return calendar.get(2) + 1 == 10 && calendar.get(5) >= 20 || calendar.get(2) + 1 == 11 && calendar.get(5) <= 3;
    }

    @Override
    public float getEyeHeight() {
        return this.height / 2.0F;
    }
}
