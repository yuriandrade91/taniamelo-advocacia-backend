# CNIS, cálculo previdenciário, relatório e IA jurídica — plano de ação

**Levantado em 03/11/2026**, sobre a `develop` no commit `92dd308`, tomando como referência o
[Tramitação Inteligente](https://planilha.tramitacaointeligente.com.br/) (planilha previdenciária
+ assistente "TramitaIA").

O pedido: trazer para cá quatro coisas que o Tramitação tem — **importação automática do CNIS**,
**simulação de todas as regras de transição**, **relatório profissional em PDF para o cliente** e
uma **IA** que faça (1) redação de peças, (2) "o que tem para hoje", (3) do cliente à petição,
(4) jurisprudência inteligente e (5) análise de casos —, dizer como fazer, quanto custa, e onde dá
para ser melhor que eles.

> O site deles é renderizado por JavaScript. O que está descrito aqui saiu do código da página
> (textos da demonstração, tabela de planos), não de uso logado. O que eles fazem por dentro é
> inferência a partir do que mostram.

---

## Resposta curta

| Item | Como | Esforço (1 dev full-stack) | Custo mensal de operação |
| --- | --- | --- | --- |
| 1. Importar CNIS | Parser determinístico do PDF (PDFBox, **já está no classpath**); IA só onde o parser falhar | 2–3 semanas | ~zero |
| 2. Regras de transição | **Motor de cálculo em código, sem IA**, com casos-ouro | 5–7 semanas (com tabelas) | zero |
| 3. Relatório PDF | Mesmo caminho da ficha do cliente (`openhtmltopdf`, já em uso) | 1–2 semanas | zero |
| 4. IA (cinco tarefas) | Claude via SDK Java, por cima do motor e do domínio | 11–16 semanas | ~R$ 100–300 por escritório |
| Pré-requisito do 4 | Processos, prazos, publicações e agendador | 3–5 semanas | zero (DJEN é público) |

**Total: ~22–33 semanas de um dev.** O custo de operação é pequeno; o que custa é
desenvolvimento e, sobretudo, **validação jurídica** do motor de cálculo.

Duas conclusões mudam a ordem natural das coisas:

1. **Os itens 1, 2 e 3 não são IA — são o produto.** O Tramitação vende "potencializada por IA",
   mas o número que vai para o cliente sai de regra, não de modelo. Se o cálculo for nosso, testado
   e explicável, a IA vira uma camada por cima — e é aí que dá para ser melhor do que eles.
2. **Duas das cinco tarefas de IA dependem de algo que ainda não existe.** "O que tem para hoje"
   e "do cliente à petição" precisam de **processo, prazo e publicação** — e o
   [MELHORIAS_E_PENDENCIAS.md](MELHORIAS_E_PENDENCIAS.md) registra que não há entidade de processo
   nem `@Scheduled` no projeto. Sem isso, a IA não tem sobre o que falar.

---

## 1. O que o concorrente entrega, de fato

### 1.1 Análise do CNIS (o que a demonstração deles mostra)

Do texto da cena "análise" na página inicial, a planilha lê o PDF do CNIS e aponta:

| Achado | O que faz |
| --- | --- |
| Tempo rural antes do primeiro emprego | Mostra o intervalo desde a infância e o guia para averbar; rural antes de 1991 conta sem contribuição |
| Vínculo com pendência (indicadores) | Explica cada indicador do CNIS e separa o que precisa ser comprovado antes do pedido |
| Lacunas entre vínculos | Calcula os intervalos e **prepara a mensagem de WhatsApp** perguntando ao cliente se trabalhou neles |
| Meses sem salário | Lista cada mês para buscar no contracheque — sem ele a média cai |
| Salários baixos puxando a média | Testa o **descarte** salário a salário (art. 26, §6º, EC 103/2019) e fica com a maior renda |
| Possível atividade especial | Com o PPP anexado, a IA diz período por período se dá para reconhecer |
| Resultado | "Melhor aposentadoria hoje" + cada outra regra com "Em <data>" ou "Não alcança" |

Têm também páginas sobre tema 1070 do STJ (soma de salários em atividades concomitantes),
qualidade de segurado e carência, e a Lei 14.331/2022 (incapacidade).

### 1.2 TramitaIA (as cinco tarefas)

Das cenas da demonstração:

