# Observabilidade (métricas, logs e tracing)

Stack self-hosted na própria instância EC2, via Docker Compose, ligada ao
backend Spring Boot. Cobre os três sinais sobre os endpoints já criados
(`/api/v1/**`): **métricas** (RED por rota), **logs** (agregados e
correlacionados) e **tracing** distribuído.

## Peças

| Peça | Papel | Como recebe os dados |
|------|-------|----------------------|
| **Micrometer + Actuator** (no app) | Produz métricas e spans | Expõe `/actuator/prometheus`; exporta spans via OTLP |
| **Prometheus** | Armazena métricas | Faz *scrape* de `app:8080/actuator/prometheus` a cada 15s |
| **Tempo** | Armazena traces | Recebe OTLP do app em `tempo:4318` |
| **Loki** | Armazena logs | Recebe do Alloy |
| **Alloy** | Coleta logs dos containers | Lê o socket do Docker e envia ao Loki |
| **Grafana** | Painéis e correlação | Lê Prometheus, Loki e Tempo |

## O que o app passou a fazer

- **Métricas** em `/actuator/prometheus` (liberado no `SecurityConfig`), incluindo
  `http_server_requests` com histograma de latência — é a base do painel RED
  (taxa, erros, latência p50/p95/p99 por `uri`, `method`, `status`).
- **Tracing**: cada request vira um trace exportado ao Tempo (OTLP). O `traceId`
  e o `spanId` entram automaticamente no log (correlação log↔trace no Grafana).
- **Sampling**: controlado por `MANAGEMENT_TRACING_SAMPLING`. O compose **base**
  mantém em `0` (sem stack, nada é exportado — zero ruído); o
  `docker-compose.observability.yml` liga (1.0 por padrão). Métricas independem
  disso (são *pull*).

## Como subir

Na instância, com o `.env` já preenchido (inclusive `GRAFANA_ADMIN_PASSWORD`):

```bash
cd ~/taniamelo-advocacia-backend
docker compose \
  -f docker-compose.yml \
  -f docker-compose.override.yml \
  -f docker-compose.observability.yml \
  --profile full up -d --build
```

Para subir só o app (sem observabilidade), continue usando o
`scripts/restart-app-docker.sh` de sempre — a stack **não** entra no deploy
automático de propósito (é pesada para uma instância pequena).

## Acessos

| Serviço | Porta | Exposição |
|---------|-------|-----------|
| Grafana | 3000 | Publicada — **abra no Security Group só para o seu IP**, ou use túnel SSH |
| Prometheus | 9090 | `127.0.0.1` (loopback) — via Grafana ou túnel SSH |
| Loki | 3100 | `127.0.0.1` |
| Tempo / Alloy | — | Só rede interna do compose |

Túnel SSH (mais seguro que abrir portas):

```bash
ssh -L 3000:localhost:3000 -L 9090:localhost:9090 ec2-user@<host>
# Grafana em http://localhost:3000  (login: admin / GRAFANA_ADMIN_PASSWORD)
```

Datasources (Prometheus, Loki, Tempo) e o dashboard **“Law Firm — Endpoints
(RED)”** já sobem provisionados; não precisa configurar nada na UI.

## Segurança

- **Grafana**: senha do admin obrigatória via `GRAFANA_ADMIN_PASSWORD` (o `up`
  falha se estiver vazia). Não deixe o default.
- **`/actuator/prometheus`** é liberado sem auth (o Prometheus faz scrape na rede
  interna). A porta 8080 já é restrita ao seu IP no Security Group; ainda assim,
  não exponha a métrica publicamente.
- **Alloy** monta o socket do Docker (`:ro`) para ler logs — isso dá acesso amplo
  ao daemon. Aceitável numa instância dev de dono único; não replique num host
  compartilhado.

## Recursos e retenção (instância pequena)

Rodar app + Postgres + 5 serviços de observabilidade numa EC2 pequena pesa.
Retenções já vêm curtas: **Prometheus 15d**, **Loki 7d**, **Tempo 48h**. Se a
instância sofrer, o caminho é: subir o tipo da instância, encurtar retenção, ou
mover a stack para Grafana Cloud / Amazon Managed (o app não muda — só o destino
do scrape/OTLP).

## Desligar

```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml \
  -f docker-compose.observability.yml --profile full down
# (sem -v: preserva os dados; com -v: apaga métricas/logs/traces)
```
