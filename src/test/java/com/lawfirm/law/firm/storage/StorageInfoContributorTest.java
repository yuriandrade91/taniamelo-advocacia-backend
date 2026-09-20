package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

@DisplayName("StorageInfoContributor: qual backend de arquivos está servindo esta instância")
class StorageInfoContributorTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> storageDe(StorageInfoContributor c) {
        Info.Builder builder = new Info.Builder();
        c.contribute(builder);
        return (Map<String, Object>) builder.build().getDetails().get("storage");
    }

    @Test
    @DisplayName("com s3, publica bucket, região e endpoint")
    void comS3() {
        Map<String, Object> s =
                storageDe(new StorageInfoContributor("s3", "taniamelo-dev", "sa-east-1", ""));
        assertEquals("s3", s.get("type"));
        assertEquals("taniamelo-dev", s.get("bucket"));
        assertEquals("sa-east-1", s.get("region"));
        assertEquals("(AWS padrão)", s.get("endpoint"));
    }

    @Test
    @DisplayName("com s3 e bucket vazio, diz isso alto em vez de publicar string vazia")
    void comS3SemBucket() {
        // A falha silenciosa que este contributor existe para evitar: S3 "ligado" e sem destino.
        assertEquals(
                "(não definido)",
                storageDe(new StorageInfoContributor("s3", "", "sa-east-1", "")).get("bucket"));
    }

    @Test
    @DisplayName("com local, não publica campo de S3 nenhum")
    void comLocal() {
        Map<String, Object> s =
                storageDe(new StorageInfoContributor("local", "sobra", "sa-east-1", ""));
        assertEquals("local", s.get("type"));
        assertFalse(s.containsKey("bucket"), "bucket de S3 não faz sentido com disco local");
    }

    @Test
    @DisplayName("nunca publica credencial - /actuator/info é aberto")
    void semCredenciais() {
        String publicado =
                storageDe(new StorageInfoContributor("s3", "b", "sa-east-1", "")).toString();
        for (String proibido : new String[] {"secret", "accessKey", "password", "token"}) {
            assertTrue(
                    !publicado.toLowerCase().contains(proibido.toLowerCase()),
                    "vazou " + proibido + " em /actuator/info: " + publicado);
        }
    }
}
