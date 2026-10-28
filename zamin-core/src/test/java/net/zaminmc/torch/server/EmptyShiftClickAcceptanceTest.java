package net.zaminmc.torch.server;

import net.zaminmc.torch.server.beacon.BeaconSystem;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.brewing.BrewingStandBlocks;
import net.zaminmc.torch.server.chest.EnderChestBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.dispenser.DispenserBlocks;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * ML-004's fix pinned — the empty shift-click is a CONFIRMED NO-OP in
 * every container family, the reference's own prediction-match law
 * (ServerPlayNetworkHandler.handleInventoryMenuClickSlot lines 778-812:
 * the client's claimed stack IS its predicted result, and an empty slot
 * quick-move predicts null on BOTH sides — quickMoveItem answers the
 * slot's item, null when empty — so ItemStack.matches(null, null) accepts
 * and the reference confirms with no resync).
 *
 * <p>Before the fix, eight container arms rejected the empty player-side
 * shift-click (and the beacon its empty payment slot): hopper, chest,
 * ender chest, beacon, dispenser, brewing stand, chest minecart, hopper
 * minecart — a confirm=false plus a full window resync where the
 * reference sends confirm=true and nothing else. Each arm below opens
 * its REAL window and pins the acceptance.</p>
 */
class EmptyShiftClickAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession session;
    private final List<String> rejections = new CopyOnWriteArrayList<>();
    private Handler capture;
    private Logger engineLogger;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "esc" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        session = join("Shifter");
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        // Anchor beside the container row (99.5, 61.5, 100.5), then place
        // every container within the use handler's 8-block body gate.
        session.applyMovement(new Position(99.5, 61.5, 100.5), Rotation.ZERO, true);
        EngineAwaits.awaitTicks(server.world(), 2, "the anchor settles");
        assertTrue(session.inventory().snapshot().get(9).isEmpty(),
                "the player's first main-inventory slot starts empty (engine slot 9)");
        // The ML-004 rejection capture: a fix regression would fire the
        // instrumentation's FINE line naming its gate — the strongest
        // signal that the click reached the fixed arm, not a refusal path.
        engineLogger = Logger.getLogger(EngineServer.class.getName());
        capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getMessage() != null
                        && record.getMessage().startsWith("Window click rejected:")) {
                    rejections.add(record.getMessage());
                }
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        capture.setLevel(Level.FINE);
        engineLogger.addHandler(capture);
        engineLogger.setLevel(Level.FINE);
    }

    @AfterEach
    void stop() {
        if (engineLogger != null && capture != null) {
            engineLogger.removeHandler(capture);
        }
        server.shutdown(null);
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession joined = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(joined);
        return joined;
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

    /** Places a block on the tick lane and waits for it to land. */
    private void place(int x, int y, int z, net.zaminmc.torch.block.BlockType type) {
        var at = new net.zaminmc.torch.block.BlockPosition(x, y, z);
        server.ticker().submit(() -> server.world().setBlock(at, type));
    }

    /** Opens a placed container through the real use path. */
    private int openViaUse(net.zaminmc.torch.block.BlockPosition at) throws Exception {
        AtomicInteger opened = new AtomicInteger(-1);
        server.useItemOnBlock(session, at, 1, java.util.Optional.empty(), 0, opened::set);
        await(() -> opened.get() > 0, "the container window opened");
        return opened.get();
    }

    /**
     * The law under test: a mode-1 click on an EMPTY player-side slot of
     * the open window confirms (accepted=true), moves nothing, and fires
     * no rejection.
     */
    private void assertEmptyShiftClickAccepts(int windowId, int playerMainWireSlot)
            throws Exception {
        AtomicBoolean accepted = new AtomicBoolean(false);
        AtomicBoolean answered = new AtomicBoolean(false);
        server.windowClick(session, windowId, playerMainWireSlot, 0, 1, ok -> {
            accepted.set(ok);
            answered.set(true);
        });
        await(answered::get, "the click's confirm answered");
        assertTrue(accepted.get(),
                "the empty shift-click is a confirmed no-op (the reference's "
                        + "prediction-match law; slot=" + playerMainWireSlot + ")");
        EngineAwaits.awaitTicks(server.world(), 2, "settle");
        assertTrue(session.inventory().snapshot().get(9).isEmpty(),
                "nothing moved into the empty slot");
        assertTrue(rejections.isEmpty(),
                "the instrumentation stayed silent (no rejection fired): " + rejections);
    }

    @Test
    void theChestWindowAcceptsTheEmptyShiftClick() throws Exception {
        place(100, 60, 100, BuiltinBlocks.CHEST);
        await(() -> server.world().getBlock(new net.zaminmc.torch.block.BlockPosition(100, 60, 100))
                .identifier().value().contains("chest"), "the chest block landed");
        int windowId = openViaUse(new net.zaminmc.torch.block.BlockPosition(100, 60, 100));
        assertEquals(PlayerSession.ContainerKind.CHEST, session.openContainerKind());
        // Chest layout: 0-26 the grid, 27+ the player main — wire 27 = engine 9.
        assertEmptyShiftClickAccepts(windowId, 27);
    }

    @Test
    void theEnderChestWindowAcceptsTheEmptyShiftClick() throws Exception {
        place(100, 60, 100, EnderChestBlocks.enderChestOf(0));
        await(() -> EnderChestBlocks.isFamily(server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(100, 60, 100))),
                "the ender chest block landed");
        int windowId = openViaUse(new net.zaminmc.torch.block.BlockPosition(100, 60, 100));
        assertEquals(PlayerSession.ContainerKind.ENDER_CHEST, session.openContainerKind());
        // The ender chest shares the chest's 63-slot layout (27 = player main).
        assertEmptyShiftClickAccepts(windowId, 27);
    }

    @Test
    void theHopperWindowAcceptsTheEmptyShiftClick() throws Exception {
        place(100, 60, 100, HopperBlocks.hopperOf(0, true));
        await(() -> server.world().getBlock(new net.zaminmc.torch.block.BlockPosition(100, 60, 100))
                .identifier().value().contains("hopper"), "the hopper block landed");
        int windowId = openViaUse(new net.zaminmc.torch.block.BlockPosition(100, 60, 100));
        assertEquals(PlayerSession.ContainerKind.HOPPER, session.openContainerKind());
        // Hopper layout: 0-4 the funnel, 5+ the player main — wire 5 = engine 9.
        assertEmptyShiftClickAccepts(windowId, 5);
    }

    @Test
    void theDispenserWindowAcceptsTheEmptyShiftClick() throws Exception {
        place(100, 60, 100, DispenserBlocks.dispenserOf(0, false));
        await(() -> server.world().getBlock(new net.zaminmc.torch.block.BlockPosition(100, 60, 100))
                .identifier().value().contains("dispenser"), "the dispenser block landed");
        int windowId = openViaUse(new net.zaminmc.torch.block.BlockPosition(100, 60, 100));
        assertEquals(PlayerSession.ContainerKind.DISPENSER, session.openContainerKind());
        // Dispenser layout: 0-8 the nine slots, 9+ the player main — wire 9 = engine 9.
        assertEmptyShiftClickAccepts(windowId, 9);
    }

    @Test
    void theBrewingStandWindowAcceptsTheEmptyShiftClick() throws Exception {
        place(100, 60, 100, BrewingStandBlocks.of(false, false, false));
        await(() -> server.world().getBlock(new net.zaminmc.torch.block.BlockPosition(100, 60, 100))
                .identifier().value().contains("brewing"), "the brewing stand landed");
        int windowId = openViaUse(new net.zaminmc.torch.block.BlockPosition(100, 60, 100));
        assertEquals(PlayerSession.ContainerKind.BREWING_STAND, session.openContainerKind());
        // Brewing layout: 0-3 the stand's slots, 4+ the player main — wire 4 = engine 9.
        assertEmptyShiftClickAccepts(windowId, 4);
    }

    @Test
    void theBeaconWindowAcceptsTheEmptyShiftClickOnBothSides() throws Exception {
        // The one-level pyramid (the BeaconSystemTest shape) plus the beacon.
        var at = new net.zaminmc.torch.block.BlockPosition(100, 60, 100);
        server.ticker().submit(() -> {
            server.world().setBlock(at, BeaconSystem.BEACON);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    server.world().setBlock(at.offset(dx, -1, dz), BuiltinBlocks.IRON_BLOCK);
                }
            }
        });
        await(() -> BeaconSystem.isBeacon(server.world().getBlock(at)),
                "the beacon block landed");
        int windowId = openViaUse(at);
        assertEquals(PlayerSession.ContainerKind.BEACON, session.openContainerKind());

        // The player side first (wire 1 = the main inventory's first slot).
        assertEmptyShiftClickAccepts(windowId, 1);

        // The payment slot (wire 0) with NO payment staged: the reference's
        // quickMoveItem answers null for the empty slot here too — the
        // confirmed no-op, the arm the fix's beacon correction covers.
        AtomicBoolean paymentAccepted = new AtomicBoolean(false);
        AtomicBoolean paymentAnswered = new AtomicBoolean(false);
        server.windowClick(session, windowId, 0, 0, 1, ok -> {
            paymentAccepted.set(ok);
            paymentAnswered.set(true);
        });
        await(paymentAnswered::get, "the payment-slot click answered");
        assertTrue(paymentAccepted.get(),
                "the empty payment slot shift-clicks as a confirmed no-op");
        EngineAwaits.awaitTicks(server.world(), 2, "settle");
        assertTrue(rejections.isEmpty(),
                "the instrumentation stayed silent across both clicks: " + rejections);
    }

    @Test
    void theChestMinecartWindowAcceptsTheEmptyShiftClick() throws Exception {
        var cart = server.vehicles().spawnChestMinecart(
                new Position(100.5, 61.0, 100.5), 0);
        await(() -> !server.vehicles().all().isEmpty(), "the chest cart spawned");
        server.interactEntity(session, cart.entityId());
        await(() -> session.openContainerKind() == PlayerSession.ContainerKind.CHEST_MINECART
                        && session.openContainerWindowId() > 0,
                "the chest cart's menu opened");
        // The chest cart shares the chest's 63-slot layout (27 = player main).
        assertEmptyShiftClickAccepts(session.openContainerWindowId(), 27);
    }

    @Test
    void theHopperMinecartWindowAcceptsTheEmptyShiftClick() throws Exception {
        server.ticker().submit(() -> server.vehicles().spawnHopperMinecart(
                new Position(100.5, 61.0, 100.5),
                server.hopper()::hopperCartTransfer, 0));
        await(() -> !server.vehicles().all().isEmpty(), "the hopper cart spawned");
        var cart = server.vehicles().all().get(0);
        server.interactEntity(session, cart.entityId());
        await(() -> session.openContainerKind() == PlayerSession.ContainerKind.HOPPER_MINECART
                        && session.openContainerWindowId() > 0,
                "the hopper cart's menu opened");
        // The hopper cart shares the hopper's 41-slot layout (5 = player main).
        assertEmptyShiftClickAccepts(session.openContainerWindowId(), 5);
    }
}
