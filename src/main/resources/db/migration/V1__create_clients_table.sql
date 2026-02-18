-- ============================================
-- V1__create_clients_table.sql
-- Migration file (Flyway convention) containing the clients table DDL
-- Generated: 2026-01-28
-- ============================================

CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) GENERATED ALWAYS AS (split_part(full_name, ' ', 1)) STORED,
    last_name VARCHAR(100) GENERATED ALWAYS AS (
        split_part(full_name, ' ', array_length(string_to_array(full_name, ' '), 1))
    ) STORED,
    birth_date DATE NOT NULL,
    marital_status_id INTEGER NOT NULL,
    cpf VARCHAR(14) UNIQUE NOT NULL,
    rg VARCHAR(20) UNIQUE NOT NULL,
    mother_name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    mobile_phone VARCHAR(20) UNIQUE NOT NULL,
    reference_phone VARCHAR(20) UNIQUE NOT NULL,
    reference_responsible VARCHAR(255),
    benefit_id INTEGER NOT NULL,
    benefit_number VARCHAR(30) UNIQUE NOT NULL,
    situation_id INTEGER NOT NULL,
    profession VARCHAR(100),
    ctps VARCHAR(30) UNIQUE NOT NULL,
    ctps_series VARCHAR(20) UNIQUE NOT NULL,
    nit_pis VARCHAR(20) UNIQUE NOT NULL,
    inss_password VARCHAR(100),
    contribution_time INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    non_billable BOOLEAN NOT NULL DEFAULT false,
    created_by INTEGER NOT NULL
);

-- Index suggestions
-- CREATE INDEX idx_clients_cpf ON clients(cpf);
-- CREATE INDEX idx_clients_email ON clients(email);
-- CREATE INDEX idx_clients_full_name ON clients(full_name);
-- CREATE INDEX idx_clients_created_at ON clients(created_at);
-- CREATE INDEX idx_clients_updated_at ON clients(updated_at);

-- Foreign keys (uncomment if referenced tables exist)
-- ALTER TABLE clients ADD CONSTRAINT fk_clients_marital_status FOREIGN KEY (marital_status_id) REFERENCES marital_status(id);
-- ALTER TABLE clients ADD CONSTRAINT fk_clients_benefit FOREIGN KEY (benefit_id) REFERENCES benefits(id);
-- ALTER TABLE clients ADD CONSTRAINT fk_clients_situation FOREIGN KEY (situation_id) REFERENCES situation(id);
-- ALTER TABLE clients ADD CONSTRAINT fk_clients_created_by FOREIGN KEY (created_by) REFERENCES users(id);
