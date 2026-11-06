package com.lawfirm.law.firm.storage;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Recusa a subida, fora de dev/test, com a chave de desenvolvimento dos arquivos de cliente.
 *
 * <p>Ela está no {@code application.yaml}, em texto, para o ambiente local funcionar sem
 * configuração. Em qualquer outro lugar, cifrar com ela é o mesmo que não cifrar: quem lê o
 * repositório decifra os documentos. Mesmo espírito do {@link StorageTypeGuard}.
 */
@Component
class FileEncryptionKeyGuard {

    /** A mesma do default de {@code app.storage.encryption.key} no {@code application.yaml}. */
    static final String DEV_KEY = "ZGV2LW9ubHktZmlsZS1rZXktTk9ULUZPUi1QUk9EISE=";

    // Mesmos perfis que CorsConfig e StorageTypeGuard tratam como locais.
    private static final List<String> PERFIS_LOCAIS = List.of("dev", "test");

    FileEncryptionKeyGuard(@Value("${app.storage.encryption.key:}") String key, Environment env) {
        boolean local = Arrays.stream(env.getActiveProfiles()).anyMatch(PERFIS_LOCAIS::contains);
        if (!local && (key.isBlank() || DEV_KEY.equals(key.trim()))) {
            throw new IllegalStateException(
                    "Recusando subir sem APP_FILE_ENCRYPTION_KEY própria fora dos perfis dev/test:"
                            + " os documentos dos clientes seriam cifrados com a chave que está no"
                            + " repositório. Gere uma com openssl rand -base64 32.");
        }
    }
}
