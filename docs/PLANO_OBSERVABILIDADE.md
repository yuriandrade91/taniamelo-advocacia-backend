# Observabilidade — clientes e agenda

Escopo deliberado: **só as rotas de `/clients` e `/appointments`**, as mesmas que a
suíte de contrato cobre. Não é observabilidade do sistema inteiro; é enxergar as
duas features que já estão em uso.

Ponto de partida (10/09/2026): `management.endpoints.web.exposure.include:
health,info`. Nenhuma métrica, nenhum trace, log em texto puro sem id de
requisição. Hoje, se o escritório disser "a tela de clientes travou às 14h20",
não há como responder.

---

## O que se quer poder responder

Um plano de observabilidade que não parte de perguntas vira painel bonito que
ninguém abre. Estas são as perguntas, e cada fase abaixo existe para responder
alguma delas:

1. A API está de pé e respondendo? *(hoje: sim, `/actuator/health`)*
2. Qual rota está lenta, e desde quando?
3. Quantos erros por rota, e são erros de quem chama (4xx) ou nossos (5xx)?
4. Um erro que o escritório reportou — qual requisição foi, e o que ela fez?
5. O aviso de conflito de horário está sendo usado, ou as pessoas agendam por cima?
6. Quantos clientes são excluídos por semana, e quantos são restaurados?

As três primeiras são operação. As três últimas são produto — e são as que
justificam instrumentar o domínio, não só o HTTP.

---

## Fase 1 — Métricas HTTP *(meio dia)*

Resolve as perguntas 1 a 3, quase sem escrever código.

1. Adicionar ao `pom.xml`:
   ```xml
   <dependency>
     <groupId>io.micrometer</groupId>
     <artifactId>micrometer-registry-prometheus</artifactId>
   </dependency>
   ```
2. Expor o endpoint:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus
   ```

O Actuator passa a publicar `http.server.requests` sozinho: contagem, soma e
máximo por **rota, método e status**. O Spring usa o *template* da rota
(`/api/v1/clients/{id}`), não a URL concreta — então o UUID não vira uma série
nova a cada cliente. Isso é o que evita explosão de cardinalidade, e é de graça.

Para ter percentis (a pergunta 2 de verdade, não a média):

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      # Só nas rotas que nos interessam: histograma é caro em memória,
      # e não queremos pagar por /actuator nem pelo Swagger.
      slo:
        http.server.requests: 100ms, 300ms, 1s, 3s
```

### O detalhe que decide a Fase 1: quem raspa as métricas

`/actuator/prometheus` cai em `anyRequest().authenticated()`, ou seja, exige JWT.
Prometheus não tem JWT. As três saídas, em ordem de preferência:

- **Não expor na internet.** Prometheus roda na mesma instância (ou na mesma VPC)
  e raspa `localhost:8080`; a porta 8080 externa continua servindo só a API.
  Requer decidir onde o Prometheus vive.
- **Porta separada** (`management.server.port: 8081`), fechada no Security Group.
  Uma linha de configuração e resolve; é o caminho mais rápido.
- **Liberar com token estático** no `SecurityConfig`. Funciona, mas cria uma
  segunda forma de autenticação para manter.

> **Não faça `permitAll` em `/actuator/prometheus`.** Ele publica a lista de rotas,
> volume de requisições, versões de biblioteca e detalhes de JVM. É reconhecimento
> pronto para quem estiver olhando.

**Critério de saída:** `curl localhost:8080/actuator/prometheus | grep http_server_requests`
mostra as rotas de clientes e agenda, separadas por status.

---

## Fase 2 — Log com identidade *(meio dia)*

Resolve a pergunta 4, que é a que mais dói no dia a dia.

Hoje o log é texto e não diz **quem** fez, **em qual escritório**, nem **em qual
requisição**. Um erro reportado pelo escritório não é rastreável até a linha.

1. Filtro que popula o MDC no início de cada requisição, e limpa no fim — mesmo
   lugar e mesmo cuidado do `TenantResolutionFilter`, que já faz isso com o
   `TenantContext`:
   - `requestId` (gerado, ou o `X-Request-Id` que vier)
   - `tenant` (do `TenantContext`, já resolvido)
   - `userId` (do `CurrentUser`)
