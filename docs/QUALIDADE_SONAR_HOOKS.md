# Qualidade de código: Sonar, cobertura e git hooks

Cobre os itens "apto ao Sonar" e "travar lint/build/test antes de commit/push".
O que já existe no projeto, o que foi adicionado agora e como o dev roda.

## 1. O que já existia (não é pouco)

O projeto **já tem um gate de qualidade** no build, independente do Sonar:

- **Spotless** (google-java-format, AOSP) — formatação canônica; `mvn compile`
  falha em código fora do padrão.
- **Checkstyle** — lint de padrões bug-prone; falha o build em violação.
- Ambos ligados na fase `process-classes`, então rodam em `mvn compile`/`verify`.
- **Pre-commit hook** (`.githooks/pre-commit`) — formata e roda lint+build por
  commit.
- **CI** (`.github/workflows/ci.yml`) — build + Checkstyle + Semgrep por PR.

## 2. O que foi adicionado agora

- **JaCoCo** (`jacoco-maven-plugin`) — instrumenta os testes e gera
  `target/site/jacoco/jacoco.xml` (cobertura que o Sonar lê).
- **sonar-maven-plugin** — roda a análise Sonar sob demanda.
- **Propriedades Sonar** no `pom.xml` (`sonar.projectKey`, `sonar.host.url`,
  `sonar.coverage.jacoco.xmlReportPaths`).
- **Pre-push hook** (`.githooks/pre-push`) — roda o build completo (`mvn verify
  -DskipTests`) antes de deixar enviar ao remoto. Testes entram aqui quando o
  Testcontainers estiver pronto (é só tirar o `-DskipTests`).

## 3. Como rodar o Sonar

**SonarCloud (recomendado para começar — grátis para repo):**
```bash
# 1. Crie o projeto no https://sonarcloud.io e gere um token.
# 2. Rode localmente ou no CI:
mvn -q clean verify sonar:sonar \
  -Dsonar.token=SEU_TOKEN \
  -Dsonar.organization=SUA_ORG
```

**SonarQube self-hosted:** suba o container e aponte `-Dsonar.host.url=http://SEU_HOST:9000`.

O `mvn verify` gera a cobertura (JaCoCo) antes do `sonar:sonar`, então o Sonar
já recebe o XML de cobertura. **Quality Gate:** configure no painel do Sonar
(ex.: cobertura mínima em código novo, zero bugs/vulnerabilidades novas) — o
`sonar:sonar` falha o build se o gate reprovar (bom para CI).

**SonarLint no editor:** instale o plugin SonarLint (VS Code/IntelliJ) e conecte
ao projeto Sonar ("connected mode") para ver as mesmas regras enquanto codifica —
é o "sonar antes do commit" no fluxo do dev, sem precisar de servidor no hook.

## 4. Husky no Java? Não — use git hooks nativos (o que já fazemos)

**Husky é uma ferramenta do ecossistema Node** (depende de `package.json` e
`node_modules`). Num projeto Java/Maven puro, adicioná-lo significa arrastar um
toolchain Node só para instalar hooks — os desenvolvedores Java sênior
**evitam** isso. As opções idiomáticas, em ordem de recomendação:

1. **Git hooks nativos versionados via `core.hooksPath`** ✅ (já é o que o
   projeto usa: `.githooks/` + `scripts/install-git-hooks.sh`). Zero dependência
   extra, os hooks vivem no repositório e valem para todos.
2. **`git-build-hook-maven-plugin`** — instala o `core.hooksPath`
   automaticamente no `mvn validate`, então ninguém esquece de rodar o script de
   instalação. Bom complemento ao item 1 (ver §5).
3. **Framework `pre-commit`** (Python) — poderoso e multi-linguagem, mas
   adiciona dependência de Python; vale se o time já usa.

**Conclusão:** mantemos git hooks nativos. Se quiser garantir a instalação
automática, adote o `git-build-hook-maven-plugin` (§5). Não recomendo Husky aqui.

## 5. (Opcional) Auto-instalar os hooks no build

Para o `core.hooksPath` ser configurado sozinho (sem depender de rodar
`scripts/install-git-hooks.sh` à mão), adicione ao `pom.xml`:

```xml
<plugin>
  <groupId>com.rudikershaw.gitbuildhook</groupId>
  <artifactId>git-build-hook-maven-plugin</artifactId>
  <version>3.5.0</version>
  <configuration>
    <gitConfig>
      <core.hooksPath>.githooks</core.hooksPath>
    </gitConfig>
  </configuration>
  <executions>
    <execution><goals><goal>configure</goal></goals></execution>
  </executions>
</plugin>
```

Assim, no primeiro `mvn` que qualquer dev rodar, os hooks passam a valer.

## 6. Fluxo final por gatilho

| Gatilho | Roda | Ferramenta |
|---|---|---|
| Enquanto codifica | regras Sonar no editor | SonarLint (connected mode) |
| `git commit` | format + lint + compile | pre-commit (Spotless+Checkstyle+compile) |
| `git push` | build completo (package) | pre-push (`mvn verify -DskipTests`) |
| Pull Request | build + lint + Semgrep | GitHub Actions (`ci.yml`) |
| Sob demanda / CI | análise + cobertura + Quality Gate | `mvn verify sonar:sonar` |

Testes unitários entram no pre-push e no CI assim que a suíte não depender mais
de Postgres externo (Testcontainers — ver Roadmap Fase A).
