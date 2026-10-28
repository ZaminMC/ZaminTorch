package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §35.12 compression-enabled/disabled and encryption-enabled/disabled
 * performance rows at the PROTOCOL layer — the two scenarios Wave T-2's
 * transport matrix argued away (the queue sits inside the plaintext
 * boundary, so the transport harness never measured the codecs). This
 * harness drives the production pipeline ORDER (the outbound walk
 * compress -> frame -> encrypt; the inbound mirror decrypt -> deframe ->
 * decompress — V18Connection's own install rows) over the two
 * representative shapes the §35.12 list names for the codecs' own costs:
 * chunk streaming (large, compressible frames — the compression CPU row)
 * and entity tracking (small frames below the threshold — the per-packet
 * codec overhead row).
 *
 * <p>Every arm asserts CORRECTNESS invariants only (the sampled
 * round-trips are byte-identical); the numbers print for the ledger — a
 * slow CI host never fails the gate (the T-2 convention).</p>
 */
class WireCodecBenchmarkTest {

    /** The production compression threshold (ServerLayout's own default row). */
    private static final int THRESHOLD = 256;

    /** The measured packet counts (sized for a stable mean on a dev host). */
    private static final int CHUNK_PACKETS = 2_048;
    private static final int CHUNK_BYTES = 16 * 1024;
    private static final int ENTITY_PACKETS = 200_000;
    private static final int ENTITY_BYTES = 40;

    /** The warmup share — the JIT's first walks are not the codec's face. */
    private static final double WARMUP_SHARE = 0.2;

    // ------------------------------------------------------------------
    // The arms (the §35.12 codec rows)
    // ------------------------------------------------------------------

    @Test
    void chunkStreamingAcrossTheFourCodecArms() {
        byte[][] payloads = chunkPayloads();
        for (Arm arm : Arm.values()) {
            runArm("chunk-streaming/" + arm.label, arm, payloads, 3);
        }
    }

    @Test
    void entityTrackingAcrossTheFourCodecArms() {
        byte[][] payloads = entityPayloads();
        for (Arm arm : Arm.values()) {
            runArm("entity-tracking/" + arm.label, arm, payloads, 25);
        }
    }

    /** The four codec states §35.12 names. */
    private enum Arm {
        PLAINTEXT("plaintext", false, false),
        COMPRESSION("compression", true, false),
        ENCRYPTION("encryption", false, true),
        BOTH("compression+encryption", true, true);

        final String label;
        final boolean compress;
        final boolean encrypt;

        Arm(String label, boolean compress, boolean encrypt) {
            this.label = label;
            this.compress = compress;
            this.encrypt = encrypt;
        }
    }

    // ------------------------------------------------------------------
    // The harness
    // ------------------------------------------------------------------

    /**
     * One arm over one workload: the full outbound pipeline
     * (compress -> frame -> encrypt), then the full inbound mirror
     * (decrypt -> deframe -> decompress), the sampled round-trips
     * asserted byte-identical. The wire bytes are what the channel
     * would put on the socket — the compression arm's payload shrink
     * rides the reported bytes/sec row.
     */
    private static void runArm(String scenario, Arm arm, byte[][] payloads, int samples) {
        SecretKey secret = TestClient18.clientSecret();

        // The outbound pipeline in the production order: outbound walks
        // compress FIRST, then frame, then encrypt (V18Connection's own
        // install rows 602-608 + 533-539 — the pipeline LIST holds
        // [.., encrypt, frame-encoder, compress] and outbound enters at
        // the TAIL). The EmbeddedChannel constructor adds head-first, so
        // the array is the LIST order: encrypt, frame, compress — the
        // absent codecs simply never install (the arm's own shape).
        List<io.netty.channel.ChannelHandler> outHandlers = new ArrayList<>();
        if (arm.encrypt) {
            outHandlers.add(new EncryptionCodec.Encoder(
                    TorchEncryption.streamCipher(1, secret)));
        }
        outHandlers.add(new FrameCodec.Encoder());
        if (arm.compress) {
            outHandlers.add(new CompressionCodec.Encoder(THRESHOLD));
        }
        EmbeddedChannel outbound = new EmbeddedChannel(
                outHandlers.toArray(new io.netty.channel.ChannelHandler[0]));

        // The inbound mirror: inbound enters at the HEAD, so the list
        // order IS the walk order — decrypt, deframe, decompress.
        List<io.netty.channel.ChannelHandler> inHandlers = new ArrayList<>();
        if (arm.encrypt) {
            inHandlers.add(new EncryptionCodec.Decoder(
                    TorchEncryption.streamCipher(2, secret)));
        }
        inHandlers.add(new FrameCodec.Decoder());
        if (arm.compress) {
            inHandlers.add(new CompressionCodec.Decoder(THRESHOLD));
        }
        EmbeddedChannel inbound = new EmbeddedChannel(
                inHandlers.toArray(new io.netty.channel.ChannelHandler[0]));

        int warmup = (int) (payloads.length * WARMUP_SHARE);
        List<Integer> sampleIndices = sampleIndices(payloads.length, samples);

        // Warmup.
        for (int i = 0; i < warmup; i++) {
            writeOne(outbound, inbound, payloads[i], -1);
        }

        long payloadBytes = 0;
        long wireBytes = 0;
        long start = System.nanoTime();
        for (int i = warmup; i < payloads.length; i++) {
            payloadBytes += payloads[i].length;
            wireBytes += writeOne(outbound, inbound, payloads[i],
                    sampleIndices.contains(i) ? i : -1);
        }
        long elapsed = System.nanoTime() - start;

        double pps = (payloads.length - warmup) * 1e9 / elapsed;
        double payloadMiB = payloadBytes / (1024.0 * 1024.0);
        double wireMiB = wireBytes / (1024.0 * 1024.0);
        printRow(scenario,
                String.format(java.util.Locale.ROOT,
                        "%.0f pps, payload %.1f MiB/s, wire %.1f MiB/s, "
                                + "shrink %.2fx, wire/packet %d B",
                        pps,
                        payloadMiB / (elapsed / 1e9),
                        wireMiB / (elapsed / 1e9),
                        payloadBytes == 0 ? 1.0 : (double) payloadBytes / Math.max(1, wireBytes),
                        payloads.length - warmup == 0 ? 0 : wireBytes / (payloads.length - warmup)));

        outbound.finishAndReleaseAll();
        inbound.finishAndReleaseAll();
    }

    /**
     * One packet through both pipelines: the payload out the outbound
     * channel, the wire bytes measured, the inbound mirror walked, and
     * the round-trip asserted byte-identical when the index names a
     * sample. @return the wire byte count.
     */
    private static long writeOne(EmbeddedChannel outbound, EmbeddedChannel inbound,
                                 byte[] payload, int sampleIndex) {
        assertTrue(outbound.writeOutbound(Unpooled.wrappedBuffer(payload).copy()));
        ByteBuf wire = outbound.readOutbound();
        long wireBytes = wire.readableBytes();
        // The inbound mirror: the same wire bytes a real client reads.
        inbound.writeInbound(wire);
        ByteBuf decoded = inbound.readInbound();
        if (sampleIndex >= 0) {
            assertEquals(payload.length, decoded.readableBytes(),
                    "sample " + sampleIndex + " round-trips whole");
            byte[] back = new byte[payload.length];
            decoded.readBytes(back);
            assertTrue(java.util.Arrays.equals(payload, back),
                    "sample " + sampleIndex + " round-trips byte-exact");
        }
        decoded.release();
        return wireBytes;
    }

    // ------------------------------------------------------------------
    // The payloads
    // ------------------------------------------------------------------

    /**
     * The chunk-streaming shape: 16 KiB frames of terrain-like data —
     * a seeded random walk over a small block-id alphabet (long runs
     * of one id, occasional boundaries), the compressibility a real
     * chunk column carries.
     */
    private static byte[][] chunkPayloads() {
        Random random = new Random(3512L);
        byte[][] payloads = new byte[CHUNK_PACKETS][];
        for (int i = 0; i < CHUNK_PACKETS; i++) {
            byte[] payload = new byte[CHUNK_BYTES];
            byte id = (byte) random.nextInt(8);
            for (int j = 0; j < payload.length; j++) {
                if (random.nextInt(16) == 0) {
                    id = (byte) random.nextInt(8);
                }
                payload[j] = id;
            }
            payloads[i] = payload;
        }
        return payloads;
    }

    /**
     * The entity-tracking shape: 40 B frames — a movement packet's own
     * size (the id varint, the three shift bytes, the yaw/pitch pair),
     * every frame below the compression threshold so the compression
     * arm rides the raw form with the one-varint prefix alone.
     */
    private static byte[][] entityPayloads() {
        Random random = new Random(3513L);
        byte[][] payloads = new byte[ENTITY_PACKETS][];
        for (int i = 0; i < ENTITY_PACKETS; i++) {
            byte[] payload = new byte[ENTITY_BYTES];
            random.nextBytes(payload);
            payloads[i] = payload;
        }
        return payloads;
    }

    // ------------------------------------------------------------------
    // The plumbing
    // ------------------------------------------------------------------

    /** The evenly-spread sample indices (the correctness rows). */
    private static List<Integer> sampleIndices(int total, int count) {
        List<Integer> indices = new ArrayList<>();
        int warmup = (int) (total * WARMUP_SHARE);
        int measured = total - warmup;
        for (int s = 0; s < count && s < measured; s++) {
            indices.add(warmup + (int) ((s + 0.5) * measured / count));
        }
        return indices;
    }

    private static void printRow(String scenario, String row) {
        System.out.println("[bench-35.12] " + scenario + ": " + row);
    }
}
