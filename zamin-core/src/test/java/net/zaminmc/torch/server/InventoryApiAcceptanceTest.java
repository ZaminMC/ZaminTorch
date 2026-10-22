package net.zaminmc.torch.server;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.entity.Vehicle;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The inventory API wave (§27.3's player-inventory + container-write
 * families): the slot-level reads/writes over {@link Player#inventory()}
 * (the 36-slot grid + the armor row, the kind-gated armor writes, the
 * wire re-sync), the container slot writes over
 * {@link World#containers()} (chest/furnace/hopper, the §27.6
 * ownership-asserted arms), and the chest minecart's rolling cargo
 * ({@link Vehicle#cargo()}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryApiAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.SURVIVAL);
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

    @Test
    void thePlayerSlotsAndArmorThroughThePublicView() throws Exception {
        server = boot();
        try {
            PlayerSession session = join("Iva");
            Player.Inventory view = session.inventory();

            assertEquals(36, view.slotCount(), "the historical 36-slot grid");
            assertEquals(4, view.armorSlotCount(), "the four armor slots");
            assertTrue(view.slot(0).isEmpty(), "a fresh inventory is empty");
            assertTrue(view.slot(99).isEmpty(), "the out-of-range read answers empty");

            // The write + read round trip through the owner path.
            server.world().execute(() ->
                    view.setSlot(4, ItemStack.of(BuiltinItems.DIAMOND, 17)));
            awaitSettled();
            assertEquals(17, view.slot(4).count(), "the written stack reads back");
            assertEquals(BuiltinItems.DIAMOND, view.slot(4).type());

            // The slots() copy is a copy — mutating it never touches the engine.
            ItemStack[] copies = view.slots();
            assertEquals(36, copies.length);
            copies[4] = ItemStack.EMPTY;
            assertEquals(17, view.slot(4).count(), "the engine slot is untouched");

            // The hotbar select (0..8) + the held read.
            server.world().execute(() -> view.selectHotbarSlot(4));
            awaitSettled();
            assertEquals(4, view.heldSlot(), "the selected hotbar slot");
            assertEquals(17, session.heldItem().count(), "the held item resolves the slot");

            // The armor ladder: the kind gate refuses a sword at the head,
            // accepts a helmet; an empty stack clears.
            assertTrue(view.armorAt(0).isEmpty(), "the head slot starts empty");
            java.util.concurrent.atomic.AtomicBoolean refused =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            server.world().execute(() -> {
                // A diamond sword is not armor — the kind rule refuses it.
                refused.set(view.setArmor(0, ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)));
            });
            awaitSettled();
            assertFalse(refused.get(), "the sword is refused at the head slot");
            assertTrue(view.armorAt(0).isEmpty(), "nothing landed");

            // The matching helmet lands; the armor row reads it back.
            server.world().execute(() ->
                    view.setArmor(0, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1)));
            awaitSettled();
            assertEquals(BuiltinItems.DIAMOND_HELMET, view.armorAt(0).type(),
                    "the helmet landed at the head slot");
            assertEquals(1, view.armorAt(0).count());
            ItemStack[] armorCopies = view.armorStacks();
            assertEquals(4, armorCopies.length);
            assertEquals(BuiltinItems.DIAMOND_HELMET, armorCopies[0].type(),
                    "the armor row copy carries the helmet");

            // The clear.
            server.world().execute(() ->
                    view.setArmor(0, ItemStack.EMPTY));
            awaitSettled();
            assertTrue(view.armorAt(0).isEmpty(), "the empty stack cleared the slot");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theContainerSlotWritesAssertTheOwnerContext() throws Exception {
        server = boot();
        try {
            BlockPosition chestAt = new BlockPosition(40, 60, 40);
            server.ticker().submit(() -> {
                server.world().setBlock(chestAt, BuiltinBlocks.CHEST);
                server.chestManager().getOrCreate(chestAt);
            });
            awaitSettled();

            World world = server.world();
            var chest = world.containers().chestAt(chestAt);
            assertTrue(chest.isPresent(), "the placed chest answers");

            // The off-lane write: the diagnosable §27.6 violation.
            AtomicReference<Throwable> caught = new AtomicReference<>();
            Thread stranger = new Thread(() -> {
                try {
                    chest.orElseThrow().setSlot(0, ItemStack.of(BuiltinItems.DIAMOND, 1));
                } catch (Throwable t) {
                    caught.set(t);
                }
            });
            stranger.start();
            stranger.join();
            assertInstanceOf(IllegalStateException.class, caught.get(),
                    "the off-lane chest write fails diagnosably");

            // The on-lane write lands and reads back.
            server.world().execute(() ->
                    chest.orElseThrow().setSlot(0, ItemStack.of(BuiltinItems.EMERALD, 9)));
            awaitSettled();
            assertEquals(9, chest.orElseThrow().slot(0).count(), "the on-lane write landed");
            assertEquals(BuiltinItems.EMERALD, chest.orElseThrow().slot(0).type());

            // The furnace + hopper writes through the same contract.
            BlockPosition furnaceAt = new BlockPosition(41, 60, 41);
            BlockPosition hopperAt = new BlockPosition(41, 61, 42);
            server.ticker().submit(() -> {
                server.world().setBlock(furnaceAt, BuiltinBlocks.FURNACE);
                server.furnaceManager().getOrCreate(furnaceAt);
                server.world().setBlock(hopperAt, HopperBlocks.hopperOf(0, true));
                server.hopper().getOrCreate(hopperAt);
            });
            awaitSettled();
            var furnace = world.containers().furnaceAt(furnaceAt);
            var hopper = world.containers().hopperAt(hopperAt);
            assertTrue(furnace.isPresent() && hopper.isPresent(), "both placed blocks answer");
            server.world().execute(() -> {
                furnace.orElseThrow().setInput(ItemStack.of(BuiltinItems.IRON_INGOT, 3));
                furnace.orElseThrow().setFuel(ItemStack.of(BuiltinItems.COAL, 2));
                hopper.orElseThrow().setSlot(1, ItemStack.of(BuiltinItems.DIAMOND, 4));
            });
            awaitSettled();
            assertEquals(3, furnace.orElseThrow().input().count(), "the furnace input write");
            assertEquals(2, furnace.orElseThrow().fuel().count(), "the furnace fuel write");
            assertEquals(4, hopper.orElseThrow().slot(1).count(), "the hopper slot write");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theChestMinecartsCargoThroughThePublicView() throws Exception {
        server = boot();
        try {
            // A rail for the cart to stand on, then the chest cart itself.
            BlockPosition railAt = new BlockPosition(50, 60, 50);
            server.ticker().submit(() -> {
                server.world().setBlock(railAt.offset(0, -1, 0), BuiltinBlocks.STONE);
                server.world().setBlock(railAt, BuiltinBlocks.RAIL);
            });
            awaitSettled();
            server.ticker().submit(() ->
                    server.vehicles().spawnChestMinecart(new Position(50.5, 60.06, 50.5)));
            awaitSettled();

            World world = server.world();
            Vehicle cart = null;
            for (Vehicle vehicle : world.vehicles()) {
                if (vehicle.vehicleKind()
                        == net.zaminmc.torch.entity.VehicleKind.CHEST_MINECART) {
                    cart = vehicle;
                }
            }
            assertTrue(cart != null, "the chest minecart answers through World.vehicles()");

            Optional<Vehicle.Cargo> cargo = cart.cargo();
            assertTrue(cargo.isPresent(), "the chest cart carries cargo");
            assertEquals(27, cargo.orElseThrow().slotCount(), "the vanilla chest grid");
            assertTrue(cargo.orElseThrow().slot(0).isEmpty(), "a fresh cart is empty");

            server.world().execute(() ->
                    cargo.orElseThrow().setSlot(6, ItemStack.of(BuiltinItems.GOLD_INGOT, 12)));
            awaitSettled();
            assertEquals(12, cargo.orElseThrow().slot(6).count(), "the cargo write reads back");
            assertEquals(BuiltinItems.GOLD_INGOT, cargo.orElseThrow().slot(6).type());

            // The slots() copy isolation.
            ItemStack[] copies = cargo.orElseThrow().slots();
            assertEquals(27, copies.length);
            copies[6] = ItemStack.EMPTY;
            assertEquals(12, cargo.orElseThrow().slot(6).count(),
                    "the engine cargo is untouched");
        } finally {
            server.shutdown(null);
        }
    }

    /** The submit drains at the head of the next tick (<= 100 ms). */
    private void awaitSettled() {
        try {
            EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
