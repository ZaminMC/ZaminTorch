package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE ANTI-OSCILLATION LAW'S OWN SEATS (Phase 8's "add telemetry and
 * anti-oscillation rules before enabling automatic balancing" — the
 * {@link MigrationPolicy} law proven against the classic flapping shapes;
 * the policy stays DISARMED: nothing in the engine consults it, and these
 * seats prove the DECISION LAW, never an automatic migration).
 *
 * <p>Every seat is RED-verified on the no-op law by construction: a policy
 * without the sustain ladder answers SPLIT on the first hot sample
 * (the sustained-qualification seats), a policy without the band answers
 * MERGE on a mid-band sample (the hysteresis seats), and a policy without
 * the cooldown re-splits the tick after a split (the cooldown seats).</p>
 */
class MigrationPolicyTest {

    @Test
    void theConstructorEnforcesTheBandLaw() {
        // The band law: lowWater < highWater, both in [0, 1], sustain >= 1,
        // cooldown >= 0 — the degenerate shapes refuse loudly.
        assertThrows(IllegalArgumentException.class,
                () -> new MigrationPolicy(0.5, 0.5, 3, 600L),
                "equal waters carry no hysteresis band");
        assertThrows(IllegalArgumentException.class,
                () -> new MigrationPolicy(0.25, 0.75, 3, 600L),
                "inverted waters carry no band at all");
        assertThrows(IllegalArgumentException.class,
                () -> new MigrationPolicy(1.5, 0.25, 3, 600L),
                "waters live in [0, 1]");
        assertThrows(IllegalArgumentException.class,
                () -> new MigrationPolicy(0.75, 0.25, 0, 600L),
                "a zero sustain qualifies on nothing");
        assertThrows(IllegalArgumentException.class,
                () -> new MigrationPolicy(0.75, 0.25, 3, -1L),
                "a negative cooldown is not a window");
        // The reference shape constructs whole.
        MigrationPolicy reference = MigrationPolicy.reference();
        assertEquals(0.75, reference.highWater);
        assertEquals(0.25, reference.lowWater);
        assertEquals(3, reference.sustainSamples);
        assertEquals(600L, reference.cooldownTicks);
    }

