#!/usr/bin/env bash
#
# Restaura um dump gerado por scripts/backup-db.sh.
#
#   bash scripts/restore-db.sh backups/<arquivo>.dump            # teste (padrão)
#   bash scripts/restore-db.sh backups/<arquivo>.dump --producao # sobrescreve o banco
#
# Teste: restaura num banco descartável dentro do mesmo Postgres, compara a
# contagem de linhas de cada tabela com o banco atual e apaga o descartável.
# Não toca no banco da aplicação. Backup que nunca foi restaurado é hipótese:
# rode isto depois de mudar o backup, e de tempos em tempos.
#
# --producao: apaga e recria os objetos do banco da aplicação a partir do dump
# (pg_restore --clean, numa transação só). Exige o app PARADO e a digitação do
# nome do banco - é irreversível.
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".env"

erro() { echo "ERRO: $*" >&2; exit 1; }

arquivo="${1:-}"
modo="${2:---teste}"
[ -n "$arquivo" ] || erro "uso: $0 <arquivo.dump> [--producao]"
[ -r "$arquivo" ] || erro "$arquivo não existe ou não é legível."
case "$modo" in --teste|--producao) ;; *) erro "modo desconhecido: $modo" ;; esac

[ -r "$ENV_FILE" ] || erro "$ENV_FILE não existe na raiz do repo."
POSTGRES_USER=$(set +u; set -a; . "./$ENV_FILE"; echo "${POSTGRES_USER:-}")
POSTGRES_DB=$(set +u; set -a; . "./$ENV_FILE"; echo "${POSTGRES_DB:-}")
[ -n "$POSTGRES_USER" ] && [ -n "$POSTGRES_DB" ] || erro "POSTGRES_USER/POSTGRES_DB vazios em $ENV_FILE."

psql_db() { docker compose exec -T db psql -U "$POSTGRES_USER" -v ON_ERROR_STOP=1 -qtA -d "$@"; }

# Linhas por tabela de todos os schemas da aplicação, uma por linha:
# "schema.tabela|contagem". query_to_xml faz o count(*) dinâmico sem função.
contar_linhas() {
    psql_db "$1" -c "
        SELECT table_schema || '.' || table_name || '|' ||
               (xpath('/row/c/text()', query_to_xml(
                   format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name),
                   false, true, '')))[1]::text
        FROM information_schema.tables
        WHERE table_type = 'BASE TABLE'
          AND table_schema NOT IN ('pg_catalog', 'information_schema')
        ORDER BY 1"
}

if [ "$modo" = "--teste" ]; then
    descartavel="restore_teste_$$"
    trap 'psql_db postgres -c "DROP DATABASE IF EXISTS $descartavel" > /dev/null' EXIT

    echo "Restaurando $arquivo no banco descartável $descartavel..."
    psql_db postgres -c "CREATE DATABASE $descartavel" > /dev/null
    docker compose exec -T db pg_restore -U "$POSTGRES_USER" -d "$descartavel" \
        --no-owner --exit-on-error < "$arquivo"

    restaurado=$(contar_linhas "$descartavel")
    atual=$(contar_linhas "$POSTGRES_DB")
    tabelas=$(echo "$restaurado" | grep -c . || true)
    linhas=$(echo "$restaurado" | awk -F'|' '{s += $2} END {print s + 0}')
    echo "Restaurado: $tabelas tabela(s), $linhas linha(s)."

    if [ "$restaurado" = "$atual" ]; then
        echo "OK: contagem idêntica à do banco $POSTGRES_DB, tabela a tabela."
    else
        # Diferença é esperada se houve escrita depois do dump; o que importa é
        # o restore ter completado. A lista mostra onde, para conferir.
        echo "Restore completo, mas difere do banco atual (normal se houve escrita após o dump):"
        diff <(echo "$atual") <(echo "$restaurado") | sed -n 's/^</  atual:      /p; s/^>/  restaurado: /p'
    fi
    exit 0
fi

# ── --producao ──
if docker compose ps --status running --services 2>/dev/null | grep -qx app; then
    erro "o serviço 'app' está rodando. Pare antes (docker compose stop app): restaurar com o app escrevendo mistura os dois estados."
fi
echo "Isto vai SOBRESCREVER o banco '$POSTGRES_DB' com $arquivo. Não há volta."
read -r -p "Digite o nome do banco para confirmar: " confirmacao
[ "$confirmacao" = "$POSTGRES_DB" ] || erro "confirmação não confere - nada foi alterado."

docker compose exec -T db pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    --clean --if-exists --no-owner --single-transaction --exit-on-error < "$arquivo"
echo "Banco '$POSTGRES_DB' restaurado de $arquivo. Suba o app de novo."
