package net.zaminmc.torch.server.spawner;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawner's entity-level canSpawn consult (MobSpawner.tick line
 * 106's {@code mobentity.canSpawn()} — the kinds' own overrides, 1:1):
 * the MonsterEntity family's peaceful + light pair (the randomized sky
 * ceiling, the raw-brightness band with the thundering clamp), the
 * silverfish/endermite 5-block player band, the slime's chunk band
 * (the MobEntity family — no light gates), the magma cube's own
 * peaceful-only override, the ghast's flying 1-in-20, the guardian's
 * open-sky roll, the squid's y-and-water band, the bat's coin and
 * brightness, the animals' spawnableBlock floor + light law (the
 * mooshroom's own MYCELIUM), and the villager/golem favor-0 pass.
 *
 * <p>The light-gate rolls are the reference's own randomized design:
 * every pin either reads a deterministic band (a raw brightness of 8+
 * never passes {@code <= nextInt(8)}; a raw of 0 always does) or rides
 * a seeded first roll (seed 18 answers nextInt(20) == 0 and
 * nextInt(10) == 0; seed 1 answers both nonzero; seed 4096 answers
 * nextBoolean() == false, seed 1 true).</p>
 */
class SpawnerSpawnGatesTest {

    /** The recording player surface (the silverfish band's own consult). */
    private static final class TestPlayers implements SpawnerManager.Players {
        final List<Position> positions = new ArrayList<>();

        @Override
        public List<Position> positions() {
            return positions;
        }
    }

    /** The flat-air world query (the minimal mob manager's own shape). */
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

    private static net.zaminmc.torch.server.entity.MobManager mobs() {
        return new net.zaminmc.torch.server.entity.MobManager(
                AIR_WORLD, new Random(7L), (position, stack, dimension) -> {
                }, 1, (x, z) -> 64);
    }

