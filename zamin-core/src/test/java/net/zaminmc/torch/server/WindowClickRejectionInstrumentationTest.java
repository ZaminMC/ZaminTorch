package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * ML-004 — the window-click rejection-reason instrumentation (the cycle-4
 * B-2 seat: a chest window's slot-27 wire transaction rejected once,
 * state-dependent, never reproduced; the evidence rule — instrumentation
 * precedes any behavior claim). Every refused click now carries its full
 * wire context and the named refusing gate at FINE on the engine's logger;
 * this test drives three deterministic rejections and pins that the log
 * fires with the right reason names, so the next state-dependent repro
 * diagnoses itself.
 */
class WindowClickRejectionInstrumentationTest {

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
                "wcr" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.CREATIVE, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        session = join("Prober");
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        // The JUL capture over the engine's own logger: every FINE record
        // the instrumentation emits lands in the list.
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
        joined.applyMovement(new Position(99.5, 61.5, 100.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
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

    /** Opens a real chest window at (100, 60, 100) and returns its id. */
    private int openChestWindow() throws Exception {
        BlockPosition at = new BlockPosition(100, 60, 100);
        server.ticker().submit(() -> server.world().setBlock(at,
                BuiltinBlocks.CHEST));
        await(() -> server.world().getBlock(at).identifier().value().contains("chest"),
                "the chest block landed");
        AtomicInteger opened = new AtomicInteger(-1);
        server.useItemOnBlock(session, at, 1, java.util.Optional.empty(), 0, opened::set);
        await(() -> opened.get() > 0, "the chest window opened");
        assertTrue(session.openContainerKind() == PlayerSession.ContainerKind.CHEST,
                "the session records the chest kind");
        return opened.get();
    }

    @Test
    void theUnknownWindowClickNamesItsGate() throws Exception {
        int windowId = openChestWindow();
        EngineAwaits.awaitTicks(server.world(), 2, "settle");
        server.windowClick(session, windowId + 7, 27, 0, 0, ok -> { });
        EngineAwaits.awaitTicks(server.world(), 2, "settle");
        assertTrue(rejections.stream().anyMatch(m ->
                        m.contains("reason=unknown-window") && m.contains("slot=27")),
                "the unknown-window rejection carries the slot-27 context: " + rejections);
    }

    @Test
    void theCreativeOnlyGestureNamesItsGate() throws Exception {
        int windowId = openChestWindow();
        EngineAwaits.awaitTicks(server.world(), 2, "settle");
        server.windowClick(session, windowId, 27, 2, 3, ok -> { });
        EngineAwaits.awaitTicks(server.world(), 2, "settle");
        assertTrue(rejections.stream().anyMatch(m ->
                        m.contains("reason=creative-only-gesture") && m.contains("mode=3")),
                "the mode-3 rejection names the creative-only gate: " + rejections);
    }

    @Test
    void theOutOfRangeSlotNamesItsGate() throws Exception {
        int windowId = openChestWindow();
        EngineAwaits.awaitTicks(server.world(), 2, "settle");
        // A chest window's own band is 0..26, the player band 27..62 —
        // wire slot 999 maps to nothing.
        server.windowClick(session, windowId, 999, 0, 0, ok -> { });
        EngineAwaits.awaitTicks(server.world(), 2, "settle");
        assertTrue(rejections.stream().anyMatch(m ->
                        m.contains("reason=slot-out-of-range") && m.contains("slot=999")),
                "the out-of-range rejection names the slot gate: " + rejections);
    }

    @Test
    void theStaleChestNamesItsGate() throws Exception {
        int windowId = openChestWindow();
        assertNotNull(server.chests().peek(new BlockPosition(100, 60, 100)),
                "the chest state stands");
        // Break the chest under the open window — the container's state
        // leaves the store; if the engine closes the window first, the
        // click's window id no longer matches and the gate becomes
        // unknown-window (either way the instrumentation names it).
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(100, 60, 100), BuiltinBlocks.AIR));
        EngineAwaits.awaitTicks(server.world(), 4, "the break settles");
        server.windowClick(session, windowId, 27, 0, 0, ok -> { });
        EngineAwaits.awaitTicks(server.world(), 2, "settle");
        assertTrue(rejections.stream().anyMatch(m ->
                        m.contains("reason=stale-chest") || m.contains("reason=unknown-window")),
                "the post-break rejection names its gate: " + rejections);
        assertNull(server.chests().peek(new BlockPosition(100, 60, 100)),
                "the chest state left the store");
    }
}
