-- ============================================================================
-- V24 (por tenant): etapa do requerimento no INSS.
--
-- Campo separado de `situation` de propósito. `situation` é a esteira do
-- ESCRITÓRIO (formulário, análise documental, planejamento); esta é a esteira do
-- INSS (requerimento, exigência, deferimento, recurso). As duas correm em
-- paralelo: um cliente pode estar com "Planejamento concluído" aqui dentro e
-- "Exigência" lá fora ao mesmo tempo. Colapsar as duas numa coluna só perderia
-- justamente o par que diz o que fazer a seguir.
--
-- ANULÁVEL, e isso é regra de negócio, não omissão. Cliente em prospecção ou em
-- planejamento não protocolou requerimento nenhum, e NOT NULL DEFAULT
-- 'Requerimento' afirmaria um protocolo inexistente para toda a base atual.
-- Nulo aqui lê-se "ainda não foi ao INSS".
--
-- VARCHAR(100) e índice simples acompanham `situation` (V2), porque o filtro da
-- listagem é do mesmo formato: IN sobre o label gravado.
-- ============================================================================

ALTER TABLE clients ADD COLUMN IF NOT EXISTS request_stage VARCHAR(100);

COMMENT ON COLUMN clients.request_stage IS
    'Etapa do requerimento no INSS (Requerimento, Exigência, Cumprimento de exigência, '
    'Deferimento, Indeferimento, Recurso). Nulo = ainda não protocolado. '
    'Não confundir com situation, que é a esteira interna do escritório.';

CREATE INDEX IF NOT EXISTS idx_clients_request_stage ON clients (request_stage);
