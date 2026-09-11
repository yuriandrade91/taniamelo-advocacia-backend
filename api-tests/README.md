# Testes de contrato da API

Suíte que roda **de fora**, contra uma instância já publicada, sem saber nada do
código. É o que responde *"o que subiu na AWS funciona?"* — pergunta que nenhum
teste de dentro do build responde.

Duas formas de executar os mesmos cenários:

| | Playwright (`api-tests/`) | Postman (`docs/postman/`) |
|---|---|---|
| Para que serve | CI e regressão | conferência manual e exploração |
| Como roda | `pnpm test` | Postman, ou `newman` no terminal |
| Estado atual | **205 testes** | **100 requests, 359 asserções** |
| Ponto forte | fixtures, tipos, limpeza automática | abrir, editar e reenviar na hora |

Não é duplicação por acaso: quem está depurando um erro de campo quer clicar e
reenviar, e quem está barrando um merge quer um comando que devolve verde ou
vermelho. As duas cobrem os mesmos cenários de propósito — se uma passar e a
outra não, é sinal de que uma delas está mentindo.

## O que já não é responsabilidade daqui

- **Regra de negócio isolada** → teste unitário Java (`mvn test`, 849 casos). Se
  dá para afirmar sem subir a aplicação, não é caso desta suíte.
- **Interface** → Playwright do frontend (`taniamelo-advocacia-frontend/e2e`).

## Configuração

```bash
cd api-tests
npm install
cp .env.example .env      # e preencha
```

Variáveis (arquivo `.env`, shell ou secret do CI — **nunca commitadas**):

| Variável | Para quê |
|---|---|
| `API_BASE_URL` | onde a API está. Default: a instância de dev na AWS |
| `API_TENANT` | escritório usado pela suíte. Default: `demo` |
| `API_LOGIN` / `API_PASSWORD` | usuário **dedicado ao teste** |
| `API_TENANT_SECUNDARIO` | segundo escritório, só para provar isolamento |
| `API_LOGIN_STAFF` / `API_PASSWORD_STAFF` | usuário **STAFF**, para provar a autorização por papel |

> **Use `demo`, nunca `tania`.** A suíte cria, cancela e exclui registros. Tudo
> que ela cria nasce marcado com `[api-test]`, para resíduo ser reconhecível no
> meio de dado real se alguma limpeza falhar.

## Rodando

```bash
npm test                  # contra o que estiver em API_BASE_URL
npm run test:local        # contra localhost:8080
npm run test:ui           # modo interativo
npx playwright show-report

npm run newman            # a coleção Postman, mesmo alvo
```

Contra um backend local, suba o Postgres e a aplicação antes:

```bash
docker compose up -d db
mvn spring-boot:run
```

## Como está organizada

```
src/env.ts          configuração; falha alto e cedo quando falta credencial
src/envelope.ts     asserções do envelope { success, data, pagination, errors }
src/factories.ts    massa: CPF válido, janelas de horário livres, marcação
src/fixtures.ts     contextos autenticados (principal, anônimo, outro escritório)

tests/auth.spec.ts                login, refresh, logout, token forjado
tests/tenancy.spec.ts             401 em toda rota protegida + isolamento entre escritórios
tests/clients.crud.spec.ts        cadastro, edição, PATCH, histórico de situação
tests/clients.filters.spec.ts     paginação e todos os filtros da listagem
tests/clients.softdelete.spec.ts  exclusão lógica e restauração
tests/clients.subresources.spec.ts endereços (e lote), entrevistas, abas, pagamentos
tests/clients.files.spec.ts       upload multipart, MIME, download, simulação principal
tests/appointments.crud.spec.ts   agendamento e validações
tests/appointments.conflicts.spec.ts  conflito de horário (avisa, não bloqueia)
tests/appointments.lifecycle.spec.ts  cancelar, concluir, guarda de edição, excluir, restaurar
tests/appointments.filters.spec.ts    filtros da agenda e resumo por mês
tests/clients.inss.spec.ts        a senha do INSS: saiu do GET, sai por rota própria, e a edição não a apaga
tests/roles.spec.ts               autorização por papel: STAFF opera, só ADMIN/LAWYER destrói
tests/users.spec.ts               consulta de usuários e o que ela não pode vazar
```

