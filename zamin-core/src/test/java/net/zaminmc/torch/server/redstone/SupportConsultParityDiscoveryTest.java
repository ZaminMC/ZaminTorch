package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.TrapdoorBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-SP1's discovery seats — the support-consult family (the reference's
 * law, 1.8.8): the four redstone mount consults each read a DIFFERENT
 * vanilla law, never the one collision-solid question:
 *
 * <ul>
 * <li>the torch WALL arm reads {@code World.isSolidBlockingCube} =
 *     {@code material.isSolidBlocking() && isCube()} (World.java lines
 *     1754-1768) — the translucent materials (LEAVES, GLASS, TNT, ICE,
 *     SNOW_LAYER, CACTUS — Material.java lines 13/22/24/26/28/30, the
 *     whole {@code setTranslucent()} census) NEVER answer, whatever their
 *     shape; the non-cubes (fences, single slabs, stairs, panes, iron
 *     bars) never answer either.</li>
 * <li>the torch FLOOR arm reads {@code TorchBlock.canSitOnTop} (lines
 *     49-56): {@code World.hasSolidTop} (the five-arm law, World.java
 *     lines 1740-1752) OR the explicit list — FENCES, GLASS,
 *     COBBLESTONE_WALL, STAINED_GLASS. A torch stands on GLASS in vanilla
 *     (the explicit list) but NEVER on ICE, TNT, LEAVES, CACTUS or the
 *     SNOW_LAYER (each translucent, each outside the list).</li>
 * <li>the lever/button consult reads {@code ButtonBlock.canSurvive}
 *     (lines 70-73; {@code LeverBlock.canSurvive} line 60 delegates to
 *     it): {@code attachment == DOWN ? World.hasSolidTop(host) :
 *     block.isSolid()} — the FLOOR mount rides hasSolidTop's five arms
 *     (a bottom-half slab is NOT a solid top), every wall/ceiling mount
 *     rides {@code Block.isSolid()} (Block.java lines 274-276:
 *     {@code material.isSolidBlocking() && isCube() && !isSignalSource()}).</li>
 * <li>the trapdoor consult reads {@code TrapdoorBlock.canBePlacedOn}
 *     (lines 188-190): {@code material.isSolidBlocking() && isCube() ||
 *     GLOWSTONE || SlabBlock || StairsBlock} — the translucent mounts
 *     (glass, ice, TNT, leaves, cactus) and the non-cube mounts (panes,
 *     iron bars, walls, fences) never hold a closed trapdoor.</li>
 * </ul>
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — the
 * engine's four consults ({@code RedstoneSystem.torchSupported} lines
 * 1685-1689, {@code leverSupported} lines 2223-2227, {@code
 * buttonSupported} lines 2229-2232 and {@code trapdoorMountHolds}'s final
 * arm, lines 1349-1375) all answer the ONE collision-solid question
 * {@code WorldSolidity.isSolid} (lines 29-54): no material awareness —
 * the translucents all PASS — and no cube awareness beyond the exclusion
 * list. A torch HANGS on a glass wall, a lever RIDES a glass wall, a
 * floor lever STANDS on a bottom slab, a button RIDES a glass wall and a
 * trapdoor LEANS on a glass wall where vanilla pops each of them at the
 * same neighbor update; each seat's stone control keeps its family (the
 * attribution: the pops are the consult's law, never the trigger's
 * side effect).</p>
 */
class SupportConsultParityDiscoveryTest {

    @TempDir
    Path dataDir;

