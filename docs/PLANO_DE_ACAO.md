# Plano de ação — o que está aberto e em que ordem fechar

Levantamento de 10/09/2026, feito sobre o código e sobre a instância em execução,
não sobre a memória de quem escreveu.

## Fechado depois deste levantamento

Quatro lacunas de maturidade que não estavam nesta lista — apareceram ao avaliar
se o caminho estava certo, e foram fechadas antes de seguir. Ficam registradas
porque mudam o contrato da API:

| O que era | O que é agora |
|---|---|
| `pageSize` sem teto: `pageSize=100000` montava a base inteira em memória | teto de **100**, cortado no valor em vez de recusado com 400 |
| Autenticado era autorizado: qualquer token excluía, restaurava e listava usuários | **STAFF opera, só ADMIN/LAWYER destrói** (`@RequerAdvogado` + `@EnableMethodSecurity`) |
| Senha do INSS voltava em toda abertura de ficha | saiu do `GET /clients/{id}`; sai por `GET /clients/{id}/inss-password`, restrita e **auditada**. Na edição, ausente = mantém |
| `POST /auth/login` sem limite de tentativas | freio por IP e por login (memória) **+** bloqueio de conta por 15 min (banco); 429 com `Retry-After` |

E um defeito encontrado ao validar as quatro: **busca por nome com algarismo
virava busca por CPF** — o termo tinha os dígitos extraídos e virava
`cpf LIKE '%2%'`, que casa com quase toda a base. Procurar "Maria 2ª" trazia meio
escritório. O ramo do CPF agora só entra em termo sem letra e com pelo menos 3
dígitos.

Item 16 abaixo (`.claude/` sem `.gitignore`) também está fechado.

---

## Como ler

**Severidade** é o custo de deixar como está, não a dificuldade de arrumar:

| | Significa |
|---|---|
| **P0** | está no ar e errado agora |
| **P1** | trava o próximo passo de todo o resto |
| **P2** | tem contorno, e o contorno custa caro todo dia |
| **P3** | falta, mas ninguém está sangrando |

---

## P0 — Está no ar e errado agora

### 1. Três defeitos corrigidos que não foram publicados

`origin/develop` e a instância da AWS estão no commit `c5a1d50`. As correções
estão em **7 commits locais que ninguém enviou**. Quer dizer: tudo abaixo é o
comportamento de hoje, em `http://98.82.73.175:8080`.

| Defeito | O que acontece hoje |
|---|---|
| `POST /clients` sem `nationality`, `isWhatsapp` e `hasDisability` | **409 `DATABASE_INTEGRITY_ERROR`**. Os três são `NOT NULL` no banco e opcionais no Swagger — seguir a documentação à risca é o caminho para o erro. Só não aparece porque o frontend manda os três sempre. |
| `X-Tenant-Id` com valor desconhecido | Cai no schema **default**, que é `tenant_tania` — o escritório real. Um erro de digitação no cabeçalho autentica na base errada, em silêncio. |
| `@Valid` em `@RequestPart` (upload de arquivo) | **500**: *"Erro interno do sistema… contate o suporte"*. A API culpando a si mesma por um campo que faltou no corpo. |

O terceiro é chato; o segundo é de segurança.

**Ação:** `git push` e deploy. É o item mais barato e o de maior efeito da lista
inteira.

---

## P1 — Trava o próximo passo

### 2. A suíte que achou os defeitos não protege nada

`api-tests/` (205 testes) e a coleção Postman (359 asserções) **não aparecem em
nenhum workflow**. Hoje elas só rodam quando alguém lembra de rodar.

Enquanto isso valer, o próximo defeito da mesma família entra do mesmo jeito —
e a suíte vai continuar achando *depois*, que é quando não adianta.

**Ação:** um job `api-tests` no CI, disparado **depois do deploy**, contra o
escritório `demo` da instância de desenvolvimento. Credenciais em secret. É o
único teste que responde "o que subiu funciona?".

### 3. O que o CI valida não é o que roda na instância

O job `docker-build` monta a imagem e a descarta. O deploy entra por SSH, faz
`git pull` e `mvn package` **na própria instância**. São dois artefatos
diferentes: o Dockerfile é validado e nunca usado; o que roda é compilado numa
`t4g` que ninguém inspeciona.

