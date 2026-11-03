# Passo 12 — Processos, prazos e agendador

> **Repos:** backend e frontend · **Branch:** `feat/12-processos` nos dois · **Depende de:** — ·
> **Decisões:** T12, T13 · **Esforço:** 2 semanas

## 0. Antes de começar

Leia `model/Appointment.java`, `AppointmentType` (já tem `PRAZO`, `PERICIA`, `AUDIENCIA`),
`service/AppointmentService.java`, `service/HolidayService.java` (só feriados **nacionais** — leia o
Javadoc sobre o risco disso em prazo), `model/RequestStage.java` e `ClientRequestStageHistory`
(etapas do requerimento no INSS, que **já existem** — não duplique), `tenant/ActiveTenantSchemas.java`,
`tenant/TenantContext.java`. No front: `src/app/(private)/agenda/` e os componentes de agenda.

## 1. Objetivo

O escritório passa a registrar **processos** (administrativos e judiciais) ligados ao cliente, com
**prazos** calculados em dias úteis ou corridos que aparecem na agenda como compromisso `PRAZO`. E o
sistema ganha um **agendador** que roda tarefas por escritório — usado pelo passo 13 e pelos
alertas futuros.

## 2. Modelo de dados (tenant, próxima versão)

```
legal_cases
  id, client_id fk on delete cascade, kind varchar not null,      -- ADMINISTRATIVO, JUDICIAL
  number varchar not null,              -- judicial: CNJ só dígitos (20); administrativo: protocolo/NB
  court varchar null, court_body varchar null,  -- tribunal / vara ou agência
  benefit_type varchar null,            -- label de BenefitType
  phase varchar not null,               -- LegalCasePhase (lista curta, validar com a advogada)
  status varchar not null,              -- ATIVO, ARQUIVADO
  opened_on date null, notes text null, deleted_at timestamptz null, + auditoria
  -- único (kind, number) entre não excluídos
case_deadlines
  id, legal_case_id fk on delete cascade, description varchar not null,
  counting varchar not null,            -- DIAS_UTEIS, DIAS_CORRIDOS
  days int not null, start_date date not null,  -- data a partir da qual se conta (ex.: publicação)
  due_date date not null,               -- calculada, gravada; recalculada se os dados mudarem
  status varchar not null,              -- ABERTO, CUMPRIDO, CANCELADO
  appointment_id uuid null fk appointments,  -- o PRAZO na agenda
  source varchar not null,              -- MANUAL; PUBLICACAO entra no passo 13
  + auditoria
court_holidays                          -- feriados e suspensões locais que a BrasilAPI não tem
  id, date_from date not null, date_to date not null, description varchar not null,
  scope varchar null,                   -- tribunal/comarca; null = todos do escritório
  + auditoria
```

## 3. Cálculo de prazo — núcleo puro `service/deadline/`

`DeadlineCalculator.dueDate(start, days, counting, Calendar calendar)` + `Calendar` (record com
feriados nacionais + `court_holidays` + suspensões). Regras **com fonte no Javadoc e teste**:

- **Dias úteis** (CPC, arts. 219 e 224): exclui o dia do começo, inclui o do vencimento; começo e
  vencimento em dia não útil passam para o próximo útil.
- **Suspensão de 20/12 a 20/01** (CPC, art. 220) para prazos judiciais em dias úteis.
- **Dias corridos** (ex.: processo administrativo — Lei 9.784/1999, art. 66): conta contínua,
  vencimento em dia não útil prorroga.
- **Publicação eletrônica** (Lei 11.419/2006, art. 4º, §§3º–4º): a publicação é o dia útil
  seguinte à disponibilização, e o prazo começa no útil seguinte à publicação — o passo 13 usa
  isto; implemente aqui como função própria (`startFromAvailability`).
- Feriado nacional vem do `HolidayService`; se ele estiver indisponível (`verified=false`), o
  prazo é calculado **e marcado** "feriados nacionais não verificados" — nunca silencioso.

Os valores de prazo por tipo de ato (15 dias, 10 dias…) **não** são decididos pelo sistema neste
passo: a pessoa informa `days`. (Sugestão por IA é o passo 16, sempre confirmada — T13.)

## 4. Agendador

- `config/SchedulingConfig` com `@EnableScheduling` e pool próprio.
- `service/jobs/TenantJobRunner`: para cada schema de `ActiveTenantSchemas.all()`, define
  `TenantContext`, executa a tarefa, limpa em `finally`; falha num escritório **não** interrompe os
  outros (log WARN com o schema, sem dado pessoal).
