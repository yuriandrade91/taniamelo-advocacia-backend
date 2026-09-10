import { defineConfig } from "@playwright/test";

/**
 * Testes de contrato da API. Sem navegador: tudo aqui usa o `request` do
 * Playwright, que é um cliente HTTP com asserções — o mesmo runner, relatório e
 * `trace` dos testes de interface, sem o custo de subir um Chromium.
 *
 * Por que Playwright e não Rest-assured: o Rest-assured continua sendo o lugar
 * certo para teste que roda DENTRO do build Maven, com o contexto do Spring
 * (Camada 3 do plano de testes). Esta suíte é outra coisa — ela roda contra uma
 * instância já publicada, de fora, sem saber nada do código. É o que responde
 * "o que subiu na AWS funciona?", que nenhum teste de dentro do build responde.
 */
const baseURL = process.env.API_BASE_URL ?? "http://98.82.73.175:8080";

export default defineConfig({
  testDir: "./tests",

  // Um worker. Os testes gravam num banco compartilhado: em paralelo, dois
  // deles marcariam o mesmo horário na agenda e um falharia por "conflito de
  // horário" — que é justamente uma regra sob teste.
  fullyParallel: false,
  workers: 1,

  forbidOnly: !!process.env.CI,
  // Uma tentativa extra no CI: a instância é EC2 pequena e um timeout de rede
  // não é defeito da API. Zero localmente, para a flakiness aparecer.
  retries: process.env.CI ? 1 : 0,
  timeout: 30_000,
  expect: { timeout: 10_000 },

  reporter: process.env.CI
    ? [["github"], ["html", { open: "never" }], ["json", { outputFile: "test-results/resultado.json" }]]
    : [["list"]],

  use: {
    baseURL,
    // Sem Content-Type fixo: o Playwright já define application/json quando `data`
    // é objeto, e um header fixo aqui quebraria os uploads multipart.
    // Guarda o rastro só do que falhou. Trace de suíte verde é lixo em disco.
    trace: "retain-on-failure",
    // A API devolve o próprio envelope de erro em 4xx; deixar o Playwright
    // estourar sozinho esconderia a mensagem que queremos afirmar.
    ignoreHTTPSErrors: true,
  },
});
