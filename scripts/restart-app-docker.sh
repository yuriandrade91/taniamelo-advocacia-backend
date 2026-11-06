#!/usr/bin/env bash
#
# Sobe o backend containerizado (app + Postgres + proxy TLS) na instância EC2, via
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
             SPRING_PROFILES_ACTIVE SITE_DOMAIN APP_CORS_ALLOWED_ORIGINS \
             APP_JWT_SECRET APP_ENCRYPTION_KEY APP_FILE_ENCRYPTION_KEY; do
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
    case "$APP_FILE_ENCRYPTION_KEY" in
        COLE_AQUI*|ZGV2LW9ubHktZmlsZS1rZXktTk9ULUZPUi1QUk9EISE=) echo "APP_FILE_ENCRYPTION_KEY (placeholder ou CHAVE DE DEV)";;
    esac
    [ -n "$APP_FILE_ENCRYPTION_KEY" ] && [ "$APP_FILE_ENCRYPTION_KEY" = "$APP_ENCRYPTION_KEY" ] && echo "APP_FILE_ENCRYPTION_KEY igual a APP_ENCRYPTION_KEY (use chaves diferentes)"
    # Produção roda só com o perfil "postgres". Qualquer outro (dev, test) liga o que é de
    # máquina de desenvolvedor, como o CORS "*" e a amostragem total de tracing.
    [ "$SPRING_PROFILES_ACTIVE" != "postgres" ] && echo "SPRING_PROFILES_ACTIVE='$SPRING_PROFILES_ACTIVE' (produção usa postgres)"
    case "$SITE_DOMAIN" in
        *.invalid|localhost*|*example*) echo "SITE_DOMAIN (valor de exemplo; use o domínio real, com DNS já apontado)";;
    esac
    case "$APP_CORS_ALLOWED_ORIGINS" in
        '*'|*.invalid|*localhost*) echo "APP_CORS_ALLOWED_ORIGINS (use o https do frontend real)";;
    esac
    [ "${APP_STORAGE_TYPE:-}" != "s3" ] && echo "APP_STORAGE_TYPE='${APP_STORAGE_TYPE:-}' (produção usa s3)"
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
# Migrations rodam no boot (MultiTenantFlywayMigrator).
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
    echo "Containers subiram mas a aplicação não respondeu UP a tempo."
    echo "O deploy segue com a checagem de versão, que vai reportar a falha."
    exit 0
fi

# O banco sobe vazio e continua vazio: nada é semeado. O escritório e os usuários entram pelos
# scripts de scripts/tenant (ver docs/CADASTRO_DE_TENANT.md).
echo "Containers no ar. O deploy segue com a checagem de versão."
