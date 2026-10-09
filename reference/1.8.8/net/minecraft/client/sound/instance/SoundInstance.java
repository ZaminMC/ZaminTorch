package net.minecraft.client.sound.instance;

import net.minecraft.resource.Identifier;

public interface SoundInstance {
    Identifier getLocation();

    boolean isLooping();

    int getPeriod();

    float getVolume();

    float getPitch();

    float getX();

    float getY();

    float getZ();

    SoundInstance.Attenuation getAttenuationType();

    enum Attenuation {
        NONE(0),
        LINEAR(2);

        private final int attenuation;

        Attenuation(int attenuation) {
            this.attenuation = attenuation;
        }

        public int get() {
            return this.attenuation;
        }
    }
}
