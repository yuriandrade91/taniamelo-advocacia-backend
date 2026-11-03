package com.lawfirm.law.firm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Catálogo de tenants (control-plane), na tabela {@code public.tenants}. Como {@code tenants} só
 * existe no schema public e o {@code search_path} de toda requisição inclui public, esta entidade
 * sempre resolve para a mesma tabela compartilhada, independente do tenant corrente. É o registro
 * de negócio de cada escritório (razão social, CNPJ, plano, status) e a fonte de verdade de quais
 * schemas a plataforma atende.
 */
@Entity
@Table(name = "tenants", schema = "public")
@Getter
public class Tenant {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "schema_name", nullable = false, unique = true, length = 63)
    @Setter
    private String schemaName;

    @Column(name = "slug", nullable = false, unique = true, length = 60)
    @Setter
    private String slug;

    @Column(name = "razao_social", nullable = false)
    @Setter
    private String razaoSocial;

    @Column(name = "cnpj", nullable = false, unique = true, length = 20)
    @Setter
    private String cnpj;

    @Column(name = "responsavel")
    @Setter
    private String responsavel;

    @Column(name = "email")
    @Setter
    private String email;

    @Column(name = "telefone", length = 20)
    @Setter
    private String telefone;

    @Column(name = "plano", length = 50)
    @Setter
    private String plano;

    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private String status = "ativo";

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    /** Chave da logo no storage. Nula: o escritório usa a marca padrão do sistema. */
    @Column(name = "logo_key", length = 500)
    private String logoKey;

    /** O tipo conferido nos bytes no envio, não o que o navegador declarou. */
    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    /** Versão da logo: entra na URL pública para o navegador não ficar com a imagem anterior. */
    @Column(name = "logo_updated_at")
    private Instant logoUpdatedAt;

    public void useLogo(String storageKey, String contentType) {
        this.logoKey = storageKey;
        this.logoContentType = contentType;
        this.logoUpdatedAt = Instant.now();
    }

    public void clearLogo() {
        this.logoKey = null;
        this.logoContentType = null;
        this.logoUpdatedAt = null;
    }

    @PreUpdate
    void onUpdate() {
        this.atualizadoEm = Instant.now();
    }
}