2. Devolver o `requestId` no header da resposta. É o que o escritório pode
   copiar de um print e mandar junto do "deu erro".
3. Log em JSON, para o campo virar filtro em vez de `grep`.

**O que NÃO logar:** `inssPassword`, `cpf` inteiro, corpo de requisição. Log é
copiado, exportado e lido por gente que não deveria ver ficha de cliente.

**Critério de saída:** dado um `requestId`, achar todas as linhas daquela
requisição, com tenant e usuário.

---

## Fase 3 — Métricas de domínio *(meio dia)*

Resolve as perguntas 5 e 6. É o que a métrica genérica de HTTP não alcança:
`POST /appointments` com status 201 não diz se havia conflito.

Contadores no `AppointmentService` e no `ClientServiceImpl`, onde a decisão
acontece:

| Métrica | Responde |
|---|---|
| `appointments.created` (tag `type`) | mix real de entrevista/perícia/audiência |
| `appointments.cancelled` / `.completed` | quanto da agenda vira nada |
| `appointments.deleted` / `.restored` | se a exclusão está sendo usada por engano |
| `appointments.conflicts.detected` | quantas vezes o aviso apareceu |
| `appointments.created.with_conflict` | **e quantas a pessoa agendou mesmo assim** |
| `clients.created` (tag `clientType`) | entrada de lead × cliente |
| `clients.deleted` / `clients.restored` | idem, do lado de clientes |

O par *conflito detectado × agendado mesmo assim* é o mais valioso da lista: se
quase todo aviso é ignorado, o aviso está errado (ou é ruído) e vale rever a
regra. Sem medir, isso é palpite.

**Regra de tag:** só valores de cardinalidade baixa — `type`, `status`,
`clientType`, `tenant`. **Nunca** `clientId`, `appointmentId` ou `userId`: cada
valor novo é uma série temporal nova, e é assim que se derruba um Prometheus.

---

## Fase 4 — Alertas *(meio dia)*

Métrica que ninguém olha é log com gráfico. O corte é: **isto acorda alguém?**

| Alerta | Limite sugerido | Por quê |
|---|---|---|
| `/health` fora do ar | 2 min | óbvio |
| 5xx em `/clients` ou `/appointments` | > 1% em 5 min | erro nosso, não de quem chama |
| p95 de `GET /clients` | > 2 s por 10 min | é a rota mais chamada da aplicação |
| Falha de migration no boot | qualquer | sobe sem schema e falha adiante |

Deliberadamente **não** alertar: pico de 4xx (é o cliente errando, e a suíte já
garante que a API responde certo), uso de CPU/memória sozinho (sintoma, não
problema).

---

## O que NÃO fazer agora

- **Não instale Grafana + Prometheus + Loki na instância.** É uma `t4g` pequena
  que já roda a aplicação e o banco. Métrica exposta e log estruturado valem por
  si; a coleta pode ser um Prometheus gerenciado ou um contêiner à parte quando
  houver o que olhar.
- **Não adicione tracing distribuído (OTLP/Zipkin) ainda.** É um monólito com um
  banco: o trace mostraria uma chamada só. O `requestId` no log resolve o mesmo
  problema por muito menos.
- **Não instrumente a aplicação inteira.** Este plano cobre clientes e agenda
  porque são as features em uso e testadas. Instrumentar o que ninguém usa gera
  série temporal para ninguém ler.

---

## Ordem e custo

| Fase | Custo | Depende de |
|---|---|---|
| 1 — métricas HTTP | meio dia | decidir quem raspa (a decisão, não o código) |
| 2 — log com identidade | meio dia | nada |
| 3 — métricas de domínio | meio dia | fase 1 |
| 4 — alertas | meio dia | fases 1 e 3, e ter onde coletar |

Fases 1 e 2 são independentes e podem sair no mesmo dia. **A fase 2 é a que dá
retorno mais rápido**, porque resolve o problema que já existe hoje: erro
reportado que ninguém consegue rastrear.
