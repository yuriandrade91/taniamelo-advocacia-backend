# Plano de testes — backend

> Documento de trabalho. Define **em que ordem** cobrir o backend com testes,
> **o que** cada camada afirma, e o **critério de saída** de cada fase.
> O frontend é assunto de um plano separado, depois deste.

---

## 0. Onde estamos — o diagnóstico

Levantamento de 06/09/2026, sobre o código como está hoje.

### 0.1 A suite existe e não protege ninguém

São **57 arquivos de teste e ~640 `@Test`**. A cobertura de unidade é
genuinamente boa: `AppointmentServiceTest` (41), `ClientServiceImplTest` (40),
`ClientFileServiceTest` (37), mais converters, tenant, security e storage.

E o pipeline roda assim:

```yaml
- name: mvn clean verify (sem testes)
  run: mvn -B clean verify -DskipTests
```

O próprio `ci.yml` explica a razão, e ela é honesta:

> Testes pulados por ora: são `@SpringBootTest` e exigem um Postgres real pra
> subir o contexto, que o runner não tem.

São **três** arquivos nessa situação — `ClientControllerTest`,
`LawFirmApplicationTests`, `AuditLogListenerTest` — e por causa deles os 640
foram desligados. Não existe `src/test/resources/`, então esses três usam o
`application.yaml` de desenvolvimento e escrevem em
`jdbc:postgresql://localhost:5432/system`: o banco de trabalho de quem roda.

**Consequência prática:** hoje um PR pode quebrar qualquer regra de negócio
coberta por aqueles 640 testes e o CI passa verde. Toda cobertura nova que
escrevermos herda esse destino até isso mudar — é por isso que a Fase 0 vem
antes de tudo.

### 0.2 As camadas, hoje

| Camada | Estado | Observação |
|---|---|---|
| 1 — Unidade | **Forte** | Services, converters, validators, security, storage |
| 2 — Integração | **Quase ausente** | 3 `@SpringBootTest` acoplados a um Postgres local |
| 3 — E2E de API | **Inexistente** | Sem Rest-assured; há uma collection Postman de agenda |

Sem Testcontainers e sem Rest-assured no `pom.xml`. O H2 está declarado como
`runtime`.

> **H2 não serve para este projeto.** O isolamento entre escritórios é feito por
> **schema do Postgres**, via `search_path` no
> `SchemaMultiTenantConnectionProvider`. O H2 não reproduz esse comportamento:
> um teste que passa nele não afirma nada sobre o mecanismo que realmente
> protege os dados. Camada 2 roda em Postgres de verdade, via Testcontainers.

### 0.3 O que os 640 testes não afirmam

Estas são as lacunas por risco, não por cobertura de linha:

1. **Isolamento entre tenants por HTTP.** `TenantContextTest`,
   `SchemaCurrentTenantResolverTest` e `TenantResolutionFilterTest` cobrem as
   peças isoladas. Nada afirma *"uma requisição autenticada do escritório A não
   enxerga dado do escritório B"*. É o defeito mais caro que este sistema pode
   ter e é o único sem teste de ponta a ponta.
2. **`totalRecords` sob filtro.** É comportamento de banco, não de código. A
   home e a modal de formulários pendentes dependem dele para não mentir a
   contagem.
3. **Flyway por schema.** `MultiTenantFlywayMigratorTest` tem 3 testes; a
   migração real de um schema novo num Postgres não é exercida.
4. **Autorização por rota.** "Sem token → 401" e "token de outro tenant → 403/404"
   não estão afirmados em lugar nenhum.
5. **Contrato de enum com o frontend.** O backend serializa o **rótulo**
   (`"Aposentadoria por idade"`); o frontend indexa pela **chave**
   (`APOSENTADORIA_POR_IDADE`). Quando divergem, o sintoma é campo em branco na
   tela — nunca erro, nunca log.

---

## 1. As camadas — o que cada uma afirma

A divisão proposta (unidade / integração / E2E) está correta. O que muda é a
ênfase e uma camada a mais.

### Camada 1 — Unidade
**Já existe e está boa.** Regra de negócio pura, sem Spring, sem banco. Mocks
onde há colaborador. Continua sendo a maioria dos testes.
*Só acrescentamos onde o levantamento apontar buraco.*

