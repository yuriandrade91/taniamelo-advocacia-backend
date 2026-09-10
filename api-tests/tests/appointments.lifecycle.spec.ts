import { test, expect } from "../src/fixtures.js";
import { dadosDe, errosDe, listaDe } from "../src/envelope.js";
import { novoCompromisso, janelaLivre } from "../src/factories.js";

/**
 * Cancelar, concluir, editar, excluir, restaurar — e a trilha de tudo isso.
 */

type Compromisso = { id: string; title: string; status: string; cancellationReason: string | null };
type Trilha = { id: string; action: string; justification: string | null; changedAt: string; changedByUserId: string | null };

const UUID_INEXISTENTE = "11111111-2222-3333-4444-555555555555";

async function novo(api: import("@playwright/test").APIRequestContext) {
  return dadosDe<Compromisso>(await api.post("/api/v1/appointments", { data: novoCompromisso() }), 201);
}

test.describe("cancelar", () => {
  test("exige justificativa, grava o motivo e registra na trilha", async ({ api }) => {
    const c = await novo(api);
    try {
      const semMotivo = await api.patch(`/api/v1/appointments/${c.id}/cancel`, { data: {} });
      expect(semMotivo.status()).toBe(400);

      const cancelado = await dadosDe<Compromisso>(
        await api.patch(`/api/v1/appointments/${c.id}/cancel`, { data: { justification: "  cliente desistiu  " } }),
      );
      expect(cancelado.status).toBe("Cancelado");
      expect(cancelado.cancellationReason).toBe("cliente desistiu");

      const trilha = await dadosDe<Trilha[]>(await api.get(`/api/v1/appointments/${c.id}/history`));
      const registro = trilha.find((h) => h.action === "CANCELLED");
      expect(registro?.justification).toBe("cliente desistiu");
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });

  test("cancelar duas vezes é recusado", async ({ api }) => {
    const c = await novo(api);
    try {
      await dadosDe(await api.patch(`/api/v1/appointments/${c.id}/cancel`, { data: { justification: "[api-test]" } }));
      // 422, não 400: é regra de negócio (o compromisso já está cancelado), não
      // corpo malformado. A distinção importa para a tela — 400 manda revisar o
      // formulário, 422 manda revisar a intenção.
      const erros = await errosDe(
        await api.patch(`/api/v1/appointments/${c.id}/cancel`, { data: { justification: "de novo" } }),
        422,
      );
      expect(erros.map((e) => e.code)).toContain("OPERATION_NOT_ALLOWED");
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });

  test("justificativa em branco é recusada", async ({ api }) => {
    const c = await novo(api);
    try {
      await errosDe(
        await api.patch(`/api/v1/appointments/${c.id}/cancel`, { data: { justification: "   " } }),
        400,
      );
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });
});

test.describe("concluir", () => {
  test("marca como Concluído", async ({ api }) => {
    const c = await novo(api);
    try {
      const concluido = await dadosDe<Compromisso>(await api.patch(`/api/v1/appointments/${c.id}/complete`));
      expect(concluido.status).toBe("Concluído");
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });

  test("compromisso cancelado não pode ser concluído", async ({ api }) => {
    const c = await novo(api);
    try {
      await dadosDe(await api.patch(`/api/v1/appointments/${c.id}/cancel`, { data: { justification: "[api-test]" } }));
      const erros = await errosDe(await api.patch(`/api/v1/appointments/${c.id}/complete`), 422);
      expect(erros.map((e) => e.code)).toContain("OPERATION_NOT_ALLOWED");
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });
});

test.describe("guarda de estado na edição", () => {
  test("compromisso cancelado não pode ser editado", async ({ api }) => {
    const c = await novo(api);
    try {
      await dadosDe(await api.patch(`/api/v1/appointments/${c.id}/cancel`, { data: { justification: "[api-test]" } }));

      const erros = await errosDe(
        await api.put(`/api/v1/appointments/${c.id}`, {
          data: novoCompromisso({ ...janelaLivre(), justification: "quero remarcar" }),
        }),
        422,
      );
      expect(erros.map((e) => e.code)).toContain("OPERATION_NOT_ALLOWED");
      // A mensagem tem de dizer QUAL estado impede, senão a pessoa tenta de novo igual.
      expect(erros.map((e) => e.message).join(" ")).toContain("cancelado");

      // E o compromisso não pode ter mudado nada no caminho.
      const depois = await dadosDe<Compromisso>(await api.get(`/api/v1/appointments/${c.id}`));
      expect(depois.status).toBe("Cancelado");
      expect(depois.title).toBe(c.title);
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });

  test("compromisso concluído não pode ser editado", async ({ api }) => {
    const c = await novo(api);
    try {
      await dadosDe(await api.patch(`/api/v1/appointments/${c.id}/complete`));
      const erros = await errosDe(
        await api.put(`/api/v1/appointments/${c.id}`, {
          data: novoCompromisso({ ...janelaLivre(), justification: "quero remarcar" }),
        }),
        422,
      );
      expect(erros.map((e) => e.message).join(" ")).toContain("concluído");
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });

  test("a guarda vem antes da checagem de justificativa", async ({ api }) => {
    const c = await novo(api);
    try {
      await dadosDe(await api.patch(`/api/v1/appointments/${c.id}/cancel`, { data: { justification: "[api-test]" } }));
      // Sem justificativa E cancelado: se o erro apontasse a justificativa, a
      // pessoa preencheria e levaria o mesmo 400 de novo.
      const erros = await errosDe(
        await api.put(`/api/v1/appointments/${c.id}`, { data: novoCompromisso() }),
        422,
      );
      expect(erros.map((e) => e.message).join(" ")).toContain("não pode ser editado");
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });
});

test.describe("excluir e restaurar", () => {
  test("excluir tira da agenda e deixa DELETED na trilha", async ({ api }) => {
    const c = await novo(api);
    expect((await api.delete(`/api/v1/appointments/${c.id}`)).status()).toBe(204);

    expect((await api.get(`/api/v1/appointments/${c.id}`)).status()).toBe(404);
    expect((await api.get(`/api/v1/appointments/${c.id}/history`)).status()).toBe(404);

    const { itens } = await listaDe<Compromisso>(await api.get("/api/v1/appointments?pageSize=100"));
    expect(itens.map((a) => a.id)).not.toContain(c.id);

    const restaurado = await dadosDe<Compromisso>(await api.patch(`/api/v1/appointments/${c.id}/restore`));
    expect(restaurado.id).toBe(c.id);
    expect(restaurado.status).toBe("Agendado");

    const trilha = await dadosDe<Trilha[]>(await api.get(`/api/v1/appointments/${c.id}/history`));
    const acoes = trilha.map((h) => h.action);
    // Excluir era a ação mais destrutiva e a única sem rastro, numa entidade
    // que o javadoc diz preservar "para auditoria".
    expect(acoes, "exclusão não deixou rastro").toContain("DELETED");
    expect(acoes, "restauração não deixou rastro").toContain("RESTORED");

    const registroExclusao = trilha.find((h) => h.action === "DELETED")!;
    // Excluir não pede motivo; o que a trilha precisa guardar é quem e quando.
    expect(registroExclusao.justification).toBeNull();
    expect(registroExclusao.changedByUserId).toBeTruthy();
    expect(registroExclusao.changedAt).toBeTruthy();

    await api.delete(`/api/v1/appointments/${c.id}`);
  });

  test("excluir duas vezes devolve 404 na segunda", async ({ api }) => {
    const c = await novo(api);
    expect((await api.delete(`/api/v1/appointments/${c.id}`)).status()).toBe(204);
    expect((await api.delete(`/api/v1/appointments/${c.id}`)).status()).toBe(404);
    await api.patch(`/api/v1/appointments/${c.id}/restore`);
    await api.delete(`/api/v1/appointments/${c.id}`);
  });

  test("restore em compromisso ativo é no-op e não polui a trilha", async ({ api }) => {
    const c = await novo(api);
    try {
      const antes = await dadosDe<Trilha[]>(await api.get(`/api/v1/appointments/${c.id}/history`));
      expect((await api.patch(`/api/v1/appointments/${c.id}/restore`)).status()).toBe(200);
      const depois = await dadosDe<Trilha[]>(await api.get(`/api/v1/appointments/${c.id}/history`));
      expect(depois.length, "restore de ativo gerou registro").toBe(antes.length);
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });

  test("restore de id inexistente devolve 404", async ({ api }) => {
    expect((await api.patch(`/api/v1/appointments/${UUID_INEXISTENTE}/restore`)).status()).toBe(404);
  });

  test("restaurar preserva status, horário e trilha anteriores", async ({ api }) => {
    const c = await novo(api);
    await dadosDe(await api.patch(`/api/v1/appointments/${c.id}/cancel`, { data: { justification: "[api-test] motivo" } }));
    const antes = await dadosDe<Compromisso>(await api.get(`/api/v1/appointments/${c.id}`));

    await api.delete(`/api/v1/appointments/${c.id}`);
    const voltou = await dadosDe<Compromisso>(await api.patch(`/api/v1/appointments/${c.id}/restore`));

    // Restaurar não é recriar: o compromisso volta como estava, cancelado inclusive.
    expect(voltou.status).toBe(antes.status);
    expect(voltou.cancellationReason).toBe(antes.cancellationReason);

    const trilha = await dadosDe<Trilha[]>(await api.get(`/api/v1/appointments/${c.id}/history`));
    expect(trilha.map((h) => h.action)).toContain("CANCELLED");

    await api.delete(`/api/v1/appointments/${c.id}`);
  });
});

test.describe("GET /appointments/{id}/history", () => {
  test("vem paginado, mais recente primeiro", async ({ api }) => {
    const c = await novo(api);
    try {
      await dadosDe(
        await api.put(`/api/v1/appointments/${c.id}`, {
          data: novoCompromisso({ ...janelaLivre(), justification: "primeira edição" }),
        }),
      );
      await dadosDe(
        await api.put(`/api/v1/appointments/${c.id}`, {
          data: novoCompromisso({ ...janelaLivre(), justification: "segunda edição" }),
        }),
      );

      const { itens, pagina } = await listaDe<Trilha>(await api.get(`/api/v1/appointments/${c.id}/history`));
      expect(pagina.totalRecords).toBeGreaterThanOrEqual(2);
      expect(itens[0].justification).toBe("segunda edição");

      const datas = itens.map((h) => new Date(h.changedAt).getTime());
      expect(datas, "trilha fora de ordem").toEqual([...datas].sort((a, b) => b - a));
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });

  test("compromisso sem alterações devolve trilha vazia, não 404", async ({ api }) => {
    const c = await novo(api);
    try {
      const resposta = await api.get(`/api/v1/appointments/${c.id}/history`);
      expect(resposta.status()).toBe(200);
    } finally {
      await api.delete(`/api/v1/appointments/${c.id}`);
    }
  });
});
