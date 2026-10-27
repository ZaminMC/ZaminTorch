package net.zaminmc.torch.transport;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * The outbound transport's own counters (§35.4 / §35.12): the rows a
 * transport optimization must be able to show before and after.
 *
 * <p>The snapshot covers the slice's claim scope — the flush-count
 * bottleneck's direct rows (packets enqueued vs packets written vs flushes,
 * so packets-per-flush falls out), the queue's depth, the dropped-packet
 * count (the lifetime law's visible face: a release-on-drop must never be
 * silent), and the §35.12 outbound-latency rows: the p50/p95/p99 of the
 * queue's own dwell (enqueue → the drain's write), sampled into a fixed
 * ring (the newest sample wins on wrap; the percentiles answer over the
 * valid window, nearest-rank). Allocation-rate, GC, serialization CPU, and
 * compression CPU rows stay with the measurement harnesses that own them;
 * the counters here exist so any such harness can read the transport's face
 * without touching the wire.</p>
 *
 * <p>All counters are monotonic; the snapshot is a plain read. Safe from any
 * thread.</p>
 */
public final class TransportMetrics {

    private final LongAdder packetsEnqueued = new LongAdder();
    private final LongAdder bytesEnqueued = new LongAdder();
    private final LongAdder packetsWritten = new LongAdder();
    private final LongAdder flushes = new LongAdder();
    private final LongAdder packetsDropped = new LongAdder();
    private final LongAdder bytesDropped = new LongAdder();
    private final AtomicLong directWrites = new AtomicLong();
    private final AtomicLong maxQueueDepth = new AtomicLong();

    /** The latency ring's capacity: the newest {@value #LATENCY_SAMPLE_CAPACITY} dwell samples carry the percentiles. */
    static final int LATENCY_SAMPLE_CAPACITY = 4096;

    private final long[] latencyRing = new long[LATENCY_SAMPLE_CAPACITY];
    private final AtomicLong latencyRecorded = new AtomicLong();
    private final AtomicLong latencyMax = new AtomicLong();

    /** One packet joined the queue (ordinary or urgent; the direct-write fallback counts separately). */
    void packetEnqueued(int bytes) {
        this.packetsEnqueued.increment();
        this.bytesEnqueued.add(bytes);
    }

    /** One packet left the queue through a drain's write(). */
    void packetWritten() {
        this.packetsWritten.increment();
    }

    /** One drain's single flush. */
    void flushed() {
        this.flushes.increment();
    }

    /** One packet released without ever reaching the channel (close/cancel paths, §35.13). */
    void packetDropped(int bytes) {
        this.packetsDropped.increment();
        this.bytesDropped.add(bytes);
    }

    /** One packet bypassed the queue through the saturation fallback's direct write. */
    void directWrite() {
        this.directWrites.incrementAndGet();
    }

    /** The drain observed this queue depth; keeps the high-water mark. */
    void observeQueueDepth(int depth) {
        this.maxQueueDepth.accumulateAndGet(depth, Math::max);
    }

    /**
     * One packet's outbound dwell sample (§35.12's latency row): the nanos
     * between the enqueue and the drain's write. Only WRITTEN packets
     * sample — a dropped packet never rode the wire, so it owns no latency
     * row. The sample joins the fixed ring (the newest wins on wrap); the
     * snapshot answers the percentiles over the valid window. Negative
     * deltas clamp to zero (a coarse clock's tie, never a lie).
     */
    void recordLatencyNanos(long nanos) {
        if (nanos < 0L) {
            nanos = 0L;
        }
        long index = this.latencyRecorded.getAndIncrement();
        this.latencyRing[(int) (index % LATENCY_SAMPLE_CAPACITY)] = nanos;
        this.latencyMax.accumulateAndGet(nanos, Math::max);
    }

    /** The sorted valid window of the latency ring (the percentile source). */
    private long[] latencyWindow() {
        long recorded = this.latencyRecorded.get();
        int valid = (int) Math.min(recorded, LATENCY_SAMPLE_CAPACITY);
        long[] window = new long[valid];
        System.arraycopy(this.latencyRing, 0, window, 0, valid);
        Arrays.sort(window);
        return window;
    }

    /** Nearest-rank percentile over a sorted window ({@code 0} for an empty one). */
    private static long nearestRank(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0L;
        }
        int index = (int) Math.ceil(p * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }

    /** An immutable read of the counters. */
    public Snapshot snapshot() {
        long[] window = latencyWindow();
        return new Snapshot(
                this.packetsEnqueued.sum(),
                this.bytesEnqueued.sum(),
                this.packetsWritten.sum(),
                this.flushes.sum(),
                this.packetsDropped.sum(),
                this.bytesDropped.sum(),
                this.directWrites.get(),
                this.maxQueueDepth.get(),
                this.latencyRecorded.get(),
                nearestRank(window, 0.50),
                nearestRank(window, 0.95),
                nearestRank(window, 0.99),
                this.latencyMax.get());
    }

    /**
     * @param packetsEnqueued packets that joined the queue
     * @param bytesEnqueued   their payload bytes
     * @param packetsWritten  packets handed to the channel through a drain
     * @param flushes         the drain's flush count (the bottleneck's row:
     *                        the per-packet-flush shape answers
     *                        {@code flushes == packetsWritten}; the batched
     *                        shape answers {@code flushes <= drains})
     * @param packetsDropped  packets released without reaching the channel
     * @param bytesDropped    their payload bytes
     * @param directWrites    packets that bypassed the queue under saturation
     * @param maxQueueDepth   the queue depth's high-water mark
     * @param latencySamples  total dwell samples recorded (monotonic; the ring keeps the newest {@value #LATENCY_SAMPLE_CAPACITY})
     * @param latencyP50Nanos the nearest-rank p50 of the ring window (enqueue → the drain's write)
     * @param latencyP95Nanos the nearest-rank p95 of the ring window
     * @param latencyP99Nanos the nearest-rank p99 of the ring window
     * @param latencyMaxNanos the dwell high-water mark over the ring window
     */
    public record Snapshot(long packetsEnqueued, long bytesEnqueued,
                           long packetsWritten, long flushes,
                           long packetsDropped, long bytesDropped,
                           long directWrites, long maxQueueDepth,
                           long latencySamples,
                           long latencyP50Nanos, long latencyP95Nanos,
                           long latencyP99Nanos, long latencyMaxNanos) {

        /** @return the average packets per flush ({@code 0.0} before any flush). */
        public double packetsPerFlush() {
            return this.flushes == 0 ? 0.0 : (double) this.packetsWritten / (double) this.flushes;
        }
    }
}