### Camada 2 — Integração (Postgres real, Testcontainers)
**É onde está o maior retorno deste plano.** Sobe o contexto do Spring contra um
Postgres em container, com as migrations aplicadas pelo
`MultiTenantFlywayMigrator`. Afirma o que só é verdade com banco:
multi-tenancy, `Specification`, paginação, constraints, soft delete, conversores
de enum na ida e na volta.

### Camada 3 — E2E de API (Rest-assured)
**Poucos, e por fluxo de negócio.** `webEnvironment = RANDOM_PORT` + o mesmo
container da camada 2, exercitando o caminho completo — login, token, rota,
resposta. Rest-assured é a escolha certa aqui: a API é REST/JSON e o assert de
corpo fica legível.

Regra para não inflar: **um E2E por fluxo que o escritório executa de verdade.**
Se um caso pode ser afirmado na camada 2, ele pertence à camada 2.

### Camada 4 — Contrato (nova)
Não estava na proposta e, neste projeto, vale mais que boa parte da camada 3.

Um teste que exporta os enums do backend (chave + rótulo) para um JSON versionado
e falha quando o conjunto muda. O frontend consome o mesmo arquivo. Custa pouco e
elimina a classe inteira de defeito descrita em 0.3.5 — que já apareceu duas
vezes esta semana.

---

## 2. Cronograma

**Ordem escolhida: por uso real do escritório.** Clientes e agenda primeiro,
porque é o que se usa todo dia; infraestrutura depois.

> **Uma ressalva sobre essa ordem, e como ela é resolvida.** O risco mais caro
> (isolamento entre tenants) está na infraestrutura, que ficou por último. Mas
> *toda requisição de cliente é uma requisição de tenant*: as asserções de
> isolamento entram já na **Fase 1**, junto de Clientes, em vez de esperar a
> Fase 6. A Fase 6 fica com o que sobra — rotação de token, expiração, registro
> de tenant novo.

### Fase 0 — Destravar o CI *(pré-requisito, bloqueia todas as demais)* — ✅ CONCLUÍDA

Nenhum teste novo. O objetivo era fazer os que já existem passarem a valer.

**Resultado:** `mvn verify` executa a suíte inteira, verde, com um Postgres 16 em
container. O que ficou diferente do plano abaixo, e por quê:

- **Sem `@Testcontainers`/`@Container`/`@DynamicPropertySource`.** O Spring Boot 4
  resolve isso com `@ServiceConnection` sobre um bean do container
  (`PostgresContainerConfig`): o datasource aponta para o container sem nenhuma
  propriedade escrita à mão. Menos código e sem risco de a URL e o container
  saírem de sincronia.
- **Sem `testcontainers-junit-jupiter`.** Ele só serve para o estilo por anotação,
  que deixamos de usar. Uma dependência a menos.
- **Sem Rest-assured por ora.** Entra quando a Camada 3 começar; adicioná-lo
  agora seria dependência parada no `pom.xml`.
- **Base chamada `PostgresIntegrationTest`**, não `AbstractIntegrationTest`: o
  nome diz o que ela traz. Ela carrega `@SpringBootTest`, `@ActiveProfiles("test")`
  e o `@Import` do container — as três juntas, porque basta uma divergir para o
  Spring criar um segundo contexto e, com ele, um segundo container.
- **`h2` saiu do `pom.xml`.** Estava como `runtime` desde o início, sem nenhum
  perfil, yaml ou teste que o usasse.
- **Deploy passou a depender do CI.** `deploy.yml` disparava em `push` na
  `develop`, em paralelo com o CI: um commit que quebrasse a suíte ia para a
  instância mesmo assim. Agora usa `workflow_run` sobre o workflow "CI" e só roda
  com conclusão `success` (o `workflow_dispatch` continua como saída manual).

O plano original, mantido como registro:

1. Adicionar Testcontainers (`postgresql`, `junit-jupiter`) e Rest-assured ao
   `pom.xml`, escopo `test`.
2. Criar `src/test/resources/application-test.yaml` apontando para o container.
   **O banco de desenvolvimento deixa de ser tocado por teste.**
3. Criar uma base compartilhada:

