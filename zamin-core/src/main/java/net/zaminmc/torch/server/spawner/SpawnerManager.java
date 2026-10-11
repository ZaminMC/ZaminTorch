package net.zaminmc.torch.server.spawner;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * The mob spawner system (Slice 12g — the reference port of
 * block/spawner/MobSpawner.java over the engine's ownership model): the
 * per-position spawner state (the type + the delay band) and the tick walk.
 *
 * <p><b>The spawner logic (tick lines 49-113 + delay lines 156-169,
 * 1:1):</b> the 16-block player range gate, the delay countdown (the
 * -1 first-tick reset, the 200..800 band on fire), then the four-at-a-time
 * spawn walk — each attempt counts the same-kind mobs inside the
 * spawn-range-grown box (the 6-mob cap aborts the whole cycle with a fresh
 * delay), rolls the position inside the 4-block range ({@code nextDouble()
 * - nextDouble()} per axis, {@code nextInt(3) - 1} for y — the reference's
 * own distribution shapes), and spawns when the cell is unobstructed. The
 * event 2004 + the spin display ledger with the particle/wire slices (the
 * engine's envelope note).</p>
 *
 * <p><b>The ownership model</b> (the chest manager's own): the map lives
 * on the world owner, mutated only on the tick thread; the generator's
 * populate-time records drain into it exactly once per chunk publish with
 * the contents guard (a spawner already carrying a type skips the
 * re-set).</p>
 */
public final class SpawnerManager {

    /**
     * The per-position spawner state (the reference's own fields): the
     * spawned kind + the delay countdown.
     */
    public static final class MobSpawnerBlockEntity {
        private MobType type;
        private int delay = 20; // the reference's initial 20

        public MobType type() {
            return type;
        }

        public void setType(MobType type) {
            this.type = type;
        }

        int delay() {
            return delay;
        }

        void setDelay(int delay) {
            this.delay = delay;
        }
    }

    /** The player surface (the 16-block range gate). */
    public interface Players {
        List<Position> positions();
    }

    private final Map<net.zaminmc.torch.block.BlockPosition, MobSpawnerBlockEntity> spawners =
            new LinkedHashMap<>();
    private final Random random;
    private final Players players;
    private final MobManager mobs;
    private final net.zaminmc.torch.server.world.EngineWorld world;

    /** The reference's own constants (lines 21-29). */
    private static final int MIN_SPAWN_DELAY = 200;
    private static final int MAX_SPAWN_DELAY = 800;
    private static final int SPAWN_COUNT = 4;
    private static final int MAX_NEARBY_ENTITIES = 6;
    private static final int REQUIRED_PLAYER_RANGE = 16;
    private static final int SPAWN_RANGE = 4;

    public SpawnerManager(Random random, Players players, MobManager mobs,
                          net.zaminmc.torch.server.world.EngineWorld world) {
        this.random = Objects.requireNonNull(random, "random");
        this.players = Objects.requireNonNull(players, "players");
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        // The world is optional: the unit harness runs the walk without a
        // chunk grid (every spawn cell clears, no cleanup pass); the
        // engine always passes the live world.
        this.world = world;
    }

    /** @return the spawner state at the position, creating it on first use. */
    public MobSpawnerBlockEntity getOrCreate(net.zaminmc.torch.block.BlockPosition position) {
        MobSpawnerBlockEntity existing = spawners.get(position);
        if (existing != null) {
            return existing;
        }
        MobSpawnerBlockEntity created = new MobSpawnerBlockEntity();
        spawners.put(position, created);
        return created;
    }

    /** @return the live state at the position, or null when there is none. */
    public MobSpawnerBlockEntity peek(net.zaminmc.torch.block.BlockPosition position) {
        return spawners.get(position);
    }

    /** @return a defensive copy of the whole spawner map (save snapshot). */
    public Map<net.zaminmc.torch.block.BlockPosition, MobSpawnerBlockEntity> snapshot() {
        return new LinkedHashMap<>(spawners);
    }

    /** Restores persisted state after boot (world deltas already applied). */
    public void restoreAll(Map<net.zaminmc.torch.block.BlockPosition, MobSpawnerBlockEntity> restored) {
        spawners.putAll(restored);
    }

    /**
     * The tick walk (tick lines 49-113, 1:1 over the engine's surfaces):
     * the player gate, the delay walk, the four-attempt spawn cycle with
     * the nearby cap, then the state cleanup (a broken spawner block
     * leaves).
     */
    public void tick() {
        for (var entry : spawners.entrySet()) {
            net.zaminmc.torch.block.BlockPosition pos = entry.getKey();
            MobSpawnerBlockEntity spawner = entry.getValue();
            if (spawner.type() == null) {
                continue;
            }
            if (!isNearPlayer(pos)) {
                continue;
            }
            if (spawner.delay() == -1) {
                delay(spawner);
            }
            if (spawner.delay() > 0) {
                spawner.setDelay(spawner.delay() - 1);
                continue;
            }
            boolean anySpawned = false;
            for (int i = 0; i < SPAWN_COUNT; i++) {
                // The nearby cap (lines 77-85): the same-kind count inside
                // the spawn-range-grown box.
                int nearby = 0;
                for (MobEntity mob : mobs.all()) {
                    if (mob.type() == spawner.type()) {
                        Position at = mob.position();
                        if (Math.abs(at.x() - (pos.x() + 0.5)) <= SPAWN_RANGE
                                && Math.abs(at.y() - (pos.y() + 0.5)) <= SPAWN_RANGE
                                && Math.abs(at.z() - (pos.z() + 0.5)) <= SPAWN_RANGE) {
                            nearby++;
                        }
                    }
                }
                if (nearby >= MAX_NEARBY_ENTITIES) {
                    delay(spawner);
                    break;
                }
                // The position roll (lines 87-89): the reference's own
                // distribution shapes.
                double x = pos.x() + (random.nextDouble() - random.nextDouble()) * SPAWN_RANGE + 0.5;
                double y = pos.y() + random.nextInt(3) - 1;
                double z = pos.z() + (random.nextDouble() - random.nextDouble()) * SPAWN_RANGE + 0.5;
                // The spawn gate (canSpawn + isUnobstructed lines 90-92):
                // the engine's stand-in — the body cell + the head cell
                // must be non-solid.
                if (canSpawnAt(spawner.type(), x, y, z)) {
                    mobs.spawnAt(spawner.type(), new Position(x, y, z));
                    anySpawned = true;
                }
            }
            if (anySpawned) {
                delay(spawner);
            }
        }
        // The cleanup: a spawner whose block left (a break) leaves the
        // map (skipped without a world — the harness shape).
        if (world != null) {
            spawners.entrySet().removeIf(entry ->
                    !isSpawnerBlock(entry.getKey()));
        }
    }

    /** The player gate (isNearPlayer lines 45-48). */
    private boolean isNearPlayer(net.zaminmc.torch.block.BlockPosition pos) {
        for (Position player : players.positions()) {
            double dx = player.x() - (pos.x() + 0.5);
            double dy = player.y() - (pos.y() + 0.5);
            double dz = player.z() - (pos.z() + 0.5);
            if (dx * dx + dy * dy + dz * dz
                    <= (double) REQUIRED_PLAYER_RANGE * REQUIRED_PLAYER_RANGE) {
                return true;
            }
        }
        return false;
    }

    /** The unobstructed gate: the body cell + the head cell clear. */
    private boolean canSpawnAt(MobType type, double x, double y, double z) {
        if (world == null) {
            return true; // the harness shape: no chunk grid to consult
        }
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        if (net.zaminmc.torch.server.block.WorldSolidity.isSolid(world.getBlock(
                new net.zaminmc.torch.block.BlockPosition(bx, by, bz)))) {
            return false;
        }
        if (type.height > 1.0 && net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                world.getBlock(new net.zaminmc.torch.block.BlockPosition(bx, by + 1, bz)))) {
            return false;
        }
        return true;
    }

    /** The delay re-roll (delay lines 156-169): the 200..800 band. */
    private void delay(MobSpawnerBlockEntity spawner) {
        if (MAX_SPAWN_DELAY <= MIN_SPAWN_DELAY) {
            spawner.setDelay(MIN_SPAWN_DELAY);
        } else {
            spawner.setDelay(MIN_SPAWN_DELAY
                    + random.nextInt(MAX_SPAWN_DELAY - MIN_SPAWN_DELAY));
        }
    }

    private boolean isSpawnerBlock(net.zaminmc.torch.block.BlockPosition pos) {
        return world.getBlock(pos).identifier().value().equals("mob_spawner");
    }
}
