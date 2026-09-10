# Plano de teste na AWS — Clientes e Agenda

Roteiro para validar, na instância de desenvolvimento (EC2), as duas features
que vão subir. Escrito para ser executado por uma pessoa com o Swagger ou o
Postman aberto, na ordem em que está.

**Escopo:** `/api/v1/clients` (e sub-recursos), `/api/v1/appointments` e
`/api/v1/users`. Fora do escopo: `/payments`, `/expenses` e `/revenues`, que não
existem no backend (lacuna B1 do plano de testes).

---

## 0. Antes de subir

Nada abaixo faz sentido sem estes quatro pontos confirmados.

1. **Backup do banco.** A migration V12 mexe em restrição única de `clients`
   (`cpf`, `nit_pis`, `benefit_number` deixam de ser `UNIQUE` de coluna e viram
   índice único parcial). É reversível, mas não é gratuito.
2. **CI verde.** O deploy agora depende dele (`workflow_run` sobre o workflow
   "CI"). Se o job de deploy não disparou, o motivo é esse — e é o
   comportamento desejado.
3. **Qual commit subiu.** `GET /actuator/info` devolve `build.commit`. Se ele
   não bater com o SHA da `develop`, pare aqui: todo o resto do roteiro estaria
   testando outra versão.
   ```bash
   curl -s http://<host>:8080/actuator/info | jq '.build'
   ```
4. **Migrations aplicadas em TODOS os schemas.** O `MultiTenantFlywayMigrator`
   roda por tenant. Confirme nos dois:
   ```sql
   SELECT version, description, success
     FROM tenant_tania.flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
   SELECT version, description, success
     FROM tenant_demo.flyway_schema_history  ORDER BY installed_rank DESC LIMIT 3;
   ```
   Esperado nos dois: **V13** (`appointment history delete trail`) e **V12**
   (`clients soft delete`), `success = true`.

### Preparação da sessão

```bash
export API=http://<host>:8080/api/v1
export TENANT=$(curl -s "$API/tenants/resolve?slug=tania" | jq -r '.data.tenantId')
export TOKEN=$(curl -s -X POST "$API/auth/login" \
  -H "Content-Type: application/json" -H "X-Tenant-Id: $TENANT" \
  -d '{"login":"...","password":"..."}' | jq -r '.data.accessToken')
alias api='curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" -H "Content-Type: application/json"'
```

> Use o **tenant_demo** para tudo que grava. `tenant_tania` é a base real do
> escritório; teste que cria e exclui cliente não tem lugar nela.

---

## 1. Roteiros

Encadeados de propósito. Uma matriz de endpoint por linha verifica que cada rota
responde; um roteiro verifica que elas **funcionam juntas**, que é onde os
defeitos moram. A matriz completa está na seção 2.

### R1 — Sessão e isolamento entre tenants

| # | Passo | Esperado |
|---|---|---|
| 1.1 | `GET /clients` sem `Authorization` | 401 no envelope padrão (não HTML do Spring) |
| 1.2 | `GET /clients` sem `X-Tenant-Id` | 400/404 explícito, nunca dado de outro escritório |
| 1.3 | `GET /clients/{id-do-tania}` com o tenant **demo** | **404** |
| 1.4 | `POST /auth/refresh` e repetir 1.1 com o token novo | 200 |
| 1.5 | `POST /auth/logout` e repetir | 401 |

O passo **1.3 é o mais importante do roteiro inteiro**. Vazamento entre tenants
é o único defeito desta lista que não tem conserto depois de acontecer.

### R2 — Ciclo de vida do cliente

| # | Passo | Esperado |
|---|---|---|
| 2.1 | `POST /clients` com CPF válido | 201, `Location`, `clientType` = `Potencial` |
| 2.2 | `POST /clients` repetindo o CPF | 400/409 dizendo qual campo repetiu |
| 2.3 | `GET /clients?searchTerm=<nome parcial>` | encontra sem depender de acento/caixa |
| 2.4 | `GET /clients?clientType=POTENCIAL` e `?clientType=Potencial` | mesmo resultado (nome da constante **ou** label) |
| 2.5 | `GET /clients?clientType=Verificado&clientType=Potencial` | os dois tipos |
| 2.6 | `GET /clients?clientType=Inexistente` | 400 nomeando o campo e os valores aceitos |
| 2.7 | `PATCH /clients/{id}` mudando `situation` | 200 com mensagem em PT-BR concordando em gênero/número |
| 2.8 | `GET /clients/{id}/situation-history` | entrada com **`previousSituation` e `currentSituation`** |
| 2.9 | `PUT /clients/{id}` | substituição completa; campo omitido é apagado (é o contrato) |
| 2.10 | `DELETE /clients/{id}` | 200 com mensagem |
| 2.11 | `GET /clients/{id}` logo depois | **404** |
| 2.12 | `GET /clients` | o excluído **não** aparece |
| 2.13 | `PATCH /clients/{id}/restore` | 200 |
| 2.14 | `GET /clients/{id}` | **200, ficha inteira de volta** |
| 2.15 | `PATCH /clients/{id}/restore` de novo | 200, sem alterar nada (idempotente) |
| 2.16 | `PATCH /clients/{uuid-que-não-existe}/restore` | 404 |

