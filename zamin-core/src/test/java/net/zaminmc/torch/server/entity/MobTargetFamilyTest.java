package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mob-vs-mob target family (Slice 16b — the reference's targetSelector
 * over the wolf and the zombie): the hunts (the untamed wolf's
 * sheep/rabbits, the always-on skeleton feud, the zombie's villagers), the
 * single-target state with the wolf's angry posture, the (amount+1)/2
 * shave on a mob-hit wolf, the revenge arms with the call-others, and the
 * owner arms over the manager's join helper.
 */
class MobTargetFamilyTest {

    /** A flat-world stub carrying the mob-vs-mob world's mob list. */
    private static final class MobWorld implements MobEntity.WorldQuery {
        final List<MobEntity> mobs = new ArrayList<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public MobEntity nearestMobOfKind(double x, double y, double z, double range, MobType kind) {
            MobEntity best = null;
            double bestDistance = range * range;
            for (MobEntity mob : mobs) {
                if (mob.dead() || mob.type() != kind) {
                    continue;
                }
                Position p = mob.position();
                double dx = p.x() - x;
                double dy = p.y() - y;
                double dz = p.z() - z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d <= bestDistance) {
                    bestDistance = d;
                    best = mob;
                }
            }
            return best;
        }
    }

    private static MobEntity spawn(MobWorld world, int id, MobType type, Position at, long seed) {
        MobEntity mob = new MobEntity(id, type, at, new Random(seed), world);
        world.mobs.add(mob);
        return mob;
    }

    @Test
    void theUntamedWolfHuntsSheepAndRabbitsTheTamedOneDoesNot() {
        MobWorld world = new MobWorld();
        MobEntity wolf = spawn(world, 800, MobType.WOLF, new Position(0.5, 8.0, 0.5), 41L);
        MobEntity sheep = spawn(world, 801, MobType.SHEEP, new Position(6.5, 8.0, 0.5), 42L);
        MobEntity rabbit = spawn(world, 802, MobType.RABBIT, new Position(8.5, 8.0, 0.5), 43L);

        // The 1-in-10 cadence: keep ticking until the scan fires (the
        // reference's own nextInt(10) == 0 gate).
        MobEntity found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = wolf.tickMobTargeting();
        }
        assertSame(sheep, found, "the nearer prey (sheep) wins the scan");
        assertTrue(wolf.wolfAngry(), "a wild wolf on a target goes angry");

        // The tamed wolf hunts no sheep (UntamedActiveTargetGoal).
        wolf.tameWolf("Owner");
        wolf.setMobAttackTarget(null);
        found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = wolf.tickMobTargeting();
        }
        assertNull(found, "the tamed wolf hunts neither sheep nor rabbit");
        assertFalse(wolf.wolfAngry());
    }

    @Test
    void theSkeletonFeudOutranksTheSheepHuntAndTheTargetDies() {
        MobWorld world = new MobWorld();
        MobEntity wolf = spawn(world, 810, MobType.WOLF, new Position(0.5, 8.0, 0.5), 44L);
        MobEntity sheep = spawn(world, 811, MobType.SHEEP, new Position(4.5, 8.0, 0.5), 45L);
        MobEntity skeleton = spawn(world, 812, MobType.SKELETON, new Position(3.5, 8.0, 3.5), 46L);

        MobEntity found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = wolf.tickMobTargeting();
        }
        assertSame(skeleton, found, "the feud (targetSelector 5) outranks the sheep (4)");

        // The target's death stands the wolf down.
        skeleton.hurt(1000.0f);
        assertTrue(skeleton.dead());
        assertNull(wolf.tickMobTargeting(), "the dead target clears");
        assertFalse(wolf.wolfAngry(), "the anger follows the target out");

        // The scan takes the sheep next.
        found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = wolf.tickMobTargeting();
        }
        assertSame(sheep, found);
    }

    @Test
    void theSkeletonFleesWolvesWithinSixBlocks() {
        MobWorld world = new MobWorld();
        MobEntity skeleton = spawn(world, 820, MobType.SKELETON, new Position(0.5, 8.0, 0.5), 47L);
        MobEntity wolf = spawn(world, 821, MobType.WOLF, new Position(4.5, 8.0, 0.5), 48L);

        // The flee runs through the mind's own gate (the pending scare is
        // the mind's pre-computed wolf).
        skeleton.tick(true); // the mind + body pass: the flee claims the walk
        assertEquals(MobEntity.Mode.PANIC, skeleton.mode(), "a wolf within 6 blocks owns the movement");
        // The heading runs away from the wolf: the wolf sits at +x, so the
        // walk's -sin(yaw) must drive the body -x — yaw near +90.
        double expectedYaw = Math.toDegrees(Math.atan2(
                -(skeleton.position().x() - wolf.position().x()),
                skeleton.position().z() - wolf.position().z()));
        assertEquals(expectedYaw, skeleton.yaw(), 1e-6, "the flee heads directly away");
        assertTrue(Math.sin(Math.toRadians(skeleton.yaw())) > 0.9,
                "the walk integrates -sin(yaw): the body moves -x, away from the wolf");

        // Out of the 6-block range the flee stops claiming.
        world.mobs.remove(wolf);
        skeleton.tick(true);
        assertEquals(MobEntity.Mode.PANIC != skeleton.mode() || Math.abs(
                Math.sin(Math.toRadians(skeleton.yaw()))) < 0.5, true,
                "no wolf within range: no flee claim");
    }

    @Test
    void theZombieHuntsVillagers() {
        MobWorld world = new MobWorld();
        MobEntity zombie = spawn(world, 830, MobType.ZOMBIE, new Position(0.5, 8.0, 0.5), 50L);
        MobEntity villager = spawn(world, 831, MobType.VILLAGER, new Position(7.5, 8.0, 0.5), 51L);
        MobEntity cow = spawn(world, 832, MobType.COW, new Position(2.5, 8.0, 0.5), 52L);

        MobEntity found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = zombie.tickMobTargeting();
        }
        assertSame(villager, found, "the zombie hunts the villager, not the cow");
    }

    @Test
    void theSingleTargetWriteCarriesTheWolfPosture() {
        MobWorld world = new MobWorld();
        MobEntity wolf = spawn(world, 840, MobType.WOLF, new Position(0.5, 8.0, 0.5), 53L);
        MobEntity sheep = spawn(world, 841, MobType.SHEEP, new Position(3.5, 8.0, 0.5), 54L);

        assertFalse(wolf.wolfAngry());
        wolf.setMobAttackTarget(sheep);
        assertTrue(wolf.wolfAngry(), "a wild wolf on any target goes angry");
        assertSame(sheep, wolf.mobAttackTarget());

        wolf.setMobAttackTarget(null);
        assertFalse(wolf.wolfAngry(), "the target out stands the anger down");

        // A TAMED wolf never goes angry on a target (WolfEntity's own
        // gate: only the untamed flip).
        wolf.tameWolf("Owner");
        wolf.setMobAttackTarget(sheep);
        assertFalse(wolf.wolfAngry());
        assertSame(sheep, wolf.mobAttackTarget());
    }

    @Test
    void theMobHitOnAWolfShavesToHalfRoundedUp() {
        // The shave rides the manager's mob-vs-mob pipeline (hurtByMob —
        // WolfEntity.takeDamage's (amount + 1) / 2 on a mob attacker).
        java.util.function.BiFunction<Integer, Integer, Integer> surface = (x, z) -> 8;
        class CountingLoot implements MobManager.LootSink {
            final List<net.zaminmc.torch.item.ItemStack> drops = new ArrayList<>();
            @Override public void spawnLootDrop(Position position, net.zaminmc.torch.item.ItemStack stack) {
                drops.add(stack);
            }
        }
        CountingLoot loot = new CountingLoot();
        MobManager manager = new MobManager(new MobWorld(), new Random(63L), loot, 900_000, surface);
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5));
        MobEntity wolf = manager.spawnAt(MobType.WOLF, new Position(2.5, 8.0, 0.5));

        assertEquals(8.0f, wolf.health(), 1e-6f);
        assertTrue(manager.hurtByMob(zombie, wolf, 5.0f));
        // (5 + 1) / 2 = 3: the wolf keeps 5 of its 8.
        assertEquals(5.0f, wolf.health(), 1e-6f, "the mob-hit shave rounds up to half");

        // The shave is the wolf's own: a mob hit on a sheep lands whole.
        MobEntity sheep = manager.spawnAt(MobType.SHEEP, new Position(4.5, 8.0, 0.5));
        assertTrue(manager.hurtByMob(zombie, sheep, 5.0f));
        assertEquals(sheep.maxHealth() - 5.0f, sheep.health(), 1e-6f,
                "the sheep takes the whole hit");

        // The revenge arm fired on the zombie (its own attacker).
        assertSame(zombie, wolf.mobAttackTarget(), "the hurt wolf avenges on its attacker");
    }

    @Test
    void theOwnerArmsJoinOnlyTheStandingTamedWolves() {
        // The manager helper's gates at the MobEntity level: the sitting
        // wolf and the stranger's wolf never join.
        MobWorld world = new MobWorld();
        MobEntity standing = spawn(world, 860, MobType.WOLF, new Position(0.5, 8.0, 0.5), 57L);
        MobEntity sitting = spawn(world, 861, MobType.WOLF, new Position(2.5, 8.0, 0.5), 58L);
        MobEntity stranger = spawn(world, 862, MobType.WOLF, new Position(4.5, 8.0, 0.5), 59L);
        MobEntity sheep = spawn(world, 863, MobType.SHEEP, new Position(6.5, 8.0, 0.5), 60L);

        standing.tameWolf("Owner");
        sitting.tameWolf("Owner");
        stranger.tameWolf("Elsewhere");

        // The join helper's own law, expressed at the state level: the
        // standing wolf of the owner takes the target; the sitting one
        // keeps its seat; the stranger's wolf ignores the call.
        // (The full manager walk rides the acceptance lane — the
        // MobManagerTest harness.)
        standing.setMobAttackTarget(sheep);
        assertSame(sheep, standing.mobAttackTarget());
        assertNull(sitting.mobAttackTarget(), "the sitting wolf never joined");
        assertNull(stranger.mobAttackTarget(), "the stranger's wolf never joined");
    }

    @Test
    void theScanStaysInsideTheFollowRange() {
        MobWorld world = new MobWorld();
        MobEntity wolf = spawn(world, 870, MobType.WOLF, new Position(0.5, 8.0, 0.5), 61L);
        MobEntity sheep = spawn(world, 871, MobType.SHEEP, new Position(64.5, 8.0, 0.5), 62L);

        MobEntity found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = wolf.tickMobTargeting();
        }
        assertNull(found, "the prey beyond the 32-block follow range is out of the hunt");
    }
}
