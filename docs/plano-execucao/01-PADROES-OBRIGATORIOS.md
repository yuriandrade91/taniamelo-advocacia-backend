# Padrões obrigatórios — valem para TODOS os passos

Este arquivo é a **definição de pronto** de qualquer passo desta pasta. Um passo só está entregue
quando tudo aqui foi cumprido e verificado por comando. "Acho que está certo" não é verificação.

Linguagem deste documento: **DEVE** = obrigatório; **NÃO** = proibido; **PREFIRA** = padrão, que
só se quebra com motivo escrito no relatório.

---

## 1. Leitura obrigatória antes de codar

Leia por inteiro, nesta ordem, **antes** de propor o plano:

| Repo | Arquivo | Por quê |
| --- | --- | --- |
| back | `CLAUDE.md` | comandos, armadilhas, convenções |
| back | `docs/ARQUITETURA.md` | camadas, pacotes, envelope, erros, persistência |
| back | `docs/MULTI_TENANT_E_AUTH.md` | tenant por schema, JWT, papéis |
| back | `docs/QUALIDADE_SONAR_HOOKS.md` | Quality Gate, hooks |
| back | `api-tests/README.md` | convenções da suíte de contrato |
| back | `docs/PLANO_CNIS_CALCULO_E_IA.md` | o produto que estamos construindo |
| back | `docs/PLANO_IA_LLM_RAG.md` §9 | restrições de sigilo/LGPD (passos 10+) |
| front | `CLAUDE.md` | peças de UI obrigatórias, regras de mudança |
| front | `docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/BACKEND_CONTRACT.md` | desenho, testes, contrato |

E, antes de criar qualquer classe, **abra uma classe vizinha do mesmo tipo** e copie a forma dela
(ex.: controller novo → leia `ClientDisabilityPeriodController`; service com cliente HTTP → leia
`HolidayService`; PDF → `ClientRecordService` + `ClientRecordHtmlRenderer`; integração →
`AniversariantesIntegrationTest`).

## 2. Modo de trabalho

1. **Plano antes de código.** Apresente: suposições, leituras possíveis da tarefa (se houver mais
   de uma), e a lista `passo → verificação`. Espere aprovação.
2. **Regras do `CLAUDE.md` (Karpathy):** pense antes de codar, simplicidade primeiro, mudança
   cirúrgica, execução guiada por objetivo. Toda linha alterada tem de se explicar pelo passo.
3. **NÃO invente regra jurídica.** Prazo, idade, pontos, alíquota, índice, carência: só vale o que
   estiver no texto legal citado **e** registrado como validado em `02-DECISOES.md` ou no
   documento do motor. Na dúvida, pare e pergunte. Errar aqui é dano ao cliente do escritório.
4. **NÃO amplie o escopo.** O que não está no passo fica para o passo dele. Achou um defeito fora
   do escopo? Registre no relatório; não conserte de carona.
5. **Pare e pergunte** quando: um requisito for ambíguo; uma decisão estiver PENDENTE; um teste
   existente precisar ser apagado ou enfraquecido; a solução exigir mudar migration aplicada,
   contrato público já consumido pelo front, ou um padrão deste arquivo.

## 3. Backend — arquitetura

- **Camadas:** `controller → service → repository → model`. Controller é fino (HTTP, `@Valid`,
  delega). Entidade **nunca** sai do service: DTO na fronteira. `ControllerLayeringTest` reprova
  controller com repositório.
- **Pacotes por camada**, como hoje (`controller/`, `service/`, `repository/`, `model/`, `dto/`).
  Controllers e DTOs ficam **planos** nos pacotes existentes. Domínio grande ganha subpacote **de
  service** (ex.: `service/cnis`, `service/benefit`, `service/ai`) — nunca um pacote raiz novo.
