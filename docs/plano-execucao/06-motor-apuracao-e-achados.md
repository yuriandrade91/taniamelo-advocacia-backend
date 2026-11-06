# Passo 06 — Motor previdenciário: apuração e achados do CNIS

> **Repo:** backend · **Branch:** `feat/06-motor-apuracao` · **Depende de:** 03, 05 ·
> **Decisões:** T1, D5, D6 · **Esforço:** 1,5–2 semanas

## 0. Antes de começar

Leia, além do `01`: o passo 05 (`ReferenceTables`), o passo 03 (modelo do CNIS),
`service/DisabilityTimeConversion.java` e `ClientDisabilityPeriodService.java` (conversão da
LC 142/2013 que **já existe** — reutilize, não reescreva), `util/TempoDeContribuicao.java`
(convenção de mês de 30 dias), `util/OfficeClock.java`.

**Regra deste passo e do 07:** todo parâmetro legal (idade, prazo, fator, data de corte, período de
graça…) nasce como constante nomeada **e** como linha em `docs/MOTOR_PREVIDENCIARIO.md`
(id, valor, dispositivo legal, status de validação). Teste referencia o id no `@DisplayName`.
Parâmetro sem fonte legal encontrável **não entra** — vai para o relatório como pergunta.

## 1. Objetivo

Um núcleo puro que, dado o perfil do segurado montado a partir do CNIS **confirmado** e dos
períodos informados pelo escritório, devolve a **apuração** (tempo, carência, qualidade de
segurado, salários por competência) e os **achados** (o que falta provar, perguntar ou buscar).
Ainda não decide aposentadoria nenhuma — isso é o passo 07.

## 2. Pacote e tipos — `service/benefit/` (puro; o teste de convenção do passo 05 cobre)

**Entrada** (`service/benefit/profile/`):

- `InsuredProfile` (record): `sex` (só `MASCULINO`/`FEMININO` — outro valor: o service recusa com
  400, como a conversão da LC 142 já faz), `birthDate`, `List<ContributionPeriod>`,
  `List<SalaryEntry>`, `List<DeclaredPeriod>`, `List<DisabilityPeriod>` (do recurso existente).
- `ContributionPeriod`: origem (vínculo do CNIS ou declarado), `FiliationType`, início, fim,
  indicadores, se é concomitante.
- `SalaryEntry`: `YearMonth competence`, `BigDecimal amount`, vínculo de origem.
- `DeclaredPeriod`: períodos que **não** vêm do CNIS e o escritório informa com prova —
  `RURAL_SEGURADO_ESPECIAL`, `ESPECIAL` (com `SpecialGrade` 15/20/25 anos), `MAGISTERIO`,
  `RECONHECIDO_JUDICIALMENTE`. Cada um com início, fim e `evidenceNote`.

**Saída** (`service/benefit/assessment/`):

- `Assessment` (record): para uma data de referência —
  `contributionTime` (anos/meses/dias e total em dias), `contributionTimeAt(cutoff)` para os
  marcos legais (ao menos 16/12/1998, 28/11/1999 e 13/11/2019 — confirme cada um em D5),
  `qualifyingContributions` (carência, em número de competências), `insuredStatus` (mantém
  qualidade de segurado? até quando?), `salariesByCompetence` (consolidados), `steps`
  (memória: lista de `CalculationStep(description, value, legalBasis)`).

**Achados** (`service/benefit/findings/`): `CnisFinding(code, severity, period, lawyerMessage,
clientMessage, requiredEvidence)`. `clientMessage` é em linguagem de cliente (será usada no
relatório e na mensagem de WhatsApp do passo 08).

## 3. Regras da apuração (cada uma: constante + linha no MOTOR + teste)

Implemente segundo o texto legal; os itens abaixo dizem **o que** tratar, não substituem a lei.

1. **Contagem de tempo** de cada período, sem contar duas vezes o mesmo dia (concomitância conta
   **uma vez** para tempo). Método de contagem de dias e conversão para anos/meses/dias
   **definido no MOTOR e validado (D5)**; alinhe com `TempoDeContribuicao`.
2. **Concomitância de salários:** salários de vínculos simultâneos na mesma competência são
   **somados**, limitados ao teto da competência (Tema 1070/STJ) — com a fonte no MOTOR.
3. **Tempo especial → comum** só até 13/11/2019 (vedação da EC 103/2019, art. 25, §2º), com os
   fatores da tabela do art. 70 do Decreto 3.048/1999 por grau e sexo.
4. **Tempo rural de segurado especial** anterior a 11/1991 conta como tempo **sem** contar como
   carência (Lei 8.213/1991, art. 55, §2º) — confirmar e citar.
5. **Competência abaixo do mínimo** depois de 13/11/2019 não conta, salvo complementação ou
   agrupamento (CF, art. 195, §14; EC 103/2019, art. 29) — marque a competência e gere achado.
6. **Carência:** competências que contam para carência segundo a regra de cada tipo de filiação
   (ex.: contribuinte individual em atraso tem regra própria) — levante, cite, valide.
