# Plano de virada: `develop` → `main` → produção

**Levantado em 06/11/2026**, sobre a `develop` em `0d1e5dd`. Produção **ainda não existe**: a instância
atual é o ambiente `development` do GitHub.

**Estado em 06/11/2026 (PR `release/main-producao` → `main`):** os itens marcados `[x]` abaixo foram feitos
nessa branch. A `main` sai **sem seed, sem seeders e sem ferramentas de dev**; a `develop` continua como está
(ver "Main e develop divergem de propósito" no fim).

Este documento consolida o que antes estava espalhado e **não substitui** os outros, que continuam
sendo a fonte de cada assunto. Onde um item já tem roteiro, o link está na linha.

| Assunto | Documento |
| --- | --- |
| Pendências e riscos conhecidos | [MELHORIAS_E_PENDENCIAS.md](MELHORIAS_E_PENDENCIAS.md) |
| Pipeline e secrets do GitHub | [CI_CD.md](CI_CD.md) |
| Subir a instância na AWS | [AWS_EC2_ARM64_DEPLOY.md](AWS_EC2_ARM64_DEPLOY.md) |
| Backup e restore do banco | [BACKUP.md](BACKUP.md) |
| Cadastrar o escritório (tenant) | [CADASTRO_DE_TENANT.md](CADASTRO_DE_TENANT.md) |
| Teste de intrusão | [PENTEST.md](PENTEST.md) |
| Observabilidade | [OBSERVABILIDADE.md](OBSERVABILIDADE.md) |
| Decisões de infra e orçamento | [INFRA_PLAN.md](INFRA_PLAN.md) |

Legenda: **[BLOQUEIA]** = não dá para virar sem isto. **[DECIDIR]** = precisa de uma escolha sua antes.
Cada achado vem com o comando ou arquivo que o provou.

---

## 0. Decisões que destravam o resto

| # | Decisão | Minha recomendação |
| --- | --- | --- |
| D1 | **Tamanho dos arquivos (10 a 60 MB).** Ver seção 4. | Cifra em blocos (caminho B). Com 60 MB, um download ocupa cerca de 300 MB de heap; a t4g.small tem cerca de 512 MB. **Em aberto.** |
| D2 | **Repositório público com dado real no histórico.** | **Decidido: vai ficar privado** (a mudança é feita pelo dono no GitHub). Falta o clone da instância passar a usar *deploy key*. Ver 1.3. |
| D3 | **Postgres:** container na mesma EC2 ou RDS. | **Decidido: container na EC2**, com snapshot do EBS e dump fora da instância. |
| D4 | **TLS e domínio.** | **Decidido: proxy próprio (Caddy) na EC2.** Passo a passo em [PROXY_TLS.md](PROXY_TLS.md). |
| D5 | **Região do bucket.** | `sa-east-1`, por latência e por dado de cliente brasileiro. **Em aberto.** |
| D6 | **Seed e schema do escritório real.** | **Decidido: produção vai zerada, sem seed nem inserção.** A `main` não tem mais migration de massa, seeders nem linhas de tenant no catálogo. O schema do escritório real é escolhido no cadastro (`scripts/tenant/01`). |

---

## 1. Projeto local e o merge para `main`

### 1.1 Estado do git

- `develop` está 261 commits à frente da `main`, e a `main` não tem nenhum commit que a `develop`
  não tenha (`git log main..develop` / `develop..main`). O merge é *fast-forward*.
- A branch padrão do GitHub é `develop`, e **nenhuma das duas tem proteção**
  (`gh api repos/.../branches/{main,develop}/protection` devolve 404).

- [ ] **[BLOQUEIA]** Proteger `main` e `develop`: exigir os jobs `build`, `cobertura-rotas`, `sonar`,
      `semgrep` e `docker-build`, exigir PR e proibir force-push. Roteiro em [CI_CD.md](CI_CD.md) (hoje só descreve a `develop`).
- [ ] Decidir se a branch padrão passa a ser `main` (o deploy de produção seguirá a `main`).
- [ ] Commitar o que está em andamento (lixeira de arquivos e este plano) em commits por tema, como pede o `CLAUDE.md`.
- [ ] Abrir o PR `develop` → `main`, com CI verde e Quality Gate do Sonar aprovado.
- [ ] Marcar uma tag (`v1.0.0`) no commit que for para produção. É o que permite voltar a uma versão conhecida.

### 1.2 Arquivos vazios, sobras e configuração que não vale para produção

