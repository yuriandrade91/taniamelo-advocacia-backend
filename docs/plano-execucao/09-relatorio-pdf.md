# Passo 09 — Relatório profissional em PDF para o cliente

> **Repos:** backend e frontend · **Branch:** `feat/09-relatorio` nos dois · **Depende de:** 08 ·
> **Decisões:** T9, D11 · **Esforço:** 1–1,5 semana

## 0. Antes de começar

Leia `service/ClientRecordService.java`, `ClientRecordHtmlRenderer.java`, `ClientRecordPdf.java`,
`RichText.java`, `TenantLogo.java`/`LogoImage.java`, `storage/StorageKeys.java`, os testes
`ClientRecordHtmlRendererTest`, `ClientRecordPdfTest`, `ClientRecordIntegrationTest`, e o
controller `ClientRecordController`. **O relatório segue exatamente esse padrão** (HTML → 
`openhtmltopdf`), sem biblioteca nova.

## 1. Objetivo

A partir de uma simulação gravada, gerar um PDF com a marca do escritório que o cliente entende e
o advogado consegue defender. O PDF gerado é **guardado como foi entregue**.

## 2. Conteúdo do relatório (nesta ordem)

1. **Capa:** logo e nome do escritório (do tenant), nome do cliente, data do relatório, "Estudo
   de aposentadoria".
2. **Resumo para o cliente** (linguagem simples, sem jargão): a melhor opção hoje com valor
   estimado; e a melhor opção futura se houver ("se continuar contribuindo até dd/mm/aaaa…").
3. **Suas opções:** tabela de regras (nome amigável, situação, data, valor estimado).
4. **Linha do tempo** dos vínculos (tabela; gráfico só se der para fazer em HTML/CSS estático que
   o `openhtmltopdf` renderiza — sem JavaScript).
5. **O que precisamos de você:** achados com `clientMessage` e documento a trazer.
6. **Anexo técnico:** por regra, requisitos e memória de cálculo completa com o dispositivo legal;
   média, coeficiente, fator, competências descartadas.
7. **Rodapé de toda página:** "Estimativa baseada no CNIS emitido em dd/mm/aaaa e nas regras
   vigentes em dd/mm/aaaa. Não é garantia de concessão." + versão do motor e das tabelas + página
   N/M.

Textos fixos ficam num template; nenhum texto de cliente entra sem escape (use o que
`RichText`/renderer atual fazem).

## 3. Backend

- `service/BenefitReportHtmlRenderer` (puro na forma do `ClientRecordHtmlRenderer`) +
  `service/BenefitReportService` (orquestra, gera PDF, guarda).
- Migration tenant: em `benefit_simulations`, colunas `report_storage_key varchar`,
  `report_generated_at timestamptz`, `report_generated_by uuid`.
- Chave de storage pelo `StorageKeys` (começa pelo schema do tenant, como os outros arquivos).
- Rotas em `ClientBenefitSimulationController`:

| Verbo | Rota | Papel | Faz |
| --- | --- | --- | --- |
| POST | `/{simulationId}/report` | **ADVOGADO** | gera e guarda; se já existir, 409 `RELATORIO_JA_GERADO` (o entregue não se sobrescreve; nova versão = nova simulação) |
| GET | `/{simulationId}/report` | autenticado | baixa o PDF guardado (`Content-Disposition` com nome `estudo-aposentadoria-<nome>-<data>.pdf`, como a ficha faz) |

- **Amostra para aprovação (D11):** gere o PDF de um cliente **fictício** e salve em
  `Claude outputs/estudo-aposentadoria-amostra.pdf` (pasta ignorada pelo git, já usada para as
  amostras da ficha). Diga no relatório que a aprovação está pendente.

## 4. Frontend

Na simulação aberta (passo 08): botão **Gerar relatório** (ADVOGADO; `ConfirmDialog` avisando que
o relatório fica guardado como entregue) e **Baixar relatório** quando existir (`downloadClientFile`
+ `saveBlob`). Feedback pelo serviço. Testes de tela (com e sem relatório, STAFF não gera) e e2e
`tests/e2e/benefit-report.spec.ts` (gera, baixa, confere que veio PDF).

## 5. Testes back

- `BenefitReportHtmlRendererTest`: cada seção presente, escape de texto, rodapé com versões,
  cliente sem achados, sem regra elegível.
- `BenefitReportPdfTest`: PDF válido, texto extraível contém nome, valor e rodapé.
- `BenefitReportIntegrationTest`: gera, guarda, baixa idêntico, 409 na segunda geração, outro
  tenant não baixa.
- `SecuredEndpointsContractTest`, api-tests (`clients.benefit-simulations.spec.ts` estendido) +
  Postman.

## 6. Critérios de aceite

- [ ] PDF com as 7 partes, legível em A4, marca do escritório.
- [ ] Guardado e baixável idêntico; não sobrescreve.
- [ ] Amostra fictícia gerada para D11.
- [ ] Gates e Sonar verdes nos dois repos.
- [ ] **Merge só com D11 aprovada.**

## 7. Commits sugeridos

`feat(relatorio): HTML do estudo de aposentadoria`, `feat(relatorio): geração e guarda do PDF`,
`feat(relatorio): gerar e baixar pela ficha` (front), `test(e2e): relatório da simulação`.

## 8. Pare e pergunte se

O tenant não tiver logo (defina com a advogada o fallback: só o nome); o `openhtmltopdf` não
renderizar algo do layout — simplifique, não troque de biblioteca.
