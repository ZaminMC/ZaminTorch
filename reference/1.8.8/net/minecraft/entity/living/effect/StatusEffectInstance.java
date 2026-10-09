package net.minecraft.entity.living.effect;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StatusEffectInstance {
    private static final Logger LOGGER = LogManager.getLogger();
    private int effect;
    private int duration;
    private int amplifier;
    private boolean splash;
    private boolean ambient;
    private boolean permanent;
    private boolean particles;

    public StatusEffectInstance(int id, int duration) {
        this(id, duration, 0);
    }

    public StatusEffectInstance(int id, int duration, int amplifier) {
        this(id, duration, amplifier, false, true);
    }

    public StatusEffectInstance(int id, int duration, int amplifier, boolean ambient, boolean particles) {
        this.effect = id;
        this.duration = duration;
        this.amplifier = amplifier;
        this.ambient = ambient;
        this.particles = particles;
    }

    public StatusEffectInstance(StatusEffectInstance instance) {
        this.effect = instance.effect;
        this.duration = instance.duration;
        this.amplifier = instance.amplifier;
        this.ambient = instance.ambient;
        this.particles = instance.particles;
    }

    public void combine(StatusEffectInstance instance) {
        if (this.effect != instance.effect) {
            LOGGER.warn("This method should only be called for matching effects!");
        }

        if (instance.amplifier > this.amplifier) {
            this.amplifier = instance.amplifier;
            this.duration = instance.duration;
        } else if (instance.amplifier == this.amplifier && this.duration < instance.duration) {
            this.duration = instance.duration;
        } else if (!instance.ambient && this.ambient) {
            this.ambient = instance.ambient;
        }

        this.particles = instance.particles;
    }

    public int getId() {
        return this.effect;
    }

    public int getDuration() {
        return this.duration;
    }

    public int getAmplifier() {
        return this.amplifier;
    }

    public void setSplash(boolean splash) {
        this.splash = splash;
    }

    public boolean isAmbient() {
        return this.ambient;
    }

    public boolean hasParticles() {
        return this.particles;
    }

    public boolean tick(LivingEntity entity) {
        if (this.duration > 0) {
            if (StatusEffect.BY_ID[this.effect].shouldApply(this.duration, this.amplifier)) {
                this.apply(entity);
            }

            this.tickDuration();
        }

        return this.duration > 0;
    }

    private int tickDuration() {
        return --this.duration;
    }

    public void apply(LivingEntity entity) {
        if (this.duration > 0) {
            StatusEffect.BY_ID[this.effect].apply(entity, this.amplifier);
        }
    }

    public String getName() {
        return StatusEffect.BY_ID[this.effect].getTranslationKey();
    }

    @Override
    public int hashCode() {
        return this.effect;
    }

    @Override
    public String toString() {
        String s = "";
        if (this.getAmplifier() > 0) {
            s = this.getName() + " x " + (this.getAmplifier() + 1) + ", Duration: " + this.getDuration();
        } else {
            s = this.getName() + ", Duration: " + this.getDuration();
        }

        if (this.splash) {
            s = s + ", Splash: true";
        }

        if (!this.particles) {
            s = s + ", Particles: false";
        }

        return StatusEffect.BY_ID[this.effect].isUsable() ? "(" + s + ")" : s;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof StatusEffectInstance)) {
            return false;
        }

        StatusEffectInstance statuseffectinstance = (StatusEffectInstance)object;
        return this.effect == statuseffectinstance.effect
            && this.amplifier == statuseffectinstance.amplifier
            && this.duration == statuseffectinstance.duration
            && this.splash == statuseffectinstance.splash
            && this.ambient == statuseffectinstance.ambient;
    }

    public NbtCompound toNbt(NbtCompound nbt) {
        nbt.putByte("Id", (byte)this.getId());
        nbt.putByte("Amplifier", (byte)this.getAmplifier());
        nbt.putInt("Duration", this.getDuration());
        nbt.putBoolean("Ambient", this.isAmbient());
        nbt.putBoolean("ShowParticles", this.hasParticles());
        return nbt;
    }

    public static StatusEffectInstance fromNbt(NbtCompound nbt) {
        int i = nbt.getByte("Id");
        if (i >= 0 && i < StatusEffect.BY_ID.length && StatusEffect.BY_ID[i] != null) {
            int j = nbt.getByte("Amplifier");
            int k = nbt.getInt("Duration");
            boolean flag = nbt.getBoolean("Ambient");
            boolean flag1 = true;
            if (nbt.contains("ShowParticles", 1)) {
                flag1 = nbt.getBoolean("ShowParticles");
            }

            return new StatusEffectInstance(i, k, j, flag, flag1);
        } else {
            return null;
        }
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return this.permanent;
    }
}
