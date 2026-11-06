import { test, expect } from "../src/fixtures.js";
import { dadosDe, listaDe, errosDe } from "../src/envelope.js";
import { novoCompromisso, futuroIso } from "../src/factories.js";

/**
 * GET /appointments (filtros e paginação) e GET /appointments/summary.
 */

type Compromisso = { id: string; title: string; type: string; status: string; startAt: string; clientId: string | null };
type Resumo = { year: number; month: number; count: number };

/** Ano exclusivo deste arquivo: nenhum outro teste agenda tão longe. */
const ANO = new Date().getUTCFullYear() + 5;
const marca = `[api-test] filtro-${Date.now().toString(36)}`;

let entrevista: Compromisso;
let pericia: Compromisso;

function emAno(mes: number, dia: number, hora: number) {
  return new Date(Date.UTC(ANO, mes - 1, dia, hora, 0, 0)).toISOString();
}

test.beforeAll(async ({ api }) => {
  entrevista = await dadosDe<Compromisso>(
    await api.post("/api/v1/appointments", {
      data: novoCompromisso({
        title: `${marca} entrevista`,
        type: "Entrevista",
        startAt: emAno(3, 10, 14),
        endAt: emAno(3, 10, 15),
      }),
    }),
    201,
  );
  pericia = await dadosDe<Compromisso>(
    await api.post("/api/v1/appointments", {
      data: novoCompromisso({
        title: `${marca} pericia`,
        type: "Perícia",
        startAt: emAno(7, 20, 9),
        endAt: emAno(7, 20, 10),
      }),
    }),
    201,
  );
});

test.afterAll(async ({ api }) => {
  for (const c of [entrevista, pericia]) {
    if (c) await api.delete(`/api/v1/appointments/${c.id}`);
  }
});

test("searchTerm filtra por título", async ({ api }) => {
  const { itens } = await listaDe<Compromisso>(
    await api.get(`/api/v1/appointments?searchTerm=${encodeURIComponent(marca)}`),
  );
  expect(itens.map((c) => c.id).sort()).toEqual([entrevista.id, pericia.id].sort());
});

test("year filtra e aceita valores repetidos", async ({ api }) => {
  const { itens } = await listaDe<Compromisso>(
    await api.get(`/api/v1/appointments?year=${ANO}&searchTerm=${encodeURIComponent(marca)}`),
  );
  expect(itens).toHaveLength(2);

  const doisAnos = await listaDe<Compromisso>(
    await api.get(`/api/v1/appointments?year=${ANO}&year=${ANO + 1}&searchTerm=${encodeURIComponent(marca)}`),
  );
  expect(doisAnos.itens).toHaveLength(2);
});

test("month restringe dentro do ano", async ({ api }) => {
  const { itens } = await listaDe<Compromisso>(
    await api.get(`/api/v1/appointments?year=${ANO}&month=3&searchTerm=${encodeURIComponent(marca)}`),
  );
  expect(itens.map((c) => c.id)).toEqual([entrevista.id]);

  const doisMeses = await listaDe<Compromisso>(
    await api.get(`/api/v1/appointments?year=${ANO}&month=3&month=7&searchTerm=${encodeURIComponent(marca)}`),
  );
  expect(doisMeses.itens).toHaveLength(2);
});

test("type filtra por label e por nome da constante", async ({ api }) => {
  const porLabel = await listaDe<Compromisso>(
    await api.get(`/api/v1/appointments?type=${encodeURIComponent("Perícia")}&searchTerm=${encodeURIComponent(marca)}`),
  );
  expect(porLabel.itens.map((c) => c.id)).toEqual([pericia.id]);

  const porConstante = await listaDe<Compromisso>(
    await api.get(`/api/v1/appointments?type=PERICIA&searchTerm=${encodeURIComponent(marca)}`),
  );
  expect(porConstante.itens.map((c) => c.id)).toEqual([pericia.id]);
});

test("status filtra", async ({ api }) => {
  await dadosDe(await api.patch(`/api/v1/appointments/${pericia.id}/cancel`, { data: { justification: "[api-test]" } }));

  const agendados = await listaDe<Compromisso>(
    await api.get(`/api/v1/appointments?status=Agendado&searchTerm=${encodeURIComponent(marca)}`),
  );
  expect(agendados.itens.map((c) => c.id)).toEqual([entrevista.id]);

  const cancelados = await listaDe<Compromisso>(
    await api.get(`/api/v1/appointments?status=Cancelado&searchTerm=${encodeURIComponent(marca)}`),
  );
  expect(cancelados.itens.map((c) => c.id)).toEqual([pericia.id]);
});

