# Índice da documentação

Ponto de entrada único da documentação do backend. Agrupada por tema, com o
documento **canônico** de cada assunto destacado para evitar redundância.

## Arquitetura e decisões
- **`ARQUITETURA.md`** — **referência canônica**: a arquitetura Java ponta a
  ponta (stack, camadas, fluxo de requisição, segurança, persistência, build,
  deploy). Comece por aqui.
- **`ARCHITECTURE.md`** — racional das decisões de API e o caminho de evolução
  (microserviços/multi-tenant). Complementa o de cima com o *porquê*.
- **`DATA_MODEL.md`** — schema atual (baseline de migrations, PKs em UUID,
  enums por label).
- **`ROADMAP.md`** — o que foi conscientemente adiado, por fases.
- **`MULTI_TENANT_E_AUTH.md`** — guia operacional da multi-tenancy por schema e do
  refresh token/cookie (como rodar e testar com 2 tenants). **Implementado.**
- **`adr/`** — Architecture Decision Records (uma decisão por arquivo):
  - `ADR-0001-multi-tenant.md` — multi-escritório (implementado como schema-per-tenant).
  - `ADR-0002-refresh-token.md` — refresh token + cookie httpOnly (implementado).

## Funcionalidades planejadas
- **`NOTIFICACOES_E_MENSAGERIA.md`** — central de notificações: RabbitMQ vs Kafka,
  scheduler, SSE/WebSocket. Plano de ação por fases.

## Qualidade e processo
- **`QUALIDADE_SONAR_HOOKS.md`** — Sonar, JaCoCo, git hooks (pre-commit/pre-push),
  por que não usar Husky em Java. Documento canônico de qualidade.
- **`CI_CD.md`** — workflows do GitHub Actions (build/Semgrep/deploy).

## Infraestrutura e deploy (AWS)
- **`AWS_EC2_ARM64_DEPLOY.md`** — **guia canônico e verificado** de deploy
  (EC2 ARM64 / Amazon Linux 2023 / systemd). Use este para subir/atualizar.
- **`AWS_TESTE_GRATUITO.md`** — PoC no free tier com dados mockados (cenário de
  teste sem custo). Complementar, propósito distinto do canônico.
- **`INFRA_PLAN.md`** — pesquisa e decisões de infra/observabilidade (contexto).
- **`PROVISIONAMENTO_INFRA.md`** — provisionamento de pipeline/Sonar/Grafana.
- **`README-DOCKER.md`** — execução via Docker (local/containerizado).

## Operação / testes manuais
- **`requests.http`** — coleção de requisições da API para teste ponta a ponta.

---

## Consolidação feita nesta revisão (item 3)

Para remover redundância mantendo o histórico útil:

- **Removido `AWS_DEPLOY_PASSO_A_PASSO.md`** — ele próprio se declarava
  *histórico* (instância x86 substituída), apontando para `AWS_EC2_ARM64_DEPLOY.md`
  como guia corrente. Conteúdo superado.
- **Removido `AUDITORIA_ARQUITETURA.md`** — relatório pontual de auditoria; os
  achados já foram aplicados/registrados aqui e nos ADRs. Deixa de fazer sentido
  como doc permanente.

### Sobreposição restante (recomendação, não executada — confirme antes)
Os três docs de infra abaixo cobrem temas próximos e poderiam virar **um só**
(`INFRA.md`), com seções "pesquisa/decisão" (INFRA_PLAN) + "provisionamento"
(PROVISIONAMENTO_INFRA) + "pipeline" (CI_CD):

- `INFRA_PLAN.md`, `PROVISIONAMENTO_INFRA.md`, `CI_CD.md`.

Mantidos separados por ora porque cada um é referenciado por nome em outros docs;
posso fundi-los e ajustar as referências se você aprovar.
