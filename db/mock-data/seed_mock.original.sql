-- ============================================================================
-- SEED DE MOCK - popula TODAS as tabelas para exercitar TODOS os endpoints.
--
-- Uso (banco já migrado pelo Flyway):
--   psql "$DATABASE_URL" -f db/mock-data/seed_mock.sql
--
-- Logins criados (senha de todos: password):
--   dra.tania@taniamelo.adv.br  (ADMIN)
--   ana.souza@taniamelo.adv.br  (STAFF)
--
-- Observações:
--  - Todas as PKs (users, clients, client_addresses, client_interviews,
--    client_files, client_payments, client_situation_history) são UUID.
--    users e clients usam UUIDs literais fixos abaixo para que as demais
--    tabelas possam referenciá-los via FK dentro do próprio script; as
--    demais tabelas usam gen_random_uuid() por não serem referenciadas.
--  - inss_password está em texto puro: o CryptoConverter tem fallback de
--    leitura para valores não criptografados (dado de mock, nunca produção).
--  - storage_key dos arquivos aponta para caminhos fictícios: listagem,
--    detalhe e edição funcionam; o download retorna erro de storage (esperado
--    em mock - suba um arquivo real via POST para testar download).
-- ============================================================================

BEGIN;

TRUNCATE client_payments, client_files, client_interviews, client_situation_history,
         client_addresses, clients, users RESTART IDENTITY CASCADE;

-- ── Usuários ──  (hash BCrypt de "password")
-- UUIDs fixos: ...179001 = Tania (ADMIN), ...179002 = Ana (STAFF)
INSERT INTO users (id, full_name, email, password_hash, role, active) VALUES
 ('123e4567-e89b-12d3-a456-426614179001', 'Tania Melo',  'dra.tania@taniamelo.adv.br', '$2a$10$vnyhmcm3Q7JJbZHPbwuhQO8EdFr4IxV/W1bJQkoGaXI4i9PrPqUcW', 'ADMIN', true),
 ('123e4567-e89b-12d3-a456-426614179002', 'Ana Souza',   'ana.souza@taniamelo.adv.br', '$2a$10$vnyhmcm3Q7JJbZHPbwuhQO8EdFr4IxV/W1bJQkoGaXI4i9PrPqUcW', 'STAFF', true);

