# Deploy na EC2 ARM64 (Amazon Linux 2023, systemd, sem Docker)

Guia de execução **completo e verificado de ponta a ponta** - da infraestrutura
vazia até a aplicação respondendo, login funcionando e upload de arquivo
gravando no S3 de verdade. Cobre a instância `tm-adv-backend` (t4g.small,
Graviton2/ARM64, Amazon Linux 2023).

Este é o documento de referência para "subir o backend do zero" **hoje**.
`docs/AWS_DEPLOY_PASSO_A_PASSO.md` documenta uma instância anterior (x86/
Ubuntu, tudo via `docker-compose`, IP `3.20.238.108`) que não é mais o
ambiente ativo - mantido só como histórico; se aquela instância ainda existir,
encerre-a para não pagar as duas. Este ambiente roda o JAR direto na JVM via
systemd, com Postgres nativo - sem Docker.

**Status: 100% funcional**, testado nesta rodada: SSH, Java 25, Maven,
Postgres, clone, build, systemd, login, Swagger, upload/download de arquivo
no S3 (fluxo completo pela própria API, não só CLI) e acesso externo pela
porta 8080 - tudo confirmado.

---

## Parte A - Provisionar a infraestrutura na AWS (se for recriar do zero)

Pule esta parte se a instância já existir (é o caso hoje - `tm-adv-backend`
já está no ar). Fica documentada para recriar o ambiente do zero se a
instância for perdida/recriada, ou para replicar em outra conta.

### A.1 - Par de chaves

Console → **EC2 → Key Pairs → Create key pair** → tipo `ED25519` ou `RSA`,
formato `.pem`. Baixe e restrinja a permissão: `chmod 400 my-key.pem`.

### A.2 - Security Group

Console → **EC2 → Security Groups → Create security group**:

| Type | Port | Source |
|---|---|---|
| SSH | 22 | `SEU_IP/32` (confira em https://checkip.amazonaws.com) |
| Custom TCP | 8080 | `SEU_IP/32` |

**Nunca** libere para `0.0.0.0/0` - o modelo aqui é allowlist por IP
residencial, mais restritivo que o de outras PoCs deste projeto. Se o IP
residencial mudar, atualize as duas regras.

### A.3 - Bucket S3

Console → **S3 → Create bucket** → nome único (ex.: `amz-s3-bucket-tm`) →
região `us-east-1` (ou a mais próxima) → **Block all public access** marcado
→ criptografia SSE-S3 (padrão) → **ACLs desativadas** (Bucket Owner
Enforced, padrão atual do console).

### A.4 - IAM Role para a instância

Console → **IAM → Roles → Create role** → Trusted entity: **AWS service →
EC2** → anexar uma policy de acesso ao bucket. Duas opções:

- **Gerenciada, simples:** `AmazonS3FullAccess` (o que está em uso hoje -
  funciona, mas dá acesso a *todos* os buckets da conta, não só este).
- **Inline, restrita ao bucket (recomendado para produção):**
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:ListBucket"],
      "Resource": [
        "arn:aws:s3:::amz-s3-bucket-tm",
        "arn:aws:s3:::amz-s3-bucket-tm/*"
      ]
    }]
  }
  ```

Nomeie a role (ex.: `role-ec2-s3-tm`). **Atenção:** anexar a policy no
console não é garantia de que ela está em vigor - depois de lançar a
instância, valide de verdade com `aws s3 ls s3://SEU_BUCKET/` rodando
*dentro* da instância (ver Passo 10.1) antes de assumir que está tudo certo.
Foi exatamente isso que pegou nesta rodada: a policy aparecia anexada, mas
`s3:PutObject`/`s3:ListBucket` continuavam negados até revisar a permissão
de verdade no console.

### A.5 - Lançar a instância EC2

Console → **EC2 → Launch Instance**:

1. **AMI:** Amazon Linux 2023 (arm64).
2. **Instance type:** `t4g.small` (Graviton2/ARM64 - **atenção**: todo
   pacote/binário instalado depois precisa ser build `aarch64`, não `x86_64`).