7. **Qualidade de segurado e período de graça** (Lei 8.213/1991, art. 15 e parágrafos),
   incluindo as prorrogações previstas.
8. **Períodos de benefício por incapacidade intercalados** — se e como contam para tempo e
   carência, com a fonte (há entendimento do STF sobre isso; cite o tema/julgado exato).
9. **LC 142/2013** — use a conversão existente para os períodos de deficiência.

## 4. Achados (cada um com código estável, teste e texto revisado)

| Código | Quando | Pede |
| --- | --- | --- |
| `LACUNA_ENTRE_VINCULOS` | intervalo sem contribuição entre dois períodos (limiar no MOTOR) | perguntar ao cliente se trabalhou |
| `COMPETENCIA_SEM_SALARIO` | competência dentro de vínculo sem valor no extrato | contracheque/holerite daquele mês |
| `INDICADOR_PENDENTE` | vínculo com indicador que exige comprovação (catálogo do passo 03) | o documento que o indicador pede |
| `ABAIXO_DO_MINIMO` | competência pós-13/11/2019 abaixo do mínimo | avaliar complementação |
| `VINCULO_SEM_FIM_SEM_REMUNERACAO` | vínculo aberto sem remuneração recente | confirmar desligamento |
| `POSSIVEL_TEMPO_RURAL` | intervalo entre a idade mínima do MOTOR e o primeiro vínculo | perguntar sobre trabalho no campo + documentos de início de prova material |
| `CONCOMITANCIA` | vínculos simultâneos (informativo) | — |

## 5. Casos-ouro e avaliação

- **No repositório:** `src/test/resources/benefit/cases/*.json`, **somente fictícios**, cada um
  com entrada (`InsuredProfile` serializado), data de referência e saída esperada (tempo,
  carência, qualidade de segurado, achados). Mínimo 8 casos cobrindo: só empregado; contribuinte
  individual com atraso; concomitância; especial convertido; rural; abaixo do mínimo pós-reforma;
  lacuna longa com perda de qualidade; deficiência. **O esperado de cada caso fictício é
  conferido pela advogada (D5)** — até lá, o caso leva `"validated": false` e o relatório diz isso.
- **Fora do repositório (D6):** casos reais anonimizados, lidos de `BENEFIT_CASES_DIR`, em teste
  `@Tag("eval-benefit")` excluído do build padrão — mesmo formato. NUNCA dentro do repo.
- Defina o formato JSON em `docs/MOTOR_PREVIDENCIARIO.md` (§ "Formato dos casos"), para o
  escritório montar os casos reais.

## 6. Service Spring

`service/BenefitAssessmentService`: monta `InsuredProfile` a partir da extração CNIS
`CONFIRMADA` vigente (passo 03) + períodos de deficiência existentes + `DeclaredPeriod`s recebidos
na requisição; obtém `ReferenceTables` (passo 05); chama o núcleo. Sem CNIS confirmado → 422
`CNIS_NAO_CONFIRMADO`. Ainda **sem rota pública** — a API nasce no passo 08 junto com a
simulação; aqui o service é exercitado por teste de integração.

## 7. Testes obrigatórios

- Núcleo: um teste por regra do §3 e por achado do §4 (bordas: virada de mês, ano bissexto,
  período de um dia, vínculo sem fim, concomitância parcial).
- `BenefitCasesTest` parametrizado sobre os casos fictícios.
- `BenefitAssessmentIntegrationTest`: do CNIS confirmado (fixture do passo 03) à apuração.
- Teste de convenção do pacote `service/benefit` continua verde.

## 8. Documentação

Crie `docs/MOTOR_PREVIDENCIARIO.md`: visão geral, tabela de parâmetros (id, valor, fonte,
**validado por / em**), regras da apuração, achados, formato dos casos. Atualize
`docs/ARQUITETURA.md` (motor como núcleo puro).

## 9. Critérios de aceite

- [ ] Cada regra do §3 implementada com fonte, ou listada como pergunta no relatório.
- [ ] 8+ casos fictícios passando; status de validação de cada um declarado.
- [ ] Nenhuma dependência de Spring/JPA em `service/benefit` (teste verde).
- [ ] `MOTOR_PREVIDENCIARIO.md` com todos os parâmetros usados.
- [ ] Gate e Sonar back verdes.
- [ ] **Merge só com D5 assinada** para os parâmetros deste passo.

## 10. Commits sugeridos

1. `feat(motor): perfil do segurado e apuração de tempo e carência`
2. `feat(motor): qualidade de segurado, concomitância e conversão de especial`
3. `feat(motor): achados do CNIS`
4. `test(motor): casos de referência fictícios`
5. `docs(motor): parâmetros, fontes e formato dos casos`

## 11. Pare e pergunte se

Duas fontes divergirem; um entendimento depender de jurisprudência sem tese firmada; um caso
fictício não fechar com a conta manual da advogada.
