# Acesso de suporte multi-tenant (impersonation)

Como a equipe de suporte da plataforma acessa qualquer escritório **sem ter um
usuário em cada tenant**. Uma identidade no control-plane, sessão curta e
auditada por escritório, com **acesso total** durante o troubleshooting.

## Por que assim

Cada tenant é um schema com sua própria tabela `users`. Dar ao suporte um
usuário por escritório vira sprawl de credenciais (onboarding/offboarding em N
schemas) e atribuição de auditoria fraca — ruim com dado sensível (LGPD, senha
do INSS). A abordagem: **uma** identidade de plataforma em `public.support_users`
e impersonation explícita, curta e rastreada.

## Fluxo (2 passos)

```
1) POST /api/v1/support/login            (email + senha)
   -> token de PLATAFORMA (scope=platform). Só abre sessões; não acessa tenant.

2) POST /api/v1/support/sessions/{tenantId}   (Authorization: Bearer <token de plataforma>)
   -> token de SESSÃO (impersonation): tenant fixado, role ADMIN, curto (~20 min).
      Use como Bearer nas rotas /api/v1/** do escritório. NÃO precisa de X-Tenant-Id
      (o tenant está no token).
```

`tenantId` aceita o **slug** (`demo`) ou o **UUID público** do escritório.

## Acesso total, mas rastreável

- O token de sessão carrega `role=ADMIN`, então passa por `@RequerAdvogado` **e**
  `@RequerAdmin` — o suporte faz tudo, inclusive o que é ADMIN-only. Foi decisão
  explícita: escopo reduzido atrapalharia o troubleshooting.
- A rede de segurança é a **auditoria**, não a limitação de poder. Como o agente
  não é usuário do escritório, as colunas `created_by`/`updated_by`/`performed_by`
  (FK para `users`) ficam **NULL**; a atribuição real vai em
  **`audit_log.acting_support_user_id`** (id do `support_user`). Toda ação fica
  amarrada à pessoa real.
- Abertura de sessão também é logada (agente + tenant).

## Configuração

| Variável | Default | Papel |
|----------|---------|-------|
| `APP_SUPPORT_EMAIL` / `APP_SUPPORT_PASSWORD` | dev óbvio | Agente inicial, semeado no 1º boot se `public.support_users` vazio |
| `APP_JWT_SUPPORT_PLATFORM_MINUTES` | 60 | TTL do token de plataforma |
| `APP_JWT_SUPPORT_SESSION_MINUTES` | 20 | TTL do token de sessão (impersonation) |

O agente inicial é criado pelo `SupportUserSeeder` (control-plane, não por tenant).
**Troque a senha em produção.**

## Isolamento (o que impede abuso)

- O token de **plataforma** só é aceito em `/api/v1/support/**` (o
  `JwtAuthenticationFilter` recusa em qualquer outra rota) — sozinho não lê nada
  de escritório.
- O token de **sessão** vale só para o tenant que o emitiu: o filtro exige que o
  tenant do token bata com o da requisição (mesma regra dos usuários normais).
- `/api/v1/support/**` (fora o login) exige `ROLE_PLATFORM`; o login entra no
  throttle por IP+login, como o `/auth/login`.

## Limites / evolução

- Não há refresh para o token de plataforma: expirou, loga de novo (é raro e
  barato). O token de sessão é curto de propósito.
- Sem tela de troca de senha do agente ainda (roadmap).
- Se o time crescer, trocar `public.support_users` por **SSO/OIDC corporativo**
  com um grupo que concede o escopo de plataforma — a arquitetura (sessão curta
  + auditoria do ator) não muda, só a origem da identidade.
