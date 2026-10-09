package net.minecraft.client.sound.instance;

import net.minecraft.resource.Identifier;

public abstract class AbstractSoundInstance implements SoundInstance {
    protected final Identifier location;
    protected float volume = 1.0F;
    protected float pitch = 1.0F;
    protected float x;
    protected float y;
    protected float z;
    protected boolean looping = false;
    protected int period = 0;
    protected SoundInstance.Attenuation attenuation = SoundInstance.Attenuation.LINEAR;

    protected AbstractSoundInstance(Identifier location) {
        this.location = location;
    }

    @Override
    public Identifier getLocation() {
        return this.location;
    }

    @Override
    public boolean isLooping() {
        return this.looping;
    }

    @Override
    public int getPeriod() {
        return this.period;
    }

    @Override
    public float getVolume() {
        return this.volume;
    }

    @Override
    public float getPitch() {
        return this.pitch;
    }

    @Override
    public float getX() {
        return this.x;
    }

    @Override
    public float getY() {
        return this.y;
    }

    @Override
    public float getZ() {
        return this.z;
    }

    @Override
    public SoundInstance.Attenuation getAttenuationType() {
        return this.attenuation;
    }
}
