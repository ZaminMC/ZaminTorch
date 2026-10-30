package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PER-DIMENSION FLUID SIMULATION (Wave FL4 — the F-FL4 architecture
 * pass, the fluid family audit's standing tail): the reference gives every
 * ServerWorld its own scheduled-tick queue, its own random-tick walk, and
 * its own liquid family. The engine's single-mechanics-world shape left
 * the nether's lava inert beside a broken block forever and an End pour
 * sitting still. These seats drive the REAL boot: the per-dimension
 * systems exist, drain, and carry the reference's dimension profiles —
 *
 * <ul>
 *   <li>the NETHER (NetherDimension lines 13-15: yeetsWater true, noSky
 *       true): lava at the 10-tick cadence with the FULL seven-block
 *       reach (the j=1 decay band where water cannot exist);</li>
 *   <li>the END (TheEndDimension line 16: noSky alone): water flows at
 *       cadence 5, lava at cadence 10 with the WATER-KEEPING j=2 decay —
 *       the End's lava reaches three cells, not seven;</li>
 *   <li>the DIMENSION ISOLATION: a nether flow never touches the
 *       overworld's chunk map at the same coordinates;</li>
 *   <li>the WASH-OUT stamp: a block the nether stream destroys drops its
 *       item IN the nether (the TF-9 dimension row riding the sink).</li>
 * </ul>
 *
 * <p>The engine-level seats assert the WIRING whole (the systems + the
 * drains + the profiles through the real boot); the LAWS themselves (the
 * flow walk, the contact bands, the fizz) carry their own unit battery in
 * {@code FluidFlowTest} over the stub worlds.</p>
 *
 * <p>THE CHAMBER: each seat carves its own deterministic pocket — a stone
 * PLATE floor spanning the whole reach window (the spread's gap-seeking
 * walk must find no down-gap inside the window) with cleared air above
 * (the nether's and the End's natural terrain is seed-dependent). The
 * pour is the bucket's own shape (the flowing form at LEVEL 0).</p>
 */
class PerDimensionFluidAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The chamber's floor Y: high above the nether's natural sea. */
    private static final int FLOOR_Y = 100;
    /** The chamber spans x/z 58..72 — the seven-cell reach plus margin. */
    private static final int FROM = 58;
    private static final int TO = 72;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "fluids" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * Carves the deterministic chamber in the named dimension: cleared air
     * for the working volume, then the full stone plate floor (the spread
     * finds no down-gap inside the window — it must run IN PLANE).
     */
    private void carveChamber(EngineWorld world) throws Exception {
        server.ticker().submit(() -> {
            for (int x = FROM; x <= TO; x++) {
                for (int z = FROM; z <= TO; z++) {
                    for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                        world.setBlock(new BlockPosition(x, y, z), world.airType());
                    }
                    world.setBlock(new BlockPosition(x, FLOOR_Y, z), BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(64, FLOOR_Y, 64))),
                40, "the chamber floor landed");
    }

    /** The fluid kind at a cell (null when the cell holds no fluid). */
    private static FluidBlocks.Kind fluidKindAt(EngineWorld world, int x, int y, int z) {
        return FluidBlocks.kindOf(world.getBlock(new BlockPosition(x, y, z)).identifier());
    }

    /** The pour: the bucket's own shape — the flowing form at LEVEL 0. */
    private void pour(EngineWorld world, int x, int z, FluidBlocks.Kind kind) throws Exception {
        server.ticker().submit(() -> world.setBlock(
                new BlockPosition(x, FLOOR_Y + 1, z),
                FluidBlocks.typeOf(kind, FluidBlocks.Form.FLOWING, 0)));
    }

    /**
     * THE CORE F-FL4 CASE: the nether's lava flows. A flowing-0 pour on a
     * nether stone plate schedules on the NETHER's scheduled-tick system at
     * the 10-tick cadence, settles into the source, and spreads the FULL
     * seven-block reach (the j=1 decay band — the pre-fix engine left this
     * lava inert forever).
     */
    @Test
    void theNetherLavaFlowsWithTheFullReach() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        carveChamber(nether);
        pour(nether, 64, 64, FluidBlocks.Kind.LAVA);
        // The settle + the spread: the cadence is 10 ticks a round (the
        // lava's slow-creep roll can stretch a ring fourfold); the engine's
        // own clock is the honest budget — a slow runner waits, never skips.
        EngineAwaits.awaitCondition(server.world(),
                () -> fluidKindAt(nether, 66, FLOOR_Y + 1, 64) == FluidBlocks.Kind.LAVA,
                400, "the nether lava reached the third cell");
        EngineAwaits.awaitCondition(server.world(),
                () -> fluidKindAt(nether, 71, FLOOR_Y + 1, 64) == FluidBlocks.Kind.LAVA,
                400, "the nether lava's FULL seven-block reach (the j=1 band)");
        // The decay band's own proof: the seventh cell out carries LEVEL 7 —
        // only the j=1 band reaches it (the overworld's j=2 dies at the
        // fourth).
        assertEquals(7, FluidBlocks.levelOf(
                nether.getBlock(new BlockPosition(71, FLOOR_Y + 1, 64)).identifier()),
                "the seventh cell's level — the j=1 full-reach band");
    }

    /**
     * THE DIMENSION ISOLATION: the nether flow never touches the overworld's
     * chunk map at the same coordinates (the pre-fix single-system shape
     * read and wrote the overworld alone).
     */
    @Test
    void theNetherFlowNeverTouchesTheOverworld() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        carveChamber(nether);
        pour(nether, 64, 64, FluidBlocks.Kind.LAVA);
        EngineAwaits.awaitCondition(server.world(),
                () -> fluidKindAt(nether, 66, FLOOR_Y + 1, 64) == FluidBlocks.Kind.LAVA,
                400, "the nether flow is live");
        // The overworld at the same coordinates: untouched (the flat world's
        // own terrain — no stone plate at 100, no lava).
        EngineWorld overworld = server.worldFor(0);
        assertNotEquals(FluidBlocks.Kind.LAVA,
                fluidKindAt(overworld, 66, FLOOR_Y + 1, 64),
                "the overworld cell at the nether's coordinates holds no lava");
        assertNotEquals(BuiltinBlocks.STONE,
                overworld.getBlock(new BlockPosition(64, FLOOR_Y, 64)),
                "the overworld plate was never built");
    }

    /**
     * THE END'S WATER FLOWS (TheEndDimension: yeetsWater FALSE — the End
     * keeps water): a pour on an End plate spreads at the water cadence.
     */
    @Test
    void theEndWaterFlows() throws Exception {
        EngineWorld end = server.worldFor(1);
        assertEquals(1, end.dimension(), "the End world carries its dimension row");
        carveChamber(end);
        pour(end, 64, 64, FluidBlocks.Kind.WATER);
        EngineAwaits.awaitCondition(server.world(),
                () -> fluidKindAt(end, 68, FLOOR_Y + 1, 64) == FluidBlocks.Kind.WATER,
                400, "the End water spread past the fourth cell");
    }

    /**
     * THE END'S LAVA DECAYS AT TWO (the water-keeping dimension's band):
     * the End's lava reaches THREE cells (the j=2 decay — the level hits 8
     * at the fourth), never the nether's seven.
     */
    @Test
    void theEndLavaDecaysAtTwo() throws Exception {
        EngineWorld end = server.worldFor(1);
        carveChamber(end);
        pour(end, 64, 64, FluidBlocks.Kind.LAVA);
        EngineAwaits.awaitCondition(server.world(),
                () -> fluidKindAt(end, 67, FLOOR_Y + 1, 64) == FluidBlocks.Kind.LAVA,
                400, "the End lava reached the third cell");
        // The settled stream: give the walk every chance to over-reach, then
        // assert the band held (the fourth cell never fills).
        EngineAwaits.awaitTicks(server.world(), 120, "the walk settled");
        assertEquals(FluidBlocks.Kind.LAVA, fluidKindAt(end, 67, FLOOR_Y + 1, 64),
                "the third cell holds lava");
        assertNotEquals(FluidBlocks.Kind.LAVA, fluidKindAt(end, 68, FLOOR_Y + 1, 64),
                "the End's j=2 band stops the lava at the third cell");
    }

    /**
     * THE WASH-OUT STAMP: a torch the nether WATER stream destroys drops
     * its item IN THE NETHER (the sink's dimension row — the pre-fix shape
     * dropped every wash-out into the overworld). The WATER kind carries
     * the wash-out's drops (the reference's spreadTo pair: water drops the
     * displaced block's items, LAVA fizzles them away with no drop — the
     * water placed by setBlock in the nether flows normally, exactly like
     * the reference's own placed-water law; only the BUCKET pour
     * vaporizes).
     */
    @Test
    void theNetherWashOutDropsInTheNether() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        carveChamber(nether);
        server.ticker().submit(() -> {
            nether.setBlock(new BlockPosition(65, FLOOR_Y + 1, 64), BuiltinBlocks.TORCH);
            nether.setBlock(new BlockPosition(64, FLOOR_Y + 1, 64),
                    FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> FluidBlocks.kindOf(nether.getBlock(
                        new BlockPosition(65, FLOOR_Y + 1, 64)).identifier())
                        == FluidBlocks.Kind.WATER,
                400, "the stream washed the torch away");
        // The torch's item body: in the NETHER (the dimension stamp).
        assertTrue(server.itemEntities().all().size() > 0,
                "the wash-out spawned an item body");
        boolean netherDrop = server.itemEntities().all().stream()
                .anyMatch(item -> item.dimension() == -1);
        assertTrue(netherDrop, "the wash-out item carries the nether dimension");
        boolean overworldDrop = server.itemEntities().all().stream()
                .anyMatch(item -> item.dimension() == 0
                        && (int) Math.floor(item.position().x()) == 65
                        && (int) Math.floor(item.position().z()) == 64);
        assertTrue(!overworldDrop, "no wash-out item landed in the overworld");
    }

    /**
     * THE PERSISTENCE ROUND TRIP: a nether flow's pending scheduled rows
     * survive the restart (the per-dimension ZST store) and resume — the
     * same law the overworld's own store carries.
     */
    @Test
    void theNetherScheduledTicksSurviveARestart() throws Exception {
        // Session 1: start a flow, shut down mid-stream.
        String worldName = server.world().name();
        EngineWorld nether = server.worldFor(-1);
        carveChamber(nether);
        pour(nether, 64, 64, FluidBlocks.Kind.LAVA);
        EngineAwaits.awaitCondition(server.world(),
                () -> fluidKindAt(nether, 65, FLOOR_Y + 1, 64) == FluidBlocks.Kind.LAVA,
                400, "the flow's first cell");
        server.shutdown(null);
        Path store = dataDir.resolve(worldName)
                .resolve("data").resolve("DIM-1").resolve("scheduledticks.bin");
        assertTrue(Files.exists(store) && Files.size(store) > 0,
                "the nether ZST store wrote its pending rows");

        // Session 2: the flow resumes and completes its reach. The chamber
        // chunks arrive through the player-arrival stand-in (getOrGenerate —
        // the reference's chunk tickets load a nether chunk when a player
        // walks there; the scheduled rows wait for exactly that, the
        // reference's own line-505 re-arm-until-loaded shape).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                worldName, "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer second = new EngineServer(config);
        second.start();
        try {
            EngineWorld resumed = second.worldFor(-1);
            second.ticker().submit(() -> {
                for (int cx = FROM / 16; cx <= TO / 16; cx++) {
                    resumed.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(cx, 64 / 16));
                }
            });
            EngineAwaits.awaitCondition(second.world(),
                    () -> fluidKindAt(resumed, 69, FLOOR_Y + 1, 64) == FluidBlocks.Kind.LAVA,
                    400, "the resumed flow completed its reach");
        } finally {
            second.shutdown(null);
        }
    }
}
