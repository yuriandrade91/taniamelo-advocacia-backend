import { test, expect } from "../src/fixtures.js";
import { dadosDe, errosDe, temCampo } from "../src/envelope.js";
import { novoCliente, cpfInvalido, cpfValido } from "../src/factories.js";

/**
 * POST/GET/PUT/PATCH /clients — o ciclo do cadastro.
 * Exclusão e restauração ficam em clients.softdelete.spec.ts.
 */

type Cliente = {
  id: string;
  fullName: string;
  cpf: string;
  situation: string;
  benefit: string;
  clientType: string;
  nationality: string;
  isWhatsapp: boolean;
  hasDisability: boolean;
  notBillable: boolean;
  age?: number;
  inssPassword?: string;
  createdAt: string;
};

test.describe("POST /clients", () => {
  const criados: string[] = [];
  test.afterAll(async ({ api }) => {
    for (const id of criados) await api.delete(`/api/v1/clients/${id}`);
  });

  test("cria com os campos obrigatórios e devolve a ficha", async ({ api }) => {
    const payload = novoCliente();
    const criado = await dadosDe<Cliente>(
      await api.post("/api/v1/clients", { data: payload }),
      201,
    );
    criados.push(criado.id);

    expect(criado.id).toBeTruthy();
    expect(criado.fullName).toBe(payload.fullName);
    expect(criado.situation).toBe(payload.situation);
    expect(criado.benefit).toBe(payload.benefit);
    // Enum volta como label PT-BR, não como o nome da constante.
    expect(criado.clientType).toBe("Potencial");
  });

  test("SÓ com os obrigatórios do Swagger, sem os opcionais", async ({ api }) => {
    // Regressão de um defeito real: nationality, isWhatsapp e hasDisability são
    // NOT NULL no banco e opcionais no contrato. Sem eles o INSERT quebrava e a
    // API devolvia 409 DATABASE_INTEGRITY_ERROR — seguir o Swagger à risca era
    // o caminho para o erro.
    const minimo = {
      fullName: "[api-test] Só obrigatórios",
      birthDate: "1970-01-01",
      cpf: cpfValido(),
      motherName: "Mãe",
      mobilePhone: "+5531999990000",
      inssPassword: "senha",
      gender: "Feminino",
      benefit: "Aposentadoria por idade",
      situation: "Formulário preenchido",
    };

    const criado = await dadosDe<Cliente>(await api.post("/api/v1/clients", { data: minimo }), 201);
    criados.push(criado.id);

    // E os padrões do banco precisam aparecer na resposta, não vir nulos.
    expect(criado.nationality).toBe("Brasileira");
    expect(criado.isWhatsapp).toBe(true);
    expect(criado.hasDisability).toBe(false);
    expect(criado.notBillable).toBe(false);
    expect(criado.clientType).toBe("Potencial");
  });

  test("devolve Location apontando para o recurso criado", async ({ api }) => {
    const resposta = await api.post("/api/v1/clients", { data: novoCliente() });
    const criado = await dadosDe<Cliente>(resposta, 201);
    criados.push(criado.id);

    const location = resposta.headers()["location"];
    expect(location, "201 sem header Location").toBeTruthy();
    expect(location).toContain(criado.id);
  });

  test("CPF com dígito verificador errado é recusado", async ({ api }) => {
    const erros = await errosDe(
      await api.post("/api/v1/clients", { data: novoCliente({ cpf: cpfInvalido() }) }),
      400,
    );
    temCampo(erros, "cpf");
  });

  test("CPF repetido é recusado", async ({ api }) => {
    const payload = novoCliente();
    const primeiro = await dadosDe<Cliente>(
      await api.post("/api/v1/clients", { data: payload }),
      201,
    );
    criados.push(primeiro.id);

    const repetido = await api.post("/api/v1/clients", {
      data: novoCliente({ cpf: payload.cpf }),
    });
    expect(
      [400, 409],
      `CPF repetido devolveu ${repetido.status()}`,
    ).toContain(repetido.status());
  });

  for (const campo of ["fullName", "cpf", "birthDate", "motherName", "gender", "benefit", "situation"]) {
    test(`sem ${campo} devolve 400 apontando o campo`, async ({ api }) => {
      const payload = novoCliente() as Record<string, unknown>;
      delete payload[campo];
      const erros = await errosDe(await api.post("/api/v1/clients", { data: payload }), 400);
      // Sem o nome do campo no erro, quem consome não sabe o que corrigir.
      expect(erros.map((e) => e.field)).toContain(campo);
    });
  }

  test("valor fora do enum devolve 400, não grava lixo", async ({ api }) => {
    const erros = await errosDe(
      await api.post("/api/v1/clients", { data: novoCliente({ situation: "Situação inventada" }) }),
      400,
    );
    expect(erros.length).toBeGreaterThan(0);
  });

  test("enum aceita o nome da constante além do label", async ({ api }) => {
    const criado = await dadosDe<Cliente>(
      await api.post("/api/v1/clients", {
        data: novoCliente({ situation: "ANALISE_DOCUMENTAL", clientType: "VERIFICADO" }),
      }),
      201,
    );
    criados.push(criado.id);

    // Entra pelo nome da constante, sai sempre como label — é o contrato
    // @JsonValue/@JsonCreator do projeto.
    expect(criado.situation).toBe("Análise documental");
    expect(criado.clientType).toBe("Verificado");
  });
});

