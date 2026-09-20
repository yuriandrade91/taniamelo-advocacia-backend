#!/usr/bin/env bash
#
# Reinicia o backend na instância EC2. Chamado pelo job de deploy.
#
# Existe no repositório porque a versão anterior morava solta em
# /home/ec2-user/restart-app.sh: o passo mais importante do deploy não era
# revisado, não era reproduzível e sumiria junto com a instância. Secret nenhum
# entra aqui - eles ficam em /etc/law-firm/law-firm.env (chmod 600), fora do
# git. O que é versionado é a LÓGICA; o que é secreto continua na máquina.
#
# A instância roda o app como serviço systemd (ver docs/AWS_EC2_ARM64_DEPLOY.md),
# não como processo solto - por isso systemctl, e por isso o log é o journal.
set -euo pipefail

SERVICO="${LAW_FIRM_SERVICE:-law-firm}"
ENV_FILE="${LAW_FIRM_ENV_FILE:-/etc/law-firm/law-firm.env}"
JAR_GLOB="${LAW_FIRM_JAR_GLOB:-/home/ec2-user/taniamelo-advocacia-backend/target/law-firm-*.jar}"

erro() { echo "ERRO: $*" >&2; exit 1; }

# ── 1. O jar que o systemd vai executar existe? ──
# Sem isto, o restart falha depois, dentro do systemd, com uma mensagem muito
# menos óbvia do que "o build não gerou jar".
# shellcheck disable=SC2086
if ! compgen -G $JAR_GLOB > /dev/null; then
    erro "nenhum jar em $JAR_GLOB - o 'mvn package' do deploy não produziu artefato."
fi

# ── 2. O ambiente está completo? ──
[ -r "$ENV_FILE" ] || erro "$ENV_FILE não existe ou não é legível."

# Lido em subshell: as variáveis não vazam para o resto do script, e nenhum
# valor é impresso em lugar nenhum - só o NOME do que estiver faltando.
faltando=$(
    set +u
    # shellcheck disable=SC1090
    . "$ENV_FILE"
    for v in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
             APP_JWT_SECRET APP_ENCRYPTION_KEY; do
        [ -z "${!v}" ] && echo "$v"
    done
    true
)
[ -z "$faltando" ] || erro "variáveis obrigatórias vazias em $ENV_FILE: $(echo "$faltando" | tr '\n' ' ')"

# ── 3. Sobrou algum valor de exemplo? ──
#
# Este é o ponto do script. A aplicação SOBE com a chave de criptografia de
# desenvolvimento - aquela que está no fonte, em texto - registrando apenas um
# log.warn no boot. Ou seja: senha de cliente "criptografada" com chave pública,
# sem nada quebrar e sem ninguém perceber. Aqui isso vira deploy recusado.
inseguros=$(
    set +u
    # shellcheck disable=SC1090
    . "$ENV_FILE"
    # Placeholders do passo 7 do docs/AWS_EC2_ARM64_DEPLOY.md e os defaults de dev
    # do application.yaml / CryptoConverter.
    [ "$SPRING_DATASOURCE_PASSWORD" = "TROQUE_ESTA_SENHA" ] && echo "SPRING_DATASOURCE_PASSWORD (placeholder)"
    case "$APP_JWT_SECRET" in
        COLE_AQUI*|dev-only-secret-change-me-please-32chars) echo "APP_JWT_SECRET (placeholder ou default de dev)";;
    esac
    case "$APP_ENCRYPTION_KEY" in
        COLE_AQUI*|ZGV2LW9ubHktaW5zZWN1cmUtMzItYnl0ZS1rZXkhISE=) echo "APP_ENCRYPTION_KEY (placeholder ou CHAVE DE DEV)";;
    esac
    [ "${APP_ADMIN_PASSWORD:-}" = "changeme123" ] && echo "APP_ADMIN_PASSWORD (default de dev)"
    true
)
[ -z "$inseguros" ] || erro "valores inseguros em $ENV_FILE: $(echo "$inseguros" | tr '\n' '; ')"

# ── 4. Reiniciar ──
#
# Devolve o controle assim que o systemd aceita o comando; quem espera a
# aplicação responder UP é o job de deploy (.github/workflows/deploy.yml).
# As migrations rodam no boot, então "subiu" e "migrou" são o mesmo evento.
echo "Reiniciando $SERVICO..."
sudo systemctl restart "$SERVICO"
sudo systemctl is-active --quiet "$SERVICO" || erro "$SERVICO não ficou ativo. Veja: sudo journalctl -u $SERVICO -n 80"
echo "$SERVICO reiniciado. O deploy aguarda o health check."
