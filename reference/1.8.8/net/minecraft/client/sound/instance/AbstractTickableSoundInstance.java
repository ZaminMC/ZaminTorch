package net.minecraft.client.sound.instance;

import net.minecraft.resource.Identifier;

public abstract class AbstractTickableSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
    protected boolean done = false;

    protected AbstractTickableSoundInstance(Identifier identifier) {
        super(identifier);
    }

    @Override
    public boolean isStopped() {
        return this.done;
    }
}
