-- V22: dia do aniversário (MMDD) indexado, para "próximos aniversariantes".
--
-- "Quem faz aniversário nos próximos N dias" depende de mês/dia relativos a
-- hoje, então um índice em birth_date não serve: a consulta varreria a tabela.
-- Guardamos mês*100+dia (ex.: 12/10 -> 1012) numa coluna GERADA - o banco a
-- mantém sozinho, nenhum código precisa lembrar de preenchê-la - e a janela
-- vira um range nesse índice (com OR na virada de ano).
--
-- EXTRACT sobre DATE é IMMUTABLE (não depende de fuso), requisito de coluna
-- gerada. Índice parcial: só clientes ativos entram no card.
ALTER TABLE clients
    ADD COLUMN birth_mmdd SMALLINT
        GENERATED ALWAYS AS (
            (EXTRACT(MONTH FROM birth_date) * 100 + EXTRACT(DAY FROM birth_date))::SMALLINT
        ) STORED;

CREATE INDEX idx_clients_birth_mmdd ON clients (birth_mmdd) WHERE deleted_at IS NULL;
