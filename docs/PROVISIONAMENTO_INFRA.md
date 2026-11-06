# Plano de ação: provisionamento (pipeline, Sonar, Grafana)

Passo a passo executável, complementar ao `docs/INFRA_PLAN.md` (que traz a
pesquisa e as decisões) e ao `docs/AWS_TESTE_GRATUITO.md` (que cobre o
servidor/banco/S3 da PoC). Este documento foca no que falta provisionar:
pipeline de CI/CD, SonarQube e Grafana/Prometheus. Ordem pensada pra cada
fase já entregar valor sozinha, sem depender da nuvem estar no ar.

---

## Fase 1 - Pipeline (GitHub Actions)

**Status: itens 1-3 e 5 implementados** - ver `docs/CI_CD.md` para a
configuração de secrets/ambiente necessária no GitHub (não é feita pelo
código, precisa de uma ação manual sua uma vez).

1. ~~Criar `.github/workflows/ci.yml`~~ - feito: `mvn -B clean verify` +
   cache Maven, trigger em `push`/`pull_request` pra `main` e `develop`.
2. ~~Semgrep como job separado~~ - feito, rodando `p/java` + `p/owasp-top-ten`.
3. ~~Job de build da imagem Docker~~ - feito (`docker build .`, sem push).
4. Proteger a branch `main` no GitHub (Settings → Branches): **ainda
   pendente, ação manual** - exigir que os 3 jobs do `ci.yml` passem antes
   de merge (ver `docs/CI_CD.md`).
5. ~~Job de deploy atrás de aprovação manual~~ - feito
   (`.github/workflows/deploy.yml`, ambiente `production`). Configuração do
   reviewer e dos secrets (`EC2_HOST`/`EC2_USER`/`EC2_SSH_KEY`) é manual,
   ver `docs/CI_CD.md`.
6. Backup agendado (`docs/INFRA_PLAN.md` item 2): **ainda não implementado**
   - workflow separado `.github/workflows/backup.yml` com `schedule: cron`
     diário, rodando `pg_dump` via SSH no servidor e subindo o dump pro
     Cloudflare R2. Próximo passo natural depois do deploy automático
     validado.

**Entrega da fase:** todo PR passa por build + Semgrep automaticamente;
merge pra `main` protegido (pendente); deploy manual com gate (feito);
backup automatizado (pendente).

## Fase 2 - SonarQube (local, sob demanda)

Depende só de Docker local - não depende da AWS.

1. Criar `docker-compose.sonar.yml` **separado** do compose principal, com
   os serviços `sonarqube` (imagem `sonarqube:community`) e seu próprio
   Postgres de metadados (nunca dividir com o Postgres da aplicação).
2. Subir sob demanda: `docker compose -f docker-compose.sonar.yml up -d`,
   acessar `http://localhost:9000` (login padrão `admin`/`admin`, troca
   obrigatória no primeiro acesso).
3. Gerar um token de projeto na UI (`Administration → Security → Users →
   Tokens`).
4. Adicionar o `sonar-maven-plugin` no `pom.xml` (`org.sonarsource.scanner.maven:sonar-maven-plugin`).
5. Rodar localmente:
   ```bash
   mvn clean verify sonar:sonar \
     -Dsonar.projectKey=taniamelo-advocacia-backend \
     -Dsonar.host.url=http://localhost:9000 \
     -Dsonar.login=<token>
   ```
6. Depois de validar local, adicionar um job **opcional** (`workflow_dispatch`
   manual, não em todo PR - custo/tempo) no GitHub Actions que sobe o
   SonarQube via Docker dentro do próprio runner e roda o mesmo comando -
   ou, se decidirem pagar depois, trocar por SonarCloud (SaaS, sem precisar
   subir servidor em lugar nenhum).
7. Revisar o primeiro relatório junto: duplicação, complexidade ciclomática,
   code smells. Cobertura vai aparecer zerada até existir suíte de testes -
   registrado como gap conhecido em `docs/ROADMAP.md`.

**Entrega da fase:** SonarQube local rodando sob demanda, primeiro relatório
de qualidade gerado e revisado.

## Fase 3 - Grafana + Prometheus

Pode ser feito local primeiro; migra pra produção só depois da PoC AWS
validada (Fase 4 do `docs/AWS_TESTE_GRATUITO.md`).

### 3.1 - Expor métricas na aplicação

1. Adicionar `micrometer-registry-prometheus` no `pom.xml`.
2. Em `application.yaml`, ajustar:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
   ```
3. Validar local: `curl http://localhost:8080/actuator/prometheus` deve
   retornar as métricas no formato texto do Prometheus.

### 3.2 - Subir Prometheus + Grafana local

1. Criar `prometheus.yml`:
   ```yaml
   scrape_configs:
     - job_name: 'law-firm-backend'
       metrics_path: '/actuator/prometheus'
       static_configs:
         - targets: ['host.docker.internal:8080']
   ```
2. Adicionar os serviços `prometheus` e `grafana` num
   `docker-compose.observability.yml` separado (mesmo raciocínio do Sonar:
   não obrigatório rodar 24/7 em dev).
3. Subir: `docker compose -f docker-compose.observability.yml up -d`.
4. Grafana em `http://localhost:3000` (login padrão `admin`/`admin`),
   adicionar o Prometheus (`http://prometheus:9090`) como data source.
5. Importar um dashboard pronto da comunidade pra Spring Boot/Micrometer
   (JVM heap/GC, HikariCP, latência por endpoint, taxa de erro HTTP) em vez
   de montar do zero - buscar por "Spring Boot" no catálogo oficial do
   Grafana (grafana.com/grafana/dashboards).

### 3.3 - Levar pra produção (só depois da PoC validada)

1. Decidir entre subir `prometheus`+`grafana` no mesmo servidor da aplicação
   (cabe no Droplet de 2GB citado no `INFRA_PLAN.md`; na AWS, exige instância
   com RAM sobrando) ou usar o **Grafana Cloud free tier** (métricas ficam
   fora do servidor, mais folga de RAM na instância que roda a app).
2. Se local: abrir a porta do Grafana (3000) só via túnel SSH ou VPN - nunca
   exposta publicamente sem autenticação reforçada.
3. Configurar um alerta básico no Grafana (ex.: uso de heap > 80%, taxa de
   erro 5xx > 5% em 5 min) notificando por e-mail - mesmo com só 2 usuários
   simultâneos, um alerta cedo evita descobrir problema pelo cliente.

**Entrega da fase:** métricas da aplicação visíveis em dashboard, com
alerta básico configurado.

---

## Ordem recomendada de execução

| Ordem | Fase | Pré-requisito | Pode começar hoje? |
|---|---|---|---|
| 1 | Pipeline (build + Semgrep) | Nenhum | Sim |
| 2 | SonarQube local | Docker local | Sim |
| 3 | Grafana + Prometheus local | Docker local | Sim |
| 4 | PoC AWS (servidor + banco + S3) | `docs/AWS_TESTE_GRATUITO.md` Fases 0-4 | Sim, em paralelo às fases 1-3 |
| 5 | Deploy automatizado via pipeline | Fase 1 + Fase 4 | Depois do servidor existir |
| 6 | Grafana/Prometheus em produção | Fase 3 + Fase 4 | Depois da PoC validada |
| 7 | Backup agendado via Actions | Fase 1 + Fase 4 | Depois do servidor existir |

As fases 1-3 não dependem da AWS e podem rodar em paralelo com o
`docs/AWS_TESTE_GRATUITO.md` - não há motivo pra esperar o servidor existir
pra começar o pipeline ou validar o Sonar/Grafana localmente.
