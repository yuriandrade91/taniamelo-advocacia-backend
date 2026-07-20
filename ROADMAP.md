# Roadmap de evolução — Sistema de Advocacia Previdenciária

Este documento registra o que foi feito na última rodada de melhorias e o que
foi **conscientemente adiado**, para não perder o fio da meada.

## O que foi feito nesta rodada

- Removido do código (model/repository/controller) tudo relacionado ao antigo
  módulo de CNIS. **Correção nesta rodada:** as migrations físicas
  correspondentes (`addresses` antiga e `cnis_simulations` com PDF em BYTEA)
  não tinham sido de fato apagadas/dropadas apesar de documentação anterior
  dizer que sim - agora há um `DROP TABLE IF EXISTS` de verdade para as duas
  (`V15`, ver `docs/DATA_MODEL.md`).
- Cadastro de cliente ampliado: endereço completo, órgão emissor/data do RG,
  nacionalidade, contato via WhatsApp, indicador de deficiência (PCD),
  observações livres e advogado/responsável pelo caso.
- `inss_password` passou a ser criptografado em repouso (AES-256-GCM) via
  `CryptoConverter`, chave vinda de `APP_ENCRYPTION_KEY`.
- Autenticação JWT simples: entidade `User`/`Role`, login em
  `POST /api/v1/auth/login`, todos os endpoints de negócio agora exigem token
  (exceto login e docs). Usuário ADMIN inicial é criado automaticamente no
  primeiro start (`APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD`).
- DTOs de entrada e saída separados (`ClientCreateRequestDTO`,
  `ClientUpdateRequestDTO`, `ClientDetailsDTO` como resposta), mapeamento direto
  via MapStruct.
- `ClientServiceImpl` simplificado: paginação/ordenação agora usa
  `Specification` + `Pageable` do Spring Data (removida a `CriteriaQuery`
  manual que duplicava essa responsabilidade).
- Adicionado `spring-boot-starter-actuator` (o healthcheck do docker-compose
  apontava para `/actuator/health`, que não existia).
- `created_by`/`updated_by` passam a ser preenchidos automaticamente a partir
  do usuário autenticado, em vez de aceitos como input do cliente da API.
- Documentos do cliente e simulações de CNIS (rodada seguinte, `V9`): upload em
  lote, download, PATCH de metadados e soft delete para os dois sub-recursos
  (hoje unificados em `/clients/{id}/files/documents` e `/clients/{id}/files/simulations`), com
  armazenamento de arquivo abstraído por `FileStorageService` (disco local
  agora, trocável por S3 depois). Desenho completo em
  `docs/DATA_MODEL.md` e `docs/ARCHITECTURE.md`.
- Endpoints que faltavam do desenho original de `/api/v1/clients` (`V12`-`V15`):
  - `/clients/{id}/addresses` (CRUD completo, 1:N, endereço principal) —
    nova tabela `client_addresses`; colunas de endereço embutidas em `clients`
    viram legado (mantidas, não removidas ainda).
  - `/clients/{id}/personal-data` (GET/PUT) — recorte só dos campos
    pessoais/cadastrais do cliente, sem tabela própria.
  - `/clients/{id}/not-billable` (PATCH) — endpoint de propósito único para a
    flag de arrecadação, sem tocar em situação.
  - `/clients/{id}/interviews` (CRUD completo) — tabela
    `client_interviews` (data, duração, rich text), com soft delete.
  - `/clients/{id}/payments` (POST/GET/PATCH/DELETE) — nova tabela
    `client_payments` (parcelas de honorários), com status derivado
    "atrasado" calculado em runtime, nunca persistido.
  - `/clients/{id}/situation-history` (histórico; PATCH de situação agora é `PATCH /clients/{id}`) e
    `/clients/{id}/files/*` já existiam - não duplicados.

## Decisão de arquitetura: MVC em camadas

Mantivemos o padrão Controller → Service → Repository (Spring MVC clássico).
Para o escopo atual — um único domínio (clientes), CRUD-cêntrico, time
pequeno — arquitetura hexagonal/Clean Architecture ou CQRS adicionariam
cerimônia sem ganho real. Vale reavaliar quando o sistema ganhar múltiplos
contextos (processos, financeiro, documentos) com regras de negócio mais
complexas por módulo.

## Adiado propositalmente (por fases)

### Fase A — Antes de ir a produção
- Restringir CORS a domínios conhecidos (hoje propositalmente aberto para
  facilitar o desenvolvimento local).
- Trocar `APP_JWT_SECRET`, `APP_ENCRYPTION_KEY`, `APP_ADMIN_PASSWORD` para
  valores fortes e únicos por ambiente (os do `.env` são só para dev local).
