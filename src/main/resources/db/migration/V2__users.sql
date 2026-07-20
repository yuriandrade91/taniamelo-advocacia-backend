-- V2: usuários do sistema (autenticação/autorização).
-- Um usuário ADMIN inicial é criado pela aplicação (AdminUserSeeder) na
-- primeira subida quando a tabela está vazia.
--
-- Padrão do projeto: listas fixas (role etc.) são validadas SOMENTE nos enums
-- Java + converters - sem CHECK em SQL, para não manter duas fontes de verdade.
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'STAFF',
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP
);
