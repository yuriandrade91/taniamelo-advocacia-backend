import { test, expect } from "../src/fixtures.js";
import { dadosDe } from "../src/envelope.js";
import { novoCliente, novoCompromisso } from "../src/factories.js";

/**
 * Multi-tenancy e rotas protegidas.
 *
 * O teste de vazamento entre escritórios é o mais importante da suíte inteira:
 * é o único defeito desta lista que não tem conserto depois de acontecer.
 */

const ROTAS_PROTEGIDAS: Array<[string, string]> = [
  ["GET", "/api/v1/clients"],
  ["POST", "/api/v1/clients"],
  ["GET", "/api/v1/clients/00000000-0000-0000-0000-000000000000"],
  ["GET", "/api/v1/clients/00000000-0000-0000-0000-000000000000/situation-history"],
  ["GET", "/api/v1/clients/00000000-0000-0000-0000-000000000000/addresses"],
  ["GET", "/api/v1/clients/00000000-0000-0000-0000-000000000000/interviews"],
  ["GET", "/api/v1/clients/00000000-0000-0000-0000-000000000000/payments"],
  ["GET", "/api/v1/clients/00000000-0000-0000-0000-000000000000/personal-data"],
  ["GET", "/api/v1/clients/00000000-0000-0000-0000-000000000000/professional-data"],
  ["GET", "/api/v1/clients/00000000-0000-0000-0000-000000000000/files/documents"],
  ["GET", "/api/v1/appointments"],
  ["POST", "/api/v1/appointments"],
  ["GET", "/api/v1/appointments/summary?year=2026"],
  ["GET", "/api/v1/appointments/conflicts?startAt=2030-01-01T10:00:00Z&endAt=2030-01-01T11:00:00Z"],
  ["GET", "/api/v1/appointments/00000000-0000-0000-0000-000000000000"],
  ["GET", "/api/v1/appointments/00000000-0000-0000-0000-000000000000/history"],
  ["GET", "/api/v1/users"],
  ["GET", "/api/v1/tenants/current"],
];

test.describe("sem token, toda rota protegida devolve 401", () => {
  // Varredura parametrizada: barata, e pega rota nova que alguém esqueceu de
  // proteger — que é o tipo de defeito que ninguém escreve teste específico para.
  for (const [metodo, rota] of ROTAS_PROTEGIDAS) {
    test(`${metodo} ${rota.split("?")[0]}`, async ({ anonimo }) => {
      const resposta = await anonimo.fetch(rota, { method: metodo, data: {} });
      expect(resposta.status(), `${metodo} ${rota} ficou sem proteção`).toBe(401);
    });
  }
});

test.describe("rotas públicas continuam públicas", () => {
  test("GET /tenants/resolve não exige token nem tenant", async ({ anonimo }) => {
    const resposta = await anonimo.get("/api/v1/tenants/resolve?slug=demo");
    expect(resposta.status()).toBe(200);
  });

  test("slug inexistente devolve 404", async ({ anonimo }) => {
    const resposta = await anonimo.get(`/api/v1/tenants/resolve?slug=nao-existe-${Date.now()}`);
    expect(resposta.status()).toBe(404);
  });
});

test.describe("isolamento entre escritórios", () => {
  test("cliente de um escritório é 404 no outro", async ({ api, outroTenant }) => {
    test.skip(
      !outroTenant,
      "API_TENANT_SECUNDARIO não configurado (ou sem usuário lá): sem segundo escritório não dá para provar isolamento",
    );

    const criado = await dadosDe<{ id: string }>(
      await api.post("/api/v1/clients", { data: novoCliente() }),
      201,
    );

    try {
      const vazamento = await outroTenant!.get(`/api/v1/clients/${criado.id}`);
      expect(
        vazamento.status(),
        "VAZAMENTO ENTRE ESCRITÓRIOS: um tenant enxergou cliente do outro",
      ).toBe(404);

      // Sub-recurso também: o filtro pode existir na rota principal e faltar aqui.
      const enderecos = await outroTenant!.get(`/api/v1/clients/${criado.id}/addresses`);
      expect(enderecos.status()).toBe(404);
    } finally {
      await api.delete(`/api/v1/clients/${criado.id}`);
    }
  });

  test("compromisso de um escritório é 404 no outro", async ({ api, outroTenant }) => {
    test.skip(!outroTenant, "API_TENANT_SECUNDARIO não configurado");

    const criado = await dadosDe<{ id: string }>(
      await api.post("/api/v1/appointments", { data: novoCompromisso() }),
      201,
    );

    try {
      expect((await outroTenant!.get(`/api/v1/appointments/${criado.id}`)).status()).toBe(404);
      expect((await outroTenant!.get(`/api/v1/appointments/${criado.id}/history`)).status()).toBe(404);
    } finally {
      await api.delete(`/api/v1/appointments/${criado.id}`);
    }
  });

  test("a listagem de um escritório não traz registro do outro", async ({ api, outroTenant }) => {
    test.skip(!outroTenant, "API_TENANT_SECUNDARIO não configurado");

    const criado = await dadosDe<{ id: string; fullName: string }>(
      await api.post("/api/v1/clients", { data: novoCliente() }),
      201,
    );

    try {
      const busca = await outroTenant!.get(
        `/api/v1/clients?searchTerm=${encodeURIComponent(criado.fullName)}`,
      );
      const corpo = await busca.json();
      expect(
        (corpo.data ?? []).map((c: { id: string }) => c.id),
        "VAZAMENTO: cliente apareceu na busca do outro escritório",
      ).not.toContain(criado.id);
    } finally {
      await api.delete(`/api/v1/clients/${criado.id}`);
    }
  });
});

test.describe("GET /tenants/current", () => {
  test("devolve o escritório da sessão", async ({ api }) => {
    const dados = await dadosDe<{ razaoSocial?: string; slug?: string }>(
      await api.get("/api/v1/tenants/current"),
    );
    expect(dados).toBeTruthy();
    expect(dados.razaoSocial || dados.slug).toBeTruthy();
  });
});
