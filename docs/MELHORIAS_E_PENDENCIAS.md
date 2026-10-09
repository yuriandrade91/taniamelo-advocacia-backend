# Margem de melhoria e pendências

**Levantado em 08/10/2026**, sobre a `develop` no commit `9ce5b5d`.

Este documento existe porque o [ROADMAP.md](ROADMAP.md) envelheceu: ele lista como adiadas
coisas que já estão implementadas (papéis de acesso, testes automatizados, CI, multi-tenancy),
e quem o lê hoje conclui que o projeto é menos maduro do que é. Aqui está o estado medido, o que
mudou nesta rodada, e o que falta — separando o que é risco do que é desejo.

## Como estes números foram obtidos

Toda afirmação quantitativa abaixo vem de um comando, não de leitura de código. Isso está
registrado de propósito: na primeira passada desta análise foram reportados "68,3% de linhas" e
"cobertura zero nos services", ambos errados, porque o relatório lido era de uma execução
incompleta. Os números valem o que vale o comando que os produziu.

| Afirmação | Comando |
| --- | --- |
| Contagem e resultado dos testes | `mvn clean verify` |
| Cobertura | `target/site/jacoco/jacoco.xml`, após o `verify` completo |
| Rotas cobertas por coleção | job `cobertura-rotas` do CI |
| Ausência de uma funcionalidade | `grep -rl` sobre `src/main` + ausência de entidade em `model/` |

---

## Estado atual

| | |
| --- | --- |
| Testes Java | 1045, 0 falhas |
| Cobertura de linhas | 90,5% (5768/6374) |
| Cobertura de branches | 82,3% (1081/1314) |
| Rotas | 90, todas com requisição em alguma coleção |
| Suítes de contrato | 14 arquivos Playwright, 2 coleções Postman |

O núcleo é sólido: multi-tenancy por schema, refresh token em cookie httpOnly, autorização por
papel com teste de contrato que **barra rota nova sem decisão declarada**, observabilidade ligada,
Quality Gate e Semgrep no CI, ADRs escritos. A régua de "teste que impede o esquecimento futuro,
não só o bug de hoje" é alta e está sendo cumprida.

---

## O que foi melhorado nesta rodada

### Teto de log nos containers

`docker-compose.yml` não limitava o driver `json-file`, que cresce sem teto. Em uma instância de
8 GiB com Postgres no mesmo volume, log de requisição acumulado é uma das formas de encher o
disco. Agora há âncora `x-logging` com `max-size: 10m` e `max-file: 3`, aplicada aos dois
serviços.

Na mesma passada saiu a chave `version: "3.8"` dos dois arquivos de compose — obsoleta no Compose
v2, gerava aviso em todo comando.

### Faxina de disco no deploy

Todo deploy roda `up -d --build` ([restart-app-docker.sh](../scripts/restart-app-docker.sh)) e
deixa a imagem anterior órfã. Nada no pipeline limpava. Em 8 GiB, algumas dezenas de deploys
bastam — foi exatamente assim que o deploy de 08/10/2026 morreu, no `git fetch`, com
`No space left on device`, antes de publicar qualquer coisa.

[deploy.yml](../.github/workflows/deploy.yml) passa a rodar `docker image prune -f` e
`docker builder prune -f --filter "until=168h"`, **depois** das checagens de health, versão e
storage. A ordem é deliberada: enquanto a versão nova não provou que sobe saudável, a imagem
anterior é o rollback.

`--volumes` não aparece e não deve aparecer. `db-data` é o Postgres de produção e `app-storage`
são os arquivos dos clientes; essa flag é a diferença entre limpar e destruir.

### Postgres dos testes alinhado com produção

[PostgresContainerConfig](../src/test/java/com/lawfirm/law/firm/support/PostgresContainerConfig.java)
fixava `postgres:16-alpine` enquanto o compose usa `postgres:17.5` — e o javadoc do próprio
arquivo mandava acompanhar produção. A suíte afirmava sobre um banco que não é o que atende o
cliente, e a variante alpine agrava: outra libc, outro comportamento de collation e de
`unaccent`, que é justamente o que a busca de clientes usa. Agora é `postgres:17.5`, com o
acoplamento registrado no javadoc.

### Teste para o card de aniversariantes

