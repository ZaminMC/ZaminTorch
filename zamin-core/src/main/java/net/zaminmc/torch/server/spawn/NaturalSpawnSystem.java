package net.zaminmc.torch.server.spawn;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * The natural spawn cycle (Slice 11d — the reference port of
 * world/NaturalSpawner.java + MonsterEntity.canSpawnAtLightLevel +
 * AnimalEntity.canSpawn + the dimension's time-of-day walk): the
 * 17-block spawning-chunk window around every player (the outer ring
 * counts toward the cap but never spawns), the per-category caps
 * scaled by the spawning chunks ({@code cap * chunks / 289}), the
 * three pack attempts each drifting four positions by
 * {@code nextInt(6) - nextInt(6)} (the y never drifts — the reference's
 * own {@code nextInt(1) - nextInt(1)} quirk), the 24-block player and
 * world-spawn keep-out rings, the spawn-cell rule (the full-cube solid
 * below, the air cell, the air above, no bedrock floor), the biome's
 * weighted entry pick with the plains-style tables, the animal grass +
 * brightness gate (the raw light — no ambient-darkness shave, the
 * reference's own {@code getActualLight} = {@code chunk.getLight(pos, 0)}),
 * the monster's randomized sky/block light rule read against the
 * time-of-day ambient darkness, and the four-per-chunk limit with the
 * horse-only 1/5 baby roll.
 *
 * <p>The mobs stay the MobManager's; this system owns the spawn math and
 * the cadence (every tick — the reference's own ServerWorld.tick call).</p>
 */
public final class NaturalSpawnSystem {

    /** The spawning window (NaturalSpawner line 42: the 17x17 grid). */
    private static final int SPAWN_RADIUS_CHUNKS = 8;
    /** The cap divisor (MOB_CAPACITY_CHUNK_AREA = 17^2). */
    private static final int CAP_CHUNK_AREA = 17 * 17;
    /** The pack drift (NaturalSpawner line 85: 6). */
    private static final int DRIFT = 6;
    /** The player keep-out ring (NaturalSpawner line 90: 24.0). */
    private static final double PLAYER_KEEP_OUT = 24.0;
    /** The world-spawn keep-out ring (576 = 24^2). */
    private static final double SPAWN_KEEP_OUT_SQUARED = 576.0;
    /** The per-chunk limit (MobEntity.getLimitPerChunk: 4). */
    private static final int LIMIT_PER_CHUNK = 4;
    /** The horse baby roll (HorseBaseEntity.initialize lines 1284-1286:
     * nextInt(5) == 0 — the ONLY animal that spawns as a baby; the cows,
     * pigs, sheep and chickens never do). */
    private static final int HORSE_BABY_ROLL = 5;

    /** The categories (MobCategory: the caps). */
    public enum Category {
        MONSTER(70),
        CREATURE(10),
        AMBIENT(15),
        WATER_CREATURE(5);

        final int cap;

        Category(int cap) {
            this.cap = cap;
        }
    }

    /** One biome spawn entry (Biome.SpawnEntry: the weight + the group size). */
    public record SpawnEntry(MobType type, int weight, int minGroup, int maxGroup) {
    }

    /**
     * The ocean monument's own monster rows (OceanMonumentStructure's
     * static SPAWN_ENTRIES, Slice 17a): the guardian at weight 1, groups
     * of 2-4 — the only monster that spawns inside the monument bounds
     * (the reference's getSpawnEntries swap).
     */
    private static final List<SpawnEntry> MONUMENT_MONSTER_ENTRIES = List.of(
            new SpawnEntry(MobType.GUARDIAN, 1, 2, 4));

    /**
     * The fortress's own monster rows (FortressStructure's constructor,
     * Slice 18a — the reference's monsterSpawnEntries): the blaze at 10
     * (groups 2-3), the pigman at 5, the skeleton at 10, the magma cube at
     * 3 — the nether's spawn consult swaps to these rows inside a
     * fortress's piece bounds, or its start bounds over nether bricks
     * (NetherChunkGenerator.getSpawnEntries lines 383-392).
     */
    private static final List<SpawnEntry> FORTRESS_MONSTER_ENTRIES = List.of(
            new SpawnEntry(MobType.BLAZE, 10, 2, 3),
            new SpawnEntry(MobType.ZOMBIE_PIGMAN, 5, 4, 4),
            new SpawnEntry(MobType.SKELETON, 10, 4, 4),
            new SpawnEntry(MobType.MAGMA_CUBE, 3, 4, 4));

    /** The engine's mob surface. */
    public interface Mobs {
        /**
         * Spawns one mob at the exact position with the walk's own yaw roll
         * (the reference's setPositionAndAngles: the group's baby flag).
         */
        void spawn(MobType type, Position at, boolean baby, float yaw);

        /** The live count of the category (MONSTER = hostile). */
        int countOf(Category category);
    }

    /** The world surface the spawner reads. */
    public interface WorldQuery {
        /** The block type at the position (null = air, the engine's own). */
        Object blockAt(BlockPosition position);

        /** The column's surface height (the heightmap read). */
        int surfaceHeight(int x, int z);

        /** The block light at the position. */
        int blockLightAt(BlockPosition position);

        /** The sky light at the position. */
        int skyLightAt(BlockPosition position);

        /** The world's spawn point. */
        Position spawnPoint();

        /**
         * Whether the chunk stands loaded (the engine's own border gate:
         * the reference's {@code getWorldBorder().contains(chunkpos)}
         * stand-in — the engine refuses the reference's pathological
         * forced generation of the spawning window at small view
         * distances; at view distance 9 or more the two are identical).
         */
        boolean isChunkLoaded(int chunkX, int chunkZ);

        /**
         * The world's own seed (the slime-chunk derivation's input —
         * WorldChunk.getRandomForSlime reads the world seed).
         */
        long worldSeed();

        /**
         * Whether the position stands inside a generated ocean monument's
         * bounds (Slice 17a — the reference's OverworldChunkGenerator
         * getSpawnEntries monument arm: the guardian's spawn rows replace
         * the monster list inside). The default answers false (the stub
         * worlds carry no monuments).
         */
        default boolean insideMonument(int x, int y, int z) {
            return false;
        }

        /**
         * Whether the position stands inside a generated nether fortress
         * (Slice 18a — the reference's
         * NetherChunkGenerator.getSpawnEntries fortress arm): true inside
         * a PIECE's bounds, or inside the START's bounds with nether
         * bricks under the feet. The default answers false (the stub
         * worlds carry no fortresses).
         */
        default boolean insideFortress(int x, int y, int z) {
            return false;
        }
    }

    private final Mobs mobs;
    private final WorldQuery world;
    private final Random random;
    /** Whether this cycle serves the nether dimension (the HellBiome rows). */
    private final boolean nether;
    /** The spawning-chunk set (rebuilt every tick, the reference's own). */
    private final Set<Long> spawningChunks = new HashSet<>();

    public NaturalSpawnSystem(Mobs mobs, WorldQuery world, Random random) {
        this(mobs, world, random, false);
    }

    /**
     * The dimension-aware cycle: the nether instance walks the HellBiome
     * rows (Slice 16d — GhastEntity 50/4-4, ZombiePigmanEntity 100/4-4,
     * MagmaCubeEntity 1/4-4, every category but MONSTER empty — the
     * reference's own hell table has no creature/ambient/water rows).
     */
    public NaturalSpawnSystem(Mobs mobs, WorldQuery world, Random random, boolean nether) {
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.world = Objects.requireNonNull(world, "world");
        this.random = Objects.requireNonNull(random, "random");
        this.nether = nether;
    }

    // ------------------------------------------------------------------
    // The biome tables (Biome's own defaults + the plains' horse row)
    // ------------------------------------------------------------------

    private List<SpawnEntry> entriesOf(Category category) {
        if (nether) {
            // HellBiome lines 14-16: the nether's own monster table, and
            // nothing else (no creature/ambient/water rows — the reference's
            // own hell list).
            return switch (category) {
                case MONSTER -> List.of(
                        new SpawnEntry(MobType.GHAST, 50, 4, 4),
                        new SpawnEntry(MobType.ZOMBIE_PIGMAN, 100, 4, 4),
                        new SpawnEntry(MobType.MAGMA_CUBE, 1, 4, 4));
                default -> List.of();
            };
        }
        return switch (category) {
            case MONSTER -> List.of(
                    // Biome lines 284-290: the four common hostiles at 100
                    // each, groups of 4 + the enderman's row (Slice 12i)
                    // + the witch (12j) + the slime (12k) — the full
                    // default monster list.
                    new SpawnEntry(MobType.SPIDER, 100, 4, 4),
                    new SpawnEntry(MobType.ZOMBIE, 100, 4, 4),
                    new SpawnEntry(MobType.SKELETON, 100, 4, 4),
                    new SpawnEntry(MobType.CREEPER, 100, 4, 4),
                    new SpawnEntry(MobType.SLIME, 100, 4, 4),
                    new SpawnEntry(MobType.ENDERMAN, 10, 1, 4),
                    new SpawnEntry(MobType.WITCH, 5, 1, 1));
            case CREATURE -> List.of(
                    // Biome lines 279-283 + the plains' horse row
                    // (PlainsBiome line 20: weight 5, groups 2-6).
                    new SpawnEntry(MobType.SHEEP, 12, 4, 4),
                    new SpawnEntry(MobType.RABBIT, 10, 3, 3),
                    new SpawnEntry(MobType.PIG, 10, 4, 4),
                    new SpawnEntry(MobType.CHICKEN, 10, 4, 4),
                    new SpawnEntry(MobType.COW, 8, 4, 4),
                    new SpawnEntry(MobType.HORSE, 5, 2, 6));
            case AMBIENT -> List.of(
                    // Biome line 292: the bat's own ambient row.
                    new SpawnEntry(MobType.BAT, 10, 8, 8));
            case WATER_CREATURE -> List.of(
                    // Biome line 277: the squid's own water row.
                    new SpawnEntry(MobType.SQUID, 10, 4, 4));
            default -> List.of();
        };
    }

    /** @return whether the cell holds water (the squid's gate reads the block). */
    private boolean isWaterAt(int x, int y, int z) {
        Object block = world.blockAt(new BlockPosition(x, y, z));
        return block != null && block.toString().contains("water");
    }

    // ------------------------------------------------------------------
    // The cycle (NaturalSpawner.tick 1:1)
    // ------------------------------------------------------------------

    /**
     * One spawn cycle (every tick — ServerWorld.tick line 180): the
     * spawning-chunk set rebuild, then per category the cap check and the
     * three-pack walk over every spawning chunk. The rare categories
     * (CREATURE — MobCategory's own {@code rare} flag) walk only on the
     * 400-tick cadence ({@code time % 400 == 0}, the reference's own
     * animal-spawn throttle).
     *
     * @param timeOfDay the world's time of day (the light walk's input)
     * @return how many mobs landed (the reference's own return).
     */
    public int tick(Iterable<PlayerSession> players, long timeOfDay) {
        return tick(players, timeOfDay, timeOfDay % 400L == 0L);
    }

    /** The cycle with the explicit rare flag (the 400-tick arm). */
    public int tick(Iterable<PlayerSession> players, long timeOfDay, boolean rareMobs) {
        // The spawning-chunk set (lines 34-53): the 17x17 grid around
        // every non-spectator player, the outer ring counted but excluded.
        spawningChunks.clear();
        int countedChunks = 0;
        for (PlayerSession player : players) {
            if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                    || player.dead()
                    || player.gamemode() == net.zaminmc.torch.GameMode.SPECTATOR) {
                continue;
            }
            int chunkX = (int) Math.floor(player.position().x() / 16.0);
            int chunkZ = (int) Math.floor(player.position().z() / 16.0);
            for (int dx = -SPAWN_RADIUS_CHUNKS; dx <= SPAWN_RADIUS_CHUNKS; dx++) {
                for (int dz = -SPAWN_RADIUS_CHUNKS; dz <= SPAWN_RADIUS_CHUNKS; dz++) {
                    boolean ring = dx == -SPAWN_RADIUS_CHUNKS || dx == SPAWN_RADIUS_CHUNKS
                            || dz == -SPAWN_RADIUS_CHUNKS || dz == SPAWN_RADIUS_CHUNKS;
                    long packed = pack(chunkX + dx, chunkZ + dz);
                    if (!spawningChunks.contains(packed)) {
                        countedChunks++;
                        if (!ring && world.isChunkLoaded(chunkX + dx, chunkZ + dz)) {
                            spawningChunks.add(packed);
                        }
                    }
                }
            }
        }
        if (spawningChunks.isEmpty()) {
            return 0;
        }

        int spawned = 0;
        Position worldSpawn = world.spawnPoint();
        int ambientDarkness = ambientDarkness(timeOfDay);

        for (Category category : Category.values()) {
            // The rare gate (lines 58-59): CREATURE (rare=true) walks only
            // every 400 ticks — the animal spawn throttle.
            if (category == Category.CREATURE && !rareMobs) {
                continue;
            }
            List<SpawnEntry> entries = entriesOf(category);
            if (entries.isEmpty()) {
                continue; // the ambient/water families: no mobs registered
            }
            // The cap (lines 59-61): count <= cap * chunks / 289.
            int count = mobs.countOf(category);
            int allowed = category.cap * countedChunks / CAP_CHUNK_AREA;
            if (count > allowed) {
                continue;
            }
            for (long packed : spawningChunks) {
                int chunkX = unpackX(packed);
                int chunkZ = unpackZ(packed);
                // The random anchor cell (getRandomPosInChunk lines
                // 152-160): the random x/z in the chunk, the y under the
                // heightmap's roundUp ceiling — the heightmap value is the
                // top solid y + 1, and the reference rounds (that + 1) up
                // to the next 16.
                int anchorX = chunkX * 16 + random.nextInt(16);
                int anchorZ = chunkZ * 16 + random.nextInt(16);
                int surface = world.surfaceHeight(anchorX, anchorZ);
                int yCeiling = roundUp(surface + 2, 16);
                if (yCeiling <= 0) {
                    yCeiling = 16; // the empty-column fallback
                }
                int anchorY = random.nextInt(yCeiling);
                // The initial cell must be non-solid (line 71).
                if (isSolidAt(anchorX, anchorY, anchorZ)) {
                    continue;
                }

                int packCount = 0;
                boolean chunkDone = false;
                for (int pack = 0; pack < 3 && !chunkDone; pack++) {
                    int x = anchorX;
                    int y = anchorY;
                    int z = anchorZ;
                    SpawnEntry entry = null;
                    for (int attempt = 0; attempt < 4 && !chunkDone; attempt++) {
                        // The drift (lines 84-86): +-6 horizontal, the y's
                        // nextInt(1) - nextInt(1) never moves (the quirk).
                        x += random.nextInt(DRIFT) - random.nextInt(DRIFT);
                        y += random.nextInt(1) - random.nextInt(1);
                        z += random.nextInt(DRIFT) - random.nextInt(DRIFT);
                        double centerX = x + 0.5;
                        double centerZ = z + 0.5;
                        // The keep-out rings (lines 91-92): no player within
                        // 24, no spawn within 24 of the world spawn.
                        if (playerWithin(players, centerX, y, centerZ, PLAYER_KEEP_OUT)) {
                            continue;
                        }
                        double sdx = centerX - worldSpawn.x();
                        double sdy = y - worldSpawn.y();
                        double sdz = centerZ - worldSpawn.z();
                        if (sdx * sdx + sdy * sdy + sdz * sdz < SPAWN_KEEP_OUT_SQUARED) {
                            continue;
                        }
                        // The entry picks once per pack (lines 93-99) —
                        // the position-aware resolution: inside the
                        // monument bounds the monster list IS the guardian
                        // rows (OverworldChunkGenerator.getSpawnEntries
                        // lines 451-454, Slice 17a); inside a fortress the
                        // nether's monster table IS the fortress rows
                        // (NetherChunkGenerator.getSpawnEntries lines
                        // 383-392, Slice 18a — the piece bounds, or the
                        // start bounds over nether bricks).
                        if (entry == null) {
                            List<SpawnEntry> candidates = entries;
                            if (category == Category.MONSTER) {
                                if (world.insideMonument(x, y, z)) {
                                    candidates = MONUMENT_MONSTER_ENTRIES;
                                } else if (world.insideFortress(x, y, z)) {
                                    candidates = FORTRESS_MONSTER_ENTRIES;
                                }
                            }
                            entry = pickWeighted(candidates);
                            if (entry == null) {
                                break;
                            }
                        }
                        BlockPosition cell = new BlockPosition(x, y, z);
                        if (!isValidSpawnCell(entry.type(), cell, ambientDarkness)) {
                            continue;
                        }
                        // The baby roll (HorseBaseEntity.initialize lines
                        // 1284-1286): the horse's own per-mob nextInt(5) —
                        // the ONLY animal that spawns young; every other
                        // passive lands grown.
                        boolean baby = entry.type() == MobType.HORSE
                                && random.nextInt(HORSE_BABY_ROLL) == 0;
                        // The reference's setPositionAndAngles (line 109): the
                        // walk's own yaw roll lands with the body.
                        float yaw = random.nextFloat() * 360.0F;
                        // The entity's own canSpawn gate (line 112 — after
                        // the yaw roll, before the initialize): the slime's
                        // swamp/chunk arms; every other kind carries none.
                        if (!canSpawnAt(entry.type(), x, y, z, chunkX, chunkZ)) {
                            continue;
                        }
                        mobs.spawn(entry.type(),
                                new Position(centerX, y, centerZ), baby, yaw);
                        packCount++;
                        if (packCount >= LIMIT_PER_CHUNK) {
                            chunkDone = true; // continue label129
                        }
                    }
                }
                spawned += packCount;
            }
        }
        return spawned;
    }

    // ------------------------------------------------------------------
    // The spawn cell (isValidSpawnPos + canSpawn 1:1)
    // ------------------------------------------------------------------

    /**
     * The cell rule (NaturalSpawner.isValidSpawnPos lines 137-157 +
     * the kinds' own canSpawn gates): the full-cube solid ground (no
     * bedrock), the air cell with the air above, and the light — the
     * animals need the grass floor and the {@code max(sky, block) > 8}
     * brightness, the monsters the randomized sky/block pair read
     * against the time-of-day darkness.
     */
    private boolean isValidSpawnCell(MobType type, BlockPosition cell, int ambientDarkness) {
        if (cell.y() <= 0) {
            return false; // the void floor: nothing below y=1 ever grounds a spawn
        }
        if (type == MobType.GUARDIAN) {
            // SpawnPlacement.ENVIRONMENTS (line 67): the guardian's own
            // IN_WATER environment — the liquid cell with the liquid below
            // and the non-solid above (isValidSpawnPos lines 150-155); no
            // light gate at all (canSpawnAtLightLevel answers true).
            return isFluidAt(cell.x(), cell.y(), cell.z())
                    && isFluidAt(cell.x(), cell.y() - 1, cell.z())
                    && !isSolidAt(cell.x(), cell.y() + 1, cell.z());
        }
        BlockPosition below = cell.offset(0, -1, 0);
        Object belowType = world.blockAt(below);
        if (!isFullCubeGround(belowType)) {
            return false;
        }
        // The bedrock floor refuses (line 152): no mob spawns on it.
        if (Objects.equals(identifierOf(belowType), "minecraft:bedrock")) {
            return false;
        }
        // The cell and the cell above must be air (lines 153-155).
        if (isSolidAt(cell.x(), cell.y(), cell.z())
                || isFluidAt(cell.x(), cell.y(), cell.z())) {
            return false;
        }
        if (isSolidAt(cell.x(), cell.y() + 1, cell.z())) {
            return false;
        }
        if (isHostile(type)) {
            // MonsterEntity.canSpawnAtLightLevel lines 112-127: the sky
            // light above a random 32 (the day gate), the raw brightness
            // (max(sky - darkness, block)) under a random 8.
            int sky = world.skyLightAt(cell);
            if (sky > random.nextInt(32)) {
                return false;
            }
            int brightness = Math.max(sky - ambientDarkness,
                    world.blockLightAt(cell));
            return brightness <= random.nextInt(8);
        }
        // AnimalEntity.canSpawn lines 90-95: the grass floor (the
        // mooshroom's mycelium rides its own slice) and the actual
        // light max(sky, block) over 8.
        if (!Objects.equals(identifierOf(belowType), "minecraft:grass_block")) {
            return false;
        }
        int actualLight = Math.max(world.skyLightAt(cell), world.blockLightAt(cell));
        return actualLight > 8;
    }

    /**
     * The entity-level spawn gates (the spawner's own consult, line 112):
     * only the slime carries one (SlimeEntity.canSpawn lines 235-259) —
     * the swampland band (the moon-phased night roll — the engine's biome
     * family carries no swamp, so the arm short-circuits before its
     * rolls exactly like a plains spawn in the reference) and the famous
     * slime-chunk arm: the shared {@code nextInt(10)}, then the chunk's
     * own seeded random ({@code WorldChunk.getRandomForSlime(987234911L)}
     * 1:1 — {@code seed + cx*cx*4987142 + cx*5947611 + cz*cz*4392871L +
     * cz*389711 ^ 987234911L}), then the {@code y < 40} depth gate.
     */
    private boolean canSpawnAt(MobType type, int x, int y, int z, int chunkX, int chunkZ) {
        if (type == MobType.SQUID) {
            // SquidEntity.canSpawn: the y 45-seaLevel band, in water.
            return y > 45 && y < 62
                    && isWaterAt(x, y, z);
        }
        if (type == MobType.GUARDIAN) {
            // GuardianEntity.canSpawn (line 418): the open-sky 1-in-20
            // roll — the covered position (the monument's interior, the
            // sky-light under 15 stand-in for the reference's
            // hasSkyAccessIgnoreLiquids) always passes.
            return random.nextInt(20) == 0
                    || world.skyLightAt(new BlockPosition(x, y, z)) < 15;
        }
        if (type != MobType.SLIME) {
            return true; // every other kind carries no entity-level gate
        }
        // The slime-chunk arm (line 253). The superflat refusal (line 238:
        // the 3-in-4 gate) never runs — the engine's world is normal.
        if (random.nextInt(10) != 0) {
            return false;
        }
        long slimeSeed = world.worldSeed()
                + (long) chunkX * chunkX * 4987142L
                + (long) chunkX * 5947611L
                + (long) chunkZ * chunkZ * 4392871L
                + (long) chunkZ * 389711L
                ^ 987234911L;
        return new Random(slimeSeed).nextInt(10) == 0 && y < 40;
    }

    /** The weighted pick (WeightedPicker.pick). */
    private SpawnEntry pickWeighted(List<SpawnEntry> entries) {
        int total = 0;
        for (SpawnEntry entry : entries) {
            total += entry.weight();
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (SpawnEntry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    /** The pack size (SpawnEntry's own min-max band). */
    private boolean isHostile(MobType type) {
        return type.hostile;
    }

    /** @return whether a player stands within the radius of the point. */
    private static boolean playerWithin(Iterable<PlayerSession> players,
                                        double x, double y, double z, double radius) {
        double squared = radius * radius;
        for (PlayerSession player : players) {
            if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                    || player.dead()
                    || player.gamemode() == net.zaminmc.torch.GameMode.SPECTATOR) {
                continue;
            }
            Position at = player.position();
            double dx = at.x() - x;
            double dy = at.y() - y;
            double dz = at.z() - z;
            if (dx * dx + dy * dy + dz * dz < squared) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The time-of-day light walk (the dimension + calculateAmbientDarkness)
    // ------------------------------------------------------------------

    /**
     * The ambient darkness (World.calculateAmbientDarkness lines
     * 1004-1013, the clear sky — the rain/thunder shaves ride the weather
     * slice): the dimension's own time-fraction walk, then the
     * {@code (int)((1 - clamp(1 - (2cos + 0.5))) * 11)} shape — 0 at
     * noon, 11 at midnight.
     */
    public static int ambientDarkness(long timeOfDay) {
        float fraction = celestialFraction(timeOfDay);
        float f1 = 1.0f - (float) (Math.cos(fraction * Math.PI * 2.0) * 2.0 + 0.5);
        f1 = Math.min(1.0f, Math.max(0.0f, f1));
        f1 = 1.0f - f1;
        f1 = 1.0f - f1; // no rain, no thunder (the clear-sky walk)
        return (int) (f1 * 11.0f);
    }

    /**
     * The dimension's time-of-day fraction (Dimension.getTimeOfDay lines
     * 76-89): the -0.25 offset with the cosine smoothing blend.
     */
    public static float celestialFraction(long timeOfDay) {
        int i = (int) (timeOfDay % 24000L);
        float f = i / 24000.0f - 0.25f;
        if (f < 0.0f) {
            f++;
        }
        if (f > 1.0f) {
            f--;
        }
        float base = f;
        f = 1.0f - (float) ((Math.cos(f * Math.PI) + 1.0) / 2.0);
        return base + (f - base) / 3.0f;
    }

    // ------------------------------------------------------------------
    // The world reads
    // ------------------------------------------------------------------

    private boolean isSolidAt(int x, int y, int z) {
        Object type = world.blockAt(new BlockPosition(x, y, z));
        return WorldSolidity.isSolid(blockTypeOf(type));
    }

    private boolean isFluidAt(int x, int y, int z) {
        Object type = world.blockAt(new BlockPosition(x, y, z));
        var blockType = blockTypeOf(type);
        return blockType != null
                && net.zaminmc.torch.server.block.FluidBlocks.isFluid(blockType.identifier());
    }

    /** The full-cube ground (World.hasSolidTop: solid + the full shape). */
    private boolean isFullCubeGround(Object type) {
        if (type == null) {
            return false;
        }
        var blockType = blockTypeOf(type);
        return WorldSolidity.isSolid(blockType)
                && !net.zaminmc.torch.server.block.WorldSolidity.isFence(blockType.identifier())
                && !isPartialShape(blockType);
    }

    /** The partial shapes (slabs/stairs) never answer the spawn floor. */
    private static boolean isPartialShape(net.zaminmc.torch.block.BlockType type) {
        String name = type.identifier().value();
        return name.endsWith("_slab") || name.endsWith("_stairs");
    }

    private static net.zaminmc.torch.block.BlockType blockTypeOf(Object type) {
        return type instanceof net.zaminmc.torch.block.BlockType casted ? casted : null;
    }

    private static Object identifierOf(Object type) {
        var blockType = blockTypeOf(type);
        return blockType == null ? null : blockType.identifier().toString();
    }

    private static int roundUp(int value, int step) {
        // MathHelper.roundUp: the ceiling to the step.
        return (value + step - 1) / step * step;
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    // ------------------------------------------------------------------
    // The despawn (MobEntity.checkDespawn 1:1)
    // ------------------------------------------------------------------

    /**
     * The per-mob despawn walk (MobEntity.checkDespawn lines 417-438):
     * the persistent never leave (the leashed family), and among the
     * despawnable ({@code canDespawn()} — the animals' own override
     * returns false, AnimalEntity lines 104-106: a penned cow never
     * leaves) the beyond-128 (16384 squared) removal at once and the
     * past-600-tick 1/800 idle roll beyond 32 blocks (1024 squared).
     *
     * @param canDespawn the mob family's own gate ({@code
     *                   MobEntity.canDespawn}: monsters true, animals
     *                   false)
     * @return true when the mob must leave the world now.
     */
    public static boolean shouldDespawn(boolean persistent, boolean canDespawn,
                                         double squaredNearest, int farFromPlayerTicks,
                                         Random random) {
        if (persistent) {
            return false; // line 418: the persistent never roll
        }
        if (canDespawn && squaredNearest > 16384.0) {
            return true; // line 427: the 128-block instant removal
        }
        if (farFromPlayerTicks > 600 && random.nextInt(800) == 0
                && squaredNearest > 1024.0 && canDespawn) {
            return true; // line 431: the idle roll
        }
        return false;
    }
}
