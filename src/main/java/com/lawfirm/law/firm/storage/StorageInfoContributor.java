package com.lawfirm.law.firm.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Publica em {@code /actuator/info} qual backend de arquivos está SERVINDO esta instância.
 *
 * <p>Existe por um buraco de verificação, não de funcionalidade. Subir um arquivo e baixá-lo de
 * volta byte a byte funciona igual com disco local e com S3, e nada na API distingue os dois: o
 * {@code Content-Length} vem dos metadados no banco e o {@code storageKey} tem o mesmo formato nos
 * dois. Ou seja, a suíte podia passar inteira contra disco local e alguém concluir "S3 testado".
 *
 * <p>O padrão de {@code app.storage.type} é {@code local}. Basta a variável não chegar à instância
 * para o S3 sair de cena em silêncio - sem erro, sem log, com os testes verdes.
 *
 * <p>Só o que identifica a configuração: tipo, bucket, região e endpoint. Nada de credencial - elas
 * vêm da IAM Role da instância e não passam por aqui. O {@code /actuator/info} é exposto sem
 * autenticação, então nada que não possa ser público entra neste mapa.
 */
@Component
public class StorageInfoContributor implements InfoContributor {

    private final String tipo;
    private final String bucket;
    private final String regiao;
    private final String endpoint;

    public StorageInfoContributor(
            @Value("${app.storage.type:local}") String tipo,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.region:}") String regiao,
            @Value("${app.storage.s3.endpoint:}") String endpoint) {
        this.tipo = tipo;
        this.bucket = bucket;
        this.regiao = regiao;
        this.endpoint = endpoint;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("type", tipo);
        if ("s3".equalsIgnoreCase(tipo)) {
            storage.put("bucket", bucket.isBlank() ? "(não definido)" : bucket);
            storage.put("region", regiao);
            storage.put("endpoint", endpoint.isBlank() ? "(AWS padrão)" : endpoint);
        }
        builder.withDetail("storage", storage);
    }
}
