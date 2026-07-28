-- V9: trilha de auditoria genérica - quem criou/alterou/removeu qualquer
-- registro auditável (clientes e todos os sub-recursos), independente de
-- created_by/updated_by já existirem em cada tabela.
--
-- Motivação: created_by/updated_by por tabela cobre create/update, mas um
-- DELETE físico apaga a linha inteira - sem essa trilha à parte, não sobra
-- nenhum registro de quem removeu o quê. audit_log é alimentada
-- automaticamente por AuditLogListener (JPA @PostPersist/@PostUpdate/
-- @PostRemove), então nenhum service precisa lembrar de gravar nela.
CREATE TABLE audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_name   VARCHAR(100)  NOT NULL,
    entity_id     UUID          NOT NULL,
    action        VARCHAR(20)   NOT NULL,
    performed_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    performed_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    detail        VARCHAR(255)
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_name, entity_id);
CREATE INDEX idx_audit_log_performed_by ON audit_log (performed_by);
CREATE INDEX idx_audit_log_performed_at ON audit_log (performed_at);
