package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave ON-1b's stream-codec pins — the AES/CFB8 pair over the netty
 * pipeline: the encryptor's output is the decryptor's input across
 * arbitrary frame sizes, and the pipeline ORDER rides the reference's
 * own law (the encryptor sees the framed bytes — the length prefix is
 * INSIDE the ciphertext, the form a real client's mirror expects).
 */
class EncryptionCodecTest {

    @Test
    void thePairRoundTripsArbitrarySizes() throws Exception {
        // CFB8 is ONE CONTINUOUS stream per cipher instance: each size gets
        // its own fresh encoder/decoder pair (a fresh stream), because a
        // decoder that starts mid-stream answers garbage — the law the
        // fragmentation test below pins from the other side.
        int[] sizes = {1, 2, 15, 16, 17, 255, 256, 4096, 20_000};
        for (int size : sizes) {
            SecretKey secret = TestClient18.clientSecret();
            EmbeddedChannel encoder = new EmbeddedChannel(
                    new EncryptionCodec.Encoder(TorchEncryption.streamCipher(1, secret)));
            EmbeddedChannel decoder = new EmbeddedChannel(
                    new EncryptionCodec.Decoder(TorchEncryption.streamCipher(2, secret)));
            byte[] payload = new byte[size];
            new java.util.Random(size).nextBytes(payload);
            assertTrue(encoder.writeOutbound(Unpooled.wrappedBuffer(payload).copy()));
            ByteBuf cipherText = encoder.readOutbound();
            assertTrue(decoder.writeInbound(cipherText));
            ByteBuf decoded = decoder.readInbound();
            assertEquals(size, decoded.readableBytes(), "size " + size + " round-trips whole");
            byte[] back = new byte[size];
            decoded.readBytes(back);
            assertArrayEquals(payload, back, "size " + size + " round-trips byte-exact");
            decoded.release();
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    @Test
    void theCipherStateCarriesAcrossFragments() throws Exception {
        // One encoder, one decoder, a message split into odd chunks —
        // the CFB8 feedback state must carry across update() boundaries
        // exactly as it does across TCP segments.
        SecretKey secret = TestClient18.clientSecret();
        Cipher encrypt = TorchEncryption.streamCipher(1, secret);
        Cipher decrypt = TorchEncryption.streamCipher(2, secret);
        EmbeddedChannel encoder = new EmbeddedChannel(new EncryptionCodec.Encoder(encrypt));
        EmbeddedChannel decoder = new EmbeddedChannel(new EncryptionCodec.Decoder(decrypt));

        byte[] first = new byte[101];
        byte[] second = new byte[53];
        new java.util.Random(3L).nextBytes(first);
        new java.util.Random(4L).nextBytes(second);
        assertTrue(encoder.writeOutbound(Unpooled.wrappedBuffer(first).copy()));
        assertTrue(encoder.writeOutbound(Unpooled.wrappedBuffer(second).copy()));
        ByteBuf firstCipher = encoder.readOutbound();
        ByteBuf secondCipher = encoder.readOutbound();

        // The decryptor receives the FIRST ciphertext split at its own
        // odd boundary (a 37-byte read then the rest) — the state law:
        // collect BOTH partial outputs and concatenate.
        byte[] firstCipherBytes = new byte[firstCipher.readableBytes()];
        firstCipher.readBytes(firstCipherBytes);
        firstCipher.release();
        ByteBuf partA = Unpooled.wrappedBuffer(firstCipherBytes, 0, 37);
        ByteBuf partB = Unpooled.wrappedBuffer(firstCipherBytes, 37,
                firstCipherBytes.length - 37);
        assertTrue(decoder.writeInbound(partA));
        assertTrue(decoder.writeInbound(partB));
        java.io.ByteArrayOutputStream reassembled = new java.io.ByteArrayOutputStream();
        ByteBuf partial;
        while ((partial = decoder.readInbound()) != null) {
            byte[] chunk = new byte[partial.readableBytes()];
            partial.readBytes(chunk);
            partial.release();
            reassembled.write(chunk);
        }
        assertArrayEquals(first, reassembled.toByteArray());

        assertTrue(decoder.writeInbound(secondCipher));
        ByteBuf decodedSecond = decoder.readInbound();
        byte[] secondBack = new byte[decodedSecond.readableBytes()];
        decodedSecond.readBytes(secondBack);
        assertArrayEquals(second, secondBack);

        encoder.finishAndReleaseAll();
        decoder.finishAndReleaseAll();
    }

    @Test
    void theEncryptedFormIsNotThePlaintext() throws Exception {
        SecretKey secret = TestClient18.clientSecret();
        EmbeddedChannel encoder = new EmbeddedChannel(
                new EncryptionCodec.Encoder(TorchEncryption.streamCipher(1, secret)));
        byte[] payload = new byte[512];
        new java.util.Random(11L).nextBytes(payload);
        assertTrue(encoder.writeOutbound(Unpooled.wrappedBuffer(payload).copy()));
        ByteBuf cipherText = encoder.readOutbound();
        assertEquals(512, cipherText.readableBytes(),
                "CFB8 preserves the length exactly (a stream mode, no padding)");
        byte[] cipher = new byte[512];
        cipherText.readBytes(cipher);
        boolean differs = false;
        for (int i = 0; i < 512; i++) {
            if (cipher[i] != payload[i]) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "the ciphertext is not the plaintext");
        encoder.finishAndReleaseAll();
    }
}
