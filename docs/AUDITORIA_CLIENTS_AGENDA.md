# Auditoria de clients e appointments — o que falta, o que está estranho

Rodada de 13/09/2026 sobre `88a79de`. Tudo abaixo foi **verificado**: lendo o
código e, onde dava, executando contra a API rodando localmente. Cada item traz
a evidência. Onde não consegui confirmar intenção, está escrito.

Ponto de partida: `mvn verify` verde, **903 testes, 0 falhas**. Nada aqui é
regressão — é o que a suíte não pergunta.

O documento está dividido pelo que você pediu: primeiro o que **depende de
decisão sua**, depois o que é **implementação faltando**, depois as
**inconsistências** que não machucam hoje mas cobram juros.

---

## Índice por urgência

| # | O que é | Gravidade | De quem é a decisão |
|---|---|---|---|
| 1 | Token de um escritório lê (e escreve) no outro | **Crítico** | Minha — é defeito |
| 2 | Dois clientes ativos com o mesmo CPF | **Alto** | Sua — o que é "mesmo CPF" |
| 3 | Tempo de contribuição negativo / zero inventado | **Alto** | Sua — o que fazer com texto que não parseia |
| 4 | Atendente marca parcela como paga | **Alto** | Sua — quem mexe em dinheiro |
| 5 | Agenda erra o mês na virada (fuso) | **Médio-alto** | Sua — qual fuso manda |
| 6 | Exclusão lógica não se distingue de edição na trilha | **Médio-alto** | Minha — é defeito |
| 7 | Não há como listar o que foi excluído, então não há como restaurar | **Médio** | Sua — a feature existe pela metade |
| 8 | Concluir compromisso: sem trilha, sem guarda, reversível | **Médio** | Sua — regras do ciclo de vida |
| 9 | Campo grande demais vira 409 sem dizer o campo | **Médio** | Minha — é defeito |
| 10 | Idade negativa aceita | **Baixo-médio** | Minha — é defeito |
| 11 | Dois nomes para o mesmo benefício no enum | **Médio** | Sua — qual vale |
| 12 | Papéis: ninguém consegue criar um LAWYER ou STAFF | **Médio** | Sua — como entram os usuários |

---

# Parte I — Decisões que dependem de você

## D1. O que é "o mesmo CPF"? *(hoje dá para cadastrar a mesma pessoa duas vezes)*

**Verificado, rodando:** criei dois clientes, ambos aceitos, ambos ativos:

```
CPF "39053344705"    -> 201, clientId 7e4e8fc3-…
CPF "390.533.447-05" -> 201, clientId 5e09799d-…
```

É a mesma pessoa. O validador de CPF tira a pontuação antes de validar
(`validation/CpfValidator.java:11`), mas a checagem de duplicidade compara a
**string crua** (`service/ClientServiceImpl.java:392`, `existsByCpf(value.trim())`)
e o índice único também é sobre a coluna crua
(`V12__clients_soft_delete.sql:24`). A busca, por outro lado, já compara por
dígitos (`repository/ClientSpecification.java:58-66`) — ou seja, **o sistema já
sabe que os dois formatos existem na leitura, e ignora isso na escrita.**

**A decisão:** identidade do cliente é o número (dígitos) ou o texto digitado?
Se for o número — e acho difícil argumentar o contrário num escritório
previdenciário —, o CPF precisa ser normalizado antes de gravar, e isso implica
uma migration para consertar o que já está no banco.

**Enquanto não decidir:** duas fichas para a mesma pessoa, com históricos
separados, e nenhuma das duas errada do ponto de vista do sistema.

---

## D2. Tempo de contribuição que não parseia: erro, ou zero?

**Verificado, rodando:**

```
"300000000 anos" -> 201, contributionInMonths = -694967296
"nao informado"  -> 201, contributionInMonths = 0
```

