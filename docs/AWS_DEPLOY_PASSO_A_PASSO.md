# Passo a passo: colocar o backend no ar na AWS (com o crédito já disponível)

> **⚠️ Histórico - instância substituída.** Este documento cobre a instância
> x86/Ubuntu original (`3.20.238.108`, tudo via `docker-compose`). O ambiente
> ativo hoje é ARM64/Amazon Linux 2023, sem Docker - ver
> `docs/AWS_EC2_ARM64_DEPLOY.md`, que é o guia corrente e verificado de ponta
> a ponta. Mantido aqui só como referência histórica; se a instância antiga
> ainda existir, encerre-a para não pagar as duas.

Guia de execução, no nível de "clique aqui, rode este comando". Pressupõe
que você já tem conta AWS com crédito ativo. Para o *porquê* de cada decisão
(EC2 vs Lightsail, self-hosted vs RDS, quando migrar pra produção real),
ver `docs/AWS_TESTE_GRATUITO.md` - este documento aqui é só a execução.

Tempo estimado: 45-60 min na primeira vez.

---

## Antes de começar

- [ ] Confirme o crédito em **Console AWS → Billing → Free Tier / Credits**.
- [ ] Tenha em mãos: um terminal com `ssh` e `git`, e a URL do repositório
      (GitHub).
- [ ] Todo comando `ssh`/dentro da instância abaixo assume Linux/macOS. No
      Windows, use o PowerShell ou WSL.

---

## Passo 1 - Proteger a conta (5 min)

Faça isso **antes** de criar qualquer recurso - evita cobrança surpresa.

1. **MFA na conta root:** Console → clique no seu nome (canto superior
   direito) → **Security credentials** → **Assign MFA device**. Use o app
   de autenticação do seu celular (Google Authenticator, Authy, etc.).
2. **Alerta de orçamento:** Console → busque **Budgets** → **Create budget**
   → *Monthly cost budget* → defina US$5 → em **Alert threshold** coloque
   80% (US$4) → informe seu e-mail. Isso avisa antes do crédito estourar ou
   de algo ficar ligado sem querer.
3. **Usuário IAM para o dia a dia** (não usar root depois de hoje):
   Console → **IAM** → **Users** → **Create user** → nome `deploy-admin` →
   marque **Provide user access to the AWS Management Console** (opcional)
   → em permissões, anexe a policy gerenciada `AdministratorAccess` (é só
   você usando, então é aceitável simplificar aqui; numa conta com mais
   gente, restringir a EC2+S3+Budgets).
   - Depois de criado, gere uma **Access key** (aba *Security credentials*
     → *Create access key* → caso de uso *Command Line Interface*) e guarde
     o `.csv` num lugar seguro - você vai usar para o AWS CLI, se quiser.
4. Anote a data de hoje e a validade do crédito num lugar visível (calendário,
   nota) - esse ambiente é para teste, não para esquecer rodando.

---

## Passo 2 - Criar o par de chaves SSH (2 min)

1. Console → **EC2** → menu lateral **Key Pairs** → **Create key pair**.
2. Nome: `taniamelo-deploy`. Tipo: `RSA`. Formato: `.pem`.
3. Baixe o arquivo `taniamelo-deploy.pem` e restrinja a permissão:
   ```bash
   mv ~/Downloads/taniamelo-deploy.pem ~/.ssh/
   chmod 400 ~/.ssh/taniamelo-deploy.pem
   ```

---

## Passo 3 - Security Group (regras de firewall) (3 min)

1. Console → **EC2** → **Security Groups** → **Create security group**.
2. Nome: `taniamelo-backend-sg`. Descrição: "Backend PoC".
3. **Inbound rules** → **Add rule** (repita para cada uma):
   | Type | Port | Source |
   |---|---|---|
   | SSH | 22 | *My IP* (o console preenche seu IP atual automaticamente) |
   | HTTP | 80 | Anywhere (0.0.0.0/0) |
   | Custom TCP | 8080 | Anywhere (0.0.0.0/0) — porta da API enquanto não há proxy/HTTPS |
   | HTTPS | 443 | Anywhere (0.0.0.0/0) — só se for configurar domínio+TLS (Passo 10) |
