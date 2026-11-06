package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE HORSE'S EATING-WINDOW FLAG FAMILY'S DISCOVERY SEAT (Wave PE-28):
 * the reference's TWO graze windows, each law read from its own source
 * (reference/1.8.8 net/minecraft/entity/living/mob/passive/animal/
 * HorseBaseEntity.java):
 *
 * <ul>
 *   <li>FLAG 32, THE EATING-HAY WINDOW — the long graze: the
 *       spontaneous dice (mobTick lines 862-865: nextInt(300), no
 *       rider, a GRASS block below), the 50-tick close (lines 867-869),
 *       and the two clears — the anger (setAngry(true), line 982) and
 *       the mount (putPlayerOnBack, lines 798-799).</li>
 *   <li>FLAG 128, THE EATING FLASH — the brief head-down chew every
 *       SOUND consult opens (setEating, lines 956-960: the clock to 1,
 *       the flag up; getHurtSound line 480, getAmbientSound line 501,
 *       getDeathSound line 454, getAngreType line 517,
 *       playEatingAnimation line 348), the 30-tick UP-count decay
 *       (tick line 889), and the eating sound's two-draw pitch
 *       (playEatingAnimation line 351).</li>
 *   <li>THE OWN mobTick FAMILY BESIDE THEM — the slow regen
 *       (nextInt(900), lines 858-860), the ai-dead park consult (the
 *       isDead override, line 811: the ridden SADDLED seat, the graze,
 *       and the rear), the horse's own parent-walk registration
 *       (line 111), and the bred child's family walk (lines 872-878).
 *   </li>
 * </ul>
 */
class HorseGrazeParityDiscoveryTest {

    /** A nextInt(bound) ladder: every consult hits while the ladder
     *  stands, the plain stream after it. */
    private static final class ScriptedRandom extends java.util.Random {
        int[] intResults = {};
        int intAt = 0;

        @Override
        public int nextInt(int bound) {
            if (intAt < intResults.length) {
                int value = intResults[intAt++];
                return Math.min(Math.max(value, 0), bound - 1);
            }
            return super.nextInt(bound);
        }
    }

    /** The flat world with a scriptable ground block and a mobs list. */
    private static final class GrazeWorld implements MobEntity.WorldQuery {
        final List<MobEntity> mobs = new ArrayList<>();
        String groundBelow = "minecraft:grass_block";

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 4.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public net.zaminmc.torch.util.Identifier blockIdAt(int x, int y, int z) {
            // The feet cell below the flat world's floor (y 4): the
            // horse's own grass consult reads y-1 = 3.
            if (y == 3) {
                return net.zaminmc.torch.util.Identifier.parse(groundBelow);
            }
            return null;
        }

        @Override
        public MobEntity nearestBredHorseInBox(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ,
                int excludeId, double sx, double sy, double sz) {
            MobEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (MobEntity mob : mobs) {
                if (mob.dead() || mob.type() != MobType.HORSE
                        || !mob.horseBred() || mob.entityId() == excludeId) {
                    continue;
                }
                Position p = mob.position();
                double half = mob.type().width / 2.0;
                if (p.x() + half <= minX || p.x() - half >= maxX
                        || p.y() + mob.type().height <= minY || p.y() >= maxY
                        || p.z() + half <= minZ || p.z() - half >= maxZ) {
                    continue;
                }
                double dx = p.x() - sx;
                double dy = p.y() - sy;
                double dz = p.z() - sz;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = mob;
                }
            }
            return best;
        }

