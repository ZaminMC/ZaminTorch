package net.zaminmc.torch.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The per-connection outbound stage (§35): outbound queueing, write
 * batching, and the flush policy — the common transport every protocol
 * implementation shares. The version-specific codecs produce the packet
 * buffers; this class moves them.
 *
 * <p><b>The batching boundary is explicit</b> (§35.5): an ordinary
 * {@link #enqueue(ByteBuf)} joins a FIFO queue and schedules ONE drain task
 * on the channel's event loop; the drain pops everything queued, issues
 * {@code write()} per packet, and issues a SINGLE {@code flush()} — the
 * {@code write → write → write → flush} shape beside the reference's own
 * per-packet {@code write → flush} storm. The drain scheduling coalesces:
 * however many packets a tick walk enqueues, the loop runs one drain.</p>
 *
 * <p><b>The flush policy</b>: ordinary traffic batches. Latency-sensitive
 * traffic rides {@link #enqueueUrgent(ByteBuf)} — the packet joins the
 * queue's tail (order never inverts) and rides the soonest drain (a pending
 * drain carries it; none pending, one schedules at once), so the queued
 * ordinary packets and the urgent packet land in the same single flush. A
 * closing channel rides {@link #closeWith(ByteBuf)}: the final packet (a
 * Disconnect) joins, everything queued writes with one flush, and the
 * channel closes — the disconnect can never overtake packets the client
 * was already owed.</p>
 *
 * <p><b>Thread boundary</b> (§35.9): enqueues arrive from the simulation
 * lanes; every drain runs on the channel's own event loop — the loop never
 * becomes gameplay authority, and the queue never learns protocol
 * semantics. The queue preserves the connection's FIFO ordering across
 * threads: a {@code ConcurrentLinkedQueue} fed from any lane, drained by
 * the single loop thread.</p>
 *
 * <p><b>The ownership law</b> (§35.4): a packet buffer handed to
 * {@code enqueue} becomes the queue's ticket. The queue releases the ticket
 * on exactly one of three paths: the pipeline consumes it after the drain's
 * write (the frame encoder's post-encode release, and Netty's own
 * write-failure release when the channel rejects it); the queue drops it on
 * {@link #closeNow()} (the cancelled-work path); or the admission-closed
 * drop. The queued-byte ceiling is the saturation OBSERVABLE for this
 * slice: past it the queue logs and the depth counter carries the
 * high-water mark — the queue itself degrades no differently than the
 * channel's own outbound buffer would. The WEDGED-LOOP CEILING
 * (§35.13's fail-safely law) bounds the one growth a healthy connection
 * never shows: a queue that far past the bound means the event loop has
 * stopped draining entirely — the connection closes NOW (every queued
 * ticket released, the wedged close counted) rather than holding the
 * queue's memory hostage forever. A slow SOCKET never reaches it: the
 * drain still writes, and Netty's own outbound buffer absorbs the
 * backpressure exactly as the reference's per-packet shape did — the
 * ceiling is the wedged loop's protection alone.</p>
 *
 * <p><b>Shutdown</b> (§35.13): {@link #close()} stops admission, drains
 * what was admitted (one flush), and closes the channel — no retained
 * packet survives the close. {@link #closeNow()} stops admission, releases
 * everything queued, and closes at once (the error paths).</p>
 */
public final class OutboundQueue {

    /**
     * The queued-byte saturation observable (the §35.12 queue-depth row):
     * past this many payload bytes waiting in the queue, the enqueue logs.
     * The bound exists so a wedged event loop is VISIBLE in the metrics;
     * the slice's liveness law stays the channel's own (Netty's outbound
     * buffer already absorbs a slow socket exactly as the per-packet-flush
     * shape did — no regression, no silent reordering shortcut).
     */
    static final int SATURATION_LOG_BYTES = 1 << 20; // 1 MiB

    /**
     * The wedged-loop ceiling (§35.13's fail-safely law): past this many
     * payload bytes the connection closes NOW — the event loop is never
     * coming back at that depth, and the memory is freed instead of held.
     * The bound sits 64x past the saturation observable and far past any
     * healthy-loop traffic (a healthy loop drains every task cycle; the
     * benchmark's own face answers 20 packets per flush at a 6us dwell —
     * 64 MiB of depth is a loop SECONDS behind, which is a dead loop, not
     * a slow one). Only a stopped loop reaches it: a slow socket's writes
     * still complete into Netty's own outbound buffer, emptying this
     * queue — the ceiling never fires for backpressure the reference
     * itself absorbs the same way.
     */
    static final long WEDGED_QUEUE_CLOSE_BYTES = 64L << 20; // 64 MiB

    private final Channel channel;
    private final TransportMetrics metrics;
    private final ConcurrentLinkedQueue<QueuedPacket> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong queuedBytes = new AtomicLong();
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);
    private volatile boolean admissionOpen = true;

    /** One queued ticket and its enqueue stamp — the §35.12 dwell row's raw material. */
    private record QueuedPacket(ByteBuf packet, long enqueuedAtNanos) {
    }

    public OutboundQueue(Channel channel) {
        this(channel, new TransportMetrics());
    }

    public OutboundQueue(Channel channel, TransportMetrics metrics) {
        this.channel = channel;
        this.metrics = metrics;
    }

    /** @return the queue's own metrics face. */
    public TransportMetrics metrics() {
        return this.metrics;
    }

    /** @return whether the underlying channel is still open. */
    public boolean isActive() {
        return this.channel.isActive();
    }

    /**
     * Ordinary traffic: the packet joins the queue's tail and the drain
     * schedules on the event loop (coalesced — at most one pending drain
     * task). Takes ownership of the buffer: the caller must not touch it
     * after the call.
     */
    public void enqueue(ByteBuf packet) {
        if (!admit(packet)) {
            return;
        }
        int bytes = packet.readableBytes();
        this.metrics.packetEnqueued(bytes);
        long nowQueued = this.queuedBytes.addAndGet(bytes);
        if (nowQueued >= SATURATION_LOG_BYTES && (nowQueued - bytes) < SATURATION_LOG_BYTES) {
            // The saturation observable crossed once per crossing, not per packet.
            TransportLog.get().warning(() -> "Outbound queue past " + SATURATION_LOG_BYTES
                    + " bytes on " + this.channel + " — the event loop is not draining");
        }
        if (wedged(nowQueued, bytes, packet)) {
            return;
        }
        this.queue.add(new QueuedPacket(packet, System.nanoTime()));
        scheduleDrain();
    }

    /**
     * Latency-sensitive traffic: the packet joins the queue's tail (FIFO
     * never inverts) and rides the SOONEST drain — an already-scheduled
     * drain carries it (the queue's order puts it behind exactly what was
     * already owed), otherwise one is scheduled at once. One flush carries
     * the queued packets and this one.
     */
    public void enqueueUrgent(ByteBuf packet) {
        if (!admit(packet)) {
            return;
        }
        int bytes = packet.readableBytes();
        this.metrics.packetEnqueued(bytes);
        long nowQueued = this.queuedBytes.addAndGet(bytes);
        if (wedged(nowQueued, bytes, packet)) {
            return;
        }
        this.queue.add(new QueuedPacket(packet, System.nanoTime()));
        // The coalescing law covers both lanes (§35.5: "however many
        // packets a tick walk enqueues, the loop runs one drain") — the
        // ordering guarantee rides the FIFO queue itself, never the task
        // queue's shape: a pending drain pops this packet in its turn, and
        // an urgent burst cannot flood the loop with per-packet drain tasks.
        scheduleDrain();
    }

    /**
     * The ordered close (§35.13): the last packet (a Disconnect) joins the
     * queue's tail, admission closes, the final drain writes everything
     * admitted with ONE flush, and the channel closes. Takes ownership of
     * the buffer.
     */
    public void closeWith(ByteBuf packet) {
        if (admit(packet)) {
            this.metrics.packetEnqueued(packet.readableBytes());
            this.queuedBytes.addAndGet(packet.readableBytes());
            this.queue.add(new QueuedPacket(packet, System.nanoTime()));
        }
        close();
    }

    /**
     * The ordered shutdown: admission closes, the remaining queue drains
     * (one flush), the channel closes. Safe to call from any thread and
     * more than once.
     */
    public void close() {
        this.admissionOpen = false;
        Runnable finalDrain = () -> {
            this.drainScheduled.set(false);
            drainNow();
            this.channel.close();
        };
        if (this.channel.eventLoop().inEventLoop()) {
            finalDrain.run();
        } else {
            this.channel.eventLoop().execute(finalDrain);
        }
    }

    /**
     * The error-path shutdown: admission closes, every queued packet
     * RELEASES (the cancelled-work path, §35.13), the channel closes at
     * once. Nothing queued survives; nothing leaks.
     */
    public void closeNow() {
        this.admissionOpen = false;
        this.drainScheduled.set(false);
        QueuedPacket node;
        while ((node = this.queue.poll()) != null) {
            int bytes = node.packet().readableBytes();
            this.queuedBytes.addAndGet(-bytes);
            this.metrics.packetDropped(bytes);
            node.packet().release();
        }
        this.channel.close();
    }

    /** @return the number of packets currently waiting (diagnostics; the §35.12 queue-depth row). */
    public int queuedPackets() {
        return this.queue.size();
    }

    /** @return the payload bytes currently waiting. */
    public long queuedBytes() {
        return this.queuedBytes.get();
    }

    /**
     * The admission gate: a closed queue or an inactive channel drops the
     * packet with a release — the failure path is visible in the metrics,
     * never silent, and never a leak (§35.13).
     */
    private boolean admit(ByteBuf packet) {
        if (!this.admissionOpen || !this.channel.isActive()) {
            this.metrics.packetDropped(packet.readableBytes());
            packet.release();
            return false;
        }
        return true;
    }

    /**
     * The wedged-loop ceiling (§35.13): the enqueue that pushed the queue
     * past the close bound releases its own ticket (counted, never
     * silent), then the connection closes NOW — every queued ticket
     * released, the close counted once, the loop's dead connection freed.
     * Never throws: the simulation lanes that enqueue must not see a
     * gameplay exception from a transport protection.
     *
     * @param nowQueued the queue's byte total INCLUDING this packet
     * @param bytes     this packet's own payload bytes
     * @return {@code true} when the ceiling closed the connection (the
     *         caller must NOT queue the packet — it is released)
     */
    private boolean wedged(long nowQueued, int bytes, ByteBuf packet) {
        if (nowQueued < WEDGED_QUEUE_CLOSE_BYTES) {
            return false;
        }
        this.queuedBytes.addAndGet(-bytes);
        this.metrics.packetDropped(bytes);
        packet.release();
        TransportLog.get().severe(() -> "Outbound queue wedged past " + WEDGED_QUEUE_CLOSE_BYTES
                + " bytes on " + this.channel + " — closing the connection (the event loop is not draining)");
        closeNow();
        this.metrics.wedgedClose();
        return true;
    }

    /**
     * The coalesced scheduling: at most one pending drain task at a time;
     * both lanes ride it (the urgent lane's packet joins the FIFO queue,
     * so a pending drain carries it — the queue is the order guarantee,
     * never the task queue's shape).
     */
    private void scheduleDrain() {
        if (this.drainScheduled.compareAndSet(false, true)) {
            this.channel.eventLoop().execute(() -> {
                this.drainScheduled.set(false);
                drainNow();
            });
        }
    }

    /**
     * One drain: pop the queue FIFO, {@code write()} per packet (the
     * pipeline owns each ticket from here — consumption and failure releases
     * are the channel's), then a SINGLE {@code flush()}. The drain runs
     * only on the event loop, so two drains never interleave.
     */
    private void drainNow() {
        if (this.queue.isEmpty()) {
            return;
        }
        int written = 0;
        QueuedPacket node;
        while ((node = this.queue.poll()) != null) {
            ByteBuf packet = node.packet();
            int bytes = packet.readableBytes();
            this.queuedBytes.addAndGet(-bytes);
            if (!this.channel.isActive()) {
                // The channel died between admission and the drain: the
                // failure path releases the ticket instead of writing into
                // the dead socket (§35.13 — a failed write releases every
                // reference the failed path owns; the drop is counted, never
                // silent, and never a broken-pipe storm).
                this.metrics.packetDropped(bytes);
                packet.release();
                continue;
            }
            // The write hands the ticket to the pipeline: the frame encoder
            // releases it after encoding, and Netty's own failure path
            // releases it when the channel rejects the write. Both are the
            // channel's law; the queue's ownership ends here. The packet's
            // dwell (enqueue → this write) lands in the §35.12 latency row.
            this.metrics.recordLatencyNanos(System.nanoTime() - node.enqueuedAtNanos());
            this.channel.write(packet, this.channel.voidPromise());
            this.metrics.packetWritten();
            written++;
        }
        this.metrics.observeQueueDepth(this.queue.size());
        if (written > 0) {
            this.channel.flush();
            this.metrics.flushed();
        }
    }
}
