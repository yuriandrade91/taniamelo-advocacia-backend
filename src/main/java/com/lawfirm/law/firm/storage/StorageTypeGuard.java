package com.lawfirm.law.firm.storage;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Recusa a subida quando os arquivos dos clientes iriam para um lugar errado.
 *
 * <p>O padrão de {@code app.storage.type} é {@code local}: basta a variável não chegar à instância
 * para os documentos irem para o disco da máquina, sem cópia, sem erro e sem log - foi assim que a
 * instância passou meses em disco local. Fora de dev/test isso agora derruba o boot, no mesmo
 * espírito do {@code CorsConfig} com {@code "*"}.
 *
 * <p>{@code s3} sem bucket subia e só falhava no primeiro upload, no meio do expediente. Esse caso
 * é recusado em qualquer perfil, porque nunca é o que se quer.
 */
@Component
class StorageTypeGuard {

    // Mesmos perfis que CorsConfig e SeedPasswords tratam como locais.
    private static final List<String> PERFIS_LOCAIS = List.of("dev", "test");

    StorageTypeGuard(
            @Value("${app.storage.type:local}") String tipo,
            @Value("${app.storage.s3.bucket:}") String bucket,
            Environment env) {
        if ("s3".equals(tipo)) {
            if (bucket.isBlank()) {
                throw new IllegalStateException(
                        "app.storage.type=s3 sem bucket. Defina APP_STORAGE_S3_BUCKET.");
            }
            return;
        }
        boolean local = Arrays.stream(env.getActiveProfiles()).anyMatch(PERFIS_LOCAIS::contains);
        if (!local) {
            throw new IllegalStateException(
                    "Recusando subir com app.storage.type="
                            + tipo
                            + " fora dos perfis dev/test: os arquivos dos clientes ficariam no"
                            + " disco da instância, sem cópia. Defina APP_STORAGE_TYPE=s3 e"
                            + " APP_STORAGE_S3_BUCKET.");
        }
    }
}