-- ── Clientes ──
-- UUIDs fixos: ...174001 a ...174005, na ordem abaixo
INSERT INTO clients (id, full_name, birth_date, cpf, mother_name, mobile_phone, inss_password, gender,
                     situation, benefit, rg, rg_issuer, rg_issue_date, marital_status, email, is_whatsapp,
                     reference_phone, reference_responsible, profession, nit_pis, ctps, ctps_series,
                     benefit_number, contribution_time, contribution_in_months, has_disability, client_type,
                     not_billable, notes, responsible_user_id, created_by, created_at, updated_at) VALUES
 ('123e4567-e89b-12d3-a456-426614174001', 'Maria Aparecida da Silva', '1961-03-12', '390.533.447-05', 'Josefa da Silva', '(31) 98888-1111', 'inss@123',
  'Feminino', 'Análise documental', 'Aposentadoria por idade', 'MG-11.222.333', 'SSP-MG', '1979-06-01',
  'Casado(a)', 'maria.silva@gmail.com', true, '(31) 97777-2222', 'João da Silva (filho)', 'Costureira',
  '123.45678.90-1', '12345', '00123', NULL, '16 anos, 4 meses, 10 dias', 196, false, 'Verificado',
  false, 'Cliente indicada pela paróquia.', '123e4567-e89b-12d3-a456-426614179001', '123e4567-e89b-12d3-a456-426614179001', now() - interval '90 days', now() - interval '2 days'),

 ('123e4567-e89b-12d3-a456-426614174002', 'José Carlos Pereira', '1958-11-30', '295.379.410-70', 'Antônia Pereira', '(31) 96666-3333', 'senha#Inss1',
  'Masculino', 'Planejamento em execução', 'Aposentadoria especial', 'MG-22.333.444', 'SSP-MG', '1976-02-15',
  'Divorciado(a)', 'jc.pereira@hotmail.com', true, NULL, NULL, 'Eletricista industrial',
  '234.56789.01-2', '54321', '00987', 'B-556677', '28 anos, 1 mês', 337, false, 'Verificado',
  false, 'Exposição a agentes nocivos - separar PPP e LTCAT.', '123e4567-e89b-12d3-a456-426614179001', '123e4567-e89b-12d3-a456-426614179001', now() - interval '60 days', now() - interval '1 day'),

 ('123e4567-e89b-12d3-a456-426614174003', 'Sebastiana Rocha', '1966-07-22', '834.796.430-27', 'Carmelita Rocha', '(37) 95555-4444', 'r0cha!55',
  'Feminino', 'Formulário preenchido', 'Aposentadoria rural', NULL, NULL, NULL,
  'Viúvo(a)', NULL, true, '(37) 94444-5555', 'Pedro Rocha (irmão)', 'Lavradora',
  NULL, NULL, NULL, NULL, NULL, NULL, false, 'Potencial',
  true, 'Segurada especial - atividade rural em regime de economia familiar.', '123e4567-e89b-12d3-a456-426614179002', '123e4567-e89b-12d3-a456-426614179002', now() - interval '20 days', now() - interval '20 days'),

 ('123e4567-e89b-12d3-a456-426614174004', 'Antônio Ferreira Lima', '1972-01-05', '145.382.206-30', 'Raimunda Lima', '(31) 93333-6666', 'lima#2024',
  'Masculino', 'Benefício futuro', 'Aposentadoria por tempo de contribuição', 'MG-33.444.555', 'PC-MG', '1990-09-10',
  'Solteiro(a)', 'antonio.lima@yahoo.com.br', false, NULL, NULL, 'Motorista de caminhão',
  '345.67890.12-3', '67890', '00456', NULL, '31 anos, 8 meses, 20 dias', 381, false, 'Verificado',
  false, NULL, '123e4567-e89b-12d3-a456-426614179002', '123e4567-e89b-12d3-a456-426614179001', now() - interval '45 days', now() - interval '5 days'),

 ('123e4567-e89b-12d3-a456-426614174005', 'Francisca das Chagas Oliveira', '1959-09-14', '204.782.760-95', 'Maria das Chagas', '(31) 92222-7777', 'chagas@59',
  'Feminino', 'Benefício concluído', 'Aposentadoria por invalidez', 'MG-44.555.666', 'SSP-MG', '1981-03-25',
  'Casado(a)', NULL, true, NULL, NULL, 'Auxiliar de serviços gerais',
  '456.78901.23-4', NULL, NULL, 'B-998877', '22 anos', 264, true, 'Verificado',
  false, 'Benefício concedido em 2025 - acompanhar revisão.', '123e4567-e89b-12d3-a456-426614179001', '123e4567-e89b-12d3-a456-426614179002', now() - interval '200 days', now() - interval '30 days');

-- ── Endereços (múltiplos, um principal por cliente) ──
INSERT INTO client_addresses (client_id, address_type, street, address_number, complement, neighborhood,
                              city, state, zip_code, is_primary, created_by) VALUES
 ('123e4567-e89b-12d3-a456-426614174001', 'Residencial',     'Rua das Acácias',        '128',  'Casa',      'Santa Efigênia', 'Belo Horizonte', 'MG', '30240-000', true,  '123e4567-e89b-12d3-a456-426614179001'),
 ('123e4567-e89b-12d3-a456-426614174001', 'Correspondência', 'Av. do Contorno',        '4500', 'Sala 1203', 'Funcionários',   'Belo Horizonte', 'MG', '30110-090', false, '123e4567-e89b-12d3-a456-426614179001'),
 ('123e4567-e89b-12d3-a456-426614174002', 'Residencial',     'Rua Padre Eustáquio',    '77',   NULL,        'Padre Eustáquio','Belo Horizonte', 'MG', '30720-100', true,  '123e4567-e89b-12d3-a456-426614179001'),
 ('123e4567-e89b-12d3-a456-426614174003', 'Residencial',     'Estrada da Serrinha',    's/n',  'Sítio Boa Vista', 'Zona Rural', 'Bom Despacho', 'MG', '35600-000', true,  '123e4567-e89b-12d3-a456-426614179002'),
 ('123e4567-e89b-12d3-a456-426614174004', 'Residencial',     'Rua dos Tupis',          '900',  'Apto 302',  'Centro',         'Belo Horizonte', 'MG', '30190-060', true,  '123e4567-e89b-12d3-a456-426614179002'),
 ('123e4567-e89b-12d3-a456-426614174004', 'Comercial',       'Rodovia BR-381, km 480', NULL,   'Galpão 2',  'Distrito Industrial', 'Betim',    'MG', '32669-000', false, '123e4567-e89b-12d3-a456-426614179002'),
 ('123e4567-e89b-12d3-a456-426614174005', 'Residencial',     'Rua Itambé',             '45',   NULL,        'Floresta',       'Belo Horizonte', 'MG', '31015-180', true,  '123e4567-e89b-12d3-a456-426614179001');

