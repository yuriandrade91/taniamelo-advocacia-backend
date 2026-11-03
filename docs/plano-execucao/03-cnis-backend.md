# Passo 03 — Importação do CNIS (backend)

> **Repo:** backend · **Branch:** `feat/03-cnis-backend` · **Depende de:** — ·
> **Decisões:** T2, T3, T8, D7 · **Esforço:** 1,5–2 semanas

## 0. Antes de começar

Leia, além do `01`: `model/ClientFile.java`, `service/ClientFileService.java`,
`controller/ClientFileController.java`, `storage/FileStorageService.java`,
`util/TempoDeContribuicao.java`, `model/BenefitType.java` (padrão de enum), `V6__client_files.sql`,
`api-tests/tests/clients.files.spec.ts`. Confirme que `org.apache.pdfbox` está no classpath
(`mvn dependency:tree | grep pdfbox` — vem do `openhtmltopdf-pdfbox`). Se a versão transitiva for
inadequada, declare-a explicitamente no `pom.xml` e diga no plano.

## 1. Objetivo

O PDF do CNIS que já sobe como simulação (`files/simulations`) vira **vínculos e competências
estruturados**, em estado **pendente de conferência**, que uma pessoa edita e uma ADVOGADA confirma.
Só a extração **confirmada** alimenta o cálculo (passos 06–07).

Verificável: dado o PDF fictício de teste, `POST .../cnis/extractions` devolve os vínculos e as
competências exatos do fixture; com o conjunto real D7 (fora do repo), ≥ 98% de acerto em datas e
valores.

## 2. Fora do escopo

Tela (passo 04). Cálculo de tempo (06). Fallback por IA (só se o critério falhar; vira tarefa nova
depois do passo 10). Baixar CNIS do Meu INSS (T3: proibido).

## 3. Modelo de dados — migration tenant `V30__cnis.sql` (ou a próxima livre)

```
cnis_extractions
  id uuid pk, client_id uuid not null fk clients on delete cascade,
  client_file_id uuid not null fk client_files,      -- o PDF de origem
  status varchar not null,                            -- label de CnisExtractionStatus
  issued_on date,                                     -- data de emissão impressa no extrato
  holder_cpf varchar(11) not null, holder_nit varchar(11), holder_name varchar not null,
  parser_version varchar not null,
  confirmed_by uuid, confirmed_at timestamptz,
  created_by/updated_by/created_at/updated_at          -- como as vizinhas
  -- índice: (client_id, status); unicidade parcial: um CONFIRMADA por client_id

cnis_bonds                       -- "vínculo" / "relação previdenciária" do CNIS
  id, extraction_id fk on delete cascade, sequence int not null,
  origin_document varchar,       -- CNPJ/CEI/CPF do empregador, só dígitos
  origin_name varchar,
  filiation_type varchar not null,   -- label de FiliationType
  start_date date not null, end_date date,           -- end null = sem data fim no extrato
  last_remuneration_competence date,                 -- "últ. remun." quando houver
  indicators text[] not null default '{}',           -- códigos (PEXT, AEXT-VI, ...)
  source varchar not null,           -- label de CnisDataSource
  edited boolean not null default false,
  + auditoria

cnis_contributions              -- competência a competência
  id, bond_id fk on delete cascade, competence date not null (dia 1),
  amount numeric(14,2),          -- remuneração/salário de contribuição; null = mês sem valor no extrato
  indicators text[] not null default '{}',
  source varchar not null, edited boolean not null default false
  -- unicidade (bond_id, competence)

cnis_extraction_issues          -- o que a validação achou (não bloqueia gravar; bloqueia confirmar se BLOQUEANTE)
  id, extraction_id fk on delete cascade, severity varchar not null, code varchar not null,
  message varchar not null, bond_id uuid null
```

Ao **confirmar**: a extração vira `CONFIRMADA`, a anterior confirmada do mesmo cliente vira
`SUBSTITUIDA`, e `client_files.vinculos` do arquivo de origem recebe a contagem de vínculos.

## 4. Código

**Enums (`model/`)**, padrão `BenefitType` (constante PT, label PT, converter):

- `CnisExtractionStatus`: `PENDENTE_CONFERENCIA("Pendente de conferência")`, `CONFIRMADA`,
  `SUBSTITUIDA`, `DESCARTADA`.
