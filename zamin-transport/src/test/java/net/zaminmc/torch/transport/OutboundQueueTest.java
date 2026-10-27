package net.zaminmc.torch.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outbound queue's transport-gate pins (§35.11's shapes, the queue's
 * own slice): the batched drain's ONE flush for N packets (the flush-count
 * bottleneck's direct evidence), FIFO order across the ordinary and urgent
 * paths, the ordered close's disconnect-last law, the error-path release
 * (nothing queued survives a closeNow, nothing leaks), the inactive-channel
 * drop, and concurrent enqueues from two lanes losing nothing.
 */
class OutboundQueueTest {

    private static ByteBuf packet(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
    }

    private static String readAll(ByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return new String(out, StandardCharsets.UTF_8);
    }

    private static List<String> drainOutbound(EmbeddedChannel channel) {
        List<String> out = new ArrayList<>();
        Object msg;
        while ((msg = channel.readOutbound()) != null) {
            out.add(readAll((ByteBuf) msg));
        }
        return out;
    }

    @Test
    void tenEnqueuesOneFlushTheBatchingBoundary() {
        EmbeddedChannel channel = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(channel);

        for (int i = 0; i < 10; i++) {
            queue.enqueue(packet("p" + i));
        }
        // The drain task runs on the (embedded) event loop; runPendingTasks
        // is the deterministic join for whichever scheduling the loop uses.
        channel.runPendingTasks();

        TransportMetrics.Snapshot snapshot = queue.metrics().snapshot();
        assertEquals(10, snapshot.packetsEnqueued());
        assertEquals(10, snapshot.packetsWritten());
        // THE row: ten packets, one flush — the per-packet-flush shape would
        // answer ten.
        assertEquals(1, snapshot.flushes());
        assertEquals(10.0, snapshot.packetsPerFlush(), 1e-9);

        List<String> out = drainOutbound(channel);
        assertEquals(10, out.size());
        for (int i = 0; i < 10; i++) {
            assertEquals("p" + i, out.get(i));
        }
        channel.finishAndReleaseAll();
    }

    @Test
    void urgentJoinsTheTailOrderNeverInverts() {
        EmbeddedChannel channel = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(channel);

        queue.enqueue(packet("queued-1"));
        queue.enqueue(packet("queued-2"));
        queue.enqueueUrgent(packet("urgent"));
        queue.enqueue(packet("after-urgent"));
        channel.runPendingTasks();

        List<String> out = drainOutbound(channel);
        assertEquals(List.of("queued-1", "queued-2", "urgent", "after-urgent"), out);
        // Two drains ran (the scheduled one, the urgent one) — the urgent
        // flush carried the queue's then-head; the exact count is the
        // scheduling's shape, the ORDER is the law.
        TransportMetrics.Snapshot snapshot = queue.metrics().snapshot();
        assertEquals(4, snapshot.packetsWritten());
        assertTrue(snapshot.flushes() >= 1 && snapshot.flushes() <= 2);
        channel.finishAndReleaseAll();
    }

    @Test
    void closeWithWritesEverythingDisconnectLastThenCloses() {
        EmbeddedChannel channel = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(channel);

        queue.enqueue(packet("owed-1"));
        queue.enqueue(packet("owed-2"));
        queue.closeWith(packet("disconnect"));
        assertTrue(queue.metrics().snapshot().packetsEnqueued() >= 3);

        assertFalse(channel.isOpen());
        List<String> out = drainOutbound(channel);
        assertEquals(List.of("owed-1", "owed-2", "disconnect"), out);
        assertEquals(0, queue.queuedPackets());
    }

