package net.minecraft.entity.decoration;

import net.minecraft.block.Block;
import net.minecraft.block.DiodeBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;

public abstract class DecorationEntity extends Entity {
    private int ticksSinceValidation;
    protected BlockPos pos;
    public Direction dir;

    public DecorationEntity(World world) {
        super(world);
        this.setSize(0.5F, 0.5F);
    }

    public DecorationEntity(World world, BlockPos pos) {
        this(world);
        this.pos = pos;
    }

    @Override
    protected void registerSyncedData() {
    }

    protected void setDirection(Direction dir) {
        Validate.notNull(dir);
        Validate.isTrue(dir.getAxis().isHorizontal());
        this.dir = dir;
        this.lastYaw = this.yaw = this.dir.getIdHorizontal() * 90;
        this.updateShape();
    }

    private void updateShape() {
        if (this.dir != null) {
            double d0 = this.pos.getX() + 0.5;
            double d1 = this.pos.getY() + 0.5;
            double d2 = this.pos.getZ() + 0.5;
            double d3 = 0.46875;
            double d4 = this.getPositionOffset(this.getWidth());
            double d5 = this.getPositionOffset(this.getHeight());
            d0 -= this.dir.getOffsetX() * 0.46875;
            d2 -= this.dir.getOffsetZ() * 0.46875;
            d1 += d5;
            Direction direction = this.dir.counterClockwiseY();
            d0 += d4 * direction.getOffsetX();
            d2 += d4 * direction.getOffsetZ();
            this.x = d0;
            this.y = d1;
            this.z = d2;
            double d6 = this.getWidth();
            double d7 = this.getHeight();
            double d8 = this.getWidth();
            if (this.dir.getAxis() == Direction.Axis.Z) {
                d8 = 1.0;
            } else {
                d6 = 1.0;
            }

            d6 /= 32.0;
            d7 /= 32.0;
            d8 /= 32.0;
            this.setShape(new Box(d0 - d6, d1 - d7, d2 - d8, d0 + d6, d1 + d7, d2 + d8));
        }
    }

    private double getPositionOffset(int length) {
        return length % 32 == 0 ? 0.5 : 0.0;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.ticksSinceValidation++ == 100 && !this.world.isClient) {
            this.ticksSinceValidation = 0;
            if (!this.removed && !this.canSurvive()) {
                this.remove();
                this.onAttack(null);
            }
        }
    }

    public boolean canSurvive() {
        if (!this.world.getCollisions(this, this.getShape()).isEmpty()) {
            return false;
        }

        int i = Math.max(1, this.getWidth() / 16);
        int j = Math.max(1, this.getHeight() / 16);
        BlockPos blockpos = this.pos.offset(this.dir.getOpposite());
        Direction direction = this.dir.counterClockwiseY();

        for (int k = 0; k < i; k++) {
            for (int l = 0; l < j; l++) {
                BlockPos blockpos1 = blockpos.offset(direction, k).up(l);
                Block block = this.world.getBlockState(blockpos1).getBlock();
                if (!block.getMaterial().isSolid() && !DiodeBlock.isDiode(block)) {
                    return false;
                }
            }
        }

        for (Entity entity : this.world.getEntities(this, this.getShape())) {
            if (entity instanceof DecorationEntity) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean hasCollision() {
        return true;
    }

    @Override
    public boolean onPunched(Entity attacker) {
        return attacker instanceof PlayerEntity && this.takeDamage(DamageSource.player((PlayerEntity)attacker), 0.0F);
    }

    @Override
    public Direction getHorizontalFacing() {
        return this.dir;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (!this.removed && !this.world.isClient) {
            this.remove();
            this.markDamaged();
            this.onAttack(source.getAttacker());
        }

        return true;
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (!this.world.isClient && !this.removed && dx * dx + dy * dy + dz * dz > 0.0) {
            this.remove();
            this.onAttack(null);
        }
    }

    @Override
    public void addVelocity(double dx, double dy, double dz) {
        if (!this.world.isClient && !this.removed && dx * dx + dy * dy + dz * dz > 0.0) {
            this.remove();
            this.onAttack(null);
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putByte("Facing", (byte)this.dir.getIdHorizontal());
        nbt.putInt("TileX", this.getBlockPos().getX());
        nbt.putInt("TileY", this.getBlockPos().getY());
        nbt.putInt("TileZ", this.getBlockPos().getZ());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        this.pos = new BlockPos(nbt.getInt("TileX"), nbt.getInt("TileY"), nbt.getInt("TileZ"));
        Direction direction;
        if (nbt.contains("Direction", 99)) {
            direction = Direction.byIdHorizontal(nbt.getByte("Direction"));
            this.pos = this.pos.offset(direction);
        } else if (nbt.contains("Facing", 99)) {
            direction = Direction.byIdHorizontal(nbt.getByte("Facing"));
        } else {
            direction = Direction.byIdHorizontal(nbt.getByte("Dir"));
        }

        this.setDirection(direction);
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract void onAttack(Entity entity);

    @Override
    protected boolean shouldSetPositionOnLoad() {
        return false;
    }

    @Override
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        BlockPos blockpos = this.pos;
        this.pos = new BlockPos(x, y, z);
        if (!this.pos.equals(blockpos)) {
            this.updateShape();
            this.velocityDirty = true;
        }
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }
}
