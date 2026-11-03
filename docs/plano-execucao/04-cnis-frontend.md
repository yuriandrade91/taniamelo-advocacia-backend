# Passo 04 — Conferência do CNIS (frontend)

> **Repo:** frontend (`../taniamelo-advocacia-frontend`) · **Branch:** `feat/04-cnis-frontend` ·
> **Depende de:** 03 integrado em `develop` do backend · **Decisões:** T8, T16 · **Esforço:** 1 semana

## 0. Antes de começar

Leia, além do `01`: front `CLAUDE.md` (todo), `docs/TESTING.md`, `docs/BACKEND_CONTRACT.md`,
`src/components/clients/details/ClientDetailsModal.tsx` e `SheetLayout.tsx`,
`src/components/clients/form/sections/files/*`, `src/services/clientFileService.ts`,
`src/lib/permissions.ts`, `src/components/clients/disability/ClientDisabilityPanel.tsx` (painel de
sub-recurso da ficha — o modelo mais próximo do que este passo faz). Rode o backend local na 8080
(`01` §9) e abra o Swagger para conferir os DTOs do passo 03 — **o contrato é o do backend**, não
o deste documento.

## 1. Objetivo

Na ficha do cliente, a pessoa escolhe um PDF de CNIS já enviado, clica **Importar vínculos**, vê o
PDF ao lado da tabela extraída, corrige o que estiver errado e — se for ADVOGADO — **confirma**.

Verificável: e2e sobe o CNIS fictício do passo 03, importa, edita um vínculo, confirma como ADMIN e
vê o estado "Confirmada"; como STAFF, o botão Confirmar não aparece.

## 2. Fora do escopo

Cálculo e achados (passo 08). Qualquer regra de validação nova no front além de forma de campo — a
validação de negócio é a do backend (`issues`).

## 3. Desenho

**Onde:** na ficha (`ClientDetailsModal`), onde as simulações do cliente são listadas, cada PDF de
simulação ganha a ação **Importar vínculos** (`RowActions`). A ficha ganha a seção/aba **CNIS** com
o histórico de extrações (status em `StatusBadge`: Pendente de conferência, Confirmada, Substituída,
Descartada) e a extração vigente.

**Conferência:** abre em `ModalShell` de tela cheia (não é rota — export estático):

- Esquerda: o PDF (blob via `downloadClientFile` + `openBlob`/URL de objeto em `<iframe>`/`<object>`;
  libere a URL ao fechar).
- Direita: `DataList` dos vínculos (sequência, origem, tipo de filiação, início, fim, indicadores
  como `Pill`), cada um expansível com as competências (mês, valor, indicadores). Mês sem valor
  aparece destacado como "sem remuneração no extrato".
- Topo: lista de pendências (`issues`) — `BLOQUEANTE` em tom de erro, `ALERTA` em tom de aviso,
  cada uma clicável levando ao vínculo.
- Editar vínculo: `RecordDrawer` com formulário `defineSection` (datas, tipo, origem); competências
  editáveis em lista (mês + valor em reais com `lib/formValues`).
- Ações: **Adicionar vínculo**, **Descartar** (ADVOGADO, `ConfirmDialog`), **Confirmar**
  (ADVOGADO, desabilitado com dica enquanto houver `BLOQUEANTE`; `ConfirmDialog` dizendo que a
  confirmação substitui a anterior e passa a alimentar o cálculo).
- Extração não pendente abre **só leitura**.

**Peças obrigatórias** (front `CLAUDE.md`): `loadView` para os quatro estados; skeleton na forma
do dado (`RowSkeletons`); `feedback("Vínculos importados.")`, `feedback("Extração confirmada.")`
etc. nos serviços; `messageOf` nas falhas; `createRequestCache` por cliente; `next/dynamic` para o
modal de conferência.

**Código novo:**

| Arquivo | Conteúdo |
| --- | --- |
| `src/constants/endpoints/paths.ts` | grupo `CNIS` com as 10 rotas do passo 03 |
| `src/interfaces/cnis.ts` | tipos dos DTOs (nomes de campo **iguais** aos do backend) |
| `src/enums/cnis/` | status, tipo de filiação, origem do dado, severidade (chaves do backend, rótulos PT) |
| `src/services/cnisService.ts` | chamadas com `feedback` |
| `src/lib/permissions.ts` | `canConfirmCnis()` com nome próprio (mesma regra de `canDestroy` hoje; comente o porquê, como `canSeeInssPassword`) |
| `src/components/clients/cnis/` | `ClientCnisPanel.tsx`, `CnisReviewModal.tsx`, `CnisBondDrawer.tsx`, módulos puros `cnisReview.ts` (ordenação, agrupamento de issues por vínculo, formatação de competência) |

## 4. Testes obrigatórios

- Unidade (`tests/unit/components/clients/cnis/cnisReview.test.ts`): agrupamento de issues,
  ordenação de vínculos, competência sem valor, montagem do payload de edição.
- Tela (`*.test.tsx`, fakeApi): painel nos 4 estados; conferência mostra issues; Confirmar
  desabilitado com `BLOQUEANTE`; STAFF não vê Confirmar/Descartar; edição chama o `PUT` certo e
  mostra o feedback; extração confirmada é só leitura.
- E2E `tests/e2e/cnis-import.spec.ts`: fluxo do §1 com o PDF fictício do passo 03 (copie-o para
  `tests/e2e/fixtures/` ou leia do backend — sem dado real) e limpeza pela API.
- `pnpm lint && pnpm typecheck && pnpm test` limpos; Sonar front com gate OK.

## 5. Documentação

`docs/BACKEND_CONTRACT.md`: as 10 rotas com papel e tela. `docs/ROADMAP.md` do front se listar
CNIS como pendente.

## 6. Critérios de aceite

- [ ] Fluxo do §1 funciona contra o backend local e o e2e passa.
- [ ] Os 4 estados de carregamento seguem o padrão (nenhum "vazio" enquanto carrega).
- [ ] STAFF não vê ação de ADVOGADO; backend continua sendo quem recusa (teste com 403 tratado).
- [ ] Nenhuma dependência nova.
- [ ] Gate do front e Sonar front verdes.

## 7. Commits sugeridos

1. `feat(cnis): rotas, tipos e serviço do CNIS`
2. `feat(cnis): painel de extrações na ficha do cliente`
3. `feat(cnis): conferência lado a lado com edição e confirmação`
4. `test(e2e): importação e confirmação do CNIS`
5. `docs(contrato): rotas do CNIS`

## 8. Pare e pergunte se

O contrato real do passo 03 divergir deste documento (siga o backend e avise); a ficha não tiver
lugar natural para a seção sem mexer no layout aprovado (há ADR em aberto sobre o cadastro —
`docs/adr/0002-client-form-layout.md`: **não** mexa na revisão do cadastro).
