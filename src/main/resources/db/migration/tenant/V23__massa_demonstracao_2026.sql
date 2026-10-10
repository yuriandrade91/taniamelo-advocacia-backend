-- ============================================================================
-- V23 (por tenant): massa de demonstração de janeiro a outubro de 2026.
--
-- Cobre carteira (clients), pagamentos (client_payments), agenda (appointments)
-- e o que alimenta o relatório financeiro (office_revenues, office_expenses),
-- com dez meses de histórico para a linha do tempo mensal ter o que mostrar.
--
-- POR QUE AQUI, E NÃO EM db/mock-data. As massas de db/mock-data só rodam no
-- PRIMEIRO deploy de um schema: o restart-app-docker.sh pula o seed quando a
-- tabela clients já tem linha, para não apagar dado real. Entrando como
-- migration, a massa passa a vir pelo Flyway, que é o que roda em toda subida
-- e em todo schema novo.
--
-- O GUARD NÃO É DETALHE. db/migration/tenant é aplicado a TODO schema do
-- catálogo public.tenants - inclusive o de um escritório real que venha a ser
-- criado. Sem o teste de current_schema() abaixo, cadastrar um cliente novo na
-- plataforma faria nascer, junto, cinco clientes fictícios com CPF fictício no
-- schema dele. O guard restringe a massa aos dois schemas de hoje.
--
-- IDEMPOTENTE por ON CONFLICT DO NOTHING, SEM alvo de propósito. Com alvo
-- "(id)" o Postgres só ignora conflito na chave primária - e a primeira
-- aplicação em produção morreu em OUTRA restrição: ux_clients_cpf_active,
-- porque um dos CPFs escolhidos já existia no schema do escritório. Sem alvo,
-- qualquer restrição de unicidade é tolerada, que é o que "semear sem atropelar
-- o que já está lá" exige.
--
-- Os CPFs vivem na faixa 870.11x, escolhida por não aparecer em nenhuma massa
-- nem teste do repositório - os anteriores eram CPFs de teste "famosos" e por
-- isso colidiram.
--
-- created_by/responsible_user_id são resolvidos por consulta, não fixados: numa
-- migration os usuários podem ainda não existir (o AdminUserSeeder roda DEPOIS
-- do Flyway). As três colunas são nullable, então massa sem usuário é válida -
-- e nenhum login fictício é criado, que seria credencial de verdade no schema
-- do escritório.
-- ============================================================================

DO $$
DECLARE
    v_schema text := current_schema();
    v_admin  uuid;
    v_staff  uuid;
    -- Primeiro e último mês da massa. Datas fixas, não relativas a CURRENT_DATE:
    -- o pedido é "janeiro a outubro de 2026", e relatório de período fechado não
    -- deve mudar de conteúdo conforme o calendário anda.
    v_ini    date := DATE '2026-01-01';
    v_fim    date := DATE '2026-10-01';
    -- Referência de "mês corrente" para decidir o que está pago e o que está em
    -- aberto, de modo que os três baldes do relatório (vencido, a vencer, pago)
    -- fiquem todos populados.
    v_mes    date := date_trunc('month', CURRENT_DATE)::date;
    -- Resolvidos depois do guard, conforme o schema: evita repetir CASE WHEN
    -- v_schema em cada coluna de cada INSERT.
    v_cli_a  uuid;   -- cliente do contrato em 10 parcelas
    v_cli_b  uuid;   -- cliente do honorário de êxito em atraso
    v_parc   numeric;
    v_exito  numeric;
    v_rec    numeric;