É o Passo 2 do plano de infra, adiado duas vezes.

**Ação:** CI publica a imagem no ECR; a instância faz `docker pull` e reinicia.
O deploy deixa de compilar e passa a buscar um artefato assinado pelo CI.

### 4. 107 arquivos sem commit no frontend

A branch `feature/clients-page` acumula 107 arquivos modificados. Não há ponto de
retorno, não dá para revisar, e um `git checkout` errado apaga semanas.

**Ação:** quebrar em commits por assunto, como fizemos no backend. É trabalho
chato e de uma tarde só.

---

## P2 — Contorno caro todo dia

### 5. O frontend não colhe o que o backend já entrega

Quatro rotas existem, foram testadas e **nenhuma tela usa**:

| Já existe no backend | A tela ainda faz |
|---|---|
| `PATCH /appointments/{id}/restore` | Segura a requisição por 5 s (`usePendingAction`) para poder "desfazer". O comentário no arquivo ainda diz *"o delete é soft delete sem rota de restore"* — deixou de ser verdade. **Enquanto a janela corre, a ação não está persistida: fechar a aba perde.** |
| `GET /users` | Nada. `createdBy`, `updatedBy`, `responsibleUserId` e `changedByUserId` continuam UUID na tela. |
| `previousSituation` no histórico | `SituationHistorySection` ainda diz *"o DTO não traz a anterior"* e mostra só "passou para X". |
| `POST /clients/{id}/addresses/batch` | Um POST por endereço, sem transação comum: falha no segundo deixa a ficha pela metade. |

São quatro mudanças pequenas, independentes entre si, e cada uma remove um
contorno que hoje custa em confiabilidade ou em clareza.

### 6. Lacuna B1 — a Carteira e os Pagamentos não têm servidor

`/api/v1/payments`, `/expenses` e `/revenues` não existem. Três serviços do
frontend (`officePaymentService`, `officeExpenseService`, `officeRevenueService`)
estão escritos, tipados e sem nada do outro lado.

Efeito: a tela de Pagamentos lista vazio, e a Carteira não tem **nenhuma das duas
metades** do caixa. `expenses` e `revenues` não têm nem tabela.

É a maior lacuna aberta, e é uma feature inteira — não um conserto.

### 7. Lacuna B2 — lançar pagamento já recebido são duas requisições

`ClientPaymentRequestDTO` não tem `paidDate`; o `POST` cria sempre `PENDENTE` e
um `PATCH` marca pago. Se o segundo falhar, a parcela fica pendente sem que
ninguém tenha errado.

Conserto pequeno: aceitar `paidDate` na criação.

### 8. Lacuna B3 — a ficha do cliente são cinco requisições

`GET /clients/{id}` não traz endereços, entrevistas nem arquivos. A modal busca
cada um e usa `allSettled` para que um 404 não derrube a ficha inteira.

Não é defeito, é latência — e um `?include=addresses,interviews` resolveria sem
inchar o payload de quem não precisa.

### 9. Guarda de coluna `NOT NULL` duplicada em três lugares

A mesma regra ("campo ausente não pode virar null nessas cinco colunas") está no
`ClientMapper` (create e update) e repetida à mão em
`ClientServiceImpl.updatePersonalData`. A terceira cópia é a que vai divergir.

### 10. `listSummary` com oito parâmetros posicionais

Cinco deles são filtros, e dois `List<…>` adjacentes trocam de lugar sem o
compilador reclamar — foi exatamente o que quebrou a suíte quando `clientType`
entrou. A agenda já resolveu isso com `AppointmentSearchParams` +
`@ParameterObject`; clientes não.

---

## P3 — Falta, mas ninguém está sangrando

11. **Compromisso não tem responsável.** Só `createdBy`. Com mais de um
    advogado, "de quem é essa audiência?" não tem resposta. Cliente já tem
    `responsibleUserId`; compromisso não.
12. **Agenda sem filtro por modalidade nem por responsável.**
13. **Nenhum lembrete.** Existe `NOTIFICACOES_E_MENSAGERIA.md`, e a agenda não
    dispara nada. É o que separa uma agenda de uma lista.
