package net.minecraft.entity.vehicle;

import net.minecraft.block.Blocks;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class FurnaceMinecartEntity extends MinecartEntity {
    private int fuel;
    public double pushX;
    public double pushZ;

    public FurnaceMinecartEntity(World world) {
        super(world);
    }

    public FurnaceMinecartEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    public MinecartEntity.Type getMinecartType() {
        return MinecartEntity.Type.FURNACE;
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, new Byte((byte)0));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fuel > 0) {
            this.fuel--;
        }

        if (this.fuel <= 0) {
            this.pushX = this.pushZ = 0.0;
        }

        this.setLit(this.fuel > 0);
        if (this.isLit() && this.random.nextInt(4) == 0) {
            this.world.addParticle(ParticleType.SMOKE_LARGE, this.x, this.y + 0.8, this.z, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected double getMaxSpeed() {
        return 0.2;
    }

    @Override
    public void dropItems(DamageSource damageSource) {
        super.dropItems(damageSource);
        if (!damageSource.isExplosive() && this.world.getGameRules().getBoolean("doEntityDrops")) {
            this.dropItem(new ItemStack(Blocks.FURNACE, 1), 0.0F);
        }
    }

    @Override
    protected void moveOnRail(BlockPos pos, BlockState state) {
        super.moveOnRail(pos, state);
        double d0 = this.pushX * this.pushX + this.pushZ * this.pushZ;
        if (d0 > 1.0E-4 && this.velocityX * this.velocityX + this.velocityZ * this.velocityZ > 0.001) {
            d0 = MathHelper.sqrt(d0);
            this.pushX /= d0;
            this.pushZ /= d0;
            if (this.pushX * this.velocityX + this.pushZ * this.velocityZ < 0.0) {
                this.pushX = 0.0;
                this.pushZ = 0.0;
            } else {
                double d1 = d0 / this.getMaxSpeed();
                this.pushX *= d1;
                this.pushZ *= d1;
            }
        }
    }

    @Override
    protected void applySlowdown() {
        double d0 = this.pushX * this.pushX + this.pushZ * this.pushZ;
        if (d0 > 1.0E-4) {
            d0 = MathHelper.sqrt(d0);
            this.pushX /= d0;
            this.pushZ /= d0;
            double d1 = 1.0;
            this.velocityX *= 0.8F;
            this.velocityY *= 0.0;
            this.velocityZ *= 0.8F;
            this.velocityX = this.velocityX + this.pushX * d1;
            this.velocityZ = this.velocityZ + this.pushZ * d1;
        } else {
            this.velocityX *= 0.98F;
            this.velocityY *= 0.0;
            this.velocityZ *= 0.98F;
        }

        super.applySlowdown();
    }

    @Override
    public boolean interact(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() == Items.COAL) {
            if (!player.abilities.creativeMode && --itemstack.size == 0) {
                player.inventory.setItem(player.inventory.selectedSlot, null);
            }

            this.fuel += 3600;
        }

        this.pushX = this.x - player.x;
        this.pushZ = this.z - player.z;
        return true;
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putDouble("PushX", this.pushX);
        nbt.putDouble("PushZ", this.pushZ);
        nbt.putShort("Fuel", (short)this.fuel);
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.pushX = nbt.getDouble("PushX");
        this.pushZ = nbt.getDouble("PushZ");
        this.fuel = nbt.getShort("Fuel");
    }

    protected boolean isLit() {
        return (this.syncedData.getByte(16) & 1) != 0;
    }

    protected void setLit(boolean lit) {
        if (lit) {
            this.syncedData.update(16, (byte)(this.syncedData.getByte(16) | 1));
        } else {
            this.syncedData.update(16, (byte)(this.syncedData.getByte(16) & -2));
        }
    }

    @Override
    public BlockState getDefaultDisplayBlock() {
        return (this.isLit() ? Blocks.LIT_FURNACE : Blocks.FURNACE).defaultState().set(FurnaceBlock.FACING, Direction.NORTH);
    }
}
