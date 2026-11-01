package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-SP2's behavioral seats (pass 2) — the floor-consult family's survival
 * walks, seated per divergent row class against the reference's laws
 * (reference/1.8.8 RedstoneWireBlock / AbstractPressurePlateBlock /
 * DiodeBlock / AbstractRailBlock / World / Material).
 *
 * <p>THE LAW (each family's floor consult different): the wire rides
 * {@code RedstoneWireBlock.canBePlaced} (lines 92-94:
 * {@code World.hasSolidTop(pos.down()) || below == Blocks.GLOWSTONE});
 * the four pressure plates ride the private
 * {@code AbstractPressurePlateBlock.canSitOnTop} (lines 84-86:
 * {@code World.hasSolidTop || below instanceof FenceBlock}); the repeater
 * and the comparator ride {@code DiodeBlock.canSurvive} (line 34:
 * {@code World.hasSolidTop(pos.down())} ALONE — no glowstone arm, no
 * fence arm); the four rail families ride
 * {@code AbstractRailBlock.neighborChanged} (lines 90-102: bare
 * {@code hasSolidTop}, plus the ascending headwall). The five arms of
 * {@code World.hasSolidTop} (World.java lines 1740-1752) turn on
 * {@code material.isSolidBlocking()} (Material.java line 100 =
 * {@code !translucent && blocksMovement()}) — the translucent materials
 * (LEAVES, GLASS, TNT, ICE, SNOW_LAYER, CACTUS — Material.java lines
 * 13/22/24/26/28/30, the glowstone and the sea lantern among the
 * Material.GLASS registrations, Block.java line 89) NEVER answer.</p>
 *
 * <p>THE ENGINE'S SHAPE: {@code RedstoneSystem.hasSupportBelow} (line
 * 2297) answers the ONE collision-solid question
 * {@code WorldSolidity.isSolid} for all six families — no material
 * awareness (the translucents all pass), no half awareness (the bottom
 * slabs and stairs pass), no cube awareness (the fences and the wall
 * pass).</p>
 *
 * <p>THE CONSTRUCTION: every seat builds the surface family on stone (the
 * agreeing bed), then REPLACES the stone with the divergent bed — the
 * same commit that fires the survival walk ({@code setBlock} → the world
 * change listener → the six-neighbor neighborChanged dispatch → the
 * family's floor consult). Vanilla pops the surface block on that very
 * walk; the engine's one-question consult keeps it. These seats are RED
 * BY CONTRACT on unfixed develop (the vanilla answer asserted); the
 * controls at the bottom are GREEN on develop (the pop path itself and
 * the agreeing rows — the fix's regression pins — pinned).</p>
 */
class FloorConsultSurvivalParityDiscoveryTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The wire — RedstoneWireBlock.canBePlaced (lines 92-94)
    // ------------------------------------------------------------------

    /**
     * A wire whose bed becomes GLASS pops in vanilla (hasSolidTop's first
     * arm refuses — Material.GLASS is translucent, Material.java line 22)
     * and SURVIVES in the engine (WorldSolidity.isSolid carries no
     * material awareness — glass passes). Driven by RedstoneWireBlock
     * neighborChanged lines 235-244 (canBePlaced false → dropItems +
     * removeBlock).
     */
    @Test
    void theWirePopsWhenItsBedBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.GLASS);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the glass-bedded wire pops (hasSolidTop: GLASS is translucent)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A wire whose bed becomes an OAK FENCE pops in vanilla (the fence is
     * not a cube — hasSolidTop's first arm fails, and the wire's explicit
     * arm names GLOWSTONE alone, not the fences) and SURVIVES in the
     * engine (the fence is not in WorldSolidity's exclusion list). The
     * fence instance arm belongs to the PLATES' law and the torch's
     * canSitOnTop alone.
     */
    @Test
    void theWirePopsWhenItsBedBecomesAFence() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            BlockType fence = block(server, "minecraft:oak_fence");
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z), fence);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the fence-bedded wire pops (non-cube; the wire's explicit arm is GLOWSTONE alone)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A wire whose bed becomes a BOTTOM SLAB pops in vanilla (the slab's
     * HALF==TOP arm alone passes — the bottom half fails) and SURVIVES in
     * the engine (no half awareness in isSolid).
     */
    @Test
    void theWirePopsWhenItsBedBecomesABottomSlab() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.OAK_SLAB);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the bottom-slab-bedded wire pops (the slab's HALF==TOP arm alone)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the wire's explicit GLOWSTONE arm — the fix's regression
     * pin): a wire whose bed becomes GLOWSTONE SURVIVES in both — the
     * wire's law is hasSolidTop || GLOWSTONE (RedstoneWireBlock lines
     * 92-94), the glowstone named explicitly whatever its translucent
     * material. A naive hasSolidTop-only fix would break this row.
     */
    @Test
    void controlTheWireStaysWhenItsBedBecomesGlowstone() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.GLOWSTONE);
            });
            BlockType resting = read(server, new BlockPosition(x, y, z));
            assertTrue(resting.identifier().value().startsWith("redstone_wire"),
                    "the glowstone-bedded wire stays (the explicit arm): "
                            + resting.identifier().value());
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the agreeing top half): a wire whose bed becomes a TOP
     * SLAB SURVIVES in both — the slab's HALF==TOP arm passes.
     */
    @Test
    void controlTheWireStaysWhenItsBedBecomesATopSlab() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.OAK_SLAB_TOP);
            });
            BlockType resting = read(server, new BlockPosition(x, y, z));
            assertTrue(resting.identifier().value().startsWith("redstone_wire"),
                    "the top-slab-bedded wire stays (the HALF==TOP arm): "
                            + resting.identifier().value());
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the pop path itself): a wire whose bed becomes AIR pops in
     * both — the engine's consult and vanilla's agree on the empty row.
     */
    @Test
    void controlTheWirePopsWhenItsBedBecomesAir() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.AIR);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the air-bedded wire pops (the pop path itself — both agree)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The plates — AbstractPressurePlateBlock.canSitOnTop (lines 84-86)
    // ------------------------------------------------------------------

    /**
     * A stone plate whose bed becomes GLOWSTONE pops in vanilla (the
     * plate's law is hasSolidTop || FenceBlock — the glowstone is
     * Material.GLASS, translucent, and not a fence) and SURVIVES in the
     * engine (isSolid(glowstone) = true). THE STRIKING ROW: the wire's
     * glowstone arm and the trapdoor's glowstone arm do NOT generalize —
     * the plate has neither.
     */
    @Test
    void thePlatePopsWhenItsBedBecomesGlowstone() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.plateOf(false, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.GLOWSTONE);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the glowstone-bedded plate pops (hasSolidTop || FenceBlock — glowstone is neither)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A stone plate whose bed becomes a BOTTOM SLAB pops in vanilla (the
     * HALF==TOP arm alone) and SURVIVES in the engine (no half
     * awareness).
     */
    @Test
    void thePlatePopsWhenItsBedBecomesABottomSlab() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.plateOf(false, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.OAK_SLAB);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the bottom-slab-bedded plate pops (the HALF==TOP arm alone)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the plate's FenceBlock arm — the fix's regression pin): a
     * stone plate whose bed becomes an OAK FENCE SURVIVES in both — the
     * plate's law is hasSolidTop || FenceBlock (AbstractPressurePlate
     * Block lines 84-86), the fences named by instance. The wire's and
     * the diodes' refusal of the same row is the class boundary.
     */
    @Test
    void controlThePlateStaysWhenItsBedBecomesAFence() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            BlockType fence = block(server, "minecraft:oak_fence");
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.plateOf(false, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z), fence);
            });
            BlockType resting = read(server, new BlockPosition(x, y, z));
            assertTrue(resting.identifier().value().contains("pressure_plate"),
                    "the fence-bedded plate stays (the FenceBlock arm): "
                            + resting.identifier().value());
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * The light weighted plate — the same class row as the stone plate's
     * bottom slab (WeightedPressurePlateBlock extends AbstractPressure
     * PlateBlock; the law is inherited). The weighted pair's consult is
     * the engine's sixth hasSupportBelow family.
     */
    @Test
    void theWeightedPlatePopsWhenItsBedBecomesABottomSlab() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        net.zaminmc.torch.server.redstone.WeightedPlateBlocks.state(0, 0));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.OAK_SLAB);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the bottom-slab-bedded weighted plate pops (the inherited law)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The diodes — DiodeBlock.canSurvive (line 34): hasSolidTop ALONE
    // ------------------------------------------------------------------

    /**
     * A repeater whose bed becomes GLOWSTONE pops in vanilla (the diode's
     * law is hasSolidTop ALONE — DiodeBlock line 34; the glowstone's
     * Material.GLASS is translucent) and SURVIVES in the engine
     * (isSolid(glowstone) = true). THE CLASSIC ROW: the reference's own
     * redstone tutorials rest on it — no diode on glowstone.
     */
    @Test
    void theRepeaterPopsWhenItsBedBecomesGlowstone() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.repeaterOf(0, 1, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.GLOWSTONE);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the glowstone-bedded repeater pops (the diode's law is hasSolidTop ALONE)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A repeater whose bed becomes an OAK FENCE pops in vanilla (the
     * fence is not a cube; the diode carries no fence arm) and SURVIVES
     * in the engine (isSolid(fence) = true).
     */
    @Test
    void theRepeaterPopsWhenItsBedBecomesAFence() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            BlockType fence = block(server, "minecraft:oak_fence");
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.repeaterOf(0, 1, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z), fence);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the fence-bedded repeater pops (non-cube; no fence arm in the diode's law)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the hopper row): a repeater whose bed becomes a HOPPER
     * SURVIVES in both — hasSolidTop's fourth arm names HopperBlock
     * (World.java line 1750), and the engine's isSolid(hopper) = true.
     */
    @Test
    void controlTheRepeaterStaysWhenItsBedBecomesAHopper() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            BlockType hopper = block(server, "minecraft:hopper_down");
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.repeaterOf(0, 1, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z), hopper);
            });
            BlockType resting = read(server, new BlockPosition(x, y, z));
            assertTrue(resting.identifier().value().startsWith("repeater"),
                    "the hopper-bedded repeater stays (the HopperBlock arm): "
                            + resting.identifier().value());
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A comparator whose bed becomes GLASS pops in vanilla (the shared
     * diode law — hasSolidTop; GLASS is translucent) and SURVIVES in the
     * engine (isSolid(glass) = true). The comparator's consult is the
     * same DiodeBlock.canSurvive the repeater reads.
     */
    @Test
    void theComparatorPopsWhenItsBedBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.comparatorOf(0, false, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.GLASS);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the glass-bedded comparator pops (the shared diode law; GLASS is translucent)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The rails — AbstractRailBlock.neighborChanged (lines 90-102)
    // ------------------------------------------------------------------

    /**
     * A rail whose bed becomes GLASS pops in vanilla (the rail's law is
     * bare hasSolidTop — AbstractRailBlock neighborChanged lines 90-102;
     * GLASS is translucent) and SURVIVES in the engine (the survival
     * consult hasSupportBelow reads isSolid — glass passes). NOTE the
     * engine's own placement gate reads FireBlocks.hasSolidTop, whose
     * fallthrough ALSO accepts glass (the placement row is the pass-3
     * census's arm).
     */
    @Test
    void theRailPopsWhenItsBedBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        net.zaminmc.torch.server.block.RailBlocks.defaultState("rail"));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.GLASS);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the glass-bedded rail pops (the rail's law is bare hasSolidTop)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the agreeing top half): a rail whose bed becomes a TOP
     * SLAB SURVIVES in both — the slab's HALF==TOP arm passes, and the
     * engine's isSolid accepts it too.
     */
    @Test
    void controlTheRailStaysWhenItsBedBecomesATopSlab() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        net.zaminmc.torch.server.block.RailBlocks.defaultState("rail"));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.OAK_SLAB_TOP);
            });
            BlockType resting = read(server, new BlockPosition(x, y, z));
            assertTrue(resting.identifier().value().equals("rail")
                            || resting.identifier().value().startsWith("rail_"),
                    "the top-slab-bedded rail stays (the HALF==TOP arm): "
                            + resting.identifier().value());
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The helpers (the SupportConsultSurvivalParityDiscoveryTest pattern)
    // ------------------------------------------------------------------

    /** Awaits the vanilla pop: the surface cell must read air. */
    private void awaitPopped(EngineServer server, BlockPosition cell, String law)
            throws InterruptedException {
        await(() -> {
            BlockType type = read(server, cell);
            return type == null || type.identifier().value().equals("air");
        }, "the vanilla pop never fired: " + law);
    }

    private static BlockType read(EngineServer server, BlockPosition cell) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(server.world().getBlock(cell)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return result.get();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static BlockType block(EngineServer server, String id) {
        return server.blockRegistry().lookup(Identifier.parse(id)).orElseThrow(
                () -> new IllegalArgumentException("Unknown block type: " + id));
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "spf" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