**Arquivos vazios no repositório** (`find` e `git ls-files`):

| Arquivo | Situação | Ação |
| --- | --- | --- |
| `src/main/resources/db/migration/V1__create_clients_table.sql` | 0 bytes, versionado. O migrator só lê `db/migration/shared` e `db/migration/tenant`, então é código morto que confunde. | Remover. **Feito.** |
| `src/main/resources/db/migration/V2__create_client_situation_history_table.sql` | Idem. | Remover. **Feito.** |
| `README-DOCKER.md` (raiz) | 0 bytes. O conteúdo real está em `docs/README-DOCKER.md`, que é o que o índice cita. | Remover o da raiz. **Feito.** |
| `api-tests/postman/reports/.gitkeep` | Vazio de propósito. | Manter. |

**Sobras locais** (todas já ignoradas pelo `.gitignore`, não precisam de ação no repositório): `hs_err_pid34753.log`,
`Claude outputs/`, `backups/` (3 dumps com dado de cliente), `storage/` (arquivos de teste), `logs/`.
Vale apagar `hs_err_pid*.log` e `backups/` da máquina antes de qualquer cópia do diretório.
`mvnw.cmd` existe sem o `mvnw` correspondente (o `Dockerfile` trata os dois casos).

**Documentação com referência quebrada ou desatualizada:**

- [ ] `PROVISIONAMENTO_INFRA.md` e `INFRA_PLAN.md` citam `docs/AWS_TESTE_GRATUITO.md`, `docs/AWS_DEPLOY_PASSO_A_PASSO.md`
      e `docs/custos_hospedagem.xlsx`, que **não existem** (conferido com `ls docs`).
- [x] `CI_CD.md` e `AWS_EC2_ARM64_DEPLOY.md` descrevem deploy por `systemd`, sem Docker. O `deploy.yml` real
      usa `docker compose --profile full`. Só a seção "Storage dos arquivos" do segundo está atualizada.
      Reescrever o fluxo de deploy para o que existe, ou alguém seguirá o roteiro errado numa emergência.
- [ ] `docs/README.md` descreve `AWS_EC2_ARM64_DEPLOY.md` como "systemd".
- [ ] `ROADMAP.md` ainda lista como adiado o que já existe (item 7 de [MELHORIAS_E_PENDENCIAS](MELHORIAS_E_PENDENCIAS.md)).

**Arquivo `.env`** (a referência é o `.env.example`; o `.env` real não é versionado):

- [x] **[BLOQUEIA]** `SPRING_PROFILES_ACTIVE=dev` está no `.env.example` **e** no `.env` local. Em produção tem de ser
      `postgres`. Nada no deploy confere isso: com `dev` o tracing sobe a 100% e o CORS `*` é permitido (`CorsConfig`).
      Fazer o `restart-app-docker.sh` recusar `dev`.
- [x] **[BLOQUEIA]** O `docker-compose.yml` **não repassa** ao container estas variáveis, então defini-las no `.env` não tem efeito
      (`grep` no compose não acha nenhuma):
      `APP_AUTH_REFRESH_COOKIE_SECURE` e `APP_AUTH_REFRESH_COOKIE_SAMESITE` (hoje o cookie do refresh sai sem `Secure`),
      `APP_SEED_DEMO_DATA` (ver adiante), `APP_JWT_EXPIRATION_MINUTES`, `APP_JWT_REFRESH_EXPIRATION_DAYS`
      e opções da JVM (`JAVA_TOOL_OPTIONS`). Declará-las no compose e no `.env.example`.
- [ ] Preencher no `.env` de produção, com valores novos e nunca os do dev: `POSTGRES_PASSWORD`, `APP_JWT_SECRET`
      (48 bytes), `APP_ENCRYPTION_KEY`, `APP_FILE_ENCRYPTION_KEY` (diferente da anterior), `APP_ADMIN_*`, `APP_SUPPORT_*`
      e `APP_CORS_ALLOWED_ORIGINS` com o domínio real do front. O script de deploy já recusa placeholders e as chaves de dev.
- [ ] `GRAFANA_ADMIN_PASSWORD` só se a observabilidade subir no mesmo servidor.

**Primeiro boot em banco novo: o que a aplicação semeia sozinha**