3. **Key pair:** a criada no passo A.1.
4. **Security group:** o criado no passo A.2.
5. **IAM instance profile:** a role criada no passo A.4.
6. **Storage:** gp3, tamanho conforme necessidade (8 GiB funciona para uma
   PoC com Postgres + app no mesmo disco, mas acompanhe o uso).
7. **Launch.**

### A.6 - Budget (recomendado)

Console → **Billing → Budgets → Create budget** → orçamento mensal com
alertas em 50/80/100% - trava a instância automaticamente ao bater o teto,
evitando surpresa de cobrança.

---

## Infraestrutura desta instância (referência rápida)

| Item | Valor |
|---|---|
| Instância | `tm-adv-backend` / `i-0604e41d1c496a9e3` / `t4g.small` (ARM64/Graviton2) |
| SO | Amazon Linux 2023.12 |
| IP público | `18.208.158.75` (DNS: `ec2-18-208-158-75.compute-1.amazonaws.com`) |
| Usuário SSH | `ec2-user` |
| Security Group | `sg-04a73fdd7609e52e9` (único SG anexado à interface de rede - confirmado via instance metadata) - portas 22 e 8080 restritas ao IP residencial |
| IAM Role | `role-ec2-s3-tm` (`AmazonS3FullAccess`, confirmado funcionando via `aws s3 ls`/`cp` na instância) |
| Bucket S3 | `amz-s3-bucket-tm` (região `us-east-1`, acesso público bloqueado, SSE-S3) |
| Disco | 8 GiB gp3 (app + Postgres no mesmo disco - acompanhar uso) |
| Java | Amazon Corretto 25.0.3 (aarch64), via `dnf` direto |
| Postgres | 18.4, nativo (pacotes `postgresql18` + `postgresql18-server` + `postgresql18-contrib`) |

---

## Parte B - Software (executado e validado nesta instância)

### Passo 1 - Conectar via SSH

```bash
chmod 400 my-key.pem
ssh -i my-key.pem ec2-user@18.208.158.75
```

### Passo 2 - Instalar Java 25 (ARM64)

Disponível direto no repositório padrão do AL2023:

```bash
sudo dnf install -y java-25-amazon-corretto-devel
java -version
```

Confirmado: `openjdk version "25.0.3"`, `Corretto-25.0.3.9.1`, build `aarch64`.

### Passo 3 - Instalar Maven (e corrigir a versão de Java que ele usa)

```bash
sudo dnf install -y maven
```

**Pegadinha real:** o pacote `maven` traz `java-17-amazon-corretto` como
dependência e escreve `/etc/java/maven.conf` apontando `JAVA_HOME` pro 17 -
mesmo com `java`/`javac` corretamente em 25 via `alternatives`. Sem corrigir,
o build falha com `release version 25 not supported` (mesmo erro que
apareceu no `Dockerfile` deste projeto antes de ser corrigido).

```bash
sudo sed -i 's#JAVA_HOME=/usr/lib/jvm/java-17#JAVA_HOME=/usr/lib/jvm/java-25-amazon-corretto.aarch64#' /etc/java/maven.conf
mvn -version
```

Confirmado: `Java version: 25.0.3, ... runtime:
/usr/lib/jvm/java-25-amazon-corretto.aarch64`.

### Passo 4 - Instalar e configurar o PostgreSQL (nativo, sem Docker)

```bash
sudo dnf install -y postgresql18 postgresql18-server postgresql18-contrib
```

**`postgresql18-contrib` é obrigatório:** sem ele a extensão `unaccent` não
existe, e a primeira migration do Flyway (`V1__extensions.sql`) derruba a
aplicação com `extension "unaccent" is not available`.

```bash
sudo postgresql-setup --initdb
sudo systemctl enable --now postgresql
sudo systemctl status postgresql --no-pager
```

(Nome do serviço/script **não é versionado** neste pacote, apesar de
`postgresql18` no nome do pacote.)

