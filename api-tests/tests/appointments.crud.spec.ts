import { test, expect } from "../src/fixtures.js";
import { dadosDe, errosDe, listaDe } from "../src/envelope.js";
import { novoCompromisso, janelaLivre, futuroIso, passadoIso } from "../src/factories.js";

type Compromisso = {
  id: string;
  title: string;
  type: string;
  startAt: string;
  endAt: string;
  modality: string;
  status: string;
  clientId: string | null;
  clientName: string | null;
  location: string | null;
  meetingUrl: string | null;
  description: string | null;
  pastDateAuthorizedBy: string | null;
  createdBy: string | null;
};

const UUID_INEXISTENTE = "11111111-2222-3333-4444-555555555555";

test.describe("POST /appointments", () => {
  const criados: string[] = [];
  test.afterAll(async ({ api }) => {
    for (const id of criados) await api.delete(`/api/v1/appointments/${id}`);
  });

  test("cria com status Agendado e devolve os labels em PT-BR", async ({ api }) => {
    const payload = novoCompromisso();
    const criado = await dadosDe<Compromisso>(
      await api.post("/api/v1/appointments", { data: payload }),
      201,
    );
    criados.push(criado.id);

    expect(criado.status).toBe("Agendado");
    expect(criado.type).toBe("Entrevista");
    expect(criado.modality).toBe("Presencial");
    expect(criado.title).toBe(payload.title);
    expect(criado.createdBy, "sem autor não dá para auditar a agenda").toBeTruthy();
  });

  test("modalidade ausente cai em Presencial", async ({ api }) => {
    const payload = novoCompromisso() as Record<string, unknown>;
    delete payload.modality;
    const criado = await dadosDe<Compromisso>(await api.post("/api/v1/appointments", { data: payload }), 201);
    criados.push(criado.id);
    expect(criado.modality).toBe("Presencial");
  });

  test("aceita o nome da constante no lugar do label", async ({ api }) => {
    const criado = await dadosDe<Compromisso>(
      await api.post("/api/v1/appointments", {
        data: novoCompromisso({ type: "PERICIA", modality: "ONLINE", meetingUrl: "https://meet.google.com/abc-defg-hij" }),
      }),
      201,
    );
    criados.push(criado.id);
    expect(criado.type).toBe("Perícia");
    expect(criado.modality).toBe("Online");
  });

  test("término anterior ao início devolve 400", async ({ api }) => {
    const erros = await errosDe(
      await api.post("/api/v1/appointments", {
        data: novoCompromisso({ startAt: futuroIso(40, 15), endAt: futuroIso(40, 14) }),
      }),
      400,
    );
    expect(erros.length).toBeGreaterThan(0);
  });

  test("término IGUAL ao início devolve 400", async ({ api }) => {
    // Compromisso de duração zero não é agendamento; se passar, ele nunca
    // conflita com nada e some das contas de ocupação.
    const mesmo = futuroIso(41, 14);
    await errosDe(
      await api.post("/api/v1/appointments", { data: novoCompromisso({ startAt: mesmo, endAt: mesmo }) }),
      400,
    );
  });

  for (const campo of ["title", "type", "startAt", "endAt"]) {
    test(`sem ${campo} devolve 400 apontando o campo`, async ({ api }) => {
      const payload = novoCompromisso() as Record<string, unknown>;
      delete payload[campo];
      const erros = await errosDe(await api.post("/api/v1/appointments", { data: payload }), 400);
      expect(erros.map((e) => e.field)).toContain(campo);
    });
  }

  test("título em branco devolve 400", async ({ api }) => {
    const erros = await errosDe(
      await api.post("/api/v1/appointments", { data: novoCompromisso({ title: "   " }) }),
      400,
    );
    expect(erros.map((e) => e.field)).toContain("title");
  });

  test("tipo fora do enum devolve 400", async ({ api }) => {
    await errosDe(
      await api.post("/api/v1/appointments", { data: novoCompromisso({ type: "Chá da tarde" }) }),
      400,
    );
  });

  test("data no passado exige ciência explícita", async ({ api }) => {
    const janela = { startAt: passadoIso(5, 10), endAt: passadoIso(5, 11) };

    const semCiencia = await api.post("/api/v1/appointments", { data: novoCompromisso(janela) });
    expect(semCiencia.status(), "compromisso retroativo passou sem confirmação").toBe(422);
    const corpo = await semCiencia.json();
    expect(corpo.errors.map((e: { code: string }) => e.code)).toContain("PAST_DATE_NOT_CONFIRMED");

    // Não é proibido — audiência que já aconteceu precisa ser registrada. Só exige
    // que alguém assuma a data, e isso fica na auditoria.
    const comCiencia = await dadosDe<Compromisso>(
      await api.post("/api/v1/appointments", {
        data: novoCompromisso({ ...janela, pastDateAcknowledged: true }),
      }),
      201,
    );
    criados.push(comCiencia.id);
    expect(comCiencia.pastDateAuthorizedBy, "ciência sem registrar quem confirmou").toBeTruthy();
  });

  test("com clientId, o nome vem do cliente e o nome livre é descartado", async ({ api, clienteId }) => {
    const cliente = await dadosDe<{ fullName: string }>(await api.get(`/api/v1/clients/${clienteId}`));
    const criado = await dadosDe<Compromisso>(
      await api.post("/api/v1/appointments", {
        data: novoCompromisso({ clientId: clienteId, clientName: "Nome Digitado À Mão" }),
      }),
      201,
    );
    criados.push(criado.id);

    expect(criado.clientId).toBe(clienteId);
    expect(criado.clientName).toBe(cliente.fullName);
  });

  test("sem clientId, aceita nome livre (pessoa ainda não cadastrada)", async ({ api }) => {
    const criado = await dadosDe<Compromisso>(
      await api.post("/api/v1/appointments", {
        data: novoCompromisso({ clientName: "  Maria Não Cadastrada  " }),
      }),
      201,
    );
    criados.push(criado.id);
    expect(criado.clientId).toBeNull();
    expect(criado.clientName?.trim()).toBe("Maria Não Cadastrada");
  });

  test("clientId inexistente devolve 404", async ({ api }) => {
    const resposta = await api.post("/api/v1/appointments", {
      data: novoCompromisso({ clientId: UUID_INEXISTENTE }),
    });
    expect(resposta.status()).toBe(404);
  });

  test("o nome do cliente sobrevive à exclusão do cliente", async ({ api, clienteId }) => {
    // Regressão: antes, client_name era zerado quando havia vínculo. Com o
    // cliente excluído (client_id vira null), o compromisso ficava sem cliente
    // E sem nome — a audiência seguia na agenda sem dizer de quem era.
    const cliente = await dadosDe<{ fullName: string }>(await api.get(`/api/v1/clients/${clienteId}`));
    const criado = await dadosDe<Compromisso>(
      await api.post("/api/v1/appointments", { data: novoCompromisso({ clientId: clienteId }) }),
      201,
    );
    criados.push(criado.id);

    await api.delete(`/api/v1/clients/${clienteId}`);
    const depois = await dadosDe<Compromisso>(await api.get(`/api/v1/appointments/${criado.id}`));
    expect(depois.clientName, "compromisso perdeu o nome do cliente").toBe(cliente.fullName);

    await api.patch(`/api/v1/clients/${clienteId}/restore`);
  });

  test("renomear o cliente reflete na agenda", async ({ api, clienteId }) => {
    const criado = await dadosDe<Compromisso>(
      await api.post("/api/v1/appointments", { data: novoCompromisso({ clientId: clienteId }) }),
      201,
    );
    criados.push(criado.id);

    const atual = await dadosDe<Record<string, unknown>>(await api.get(`/api/v1/clients/${clienteId}`));
    await dadosDe(
      await api.put(`/api/v1/clients/${clienteId}`, { data: { ...atual, fullName: "[api-test] Nome Renomeado" } }),
    );

    const depois = await dadosDe<Compromisso>(await api.get(`/api/v1/appointments/${criado.id}`));
    // O retrato gravado não pode ganhar do nome atual enquanto o vínculo resolve.
    expect(depois.clientName).toBe("[api-test] Nome Renomeado");
  });
});