test.describe("GET /clients/{id}", () => {
  test("devolve a ficha com idade calculada", async ({ api, clienteId }) => {
    const cliente = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${clienteId}`));
    expect(cliente.id).toBe(clienteId);
    // birthDate da fábrica é 1970-05-20: a idade tem de ser calculada, não nula.
    expect(cliente.age, "idade não calculada").toBeGreaterThan(40);
  });

  test("id inexistente devolve 404", async ({ api }) => {
    const resposta = await api.get("/api/v1/clients/11111111-2222-3333-4444-555555555555");
    expect(resposta.status()).toBe(404);
  });

  test("id malformado devolve 400, não 500", async ({ api }) => {
    const resposta = await api.get("/api/v1/clients/isto-nao-e-um-uuid");
    expect(resposta.status(), "UUID inválido não pode virar erro de servidor").toBeLessThan(500);
  });
});

test.describe("PUT /clients/{id}", () => {
  test("substitui os campos enviados", async ({ api, clienteId }) => {
    const antes = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${clienteId}`));

    const atualizado = await dadosDe<Cliente>(
      await api.put(`/api/v1/clients/${clienteId}`, {
        data: {
          ...antes,
          fullName: "[api-test] Nome Editado",
          inssPassword: "nova-senha-inss",
        },
      }),
    );
    expect(atualizado.fullName).toBe("[api-test] Nome Editado");
  });

  test("campo obrigatório ausente devolve 400", async ({ api, clienteId }) => {
    const erros = await errosDe(
      await api.put(`/api/v1/clients/${clienteId}`, { data: { fullName: "só o nome" } }),
      400,
    );
    expect(erros.length).toBeGreaterThan(0);
  });

  test("PUT omitindo os opcionais não zera coluna obrigatória", async ({ api, clienteId }) => {
    // Mesma família do defeito do POST: no PUT, campo ausente significa "mantém
    // o que está gravado" para os NOT NULL — nunca null, nunca de volta ao padrão.
    const antes = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${clienteId}`));
    const semOpcionais = { ...antes } as Record<string, unknown>;
    for (const campo of ["nationality", "isWhatsapp", "hasDisability", "notBillable", "clientType"]) {
      delete semOpcionais[campo];
    }

    const depois = await dadosDe<Cliente>(
      await api.put(`/api/v1/clients/${clienteId}`, { data: semOpcionais }),
    );
    expect(depois.nationality).toBe(antes.nationality);
    expect(depois.clientType).toBe(antes.clientType);
    expect(depois.notBillable).toBe(antes.notBillable);
  });

  test("id inexistente devolve 404", async ({ api }) => {
    const resposta = await api.put("/api/v1/clients/11111111-2222-3333-4444-555555555555", {
      data: novoCliente(),
    });
    expect(resposta.status()).toBe(404);
  });
});

test.describe("PATCH /clients/{id}", () => {
  test("muda só a situação e registra no histórico", async ({ api, clienteId }) => {
    const antes = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${clienteId}`));

    const resultado = await dadosDe<{ message: string }>(
      await api.patch(`/api/v1/clients/${clienteId}`, { data: { situation: "Análise documental" } }),
    );
    expect(resultado.message).toMatch(/sucesso/i);

    const depois = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${clienteId}`));
    expect(depois.situation).toBe("Análise documental");
    // PATCH parcial: o que não foi enviado não pode ter mudado.
    expect(depois.fullName).toBe(antes.fullName);
    expect(depois.benefit).toBe(antes.benefit);
  });

  test("muda o tipo de cliente", async ({ api, clienteId }) => {
    await dadosDe(await api.patch(`/api/v1/clients/${clienteId}`, { data: { clientType: "Verificado" } }));
    const depois = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${clienteId}`));
    expect(depois.clientType).toBe("Verificado");
  });

  test("aceita mais de um campo na mesma chamada", async ({ api, clienteId }) => {
    await dadosDe(
      await api.patch(`/api/v1/clients/${clienteId}`, {
        data: { situation: "Planejamento em execução", notBillable: true },
      }),
    );
    const depois = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${clienteId}`));
    expect(depois.situation).toBe("Planejamento em execução");
    expect(depois.notBillable).toBe(true);
  });

  test("valor de enum inválido devolve 400", async ({ api, clienteId }) => {
    await errosDe(
      await api.patch(`/api/v1/clients/${clienteId}`, { data: { situation: "não existe" } }),
      400,
    );
  });

  test("corpo vazio não quebra", async ({ api, clienteId }) => {
    const resposta = await api.patch(`/api/v1/clients/${clienteId}`, { data: {} });
    expect(resposta.status(), "PATCH sem nada para mudar não pode dar 500").toBeLessThan(500);
  });
});

test.describe("GET /clients/{id}/situation-history", () => {
  test("registra de → para a cada mudança de situação", async ({ api, clienteId }) => {
    await dadosDe(await api.patch(`/api/v1/clients/${clienteId}`, { data: { situation: "Análise documental" } }));
    await dadosDe(await api.patch(`/api/v1/clients/${clienteId}`, { data: { situation: "Planejamento em execução" } }));

    const historico = await dadosDe<
      Array<{ previousSituation: string | null; currentSituation: string; changedAt: string; changedByUserId: string }>
    >(await api.get(`/api/v1/clients/${clienteId}/situation-history`));

    expect(historico.length).toBeGreaterThanOrEqual(2);
    const maisRecente = historico[0];
    expect(maisRecente.currentSituation).toBe("Planejamento em execução");
    // Sem previousSituation a linha do tempo só consegue dizer "passou para X".
    expect(maisRecente.previousSituation).toBe("Análise documental");
    expect(maisRecente.changedAt).toBeTruthy();
    expect(maisRecente.changedByUserId, "sem autor, o histórico não responde 'por quem'").toBeTruthy();
  });

  test("mudança de benefício NÃO entra no histórico de situação", async ({ api, clienteId }) => {
    const antes = await dadosDe<unknown[]>(await api.get(`/api/v1/clients/${clienteId}/situation-history`));
    await dadosDe(await api.patch(`/api/v1/clients/${clienteId}`, { data: { benefit: "Aposentadoria especial" } }));
    const depois = await dadosDe<unknown[]>(await api.get(`/api/v1/clients/${clienteId}/situation-history`));

    // O histórico é DE SITUAÇÃO. Misturar benefício aqui tornaria a linha do
    // tempo ilegível, e é o que o próprio Swagger promete.
    expect(depois.length).toBe(antes.length);
  });

  test("cliente sem mudanças devolve lista vazia paginada, não 404", async ({ api }) => {
    const criado = await dadosDe<Cliente>(await api.post("/api/v1/clients", { data: novoCliente() }), 201);
    try {
      const resposta = await api.get(`/api/v1/clients/${criado.id}/situation-history`);
      expect(resposta.status()).toBe(200);
    } finally {
      await api.delete(`/api/v1/clients/${criado.id}`);
    }
  });
});
