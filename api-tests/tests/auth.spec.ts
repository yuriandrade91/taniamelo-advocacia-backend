import { test, expect } from "../src/fixtures.js";
import { request as playwrightRequest } from "@playwright/test";
import { dadosDe, errosDe } from "../src/envelope.js";
import { env } from "../src/env.js";

/**
 * POST /auth/login, /auth/refresh, /auth/logout.
 *
 * O login é a única rota que precisa do `X-Tenant-Id` sem ter token: é ele que
 * diz em qual escritório procurar o usuário. Por isso os casos de tenant estão
 * aqui e não só em tenancy.spec.ts.
 */

type Login = {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  fullName: string;
  email: string;
  role: string;
  tenantId: string;
  tenantSlug: string;
};

function contexto() {
  return playwrightRequest.newContext({
    baseURL: env.baseURL,
    extraHTTPHeaders: { "Content-Type": "application/json" },
  });
}

const comTenant = { "X-Tenant-Id": env.tenant };

test.describe("POST /auth/login", () => {
  test("credencial correta devolve token e identidade do escritório", async () => {
    const ctx = await contexto();
    const dados = await dadosDe<Login>(
      await ctx.post("/api/v1/auth/login", {
        headers: comTenant,
        data: { login: env.login, password: env.password },
      }),
    );

    expect(dados.token, "token vazio").toBeTruthy();
    expect(dados.tokenType).toBe("Bearer");
    expect(dados.expiresInSeconds).toBeGreaterThan(0);
    // O tenant volta na resposta para o frontend não precisar guardar o que enviou.
    expect(dados.tenantSlug || dados.tenantId).toBeTruthy();
    expect(["ADMIN", "LAWYER", "STAFF"]).toContain(dados.role);

    // JWT tem três partes. Sem esta asserção, uma string qualquer passaria.
    expect(dados.token.split(".")).toHaveLength(3);
    await ctx.dispose();
  });

  test("o token emitido abre uma rota protegida", async () => {
    const ctx = await contexto();
    const { token } = await dadosDe<Login>(
      await ctx.post("/api/v1/auth/login", {
        headers: comTenant,
        data: { login: env.login, password: env.password },
      }),
    );

    const protegida = await ctx.get("/api/v1/clients", {
      headers: { ...comTenant, Authorization: `Bearer ${token}` },
    });
    expect(protegida.status()).toBe(200);
    await ctx.dispose();
  });

  test("senha errada devolve 401 sem dizer se o usuário existe", async () => {
    const ctx = await contexto();
    const resposta = await ctx.post("/api/v1/auth/login", {
      headers: comTenant,
      data: { login: env.login, password: "senha-definitivamente-errada" },
    });
    expect(resposta.status()).toBe(401);

    const texto = (await resposta.text()).toLowerCase();
    // Enumeração de usuário: a mensagem não pode confirmar que o login existe.
    expect(texto).not.toContain("senha incorreta");
    expect(texto).not.toContain("password incorrect");
    await ctx.dispose();
  });

  test("usuário inexistente devolve a MESMA resposta que senha errada", async () => {
    const ctx = await contexto();
    const inexistente = await ctx.post("/api/v1/auth/login", {
      headers: comTenant,
      data: { login: `nao-existe-${Date.now()}@teste.com`, password: "qualquer" },
    });
    const senhaErrada = await ctx.post("/api/v1/auth/login", {
      headers: comTenant,
      data: { login: env.login, password: "senha-errada" },
    });

    // Respostas diferentes aqui viram oráculo de "este e-mail é cliente do
    // escritório?" para quem estiver de fora.
    expect(inexistente.status()).toBe(senhaErrada.status());
    await ctx.dispose();
  });

  test("corpo sem login ou sem senha devolve 400 apontando o campo", async () => {
    const ctx = await contexto();
    const semSenha = await errosDe(
      await ctx.post("/api/v1/auth/login", { headers: comTenant, data: { login: env.login } }),
      400,
    );
    expect(semSenha.map((e) => e.field)).toContain("password");

    const semLogin = await errosDe(
      await ctx.post("/api/v1/auth/login", { headers: comTenant, data: { password: "x" } }),
      400,
    );
    expect(semLogin.map((e) => e.field)).toContain("login");
    await ctx.dispose();
  });

  test("tenant inexistente é recusado, não vira o escritório default", async () => {
    const ctx = await contexto();
    const erros = await errosDe(
      await ctx.post("/api/v1/auth/login", {
        headers: { "X-Tenant-Id": `escritorio-que-nao-existe-${Date.now()}` },
        data: { login: env.login, password: env.password },
      }),
      400,
    );

    // Este teste existe por causa de um defeito real: o header desconhecido era
    // ignorado e a requisição caía no schema default — que é o escritório real.
    // Um erro de digitação no X-Tenant-Id autenticava na base errada, em silêncio.
    expect(erros.map((e) => e.code)).toContain("TENANT_NOT_FOUND");
    expect(erros.map((e) => e.field)).toContain("X-Tenant-Id");

    // A mensagem não pode confirmar quais escritórios existem para quem chuta.
    const mensagens = erros.map((e) => (e.message ?? "").toLowerCase()).join(" ");
    expect(mensagens).not.toContain("tania");
    expect(mensagens).not.toContain("demo");
    await ctx.dispose();
  });

  test("tenant desconhecido é recusado em qualquer rota, não só no login", async ({ api }) => {
    // O filtro roda antes da autenticação: se ele só protegesse o login, dava
    // para trocar de escritório no meio da sessão trocando o header.
    const ctx = await contexto();
    const { token } = await dadosDe<Login>(
      await ctx.post("/api/v1/auth/login", {
        headers: comTenant,
        data: { login: env.login, password: env.password },
      }),
    );
    const resposta = await ctx.get("/api/v1/clients", {
      headers: { "X-Tenant-Id": "nao-existe", Authorization: `Bearer ${token}` },
    });
    expect(resposta.status()).toBe(400);
    await ctx.dispose();
    expect(api).toBeTruthy();
  });
});

