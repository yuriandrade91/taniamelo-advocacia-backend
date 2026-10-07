#!/usr/bin/env bash
# Compara o banco local com um banco construído do zero pelas 20 migrations.
# Rode da raiz do taniamelo-advocacia-backend.
set -u
DB=${POSTGRES_DB:-system}; U=${POSTGRES_USER:-postgres}; C=${PG_CONTAINER:-database-postgress}
q() { docker exec "$C" psql -U "$U" -d "$DB" -At -c "$1" 2>&1; }

echo "=== flyway_schema_history (tenant_tania) ==="
q "select version||' | '||description||' | '||checksum||' | '||case when success then 'ok' else 'FALHOU' end
     from tenant_tania.flyway_schema_history order by installed_rank"

echo; echo "=== impressão digital das tabelas ==="
for t in clients appointments audit_log users; do
  printf '%-14s ' "$t"
  q "select coalesce(count(*)||' cols / '||md5(string_agg(column_name||':'||data_type, ',' order by column_name)), 'TABELA AUSENTE')
       from information_schema.columns where table_schema='tenant_tania' and table_name='$t'"
done

echo; echo "=== tabelas da carteira (V18) ==="
q "select coalesce(string_agg(table_name, ', '), 'AUSENTES')
     from information_schema.tables
    where table_schema='tenant_tania' and table_name in ('office_revenues','office_expenses')"

echo; echo "=== massa e papéis ==="
q "select 'clients: '||count(*) from tenant_tania.clients"
q "select 'appointments: '||count(*) from tenant_tania.appointments"
q "select 'role '||role||': '||count(*) from tenant_tania.users group by role"
