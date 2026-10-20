# Prometheus na prática — guia e queries prontas

Para quem nunca usou. Primeiro o mínimo de conceito para ler uma query; depois as
queries que respondem às perguntas do dia a dia deste backend. Todas foram
testadas contra as métricas reais do app (`/actuator/prometheus`).

Como subir a stack: [OBSERVABILIDADE.md](OBSERVABILIDADE.md).

---

## 1. Onde digitar as queries

| Onde | URL | Para quê |
|---|---|---|
| **Grafana → Explore** | http://localhost:3000/explore | O melhor lugar para aprender: autocompleta métricas e labels, e mostra gráfico e tabela. Escolha o datasource **Prometheus**. |
| Prometheus | http://localhost:9090/query | A UI crua. Útil para ver **Status → Targets** (se o app está sendo raspado). |
| Dashboard pronto | Grafana → Dashboards → **Law Firm — Endpoints (RED)** | Os gráficos principais já montados. |

No Explore, alterne entre **Table** (valor de agora) e **Graph** (evolução no
tempo). O seletor de período no topo (ex.: "Last 1 hour") vale para o gráfico.

---

## 2. Os conceitos que bastam

### O que o Prometheus guarda

A cada 15 s ele faz um GET em `/actuator/prometheus` e guarda cada número com a
hora. Cada linha é uma **série temporal**: um nome de métrica + um conjunto de
**labels** (etiquetas). Exemplo real:

```
http_server_requests_seconds_count{method="GET", uri="/api/v1/clients", status="200", ...} 1523
```

- **Nome**: `http_server_requests_seconds_count` — quantas requisições HTTP.
- **Labels**: `method`, `uri`, `status`, `outcome`, `exception` — cada combinação
  diferente é uma série separada. `uri` é o *padrão* da rota
  (`/api/v1/clients/{clientId}`), não o id concreto.
- **Valor**: 1523 requisições desde que o app subiu.

Labels que o nosso setup acrescenta e que você vai usar para filtrar:

| Label | Valores | Para quê |
|---|---|---|
| `application` | `law-firm` | Separa este serviço de outros no mesmo Prometheus. |
| `environment` | `dev` (servidor EC2), `local` (app da IDE) | **Filtre sempre por ele** — ver armadilha 6.1. |
| `instance` | `98.82.73.175:8080`, `host.docker.internal:8080`… | De qual máquina veio. |

### Os tipos de métrica (é isto que decide a função a usar)

| Tipo | Como se comporta | Exemplo aqui | Como consultar |
|---|---|---|---|
| **Counter** | Só sobe (zera quando o app reinicia). Nome termina em `_total` ou `_count`/`_sum`. | `http_server_requests_seconds_count`, `logback_events_total` | **Nunca** use o valor cru: use `rate()` ou `increase()`. |
| **Gauge** | Sobe e desce; é o valor de agora. | `jvm_memory_used_bytes`, `hikaricp_connections_active` | Use direto. |
| **Timer / histograma** | Um trio (às vezes quarteto) de séries: `_count` (quantas), `_sum` (tempo total), `_max` (pior caso recente) e `_bucket` (distribuição). | `http_server_requests_seconds_*` | Média = `rate(_sum)/rate(_count)`; percentil = `histogram_quantile()` sobre `_bucket`. |

### A gramática em cinco peças

```promql
# 1. Seletor: escolhe séries pelo nome e filtra por labels
http_server_requests_seconds_count{environment="dev", status="500"}

# Filtros de label:   =  igual   !=  diferente   =~  regex   !~  não casa a regex
http_server_requests_seconds_count{environment="dev", status=~"5..", uri!~"/actuator.*"}

# 2. Janela [5m]: "os últimos 5 minutos de pontos" de cada série (range vector)
http_server_requests_seconds_count{environment="dev"}[5m]

# 3. rate(): quanto o counter subiu POR SEGUNDO, em média, na janela
rate(http_server_requests_seconds_count{environment="dev"}[5m])
#    increase(): quanto subiu NO TOTAL na janela (= rate × duração)
increase(http_server_requests_seconds_count{environment="dev"}[1h])

# 4. Agregação: junta séries. "by" diz o que manter separado
sum(rate(...[5m]))                    # tudo junto: um número só
sum by (uri) (rate(...[5m]))          # um número por endpoint
sum by (uri, status) (rate(...[5m]))  # por endpoint e status

# 5. Aritmética e comparação entre resultados
100 * erros / total                   # porcentagem
... > 0                               # filtra: só mostra o que for maior que zero
topk(10, ...)                         # os 10 maiores
```

