package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The complete door families (Slice 34 — DoorBlock.java + DoorItem.java
 * 1:1): the placement's facing band and hinge rule (the neighbor-solid
 * count with the same-family preference), the use swing over both halves
 * with the hinge and POWERED bits preserved, the iron door's material
 * refusal, the signal arm's open/close edge over the redstone neighbor
 * bus, and the orphan/support arms' pop-with-drop.
 */
class WoodDoorFamilyTest {

    @TempDir
    Path dataDir;

    @Test
    void thePlacementCarriesTheReferenceFacingBandAndTheHingeRule() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            });
            await(() -> solidAt(server, x, y - 1, z), "the floor placed");
            PlayerSession player = joined(server);
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the door site");
            hold(player, "minecraft:spruce_door");
            face(player, 180.0f); // looking north
            server.useItemOnBlock(player, new BlockPosition(x, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> WoodDoorFamily.isDoor(typeAt(server, x, y, z)),
                    "the spruce door's lower half lands");
            // Looking north: the facing band writes metadata 3
            // (DoorItem.place → getMetadataFromState — the exact byte the
            // 1.8.8 client decodes for a north-looking placer).
            assertEquals(3, WoodDoorFamily.metaOf(typeAt(server, x, y, z).identifier()));
            assertTrue(WoodDoorFamily.isDoor(typeAt(server, x, y + 1, z)));
            assertEquals(8, WoodDoorFamily.metaOf(typeAt(server, x, y + 1, z).identifier()));
            assertEquals("spruce", WoodDoorFamily.familyOf(typeAt(server, x, y, z)));

            // The hinge rule (DoorItem.place lines 44-54): a solid block on
            // the clockwise side (j > i) flips the right hinge — looking
            // north, clockwise is east.
            int x2 = x + 4;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x2, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x2 + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x2 + 1, y + 1, z), BuiltinBlocks.STONE);
            });
            await(() -> solidAt(server, x2 + 1, y, z), "the clockwise wall placed");
            hold(player, "minecraft:dark_oak_door");
            face(player, 180.0f); // looking north again
            server.useItemOnBlock(player, new BlockPosition(x2, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> WoodDoorFamily.isDoor(typeAt(server, x2, y, z)),
                    "the dark oak door's lower half lands");
            await(() -> WoodDoorFamily.isRightHinge(typeAt(server, x2, y + 1, z)),
                    "the solid clockwise pair set the right hinge");
            assertEquals("dark_oak", WoodDoorFamily.familyOf(typeAt(server, x2, y, z)));

            // The UP-face gate (DoorItem.useOn line 21): a side-face use
            // never places.
            int x3 = x + 8;
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x3, y - 1, z), BuiltinBlocks.STONE));
            await(() -> solidAt(server, x3, y - 1, z), "the third floor placed");
            hold(player, "minecraft:birch_door");
            face(player, 0.0f);
            server.useItemOnBlock(player, new BlockPosition(x3, y - 1, z), 2,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> true, "the side-face use settles");
            assertFalse(WoodDoorFamily.isDoor(typeAt(server, x3, y, z)),
                    "a north-face click never places a door");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSwingFlipsBothHalvesAndTheIronDoorRefusesTheUse() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 12, y = 40;
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE));
            await(() -> solidAt(server, x, y - 1, z), "the floor placed");
            PlayerSession player = joined(server);
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the door site");
            // The oak pair placed through the reference's own path (the
            // use face UP on the floor, the look south — facing band 1).
            hold(player, "minecraft:oak_door");
            face(player, 0.0f);
            server.useItemOnBlock(player, new BlockPosition(x, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> WoodDoorFamily.isDoor(typeAt(server, x, y, z)), "the oak door placed");
            assertEquals(1, WoodDoorFamily.metaOf(typeAt(server, x, y, z).identifier()));

            // The use on the LOWER half (DoorBlock.use): open both.
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> WoodDoorFamily.isOpen(typeAt(server, x, y, z)),
                    "the lower half swung open");
            assertTrue(WoodDoorFamily.isOpen(typeAt(server, x, y + 1, z)),
                    "the upper mirrors the swing (the engine's open flag)");
            assertEquals(5, WoodDoorFamily.metaOf(typeAt(server, x, y, z).identifier()));
            assertEquals(12, WoodDoorFamily.metaOf(typeAt(server, x, y + 1, z).identifier()),
                    "the manual open carries no POWERED bit (use() never writes it)");
            // The open pair is the walk-through band; the closed halves solid.
            assertTrue(WorldSolidity.isOpenDoorHalf(typeAt(server, x, y, z).identifier()));
            assertTrue(WorldSolidity.isOpenDoorHalf(typeAt(server, x, y + 1, z).identifier()));

            // The use on the UPPER half redirects down (use() lines 130-131):
            // the pair closes.
            server.useItemOnBlock(player, new BlockPosition(x, y + 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> !WoodDoorFamily.isOpen(typeAt(server, x, y, z)),
                    "the upper-half click swings the pair closed");

            // The iron door's material refusal (use() lines 125-128): the
            // click consumes but nothing swings.
            int x2 = x + 6;
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x2, y - 1, z), BuiltinBlocks.STONE));
            await(() -> solidAt(server, x2, y - 1, z), "the iron floor placed");
            server.teleportPlayer(player, new Position(x2 + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x2 + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the iron door site");
            hold(player, "minecraft:iron_door");
            face(player, 0.0f);
            server.useItemOnBlock(player, new BlockPosition(x2, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> WoodDoorFamily.isDoor(typeAt(server, x2, y, z)), "the iron door placed");
            server.useItemOnBlock(player, new BlockPosition(x2, y, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> true, "the iron-door click settles");
            assertFalse(WoodDoorFamily.isOpen(typeAt(server, x2, y, z)),
                    "the iron door refuses the swing");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSignalArmOpensTheIronDoorAndClosesItWhenTheFeedDeparts() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 14, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x - 1, y - 1, z), BuiltinBlocks.STONE);
            });
            await(() -> solidAt(server, x - 1, y - 1, z), "the floors placed");
            PlayerSession player = joined(server);
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the door site");
            hold(player, "minecraft:iron_door");
            face(player, 0.0f);
            server.useItemOnBlock(player, new BlockPosition(x, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> WoodDoorFamily.isDoor(typeAt(server, x, y, z)), "the iron door placed");
            assertFalse(WoodDoorFamily.isOpen(typeAt(server, x, y, z)),
                    "a door never evaluates its feed on placement (no onAdded arm)");
            // The torch lands NEXT to the standing door: its commit fires the
            // door's neighborChanged — the signal arm's edge.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z), RedstoneBlocks.torchStandingLit()));
            await(() -> WoodDoorFamily.isOpen(typeAt(server, x, y, z)),
                    "the torch-fed iron door swung open");
            assertTrue(WoodDoorFamily.isPowered(typeAt(server, x, y + 1, z)),
                    "the signal arm set the POWERED bit");
            assertEquals(14, WoodDoorFamily.metaOf(typeAt(server, x, y + 1, z).identifier()),
                    "the open+powered upper carries the reference's own byte");
            assertTrue(WoodDoorFamily.isOpen(typeAt(server, x, y + 1, z)));

            // A signal-held door does NOT re-toggle on an unrelated neighbor
            // change (the POWERED edge test, lines 188-189).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y + 3, z), BuiltinBlocks.STONE));
            await(() -> true, "the unrelated neighbor change settles");
            assertTrue(WoodDoorFamily.isOpen(typeAt(server, x, y, z)),
                    "the held-open door stays open through unrelated changes");

            // The feed departs: the edge test flips both bits — closed.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z), BuiltinBlocks.AIR));
            await(() -> !WoodDoorFamily.isOpen(typeAt(server, x, y, z)),
                    "the iron door closed when the torch broke");
            assertFalse(WoodDoorFamily.isPowered(typeAt(server, x, y + 1, z)),
                    "the POWERED bit dropped with the signal");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSupportLossPopsThePairAndTheOrphanedLowerDropsItsItem() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 16, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 6, y - 1, z), BuiltinBlocks.STONE);
            });
            await(() -> solidAt(server, x + 6, y - 1, z), "the floors placed");
            PlayerSession player = joined(server);
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the first door site");
            hold(player, "minecraft:birch_door");
            face(player, 0.0f);
            server.useItemOnBlock(player, new BlockPosition(x, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> WoodDoorFamily.isDoor(typeAt(server, x, y, z)), "the birch door placed");

            // The floor departs (World.hasSolidTop): the pair dies with one
            // drop (neighborChanged lines 175-186).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y - 1, z), BuiltinBlocks.AIR));
            await(() -> !WoodDoorFamily.isDoor(typeAt(server, x, y, z))
                            && !WoodDoorFamily.isDoor(typeAt(server, x, y + 1, z)),
                    "the unsupported pair died");
            // The drop: exactly one birch door item near the cell.
            await(() -> countDoorDrops(server, "minecraft:birch_door") == 1,
                    "the popped pair dropped exactly one item");

            // The orphaned lower (its upper removed by hand): the neighbor
            // arm pops it with the drop (lines 166-173).
            int x2 = x + 6;
            server.teleportPlayer(player, new Position(x2 + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x2 + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the second door site");
            hold(player, "minecraft:jungle_door");
            face(player, 0.0f);
            server.useItemOnBlock(player, new BlockPosition(x2, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> WoodDoorFamily.isDoor(typeAt(server, x2, y, z)), "the jungle door placed");
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x2, y + 1, z), BuiltinBlocks.AIR));
            await(() -> !WoodDoorFamily.isDoor(typeAt(server, x2, y, z)),
                    "the orphaned lower popped");
            await(() -> countDoorDrops(server, "minecraft:jungle_door") >= 1,
                    "the orphan's drop spawned");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFamilyTablesAndBehaviorRowsCoverEveryState() {
        for (String family : WoodDoorFamily.FAMILIES) {
            for (int meta = 0; meta < 4; meta++) {
                assertEquals(meta, WoodDoorFamily.metaOf(
                        WoodDoorFamily.lower(family, meta, false).identifier()));
                assertEquals(meta + 4, WoodDoorFamily.metaOf(
                        WoodDoorFamily.lower(family, meta, true).identifier()));
            }
            for (boolean right : new boolean[] {false, true}) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean powered : new boolean[] {false, true}) {
                        EngineBlockType upper =
                                WoodDoorFamily.upper(family, right, open, powered);
                        int meta = 8 | (right ? 1 : 0) | (powered ? 2 : 0) | (open ? 4 : 0);
                        assertEquals(meta, WoodDoorFamily.metaOf(upper.identifier()),
                                family + " " + upper.identifier().value());
                        // The behavior rows: the family item drops, the
                        // iron pair needs the pickaxe class.
                        BlockBehavior behavior = BlockBehaviorTable.of(upper.identifier())
                                .orElseThrow();
                        assertEquals(WoodDoorFamily.doorItem(family), behavior.drops().get(0).item());
                        // The light rows: the CUTOUT family passes light whole.
                        assertEquals(15, BlockLightTable.lookup(upper.identifier())
                                .orElseThrow().filter());
                    }
                }
            }
        }
        // The item forms of the five new woods exist.
        for (String wood : new String[] {"spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            assertTrue(BuiltinItems.lookup(Identifier.parse("minecraft:" + wood + "_door"))
                    .isPresent(), wood + " door item registered");
        }
    }

    // ------------------------------------------------------------- helpers

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "doors" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession joined(EngineServer server) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        }, "Doorwright", UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, "the player playing");
        return session;
    }

    private void hold(PlayerSession player, String id) {
        player.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                BuiltinItems.lookup(Identifier.parse(id)).orElseThrow(), 1));
        player.inventory().selectHotbarSlot(0);
    }

    private void face(PlayerSession player, float yaw) {
        player.applyMovement(player.position(), new Rotation(yaw, 0.0f), true);
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        return waitFor(result);
    }

    private static boolean solidAt(EngineServer server, int x, int y, int z) {
        AtomicReference<Boolean> solid = new AtomicReference<>();
        server.ticker().submit(() -> solid.set(
                WorldSolidity.isSolid(server.world().getBlock(new BlockPosition(x, y, z)))));
        return waitFor(solid);
    }

    private static int countDoorDrops(EngineServer server, String itemId) {
        AtomicReference<Integer> count = new AtomicReference<>();
        server.ticker().submit(() -> {
            int found = 0;
            for (var entity : server.itemEntities().all()) {
                if (entity.stack() != null
                        && entity.stack().type().identifier().toString().equals(itemId)) {
                    found++;
                }
            }
            count.set(found);
        });
        Integer value = waitFor(count);
        return value == null ? 0 : value;
    }

    private static <T> T waitFor(AtomicReference<T> ref) {
        long deadline = System.currentTimeMillis() + 10_000;
        T value = ref.get();
        while (value == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            value = ref.get();
        }
        return value;
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
