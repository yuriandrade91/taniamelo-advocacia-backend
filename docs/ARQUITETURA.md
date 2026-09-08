# Arquitetura do sistema — visão ponta a ponta

Documento único e canônico da arquitetura do backend. Descreve o sistema como
ele é hoje: stack, camadas, fluxo de uma requisição, segurança, persistência,
padrões transversais, build/qualidade e deploy. Para o *porquê* de decisões
específicas e para o que está planejado, ver `ARCHITECTURE.md` (racional de API)
e os ADRs em `adr/`.

---

## 1. Visão geral

Backend de um sistema de gestão para escritório de advocacia previdenciária.
Monolito modular em **Spring Boot**, API REST estateless autenticada por **JWT**,
persistência em **PostgreSQL** via **JPA/Hibernate** com schema versionado por
**Flyway**, e armazenamento de arquivos atrás de uma abstração trocável
(disco local ou **Amazon S3**).

| Aspecto | Escolha |
|---|---|
| Linguagem / runtime | Java 25 |
| Framework | Spring Boot 4.1 (Spring MVC, Data JPA, Security, Validation, Actuator) |
| Banco | PostgreSQL 17 (extensão `unaccent`) |
| Migrations | Flyway (dono único do schema; Hibernate só valida) |
| Auth | JWT (jjwt 0.12.x), stateless |
| Mapeamento DTO↔entidade | MapStruct 1.6 |
| Armazenamento de arquivos | `FileStorageService` → disco local **ou** S3 (AWS SDK v2) |
| Docs de API | springdoc-openapi (Swagger UI em `/api/docs`) |
| Build | Maven; Spotless (google-java-format AOSP) + Checkstyle + JaCoCo |
| Empacotamento | Jar executável; imagem Docker (eclipse-temurin 25) |

Estilo arquitetural: **MVC em camadas** (Controller → Service → Repository),
com um controller dedicado por sub-recurso/aba da tela do cliente. Não é
hexagonal/CQRS nem microserviços — decisão consciente para o escopo atual (um
domínio dominante: o cliente).

---

## 2. Estrutura de pacotes

Raiz: `com.lawfirm.law.firm`

```
controller/    Endpoints REST (fina camada HTTP; sem regra de negócio)
service/       Regra de negócio e transações
repository/    Spring Data JPA + Specifications (filtros dinâmicos)
model/         Entidades JPA, enums de domínio e AttributeConverters
  converter/   Conversores enum↔coluna e CryptoConverter (AES-GCM)
dto/           Contratos de entrada/saída + ClientMapper (MapStruct)
security/      JWT, filtro de autenticação, UserDetails, SecurityConfig
storage/       Abstração de arquivos (interface + impl local/S3)
exception/     Hierarquia de exceções + GlobalExceptionHandler
validation/    Bean Validation customizado (@ValidCPF)
config/        OpenAPI, CORS, conversores de enum em query, seed de admin
util/          Helpers (EnumLabelSupport, ContributionTimeParser)
audit/         Trilha de auditoria (AuditLogListener + audit_log)
```

Regra de dependência: `controller → service → repository → model`. DTOs cruzam
controller↔service; entidades nunca vazam para fora do service.

---

## 3. Ciclo de vida de uma requisição

```
HTTP → Filtro CORS (Spring Security) → JwtAuthenticationFilter → SecurityFilterChain
     → Controller (@Valid no corpo, binding de params/enum)
     → Service (@Transactional, regra de negócio, CurrentUser/tenant)
     → Repository (JPA/Specification) → PostgreSQL
     ← Entidade → ClientMapper/DTO
     ← ResponseEntity<ApiResponse<T>> (envelope padrão)
   (qualquer exceção) → GlobalExceptionHandler → ApiResponse de erro
```

1. **CORS** roda primeiro (via `http.cors(...)`), antes da autorização, para o
   preflight `OPTIONS` não ser barrado com 401 sem headers de CORS.
2. **`JwtAuthenticationFilter`** lê o `Authorization: Bearer`, valida o token,
   carrega o `UserDetails` e popula o `SecurityContext`.
3. **Controller** valida o corpo (`@Valid`), converte params (enums aceitam nome
   ou label PT-BR), delega ao service e devolve o envelope padrão.
4. **Service** aplica a regra em transação e usa `CurrentUser.id()` para
   `created_by`/`updated_by`.
5. **Erros** convergem no `GlobalExceptionHandler`.

---

## 4. Camada de API (contratos)

### Envelope padrão
Toda resposta usa `ApiResponse<T>`:
```json
{ "success": true, "data": {}, "pagination": {}, "errors": [] }
```
- `successObject(T)` / `successList(List<T>, Pagination)` para sucesso;
- `error(List<ApiError>)` para falha, com `ApiError { field, message, code }`.

### Convenções
- **Verbos:** `POST` cria; `PUT` substitui o objeto completo; `PATCH` altera
  parcialmente (situação/benefício/arrecadação, metadados de arquivo, status de
  pagamento); `DELETE` remove (soft delete onde o dado é evidência).
