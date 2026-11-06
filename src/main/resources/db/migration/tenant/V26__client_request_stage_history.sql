-- ============================================================================
-- V26 (por tenant): histórico de transições da etapa no INSS.
--
-- Espelha client_situation_history (V4), que faz o mesmo para a esteira do
-- escritório. Gravado automaticamente pela aplicação a cada mudança de
-- request_stage.
--
-- POR QUE previous_stage_entered_at EXISTE, e não é derivado na leitura.
-- A pergunta que motivou a tabela é "quanto tempo ficou em exigência?", e a
-- resposta é a distância entre duas entradas consecutivas. Calcular isso a
-- partir da linha vizinha quebra na primeira página de uma listagem paginada,
-- onde a vizinha pode não estar carregada - o mesmo motivo pelo qual
-- client_situation_history grava previous_situation em vez de reconstruí-la.
-- Guardando quando a etapa anterior começou, cada linha responde sozinha.
--
-- Nulo em previous_stage e previous_stage_entered_at = primeira entrada, quando
-- o requerimento foi protocolado e não havia etapa anterior.
-- ============================================================================

CREATE TABLE IF NOT EXISTS client_request_stage_history (
    id                        UUID PRIMARY KEY,
    client_id                 UUID NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    previous_stage            VARCHAR(100),
    new_stage                 VARCHAR(100),
    previous_stage_entered_at TIMESTAMP,
    changed_at                TIMESTAMP NOT NULL DEFAULT now(),
    changed_by                UUID REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_request_stage_history_client_id
    ON client_request_stage_history (client_id);
CREATE INDEX IF NOT EXISTS idx_request_stage_history_changed_at
    ON client_request_stage_history (changed_at DESC);

COMMENT ON TABLE client_request_stage_history IS
    'Trilha das transições de etapa do requerimento no INSS. Não confundir com '
    'client_situation_history, que é a esteira interna do escritório.';
