# ADR-0001 — Multi-tenancy (multi-escritório)

- **Status:** IMPLEMENTADO como **schema-per-tenant** (o time optou por isolamento
  por schema em vez do discriminador por coluna descrito abaixo). Guia operacional
  e detalhes da implementação em `../MULTI_TENANT_E_AUTH.md`. O texto abaixo é
  mantido como registro das alternativas avaliadas.
- **Contexto alinhado em:** `docs/ARCHITECTURE.md` §2 ("Multi-escritório")
- **Decisão resumida:** multi-tenancy **por discriminador** (`office_id` em cada
  linha), **um único banco**, isolamento aplicado no servidor via Hibernate —
  nunca confiando no frontend. O escritório atual vira o **tenant padrão**, então
  nada do que roda hoje quebra.

## 1. Decisão

Três estratégias possíveis para SaaS:

| Estratégia | Isolamento | Custo/ops | Quando usar |
|---|---|---|---|
| **Discriminador (`office_id` por linha)** ✅ | Lógico (por linha) | Menor (1 banco, 1 schema) | SaaS de pequeno/médio porte, muitos tenants pequenos |
| Schema por tenant | Médio | Médio (migrations × schemas) | Isolamento intermediário, dezenas de tenants |
| Banco por tenant | Máximo (físico) | Alto | Exigência contratual/regulatória de isolamento |

**Escolhido: discriminador por `office_id`.** É o padrão de mercado para este
porte e é exatamente o que `ARCHITECTURE.md` §2 já registrou: um banco só,
backup/migração simples, e as costuras (`security`/`users`, `storage`) já
preparadas para virar serviço multi-tenant no futuro, se necessário. Schema/banco
por tenant só se um cliente exigir isolamento físico — decisão reversível, pois o
`office_id` continua existindo em qualquer um dos modelos.

## 2. Mecanismo: Hibernate `@TenantId` (discriminator), não `@Filter`

O `ARCHITECTURE.md` cita "`@Filter` ou Specification". Recomendo a evolução para
**`@TenantId` do Hibernate 6** (discriminator-based multitenancy nativo) porque:

- É aplicado **automaticamente em toda leitura E escrita** — o `INSERT` grava o
  tenant corrente e todo `SELECT` já vem filtrado, sem depender de lembrar de
  ativar um filtro por sessão (fonte clássica de vazamento entre tenants).
- Não exige reescrever cada query/Specification (o `ClientSpecification` e todos
  os `findBy...` continuam iguais; o filtro de tenant é transversal).

Peças:

1. **`CurrentTenantIdentifierResolver<UUID>`** (bean) — devolve o tenant corrente
   a partir de um `TenantContext` (ThreadLocal). Sem tenant no contexto (job de
   sistema, seed, boot), devolve o **DEFAULT_OFFICE_ID** — é isso que mantém o
   escritório de hoje funcionando como tenant único.
2. **`TenantContext`** — ThreadLocal com o `office_id` da requisição.
3. **`TenantResolverFilter`** — lê o `office_id` do claim do JWT e popula o
   `TenantContext` no início da requisição; limpa no fim (evita vazar entre
   threads reusadas do pool).
4. **JWT** — `JwtService.generateToken` passa a incluir o claim `office` (hoje
   sempre o DEFAULT_OFFICE_ID); `JwtAuthenticationFilter`/`TenantResolverFilter`
   lê e valida.
5. **`@TenantId private UUID officeId;`** em cada entidade tenant-scoped.

## 3. Modelo de dados

- Nova tabela **`offices`** (`id`, `name`, `slug`, `plan`, `active`, timestamps).
- `office_id UUID NOT NULL` em: `users`, `clients`, `client_addresses`,
  `client_interviews`, `client_files`, `client_payments`,
  `client_situation_history`, `audit_log`, e futuras `notifications`/`outbox`.
- FK `office_id → offices(id)`. Índice em `office_id` (e composto onde o filtro
  por tenant + coluna quente ajuda, ex.: `(office_id, updated_at)` em `clients`).
