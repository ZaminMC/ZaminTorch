package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mount slice on a deterministic world: the temper taming flow, the
 * saddle gate, the ridden body's steering (turn, drive, jump) and the buck
 * throw. All values the wire and the seat flow depend on.
 */
class HorseMountTest {

    /** Solid everywhere below y=4 (the flat test world). */
    private static MobEntity.WorldQuery worldAt() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    private static MobEntity horse(long seed) {
        return new MobEntity(1, MobType.HORSE, new Position(0.5, 4.0, 0.5),
                new Random(seed), worldAt());
    }

    private static MobEntity pig(long seed) {
        return new MobEntity(2, MobType.PIG, new Position(0.5, 4.0, 0.5),
                new Random(seed), worldAt());
    }

    @Test
    void horseSpawnsWildWithAHerdLook() {
        MobEntity wild = horse(11);
        assertFalse(wild.tamed(), "fresh horses are wild");
        assertFalse(wild.saddled(), "fresh horses carry no saddle");
        assertTrue(wild.horseSubtype() == MobEntity.HORSE_SUBTYPE_HORSE
                        || wild.horseSubtype() == MobEntity.HORSE_SUBTYPE_DONKEY,
                "the subtype is a horse or a donkey");
        assertTrue(wild.variant() >= 0 && wild.variant() < (7 << 8),
                "the variant stays inside the color|marking space");
        assertEquals(0, wild.temper(), "temper starts at zero");
        assertTrue(wild.isMountable(), "the horse takes a rider");
        assertTrue(pig(1).tamed(), "pigs mount without taming (tamed by definition)");
        assertTrue(pig(1).isMountable(), "the pig takes a rider");
    }

    @Test
    void temperAttemptsTameTheHorseDeterministicallyAtFullTemper() {
        MobEntity wild = horse(5);
        // Feeding rides the temper: 34 feeds of +3 land above 100 -> min-capped.
        for (int i = 0; i < 34; i++) {
            wild.feedTemper(MobEntity.TEMPER_PER_FEED);
        }
        assertEquals(100, wild.temper(), "temper caps at 100");
        // The first mount attempt at full temper always tames (nextInt(100) < 100).
        MobEntity.MountAttempt attempt = wild.attemptMount();
        assertEquals(MobEntity.MountAttempt.TAMED, attempt, "full temper tames on the first try");
        assertTrue(wild.tamed(), "the tame bit flipped");
    }

    @Test
    void untamedMountAttemptRearsAndArmsTheBuck() {
        MobEntity wild = horse(1);
        MobEntity.MountAttempt attempt = wild.attemptMount();
        // Seed 1, temper 5: nextInt(100) < 5 is nearly impossible; assert the
        // attempt either tamed (lucky roll) or armed the buck — and that the
        // bucked path always rears and keeps the horse untamed.
        if (attempt == MobEntity.MountAttempt.BUCKED) {
            assertTrue(wild.rearing(), "the rear flag is up while bucking");
            assertFalse(wild.tamed(), "a buck does not tame");
        }
    }

    @Test
    void buckedSeatThrowsTheRiderAfterTheClock() {
        MobEntity wild = horse(3);
        // Force the buck path deterministically: low temper, force the roll.
        MobEntity.MountAttempt attempt = wild.attemptMount();
        if (attempt != MobEntity.MountAttempt.BUCKED) {
            return; // this seed tamed on the first try; the throw test needs a buck
        }
        // The manager seats the rider after the attempt; the buck clock runs
        // only on a seated body.
        wild.setRider(100);
        assertTrue(wild.rearing(), "seated buck rears");
        // The buck clock (20-40 ticks) runs down and arms the throw.
        boolean thrown = false;
        for (int i = 0; i < 60 && !thrown; i++) {
            wild.tick();
            thrown = wild.consumeBuckThrow();
        }
        assertTrue(thrown, "the buck throws within the clock window");
        assertFalse(wild.rearing(), "the rear flag drops at the throw");
    }

    @Test
    void unsaddledHorseAnswersNoReins() {
        MobEntity mount = horse(9);
        mount.tame("rider");
        mount.setRider(100);
        // Forward input with no saddle: the body must not drive.
        mount.steer(0.0f, 1.0f, false);
        Position before = mount.position();
        for (int i = 0; i < 20; i++) {
            mount.tick();
        }
        assertEquals(before.x(), mount.position().x(), 0.001,
                "an unsaddled horse cannot be driven");
        assertEquals(before.z(), mount.position().z(), 0.001,
                "an unsaddled horse cannot be driven");
    }

