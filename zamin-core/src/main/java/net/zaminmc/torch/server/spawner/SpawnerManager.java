package net.zaminmc.torch.server.spawner;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.EngineWorld;
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
 * own distribution shapes), and spawns when the cell is unobstructed.</p>
 *
 * <p><b>The dimension model (Slice 18e)</b>: every spawner carries the
 * dimension it lives in — the cell gate and the broken-block cleanup read
 * the spawner's OWN world (the overworld's strongholds and the nether's
 * fortresses), closing the pre-18e blind sweep that removed a nether
 * spawner on its first tick (the overworld read at the same coordinates
 * never answers a spawner block).</p>
 *
 * <p><b>The wire (Slice 18e)</b>: the immutable per-chunk tile snapshot
 * ({@link SpawnerTile} — rebuilt on every mutation, published through a
 * volatile) feeds the chunk-send path's Update Block Entity packets, and
 * the listeners carry the reference's own two broadcasts — the fired
 * cycle's world event 2004 (the flame burst, {@code world.doEvent(2004,
 * blockpos, 0)}) and the delay reroll's block event 1 (the spin reset,
 * {@code addBlockEvent(pos, MOB_SPAWNER, 1, 0)}).</p>
 *
 * <p><b>The ownership model</b> (the chest manager's own): the map lives
 * on the world owner, mutated only on the tick thread; the generator's
 * populate-time records drain into it exactly once per chunk publish with
 * the contents guard (a spawner already carrying a type skips the
 * re-set).</p>
 */
public final class SpawnerManager {

    /**
     * The immutable wire view of one spawner (the tile packet's payload):
     * the position, the kind, and the live delay at the last mutation —
     * the Update Block Entity (0x35) payload the chunk-send path reads
     * from any thread.
     */
    public record SpawnerTile(int dimension, BlockPosition position, MobType type, int delay) {
    }

    /**
     * The per-position spawner state (the reference's own fields): the
     * spawned kind + the delay countdown.
     */
    public static final class MobSpawnerBlockEntity {
        private MobType type;
        private int delay = 20; // the reference's initial 20
        private int dimension;

        public MobType type() {
            return type;
        }

        public void setType(MobType type) {
            this.type = type;
        }

        /** @return the dimension the spawner lives in (0 overworld, -1 nether). */
        public int dimension() {
            return dimension;
        }

        void setDimension(int dimension) {
            this.dimension = dimension;
        }

        /** @return the remaining spawn-delay ticks (the public read surface's own). */
        public int delay() {
            return delay;
        }

        public void setDelay(int delay) {
            this.delay = delay;
        }
    }

    /** The player surface (the 16-block range gate). */
    public interface Players {
        List<Position> positions();
    }

    /**
     * The observers of the reference's own broadcasts: the fired cycle's
     * flames (doEvent 2004) and the delay reroll's spin reset (the block
     * event 1).
     */
    public interface Listener {

        /**
         * The spawn cycle fired (tick line 108: {@code world.doEvent(2004,
         * blockpos, 0)}) — the client draws the flame burst itself.
         */
        void onSpawnerFired(int dimension, BlockPosition position);

        /**
         * The delay re-rolled (delay line 187's broadcastEvent(1) →
         * addBlockEvent(pos, MOB_SPAWNER, 1, 0)) — the client resets its
         * spin to the min-delay rate.
         */
        void onSpawnerDelayRerolled(int dimension, BlockPosition position);
    }

    private final Map<BlockPosition, MobSpawnerBlockEntity> spawners =
            new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    /** The immutable tile snapshot (the wire's cross-thread read). */
    private volatile List<SpawnerTile> wireTiles = List.of();
    private final Random random;
    private final Players players;
    private final MobManager mobs;
    /** The dimension-aware world read (the cell gate + the cleanup). */
    private final java.util.function.IntFunction<EngineWorld> worldResolver;

    /** The reference's own constants (lines 21-29). */
    private static final int MIN_SPAWN_DELAY = 200;
    private static final int MAX_SPAWN_DELAY = 800;
    private static final int SPAWN_COUNT = 4;
    private static final int MAX_NEARBY_ENTITIES = 6;
    private static final int REQUIRED_PLAYER_RANGE = 16;
    private static final int SPAWN_RANGE = 4;

    /**
     * @param worldResolver the dimension → world read (0 the overworld,
     *                      -1 the nether); the unit harness passes
     *                      {@code d -> null} for the world-less walk.
     */
    public SpawnerManager(Random random, Players players, MobManager mobs,
                          java.util.function.IntFunction<EngineWorld> worldResolver) {
        this.random = Objects.requireNonNull(random, "random");
        this.players = Objects.requireNonNull(players, "players");
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.worldResolver = Objects.requireNonNull(worldResolver, "worldResolver");
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** @return the spawner state at the position, creating it on first use. */
    public MobSpawnerBlockEntity getOrCreate(int dimension, BlockPosition position) {
        MobSpawnerBlockEntity existing = spawners.get(position);
        if (existing != null) {
            existing.setDimension(dimension); // the publish walk's own world
            return existing;
        }
        MobSpawnerBlockEntity created = new MobSpawnerBlockEntity();
        created.setDimension(dimension);
        spawners.put(position, created);
        republishTiles();
        return created;
    }

    /** @return the live state at the position, or null when there is none. */
    public MobSpawnerBlockEntity peek(BlockPosition position) {
        return spawners.get(position);
    }

    /** @return a defensive copy of the whole spawner map (save snapshot). */
    public Map<BlockPosition, MobSpawnerBlockEntity> snapshot() {
        return new LinkedHashMap<>(spawners);
    }

    /** Restores persisted state after boot (world deltas already applied). */
    public void restoreAll(Map<BlockPosition, MobSpawnerBlockEntity> restored) {
        spawners.putAll(restored);
        republishTiles();
    }

    /**
     * @return the immutable wire tiles inside the packed chunk (the
     *         chunk-send path's tile packets), read safely from any thread.
     */
    public List<SpawnerTile> spawnersIn(long packedChunk) {
        List<SpawnerTile> found = new ArrayList<>();
        for (SpawnerTile tile : wireTiles) {
            if (tile.position().chunkPosition().packed() == packedChunk) {
                found.add(tile);
            }
        }
        return found;
    }

    /** Rebuilds the immutable wire snapshot (tick-thread mutations only). */
    private void republishTiles() {
        List<SpawnerTile> rebuilt = new ArrayList<>(spawners.size());
        for (var entry : spawners.entrySet()) {
            MobSpawnerBlockEntity spawner = entry.getValue();
            if (spawner.type() != null) {
                rebuilt.add(new SpawnerTile(spawner.dimension(), entry.getKey(),
                        spawner.type(), spawner.delay()));
            }
        }
        wireTiles = List.copyOf(rebuilt);
    }

    /**
     * The public tile refresh (the drain's typed spawner path — the type
     * lands after the getOrCreate, so the snapshot needs the second
     * publish). Tick-thread context.
     */
    public void refreshWireTiles() {
        republishTiles();
    }

    /**
     * The tick walk (tick lines 49-113, 1:1 over the engine's surfaces):
     * the player gate, the delay walk, the four-attempt spawn cycle with
     * the nearby cap, then the state cleanup (a broken spawner block
     * leaves) — the cleanup reading the spawner's OWN dimension world
     * (Slice 18e's fix).
     */
    public void tick() {
        for (var entry : spawners.entrySet()) {
            BlockPosition pos = entry.getKey();
            MobSpawnerBlockEntity spawner = entry.getValue();
            if (spawner.type() == null) {
                continue;
            }
            if (!isNearPlayer(pos)) {
                continue;
            }
            if (spawner.delay() == -1) {
                delay(spawner, pos);
            }
            if (spawner.delay() > 0) {
                spawner.setDelay(spawner.delay() - 1);
                continue;
            }
            boolean anySpawned = false;
            EngineWorld world = worldResolver.apply(spawner.dimension());
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
                    delay(spawner, pos);
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
                if (canSpawnAt(world, spawner.type(), x, y, z)) {
                    MobEntity spawned = mobs.spawnAt(spawner.type(), new Position(x, y, z));
                    if (spawned != null) {
                        // The body lives in the spawner's own dimension
                        // (Slice 25d — the 18e dimension model's spawn arm).
                        spawned.setDimension(spawner.dimension());
                    }
                    anySpawned = true;
                }
            }
            if (anySpawned) {
                // tick line 108: the world event 2004 — the flame burst.
                for (Listener listener : listeners) {
                    listener.onSpawnerFired(spawner.dimension(), pos);
                }
                delay(spawner, pos);
            }
        }
        // The cleanup: a spawner whose block left (a break) leaves the map
        // — read against the spawner's OWN world (the 18e fix). A null
        // world (the unit harness's shape) skips the sweep, the pre-18e
        // null-world contract.
        boolean anyWorldless = false;
        for (var entry : spawners.entrySet()) {
            if (worldResolver.apply(entry.getValue().dimension()) == null) {
                anyWorldless = true;
                break;
            }
        }
        if (anyWorldless) {
            return; // no world to consult: nothing decides a removal
        }
        boolean removed = spawners.entrySet().removeIf(entry ->
                !isSpawnerBlock(worldResolver.apply(entry.getValue().dimension()), entry.getKey())
                        // The block-gone discard only decides on a LOADED
                        // chunk — an unloaded chunk reads as air, but the
                        // vanilla block entity freezes there (a restored
                        // spawner outside the spawn area survives the
                        // restart; it resumes when its chunk returns).
                        && worldResolver.apply(entry.getValue().dimension())
                                .isChunkLoadedAt(entry.getKey()));
        if (removed) {
            republishTiles();
        }
    }

    /** The player gate (isNearPlayer lines 45-48). */
    private boolean isNearPlayer(BlockPosition pos) {
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
    /** The unobstructed gate (public static — the spawner minecart's host shares it). */
    public static boolean canSpawnAt(EngineWorld world, MobType type, double x, double y, double z) {
        if (world == null) {
            return true; // the harness shape: no chunk grid to consult
        }
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        if (net.zaminmc.torch.server.block.WorldSolidity.isSolid(world.getBlock(
                new BlockPosition(bx, by, bz)))) {
            return false;
        }
        if (type.height > 1.0 && net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                world.getBlock(new BlockPosition(bx, by + 1, bz)))) {
            return false;
        }
        return true;
    }

    /**
     * The delay re-roll (delay lines 156-169): the 200..800 band, the
     * event-1 broadcast (the spin reset), and the wire snapshot's refresh.
     */
    private void delay(MobSpawnerBlockEntity spawner, BlockPosition pos) {
        if (MAX_SPAWN_DELAY <= MIN_SPAWN_DELAY) {
            spawner.setDelay(MIN_SPAWN_DELAY);
        } else {
            spawner.setDelay(MIN_SPAWN_DELAY
                    + random.nextInt(MAX_SPAWN_DELAY - MIN_SPAWN_DELAY));
        }
        // broadcastEvent(1) → addBlockEvent(pos, MOB_SPAWNER, 1, 0).
        for (Listener listener : listeners) {
            listener.onSpawnerDelayRerolled(spawner.dimension(), pos);
        }
    }

    private boolean isSpawnerBlock(EngineWorld world, BlockPosition pos) {
        return world.getBlock(pos).identifier().value().equals("mob_spawner");
    }
}