- [x] **[BLOQUEIA]** `app.seed.demo-data` tem default **`true`** (`MultiTenantFlywayMigrator`). As migrations V23, V25 e V29 inserem massa de
      demonstração (a V29 faz `TRUNCATE` antes) nos schemas `tenant_demo` e `tenant_tania`. Num banco novo, se o escritório real usar o
      schema `tenant_tania`, ele nasce **cheio de cliente fictício**, e o Flyway registra as migrations como aplicadas, então não
      se desfaz sem trabalho manual. Opções: (a) subir a primeira vez com `APP_SEED_DEMO_DATA=false` (depende do item do compose acima);
      (b) usar um schema novo para o escritório real. Recomendo as duas.
- [x] **[BLOQUEIA]** `scripts/restart-app-docker.sh` (etapa 6) popula `tenant_tania` e `tenant_demo` com `db/mock-data/seed_tenant_*.sql`
      quando o schema não tem clientes. Em produção isso despeja dado de teste no escritório real. Pôr atrás de uma variável
      (`SEED_MOCK=false` por padrão) ou remover a etapa do caminho de produção.
- [x] **[DECIDIR]** `shared/V2__tenants.sql` cadastra `tenant_tania` (CNPJ fictício `12.345.678/0001-90`) e `tenant_demo` como `ativo`.
      Migration aplicada não se edita, e a instância de dev já a aplicou. Em produção: corrigir razão social e CNPJ do escritório real por SQL
      e inativar o `tenant_demo` (roteiro em [CADASTRO_DE_TENANT.md](CADASTRO_DE_TENANT.md) e `scripts/tenant/`).
- [x] **[BLOQUEIA]** Garantir que o `tenant_demo` **não** fica ativo em produção. Ele é público pelo slug `demo`.

### 1.3 Segurança do repositório

- [ ] **[BLOQUEIA, DECIDIR]** O repositório é **público** (`gh repo view`: `visibility: PUBLIC`) e o deploy clona sem token.
      [MELHORIAS_E_PENDENCIAS](MELHORIAS_E_PENDENCIAS.md) registra que o commit `897c9ae` tem nomes e e-mails reais de usuários do escritório Prado.
      Dado de escritório de advocacia não deveria estar em repositório público, nem no histórico.
      Caminhos: tornar privado (o deploy passa a precisar de *deploy key* na instância; conferir a regra do SonarCloud para repositório privado)
      ou reescrever o histórico e fazer *force-push*. Torná-lo privado não apaga cópias que já existam.
- [ ] Rodar um scanner de segredos no histórico completo (por exemplo, `gitleaks detect`) antes do merge.
- [ ] Na `.env`/Secrets: rotacionar o que já passou por conversa, log ou commit.
- [ ] `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` estão nos secrets do repositório (chave de longa duração, criada em jul/2026) para o
      deploy abrir e fechar a porta 22. Recomendo OIDC do GitHub com uma role mínima. Se ficar com chave, o usuário IAM só pode ter
      `ec2:AuthorizeSecurityGroupIngress` e `ec2:RevokeSecurityGroupIngress` naquele SG.

### 1.4 Código que a virada exige fechar

Itens abertos em [MELHORIAS_E_PENDENCIAS](MELHORIAS_E_PENDENCIAS.md), mais o que confirmei agora:

- [ ] `/actuator/prometheus` é público (`SecurityConfig`). Restringir por rede ou autenticação.
- [ ] Swagger público (`/api/docs`, `/v3/api-docs`), com exemplos de login. Desligar em produção ou retirar os exemplos.
- [x] `JwtService` completa segredo curto com zeros (linhas 33 a 36). Recusar o boot fora de dev/test, como o `CorsConfig`.
- [x] Não há `server.forward-headers-strategy` nem cabeçalho HSTS. Atrás de proxy TLS, sem isso o Spring enxerga HTTP e o IP do proxy.
      O *throttle* de login lê o `X-Forwarded-For` (`AuthController.clientIp`), que quem chega direto na instância pode forjar: o proxy
      deve sobrescrever o cabeçalho, e a 8080 não pode ficar acessível por fora dele.
- [ ] Access token continua válido por até 8 h depois de trocar a senha. Encurtar o TTL ou versionar o token.
- [ ] MIME conferido só pelo `Content-Type` declarado; falta conferir a assinatura do arquivo. Antivírus segue na Fase C.
- [ ] Senha do INSS exposta antes da correção de 01/11: decidir se os clientes precisam trocar a senha do Meu INSS.
- [ ] Exclusão definitiva: hoje nada é apagado (decisão desta rodada). Registrar a política de retenção para o caso de pedido de exclusão (LGPD).

