# Auditoria de Arquitetura e Endpoints — law-firm (Tania Melo Advocacia)

**Data:** 2026-07-21 · **Escopo:** revisão completa da arquitetura, contrato de todos os endpoints (rotas, parâmetros, envelopes) e boas práticas de um projeto Java/Spring Boot de alto padrão. **Entrega:** relatório + correção crítica de consistência aplicada.

---

## 1. Veredito

A base é de **padrão alto e internamente consistente**. Camadas bem separadas (controller → service → repository → model), envelope de resposta único, central única de erros, segurança stateless via JWT, mapeamento por MapStruct, validação declarativa e migrations versionadas com Flyway. Não foram encontrados **endpoints quebrados nem falhas de segurança acidentais**. Os pontos abaixo são, em ordem: uma correção de consistência (aplicada), lacunas de infraestrutura de teste, e itens de endurecimento deliberadamente adiados para produção (já documentados no `ROADMAP.md`/`SecurityConfig`).

> **Nota sobre execução do build:** o ambiente de análise não tem JDK 25 nem Maven e o proxy bloqueia o Maven Central, então `mvn compile`/`mvn test` **não puderam ser executados aqui**. A verificação foi estática (contratos, tipos, fluxos). Os comandos para você validar localmente estão na seção 7.

---

## 2. Arquitetura em camadas

| Camada | Responsabilidade | Observação |
|---|---|---|
| `controller` | HTTP, binding de parâmetros, envelope de resposta | Fina, sem regra de negócio. Consistente. |
| `service` | Regra de negócio, transações (`@Transactional`) | `ClientService` tem interface + `ClientServiceImpl`; os demais services são classes concretas. |
| `repository` | Spring Data JPA + `ClientSpecification` (filtros dinâmicos) | Uso correto de derived queries e Specification. |
| `model` | Entidades JPA + enums de domínio + converters | Enums persistidos por label via `AttributeConverter`. |
| `dto` | Contratos de entrada/saída + `ClientMapper` (MapStruct) | Separação DTO×entidade respeitada. |
| `security` | Filtro JWT, `JwtService`, `CurrentUser`, `SecurityConfig` | Stateless, sem sessão. |
| `exception` | `GlobalExceptionHandler` + hierarquia (Validation/Business/System) | Excelente: 3 famílias de erro com política de log distinta. |
| `storage` | `FileStorageService` (abstração) + `LocalDiskFileStorageService` | Troca por S3 sem tocar service/DTO. |
| `config` | OpenAPI, CORS, conversores de enum, seed de admin | — |

**Pontos fortes de destaque:** envelope `{success, data, pagination?, errors?}` uniforme; paginação 1-based padronizada em todos os listagens; `EnumLabelSupport` centraliza a resolução "nome da constante OU label PT-BR, sem acento/caixa"; erros de deserialização de enum (Jackson 3 / `tools.jackson`) são traduzidos para 400 com o campo correto; OpenAPI anexa automaticamente as respostas de erro padrão a todas as operações.

---

## 3. Inventário de endpoints (contrato verificado)

Todos sob `/api/v1`. Todos exigem `Authorization: Bearer <token>`, exceto `POST /auth/login` e a documentação.

**Autenticação** — `POST /auth/login` (body `LoginRequestDTO`, `@Valid`).

**Cliente(s)** — `POST /clients` · `GET /clients` (params: `pageNumber`, `pageSize`, `searchTerm`, `benefitType[]`, `situation[]`, `createdFrom`, `createdTo`) · `GET /clients/{id}` · `GET /clients/{id}/situation-history` · `PUT /clients/{id}` · `PATCH /clients/{id}` · `DELETE /clients/{id}`.

**Dados pessoais** — `GET|PUT /clients/{id}/personal-data`.
**Dados profissionais** — `GET|PUT /clients/{id}/professional-data`.
**Endereços** — `POST` · `GET` (paginado) · `GET|PUT|DELETE /{addressId}`.
**Entrevistas** — `POST` · `GET` (paginado) · `GET|PUT|DELETE /{interviewId}`.
**Financeiro (parcelas)** — `POST` · `GET` (paginado) · `GET|PATCH|DELETE /{paymentId}`.
**Arquivos/Documentos** — `POST /files/documents` (multipart) · `GET` · `GET|PATCH|DELETE /{fileId}` · `GET /{fileId}/download`.
**Arquivos/Simulações** — `POST /files/simulations` (multipart) · `GET` · `GET|PATCH|DELETE /{fileId}` · `GET /{fileId}/download` · `PATCH /{fileId}/principal`.

**Verificações de parâmetros que passaram:**
- Datas de filtro (`createdFrom/To`) aceitam `yyyy-MM-dd` ou timestamp ISO; valor inválido gera **400 explícito** (nunca ignorado). `createdTo` é inclusivo (fim do dia). ✔
- `benefitType`/`situation` em query string convertem por `LabelAwareEnumConverter` (nome **ou** label). ✔
- Uploads em lote validam `files.size() == metadata.size()` e o MIME de cada arquivo antes de persistir. ✔
- `PATCH` de cliente e de parcela usam body **sem `@Valid`** propositalmente (campos opcionais), com parsing/validação manual dos enums. ✔
- Unicidade (CPF, NIT/PIS, nº benefício) checada na criação; no update recai na constraint do banco → **409** mapeado por campo. ✔

