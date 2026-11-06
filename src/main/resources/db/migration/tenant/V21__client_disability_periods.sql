-- V21: períodos de deficiência do cliente (LC 142/2013).
--
-- Um cliente pode ter a deficiência reconhecida em mais de um intervalo, e em
-- GRAUS diferentes em cada um — é justamente essa variação que a conversão de
-- tempo da LC 142/2013 existe para resolver. Por isso é coleção 1:N e não três
-- colunas em `clients`.
--
-- `ended_on` nulo não é "não sei": significa que a deficiência se mantém até a
-- presente data, que é a opção que a tela oferece explicitamente. Quem lê o
-- intervalo aberto calcula o fim como "hoje" no fuso do escritório.
CREATE TABLE client_disability_periods (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id  UUID        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    grade      VARCHAR(20) NOT NULL,
    started_on DATE        NOT NULL,
    -- Nulo = sem data de cessação (deficiência em curso).
    ended_on   DATE,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users (id) ON DELETE SET NULL,

    -- Cessar antes de começar não é intervalo. O banco recusa junto com a
    -- aplicação: a validação da API protege a mensagem de erro, esta protege o
    -- dado de qualquer caminho de escrita (seed, correção manual, migração).
    CONSTRAINT ck_disability_period_order
        CHECK (ended_on IS NULL OR ended_on >= started_on)
);

CREATE INDEX idx_client_disability_periods_client_id
    ON client_disability_periods (client_id);

-- No máximo um intervalo em aberto por cliente: dois "mantém até a presente
-- data" ao mesmo tempo significariam dois graus vigentes hoje, e a conversão
-- não teria como escolher entre eles.
CREATE UNIQUE INDEX ux_client_disability_periods_em_aberto
    ON client_disability_periods (client_id)
    WHERE ended_on IS NULL;
