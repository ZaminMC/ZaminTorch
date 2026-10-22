package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The stairs' true two-box collision geometry (the two-box slice — the
 * ledger's last deferred stair row): StairsBlock.addCollisions 1:1, the
 * base half slab plus the tall back on the facing side, with the
 * reference's own virtual corner SHAPE resolved against the neighbours —
 * the outer corner's narrowed quarter back, the inner corner's third
 * step box. The straight form's walk-up ladder (support 0.5 on the low
 * side, 1.0 on the tall), the top form's descended back, and the live
 * dropped item resting on both surfaces through the engine's own
 * shape-aware ground.
 */
class StairsCollisionGeometryTest {

    // --- the straight law ---------------------------------------------------

    @Test
    void theStraightTwoBoxLawOverEveryFacingAndHalf() {
        // The four bottom facings, the isolated form (no neighbours): the
        // base slab fills the lower band everywhere, the tall back rises
        // on the FACING side only — the walk-up geometry. The back's
        // expected cell fractions per facing (isInnerStair's own arms).
        assertStraightBottom(BuiltinBlocks.OAK_STAIRS_EAST, 0.5, 1.0, 0.0, 1.0);
        assertStraightBottom(BuiltinBlocks.OAK_STAIRS_WEST, 0.0, 0.5, 0.0, 1.0);
        assertStraightBottom(BuiltinBlocks.OAK_STAIRS_SOUTH, 0.0, 1.0, 0.5, 1.0);
        assertStraightBottom(BuiltinBlocks.OAK_STAIRS_NORTH, 0.0, 1.0, 0.0, 0.5);
        // The four top facings: the base slab fills the upper band, the
        // back descends into the lower band on the facing side.
        assertStraightTop(BuiltinBlocks.OAK_STAIRS_EAST_TOP, 0.5, 1.0, 0.0, 1.0);
        assertStraightTop(BuiltinBlocks.OAK_STAIRS_WEST_TOP, 0.0, 0.5, 0.0, 1.0);
        assertStraightTop(BuiltinBlocks.OAK_STAIRS_SOUTH_TOP, 0.0, 1.0, 0.5, 1.0);
        assertStraightTop(BuiltinBlocks.OAK_STAIRS_NORTH_TOP, 0.0, 1.0, 0.0, 0.5);
    }

    /** @param backMinX..backMaxZ the tall back's expected cell fractions. */
    private void assertStraightBottom(net.zaminmc.torch.block.BlockType stair,
            double backMinX, double backMaxX, double backMinZ, double backMaxZ) {
        CollisionShapes.NeighborProbe probe = CollisionShapes.ISOLATED;
        // The base slab: the whole lower band, everywhere in the cell.
        assertTrue(CollisionShapes.contains(stair, 0.25, 0.25, 0.25, probe),
                "the base slab's lower band collides");
        assertTrue(CollisionShapes.contains(stair, 0.75, 0.25, 0.75, probe),
                "the base slab covers the whole footprint");
        // The tall back: the facing half of the upper band. The open
        // point is the OPPOSITE half on the back's own axis.
        boolean xBack = backMinZ == 0.0 && backMaxZ == 1.0;
        double midX = (backMinX + backMaxX) / 2;
        double midZ = (backMinZ + backMaxZ) / 2;
        double openX = xBack ? (backMinX >= 0.5 ? 0.25 : 0.75) : midX;
        double openZ = xBack ? midZ : (backMinZ >= 0.5 ? 0.25 : 0.75);
        assertTrue(CollisionShapes.contains(stair, midX, 0.75, midZ, probe),
                "the tall back rises on the facing side");
        assertTrue(!CollisionShapes.contains(stair, openX, 0.75, openZ, probe),
                "the low side's upper band stays open");
        // The support ladder: the base slab's 0.5 on the low side, the
        // tall back's 1.0 on the facing side.
        assertEquals(0.5, CollisionShapes.top(stair, openX, 0.1, openZ, probe), 1e-9,
                "the low side supports at the half");
        assertEquals(1.0, CollisionShapes.top(stair, midX, 0.6, midZ, probe), 1e-9,
                "the tall side supports at the ceiling");
    }