- `FiliationType`: `EMPREGADO`, `EMPREGADO_DOMESTICO`, `CONTRIBUINTE_INDIVIDUAL`, `FACULTATIVO`,
  `SEGURADO_ESPECIAL`, `TRABALHADOR_AVULSO`, `BENEFICIO` (o CNIS lista benefício recebido como
  linha) — labels como o CNIS escreve; confira nos PDFs reais.
- `CnisDataSource`: `PARSER("Leitura automática")`, `MANUAL("Informado manualmente")`.
  (`MODELO` só nasce se o fallback de IA for feito — não crie agora.)
- `IssueSeverity`: `BLOQUEANTE`, `ALERTA`.

**Núcleo puro `service/cnis/`** (sem Spring/JPA; teste de convenção obrigatório — `01` §3):

| Classe | Responsabilidade |
| --- | --- |
| `CnisPdfTextReader` | PDF (bytes) → linhas de texto com posição (página, y, x), via PDFBox. Única classe que conhece PDFBox |
| `CnisParser` | linhas → `CnisDocument` (cabeçalho, vínculos, competências). Constante `VERSION`. Layout desconhecido → `CnisLayoutNotRecognizedException` (nunca resultado parcial calado) |
| `CnisDocument`, `CnisHolder`, `CnisBondLine`, `CnisContributionLine` | `record`s imutáveis |
| `CnisIndicatorCatalog` | código → descrição em linguagem de escritório + se exige comprovação. **Construa a lista a partir dos PDFs reais e da documentação oficial do INSS**, com fonte; código desconhecido é mantido e marcado "indicador não catalogado" (não descartado) |
| `CnisValidator` | `CnisDocument` + CPF do cliente → `List<CnisIssue>` |

**Validações (`CnisValidator`)** — cada uma é um teste:

1. CPF do titular ≠ CPF do cliente → `BLOQUEANTE` `CNIS_CPF_DIVERGENTE`. (Na importação, isto
   **recusa** com 422 — não grava extração de outra pessoa.)
2. `end_date < start_date` → `BLOQUEANTE`.
3. Competência fora do intervalo do vínculo → `ALERTA`.
4. Competência duplicada no mesmo vínculo → `BLOQUEANTE`.
5. Vínculo com indicador que exige comprovação → `ALERTA` com a descrição do catálogo.
6. Vínculo sem data fim e sem remuneração recente → `ALERTA` "vínculo em aberto sem
   remuneração".

**Service Spring `service/CnisImportService`** — no pacote `service/` (fora de `service/cnis`, que
fica 100% puro): carrega o arquivo pelo `FileStorageService`, exige `kind = SIMULATION` e MIME PDF, roda leitor →
parser → validador, persiste `PENDENTE_CONFERENCIA` com `source = PARSER`, devolve DTO.

**API — `controller/ClientCnisController`**, base `/api/v1/clients/{clientId}/cnis/extractions`:

| Verbo | Rota | Papel | Faz |
| --- | --- | --- | --- |
| POST | `/` corpo `{ "fileId": uuid }` | autenticado | importa; 201 com a extração completa |
| GET | `/` | autenticado | lista (resumo: status, emissão, nº vínculos, nº issues) — sem paginação, é por cliente |
| GET | `/{extractionId}` | autenticado | extração + vínculos + competências + issues |
| GET | `/current` | autenticado | a `CONFIRMADA` vigente; 404 se não houver |
| POST | `/{extractionId}/bonds` | autenticado | vínculo manual (`source=MANUAL`) |
| PUT | `/{extractionId}/bonds/{bondId}` | autenticado | edita vínculo (marca `edited`) |
| PUT | `/{extractionId}/bonds/{bondId}/contributions` | autenticado | substitui a lista de competências do vínculo |
| DELETE | `/{extractionId}/bonds/{bondId}` | **ADVOGADO** | remove vínculo |
| POST | `/{extractionId}/confirm` | **ADVOGADO** | confirma; 422 se houver issue `BLOQUEANTE` |
| POST | `/{extractionId}/discard` | **ADVOGADO** | descarta |

