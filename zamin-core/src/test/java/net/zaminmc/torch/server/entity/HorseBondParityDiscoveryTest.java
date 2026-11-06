package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE HORSE-BOND FAMILY'S DISCOVERY SEAT (Wave PE-25): the reference's
 * HorseBondWithPlayerGoal whole — the taming law the engine's former
 * MountAttempt stand-in rolled at MOUNT time, retired onto the RIDING
 * window it actually rides. Each law read from the reference's own
 * source (reference/1.8.8 net/minecraft/entity/ai/goal/
 * HorseBondWithPlayerGoal.java): the rider gate (canStart line 23), the
 * (5, 4) wander pick (line 24), the 1-in-50 per-tick dice (tick line
 * 50), the temper dice nextInt(maxTemper) &lt; temper (line 54), the
 * hearts Entity Status 7 on the tame (line 56, no buck — the return at
 * line 57), the +5 temper on the miss (line 60), the BUCK (lines
 * 63-66): the thrown dismount, the angry flag, the angry sound, the
 * smoke Entity Status 6 — all through the manager's drains.
 */
class HorseBondParityDiscoveryTest {

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

    /** The recorded wire events (the manager's listener surface). */
    private static final class Events implements MobManager.Listener {
        final List<Integer> statusEvents = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        final List<Float> volumes = new ArrayList<>();
        int dismounts;
        int thrownDismounts;
        int flagChanges;

        @Override public void onMobExploded(MobEntity mob) {
            // the creeper's own family — never in this seat
        }

        @Override public void onMobSpawned(MobEntity mob) {
        }

        @Override public void onMobMoved(MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession rider) {
        }

        @Override public void onMobHurt(MobEntity mob) {
        }

        @Override public void onMobDied(MobEntity mob) {
        }

        @Override public void onMobRemoved(MobEntity mob, String reason) {
        }

        @Override public void onMobAttackedPlayer(MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession target, float damage) {
        }

        @Override public void onMobSound(MobEntity mob, String soundName) {
        }

        @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) {
        }

        @Override public void onMobFuseChanged(MobEntity mob, boolean priming) {
        }

        @Override public void onMobSheared(MobEntity mob, int woolCount) {
        }

        @Override public void onMobCoatRegrown(MobEntity mob) {
        }

        @Override public void onMobStatusEvent(MobEntity mob, int status) {
            statusEvents.add(status);
        }

        @Override public void onMobSoundAt(MobEntity mob, String soundName,
                                           float volume, float pitch) {
            sounds.add(soundName);
            volumes.add(volume);
        }

        @Override public void onMobDismounted(MobEntity mob, int riderEngineId,
                                              Position exit, boolean thrown) {
            dismounts++;
            if (thrown) {
                thrownDismounts++;
            }
        }