### R3 — Sub-recursos do cliente

| # | Passo | Esperado |
|---|---|---|
| 3.1 | `POST /clients/{id}/addresses` (primeiro endereço) | 201, `isPrimary = true` automático |
| 3.2 | `POST /clients/{id}/addresses` com `isPrimary=true` | 201; o anterior deixa de ser principal |
| 3.3 | `GET /clients/{id}/addresses` | **exatamente um** principal, e ele vem primeiro |
| 3.4 | `POST /clients/{id}/addresses/batch` com 3 endereços | 201 com os 3, na ordem enviada |
| 3.5 | `POST .../batch` com o 2º inválido (sem `street`) | 400 e **nenhum** endereço gravado |
| 3.6 | `POST .../batch` com lista vazia | 400 |
| 3.7 | `POST .../batch` com 11 itens | 400 |
| 3.8 | `DELETE` do endereço principal, havendo outros | o mais antigo restante vira principal |
| 3.9 | `POST` + `PUT` de entrevista; `GET` da lista | ordenada por `occurredAt` |
| 3.10 | `PUT /personal-data` e `/professional-data` | campo não enviado não é apagado |
| 3.11 | Upload de documento (multipart, `documentType` presente) | 201 |
| 3.12 | Upload com MIME não permitido | 415 |
| 3.13 | Upload > 10 MB | 413 |
| 3.14 | `PATCH /simulations/{id}/principal` | desmarca a anterior |
| 3.15 | `GET /files/{id}/download` | mesmos bytes que subiram |
| 3.16 | Qualquer sub-recurso com id de cliente de **outro tenant** | 404 |
| 3.17 | `POST /clients/{id}/payments` | nasce `PENDENTE`, ignora `paidDate` |
| 3.18 | `PATCH` do pagamento para `PAGO` sem data | assume hoje |
| 3.19 | `PATCH` de volta para `PENDENTE` | limpa `paidDate` |
| 3.20 | `POST` de pagamento com `amount = 0` | 400 |

### R4 — Ciclo do compromisso

| # | Passo | Esperado |
|---|---|---|
| 4.1 | `POST /appointments` com `endAt` anterior ao `startAt` | 400 (`@AssertTrue`) |
| 4.2 | `POST` com data no passado, sem ciência | 422 `PAST_DATE_NOT_CONFIRMED` |
| 4.3 | `POST` com data no passado + ciência | 201, e `ACKNOWLEDGED` aparece no histórico |
| 4.4 | `POST` normal com `clientId` | 201; **`clientName` vem preenchido com o nome do cliente** |
| 4.5 | `POST` sem `clientId`, só `clientName` | 201, nome livre preservado |
| 4.6 | `POST` com `clientId` inexistente | 404 |
| 4.7 | `GET /appointments/conflicts?startAt=&endAt=` na mesma faixa | devolve o de 4.4 |
| 4.8 | `conflicts` com faixa que só encosta (14h-15h vs 15h-16h) | **lista vazia** |
| 4.9 | `conflicts` com `excludeId` do próprio | não conflita consigo mesmo |
| 4.10 | `conflicts` com janela invertida ou ausente | lista vazia, **não** 400 |
| 4.11 | `conflicts` depois de cancelar o conflitante | lista vazia |
| 4.12 | `PUT /{id}` sem `justification` | 400 |
| 4.13 | `PUT /{id}` com justificativa | 200; `EDITED` no histórico |
| 4.14 | `PATCH /{id}/cancel` sem justificativa | 400 |
| 4.15 | `PATCH /{id}/cancel` com justificativa | 200; `CANCELLED` no histórico |
| 4.16 | **`PUT /{id}` num cancelado** | **422 `OPERATION_NOT_ALLOWED`, mensagem citando "cancelado"** |
| 4.17 | `PATCH /{id}/complete` num cancelado | 422 |
| 4.18 | `PATCH /{id}/complete` num agendado, depois `PUT` | **422** (concluído não é editável) |
| 4.19 | `DELETE /{id}` | 204 |
| 4.20 | `GET /{id}/history` do excluído | 404 (some da API) |
| 4.21 | `PATCH /{id}/restore` | **200 com o compromisso de volta** |
| 4.22 | `GET /{id}/history` | **`DELETED` e `RESTORED` registrados**, com `changedByUserId` |
| 4.23 | `PATCH /{id}/restore` de novo | 200 sem novo registro na trilha |
| 4.24 | `GET /appointments/summary?year=` | conta só pendentes; criar soma, cancelar/concluir/excluir subtrai |
| 4.25 | `GET /appointments?year=&year=&month=` | aceita valor repetido no mesmo parâmetro |
| 4.26 | `GET /appointments?from=&to=` | só vale quando não há ano/mês |

