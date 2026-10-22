package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.spawn.NaturalSpawnSystem;
import net.zaminmc.torch.server.weather.WeatherSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The weather family's own law (Slice 20b — the reference's
 * World.tickWeather + WorldData + WeatherCommand + the ambient-darkness
 * shaves): the countdowns with their reroll bands, the gradient walks,
 * the publishes, the command's three arms, and the storm-darkened day.
 */
class WeatherSystemTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    /** A recording listener (ServerWorld.tickWeather's own publishes). */
    private static final class Recording implements WeatherSystem.Listener {
        final List<Float> rainPushes = new ArrayList<>();
        final List<Float> thunderPushes = new ArrayList<>();
        final List<Boolean> flips = new ArrayList<>();

        @Override
        public void onRainGradientChanged(float gradient) {
            rainPushes.add(gradient);
        }

        @Override
        public void onThunderGradientChanged(float gradient) {
            thunderPushes.add(gradient);
        }

        @Override
        public void onRainingFlipped(boolean raining) {
            flips.add(raining);
        }
    }

    @Test
    void theRainArmClimbsTheGradientAndPublishesEveryStep() {
        WeatherSystem weather = new WeatherSystem(new Random(1L));
        Recording wire = new Recording();
        weather.addListener(wire);
        // WeatherCommand's rain arm: both clocks take the duration, the
        // rain flag rises, the thunder flag stays down.
        weather.setWeather(true, false, 200);
        assertTrue(weather.raining());
        assertFalse(weather.thundering());
        assertEquals(200, weather.rainTime());
        assertEquals(200, weather.thunderTime());
        assertEquals(0, weather.clearWeatherTime());

        // The gradient walks 0.01 per tick toward the flag (World lines
        // 1839-1847): one hundred ticks to the full rain.
        for (int i = 0; i < 100; i++) {
            weather.tick();
        }
        assertEquals(1.0f, weather.rainGradient(), 1.0E-6, "the full climb");
        assertEquals(100, wire.rainPushes.size(), "a push per change (game state 7)");
        assertEquals(0.01f, wire.rainPushes.get(0), 1.0E-6, "the first step");
        assertEquals(1.0f, wire.rainPushes.get(99), 1.0E-6, "the last step");
        assertEquals(0.0f, weather.thunderGradient(), 1.0E-6, "no thunder arm");
        assertTrue(wire.thunderPushes.isEmpty(), "no thunder pushes while the flag is down");
    }

    @Test
    void theExpiryFlipsTheRainAndTheRerollBandsFollow() {
        WeatherSystem weather = new WeatherSystem(new Random(7L));
        Recording wire = new Recording();
        weather.addListener(wire);
        weather.setWeather(true, false, 3);
        // The countdown's own shape (lines 1833-1836): the tick that
        // decrements the clock to zero is the tick that flips the flag —
        // the third tick, not the fourth.
        weather.tick();
        weather.tick();
        assertTrue(weather.raining(), "still raining through the second tick");
        weather.tick();
        assertFalse(weather.raining(), "the third tick decremented to zero and flipped");
        assertEquals(1, wire.flips.size(), "exactly one flip publish (game state 1)");
        assertEquals(Boolean.FALSE, wire.flips.get(0), "the flip carries the end-rain state");
        // The next tick's reroll: raining == false -> the off-band
        // nextInt(168000) + 12000 (lines 1827-1831).
        weather.tick();
        assertTrue(weather.rainTime() >= 12000 && weather.rainTime() < 180000,
                "the off-rain reroll band 10-15 minutes: " + weather.rainTime());
    }

    @Test
    void theThunderArmRaisesBothFlagsAndBothGradients() {
        WeatherSystem weather = new WeatherSystem(new Random(3L));
        Recording wire = new Recording();
        weather.addListener(wire);
        weather.setWeather(true, true, 500);
        assertTrue(weather.thundering());
        for (int i = 0; i < 120; i++) {
            weather.tick();
        }
        assertEquals(1.0f, weather.rainGradient(), 1.0E-6);
        assertEquals(1.0f, weather.thunderGradient(), 1.0E-6);
        // The push count rides the float path (the 0.01 walk saturates at
        // the 100th or 101st step depending on the accumulation's last
        // bit) — the law is: a push per CHANGE, none once saturated.
        int pushesAtSaturation = wire.thunderPushes.size();
        assertTrue(pushesAtSaturation == 100 || pushesAtSaturation == 101,
                "the saturation point: " + pushesAtSaturation);
        assertEquals(1.0f, wire.thunderPushes.get(pushesAtSaturation - 1), 1.0E-6,
                "the last push carries the ceiling");
        for (int i = 0; i < 20; i++) {
            weather.tick();
        }
        assertEquals(pushesAtSaturation, wire.thunderPushes.size(),
                "a saturated gradient pushes nothing");
    }

    @Test
    void theClearLockPinsBothClocksThroughTheHold() {
        WeatherSystem weather = new WeatherSystem(new Random(11L));
        weather.setWeather(true, true, 100);
        for (int i = 0; i < 100; i++) {
            weather.tick();
        }
        // The clear arm (WeatherCommand lines 32-38): the hold-clock takes
        // over, both flags drop, both clocks zero until the hold's own
        // countdown drains.
        weather.setWeather(false, false, 5);
        assertFalse(weather.raining());
        assertFalse(weather.thundering());
        assertEquals(5, weather.clearWeatherTime());
        weather.tick();
        // The lock (lines 1798-1801) resets both clocks to 2 at the tick's
        // head, then the same tick's own countdown walks them to 1 — the
        // oscillation never lets a clock reach the flip inside the hold.
        assertEquals(4, weather.clearWeatherTime(), "the hold drains");
        assertEquals(1, weather.rainTime(), "the rain clock re-pinned to 2 then walked to 1");
        assertEquals(1, weather.thunderTime(), "the thunder clock the same");
        for (int i = 0; i < 4; i++) {
            weather.tick();
            assertFalse(weather.raining(), "the hold never flips the flag");
        }
        assertEquals(0, weather.clearWeatherTime(), "the hold drained");
        // The lock's terminal quirk (the reference's own shape): the last
        // pinned clock sits at 1, so the FIRST unlocked tick decrements it
        // to zero and flips the rain back ON — the clear state only ever
        // persists while the hold runs.
        weather.tick();
        assertTrue(weather.raining(), "the post-hold tick flips the rain back on");
        // The next tick rerolls on the on-rain band (nextInt(12000)+12000).
        weather.tick();
        assertTrue(weather.rainTime() >= 12000 && weather.rainTime() < 24000,
                "the post-flip on-rain reroll: " + weather.rainTime());
    }

    @Test
    void theSleepResetDropsBothFlagsWithoutTheHold() {
        WeatherSystem weather = new WeatherSystem(new Random(5L));
        weather.setWeather(true, true, 10000);
        // ServerWorld.clearWeather lines 248-256: both flags drop with
        // zeroed clocks — NOT the command's clear-hold.
        weather.clearWeather();
        assertFalse(weather.raining());
        assertFalse(weather.thundering());
        assertEquals(0, weather.rainTime());
        assertEquals(0, weather.thunderTime());
        assertEquals(0, weather.clearWeatherTime(), "no hold: the cycle rerolls immediately");
        weather.tick();
        assertTrue(weather.rainTime() >= 12000 && weather.rainTime() < 180000);
    }

    @Test
    void theStormDarkensTheDaysAmbientLight() {
        // World.calculateAmbientDarkness lines 1004-1016: the noon plateau
        // is 0 clear; the full storm (both gradients 1.0) lifts it to 5 —
        // the (1 - (11/16)^2) * 11 shape.
        assertEquals(0, NaturalSpawnSystem.ambientDarkness(6000L, 0.0f, 0.0f),
                "the clear noon");
        assertEquals(5, NaturalSpawnSystem.ambientDarkness(6000L, 1.0f, 1.0f),
                "the fully-stormed noon");
        assertEquals(3, NaturalSpawnSystem.ambientDarkness(6000L, 1.0f, 0.0f),
                "the rained noon (the single shave)");
        // The midnight ceiling never moves (the clamped band).
        assertEquals(11, NaturalSpawnSystem.ambientDarkness(18000L, 0.0f, 0.0f));
        assertEquals(11, NaturalSpawnSystem.ambientDarkness(18000L, 1.0f, 1.0f));
        // The clear-sky overload delegates with the same shape.
        assertEquals(NaturalSpawnSystem.ambientDarkness(6000L),
                NaturalSpawnSystem.ambientDarkness(6000L, 0.0f, 0.0f));
    }

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theLiveCommandWalksTheEngineCycle() throws Exception {
        EngineServer server = boot();
        try {
            ClientLink link = new ClientLink() {
                @Override public boolean isActive() { return true; }
                @Override public void kick(String reason) { }
            };
            var result = server.joinRequest(link, "Stormwatch",
                    UUID.nameUUIDFromBytes("Stormwatch".getBytes()));
            PlayerSession player = ((EngineBridge.Accepted) result).session();
            var console = new net.zaminmc.torch.server.chat.CommandSender() {
                @Override public String name() { return "Console"; }
                @Override public void sendMessage(String message) { }
                @Override public int opLevel() { return 4; }
                @Override public PlayerSession player() { return null; }
            };

            // /weather thunder 30 (the console's own level-2 gate): both
            // flags rise, both gradients walk.
            server.chatService().dispatchCommand(console, "/weather thunder 30");
            await(() -> server.raining(), "the rain flag rose");
            await(() -> server.weather().thunderGradient() > 0.05f,
                    "the thunder gradient walks");
            assertTrue(server.weather().rainGradient() > 0.05f,
                    "the rain gradient walks beside it");

            // The expiry: thirty seconds later the flags flip and the
            // gradients walk back down (the auto-cycle's own reroll).
            await(() -> !server.raining(), "the expiry flipped the rain flag", 40_000);
            await(() -> server.weather().rainGradient() < 0.9f,
                    "the gradient walks back down");

            // The sleep reset shape: rain again then the clear arm.
            server.chatService().dispatchCommand(console, "/weather rain 60");
            await(() -> server.raining(), "the rain returned");
            server.chatService().dispatchCommand(console, "/weather clear 60");
            assertFalse(server.raining(), "the clear arm dropped the flag at once");
            assertEquals(60 * 20, server.weather().clearWeatherTime());
        } finally {
            server.shutdown(null);
        }
    }

    /**
     * Slice 21b — the weather persistence ({@code WorldData}'s own save
     * fields): the five flags/clocks survive the restart (the gradients
     * never persist — the fresh fields walk from 0 like the reference's),
     * and a corrupt store boots the fresh defaults with the quarantine
     * copy beside it.
     */
    @Test
    void theWeatherStateSurvivesTheRestart() throws Exception {
        var console = new net.zaminmc.torch.server.chat.CommandSender() {
            @Override public String name() { return "Console"; }
            @Override public void sendMessage(String message) { }
            @Override public int opLevel() { return 4; }
            @Override public PlayerSession player() { return null; }
        };

        // Life one: the thunder arm's flags + clocks land, then the save.
        EngineServer first = boot();
        try {
            first.chatService().dispatchCommand(console, "/weather thunder 120");
            await(() -> first.raining() && first.weather().thundering(),
                    "the thunder arm raised both flags");
            first.saveAllNow();
        } finally {
            first.shutdown(null);
        }

        // Life two: the same dataDir — the flags and the clocks restore
        // (WorldData lines 94-98: the five fields come back as saved).
        EngineServer second = boot();
        try {
            assertTrue(second.raining(), "the rain flag survived the restart");
            assertTrue(second.weather().thundering(), "the thunder flag survived");
            int rainClock = second.weather().rainTime();
            int clearClock = second.weather().clearWeatherTime();
            assertTrue(rainClock > 2000 && rainClock <= 120 * 20,
                    "the rain clock restored inside the command window: " + rainClock);
            assertEquals(0, clearClock, "the clear-weather hold stayed empty");
            // The gradients never persist: the fresh fields walk from 0
            // (a full storm would carry 1.0 — the restart carries the
            // first few ticks of the walk).
            assertTrue(second.weather().rainGradient() < 0.5f,
                    "the gradient restarted its walk from 0");
        } finally {
            second.shutdown(null);
        }

        // The corrupt-store arm: garbage in weather.bin boots the fresh
        // defaults (clear — the first tick's reroll band draws the clocks)
        // with the quarantine copy beside the file. The store lives in the
        // world's own data folder (worldDataDir = <dataDir>/itest/data —
        // the boot helper's own world name).
        java.nio.file.Path store = dataDir.resolve("itest").resolve("data")
                .resolve("weather.bin");
        java.nio.file.Files.write(store, new byte[]{9, 9, 9, 9, 1, 2, 3, 4, 5, 6, 7, 8});
        EngineServer third = boot();
        try {
            EngineAwaits.awaitTicks(third.world(), 6, "a few ticks run");
            assertFalse(third.raining(),
                    "the corrupt store booted the fresh clear defaults");
            assertTrue(java.nio.file.Files.exists(
                            dataDir.resolve("itest").resolve("data").resolve("weather.bin.corrupt")),
                    "the corrupt copy is preserved beside the store");
        } finally {
            third.shutdown(null);
        }
    }

    private void await(BooleanSupplier condition, String description, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
