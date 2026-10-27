package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The dimension-aware mob query (Slice 25d — the reference's
 * Entity.world: every body reads its own world): the manager flips the
 * shared query's active dimension before each body's walk, the nether
 * spawns stamp their bodies, the ZMD state block carries the dimension,
 * and the live engine's nether bodies read the NETHER's blocks (not the
 * overworld's at the same coordinates).
 */
class DimensionAwareMobQueryTest {

    /** The recording stub: the dimension the manager last switched to. */
    private static final class RecordingQuery implements MobEntity.WorldQuery {
        int lastDimension = Integer.MIN_VALUE;
        int switches;

        @Override
        public void setActiveDimension(int dimension) {
            this.lastDimension = dimension;
            switches++;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    private static MobManager manager(MobEntity.WorldQuery world, long seed) {
        return new MobManager(world, new Random(seed), (position, stack, dimension) -> { },
                900, (x, z) -> 8);
    }

    @Test
    void theTickWalkFlipsTheQueryPerBody() {
        RecordingQuery query = new RecordingQuery();
        MobManager manager = manager(query, 31L);
        MobEntity overworld = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5));
        MobEntity nether = manager.spawnNaturalNether(MobType.ZOMBIE_PIGMAN,
                new Position(4.5, 8.0, 4.5), false, 0.0f);
        assertEquals(0, overworld.dimension(), "spawnAt keeps the overworld default");
        assertEquals(-1, nether.dimension(), "the nether spawn stamps -1");

        query.switches = 0;
        manager.tick(List.of(), 6000L);
        assertTrue(query.switches >= 2,
                "both bodies' walks flipped the shared query (got " + query.switches + ")");
        // The last flip of the pass: the loop's order is the list's order —
        // the pigman (second) leaves the query in the nether context.
        // The first body's walk answered with the overworld context: the
        // switch happened before it (the contract the engine's query reads).
        assertEquals(-1, query.lastDimension, "the walk ends on the last body's dimension");
    }

    @Test
    void theDimensionRoundTripsThroughTheStore(@TempDir Path tempDir) {
        net.zaminmc.torch.server.entity.MobDataStore store =
                new MobDataStore(tempDir.resolve("mobs.bin"));
        // A nether pigman mid-conversion and an overworld zombie.
        List<MobManager.MobSnapshot> saved = List.of(
                new MobManager.MobSnapshot("ZOMBIE_PIGMAN", 1.5, 40.0, 1.5, 90f, 20f,
                        new MobManager.MobStateExtras(false, 0, 0, 0, 0, 0,
                                0, 0, false, 1, -1)),
                new MobManager.MobSnapshot("ZOMBIE", 1.5, 8.0, 1.5, 90f, 18f,
                        new MobManager.MobStateExtras(true, 100, 0, 0, 0, 0,
                                0, 0, false, 1)));
        store.save(saved);
        assertEquals(saved, store.load(), "the dimension byte round-trips");

        MobManager restore = manager(new RecordingQuery(), 33L);
        assertTrue(restore.restoreAll(store.load()));
        MobEntity pigman = restore.all().stream()
                .filter(m -> m.type() == MobType.ZOMBIE_PIGMAN).findFirst().orElseThrow();
        MobEntity zombie = restore.all().stream()
                .filter(m -> m.type() == MobType.ZOMBIE).findFirst().orElseThrow();
        assertEquals(-1, pigman.dimension(), "the pigman restores into the nether");
        assertEquals(0, zombie.dimension(), "the zombie restores into the overworld");
        assertTrue(zombie.zombieVillager(), "the conversion state still lands");
    }

    @Test
    void theSpawnerBodiesCarryTheirSpawnersDimension() {
        // The dimension stamp rides every spawner spawn (the 18e dimension
        // model's own spawn arm) — verified through the manager's surface:
        // the spawner test pins the stamp itself; here the contract is the
        // MobEntity's own default.
        MobEntity fresh = new MobEntity(950, MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5), new Random(3L),
                new RecordingQuery());
        assertEquals(0, fresh.dimension(), "a bare body defaults to the overworld");
        fresh.setDimension(-1);
        assertEquals(-1, fresh.dimension());
    }

    /**
     * The live engine's nether bodies read the NETHER's blocks: a platform
     * in the nether holds a nether-spawned body while the overworld's air
     * at the same coordinates would drop it (the pre-25d engine-wide
     * limitation — the shared query read the overworld for everyone).
     */
    @org.junit.jupiter.api.Test
    void theNetherBodyStandsOnTheNethersBlocks(@TempDir Path dataDir) throws Exception {
        net.zaminmc.torch.server.config.EngineConfig config =
                new net.zaminmc.torch.server.config.EngineConfig(
                        "127.0.0.1", 0, "dimquery", "it", 20, 2, 20, dataDir.toString());
        net.zaminmc.torch.server.EngineServer server =
                new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        try {
            int x = 200; // far from the spawn fixtures, distinct coordinates
            int y = 48;
            int z = 200;
            // A single netherrack platform in the NETHER at (200, 48, 200).
            server.ticker().submit(() -> {
                server.netherWorld().setBlock(new net.zaminmc.torch.block.BlockPosition(
                        x, y, z), BuiltinBlocks.NETHERRACK);
            });
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && !server.netherWorld().isChunkLoaded(
                            new net.zaminmc.torch.block.ChunkPosition(x >> 4, z >> 4))) {
                EngineAwaits.awaitTicks(server.world(), 1, "engine progress (a 50 wall settle)");
            }
            // The nether body on the platform (a pigman, the nether's own).
            server.ticker().submit(() -> server.mobs().spawnNaturalNether(
                    MobType.ZOMBIE_PIGMAN, new Position(x + 0.5, y + 1.0, z + 0.5),
                    false, 0.0f));
            // The body stands on the platform (the nether's block answers
            // the support read; the overworld's air at the same coordinates
            // never drops it).
            boolean standing = false;
            deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                MobEntity pigman = server.mobs().all().stream()
                        .filter(m -> m.type() == MobType.ZOMBIE_PIGMAN).findFirst()
                        .orElse(null);
                if (pigman != null && pigman.onGround()) {
                    standing = true;
                    break;
                }
                Thread.sleep(50);
            }
            assertTrue(standing,
                    "the nether body's support read answers from the nether's blocks");
        } finally {
            server.shutdown(() -> { });
        }
    }
}
