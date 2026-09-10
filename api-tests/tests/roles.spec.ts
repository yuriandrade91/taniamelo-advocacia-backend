import { test, expect } from "../src/fixtures.js";
import { request as playwrightRequest, type APIRequestContext } from "@playwright/test";
import { dadosDe, errosDe } from "../src/envelope.js";
import { env } from "../src/env.js";

/**
 * Autorização por papel: STAFF opera, só ADMIN/LAWYER destrói.
 *
 * Antes, autenticado era autorizado — qualquer token válido excluía cliente,
 * excluía compromisso e listava quem trabalha no escritório. Estes testes
 * afirmam os dois lados: que STAFF é recusado onde deve ser, e que o dia a dia
 * dele continua funcionando (se isso quebrar, a regra está errada, não o teste).
 *
 * Precisam de um usuário STAFF no escritório de teste. Sem `API_LOGIN_STAFF` e
 * `API_PASSWORD_STAFF` no ambiente, o arquivo inteiro é pulado com aviso — o
 * que é melhor que passar verde sem ter testado nada.
 */

const UUID_INEXISTENTE = "11111111-2222-3333-4444-555555555555";

let staff: APIRequestContext | null = null;

test.beforeAll(async () => {
  if (!env.loginStaff || !env.passwordStaff) return;

  const anon = await playwrightRequest.newContext({ baseURL: env.baseURL });
  const resposta = await anon.post("/api/v1/auth/login", {
    headers: { "X-Tenant-Id": env.tenant, "Content-Type": "application/json" },
    data: { login: env.loginStaff, password: env.passwordStaff },
  });
  const dados = await dadosDe<{ token: string; role: string }>(resposta);
  await anon.dispose();

  // Se a credencial for de ADMIN, todo teste de 403 falharia por um motivo que
  // não é o testado. Melhor dizer isso alto do que investigar a API à toa.
  expect(dados.role, "API_LOGIN_STAFF não é um usuário STAFF").toBe("STAFF");

  staff = await playwrightRequest.newContext({
    baseURL: env.baseURL,
    extraHTTPHeaders: {
      Authorization: `Bearer ${dados.token}`,
      "X-Tenant-Id": env.tenant,
    },
  });
});

test.afterAll(async () => {
  await staff?.dispose();
});

test.beforeEach(() => {
  test.skip(!env.loginStaff || !env.passwordStaff, "defina API_LOGIN_STAFF e API_PASSWORD_STAFF");
});

test.describe("STAFF não destrói", () => {
  const proibidas: Array<[string, string, string]> = [
    ["DELETE", `/api/v1/clients/${UUID_INEXISTENTE}`, "excluir cliente"],
    ["DELETE", `/api/v1/appointments/${UUID_INEXISTENTE}`, "excluir compromisso"],
    ["PATCH", `/api/v1/clients/${UUID_INEXISTENTE}/restore`, "restaurar cliente"],
    ["PATCH", `/api/v1/appointments/${UUID_INEXISTENTE}/restore`, "restaurar compromisso"],
    ["GET", `/api/v1/clients/${UUID_INEXISTENTE}/inss-password`, "ler a senha do INSS"],
    ["GET", "/api/v1/users", "listar os usuários do escritório"],
  ];

  for (const [metodo, rota, oque] of proibidas) {
    test(`403 ao ${oque}`, async () => {
      const resposta = await staff!.fetch(rota, { method: metodo });

      // 403 e não 404: o id inexistente é de propósito. Se a autorização
      // falhasse e a requisição chegasse ao service, viria 404 — e um teste que
      // aceitasse os dois não provaria nada.
      const erros = await errosDe(resposta, 403);
      expect(erros.length).toBeGreaterThan(0);
    });
  }

  test("o 403 vem no envelope padrão, não em HTML do Spring", async () => {
    const resposta = await staff!.delete(`/api/v1/clients/${UUID_INEXISTENTE}`);
    const texto = await resposta.text();
    expect(texto.trim().startsWith("<"), `403 veio como HTML: ${texto.slice(0, 120)}`).toBe(false);
  });
});

test.describe("STAFF continua operando", () => {
  test("lista clientes e agenda", async () => {
    expect((await staff!.get("/api/v1/clients")).status()).toBe(200);
    expect((await staff!.get("/api/v1/appointments")).status()).toBe(200);
  });

  test("cadastra e edita cliente", async ({ api }) => {
    const { novoCliente } = await import("../src/factories.js");
    const criado = await dadosDe<{ id: string }>(
      await staff!.post("/api/v1/clients", { data: novoCliente() }),
      201,
    );

    const ficha = await dadosDe<Record<string, unknown>>(await staff!.get(`/api/v1/clients/${criado.id}`));
    await dadosDe(
      await staff!.put(`/api/v1/clients/${criado.id}`, {
        data: { ...ficha, fullName: "[api-test] Editado por STAFF" },
      }),
    );

    // Quem cria não pode excluir — é o corte entre operar e destruir aparecendo
    // no meio do fluxo normal. A limpeza sai pelo usuário com papel de advogado,
    // senão o teste deixaria resíduo justamente por estar certo.
    expect((await staff!.delete(`/api/v1/clients/${criado.id}`)).status()).toBe(403);
    await api.delete(`/api/v1/clients/${criado.id}`);
  });
});
