# Passo 13 — Publicações (DJEN) e prazo a partir da publicação

> **Repos:** backend e frontend · **Branch:** `feat/13-publicacoes` nos dois · **Depende de:** 12 ·
> **Decisões:** T13, D9, D10 · **Esforço:** 1,5–2 semanas

## 0. Antes de começar — verificação obrigatória da fonte (D9)

**Antes de qualquer código**, verifique e documente a API de consulta de comunicações do DJEN
(portal `comunica.pje.jus.br`; API conhecida como `comunicaapi.pje.jus.br`):

1. Endpoint, parâmetros (OAB número/UF, intervalo de datas de disponibilização, tribunal),
   paginação, limites de taxa, autenticação exigida (se houver), formato da resposta, campos
   (id estável da comunicação, número do processo, tribunal, órgão, data de disponibilização,
   tipo de comunicação, texto, destinatários/advogados).
2. Faça **uma** consulta real de teste com uma OAB pública de exemplo **sem gravar o resultado no
   repositório**, só para confirmar o formato.
3. Registre tudo em `docs/PUBLICACOES.md` com a data da verificação.

Se a API exigir credencial que não temos, não for estável, ou proibir uso automatizado: **pare**,
reporte e espere D9 (agregador pago). NÃO faça scraping de página.

## 1. Objetivo

Todo dia útil, o sistema busca as publicações das OABs das advogadas do escritório, liga cada uma
ao processo pelo número, e mostra uma **caixa de publicações**. De uma publicação, a pessoa cria o
prazo em um clique, com a data de início **já calculada** pela regra da publicação eletrônica
(passo 12) — e só ela decide o número de dias (T13).

## 2. Modelo de dados (tenant, próxima versão)

```
users: + oab_number varchar null, + oab_state varchar(2) null      -- D10
publications
  id, external_id varchar not null unique,   -- id da comunicação no DJEN
  oab_number varchar not null, oab_state varchar(2) not null,
  case_number varchar null,                  -- só dígitos
  legal_case_id uuid null fk legal_cases,    -- casado pelo número
  court varchar, court_body varchar, communication_type varchar,
  available_on date not null,                -- disponibilização
  published_on date not null,                -- calculada (passo 12)
  deadline_start_on date not null,           -- calculada (passo 12)
  content text not null,
  status varchar not null,                   -- NOVA, LIDA, ARQUIVADA
  read_by uuid null, read_at timestamptz null,
  created_at timestamptz not null
case_deadlines: source passa a aceitar PUBLICACAO; + publication_id uuid null fk publications
```

## 3. Backend

- `service/publications/DjenClient` (Spring, `RestClient` no padrão do `HolidayService`: base URL e
  timeouts em config, sem seguir redirect para fora do host, falha = aviso e nova tentativa no
  próximo ciclo, nunca 500 para a tela).
- `PublicationIngestionJob` (agendador do passo 12): em dias úteis, horários em config, para cada
  OAB de usuário ativo do escritório, busca desde a última disponibilização ingerida (com folga de
  2 dias para atrasos), **idempotente** por `external_id`. Casa `case_number` com `legal_cases`
  (só dígitos). Registra execução (início, fim, quantas novas, erro) em log estruturado.
- Ingestão manual: `POST /api/v1/publications/sync` (**ADVOGADO**) — mesma rotina, para o "buscar
  agora".
- Rotas:

| Verbo | Rota | Papel |
| --- | --- | --- |
| GET | `/api/v1/publications?status&from&to&caseId&oab` (paginado) | autenticado |
| GET | `/api/v1/publications/{publicationId}` | autenticado |
| PATCH | `/api/v1/publications/{publicationId}/status` | autenticado |
| PATCH | `/api/v1/publications/{publicationId}/legal-case` (ligar manualmente) | autenticado |
| POST | `/api/v1/publications/{publicationId}/deadlines` corpo `{days, counting, description}` | autenticado |
| POST | `/api/v1/publications/sync` | **ADVOGADO** |
| PUT | `/api/v1/users/me/oab` (número/UF da própria OAB) | autenticado |

Criar prazo da publicação usa `deadline_start_on` como início, exige processo ligado (422
`PUBLICACAO_SEM_PROCESSO` com a ação "ligar ao processo"), e cria o compromisso como no passo 12.

## 4. Frontend

- Rota `src/app/(private)/publicacoes/page.tsx`: caixa com filtros (novas, lidas, arquivadas,
  período, processo), `DataList`, contador de novas no menu (`CountBadge`).
- Drawer da publicação: texto, processo ligado (ou ligar), datas (disponibilização, publicação,
  início do prazo — com a explicação da regra), **Criar prazo** (dias + contagem, pré-visualização
  do vencimento do passo 12).
- Perfil: campo OAB (número/UF).
- Testes unidade/tela + e2e `tests/e2e/publications.spec.ts` com publicações semeadas pelo backend
  em `dev` (fixture fictícia — não chame o DJEN real no e2e: `app.publications.fake=true` só em
  `dev`/`test`, mesmo padrão do `DevFakeAiGateway`).

## 5. Testes back

`DjenClientTest` (contra servidor HTTP local de teste: paginação, erro, timeout, resposta
inesperada), `PublicationIngestionIntegrationTest` (idempotência, casamento por número,
cálculo de datas, falha de uma OAB não para as outras), contratos, api-tests
`publications.spec.ts` (com o fake) + Postman.

## 6. Critérios de aceite

- [ ] `docs/PUBLICACOES.md` com a verificação da API (ou relatório de bloqueio D9).
- [ ] Ingestão diária idempotente por escritório e por OAB.
- [ ] Publicação → prazo em um clique, com início correto e dias decididos pela pessoa.
- [ ] Gates e Sonar verdes.

## 7. Commits sugeridos

`docs(publicacoes): contrato verificado da API do DJEN`, `feat(publicacoes): OAB do usuário e
caixa de publicações`, `feat(publicacoes): ingestão diária idempotente`, `feat(publicacoes):
prazo a partir da publicação`, `feat(publicacoes): telas` (front).

## 8. Pare e pergunte se

A fonte não servir (D9); o DJEN não trouxer o número do processo de forma confiável; um tribunal
relevante do escritório não publicar no DJEN.