O primeiro é estouro de `int` (`util/ContributionTimeParser.java:48`,
`anos * 12`). O segundo é a regra atual: texto que não casa com o padrão vira
**0** (`:48`), e 0 é indistinguível de "esta pessoa não contribuiu".

Num escritório previdenciário esse número é a base do cálculo. Um negativo pelo
menos grita; o **zero é o perigoso**, porque é plausível.

**A decisão:** texto livre que não parseia deve (a) recusar o cadastro com 400,
(b) gravar `null` e a tela mostra "não calculado", ou (c) continuar 0? Eu
recomendo (b) — mas quem responde pelo cálculo é o escritório.

---

## D3. Quem pode mexer em dinheiro?

**Verificado, rodando, com o usuário `atendente` (STAFF):**

| Ação | Resultado |
|---|---|
| ler ficha completa | 200 permitido |
| ler dados pessoais (CPF, RG, nome da mãe) | 200 permitido |
| ler dados profissionais (NIT, CTPS) | 200 permitido |
| ler a senha do INSS | **403 recusado** |
| ler o financeiro do cliente | 200 permitido |
| **marcar parcela como PAGA** | **200 permitido** |
| excluir a parcela | **403 recusado** |
| excluir o cliente | **403 recusado** |

A assimetria é esta: o atendente **não pode excluir** uma parcela — que é
exclusão lógica, reversível — mas **pode declará-la paga**, que é a mudança com
significado financeiro e que ninguém desfaz sem perceber.

Quando fizemos o corte "STAFF opera, só ADMIN/LAWYER destrói", pensamos em
exclusão. Dinheiro não entrou na conversa.

**A decisão:** lançar e quitar parcela é operação de atendente ou de advogado?
(Endpoints em `controller/ClientPaymentController.java:31,42,54,65` — nenhum
tem `@RequerAdvogado`.)

Vale decidir junto: **ler dados pessoais e profissionais está aberto para
qualquer autenticado**. O único dado que exige papel hoje é a senha do INSS. Se
a régua era "dado sensível exige advogado", CPF/RG/NIT ficaram do lado de fora.

---

## D4. Qual fuso define "agosto" na agenda?

**Verificado, rodando.** Compromisso em **31/01/2028 às 23h de Brasília**
(= `2028-02-01T02:00Z`):

```
aparece no filtro de JANEIRO?   False
aparece no filtro de FEVEREIRO? True
resumo anual: {"2": ...}
```

O agrupamento é em UTC (`service/AppointmentService.java:183`,
`atZone(ZoneOffset.UTC)`). Para um escritório em UTC−3, **todo compromisso
marcado entre 21h e meia-noite cai no mês seguinte** — erro recorrente em toda
virada de mês, e do tipo que ninguém reporta como bug, só acha a agenda
estranha.

**A decisão:** fixar `America/Sao_Paulo` como fuso do negócio, ou deixar o fuso
vir do escritório (útil se um dia houver cliente em outro estado/país)? A
segunda é mais trabalho e mais correta a longo prazo.

---

## D5. Ciclo de vida do compromisso — três regras que não existem

**Verificado, rodando,** no mesmo compromisso:

```
concluir 1ª vez        -> 200  status "Concluído"
concluir 2ª vez        -> 200  status "Concluído"
cancelar o concluído   -> 200  status "Cancelado"
trilha final           -> ['CANCELLED']
```

Três coisas de uma vez:

1. **Concluir não é idempotente nem guardado** — concluir duas vezes passa
   calado (`AppointmentService.java:278-283` só barra `CANCELADO`), enquanto
   cancelar duas vezes é recusado (`:262-265`). Duas transições irmãs, duas
   posturas.
2. **Um compromisso concluído pode ser cancelado.** Uma audiência que já
   aconteceu vira "Cancelado" e some da contagem.
3. **Concluir não grava nada na trilha.** Não existe `COMPLETED` em
   `model/AppointmentAction.java:10-19`. Repare no resultado acima: a trilha diz
   que o compromisso foi cancelado e **nunca menciona que ele foi concluído duas
   vezes antes disso**. Para uma tabela que existe para auditar, é um buraco.