---

## 2. Pipeline para `main` e produção

O `deploy.yml` atual segue a `develop`, usa `environment: development` e o secret `APP_STORAGE_S3_BUCKET_DEV`.
Os ambientes `development` e `production` existem no GitHub, mas **todos os secrets são do repositório**, não de ambiente
(`gh secret list`).

- [ ] **[BLOQUEIA]** Criar `deploy-producao.yml` (ou parametrizar o atual): gatilho na `main` ou em tag, `environment: production` com
      **Required reviewers**, e secrets *do ambiente* (`EC2_HOST`, `EC2_SG_ID`, `APP_STORAGE_S3_BUCKET`, `APP_STORAGE_S3_REGION`).
      Hoje um erro de digitação poderia fazer o deploy de dev cair em produção, ou o contrário.
- [ ] Mover os secrets de dev para o ambiente `development` e criar os de produção sem reaproveitar valor algum.
- [ ] A checagem de commit e de storage do `deploy.yml` já vale para produção. Acrescentar a de **perfil** (`postgres`) e a de `demo-data`.
- [ ] Definir o rollback: o deploy reconstrói a imagem no servidor e a anterior fica órfã até o `prune`. Para voltar, `git reset` na tag anterior
      e `restart-app-docker.sh` serve, mas precisa estar escrito e testado. Melhor: publicar a imagem com a tag do commit.
- [ ] Rodar a suíte de contrato (Playwright/Postman) contra a instância depois do deploy, em *staging*. **Nunca** contra produção com dado real.
      (item 3 de [MELHORIAS_E_PENDENCIAS](MELHORIAS_E_PENDENCIAS.md)).
- [ ] Ter um ambiente de *staging* (pode ser a instância atual, que já é o `development`) para ensaiar a virada e o pentest.

---

## 3. AWS

### 3.1 Conta e rede

- [ ] MFA no usuário raiz; trabalhar com usuário/role de administração, nunca com o raiz. Sem chave de acesso de longa duração no dia a dia.
- [ ] Budget com alertas em 50/80/100% ([AWS_EC2_ARM64_DEPLOY.md](AWS_EC2_ARM64_DEPLOY.md), A.6) e alarmes de billing.
- [ ] CloudTrail ligado na conta (quem apagou ou leu o quê no S3 e no IAM).
- [ ] Security Group de produção, novo: **443 e 80 públicos**; **22 fechado** (preferir SSM Session Manager; o deploy atual abre a 22 por IP do runner e fecha depois);
      **8080 e 5432 não expostos** (o Postgres já só escuta em `127.0.0.1`).
- [ ] IP fixo (Elastic IP) e DNS apontando para ele.
- [ ] Definir o domínio, o certificado (D4) e o `APP_CORS_ALLOWED_ORIGINS` com o endereço exato do front.
- [ ] Se o front ficar em outro domínio que o da API: `APP_AUTH_REFRESH_COOKIE_SECURE=true` e `SAMESITE=None`.

### 3.2 Instância

- [ ] Tamanho: a t4g.small (2 GB) é pouco para app + Postgres + observabilidade, e mais ainda com os arquivos grandes (seção 4). Considerar t4g.medium.
- [ ] **[BLOQUEIA]** Disco: o EBS é de **8 GiB** e já estava 89,5% cheio (medido em 09/10). Produção precisa de volume maior, **criptografado**,
      de preferência com o Postgres em volume separado, e política de *snapshot* diária (Data Lifecycle Manager).
- [ ] IMDSv2 com `--http-put-response-hop-limit 2`: sem isso a aplicação, que roda dentro do Docker, não recebe credencial da Role
      e o S3 falha com mensagem que fala de credencial, não de rede ([AWS_EC2_ARM64_DEPLOY.md](AWS_EC2_ARM64_DEPLOY.md), seção Storage).
- [x] Proxy TLS na frente da 8080 com `client_max_body_size` (ou equivalente) acima de 60 MB e *timeouts* de leitura longos para upload e download.
- [x] Opções de JVM no container (`-XX:MaxRAMPercentage`), hoje ausentes no `Dockerfile` e no compose.
- [ ] Alarmes no CloudWatch: disco acima de 80%, CPU, falha de *status check* da instância.
- [ ] `docker compose` com `restart: unless-stopped` já existe; confirmar que o Docker sobe no boot (`systemctl enable docker`).

### 3.3 S3 (bucket de produção, separado do de dev)