-- ── Histórico de situação ──
INSERT INTO client_situation_history (id, client_id, previous_situation, new_situation, changed_at, changed_by) VALUES
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174001', NULL,                       'Formulário preenchido',     now() - interval '90 days', '123e4567-e89b-12d3-a456-426614179001'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174001', 'Formulário preenchido',    'Análise documental',        now() - interval '70 days', '123e4567-e89b-12d3-a456-426614179001'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174002', NULL,                       'Formulário preenchido',     now() - interval '60 days', '123e4567-e89b-12d3-a456-426614179001'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174002', 'Formulário preenchido',    'Análise documental',        now() - interval '50 days', '123e4567-e89b-12d3-a456-426614179002'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174002', 'Análise documental',       'Planejamento em execução',  now() - interval '30 days', '123e4567-e89b-12d3-a456-426614179001'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174003', NULL,                       'Formulário preenchido',     now() - interval '20 days', '123e4567-e89b-12d3-a456-426614179002'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174004', NULL,                       'Formulário preenchido',     now() - interval '45 days', '123e4567-e89b-12d3-a456-426614179001'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174004', 'Formulário preenchido',    'Planejamento concluído',    now() - interval '25 days', '123e4567-e89b-12d3-a456-426614179001'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174004', 'Planejamento concluído',   'Benefício futuro',          now() - interval '10 days', '123e4567-e89b-12d3-a456-426614179002'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174005', NULL,                       'Formulário preenchido',     now() - interval '200 days', '123e4567-e89b-12d3-a456-426614179002'),
 (gen_random_uuid(), '123e4567-e89b-12d3-a456-426614174005', 'Formulário preenchido',    'Benefício concluído',       now() - interval '30 days', '123e4567-e89b-12d3-a456-426614179001');

-- ── Entrevistas (data, duração, conteúdo rich text) ──
INSERT INTO client_interviews (client_id, occurred_at, duration_minutes, content, created_by) VALUES
 ('123e4567-e89b-12d3-a456-426614174001', now() - interval '85 days', 45, '<h3>Primeira entrevista</h3><p>Cliente relatou <strong>16 anos</strong> de contribuição como costureira registrada.</p><ul><li>Trazer CTPS antiga</li><li>Verificar período rural na juventude</li></ul>', '123e4567-e89b-12d3-a456-426614179001'),
 ('123e4567-e89b-12d3-a456-426614174001', now() - interval '30 days', 20, '<p>Ligação de acompanhamento: documentos da CTPS recebidos. Falta comprovante de residência atualizado.</p>', '123e4567-e89b-12d3-a456-426614179002'),
 ('123e4567-e89b-12d3-a456-426614174002', now() - interval '55 days', 60, '<h3>Entrevista inicial</h3><p>Atividade especial como eletricista desde 1996.</p><p><em>Pendências:</em></p><ol><li>PPP da Usiminas</li><li>LTCAT do período 2001-2010</li></ol>', '123e4567-e89b-12d3-a456-426614179001'),
 ('123e4567-e89b-12d3-a456-426614174003', now() - interval '18 days', 90, '<p>Entrevista presencial com a segurada e o irmão. Atividade rural em regime de economia familiar desde 1980. Elaborar <strong>autodeclaração rural</strong>.</p>', '123e4567-e89b-12d3-a456-426614179002'),
 ('123e4567-e89b-12d3-a456-426614174004', now() - interval '12 days', 30, '<p>Revisão do planejamento: aguardar 14 meses para atingir o tempo mínimo com melhor fator.</p>', '123e4567-e89b-12d3-a456-426614179002');