Regras de estado: só `PENDENTE_CONFERENCIA` aceita edição/confirmação/descarte (senão 422
`CNIS_EXTRACAO_NAO_EDITAVEL`). Toda edição **revalida** e regrava as issues. DTOs:
`CnisExtractionResponseDTO`, `CnisExtractionSummaryDTO`, `CnisBondRequestDTO`/`ResponseDTO`,
`CnisContributionDTO`, `CnisIssueDTO`, `CnisImportRequestDTO` — `@JsonPropertyOrder({"id"})`,
`clientId` onde for o cliente.

Erros novos em `BusinessErrorCode`: `CNIS_CPF_DIVERGENTE`, `CNIS_LAYOUT_NAO_RECONHECIDO`,
`CNIS_ARQUIVO_INVALIDO`, `CNIS_EXTRACAO_NAO_EDITAVEL`, `CNIS_PENDENCIA_BLOQUEANTE`.

## 5. Fixtures e avaliação

- **Fixture fictício** `src/test/resources/cnis/cnis-ficticio-01.pdf` (+ `.expected.json`): gerado
  por um gerador **versionado** (`src/test/java/.../cnis/CnisFixtureGenerator` ou script em
  `scripts/`), que desenha um PDF no **mesmo layout** do CNIS real com pessoa inventada (CPF válido
  gerado, empregadores inventados). Pelo menos 3 fixtures: simples (2 vínculos); com indicadores e
  meses sem valor; com contribuinte individual e benefício. O mesmo PDF serve ao `api-tests`.
- **Avaliação real (D7)**: teste `CnisParserEvaluationTest` com `@Tag("eval-cnis")`, **excluído
  do build padrão**, lendo PDFs e `.expected.json` de um diretório fora do repo indicado por
  `CNIS_EVAL_DIR`. Imprime acerto por campo (datas, valores, tipo, indicadores). Sem a variável,
  o teste se pula com aviso. **NUNCA** copie um CNIS real para dentro do repositório.

## 6. Testes obrigatórios

- `CnisParserTest` (fixtures + bordas: vínculo sem fim, competência sem valor, várias páginas,
  layout estranho → exceção).
- `CnisValidatorTest` (as 6 validações, uma por caso).
- `CnisIndicatorCatalogTest` (código conhecido, desconhecido).
- Teste de convenção do pacote `service/cnis`.
- `CnisImportIntegrationTest extends PostgresIntegrationTest`: importa fixture, edita, confirma,
  substitui a anterior, `client_files.vinculos` atualizado, 422 nos estados inválidos,
  unicidade de `CONFIRMADA`.
- `SecuredEndpointsContractTest`: as 10 rotas classificadas.
- `api-tests/tests/clients.cnis.spec.ts`: sobe o fixture como simulação, importa, edita,
  confirma como ADVOGADO, STAFF recebe 403 ao confirmar, CPF divergente → 422 com código no
  envelope, limpeza no fim. Requests equivalentes na coleção Postman.

## 7. Documentação

`docs/DATA_MODEL.md` (4 tabelas), `docs/ARQUITETURA.md` (subpacote `service/cnis` e a regra de
núcleo puro), `api-tests/README.md` (spec novo), novo `docs/CNIS.md` curto: layout suportado,
catálogo de indicadores com fonte, como rodar a avaliação.

## 8. Critérios de aceite

- [ ] As 10 rotas respondem conforme a tabela, com envelope e papéis.
- [ ] Fixtures fictícios extraídos com 100% de igualdade ao `.expected.json`.
- [ ] Avaliação D7 ≥ 98% em datas e valores — **ou** relatório dizendo "não medido: D7 pendente".
- [ ] CNIS de outro CPF recusado com 422 `CNIS_CPF_DIVERGENTE`.
- [ ] Gate completo do `01` §9 verde, Sonar back com gate OK.

## 9. Commits sugeridos

1. `feat(cnis): tabelas de extração, vínculos e competências`
2. `feat(cnis): leitor e parser do PDF do CNIS`
3. `feat(cnis): validação e catálogo de indicadores`
4. `feat(cnis): importação, conferência e confirmação pela API`
5. `test(api-tests): contrato das rotas de CNIS`
6. `docs(cnis): layout suportado, indicadores e avaliação`

## 10. Pare e pergunte se

- O layout dos PDFs reais divergir entre si a ponto de exigir dois parsers.
- Um indicador real não tiver significado oficial encontrável.
- A avaliação D7 ficar abaixo de 98% — **não** "ajuste" o esperado; reporte onde erra.
