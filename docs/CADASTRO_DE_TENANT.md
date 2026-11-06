# Cadastro de tenant (escritório)

**Status:** o catálogo é a fonte única (feito) e o cadastro é por scripts SQL, sem passo manual escondido.
Um endpoint de provisionamento ainda é proposta (abaixo).

O banco **sobe vazio**: nenhuma migration ou rotina cria escritório, usuário ou cliente. Cada escritório
é um schema Postgres; quem decide quais schemas existem é a tabela `public.tenants`, e o
`MultiTenantFlywayMigrator` cria e migra cada schema `ativo` a cada boot. Não há lista em configuração
(`APP_TENANCY_SCHEMAS` não existe) nem escritório padrão: rota de escritório sem `X-Tenant-Id` nem JWT
responde 400 `TENANT_REQUIRED`.

## Procedimento

Os scripts ficam em `scripts/tenant/`. **Não coloque dado real neles dentro do repositório**: copie para
fora (`cp scripts/tenant/01-cadastrar-tenant.sql ~/01-meu-escritorio.sql`), edite a cópia e rode a cópia.

1. **Cadastrar no catálogo** — `01-cadastrar-tenant.sql`. Grava uma linha em `public.tenants` com slug,
   schema (`tenant_` + algo), razão social e CNPJ (únicos). Atômico.
2. **Reiniciar a aplicação.** O Flyway cria o schema e as tabelas. Confira no log
   `Flyway: migrations de tenant aplicadas (schema ...)`. Criar o schema à mão deixaria o escritório sem
   `flyway_schema_history` e quebraria a próxima migration.
3. **Criar os usuários** — `02-criar-usuarios.sql`. Senha temporária aleatória por usuário, impressa uma
   única vez; só o hash bcrypt é gravado. Exige ao menos um ADMIN.
4. **Agente de suporte da plataforma** (uma vez, independe do escritório) —
   `04-criar-agente-suporte.sql`. Ver [SUPORTE_MULTITENANT.md](SUPORTE_MULTITENANT.md).
5. **Senha perdida** — `03-redefinir-senha.sql`.

Rodando o banco em contêiner (ver `AWS_EC2_ARM64_DEPLOY.md`, B.4):

```bash
docker exec -i database-postgress psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 < ~/01-meu-escritorio.sql
docker compose --profile full restart app
docker exec -i database-postgress psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 < ~/02-usuarios.sql
```

## Proposta: serviço de provisionamento

Para o procedimento deixar de ser SQL à mão:

1. `TenantProvisioningService.provision(...)`: valida slug e schema contra `^tenant_[a-z0-9_]{1,50}$`
   (o nome do schema entra em SQL), registra com status `provisionando` (o migrator só lê `ativo`),
   migra só o schema novo, cria os admins com senha aleatória (devolvida uma única vez), e então ativa e
   recarrega o registry. Falha antes de ativar deixa `provisionando` e a operação é reexecutável; nunca
   há `DROP SCHEMA` automático.
2. `POST /api/v1/support/tenants`, com `ROLE_PLATFORM`, registrada no audit log com o agente que a fez.
3. Testes de integração: tenant nasce com 0 linhas nas tabelas de negócio, logins funcionam, slug
   duplicado é recusado, reexecução após falha forçada conclui o trabalho.

## Decisões em aberto

1. **Troca de senha no primeiro acesso.** Não existe `must_change_password`; a senha gerada vale até
   alguém trocá-la (`PATCH /api/v1/users/me/password`). Opção: coluna nova e tela no front.
2. **Permissão do usuário do banco.** O Flyway cria o schema com o usuário da aplicação; confirmar que ele
   tem `CREATE` no banco.

Ver também [MULTI_TENANT_E_AUTH.md](MULTI_TENANT_E_AUTH.md).
