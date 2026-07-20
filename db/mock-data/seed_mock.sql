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
-- UUIDs fixos (v4): 9786dc7f... = Tania (ADMIN), e55affc6... = Ana (STAFF)
INSERT INTO users (id, full_name, email, password_hash, role, active) VALUES
 ('9786dc7f-6b65-465a-b07b-bdf5c152fe66', 'Tania Melo',  'dra.tania@taniamelo.adv.br', '$2a$10$vnyhmcm3Q7JJbZHPbwuhQO8EdFr4IxV/W1bJQkoGaXI4i9PrPqUcW', 'ADMIN', true),
 ('e55affc6-2e17-45b2-8e6a-0dd3be939628', 'Ana Souza',   'ana.souza@taniamelo.adv.br', '$2a$10$vnyhmcm3Q7JJbZHPbwuhQO8EdFr4IxV/W1bJQkoGaXI4i9PrPqUcW', 'STAFF', true);

-- ── Clientes ──
-- UUIDs fixos (v4), na ordem abaixo: Maria, José, Sebastiana, Antônio, Francisca
INSERT INTO clients (id, full_name, birth_date, cpf, mother_name, mobile_phone, inss_password, gender,
                     situation, benefit, rg, rg_issuer, rg_issue_date, marital_status, email, is_whatsapp,
                     reference_phone, reference_responsible, profession, nit_pis, ctps, ctps_series,
                     benefit_number, contribution_time, contribution_in_months, has_disability, client_type,
                     not_billable, notes, responsible_user_id, created_by, created_at, updated_at) VALUES
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'Maria Aparecida da Silva', '1961-03-12', '390.533.447-05', 'Josefa da Silva', '(31) 98888-1111', 'inss@123',
  'Feminino', 'Análise documental', 'Aposentadoria por idade', 'MG-11.222.333', 'SSP-MG', '1979-06-01',
  'Casado(a)', 'maria.silva@gmail.com', true, '(31) 97777-2222', 'João da Silva (filho)', 'Costureira',
  '123.45678.90-1', '12345', '00123', NULL, '16 anos, 4 meses, 10 dias', 196, false, 'Verificado',
  false, 'Cliente indicada pela paróquia.', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '90 days', now() - interval '2 days'),

 ('490c2746-a824-446b-87d5-d6c3a977f05c', 'José Carlos Pereira', '1958-11-30', '295.379.410-70', 'Antônia Pereira', '(31) 96666-3333', 'senha#Inss1',
  'Masculino', 'Planejamento em execução', 'Aposentadoria especial', 'MG-22.333.444', 'SSP-MG', '1976-02-15',
  'Divorciado(a)', 'jc.pereira@hotmail.com', true, NULL, NULL, 'Eletricista industrial',
  '234.56789.01-2', '54321', '00987', 'B-556677', '28 anos, 1 mês', 337, false, 'Verificado',
  false, 'Exposição a agentes nocivos - separar PPP e LTCAT.', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '60 days', now() - interval '1 day'),

 ('25ecf73d-4c0c-4107-952b-ce6b7969ebbb', 'Sebastiana Rocha', '1966-07-22', '834.796.430-27', 'Carmelita Rocha', '(37) 95555-4444', 'r0cha!55',
  'Feminino', 'Formulário preenchido', 'Aposentadoria rural', NULL, NULL, NULL,
  'Viúvo(a)', NULL, true, '(37) 94444-5555', 'Pedro Rocha (irmão)', 'Lavradora',
  NULL, NULL, NULL, NULL, NULL, NULL, false, 'Potencial',
  true, 'Segurada especial - atividade rural em regime de economia familiar.', 'e55affc6-2e17-45b2-8e6a-0dd3be939628', 'e55affc6-2e17-45b2-8e6a-0dd3be939628', now() - interval '20 days', now() - interval '20 days'),

 ('a8101a8b-b913-47d5-a370-2ec5a877fd0e', 'Antônio Ferreira Lima', '1972-01-05', '145.382.206-30', 'Raimunda Lima', '(31) 93333-6666', 'lima#2024',
  'Masculino', 'Benefício futuro', 'Aposentadoria por tempo de contribuição', 'MG-33.444.555', 'PC-MG', '1990-09-10',
  'Solteiro(a)', 'antonio.lima@yahoo.com.br', false, NULL, NULL, 'Motorista de caminhão',
  '345.67890.12-3', '67890', '00456', NULL, '31 anos, 8 meses, 20 dias', 381, false, 'Verificado',
  false, NULL, 'e55affc6-2e17-45b2-8e6a-0dd3be939628', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '45 days', now() - interval '5 days'),

 ('48d3794b-cb32-41ca-811a-c53282403c49', 'Francisca das Chagas Oliveira', '1959-09-14', '204.782.760-95', 'Maria das Chagas', '(31) 92222-7777', 'chagas@59',
  'Feminino', 'Benefício concluído', 'Aposentadoria por invalidez', 'MG-44.555.666', 'SSP-MG', '1981-03-25',
  'Casado(a)', NULL, true, NULL, NULL, 'Auxiliar de serviços gerais',
  '456.78901.23-4', NULL, NULL, 'B-998877', '22 anos', 264, true, 'Verificado',
  false, 'Benefício concedido em 2025 - acompanhar revisão.', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', 'e55affc6-2e17-45b2-8e6a-0dd3be939628', now() - interval '200 days', now() - interval '30 days');

-- ── Endereços (múltiplos, um principal por cliente) ──
INSERT INTO client_addresses (client_id, address_type, street, address_number, complement, neighborhood,
                              city, state, zip_code, is_primary, created_by) VALUES
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'Residencial',     'Rua das Acácias',        '128',  'Casa',      'Santa Efigênia', 'Belo Horizonte', 'MG', '30240-000', true,  '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'Correspondência', 'Av. do Contorno',        '4500', 'Sala 1203', 'Funcionários',   'Belo Horizonte', 'MG', '30110-090', false, '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('490c2746-a824-446b-87d5-d6c3a977f05c', 'Residencial',     'Rua Padre Eustáquio',    '77',   NULL,        'Padre Eustáquio','Belo Horizonte', 'MG', '30720-100', true,  '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('25ecf73d-4c0c-4107-952b-ce6b7969ebbb', 'Residencial',     'Estrada da Serrinha',    's/n',  'Sítio Boa Vista', 'Zona Rural', 'Bom Despacho', 'MG', '35600-000', true,  'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 ('a8101a8b-b913-47d5-a370-2ec5a877fd0e', 'Residencial',     'Rua dos Tupis',          '900',  'Apto 302',  'Centro',         'Belo Horizonte', 'MG', '30190-060', true,  'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 ('a8101a8b-b913-47d5-a370-2ec5a877fd0e', 'Comercial',       'Rodovia BR-381, km 480', NULL,   'Galpão 2',  'Distrito Industrial', 'Betim',    'MG', '32669-000', false, 'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 ('48d3794b-cb32-41ca-811a-c53282403c49', 'Residencial',     'Rua Itambé',             '45',   NULL,        'Floresta',       'Belo Horizonte', 'MG', '31015-180', true,  '9786dc7f-6b65-465a-b07b-bdf5c152fe66');

-- ── Histórico de situação ──
INSERT INTO client_situation_history (id, client_id, previous_situation, new_situation, changed_at, changed_by) VALUES
 (gen_random_uuid(), '7d7ff165-cb15-481b-8e9a-b755ba876ca1', NULL,                       'Formulário preenchido',     now() - interval '90 days', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 (gen_random_uuid(), '7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'Formulário preenchido',    'Análise documental',        now() - interval '70 days', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 (gen_random_uuid(), '490c2746-a824-446b-87d5-d6c3a977f05c', NULL,                       'Formulário preenchido',     now() - interval '60 days', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 (gen_random_uuid(), '490c2746-a824-446b-87d5-d6c3a977f05c', 'Formulário preenchido',    'Análise documental',        now() - interval '50 days', 'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 (gen_random_uuid(), '490c2746-a824-446b-87d5-d6c3a977f05c', 'Análise documental',       'Planejamento em execução',  now() - interval '30 days', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 (gen_random_uuid(), '25ecf73d-4c0c-4107-952b-ce6b7969ebbb', NULL,                       'Formulário preenchido',     now() - interval '20 days', 'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 (gen_random_uuid(), 'a8101a8b-b913-47d5-a370-2ec5a877fd0e', NULL,                       'Formulário preenchido',     now() - interval '45 days', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 (gen_random_uuid(), 'a8101a8b-b913-47d5-a370-2ec5a877fd0e', 'Formulário preenchido',    'Planejamento concluído',    now() - interval '25 days', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 (gen_random_uuid(), 'a8101a8b-b913-47d5-a370-2ec5a877fd0e', 'Planejamento concluído',   'Benefício futuro',          now() - interval '10 days', 'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 (gen_random_uuid(), '48d3794b-cb32-41ca-811a-c53282403c49', NULL,                       'Formulário preenchido',     now() - interval '200 days', 'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 (gen_random_uuid(), '48d3794b-cb32-41ca-811a-c53282403c49', 'Formulário preenchido',    'Benefício concluído',       now() - interval '30 days', '9786dc7f-6b65-465a-b07b-bdf5c152fe66');

-- ── Entrevistas (data, duração, conteúdo rich text) ──
INSERT INTO client_interviews (client_id, occurred_at, duration_minutes, content, created_by) VALUES
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', now() - interval '85 days', 45, '<h3>Primeira entrevista</h3><p>Cliente relatou <strong>16 anos</strong> de contribuição como costureira registrada.</p><ul><li>Trazer CTPS antiga</li><li>Verificar período rural na juventude</li></ul>', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', now() - interval '30 days', 20, '<p>Ligação de acompanhamento: documentos da CTPS recebidos. Falta comprovante de residência atualizado.</p>', 'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 ('490c2746-a824-446b-87d5-d6c3a977f05c', now() - interval '55 days', 60, '<h3>Entrevista inicial</h3><p>Atividade especial como eletricista desde 1996.</p><p><em>Pendências:</em></p><ol><li>PPP da Usiminas</li><li>LTCAT do período 2001-2010</li></ol>', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('25ecf73d-4c0c-4107-952b-ce6b7969ebbb', now() - interval '18 days', 90, '<p>Entrevista presencial com a segurada e o irmão. Atividade rural em regime de economia familiar desde 1980. Elaborar <strong>autodeclaração rural</strong>.</p>', 'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 ('a8101a8b-b913-47d5-a370-2ec5a877fd0e', now() - interval '12 days', 30, '<p>Revisão do planejamento: aguardar 14 meses para atingir o tempo mínimo com melhor fator.</p>', 'e55affc6-2e17-45b2-8e6a-0dd3be939628');

-- ── Arquivos: aba DOCUMENTOS (cobrindo os 11 tipos) ──
INSERT INTO client_files (client_id, kind, original_filename, storage_key, mime_type, file_size_bytes,
                          notes, document_type, uploaded_by, uploaded_at) VALUES
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'DOCUMENT', 'rg-maria.pdf',                  'mock/clients/1/documents/rg-maria.pdf',              'application/pdf', 182345, NULL, 'Documentos de identificação do segurado',      '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '80 days'),
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'DOCUMENT', 'comprovante-residencia.pdf',    'mock/clients/1/documents/comp-res.pdf',              'application/pdf', 90211,  'Conta de luz 05/2026', 'Documentos cadastrais / dados pessoais', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '78 days'),
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'DOCUMENT', 'ctps-completa.pdf',             'mock/clients/1/documents/ctps.pdf',                  'application/pdf', 2400000, NULL, 'Documentos de vínculo e tempo de contribuição', 'e55affc6-2e17-45b2-8e6a-0dd3be939628', now() - interval '29 days'),
 ('490c2746-a824-446b-87d5-d6c3a977f05c', 'DOCUMENT', 'gps-carne-2003-2008.pdf',       'mock/clients/2/documents/gps.pdf',                   'application/pdf', 1100500, 'Carnês de contribuinte individual', 'Contribuinte individual / facultativo', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '48 days'),
 ('490c2746-a824-446b-87d5-d6c3a977f05c', 'DOCUMENT', 'ppp-usiminas.pdf',              'mock/clients/2/documents/ppp.pdf',                   'application/pdf', 350700, 'PPP período 1996-2010', 'Atividade especial', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '40 days'),
 ('490c2746-a824-446b-87d5-d6c3a977f05c', 'DOCUMENT', 'laudo-medico-audiometria.pdf',  'mock/clients/2/documents/laudo.pdf',                 'application/pdf', 275000, NULL, 'Documentos médicos', 'e55affc6-2e17-45b2-8e6a-0dd3be939628', now() - interval '38 days'),
 ('25ecf73d-4c0c-4107-952b-ce6b7969ebbb', 'DOCUMENT', 'autodeclaracao-rural.pdf',      'mock/clients/3/documents/autodecl.pdf',              'application/pdf', 65000,  'Assinada em cartório', 'Declarações e autodeclarações', 'e55affc6-2e17-45b2-8e6a-0dd3be939628', now() - interval '15 days'),
 ('25ecf73d-4c0c-4107-952b-ce6b7969ebbb', 'DOCUMENT', 'declaracao-sindicato-rural.pdf','mock/clients/3/documents/sindicato.pdf',             'application/pdf', 88000,  NULL, 'Segurado especial', 'e55affc6-2e17-45b2-8e6a-0dd3be939628', now() - interval '15 days'),
 ('a8101a8b-b913-47d5-a370-2ec5a877fd0e', 'DOCUMENT', 'certidao-casamento.png',        'mock/clients/4/documents/certidao.png',              'image/png',       420000, NULL, 'Dependentes e relação familiar', 'e55affc6-2e17-45b2-8e6a-0dd3be939628', now() - interval '22 days'),
 ('48d3794b-cb32-41ca-811a-c53282403c49', 'DOCUMENT', 'carta-concessao-inss.pdf',      'mock/clients/5/documents/carta-concessao.pdf',       'application/pdf', 150000, 'Concessão do benefício', 'Documentos judiciais e administrativos', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '28 days'),
 ('48d3794b-cb32-41ca-811a-c53282403c49', 'DOCUMENT', 'foto-comprovante-antigo.jpg',   'mock/clients/5/documents/foto.jpg',                  'image/jpeg',      310000, 'Documento sem categoria definida', 'Outros', '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '27 days');

-- ── Arquivos: aba SIMULAÇÕES (a mais recente de cada cliente é a principal) ──
INSERT INTO client_files (client_id, kind, original_filename, storage_key, mime_type, file_size_bytes,
                          notes, simulation_date, version, vinculos, is_principal, uploaded_by, uploaded_at) VALUES
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'SIMULATION', 'simulacao-maria-v1.pdf', 'mock/clients/1/simulations/sim-v1.pdf', 'application/pdf', 210000, 'Cenário sem período rural',  (now() - interval '75 days')::date, 'v1.0.0', 3, false, '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '75 days'),
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'SIMULATION', 'simulacao-maria-v2.pdf', 'mock/clients/1/simulations/sim-v2.pdf', 'application/pdf', 215500, 'Inclui período rural 1975-1979', (now() - interval '28 days')::date, 'v1.1.0', 4, true,  'e55affc6-2e17-45b2-8e6a-0dd3be939628', now() - interval '28 days'),
 ('490c2746-a824-446b-87d5-d6c3a977f05c', 'SIMULATION', 'simulacao-jose-v1.pdf',  'mock/clients/2/simulations/sim-v1.pdf', 'application/pdf', 198000, NULL, (now() - interval '35 days')::date, 'v1.0.2', 6, true,  '9786dc7f-6b65-465a-b07b-bdf5c152fe66', now() - interval '35 days'),
 ('a8101a8b-b913-47d5-a370-2ec5a877fd0e', 'SIMULATION', 'simulacao-antonio.pdf',  'mock/clients/4/simulations/sim.pdf',    'application/pdf', 187000, 'Projeção para 2027', (now() - interval '9 days')::date, 'v2.0.0', 5, true, 'e55affc6-2e17-45b2-8e6a-0dd3be939628', now() - interval '9 days');

-- ── Parcelas de honorários (pendente, paga, atrasada e cancelada) ──
INSERT INTO client_payments (client_id, description, amount, installment_number, installment_total,
                             due_date, paid_date, status, payment_method, notes, created_by) VALUES
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'Honorários - planejamento previdenciário', 800.00, 1, 4, (now() - interval '30 days')::date, (now() - interval '28 days')::date, 'Pago',     'Pix',    NULL, '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'Honorários - planejamento previdenciário', 800.00, 2, 4, (now() - interval '1 day')::date,   NULL,                               'Pendente', NULL,     'Cliente avisou que paga na sexta', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('7d7ff165-cb15-481b-8e9a-b755ba876ca1', 'Honorários - planejamento previdenciário', 800.00, 3, 4, (now() + interval '29 days')::date, NULL,                               'Pendente', NULL,     NULL, '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('490c2746-a824-446b-87d5-d6c3a977f05c', 'Entrada contrato de êxito',                1500.00, 1, 1, (now() - interval '20 days')::date, (now() - interval '20 days')::date, 'Pago',     'Boleto', NULL, '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('a8101a8b-b913-47d5-a370-2ec5a877fd0e', 'Consultoria avulsa - revisão de CNIS',     450.00, NULL, NULL, (now() + interval '10 days')::date, NULL,                          'Pendente', NULL,     NULL, 'e55affc6-2e17-45b2-8e6a-0dd3be939628'),
 ('48d3794b-cb32-41ca-811a-c53282403c49', 'Parcela cancelada por renegociação',       600.00, 1, 2, (now() - interval '90 days')::date, NULL,                               'Cancelado', NULL,    'Substituída pelo contrato 2026-002', '9786dc7f-6b65-465a-b07b-bdf5c152fe66');

COMMIT;

-- Conferência rápida:
--   SELECT count(*) FROM clients;                              -- 5
--   SELECT kind, count(*) FROM client_files GROUP BY kind;     -- 11 documentos, 4 simulações
--   SELECT client_id, count(*) FROM client_files WHERE kind='SIMULATION' AND is_principal GROUP BY client_id;  -- 1 por cliente
