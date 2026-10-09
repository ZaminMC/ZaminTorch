package net.minecraft.client.sound;

import com.google.common.collect.Maps;
import java.util.Map;

public enum SoundCategory {
    MASTER("master", 0),
    MUSIC("music", 1),
    RECORDS("record", 2),
    WEATHER("weather", 3),
    BLOCKS("block", 4),
    MOBS("hostile", 5),
    ANIMALS("neutral", 6),
    PLAYERS("player", 7),
    AMBIENT("ambient", 8);

    private static final Map<String, SoundCategory> BY_NAME = Maps.newHashMap();
    private static final Map<Integer, SoundCategory> BY_ID = Maps.newHashMap();
    private final String name;
    private final int id;

    SoundCategory(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public int getId() {
        return this.id;
    }

    public static SoundCategory byName(String name) {
        return BY_NAME.get(name);
    }

    static {
        for (SoundCategory soundcategory : values()) {
            if (BY_NAME.containsKey(soundcategory.getName()) || BY_ID.containsKey(soundcategory.getId())) {
                throw new Error("Clash in Sound Category ID & Name pools! Cannot insert " + soundcategory);
            }

            BY_NAME.put(soundcategory.getName(), soundcategory);
            BY_ID.put(soundcategory.getId(), soundcategory);
        }
    }
}
