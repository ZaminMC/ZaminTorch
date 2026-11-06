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
 * THE HORSE'S RIDING-WINDOW TAILS' DISCOVERY SEAT (Wave PE-30): the
 * steer model's own family, each law read from its own source
 * (reference/1.8.8 net/minecraft/entity/living/mob/passive/animal/
 * HorseBaseEntity.java):
 *
 * <ul>
 *   <li>THE SADDLE-JUMP CHARGE — setJumpStrength lines 1322-1338: the
 *       saddle head-gate, the negative clamp (no rear), the rear arm
 *       ({@code this.jumping = true} + {@code updateAnger()} — flag 64,
 *       the 20-tick decay, the carve-out's own clear at the anger's
 *       end), and the band {@code 0.4F + 0.4F * strength / 90} with the
 *       90-cap.</li>
 *   <li>THE SPEND — moveRelative lines 1046-1063: the impulse = the
 *       JUMP ATTRIBUTE * the band (never the retired flat stand-in),
 *       the JUMP_BOOST add, the forward lunge's own scale, the
 *       "mob.horse.jump" sound at 0.4F/1.0F on the lunge arm alone,
 *       the charge's clear at the spend, and the grounded tail's pair
 *       (the standing charge's clear + the in-air flag's land).</li>
 *   <li>THE REVERSE DRAG + THE LADDER'S RESET (lines 1033-1036) and
 *       THE ANGRY REFUSAL (lines 1041-1044) with the rear-jump
 *       carve-out.</li>
 *   <li>THE STEP-SOUND LADDER — playStepSound lines 528-548: the first
 *       five crossings' wood, the every-third gallop past five (with
 *       the plain horse's 1-in-10 breathe as the crossing's SECOND
 *       sound), the reverse drag's reset, the donkey/mule refusal, the
 *       unridden wood-or-soft pair, and the snow-layer override.</li>
 *   <li>THE WALK-ANIMATION PAIR (lines 1075-1086) — the steer walk's
 *       own compute over the applied move.</li>
 * </ul>
 */
class HorseSteerParityDiscoveryTest {

    /**
     * A random whose every nextInt(bound) answers bound-1 (never 0):
     * the mouth, the regen, and the graze dice all refuse while the
     * breathe's 1-in-10 never lands — the ladder's sound stream stays
     * pure for the seat.
     */
    private static final class NoBreatheRandom extends Random {
        NoBreatheRandom(long seed) {
            super(seed);
        }

        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    }

    /** The breathe's own scripted dice: nextInt(10) always lands, every
     *  other consult answers bound-1. */
    private static final class AlwaysBreatheRandom extends Random {
        AlwaysBreatheRandom(long seed) {
            super(seed);
        }

        @Override
        public int nextInt(int bound) {
            return bound == 10 ? 0 : bound - 1;
        }
    }

    /** The solver-backed flat world (the StepSoundFamilyTest shape)
     *  with a scriptable ground row and a scriptable above-feet row
     *  (the snow consult's own read): the stone floor at y=63 — the
     *  top at 64.0, the ridden walk's step crossings live. */
    private static final class SteerWorld implements MobEntity.WorldQuery {
        String ground = "minecraft:stone";
        String aboveFeet = "minecraft:air";

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 64.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public net.zaminmc.torch.util.Identifier blockIdAt(int x, int y, int z) {
            // The feet cell below the flat world's floor (y 64): the step
            // walk reads y-1 = 63; the snow consult reads y = 64.
            if (y == 63) {
                return net.zaminmc.torch.util.Identifier.parse(ground);
            }
            if (y == 64) {
                return net.zaminmc.torch.util.Identifier.parse(aboveFeet);
            }
            return null;
        }

        private final net.zaminmc.torch.server.physics.EntityCollisionWorld surface =
                new net.zaminmc.torch.server.physics.EntityCollisionWorld() {
                    @Override
                    public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
                        return y <= 63
                                ? net.zaminmc.torch.server.block.BuiltinBlocks.STONE
                                : net.zaminmc.torch.server.block.BuiltinBlocks.AIR;
                    }

                    @Override
                    public boolean chunkLoaded(int x, int z) {
                        return true;
                    }
                };