- [ ] **[BLOQUEIA]** Bucket **novo e exclusivo de produção**, com *Block all public access*, ACLs desativadas e criptografia SSE-S3 (ou KMS).
- [ ] Política do bucket negando acesso sem TLS (`aws:SecureTransport = false`).
- [ ] **Versionamento ligado.** A lixeira `_trash` cobre a exclusão pela API, mas não um `delete` feito direto no console ou por IAM.
- [ ] Regras de ciclo de vida: abortar *multipart uploads* incompletos (7 dias) e expirar versões antigas. **Não** expirar `_trash/` nem os objetos atuais
      (decisão desta rodada: nada é apagado).
- [ ] **[BLOQUEIA]** IAM Role da instância com o mínimo, no bucket de produção. O `move` da lixeira é `CopyObject` mais `DeleteObject`, então a policy precisa de
      `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject` em `arn:aws:s3:::BUCKET/*` e `s3:ListBucket` em `arn:aws:s3:::BUCKET`
      (JSON em [AWS_EC2_ARM64_DEPLOY.md](AWS_EC2_ARM64_DEPLOY.md), A.4). Hoje a role usa `AmazonS3FullAccess`: trocar.
- [ ] Provar a permissão de dentro da instância (`aws s3 ls`, e um upload, exclusão e restauração de um arquivo de teste que passe pela lixeira).
- [ ] `/actuator/info` tem de responder `"storage":{"type":"s3"}`. O deploy já reprova se não.

### 3.4 Backup e recuperação

- [ ] **[BLOQUEIA]** Agendar `scripts/backup-db.sh` (cron na instância) e **mandar o dump para fora dela**: bucket separado (com versionamento e acesso só de escrita
      para a instância) ou R2. Hoje o dump fica no mesmo disco do banco ([BACKUP.md](BACKUP.md), "Pendente").
- [ ] **[BLOQUEIA]** Fazer um exercício de restore na instância de verdade e anotar o tempo que levou.
- [ ] Guardar `APP_FILE_ENCRYPTION_KEY` e `APP_ENCRYPTION_KEY` **fora da instância** (gerenciador de senhas ou Secrets Manager), com mais de uma pessoa sabendo onde.
      Perder a primeira é perder todos os arquivos; perder a segunda é perder as senhas do INSS.
- [ ] Antes de qualquer rotação da chave dos arquivos: o `decrypt` só aceita a versão atual da chave, então a chave antiga precisa continuar disponível
      (inclusive para o que está em `_trash`).
- [ ] Documentar a ordem de recuperação (banco, depois chaves, depois aplicação).

### 3.5 Observabilidade e operação

- [ ] Decidir onde roda a stack ([OBSERVABILIDADE.md](OBSERVABILIDADE.md)): na mesma instância (exige RAM) ou Grafana Cloud.
- [ ] Alerta mínimo: disco, 5xx, heap, e a aplicação fora do ar (`/actuator/health`).
- [ ] Rotação de logs: já limitada a 10 MB × 3 por container no compose.

---

## 4. Arquivos de 10 a 60 MB

Contexto: hoje `max-file-size` é 10 MB e `max-request-size` é 60 MB. Um arquivo acima de 10 MB volta como 400 `FILE_TOO_LARGE`.
O `EncryptedFileStorage` carrega o arquivo inteiro na memória, na ida e na volta (upload: 3 a 4 vezes o tamanho; download: 4 a 5 vezes).
Com 60 MB, um download ocupa cerca de 300 MB de heap. Uma t4g.small deixa cerca de 512 MB de heap por padrão (a JVM usa 25% da RAM).
Dois downloads simultâneos de arquivos grandes derrubam a JVM inteira, e com ela todos os escritórios.

- [ ] **[BLOQUEIA, DECIDIR]** Escolher: (A) subir os limites, definir heap e crescer a instância; ou (B) **cifra em blocos** (memória constante), com upload
      multipart no S3. Recomendo B: o cenário real já bate no teto da instância e a volumetria é desconhecida.
- [ ] Qualquer que seja o caminho: subir `spring.servlet.multipart.max-file-size` e `max-request-size` e fazer o front tratar `FILE_TOO_LARGE`.
- [ ] No caminho B: formato novo identificado pelo `MAGIC` (arquivos já gravados continuam abrindo), interface de storage trabalhando com `InputStream`, upload multipart a
      partir de cerca de 16 MB, e testes de truncamento e reordenação de blocos.
