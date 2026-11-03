# Passo 16 — IA: "o que tem para hoje"

> **Repos:** backend e frontend · **Branch:** `feat/16-hoje` nos dois · **Depende de:** 10, 12, 13 ·
> **Decisões:** T6, T13 · **Esforço:** 1 semana

## 0. Antes de começar

Leia os passos 12 e 13, `ClientServiceImpl.upcomingBirthdays` (card de aniversariantes que já
existe), `AppointmentService` (consultas por intervalo), e a home do front
(`src/app/(private)/home/page.tsx` e `src/components/home/`).

## 1. Objetivo

Ao abrir o sistema, a pessoa vê **o dia dela**: compromissos e perícias de hoje, prazos vencendo
(hoje e nos próximos dias úteis), publicações novas, análises e pesquisas concluídas, pendências
de documento dos clientes e aniversariantes. Opcionalmente, um **resumo em texto** feito pela IA. E
cada publicação nova pode receber uma **sugestão de prazo** da IA, que a pessoa confirma ou não.

**A lista vem do banco, não do modelo.** A IA só redige o resumo e sugere; nada aparece na lista
porque a IA disse.

## 2. Backend

**Agregado determinístico** — `service/TodayService` + `GET /api/v1/today` (autenticado):

| Bloco | Fonte | Regra |
| --- | --- | --- |
| `appointments` | agenda | hoje (fuso do `OfficeClock`), não cancelados, do escritório; marcar os do usuário |
| `deadlines` | `case_deadlines` | `ABERTO` vencendo hoje e nos próximos N dias úteis (N em config, padrão 3); atrasados à parte |
| `publications` | `publications` | `NOVA` |
| `finishedAi` | análises (11) e pesquisas (14) | concluídas desde o último acesso do usuário (ou 24 h) |
| `pendingDocuments` | achados do CNIS (06/08) | clientes com achado que pede documento, contagem por cliente |
| `birthdays` | existente | reutilize o método atual |

Resposta enxuta (ids, títulos, datas, contagens) e **paginada por bloco** onde puder crescer.
Sem IA. Teste de desempenho simples (resposta em < 500 ms com a massa de demonstração).

**Resumo por IA** — `POST /api/v1/today/briefing` (autenticado; só se IA ligada):
entrada = o JSON do agregado **sem conteúdo de documentos nem de entrevistas**, só títulos, nomes
de cliente e datas; modelo `fast` (T7); saída = texto curto (≤ 120 palavras) + lista de
prioridades referenciando ids do agregado (o front liga cada prioridade ao item). Cache por
usuário por dia (tabela `daily_briefings` no tenant ou Caffeine — escolha no plano e justifique);
"Atualizar" regenera.

**Sugestão de prazo por publicação** —
`POST /api/v1/publications/{publicationId}/ai/suggest-deadline` (autenticado):
saída estruturada `{ actType, days, counting, legalBasis, confidence, reasoning }`. Grava a
sugestão na publicação (`ai_suggestion jsonb`) — **não cria prazo**. A tela do passo 13 mostra a
sugestão pré-preenchendo o formulário de prazo; a pessoa confirma (T13). Rodar a sugestão
automaticamente na ingestão? **Não** neste passo (custo e controle); só sob demanda.

## 3. Frontend

- Home: cartões por bloco seguindo os padrões de carregamento (skeleton por cartão, `loadView`,
  `useSettled` — falha de um bloco não apaga os outros); cada item leva à tela dele.
- Cartão **Resumo do dia** (só com IA ligada): botão gerar/atualizar, texto e prioridades
  clicáveis.
- Na publicação (tela do passo 13): **Sugerir prazo com IA** → formulário pré-preenchido com a
  sugestão, a base legal e o grau de confiança visíveis; criar continua sendo o clique da pessoa.
- Testes unidade/tela (blocos independentes, IA desligada esconde o resumo) e e2e
  `tests/e2e/today.spec.ts` com gateway falso.

## 4. Testes back

`TodayServiceIntegrationTest` (cada bloco, fuso — 23h30 em Brasília continua "hoje", dias úteis),
`TodayBriefingTest` (o conteúdo enviado não contém texto de documento/entrevista — sentinela;
cache por dia), `DeadlineSuggestionTest` (saída validada; sugestão não cria prazo), contratos,
api-tests `today.spec.ts` + Postman.

## 5. Critérios de aceite

- [ ] Home mostra o dia completo sem IA.
- [ ] Resumo e sugestão funcionam com IA ligada e somem com ela desligada.
- [ ] Nenhum prazo criado sem clique humano (teste).
- [ ] Gates e Sonar verdes.

## 6. Commits sugeridos

`feat(hoje): agregado do dia`, `feat(hoje): resumo do dia por IA`, `feat(publicacoes): sugestão
de prazo por IA`, `feat(hoje): home com o dia e o resumo` (front).

## 7. Pare e pergunte se

O agregado precisar de dado que não existe (ex.: "último acesso" do usuário — proponha a coluna);
a sugestão de prazo exigir tabela de prazos por tipo de ato validada pela advogada (provável: peça
a tabela e use-a como contexto do prompt).