    /** A fresh flat world (ground level 4, generator seed 0). */
    private static EngineWorld flatWorld() {
        var registry = net.zaminmc.torch.server.block.BuiltinBlocks.registerAll(
                new net.zaminmc.torch.server.block.BlockRegistryBuilder()).freeze();
        return new EngineWorld("overworld", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
    }

    /**
     * Wires one fixed context (the consult reads it live): the noon is
     * 6000 (ambient darkness 0), the midnight 18000 (11).
     */
    private static void wire(SpawnerManager spawners, long timeOfDay, boolean thundering,
                             int difficulty) {
        SpawnerManager.SpawnContext context = new SpawnerManager.SpawnContext(
                timeOfDay, 0.0f, 0.0f, thundering, difficulty);
        spawners.setSpawnContext(() -> context);
    }

    /**
     * Loads the cell's chunk and sets its two light rows explicitly
     * (deterministic regardless of the generator's initial light).
     */
    private static void setLight(EngineWorld world, int x, int y, int z, int sky, int block) {
        world.setBlock(new BlockPosition(x, y, z), world.airType()); // loads the chunk
        var chunk = world.peek(new ChunkPosition(x >> 4, z >> 4));
        chunk.setSkyLight(x & 15, y, z & 15, sky);
        chunk.setBlockLight(x & 15, y, z & 15, block);
    }

    // ------------------------------------------------------- the monster family

    /** MonsterEntity.canSpawn line 131: the peaceful world never hosts the family. */
    @Test
    void thePeacefulWorldRefusesTheMonsterFamily() {
        EngineWorld world = flatWorld();
        SpawnerManager spawners = new SpawnerManager(new Random(18L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(spawners, 6000L, false, 0);
        setLight(world, 8, 64, 8, 0, 0); // a dark cell
        assertFalse(spawners.canSpawnEntity(MobType.ZOMBIE, world, 8.5, 64.5, 8.5),
                "the peaceful difficulty refuses the zombie spawner");
        assertFalse(spawners.canSpawnEntity(MobType.SKELETON, world, 8.5, 64.5, 8.5),
                "the peaceful difficulty refuses the skeleton spawner");
    }

    /**
     * canSpawnAtLightLevel line 126: a raw brightness of 8 never passes
     * the {@code <= nextInt(8)} band (the roll answers 0..7) — the
     * torch-lit dungeon room's own refusal.
     */
    @Test
    void theBrightBlockLightBandRefusesTheMonsterFamily() {
        EngineWorld world = flatWorld();
        SpawnerManager spawners = new SpawnerManager(new Random(18L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(spawners, 6000L, false, 1);
        setLight(world, 8, 64, 8, 0, 8); // the sky gate passes (sky 0), the brightness never
        assertFalse(spawners.canSpawnEntity(MobType.ZOMBIE, world, 8.5, 64.5, 8.5),
                "a block-lit 8 cell never hosts the monster family");
    }

    /** The dark cell passes whole (sky 0 passes the ceiling, raw 0 passes the band). */
    @Test
    void theDarkCellPassesTheMonsterFamily() {
        EngineWorld world = flatWorld();
        SpawnerManager spawners = new SpawnerManager(new Random(18L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(spawners, 6000L, false, 1);
        setLight(world, 8, 64, 8, 0, 0);
        setLight(world, 30, 64, 30, 0, 0);
        assertTrue(spawners.canSpawnEntity(MobType.ZOMBIE, world, 8.5, 64.5, 8.5),
                "the dark cell hosts the zombie spawner");
        assertTrue(spawners.canSpawnEntity(MobType.SILVERFISH, world, 30.5, 64.5, 30.5),
                "the far dark cell hosts the silverfish (no player within 5)");
    }

    /**
     * The thundering clamp (canSpawnAtLightLevel lines 119-124): the
     * storming sky re-reads the brightness with the ambient darkness
     * forced 10 — a sky-lit 10 cell that a clear noon refuses (raw 10)
     * passes under the storm (raw 0).
     */
    @Test
    void theThunderingClampDarkensTheSkyBand() {
        EngineWorld world = flatWorld();
        setLight(world, 8, 64, 8, 10, 0);
        // A fresh manager per consult (the reference consults the
        // entity's own fresh random per attempt — one roll stream per
        // case keeps the seeded-ceiling pin deterministic).
        SpawnerManager clearNoon = new SpawnerManager(new Random(18L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(clearNoon, 6000L, false, 1); // the clear noon: darkness 0
        assertFalse(clearNoon.canSpawnEntity(MobType.ZOMBIE, world, 8.5, 64.5, 8.5),
                "the clear noon refuses the sky-10 cell (raw 10)");
        SpawnerManager storm = new SpawnerManager(new Random(18L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(storm, 6000L, true, 1); // the storm: the forced 10
        assertTrue(storm.canSpawnEntity(MobType.ZOMBIE, world, 8.5, 64.5, 8.5),
                "the storming clamp passes the sky-10 cell (raw 0)");
    }

    // ------------------------------------------------ the silverfish player band

    /**
     * SilverfishEntity.canSpawn lines 116-123: the monster chain + the
     * empty 5-block player band — a player 3.4 blocks from the spawn
     * cell refuses, the same spawner passing the far consult.
     */
    @Test
    void theSilverfishPlayerBandRefusesTheNearCell() {
        EngineWorld world = flatWorld();
        TestPlayers players = new TestPlayers();
        SpawnerManager spawners = new SpawnerManager(new Random(18L),
                players, mobs(), dimension -> world);
        wire(spawners, 6000L, false, 1);
        setLight(world, 8, 64, 8, 0, 0);
        setLight(world, 40, 64, 40, 0, 0);
        // A player 3.4 blocks from the spawn cell (dx 2, dy 0, dz 3):
        players.positions.add(new Position(10.5, 64.5, 5.5));
        assertFalse(spawners.canSpawnEntity(MobType.SILVERFISH, world, 8.5, 64.5, 8.5),
                "the player within 5 blocks refuses the silverfish spawn");
        assertFalse(spawners.canSpawnEntity(MobType.ENDERMITE, world, 8.5, 64.5, 8.5),
                "the endermite carries the same band");
        // The far cell (35+ blocks away) passes:
        assertTrue(spawners.canSpawnEntity(MobType.SILVERFISH, world, 40.5, 64.5, 40.5),
                "the far cell hosts the silverfish");
    }

    // ---------------------------------------------------------- the slime band

    /**
     * SlimeEntity.canSpawn lines 234-259: the MobEntity family (no
     * light gates — the bright cell passes) with the chunk band — the
     * first 1-in-10 (seed 18 answers 0), the chunk-seeded 1-in-10
     * (chunk 0,10 over the seed-0 flat world answers 0), the y &lt; 40
     * floor.
     */
    @Test
    void theSlimeChunkBandPassesItsOwnChunkAndFloor() {
        EngineWorld world = flatWorld();
        SpawnerManager spawners = new SpawnerManager(new Random(18L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(spawners, 6000L, false, 1);
        setLight(world, 8, 39, 160, 15, 15); // the bright cell: no light gate rides the slime
        assertTrue(spawners.canSpawnEntity(MobType.SLIME, world, 8.5, 39.5, 160.5),
                "the bright slime-chunk cell passes (no light gates) below y 40");
        assertFalse(spawners.canSpawnEntity(MobType.SLIME, world, 8.5, 40.5, 160.5),
                "the y 40 floor refuses the band");
        SpawnerManager refusing = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> world); // the first roll answers nonzero
        assertFalse(refusing.canSpawnEntity(MobType.SLIME, world, 8.5, 39.5, 160.5),
                "the first 1-in-10 roll refuses the band");
    }

    // ------------------------------------------------------- the special kinds

    /**
     * MagmaCubeEntity.canSpawn line 25: the cube's own override — the
     * peaceful gate alone; a fully-lit cell passes on easy.
     */
    @Test
    void theMagmaCubeOverridesToThePeacefulGateAlone() {
        EngineWorld world = flatWorld();
        SpawnerManager spawners = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> world);
        setLight(world, 8, 64, 8, 15, 15);
        wire(spawners, 6000L, false, 1);
        assertTrue(spawners.canSpawnEntity(MobType.MAGMA_CUBE, world, 8.5, 64.5, 8.5),
                "the bright cell passes the magma cube (the peaceful gate alone)");
        wire(spawners, 6000L, false, 0);
        assertFalse(spawners.canSpawnEntity(MobType.MAGMA_CUBE, world, 8.5, 64.5, 8.5),
                "the peaceful world refuses the cube");
    }

    /** GhastEntity.canSpawn line 126: the flying family's 1-in-20 + the peaceful gate. */
    @Test
    void theGhastRollsItsOneInTwenty() {
        EngineWorld world = flatWorld();
        SpawnerManager passing = new SpawnerManager(new Random(18L),
                new TestPlayers(), mobs(), dimension -> world); // the roll answers 0
        wire(passing, 6000L, false, 1);
        assertTrue(passing.canSpawnEntity(MobType.GHAST, world, 8.5, 64.5, 8.5),
                "the 1-in-20 roll passes the ghast");
        wire(passing, 6000L, false, 0);
        assertFalse(passing.canSpawnEntity(MobType.GHAST, world, 8.5, 64.5, 8.5),
                "the peaceful world refuses the ghast");
        SpawnerManager refusing = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> world); // the roll answers nonzero
        wire(refusing, 6000L, false, 1);
        assertFalse(refusing.canSpawnEntity(MobType.GHAST, world, 8.5, 64.5, 8.5),
                "the failed roll refuses the ghast (no light gate rides the flying family)");
    }

    /**
     * GuardianEntity.canSpawn line 418: the open-sky 1-in-20 — the
     * covered cell (sky 14) passes seed-free, the open sky (15) riding
     * the roll alone (no peaceful, no light gates).
     */
    @Test
    void theGuardianRidesTheOpenSkyRoll() {
        EngineWorld world = flatWorld();
        SpawnerManager refusing = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(refusing, 6000L, false, 1);
        setLight(world, 8, 64, 8, 15, 0);
        assertFalse(refusing.canSpawnEntity(MobType.GUARDIAN, world, 8.5, 64.5, 8.5),
                "the failed roll over the open sky refuses the guardian");
        setLight(world, 40, 64, 40, 14, 0);
        assertTrue(refusing.canSpawnEntity(MobType.GUARDIAN, world, 40.5, 64.5, 40.5),
                "the covered cell passes the guardian seed-free");
        SpawnerManager passing = new SpawnerManager(new Random(18L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(passing, 6000L, false, 0);
        assertTrue(passing.canSpawnEntity(MobType.GUARDIAN, world, 8.5, 64.5, 8.5),
                "the passing roll answers true even on peaceful (no monster chain)");
    }

    /** SquidEntity.canSpawn line 160: the y band (45..seaLevel 63) + the water cell. */
    @Test
    void theSquidBandReadsTheDepthAndTheWater() {
        EngineWorld world = flatWorld();
        SpawnerManager spawners = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(spawners, 6000L, false, 1);
        var water = net.zaminmc.torch.server.block.FluidBlocks.typeOf(
                net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER,
                net.zaminmc.torch.server.block.FluidBlocks.Form.FLOWING, 0);
        world.setBlock(new BlockPosition(8, 50, 8), water);
        assertTrue(spawners.canSpawnEntity(MobType.SQUID, world, 8.5, 50.5, 8.5),
                "the water cell inside the band hosts the squid");
        assertFalse(spawners.canSpawnEntity(MobType.SQUID, world, 40.5, 50.5, 40.5),
                "the dry cell refuses the squid");
        assertFalse(spawners.canSpawnEntity(MobType.SQUID, world, 8.5, 45.0, 8.5),
                "the y 45 floor refuses the band (y > 45)");
        assertFalse(spawners.canSpawnEntity(MobType.SQUID, world, 8.5, 63.0, 8.5),
                "the sea level 63 refuses the band (y < 63)");
        assertFalse(spawners.canSpawnEntity(MobType.SQUID, world, 8.5, 62.5, 8.5),
                "the water cell above the sea level refuses (y < 63)");
    }

    /**
     * BatEntity.canSpawn lines 188-203: below the sea level, the 1-in-2
     * coin (seed 4096 answers false, seed 1 true), the raw brightness
     * against the nextInt(4).
     */
    @Test
    void theBatFlipsItsCoinUnderTheSeaLevel() {
        EngineWorld world = flatWorld();
        SpawnerManager passing = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(passing, 6000L, false, 1);
        setLight(world, 8, 62, 8, 0, 0);
        assertTrue(passing.canSpawnEntity(MobType.BAT, world, 8.5, 62.5, 8.5),
                "the coin's true face passes the dark cell below the sea level");
        SpawnerManager refusing = new SpawnerManager(new Random(4096L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(refusing, 6000L, false, 1);
        assertFalse(refusing.canSpawnEntity(MobType.BAT, world, 8.5, 62.5, 8.5),
                "the coin's false face refuses the bat");
        assertFalse(refusing.canSpawnEntity(MobType.BAT, world, 8.5, 63.5, 8.5),
                "the sea level 63 refuses the band (y < 63)");
    }

    // ---------------------------------------------------------- the animal law

    /**
     * AnimalEntity.canSpawn lines 90-95: the spawnableBlock floor under
     * the feet + the actual light {@code max(sky, block) > 8} — the
     * grass floor's own law (a cow spawner in a stone room never fires).
     */
    @Test
    void theAnimalsReadTheGrassFloorAndTheLight() {
        EngineWorld world = flatWorld();
        SpawnerManager spawners = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(spawners, 6000L, false, 1);
        world.setBlock(new BlockPosition(8, 63, 8),
                net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
        setLight(world, 8, 64, 8, 15, 0);
        assertTrue(spawners.canSpawnEntity(MobType.COW, world, 8.5, 64.5, 8.5),
                "the grass floor and the open sky host the cow");
        world.setBlock(new BlockPosition(40, 63, 40),
                net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
        setLight(world, 40, 64, 40, 15, 0);
        assertFalse(spawners.canSpawnEntity(MobType.COW, world, 40.5, 64.5, 40.5),
                "the stone floor refuses the animal family");
        setLight(world, 8, 64, 8, 0, 0);
        assertFalse(spawners.canSpawnEntity(MobType.COW, world, 8.5, 64.5, 8.5),
                "the dark cell refuses the animal family (light > 8)");
    }

    /** MooshroomEntity line 16: the mooshroom's own MYCELIUM floor. */
    @Test
    void theMooshroomReadsItsOwnMyceliumFloor() {
        EngineWorld world = flatWorld();
        SpawnerManager spawners = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(spawners, 6000L, false, 1);
        world.setBlock(new BlockPosition(8, 63, 8),
                net.zaminmc.torch.server.block.BuiltinBlocks.MYCELIUM);
        setLight(world, 8, 64, 8, 15, 0);
        assertTrue(spawners.canSpawnEntity(MobType.MOOSHROOM, world, 8.5, 64.5, 8.5),
                "the mycelium floor hosts the mooshroom");
        world.setBlock(new BlockPosition(8, 63, 8),
                net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
        assertFalse(spawners.canSpawnEntity(MobType.MOOSHROOM, world, 8.5, 64.5, 8.5),
                "the plain grass refuses the mooshroom");
    }

    /**
     * The villager + the golem pair: the PathFinderMob favor 0.0 pass —
     * no gate of their own (the air floor and the dark cell both host).
     */
    @Test
    void theVillagerAndTheGolemsCarryNoGateOfTheirOwn() {
        EngineWorld world = flatWorld();
        SpawnerManager spawners = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> world);
        wire(spawners, 6000L, false, 1);
        setLight(world, 8, 64, 8, 0, 0);
        assertTrue(spawners.canSpawnEntity(MobType.VILLAGER, world, 8.5, 64.5, 8.5),
                "the villager passes the favor-0 consult");
        assertTrue(spawners.canSpawnEntity(MobType.IRON_GOLEM, world, 8.5, 64.5, 8.5),
                "the iron golem passes the favor-0 consult");
        assertTrue(spawners.canSpawnEntity(MobType.SNOW_GOLEM, world, 8.5, 64.5, 8.5),
                "the snow golem passes the favor-0 consult");
    }

    // ------------------------------------------------------- the harness law

    /**
     * The pre-existing harness contract (Slice 12g's own law): a null
     * world consult answers true whole — no chunk grid to read, no gate
     * to decide.
     */
    @Test
    void theWorldlessHarnessConsultPassesWhole() {
        SpawnerManager spawners = new SpawnerManager(new Random(1L),
                new TestPlayers(), mobs(), dimension -> null);
        assertTrue(spawners.canSpawnEntity(MobType.ZOMBIE, null, 8.5, 64.5, 8.5),
                "the null world passes the zombie");
        assertTrue(spawners.canSpawnEntity(MobType.SILVERFISH, null, 8.5, 64.5, 8.5),
                "the null world passes the silverfish (even the player band)");
    }
}
