# Plano de infraestrutura, observabilidade e qualidade

Rascunho para revisão em conjunto antes de qualquer execução. Cobre: onde os
dados vão morar agora (autohospedado, por desconfiança do free tier de
terceiro), observabilidade, qualidade de código, mensageria (Kafka e/ou
RabbitMQ) e o caminho de migração pra nuvem quando fizer sentido.

**Orçamento-alvo:** até US$20/mês. **Escala real:** 2 usuários simultâneos -
isso dimensiona tudo abaixo; nenhum item aqui precisa de capacidade de
"produção com muitos usuários".

---

## 1. Por que saímos do Neon e como fica o banco

Motivo: Neon é free tier de terceiro - o dado vivo do cliente fica hospedado
fora do nosso controle, e uma mudança de política/suspensão de conta (comum
em free tier, sem SLA) deixaria a aplicação sem banco sem aviso. Para dado de
cliente de escritório de advocacia (CPF, financeiro, senha do INSS), esse
risco não é aceitável mesmo sendo grátis.

**Decisão:** Postgres autohospedado, no mesmo servidor da aplicação, dentro
do nosso próprio `docker-compose.yml` (já existe, só ganha o serviço extra
que hoje é externo/Neon).

- **Servidor:** Droplet DigitalOcean, 2GB RAM (~US$12/mês). O de 1GB
  (~US$6/mês) fica apertado pra rodar app + Postgres + observabilidade juntos
  - JVM (~300-500MB) + Postgres (~100-200MB) + Prometheus/Grafana
  (~250-400MB) não cabe com folga em 1GB.
- **Controle total do dado:** sem terceiro guardando a base viva.
- **Trade-off honesto:** autohospedar não elimina risco de queda - só troca
  "risco de terceiro" por "risco operacional nosso". É por isso que o backup
  abaixo não é opcional.

## 2. Backup - não negociável nesse modelo

- `pg_dump` diário, criptografado, subindo pro **Cloudflare R2** (10GB
  grátis, sem taxa de saída) - **provedor diferente do compute**, de
  propósito: se o Droplet cair ou a conta DigitalOcean tiver problema, o
  backup não cai junto.
- Agendamento via GitHub Actions (`schedule`/cron) ou cron no próprio
  servidor - o job já roda dentro dos 2.000 minutos grátis mensais do plano
  free do GitHub Actions em repositório privado.
- Mesmo bucket R2 guarda os arquivos de cliente (documentos/simulações),
  então já existe a conta - só adiciona um prefixo `backups/` separado dos
  arquivos normais.

## 3. Observabilidade

### Opção A - Grafana + Prometheus (o que foi pedido)

Passo a passo local primeiro, produção depois se a RAM do Droplet sobrar:

1. Adicionar `micrometer-registry-prometheus` no `pom.xml`.
2. Em `application.yaml`, incluir `prometheus` e `metrics` em
   `management.endpoints.web.exposure.include` (hoje só tem `health,info`).
3. Novos serviços `prometheus` e `grafana` no `docker-compose.yml`, com um
   `prometheus.yml` fazendo scrape de `/actuator/prometheus` da aplicação.
4. Importar um dashboard pronto da comunidade Grafana pra Spring Boot (JVM,
   GC, latência por endpoint, uso do pool HikariCP) em vez de montar do zero.
5. Validar local; decidir depois se sobe no Droplet de produção ou se as
   métricas de produção vão para o **Grafana Cloud free tier** (existe e
   cobre bem esse volume), mantendo o Droplet mais livre.

### Opção B - sugestão mais moderna: SigNoz

Tendência 2026 é plataforma unificada nativa em OpenTelemetry em vez de
juntar Prometheus + Loki + Tempo + Grafana na mão. SigNoz junta métricas,
logs e traces num produto só:

1. Um `docker-compose` oficial do SigNoz (inclui o próprio banco,
   ClickHouse).
