package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.RailBlocks;
import net.zaminmc.torch.server.block.WallBlocks;
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
 * F-SP2's discovery seats — the hasSupportBelow family (the reference's
 * law, 1.8.8): the engine's ONE floor consult for the whole redstone
 * floor-mounted family ({@code RedstoneSystem.hasSupportBelow} lines
 * 2297-2299) answers the collision-solid question
 * {@code WorldSolidity.isSolid}, where vanilla answers a DIFFERENT law
 * per family, every one built on {@code World.hasSolidTop} (World.java
 * lines 1740-1752, the five arms):
 *
 * <ul>
 * <li>the WIRE — {@code RedstoneWireBlock.canBePlaced} (lines 91-93) and
 *     the survival arm (lines 235-244): {@code World.hasSolidTop(below) ||
 *     below == GLOWSTONE} — the GLOWSTONE disjunct the wire's own.</li>
 * <li>the REPEATER and the COMPARATOR — {@code DiodeBlock.canBePlaced}
 *     (lines 29-31), {@code canSurvive} (lines 33-35) and the survival
 *     arm (lines 80-90): {@code World.hasSolidTop(below)} ALONE — no
 *     glowstone disjunct (a repeater on glowstone POPS in vanilla), no
 *     fence arm.</li>
 * <li>the PLATES — wooden, stone and both weighted pairs
 *     (AbstractPressurePlateBlock, the weighted pair inheriting):
 *     {@code canBePlaced} (lines 72-74) over {@code canSitOnTop} (lines
 *     86-88): {@code World.hasSolidTop(below) || below instanceof
 *     FenceBlock} — the census EXACTLY seven families (the nether brick
 *     fence a member; the cobblestone wall NOT — BlockWall is no
 *     FenceBlock).</li>
 * <li>the RAILS — all four families — {@code AbstractRailBlock.
 *     canBePlaced} (lines 71-73) and the survival walk (lines 86-111):
 *     {@code World.hasSolidTop} for the floor and every ASCENDING
 *     headwall.</li>
 * </ul>
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — the six
 * survival arms ({@code RedstoneSystem.neighborChanged}'s wire arm line
 * 864, the repeater 888, the comparator 903, the weighted plates 963, the
 * plates 974, the rails 1155) all consult the one collision-solid
 * question, so a family whose floor becomes GLASS — translucent, never a
 * hasSolidTop member, never the wire's glowstone — is HELD by the engine
 * where vanilla pops it at the same neighbour update. Each seat's stone
 * control (the floor becoming DIRT) keeps its family — the attribution:
 * the pops are the consult's law, never the swap's side effect. The
 * agreement rows (the wire's glowstone floor, the plates' fence floor)
 * pin the disjuncts the laws carry.</p>
 */
class HasSupportBelowParityDiscoveryTest {

    @TempDir
    Path dataDir;

