# Plano de execução — CNIS, cálculo previdenciário, relatório e IA jurídica

Esta pasta transforma o [PLANO_CNIS_CALCULO_E_IA.md](../PLANO_CNIS_CALCULO_E_IA.md) em **passos
executáveis por uma sessão do Claude Code cada**. Cada arquivo `NN-*.md` é uma tarefa fechada:
diz o que construir, onde, com que nomes, quais testes provam que ficou certo e quando parar.

## Como usar

1. **Uma sessão por passo, na ordem.** Abra o Claude Code na raiz do **backend**
   (`taniamelo-advocacia-backend`). Nos passos que tocam o front, dê acesso à pasta irmã ao abrir:
   `claude --add-dir ../taniamelo-advocacia-frontend`. Cada repo tem sua branch e seus commits.
2. Cole o prompt abaixo trocando `NN`:

   ```text
   Execute o passo NN de docs/plano-execucao/.
   Antes de escrever qualquer código, leia nesta ordem:
   1. docs/plano-execucao/00-LEIA-PRIMEIRO.md
   2. docs/plano-execucao/01-PADROES-OBRIGATORIOS.md
   3. docs/plano-execucao/02-DECISOES.md
   4. docs/plano-execucao/NN-*.md (o passo)
   e as leituras que o próprio passo lista.
   Depois me apresente o plano de execução (passo → verificação) e as suposições.
   Só comece a codar depois que eu aprovar o plano.
   ```

3. Aprove o plano, acompanhe e, no fim, receba o **relatório de entrega** (formato no
   `01-PADROES-OBRIGATORIOS.md`, §12).
4. Antes de integrar a branch, rode o [99-REVISAO-PENTE-FINO.md](99-REVISAO-PENTE-FINO.md) numa
   **sessão nova** (quem revisa não pode ser quem escreveu).
5. Integre (merge/PR) você mesmo. O Claude **não** faz push, PR nem merge sem pedido explícito.

## Ordem e dependências

| Passo | Arquivo | Repos | Depende de | Esforço |
| --- | --- | --- | --- | --- |
| 03 | [CNIS — backend](03-cnis-backend.md) | back | — | 1,5–2 sem |
| 04 | [CNIS — tela de conferência](04-cnis-frontend.md) | front | 03 | 1 sem |
| 05 | [Tabelas de referência](05-tabelas-de-referencia.md) | back | — | 1 sem |
| 06 | [Motor — apuração e achados](06-motor-apuracao-e-achados.md) | back | 03, 05 | 1,5–2 sem |
| 07 | [Motor — regras e RMI](07-motor-regras-e-rmi.md) | back | 06 | 2,5–3 sem |
| 08 | [Simulação — API e tela](08-simulacao-api-e-tela.md) | back + front | 07 | 1,5 sem |
| 09 | [Relatório PDF](09-relatorio-pdf.md) | back + front | 08 | 1–1,5 sem |
| 10 | [IA — infraestrutura](10-ia-infraestrutura.md) | back + front | — (decisões D1–D4) | 1,5 sem |
| 11 | [IA — análise de casos](11-ia-analise-de-casos.md) | back + front | 10 | 2 sem |
| 12 | [Processos, prazos e agendador](12-processos-prazos-agendador.md) | back + front | — | 2 sem |
| 13 | [Publicações (DJEN)](13-publicacoes-djen.md) | back + front | 12 | 1,5–2 sem |
| 14 | [IA — verificador e jurisprudência](14-ia-verificador-e-jurisprudencia.md) | back + front | 10 | 2–3 sem |
| 15 | [IA — redação de peças](15-ia-redacao-de-pecas.md) | back + front | 11, 14 | 3–4 sem |
| 16 | [IA — o que tem para hoje](16-ia-o-que-tem-para-hoje.md) | back + front | 12, 13, 10 | 1 sem |
| 17 | [IA — do cliente à petição](17-ia-do-cliente-a-peticao.md) | back + front | 11, 15 | 2 sem |
| 18 | [MCP somente leitura](18-mcp-somente-leitura.md) | novo diretório | 08, 12 | 3–5 dias |
| 99 | [Revisão — pente fino](99-REVISAO-PENTE-FINO.md) | ambos | cada passo | — |

**Pré-requisito de base:** em 03/11/2026 havia no working tree, sem commit, o trabalho da logo do
escritório (`TenantLogo`, `LogoImage`, `db/migration/shared/V5__tenant_logo.sql`,
`TenantController`). Ele precisa estar em `develop` antes do passo 05 (que usa a próxima versão
shared, `V6`) e do passo 09 (que usa a logo no relatório). Cada sessão confere com `git status` e
`ls db/migration/*` antes de numerar migration.

Caminhos paralelos possíveis (dois devs/sessões): **03→04→06→07→08→09** (cálculo) e
**12→13** (processos) e **10→11** (IA). Os três se juntam em 14–17.

## Portões humanos (o Claude não decide)

Alguns passos **não começam** ou **não terminam** sem uma decisão ou validação de pessoa. Eles
estão listados em [02-DECISOES.md](02-DECISOES.md). Se o passo precisar de uma decisão marcada
como **PENDENTE**, a sessão para e pergunta — não escolhe um valor "razoável".

Os principais:

- **Parâmetros legais do motor (passos 06–07)** são conferidos e assinados pela advogada.
- **Casos-ouro** (CNIS + carta de concessão, anonimizados) vêm do escritório.
- **Envio de dado de cliente a provedor de IA (passo 10 em diante)** depende de decisão da banca.
- **Modelo do relatório (passo 09)** é aprovado pela advogada.

## O que esta pasta não é

- Não é a fonte das regras previdenciárias: a fonte é o texto legal (EC 103/2019, Lei 8.213/1991,
  Decreto 3.048/1999, LC 142/2013) e a validação da advogada.
- Não substitui os `CLAUDE.md` dos dois repositórios — soma-se a eles. Em conflito, o `CLAUDE.md`
  vence e a sessão aponta o conflito no relatório.