    @Test
    void theWallTorchPopsWhenItsHostBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 300, z = 300, y = 40;
            // The torch pointing EAST hangs legally on the stone wall to
            // its west first (the reference's isSolidBlockingCube answers
            // TRUE there); the host then becomes GLASS — the swap's own
            // commit wakes the torch's neighborChanged arm, the survival
            // consult re-reads the host.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x - 1, y, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.torchOfFacing(5, true));
                server.world().setBlock(new BlockPosition(x - 1, y + 40, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 40, z),
                        RedstoneBlocks.torchOfFacing(5, true));
            });
            await(() -> BuiltinBlocks.STONE.equals(typeAt(server, x - 1, y, z)),
                    "the stone host placed");
            await(() -> RedstoneBlocks.isTorch(typeAt(server, x, y, z)),
                    "the wall torch placed on its stone host");
            await(() -> RedstoneBlocks.isTorch(typeAt(server, x, y + 40, z)),
                    "the control torch placed on its stone host");

            // The host swap: GLASS beside the divergence torch, DIRT beside
            // the control (a different opaque cube — the write itself wakes
            // the torch's consult in both vanilla and the engine).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x - 1, y, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x - 1, y + 40, z),
                        BuiltinBlocks.DIRT);
            });
            sleep(1500);

            // Vanilla (World.isSolidBlockingCube over Material.GLASS —
            // translucent): the glass-hosted torch POPS at the swap.
            // The engine before the fix: WorldSolidity.isSolid(glass) =
            // true — the torch HUNG on the glass where vanilla fells it.
            assertTrue(BuiltinBlocks.AIR.equals(
                            typeAt(server, x, y, z)),
                    "F-SP1: the torch pops when its wall host becomes glass "
                            + "(isSolidBlockingCube — the translucent material "
                            + "never answers)");
            // The attribution control: the opaque-hosted torch STAYS (the
            // consult's TRUE row — the pop above is the law, not the swap's
            // side effect).
            assertTrue(RedstoneBlocks.isTorch(typeAt(server, x, y + 40, z)),
                    "F-SP1: the torch stays when its wall host becomes dirt "
                            + "(the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFloorTorchPopsWhenItsFloorBecomesIceButGlassHosts() throws Exception {
        EngineServer server = boot();
        try {
            int x = 320, z = 320, y = 40;
            // The standing torch (FACING=UP) placed legally on STONE
            // floors first; each floor then swaps — one to ICE (canSitOnTop
            // reads hasSolidTop — ice is translucent — false: the torch
            // pops), one to GLASS (the explicit list's own member: the
            // torch stays).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.torchOfFacing(1, true));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.torchOfFacing(1, true));
            });
            await(() -> RedstoneBlocks.isTorch(typeAt(server, x, y, z)),
                    "the torch placed on its stone floor");
            await(() -> RedstoneBlocks.isTorch(typeAt(server, x, y, z + 5)),
                    "the control torch placed on its stone floor");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.ICE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.GLASS);
            });
            sleep(1500);

            // Vanilla: the ICE-hosted torch pops (canSitOnTop false); the
            // engine before the fix: WorldSolidity.isSolid(ice) = true —
            // it stood on the ice where vanilla fells it.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP1: the floor torch pops when its floor becomes ice "
                            + "(canSitOnTop — the translucent floor outside the "
                            + "explicit list)");
            // The GLASS floor is canSitOnTop's EXPLICIT member — the torch
            // stands there in vanilla AND in the engine (the agreement row).
            assertTrue(RedstoneBlocks.isTorch(typeAt(server, x, y, z + 5)),
                    "F-SP1: the floor torch stays when its floor becomes glass "
                            + "(canSitOnTop's explicit list — GLASS is a member)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theLeverOnTheGlassWallPops() throws Exception {
        EngineServer server = boot();
        try {
            int x = 340, z = 340, y = 40;
            // lever_east: the lever points east, its host is the block to
            // its west (the glass). Vanilla: the wall mount rides
            // Block.isSolid() — glass is translucent — the lever POPS.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x - 1, y, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.leverOf(1, false));
                // The control: the stone-hosted wall lever stays.
                server.world().setBlock(new BlockPosition(x - 1, y + 40, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 40, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            await(() -> RedstoneBlocks.leverOf(1, false).equals(typeAt(server, x, y, z)),
                    "the glass-hosted lever placed");
            await(() -> RedstoneBlocks.leverOf(1, false).equals(typeAt(server, x, y + 40, z)),
                    "the stone-hosted lever placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 41, z),
                        BuiltinBlocks.STONE);
            });
            sleep(1500);

            // Vanilla: the glass-hosted lever pops. The engine today:
            // WorldSolidity.isSolid passes the glass — the lever hangs.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP1: the lever on the glass wall pops (Block.isSolid — "
                            + "the translucent host never answers)");
            assertTrue(RedstoneBlocks.leverOf(1, false).equals(typeAt(server, x, y + 40, z)),
                    "F-SP1: the stone-hosted lever stays (the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFloorLeverOnTheBottomSlabPops() throws Exception {
        EngineServer server = boot();
        try {
            int x = 350, z = 350, y = 40;
            // lever_up_z: the floor lever standing on a BOTTOM slab.
            // Vanilla: attachment DOWN -> hasSolidTop — the bottom half
            // is not a solid top — the lever POPS. The TOP slab (the
            // hasSolidTop arm's own member) is the control: the lever stays.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE_SLAB);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.leverOf(5, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE_SLAB_TOP);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> RedstoneBlocks.leverOf(5, false).equals(typeAt(server, x, y, z)),
                    "the bottom-slab lever placed");
            await(() -> RedstoneBlocks.leverOf(5, false).equals(typeAt(server, x, y, z + 5)),
                    "the top-slab lever placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z + 5),
                        BuiltinBlocks.STONE);
            });
            sleep(1500);

            // Vanilla: the bottom-slab lever pops. The engine today:
            // WorldSolidity.isSolid passes the bottom slab — the lever
            // stands where vanilla fells it.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP1: the floor lever on the bottom slab pops "
                            + "(attachment DOWN -> hasSolidTop — the bottom "
                            + "half is not a solid top)");
            assertTrue(RedstoneBlocks.leverOf(5, false).equals(typeAt(server, x, y, z + 5)),
                    "F-SP1: the floor lever on the top slab stays (hasSolidTop's "
                            + "TOP-half arm — the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theButtonOnTheGlassWallPops() throws Exception {
        EngineServer server = boot();
        try {
            int x = 360, z = 360, y = 40;
            // stone_button_east: the button points east, its mount is the
            // glass to its west. Vanilla: Block.isSolid(glass) = false —
            // the button POPS.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x - 1, y, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.buttonOf(5, false, false));
                // The control: the stone-hosted button stays.
                server.world().setBlock(new BlockPosition(x - 1, y + 40, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 40, z),
                        RedstoneBlocks.buttonOf(5, false, false));
            });
            await(() -> RedstoneBlocks.buttonOf(5, false, false)
                            .equals(typeAt(server, x, y, z)),
                    "the glass-hosted button placed");
            await(() -> RedstoneBlocks.buttonOf(5, false, false)
                            .equals(typeAt(server, x, y + 40, z)),
                    "the stone-hosted button placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 41, z),
                        BuiltinBlocks.STONE);
            });
            sleep(1500);

            // Vanilla: the glass-hosted button pops. The engine today:
            // WorldSolidity.isSolid passes the glass — the button rides
            // the glass wall.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP1: the button on the glass wall pops (Block.isSolid — "
                            + "the translucent mount never answers)");
            assertTrue(RedstoneBlocks.buttonOf(5, false, false)
                            .equals(typeAt(server, x, y + 40, z)),
                    "F-SP1: the stone-hosted button stays (the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theTrapdoorOnTheGlassWallPops() throws Exception {
        EngineServer server = boot();
        try {
            int x = 370, z = 370, y = 40;
            // trapdoor_east (closed, bottom, facing east): the wall it
            // leans on is the glass to its west. Vanilla:
            // canBePlacedOn(glass) = false — the trapdoor POPS.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x - 1, y, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y, z),
                        TrapdoorBlocks.state("trapdoor", 3, false, false));
                // The control: the stone wall holds the family.
                server.world().setBlock(new BlockPosition(x - 1, y + 40, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 40, z),
                        TrapdoorBlocks.state("trapdoor", 3, false, false));
            });
            await(() -> TrapdoorBlocks.state("trapdoor", 3, false, false)
                            .equals(typeAt(server, x, y, z)),
                    "the glass-hosted trapdoor placed");
            await(() -> TrapdoorBlocks.state("trapdoor", 3, false, false)
                            .equals(typeAt(server, x, y + 40, z)),
                    "the stone-hosted trapdoor placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 41, z),
                        BuiltinBlocks.STONE);
            });
            sleep(1500);

            // Vanilla: the glass-hosted trapdoor pops. The engine today:
            // the final arm's WorldSolidity.isSolid passes the glass —
            // the trapdoor leans on the glass wall.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP1: the trapdoor on the glass wall pops "
                            + "(canBePlacedOn — the translucent mount never "
                            + "answers the first arm)");
            assertTrue(TrapdoorBlocks.state("trapdoor", 3, false, false)
                            .equals(typeAt(server, x, y + 40, z)),
                    "F-SP1: the stone-hosted trapdoor stays (the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "scp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
