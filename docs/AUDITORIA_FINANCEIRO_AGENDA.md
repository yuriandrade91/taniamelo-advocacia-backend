# Auditoria do financeiro e da agenda — pente fino antes do commit

Rodada de 01/10/2026 sobre a árvore de trabalho (último commit: `1d45a93`).
Tudo abaixo foi **verificado**: lendo o código e, na maior parte, executando
contra a API local com token real e SQL logado. Onde a conclusão é opinião e não
medição, está escrito.

Ponto de partida e de chegada: **1016 testes, 0 falhas**, `mvn clean test` verde.
Cobertura de linhas **90,4%** (5652/6251).

---

## Índice por urgência

| # | O que é | Gravidade | Situação | De quem é a decisão |
|---|---|---|---|---|
| 1 | N+1 nas listas consolidadas de pagamentos e receitas | **Alto** | **Corrigido** | Minha — era defeito |
| 2 | `GET /revenues/{id}` só funciona porque o OSIV está ligado | **Alto** | **Corrigido no financeiro** | Minha — era defeito |
| 3 | 34 métodos de leitura sem `@Transactional` no resto da base | **Médio-alto** | Aberto | Sua — é refactor amplo |
| 4 | `/appointments/summary` é subconjunto de `/appointments/timeline` | **Médio** | Aberto | Sua — quebra o front |
| 5 | `PaymentService` e `OfficeRevenueService` sem teste nenhum | **Médio** | **Corrigido (parcial)** | Minha |
| 6 | Tags de financeiro espalhadas no Swagger | **Baixo** | **Corrigido** | Minha |
| 7 | `SupportUserSeeder` sem teste | **Baixo** | Aberto | Sua |
| 8 | A árvore mistura dois trabalhos distintos | **Médio** | Aberto | Sua — como commitar |

---

## 1. N+1 nas listas consolidadas — corrigido

**Evidência (medida, não suposta).** Com `logging.level.org.hibernate.SQL=DEBUG`,
contando statements por requisição:

| rota | antes | depois |
|---|---|---|
| `GET /payments?pageSize=10` | 13 statements, **10** SELECTs em `clients` | 3 statements, 1 |
| `GET /revenues?pageSize=10` | 10 statements, **7** SELECTs em `clients` | 3 statements, 1 |
| `GET /clients/{id}/payments` | 3 statements, 1 | inalterado |
| `GET /expenses?pageSize=10` | 3 statements, 0 | inalterado |

A causa: `client` é `FetchType.LAZY` em `ClientPayment` e `OfficeRevenue`, e a
linha da lista mostra o nome do cliente. Uma consulta por linha. Com
`pageSize=100`, que é o teto de `PageRequests`, seriam 100 idas ao banco para
montar uma tela.

**Não era regressão nossa** — os `toDTO` antigos já liam `getClient()`. A aba do
cliente (`/clients/{id}/payments`) nunca teve o problema: todas as linhas são do
mesmo cliente, que já está no cache da sessão depois do `findClientOrThrow`.

**Correção:** `@EntityGraph(attributePaths = "client")` sobre o
`findAll(Specification, Pageable)` em `PaymentQueryRepository` e
`OfficeRevenueRepository`. Funciona com paginação porque `client` é `ToOne` — o
join não multiplica linhas, então o `LIMIT` continua valendo no banco. Com
coleção seria o caso em que o Hibernate pagina em memória, e aí o remédio seria
pior que a doença.

---

## 2. O `open-in-view` estava escondendo um defeito — corrigido no financeiro

O Spring Boot avisa isso em todo boot da aplicação, e nós vínhamos ignorando:

```
spring.jpa.open-in-view is enabled by default. Therefore, database queries
may be performed during view rendering.
```

**Evidência.** Subi a aplicação com `--spring.jpa.open-in-view=false` e passei
por 30 rotas de leitura. Vinte e nove responderam 200. Uma quebrou:

```
GET /api/v1/revenues/{id}  ->  500
LazyInitializationException (4 ocorrências no log)
```

