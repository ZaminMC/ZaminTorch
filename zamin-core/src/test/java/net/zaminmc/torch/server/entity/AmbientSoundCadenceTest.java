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
 * THE AMBIENT-SOUND CADENCE (the VIII14 row — the last open Wave VIII item):
 * the reference's {@code MobEntity.baseTick} lines 151-159 whole — the
 * {@code random.nextInt(1000) < ambientSoundDelay++} roll, the negative
 * {@code -getAmbientSoundInterval()} reset, the per-family interval band,
 * the {@code getSoundVolume}/{@code getSoundPitch} play pair, and the
 * villager's trade-arm clock writes.
 */
class AmbientSoundCadenceTest {

    /** Solid below y=4 (the flat world's surface level). */
    private static MobEntity.WorldQuery ground() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    private static MobEntity spawn(MobType type, long seed) {
        return new MobEntity(1, type, new Position(0.5, 4.0, 0.5), new Random(seed), ground());
    }

    @Test
    void theDelayClimbsOnePerRollUntilTheFirstPlay() {
        // The clock's own law: 0 at construction, +1 every walked tick
        // (the post-increment), never resetting before the first hit. The
        // hit tick itself writes the exact -interval reset (the increment's
        // own value discarded), and the walks after it climb from there.
        MobEntity cow = spawn(MobType.COW, 101L);
        assertEquals(0, cow.ambientSoundDelay(), "the reference's plain field default");
        int hitTick = -1;
        for (int i = 1; i <= 80 && hitTick < 0; i++) {
            cow.ambientSoundDue();
            int d = cow.ambientSoundDelay();
            if (d < 0) {
                hitTick = i;
                assertEquals(-120, d, "the hit tick writes the exact -interval reset");
                // one more walk climbs from the reset: -120 -> -119
                cow.ambientSoundDue();
                assertEquals(-119, cow.ambientSoundDelay(), "the post-hit climb");
            } else {
                assertEquals(i, d, "the climb: the delay equals the tick count before the hit");
            }
        }
        assertTrue(hitTick > 0, "a hit landed inside 80 walks (the hazard's own scale)");
    }

    @Test
    void theFirstPlayAndEveryGapObeyTheHardBands() {
        // The hazard laws: the first play by tick 1001 at the latest (any
        // roll succeeds once the delay reaches 1000), every gap inside
        // [interval + 1, interval + 1000] — the delay must first climb back
        // from the negative reset before another hit is even possible, and
        // it can never starve past +1000 of hazard.
        MobEntity cow = spawn(MobType.COW, 23L);
        List<Integer> plays = new ArrayList<>();
        for (int i = 1; i <= 4_000; i++) {
            if (cow.ambientSoundDue() != null) {
                plays.add(i);
            }
        }
        assertFalse(plays.isEmpty(), "a hit is guaranteed by tick 1001");
        int first = plays.get(0);
        assertTrue(first >= 2 && first <= 1001, "the first play's band [2, 1001], got " + first);
        for (int i = 1; i < plays.size(); i++) {
            int gap = plays.get(i) - plays.get(i - 1);
            assertTrue(gap >= 121 && gap <= 1120, "the cow's gap band [121, 1120], got " + gap);
        }
        assertTrue(plays.size() >= 12, "the 1:1 density over 4000 ticks, got " + plays.size());
    }

    @Test
    void theIntervalFamilyBandsMatchTheReference() {
        // getAmbientSoundInterval's override family: the animal band 120
        // (the tameable children with it), the water mob, the golems, the
        // guardian's 160, the horse's 400, the base 80 for the rest.
        for (MobType t : List.of(MobType.PIG, MobType.COW, MobType.CHICKEN, MobType.SHEEP,
                MobType.RABBIT, MobType.WOLF, MobType.OCELOT, MobType.MOOSHROOM,
                MobType.SQUID, MobType.IRON_GOLEM, MobType.SNOW_GOLEM)) {
            assertEquals(120, spawn(t, 5L).ambientSoundInterval(),
                    t + " carries the animal/golem/water 120");
        }
        assertEquals(160, spawn(MobType.GUARDIAN, 5L).ambientSoundInterval(),
                "the guardian's own 160 (GuardianEntity line 197)");
        assertEquals(400, spawn(MobType.HORSE, 5L).ambientSoundInterval(),
                "the horse family's 400 (HorseBaseEntity lines 570-572)");
        for (MobType t : List.of(MobType.ZOMBIE, MobType.SKELETON, MobType.CREEPER,
                MobType.SPIDER, MobType.CAVE_SPIDER, MobType.SILVERFISH, MobType.BLAZE,
                MobType.ENDERMAN, MobType.WITCH, MobType.SLIME, MobType.MAGMA_CUBE,
                MobType.GHAST, MobType.ZOMBIE_PIGMAN, MobType.BAT, MobType.VILLAGER,
                MobType.ENDERMITE, MobType.GIANT, MobType.WITHER, MobType.ENDER_DRAGON)) {
            assertEquals(80, spawn(t, 5L).ambientSoundInterval(),
                    t + " carries the base 80 (MobEntity lines 138-140)");
        }
    }

