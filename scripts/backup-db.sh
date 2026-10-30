#!/usr/bin/env bash
#
# Dump do banco inteiro (catálogo public + todos os schemas de escritório) do
# Postgres do compose, em formato custom do pg_dump (-Fc: comprimido, e o
# pg_restore escolhe o que restaurar). Guarda em $BACKUP_DIR e apaga os mais
# velhos que $BACKUP_RETENTION_DAYS.
#
#   bash scripts/backup-db.sh
#
# Um dump no MESMO disco do banco protege de "DROP SCHEMA" errado, migration
# ruim e "docker compose down -v" - não de perder o volume/a instância. Para
# isso o arquivo precisa sair da máquina (S3, R2...), o que fica a cargo de
# quem agenda este script. Ver docs/BACKUP.md.
#
# Restore, e teste de que o dump restaura: scripts/restore-db.sh.
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".env"
BACKUP_DIR="${BACKUP_DIR:-backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"

erro() { echo "ERRO: $*" >&2; exit 1; }

[ -r "$ENV_FILE" ] || erro "$ENV_FILE não existe na raiz do repo."
# Só os dois nomes necessários; nenhum valor é impresso.
POSTGRES_USER=$(set +u; set -a; . "./$ENV_FILE"; echo "${POSTGRES_USER:-}")
POSTGRES_DB=$(set +u; set -a; . "./$ENV_FILE"; echo "${POSTGRES_DB:-}")
[ -n "$POSTGRES_USER" ] && [ -n "$POSTGRES_DB" ] || erro "POSTGRES_USER/POSTGRES_DB vazios em $ENV_FILE."

docker compose exec -T db pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" > /dev/null \
    || erro "o serviço 'db' do compose não está respondendo."

# Dado de cliente (CPF, senha do INSS cifrada, nome da mãe): só o dono lê.
umask 077
mkdir -p "$BACKUP_DIR"

destino="$BACKUP_DIR/${POSTGRES_DB}-$(date -u +%Y%m%dT%H%M%SZ).dump"
parcial="$destino.parcial"
trap 'rm -f "$parcial"' EXIT

echo "Gerando $destino..."
docker compose exec -T db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$parcial"

# Um dump truncado (disco cheio, container reiniciado no meio) também sai com
# arquivo. Lê o arquivo INTEIRO, convertendo para SQL e jogando fora: o
# "pg_restore --list" só lê o índice do começo e aprova um dump cortado no fim
# (testado com um arquivo truncado a 90%).
docker compose exec -T db pg_restore -f /dev/null < "$parcial" \
    || erro "o dump gerado não é legível pelo pg_restore - descartado."

mv "$parcial" "$destino"
echo "Backup ok: $destino ($(du -h "$destino" | cut -f1))"

# Retenção: só arquivos deste script (*.dump) dentro de $BACKUP_DIR.
removidos=$(find "$BACKUP_DIR" -maxdepth 1 -name '*.dump' -mtime +"$BACKUP_RETENTION_DAYS" -print -delete | wc -l | tr -d ' ')
[ "$removidos" -eq 0 ] || echo "Removidos $removidos backup(s) com mais de $BACKUP_RETENTION_DAYS dia(s)."