Some-se: não há caminho de volta. `CANCELADO` e `CONCLUIDO` são definitivos —
`restore` só limpa `deleted_at` (`:310-323`), não mexe em status. Um clique
errado em "Concluir" não tem desfazer.

**As decisões:** (a) concluir antes da hora, pode? (b) cancelar o que já foi
concluído, pode? (c) existe "reabrir"? Se a resposta de (c) for não, tudo bem —
mas aí a tela precisa confirmar antes, porque é irreversível.

---

## D6. O funil de situação tem ordem?

**Verificado:** criei um cliente já em **"Benefício concluído"** → 201.

`Situation` descreve seis etapas de um funil
(`model/Situation.java:9-14`), mas nada impõe ordem: `PATCH` e `PUT` aceitam
qualquer valor, em qualquer direção, e o `POST` aceita qualquer um como inicial.

**A decisão:** o funil é monotônico? Existe estado terminal? Se for só um rótulo
livre — o que é uma resposta legítima —, vale escrever isso, porque hoje o
código *parece* um funil e não é.

---

## D7. Dois nomes para o mesmo benefício

`model/BenefitType.java`:

```
:11  APOSENTADORIA_POR_INCAPACIDADE_PERMANENTE
:15  APOSENTADORIA_POR_INVALIDEZ
:13  APOSENTADORIA_POR_DEFICIENCIA
:17  APOSENTADORIA_PCD
```

"Invalidez" virou "incapacidade permanente" na reforma de 2019; PCD e
"deficiência" são o mesmo. Os quatro são aceitos e indexados
(`V2__clients.sql:63`), então **o mesmo benefício se divide em dois valores** e
qualquer relatório agrupado por `benefit` conta metade em cada um.

**A decisão:** qual nome vale? Os outros viram alias de entrada (aceita, grava o
canônico) ou somem com migration?

---

## D8. Como entram os usuários do escritório?

Não existe endpoint de criação de usuário — `UserController` só tem `GET`
(`controller/UserController.java:66`). O único lugar que escreve papel é o
seeder, e ele sempre escreve `ADMIN`
(`config/AdminUserSeeder.java:65-70`).

Consequência: **todo usuário que o sistema sabe criar é ADMIN**, e
`@RequerAdvogado` nunca recusa ninguém em instalação limpa. O `atendente` que usei
nos testes existe porque eu o inseri no banco à mão.

Isto não invalida o trabalho de autorização — ele funciona, como a tabela do D3
mostra. Mas ele só passa a valer quando existirem usuários não-ADMIN, e hoje não
há caminho para criar um.

**A decisão:** cadastro de usuário entra no produto (tela + endpoint, com papel),
ou continua sendo SQL na mão? Se for a segunda, vale documentar — senão alguém
vai concluir que a autorização não funciona.

---

# Parte II — Defeitos (não precisam de decisão, precisam de conserto)

## B1. 🔴 Token de um escritório lê e escreve no outro

**O mais grave da rodada. Verificado, rodando.**

```
token emitido para demo + header X-Tenant-Id: tania
  GET  /clients            -> 200, devolveu o cliente de tania
  POST /appointments       -> 201, criou compromisso DENTRO de tania
  GET  /clients/{id}/inss-password -> 200, valor "senha-secreta"
```

**Causa.** `tenant/TenantResolutionFilter.java:111-115` resolve o escritório
pelo **header primeiro** e só cai no claim do token se o header não resolver. E
`security/JwtAuthenticationFilter.java:42-52` nunca compara o claim `tenant` do
token com o `TenantContext` — valida só a assinatura e a expiração
(`security/JwtService.java:62-65`) e carrega o usuário **pelo e-mail, no schema
que o header escolheu** (`security/CustomUserDetailsService.java:25-29`).