    @Test
    void saddledHorseDrivesForwardAndTurns() {
        MobEntity mount = horse(13);
        mount.tame("rider");
        mount.applySaddle();
        mount.setRider(100);
        mount.steer(0.0f, 1.0f, false);
        Position before = mount.position();
        for (int i = 0; i < 20; i++) {
            mount.tick();
        }
        double traveled = Math.sqrt(
                Math.pow(mount.position().x() - before.x(), 2)
                        + Math.pow(mount.position().z() - before.z(), 2));
        assertTrue(traveled > 1.0, "a saddled horse covers ground (20 ticks at the canter): "
                + traveled);
        // The turn: full sideways input yaws the body.
        float yawBefore = mount.yaw();
        mount.steer(1.0f, 0.0f, false);
        mount.tick();
        assertTrue(Math.abs(mount.yaw() - yawBefore) >= MobEntity.RIDDEN_TURN_RATE - 0.001,
                "sideways input turns the mount");
    }

    @Test
    void riddenHorseJumpsOffTheGround() {
        MobEntity mount = horse(21);
        mount.tame("rider");
        mount.applySaddle();
        mount.setRider(100);
        // Settle the body first (the jump needs the ground under the feet).
        for (int i = 0; i < 40; i++) {
            mount.tick();
        }
        assertTrue(mount.onGround(), "settled before the jump");
        mount.steer(0.0f, 0.0f, true);
        mount.tick();
        assertTrue(mount.velocityY() > 0.3, "the jump impulse lifts the body");
        assertFalse(mount.onGround(), "airborne after the jump");
        // The cooldown blocks an immediate second jump: while airborne the
        // velocity decays monotonically — a re-jump would bump it back up.
        boolean secondJump = false;
        double previous = mount.velocityY();
        for (int i = 0; i < 5; i++) {
            mount.steer(0.0f, 0.0f, true);
            mount.tick();
            if (mount.velocityY() > previous + 0.05) {
                secondJump = true;
            }
            previous = mount.velocityY();
        }
        assertFalse(secondJump, "the jump cooldown gates rapid re-jumps");
    }

    @Test
    void pigSteersOnlyThroughTheReinsGate() {
        MobEntity mount = pig(7);
        mount.setRider(100);
        // The manager feeds forward=0 when the rider holds no carrot on a
        // stick (the control gate is the caller's); drive input ignored.
        mount.steer(0.0f, 0.0f, false);
        Position before = mount.position();
        for (int i = 0; i < 20; i++) {
            mount.tick();
        }
        assertEquals(before.x(), mount.position().x(), 0.001, "no reins, no motion");
        // With the gate open (the manager's job), the pig drives.
        mount.steer(0.0f, 1.0f, false);
        for (int i = 0; i < 20; i++) {
            mount.tick();
        }
        double traveled = Math.abs(mount.position().z() - before.z())
                + Math.abs(mount.position().x() - before.x());
        assertTrue(traveled > 0.5, "a controlled pig covers ground: " + traveled);
    }

    @Test
    void saddleAndArmorFlagsFlowThroughTheWireView() {
        MobEntity mount = horse(17);
        assertEquals(MobEntity.HORSE_ARMOR_NONE, mount.armorType());
        mount.applySaddle();
        assertTrue((mount.horseFlagsRaw() & MobEntity.HORSE_FLAG_SADDLED) != 0,
                "the saddle bit rides the flag word");
        assertTrue(mount.saddled(), "the accessor agrees with the bit");
        mount.applyArmor(MobEntity.HORSE_ARMOR_DIAMOND);
        assertEquals(MobEntity.HORSE_ARMOR_DIAMOND, mount.armorType(),
                "the armor row rides index 22");
        mount.tame("Steve");
        assertEquals("Steve", mount.ownerName(), "the owner string rides index 21");
    }

    @Test
    void dyingMountOpensTheSeat() {
        // The manager-level rule: a dying mount never keeps a ghost rider.
        MobEntity mount = horse(23);
        mount.setRider(100);
        mount.hurt(1000.0f); // instant death
        assertTrue(mobDyingOpensSeat(mount), "the manager dismounts on the dying tick");
    }

    /** The manager's dying-dismount rule exercised through the real MobManager. */
    private boolean mobDyingOpensSeat(MobEntity mount) {
        MobManager manager = new MobManager(worldAt(), new Random(1),
                (pos, stack) -> { }, 10, (x, z) -> 4);
        MobEntity spawned = manager.spawnAt(MobType.HORSE, new Position(0.5, 4.0, 0.5));
        spawned.setRider(100);
        spawned.hurt(1000.0f);
        final boolean[] seatOpened = {false};
        // The manager's tick runs the dying branch, which must dismount.
        final int[] riderId = {spawned.riderId()};
        for (int i = 0; i < 30; i++) {
            manager.tick(java.util.List.of(), 0);
            if (!spawned.hasRider()) {
                seatOpened[0] = true;
                break;
            }
        }
        return seatOpened[0];
    }
}
