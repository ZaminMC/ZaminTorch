package net.zaminmc.torch.server.spawner;

import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The mob spawner walk (Slice 12g — the reference port of
 * block/spawner/MobSpawner.java): the player gate, the delay band, the
 * typed records from the structures and the snapshot surface.
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
                mobs(), null);
        net.zaminmc.torch.block.BlockPosition pos =
                new net.zaminmc.torch.block.BlockPosition(0, 64, 0);
        spawners.getOrCreate(pos).setType(MobType.SILVERFISH);
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
                mobs(), null);
        net.zaminmc.torch.block.BlockPosition pos =
                new net.zaminmc.torch.block.BlockPosition(0, 64, 0);
        SpawnerManager.MobSpawnerBlockEntity spawner = spawners.getOrCreate(pos);
        spawner.setType(MobType.SILVERFISH);
        spawner.setDelay(5);
        // The null MobManager aborts the spawn walk before the spawn —
        // but the delay countdown happens first (the reference's own
        // order), so the count reaches zero without an NPE only when
        // the walk stops at the delay. Set a high delay to stay above.
        spawner.setDelay(3);
        spawners.tick();
        assertEquals(2, spawner.delay(), "the near player counts the delay down");
    }

    @Test
    void theTypedRecordSurvivesTheSnapshot() {
        SpawnerManager spawners = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), null);
        net.zaminmc.torch.block.BlockPosition pos =
                new net.zaminmc.torch.block.BlockPosition(10, 20, 30);
        SpawnerManager.MobSpawnerBlockEntity spawner = spawners.getOrCreate(pos);
        spawner.setType(MobType.BLAZE);
        assertEquals(MobType.BLAZE, spawners.snapshot().get(pos).type(),
                "the typed state snapshots for the save");
        assertEquals(20, spawners.snapshot().get(pos).delay(),
                "the reference's initial delay rides the record");
    }
}