- Autorização por papel (`@PreAuthorize`) — hoje qualquer usuário autenticado
  acessa tudo; falta diferenciar ADMIN/LAWYER/STAFF nas rotas sensíveis.
- Testes automatizados: unitários de service (regras de situação, parsing de
  tempo de contribuição), integração com Testcontainers, teste de contrato da
  API de auth.
- CI (GitHub Actions): build + test + lint em cada PR.

### Fase B — Observabilidade
- Logs estruturados (JSON) e correlação de request-id.
- Métricas (Micrometer + Prometheus/Grafana) além do `/actuator/health` básico.
- Auditoria completa de acesso a dados sensíveis (quem viu a senha do INSS de
  qual cliente e quando).

### Fase C — Funcionalidades de domínio previdenciário
- Gestão de processos administrativos/judiciais com prazos e alertas.
- ~~Upload/gestão de documentos (procuração, RG, CNIS, comprovantes)~~ — feito
  (`client_documents`, `V9`), storage local com abstração pronta para S3.
  Pendente: rotina de expurgo definitivo após retenção (hoje é só soft delete)
  e verificação de vírus/malware antes de expor fora da rede local.
- ~~Reintrodução do módulo de simulação de CNIS~~ — feito na forma de
  armazenamento de PDFs importados (`client_cnis_simulations`, `V9`), sem
  cálculo previdenciário. O cálculo em si (tempo de contribuição, regras de
  transição da EC 103/2019, pontos, pedágio) continua adiado — desenhar com
  calma quando houver demanda de gerar a simulação dentro do sistema, em vez
  de só importar um PDF feito em outro lugar.
- ~~Notas de atendimento/entrevista por cliente~~ — feito (`client_interview_notes`, `V13`).
- Agenda de perícias e audiências.
- ~~Controle financeiro: honorários, parcelamento por cliente~~ — feito na
  forma de parcelas individuais (`client_payments`, `V14`). Pendente: contratos
  formais (documento assinado, % de êxito automático sobre valor de benefício
  retroativo) e relatório financeiro consolidado do escritório (hoje só dá
  para ver parcela por parcela, por cliente).
- Notificações (e-mail/WhatsApp) em mudança de situação, parcela vencendo e
  proximidade de prazos.
- ~~Se o número de endereços por cliente crescer, avaliar voltar a uma tabela
  `addresses` 1:N~~ — feito (`client_addresses`, `V12`). Pendente: remover as
  colunas de endereço legadas de `clients` assim que o front migrar de vez
  para `/clients/{id}/addresses` (ver docs/DATA_MODEL.md).
- Autorização por papel nos módulos novos: hoje `/documents`, `/addresses`,
  `/interview_notes` e `/payments` só exigem "estar logado" (mesmo nível de
  qualquer endpoint de cliente) - quando `@PreAuthorize` por papel entrar
  (Fase A), avaliar se STAFF deveria ver dados financeiros/senha do INSS ou
  só ADMIN/LAWYER.

### Fase D — Escala
- Multi-tenant, se o sistema vier a atender mais de um escritório.
- Backup automatizado e plano de disaster recovery do Postgres.

## Nota operacional

Estado real do `db/migration` hoje (`V1` a `V15`) - alguns arquivos foram
renomeados ao longo de rodadas anteriores sem atualizar o comentário de
cabeçalho interno (o nome do arquivo é o que vale para o Flyway):

- `V1`/`V2`: tabelas originais de `clients`/`client_situation_history`.
- `V3`: `users`. `V5`: enriquece `clients` (endereço embutido, RG, etc.).
  `V7`: hardening (remove CHECKs redundantes, índices, FK de `changed_by`).
- `V4` (`addresses`) e `V11` (`cnis_simulations`, PDF em BYTEA): tabelas
  **mortas** de um desenho anterior, sem entidade Java correspondente -
  documentação de uma rodada anterior já as citava como removidas, mas o
  `DROP TABLE` nunca tinha sido escrito de fato. Corrigido na `V15`.
- `V6` e `V10`: migrations vazias (no-op) de rodadas anteriores - inofensivas,
  mantidas por serem migrations já possivelmente aplicadas em algum ambiente
  (nunca se edita/remove uma migration já commitada).
- `V8`: adiciona `clients.updated_at` (necessária - `Client.java` espera essa
  coluna).
- `V9`: `client_documents` + `client_cnis_simulations`.
- `V12`-`V14`: `client_addresses`, `client_interview_notes`, `client_payments`.
- `V15`: `DROP TABLE IF EXISTS` das duas tabelas mortas (`addresses`,
  `cnis_simulations`).

Se já existir um banco de desenvolvimento com um schema antigo/inconsistente,
o caminho mais simples continua sendo derrubar o volume do Postgres local e
deixar o Flyway recriar tudo do zero:

```bash
docker compose down -v
docker compose up
```