**Pré-condição — e por que ela não salva.** O vazamento só funciona se o mesmo
login existir nos dois escritórios. Confirmei que um usuário exclusivo de um
schema é barrado:

```
staff@ (só existe em demo) + header tania -> 401 UNAUTHENTICATED
```

Só que `AdminUserSeeder` semeia **o mesmo `app.admin.email`, com a mesma senha,
em todos os schemas** de `app.tenancy.schemas`
(`AdminUserSeeder.java:51`, `application.yaml:99`). Ou seja: existe, por
construção, uma conta presente em todos os escritórios — e para ela o isolamento
não vale.

**Por que a suíte não pegou.** O teste de isolamento compara escritórios usando
**tokens diferentes** (cliente de um é 404 no outro). Ninguém testou token de um
com header do outro. É o vetor que falta.

**Conserto:** o filtro de JWT precisa recusar quando o claim `tenant` do token
não bate com o escritório resolvido. Mais um teste de contrato com esse vetor.

---

## B2. 🔴 Exclusão lógica é indistinguível de edição na trilha

**Verificado, no banco:**

```
 entity_name   | action | count
---------------+--------+-------
 Appointment   | UPDATE |   405
 Client        | UPDATE |   786
 ClientAddress | DELETE |    13     <- único DELETE do sistema
 ClientPayment | UPDATE |    51
```

`DELETE` só aparece para `ClientAddress`, porque é a única entidade com exclusão
física (`service/ClientAddressService.java:117`). Todas as outras "excluem"
gravando `deleted_at` via `save()`, e o listener registra isso como **`UPDATE`**
(`audit/AuditLogListener.java:66-69`).

Isso derruba o motivo pelo qual a tabela foi criada — está escrito na própria
migration (`V8__audit_log.sql:80-84`): *"sem essa trilha à parte, não sobra
nenhum registro de quem removeu o quê"*. Hoje ela não responde isso para
cliente, compromisso, parcela, arquivo nem entrevista.

Agrava: `detail` é sempre `null` nas linhas do listener
(`AuditLogListener.java:97`), então não dá nem para diferenciar pelo texto. E
nada lê `audit_log` em produção — não há endpoint; a única consulta existente é
usada só por teste (`audit/AuditLogRepository.java:10`).

---

## B3. Campo grande demais devolve 409 sem dizer qual campo

**Verificado, rodando:** `POST /appointments` com título de 300 caracteres →
**HTTP 409 `DATABASE_INTEGRITY_ERROR`**, sem `field`.

Deveria ser 400 apontando `title`. A causa é que **não existe um único `@Size`
nos DTOs de clients/appointments** — o limite só existe no Postgres
(`VARCHAR(255)` em `V11__appointments.sql:9`), e a violação cai no ramo genérico
do handler (`exception/GlobalExceptionHandler.java:304-312`).

Vale para todos os campos com tamanho: `cpf(14)`, `mobile_phone(20)`, `rg(20)`,
`profession(100)`, `nit_pis(20)`, `ctps(30)`, `location(255)`,
`meeting_url(500)`, `client_name(255)`.

---

## B4. Idade negativa

**Verificado, rodando:** cliente com `birthDate: 2090-01-01` → **201**, e a API
devolve `age: -63`. Falta `@Past` em `ClientCreateRequestDTO.java:23` e no DTO de
dados pessoais.

---

## B5. Unicidade só é checada na criação

`validateUniqueness` é chamado uma vez, em `create`
(`service/ClientServiceImpl.java:70`). Não é chamado em `update` (`:113`), nem
em `updatePersonalData` (`:266`), nem em `updateProfessionalData` (`:306`) — e
os três escrevem `cpf`, `nitPis` ou `beneficiaryNumber`.

Efeito: CPF duplicado no cadastro dá **400 com o campo apontado**; o mesmo CPF
duplicado numa edição dá **409 genérico**, porque só o banco barra. Mesma regra,
duas respostas.

---