```bash
sudo -u postgres psql -c "ALTER ROLE postgres WITH PASSWORD 'TROQUE_ESTA_SENHA';"
sudo -u postgres createdb system
```

Habilitar autenticação por senha no loopback (AL2023 configura `ident` por
padrão, que não serve pro JDBC via TCP):

```bash
sudo sed -i '/^host *all *all *127.0.0.1\/32/s/ident/scram-sha-256/; /^host *all *all *::1\/128/s/ident/scram-sha-256/' /var/lib/pgsql/data/pg_hba.conf
sudo systemctl restart postgresql
psql "postgresql://postgres:TROQUE_ESTA_SENHA@localhost:5432/system" -c '\conninfo'
```

`Password Used: true` na saída confirma que funcionou.

### Passo 5 - Clonar o projeto

Repositório público - não precisa de token:

```bash
sudo dnf install -y git
cd ~
git clone --branch develop https://github.com/yuriandrade91/taniamelo-advocacia-backend.git
cd taniamelo-advocacia-backend
```

### Passo 6 - Build do projeto

```bash
mvn -B clean package -DskipTests
ls -la target/*.jar
```

Gera `target/law-firm-0.0.1-SNAPSHOT.jar`. `-DskipTests` porque a suíte é
`@SpringBootTest` e sobe o contexto completo; com o Postgres já de pé dá pra
rodar `mvn clean verify` sem o flag se quiser validar.

### Passo 7 - Variáveis de ambiente (arquivo usado pelo systemd)

```bash
openssl rand -base64 48   # APP_JWT_SECRET
openssl rand -base64 32   # APP_ENCRYPTION_KEY
```

```bash
sudo mkdir -p /etc/law-firm
sudo tee /etc/law-firm/law-firm.env > /dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=postgres
SERVER_PORT=8080

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/system
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=TROQUE_ESTA_SENHA

APP_JWT_SECRET=COLE_AQUI_O_JWT_SECRET_GERADO
APP_ENCRYPTION_KEY=COLE_AQUI_A_CHAVE_DE_CRIPTOGRAFIA_GERADA

APP_ADMIN_EMAIL=dra.tania@taniamelo.adv.br
APP_ADMIN_PASSWORD=escolha-uma-senha-forte-aqui

APP_CORS_ALLOWED_ORIGINS=*

# S3: a instância tem a IAM Role role-ec2-s3-tm anexada - nenhuma chave de
# acesso entra aqui, o SDK resolve via DefaultCredentialsProvider.
APP_STORAGE_TYPE=s3
APP_STORAGE_S3_BUCKET=amz-s3-bucket-tm
APP_STORAGE_S3_REGION=us-east-1
EOF

sudo chmod 600 /etc/law-firm/law-firm.env
sudo chown ec2-user:ec2-user /etc/law-firm/law-firm.env
nano /etc/law-firm/law-firm.env   # preencher os placeholders
```

**Nota:** se for popular com `seed_mock.sql` (Passo 11), ele faz `TRUNCATE`
na tabela de usuários - o admin criado automaticamente a partir de
`APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD` é substituído pelos usuários de teste
do seed (`dra.tania@taniamelo.adv.br` / `password`).

### Passo 8 - Configurar como serviço systemd

```bash
sudo tee /etc/systemd/system/law-firm.service > /dev/null <<'EOF'
[Unit]
Description=Tania Melo Advocacia - Backend (Spring Boot)
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user/taniamelo-advocacia-backend
EnvironmentFile=/etc/law-firm/law-firm.env
ExecStart=/usr/lib/jvm/java-25-amazon-corretto.aarch64/bin/java -jar /home/ec2-user/taniamelo-advocacia-backend/target/law-firm-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now law-firm
sleep 10
sudo systemctl status law-firm --no-pager
```

Confira `ls target/*.jar` se o nome do jar mudar e ajuste o `ExecStart`.

### Passo 9 - Verificar logs

```bash
sudo journalctl -u law-firm -f          # tempo real
sudo journalctl -u law-firm -n 100 --no-pager
```

