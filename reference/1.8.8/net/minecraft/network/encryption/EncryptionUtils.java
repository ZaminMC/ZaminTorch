package net.minecraft.network.encryption;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EncryptionUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    public static SecretKey generateSecretKey() {
        try {
            KeyGenerator keygenerator = KeyGenerator.getInstance("AES");
            keygenerator.init(128);
            return keygenerator.generateKey();
        } catch (NoSuchAlgorithmException nosuchalgorithmexception) {
            throw new Error(nosuchalgorithmexception);
        }
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keypairgenerator = KeyPairGenerator.getInstance("RSA");
            keypairgenerator.initialize(1024);
            return keypairgenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException nosuchalgorithmexception) {
            nosuchalgorithmexception.printStackTrace();
            LOGGER.error("Key pair generation failed!");
            return null;
        }
    }

    public static byte[] digest(String data, PublicKey publicKey, SecretKey secretKey) {
        try {
            return digest("SHA-1", data.getBytes("ISO_8859_1"), secretKey.getEncoded(), publicKey.getEncoded());
        } catch (UnsupportedEncodingException unsupportedencodingexception) {
            unsupportedencodingexception.printStackTrace();
            return null;
        }
    }

    private static byte[] digest(String algorithm, byte[]... data) {
        try {
            MessageDigest messagedigest = MessageDigest.getInstance(algorithm);

            for (byte[] abyte : data) {
                messagedigest.update(abyte);
            }

            return messagedigest.digest();
        } catch (NoSuchAlgorithmException nosuchalgorithmexception) {
            nosuchalgorithmexception.printStackTrace();
            return null;
        }
    }

    public static PublicKey generatePublicKey(byte[] data) {
        try {
            EncodedKeySpec encodedkeyspec = new X509EncodedKeySpec(data);
            KeyFactory keyfactory = KeyFactory.getInstance("RSA");
            return keyfactory.generatePublic(encodedkeyspec);
        } catch (NoSuchAlgorithmException nosuchalgorithmexception) {
        } catch (InvalidKeySpecException invalidkeyspecexception) {
        }

        LOGGER.error("Public key reconstitute failed!");
        return null;
    }

    public static SecretKey decryptSecretKey(PrivateKey privateKey, byte[] data) {
        return new SecretKeySpec(decrypt(privateKey, data), "AES");
    }

    public static byte[] encrypt(Key key, byte[] data) {
        return cipher(1, key, data);
    }

    public static byte[] decrypt(Key key, byte[] data) {
        return cipher(2, key, data);
    }

    private static byte[] cipher(int opmode, Key key, byte[] data) {
        try {
            return setupCipher(opmode, key.getAlgorithm(), key).doFinal(data);
        } catch (IllegalBlockSizeException illegalblocksizeexception) {
            illegalblocksizeexception.printStackTrace();
        } catch (BadPaddingException badpaddingexception) {
            badpaddingexception.printStackTrace();
        }

        LOGGER.error("Cipher data failed!");
        return null;
    }

    private static Cipher setupCipher(int opmode, String transformation, Key key) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(opmode, key);
            return cipher;
        } catch (InvalidKeyException invalidkeyexception) {
            invalidkeyexception.printStackTrace();
        } catch (NoSuchAlgorithmException nosuchalgorithmexception) {
            nosuchalgorithmexception.printStackTrace();
        } catch (NoSuchPaddingException nosuchpaddingexception) {
            nosuchpaddingexception.printStackTrace();
        }

        LOGGER.error("Cipher creation failed!");
        return null;
    }

    public static Cipher crypt(int opMode, Key key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(opMode, key, new IvParameterSpec(key.getEncoded()));
            return cipher;
        } catch (GeneralSecurityException generalsecurityexception) {
            throw new RuntimeException(generalsecurityexception);
        }
    }
}
