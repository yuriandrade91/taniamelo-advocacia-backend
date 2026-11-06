# Decisões — o que já está decidido e o que espera uma pessoa

A sessão do Claude **lê este arquivo antes de planejar**. Decisão **TOMADA** vale como requisito.
Decisão **PENDENTE** bloqueia o passo que depende dela: a sessão para e pergunta, sem escolher
sozinha. Quando uma pendente for respondida, edite a linha (status, resposta, data, quem).

## Tomadas

| # | Decisão | Valor | Por quê |
| --- | --- | --- | --- |
| T1 | Cálculo previdenciário | **Código determinístico, sem IA** | Mesma entrada → mesmo número; auditável |
| T2 | Leitura do CNIS | Parser determinístico (PDFBox) primeiro; IA só se o parser não atingir o critério | PLANO_IA_LLM_RAG §4.1 |
| T3 | Robô no Meu INSS com a senha do cliente | **Não fazer** | Risco jurídico e de segurança |
| T4 | Acervo próprio de jurisprudência (RAG) | **Não construir**; busca na hora em fonte oficial | PLANO_IA_LLM_RAG §6–7 |
| T5 | Peça gerada por IA | Nasce **minuta** em editor; exportar exige revisão marcada por ADVOGADO e zero citação não verificada | Responsabilidade profissional |
| T6 | Ferramentas de IA | **Somente leitura**; gravar é sempre clique humano fora do modelo | Injeção de prompt por texto de terceiros |
| T7 | Provedor/modelos | Anthropic, SDK `com.anthropic:anthropic-java`; `claude-opus-5-5` (redação, análise, jurisprudência), `claude-haiku-5-5` (volume: briefing, classificação) — nomes em configuração | Custo e qualidade (PLANO §4.2) |
| T8 | Papéis — CNIS | Importar/editar: qualquer autenticado. **Confirmar, descartar, excluir vínculo: ADVOGADO** | Confirmado alimenta cálculo jurídico |
| T9 | Papéis — simulação | Criar/ler: qualquer autenticado. Excluir: ADVOGADO. Gerar relatório: ADVOGADO | Relatório vai ao cliente com o nome do escritório |
| T10 | Papéis — IA | Configurar IA do escritório: ADMIN. Registrar consentimento médico: ADVOGADO. Usar recursos: qualquer autenticado, se o escritório ligou | — |
| T11 | Tabelas de referência | Em `public` (shared), atualizadas por **migration nova** a cada publicação oficial | Versionado, revisado, igual para todos |
| T12 | Agendador | `@EnableScheduling` na instância única atual; o job itera os tenants ativos | Uma instância hoje; documentar o limite |
| T13 | Prazo vindo de publicação | **Sugerido**, nunca gravado sozinho; a pessoa confirma com um clique | Prazo errado é perda de direito |
| T14 | Exportação de peça | PDF (via `openhtmltopdf`) + copiar HTML | Sem dependência nova; DOCX é D8 |
| T15 | MCP | Processo separado em `mcp-server/` (TypeScript), STDIO, chamando a API REST com a credencial de quem usa | Mesma autorização da API; nada de IA dentro do backend |
| T16 | Front | Telas novas seguem o `CLAUDE.md` do front; sem rota dinâmica | Export estático |

## Pendentes (bloqueiam o passo indicado)

| # | Pergunta | Bloqueia | Quem responde | Resposta |
| --- | --- | --- | --- | --- |
| D1 | A banca autoriza enviar conteúdo de cliente ao provedor de IA sob contrato com **retenção zero**? | 10 em diante (produção) | Banca | PENDENTE |
| D2 | Retenção zero contratada com a Anthropic (org/console)? | 10 (produção) | Responsável técnico | PENDENTE |
| D3 | Teto mensal de gasto de IA por escritório (US$) e cota padrão | 10 | Produto | PENDENTE (sugestão: US$ 60) |
| D4 | Texto do termo de consentimento para documento médico em IA | 10, 11 | Advogada | PENDENTE |
| D5 | **Parâmetros legais do motor** conferidos e assinados (tabela do `docs/MOTOR_PREVIDENCIARIO.md`) | 06–07 (merge) | Advogada | PENDENTE |
| D6 | **Casos-ouro:** CNIS + carta de concessão de clientes antigos, anonimizados, no formato do passo 06, guardados **fora do repositório** (`BENEFIT_CASES_DIR`) | 07 (merge) | Escritório | PENDENTE |
| D7 | Conjunto de avaliação do parser: 20 PDFs de CNIS reais, **fora do repositório**, com o esperado conferido | 03 (critério ≥ 98%) | Escritório | PENDENTE |
| D8 | Exportar peça em DOCX além de PDF? | 15 | Advogada | PENDENTE (padrão: só PDF) |
| D9 | Fonte das publicações: API pública do DJEN (`comunicaapi.pje.jus.br`) serve? Se não, qual agregador pago? | 13 | Responsável técnico | PENDENTE (o passo 13 começa verificando) |
| D10 | OAB(s) monitorada(s) por escritório (número/UF de cada advogada) | 13 | Escritório | PENDENTE |
| D11 | Aprovação do modelo do relatório PDF (amostra gerada no passo 09) | 09 (merge) | Advogada | PENDENTE |
| D12 | Modelos de peça do escritório (texto-base de cada tipo) | 15 | Advogada | PENDENTE |
| D13 | Domínios oficiais permitidos na busca de jurisprudência (lista inicial no passo 14) | 14 | Advogada | PENDENTE |

## Como uma sessão trata uma pendente

- **Para desenvolver:** pode avançar com dado **fictício** onde a pendente é só de produção
  (D1, D2), deixando a funcionalidade atrás da flag desligada.
- **Para concluir:** critério de aceite que dependa de pendente fica `[ ]` com o motivo no
  relatório. Nunca marque como feito.
- **Nunca** preencha uma pendente por conta própria, nem com "valor provisório razoável".
