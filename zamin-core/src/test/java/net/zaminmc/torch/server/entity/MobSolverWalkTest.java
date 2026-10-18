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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The physics-solver slice's LIVE walker contract (Slice M3): the mob
 * bodies whose world provides the collision surface run the reference's
 * own swept-box {@code Entity.move} — the axis-by-axis clip with the
 * step-height retry — through the engine's own {@code tickBody}. The
 * stand-ins the point-query walk carried retire one by one: the wall
 * stops the body at its FACE (never the full-stop stand-in), the half
 * slab mounts in ONE step (never the destination-cell test), the
 * fence's 1.5 band catches a body from the cell above (the ledger's
 * own named case), and the fall law rides the actual clipped delta.
 */
class MobSolverWalkTest {

    /**
     * The solver-backed stub world: a block map (the real solidity
     * reads through {@link WorldSolidity}), every chunk loaded, no
     * border — and the collision surface the solver gathers through,
     * exactly the live server's own shape.
     */
    private static class SolverStubWorld implements MobEntity.WorldQuery {
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

        /** The stone floor: a wide slab of cells at y=63 — the top at 64.0 (wide enough for the swim wander). */
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

    /** A cow on the stub floor (the 0.9x1.4 walker, step height 0.5). */
    private static MobEntity cow(SolverStubWorld world, double x, double y, double z) {
        MobEntity mob = new MobEntity(7, MobType.COW, new Position(x, y, z),
                new Random(11), world);
        mob.setOnGround(true);
        return mob;
    }

    @Test
    void theGroundedWalkStaysOnTheFloor() {
        SolverStubWorld world = new SolverStubWorld();
        world.floor();
        MobEntity mob = cow(world, 5.5, 64.0, 5.5);
        for (int i = 0; i < 10; i++) {
            mob.tick(false);
        }
        assertEquals(64.0, mob.position().y(), 1e-9,
                "the grounded body never leaves the floor's ceiling");
        assertTrue(mob.onGround());
    }

    @Test
    void theWallStopsTheBodyAtItsFace() {
        SolverStubWorld world = new SolverStubWorld();
        world.floor();
        // A wall one cell east: cells (7, 64..66, 4..7) — the near face 7.0.
        for (int y = 64; y <= 66; y++) {
            for (int z = 4; z <= 7; z++) {
                world.put(7, y, z, BuiltinBlocks.STONE);
            }
        }
        MobEntity mob = cow(world, 5.5, 64.0, 5.5);
        mob.setVelocity(2.0, 0.0, 0.0);
        mob.tick(false);
        // The body's east face (x + 0.45) stops exactly at 7.0: the feet
        // centre 6.55 — the CLIP, never the full-stop stand-in.
        assertEquals(7.0 - 0.45, mob.position().x(), 1e-9,
                "the walk clips at the wall's near face");
        assertTrue(mob.onGround());
        // A second tick: the velocity was zeroed by the X clip; the body
        // stays put.
        mob.tick(false);
        assertEquals(7.0 - 0.45, mob.position().x(), 1e-9);
    }

    @Test
    void theHalfSlabMountsInOneStep() {
        SolverStubWorld world = new SolverStubWorld();
        world.floor();
        // A bottom slab ahead: cell (7, 64, 5) — the box 7..8, 64..64.5.
        world.put(7, 64, 5, BuiltinBlocks.OAK_SLAB);
        MobEntity mob = cow(world, 5.5, 64.0, 5.5);
        mob.setVelocity(2.0, 0.0, 0.0);
        mob.tick(false);
        // The step-height retry: the body mounts the slab's half (64.5)
        // keeping its eastward motion — never the destination-cell stop.
        assertEquals(64.5, mob.position().y(), 1e-9,
                "the walker steps onto the slab's half in one move");
        assertTrue(mob.position().x() > 6.5,
                "the eastward motion survived the step: " + mob.position().x());
        assertTrue(mob.onGround());
    }

    @Test
    void theFullBlockRefusesTheWalkersStep() {
        SolverStubWorld world = new SolverStubWorld();
        world.floor();
        // A single full block ahead (one cell tall — the 0.5 step never
        // mounts it, unlike the horse's 1.0).
        for (int z = 4; z <= 7; z++) {
            world.put(7, 64, z, BuiltinBlocks.STONE);
        }
        MobEntity mob = cow(world, 5.5, 64.0, 5.5);
        mob.setVelocity(2.0, 0.0, 0.0);
        mob.tick(false);
        assertEquals(64.0, mob.position().y(), 1e-9,
                "the full block never mounts under the 0.5 step");
        assertEquals(7.0 - 0.45, mob.position().x(), 1e-9,
                "the walk clips at the block's face");
    }

    @Test
    void theFencesTallBandCatchesTheBodyFromAbove() {
        SolverStubWorld world = new SolverStubWorld();
        world.floor();
        // A fence on the floor: cell (5, 64, 5) — the box 5.375..5.625,
        // 64.0..65.5 (the 1.5-tall band).
        world.put(5, 64, 5, BuiltinBlocks.FENCE);
        MobEntity mob = cow(world, 5.5, 66.0, 5.5);
        for (int i = 0; i < 20; i++) {
            mob.tick(false);
        }
        assertEquals(65.5, mob.position().y(), 1e-9,
                "the ledger's own named case: the body rests on the fence's 1.5 ceiling");
        assertTrue(mob.onGround());
    }

    @Test
    void theFallWoundRidesTheClippedDelta() {
        SolverStubWorld world = new SolverStubWorld();
        world.floor();
        MobEntity mob = cow(world, 5.5, 68.0, 5.5);
        float health = mob.health();
        for (int i = 0; i < 40; i++) {
            mob.tick(false);
        }
        assertEquals(64.0, mob.position().y(), 1e-9, "the body landed on the floor");
        // The four-block fall: ceil(4 - 3) = 1.0 wound (Slice 92's law).
        assertEquals(health - 1.0f, mob.health(), 1e-6f);
    }

    @Test
    void theGuardianSeabedRestThroughTheSolver() {
        // The guardian's water arm: the stub reads water through inFluid
        // — the swim consult the tick makes (the column reaching the
        // seabed so the water arm holds all the way to the floor).
        SolverStubWorld waterWorld = new SolverStubWorld() {
            @Override
            public boolean inFluid(double x, double y, double z) {
                return y <= 66.0 && y > 63.0; // the water column over the floor
            }
        };
        waterWorld.floor();
        MobEntity guardian = new MobEntity(8, MobType.GUARDIAN,
                new Position(5.5, 65.8, 5.5), new Random(7), waterWorld);
        for (int i = 0; i < 120; i++) {
            guardian.tick(false);
        }
        assertEquals(64.0, guardian.position().y(), 1e-9,
                "the sinking body rests on the seabed through the solver's downward clip"
                        + " (the swim wander drifts the body — the floor spans it)");
        assertTrue(guardian.onGround());
        assertFalse(guardian.dead());
    }
}