2. Java agent do OpenTelemetry anexado na aplicação (`-javaagent`, sem mudar
   código) apontando pro coletor local.
3. Métricas + logs + traces por requisição aparecem automaticamente.

**Recomendação:** começar pelo Grafana/Prometheus (mais testado, mais
documentação), manter o SigNoz anotado como troca futura se administrar 4
componentes separados pesar demais pro time.

## 4. Qualidade de código

### SonarQube Community - local, sob demanda (não fica ligado 24/7)

1. Serviço `sonarqube:community` num `docker-compose` **separado** do de
   produção - o Sonar precisa do próprio banco de metadados, não deve
   dividir o Postgres da aplicação.
2. Sobe só na hora de analisar, derruba depois - não consome o orçamento de
   hosting.
3. Configurar `sonar-maven-plugin` no `pom.xml`; gerar token local na UI;
   rodar `mvn clean verify sonar:sonar -Dsonar.host.url=... -Dsonar.login=...`.
4. Sem suíte de testes automatizados ainda (já registrado no
   `docs/ROADMAP.md`), a métrica de cobertura aparece zerada no início - mesmo
   assim, duplicação/complexidade/code smells já valem a pena medir.

### Semgrep - o "hype" atual, complementar

Não precisa de servidor rodando (CLI/GitHub Action só), análise em segundos,
regras customizadas em YAML simples. Roda em CI de graça, cobrindo o lado de
segurança/padrão customizado que o Checkstyle não cobre. Não é
Sonar-ou-Semgrep: Sonar local pra visão geral, Semgrep em CI pra segurança.

## 5. Mensageria: Kafka e/ou RabbitMQ

Ainda não há caso de uso implementado - registrando aqui pra planejar antes
de implementar. Casos de uso plausíveis pra esta aplicação:

- Notificação assíncrona (e-mail/WhatsApp) em mudança de situação, parcela
  vencendo, prazo se aproximando (já cotado no `docs/ROADMAP.md` Fase C).
- Processamento pesado desacoplado da requisição: verificação de
  vírus/malware no upload de arquivo, OCR/cálculo em cima de simulação de
  CNIS (também Fase C).
- Desacoplar de vez a escrita em `audit_log` do fluxo principal (hoje isso já
  é feito via `TransactionSynchronization#afterCommit()` dentro do próprio
  Hibernate - uma fila tornaria isso independente do processo da aplicação,
  mas hoje não é um gargalo real).

**Comparação técnica honesta pro tamanho de vocês (2 usuários):**

| | RabbitMQ | Kafka |
|---|---|---|
| Footprint de recurso | Leve - um container, roda bem no Droplet de 2GB | Pesado - precisa de mais RAM/disco, historicamente exigia Zookeeper (hoje dá pra rodar em modo KRaft sem ele, mas ainda é mais componente) |
| Encaixe no caso de uso | Ótimo pra fila de tarefa/notificação (exatamente os casos acima) | Pensado pra alto volume de eventos/streaming com múltiplos consumidores replayable - não é o que a aplicação precisa hoje |
| Operação | Simples de subir e entender | Mais peças móveis pra manter |

**Recomendação:** começar por **RabbitMQ** local via `docker-compose` pros
casos de uso imediatos (fila de notificação, processamento assíncrono de
upload) - cabe tranquilo no orçamento e no Droplet de 2GB. Kafka fica
registrado como evolução natural **se** o volume de eventos crescer a ponto
de precisar de múltiplos consumidores independentes ou replay de histórico de
eventos - não há necessidade técnica disso agora, mas a intenção de usar os
dois fica documentada aqui pra quando o gatilho aparecer.

## 6. Pesquisa: ferramentas de nuvem para quando migrarmos

Resumo do que já foi levantado, pra quando o volume justificar sair do
autohospedado. Nenhuma decisão tomada ainda - só o material de apoio.