---

## 4. Correção aplicada (consistência)

**Cálculo de idade divergente entre camadas.** `ClientServiceImpl.ageOf(...)` usava `ZoneOffset.UTC`, enquanto `ClientMapper.computeAge(...)` usava `ZoneId.systemDefault()`. Nos endpoints `GET /clients/{id}` (via mapper) e `GET /clients/{id}/personal-data` (via service) a mesma pessoa podia aparecer com idade diferindo em 1 ano na virada do aniversário, dependendo do fuso do host. Como a camada JDBC já fixa `time_zone=UTC`, **alinhei o mapper para `LocalDate.now(ZoneOffset.UTC)`**, tornando o cálculo determinístico e igual nas duas rotas. (`src/main/java/.../dto/ClientMapper.java`.)

---

## 5. Recomendações prioritárias (não aplicadas — decisão sua)

**Alta — infraestrutura de teste**
- Os dois testes (`@SpringBootTest`) dependem de um **PostgreSQL real** (migrations usam `unaccent`, `gen_random_uuid`, índices únicos parciais — incompatíveis com H2, apesar do H2 estar no POM). Sem Postgres no ambiente, `mvn test` falha. Recomendo **Testcontainers** (`@Testcontainers` + `PostgreSQLContainer`) para o suite rodar em qualquer CI sem depender de serviço externo.
- `ClientControllerTest` monta o `MockMvc` sem `.apply(springSecurity())`, então **os testes não passam pela cadeia de segurança** — não detectam regressões de autenticação/autorização. Adicionar um teste com o filtro JWT ativo.
- Não há workflow de CI (`.github/workflows` ausente). O pré-commit roda spotless+checkstyle localmente, mas nada valida build/test no push. Sugiro um workflow que rode `mvn verify` com Testcontainers.

**Média — segurança (já sinalizada no código para pós-MVP)**
- **Autorização por papel ausente:** `SecurityConfig` libera qualquer usuário autenticado em todos os endpoints de negócio (comentado como "próxima fase"). Introduzir `@PreAuthorize`/roles antes de produção.
- **Senha do INSS descriptografada** é devolvida em `GET /clients/{id}` e `/professional-data` a qualquer autenticado. Restringir por papel e/ou omitir em respostas de listagem.
- **CORS aberto** (`allowedOriginPatterns("*")` + `allowCredentials(true)`) — deliberado para dev (ver `CorsConfig`/`ROADMAP`). Fixar origens permitidas em produção.
- **Secrets em default:** `APP_JWT_SECRET`, `APP_ADMIN_PASSWORD` têm fallback de dev. Garantir override obrigatório em produção (falhar o boot se ausente seria mais seguro que padrão fraco).

**Baixa — limpeza**
- Comentário obsoleto no `pom.xml` cita `MapperConfig.java` ("manual bean registration"), mas o `ClientMapper` usa `componentModel = "spring"` e esse arquivo não existe. Remover o comentário.
- `ContributionTimeParser`: o regex de meses (`mes(?:es)?`) não casa a forma acentuada "mês" isolada; hoje o formato usado é "X meses", então sem impacto prático — apenas registrar.
- Padronizar o estilo dos services: `ClientAddressService`/`ClientPaymentService` usam nomes de tipo totalmente qualificados (`org.springframework.data.domain.*`) inline, enquanto os demais importam. Cosmético.

---

## 6. Conformidade com boas práticas Java (checklist)

Injeção por construtor (sem `@Autowired` em campo) ✔ · imutabilidade dos serviços (`final`) ✔ · sem regra de negócio no controller ✔ · DTOs isolando entidades ✔ · transações no service ✔ · tratamento central de exceções sem vazar stack trace ✔ · logging por família de erro (WARN negócio / ERROR sistema) ✔ · enums de domínio com resolução tolerante e documentada ✔ · Flyway como dono único do schema (`ddl-auto: validate`) ✔ · formatação/lint automatizados (Spotless google-java-format AOSP + Checkstyle, 0 erros no último resultado) ✔.

---

## 7. Como validar o build localmente

```bash
# Requer JDK 25 e Docker (para o Postgres via docker-compose ou Testcontainers)
./scripts/start-postgres.sh          # sobe o Postgres local
mvn -q clean compile                 # compila (roda spotless + checkstyle no process-classes)
mvn -q test                          # testes (precisam do Postgres no ar)
mvn -q spring-boot:run               # sobe a API em :8080
# Swagger/OpenAPI: http://localhost:8080/api/docs
```

Se adotar Testcontainers, `mvn test` passa a subir o Postgres sozinho, sem o passo manual.