    private void assertStraightTop(net.zaminmc.torch.block.BlockType stair,
            double backMinX, double backMaxX, double backMinZ, double backMaxZ) {
        CollisionShapes.NeighborProbe probe = CollisionShapes.ISOLATED;
        // The base slab: the whole upper band.
        assertTrue(CollisionShapes.contains(stair, 0.25, 0.75, 0.25, probe),
                "the top form's upper band collides");
        // The back descends into the lower band on the facing side.
        boolean xBack = backMinZ == 0.0 && backMaxZ == 1.0;
        double midX = (backMinX + backMaxX) / 2;
        double midZ = (backMinZ + backMaxZ) / 2;
        double openX = xBack ? (backMinX >= 0.5 ? 0.25 : 0.75) : midX;
        double openZ = xBack ? midZ : (backMinZ >= 0.5 ? 0.25 : 0.75);
        assertTrue(CollisionShapes.contains(stair, midX, 0.25, midZ, probe),
                "the back descends on the facing side");
        assertTrue(!CollisionShapes.contains(stair, openX, 0.25, openZ, probe),
                "the low side's lower band stays open");
        // The support: the top form's surface is the cell ceiling.
        assertEquals(1.0, CollisionShapes.top(stair, 0.25, 0.6, 0.5, probe), 1e-9,
                "the top form supports at the ceiling");
    }

    @Test
    void theLegacyYOnlyViewKeepsTheBaseColumn() {
        // The Y-only legacy view (the isolated centre column): the base
        // half law of the old pin survives the two-box model over every
        // facing — the centre column's lower band solid, and the north
        // top form's centre column still open above (its back misses the
        // centre exactly like the reference's own box).
        for (net.zaminmc.torch.block.BlockType stair : new net.zaminmc.torch.block.BlockType[]{
                BuiltinBlocks.OAK_STAIRS_EAST, BuiltinBlocks.OAK_STAIRS_WEST,
                BuiltinBlocks.OAK_STAIRS_SOUTH, BuiltinBlocks.OAK_STAIRS_NORTH,
                BuiltinBlocks.COBBLESTONE_STAIRS_EAST, BuiltinBlocks.COBBLESTONE_STAIRS_WEST,
                BuiltinBlocks.COBBLESTONE_STAIRS_SOUTH, BuiltinBlocks.COBBLESTONE_STAIRS_NORTH}) {
            assertTrue(WorldSolidity.isSolidAt(stair, 64.25),
                    "the base half collides on the centre column");
        }
        assertFalse(WorldSolidity.isSolidAt(BuiltinBlocks.OAK_STAIRS_NORTH, 64.6),
                "the north back misses the centre column (its box is z 0-0.5)");
        // The east back CONTAINS the centre column exactly at its minX
        // edge (the box's own inclusive bound): the support reads the
        // back's ceiling from the upper band.
        assertEquals(64.5, WorldSolidity.supportY(BuiltinBlocks.COBBLESTONE_STAIRS_WEST, 64.1), 1e-9,
                "the west back misses the centre column: the base half support");
        assertEquals(65.0, WorldSolidity.supportY(BuiltinBlocks.OAK_STAIRS_EAST, 64.75), 1e-9,
                "the east back holds the centre column edge: the ceiling support");
    }

    // --- the corner resolution ----------------------------------------------

    /** A probe over a fixed map of offsets to block values. */
    private static CollisionShapes.NeighborProbe probeOver(Map<String, String> cells) {
        return (dx, dy, dz) -> {
            String value = cells.get(dx + "," + dz);
            return value == null ? null
                    : new net.zaminmc.torch.server.block.EngineBlockType(
                            Identifier.parse("minecraft:" + value), value);
        };
    }