    @Test
    void theVolumeFamilyBandsMatchTheReference() {
        // getSoundVolume's overrides: the ghast's 10.0, the dragon's 5.0,
        // the horse's 0.8, the cow family's 0.4 (the mooshroom inherits),
        // the squid's, the ocelot/wolf pair's, the bat's 0.1, the slime's
        // 0.4 * size, the base 1.0 for the rest.
        assertEquals(10.0f, spawn(MobType.GHAST, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(5.0f, spawn(MobType.ENDER_DRAGON, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(0.8f, spawn(MobType.HORSE, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(0.4f, spawn(MobType.COW, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(0.4f, spawn(MobType.MOOSHROOM, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(0.4f, spawn(MobType.OCELOT, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(0.4f, spawn(MobType.WOLF, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(0.4f, spawn(MobType.SQUID, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(0.1f, spawn(MobType.BAT, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(0.4f, spawn(MobType.SLIME, 5L).ambientSoundVolume(), 1e-6f,
                "the size-1 slime's 0.4 * 1");
        assertEquals(1.0f, spawn(MobType.ZOMBIE, 5L).ambientSoundVolume(), 1e-6f);
        assertEquals(1.0f, spawn(MobType.VILLAGER, 5L).ambientSoundVolume(), 1e-6f);
    }

    @Test
    void thePitchCarriesTheBabyAndAdultBands() {
        // getSoundPitch (LivingEntity lines 1060-1064): the two-draw jitter
        // (r - r) * 0.2 around 1.5 for a baby, around 1.0 for a grown body.
        // The distribution pins: every sample inside the hard bounds, the
        // means pinned to the band centers (the jitter's own mean is zero).
        MobEntity adult = spawn(MobType.COW, 77L);
        double adultSum = 0.0;
        for (int i = 0; i < 2_000; i++) {
            float p = adult.ambientSoundPitch();
            assertTrue(p >= 0.8f && p <= 1.2f, "the adult band [0.8, 1.2], got " + p);
            adultSum += p;
        }
        assertEquals(1.0, adultSum / 2_000, 0.01, "the adult band's center");
        MobEntity baby = spawn(MobType.COW, 78L);
        baby.setBreedingAge(-24000);
        assertTrue(baby.isBaby(), "the fixture is a baby");
        double babySum = 0.0;
        for (int i = 0; i < 2_000; i++) {
            float p = baby.ambientSoundPitch();
            assertTrue(p >= 1.3f && p <= 1.7f, "the baby band [1.3, 1.7], got " + p);
            babySum += p;
        }
        assertEquals(1.5, babySum / 2_000, 0.01, "the baby band's center");
    }

    @Test
    void theSilentFamiliesRollButNeverPlay() {
        // getAmbientSound() == null never plays (the reference's gate): the
        // cadence rolls and resets the clock all the same — only the voice
        // is absent. The engine's empty-string idle is the same silence.
        for (MobType t : List.of(MobType.WITCH, MobType.SQUID, MobType.CREEPER,
                MobType.SLIME, MobType.MAGMA_CUBE, MobType.GIANT)) {
            MobEntity mob = spawn(t, 31L);
            boolean played = false;
            for (int i = 1; i <= 1_200 && !played; i++) {
                played = mob.ambientSoundDue() != null;
            }
            assertFalse(played, t + " has no ambient voice in the reference");
        }
    }

    @Test
    void theDeadBodyStopsRolling() {
        // isAlive() gates the whole arm: a dead body neither draws nor
        // climbs — the clock freezes exactly where it stopped.
        MobEntity zombie = spawn(MobType.ZOMBIE, 41L);
        for (int i = 0; i < 40; i++) {
            zombie.ambientSoundDue();
        }
        int frozen = zombie.ambientSoundDelay();
        zombie.hurt(1000.0f); // the 20-health body dies
        assertTrue(zombie.dead(), "the fixture died");
        for (int i = 0; i < 100; i++) {
            assertNull(zombie.ambientSoundDue(), "the dead body never plays");
        }
        assertEquals(frozen, zombie.ambientSoundDelay(), "the frozen clock");
    }

    @Test
    void theIdleNamesMatchTheReferenceTable() {
        // The static family survey (getAmbientSound's own returns) — the
        // slice's three repairs (the cow's say, the chicken's say, the
        // villager's haggle) beside the already-correct rows.
        assertEquals("mob.pig.say", spawn(MobType.PIG, 3L).effectiveIdleSound());
        assertEquals("mob.cow.say", spawn(MobType.COW, 3L).effectiveIdleSound());
        assertEquals("mob.chicken.say", spawn(MobType.CHICKEN, 3L).effectiveIdleSound());
        assertEquals("mob.sheep.say", spawn(MobType.SHEEP, 3L).effectiveIdleSound());
        assertEquals("mob.rabbit.idle", spawn(MobType.RABBIT, 3L).effectiveIdleSound());
        assertEquals("mob.villager.haggle", spawn(MobType.VILLAGER, 3L).effectiveIdleSound());
        assertEquals("mob.bat.idle", spawn(MobType.BAT, 3L).effectiveIdleSound());
        assertEquals("mob.ghast.moan", spawn(MobType.GHAST, 3L).effectiveIdleSound());
        assertEquals("mob.blaze.breathe", spawn(MobType.BLAZE, 3L).effectiveIdleSound());
        assertEquals("mob.silverfish.say", spawn(MobType.SILVERFISH, 3L).effectiveIdleSound());
        assertEquals("mob.silverfish.say", spawn(MobType.ENDERMITE, 3L).effectiveIdleSound());
        assertEquals("mob.zombie.say", spawn(MobType.ZOMBIE, 3L).effectiveIdleSound());
        assertEquals("mob.skeleton.say", spawn(MobType.SKELETON, 3L).effectiveIdleSound());
        assertEquals("mob.spider.say", spawn(MobType.SPIDER, 3L).effectiveIdleSound());
        assertEquals("mob.zombiepig.zpig", spawn(MobType.ZOMBIE_PIGMAN, 3L).effectiveIdleSound());
        assertEquals("mob.wither.idle", spawn(MobType.WITHER, 3L).effectiveIdleSound());
        assertEquals("mob.enderdragon.growl", spawn(MobType.ENDER_DRAGON, 3L).effectiveIdleSound());
        assertEquals("mob.horse.idle", spawn(MobType.HORSE, 3L).effectiveIdleSound());
        assertEquals("mob.cow.say", spawn(MobType.MOOSHROOM, 3L).effectiveIdleSound(),
                "the mooshroom inherits the cow's voice");
    }

    @Test
    void theEndermanAngryArmSwapsTheVoice() {
        // EndermanEntity.getAmbientSound lines 241-244: the angry body's
        // scream over the calm hum.
        MobEntity enderman = spawn(MobType.ENDERMAN, 51L);
        assertEquals("mob.endermen.idle", enderman.effectiveIdleSound());
        enderman.setEndermanAngry(true);
        assertEquals("mob.endermen.scream", enderman.effectiveIdleSound());
        enderman.setEndermanAngry(false);
        assertEquals("mob.endermen.idle", enderman.effectiveIdleSound());
    }

    @Test
    void theVillagerArmsWriteTheClock() {
        // trade()'s unconditional reset (line 600) and updateOffer's
        // 20-tick gate (line 627: the clock must climb past -interval + 20
        // — 21 walks after the reset, exactly).
        MobEntity villager = spawn(MobType.VILLAGER, 61L);
        assertEquals(80, villager.ambientSoundInterval(), "the villager carries the base 80");
        villager.resetAmbientSoundDelay();
        assertEquals(-80, villager.ambientSoundDelay(), "the reset writes -interval");
        assertFalse(villager.ambientOfferSoundGateOpen(),
                "the gate is closed for 20 ticks after the reset");
        for (int i = 0; i < 20; i++) {
            villager.ambientSoundDue(); // the climb: -80 -> -61
        }
        assertFalse(villager.ambientOfferSoundGateOpen(),
                "still closed at -61? no: -61 > -60 — this asserts the 21st");
        // -80 + 20 = -60 is NOT > -60: the gate opens on the 21st walk.
        villager.ambientSoundDue();
        assertTrue(villager.ambientOfferSoundGateOpen(),
                "the gate opens past -interval + 20");
        // A second reset closes it again.
        villager.resetAmbientSoundDelay();
        assertFalse(villager.ambientOfferSoundGateOpen());
    }

    @Test
    void theCadenceDeliversTheCowPlayBundle() {
        // The record's own shape: the name, the cow's 0.4 volume, the pitch
        // inside the adult band — everything the onMobSoundAt fan carries.
        MobEntity cow = spawn(MobType.COW, 71L);
        MobEntity.AmbientSound bundle = null;
        for (int i = 1; i <= 1_100 && bundle == null; i++) {
            bundle = cow.ambientSoundDue();
        }
        assertNotNull(bundle, "a play is guaranteed by tick 1001");
        assertEquals("mob.cow.say", bundle.name());
        assertEquals(0.4f, bundle.volume(), 1e-6f);
        assertTrue(bundle.pitch() >= 0.8f && bundle.pitch() <= 1.2f,
                "the adult pitch band, got " + bundle.pitch());
    }
}