14. **Lacuna B7 — a análise do CNIS não tem onde ser guardada.** Roda inteira no
    navegador e se perde ao fechar a tela; por isso ela existe só no cadastro e
    não na ficha. A tabela de correção monetária mora no `localStorage` de cada
    máquina: não é do escritório, é de quem colou.
15. **Camadas 2 e 3 do plano de testes não começaram.** A Fase 0 destravou o CI;
    as fases por feature (integração e E2E com Rest-assured) continuam no papel.
16. ~~**`.claude/` sem `.gitignore`.**~~ Fechado.

---

## Plano de ação

Em ondas, porque a ordem importa: cada uma torna a seguinte verificável.

### Onda 0 — hoje, meia hora

1. `git push` dos 7 commits.
2. Confirmar o deploy: `GET /actuator/info` tem de mudar de `c5a1d50`.
3. Rodar a suíte contra a AWS com credenciais de um usuário de teste:
   ```bash
   cd api-tests && npm install
   API_LOGIN=... API_PASSWORD=... npm test
   ```
   **Antes do deploy os testes falham de propósito** — os três defeitos ainda
   estão lá. É essa transição de vermelho para verde que prova as duas coisas ao
   mesmo tempo.

> Fecha o **P0**. Nada abaixo faz sentido antes disto.

### Onda 1 — a rede de segurança, 1 dia

4. Job `api-tests` no CI, depois do deploy, contra o `demo`.
5. `newman` no mesmo job, ou como passo separado.
6. Passo 2 da infra: imagem no ECR, `docker pull` na instância.

> Fecha **2** e **3**. A partir daqui, defeito da mesma família não volta.

### Onda 2 — colher o que já existe, 1 a 2 dias

Quatro tarefas independentes, qualquer ordem:

7. Trocar a janela de 5 s por `restore` de verdade — e apagar o comentário que
   virou mentira. **Esta é a de maior efeito:** hoje a ação não está persistida
   enquanto a janela corre.
8. Consumir `GET /users` para exibir autoria.
9. Mostrar "de X para Y" no histórico de situação.
10. Usar `addresses/batch` no cadastro.

> Fecha **5**. Nenhuma exige backend novo.

### Onda 3 — os consertos baratos de contrato, 1 dia

11. `paidDate` no `POST` de pagamento (B2).
12. `?include=` em `GET /clients/{id}` (B3).
13. Dívidas de código **9** e **10**, que são refactor com teste já existente.

### Onda 4 — o financeiro do escritório, 1 a 2 semanas

14. Tabelas, migrations e rotas de `/payments`, `/expenses` e `/revenues` (B1),
    seguindo `ESPEC_FINANCEIRO.md`.

> É feature, não conserto. Merece ser tratada como tal — e é o que devolve a
> Carteira e a tela de Pagamentos.

### Onda 5 — quando as anteriores fecharem

15. Responsável no compromisso e filtros correspondentes.
16. Lembretes da agenda.
17. Persistência da análise do CNIS (B7).
18. Camadas 2 e 3 do plano de testes.

### Contínuo

- Commits do frontend (**4**) — não depende de nada, e quanto mais tarde, pior.
- `.claude/` no `.gitignore` (**16**) — trinta segundos.

---

## O que NÃO é problema

Três coisas que pareciam ser e não são. Ficam registradas porque foram afirmadas
por engano ao longo do caminho, e alguém pode reencontrá-las:

- **`isPrimary` de endereço.** É coordenado no servidor desde sempre:
  `ClientAddressService` desmarca o anterior no create e no update, promove o
  mais antigo no delete, e o banco garante com índice único parcial desde a V3.
- **A guarda de edição de compromisso não quebrou a tela.** O aviso de que
  "editar concluído/cancelado passa a dar erro na interface" estava errado: a
  agenda já oferece editar só em `Agendado`, e "Reagendar" um cancelado abre o
  drawer de **criação**, não de edição.
- **`PUT` das abas de dados apagar campo omitido** é o contrato, não um defeito.
  PUT substitui a aba inteira, igual a `PUT /clients/{id}`.