- **Backfill não-destrutivo:** cria um office padrão fixo
  (`00000000-0000-0000-0000-000000000001` = "Tania Melo Advocacia") e seta
  `office_id` de todas as linhas existentes para ele antes de aplicar o
  `NOT NULL`. Nenhum dado atual é perdido nem precisa ser recriado.

## 4. Migration (esboço — `V10__multi_tenant.sql`)

```sql
CREATE TABLE offices (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(150) NOT NULL,
    slug       VARCHAR(60)  NOT NULL UNIQUE,
    plan       VARCHAR(30)  NOT NULL DEFAULT 'standard',
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

-- Tenant padrão = escritório atual. UUID fixo, referenciado no código (DEFAULT_OFFICE_ID).
INSERT INTO offices (id, name, slug)
VALUES ('00000000-0000-0000-0000-000000000001', 'Tania Melo Advocacia', 'tania-melo');

-- Para CADA tabela tenant-scoped (exemplo com clients; repetir nas demais):
ALTER TABLE clients ADD COLUMN office_id UUID
    REFERENCES offices(id);
UPDATE clients SET office_id = '00000000-0000-0000-0000-000000000001'
    WHERE office_id IS NULL;
ALTER TABLE clients ALTER COLUMN office_id SET NOT NULL;
CREATE INDEX idx_clients_office ON clients (office_id);
-- (idem para users, client_addresses, client_interviews, client_files,
--  client_payments, client_situation_history, audit_log)
```

## 5. Impacto e riscos

- **Não quebra o app atual:** resolver devolve DEFAULT_OFFICE_ID quando não há
  JWT com `office`; todas as linhas antigas já pertencem a esse office. Login e
  todos os fluxos continuam idênticos com um só tenant.
- **Storage:** particionar por tenant no prefixo (`offices/{officeId}/clients/...`)
  — muda só o `directory` passado ao `FileStorageService`, alinhado ao doc.
- **Unicidade:** os índices únicos hoje globais (`cpf`, `nit_pis`,
  `benefit_number`) devem passar a ser **únicos por tenant** (`UNIQUE (office_id,
  cpf)`), senão dois escritórios não poderiam ter o mesmo CPF — que é legítimo.
  **Ponto de atenção obrigatório na Fase 1.**
- **Testes:** ao ativar `@TenantId`, os `@SpringBootTest` precisam de um tenant no
  contexto (ou o default) — cobrir no setup.

## 6. Plano de execução (faseado)

**Fase 1 — Fundação (sem exigir 2º tenant):**
1. `V10__multi_tenant.sql` (tabela + colunas + backfill + índices, unicidade por
   tenant).
2. `offices` entidade/repo; constante `DEFAULT_OFFICE_ID`.
3. `TenantContext`, `CurrentTenantIdentifierResolver`, `TenantResolverFilter`.
4. Claim `office` no JWT (sempre DEFAULT por ora).
5. `@TenantId officeId` nas entidades tenant-scoped.
6. Ajustar unicidade e storage por tenant.
7. Rodar `mvn verify` (com Postgres) — validar que o app sobe e o CRUD funciona
   com o tenant default.

**Fase 2 — Onboarding de novos escritórios:**
- CRUD de `offices` (só ADMIN da plataforma).
- Cadastro de usuário vinculado a um office; login emite JWT com o `office` certo.
- Seed/rotina para criar o primeiro ADMIN de cada office.

**Fase 3 — Autorização por papel dentro do tenant** (já previsto no Roadmap Fase A):
`@PreAuthorize` diferenciando ADMIN/LAWYER/STAFF, agora com escopo de office.

## 7. Alternativas descartadas

- **Schema/banco por tenant:** isolamento maior, mas custo/ops desproporcional ao
  porte atual; reversível no futuro sem perder o `office_id`.
- **Filtro só via Specification:** exigiria tocar em toda query e depende de
  disciplina do dev (risco de vazamento); `@TenantId` é transversal e mais seguro.
