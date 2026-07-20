package com.lawfirm.law.firm.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA converter that transparently encrypts/decrypts a String column at rest using AES-256-GCM.
 *
 * The encryption key is read from the {@code APP_ENCRYPTION_KEY} environment variable
 * (base64-encoded, 32 bytes / 256 bits). A random IV is generated per value and stored
 * alongside the ciphertext (IV + ciphertext, base64-encoded) so no separate column is needed.
 *
 * This is applied explicitly (not autoApply) only to fields that hold sensitive data
 * (e.g. Client#inssPassword) to avoid silently encrypting unrelated columns.
 */
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(CryptoConverter.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt attribute", ex);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // Data stored before encryption was introduced, or with a different key, would land here.
            log.warn("Could not decrypt value - returning raw stored value as fallback: {}", ex.getMessage());
            return dbData;
        }
    }

    private SecretKeySpec secretKey() {
        String base64Key = System.getenv("APP_ENCRYPTION_KEY");
        if (base64Key == null || base64Key.isBlank()) {
            // Dev-only fallback so local runs don't break without extra setup.
            // MUST be overridden via APP_ENCRYPTION_KEY in every real environment.
            log.warn("APP_ENCRYPTION_KEY not set - using an insecure development-only key. " +
                    "Set APP_ENCRYPTION_KEY (base64, 32 bytes) before deploying.");
            base64Key = "ZGV2LW9ubHktaW5zZWN1cmUtMzItYnl0ZS1rZXkhIQ==";
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