-- ── Arquivos: aba DOCUMENTOS (cobrindo os 11 tipos) ──
INSERT INTO client_files (client_id, kind, original_filename, storage_key, mime_type, file_size_bytes,
                          notes, document_type, uploaded_by, uploaded_at) VALUES
 ('123e4567-e89b-12d3-a456-426614174001', 'DOCUMENT', 'rg-maria.pdf',                  'mock/clients/1/documents/rg-maria.pdf',              'application/pdf', 182345, NULL, 'Documentos de identificação do segurado',      '123e4567-e89b-12d3-a456-426614179001', now() - interval '80 days'),
 ('123e4567-e89b-12d3-a456-426614174001', 'DOCUMENT', 'comprovante-residencia.pdf',    'mock/clients/1/documents/comp-res.pdf',              'application/pdf', 90211,  'Conta de luz 05/2026', 'Documentos cadastrais / dados pessoais', '123e4567-e89b-12d3-a456-426614179001', now() - interval '78 days'),
 ('123e4567-e89b-12d3-a456-426614174001', 'DOCUMENT', 'ctps-completa.pdf',             'mock/clients/1/documents/ctps.pdf',                  'application/pdf', 2400000, NULL, 'Documentos de vínculo e tempo de contribuição', '123e4567-e89b-12d3-a456-426614179002', now() - interval '29 days'),
 ('123e4567-e89b-12d3-a456-426614174002', 'DOCUMENT', 'gps-carne-2003-2008.pdf',       'mock/clients/2/documents/gps.pdf',                   'application/pdf', 1100500, 'Carnês de contribuinte individual', 'Contribuinte individual / facultativo', '123e4567-e89b-12d3-a456-426614179001', now() - interval '48 days'),
 ('123e4567-e89b-12d3-a456-426614174002', 'DOCUMENT', 'ppp-usiminas.pdf',              'mock/clients/2/documents/ppp.pdf',                   'application/pdf', 350700, 'PPP período 1996-2010', 'Atividade especial', '123e4567-e89b-12d3-a456-426614179001', now() - interval '40 days'),
 ('123e4567-e89b-12d3-a456-426614174002', 'DOCUMENT', 'laudo-medico-audiometria.pdf',  'mock/clients/2/documents/laudo.pdf',                 'application/pdf', 275000, NULL, 'Documentos médicos', '123e4567-e89b-12d3-a456-426614179002', now() - interval '38 days'),
 ('123e4567-e89b-12d3-a456-426614174003', 'DOCUMENT', 'autodeclaracao-rural.pdf',      'mock/clients/3/documents/autodecl.pdf',              'application/pdf', 65000,  'Assinada em cartório', 'Declarações e autodeclarações', '123e4567-e89b-12d3-a456-426614179002', now() - interval '15 days'),
 ('123e4567-e89b-12d3-a456-426614174003', 'DOCUMENT', 'declaracao-sindicato-rural.pdf','mock/clients/3/documents/sindicato.pdf',             'application/pdf', 88000,  NULL, 'Segurado especial', '123e4567-e89b-12d3-a456-426614179002', now() - interval '15 days'),
 ('123e4567-e89b-12d3-a456-426614174004', 'DOCUMENT', 'certidao-casamento.png',        'mock/clients/4/documents/certidao.png',              'image/png',       420000, NULL, 'Dependentes e relação familiar', '123e4567-e89b-12d3-a456-426614179002', now() - interval '22 days'),
 ('123e4567-e89b-12d3-a456-426614174005', 'DOCUMENT', 'carta-concessao-inss.pdf',      'mock/clients/5/documents/carta-concessao.pdf',       'application/pdf', 150000, 'Concessão do benefício', 'Documentos judiciais e administrativos', '123e4567-e89b-12d3-a456-426614179001', now() - interval '28 days'),
 ('123e4567-e89b-12d3-a456-426614174005', 'DOCUMENT', 'foto-comprovante-antigo.jpg',   'mock/clients/5/documents/foto.jpg',                  'image/jpeg',      310000, 'Documento sem categoria definida', 'Outros', '123e4567-e89b-12d3-a456-426614179001', now() - interval '27 days');

