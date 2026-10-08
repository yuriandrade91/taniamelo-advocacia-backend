# Binários de upload da coleção Postman

Dois arquivos, versionados de propósito: a pasta **8. Arquivos do cliente** da
coleção os referencia por caminho relativo (`fixtures/...`), e sem eles aquelas
três requisições falham com "file not found" em qualquer máquina que não seja a
de quem gravou a coleção.

| Arquivo | Usado em | Por quê |
|---|---|---|
| `exemplo.pdf` | *Enviar documento (PDF)*, *Enviar simulação (PDF)* | PDF mínimo e válido (191 bytes). O teste é sobre o upload, não sobre o conteúdo — PDF de verdade só deixaria o repositório mais pesado. |
| `malicioso.exe` | *Executável é recusado no upload* | 4 bytes: só a assinatura `MZ` do cabeçalho PE. Prova que a API recusa executável **pelo tipo**, e é por isso que não pode ser um `.txt` renomeado. |

O nome `malicioso.exe` descreve o papel no teste, não o conteúdo: não há código
executável aqui, e não haveria espaço para ele em 4 bytes. Um antivírus que
reclame deste arquivo está reclamando dos dois primeiros bytes.

O caminho é resolvido a partir de `api-tests/postman/` — o runner define esse
`workingDir` justamente para isso (ver `scripts/run-all-postman.mjs`). Abrindo a
coleção no app do Postman, aponte os três campos de arquivo para cá na primeira
execução; o app guarda o caminho absoluto no perfil da máquina, não na coleção.
