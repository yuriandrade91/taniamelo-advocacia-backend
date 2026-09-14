-- V15: os documentos que identificam o cliente passam a ser normalizados e únicos.
--
-- Motivação: dava para cadastrar a mesma pessoa duas vezes - uma com
-- "39053344705" e outra com "390.533.447-05". O CpfValidator já tirava a
-- pontuação antes de validar e a busca já comparava por dígitos; só a checagem
-- de duplicidade e o índice único olhavam o texto cru.
--
-- Únicos a partir daqui: cpf, rg, ctps, nit_pis, benefit_number - os cinco
-- documentos que dizem QUEM a pessoa é. Celular, e-mail e telefone de recado
-- NÃO entram de propósito: são contato, e contato se compartilha (mãe e filho,
-- casal, telefone do responsável). Torná-los únicos recusaria o segundo
-- cadastro de uma família.
--
-- Normalização: numéricos viram só dígitos; o RG vira alfanumérico maiúsculo
-- (o formato varia por estado e o órgão emissor pode vir no número).
-- Guardamos normalizado em vez de indexar por expressão porque um índice sobre
-- expressão barraria o duplicado e deixaria os dois formatos convivendo no
-- banco - que é exatamente a bagunça que se está limpando.

-- ───────────────── 0. Derrubar os índices antigos ─────────────────
--
-- Antes de normalizar, e não depois: normalizar com o índice de CPF de pé faz
-- duas linhas que eram diferentes ("39053344705" e "390.533.447-05") virarem a
-- mesma NO MEIO do UPDATE, e o índice recusa a própria migration que existe
-- para arrumá-las.

DROP INDEX IF EXISTS ux_clients_cpf_active;
DROP INDEX IF EXISTS ux_clients_nit_pis_active;
DROP INDEX IF EXISTS ux_clients_benefit_number_active;

-- ─────────────────────────── 1. Normalizar ───────────────────────────

UPDATE clients SET cpf            = regexp_replace(cpf,            '[^0-9]',       '', 'g') WHERE cpf            IS NOT NULL;
UPDATE clients SET nit_pis        = regexp_replace(nit_pis,        '[^0-9]',       '', 'g') WHERE nit_pis        IS NOT NULL;
UPDATE clients SET ctps           = regexp_replace(ctps,           '[^0-9]',       '', 'g') WHERE ctps           IS NOT NULL;
UPDATE clients SET benefit_number = regexp_replace(benefit_number, '[^0-9]',       '', 'g') WHERE benefit_number IS NOT NULL;
UPDATE clients SET rg             = upper(regexp_replace(rg,       '[^a-zA-Z0-9]', '', 'g')) WHERE rg            IS NOT NULL;

-- Vazio e NULL significam a mesma coisa aqui, e manter os dois faria o índice
-- único tratar '' como um valor legítimo (e repetível uma vez só).
UPDATE clients SET nit_pis        = NULL WHERE nit_pis        = '';
UPDATE clients SET ctps           = NULL WHERE ctps           = '';
UPDATE clients SET benefit_number = NULL WHERE benefit_number = '';
UPDATE clients SET rg             = NULL WHERE rg             = '';

-- ──────────────────── 2. Desempatar o que colidiu ────────────────────
--
-- A normalização pode ter transformado dois valores diferentes no mesmo. A
-- regra é a mesma para todos: o registro MAIS ANTIGO fica com o documento.
--
-- Para os campos opcionais, o documento é esvaziado nos mais novos - o cliente
-- continua lá, com o campo em branco para alguém reconferir.
--
-- Para o CPF não dá: é NOT NULL e é a identidade do registro. Nesse caso o
-- duplicado MAIS NOVO é excluído logicamente. Nada é apagado: deleted_at é
-- reversível e o registro continua no banco.
--
-- Tudo o que esta migration mexer vira linha em audit_log, com performed_by
-- nulo (foi o sistema, não uma pessoa). Para ver depois:
--   SELECT * FROM audit_log WHERE detail LIKE 'V15:%' ORDER BY performed_at;

DO $$
DECLARE
    r        RECORD;
    campo    TEXT;
    campos   TEXT[] := ARRAY['nit_pis', 'ctps', 'benefit_number', 'rg'];
    afetados INT;
BEGIN
    -- 2a. Campos opcionais: o mais novo perde o documento.
    FOREACH campo IN ARRAY campos LOOP
        EXECUTE format($f$
            WITH ordenados AS (
                SELECT id,
                       row_number() OVER (PARTITION BY %1$I ORDER BY created_at, id) AS posicao
                  FROM clients
                 WHERE deleted_at IS NULL AND %1$I IS NOT NULL
            ),
            perdedores AS (
                SELECT id FROM ordenados WHERE posicao > 1
            ),
            limpos AS (
                UPDATE clients SET %1$I = NULL
                 WHERE id IN (SELECT id FROM perdedores)
             RETURNING id
            )
            INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
            SELECT 'Client', id, 'UPDATE', NULL,
                   'V15: %1$s duplicado apagado (registro mais antigo ficou com o documento)'
              FROM limpos
        $f$, campo);
        GET DIAGNOSTICS afetados = ROW_COUNT;
        IF afetados > 0 THEN
            RAISE NOTICE 'V15: % duplicado em % registro(s) - campo esvaziado no mais novo', campo, afetados;
        END IF;
    END LOOP;

    -- 2b. CPF: o mais novo é excluído logicamente (não dá para esvaziar).
    WITH ordenados AS (
        SELECT id,
               row_number() OVER (PARTITION BY cpf ORDER BY created_at, id) AS posicao
          FROM clients
         WHERE deleted_at IS NULL AND cpf IS NOT NULL
    ),
    perdedores AS (
        SELECT id FROM ordenados WHERE posicao > 1
    ),
    excluidos AS (
        UPDATE clients SET deleted_at = now()
         WHERE id IN (SELECT id FROM perdedores)
     RETURNING id
    )
    INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
    SELECT 'Client', id, 'DELETE', NULL,
           'V15: CPF duplicado - excluido logicamente (restaure apos conferir)'
      FROM excluidos;

    GET DIAGNOSTICS afetados = ROW_COUNT;
    IF afetados > 0 THEN
        RAISE WARNING 'V15: % cliente(s) com CPF duplicado foram excluidos logicamente. Confira em audit_log e restaure o que for legitimo.', afetados;
    END IF;
END $$;

-- ──────────────────────── 3. Índices únicos ────────────────────────
--
-- Parciais (WHERE deleted_at IS NULL) pelo mesmo motivo da V12: excluir um
-- cliente e recadastrá-lo com o mesmo CPF tem de funcionar.

CREATE UNIQUE INDEX ux_clients_cpf_active
    ON clients (cpf) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_clients_rg_active
    ON clients (rg) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_clients_ctps_active
    ON clients (ctps) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_clients_nit_pis_active
    ON clients (nit_pis) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_clients_benefit_number_active
    ON clients (benefit_number) WHERE deleted_at IS NULL;
