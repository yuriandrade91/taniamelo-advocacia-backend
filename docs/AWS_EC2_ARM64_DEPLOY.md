# Deploy na EC2 ARM64 (Amazon Linux 2023, Docker Compose)

Guia para subir o backend do zero: infraestrutura na AWS, instância, aplicação e proxy
HTTPS. A aplicação roda em contêiner (`docker compose --profile full`): **app**, **Postgres**
e **proxy Caddy**, na mesma instância (`t4g`, Graviton/ARM64, Amazon Linux 2023).

O que precisa estar pronto antes de apontar tráfego real está em
[PLANO_VIRADA_PRODUCAO.md](PLANO_VIRADA_PRODUCAO.md). O HTTPS tem guia próprio:
[PROXY_TLS.md](PROXY_TLS.md).

---

## Parte A - Infraestrutura na AWS

### A.1 - Par de chaves e acesso

Console → **EC2 → Key Pairs → Create key pair** (`ED25519`, `.pem`) e `chmod 400`. Preferir o
**Session Manager** (SSM) ao SSH: sem porta 22 aberta.

### A.2 - Security Group

| Type | Port | Source |
|---|---|---|
| HTTP | 80 | `0.0.0.0/0` (o Caddy usa para emitir o certificado e redireciona para HTTPS) |
| HTTPS | 443 (TCP e UDP) | `0.0.0.0/0` |
| SSH | 22 | só o IP de quem administra, ou fechada se usar SSM |

A 8080 (aplicação) e a 5432 (Postgres) **não** são abertas: o compose as publica só em
`127.0.0.1`.

### A.3 - Bucket S3 (um por ambiente)

Console → **S3 → Create bucket**: nome único, região **`sa-east-1`**, **Block all public access**,
ACLs desativadas (*Bucket Owner Enforced*), criptografia SSE-S3, **versionamento ligado**.

- Regra de ciclo de vida: abortar *multipart uploads* incompletos após 7 dias.
- **Não** criar regra que expire objetos nem o prefixo `_trash/`: a aplicação não apaga nada, e a
  lixeira é o que permite restaurar.
- Política do bucket negando acesso sem TLS (`aws:SecureTransport = false`).

### A.4 - IAM Role da instância