BEGIN
    -- Desligado pela suíte de testes (app.seed.demo-data=false em
    -- application-test.yaml). Sem isto, a massa entra no banco de teste e os
    -- testes que afirmam contagem exata passam a depender dela.
    IF '${seedDemoData}' <> 'true' THEN
        RAISE NOTICE 'V23: seed de demonstração desligado (app.seed.demo-data=false).';
        RETURN;
    END IF;

    IF v_schema NOT IN ('tenant_demo', 'tenant_tania') THEN
        RAISE NOTICE 'V23: massa de demonstração não se aplica ao schema %; nada inserido.', v_schema;
        RETURN;
    END IF;

    -- Já semeado? Sai sem tocar em nada.
    --
    -- Pagamentos, receitas e despesas não têm id fixo (são séries de
    -- generate_series), logo não têm ON CONFLICT para protegê-los. Sem esta
    -- sentinela, reaplicar a migration num schema que já a recebeu duplicaria 63
    -- linhas. E reaplicar é exatamente o que acontece quando a V23 aplica num
    -- schema e falha em outro na mesma execução - que foi o caso.
    IF EXISTS (SELECT 1 FROM office_revenues WHERE description LIKE 'Honorários do mês%') THEN
        RAISE NOTICE 'V23: massa de demonstração já presente no schema %; nada a fazer.', v_schema;
        RETURN;
    END IF;

    SELECT id INTO v_admin FROM users WHERE role = 'ADMIN' ORDER BY created_at LIMIT 1;
    SELECT id INTO v_staff FROM users WHERE role = 'STAFF' ORDER BY created_at LIMIT 1;
    v_staff := COALESCE(v_staff, v_admin);

    IF v_schema = 'tenant_tania' THEN
        v_cli_a := 'd0000023-0000-4000-8000-000000000001';
        v_cli_b := 'd0000023-0000-4000-8000-000000000002';
        v_parc  := 480.00;
        v_exito := 2850.00;
        v_rec   := 7400.00;
    ELSE
        v_cli_a := 'd0000023-1000-4000-8000-000000000001';
        v_cli_b := 'd0000023-1000-4000-8000-000000000002';
        v_parc  := 350.00;
        v_exito := 1600.00;
        v_rec   := 4200.00;
    END IF;

    -- ──────────────────────────────────────────────────────────────────────
    -- Carteira
    --
    -- Massa distinta por tenant, de propósito: nomes, CPFs e valores diferentes
    -- em cada schema é o que prova o isolamento. Dois schemas com a mesma massa
    -- não distinguem "o filtro por tenant funciona" de "só existe um tenant".
    -- ──────────────────────────────────────────────────────────────────────
    IF v_schema = 'tenant_tania' THEN
        INSERT INTO clients
            (id, full_name, birth_date, cpf, mother_name, mobile_phone, inss_password, gender,
             situation, benefit, marital_status, email, is_whatsapp, profession, nit_pis,
             benefit_number, contribution_years, contribution_months, contribution_days,
             contribution_in_months, client_type, not_billable, responsible_user_id, created_by,
             created_at, updated_at)
        VALUES
         ('d0000023-0000-4000-8000-000000000001', 'Antônio Carlos Ribeiro', DATE '1958-03-11',
          '870.111.111-66', 'Benedita Ribeiro', '(31) 98801-0001', 'tania@inss1', 'Masculino',
          'Benefício concluído', 'Aposentadoria por tempo de contribuição', 'Casado(a)',
          'antonio.ribeiro@exemplo.com', true, 'Metalúrgico', '120.11111.11-1', 'T-200001',
          35, 4, 0, 424, 'Verificado', false, v_admin, v_admin,
          DATE '2026-01-08', DATE '2026-09-30'),
         ('d0000023-0000-4000-8000-000000000002', 'Rosângela Maria Duarte', DATE '1963-07-22',
          '870.112.222-35', 'Neusa Duarte', '(31) 98801-0002', 'tania@inss2', 'Feminino',
          'Planejamento em execução', 'Aposentadoria por idade', 'Viúvo(a)',
          'rosangela.duarte@exemplo.com', true, 'Costureira', '120.22222.22-2', NULL,
          27, 6, 0, 330, 'Verificado', false, v_staff, v_admin,
          DATE '2026-02-12', DATE '2026-10-02'),
         ('d0000023-0000-4000-8000-000000000003', 'Sebastião Alves Pinto', DATE '1966-11-05',
          '870.113.333-04', 'Terezinha Pinto', '(31) 98801-0003', 'tania@inss3', 'Masculino',
          'Análise documental', 'Aposentadoria especial', 'Solteiro(a)',
          NULL, true, 'Soldador', '120.33333.33-3', NULL,
          24, 0, 0, 288, 'Potencial', false, v_staff, v_admin,
          DATE '2026-04-03', DATE '2026-09-18'),
         ('d0000023-0000-4000-8000-000000000004', 'Marlene Souza Campos', DATE '1961-01-30',
          '870.114.444-83', 'Aparecida Campos', '(31) 98801-0004', 'tania@inss4', 'Feminino',
          'Formulário preenchido', 'Aposentadoria por idade', 'Divorciado(a)',
          'marlene.campos@exemplo.com', false, 'Doméstica', '120.44444.44-4', NULL,
          18, 3, 0, 219, 'Potencial', false, v_admin, v_admin,
          DATE '2026-06-20', DATE '2026-10-05'),
         ('d0000023-0000-4000-8000-000000000005', 'Geraldo Magela Faria', DATE '1955-09-14',
          '870.115.555-52', 'Maria Faria', '(31) 98801-0005', 'tania@inss5', 'Masculino',
          'Benefício futuro', 'Aposentadoria por tempo de contribuição', 'Casado(a)',
          NULL, true, 'Motorista', '120.55555.55-5', 'T-200005',
          38, 0, 0, 456, 'Verificado', true, v_admin, v_admin,
          DATE '2026-08-07', DATE '2026-10-08')
        ON CONFLICT DO NOTHING;
    ELSE
        INSERT INTO clients
            (id, full_name, birth_date, cpf, mother_name, mobile_phone, inss_password, gender,
             situation, benefit, marital_status, email, is_whatsapp, profession, nit_pis,
             benefit_number, contribution_years, contribution_months, contribution_days,
             contribution_in_months, client_type, not_billable, responsible_user_id, created_by,
             created_at, updated_at)
        VALUES
         ('d0000023-1000-4000-8000-000000000001', 'Juliana Prado Martins', DATE '1972-04-18',
          '870.116.666-21', 'Sueli Martins', '(11) 97701-0001', 'demo@inss1', 'Feminino',
          'Análise documental', 'Aposentadoria por idade', 'Casado(a)',
          'juliana.martins@demo.com', true, 'Enfermeira', '220.11111.11-1', NULL,
          21, 9, 0, 261, 'Verificado', false, v_admin, v_admin,
          DATE '2026-01-15', DATE '2026-09-22'),
         ('d0000023-1000-4000-8000-000000000002', 'Edson Ramalho Vieira', DATE '1960-12-02',
          '870.117.777-09', 'Lúcia Vieira', '(11) 97701-0002', 'demo@inss2', 'Masculino',
          'Planejamento concluído', 'Aposentadoria especial', 'Divorciado(a)',
          NULL, true, 'Eletricista', '220.22222.22-2', 'D-300002',
          29, 2, 0, 350, 'Verificado', false, v_staff, v_admin,
          DATE '2026-03-05', DATE '2026-10-01'),
         ('d0000023-1000-4000-8000-000000000003', 'Vera Lúcia Barreto', DATE '1968-08-25',
          '870.118.888-70', 'Dalva Barreto', '(11) 97701-0003', 'demo@inss3', 'Feminino',
          'Formulário preenchido', 'Aposentadoria por tempo de contribuição', 'Solteiro(a)',
          'vera.barreto@demo.com', true, 'Auxiliar administrativo', '220.33333.33-3', NULL,
          16, 5, 0, 197, 'Potencial', false, v_staff, v_admin,
          DATE '2026-05-11', DATE '2026-10-06')
        ON CONFLICT DO NOTHING;
    END IF;

    -- Se a carteira não entrou - porque um CPF já existia e o ON CONFLICT
    -- ignorou a linha - o resto da massa não pode entrar: pagamentos, receitas e
    -- agenda apontam para estes ids fixos e violariam a chave estrangeira,
    -- derrubando a subida. Melhor semear nada do que semear pela metade.
    IF NOT EXISTS (SELECT 1 FROM clients WHERE id = v_cli_a)
       OR NOT EXISTS (SELECT 1 FROM clients WHERE id = v_cli_b) THEN
        RAISE NOTICE
            'V23: carteira de demonstração não entrou no schema % (CPF já existente); massa'
            ' dependente pulada.', v_schema;
        RETURN;
    END IF;

    -- ──────────────────────────────────────────────────────────────────────
    -- Pagamentos do cliente: um contrato em 10 parcelas, jan a out
    --
    -- generate_series em vez de dez linhas fixas - o mês é o próprio dado, e
    -- escrevê-lo à mão dez vezes só multiplica a chance de um errado passar.
    -- Vencimento no dia 10; parcela de mês já fechado está paga, a do mês
    -- corrente fica pendente. Isso é o que faz os baldes do relatório terem,
    -- cada um, conteúdo.
    -- ──────────────────────────────────────────────────────────────────────
    INSERT INTO client_payments
        (client_id, description, amount, installment_number, installment_total,
         due_date, paid_date, status, payment_method, created_by)
    SELECT
        v_cli_a,
        'Honorário contratual — parcela ' || EXTRACT(MONTH FROM mes)::int || '/10',
        v_parc,
        EXTRACT(MONTH FROM mes)::int,
        10,
        (mes + INTERVAL '9 days')::date,
        CASE WHEN mes < v_mes THEN (mes + INTERVAL '9 days')::date END,
        CASE WHEN mes < v_mes THEN 'Pago' ELSE 'Pendente' END,
        CASE WHEN EXTRACT(MONTH FROM mes)::int % 3 = 0 THEN 'Boleto' ELSE 'Pix' END,
        v_admin
    FROM generate_series(v_ini, v_fim, INTERVAL '1 month') AS mes;

    -- Uma parcela vencida e não paga, para o balde "vencido" não ficar vazio:
    -- sem ela o relatório mostraria só pago e a vencer, e ninguém saberia se a
    -- classificação de atraso funciona.
    INSERT INTO client_payments
        (client_id, description, amount, installment_number, installment_total,
         due_date, paid_date, status, payment_method, created_by)
    VALUES
        (v_cli_b, 'Honorário de êxito — parcela única', v_exito, NULL, NULL,
         (v_mes - INTERVAL '18 days')::date, NULL, 'Pendente', 'Transferência', v_admin);

    -- ──────────────────────────────────────────────────────────────────────
    -- Receitas do escritório: jan a out
    --
    -- Esta é a área menos coberta por teste do sistema (ver
    -- docs/MELHORIAS_E_PENDENCIAS.md), então massa de verdade aqui também serve
    -- para exercitar a tela à mão enquanto o teste automatizado não existe.
    -- ──────────────────────────────────────────────────────────────────────
    INSERT INTO office_revenues
        (description, amount, due_date, paid_date, status, payment_method, client_id, created_by)
    SELECT
        'Honorários do mês — ' || to_char(mes, 'TMMonth/YYYY'),
        v_rec + EXTRACT(MONTH FROM mes)::int * 110,
        (mes + INTERVAL '14 days')::date,
        CASE WHEN mes < v_mes THEN (mes + INTERVAL '14 days')::date END,
        CASE WHEN mes < v_mes THEN 'Pago' ELSE 'Pendente' END,
        'Pix',
        NULL,
        v_admin
    FROM generate_series(v_ini, v_fim, INTERVAL '1 month') AS mes;

    -- Um parecer avulso atrasado, de novo para o balde "vencido".
    INSERT INTO office_revenues
        (description, amount, due_date, paid_date, status, payment_method, client_id, created_by)
    VALUES
        ('Parecer técnico avulso', 980.00, (v_mes - INTERVAL '22 days')::date, NULL,
         'Pendente', 'Transferência', NULL, v_staff),
        ('Receita cancelada no fechamento', 500.00, (v_mes + INTERVAL '6 days')::date, NULL,
         'Cancelado', 'Boleto', NULL, v_staff);

    -- ──────────────────────────────────────────────────────────────────────
    -- Despesas do escritório: jan a out, nas categorias que repetem todo mês
    -- ──────────────────────────────────────────────────────────────────────
    INSERT INTO office_expenses
        (description, amount, category, supplier, due_date, paid_date, status, payment_method,
         created_by)
    SELECT
        d.descricao || ' — ' || to_char(mes, 'TMMonth/YYYY'),
        d.valor,
        d.categoria,
        d.fornecedor,
        (mes + make_interval(days => d.dia))::date,
        CASE WHEN mes < v_mes THEN (mes + make_interval(days => d.dia))::date END,
        CASE WHEN mes < v_mes THEN 'Pago' ELSE 'Pendente' END,
        d.forma,
        v_admin
    FROM generate_series(v_ini, v_fim, INTERVAL '1 month') AS mes,
         (VALUES
            ('Aluguel da sala',            2400.00, 'Aluguel e condomínio',     'Imobiliária Central',  4, 'Boleto'),
            ('Salários e encargos',        6200.00, 'Salários e encargos',      NULL,                   4, 'Transferência'),
            ('Contabilidade',               890.00, 'Contabilidade e jurídico', 'Contabiliza ME',       9, 'Pix'),
            ('Assinatura do sistema',       389.90, 'Software e assinaturas',   'Jurisoft',            14, 'Cartão')
         ) AS d(descricao, valor, categoria, fornecedor, dia, forma);

    -- ──────────────────────────────────────────────────────────────────────
    -- Agenda: perícias, audiências, prazos e entrevistas ao longo dos dez meses
    --
    -- Compromisso de mês fechado entra como Concluído e o do mês corrente como
    -- Agendado; um fica Cancelado com motivo, porque cancelamento sem motivo é
    -- justamente o caso que a tela precisa saber exibir.
    -- ──────────────────────────────────────────────────────────────────────
    INSERT INTO appointments
        (id, title, type, start_at, end_at, modality, location, meeting_url, description,
         status, cancellation_reason, client_id, client_name, created_by)
    SELECT
        -- Id determinístico a partir do schema e do mês: reaplicar não duplica.
        md5(v_schema || 'ag' || to_char(mes, 'YYYYMM') || a.ord)::uuid,
        a.titulo || ' — ' || to_char(mes, 'TMMonth'),
        a.tipo,
        (mes + make_interval(days => a.dia) + a.hora)::timestamptz,
        (mes + make_interval(days => a.dia) + a.hora + INTERVAL '1 hour')::timestamptz,
        a.modalidade,
        CASE WHEN a.modalidade = 'Presencial' THEN 'Escritório — Sala 1' END,
        CASE WHEN a.modalidade = 'Online' THEN 'https://meet.google.com/exemplo-demo' END,
        a.descricao,
        CASE WHEN mes < v_mes THEN 'Concluído' ELSE 'Agendado' END,
        NULL,
        NULL,
        a.nome_livre,
        v_admin
    FROM generate_series(v_ini, v_fim, INTERVAL '1 month') AS mes,
         (VALUES
            ('1', 'Perícia médica INSS',   'Perícia',   12, TIME '09:00', 'Presencial',
             'Perícia agendada pelo INSS.',                 'Periciando do mês'),
            ('2', 'Audiência de instrução','Audiência', 19, TIME '14:00', 'Presencial',
             'Audiência na vara previdenciária.',           'Parte do processo'),
            ('3', 'Entrevista inicial',    'Entrevista', 6, TIME '10:30', 'Online',
             'Levantamento de documentos com o cliente.',   'Entrevistado do mês')
         ) AS a(ord, titulo, tipo, dia, hora, modalidade, descricao, nome_livre)
    ON CONFLICT DO NOTHING;

    -- Um cancelado, com motivo preenchido.
    INSERT INTO appointments
        (id, title, type, start_at, end_at, modality, location, meeting_url, description,
         status, cancellation_reason, client_id, client_name, created_by)
    VALUES
        (md5(v_schema || 'ag-cancelado')::uuid,
         'Reunião de alinhamento (cancelada)', 'Reunião',
         (v_mes + INTERVAL '8 days' + TIME '16:00')::timestamptz,
         (v_mes + INTERVAL '8 days' + TIME '17:00')::timestamptz,
         'Online', NULL, 'https://meet.google.com/exemplo-demo',
         'Remarcada a pedido do cliente.', 'Cancelado',
         'Cliente pediu para remarcar após a perícia.', NULL, 'Cliente do mês', v_staff)
    ON CONFLICT DO NOTHING;

    RAISE NOTICE 'V23: massa de jan-out/2026 aplicada ao schema %.', v_schema;
END $$;
