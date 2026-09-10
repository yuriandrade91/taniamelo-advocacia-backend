import { test, expect } from "../src/fixtures.js";
import { dadosDe } from "../src/envelope.js";
import { novoCompromisso, futuroIso } from "../src/factories.js";

/**
 * GET /appointments/conflicts.
 *
 * A rota AVISA, não bloqueia: perícia e audiência se sobrepõem de propósito, e
 * recusar a gravação obrigaria a contornar o sistema — que é como um sistema
 * deixa de refletir a realidade. Por isso há um teste afirmando que o POST
 * continua aceitando o horário conflitante.
 */

type Compromisso = { id: string; title: string; startAt: string; endAt: string };

/** Dia exclusivo deste arquivo, para nenhum outro teste entrar na janela. */
const DIA = 200;
const inicio = futuroIso(DIA, 14, 0);
const fim = futuroIso(DIA, 15, 0);

let existente: Compromisso;

test.beforeAll(async ({ api }) => {
  existente = await dadosDe<Compromisso>(
    await api.post("/api/v1/appointments", {
      data: novoCompromisso({ title: "[api-test] Ocupa 14h-15h", startAt: inicio, endAt: fim }),
    }),
    201,
  );
});

test.afterAll(async ({ api }) => {
  if (existente) await api.delete(`/api/v1/appointments/${existente.id}`);
});

function conflitos(api: import("@playwright/test").APIRequestContext, de: string, ate: string, excludeId?: string) {
  const query = new URLSearchParams({ startAt: de, endAt: ate });
  if (excludeId) query.set("excludeId", excludeId);
  return api.get(`/api/v1/appointments/conflicts?${query.toString()}`);
}

test("faixa idêntica acusa conflito e diz COM QUEM", async ({ api }) => {
  const achados = await dadosDe<Compromisso[]>(await conflitos(api, inicio, fim));
  const encontrado = achados.find((c) => c.id === existente.id);
  expect(encontrado, "conflito idêntico não foi detectado").toBeTruthy();
  // "Existe conflito" sem dizer com o quê obriga a abrir outra tela para decidir.
  expect(encontrado!.title).toBe("[api-test] Ocupa 14h-15h");
});

test("sobreposição parcial no começo e no fim acusa conflito", async ({ api }) => {
  const antes = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA, 13, 30), futuroIso(DIA, 14, 30)),
  );
  expect(antes.map((c) => c.id)).toContain(existente.id);

  const depois = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA, 14, 30), futuroIso(DIA, 15, 30)),
  );
  expect(depois.map((c) => c.id)).toContain(existente.id);
});

test("janela que engloba o existente acusa conflito", async ({ api }) => {
  const achados = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA, 13, 0), futuroIso(DIA, 16, 0)),
  );
  expect(achados.map((c) => c.id)).toContain(existente.id);
});

test("janela contida dentro do existente acusa conflito", async ({ api }) => {
  const achados = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA, 14, 15), futuroIso(DIA, 14, 45)),
  );
  expect(achados.map((c) => c.id)).toContain(existente.id);
});

test("intervalos que só encostam NÃO conflitam", async ({ api }) => {
  // 13h-14h e 15h-16h contra um 14h-15h. Intervalo é meio-aberto: acusar aqui
  // faria a agenda avisar de conflito em toda sequência de reuniões coladas, e
  // o aviso viraria ruído que se aprende a ignorar.
  const encostaAntes = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA, 13, 0), futuroIso(DIA, 14, 0)),
  );
  expect(encostaAntes.map((c) => c.id), "13h-14h não deveria conflitar com 14h-15h").not.toContain(existente.id);

  const encostaDepois = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA, 15, 0), futuroIso(DIA, 16, 0)),
  );
  expect(encostaDepois.map((c) => c.id), "15h-16h não deveria conflitar com 14h-15h").not.toContain(existente.id);
});

test("horário completamente livre devolve lista vazia", async ({ api }) => {
  const achados = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA + 1, 9, 0), futuroIso(DIA + 1, 10, 0)),
  );
  expect(achados).toEqual([]);
});

