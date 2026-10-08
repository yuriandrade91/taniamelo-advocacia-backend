# CI/CD - GitHub Actions

Dois workflows, complementando o plano em `docs/PROVISIONAMENTO_INFRA.md`
(Fase 1):

- **`.github/workflows/ci.yml`** - roda em todo `push`/`pull_request` pra
  `main` e `develop`. Cinco jobs paralelos:
  - **`build`** - `mvn -B clean verify`, com a suíte inteira. Os testes de
    contexto sobem um Postgres real via Testcontainers (ver
    `PostgresContainerConfig`): o runner `ubuntu-latest` já traz o daemon do
    Docker, então não é preciso serviço Postgres no job. O relatório do
    surefire sai como artefato.
  - **`cobertura-rotas`** - compara as rotas declaradas nos controllers com as
    requisições das coleções Postman (`api-tests/scripts/cobertura-de-rotas.mjs`)
    e falha se alguma rota não for exercitada por ninguém. É conferência
    estática: não sobe aplicação, banco nem `npm install`.
  - **`sonar`** - análise + Quality Gate, só quando o `SONAR_TOKEN` existe.
  - **`semgrep`** - `p/java` + `p/owasp-top-ten`; falha só em severidade ERROR.
  - **`docker-build`** - `docker build` só pra validar o `Dockerfile` cedo
    (sem push de imagem).
- **`.github/workflows/deploy.yml`** - roda em `push` na **`develop`** (a branch
  que a instância segue) ou manualmente via `workflow_dispatch`. Conecta na
  instância EC2 ARM64 (`tm-adv-backend`, Amazon Linux 2023) via SSH e roda
  `git reset --hard origin/develop` + `mvn -B clean package -DskipTests` +
  `sudo systemctl restart law-firm` - a instância roda o JAR direto na JVM via
  **systemd, sem Docker**. Fica atrás do ambiente `production`, que exige
  aprovação manual antes de executar (para ambiente de teste, dá para remover o
  gate e ter deploy automático).

## Configuração necessária (fazer uma vez, no GitHub)

### 1. Secrets do repositório

Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Valor |
|---|---|
| `EC2_HOST` | IP público da instância ARM64 (`18.208.158.75`) |
| `EC2_USER` | `ec2-user` (Amazon Linux 2023) |
| `EC2_SSH_KEY` | Conteúdo **completo** da chave privada `.pem` da instância |

> **Pré-requisito na instância:** o deploy roda `sudo systemctl restart law-firm`
> por SSH não-interativo, então o `ec2-user` precisa de sudo **sem senha** só
> para esse comando. Uma vez, na instância:
> ```bash
> echo 'ec2-user ALL=(ALL) NOPASSWD: /usr/bin/systemctl restart law-firm' \
>   | sudo tee /etc/sudoers.d/law-firm-deploy
> ```
> Confira também que `java` e `mvn` estão no PATH de sessões não-interativas
> (o workflow faz `source ~/.bashrc`/`~/.bash_profile` por segurança).

Pra pegar o conteúdo da chave sem abrir o arquivo num editor (evita quebrar
quebras de linha):

```bash
cat ~/.ssh/taniamelo-deploy.pem
```

Cole a saída inteira (incluindo as linhas `-----BEGIN ... KEY-----` e
`-----END ... KEY-----`) no valor do secret `EC2_SSH_KEY`.

Alternativa via `gh` CLI (roda local, no seu terminal - não deixe o Claude
Code rodar isso por você, já que envolve colar a chave privada):

```bash
gh secret set EC2_HOST --body "18.208.158.75"
gh secret set EC2_USER --body "ec2-user"
gh secret set EC2_SSH_KEY < ~/.ssh/SUA_CHAVE.pem
```

### 2. Ambiente `production` com aprovação manual

Settings → Environments → **New environment** → nome `production`:

1. Marque **Required reviewers** e adicione você (ou quem for aprovar
   deploys).
2. Sem isso, o job de deploy roda automaticamente a cada push na `develop` -
   com o reviewer configurado, o GitHub pausa o job até alguém aprovar
   manualmente na aba **Actions** do repositório. (Para um ambiente de teste,
   dá para não configurar reviewer e ter deploy automático em cada push.)

### 3. Proteger a branch `develop`

Settings → Branches → **Add branch protection rule** → `develop`:

- **Require status checks to pass before merging** → marque os jobs do
  `ci.yml` (`build`, `cobertura-rotas`, `semgrep`, `docker-build`; `sonar`
  apenas se o token estiver configurado).
- Opcional: **Require a pull request before merging**.

## Fluxo resultante

1. Todo PR/push pra `develop` roda build com testes + cobertura de rotas +
   Semgrep + docker build automaticamente (`ci.yml`).
2. Push na `develop` dispara `deploy.yml`, que fica pendente até um reviewer
   aprovar em **Actions → Deploy → Review deployments**.
3. Aprovado, o workflow conecta na instância ARM64 e sobe a versão nova via
   `git reset --hard origin/develop` + `mvn package` + `systemctl restart law-firm`.

## Fora de escopo por ora

- **Backup automatizado** (`pg_dump` → Cloudflare R2) - item 6 da Fase 1 em
  `docs/PROVISIONAMENTO_INFRA.md`, ainda não implementado; fica como próximo
  passo natural depois que o deploy automático estiver validado.
- **SonarQube/Grafana em CI** - cobertos nas Fases 2 e 3 de
  `docs/PROVISIONAMENTO_INFRA.md`, rodam local sob demanda, não fazem parte
  desta pipeline.
