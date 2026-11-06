# ADR-0003 — Extração para microsserviços (identity, clients, financial, schedules)

- **Status:** planejado — registro de desenho antes de qualquer código, decisão
  já discutida e alinhada, execução ainda não iniciada.
- **Motivação declarada:** não é necessidade técnica do porte atual (2 usuários
  simultâneos, sem time separado, sem pico de tráfego). É uma decisão
  deliberada de arquitetura para (a) aprendizado/portfólio real, feito em cima
  de um sistema em produção, e (b) preparar o terreno para uma eventual venda
  do produto como SaaS multi-escritório, onde o Identity/onboarding de tenant
  passa a ser necessidade real, não só exercício.
- **Escopo hoje:** 4 domínios candidatos — `identity` (login/tenants/usuários),
  `clients` (o monolito atual, que continua sendo o núcleo), `financial`
  (receitas/despesas do escritório) e `schedules` (agenda).

---

## 1. Por que extrair, e por que essa ordem

`clients` é o domínio dominante do sistema (cadastro, endereços, entrevistas,
arquivos, histórico de situação/benefício) — **continua no monolito**. Não há
gatilho técnico para quebrá-lo agora, e é o núcleo que mais se beneficia de
transação local (várias tabelas relacionadas, muito `JOIN`).

Os outros três têm perfis diferentes:

| Domínio | Por que é candidato | Depende de quem |
|---|---|---|
| **identity** | Responsabilidade transversal — todo outro serviço precisa validar "quem é esse usuário, de que tenant". Candidato clássico a ser extraído **primeiro**, porque define o contrato (JWT) que os outros consomem. | Ninguém — é a base |
| **financial** | Escopo é o escritório (tenant), não o cliente — zero acoplamento com o domínio `clients` hoje. | `identity` (JWT) |
| **schedules** | Mesmo perfil de `financial`: CRUD simples, sem regra cruzada com `clients` hoje. | `identity` (JWT) |

**Ordem de extração: `identity` → `financial`/`schedules` (podem ser
paralelos) → `clients` permanece monolito por tempo indeterminado.**
Extrair `identity` por último obrigaria retrabalhar a validação de token nos
serviços que já tivessem sido extraídos antes dele.

## 2. Como token e tenant atravessam os serviços sem acoplamento síncrono

O erro mais comum nessa migração é todo serviço chamar `identity` a cada
requisição só para validar o token — isso troca uma verificação em memória
(o que existe hoje) por uma chamada de rede em toda requisição de todo
serviço, criando um "monolito distribuído" (acoplado como monolito, lento e
frágil como distribuído).

**Desenho adotado:** `identity` é o único serviço que **emite** tokens
(login, refresh, logout). Os demais (`clients`, `financial`, `schedules`)
continuam validando o JWT **localmente e sem estado**, com a mesma chave de
assinatura (`APP_JWT_SECRET`, compartilhada via secret do orquestrador/CI, não
hardcoded) — exatamente o que o `JwtService`/`JwtAuthenticationFilter` do
monolito já fazem hoje. Nenhum serviço chama `identity` para validar uma
requisição comum; só o fluxo de login/refresh/logout fala com `identity`
diretamente (do frontend, ou via gateway).

O claim `tenant` (schema/slug do escritório, já implementado no monolito via
`TenantContext`/header `X-Tenant-Id`) viaja dentro do próprio JWT e é lido do
mesmo jeito em todos os serviços — cada um resolve o schema/tenant-scope
localmente a partir do claim, sem perguntar a `identity`.

## 3. O que `identity` passa a possuir

- **Catálogo de tenants** (`tenants`, hoje já isolado no schema `public` —
  praticamente pronto para virar o banco desse serviço sozinho).
- **Usuários e papéis.** Decisão de modelo pendente de execução: hoje cada
  escritório tem sua própria tabela `users` dentro do schema do tenant
  (schema-per-tenant, ver `MULTI_TENANT_E_AUTH.md`). Centralizado num serviço
  de identidade, o padrão de mercado para SaaS B2B é inverter isso — uma
  tabela `users` única no banco do `identity`, com coluna `tenant_id`, em vez
  de duplicar a tabela por schema. Facilita usuário com acesso a mais de um
  tenant e login único no futuro. **Ponto a decidir na Fase 1 de execução**,
  não neste ADR.
- **Endpoints `login`, `refresh`, `logout`** (hoje em `AuthController` no
  monolito — migram de posição, contrato de resposta não muda).
- **Onboarding de tenant** (novo, não existe hoje): hoje um escritório novo
  entra por migration/SQL manual. Um `identity` pensado para venda precisa de
  um fluxo de "cadastrar novo escritório" — criar linha em `tenants`,
  provisionar schema/dados iniciais (ou, se o modelo de usuários virar
  centralizado, só criar as linhas necessárias), criar o primeiro usuário
  ADMIN. Esse é o pedaço que fecha a história de "pronto para vender", e é o
  maior item de código novo desta extração (não é só mover código existente).

