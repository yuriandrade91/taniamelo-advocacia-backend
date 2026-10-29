# Cadastro de tenant — plano

**Status: Fase 1 implementada; Fases 2 a 4 propostas.** Um escritório novo ainda é cadastrado à mão.
Este documento descreve como torná-lo um procedimento único, repetível e sem falha silenciosa.

## Como é hoje (e por que dói)

Antes da Fase 1, criar um tenant exigia manter três coisas em sincronia:

1. a linha em `public.tenants` (catálogo; é ela que faz o Flyway criar e migrar o schema);
2. a lista `APP_TENANCY_SCHEMAS` (é ela que o `SchemaCurrentTenantResolver` e o `AdminUserSeeder` usam);
3. os usuários ADMIN, inseridos por SQL com hash bcrypt (o `AdminUserSeeder` criaria o
   `admin@taniamelo.adv.br` com a senha de dev no schema novo).

Esquecer o item 2 não dá erro: o `TenantResolutionFilter` aceita o `X-Tenant-Id` (o registry
lê o catálogo), mas o resolver do Hibernate não reconhece o schema e cai no padrão
(`tenant_tania`). O sintoma visto foi `401 INVALID_CREDENTIALS` no login de um usuário que
existe — na prática, a sessão foi parar no escritório errado.

Não dá para resolver só pondo o schema novo no default da lista: num banco novo o catálogo
ainda não tem a linha, e o `AdminUserSeeder` consultaria `users` de um schema inexistente.

## Fase 1 — o catálogo como única fonte de verdade (feita)

1. `ActiveTenantSchemas` lê os tenants `ativo` do catálogo por JDBC (um repositório JPA fecharia
   o ciclo resolver → `EntityManagerFactory` → resolver). O `SchemaCurrentTenantResolver` e o
   `AdminUserSeeder` passam a usá-lo.
2. `app.tenancy.schemas` / `APP_TENANCY_SCHEMAS` foram **removidos**: não há lista de reserva.
   Catálogo ilegível ou vazio significa nenhum tenant conhecido.
3. Teste: tenant só no catálogo resolve para o próprio schema, nunca para o padrão.

Resta estático o `app.tenancy.default-schema` (`tenant_tania`), usado no boot e em requisição
sem tenant; ver as decisões em aberto.

## Fase 2 — serviço de provisionamento

`TenantProvisioningService.provision(...)`, nesta ordem:

1. **Validar.** Slug e schema contra `^tenant_[a-z0-9_]{1,50}$` (o nome do schema entra em
   SQL, não pode vir livre); CNPJ e slug únicos.
2. **Registrar** no catálogo com status `provisionando`. O migrator só lê `ativo`, então nada
   acontece a meio caminho.
3. **Migrar** só o schema novo: o `MultiTenantFlywayMigrator` ganha `migrate(String schema)`,
   extraído do laço atual. O placeholder `seedDemoData` continua obrigatório.
4. **Criar os admins** com senha aleatória por usuário, codificada pelo `PasswordEncoder`. A
   senha é devolvida uma única vez; não é gravada nem logada.
5. **Ativar**: status `ativo` e `registry.reload()`.
6. **Falha** antes do passo 5: o status fica `provisionando` e a operação pode ser
   reexecutada (idempotente). Sem `DROP SCHEMA` automático — apagar dado exige decisão humana.

## Fase 3 — ponto de entrada

- `POST /api/v1/support/tenants`, com `ROLE_PLATFORM` (agente de suporte já existente).
  Corpo: `razaoSocial`, `cnpj`, `slug`, `admins[{fullName, email, username}]`.
- A criação vai para o audit log com `acting_support_user_id`.
- Como ponte entre as fases, `scripts/novo-tenant.sh` com o procedimento manual codificado
  (catálogo → subida → usuários), para não depender de SQL de memória.

## Fase 4 — testes

- Integração (Testcontainers): tenant nasce com 0 linhas nas tabelas de negócio, logins
  funcionam, slug duplicado é recusado, reexecução após falha forçada conclui o trabalho.
- Regressão: V23, V25, V28 e V29 não semeiam nada em schema fora de `tenant_demo` e
  `tenant_tania` (a guarda de `current_schema()` é o que protege escritório real).

## Decisões em aberto

1. **Troca de senha no primeiro acesso.** Não existe `must_change_password`. Sem ele, a senha
   gerada vale até alguém trocá-la. Opção: coluna nova (migration V30) + tela no front.
2. **Por onde começar:** endpoint de suporte ou o script de operação (mais simples).
   Recomendação: script primeiro, Fase 1 em paralelo.
3. **Produção.** O provisionamento cria schema com o usuário do banco da aplicação; confirmar
   que ele tem `CREATE` no banco (o Flyway já cria schemas hoje, então provavelmente tem).
4. **`default-schema`.** Segue estático (`tenant_tania`). Em requisição sem tenant ele é o
   schema atendido em silêncio, que é o modo de falha que o filtro já evita para header errado.
   Eliminar exige decidir o que o boot (validação do Hibernate) usa e se requisição sem
   tenant passa a ser 400.
5. **Dados reais do primeiro caso (Prados).** No banco local o tenant foi criado com CNPJ
   placeholder `00.000.000/0001-01` e e-mails `@prados.adv.br` inventados.

## Referência: o procedimento manual usado no tenant `prados` (dev local)

1. `INSERT INTO public.tenants (schema_name, slug, razao_social, cnpj, responsavel, status)`
   com `status = 'ativo'`.
2. Subir o app: o Flyway cria e migra o schema. O `AdminUserSeeder` percorre o catálogo e,
   com a tabela `users` vazia, cria o ADMIN padrão (`admin@taniamelo.adv.br`, senha de dev)
   no schema novo.
3. Inserir os usuários reais em `<schema>.users` por SQL (`role = 'ADMIN'`, hash bcrypt) e
   apagar esse ADMIN padrão.

Ver também [MULTI_TENANT_E_AUTH.md](MULTI_TENANT_E_AUTH.md) e
[SUPORTE_MULTITENANT.md](SUPORTE_MULTITENANT.md).
