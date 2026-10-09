package net.minecraft.entity.vehicle;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.World;

public class RideableMinecartEntity extends MinecartEntity {
    public RideableMinecartEntity(World world) {
        super(world);
    }

    public RideableMinecartEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    public boolean interact(PlayerEntity player) {
        if (this.rider != null && this.rider instanceof PlayerEntity && this.rider != player) {
            return true;
        }

        if (this.rider != null && this.rider != player) {
            return false;
        }

        if (!this.world.isClient) {
            player.startRiding(this);
        }

        return true;
    }

    @Override
    public void onActivatorRail(int x, int y, int z, boolean powered) {
        if (powered) {
            if (this.rider != null) {
                this.rider.startRiding(null);
            }

            if (this.getDamagedTimer() == 0) {
                this.setDamagedSwingDirection(-this.getDamagedSwingDirection());
                this.setDamagedTimer(10);
                this.setDamage(50.0F);
                this.markDamaged();
            }
        }
    }

    @Override
    public MinecartEntity.Type getMinecartType() {
        return MinecartEntity.Type.RIDEABLE;
    }
}