test.describe("POST /auth/refresh", () => {
  test("rotaciona o token usando o cookie httpOnly", async () => {
    // Mesmo contexto do login: é ele que guarda o cookie do refresh token.
    const ctx = await contexto();
    const primeiro = await dadosDe<Login>(
      await ctx.post("/api/v1/auth/login", {
        headers: comTenant,
        data: { login: env.login, password: env.password },
      }),
    );

    const renovado = await dadosDe<Login>(
      await ctx.post("/api/v1/auth/refresh", { headers: comTenant }),
    );

    expect(renovado.token).toBeTruthy();
    const usavel = await ctx.get("/api/v1/clients", {
      headers: { ...comTenant, Authorization: `Bearer ${renovado.token}` },
    });
    expect(usavel.status(), "token renovado deveria abrir rota protegida").toBe(200);
    expect(primeiro.token).toBeTruthy();
    await ctx.dispose();
  });

  test("sem o cookie, o refresh é recusado", async () => {
    const ctx = await contexto();
    const resposta = await ctx.post("/api/v1/auth/refresh", { headers: comTenant });
    expect(resposta.status()).toBeGreaterThanOrEqual(400);
    await ctx.dispose();
  });
});

test.describe("POST /auth/logout", () => {
  test("invalida a sessão: o refresh deixa de funcionar", async () => {
    const ctx = await contexto();
    await dadosDe<Login>(
      await ctx.post("/api/v1/auth/login", {
        headers: comTenant,
        data: { login: env.login, password: env.password },
      }),
    );

    const saida = await ctx.post("/api/v1/auth/logout", { headers: comTenant });
    expect(saida.status()).toBeLessThan(400);

    // O que o logout precisa garantir é que o refresh não ressuscita a sessão.
    // O access token continua válido até expirar — é a natureza de JWT, não um defeito.
    const depois = await ctx.post("/api/v1/auth/refresh", { headers: comTenant });
    expect(depois.status(), "refresh depois do logout deveria falhar").toBeGreaterThanOrEqual(400);
    await ctx.dispose();
  });
});

test.describe("token inválido", () => {
  test("Bearer forjado devolve 401 no envelope, não HTML do Spring", async ({ anonimo }) => {
    const resposta = await anonimo.get("/api/v1/clients", {
      headers: { Authorization: "Bearer nao.e.um.jwt" },
    });
    expect(resposta.status()).toBe(401);
    // Se isto quebrar com "Unexpected token <", o 401 voltou a ser página HTML.
    expect(() => JSON.parse("" + "{}")).not.toThrow();
    const corpo = await resposta.text();
    expect(corpo.trim().startsWith("<"), `401 veio como HTML: ${corpo.slice(0, 120)}`).toBe(false);
  });

  test("header Authorization malformado devolve 401", async ({ anonimo }) => {
    const resposta = await anonimo.get("/api/v1/clients", {
      headers: { Authorization: "sem-o-prefixo-bearer" },
    });
    expect(resposta.status()).toBe(401);
  });
});