Leia uma query **de dentro para fora**: seletor → janela → `rate` → `sum by` →
conta.

---

## 3. Queries prontas

Todas filtram `application="law-firm", environment="dev"`. Para olhar o app
rodando na sua IDE, troque `environment="dev"` por `environment="local"`.

### 3.1 O app está vivo?

```promql
# 1 = o Prometheus conseguiu raspar o app no último ciclo; 0 = caiu ou inacessível
up{job="law-firm", environment="dev"}

# Há quantas horas o app está no ar
process_uptime_seconds{application="law-firm", environment="dev"} / 3600

# Quantas vezes o app reiniciou nas últimas 24h (deploy, crash, OOM)
changes(process_start_time_seconds{application="law-firm", environment="dev"}[24h])
```

### 3.2 Tráfego — quanto estão usando

```promql
# Requisições por segundo, total
sum(rate(http_server_requests_seconds_count{application="law-firm", environment="dev"}[5m]))

# Só a API de negócio (sem health check, scrape do Prometheus e Swagger)
sum(rate(http_server_requests_seconds_count{application="law-firm", environment="dev", uri=~"/api/v1/.*"}[5m]))

# Requisições por segundo, por endpoint (melhor em Graph)
sum by (method, uri) (rate(http_server_requests_seconds_count{application="law-firm", environment="dev"}[5m]))

# Os 10 endpoints mais chamados nas últimas 24h (melhor em Table)
topk(10, sum by (method, uri) (increase(http_server_requests_seconds_count{application="law-firm", environment="dev", uri=~"/api/v1/.*"}[24h])))

# Total de requisições nas últimas 24h
sum(increase(http_server_requests_seconds_count{application="law-firm", environment="dev"}[24h]))
```

### 3.3 Erros — o que está quebrando

```promql
# % de respostas 5xx (erro do servidor). Deveria ficar em 0.
100 * sum(rate(http_server_requests_seconds_count{application="law-firm", environment="dev", status=~"5.."}[5m]))
    / sum(rate(http_server_requests_seconds_count{application="law-firm", environment="dev"}[5m]))

# Quais endpoints deram 5xx nas últimas 24h, e com qual exceção
sum by (method, uri, exception) (increase(http_server_requests_seconds_count{application="law-firm", environment="dev", status=~"5.."}[24h])) > 0

# Distribuição por status nas últimas 24h (200, 201, 400, 404, 500…)
sum by (status) (increase(http_server_requests_seconds_count{application="law-firm", environment="dev"}[24h]))

# Rotas inexistentes (404 de rota): o uri vira "/**"
sum by (method) (increase(http_server_requests_seconds_count{application="law-firm", environment="dev", uri="/**"}[24h]))
```

**4xx não é bug do servidor**: 400 é validação, 404 é recurso inexistente. Mas
um salto repentino de 400 num endpoint costuma ser o frontend mandando algo que
a API não aceita mais.

### 3.4 Segurança — login e acesso negado

```promql
# Resultado dos logins nas últimas 24h: 200 ok, 401 senha errada, 429 bloqueado pelo freio
sum by (status) (increase(http_server_requests_seconds_count{application="law-firm", environment="dev", uri="/api/v1/auth/login"}[24h]))

# 401 (sem login/token inválido), 403 (sem permissão) e 429 (freio) por endpoint, na última hora
sum by (status, uri) (increase(http_server_requests_seconds_count{application="law-firm", environment="dev", status=~"401|403|429"}[1h])) > 0
```

Muitos 401 seguidos no `/auth/login` vindos do nada = alguém testando senhas; o
429 mostra o freio (`LoginThrottleService`) agindo. Muitos 403 num endpoint =
um papel tentando algo que não pode (ou uma tela do frontend mostrando botão que
não devia).

### 3.5 Latência — está lento?

