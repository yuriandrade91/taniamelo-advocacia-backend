# Passo 18 — Servidor MCP somente leitura

> **Repo:** backend, diretório novo `mcp-server/` · **Branch:** `feat/18-mcp` · **Depende de:** 08,
> 12 (e 13 para publicações) · **Decisões:** T6, T15 · **Esforço:** 3–5 dias

## 0. Antes de começar

Leia `docs/PLANO_IA_LLM_RAG.md` §5 e §9.5 inteiros — este passo é aquele desenho. Leia
`api-tests/src/env.ts` e `fixtures.ts` (como a suíte autentica com `X-Tenant-Id` e refresh). Use a
documentação **atual** do SDK oficial de MCP para TypeScript (`@modelcontextprotocol/sdk`) e da
especificação vigente; não escreva de memória (a especificação mudou em 2026).

## 1. Objetivo

Quem usa Claude Desktop/Claude Code (ou outro cliente MCP) conecta o escritório e pergunta em
linguagem natural ("agenda de amanhã", "prazos da semana", "resume a simulação da dona Maria"). O
servidor MCP roda **na máquina de quem usa** (STDIO), autentica na API REST **com a credencial
dessa pessoa** e só lê. Nenhuma linha do backend muda.

## 2. Estrutura

```
mcp-server/
  package.json        # TypeScript, sem dependência além do SDK MCP, zod e um cliente HTTP
  tsconfig.json
  src/
    index.ts          # STDIO
    config.ts         # MCP_API_BASE_URL, MCP_TENANT, MCP_LOGIN, MCP_PASSWORD — falha alto se faltar
    api.ts            # login, refresh com rotação serializada, envelope, erro legível
    tools/*.ts        # uma ferramenta por arquivo
  tests/              # vitest: cada ferramenta contra API falsa
  README.md           # instalar, configurar no Claude Desktop/Code, o que expõe e o que não expõe
```

Credencial **só** por variável de ambiente do cliente MCP; o README avisa que o arquivo de
configuração do cliente passa a conter a senha e recomenda usuário próprio com papel mínimo.

## 3. Ferramentas (só GET)

| Ferramenta | Rota(s) |
| --- | --- |
| `buscar_clientes` | `GET /clients` com os filtros existentes |
| `ficha_do_cliente` | `GET /clients/{clientId}` + abas (a senha do INSS **não** vem nesse GET — confirme por teste) |
| `historico_de_situacao` | `GET /clients/{clientId}/situation-history` |
| `agenda` | `GET /appointments` com intervalo |
| `prazos` | `GET /deadlines` |
| `publicacoes_novas` | `GET /publications?status=NOVA` |
| `ultima_simulacao` | `GET /clients/{clientId}/benefit-simulations` (+ detalhe da mais recente) |

**Proibido expor** (teste que lista as rotas usadas e reprova qualquer não-GET ou estas):
`/clients/{clientId}/inss-password` (ou equivalente), `/users`, qualquer `POST/PUT/PATCH/DELETE`,
rotas de IA (o custo é do escritório, não do cliente MCP).

Descrição de cada ferramenta em PT, dizendo o que devolve e o limite (paginação: devolva a 1ª
página e diga como pedir a próxima).

## 4. Testes e gate

- `mcp-server`: `npm run lint`, `npm run typecheck`, `npm test` (vitest) com API falsa: cada
  ferramenta, erro 401 → refresh → repete uma vez, 403 → mensagem clara, tenant desconhecido.
- Teste de convenção: varre `src/tools` e reprova verbo diferente de GET e rotas proibidas.
- Smoke manual documentado no README (conectar no Claude Code com `claude mcp add` em escopo local
  e perguntar a agenda) — registre no relatório se rodou.
- O Sonar do backend analisa `mcp-server/`? Se não estiver em `sonar.sources`, **não** mude a
  configuração do projeto sem aprovação: rode lint/testes e reporte.

## 5. Critérios de aceite

- [ ] 7 ferramentas funcionando contra a API local.
- [ ] Nenhuma rota de escrita ou sensível (teste de convenção).
- [ ] README com instalação e riscos.
- [ ] Nada mudou em `src/` do backend.

## 6. Commits sugeridos

`feat(mcp): servidor MCP local somente leitura`, `test(mcp): ferramentas e rotas proibidas`,
`docs(mcp): instalação e limites`.

## 7. Pare e pergunte se

A especificação vigente não suportar STDIO como descrito; for preciso endpoint novo no backend para
alguma ferramenta (isso é outro passo).
