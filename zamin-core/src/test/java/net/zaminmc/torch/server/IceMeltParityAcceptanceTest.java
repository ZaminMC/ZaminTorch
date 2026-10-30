package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ------------------------------------------------------------------
    // THE DIG ARM (Wave FL4-F6 — IceBlock.afterMinedByPlayer lines 39-51,
    // the non-silk else branch over the real dig wire)
    // ------------------------------------------------------------------

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    /** Runs one survival dig of the ice cell over the real wire (a bare hand — ice is tool-not-required, 15 ticks). */
    private void digIce(PlayerSession digger, BlockPosition target) throws InterruptedException {
        server.blockInteraction().submitMiningStart(digger, target);
        EngineAwaits.awaitTicks(server.world(), 18, "the ice dig accumulates (15 nominal)");
        server.blockInteraction().submitMiningFinished(digger, target);
        EngineAwaits.awaitCondition(server.world(),
                () -> !BuiltinBlocks.ICE.equals(server.world().getBlock(target)),
                40, "the ice dig committed");
    }

    /**
     * SEAT 5 — the dig's water law: a bare-handed survival dig of ice over
     * STONE places the FLOWING form at level 0 in the dug cell (the
     * reference's own pour form — {@code Blocks.FLOWING_WATER}, never the
     * melt arm's still source); the bare hand harvests ice (the ICE
     * material is tool-not-required) so the law runs with nothing dropped
     * ({@code getBaseDropCount} 0).
     */
    @Test
    void theIceDigOverStonePlacesFlowingWater() throws Exception {
        EngineWorld overworld = server.world();
        PlayerSession digger = join("IceDigger");
        // The surface pair: ice at y=5 on the flat world's grass (y=4) —
        // the grass below blocks movement, the water law holds.
        BlockPosition iceAt = new BlockPosition(64, 5, 64);
        server.ticker().submit(() -> overworld.setBlock(iceAt, BuiltinBlocks.ICE));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ICE.equals(overworld.getBlock(iceAt)),
                40, "the dig seat's ice landed");
        digger.applyMovement(new net.zaminmc.torch.util.Position(64.5, 5, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the digger settles");

        digIce(digger, iceAt);
        assertEquals(FluidBlocks.levelId(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0),
                overworld.getBlock(iceAt).identifier(),
                "the dug ice over stone holds the FLOWING water form (the reference's pour form)");
        assertFalse(FluidBlocks.WATER_SOURCE.equals(overworld.getBlock(iceAt).identifier()),
                "never the melt arm's still source");
    }

    /**
     * SEAT 6 — the below consult's other arm: the same dig over AIR (the
     * ice floats one cell above the surface) leaves the dug cell EMPTY —
     * the reference's material consult ({@code blocksMovement() ||
     * isLiquid()}) refuses the water when nothing holds it.
     */
    @Test
    void theIceDigOverAirLeavesTheCellEmpty() throws Exception {
        EngineWorld overworld = server.world();
        PlayerSession digger = join("FloatIceDigger");
        // The floating ice: y=6, air at y=5 below (the flat world's
        // surface ends at y=4).
        BlockPosition iceAt = new BlockPosition(64, 6, 64);
        server.ticker().submit(() -> overworld.setBlock(iceAt, BuiltinBlocks.ICE));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ICE.equals(overworld.getBlock(iceAt)),
                40, "the floating ice landed");
        digger.applyMovement(new net.zaminmc.torch.util.Position(64.5, 5, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the digger settles");

        digIce(digger, iceAt);
        assertEquals(overworld.airType(), overworld.getBlock(iceAt),
                "the dug floating ice leaves the cell empty (the below consult refused the water)");
    }

    /**
     * SEAT 7 — the silk arm never forms water: a silk pickaxe's dig drops
     * the ICE CUBE itself (the silk set's own row) and the cell stays
     * empty — the reference's silk branch replaces the whole else arm.
     */
    @Test
    void theSilkDigDropsTheCubeAndNeverFormsWater() throws Exception {
        EngineWorld overworld = server.world();
        PlayerSession digger = join("SilkIceDigger");
        BlockPosition iceAt = new BlockPosition(64, 5, 64);
        server.ticker().submit(() -> overworld.setBlock(iceAt, BuiltinBlocks.ICE));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ICE.equals(overworld.getBlock(iceAt)),
                40, "the silk seat's ice landed");
        digger.applyMovement(new net.zaminmc.torch.util.Position(64.5, 5, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the digger settles");
        digger.inventory().setSlot(0, ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_PICKAXE, 1)
                .withEnchantment(net.zaminmc.torch.server.enchantment.Enchantments.SILK_TOUCH.id, 1));
        digger.inventory().selectHotbarSlot(0);

        server.blockInteraction().submitMiningStart(digger, iceAt);
        EngineAwaits.awaitTicks(server.world(), 8, "the silk dig accumulates");
        server.blockInteraction().submitMiningFinished(digger, iceAt);
        EngineAwaits.awaitCondition(server.world(),
                () -> !BuiltinBlocks.ICE.equals(overworld.getBlock(iceAt)),
                40, "the silk dig committed");
        assertEquals(overworld.airType(), overworld.getBlock(iceAt),
                "the silk dig leaves the cell empty — no water under silk");
        EngineAwaits.awaitCondition(server.world(),
                () -> server.itemEntities().all().stream()
                        .anyMatch(item -> item.dimension() == 0
                                && item.stack().type().identifier().value().equals("ice")),
                40, "the silk dig dropped the ice cube itself");
    }

    /**
     * SEAT 8 — the nether's dig never forms water: the nether player's
     * bare-handed dig over nether stone leaves the cell AIR (the
     * {@code yeetsWater()} consult's early return — the same dimension
     * profile that vaporizes the bucket's pour and the melt's pool).
     */
    @Test
    void theNetherIceDigNeverFormsWater() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        PlayerSession digger = join("NetherIceDigger");
        digger.setDimension(-1);
        int fy = 100;
        BlockPosition iceAt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 3; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(iceAt, BuiltinBlocks.ICE);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ICE.equals(nether.getBlock(iceAt)),
                40, "the nether dig seat's ice landed");
        digger.applyMovement(new net.zaminmc.torch.util.Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the digger settles");

        server.blockInteraction().submitMiningStart(digger, iceAt);
        EngineAwaits.awaitTicks(server.world(), 18, "the nether ice dig accumulates");
        server.blockInteraction().submitMiningFinished(digger, iceAt);
        EngineAwaits.awaitCondition(server.world(),
                () -> !BuiltinBlocks.ICE.equals(nether.getBlock(iceAt)),
                40, "the nether ice dig committed");
        assertEquals(nether.airType(), nether.getBlock(iceAt),
                "the nether dig leaves the cell air — yeetsWater never forms water");
        assertFalse(FluidBlocks.isFluid(nether.getBlock(iceAt).identifier()),
                "no fluid ever forms in the nether's dug ice cell");
    }
}
