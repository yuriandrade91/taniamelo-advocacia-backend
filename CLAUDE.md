# Karpathy Guidelines

Behavioral guidelines to reduce common LLM coding mistakes.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

# Este projeto

API do escritório (Java 25, Spring Boot 4.1, Postgres). Multi-tenant por **schema**: cada
escritório é um schema, resolvido por requisição (`X-Tenant-Id` → `TenantResolutionFilter`).
Lombok + MapStruct nos DTOs/entidades. Detalhes em `docs/` (ARQUITETURA, MULTI_TENANT_E_AUTH,
QUALIDADE_SONAR_HOOKS).

## Comandos

- Testes: `mvn clean test` — Testcontainers sobe o Postgres, então precisa do Docker no ar.
  Sempre com `clean` (ver armadilhas).
- Formatar: `mvn spotless:apply`. `mvn process-classes` roda compile + `spotless:check` +
  Checkstyle e **falha** o build em violação — é o que o pre-commit roda.
- App local: `./scripts/dev.sh` (perfil `dev`, porta 8081).
- Sonar local: `docker compose -f docker-compose.sonar.yml up -d`, depois, com a árvore limpa,
  `mvn clean verify sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token>
  -Dsonar.projectKey=taniamelo-advocacia-backend` (o `projectKey` do pom é o do SonarCloud).

## Armadilhas

- **O VS Code compila em `target/` junto com o Maven.** Sintomas: `X cannot be converted to X`,
  `class file for X not found`, `Unresolved compilation problem`, `NoClassDefFoundError` com nome
  sem pacote. Não é defeito do código: `mvn clean` resolve. Não rode `spotless:apply` no mesmo
  comando do build — ele reescreve arquivos e o VS Code recompila no meio.
- **O pre-commit compila o working tree, não o índice.** Ao fatiar mudanças em vários commits,
  verifique cada um sozinho: `git worktree add <dir> <sha>` +
  `mvn -q test-compile -Dspotless.check.skip=true` lá dentro.
- **Migration aplicada não se edita** — o checksum muda e a subida falha. Crie uma nova versão.
- `db/migration/tenant` roda em **todo** schema do catálogo, inclusive de escritório real. Massa
  de demonstração só com guarda de `current_schema()` e atrás de `app.seed.demo-data`.
- **Sonar local ≠ SonarCloud.** A Community Edition local não analisa PL/SQL nem segredos; o
  SonarCloud do CI analisa. Verde local não garante o gate do CI.
- Falsos positivos conhecidos do Sonar: S1135 casa a palavra "todo" do português; as regras
  PL/SQL assumem Oracle (onde `''` é `NULL`) — em Postgres a comparação com `''` é intencional.

## Convenções

- Commits: um tema por commit, `tipo(escopo): descrição` em PT-BR, corpo com o porquê. Arquivo que
  cruza temas é fatiado por hunk.
- DTO de resposta com id: `@JsonPropertyOrder({"id"})`. O id **do próprio cliente** se chama
  `clientId` (campo, path variable, query param); id de sub-recurso continua `id`.
- Renomear campo de DTO exige atualizar `api-tests/` (TypeScript, nomes de campo hardcoded).
- Perfis: `dev` (máquina local), `test` (todo `@SpringBootTest` usa `@ActiveProfiles("test")` via
  `PostgresIntegrationTest`), `postgres` (deploy). CORS `"*"` só sobe em `dev`/`test`.
- Credencial sempre por variável de ambiente, com default só para o ambiente local:
  `${SPRING_DATASOURCE_PASSWORD:postgres}`, nunca o valor cravado no YAML.
- Comentários e mensagens em PT-BR, explicando o porquê (o código já diz o quê).