A rota nasceu sem teste: `upcomingBirthdays`, `findBirthdaysInWindow` e `BirthdayRow` não
apareciam em lugar algum da suíte além do inventário de papéis. Quando celular e WhatsApp foram
acrescentados à projeção (commit `9ce5b5d`), a suíte seguiu verde sem afirmar uma linha sobre o
comportamento novo — e a mudança era numa *constructor expression* de JPQL, que o Hibernate só
valida no bootstrap: nome de campo errado ali não quebra teste de unidade, derruba a aplicação na
subida.

[AniversariantesIntegrationTest](../src/test/java/com/lawfirm/law/firm/service/AniversariantesIntegrationTest.java)
cobre quatro casos contra Postgres real: os dados de contato chegam ao card, `false` em
`is_whatsapp` é preservado, a janela filtra e ordena pelo próximo aniversário, e o limite corta
sem desordenar.

Efeito medido: `ClientServiceImpl` saiu de 91,9% para 99,0% de linhas (24 descobertas para 3), e
o total do projeto subiu de 90,1% para 90,5% de linhas e de 82,0% para 82,3% de branches.

---

## Margem de melhoria, por prioridade

### 0. A instância está em disco local, não em S3 — verificado em 09/10/2026

`GET http://98.82.73.175:8080/actuator/info` responde `"storage":{"type":"local"}`, no commit
`046d24d`. Os arquivos dos clientes — PDFs de perícia, documentos — estão no volume da EC2, sem
cópia em bucket nenhum.

O S3 não está mal configurado: **não está em uso**. A cadeia que deixa isso passar verde:

1. `vars.APP_STORAGE_TYPE` não está definida no repositório.
2. [configurar-storage.sh](../scripts/configurar-storage.sh) sem a variável imprime "mantendo o
   que está no .env" e sai com 0, sem alterar nada.
3. A asserção de storage do [deploy.yml](../.github/workflows/deploy.yml) está dentro de
   `if [ -n "$STORAGE_ESPERADO" ]` — sem a variável, é pulada inteira.
4. [application.yaml](../src/main/resources/application.yaml) resolve
   `${APP_STORAGE_TYPE:local}` para o default `local`.
5. Deploy verde, aplicação em disco local.

O mecanismo construído para impedir exatamente isso está inerte, porque o script e a asserção
dependem da mesma variável que não existe. O comentário no workflow registra que a instância
"passou meses com APP_STORAGE_TYPE=local enquanto a documentação dizia s3": o problema foi
instrumentado, não corrigido, e a instrumentação não dispara no caso que importa.

**Agravante:** o `/actuator/health` do mesmo instante reporta 852 MB livres de 7,9 GiB — 89,5%
cheio. Com storage local, cada arquivo de cliente cai no mesmo volume que derrubou o deploy de
08/10. O S3 ausente e o disco apertado são um problema só.

**Como corrigir:** definir `APP_STORAGE_TYPE=s3`, `APP_STORAGE_S3_BUCKET` e
`APP_STORAGE_S3_REGION` como *variables* do repositório (não secrets — bucket e região não são
segredo), redeployar e conferir que a IAM Role da instância tem permissão no bucket. A partir
daí a asserção do workflow passa a valer e reprova o deploy que não subir como s3.

**Melhoria no pipeline:** tornar a asserção incondicional, ou reprovar o deploy quando
`APP_STORAGE_TYPE` não estiver definida. Hoje o pipeline é silencioso precisamente no caso que
ele existe para pegar.

### 0-bis. Teste de upload pelos endpoints — e o que ele não prova

Não há teste de ponta a ponta exercitando upload e download pelos endpoints
(`POST /api/v1/clients/{clientId}/files/documents` e `/simulations`) contra a instância
publicada.

Vale registrar a armadilha antes de alguém escrever esse teste achando que resolve o item 0:
**upload e download funcionam identicamente em `local` e em `s3`.** O `Content-Length` do
download vem dos metadados no banco, não do storage. Um teste de upload verde não diz nada sobre
onde o arquivo foi gravado — é o próprio comentário do `deploy.yml` que documenta esse engano.

O que um teste de upload prova de verdade é condicional: **com `type=s3` já confirmado pelo
`/actuator/info`**, um upload que completa prova que bucket, região e IAM Role estão corretos,
porque é aí que uma permissão faltando falharia. A ordem certa é: primeiro o `/actuator/info`
afirmar `s3`, depois o upload servir de prova do provisionamento.


