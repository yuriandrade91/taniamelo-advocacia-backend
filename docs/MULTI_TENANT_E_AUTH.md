# Multi-tenancy por schema + Refresh token — guia operacional

Como a multi-tenancy por schema e o refresh token funcionam neste projeto, e
como rodar/testar com **dois tenants**. Decisão de estratégia: `adr/ADR-0001`
(atualizado para schema-per-tenant).

## 1. Modelo: um schema Postgres autocontido por tenant

- Cada escritório = um **schema** (ex.: `tenant_tania`, `tenant_demo`) com o
  conjunto COMPLETO de tabelas (users, clients, client_*, audit_log,
  refresh_tokens). Isolamento forte: nenhuma linha de um tenant é visível no
  outro.
- A extensão `unaccent` fica no schema **public** (compartilhado); as queries de
  cada tenant a alcançam via `search_path = "<tenant>", public`.
- Identificador do tenant = **nome do schema**. Configurado em `app.tenancy`:

```yaml
app:
  tenancy:
    default-schema: tenant_tania
    schemas: tenant_tania,tenant_demo
    header-name: X-Tenant-Id
```

## 2. Como o tenant é resolvido em cada requisição

`TenantResolutionFilter` (roda antes de tudo na cadeia de segurança) define o
schema corrente na ordem:

1. Cabeçalho **`X-Tenant-Id`** (o frontend envia sempre; obrigatório no login,
   quando ainda não há JWT).
2. Claim **`tenant`** do JWT (requisições autenticadas sem o header).
3. Nada → cai no `default-schema`.

O `SchemaMultiTenantConnectionProvider` então ajusta o `search_path` da conexão
para o schema do tenant; o Hibernate passa a ler/gravar só nele. Nada de
`tenant_id` em query — o isolamento é no nível do schema.

## 2.1. Catálogo de tenants (`public.tenants`)

O registro de QUAIS escritórios existem e seus metadados de negócio vive numa
tabela de **control-plane** no schema `public` (nunca dentro de um tenant):

| coluna | uso |
|---|---|
| `schema_name` | liga o registro ao schema físico (ex.: `tenant_demo`) |
| `razao_social`, `cnpj`, `responsavel`, `email`, `telefone` | dados cadastrais do escritório |
| `plano`, `status` | plano contratado e se está `ativo` |
| `criado_em`, `atualizado_em` | auditoria |

É a **fonte de verdade**: o `MultiTenantFlywayMigrator` lê os `schema_name` com
`status='ativo'` daqui para saber quais schemas criar/migrar (com fallback para
`app.tenancy.schemas`). Adicionar um escritório = inserir uma linha (via migration
compartilhada) e reiniciar. A entidade `Tenant`/`TenantRepository` expõe esse
catálogo; `GET /api/v1/tenants/current` devolve os dados do escritório da sessão.

> A tabela estende a sua proposta com `schema_name` (essencial para ligar o
> registro ao schema físico) e usa `TIMESTAMPTZ` nos tempos.

## 3. Migrations (automáticas na subida)

O Flyway automático do Spring está **desligado**; o `MultiTenantFlywayMigrator`
aplica, na subida:

- `db/migration/shared` → schema `public` (extensão `unaccent`).
- `db/migration/tenant` → em CADA schema de `app.tenancy.schemas` (cria o schema
  se não existir; histórico Flyway próprio por tenant).

Ou seja: adicionar um tenant novo = incluir o schema na lista e reiniciar; as
tabelas são criadas automaticamente.

## 4. Rodar localmente com 2 tenants

```bash
# 1. Suba o Postgres e a aplicação (as migrations criam tenant_tania e tenant_demo).
./scripts/start-postgres.sh
mvn spring-boot:run           # cria os schemas + tabelas + um ADMIN em cada

# 2. Popule as massas (cada uma no seu schema):
psql "$DATABASE_URL" -f db/mock-data/seed_tenant_tania.sql   # 125 clientes
psql "$DATABASE_URL" -f db/mock-data/seed_tenant_demo.sql    # 6 clientes distintos
```

## 5. Identificador público do tenant (nunca o nome do schema)

O nome físico do schema (`tenant_tania`) **não** é exposto. A API usa:

- **`tenantId`** — UUID canônico e opaco (`tenants.id`).
- **`slug`** — identificador amigável para subdomínio/URL (ex.: `tania`, `demo`).

O cabeçalho **`X-Tenant-Id` aceita o UUID OU o slug**; internamente o
`TenantRegistry` traduz para o schema. O front descobre o `tenantId` a partir do
slug (subdomínio) via endpoint público, antes do login:

```bash
curl 'http://localhost:8080/api/v1/tenants/resolve?slug=tania'
# → { "data": { "tenantId": "<uuid>", "slug": "tania", "razaoSocial": "..." } }
```

## 6. Login por email OU username (o header decide o escritório)

O campo é `login` e aceita **e-mail ou username**. Credenciais reais das massas
(senha de todos: `password`):

| Tenant (slug) | Usuário (email / username) | Papel |
|---|---|---|
| `tania` | `dra.tania@taniamelo.adv.br` / `dra.tania` | ADMIN |
| `tania` | `ana.souza@taniamelo.adv.br` / `ana.souza` | STAFF |
| `demo`  | `dr.almeida@escritoriodemo.adv.br` / `dr.almeida` | ADMIN |
| `demo`  | `bruna.lima@escritoriodemo.adv.br` / `bruna.lima` | STAFF |

```bash
# Tenant tania (por e-mail):
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -H 'X-Tenant-Id: tania' \
  -d '{"login":"dra.tania@taniamelo.adv.br","password":"password"}' -i

# Tenant demo (por username):
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -H 'X-Tenant-Id: demo' \
  -d '{"login":"dr.almeida","password":"password"}' -i
```

A resposta traz `tenantId` (UUID) e `tenantSlug`. Nas chamadas seguintes envie o
`Authorization: Bearer <access>` (o tenant vai no claim como UUID) e, por
robustez, também o `X-Tenant-Id`. No Swagger, o login já tem 4 exemplos prontos
(email/username × tania/demo) — clique em **Authorize** para preencher o
`X-Tenant-Id`.

## 6. Refresh token + cookie httpOnly

- **Login** devolve o *access token* (JWT) no corpo e um *refresh token* em
  cookie `httpOnly` (`Set-Cookie: refreshToken=...; Path=/api/v1/auth`).
- **`POST /api/v1/auth/refresh`** (enviar o cookie + `X-Tenant-Id`): valida o
  refresh, **rotaciona** (o anterior é invalidado) e devolve um novo access.
  Reuso de um refresh já usado → revoga toda a família (defesa contra roubo).
- **`POST /api/v1/auth/logout`**: revoga o refresh e limpa o cookie.
- Guardado só **hasheado** (SHA-256) na tabela `refresh_tokens` de cada tenant.

Cookie por ambiente (`app.auth.refresh-cookie`): dev local (http, mesma origem)
usa `secure=false`, `same-site=Lax`; produção com front em outro domínio deve
usar `secure=true`, `same-site=None`.

## 7. Ponto de atenção (Spring Boot 4)

Dois imports de infraestrutura podem ter mudado de pacote entre o Boot 3 e o 4;
se o `mvn compile` reclamar, ajuste o pacote (a classe é a mesma):

- `EntityManagerFactoryDependsOnPostProcessor` (usado em `MultiTenantJpaConfig`).
- `HibernatePropertiesCustomizer` (idem).

O resto (Hibernate `MultiTenantConnectionProvider`/`CurrentTenantIdentifierResolver`,
Flyway programático) é estável entre as versões.
