#!/usr/bin/env bash
#
# Bootstrap ÚNICO da instância EC2 (Amazon Linux 2023, ARM64) para o deploy
# containerizado via Docker Compose. Rode UMA VEZ, manualmente, por SSH, como
# ec2-user. Depois disso o deploy automático (.github/workflows/deploy.yml ->
# scripts/restart-app-docker.sh) funciona sozinho.
#
# É idempotente: pode rodar de novo sem quebrar.
#
#   ssh ec2-user@<host>
#   cd ~/taniamelo-advocacia-backend
#   git fetch origin develop && git reset --hard origin/develop   # pega este script
#   bash scripts/bootstrap-docker.sh
#
set -euo pipefail

log()  { echo -e "\n>>> $*"; }
warn() { echo "AVISO: $*" >&2; }

[ "$(id -un)" = "ec2-user" ] || warn "esperado rodar como ec2-user (atual: $(id -un))."

# ── 1. Docker Engine ──────────────────────────────────────────────────────
if command -v docker > /dev/null; then
    log "Docker já instalado ($(docker --version))."
else
    log "Instalando Docker (dnf)..."
    sudo dnf install -y docker
fi
sudo systemctl enable --now docker

# ── 2. Plugin docker compose (v2) ─────────────────────────────────────────
# No AL2023 o pacote 'docker' nem sempre traz o plugin compose. Tenta via dnf;
# se não houver, instala o binário oficial em ~/.docker/cli-plugins.
if docker compose version > /dev/null 2>&1; then
    log "'docker compose' já disponível ($(docker compose version | head -1))."
else
    log "Instalando o plugin docker compose..."
    if sudo dnf install -y docker-compose-plugin 2>/dev/null && docker compose version > /dev/null 2>&1; then
        log "Plugin instalado via dnf."
    else
        ARCH="$(uname -m)"  # aarch64 na ARM64
        mkdir -p "$HOME/.docker/cli-plugins"
        curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${ARCH}" \
            -o "$HOME/.docker/cli-plugins/docker-compose"
        chmod +x "$HOME/.docker/cli-plugins/docker-compose"
        log "Plugin instalado em ~/.docker/cli-plugins ($(docker compose version | head -1))."
    fi
fi

# ── 3. Cliente psql (para o seed por tenant no restart-app-docker.sh) ─────
if command -v psql > /dev/null; then
    log "psql já instalado ($(psql --version))."
else
    log "Instalando cliente PostgreSQL..."
    sudo dnf install -y postgresql15 || sudo dnf install -y postgresql
fi

# ── 4. ec2-user no grupo docker (roda docker sem sudo) ────────────────────
if id -nG ec2-user | tr ' ' '\n' | grep -qx docker; then
    log "ec2-user já está no grupo docker."
else
    log "Adicionando ec2-user ao grupo docker (efetivo no próximo login/SSH)..."
    sudo usermod -aG docker ec2-user
fi

# ── 5. Liberar as portas: parar o deploy nativo antigo ────────────────────
# A instância rodava via systemd (serviço 'law-firm') + Postgres nativo
# (serviço 'postgresql'). O compose publica 8080 (app) e 127.0.0.1:5432 (db) -
# se os serviços nativos continuarem no ar, dá conflito de porta.
for svc in law-firm postgresql; do
    if systemctl list-unit-files 2>/dev/null | grep -q "^${svc}.service"; then
        log "Parando e desabilitando o serviço nativo '${svc}'..."
        sudo systemctl disable --now "${svc}" 2>/dev/null || true
    fi
done

# ── 6. .env ───────────────────────────────────────────────────────────────
cd "$(dirname "$0")/.."
if [ -r .env ]; then
    log ".env já existe na raiz do repo - mantido."
else
    warn ".env NÃO existe. Crie-o a partir do modelo e preencha os segredos:"
    echo "      cp .env.example .env && nano .env"
    echo "      (gere APP_JWT_SECRET/APP_ENCRYPTION_KEY com os 'openssl rand' do .env.example)"
fi

cat <<'EOF'

──────────────────────────────────────────────────────────────────────────
Bootstrap concluído.

ATENÇÃO — dados: o container 'db' começa com um volume NOVO e vazio. O que
estava no Postgres NATIVO da instância NÃO é migrado automaticamente. Se havia
dados reais que precisam ser preservados, faça o dump do Postgres nativo ANTES
e restaure no container 'db' depois de subir (pg_dump | psql). Em base de dev
(tenant demo), o próprio deploy popula com a massa de teste.

Próximos passos:
  1. Garanta o .env preenchido (passo 6 acima).
  2. Faça logout/login do SSH uma vez (para o grupo 'docker' valer sem sudo).
  3. Suba manualmente a primeira vez para conferir:
         bash scripts/restart-app-docker.sh
     ou dispare o deploy pelo GitHub Actions (workflow_dispatch / CI verde).
──────────────────────────────────────────────────────────────────────────
EOF