test("clientId filtra pelo cliente vinculado", async ({ api, clienteId }) => {
  const doCliente = await dadosDe<Compromisso>(
    await api.post("/api/v1/appointments", {
      data: novoCompromisso({ title: `${marca} do cliente`, clientId: clienteId }),
    }),
    201,
  );
  try {
    const { itens } = await listaDe<Compromisso>(await api.get(`/api/v1/appointments?clientId=${clienteId}`));
    expect(itens.map((c) => c.id)).toContain(doCliente.id);
    expect(itens.every((c) => c.clientId === clienteId)).toBe(true);
  } finally {
    await api.delete(`/api/v1/appointments/${doCliente.id}`);
  }
});

test("from/to filtram por intervalo quando não há ano/mês", async ({ api }) => {
  const { itens } = await listaDe<Compromisso>(
    await api.get(
      `/api/v1/appointments?from=${encodeURIComponent(emAno(7, 1, 0))}&to=${encodeURIComponent(emAno(7, 31, 23))}` +
        `&searchTerm=${encodeURIComponent(marca)}`,
    ),
  );
  expect(itens.map((c) => c.id)).toEqual([pericia.id]);
});

test("ano/mês têm precedência sobre from/to", async ({ api }) => {
  // Contrato documentado no Swagger: from/to só valem quando nenhum ano/mês vem.
  // Sem isto, as abas da agenda (que mandam ano+mês) e um intervalo esquecido na
  // query dariam resultado imprevisível.
  const { itens } = await listaDe<Compromisso>(
    await api.get(
      `/api/v1/appointments?year=${ANO}&month=3` +
        `&from=${encodeURIComponent(emAno(7, 1, 0))}&to=${encodeURIComponent(emAno(7, 31, 23))}` +
        `&searchTerm=${encodeURIComponent(marca)}`,
    ),
  );
  expect(itens.map((c) => c.id)).toEqual([entrevista.id]);
});

test("enum inválido devolve 400 em vez de ignorar o filtro", async ({ api }) => {
  await errosDe(await api.get("/api/v1/appointments?type=Piquenique"), 400);
  await errosDe(await api.get("/api/v1/appointments?status=Talvez"), 400);
});

test("data malformada em from devolve 400", async ({ api }) => {
  const erros = await errosDe(await api.get("/api/v1/appointments?from=semana-que-vem"), 400);
  expect(erros.map((e) => e.field)).toContain("from");
});

test.describe("GET /appointments/summary", () => {
  test("conta apenas os pendentes, por mês", async ({ api }) => {
    const resumo = await dadosDe<Resumo[]>(await api.get(`/api/v1/appointments/summary?year=${ANO}`));

    const marco = resumo.find((r) => r.month === 3);
    expect(marco, "março não apareceu no resumo").toBeTruthy();
    expect(marco!.count).toBeGreaterThanOrEqual(1);

    // A perícia de julho foi cancelada no teste de status: cancelado não é
    // pendente, e as abas da agenda contam pendência.
    const julho = resumo.find((r) => r.month === 7);
    expect(julho?.count ?? 0, "cancelado continuou contando como pendente").toBe(0);
  });

  test("toda ação reflete no número", async ({ api }) => {
    const antes = await dadosDe<Resumo[]>(await api.get(`/api/v1/appointments/summary?year=${ANO}`));
    const contaDe = (r: Resumo[], mes: number) => r.find((x) => x.month === mes)?.count ?? 0;

    const novo = await dadosDe<Compromisso>(
      await api.post("/api/v1/appointments", {
        data: novoCompromisso({ title: `${marca} conta`, startAt: emAno(9, 5, 10), endAt: emAno(9, 5, 11) }),
      }),
      201,
    );

    const criado = await dadosDe<Resumo[]>(await api.get(`/api/v1/appointments/summary?year=${ANO}`));
    expect(contaDe(criado, 9)).toBe(contaDe(antes, 9) + 1);

    await api.delete(`/api/v1/appointments/${novo.id}`);
    const excluido = await dadosDe<Resumo[]>(await api.get(`/api/v1/appointments/summary?year=${ANO}`));
    expect(contaDe(excluido, 9), "excluir não subtraiu do resumo").toBe(contaDe(antes, 9));
  });

  test("ano sem compromisso devolve zeros ou lista vazia, não 404", async ({ api }) => {
    const resposta = await api.get("/api/v1/appointments/summary?year=1999");
    expect(resposta.status()).toBe(200);
  });

  test("year é obrigatório", async ({ api }) => {
    expect((await api.get("/api/v1/appointments/summary")).status()).toBe(400);
  });
});
