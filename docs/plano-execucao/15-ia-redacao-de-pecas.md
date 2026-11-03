# Passo 15 — IA: redação de peças

> **Repos:** backend e frontend · **Branch:** `feat/15-pecas` nos dois · **Depende de:** 11, 14
> (e 12 para ligar a processo) · **Decisões:** T5, T6, T14, D8, D12 · **Esforço:** 3–4 semanas

## 0. Antes de começar

Leia os passos 10, 11, 14, `docs/IA.md`, `service/RichText.java` (sanitização do rich text que já
existe), `ClientRecordHtmlRenderer` (HTML → PDF). No front, o editor rico já usado nas entrevistas
(TipTap: `@tiptap/react`, `starter-kit`) — **reutilize-o**, não traga outro editor.

## 1. Objetivo

A pessoa escolhe um **modelo do escritório** (D12), o cliente e, opcionalmente, o processo, a
análise de caso e a pesquisa de jurisprudência; o sistema gera uma **minuta** no editor. Ela edita;
pede à IA para **reescrever só um parágrafo**; insere julgados **verificados**; marca **revisado**
(ADVOGADO); e exporta em PDF. Exportar é impossível com citação não verificada ou sem revisão (T5).

## 2. Modelo de dados (tenant)

```
piece_templates
  id, name varchar not null, category varchar not null,   -- PieceCategory (abaixo)
  body text not null,                    -- HTML sanitizado, com marcadores {{cliente.nome}} etc.
  active boolean not null default true, + auditoria
legal_pieces
  id, client_id fk on delete cascade, legal_case_id uuid null, template_id uuid null,
  case_analysis_id uuid null, jurisprudence_research_id uuid null,
  title varchar not null, category varchar not null,
  status varchar not null,               -- RASCUNHO, REVISADA, EXPORTADA
  content text not null,                 -- HTML sanitizado (RichText)
  version int not null,                  -- otimista: PUT com versão antiga → 409
  reviewed_by uuid null, reviewed_at timestamptz null,
  export_storage_key varchar null, exported_at timestamptz null, exported_by uuid null,
  deleted_at timestamptz null, + auditoria
legal_piece_versions (piece_id, version, content, created_by, created_at, origin)  -- origin: MANUAL, IA_MINUTA, IA_PARAGRAFO
legal_piece_citations (id, piece_id, jurisprudence_citation_id, anchor_id varchar not null)
```

`PieceCategory`: `PETICAO_INICIAL`, `MANIFESTACAO_LAUDO`, `IMPUGNACAO`, `REPLICA`,
`RECURSO_INOMINADO`, `APELACAO`, `CONTRARRAZOES`, `EMBARGOS_DECLARACAO`, `REQUERIMENTO_ADMINISTRATIVO`,
`RECURSO_ADMINISTRATIVO`, `OUTRA` — confirme a lista com a advogada (D12).

**Citação no HTML:** elemento `<cite data-citation-id="<uuid>">…</cite>` (permitido na allowlist do
`RichText` — ajuste com teste). Só pode apontar para `jurisprudence_citations` com status
`VERIFICADA`; o backend recusa salvar `cite` para citação não verificada ou inexistente.

## 3. Operações de IA (via `AiService`)

| Operação | Entrada | Saída (estruturada) |
| --- | --- | --- |
| Minuta | modelo + contexto do cliente (allowlist) + simulação/achados + pontos **aceitos** da análise + citações **verificadas** escolhidas | seções em HTML simples (títulos, parágrafos), usando só as citações dadas, por `citationId` |
| Reescrever parágrafo | parágrafo + parágrafos vizinhos (contexto) + instrução da pessoa | **um** parágrafo substituto |

Regras:

- O modelo **não** pode introduzir citação nova: qualquer referência a julgado na saída que não
  seja um `citationId` fornecido é removida e vira aviso "o texto mencionava julgado não
  verificado — removido" (detecção por padrão de número de processo/súmula/tema + teste).
- Reescrever parágrafo troca **só** aquele parágrafo (o front envia o id do nó; o backend devolve
  o texto; quem aplica é o editor). Cada aplicação cria versão `IA_PARAGRAFO`.
- Contexto nunca inclui senha do INSS; documento médico só com consentimento (passo 10).

## 4. Revisão e exportação

- `POST /review` (**ADVOGADO**): exige zero `cite` órfão/não verificado e reverifica as citações
  com mais de 7 dias (`CitationVerifier`); muda para `REVISADA`. Editar uma peça `REVISADA` volta
  para `RASCUNHO` (revisão perdida, com aviso).