| Tarefa | O que mostram |
| --- | --- |
| Começar o dia | "Da publicação ao prazo na agenda — sem abrir uma tela sequer": lê publicações, calcula prazo (15 dias úteis, 10 dias úteis), põe na agenda |
| Análise de casos | "Arraste o documento. Ele lê, aponta o caminho e redige. Você decide." Ex.: laudo pericial com três pontos objetivos de impugnação |
| Do cliente à petição | "Da ficha do cliente à minuta da peça — na mesma conversa" |
| Redação de peças | Minuta no editor; "troca um parágrafo, não o documento — e confere as citações" |
| Jurisprudência | "Lê o inteiro teor antes de citar — e mostra para onde a jurisprudência caminha" (ex.: 6 acórdãos lidos, corrente favorável com 16 julgados) |

Também já têm **conector MCP para Claude e ChatGPT** — o que o
[PLANO_IA_LLM_RAG.md](PLANO_IA_LLM_RAG.md) §5 propunha como experimento aqui.

### 1.3 Preço deles (tabela pública, 03/11/2026)

| Plano | Mensal | Anual (por mês) | Usuários | Clientes | IA |
| --- | --- | --- | --- | --- | --- |
| Ouro | R$ 209 | R$ 139 | até 3 | até 300 | cota mensal; 3 análises/mês |
| Diamante | R$ 269 | R$ 179 | até 6 | até 1.000 | 2,5× a cota; 10 análises/mês |
| Black | R$ 539 | R$ 359 | até 10 | até 2.000 | 5× a cota; 20 análises/mês |

A IA é **racionada por cota**, e "análise" é contada à parte. Isso diz onde está o custo deles — e
abre espaço (§6).

---

## 2. O que já existe aqui e encurta o caminho

| Peça | Onde | Serve para |
| --- | --- | --- |
| PDFBox | dependência transitiva de `openhtmltopdf-pdfbox` no `pom.xml` | Ler o texto do CNIS sem dependência nova |
| PDF a partir de HTML | `ClientRecordHtmlRenderer`, `ClientRecordService` (ficha do cliente) | Relatório do item 3 é o mesmo padrão |
| CNIS já sobe | `client_files` com `kind=SIMULATION`; `vinculos` hoje é digitado à mão | O ponto de entrada do item 1 já existe |
| `BenefitType` | 7 benefícios canônicos | Ponto de partida das regras do item 2 |
| Agenda com `PRAZO`, `PERICIA`, `AUDIENCIA` | `AppointmentType` | "O que tem para hoje" tem onde gravar |
| Entrevistas, situação, etapas | `ClientInterview`, `ClientSituationHistory`, `ClientRequestStageHistory` | Contexto para "do cliente à petição" |
| Auditoria | `audit_log`, `AuditAction.READ` | Registrar cada uso de IA sobre dado de cliente |
| Multi-tenant por schema | `db/migration/tenant` | Tabelas novas nascem isoladas por escritório |

O que **não** existe e precisa nascer: tabelas de referência previdenciária (índices, teto,
mínimo), entidade de processo, publicações, agendador, cliente da API de IA.

---

## 3. Como fazer cada item

### 3.1 Importação do CNIS

**Desenho:** parser determinístico primeiro, IA só no que ele não resolver — a mesma conclusão do
[PLANO_IA_LLM_RAG.md](PLANO_IA_LLM_RAG.md) §4.1, que continua valendo.

1. **Extração** com PDFBox (`PDFTextStripper` com posição), por regras sobre o layout do CNIS do
   Meu INSS: cabeçalho (NIT, CPF, nome), vínculos (sequência, empregador/CNPJ, tipo de filiado,
   início, fim, indicadores) e a tabela de remunerações/contribuições mês a mês.
2. **Modelo de dados** (migration por tenant): `cnis_extracao` (arquivo de origem, data de
   emissão, versão do parser) → `cnis_vinculo` → `cnis_competencia` (mês, salário, indicadores).
   O `client_files.vinculos` digitado passa a ser derivado.
3. **Validação determinística** antes de gravar: CPF bate com o do cliente, datas em ordem,
   competências dentro do vínculo, totais conferem.
4. **Tela de conferência** lado a lado (PDF × linhas editáveis). Nada entra sem clique.
5. **Fallback com IA** (depois, e só se o parser errar em layout real): PDF para o Claude com saída
   em JSON de schema fechado; a linha fica marcada como extraída por modelo.