- **Núcleo puro para regra de cálculo.** Motor previdenciário, parser e verificadores
  determinísticos são **classes sem Spring e sem JPA**: entram `record`s imutáveis, saem
  `record`s imutáveis. O service Spring só busca dados, chama o núcleo e persiste. Cada passo que
  cria núcleo puro cria também um **teste de convenção** que reprova anotação Spring
  (`@Service`, `@Component`, `@Autowired`…) e import de `model`/`repository`/`jakarta.persistence`
  dentro do pacote do núcleo (estilo `ControllerLayeringTest`).
- **Envelope:** toda resposta é `ApiResponse<T>` (`successObject`, `successList`), exceto download
  de arquivo e `204`.
- **Erros:** use a hierarquia existente — `ValidationException` (400), `NotFoundException` (404),
  `BusinessException` com código novo em `BusinessErrorCode` (409/422), `SystemException` (500).
  Mensagem em PT-BR, dizendo **o que fazer**. NÃO lance `RuntimeException` genérica; NÃO capture
  `Exception` para engolir.
- **Enums de domínio:** constante em PT maiúsculo, label PT-BR, persistido pelo label via
  `AttributeConverter` dedicado, resolução tolerante por `EnumLabelSupport` (siga `BenefitType`).
  Teste de enum em `DomainEnumsTest` (ou o equivalente existente).
- **DTO de resposta com `id`:** `@JsonPropertyOrder({"id"})` (`DtoConventionsTest`). O id do
  próprio cliente é **`clientId`** em campo, path variable e query param; id de sub-recurso é `id`.
- **Rotas:** `/api/v1/...`, recurso em inglês, kebab-case (`benefit-simulations`). Sub-recurso de
  cliente sob `/api/v1/clients/{clientId}/...`. Verbos conforme `ARQUITETURA.md` §4. Listagem com
  `pageNumber` (1-based) + `pageSize` quando puder crescer sem limite.
- **Papéis:** decida o papel de **toda** rota nova e declare no mapa de
  `SecuredEndpointsContractTest#everyEndpointIsClassified` (o teste quebra até você fazer isso).
  Destrutivo (`DELETE`, descartar, confirmar dado que alimenta cálculo jurídico) exige
  `@RequerAdvogado` no mínimo; configuração do escritório exige `@RequerAdmin`. Nos passos, o
  papel **"autenticado"** corresponde, no mapa do teste, a `ABERTO` (`"-"`): no vocabulário dele
  isso quer dizer "sem papel exigido além de estar logado", não "rota pública". Nenhuma rota destes
  passos é pública.
- **Swagger:** `@Tag` no controller e `@Operation(summary, description)` em toda rota, com a
  descrição explicando o **porquê** das regras não óbvias (veja `ClientDisabilityPeriodController`).
- **Datas e dinheiro:** `LocalDate` para data civil, `Instant` para carimbo; "hoje" vem de
  `OfficeClock`, nunca `LocalDate.now()`. Dinheiro em `BigDecimal` com escala e `RoundingMode`
  explícitos e citados (ex.: RMI arredonda em centavos, `HALF_UP` — só se a fonte disser).
- **Auditoria:** entidade nova de negócio implementa `Auditable` e tem `created_by/updated_by`
  e timestamps, como as vizinhas. Leitura sensível (senha do INSS, envio a IA) usa `audit_log`
  com `AuditAction` explícita.

## 4. Backend — persistência e migrations

- Flyway é o dono do schema; Hibernate só valida (`ddl-auto: validate`).
- **Tabela de escritório** → `src/main/resources/db/migration/tenant/` (roda em **todo** schema).
  **Dado público e igual para todos** (tabelas de referência, configuração de plataforma) →
  `db/migration/shared/` (schema `public`; entidade com `@Table(schema = "public")`, como `Tenant`).
- Versão: a **próxima livre** de cada pasta (confira com `ls`; hoje tenant → `V30`, shared → `V6`).
  Nome descritivo em snake_case PT ou EN, como as vizinhas.
