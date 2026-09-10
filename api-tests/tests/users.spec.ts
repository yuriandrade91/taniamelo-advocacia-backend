import { test, expect } from "../src/fixtures.js";
import { dadosDe } from "../src/envelope.js";

/**
 * GET /users — existe para traduzir em nome os UUIDs de autoria que os demais
 * recursos devolvem. Os testes abaixo são sobre isso: o que ela precisa
 * entregar, e o que ela não pode vazar.
 */

type Usuario = { id: string; fullName: string; username: string; role: string; active: boolean };

test("lista ordenada por nome, no envelope padrão", async ({ api }) => {
  const usuarios = await dadosDe<Usuario[]>(await api.get("/api/v1/users"));

  expect(usuarios.length).toBeGreaterThan(0);
  const nomes = usuarios.map((u) => u.fullName);
  expect(nomes, "a lista deveria vir ordenada por nome").toEqual(
    [...nomes].sort((a, b) => a.localeCompare(b, "pt-BR")),
  );
  for (const u of usuarios) {
    expect(u.id).toBeTruthy();
    expect(["ADMIN", "LAWYER", "STAFF"]).toContain(u.role);
  }
});

test("não expõe e-mail nem hash de senha", async ({ api }) => {
  const usuarios = await dadosDe<Record<string, unknown>[]>(await api.get("/api/v1/users"));

  for (const u of usuarios) {
    // O propósito da rota é traduzir id em nome, não ser diretório de contatos.
    expect(Object.keys(u)).not.toContain("email");
    expect(Object.keys(u)).not.toContain("passwordHash");
    expect(Object.keys(u)).not.toContain("password");
  }
});

test("por padrão só ativos; includeInactive traz quem já saiu", async ({ api }) => {
  const ativos = await dadosDe<Usuario[]>(await api.get("/api/v1/users"));
  expect(ativos.every((u) => u.active), "usuário desativado apareceu no padrão").toBe(true);

  const todos = await dadosDe<Usuario[]>(await api.get("/api/v1/users?includeInactive=true"));
  // Nunca menos: incluir desativados só pode aumentar ou manter a lista.
  expect(todos.length).toBeGreaterThanOrEqual(ativos.length);
});

test("os ids de autoria dos outros recursos resolvem nesta lista", async ({ api, clienteId }) => {
  const usuarios = await dadosDe<Usuario[]>(await api.get("/api/v1/users?includeInactive=true"));
  const conhecidos = new Set(usuarios.map((u) => u.id));

  const cliente = await dadosDe<{ createdBy?: string }>(await api.get(`/api/v1/clients/${clienteId}`));

  // É a razão de a rota existir: sem ela, createdBy é um UUID que nenhuma tela
  // consegue transformar em "criado por Fulano".
  if (cliente.createdBy) {
    expect(conhecidos, "createdBy não resolve em GET /users").toContain(cliente.createdBy);
  }
});
