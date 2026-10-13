package net.zaminmc.torch.server.spawner;

import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mob spawner walk (Slice 12g — the reference port of
 * block/spawner/MobSpawner.java): the player gate, the delay band, the
 * typed records from the structures and the snapshot surface. Slice 18e:
 * the dimension model (a nether spawner survives the cleanup reading its
 * OWN world) and the wire surface (the immutable per-chunk tile snapshot
 * + the reference's own two broadcasts).
 */
class SpawnerManagerTest {

    /** The recording player surface (one controllable player). */
    private static final class TestPlayers implements SpawnerManager.Players {
        final List<Position> positions = new ArrayList<>();

        @Override
        public List<Position> positions() {
            return positions;
        }
    }

    /** A flat-air world query (every spawn cell clears). */
    private static final net.zaminmc.torch.server.entity.MobEntity.WorldQuery AIR_WORLD =
            new net.zaminmc.torch.server.entity.MobEntity.WorldQuery() {
                @Override
                public boolean isSolid(double x, double y, double z) {
                    return false;
                }

                @Override
                public Position nearestPlayer(double x, double y, double z, double range) {
                    return null;
                }
            };

    /** The minimal mob manager for the walk (the flat-air world). */
    private static net.zaminmc.torch.server.entity.MobManager mobs() {
        return new net.zaminmc.torch.server.entity.MobManager(
                AIR_WORLD, new Random(7L), (position, stack) -> {
                }, 1, (x, z) -> 64);
    }

    @Test
    void theSpawnerWaitsForThePlayerRange() {
        TestPlayers players = new TestPlayers();
        players.positions.add(new Position(1000.0, 64.0, 1000.0));
        SpawnerManager spawners = new SpawnerManager(new Random(1L), players,
                mobs(), dimension -> null);
        net.zaminmc.torch.block.BlockPosition pos =
                new net.zaminmc.torch.block.BlockPosition(0, 64, 0);
        spawners.getOrCreate(0, pos).setType(MobType.SILVERFISH);
        // The 16-block gate: the far player never triggers the walk —
        // the delay stays at its initial value.
        spawners.tick();
        assertEquals(20, spawners.peek(pos).delay(),
                "the far player leaves the spawner dormant");
    }

    @Test
    void theNearPlayerWalkCountsTheDelayDown() {
        TestPlayers players = new TestPlayers();
        players.positions.add(new Position(8.5, 64.0, 8.5));
        SpawnerManager spawners = new SpawnerManager(new Random(1L), players,
                mobs(), dimension -> null);
        net.zaminmc.torch.block.BlockPosition pos =
                new net.zaminmc.torch.block.BlockPosition(0, 64, 0);
        SpawnerManager.MobSpawnerBlockEntity spawner = spawners.getOrCreate(0, pos);
        spawner.setType(MobType.SILVERFISH);
        spawner.setDelay(3);
        spawners.tick();
        assertEquals(2, spawner.delay(), "the near player counts the delay down");
    }

    @Test
    void theTypedRecordSurvivesTheSnapshot() {
        SpawnerManager spawners = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> null);
        net.zaminmc.torch.block.BlockPosition pos =
                new net.zaminmc.torch.block.BlockPosition(10, 20, 30);
        SpawnerManager.MobSpawnerBlockEntity spawner = spawners.getOrCreate(0, pos);
        spawner.setType(MobType.BLAZE);
        assertEquals(MobType.BLAZE, spawners.snapshot().get(pos).type(),
                "the typed state snapshots for the save");
        assertEquals(20, spawners.snapshot().get(pos).delay(),
                "the reference's initial delay rides the record");
    }

    /**
     * The 18e dimension fix: a nether spawner's cleanup reads the NETHER
     * world — the overworld's block at the same coordinates never decides
     * a nether spawner's fate (the pre-18e blind sweep removed the
     * fortress's blaze spawners on their first tick).
     */
    @Test
    void theNetherSpawnerSurvivesItsOwnDimensionRead() {
        var registry = net.zaminmc.torch.server.block.BuiltinBlocks.registerAll(
                new net.zaminmc.torch.server.block.BlockRegistryBuilder()).freeze();
        var generator = new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4);
        net.zaminmc.torch.server.world.EngineWorld overworld =
                new net.zaminmc.torch.server.world.EngineWorld(
                        "overworld", registry, generator, Thread.currentThread());
        net.zaminmc.torch.server.world.EngineWorld nether =
                new net.zaminmc.torch.server.world.EngineWorld(
                        "nether", registry,
                        new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                        Thread.currentThread());
        TestPlayers players = new TestPlayers();
        players.positions.add(new Position(8.5, 64.0, 8.5));
        SpawnerManager spawners = new SpawnerManager(new Random(3L), players,
                mobs(), dimension -> dimension == -1 ? nether : overworld);
        net.zaminmc.torch.block.BlockPosition pos =
                new net.zaminmc.torch.block.BlockPosition(0, 64, 0);
        // The fortress placed the spawner block in the NETHER — the
        // overworld's same coordinates stay air (the real bug's shape).
        nether.setBlock(pos, net.zaminmc.torch.server.block.BuiltinBlocks.MOB_SPAWNER);
        SpawnerManager.MobSpawnerBlockEntity spawner = spawners.getOrCreate(-1, pos);
        spawner.setType(MobType.BLAZE);
        spawner.setDelay(100); // stay off the spawn walk: the cleanup is the test
        for (int i = 0; i < 3; i++) {
            spawners.tick();
        }
        assertEquals(MobType.BLAZE, spawners.peek(pos).type(),
                "the nether spawner survives its own-world read");
        // The mirror: an overworld spawner whose world lost the block leaves.
        net.zaminmc.torch.block.BlockPosition gonePos =
                new net.zaminmc.torch.block.BlockPosition(64, 64, 64);
        SpawnerManager.MobSpawnerBlockEntity gone = spawners.getOrCreate(0, gonePos);
        gone.setType(MobType.ZOMBIE);
        gone.setDelay(100);
        spawners.tick();
        assertEquals(null, spawners.peek(gonePos),
                "the broken spawner leaves the map");
    }

    /**
     * The 18e wire surface: the immutable per-chunk tile snapshot (the
     * chunk-send path's tile packets) and the reference's own broadcasts —
     * the fired cycle's flames (doEvent 2004) and the delay reroll's spin
     * reset (the block event 1).
     */
    @Test
    void theWireSurfaceCarriesTheTilesAndTheBroadcasts() {
        TestPlayers players = new TestPlayers();
        players.positions.add(new Position(8.5, 64.0, 8.5));
        List<String> events = new ArrayList<>();
        SpawnerManager spawners = new SpawnerManager(new Random(9L), players,
                mobs(), dimension -> null);
        spawners.addListener(new SpawnerManager.Listener() {
            @Override
            public void onSpawnerFired(int dimension,
                    net.zaminmc.torch.block.BlockPosition position) {
                events.add("fired:" + dimension);
            }

            @Override
            public void onSpawnerDelayRerolled(int dimension,
                    net.zaminmc.torch.block.BlockPosition position) {
                events.add("rerolled:" + dimension);
            }
        });
        net.zaminmc.torch.block.BlockPosition pos =
                new net.zaminmc.torch.block.BlockPosition(4, 64, 7);
        SpawnerManager.MobSpawnerBlockEntity spawner = spawners.getOrCreate(-1, pos);
        spawner.setType(MobType.BLAZE);
        spawner.setDelay(0); // the cycle fires this very tick
        spawners.refreshWireTiles();

        // The tile snapshot: the chunk's own list carries the typed spawner.
        List<SpawnerManager.SpawnerTile> tiles =
                spawners.spawnersIn(pos.chunkPosition().packed());
        assertEquals(1, tiles.size(), "one tile in the spawner's chunk");
        assertEquals(MobType.BLAZE, tiles.get(0).type(), "the tile's kind");
        assertEquals(-1, tiles.get(0).dimension(), "the tile's dimension");
        assertTrue(spawners.spawnersIn(new net.zaminmc.torch.block.ChunkPosition(
                31, 31).packed()).isEmpty(), "no tiles in the far chunk");

        spawners.tick();
        assertTrue(events.contains("fired:-1"),
                "the fired cycle broadcasts the flames (was " + events + ")");
        assertTrue(events.contains("rerolled:-1"),
                "the reroll broadcasts the spin reset (was " + events + ")");
    }
}
