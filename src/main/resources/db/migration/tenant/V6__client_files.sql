-- V7: collection ÚNICA de arquivos do cliente, servindo as abas "Documentos"
-- e "Simulações" (discriminadas por kind = DOCUMENT | SIMULATION).
--
--  - O binário NUNCA fica no banco: só storage_key + metadados (o arquivo vive
--    no storage - disco local hoje, trocável por S3 via FileStorageService).
--  - document_type: um dos 11 tipos (enum Java DocumentType) - só p/ DOCUMENT.
--  - simulation_date/version/vinculos/is_principal: só p/ SIMULATION.
--  - Exatamente uma simulação principal ativa por cliente (índice parcial);
--    a aplicação promove a mais recente automaticamente.
--  - Soft delete nas duas abas: arquivos podem ser evidência exigida depois.
CREATE TABLE client_files (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id         UUID         NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    kind              VARCHAR(20)  NOT NULL,

    -- comum
    original_filename VARCHAR(255) NOT NULL,
    storage_key       VARCHAR(500) NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    notes             TEXT,

    -- específico de DOCUMENT
    document_type     VARCHAR(60),

    -- específico de SIMULATION
    simulation_date   DATE,
    version           VARCHAR(30),
    vinculos          INTEGER,
    is_principal      BOOLEAN      NOT NULL DEFAULT false,

    -- auditoria
    uploaded_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    uploaded_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at        TIMESTAMP,
    deleted_at        TIMESTAMP
);

CREATE INDEX idx_client_files_client_kind_active
    ON client_files (client_id, kind, uploaded_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_client_files_document_type_active
    ON client_files (client_id, document_type)
    WHERE deleted_at IS NULL AND kind = 'DOCUMENT';

-- No máximo uma simulação principal ativa por cliente.
CREATE UNIQUE INDEX ux_client_files_simulation_principal
    ON client_files (client_id)
    WHERE kind = 'SIMULATION' AND is_principal AND deleted_at IS NULL;