test.describe("GET /appointments/{id}", () => {
  test("id inexistente devolve 404", async ({ api }) => {
    expect((await api.get(`/api/v1/appointments/${UUID_INEXISTENTE}`)).status()).toBe(404);
  });

  test("id malformado não vira erro de servidor", async ({ api }) => {
    expect((await api.get("/api/v1/appointments/nao-e-uuid")).status()).toBeLessThan(500);
  });
});

test.describe("PUT /appointments/{id}", () => {
  test("exige justificativa e registra a edição na trilha", async ({ api }) => {
    const criado = await dadosDe<Compromisso>(
      await api.post("/api/v1/appointments", { data: novoCompromisso() }),
      201,
    );
    try {
      const semJustificativa = await api.put(`/api/v1/appointments/${criado.id}`, {
        data: novoCompromisso({ title: "[api-test] Editado" }),
      });
      expect(semJustificativa.status()).toBe(400);

      const janela = janelaLivre();
      const editado = await dadosDe<Compromisso>(
        await api.put(`/api/v1/appointments/${criado.id}`, {
          data: novoCompromisso({ ...janela, title: "[api-test] Editado", justification: "cliente pediu para remarcar" }),
        }),
      );
      expect(editado.title).toBe("[api-test] Editado");

      const trilha = await dadosDe<Array<{ action: string; justification: string }>>(
        await api.get(`/api/v1/appointments/${criado.id}/history`),
      );
      const edicao = trilha.find((h) => h.action === "EDITED");
      expect(edicao, "edição não deixou rastro").toBeTruthy();
      expect(edicao!.justification).toBe("cliente pediu para remarcar");
    } finally {
      await api.delete(`/api/v1/appointments/${criado.id}`);
    }
  });

  test("id inexistente devolve 404", async ({ api }) => {
    const resposta = await api.put(`/api/v1/appointments/${UUID_INEXISTENTE}`, {
      data: novoCompromisso({ justification: "x" }),
    });
    expect(resposta.status()).toBe(404);
  });
});

test.describe("GET /appointments (listagem)", () => {
  test("vem paginada e ordenada por início crescente", async ({ api }) => {
    const { itens, pagina } = await listaDe<Compromisso>(
      await api.get("/api/v1/appointments?pageNumber=1&pageSize=20"),
    );
    expect(pagina.pageNumber).toBe(1);

    const inicios = itens.map((c) => new Date(c.startAt).getTime());
    // Agenda fora de ordem cronológica é agenda inútil.
    expect(inicios, "listagem não veio ordenada por início").toEqual([...inicios].sort((a, b) => a - b));
  });
});