4. **Não crie regra para a porta 5432** (Postgres). O banco roda dentro da
   mesma instância via Docker e nunca precisa ficar acessível pela internet.
5. **Create security group**.

---

## Passo 4 - Lançar a instância EC2 (5 min)

1. Console → **EC2** → **Launch Instance**.
2. **Name:** `taniamelo-backend-poc`.
3. **AMI:** Ubuntu Server 22.04 LTS (marcado "Free tier eligible").
4. **Instance type:** `t3.micro` (ou `t2.micro` se for a opção elegível ao
   free tier na sua região). 1GB RAM é suficiente para app + Postgres numa
   PoC com massa de teste.
5. **Key pair:** selecione `taniamelo-deploy` (criado no Passo 2).
6. **Network settings** → **Edit** → **Select existing security group** →
   escolha `taniamelo-backend-sg`.
7. **Configure storage:** aumente para **20 GiB** gp3 (o padrão de 8GiB é
   apertado com imagem Docker + Postgres + logs).
8. **Launch instance**.
9. Aguarde o status ficar **Running** e o *status check* **2/2 checks
   passed** (leva ~1-2 min). Anote o **Public IPv4 address** da instância
   (aparece na lista de instâncias) - vamos chamar de `SEU_IP` daqui pra
   frente.

---

## Passo 5 - Conectar e instalar Docker (10 min)

```bash
ssh -i ~/.ssh/taniamelo-deploy.pem ubuntu@SEU_IP
```

Dentro da instância:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg git

# Instalar Docker Engine + Compose plugin (repositório oficial)
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Rodar docker sem sudo
sudo usermod -aG docker ubuntu
```

Saia e reconecte para o grupo `docker` valer:

```bash
exit
ssh -i ~/.ssh/taniamelo-deploy.pem ubuntu@SEU_IP
docker --version && docker compose version
```

---

## Passo 6 - Levar o código para a instância (2 min)

```bash
git clone https://github.com/SEU_USUARIO/taniamelo-advocacia-backend.git
cd taniamelo-advocacia-backend
```

Se o repositório for privado, use um **Personal Access Token** do GitHub no
lugar da senha quando pedir autenticação (ou configure uma chave de deploy
SSH - fica a seu critério).

---

## Passo 7 - Configurar o `.env` de produção (5 min)

**Nunca reutilize os valores de `.env` local.** Gere segredos novos:

```bash
# JWT secret (32+ caracteres exigidos)
openssl rand -base64 48

# Chave de criptografia (precisa decodificar para exatamente 32 bytes)
openssl rand -base64 32

# Senha do Postgres
openssl rand -base64 24
```

Crie o `.env` na raiz do projeto (na instância):

```bash
cat > .env <<'EOF'
POSTGRES_DB=system
POSTGRES_USER=postgres
POSTGRES_PASSWORD=COLE_A_SENHA_GERADA_AQUI

SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/system
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=COLE_A_MESMA_SENHA_ACIMA

SPRING_PROFILES_ACTIVE=postgres

APP_JWT_SECRET=COLE_O_JWT_SECRET_GERADO_AQUI
APP_ENCRYPTION_KEY=COLE_A_CHAVE_DE_CRIPTOGRAFIA_AQUI

APP_ADMIN_EMAIL=dra.tania@taniamelo.adv.br
APP_ADMIN_PASSWORD=escolha-uma-senha-forte-aqui

# Domínio do frontend, quando existir publicamente. Enquanto for só teste
# interno, "*" é aceitável - nunca deixe "*" com dado real de cliente.
APP_CORS_ALLOWED_ORIGINS=*

