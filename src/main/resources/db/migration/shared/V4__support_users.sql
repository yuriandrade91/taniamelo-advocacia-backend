-- V4 (compartilhada / schema public): usuários da PLATAFORMA (equipe de suporte).
--
-- Diferente de tenant_x.users: estes NÃO pertencem a nenhum escritório. Uma única
-- identidade autentica no control-plane e, a partir dela, abre uma sessão de
-- suporte impersonando um tenant específico (ver SupportController / docs).
-- Fica no public, ao lado de tenants: é dado de controle da plataforma.
CREATE TABLE support_users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);

CREATE INDEX idx_support_users_active ON support_users (active);
