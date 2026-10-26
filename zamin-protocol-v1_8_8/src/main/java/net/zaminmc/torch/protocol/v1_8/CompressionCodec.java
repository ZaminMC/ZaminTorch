package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The login compression codec (Wave ON-1a) — the reference's own
 * CompressionDecoder/CompressionEncoder pair, ported 1:1 (reference/
 * 1.8.8/net/minecraft/network/CompressionDecoder.java + CompressionEncoder
 * .java).
 *
 * <p><b>The wire form</b>: once the login Set Compression exchange has
 * landed (S2C login 0x03, sent before Login Success —
 * ServerLoginNetworkHandler.acceptLogin lines 91-101), every frame
 * carries a second varint INSIDE the length framing: the <i>data
 * length</i> — the payload's size before compression. A zero data-length
 * means the payload that follows is raw (the frame was below the
 * threshold); any other value means the payload is zlib-deflated and
 * inflates to exactly that many bytes. The decoder's own two guards are
 * the reference's verbatim law: a data length below the threshold is a
 * {@code Badly compressed packet} defect (the sender compressed what it
 * should not have), and a data length above 2097152 is the same defect
 * at the protocol's ceiling.</p>
 *
 * <p><b>Pipeline placement</b> (the reference Connection.setCompression
 * Threshold lines 325-346 — decompress before the packet decoder, compress
 * before the packet encoder, over Torch's frame codec): the decoder sits
 * immediately after the length-framing decoder (inbound: frame -&gt;
 * decompress -&gt; the connection's packet walk); the encoder sits
 * immediately after the length-framing encoder in the pipeline list, so
 * the outbound walk (tail to head) visits it BEFORE the length prefix is
 * applied — the packet hook keeps its plaintext view, the frame encoder
 * frames the compressed bytes. One instance pair per pipeline; the
 * handlers are installed once, at the exchange, and never re-thresholded
 * (the reference's setThreshold arm exists for its own re-config path;
 * the login exchange installs a fresh pair, the shape carried here).</p>
 *
 * <p><b>The deflater's own economy</b> (the reference encoder's exact
 * shape): one Deflater per encoder, an 8KB transfer buffer, setInput +
 * finish + the drain loop + reset — the deflater is reused across frames,
 * never closed per frame. The threshold comparison uses the payload's
 * UNCOMPRESSED size ({@code i < this.threshold} — a payload exactly at
 * the threshold compresses; the reference's own comparison).</p>
 */
final class CompressionCodec {

    static final String DECODER_NAME = "decompress";
    static final String ENCODER_NAME = "compress";

    /** The protocol's decompressed-size ceiling (the reference decoder's own constant). */
    static final int MAX_DECOMPRESSED_SIZE = 2_097_152;

    private CompressionCodec() {
    }

    /**
     * The inbound half: strips the data-length varint, inflates the
     * payload when it was compressed, and enforces the reference's two
     * badly-compressed guards. An empty buffer produces nothing (the
     * reference's own {@code readableBytes() != 0} gate — a zero-length
     * frame is not a compression defect).
     */
    static final class Decoder extends ByteToMessageDecoder {

        private final Inflater inflater = new Inflater();
        private final int threshold;

        Decoder(int threshold) {
            this.threshold = threshold;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (in.readableBytes() != 0) {
                int dataLength = ByteBufOps.readVarInt(in);
                if (dataLength == 0) {
                    out.add(in.readBytes(in.readableBytes()));
                } else {
                    if (dataLength < threshold) {
                        throw new DecoderException("Badly compressed packet - size of " + dataLength
                                + " is below server threshold of " + threshold);
                    }

                    if (dataLength > MAX_DECOMPRESSED_SIZE) {
                        throw new DecoderException("Badly compressed packet - size of " + dataLength
                                + " is larger than protocol maximum of " + MAX_DECOMPRESSED_SIZE);
                    }

                    byte[] compressed = new byte[in.readableBytes()];
                    in.readBytes(compressed);
                    inflater.setInput(compressed);
                    byte[] inflated = new byte[dataLength];
                    inflater.inflate(inflated);
                    out.add(Unpooled.wrappedBuffer(inflated));
                    inflater.reset();
                }
            }
        }
    }

    /**
     * The outbound half: prefixes the data-length varint (zero for a raw
     * ride, the pre-inflation size for a deflated one) and deflates with
     * the reused Deflater when the payload reaches the threshold.
     */
    static final class Encoder extends MessageToByteEncoder<ByteBuf> {

        private final byte[] buffer = new byte[8192];
        private final Deflater deflator = new Deflater();
        private final int threshold;

        Encoder(int threshold) {
            this.threshold = threshold;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
            int size = msg.readableBytes();
            if (size < threshold) {
                ByteBufOps.writeVarInt(out, 0);
                out.writeBytes(msg);
            } else {
                byte[] data = new byte[size];
                msg.readBytes(data);
                ByteBufOps.writeVarInt(out, data.length);
                this.deflator.setInput(data, 0, size);
                this.deflator.finish();

                while (!this.deflator.finished()) {
                    int written = this.deflator.deflate(this.buffer);
                    out.writeBytes(this.buffer, 0, written);
                }

                this.deflator.reset();
            }
        }
    }
}