test("excludeId impede o compromisso de conflitar consigo mesmo", async ({ api }) => {
  const semExclusao = await dadosDe<Compromisso[]>(await conflitos(api, inicio, fim));
  expect(semExclusao.map((c) => c.id)).toContain(existente.id);

  // Sem isto, toda edição de horário mostraria o próprio compromisso como conflito.
  const comExclusao = await dadosDe<Compromisso[]>(await conflitos(api, inicio, fim, existente.id));
  expect(comExclusao.map((c) => c.id)).not.toContain(existente.id);
});

test("compromisso cancelado deixa de disputar horário", async ({ api }) => {
  const outro = await dadosDe<Compromisso>(
    await api.post("/api/v1/appointments", {
      data: novoCompromisso({
        title: "[api-test] Será cancelado",
        startAt: futuroIso(DIA + 2, 10, 0),
        endAt: futuroIso(DIA + 2, 11, 0),
      }),
    }),
    201,
  );

  const antes = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA + 2, 10, 0), futuroIso(DIA + 2, 11, 0)),
  );
  expect(antes.map((c) => c.id)).toContain(outro.id);

  await dadosDe(await api.patch(`/api/v1/appointments/${outro.id}/cancel`, { data: { justification: "[api-test]" } }));

  const depois = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA + 2, 10, 0), futuroIso(DIA + 2, 11, 0)),
  );
  // Avisar de conflito com algo que não vai acontecer é pior que não avisar.
  expect(depois.map((c) => c.id), "cancelado continuou aparecendo como conflito").not.toContain(outro.id);

  await api.delete(`/api/v1/appointments/${outro.id}`);
});

test("compromisso concluído deixa de disputar horário", async ({ api }) => {
  const outro = await dadosDe<Compromisso>(
    await api.post("/api/v1/appointments", {
      data: novoCompromisso({
        title: "[api-test] Será concluído",
        startAt: futuroIso(DIA + 3, 10, 0),
        endAt: futuroIso(DIA + 3, 11, 0),
      }),
    }),
    201,
  );

  await dadosDe(await api.patch(`/api/v1/appointments/${outro.id}/complete`));
  const depois = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA + 3, 10, 0), futuroIso(DIA + 3, 11, 0)),
  );
  expect(depois.map((c) => c.id)).not.toContain(outro.id);

  await api.delete(`/api/v1/appointments/${outro.id}`);
});

test("compromisso excluído deixa de disputar horário", async ({ api }) => {
  const outro = await dadosDe<Compromisso>(
    await api.post("/api/v1/appointments", {
      data: novoCompromisso({
        title: "[api-test] Será excluído",
        startAt: futuroIso(DIA + 4, 10, 0),
        endAt: futuroIso(DIA + 4, 11, 0),
      }),
    }),
    201,
  );
  await api.delete(`/api/v1/appointments/${outro.id}`);

  const depois = await dadosDe<Compromisso[]>(
    await conflitos(api, futuroIso(DIA + 4, 10, 0), futuroIso(DIA + 4, 11, 0)),
  );
  expect(depois.map((c) => c.id)).not.toContain(outro.id);
});

test("janela invertida devolve lista vazia, não 400", async ({ api }) => {
  // É consulta disparada enquanto a pessoa digita: um 400 a cada tecla poluiria
  // a tela com erro que não é erro.
  const achados = await dadosDe<Compromisso[]>(await conflitos(api, fim, inicio));
  expect(achados).toEqual([]);
});

test("parâmetro ausente é recusado, e não silenciosamente ignorado", async ({ api }) => {
  const semFim = await api.get(`/api/v1/appointments/conflicts?startAt=${encodeURIComponent(inicio)}`);
  // startAt e endAt são obrigatórios no contrato; sem endAt não há janela.
  expect(semFim.status()).toBe(400);
});

test("o POST continua aceitando horário conflitante — avisa, não bloqueia", async ({ api }) => {
  const conflitante = await api.post("/api/v1/appointments", {
    data: novoCompromisso({ title: "[api-test] Sobrepõe de propósito", startAt: inicio, endAt: fim }),
  });
  expect(
    conflitante.status(),
    "o backend passou a BLOQUEAR conflito; perícia e audiência se sobrepõem de propósito",
  ).toBe(201);

  const criado = await dadosDe<Compromisso>(conflitante, 201);
  await api.delete(`/api/v1/appointments/${criado.id}`);
});
