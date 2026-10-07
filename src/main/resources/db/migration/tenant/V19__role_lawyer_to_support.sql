-- V19: o papel LAWYER passa a se chamar SUPPORT.
--
-- O enum Role é gravado pelo nome (EnumType.STRING), então um usuário que já
-- estava como 'LAWYER' deixaria de carregar assim que o enum perdesse a
-- constante. Esta migration renomeia o que já está gravado; as permissões são
-- as mesmas de antes (@RequerAdvogado: ADMIN ou SUPPORT).
--
-- Cada linha mexida vira registro em audit_log, com performed_by nulo (foi o
-- sistema). Para conferir depois:
--   SELECT * FROM audit_log WHERE detail LIKE 'V19:%' ORDER BY performed_at;

DO $$
DECLARE
    afetados INT;
BEGIN
    WITH renomeados AS (
        UPDATE users
           SET role = 'SUPPORT'
         WHERE role = 'LAWYER'
     RETURNING id
    )
    INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
    SELECT 'User', id, 'UPDATE', NULL,
           'V19: papel ''LAWYER'' renomeado para ''SUPPORT'' (mesmas permissoes)'
      FROM renomeados;

    GET DIAGNOSTICS afetados = ROW_COUNT;
    IF afetados > 0 THEN
        RAISE NOTICE 'V19: % usuario(s) migrados de LAWYER para SUPPORT', afetados;
    END IF;
END $$;
