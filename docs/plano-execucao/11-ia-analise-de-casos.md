# Passo 11 — IA: análise de casos

> **Repos:** backend e frontend · **Branch:** `feat/11-ia-analise` nos dois · **Depende de:** 10 ·
> **Decisões:** T5, T6, D1, D4 · **Esforço:** 2 semanas

## 0. Antes de começar

Leia o passo 10 e `docs/IA.md`. Use a skill `claude-api` para PDF como entrada (`document` em
base64) e saída estruturada. Leia `tenant/TenantContext.java` e `security/CurrentUser.java`:
**os dois são `ThreadLocal`** — processamento em outra thread precisa propagá-los (§3).

## 1. Objetivo

Na ficha, a pessoa escolhe um ou mais documentos (laudo pericial, PPP, carta de indeferimento,
outro), clica **Analisar com IA** e recebe, em até alguns minutos, **pontos objetivos**: o achado,
a página e o trecho literal de onde saiu, por que importa e a providência sugerida. Cada trecho é
**conferido pelo sistema** contra o texto do PDF; o que não confere aparece marcado.

## 2. Modelo de dados (tenant, próxima versão)

```
case_analyses
  id, client_id fk on delete cascade, analysis_type varchar not null,  -- CaseAnalysisType
  question varchar null,                     -- pergunta livre opcional da pessoa
  status varchar not null,                   -- PROCESSANDO, CONCLUIDA, FALHOU
  failure_reason varchar null,
  model varchar, prompt_version varchar, ai_usage_id uuid,
  summary text null,
  + auditoria
case_analysis_files     (analysis_id, client_file_id)  pk composta
case_analysis_findings
  id, analysis_id fk on delete cascade, position int,
  title varchar not null, explanation text not null, suggested_action text not null,
  client_file_id uuid not null, page int not null, excerpt text not null,
  excerpt_verified boolean not null,          -- conferido pelo sistema, não pelo modelo
  decision varchar null,                      -- ACEITO, DESCARTADO (pela pessoa)
  decided_by uuid null, decided_at timestamptz null
```

Enums: `CaseAnalysisType` (`LAUDO_PERICIAL`, `PPP`, `INDEFERIMENTO`, `OUTRO`),
`CaseAnalysisStatus`, `FindingDecision`.

## 3. Backend

- `service/CaseAnalysisService`: valida arquivos (do cliente, PDF, `AiDocumentGate` do passo 10
  para cada um), cria `PROCESSANDO`, dispara o processamento assíncrono e devolve 202.
- **Assíncrono:** `config/AsyncConfig` com `@EnableAsync` e um `ThreadPoolTaskExecutor` dedicado
  à IA (tamanho em config), com `TaskDecorator` que **copia `TenantContext` e o
  `SecurityContext`** para a thread e limpa no fim. Teste que prova a propagação (o resultado é
  gravado no schema certo e com o autor certo). Falha no processamento → `FALHOU` com motivo
  legível; nunca fica `PROCESSANDO` para sempre (timeout do passo 10 + marca de falha).
- Contexto enviado (via `AiContextBuilder`): dados mínimos do cliente (benefício pretendido,
  situação, data de nascimento/sexo se relevantes), resultado resumido da última simulação se
  houver, e os PDFs. Prompt `case-analysis-v1.md` por tipo de análise, pedindo pontos objetivos com
  página e **trecho literal**.
- Saída estruturada (schema): `summary`, `findings[]` (`title`, `explanation`, `suggestedAction`,
  `fileIndex`, `page`, `excerpt`).
- **Conferência do trecho (`service/citation/ExcerptVerifier`, núcleo puro com teste de
  convenção do pacote — o passo 14 reutiliza este pacote):** extrai o texto da página com PDFBox e
  confere se o trecho aparece, normalizando espaço, hifenização de quebra de linha, caixa e
  acento. `excerpt_verified` = resultado. Teste com casos de borda.
- Rotas — base `/api/v1/clients/{clientId}/case-analyses`:

| Verbo | Rota | Papel | Faz |
| --- | --- | --- | --- |
| POST | `/` corpo `{ analysisType, fileIds[], question? }` | autenticado | cria e processa; 202 com id e status |
| GET | `/` | autenticado | lista (paginada) |
| GET | `/{analysisId}` | autenticado | status + resultado |
| PATCH | `/{analysisId}/findings/{findingId}` corpo `{ decision }` | autenticado | aceita/descarta |
| DELETE | `/{analysisId}` | **ADVOGADO** | exclui (física; o registro de uso fica em `ai_usage`) |

## 4. Frontend

- Na lista de documentos da ficha: ação **Analisar com IA** (só se `useAiSettings` diz ligado),
  abrindo `ModalShell` para escolher tipo, documentos e pergunta opcional. Documento médico sem
  consentimento aparece desabilitado com a explicação e o atalho para registrar (passo 10).
- Seção **Análises** na ficha: lista; abrir mostra resumo e os pontos; cada ponto com selo
  **trecho conferido** / **trecho não encontrado no documento**, página clicável (abre o PDF na
  página, se o visualizador do passo 04 permitir; senão, mostra a página), e botões
  **Aceitar** / **Descartar**.
- Status `PROCESSANDO`: polling a cada 5 s com parada ao concluir/falhar/fechar (hook puro e
  testado; sem polling infinito).
- Testes de unidade (polling, agrupamento), tela (4 estados + processando + falhou + IA
  desligada) e e2e `tests/e2e/case-analysis.spec.ts` **contra o backend com `FakeAiGateway`**:
  crie `service/ai/DevFakeAiGateway` em `src/main`, ativado **só** por `app.ai.fake=true` e só nos
  perfis `dev`/`test` (boot recusado com a propriedade ligada em outro perfil, com teste), que
  devolve respostas fixas por `AiFeature`. Assim o e2e e o api-tests não dependem de chave nem
  gastam. Os passos 14–17 acrescentam respostas a ele.

## 5. Testes back

`ExcerptVerifierTest`, `CaseAnalysisServiceTest` (validações, médico sem consentimento, arquivo de
outro cliente), `CaseAnalysisIntegrationTest` (assíncrono com `FakeAiGateway`: tenant e autor
propagados, `CONCLUIDA` com achados, `FALHOU` em erro, refusal tratado), contratos, api-tests
`clients.case-analyses.spec.ts` (com o gateway falso) + Postman. Avaliação `@Tag("ai-eval")`:
5 laudos **fictícios** escritos para o teste, com os pontos esperados; mede quantos o modelo acha.

## 6. Critérios de aceite

- [ ] Fluxo do §1 completo com o gateway falso, e uma vez com o real (`ai-live`) se houver chave.
- [ ] Todo trecho conferido pelo sistema; não conferido fica visível como tal.
- [ ] Aceitar/descartar gravado (métrica do PLANO: ≥ metade aceita — medida depois em uso).
- [ ] Gates e Sonar verdes.

## 7. Commits sugeridos

`feat(analise): processamento assíncrono com tenant e autor propagados`,
`feat(analise): análise de documentos com conferência de trecho`, `feat(analise): análises na
ficha` (front), `test(e2e): análise de laudo com gateway falso`.

## 8. Pare e pergunte se

O PDF for imagem sem texto (escaneado): o verificador não confere — proponha (OCR é dependência
nova) e não faça sem aprovação; o tempo de resposta passar de 5 min.
