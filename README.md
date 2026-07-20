# taniamelo-advocacia-backend
Java application

## Run (build + run)

**Recommended approach for local development:**

```bash
# Build (skip tests for speed) and run the generated jar
mvn -DskipTests clean package && java -jar target/law-firm-0.0.1-SNAPSHOT.jar
```

**Alternative: run via Maven (auto-recompiles on changes):**

```bash
mvn spring-boot:run
```

## Important Notes

### DevTools and MapStruct
- **DevTools automatic restart is DISABLED** to avoid classloader issues with MapStruct-generated classes.
- When running from VS Code/IDE, the Java extension may use a different classloader that doesn't see generated annotation-processor sources.
- **Solution:** Always run the build task (Cmd+Shift+B or Terminal → Run Build Task: `mvn: build (generate sources)`) before starting the app from the IDE, OR use the terminal commands above.

### CORS
- CORS is permissive for development (allows all origins). **Restrict origins before deploying to production.**

### Database
- PostgreSQL required on `localhost:5432` (database: `system`, user: `postgres`, password: `postgres`).
- You can start a local Postgres instance using the script: `./scripts/start-postgres.sh` (if available).

### Migrations (Flyway)
- O schema é gerenciado 100% pelo Flyway (`src/main/resources/db/migration`); o app roda as
  migrations sozinho ao subir. `spring.jpa.hibernate.ddl-auto` é sempre `validate` - Hibernate
  nunca altera o schema, só confere se as entidades batem com o que o Flyway criou.
- **Nunca edite uma migration já commitada/já aplicada em algum ambiente.** Qualquer ajuste de
  schema vira uma migration nova (`V6`, `V7`, ...).
- **Adotando o Flyway em um banco já existente** (schema criado à mão antes do Flyway): rode uma
  vez com `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` e `SPRING_FLYWAY_BASELINE_VERSION=<versão atual>`
  exportados no shell (ou via `docker-compose`), só nesse boot. Depois disso o `flyway_schema_history`
  já existe e as próximas migrations aplicam normalmente. Alternativa mais simples para dev local:
  `docker compose down -v` e deixar o Flyway recriar tudo do zero (V1 em diante).
- Veja `docs/DATA_MODEL.md` para a modelagem completa das tabelas.

### Massa de dados para teste de carga
- `db/mock-data/seed_load_test.sql` tem ~55 clientes + 2 usuários de exemplo. **Não é uma
  migration** - rode manualmente quando quiser popular um banco local:
  ```bash
  psql "postgresql://postgres:postgres@localhost:5432/system" -f db/mock-data/seed_load_test.sql
  ```
- Login de teste: `dra.tania@taniamelo.adv.br` / `advogado123` (LAWYER) e
  `recepcao@taniamelo.adv.br` / `equipe123` (STAFF).

### Autenticação e Swagger
- Todos os endpoints de negócio exigem um token JWT (`Authorization: Bearer <token>`), obtido em
  `POST /api/v1/auth/login`. Um usuário ADMIN é criado automaticamente no primeiro boot
  (`APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD`, padrão `admin@taniamelo.adv.br` / `changeme123` em dev).
- Swagger UI em `/api/docs`. A página em si é pública (só mostra o formato da API), mas as
  chamadas reais de "Try it out" batem no backend de verdade e exigem token igual a qualquer
  outro cliente:
  1. `POST /api/v1/auth/login` com e-mail/senha, copie o `token` da resposta.
  2. Clique em **Authorize** (canto superior direito do Swagger UI) e cole o token (sem
     precisar digitar "Bearer ", o Swagger já adiciona o prefixo).
  3. A partir daí, todo "Try it out" já envia o header `Authorization` automaticamente.
  - O login não pede token (está marcado com `@SecurityRequirements` vazio) - é o único
    endpoint acessível sem Authorize.

### Arquivos do cliente (documentos e simulações)
- Collection única `/api/v1/clients/{id}/files`, dividida em
  `/files/documents` (11 tipos de documento) e `/files/simulations`
  (versão, vínculos, principal): upload em lote (multipart, uma parte `files`
  + uma parte `metadata` em JSON, na mesma ordem), listagem paginada no
  envelope padrão, download, PATCH de metadados e exclusão (soft delete - o
  arquivo original é preservado). Desenho completo em `docs/DATA_MODEL.md` e
  `docs/ARCHITECTURE.md`.
- Requests de exemplo de TODOS os endpoints: `docs/requests.http`; seed de
  mock: `db/mock-data/seed_mock.sql`.
- Arquivos ficam em disco local por padrão (`app.storage.local.base-path`,
  `./storage` fora de Docker, volume `app-storage` dentro do compose) atrás de
  uma interface (`FileStorageService`) trocável por S3 depois sem mexer em
  entidade/DTO/service.
- Limite de upload: 10MB por arquivo, 60MB por requisição
  (`spring.servlet.multipart.max-*`, ajustável via `application.yaml`).

### Demais sub-recursos de cliente
- `/clients/{id}/personal-data` e `/clients/{id}/professional-data` (GET/PUT): recortes por aba dos dados
  pessoais/cadastrais - não mexe em endereço, benefício, situação ou financeiro.
- `/clients/{id}/not-billable` (PATCH): liga/desliga a flag de arrecadação sem
  tocar em situação.
- `/clients/{id}/addresses` (CRUD completo): endereços residencial/comercial/
  correspondência, um marcado como principal. As colunas de endereço
  embutidas em `clients` são legado (ver `docs/DATA_MODEL.md`).
- `/clients/{id}/interviews` (CRUD completo): entrevistas com data, duração e conteúdo rich text.
- `/clients/{id}/payments` (POST/GET/PATCH/DELETE): parcelas de honorários;
  "atrasado" é calculado na leitura, nunca gravado no banco.

