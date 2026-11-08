package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileManager;
import net.zaminmc.torch.server.fx.FxManager;
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
 * THE PROJECTILEATTACKGOAL FAMILY CENSUS (the reference's own goal read
 * whole — ProjectileAttackGoal lines 20-93, every carrier audited): the
 * goal's OWN law over the per-kind constructor triples (the skeleton's
 * 20/60/15, the witch's flat 60 over 10), the carriers' own loose arms
 * (the skeleton's ArrowEntity-ctor form + the distance-scaled damage
 * field, the witch's aim/lead), and the witch's drink-ladder quirk (the
 * reference's own DUPLICATED speed arm).
 *
 * <p>The retired engine form — the flat 60 + nextInt(20) cadence, the
 * 6/12 retreat bands, the immediate first shot, the player-fixed aim —
 * was never the reference's shape; every seat here is RED on that form
 * and GREEN on the landed law.</p>
 */
class ProjectileAttackGoalParityDiscoveryTest {

    /**
     * The flat stub world carrying the goal family's consults: solid
     * ground below y=8, a clear line of sight, and the player seat the
     * hunt scan reads.
     */
    private static final class GoalWorld implements MobEntity.WorldQuery {
        Position playerAt;
        String playerTeam;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public boolean clearLine(Position from, Position to) {
            return true; // the open plain — the seen consult always true
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            if (playerAt == null) {
                return null;
            }
            double dx = playerAt.x() - x;
            double dy = playerAt.y() - y;
            double dz = playerAt.z() - z;
            return dx * dx + dy * dy + dz * dz <= range * range ? playerAt : null;
        }
    }

    private static MobEntity spawn(GoalWorld world, int id, MobType type,
                                   Position at, long seed) {
        return new MobEntity(id, type, at, new Random(seed), world);
    }

    // ------------------------------------------------------------------
    // The goal's own cadence — the skeleton's 20/60/15 triple
    // ------------------------------------------------------------------

    @Test
    void theFirstShotWindowArmsWithoutLoosingAndTheCadenceScales() {
        GoalWorld world = new GoalWorld();
        MobEntity skeleton = spawn(world, 300, MobType.SKELETON,
                new Position(0.5, 8.0, 0.5), 61L);
        MobEntity golem = spawn(world, 301, MobType.IRON_GOLEM,
                new Position(10.5, 8.0, 0.5), 62L);
        skeleton.setMobAttackTarget(golem);

        // THE FIRST-SHOT ARM (ProjectileAttackGoal lines 89-92 — the
        // {@code < 0} arm): the claim's first tick draws NO bow — the
        // counter arms floor(f * (60 - 20) + 20) at the 10-block ratio
        // 10/15 = 0.667 → 46 ticks, the loose on the counter's zero at
        // tick 47.
        skeleton.tick(true);
        assertFalse(skeleton.consumePendingRangedShot(),
                "the claim's first tick never looses (the window arms first)");
        assertEquals(MobEntity.Mode.CHASE, skeleton.mode(),
                "seen under twenty: the goal walks the approach");
        // The seen counter's twentieth tick arms the STAND (lines 72-76):
        // inside the range, the navigation stops — the body frozen at
        // x=1.184 (the walk's own 18 steps), the standing separation
        // 9.316 blocks.
        for (int i = 0; i < 18; i++) {
            skeleton.tick(true);
        }
        assertFalse(skeleton.consumePendingRangedShot(),
                "the 46-tick window still holds");
        skeleton.tick(true); // tick 20: the stand arms
        assertEquals(MobEntity.Mode.STRAFE, skeleton.mode(),
                "twenty consecutive seen ticks inside the range: the stand");
        // The counter's zero at tick 47 (46 decrements past the arm):
        // the loose, then the distance-scaled re-arm — floor(0.621 * 40
        // + 20) = 44 at the standing separation — the SECOND loose at
        // tick 91, the third at 135, never the retired flat 60-79.
        for (int i = 0; i < 26; i++) {
            skeleton.tick(true);
        }
        assertFalse(skeleton.consumePendingRangedShot(),
                "the window holds through tick 46");
        skeleton.tick(true); // tick 47: the counter's zero
        assertTrue(skeleton.consumePendingRangedShot(),
                "the first loose at the counter's zero (tick 47)");
        for (int i = 0; i < 43; i++) {
            skeleton.tick(true);
        }
        assertFalse(skeleton.consumePendingRangedShot(),
                "the re-armed window (44 ticks) holds through tick 90");
        skeleton.tick(true); // tick 91: the second loose
        assertTrue(skeleton.consumePendingRangedShot(),
                "the second loose at tick 91 — the distance-scaled re-arm");
        for (int i = 0; i < 43; i++) {
            skeleton.tick(true);
        }
        assertFalse(skeleton.consumePendingRangedShot(),
                "the second window holds through tick 134");
        skeleton.tick(true); // tick 135: the third loose
        assertTrue(skeleton.consumePendingRangedShot(),
                "the cadence holds periodic at the standing separation");
    }

    @Test
    void theStandBandNeverRetreats() {
        GoalWorld world = new GoalWorld();
        MobEntity skeleton = spawn(world, 310, MobType.SKELETON,
                new Position(0.5, 8.0, 0.5), 63L);
        MobEntity golem = spawn(world, 311, MobType.IRON_GOLEM,
                new Position(2.0, 8.0, 0.5), 64L);
        skeleton.setMobAttackTarget(golem);

        // The reference's goal carries NO retreat band — the retired
        // engine's TOO_CLOSE retreat (the WANDER flip inside 6 blocks)
        // was an invention: an adjacent target still walks (seen < 20)
        // then STANDS; the loose's own window floor(0.1 * 40 + 20) = 24
        // ticks (the 1.5-block ratio clamped at 0.1).
        for (int tick = 1; tick <= 24; tick++) {
            skeleton.tick(true);
            assertFalse(skeleton.mode() == MobEntity.Mode.WANDER,
                    "the goal never retreats (tick " + tick + ")");
            assertFalse(skeleton.consumePendingRangedShot() && tick < 25,
                    "no loose before the counter's zero (tick " + tick + ")");
        }
        assertEquals(MobEntity.Mode.STRAFE, skeleton.mode(),
                "the adjacent stand: twenty seen ticks inside the range");
        skeleton.tick(true); // tick 25: the counter's zero (arm 24)
        assertTrue(skeleton.consumePendingRangedShot(),
                "the adjacent loose at tick 25 (the clamped 0.1 charge)");
        assertFalse(skeleton.consumePendingMeleeAttack(),
                "the melee swing never arms against a mob (the Player-only goal)");
    }

    @Test
    void theRefusedShotReArmsTheWindow() {
        GoalWorld world = new GoalWorld();
        MobEntity skeleton = spawn(world, 320, MobType.SKELETON,
                new Position(0.5, 8.0, 0.5), 65L);
        MobEntity golem = spawn(world, 321, MobType.IRON_GOLEM,
                new Position(10.5, 8.0, 0.5), 66L);
        skeleton.setMobAttackTarget(golem);

        // The window arms at the 10-block ratio → 46; on the zero tick
        // (47) the target stands OUT of range (20 blocks > 15): the
        // loose is REFUSED (lines 80-82's {@code d0 > range²} early
        // return) — the counter left at zero, re-armed by the next
        // tick's {@code < 0} arm at the returned golem's standing
        // separation (9.316 → 44) — the loose at tick 92.
        for (int i = 0; i < 45; i++) {
            skeleton.tick(true);
        }
        assertFalse(skeleton.consumePendingRangedShot(),
                "no loose through the arming window");
        assertTrue(golem.teleportTo(20.5, 8.0, 0.5), "the golem steps out of range");
        skeleton.tick(true); // tick 46: the window's last decrement
        assertFalse(skeleton.consumePendingRangedShot(),
                "the last pre-zero tick never looses");
        skeleton.tick(true); // tick 47: the zero — REFUSED (out of range)
        assertFalse(skeleton.consumePendingRangedShot(),
                "the refused loose (the target beyond the 15-block range)");
        assertTrue(golem.teleportTo(10.5, 8.0, 0.5), "the golem steps back in");
        skeleton.tick(true); // tick 48: the < 0 arm re-arms 44
        // The re-armed window: 44 ticks — the loose at tick 92.
        for (int i = 0; i < 43; i++) {
            skeleton.tick(true);
            assertFalse(skeleton.consumePendingRangedShot(),
                    "the refused re-arm window holds (tick " + (48 + i) + ")");
        }
        skeleton.tick(true); // tick 92: the re-armed zero
        assertTrue(skeleton.consumePendingRangedShot(),
                "the loose at the re-armed window's zero (tick 92)");
    }

    // ------------------------------------------------------------------
    // The witch's own triple — the flat 60 over the 10-block range
    // ------------------------------------------------------------------

    @Test
    void theWitchThrowsOnHerOwnFlatCadence() {
        GoalWorld world = new GoalWorld();
        MobEntity witch = spawn(world, 330, MobType.WITCH,
                new Position(0.5, 8.0, 0.5), 67L);
        MobEntity golem = spawn(world, 331, MobType.IRON_GOLEM,
                new Position(9.5, 8.0, 0.5), 68L);
        witch.setMobAttackTarget(golem);

        // The witch's own triple (WitchEntity line 42: the 4-arg form,
        // min == max 60, range 10): the first arm floor(0.9 * 0 + 60) =
        // 60 — the loose at tick 61, the FLAT cadence whatever the
        // distance, never the skeleton's scaled band.
        for (int tick = 1; tick <= 60; tick++) {
            witch.tick(true);
            assertFalse(witch.consumePendingRangedShot() && tick < 61,
                    "the flat 60-tick window holds (tick " + tick + ")");
        }
        witch.tick(true); // tick 61: the counter's zero — in range (9 < 10)
        assertTrue(witch.consumePendingRangedShot(),
                "the witch's loose at tick 61 (the flat 60 window)");
    }

    @Test
    void theWitchRefusesBeyondHerOwnTenBlocks() {
        GoalWorld world = new GoalWorld();
        MobEntity witch = spawn(world, 340, MobType.WITCH,
                new Position(0.5, 8.0, 0.5), 69L);
        MobEntity golem = spawn(world, 341, MobType.IRON_GOLEM,
                new Position(12.5, 8.0, 0.5), 70L);
        witch.setMobAttackTarget(golem);

        // The witch's own range is TEN (never the skeleton's 15): a
        // 12-block target is out of range — the zero-tick loose is
        // refused every cycle. The golem is RE-PINNED at 12 blocks every
        // tick (the goal's own walk would otherwise close the gap and
        // the loose would rightly fire inside the range).
        boolean anyLoose = false;
        for (int tick = 1; tick <= 200; tick++) {
            witch.tick(true);
            if (witch.consumePendingRangedShot()) {
                anyLoose = true;
                break;
            }
            // Hold the separation at 12 blocks FROM THE WITCH'S CURRENT
            // FEET (the walk accumulates — an absolute pin would let the
            // approach close the gap inside the range).
            golem.teleportTo(witch.position().x() + 12.0, 8.0, 0.5);
        }
        assertFalse(anyLoose,
                "the 12-block target is beyond the witch's own 10 — no loose");
        // Out of range: the goal keeps walking — the mode stays the
        // walking CHASE, the seen-twenty stand never arming outside the
        // witch's own range.
        assertEquals(MobEntity.Mode.CHASE, witch.mode(),
                "out of range: the goal keeps walking (never the stand)");
    }

    // ------------------------------------------------------------------
    // The witch's drink ladder — the reference's own duplicated arm
    // ------------------------------------------------------------------

    /** The scripted dice (the WitchEntityTest convention). */
    private static final class ScriptedRandom extends Random {
        private final float[] scripted;
        private int index;

        ScriptedRandom(float... scripted) {
            this.scripted = scripted;
        }

        @Override
        public float nextFloat() {
            if (index < scripted.length) {
                return scripted[index++];
            }
            return 0.5f;
        }
    }

    @Test
    void theDrinkLaddersDuplicatedSpeedArmDrawsTwice() {
        // THE REFERENCE'S OWN DUPLICATION (WitchEntity.mobTick lines
        // 113-123): the speed arm appears TWICE — the second draw firing
        // only when the first missed. The scripted chain: the look-at's
        // 0.5, the water arm's 0.5 (fail), the fire arm's 0.5 (fail),
        // the heal arm's 0.5 (fail — the roll draws before the health
        // gate), the FIRST speed arm's 0.9 (fail) — then the SECOND
        // arm's 0.1 (pass < 0.25, the far target 13 blocks out, no
        // speed effect): ONLY the duplicated arm can arm this drink. On
        // the retired single-arm engine the sixth draw is the aura roll
        // (0.1 ≥ 0.00075) and NOTHING arms.
        GoalWorld world = new GoalWorld();
        world.playerAt = new Position(13.5, 8.0, 0.5);
        ScriptedRandom random = new ScriptedRandom(
                0.5f, 0.5f, 0.5f, 0.5f, 0.9f, 0.1f);
        MobEntity witch = new MobEntity(350, MobType.WITCH,
                new Position(0.5, 8.0, 0.5), random, world);

        assertFalse(witch.witchDrinking());
        witch.tick(false);
        assertTrue(witch.witchDrinking(),
                "the DUPLICATED arm's own draw armed the speed drink");
    }

    @Test
    void theDrinkingWitchStopsWhole() {
        // THE DRINKING SPEED PENALTY'S OWN FORM (WitchEntity lines
        // 30-32: {@code new AttributeModifier(UUID, "Drinking speed
        // penalty", -0.25, 0)} — operation ZERO, the ADDITIVE form over
        // the registered 0.25 base): the drinking witch's MOVEMENT_SPEED
        // is EXACTLY ZERO — every walk input dies whole for the 33-tick
        // window, the modifier removed at the completion.
        GoalWorld world = new GoalWorld();
        world.playerAt = new Position(13.5, 8.0, 0.5);
        ScriptedRandom random = new ScriptedRandom(
                0.5f, 0.5f, 0.5f, 0.5f, 0.9f, 0.1f);
        MobEntity witch = new MobEntity(351, MobType.WITCH,
                new Position(0.5, 8.0, 0.5), random, world);
        witch.tick(false);
        assertTrue(witch.witchDrinking(), "the drink armed");
        assertEquals(0.0, witch.walkSpeed(), 1e-9,
                "the drinking window zeroes the walk speed whole (0.25 - 0.25)");
        // The completion returns the base.
        for (int i = 0; i < 40; i++) {
            witch.tick(false);
        }
        assertFalse(witch.witchDrinking(), "the window closed");
        assertTrue(witch.walkSpeed() > 0.0,
                "the completion removes the modifier — the base returns");
    }

    @Test
    void theWitchWalksHerSpeedClassTwinsOwnGround() {
        // THE SPEED-CLASS TWIN LAW (WitchEntity.initAttributes line 83 +
        // SkeletonEntity line 66: BOTH register the SAME 0.25
        // MOVEMENT_SPEED base, both walking their ProjectileAttackGoal
        // approach at the goal's own 1.0): over the approach's walking
        // window (the nineteen pre-stand ticks), the witch covers the
        // SAME ground the skeleton covers — the retired 0.03 calibration
        // walked her 21% slower than her twin.
        GoalWorld world = new GoalWorld();
        MobEntity skeleton = spawn(world, 370, MobType.SKELETON,
                new Position(0.5, 8.0, 0.5), 72L);
        MobEntity golemA = spawn(world, 371, MobType.IRON_GOLEM,
                new Position(10.5, 8.0, 0.5), 73L);
        skeleton.setMobAttackTarget(golemA);
        GoalWorld witchWorld = new GoalWorld();
        MobEntity witch = spawn(witchWorld, 372, MobType.WITCH,
                new Position(0.5, 8.0, 0.5), 74L);
        MobEntity golemB = spawn(witchWorld, 373, MobType.IRON_GOLEM,
                new Position(10.5, 8.0, 0.5), 75L);
        witch.setMobAttackTarget(golemB);

        // The nineteen walking ticks (the stand arms at twenty seen).
        for (int i = 0; i < 19; i++) {
            skeleton.tick(true);
            witch.tick(true);
        }
        double skeletonGround = skeleton.position().x() - 0.5;
        double witchGround = witch.position().x() - 0.5;
        assertEquals(skeletonGround, witchGround, 1e-9,
                "the speed-class twins cover the same ground (skeleton "
                        + skeletonGround + " vs witch " + witchGround + ")");
    }

    // ------------------------------------------------------------------
    // The skeleton's loose — the ArrowEntity-ctor damage field
    // ------------------------------------------------------------------

    /** Records the wounds the combat sink received. */
    private static final class RecordingCombat implements ProjectileManager.CombatSink {
        final List<Float> damages = new ArrayList<>();

        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            damages.add(damage);
            return true;
        }

        @Override
        public void playerHit(net.zaminmc.torch.server.player.PlayerSession player,
                              float damage, double kbYaw) {
            damages.add(damage);
        }

        @Override
        public void chickenHatch(Position position, int dimension) {
        }
    }

    /** The fixed mob target the flight path always lands inside. */
    private static final class RiggedHits implements ProjectileManager.HitResolver {
        MobEntity mob;

        @Override
        public MobEntity mobAt(Position point, int dimension) {
            return mob;
        }

        @Override
        public net.zaminmc.torch.server.player.PlayerSession playerAt(
                Position point, int dimension) {
            return null;
        }
    }

    @Test
    void theSkeletonsArrowDamageRidesTheDistanceScaledField() {
        // THE DAMAGE FIELD (SkeletonEntity.doRangedAttack line 256 —
        // ArrowEntity's own {@code damage} replacing the 2.0 base): the
        // hit wound reads ceil(hitSpeed * damageField) with the field
        // the distance-scaled charge form — a CLOSE charge (0.1 → 0.2
        // + difficulty 0.22) wounds FAR softer than the retired flat
        // ceil(speed * 2.0) ≈ 4.
        RiggedHits hits = new RiggedHits();
        RecordingCombat combat = new RecordingCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, new FxManager(),
                new Random(3), 2_000);
        GoalWorld world = new GoalWorld();
        MobEntity zombie = spawn(world, 360, MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5), 71L);
        hits.mob = zombie;

        // A close-charge arrow (the field the skeleton's loose would
        // carry at a 1.5-block target on NORMAL): 0.1 * 2 + 0.22 =
        // 0.42 → ceil(speed * 0.42) — a glancing wound.
        ProjectileEntity close = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0.5, 8.5, 0.5), 0, -20, 1.6, 0);
        assertNotNull(close);
        close.setDamageOverride(0.1 * 2.0 + (0.0 * 0.25 + 2 * 0.11));
        manager.tick(List.of());
        for (int i = 0; i < 10 && combat.damages.isEmpty(); i++) {
            manager.tick(List.of());
        }
        assertFalse(combat.damages.isEmpty(), "the arrow reached the rigged mob");
        float closeWound = combat.damages.get(0);
        assertTrue(closeWound <= 2.0f,
                "the distance-scaled field wounds soft at the close charge ("
                        + closeWound + ")");

        // A full-charge arrow (the 15-block ratio 1.0): 1.0 * 2 + 0.22 =
        // 2.22 → ceil(speed * 2.22) — the heavier wound, still the
        // FIELD's own arithmetic and never the retired flat base.
        combat.damages.clear();
        ProjectileEntity far = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0.5, 8.5, 0.5), 0, -20, 1.6, 0);
        assertNotNull(far);
        far.setDamageOverride(1.0 * 2.0 + (0.0 * 0.25 + 2 * 0.11));
        manager.tick(List.of());
        for (int i = 0; i < 10 && combat.damages.isEmpty(); i++) {
            manager.tick(List.of());
        }
        assertFalse(combat.damages.isEmpty(), "the second arrow reached the mob");
        float farWound = combat.damages.get(0);
        assertTrue(farWound > closeWound,
                "the full charge wounds heavier than the close charge ("
                        + farWound + " > " + closeWound + ")");
        // And the PLAYER bow's base stands untouched: no override →
        // ceil(speed * 2.0) — the 4-5 band.
        combat.damages.clear();
        ProjectileEntity playerArrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0.5, 8.5, 0.5), 0, -20, 1.6, 0);
        assertNotNull(playerArrow);
        manager.tick(List.of());
        for (int i = 0; i < 10 && combat.damages.isEmpty(); i++) {
            manager.tick(List.of());
        }
        assertFalse(combat.damages.isEmpty(), "the third arrow reached the mob");
        assertTrue(combat.damages.get(0) >= 3.0f,
                "the player bow's 2.0 base stands (no override): "
                        + combat.damages.get(0));
        assertNull(manager.byId(close.entityId()), "the first arrow left the world");
    }
}
