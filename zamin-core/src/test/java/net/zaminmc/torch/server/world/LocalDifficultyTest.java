package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The local-difficulty completion's own evidence (VIII14 — the reference's
 * LocalDifficulty 1:1, the raw day clock, the inhabited-time model):
 *
 * <ul>
 *   <li>THE FORMULA — the class against the reference's own
 *   {@code calculate} replicated verbatim as the shadow, over the full
 *   sweep of the four bases x the raw-clock band x the inhabited band x
 *   the moon sizes, every cell equal; plus the law pins (the peaceful
 *   zero, the moon cap, the easy shave, the hard inhabited weight, the
 *   72000 time offset, the multiplier's 2..4 band).</li>
 *   <li>THE MOON — the phase walk over the RAW day clock (the +8
 *   negative guard included), the size table, the day-fold laws of the
 *   two clocks (the add arm accumulates the raw clock past the day
 *   boundary, the set arm resets it verbatim — the reference's own
 *   behaviors).</li>
 *   <li>THE INHABITED MODEL — the per-tick credit walk (watched chunks
 *   +1, unwatched 0, the union dedup), the consult's loaded-chunk gate
 *   (an unloaded position reads 0 inhabited + 0 moon, the raw clock
 *   still carrying).</li>
 *   <li>THE PERSISTENCE — the ZWD v2 round trip (the raw clock + the
 *   inhabited tail survive the save/load, the never-loaded pending row
 *   survives a second cycle) and the v1 migration (a v1 file reads as
 *   the historical fresh-moon shape: raw = wrapped, no tail).</li>
 * </ul>
 */
class LocalDifficultyTest {

    @TempDir
    Path dir;

    private static net.zaminmc.torch.block.BlockRegistry registry;
    private static EngineWorld world;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    /** A fresh owner-thread world per test (the clock starts at 0/0). */
    private static EngineWorld freshWorld() {
        return new EngineWorld("local-difficulty", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
    }

    // ------------------------------------------------------- the formula

    /**
     * The reference's own calculate (net.minecraft.world.LocalDifficulty),
     * replicated verbatim as the shadow — the sweep's every cell must
     * equal it.
     */
    private static float referenceCalculate(int base, long timeOfDay,
                                            long inhabitedTime, float moonSize) {
        if (base == 0) { // Difficulty.PEACEFUL
            return 0.0F;
        }
        boolean flag = base == 3; // Difficulty.HARD
        float f = 0.75F;
        float f1 = clamp(((float) timeOfDay + -72000.0F) / 1440000.0F, 0.0F, 1.0F) * 0.25F;
        f += f1;
        float f2 = 0.0F;
        f2 += clamp((float) inhabitedTime / 3600000.0F, 0.0F, 1.0F) * (flag ? 1.0F : 0.75F);
        f2 += clamp(moonSize * 0.25F, 0.0F, f1);
        if (base == 1) { // Difficulty.EASY
            f2 *= 0.5F;
        }
        f += f2;
        return base * f;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** The reference's getMultiplier band, replicated for the band pins. */
    private static float referenceMultiplier(float regional) {
        if (regional < 2.0F) {
            return 0.0F;
        }
        return regional > 4.0F ? 1.0F : (regional - 2.0F) / 2.0F;
    }

    @Test
    void theFormulaMatchesTheReferenceShadowOverTheWholeSweep() {
        long[] rawClocks = {0L, 71_999L, 72_000L, 792_000L, 1_512_000L, 2_400_000L, 4_800_000L};
        long[] inhabited = {0L, 1L, 1_800_000L, 3_599_999L, 3_600_000L, 7_200_000L};
        float[] moons = {0.0F, 0.25F, 0.5F, 0.75F, 1.0F};
        int cells = 0;
        for (int base = 0; base <= 3; base++) {
            for (long raw : rawClocks) {
                for (long inhabitedTime : inhabited) {
                    for (float moon : moons) {
                        LocalDifficulty at = new LocalDifficulty(base, raw, inhabitedTime, moon);
                        assertEquals(referenceCalculate(base, raw, inhabitedTime, moon),
                                at.get(), 0.0F,
                                "base=" + base + " raw=" + raw + " inhabited=" + inhabitedTime
                                        + " moon=" + moon);
                        assertEquals(referenceMultiplier(at.get()), at.getMultiplier(), 0.0F,
                                "the multiplier band at regional=" + at.get());
                        cells++;
                    }
                }
            }
        }
        assertTrue(cells > 400, "the sweep covered the space (" + cells + " cells)");
    }

    @Test
    void theLawPins() {
        // The peaceful base: zero whatever the world carries.
        assertEquals(0.0F, new LocalDifficulty(0, 4_800_000L, 7_200_000L, 1.0F).get(),
                "peaceful reads zero outright");
        // The moon cap: on a fresh world (raw < 72000 — the time term 0),
        // a full moon adds NOTHING (the moon term clamps to the time term).
        assertEquals(new LocalDifficulty(3, 0L, 0L, 0.0F).get(),
                new LocalDifficulty(3, 0L, 0L, 1.0F).get(),
                "a full moon doubles nothing on a fresh world");
        // The cap lifts as the time term grows: raw 792,000 carries the
        // time term 0.125 — a full moon's 0.25 clamps to 0.125.
        float noMoon = new LocalDifficulty(3, 792_000L, 0L, 0.0F).get();
        float fullMoon = new LocalDifficulty(3, 792_000L, 0L, 1.0F).get();
        assertEquals(noMoon + 3L * 0.125F, fullMoon, 1.0e-6F,
                "the moon term rides the time term's cap");
        // The easy shave: the inhabited term halves on easy (the 0.75
        // non-hard weight, the 0.5 easy shave, the base-1 multiplier).
        float easyInhabited = new LocalDifficulty(1, 0L, 3_600_000L, 0.0F).get()
                - new LocalDifficulty(1, 0L, 0L, 0.0F).get();
        assertEquals(0.75F * 0.5F, easyInhabited, 1.0e-6F,
                "easy halves the (0.75-weighted, saturated) inhabited band");
        // The hard inhabited weight: the full 1.0 against the others' 0.75.
        float hardInhabited = new LocalDifficulty(3, 0L, 3_600_000L, 0.0F).get()
                - new LocalDifficulty(3, 0L, 0L, 0.0F).get();
        assertEquals(3L * 1.0F, hardInhabited, 1.0e-6F,
                "hard carries the full saturated inhabited weight");
        // The 72000 offset: one tick below reads zero, the boundary itself
        // still zero (the reference's -72000 shifts the ramp's start).
        assertEquals(0.0F, new LocalDifficulty(2, 71_999L, 0L, 0.0F).get() - 2L * 0.75F, 1.0e-6F);
        assertEquals(0.0F, new LocalDifficulty(2, 72_000L, 0L, 0.0F).get() - 2L * 0.75F, 1.0e-6F);
    }

    @Test
    void theMultiplierBands() {
        // The fresh world: normal 1.5 -> 0, hard 2.25 -> 0.125.
        assertEquals(0.0F, new LocalDifficulty(2, 0L, 0L, 0.0F).getMultiplier(),
                "fresh normal stays under the 2 floor");
        assertEquals(0.125F, new LocalDifficulty(3, 0L, 0L, 0.0F).getMultiplier(), 1.0e-6F,
                "fresh hard rides the band's first step");
        // The saturated hard world: 3 * (0.75 + 0.25 + 1.0 + 0.25) = 6.75 -> 1.
        assertEquals(6.75F, new LocalDifficulty(3, 1_512_000L, 3_600_000L, 1.0F).get(), 1.0e-6F);
        assertEquals(1.0F, new LocalDifficulty(3, 1_512_000L, 3_600_000L, 1.0F).getMultiplier(),
                "the deep-geared late-world read caps at 1");
    }

    // ------------------------------------------------------- the moon + the clocks

    @Test
    void theMoonPhaseWalkAndSizeTable() {
        EngineWorld at = freshWorld();
        // Phase 0 the full moon at raw 0; each day advances the phase.
        at.setTimeOfDay(0L);
        assertEquals(0, at.moonPhase(), "day 0 is the full moon");
        assertEquals(1.0F, at.moonSize(), "the full moon's size");
        at.setTimeOfDay(24_000L);
        assertEquals(1, at.moonPhase());
        assertEquals(0.75F, at.moonSize());
        at.setTimeOfDay(96_000L);
        assertEquals(4, at.moonPhase(), "day 4 is the new moon");
        assertEquals(0.0F, at.moonSize());
        at.setTimeOfDay(192_000L);
        assertEquals(0, at.moonPhase(), "day 8 wraps to the full moon again");
        // The negative-clock guard: (t/24000 % 8 + 8) % 8.
        at.setTimeOfDay(-24_000L);
        assertEquals(7, at.moonPhase(), "the +8 band guards the negative clock");
    }

    @Test
    void theTwoClocksWalkAndTheTimeArmsCarryTheRawOne() {
        EngineWorld at = freshWorld();
        // The tick walk: both clocks advance together under the gate.
        at.tickTime(true);
        at.tickTime(true);
        assertEquals(2L, at.timeOfDay());
        assertEquals(2L, at.rawTimeOfDay());
        // The wrapped clock folds at the day length; the raw one never.
        for (int i = 0; i < 24_000; i++) {
            at.tickTime(true);
        }
        assertEquals(2L, at.timeOfDay(), "the wrapped clock folded at 24000");
        assertEquals(24_002L, at.rawTimeOfDay(), "the raw clock carried the day count");
        assertEquals(1, at.moonPhase(), "the moon advanced to the second phase");
        // The add arm: the wrapped derives, the raw accumulates — the old
        // setTimeOfDay(wrapped + add) form folded the raw clock at the day
        // boundary (a /time add 24000 reset the moon); the new arm carries.
        at.addTimeOfDay(24_000L);
        assertEquals(2L, at.timeOfDay());
        assertEquals(48_002L, at.rawTimeOfDay());
        assertEquals(2, at.moonPhase(), "the /time add moved the moon");
        // The set arm: the reference's own reset — the raw clock takes the
        // value verbatim (a /time set resets the day count, the moon with it).
        at.setTimeOfDay(6_000L);
        assertEquals(6_000L, at.timeOfDay());
        assertEquals(6_000L, at.rawTimeOfDay());
        assertEquals(0, at.moonPhase(), "the /time set reset the moon to full");
    }

    // ------------------------------------------------------- the inhabited model

    @Test
    void theWatchedSquaresAccrueExactlyOneTickPerTick() {
        EngineWorld at = freshWorld();
        ChunkPosition watchedA = new ChunkPosition(0, 0);
        ChunkPosition watchedB = new ChunkPosition(1, 0);
        ChunkPosition unwatched = new ChunkPosition(5, 5);
        at.getOrGenerate(watchedA);
        at.getOrGenerate(watchedB);
        at.getOrGenerate(unwatched);
        // The union set: two watchers may share a chunk — the set's own
        // dedup carries the once-per-tick law (the holder's span).
        Set<Long> union = Set.of(watchedA.packed(), watchedB.packed());
        for (int tick = 0; tick < 100; tick++) {
            at.accrueInhabitedTime(union);
        }
        assertEquals(100L, at.peek(watchedA).inhabitedTime(),
                "the watched chunk accrued every tick");
        assertEquals(100L, at.peek(watchedB).inhabitedTime());
        assertEquals(0L, at.peek(unwatched).inhabitedTime(),
                "the unwatched chunk never accrues");
        // A second watched span continues the accrual (the union of spans).
        at.accrueInhabitedTime(Set.of(watchedA.packed()));
        assertEquals(101L, at.peek(watchedA).inhabitedTime());
        assertEquals(100L, at.peek(watchedB).inhabitedTime());
    }

    @Test
    void theConsultGatesOnTheLoadedChunk() {
        EngineWorld at = freshWorld();
        ChunkPosition loaded = new ChunkPosition(0, 0);
        at.getOrGenerate(loaded);
        at.accrueInhabitedTime(Set.of(loaded.packed()));
        at.setTimeOfDay(1_512_000L); // the raw clock: the saturated time term
        // The loaded position: the inhabited row + the moon carry.
        LocalDifficulty atLoaded = at.localDifficulty(
                new net.zaminmc.torch.block.BlockPosition(0, 10, 0), 3);
        assertEquals(new LocalDifficulty(3, 1_512_000L, 1L, at.moonSize()).get(),
                atLoaded.get(), 1.0e-6F,
                "the loaded read carries the chunk's inhabited row + the moon");
        // The unloaded position: the reference's own gate — 0 inhabited,
        // 0 moon — while the raw clock + the base still carry.
        LocalDifficulty atUnloaded = at.localDifficulty(
                new net.zaminmc.torch.block.BlockPosition(8_000, 10, 8_000), 3);
        assertEquals(new LocalDifficulty(3, 1_512_000L, 0L, 0.0F).get(),
                atUnloaded.get(), 1.0e-6F,
                "the unloaded read zeroes the chunk-gated terms alone");
    }

    // ------------------------------------------------------- the persistence

    @Test
    void theZwdV2RoundTripCarriesTheRawClockAndTheInhabitedTail() throws Exception {
        EngineWorld source = freshWorld();
        ChunkPosition lived = new ChunkPosition(0, 0);
        ChunkPosition neverLoaded = new ChunkPosition(9, 9);
        source.getOrGenerate(lived);
        source.setTimeOfDay(300_000L); // the raw clock past the day fold
        source.addTimeOfDay(50_000L);
        for (int tick = 0; tick < 500; tick++) {
            source.accrueInhabitedTime(Set.of(lived.packed()));
        }
        // The never-loaded row rides the pending map (the restore shape).
        WorldDeltaSnapshot withPending = source.snapshotDeltas();
        Map<Long, Long> pendingRow = new HashMap<>(withPending.inhabitedTimes());
        pendingRow.put(neverLoaded.packed(), 7_777L);
        WorldDeltaSnapshot snap = new WorldDeltaSnapshot(withPending.totalTicks(),
                withPending.timeOfDay(), withPending.rawTimeOfDay(), pendingRow,
                withPending.deltas());

        Path file = dir.resolve("v2-roundtrip.bin");
        DeltaWorldStorage storage = new DeltaWorldStorage(file, registry::require);
        storage.save(snap);

        WorldDeltaSnapshot loaded = new DeltaWorldStorage(file, registry::require)
                .load().orElseThrow();
        assertEquals(350_000L, loaded.rawTimeOfDay(), "the raw day clock survived the round trip");
        assertEquals(snap.timeOfDay(), loaded.timeOfDay());
        assertEquals(snap.inhabitedTimes(), loaded.inhabitedTimes(),
                "the inhabited tail survived whole (the live row + the pending row)");

        // The restore: a fresh world picks the rows up — the published
        // chunk at install, the never-loaded row stays pending and
        // survives a SECOND snapshot (the round trip never loses it).
        EngineWorld restored = freshWorld();
        restored.applyDeltas(loaded);
        restored.getOrGenerate(lived);
        assertEquals(500L, restored.peek(lived).inhabitedTime(),
                "the published chunk picked its row up at install");
        WorldDeltaSnapshot second = restored.snapshotDeltas();
        assertEquals(500L, second.inhabitedTimes().get(lived.packed()),
                "the live value is authoritative once installed");
        assertEquals(7_777L, second.inhabitedTimes().get(neverLoaded.packed()),
                "the never-loaded pending row survived the second cycle");
    }

    @Test
    void theZwdV1FileMigratesToTheHistoricalFreshMoonRead() throws Exception {
        // A hand-built v1 file (the historical layout: magic, version 1,
        // the two clocks, an empty palette, no deltas, NO raw row, NO
        // inhabited tail).
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        out.writeByte('Z');
        out.writeByte('W');
        out.writeByte('D');
        out.writeByte(1); // the v1 version
        out.writeLong(1_000L); // totalTicks
        out.writeLong(13_000L); // timeOfDay (the wrapped clock alone)
        out.writeInt(0); // palette
        out.writeInt(0); // chunk count
        out.flush();
        Path file = dir.resolve("v1-legacy.bin");
        Files.write(file, bytes.toByteArray());

        WorldDeltaSnapshot loaded = new DeltaWorldStorage(file, registry::require)
                .load().orElseThrow();
        assertEquals(1_000L, loaded.totalTicks());
        assertEquals(13_000L, loaded.timeOfDay());
        assertEquals(13_000L, loaded.rawTimeOfDay(),
                "a v1 file migrates the raw clock as the wrapped value — day 0,"
                        + " the historical fresh-moon read");
        assertTrue(loaded.inhabitedTimes().isEmpty(),
                "a v1 file carries no inhabited tail");
    }
}
