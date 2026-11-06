# Passo 99 — Revisão pente fino (rodar em sessão NOVA, para cada passo)

Quem escreveu não revisa. Abra uma sessão nova do Claude Code e cole:

```text
Revise a branch feat/NN-... contra develop, nos repositórios que o passo NN toca.
Leia docs/plano-execucao/00, 01, 02 e o NN-*.md. Não altere código: produza o relatório
de revisão do 99-REVISAO-PENTE-FINO.md. Para cada item reprovado, cite arquivo:linha e o
critério violado. Rode os comandos do gate você mesmo; não confie no relatório do autor.
```

## 1. Escopo

- [ ] Toda mudança se explica pelo passo (`git diff develop...` arquivo por arquivo). Mudança de
      carona → apontar.
- [ ] Nada do "fora do escopo" do passo foi feito.
- [ ] Decisões PENDENTES do `02` não foram "resolvidas" no código.

## 2. Arquitetura (back)

- [ ] `controller → service → repository`; nenhuma entidade em resposta; controllers e DTOs nos
      pacotes planos.
- [ ] Núcleo puro sem Spring/JPA, com o teste de convenção.
- [ ] Toda rota nova classificada em `SecuredEndpointsContractTest`, com o papel do passo.
- [ ] DTO de resposta com `id` tem `@JsonPropertyOrder({"id"})`; `clientId` para o cliente.
- [ ] Erros pela hierarquia existente, com código em `BusinessErrorCode` e mensagem acionável.
- [ ] Migration nova (não editou aplicada), na pasta certa (tenant x shared), com índices e
      `NOT NULL` coerentes com o DTO; massa de demonstração só com guarda.
- [ ] "Hoje" via `OfficeClock`; dinheiro em `BigDecimal` com arredondamento citado.
- [ ] Swagger com `@Tag`/`@Operation` explicando o porquê.

## 3. Domínio e risco jurídico

- [ ] Toda constante legal com fonte no Javadoc **e** linha no documento do domínio
      (`MOTOR_PREVIDENCIARIO.md`, `PRAZOS.md`…).
- [ ] Nenhum parâmetro legal sem fonte; nenhum "valor razoável" inventado.
- [ ] Prazo nunca criado sem clique humano; peça nunca exportada sem revisão e com citação não
      verificada; cálculo recusa tabela desatualizada.

## 4. IA, sigilo e segurança

- [ ] Toda chamada de modelo passa por `AiService` (policy, gate, ledger, auditoria).
- [ ] Teste de sentinela da senha do INSS presente e verde para o recurso novo.
- [ ] Documento médico respeita consentimento.
- [ ] Ferramentas e saídas de IA sem escrita; saída validada por schema.
- [ ] Chamadas externas com timeout, allowlist e proteção SSRF quando buscam URL.
- [ ] Nenhum segredo em código, YAML, teste, doc ou histórico (`git log -p develop.. | grep -iE
      "sqp_|sk-ant|password\s*=|token\s*="` — revise cada ocorrência).
- [ ] Nenhum dado pessoal real em fixture ou commit.

## 5. Clean code

- [ ] Nomes de domínio; funções curtas; sem número mágico; sem código morto/`TODO`; sem abstração
      de uso único; comentários PT explicando o porquê, na densidade dos vizinhos.
- [ ] Front: só peças do `CLAUDE.md` do front; nenhuma variação paralela; sem rota dinâmica;
      feedback pelo serviço; skeleton na forma do dado; `loadView`.

## 6. Testes

- [ ] Cada regra/critério do passo tem teste que falharia sem o código.
- [ ] Unidade, integração, contratos, api-tests (feliz + negativo, envelope, `[api-test]`,
      limpeza), Postman em `api-tests/postman/collections/`, Vitest (puro + tela), e2e — os que o
      passo exige.
- [ ] Nenhum teste apagado, desativado ou enfraquecido sem justificativa aceita.
- [ ] Avaliações `@Tag` fora do build padrão; resultado relatado quando o passo exige.

## 7. Gate (rodar de novo, aqui)

- [ ] `mvn clean verify` · `npm run cobertura` · `npm run test:local` (api-tests)
- [ ] `pnpm lint && pnpm typecheck && pnpm test` · `pnpm test:e2e`
- [ ] Sonar back e front com Quality Gate OK (comandos do `01` §9), sem `NOSONAR` novo.

## 8. Git e documentação

- [ ] Um tema por commit, mensagem no padrão, cada commit compila sozinho (worktree).
- [ ] Docs do passo atualizados (`DATA_MODEL`, `ARQUITETURA`, `BACKEND_CONTRACT`, `api-tests/README`,
      doc de domínio).
- [ ] Relatório de entrega do autor confere com o que foi verificado aqui.

## Relatório de revisão

```markdown
## Revisão do passo NN — branch feat/NN-...
**Veredito:** APROVADO | APROVADO COM RESSALVAS | REPROVADO
### Reprovados (bloqueiam merge)
- [§N] critério — arquivo:linha — o que está errado — como corrigir
### Ressalvas (não bloqueiam)
### Gate rodado nesta revisão (comando → resultado)
### Divergências com o relatório do autor
```
