package net.minecraft.client.sound.instance;

import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.resource.Identifier;

public class GuardianAttackSoundInstance extends AbstractTickableSoundInstance {
    private final GuardianEntity guardian;

    public GuardianAttackSoundInstance(GuardianEntity guardian) {
        super(new Identifier("minecraft:mob.guardian.attack"));
        this.guardian = guardian;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = true;
        this.period = 0;
    }

    @Override
    public void tick() {
        if (!this.guardian.removed && this.guardian.hasBeamTarget()) {
            this.x = (float)this.guardian.x;
            this.y = (float)this.guardian.y;
            this.z = (float)this.guardian.z;
            float f = this.guardian.getBeamProgress(0.0F);
            this.volume = 0.0F + 1.0F * f * f;
            this.pitch = 0.7F + 0.5F * f;
        } else {
            this.done = true;
        }
    }
}
