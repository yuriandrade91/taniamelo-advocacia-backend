# Passo 08 — Simulação de aposentadoria: API e tela

> **Repos:** backend e frontend · **Branch:** `feat/08-simulacao` nos dois · **Depende de:** 07 ·
> **Decisões:** T9, T16 · **Esforço:** 1,5 semana

## 0. Antes de começar

Leia os passos 05–07 e `docs/MOTOR_PREVIDENCIARIO.md`. No front, os mesmos arquivos do passo 04 e
o painel do CNIS que ele criou. Faça **primeiro o backend inteiro** (com api-tests verdes), depois
o front contra ele.

## 1. Objetivo

Na ficha do cliente, a pessoa abre **Cálculo**, revisa as premissas, clica **Simular** e vê: a
melhor aposentadoria hoje, cada regra com "Alcança hoje" / "Em dd/mm/aaaa" / "Não alcança", o valor
estimado, a memória de cálculo e os achados do CNIS com o que pedir ao cliente. Cada simulação
fica **guardada como foi feita** (snapshot imutável).

## 2. Backend

**Migration tenant (próxima livre):**

```
benefit_simulations
  id uuid pk, client_id fk clients on delete cascade,
  cnis_extraction_id fk cnis_extractions,       -- a confirmada usada
  reference_date date not null,
  declared_periods jsonb not null,             -- DeclaredPeriod[] usados
  assumptions jsonb not null,                  -- ProjectionAssumptions
  result jsonb not null,                       -- SimulationResult (abaixo)
  engine_version varchar not null,             -- constante do motor; mude a cada mudança de regra
  reference_tables_version varchar not null,   -- ReferenceTables.version()
  deleted_at timestamptz,                      -- soft delete: é evidência
  + auditoria
```

`SimulationResult` (record serializado em jsonb): `assessment` resumido, `evaluations`
(`RuleEvaluation[]`), `bestRuleId`, `findings`. Mapeie com `@JdbcTypeCode(SqlTypes.JSON)`.

`engine_version`: constante `BenefitEngine.VERSION` no núcleo; um teste falha se o conteúdo de
`MOTOR_PREVIDENCIARIO.md` (hash da tabela de parâmetros) mudar sem a versão mudar — ou outra
trava equivalente que você proponha no plano.

**API — `controller/ClientBenefitSimulationController`**, base
`/api/v1/clients/{clientId}/benefit-simulations`:

| Verbo | Rota | Papel | Faz |
| --- | --- | --- | --- |
| POST | `/` corpo `{ referenceDate?, declaredPeriods[], assumptions }` | autenticado | calcula com o CNIS confirmado vigente e grava; 201 |
| GET | `/` | autenticado | lista resumida (data, melhor regra, valor, versões), mais recente primeiro, paginada |
| GET | `/{simulationId}` | autenticado | snapshot completo |
| DELETE | `/{simulationId}` | **ADVOGADO** | soft delete |
| GET | `/findings` | autenticado | achados do CNIS confirmado vigente, sem simular (para o painel do CNIS) |

`referenceDate` padrão = hoje (`OfficeClock`). Erros: sem CNIS confirmado → 422
`CNIS_NAO_CONFIRMADO`; tabela desatualizada → 422 `TABELA_REFERENCIA_DESATUALIZADA` (passo 05);
sexo fora de masculino/feminino → 400 com a mesma explicação da conversão da LC 142.

**Testes back:** `BenefitSimulationIntegrationTest` (grava, lê igual, soft delete, outro tenant
não vê), `SecuredEndpointsContractTest`, `DtoConventionsTest`,
`api-tests/tests/clients.benefit-simulations.spec.ts` (fluxo com o CNIS fictício: importar →
confirmar → simular → ler → excluir como ADVOGADO; 422 sem CNIS confirmado; STAFF não exclui) +
Postman.

## 3. Frontend

**Onde:** seção/aba **Cálculo** na ficha, ao lado da seção CNIS do passo 04.

- **Premissas** (`defineSection`/`useSectionForm`): data de referência; segue contribuindo?;
  salário futuro (reais); períodos declarados (lista editável: tipo, início, fim, observação de
  prova) — tipos e rótulos do backend.
- **Resultado:** cartão de destaque "Melhor aposentadoria hoje" (regra, valor, data); lista das
  demais regras com `StatusBadge`/`Pill` para o estado e a data; cada regra expande a memória de
  cálculo (`steps` em lista legível, com o dispositivo legal) e os requisitos não cumpridos
  ("faltam 1 ano e 3 meses de contribuição").
- **Achados:** lista com mensagem para o escritório e documento a pedir; ação **Mensagem para o
  cliente** que monta o texto (a partir de `clientMessage` dos achados selecionados) e oferece
  **Copiar** e **Abrir no WhatsApp** (`openInNewTab` com `https://wa.me/<celular>?text=...` usando o
  celular do cliente quando `isWhatsapp`). Módulo puro `whatsappMessage.ts` monta o texto e o link.
- **Histórico:** lista das simulações anteriores (`usePagedList`), abrir em modo leitura, excluir
  (ADVOGADO, `ConfirmDialog`).
- Sem CNIS confirmado: `Notice` explicando e levando ao painel do CNIS.

Código: `paths.ts`, `interfaces/benefitSimulation.ts`, `enums/benefit/` (ruleId, tipos de período
declarado, códigos de achado com rótulo), `services/benefitSimulationService.ts` (com `feedback`),
`components/clients/benefit/` (`ClientBenefitPanel.tsx`, `SimulationResult.tsx`,
`RuleCard.tsx`, `FindingsList.tsx`, `simulationView.ts` puro: ordenação, formatação de datas e
durações, texto de requisito).

**Testes front:** unidade (`simulationView`, `whatsappMessage`: texto, link com telefone
normalizado, sem telefone → sem link), tela (4 estados, sem CNIS → Notice, ADVOGADO x STAFF),
e2e `tests/e2e/benefit-simulation.spec.ts` (do CNIS fictício ao resultado).

## 4. Documentação

Back: `DATA_MODEL.md`, `api-tests/README.md`. Front: `BACKEND_CONTRACT.md`.

## 5. Critérios de aceite

- [ ] Simular a partir do CNIS fictício mostra todas as regras com estado, data e valor.
- [ ] Reabrir uma simulação antiga mostra exatamente o que foi calculado (mesmo após mudança de
      tabela/motor) e as versões usadas.
- [ ] Mensagem de WhatsApp montada a partir dos achados.
- [ ] Gates e Sonar dos dois repos verdes; e2e e api-tests verdes.

## 6. Commits sugeridos

Back: `feat(simulacao): snapshot de simulação com versões do motor e das tabelas`,
`feat(simulacao): rotas de simulação e achados`, `test(api-tests): contrato da simulação`.
Front: `feat(simulacao): serviço e tipos`, `feat(simulacao): premissas e resultado na ficha`,
`feat(simulacao): achados e mensagem ao cliente`, `test(e2e): simulação a partir do CNIS`.

## 7. Pare e pergunte se

O JSON do resultado ficar grande demais para a tela (> 1 MB) — proponha paginação da memória; a
regra de "melhor" precisar de critério além de maior valor.
