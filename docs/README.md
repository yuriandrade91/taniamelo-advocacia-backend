# Índice da documentação

Ponto de entrada único da documentação do backend. Agrupada por tema, com o
documento **canônico** de cada assunto destacado para evitar redundância.

Aqui só entra documento que descreve **o que o sistema é hoje** ou **uma decisão
que continua valendo**. Levantamento datado, auditoria de um commit e plano já
cumprido saem quando terminam de servir — o histórico deles fica no git, e
mantê-los aqui faz quem chega ler o passado achando que é o presente.

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
- **`SUPORTE_MULTITENANT.md`** — acesso da equipe de plataforma a qualquer
  escritório (impersonation), com sessão curta e auditada. **Implementado.**
- **`adr/`** — Architecture Decision Records (uma decisão por arquivo):
  - `ADR-0001-multi-tenant.md` — multi-escritório (implementado como schema-per-tenant).
  - `ADR-0002-refresh-token.md` — refresh token + cookie httpOnly (implementado).
  - `ADR-0003-extracao-microservicos.md` — extração de identity/clients/financial/schedules
    (desenho registrado, execução não iniciada).

## Funcionalidades planejadas
- **`NOTIFICACOES_E_MENSAGERIA.md`** — central de notificações: RabbitMQ vs Kafka,
  scheduler, SSE/WebSocket. Plano de ação por fases.
- **`PLANO_IA_LLM_RAG.md`** — o que de LLM/RAG/MCP cabe neste schema e, sobretudo,
  o que não cabe. Exploração, não compromisso.

## Qualidade e processo
- **`QUALIDADE_SONAR_HOOKS.md`** — Sonar, JaCoCo, git hooks (pre-commit/pre-push),
  por que não usar Husky em Java. Documento canônico de qualidade.
- **`CI_CD.md`** — workflows do GitHub Actions (build/Semgrep/deploy).
- **`OBSERVABILIDADE.md`** — métricas, logs e tracing self-hosted na EC2, ligados
  ao Spring Boot. **Implementado.**

## Infraestrutura e deploy (AWS)
- **`AWS_EC2_ARM64_DEPLOY.md`** — **guia canônico e verificado** de deploy
  (EC2 ARM64 / Amazon Linux 2023 / systemd). Use este para subir/atualizar.
- **`INFRA_PLAN.md`** — pesquisa e decisões de infra/observabilidade (contexto).
- **`PROVISIONAMENTO_INFRA.md`** — provisionamento de pipeline/Sonar/Grafana.
- **`README-DOCKER.md`** — execução via Docker (local/containerizado).

## Operação / testes
- **`requests.http`** — requisições da API para disparar à mão do editor.
- **`../api-tests/README.md`** — a suíte de contrato (Playwright + newman), que
  roda de fora contra uma instância publicada.
