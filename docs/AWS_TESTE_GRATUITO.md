# Plano de ação: ambiente de teste na AWS (grátis, dados mockados)

Objetivo: colocar a aplicação no ar na AWS usando só o que é gratuito (tier
clássico de 12 meses ou o crédito de US$200/6 meses, dependendo da idade da
conta), populada com `db/mock-data/seed_mock.sql` + `db/mock-data/seed_mock_bulk_100.sql`
- **sem dado real de cliente** - pra testar tudo ponta a ponta antes de
decidir migrar pra uma conta paga de verdade. Complementa `docs/INFRA_PLAN.md`
(que cobre o cenário de produção com dado real).

## Vale a pena fazer essa PoC agora? Sim.

Três motivos concretos, dado o estado atual do projeto:

1. **O relógio já está correndo.** A conta é nova (pós-15/07/2025), então já
   está no modelo de crédito de US$200/6 meses - esse crédito não é
   recuperável depois. Cada semana sem testar é uma semana a menos de janela
   pra descobrir problema de infra com folga, sem pressão de prazo acabando.
2. **Backend tinha lacunas que só apareciam rodando fora do notebook.** Antes
   só existia `LocalDiskFileStorageService` (sem armazenamento de objetos),
   CORS totalmente aberto (`*`, sem jeito de restringir por config) e nenhuma
   massa de dado em volume - nenhum desses três era visível testando local
   com 5 clientes. Os três já foram resolvidos: `ObjectStorageFileStorageService`
   (Fase 3 abaixo), CORS parametrizável via `APP_CORS_ALLOWED_ORIGINS`
   (`CorsConfig.java`) e a massa de teste maior (`db/mock-data/seed_mock_bulk_100.sql`).
3. **O frontend já existe** (em outro repositório) - ou seja, dá pra validar
   a integração ponta a ponta de verdade (não só Postman), que é o teste que
   mais importa antes de decidir gastar dinheiro de verdade em produção.

**O que precisa acontecer antes de começar** (bloqueadores reais, não
burocracia): restringir CORS pro domínio real do frontend assim que ele
estiver acessível publicamente (ou manter `*` só enquanto for teste interno,
nunca em produção com dado real) via `APP_CORS_ALLOWED_ORIGINS`.

**Antes de tudo, uma verificação que muda o resto do plano:** entre no
Console AWS → **Billing → Free Tier** e confira se a conta foi criada antes
ou depois de 15/07/2025.

- **Antes dessa data:** vocês têm o tier clássico de 12 meses - EC2
  t2/t3.micro (750h/mês), RDS t2/t3.micro (750h/mês) e S3 (5GB) genuinamente
  grátis, sem consumir crédito nenhum.
- **Depois dessa data (conta nova):** vale o crédito único de US$200,
  válido por 6 meses ou até acabar o crédito - o que vier primeiro. O plano
  abaixo funciona nos dois casos, só muda **quanto tempo** dura de graça.

---

## Fase 0 - Segurança e proteção contra cobrança surpresa (fazer antes de criar qualquer recurso)

1. Ativar **MFA na conta root** da AWS.
2. Criar um **usuário IAM** separado do root pra uso do dia a dia (root só
   pra emergência) - com permissões de EC2/Lightsail, RDS, S3 e Budgets.
3. Criar um **AWS Budget** (Billing → Budgets) com alerta em, por exemplo,
   US$5 - avisa por e-mail assim que o gasto passar disso, o que pega tanto
   o fim do tier grátis quanto qualquer recurso esquecido ligado.
4. Anotar a data-limite (12 meses da criação da conta, ou 6 meses do
   crédito) num lugar visível - esse plano é pra testar, não pra esquecer
   rodando.

## Fase 1 - Provisionar o servidor

**Se conta antiga (tier clássico):** EC2 t3.micro (1GB RAM) - 750h/mês
grátis cobre o mês inteiro com uma instância só ligada.

**Se conta nova (crédito):** Lightsail 1-2GB (US$7-12/mês) rodando dentro do
crédito de US$200, ou EC2 t3.micro (também entra no crédito). Lightsail é
mais simples de configurar (menos peça de rede pra mexer manualmente).

Passo a passo (EC2, que serve pra qualquer um dos dois casos):

1. Console AWS → EC2 → **Launch Instance**.
2. AMI: Ubuntu Server 22.04 LTS (ou Amazon Linux 2023).
3. Tipo: `t3.micro` (ou `t2.micro` se aparecer como elegível ao free tier na
   sua região).
4. Criar/usar um **par de chaves SSH** (baixar o `.pem`, guardar com
   permissão restrita).
5. **Security Group:** liberar só a porta 22 (SSH, restringir ao seu IP se
   possível), 80 e 443 (HTTP/HTTPS). **Nunca abrir a porta 5432 (Postgres)
   pra internet** - o banco só deve ser acessível de dentro da própria
   instância.