```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    // `static` de propósito: um container para a suite inteira. Um por classe
    // multiplicaria por 30 o tempo de CI sem afirmar nada a mais.
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

4. Migrar os três `@SpringBootTest` existentes para essa base.
5. Trocar no `ci.yml`:

```diff
-      - name: mvn clean verify (sem testes)
-        run: mvn -B clean verify -DskipTests
+      - name: mvn clean verify
+        run: mvn -B clean verify
```

   O runner do GitHub já tem Docker — o job `docker-build` prova isso —, então
   Testcontainers funciona sem serviço adicional.

**Critério de saída:** `mvn verify` verde no CI, com os testes executando, e
`ClientControllerTest` sem tocar em banco de desenvolvimento. ✅ Atingido —
**842 testes, 0 falhas**, e nenhum deles toca
`jdbc:postgresql://localhost:5432/system`.

---

### Fase 1 — Clientes *(+ isolamento entre tenants)*

O núcleo do sistema e o que mais se usa.

**Camada 2**
- CRUD completo contra Postgres: `POST`, `GET /{id}`, `PUT`, `PATCH`, soft delete.
- **Isolamento:** cliente criado no tenant A **não** aparece em `GET /clients` do
  tenant B; `GET /clients/{id}` do outro tenant devolve 404, não 200 vazio.
- Filtros e paginação: `searchTerm`, `benefitType[]`, `situation[]`, faixa de
  datas — e **`totalRecords` refletindo o filtro, não a página**.
- CPF duplicado dentro do mesmo tenant → 409/400; o **mesmo CPF em tenants
  diferentes é permitido** (são escritórios distintos).
- `PATCH` de situação grava linha em `client_situation_history`.

**Camada 3 (E2E)**
- *Cadastrar cliente e encontrá-lo na listagem*: login → `POST /clients` →
  `GET /clients?searchTerm=` → o cliente está lá, com os campos que foram
  enviados.

**Camada 4**
- Congelar `Situation`, `BenefitType`, `Gender`, `MaritalStatus`, `ClientType`.

**Critério de saída:** o teste de isolamento existe e falha se alguém remover o
`TenantResolutionFilter`.

---

### Fase 2 — Agenda

Segundo em uso diário. Já tem `AppointmentServiceTest` (41) e collection Postman.

**Camada 2**
- `GET /appointments` com filtros de período e status; `summary` batendo com a
  listagem no mesmo recorte.
- `PATCH /{id}/cancel` e `/complete`: transição de status válida e **inválida**
  (concluir um cancelado).
- `DELETE` soft: some da listagem e **não** volta em `GET /{id}`.
- `GET /{id}/history` registra as transições.

**Camada 3 (E2E)**
- *Agendar, concluir e conferir o resumo*: cria → conclui → o `summary` do mês
  reflete a conclusão.

---

### Fase 3 — Sub-recursos do cliente

Endereços, entrevistas, dados pessoais e profissionais.

**Camada 2**
- Endereços: criar, listar, atualizar, excluir; **`isPrimary` — hoje sem
  coordenação no backend** (ver Anexo B). O teste registra o comportamento
  atual, e passa a afirmar o novo quando a lacuna for fechada.
- Entrevistas: `content` obrigatório; ordenação por `occurredAt`.
- Dados pessoais/profissionais: `PUT` parcial não apaga campo não enviado.
- Sub-recurso de cliente de **outro tenant** → 404.

**Camada 3 (E2E)**
- *Ficha completa*: cria cliente → adiciona endereço → grava entrevista →
  `GET /clients/{id}` e sub-recursos devolvem tudo.

---

### Fase 4 — Arquivos

Maior superfície de risco técnico: multipart, MIME, storage externo.

**Camada 2**
- Upload de documento com `documentType` ausente → 400.
- MIME não permitido → 415 (`AllowedMimeTypesTest` já cobre a regra pura).
- Limites: `max-file-size: 10MB`, `max-request-size: 60MB`.
- `PATCH /simulations/{id}/principal` desmarca a anterior.
- Download de arquivo de outro tenant → 404.

**Camada 3 (E2E)**
- *Anexar e baixar*: upload → aparece na listagem → download devolve os mesmos
  bytes.

> Storage em disco local no perfil de teste. O S3 continua coberto por
> `ObjectStorageFileStorageServiceTest` na camada 1 — subir MinIO em container
> não afirma nada que aquele teste já não afirme.

---

### Fase 5 — Pagamentos (`client_payments`)

⚠️ **Antes de escrever teste, registrar o significado.** `client_payments` guarda
**o que o INSS paga ao cliente** — atrasados, benefício, parcela de acordo. **Não
é honorário.** Honorário é receita do escritório e não tem tabela (Anexo B).
Os dois têm exatamente o mesmo formato e nada na tabela os distingue: o
significado vem de quem escreve nela.