-- ── Arquivos: aba SIMULAÇÕES (a mais recente de cada cliente é a principal) ──
INSERT INTO client_files (client_id, kind, original_filename, storage_key, mime_type, file_size_bytes,
                          notes, simulation_date, version, vinculos, is_principal, uploaded_by, uploaded_at) VALUES
 ('123e4567-e89b-12d3-a456-426614174001', 'SIMULATION', 'simulacao-maria-v1.pdf', 'mock/clients/1/simulations/sim-v1.pdf', 'application/pdf', 210000, 'Cenário sem período rural',  (now() - interval '75 days')::date, 'v1.0.0', 3, false, '123e4567-e89b-12d3-a456-426614179001', now() - interval '75 days'),
 ('123e4567-e89b-12d3-a456-426614174001', 'SIMULATION', 'simulacao-maria-v2.pdf', 'mock/clients/1/simulations/sim-v2.pdf', 'application/pdf', 215500, 'Inclui período rural 1975-1979', (now() - interval '28 days')::date, 'v1.1.0', 4, true,  '123e4567-e89b-12d3-a456-426614179002', now() - interval '28 days'),
 ('123e4567-e89b-12d3-a456-426614174002', 'SIMULATION', 'simulacao-jose-v1.pdf',  'mock/clients/2/simulations/sim-v1.pdf', 'application/pdf', 198000, NULL, (now() - interval '35 days')::date, 'v1.0.2', 6, true,  '123e4567-e89b-12d3-a456-426614179001', now() - interval '35 days'),
 ('123e4567-e89b-12d3-a456-426614174004', 'SIMULATION', 'simulacao-antonio.pdf',  'mock/clients/4/simulations/sim.pdf',    'application/pdf', 187000, 'Projeção para 2027', (now() - interval '9 days')::date, 'v2.0.0', 5, true, '123e4567-e89b-12d3-a456-426614179002', now() - interval '9 days');

-- ── Parcelas de honorários (pendente, paga, atrasada e cancelada) ──
INSERT INTO client_payments (client_id, description, amount, installment_number, installment_total,
                             due_date, paid_date, status, payment_method, notes, created_by) VALUES
 ('123e4567-e89b-12d3-a456-426614174001', 'Honorários - planejamento previdenciário', 800.00, 1, 4, (now() - interval '30 days')::date, (now() - interval '28 days')::date, 'Pago',     'Pix',    NULL, '123e4567-e89b-12d3-a456-426614179001'),
 ('123e4567-e89b-12d3-a456-426614174001', 'Honorários - planejamento previdenciário', 800.00, 2, 4, (now() - interval '1 day')::date,   NULL,                               'Pendente', NULL,     'Cliente avisou que paga na sexta', '123e4567-e89b-12d3-a456-426614179001'),
 ('123e4567-e89b-12d3-a456-426614174001', 'Honorários - planejamento previdenciário', 800.00, 3, 4, (now() + interval '29 days')::date, NULL,                               'Pendente', NULL,     NULL, '123e4567-e89b-12d3-a456-426614179001'),
 ('123e4567-e89b-12d3-a456-426614174002', 'Entrada contrato de êxito',                1500.00, 1, 1, (now() - interval '20 days')::date, (now() - interval '20 days')::date, 'Pago',     'Boleto', NULL, '123e4567-e89b-12d3-a456-426614179001'),
 ('123e4567-e89b-12d3-a456-426614174004', 'Consultoria avulsa - revisão de CNIS',     450.00, NULL, NULL, (now() + interval '10 days')::date, NULL,                          'Pendente', NULL,     NULL, '123e4567-e89b-12d3-a456-426614179002'),
 ('123e4567-e89b-12d3-a456-426614174005', 'Parcela cancelada por renegociação',       600.00, 1, 2, (now() - interval '90 days')::date, NULL,                               'Cancelado', NULL,    'Substituída pelo contrato 2026-002', '123e4567-e89b-12d3-a456-426614179001');

COMMIT;

-- Conferência rápida:
--   SELECT count(*) FROM clients;                              -- 5
--   SELECT kind, count(*) FROM client_files GROUP BY kind;     -- 11 documentos, 4 simulações
--   SELECT client_id, count(*) FROM client_files WHERE kind='SIMULATION' AND is_principal GROUP BY client_id;  -- 1 por cliente
