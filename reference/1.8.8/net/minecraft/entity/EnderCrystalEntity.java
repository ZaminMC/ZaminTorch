package net.minecraft.entity;

import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.dimension.TheEndDimension;

public class EnderCrystalEntity extends Entity {
    public int renderTicks;
    public int explosionCountdown;

    public EnderCrystalEntity(World world) {
        super(world);
        this.blocksBuilding = true;
        this.setSize(2.0F, 2.0F);
        this.explosionCountdown = 5;
        this.renderTicks = this.random.nextInt(100000);
    }

    public EnderCrystalEntity(World world, double x, double y, double z) {
        this(world);
        this.setPosition(x, y, z);
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    protected void registerSyncedData() {
        this.syncedData.register(8, this.explosionCountdown);
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.renderTicks++;
        this.syncedData.update(8, this.explosionCountdown);
        int i = MathHelper.floor(this.x);
        int j = MathHelper.floor(this.y);
        int k = MathHelper.floor(this.z);
        if (this.world.dimension instanceof TheEndDimension && this.world.getBlockState(new BlockPos(i, j, k)).getBlock() != Blocks.FIRE) {
            this.world.setBlockState(new BlockPos(i, j, k), Blocks.FIRE.defaultState());
        }
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
    }

    @Override
    public boolean hasCollision() {
        return true;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (!this.removed && !this.world.isClient) {
            this.explosionCountdown = 0;
            if (this.explosionCountdown <= 0) {
                this.remove();
                if (!this.world.isClient) {
                    this.world.explode(null, this.x, this.y, this.z, 6.0F, true);
                }
            }
        }

        return true;
    }
}