- **NÃO edite migration já commitada em `develop`.** Corrija com versão nova.
- PK `UUID` com `gen_random_uuid()`; FK com `ON DELETE` pensado e comentado; índices para as
  consultas que a tela faz; `NOT NULL` + `DEFAULT` coerentes com o DTO (lembre do defeito 1 do
  `api-tests/README.md`: opcional no Swagger e `NOT NULL` no banco é contrato mentiroso).
- **Massa de demonstração** só com guarda de `current_schema()` e atrás de `app.seed.demo-data`
  (veja as migrations `V23`/`V29`). NÃO coloque dado fictício em migration de estrutura.
- JSON livre (resultado de cálculo, saída de IA) em `jsonb`, mapeado com
  `@JdbcTypeCode(SqlTypes.JSON)` para um `record` tipado — nunca `String` com JSON dentro.

## 5. Clean code (os dois repos)

- Nomes que dizem o que a coisa é no domínio. Regra legal nomeada pelo dispositivo
  (`Art15PointsRule`), com Javadoc PT citando lei, artigo e parágrafo.
- Funções curtas, um nível de abstração, guard clauses no lugar de `if` aninhado.
- **Nenhum número mágico:** toda constante legal é `static final` nomeada e com a fonte no
  comentário (`/** Art. 15, §1º, EC 103/2019: +1 ponto por ano a partir de 2020. */`).
- Imutabilidade por padrão (`record`, `List.copyOf`); nada de estado estático mutável.
- Sem código morto, sem `TODO` (o Sonar já casa "todo" em português — S1135), sem configuração
  ou abstração especulativa, sem tratamento de erro para cenário impossível.
- Comentário e mensagem em **PT-BR explicando o porquê**; o código já diz o quê. Siga a densidade
  de comentário dos arquivos vizinhos.
- Frontend: identificadores em inglês, texto de tela e comentário em português (front
  `CLAUDE.md`, "Idioma").

## 6. Testes — o que todo passo entrega

| Camada | Ferramenta | Onde | Obrigatório |
| --- | --- | --- | --- |
| Núcleo puro (regra, parser, cálculo) | JUnit 5 | `src/test/java/...` espelhando o pacote | caso feliz, bordas, erro; tabelas de casos com `@ParameterizedTest` |
| Service com Spring | JUnit 5 + Mockito | idem | regra de orquestração e erros |
| Integração com banco | `*IntegrationTest extends PostgresIntegrationTest` | idem | migrations sobem, consultas, isolamento por tenant quando houver dado novo |
| Contratos transversais | `SecuredEndpointsContractTest`, `DtoConventionsTest`, `ControllerLayeringTest`, `EntityContractTest` | existentes | continuam verdes; atualizados quando a rota/DTO nova exigir |
| Contrato da API (de fora) | Playwright em `api-tests/tests/*.spec.ts` | um arquivo por recurso | **toda rota nova**: caso feliz **e** negativo, erro afirmado pelo envelope, massa própria marcada `[api-test]`, limpeza em `finally`/`afterAll` |
| Coleção Postman | `api-tests/postman/collections/*.json` (é o que `scripts/cobertura-de-rotas.mjs` lê) | — | **toda rota nova** (o job `cobertura-rotas` do CI reprova sem). Se `api-tests/README.md` mandar espelhar em `docs/postman/`, espelhe |
| Front — lógica pura | Vitest `*.test.ts` | `tests/unit/` espelhando `src/` | validação, payload, formatação |
| Front — tela | Vitest `*.test.tsx` (jsdom + fakeApi) | idem | carregando/falha/vazio/conteúdo, papéis, gravação com feedback |
| Front — ponta a ponta | Playwright | `tests/e2e/<fluxo>.spec.ts` | fluxo principal do passo contra o backend local |

Regras:

- `@DisplayName` em PT descrevendo o comportamento ("recusa CNIS de outro CPF").
- Teste reproduz o requisito **antes** do código quando o passo é de regra (TDD para núcleo puro).
- NÃO apague, desative (`@Disabled`) nem enfraqueça teste existente. Se um precisar mudar por
  causa do passo, explique no relatório qual e por quê.
