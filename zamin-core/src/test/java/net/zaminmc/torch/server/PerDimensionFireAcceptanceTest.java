package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FireBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE FIRE FAMILY'S PER-DIMENSION SCHEDULING (Wave FL4-F2 — the FL4
 * wave's own surfaced tail): the reference's every ServerWorld carries
 * its own tickUpdates queue and its own block dispatch, and
 * FireBlock's every schedule consult rides the CALLING world's queue —
 * {@code world.scheduleTick(pos, this, ...)} (tick line 165's re-arm +
 * onAdded line 323's first arm). The engine's fire family read ONE
 * static seam (the overworld's system), so a nether or End flame armed
 * into the OVERWORLD's queue — where its dispatch read the FOREIGN
 * world's cells and the flame froze inert forever.
 *
 * <p>These seats drive the REAL boot (the FL4 per-dimension systems)
 * and pin the three laws the fix restores:</p>
 *
 * <ul>
 *   <li><b>THE NETHER'S OWN QUEUE</b> (tick lines 166-179): an age-4
 *       flame on a bare stone floor with no fuel DIES on its own
 *       dimension's scheduled tick ({@code i > 3} with no flammable
 *       neighbor) — the pre-fix engine froze it inert (the row armed
 *       into the overworld's queue, whose dispatch read the overworld's
 *       cells at those coordinates).</li>
 *   <li><b>THE END'S ETERNAL BEDROCK FLAME</b> (tick lines 150-154:
 *       {@code world.dimension instanceof TheEndDimension && block ==
 *       Blocks.BEDROCK}): an age-4 flame on an End bedrock plate
 *       SURVIVES its own tick — the eternal flag skips the fuel
 *       consults whole (the same age-4 flame on stone dies; on the
 *       End's bedrock it stands forever).</li>
 *   <li><b>THE NETHER-SIDE FRAME IGNITE</b> (onAdded lines 318-319:
 *       {@code world.dimension.getId() > 0} — the OVERWORLD AND THE
 *       NETHER both attempt the portal build, the End alone skips):
 *       fire placed inside a nether obsidian frame BUILDS THE PORTAL —
 *       the pre-fix engine gated the attempt on the overworld alone, so
 *       a nether-side frame's ignite never lit its portal.</li>
 * </ul>
 */
class PerDimensionFireAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The chamber's floor Y: high above the nether's natural sea. */
    private static final int FLOOR_Y = 100;
    /** The chamber spans x/z 58..72 — the frame build plus margin. */
    private static final int FROM = 58;
    private static final int TO = 72;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "fire" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * Carves the deterministic chamber in the named dimension: cleared
     * air for the working volume, then the full plate floor of the named
     * material (stone for the death seat — a solid, non-flammable,
     * non-eternal floor; bedrock for the End's eternal seat).
     */
    private void carveChamber(EngineWorld world, net.zaminmc.torch.block.BlockType floor)
            throws Exception {
        server.ticker().submit(() -> {
            for (int x = FROM; x <= TO; x++) {
                for (int z = FROM; z <= TO; z++) {
                    for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                        world.setBlock(new BlockPosition(x, y, z), world.airType());
                    }
                    world.setBlock(new BlockPosition(x, FLOOR_Y, z), floor);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> floor.equals(world.getBlock(new BlockPosition(64, FLOOR_Y, 64))),
                40, "the chamber floor landed");
    }

    /**
     * SEAT 1 — the nether flame dies on its OWN queue's tick: the age-4
     * flame on a bare stone floor carries no fuel anywhere in the
     * chamber (stone + air alone), and the reference's no-fuel law
     * ({@code !hasFlammableNeighbor && i > 3} → removeBlock) lands on the
     * FIRST scheduled tick 30-39 ticks out. The pre-fix engine armed the
     * row into the overworld's queue — the flame froze inert forever.
     */
    @Test
    void theNetherFlameDiesOnItsOwnQueue() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        carveChamber(nether, BuiltinBlocks.STONE);
        server.ticker().submit(() -> nether.setBlock(
                new BlockPosition(64, FLOOR_Y + 1, 64), FireBlocks.stateOf(4)));

        // Phase 1 — the flame LANDED (the submit is async; the death await
        // must never see the pre-placement air cell — the vacuous pass).
        EngineAwaits.awaitCondition(server.world(),
                () -> FireBlocks.isFire(nether.getBlock(new BlockPosition(64, FLOOR_Y + 1, 64))
                        .identifier()),
                40, "the age-4 nether flame landed");

        // Phase 2 — the death on the NETHER'S OWN queue: the first fire
        // tick lands 30-39 ticks out and the no-fuel law (i > 3) removes
        // the flame. The pre-fix engine froze it inert forever (the row
        // armed into the overworld's queue, whose dispatch read the
        // overworld's cells at those coordinates).
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(new BlockPosition(64, FLOOR_Y + 1, 64))
                        .identifier().equals(nether.airType().identifier()),
                100, "the age-4 nether flame died on its own queue's tick "
                        + "(i > 3, no flammable neighbor)");
    }

    /**
     * SEAT 2 — the End's eternal bedrock flame: the SAME age-4 flame
     * that dies on stone stands forever on the End's bedrock (the
     * eternal flag skips the fuel consults whole — lines 150-154's
     * {@code TheEndDimension && BEDROCK} gate). The flame must SURVIVE
     * its own first scheduled tick (well past the 30-39 window) — the
     * pair with seat 1 pins the eternal law against both the frozen
     * flame and the un-gated death.
     */
    @Test
    void theEndBedrockFlameIsEternal() throws Exception {
        EngineWorld end = server.worldFor(1);
        assertEquals(1, end.dimension(), "the End world carries its dimension row");
        carveChamber(end, BuiltinBlocks.BEDROCK);
        server.ticker().submit(() -> end.setBlock(
                new BlockPosition(64, FLOOR_Y + 1, 64), FireBlocks.stateOf(4)));

        // Phase 1 — the flame LANDED (the same anti-race shape).
        EngineAwaits.awaitCondition(server.world(),
                () -> FireBlocks.isFire(end.getBlock(new BlockPosition(64, FLOOR_Y + 1, 64))
                        .identifier()),
                40, "the age-4 End flame landed");

        // Past the first scheduled fire tick (30-39 out) with margin: the
        // eternal flame still stands.
        EngineAwaits.awaitTicks(server.world(), 80,
                "the End's first fire tick window passed");
        assertTrue(FireBlocks.isFire(end.getBlock(new BlockPosition(64, FLOOR_Y + 1, 64))
                        .identifier()),
                "the End's bedrock flame is eternal (survives its own tick)");
    }

    /**
     * SEAT 3 — the nether-side frame ignite builds the portal: the
     * reference's onAdded gate is {@code world.dimension.getId() > 0} —
     * the overworld AND the nether both attempt the frame build (the End
     * alone skips). Fire placed inside a nether obsidian frame lights
     * the portal interior. The pre-fix engine gated the attempt on the
     * overworld alone — a nether-side frame's ignite left the fire
     * sitting in the frame.
     */
    @Test
    void theNetherFrameIgniteBuildsThePortal() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        carveChamber(nether, BuiltinBlocks.STONE);
        // The frame: interior 2 wide x 3 tall at (60..61, FLOOR_Y+1..+3, 60),
        // the bottom row on the floor line, the top row at FLOOR_Y+4 —
        // all inside the cleared chamber volume.
        server.ticker().submit(() -> {
            int x = 60;
            int y = FLOOR_Y + 1;
            int z = 60;
            for (int i = -1; i <= 2; i++) {
                nether.setBlock(new BlockPosition(x + i, y - 1, z), BuiltinBlocks.OBSIDIAN);
                nether.setBlock(new BlockPosition(x + i, y + 3, z), BuiltinBlocks.OBSIDIAN);
            }
            for (int j = 0; j < 3; j++) {
                nether.setBlock(new BlockPosition(x - 1, y + j, z), BuiltinBlocks.OBSIDIAN);
                nether.setBlock(new BlockPosition(x + 2, y + j, z), BuiltinBlocks.OBSIDIAN);
            }
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 3; j++) {
                    nether.setBlock(new BlockPosition(x + i, y + j, z), nether.airType());
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.OBSIDIAN.equals(
                        nether.getBlock(new BlockPosition(59, FLOOR_Y, 60))),
                40, "the nether frame landed");
        // The ignite: fire in the interior's bottom cell.
        server.ticker().submit(() -> nether.setBlock(
                new BlockPosition(60, FLOOR_Y + 1, 60), FireBlocks.stateOf(0)));

        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.NETHER_PORTAL.equals(
                        nether.getBlock(new BlockPosition(60, FLOOR_Y + 1, 60))),
                40, "the nether-side ignite built the portal interior");
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(BuiltinBlocks.NETHER_PORTAL,
                        nether.getBlock(new BlockPosition(60 + i, FLOOR_Y + 1 + j, 60)),
                        "the interior cell (" + i + "," + j + ") fills with the portal");
            }
        }
    }
}
