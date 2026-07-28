-- V8: parcelas de honorários. Cada linha é UMA parcela (um contrato 6x vira 6
-- linhas com installment_number 1..6). "Atrasado" nunca é persistido: é
-- derivado em runtime (Pendente + due_date no passado).
CREATE TABLE client_payments (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id          UUID          NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    description        VARCHAR(255)  NOT NULL,
    amount             NUMERIC(12,2) NOT NULL,
    installment_number INTEGER,
    installment_total  INTEGER,
    due_date           DATE          NOT NULL,
    paid_date          DATE,
    status             VARCHAR(20)   NOT NULL DEFAULT 'Pendente',
    payment_method     VARCHAR(20),
    notes              TEXT,
    created_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT now(),
    updated_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at         TIMESTAMP,
    deleted_at         TIMESTAMP
);

CREATE INDEX idx_client_payments_client_active
    ON client_payments (client_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_client_payments_due_date_active
    ON client_payments (due_date)
    WHERE deleted_at IS NULL;
