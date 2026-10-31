package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave ON-1b's crypto unit pins — the reference EncryptionUtils laws,
 * driven with fixed inputs against the JDK's own primitives: the RSA
 * round-trip (the reference generateKeyPair/encrypt/decrypt), the
 * server-id digest's exact concatenation order, the BigInteger hex
 * spelling (the hasJoined server-id form), and the AES/CFB8 stream
 * cipher's byte-exact round-trip.
 */
class TorchEncryptionTest {

    @Test
    void theKeyPairIsTheReferencesOwnRsaShape() {
        KeyPair pair = TorchEncryption.generateKeyPair();
        assertEquals("RSA", pair.getPublic().getAlgorithm());
        assertEquals(1024, pair.getPublic().getEncoded().length * 8
                - (pair.getPublic().getEncoded().length * 8 - bitLengthOf(pair)));
        assertTrue(pair.getPublic().getEncoded().length > 100,
                "a 1024-bit RSA public key's DER form is the ~140-byte X509 shape");
        // A second pair differs (per-boot generation, never a constant key).
        assertFalse(Arrays.equals(pair.getPublic().getEncoded(),
                TorchEncryption.generateKeyPair().getPublic().getEncoded()));
    }

    private static int bitLengthOf(KeyPair pair) {
        return ((java.security.interfaces.RSAPublicKey) pair.getPublic()).getModulus().bitLength();
    }

    @Test
    void theRsaRoundTripCarriesTheSecretAndTheNonce() {
        KeyPair pair = TorchEncryption.generateKeyPair();
        byte[] secret = new byte[16];
        new java.util.Random(42L).nextBytes(secret);
        byte[] nonce = {0x0A, 0x0B, 0x0C, 0x0D};
        // The client's arm: both blocks encrypted with the PUBLIC key.
        byte[] encryptedSecret = TorchEncryption.encrypt(pair.getPublic(), secret);
        byte[] encryptedNonce = TorchEncryption.encrypt(pair.getPublic(), nonce);
        assertEquals(128, encryptedSecret.length,
                "a 1024-bit RSA block is 128 bytes (the wire's own PKCS#1 form)");
        assertEquals(128, encryptedNonce.length);
        assertFalse(Arrays.equals(secret, encryptedSecret));
        // The server's arm: decrypted with the PRIVATE key.
        assertArrayEquals(secret, TorchEncryption.decrypt(pair.getPrivate(), encryptedSecret));
        assertArrayEquals(nonce, TorchEncryption.decrypt(pair.getPrivate(), encryptedNonce));
    }

    @Test
    void theDigestIsTheExactConcatenationOrder() throws Exception {
        KeyPair pair = TorchEncryption.generateKeyPair();
        SecretKey secret = TestClient18.clientSecret();
        // The independent walk: the JDK's own SHA-1 over the same three
        // parts in the reference's order (server-id ISO-8859-1, secret
        // raw, public DER).
        MessageDigest expected = MessageDigest.getInstance("SHA-1");
        expected.update("".getBytes(java.nio.charset.Charset.forName("ISO_8859_1")));
        expected.update(secret.getEncoded());
        expected.update(pair.getPublic().getEncoded());
        assertArrayEquals(expected.digest(),
                TorchEncryption.digest("", pair.getPublic(), secret));
        // The order matters: swapping the secret and the key answers a
        // different digest (the pin the concatenation order deserves).
        MessageDigest swapped = MessageDigest.getInstance("SHA-1");
        swapped.update("".getBytes(java.nio.charset.Charset.forName("ISO_8859_1")));
        swapped.update(pair.getPublic().getEncoded());
        swapped.update(secret.getEncoded());
        assertFalse(Arrays.equals(swapped.digest(),
                TorchEncryption.digest("", pair.getPublic(), secret)));
    }

