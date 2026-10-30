package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
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
 * THE ICE FAMILY'S MELT ARMS (Wave FL4-F4 — the FL4 row's last named
 * tail): the reference's {@code IceBlock.tick} (lines 60-69 over the
 * randomTick → tick delegation) is the whole law — {@code if
 * (world.getLight(LightType.BLOCK, pos) > 11 - this.getOpacity())}, the
 * BLOCK channel ALONE, the ice's own opacity 0 ({@code
 * TransparentBlock.isSolidRender} false — Block line 216's constructor
 * assignment). The pre-fix engine carried NO melt arm at all: a torch
 * sat beside an ice sheet forever, and the nether's placed ice could
 * never answer its own dimension law.
 *
 * <p>These seats drive the REAL boot (the per-dimension random-tick
 * systems) with the deterministic probe the growth family established
 * (the sampler's 3-of-4096 odds make seeded end-to-end assertions
 * flaky):</p>
 *
 * <ul>
 *   <li><b>THE OVERWORLD MELT</b> (tick lines 64-67): an ice cell under
 *       a glowstone's block light 15 dissolves to the STILL SOURCE water
 *       form ({@code Blocks.WATER.defaultState()} — the melt pool stands
 *       until a neighbour wakes it) and yields NOTHING
 *       ({@code getBaseDropCount} 0 — the melt has no drop walk).</li>
 *   <li><b>THE SKY NEVER MELTS</b> (the {@code LightType.BLOCK} law):
 *       the SAME ice cell under open daylight (skylight 15, block light
 *       0) NEVER melts — the frozen-lake law. A {@code max(block, sky)}
 *       read of the general light would melt it; the reference's channel
 *       choice is the law.</li>
 *   <li><b>THE NETHER REMOVES</b> (tick lines 62-63, the
 *       {@code yeetsWater()} consult): a lit nether ice cell dissolves to
 *       AIR — no water ever forms in the nether (the same dimension
 *       profile that vaporizes the bucket's pour).</li>
 *   <li><b>THE END MELTS TO WATER</b> (the End carries NO yeetsWater
 *       row — water stands there): a lit End ice cell dissolves to the
 *       still source, the overworld's own melt shape.</li>
 * </ul>
 */
class IceMeltParityAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ice" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /** Runs a random-tick probe on the tick thread and waits for it. */
    private void probe(net.zaminmc.torch.server.block.RandomTickSystem ticks,
                       BlockPosition at) throws InterruptedException {
        server.ticker().submit(() -> ticks.randomTick(at));
        EngineAwaits.awaitTicks(server.world(), 2, "engine progress (a 60 wall settle)");
    }

    /**
     * SEAT 1 — the overworld melt: the glowstone's block light lands 15
     * at the ice cell (the light engine's relight queue is per-tick; the
     * two-phase await never probes a pre-relight cell), and the probe
     * dissolves the ice into the STILL SOURCE water — the melt's own
     * form, never the flowing one the dig arm places.
     */
    @Test
    void theLitOverworldIceMeltsToStillSourceWater() throws Exception {
        EngineWorld overworld = server.world();
        // The surface pair: ice at the flat world's y=5 (grass at 4), the
        // glowstone one cell east pushes block light 14 into the ice cell.
        BlockPosition iceAt = new BlockPosition(64, 5, 64);
        BlockPosition lampAt = new BlockPosition(65, 5, 64);
        server.ticker().submit(() -> {
            overworld.setBlock(iceAt, BuiltinBlocks.ICE);
            overworld.setBlock(lampAt, BuiltinBlocks.GLOWSTONE);
        });
        // Phase 1 — the placements landed AND the light engine propagated
        // the glowstone's emission (the probe must never read the
        // pre-relight 0 — the vacuous-pass shape the fire seats taught).
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ICE.equals(overworld.getBlock(iceAt))
                        && overworld.blockLightAt(iceAt) > 11,
                40, "the ice landed under the glowstone's block light");
        assertTrue(overworld.blockLightAt(iceAt) > 11,
                "the glowstone's light exceeds the melt threshold at the ice cell");

        // Phase 2 — the melt: the probe dissolves the ice to the STILL
        // SOURCE form (Blocks.WATER.defaultState() — the standing pool).
        probe(server.randomTicks(), iceAt);
        assertEquals(FluidBlocks.WATER_SOURCE,
                overworld.getBlock(iceAt).identifier(),
                "the lit ice melted to the still source water");
    }

    /**
     * SEAT 2 — the sky never melts: the same ice cell under open sky
     * (skylight 15, block light 0 — the flat world's unlit surface far
     * from any emission) SURVIVES the probe. The reference's channel
     * choice ({@code LightType.BLOCK} alone) is the frozen-lake law: a
     * {@code max(block, sky)} light read would dissolve every surface
     * sheet in daylight.
     */
    @Test
    void theDaylitIceNeverMelts() throws Exception {
        EngineWorld overworld = server.world();
        // Far from seat 1's lamp: 40 cells west, unlit open sky.
        BlockPosition iceAt = new BlockPosition(24, 5, 64);
        server.ticker().submit(() -> overworld.setBlock(iceAt, BuiltinBlocks.ICE));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ICE.equals(overworld.getBlock(iceAt)),
                40, "the daylight ice landed");
        assertTrue(overworld.skyLightAt(iceAt) > 11, "the cell sits under open sky");
        assertEquals(0, overworld.blockLightAt(iceAt), "the cell carries no block light");

        // The probe runs and the ice STANDS — the sky channel is not the
        // melt's consult.
        probe(server.randomTicks(), iceAt);
        probe(server.randomTicks(), iceAt);
        assertEquals(BuiltinBlocks.ICE, overworld.getBlock(iceAt),
                "the daylit ice never melts (LightType.BLOCK alone)");
    }

    /**
     * SEAT 3 — the nether removes: the nether's placed ice under a lit
     * cell dissolves to AIR, never water — the {@code yeetsWater()}
     * consult (the same dimension profile that vaporizes the bucket's
     * pour). The nether carries no live light engine (the §475 lane's
     * overworld-attached shape), so the seat writes the block light
     * directly into the chunk's section — the melt's own consult reads
     * the stored channel either way.
     */
    @Test
    void theLitNetherIceDissolvesToAirNeverWater() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        BlockPosition iceAt = new BlockPosition(64, 100, 64);
        server.ticker().submit(() -> {
            nether.setBlock(iceAt, BuiltinBlocks.ICE);
            nether.peek(iceAt.chunkPosition()).setBlockLight(
                    iceAt.localX(), iceAt.y(), iceAt.localZ(), 12);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ICE.equals(nether.getBlock(iceAt))
                        && nether.blockLightAt(iceAt) > 11,
                40, "the nether ice landed with its lit cell");

        // The probe over the NETHER'S OWN random-tick system: the melt
        // removes the cell whole — the nether never forms water.
        probe(server.randomTicksFor(-1), iceAt);
        assertEquals(nether.airType(), nether.getBlock(iceAt),
                "the lit nether ice dissolved to air (yeetsWater)");
    }

    /**
     * SEAT 4 — the End melts to water: the End carries NO yeetsWater row
     * (water stands there — the vanilla End moat law), so the same lit
     * ice dissolves to the still source exactly like the overworld's.
     */
    @Test
    void theLitEndIceMeltsToStillSourceWater() throws Exception {
        EngineWorld end = server.worldFor(1);
        assertEquals(1, end.dimension(), "the End world carries its dimension row");
        BlockPosition iceAt = new BlockPosition(64, 100, 64);
        server.ticker().submit(() -> {
            end.setBlock(iceAt, BuiltinBlocks.ICE);
            end.peek(iceAt.chunkPosition()).setBlockLight(
                    iceAt.localX(), iceAt.y(), iceAt.localZ(), 12);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ICE.equals(end.getBlock(iceAt))
                        && end.blockLightAt(iceAt) > 11,
                40, "the End ice landed with its lit cell");

        probe(server.randomTicksFor(1), iceAt);
        assertEquals(FluidBlocks.WATER_SOURCE, end.getBlock(iceAt).identifier(),
                "the lit End ice melted to the still source water");
    }
}
