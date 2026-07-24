package com.lawfirm.law.firm.storage;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Cria o {@link S3Client} apenas quando {@code app.storage.type=s3}. Em dev local (type=local, o
 * padrão) esta configuração nem é avaliada, então nenhuma credencial não é exigida para rodar no
 * notebook. Chama-se "ObjectStorage" e não "S3" porque o endpoint é configurável para qualquer
 * provedor compatível com a API S3 (AWS, Cloudflare R2, MinIO, LocalStack) - o SDK da AWS é só o
 * cliente HTTP que fala esse protocolo, não um vínculo com a AWS em si.
 *
 * <p>Credenciais são resolvidas pelo {@link DefaultCredentialsProvider}: em produção na AWS isso
 * pega automaticamente a IAM Role da instância EC2 (sem chave hardcoded); fora da AWS, cai nas
 * variáveis de ambiente {@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY} ou no {@code
 * ~/.aws/credentials}.
 *
 * <p>{@code app.storage.s3.endpoint} é opcional: preencha só para apontar a um endpoint compatível
 * diferente da AWS (Cloudflare R2, LocalStack/MinIO em testes); vazio usa o endpoint real da AWS
 * para a região configurada.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class ObjectStorageConfig {

    @Bean
    public S3Client s3Client(
            @org.springframework.beans.factory.annotation.Value("${app.storage.s3.region}")
                    String region,
            @org.springframework.beans.factory.annotation.Value("${app.storage.s3.endpoint:}")
                    String endpoint,
            @org.springframework.beans.factory.annotation.Value(
                            "${app.storage.s3.path-style-access:false}")
                    boolean pathStyleAccess) {
        var builder =
                S3Client.builder()
                        .region(Region.of(region))
                        .credentialsProvider(DefaultCredentialsProvider.create());

        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        // path-style é necessário para LocalStack/MinIO; na AWS real deixa virtual-hosted
        // (default).
        builder.serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build());

        return builder.build();
    }
}