### 1. Não existe backup

`grep -rl "pg_dump\|backup"` sobre `src/main`, `scripts/` e `pom.xml` não retorna **nenhum**
arquivo. [CI_CD.md](CI_CD.md) registra o plano (`pg_dump` → Cloudflare R2) como não implementado.

Há dado de cliente real em produção — nome, CPF, nome da mãe, senha do INSS, PDFs de perícia. Em
um volume único de 8 GiB, sem cópia. Qualquer falha de EBS, erro de `docker compose down -v` ou
`DROP SCHEMA` equivocado é perda definitiva, e não há de onde voltar.

Isso está acima de tudo o que a Fase C propõe. Um `pg_dump` diário com retenção e um restore
testado valem mais que qualquer funcionalidade nova.

### 2. O volume da instância está subdimensionado

[AWS_EC2_ARM64_DEPLOY.md](AWS_EC2_ARM64_DEPLOY.md) registra "8 GiB gp3 (app + Postgres no mesmo
disco - acompanhar uso)". O `prune` adicionado nesta rodada trata o sintoma; 8 GiB para
aplicação, banco, imagens Docker e arquivos de cliente no mesmo volume continua apertado.

Pior: com o disco cheio, o Postgres passa a recusar escrita. O deploy que falhou foi o aviso
barato — o caro é o banco parar de aceitar um cadastro no meio do expediente.

### 3. As suítes de contrato não rodam em CI

O CI tem cinco jobs: `build`, `cobertura-rotas`, `sonar`, `semgrep`, `docker-build`. Nenhum
executa os 14 arquivos Playwright nem as 2 coleções Postman.

O motivo é legítimo — elas precisam de instância publicada. O efeito é que a suíte que responde
"o que subiu na AWS funciona?" não é consultada em nenhum deploy. Como o `deploy.yml` já espera
health UP e confere o commit publicado, há um ponto natural para um job pós-deploy apontando
para a instância.

### 4. Cobertura concentrada: receitas do escritório

A média de 90,1% esconde um bolsão. A funcionalidade de receita do escritório está
praticamente sem teste:

| Classe | Linhas descobertas | Cobertura |
| --- | --- | --- |
| `OfficeRevenueService` | 82 | 31,7% |
| `OfficeRevenueResponseDTO` | 49 | 0,0% |
| `OfficeRevenue` | 31 | 42,6% |
| `OfficeRevenueRequestDTO` | 28 | 0,0% |
| `OfficeRevenueSearchParams` | 20 | 39,4% |

É dinheiro entrando no escritório, e é a área menos verificada do sistema. `OfficeExpense`, que
compartilha `FinanceQueries`, está em ~88% — a assimetria é histórica, não técnica.

### 5. Cobertura em código de segurança

| Classe | Linhas descobertas | Cobertura |
| --- | --- | --- |
| `JwtService` | 29 | 50,0% |
| `JwtAuthenticationFilter` | 25 | 59,7% |

Há 12 testes em `JwtService`, então não é terra arrasada — mas metade do caminho de emissão e
validação de token não é exercitado. Em código que decide quem entra, 50% é pouco.

Também baixos: `SupportService` (38 linhas, 19,1%) e `PaymentService` (20 linhas, 28,6%).

### 6. Padrões abertos em configuração

[application.yaml](../src/main/resources/application.yaml):

| Chave | Default | Risco |
| --- | --- | --- |
| `app.cors.allowed-origins` | `*` | Qualquer origem, se a variável não for definida |
| `app.jwt.secret` | `dev-only-secret-change-me-please-32chars` | Token forjável com segredo público |
| `app.admin.password` | `changeme123` | Admin com senha conhecida |

Os três funcionam corretamente **quando** a variável de ambiente existe. O risco é o ambiente em
que ela não existe: nada falha, nada avisa, e o default vale. É o padrão "funciona até não
funcionar".

Deliberadamente **não** alterei esses defaults nesta rodada: fechar o CORS ou exigir segredo
poderia derrubar a instância atual, dependendo do que está no `.env` dela — e eu não tenho acesso
para verificar. A decisão é sua, e o caminho seguro é o mesmo que o deploy já usa para storage:
falhar o boot quando o valor de produção estiver ausente, em vez de cair num default silencioso.

### 7. O ROADMAP desinforma

