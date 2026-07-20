README - Docker build & diagnostics

Objetivo

Este README descreve os comandos para build, execução e diagnóstico da stack Docker (Postgres + Flyway + app) para desenvolvimento local.

Pré-requisitos

- Docker Desktop (macOS) com BuildKit habilitado (normalmente já ativo). Para habilitar manualmente:
  export DOCKER_BUILDKIT=1

Comandos principais

1) Verificar Docker/Compose

```bash
docker --version
docker compose version
```

2) Build (aplica cache maven via BuildKit)

```bash
# habilitar BuildKit temporariamente na sessão shell
export DOCKER_BUILDKIT=1

# build do serviço app (usa cache .m2 dentro do build)
docker compose build app
```

3) Subir a stack (build + detach)

```bash
docker compose up --build -d
```

4) Logs do Flyway

```bash
docker compose logs -f flyway
```

5) Ver status

```bash
docker compose ps
```

6) Health check do app

```bash
curl -sS http://localhost:8080/actuator/health | jq .
```

7) Inspecionar arquivo montado

```bash
docker compose exec app sh -c 'cat /app/config/application-dev.yaml'
```

8) Reiniciar apenas o app após editar `application-dev.yaml`

```bash
docker compose restart app
```

Diagnóstico rápido

- Se o build falhar ao copiar `.mvn`: o Dockerfile foi escrito para não exigir o diretório `.mvn`. Ainda assim, se você quiser incluir o wrapper, mantenha `mvnw` e o diretório `.mvn` no repo. Para copiar condicionalmente usando BuildKit avançado, a abordagem mais simples é manter `.mvn` no repositório ou adaptar o pipeline de CI para incluir o wrapper.

- Para ver as variáveis de ambiente que o compose gerou:

```bash
docker compose config
```

- Abrir shell no container para debugar:

```bash
docker compose exec app sh
```

Notas finais

- Montar `application-dev.yaml` permite editar sem rebuild; lembre-se de reiniciar o container para aplicar as mudanças.
- Se quiser que eu altere o Dockerfile para copiar `.mvn` condicionalmente via recursos avançados do BuildKit (ex.: buildkit `--ssh` ou `--secret`), especifique e eu aplico a solução desejada.
