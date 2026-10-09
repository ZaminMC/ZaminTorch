package net.minecraft.client.sound.system;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.client.sound.SoundContainer;
import net.minecraft.client.sound.SoundList;
import net.minecraft.client.sound.SoundListDeserializer;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.Tickable;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SoundManager implements ResourceReloadListener, Tickable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(SoundList.class, new SoundListDeserializer()).create();
    private static final ParameterizedType TYPE = new ParameterizedType() {
        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{String.class, SoundList.class};
        }

        @Override
        public Type getRawType() {
            return Map.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    };
    public static final Sound MISSING_SOUND = new Sound(new Identifier("meta:missing_sound"), 0.0, 0.0, false);
    private final SoundRegistry registry = new SoundRegistry();
    private final SoundEngine engine;
    private final ResourceManager resourceManager;

    public SoundManager(ResourceManager resourceManager, GameOptions options) {
        this.resourceManager = resourceManager;
        this.engine = new SoundEngine(this, options);
    }

    @Override
    public void reload(ResourceManager resourceManager) {
        this.engine.reload();
        this.registry.clear();

        for (String s : resourceManager.getNamespaces()) {
            try {
                for (Resource resource : resourceManager.getResources(new Identifier(s, "sounds.json"))) {
                    try {
                        Map<String, SoundList> map = this.loadContainers(resource.asStream());

                        for (Entry<String, SoundList> entry : map.entrySet()) {
                            this.register(new Identifier(s, entry.getKey()), entry.getValue());
                        }
                    } catch (RuntimeException runtimeexception) {
                        LOGGER.warn("Invalid sounds.json", runtimeexception);
                    }
                }
            } catch (IOException ioexception) {
            }
        }
    }

    protected Map<String, SoundList> loadContainers(InputStream is) {
        try {
            return GSON.fromJson(new InputStreamReader(is), TYPE);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private void register(Identifier location, SoundList sounds) {
        boolean flag = !this.registry.containsKey(location);
        SoundPool soundpool;
        if (!flag && !sounds.isReplacable()) {
            soundpool = this.registry.get(location);
        } else {
            if (!flag) {
                LOGGER.debug("Replaced sound event location {}", new Object[]{location});
            }

            soundpool = new SoundPool(location, 1.0, 1.0, sounds.getCategory());
            this.registry.add(soundpool);
        }

        for (final SoundList.Sound soundlist$sound : sounds.getSounds()) {
            String s = soundlist$sound.getName();
            Identifier identifier = new Identifier(s);
            final String s1 = s.contains(":") ? identifier.getNamespace() : location.getNamespace();
            SoundContainer soundContainer;
            switch (soundlist$sound.getType()) {
                case FILE:
                    Identifier identifier1 = new Identifier(s1, "sounds/" + identifier.getPath() + ".ogg");
                    InputStream inputstream = null;

                    try {
                        inputstream = this.resourceManager.getResource(identifier1).asStream();
                    } catch (FileNotFoundException filenotfoundexception) {
                        LOGGER.warn("File {} does not exist, cannot add it to event {}", identifier1, location);
                        continue;
                    } catch (IOException ioexception) {
                        LOGGER.warn("Could not load sound file " + identifier1 + ", cannot add it to event " + location, ioexception);
                        continue;
                    } finally {
                        IOUtils.closeQuietly(inputstream);
                    }

                    soundContainer = new FileSoundContainer(
                        new Sound(identifier1, soundlist$sound.getPitch(), soundlist$sound.getVolume(), soundlist$sound.isStream()),
                        soundlist$sound.getWeight()
                    );
                    break;
                case EVENT:
                    soundContainer = new SoundContainer<Sound>() {
                        final Identifier id = new Identifier(s1, soundlist$sound.getName());

                        @Override
                        public int getWeight() {
                            SoundPool soundpool1 = SoundManager.this.registry.get(this.id);
                            return soundpool1 == null ? 0 : soundpool1.getWeight();
                        }

                        public Sound get() {
                            SoundPool soundpool1 = SoundManager.this.registry.get(this.id);
                            return soundpool1 == null ? SoundManager.MISSING_SOUND : soundpool1.get();
                        }
                    };
                    break;
                default:
                    throw new IllegalStateException("IN YOU FACE");
            }

            soundpool.add(soundContainer);
        }
    }

    public SoundPool get(Identifier location) {
        return this.registry.get(location);
    }

    public void play(SoundInstance sound) {
        this.engine.play(sound);
    }

    public void play(SoundInstance sound, int delay) {
        this.engine.playLater(sound, delay);
    }

    public void updateListener(PlayerEntity player, float tickDelta) {
        this.engine.update(player, tickDelta);
    }

    public void pause() {
        this.engine.pause();
    }

    public void stop() {
        this.engine.stop();
    }

    public void close() {
        this.engine.close();
    }

    @Override
    public void tick() {
        this.engine.tick();
    }

    public void resume() {
        this.engine.resume();
    }

    public void setVolume(SoundCategory category, float volume) {
        if (category == SoundCategory.MASTER && volume <= 0.0F) {
            this.stop();
        }

        this.engine.setVolume(category, volume);
    }

    public void stop(SoundInstance sound) {
        this.engine.stop(sound);
    }

    public SoundPool getRandom(SoundCategory... categories) {
        List<SoundPool> list = Lists.newArrayList();

        for (Identifier identifier : this.registry.keySet()) {
            SoundPool soundpool = this.registry.get(identifier);
            if (ArrayUtils.contains(categories, soundpool.getCategory())) {
                list.add(soundpool);
            }
        }

        return list.isEmpty() ? null : list.get(new Random().nextInt(list.size()));
    }

    public boolean isPlaying(SoundInstance sound) {
        return this.engine.isPlaying(sound);
    }
}
