import { test as base, request as playwrightRequest, type APIRequestContext } from "@playwright/test";
import { env } from "./env.js";
import { dadosDe } from "./envelope.js";

/**
 * Fixtures da suíte.
 *
 * `api`        — cliente autenticado no tenant principal. É o que quase todo teste usa.
 * `anonimo`    — sem Authorization, para afirmar 401.
 * `outroTenant`— autenticado no segundo escritório, para afirmar isolamento.
 * `clienteId`  — cria um cliente, entrega o id e o exclui no fim.
 *
 * O login acontece uma vez por worker (escopo `worker`), não por teste: são
 * dezenas de casos, e repetir o POST /auth/login em cada um só somaria latência
 * sem afirmar nada — o login em si tem os seus próprios testes em auth.spec.ts.
 */

type Sessao = { token: string; tenantId: string; usuarioId?: string };

async function autenticar(baseURL: string, tenant: string, login: string, password: string): Promise<Sessao> {
  const anon = await playwrightRequest.newContext({ baseURL });
  const resposta = await anon.post("/api/v1/auth/login", {
    headers: { "X-Tenant-Id": tenant, "Content-Type": "application/json" },
    data: { login, password },
  });
  const dados = await dadosDe<{ token: string; tenantId: string }>(resposta);
  await anon.dispose();
  return { token: dados.token, tenantId: dados.tenantId };
}

async function contextoAutenticado(tenant: string): Promise<APIRequestContext> {
  const sessao = await autenticar(env.baseURL, tenant, env.login, env.password);
  return playwrightRequest.newContext({
    baseURL: env.baseURL,
    extraHTTPHeaders: {
      Authorization: `Bearer ${sessao.token}`,
      "X-Tenant-Id": tenant,
    },
  });
}

type Fixtures = {
  anonimo: APIRequestContext;
  clienteId: string;
};

type WorkerFixtures = {
  api: APIRequestContext;
  outroTenant: APIRequestContext | null;
};

export const test = base.extend<Fixtures, WorkerFixtures>({
  api: [
    async ({}, use) => {
      const ctx = await contextoAutenticado(env.tenant);
      await use(ctx);
      await ctx.dispose();
    },
    { scope: "worker" },
  ],

  outroTenant: [
    async ({}, use) => {
      // Ausente é estado legítimo (nem toda instalação tem um segundo
      // escritório); os testes que dependem dele se pulam com aviso.
      if (!env.tenantSecundario || env.tenantSecundario === env.tenant) {
        await use(null);
        return;
      }
      let ctx: APIRequestContext | null = null;
      try {
        ctx = await contextoAutenticado(env.tenantSecundario);
      } catch {
        // Credencial do usuário de teste pode não existir no outro escritório.
        ctx = null;
      }
      await use(ctx);
      await ctx?.dispose();
    },
    { scope: "worker" },
  ],

  anonimo: async ({}, use) => {
    const ctx = await playwrightRequest.newContext({
      baseURL: env.baseURL,
      extraHTTPHeaders: { "X-Tenant-Id": env.tenant },
    });
    await use(ctx);
    await ctx.dispose();
  },

  /**
   * Cliente descartável para os testes de sub-recurso.
   *
   * A exclusão no fim é lógica (o backend não apaga mais a linha), então o
   * resíduo continua no banco marcado com `[api-test]` — de propósito: apagar
   * de verdade exigiria acesso ao banco, e o que a suíte pode garantir é que
   * ele sai das listagens.
   */
  clienteId: async ({ api }, use) => {
    const { novoCliente } = await import("./factories.js");
    const criado = await api.post("/api/v1/clients", { data: novoCliente() });
    const cliente = await dadosDe<{ clientId: string }>(criado, 201);
    await use(cliente.clientId);
    await api.delete(`/api/v1/clients/${cliente.clientId}`);
  },
});

export { expect } from "@playwright/test";
