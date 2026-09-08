-- ============================================================================
-- Massa do tenant "tania" (schema tenant_tania).
-- Reaproveita o seed grande (125 clientes) apontando o search_path para o
-- schema do tenant. Rode a partir da raiz do repositório:
--   psql "$DATABASE_URL" -f db/mock-data/seed_tenant_tania.sql
-- ============================================================================
SET search_path TO tenant_tania, public;

-- \ir inclui relativo a ESTE arquivo (independe do diretório atual do psql).
\ir seed_mock.sql

-- ── Agenda (compromissos) ──
-- Cobre tipos, modalidades, status (inclui cancelado com justificativa +
-- histórico), vínculo a cliente cadastrado (client_id) e nome livre
-- (client_name). Distribuição por mês reproduz as abas: Ago 3 / Set 2 / Nov 1.
-- UUIDs fixos: usuário Tania = 9786dc7f...; cliente Maria = 7d7ff165...
BEGIN;

TRUNCATE appointment_history, appointments RESTART IDENTITY CASCADE;

INSERT INTO appointments
 (id, title, type, start_at, end_at, modality, location, meeting_url, description,
  status, cancellation_reason, client_id, client_name, created_by) VALUES
 ('aa000001-0000-4000-8000-000000000001', 'Entrevista com Maria Aparecida', 'Entrevista',
  '2026-08-20 14:30:00-03', '2026-08-20 15:30:00-03', 'Presencial', 'Escritório - Sala 1', NULL,
  'Levantamento inicial de documentos.', 'Agendado', NULL,
  '7d7ff165-cb15-481b-8e9a-b755ba876ca1', NULL, '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),

 ('aa000001-0000-4000-8000-000000000002', 'Reunião online com Yuri Andrade', 'Reunião',
  '2026-08-22 10:00:00-03', '2026-08-22 11:00:00-03', 'Online', NULL,
  'https://meet.google.com/abc-defg-hij', 'Alinhamento sobre planejamento.', 'Agendado', NULL,
  NULL, 'Yuri Andrade', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),

 ('aa000001-0000-4000-8000-000000000003', 'Perícia médica INSS', 'Perícia',
  '2026-08-08 09:00:00-03', '2026-08-08 10:00:00-03', 'Presencial', 'Agência INSS Centro', NULL,
  'Perícia de incapacidade.', 'Concluído', NULL,
  '7d7ff165-cb15-481b-8e9a-b755ba876ca1', NULL, '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),

 ('aa000001-0000-4000-8000-000000000004', 'Audiência de instrução', 'Audiência',
  '2026-09-15 13:00:00-03', '2026-09-15 14:00:00-03', 'Presencial', 'Fórum - 3ª Vara', NULL,
  NULL, 'Agendado', NULL, NULL, 'José Carlos Pereira', '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),

 ('aa000001-0000-4000-8000-000000000005', 'Reunião de alinhamento', 'Reunião',
  '2026-09-28 16:00:00-03', '2026-09-28 16:30:00-03', 'Online', NULL,
  'https://meet.google.com/klm-nopq-rst', 'Revisão de simulação.', 'Agendado', NULL,
  NULL, NULL, '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),

 ('aa000001-0000-4000-8000-000000000006', 'Prazo: entrega de documentos', 'Prazo',
  '2026-11-02 18:00:00-03', '2026-11-02 18:30:00-03', 'Presencial', NULL, NULL,
  'Vencimento da 1ª parcela / entrega de documentos.', 'Agendado', NULL,
  NULL, NULL, '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),

 ('aa000001-0000-4000-8000-000000000007', 'Entrevista inicial', 'Entrevista',
  '2026-10-05 11:00:00-03', '2026-10-05 12:00:00-03', 'Presencial', 'Escritório - Sala 2', NULL,
  'Primeiro contato.', 'Cancelado', 'Cliente remarcou para a próxima semana.',
  NULL, 'Fulano de Tal', '9786dc7f-6b65-465a-b07b-bdf5c152fe66');

INSERT INTO appointment_history
 (appointment_id, action, justification, changed_by) VALUES
 ('aa000001-0000-4000-8000-000000000003', 'EDITED', 'Ajuste de horário da perícia.',
  '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('aa000001-0000-4000-8000-000000000007', 'CANCELLED', 'Cliente remarcou para a próxima semana.',
  '9786dc7f-6b65-465a-b07b-bdf5c152fe66'),
 ('aa000001-0000-4000-8000-000000000003', 'ACKNOWLEDGED',
  'Ciência confirmada: compromisso com data no passado.',
  '9786dc7f-6b65-465a-b07b-bdf5c152fe66');

-- Autorizador da data retroativa (perícia de 08/08, no passado).
UPDATE appointments
   SET past_date_authorized_by = '9786dc7f-6b65-465a-b07b-bdf5c152fe66',
       past_date_authorized_at = now()
 WHERE id = 'aa000001-0000-4000-8000-000000000003';

COMMIT;
