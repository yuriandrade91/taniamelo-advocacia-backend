# Central de Notificações — plano de ação (mensageria, agendamento e tempo real)

Documento de decisão para o item "central de notificações". Responde à pergunta
**RabbitMQ ou Kafka?**, define a arquitetura e lista o que falta implementar,
por fases. Escrito antes de qualquer código, para alinharmos o desenho.

## 1. Os cenários reais (e o que cada um exige)

| # | Exemplo | Natureza | Gatilho |
|---|---|---|---|
| A | "O cliente Xpto foi cadastrado e a 1ª de 12 parcelas vence em 02/11/2026." | **Evento de domínio** | Acontece no momento de uma ação (POST cliente / criação de parcela) |
| B | "O repasse do cliente Xpp vence hoje!" | **Baseado em tempo** | Uma varredura diária encontra parcelas com `dueDate = hoje` |
| C | "O cliente Xpto está com pagamento atrasado há 12 dias." | **Baseado em tempo** | Varredura diária: `status=PENDENTE` e `dueDate < hoje - N` |

Conclusão-chave: **só o cenário A é um evento de mensageria de verdade.** B e C
não nascem de um evento — nascem de uma **regra temporal** que ninguém
"dispara": alguém precisa *procurar* essas parcelas todo dia. Isso é trabalho de
**agendador (scheduler)**, não de broker. O broker entra depois, como transporte
da notificação gerada, não como origem dela.

## 2. RabbitMQ ou Kafka? → **RabbitMQ** (e nem os dois, por ora)

| Critério | RabbitMQ | Kafka |
|---|---|---|
| Modelo | Fila de tarefas / mensagens, roteamento, retry, dead-letter | Log de eventos append-only, streaming de alto volume, replay |
| Volume que ele brilha | Baixo/médio (milhares/dia) | Alto (milhões/s), múltiplos consumidores independentes |
| Complexidade operacional | Baixa (1 container, console web) | Alta (brokers, partições, ZooKeeper/KRaft, retenção) |
| Encaixe no nosso caso | **Direto** — "envie esta notificação" é uma tarefa | Sobra capacidade que não usamos e cobra em complexidade |

**Recomendação: RabbitMQ.** O caso de uso é notificação transacional de baixo
volume para um (em breve poucos) escritório(s) — o ponto forte do RabbitMQ
(work queue com retry e dead-letter para reentrega quando o e-mail/WhatsApp
falha). Kafka resolve um problema que não temos (streaming massivo, replay,
vários consumidores analíticos) e cobраria em operação — especialmente contra a
decisão já registrada em `ARCHITECTURE.md` de **não** adicionar infra
distribuída sem ganho real.

**Quando reconsiderar Kafka (gatilhos explícitos):** se surgir (a) um stream de
eventos de auditoria/analytics consumido por vários serviços, (b) necessidade de
*replay* histórico de eventos, ou (c) volume de eventos que o RabbitMQ não
acompanhe. Nada disso está no horizonte. Não usar os dois agora.

> Observação importante: para o volume atual, dá até para **começar sem broker
> nenhum** (Spring Application Events em processo + tabela `notifications`) e
> introduzir o RabbitMQ na Fase 2, quando o envio de e-mail/WhatsApp for
> desacoplado num worker. O desenho abaixo já deixa essa costura pronta.

## 3. Arquitetura proposta

```
                    ┌─────────────────────────────────────────────┐
   POST /clients ──▶│ ClientService  ──emite──▶ Domain Event       │
   (cenário A)      │                          (ClienteCadastrado) │
                    └───────────────┬─────────────────────────────┘
                                    │ (outbox: grava na mesma transação)
                                    ▼
   @Scheduled diário ─────▶ NotificationService ──▶ tabela `notifications`
   (cenários B e C)                    │                    (inbox in-app)
                                       │ publica
                                       ▼
                                  RabbitMQ (fila "notifications")
                                       │
                                       ▼
                             NotificationWorker (consumer)
                              ├─▶ E-mail (SES/SMTP)
                              ├─▶ WhatsApp (provedor)  [item 4]
                              └─▶ SSE/WebSocket ──▶ "sininho" no frontend em tempo real
```