APP_STORAGE_TYPE=local
APP_STORAGE_LOCAL_PATH=/app/storage
EOF
```

Edite os placeholders com os valores gerados acima:

```bash
nano .env
```

> `SPRING_PROFILES_ACTIVE=postgres` usa `application-postgres.yaml` (perfil
> de banco real, sem os ajustes de hot-reload do perfil `dev`). Confira em
> `src/main/resources/application-postgres.yaml` se algo específico do seu
> ambiente precisa de ajuste antes de subir.

---

## Passo 8 - Subir a aplicação (5 min)

```bash
docker compose --profile full up -d --build
```

Isso builda a imagem (Dockerfile multi-stage: Maven → JRE), sobe o Postgres,
espera ele ficar saudável (`wait-for-db.sh`) e sobe a aplicação, que roda as
migrations do Flyway automaticamente na subida.

Acompanhe os logs até ver a aplicação subir:

```bash
docker compose logs -f app
```

Teste local, de dentro da instância:

```bash
curl -sS http://localhost:8080/actuator/health
```

Deve responder `{"status":"UP"}`. Agora teste de fora, na sua máquina:

```bash
curl -sS http://SEU_IP:8080/actuator/health
```

Se responder, a API já está publicamente acessível. Abra
`http://SEU_IP:8080/api/docs` no navegador para ver o Swagger.

---

## Passo 9 - Popular com dados de teste e validar (5 min)

Dentro da instância, com os containers já de pé:

```bash
docker compose cp db/mock-data/seed_mock.sql db:/tmp/seed_mock.sql
docker compose cp db/mock-data/seed_mock_bulk_100.sql db:/tmp/seed_mock_bulk_100.sql
docker compose exec db psql -U postgres -d system -f /tmp/seed_mock.sql -f /tmp/seed_mock_bulk_100.sql
```

Teste de login pela API (da sua máquina local):

```bash
curl -sS -X POST http://SEU_IP:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dra.tania@taniamelo.adv.br","password":"password"}'
```

Deve devolver um token JWT. A partir daqui, use o Postman ou o frontend
apontando para `http://SEU_IP:8080` para validar o fluxo completo.

---

## Passo 10 - Bucket S3 para os arquivos (10 min)

O código já tem `ObjectStorageFileStorageService` pronto
(`src/main/java/com/lawfirm/law/firm/storage/`) - trocar de disco local para
armazenamento de objetos é só configuração, sem mexer em código. O nome não é
"S3FileStorageService" de propósito: o cliente aponta pra qualquer endpoint
compatível com a API S3 (AWS, Cloudflare R2, MinIO), então o código não fica
preso a um provedor específico mesmo usando o SDK da AWS por baixo.

### 10.1 - Criar o bucket

1. Console → **S3** → **Create bucket**.
2. Nome: `taniamelo-backend-poc` (nomes de bucket são globais - se já
   existir, acrescente algo como `-2026`).
3. Região: `South America (São Paulo) sa-east-1` (a mesma da instância
   EC2, para latência e para não pagar transferência entre regiões).
4. Deixe **Block all public access** marcado (arquivos de cliente nunca
   devem ser públicos).
5. **Create bucket**.

### 10.2 - Criar uma IAM Role para a instância (em vez de access key fixa)

Mais seguro que gerar uma access key: a instância assume uma *role* e o
`DefaultCredentialsProvider` do SDK (já usado em `ObjectStorageConfig.java`)
pega as credenciais automaticamente, sem nenhuma chave no `.env`.

1. Console → **IAM** → **Roles** → **Create role**.
2. **Trusted entity type:** AWS service → **EC2**.
3. Próxima tela: pule as policies gerenciadas por enquanto → **Next** →
   nome `taniamelo-backend-ec2-role` → **Create role**.
