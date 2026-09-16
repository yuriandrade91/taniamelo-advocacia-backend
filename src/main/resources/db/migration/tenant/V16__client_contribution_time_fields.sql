-- V16: tempo de contribuição deixa de ser texto livre e vira anos, meses e dias.
--
-- Motivação: contribution_time era VARCHAR e o servidor lia os números dele com
-- expressão regular. Duas consequências reais, verificadas:
--   "nao informado"   -> contribution_in_months = 0     (igual a quem não contribuiu)
--   "300000000 anos"  -> contribution_in_months = -694967296  (estouro de int)
-- Nenhum dos dois era recusado. Os dois viravam dado gravado.
--
-- Três colunas numéricas com faixa própria não têm o que interpretar nem como
-- estourar, e é assim que o cálculo previdenciário é escrito e que o extrato do
-- INSS apresenta ("33 anos, 11 meses e 5 dias"). A frase continua existindo, mas
-- passa a ser derivada na resposta (TempoDeContribuicao.formatar) em vez de
-- guardada - texto guardado ao lado dos números diverge deles na primeira edição.
--
-- Convenção do domínio, aplicada aqui e nos CHECKs: mês de 30 dias, ano de 12
-- meses. Meses vai de 0 a 11 e dias de 0 a 29; 12 meses são 1 ano e 30 dias são
-- 1 mês, e aceitar as duas escritas gravaria a mesma duração de dois jeitos.

ALTER TABLE clients
    ADD COLUMN contribution_years  INTEGER,
    ADD COLUMN contribution_months INTEGER,
    ADD COLUMN contribution_days   INTEGER;

-- ─────────────── 1. Trazer o que dá para entender do texto ───────────────
--
-- Mesma leitura que o parser antigo fazia, com duas correções: "mês" com acento
-- passa a contar (o regex do Java só pegava "mes"/"meses", então quem escreveu
-- "1 ano, 1 mês" estava com o mês perdido), e o excedente é normalizado em vez
-- de gravado como veio - "14 meses" vira 1 ano e 2 meses, que é a mesma duração
-- na forma canônica.

UPDATE clients c
   SET contribution_years  = n.anos::int,
       contribution_months = n.meses::int,
       contribution_days   = n.dias::int
  FROM (
        SELECT id,
               (a + floor((m + floor(d / 30)) / 12))  AS anos,
               (m + floor(d / 30)) % 12               AS meses,
               d % 30                                 AS dias
          FROM (
                SELECT id,
                       COALESCE((substring(lower(contribution_time) from '([0-9]+)[[:space:]]*anos?'))::numeric,  0) AS a,
                       COALESCE((substring(lower(contribution_time) from '([0-9]+)[[:space:]]*m[eê]s'))::numeric,  0) AS m,
                       COALESCE((substring(lower(contribution_time) from '([0-9]+)[[:space:]]*dias?'))::numeric,   0) AS d
                  FROM clients
                 WHERE contribution_time IS NOT NULL
                   AND btrim(contribution_time) <> ''
                   -- Só linhas em que há de fato "<número> <unidade>". Sem isto,
                   -- "nao informado" cairia em 0/0/0 e voltaria a mentir zero.
                   AND lower(contribution_time) ~ '[0-9]+[[:space:]]*(anos?|m[eê]s|dias?)'
               ) bruto
       ) n
 WHERE c.id = n.id
   AND n.anos <= 130;

-- ────────────── 2. Registrar o que não deu para entender ──────────────
--
-- Texto preenchido que não virou número não é apagado em silêncio: vira linha em
-- audit_log com o que estava escrito, para alguém reconferir com o cliente.
--   SELECT * FROM audit_log WHERE detail LIKE 'V16:%' ORDER BY performed_at;

DO $$
DECLARE
    afetados INT;
BEGIN
    INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
    SELECT 'Client', id, 'UPDATE', NULL,
           'V16: tempo de contribuicao nao reconhecido e nao migrado - estava escrito: ' || contribution_time
      FROM clients
     WHERE contribution_time IS NOT NULL
       AND btrim(contribution_time) <> ''
       AND contribution_years IS NULL;

    GET DIAGNOSTICS afetados = ROW_COUNT;
    IF afetados > 0 THEN
        RAISE WARNING 'V16: % cliente(s) tinham tempo de contribuicao em texto que nao virou numero. Confira em audit_log e preencha na tela.', afetados;
    END IF;
END $$;

-- ──────────── 3. Recalcular o total em meses a partir dos números ────────────
--
-- Mesma regra do parser (dia >= 15 arredonda um mês para cima), para o número não
-- mudar debaixo de quem já está cadastrado. Quem não tem os três vira NULL - que
-- é "não informado", e é exatamente a distinção que o 0 apagava.

UPDATE clients
   SET contribution_in_months =
           CASE
               WHEN contribution_years IS NULL
                AND contribution_months IS NULL
                AND contribution_days IS NULL THEN NULL
               ELSE COALESCE(contribution_years, 0) * 12
                    + COALESCE(contribution_months, 0)
                    + CASE WHEN COALESCE(contribution_days, 0) >= 15 THEN 1 ELSE 0 END
           END;

-- ─────────────────────── 4. Aposentar a coluna de texto ───────────────────────

ALTER TABLE clients DROP COLUMN contribution_time;

-- ──────────────────────────── 5. Faixas no banco ────────────────────────────
--
-- O DTO já valida, mas a regra vale para qualquer caminho de escrita (carga,
-- correção manual, script) - e é o banco que impede o -694967296 de voltar.

ALTER TABLE clients
    ADD CONSTRAINT ck_clients_contribution_years
        CHECK (contribution_years IS NULL OR (contribution_years BETWEEN 0 AND 130)),
    ADD CONSTRAINT ck_clients_contribution_months
        CHECK (contribution_months IS NULL OR (contribution_months BETWEEN 0 AND 11)),
    ADD CONSTRAINT ck_clients_contribution_days
        CHECK (contribution_days IS NULL OR (contribution_days BETWEEN 0 AND 29));