**Critério de pronto:** 20 CNIS reais conferidos à mão; ≥ 98% de acerto em datas e valores.

**Não fazer:** login automático no Meu INSS com a senha guardada do cliente para baixar o CNIS.
A senha já é o dado mais sensível do sistema (ver revisão de 01/11 no MELHORIAS); robô logando
com ela é risco jurídico e de segurança que nenhum ganho paga. O cliente ou o escritório baixa o
PDF e sobe.

### 3.2 Simulação das regras de transição

**É código, não IA.** Quem erra aqui entrega ao cliente uma data de aposentadoria errada. O motor
tem que dar a mesma resposta toda vez e mostrar a conta.

**Camadas:**

1. **Tabelas de referência** (migration compartilhada, não por tenant — é dado público igual
   para todos): INPC mensal desde 07/1994, teto e salário mínimo por ano, tabela do fator
   previdenciário (expectativa de sobrevida IBGE por ano). Atualização: rotina mensal + teste
   que falha se o mês corrente não tiver índice.
2. **Apuração** a partir do CNIS conferido: tempo de contribuição (com conversão de especial até
   13/11/2019), carência, qualidade de segurado e período de graça, soma de concomitantes
   (tema 1070 STJ), tempo rural informado à parte.
3. **Regras**, cada uma uma classe com a mesma interface — `avaliar(segurado, data) →
   {elegível, data em que alcança, RMI, memória de cálculo}`:

| Grupo | Regras |
| --- | --- |
| Direito adquirido | Regras anteriores à EC 103, para quem completou requisitos até 13/11/2019 |
| Tempo de contribuição — transição | Pontos (art. 15) · idade mínima progressiva (art. 16) · pedágio 50% (art. 17) · pedágio 100% (art. 20) |
| Idade | Transição da mulher (art. 18) · regra permanente |
| Especial | Pontos 66/76/86 (art. 21) · permanente |
| Professor | Pontos, idade progressiva e pedágio específicos |
| Deficiência | LC 142/2013 (não mudou com a EC) |
| Rural | Idade com tempo rural |

4. **Cálculo da RMI:** média dos salários corrigidos desde 07/1994, coeficiente (60% + 2% por ano
   acima do mínimo), fator previdenciário onde a regra exige, piso e teto, e **otimização de
   descarte** (art. 26, §6º): testar a exclusão das menores contribuições mantendo o direito e
   ficar com a maior renda.
5. **Projeção:** para quem ainda não alcança, a data em que alcança cada regra supondo que siga
   contribuindo (e com qual salário).

**Validação — a parte cara e a que mais importa:**

- **Casos-ouro:** clientes antigos do escritório com **carta de concessão** do INSS. O motor tem
  que chegar à mesma RMI (ou explicar a diferença). É o oráculo mais barato que existe e já está
  nos arquivos do escritório.
- O simulador do próprio Meu INSS como segunda conferência.
- Os parâmetros (pontos, idades, pedágios) são conferidos pela advogada contra o texto da
  EC 103/2019 antes de virar teste. **Este documento não é a fonte das regras** — o texto legal é.

**Esforço:** tabelas 1 semana, apuração 1–2, regras + RMI 3–4. Validação jurídica corre em
paralelo e é de quem responde pela banca.

### 3.3 Relatório em PDF para o cliente

Mesmo padrão de `ClientRecordHtmlRenderer`: HTML com template, `openhtmltopdf` gera o PDF.
Conteúdo:

- Capa com a marca do escritório (logo e cores por tenant).
- **Resumo em linguagem de cliente:** "hoje você se aposenta pela regra X com R$ Y; se esperar
  até <data>, pela regra Z com R$ W".
- Linha do tempo dos vínculos, gráfico de cada regra (alcança / em quando / não alcança).
- **Pendências para o cliente**: lacunas, meses sem salário, vínculos com indicador — cada uma
  com o documento a trazer.
- Anexo técnico: memória de cálculo completa (para o advogado e para quem for conferir).
- Versão e data do cálculo, versão do motor e das tabelas — o relatório é reproduzível.

O PDF gerado é gravado como `client_files` (já há storage), não regerado: o que o cliente recebeu
fica guardado como recebeu.

### 3.4 IA — infraestrutura comum (vem antes das cinco tarefas)

- **SDK oficial `com.anthropic:anthropic-java`**, um serviço `AssistenteJuridico` por trás de
  **feature flag por tenant** (nasce desligado).
