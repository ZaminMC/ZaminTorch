package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The chest minecart family's own law (Slice 21a — the reference's
 * ChestMinecartEntity over InventoryMinecartEntity): the wire's type index
 * (TrackedEntity's objectData 1), the fullness rail drag (applySlowown's
 * 0.98 + (15 - signal) * 0.001), the death's three-drop chain (the cart
 * item, the contents, the chest block — never the chest-minecart item),
 * the void's no-drop arm, the interact's chest menu (the clicks routing
 * into the cart's own 27 slots), and the item's rail placement.
 */
class ChestMinecartTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    /** A straight east-west rail over flat ground (the VehicleManagerTest shape). */
    private static VehicleEntity.WorldQuery straightRailWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public int railAxisAt(double x, double y, double z) {
                if (y < 3.9 || y > 5.0) {
                    return -1;
                }
                int cellX = (int) Math.floor(x);
                int cellZ = (int) Math.floor(z);
                return cellZ == 0 ? 1 : -1; // the east-west leg along z=0
            }
        };
    }

    @Test
    void theWireTypeIndexNamesTheChestVariant() {
        MinecartEntity plain = new MinecartEntity(2, new Position(0.5, 4.06, 0.5), new Random(1));
        assertEquals(0, plain.minecartTypeIndex(),
                "the rideable cart carries objectData 0 (no velocity triple)");
        ChestMinecartEntity chest =
                new ChestMinecartEntity(3, new Position(0.5, 4.06, 0.5), new Random(1));
        assertEquals(1, chest.minecartTypeIndex(),
                "the chest cart carries objectData 1 (the chest box + the velocity triple)");
        assertEquals(VehicleEntity.Kind.CHEST_MINECART, chest.kind());
        assertEquals(net.zaminmc.torch.entity.VehicleKind.CHEST_MINECART, chest.vehicleKind());
    }

    @Test
    void theFullnessDragsTheRailRide() {
        // applySlowown lines 167-173: an empty cart multiplies 0.995 (15 -
        // 0 signal), a full one 0.98 (15 - 15) — the fuller, the slower.
        var world = straightRailWorld();
        ChestMinecartEntity empty =
                new ChestMinecartEntity(2, new Position(0.5, 4.06, 0.5), new Random(1));
        ChestMinecartEntity full =
                new ChestMinecartEntity(3, new Position(0.5, 4.06, 0.5), new Random(1));
        for (int slot = 0; slot < 27; slot++) {
            full.chest().setSlot(slot, ItemStack.of(BuiltinItems.COAL, 64));
        }
        empty.setVelocity(0.3, 0.0, 0.0);
        full.setVelocity(0.3, 0.0, 0.0);
        for (int i = 0; i < 20; i++) {
            empty.tick(world);
            full.tick(world);
        }
        double emptySpeed = Math.abs(empty.velocityX());
        double fullSpeed = Math.abs(full.velocityX());
        // 0.3 * 0.995^20 = 0.2717; 0.3 * 0.98^20 = 0.2008 — the bands.
        assertTrue(emptySpeed > 0.26 && emptySpeed < 0.28,
                "the empty cart's drag band (0.995/tick): " + emptySpeed);
        assertTrue(fullSpeed > 0.19 && fullSpeed < 0.21,
                "the full cart's drag band (0.98/tick): " + fullSpeed);
        assertTrue(fullSpeed < emptySpeed, "the loaded cart rolls slower");
    }

    @Test
    void theBreakSpillsTheCartTheContentsAndTheChestBlock() {
        // The reference's dropItems chain: MinecartEntity.dropItems (the
        // MINECART item), InventoryMinecartEntity.dropItems (the contents),
        // ChestMinecartEntity.dropItems (a CHEST block) — never the
        // chest-minecart item.
        List<String> drops = new ArrayList<>();
        VehicleManager manager = new VehicleManager(straightRailWorld(),
                new VehicleManager.DropSink() {
                    @Override public void dropVehicleItem(VehicleEntity vehicle) {
                        drops.add(vehicle.kind() == VehicleEntity.Kind.BOAT ? "boat" : "cart");
                    }

                    @Override public void dropChestMinecartExtras(
                            VehicleEntity vehicle, List<ItemStack> contents) {
                        for (ItemStack stack : contents) {
                            drops.add("contents:" + stack.type().identifier().value()
                                    + ":" + stack.count());
                        }
                        drops.add("chest");
                    }
                },
                new Random(1), 100);
        VehicleEntity spawned = manager.spawnChestMinecart(new Position(0.5, 4.06, 0.5), 0);
        assertTrue(spawned instanceof ChestMinecartEntity, "the chest variant spawned");
        ChestMinecartEntity cart = (ChestMinecartEntity) spawned;
        cart.chest().setSlot(3, ItemStack.of(BuiltinItems.COAL, 12));
        cart.chest().setSlot(11, ItemStack.of(BuiltinItems.BREAD, 2));

        // Four 1.0 wounds with the i-frame windows between them.
        for (int wound = 0; wound < 4; wound++) {
            manager.hurt(cart, 1.0f);
            for (int i = 0; i < 11; i++) {
                manager.tick();
            }
        }
        assertTrue(cart.dead(), "four wounds broke the cart");
        manager.tick(); // the dead arm drains with the drops

        assertEquals(0, manager.all().size(), "the broken cart left the world");
        assertEquals(List.of("cart", "contents:coal:12", "contents:bread:2", "chest"),
                drops,
                "the death spilled the cart item, the contents, then the chest block");
    }

    @Test
    void theVoidConsumesTheLoadedCartWithoutDrops() {
        List<String> drops = new ArrayList<>();
        VehicleManager manager = new VehicleManager(straightRailWorld(),
                new VehicleManager.DropSink() {
                    @Override public void dropVehicleItem(VehicleEntity vehicle) {
                        drops.add("cart");
                    }

                    @Override public void dropChestMinecartExtras(
                            VehicleEntity vehicle, List<ItemStack> contents) {
                        drops.add("extras");
                    }
                },
                new Random(1), 100);
        VehicleEntity spawned = manager.spawnChestMinecart(new Position(0.5, -65.0, 0.5), 0);
        ((ChestMinecartEntity) spawned).chest().setSlot(0, ItemStack.of(BuiltinItems.COAL, 5));
        manager.tick();
        assertEquals(0, manager.all().size(), "the loaded cart below the kill plane is gone");
        assertEquals(List.of(), drops, "the void leaves neither the cart nor its contents");
    }

    // ------------------------------------------------------- the live-engine arms

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        server.ticker().submit(() -> session.setOpLevel(2));
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theInteractOpensTheChestMenuAndRoutesTheClicks() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession player = join(server, "CartOpener");
            await(() -> player.state()
                    == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the session promoted to PLAYING");

            // A chest minecart beside the player.
            var at = player.position();
            Position cartAt = new Position(Math.floor(at.x()) + 1.5,
                    Math.floor(at.y()), Math.floor(at.z()) + 0.5);
            VehicleEntity spawned = server.vehicles().spawnChestMinecart(cartAt, 0);
            assertTrue(spawned instanceof ChestMinecartEntity);
            ChestMinecartEntity cart = (ChestMinecartEntity) spawned;

            // InventoryMinecartEntity.interact lines 158-164: the use opens
            // the chest menu — never the seat.
            server.interactEntity(player, cart.entityId());
            await(() -> player.openContainerKind()
                            == PlayerSession.ContainerKind.CHEST_MINECART
                            && player.openContainerWindowId() > 0,
                    "the chest menu opened over the cart");
            assertEquals(cart.entityId(), player.containerMountId(),
                    "the window names the cart");
            assertFalse(cart.hasPassenger(), "the chest cart never takes a rider");

            // A shift-click from the player's hotbar into the cart: the
            // chest menu's own no-filter fill (wire slot 54 = hotbar 0).
            server.chatService().submitChat(player, "/give " + player.name() + " coal 7");
            await(() -> player.inventory().countOf(BuiltinItems.COAL) >= 7,
                    "the coal granted");
            server.windowClick(player, player.openContainerWindowId(),
                    54, 0, 1, ignored -> { }); // hotbar 0 shift-clicks into the cart
            await(() -> {
                for (ItemStack stack : cart.chest().snapshotSlots()) {
                    if (!stack.isEmpty()
                            && stack.type().identifier().toString().equals("minecraft:coal")) {
                        return true;
                    }
                }
                return false;
            }, "the coal moved into the cart's slots");
            assertEquals(0, player.inventory().countOf(BuiltinItems.COAL),
                    "the shift-click emptied the hand");

            // A plain take from the cart's own slot: the wire slot 0 arm.
            int filled = -1;
            for (int slot = 0; slot < 27; slot++) {
                if (!cart.chest().snapshotSlots()[slot].isEmpty()) {
                    filled = slot;
                    break;
                }
            }
            assertTrue(filled >= 0, "a filled slot exists");
            server.windowClick(player, player.openContainerWindowId(), filled, 0, 0,
                    ignored -> { });
            int finalFilled = filled;
            await(() -> cart.chest().snapshotSlots()[finalFilled].isEmpty(),
                    "the take emptied the cart slot");
            assertFalse(player.inventory().cursor().isEmpty(),
                    "the take carried the coal in the cursor");

            // The reach gate (InventoryMinecartEntity.isValid lines 83-85):
            // return the coal to the hand (the cursor deposit), shift it
            // back into the cart, walk 9 blocks away, and the click
            // refuses — the cart keeps its load (squaredDistanceTo > 64.0).
            server.windowClick(player, player.openContainerWindowId(), 54, 0, 0,
                    ignored -> { }); // the cursor deposits into the empty hotbar slot
            await(() -> player.inventory().cursor().isEmpty()
                            && !player.inventory().snapshot().get(0).isEmpty(),
                    "the coal returned to the hand");
            server.windowClick(player, player.openContainerWindowId(), 54, 0, 1,
                    ignored -> { }); // the coal shift-clicks back into the cart
            await(() -> !cart.chest().snapshotSlots()[0].isEmpty()
                            && cart.chest().snapshotSlots()[0].type().identifier().toString()
                                    .equals("minecraft:coal"),
                    "the coal refilled the cart's first slot");
            ItemStack heldByCart = cart.chest().snapshotSlots()[0];
            server.world().execute(() -> player.applyMovement(
                    new Position(cartAt.x() + 9.0, cartAt.y(), cartAt.z()),
                    player.rotation(), true));
            await(() -> Math.abs(player.position().x() - (cartAt.x() + 9.0)) < 0.01,
                    "the player moved away");
            server.windowClick(player, player.openContainerWindowId(), 0, 0, 0,
                    ignored -> { });
            EngineAwaits.awaitTicks(server.world(), 6, "the far click refused (the settle)");
            assertEquals(heldByCart, cart.chest().snapshotSlots()[0],
                    "the far click refused: the cart kept its contents");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theItemPlacesTheChestVariantOnARail() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession player = join(server, "CartPlacer");
            await(() -> player.state()
                    == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the session promoted to PLAYING");

            var at = player.position();
            BlockPositionLike railCell = new BlockPositionLike(
                    (int) Math.floor(at.x()) + 2, (int) Math.floor(at.y()),
                    (int) Math.floor(at.z()));
            var railPos = new net.zaminmc.torch.block.BlockPosition(
                    railCell.x(), railCell.y(), railCell.z());
            server.world().execute(() -> server.world().setBlock(railPos, BuiltinBlocks.RAIL_EW));
            await(() -> server.world().getBlock(railPos).equals(BuiltinBlocks.RAIL_EW),
                    "the rail landed");

            server.chatService().submitChat(player, "/give " + player.name() + " chest_minecart 1");
            await(() -> !player.inventory().held().isEmpty(), "the chest minecart granted");

            server.useItemOnBlock(player, railPos, 1, java.util.Optional.empty(), ignored -> { });
            await(() -> server.vehicles().all().stream().anyMatch(
                            v -> v.kind() == VehicleEntity.Kind.CHEST_MINECART),
                    "the chest variant spawned on the rail");
            assertTrue(player.inventory().held().isEmpty(), "the placement paid the item");
            VehicleEntity spawned = server.vehicles().all().stream()
                    .filter(v -> v.kind() == VehicleEntity.Kind.CHEST_MINECART)
                    .findFirst().orElseThrow();
            assertEquals(railCell.x() + 0.5, spawned.position().x(), 0.01,
                    "the cart centers on the rail cell");
            assertNull(server.vehicles().minecartAt(
                    spawned.position().x(), spawned.position().y(), spawned.position().z()),
                    "the chest cart is not the rideable neighbor");
        } finally {
            server.shutdown(null);
        }
    }

    /** The local cell triple (the test's own readability helper). */
    private record BlockPositionLike(int x, int y, int z) {
    }
}