[ROADMAP.md](ROADMAP.md) lista na Fase A, como adiado, aquilo que já existe: autorização por
papel, testes automatizados, CI, e na Fase D a multi-tenancy. Um documento de planejamento que
subestima o próprio projeto faz tomar decisão errada — alguém vai propor implementar o que já
está pronto.

### 8. Ramo inalcançável no card de aniversariantes

`ClientServiceImpl.upcomingBirthdays` trata `isWhatsapp` nulo com o comentário "Nulo em linha
antiga: o padrão da coluna é 'é WhatsApp'". Mas a V2 define
`is_whatsapp BOOLEAN NOT NULL DEFAULT true`: o `NOT NULL` torna o caso impossível, e o ramo
inalcançável. O código é inofensivo; o comentário afirma um cenário que o schema proíbe. Ou o
comentário muda, ou a coluna deveria ser anulável — as duas coisas não podem estar certas.

---

## O que falta implementar

Verificado por ausência de entidade em `model/` e por `grep` sobre `src/main`.

### Fase C — domínio previdenciário (quase toda em aberto)

| Item | Estado |
| --- | --- |
| Processos administrativos/judiciais com prazos | **Não existe entidade.** É o maior buraco funcional |
| Agendador | **Nenhum `@Scheduled` nem `@EnableScheduling` no projeto** |
| Notificações por e-mail | Nenhum `JavaMailSender` nem `starter-mail` |
| Notificações por WhatsApp | Nenhuma integração; plano em [NOTIFICACOES_E_MENSAGERIA.md](NOTIFICACOES_E_MENSAGERIA.md) |
| Cálculo previdenciário | Não existe. Hoje `client_files` (kind=SIMULATION) só guarda o PDF e metadados digitados |
| Expurgo definitivo após retenção | Só soft delete |
| Verificação de vírus no upload | Nenhum ClamAV. MIME é validado (há teste recusando `.exe`), conteúdo não é varrido |
| Contratos de honorários com % de êxito | Não existe entidade |
| Relatório financeiro consolidado | Parcial: `OfficeRevenue`/`OfficeExpense` e séries mensais existem; falta o contrato alimentando a receita |

A ausência de agendador bloqueia de uma vez alerta de prazo, parcela vencendo, expurgo por
retenção e qualquer notificação proativa. É uma dependência única com quatro itens atrás dela —
e é o coração do domínio previdenciário, onde perder prazo é perder o direito do cliente.

Um ponto a favor: `AppointmentType` já tem `PERICIA`, `AUDIENCIA` e `PRAZO`, então a agenda
sustenta o evento. Falta o processo a que ele pertence.

### Fase D — escala

| Item | Estado |
| --- | --- |
| Multi-tenancy | **Implementado** (o ROADMAP ainda a lista como hipótese) |
| Backup automatizado e plano de recuperação | Não implementado — ver item 1 acima |

### Lacuna aceita e documentada

A trilha de auditoria não registra remoção em cascata feita pelo Postgres: `ON DELETE CASCADE`
não passa pelo Hibernate, então só a remoção do cliente é auditada, não cada filho. Está
consciente e escrito; fica aqui para não ser redescoberto como novidade.

### ADR-0003 — microsserviços

Desenho registrado, execução não iniciada. O próprio ADR é honesto que não é necessidade técnica
do porte atual. Não tratar como pendência.

---

## Ordem sugerida

1. **Pôr a instância em S3** (item 0). Hoje os arquivos dos clientes existem em uma única
   máquina, num volume 89,5% cheio, e o pipeline não acusa.
2. **Backup** (`pg_dump` diário + restore testado). É o único item cuja ausência causa perda
   irreversível — e enquanto o storage for local, os arquivos também não têm cópia.
3. **Crescer o volume EBS.** Barato, e o `prune` só adia o problema.
4. **Fechar os defaults de configuração** — falhando o boot em vez de assumir valor de dev. O
   `APP_STORAGE_TYPE:local` do item 0 é o mesmo padrão, e já custou.
5. **Job pós-deploy** rodando Postman contra a instância, com o teste de upload do item 0-bis.
6. **Testes de receita do escritório** e do caminho de JWT.
7. **Atualizar o ROADMAP** para refletir o que existe.
8. Só então Fase C, começando pelo **agendador**, que destrava quatro itens.
