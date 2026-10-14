package net.zaminmc.torch.server.daylight;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daylight detector family on the live engine (Slice 27c) — the
 * reference port of DaylightDetectorBlock + DaylightDetectorBlockEntity:
 * the math chain (the time fraction, the ambient darkness, the sun-angle
 * cosine, the invert), the 20-tick BE gate over the world clock, the
 * variant flips (the flattened power states), the use arm's invert toggle
 * keeping the level, and the weak emission the redstone system consults.
 */
class DaylightDetectorSystemTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The math chain (the reference's own walks)
    // ------------------------------------------------------------------

    @Test
    void theMathChainMatchesTheReferenceAnchors() {
        // Noon (6000): the fraction ~0, the sun overhead — the full band.
        assertEquals(0.0F, DaylightDetectorSystem.timeFraction(6000), 0.001F,
                "the noon fraction sits at the day's start");
        assertEquals(0.0F, DaylightDetectorSystem.sunAngle(6000), 0.01F,
                "the noon sun angle is zero (the cosine peaks)");
        // Midnight (18000): the fraction ~0.5, the sun underfoot.
        assertEquals(0.5F, DaylightDetectorSystem.timeFraction(18000), 0.001F);
        // The ambient darkness: 0 at noon, 11 at midnight under a clear sky
        // (calculateAmbientDarkness's * 11 ceiling).
        assertEquals(0, DaylightDetectorSystem.ambientDarkness(6000, 0.0F, 0.0F),
                "a clear noon carries no darkness");
        assertEquals(11, DaylightDetectorSystem.ambientDarkness(18000, 0.0F, 0.0F),
                "a clear midnight darkens the full 11");
        // Rain dims: the gradient deepens the darkness in the partial band
        // (a dawn sky — the saturated midnight band never moves).
        assertTrue(DaylightDetectorSystem.ambientDarkness(0, 1.0F, 1.0F)
                        > DaylightDetectorSystem.ambientDarkness(0, 0.0F, 0.0F),
                "the storm gradients deepen the dawn darkness");
        assertEquals(DaylightDetectorSystem.ambientDarkness(18000, 0.0F, 0.0F),
                DaylightDetectorSystem.ambientDarkness(18000, 1.0F, 1.0F),
                "the saturated midnight band never moves (the reference's own multiplication order)");

        // The power walk (updateOutputState lines 49-58): noon under open
        // sky = 15; midnight = 0 (the negative cosine clamps); the inverted
        // form mirrors.
        assertEquals(15, DaylightDetectorSystem.computePower(15, 6000, 0.0F, 0.0F, false),
                "a clear noon under open sky answers the full 15");
        assertEquals(0, DaylightDetectorSystem.computePower(15, 18000, 0.0F, 0.0F, false),
                "the midnight cosine clamps to 0");
        assertEquals(0, DaylightDetectorSystem.computePower(15, 6000, 0.0F, 0.0F, true),
                "the inverted noon answers 0");
        assertEquals(15, DaylightDetectorSystem.computePower(15, 18000, 0.0F, 0.0F, true),
                "the inverted midnight answers the full 15");
        // The dawn band (time 0 — the walk's -0.25 shift lands the morning
        // at the fraction ~0.78, the sun low but climbing).
        float dawn = DaylightDetectorSystem.timeFraction(0);
        assertTrue(dawn > 0.75F && dawn < 0.82F, "the time-zero fraction sits in the morning band: " + dawn);
        // The dawn power: the low cosine band (the wrap pulls it toward
        // the horizon) — a partial dial, not the extremes.
        int dawnPower = DaylightDetectorSystem.computePower(15, 0, 0.0F, 0.0F, false);
        assertTrue(dawnPower > 3 && dawnPower < 12, "the dawn dial sits mid-band: " + dawnPower);
    }

    // ------------------------------------------------------------------
    // The live 20-tick walk + the variant flips
    // ------------------------------------------------------------------

    @Test
    void theTwentyTickWalkFollowsTheSky() throws Exception {
        EngineServer server = boot();
        try {
            // A detector high above the flat ground: open sky, skylight 15.
            BlockPosition at = new BlockPosition(8, 40, 8);
            server.ticker().submit(() -> {
                server.world().setBlock(at, DaylightDetectorBlocks.ofPower(0));
            });
            await(server, () -> server.world().getBlock(at)
                    .identifier().value().equals("daylight_detector_p0"), "the detector placed");

            // Noon: the walk lifts the dial to the full band on the next
            // 20-tick boundary.
            server.ticker().submit(() -> server.world().setTimeOfDay(6000));
            await(server, () -> server.world().getBlock(at)
                    .identifier().value().equals("daylight_detector_p15"),
                    "the noon walk lifted the dial to 15");

            // The emission consult: the weak power IS the dial, every
            // direction (getSignal's own no-dir-gate — dir 1 = UP here,
            // the same convention the consult's own callers use).
            AtomicReference<Integer> signal = new AtomicReference<>();
            server.ticker().submit(() -> signal.set(server.redstone().signal(at, 1)));
            await(server, () -> signal.get() != null, "the consult answered");
            assertEquals(15, signal.get(), "the detector emits its dial weakly");

            // Midnight: the walk drops it to 0.
            server.ticker().submit(() -> server.world().setTimeOfDay(18000));
            await(server, () -> server.world().getBlock(at)
                    .identifier().value().equals("daylight_detector_p0"),
                    "the midnight walk dropped the dial to 0");

            // The use arm toggles the form keeping the level: midnight's 0
            // becomes the INVERTED 15 (use lines 73-79: the swap keeps the
            // POWER, the re-update applies the other form's math).
            AtomicReference<Boolean> toggled = new AtomicReference<>();
            server.ticker().submit(() -> toggled.set(
                    server.daylightDetectors().useFromTick(at)));
            await(server, () -> Boolean.TRUE.equals(toggled.get()), "the toggle consumed the use");
            await(server, () -> server.world().getBlock(at)
                    .identifier().value().equals("daylight_detector_inverted_p15"),
                    "the inverted midnight answers the full 15");

            // The break hook cleans the set (a re-placed detector starts
            // fresh at the placed state).
            server.ticker().submit(() -> {
                server.world().setBlock(at, server.world().airType());
                server.daylightDetectors().onBlockBroken(at);
            });
            await(server, () -> server.world().getBlock(at)
                    .identifier().value().equals("air"), "the detector left");
            server.ticker().submit(() -> server.world().setBlock(
                    at, DaylightDetectorBlocks.ofPower(0)));
            await(server, () -> server.world().getBlock(at)
                    .identifier().value().equals("daylight_detector_p0"), "the re-place landed");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The family predicates
    // ------------------------------------------------------------------

    @Test
    void theFamilyPredicatesCarryTheForms() {
        assertTrue(DaylightDetectorBlocks.isFamily(DaylightDetectorBlocks.ofPower(7)));
        assertTrue(DaylightDetectorBlocks.isFamily(DaylightDetectorBlocks.invertedOfPower(7)));
        assertFalse(DaylightDetectorBlocks.isFamily(BuiltinBlocks.JUKEBOX));
        assertTrue(DaylightDetectorBlocks.isInverted(DaylightDetectorBlocks.invertedOfPower(3)));
        assertFalse(DaylightDetectorBlocks.isInverted(DaylightDetectorBlocks.ofPower(3)));
        assertEquals(3, DaylightDetectorBlocks.powerOf(DaylightDetectorBlocks.ofPower(3)));
        assertEquals(15, DaylightDetectorBlocks.powerOf(DaylightDetectorBlocks.invertedOfPower(15)));
        // The wrap: the family band modulo.
        assertEquals(DaylightDetectorBlocks.ofPower(0),
                DaylightDetectorBlocks.ofPower(16));
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private static void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dldt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
