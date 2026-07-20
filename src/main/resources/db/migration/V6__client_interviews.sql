-- V6: entrevistas/atendimentos do cliente. content recebe o conteúdo do
-- componente rich text do frontend (HTML/JSON), armazenado sem transformação.
-- Soft delete: o conteúdo pode ser evidência relevante depois.
CREATE TABLE client_interviews (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id        UUID      NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    occurred_at      TIMESTAMP NOT NULL DEFAULT now(),
    duration_minutes INTEGER,
    content          TEXT      NOT NULL,
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at       TIMESTAMP,
    deleted_at       TIMESTAMP
);

CREATE INDEX idx_client_interviews_client_active
    ON client_interviews (client_id, occurred_at DESC)
    WHERE deleted_at IS NULL;