- [ ] Limitar a concorrência de upload/download pesado, ou deixar o proxy fazê-lo.
- [ ] Não manter a conexão do banco presa durante o I/O do S3: hoje `uploadDocuments`, `downloadFile` e `deleteFile` são transacionais e seguram a conexão
      durante a cifra e a transferência.
- [ ] Teste de carga com arquivos de 10, 30 e 60 MB (um e vários simultâneos) na instância de *staging*, observando heap e tempo de resposta.
- [ ] Conferir o tempo da exclusão (`copyObject` mais `deleteObject`) para o arquivo de 60 MB.

---

## 5. Dados e escritório real

- [ ] Cadastrar o escritório real no catálogo e criar os usuários reais com os scripts de `scripts/tenant/` (copiados para fora do repositório, como o próprio
      script pede), com senha forte e troca no primeiro acesso (`must_change_password` ainda não existe; ver [CADASTRO_DE_TENANT.md](CADASTRO_DE_TENANT.md)).
- [ ] Trocar a senha do `APP_ADMIN_*` e do agente de suporte (`APP_SUPPORT_*`); o seeder já recusa `changeme123` fora de dev/test.
- [ ] Decidir se o `tenant_demo` fica (em outro ambiente) ou se é inativado em produção.
- [ ] Migrar dados do escritório que já existam na instância de dev, se for o caso. Sem isso, o cadastro começa do zero.
- [ ] LGPD: base legal e aviso aos clientes, contrato/DPA da AWS, política de retenção e de resposta a pedido de exclusão (hoje não há expurgo, só lixeira).

---

## 6. Ensaio, virada e pós-virada

1. [ ] Ensaiar **tudo** acima numa instância de staging: criar do zero, subir, cadastrar o escritório, subir arquivos de 60 MB, excluir e restaurar, derrubar a instância e restaurar do backup.
2. [ ] Pentest em staging com massa sintética, conforme [PENTEST.md](PENTEST.md) (autorização por escrito antes).
3. [ ] Congelar: PR `develop` → `main`, tag, aprovação do reviewer no ambiente `production`.
4. [ ] Conferir após o deploy: `/actuator/health` UP, `/actuator/info` com o commit, o storage `s3` e o perfil `postgres`.
5. [ ] Smoke test manual com um cliente de teste: login, upload, download, excluir, restaurar.
6. [ ] Primeiro backup manual e verificação de que chegou fora da instância.
7. [ ] Comunicar o escritório, com um contato para problemas e o plano de voltar (rollback) escrito.
8. [ ] Na primeira semana: olhar disco, heap, erros 5xx e o log de arquivos órfãos (`Arquivo órfão após rollback`, `Rollback não devolveu o arquivo`).

---

## Ordem sugerida

1. Decisões D1 a D6.
2. Limpeza do repositório, compose e `.env.example`, e os guardas de perfil e de seed (seção 1.2). Tudo pequeno e independente de AWS.
3. Proteção das branches e decisão sobre o repositório público (1.1 e 1.3).
4. Arquivos grandes (seção 4), porque muda a dimensão da instância.
5. AWS de staging, ensaio completo, pentest.
6. AWS de produção, pipeline de produção, virada.

---

## Main e develop divergem de propósito

A `main` (produção) não tem o que serve a máquina de desenvolvimento. Removido na `release/main-producao`:
`db/mock-data/`, as migrations de massa (V23, V25, V28 e V29), as linhas de tenant da V2/V3, os seeders
(`AdminUserSeeder`, `SupportUserSeeder`, `SeedPasswords`), `FlywayChecksumRepair` (atalho para bancos de dev
com checksum antigo), `application-dev.yaml`, `docker-compose.override.yml`, `scripts/{dev,run,start-postgres,diagnostico-local,restart-app}.sh`
e os alvos de observabilidade da IDE. Consequências:

- **Nunca faça deploy da `main` sobre um banco criado pela `develop`**: a V2 e a V3 mudaram de checksum e o Flyway recusa a subida.
- Os números V23, V25, V28 e V29 ficam vagos na `main`; a próxima migration é a V30 nas duas.
- Ao levar a `develop` para a `main` de novo, os arquivos removidos aparecem como conflito de modificação/remoção: a resolução é manter a remoção.
- Os testes da `main` criam seus dois escritórios em `src/test/resources/db/migration/shared/V1000__tenants_de_teste.sql`, que não entra no JAR.