A causa: `OfficeRevenueService.get()` não era `@Transactional`, e o `toDTO` dele
lê `entity.getClient().getFullName()` — que inicializa o proxy. Fora de
transação e sem OSIV, estoura. **Hoje não quebra em produção porque o OSIV está
ligado** — ele mantém a sessão aberta durante a renderização e engole o
problema. É literalmente o mecanismo que escondeu o N+1 do item 1.

**Correção:** `@Transactional(readOnly = true)` nos 13 métodos de leitura dos
quatro services do financeiro (`OfficeExpenseService`, `OfficeRevenueService`,
`PaymentService`, `ClientPaymentService`). Depois disso, as 8 rotas do financeiro
respondem 200 com o OSIV desligado e zero `LazyInitializationException`.

**Não mexi na configuração do OSIV.** Ele continua ligado. O financeiro é que
agora não depende dele — ver item 3 para o resto.

---

## 3. O resto da base ainda depende do OSIV — aberto, decisão sua

São **34 métodos públicos de leitura** sem `@Transactional` em toda a base.
Todos funcionam hoje só porque o OSIV está ligado:

```
AppointmentService#history            ClientFileService#listDocuments
ClientAddressService#list,get         ClientFileService#getDocument
ClientDisabilityPeriodService#list    ClientFileService#listSimulations
ClientDisabilityPeriodService#get     ClientFileService#getSimulation
ClientDisabilityPeriodService#conversion
ClientInterviewService#list,get       ClientServiceImpl#listSummary
MyProfileService#meusDados            ClientServiceImpl#listDeleted
SupportService#login,openSession      ClientServiceImpl#findById
                                      ClientServiceImpl#historyByClientId
                                      ClientServiceImpl#getPersonalData
                                      ClientServiceImpl#getProfessionalData
```

(Os 13 do financeiro saíram desta lista nesta rodada.)

**Recomendação:** anotar os 34 e então desligar o OSIV
(`spring.jpa.open-in-view: false`). A referência do Spring Boot e a literatura
são unânimes: em produção se desliga, justamente para que N+1 e lazy fora de
transação falhem alto em vez de silenciosamente. Não fiz agora porque são 12
arquivos fora do escopo do que combinamos, e porque desligar o OSIV **antes** de
anotar todos transformaria o item 2 em cinco incidentes iguais.

**Como validar quando fizer:** suba com `--spring.jpa.open-in-view=false` e
percorra as rotas de leitura; o sintoma é 500 com `LazyInitializationException`
no log, não falha de teste — a suíte não pega isso porque os testes de controller
usam serviço mockado.

---

## 4. `/appointments/summary` virou subconjunto de `/appointments/timeline` — decisão sua

O `timeline` que criei hoje devolve, por mês, `scheduledCount` — que é
exatamente o que o `summary` devolve como `count`. A diferença é só o formato:

| | `summary?year=2026` | `timeline?from=…&to=…` |
|---|---|---|
| resposta | `{year, month, count}` | `{month, totalCount, scheduledCount, completedCount, cancelledCount}` |
| meses vazios | omitidos | incluídos, zerados |
| janela | ano fechado | qualquer intervalo |

**Não cortei** porque o `summary` alimenta as abas da agenda no front, e
removê-lo quebra a tela hoje. O corte é de uma linha no controller mais uma
adaptação no front (ler `scheduledCount` e filtrar meses com zero). É o
candidato número um se a verbosidade incomodar.

Dos 88 endpoints, é a única sobreposição real que encontrei. O resto que parece
redundante não é: `/payments` é somente leitura e consolidada (escrita mora em
`/clients/{id}/payments`, e está documentado), e `summary`/`timeline` de despesa
e receita respondem perguntas diferentes (saldo agregado × série mensal).

---

## 5. Cobertura — duas classes estavam sem teste nenhum

Antes desta rodada:

| classe | antes | depois |
|---|---|---|
| `OfficeRevenueService` | **5,0%** | 31,7% |
| `PaymentService` | **14,3%** | 28,6% |
| `FinanceSpecifications` | 59,0% | 59,0% |

