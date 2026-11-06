-- V2 (compartilhada / schema public): catálogo de tenants (control-plane).
--
-- Fonte de verdade de QUAIS escritórios existem, para qual SCHEMA cada um aponta
-- e seus metadados de negócio (CNPJ, plano, status). Vive só no public: é dado de
-- controle da plataforma, não dado de nenhum tenant. O MultiTenantFlywayMigrator
-- lê os schemas ativos daqui para saber quais migrar; adicionar um escritório =
-- inserir uma linha aqui (status='ativo') e reiniciar (scripts/tenant). A tabela nasce vazia.
CREATE TABLE tenants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schema_name   VARCHAR(63)  NOT NULL UNIQUE,
    razao_social  VARCHAR(255) NOT NULL,
    cnpj          VARCHAR(20)  NOT NULL UNIQUE,
    responsavel   VARCHAR(255),
    email         VARCHAR(255),
    telefone      VARCHAR(20),
    plano         VARCHAR(50),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ativo',
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ
);

CREATE INDEX idx_tenants_status ON tenants (status);
