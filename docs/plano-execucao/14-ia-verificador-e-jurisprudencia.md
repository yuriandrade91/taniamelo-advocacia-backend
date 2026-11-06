# Passo 14 — IA: verificador de citações e jurisprudência inteligente

> **Repos:** backend e frontend · **Branch:** `feat/14-jurisprudencia` nos dois · **Depende de:** 10 ·
> **Decisões:** T4, T5, T6, D13 · **Esforço:** 2–3 semanas

## 0. Antes de começar

Leia o passo 10 e `docs/IA.md`. **Use a skill `claude-api`** para as ferramentas de servidor
`web_search` e `web_fetch` (versão atual, `allowed_domains`, `max_uses`, como os erros voltam —
"server-tool errors don't raise") e para a compatibilidade entre saída estruturada e ferramentas.
Confirme no plano a combinação que a documentação atual permite.

## 1. Objetivo

1. Um **verificador de citações** determinístico, nosso, que confirma no site oficial do tribunal
   que um julgado existe e que o trecho citado está **literalmente** no inteiro teor. É usado aqui
   e na redação de peças (passo 15).
2. A **pesquisa de jurisprudência**: a pessoa descreve a tese; o sistema busca em fontes oficiais,
   lê o inteiro teor, agrupa os julgados em **correntes** (favorável/desfavorável) e só mostra como
   citável o que passou no verificador.

Sem acervo próprio indexado (T4): a busca é feita na hora, na fonte.

## 2. Verificador — núcleo + adaptador

- Puro `service/citation/`: `CitationClaim(court, caseNumber, organ, rapporteur, judgedOn, url,
  excerpt)`, `CitationCheck(status, checkedAt, matchedPage?, reason)`, `ExcerptMatcher` (a
  normalização de texto extraída do `ExcerptVerifier` do passo 11, no mesmo pacote — os dois usam
  a mesma, sem duplicar), `CitationStatus`: `VERIFICADA`, `TRECHO_NAO_ENCONTRADO`, `JULGADO_NAO_ENCONTRADO`,
  `FONTE_INDISPONIVEL`, `DOMINIO_NAO_PERMITIDO`.
- Spring `service/CitationVerifier`: busca a URL **ela mesma** (não confia no texto que o modelo
  diz ter lido), com:
  - **Allowlist de domínios** (D13) em configuração — proposta inicial para a advogada validar:
    `stf.jus.br`, `stj.jus.br`, `cjf.jus.br` (TNU), `trf1.jus.br` … `trf6.jus.br`, `tst.jus.br`
    não (não é previdenciário). Subdomínios dos permitidos são aceitos.
  - **Proteção SSRF:** só `https`, host na allowlist, redirect só para host permitido, sem IP
    literal, sem porta fora de 443, limite de tamanho e timeout.
  - Extração de texto de HTML (sem dependência nova se der; se precisar de um parser HTML,
    proponha no plano) e de PDF (PDFBox).
  - Confere número do processo **e** trecho; grava `checkedAt`.
- Cache por URL+trecho (Caffeine, 24 h) para não refazer a mesma conferência na mesma peça.

## 3. Pesquisa de jurisprudência

- Prompt `jurisprudence-research-v1.md`: tese, benefício, tribunais desejados; pede que o modelo
  use `web_search` (restrito à allowlist) e `web_fetch` para ler o inteiro teor **antes** de citar,
  e devolva correntes com julgados (campos de `CitationClaim`) e uma síntese de "para onde a
  jurisprudência caminha" com contagem por corrente.
- Toda `CitationClaim` devolvida passa pelo verificador **antes** de ir para a tela. Não
  verificadas aparecem separadas, marcadas, e **não** podem ser inseridas em peça (passo 15).
- Assíncrono como o passo 11 (mesmo executor e propagação de contexto).
- Modelo `default`; `max_uses` de busca e de leitura em configuração (custo — PLANO §4.2).

**Modelo de dados (tenant):** `jurisprudence_researches` (id, question, benefit_type, courts[],
status, summary, model, prompt_version, ai_usage_id, client_id null, + auditoria) e
`jurisprudence_citations` (id, research_id, current_label, stance `FAVORAVEL`/`DESFAVORAVEL`/
`NEUTRA`, campos da claim, `verification_status`, `verified_at`, `verification_reason`).

**Rotas:**

| Verbo | Rota | Papel |
| --- | --- | --- |
| POST | `/api/v1/jurisprudence/researches` corpo `{question, benefitType?, courts[]?, clientId?}` → 202 | autenticado |
| GET | `/api/v1/jurisprudence/researches` (paginado) | autenticado |
| GET | `/api/v1/jurisprudence/researches/{researchId}` | autenticado |
| POST | `/api/v1/jurisprudence/citations/{citationId}/reverify` | autenticado |
| DELETE | `/api/v1/jurisprudence/researches/{researchId}` | **ADVOGADO** |

## 4. Frontend

- Rota `src/app/(private)/jurisprudencia/page.tsx`: formulário da tese (benefício, tribunais),
  histórico de pesquisas; resultado com síntese, correntes (contagem), julgados com selo
  **verificado no site do tribunal em dd/mm** + link para a fonte; não verificados em grupo
  separado com o motivo.
- **NÃO** crie a ação "Usar na peça" neste passo (UI sem uso); o passo 15 a acrescenta.
- Polling como no passo 11. Testes unidade/tela + e2e `tests/e2e/jurisprudence.spec.ts` com o
  `DevFakeAiGateway` (acrescente a resposta de pesquisa) e o verificador apontado para um servidor
  local de teste em `dev`/`test` (propriedade de allowlist de teste — nunca liberar `localhost` em
  outros perfis, com teste).

## 5. Testes back

`ExcerptMatcherTest`, `CitationVerifierTest` (servidor HTTP local: verificado, trecho ausente,
número ausente, 404, timeout, redirect para fora → bloqueado, domínio fora da allowlist, IP
literal), `JurisprudenceResearchIntegrationTest` (gateway falso devolve 3 julgados, 1 com trecho
inventado → fica `TRECHO_NAO_ENCONTRADO`), contratos, api-tests + Postman.
Avaliação `@Tag("ai-eval")`: 5 teses previdenciárias conhecidas (definidas com a advogada) —
mede quantos julgados verificados por tese e a taxa de citação não verificada.

## 6. Critérios de aceite

- [ ] Nenhuma citação aparece como citável sem passar pelo verificador.
- [ ] Proteções de SSRF com teste.
- [ ] Pesquisa completa com gateway falso; uma rodada real (`ai-live`) relatada se houver chave.
- [ ] Gates e Sonar verdes.

## 7. Commits sugeridos

`feat(citacao): verificador de citações em fonte oficial`, `feat(jurisprudencia): pesquisa com
busca e leitura restritas a domínios oficiais`, `feat(jurisprudencia): tela de pesquisa` (front),
`test(jurisprudencia): verificador contra servidor de teste`.

## 8. Pare e pergunte se

Um tribunal só servir inteiro teor por página com JavaScript/captcha (não contorne); a
documentação atual do SDK não permitir a combinação de ferramentas e saída estruturada desejada.
