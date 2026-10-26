package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave ON-1a's codec unit pins: the compression pair's own laws, driven
 * directly (the reference CompressionDecoder/CompressionEncoder lines,
 * ported 1:1) — the two wire forms (the zero data-length raw ride below
 * the threshold, the size-prefixed deflate at it), the deflater's reuse
 * across frames, and the decoder's two badly-compressed guards with the
 * reference's own messages.
 */
class CompressionCodecTest {

    /** The encoder's output form, read back through the reference's own walk. */
    private static ByteBuf encode(int threshold, byte[] payload) {
        EmbeddedChannel encoder = new EmbeddedChannel(new CompressionCodec.Encoder(threshold));
        assertTrue(encoder.writeOutbound(Unpooled.wrappedBuffer(payload).copy()));
        ByteBuf out = encoder.readOutbound();
        encoder.finishAndReleaseAll();
        return out;
    }

    /** The decoder's output, driven over an encoder-produced buffer. */
    private static ByteBuf decode(int threshold, ByteBuf wire) {
        EmbeddedChannel decoder = new EmbeddedChannel(new CompressionCodec.Decoder(threshold));
        assertTrue(decoder.writeInbound(wire));
        ByteBuf out = decoder.readInbound();
        decoder.finishAndReleaseAll();
        return out;
    }

    @Test
    void belowThresholdFramesRideRawBehindAZeroDataLength() {
        byte[] payload = new byte[255];
        payload[0] = 0x42;
        payload[254] = (byte) 0x99;
        ByteBuf wire = encode(256, payload);
        assertEquals(0, ByteBufOps.readVarInt(wire), "the data-length varint is zero (no compression)");
        assertEquals(payload.length, wire.readableBytes(),
                "the payload follows verbatim behind the one varint");
        ByteBuf decoded = decode(256, wire.resetReaderIndex());
        assertEquals(255, decoded.readableBytes());
        assertEquals(0x42, decoded.readUnsignedByte());
        decoded.readerIndex(254);
        assertEquals(0x99, decoded.readUnsignedByte());
        decoded.release();
    }

    @Test
    void atThresholdFramesDeflateBehindTheirUncompressedSize() throws Exception {
        // A payload exactly AT the threshold compresses (the reference's
        // strict `i < threshold` comparison, never <=).
        byte[] payload = new byte[256];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 7);
        }
        ByteBuf wire = encode(256, payload);
        int dataLength = ByteBufOps.readVarInt(wire);
        assertEquals(256, dataLength, "the data-length varint is the UNCOMPRESSED size");
        byte[] compressed = new byte[wire.readableBytes()];
        wire.readBytes(compressed);
        wire.release();
        assertTrue(compressed.length < payload.length,
                "repetitive data shrinks: " + compressed.length + " vs " + payload.length);
        // The independent client-side walk: inflate to exactly dataLength bytes.
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] inflated = new byte[dataLength];
        assertEquals(dataLength, inflater.inflate(inflated), "the payload inflates whole");
        assertTrue(inflater.finished(), "the stream is whole — nothing left");
        assertArrayEquals(payload, inflated);
    }

    @Test
    void thePairRoundTripsAcrossSizesAndReusesItsDeflater() {
        // Multiple frames through ONE encoder instance — the deflater's
        // finish/reset economy across frames is the reference's own shape.
        EmbeddedChannel pair = new EmbeddedChannel(
                new CompressionCodec.Decoder(64), new CompressionCodec.Encoder(64));
        int[] sizes = {1, 63, 64, 65, 8192, 20_000};
        for (int size : sizes) {
            byte[] payload = new byte[size];
            for (int i = 0; i < size; i++) {
                payload[i] = (byte) ((i * 31 + size) % 251);
            }
            assertTrue(pair.writeOutbound(Unpooled.wrappedBuffer(payload).copy()));
            ByteBuf wire = pair.readOutbound();
            // Feed the wire form back through the decoder side of the pair.
            EmbeddedChannel decoder = new EmbeddedChannel(new CompressionCodec.Decoder(64));
            assertTrue(decoder.writeInbound(wire.copy()));
            ByteBuf decoded = decoder.readInbound();
            assertEquals(size, decoded.readableBytes(), "size " + size + " round-trips whole");
            byte[] back = new byte[size];
            decoded.readBytes(back);
            assertArrayEquals(payload, back, "size " + size + " round-trips byte-exact");
            decoded.release();
            decoder.finishAndReleaseAll();
        }
        pair.finishAndReleaseAll();
    }

    @Test
    void emptyBuffersProduceNothing() {
        // The reference's own `readableBytes() != 0` gate: a zero-length
        // inbound buffer is not a compression defect and yields no output.
        EmbeddedChannel decoder = new EmbeddedChannel(new CompressionCodec.Decoder(256));
        assertFalse(decoder.writeInbound(Unpooled.EMPTY_BUFFER),
                "an empty buffer surfaces no inbound message (nothing to read)");
        decoder.finishAndReleaseAll();
    }

    @Test
    void theDecoderRejectsBelowThresholdCompressedSizes() {
        // A data-length below the threshold is the sender's own defect —
        // the reference's exact message.
        ByteBuf wire = Unpooled.buffer();
        ByteBufOps.writeVarInt(wire, 10);
        wire.writeBytes(new byte[8]);
        EmbeddedChannel decoder = new EmbeddedChannel(new CompressionCodec.Decoder(256));
        DecoderException error = assertThrows(DecoderException.class,
                () -> decoder.writeInbound(wire));
        assertEquals("Badly compressed packet - size of 10 is below server threshold of 256",
                error.getMessage());
        decoder.finishAndReleaseAll();
    }

    @Test
    void theDecoderRejectsSizesAboveTheProtocolMaximum() {
        ByteBuf wire = Unpooled.buffer();
        ByteBufOps.writeVarInt(wire, 2_097_153);
        wire.writeBytes(new byte[4]);
        EmbeddedChannel decoder = new EmbeddedChannel(new CompressionCodec.Decoder(256));
        DecoderException error = assertThrows(DecoderException.class,
                () -> decoder.writeInbound(wire));
        assertEquals("Badly compressed packet - size of 2097153 is larger than protocol maximum of 2097152",
                error.getMessage());
        decoder.finishAndReleaseAll();
    }

    @Test
    void incompressiblePayloadsStillRideTheCompressedForm() throws Exception {
        // Even when zlib cannot shrink the payload (random data), the
        // encoder still writes the size-prefixed deflate — the decoder
        // inflates it back whole. The wire form never depends on the
        // data's compressibility.
        byte[] payload = new byte[512];
        new java.util.Random(1234L).nextBytes(payload);
        ByteBuf wire = encode(256, payload);
        int dataLength = ByteBufOps.readVarInt(wire);
        assertEquals(512, dataLength);
        byte[] compressed = new byte[wire.readableBytes()];
        wire.readBytes(compressed);
        wire.release();
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] inflated = new byte[512];
        assertEquals(512, inflater.inflate(inflated));
        assertArrayEquals(payload, inflated);
    }

    @Test
    void aHumanReadablePayloadRoundTripsExactly() {
        // The one-liner sanity walk: text in, text out, byte-for-byte.
        byte[] payload = ("compression, the vanilla way: "
                + "x".repeat(600)).getBytes(StandardCharsets.UTF_8);
        ByteBuf wire = encode(256, payload);
        ByteBuf decoded = decode(256, wire);
        byte[] back = new byte[decoded.readableBytes()];
        decoded.readBytes(back);
        assertArrayEquals(payload, back);
        decoded.release();
    }
}
