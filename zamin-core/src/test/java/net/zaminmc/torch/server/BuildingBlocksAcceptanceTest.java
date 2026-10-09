package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral scenarios for the building vocabulary: doors place and swing
 * both halves, break as a unit, and open the passage to bodies; ladders
 * climb (never block a body) and cancel the landing damage; the fence
 * blocks a body like a full wall in the flat-solid model.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuildingBlocksAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "build", "it", 20, 4, 20,
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

    private ItemType item(String id) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow();
    }

    private void hold(PlayerSession player, ItemType type, int count) {
        player.inventory().setSlot(0, ItemStack.of(type, count));
        player.inventory().selectHotbarSlot(0);
    }

    /** Runs one right-click use on the tick thread and waits for the application. */
    private void useOn(PlayerSession player, BlockPosition at, int face) throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
        Thread.sleep(120);
    }

    @Test
    void doorsPlaceBothHalvesSwingAndBreakAsAUnit() throws Exception {
        server = boot();
        PlayerSession builder = join("Builder");
        var world = server.world();
        // Two columns east of the join anchor: within survival reach, clear
        // of the placer's own bounding box.
        BlockPosition floor = new BlockPosition(
                (int) Math.floor(builder.position().x()) + 2,
                (int) Math.floor(builder.position().y()) - 1,
                (int) Math.floor(builder.position().z()));
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "floor seeded");
        BlockPosition lower = floor.offset(0, 1, 0);
        BlockPosition upper = floor.offset(0, 2, 0);

        // Placement: the door item on the floor's top face commits both halves.
        hold(builder, item("minecraft:oak_door"), 2);
        useOn(builder, floor, 1);
        assertTrue(EngineServer.isDoorHalf(world.getBlock(lower)), "the lower half commits");
        assertEquals(BuiltinBlocks.OAK_DOOR_UPPER, world.getBlock(upper),
                "the upper half rides along");
        assertEquals(1, builder.inventory().held().count(), "one door item spent");

        // Swing: the right click opens the passage (both halves flip).
        useOn(builder, lower, 1);
        assertTrue(EngineServer.isDoorHalf(world.getBlock(lower)), "the lower survives the swing");
        assertTrue(WorldSolidity.isOpenDoorHalf(world.getBlock(lower).identifier()),
                "the open half stops blocking bodies");
        assertFalse(WorldSolidity.isSolid(world.getBlock(lower)), "bodies pass the open door");
        assertTrue(WorldSolidity.isOpenDoorHalf(world.getBlock(upper).identifier()),
                "the upper mirrors the swing");

        // Break the upper half: the lower dies with it (the two-block unit).
        // Creative break removes the block without a dig timer.
        server.blockInteraction().submitCreativeBreak(builder, lower);
        Thread.sleep(80);
        await(() -> world.getBlock(lower).equals(BuiltinBlocks.AIR), "the lower half breaks");
        await(() -> world.getBlock(upper).equals(BuiltinBlocks.AIR),
                "the sibling upper half cleans up");
        server.shutdown(null);
    }

    @Test
    void laddersAndFencesCarryTheRightBodySemantics() {
        // Ladders: climbable — never a body blocker, transparent to light.
        assertFalse(WorldSolidity.isSolid(BuiltinBlocks.LADDER_NORTH), "ladders never block");
        assertFalse(WorldSolidity.isSolid(BuiltinBlocks.LADDER_EAST), "every facing agrees");
        assertTrue(WorldSolidity.isLadder(BuiltinBlocks.LADDER_NORTH.identifier()),
                "the ladder identity registers");
        // Fences: the flat-solid model stands in for the 1.5-block collision.
        assertTrue(WorldSolidity.isSolid(BuiltinBlocks.FENCE), "fences block bodies");
        // Closed doors block; open halves do not.
        assertTrue(WorldSolidity.isSolid(BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W),
                "the closed door blocks");
        assertTrue(WorldSolidity.isSolid(BuiltinBlocks.OAK_DOOR_UPPER),
                "the closed upper blocks");
        assertFalse(WorldSolidity.isSolid(BuiltinBlocks.OAK_DOOR_LOWER_OPEN_S),
                "the open passage");
    }
}
