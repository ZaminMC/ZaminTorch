package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.beacon.BeaconBlockEntity;
import net.zaminmc.torch.server.beacon.BeaconSystem;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.StatusEffects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The beacon family (Slice 29b — the reference port of block/BeaconBlock +
 * block/entity/BeaconBlockEntity + inventory/menu/BeaconMenu): the pyramid
 * walk (updateLevels), the beam's opacity kill, the effect spread over the
 * range box, the payment + the MC|Beacon selection, and the ZBT
 * persistence.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BeaconSystemTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private void settle(EngineServer server) throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), 6, "the settle");
    }

    /** Places the beacon at (x, 60, z) with a pyramid of the given depth. */
    private void buildBeacon(EngineServer server, int x, int z, int depth) {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, 60, z),
                    BeaconSystem.BEACON);
            for (int l1 = 1; l1 <= depth; l1++) {
                for (int dx = -l1; dx <= l1; dx++) {
                    for (int dz = -l1; dz <= l1; dz++) {
                        server.world().setBlock(new BlockPosition(x + dx, 60 - l1, z + dz),
                                net.zaminmc.torch.server.block.BuiltinBlocks.IRON_BLOCK);
                    }
                }
            }
        });
    }

    @Test
    void thePyramidWalkAndTheBeamKill() throws Exception {
        EngineServer server = boot();
        try {
            buildBeacon(server, 110, 60, 1);
            BlockPosition at = new BlockPosition(110, 60, 60);
            await(() -> BeaconSystem.isBeacon(server.world().getBlock(at)),
                    "the beacon block landed");
            // The manual recompute (the neighbor bus reaches it too, but the
            // test drives the walk directly).
            server.ticker().submit(() ->
                    server.beacons().update(at, server.beacons().getOrCreate(at)));
            await(() -> server.beacons().peek(at) != null
                            && server.beacons().peek(at).levels() == 1,
                    "the one-level pyramid computes level 1");

            // The beam kill: an opaque block above deactivates the whole
            // pyramid (updateLevels line 102-106: getOpacity >= 15).
            server.ticker().submit(() -> server.world().setBlock(
                    at.offset(0, 1, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE));
            settle(server);
            server.ticker().submit(() ->
                    server.beacons().update(at, server.beacons().getOrCreate(at)));
            await(() -> server.beacons().peek(at).levels() == 0,
                    "the opaque lid killed the beam");

            // The bedrock exception (line 102: bedrock never kills the beam).
            server.ticker().submit(() -> {
                server.world().setBlock(at.offset(0, 1, 0),
                        net.zaminmc.torch.server.block.BuiltinBlocks.BEDROCK);
            });
            settle(server);
            server.ticker().submit(() ->
                    server.beacons().update(at, server.beacons().getOrCreate(at)));
            await(() -> server.beacons().peek(at).levels() == 1,
                    "the bedrock exception keeps the beam");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePaymentSelectionAndTheEffectSpread() throws Exception {
        EngineServer server = boot();
        try {
            buildBeacon(server, 114, 60, 1);
            BlockPosition at = new BlockPosition(114, 60, 60);
            await(() -> BeaconSystem.isBeacon(server.world().getBlock(at)),
                    "the beacon block landed");
            PlayerSession player = join(server, "Beaconer");
            // The spread's range is 20 blocks at level 1 — the player must
            // stand near the beacon (the join's spawn is far away).
            server.ticker().submit(() -> player.applyMovement(
                    new net.zaminmc.torch.util.Position(114.5, 61.0, 64.5),
                    net.zaminmc.torch.util.Rotation.ZERO, true));
            settle(server);
            // The manual recompute: the pyramid computes before the spread.
            server.ticker().submit(() ->
                    server.beacons().update(at, server.beacons().getOrCreate(at)));
            await(() -> server.beacons().peek(at) != null
                            && server.beacons().peek(at).levels() == 1,
                    "the one-level pyramid computes level 1");

            // The use opens the GUI (BeaconBlock.use).
            AtomicInteger opened = new AtomicInteger(-1);
            server.useItemOnBlock(player, at, 1, Optional.empty(), 0, opened::set);
            await(() -> opened.get() > 0, "the beacon window opened");
            assertEquals(PlayerSession.ContainerKind.BEACON, player.openContainerKind());

            // The payment lands through the window: the player holds an
            // emerald, picks it up, clicks wire slot 0.
            server.ticker().submit(() -> player.inventory().setSlot(
                    player.inventory().heldSlot(),
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.EMERALD, 3)));
            settle(server);
            server.windowClick(player, opened.get(),
                    28 + player.inventory().heldSlot(), 0, 0, ok -> { });
            settle(server);
            server.windowClick(player, opened.get(), 0, 0, 0, ok -> { });
            settle(server);
            assertEquals(1, player.openContainerPosition() == null ? 0
                            : server.beacons().peek(at).payment().count(),
                    "one emerald landed in the payment slot (max stack 1)");
            assertEquals(BuiltinItems.EMERALD, server.beacons().peek(at).payment().type());

            // The MC|Beacon confirm: one unit consumes, the choice lands
            // (the reference's effect gate keeps speed).
            server.beaconSelect(player, StatusEffects.SPEED, 0);
            settle(server);
            assertEquals(StatusEffects.SPEED, server.beacons().peek(at).primary(),
                    "the primary choice landed");
            assertTrue(server.beacons().peek(at).payment().isEmpty(),
                    "the payment unit consumed");
            server.closeWindow(player, opened.get());
            settle(server);

            // A confirm without a payment is the reference's silent no-op.
            server.useItemOnBlock(player, at, 1, Optional.empty(), 0, opened::set);
            settle(server);
            server.beaconSelect(player, StatusEffects.HASTE, 0);
            settle(server);
            assertEquals(StatusEffects.SPEED, server.beacons().peek(at).primary(),
                    "the unpaid confirm changed nothing");

            // The effect spread: a player inside the 20-block range gains
            // the 180-tick speed instance on the 80-tick phase.
            long deadline = System.currentTimeMillis() + 8_000;
            while (System.currentTimeMillis() < deadline) {
                if (server.potions().get(player, StatusEffects.SPEED) != null) {
                    break;
                }
                Thread.sleep(100);
            }
            var speed = server.potions().get(player, StatusEffects.SPEED);
            assertTrue(speed != null, "the spread applied the speed effect");
            assertEquals(180, speed.duration(), "the reference's own 180-tick instance");

            // The non-effect id never lands (getBeaconEffect's gate).
            assertEquals(0, BeaconBlockEntity.beaconEffectOf(StatusEffects.POISON),
                    "poison is not a beacon effect");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theStateSurvivesTheRestart() throws Exception {
        EngineServer first = boot();
        buildBeacon(first, 118, 60, 1);
        BlockPosition at = new BlockPosition(118, 60, 60);
        await(() -> BeaconSystem.isBeacon(first.world().getBlock(at)),
                "the beacon block landed");
        first.ticker().submit(() -> {
            var beacon = first.beacons().getOrCreate(at);
            beacon.restoreLevels(1);
            beacon.restorePrimary(StatusEffects.HASTE);
            beacon.restoreSecondary(0);
            beacon.setPayment(net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.DIAMOND, 1));
        });
        settle(first);
        first.shutdown(null);

        EngineServer second = boot();
        try {
            var beacon = second.beacons().peek(at);
            assertTrue(beacon != null, "the beacon state restored");
            assertEquals(1, beacon.levels(), "the level came back");
            assertEquals(StatusEffects.HASTE, beacon.primary(), "the primary came back");
            assertEquals(BuiltinItems.DIAMOND, beacon.payment().type(),
                    "the payment came back");
        } finally {
            second.shutdown(null);
        }
    }
}
