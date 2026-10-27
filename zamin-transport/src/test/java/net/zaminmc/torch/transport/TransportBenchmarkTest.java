package net.zaminmc.torch.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §35.12 benchmark matrix at the transport layer: the scenario list the
 * performance gate names, run against the queue over embedded channels, the
 * numbers PRINTED for the ledger (never asserted as thresholds — a slow CI
 * host must not fail the gate; the assertions pin CORRECTNESS only).
 *
 * <p>Scope honesty: these rows measure the queue's own face (packets/sec,
 * flush counts, packets-per-flush, queue depth, the p50/p95/p99 dwell the
 * §35.12 latency row tracks). The compression/encryption scenario rows ride
 * the protocol module's wire suites (the queue sits INSIDE the plaintext
 * frame layer by construction — §35.8's boundary — so those arms change the
 * bytes the pipeline sees, never the queue's behavior). The allocation-rate,
 * GC, serialization-CPU, and event-loop-CPU rows stay with the harnesses
 * that own them (the §35.4 pooled decision's before/after among them).</p>
 */
class TransportBenchmarkTest {

    private static final int WARMUP_DRAINS = 200;

    private static ByteBuf packetOfSize(int bytes, long seed) {
        ByteBuf out = Unpooled.buffer(bytes);
        for (int i = 0; i < bytes; i++) {
            out.writeByte((int) (seed + i) & 0xFF);
        }
        return out;
    }

    /** The per-byte content check helper: the delivered buffer matches the seed's ramp. */
    private static void assertRamp(ByteBuf buf, long seed, int bytes) {
        assertEquals(bytes, buf.readableBytes());
        for (int i = 0; i < bytes; i++) {
            if (buf.getByte(i) != (byte) ((seed + i) & 0xFF)) {
                throw new AssertionError("byte mismatch at " + i);
            }
        }
    }

    private static void printRow(String scenario, String row) {
        System.out.println("[bench-35.12] " + scenario + ": " + row);
    }

    private static String micros(long nanos) {
        return String.format("%.1fus", nanos / 1000.0);
    }

    private static String rate(long packets, long nanos) {
        return String.format("%,d pps", (long) (packets * 1_000_000_000.0 / Math.max(1, nanos)));
    }

    @Test
    void onePlayerTickWalkStream() {
        EmbeddedChannel channel = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(channel);

        // Warmup: the JIT's first walks are not the transport's face.
        for (int i = 0; i < WARMUP_DRAINS; i++) {
            for (int p = 0; p < 20; p++) {
                queue.enqueue(packetOfSize(64, i));
            }
            channel.runPendingTasks();
        }
        TransportMetrics warm = queue.metrics();
        long warmEnqueued = warm.snapshot().packetsEnqueued();
        long warmFlushes = warm.snapshot().flushes();
        long warmWritten = warm.snapshot().packetsWritten();
        long warmSamples = warm.snapshot().latencySamples();

        int walks = 1_000;
        int perWalk = 20;
        long start = System.nanoTime();
        for (int i = 0; i < walks; i++) {
            for (int p = 0; p < perWalk; p++) {
                queue.enqueue(packetOfSize(64, i));
            }
            channel.runPendingTasks();
        }
        long elapsed = System.nanoTime() - start;

        TransportMetrics.Snapshot s = queue.metrics().snapshot();
        long packets = s.packetsEnqueued() - warmEnqueued;
        long flushes = s.flushes() - warmFlushes;
        long written = s.packetsWritten() - warmWritten;
        assertEquals((long) walks * perWalk, packets);
        assertEquals(packets, written);
        // THE batching row: one flush per walk — 1,000 flushes for 20,000
        // packets (the per-packet-flush shape would answer 20,000).
        assertEquals(walks, flushes);
        printRow("one-player", rate(packets, elapsed) + ", flushes=" + flushes
                + ", packets/flush=" + String.format("%.1f", (double) written / flushes)
                + ", dwell p50=" + micros(s.latencyP50Nanos())
                + " p95=" + micros(s.latencyP95Nanos())
                + " p99=" + micros(s.latencyP99Nanos())
                + " (samples=" + (s.latencySamples() - warmSamples) + ")");
        channel.finishAndReleaseAll();
    }

    @Test
    void manyPlayersSameBroadcastEncodeOnce() {
        final int recipients = 100;
        final int ticks = 2_000;

        List<EmbeddedChannel> channels = new ArrayList<>();
        List<OutboundQueue> queues = new ArrayList<>();
        for (int i = 0; i < recipients; i++) {
            EmbeddedChannel channel = new EmbeddedChannel();
            channels.add(channel);
            queues.add(new OutboundQueue(channel));
        }

        // Warmup.
        for (int t = 0; t < WARMUP_DRAINS; t++) {
            broadcastTick(queues, 64, t);
            for (EmbeddedChannel channel : channels) {
                channel.runPendingTasks();
            }
        }
        long warmEnqueued = queues.get(0).metrics().snapshot().packetsEnqueued();

        long start = System.nanoTime();
        for (int t = 0; t < ticks; t++) {
            broadcastTick(queues, 64, t);
            for (EmbeddedChannel channel : channels) {
                channel.runPendingTasks();
            }
        }
        long elapsed = System.nanoTime() - start;

        // Correctness: every recipient's LAST delivery is byte-identical —
        // the encode-once ticket is an index-independent view.
        for (int r = 0; r < recipients; r += Math.max(1, recipients / 5)) {
            ByteBuf last = null;
            Object msg;
            while ((msg = channels.get(r).readOutbound()) != null) {
                if (last != null) {
                    last.release();
                }
                last = (ByteBuf) msg;
            }
            assertTrue(last != null, "recipient " + r + " got deliveries");
            assertRamp(last, ticks - 1, 64);
            last.release();
        }

        TransportMetrics.Snapshot s = queues.get(0).metrics().snapshot();
        long perQueuePackets = s.packetsEnqueued() - warmEnqueued;
        printRow("broadcast-same", recipients + " recipients x " + perQueuePackets
                + " ticks, ONE encode per tick (" + perQueuePackets + " encodes for "
                + (perQueuePackets * recipients) + " deliveries), fan-out "
                + rate((long) perQueuePackets * recipients, elapsed)
                + ", dwell p50=" + micros(s.latencyP50Nanos())
                + " p99=" + micros(s.latencyP99Nanos()));
        for (EmbeddedChannel channel : channels) {
            channel.finishAndReleaseAll();
        }
    }

    /** One broadcast tick: one payload encoded once, every queue takes a share. */
    private static void broadcastTick(List<OutboundQueue> queues, int bytes, long seed) {
        ByteBuf encoded = packetOfSize(bytes, seed);
        EncodedPayload payload = EncodedPayload.of(encoded);
        for (OutboundQueue queue : queues) {
            ByteBuf ticket = payload.share();
            queue.enqueue(ticket);
        }
        payload.release();
    }

    @Test
    void manyPlayersMostlyDifferentPackets() {
        final int recipients = 100;
        final int ticks = 500;

        List<EmbeddedChannel> channels = new ArrayList<>();
        List<OutboundQueue> queues = new ArrayList<>();
        for (int i = 0; i < recipients; i++) {
            EmbeddedChannel channel = new EmbeddedChannel();
            channels.add(channel);
            queues.add(new OutboundQueue(channel));
        }

        for (int t = 0; t < WARMUP_DRAINS; t++) {
            for (int r = 0; r < recipients; r++) {
                queues.get(r).enqueue(packetOfSize(64, r * 1_000_000L + t));
            }
            for (EmbeddedChannel channel : channels) {
                channel.runPendingTasks();
            }
        }
        long warmEnqueued = queues.get(0).metrics().snapshot().packetsEnqueued();
        long warmFlushes = queues.get(0).metrics().snapshot().flushes();

        long start = System.nanoTime();
        for (int t = 0; t < ticks; t++) {
            for (int r = 0; r < recipients; r++) {
                queues.get(r).enqueue(packetOfSize(64, r * 1_000_000L + t));
            }
            for (EmbeddedChannel channel : channels) {
                channel.runPendingTasks();
            }
        }
        long elapsed = System.nanoTime() - start;

        TransportMetrics.Snapshot s = queues.get(0).metrics().snapshot();
        long perQueuePackets = s.packetsEnqueued() - warmEnqueued;
        long flushes = s.flushes() - warmFlushes;
        printRow("mostly-different", recipients + " recipients x " + perQueuePackets
                + " distinct packets, " + rate((long) perQueuePackets * recipients, elapsed)
                + ", flushes/queue=" + flushes
                + ", dwell p50=" + micros(s.latencyP50Nanos())
                + " p99=" + micros(s.latencyP99Nanos()));
        for (EmbeddedChannel channel : channels) {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void highEntityTrackingTraffic() {
        final int mobs = 50;
        final int ticks = 4_000;

        List<EmbeddedChannel> channels = new ArrayList<>();
        List<OutboundQueue> queues = new ArrayList<>();
        for (int i = 0; i < mobs; i++) {
            EmbeddedChannel channel = new EmbeddedChannel();
            channels.add(channel);
            queues.add(new OutboundQueue(channel));
        }

        // Warmup.
        for (int t = 0; t < WARMUP_DRAINS; t++) {
            for (OutboundQueue queue : queues) {
                queue.enqueue(packetOfSize(8, t)); // the rel-move-look shape
            }
            for (EmbeddedChannel channel : channels) {
                channel.runPendingTasks();
            }
        }
        long warmEnqueued = queues.get(0).metrics().snapshot().packetsEnqueued();
        long warmFlushes = queues.get(0).metrics().snapshot().flushes();

        long start = System.nanoTime();
        for (int t = 0; t < ticks; t++) {
            for (OutboundQueue queue : queues) {
                queue.enqueue(packetOfSize(8, t));
            }
            for (EmbeddedChannel channel : channels) {
                channel.runPendingTasks();
            }
        }
        long elapsed = System.nanoTime() - start;

        TransportMetrics.Snapshot s = queues.get(0).metrics().snapshot();
        long perQueuePackets = s.packetsEnqueued() - warmEnqueued;
        long flushes = s.flushes() - warmFlushes;
        assertEquals(ticks, flushes); // one flush per tick per queue
        printRow("entity-tracking", mobs + " bodies x " + perQueuePackets + " rel-move ticks (8B), "
                + rate((long) perQueuePackets * mobs, elapsed)
                + ", packets/flush=" + String.format("%.1f", (double) perQueuePackets / flushes)
                + ", dwell p99=" + micros(s.latencyP99Nanos()));
        for (EmbeddedChannel channel : channels) {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void chunkStreamingBytesPerSecond() {
        final int recipients = 20;
        final int columns = 50;
        final int columnBytes = 32 * 1024;

        List<EmbeddedChannel> channels = new ArrayList<>();
        List<OutboundQueue> queues = new ArrayList<>();
        for (int i = 0; i < recipients; i++) {
            EmbeddedChannel channel = new EmbeddedChannel();
            channels.add(channel);
            queues.add(new OutboundQueue(channel));
        }

        long start = System.nanoTime();
        for (int c = 0; c < columns; c++) {
            // One column encoded once, fanned to every recipient's queue
            // (the chunk window's encode-once shape), each queue's own drain.
            ByteBuf encoded = packetOfSize(columnBytes, c);
            EncodedPayload payload = EncodedPayload.of(encoded);
            for (OutboundQueue queue : queues) {
                queue.enqueue(payload.share());
            }
            payload.release();
            for (EmbeddedChannel channel : channels) {
                channel.runPendingTasks();
            }
        }
        long elapsed = System.nanoTime() - start;

        long totalBytes = (long) columns * columnBytes * recipients;
        // Correctness spot-check: two recipients' columns are byte-identical.
        for (int r : new int[]{0, recipients - 1}) {
            ByteBuf last = null;
            Object msg;
            while ((msg = channels.get(r).readOutbound()) != null) {
                if (last != null) {
                    last.release();
                }
                last = (ByteBuf) msg;
            }
            assertRamp(last, columns - 1, columnBytes);
            last.release();
        }
        printRow("chunk-streaming", String.format("%,d bytes total (%d columns x %d recipients)",
                        totalBytes, columns, recipients)
                + String.format(", %,.1f MiB/s", totalBytes * 1_000_000_000.0 / elapsed / (1024.0 * 1024.0))
                + ", dwell p99=" + micros(queues.get(0).metrics().snapshot().latencyP99Nanos()));
        for (EmbeddedChannel channel : channels) {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void joinBurstOneFlushVersusThePerPacketStorm() {
        final int burst = 1_000;

        // (a) The reference-era shape per packet: write → flush, N times.
        AtomicLong stormFlushes = new AtomicLong();
        EmbeddedChannel storm = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void flush(ChannelHandlerContext ctx) {
                stormFlushes.incrementAndGet();
                ctx.flush();
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ctx.write(msg, promise);
            }
        });
        long stormStart = System.nanoTime();
        for (int i = 0; i < burst; i++) {
            storm.writeAndFlush(packetOfSize(64, i));
        }
        long stormElapsed = System.nanoTime() - stormStart;

        // (b) The queue's shape: N writes, ONE flush per drain.
        EmbeddedChannel batched = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void flush(ChannelHandlerContext ctx) {
                ctx.flush();
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ctx.write(msg, promise);
            }
        });
        OutboundQueue queue = new OutboundQueue(batched);
        long batchedStart = System.nanoTime();
        for (int i = 0; i < burst; i++) {
            queue.enqueue(packetOfSize(64, i));
        }
        batched.runPendingTasks();
        long batchedElapsed = System.nanoTime() - batchedStart;

        assertEquals(burst, stormFlushes.get());
        TransportMetrics.Snapshot s = queue.metrics().snapshot();
        assertEquals(1, s.flushes());
        assertEquals(burst, s.packetsWritten());
        assertEquals(burst, s.packetsEnqueued());
        printRow("join-burst", burst + "-packet burst: flush storm=" + stormFlushes.get()
                + " flushes (" + micros(stormElapsed / burst) + "/packet) vs the queue's "
                + s.flushes() + " flush (" + micros(batchedElapsed) + " total, "
                + (s.flushes() * 100 / Math.max(1, stormFlushes.get())) + "% of the storm's flush count)");
        storm.finishAndReleaseAll();
        batched.finishAndReleaseAll();
    }

    @Test
    void slowClientWedgedLoopTheSaturationObservable() throws Exception {
        // A queue on an embedded loop that is NEVER pumped: every enqueue
        // waits in the queue itself — the wedge the saturation observable
        // exists to make VISIBLE (the §35.11 slow-client evidence shape).
        EmbeddedChannel wedged = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(wedged);

        List<LogRecord> warnings = new ArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger transportLog = Logger.getLogger("net.zaminmc.torch.transport");
        transportLog.addHandler(capture);
        try {
            int packetBytes = 1024;
            int crossed = -1;
            for (int i = 0; i < 2_000; i++) {
                queue.enqueue(packetOfSize(packetBytes, i));
                if (crossed < 0 && queue.queuedBytes() >= OutboundQueue.SATURATION_LOG_BYTES) {
                    crossed = i;
                }
            }
            assertTrue(crossed >= 0, "the queue crossed the 1 MiB observable");
            // The wedge is VISIBLE: the queue's LIVE counters carry the
            // backlog (the drain-side depth high-water only records when a
            // drain runs — in a full wedge NOTHING runs, so the live
            // queuedPackets/queuedBytes pair is the wedge's face), the
            // write counters are silent, admission stays open (the liveness
            // law — the queue degrades, never wedges the caller).
            assertTrue(queue.queuedBytes() >= OutboundQueue.SATURATION_LOG_BYTES);
            assertTrue(queue.queuedPackets() > 0);
            assertEquals(0, queue.metrics().snapshot().packetsWritten());
            assertEquals(0, queue.metrics().snapshot().latencySamples());

            boolean loggedOnce = warnings.stream().anyMatch(r ->
                    r.getMessage() != null && r.getMessage().contains("not draining"));
            assertTrue(loggedOnce, "the crossing logged the wedge warning");
            printRow("slow-client", "the loop wedged: " + queue.queuedPackets()
                    + " tickets (" + queue.queuedBytes() + " bytes) waited past the "
                    + OutboundQueue.SATURATION_LOG_BYTES + "B observable at enqueue #"
                    + (crossed + 1) + ", the warning logged once, the caller never blocked, "
                    + "0 writes, 0 latency samples (the degradation lives in the queue's "
                    + "live depth — the kick decision's evidence row)");
        } finally {
            transportLog.removeHandler(capture);
            // The error path releases every waiting ticket (§35.13) — the
            // wedge's own cleanup, nothing leaks.
            queue.closeNow();
        }
    }

    @Test
    void overloadedSimulationDualLaneConcurrentEnqueue() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        OutboundQueue queue = new OutboundQueue(channel);
        final int perLane = 5_000;


        long start = System.nanoTime();
        CountDownLatch both = new CountDownLatch(2);
        Thread laneA = new Thread(() -> {
            for (int i = 0; i < perLane; i++) {
                queue.enqueue(packetOfSize(64, i));
            }
            both.countDown();
        }, "lane-a");
        Thread laneB = new Thread(() -> {
            for (int i = 0; i < perLane; i++) {
                queue.enqueueUrgent(packetOfSize(64, 1_000_000L + i));
            }
            both.countDown();
        }, "lane-b");
        laneA.start();
        laneB.start();
        assertTrue(both.await(60, TimeUnit.SECONDS));
        // One pump, one drain task pending (the coalescing law covers both
        // lanes — the urgent burst cannot flood the task queue), so the
        // embedded loop's write-side task replay stays depth-1 and this
        // final pump is safe at any volume.
        channel.runPendingTasks();
        long elapsed = System.nanoTime() - start;

        TransportMetrics.Snapshot s = queue.metrics().snapshot();
        assertEquals(2L * perLane, s.packetsEnqueued());
        assertEquals(2L * perLane, s.packetsWritten());
        assertTrue(s.flushes() > 0);
        printRow("overloaded-dual-lane", (2 * perLane) + " packets from two lanes (ordinary + urgent), "
                + rate(2L * perLane, elapsed) + ", flushes=" + s.flushes()
                + " (the coalescing law covers both lanes — one pending drain task), "
                + "dwell p99=" + micros(s.latencyP99Nanos())
                + ", max depth=" + s.maxQueueDepth());
        channel.finishAndReleaseAll();
    }
}
