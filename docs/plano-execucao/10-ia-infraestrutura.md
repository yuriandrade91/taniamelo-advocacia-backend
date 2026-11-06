# Passo 10 — IA: infraestrutura comum

> **Repos:** backend (principal) e frontend (configuração e consentimento) ·
> **Branch:** `feat/10-ia-infra` · **Depende de:** — · **Decisões:** T6, T7, T10, D1–D4 ·
> **Esforço:** 1,5 semana

## 0. Antes de começar

1. Leia `docs/PLANO_IA_LLM_RAG.md` §9 inteiro (senha do INSS, dado sensível, sigilo, isolamento,
   auditoria) — **é requisito deste passo**.
2. **Use a skill `claude-api`** do Claude Code antes de escrever qualquer linha do SDK: ela tem a
   versão atual do `com.anthropic:anthropic-java`, os ids de modelo, saída estruturada
   (`output_config.format`), tratamento de `stop_reason: "refusal"`, streaming e retries. NÃO
   escreva chamada do SDK de memória.
3. Leia `service/HolidayService.java` (cliente externo com timeout, cache e falha como aviso),
   `audit/AuditAction.java`, `audit/AuditLogListener.java`, `model/Tenant.java`.

## 1. Objetivo

Uma fundação **única** que todos os recursos de IA (passos 11, 14–17) usam, e que garante por
código — não por disciplina — que: o escritório ligou a IA; há orçamento; nenhum campo proibido
sai; documento médico só sai com consentimento; cada uso fica registrado e auditado.

Nenhum recurso de IA para o usuário nasce neste passo.

## 2. Configuração

`application.yaml` (com defaults só de local, como o `CLAUDE.md` exige):

```yaml
app:
  ai:
    enabled: ${APP_AI_ENABLED:false}            # chave geral da instância
    api-key: ${ANTHROPIC_API_KEY:}               # vazio = IA indisponível, nunca erro de boot em dev
    models:
      default: ${APP_AI_MODEL_DEFAULT:claude-opus-5-5}
      fast: ${APP_AI_MODEL_FAST:claude-haiku-5-5}
    timeout-seconds: ${APP_AI_TIMEOUT_SECONDS:120}
```

Fora de `dev`/`test`, `enabled=true` com `api-key` vazia **recusa o boot** (mesmo padrão do
`CorsConfig`/`StorageTypeGuard`). Acrescente as variáveis ao `.env.example` (sem valor) e ao
`deploy.yml` como secret, **sem** ligar em produção (D1/D2).

## 3. Modelo de dados

- **Shared** (`public`), próxima versão: `tenant_ai_settings` (`tenant_id` pk fk `tenants`,
  `enabled boolean not null default false`, `monthly_budget_usd_cents int not null`,
  `updated_by`, `updated_at`). Sem linha = desligado.
- **Tenant**, próxima versão:
  - `ai_usage` (`id`, `feature varchar not null`, `model varchar not null`,
    `prompt_version varchar not null`, `input_tokens int`, `output_tokens int`,
    `cost_usd_micros bigint not null`, `client_id uuid null`, `user_id uuid not null`,
    `outcome varchar not null` — ok/refusal/error/budget_blocked —, `created_at`). Índice
    `(created_at)`.
  - Em `clients`: `ai_medical_consent_at timestamptz null`, `ai_medical_consent_by uuid null`.
- `AuditAction`: acrescente `AI_ACCESS` (Javadoc no estilo do `READ`: por que este acesso merece
  registro).

## 4. Código — `service/ai/`

Aqui o subpacote **tem** Spring (é integração, não núcleo puro); o que for regra pura
(montagem de contexto, cálculo de custo) fica em classes sem estado testáveis sem Spring.

| Classe | Responsabilidade |
| --- | --- |
| `AiGateway` (interface) | `AiResult<T> structured(AiRequest<T>)` e `AiTextResult text(AiRequest<String>)` — a única porta para o modelo. Recursos dependem da interface |
| `AnthropicAiGateway` | implementação com o SDK oficial: saída estruturada por JSON schema, timeout, retries do SDK, `refusal` tratado como resultado (não exceção), uso de tokens devolvido |
| `FakeAiGateway` (em `src/test`) | resposta programável para testes; **nenhum teste do build padrão chama a API real** |
| `AiRequest<T>` | feature (`AiFeature` enum), modelo (default/fast), prompt versionado, conteúdo, schema de saída, `clientId` opcional |
| `PromptTemplates` | carrega prompts de `src/main/resources/ai/prompts/<feature>-v<N>.md`; a versão vai para `ai_usage` |
| `AiContextBuilder` | monta o conteúdo do prompt **a partir de uma allowlist explícita** de campos (nunca serializa entidade ou DTO inteiro). Campos proibidos em constante (`inssPassword` e o que mais §9 listar) com teste |
| `AiDocumentGate` | decide se um `ClientFile` pode ir ao modelo: `DOCUMENTOS_MEDICOS` só com `ai_medical_consent_at` preenchido; caso contrário 422 `IA_CONSENTIMENTO_MEDICO_AUSENTE` |
| `AiAccessPolicy` | antes de toda chamada: instância ligada? escritório ligou? orçamento do mês tem saldo (soma de `ai_usage.cost_usd_micros` do mês do `OfficeClock`)? Falha → 422 `IA_DESLIGADA` / `IA_ORCAMENTO_ESGOTADO` com mensagem para a tela |
| `AiCostCalculator` | custo a partir de tokens e de uma tabela de preço por modelo em configuração (`app.ai.prices.<modelo>.input/output` em US$/milhão) — preços **não** cravados no código |
| `AiUsageLedger` | grava `ai_usage` e o `audit_log` (`AI_ACCESS`, cliente, feature) na mesma operação |
| `AiService` (fachada) | `policy → gate → context → gateway → ledger`. É o que os passos seguintes chamam |

