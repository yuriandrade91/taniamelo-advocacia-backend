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
- Identificador público do tenant = **slug ou UUID** do catálogo `public.tenants`;
  o nome do schema nunca trafega. Não há tenant em configuração, nem lista nem
  padrão; `app.tenancy` só define o nome do cabeçalho:

```yaml
app:
  tenancy:
    header-name: X-Tenant-Id
```

## 2. Como o tenant é resolvido em cada requisição

`TenantResolutionFilter` (roda antes de tudo na cadeia de segurança) define o
schema corrente na ordem:

1. Cabeçalho **`X-Tenant-Id`** (o frontend envia sempre; obrigatório no login,
   quando ainda não há JWT).
2. Claim **`tenant`** do JWT (requisições autenticadas sem o header).
3. Nada → **400 `TENANT_REQUIRED`** em rota de escritório. Não existe escritório
   padrão: com ele, um login sem header autenticava contra os usuários da Tania.
   Ficam de fora `/api/v1/support/**`, `/api/v1/tenants/resolve`, a logo pública
   `/api/v1/tenants/logo` (um `<img>` não manda cabeçalho), o que não é
   `/api/v1` (actuator, Swagger) e o preflight `OPTIONS`. Requisição com Bearer
   sem tenant resolvível segue para a autenticação responder 401 (o front usa
   esse 401 para tentar o refresh).

Sem tenant, o Hibernate aponta para `public`, onde não há tabela de escritório: um
acesso indevido falha alto em vez de ler dado de alguém. Na validação do
mapeamento no boot, usa o primeiro tenant ativo do catálogo.

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
`status='ativo'` daqui para saber quais schemas criar/migrar, e o `ActiveTenantSchemas`
usa a mesma tabela para decidir quais schemas o Hibernate atende; não há lista de
tenants em configuração. Adicionar um escritório = inserir uma linha no catálogo e
reiniciar (para o Flyway criar o schema). A entidade `Tenant`/`TenantRepository` expõe esse
catálogo; `GET /api/v1/tenants/current` devolve os dados do escritório da sessão.

> A tabela estende a sua proposta com `schema_name` (essencial para ligar o
> registro ao schema físico) e usa `TIMESTAMPTZ` nos tempos.

## 3. Migrations (automáticas na subida)

O Flyway automático do Spring está **desligado**; o `MultiTenantFlywayMigrator`
aplica, na subida:

- `db/migration/shared` → schema `public` (extensão `unaccent`).
- `db/migration/tenant` → em CADA schema ativo do catálogo `public.tenants` (cria o
  schema se não existir; histórico Flyway próprio por tenant).

Ou seja: adicionar um tenant novo = inserir a linha no catálogo e reiniciar; as
tabelas são criadas automaticamente.

## 4. Criar um escritório

O catálogo nasce vazio. Escritório e usuários entram pelos scripts de `scripts/tenant/`;
roteiro em [CADASTRO_DE_TENANT.md](CADASTRO_DE_TENANT.md).

## 5. Identificador público do tenant (nunca o nome do schema)

O nome físico do schema (`tenant_tania`) **não** é exposto. A API usa:

- **`tenantId`** — UUID canônico e opaco (`tenants.id`).
- **`slug`** — identificador amigável para subdomínio/URL (ex.: `meu-escritorio`).

O cabeçalho **`X-Tenant-Id` aceita o UUID OU o slug**; internamente o
`TenantRegistry` traduz para o schema. O front descobre o `tenantId` a partir do
slug (subdomínio) via endpoint público, antes do login:

```bash
curl 'https://api.seudominio.adv.br/api/v1/tenants/resolve?slug=meu-escritorio'
# → { "data": { "tenantId": "<uuid>", "slug": "meu-escritorio", "razaoSocial": "..." } }
```

## 6. Login por email OU username (o header decide o escritório)

O campo é `login` e aceita **e-mail ou username**. O usuário é o que o
`02-criar-usuarios.sql` criou para aquele escritório.

```bash
curl -X POST https://api.seudominio.adv.br/api/v1/auth/login \
  -H 'Content-Type: application/json' -H 'X-Tenant-Id: meu-escritorio' \
  -d '{"login":"usuario@meu-escritorio.adv.br","password":"<senha>"}' -i
```

A resposta traz `tenantId` (UUID) e `tenantSlug`. Nas chamadas seguintes envie o
`Authorization: Bearer <access>` (o tenant vai no claim como UUID) e, por
robustez, também o `X-Tenant-Id`. No Swagger, clique em **Authorize** para preencher o `X-Tenant-Id`.

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
