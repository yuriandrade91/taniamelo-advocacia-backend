-- V17: dois nomes para o mesmo benefício viram um.
--
-- "Aposentadoria por invalidez" é como a lei chamava, antes da EC 103/2019, o
-- que hoje se chama "Aposentadoria por incapacidade permanente". "Aposentadoria
-- para PCD" é o jeito informal de dizer "Aposentadoria por deficiência". Os
-- quatro eram valores aceitos do enum, e o banco guarda o LABEL - então o mesmo
-- benefício ficava gravado de dois jeitos e qualquer contagem por benefício
-- somava metade em cada linha, sem ninguém notar que os dois eram um.
--
-- Os nomes antigos continuam aceitos na ENTRADA (BenefitType.APELIDOS), para
-- payload antigo e integração de fora não quebrarem. O que se grava, daqui em
-- diante, é sempre o canônico - e esta migration acerta o que já está gravado.
--
-- Cada linha mexida vira registro em audit_log, com performed_by nulo (foi o
-- sistema). Para conferir depois:
--   SELECT * FROM audit_log WHERE detail LIKE 'V17:%' ORDER BY performed_at;

DO $$
DECLARE
    afetados INT;
BEGIN
    -- Invalidez -> incapacidade permanente
    WITH renomeados AS (
        UPDATE clients
           SET benefit = 'Aposentadoria por incapacidade permanente'
         WHERE benefit = 'Aposentadoria por invalidez'
     RETURNING id
    )
    INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
    SELECT 'Client', id, 'UPDATE', NULL,
           'V17: beneficio ''Aposentadoria por invalidez'' renomeado para ''Aposentadoria por incapacidade permanente'' (mesmo beneficio, nome anterior a EC 103/2019)'
      FROM renomeados;

    GET DIAGNOSTICS afetados = ROW_COUNT;
    IF afetados > 0 THEN
        RAISE NOTICE 'V17: % cliente(s) migrados de invalidez para incapacidade permanente', afetados;
    END IF;

    -- PCD -> deficiência
    WITH renomeados AS (
        UPDATE clients
           SET benefit = 'Aposentadoria por deficiência'
         WHERE benefit = 'Aposentadoria para PCD'
     RETURNING id
    )
    INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
    SELECT 'Client', id, 'UPDATE', NULL,
           'V17: beneficio ''Aposentadoria para PCD'' renomeado para ''Aposentadoria por deficiência'' (mesmo beneficio, escrita informal)'
      FROM renomeados;

    GET DIAGNOSTICS afetados = ROW_COUNT;
    IF afetados > 0 THEN
        RAISE NOTICE 'V17: % cliente(s) migrados de PCD para deficiencia', afetados;
    END IF;
END $$;
