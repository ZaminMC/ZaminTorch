package net.minecraft.client.sound;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.resource.Identifier;

public class SoundPool implements SoundContainer<Sound> {
    private final List<SoundContainer<Sound>> containers = Lists.newArrayList();
    private final Random random = new Random();
    private final Identifier location;
    private final SoundCategory category;
    private double volumeMultiplier;
    private double pitchMultiplier;

    public SoundPool(Identifier location, double volumeMultiplier, double pitchMultiplier, SoundCategory category) {
        this.location = location;
        this.pitchMultiplier = pitchMultiplier;
        this.volumeMultiplier = volumeMultiplier;
        this.category = category;
    }

    @Override
    public int getWeight() {
        int i = 0;

        for (SoundContainer<Sound> soundcontainer : this.containers) {
            i += soundcontainer.getWeight();
        }

        return i;
    }

    public Sound get() {
        int i = this.getWeight();
        if (!this.containers.isEmpty() && i != 0) {
            int j = this.random.nextInt(i);

            for (SoundContainer<Sound> soundcontainer : this.containers) {
                j -= soundcontainer.getWeight();
                if (j < 0) {
                    Sound sound = soundcontainer.get();
                    sound.setVolume(sound.getVolume() * this.volumeMultiplier);
                    sound.setPitch(sound.getPitch() * this.pitchMultiplier);
                    return sound;
                }
            }

            return SoundManager.MISSING_SOUND;
        } else {
            return SoundManager.MISSING_SOUND;
        }
    }

    public void add(SoundContainer<Sound> sound) {
        this.containers.add(sound);
    }

    public Identifier getLocation() {
        return this.location;
    }

    public SoundCategory getCategory() {
        return this.category;
    }
}