Console → **IAM → Roles → Create role** → *AWS service → EC2*, com esta política inline (troque
`SEU_BUCKET`). Nada de `AmazonS3FullAccess`. O `move` da lixeira é `CopyObject` mais
`DeleteObject`, então os três acessos a objeto são necessários:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::SEU_BUCKET/*"
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": "arn:aws:s3:::SEU_BUCKET"
    }
  ]
}
```

Anexar a política no console não prova que ela vale: depois de lançar a instância, rode
`aws s3 ls s3://SEU_BUCKET/` **dentro** dela.

### A.5 - Instância

**EC2 → Launch Instance**: AMI Amazon Linux 2023 (arm64); tipo `t4g.medium` ou maior (app, Postgres
e proxy dividem a RAM, e arquivos de até 60 MB passam pela memória da JVM); Security Group do A.2;
instance profile do A.4; disco gp3 **criptografado**, de tamanho que comporte o Postgres e as imagens
Docker (8 GiB não basta). Associe um **Elastic IP** e crie a política de *snapshot* diário do volume
(Data Lifecycle Manager).

IMDSv2 com salto 2, senão a aplicação, que roda dentro do Docker, não recebe a credencial da Role:

```bash
aws ec2 modify-instance-metadata-options \
  --instance-id i-xxxxxxxxxxxx \
  --http-put-response-hop-limit 2 \
  --http-tokens required
```

### A.6 - Billing e auditoria

Budget com alertas em 50/80/100% (**Billing → Budgets**) e CloudTrail ligado na conta.

---

## Parte B - Aplicação

### B.1 - Docker na instância

```bash
git clone https://github.com/<organizacao>/taniamelo-advocacia-backend.git
cd taniamelo-advocacia-backend
bash scripts/bootstrap-docker.sh   # instala Docker e o plugin compose; idempotente
```

Se o repositório for privado, o clone usa uma *deploy key* somente leitura cadastrada no GitHub.

### B.2 - Variáveis de ambiente

```bash
cp .env.example .env && chmod 600 .env
openssl rand -base64 48   # APP_JWT_SECRET
openssl rand -base64 32   # APP_ENCRYPTION_KEY
openssl rand -base64 32   # APP_FILE_ENCRYPTION_KEY (outra)
```

Preencha todos os campos de `.env.example`. `restart-app-docker.sh` **recusa** subir com valor de
exemplo, perfil diferente de `postgres`, storage diferente de `s3`, CORS `*` ou datasource em
`localhost`. **Guarde `APP_FILE_ENCRYPTION_KEY` e `APP_ENCRYPTION_KEY` fora da instância**: perder a
primeira é perder os arquivos, e perder a segunda é perder as senhas do INSS.

### B.3 - Subir

O DNS de `SITE_DOMAIN` já deve apontar para o Elastic IP (ver [PROXY_TLS.md](PROXY_TLS.md)).

```bash
bash scripts/restart-app-docker.sh
```

O script valida o `.env`, sobe `db`, `app` e `proxy`, e espera a aplicação responder. O Flyway cria o
catálogo e roda a cada boot; **nenhum dado é semeado**.

### B.4 - Escritório e usuários

O banco sobe vazio. Cadastre o escritório e os usuários com os scripts de `scripts/tenant/`
(copie para fora do repositório, edite a cópia e rode a cópia). Roteiro em
[CADASTRO_DE_TENANT.md](CADASTRO_DE_TENANT.md):

```bash
docker exec -i database-postgress psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 < ~/01-meu-escritorio.sql
docker compose --profile full restart app     # cria o schema do escritório
docker exec -i database-postgress psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 < ~/02-usuarios.sql
```

### B.5 - Conferir

```bash
curl -s https://SEU_DOMINIO/actuator/health         # {"status":"UP",...}
curl -s https://SEU_DOMINIO/actuator/info           # commit, e "storage":{"type":"s3",...}
```

`type` tem de ser `s3`: upload e download funcionam igual em disco, então um teste de arquivo
verde não prova onde o arquivo foi parar. Um upload só prova o provisionamento depois de o
`/actuator/info` afirmar `s3`.

### B.6 - Deploy automático

O CI/CD está em [CI_CD.md](CI_CD.md). O deploy confere health, commit, perfil e storage antes de
se dizer concluído.

---

## Troubleshooting

| Sintoma | Causa provável |
|---|---|
| `restart-app-docker.sh` recusa subir | Mensagem lista o que está inválido no `.env` |
| Certificado não é emitido | DNS ainda não aponta para a instância, ou as portas 80/443 estão fechadas ([PROXY_TLS.md](PROXY_TLS.md)) |
| Upload falha com `AccessDenied` | `aws s3 ls s3://SEU_BUCKET/` na instância: se falhar, é a Role; se só a app falhar, confira o IMDSv2 (A.5) |
| `X cannot be converted` / `NoClassDefFoundError` ao compilar | `mvn clean` |
| Disco enche | Imagens Docker órfãs: `docker image prune -f`. Nunca use `--volumes`: `db-data` é o Postgres e `app-storage`, os arquivos |
| Instância para sozinha | O budget a travou; confira o alerta de billing |

---

## Storage dos arquivos: quem decide, e como se prova

### O problema que ela resolve

O `.env` da EC2 é criado uma vez, à mão, e depois ninguém lembra dele. Foi
assim que a instância ficou com `APP_STORAGE_TYPE=local` enquanto este mesmo
documento mandava `s3` — os documentos dos clientes viveram num volume de uma
máquina só, sem cópia no bucket, e **nada denunciava**: o upload funciona
igual nos dois, porque o `Content-Length` do download vem dos metadados no
banco, não do storage. O teste de arquivo passava verde.

Agora a configuração vem do pipeline e é conferida no fim dele.

### Pré-requisitos na AWS (uma vez, e não dá para o pipeline fazer)

1. **O bucket existe** e está na região que você vai declarar.

2. **A IAM Role da instância** (`a Role da instância`) tem permissão no bucket. O
   mínimo:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
         "Resource": "arn:aws:s3:::SEU_BUCKET/*"
       },
       {
         "Effect": "Allow",
         "Action": ["s3:ListBucket"],
         "Resource": "arn:aws:s3:::SEU_BUCKET"
       }
     ]
   }
   ```

   `ListBucket` no bucket (sem `/*`) e as demais nos objetos (com `/*`) — é a
   distinção que mais gera "Access Denied" sem explicação.

3. **O IMDSv2 precisa alcançar o container.** Esta é a pegadinha que mais
   custa tempo: a aplicação roda **dentro do Docker**, e o
   `DefaultCredentialsProvider` busca a credencial da Role no endpoint de
   metadados da instância. O limite de saltos de rede padrão é `1`, que a
   rede do container já consome — o SDK não recebe credencial nenhuma e a
   mensagem fala de credencial, não de rede.

   ```bash
   aws ec2 modify-instance-metadata-options \
     --instance-id i-xxxxxxxxxxxx \
     --http-put-response-hop-limit 2 \
     --http-tokens required
   ```

### O que configurar no GitHub

Em **Settings → Secrets and variables → Actions → Secrets** (o workflow lê
`secrets`, não `vars`: uma Variable com o mesmo nome não é enxergada, e o
GitHub mascara o valor nos logs):

| Secret | Valor |
|---|---|
| `APP_STORAGE_TYPE` | `s3` |
| `APP_STORAGE_S3_BUCKET` | `SEU_BUCKET` (secret do ambiente, não do repositório) |
| `APP_STORAGE_S3_REGION` | `sa-east-1` |

Sem `APP_STORAGE_TYPE` definido, o primeiro passo do deploy o **reprova** antes
de abrir a porta 22 ou tocar na instância: um deploy que não diz onde os
arquivos dos clientes vão parar não deve ir para o ar.

### O que acontece no deploy

1. `scripts/configurar-storage.sh` grava as três chaves no `.env` da
   instância. Ele recusa `s3` sem bucket e recusa tipo que não seja
   `local`/`s3`. É idempotente: rodar de novo substitui a chave, não empilha.
2. Os containers sobem.
3. Depois do health e da conferência de commit, o job compara
   `/actuator/info` com o que foi pedido:

   ```
   Storage esperado 's3', mas a instância subiu com 'local'.
   ```

   e **falha o deploy**. Sem isso, bucket inexistente, região errada ou Role
   sem permissão viravam um fallback silencioso para disco local, com o
   pipeline verde.

### Conferindo depois

```bash
curl -s http://<host>:8080/actuator/info
# {"build":{...},"storage":{"type":"s3","bucket":"SEU_BUCKET","region":"sa-east-1"}}
```

Com `type: s3` confirmado, a suíte de arquivos (`clients.files.spec.ts`) passa
a valer como prova de bucket. Antes disso ela prova apenas que o arquivo vai e
volta — o que é verdade em disco local também.