```promql
# Tempo médio por endpoint, em ms, na última hora (só endpoints que foram chamados)
1000 * sum by (uri) (rate(http_server_requests_seconds_sum{application="law-firm", environment="dev", uri=~"/api/v1/.*"}[1h]))
     / sum by (uri) (rate(http_server_requests_seconds_count{application="law-firm", environment="dev", uri=~"/api/v1/.*"}[1h])) > 0

# p95 geral em ms: 95% das requisições terminam abaixo disto
1000 * histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="law-firm", environment="dev"}[5m])))

# p95 por endpoint, em ms
1000 * histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="law-firm", environment="dev", uri=~"/api/v1/.*"}[5m])))

# Os 5 endpoints com pior p99 nas últimas 24h
topk(5, 1000 * histogram_quantile(0.99, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="law-firm", environment="dev", uri=~"/api/v1/.*"}[24h]))))

# Pior requisição recente de cada endpoint, em ms (janela de ~2 min, não é histórico)
1000 * max by (uri) (http_server_requests_seconds_max{application="law-firm", environment="dev"})

# % das requisições respondidas em até 500 ms na última hora (meta de qualidade)
100 * sum(rate(http_server_requests_seconds_bucket{application="law-firm", environment="dev", le="0.5"}[1h]))
    / sum(rate(http_server_requests_seconds_count{application="law-firm", environment="dev"}[1h]))
```

**Média x percentil**: a média esconde os casos ruins. Se 99 requisições levam
50 ms e uma leva 5 s, a média é ~100 ms e parece ótima — o p99 mostra os 5 s.
Para "está lento para o usuário?", olhe o **p95**.

`le` no `_bucket` significa "less or equal": `le="0.5"` conta quantas
requisições terminaram em até 0,5 s. Há ~76 limites gerados automaticamente
(com valores "quebrados", como `0.536870911`), mais os que o `application.yaml`
fixa como metas: `0.05`, `0.1`, `0.2`, `0.3`, `0.5`, `1.0`, `2.0`. Para filtrar
por `le` direto, use um desses sete — os outros não são números redondos.

### 3.6 Banco de dados — pool de conexões e repositórios

```promql
# Conexões em uso agora / máximo do pool (hoje 10)
hikaricp_connections_active{application="law-firm", environment="dev"}
hikaricp_connections_max{application="law-firm", environment="dev"}

# Requisições ESPERANDO conexão. Acima de 0 com frequência = pool pequeno ou query lenta segurando conexão
hikaricp_connections_pending{application="law-firm", environment="dev"}

# Timeouts pegando conexão na última hora (cada um vira erro para o usuário)
increase(hikaricp_connections_timeout_total{application="law-firm", environment="dev"}[1h])

# Tempo médio para conseguir uma conexão, em ms
1000 * rate(hikaricp_connections_acquire_seconds_sum{application="law-firm", environment="dev"}[5m])
     / rate(hikaricp_connections_acquire_seconds_count{application="law-firm", environment="dev"}[5m])

# Os 5 métodos de repositório mais lentos (média em ms) na última hora
topk(5, 1000 * sum by (repository, method) (rate(spring_data_repository_invocations_seconds_sum{application="law-firm", environment="dev"}[1h]))
              / sum by (repository, method) (rate(spring_data_repository_invocations_seconds_count{application="law-firm", environment="dev"}[1h])) > 0)

# Chamadas de repositório que falharam nas últimas 24h
sum by (repository, method) (increase(spring_data_repository_invocations_seconds_count{application="law-firm", environment="dev", state!="SUCCESS"}[24h])) > 0
```

### 3.7 JVM e máquina — memória, CPU, disco

```promql
# % do heap em uso. Perto de 100% e não descendo = vazamento ou heap pequeno
100 * sum(jvm_memory_used_bytes{application="law-firm", environment="dev", area="heap"})
    / sum(jvm_memory_max_bytes{application="law-firm", environment="dev", area="heap"})

# Heap em MB por região (Eden enche e esvazia o tempo todo; Old Gen subindo sem parar é o sinal ruim)
sum by (id) (jvm_memory_used_bytes{application="law-firm", environment="dev", area="heap"}) / 1024 / 1024

# Milissegundos de pausa de GC por segundo (app parado coletando lixo)
1000 * rate(jvm_gc_pause_seconds_sum{application="law-firm", environment="dev"}[5m])

# CPU: do processo do app e da máquina inteira, em %
100 * process_cpu_usage{application="law-firm", environment="dev"}
100 * system_cpu_usage{application="law-firm", environment="dev"}

# Threads vivas
jvm_threads_live_threads{application="law-firm", environment="dev"}

# % do disco usado onde o app roda (a EC2 tem 8 GiB e já encheu uma vez)
100 * (1 - disk_free_bytes{application="law-firm", environment="dev"} / disk_total_bytes{application="law-firm", environment="dev"})
```

