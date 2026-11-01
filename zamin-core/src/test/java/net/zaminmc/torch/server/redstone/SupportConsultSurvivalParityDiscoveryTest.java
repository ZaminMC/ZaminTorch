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
 * F-SP1's behavioral seats (pass 2) — the support-consult family's survival
 * walks, seated per divergent row class against the reference's laws
 * (reference/1.8.8 TorchBlock / ButtonBlock / LeverBlock / TrapdoorBlock /
 * World / Material / Block).
 *
 * <p>THE LAW (each vanilla consult different): the torch's WALL consult rides
 * {@code World.isSolidBlockingCube} = {@code material.isSolidBlocking() &&
 * isCube()} (World.java lines 1753-1768 — the translucent materials LEAVES,
 * GLASS, TNT, ICE, SNOW_LAYER, CACTUS never answer, whatever their shape);
 * the torch's FLOOR consult rides {@code TorchBlock.canSitOnTop} (lines
 * 49-55: {@code World.hasSolidTop} + the explicit FENCE/GLASS/COBBLESTONE_
 * WALL/STAINED_GLASS list); the lever/button split is
 * {@code facing == DOWN ? World.hasSolidTop : Block.isSolid()} (ButtonBlock
 * lines 70-73, LeverBlock line 60 — {@code Block.isSolid()} = isSolidBlocking
 * && isCube && !isSignalSource, Block.java line 274); the trapdoor rides
 * {@code TrapdoorBlock.canBePlacedOn} (lines 188-190: isSolidBlocking &&
 * isCube || GLOWSTONE || SlabBlock || StairsBlock).</p>
 *
 * <p>THE ENGINE'S SHAPE: the four mount consults
 * ({@code RedstoneSystem.torchSupported} / {@code leverSupported} /
 * {@code buttonSupported} / {@code trapdoorMountHolds}'s final arm) all
 * answer the ONE collision-solid question {@code WorldSolidity.isSolid} —
 * no material awareness (the translucents all pass), no cube awareness
 * beyond the exclusion list (panes, iron bars, walls, fences all pass).</p>
 *
 * <p>THE CONSTRUCTION: every seat builds the mount on stone (the agreeing
 * support), then REPLACES the stone with the divergent support — the same
 * commit that fires the survival walk ({@code setBlock} → the world change
 * listener → the six-neighbor neighborChanged dispatch → the family's mount
 * consult). Vanilla pops the attached block on that very walk; the engine's
 * one-question consult keeps it. These seats are RED BY CONTRACT on unfixed
 * develop (the vanilla answer asserted), GREEN on the correction; the
 * controls at the bottom are GREEN on develop (the pop path itself and the
 * agreeing rows pinned).</p>
 */
class SupportConsultSurvivalParityDiscoveryTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // Row 1 — the torch WALL on a translucent mount
    // ------------------------------------------------------------------

    /**
     * A wall torch whose mount becomes GLASS pops in vanilla
     * (TorchBlock.tryBreak lines 96-118: the horizontal arm reads
     * isSolidBlockingCube — Material.GLASS is translucent, Material.java
     * line 22, so isSolidBlocking() is false) and SURVIVES in the engine
     * (WorldSolidity.isSolid carries no material awareness — glass passes).
     */
    @Test
    void theWallTorchPopsWhenItsMountBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.torchOfFacing(2, true));
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.GLASS);
            });
            awaitPopped(server, new BlockPosition(x, y, z - 1),
                    "the glass-mounted wall torch pops (isSolidBlockingCube: GLASS is translucent)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Row 2 — the torch WALL on a non-cube mount
    // ------------------------------------------------------------------

    /**
     * A wall torch whose mount becomes a COBBLESTONE WALL pops in vanilla
     * (WallBlock.isCube line 52 returns false — isSolidBlockingCube's cube
     * conjunct fails) and SURVIVES in the engine (the wall is not in
     * WorldSolidity's exclusion list). The torch's wall law has NO fence /
     * wall / slab exceptions — those instance arms belong to the trapdoor's
     * law alone.
     */
    @Test
    void theWallTorchPopsWhenItsMountBecomesACobblestoneWall() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            BlockType wall = block(server, "minecraft:cobblestone_wall");
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.torchOfFacing(2, true));
                server.world().setBlock(new BlockPosition(x, y, z), wall);
            });
            awaitPopped(server, new BlockPosition(x, y, z - 1),
                    "the wall-mounted torch pops (isSolidBlockingCube: the wall is not a cube)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Row 3 — the torch FLOOR beyond canSitOnTop's explicit list
    // ------------------------------------------------------------------

    /**
     * A standing torch whose floor becomes a GLASS PANE pops in vanilla
     * (canSitOnTop lines 49-55: hasSolidTop(pane) is false — the pane's
     * GLASS material is translucent; the explicit list is FENCE/GLASS/
     * COBBLESTONE_WALL/STAINED_GLASS alone, the pane not among them) and
     * SURVIVES in the engine (the pane passes WorldSolidity.isSolid).
     */
    @Test
    void theStandingTorchPopsWhenItsFloorBecomesAGlassPane() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.GLASS_PANE);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the pane-floored standing torch pops (canSitOnTop: the pane is neither hasSolidTop nor the explicit list)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Row 4 — the lever / button on a translucent mount
    // ------------------------------------------------------------------

    /**
     * A wall lever whose mount becomes GLASS pops in vanilla
     * (LeverBlock.neighborChanged lines 100-107 → ButtonBlock.canSurvive
     * lines 70-73: the horizontal arm reads Block.isSolid() — GLASS's
     * translucent material fails isSolidBlocking) and SURVIVES in the
     * engine (leverSupported reads WorldSolidity.isSolid alone).
     */
    @Test
    void theWallLeverPopsWhenItsMountBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                // lever_north: points north, its mount to the south —
                // the LEVER_ATTACHMENT row for "north" is NORTH(2), the
                // consult reading pos.offset(SOUTH) = the mount cell.
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.leverOf(4, false));
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.GLASS);
            });
            awaitPopped(server, new BlockPosition(x, y, z - 1),
                    "the glass-mounted wall lever pops (Block.isSolid: GLASS is translucent)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A wall button whose mount becomes GLASS pops in vanilla (the same
     * ButtonBlock.canSurvive law the lever delegates to) and SURVIVES in
     * the engine (buttonSupported reads WorldSolidity.isSolid alone).
     */
    @Test
    void theWallButtonPopsWhenItsMountBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                // stone_button_north: FACING north, the mount at south —
                // buttonSupported reads pos.offset(-OFFSETS[NORTH]) = south.
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.buttonOf(2, false, false));
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.GLASS);
            });
            awaitPopped(server, new BlockPosition(x, y, z - 1),
                    "the glass-mounted wall button pops (Block.isSolid: GLASS is translucent)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Row 5 — the lever's FLOOR mount on a bottom-half slab
    // ------------------------------------------------------------------

    /**
     * A floor lever whose floor becomes a BOTTOM slab pops in vanilla
     * (ButtonBlock.canSurvive's DOWN arm reads World.hasSolidTop —
     * World.java lines 1740-1752: the slab answers HALF == TOP alone; the
     * bottom half's top face is the slab's own mid-band, no solid top) and
     * SURVIVES in the engine (isSolid accepts both halves — the lever
     * hangs on the bottom slab's mid-band).
     */
    @Test
    void theFloorLeverPopsWhenItsFloorBecomesABottomSlab() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                // lever_up_z: points up, sits on the floor — the consult
                // reads pos.offset(DOWN) = the floor cell.
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.leverOf(5, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE_SLAB);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the bottom-slab floor lever pops (hasSolidTop: the bottom half answers no solid top)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Row 6 — the trapdoor's final arm on a translucent / non-cube mount
    // ------------------------------------------------------------------

    /**
     * A trapdoor whose wall becomes a GLASS PANE pops in vanilla
     * (TrapdoorBlock.neighborChanged lines 119-136 → canBePlacedOn lines
     * 188-190: the pane's GLASS material fails isSolidBlocking and the
     * pane is neither GLOWSTONE nor SlabBlock nor StairsBlock) and
     * SURVIVES in the engine (trapdoorMountHolds' final arm reads
     * WorldSolidity.isSolid — the pane passes).
     */
    @Test
    void theTrapdoorPopsWhenItsWallBecomesAGlassPane() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                // The closed bottom form facing north (facing 0): the wall
                // cell is at south = the mount stone.
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        net.zaminmc.torch.server.block.TrapdoorBlocks
                                .state("trapdoor", 0, false, false));
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.GLASS_PANE);
            });
            awaitPopped(server, new BlockPosition(x, y, z - 1),
                    "the pane-walled trapdoor pops (canBePlacedOn: the pane is not a solid-blocking cube)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A trapdoor whose wall becomes an OAK FENCE pops in vanilla
     * (FenceBlock.isCube line 119 returns false — the first arm's cube
     * conjunct fails; the fence is not GLOWSTONE/SlabBlock/StairsBlock)
     * and SURVIVES in the engine: trapdoorMountHolds' fence carve reads
     * {@code value.startsWith("minecraft:fence")} against an
     * identifier VALUE that never carries the namespace — the exclusion
     * is dead code (the dead-check census, this pass's addition to the
     * record's row set).
     */
    @Test
    void theTrapdoorPopsWhenItsWallBecomesAFence() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        net.zaminmc.torch.server.block.TrapdoorBlocks
                                .state("trapdoor", 0, false, false));
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.FENCE);
            });
            awaitPopped(server, new BlockPosition(x, y, z - 1),
                    "the fence-walled trapdoor pops (canBePlacedOn: the fence is not a cube)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The controls — GREEN on unfixed develop (the pop path and the
    // agreeing rows pinned, the attribution controls)
    // ------------------------------------------------------------------

    /**
     * CONTROL (the pop path): a wall torch whose mount becomes AIR pops in
     * BOTH — the engine's own consult fails on air (isSolid(AIR) = false),
     * so the seat's machinery (the submit → the walk → the pop → the read)
     * is proven live before the divergent rows are read.
     */
    @Test
    void controlTheWallTorchPopsWhenItsMountBecomesAir() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.torchOfFacing(2, true));
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.AIR);
            });
            awaitPopped(server, new BlockPosition(x, y, z - 1),
                    "the air-mounted torch pops in the engine too (the pop path live)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the agreeing cube row): a wall torch whose mount becomes
     * COBBLESTONE (an opaque cube) SURVIVES in both — isSolidBlockingCube
     * accepts it exactly as the engine's isSolid does. The fix must keep
     * this row.
     */
    @Test
    void controlTheWallTorchStaysWhenItsMountBecomesCobblestone() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.torchOfFacing(2, true));
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.COBBLESTONE);
            });
            BlockType resting = read(server, new BlockPosition(x, y, z - 1));
            assertTrue(resting.identifier().value().startsWith("redstone_torch"),
                    "the cobblestone-mounted torch stays (the agreeing cube row): "
                            + resting.identifier().value());
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (canSitOnTop's explicit GLASS row — the fix's regression
     * pin): a standing torch whose floor becomes GLASS SURVIVES in both —
     * vanilla's canSitOnTop lists GLASS explicitly (TorchBlock lines
     * 49-55), so a naive "no translucent floors" fix would break this
     * row; the correction must carry the explicit list.
     */
    @Test
    void controlTheStandingTorchStaysWhenItsFloorBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.GLASS);
            });
            BlockType resting = read(server, new BlockPosition(x, y, z));
            assertTrue(resting.identifier().value().startsWith("redstone_torch"),
                    "the glass-floored standing torch stays (canSitOnTop's explicit row): "
                            + resting.identifier().value());
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The helpers (the RedstoneSystemTest pattern)
    // ------------------------------------------------------------------

    /** Awaits the vanilla pop: the attached cell must read air. */
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