    @Test
    void theOuterCornerNarrowsTheBackToTheQuarter() {
        // Facing EAST, bottom: the FRONT (east) neighbour is a
        // same-half stair facing north — a different family, proving the
        // reference's own instanceof law — and the south cell holds no
        // same-same stair: the back narrows to the NORTHEAST quarter.
        Map<String, String> cells = new HashMap<>();
        cells.put("1,0", "cobblestone_stairs_north");
        CollisionShapes.NeighborProbe probe = probeOver(cells);
        net.zaminmc.torch.block.BlockType stair = BuiltinBlocks.OAK_STAIRS_EAST;
        assertTrue(CollisionShapes.contains(stair, 0.75, 0.75, 0.25, probe),
                "the narrowed quarter (northeast) collides");
        assertTrue(!CollisionShapes.contains(stair, 0.75, 0.75, 0.75, probe),
                "the quarter's southwest neighbour inside the back is open");
        assertTrue(!CollisionShapes.contains(stair, 0.25, 0.75, 0.25, probe),
                "the low side's upper band stays open");
        assertEquals(1.0, CollisionShapes.top(stair, 0.75, 0.55, 0.25, probe), 1e-9,
                "the quarter supports at the ceiling (read from above the base band)");
        assertEquals(0.5, CollisionShapes.top(stair, 0.75, 0.55, 0.75, probe), 1e-9,
                "the narrowed-away side leaves the base half support");

        // The mirrored SOUTH arm: facing SOUTH, the front (south)
        // neighbour facing west, the east cell open: the back narrows to
        // the SOUTHWEST quarter.
        Map<String, String> southCells = new HashMap<>();
        southCells.put("0,1", "cobblestone_stairs_west");
        CollisionShapes.NeighborProbe southProbe = probeOver(southCells);
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_STAIRS_SOUTH,
                0.25, 0.75, 0.75, southProbe),
                "the narrowed southwest quarter collides");
        assertTrue(!CollisionShapes.contains(BuiltinBlocks.OAK_STAIRS_SOUTH,
                0.75, 0.75, 0.75, southProbe),
                "the southeast quarter inside the back is open");
    }

    @Test
    void theSameSameBlockerKeepsTheStraightBack() {
        // The same outer-corner arrangement, but the blocker cell (south)
        // holds a stair of the SAME half AND facing as the queried state:
        // the reference's isStairSameHalfSameFacing refusal — the back
        // stays the full facing half.
        Map<String, String> cells = new HashMap<>();
        cells.put("1,0", "cobblestone_stairs_north");
        cells.put("0,1", "oak_stairs_east");
        CollisionShapes.NeighborProbe probe = probeOver(cells);
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_STAIRS_EAST,
                0.75, 0.75, 0.75, probe),
                "the blocker keeps the whole east back");
    }

    @Test
    void theHalfMismatchNeverFormsACorner() {
        // The front neighbour on the OTHER half (a top form against the
        // queried bottom): the reference's same-half gate — no corner,
        // the straight back stays.
        Map<String, String> cells = new HashMap<>();
        cells.put("1,0", "cobblestone_stairs_north_top");
        CollisionShapes.NeighborProbe probe = probeOver(cells);
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_STAIRS_EAST,
                0.75, 0.75, 0.75, probe),
                "the half mismatch keeps the whole east back");
    }

    @Test
    void theInnerCornerAddsTheThirdStepBox() {
        // Facing EAST, bottom: the BEHIND (west) neighbour is a same-half
        // stair facing north and the north cell holds no same-same stair:
        // the inner corner's step box — the NORTHWEST quarter of the base
        // band, the third box beside the whole east back.
        Map<String, String> cells = new HashMap<>();
        cells.put("-1,0", "cobblestone_stairs_north");
        CollisionShapes.NeighborProbe probe = probeOver(cells);
        net.zaminmc.torch.block.BlockType stair = BuiltinBlocks.OAK_STAIRS_EAST;
        assertTrue(CollisionShapes.contains(stair, 0.25, 0.75, 0.25, probe),
                "the step quarter (northwest) collides");
        assertTrue(!CollisionShapes.contains(stair, 0.25, 0.75, 0.75, probe),
                "the southwest quarter stays open (no step there)");
        assertTrue(CollisionShapes.contains(stair, 0.75, 0.75, 0.75, probe),
                "the east back stays whole beside the step");
        assertEquals(1.0, CollisionShapes.top(stair, 0.25, 0.55, 0.25, probe), 1e-9,
                "the step supports at the ceiling (read from above the base band)");
    }

    @Test
    void theNonStairNeighboursNeverFormACorner() {
        // A full cube (stone) in the front and behind cells: the
        // reference's isStair gate — only stairs corner, the straight
        // two-box law stays.
        Map<String, String> cells = new HashMap<>();
        cells.put("1,0", "stone");
        cells.put("-1,0", "stone");
        CollisionShapes.NeighborProbe probe = probeOver(cells);
        net.zaminmc.torch.block.BlockType stair = BuiltinBlocks.OAK_STAIRS_EAST;
        assertTrue(CollisionShapes.contains(stair, 0.75, 0.75, 0.25, probe),
                "the straight back's northeast half collides");
        assertTrue(CollisionShapes.contains(stair, 0.75, 0.75, 0.75, probe),
                "the straight back's southeast half collides");
        assertTrue(!CollisionShapes.contains(stair, 0.25, 0.75, 0.25, probe),
                "no step box appears beside the back");
    }

    // --- the parse law ------------------------------------------------------

    @Test
    void theParseLawOverEveryFamily() {
        // The thirteen families' eight states each parse; the red
        // sandstone joins through its own names.
        String[] families = {
                "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                "cobblestone", "brick", "stone_brick", "nether_brick",
                "sandstone", "quartz", "red_sandstone"};
        for (String family : families) {
            for (String facing : new String[]{"east", "west", "south", "north"}) {
                String bottom = family + "_stairs_" + facing;
                String top = bottom + "_top";
                assertTrue(StairsBlocks.isStair(Identifier.parse("minecraft:" + bottom)),
                        bottom + " parses");
                assertTrue(StairsBlocks.isStair(Identifier.parse("minecraft:" + top)),
                        top + " parses");
                assertEquals(facing, StairsBlocks.facingOf(bottom));
                assertEquals(facing, StairsBlocks.facingOf(top));
                assertFalse(StairsBlocks.isTop(bottom), bottom + " is the bottom half");
                assertTrue(StairsBlocks.isTop(top), top + " is the top half");
            }
        }
        // The bare family items carry no facing segment: never a state.
        assertFalse(StairsBlocks.isStairState("oak_stairs"));
        assertFalse(StairsBlocks.isStairState("red_sandstone_stairs"));
        assertFalse(StairsBlocks.isStairState("stone"));
        // The same-half-same-facing read: the family never matters, the
        // half and the facing always do.
        assertTrue(StairsBlocks.sameHalfSameFacing(
                "cobblestone_stairs_east", "oak_stairs_east"));
        assertFalse(StairsBlocks.sameHalfSameFacing(
                "cobblestone_stairs_east_top", "oak_stairs_east"));
        assertFalse(StairsBlocks.sameHalfSameFacing(
                "cobblestone_stairs_west", "oak_stairs_east"));
        assertFalse(StairsBlocks.sameHalfSameFacing(
                "stone", "oak_stairs_east"));
    }

    // --- the live arm ---------------------------------------------------------

    @TempDir
    Path dataDir;

    @Test
    void theDroppedItemRestsOnBothStairSurfaces() throws Exception {
        EngineServer server = boot();
        try {
            // One bottom EAST stair at (0, 64, 0): the tall back on the
            // east (+X) half, the base slab's low half to the west. Two
            // drops — one over each surface — read the two-box law
            // through the engine's own shape-aware ground (the live
            // probe resolving the stair against the air around it).
            BlockPosition stairAt = new BlockPosition(0, 64, 0);
            server.ticker().submit(() ->
                    server.world().setBlock(stairAt, BuiltinBlocks.OAK_STAIRS_EAST));
            await(() -> server.world().getBlock(stairAt)
                    .identifier().value().equals("oak_stairs_east"),
                    "the stair placed");

            // The tall side: the east half's centre (x 0.75).
            server.ticker().submit(() -> server.itemEntities().spawnThrown(
                    new Position(0.75, 65.3, 0.5),
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.EMERALD, 1)));
            // The low side: the west half's centre (x 0.25).
            server.ticker().submit(() -> server.itemEntities().spawnThrown(
                    new Position(0.25, 65.3, 0.5),
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIAMOND, 1)));

            AtomicReference<Double> emeraldRest = new AtomicReference<>();
            AtomicReference<Double> diamondRest = new AtomicReference<>();
            await(() -> {
                for (var item : server.itemEntities().all()) {
                    if (item.stack().type().identifier().equals(BuiltinItems.EMERALD.identifier())
                            && item.onGround()) {
                        emeraldRest.set(item.position().y());
                    }
                    if (item.stack().type().identifier().equals(BuiltinItems.DIAMOND.identifier())
                            && item.onGround()) {
                        diamondRest.set(item.position().y());
                    }
                }
                return emeraldRest.get() != null && diamondRest.get() != null;
            }, "both items came to rest on the stair");

            // The rest = the support + the item's half height (0.125):
            // the tall back's ceiling 65.0, the base slab's half 64.5.
            assertTrue(Math.abs(emeraldRest.get() - 65.125) < 0.02,
                    "the tall side rests on the back's ceiling: " + emeraldRest.get());
            assertTrue(Math.abs(diamondRest.get() - 64.625) < 0.02,
                    "the low side rests on the base slab's half: " + diamondRest.get());

            // And they STAY (the pre-two-box model had the tall side
            // open above the base slab — the emerald fell through to
            // 64.625 like the diamond).
            EngineAwaits.awaitTicks(server.world(), 20, "engine progress (a 1000 wall settle)");
            double emeraldStill = Double.NaN;
            double diamondStill = Double.NaN;
            for (var item : server.itemEntities().all()) {
                if (item.stack().type().identifier().equals(BuiltinItems.EMERALD.identifier())) {
                    emeraldStill = item.position().y();
                }
                if (item.stack().type().identifier().equals(BuiltinItems.DIAMOND.identifier())) {
                    diamondStill = item.position().y();
                }
            }
            assertTrue(Math.abs(emeraldStill - emeraldRest.get()) < 0.01,
                    "the tall-side item stayed on the back");
            assertTrue(Math.abs(diamondStill - diamondRest.get()) < 0.01,
                    "the low-side item stayed on the slab");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "scg" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
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
