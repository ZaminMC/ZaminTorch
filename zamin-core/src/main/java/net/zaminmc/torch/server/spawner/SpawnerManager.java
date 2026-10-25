package net.zaminmc.torch.server.spawner;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.FluidBlocks;
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
    /** The immutable wire tile (the chunk-send + the per-spawner sync). */
    public record SpawnerTile(int dimension, BlockPosition position, MobType type, int delay,
                              int minSpawnDelay, int maxSpawnDelay, int spawnCount,
                              int maxNearbyEntities, int requiredPlayerRange, int spawnRange) {
    }

    /**
     * The per-position spawner state (the reference's own fields): the
     * spawned kind + the delay countdown + the six cadence rows
     * (MobSpawner lines 16-29 — the NBT-settable spawn law, Slice 91
     * Wave C: read into the model, steering the walk per spawner).
     */
    public static final class MobSpawnerBlockEntity {
        private MobType type;
        private int delay = 20; // the reference's initial 20
        private int dimension;
        private int minSpawnDelay = 200;
        private int maxSpawnDelay = 800;
        private int spawnCount = 4;
        private int maxNearbyEntities = 6;
        private int requiredPlayerRange = 16;
        private int spawnRange = 4;

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

        /** The cadence rows (MobSpawner.readNbt lines 197-213 — the NBT row
         * names, the six fields steering this spawner's own walk). */
        public int minSpawnDelay() {
            return minSpawnDelay;
        }

        public void setMinSpawnDelay(int minSpawnDelay) {
            this.minSpawnDelay = minSpawnDelay;
        }

        public int maxSpawnDelay() {
            return maxSpawnDelay;
        }

        public void setMaxSpawnDelay(int maxSpawnDelay) {
            this.maxSpawnDelay = maxSpawnDelay;
        }

        public int spawnCount() {
            return spawnCount;
        }

        public void setSpawnCount(int spawnCount) {
            this.spawnCount = spawnCount;
        }

        public int maxNearbyEntities() {
            return maxNearbyEntities;
        }

        public void setMaxNearbyEntities(int maxNearbyEntities) {
            this.maxNearbyEntities = maxNearbyEntities;
        }

        public int requiredPlayerRange() {
            return requiredPlayerRange;
        }

        public void setRequiredPlayerRange(int requiredPlayerRange) {
            this.requiredPlayerRange = requiredPlayerRange;
        }

        public int spawnRange() {
            return spawnRange;
        }

        public void setSpawnRange(int spawnRange) {
            this.spawnRange = spawnRange;
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

    /**
     * The light walk's live context (the reference's own World reads the
     * canSpawn consult rides): the time-of-day and the two weather
     * gradients behind the ambient-darkness walk
     * (World.calculateAmbientDarkness), the thundering flag behind the
     * canSpawnAtLightLevel clamp (the forced ambientDarkness 10), and
     * the runtime difficulty behind every monster family's peaceful
     * gate. Wired by the boot (the same reads the natural cycle's tick
     * parameters carry); unwired keeps the unit harness's own law — a
     * not-peaceful world under a clear noon (the gates that need no
     * world read never refuse on defaults alone, and a null world
     * consult passes whole — the pre-existing harness contract).
     */
    public record SpawnContext(long timeOfDay, float rainGradient, float thunderGradient,
                               boolean thundering, int difficulty) {
    }

    /** The live context supplier (volatile — the boot wires it once). */
    private volatile java.util.function.Supplier<SpawnContext> spawnContext;

    /**
     * Wires the canSpawn consult's live reads (the boot site — the same
     * reads the natural cycle's tick parameters carry, Slice 11d/19e).
     */
    public void setSpawnContext(java.util.function.Supplier<SpawnContext> spawnContext) {
        this.spawnContext = Objects.requireNonNull(spawnContext, "spawnContext");
    }

    /** The runtime difficulty (0 peaceful .. 3 hard); unwired answers the easy default. */
    private int difficulty() {
        java.util.function.Supplier<SpawnContext> wired = spawnContext;
        if (wired == null) {
            return 1; // the harness's not-peaceful default
        }
        SpawnContext context = wired.get();
        return context != null ? context.difficulty() : 1;
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
                        spawner.type(), spawner.delay(),
                        spawner.minSpawnDelay(), spawner.maxSpawnDelay(), spawner.spawnCount(),
                        spawner.maxNearbyEntities(), spawner.requiredPlayerRange(),
                        spawner.spawnRange()));
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
            if (!isNearPlayer(pos, spawner.requiredPlayerRange())) {
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
            for (int i = 0; i < spawner.spawnCount(); i++) {
                // The nearby cap (lines 77-85): the same-kind count inside
                // the spawn-range-grown box — the reference's OWN geometry
                // (the [pos, pos+1] box grown by the range per axis,
                // intersected with each mob's bounding box).
                int nearby = 0;
                for (MobEntity mob : mobs.all()) {
                    if (mob.type() == spawner.type()
                            && intersectsGrownBox(mob, pos, spawner.spawnRange())) {
                        nearby++;
                    }
                }
                if (nearby >= spawner.maxNearbyEntities()) {
                    delay(spawner, pos);
                    break;
                }
                // The position roll (lines 87-89): the reference's own
                // distribution shapes.
                double x = pos.x() + (random.nextDouble() - random.nextDouble())
                        * spawner.spawnRange() + 0.5;
                double y = pos.y() + random.nextInt(3) - 1;
                double z = pos.z() + (random.nextDouble() - random.nextDouble())
                        * spawner.spawnRange() + 0.5;
                // The spawn gate (canSpawn + isUnobstructed lines 90-92):
                // the engine's stand-in — the body cell + the head cell
                // must be non-solid — AND the entity-level consult
                // (line 106's mobentity.canSpawn()): the kinds' own
                // gates, the whole family riding the same walk.
                if (canSpawnAt(world, spawner.type(), x, y, z)
                        && canSpawnEntity(spawner.type(), world, x, y, z)) {
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

    /** The player gate (isNearPlayer lines 45-48 — the spawner's OWN range row). */
    private boolean isNearPlayer(BlockPosition pos, int requiredPlayerRange) {
        for (Position player : players.positions()) {
            double dx = player.x() - (pos.x() + 0.5);
            double dy = player.y() - (pos.y() + 0.5);
            double dz = player.z() - (pos.z() + 0.5);
            if (dx * dx + dy * dy + dz * dz
                    <= (double) requiredPlayerRange * requiredPlayerRange) {
                return true;
            }
        }
        return false;
    }

    /**
     * The reference's nearby-count geometry (tick lines 73-77): the
     * {@code new Box(pos, pos+1).grown(range, range, range)} box intersected
     * with the mob's own bounding box — the mob spans
     * {@code [m - width/2, m + width/2]} on x/z and {@code [m, m + height]} on
     * y, the query box spans {@code [pos - range, pos + 1 + range]} per axis;
     * the strict-intersection walk is the grown box's own (a mob at the
     * box's far edge still counts — the engine's old center-band stand-in
     * missed it).
     */
    private static boolean intersectsGrownBox(MobEntity mob, BlockPosition pos, int range) {
        double halfWidth = mob.type().width * 0.5;
        double height = mob.type().height;
        Position at = mob.position();
        return at.x() + halfWidth > pos.x() - range && at.x() - halfWidth < pos.x() + 1 + range
                && at.y() + height > pos.y() - range && at.y() < pos.y() + 1 + range
                && at.z() + halfWidth > pos.z() - range && at.z() - halfWidth < pos.z() + 1 + range;
    }

    /** The unobstructed gate (public static — the spawner minecart's host shares it):
     * the body cell + the head cell clear. */
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

    // ------------------------------------------------------------------
    // The entity-level gate (MobSpawner line 106 — mobentity.canSpawn(),
    // the kinds' own overrides 1:1)
    // ------------------------------------------------------------------

    /**
     * The entity-level spawn gate (MobSpawner.tick line 106's
     * {@code mobentity.canSpawn() && mobentity.isUnobstructed()} — the
     * second conjunct the unobstructed stand-in above carries): the
     * kinds' own overrides, the whole family 1:1.
     *
     * <ul>
     *   <li>the MonsterEntity family (zombie .. wither):
     *   {@code difficulty != PEACEFUL} + the light pair
     *   (MonsterEntity.canSpawn lines 130-132 over canSpawnAtLightLevel
     *   112-127 — the randomized sky ceiling, the raw-brightness band
     *   with the thundering clamp). The PathFinderMob favor gate
     *   (0.5 - brightnessTable[raw] &ge; 0) auto-passes under the light
     *   gate's raw &le; 7 (table[7] = 0.18) — the engine's pathfinder
     *   carries no favor model at all, the giant's standing ledger
     *   envelope.</li>
     *   <li>the silverfish + the endermite: the monster chain + the
     *   empty 5-block player band (SilverfishEntity.canSpawn lines
     *   116-123, EndermiteEntity line 140 — {@code getNearestPlayer(this,
     *   5.0) == null}).</li>
     *   <li>the slime: SlimeEntity.canSpawn lines 234-259 — the
     *   MobEntity family ({@code implements Monster}, never a
     *   MonsterEntity: no light gates), the peaceful wrap, the swamp arm
     *   short-circuiting exactly like a plains consult (the engine's
     *   biome family carries no swampland), the slime-chunk band's own
     *   1-in-10 then the chunk-seeded 1-in-10 then the y &lt; 40 floor.
     *   The superflat refusal (line 238) never runs — the engine's
     *   world is normal.</li>
     *   <li>the magma cube: MagmaCubeEntity.canSpawn line 25's own
     *   override — the peaceful gate alone (no slime arms, no light).</li>
     *   <li>the ghast: GhastEntity.canSpawn line 126 — the flying
     *   family's 1-in-20 + the peaceful gate, no light gate.</li>
     *   <li>the guardian: GuardianEntity.canSpawn line 418 — the
     *   MobEntity family (no peaceful, no light), the open-sky 1-in-20
     *   with the sky-light stand-in for hasSkyAccessIgnoreLiquids (the
     *   natural cycle's own consult shape).</li>
     *   <li>the squid: SquidEntity.canSpawn line 160 — the y band
     *   (45..seaLevel) + the water cell (the super's
     *   WaterMobEntity.canSpawn answers true).</li>
     *   <li>the bat: BatEntity.canSpawn lines 188-203 — below the sea
     *   level, the 1-in-2 coin (the Halloween window's j = 7 rides no
     *   engine clock — the band keeps its j = 4, the natural cycle's
     *   own ruling), the raw brightness against the nextInt(4).</li>
     *   <li>the animals: AnimalEntity.canSpawn lines 90-95 — the
     *   spawnableBlock floor (the grass every animal reads; the
     *   mooshroom's own MYCELIUM, MooshroomEntity line 16) + the actual
     *   light {@code max(sky, block) > 8} (the PathFinderMob favor 0.0
     *   pass rides the super).</li>
     *   <li>the villager + the golem pair: the PathFinderMob favor 0.0
     *   pass — no gate of their own.</li>
     * </ul>
     *
     * <p>The harness shape stays the pre-existing contract: a null
     * world consult answers true whole (no chunk grid to read, no gate
     * to decide) — the unit harness's own law since Slice 12g.</p>
     */
    public boolean canSpawnEntity(MobType type, EngineWorld world, double x, double y, double z) {
        if (world == null) {
            return true; // the harness shape: no chunk grid to consult
        }
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        BlockPosition cell = new BlockPosition(bx, by, bz);
        switch (type) {
            case GHAST -> {
                // GhastEntity.canSpawn line 126: the 1-in-20 roll + the
                // peaceful gate (the FlyingMob family — the Monster chain
                // never rides it).
                return random.nextInt(20) == 0 && difficulty() != 0;
            }
            case GUARDIAN -> {
                // GuardianEntity.canSpawn line 418: the MobEntity family —
                // the open-sky 1-in-20 (the sky-light stand-in), no
                // peaceful and no light gate.
                return random.nextInt(20) == 0 || world.skyLightAt(cell) < 15;
            }
            case SQUID -> {
                // SquidEntity.canSpawn line 160: the y band over the
                // World's own sea level 63 + the water cell.
                return y > 45.0 && y < 63.0
                        && FluidBlocks.isFluid(world.getBlock(cell).identifier());
            }
            case BAT -> {
                // BatEntity.canSpawn lines 188-203: below the sea level,
                // the 1-in-2 coin, then the raw brightness against the
                // nextInt(4).
                return by < 63 && random.nextBoolean()
                        && rawBrightness(world, cell) <= random.nextInt(4);
            }
            case SLIME -> {
                // SlimeEntity.canSpawn lines 234-259: the peaceful wrap,
                // then the swamp arm (no swampland in the engine's biome
                // family — the arm refuses exactly like a plains consult)
                // and the slime-chunk band alone.
                if (difficulty() == 0) {
                    return false;
                }
                if (random.nextInt(10) != 0) {
                    return false;
                }
                int chunkX = bx >> 4;
                int chunkZ = bz >> 4;
                long slimeSeed = world.generatorSeed()
                        + (long) chunkX * chunkX * 4987142L
                        + (long) chunkX * 5947611L
                        + (long) chunkZ * chunkZ * 4392871L
                        + (long) chunkZ * 389711L
                        ^ 987234911L;
                return new Random(slimeSeed).nextInt(10) == 0 && y < 40.0;
            }
            case MAGMA_CUBE -> {
                // MagmaCubeEntity.canSpawn line 25: the cube's own
                // override — the peaceful gate alone.
                return difficulty() != 0;
            }
            case SILVERFISH, ENDERMITE -> {
                // SilverfishEntity.canSpawn lines 116-123 (the endermite's
                // own line 140 the same shape): the monster chain + the
                // empty 5-block player band.
                return monsterCanSpawn(world, cell)
                        && !playerWithin(x, y, z, 5.0);
            }
            default -> {
                if (type.hostile) {
                    // The MonsterEntity family: the peaceful gate + the
                    // light pair.
                    return monsterCanSpawn(world, cell);
                }
                if (isAnimal(type)) {
                    return animalCanSpawn(world, cell, type);
                }
                // The villager + the golem pair: the favor 0.0 pass.
                return true;
            }
        }
    }

    /**
     * MonsterEntity.canSpawn lines 130-132 over canSpawnAtLightLevel
     * 112-127: the peaceful gate, the randomized sky-light ceiling, the
     * raw-brightness band (the thundering clamp rides the brightness
     * read itself).
     */
    private boolean monsterCanSpawn(EngineWorld world, BlockPosition cell) {
        if (difficulty() == 0) {
            return false; // the peaceful world never hosts the monster family
        }
        int sky = world.skyLightAt(cell);
        if (sky > random.nextInt(32)) {
            return false; // the day gate: the randomized sky ceiling
        }
        return rawBrightness(world, cell) <= random.nextInt(8);
    }

    /**
     * AnimalEntity.canSpawn lines 90-95: the spawnableBlock floor under
     * the feet (the grass every animal reads; the mooshroom's own
     * MYCELIUM) + the actual light {@code max(sky, block) > 8}.
     */
    private boolean animalCanSpawn(EngineWorld world, BlockPosition cell, MobType type) {
        String floor = type == MobType.MOOSHROOM
                ? "minecraft:mycelium" : "minecraft:grass_block";
        if (!Objects.equals(world.getBlock(cell.offset(0, -1, 0)).identifier().toString(), floor)) {
            return false;
        }
        return Math.max(world.skyLightAt(cell), world.blockLightAt(cell)) > 8;
    }

    /**
     * World.getRawBrightness over Chunk.getLight (lines 592-606): the
     * {@code max(sky - ambientDarkness, block)} walk — the ambient
     * darkness the live context's time-and-gradient shape answers
     * (NaturalSpawnSystem.ambientDarkness, the calculateAmbientDarkness
     * port), the thundering clamp forcing the 10 the reference's own
     * canSpawnAtLightLevel walk reads (lines 119-124).
     */
    private int rawBrightness(EngineWorld world, BlockPosition cell) {
        java.util.function.Supplier<SpawnContext> wired = spawnContext;
        SpawnContext context = wired != null ? wired.get() : null;
        int darkness = context != null
                ? net.zaminmc.torch.server.spawn.NaturalSpawnSystem.ambientDarkness(
                        context.timeOfDay(), context.rainGradient(), context.thunderGradient())
                : 0;
        if (context != null && context.thundering()) {
            darkness = 10; // the storming sky's forced clamp
        }
        return Math.max(world.skyLightAt(cell) - darkness, world.blockLightAt(cell));
    }

    /** The AnimalEntity family (the passive kinds the spawnableBlock law reads). */
    private static boolean isAnimal(MobType type) {
        return switch (type) {
            case PIG, COW, CHICKEN, SHEEP, RABBIT, WOLF, OCELOT, HORSE, MOOSHROOM -> true;
            default -> false;
        };
    }

    /**
     * The player proximity consult (World.getNearestPlayer's own strict
     * band): whether any player stands within the radius of the point.
     */
    private boolean playerWithin(double x, double y, double z, double radius) {
        double squared = radius * radius;
        for (Position player : players.positions()) {
            double dx = player.x() - x;
            double dy = player.y() - y;
            double dz = player.z() - z;
            if (dx * dx + dy * dy + dz * dz < squared) {
                return true;
            }
        }
        return false;
    }

    /**
     * The delay re-roll (delay lines 156-169): the spawner's OWN band
     * (the NBT-settable pair), the event-1 broadcast (the spin reset),
     * and the wire snapshot's refresh.
     */
    private void delay(MobSpawnerBlockEntity spawner, BlockPosition pos) {
        if (spawner.maxSpawnDelay() <= spawner.minSpawnDelay()) {
            spawner.setDelay(spawner.minSpawnDelay());
        } else {
            spawner.setDelay(spawner.minSpawnDelay()
                    + random.nextInt(spawner.maxSpawnDelay() - spawner.minSpawnDelay()));
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
