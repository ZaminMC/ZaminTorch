package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.util.List;

/**
 * The login stream-cipher codec (Wave ON-1b) — the reference's
 * PacketDecryptor/PacketEncryptor pair over the shared
 * PacketEncryptionManager economy, ported 1:1 (reference/1.8.8/net/
 * minecraft/network/encryption/*). AES/CFB8/NoPadding is a BYTE-STREAM
 * mode: every {@link Cipher#update} over whatever arrived transforms
 * exactly those bytes and the cipher's feedback state carries across
 * buffers — no block alignment, no padding, no buffering. Each handler
 * owns its cipher (one encrypting, one decrypting, the same key's bytes
 * as both sides' IVs — the reference's own crypt shape).
 *
 * <p><b>Pipeline placement</b> (the reference Connection.setupEncryption
 * lines 295-299: decrypt before the splitter, encrypt before the
 * prepender): the decryptor sits BEFORE the length-framing decoder — it
 * transforms the raw TCP stream, length prefixes included; the encryptor
 * sits before the length-framing encoder in the pipeline list, so the
 * outbound walk (tail to head) visits it AFTER the length prefix is
 * applied — the ciphertext IS the framed bytes, exactly the form a
 * 1.8.8 client's mirror pipeline expects. The compression stage (when
 * the exchange lands later in the same login) rides INSIDE: payload
 * compressed, framed, then the whole frame encrypted.</p>
 *
 * <p><b>The conversion-buffer economy</b> (the reference manager's own
 * shape): one grow-only byte[] per handler for the heap round-trip, the
 * cipher's {@code getOutputSize} sizing the output — CFB8's output size
 * always equals the input size, so the buffers stabilize at the
 * connection's frame size.</p>
 */
final class EncryptionCodec {

    static final String DECRYPT_NAME = "decrypt";
    static final String ENCRYPT_NAME = "encrypt";

    private EncryptionCodec() {
    }

    /** The inbound half: the raw stream bytes in, the plaintext stream bytes out. */
    static final class Decoder extends MessageToMessageDecoder<ByteBuf> {

        private final Cipher cipher;
        private byte[] conversionBuffer = new byte[0];

        Decoder(Cipher cipher) {
            this.cipher = cipher;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
                throws ShortBufferException {
            int size = in.readableBytes();
            byte[] source = toByteArray(in, size);
            ByteBuf decoded = ctx.alloc().heapBuffer(cipher.getOutputSize(size));
            decoded.writerIndex(cipher.update(source, 0, size, decoded.array(),
                    decoded.arrayOffset()));
            out.add(decoded);
        }

        private byte[] toByteArray(ByteBuf buffer, int size) {
            if (conversionBuffer.length < size) {
                conversionBuffer = new byte[size];
            }
            buffer.readBytes(conversionBuffer, 0, size);
            return conversionBuffer;
        }
    }

    /** The outbound half: the framed bytes in, the ciphertext stream bytes out. */
    static final class Encoder extends MessageToByteEncoder<ByteBuf> {

        private final Cipher cipher;
        private byte[] conversionBuffer = new byte[0];
        private byte[] encryptionBuffer = new byte[0];

        Encoder(Cipher cipher) {
            this.cipher = cipher;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out)
                throws ShortBufferException {
            int size = in.readableBytes();
            byte[] source = toByteArray(in, size);
            int outputSize = cipher.getOutputSize(size);
            if (encryptionBuffer.length < outputSize) {
                encryptionBuffer = new byte[outputSize];
            }
            out.writeBytes(encryptionBuffer, 0,
                    cipher.update(source, 0, size, encryptionBuffer));
        }

        private byte[] toByteArray(ByteBuf buffer, int size) {
            if (conversionBuffer.length < size) {
                conversionBuffer = new byte[size];
            }
            buffer.readBytes(conversionBuffer, 0, size);
            return conversionBuffer;
        }
    }
}