## Convenções

- **Um worker, em série.** Os testes gravam num banco compartilhado; em paralelo,
  dois deles marcariam o mesmo horário e um falharia por "conflito de horário" —
  que é justamente uma regra sob teste.
- **Cada teste cria a própria massa.** Afirmar sobre dado que já estava no banco
  faz a suíte depender de algo que alguém apaga amanhã.
- **Cada teste limpa o que criou**, em `finally` ou `afterAll`, para um teste que
  falha no meio não deixar lixo para o próximo.
- **Toda rota tem caso feliz e caso negativo.** Rota testada só no caminho feliz
  é rota não testada.
- **Erro se afirma pelo envelope, não só pelo status.** Um 400 com `errors: []`
  deixa quem consome sem saber o que corrigir, e isso é defeito de contrato tanto
  quanto o status errado.
- **Senha errada só contra login descartável.** O backend trava a conta depois de
  5 falhas seguidas e freia o IP depois de 30 por minuto. A suíte gasta ~9
  tentativas por execução (2 contra o usuário real, que qualquer login certo
  zera; 6 numa rajada com login inventado). Cabe rodar Playwright e Postman na
  mesma janela; três execuções seguidas no mesmo minuto, não. Caso novo com senha
  errada usa login descartável — somar ao usuário real derruba a suíte inteira
  com 429.

## Quatro defeitos que esta suíte encontrou

Ficam registrados porque explicam por que vários testes existem:

1. **`POST /clients` mentia sobre campos opcionais.** `nationality`, `isWhatsapp`
   e `hasDisability` são `NOT NULL` no banco e opcionais no Swagger; sem eles o
   `INSERT` quebrava e a API devolvia 409 `DATABASE_INTEGRITY_ERROR`. Seguir a
   documentação à risca era o caminho para o erro.
2. **`X-Tenant-Id` desconhecido caía no escritório default.** Um erro de digitação
   no header autenticava na base do escritório real, em silêncio. Agora é 400
   `TENANT_NOT_FOUND`.
3. **Validação de `@RequestPart` virava 500.** Subir documento sem `documentType`
   devolvia "Erro interno do sistema... contate o suporte" — a API culpando a si
   mesma por um campo que faltou no corpo. Agora é 400 com o nome do campo.
4. **Busca por nome com algarismo virava busca por CPF.** O termo tinha os
   dígitos extraídos e virava `cpf LIKE '%2%'`, que casa com quase toda a base:
   procurar "Maria 2ª" trazia meio escritório. Agora o ramo do CPF só entra em
   termo sem letra e com pelo menos 3 dígitos.

## Execução serial das três coleções Postman

Os snapshots sanitizados ficam em `postman/collections/` e a ordem de execução
fica em `postman/manifest.json`. O runner valida todos os snapshots antes de
iniciar, executa uma coleção por vez e injeta somente em memória os aliases
`baseUrl`, `tenantSlug`, `tenant`, `login`, `loginId` e `password` a partir das
variáveis `API_*`. JSON, JUnit, saída CLI e `summary.json` são separados por
execução em `postman/reports/`. A coleção **Endpoints** aparece como `external`
no resumo.

```bash
npm run postman:all       # alvo definido por API_BASE_URL
npm run postman:aws       # instância AWS de desenvolvimento
npm run postman:local     # http://localhost:8080
npm run postman:public    # somente coleções públicas, sem credenciais
```

Coleções mutáveis exigem `API_LOGIN` e `API_PASSWORD`. O tenant padrão e seguro é
`demo`; outro tenant é recusado, salvo override explícito e consciente:

```bash
POSTMAN_ALLOW_NON_DEMO=true npm run postman:all
# equivalente: node scripts/run-all-postman.mjs --allow-non-demo
```

A senha deve ficar apenas no `.env`, shell ou secret do CI. Ela não é persistida
pelo runner. Esta automação Node é independente do Maven.
