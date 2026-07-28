-- ============================================================================
-- Massa do tenant "demo" (schema tenant_demo) - Escritório Demonstração.
-- Massa DISTINTA e menor que a do tenant tania, para provar o isolamento por
-- schema: outros usuários, outros clientes, outros valores. Rode:
--   psql "$DATABASE_URL" -f db/mock-data/seed_tenant_demo.sql
--
-- Logins deste tenant (senha: password):
--   dr.almeida@escritoriodemo.adv.br  (ADMIN)
--   bruna.lima@escritoriodemo.adv.br  (STAFF)
-- ============================================================================
SET search_path TO tenant_demo, public;

BEGIN;

TRUNCATE refresh_tokens, client_payments, client_files, client_interviews,
         client_situation_history, client_addresses, clients, users RESTART IDENTITY CASCADE;

-- ── Usuários ── (hash BCrypt de "password")
INSERT INTO users (id, full_name, email, username, password_hash, role, active) VALUES
 ('a1a1a1a1-0000-4000-8000-000000000001', 'Rafael Almeida', 'dr.almeida@escritoriodemo.adv.br', 'dr.almeida', '$2a$10$vnyhmcm3Q7JJbZHPbwuhQO8EdFr4IxV/W1bJQkoGaXI4i9PrPqUcW', 'ADMIN', true),
 ('a1a1a1a1-0000-4000-8000-000000000002', 'Bruna Lima',    'bruna.lima@escritoriodemo.adv.br', 'bruna.lima', '$2a$10$vnyhmcm3Q7JJbZHPbwuhQO8EdFr4IxV/W1bJQkoGaXI4i9PrPqUcW', 'STAFF', true);

-- ── Clientes ── (6, distintos dos do tenant tania)
INSERT INTO clients (id, full_name, birth_date, cpf, mother_name, mobile_phone, inss_password, gender,
                     situation, benefit, marital_status, email, is_whatsapp, profession, nit_pis,
                     benefit_number, contribution_time, contribution_in_months, client_type,
                     not_billable, responsible_user_id, created_by, created_at, updated_at) VALUES
 ('c0000001-0000-4000-8000-000000000001', 'Paulo Henrique Nogueira', '1959-05-04', '111.222.333-96', 'Alice Nogueira', '(11) 90000-0001', 'demo@inss1', 'Masculino',
  'Análise documental', 'Aposentadoria por tempo de contribuição', 'Casado(a)', 'paulo.nogueira@demo.com', true, 'Torneiro mecânico',
  '111.11111.11-1', 'D-100001', '31 anos, 2 meses', 374, 'Verificado', false,
  'a1a1a1a1-0000-4000-8000-000000000001', 'a1a1a1a1-0000-4000-8000-000000000001', now() - interval '40 days', now() - interval '3 days'),
 ('c0000001-0000-4000-8000-000000000002', 'Cláudia Regina Fonseca', '1964-09-19', '222.333.444-07', 'Marta Fonseca', '(11) 90000-0002', 'demo@inss2', 'Feminino',
  'Planejamento em execução', 'Aposentadoria por idade', 'Divorciado(a)', 'claudia.fonseca@demo.com', true, 'Professora',
  '222.22222.22-2', 'D-100002', '25 anos', 300, 'Verificado', false,
  'a1a1a1a1-0000-4000-8000-000000000002', 'a1a1a1a1-0000-4000-8000-000000000001', now() - interval '30 days', now() - interval '1 day'),
 ('c0000001-0000-4000-8000-000000000003', 'Marcos Vinícius Teles', '1970-01-27', '333.444.555-18', 'Rosa Teles', '(21) 90000-0003', 'demo@inss3', 'Masculino',
  'Formulário preenchido', 'Aposentadoria especial', 'Solteiro(a)', NULL, true, 'Soldador',
  '333.33333.33-3', NULL, '22 anos, 6 meses', 270, 'Potencial', false,
  'a1a1a1a1-0000-4000-8000-000000000002', 'a1a1a1a1-0000-4000-8000-000000000001', now() - interval '20 days', now() - interval '5 days'),
 ('c0000001-0000-4000-8000-000000000004', 'Isabel Cristina Ramos', '1957-12-08', '444.555.666-29', 'Lúcia Ramos', '(31) 90000-0004', 'demo@inss4', 'Feminino',
  'Benefício futuro', 'Aposentadoria rural', 'Viúvo(a)', NULL, true, 'Lavradora',
  NULL, 'D-100004', '15 anos', 180, 'Verificado', true,
  'a1a1a1a1-0000-4000-8000-000000000001', 'a1a1a1a1-0000-4000-8000-000000000001', now() - interval '60 days', now() - interval '2 days'),
 ('c0000001-0000-4000-8000-000000000005', 'Gustavo Prado Martins', '1968-07-14', '555.666.777-30', 'Sônia Martins', '(41) 90000-0005', 'demo@inss5', 'Masculino',
  'Planejamento concluído', 'Aposentadoria por incapacidade permanente', 'Casado(a)', 'gustavo.martins@demo.com', true, 'Motorista',
  '555.55555.55-5', 'D-100005', '19 anos, 3 meses', 231, 'Verificado', false,
  'a1a1a1a1-0000-4000-8000-000000000002', 'a1a1a1a1-0000-4000-8000-000000000001', now() - interval '15 days', now() - interval '1 day'),
 ('c0000001-0000-4000-8000-000000000006', 'Renata Aparecida Dias', '1975-03-22', '666.777.888-41', 'Vera Dias', '(51) 90000-0006', 'demo@inss6', 'Feminino',
  'Benefício concluído', 'Aposentadoria por deficiência', 'Casado(a)', 'renata.dias@demo.com', true, 'Auxiliar administrativo',
  '666.66666.66-6', 'D-100006', '27 anos', 324, 'Verificado', false,
  'a1a1a1a1-0000-4000-8000-000000000001', 'a1a1a1a1-0000-4000-8000-000000000001', now() - interval '90 days', now() - interval '10 days');