As duas primeiras não tinham **nenhum** arquivo de teste, justamente enquanto a
projeção delas mudava. Criei `OfficeRevenueServiceTest` (4 casos) e
`PaymentServiceTest` (5 casos), cobrindo o caminho da lista: projeção dos campos,
cliente nulo, ausência dos campos de detalhe e o `Pageable` sem ordenação.

O número continua baixo porque `create`/`update`/`delete`/`summary`/`timeline`
dessas classes seguem sem teste — lacuna **anterior** ao nosso trabalho, não
coberta aqui para não inflar o diff.

`FinanceSpecifications` em 59% é a busca textual (`textoEm`/`caminho`, com join
para `client.fullName`) sem exercício. Também anterior.

**O que a suíte passou a travar:** `FinanceListProjectionTest` fixa o conjunto
**exato** de campos das três grades. Campo novo quebra o teste de propósito —
alargar a lista tem de ser decisão, não acidente.

---

## 6. Swagger — revisado por inteiro

- **88/88 endpoints com `@Operation`.** Varri os controllers; não há rota sem
  descrição.
- **Tags agrupadas.** As três tags de `Financeiro` estavam fora da lista fixa do
  `OpenApiConfig` e caíam no fim por ordem de descoberta, depois de "Usuários" —
  três tags do mesmo assunto espalhadas pela página. Agora a ordem é
  `… → Cliente - Financeiro → pagamentos aos clientes → despesas → receitas →
  Agenda → Usuários`, travada por teste de adjacência.
- **Atenção ao em-dash.** `"Financeiro — pagamentos aos clientes"` usa `—`
  (U+2014), enquanto `"Cliente - Financeiro"` usa hífen comum. A lista casa por
  string: trocar o caractere faz a tag cair silenciosamente no fim.
- As descrições das listas de despesa, receita e pagamentos agora dizem a ordem
  (vencido → a vencer → cancelado → pago) e apontam o `{id}` para o detalhe.

---

## 7. Queries e relações — revisão

**Índices.** As tabelas do financeiro e da agenda têm partial indexes
(`WHERE deleted_at IS NULL`) nas colunas que os filtros usam: `due_date`,
`paid_date`, `client_id`, `start_at`, `status`. Cobrem bem o que as listas
pedem.

**Uma ressalva honesta:** a ordenação nova lidera com um `CASE` sobre o status.
Nenhum índice simples satisfaz `ORDER BY CASE(...), due_date, created_at`, então
o Postgres ordena em memória. No volume atual (centenas de linhas) é
irrelevante. Se um dia incomodar, o remédio é um índice de expressão sobre o
mesmo `CASE` — não é preciso mudar o código.

**Relações.** `ClientPayment.client` e `OfficeRevenue.client`/`sourcePayment` são
`ManyToOne LAZY`, o que está certo; o cuidado é o do item 1. `Appointment` não
tem relação mapeada para cliente — guarda `client_id` solto e resolve nomes em
lote (`clientNamesFor`), que é o que evita N+1 lá.

**Soft delete.** `ClientPayment` não tem `@SQLRestriction`; o filtro de excluídos
é explícito em cada consulta (`activeOnly()` no `PaymentSpecs`, `SOMENTE_ATIVOS`
no resumo). É decisão registrada no código e vale conferir em toda consulta nova
— é o tipo de coisa que passa batido.

---

## 8. Avaliação das decisões de hoje — e o que a literatura diz

Pesquisei para confirmar se o caminho está certo. Resumo:

**Ordenação dentro da `Specification`.** Vlad Mihalcea documenta exatamente a
técnica que usamos (`query.orderBy(...)` dentro do `toPredicate`). O artigo dele
**não** cobre três armadilhas que tratamos: que o `Sort` do `Pageable` substitui
a ordem da specification (por isso o `Pageable` vai sem ordenação, e há teste
para isso), que a consulta de contagem passa pelo mesmo `toPredicate` (por isso o
guard `isCountQuery`), e o retorno `null` como "sem predicado". Nossa
implementação está mais defensiva que a referência publicada.

