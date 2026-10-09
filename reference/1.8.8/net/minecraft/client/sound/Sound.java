package net.minecraft.client.sound;

import net.minecraft.resource.Identifier;

public class Sound {
    private final Identifier location;
    private final boolean stream;
    private double volume;
    private double pitch;

    public Sound(Identifier location, double volume, double pitch, boolean stream) {
        this.location = location;
        this.volume = volume;
        this.pitch = pitch;
        this.stream = stream;
    }

    public Sound(Sound sound) {
        this.location = sound.location;
        this.volume = sound.volume;
        this.pitch = sound.pitch;
        this.stream = sound.stream;
    }

    public Identifier getLocation() {
        return this.location;
    }

    public double getVolume() {
        return this.volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getPitch() {
        return this.pitch;
    }

    public void setPitch(double pitch) {
        this.pitch = pitch;
    }

    public boolean isStream() {
        return this.stream;
    }
}
