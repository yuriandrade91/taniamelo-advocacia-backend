-- ============================================================================
-- V25 (por tenant): preenche a etapa do INSS na massa de demonstração.
--
-- POR QUE NÃO EDITEI A V23. Ela já está aplicada - aqui e na instância - e
-- alterar um arquivo de migration aplicado muda o checksum, que é exatamente o
-- que derrubou o deploy de 10/10/2026 ("Migration checksum mismatch for
-- migration version 23"). Migration aplicada é imutável; dado novo entra em
-- migration nova.
--
-- Os mesmos dois guards da V23, pelos mesmos motivos: o placeholder mantém a
-- massa fora do banco de teste, e o current_schema() impede que um escritório
-- real criado no futuro receba dado fictício.
--
-- IDEMPOTENTE por construção: é UPDATE com WHERE por id, não INSERT. Reaplicar
-- grava o mesmo valor.
-- ============================================================================

DO $$
DECLARE
    v_schema text := current_schema();
BEGIN
    IF '${seedDemoData}' <> 'true' THEN
        RAISE NOTICE 'V25: seed de demonstração desligado; etapa do INSS não preenchida.';
        RETURN;
    END IF;

    IF v_schema NOT IN ('tenant_demo', 'tenant_tania') THEN
        RAISE NOTICE 'V25: não se aplica ao schema %; nada alterado.', v_schema;
        RETURN;
    END IF;

    -- Uma etapa por cliente, cobrindo a esteira inteira para a tela ter o que
    -- mostrar em cada filtro. Dois ficam NULOS de propósito: cliente em
    -- prospecção não protocolou requerimento, e esse é o estado mais comum na
    -- base real - uma massa em que todo mundo tem etapa esconderia o caso.
    IF v_schema = 'tenant_tania' THEN
        UPDATE clients SET request_stage = 'Deferimento'
         WHERE id = 'd0000023-0000-4000-8000-000000000001';   -- benefício concluído
        UPDATE clients SET request_stage = 'Exigência'
         WHERE id = 'd0000023-0000-4000-8000-000000000002';   -- aguardando documento
        UPDATE clients SET request_stage = 'Requerimento'
         WHERE id = 'd0000023-0000-4000-8000-000000000003';   -- acabou de protocolar
        -- 004 fica nulo: em análise documental, ainda não foi ao INSS.
        UPDATE clients SET request_stage = 'Recurso'
         WHERE id = 'd0000023-0000-4000-8000-000000000005';   -- indeferido, recorrendo
    ELSE
        UPDATE clients SET request_stage = 'Cumprimento de exigência'
         WHERE id = 'd0000023-1000-4000-8000-000000000001';
        UPDATE clients SET request_stage = 'Indeferimento'
         WHERE id = 'd0000023-1000-4000-8000-000000000002';
        -- 003 fica nulo: potencial, sem requerimento.
    END IF;

    RAISE NOTICE 'V25: etapa do INSS preenchida na massa do schema %.', v_schema;
END $$;
