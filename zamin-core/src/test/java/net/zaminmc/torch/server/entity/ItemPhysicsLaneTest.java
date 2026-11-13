package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.concurrent.DomainWorkerPool;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.physics.EntityCollisionWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE P6-IL — THE ITEM-BODIES' PHYSICS COMPUTE LANE'S LOCKSTEP SEAT
 * (the Phase-6 entities-family lane, §22 Phase 6's evidence gate): the
 * armed lane (the movement solves on the worker pool over the world
 * frozen at the dispatch-and-join barrier, every other arm on the tick
 * lane in body order) and the unarmed sequential walk must produce
 * IDENTICAL states — per-body whole state (position, velocities, the
 * ground flag, the clocks), the wire-visible listener sequence, and the
 * lava fizz band's own sound sequence (the SHARED random stream's
 * draws, the strongest divergence witness the walk carries: any
 * reorder, drop, or duplication of a fizz draw would shift the stream
 * and every later draw would follow).
 *
 * <p>THE LOCKSTEP DESIGN (the random-tick lane test's own shape): two
 * identically-built managers over identically-built ground scenes,
 * same seeded randoms, same spawn order — driven tick by tick, one
 * sequential, one armed over a real worker pool. STRICT equality of
 * the full state, never a convergence fixed point.</p>
 */
class ItemPhysicsLaneTest {

    /**
     * The solver-backed ground: a block map with the collision surface
     * (the lane only rides the solver arm), the lava pool (the fizz
     * band's draws), the slime pad (the vertical-clip consult's
     * override), and the ice sheet (the drag's slipperiness band) —
     * every world-consulting arm of the walk exercised.
     */
    private static final class SceneGround implements ItemEntity.Ground {
        final Map<Long, BlockType> cells = new HashMap<>();
        int activeDimension;

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

        @Override
        public void setActiveDimension(int dimension) {
            this.activeDimension = dimension;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        @Override
        public boolean isSolidAt(double x, double y, double z) {
            return isSolid(x, y, z);
        }

        @Override
        public double supportY(double x, double y, double z) {
            return isSolid(x, y, z) ? Math.floor(y) + 1.0 : Double.NEGATIVE_INFINITY;
        }

        /** The fizz gate's own consult (the reference's lava material read). */
        @Override
        public boolean lavaAtCell(double centerX, double feetY, double centerZ) {
            return net.zaminmc.torch.server.block.FluidBlocks.kindOf(
                    read((int) Math.floor(centerX), (int) Math.floor(feetY),
                            (int) Math.floor(centerZ)).identifier())
                    == net.zaminmc.torch.server.block.FluidBlocks.Kind.LAVA;
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

    /** The fizz band's recording sink (the stream-order witness). */
    private static final class RecordingSounds implements ItemEntity.Sounds {
        final List<String> events = new ArrayList<>();

        @Override
        public void sound(Position at, String name, float volume, float pitch,
                int dimension) {
            events.add(String.format(java.util.Locale.ROOT,
                    "%.3f,%.3f,%.3f|%s|%.2f|%.3f|%d",
                    at.x(), at.y(), at.z(), name, volume, pitch, dimension));
        }
    }

    /** The wire-visible publication recorder (the listener sequence witness). */
    private static final class RecordingListener implements ItemEntityManager.Listener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onItemSpawned(ItemEntity entity) {
        }

        @Override
        public void onItemMoved(ItemEntity entity) {
            events.add("moved:" + entity.entityId());
        }

        @Override
        public void onItemCollected(ItemEntity entity,
                net.zaminmc.torch.server.player.PlayerSession collector, int count) {
        }

        @Override
        public void onItemStackChanged(ItemEntity entity) {
        }

        @Override
        public void onItemRemoved(ItemEntity entity, String reason) {
        }
    }

    /**
     * THE LOCKSTEP LAW: 200 bodies (over the lane's 128 dispatch floor)
     * scattered over the scene's terrain — falls onto stone, slides
     * over ice, lands in the lava pool, bounces on the slime pad —
     * driven 300 ticks, one manager sequential, one armed over a real
     * 3-worker pool. The full state, the listener sequence, and the
     * fizz sound sequence must match EXACTLY, while the drives moved
     * bodies and popped fizz (non-trivial — a vacuous equality of two
     * untouched scenes proves nothing).
     */
    @Test
    void theArmedLaneAndTheSequentialWalkAgreeInLockstep() {
        SceneGround sequentialGround = buildScene();
        SceneGround parallelGround = buildScene();
        RecordingSounds sequentialSounds = new RecordingSounds();
        RecordingSounds parallelSounds = new RecordingSounds();
        ItemEntityManager sequential = new ItemEntityManager(
                sequentialGround, new Random(4242), 1000, sequentialSounds);
        ItemEntityManager parallel = new ItemEntityManager(
                parallelGround, new Random(4242), 1000, parallelSounds);
        RecordingListener sequentialListener = new RecordingListener();
        RecordingListener parallelListener = new RecordingListener();
        sequential.addListener(sequentialListener);
        parallel.addListener(parallelListener);

        // The identical spawn set (the same seeded scatter, the same
        // order — the spawn draws ride each manager's own stream).
        Random scatter = new Random(777);
        for (int i = 0; i < 200; i++) {
            double x = -24.0 + scatter.nextDouble() * 48.0;
            double z = -24.0 + scatter.nextDouble() * 48.0;
            double y = 66.0 + scatter.nextDouble() * 14.0;
            ItemStack stack = new ItemStack(BuiltinItems.DIAMOND, 1, 0, null, Map.of());
            Position at = new Position(x, y, z);
            sequential.spawnRestored(at, stack);
            parallel.spawnRestored(at, stack);
        }
        assertEquals(200, sequential.size());
        assertEquals(200, parallel.size());

        DomainWorkerPool pool = new DomainWorkerPool(3);
        try {
            parallel.setLanePool(pool);
            for (int tick = 0; tick < 300; tick++) {
                sequential.tick(List.of());
                parallel.tick(List.of());
            }
        } finally {
            pool.shutdown();
        }

        // THE ENGAGEMENT: the armed walk rode the pool (300 dispatches),
        // the sequential walk never did.
        assertTrue(parallel.parallelDispatchTicks() >= 290,
                "the armed walk rode the pool dispatch (got "
                        + parallel.parallelDispatchTicks() + ")");
        assertEquals(0, sequential.parallelDispatchTicks(),
                "the unarmed walk stayed sequential");

        // THE FULL STATE: every body's whole state identical.
        List<ItemEntity> sequentialBodies = sequential.all();
        List<ItemEntity> parallelBodies = parallel.all();
        assertEquals(sequentialBodies.size(), parallelBodies.size(),
                "the two walks kept the same population");
        for (int i = 0; i < sequentialBodies.size(); i++) {
            ItemEntity a = sequentialBodies.get(i);
            ItemEntity b = parallelBodies.get(i);
            assertEquals(a.entityId(), b.entityId(), "body " + i + "'s id");
            assertEquals(a.position().x(), b.position().x(), 0.0, "body " + i + "'s x");
            assertEquals(a.position().y(), b.position().y(), 0.0, "body " + i + "'s y");
            assertEquals(a.position().z(), b.position().z(), 0.0, "body " + i + "'s z");
            assertEquals(a.velocityX(), b.velocityX(), 0.0, "body " + i + "'s vx");
            assertEquals(a.velocityY(), b.velocityY(), 0.0, "body " + i + "'s vy");
            assertEquals(a.velocityZ(), b.velocityZ(), 0.0, "body " + i + "'s vz");
            assertEquals(a.onGround(), b.onGround(), "body " + i + "'s ground flag");
            assertEquals(a.stack().count(), b.stack().count(), "body " + i + "'s stack");
        }

        // THE PUBLICATION SEQUENCE and THE FIZZ STREAM: identical order,
        // identical payloads.
        assertEquals(sequentialListener.events, parallelListener.events,
                "the wire-visible listener sequence matches");
        assertEquals(sequentialSounds.events, parallelSounds.events,
                "the fizz band's sound sequence matches (the shared stream's order)");
        assertTrue(!sequentialSounds.events.isEmpty(),
                "non-trivial: the lava pool popped fizz in the scene");
        // NON-TRIVIAL: bodies actually moved (the lane's compute flowed).
        assertTrue(sequentialBodies.stream().anyMatch(
                        body -> body.position().y() < 70.0),
                "non-trivial: bodies fell through the scene");
        assertTrue(parallelBodies.stream().anyMatch(
                        body -> body.position().y() < 70.0),
                "non-trivial: the armed lane's bodies fell too");
    }

    /**
     * THE FLOOR GATE: under the dispatch floor the armed manager stays
     * sequential — the lane never pays a dispatch for a small world.
     */
    @Test
    void underTheFloorTheLaneStaysSequential() {
        SceneGround ground = buildScene();
        ItemEntityManager under = new ItemEntityManager(
                ground, new Random(1), 1000, null);
        // A handful of bodies — under the 128 floor.
        for (int i = 0; i < 20; i++) {
            under.spawnRestored(new Position(i * 0.5, 70.0, 0.0),
                    new ItemStack(BuiltinItems.DIAMOND, 1, 0, null, Map.of()));
        }
        DomainWorkerPool pool = new DomainWorkerPool(2);
        try {
            under.setLanePool(pool);
            for (int i = 0; i < 50; i++) {
                under.tick(List.of());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(0, under.parallelDispatchTicks(),
                "the under-floor walk never dispatched");
    }

    /**
     * THE PURE SOLVE'S OWN REPLAY LAW (the per-body unit the lockstep
     * drives end to end): two IDENTICAL bodies, one ticking through the
     * inline arm, one replaying the precomputed pure solve every tick —
     * the whole state must match after every tick (the gravity's own
     * arithmetic rides the intent, never applied twice: a wrong intent
     * vertical would diverge the trajectories on the very first fall).
     */
    @Test
    void thePureSolveReplaysTheInlineArmPerTick() {
        SceneGround ground = buildScene();
        for (int seed = 0; seed < 60; seed++) {
            Random random = new Random(seed * 31L + 5L);
            Position start = new Position(-20.0 + random.nextDouble() * 40.0,
                    66.0 + random.nextDouble() * 12.0,
                    -20.0 + random.nextDouble() * 40.0);
            ItemStack stack = new ItemStack(BuiltinItems.DIAMOND, 1, 0, null, Map.of());
            ItemEntity inline = new ItemEntity(1, start, stack, 0);
            ItemEntity replayed = new ItemEntity(1, start, stack, 0);
            for (int tick = 0; tick < 40; tick++) {
                // The inline arm — the walk as it always ran.
                inline.tick(ground);
                // The lane's split — the intent captured, the pure solve
                // computed, the tick replaying it.
                ItemEntity.SolveResult solved = ItemEntity.solveMovement(
                        ground.collisionWorld(), replayed.solveIntent());
                replayed.tick(ground, null, null, solved);
                assertEquals(inline.position().x(), replayed.position().x(), 0.0,
                        "seed " + seed + " tick " + tick + "'s x");
                assertEquals(inline.position().y(), replayed.position().y(), 0.0,
                        "seed " + seed + " tick " + tick + "'s y");
                assertEquals(inline.position().z(), replayed.position().z(), 0.0,
                        "seed " + seed + " tick " + tick + "'s z");
                assertEquals(inline.velocityX(), replayed.velocityX(), 0.0,
                        "seed " + seed + " tick " + tick + "'s vx");
                assertEquals(inline.velocityY(), replayed.velocityY(), 0.0,
                        "seed " + seed + " tick " + tick + "'s vy");
                assertEquals(inline.velocityZ(), replayed.velocityZ(), 0.0,
                        "seed " + seed + " tick " + tick + "'s vz");
                assertEquals(inline.onGround(), replayed.onGround(),
                        "seed " + seed + " tick " + tick + "'s ground flag");
            }
        }
    }

    // ------------------------------------------------------------------
    // The rig
    // ------------------------------------------------------------------

    /**
     * The scene: a stone shelf at y=64 under the spawn band, an ice
     * sheet patch (the drag's 0.98 band), a lava pool (the fizz), and a
     * slime pad (the vertical-clip flip) — every consult the walk makes.
     */
    private static SceneGround buildScene() {
        SceneGround ground = new SceneGround();
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                ground.put(x, 64, z, BuiltinBlocks.STONE);
            }
        }
        // The ice sheet: the north-west quadrant.
        for (int x = -32; x <= -12; x++) {
            for (int z = -32; z <= -12; z++) {
                ground.put(x, 65, z, BuiltinBlocks.ICE);
            }
        }
        // The lava pool: the south-east quadrant (a cell above the floor).
        for (int x = 8; x <= 24; x++) {
            for (int z = 8; z <= 24; z++) {
                ground.put(x, 65, z, net.zaminmc.torch.server.block.FluidBlocks.typeOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.LAVA,
                        net.zaminmc.torch.server.block.FluidBlocks.Form.SOURCE, 0));
            }
        }
        // The slime pad: a 4x4 patch mid-field.
        for (int x = -6; x <= -3; x++) {
            for (int z = 2; z <= 5; z++) {
                ground.put(x, 65, z, BuiltinBlocks.SLIME_BLOCK);
            }
        }
        return ground;
    }
}
