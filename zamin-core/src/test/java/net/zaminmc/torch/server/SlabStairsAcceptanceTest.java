package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collision-shape slice's placement contracts over the real engine: the
 * slab's half rides the clicked face (UP lays the bottom, DOWN hangs the
 * top, the cursor byte splits the sides), a use against the same family's
 * slab doubles it into the parent block, and the stairs ascend away from
 * the placer. Mining a variant yields its item form.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlabStairsAcceptanceTest {

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

    private void look(PlayerSession player, float yaw) {
        player.applyMovement(player.position(),
                new net.zaminmc.torch.util.Rotation(yaw, 0.0f), true);
    }

    private void useOn(PlayerSession player, BlockPosition at, int face, int cursorY)
            throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(), cursorY,
                windowId -> { });
        Thread.sleep(120);
    }

    private BlockPosition anchorNear(PlayerSession player) {
        var world = server.world();
        return new BlockPosition(
                (int) Math.floor(player.position().x()) + 2,
                (int) Math.floor(player.position().y()) - 1,
                (int) Math.floor(player.position().z()));
    }

    @Test
    void slabsPlaceTheirHalfByTheClickedFaceAndDoubleIntoTheParent() throws Exception {
        server = boot();
        PlayerSession builder = join("Slabber");
        var world = server.world();
        BlockPosition floor = anchorNear(builder);
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "floor seeded");
        BlockPosition above = floor.offset(0, 1, 0);

        // Face UP lays the bottom half in the cell above.
        hold(builder, item("minecraft:oak_slab"), 5);
        useOn(builder, floor, 1, 0);
        assertEquals(BuiltinBlocks.OAK_SLAB, world.getBlock(above),
                "the bottom half lands on an up-click");
        assertEquals(4, builder.inventory().held().count(), "one slab spent");

        // Face DOWN hangs the top half under a block placed above the cell.
        BlockPosition ceiling = above.offset(0, 2, 0);
        server.ticker().submit(() -> world.setBlock(ceiling, BuiltinBlocks.STONE));
        await(() -> world.getBlock(ceiling).equals(BuiltinBlocks.STONE), "ceiling seeded");
        useOn(builder, ceiling, 0, 0);
        assertEquals(BuiltinBlocks.OAK_SLAB_TOP, world.getBlock(above.offset(0, 1, 0)),
                "the top half hangs on a down-click");

        // Side clicks split on the cursor byte (the hit offset within the
        // face, 16ths): the upper half of a side face hangs the top slab,
        // the lower half lays the bottom. The bases sit one above the grade
        // (the surface line itself is natural terrain).
        server.ticker().submit(() -> world.setBlock(floor.offset(1, 1, 0), BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor.offset(1, 1, 0)).equals(BuiltinBlocks.STONE),
                "first base seeded");
        useOn(builder, floor.offset(1, 1, 0), 5, 12);
        assertEquals(BuiltinBlocks.OAK_SLAB_TOP, world.getBlock(floor.offset(2, 1, 0)),
                "the cursor's upper half makes the top slab");
        server.ticker().submit(() -> world.setBlock(floor.offset(1, 1, 1), BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor.offset(1, 1, 1)).equals(BuiltinBlocks.STONE),
                "second base seeded");
        useOn(builder, floor.offset(1, 1, 1), 3, 3);
        assertEquals(BuiltinBlocks.OAK_SLAB, world.getBlock(floor.offset(1, 1, 2)),
                "the cursor's lower half makes the bottom slab");

        // The double: a use against the family's own half fills the clicked
        // cell with the parent material (the vanilla ItemBlock rule).
        useOn(builder, above, 1, 0);
        assertEquals(BuiltinBlocks.OAK_PLANKS, world.getBlock(above),
                "the double slab is the parent material");
        assertEquals(0, builder.inventory().held().count(), "every slab spent");
    }

    @Test
    void stairsAscendAwayFromThePlacerAndDropTheirItem() throws Exception {
        server = boot();
        PlayerSession builder = join("StairBuilder");
        var world = server.world();
        BlockPosition floor = anchorNear(builder);
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "floor seeded");
        BlockPosition above = floor.offset(0, 1, 0);

        // The placer looks east: the stairs ascend east (the walk-up feel).
        look(builder, 270.0f);
        hold(builder, item("minecraft:oak_stairs"), 2);
        useOn(builder, floor, 1, 0);
        assertEquals(BuiltinBlocks.OAK_STAIRS_EAST, world.getBlock(above),
                "looking east lays east-ascending stairs");

        // Looking south: the south-ascending state (the Bukkit band S=2).
        look(builder, 0.0f);
        BlockPosition other = floor.offset(2, 0, 0);
        server.ticker().submit(() -> world.setBlock(other, BuiltinBlocks.STONE));
        await(() -> world.getBlock(other).equals(BuiltinBlocks.STONE), "second floor seeded");
        useOn(builder, other, 1, 0);
        assertEquals(BuiltinBlocks.OAK_STAIRS_SOUTH, world.getBlock(other.offset(0, 1, 0)),
                "looking south lays south-ascending stairs");

        // Breaking yields the item form (the facing suffix never leaks): a
        // survival dig — creative breaks drop nothing (the vanilla rule).
        server.blockInteraction().submitMiningStart(builder, above);
        Thread.sleep(4_000); // oak stairs by hand: 2.0 hardness wood, 3s nominal
        server.blockInteraction().submitMiningFinished(builder, above);
        await(() -> world.getBlock(above).equals(BuiltinBlocks.AIR), "the stairs broke");
        await(() -> server.itemEntities().all().stream()
                        .anyMatch(drop -> drop.stack().type().identifier()
                                .value().equals("oak_stairs")),
                "the oak stairs item dropped");
    }

    @Test
    void theStoneFamiliesDoublesObeyTheSameContract() throws Exception {
        server = boot();
        PlayerSession builder = join("StoneSlabber");
        var world = server.world();
        BlockPosition floor = anchorNear(builder);
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "floor seeded");
        BlockPosition above = floor.offset(0, 1, 0);

        hold(builder, item("minecraft:cobblestone_slab"), 2);
        useOn(builder, floor, 1, 0);
        assertEquals(BuiltinBlocks.COBBLESTONE_SLAB, world.getBlock(above),
                "the cobble family places its own half");
        useOn(builder, above, 1, 0);
        assertEquals(BuiltinBlocks.COBBLESTONE, world.getBlock(above),
                "the cobble double is the cobblestone cube");

        BlockPosition next = floor.offset(1, 1, 0);
        server.ticker().submit(() -> world.setBlock(floor.offset(1, 0, 0), BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor.offset(1, 0, 0)).equals(BuiltinBlocks.STONE),
                "second base seeded");
        hold(builder, item("minecraft:stone_slab"), 2);
        useOn(builder, floor.offset(1, 0, 0), 1, 0);
        assertEquals(BuiltinBlocks.STONE_SLAB, world.getBlock(next),
                "the stone family places its own half");
        // A different family's slab never doubles the neighbor: the cobble
        // use stacks its own half in the open cell above (the vanilla rule).
        hold(builder, item("minecraft:cobblestone_slab"), 1);
        useOn(builder, next, 1, 0);
        assertEquals(BuiltinBlocks.STONE_SLAB, world.getBlock(next),
                "the stone half survives the cross-family use");
        assertEquals(BuiltinBlocks.COBBLESTONE_SLAB, world.getBlock(next.offset(0, 1, 0)),
                "the cobble slab stacks in the open cell above");
    }
}