Confirmado: `Started LawFirmApplication in 13.319 seconds`,
`ObjectStorageFileStorageService ativo (bucket=amz-s3-bucket-tm)`.

### Passo 10 - Testar a porta 8080 e o S3

**10.1 - De dentro da instância** (sempre funciona, independe do Security Group):

```bash
curl -sS http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}

# Valide o S3 isolado da aplicação, direto com o AWS CLI - se isso falhar,
# o problema é IAM, não a app:
aws s3 ls s3://amz-s3-bucket-tm/
```

**10.2 - De fora, do IP liberado no Security Group** - confirmado
funcionando nesta rodada:

```bash
curl -sS --connect-timeout 8 http://18.208.158.75:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

**10.3 - Smoke test completo pela API** (login → upload → download →
exclusão), confirmado funcionando ponta a ponta:

```bash
# Login
TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dra.tania@taniamelo.adv.br","password":"password"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['token'])")

# Pega um clientId qualquer (requer dado populado - ver Passo 11)
CLIENT_ID=$(curl -sS "http://localhost:8080/api/v1/clients?pageNumber=1&pageSize=1" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import json,sys; print(json.load(sys.stdin)['data'][0]['id'])")

# Upload
printf '%%PDF-1.4\nteste' > /tmp/teste.pdf
curl -sS -X POST "http://localhost:8080/api/v1/clients/$CLIENT_ID/files/documents" \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@/tmp/teste.pdf;type=application/pdf" \
  -F 'metadata=[{"documentType":"Documentos de identificação do segurado"}];type=application/json'
```

---

## Passo 11 - Popular com dado mockado

Arquivo único, autocontido (125 clientes: 5 com dado detalhado de exemplo +
120 de massa variada) - confirmado rodando de ponta a ponta nesta instância:

```bash
cd ~/taniamelo-advocacia-backend
PGPASSWORD='TROQUE_ESTA_SENHA' psql -h localhost -U postgres -d system \
  -f db/mock-data/seed_mock.sql
```

## Atualizar depois de um novo commit (manual)

```bash
cd ~/taniamelo-advocacia-backend
git pull origin develop
mvn -B clean package -DskipTests
sudo systemctl restart law-firm
sudo journalctl -u law-firm -f
```

## Deploy automático (CD)

`.github/workflows/deploy.yml` já executa exatamente esse fluxo por SSH a cada
push na `develop` (SSH + `git reset --hard origin/develop` + `mvn package` +
`systemctl restart law-firm`, sem Docker). Configuração dos secrets
(`EC2_HOST`/`EC2_USER`/`EC2_SSH_KEY`), do ambiente `production` e do
pré-requisito de `sudo` sem senha para `systemctl restart law-firm` estão em
`docs/CI_CD.md`.

## Troubleshooting

| Sintoma | Causa provável |
|---|---|
| `ssh` trava/timeout | Porta 22 fechada no SG - ver Parte A.2 |
| Build falha com `release version 25 not supported` | `/etc/java/maven.conf` ainda aponta pro Java 17 - ver Passo 3 |
| App cai com `extension "unaccent" is not available` | Faltou instalar `postgresql18-contrib` - ver Passo 4 |
| App cai com erro de autenticação Postgres | Senha divergente entre `pg_hba.conf`/`ALTER ROLE` (Passo 4) e `SPRING_DATASOURCE_PASSWORD` (Passo 7), ou esqueceu de trocar `ident` por `scram-sha-256` |
| Upload de arquivo falha com `AccessDenied` no S3 | Teste `aws s3 ls s3://SEU_BUCKET/` direto na instância - se falhar também, é a IAM Role (revisar Permissions no console, mesmo que a policy pareça anexada); se só a app falhar, é bug na aplicação |
| `curl` externo não responde mas local funciona | Conferir a regra exata do SG (porta/CIDR) e os Network ACLs da subnet - app/firewall do SO não costumam ser a causa (confirme com `sudo ss -tlnp \| grep 8080` mostrando `*:8080`) |
| Instância para sozinha | Budget travou a instância automaticamente - confira o alerta de billing antes de reiniciar |
