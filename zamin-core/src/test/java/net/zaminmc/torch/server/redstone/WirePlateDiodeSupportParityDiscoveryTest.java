package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
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
 * VT-029's discovery seats — the wire/plate/repeater support family (the
 * reference's law, 1.8.8): the redstone floor consults each read a
 * FAMILY-SPECIFIC vanilla law, never the one collision-solid question:
 *
 * <ul>
 * <li>the WIRE reads {@code RedstoneWireBlock.canBePlaced} (lines 92-94):
 *     {@code World.hasSolidTop(pos.down()) || below == Blocks.GLOWSTONE}
 *     — the five-arm law (World.java lines 1740-1752) PLUS the wire's own
 *     explicit glowstone member. A wire NEVER sits on the translucent
 *     floors (glass, ice, TNT, leaves, cactus — Material.java's
 *     {@code setTranslucent()} census) nor the non-cubes (fences, panes,
 *     iron bars, walls) nor a BOTTOM slab (HALF != TOP).</li>
 * <li>the PLATES (stone, wooden, weighted — PressurePlateBlock /
 *     WeightedPressurePlateBlock over AbstractPressurePlateBlock lines
 *     72-86) read {@code canSitOnTop(pos.down())} =
 *     {@code World.hasSolidTop || below instanceof FenceBlock} — the
 *     fence is the plate's OWN explicit member (a stone plate sits on a
 *     fence in vanilla) but glass, glowstone, the panes and the bottom
 *     slabs never hold one.</li>
 * <li>the DIODES (repeater + comparator over DiodeBlock lines 29-35)
 *     read {@code canSurvive} = {@code World.hasSolidTop(pos.down())}
 *     ALONE — no glowstone arm, no fence arm. A repeater pops on glass,
 *     on glowstone AND on a fence where the wire's glowstone row and the
 *     plate's fence row each hold.</li>
 * </ul>
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — the
 * engine's ONE consult {@code RedstoneSystem.hasSupportBelow} (lines
 * 2297-2299) answers {@code WorldSolidity.isSolid} (the collision-solid
 * exclusion list, lines 29-54) for ALL FIVE members (the wire's
 * neighborChanged arm line 864, the repeater's line 888, the
 * comparator's line 903, the weighted plates' line 963 and the stone/
 * wooden plates' line 974): no material awareness — the translucents
 * (glass, glowstone) all PASS — and no half-law awareness — a BOTTOM
 * slab PASSES. A wire LIES on glass, a repeater RIDES a fence, a plate
 * STANDS on a bottom slab where vanilla pops each of them at the same
 * neighbor update; each seat's control keeps its family's own TRUE row
 * (the attribution: the pops are the consult's law, never the trigger's
 * side effect).</p>
 */
class WirePlateDiodeSupportParityDiscoveryTest {

    @TempDir
    Path dataDir;

    @Test
    void theWireOnGlassPopsButGlowstoneHosts() throws Exception {
        EngineServer server = boot();
        try {
            int x = 400, z = 400, y = 40;
            // The wire placed legally on STONE floors first; each floor
            // then swaps — one to GLASS (hasSolidTop false — translucent;
            // not GLOWSTONE: the wire POPS), one to GLOWSTONE (the wire's
            // own explicit member: the wire stays).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, x, y, z)),
                    "the wire placed on its stone floor");
            await(() -> RedstoneBlocks.isWire(typeAt(server, x, y, z + 5)),
                    "the control wire placed on its stone floor");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.GLOWSTONE);
            });
            sleep(1500);

            // Vanilla (canBePlaced lines 92-94 over Material.GLASS —
            // translucent, not glowstone): the glass-hosted wire POPS at
            // the swap. The engine before the fix:
            // WorldSolidity.isSolid(glass) = true — the wire LAY on the
            // glass where vanilla fells it.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-029: the wire pops when its floor becomes glass "
                            + "(canBePlaced — the translucent floor is not a "
                            + "solid top and not glowstone)");
            // The GLOWSTONE floor is the wire's OWN explicit member — the
            // wire stays there in vanilla AND in the engine (the agreement
            // row that isolates the wire's law from the plates' and the
            // diodes').
            assertTrue(RedstoneBlocks.isWire(typeAt(server, x, y, z + 5)),
                    "VT-029: the wire stays when its floor becomes glowstone "
                            + "(canBePlaced's explicit GLOWSTONE member)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theWireOnTheBottomSlabPops() throws Exception {
        EngineServer server = boot();
        try {
            int x = 410, z = 410, y = 40;
            // The wire on a BOTTOM slab (hasSolidTop false — the HALF is
            // not TOP: the wire pops) with the TOP slab the control
            // (hasSolidTop's TOP-half arm: the wire stays).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE_SLAB);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE_SLAB_TOP);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, x, y, z)),
                    "the bottom-slab wire placed");
            await(() -> RedstoneBlocks.isWire(typeAt(server, x, y, z + 5)),
                    "the top-slab wire placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z + 5),
                        BuiltinBlocks.STONE);
            });
            sleep(1500);

            // Vanilla: the bottom-slab wire pops (the half law). The
            // engine before the fix: WorldSolidity.isSolid passes the
            // single bottom slab — the wire lay on the half block.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-029: the wire on the bottom slab pops "
                            + "(hasSolidTop — the BOTTOM half is not a solid "
                            + "top and a slab is not glowstone)");
            assertTrue(RedstoneBlocks.isWire(typeAt(server, x, y, z + 5)),
                    "VT-029: the wire on the top slab stays (hasSolidTop's "
                            + "TOP-half arm — the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theWireOnTheFencePops() throws Exception {
        EngineServer server = boot();
        try {
            int x = 420, z = 420, y = 40;
            // The wire on a FENCE (hasSolidTop false — non-cube; not
            // glowstone: the wire pops) with DIRT the control.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.FENCE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.DIRT);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, x, y, z)),
                    "the fence-hosted wire placed");
            await(() -> RedstoneBlocks.isWire(typeAt(server, x, y, z + 5)),
                    "the dirt-hosted wire placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z + 5),
                        BuiltinBlocks.STONE);
            });
            sleep(1500);

            // Vanilla: the fence-hosted wire pops. The engine before the
            // fix: WorldSolidity.isSolid passes the fence — the wire rode
            // the 1.5-tall post.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-029: the wire on the fence pops (hasSolidTop — the "
                            + "non-cube floor is not a solid top and not "
                            + "glowstone)");
            assertTrue(RedstoneBlocks.isWire(typeAt(server, x, y, z + 5)),
                    "VT-029: the dirt-hosted wire stays (the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePlateOnGlassPopsButTheFenceHosts() throws Exception {
        EngineServer server = boot();
        try {
            int x = 430, z = 430, y = 40;
            // The stone plate on GLASS (canSitOnTop false — translucent;
            // not a fence: the plate pops) with the FENCE the plate's OWN
            // explicit member (the plate stays — the row that isolates
            // the plate's law from the wire's and the diodes').
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.plateOf(false, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.plateOf(false, false));
            });
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z)),
                    "the plate placed on its stone floor");
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z + 5)),
                    "the control plate placed on its stone floor");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.FENCE);
            });
            sleep(1500);

            // Vanilla (canSitOnTop lines 84-86): the glass-hosted plate
            // POPS. The engine before the fix:
            // WorldSolidity.isSolid(glass) = true — the plate stood on
            // the glass where vanilla fells it.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-029: the plate pops when its floor becomes glass "
                            + "(canSitOnTop — the translucent floor is not a "
                            + "solid top and not a fence)");
            // The FENCE floor is canSitOnTop's EXPLICIT member — the plate
            // stays there in vanilla AND in the engine (the agreement row).
            assertTrue(RedstoneBlocks.isPlate(typeAt(server, x, y, z + 5)),
                    "VT-029: the plate stays when its floor becomes a fence "
                            + "(canSitOnTop's explicit FenceBlock member)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePlatesOnTheBottomSlabPop() throws Exception {
        EngineServer server = boot();
        try {
            int x = 440, z = 440, y = 40;
            // The stone AND wooden plates on a BOTTOM slab (hasSolidTop
            // false: both pop) with the TOP slab the control.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE_SLAB);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.plateOf(false, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE_SLAB);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.plateOf(false, true));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.STONE_SLAB_TOP);
                server.world().setBlock(new BlockPosition(x, y, z + 10),
                        RedstoneBlocks.plateOf(false, false));
            });
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z)),
                    "the bottom-slab stone plate placed");
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z + 5)),
                    "the bottom-slab wooden plate placed");
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z + 10)),
                    "the top-slab control plate placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z + 10),
                        BuiltinBlocks.STONE);
            });
            sleep(1500);

            // Vanilla: both bottom-slab plates pop (the half law; the
            // fence arm does not reach a slab). The engine before the
            // fix: WorldSolidity.isSolid passes the bottom slab — both
            // plates stood on the half block.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-029: the stone plate on the bottom slab pops "
                            + "(hasSolidTop — the BOTTOM half is not a solid "
                            + "top and a slab is not a fence)");
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z + 5)),
                    "VT-029: the wooden plate on the bottom slab pops "
                            + "(the same half law)");
            assertTrue(RedstoneBlocks.isPlate(typeAt(server, x, y, z + 10)),
                    "VT-029: the plate on the top slab stays (hasSolidTop's "
                            + "TOP-half arm — the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theWeightedPlateOnGlassPops() throws Exception {
        EngineServer server = boot();
        try {
            int x = 450, z = 450, y = 40;
            // The light weighted plate on GLASS (the same
            // AbstractPressurePlateBlock law — hasSolidTop false, not a
            // fence: the plate pops) with the FENCE the control.
            BlockType weighted = WeightedPlateBlocks.state(0, 0);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z), weighted);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5), weighted);
            });
            await(() -> weighted.equals(typeAt(server, x, y, z)),
                    "the weighted plate placed on its stone floor");
            await(() -> weighted.equals(typeAt(server, x, y, z + 5)),
                    "the control weighted plate placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.FENCE);
            });
            sleep(1500);

            // Vanilla: the glass-hosted weighted plate pops; the fence
            // hosts it. The engine before the fix:
            // WorldSolidity.isSolid(glass) = true — the plate stood on
            // the glass.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-029: the weighted plate pops when its floor becomes "
                            + "glass (canSitOnTop — the translucent floor is "
                            + "not a solid top and not a fence)");
            assertTrue(weighted.equals(typeAt(server, x, y, z + 5)),
                    "VT-029: the weighted plate stays when its floor becomes "
                            + "a fence (canSitOnTop's explicit member)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDiodesOnGlassAndGlowstonePop() throws Exception {
        EngineServer server = boot();
        try {
            int x = 460, z = 460, y = 40;
            // The repeater and the comparator on GLASS and GLOWSTONE
            // (DiodeBlock.canSurvive = hasSolidTop ALONE — the translucent
            // floors never answer, and the diode carries NEITHER the wire's
            // glowstone arm NOR the plate's fence arm: both pop) with DIRT
            // the control.
            BlockType repeater = RedstoneBlocks.repeaterOf(0, 1, false);
            BlockType comparator = RedstoneBlocks.comparatorOf(0, false, false);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z), repeater);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5), comparator);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 10), repeater);
            });
            await(() -> repeater.equals(typeAt(server, x, y, z)),
                    "the glass-floor repeater placed");
            await(() -> comparator.equals(typeAt(server, x, y, z + 5)),
                    "the glass-floor comparator placed");
            await(() -> repeater.equals(typeAt(server, x, y, z + 10)),
                    "the control repeater placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.GLOWSTONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.DIRT);
            });
            sleep(1500);

            // Vanilla: the glass-hosted repeater pops, the GLOWSTONE-hosted
            // comparator pops (canSurvive = hasSolidTop alone — the wire's
            // explicit glowstone member does not reach the diode). The
            // engine before the fix: WorldSolidity.isSolid passes both —
            // the diodes stood on the translucent floors.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-029: the repeater pops when its floor becomes glass "
                            + "(canSurvive — hasSolidTop alone, the translucent "
                            + "floor never answers)");
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z + 5)),
                    "VT-029: the comparator pops when its floor becomes "
                            + "glowstone (canSurvive has NO glowstone arm — the "
                            + "wire's explicit member is the wire's alone)");
            assertTrue(repeater.equals(typeAt(server, x, y, z + 10)),
                    "VT-029: the dirt-hosted repeater stays (the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDiodesOnTheFencePop() throws Exception {
        EngineServer server = boot();
        try {
            int x = 470, z = 470, y = 40;
            // The repeater and the comparator on a FENCE (hasSolidTop
            // false — and the diode carries NO fence arm: both pop) with
            // DIRT the control. The fence row that the PLATE hosts (its
            // own explicit member) is the isolation: the same floor, the
            // opposite verdict per family.
            BlockType repeater = RedstoneBlocks.repeaterOf(0, 1, false);
            BlockType comparator = RedstoneBlocks.comparatorOf(0, false, false);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z), repeater);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5), comparator);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 10), repeater);
            });
            await(() -> repeater.equals(typeAt(server, x, y, z)),
                    "the fence-floor repeater placed");
            await(() -> comparator.equals(typeAt(server, x, y, z + 5)),
                    "the fence-floor comparator placed");
            await(() -> repeater.equals(typeAt(server, x, y, z + 10)),
                    "the control repeater placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.FENCE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.FENCE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.DIRT);
            });
            sleep(1500);

            // Vanilla: both fence-hosted diodes pop (the plate's fence
            // member does not reach the diode family). The engine before
            // the fix: WorldSolidity.isSolid passes the fence — the diodes
            // rode the post.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-029: the repeater on the fence pops (canSurvive — "
                            + "hasSolidTop alone, the non-cube floor never "
                            + "answers)");
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z + 5)),
                    "VT-029: the comparator on the fence pops (the same law)");
            assertTrue(repeater.equals(typeAt(server, x, y, z + 10)),
                    "VT-029: the dirt-hosted repeater stays (the control)");
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