        @Override public void onHorseFlagsChanged(MobEntity mob) {
            flagChanges++;
        }
    }

    private record Rig(MobManager manager, Events events) {
    }

    private static Rig rig(long seed) {
        Events events = new Events();
        MobManager manager = new MobManager(worldAt(), new Random(seed),
                (pos, stack, dimension) -> { }, 2_000_000, (x, z) -> 4);
        manager.addListener(events);
        return new Rig(manager, events);
    }

    // ------------------------------------------------ the claim gates

    /**
     * canStart's own pair (lines 22-36): the UNTAMED gate and the rider
     * gate — no seat, no claim; a tamed body never claims; and the pick
     * itself lives inside the ±5/±4/±5 band (the escape family's own
     * TargetFinder pair, line 24).
     */
    @Test
    void theClaimRidesTheUntamedGateAndTheSeat() {
        MobEntity wild = new MobEntity(1, MobType.HORSE, new Position(0.5, 4.0, 0.5),
                new Random(7L), worldAt());
        assertFalse(wild.goalHorseBondCanUse(),
                "no rider, no claim (canStart line 23's rider gate)");
        wild.setRider(100);
        assertTrue(wild.goalHorseBondCanUse(),
                "the seated untamed horse claims the window");
        Position picked = bondTargetForTest(wild);
        assertNotNull(picked, "the claim holds its wander pick");
        assertTrue(Math.abs(picked.x() - 0.5) <= 5.0 && Math.abs(picked.z() - 0.5) <= 5.0,
                "the pick lives inside the ±5 horizontal band");
        assertTrue(picked.y() >= 0.0 && picked.y() <= 8.0,
                "the pick lives inside the ±4 vertical band");
        // The untamed gate: a tamed body never claims, seat or not.
        wild.tame("Alex", "uuid-alex");
        assertFalse(wild.goalHorseBondCanUse(),
                "the tamed body refuses the claim (canStart line 23's !isTame())");
        assertNull(bondTargetForTest(wild), "the tamed claim holds no pick");
    }

    /** @return the bond window's live pick (the acceptance pin's read). */
    private static Position bondTargetForTest(MobEntity mob) {
        return mob.horseBondTargetForTest();
    }

    // ------------------------------------------------ the manager drains

    /**
     * THE BUCK DRAIN (tick lines 60-66 through the manager): the landed
     * dice at low temper pays addTemper(5), the THROWN dismount opens
     * the seat, the angry sound plays at the horse's 0.8 volume (the
     * type table: the plain horse's own cry, getAngreType lines
     * 516-524), the smoke Entity Status 6 fires, and the angry flag
     * stands with its 20-tick decay.
     */
    @Test
    void theBuckDrainThrowsTheSeatWithTheAngryPair() {
        Rig rig = rig(3);
        MobEntity wild = rig.manager().spawnAt(MobType.HORSE,
                new Position(0.5, 4.0, 0.5));
        wild.setTemperForTest(0);
        assertTrue(rig.manager().mountMob(wild, 500, "Alex", "uuid-alex"),
                "the plain seat holds (no dice at the mount)");
        assertEquals(0, wild.temper(), "the mount paid no temper (the retired stand-in's law)");
        boolean bucked = false;
        for (int i = 0; i < 10_000 && !bucked; i++) {
            rig.manager().tick(List.of(), 1_000L + i * 50L);
            bucked = !wild.hasRider();
        }
        assertTrue(bucked, "the riding dice threw the rider within the budget");
        assertEquals(1, rig.events().thrownDismounts,
                "the buck's exit is the THROWN dismount (line 63's startRiding(null))");
        assertEquals(MobEntity.TEMPER_PER_ATTEMPT, wild.temper(),
                "the failed roll paid addTemper(5) (line 60)");
        assertFalse(wild.tamed(), "the buck does not tame");
        assertTrue(wild.rearing(), "the angry flag stands (updateAnger's flag 64)");
        assertEquals(1, rig.events().sounds.size(),
                "the angry sound played once (playAngrySound line 65)");
        assertEquals(wild.horseAngrySound(), rig.events().sounds.get(0),
                "the type table's own id (getAngreType lines 516-524)");
        assertEquals(0.8f, rig.events().volumes.get(0), 1e-6,
                "the horse's own volume (getSoundVolume line 595: 0.8F)");
        assertTrue(rig.events().statusEvents.contains(6),
                "the smoke Entity Status 6 fired (doEntityEvent line 66)");
        assertFalse(rig.events().statusEvents.contains(7),
                "the buck arm never fires the hearts");
        // The angry window decays on the horse's own clock (tick lines
        // 894-897): ~20 ticks after the throw the flag drops.
        boolean calmed = false;
        for (int i = 0; i < 40 && !calmed; i++) {
            rig.manager().tick(List.of(), 2_000_000L + i * 50L);
            calmed = !wild.rearing();
        }
        assertTrue(calmed, "the angry window decayed after ~20 ticks");
    }

    /**
     * THE TAME DRAIN (tick lines 52-57 through the manager): the landed
     * dice at full temper tames — the LIVE rider's identity on the owner
     * rows (bondWithPlayer lines 1020-1024), the hearts Entity Status 7
     * (line 56), the flags publication, and NO buck: the taming tick
     * returns before the throw (line 57) — the seat holds.
     */
    @Test
    void theTameDrainFiresTheHeartsAndHoldsTheSeat() {
        Rig rig = rig(5);
        MobEntity wild = rig.manager().spawnAt(MobType.HORSE,
                new Position(0.5, 4.0, 0.5));
        // Feeding rides the temper to the cap (the interact's own arm —
        // addTemper over the foods' band).
        for (int i = 0; i < 34; i++) {
            wild.feedTemper(MobEntity.TEMPER_PER_FEED);
        }
        assertEquals(100, wild.temper(), "temper caps at 100 (getMaxTemper line 567)");
        assertTrue(rig.manager().mountMob(wild, 500, "Alex", "uuid-alex"),
                "the plain seat holds");
        boolean tamed = false;
        for (int i = 0; i < 10_000 && !tamed; i++) {
            rig.manager().tick(List.of(), 1_000L + i * 50L);
            tamed = wild.tamed();
        }
        assertTrue(tamed, "the riding dice tamed within the budget (the 1-in-50 window)");
        assertEquals("Alex", wild.ownerName(),
                "the LIVE rider's name rides the owner row (bondWithPlayer line 1022)");
        assertEquals("uuid-alex", wild.ownerUuid(),
                "the rider's uuid rides the owner slot");
        assertTrue(wild.hasRider(), "the taming tick keeps the seat (line 57's return)");
        assertTrue(rig.events().statusEvents.contains(7),
                "the hearts Entity Status 7 fired (doEntityEvent line 56)");
        assertFalse(rig.events().statusEvents.contains(6),
                "the taming arm never fires the smoke");
        assertEquals(0, rig.events().thrownDismounts, "no throw on the taming window");
        assertTrue(rig.events().flagChanges > 0,
                "the flags publication rode the drain (the tame bit flipped)");
    }

    // ------------------------------------------------ the untamed seat's mind

    /**
     * THE UNTAMED SEAT KEEPS THE MIND (the reference's own law:
     * serverTickAi carries NO rider gate — MobEntity line 441 runs under
     * isLocallyControlled alone; moveRelative's rider arm requires
     * isSaddled(), HorseBaseEntity line 1034, so the unsaddled body's
     * AI still owns the movement). The proof is behavioral: the dice
     * LAND while an untamed rider sits (the window above), and a TAMED
     * seat — the parked mind — never rolls one.
     */
    @Test
    void aTamedSeatParksTheMindAndNeverRollsTheDice() {
        Rig rig = rig(9);
        MobEntity mount = rig.manager().spawnAt(MobType.HORSE,
                new Position(0.5, 4.0, 0.5));
        mount.tame("Alex", "uuid-alex");
        assertTrue(rig.manager().mountMob(mount, 500, "Alex", "uuid-alex"),
                "the tamed seat holds");
        // The rider-park window: 2000 ticks would land ~40 dice at
        // 1-in-50 if the mind ran (P(no dice) ≈ e^-40) — none may land.
        for (int i = 0; i < 2_000; i++) {
            rig.manager().tick(List.of(), 1_000L + i * 50L);
        }
        assertFalse(mount.consumeHorseBondTame(), "the parked mind rolls no dice");
        assertFalse(mount.consumeHorseBondBuck(), "the parked mind rolls no dice");
        assertTrue(mount.hasRider(), "the tamed seat holds through the window");
        assertEquals(0, rig.events().thrownDismounts, "no throw on the tamed seat");
    }
}
