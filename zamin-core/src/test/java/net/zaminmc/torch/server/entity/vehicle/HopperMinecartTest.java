package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlockEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hopper minecart's own law (Slice 66 — the reference port of
 * HopperMinecartEntity.java): the 5-slot rolling pickup, the transfer
 * walk's cooldown cadence with the reference's own never-updated-position
 * quirk (the every-tick attempt away from the world-origin cell), the
 * activator rail's disable, the interact's hopper menu, and the
 * persistence payload.
 */
class HopperMinecartTest {

    @TempDir
    Path dataDir;

    /** The flat ground world: a floor at y=0 so a resting cart keeps its cell. */
    private static final VehicleEntity.WorldQuery GROUND_WORLD =
            new VehicleEntity.WorldQuery() {
                @Override public boolean isSolid(double x, double y, double z) {
                    return y < 0.0;
                }
            };

    /** The recording host: counts the attempts, answers the caller's verdict. */
    private static final class RecordingHost implements HopperMinecartEntity.TransferHost {
        final AtomicInteger attempts = new AtomicInteger();
        volatile boolean nextVerdict;

        @Override
        public boolean transferItems(HopperMinecartEntity cart) {
            attempts.incrementAndGet();
            return nextVerdict;
        }
    }

    private static HopperMinecartEntity cartAt(double x, double y, double z,
                                               HopperMinecartEntity.TransferHost host) {
        return new HopperMinecartEntity(9800, new Position(x, y, z), new Random(81L), host);
    }

    @Test
    void theWireTypeIndexAndTheFiveSlots() {
        RecordingHost host = new RecordingHost();
        HopperMinecartEntity cart = cartAt(0.5, 64.06, 0.5, host);
        assertEquals(5, cart.minecartTypeIndex(),
                "the hopper cart carries objectData 5 (the hopper box + the velocity triple)");
        assertEquals(VehicleEntity.Kind.HOPPER_MINECART, cart.kind());
        assertEquals(net.zaminmc.torch.entity.VehicleKind.HOPPER_MINECART, cart.vehicleKind());
        assertEquals(HopperBlockEntity.SLOT_COUNT, cart.hopper().snapshotSlots().length,
                "the five-slot container");
        assertTrue(cart.enabled(), "freshly placed: the transfer walk is armed");
        assertEquals(-1, cart.transferCooldown(), "the reference's initial -1");
        assertFalse(cart.hasTransferCooldown(), "-1 is not a holding cooldown");
    }

    @Test
    void theArmedCooldownNeverHoldsAwayFromTheOriginCell() {
        // tick lines 99-117, 1:1 with the never-reassigned f_0016592 quirk:
        // a successful pull arms cooldown 4, but the next tick's
        // changed-cell arm (the position never equals the stale ORIGIN)
        // zeroes it again — the cart attempts EVERY tick away from (0,0,0).
        RecordingHost host = new RecordingHost();
        HopperMinecartEntity cart = cartAt(100.5, 0.35, 100.5, host);
        host.nextVerdict = true;
        cart.tick(GROUND_WORLD);
        assertEquals(1, host.attempts.get(), "the first tick transferred");
        assertEquals(HopperMinecartEntity.TRANSFER_COOLDOWN, cart.transferCooldown(),
                "the successful pull armed the 4-tick cooldown");
        host.nextVerdict = false; // the next attempt fails: a held cooldown would show as 4
        cart.tick(GROUND_WORLD);
        assertEquals(0, cart.transferCooldown(),
                "the quirk zeroed the armed cooldown: the position never matches the stale origin");
        assertEquals(2, host.attempts.get(),
                "the every-tick cadence away from origin (the armed cooldown never holds)");
    }

    @Test
    void atTheWorldOriginCellTheCooldownHolds() {
        // The only place the equals arm ever holds: the cart sitting in
        // the world-origin cell — the cooldown burns down one per tick and
        // the attempt only runs when it reaches zero.
        RecordingHost host = new RecordingHost();
        HopperMinecartEntity cart = cartAt(0.5, 0.35, 0.5, host);
        host.nextVerdict = true;
        cart.tick(GROUND_WORLD);
        assertEquals(1, host.attempts.get());
        assertEquals(4, cart.transferCooldown());
        for (int i = 0; i < 3; i++) {
            cart.tick(GROUND_WORLD);
        }
        assertEquals(1, host.attempts.get(), "the 4-tick cooldown held at the origin cell");
        cart.tick(GROUND_WORLD);
        assertEquals(2, host.attempts.get(), "the cooldown burned out: the attempt ran");
    }

    @Test
    void theDisabledCartNeverTransfersAndTheActivatorRailOwnsTheFlag() {
        // onActivatorRail lines 63-68: a POWERED rail disables (the inverse
        // sense of the TNT prime), an unpowered one re-enables.
        RecordingHost host = new RecordingHost();
        HopperMinecartEntity cart = cartAt(50.5, 0.35, 50.5, host);
        host.nextVerdict = true;
        cart.onActivatorRail(true);
        assertFalse(cart.enabled(), "the powered activator rail disabled the walk");
        for (int i = 0; i < 10; i++) {
            cart.tick(GROUND_WORLD);
        }
        assertEquals(0, host.attempts.get(), "the disabled cart never attempted");
        assertEquals(-1, cart.transferCooldown(), "the disabled walk leaves the cooldown alone");
        cart.onActivatorRail(false);
        assertTrue(cart.enabled(), "the unpowered rail re-enabled the walk");
        cart.tick(GROUND_WORLD);
        assertEquals(1, host.attempts.get());
    }