**Camada 2**
- `POST` nasce sempre `PENDENTE` e **ignora `paidDate`** (o DTO não tem o campo).
- `PATCH` com `status: PAGO` sem data assume hoje; com data, respeita.
- `PATCH` para `PENDENTE`/`CANCELADO` limpa `paidDate`.
- `amount <= 0` → 400 (`@DecimalMin("0.01")`).

**Camada 3 (E2E)**
- *Lançar e confirmar crédito*: `POST` → `PATCH` marcando pago → `GET` mostra
  `PAGO` com a data enviada. **É o fluxo que o frontend executa hoje em duas
  requisições** por causa da lacuna A2.

---

### Fase 6 — Auth e multi-tenancy (o que sobrou)

O isolamento já foi coberto na Fase 1. Aqui fica a infraestrutura em si.

**Camada 2**
- Login com credencial errada → 401 sem vazar se o usuário existe.
- `refresh` rotaciona e invalida o token anterior; reuso de token já usado →
  401 (`RefreshTokenServiceTest` cobre a regra; falta o caminho HTTP).
- `logout` invalida.
- Registro de tenant novo aplica as migrations no schema recém-criado.

**Camada 3 (E2E)**
- *Sessão*: login → rota protegida → refresh → mesma rota com o token novo →
  logout → a rota devolve 401.

**Varredura transversal:** um teste parametrizado que percorre todas as rotas
protegidas e afirma **401 sem token**. Barato e pega rota nova esquecida.

---

## 3. Convenções

- **Nome:** `<Alvo>IntegrationTest` (camada 2), `<Fluxo>E2ETest` (camada 3). O
  sufixo `Test` puro fica com a camada 1.
- **Dados:** `TestFixtures` (já existe) para construção; cada teste cria o que
  precisa e não depende de ordem. Sem `@Order`.
- **Limpeza:** transação com rollback onde der; onde não der (multipart, commit
  explícito), limpeza no `@AfterEach` — nunca "o próximo teste limpa".
- **Tenant nos testes:** sempre explícito. Teste que depende do tenant default
  esconde justamente o defeito que procuramos.
- **Sem `Thread.sleep`.** Se um teste precisa esperar, ou é o `await` do
  Awaitility, ou o desenho está errado.

---

## Anexo A — De/para: tela do frontend × backend

O frontend mudou bastante nas últimas semanas — telas novas (Pagamentos,
Carteira, Agenda como página), o cadastro de cliente virou página com accordion,
a ficha virou modal com histórico, e a home ganhou a lista de formulários
pendentes. Esta tabela é o estado real de quem consome o quê, e é a base do
levantamento de lacunas do Anexo B.

Legenda: ✅ existe e é consumido · ⚠️ existe mas com contorno no front ·
❌ o front chama e o backend não serve.

### A1 — Por tela

