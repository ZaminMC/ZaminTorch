package net.minecraft.client.sound.instance;

import net.minecraft.util.Tickable;

public interface TickableSoundInstance extends SoundInstance, Tickable {
    boolean isStopped();
}