- **Modelos:** Claude Opus 5.5 para redação, análise e jurisprudência; Claude Haiku 5.5 para
  tarefas de volume (briefing do dia, classificação de publicação).
- **Montador de contexto com lista de campos proibidos, testada:** senha do INSS nunca entra; na
  primeira rodada, `DOCUMENTOS_MEDICOS` só com consentimento registrado (PLANO_IA_LLM_RAG §9).
- **Retenção zero** contratada com o provedor; registro em `audit_log` de quem pediu, sobre qual
  cliente, qual modelo, quantos tokens.
- **Cota e teto de gasto por tenant**, como o freio de login: defeito não vira conta.
- **Saída estruturada** (JSON com schema) sempre que o resultado alimenta tela ou banco.

**Esforço:** 1–2 semanas.

### 3.5 As cinco tarefas

| Tarefa | Como funciona aqui | Depende de | Esforço |
| --- | --- | --- | --- |
| **Análise de casos** | Sobe laudo/PPP/indeferimento → Claude lê o PDF com a ficha e o resultado do motor → devolve pontos objetivos (achado, página, por que importa, peça sugerida) | 3.4 | 2 semanas |
| **Redação de peças** | Modelos do próprio escritório como base (não texto genérico); editor com edição **por parágrafo**; toda citação passa pelo verificador (abaixo) | 3.4, verificador | 3–4 semanas |
| **Jurisprudência inteligente** | Busca na hora em fontes oficiais (web search do Claude restrita a domínios `*.jus.br`), leitura do inteiro teor, agrupamento em correntes, citação só do que foi lido | 3.4, verificador | 2–3 semanas |
| **O que tem para hoje** | Consulta determinística (prazos, perícias, audiências, publicações novas, aniversariantes, pendências de documento) + resumo curto pelo Haiku. A lista vem do SQL; o modelo só redige | Fase 2 (processo, publicação, agendador) | 1 semana |
| **Do cliente à petição** | Conversa com ferramentas de **leitura** (ficha, entrevistas, CNIS, cálculo, documentos) que termina numa minuta no editor. Reaproveita análise + redação | 3.4, análise, redação | 2 semanas |

**Verificador de citações** (componente próprio, ~1 semana, conta dentro de redação e
jurisprudência): para cada julgado citado, buscar no site do tribunal, confirmar número, órgão,
relator e data, e conferir que o trecho entre aspas existe **literalmente** no inteiro teor.
Citação não confirmada sai marcada em vermelho e bloqueia a exportação da peça até alguém
decidir. É a resposta mecânica ao risco que o PLANO_IA_LLM_RAG §6 apontava — citação inventada em
peça protocolada.

**Coerência com o PLANO_IA_LLM_RAG.md.** Aquele documento disse "não construir RAG sobre
jurisprudência" e "petição só em editor com revisão humana". Este plano segue as duas: **não há
acervo próprio de jurisprudência** para indexar e manter — a busca é feita na hora, na fonte
oficial — e a peça nasce como minuta num editor, com verificador e revisão obrigatória. O que
mudou foi a decisão de produto (o concorrente já entrega isso), não a avaliação de risco.

**MCP (paridade barata):** o servidor MCP só-leitura do PLANO_IA_LLM_RAG §5 é 3–5 dias e iguala o
conector deles.

### 3.6 Fase 2 — processo, prazos e publicações (pré-requisito)

Já listada como Fase C no MELHORIAS. O mínimo para as tarefas de IA:

- Entidade **processo** (administrativo e judicial: número, órgão, fase) ligada ao cliente.
- **Publicações**: consulta diária ao DJEN (Diário de Justiça Eletrônico Nacional) pela OAB das
  advogadas do tenant. O CNJ expõe as comunicações em `comunica.pje.jus.br`; **confirmar o
  contrato da API de consulta antes de orçar** — se não for estável, a alternativa é um
  agregador pago (Judit, Escavador), sob proposta.
- **Cálculo de prazo** em dias úteis com calendário de feriados por tribunal.
- **Agendador** (`@EnableScheduling` ou job externo) — destrava também os alertas e o expurgo que
  o MELHORIAS lista.

**Esforço:** 3–5 semanas.

---

## 4. Custo

### 4.1 Desenvolvimento