        @Override
        public MobEntity nearestAdultOfKindInBox(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ,
                MobType kind, double sx, double sy, double sz) {
            MobEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (MobEntity mob : mobs) {
                if (mob.dead() || mob.type() != kind || mob.isBaby()) {
                    continue;
                }
                Position p = mob.position();
                double half = mob.type().width / 2.0;
                if (p.x() + half <= minX || p.x() - half >= maxX
                        || p.y() + mob.type().height <= minY || p.y() >= maxY
                        || p.z() + half <= minZ || p.z() - half >= maxZ) {
                    continue;
                }
                double dx = p.x() - sx;
                double dy = p.y() - sy;
                double dz = p.z() - sz;
                double distance = dx * dx + dy * dy + dz * dz;
                if (!(distance > bestDistance)) {
                    bestDistance = distance;
                    best = mob;
                }
            }
            return best;
        }
    }

    private static final class Rig {
        final GrazeWorld world = new GrazeWorld();
        final ScriptedRandom random = new ScriptedRandom();
        final MobEntity horse;

        Rig(long unused, String ground) {
            // The ALL-ZERO ladder — every nextInt consult in every tick
            // hits for the whole seat (the mind's own goal dice ahead of
            // the mobTick tail would otherwise consume a short ladder
            // before the graze's roll reads it).
            random.intResults = new int[4096];
            java.util.Arrays.fill(random.intResults, 0);
            world.groundBelow = ground;
            horse = new MobEntity(1, MobType.HORSE,
                    new Position(0.5, 4.0, 0.5), random, world);
            world.mobs.add(horse);
        }
    }

    private static boolean mouthOpen(MobEntity mob) {
        return (mob.horseFlagsRaw() & MobEntity.HORSE_FLAG_MOUTH_OPEN) != 0;
    }

    // ------------------------------------------------ the hay window

    /**
     * THE SPONTANEOUS GRAZE + THE 50-TICK CLOSE (mobTick lines 862-869):
     * an unridden horse over a GRASS block arms the flag-32 graze on the
     * 1-in-300 dice; the window counts UP to its own 50-tick close
     * (never the old 20-tick down-count), the flag dropping exactly on
     * the 51st tick. The all-zero ladder arms the graze on the very
     * first consult.
     */
    @Test
    void theSpontaneousGrazeRunsItsOwnFiftyTickWindow() {
        Rig rig = new Rig(11L, "minecraft:grass_block");
        MobEntity horse = rig.horse;
        assertFalse(horse.eating(), "the graze starts closed");
        horse.tick(true);
        assertTrue(horse.eating(),
                "the dice armed the flag-32 graze (the grass below, no rider)");
        assertTrue(horse.horseAiDead(),
                "the ai-dead consult answers true under the graze (isDead line 811)");
        int window = 0;
        while (horse.eating() && window < 100) {
            horse.tick(true);
            window++;
        }
        assertEquals(50, window,
                "the hay window closes on its own 50-tick clock (line 867)");
        assertFalse(horse.eating(), "the close stands the graze down");
    }

    /**
     * THE MOUNT CLEAR (putPlayerOnBack lines 798-799): the seat stands
     * BOTH the graze and the anger down — an angry horse mounts calm.
     */
    @Test
    void theMountClearsTheGrazeAndTheAnger() {
        Rig rig = new Rig(12L, "minecraft:grass_block");
        MobEntity horse = rig.horse;
        horse.tick(true); // the graze arms (the all-zero ladder)
        assertTrue(horse.eating());
        horse.armHorseAnger(); // setAngry(true)'s own law (line 982)
        assertTrue(horse.rearing());
        assertFalse(horse.eating(), "the anger arm itself stood the graze down");
        // putPlayerOnBack: setEatingHay(false) + setAngry(false).
        horse.setEatingHay(false);
        horse.setHorseAngerDownAtMount();
        assertFalse(horse.eating(), "the mount stands the graze down (line 798)");
        assertFalse(horse.rearing(), "the mount stands the anger down (line 799)");
    }

    /**
     * THE ANGER ARM'S PARK (the isDead override, line 811): the rear
     * parks the mind beside its own graze clear.
     */
    @Test
    void theAngerRearParksTheMind() {
        Rig rig = new Rig(13L, "minecraft:dirt");
        MobEntity horse = rig.horse;
        assertFalse(horse.horseAiDead(), "a free calm horse's mind runs");
        horse.armHorseAnger();
        assertTrue(horse.rearing(), "the flag-64 rear stands");
        assertTrue(horse.horseAiDead(), "the rear parks the mind (line 811)");
    }

    // ------------------------------------------------ the eating flash

    /**
     * THE FLASH AT EVERY SOUND CONSULT (setEating lines 956-960): the
     * hurt consult, the death consult, and the angry consult each open
     * the flag-128 window — the clock to 1, the 30-tick UP-count decay
     * (tick line 889), and the hurt consult's own 1-in-3 anger roll
     * (line 481).
     */
    @Test
    void everySoundConsultOpensTheThirtyTickFlash() {
        Rig rig = new Rig(14L, "minecraft:dirt");
        MobEntity horse = rig.horse;

        // getHurtSound (lines 479-483): the flash + the 1-in-3 anger roll
        // (the all-zero ladder lands the roll).
        String hurt = horse.hurtSound();
        assertEquals("mob.horse.hit", hurt, "the type table's own id");
        assertTrue(mouthOpen(horse), "the hurt consult opened the flash (line 480)");
        assertTrue(horse.rearing(), "the 1-in-3 anger roll landed (line 481)");
        int flash = 0;
        while (mouthOpen(horse) && flash < 100) {
            horse.tick(true);
            flash++;
        }
        assertEquals(30, flash, "the flash decays on the 30-tick up-count (line 889)");
        assertFalse(mouthOpen(horse), "the close brings the flag down");

        // getDeathSound (lines 453-454): the consult opens the flash.
        horse.deathSound();
        assertTrue(mouthOpen(horse), "the death consult opened the flash (line 454)");

        // getAngreType (lines 516-518): the consult opens the flash —
        // the angry-sound consult's own arm (the buck drain's read).
        horse.horseAngrySound();
        assertTrue(mouthOpen(horse), "the angry-sound consult opened the flash (line 517)");
    }

    /**
     * THE AMBIENT CONSULT'S SIDE EFFECTS (getAmbientSound lines 500-504):
     * the flash + the 1-in-10 anger roll GATED ON THE ai-dead consult —
     * a GRAZING horse's ambient consult flashes but never arms the
     * anger (the roll's own {@code !isDead()} gate, line 502).
     */
    @Test
    void theAmbientConsultFlashesAndGatesItsAngerRollOnAiDead() {
        Rig rig = new Rig(15L, "minecraft:grass_block");
        MobEntity horse = rig.horse;
        // The graze arms first (the ai-dead state the gate reads).
        horse.tick(true);
        assertTrue(horse.eating());
        assertFalse(horse.rearing());
        // The cadence's own climb: the delay grows one consult at a time
        // (the reference's growing-hazard shape — {@code nextInt(1000) <
        // delay++}), so the loop consults until the roll lands; the
        // HITTING consult alone runs the side effects (the misses return
        // before the horse block).
        MobEntity.AmbientSound ambient = null;
        for (int i = 0; i < 500 && ambient == null; i++) {
            ambient = horse.ambientSoundDue();
        }
        assertNotNull(ambient, "the cadence roll hit within the climb");
        assertEquals("mob.horse.idle", ambient.name(), "the type table's own idle");
        assertTrue(mouthOpen(horse), "the ambient consult opened the flash (line 501)");
        assertFalse(horse.rearing(),
                "the ai-dead gate refused the anger roll under the graze (line 502)");
        assertTrue(horse.eating(),
                "the refused roll never cleared the graze either (no anger arm)");

        // The ungated arm: a FREE horse's consult with the roll landing
        // arms the anger (the same climb, no ai-dead state standing).
        horse.setEatingHay(false); // stand the graze down (not ai-dead now)
        assertFalse(horse.horseAiDead());
        boolean angered = false;
        for (int i = 0; i < 500 && !angered; i++) {
            horse.ambientSoundDue();
            angered = horse.rearing();
        }
        assertTrue(angered,
                "the free horse's ambient roll armed the anger (line 502's pass arm)");
    }

    /**
     * THE EATING ANIMATION (playEatingAnimation lines 346-352): the
     * flash + the "eating" sound's two-draw pitch jitter
     * (1.0 + (r - r) * 0.2 — the band [0.8, 1.2]).
     */
    @Test
    void theEatingAnimationFlashesAndReturnsThePitchBand() {
        Rig rig = new Rig(16L, "minecraft:dirt");
        MobEntity horse = rig.horse;
        for (int i = 0; i < 50; i++) {
            float pitch = horse.playEatingAnimation();
            assertTrue(pitch >= 0.8f && pitch <= 1.2f,
                    "the pitch jitter lives in the [0.8, 1.2] band: " + pitch);
            assertTrue(mouthOpen(horse), "the flash opened (line 348)");
        }
    }

    // ------------------------------------------------ the mobTick family

    /**
     * THE SLOW REGEN (mobTick lines 858-860): the 1-in-900 dice with
     * deathTicks == 0 heals a half-heart — the all-zero ladder heals
     * every tick up to the cap.
     */
    @Test
    void theSlowRegenHealsAHalfHeartOnTheDice() {
        Rig rig = new Rig(17L, "minecraft:dirt");
        MobEntity horse = rig.horse;
        horse.hurt(10.0f, true);
        float wounded = horse.health();
        assertTrue(wounded < horse.maxHealth());
        horse.tick(true);
        assertEquals(wounded + MobEntity.HORSE_REGEN_HEAL, horse.health(), 1e-6f,
                "the regen dice healed a half-heart (line 859)");
        for (int i = 0; i < 100; i++) {
            horse.tick(true);
        }
        assertEquals(horse.maxHealth(), horse.health(), 1e-6f,
                "the regen caps at the max health");
    }

    /**
     * THE SADDLE'S OWN PARK CONSULT (the isDead override, line 811 —
     * Wave PE-28's repair of the PE-25 carve-out): the ridden SADDLED
     * seat parks the mind; an UNSADDLED ridden horse — TAMED or wild —
     * keeps it (moveRelative's rider arm requires the saddle, never the
     * tame bit).
     */
    @Test
    void theParkConsultReadsTheSaddleNeverTheTameBit() {
        Rig rig = new Rig(18L, "minecraft:dirt");
        MobEntity horse = rig.horse;
        assertFalse(horse.horseAiDead(), "a free horse's mind runs");
        horse.setRider(500);
        assertFalse(horse.horseAiDead(),
                "an UNSADDLED ridden horse keeps its mind (the saddle consult)");
        horse.tame("Alex", "uuid-alex");
        assertFalse(horse.horseAiDead(),
                "the TAMED unsaddled seat still runs (never the tame bit)");
        horse.setSaddled(true);
        assertTrue(horse.horseAiDead(),
                "the SADDLED seat parks the mind (line 811)");
        horse.setRider(-1);
        horse.setSaddled(false);
        assertFalse(horse.horseAiDead(), "the clear returns the mind");
    }

    /**
     * THE HORSE BABY'S PARENT WALK (the FollowParentGoal registration,
     * HorseBaseEntity line 111): a foal claims the nearest adult of its
     * own kind beyond the squared-9 start refusal.
     */
    @Test
    void theHorseFoalFollowsItsParent() {
        Rig rig = new Rig(19L, "minecraft:dirt");
        GrazeWorld world = rig.world;
        MobEntity baby = rig.horse;
        baby.setBreedingAge(-24000);
        ScriptedRandom adultRandom = new ScriptedRandom();
        MobEntity adult = new MobEntity(2, MobType.HORSE,
                new Position(8.5, 4.0, 0.5), adultRandom, world);
        world.mobs.add(adult);
        assertTrue(baby.goalFollowParentCanUse(),
                "the foal claims the parent walk (the horse's own registration)");
        assertTrue(baby.goalFollowParentCanUse(),
                "the window holds while the parent stands in the band");
        baby.setBreedingAge(0);
        assertFalse(baby.goalFollowParentCanUse(),
                "the grown body never claims (the baby gate)");
    }

    /**
     * THE BRED CHILD'S FAMILY WALK (mobTick lines 872-878): a BRED foal
     * walks to the nearest BRED horse beyond the squared-4 band — the
     * flag only a foreign save's read ever sets (the reference's own
     * breeding path never writes it).
     */
    @Test
    void theBredFoalWalksToItsBredKin() {
        Rig rig = new Rig(20L, "minecraft:dirt"); // the graze never claims the gate
        GrazeWorld world = rig.world;
        MobEntity foal = rig.horse;
        foal.setHorseBred(true);
        foal.setBreedingAge(-24000);
        ScriptedRandom kinRandom = new ScriptedRandom();
        MobEntity kin = new MobEntity(2, MobType.HORSE,
                new Position(8.5, 4.0, 0.5), kinRandom, world);
        kin.setHorseBred(true);
        world.mobs.add(kin);

        double beforeX = foal.position().x();
        for (int i = 0; i < 60; i++) {
            foal.tick(true);
        }
        assertTrue(foal.position().x() > beforeX,
                "the bred foal walked toward its bred kin (findPath line 877)");
        assertEquals(MobEntity.Mode.WANDER, foal.mode(),
                "the walk carries the wander mode");
    }
}
