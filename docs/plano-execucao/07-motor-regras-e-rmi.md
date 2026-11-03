# Passo 07 — Motor previdenciário: regras de aposentadoria, RMI e projeção

> **Repo:** backend · **Branch:** `feat/07-motor-regras` · **Depende de:** 06 ·
> **Decisões:** T1, D5, D6 · **Esforço:** 2,5–3 semanas

## 0. Antes de começar

Leia o passo 06 e o `docs/MOTOR_PREVIDENCIARIO.md` que ele criou. A mesma regra vale: **todo
parâmetro com fonte legal, constante nomeada, linha no MOTOR e teste**. Este é o passo de maior
risco jurídico do plano; prefira parar e perguntar a "interpretar".

## 1. Objetivo

Para um `Assessment` (passo 06) e uma data de referência, avaliar **cada regra** de aposentadoria
aplicável e devolver: elegível hoje?, se não, **em que data** fica (supondo que siga contribuindo),
o **valor estimado** (RMI) com memória de cálculo, e qual é a **melhor regra hoje**.

## 2. Contrato do núcleo — `service/benefit/rules/`, `service/benefit/amount/`

```java
public interface RetirementRule {
    RuleId id();                                   // enum estável, vai para o banco e para a tela
    RuleEvaluation evaluate(RuleContext context);  // puro e determinístico
}
// RuleContext: Assessment + InsuredProfile + ReferenceTables + LocalDate referenceDate
// RuleEvaluation: ruleId, eligible, eligibleOn (LocalDate, null se não alcança no horizonte),
//   unmetRequirements (List<Requirement(description, required, current)>),
//   amount (BenefitAmount, null se não elegível), steps (memória), legalBasis
```

`RetirementRuleCatalog` lista as regras; `BenefitSimulator` roda todas, ordena e escolhe a melhor
(maior RMI entre as elegíveis na data; empate → a de requisitos mais folgados; regra documentada).

## 3. Regras a implementar

Nome da classe pelo dispositivo. Os parâmetros (pontos, idades, tempos, pedágios, coeficientes) vêm
**do texto da EC 103/2019 e das leis citadas**, conferidos em D5 — não deste documento.

| `RuleId` | Classe | Dispositivo |
| --- | --- | --- |
| `DIREITO_ADQUIRIDO_TEMPO` | `AcquiredRightContributionTimeRule` | regras anteriores à EC 103, requisitos cumpridos até 13/11/2019 (art. 3º EC 103) |
| `DIREITO_ADQUIRIDO_IDADE` | `AcquiredRightAgeRule` | idem, por idade |
| `TRANSICAO_PONTOS` | `Art15PointsRule` | art. 15 EC 103 |
| `TRANSICAO_IDADE_MINIMA` | `Art16MinimumAgeRule` | art. 16 EC 103 |
| `TRANSICAO_PEDAGIO_50` | `Art17Toll50Rule` | art. 17 EC 103 |
| `TRANSICAO_IDADE` | `Art18AgeRule` | art. 18 EC 103 |
| `TRANSICAO_PEDAGIO_100` | `Art20Toll100Rule` | art. 20 EC 103 |
| `TRANSICAO_ESPECIAL_PONTOS` | `Art21SpecialPointsRule` | art. 21 EC 103 |
| `PERMANENTE_IDADE` | `PermanentAgeRule` | art. 19 EC 103 |
| `PERMANENTE_ESPECIAL` | `PermanentSpecialRule` | art. 19, §1º, I EC 103 |
| `PROFESSOR_*` | regras do professor (pontos, idade, pedágio) | arts. 15 §3º, 16 §2º, 20 §1º EC 103 |
| `DEFICIENCIA_TEMPO`, `DEFICIENCIA_IDADE` | `Lc142ContributionTimeRule`, `Lc142AgeRule` | LC 142/2013 (inalterada pela EC 103) |
| `RURAL_IDADE` | `RuralAgeRule` | Lei 8.213/1991, art. 48 |

Regra não listada (ex.: incapacidade permanente, que depende de perícia) **não entra**.

## 4. RMI — `service/benefit/amount/`

Implemente, com fonte e teste, para cada regra a fórmula que ela manda:

1. **Período básico de cálculo** e **média** dos salários corrigidos (passo 05:
   `correctionFactor`) — a regra geral pós-EC 103 (art. 26) e as regras de direito adquirido
   (que usam outra base e, conforme o caso, o fator previdenciário).
2. **Coeficiente** de cada regra (geral do art. 26 e exceções: pedágio 100%, especial, etc.).
3. **Fator previdenciário** onde a regra o exige — fórmula da Lei 8.213/1991/Lei 9.876/1999 com a
   expectativa de sobrevida do IBGE (passo 05) do ano correto.
4. **Descarte** de contribuições (art. 26, §6º, EC 103): testar a exclusão das menores
   contribuições enquanto o tempo mínimo da regra continuar cumprido, ficando com a maior RMI;
   o tempo descartado **não conta para nenhum outro fim** naquela regra — e a memória mostra quais
   competências saíram.
5. **Piso e teto** na data de início; **arredondamento** com escala e modo citados.
6. `BenefitAmount(average, coefficient, factor, discardedCompetences, monthlyAmount, steps)`.

## 5. Projeção

Para regra ainda não alcançada: avançar **mês a mês** a partir da data de referência, supondo
contribuição contínua com o salário da premissa (`ProjectionAssumptions`: salário mensal futuro —
padrão = último salário corrigido; segue contribuindo: sim/não), até alcançar ou até o horizonte
(constante, ex.: 40 anos, justificada). Determinístico; teste de desempenho simples (todas as
regras de um caso em < 1 s).

## 6. Testes obrigatórios

- Por regra: elegível, não elegível, véspera e dia em que alcança, transição de ano (pontos e
  idades progressivas), homem/mulher, e a memória contendo os passos esperados.
- RMI: média, coeficiente, fator, descarte (caso em que descartar aumenta e caso em que
  diminuiria → não descarta), piso, teto, arredondamento.
- Projeção: data correta para 3 perfis; "não alcança" dentro do horizonte.
- `BenefitCasesTest` (do passo 06) estendido com resultado por regra nos casos fictícios.
- Avaliação real `@Tag("eval-benefit")` (D6): compara regra e RMI com a carta de concessão;
  imprime acerto e diferença em R$. Critério de merge: **todos** os casos reais batem em regra e
  RMI (tolerância só de arredondamento documentado) **ou** a diferença está explicada caso a caso
  no relatório e aceita pela advogada.

## 7. Documentação

`docs/MOTOR_PREVIDENCIARIO.md`: uma seção por regra (requisitos, fórmula, dispositivo, exemplo
numérico), RMI, descarte, projeção, critério de melhor regra.

## 8. Critérios de aceite

- [ ] Todas as regras do §3 com testes e fonte.
- [ ] Casos fictícios verdes; casos reais (D6) batendo ou com diferença explicada.
- [ ] Memória de cálculo legível em cada resultado.
- [ ] Gate e Sonar back verdes; núcleo continua puro.
- [ ] **Merge só com D5 e D6 resolvidas.**

## 9. Commits sugeridos

Um commit por grupo de regras (direito adquirido; transições de tempo; idade e permanentes;
especial; professor; deficiência e rural), depois RMI, descarte, projeção, simulador e docs.

## 10. Pare e pergunte se

Uma regra admitir duas leituras; a carta de concessão divergir do cálculo sem explicação; o
descarte ou o fator exigirem dado que o CNIS não traz.