6. Storage: 20-30GB de EBS gp3 (dentro do free tier de 30GB, se aplicável).

## Fase 2 - Banco de dados

Duas opções, dependendo do que sobrar de free tier:

- **RDS db.t3.micro** (só se conta antiga - 750h/mês grátis): banco
  totalmente gerenciado pela AWS, sem vocês administrarem.
- **Postgres self-hosted na mesma instância** (via Docker, do jeito que o
  `docker-compose.yml` do projeto já está montado): não consome nenhuma cota
  de RDS, funciona igual em conta nova ou antiga.

Recomendo o **self-hosted** pro ambiente de teste - é o que já está pronto no
projeto, sem depender de qual tier a conta tem.

1. Instalar Docker + Docker Compose na instância EC2.
2. Copiar o repositório (ou só o `docker-compose.yml`, `Dockerfile` e
   `.env` de exemplo) pra instância via `git clone` ou `scp`.
3. Preencher as variáveis de ambiente (`.env`): `APP_JWT_SECRET`,
   `APP_ENCRYPTION_KEY`, credenciais do Postgres - gerar valores novos, não
   reusar os de desenvolvimento local.
4. `docker compose up -d` - o Flyway roda as migrations sozinho na subida da
   aplicação (já é o comportamento padrão do projeto).

## Fase 3 - Arquivos (armazenamento de objetos)

1. Criar um bucket S3 (ex.: `taniamelo-teste-mock`) - região próxima (ex.:
   `sa-east-1`, São Paulo).
2. Criar uma policy IAM restrita só a esse bucket (leitura/escrita) e um
   usuário/role de aplicação com essa policy - nunca usar credenciais root
   pra isso.
3. `ObjectStorageFileStorageService` já está implementado (segunda
   implementação de `FileStorageService`, ao lado de
   `LocalDiskFileStorageService`) - só trocar `app.storage.type=local` por
   `s3` (via `APP_STORAGE_TYPE`) e preencher `APP_STORAGE_S3_BUCKET`/
   `APP_STORAGE_S3_REGION`. Ver passo a passo detalhado em
   `docs/AWS_DEPLOY_PASSO_A_PASSO.md`.
4. 5GB grátis por 12 meses se a conta for antiga; se for conta nova, o custo
   é centavos e sai do crédito de US$200 (como já calculamos, ~US$0,02-0,05
   pro volume de teste).

## Fase 4 - Popular com dado mockado e validar

1. Rodar, nessa ordem, `db/mock-data/seed_mock.sql` (baseline: 2 usuários + 5
   clientes) e depois `db/mock-data/seed_mock_bulk_100.sql` (120 clientes
   adicionais, com endereço/histórico/entrevista/arquivo/pagamento variados)
   contra o Postgres da instância:
   ```bash
   psql "$DATABASE_URL" -f db/mock-data/seed_mock.sql -f db/mock-data/seed_mock_bulk_100.sql
   ```
   Total após rodar os dois: 125 clientes - cobre o "pelo menos 100" pedido
   com folga pra testar paginação, filtro e volume de arquivo/pagamento.
2. Testar login com as credenciais de teste (`dra.tania@taniamelo.adv.br` /
   `ana.souza@taniamelo.adv.br`, senha `password`).
3. Rodar a suíte de requests do Postman (já montada em rodadas anteriores)
   contra o endereço público da instância, ponta a ponta: criar cliente,
   endereços, entrevistas, arquivos, pagamentos, PATCH de situação/benefício,
   consulta de `audit_log`.
4. Conferir o Swagger (`/api/docs`) acessível publicamente (é só o contrato
   da API, sem dado exposto - "Try it out" continua exigindo token).

## Fase 5 - Decisão de saída (antes do prazo acabar)

Antes dos 12 meses (conta antiga) ou 6 meses (crédito, conta nova):

- **Migrar pra pago:** seguir o `docs/INFRA_PLAN.md` (rota AWS já
  precificada em ~US$13-14/mês) trocando o dado mockado pelo dado real de
  cliente, com backup pro R2 já configurado desde o início dessa vez.
- **Ou desligar tudo** se decidirem por outro provedor - terminar a
  instância EC2, apagar o bucket S3, remover o Budget.

---

## Resumo das fases

| Fase | O que entrega | Depende de |
|---|---|---|
| 0 | Conta protegida contra cobrança surpresa | - |
| 1 | Servidor no ar | Fase 0 |
| 2 | Banco populável | Fase 1 |
| 3 | Upload/download de arquivo funcionando | Fase 1 |
| 4 | Sistema testável ponta a ponta com dado mockado | Fases 2 e 3 |
| 5 | Decisão consciente antes do grátis acabar | Fase 4 |

Referências: `docs/INFRA_PLAN.md` (plano de produção/observabilidade),
`docs/custos_hospedagem.xlsx` (planilha de custos comparados).