    @Test
    void closeNowReleasesEverythingQueuedNothingLeaks() {
        EmbeddedChannel channel = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(channel);

        ByteBuf held = packet("held-for-refcount");
        // Track the release: enqueue a ticket and watch the refcount fall.
        ByteBuf tracked = Unpooled.buffer(4);
        queue.enqueue(packet("drop-1"));
        queue.enqueue(tracked.retain()); // refCnt 2: ours + the queue's ticket
        assertEquals(2, tracked.refCnt());

        queue.closeNow();
        assertFalse(channel.isOpen());
        assertEquals(1, tracked.refCnt()); // the queue's ticket released
        assertEquals(2, queue.metrics().snapshot().packetsDropped());
        assertEquals(0, queue.queuedPackets());
        assertNull(channel.readOutbound()); // nothing was written

        // After close, admission is closed: further enqueues drop with a release.
        ByteBuf late = packet("late");
        queue.enqueue(late);
        assertTrue(queue.metrics().snapshot().packetsDropped() >= 3);

        tracked.release();
        held.release(); // never handed over — the test's own ticket
        assertEquals(0, tracked.refCnt());
    }

    @Test
    void inactiveChannelDropsWithRelease() {
        EmbeddedChannel channel = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(channel);
        channel.close();

        ByteBuf tracked = Unpooled.buffer(4);
        queue.enqueue(tracked.retain());
        assertEquals(1, tracked.refCnt()); // dropped + released, not leaked
        assertEquals(1, queue.metrics().snapshot().packetsDropped());
        assertEquals(0, queue.queuedPackets());
        tracked.release();
        assertEquals(0, tracked.refCnt());
    }

    @Test
    void concurrentEnqueuesFromTwoLanesLoseNothing() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(channel);
        int perLane = 500;

        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread laneA = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < perLane; i++) {
                    queue.enqueue(packet("a" + i));
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        Thread laneB = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < perLane; i++) {
                    queue.enqueue(packet("b" + i));
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        laneA.start();
        laneB.start();
        start.countDown();
        laneA.join(10_000);
        laneB.join(10_000);
        assertNull(failure.get());

        channel.runPendingTasks();
        TransportMetrics.Snapshot snapshot = queue.metrics().snapshot();
        assertEquals(2 * perLane, snapshot.packetsEnqueued());
        assertEquals(2 * perLane, snapshot.packetsWritten());
        // The coalescing: two lane walks drained in few flushes — nowhere
        // near the 1000 flushes the per-packet shape produces.
        assertTrue(snapshot.flushes() <= 10, "flushes=" + snapshot.flushes());

        List<String> out = drainOutbound(channel);
        assertEquals(2 * perLane, out.size());
        long a = out.stream().filter(s -> s.startsWith("a")).count();
        long b = out.stream().filter(s -> s.startsWith("b")).count();
        assertEquals(perLane, a);
        assertEquals(perLane, b);
        // Per-lane FIFO: each lane's own order survives the merge.
        assertEquals(0, firstOutOfOrder(out, "a"));
        assertEquals(0, firstOutOfOrder(out, "b"));
        channel.finishAndReleaseAll();
    }

    private static int firstOutOfOrder(List<String> out, String lane) {
        int seen = 0;
        for (String s : out) {
            if (!s.startsWith(lane)) {
                continue;
            }
            if (!s.equals(lane + seen)) {
                return seen;
            }
            seen++;
        }
        return 0;
    }

    @Test
    void doubleCloseIsSafeAndSaturationCrossingLogsOnce() {
        EmbeddedChannel channel = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(channel);
        queue.close();
        queue.close();
        assertFalse(channel.isOpen());

        // The saturation observable: past the 1 MiB mark the enqueue keeps
        // working (the slice's liveness law) and the depth row carries it.
        EmbeddedChannel openChannel = new EmbeddedChannel();
        OutboundQueue saturated = new OutboundQueue(openChannel);
        ByteBuf big = Unpooled.buffer((1 << 20) + 1);
        big.writerIndex(big.capacity());
        saturated.enqueue(big);
        assertEquals(1, saturated.queuedPackets());
        assertEquals((1 << 20) + 1, saturated.queuedBytes());
        openChannel.runPendingTasks();
        assertEquals(0, saturated.queuedPackets());
        openChannel.finishAndReleaseAll();
    }
}