    @Test
    void aSustainedHotRunSplitsAndASingleSpikeDoesNot() {
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 3, 600L);
        // A single spike: hot for one sample, then back — never qualifies.
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("a", 0.9, 1));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("a", 0.5, 2));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("a", 0.9, 3));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("a", 0.5, 4));
        // Two hot samples then a break: the run resets — still nothing.
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("b", 0.9, 1));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("b", 0.9, 2));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("b", 0.5, 3));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("b", 0.9, 4));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("b", 0.9, 5));
        // THE SUSTAINED RUN: three consecutive hot samples — the third splits.
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("c", 0.9, 1));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("c", 0.9, 2));
        assertEquals(MigrationPolicy.Decision.SPLIT, policy.decide("c", 0.9, 3));
        // The decision consumed the run: the next hot sample starts over.
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("c", 0.9, 4));
    }

    @Test
    void aSustainedColdRunMergesAndTheBandQualifiesNothing() {
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 3, 600L);
        // The hysteresis band: a load between the waters (0.25..0.75)
        // qualifies for NOTHING however long it sits there.
        for (long tick = 1; tick <= 20; tick++) {
            assertEquals(MigrationPolicy.Decision.NONE, policy.decide("band", 0.5, tick),
                    "the band is the no-decision zone: tick " + tick);
        }
        // THE SUSTAINED COLD RUN: three consecutive cold samples merge.
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("cold", 0.1, 1));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("cold", 0.1, 2));
        assertEquals(MigrationPolicy.Decision.MERGE, policy.decide("cold", 0.1, 3));
        // The exact water marks are exclusive: 0.75 is NOT above the high
        // water, 0.25 is NOT below the low water — both sit in the band.
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("edge-hi", 0.75, 1));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("edge-lo", 0.25, 1));
    }

    @Test
    void theSquareWaveNeverQualifies() {
        // THE CLASSIC OSCILLATION: a load square-waving across the high
        // water with a period shorter than the sustain never splits — the
        // run resets on every cold half-cycle. This is the shape a naive
        // threshold-only balancer flaps on.
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 3, 600L);
        for (long tick = 1; tick <= 100; tick++) {
            boolean hotPhase = (tick / 2) % 2 == 0; // 2-tick square wave
            double sample = hotPhase ? 0.9 : 0.5;
            assertEquals(MigrationPolicy.Decision.NONE, policy.decide("wave", sample, tick),
                    "the square wave never qualifies: tick " + tick);
        }
    }

    @Test
    void noiseAroundTheHighWaterNeverQualifies() {
        // Noise alternating above and below the mark (0.9, 0.7, 0.9, 0.7 —
        // the 0.7 sits in the band and resets the run every other sample):
        // a threshold jitter never accumulates a sustained run.
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 3, 600L);
        for (long tick = 1; tick <= 100; tick++) {
            double sample = tick % 2 == 0 ? 0.7 : 0.9;
            assertEquals(MigrationPolicy.Decision.NONE, policy.decide("noise", sample, tick),
                    "the noisy mark never qualifies: tick " + tick);
        }
    }

    @Test
    void theCooldownBlocksThePostMigrationTransientAndRequiresFreshEvidence() {
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 3, 600L);
        // The domain splits at tick 3 (the sustained run).
        policy.decide("d", 0.9, 1);
        policy.decide("d", 0.9, 2);
        assertEquals(MigrationPolicy.Decision.SPLIT, policy.decide("d", 0.9, 3));
        // The balancer executes the migration and records it.
        policy.noteMigration("d", 3);

        // The post-migration transient: the load STAYS hot (the split has
        // not settled yet) — the cooldown refuses every decision until it
        // expires, and the settling window's samples never count.
        assertTrue(policy.inCooldown("d", 4));
        for (long tick = 4; tick <= 3 + 600L; tick++) {
            assertEquals(MigrationPolicy.Decision.NONE, policy.decide("d", 0.95, tick),
                    "the cooldown holds: tick " + tick);
        }
        assertFalse(policy.inCooldown("d", 3 + 600L + 1));

        // After the cooldown: the hot samples must build a FRESH run — the
        // 600 settling samples the window swallowed never qualified.
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("d", 0.95, 604L));
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("d", 0.95, 605L));
        assertEquals(MigrationPolicy.Decision.SPLIT, policy.decide("d", 0.95, 606L),
                "the fresh sustained run splits after the cooldown");
    }

    @Test
    void theCooldownClockIsPerDomainAndNeverMigratesOthers() {
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 2, 100L);
        policy.noteMigration("migrated", 50);
        assertTrue(policy.inCooldown("migrated", 150));
        assertFalse(policy.inCooldown("neighbor", 150),
                "a never-migrated domain carries no cooldown");
        // The neighbor decides freely while the migrated domain cools.
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("neighbor", 0.9, 150));
        assertEquals(MigrationPolicy.Decision.SPLIT, policy.decide("neighbor", 0.9, 151));
        // The migrated domain is still inside its own window.
        assertEquals(MigrationPolicy.Decision.NONE, policy.decide("migrated", 0.9, 151));
    }

    @Test
    void theDecisionsArePureInTheSampleHistory() {
        // Determinism: two policies fed the same sample sequence answer the
        // same decision sequence (the balancer's replay/audit premise).
        MigrationPolicy first = new MigrationPolicy(0.8, 0.2, 2, 10L);
        MigrationPolicy second = new MigrationPolicy(0.8, 0.2, 2, 10L);
        double[] samples = {0.5, 0.9, 0.9, 0.5, 0.1, 0.1, 0.5, 0.9, 0.9};
        for (int i = 0; i < samples.length; i++) {
            long tick = i + 1;
            assertEquals(first.decide("same", samples[i], tick),
                    second.decide("same", samples[i], tick),
                    "the law is pure in the history: sample " + i);
        }
    }
}