    @Test
    void thePullFromTheChestAboveArmsTheCadenceOnTheLiveEngine() throws Exception {
        EngineServer server = boot();
        try {
            // The cart parked on a rail cell at (10, 40, 10); a chest with
            // diamonds sits one cell above the rail's cell (the mouth's
            // lookup: floor(cartY + 0.5 + 1.0)).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(10, 40, 10), BuiltinBlocks.RAIL);
                server.world().setBlock(new BlockPosition(10, 41, 10), BuiltinBlocks.CHEST);
                server.chests().getOrCreate(new BlockPosition(10, 41, 10))
                        .setSlot(0, ItemStack.of(BuiltinItems.DIAMOND, 5));
                server.vehicles().spawnHopperMinecart(
                        new Position(10.5, 40.06, 10.5),
                        server.hopper()::hopperCartTransfer);
            });
            await(() -> hopperCartSlotCount(server, 0) >= 1,
                    "the cart pulled the first diamond from the chest above");
            await(() -> hopperCartSlotCount(server, 0) >= 2,
                    "the second diamond followed (the quirk's every-tick cadence away from origin)");
            int chestRemainder = server.chests()
                    .getOrCreate(new BlockPosition(10, 41, 10)).snapshotSlots()[0].count();
            assertTrue(chestRemainder <= 3,
                    "the chest gave up its diamonds one per attempt: " + chestRemainder);
        } finally {
            server.shutdown(null);
        }
    }

    /** The hopper cart's first live slot count (0 when the cart is absent). */
    private static int hopperCartSlotCount(EngineServer server, int slot) {
        for (var vehicle : server.vehicles().all()) {
            if (vehicle instanceof HopperMinecartEntity cart) {
                return cart.hopper().getItem(slot).count();
            }
        }
        return 0;
    }

    @Test
    void theInteractOpensTheFiveSlotMenuAndTheClicksRoute() throws Exception {
        EngineServer server = boot();
        try {
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(10, 40, 10), BuiltinBlocks.RAIL);
                server.vehicles().spawnHopperMinecart(
                        new Position(10.5, 40.06, 10.5),
                        server.hopper()::hopperCartTransfer);
            });
            await(() -> !server.vehicles().all().isEmpty(), "the cart spawned");
            HopperMinecartEntity cart = (HopperMinecartEntity) server.vehicles().all().get(0);
            PlayerSession session = join(server, "hopperclicker");
            await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the session promoted to PLAYING");
            // The player stands beside the cart (within the interact reach
            // + the window's 8-block validity gate).
            server.ticker().submit(() -> session.applyMovement(
                    new Position(11.5, 40.06, 10.5), session.rotation(), true));
            server.ticker().submit(() -> server.interactEntity(session, cart.entityId()));
            await(() -> session.openContainerKind()
                    == PlayerSession.ContainerKind.HOPPER_MINECART,
                    "the interact opened the hopper menu");
            assertEquals(cart.entityId(), session.containerMountId());
            int windowId = session.openContainerWindowId();
            // A shift-click from the hotbar into the cart's five slots: a
            // coal stack rides hotbar slot 0 (wire slot 32).
            server.ticker().submit(() ->
                    session.inventory().setSlot(0, ItemStack.of(BuiltinItems.COAL, 7)));
            server.windowClick(session, windowId, 32, 0, 1, ok -> { });
            await(() -> cart.hopper().getItem(0).count() == 7,
                    "the shift-click landed the coal in the cart's first slot");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePersistencePayloadCarriesTheCargoTheFlagAndTheCooldown() {
        // The ZVD v2 roundtrip: the five cargo slots, the disable flag,
        // and the transfer cooldown (the reference's own TransferCooldown
        // NBT row).
        java.nio.file.Path file = dataDir.resolve("vehicles.bin");
        net.zaminmc.torch.server.entity.vehicle.VehicleDataStore store =
                new net.zaminmc.torch.server.entity.vehicle.VehicleDataStore(file);
        VehicleManager manager = new VehicleManager(GROUND_WORLD,
                vehicle -> { }, new Random(7), 9000);
        var spawned = (HopperMinecartEntity) manager.spawnHopperMinecart(
                new Position(12.5, 64.06, 12.5), cart -> true);
        spawned.hopper().setItem(2, ItemStack.of(BuiltinItems.IRON_INGOT, 12));
        spawned.setEnabled(false);
        spawned.setTransferCooldown(3);
        List<VehicleManager.VehicleSnapshot> saved = manager.snapshot();
        store.save(saved);
        List<VehicleManager.VehicleSnapshot> loaded = store.load();
        assertEquals(1, loaded.size());
        VehicleManager.VehicleSnapshot row = loaded.get(0);
        assertEquals(VehicleEntity.Kind.HOPPER_MINECART.name(), row.kind());
        assertEquals(5, row.cargo().length, "the five-slot cargo block");
        assertEquals(12, row.cargo()[2].count());
        assertFalse(row.hopperEnabled(), "the disable flag survived");
        assertEquals(3, row.hopperCooldown(), "the cooldown survived");
        // The restore: the cargo, flag, and cooldown land on the fresh body.
        VehicleManager restored = new VehicleManager(GROUND_WORLD,
                vehicle -> { }, new Random(8), 9100);
        restored.restoreAll(store.load(), () -> cart -> true);
        var back = (HopperMinecartEntity) restored.byId(restored.all().get(0).entityId());
        assertEquals(12, back.hopper().getItem(2).count());
        assertFalse(back.enabled());
        assertEquals(3, back.transferCooldown());
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "hmct" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(), GameMode.SURVIVAL);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
