package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.FallingBlockEntityManager;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.world.anvil.AnvilBlockIds;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE RAIL FAMILY'S PLACEMENT + CONNECTION LAWS (the thirteenth verifier
 * pass — the reference's AbstractRailBlock, every seat anchored):
 *
 * <p><b>The default, NEVER the look</b> (RailBlock line 15 +
 * PoweredRailBlock line 29): the DEFAULT north-south state lands and
 * onAdded's force walk re-orients it — the pre-audit engine's +45 look
 * band folded every east-west look to a wrong first state and the walk
 * never ran at all.</p>
 *
 * <p><b>The connection walk</b> (AbstractRailBlock lines 143-477): the
 * placed rail re-orients to an existing rail AND the existing rail
 * re-orients back (the reciprocation), the curve forms from two
 * orthogonal neighbours (the plain rail's own family), the ascending
 * upgrade forms from a one-level slope, and the trio families connect
 * WITHOUT the curves (the alwaysStraight gate). And the boundary: a
 * rail never re-orients on a neighbour's REMOVAL — the walk runs at the
 * placement alone (the signal re-walk is the redstone seam's own row).</p>
 *
 * <p><b>The gates</b> (canBePlaced lines 71-73 + the collision-null law
 * B7): the floor consult is the has-solid-top family (a bottom slab or
 * a fence is NOT a rail bed), and the body-collision consult the
 * pre-audit engine carried is gone — a vanilla rail plants into the
 * placer's own cell.</p>
 *
 * <p><b>The support pops</b> (neighborChanged lines 86-111): the floor
 * break pops the rail with its item, the ASCENDING run's own break (the
 * run cell at the RAIL'S OWN LEVEL — pos.east(), not the bed below)
 * pops it the same way, the trio's pop drops its family item, an
 * imported rail over a bottom slab pops on its first wake, and the
 * OWN-COMMIT EXEMPTION (World.updateNeighbors lines 342-349 — the
 * changed cell never notifies ITSELF) keeps a directly-written floating
 * rail standing exactly like vanilla's until a neighbour changes.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RailPlacementParityTest {

    private EngineServer server;

    @TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "railparity", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** Joins a fresh survival player holding four rails, aimed at the yaw. */
    private PlayerSession railer(String name, ItemType railItem, float yaw) {
        PlayerSession player = join(name);
        player.inventory().setSlot(0, ItemStack.of(railItem, 4));
        player.inventory().selectHotbarSlot(0);
        player.applyMovement(player.position(), new Rotation(yaw, 0.0f), true);
        return player;
    }

    /** Runs one right-click use on the tick thread (the empty claim form). */
    private void useOn(PlayerSession player, BlockPosition at, int face) {
        server.useItemOnBlock(player, at, face, Optional.empty(), windowId -> { });
    }

    private void stone(BlockPosition at) {
        server.ticker().submit(() -> server.world().setBlock(at, BuiltinBlocks.STONE));
    }

    private void awaitStone(BlockPosition at) throws InterruptedException {
        await(() -> server.world().getBlock(at).equals(BuiltinBlocks.STONE), "stone at " + at);
    }

    private String nameAt(BlockPosition at) {
        return server.world().getBlock(at).identifier().value();
    }

    /**
     * One scene's origin, with the player TELEPORTED two cells south of
     * it (the handler's own 8-block body gate, handlePlayerUse lines
     * 463-472: a use beyond eight blocks answers nothing — every seat
     * must stand next to its scene) aimed at the given yaw.
     */
    private int[] scene(PlayerSession player, int seat, float yaw) {
        int x = (int) Math.floor(player.position().x()) + 20 + seat * 10;
        int y = (int) Math.floor(player.position().y());
        int z = (int) Math.floor(player.position().z());
        player.applyMovement(new Position(x, y, z + 2), new Rotation(yaw, 0.0f), true);
        return new int[]{x, y, z};
    }

    // ------------------------------------------------- the default, never the look

    @Test
    void aLoneRailLandsNorthSouthWhateverTheLook() throws Exception {
        server = boot();
        ItemType railItem = net.zaminmc.torch.server.item.BuiltinItems.RAIL;
        // The pre-audit engine's +45 band: a west look (yaw 90) picked
        // east-west; vanilla places the default north-south and no walk
        // re-orients a LONE rail (RailBlock line 15 — the look is never
        // consulted anywhere in the family).
        PlayerSession westLooker = railer("LookW", railItem, 90.0f);
        int[] a = scene(westLooker, 0, 90.0f);
        BlockPosition floorA = new BlockPosition(a[0], a[1] - 1, a[2]);
        stone(floorA);
        awaitStone(floorA);
        useOn(westLooker, floorA, 1);
        await(() -> "rail".equals(nameAt(floorA.offset(0, 1, 0))),
                "a west look still lands the default north-south rail");

        PlayerSession eastLooker = railer("LookE", railItem, 270.0f);
        int[] b = scene(eastLooker, 1, 270.0f);
        BlockPosition floorB = new BlockPosition(b[0], b[1] - 1, b[2]);
        stone(floorB);
        awaitStone(floorB);
        useOn(eastLooker, floorB, 1);
        await(() -> "rail".equals(nameAt(floorB.offset(0, 1, 0))),
                "an east look lands the same default");
    }

    // ----------------------------------------------------------- the connection walk

    @Test
    void theWalkBendsBothThePlacedAndTheExistingRail() throws Exception {
        server = boot();
        PlayerSession placer = railer("Pair",
                net.zaminmc.torch.server.item.BuiltinItems.RAIL, 0.0f);
        int[] o = scene(placer, 0, 0.0f);
        BlockPosition floorA = new BlockPosition(o[0], o[1] - 1, o[2]);
        BlockPosition floorB = floorA.offset(1, 0, 0);
        stone(floorA);
        stone(floorB);
        awaitStone(floorA);
        awaitStone(floorB);
        BlockPosition railA = floorA.offset(0, 1, 0);
        BlockPosition railB = floorB.offset(0, 1, 0);
        useOn(placer, floorA, 1);
        await(() -> "rail".equals(nameAt(railA)), "the first rail lands the default");
        // The second rail, EAST of the first: the placed rail re-orients
        // to the existing one AND the existing rail re-orients back —
        // the reciprocation loop (updateState lines 460-468).
        useOn(placer, floorB, 1);
        await(() -> "rail_ew".equals(nameAt(railB)),
                "the placed rail re-orients to the existing (east-west band)");
        await(() -> "rail_ew".equals(nameAt(railA)),
                "the EXISTING rail re-orients back through the reciprocation");
    }

    @Test
    void theCurveFormsFromTwoOrthogonalNeighbours() throws Exception {
        server = boot();
        PlayerSession placer = railer("Curve",
                net.zaminmc.torch.server.item.BuiltinItems.RAIL, 0.0f);
        int[] o = scene(placer, 0, 0.0f);
        BlockPosition floorNorth = new BlockPosition(o[0], o[1] - 1, o[2] - 1);
        BlockPosition floorCorner = floorNorth.offset(0, 0, 1);
        BlockPosition floorEast = floorNorth.offset(1, 0, 1);
        stone(floorNorth);
        stone(floorCorner);
        stone(floorEast);
        awaitStone(floorNorth);
        awaitStone(floorCorner);
        awaitStone(floorEast);
        BlockPosition railNorth = floorNorth.offset(0, 1, 0);
        BlockPosition railEast = floorEast.offset(0, 1, 0);
        BlockPosition corner = floorCorner.offset(0, 1, 0);
        useOn(placer, floorNorth, 1);
        await(() -> "rail".equals(nameAt(railNorth)), "the north rail lands");
        useOn(placer, floorEast, 1);
        await(() -> "rail".equals(nameAt(railEast)), "the east rail lands");
        // The corner: north + east flags, no south, no west — the plain
        // rail's own curve branch (updateState lines 380-382, the
        // alwaysStraight gate open for the plain family ALONE).
        useOn(placer, floorCorner, 1);
        await(() -> "rail_north_east".equals(nameAt(corner)),
                "the corner forms the NORTH_EAST curve");
    }

    @Test
    void aOneLevelSlopeUpgradesTheGroundRailToAscending() throws Exception {
        server = boot();
        PlayerSession placer = railer("Slope",
                net.zaminmc.torch.server.item.BuiltinItems.RAIL, 0.0f);
        int[] o = scene(placer, 0, 0.0f);
        BlockPosition floor = new BlockPosition(o[0], o[1] - 1, o[2]);
        BlockPosition step = floor.offset(0, 1, 0).offset(0, 0, -1); // the raised bed one north
        stone(floor);
        stone(step);
        awaitStone(floor);
        awaitStone(step);
        BlockPosition upperRail = step.offset(0, 1, 0);
        BlockPosition groundRail = floor.offset(0, 1, 0);
        useOn(placer, step, 1);
        await(() -> "rail".equals(nameAt(upperRail)), "the step's own rail lands the default");
        // The ground rail: its north consult finds the step's rail ONE UP
        // (getNeighborRail's up search, lines 232-235) and the upgrade
        // reads north.up (lines 432-434) — the slope's bottom rail is
        // the ASCENDING one, the top stays flat.
        useOn(placer, floor, 1);
        await(() -> "rail_ascending_north".equals(nameAt(groundRail)),
                "the slope upgrades the bottom rail to ASCENDING_NORTH");
        await(() -> "rail".equals(nameAt(upperRail)),
                "the step's own rail stays the flat default");
    }

    @Test
    void theTrioConnectsWithoutTheCurves() throws Exception {
        server = boot();
        ItemType golden = net.zaminmc.torch.server.item.BuiltinItems.GOLDEN_RAIL;
        PlayerSession placer = railer("Golden", golden, 0.0f);
        // The pair: the placed golden rail and the existing one both
        // re-orient east-west — the walk carries the trio too (onAdded
        // lines 76-78 run for every family).
        int[] o = scene(placer, 0, 0.0f);
        BlockPosition floorA = new BlockPosition(o[0], o[1] - 1, o[2]);
        BlockPosition floorB = floorA.offset(1, 0, 0);
        stone(floorA);
        stone(floorB);
        awaitStone(floorA);
        awaitStone(floorB);
        useOn(placer, floorA, 1);
        await(() -> "golden_rail".equals(nameAt(floorA.offset(0, 1, 0))),
                "the first golden rail lands its default");
        useOn(placer, floorB, 1);
        await(() -> "golden_rail_east_west".equals(nameAt(floorB.offset(0, 1, 0))),
                "the placed golden rail re-orients east-west");
        await(() -> "golden_rail_east_west".equals(nameAt(floorA.offset(0, 1, 0))),
                "the existing golden rail re-orients back");
        // The alwaysStraight exclusion (the SHAPE predicate,
        // PoweredRailBlock lines 13-24): a golden corner NEVER curves —
        // the null fallback's own bands answer east-west.
        // (The stack re-provisioned: scene A consumed two of the four
        // rails and scene B needs three — the fifth use would find an
        // empty hand.)
        placer.inventory().setSlot(0, ItemStack.of(golden, 4));
        int[] p = scene(placer, 1, 0.0f);
        BlockPosition floorNorth = new BlockPosition(p[0], p[1] - 1, p[2] - 1);
        BlockPosition floorCorner = floorNorth.offset(0, 0, 1);
        BlockPosition floorEast = floorNorth.offset(1, 0, 1);
        stone(floorNorth);
        stone(floorCorner);
        stone(floorEast);
        awaitStone(floorNorth);
        awaitStone(floorCorner);
        awaitStone(floorEast);
        useOn(placer, floorNorth, 1);
        await(() -> "golden_rail".equals(nameAt(floorNorth.offset(0, 1, 0))),
                "north golden lands");
        useOn(placer, floorEast, 1);
        await(() -> "golden_rail".equals(nameAt(floorEast.offset(0, 1, 0))),
                "east golden lands");
        useOn(placer, floorCorner, 1);
        await(() -> "golden_rail_east_west".equals(nameAt(floorCorner.offset(0, 1, 0))),
                "a golden corner falls to east-west — the curves are the plain rail's alone");
    }

    @Test
    void aRailNeverReOrientsOnANeighboursRemoval() throws Exception {
        server = boot();
        PlayerSession placer = railer("NoRewalk",
                net.zaminmc.torch.server.item.BuiltinItems.RAIL, 0.0f);
        int[] o = scene(placer, 0, 0.0f);
        BlockPosition floorA = new BlockPosition(o[0], o[1] - 1, o[2]);
        BlockPosition floorB = floorA.offset(1, 0, 0);
        stone(floorA);
        stone(floorB);
        awaitStone(floorA);
        awaitStone(floorB);
        BlockPosition railA = floorA.offset(0, 1, 0);
        BlockPosition railB = floorB.offset(0, 1, 0);
        useOn(placer, floorA, 1);
        await(() -> "rail".equals(nameAt(railA)), "the first rail lands");
        useOn(placer, floorB, 1);
        await(() -> "rail_ew".equals(nameAt(railA)) && "rail_ew".equals(nameAt(railB)),
                "the pair is east-west");
        // The boundary: the reference's neighborChanged re-walks only on a
        // redstone signal (RailBlock lines 19-23 — the honest tail); a
        // broken neighbour pops nothing and re-orients nothing — the
        // survivor KEEPS its east-west (vanilla's own write-once-after-
        // placement behavior).
        server.ticker().submit(() -> server.world().setBlock(railA, server.world().airType()));
        Thread.sleep(400);
        assertEquals("rail_ew", nameAt(railB),
                "the survivor keeps its shape — no re-walk rides the removal");
        assertEquals("air", nameAt(railA), "the broken rail is gone");
    }

    // ------------------------------------------------------------------ the gates

    @Test
    void theFloorConsultIsTheHasSolidTopFamily() throws Exception {
        server = boot();
        PlayerSession placer = railer("Floors",
                net.zaminmc.torch.server.item.BuiltinItems.RAIL, 0.0f);
        int[] o = scene(placer, 0, 0.0f);
        // A bottom slab (canBePlaced's World.hasSolidTop — the reference
        // family the fire walk already ports: NOT a rail bed).
        BlockPosition slabFloor = new BlockPosition(o[0], o[1] - 1, o[2]);
        server.ticker().submit(() ->
                server.world().setBlock(slabFloor, BuiltinBlocks.STONE_SLAB));
        await(() -> server.world().getBlock(slabFloor).equals(BuiltinBlocks.STONE_SLAB),
                "the bottom slab floor placed");
        useOn(placer, slabFloor, 1);
        Thread.sleep(300);
        assertEquals("air", nameAt(slabFloor.offset(0, 1, 0)),
                "a bottom slab is not a rail bed");
        // A fence: the isCube gate refuses it (the pre-audit engine's
        // isSolid consult accepted it). The player walks beside each
        // floor before its use — the handler's own 8-block body gate
        // (handlePlayerUse lines 463-472) refuses a distant click, and
        // the +4/+8 offsets carry the three beds apart.
        BlockPosition fenceFloor = new BlockPosition(o[0] + 4, o[1] - 1, o[2]);
        placer.applyMovement(new Position(o[0] + 4, o[1], o[2] + 2),
                new Rotation(0.0f, 0.0f), true);
        server.ticker().submit(() ->
                server.world().setBlock(fenceFloor, BuiltinBlocks.FENCE));
        await(() -> server.world().getBlock(fenceFloor).equals(BuiltinBlocks.FENCE),
                "the fence floor placed");
        useOn(placer, fenceFloor, 1);
        Thread.sleep(300);
        assertEquals("air", nameAt(fenceFloor.offset(0, 1, 0)),
                "a fence is not a rail bed");
        // The control: stone accepts.
        BlockPosition stoneFloor = new BlockPosition(o[0] + 8, o[1] - 1, o[2]);
        placer.applyMovement(new Position(o[0] + 8, o[1], o[2] + 2),
                new Rotation(0.0f, 0.0f), true);
        stone(stoneFloor);
        awaitStone(stoneFloor);
        useOn(placer, stoneFloor, 1);
        await(() -> "rail".equals(nameAt(stoneFloor.offset(0, 1, 0))),
                "stone carries the rail");
    }

    @Test
    void aRailPlantsIntoThePlacersOwnCell() throws Exception {
        server = boot();
        // The collision-null law (B7): the rail's collision shape is null
        // (AbstractRailBlock lines 38-41) and the item walk's entity
        // consult runs with collide=FALSE — a vanilla rail plants under
        // the placer's feet. The pre-audit engine's intersectsPlayerBox
        // gate refused exactly this click.
        PlayerSession placer = railer("Underfoot",
                net.zaminmc.torch.server.item.BuiltinItems.RAIL, 0.0f);
        BlockPosition feet = new BlockPosition(
                (int) Math.floor(placer.position().x()),
                (int) Math.floor(placer.position().y()),
                (int) Math.floor(placer.position().z()));
        BlockPosition floor = feet.offset(0, -1, 0);
        stone(floor);
        awaitStone(floor);
        useOn(placer, floor, 1);
        await(() -> "rail".equals(nameAt(feet)),
                "the rail plants into the placer's own cell (no body gate)");
    }

    // ------------------------------------------------------------- the support pops

    @Test
    void theSupportArmsPopTheRailWithItsDrop() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        EngineWorld world = new EngineWorld("railsupport", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(20, 20));
        ItemEntityManager items = new ItemEntityManager((x, y, z) -> false, new Random(11), 3000);
        FallingBlockEntityManager falling = new FallingBlockEntityManager(
                (x, y, z) -> false, dimension -> world, (position, stack, dimension) -> { }, new Random(12), 3100);
        BlockUpdateSystem system = new BlockUpdateSystem(world, items, falling);

        // Scene A: a plain rail on stone; the stone breaks -> the rail
        // pops with its item (neighborChanged lines 90-106).
        BlockPosition stoneA = new BlockPosition(200, 5, 200);
        BlockPosition railA = stoneA.offset(0, 1, 0);
        world.setBlock(stoneA, BuiltinBlocks.STONE);
        world.setBlock(railA, BuiltinBlocks.RAIL);
        world.setBlock(stoneA, world.airType());
        system.onBlockChanged(world, stoneA, world.airType());
        system.tick();
        assertEquals(world.airType(), world.getBlock(railA),
                "the floor break popped the rail");
        assertEquals(1, items.size(), "one pop, one item entity");
        assertEquals("minecraft:rail", items.all().get(0).stack().type().identifier().toString());

        // Scene B: the ASCENDING run's own consult (lines 94-102) — the
        // run cell is the ascent's own-direction cell at the RAIL'S LEVEL
        // (pos.east(), the step the slope climbs onto) — the floor HOLDS
        // but the run breaks -> the rail pops the same way.
        BlockPosition floorB = new BlockPosition(210, 5, 210);
        BlockPosition ascB = floorB.offset(0, 1, 0);
        BlockPosition runB = ascB.offset(1, 0, 0);
        world.setBlock(floorB, BuiltinBlocks.STONE);
        world.setBlock(runB, BuiltinBlocks.STONE);
        world.setBlock(ascB, RailBlocks.plainState(RailConnections.ASCENDING_EAST));
        world.setBlock(runB, world.airType());
        system.onBlockChanged(world, runB, world.airType());
        system.tick();
        assertEquals(world.airType(), world.getBlock(ascB),
                "the ascending run's break popped the slope rail");
        assertEquals(2, items.size(), "two pops now");
        assertEquals("minecraft:rail", items.all().get(1).stack().type().identifier().toString());

        // Scene C: the trio's own pop drops its FAMILY item (the golden
        // rail's, never the plain rail's) — ASCENDING_NORTH's run is the
        // cell one north at the rail's level.
        BlockPosition floorC = new BlockPosition(220, 5, 220);
        BlockPosition ascC = floorC.offset(0, 1, 0);
        BlockPosition runC = ascC.offset(0, 0, -1);
        world.setBlock(floorC, BuiltinBlocks.STONE);
        world.setBlock(runC, BuiltinBlocks.STONE);
        world.setBlock(ascC, RailBlocks.state("golden_rail", RailConnections.ASCENDING_NORTH, false));
        world.setBlock(runC, world.airType());
        system.onBlockChanged(world, runC, world.airType());
        system.tick();
        assertEquals(world.airType(), world.getBlock(ascC),
                "the golden slope popped with its run");
        assertEquals(3, items.size(), "three pops now");
        assertEquals("minecraft:golden_rail",
                items.all().get(2).stack().type().identifier().toString(),
                "the trio's pop drops the family item");

        // Scene D: an imported rail over a BOTTOM SLAB (the has-solid-top
        // arm, the vanilla world's own pop on its first wake) — the wake
        // is the neighbour beside the rail, never the rail itself.
        BlockPosition slabD = new BlockPosition(230, 5, 230);
        BlockPosition railD = slabD.offset(0, 1, 0);
        BlockPosition wakeD = railD.offset(1, 0, 0);
        world.setBlock(slabD, BuiltinBlocks.STONE_SLAB);
        world.setBlock(railD, BuiltinBlocks.RAIL);
        world.setBlock(wakeD, BuiltinBlocks.STONE);
        world.setBlock(wakeD, world.airType());
        system.onBlockChanged(world, wakeD, world.airType());
        system.tick();
        assertEquals(world.airType(), world.getBlock(railD),
                "a rail over a bottom slab pops on its first wake");
        assertEquals(4, items.size(), "four pops now");

        // Scene E: the control — a supported ASCENDING_SOUTH rail (its
        // own run block present) woken by an unrelated neighbour change
        // STAYS (no false pop, no re-walk).
        BlockPosition stoneE = new BlockPosition(240, 5, 240);
        BlockPosition railE = stoneE.offset(0, 1, 0);
        BlockPosition runE = railE.offset(0, 0, 1); // ASCENDING_SOUTH's own run
        BlockPosition wakeE = railE.offset(-1, 0, 0); // the unrelated wake
        world.setBlock(stoneE, BuiltinBlocks.STONE);
        world.setBlock(runE, BuiltinBlocks.STONE);
        world.setBlock(railE, RailBlocks.plainState(RailConnections.ASCENDING_SOUTH));
        world.setBlock(wakeE, BuiltinBlocks.STONE);
        world.setBlock(wakeE, world.airType());
        system.onBlockChanged(world, wakeE, world.airType());
        system.tick();
        assertEquals(RailBlocks.plainState(RailConnections.ASCENDING_SOUTH), world.getBlock(railE),
                "a supported rail keeps its shape through an unrelated wake");

        // Scene F: THE OWN-COMMIT EXEMPTION (the reference's dispatch law,
        // World.updateNeighbors lines 342-349: a block's change notifies
        // its SIX NEIGHBOURS alone — the changed cell itself never
        // receives neighborChanged): a FLOATING rail written directly by
        // setBlock (the plugin path, the harness, the import) FLOATS —
        // its own commit runs no support consult (onAdded lines 76-83
        // carry the walk alone) — and the pop comes only when a
        // NEIGHBOUR changes. The engine's {0,0,0} self-offset never
        // reaches the rail arm. (y 6: the cell BELOW must be air — the
        // flat world's grass top sits at y 4, a supported rail at y 5.)
        BlockPosition floatF = new BlockPosition(250, 6, 250); // no floor below at all
        BlockPosition neighbourF = floatF.offset(1, 0, 0);
        world.setBlock(floatF, BuiltinBlocks.RAIL);
        system.onBlockChanged(world, floatF, BuiltinBlocks.RAIL);
        system.tick();
        assertEquals(BuiltinBlocks.RAIL, world.getBlock(floatF),
                "the directly-written rail floats through its own commit");
        assertEquals(4, items.size(), "no pop, no item — the own-commit wake consults nothing");
        // The NEIGHBOUR change pops it (neighborChanged lines 86-111).
        world.setBlock(neighbourF, world.airType());
        system.onBlockChanged(world, neighbourF, world.airType());
        system.tick();
        assertEquals(world.airType(), world.getBlock(floatF),
                "the neighbour's change pops the floating rail");
        assertEquals(5, items.size(), "five pops now");
        assertEquals("minecraft:rail", items.all().get(4).stack().type().identifier().toString());
    }

    // ------------------------------------------------------------- the vocabulary

    @Test
    void allTenPlainRailStatesRegisterImportAndFold() {
        // The full ten-shape band (AbstractRailBlock.Shape lines 479-489):
        // the two historical flats plus the eight new slope/curve states,
        // each carrying its own wire nibble and its own anvil import row.
        String[] names = {"rail", "rail_ew",
                "rail_ascending_east", "rail_ascending_west",
                "rail_ascending_north", "rail_ascending_south",
                "rail_south_east", "rail_south_west",
                "rail_north_west", "rail_north_east"};
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        for (int shape = 0; shape < 10; shape++) {
            var id = net.zaminmc.torch.util.Identifier.parse("minecraft:" + names[shape]);
            Optional<BlockType> found = registry.lookup(id);
            assertTrue(found.isPresent(), names[shape] + " is registered");
            assertEquals(shape,
                    ((EngineBlockType) found.get()).legacyMetadata(),
                    names[shape] + " carries the wire nibble " + shape);
            // The anvil import resolves every vanilla shape (block 66,
            // RailBlock.getStateFromMetadata): the pre-audit import
            // isolated the slopes and the curves AS AIR.
            assertEquals(Optional.of(id), AnvilBlockIds.resolve(66, shape),
                    "(66, " + shape + ") imports as " + names[shape]);
            // The blast fold resolves every state to the family's own
            // 0.7 (no explicit rows needed — the compass suffix fold).
            assertEquals(0.7f, BlastResistanceTable.read(id), names[shape] + " folds to 0.7");
            // The reads over the band: the family shape consults.
            assertEquals(shape, RailBlocks.plainShapeOf(found.get()),
                    names[shape] + " reads its own shape id");
        }
        assertEquals("rail", RailBlocks.plainState(0).identifier().value(),
                "the bare north_south keeps the historical name");
        assertEquals("rail_ew", RailBlocks.plainState(1).identifier().value(),
                "the east-west keeps the historical name");
        assertTrue(RailBlocks.isRailFamily(RailBlocks.plainState(9)),
                "the curve is a rail family state for the walk's consults");
        assertTrue(RailBlocks.isRailFamily(RailBlocks.state("golden_rail", 2, false)),
                "the trio rides the same consult");
    }
}
