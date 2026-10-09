package net.minecraft.client.sound.system;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import io.netty.util.internal.ThreadLocalRandom;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.instance.TickableSoundInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;
import paulscode.sound.SoundSystemLogger;
import paulscode.sound.Source;
import paulscode.sound.codecs.CodecJOrbis;
import paulscode.sound.libraries.LibraryLWJGLOpenAL;

public class SoundEngine {
    private static final Marker MARKER = MarkerManager.getMarker("SOUNDS");
    private static final Logger LOGGER = LogManager.getLogger();
    private final SoundManager manager;
    private final GameOptions options;
    private SoundEngine.System system;
    private boolean started;
    private int ticks = 0;
    private final Map<String, SoundInstance> eventsByChannel = HashBiMap.create();
    private final Map<SoundInstance, String> channelsByEvent = ((BiMap)this.eventsByChannel).inverse();
    private Map<SoundInstance, Sound> sources = Maps.newHashMap();
    private final Multimap<SoundCategory, String> channelsByCategory = HashMultimap.create();
    private final List<TickableSoundInstance> tickableEvents = Lists.newArrayList();
    private final Map<SoundInstance, Integer> delayedEvents = Maps.newHashMap();
    private final Map<String, Integer> soundBuffer = Maps.newHashMap();

    public SoundEngine(SoundManager manager, GameOptions options) {
        this.manager = manager;
        this.options = options;

        try {
            SoundSystemConfig.addLibrary(LibraryLWJGLOpenAL.class);
            SoundSystemConfig.setCodec("ogg", CodecJOrbis.class);
        } catch (SoundSystemException soundsystemexception) {
            LOGGER.error(MARKER, "Error linking with the LibraryJavaSound plug-in", soundsystemexception);
        }
    }

    public void reload() {
        this.close();
        this.start();
    }

