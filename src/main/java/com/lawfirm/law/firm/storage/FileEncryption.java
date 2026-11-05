package com.lawfirm.law.firm.storage;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra o conteúdo dos arquivos de cliente antes de ele chegar ao storage (AES-256-GCM).
 *
 * <p><b>Envelope.</b> Cada arquivo tem uma chave própria, sorteada na gravação; só ela cifra o
 * conteúdo. A chave-mestra ({@code APP_FILE_ENCRYPTION_KEY}) cifra apenas essas chaves, que viajam
 * no cabeçalho do próprio arquivo. Assim nenhuma coluna nova é necessária, e trocar a chave-mestra
 * no futuro é recifrar 48 bytes por arquivo, não o documento inteiro.
 *
 * <p><b>Formato:</b> {@code "TMFE"} | versão da chave-mestra (1 byte) | IV da chave do arquivo (12)
 * | chave do arquivo cifrada (48) | IV do conteúdo (12) | conteúdo cifrado com a tag. O cabeçalho
 * entra como dado autenticado do conteúdo: trocar a chave de um arquivo pela de outro, ou mexer na
 * versão, faz a decifragem falhar em vez de devolver lixo.
 *
 * <p><b>Falha fecha.</b> Ao contrário do {@code CryptoConverter}, que devolve o valor gravado
 * quando não consegue decifrar, aqui qualquer falha é erro: entregar bytes cifrados como se fossem
 * o documento esconderia uma chave errada até alguém abrir o PDF.
 */
@Component
public class FileEncryption {

    private static final byte[] MAGIC = {'T', 'M', 'F', 'E'};
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final int WRAPPED_KEY_BYTES = KEY_BYTES + TAG_BITS / Byte.SIZE;
    private static final int PREFIX_BYTES = MAGIC.length + 1;
    private static final int HEADER_BYTES = PREFIX_BYTES + IV_BYTES + WRAPPED_KEY_BYTES;
    private static final int MIN_ENCRYPTED_BYTES = HEADER_BYTES + IV_BYTES + TAG_BITS / Byte.SIZE;
    private static final int MAX_KEY_VERSION = 255;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey masterKey;
    private final int keyVersion;

    public FileEncryption(
            @Value("${app.storage.encryption.key}") String base64Key,
            @Value("${app.storage.encryption.key-version:1}") int keyVersion) {
        this.masterKey = new SecretKeySpec(decodeKey(base64Key), "AES");
        if (keyVersion < 1 || keyVersion > MAX_KEY_VERSION) {
            throw new IllegalStateException(
                    "APP_FILE_ENCRYPTION_KEY_VERSION deve estar entre 1 e "
                            + MAX_KEY_VERSION
                            + ", veio "
                            + keyVersion);
        }
        this.keyVersion = keyVersion;
    }

    /** Diz se o conteúdo gravado está no formato desta classe. */
    public static boolean isEncrypted(byte[] stored) {
        return stored.length >= MAGIC.length
                && Arrays.equals(stored, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] fileKey = randomBytes(KEY_BYTES);
            byte[] prefix =
                    ByteBuffer.allocate(PREFIX_BYTES).put(MAGIC).put((byte) keyVersion).array();
            byte[] keyIv = randomBytes(IV_BYTES);
            byte[] wrappedKey =
                    cipher(Cipher.ENCRYPT_MODE, masterKey, keyIv, prefix).doFinal(fileKey);
            byte[] header =
                    ByteBuffer.allocate(HEADER_BYTES)
                            .put(prefix)
                            .put(keyIv)
                            .put(wrappedKey)
                            .array();

            byte[] contentIv = randomBytes(IV_BYTES);
            byte[] content =
                    cipher(
                                    Cipher.ENCRYPT_MODE,
                                    new SecretKeySpec(fileKey, "AES"),
                                    contentIv,
                                    header)
                            .doFinal(plain);

            return ByteBuffer.allocate(header.length + IV_BYTES + content.length)
                    .put(header)
                    .put(contentIv)
                    .put(content)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar arquivo", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        if (!isEncrypted(stored) || stored.length < MIN_ENCRYPTED_BYTES) {
            throw new FileStorageException("Arquivo não está no formato cifrado esperado");
        }
        int storedVersion = Byte.toUnsignedInt(stored[MAGIC.length]);
        if (storedVersion != keyVersion) {
            throw new FileStorageException(
                    "Arquivo cifrado com a versão "
                            + storedVersion
                            + " da chave, e a instância tem a versão "
                            + keyVersion);
        }

        ByteBuffer buffer = ByteBuffer.wrap(stored);
        byte[] prefix = take(buffer, PREFIX_BYTES);
        byte[] keyIv = take(buffer, IV_BYTES);
        byte[] wrappedKey = take(buffer, WRAPPED_KEY_BYTES);
        byte[] header = Arrays.copyOf(stored, HEADER_BYTES);
        byte[] contentIv = take(buffer, IV_BYTES);
        byte[] content = take(buffer, buffer.remaining());

        try {
            byte[] fileKey =
                    cipher(Cipher.DECRYPT_MODE, masterKey, keyIv, prefix).doFinal(wrappedKey);
            return cipher(Cipher.DECRYPT_MODE, new SecretKeySpec(fileKey, "AES"), contentIv, header)
                    .doFinal(content);
        } catch (AEADBadTagException e) {
            throw new FileStorageException(
                    "Não foi possível decifrar o arquivo: conteúdo alterado ou chave errada", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar arquivo", e);
        }
    }

    private static Cipher cipher(int mode, SecretKey key, byte[] iv, byte[] associatedData)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(associatedData);
        return cipher;
    }

    private static byte[] decodeKey(String base64Key) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_FILE_ENCRYPTION_KEY não é base64 válido", e);
        }
        if (key.length != KEY_BYTES) {
            // AES-256 e nada menos: documento de cliente não tem por que ficar com chave mais
            // curta.
            throw new IllegalStateException(
                    "APP_FILE_ENCRYPTION_KEY deve ter 32 bytes (openssl rand -base64 32), veio "
                            + key.length);
        }
        return key;
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] take(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }
}