| Fase | Semanas (1 dev full-stack, com testes e front) |
| --- | --- |
| 1. CNIS (parser + conferência) | 2–3 |
| 2. Motor de regras (tabelas + apuração + regras + RMI) | 5–7 |
| 3. Relatório PDF | 1–2 |
| 4. Processo, prazos, publicações, agendador | 3–5 |
| 5. Infra de IA | 1–2 |
| 6. Cinco tarefas + verificador + MCP | 10–14 |
| **Total** | **22–33** |

Para converter em reais, multiplique pelo custo da semana do dev. Fora disso, **horas da
advogada** para validar regras e casos-ouro — estimar ~2 semanas distribuídas ao longo das fases
1–3. Sem esse tempo, o motor não vai para produção.

### 4.2 Operação (por escritório, por mês)

Preços públicos da API Claude (US$ por milhão de tokens, entrada / saída): **Opus 5.5 4 / 20**,
**Sonnet 5.5 2 / 10**, **Haiku 5.5 0,10 / 0,50**; lote (batch) com 50% de desconto; leitura de
cache mais barata. Câmbio assumido: **US$ 1 = R$ 5,50**.

| Uso | Tokens por uso (estimativa) | Custo por uso | Volume/mês (escritório de 3) | Mês |
| --- | --- | --- | --- | --- |
| Peça (Opus) | ~30k entrada, ~8k saída | ~US$ 0,28 | 40 | ~US$ 11 |
| Pesquisa de jurisprudência (Opus + busca web) | ~90k entrada (inteiro teor), ~4k saída, ~5 buscas | ~US$ 0,50 | 60 | ~US$ 30 |
| Análise de documento (Opus) | ~45k entrada, ~4k saída | ~US$ 0,26 | 30 | ~US$ 8 |
| Briefing do dia (Haiku) | ~10k entrada, ~1k saída | < US$ 0,01 | 66 | < US$ 1 |
| CNIS pelo fallback de IA | ~40k entrada, ~4k saída | ~US$ 0,24 | 10 | ~US$ 2 |
| **Total** | | | | **~US$ 50 ≈ R$ 280** |

Um escritório leve fica perto de R$ 100. O preço da busca web do Claude e dos agregadores de
publicação deve ser conferido na página do fornecedor antes de fechar preço de plano. Infra
nova: nenhuma — roda na instância atual; o PDF e os índices ficam no Postgres e no storage que já
existem.

**Comparação:** o plano Ouro deles cobra R$ 139–209 por 3 usuários com IA racionada. Nosso custo
de IA para uso pesado é da mesma ordem que o preço inteiro do plano deles — dá para cobrar igual
com cota bem mais folgada, ou cobrar a IA por uso sem surpresa.

---

## 5. Plano de ação

Cada fase tem entrega usável sozinha e um critério para seguir.

| Ordem | Fase | Entrega | Segue se |
| --- | --- | --- | --- |
| 0 | Decisões (1 semana) | Banca decide sobre envio de dado a provedor com retenção zero; separa **20 CNIS conferidos + cartas de concessão**; confirma fonte de publicações | Há conjunto de avaliação e decisão escrita |
| 1 | CNIS | Upload → vínculos e competências conferidos | ≥ 98% de acerto nos 20 CNIS |
| 2 | Motor de regras | Todas as regras com data e RMI, memória de cálculo | RMI bate com as cartas de concessão (ou a diferença é explicada) |
| 3 | Relatório PDF | Relatório entregue ao cliente | Advogada assina o modelo |
| 4 | Infra de IA + análise de casos | Primeiro uso real de IA, de menor risco | Pontos sugeridos aceitos em ≥ metade dos casos |
| 5 | Processo, prazos, publicações | Publicação do dia → prazo na agenda | Uma semana sem prazo perdido ou duplicado |
| 6 | Redação + jurisprudência + verificador | Minuta com citações verificadas | Zero citação não verificada chega à exportação |
| 7 | O que tem para hoje + do cliente à petição + MCP | Paridade completa com o TramitaIA | Uso diário pela equipe |

As fases 1–3 entregam o produto previdenciário inteiro **sem nenhuma IA** — e é onde está o maior
valor. As fases 4–7 podem andar em paralelo com a 5 se houver dois devs.

Este plano entra **depois** dos itens de risco do MELHORIAS (backup fora da instância, S3, disco):
um motor de cálculo em cima de um banco sem cópia não é um produto.

---

## 6. Onde dá para ser melhor que o Tramitação