- **Paginação:** `pageNumber` (1-based) + `pageSize` em toda listagem; bloco
  `pagination` no envelope.
- **Filtros:** nome do parâmetro = nome do campo; datas ISO-8601; valor inválido
  responde **400 explícito**, nunca é ignorado.
- **Enums:** aceitam o nome da constante (`APOSENTADORIA_RURAL`) ou o label
  (`Aposentadoria rural`), sem diferenciar acento/caixa.

### Recursos (sob `/api/v1`)
- `auth/login` — único público, emite o JWT.
- `clients` — CRUD do cliente + `PATCH` de situação/benefício/tipo/arrecadação e
  `situation-history` (trilha gerada pelo PATCH).
- Sub-recursos por aba da UI: `personal-data`, `professional-data`,
  `addresses` (1:N, um principal), `interviews`, `files/documents`,
  `files/simulations`, `payments`.

O contrato completo é gerado automaticamente pelo springdoc e servido em
`/api/docs` (Swagger UI). Ver também `requests.http`.

---

## 5. Segurança

Autenticação **stateless por JWT** (sem sessão):

- **`JwtService`** — gera/valida o token (HS256, claim `uid`, `role`; TTL
  configurável via `app.jwt.expiration-minutes`).
- **`JwtAuthenticationFilter`** — extrai e valida o Bearer, popula o
  `SecurityContext`; token inválido/expirado deixa a requisição anônima (Spring
  responde 401/403).
- **`SecurityConfig`** — `SessionCreationPolicy.STATELESS`, CSRF desligado
  (API por header), CORS plugado cedo, rotas públicas: `auth/**`, docs e
  `actuator/health|info`; todo o resto exige autenticação.
- **`CustomUserDetailsService` / `UserPrincipal`** — carregam o usuário; senha
  com **BCrypt**.
- **`CurrentUser`** — acesso estático ao id do usuário autenticado, usado para
  auditoria (`created_by`/`updated_by`).
- **Dado sensível em repouso:** a senha do INSS do cliente é criptografada com
  **AES-256-GCM** via `CryptoConverter` (chave em `APP_ENCRYPTION_KEY`, IV
  aleatório por valor).

- **Refresh token + cookie httpOnly:** login devolve um access token (JWT) no
  corpo e um refresh token em cookie `httpOnly` (guardado hasheado em
  `refresh_tokens`, por tenant); `POST /auth/refresh` rotaciona e `POST
  /auth/logout` revoga. Ver `MULTI_TENANT_E_AUTH.md`.
- **Multi-tenancy:** o JWT carrega o claim `tenant` (schema do escritório); o
  `TenantResolutionFilter` roda antes do filtro JWT para o usuário ser carregado
  do schema certo. Ver seção 6 e `MULTI_TENANT_E_AUTH.md`.

Autorização por papel (`@PreAuthorize`) segue planejada (ver `ROADMAP.md`).

---

## 6. Persistência

- **JPA/Hibernate** com `ddl-auto: validate` — o Hibernate **nunca** altera o
  schema; só confere que o mapeamento bate com o que o Flyway criou.
- **Flyway** é o dono único do schema; as migrations (`V1__…` em diante) rodam na
  subida da aplicação. PKs em UUID (`gen_random_uuid`).
- **Filtros dinâmicos** via `ClientSpecification` (JPA Criteria): busca textual
  sem acento (`unaccent`), filtros por benefício/situação e intervalo de criação,
  combinados por `combine(...)`.
- **Soft delete** (`deleted_at`) onde o dado é evidência (arquivos, entrevistas,
  pagamentos); exclusão física em cliente/endereço (com `ON DELETE CASCADE`).
- **Enums de domínio** persistidos pelo **label PT-BR** via `AttributeConverter`
  dedicado por enum — sem CHECK em SQL nem tabela de domínio (a lista só muda com
  deploy). `EnumLabelSupport` centraliza a resolução tolerante (nome/label,
  acento/caixa-insensível).
- **Multi-tenancy por schema:** cada escritório é um schema Postgres autocontido
  (todas as tabelas). O Hibernate usa `MultiTenantConnectionProvider` (modo
  SCHEMA) ajustando o `search_path` por requisição conforme o tenant resolvido;
  o Flyway roda as migrations `db/migration/shared` no `public` e
  `db/migration/tenant` em cada schema. Detalhes em `MULTI_TENANT_E_AUTH.md` e
  `adr/ADR-0001-multi-tenant.md`.

---

## 7. Tratamento de erros

Central única (`GlobalExceptionHandler`, `@RestControllerAdvice`) em três
famílias, cada uma com política de log própria:

| Família | HTTP | Origem | Log |
|---|---|---|---|
| Validação | 400 | corpo/param inválido, enum, CPF, data | não loga (é normal) |
| Negócio | 404 / 409 / 422 | não encontrado, duplicidade, regra violada | WARN |
| Sistema | 500 | infra/bug | ERROR + stack; resposta sem detalhe técnico |

