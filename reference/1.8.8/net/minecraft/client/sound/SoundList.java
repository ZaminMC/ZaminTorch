package net.minecraft.client.sound;

import com.google.common.collect.Lists;
import java.util.List;

public class SoundList {
    private final List<SoundList.Sound> sounds = Lists.newArrayList();
    private boolean replacable;
    private SoundCategory category;

    public List<SoundList.Sound> getSounds() {
        return this.sounds;
    }

    public boolean isReplacable() {
        return this.replacable;
    }

    public void setReplacable(boolean replacable) {
        this.replacable = replacable;
    }

    public SoundCategory getCategory() {
        return this.category;
    }

    public void setCategory(SoundCategory category) {
        this.category = category;
    }

    public static class Sound {
        private String name;
        private float volume = 1.0F;
        private float pitch = 1.0F;
        private int weight = 1;
        private SoundList.Sound.Type type = SoundList.Sound.Type.FILE;
        private boolean stream = false;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public float getVolume() {
            return this.volume;
        }

        public void setVolume(float volume) {
            this.volume = volume;
        }

        public float getPitch() {
            return this.pitch;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        public int getWeight() {
            return this.weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public SoundList.Sound.Type getType() {
            return this.type;
        }

        public void setType(SoundList.Sound.Type type) {
            this.type = type;
        }

        public boolean isStream() {
            return this.stream;
        }

        public void setStream(boolean stream) {
            this.stream = stream;
        }

        public enum Type {
            FILE("file"),
            EVENT("event");

            private final String name;

            Type(String name) {
                this.name = name;
            }

            public static SoundList.Sound.Type byName(String name) {
                for (SoundList.Sound.Type soundlist$sound$type : values()) {
                    if (soundlist$sound$type.name.equals(name)) {
                        return soundlist$sound$type;
                    }
                }

                return null;
            }
        }
    }
}
