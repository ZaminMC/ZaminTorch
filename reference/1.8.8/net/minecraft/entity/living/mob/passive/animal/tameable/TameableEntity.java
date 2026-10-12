package net.minecraft.entity.living.mob.passive.animal.tameable;

import java.util.UUID;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.AnimalEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.server.UserConverter;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.world.World;

public abstract class TameableEntity extends AnimalEntity implements Tameable {
    protected SitGoal sitGoal = new SitGoal(this);

    public TameableEntity(World world) {
        super(world);
        this.onTamedChanged();
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, (byte)0);
        this.syncedData.register(17, "");
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        if (this.getOwnerName() == null) {
            nbt.putString("OwnerUUID", "");
        } else {
            nbt.putString("OwnerUUID", this.getOwnerName());
        }

        nbt.putBoolean("Sitting", this.isSitting());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        String s = "";
        if (nbt.contains("OwnerUUID", 8)) {
            s = nbt.getString("OwnerUUID");
        } else {
            String s1 = nbt.getString("Owner");
            s = UserConverter.convertMobOwner(s1);
        }

        if (s.length() > 0) {
            this.setOwnerName(s);
            this.setTamed(true);
        }

        this.sitGoal.setEnabledWithOwner(nbt.getBoolean("Sitting"));
        this.setSitting(nbt.getBoolean("Sitting"));
    }

    protected void addTamingParticles(boolean success) {
        ParticleType particletype = ParticleType.HEART;
        if (!success) {
            particletype = ParticleType.SMOKE_NORMAL;
        }

        for (int i = 0; i < 7; i++) {
            double d0 = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            this.world
                .addParticle(
                    particletype,
                    this.x + this.random.nextFloat() * this.width * 2.0F - this.width,
                    this.y + 0.5 + this.random.nextFloat() * this.height,
                    this.z + this.random.nextFloat() * this.width * 2.0F - this.width,
                    d0,
                    d1,
                    d2
                );
        }
    }

    @Override
    public void doEvent(byte event) {
        if (event == 7) {
            this.addTamingParticles(true);
        } else if (event == 6) {
            this.addTamingParticles(false);
        } else {
            super.doEvent(event);
        }
    }

    public boolean isTamed() {
        return (this.syncedData.getByte(16) & 4) != 0;
    }

    public void setTamed(boolean tamed) {
        byte b0 = this.syncedData.getByte(16);
        if (tamed) {
            this.syncedData.update(16, (byte)(b0 | 4));
        } else {
            this.syncedData.update(16, (byte)(b0 & -5));
        }

        this.onTamedChanged();
    }

    protected void onTamedChanged() {
    }

    public boolean isSitting() {
        return (this.syncedData.getByte(16) & 1) != 0;
    }

    public void setSitting(boolean sitting) {
        byte b0 = this.syncedData.getByte(16);
        if (sitting) {
            this.syncedData.update(16, (byte)(b0 | 1));
        } else {
            this.syncedData.update(16, (byte)(b0 & -2));
        }
    }

    @Override
    public String getOwnerName() {
        return this.syncedData.getString(17);
    }

    public void setOwnerName(String name) {
        this.syncedData.update(17, name);
    }

    public LivingEntity getOwner() {
        try {
            UUID uuid = UUID.fromString(this.getOwnerName());
            return uuid == null ? null : this.world.getPlayer(uuid);
        } catch (IllegalArgumentException illegalargumentexception) {
            return null;
        }
    }

    public boolean isOwner(LivingEntity entity) {
        return entity == this.getOwner();
    }

    public SitGoal getSitGoal() {
        return this.sitGoal;
    }

    public boolean shouldAttack(LivingEntity attackedEntity, LivingEntity attacker) {
        return true;
    }

    @Override
    public AbstractTeam getScoreboardTeam() {
        if (this.isTamed()) {
            LivingEntity livingentity = this.getOwner();
            if (livingentity != null) {
                return livingentity.getScoreboardTeam();
            }
        }

        return super.getScoreboardTeam();
    }

    @Override
    public boolean isInSameTeam(LivingEntity entity) {
        if (this.isTamed()) {
            LivingEntity livingentity = this.getOwner();
            if (entity == livingentity) {
                return true;
            }

            if (livingentity != null) {
                return livingentity.isInSameTeam(entity);
            }
        }

        return super.isInSameTeam(entity);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.world.isClient
            && this.world.getGameRules().getBoolean("showDeathMessages")
            && this.hasCustomName()
            && this.getOwner() instanceof ServerPlayerEntity) {
            ((ServerPlayerEntity)this.getOwner()).sendMessage(this.getDamageTracker().getDeathMessage());
        }

        super.die(source);
    }
}
