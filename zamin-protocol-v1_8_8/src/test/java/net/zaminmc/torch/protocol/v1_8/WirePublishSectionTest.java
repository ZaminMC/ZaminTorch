package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-024 — the wire-publish section's acceptance: the chunk
 * serialization+publication walk (ChunkSerializer18.serialize, the 0x21
 * build, the flush, the sign/spawner/beacon/skull/command tile replays)
 * rides its own profiler row ({@code root.wirePublish}), recorded through
 * the profiler's cross-thread span arm — the walk runs on lanes the
 * push/pop stack contract excludes (the join/movement netty event loop,
 * the tick thread's pre-handler job drain where the respawn re-stream
 * lands, the tick thread's relightTransport section), so a nested
 * push/pop section would tear the shared stack.
 *
 * <p><b>The acceptance drive</b> (the cheapest real paths, the ML-007
 * harness's own mechanisms): the profiler turns on through /debug, then
 * ONE join streams a full view (the netty event loop's lane — the login
 * success listener runs the join sequence on the channel's own loop) and
 * ONE death+respawn re-streams the full view again (the tick thread's
 * pre-handler lane — performRespawn's submitted job). Both lanes must
 * accumulate into the SAME row, with real nanos and the full
 * view-distance column count, and the section stack must stay balanced
 * after the cross-lane records — the regression pin: if the arm were ever
 * converted to a push/pop from a non-tick lane, the interleaved stack
 * would nest or lose sections and these invariants break.</p>
 */
class WirePublishSectionTest extends ProtocolTestBase {

    /** No keep-alive kicks inside the test's windows (the harness's law). */
    @Override
    protected long keepAliveIntervalMs() {
        return 10_000_000;
    }

    @Test
    void theJoinLaneAndTheRespawnLaneAccumulateIntoTheWirePublishRow() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            awaitPlayers("Alice");
            PlayerSession aliceSession = server.playerRegistry().byName("Alice").orElseThrow();
            // The profiler's own gate: op Alice for /debug (level 3).
            server.ticker().submit(() -> aliceSession.setOpLevel(3));
            awaitCondition(() -> aliceSession.opLevel() >= 3, "Alice opped");
            // The bed-spawn pin (the harness's own law): the respawn's
            // destination is her standing spot, so the re-stream covers the
            // exact columns her join already loaded (the inline walk).
            Position anchor = aliceSession.position();
            server.ticker().submit(() -> aliceSession.setBedSpawn(anchor));
            awaitTicks(2, "the bed-spawn pin");
            // The full view set this engine streams (viewDistance from the
            // shared config — never assumed: the tracker walks exactly this
            // square, so two full walks record at least twice its columns).
            int view = server.engineConfig().viewDistance();
            long columnsPerWalk = (2L * view + 1) * (2L * view + 1);

            server.chatService().submitChat(aliceSession, "/debug start");
            awaitTicks(2, "the profiler opened (the delayed activation)");

            // Lane 1 — the netty event loop: the join sequence (the login
            // success listener runs sendInitial on the channel's own loop)
            // streams Bob's full view there. The drain thread keeps the
            // wire honest (the harness's law: a real client reads its
            // socket or the OS buffers fill).
            try (TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {
                bob.sendHandshake(47, 2);
                bob.sendLoginStart("Bob");
                bob.readLoginSuccess();
                bob.readUntilPositionAndLook();
                awaitPlayers("Alice", "Bob");
                Thread bobDrain = drain(bob);
                bobDrain.start();
                Thread aliceDrain = drain(alice);
                aliceDrain.start();

                // Lane 2 — the tick thread's pre-handler job drain: the
                // death + respawn re-stream (damage and performRespawn are
                // both ticker submits; the walk runs in the pending-work
                // drain, before the tick handler's root window — the
                // storm's lane, where the attribution pass found the cost
                // invisible).
                server.damage(aliceSession, 1000.0f);
                awaitCondition(aliceSession::dead, "Alice died");
                server.performRespawn(aliceSession);

                // Both walks recorded: the join lane's columns plus the
                // respawn lane's columns, one count per writeChunk whatever
                // lane it ran on (a not-yet-loaded column rides the chunk
                // load callback — also a writeChunk, also recorded).
                awaitCondition(() -> server.profiler().sectionCountsSnapshot()
                                .getOrDefault("root.wirePublish", 0L) >= 2L * columnsPerWalk,
                        "both walks' columns recorded in root.wirePublish");
            }

            // The row carries real nanos in the snapshot face (the ML-024
            // acceptance: the section visible in sectionNanosSnapshot).
            Map<String, Long> nanos = server.profilerSectionNanos();
            assertTrue(nanos.containsKey("root.wirePublish"),
                    "root.wirePublish appears in sectionNanosSnapshot");
            assertTrue(nanos.get("root.wirePublish") > 0L,
                    "root.wirePublish carries nonzero nanos ("
                            + nanos.get("root.wirePublish") + "ns)");

            // The stack-balance pins (the cross-lane safety regression): the
            // record arm never pushes, so the row can never gain a child,
            // and the tick fan's own sections stay childless after records
            // arrived from both lanes — the invariants a torn stack (a
            // push/pop from the netty lane) would break.
            Map<String, Long> after = server.profilerSectionNanos();
            assertTrue(after.keySet().stream().noneMatch(key -> key.startsWith("root.wirePublish.")),
                    "root.wirePublish never gains a child (the arm holds no stack)");
            String tickPrefix = "root.levels." + server.config().worldName() + ".tick.";
            for (String section : new String[]{"mobs", "randomTicks", "relightTransport"}) {
                assertTrue(after.containsKey(tickPrefix + section),
                        "'" + section + "' still present after the cross-lane records");
                assertTrue(after.keySet().stream().noneMatch(key ->
                                key.startsWith(tickPrefix + section + ".")),
                        "'" + section + "' never gains a child (the stack stays balanced)");
            }

            server.chatService().submitChat(aliceSession, "/debug stop");
            awaitTicks(2, "the profiler stopped");
        }
        awaitEmptyServer();
    }

    /** Waits for N new ticks (the engine's own clock, never a wall sleep). */
    private void awaitTicks(int ticks, String description) throws InterruptedException {
        long anchor = server.world().totalTicks();
        awaitCondition(() -> server.world().totalTicks() >= anchor + ticks, description);
    }

    /**
     * The wire drain (the harness's own law): a real client reads its
     * socket, or the OS buffers fill and the write path stops being honest.
     * Discards everything; the thread ends with the socket.
     */
    private Thread drain(TestClient18 client) {
        Thread t = new Thread(() -> {
            try {
                client.setReadTimeout(500);
            } catch (Exception e) {
                return;
            }
            while (true) {
                try {
                    client.readPacket();
                } catch (Exception timeoutOrClosed) {
                    // a 500ms timeout just loops; a closed socket returns
                }
            }
        }, "wire-publish-drain");
        t.setDaemon(true);
        return t;
    }
}
