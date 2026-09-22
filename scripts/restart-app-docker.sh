#!/usr/bin/env bash
#
# Sobe o backend containerizado (app + Postgres) na instância EC2, via
# Docker Compose. Chamado pelo job de deploy (.github/workflows/deploy.yml).
#
# Sucessor de restart-app.sh: esta instância passou a rodar via
# `docker compose --profile full`, não mais como serviço systemd lendo
# /etc/law-firm/law-firm.env. Secret nenhum entra aqui - eles ficam em
# ".env" (chmod 600), na raiz do repo, fora do git (.gitignore). O que é
# versionado é a LÓGICA; o que é secreto continua na máquina.
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".env"

erro() { echo "ERRO: $*" >&2; exit 1; }

# ── 0. As ferramentas necessárias existem? ──
command -v docker > /dev/null || erro "docker não encontrado - rode o bootstrap manual (instalar Docker) antes do primeiro deploy containerizado."
docker compose version > /dev/null 2>&1 || erro "'docker compose' (plugin) não encontrado."
command -v psql > /dev/null || erro "psql não encontrado - necessário para popular o banco (dnf install -y postgresql18 ou equivalente)."

# ── 1. O .env existe? ──
[ -r "$ENV_FILE" ] || erro "$ENV_FILE não existe ou não é legível na raiz do repo. Ele não é gerado pelo deploy - crie manualmente uma vez, com as mesmas variáveis do docker-compose.yml."

# ── 2. O ambiente está completo? ──
# Lido em subshell: as variáveis não vazam para o resto do script, e nenhum
# valor é impresso em lugar nenhum - só o NOME do que estiver faltando.
faltando=$(
    set +u
    set -a
    . "./$ENV_FILE"
    set +a
    for v in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
             SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
             APP_JWT_SECRET APP_ENCRYPTION_KEY; do
        [ -z "${!v}" ] && echo "$v"
    done
    true
)
[ -z "$faltando" ] || erro "variáveis obrigatórias vazias em $ENV_FILE: $(echo "$faltando" | tr '\n' ' ')"

# ── 3. Sobrou algum valor de exemplo ou configuração errada? ──
#
# Mesma lógica de segurança do restart-app.sh original: a aplicação sobe com
# a chave de criptografia de desenvolvimento (a que está no fonte, em texto)
# apenas com um log.warn. Aqui isso vira deploy recusado. Adicionado também
# o erro mais comum de quem migra de nativo pra compose: apontar o
# datasource pra localhost em vez do hostname do serviço "db".
inseguros=$(
    set +u
    set -a
    . "./$ENV_FILE"
    set +a
    [ "$SPRING_DATASOURCE_PASSWORD" = "TROQUE_ESTA_SENHA" ] && echo "SPRING_DATASOURCE_PASSWORD (placeholder)"
    case "$APP_JWT_SECRET" in
        COLE_AQUI*|dev-only-secret-change-me-please-32chars) echo "APP_JWT_SECRET (placeholder ou default de dev)";;
    esac
    case "$APP_ENCRYPTION_KEY" in
        COLE_AQUI*|ZGV2LW9ubHktaW5zZWN1cmUtMzItYnl0ZS1rZXkhISE=) echo "APP_ENCRYPTION_KEY (placeholder ou CHAVE DE DEV)";;
    esac
    [ "${APP_ADMIN_PASSWORD:-}" = "changeme123" ] && echo "APP_ADMIN_PASSWORD (default de dev)"
    case "$SPRING_DATASOURCE_URL" in
        *localhost*|*127.0.0.1*) echo "SPRING_DATASOURCE_URL aponta pra localhost/127.0.0.1 (deveria ser jdbc:postgresql://db:5432/... dentro do compose)";;
    esac
    true
)
[ -z "$inseguros" ] || erro "valores inseguros/incorretos em $ENV_FILE: $(echo "$inseguros" | tr '\n' '; ')"

# ── 4. Subir os containers ──
export GIT_COMMIT="${GIT_COMMIT:-$(git rev-parse --short HEAD)}"
echo "Subindo containers (commit $GIT_COMMIT)..."
docker compose --profile full up -d --build

# ── 5. Esperar a aplicação aplicar as migrations ──
# Migrations rodam no boot (MultiTenantFlywayMigrator); só depois disso os
# schemas dos tenants existem e dá pra popular o banco com segurança.
echo "Aguardando a aplicação responder (migrations rodam no boot)..."
subiu=nao
for _ in $(seq 1 30); do
    if curl -fsS -m 5 http://localhost:8080/actuator/health > /dev/null 2>&1; then
        subiu=sim
        break
    fi
    sleep 4
done
if [ "$subiu" != "sim" ]; then
    echo "Containers subiram mas a aplicação não respondeu UP a tempo - pulando o seed."
    echo "O deploy segue com a checagem de versão, que vai reportar a falha."
    exit 0
fi

# ── 6. Popular o banco - só na primeira vez, por tenant ──
# Idempotente: os scripts de seed fazem TRUNCATE, então só rodam quando o
# schema do tenant ainda não tem usuário nenhum. Isso protege dados reais
# criados depois do primeiro deploy containerizado - deploys seguintes não
# reexecutam o seed.
set -a
. "./$ENV_FILE"
set +a

for tenant in tania demo; do
    schema="tenant_${tenant}"
    seed_file="db/mock-data/seed_tenant_${tenant}.sql"
    [ -f "$seed_file" ] || { echo "Sem $seed_file - pulando seed de $schema."; continue; }

    ja_populado=$(PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
        "SELECT 1 FROM information_schema.tables WHERE table_schema='${schema}' AND table_name='users'" 2>/dev/null || true)
    if [ -z "$ja_populado" ]; then
        echo "Schema $schema sem tabela users ainda - Flyway pode não ter rodado pra esse tenant. Pulando seed."
        continue
    fi

    tem_usuario=$(PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
        "SELECT 1 FROM ${schema}.users LIMIT 1" 2>/dev/null || true)
    if [ -n "$tem_usuario" ]; then
        echo "Schema $schema já tem usuários - pulando seed."
        continue
    fi

    echo "Populando $schema com a massa de teste ($seed_file)..."
    PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
        -v ON_ERROR_STOP=1 -f "$seed_file"
done

echo "Containers no ar, banco populado. O deploy segue com a checagem de versão."
