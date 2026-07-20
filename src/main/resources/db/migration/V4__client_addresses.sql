-- V4: endereços do cliente como coleção 1:N (residencial/comercial/
-- correspondência), com exatamente um "principal" por cliente garantido por
-- índice único parcial.
CREATE TABLE client_addresses (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id      UUID         NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    address_type   VARCHAR(20)  NOT NULL DEFAULT 'Residencial',
    street         VARCHAR(255) NOT NULL,
    address_number VARCHAR(20),
    complement     VARCHAR(100),
    neighborhood   VARCHAR(100),
    city           VARCHAR(100) NOT NULL,
    state          VARCHAR(2)   NOT NULL,
    zip_code       VARCHAR(10),
    is_primary     BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,
    created_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_by     UUID REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_client_addresses_client_id ON client_addresses (client_id);

-- No máximo um endereço principal por cliente.
CREATE UNIQUE INDEX ux_client_addresses_primary
    ON client_addresses (client_id)
    WHERE is_primary;
