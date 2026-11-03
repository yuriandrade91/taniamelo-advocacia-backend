# Passo 05 — Tabelas de referência previdenciária

> **Repo:** backend · **Branch:** `feat/05-tabelas-referencia` · **Depende de:** — ·
> **Decisões:** T1, T11 · **Esforço:** 1 semana

## 0. Antes de começar

Leia, além do `01`: `db/migration/shared/*` (forma das migrations em `public`), `model/Tenant.java`
(entidade com `@Table(schema = "public")` e o comentário sobre `search_path`),
`tenant/SchemaMultiTenantConnectionProvider.java` (`search_path` = tenant, `public`),
`util/OfficeClock.java`.

## 1. Objetivo

O motor de cálculo (passos 06–07) precisa de quatro séries oficiais, iguais para todos os
escritórios, versionadas e com fonte. Este passo as cria, carrega o histórico e entrega ao motor um
`ReferenceTables` imutável — e **recusa calcular** quando a série não cobre a data pedida.

## 2. As séries

| Tabela (`public`) | Conteúdo | Fonte oficial a usar |
| --- | --- | --- |
| `ref_minimum_wage` | `valid_from date pk`, `amount numeric(14,2)` — salário mínimo desde 07/1994 | Leis/decretos anuais do salário mínimo |
| `ref_contribution_ceiling` | `valid_from date pk`, `amount numeric(14,2)` — teto do RGPS desde 07/1994 | Portarias interministeriais anuais (MPS/MF) |
| `ref_correction_index` | `competence date pk` (dia 1), `index_name varchar`, `monthly_rate numeric(12,8)` — variação mensal do índice **legalmente aplicável** à correção de salários de contribuição naquela competência | Portarias mensais de atualização dos salários de contribuição + série do índice (IBGE/FGV) |
| `ref_life_expectancy` | `table_year int`, `age int`, `expectancy_years numeric(5,2)`, pk (table_year, age) — tábua completa de mortalidade, ambos os sexos | IBGE (publicada todo dezembro; vale para benefícios do ano seguinte) |
| `ref_dataset_versions` | `dataset varchar pk`, `covered_until date`, `source_url varchar`, `source_note varchar`, `loaded_at timestamptz` | — |

**O índice de correção mudou ao longo do tempo** (houve períodos com índices diferentes desde
1994). Levante, com a fonte legal de cada faixa, qual índice vale em cada competência, e grave
`index_name` por linha. NÃO assuma um índice único para o período inteiro.

**Conferência obrigatória:** o fator acumulado calculado a partir de `ref_correction_index`
(produto de `1 + monthly_rate` entre a competência e o mês anterior ao início do benefício) tem de
bater com a **tabela oficial publicada** em pelo menos **5 meses de início** diferentes,
espalhados (ex.: 2005, 2012, 2019, 2023, último disponível) e, em cada um, 5 competências. Grave
esses 25 pontos como teste (`CorrectionFactorOfficialSampleTest`), com a portaria citada no
`@DisplayName`. Se não bater, **pare e reporte** — não ajuste arredondamento até bater.

## 3. Migrations (shared, próximas livres — hoje `V6`, `V7`)

- `V6__reference_tables.sql`: estrutura das 5 tabelas, comentários `COMMENT ON` com a fonte.
- `V7__reference_data_historico.sql`: carga histórica até a última publicação disponível.
  Gere o SQL a partir de CSVs versionados em `db/reference/*.csv` — um por série, com cabeçalho
  e coluna `fonte`; fora de `src/main/resources` para não irem dentro do jar (a pasta `db/` já
  existe na raiz) — por um script `scripts/ref-tables/gerar-migration.mjs` (ou
  `.sh`), para que a atualização mensal seja repetível. O CSV é a fonte; a migration é o produto.
- Atualização futura = **CSV atualizado + migration nova** gerada pelo script (T11). Documente o
  passo a passo em `docs/TABELAS_DE_REFERENCIA.md`.

Valores para conferência rápida (confirme na fonte antes de usar como teste): salário mínimo
2024 = R$ 1.412,00, 2025 = R$ 1.518,00; teto 2024 = R$ 7.786,02, 2025 = R$ 8.157,41.