- NÃO use dado pessoal real em fixture, teste, migration ou commit — nem anonimizado "de olho".
  Fixture é **fictícia** (CPF válido gerado, nomes inventados). O histórico do git já teve esse
  incidente (commit `897c9ae`); não se repete.
- Cobertura de **código novo ≥ 80%** (Quality Gate). Mire nas regras, não em getters.

## 7. Frontend — padrões

- Siga **cada item** do `CLAUDE.md` do front: formulários com `defineSection`/`useSectionForm`/
  `SchemaField`; listas com `usePagedList`/`DataList`/`ListEmpty`; filtros com `defineFilters`;
  cache com `createRequestCache`; gravação com `feedback(...)` no serviço (nunca
  `notificationCenter.success` depois da requisição); carregamento com skeleton no lugar do dado e
  `loadView`; modal com `ModalShell`; painel com `RecordDrawer`; ícones existentes em `Svg`.
- **Export estático (`output: "export"`): NÃO crie rota dinâmica `[id]`.** Detalhe abre em
  modal/drawer (como a ficha do cliente) ou por query string.
- Rotas novas em `src/constants/endpoints/paths.ts`; tipos em `src/interfaces/`; enums em
  `src/enums/`; serviço em `src/services/<recurso>Service.ts`; permissões com `usePermissions`/
  `lib/permissions` (a tela esconde o que o papel não pode; quem autoriza é o backend).
- Arquivo da API: `downloadClientFile` + `openBlob`/`saveBlob`; nunca `downloadUrl` como link.
- Atualize `docs/BACKEND_CONTRACT.md` (rota, papel, tela) a cada rota consumida.
- NÃO adicione dependência nova sem dizer por quê no plano e esperar aprovação.

## 8. Segurança, sigilo e LGPD

- **Segredo nunca em código, YAML, teste, doc ou comando colado.** Sempre variável de ambiente
  com default só para local (`${VAR:valor-dev}`), como o `CLAUDE.md` exige. Tokens do Sonar estão
  no `.env` do backend como `SONAR_TOKEN_BACK` e `SONAR_TOKEN_FRONT` — use as variáveis, nunca o
  valor; não imprima o conteúdo (`echo`, `cat .env`).
- **Senha do INSS** nunca entra em DTO novo, log, prompt de IA, índice ou ferramenta MCP.
- **`DOCUMENTOS_MEDICOS`** só entra em IA com consentimento registrado (passo 10).
- Log sem dado pessoal (CPF, nome, conteúdo de documento). Logue ids.
- Toda chamada externa (DJEN, tribunais, IA) com timeout, sem seguir redirect para fora da lista
  permitida, e com falha tratada como **aviso**, não como 500, quando a função for de apoio.
- Upload continua passando por `AllowedMimeTypes`.

## 9. Gate de qualidade — comandos (rodar e colar o resultado no relatório)

**Backend** (Docker no ar; sempre `clean`; nunca `spotless:apply` no mesmo comando do build):

```bash
mvn spotless:apply                 # sozinho, antes
mvn clean verify                   # testes + Spotless check + Checkstyle + JaCoCo
cd api-tests && npm run cobertura  # nenhuma rota sem requisição em coleção
```

Contrato de fora, com a aplicação local na porta **8080** (o `scripts/dev.sh` usa 8081; para o
e2e use 8080, que é o que `api-tests` e o front local esperam):

```bash
docker compose up -d db
SPRING_PROFILES_ACTIVE=dev SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/system \
  SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=postgres \
  SERVER_PORT=8080 mvn spring-boot:run        # em outro terminal / background
cd api-tests && npm run test:local
```

**Frontend:**

```bash
pnpm lint && pnpm typecheck && pnpm test
pnpm test:e2e                      # backend local na 8080; credenciais em env/e2e.local.env
```