    @Test
    void theWirePopsWhenItsFloorBecomesGlassButGlowsoneHosts() throws Exception {
        EngineServer server = boot();
        try {
            int x = 400, z = 400, y = 40;
            // Three wires placed legally on STONE floors first (the
            // reference's hasSolidTop answering TRUE there); the floors
            // then swap — one to GLASS (translucent: hasSolidTop FALSE,
            // not the GLOWSTONE disjunct — the wire pops), one to
            // GLOWSTONE (the disjunct's own member: the wire STAYS), one
            // to DIRT (the opaque control: the wire stays).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 10),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, x, y, z)),
                    "the wire placed on its stone floor");
            await(() -> RedstoneBlocks.isWire(typeAt(server, x, y, z + 5)),
                    "the glowstone-row wire placed on its stone floor");
            await(() -> RedstoneBlocks.isWire(typeAt(server, x, y, z + 10)),
                    "the control wire placed on its stone floor");

            // The floor swaps: GLASS at the divergence row, GLOWSTONE at
            // the disjunct's agreement row, DIRT at the control.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.GLOWSTONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.DIRT);
            });
            sleep(1500);

            // Vanilla (hasSolidTop over Material.GLASS — translucent):
            // the glass-floor wire POPS at the swap.
            // The engine before the fix: WorldSolidity.isSolid(glass) =
            // true — the wire RODE the glass where vanilla fells it.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP2: the wire pops when its floor becomes glass "
                            + "(hasSolidTop — the translucent material never "
                            + "answers, and glass is not the glowstone "
                            + "disjunct)");
            // The disjunct's own member: the glowstone floor HOLDS the
            // wire (RedstoneWireBlock.canBePlaced's second arm).
            assertTrue(RedstoneBlocks.isWire(typeAt(server, x, y, z + 5)),
                    "F-SP2: the wire stays when its floor becomes glowstone "
                            + "(the disjunct's own member — the agreement row)");
            // The attribution control: the opaque floor keeps the wire.
            assertTrue(RedstoneBlocks.isWire(typeAt(server, x, y, z + 10)),
                    "F-SP2: the wire stays when its floor becomes dirt "
                            + "(the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theRepeaterPopsWhenItsFloorBecomesGlassOrGlowsone() throws Exception {
        EngineServer server = boot();
        try {
            int x = 420, z = 420, y = 40;
            // Repeaters placed legally on STONE floors; the floors swap
            // — GLASS and GLOWSTONE (the diode has NO disjunct: both
            // translucent hosts pop the family in vanilla), DIRT at the
            // control.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_EAST, 1, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_EAST, 1, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 10),
                        RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_EAST, 1, false));
            });
            await(() -> RedstoneBlocks.isRepeater(typeAt(server, x, y, z)),
                    "the repeater placed on its stone floor");
            await(() -> RedstoneBlocks.isRepeater(typeAt(server, x, y, z + 5)),
                    "the glowstone-row repeater placed on its stone floor");
            await(() -> RedstoneBlocks.isRepeater(typeAt(server, x, y, z + 10)),
                    "the control repeater placed on its stone floor");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.GLOWSTONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.DIRT);
            });
            sleep(1500);

            // Vanilla (DiodeBlock.canSurvive — hasSolidTop alone): the
            // glass-floor repeater POPS.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP2: the repeater pops when its floor becomes glass "
                            + "(hasSolidTop — the diode carries no disjunct)");
            // The diode's own contrast with the wire: GLOWSTONE is not a
            // diode host either (DiodeBlock.canSurvive's single arm).
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z + 5)),
                    "F-SP2: the repeater pops when its floor becomes glowstone "
                            + "(the diode's law has NO glowstone disjunct — the "
                            + "wire's contrast row)");
            // The attribution control: the opaque floor keeps the repeater.
            assertTrue(RedstoneBlocks.isRepeater(typeAt(server, x, y, z + 10)),
                    "F-SP2: the repeater stays when its floor becomes dirt "
                            + "(the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theComparatorPopsWhenItsFloorBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 440, z = 440, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_EAST, false, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_EAST, false, false));
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, x, y, z)),
                    "the comparator placed on its stone floor");
            await(() -> RedstoneBlocks.isComparator(typeAt(server, x, y, z + 5)),
                    "the control comparator placed on its stone floor");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.DIRT);
            });
            sleep(1500);

            // Vanilla (DiodeBlock.canSurvive — the comparator inherits
            // the diode's single arm): the glass-floor comparator POPS.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP2: the comparator pops when its floor becomes glass "
                            + "(hasSolidTop — the diode family's single arm)");
            assertTrue(RedstoneBlocks.isComparator(typeAt(server, x, y, z + 5)),
                    "F-SP2: the comparator stays when its floor becomes dirt "
                            + "(the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePlatePopsWhenItsFloorBecomesGlassOrWallButTheFenceHosts() throws Exception {
        EngineServer server = boot();
        try {
            int x = 460, z = 460, y = 40;
            // Four wooden plates placed legally on STONE floors; the
            // floors swap — GLASS (translucent: the plate pops), the OAK
            // FENCE (the FenceBlock disjunct's own member: the plate
            // STAYS), the COBBLESTONE WALL (BlockWall is NOT a
            // FenceBlock: the plate pops), DIRT at the control.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.plateOf(false, true));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RedstoneBlocks.plateOf(false, true));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 10),
                        RedstoneBlocks.plateOf(false, true));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 15),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 15),
                        RedstoneBlocks.plateOf(false, true));
            });
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z)),
                    "the glass-row plate placed on its stone floor");
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z + 5)),
                    "the fence-row plate placed on its stone floor");
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z + 10)),
                    "the wall-row plate placed on its stone floor");
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z + 15)),
                    "the control plate placed on its stone floor");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.FENCE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        WallBlocks.state(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 15),
                        BuiltinBlocks.DIRT);
            });
            sleep(1500);

            // Vanilla (AbstractPressurePlateBlock.canSitOnTop — hasSolidTop
            // || FenceBlock): the glass-floor plate POPS.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP2: the plate pops when its floor becomes glass "
                            + "(hasSolidTop — the translucent material never "
                            + "answers)");
            // The disjunct's own member: the oak fence floor HOLDS the
            // plate (canSitOnTop's FenceBlock arm).
            assertTrue(RedstoneBlocks.isPlate(typeAt(server, x, y, z + 5)),
                    "F-SP2: the plate stays when its floor becomes an oak "
                            + "fence (the FenceBlock disjunct's own member — "
                            + "the agreement row)");
            // The wall contrast: BlockWall is NOT a FenceBlock — the
            // plate pops in vanilla.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z + 10)),
                    "F-SP2: the plate pops when its floor becomes a "
                            + "cobblestone wall (BlockWall is not FenceBlock — "
                            + "the census's own contrast row)");
            // The attribution control: the opaque floor keeps the plate.
            assertTrue(RedstoneBlocks.isPlate(typeAt(server, x, y, z + 15)),
                    "F-SP2: the plate stays when its floor becomes dirt "
                            + "(the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theWeightedPlatePopsWhenItsFloorBecomesGlassButTheFenceHosts() throws Exception {
        EngineServer server = boot();
        try {
            int x = 480, z = 480, y = 40;
            // The light weighted plate (WeightedPressurePlateBlock
            // extends AbstractPressurePlateBlock — the same canSitOnTop
            // law): the floors swap to GLASS (pops), the OAK FENCE (the
            // disjunct's member: stays), DIRT (the control).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        WeightedPlateBlocks.state(0, 0));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        WeightedPlateBlocks.state(0, 0));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 10),
                        WeightedPlateBlocks.state(0, 0));
            });
            await(() -> WeightedPlateBlocks.isWeightedPlate(
                            typeAt(server, x, y, z).identifier()),
                    "the weighted plate placed on its stone floor");
            await(() -> WeightedPlateBlocks.isWeightedPlate(
                            typeAt(server, x, y, z + 5).identifier()),
                    "the fence-row weighted plate placed on its stone floor");
            await(() -> WeightedPlateBlocks.isWeightedPlate(
                            typeAt(server, x, y, z + 10).identifier()),
                    "the control weighted plate placed on its stone floor");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.FENCE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.DIRT);
            });
            sleep(1500);

            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP2: the weighted plate pops when its floor becomes "
                            + "glass (hasSolidTop — the inherited canSitOnTop "
                            + "law)");
            assertTrue(WeightedPlateBlocks.isWeightedPlate(
                            typeAt(server, x, y, z + 5).identifier()),
                    "F-SP2: the weighted plate stays when its floor becomes "
                            + "an oak fence (the inherited FenceBlock "
                            + "disjunct — the agreement row)");
            assertTrue(WeightedPlateBlocks.isWeightedPlate(
                            typeAt(server, x, y, z + 10).identifier()),
                    "F-SP2: the weighted plate stays when its floor becomes "
                            + "dirt (the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theRailPopsWhenItsFloorBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 500, z = 500, y = 40;
            // The plain rail placed legally on a STONE floor; the floor
            // swaps to GLASS — translucent, never a hasSolidTop member:
            // the rail pops in vanilla (AbstractRailBlock.neighborChanged
            // lines 86-111). The engine's TWO consults both answer TRUE
            // for glass (the collision-solid consult in RedstoneSystem,
            // the translucent fallthrough in FireBlocks.hasSolidTop) —
            // the rail RIDES the glass. DIRT at the control.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RailBlocks.defaultState("rail"));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        RailBlocks.defaultState("rail"));
            });
            await(() -> RailBlocks.isRailFamily(typeAt(server, x, y, z)),
                    "the rail placed on its stone floor");
            await(() -> RailBlocks.isRailFamily(typeAt(server, x, y, z + 5)),
                    "the control rail placed on its stone floor");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.DIRT);
            });
            sleep(1500);

            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP2: the rail pops when its floor becomes glass "
                            + "(hasSolidTop — the rail family's floor law, "
                            + "BOTH consults answering the collision question "
                            + "before the fix)");
            assertTrue(RailBlocks.isRailFamily(typeAt(server, x, y, z + 5)),
                    "F-SP2: the rail stays when its floor becomes dirt "
                            + "(the control)");
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
