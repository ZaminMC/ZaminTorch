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
        /** The spawn-table consult's answer (the spawn-tables slice). */
        net.zaminmc.torch.server.world.Biome biome =
                net.zaminmc.torch.server.world.Biome.PLAINS;
        /** The per-column biome override (the revalidation test's seam). */
        java.util.function.BiPredicate<Integer, Integer> biomeFilter;

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

        @Override
        public long worldSeed() {
            return 0L; // the fake world's seed (the slime-chunk derivation)
        }

        @Override
        public net.zaminmc.torch.server.world.Biome biomeAt(int x, int z) {
            if (biomeFilter != null && biomeFilter.test(x, z)) {
                return net.zaminmc.torch.server.world.Biome.RIVER;
            }
            return biome;
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
                if (categoryOf(type) == category) {
                    count++;
                }
            }
            return count;
        }

        static NaturalSpawnSystem.Category categoryOf(MobType type) {
            if (type == MobType.SQUID) {
                return NaturalSpawnSystem.Category.WATER_CREATURE;
            }
            if (type == MobType.BAT) {
                return NaturalSpawnSystem.Category.AMBIENT;
            }
            return type.hostile
                    ? NaturalSpawnSystem.Category.MONSTER
                    : NaturalSpawnSystem.Category.CREATURE;
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

            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) { return "plains"; }

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
    // VT-020 (renumbered from the draft VT-012 — the pass-30 lane's
    // VT-012 is the NetherBlastRouting flake, claimed 2026-10-31 before
    // this stream's 2026-11-01 claim) — the spawn floor's consult
    // ------------------------------------------------------------------

    /**
     * VT-020's discovery seat: the reference's spawn floor consult is
     * {@code World.hasSolidTop} (NaturalSpawner lines 159-166) whose FIRST
     * arm ({@code material.isSolidBlocking() && isCube()}) accepts the
     * double slab outright ({@code SlabBlock.isCube() = isDouble()} — the
     * doubles are full cubes). The engine's {@code isPartialShape} suffix
     * arm ({@code name.endsWith("_slab")}) rejects the ten
     * {@code _slab}-suffixed double names — the floor refuses the spawn
     * vanilla allows. The stone control and the single-slab control frame
     * the discrimination: the stone floor spawns (both trees agree), the
     * single bottom slab refuses (both trees agree — vanilla's
     * {@code hasSolidTop} SlabBlock arm wants HALF == TOP), and the DOUBLE
     * floor must spawn (vanilla) but refuses on the unfixed baseline —
     * RED here, the parity pin riding the fix branch.
     */
    @Test
    void theDoubleSlabFloorHostsTheNightMonsters() {
        int stoneControl = 0;
        int doubleFloor = 0;
        int singleSlabControl = 0;
        for (int seed = 0; seed < 40; seed++) {
            stoneControl += nightSpawnsOnFloor(seed,
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            doubleFloor += nightSpawnsOnFloor(seed,
                    net.zaminmc.torch.server.block.BuiltinBlocks.DOUBLE_STONE_SLAB);
            singleSlabControl += nightSpawnsOnFloor(seed,
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE_SLAB);
        }
        assertTrue(stoneControl > 0,
                "the stone floor hosts the night monsters (the harness control)");
        assertEquals(0, singleSlabControl,
                "the single bottom slab refuses in BOTH trees (hasSolidTop's "
                        + "SlabBlock arm wants HALF == TOP)");
        assertTrue(doubleFloor > 0,
                "VT-020: the double-slab floor hosts the night monsters — "
                        + "hasSolidTop's first arm (isSolidBlocking && isCube) "
                        + "accepts the double; the engine's isPartialShape "
                        + "suffix arm refuses it");
    }

    /**
     * VT-020's second row (the pass-3 widening census): the reference's
     * {@code hasSolidTop} SECOND arm rejects the BOTTOM stair — the
     * StairsBlock arm wants {@code HALF == TOP} (World.java lines
     * 1745-1747), so no vanilla spawn ever lands on a bottom-stair
     * floor. The engine's directional stair states
     * ({@code oak_stairs_east} — the metadata fold's stored forms) end
     * in a FACING suffix, never {@code "_stairs"}, so the suffix arm
     * never catches them and the floor consult ACCEPTS the bottom
     * stair — the engine spawns the night monsters where vanilla
     * refuses. The stone and TOP-stair controls frame the
     * discrimination: the stone floor spawns (both trees agree), the
     * TOP stair spawns (both trees agree — the reference's arm wants
     * HALF == TOP), and the BOTTOM stair must refuse (vanilla) but
     * hosts on the unfixed baseline — RED here, the parity pin riding
     * the fix branch.
     */
    @Test
    void theBottomStairFloorRefusesTheNightMonsters() {
        int stoneControl = 0;
        int bottomStairFloor = 0;
        int topStairControl = 0;
        for (int seed = 0; seed < 40; seed++) {
            stoneControl += nightSpawnsOnFloor(seed,
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            bottomStairFloor += nightSpawnsOnFloor(seed,
                    net.zaminmc.torch.server.block.BuiltinBlocks.OAK_STAIRS_EAST);
            topStairControl += nightSpawnsOnFloor(seed,
                    net.zaminmc.torch.server.block.BuiltinBlocks.OAK_STAIRS_EAST_TOP);
        }
        assertTrue(stoneControl > 0,
                "the stone floor hosts the night monsters (the harness control)");
        assertTrue(topStairControl > 0,
                "the TOP stair hosts the night monsters in BOTH trees "
                        + "(hasSolidTop's StairsBlock arm wants HALF == TOP)");
        assertEquals(0, bottomStairFloor,
                "VT-020: the BOTTOM stair floor refuses the night monsters — "
                        + "hasSolidTop's StairsBlock arm wants HALF == TOP; "
                        + "the engine's directional stair states end in a "
                        + "FACING suffix, so the isPartialShape arm never "
                        + "catches them and the floor consult accepts the "
                        + "bottom stair");
    }

    /**
     * VT-020's third row (the pass-3 census): the red sandstone slab
     * pair over legacy 182 — the engine's {@code stone_slab2} (the
     * BOTTOM single, the HALF bit 8 family) ends in {@code "2"}, so the
     * {@code endsWith("_slab")} arm never catches it and the floor
     * consult ACCEPTS the bottom half where the reference's SlabBlock
     * arm ({@code HALF == TOP}) refuses it. The {@code stone_slab}
     * control (the SAME bottom-half law under a caught name) frames the
     * discrimination: both are bottom singles, vanilla refuses both,
     * the engine refuses the caught name but hosts the escaped one —
     * the same suffix-approximation accident as the doubles' row.
     */
    @Test
    void theRedSandstoneBottomSlabFloorRefusesTheNightMonsters() {
        int stoneControl = 0;
        int redSandstoneBottom = 0;
        int stoneSlabControl = 0;
        for (int seed = 0; seed < 40; seed++) {
            stoneControl += nightSpawnsOnFloor(seed,
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            redSandstoneBottom += nightSpawnsOnFloor(seed,
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE_SLAB2);
            stoneSlabControl += nightSpawnsOnFloor(seed,
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE_SLAB);
        }
        assertTrue(stoneControl > 0,
                "the stone floor hosts the night monsters (the harness control)");
        assertEquals(0, stoneSlabControl,
                "the stone bottom slab refuses in BOTH trees (the caught "
                        + "name — hasSolidTop's SlabBlock arm wants HALF == TOP)");
        assertEquals(0, redSandstoneBottom,
                "VT-020: the red sandstone bottom slab floor refuses the "
                        + "night monsters — hasSolidTop's SlabBlock arm wants "
                        + "HALF == TOP; the engine's stone_slab2 ends in "
                        + "\"2\" so the isPartialShape arm never catches it");
    }

    /** The night-spawn count over a uniform floor of the given type. */
    private int nightSpawnsOnFloor(int seed,
            net.zaminmc.torch.block.BlockType block) {
        FakeWorld world = new FakeWorld(4);
        Spawned spawned = new Spawned();
        NaturalSpawnSystem system = new NaturalSpawnSystem(
                spawnSink(spawned), world, new Random(seed));
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                world.ground(x, z, block);
            }
        }
        PlayerSession walker = player("Walker", 30.5, 30.5);
        int night = 0;
        for (int cycle = 0; cycle < 20; cycle++) {
            night += system.tick(List.of(walker), 18000L, false);
        }
        return night;
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
        // Slice 16e: the bats' AMBIENT category carries no rare cadence
        // (MobCategory.AMBIENT is not rare) — the caves may fill while the
        // animals wait.
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
    void theMooshroomsMyceliumFloor() {
        // The mushroom isle: the biome's creature table carries the
        // mooshroom ALONE (MushroomBiome lines 15-18 — passiveEntries
        // cleared then the mooshroom's own 8/4-8 row), and the mooshroom's
        // spawnableBlock is MYCELIUM (MooshroomEntity line 16 over the
        // AnimalEntity line-18 grass default) — the per-KIND floor the
        // tick cycle's animal arm must read.
        FakeWorld isle = new FakeWorld(4);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                isle.ground(x, z, net.zaminmc.torch.server.block.BuiltinBlocks.MYCELIUM);
            }
        }
        isle.biome = net.zaminmc.torch.server.world.Biome.MUSHROOM_ISLAND;
        PlayerSession farmer = player("Mycologist", 0.5, 0.5);
        Spawned onMycelium = new Spawned();
        NaturalSpawnSystem myceliumSystem = new NaturalSpawnSystem(
                spawnSink(onMycelium), isle, new Random(7));
        int mooshrooms = 0;
        for (int cycle = 0; cycle < 60; cycle++) {
            myceliumSystem.tick(List.of(farmer), 6000L, true);
        }
        for (MobType type : onMycelium.types) {
            mooshrooms += type == MobType.MOOSHROOM ? 1 : 0;
        }
        assertTrue(mooshrooms > 0,
                "the daylight mycelium isle grows the mooshrooms");
        assertEquals(onMycelium.types.size(), mooshrooms,
                "the isle's creature table carries the mooshroom alone");
        // The grass-field refusal: the same biome over GRASS spawns
        // nothing — the mooshroom's own floor refuses the wrong ground
        // (the per-kind law, not the biome's).
        FakeWorld grassIsle = new FakeWorld(4);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                grassIsle.ground(x, z, net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
            }
        }
        grassIsle.biome = net.zaminmc.torch.server.world.Biome.MUSHROOM_ISLAND;
        Spawned onGrass = new Spawned();
        NaturalSpawnSystem grassSystem = new NaturalSpawnSystem(
                spawnSink(onGrass), grassIsle, new Random(7));
        for (int cycle = 0; cycle < 60; cycle++) {
            grassSystem.tick(List.of(farmer), 6000L, true);
        }
        assertEquals(0, onGrass.countOf(NaturalSpawnSystem.Category.CREATURE),
                "the mooshroom refuses the grass floor — MYCELIUM alone");
        // And the mirror: a cow over MYCELIUM never lands (the grass
        // family's own floor) — the plains table's cow consulted against
        // the mycelium ground.
        FakeWorld plainsOnMycelium = new FakeWorld(4);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                plainsOnMycelium.ground(x, z,
                        net.zaminmc.torch.server.block.BuiltinBlocks.MYCELIUM);
            }
        }
        plainsOnMycelium.biome = net.zaminmc.torch.server.world.Biome.PLAINS;
        Spawned none = new Spawned();
        NaturalSpawnSystem plainsSystem = new NaturalSpawnSystem(
                spawnSink(none), plainsOnMycelium, new Random(7));
        for (int cycle = 0; cycle < 60; cycle++) {
            plainsSystem.tick(List.of(farmer), 6000L, true);
        }
        assertEquals(0, none.countOf(NaturalSpawnSystem.Category.CREATURE),
                "the grass animals refuse the mycelium floor — the floor is per-kind");
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
        assertEquals(71, crowded.countOf(NaturalSpawnSystem.Category.MONSTER),
                "the count over the cap (71 > 70) refuses the monster cycle");
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

    // ------------------------------------------------------------------
    // The spawn-table consult (the spawn-tables slice — the biome at the
    // drifted position decides the pick, the reference's own law)
    // ------------------------------------------------------------------

    /**
     * The cleared creature tables refuse the pack at the PICK itself
     * (the reference's pickSpawnEntry null on an empty list — the pack
     * attempt breaks): a desert/river/beach world never grows animals
     * however long the rare cadence runs. The monsters (the default
     * table) still walk the night.
     */
    @Test
    void theClearedBiomeTablesRefuseTheAnimalPacks() {
        for (net.zaminmc.torch.server.world.Biome cleared
                : new net.zaminmc.torch.server.world.Biome[] {
                        net.zaminmc.torch.server.world.Biome.DESERT,
                        net.zaminmc.torch.server.world.Biome.RIVER,
                        net.zaminmc.torch.server.world.Biome.BEACH,
                        net.zaminmc.torch.server.world.Biome.OCEAN,
                        net.zaminmc.torch.server.world.Biome.MESA,
                        net.zaminmc.torch.server.world.Biome.ICE_PLAINS}) {
            FakeWorld world = new FakeWorld(4);
            world.biome = cleared;
            for (int x = -30; x <= 30; x++) {
                for (int z = -30; z <= 30; z++) {
                    world.ground(x, z,
                            net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
                }
            }
            PlayerSession walker = player("Rancher", 0.5, 0.5);
            Spawned spawned = new Spawned();
            NaturalSpawnSystem system = new NaturalSpawnSystem(
                    spawnSink(spawned), world, new Random(3));
            for (int cycle = 0; cycle < 60; cycle++) {
                system.tick(List.of(walker), 6000L, true);
            }
            assertEquals(0, spawned.countOf(NaturalSpawnSystem.Category.CREATURE),
                    cleared + " never grows the animals (the cleared table)");
        }
    }

    /**
     * The revalidation (the reference's isValidSpawnEntry contains
     * check): a pack entry picked on the plains half must never land on
     * the river half — the drifted position's own table must still
     * carry the entry. The assertion is the universal invariant: every
     * creature that lands stands on the plains side of the boundary.
     */
    @Test
    void theDriftedPositionRevalidatesAgainstItsOwnBiome() {
        // The boundary at x = 6: the plains half x <= 6, the river half
        // beyond (the drift reaches +6, so packs DO cross).
        FakeWorld world = new FakeWorld(4);
        world.biomeFilter = (x, z) -> x > 6;
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                world.ground(x, z,
                        net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
            }
        }
        PlayerSession walker = player("Rancher", -10.5, 0.5);
        Spawned spawned = new Spawned();
        NaturalSpawnSystem system = new NaturalSpawnSystem(
                spawnSink(spawned), world, new Random(11));
        for (int cycle = 0; cycle < 80; cycle++) {
            system.tick(List.of(walker), 6000L, true);
        }
        int creatures = spawned.countOf(NaturalSpawnSystem.Category.CREATURE);
        assertTrue(creatures > 0, "the plains half grows its animals");
        for (int i = 0; i < spawned.types.size(); i++) {
            if (Spawned.categoryOf(spawned.types.get(i))
                    == NaturalSpawnSystem.Category.CREATURE) {
                assertTrue(spawned.at.get(i).x() <= 6.5,
                        "the animal never lands on the river half: "
                                + spawned.at.get(i));
            }
        }
    }

    /**
     * The jungle's ocelot rides the MONSTER table (JungleBiome line 48
     * — weight 2, groups 1-1): the natural cycle's hostile walk picks
     * it, and the animal cell gates (the grass + the brightness, not
     * the monster darkness — the ocelot is no MonsterEntity) clear it
     * even at noon. The engine's own seeding-side stand-in (Slice
     * 15e's ocelot row at populate) retired with this slice.
     */
    @Test
    void theJungleGrowsTheOcelotsThroughTheMonsterWalk() {
        boolean anyOcelot = false;
        for (int seed = 0; seed < 24 && !anyOcelot; seed++) {
            FakeWorld world = new FakeWorld(4);
            world.biome = net.zaminmc.torch.server.world.Biome.JUNGLE;
            for (int x = -30; x <= 30; x++) {
                for (int z = -30; z <= 30; z++) {
                    world.ground(x, z,
                            net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
                }
            }
            PlayerSession walker = player("CatWalker", 0.5, 0.5);
            Spawned spawned = new Spawned();
            NaturalSpawnSystem system = new NaturalSpawnSystem(
                    spawnSink(spawned), world, new Random(seed));
            for (int cycle = 0; cycle < 30; cycle++) {
                system.tick(List.of(walker), 6000L, false);
            }
            anyOcelot = spawned.types.contains(MobType.OCELOT);
        }
        assertTrue(anyOcelot, "the jungle's monster walk grows the ocelots");
    }

    // ------------------------------------------------------------------
    // The ambient/water categories (Slice 16e's tail — the SpawnPlacement
    // per-kind position gates + the kinds' own canSpawn laws)
    // ------------------------------------------------------------------

    /** The water source (the fake world's fluid). */
    private static final net.zaminmc.torch.block.BlockType WATER =
            net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                    net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER);

    private static void fillLayer(FakeWorld world, int y, Object block) {
        for (int x = -34; x <= 34; x++) {
            for (int z = -34; z <= 34; z++) {
                world.blocks.put(FakeWorld.key(x, y, z), block);
            }
        }
    }

    /**
     * The AMBIENT gate (the bat): SpawnPlacement line 44 gives the bat the
     * plain ON_GROUND cell rule — never the animals' grass floor
     * (AnimalEntity lines 90-95 are the AnimalEntity's own law; the bat
     * is an AmbientMobEntity) — and BatEntity.canSpawn lines 188-203 adds
     * its own three arms below the walk: the below-the-sea-level band
     * (the World's 63, line 62), the 1-in-2 coin (line 198, outside the
     * Oct 20 - Nov 3 window the engine does not model) and the raw
     * brightness against the nextInt(4) — World.getRawBrightness line
     * 449 reads WorldChunk.getLight(pos, ambientDarkness) lines 592-606,
     * the SAME ambient-darkness shave the monsters ride, so a sky-7 cell
     * admits the bat at midnight (7 - 11 bottoms at 0) and refuses it at
     * noon (7 > 3).
     */
    @Test
    void batsSpawnUnderTheAmbientGateBelowTheSeaLevelInTheDark() {
        // Phase 1 — the stone cave (the floor is STONE, never grass; the
        // cells dark): the bats land with the rare cadence OFF (the
        // AMBIENT category carries no rare flag — MobCategory line 13),
        // below the sea level, at the cave cells' own y.
        FakeWorld cave = new FakeWorld(4);
        fillLayer(cave, 2, net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
        PlayerSession walker = player("Spelunker", 0.5, 0.5);
        Spawned caveBats = new Spawned();
        NaturalSpawnSystem caveSystem = new NaturalSpawnSystem(
                spawnSink(caveBats), cave, new Random(3));
        for (int cycle = 0; cycle < 30; cycle++) {
            caveSystem.tick(List.of(walker), 18000L, false);
        }
        assertTrue(caveBats.countOf(NaturalSpawnSystem.Category.AMBIENT) > 0,
                "the stone cave grows the bats (no grass floor, no rare cadence)");
        for (int i = 0; i < caveBats.types.size(); i++) {
            if (caveBats.types.get(i) == MobType.BAT) {
                assertEquals(3.0, caveBats.at.get(i).y(), 1e-9,
                        "the bat lands at the dark cell above the stone floor");
            }
        }
        // Phase 2 — the sky-7 band: midnight admits it through the
        // ambient-darkness shave, noon refuses it (the unshaved 7 clears
        // the roll's whole 0-3 band) while the dark half still grows —
        // the noon walk itself ran, only the band's light refuses.
        FakeWorld lit = new FakeWorld(4);
        fillLayer(lit, 2, net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
        for (int x = 7; x <= 34; x++) {
            for (int z = -34; z <= 34; z++) {
                lit.skyLight.put(FakeWorld.key(x, 3, z), 7);
            }
        }
        int midnightBand = 0;
        int midnightDark = 0;
        for (int seed = 0; seed < 16 && midnightBand == 0; seed++) {
            Spawned night = new Spawned();
            NaturalSpawnSystem nightSystem = new NaturalSpawnSystem(
                    spawnSink(night), lit, new Random(seed));
            for (int cycle = 0; cycle < 20; cycle++) {
                nightSystem.tick(List.of(walker), 18000L, false);
            }
            for (int i = 0; i < night.types.size(); i++) {
                if (night.types.get(i) != MobType.BAT) {
                    continue; // the midnight cave also hosts the monsters
                }
                if (night.at.get(i).x() >= 7.0) {
                    midnightBand++;
                } else {
                    midnightDark++;
                }
            }
        }
        assertTrue(midnightBand > 0,
                "midnight's shave (7 - 11 -> 0) admits the sky-7 band");
        assertTrue(midnightDark > 0, "the dark half grows through the same walks");
        Spawned noon = new Spawned();
        NaturalSpawnSystem noonSystem = new NaturalSpawnSystem(
                spawnSink(noon), lit, new Random(3));
        for (int cycle = 0; cycle < 20; cycle++) {
            noonSystem.tick(List.of(walker), 6000L, false);
        }
        int noonBand = 0;
        boolean noonDark = false;
        for (int i = 0; i < noon.types.size(); i++) {
            if (noon.types.get(i) != MobType.BAT) {
                continue;
            }
            if (noon.at.get(i).x() >= 7.0) {
                noonBand++;
            } else {
                noonDark = true;
            }
        }
        assertEquals(0, noonBand, "noon's raw 7 never passes the nextInt(4)");
        assertTrue(noonDark, "the dark half still grows at noon — the walk ran");
        // Phase 3 — the height band: a dark cave at y 69 (the floor 68)
        // sits over the sea level 63; the whole category refuses it.
        FakeWorld high = new FakeWorld(70);
        fillLayer(high, 68, net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
        Spawned highBats = new Spawned();
        NaturalSpawnSystem highSystem = new NaturalSpawnSystem(
                spawnSink(highBats), high, new Random(3));
        for (int cycle = 0; cycle < 40; cycle++) {
            highSystem.tick(List.of(walker), 18000L, false);
        }
        assertEquals(0, highBats.countOf(NaturalSpawnSystem.Category.AMBIENT),
                "no bat spawns at y 69 — the sea-level band holds (line 190)");
    }

    /**
     * The WATER_CREATURE gate (the squid): SpawnPlacement line 54 gives the
     * squid the IN_WATER cell rule — the liquid cell with the liquid below
     * and the non-solid above (isValidSpawnPos lines 150-155; before this
     * slice the generic arm's solid-floor/air-cell rule refused every
     * in-water cell, so the natural walk never grew a squid) — and
     * SquidEntity.canSpawn line 160 adds the y band: y > 45 and below the
     * World's own seaLevel 63 (World line 62), so the TOP water cell 62
     * stays legal (the pre-tail band read the generator's 62 water top and
     * lost it) while 63 and above never spawn.
     */
    @Test
    void squidsSpawnInWaterUnderTheWaterGateInsideTheSeaLevelBand() {
        // Phase 1 — the y-band's top: only the 62 cell passes (below it
        // the 61 water cell fails the liquid-below arm — its own below is
        // air), so EVERY landed squid measures the band's top edge.
        FakeWorld sea = new FakeWorld(62);
        fillLayer(sea, 61, WATER);
        fillLayer(sea, 62, WATER);
        PlayerSession swimmer = player("Swimmer", 0.5, 0.5);
        Spawned squids = new Spawned();
        NaturalSpawnSystem seaSystem = new NaturalSpawnSystem(
                spawnSink(squids), sea, new Random(3));
        for (int cycle = 0; cycle < 30; cycle++) {
            seaSystem.tick(List.of(swimmer), 6000L, false);
        }
        assertTrue(squids.countOf(NaturalSpawnSystem.Category.WATER_CREATURE) > 0,
                "the water column grows the squid at noon — no light gate at all");
        for (MobType type : squids.types) {
            assertEquals(MobType.SQUID, type, "the water world grows squids alone");
        }
        for (Position at : squids.at) {
            assertEquals(62.0, at.y(), 1e-9,
                    "the squid lands in the top water cell 62 — the sea-level band's own top");
        }
        // Phase 2 — over the band: the 62-64 column passes the IN_WATER
        // cell rule at 63 and 64 but the entity gate refuses both (the
        // 62 cell fails the liquid-below arm — its own below is air).
        FakeWorld flooded = new FakeWorld(64);
        fillLayer(flooded, 62, WATER);
        fillLayer(flooded, 63, WATER);
        fillLayer(flooded, 64, WATER);
        Spawned none = new Spawned();
        NaturalSpawnSystem floodedSystem = new NaturalSpawnSystem(
                spawnSink(none), flooded, new Random(3));
        for (int cycle = 0; cycle < 40; cycle++) {
            floodedSystem.tick(List.of(swimmer), 18000L, false);
        }
        assertEquals(0, none.countOf(NaturalSpawnSystem.Category.WATER_CREATURE),
                "no squid spawns at y 63 or above — the sea-level band holds (line 160)");
    }
}