4. Abra a role criada → **Add permissions** → **Create inline policy** →
   aba **JSON** → cole:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:ListBucket"],
         "Resource": [
           "arn:aws:s3:::taniamelo-backend-poc",
           "arn:aws:s3:::taniamelo-backend-poc/*"
         ]
       }
     ]
   }
   ```
5. Nome da policy: `taniamelo-s3-poc-access` → **Create policy**.

### 10.3 - Anexar a role na instância EC2

1. Console → **EC2** → selecione a instância `taniamelo-backend-poc`.
2. **Actions** → **Security** → **Modify IAM role** → selecione
   `taniamelo-backend-ec2-role` → **Update IAM role**. Não precisa
   reiniciar a instância.

### 10.4 - Trocar a configuração da aplicação

Na instância, edite o `.env`:

```bash
nano .env
```

Altere:

```
APP_STORAGE_TYPE=s3
APP_STORAGE_S3_BUCKET=taniamelo-backend-poc
APP_STORAGE_S3_REGION=sa-east-1
```

Reinicie só o container da aplicação:

```bash
docker compose up -d --build app
```

Teste um upload de arquivo pela API (ou pelo Swagger) e confira no console
S3 que o objeto apareceu no bucket.

---

## Passo 11 (opcional) - Domínio e HTTPS

Enquanto for só teste interno, acessar por `http://SEU_IP:8080` é
suficiente. Se o frontend for testado publicamente e precisar de HTTPS:

1. Aponte um subdomínio (ex.: `api-teste.taniamelo.adv.br`) para `SEU_IP`
   via registro DNS tipo `A` no provedor do domínio.
2. Instale o Caddy (proxy reverso com HTTPS automático via Let's Encrypt,
   mais simples que Nginx+Certbot manual):
   ```bash
   sudo apt-get install -y debian-keyring debian-archive-keyring apt-transport-https
   curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
   curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
   sudo apt-get update && sudo apt-get install -y caddy
   ```
3. Configure `/etc/caddy/Caddyfile`:
   ```
   api-teste.taniamelo.adv.br {
       reverse_proxy localhost:8080
   }
   ```
4. `sudo systemctl restart caddy` - o Caddy obtém e renova o certificado TLS
   sozinho. Depois disso, atualize `APP_CORS_ALLOWED_ORIGINS` no `.env` para
   o domínio real do frontend (nunca deixe `*` com um domínio público
   estável em produção).

---

## Comandos do dia a dia

```bash
# Ver logs da aplicação
docker compose logs -f app

# Reiniciar só a aplicação (ex.: depois de mudar o .env)
docker compose up -d --build app

# Atualizar para a última versão do código
git pull
docker compose up -d --build app

# Parar tudo (sem apagar dados)
docker compose down

# Ver status dos containers
docker compose ps
```

---

## Checklist final

- [ ] MFA na conta root ativo
- [ ] Budget alert configurado
- [ ] Usuário IAM próprio criado (parou de usar root)
- [ ] Instância EC2 no ar, Security Group sem a porta 5432 aberta
- [ ] `docker compose ps` mostra `app` e `db` saudáveis (`healthy`)
- [ ] `curl http://SEU_IP:8080/actuator/health` responde `UP` de fora da instância
- [ ] Login via `/api/auth/login` funcionando com os dados mockados
- [ ] Upload de arquivo indo para o bucket S3 (Passo 10)
- [ ] Swagger acessível em `/api/docs`

## Se algo der errado

| Sintoma | Onde olhar |
|---|---|
| `docker compose up` trava em "waiting for db" | `docker compose logs db` - geralmente `.env` com senha divergente entre `POSTGRES_PASSWORD` e `SPRING_DATASOURCE_PASSWORD` |
| App builda mas cai logo depois de subir | `docker compose logs app` - erro de Flyway (migration) ou `APP_JWT_SECRET`/`APP_ENCRYPTION_KEY` ausente/curto demais |
| `curl` funciona de dentro da instância mas não de fora | Conferir Security Group (porta 8080 liberada para `0.0.0.0/0`) |
| Upload de arquivo falha com `AccessDenied` (S3) | Conferir se a IAM Role foi mesmo anexada à instância (Passo 10.3) e se o nome do bucket no `.env` bate com o real |

## Próximos passos

Quando o crédito estiver perto de acabar, decida entre migrar para uma
configuração paga (ver `docs/INFRA_PLAN.md`) ou desligar tudo (terminar a
instância EC2, esvaziar e apagar o bucket S3, remover o Budget) - detalhado
na Fase 5 de `docs/AWS_TESTE_GRATUITO.md`.
