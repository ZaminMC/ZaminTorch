package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE MOB HURT-FRAME FAMILY (LivingEntity.takeDamage lines 652-669 + tick
 * lines 246-247 — the i-frame law the pre-fix engine carried NO walk for
 * at all): inside the window's first half ({@code invulnerableTimer >
 * invulnerableTicks / 2}) a hit at or under the frame's standing damage
 * is REFUSED whole; a stronger one pays only its EXCESS — no flash, no
 * re-arm; a fresh hit arms the 20-tick frame and pays in full. The
 * contact arms (lava/in-wall/cactus/drown/fire/void) ride the same frame
 * — the first wound IMMEDIATE, then one per window, and a recent
 * STRONGER hit holds the smaller contact wound off (the cross-source
 * law). The retired custom cadence timers (lava/in-wall/cactus) held the
 * first wound a full window deep — the documented divergence this wave
 * closes.
 */
class HurtFrameParityDiscoveryTest {

    /** A flat-world stub carrying the frame seats' own consults. */
    private static final class FrameWorld implements MobEntity.WorldQuery {
        boolean lava;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return lava;
        }
    }

    private static MobEntity cowIn(FrameWorld world, long seed) {
        return new MobEntity(920, MobType.COW, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    // ------------------------------------------------------------------
    // Seat 1 — the redundant hit refused whole
    // ------------------------------------------------------------------

    /**
     * Two identical hits with no ticks between: the first arms the frame
     * and pays; the second is REFUSED whole (line 657's
     * {@code amount <= lastDamageTaken} — no health, and the walk answers
     * false). RED on the pre-fix engine (both hits paid in full — the
     * machine-gun law the frame exists to refuse).
     */
    @Test
    void theRedundantHitInsideTheWindowIsRefusedWhole() {
        FrameWorld world = new FrameWorld();
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        MobManager manager = new MobManager(world, new Random(70L), new NoLoot(),
                994_000, (x, z) -> 8);
        MobEntity cow = manager.spawnAt(MobType.COW, new Position(0.5, 8.0, 0.5));

        assertTrue(manager.hurt(cow, 2.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE),
                "the fresh hit pays in full");
        assertEquals(8.0f, cow.health(), 1e-6f,
                "the cow's 10 health pays the fresh 2.0");
        assertEquals(2.0f, cow.lastDamageTaken(), 1e-6f,
                "the frame's standing damage (line 665's write)");

        assertFalse(manager.hurt(cow, 2.0f, 0.0,
                        net.zaminmc.torch.event.DamageCause.MELEE),
                "lines 657-659: the identical hit inside the window's first"
                        + " half is refused WHOLE (the walk answers false)");
        assertEquals(8.0f, cow.health(), 1e-6f,
                "the refused hit pays nothing (the pre-fix engine paid it"
                        + " in full — every rapid hit landed)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the stronger hit's excess alone
    // ------------------------------------------------------------------

    /**
     * A 5.0 hit then an 8.0 hit inside the window: only the 3.0
     * DIFFERENCE wounds (line 661's
     * {@code applyDamage(source, amount - lastDamageTaken)}), and the
     * excess arm never FLASHES ({@code flag = false} — damagedTimer is
     * the fresh arm's own line 668). RED on the pre-fix engine (the
     * second hit paid all 8.0).
     */
    @Test
    void theStrongerHitPaysOnlyItsExcessWithoutTheFlash() {
        FrameWorld world = new FrameWorld();
        MobEntity cow = cowIn(world, 71L);

        assertTrue(cow.consultHurtFrame(5.0f) == 0.0f,
                "the fresh arm's verdict: pay in full");
        cow.hurt(5.0f);
        assertEquals(5.0f, cow.health(), 1e-6f, "10 - 5");

        cow.setHurtTime(0); // the fresh flash drained by hand
        float verdict = cow.consultHurtFrame(8.0f);
        assertTrue(verdict > 0.0f, "the excess arm's verdict");
        assertEquals(3.0f, verdict, 1e-6f,
                "lines 660-662: the 8.0 against the frame's standing 5.0"
                        + " pays the 3.0 difference alone");
        cow.hurt(verdict, false, false);
        assertEquals(2.0f, cow.health(), 1e-6f, "5 - 3: the excess alone");
        assertEquals(8.0f, cow.lastDamageTaken(), 1e-6f,
                "line 662: the frame's standing damage lifts to the stronger"
                        + " hit's own");
        assertEquals(0, cow.hurtTime(),
                "the excess arm never flashes (flag = false — the pre-fix"
                        + " walk flashed every hit)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the window's decay
    // ------------------------------------------------------------------

    /**
     * After the window's first half drains (10 ticks), the identical hit
     * lands FRESH again — the frame's own decay (tick lines 246-247, one
     * per tick from the fresh arm's 20).
     */
    @Test
    void theWindowDecaysAndTheSameHitLandsFreshAgain() {
        FrameWorld world = new FrameWorld();
        MobEntity cow = cowIn(world, 72L);

        cow.consultHurtFrame(2.0f);
        cow.hurt(2.0f);
        for (int i = 0; i < 10; i++) {
            cow.tick(false); // the frame drains one per tick
        }
        assertTrue(cow.consultHurtFrame(2.0f) == 0.0f,
                "past the window's first half the same 2.0 arms a fresh"
                        + " frame (the pre-half consult would refuse it)");
        cow.hurt(2.0f);
        assertEquals(6.0f, cow.health(), 1e-6f, "10 - 2 - 2");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the lava contact's first wound is IMMEDIATE
    // ------------------------------------------------------------------

    /**
     * The lava contact wounds the FIRST tick the body stands in it (the
     * reference's every-tick takeDamage(LAVA, 4.0) against the frame —
     * the frame's window IS the cadence), then one wound per ten ticks.
     * RED on the pre-fix engine (the retired lavaDamageTimer held the
     * FIRST wound a full window deep — the documented divergence).
     */
    @Test
    void theLavaContactWoundsTheFirstTickThenOnePerWindow() {
        FrameWorld world = new FrameWorld();
        world.lava = true;
        MobEntity cow = cowIn(world, 73L);

        cow.tick(false); // the very first submerged tick
        assertEquals(6.0f, cow.health(), 1e-6f,
                "the first lava tick wounds immediately (the retired timer"
                        + " held it ten ticks deep)");
        for (int i = 0; i < 9; i++) {
            cow.tick(false);
        }
        assertEquals(6.0f, cow.health(), 1e-6f,
                "the frame refuses the redundant 4.0s for the window's"
                        + " first half");
        cow.tick(false); // the eleventh tick: the window drained
        assertEquals(2.0f, cow.health(), 1e-6f,
                "one wound per ten ticks — the frame's own cadence");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the cross-source law
    // ------------------------------------------------------------------

    /**
     * A recent STRONGER hit holds the smaller contact wound off: the
     * melee's 5.0 arms the frame, and the in-wall 1.0 refuses inside the
     * window's first half (the reference's shared takeDamage consult —
     * the fire family, the contact family, every source reads the SAME
     * frame). RED on the pre-fix engine (the contact arms carried their
     * own flat timers, blind to the melee's window).
     */
    @Test
    void theStrongerMeleeHoldsTheSmallerContactWoundOff() {
        FrameWorld world = new FrameWorld();
        MobEntity cow = cowIn(world, 74L);

        cow.consultHurtFrame(5.0f);
        cow.hurt(5.0f);

        cow.hurtThroughFrame(1.0f, true); // the in-wall form
        assertEquals(5.0f, cow.health(), 1e-6f,
                "the 1.0 contact wound at or under the frame's standing 5.0"
                        + " refuses whole (the cross-source law)");
        for (int i = 0; i < 10; i++) {
            cow.tick(false);
        }
        cow.hurtThroughFrame(1.0f, true);
        assertEquals(4.0f, cow.health(), 1e-6f,
                "past the window's first half the contact wound lands fresh");
    }
}