- **Instância única** (T12): documente em `docs/AGENDADOR.md` que, com mais de uma instância, é
  preciso trava distribuída (ex.: tabela de lock) antes de escalar; **não** implemente trava agora.
- Tarefa deste passo: `DeadlineReminderJob` (diário, horário em config, fuso do `OfficeClock`)
  que apenas marca/registra prazos vencendo em N dias úteis para o card "o que tem para hoje"
  (passo 16) — sem e-mail/WhatsApp (fora do escopo).
- Propriedade `app.jobs.enabled` (default `true` em `dev`/produção, `false` em `test`; os testes
  chamam a tarefa diretamente).

## 5. API

| Verbo | Rota | Papel |
| --- | --- | --- |
| GET/POST | `/api/v1/clients/{clientId}/legal-cases` | autenticado |
| GET | `/api/v1/legal-cases` (filtros: kind, status, número, cliente; paginado) | autenticado |
| GET/PUT | `/api/v1/legal-cases/{caseId}` | autenticado |
| DELETE | `/api/v1/legal-cases/{caseId}` (soft) | **ADVOGADO** |
| GET/POST | `/api/v1/legal-cases/{caseId}/deadlines` | autenticado |
| PUT | `/api/v1/legal-cases/{caseId}/deadlines/{deadlineId}` | autenticado |
| PATCH | `/api/v1/legal-cases/{caseId}/deadlines/{deadlineId}/status` | autenticado |
| GET | `/api/v1/deadlines?from&to&status` (paginado) | autenticado |
| POST | `/api/v1/deadlines/preview` corpo `{startDate, days, counting, scope?}` → data e avisos | autenticado |
| GET/POST | `/api/v1/court-holidays` | autenticado / **ADVOGADO** para criar |
| DELETE | `/api/v1/court-holidays/{holidayId}` | **ADVOGADO** |

Criar prazo cria o `Appointment` `PRAZO` no `due_date` (dia inteiro, conforme a agenda já trata) e
liga `appointment_id`; cumprir/cancelar o prazo conclui/cancela o compromisso; recalcular move o
compromisso. Tudo na mesma transação. Número CNJ validado (dígito verificador, ISO 7064 mod 97-10)
com `@ValidCnjNumber` no pacote `validation/`, ao lado do `@ValidCPF`.

## 6. Frontend

- Rota nova `src/app/(private)/processos/page.tsx` (lista com filtros, `DataList`, drawer de
  detalhe `RecordDrawer` com prazos) + entrada no menu.
- Na ficha do cliente: seção **Processos** com os do cliente e "Novo processo".
- Formulário de prazo com **pré-visualização** do vencimento (`/deadlines/preview`) e avisos
  (feriados não verificados, suspensão de fim de ano).
- Cadastro de feriados locais (tela simples em processos ou configurações).
- Agenda existente mostra os `PRAZO` (já mostra o tipo) e o clique leva ao processo.
- Testes unidade/tela + e2e `tests/e2e/legal-cases.spec.ts` (cria processo, prazo de 15 dias
  úteis, vê na agenda, cumpre).

## 7. Testes back

`DeadlineCalculatorTest` (tabela grande: início sexta, feriado no meio, vencimento em feriado,
atravessando 20/12–20/01, corridos, disponibilização → publicação → início), `CnjNumberValidatorTest`,
`TenantJobRunnerTest` (falha num tenant não para os outros; contexto limpo),
`LegalCaseIntegrationTest` (prazo ↔ compromisso na mesma transação), contratos, api-tests
`legal-cases.spec.ts` e `deadlines.spec.ts` + Postman.

## 8. Documentação

`docs/AGENDADOR.md`, `docs/PRAZOS.md` (regras com fonte), `DATA_MODEL.md`, `ARQUITETURA.md`,
`MELHORIAS_E_PENDENCIAS.md` (o item "Agendador: nenhum `@Scheduled`" muda de estado).

## 9. Critérios de aceite

- [ ] Processo, prazo e compromisso coerentes em toda operação.
- [ ] Vencimento correto em todos os casos da tabela de teste.
- [ ] Agendador rodando por tenant, com isolamento de falha.
- [ ] Gates e Sonar verdes nos dois repos.

## 10. Commits sugeridos

`feat(processos): processos e feriados locais`, `feat(prazos): cálculo de prazo em dias úteis e
corridos`, `feat(prazos): prazo vira compromisso na agenda`, `feat(agendador): tarefas por
escritório`, `feat(processos): telas de processos e prazos` (front), `docs(prazos): regras e
agendador`.

## 11. Pare e pergunte se

A lista de fases (`LegalCasePhase`) não estiver definida (proponha uma curta e peça validação);
houver dúvida de contagem em JEF ou em prazo administrativo específico.
