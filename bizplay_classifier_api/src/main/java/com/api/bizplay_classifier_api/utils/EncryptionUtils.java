package com.api.bizplay_classifier_api.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EncryptionUtils {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int NONCE_LENGTH_BYTES = 12;

    private final String base64Key;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionUtils(@Value("${app.encryption.base64-key:}") String base64Key) {
        this.base64Key = base64Key;
    }

    public String encrypt(String value) {
        try {
            SecretKeySpec secretKey = createSecretKey(base64Key);
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));

            byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] combined = new byte[nonce.length + ciphertext.length];

            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt value", ex);
        }
    }

    public String decrypt(String encrypted) {
        try {
            SecretKeySpec secretKey = createSecretKey(base64Key);
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= NONCE_LENGTH_BYTES) {
                throw new IllegalArgumentException("Encrypted value is invalid");
            }

            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - NONCE_LENGTH_BYTES];

            System.arraycopy(combined, 0, nonce, 0, NONCE_LENGTH_BYTES);
            System.arraycopy(combined, NONCE_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt value", ex);
        }
    }

    private static SecretKeySpec createSecretKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("Property 'app.encryption.base64-key' must be configured");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Property 'app.encryption.base64-key' must be valid Base64", ex);
        }

        int keyLength = keyBytes.length;
        if (keyLength != 16 && keyLength != 24 && keyLength != 32) {
            throw new IllegalStateException("AES key must be 16, 24, or 32 bytes after Base64 decoding");
        }

        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