    @Test
    void theServerIdHashIsTheBigIntegerHexSpelling() {
        KeyPair pair = TorchEncryption.generateKeyPair();
        SecretKey secret = TestClient18.clientSecret();
        String hash = TorchEncryption.serverIdHash("", pair.getPublic(), secret);
        // SHA-1's 20 bytes: 39-40 hex chars (a leading zero nibble
        // shortens the unsigned magnitude), and the sign rides as the
        // reference's own leading '-' when the digest's top bit is set.
        assertTrue(hash.matches("-?[0-9a-f]{1,40}"),
                "the hash is the BigInteger-signed hex form: " + hash);
        // The same inputs always answer the same hash (deterministic law).
        assertEquals(hash, TorchEncryption.serverIdHash("", pair.getPublic(), secret));
        // And it IS the BigInteger spelling of the digest.
        assertEquals(new BigInteger(TorchEncryption.digest("", pair.getPublic(), secret)).toString(16),
                hash);
        // A negative-digest case exists in the family: hunt one seed and
        // pin the reference's own spelling law (ServerLoginNetworkHandler
        // line 155 + ClientLoginNetworkHandler line 47, both the bare
        // BigInteger.toString(16)): '-' rides the top bit, and the
        // magnitude is NEVER padded — a digest whose top magnitude bytes
        // are small legitimately spells shorter than 40 chars (run 571's
        // 38-char member; both wire sides carry the same variable-length
        // form, which is why the hasJoined serverId row is unpadded by
        // design). The false "full 40-char magnitude" claim was the test's
        // own invention against the reference.
        boolean foundNegative = false;
        for (int seed = 0; seed < 200 && !foundNegative; seed++) {
            final long chosenSeed = seed;
            javax.crypto.KeyGenerator generator;
            try {
                generator = javax.crypto.KeyGenerator.getInstance("AES");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            generator.init(128, new java.security.SecureRandom() {
                @Override
                public void nextBytes(byte[] bytes) {
                    new java.util.Random(chosenSeed).nextBytes(bytes);
                }
            });
            SecretKey candidate = generator.generateKey();
            String candidateHash = TorchEncryption.serverIdHash("", pair.getPublic(), candidate);
            if (candidateHash.startsWith("-")) {
                foundNegative = true;
                assertTrue(candidateHash.length() - 1 <= 40,
                        "a negative digest's magnitude never exceeds SHA-1's 20 bytes: "
                                + candidateHash);
                assertFalse(candidateHash.startsWith("-0"),
                        "the reference never zero-pads the magnitude: " + candidateHash);
            }
        }
        assertTrue(foundNegative, "the hunt found a negative digest within 200 seeds");
    }

    @Test
    void theStreamCipherRoundTripsByteExact() throws Exception {
        SecretKey secret = TestClient18.clientSecret();
        Cipher encrypt = TorchEncryption.streamCipher(1, secret);
        Cipher decrypt = TorchEncryption.streamCipher(2, secret);
        byte[] plain = new byte[1000];
        new java.util.Random(7L).nextBytes(plain);
        byte[] cipherText = encrypt.update(plain);
        assertArrayEquals(plain, decrypt.update(cipherText));
    }

    @Test
    void theStreamCipherIsAStreamAcrossFragmentedUpdates() throws Exception {
        // CFB8's own law: the cipher transforms whatever arrived, the
        // feedback state carries across update() calls — a frame split at
        // odd boundaries reassembles whole. This is the property the
        // EncryptionCodec rides over TCP's arbitrary segmentation.
        SecretKey secret = TestClient18.clientSecret();
        Cipher encrypt = TorchEncryption.streamCipher(1, secret);
        Cipher decrypt = TorchEncryption.streamCipher(2, secret);
        byte[] plain = new byte[997]; // a prime — no alignment luck
        new java.util.Random(9L).nextBytes(plain);
        java.io.ByteArrayOutputStream cipherStream = new java.io.ByteArrayOutputStream();
        int[] cuts = {0, 1, 2, 3, 7, 61, 500, 997};
        for (int i = 0; i + 1 < cuts.length; i++) {
            int from = cuts[i];
            int to = cuts[i + 1];
            if (to > from) {
                cipherStream.write(encrypt.update(Arrays.copyOfRange(plain, from, to)));
            }
        }
        byte[] decrypted = decrypt.update(cipherStream.toByteArray());
        assertArrayEquals(plain, decrypted);
    }

    @Test
    void thePublicKeyReconstitutionRoundTrips() {
        KeyPair pair = TorchEncryption.generateKeyPair();
        PublicKey reconstituted = TorchEncryption.publicKey(pair.getPublic().getEncoded());
        assertEquals(pair.getPublic(), reconstituted,
                "the X509 DER round-trip answers the same key");
        assertThrows(IllegalArgumentException.class,
                () -> TorchEncryption.publicKey(new byte[]{1, 2, 3}));
    }

    @Test
    void theNonceComparisonIsTheArraysLaw() {
        byte[] nonce = {1, 2, 3, 4};
        assertTrue(TorchEncryption.nonceMatches(nonce, new byte[]{1, 2, 3, 4}));
        assertFalse(TorchEncryption.nonceMatches(nonce, new byte[]{1, 2, 3, 5}));
        assertFalse(TorchEncryption.nonceMatches(nonce, new byte[]{1, 2, 3}));
        assertFalse(TorchEncryption.nonceMatches(nonce, new byte[]{}));
    }
}