**`@EntityGraph` com `Specification` e `Pageable`.** É a forma recomendada, e é
explicitamente apontada como mais segura que `JOIN FETCH` para paginação —
`JOIN FETCH` em coleção vira produto cartesiano e força paginação em memória.
Como o nosso é `ToOne`, estamos no caso seguro.

**DTO de lista separado do DTO de detalhe.** É a prática recomendada contra
over-fetching: resposta de coleção leva o mínimo, detalhe leva o completo. É o
que fizemos nas três grades.

**`open-in-view`.** A recomendação para produção é desligar, exatamente porque
ele esconde N+1 e lazy fora de transação. Confirmado na prática aqui (item 2).

Fontes:
- [The best way to use the Spring Data JPA Specification — Vlad Mihalcea](https://vladmihalcea.com/spring-data-jpa-specification/)
- [Preventing N+1 SELECT problem using Spring Data JPA EntityGraph](https://tech.asimio.net/2020/11/06/Preventing-N-plus-1-select-problem-using-Spring-Data-JPA-EntityGraph.html)
- [Dynamic Entity Graphs in Spring Data JPA — JPA Buddy](https://jpa-buddy.com/blog/dynamic-entity-graphs-in-spring-data-jpa/)
- [Don't expose your JPA entities in your REST API — Thorben Janssen](https://thorben-janssen.com/dont-expose-entities-in-api/)
- [Open Session in View Is Convenient — Until It Hides Your Lazy Loading Problem](https://dev.to/thellu/open-session-in-view-is-convenient-until-it-hides-your-lazy-loading-problem-20i2)
- [API design best practices — Microsoft Azure Architecture Center](https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design)

---

## 9. O que está limpo

Verificado e sem achado:

- **Imports não usados:** 0 em toda a base (o spotless cuida).
- **Métodos privados sem chamada:** 0.
- **Tipos órfãos:** só `SupportUserSeeder`, e é falso positivo (bean do Spring).
  Mas ele **não tem teste** — item 7 do índice.
- **Testes quebrados:** nenhum. 1016 passando.
- **Divergência entre DTO e tabela:** conferi `appointment_history` coluna a
  coluna contra a entidade; casam.

---

## 10. Antes de commitar: a árvore mistura dois trabalhos

`git status` tem **62 arquivos** em `src/`, e eles são de duas frentes:

1. **O refactor anterior** (não commitado antes de hoje): renomeação
   PT→EN (`DueDateRules`, `FinanceQueries`, `PaymentSpecs`, `PeriodBasis`,
   `RequestEnums`, `OfficeClock`, `AuditIntent`), o trabalho de suporte
   multi-tenant e a migration `V19__role_lawyer_to_support.sql`.
2. **O trabalho de hoje**: ordenação por status, projeções de lista, timeline da
   agenda, entity graphs, `@Transactional` do financeiro e ordem das tags.

Commitar tudo junto faz um commit que ninguém consegue revisar nem reverter em
partes. **Sugestão:** dois ou três commits separados por frente. Posso preparar
isso quando você disser.

Um detalhe para não esquecer: a migration `V19` está **não rastreada** no git mas
**já aplicada** nos dois schemas (`tenant_tania` e `tenant_demo` estão na V21).
Se ela não entrar no commit, um ambiente novo sobe sem ela e diverge.

---

## 11. Ambiente — duas pendências que não são de código

**O JDT corrompeu `target/classes` três vezes nesta sessão.** Sintomas vistos:
classes com `java.lang.Error: Unresolved compilation problem` (assinatura do
compilador do Eclipse, que o `javac` nunca produz), `cannot access` em dez
classes sem relação, e um `NoClassDefFoundError` numa classe local de teste. Nas
três, `mvn clean` resolveu.

O conserto é tirar um dos dois da disputa pelo diretório:

```jsonc
// .vscode/settings.json
"java.autobuild.enabled": false
```

E `Java: Clean Java Language Server Workspace` segue pendente desde ontem.

**Builds concorrentes do Maven também corrompem.** O `NoClassDefFoundError` acima
fui eu que causei, rodando `spotless:apply` e outro `mvn test` em paralelo com um
`mvn clean test` em background. Um Maven por vez no mesmo `target/`.
