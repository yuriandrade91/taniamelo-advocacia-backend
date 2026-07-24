# Arquitetura — decisões e evolução

## 1. Endpoints separados por aba de cadastro (decisão: manter separados)

A tela do cliente é dividida em abas (dados pessoais, endereços, dados
profissionais, entrevista, arquivos). A API espelha essa divisão com
sub-recursos de `/api/v1/clients/{id}`:

| Aba | Endpoint | Verbo de escrita |
|---|---|---|
| Dados pessoais | `/personal-data` | PUT (substituição do subconjunto) |
| Dados profissionais | `/professional-data` | PUT (substituição do subconjunto) |
| Endereços | `/addresses` (1:N, um principal) | POST/PUT/DELETE |
| Entrevistas | `/interviews` | POST/PUT/DELETE |
| Arquivos | `/files/documents` e `/files/simulations` (upload/lista/detalhe/patch - forma difere por tipo) | POST/GET/PATCH |
| Arquivos (download/exclusão) | `/files/{fileId}/download` e `DELETE /files/{fileId}` (kind-agnostic - resolvido pelo id, sem forma específica por tipo) | GET/DELETE |
| Situação/benefício/arrecadação | `PATCH /clients/{id}` e `PATCH /clients/{id}/not-billable` | PATCH |
| Histórico de situação | `GET /clients/{id}/situation-history` | (leitura - gerado automaticamente pelo PATCH acima) |

**Por que separar é a escolha certa aqui:**

- **Payloads pequenos e validação focada** — cada aba salva só o que ela
  edita; um erro de validação em "dados profissionais" nunca bloqueia o salvar
  de "dados pessoais".
- **Concorrência natural** — duas pessoas editando abas diferentes do mesmo
  cliente não sobrescrevem o trabalho uma da outra (um PUT gigante do cliente
  inteiro faria last-write-wins).
- **Autorização futura por seção** — quando houver papéis (ex.: financeiro só
  edita pagamentos), o corte por endpoint já está pronto.
- **Auditoria granular** — updated_by/updated_at por recurso dizem *o que* foi
  mexido, não só "o cliente mudou".

**O trade-off aceito:** mais endpoints para documentar e mais round-trips se o
frontend quiser salvar tudo de uma vez. Como o fluxo real é salvar por aba,
isso não pesa. O `PUT /clients/{id}` completo continua existindo para o
cadastro/edição geral.

**Regra para não degenerar:** sub-recurso novo só quando corresponde a uma
seção real da UI ou a uma coleção 1:N — não criar endpoint por campo. O
`PATCH /clients/{id}/not-billable` é a exceção deliberada de "propósito único"
(toggle rápido na listagem), e o `PATCH /clients/{id}` genérico cobre o resto.

**Caso oposto — quando NÃO separar por tipo:** documentos e simulações (aba
"Arquivos") têm forma de dado genuinamente diferente (`documentType` vs
`simulationDate`/`version`/`vinculos`) e regra de negócio que só existe de um
lado (simulação tem "principal" automático; documento não) — por isso
upload/lista/detalhe/patch continuam em endpoints separados por tipo, um DTO
por forma, sem campo opcional condicional. Mas download e exclusão não têm
nenhuma diferença de contrato entre os dois (mesmo `FileDownload` binário,
mesmo soft delete) — manter dois endpoints ali era duplicação sem ganho, então
foram unificados em `/files/{fileId}/download` e `DELETE /files/{fileId}`,
resolvendo o tipo pelo próprio registro. Regra geral: separar por forma de
dado/regra de negócio, nunca por identidade — se dois endpoints fariam
exatamente a mesma coisa só mudando o path, é duplicação, não modelagem.

## 2. Microserviços e multi-escritório (visão de evolução)

**Recomendação: NÃO quebrar em microserviços agora.** O sistema é um CRUD
coeso com um único agregado dominante (cliente) e um time pequeno. Quebrar
agora custaria infraestrutura (service discovery, mensageria, observabilidade
distribuída) sem nenhum ganho: não há domínios com escala ou ciclo de deploy
independentes ainda.

O caminho é **monolito modular** com costuras bem definidas, que são exatamente
as linhas de corte caso a plataforma cresça:

| Módulo hoje (pacote/serviço) | Candidato a serviço futuro | Gatilho para extrair |
|---|---|---|
| `security` + `users` | **Identidade/Contas** (auth, usuários, escritórios, papéis) | Multi-escritório com login único |
| `storage` + `client_files` | **Arquivos** (upload/download, S3, antivírus, expurgo) | Volume de storage / processamento de arquivo (OCR de CNIS) |
| `client_payments` | **Financeiro** (cobrança, boletos, conciliação) | Integração com gateway de pagamento |
| núcleo `clients` | **Cadastro/CRM** (permanece o core) | — |

Boas práticas já aplicadas que preservam essas costuras: storage atrás da
interface `FileStorageService` (troca disco→S3 sem tocar em negócio), erros e
envelope padronizados (contrato estável entre futuros serviços), enums de
domínio no código (contrato versionado com deploy).

### Multi-escritório (multi-tenant)

Para comportar mais de um escritório, o passo 1 **não é** microserviço — é
tenancy no monolito:

1. Tabela `offices` (id, nome, plano...); `users.office_id` e `office_id` nas
   tabelas raiz de dados (`clients`); filhos herdam o tenant pelo cliente.
2. `office_id` no JWT; um filtro (Hibernate `@Filter` ou Specification) aplica
   `office_id = :tenant` em toda query — nunca confiar no frontend.
3. Storage particionado por tenant (`offices/{officeId}/clients/...`).
4. Só depois, se um módulo tiver escala própria (ex.: arquivos), extrair o
   serviço já nascendo multi-tenant.

Racional: multi-tenancy por coluna + filtro é o padrão de mercado para SaaS
desse porte (isolamento por linha, um banco só, backup/migração simples).
Schema-por-tenant ou banco-por-tenant só se um cliente exigir isolamento
contratual.

## 3. Padrões transversais adotados nesta revisão

- **Envelope**: toda resposta `{ success, data, pagination?, errors? }`.
- **Paginação**: `pageNumber` (1-based) + `pageSize` em TODA listagem.
- **Filtros**: nomes de parâmetro = nome do campo; enums aceitam nome ou label;
  datas ISO-8601; valor inválido responde 400 explícito (nunca ignorado).
- **Verbos**: POST cria; PUT substitui (objeto completo); PATCH altera
  parcialmente (só not-billable/situação/metadados de arquivo); DELETE remove
  (soft delete onde o dado é evidência).
- **Erros**: central única (`GlobalExceptionHandler`) — 400 validação,
  404/409/422 negócio, 500 sistema (log ERROR com stack, resposta sem detalhe
  técnico).
- **Listas fixas**: enum Java + converter persistindo o label PT-BR; sem CHECK
  em SQL e sem tabela de domínio (decisão registrada em docs/DATA_MODEL.md).
