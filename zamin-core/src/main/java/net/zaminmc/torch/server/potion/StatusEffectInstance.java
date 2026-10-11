package net.zaminmc.torch.server.potion;

/**
 * One status effect on one body (Slice 11b — the reference port of
 * entity/living/effect/StatusEffectInstance.java): the id, the countdown
 * duration, the amplifier, the splash origin flag. The reference's combine
 * rule (lines 42-57) drives the re-application: a strictly stronger
 * amplifier replaces (duration reset), an equal amplifier with a longer
 * duration extends, an ambient instance un-ambientifies.
 */
public final class StatusEffectInstance {

    private final int effect;
    private int duration;
    private int amplifier;
    private boolean splash;
    private boolean ambient;
    private boolean particles = true;

    public StatusEffectInstance(int effect, int duration, int amplifier) {
        this.effect = effect;
        this.duration = duration;
        this.amplifier = amplifier;
    }

    public StatusEffectInstance(int effect, int duration, int amplifier, boolean splash) {
        this(effect, duration, amplifier);
        this.splash = splash;
    }

    public int id() {
        return effect;
    }

    public int duration() {
        return duration;
    }

    public int amplifier() {
        return amplifier;
    }

    public boolean splash() {
        return splash;
    }

    public void setSplash(boolean splash) {
        this.splash = splash;
    }

    public boolean isAmbient() {
        return ambient;
    }

    public boolean hasParticles() {
        return particles;
    }

    /**
     * The merge (StatusEffectInstance.combine lines 42-57): the stronger
     * amplifier wins outright (duration reset), the equal amplifier keeps
     * the longer duration, the ambient clears when the newcomer is not.
     */
    public void combine(StatusEffectInstance other) {
        if (other.amplifier > this.amplifier) {
            this.amplifier = other.amplifier;
            this.duration = other.duration;
        } else if (other.amplifier == this.amplifier && this.duration < other.duration) {
            this.duration = other.duration;
        } else if (!other.ambient && this.ambient) {
            this.ambient = other.ambient;
        }
        this.particles = other.particles;
    }

    /**
     * One tick of the effect on the body (StatusEffectInstance.tick lines
     * 83-93): the cadenced application through the sink, then the countdown.
     * @return whether the effect still lives.
     */
    public boolean tick(java.util.function.Consumer<StatusEffectInstance> applier) {
        if (duration > 0) {
            if (StatusEffects.shouldApply(effect, duration, amplifier)) {
                applier.accept(this);
            }
            --duration;
        }
        return duration > 0;
    }

    /** @return a copy carrying the same state (the reference's copy ctor). */
    public StatusEffectInstance copy() {
        StatusEffectInstance copy = new StatusEffectInstance(effect, duration, amplifier, splash);
        copy.ambient = this.ambient;
        copy.particles = this.particles;
        return copy;
    }
}
