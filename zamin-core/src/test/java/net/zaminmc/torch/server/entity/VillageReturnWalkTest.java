package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.physics.EntityCollisionWorld;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The village-return walk (WanderThroughVillageGoal, VT-7k — the
 * reference's goal 5 over the villager and the guardian): the cache's
 * own gate (PathFinderMobEntity's inverted pair — the unset cache
 * answers isInVillage() TRUE through the -1 short-circuit, so the walk
 * never starts for a village-less body), the candidate pick's
 * half-plane homing law (TargetFinder.getTargetAwayFromPosition over
 * the village center), the walk's life (the path runs to its end), and
 * the guardian's own pin (the self-cache at 16 — the monument anchor).
 */
class VillageReturnWalkTest {

    /** The solver-backed stub world (the MobSolverWalkTest shape). */
    private static class VillageStubWorld implements MobEntity.WorldQuery {
        final Map<Long, BlockType> cells = new HashMap<>();

        void put(int x, int y, int z, BlockType type) {
            cells.put(((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL), type);
        }

        BlockType read(int x, int y, int z) {
            if (y == 0) {
                return BuiltinBlocks.BEDROCK;
            }
            BlockType type = cells.get(
                    ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL));
            return type != null ? type : BuiltinBlocks.AIR;
        }

        /** The stone floor: cells at y=63 — the top at 64.0. */
        void floor() {
            for (int x = -100; x <= 100; x++) {
                for (int z = -100; z <= 100; z++) {
                    put(x, 63, z, BuiltinBlocks.STONE);
                }
            }
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return net.zaminmc.torch.server.block.FluidBlocks.kindOf(read(
                    (int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.floor(z)).identifier()) != null;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        private final EntityCollisionWorld surface = new EntityCollisionWorld() {
            @Override
            public BlockType blockAt(int x, int y, int z) {
                return read(x, y, z);
            }

            @Override
            public boolean chunkLoaded(int x, int z) {
                return true;
            }
        };

        @Override
        public EntityCollisionWorld collisionWorld() {
            return surface;
        }
    }

    /** A villager on the stub floor. */
    private static MobEntity villager(VillageStubWorld world, double x, double y, double z) {
        MobEntity mob = new MobEntity(9, MobType.VILLAGER, new Position(x, y, z),
                new Random(42), world);
        mob.setOnGround(true);
        return mob;
    }

    @Test
    void theUnsetCacheNeverOpensTheGate() {
        VillageStubWorld world = new VillageStubWorld();
        world.floor();
        MobEntity mob = villager(world, 40.5, 64.0, 40.5);
        // No cache: the reference's isInVillage() answers TRUE (the -1
        // short-circuit) — the walk never starts, the stroll owns.
        for (int i = 0; i < 30; i++) {
            mob.tick(false);
        }
        assertFalse(mob.villageReturnWalking(),
                "a village-less villager never walks the return goal");
    }

    @Test
    void theVillagerInsideTheRadiusStrolls() {
        VillageStubWorld world = new VillageStubWorld();
        world.floor();
        MobEntity mob = villager(world, 40.5, 64.0, 40.5);
        // A village centered on the villager with radius 20: inside.
        mob.setVillageCache(40, 64, 40, 20);
        for (int i = 0; i < 30; i++) {
            mob.tick(false);
        }
        assertFalse(mob.villageReturnWalking(),
                "a villager inside its village's radius never walks the return goal");
    }

    @Test
    void theVillagerOutsideWalksHome() {
        VillageStubWorld world = new VillageStubWorld();
        world.floor();
        // The village at the origin (radius 16); the villager 40 blocks
        // east — well outside.
        MobEntity mob = villager(world, 40.5, 64.0, 0.5);
        mob.setVillageCache(0, 64, 0, 16);
        // The walk arms within the first few mind ticks (the candidate
        // pick's 10 tries at the seeded random).
        boolean walking = false;
        for (int i = 0; i < 10 && !walking; i++) {
            mob.tick(false);
            walking = mob.villageReturnWalking();
        }
        assertTrue(walking, "the outside villager armed the return walk");
        // The homing law: over the walk's life the body approaches the
        // village center (the half-plane filter keeps only candidates on
        // the center's side; the nudge pulls further in).
        double startDistance = Math.hypot(mob.position().x() - 0.5, mob.position().z() - 0.5);
        for (int i = 0; i < 200; i++) {
            mob.tick(false);
        }
        double endDistance = Math.hypot(mob.position().x() - 0.5, mob.position().z() - 0.5);
        assertTrue(endDistance < startDistance,
                "the return walk homed toward the village center: start "
                        + startDistance + " end " + endDistance);
        // The walk's own life law: once the path completes, the goal
        // stops (the stroll resumes; the body inside never re-arms).
        assertTrue(endDistance < 40.0,
                "the villager actually moved: " + endDistance);
    }

    @Test
    void theCandidatePickHomesTowardTheCenter() {
        VillageStubWorld world = new VillageStubWorld();
        world.floor();
        // The mob far outside, the village to the WEST: every candidate
        // the pick answers must sit on the center's side of the body
        // (the half-plane filter) — the returned target answers the law.
        MobEntity mob = villager(world, 60.5, 64.0, 0.5);
        mob.setVillageCache(0, 64, 0, 16);
        for (int trial = 0; trial < 25; trial++) {
            Position target = mob.pickVillageReturnTargetForTest();
            if (target == null) {
                continue; // the no-passer arm (the goal waits) is lawful
            }
            assertTrue(target.x() < 60.5,
                    "the candidate sits on the village's side of the body: " + target.x());
        }
    }

    @Test
    void theGuardianPinsItsFirstMindTickAndReturns() {
        VillageStubWorld world = new VillageStubWorld();
        // Water for the guardian's swim: a pool over the floor.
        for (int x = -40; x <= 60; x++) {
            for (int z = -20; z <= 20; z++) {
                world.put(x, 63, z, BuiltinBlocks.STONE);
                for (int y = 64; y <= 68; y++) {
                    world.put(x, y, z,
                            net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                                    net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER));
                }
            }
        }
        MobEntity guardian = new MobEntity(11, MobType.GUARDIAN,
                new Position(0.5, 66.0, 0.5), new Random(7), world);
        // The first mind tick pins the cache at the body's own cell
        // (radius 16, never re-pinned).
        guardian.tick(false);
        assertTrue(guardian.villageCacheSet(),
                "the guardian pinned its village cache at the first mind tick");
        // Push the guardian 30 blocks east — outside the pin's 16.
        guardian.pushBy(30.0, 0.0, 0.0);
        double pushedX = guardian.position().x();
        assertTrue(pushedX > 20.0, "the guardian sits outside its pin: " + pushedX);
        // The pin-return arms: the body steers back toward the pin (the
        // horizontal distance to the pin shrinks over the swim ticks).
        for (int i = 0; i < 120; i++) {
            guardian.tick(false);
        }
        assertTrue(guardian.position().x() < pushedX - 5.0,
                "the guardian swam back toward its pin: " + guardian.position().x());
    }
}
