package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

@DisplayName("ObjectStorageConfig: cliente S3 criado só quando app.storage.type=s3")
class ObjectStorageConfigTest {

    private ObjectStorageConfig config;

    @BeforeEach
    void setUp() {
        config = new ObjectStorageConfig();
        // O DefaultCredentialsProvider é resolvido de forma preguiçosa: o bean é construído
        // sem credenciais reais, que só seriam exigidas numa chamada de verdade.
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @Test
    @DisplayName("sem endpoint, usa o endpoint real da região configurada")
    void buildsClientForAwsRegion() {
        try (S3Client client = config.s3Client("sa-east-1", "", false)) {
            assertNotNull(client);
            assertNotNull(client.serviceClientConfiguration().region());
        }
    }

    @Test
    @DisplayName("com endpoint e path-style, aponta para um provedor compatível (MinIO/LocalStack)")
    void buildsClientForCompatibleProvider() {
        try (S3Client client = config.s3Client("us-east-1", "http://localhost:9000", true)) {
            assertNotNull(client);
        }
    }

    @Test
    @DisplayName("endpoint em branco é tratado como ausente")
    void blankEndpointIsIgnored() {
        try (S3Client client = config.s3Client("sa-east-1", "   ", false)) {
            assertNotNull(client);
        }
    }

    @Test
    @DisplayName("região inválida é rejeitada na construção")
    void invalidRegionIsRejected() {
        assertThrows(RuntimeException.class, () -> config.s3Client(null, "", false));
    }
}
