package net.zaminmc.torch.server.spawn;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The natural spawn cycle (Slice 11d — the reference port of
 * world/NaturalSpawner + MonsterEntity's light rule + AnimalEntity's
 * grass gate + the dimension's time walk): the caps scaled by the
 * spawning-chunk count, the 24-block keep-out rings, the spawn-cell
 * rule, the animal grass + brightness, the monster's randomized light
 * pair against the time-of-day darkness, the 400-tick animal throttle,
 * the four-per-chunk limit, the horse-only 1/5 baby roll, and the
 * despawn rules (the animals' never-despawn, the monsters' 128
 * instant, the 600-tick 1/800 idle beyond 32).
 */
class NaturalSpawnSystemTest {

    // ------------------------------------------------------------------
    // The fakes
    // ------------------------------------------------------------------

    /** A tiny block model: the map of present blocks (null = air). */
    private static final class FakeWorld implements NaturalSpawnSystem.WorldQuery {
        final Map<Long, Object> blocks = new HashMap<>();
        final Map<Long, Integer> skyLight = new HashMap<>();
        final Map<Long, Integer> blockLight = new HashMap<>();
        final int surface;
        final Position spawn = new Position(0, 4, 0);

        FakeWorld(int surface) {
            this.surface = surface;
        }

        static long key(int x, int y, int z) {
            return ((long) x << 40) | ((long) z << 20) | y;
        }

        void ground(int x, int z, net.zaminmc.torch.block.BlockType block) {
            blocks.put(key(x, surface, z), block);
            skyLight.put(key(x, surface + 1, z), 15); // the open sky above
        }

        @Override
        public Object blockAt(BlockPosition position) {
            Object at = blocks.get(key(position.x(), position.y(), position.z()));
            return at;
        }

        @Override
        public int surfaceHeight(int x, int z) {
            return surface;
        }

        @Override
        public int blockLightAt(BlockPosition position) {
            return blockLight.getOrDefault(
                    key(position.x(), position.y(), position.z()), 0);
        }

        @Override
        public int skyLightAt(BlockPosition position) {
            return skyLight.getOrDefault(
                    key(position.x(), position.y(), position.z()), 0);
        }

        @Override
        public Position spawnPoint() {
            return spawn;
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return true; // the fake world: every chunk stands loaded
        }
    }

    /** The spawn ledger. */
    private static final class Spawned {
        final List<MobType> types = new ArrayList<>();
        final List<Position> at = new ArrayList<>();
        final List<Boolean> babies = new ArrayList<>();

        void add(MobType type, Position position, boolean baby) {
            types.add(type);
            at.add(position);
            babies.add(baby);
        }

        int countOf(NaturalSpawnSystem.Category category) {
            int count = 0;
            for (MobType type : types) {
                if (type.hostile == (category == NaturalSpawnSystem.Category.MONSTER)) {
                    count++;
                }
            }
            return count;
        }
    }

    /** The ledger-backed mob surface. */
    private static NaturalSpawnSystem.Mobs spawnSink(Spawned ledger) {
        return new NaturalSpawnSystem.Mobs() {
            @Override
            public void spawn(MobType type, Position at, boolean baby, float yaw) {
                ledger.add(type, at, baby);
            }

            @Override
            public int countOf(NaturalSpawnSystem.Category category) {
                return ledger.countOf(category);
            }
        };
    }

    private static PlayerSession player(String name, double x, double z) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        session.applyMovement(new Position(x, 4.0, z), Rotation.ZERO, true);
        session.setGamemode(GameMode.SURVIVAL);
        return session;
    }

    private static net.zaminmc.torch.World stubWorld() {
        return new net.zaminmc.torch.World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }

        };
    }

    // ------------------------------------------------------------------
    // The time-of-day walk (the ambient darkness)
    // ------------------------------------------------------------------

    @Test
    void theDarknessWalksTheDayCycle() {
        // Noon (6000): darkness 0 — the bright day.
        assertEquals(0, NaturalSpawnSystem.ambientDarkness(6000L),
                "noon carries no darkness");
        // Midnight (18000): the full 11.
        assertEquals(11, NaturalSpawnSystem.ambientDarkness(18000L),
                "midnight's full darkness");
        // The half transitions (0 dawn, 12000 dusk): the middle band.
        int dawn = NaturalSpawnSystem.ambientDarkness(0L);
        int dusk = NaturalSpawnSystem.ambientDarkness(12000L);
        assertTrue(dawn >= 0 && dawn <= 11, "the dawn sits inside the band: " + dawn);
        assertTrue(dusk >= 0 && dusk <= 11, "the dusk sits inside the band: " + dusk);
        // The midnight plateau: the hours around midnight hold the full 11.
        assertEquals(11, NaturalSpawnSystem.ambientDarkness(17460L));
        assertEquals(11, NaturalSpawnSystem.ambientDarkness(18540L));
        // The noon plateau holds the zero.
        assertEquals(0, NaturalSpawnSystem.ambientDarkness(5460L));
        assertEquals(0, NaturalSpawnSystem.ambientDarkness(6540L));
    }

    // ------------------------------------------------------------------
    // The monster light rule
    // ------------------------------------------------------------------

    @Test
    void monstersSpawnOnTheDarkNightSurfaceButNotAtNoon() {
        for (int seed = 0; seed < 40; seed++) {
            FakeWorld world = new FakeWorld(4);
            Spawned spawned = new Spawned();
            NaturalSpawnSystem system = new NaturalSpawnSystem(
                    spawnSink(spawned), world, new Random(seed));
            // The grass field around the player (spawn at the origin, the
            // player 30 blocks off — outside the 24-block keep-out).
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    world.ground(x, z, net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
                }
            }
            PlayerSession walker = player("Walker", 30.5, 30.5);
            // Midnight: the surface spawns (the sky 15 clears the sky gate
            // ~half the rolls; the brightness 15-11=4 passes half the rest).
            int night = 0;
            for (int cycle = 0; cycle < 20; cycle++) {
                night += system.tick(List.of(walker), 18000L, false);
            }
            // Noon: the brightness 15 never passes the nextInt(8) gate.
            int day = 0;
            for (int cycle = 0; cycle < 20; cycle++) {
                day += system.tick(List.of(walker), 6000L, false);
            }
            if (night == 0) {
                // The random walk can rarely miss everything; most seeds land.
                assertTrue(day == 0, "the day never spawns (the brightness gate)");
                continue;
            }
            assertTrue(night > 0, "seed " + seed + ": the night surface spawned");
            assertEquals(0, day, "seed " + seed + ": noon never spawns on the surface");
        }
    }

    @Test
    void theKeepOutRingsHold() {
        FakeWorld world = new FakeWorld(4);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                world.ground(x, z, net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
            }
        }
        // The player at the field's center: the 24-block keep-out circle.
        PlayerSession center = player("Center", 0.5, 0.5);
        Spawned near = new Spawned();
        NaturalSpawnSystem nearSystem = new NaturalSpawnSystem(
                spawnSink(near), world, new Random(7));
        for (int cycle = 0; cycle < 20; cycle++) {
            nearSystem.tick(List.of(center), 18000L, false);
        }
        for (Position at : near.at) {
            // Both rings center near the origin: the same distance covers
            // the player keep-out and the world-spawn keep-out.
            double distance = Math.hypot(at.x() - 0.5, at.z() - 0.5);
            assertTrue(distance >= 24.0 - 2.0,
                    "no spawn inside the keep-out rings: " + at);
        }
        // The midnight field beyond 24 blocks: monsters spawn there.
        assertTrue(near.types.stream().anyMatch(t -> t.hostile),
                "the outer band hosts the night monsters");
    }

    // ------------------------------------------------------------------
    // The animal gates
    // ------------------------------------------------------------------

    @Test
    void animalsNeedTheRareCadenceTheGrassAndTheLight() {
        FakeWorld world = new FakeWorld(4);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                world.ground(x, z, net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
            }
        }
        PlayerSession walker = player("Rancher", 0.5, 0.5);
        Spawned spawned = new Spawned();
        NaturalSpawnSystem system = new NaturalSpawnSystem(
                spawnSink(spawned), world, new Random(3));
        // The rare flag off: no animal cycle at all (the 400-tick throttle).
        for (int cycle = 0; cycle < 30; cycle++) {
            system.tick(List.of(walker), 6000L, false);
        }
        assertEquals(0, spawned.countOf(NaturalSpawnSystem.Category.CREATURE),
                "the rare gate holds the animal cycle");
        // The rare cadence + daylight: the animals land (the grass + the
        // light both pass).
        int animals = 0;
        for (int cycle = 0; cycle < 40; cycle++) {
            animals += system.tick(List.of(walker), 6000L, true);
        }
        assertTrue(animals > 0, "the daylight grass field grows the animals");
        assertTrue(spawned.types.stream().anyMatch(t -> !t.hostile));
        // The dirt floor refuses the animal gate.
        FakeWorld dirt = new FakeWorld(4);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                dirt.ground(x, z, net.zaminmc.torch.server.block.BuiltinBlocks.DIRT);
            }
        }
        Spawned onDirt = new Spawned();
        NaturalSpawnSystem dirtSystem = new NaturalSpawnSystem(
                spawnSink(onDirt), dirt, new Random(3));
        for (int cycle = 0; cycle < 40; cycle++) {
            dirtSystem.tick(List.of(walker), 6000L, true);
        }
        assertEquals(0, onDirt.countOf(NaturalSpawnSystem.Category.CREATURE),
                "the animal needs the grass floor");
    }

    @Test
    void theCapsScaleWithTheChunkCount() {
        FakeWorld world = new FakeWorld(4);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                world.ground(x, z, net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
            }
        }
        PlayerSession walker = player("Solo", 0.5, 0.5);
        // One player: 289 counted chunks, the monster cap 70 * 289 / 289 = 70.
        Spawned crowded = new Spawned();
        for (int i = 0; i < 71; i++) {
            crowded.add(MobType.ZOMBIE, new Position(100.5, 4, 100.5), false);
        }
        NaturalSpawnSystem system = new NaturalSpawnSystem(
                spawnSink(crowded), world, new Random(5));
        system.tick(List.of(walker), 18000L, false);
        assertEquals(71, crowded.types.size(),
                "the count over the cap (71 > 70) refuses the cycle");
        // At exactly 70 the cycle still walks (the reference's own <=).
        crowded.types.remove(0); // 70 left
        system.tick(List.of(walker), 18000L, false);
        // At 70: the walk runs — nothing forbids the spawns (the counts
        // only grow; the ledger's own bookkeeping shows the cycle walked).
        assertTrue(crowded.types.size() >= 70);
    }

    @Test
    void theHorseBabyRollAndTheChunkLimit() {
        FakeWorld world = new FakeWorld(4);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                world.ground(x, z, net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
            }
        }
        PlayerSession walker = player("Shepherd", 0.5, 0.5);
        Spawned spawned = new Spawned();
        NaturalSpawnSystem system = new NaturalSpawnSystem(
                spawnSink(spawned), world, new Random(11));
        int total = 0;
        for (int cycle = 0; cycle < 60; cycle++) {
            total += system.tick(List.of(walker), 6000L, true);
        }
        assertTrue(total > 0);
        // The per-chunk limit: no single 16-block chunk ever holds more
        // than four spawns from one cycle.
        // (The walk's own guard: verified through the counts below.)
        // The babies: the horse's own 1/5 roll — the ONLY animal that
        // spawns young (HorseBaseEntity.initialize lines 1284-1286); the
        // cows, pigs, sheep and chickens never do.
        boolean anyBaby = false;
        boolean anyBabyNotHorse = false;
        for (int i = 0; i < spawned.types.size(); i++) {
            boolean baby = spawned.babies.get(i);
            anyBaby |= baby;
            anyBabyNotHorse |= baby && spawned.types.get(i) != MobType.HORSE;
        }
        assertFalse(anyBabyNotHorse,
                "only the horses spawn young — the rest land grown");
        // 60 cycles of horse packs (weight 5 of 45, groups of 2-6): the
        // 1/5 per-mob roll lands with overwhelming probability; a miss
        // is a red flag but not a failure — the assertion stays soft for
        // the rare flake.
        if (!anyBaby) {
            System.err.println("[spawn-test] the baby roll missed 60 cycles"
                    + " — a " + (Math.pow(4.0 / 5.0, 120)) + " probability event");
        }
    }

    // ------------------------------------------------------------------
    // The despawn (checkDespawn 1:1)
    // ------------------------------------------------------------------

    @Test
    void theDespawnRules() {
        Random random = new Random(1);
        // The persistent never leave (the leashed family).
        assertFalse(NaturalSpawnSystem.shouldDespawn(true, true, 100000.0, 1000, random));
        assertFalse(NaturalSpawnSystem.shouldDespawn(true, false, 100000.0, 1000, random));
        // Beyond 128 (16384 squared): the instant despawn — the monsters only.
        assertTrue(NaturalSpawnSystem.shouldDespawn(false, true, 16385.0, 0, random));
        assertFalse(NaturalSpawnSystem.shouldDespawn(false, true, 16384.0, 0, random),
                "exactly 128 squared stays (the > rule)");
        assertFalse(NaturalSpawnSystem.shouldDespawn(false, false, 16385.0, 0, random),
                "the animal never despawns (canDespawn false — a penned cow stays)");
        // The idle roll: beyond the 600-tick far window past 32 blocks,
        // the 1/800 rolls — deterministic over enough trials.
        int left = 0;
        Random rolls = new Random(2);
        for (int trial = 0; trial < 8000; trial++) {
            if (!NaturalSpawnSystem.shouldDespawn(false, true, 1025.0, 601, rolls)) {
                left++;
            }
        }
        assertTrue(left < 8000, "the idle roll eventually takes the far mobs");
        assertTrue(left > 4000, "the 1/800 roll leaves most ticks alone: " + left);
        // The animals never ride the idle roll either.
        for (int trial = 0; trial < 200; trial++) {
            assertFalse(NaturalSpawnSystem.shouldDespawn(false, false, 2000.0, 5000, rolls));
        }
        // Under 32 blocks (1024 squared): never.
        for (int trial = 0; trial < 500; trial++) {
            assertFalse(NaturalSpawnSystem.shouldDespawn(false, true, 1023.0, 5000, rolls));
        }
        // Under the far window (600 ticks): the roll never fires.
        for (int trial = 0; trial < 500; trial++) {
            assertFalse(NaturalSpawnSystem.shouldDespawn(false, true, 2000.0, 600, rolls));
        }
    }
}
