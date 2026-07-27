# CI/CD - GitHub Actions

Dois workflows, complementando o plano em `docs/PROVISIONAMENTO_INFRA.md`
(Fase 1):

- **`.github/workflows/ci.yml`** - roda em todo `push`/`pull_request` pra
  `main` e `develop`. Três jobs paralelos: build + checkstyle
  (`mvn clean verify -DskipTests`), Semgrep (`p/java` + `p/owasp-top-ten`) e
  um `docker build` só pra validar o `Dockerfile` cedo (sem push de imagem).
  **Testes pulados por ora** - são `@SpringBootTest` e exigem um Postgres
  real pra subir o contexto, que o runner do GitHub Actions não tem; reativar
  exige um serviço Postgres no job (ou Testcontainers), não é só tirar a
  flag.
- **`.github/workflows/deploy.yml`** - roda em `push` na `main` (ou
  manualmente via `workflow_dispatch`). Conecta na instância EC2 via SSH e
  roda `git pull` + `docker compose --profile full up -d --build`. Fica
  atrás do ambiente `production`, que exige aprovação manual antes de
  executar (configuração abaixo) - nunca deploy automático sem esse gate.

## Configuração necessária (fazer uma vez, no GitHub)

### 1. Secrets do repositório

Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Valor |
|---|---|
| `EC2_HOST` | IP público da instância (hoje `3.20.238.108`) |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | Conteúdo **completo** do `taniamelo-deploy.pem` (a chave privada) |

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
gh secret set EC2_HOST --body "3.20.238.108"
gh secret set EC2_USER --body "ubuntu"
gh secret set EC2_SSH_KEY < ~/.ssh/taniamelo-deploy.pem
```

### 2. Ambiente `production` com aprovação manual

Settings → Environments → **New environment** → nome `production`:

1. Marque **Required reviewers** e adicione você (ou quem for aprovar
   deploys).
2. Sem isso, o job de deploy roda automaticamente a cada merge na `main` -
   com o reviewer configurado, o GitHub pausa o job até alguém aprovar
   manualmente na aba **Actions** do repositório.

### 3. Proteger a branch `main`

Settings → Branches → **Add branch protection rule** → `main`:

- **Require status checks to pass before merging** → marque os três jobs
  do `ci.yml` (`build`, `semgrep`, `docker-build`).
- Opcional: **Require a pull request before merging**.

## Fluxo resultante

1. Todo PR pra `main`/`develop` roda build + Semgrep + docker build
   automaticamente.
2. Merge na `main` dispara `deploy.yml`, que fica pendente até um reviewer
   aprovar em **Actions → Deploy → Review deployments**.
3. Aprovado, o workflow conecta na instância e sobe a versão nova via
   `docker compose --profile full up -d --build`.

## Fora de escopo por ora

- **Backup automatizado** (`pg_dump` → Cloudflare R2) - item 6 da Fase 1 em
  `docs/PROVISIONAMENTO_INFRA.md`, ainda não implementado; fica como próximo
  passo natural depois que o deploy automático estiver validado.
- **SonarQube/Grafana em CI** - cobertos nas Fases 2 e 3 de
  `docs/PROVISIONAMENTO_INFRA.md`, rodam local sob demanda, não fazem parte
  desta pipeline.