| Tela | O que consome | Rotas | Status |
|---|---|---|---|
| **Login** | `authService` | `POST /auth/login`, `/refresh`, `/logout` | ✅ |
| **Home — esteira** | `clientService` | `GET /clients` (50 primeiros), `PATCH /clients/{id}` (arrastar muda situação) | ✅ |
| **Home — formulários pendentes** | `clientService` | `GET /clients?situation=FORMULARIO_PREENCHIDO` (contador lê `totalRecords`; a modal lê a lista) | ✅ |
| **Home — card de agenda** | `appointmentService` | `GET /appointments`, `PATCH /{id}/complete`, `/cancel`, `DELETE /{id}` | ✅ |
| **Home — próximas receitas** | — | `GET /payments` | ❌ substituído por aviso na tela |
| **Home — balanço do mês** | — | `GET /revenues` + `/expenses` | ❌ dados ilustrativos |
| **Home — documentações pendentes** | — | não há fonte | ❌ dados ilustrativos |
| **Clientes — listagem** | `clientService` | `GET /clients` com `searchTerm`, `benefitType[]`, `situation[]`, faixa de datas, paginação | ✅ |
| **Clientes — ficha (modal)** | `clientService`, `clientAddressService`, `clientInterviewService`, `clientFileService` | `GET /clients/{id}`, `/situation-history`, `/addresses`, `/interviews`, `/files/*`; `PUT /clients/{id}`; `POST`/`PUT` de endereço e entrevista; upload e `DELETE` de arquivo | ⚠️ ver B3, B4, B5 |
| **Clientes — cadastro (página)** | `clientService`, `clientDataService`, `clientAddressService`, `clientInterviewService`, `clientFileService` | `POST /clients`, depois um POST por sub-recurso | ⚠️ ver B5 |
| **Cadastro — análise do CNIS** | nenhum | — | ❌ **cálculo só de tela**; não há onde persistir |
| **Agenda** | `appointmentService` | `GET /appointments`, `/summary`, `POST`, `PUT /{id}`, `PATCH /{id}/cancel`, `/complete`, `DELETE /{id}` | ✅ |
| **Pagamentos — listagem e totais** | `officePaymentService` | `GET /payments`, `/summary`, `/timeline` | ❌ tela pronta, sem servidor |
| **Pagamentos — lançar valor a receber** | `clientPaymentService` | `POST /clients/{id}/payments` + `PATCH` | ⚠️ ver B2 |
| **Pagamentos — confirmar crédito** | `clientPaymentService` | `PATCH /clients/{id}/payments/{id}` | ✅ |
| **Carteira — entradas (honorário)** | `officeRevenueService` | `POST /revenues` | ❌ sem rota e sem tabela |
| **Carteira — saídas (despesa)** | `officeExpenseService` | `GET`/`POST /expenses`, `/summary`, `/timeline` | ❌ sem rota e sem tabela |

### A2 — Serviços do frontend sem backend

Três arquivos de serviço existem, estão tipados e não têm servidor do outro
lado. Eles não são código morto: são o contrato escrito do lado de cá,
aguardando implementação.

| Serviço | Rotas | Consumido por |
|---|---|---|
| `officePaymentService` | `/payments`, `/payments/summary`, `/payments/timeline` | Pagamentos, home |
| `officeExpenseService` | `/expenses` (+ `summary`, `timeline`, CRUD) | Carteira |
| `officeRevenueService` | `/revenues` | Carteira |

As leituras desses três usam `skipErrorToast`: o 404 é esperado hoje e um toast
vermelho por carregamento seria ruído. **As escritas não silenciam** — quem
tentar lançar precisa saber que não foi.

### A3 — O que o frontend contorna hoje

Comportamentos que só existem porque o backend não oferece o caminho direto.
Cada um é uma dívida com nome e endereço:

1. **Lançar pagamento já recebido → duas requisições.** `POST` cria sempre
   `PENDENTE`; um `PATCH` marca pago. Se o segundo falhar, a tela avisa que a
   parcela ficou a vencer — não que deu erro —, porque a diferença decide se a
   pessoa lança de novo ou não. *(lacuna B2)*
2. **Ficha do cliente → cinco requisições.** `GET /clients/{id}` não traz
   endereços, entrevistas nem arquivos; a modal busca cada um e usa
   `allSettled` para que um 404 de arquivos não derrube a ficha inteira.
   *(lacuna B3)*
3. **Cadastro de endereços sem transação comum.** ~~O cadastro faz uma requisição
   por endereço.~~ **Fechado:** `POST /clients/{id}/addresses/batch` grava a lista
   inteira numa transação. O `POST` individual continua existindo. *(lacuna B5)*
4. **Desfazer com janela de 5 segundos.** Excluir compromisso muda a tela na hora
   e só envia a requisição 5s depois. **O motivo acabou:** existe
   `PATCH /appointments/{id}/restore`. A janela pode sair do frontend. *(lacuna B6)*
5. **Histórico sem "de → para".** ~~O DTO expõe só a situação nova.~~ **Fechado:**
   `previousSituation` entrou no DTO, e `GET /users` resolve o "por quem". *(lacuna B4)*
6. **Tabela de atualização monetária no navegador.** A análise do CNIS precisa
   dos fatores de correção; sem lugar no servidor, eles ficam no
   `localStorage` de cada máquina. *(lacuna B7)*

---

## Anexo B — Lacunas de contrato

Levantadas ao integrar o frontend. **Não têm data neste plano** — entram como
mapa de pendências, para decisão à parte. Onde a lacuna toca uma fase, o teste
daquela fase registra o comportamento **atual** e vira asserção do novo quando
ela for fechada.

