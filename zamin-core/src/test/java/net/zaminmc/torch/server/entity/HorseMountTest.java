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
    void mountingNeverRollsTheDice() {
        MobEntity wild = horse(5);
        // The seat alone consults nothing (Wave PE-25 — putPlayerOnBack
        // lines 795-803: the plain seat; the taming dice ride the RIDING
        // window, the HorseBondWithPlayerGoal's own per-tick roll).
        wild.setRider(100);
        wild.setRiderIdentity("Alex", "uuid-alex");
        assertEquals(0, wild.temper(), "the seat itself never pays temper");
        assertFalse(wild.consumeHorseBondTame(), "no tame verdict at the seat");
        assertFalse(wild.consumeHorseBondBuck(), "no buck verdict at the seat");
        assertTrue(wild.hasRider(), "the plain seat holds");
        assertFalse(wild.tamed(), "mounting never tames");
        assertFalse(wild.rearing(), "mounting never rears");
    }

    @Test
    void theRidingDiceTameAtFullTemperAndKeepTheSeat() {
        MobEntity wild = horse(5);
        // Feeding rides the temper: 34 feeds of +3 land above 100 -> min-capped.
        for (int i = 0; i < 34; i++) {
            wild.feedTemper(MobEntity.TEMPER_PER_FEED);
        }
        assertEquals(100, wild.temper(), "temper caps at 100");
        wild.setRider(100);
        wild.setRiderIdentity("Alex", "uuid-alex");
        // The dice ride the window: a 1-in-50 roll per live tick; at full
        // temper every landed dice tames (nextInt(100) < 100). The taming
        // tick RETURNS — no buck on the same roll (line 57).
        boolean tamed = false;
        for (int i = 0; i < 10_000 && !tamed; i++) {
            wild.tick();
            assertFalse(wild.consumeHorseBondBuck(), "the taming arm never bucks");
            tamed = wild.consumeHorseBondTame();
        }
        assertTrue(tamed, "the riding dice tamed within the window budget");
        assertTrue(wild.tamed(), "the tame bit flipped");
        assertEquals("Alex", wild.ownerName(), "the LIVE rider's identity rides the owner row");
        assertEquals("uuid-alex", wild.ownerUuid(), "the rider's uuid rides the owner slot");
        assertTrue(wild.hasRider(), "the taming tick keeps the seat (no buck)");
        assertFalse(wild.rearing(), "the hearts arm never rears");
    }

    @Test
    void theRidingDiceBuckRaisesTemperAndArmTheAngryWindow() {
        MobEntity wild = horse(3);
        wild.setRider(100);
        wild.setRiderIdentity("Alex", "uuid-alex");
        // Temper 0: nextInt(100) < 0 never — the first landed dice pays
        // addTemper(5) and THE BUCK (lines 60-66): the verdict, the angry
        // flag, the sound-and-smoke arm the manager drains.
        boolean bucked = false;
        for (int i = 0; i < 10_000 && !bucked; i++) {
            wild.tick();
            assertFalse(wild.consumeHorseBondTame(), "temper 0 never tames");
            bucked = wild.consumeHorseBondBuck();
        }
        assertTrue(bucked, "the riding dice bucked within the window budget");
        assertFalse(wild.tamed(), "a buck does not tame");
        assertEquals(MobEntity.TEMPER_PER_ATTEMPT, wild.temper(),
                "the failed roll paid addTemper(5)");
        assertTrue(wild.rearing(), "the buck arms the angry flag (flag 64)");
        // The manager's drain verb empties the seat; the angry window then
        // decays on the horse's own clock (tick lines 894-897: the
        // ++angryTicks > 20 law).
        wild.clearRider();
        boolean calmed = false;
        for (int i = 0; i < 40 && !calmed; i++) {
            wild.tick();
            calmed = !wild.rearing();
        }
        assertTrue(calmed, "the angry window decays after ~20 ticks");
    }

    @Test
    void anIdentitylessSeatBucksOnEveryLandedDice() {
        MobEntity wild = horse(9);
        wild.setRider(100);
        // No identity rides the seat (the anvil chain's mob-rider arm): the
        // reference's instanceof PlayerEntity gate refuses — no temper arm,
        // every landed dice is a plain buck.
        boolean bucked = false;
        for (int i = 0; i < 10_000 && !bucked; i++) {
            wild.tick();
            bucked = wild.consumeHorseBondBuck();
        }
        assertTrue(bucked, "a non-player rider is thrown at the first dice");
        assertEquals(0, wild.temper(), "the non-player arm pays no temper");
        assertFalse(wild.tamed(), "the non-player arm never tames");
    }

    @Test
    void unsaddledHorseAnswersNoReins() {
        MobEntity mount = horse(9);
        mount.tame("rider", "");
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
        mount.tame("rider", "");
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
        mount.tame("rider", "");
        mount.applySaddle();
        mount.setRider(100);
        // Settle the body first (the jump needs the ground under the feet).
        for (int i = 0; i < 40; i++) {
            mount.tick();
        }
        assertTrue(mount.onGround(), "settled before the jump");
        // THE CHARGED SADDLE JUMP (Wave PE-30 — the wire's RIDING_JUMP
        // consult lands setJumpStrength whole): strength >= 90 charges
        // the full 1.0 band, the press rears (updateAnger rides the
        // >= 0 arm — flag 64 + the rear-jump carve-out), and the steer
        // walk spends the charge as the JUMP ATTRIBUTE * the band (the
        // unrolled horse's 0.7 base — the flat stand-in retired).
        mount.setJumpStrength(100);
        assertEquals(1.0f, mount.horseJumpCharge(), 0.0001f,
                "strength >= 90 charges the full band");
        assertTrue(mount.rearing(), "the press rears (the anger arm)");
        assertTrue(mount.horseRearJumping(), "the rear-jump flag rides the press");
        mount.steer(0.0f, 0.0f, false);
        mount.tick();
        assertTrue(mount.velocityY() > 0.3, "the charged jump lifts the body: "
                + mount.velocityY());
        assertFalse(mount.onGround(), "airborne after the jump");
        assertEquals(0.0f, mount.horseJumpCharge(), 0.0001f,
                "the spend clears the charge");
        // A MID-AIR RE-ARM NEVER SPENDS (the inAir gate + the landing
        // tick's ground consult — the reference's own pair): the charge
        // armed while airborne dies at the landing, never fires.
        boolean secondJump = false;
        double previous = mount.velocityY();
        for (int i = 0; i < 12 && !mount.onGround(); i++) {
            mount.setJumpStrength(100);
            mount.tick();
            if (!mount.onGround() && mount.velocityY() > previous + 0.05) {
                secondJump = true;
            }
            previous = mount.velocityY();
        }
        assertFalse(secondJump, "a mid-air re-arm never spends");
        // The steer bit alone no longer lifts the horse (the reference's
        // jump rides the RIDING_JUMP charge, never the steer packet's
        // boolean — the seated rider's jumping flag is read by nobody).
        mount.steer(0.0f, 0.0f, true);
        double settleY = mount.velocityY();
        mount.tick();
        assertTrue(mount.velocityY() <= Math.max(settleY, 0.0) + 0.05,
                "the steer bit never jumps the horse");
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
        mount.tame("Steve", "");
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
                (pos, stack, dimension) -> { }, 10, (x, z) -> 4);
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
