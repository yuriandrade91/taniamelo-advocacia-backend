# Backup e restore do banco

Dois scripts, ambos rodando contra o serviço `db` do compose (local ou na EC2),
lendo `POSTGRES_USER`/`POSTGRES_DB` do `.env`:

| Script | O que faz |
|---|---|
| `scripts/backup-db.sh` | `pg_dump` do banco inteiro (catálogo `public` + todos os schemas de escritório) em `backups/<banco>-<data UTC>.dump`, permissão `600`. Confere que o arquivo é legível de ponta a ponta e apaga os com mais de 7 dias. |
| `scripts/restore-db.sh <arquivo>` | **Teste** (padrão): restaura num banco descartável, compara a contagem de linhas de cada tabela com o banco atual e apaga o descartável. Não toca no banco da aplicação. |
| `scripts/restore-db.sh <arquivo> --producao` | Sobrescreve o banco da aplicação. Recusa com o `app` rodando e pede o nome do banco digitado. Irreversível. |

Variáveis opcionais: `BACKUP_DIR` (padrão `backups`) e `BACKUP_RETENTION_DAYS`
(padrão `7`). `backups/` está no `.gitignore` e não entra na imagem Docker.

## O que este backup protege, e o que não

O dump fica **no mesmo disco** do banco. Protege de erro humano e de software:
`DROP SCHEMA` errado, migration que destruiu dado, `docker compose down -v`.
**Não** protege de perder o volume EBS ou a instância. Para isso o arquivo
precisa sair da máquina — ver "Pendente" abaixo.

Os arquivos dos clientes (PDFs) não estão no dump: enquanto o storage for
`local`, eles ficam em `/app/storage` (volume `app-storage`) sem cópia. Com
`APP_STORAGE_TYPE=s3`, o bucket é que precisa de versionamento.

## Agendar na EC2

Diário às 03:00 UTC, com log:

```bash
# Amazon Linux 2023 não traz cron: sudo dnf install -y cronie && sudo systemctl enable --now crond
crontab -e
0 3 * * * cd ~/taniamelo-advocacia-backend && bash scripts/backup-db.sh >> ~/backup-db.log 2>&1
```

Depois de agendar, e de tempos em tempos, prove que o último backup restaura:

```bash
bash scripts/restore-db.sh "$(ls -t backups/*.dump | head -1)"
# "OK: contagem idêntica..." ou a lista do que mudou desde o dump
```

## Pendente (precisa de decisão/acesso de quem opera a instância)

- **Cópia fora da instância**: destino (S3 com versionamento, Cloudflare R2 —
  o plano em [CI_CD.md](CI_CD.md)) e a credencial para enviar. Com o destino
  definido, é uma linha a mais no cron depois do `backup-db.sh`.
- **Agendamento na EC2** (o cron acima).
- **Exercício de restore** na instância de verdade, com o tempo que levou
  anotado — é o número que diz quanto tempo o escritório fica parado.
