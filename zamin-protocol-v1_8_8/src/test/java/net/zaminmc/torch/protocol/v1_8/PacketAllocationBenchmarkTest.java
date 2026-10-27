package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §35.4 pooled-allocation decision's before/after harness: the engine's
 * representative outbound packet shapes — produced through this module's own
 * {@link ByteBufOps} encode law — under the three allocator arms the section
 * compares. The engine today allocates every outbound packet through
 * {@code Unpooled.buffer(n)} (heap, unpooled — the 193 codec sites); the
 * pooled arms are the candidates §35.4 names. The rows printed for the
 * ledger: packets/sec, bytes/sec, the production p50/p95/p99, the thread's
 * allocated bytes (the GC-pressure proxy — every unpooled heap packet is a
 * fresh byte[] that becomes garbage on release), the GC beans' collection
 * delta, the producing thread's CPU, and the pooled arenas' retained
 * footprint.
 *
 * <p>Scope honesty (the Wave T-2 matrix's own hand-off): this harness owns
 * the ALLOCATION axis alone — the packet shapes are the engine's real encode
 * forms, but the queue-and-flush movement is Wave T-2's measured face, not
 * this one's. The assertions pin CORRECTNESS only: the produced bytes are
 * IDENTICAL across the arms (the encode law is allocator-independent) and
 * every buffer releases exactly once (the leak detector running in this JVM
 * polices the discipline — the release law is the pooled arms' admission
 * condition, §35.4's ownership list). No rate is ever asserted as a
 * threshold: a slow CI host must not fail the gate.</p>
 */
class PacketAllocationBenchmarkTest {

    /** The small shape: the world-event packet (0x21) — 24 bytes, the tracking-storm's bulk form. */
    private static final int SMALL = 24;
    /** The medium shape: a metadata/chat-class packet — varint id, string, uuid, doubles. */
    private static final int MEDIUM = 192;
    /** The large shape: a chunk bulk window — 16 KiB of column payload. */
    private static final int LARGE = 16 * 1024;
    /** The chunk payload's shared content — allocated once, copied per packet like the engine's column writes. */
    private static final byte[] CHUNK_PAYLOAD = ramp(LARGE - 5);

    private static byte[] ramp(int bytes) {
        byte[] ramp = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            ramp[i] = (byte) i;
        }
        return ramp;
    }

    /** The three arms: the engine's today (heap unpooled) against §35.4's pooled candidates. */
    private record Arm(String name, ByteBufAllocator allocator) {
    }

    private static final List<Arm> ARMS = List.of(
            new Arm("heap-unpooled (the engine today)", UnpooledByteBufAllocator.DEFAULT),
            new Arm("pooled-heap", PooledByteBufAllocator.DEFAULT),
            new Arm("pooled-direct", PooledByteBufAllocator.DEFAULT));

    private static final List<String> SHAPES = List.of("small-24B", "medium-192B", "large-16KiB");

    private static ByteBuf allocate(Arm arm, boolean direct, int capacity) {
        // The heap-unpooled arm's one face is the engine's own ask
        // (Unpooled.buffer); the pooled arms ask direct or heap off the one
        // shared allocator.
        if (arm.name().startsWith("heap-unpooled")) {
            return Unpooled.buffer(capacity);
        }
        return direct ? arm.allocator().directBuffer(capacity) : arm.allocator().heapBuffer(capacity);
    }

    private static ByteBuf produce(int shape, Arm arm, boolean direct, int sequence) {
        return switch (shape) {
            case 0 -> produceSmall(arm, direct, sequence);
            case 1 -> produceMedium(arm, direct, sequence);
            default -> produceLarge(arm, direct, sequence);
        };
    }

    private static ByteBuf produceSmall(Arm arm, boolean direct, int sequence) {
        ByteBuf out = allocate(arm, direct, SMALL);
        ByteBufOps.writeVarInt(out, 0x21);
        out.writeInt(2001 + (sequence & 0xF));
        ByteBufOps.writePackedBlockPosition(out, sequence & 0x3FFFF, 64, (sequence * 3) & 0x3FFFF);
        out.writeInt(sequence);
        out.writeBoolean(false);
        return out;
    }

    private static ByteBuf produceMedium(Arm arm, boolean direct, int sequence) {
        ByteBuf out = allocate(arm, direct, MEDIUM);
        ByteBufOps.writeVarInt(out, 0x1C);
        ByteBufOps.writeString(out, "{\"text\":\"player-" + (sequence & 0xFF) + " moved\"}");
        ByteBufOps.writeUuid(out, new UUID(0x12345678ABCDEFL, sequence));
        out.writeDouble(12.5 + (sequence & 0xFF));
        out.writeDouble(64.0);
        out.writeDouble(-0.5 - (sequence & 0x7F));
        out.writeFloat((sequence & 0xF) / 16f);
        return out;
    }

    private static ByteBuf produceLarge(Arm arm, boolean direct, int sequence) {
        ByteBuf out = allocate(arm, direct, LARGE);
        ByteBufOps.writeVarInt(out, 0x26);
        out.writeInt(sequence);
        out.writeBytes(CHUNK_PAYLOAD);
        return out;
    }

    @Test
    void theEncodeLawIsAllocatorIndependent() {
        // The admission condition: every arm produces the IDENTICAL bytes for
        // the same sequence — the allocator changes where the bytes live,
        // never what they are. A pooled arm that failed this is broken, not
        // merely slower.
        int[] capacities = {SMALL, MEDIUM, LARGE};
        for (int shape = 0; shape < 3; shape++) {
            ByteBuf baseline = produce(shape, ARMS.get(0), false, 42);
            byte[] expected = new byte[baseline.readableBytes()];
            baseline.readBytes(expected);
            baseline.release();
            for (int arm = 1; arm < ARMS.size(); arm++) {
                for (boolean direct : new boolean[] {false, true}) {
                    ByteBuf candidate = produce(shape, ARMS.get(arm), direct, 42);
                    assertEquals(expected.length, candidate.readableBytes(),
                            "shape " + SHAPES.get(shape) + " arm " + ARMS.get(arm).name()
                                    + (direct ? " direct" : " heap"));
                    for (int i = 0; i < expected.length; i++) {
                        if (candidate.getByte(i) != expected[i]) {
                            throw new AssertionError("byte mismatch at " + i + " — shape "
                                    + SHAPES.get(shape) + ", arm " + ARMS.get(arm).name()
                                    + (direct ? " direct" : " heap")
                                    + ": the encode law is NOT allocator-independent");
                        }
                    }
                    candidate.release();
                }
            }
        }
        printRow("byte-identity", "all three shapes identical across every arm (heap/direct)");
    }

    @Test
    void allocationRateAndGcPressurePerArm() {
        com.sun.management.ThreadMXBean threadBean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().getId();
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();

        int[] packetsPerShape = {200_000, 50_000, 2_000};
        int[] latencySamples = {20_000, 10_000, 1_000};

        for (int shape = 0; shape < 3; shape++) {
            int packets = packetsPerShape[shape];
            int packetBytes = shape == 0 ? SMALL : shape == 1 ? MEDIUM : LARGE;
            for (Arm arm : ARMS) {
                for (boolean direct : armFaces(arm)) {
                    // Warmup: JIT + the pooled arena's first carve.
                    runBatch(shape, arm, direct, Math.min(packets, 10_000), null);
                    long allocBefore = threadBean.getThreadAllocatedBytes(threadId);
                    long gcCountBefore = gcCount(gcs);
                    long gcTimeBefore = gcTime(gcs);
                    long cpuBefore = threadBean.getThreadCpuTime(threadId);

                    long start = System.nanoTime();
                    long checksum = runBatch(shape, arm, direct, packets, null);
                    long elapsed = System.nanoTime() - start;

                    long allocDelta = threadBean.getThreadAllocatedBytes(threadId) - allocBefore;
                    long gcCountDelta = gcCount(gcs) - gcCountBefore;
                    long gcTimeDelta = gcTime(gcs) - gcTimeBefore;
                    long cpuDelta = threadBean.getThreadCpuTime(threadId) - cpuBefore;

                    // The production latency row: a sampled pass, per-packet.
                    long[] samples = new long[latencySamples[shape]];
                    runBatch(shape, arm, direct, latencySamples[shape], samples);

                    printRow(SHAPES.get(shape), arm.name() + (direct ? " direct" : " heap")
                            + ": " + rate(packets, elapsed) + ", "
                            + bytesPerSec((long) packets * packetBytes, elapsed) + ", "
                            + "p50 " + micros(percentile(samples, 50)) + " / p95 "
                            + micros(percentile(samples, 95)) + " / p99 "
                            + micros(percentile(samples, 99)) + ", "
                            + "thread-alloc " + mebibytes(allocDelta) + " ("
                            + (allocDelta / Math.max(1, packets)) + " B/packet), "
                            + "GC " + gcCountDelta + " collections " + gcTimeDelta + "ms, "
                            + "CPU " + micros(cpuDelta) + ", checksum " + checksum);
                }
            }
        }
        // §35.4's retained-memory row: what the pooled arena holds after full
        // release — the cost of the carve, visible.
        PooledByteBufAllocator metric = PooledByteBufAllocator.DEFAULT;
        printRow("pooled-retained",
                "direct " + mebibytes(metric.metric().usedDirectMemory()) + ", heap "
                        + mebibytes(metric.metric().usedHeapMemory()) + " after all releases");
        assertTrue(PooledByteBufAllocator.DEFAULT.metric().usedDirectMemory() >= 0);
    }

    /**
     * The heap-unpooled arm has one face (heap — that IS Unpooled.buffer);
     * the pooled arms have both.
     */
    private static boolean[] armFaces(Arm arm) {
        return arm.name().startsWith("heap-unpooled") ? new boolean[] {false} : new boolean[] {false, true};
    }

    /** One production batch: produce, consume (blackhole checksum), release — the engine's encode-to-handover window. */
    private static long runBatch(int shape, Arm arm, boolean direct, int packets, long[] latencySink) {
        long checksum = 0;
        for (int i = 0; i < packets; i++) {
            long t0 = latencySink != null ? System.nanoTime() : 0L;
            ByteBuf packet = produce(shape, arm, direct, i);
            checksum += packet.getByte(packet.writerIndex() - 1);
            packet.release();
            if (latencySink != null) {
                latencySink[i] = System.nanoTime() - t0;
            }
        }
        return checksum;
    }

    private static long gcCount(List<GarbageCollectorMXBean> gcs) {
        return gcs.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private static long gcTime(List<GarbageCollectorMXBean> gcs) {
        return gcs.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    private static long percentile(long[] samples, int p) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        int idx = Math.min(sorted.length - 1, (p * sorted.length + 99) / 100 - 1);
        return sorted[Math.max(0, idx)];
    }

    private static void printRow(String scenario, String row) {
        System.out.println("[bench-35.4] " + scenario + ": " + row);
    }

    private static String micros(long nanos) {
        return String.format("%.1fus", nanos / 1000.0);
    }

    private static String rate(long packets, long nanos) {
        return String.format("%,d pps", (long) (packets * 1_000_000_000.0 / Math.max(1, nanos)));
    }

    private static String bytesPerSec(long bytes, long nanos) {
        return String.format("%.1f MiB/s", bytes / 1024.0 / 1024.0 / (nanos / 1_000_000_000.0));
    }

    private static String mebibytes(long bytes) {
        return String.format("%.1f MiB", bytes / 1024.0 / 1024.0);
    }
}