- `POST /export` (autenticado): só `REVISADA`; gera PDF pelo padrão do `openhtmltopdf` (cabeçalho
  do escritório opcional), guarda no storage (chave por `StorageKeys`) e grava em `legal_pieces`
  as colunas `export_storage_key`, `exported_at`, `exported_by`; muda para `EXPORTADA`. Download
  por `GET .../pieces/{pieceId}/export`. **NÃO** grave como `client_files`: exigiria um
  `DocumentType` novo, e o catálogo de documentos do front (`documentCatalog.ts`, um registro por
  tipo) passaria a cobrar esse "documento" do cliente. DOCX só se D8 aprovar — **não** faça agora.

## 5. Rotas

| Verbo | Rota | Papel |
| --- | --- | --- |
| GET/POST | `/api/v1/piece-templates` | autenticado / **ADVOGADO** para criar |
| GET/PUT | `/api/v1/piece-templates/{templateId}` | autenticado / **ADVOGADO** para alterar |
| DELETE | `/api/v1/piece-templates/{templateId}` (desativa) | **ADVOGADO** |
| GET/POST | `/api/v1/clients/{clientId}/pieces` | autenticado |
| GET/PUT | `/api/v1/clients/{clientId}/pieces/{pieceId}` (PUT com `version`) | autenticado |
| GET | `/api/v1/clients/{clientId}/pieces/{pieceId}/versions` | autenticado |
| POST | `/api/v1/clients/{clientId}/pieces/{pieceId}/ai/draft` → 202 | autenticado |
| POST | `/api/v1/clients/{clientId}/pieces/{pieceId}/ai/rewrite-paragraph` | autenticado |
| POST | `/api/v1/clients/{clientId}/pieces/{pieceId}/review` | **ADVOGADO** |
| POST | `/api/v1/clients/{clientId}/pieces/{pieceId}/export` | autenticado |
| GET | `/api/v1/clients/{clientId}/pieces/{pieceId}/export` (baixa o PDF guardado) | autenticado |
| DELETE | `/api/v1/clients/{clientId}/pieces/{pieceId}` (soft) | **ADVOGADO** |

## 6. Frontend

- Rota `src/app/(private)/pecas/page.tsx` (lista de peças com filtros) + gestão de modelos.
- Editor em `ModalShell` de tela cheia (sem rota dinâmica): TipTap com marcação de `cite`
  (extensão de nó/mark mínima), barra lateral com **Citações** (selo verificado, data, link) e
  **Inserir jurisprudência** (busca nas pesquisas do passo 14 — agora sim, ação **Usar na peça**
  na tela de jurisprudência), ação por parágrafo **Reescrever com IA** (popover com instrução →
  pré-visualização lado a lado → aplicar/descartar), indicador de status, **Marcar como revisada**
  (ADVOGADO) e **Exportar PDF**, com motivos visíveis quando bloqueado.
- Aviso de alterações não salvas (o front já tem `unsaved.ts` — reutilize o padrão), conflito de
  versão (409) explicado.
- Na ficha do cliente e na análise de caso: atalho **Gerar peça**.
- Testes unidade (aplicar parágrafo, detecção de `cite` órfão no cliente, payloads), tela, e e2e
  `tests/e2e/legal-pieces.spec.ts` com gateway falso (minuta → reescrever parágrafo → inserir
  citação verificada → revisar → exportar; tentativa de exportar sem revisar bloqueada).

## 7. Testes back

Sanitização com `cite` (permitido só com id válido; script e atributos perigosos removidos),
remoção de julgado não fornecido pela IA, revisão/exportação e suas recusas, versão otimista,
volta para rascunho ao editar revisada, PDF gerado e guardado, contratos, api-tests
`clients.pieces.spec.ts` e `piece-templates.spec.ts` + Postman. Avaliação `@Tag("ai-eval")`:
3 minutas por categoria com casos fictícios, revisadas pela advogada (nota em rubrica simples
registrada no relatório).

## 8. Critérios de aceite

- [ ] É impossível exportar com citação não verificada ou sem revisão de ADVOGADO (teste de API).
- [ ] Reescrever altera só o parágrafo escolhido e gera versão.
- [ ] Modelos do escritório (D12) cadastrados — ou relatório dizendo que estão pendentes e que os
      testes usam modelos fictícios.
- [ ] Gates e Sonar verdes nos dois repos.

## 9. Commits sugeridos

`feat(pecas): modelos e peças com versões`, `feat(pecas): citação verificada dentro do texto`,
`feat(pecas): minuta e reescrita de parágrafo por IA`, `feat(pecas): revisão e exportação em PDF`,
`feat(pecas): editor com citações e reescrita` (front), `test(e2e): da minuta ao PDF`.

## 10. Pare e pergunte se

O TipTap não suportar a marcação de citação sem extensão grande (proponha); a advogada não validar
a lista de categorias; a exportação precisar de formatação (margens, fonte) específica de tribunal.
