package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The chest pair's horizontal FACING states (Slice L2 — ChestBlock 1:1):
 * the placement's look-opposite walk over the Direction-id band 2-5, the
 * neighbour re-facing walk (onPlaced lines 96-125 — the perpendicular
 * same-kind neighbour adopts the new state, the along-axis corner keeps
 * the plain look-direction state, the vanilla quirk), and the family
 * consults (the manager's spill, the hopper's adapter, the trapped
 * chest's redstone probe, the comparator's analog read).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChestFacingTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "chests", "it", 20, 4, 20,
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

    private void hold(PlayerSession player, ItemType type, int count) {
        player.inventory().setSlot(0, ItemStack.of(type, count));
        player.inventory().selectHotbarSlot(0);
    }

    private void useOn(PlayerSession player, BlockPosition at, int face) throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 120 wall settle)");
    }

    @Test
    void theFacingStatesResolveTheirBand() {
        assertEquals(2, ChestFacingBlocks.plainOf(2).legacyMetadata());
        assertEquals(5, ChestFacingBlocks.trappedOf(5).legacyMetadata());
        assertEquals("minecraft:chest_north", ChestFacingBlocks.plainOf(2).identifier().toString());
        assertEquals("minecraft:trapped_chest_east",
                ChestFacingBlocks.trappedOf(5).identifier().toString());
        // The consults: the family membership over both kinds.
        assertTrue(ChestFacingBlocks.isPlainFamily(ChestFacingBlocks.plainOf(3)));
        assertTrue(ChestFacingBlocks.isTrappedFamily(ChestFacingBlocks.trappedOf(4)));
        assertTrue(!ChestFacingBlocks.isPlainFamily(ChestFacingBlocks.trappedOf(4)),
                "a mixed family never forms");
        assertTrue(ChestFacingBlocks.isAnyFacing(ChestFacingBlocks.plainOf(2)));
        // The look consults: the opposite for the placed state, the look's
        // own id for the quirk arm.
        assertEquals(2, ChestFacingBlocks.lookOppositeOf(0)); // south look -> north front
        assertEquals(3, ChestFacingBlocks.lookOppositeOf(2)); // north look -> south front
        assertEquals(3, ChestFacingBlocks.lookOf(0)); // the plain south look
        assertEquals(4, ChestFacingBlocks.lookOf(1)); // the plain west look
    }

    @Test
    void thePlacementWritesTheLookOppositeAndTheNeighbourWalkAligns() throws Exception {
        server = boot();
        PlayerSession builder = join("ChestBuilder");
        var world = server.world();
        ItemType chestItem = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:chest")).orElseThrow();

        // A floor one block east: the first chest commits with the default
        // south look's opposite — the north front.
        BlockPosition floor = new BlockPosition(
                (int) Math.floor(builder.position().x()) + 2,
                (int) Math.floor(builder.position().y()) - 1,
                (int) Math.floor(builder.position().z()));
        BlockPosition atop = floor.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "the floor seeded");
        hold(builder, chestItem, 4);
        useOn(builder, floor, 1);
        assertEquals("minecraft:chest_north", world.getBlock(atop).identifier().toString(),
                "the plain place writes the look-opposite state");

        // The perpendicular pair: a second chest north of the first (the
        // look still south — the facing runs north/south, the neighbour
        // sits perpendicular): the walk re-faces nothing (the neighbour is
        // along the Z axis and the facing runs along Z too — THE QUIRK arm:
        // the second chest keeps the plain look-direction state).
        BlockPosition floorNorth = floor.offset(0, 0, -1);
        BlockPosition atopNorth = floorNorth.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(floorNorth, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floorNorth).equals(BuiltinBlocks.STONE), "the north floor");
        useOn(builder, floorNorth, 1);
        assertEquals("minecraft:chest_south", world.getBlock(atopNorth).identifier().toString(),
                "the along-axis quirk keeps the plain look-direction state");
        assertEquals("minecraft:chest_north", world.getBlock(atop).identifier().toString(),
                "the first chest keeps its facing under the quirk arm");

        // The perpendicular arm: the look west (the facing runs east-west)
        // with a same-kind neighbour to the SOUTH of the target — the pair
        // aligns on the new state (the neighbour re-faces east, the new
        // chest faces east). The neighbour must be a LONE chest: the
        // reference's canBePlaced (ChestBlock lines 244-284, consulted
        // through BlockItem.useOn line 43's World.canPlace) refuses any
        // placement beside an ALREADY-DOUBLED chest — the pre-fix seat
        // placed the third chest beside the pair itself, a placement
        // vanilla never allows; the re-pinned neighbour is a fresh lone
        // chest two cells clear of the pair.
        builder.applyMovement(builder.position(),
                new net.zaminmc.torch.util.Rotation(90.0f, 0.0f), true); // the west look
        BlockPosition loneFloor = floor.offset(0, 0, -4);
        BlockPosition loneAtop = loneFloor.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(loneAtop, BuiltinBlocks.CHEST));
        await(() -> world.getBlock(loneAtop).equals(BuiltinBlocks.CHEST),
                "the lone perpendicular neighbour seeded");
        BlockPosition floorFurtherNorth = floor.offset(0, 0, -5);
        BlockPosition atopFurtherNorth = floorFurtherNorth.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(floorFurtherNorth, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floorFurtherNorth).equals(BuiltinBlocks.STONE),
                "the further-north floor");
        useOn(builder, floorFurtherNorth, 1);
        assertEquals("minecraft:chest_east", world.getBlock(atopFurtherNorth).identifier().toString(),
                "the west look's opposite writes the east front");
        assertEquals("minecraft:chest_east", world.getBlock(loneAtop).identifier().toString(),
                "the perpendicular neighbour re-faces to the new state");
        server.shutdown(null);
    }
}
