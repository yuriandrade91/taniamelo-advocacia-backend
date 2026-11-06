#!/usr/bin/env bash
#
# Grava no .env da instância onde os arquivos dos clientes devem ser guardados.
#
# Por que isto existe: o .env da EC2 é criado uma vez, à mão, e depois ninguém
# lembra dele. Foi assim que a instância ficou meses com APP_STORAGE_TYPE=local
# enquanto a documentação de deploy dizia s3 — e os documentos dos clientes
# viveram num volume de uma máquina só, sem cópia no bucket. A configuração
# passa a vir do pipeline, que é versionado e roda igual toda vez.
#
# Não é o lugar de secret: bucket e região não são segredo, e credencial da AWS
# não entra aqui de propósito — em EC2 quem responde por isso é a IAM Role da
# instância. Se um dia precisar de chave, ela continua no .env, escrita à mão.
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE=".env"

erro() { echo "ERRO: $*" >&2; exit 1; }

[ -w "$ENV_FILE" ] || erro "$ENV_FILE não existe ou não é gravável. Crie-o antes do primeiro deploy."

TIPO="${APP_STORAGE_TYPE:-}"
BUCKET="${APP_STORAGE_S3_BUCKET:-}"
REGIAO="${APP_STORAGE_S3_REGION:-}"

# Sem nada definido no pipeline, o .env fica como está. Deploy que não quer
# opinar sobre storage não deve apagar o que já foi configurado na máquina.
if [ -z "$TIPO" ]; then
    echo "APP_STORAGE_TYPE não veio do pipeline; mantendo o que está no $ENV_FILE."
    exit 0
fi

case "$TIPO" in
    local|s3) ;;
    *) erro "APP_STORAGE_TYPE inválido: '$TIPO' (aceitos: local, s3)";;
esac

# Recusar cedo é o ponto: s3 sem bucket sobe e volta para disco local com um
# aviso no log, que ninguém lê. Falhar aqui é ruidoso, que é o que se quer.
if [ "$TIPO" = "s3" ] && [ -z "$BUCKET" ]; then
    erro "APP_STORAGE_TYPE=s3 sem APP_STORAGE_S3_BUCKET. Defina a variável APP_STORAGE_S3_BUCKET no repositório."
fi

# Substitui a linha se a chave existe, acrescenta se não existe. Sem isto, um
# segundo deploy empilharia chaves repetidas e o `. .env` passaria a valer a
# última — funcionando por acidente e confundindo quem lesse o arquivo.
upsert() {
    local chave="$1" valor="$2"
    if grep -qE "^${chave}=" "$ENV_FILE"; then
        # Delimitador | porque bucket e região não o contêm; barra apareceria
        # num caminho e quebraria o sed.
        sed -i "s|^${chave}=.*|${chave}=${valor}|" "$ENV_FILE"
    else
        printf '%s=%s\n' "$chave" "$valor" >> "$ENV_FILE"
    fi
}

upsert APP_STORAGE_TYPE "$TIPO"
[ -n "$BUCKET" ] && upsert APP_STORAGE_S3_BUCKET "$BUCKET"
[ -n "$REGIAO" ] && upsert APP_STORAGE_S3_REGION "$REGIAO"

# Só os NOMES e valores não-secretos; nada aqui é credencial.
echo "Storage configurado: APP_STORAGE_TYPE=$TIPO${BUCKET:+, bucket=$BUCKET}${REGIAO:+, região=$REGIAO}"
