package net.zaminmc.torch.transport;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * The outbound transport's own counters (§35.4 / §35.12): the rows a
 * transport optimization must be able to show before and after.
 *
 * <p>The snapshot covers the slice's claim scope — the flush-count
 * bottleneck's direct rows (packets enqueued vs packets written vs flushes,
 * so packets-per-flush falls out), the queue's depth, and the dropped-packet
 * count (the lifetime law's visible face: a release-on-drop must never be
 * silent). Allocation-rate, GC, and p50/p95/p99 latency rows stay with the
 * measurement harnesses that own them; the counters here exist so any such
 * harness can read the transport's face without touching the wire.</p>
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

    /** An immutable read of the counters. */
    public Snapshot snapshot() {
        return new Snapshot(
                this.packetsEnqueued.sum(),
                this.bytesEnqueued.sum(),
                this.packetsWritten.sum(),
                this.flushes.sum(),
                this.packetsDropped.sum(),
                this.bytesDropped.sum(),
                this.directWrites.get(),
                this.maxQueueDepth.get());
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
     */
    public record Snapshot(long packetsEnqueued, long bytesEnqueued,
                           long packetsWritten, long flushes,
                           long packetsDropped, long bytesDropped,
                           long directWrites, long maxQueueDepth) {

        /** @return the average packets per flush ({@code 0.0} before any flush). */
        public double packetsPerFlush() {
            return this.flushes == 0 ? 0.0 : (double) this.packetsWritten / (double) this.flushes;
        }
    }
}