        @Override
        public net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld() {
            return surface;
        }
    }

    private static MobEntity riddenHorse(Random random, SteerWorld world) {
        MobEntity mount = new MobEntity(1, MobType.HORSE,
                new Position(0.5, 64.0, 0.5), random, world);
        mount.tame("rider", "uuid-rider");
        mount.applySaddle();
        mount.setRider(100);
        mount.setRiderIdentity("rider", "uuid-rider");
        mount.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        for (int i = 0; i < 40; i++) {
            mount.tick(); // settle the body on the ground
        }
        return mount;
    }

    @Test
    void theChargeBandAndItsGates() {
        SteerWorld world = new SteerWorld();
        MobEntity mount = riddenHorse(new NoBreatheRandom(1), world);
        assertTrue(mount.onGround(), "settled");
        // THE SADDLE HEAD-GATE: an unsaddled seat charges nothing.
        MobEntity bare = new MobEntity(2, MobType.HORSE,
                new Position(0.5, 4.0, 0.5), new NoBreatheRandom(2), world);
        bare.tame("rider", "uuid-rider");
        bare.setRider(100);
        bare.setJumpStrength(100);
        assertEquals(0.0f, bare.horseJumpCharge(), 1e-9f,
                "an unsaddled seat never charges");
        assertFalse(bare.rearing(), "an unsaddled seat never rears");
        // THE BAND (lines 1332-1335): 0.4F + 0.4F * strength / 90, the
        // 90-cap at 1.0.
        int[] strengths = {0, 45, 89, 90, 100};
        float[] bands = {0.4f, 0.6f, 0.4f + 0.4f * 89f / 90f, 1.0f, 1.0f};
        for (int i = 0; i < strengths.length; i++) {
            mount.setJumpStrength(strengths[i]);
            assertEquals(bands[i], mount.horseJumpCharge(), 1e-9f,
                    "the band at strength " + strengths[i]);
            assertTrue(mount.rearing(), "the >= 0 arm rears at " + strengths[i]);
            assertTrue(mount.horseRearJumping(),
                    "the rear-jump flag rides the press at " + strengths[i]);
            mount.tick(); // spend the charge (the decay window rides on)
        }
        // THE NEGATIVE CLAMP (line 1325): strength < 0 clamps to ZERO
        // with NO rear arm — a calm horse stays calm.
        MobEntity calm = riddenHorse(new NoBreatheRandom(3), world);
        calm.setJumpStrength(-5);
        assertEquals(0.4f, calm.horseJumpCharge(), 1e-9f,
                "the negative clamps to the band's floor");
        assertFalse(calm.rearing(), "the negative arm never rears");
        assertFalse(calm.horseRearJumping(), "no rear-jump flag on the clamp");
    }

    @Test
    void theRearsDecayWindowClearsTheCarveOut() {
        SteerWorld world = new SteerWorld();
        MobEntity mount = riddenHorse(new NoBreatheRandom(4), world);
        mount.setJumpStrength(100);
        assertTrue(mount.rearing(), "the press rears");
        assertTrue(mount.horseRearJumping(), "the carve-out flag rides the press");
        mount.tick(); // the spend
        // The 20-tick decay (updateAnger's own clock): both the flag and
        // the carve-out end together (the animation walk's else branch
        // clears the jumping flag at the anger's own end).
        boolean calmed = false;
        for (int i = 0; i < 40 && !calmed; i++) {
            mount.tick();
            calmed = !mount.rearing() && !mount.horseRearJumping();
        }
        assertTrue(calmed, "the rear and its carve-out decay together (~20 ticks)");
    }

    @Test
    void theSpendPaysTheAttributeTimesTheBand() {
        SteerWorld world = new SteerWorld();
        MobEntity mount = riddenHorse(new NoBreatheRandom(5), world);
        // The jump attribute pinned at exactly 1.0 (the Attributes-row
        // override): the impulse reads the band alone.
        mount.setHorseJumpOverride(1.0);
        // THE STILL SPEND: no forward input — no lunge, no jump sound.
        mount.setJumpStrength(45); // band 0.6
        assertEquals(0.6f, mount.horseJumpCharge(), 1e-9f, "the 45 charge");
        mount.steer(0.0f, 0.0f, false);
        mount.tick();
        // The engine's before-move gravity: (0.6 - 0.04) * 0.98 (the
        // float band's own precision).
        assertEquals(0.5488, mount.velocityY(), 1e-6,
                "the impulse is the attribute * the band");
        assertFalse(mount.onGround(), "airborne after the spend");
        assertEquals(0.0f, mount.horseJumpCharge(), 1e-9f,
                "the spend clears the charge");
        assertNull(mount.consumeHorseJumpSound(),
                "the still spend carries no jump sound (the lunge arm alone pays it)");
        // Land the body again.
        for (int i = 0; i < 40 && !mount.onGround(); i++) {
            mount.tick();
        }
        assertTrue(mount.onGround(), "landed");
        // THE LUNGE SPEND: forward input pays the ±0.4 lunge scaled by
        // the charge (yaw 0: the z term alone) and the "mob.horse.jump"
        // sound at 0.4F/1.0F.
        double zBefore = mount.velocityZ();
        mount.setJumpStrength(45);
        mount.steer(0.0f, 1.0f, false);
        mount.tick();
        // The lunge adds 0.4 * cos(0) * 0.6 = 0.24 to velocityZ, then
        // the ground damping (0.6 slipperiness * 0.91) applies.
        assertEquals(zBefore + 0.24 * 0.6 * 0.91, mount.velocityZ(), 1e-6,
                "the forward lunge rides the charge's own scale");
        MobEntity.StagedSound jumpSound = mount.consumeHorseJumpSound();
        assertNotNull(jumpSound, "the lunge arm pays the jump sound");
        assertEquals("mob.horse.jump", jumpSound.name());
        assertEquals(0.4f, jumpSound.volume(), 1e-6f);
        assertEquals(1.0f, jumpSound.pitch(), 1e-6f);
    }

    @Test
    void theAngryRefusalAndTheRearCarveOut() {
        SteerWorld world = new SteerWorld();
        MobEntity mount = riddenHorse(new NoBreatheRandom(6), world);
        // The buck's own anger (no rear-jump flag — armHorseAnger is
        // updateAnger itself): a REARING horse on the ground with no
        // charge refuses the reins whole (lines 1041-1044 — both inputs
        // zero, the engine's A/D turn zeroed with the strafe arm).
        mount.armHorseAnger();
        assertTrue(mount.rearing(), "the buck arms the rear");
        assertFalse(mount.horseRearJumping(), "no carve-out flag on the buck");
        float yawBefore = mount.yaw();
        Position before = mount.position();
        mount.steer(1.0f, 1.0f, false);
        for (int i = 0; i < 10; i++) {
            mount.tick();
        }
        assertEquals(yawBefore, mount.yaw(), 1e-6f,
                "the rearing horse refuses the turn");
        assertEquals(0.0, Math.hypot(mount.position().x() - before.x(),
                mount.position().z() - before.z()), 1e-6,
                "the rearing horse refuses the drive");
        // THE CARVE-OUT: the saddle jump's own press arms the flag —
        // the rearing horse steers (and the 0.4-band hop fires with it).
        mount.setJumpStrength(0); // the floor charge, the rear-jump flag
        mount.steer(1.0f, 1.0f, false);
        mount.tick();
        assertTrue(Math.abs(mount.yaw() - yawBefore) >= MobEntity.RIDDEN_TURN_RATE - 0.001f,
                "the press steers through the rear (the carve-out)");
        assertTrue(mount.velocityY() > 0.2,
                "the floor charge fires the hop with the carve-out");
    }

    @Test
    void theReverseDragScalesTheDrive() {
        SteerWorld world = new SteerWorld();
        MobEntity forward = riddenHorse(new NoBreatheRandom(7), world);
        MobEntity reverse = riddenHorse(new NoBreatheRandom(8), world);
        Position fBefore = forward.position();
        Position rBefore = reverse.position();
        forward.steer(0.0f, 1.0f, false);
        reverse.steer(0.0f, -1.0f, false);
        for (int i = 0; i < 80; i++) {
            forward.tick();
            reverse.tick();
        }
        double fDistance = Math.hypot(forward.position().x() - fBefore.x(),
                forward.position().z() - fBefore.z());
        double rDistance = Math.hypot(reverse.position().x() - rBefore.x(),
                reverse.position().z() - rBefore.z());
        assertTrue(fDistance > 2.0, "the forward drive covers ground: " + fDistance);
        assertTrue(rDistance > 0.5, "the reverse drive still moves: " + rDistance);
        // The 0.25 drag (line 1035): the reverse rides at a quarter of
        // the forward drive (the transient settles inside 80 ticks).
        double ratio = fDistance / rDistance;
        assertTrue(ratio > 3.4 && ratio < 4.6,
                "the reverse drags at the 0.25 scale: ratio " + ratio);
    }

    @Test
    void theGallopLadderAndItsSoundFamily() {
        SteerWorld world = new SteerWorld();
        MobEntity mount = riddenHorse(new NoBreatheRandom(9), world);
        mount.steer(0.0f, 1.0f, false);
        // The crossing stream: the first FIVE crossings the wood sound,
        // then every THIRD crossing the gallop (6, 9, 12, ...) — the
        // in-between crossings (7, 8, 10, 11, ...) stage NOTHING at all
        // (lines 537-547: the > 5 && % 3 != 0 rungs fall through both
        // arms), so the STAGED stream itself is exactly the five woods
        // then the gallops.
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 320; i++) {
            mount.tick();
            MobEntity.StagedSound step = mount.consumeStepSound();
            if (step != null) {
                names.add(step.name());
                assertEquals(1.0f * 0.15f, step.volume(), 1e-6f,
                        "the stone family's volume * 0.15");
                assertEquals(1.0f, step.pitch(), 1e-6f, "the family's pitch");
            }
            assertNull(mount.consumeBreatheSound(),
                    "the scripted dice never breathe (the NoBreathe ladder)");
        }
        assertTrue(names.size() >= 10,
                "the ride staged enough sounds: " + names.size());
        for (int i = 0; i < Math.min(5, names.size()); i++) {
            assertEquals("mob.horse.wood", names.get(i),
                    "staged sound " + (i + 1) + " pays the wood sound (the first five crossings)");
        }
        int gallops = 0;
        for (int i = 5; i < names.size(); i++) {
            assertEquals("mob.horse.gallop", names.get(i),
                    "staged sound " + (i + 1) + " pays the gallop (the silent rungs never stage)");
            gallops++;
        }
        assertTrue(gallops >= 5, "the gallop window is live: " + gallops);
        // THE REVERSE DRAG'S RESET (line 1036): reversing zeroes the
        // ladder — the next crossings pay the wood sound again.
        mount.steer(0.0f, -1.0f, false);
        for (int i = 0; i < 120; i++) {
            mount.tick();
        }
        mount.steer(0.0f, 1.0f, false);
        String next = null;
        for (int i = 0; i < 60 && next == null; i++) {
            mount.tick();
            MobEntity.StagedSound step = mount.consumeStepSound();
            if (step != null) {
                next = step.name();
            }
        }
        assertEquals("mob.horse.wood", next,
                "the reverse drag resets the ladder to the wood sound");
    }

    @Test
    void theBreatheRidesTheGallopAsTheCrossingsSecondSound() {
        SteerWorld world = new SteerWorld();
        MobEntity mount = riddenHorse(new AlwaysBreatheRandom(10), world);
        mount.steer(0.0f, 1.0f, false);
        int gallops = 0;
        int breathes = 0;
        for (int i = 0; i < 320; i++) {
            mount.tick();
            MobEntity.StagedSound step = mount.consumeStepSound();
            MobEntity.StagedSound breathe = mount.consumeBreatheSound();
            if (step != null) {
                if (step.name().equals("mob.horse.gallop")) {
                    gallops++;
                    assertEquals(1.0f * 0.15f, step.volume(), 1e-6f,
                            "the gallop's volume * 0.15");
                }
            }
            if (breathe != null) {
                breathes++;
                assertEquals("mob.horse.breathe", breathe.name());
                // The breathe's own volume law (line 542): the family's
                // volume * 0.6F — TWICE the gallop's own scale.
                assertEquals(1.0f * 0.6f, breathe.volume(), 1e-6f,
                        "the breathe's volume * 0.6");
                assertEquals(1.0f, breathe.pitch(), 1e-6f);
            }
        }
        assertTrue(gallops >= 5, "the gallop window is live: " + gallops);
        assertEquals(gallops, breathes,
                "the scripted 1-in-10 lands the breathe at EVERY gallop (the pair's own crossing)");
    }

    @Test
    void theDonkeyRefusesTheLadderAndTheUnriddenPair() {
        SteerWorld world = new SteerWorld();
        // THE DONKEY'S REFUSAL (line 534's i != 1 && i != 2 gate): a
        // ridden donkey rides the PLAIN pair, never the ladder.
        MobEntity donkey = riddenHorse(new NoBreatheRandom(11), world);
        donkey.setHorseSubtype(MobEntity.HORSE_SUBTYPE_DONKEY);
        donkey.steer(0.0f, 1.0f, false);
        for (int i = 0; i < 200; i++) {
            donkey.tick();
            MobEntity.StagedSound step = donkey.consumeStepSound();
            if (step != null) {
                assertEquals("mob.horse.soft", step.name(),
                        "the ridden donkey rides the plain pair (stone ground)");
            }
            assertNull(donkey.consumeBreatheSound(), "the donkey never breathes");
        }
        // THE UNRIDDEN PAIR (lines 543-547): the WOOD family the wood
        // name, every other family the soft name — both at volume*0.15.
        // The unridden walk rides pushBy (the step walk runs over every
        // applied move): three 0.6 pushes per crossing.
        MobEntity unridden = new MobEntity(3, MobType.HORSE,
                new Position(0.5, 64.0, 0.5), new NoBreatheRandom(12), world);
        unridden.setOnGround(true);
        String first = null;
        for (int i = 0; i < 12 && first == null; i++) {
            unridden.pushBy(0.6, 0.0, 0.0);
            MobEntity.StagedSound step = unridden.consumeStepSound();
            if (step != null) {
                first = step.name();
                assertEquals(1.0f * 0.15f, step.volume(), 1e-6f);
            }
        }
        assertEquals("mob.horse.soft", first,
                "the unridden horse over stone pays the soft name");
        // The wood ground (oak planks): the wood name.
        world.ground = "minecraft:oak_planks";
        MobEntity onWood = new MobEntity(4, MobType.HORSE,
                new Position(0.5, 64.0, 0.5), new NoBreatheRandom(13), world);
        onWood.setOnGround(true);
        String woodName = null;
        for (int i = 0; i < 12 && woodName == null; i++) {
            onWood.pushBy(0.6, 0.0, 0.0);
            MobEntity.StagedSound step = onWood.consumeStepSound();
            if (step != null) {
                woodName = step.name();
            }
        }
        assertEquals("mob.horse.wood", woodName,
                "the unridden horse over the WOOD family pays the wood name");
        // THE SNOW-LAYER OVERRIDE (lines 530-533): the family consult
        // reads the SNOW row (never the ground's own), the horse's names
        // unchanged — the SOFT name at the snow family's scale.
        world.ground = "minecraft:stone";
        world.aboveFeet = "minecraft:snow_layer";
        MobEntity onSnow = new MobEntity(5, MobType.HORSE,
                new Position(0.5, 64.0, 0.5), new NoBreatheRandom(14), world);
        onSnow.setOnGround(true);
        String snowName = null;
        float snowVolume = -1.0f;
        for (int i = 0; i < 12 && snowName == null; i++) {
            onSnow.pushBy(0.6, 0.0, 0.0);
            MobEntity.StagedSound step = onSnow.consumeStepSound();
            if (step != null) {
                snowName = step.name();
                snowVolume = step.volume();
            }
        }
        assertEquals("mob.horse.soft", snowName,
                "the snow override keeps the horse's own names (the soft arm)");
        assertEquals(1.0f * 0.15f, snowVolume, 1e-6f,
                "the SNOW family's own volume scale");
    }

    @Test
    void theWalkAnimationPair() {
        SteerWorld world = new SteerWorld();
        MobEntity mount = riddenHorse(new NoBreatheRandom(15), world);
        mount.steer(0.0f, 1.0f, false);
        float progressBefore = mount.walkAnimationProgress();
        for (int i = 0; i < 100; i++) {
            mount.tick();
        }
        // The pair's own law (lines 1077-1086): f2 = the applied move's
        // horizontal delta * 4 (the canter's 0.115 -> 0.46), the 0.4
        // blend converging on it, the progress accumulating above it.
        double speed = mount.walkAnimationSpeed();
        assertTrue(speed > 0.3 && speed < 0.6,
                "the speed converges on the move * 4: " + speed);
        assertTrue(mount.walkAnimationProgress() > progressBefore + speed * 90,
                "the progress accumulates the speed");
        // The standstill decays the speed toward zero (the 0.4 blend on
        // f2 = 0).
        mount.steer(0.0f, 0.0f, false);
        for (int i = 0; i < 60; i++) {
            mount.tick();
        }
        assertTrue(mount.walkAnimationSpeed() < 0.05,
                "the standstill decays the speed: " + mount.walkAnimationSpeed());
    }
}
