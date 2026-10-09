package net.minecraft.client.sound;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.resource.Identifier;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.MathHelper;

public class MusicManager implements Tickable {
    private final Random random = new Random();
    private final Minecraft minecraft;
    private SoundInstance currentMusic;
    private int timeUntilNextSong = 100;

    public MusicManager(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void tick() {
        MusicManager.Music musicmanager$music = this.minecraft.getMusicEnvironment();
        if (this.currentMusic != null) {
            if (!musicmanager$music.getLocation().equals(this.currentMusic.getLocation())) {
                this.minecraft.getSoundManager().stop(this.currentMusic);
                this.timeUntilNextSong = MathHelper.nextInt(this.random, 0, musicmanager$music.getMinWaitTime() / 2);
            }

            if (!this.minecraft.getSoundManager().isPlaying(this.currentMusic)) {
                this.currentMusic = null;
                this.timeUntilNextSong = Math.min(
                    MathHelper.nextInt(this.random, musicmanager$music.getMinWaitTime(), musicmanager$music.getMaxWaitTime()), this.timeUntilNextSong
                );
            }
        }

        if (this.currentMusic == null && this.timeUntilNextSong-- <= 0) {
            this.startPlaying(musicmanager$music);
        }
    }

    public void startPlaying(MusicManager.Music music) {
        this.currentMusic = SimpleSoundInstance.of(music.getLocation());
        this.minecraft.getSoundManager().play(this.currentMusic);
        this.timeUntilNextSong = Integer.MAX_VALUE;
    }

    public void stopPlaying() {
        if (this.currentMusic != null) {
            this.minecraft.getSoundManager().stop(this.currentMusic);
            this.currentMusic = null;
            this.timeUntilNextSong = 0;
        }
    }

    public enum Music {
        MENU(new Identifier("minecraft:music.menu"), 20, 600),
        GAME(new Identifier("minecraft:music.game"), 12000, 24000),
        CREATIVE(new Identifier("minecraft:music.game.creative"), 1200, 3600),
        CREDITS(new Identifier("minecraft:music.game.end.credits"), Integer.MAX_VALUE, Integer.MAX_VALUE),
        NETHER(new Identifier("minecraft:music.game.nether"), 1200, 3600),
        END_BOSS(new Identifier("minecraft:music.game.end.dragon"), 0, 0),
        END(new Identifier("minecraft:music.game.end"), 6000, 24000);

        private final Identifier location;
        private final int minWaitTime;
        private final int maxWaitTime;

        Music(Identifier location, int minWaitTime, int maxWaitTime) {
            this.location = location;
            this.minWaitTime = minWaitTime;
            this.maxWaitTime = maxWaitTime;
        }

        public Identifier getLocation() {
            return this.location;
        }

        public int getMinWaitTime() {
            return this.minWaitTime;
        }

        public int getMaxWaitTime() {
            return this.maxWaitTime;
        }
    }
}
