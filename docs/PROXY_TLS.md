# Proxy TLS (Caddy): HTTPS da API, passo a passo

O proxy é o único serviço com porta pública. Ele termina o HTTPS, obtém e renova o certificado
sozinho (Let's Encrypt) e repassa para a aplicação pela rede interna do compose. A porta 8080 do app
só escuta em `127.0.0.1`. Configuração em [proxy/Caddyfile](../proxy/Caddyfile) e no serviço `proxy` do
[docker-compose.yml](../docker-compose.yml).

```
Internet ──443──▶ [ Caddy ] ──rede interna──▶ [ app:8080 ] ──▶ [ db:5432 ]
                    │  certificado Let's Encrypt, HSTS, limite de corpo 70 MB
                    └─ 80: só o desafio do certificado e o redirecionamento para HTTPS
```

## Antes de começar

- Um **nome de domínio** que você controle (ex.: `api.seudominio.adv.br`). Certificado Let's Encrypt
  não é emitido para IP puro.
- A instância com **Elastic IP** (o IP público normal muda a cada parada) e o Security Group com as
  portas **80 e 443** abertas ao mundo, TCP e UDP na 443 (HTTP/3). Ver `AWS_EC2_ARM64_DEPLOY.md`, A.2.
- O `.env` preenchido (passo 3 abaixo).

## Passo a passo

### 1. Apontar o DNS

No provedor do domínio (ou no Route 53), crie um registro **A** do nome da API para o Elastic IP:

| Tipo | Nome | Valor |
|---|---|---|
| A | `api` | `<Elastic IP>` |

Confira antes de continuar (pode levar de minutos a horas):

```bash
dig +short api.seudominio.adv.br     # tem de devolver o Elastic IP
```

> **Não suba o proxy antes de o DNS responder.** Sem isso a emissão do certificado falha e o Let's
> Encrypt limita as tentativas por hora (e por domínio).

### 2. Conferir as portas

Na instância, as portas 80 e 443 não podem estar em uso por outro processo:

```bash
sudo ss -tlnp | grep -E ':(80|443) '    # não deve listar nada antes de subir
```

### 3. Configurar o `.env`

```bash
SITE_DOMAIN=api.seudominio.adv.br
APP_CORS_ALLOWED_ORIGINS=https://app.seudominio.adv.br      # o domínio do FRONT, com https
APP_AUTH_REFRESH_COOKIE_SECURE=true
# Só se o front estiver em OUTRO domínio que a API (ex.: front em app.x.com e API em api.y.com):
# APP_AUTH_REFRESH_COOKIE_SAMESITE=None
```

`SITE_DOMAIN` com valor de exemplo faz `restart-app-docker.sh` recusar o deploy.

### 4. Subir

```bash
bash scripts/restart-app-docker.sh
docker compose logs -f proxy        # procure "certificate obtained successfully"
```

Na primeira vez o Caddy pede o certificado ao Let's Encrypt (alguns segundos). Os certificados ficam no
volume `caddy-data`: **não apague esse volume**, ou o próximo boot emite de novo (e pode esbarrar no
limite de emissões).

### 5. Verificar

```bash
curl -sI https://api.seudominio.adv.br/actuator/health     # HTTP/2 200
curl -sI http://api.seudominio.adv.br/actuator/health      # 308, redireciona para https
curl -s  https://api.seudominio.adv.br/actuator/info       # commit e "storage":{"type":"s3",...}
curl -m 5 http://<Elastic IP>:8080/actuator/health         # tem de FALHAR: a 8080 não é pública
```

E em [ssllabs.com/ssltest](https://www.ssllabs.com/ssltest/) a nota esperada é A ou A+.

### 6. Apontar o front

O front passa a chamar `https://api.seudominio.adv.br/api/v1/...`. Se a API e o front estiverem em
**domínios diferentes**, o cookie do refresh só funciona com `SameSite=None` e `Secure` (passo 3), e o
CORS tem de listar exatamente a origem do front.

## O que o Caddyfile faz, e por quê

| Diretiva | Efeito |
|---|---|
| `{$SITE_DOMAIN}` | Atende só esse nome; é ele que dispara a emissão do certificado |
| `request_body max_size 70MB` | Recusa corpo acima do limite do Spring (60 MB por requisição) antes de a aplicação gastar memória |
| `Strict-Transport-Security` | O navegador passa a usar HTTPS sozinho por 1 ano |
| `reverse_proxy app:8080` | Repassa pela rede interna; os *timeouts* de 5 min deixam subir e descer arquivo grande por uma conexão lenta |
| (padrão do Caddy) | Descarta o `X-Forwarded-For` que o cliente mandar e grava o IP real. O limite de tentativas de login (`AuthController.clientIp`) depende disso; por isso a 8080 não pode ser alcançável por fora do proxy |

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Log do proxy: `challenge failed` / `no valid A records` | O DNS ainda não aponta para a instância, ou aponta para outro IP |
| Log do proxy: `connection refused` na porta 80 | Security Group sem a 80, ou outro processo usando 80/443 |
| Log do proxy: `rate limited` | Muitas tentativas falhas no mesmo domínio; espere (a mensagem diz quando) e corrija a causa antes de tentar |
| Navegador acusa certificado inválido | O proxy ainda está emitindo (aguarde e veja o log), ou `SITE_DOMAIN` não é o nome acessado |
| Login funciona e a sessão cai ao recarregar | Cookie do refresh sem `Secure`, ou front em outro domínio sem `SameSite=None` (passo 3) |
| Upload grande falha com 413 | Passou de 70 MB no proxy, ou de 10 MB por arquivo / 60 MB por requisição no Spring |
| `502 Bad Gateway` | A aplicação ainda sobe (aguarde o health) ou caiu: `docker compose logs app` |

## Renovação e operação

- O Caddy renova o certificado sozinho, a cada ~60 dias, sem reinício. Basta as portas 80/443 seguirem abertas.
- Trocar o domínio: altere `SITE_DOMAIN`, aponte o novo DNS e rode `docker compose --profile full up -d proxy`.
- Validar mudança no `Caddyfile` sem subir nada:

  ```bash
  docker run --rm -e SITE_DOMAIN=api.exemplo.invalid -v "$PWD/proxy/Caddyfile:/etc/caddy/Caddyfile:ro" \
    caddy:2.11 caddy validate --config /etc/caddy/Caddyfile
  ```
