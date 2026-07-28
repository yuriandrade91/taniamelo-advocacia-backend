-- ============================================================================
-- Massa do tenant "tania" (schema tenant_tania).
-- Reaproveita o seed grande (125 clientes) apontando o search_path para o
-- schema do tenant. Rode a partir da raiz do repositório:
--   psql "$DATABASE_URL" -f db/mock-data/seed_tenant_tania.sql
-- ============================================================================
SET search_path TO tenant_tania, public;

-- \ir inclui relativo a ESTE arquivo (independe do diretório atual do psql).
\ir seed_mock.sql