## 4. Código

- Entidades em `model/` (plano, sem subpacote), prefixo `Ref` (`RefMinimumWage`,
  `RefContributionCeiling`, `RefCorrectionIndex`, `RefLifeExpectancy`, `RefDatasetVersion`),
  todas `@Table(schema = "public")`, **somente leitura** (sem setters públicos; repositórios só
  com consultas).
- `repository/Ref*Repository`.
- Núcleo puro `service/benefit/reference/`: `ReferenceTables` (record imutável com as séries
  carregadas e métodos `minimumWageOn(LocalDate)`, `ceilingOn(LocalDate)`,
  `correctionFactor(YearMonth competence, YearMonth benefitStart)`,
  `lifeExpectancy(int tableYear, int age)`, `coveredUntil()`, `version()`), e
  `ReferenceDataOutdatedException`. Sem Spring/JPA (teste de convenção do pacote
  `service/benefit` — criado aqui e reaproveitado nos passos 06–07).
- Spring `service/ReferenceTablesService`: monta o `ReferenceTables` a partir dos repositórios,
  com cache em memória (Caffeine, como `HolidayService`) invalidado na subida — os dados só mudam
  com deploy.
- `version()` = hash curto determinístico do conteúdo das séries (ou do `covered_until` +
  `loaded_at` de cada dataset). Ele vai gravado em cada simulação (passo 08) para o cálculo ser
  reproduzível.
- API `controller/ReferenceTablesController`, `GET /api/v1/reference-tables/status` (autenticado):
  por série, `coveredUntil`, `source`, e `outdated` (true se `coveredUntil` < mês corrente de
  `OfficeClock` menos a defasagem normal de publicação — defina a defasagem por série com
  justificativa no Javadoc).

**Regra de recusa:** pedir fator, teto ou mínimo para data além de `coveredUntil` lança
`ReferenceDataOutdatedException`, que vira **422** `TABELA_REFERENCIA_DESATUALIZADA` com mensagem
dizendo qual série e até quando cobre. NUNCA use o último valor conhecido em silêncio.

## 5. Testes obrigatórios

- `ReferenceTablesTest` (núcleo): mínimo/teto na véspera e no dia da mudança; fator acumulado;
  data além da cobertura → exceção; competência anterior a 07/1994 → exceção.
- `CorrectionFactorOfficialSampleTest`: os 25 pontos oficiais.
- `ReferenceDataCompletenessTest` (integração): nenhuma competência faltando de 07/1994 até
  `covered_until`; mínimo e teto com vigência para todo dia do período; tábua com idades 0–80+
  para cada ano carregado.
- `ReferenceTablesIntegrationTest`: lê do `public` estando num tenant (o `search_path` funciona).
- `SecuredEndpointsContractTest`, `api-tests/tests/reference-tables.spec.ts` (status; 401 sem
  token) + Postman.

## 6. Documentação

`docs/TABELAS_DE_REFERENCIA.md` (séries, fontes, como atualizar, como conferir),
`docs/DATA_MODEL.md`, `docs/ARQUITETURA.md` (tabelas em `public` lidas pelo tenant).

## 7. Critérios de aceite

- [ ] As 4 séries carregadas até a última publicação, com fonte por linha.
- [ ] 25 pontos oficiais do fator batendo.
- [ ] Recusa explícita fora da cobertura.
- [ ] Script de atualização documentado e reproduzível (rodar duas vezes gera o mesmo SQL).
- [ ] Gate e Sonar back verdes.

## 8. Commits sugeridos

1. `feat(referencia): tabelas de salário mínimo, teto, índice de correção e tábua do IBGE`
2. `feat(referencia): carga histórica a partir dos CSVs oficiais`
3. `feat(referencia): leitura das séries pelo motor e recusa fora da cobertura`
4. `docs(referencia): fontes e rotina de atualização`

## 9. Pare e pergunte se

Uma fonte oficial não for encontrável para alguma faixa; o fator não bater com a tabela oficial;
houver dúvida sobre qual índice vale num período.