| Provedor | Compute (app) | Banco | Observação |
|---|---|---|---|
| AWS | Fargate 0,5 vCPU/1GB ~US$18/mês | RDS db.t4g.micro ~US$12/mês | Mais caro dos quatro; tier gratuito pra conta nova mudou em jul/2025 (crédito de 6 meses, não mais 12 meses grátis) |
| Azure | App Service B1 ~US$13/mês | PostgreSQL Flexible B1ms ~US$12-15/mês | Compute grátis (F1) existe mas é bem limitado; banco grátis expira em 12 meses |
| GCP | Cloud Run - free tier permanente cobre nosso volume | Cloud SQL menor instância ~US$30/mês (ou manter um Postgres externo pago, mais barato) | Cloud Run é o free tier mais robusto dos grandes clouds - sem prazo de validade |
| Heroku | Eco Dyno US$5/mês | Postgres Essential-0 US$5/mês | Sem free tier desde 2022; conexões do Essential-0 sobram pra 2 usuários |
| DigitalOcean | App Platform Basic US$5/mês, ou Droplet US$6-12/mês | Managed Postgres ~US$15/mês (com backup) | Caminho atual (autohospedado) |
| Neon | - | Free (0,5GB) ou Launch pago (~US$15, uso real) | Descartado por ora pelo motivo do item 1 - reavaliar se decidirmos por banco gerenciado de terceiro no futuro |

Planilha detalhada com os totais calculados: `docs/custos_hospedagem.xlsx`.

**Critério de quando migrar:** quando o número de usuários/tráfego crescer a
ponto de justificar SLA e suporte formal de um provedor grande, ou quando o
autohospedado virar fardo operacional maior que o custo de um banco
gerenciado de confiança.

## 7. Orçamento consolidado (estado-alvo, hoje)

| Item | Custo/mês |
|---|---|
| Droplet DigitalOcean 2GB (app + Postgres + observabilidade) | ~US$12 |
| Backup no Cloudflare R2 | US$0 (dentro do free tier) |
| Grafana + Prometheus (ou SigNoz) | US$0 (autohospedado) |
| SonarQube local sob demanda | US$0 |
| Semgrep em CI | US$0 |
| RabbitMQ (quando implementado) | US$0 (autohospedado, mesmo Droplet) |
| **Total** | **~US$12/mês**, dentro do teto de US$20 |

---

## Fontes consultadas nesta rodada de pesquisa

- [Free Hosting for Spring Boot: Best Options in 2026](https://docs.bswen.com/blog/2026-02-28-springboot-free-hosting/)
- [AWS Free Tier Complete Guide 2026](https://agentdeals.dev/aws-free-tier-2026)
- [Google Cloud Free Tier Services And Limits](https://aatayyab.wordpress.com/2026/06/26/google-cloud-free-tier-services-and-limits/)
- [Azure Free Tier Complete Guide 2026](https://agentdeals.dev/azure-free-tier-2026)
- [Removal of Heroku Free Product Plans FAQ](https://help.heroku.com/RSBRUH58/removal-of-heroku-free-product-plans-faq)
- [DigitalOcean Managed PostgreSQL Review & Pricing 2026](https://infratally.com/articles/digitalocean-managed-postgres-deep-dive/)
- [App Platform Pricing | DigitalOcean](https://www.digitalocean.com/pricing/app-platform)
- [Neon Serverless Postgres Pricing 2026](https://vela.simplyblock.io/articles/neon-serverless-postgres-pricing-2026/)
- [Grafana Alternatives 2026: SaaS & Self-Hosted | SigNoz](https://signoz.io/blog/grafana-alternatives/)
- [Semgrep vs CodeQL vs SonarQube: Static Analysis Tools Compared](https://reintech.io/blog/semgrep-vs-codeql-vs-sonarqube-static-analysis-comparison)
- [14 Free and Open-Source SonarQube Alternatives (2026)](https://www.codeant.ai/blogs/free-open-source-sonarqube-alternatives)