## 4. O que `financial` e `schedules` possuem

- Banco/schema próprio, sem tabela compartilhada com `clients` ou `identity`
  (regra de ouro: um serviço, um dono do dado).
- Validação de JWT local, igual descrito na seção 2.
- Se algum dia precisarem saber algo de `clients` (ex.: nome do cliente numa
  receita vinculada a honorário), a integração é **assíncrona via RabbitMQ**
  (já desenhado em `NOTIFICACOES_E_MENSAGERIA.md`, ainda não implementado): o
  monolito publica eventos de domínio (`ClienteCriado`, `PagamentoRegistrado`)
  e quem precisar mantém uma cópia mínima local (read model), em vez de
  chamada síncrona entre serviços. Essa extração é o gatilho natural para
  finalmente implementar o RabbitMQ que hoje está só planejado.

## 5. Exposição unificada e deploy

- **API Gateway** (Nginx como reverse proxy, ou Spring Cloud Gateway) roteia
  por prefixo: `/api/v1/auth/*` e `/api/v1/tenants/*` → `identity`;
  `/api/v1/clients/*` e sub-recursos → `clients` (monolito atual);
  `/api/v1/financial/*` → `financial`; `/api/v1/schedules/*` → `schedules`.
  O frontend continua falando com um host só.
- **Descoberta de serviço:** dispensada por ora — com Docker Compose, a URL de
  cada serviço é resolvida por nome de container na mesma rede. Eureka/Consul
  ficam fora de escopo: o ganho de aprendizado é baixo para o esforço nesse
  número de serviços, e não é o que se costuma cobrar em entrevista para esse
  porte de sistema.
- **Deploy independente:** cada serviço com seu próprio `Dockerfile` e seu
  próprio workflow no GitHub Actions (build + deploy separados) — é esse
  requisito mínimo que torna a palavra "microsserviços" honesta no currículo;
  do contrário é só código dividido em pastas.

## 6. Impacto e riscos

- **Consistência entre serviços:** sem transação distribuída — cada serviço é
  dono do próprio dado; qualquer necessidade de dado de outro domínio passa
  por evento assíncrono (seção 4), aceitando consistência eventual.
- **Duplicação de validação de JWT:** `JwtService` (ou equivalente) precisa
  existir em cada serviço consumidor — é código pequeno e estável, mas é
  duplicação real; candidato a virar uma lib compartilhada (`common-auth`) se
  o número de serviços crescer.
- **Observabilidade fica mais importante, não opcional:** com 4 processos
  separados, um erro que hoje aparece numa stack trace única passa a exigir
  correlação entre serviços — reforça a prioridade do Prometheus/Grafana já
  planejado em `PROVISIONAMENTO_INFRA.md`.
- **Onboarding de tenant é o item de maior risco/esforço real** desta
  extração — é a única peça que não é "mover código que já existe", é
  desenho e implementação novos.

## 7. Plano de execução (faseado)

**Fase 1 — `identity`:**
1. Decidir e migrar o modelo de usuários (schema-per-tenant → tabela única
   com `tenant_id`, ou manter schema-per-tenant só para `clients`/dados de
   negócio e centralizar apenas auth) — decisão a fechar antes do código.
2. Novo projeto Spring Boot `identity-service`, banco próprio.
3. Mover `AuthController`, `JwtService` (emissão), `RefreshTokenService`,
   `TenantRepository`/`Tenant` do monolito para o novo serviço.
4. Endpoint novo de onboarding de tenant (criação de escritório + primeiro
   ADMIN).
5. Monolito (`clients`) passa a só **validar** JWT (remove emissão), mesma
   chave compartilhada.
6. Gateway roteando `/auth/*` e `/tenants/*` para `identity`.

**Fase 2 — `financial` e `schedules` (podem ser paralelos):**
1. Dois projetos novos, banco próprio cada.
2. CRUD completo replicando o padrão de camadas do monolito
   (controller/service/repository), auditoria própria.
3. Validação de JWT local (mesma chave de `identity`).
4. Gateway roteando `/financial/*` e `/schedules/*`.

**Fase 3 — Integração assíncrona (se/quando necessária):**
1. RabbitMQ (ver `NOTIFICACOES_E_MENSAGERIA.md`) para eventos de domínio que
   `financial`/`schedules` precisem consumir de `clients`.

**Fase 4 — Observabilidade e deploy independente:**
1. Prometheus/Grafana por serviço (ver `PROVISIONAMENTO_INFRA.md`).
2. Pipeline de CI/CD próprio por serviço.

## 8. Decisão

Executar como exercício deliberado de arquitetura, começando por `identity`
(Fase 1), com `clients` permanecendo o núcleo monolítico por tempo
indeterminado. `financial`/`schedules` entram na Fase 2, reaproveitando o
contrato de JWT definido na Fase 1. RabbitMQ e observabilidade por serviço
entram como pré-requisito das fases seguintes, não da primeira.
