package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.entity.VehicleKind;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.event.entity.VehicleSpawnEvent;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vehicle spawn gate (§27.7's spawn family completion — Slice 24b):
 * the cancellable pre-commit {@link VehicleSpawnEvent} over the mount
 * family's two spawn chokepoints — the item placements (the boat at
 * water, the carts on rails) and the manager's own spawn verbs. A
 * cancelled spawn consumes no id, spawns no body, sends no wire frame,
 * and withholds the placing item.
 */
class VehicleSpawnEventAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "vse" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
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

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    @Test
    void theGateCancelsTheBoatPlacementAndWithholdsTheItem() throws Exception {
        // The water the boat use names (the 1.8 placement rule: the use
        // lands on the fluid cell itself).
        var water = server.blockRegistry().require(
                net.zaminmc.torch.util.Identifier.parse("minecraft:water"));
        var waterCell = new net.zaminmc.torch.block.BlockPosition(2, 4, 0);
        server.ticker().submit(() -> server.world().setBlock(waterCell, water));
        await(() -> !server.world().getBlock(waterCell).equals(server.world().airType()),
                "the water stands");

        PlayerSession sailor = join("Sailor", new Position(0.5, 4, 0.5));
        await(() -> sailor.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        server.ticker().submit(() -> {
            sailor.give(ItemStack.of(BuiltinItems.BOAT, 1));
            sailor.inventory().selectHotbarSlot(
                    sailor.inventory().heldSlot());
        });
        await(() -> !sailor.inventory().held().isEmpty(), "the boat is in hand");

        AtomicInteger fired = new AtomicInteger();
        AtomicReference<VehicleKind> seenKind = new AtomicReference<>();
        AtomicReference<Position> seenAt = new AtomicReference<>();
        EventSubscription sub = ((Server) server).events().on(VehicleSpawnEvent.class, event -> {
            fired.incrementAndGet();
            seenKind.set(event.kind());
            seenAt.set(event.position());
            event.setCancelled(true);
        });

        // The boat use on the water (face 1 = the surface's top).
        server.useItemOnBlock(sailor, waterCell, 1, Optional.empty(), windowId -> { });
        await(() -> fired.get() == 1, "the gate fired");

        assertEquals(VehicleKind.BOAT, seenKind.get(), "the event carries the kind");
        assertEquals(2.5, seenAt.get().x(), 0.001, "the spawn names the water cell");
        assertTrue(server.vehicles().all().isEmpty(),
                "no boat entered the world");
        assertEquals(1, sailor.inventory().held().count(),
                "the cancelled placement consumed no item");

        sub.unsubscribe();

        // The uncancelled twin: the same use spawns the boat and pays the item.
        server.useItemOnBlock(sailor, waterCell, 1, Optional.empty(), windowId -> { });
        await(() -> !server.vehicles().all().isEmpty(), "the twin boat spawned");
        assertEquals(VehicleKind.BOAT, server.vehicles().all().get(0).kind().apiKind,
                "the spawned body carries the kind");
        assertTrue(sailor.inventory().held().isEmpty(),
                "the successful placement paid the item");
    }

    @Test
    void theGateCoversTheManagerSpawnVerbs() throws Exception {
        Server api = server;
        AtomicInteger fired = new AtomicInteger();
        EventSubscription sub = api.events().on(VehicleSpawnEvent.class, event -> {
            fired.incrementAndGet();
            event.setCancelled(true);
        });

        // The manager's own verb (the structure-seeded chest carts ride it):
        // a cancelled spawn returns null and consumes no id.
        AtomicReference<Object> spawned = new AtomicReference<>("unset");
        server.ticker().submit(() -> spawned.set(server.vehicles()
                .spawnChestMinecart(new Position(8.5, 8.0, 8.5))));
        await(() -> !"unset".equals(spawned.get()), "the spawn walk ran");

        assertEquals(1, fired.get(), "the gate fired exactly once");
        assertNull(spawned.get(), "the cancelled spawn returns null (no id consumed)");
        assertTrue(server.vehicles().all().isEmpty(), "no cart entered the world");

        sub.unsubscribe();

        // The uncancelled twin: the cart spawns.
        AtomicReference<Object> twin = new AtomicReference<>("unset");
        server.ticker().submit(() -> twin.set(server.vehicles()
                .spawnChestMinecart(new Position(8.5, 8.0, 8.5))));
        await(() -> !"unset".equals(twin.get()), "the twin walk ran");
        assertTrue(twin.get() != null, "the uncancelled cart spawned");
        assertEquals(VehicleKind.CHEST_MINECART,
                ((net.zaminmc.torch.server.entity.vehicle.VehicleEntity) twin.get())
                        .kind().apiKind,
                "the spawned body carries the chest kind");
    }
}