1. **Número auditável, não opinião.** Cada resultado com memória de cálculo linha a linha,
   versão do motor e das tabelas. O cliente e o juiz conseguem refazer a conta. Publicar a
   **acurácia medida** contra cartas de concessão reais ("o motor bate em X de Y casos") — nenhum
   concorrente mostra isso.
2. **Revisão de benefício já concedido como fonte de receita.** Com motor e carta de concessão no
   sistema, comparar automaticamente a RMI concedida com a calculada e listar quem tem revisão
   possível (descarte não aplicado, concomitantes, tempo especial não convertido). É trabalho
   novo para o escritório saindo de dado que ele já tem.
3. **Verificador de citações visível.** Eles dizem que "conferem as citações"; aqui cada citação
   mostra o selo "conferido no site do tribunal em dd/mm, trecho literal na p. N" e a peça não
   exporta com citação não verificada.
4. **IA que mostra o que leu.** Cada afirmação da análise e da jurisprudência aponta a página do
   documento ou o trecho do acórdão de onde saiu, em um clique.
5. **Profundidade previdenciária em vez de "30 ferramentas".** Eles são generalistas ("toda a sua
   advocacia"). Aqui o domínio já é previdenciário: entrevistas, etapas do requerimento, situação,
   incapacidade. Juntar isso ao cálculo — por exemplo, a entrevista dizer "trabalhou na roça dos
   12 aos 18" e o motor já projetar o ganho do tempo rural — é algo que uma planilha avulsa não faz.
6. **Fluxo com o cliente fechado.** Pendências do CNIS viram checklist do cliente e mensagem de
   WhatsApp (plano em [NOTIFICACOES_E_MENSAGERIA.md](NOTIFICACOES_E_MENSAGERIA.md)); o documento
   que chega dá baixa na pendência e recalcula.
7. **Sigilo como argumento de venda.** Retenção zero, nada usado para treino, laudo médico fora
   da IA sem consentimento, e **cada acesso de IA a dado de cliente no log de auditoria** que o
   escritório pode consultar. Para quem guarda laudo e senha do INSS de cliente, isso pesa na
   decisão.
8. **Cota honesta.** Com custo de ~R$ 100–300 por escritório, dá para oferecer IA sem "3 análises
   por mês" — a cota deles é o ponto mais fácil de superar no preço.

---

## 7. Riscos e o que não fazer

- **Regra errada é dano ao cliente.** O motor só vai para produção com casos-ouro passando e
  parâmetros assinados por quem responde pela banca. Cada mudança legal ou de entendimento vira
  teste novo antes de virar código.
- **Tabelas que envelhecem.** INPC, teto e mínimo mudam todo ano; o motor tem que recusar
  calcular com tabela desatualizada, não calcular com a do ano passado em silêncio.
- **Robô no Meu INSS:** não fazer (§3.1).
- **IA com ferramenta de escrita:** a conversa "do cliente à petição" só lê; gravar é sempre um
  clique humano fora do modelo (injeção de prompt por texto de entrevista — PLANO_IA_LLM_RAG §5.3).
- **Regulação:** OAB e CNJ (Res. 615/2025) seguem se assentando sobre IA na advocacia. Reler antes
  de ligar a fase 6 em produção.

---

## Fontes

- [Tramitação Inteligente — página inicial](https://planilha.tramitacaointeligente.com.br/) e
  [planos](https://planilha.tramitacaointeligente.com.br/planos) (lidos em 03/11/2026)
- [Preços da API Claude](https://platform.claude.com/docs/en/about-claude/pricing)
- [DataJud — CNJ](https://www.cnj.jus.br/sistemas/datajud/sobre/) (metadados processuais, sem
  inteiro teor)
- [Comunicações processuais / DJEN — CNJ](https://www.cnj.jus.br/programas-e-acoes/processo-judicial-eletronico-pje/comunicacoes-processuais/)
- [Escavador — preços](https://www.escavador.com/precos) e
  [Judit — APIs de dados jurídicos](https://judit.io/blog/apis-dados-juridicos-integracoes/api-consulta-processual-monitoramento-processual-fornecedores-brasil/)
- Emenda Constitucional 103/2019 (arts. 15 a 21 e 26) e LC 142/2013 — fonte das regras do §3.2

> Preços de modelo e de fornecedor mudam. Confira antes de fechar preço de plano.
