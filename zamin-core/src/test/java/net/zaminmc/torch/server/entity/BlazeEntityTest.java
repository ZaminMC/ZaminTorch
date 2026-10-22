package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blaze (Slice 18a — BlazeEntity 1:1): the identity, the AttackGoal's
 * step cycle (the 60-tick charge with the fire-up, the three fireball
 * volleys six ticks apart, the 100-tick cooldown), the hover rise, and
 * the water drown.
 */
class BlazeEntityTest {

    /** A flat-world stub with a scriptable player target. */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        Position target;
        boolean water;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return water;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return target;
        }

        @Override
        public boolean clearLine(Position from, Position to) {
            return true; // the open nether
        }
    }

    private static MobEntity blazeIn(ScriptedWorld world, long seed) {
        return new MobEntity(961, MobType.BLAZE, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    @Test
    void theBlazeCarriesTheIdentity() {
        // Entities.java: the Blaze 61. The body (BlazeEntity's own
        // MonsterEntity defaults): 0.6 x 1.8, the 20 health.
        assertEquals(61, MobType.BLAZE.legacyTypeId);
        assertEquals(0.6, MobType.BLAZE.width, 1e-9);
        assertEquals(1.8, MobType.BLAZE.height, 1e-9);
        assertEquals(20.0f, MobType.BLAZE.maxHealth, 1e-6f);
        // initAttributes line 42: ATTACK_DAMAGE 6.0.
        assertEquals(6.0f, MobType.BLAZE.attackDamage, 1e-6f);
        assertTrue(MobType.BLAZE.hostile);
        assertTrue(MobType.BLAZE.fireImmune(), "immuneToFire (ctor line 28)");
        assertEquals("mob.blaze.breathe", MobType.BLAZE.idleSound);
        assertEquals("mob.blaze.hit", MobType.BLAZE.hurtSound);
        assertEquals("mob.blaze.death", MobType.BLAZE.deathSound);
        // The rod item's own identity (Item.java: the blaze rod 369).
        assertEquals("minecraft:blaze_rod",
                net.zaminmc.torch.server.item.BuiltinItems.BLAZE_ROD
                        .identifier().toString());
    }

    @Test
    void theChargeCycleLightsTheFireAndLoosesThreeVolleys() {
        ScriptedWorld world = new ScriptedWorld();
        // Ten blocks out: squared 100 — inside the volley band (4 < d0 <
        // 256), outside the melee band.
        world.target = new Position(10.5, 8.0, 0.5);
        MobEntity blaze = blazeIn(world, 81L);

        assertFalse(blaze.blazeFireActive());
        int volleys = 0;
        int fireballTicks[] = new int[3];
        int firstFireTick = -1;
        for (int tick = 1; tick <= 90; tick++) {
            blaze.tick(true);
            if (blaze.blazeFireActive() && firstFireTick < 0) {
                firstFireTick = tick;
            }
            if (blaze.consumeBlazeFireball()) {
                assertTrue(firstFireTick > 0,
                        "the volley only fires past the charge");
                assertTrue(volleys < 3, "exactly three volleys (got " + (volleys + 1) + ")");
                fireballTicks[volleys] = tick;
                volleys++;
            }
        }
        // The fire rises with the charge's first tick (step 1, the
        // 60-tick charge — AttackGoal lines 219-222).
        assertEquals(1, firstFireTick, "the fire rises with the charge");
        assertEquals(3, volleys, "the reference's own step family: 3 volleys");
        // The charge (60) then the volleys six apart: ticks 61, 67, 73.
        assertEquals(61, fireballTicks[0]);
        assertEquals(67, fireballTicks[1]);
        assertEquals(73, fireballTicks[2]);
        // The reset at step 5 clears the fire (lines 224-228) — tick 79.
        assertFalse(blaze.blazeFireActive(), "the cooldown extinguishes the fire");

        // The cooldown: no new volley until the cycle re-arms (100 ticks).
        for (int tick = 91; tick <= 170; tick++) {
            blaze.tick(true);
            assertFalse(blaze.consumeBlazeFireball(), "the cooldown holds");
        }
    }

    @Test
    void theCloseBandMeleesOnTheTwentyTickCadence() {
        ScriptedWorld world = new ScriptedWorld();
        // A block away: squared 1 — inside the melee band (d0 < 4).
        world.target = new Position(1.5, 8.0, 0.5);
        // The VIII14 cadence slice's re-pin: the ctor's idle-timer draw is
        // gone (the reference has none), so the construction stream shifted
        // one nextInt — seed 2 keeps the body inside the band for the whole
        // walk (the old seed 82's in-band drift was stream-coupled accident).
        MobEntity blaze = blazeIn(world, 2L);

        int melees = 0;
        for (int tick = 1; tick <= 61; tick++) {
            blaze.tick(true);
            if (blaze.consumePendingMeleeAttack()) {
                melees++;
            }
        }
        // The first swing lands the first tick (the timer starts at 0),
        // then every 20: ticks 1, 21, 41, 61 — four swings in 61 ticks.
        assertEquals(4, melees, "the 20-tick melee cadence");
        assertFalse(blaze.blazeFireActive(), "the close band never lights the fire");
    }

    @Test
    void theHoverRisesTowardAHigherTarget() {
        ScriptedWorld world = new ScriptedWorld();
        // Eight blocks up and ten out: the target's eye band sits far
        // above the blaze's own — the hover arm pulls the vertical
        // velocity toward 0.3 (mobAiTick lines 121-125).
        world.target = new Position(10.5, 16.0, 0.5);
        MobEntity blaze = blazeIn(world, 83L);

        double peakRise = 0;
        double baselineY = blaze.position().y();
        for (int tick = 0; tick < 60; tick++) {
            blaze.tick(true);
            peakRise = Math.max(peakRise, blaze.position().y() - baselineY);
        }
        assertTrue(peakRise > 0.05, "the hover lifts the body (rose " + peakRise + ")");
        // The climb stays bounded: the hover's steady band sits under 0.3
        // against the gravity arm's pull.
        assertTrue(blaze.velocityY() < 0.3, "the hover caps below 0.3");
    }

    @Test
    void theWaterDrownsTheBody() {
        ScriptedWorld world = new ScriptedWorld();
        world.water = true;
        MobEntity blaze = blazeIn(world, 84L);

        assertEquals(20.0f, blaze.health(), 1e-6f);
        for (int tick = 0; tick < 5; tick++) {
            blaze.tick(true);
        }
        assertEquals(15.0f, blaze.health(), 1e-6f,
                "mobAiTick's DROWN 1.0 per wet tick (lines 110-112)");
    }

    @Test
    void theFallDampeningSlowsTheAirborneDescent() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity blaze = new MobEntity(962, MobType.BLAZE, new Position(0.5, 40.0, 0.5),
                new Random(85L), world);
        // No target: the body falls from the air, the dampening (mobTick
        // lines 79-82) capping the fall's terminal velocity — the standard
        // gravity body settles past |vy| 1.9, the damped blaze stays under
        // half of that.
        double maxAbsVy = 0;
        for (int tick = 0; tick < 40; tick++) {
            blaze.tick(true);
            maxAbsVy = Math.max(maxAbsVy, Math.abs(blaze.velocityY()));
        }
        assertTrue(maxAbsVy < 1.0,
                "the damped fall stays slow (terminal |vy| " + maxAbsVy + ")");
    }

    @Test
    void theVolleySigmaFollowsTheDistanceFormula() {
        MobEntity blaze = blazeIn(new ScriptedWorld(), 86L);
        // sqrt(sqrt(100)) * 0.5 = sqrt(10) * 0.5 ≈ 1.5811.
        assertEquals(0.5 * Math.sqrt(10.0), blaze.blazeFireballSigma(100.0), 1e-9);
        // At the melee band's edge the spread collapses to sqrt(2)/2.
        assertEquals(0.5 * Math.sqrt(2.0), blaze.blazeFireballSigma(4.0), 1e-9);
    }
}