O frontend chama **40 rotas**; o backend serve **38** — B4, B5 e B6 foram
fechadas, e vieram junto `GET /users` e as duas rotas de restore, que o frontend
ainda não consome. Restam abertas B1, B2, B3 e B7.

### B1 — Famílias de rota inexistentes

| Rota | Consumidor no front | Situação |
|---|---|---|
| `/api/v1/payments`, `/summary`, `/timeline` | Tela de Pagamentos, home | Especificado em `ESPEC_FINANCEIRO.md`, não implementado |
| `/api/v1/expenses`, `/summary`, `/timeline` | Carteira | Sem rota **e sem tabela** |
| `/api/v1/revenues`, `/summary`, `/timeline` | Carteira (honorários) | Sem rota **e sem tabela** |

Sem `/payments`, a tela de Pagamentos lista vazio; sem `/revenues` e
`/expenses`, a Carteira não tem nenhuma das duas metades do caixa.

### B2 — `ClientPaymentRequestDTO` sem `paidDate`

O `POST` cria sempre `PENDENTE`. Para lançar algo já recebido, o frontend faz
`POST` e depois `PATCH` — duas requisições, e se a segunda falhar a parcela fica
pendente sem que ninguém tenha errado.

### B3 — `ClientDetailsDTO` sem endereços aninhados

A ficha do cliente precisa de duas chamadas (`/clients/{id}` e
`/clients/{id}/addresses`) para montar uma tela só.

### B4 — `ClientSituationHistoryDTO` incompleto — ✅ FECHADA

O DTO passou a expor `previousSituation` (nulo só na primeira entrada), e
`GET /api/v1/users` traduz em nome os UUIDs de autoria — `changedByUserId`,
`createdBy`, `updatedBy` e `responsibleUserId`, que antes não eram exibíveis em
lugar nenhum.

### B5 — `POST /clients/{id}/addresses` não aceita lista — ✅ FECHADA

`POST /clients/{id}/addresses/batch` recebe de 1 a 10 endereços e grava todos em
uma transação: ou entram todos, ou não entra nenhum.

> **Correção do levantamento original.** A versão anterior deste anexo afirmava
> que `isPrimary` não era coordenado no servidor. **Era.** `ClientAddressService`
> desmarca o principal anterior no `create` e no `update`, promove o mais antigo
> restante no `delete`, e o banco garante o invariante com o índice único parcial
> `ux_client_addresses_primary` desde a V3. O erro veio de ler a assinatura dos
> métodos sem ler o corpo.

### B6 — Soft delete sem restore — ✅ FECHADA

`PATCH /appointments/{id}/restore` e `PATCH /clients/{id}/restore` desfazem a
exclusão. Ambas são idempotentes em registro ativo e devolvem 404 em id
inexistente; a da agenda registra `RESTORED` na trilha.

> **Correção do levantamento original.** A versão anterior afirmava que
> "`Appointment` e `Client` usam `deletedAt`". **Client não usava.**
> `ClientServiceImpl.delete` chamava `repository.deleteById` — exclusão física —
> e as cinco FKs de sub-recurso são `ON DELETE CASCADE`: um `DELETE` apagava
> endereços, entrevistas, arquivos, pagamentos e todo o histórico de situação, e
> zerava o vínculo do compromisso na agenda (`ON DELETE SET NULL`). A coluna
> `deleted_at` em `clients` só passou a existir na migration V12.

### B7 — Sem persistência para a análise do CNIS

O leitor de CNIS e o cálculo de tempo, carência e regras da EC 103 rodam
inteiramente no navegador. Não há tabela para guardar o extrato lido nem o
resultado, então a análise se perde ao fechar a tela — e por isso ela existe só
no cadastro, não na ficha do cliente. A tabela de atualização monetária, que a
RMI exige, mora no `localStorage` de cada máquina: não é do escritório, é de
quem colou.

---

## Anexo C — Ordem de execução

| Fase | Bloqueia | Depende de |
|---|---|---|
| 0 — Destravar CI | todas | — |
| 1 — Clientes (+ isolamento) | 3, 5 | 0 |
| 2 — Agenda | — | 0 |
| 3 — Sub-recursos | 4 | 1 |
| 4 — Arquivos | — | 3 |
| 5 — Pagamentos | — | 1 |
| 6 — Auth e tenancy | — | 1 |

As fases 2 e 5 são independentes entre si e podem correr em paralelo com 3 e 4.
