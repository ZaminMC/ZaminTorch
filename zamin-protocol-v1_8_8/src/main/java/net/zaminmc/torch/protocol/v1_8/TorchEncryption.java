package net.zaminmc.torch.protocol.v1_8;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The login crypto family (Wave ON-1b) — the reference
 * net.minecraft.network.encryption.EncryptionUtils ported 1:1: the
 * per-boot RSA keypair (1024, the reference's own size —
 * generateKeyPair lines 41-51), the SHA-1 server-id digest (digest lines
 * 53-60: the server-id's ISO-8859-1 bytes, the secret's raw bytes, the
 * public key's DER — in that order), the BigInteger-signed hex spelling
 * the hasJoined consult carries (the reference's own
 * {@code new BigInteger(digest).toString(16)} — ServerLoginNetworkHandler
 * lines 155-162, whose leading '-' on negative digests is the wire's own
 * historical shape), the RSA encrypt/decrypt over the JDK's default
 * "RSA" transformation (PKCS#1 — the same the 1.8.8 client's JCE
 * resolves), and the AES/CFB8/NoPadding stream cipher the connection
 * layer runs (crypt lines 132-140: the key's own bytes as the IV — the
 * reference's exact shape).
 */
final class TorchEncryption {

    private TorchEncryption() {
    }

    /** The reference's own keypair size (generateKeyPair line 44). */
    static final int RSA_KEY_BITS = 1024;

    /** The ISO-8859-1 the server-id digest reads (digest line 55). */
    private static final Charset SERVER_ID_CHARSET = Charset.forName("ISO_8859_1");

    /**
     * Generates the per-boot RSA keypair — the reference's own
     * {@code KeyPairGenerator("RSA")}, initialized at 1024 bits. Never
     * returns null here (the reference's warn-and-null arm exists for a
     * JVM without RSA, which the toolchain requires at boot anyway — a
     * missing RSA fails the wave loudly instead of disabling online mode
     * silently).
     */
    static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA keypair generation unavailable", e);
        }
    }

    /**
     * The server-id digest (the reference digest lines 53-60): SHA-1 over
     * the server-id's ISO-8859-1 bytes, the shared secret's raw bytes, and
     * the public key's encoded (DER) bytes — in exactly that order.
     */
    static byte[] digest(String serverId, PublicKey publicKey, SecretKey secretKey) {
        return sha1(serverId.getBytes(SERVER_ID_CHARSET), secretKey.getEncoded(),
                publicKey.getEncoded());
    }

    private static byte[] sha1(byte[]... data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (byte[] part : data) {
                digest.update(part);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    /**
     * The hasJoined server-id spelling: the digest as a BigInteger-signed
     * hex string — the reference's own
     * {@code new BigInteger(...).toString(16)}, whose leading '-' on
     * negative digests both sides of the wire carry identically.
     */
    static String serverIdHash(String serverId, PublicKey publicKey, SecretKey secretKey) {
        return new BigInteger(digest(serverId, publicKey, secretKey)).toString(16);
    }

    /** The RSA encrypt (the reference encrypt lines 94-96 — the JDK's "RSA"). */
    static byte[] encrypt(Key key, byte[] data) {
        return rsa(Cipher.ENCRYPT_MODE, key, data);
    }

    /** The RSA decrypt (the reference decrypt lines 98-100). */
    static byte[] decrypt(Key key, byte[] data) {
        return rsa(Cipher.DECRYPT_MODE, key, data);
    }

    private static byte[] rsa(int opmode, Key key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(key.getAlgorithm());
            cipher.init(opmode, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("RSA cipher failed", e);
        }
    }

    /**
     * The shared secret from the RSA reply (the reference
     * decryptSecretKey line 90-92): the decrypted bytes as a raw AES key.
     */
    static SecretKey secretKey(byte[] decrypted) {
        return new SecretKeySpec(decrypted, "AES");
    }

    /**
     * The stream cipher pair's AES/CFB8/NoPadding instance (the reference
     * crypt lines 132-140): the key's own bytes as the IV — the exact
     * shape a 1.8.8 client's JCE builds for the same key.
     */
    static Cipher streamCipher(int opMode, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(opMode, key, new IvParameterSpec(key.getEncoded()));
            return cipher;
        } catch (Exception e) {
            throw new IllegalStateException("AES/CFB8 cipher unavailable", e);
        }
    }

    /**
     * The public key reconstitution (the reference generatePublicKey lines
     * 77-88): the X509 DER bytes the Encryption Request carried, back to
     * a PublicKey — the client-side arm the wire tests drive.
     */
    static PublicKey publicKey(byte[] der) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("public key reconstitution failed", e);
        }
    }

    /**
     * The nonce comparison (the reference handleKey line 142:
     * {@code Arrays.equals(this.nonce, packet.getNonce(privatekey))}).
     */
    static boolean nonceMatches(byte[] expected, byte[] actual) {
        return Arrays.equals(expected, actual);
    }
}