## B6. Restaurar um cliente pode ser impossível, e só se descobre na hora

Excluir o cliente A (CPF X) → cadastrar B com o CPF X (permitido, o índice é
parcial) → restaurar A → **409**, sem nada que identifique o conflito
(`ClientServiceImpl.java:205-216`). A exclusão não avisa que a volta pode não
existir.

---

## B7. Não há como achar o que foi excluído

**Verificado:** `?includeDeleted=true` e `?deleted=true` são **ignorados
silenciosamente** (parâmetro desconhecido não dá erro nem muda o resultado).
`deletedAt` não aparece em nenhum DTO de resposta e nenhum endpoint lista
excluídos.

`PATCH /{id}/restore` existe para cliente e compromisso — mas só é utilizável
por quem anotou o UUID antes de excluir. Na prática, **a funcionalidade de
desfazer não é alcançável pela API**.

---

## B8. Autorização não cobre mudança destrutiva que não é DELETE

`SecuredEndpointsContractTest` garante que todo `@DeleteMapping` novo nasça com
`@RequerAdvogado` — e cumpre. Mas a garantia é sobre o **verbo**, não sobre o
**estrago**: `PATCH /payments/{id}` marcando pago, `PATCH /appointments/{id}/cancel`
e `PUT /clients/{id}` (que sobrescreve campos) ficam de fora da varredura.

---

## B9. Comentários que afirmam o contrário do código

- `security/SecurityConfig.java:19-20`: *"Autorização por papel fica para uma
  próxima fase — hoje qualquer usuário autenticado acessa os endpoints de
  negócio."* Contradito pela linha **40 do mesmo arquivo** (`@EnableMethodSecurity`)
  e pelos dez endpoints anotados. Mesma afirmação velha em
  `UserController.java:32` e `TenantController.java:27`.
- `ClientController.java:150-152`: `GET /clients/{clientId}` documentado como
  *"incluindo a senha do INSS (descriptografada)"*. Não inclui mais — e o mesmo
  arquivo diz isso na linha 281. Quem ler o Swagger constrói a tela errada.
- `ClientProfessionalDataController.java:22,37,76`: promete *"senha do INSS"* na
  resposta. O DTO não tem o campo. É pior que doc desatualizada: anuncia que um
  endpoint **aberto** devolve o segredo.
- `ClientServiceImpl.java:227-229`: diz gravar a auditoria *"em transação
  própria, como o AuditLogListener"*. Não grava — é `@Transactional` comum
  (`:230`), sem `REQUIRES_NEW`. Se a transação voltar atrás, o registro de quem
  leu a senha volta junto.

---

## B10. Cabeçalhos das migrations estão todos deslocados em um

`V2__clients.sql:1` diz `-- V3`. `V3` diz `-- V4`. `V5`→`V6`, `V6`→`V7`,
`V7`→`V8`, `V8`→`V9`. As referências cruzadas dentro dos comentários seguem a
numeração errada. Quem rastrear uma decisão de schema pelo comentário cai um
arquivo ao lado.

---

# Parte III — Inconsistências que cobram juros depois

Nenhuma quebra nada hoje. Todas fazem o próximo endpoint nascer torto.

