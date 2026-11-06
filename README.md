# taniamelo-advocacia-backend
Java application

## Build e execução

A aplicação roda em contêiner (Docker Compose) na EC2, atrás de um proxy TLS. O roteiro está em
`docs/AWS_EC2_ARM64_DEPLOY.md` (instância) e `docs/PROXY_TLS.md` (HTTPS); a configuração é toda por
variável de ambiente, modelada em `.env.example`. A checklist da virada para produção está em
`docs/PLANO_VIRADA_PRODUCAO.md`.

```bash
mvn clean verify    # build + testes (precisa do Docker: o Testcontainers sobe o Postgres)
```

### Banco de dados
- PostgreSQL 17 (serviço `db` do compose), com o schema gerenciado só pelo Flyway.
- O banco **sobe vazio e continua vazio**: nenhuma migration ou rotina semeia escritório, usuário ou
  cliente. O escritório entra por `scripts/tenant/` (ver `docs/CADASTRO_DE_TENANT.md`).

### Migrations (Flyway)
- O schema é gerenciado 100% pelo Flyway (`src/main/resources/db/migration`); o app roda as
  migrations sozinho ao subir. `spring.jpa.hibernate.ddl-auto` é sempre `validate` - Hibernate
  nunca altera o schema, só confere se as entidades batem com o que o Flyway criou.
- Os números V23, V25, V28 e V29 não existem nesta branch: eram massa de demonstração e ficaram
  de fora. A próxima migration é a V30.
- **Nunca edite uma migration já commitada/já aplicada em algum ambiente.** Qualquer ajuste de
  schema vira uma migration nova (`V9`, `V10`, ...).
- Veja `docs/DATA_MODEL.md` para a modelagem completa das tabelas e `docs/ARCHITECTURE.md` para as
  decisões de desenho da API.

### Autenticação e Swagger
- Todos os endpoints de negócio exigem um token JWT (`Authorization: Bearer <token>`), obtido em
  `POST /api/v1/auth/login`. Nenhuma conta nasce sozinha: os usuários do
  escritório vêm de `scripts/tenant/02-criar-usuarios.sql` e o agente de suporte, de
  `scripts/tenant/04-criar-agente-suporte.sql`.
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
  envelope padrão e PATCH de metadados - essas ações têm forma de dado
  diferente por tipo, por isso continuam em endpoints separados.
- `download` e exclusão não variam por tipo, então são únicos para os dois:
  `GET /files/{fileId}/download` e `DELETE /files/{fileId}` (soft delete - o
  objeto vai para `<schema>/_trash/` e nada é apagado; o tipo é resolvido a partir do próprio id).
  `POST /files/{fileId}/restore` desfaz.
  Desenho completo em `docs/DATA_MODEL.md` e `docs/ARCHITECTURE.md`.
- Em produção os arquivos ficam no S3 (`APP_STORAGE_TYPE=s3`), cifrados antes de sair da aplicação, atrás
  de uma interface (`FileStorageService`) que isola o provedor.
- Limite de upload: 10MB por arquivo, 60MB por requisição
  (`spring.servlet.multipart.max-*`, ajustável via `application.yaml`).

### Situação, benefício, tipo de cliente e arrecadação
- `PATCH /clients/{id}` faz atualização parcial do cliente: envie só os campos que quer mudar
  (`situation`, `benefit`, `clientType` e/ou `notBillable`, em qualquer combinação - aceitam o
  nome do enum ou o label PT-BR; `clientType` só aceita `Verificado`/`Potencial`). Mudar
  `situation` grava automaticamente um registro em `GET /clients/{id}/situation-history`
  (paginado, mais recente primeiro); mudar `benefit`/`clientType`/`notBillable` não gera
  histórico. Não há endpoint de propósito único para `notBillable` - enviar só esse campo no
  PATCH genérico tem o mesmo efeito.

### Demais sub-recursos de cliente
- `/clients/{id}/personal-data` e `/clients/{id}/professional-data` (GET/PUT): recortes por aba dos dados
  pessoais/cadastrais - não mexe em endereço, benefício, situação ou financeiro.
- `/clients/{id}/addresses` (CRUD completo): endereços residencial/comercial/
  correspondência, um marcado como principal (endereço não é mais embutido em `clients`).
- `/clients/{id}/interviews` (CRUD completo): entrevistas com data, duração e conteúdo rich text.
- `/clients/{id}/payments` (POST/GET/PATCH/DELETE): parcelas de honorários;
  "atrasado" é calculado na leitura, nunca gravado no banco.