    private synchronized void start() {
        if (!this.started) {
            try {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        SoundSystemConfig.setLogger(new SoundSystemLogger() {
                            @Override
                            public void message(String message, int indent) {
                                if (!message.isEmpty()) {
                                    SoundEngine.LOGGER.info(message);
                                }
                            }

                            @Override
                            public void importantMessage(String message, int indent) {
                                if (!message.isEmpty()) {
                                    SoundEngine.LOGGER.warn(message);
                                }
                            }

                            @Override
                            public void errorMessage(String classname, String message, int indent) {
                                if (!message.isEmpty()) {
                                    SoundEngine.LOGGER.error("Error in class '" + classname + "'");
                                    SoundEngine.LOGGER.error(message);
                                }
                            }
                        });
                        SoundEngine.this.system = SoundEngine.this.new System();
                        SoundEngine.this.started = true;
                        SoundEngine.this.system.setMasterVolume(SoundEngine.this.options.getSoundCategoryVolume(SoundCategory.MASTER));
                        SoundEngine.LOGGER.info(SoundEngine.MARKER, "Sound engine started");
                    }
                }, "Sound Library Loader").start();
            } catch (RuntimeException runtimeexception) {
                LOGGER.error(MARKER, "Error starting SoundSystem. Turning off sounds & music", runtimeexception);
                this.options.setSoundCategoryVolume(SoundCategory.MASTER, 0.0F);
                this.options.save();
            }
        }
    }

    private float getVolume(SoundCategory category) {
        return category != null && category != SoundCategory.MASTER ? this.options.getSoundCategoryVolume(category) : 1.0F;
    }

    public void setVolume(SoundCategory category, float volume) {
        if (this.started) {
            if (category == SoundCategory.MASTER) {
                this.system.setMasterVolume(volume);
            } else {
                for (String s : this.channelsByCategory.get(category)) {
                    SoundInstance soundinstance = this.eventsByChannel.get(s);
                    float f = this.getVolume(soundinstance, this.sources.get(soundinstance), category);
                    if (f <= 0.0F) {
                        this.stop(soundinstance);
                    } else {
                        this.system.setVolume(s, f);
                    }
                }
            }
        }
    }

    public void close() {
        if (this.started) {
            this.stop();
            this.system.cleanup();
            this.started = false;
        }
    }

    public void stop() {
        if (this.started) {
            for (String s : this.eventsByChannel.keySet()) {
                this.system.stop(s);
            }

            this.eventsByChannel.clear();
            this.delayedEvents.clear();
            this.tickableEvents.clear();
            this.channelsByCategory.clear();
            this.sources.clear();
            this.soundBuffer.clear();
        }
    }

    public void tick() {
        this.ticks++;

        for (TickableSoundInstance tickablesoundinstance : this.tickableEvents) {
            tickablesoundinstance.tick();
            if (tickablesoundinstance.isStopped()) {
                this.stop(tickablesoundinstance);
            } else {
                String s = this.channelsByEvent.get(tickablesoundinstance);
                this.system
                    .setVolume(
                        s,
                        this.getVolume(
                            tickablesoundinstance, this.sources.get(tickablesoundinstance), this.manager.get(tickablesoundinstance.getLocation()).getCategory()
                        )
                    );
                this.system.setPitch(s, this.getPitch(tickablesoundinstance, this.sources.get(tickablesoundinstance)));
                this.system.setPosition(s, tickablesoundinstance.getX(), tickablesoundinstance.getY(), tickablesoundinstance.getZ());
            }
        }

        Iterator<Entry<String, SoundInstance>> iterator = this.eventsByChannel.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<String, SoundInstance> entry = iterator.next();
            String s1 = entry.getKey();
            SoundInstance soundinstance = entry.getValue();
            if (!this.system.playing(s1)) {
                int i = this.soundBuffer.get(s1);
                if (i <= this.ticks) {
                    int j = soundinstance.getPeriod();
                    if (soundinstance.isLooping() && j > 0) {
                        this.delayedEvents.put(soundinstance, this.ticks + j);
                    }

                    iterator.remove();
                    LOGGER.debug(MARKER, "Removed channel {} because it's not playing anymore", s1);
                    this.system.removeSource(s1);
                    this.soundBuffer.remove(s1);
                    this.sources.remove(soundinstance);

                    try {
                        this.channelsByCategory.remove(this.manager.get(soundinstance.getLocation()).getCategory(), s1);
                    } catch (RuntimeException runtimeexception) {
                    }

                    if (soundinstance instanceof TickableSoundInstance) {
                        this.tickableEvents.remove(soundinstance);
                    }
                }
            }
        }

        Iterator<Entry<SoundInstance, Integer>> iterator1 = this.delayedEvents.entrySet().iterator();

        while (iterator1.hasNext()) {
            Entry<SoundInstance, Integer> entry1 = iterator1.next();
            if (this.ticks >= entry1.getValue()) {
                SoundInstance soundinstance1 = entry1.getKey();
                if (soundinstance1 instanceof TickableSoundInstance) {
                    ((TickableSoundInstance)soundinstance1).tick();
                }

                this.play(soundinstance1);
                iterator1.remove();
            }
        }
    }

    public boolean isPlaying(SoundInstance sound) {
        if (!this.started) {
            return false;
        }

        String s = this.channelsByEvent.get(sound);
        return s != null && (this.system.playing(s) || this.soundBuffer.containsKey(s) && this.soundBuffer.get(s) <= this.ticks);
    }

    public void stop(SoundInstance sound) {
        if (this.started) {
            String s = this.channelsByEvent.get(sound);
            if (s != null) {
                this.system.stop(s);
            }
        }
    }

    public void play(SoundInstance sound) {
        if (this.started) {
            if (this.system.getMasterVolume() <= 0.0F) {
                LOGGER.debug(MARKER, "Skipped playing soundEvent: {}, master volume was zero", sound.getLocation());
            } else {
                SoundPool soundpool = this.manager.get(sound.getLocation());
                if (soundpool == null) {
                    LOGGER.warn(MARKER, "Unable to play unknown soundEvent: {}", sound.getLocation());
                } else {
                    Sound soundx = soundpool.get();
                    if (soundx == SoundManager.MISSING_SOUND) {
                        LOGGER.warn(MARKER, "Unable to play empty soundEvent: {}", soundpool.getLocation());
                    } else {
                        float f = sound.getVolume();
                        float f1 = 16.0F;
                        if (f > 1.0F) {
                            f1 *= f;
                        }

                        SoundCategory soundcategory = soundpool.getCategory();
                        float f2 = this.getVolume(sound, soundx, soundcategory);
                        double d0 = this.getPitch(sound, soundx);
                        Identifier identifier = soundx.getLocation();
                        if (f2 == 0.0F) {
                            LOGGER.debug(MARKER, "Skipped playing sound {}, volume was zero.", identifier);
                        } else {
                            boolean flag = sound.isLooping() && sound.getPeriod() == 0;
                            String s = MathHelper.nextUuid(ThreadLocalRandom.current()).toString();
                            if (soundx.isStream()) {
                                this.system
                                    .newStreamingSource(
                                        false,
                                        s,
                                        getSoundUrl(identifier),
                                        identifier.toString(),
                                        flag,
                                        sound.getX(),
                                        sound.getY(),
                                        sound.getZ(),
                                        sound.getAttenuationType().get(),
                                        f1
                                    );
                            } else {
                                this.system
                                    .newSource(
                                        false,
                                        s,
                                        getSoundUrl(identifier),
                                        identifier.toString(),
                                        flag,
                                        sound.getX(),
                                        sound.getY(),
                                        sound.getZ(),
                                        sound.getAttenuationType().get(),
                                        f1
                                    );
                            }

                            LOGGER.debug(MARKER, "Playing sound {} for event {} as channel {}", soundx.getLocation(), soundpool.getLocation(), s);
                            this.system.setPitch(s, (float)d0);
                            this.system.setVolume(s, f2);
                            this.system.play(s);
                            this.soundBuffer.put(s, this.ticks + 20);
                            this.eventsByChannel.put(s, sound);
                            this.sources.put(sound, soundx);
                            if (soundcategory != SoundCategory.MASTER) {
                                this.channelsByCategory.put(soundcategory, s);
                            }

                            if (sound instanceof TickableSoundInstance) {
                                this.tickableEvents.add((TickableSoundInstance)sound);
                            }
                        }
                    }
                }
            }
        }
    }

    private float getPitch(SoundInstance instance, Sound sound) {
        return (float)MathHelper.clamp(instance.getPitch() * sound.getVolume(), 0.5, 2.0);
    }

    private float getVolume(SoundInstance instance, Sound sound, SoundCategory category) {
        return (float)MathHelper.clamp(instance.getVolume() * sound.getPitch(), 0.0, 1.0) * this.getVolume(category);
    }

    public void pause() {
        for (String s : this.eventsByChannel.keySet()) {
            LOGGER.debug(MARKER, "Pausing channel {}", s);
            this.system.pause(s);
        }
    }

    public void resume() {
        for (String s : this.eventsByChannel.keySet()) {
            LOGGER.debug(MARKER, "Resuming channel {}", s);
            this.system.play(s);
        }
    }

    public void playLater(SoundInstance sound, int delay) {
        this.delayedEvents.put(sound, this.ticks + delay);
    }

    private static URL getSoundUrl(Identifier location) {
        String s = String.format("%s:%s:%s", "mcsounddomain", location.getNamespace(), location.getPath());
        URLStreamHandler urlstreamhandler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() throws IOException {
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return Minecraft.getInstance().getResourceManager().getResource(location).asStream();
                    }
                };
            }
        };

        try {
            return new URL(null, s, urlstreamhandler);
        } catch (MalformedURLException malformedurlexception) {
            throw new Error("TODO: Sanely handle url exception! :D");
        }
    }

    public void update(PlayerEntity listener, float tickDelta) {
        if (this.started && listener != null) {
            float f = listener.lastPitch + (listener.pitch - listener.lastPitch) * tickDelta;
            float f1 = listener.lastYaw + (listener.yaw - listener.lastYaw) * tickDelta;
            double d0 = listener.lastX + (listener.x - listener.lastX) * tickDelta;
            double d1 = listener.lastY + (listener.y - listener.lastY) * tickDelta + listener.getEyeHeight();
            double d2 = listener.lastZ + (listener.z - listener.lastZ) * tickDelta;
            float f2 = MathHelper.cos((f1 + 90.0F) * (float) (Math.PI / 180.0));
            float f3 = MathHelper.sin((f1 + 90.0F) * (float) (Math.PI / 180.0));
            float f4 = MathHelper.cos(-f * (float) (Math.PI / 180.0));
            float f5 = MathHelper.sin(-f * (float) (Math.PI / 180.0));
            float f6 = MathHelper.cos((-f + 90.0F) * (float) (Math.PI / 180.0));
            float f7 = MathHelper.sin((-f + 90.0F) * (float) (Math.PI / 180.0));
            float f8 = f2 * f4;
            float f9 = f5;
            float f10 = f3 * f4;
            float f11 = f2 * f6;
            float f12 = f7;
            float f13 = f3 * f6;
            this.system.setListenerPosition((float)d0, (float)d1, (float)d2);
            this.system.setListenerOrientation(f8, f9, f10, f11, f12, f13);
        }
    }

    class System extends SoundSystem {
        private System() {
        }

        @Override
        public boolean playing(String string) {
            synchronized (SoundSystemConfig.THREAD_SYNC) {
                if (this.soundLibrary == null) {
                    return false;
                }

                Source source = this.soundLibrary.getSources().get(string);
                return source != null && (source.playing() || source.paused() || source.preLoad);
            }
        }
    }
}
