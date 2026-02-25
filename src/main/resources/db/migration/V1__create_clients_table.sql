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
    birth_date DATE,
    gender VARCHAR(20) NOT NULL,
    marital_status VARCHAR(50),
    cpf VARCHAR(14) UNIQUE NOT NULL,
    rg VARCHAR(20) UNIQUE,
    mother_name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    mobile_phone VARCHAR(20) UNIQUE NOT NULL,
    reference_phone VARCHAR(20) UNIQUE,
    reference_responsible VARCHAR(255),
    benefit VARCHAR(100),
    beneficiary_number VARCHAR(30) UNIQUE,
    situation VARCHAR(100),
    profession VARCHAR(100),
    ctps VARCHAR(30) UNIQUE,
    ctps_series VARCHAR(20),
    nit_pis VARCHAR(20) UNIQUE,
    inss_password VARCHAR(100) NOT NULL,
    contribution_time VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    not_billable BOOLEAN DEFAULT false,
    created_by INTEGER
);