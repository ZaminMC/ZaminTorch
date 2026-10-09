package net.minecraft.client.sound.system;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.resource.Identifier;
import net.minecraft.util.registry.MappedRegistry;

public class SoundRegistry extends MappedRegistry<Identifier, SoundPool> {
    private Map<Identifier, SoundPool> sounds;

    @Override
    protected Map<Identifier, SoundPool> createMap() {
        this.sounds = Maps.newHashMap();
        return this.sounds;
    }

    public void add(SoundPool pool) {
        this.put(pool.getLocation(), pool);
    }

    public void clear() {
        this.sounds.clear();
    }
}