Peças:

1. **Evento de domínio + Outbox (cenário A).** O `ClientService` grava, na mesma
   transação do cadastro, uma linha numa tabela `outbox` (padrão Transactional
   Outbox) — garante que a notificação não se perde se o broker estiver fora no
   instante do commit. Um publisher lê a outbox e publica no RabbitMQ.
2. **Scheduler (cenários B e C).** `@Scheduled` (ou Quartz, se precisar de
   cluster) roda 1x/dia, consulta parcelas vencendo/atrasadas e cria
   notificações. Idempotência por chave (`payment_id + tipo + data`) para não
   notificar a mesma parcela duas vezes.
3. **Tabela `notifications`** — inbox in-app: `id, tenant_id, user_id/office,
   type, title, body, entity_ref, read_at, created_at`. É a fonte do "sininho".
4. **Worker/consumer** — consome a fila e entrega nos canais (e-mail, WhatsApp),
   com **dead-letter queue** para reentrega em falha.
5. **Tempo real (item 4) — SSE vs WebSocket.** Para um sininho (fluxo
   servidor→cliente, unidirecional) **SSE (`text/event-stream`) é o suficiente e
   mais simples**: reconexão automática, roda sobre HTTP/1.1 comum, sem
   handshake especial. **WebSocket** só se surgir necessidade bidirecional (chat,
   presença). Recomendo **SSE agora**; endpoint `GET /api/v1/notifications/stream`
   emitindo eventos do usuário autenticado.

## 4. Multi-tenant desde o início

Toda notificação carrega `tenant_id` (ver ADR multi-tenant). Fila pode ser única
com o `tenant_id` no payload; o worker e o stream SSE **sempre** filtram pelo
tenant do destinatário — nunca entregar notificação de um escritório a usuário de
outro.

## 5. Plano de ação por fases

**Fase 1 — Fundação in-process (sem broker), entrega valor rápido**
- Migration: tabelas `notifications` e `outbox`.
- `NotificationService` + entidade/repo; endpoints `GET /notifications` (inbox,
  paginado, no envelope padrão) e `PATCH /notifications/{id}/read`.
- Cenário A via Spring `ApplicationEventPublisher` (`@TransactionalEventListener`
  AFTER_COMMIT) gravando na `notifications`.
- Cenários B e C via `@Scheduled` diário (idempotente).
- SSE `GET /notifications/stream` para o sininho.

**Fase 2 — Desacoplar entrega com RabbitMQ**
- `spring-boot-starter-amqp`; serviço `db` do compose ganha um `rabbitmq`.
- Publisher lê a `outbox` e publica; `NotificationWorker` consome e envia
  e-mail/WhatsApp; configurar exchange, fila e **DLQ**.
- Retry com backoff + dead-letter para falha de canal externo.

**Fase 3 — Canais externos (item 4)**
- E-mail via Amazon SES (já vamos estar na AWS) ou SMTP.
- WhatsApp via provedor (Meta Cloud API / Twilio) — atrás de uma interface
  `NotificationChannel`, no mesmo espírito do `FileStorageService` (troca de
  provedor sem tocar no núcleo).

**Fase 4 — Só se os gatilhos de Kafka aparecerem** (ver §2). Não planejar agora.

## 6. Dependências e impacto no que já existe

- Nenhuma mudança quebra o que roda hoje: Fase 1 é aditiva (novas tabelas/rotas).
- `@EnableScheduling` na aplicação (uma anotação) para os cenários B/C.
- RabbitMQ só entra na Fase 2, como o Postgres já entra hoje no compose.
- Alinhado com `ARCHITECTURE.md` §2 (módulo "Financeiro"/"Notificações" como
  costura de futuro serviço) e com o Roadmap (Fase C: "Notificações e-mail/
  WhatsApp em mudança de situação, parcela vencendo e prazos").

## 7. Decisão a confirmar antes de implementar

1. Começar pela **Fase 1 (sem broker)** e subir o RabbitMQ na Fase 2? (recomendado)
2. Canal externo prioritário: **e-mail primeiro** ou **WhatsApp primeiro**?
3. SSE agora (recomendado) e WebSocket só se precisar — ok?
