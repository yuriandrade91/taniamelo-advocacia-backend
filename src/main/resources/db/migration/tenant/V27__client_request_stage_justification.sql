-- ============================================================================
-- V27 (por tenant): justificativa da transição de etapa no INSS.
--
-- Mudar a etapa para Exigência, Indeferimento ou Recurso passa a exigir um
-- motivo - o que o INSS pediu, por que negou, contra o quê se recorre. A regra
-- é da aplicação (RequestStage.exigeJustificativa), não do banco: as demais
-- transições não pedem, e as entradas gravadas antes desta versão não têm.
-- Por isso a coluna é anulável e sem CHECK.
--
-- Migration nova, e não edição da V26: a V26 pode já estar aplicada, e mudar
-- o checksum dela derruba o deploy (ver V23/V25).
-- ============================================================================

ALTER TABLE client_request_stage_history
    ADD COLUMN IF NOT EXISTS justification TEXT;

COMMENT ON COLUMN client_request_stage_history.justification IS
    'Motivo da transição. Obrigatório (na aplicação) ao entrar em Exigência, '
    'Indeferimento ou Recurso.';