| O que | Onde | Por que incomoda |
|---|---|---|
| `DELETE /clients` devolve 200 + corpo; os outros seis deletes devolvem 204 | `ClientController.java:266` vs `AppointmentController.java:180` e os 4 sub-recursos | Um cliente de API precisa tratar dois contratos para a mesma operação |
| `restore` devolve o recurso na agenda e só uma mensagem em clientes | `AppointmentController.java:195` vs `ClientController.java:307` | Mesmo verbo, mesmo sufixo, payloads diferentes |
| Sub-recursos divididos entre PUT e PATCH sem razão estrutural | `ClientPaymentController.java:65` (PATCH) vs `ClientAddressController.java:101` e `ClientInterviewController.java:65` (PUT) | — |
| Path variable `{clientId}` em clientes, `{id}` em compromissos | `ClientController` vs `AppointmentController` | — |
| `PATCH /clients` tipa situação/benefício/tipo como `String`; o `PUT` tipa como enum | `ClientPatchRequestDTO.java:11-13` vs `ClientUpdateRequestDTO.java:52-84` | O Swagger publica valores permitidos num e texto livre no outro |
| `AppointmentResponseDTO` devolve `String` onde o request usa enum | `AppointmentResponseDTO.java:11-18` | O POST documenta os valores; a resposta do mesmo POST não |
| Trilha da agenda devolve `"CANCELLED"`; a de clientes devolve label PT-BR | `AppointmentService.java:508` vs `ClientServiceImpl.java:374` | Dois endpoints análogos, duas convenções |
| `Role` serializa como constante crua, sem `@JsonValue` | `model/Role.java:30` | Único enum de resposta fora do padrão |
| `Appointment` não tem `@SQLRestriction`; `Client` tem | `Appointment.java:26` vs `Client.java:37` | O filtro de excluídos é repetido à mão em 5 lugares; a próxima query nasce sem ele |
| `description` e `cancellation_reason` são `TEXT` no banco, `String` sem `columnDefinition` na entidade | `Appointment.java:58,64` | Fora da convenção do próprio projeto (`Client.java:152`) |
| `nationality` é `NOT NULL` no banco e nullable na entidade | `Client.java:96` vs `V2:29` | `ddl-auto: validate` não checa nulidade; passa despercebido |
| `PATCH /clients` com corpo vazio devolve 200 "Nenhuma alteração realizada!" | `ClientController.java:343` | Não dá para distinguir "não mudou" de "mandei o campo errado" |
| Código morto | `BusinessErrorCode.BUSINESS_RULE_VIOLATION`, `ValidationErrorCode.INVALID_CPF/EMAIL/PHONE`, `AuditLog.getDetail()` | Nenhuma referência em produção |

### Fora do escopo, mas achei e vale saber

- **CORS com `*` e `allowCredentials(true)` por padrão** (`config/CorsConfig.java:47-56`,
  `application.yaml:117`). O código contorna a proteção do Spring usando
  `allowedOriginPatterns`. Em produção depende inteiramente de alguém lembrar de
  `APP_CORS_ALLOWED_ORIGINS`. Já está no `ROADMAP.md` como Fase A — só
  confirmando que continua assim.
- **`application.yaml:5-8` fixa usuário e senha do banco no arquivo**, sem
  `${...}`, diferente de todos os outros segredos do mesmo arquivo.

---

# O que eu faria, nesta ordem

1. **B1, hoje.** É o único item que muda o que uma pessoa mal-intencionada (ou
   um header errado num script) consegue alcançar. Conserto pequeno: comparar o
   claim do token com o escritório resolvido, e um teste de contrato com esse
   vetor.
2. **D1 e D2**, porque são dados errados entrando no banco agora, e cada dia que
   passa é mais linha para consertar depois.
3. **B3, B4, B5** — são três tardes, e tiram do caminho a classe inteira de
   "erro do usuário que vira 409 ou 500".
4. **D3 e D4** dependem de você e mudam comportamento visível; melhor decidir
   antes de mexer no frontend de novo.
5. **B2 e B7** juntos: a trilha e o restore são a mesma conversa — "o que
   aconteceu com este registro e como volto atrás".
6. O resto da Parte III, quando encostar em cada arquivo.

---

## Como isto foi verificado

- `mvn -o verify` no `88a79de`: 903 testes, 0 falhas.
- Aplicação subida contra Postgres 17.5 local, escritórios `demo` e `tania`.
- Sondas por HTTP para cada item marcado "verificado, rodando"; consultas SQL
  diretas para a trilha de auditoria.
- Os itens sem "verificado, rodando" foram confirmados **lendo** o código, com
  arquivo e linha — não por inferência.
