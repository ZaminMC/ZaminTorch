package net.zaminmc.torch.server.player;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anti-cheat math and ledgers: the eye-to-box reach (the Acid shape),
 * the nuker rolling budget and the MultiBreak signature (NCP/Grim shapes),
 * the fastplace budget and the per-second decay on every ladder.
 */
class AntiCheatTest {

    @Test
    void eyeInsideTheBoxReadsZeroDistance() {
        Position eye = new Position(10.0, 1.0, 10.0);
        Position feet = new Position(10.0, 0.0, 10.0);
        assertEquals(0.0, AntiCheat.eyeToBoxDistance(eye, feet, 0.6, 1.8), 0.0001,
                "the eye inside the victim box touches");
    }

    @Test
    void closeHitsStayInsideTheReachBudget() {
        // A 2-block PvP hit: eye 1.62 up, victim 2 blocks out — the classic
        // honest swing must never flag.
        Position eye = new Position(0.0, 1.62, 0.0);
        Position feet = new Position(2.0, 0.0, 0.0);
        double distance = AntiCheat.eyeToBoxDistance(eye, feet, 0.6, 1.8);
        assertTrue(distance < AntiCheat.maxReach(false),
                "an honest 2-block hit stays legal: " + distance);
    }

    @Test
    void farHitsBreachTheSurvivalBudget() {
        // The classic reach hack: 4.5 blocks out (the old acceptance gate's
        // own band) reads beyond the survival budget.
        Position eye = new Position(0.0, 1.62, 0.0);
        Position feet = new Position(4.5, 0.0, 0.0);
        double distance = AntiCheat.eyeToBoxDistance(eye, feet, 0.6, 1.8);
        assertTrue(distance > AntiCheat.maxReach(false),
                "a 4.5-block hit flags: " + distance + " > "
                        + AntiCheat.maxReach(false));
    }

    @Test
    void diagonalHitsAtTheMovementCapStayLegal() {
        // The false-positive guard: a hit DOWN two blocks at a short
        // horizontal (the attacker on a ledge, the victim below) — the
        // eye-to-box math must keep honest geometry inside the budget.
        Position eye = new Position(0.0, 3.62, 0.0); // the attacker on a +2 ledge
        Position feet = new Position(1.8, 0.0, 0.0); // the victim below and out
        double distance = AntiCheat.eyeToBoxDistance(eye, feet, 0.9, 0.9);
        assertTrue(distance < AntiCheat.maxReach(false),
                "an honest diagonal hit stays legal: " + distance);
    }

    @Test
    void highAngleDropsBeyondVanillaReachFlag() {
        // A 4-block 3D drop-hit is beyond vanilla's own raycast — the flag
        // is correct even though the old horizontal gate allowed it.
        Position eye = new Position(0.0, 3.62, 0.0);
        Position feet = new Position(3.6, 0.0, 0.0);
        double distance = AntiCheat.eyeToBoxDistance(eye, feet, 0.9, 0.9);
        assertTrue(distance > AntiCheat.maxReach(false),
                "the beyond-vanilla drop hit flags: " + distance);
    }

    @Test
    void creativeRidesTheWiderBand() {
        assertTrue(AntiCheat.maxReach(true) > AntiCheat.maxReach(false),
                "creative reach exceeds survival reach");
    }

    @Test
    void breakBudgetRefusesTheRollingWindow() {
        Violations ledger = new Violations();
        long now = System.nanoTime();
        long window = TimeUnit.MILLISECONDS.toNanos(1000);
        for (int i = 0; i < 25; i++) {
            assertTrue(ledger.recordBreak(now + i, window, 25), "break " + i + " fits");
        }
        assertFalse(ledger.recordBreak(now + 26, window, 25),
                "the 26th finish inside the window breaches the budget");
        // Well past the window the budget resets.
        assertTrue(ledger.recordBreak(now + 3 * window, window, 25),
                "a finish past the window fits again");
    }

    @Test
    void multiBreakFlagsTheSameTickDifferentTargetSignature() {
        Violations ledger = new Violations();
        long now = System.nanoTime();
        Position first = new Position(0, 0, 0);
        Position second = new Position(5, 0, 5);
        ledger.noteBreakTarget(now, first);
        assertFalse(ledger.isMultiBreak(now, first), "the same target is no signature");
        assertTrue(ledger.isMultiBreak(now, second),
                "a different target inside the same tick flags");
        assertFalse(ledger.isMultiBreak(now + TimeUnit.MILLISECONDS.toNanos(100), second),
                "a later tick is no signature");
    }

    @Test
    void placeBudgetRefusesTheFastplaceBand() {
        Violations ledger = new Violations();
        long now = System.nanoTime();
        long window = TimeUnit.MILLISECONDS.toNanos(500);
        for (int i = 0; i < 6; i++) {
            assertTrue(ledger.recordPlace(now + i, window, 6), "place " + i + " fits");
        }
        assertFalse(ledger.recordPlace(now + 7, window, 6),
                "the 7th placement inside the band breaches the budget");
    }

    @Test
    void reachLadderAccumulatesAndKicks() {
        Violations ledger = new Violations();
        for (int i = 0; i < AntiCheat.REACH_KICK_VIOLATIONS - 1; i++) {
            ledger.addReachViolation();
        }
        assertTrue(ledger.reachViolations() < AntiCheat.REACH_KICK_VIOLATIONS,
                "just under the threshold");
        ledger.addReachViolation();
        assertEquals(AntiCheat.REACH_KICK_VIOLATIONS, ledger.reachViolations(), 0.0001,
                "the threshold lands at the kick boundary");
    }
}
