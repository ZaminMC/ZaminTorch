package net.minecraft.client.sound.instance;

import net.minecraft.resource.Identifier;

public class SimpleSoundInstance extends AbstractSoundInstance {
    public static SimpleSoundInstance of(Identifier id, float pitch) {
        return new SimpleSoundInstance(id, 0.25F, pitch, false, 0, SoundInstance.Attenuation.NONE, 0.0F, 0.0F, 0.0F);
    }

    public static SimpleSoundInstance of(Identifier id) {
        return new SimpleSoundInstance(id, 1.0F, 1.0F, false, 0, SoundInstance.Attenuation.NONE, 0.0F, 0.0F, 0.0F);
    }

    public static SimpleSoundInstance of(Identifier id, float x, float y, float z) {
        return new SimpleSoundInstance(id, 4.0F, 1.0F, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z);
    }

    public SimpleSoundInstance(Identifier id, float volume, float pitch, float x, float y, float z) {
        this(id, volume, pitch, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z);
    }

    private SimpleSoundInstance(
        Identifier id, float volume, float pitch, boolean repeat, int period, SoundInstance.Attenuation attenuation, float x, float y, float z
    ) {
        super(id);
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.looping = repeat;
        this.period = period;
        this.attenuation = attenuation;
    }
}