**Defesa contra injeção de prompt** (vale para todos os recursos): o prompt de sistema diz que o
conteúdo de documentos e entrevistas é **dado, não instrução**; nenhum recurso deste plano dá ao
modelo ferramenta de escrita (T6); a saída é sempre validada contra o schema antes de usar.

## 5. API

| Verbo | Rota | Papel | Faz |
| --- | --- | --- | --- |
| GET | `/api/v1/tenants/current/ai-settings` | autenticado | ligado?, orçamento, gasto do mês, saldo (a tela usa para mostrar/esconder recursos) |
| PUT | `/api/v1/tenants/current/ai-settings` | **ADMIN** | liga/desliga, orçamento (limitado por máximo da instância em config) |
| PUT | `/api/v1/clients/{clientId}/ai-medical-consent` | **ADVOGADO** | registra consentimento (corpo confirma que o termo D4 foi aceito) |
| DELETE | `/api/v1/clients/{clientId}/ai-medical-consent` | **ADVOGADO** | revoga |

## 6. Frontend

- Perfil/configurações do escritório (onde o ADMIN já troca o logo): bloco **Inteligência
  artificial** com liga/desliga, orçamento e gasto do mês.
- Ficha do cliente: indicador e ação de consentimento para documento médico (texto do termo D4;
  enquanto D4 estiver pendente, use texto marcado "rascunho — aguardando aprovação" e diga no
  relatório).
- `hooks/useAiSettings` com `createSessionCache` (limpa no logout) para os passos seguintes
  esconderem os botões de IA quando desligada.
- Testes de unidade/tela e e2e `tests/e2e/ai-settings.spec.ts` (ADMIN liga; STAFF não vê o
  controle).

## 7. Testes obrigatórios (back)

- `AiContextBuilderTest`: cliente com senha do INSS `"SENTINELA-INSS-123"` em todos os campos
  possíveis → o texto montado **não contém** a sentinela; campo fora da allowlist não aparece.
- `AiDocumentGateTest`: médico sem consentimento → recusa; com → passa; revogado → recusa.
- `AiAccessPolicyTest`: desligado na instância, no escritório, orçamento esgotado no limite exato.
- `AiCostCalculatorTest`.
- `AiServiceIntegrationTest` (com `FakeAiGateway`): grava `ai_usage` e `audit_log`, recusa
  registra `outcome=budget_blocked`, isolamento entre tenants do gasto.
- `AnthropicAiGatewayTest`: mapeamento de request/response com o cliente HTTP do SDK falso ou
  servidor HTTP local de teste (sem rede real). Refusal vira `outcome=refusal`.
- `@Tag("ai-live")` (fora do build): uma chamada real mínima, só com `ANTHROPIC_API_KEY` presente.
- Contratos, api-tests `tenants.ai-settings.spec.ts` e `clients.ai-consent.spec.ts` + Postman.

## 8. Documentação

Novo `docs/IA.md`: arquitetura da fundação, regras de sigilo implementadas (com os testes que as
provam), como ligar por escritório, como ler o gasto. Atualize `ARQUITETURA.md`, `DATA_MODEL.md`,
`.env.example`, `MELHORIAS_E_PENDENCIAS.md` (D1/D2 pendentes para produção).

## 9. Critérios de aceite

- [ ] Nenhum recurso pode chamar o modelo sem passar por `AiService` (o gateway real só é
      injetado nele — verifique com teste de convenção).
- [ ] Sentinela da senha do INSS nunca sai (teste).
- [ ] Médico sem consentimento recusado (teste).
- [ ] Orçamento e uso por escritório funcionando e visíveis na tela.
- [ ] IA desligada por padrão em todos os escritórios e na instância.
- [ ] Gates e Sonar verdes nos dois repos.

## 10. Commits sugeridos

`feat(ia): configuração e guarda de boot`, `feat(ia): configuração por escritório e registro de
uso`, `feat(ia): porta para o modelo com saída estruturada`, `feat(ia): allowlist de contexto e
consentimento para documento médico`, `feat(ia): configurações e consentimento na tela` (front),
`docs(ia): fundação e regras de sigilo`.

## 11. Pare e pergunte se

O SDK não oferecer algo que a skill `claude-api` descreve (versão); a política de retenção do
provedor (D2) exigir mudança de desenho.
