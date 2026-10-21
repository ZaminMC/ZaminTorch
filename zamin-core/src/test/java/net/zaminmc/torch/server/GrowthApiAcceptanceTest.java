package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.Growth;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The growth-family outcome reads (§27.3 — the VIII2/VII7/VIII8
 * exposure, the §27.10 capability test): the stage/age/survival
 * consults a plugin uses to predict the bonemeal arms' outcomes —
 * the crops' and stems' stage bands over the block identity, the
 * sapling family's variant + readiness + the random tick's own light
 * gate, the cocoa pod's age band (the mature pod's use never pays), and
 * the mushroom plant's survival law (the mycelium-light form) — every
 * consult exercised through {@link World#growth()} with the block
 * types resolved through the PUBLIC registry ({@code
 * Server.blocks().lookup}), no engine internals.
 */
class GrowthApiAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private World world;
    private BlockRegistry registry;
    private Growth growth;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "gra" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        world = server.world();
        registry = server.blocks();
        growth = world.growth();
        assertNotNull(growth, "the api surface is wired");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private BlockType type(String id) {
        return registry.lookup(Identifier.parse("minecraft:" + id)).orElseThrow();
    }

    /** The crop/stem stage consult over the placed rows. */
    @Test
    void theCropStageConsultsReadTheStageBands() throws Exception {
        BlockPosition cell = new BlockPosition(10, 5, 10);
        loadAndPlace(() -> {
            // Farmland under the crop (the neighbor bus pops unsupported
            // flora — the wheat survives only on its soil).
            world.setBlock(cell.offset(0, -1, 0), type("farmland"));
            world.setBlock(cell, type("wheat_stage3"));
        }, () -> world.getBlock(cell).identifier().value().equals("wheat_stage3"),
                "the wheat stage-3 committed");
        assertEquals(3, growth.cropStage(world.getBlock(cell)),
                "the stage-3 row reads its own band");
        assertEquals(0, growth.cropStage(type("wheat_stage0")),
                "the fresh stage-0 form");
        assertEquals(7, growth.cropStage(type("melon_stem_stage7")),
                "the stem families ride the same consult");
        assertEquals(-1, growth.cropStage(type("stone")),
                "a non-crop consult refuses with -1");
        assertEquals(-1, growth.cropStage(type("oak_sapling")),
                "the sapling is no crop — its own consult owns it");
    }

    /** The sapling family: the variant, the two-stage readiness, the light gate. */
    @Test
    void theSaplingConsultsReadTheVariantReadinessAndLightGate() throws Exception {
        BlockPosition sapling = new BlockPosition(12, 5, 12);
        BlockPosition ready = new BlockPosition(13, 5, 13);
        loadAndPlace(() -> {
            world.setBlock(sapling, type("oak_sapling"));
            world.setBlock(ready, type("oak_sapling_ready"));
        }, () -> world.getBlock(ready).identifier().value().equals("oak_sapling_ready"),
                "the sapling pair committed");
        assertEquals(0, growth.saplingVariant(world.getBlock(sapling)),
                "the oak's variant row");
        assertEquals(5, growth.saplingVariant(type("dark_oak_sapling")),
                "the six-wood table's last row");
        assertFalse(growth.saplingReady(world.getBlock(sapling)),
                "the fresh form rolls its stage bit first (never the tree)");
        assertTrue(growth.saplingReady(world.getBlock(ready)),
                "the ready form rolls its tree on the next grow");
        assertTrue(growth.saplingLightGatePasses(sapling),
                "the open sky passes the light gate");
        // THE VANILLA LIGHT LAW, PINNED (the engine's own finding, the
        // grass-mushroom slice's): a single floating lid NEVER closes
        // the gate — the sky light bleeds one step per neighbor from
        // every lit column, so the cell above reads ~11 and the gate
        // stays armed.
        world.execute(() -> world.setBlock(new BlockPosition(12, 7, 12), type("stone")));
        await(() -> world.skyLightAt(new BlockPosition(12, 6, 12)) < 15,
                "the lid shaded the cell above");
        assertTrue(growth.saplingLightGatePasses(sapling),
                "a single lid never closes the gate (the vanilla horizontal"
                        + " bleed — the cell reads " + world.skyLightAt(
                                new BlockPosition(12, 6, 12)) + ")");
        // THE ENCLOSED ROOM (the genuinely dark form): a carved room
        // under the ground — no sky path at all — refuses the gate.
        BlockPosition room = new BlockPosition(6, 2, 6);
        world.execute(() -> {
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    for (int dy = 0; dy < 2; dy++) {
                        world.setBlock(room.offset(dx, dy, dz), type("air"));
                    }
                }
            }
            world.setBlock(room, type("oak_sapling"));
        });
        await(() -> !growth.saplingLightGatePasses(room),
                "the enclosed room refuses the gate (the cell above reads dark)");
        assertFalse(growth.saplingLightGatePasses(room),
                "the dark room's cell never passes the gate");
    }

    /** The cocoa pod's age band + the mushroom plant's survival law. */
    @Test
    void theCocoaAgeAndMushroomSurvivalConsultsReadTheirLaws() throws Exception {
        BlockPosition pod = new BlockPosition(15, 5, 15);
        BlockPosition log = new BlockPosition(15, 5, 16);
        BlockPosition room = new BlockPosition(18, 2, 18);
        BlockPosition daylight = new BlockPosition(18, 5, 18);
        // THE DARK CELL (the survival law's dark form — the vanilla
        // horizontal bleed means a floating lid never darkens a surface
        // cell past 13; the genuinely dark cell is an ENCLOSED room
        // carved under the ground, its grass ceiling intact — no sky
        // path at all). The consult answers the CELL before any plant
        // stands in it.
        world.execute(() -> {
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    for (int dy = 0; dy < 2; dy++) {
                        world.setBlock(room.offset(dx, dy, dz), type("air"));
                    }
                }
            }
        });
        await(() -> growth.mushroomSurvivesAt(room),
                "the carved room's cell dropped under the survival band (13)");
        loadAndPlace(() -> {
            // The cocoa's jungle log (the facing names the support cell —
            // the south-facing pod's FACING is south).
            world.setBlock(log, type("jungle_log"));
            world.setBlock(pod, type("cocoa"));
            // The mushroom on the room's dirt floor (the dark form —
            // the await above guarantees the light already dropped).
            world.setBlock(room, type("brown_mushroom"));
        }, () -> world.getBlock(room).identifier().value().equals("brown_mushroom")
                        && world.getBlock(pod).identifier().value().equals("cocoa"),
                "the cocoa + mushroom scene committed");
        assertEquals(0, growth.cocoaAge(world.getBlock(pod)),
                "the fresh pod's age row (the bonemeal arm pays under 2)");
        assertEquals(1, growth.cocoaAge(type("cocoa_age1")),
                "the age-1 band");
        assertEquals(2, growth.cocoaAge(type("cocoa_age2_west")),
                "the age-2 band reads mature (the arm's use never pays)");
        assertEquals(-1, growth.cocoaAge(type("stone")), "a non-pod refuses");
        assertTrue(growth.mushroomPlant(world.getBlock(room)),
                "the brown mushroom reads its family");
        assertFalse(growth.mushroomPlant(type("stone")), "a non-plant refuses");
        // THE SURVIVAL LAW (the reference's canSurvive): the dark cell
        // under the lid survives; the open daylight cell (combined light
        // 15) refuses — the consult answers the CELL, no plant needed.
        assertTrue(growth.mushroomSurvivesAt(room),
                "the dark room's cell survives");
        assertFalse(growth.mushroomSurvivesAt(daylight),
                "the daylight cell never carries a mushroom");
    }

    // ------------------------------------------------------------------
    // The harness
    // ------------------------------------------------------------------

    /**
     * Loads the scene's two chunks, runs the placement on the owning
     * lane, then awaits the scene's own completion condition.
     */
    private void loadAndPlace(Runnable placement,
                              java.util.function.BooleanSupplier committed,
                              String description) throws Exception {
        BlockPosition anchor = new BlockPosition(10, 5, 10);
        BlockPosition far = new BlockPosition(17, 5, 17);
        server.requestChunkLoad(anchor.chunkPosition(), chunk -> { });
        server.requestChunkLoad(far.chunkPosition(), chunk -> { });
        await(() -> world.isChunkLoaded(anchor.chunkPosition())
                        && world.isChunkLoaded(far.chunkPosition()),
                "the scene's chunks loaded");
        world.execute(placement);
        await(committed, description);
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