### R5 — Usuários e autoria

| # | Passo | Esperado |
|---|---|---|
| 5.1 | `GET /users` | lista ordenada por nome, **sem `email` e sem `passwordHash`** |
| 5.2 | `GET /users` | só ativos |
| 5.3 | `GET /users?includeInactive=true` | inclui desativados |
| 5.4 | Cruzar `changedByUserId` de R2.8 com a lista | resolve para um nome |
| 5.5 | `GET /users` sem token | 401 |

### R6 — As regressões que as mudanças de hoje tornam possíveis

Estas não estão nos roteiros acima porque não são fluxo: são a pergunta "o que
esta mudança pode ter quebrado em outro lugar".

| # | Verificação | Por que |
|---|---|---|
| 6.1 | Excluir cliente e **cadastrar outro com o mesmo CPF** → 201 | O `UNIQUE` de coluna virou índice parcial na V12. Se a migration falhou pela metade, isto dá **500** (unique violation) em vez de cadastrar. |
| 6.2 | Excluir cliente → `GET /clients/{id}/addresses`, `/interviews`, `/payments`, `/situation-history` → 404, e as linhas **continuam no banco** | A exclusão era física com `ON DELETE CASCADE`. Confirme por SQL: `SELECT count(*) FROM tenant_demo.client_addresses WHERE client_id = '<id>'` deve ser > 0. |
| 6.3 | Compromisso com cliente vinculado → excluir o cliente → `GET /appointments/{id}` ainda mostra o **nome** | É o retrato gravado em `client_name`. Antes o compromisso ficava sem cliente e sem nome. |
| 6.4 | Renomear cliente (`PUT /clients/{id}`) → `GET /appointments/{id}` mostra o **nome novo** | O retrato não pode ganhar do nome atual enquanto o vínculo resolve. |
| 6.5 | `GET /clients?pageSize=200` com o banco semeado | Tempo de resposta e nenhum N+1 no log de SQL |
| 6.6 | Upload de arquivo com `APP_STORAGE_TYPE=s3` | O bucket recebe; o `GET /download` devolve os mesmos bytes |
| 6.7 | Swagger UI: "Try it out" em `/tenants/current`, `/users`, `/clients/{id}/restore`, `/appointments/{id}/restore` | Anexa o Bearer token e responde 200 (foi o defeito corrigido em `/tenants/current`) |

---

## 2. Matriz de endpoints

Marque conforme executa. Todo endpoint tem ao menos um caso feliz e um negativo
— endpoint só com caso feliz testado é endpoint não testado.

### Clientes

