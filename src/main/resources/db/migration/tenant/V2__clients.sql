-- V3: clientes do escritório.
--
-- Decisões de modelagem (ver docs/DATA_MODEL.md):
--  - Endereço NÃO fica embutido aqui: coleção 1:N em client_addresses (V4).
--  - Únicos: cpf, nit_pis e benefit_number (identificadores reais da pessoa/
--    benefício). Telefone, e-mail, RG e CTPS podem se repetir legitimamente.
--  - inss_password armazenada criptografada (AES-GCM) pela aplicação
--    (CryptoConverter) - por isso VARCHAR(255).
--  - Sem CHECKs de enum (padrão do projeto: validação nos enums Java).
--  - updated_at é mantido pela aplicação (@PrePersist/@PreUpdate) - sem trigger.
CREATE TABLE clients (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- obrigatórios
    full_name              VARCHAR(255) NOT NULL,
    birth_date             DATE         NOT NULL,
    cpf                    VARCHAR(14)  NOT NULL UNIQUE,
    mother_name            VARCHAR(255) NOT NULL,
    mobile_phone           VARCHAR(20)  NOT NULL,
    inss_password          VARCHAR(255) NOT NULL,
    gender                 VARCHAR(20)  NOT NULL,
    situation              VARCHAR(100) NOT NULL,
    benefit                VARCHAR(100) NOT NULL,

    -- identificação complementar
    rg                     VARCHAR(20),
    rg_issuer              VARCHAR(20),
    rg_issue_date          DATE,
    nationality            VARCHAR(50)  NOT NULL DEFAULT 'Brasileira',
    marital_status         VARCHAR(50),

    -- contato
    email                  VARCHAR(255),
    is_whatsapp            BOOLEAN      NOT NULL DEFAULT true,
    reference_phone        VARCHAR(20),
    reference_responsible  VARCHAR(255),

    -- dados profissionais / previdenciários
    profession             VARCHAR(100),
    nit_pis                VARCHAR(20)  UNIQUE,
    ctps                   VARCHAR(30),
    ctps_series            VARCHAR(20),
    benefit_number         VARCHAR(30)  UNIQUE,
    contribution_time      VARCHAR(255),
    contribution_in_months INTEGER,
    has_disability         BOOLEAN      NOT NULL DEFAULT false,

    -- gestão do caso
    client_type            VARCHAR(20)  NOT NULL DEFAULT 'Potencial',
    not_billable           BOOLEAN      NOT NULL DEFAULT false,
    notes                  TEXT,
    responsible_user_id    UUID REFERENCES users (id) ON DELETE SET NULL,

    -- auditoria
    created_at             TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP,
    created_by             UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_by             UUID REFERENCES users (id) ON DELETE SET NULL
);

-- Índices de apoio aos filtros/ordenação da listagem.
CREATE INDEX idx_clients_situation        ON clients (situation);
CREATE INDEX idx_clients_benefit          ON clients (benefit);
CREATE INDEX idx_clients_client_type      ON clients (client_type);
CREATE INDEX idx_clients_updated_at       ON clients (updated_at DESC);
CREATE INDEX idx_clients_responsible_user ON clients (responsible_user_id);