Toda resposta de erro usa o envelope `ApiResponse.error(...)`. Erros de
deserialização de enum (Jackson 3) são traduzidos para 400 com o campo correto;
violação de unicidade do Postgres vira 409 mapeada por campo.

---

## 8. Armazenamento de arquivos

Abstração `FileStorageService` (`store`/`load`/`delete`) com `storageKey` opaco
gravado no banco. Duas implementações, selecionadas por `app.storage.type`:

- **`LocalDiskFileStorageService`** (`type=local`, padrão) — disco sob
  `app.storage.local.base-path`, com proteção contra path traversal.
- **`S3FileStorageService`** (`type=s3`) — Amazon S3 (AWS SDK v2); credenciais
  via `DefaultCredentialsProvider` (IAM Role na EC2, sem chave hardcoded);
  `S3StorageConfig` só cria o `S3Client` quando `type=s3`.

Nenhuma entidade/DTO/service/controller muda ao trocar de disco para S3 — só a
propriedade de configuração. MIME types aceitos centralizados em
`AllowedMimeTypes` (documentos: PDF/PNG/JPEG; simulações: PDF).

---

## 9. Auditoria

Duas camadas complementares:

1. **`created_by`/`updated_by` + timestamps** por tabela — cobre create/update
   (soft delete é um update).
2. **`audit_log` genérico** — `AuditLogListener` (JPA `@PostPersist`/`@PostUpdate`/
   `@PostRemove`) grava automaticamente quem criou/alterou/removeu qualquer
   entidade `Auditable`, sem o service precisar lembrar. Limitação conhecida:
   remoção em cascata pelo Postgres (`ON DELETE CASCADE`) não passa pelo Hibernate,
   então filhos removidos em cascata não geram linha própria (ver `ROADMAP.md`).

Além disso, mudança de situação do cliente gera trilha própria em
`client_situation_history`.

---

## 10. Mapeamento e validação

- **MapStruct** (`ClientMapper`, `componentModel = "spring"`) faz DTO↔entidade,
  com regras explícitas (ignora `id`/auditoria no create, defaults de
  `clientType`/`notBillable`, idade derivada em UTC).
- **Bean Validation** (`jakarta.validation`) nos DTOs (`@NotBlank`, `@Email`,
  `@Positive`, `@ValidCPF` — validador de CPF com dígito verificador).
- **`ContributionTimeParser`** deriva tempo de contribuição em meses no servidor.

---

## 11. Configuração e perfis

- `application.yaml` (base) + `application-dev.yaml` / `application-postgres.yaml`.
- Tudo sensível vem de variável de ambiente com default de dev: `APP_JWT_SECRET`,
  `APP_ENCRYPTION_KEY`, `APP_ADMIN_*`, datasource, `APP_STORAGE_*`,
  `APP_CORS_ALLOWED_ORIGINS`.
- **CORS** configurável (`app.cors.allowed-origins`): default `*` em dev, domínio
  fixo em produção.
- **`AdminUserSeeder`** cria um ADMIN inicial se a tabela de usuários estiver
  vazia.
- Upload multipart limitado (10MB/arquivo, 60MB/requisição).

---

## 12. Build, qualidade e deploy

- **Java 25**: `maven-compiler-plugin` com `<release>25</release>`;
  `.mvn/jvm.config` traz os `--add-exports` que o google-java-format exige no
  JDK moderno; surefire habilita agente dinâmico (Mockito) no JDK 25.
- **Gate de qualidade:** Spotless (formatação) + Checkstyle (lint) na fase
  `process-classes` (falham o build); **JaCoCo** para cobertura; **Sonar** sob
  demanda (`mvn verify sonar:sonar`).
- **Git hooks** (`.githooks/`, via `core.hooksPath`): `pre-commit` (format +
  lint + compile), `pre-push` (build completo). Ver `QUALIDADE_SONAR_HOOKS.md`.
- **Docker:** build multi-stage (`maven:3.9-eclipse-temurin-25` →
  `eclipse-temurin:25-jre`); `docker-compose.yml` sobe Postgres e, com o profile
  `full`, a própria aplicação. Flyway roda as migrations na subida.
- **CI/CD:** GitHub Actions (build + Checkstyle + Semgrep por PR; deploy via SSH
  com aprovação manual). Ver `CI_CD.md` e `AWS_EC2_ARM64_DEPLOY.md`.

---

## 13. Evolução planejada (referências)

- **Multi-tenant** (multi-escritório): `adr/ADR-0001-multi-tenant.md`.
- **Notificações e mensageria** (RabbitMQ, scheduler, SSE):
  `NOTIFICACOES_E_MENSAGERIA.md`.
- **Refresh token + cookie httpOnly**: `adr/ADR-0002-refresh-token.md`.
- **Extração para microsserviços** (identity, financial, schedules):
  `adr/ADR-0003-extracao-microservicos.md`.
- **Backlog geral** (autorização por papel, observabilidade, Testcontainers,
  domínio previdenciário): `ROADMAP.md`.
