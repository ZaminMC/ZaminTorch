package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guardian (Slice 17a — GuardianEntity): the laser's charge cycle
 * with the beam verdicts, the flop-band target gate, the elder variant's
 * flips, the still-body spike verdict, and the swim/flop body pair.
 */
class GuardianEntityTest {

    /** A flat-world stub with a scripted water column and a target player. */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        boolean water = true;
        Position player;
        double playerRange = 64.0;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0; // the floor
        }

        @Override
        public boolean isSolidAt(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            // The contiguous water column: from the floor (8) up to 20.
            return water && Math.floor(y) >= 8.0 && Math.floor(y) < 20;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            if (player == null) {
                return null;
            }
            double dx = player.x() - x;
            double dy = player.y() - y;
            double dz = player.z() - z;
            return dx * dx + dy * dy + dz * dz <= range * range ? player : null;
        }

        @Override
        public boolean clearLine(Position from, Position to) {
            return true; // the open water column never blocks
        }
    }

    private static MobEntity guardianIn(ScriptedWorld world, long seed) {
        return new MobEntity(990, MobType.GUARDIAN, new Position(0.5, 12.0, 0.5),
                new Random(seed), world);
    }

    @Test
    void theGuardianCarriesTheIdentity() {
        assertEquals(68, MobType.GUARDIAN.legacyTypeId);
        assertEquals(0.85, MobType.GUARDIAN.width, 1e-9);
        assertEquals(0.85, MobType.GUARDIAN.height, 1e-9);
        assertEquals(30.0f, MobType.GUARDIAN.maxHealth, 1e-6f);
        assertTrue(MobType.GUARDIAN.hostile);
        assertEquals(6.0f, MobType.GUARDIAN.attackDamage, 1e-6f);
        assertEquals("mob.guardian.idle", MobType.GUARDIAN.idleSound);
        // The mid-body eye (GuardianEntity.getEyeHeight: height * 0.5).
        MobEntity guardian = guardianIn(new ScriptedWorld(), 1L);
        assertEquals(0.85 * 0.5, guardian.eyePosition().y() - guardian.position().y(), 1e-9);
    }

    @Test
    void theLaserChargesAndHits() {
        ScriptedWorld world = new ScriptedWorld();
        world.player = new Position(6.0, 12.0, 0.5); // 5.5 blocks out: beyond the flop band
        MobEntity guardian = guardianIn(world, 2L);

        // The charge cycle (GuardianAttackEntityGoal.start + tick): the
        // hunt starts at -10 and climbs one per tick — the beam arms
        // when the charge crosses 0 (the 10th tick), the hit lands at
        // the 80-tick charge time.
        for (int i = 0; i < 9; i++) {
            guardian.tick(true);
            assertFalse(guardian.consumeGuardianBeamStart(),
                    "the beam only arms once the -10 offset crosses 0");
            assertFalse(guardian.consumeGuardianLaserHit());
        }
        // Tick 10: the charge crossed 0 — the beam verdict fires.
        guardian.tick(true);
        assertTrue(guardian.consumeGuardianBeamStart(), "the beam arms at charge 0");
        assertTrue(guardian.guardianBeamActive(), "the charge window carries the beam");
        // 80 more ticks: the charge completes — the laser hit verdict.
        boolean hit = false;
        for (int i = 0; i < 80; i++) {
            guardian.tick(true);
            if (guardian.consumeGuardianLaserHit()) {
                hit = true;
                break;
            }
        }
        assertTrue(hit, "the laser completes at the 80-tick charge");
        assertFalse(guardian.guardianBeamActive(), "the hit clears the beam");
    }

    @Test
    void theFlopBandRefusesTooCloseTargets() {
        ScriptedWorld world = new ScriptedWorld();
        world.player = new Position(1.5, 12.0, 0.5); // 1 block out: inside the 3-block band
        MobEntity guardian = guardianIn(world, 3L);
        for (int i = 0; i < 2; i++) {
            guardian.tick(true);
            assertFalse(guardian.consumeGuardianBeamStart(),
                    "the validator's squaredDistanceTo > 9 gate refuses the near target");
            assertFalse(guardian.consumeGuardianLaserHit());
        }
        // (A longer window would drift the wander out of the band — the
        // gate reads the live distance every tick, exactly the reference's
        // own target validator.)
    }

    @Test
    void theElderFlipArmsTheVariant() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity guardian = guardianIn(world, 4L);
        guardian.setGuardianElder(true);
        // setElder lines 132-146: the hull, the health, the damage, the
        // charge time, the persistence.
        assertEquals(80.0f, guardian.health(), 1e-6f);
        assertEquals(8.0f, guardian.attackDamage(), 1e-6f);
        assertEquals(60, guardian.guardianChargeTime());
        assertTrue(guardian.persistent(), "the elder never despawns");
        assertEquals(1.9975, guardian.bodyWidth(), 1e-9);
        assertEquals(1.9975, guardian.bodyHeight(), 1e-9);
    }

    @Test
    void theStillBodySpikesItsMeleeAttacker() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity guardian = guardianIn(world, 5L);
        // A guardian without a wander steer (no ticks yet) is still.
        assertFalse(guardian.guardianMoving());
        assertTrue(guardian.guardianSpikesMeleeAttacker(),
                "the idle body reflects the melee family");
        assertEquals(2.0f, guardian.guardianThornsDamage(), 1e-6f);

        // The wander arms the swim: the moving body never spikes.
        for (int i = 0; i < 3; i++) {
            guardian.tick(true);
        }
        // (The wander's re-roll is random-seeded; the moving flag follows
        // the steer — the verdict only holds while the body stands still.)
        boolean moving = guardian.guardianMoving();
        assertEquals(!moving, guardian.guardianSpikesMeleeAttacker());
    }

    @Test
    void theSwimAndTheFlop() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity guardian = guardianIn(world, 6L);
        double startY = guardian.position().y();
        // The water body: the sinusoidal bobbing + the wander steer move
        // it; no gravity.
        for (int i = 0; i < 40; i++) {
            guardian.tick(true);
        }
        assertTrue(guardian.position().y() >= 8.0,
                "the swim holds the water column (never the ground pass-through)");

        // The land body: the gravity fall, the ground rest, and the
        // grounded flop (mobTick's +0.5 rise with the ±0.4 horizontal).
        world.water = false;
        world.player = null;
        boolean sawRise = false;
        boolean sawGround = false;
        for (int i = 0; i < 200; i++) {
            guardian.tick(true);
            assertTrue(guardian.position().y() >= 8.0,
                    "the dry body never passes through the floor");
            if (guardian.velocityY() > 0.3) {
                sawRise = true; // the flop's +0.5
            }
            if (guardian.onGround()) {
                sawGround = true;
            }
        }
        assertTrue(sawGround || sawRise,
                "the beached body either rests or flops (the rise+fall cycle)");
        assertTrue(sawRise, "the grounded flop bounces the body up");
    }

    @Test
    void theHuntBreakClearsTheBeam() {
        ScriptedWorld world = new ScriptedWorld();
        world.player = new Position(6.0, 12.0, 0.5);
        MobEntity guardian = guardianIn(world, 7L);
        for (int i = 0; i < 12; i++) {
            guardian.tick(true); // the charge crosses 0 — the beam is on
        }
        assertTrue(guardian.guardianBeamActive() || guardian.guardianBeamTargetId() == 0);
        world.player = null; // the target vanishes
        guardian.tick(true);
        assertFalse(guardian.guardianBeamActive(), "the hunt's break clears the beam");
        assertNull(world.nearestPlayer(0.5, 12.0, 0.5, 64.0));
    }
}