| Método | Rota | Feliz | Negativo | Roteiro |
|---|---|---|---|---|
| POST | `/clients` | 201 | CPF inválido/repetido → 400 | R2.1-2.2 |
| GET | `/clients` | 200 paginado | enum inválido → 400 | R2.3-2.6 |
| GET | `/clients/{id}` | 200 | inexistente/excluído → 404 | R2.11 |
| PUT | `/clients/{id}` | 200 | obrigatório ausente → 400 | R2.9 |
| PATCH | `/clients/{id}` | 200 | valor de enum inválido → 400 | R2.7 |
| DELETE | `/clients/{id}` | 200 | id inexistente → 404 | R2.10 |
| PATCH | `/clients/{id}/restore` | 200 | id inexistente → 404 | R2.13-2.16 |
| GET | `/clients/{id}/situation-history` | 200 com `previousSituation` | outro tenant → 404 | R2.8 |
| GET/PUT | `/clients/{id}/personal-data` | 200 | outro tenant → 404 | R3.10 |
| GET/PUT | `/clients/{id}/professional-data` | 200 | outro tenant → 404 | R3.10 |
| POST | `/clients/{id}/addresses` | 201 | `street` ausente → 400 | R3.1-3.2 |
| POST | `/clients/{id}/addresses/batch` | 201 | vazia / >10 / item inválido → 400 | R3.4-3.7 |
| GET | `/clients/{id}/addresses` | 200, principal primeiro | — | R3.3 |
| GET/PUT/DELETE | `/clients/{id}/addresses/{addressId}` | 200 / 200 / 204 | id de outro cliente → 404 | R3.8 |
| POST/GET/PUT/DELETE | `/clients/{id}/interviews[/{id}]` | 201/200/200/204 | `content` vazio → 400 | R3.9 |
| POST/GET/PATCH/DELETE | `/clients/{id}/payments[/{id}]` | 201/200/200/204 | `amount ≤ 0` → 400 | R3.17-3.20 |
| POST/GET | `/clients/{id}/files/documents` | 201/200 | MIME/tamanho → 415/413 | R3.11-3.13 |
| GET/PATCH | `/clients/{id}/files/documents/{fileId}` | 200 | outro tenant → 404 | R3.16 |
| POST/GET | `/clients/{id}/files/simulations` | 201/200 | — | R3.14 |
| GET/PATCH | `/clients/{id}/files/simulations/{fileId}` | 200 | — | R3.14 |
| PATCH | `/clients/{id}/files/simulations/{fileId}/principal` | 200 | — | R3.14 |
| GET | `/clients/{id}/files/{fileId}/download` | 200, bytes iguais | outro tenant → 404 | R3.15 |
| DELETE | `/clients/{id}/files/{fileId}` | 204 | inexistente → 404 | — |

### Agenda

| Método | Rota | Feliz | Negativo | Roteiro |
|---|---|---|---|---|
| POST | `/appointments` | 201 | `endAt ≤ startAt` → 400; passado → 422 | R4.1-4.6 |
| GET | `/appointments` | 200 paginado | enum inválido → 400 | R4.25-4.26 |
| GET | `/appointments/summary` | 200 | `year` ausente → 400 | R4.24 |
| GET | `/appointments/conflicts` | 200 | janela invertida → **lista vazia** | R4.7-4.11 |
| GET | `/appointments/{id}` | 200 | excluído → 404 | R4.20 |
| PUT | `/appointments/{id}` | 200 | sem justificativa → 400; **cancelado/concluído → 422** | R4.12-4.13, 4.16, 4.18 |
| PATCH | `/appointments/{id}/cancel` | 200 | sem justificativa → 400; já cancelado → 422 | R4.14-4.15 |
| PATCH | `/appointments/{id}/complete` | 200 | cancelado → 422 | R4.17 |
| PATCH | `/appointments/{id}/restore` | 200 | inexistente → 404 | R4.21-4.23 |
| DELETE | `/appointments/{id}` | 204 | já excluído → 404 | R4.19 |
| GET | `/appointments/{id}/history` | 200 com `DELETED`/`RESTORED` | excluído → 404 | R4.22 |

### Usuários

| Método | Rota | Feliz | Negativo | Roteiro |
|---|---|---|---|---|
| GET | `/users` | 200, sem credencial | sem token → 401 | R5 |

---

## 3. O que aborta o deploy

Rollback imediato, sem debugar em produção, se qualquer um acontecer:

- **R1.3 falhar** — dado de um escritório visível para outro.
- **Migration V12 ou V13 com `success = false`** em qualquer schema.
- **R6.1 devolver 500** — o índice único parcial não foi criado, e cadastrar
  cliente com CPF de um excluído passa a quebrar.
- **R6.2 mostrar as linhas apagadas** — a exclusão continuou física e levou a
  ficha junto. Este é irreversível sem o backup do passo 0.
- `GET /actuator/info` apontando um commit diferente do esperado.

Rollback = `git reset --hard <sha anterior>` na instância + `restart-app.sh`.
As migrations V12 e V13 são aditivas e **não** precisam ser desfeitas para a
versão anterior voltar a funcionar — a V12 é a exceção parcial: o código antigo
continua funcionando com os índices parciais no lugar dos `UNIQUE` de coluna.

---

## 4. Depois de verde

Este roteiro é manual porque é a primeira subida destas mudanças. Ele não deve
continuar manual:

1. **Postman.** `docs/postman/agenda_appointments.postman_collection.json` já
   cobre a agenda; falta a de clientes. Com os testes escritos na aba *Tests*,
   a coleção roda por `newman` no CI.
2. **Rest-assured (Camada 3).** Os roteiros R2 e R4 são exatamente o formato de
   um `*E2ETest`: sequência com estado, afirmando o encadeamento. Agora que a
   suíte roda no CI com Postgres em container, eles têm onde morar.
3. **O que virar teste automatizado sai deste documento** — plano de teste
   manual que cresce sem nunca encolher vira documento que ninguém executa.