Sem `env/e2e.local.env` ou sem `api-tests/.env`, **não finja**: registre no relatório que o e2e
não rodou e por quê.

**Sonar** roda só no CI (SonarCloud), a cada push. Não rode `sonar:sonar` da máquina: ele publicaria
no projeto da nuvem uma análise de árvore não commitada. O fluxo é commitar, dar push e ler o
resultado do commit (sem imprimir o token):

```bash
set -a; source ../taniamelo-advocacia-backend/.env; set +a   # no backend: source .env
# gate do branch
curl -s -u "$SONAR_TOKEN_BACK:" \
  "$SONAR_HOST_URL/api/qualitygates/project_status?projectKey=$SONAR_PROJECT_KEY_BACK&branch=develop"
# problemas abertos
curl -s -u "$SONAR_TOKEN_BACK:" \
  "$SONAR_HOST_URL/api/issues/search?componentKeys=$SONAR_PROJECT_KEY_BACK&branch=develop&resolved=false"
```

Para o frontend, troque por `SONAR_TOKEN_FRONT` e `SONAR_PROJECT_KEY_FRONT`. Sem o token, o check
"SonarCloud Code Analysis" do commit no GitHub mostra quais condições do gate falharam.

O Quality Gate (código novo) tem de **passar**: cobertura ≥ 80%, duplicação ≤ 3%, ratings A em
manutenibilidade/confiabilidade/segurança, 100% dos hotspots revisados. NÃO use `// NOSONAR` nem
`@SuppressWarnings` para passar o gate. Falsos positivos conhecidos (`CLAUDE.md`): S1135 (palavra
"todo") e regras PL/SQL de Oracle — registre no relatório se aparecerem. Build verde não garante o
gate: diga no relatório que o resultado do Sonar só existe depois do push.

## 10. Git

- Branch a partir de `develop`: `feat/NN-<tema>` (ex.: `feat/03-cnis-backend`).
- **Um tema por commit**, mensagem `tipo(escopo): descrição` em PT-BR, corpo com o porquê.
  Arquivo que cruza temas é fatiado por hunk.
- **O pre-commit compila o working tree, não o índice.** Ao fatiar, verifique cada commit sozinho:
  `git worktree add ../wt-check <sha>` + `mvn -q test-compile -Dspotless.check.skip=true` lá.
- NÃO use `--no-verify`. NÃO faça push, PR, merge ou rebase de branch compartilhada sem pedido.
- Renomeou campo de DTO? Atualize `api-tests/` (nomes de campo hardcoded) e o front no mesmo passo.

## 11. Documentação

Atualize no mesmo passo, não depois:

- `docs/DATA_MODEL.md` (tabelas novas), `docs/ARQUITETURA.md` (pacote novo, padrão novo),
  `api-tests/README.md` (spec novo na lista), front `docs/BACKEND_CONTRACT.md`.
- Documento próprio quando o passo pedir (ex.: `docs/MOTOR_PREVIDENCIARIO.md`).
- `docs/MELHORIAS_E_PENDENCIAS.md`: o que ficou em aberto por decisão ou ambiente.

## 12. Relatório de entrega (fim de toda sessão)

```markdown
## Passo NN — relatório
**Branch:** feat/NN-...  **Commits:** <sha curto> tipo(escopo): ... (um por linha)
### Feito
- ...
### Critérios de aceite
- [x] critério — evidência (comando + resultado)
- [ ] critério — por que não (bloqueio, decisão pendente)
### Gate
- mvn clean verify: N testes, 0 falhas
- cobertura de rotas: vazio
- api-tests: N passaram / N falharam (ou "não rodou: motivo")
- front lint/typecheck/test: ...  e2e: ...
- Sonar back: gate OK/FALHOU (cobertura novo código X%)  Sonar front: ...
### Decisões que tomei (e por quê)
### Pendências / riscos / fora do escopo encontrado
```

Nada de "deve funcionar": o que não foi verificado aparece como **não verificado**.
