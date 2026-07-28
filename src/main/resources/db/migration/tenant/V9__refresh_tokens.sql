-- V9 (tenant): refresh tokens para o fluxo de renovação de sessão.
-- Um refresh token é opaco (aleatório), guardado apenas HASHEADO (SHA-256) -
-- nunca o valor em claro. Rotação: cada uso invalida o anterior. Revogação:
-- logout marca revoked_at. Vive no schema do próprio tenant (isolado por office).
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