### 3.8 Logs — quantos erros o app escreveu

O conteúdo do log fica no Loki; aqui só a **contagem** por nível.

```promql
# Linhas de log ERROR na última hora
increase(logback_events_total{application="law-firm", environment="dev", level="error"}[1h])

# Ritmo de WARN e ERROR por segundo
sum by (level) (rate(logback_events_total{application="law-firm", environment="dev", level=~"warn|error"}[5m]))
```

---

## 4. Um roteiro quando "o sistema está estranho"

1. `up` → o app está no ar? Se 0, o problema é antes de tudo (deploy, crash).
2. `changes(process_start_time_seconds[...])` → reiniciou? Reinício sem deploy costuma ser falta de memória.
3. % de 5xx e "quais endpoints deram 5xx" → algo quebrou? A label `exception` diz qual.
4. p95 por endpoint → está só lento? Em qual rota?
5. `hikaricp_connections_pending` e timeouts → o gargalo é o banco?
6. Heap % e pausa de GC → o gargalo é memória?
7. Com o endpoint e o horário em mãos, vá para os logs (Loki) e traces (Tempo) daquele momento.

---

## 5. Montando um painel no Grafana

1. Acerte a query no **Explore** até o gráfico fazer sentido.
2. Clique em **Add → Add to dashboard** (ou crie um dashboard novo).
3. No painel, em **Standard options → Unit**, escolha a unidade: `ms`, `percent (0-100)`,
   `requests/sec (rps)`, `bytes(IEC)`. Isso formata os números sozinho.
4. Em **Legend**, use `{{uri}}` ou `{{status}}` para a legenda mostrar o label em vez da série inteira.

Os dashboards deste repositório ficam em `observability/grafana/dashboards/` e
sobem provisionados. Um painel criado na UI some se o volume do Grafana for
apagado; para ele virar parte do projeto, exporte o JSON (**Share → Export**) e
salve nessa pasta.

---

## 6. Armadilhas

### 6.1 Sempre filtre por `environment`

O Prometheus local pode ter dados do app da IDE (`local`), do servidor de dev
(`dev`) e de quando o app rodou em container (`instance="app:8080"`). Sem filtro,
um `sum(...)` soma tudo junto e o número não é de lugar nenhum.

### 6.2 `rate()` e `increase()` só em counter

Em gauge (memória, conexões ativas) o resultado não significa nada. Regra de
bolso: terminou em `_total`, `_count`, `_sum` ou `_bucket` → `rate`/`increase`.
Senão, use o valor direto.

### 6.3 A janela tem de caber pelo menos 4 coletas

Com scrape a cada 15 s, `[1m]` é o mínimo e `[5m]` é o confortável. `[30s]`
devolve vazio ou números que pulam.

### 6.4 `NaN` quer dizer "0 dividido por 0"

Média e percentil de um endpoint que não recebeu nenhuma requisição na janela
dão `NaN`. Não é erro: aumente a janela ou acrescente `> 0` no fim para esconder.

### 6.5 Reinício do app zera os counters

`rate` e `increase` sabem lidar com isso, mas olhar o valor cru de um `_count`
depois de um deploy mostra um número pequeno que não é "o total de sempre".

### 6.6 O Prometheus local só sabe desde quando começou a coletar

Uma janela `[24h]` com 20 minutos de dados mostra 20 minutos. E a retenção é de
15 dias (`--storage.tsdb.retention.time` no compose).

### 6.7 `uri` especiais

- `"/**"` — rota que não existe (404 do fallback).
- `"UNKNOWN"` — a requisição foi recusada antes de chegar a um controller (ex.: URL malformada).
- `/actuator/*` — health check e o próprio scrape do Prometheus; inflam a contagem de tráfego, por isso as queries de API filtram `uri=~"/api/v1/.*"`.

### 6.8 `_max` não é o máximo da história

É o pior caso de uma janela curta (~2 minutos) e zera depois. Para "pior caso no
período", use `histogram_quantile(0.99, ...)` com a janela desejada.
