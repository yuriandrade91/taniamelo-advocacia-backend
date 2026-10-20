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

    @PreUpdate
    void onUpdate() {
        this.atualizadoEm = Instant.now();
    }
}
