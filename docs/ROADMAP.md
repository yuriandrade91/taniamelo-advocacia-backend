# Roadmap de evolução — Sistema de Advocacia Previdenciária

Este documento registra o que foi **conscientemente adiado**, para não perder
o fio da meada. O que já foi feito está documentado em `docs/ARCHITECTURE.md`
(decisões de API) e `docs/DATA_MODEL.md` (schema atual, baseline `V1`-`V8`).

## Nota sobre o histórico deste documento

Rodadas anteriores acumularam um schema com migrations renumeradas e tabelas
mortas (schema criado antes do Flyway, CNIS/endereço em desenhos descartados).
Esse histórico foi **squashado** num baseline limpo (`V1__extensions` ..
`V8__client_payments`, todas as PKs em UUID) - um banco com o schema antigo
precisa ser recriado do zero (`docker compose down -v`), não há migração
incremental entre os dois. Detalhes de schema ficam só em `docs/DATA_MODEL.md`
daqui pra frente, para não duplicar (e desatualizar) a mesma informação em
dois lugares.

## Decisão de arquitetura: MVC em camadas

Mantivemos o padrão Controller → Service → Repository (Spring MVC clássico),
com um controller dedicado por sub-recurso/aba da tela do cliente (endereços,
dados pessoais, dados profissionais, entrevistas, arquivos, financeiro) em vez
de um `ClientController` monolítico. Razão completa em `docs/ARCHITECTURE.md`
§1. Para o escopo atual - um único domínio (clientes), CRUD-cêntrico, time
pequeno - arquitetura hexagonal/Clean Architecture ou CQRS adicionariam
cerimônia sem ganho real; reavaliar quando o sistema ganhar múltiplos
contextos com regras de negócio mais complexas por módulo (ver
`docs/ARCHITECTURE.md` §2 para o caminho de evolução a microserviços/multi-tenant).

**Exceção deliberada:** o histórico de situação (`GET
/clients/{id}/situation-history`) e o PATCH de situação/benefício/arrecadação
(`PATCH /clients/{id}`) ficam dentro do próprio `ClientController` - não viraram
sub-recurso à parte porque não são uma aba própria da UI nem uma coleção 1:N
com identidade independente, são uma trilha de auditoria e um conjunto de
campos do próprio cliente (mesma régua descrita em `docs/ARCHITECTURE.md` §1:
"sub-recurso novo só quando corresponde a uma seção real da UI ou a uma
coleção 1:N").

## Adiado propositalmente (por fases)

### Fase A — Antes de ir a produção
- Restringir CORS a domínios conhecidos (hoje propositalmente aberto para
  facilitar o desenvolvimento local).
- Trocar `APP_JWT_SECRET`, `APP_ENCRYPTION_KEY`, `APP_ADMIN_PASSWORD` para
  valores fortes e únicos por ambiente (os do `.env` são só para dev local).
- Autorização por papel (`@PreAuthorize`) — hoje qualquer usuário autenticado
  acessa tudo; falta diferenciar ADMIN/LAWYER/STAFF nas rotas sensíveis
  (especialmente `/personal-data` [senha do INSS] e `/payments` [dados
  financeiros] - avaliar se STAFF deveria ver os dois ou só ADMIN/LAWYER).
- Testes automatizados: unitários de service (regras de situação/benefício,
  parsing de tempo de contribuição), integração com Testcontainers, teste de
  contrato da API de auth.
- CI (GitHub Actions): build + test + lint em cada PR.

### Fase B — Observabilidade
- Logs estruturados (JSON) e correlação de request-id.
- Métricas (Micrometer + Prometheus/Grafana) além do `/actuator/health` básico.
- Auditoria completa de acesso a dados sensíveis (quem viu a senha do INSS de
  qual cliente e quando).
- **Limitação conhecida da trilha de auditoria (`audit_log`):** o
  `AuditLogListener` só dispara para remoções que passam pelo EntityManager
  (`repository.delete(...)`/`deleteById(...)`). Uma remoção em cascata feita
  pelo Postgres via `ON DELETE CASCADE` (ex.: apagar um cliente remove
  endereços/arquivos/pagamentos em cascata no banco) não passa pelo Hibernate
  e por isso não gera uma linha própria por registro filho - só a remoção do
  cliente em si é auditada. Resolver exigiria carregar e apagar cada
  sub-recurso explicitamente via repositório (perdendo o `ON DELETE CASCADE`
  como rede de segurança) ou aceitar a lacuna como está.

### Fase C — Funcionalidades de domínio previdenciário
- Gestão de processos administrativos/judiciais com prazos e alertas.
- Rotina de expurgo definitivo de arquivos/entrevistas/pagamentos após período
  de retenção (hoje é só soft delete - `deleted_at`) e verificação de
  vírus/malware no upload antes de expor a aplicação fora da rede local.
- Cálculo previdenciário de verdade a partir das simulações de CNIS importadas
  (tempo de contribuição, regras de transição da EC 103/2019, pontos,
  pedágio) - hoje `client_files` (kind=SIMULATION) só armazena o PDF já
  pronto e os metadados preenchidos pelo usuário, sem calcular nada.
- Agenda de perícias e audiências.
- Contratos de honorários formais (documento assinado, % de êxito automático
  sobre valor de benefício retroativo) e relatório financeiro consolidado do
  escritório (hoje `client_payments` só mostra parcela por parcela, por
  cliente).
- Notificações (e-mail/WhatsApp) em mudança de situação, parcela vencendo e
  proximidade de prazos.

### Fase D — Escala
- Multi-tenant, se o sistema vier a atender mais de um escritório (caminho
  detalhado em `docs/ARCHITECTURE.md` §2).
- Backup automatizado e plano de disaster recovery do Postgres.
