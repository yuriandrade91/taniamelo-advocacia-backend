-- V11 (tenant): Agenda do escritório - compromissos (entrevistas, reuniões,
-- perícias, audiências, prazos). Top-level (não é sub-recurso de cliente), com
-- vínculo OPCIONAL a um cliente. Soft delete + auditoria, no padrão do projeto.
--
-- Regra de período garantida também no banco (CHECK end_at > start_at) - não é
-- um CHECK de enum (esses o projeto evita), é invariante de integridade.
CREATE TABLE appointments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(255) NOT NULL,
    type                VARCHAR(50)  NOT NULL,
    start_at            TIMESTAMPTZ  NOT NULL,
    end_at              TIMESTAMPTZ  NOT NULL,
    modality            VARCHAR(20)  NOT NULL DEFAULT 'Presencial',
    location            VARCHAR(255),
    meeting_url         VARCHAR(500),
    description         TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'Agendado',
    cancellation_reason TEXT,
    -- Vínculo com cliente: OU um cliente cadastrado (client_id), OU um nome livre
    -- (client_name) quando a pessoa ainda não é cliente. Ambos opcionais.
    client_id           UUID REFERENCES clients (id) ON DELETE SET NULL,
    client_name         VARCHAR(255),

    -- Autorização de data retroativa: quem confirmou a ciência (start no passado) e quando.
    past_date_authorized_by UUID REFERENCES users (id) ON DELETE SET NULL,
    past_date_authorized_at TIMESTAMPTZ,

    -- auditoria
    created_by          UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT ck_appointments_period CHECK (end_at > start_at)
);

CREATE INDEX idx_appointments_start_active ON appointments (start_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_appointments_client ON appointments (client_id);
CREATE INDEX idx_appointments_status_active ON appointments (status) WHERE deleted_at IS NULL;

-- Trilha de alterações: toda EDIÇÃO e todo CANCELAMENTO exige justificativa,
-- registrada aqui com quem/quando (mesmo padrão de client_situation_history).
CREATE TABLE appointment_history (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID        NOT NULL REFERENCES appointments (id) ON DELETE CASCADE,
    action         VARCHAR(20) NOT NULL,
    justification  TEXT        NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by     UUID REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_appointment_history_appointment
    ON appointment_history (appointment_id, changed_at DESC);