-- ── Endereços ──
INSERT INTO client_addresses (client_id, address_type, street, address_number, neighborhood, city, state, zip_code, is_primary, created_by) VALUES
 ('c0000001-0000-4000-8000-000000000001', 'Residencial', 'Rua das Palmeiras', '120', 'Centro', 'São Paulo', 'SP', '01010-000', true, 'a1a1a1a1-0000-4000-8000-000000000001'),
 ('c0000001-0000-4000-8000-000000000002', 'Residencial', 'Av. Brasil', '3400', 'Jardim', 'Campinas', 'SP', '13010-000', true, 'a1a1a1a1-0000-4000-8000-000000000001');

-- ── Entrevista ──
INSERT INTO client_interviews (client_id, occurred_at, duration_minutes, content, created_by) VALUES
 ('c0000001-0000-4000-8000-000000000001', now() - interval '35 days', 45, '<p>Primeira entrevista - levantamento de vínculos (demo).</p>', 'a1a1a1a1-0000-4000-8000-000000000001');

-- ── Arquivo (documento) ──
INSERT INTO client_files (client_id, kind, original_filename, storage_key, mime_type, file_size_bytes, document_type, uploaded_by) VALUES
 ('c0000001-0000-4000-8000-000000000001', 'DOCUMENT', 'rg_paulo.pdf', 'demo/mock/rg_paulo.pdf', 'application/pdf', 84210, 'Documentos de identificação do segurado', 'a1a1a1a1-0000-4000-8000-000000000001');

-- ── Pagamentos ── (um atrasado, um a vencer - útil para testar notificações)
INSERT INTO client_payments (client_id, description, amount, installment_number, installment_total, due_date, status, payment_method, created_by) VALUES
 ('c0000001-0000-4000-8000-000000000001', 'Honorários - parcela 1/3', 500.00, 1, 3, CURRENT_DATE - 12, 'Pendente', 'Pix', 'a1a1a1a1-0000-4000-8000-000000000001'),
 ('c0000001-0000-4000-8000-000000000002', 'Honorários - parcela 1/6', 300.00, 1, 6, CURRENT_DATE, 'Pendente', 'Boleto', 'a1a1a1a1-0000-4000-8000-000000000001');

-- ── Histórico de situação ──
INSERT INTO client_situation_history (id, client_id, previous_situation, new_situation, changed_at, changed_by) VALUES
 (gen_random_uuid(), 'c0000001-0000-4000-8000-000000000002', 'Análise documental', 'Planejamento em execução', now() - interval '5 days', 'a1a1a1a1-0000-4000-8000-000000000001');

COMMIT;
