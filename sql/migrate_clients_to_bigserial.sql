-- Migration script: migrate clients.id from UUID to BIGSERIAL (bigint autoincrement)
-- WARNING: This script will create a NEW table `clients_new`, copy data from `clients` into it,
-- and (optionally) swap the tables. The script below is safe to run inside a transaction for validation
-- (it will be rolled back when used with BEGIN; \i file; ROLLBACK;).
-- Run in production only after taking a backup.

-- 1) CREATE NEW TABLE clients_new with BIGSERIAL id
CREATE TABLE IF NOT EXISTS clients_new (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    birth_date DATE NOT NULL,
    cpf VARCHAR(14) UNIQUE NOT NULL,
    mother_name VARCHAR(255) NOT NULL,
    mobile_phone VARCHAR(20) UNIQUE NOT NULL,
    inss_password VARCHAR(100) NOT NULL,
    gender VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) GENERATED ALWAYS AS (split_part(full_name, ' ', 1)) STORED,
    last_name VARCHAR(100) GENERATED ALWAYS AS (
        split_part(full_name, ' ', array_length(string_to_array(full_name, ' '), 1))
    ) STORED,
    rg VARCHAR(20) UNIQUE,
    email VARCHAR(255) UNIQUE,
    reference_phone VARCHAR(20) UNIQUE,
    reference_responsible VARCHAR(255),
    marital_status VARCHAR(50) CHECK (marital_status IN ('Solteiro(a)','Casado(a)','Separada(a)','Divorciado(a)','Viúvo(a)')),
    benefit VARCHAR(255) CHECK (benefit IN (
        'Aposentadoria por idade',
        'Aposentadoria por tempo de contribuição',
        'Aposentadoria por incapacidade permanente',
        'Aposentadoria especial',
        'Aposentadoria por deficiência',
        'Aposentadoria por tempo de contribuição do professor',
        'Aposentadoria por invalidez'
    )),
    situation VARCHAR(100) CHECK (situation IN (
        'formulário preenchido',
        'análise documental',
        'planejamento em execução',
        'planejamento concluído',
        'benefício futuro',
        'benefício concluido'
    )),
    benefit_number VARCHAR(30) UNIQUE,
    nit_pis VARCHAR(20) UNIQUE,
    profession VARCHAR(100),
    ctps VARCHAR(30) UNIQUE,
    ctps_series VARCHAR(20) UNIQUE,
    contribution_time INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    non_billable BOOLEAN NOT NULL DEFAULT false,
    created_by INTEGER
);

-- 2) Copy data from old clients to clients_new (map columns explicitly)
INSERT INTO clients_new (
    full_name, birth_date, cpf, mother_name, mobile_phone, inss_password, gender,
    rg, email, reference_phone, reference_responsible, marital_status, benefit, situation,
    benefit_number, nit_pis, profession, ctps, ctps_series, contribution_time, created_at, non_billable, created_by
)
SELECT
    full_name, birth_date, cpf, mother_name, mobile_phone, inss_password, gender,
    rg, email, reference_phone, reference_responsible, marital_status, benefit, situation,
    benefit_number, nit_pis, profession, ctps, ctps_series, contribution_time, created_at, non_billable, created_by
FROM clients;

-- 3) Verify counts
-- SELECT 'old' as which, count(*) FROM clients;
-- SELECT 'new' as which, count(*) FROM clients_new;

-- 4) After manual verification, the swap steps are:
-- BEGIN;
-- ALTER TABLE clients RENAME TO clients_old;
-- ALTER TABLE clients_new RENAME TO clients;
-- COMMIT;

-- 5) Cleanup (after verifying everything):
-- DROP TABLE clients_old;

-- End of script
