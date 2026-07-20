-- V5: histórico de mudanças de situação do cliente (trilha de auditoria do
-- funil). Gravado automaticamente pela aplicação a cada mudança de situação.
CREATE TABLE client_situation_history (
    id                 UUID PRIMARY KEY,
    client_id          UUID NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    previous_situation VARCHAR(100),
    new_situation      VARCHAR(100),
    changed_at         TIMESTAMP NOT NULL DEFAULT now(),
    changed_by         UUID REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_situation_history_client_id  ON client_situation_history (client_id);
CREATE INDEX idx_situation_history_changed_at ON client_situation_history (changed_at DESC);
