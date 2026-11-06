# ADR-0002 — Refresh token + cookie httpOnly

- **Status:** planejado (não implementar ainda — decisão sua: "só planejar por
  ora"). Implementar **junto da integração do frontend** (item 4).
- **Hoje:** um único JWT de ~8h (`app.jwt.expiration-minutes: 480`) enviado no
  header `Authorization: Bearer`. Sem refresh, sem revogação, sem logout de
  verdade.

## 1. Por que faz sentido (e por que agora não é urgente)

O modelo atual funciona, mas tem três limitações que aparecem assim que o
frontend público entra em cena:

1. **Token longo = janela de risco longa.** Um JWT de 8h roubado vale 8h. Não dá
   para revogar (JWT é stateless).
2. **Sem logout real.** "Sair" hoje é só apagar o token no cliente; o token
   continua válido até expirar.
3. **Guardar JWT no `localStorage` é vulnerável a XSS.** Qualquer script
   injetado lê o token.

A solução padrão: **access token curto (~15 min)** + **refresh token longo
(~7–30 dias) em cookie `httpOnly`+`Secure`+`SameSite`**, com **rotação** e
**revogação server-side**. Fica melhor implementado no mesmo momento em que o
frontend for integrado (o comportamento de cookie/refresh é acoplado ao cliente),
por isso está planejado, não feito.

## 2. Desenho proposto

```
login  ─▶ access token (JWT ~15min, no corpo)  +  refresh token (cookie httpOnly)
                                                     │
requisições ─▶ Authorization: Bearer <access>       │ (expira em 15min)
                                                     ▼
/auth/refresh (envia só o cookie) ─▶ novo access + NOVO refresh (rotação)
/auth/logout  ─▶ revoga o refresh no servidor + limpa o cookie
```

- **Access token:** JWT curto (~15 min), continua no corpo da resposta / header.
  Carrega `uid`, `role` e `office` (multi-tenant, ver ADR-0001).
- **Refresh token:** opaco (não-JWT), aleatório, guardado **hasheado** numa tabela
  `refresh_tokens` (`id, user_id, office_id, token_hash, expires_at, revoked_at,
  user_agent, created_at`). Enviado ao cliente em cookie
  `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh`.
- **Rotação:** todo `/refresh` invalida o refresh usado e emite um novo (detecta
  reuse → revoga a família toda: sinal de roubo).
- **Revogação/logout:** marca `revoked_at`; `/logout` limpa o cookie.

## 3. Endpoints

| Rota | O que faz | Auth |
|---|---|---|
| `POST /api/v1/auth/login` | valida credenciais → access + set-cookie refresh | pública |
| `POST /api/v1/auth/refresh` | valida cookie refresh → novo access + rotaciona | cookie |
| `POST /api/v1/auth/logout` | revoga refresh + limpa cookie | cookie/bearer |

## 4. Pontos de atenção

- **CSRF:** autenticação por cookie reabre o vetor CSRF (hoje desligado porque a
  API é stateless por header). Com refresh em cookie: manter o **access token no
  header** (imune a CSRF) e restringir o cookie ao path `/auth/refresh` com
  `SameSite=Strict`; avaliar CSRF token no `/refresh` se `SameSite` não bastar.
- **CORS + credenciais:** o `CorsConfig` já suporta origens explícitas com
  `allowCredentials(true)` — necessário para o browser mandar o cookie. Em
  produção, origens fixas (não `*`).
- **Multi-tenant:** refresh token amarrado a `user_id` **e** `office_id`; o novo
  access herda o `office` do refresh.
- **Expiração do access:** reduzir `app.jwt.expiration-minutes` de 480 para ~15
  só quando o refresh existir (senão o usuário é deslogado a cada 15 min).

## 5. Plano de execução (quando for a hora)

1. Migration `refresh_tokens`.
2. `RefreshTokenService` (gerar/rotacionar/revogar, hash com o mesmo cuidado de
   senha).
3. Endpoints `/refresh` e `/logout`; `login` seta o cookie.
4. Reduzir TTL do access; ajustar frontend para chamar `/refresh` no 401.
5. Testes de rotação e de detecção de reuse.

## 6. Decisão

Implementar **na mesma rodada da integração do frontend**. Até lá, manter o JWT
de 8h no header (funcional para o uso atual, incluindo a PoC na AWS).
