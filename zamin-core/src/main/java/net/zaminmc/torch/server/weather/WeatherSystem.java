package net.zaminmc.torch.server.weather;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The 1.8.8 weather cycle ({@code World.tickWeather} lines 1793-1849 +
 * {@code WorldData}'s own five fields): the clear-weather lock, the
 * thunder/rain countdowns with their reroll bands, and the two gradients
 * walked 0.01 per tick toward the live state. The publishes follow
 * {@code ServerWorld.tickWeather} lines 856-879 exactly: the gradient
 * pushes (game state 7 rain / 8 thunder) on every change, and the
 * raining-flip pair (game state 1 end / 2 begin + the gradient re-push).
 *
 * <p>The nether never ticks weather ({@code dimension.hasNoSky()} — the
 * reference's own gate); the engine only wires the overworld's system.</p>
 */
public final class WeatherSystem {

    /**
     * The wire observers ({@code ServerWorld.tickWeather}'s own publishes}):
     * the gradient changes and the raining flip, each carrying the value the
     * Change Game State packet needs.
     */
    public interface Listener {

        /** The rain gradient moved ({@code GameEventS2CPacket(7, rain)}). */
        default void onRainGradientChanged(float gradient) {
        }

        /** The thunder gradient moved ({@code GameEventS2CPacket(8, thunder)}). */
        default void onThunderGradientChanged(float gradient) {
        }

        /**
         * The raining flag flipped ({@code GameEventS2CPacket(1/2)} + the
         * gradient pair re-push, the reference's own closing fan).
         */
        default void onRainingFlipped(boolean raining) {
        }
    }

    // WorldData's own five fields (lines 26-30).
    private int clearWeatherTime;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    // World's own gradient pair (the "rain"/"prevRain" fields).
    private float rainGradient;
    private float prevRainGradient;
    private float thunderGradient;
    private float prevThunderGradient;

    private final Random random;
    private final List<Listener> listeners = new ArrayList<>();

    public WeatherSystem(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** @return whether the rain flag is set (WorldData.isRaining). */
    public boolean raining() {
        return raining;
    }

    /** @return whether the thunder flag is set (WorldData.isThundering). */
    public boolean thundering() {
        return thundering;
    }

    /** @return the live rain gradient ({@code World.getRain(1.0F)}). */
    public float rainGradient() {
        return rainGradient;
    }

    /** @return the live thunder gradient ({@code World.getThunder(1.0F)}). */
    public float thunderGradient() {
        return thunderGradient;
    }

    /** @return the clear-weather countdown (diagnostics + tests). */
    public int clearWeatherTime() {
        return clearWeatherTime;
    }

    /** @return the rain countdown (diagnostics + tests). */
    public int rainTime() {
        return rainTime;
    }

    /** @return the thunder countdown (diagnostics + tests). */
    public int thunderTime() {
        return thunderTime;
    }

    /**
     * The weather walk ({@code World.tickWeather} lines 1793-1849, server
     * side): the clear-time lock first (both countdowns pinned at 1/2), then
     * the thunder clock — the expired flip or the reroll band, the gradient
     * walked 0.01 toward the flag — then the rain clock's own pair. The
     * publishes ride {@code ServerWorld.tickWeather}'s override: the flip is
     * detected over the whole walk. Tick-thread context.
     */
    public void tick() {
        boolean wasRaining = raining; // ServerWorld line 856: the pre-walk flag

        int i = clearWeatherTime;
        if (i > 0) {
            clearWeatherTime = --i;
            // The lock (lines 1798-1801): both clocks pinned just above the
            // flip so the post-clear countdown never carries stale time.
            thunderTime = thundering ? 1 : 2;
            rainTime = raining ? 1 : 2;
        }

        int j = thunderTime;
        if (j <= 0) {
            // The reroll band (lines 1805-1809): on-thunder 3-9 minutes,
            // off-thunder 10-15 minutes.
            thunderTime = thundering
                    ? random.nextInt(12000) + 3600
                    : random.nextInt(168000) + 12000;
        } else {
            thunderTime = --j;
            if (j <= 0) {
                thundering = !thundering;
            }
        }

        prevThunderGradient = thunderGradient;
        if (thundering) {
            thunderGradient += 0.01f;
        } else {
            thunderGradient -= 0.01f;
        }
        thunderGradient = Math.min(1.0f, Math.max(0.0f, thunderGradient));

        int k = rainTime;
        if (k <= 0) {
            // The reroll band (lines 1827-1831): on-rain 10-20 minutes,
            // off-rain 10-15 minutes.
            rainTime = raining
                    ? random.nextInt(12000) + 12000
                    : random.nextInt(168000) + 12000;
        } else {
            rainTime = --k;
            if (k <= 0) {
                raining = !raining;
            }
        }

        prevRainGradient = rainGradient;
        if (raining) {
            rainGradient += 0.01f;
        } else {
            rainGradient -= 0.01f;
        }
        rainGradient = Math.min(1.0f, Math.max(0.0f, rainGradient));

        // The publishes (ServerWorld lines 858-879): the gradient pushes on
        // every change, the flip pair after the walk.
        if (prevRainGradient != rainGradient) {
            for (Listener listener : listeners) {
                listener.onRainGradientChanged(rainGradient);
            }
        }
        if (prevThunderGradient != thunderGradient) {
            for (Listener listener : listeners) {
                listener.onThunderGradientChanged(thunderGradient);
            }
        }
        if (wasRaining != raining) {
            for (Listener listener : listeners) {
                listener.onRainingFlipped(raining);
            }
        }
    }

    /**
     * The {@code /weather} commit ({@code WeatherCommand.run} lines 28-52):
     * the five WorldData writes per arm. The duration is already in ticks.
     */
    public void setWeather(boolean rain, boolean thunder, int durationTicks) {
        if (!rain && !thunder) {
            // "clear": the hold-clock takes over, both flags drop.
            clearWeatherTime = durationTicks;
            rainTime = 0;
            thunderTime = 0;
            raining = false;
            thundering = false;
        } else {
            // "rain"/"thunder": the clear-hold drops, both clocks take the
            // duration, the rain flag rises (thunder only on the third arm).
            clearWeatherTime = 0;
            rainTime = durationTicks;
            thunderTime = durationTicks;
            raining = true;
            thundering = thunder;
        }
    }

    /**
     * The sleep reset ({@code ServerWorld.clearWeather} lines 248-256): both
     * flags drop with zeroed clocks — the auto-cycle rerolls fresh (no
     * clear-weather hold, unlike the command's clear arm).
     */
    public void clearWeather() {
        rainTime = 0;
        raining = false;
        thunderTime = 0;
        thundering = false;
    }

    /**
     * The {@code /weather} default duration ({@code WeatherCommand} line 25:
     * {@code (300 + new Random().nextInt(600)) * 20} — the reference's own
     * fresh Random, 5-15 minutes).
     */
    public static int defaultDurationTicks() {
        return (300 + new Random().nextInt(600)) * 20;
    }
}
