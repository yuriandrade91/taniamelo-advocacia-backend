import { test, expect } from "../src/fixtures.js";
import { dadosDe, listaDe, errosDe } from "../src/envelope.js";
import { novoEndereco } from "../src/factories.js";

/**
 * Sub-recursos de /clients/{id}: endereços, entrevistas, abas de dados e o
 * financeiro do cliente (o que o INSS paga a ELE — não honorário do escritório).
 */

type Endereco = { id: string; street: string; city: string; isPrimary: boolean; addressType: string };
type Entrevista = { id: string; content: string; occurredAt: string; durationMinutes?: number };
type Pagamento = { id: string; description: string; amount: number; status: string; paidDate: string | null; overdue?: boolean };

const UUID_INEXISTENTE = "11111111-2222-3333-4444-555555555555";

test.describe("endereços", () => {
  test("o primeiro endereço vira principal automaticamente", async ({ api, clienteId }) => {
    const criado = await dadosDe<Endereco>(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: novoEndereco() }),
      201,
    );
    expect(criado.isPrimary, "o primeiro endereço deveria nascer principal").toBe(true);
  });

  test("marcar um novo como principal desmarca o anterior", async ({ api, clienteId }) => {
    const primeiro = await dadosDe<Endereco>(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: novoEndereco() }),
      201,
    );
    const segundo = await dadosDe<Endereco>(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, {
        data: novoEndereco({ isPrimary: true, addressType: "Comercial" }),
      }),
      201,
    );

    const { itens } = await listaDe<Endereco>(await api.get(`/api/v1/clients/${clienteId}/addresses`));
    const principais = itens.filter((e) => e.isPrimary);
    // O invariante é do servidor, não da tela: dois principais é estado inválido.
    expect(principais.map((e) => e.id), "deveria haver exatamente um principal").toEqual([segundo.id]);
    expect(primeiro.id).toBeTruthy();
  });

  test("a listagem traz o principal primeiro", async ({ api, clienteId }) => {
    await dadosDe(await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: novoEndereco() }), 201);
    await dadosDe(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: novoEndereco({ isPrimary: true }) }),
      201,
    );
    const { itens } = await listaDe<Endereco>(await api.get(`/api/v1/clients/${clienteId}/addresses`));
    expect(itens[0].isPrimary).toBe(true);
  });

  test("excluir o principal promove o mais antigo restante", async ({ api, clienteId }) => {
    const antigo = await dadosDe<Endereco>(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: novoEndereco() }),
      201,
    );
    const principal = await dadosDe<Endereco>(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: novoEndereco({ isPrimary: true }) }),
      201,
    );

    expect((await api.delete(`/api/v1/clients/${clienteId}/addresses/${principal.id}`)).status()).toBe(204);

    const { itens } = await listaDe<Endereco>(await api.get(`/api/v1/clients/${clienteId}/addresses`));
    // Cliente com endereço e sem principal é estado inválido: alguém tem de ser promovido.
    expect(itens.find((e) => e.id === antigo.id)?.isPrimary).toBe(true);
  });

  test("tipo de endereço ausente cai em Residencial", async ({ api, clienteId }) => {
    const semTipo = novoEndereco() as Record<string, unknown>;
    delete semTipo.addressType;
    const criado = await dadosDe<Endereco>(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: semTipo }),
      201,
    );
    expect(criado.addressType).toBe("Residencial");
  });

  test("tipo de endereço inválido devolve 400", async ({ api, clienteId }) => {
    const erros = await errosDe(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, {
        data: novoEndereco({ addressType: "Submarino" }),
      }),
      400,
    );
    expect(erros.map((e) => e.field)).toContain("addressType");
  });

  for (const campo of ["street", "city", "state"]) {
    test(`endereço sem ${campo} devolve 400`, async ({ api, clienteId }) => {
      const payload = novoEndereco() as Record<string, unknown>;
      delete payload[campo];
      const erros = await errosDe(
        await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: payload }),
        400,
      );
      expect(erros.map((e) => e.field)).toContain(campo);
    });
  }

  test("PUT substitui o endereço", async ({ api, clienteId }) => {
    const criado = await dadosDe<Endereco>(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: novoEndereco() }),
      201,
    );
    const atualizado = await dadosDe<Endereco>(
      await api.put(`/api/v1/clients/${clienteId}/addresses/${criado.id}`, {
        data: novoEndereco({ street: "Rua Nova", city: "Contagem" }),
      }),
    );
    expect(atualizado.street).toBe("Rua Nova");
    expect(atualizado.city).toBe("Contagem");
  });

  test("o único endereço do cliente continua principal mesmo pedindo isPrimary=false", async ({ api, clienteId }) => {
    const unico = await dadosDe<Endereco>(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: novoEndereco() }),
      201,
    );
    const atualizado = await dadosDe<Endereco>(
      await api.put(`/api/v1/clients/${clienteId}/addresses/${unico.id}`, {
        data: novoEndereco({ isPrimary: false }),
      }),
    );
    expect(atualizado.isPrimary, "o único endereço não pode deixar de ser principal").toBe(true);
  });

  test("endereço de outro cliente devolve 404", async ({ api, clienteId }) => {
    const criado = await dadosDe<Endereco>(
      await api.post(`/api/v1/clients/${clienteId}/addresses`, { data: novoEndereco() }),
      201,
    );
    expect((await api.get(`/api/v1/clients/${UUID_INEXISTENTE}/addresses/${criado.id}`)).status()).toBe(404);
  });
});

test.describe("endereços em lote", () => {
  test("grava a lista inteira, na ordem enviada", async ({ api, clienteId }) => {
    const enviados = [
      novoEndereco({ street: "Rua Um" }),
      novoEndereco({ street: "Rua Dois" }),
      novoEndereco({ street: "Rua Três" }),
    ];
    const criados = await dadosDe<Endereco[]>(
      await api.post(`/api/v1/clients/${clienteId}/addresses/batch`, { data: { addresses: enviados } }),
      201,
    );
    expect(criados.map((e) => e.street)).toEqual(["Rua Um", "Rua Dois", "Rua Três"]);
  });

  test("só o primeiro da lista vira principal", async ({ api, clienteId }) => {
    await dadosDe<Endereco[]>(
      await api.post(`/api/v1/clients/${clienteId}/addresses/batch`, {
        data: { addresses: [novoEndereco(), novoEndereco()] },
      }),
      201,
    );
    const { itens } = await listaDe<Endereco>(await api.get(`/api/v1/clients/${clienteId}/addresses`));
    expect(itens.filter((e) => e.isPrimary)).toHaveLength(1);
  });

  test("se um item da lista é inválido, NENHUM é gravado", async ({ api, clienteId }) => {
    const invalido = novoEndereco() as Record<string, unknown>;
    delete invalido.street;

    const resposta = await api.post(`/api/v1/clients/${clienteId}/addresses/batch`, {
      data: { addresses: [novoEndereco({ street: "Rua Que Não Deve Entrar" }), invalido] },
    });
    expect(resposta.status()).toBe(400);

    // O ponto do endpoint é a atomicidade: meio cadastro é pior que nenhum,
    // porque ninguém sabe o que entrou.
    const { itens } = await listaDe<Endereco>(await api.get(`/api/v1/clients/${clienteId}/addresses`));
    expect(itens.map((e) => e.street)).not.toContain("Rua Que Não Deve Entrar");
  });

  test("lista vazia devolve 400", async ({ api, clienteId }) => {
    await errosDe(
      await api.post(`/api/v1/clients/${clienteId}/addresses/batch`, { data: { addresses: [] } }),
      400,
    );
  });

  test("mais de 10 endereços devolve 400", async ({ api, clienteId }) => {
    const onze = Array.from({ length: 11 }, () => novoEndereco());
    await errosDe(
      await api.post(`/api/v1/clients/${clienteId}/addresses/batch`, { data: { addresses: onze } }),
      400,
    );
  });

  test("cliente inexistente devolve 404 sem gravar nada", async ({ api }) => {
    const resposta = await api.post(`/api/v1/clients/${UUID_INEXISTENTE}/addresses/batch`, {
      data: { addresses: [novoEndereco()] },
    });
    expect(resposta.status()).toBe(404);
  });
});

test.describe("entrevistas", () => {
  test("cria, lista e atualiza", async ({ api, clienteId }) => {
    const criada = await dadosDe<Entrevista>(
      await api.post(`/api/v1/clients/${clienteId}/interviews`, {
        data: { content: "[api-test] Primeira conversa", durationMinutes: 30 },
      }),
      201,
    );
    expect(criada.content).toContain("Primeira conversa");
    // occurredAt ausente assume agora — a entrevista aconteceu quando foi registrada.
    expect(criada.occurredAt).toBeTruthy();

    const atualizada = await dadosDe<Entrevista>(
      await api.put(`/api/v1/clients/${clienteId}/interviews/${criada.id}`, {
        data: { content: "[api-test] Conversa revisada", durationMinutes: 45 },
      }),
    );
    expect(atualizada.content).toContain("revisada");
    expect(atualizada.durationMinutes).toBe(45);

    const { itens } = await listaDe<Entrevista>(await api.get(`/api/v1/clients/${clienteId}/interviews`));
    expect(itens.map((e) => e.id)).toContain(criada.id);
  });

  test("conteúdo vazio devolve 400", async ({ api, clienteId }) => {
    const erros = await errosDe(
      await api.post(`/api/v1/clients/${clienteId}/interviews`, { data: { content: "   " } }),
      400,
    );
    expect(erros.map((e) => e.field)).toContain("content");
  });

  test("DELETE devolve 204 e some da lista", async ({ api, clienteId }) => {
    const criada = await dadosDe<Entrevista>(
      await api.post(`/api/v1/clients/${clienteId}/interviews`, { data: { content: "[api-test] apagar" } }),
      201,
    );
    expect((await api.delete(`/api/v1/clients/${clienteId}/interviews/${criada.id}`)).status()).toBe(204);
    expect((await api.get(`/api/v1/clients/${clienteId}/interviews/${criada.id}`)).status()).toBe(404);
  });

  test("entrevista inexistente devolve 404", async ({ api, clienteId }) => {
    expect((await api.get(`/api/v1/clients/${clienteId}/interviews/${UUID_INEXISTENTE}`)).status()).toBe(404);
  });
});

test.describe("abas de dados pessoais e profissionais", () => {
  /**
   * A aba pessoal repete os campos de identidade do cliente e os exige
   * (@NotBlank/@NotNull): ela é a substituição da aba inteira, não um patch.
   * Por isso o teste parte do que está gravado em vez de mandar só o que muda.
   */
  async function identidade(api: import("@playwright/test").APIRequestContext, clienteId: string) {
    const atual = await dadosDe<Record<string, unknown>>(
      await api.get(`/api/v1/clients/${clienteId}/personal-data`),
    );
    return {
      fullName: atual.fullName,
      birthDate: atual.birthDate,
      cpf: atual.cpf,
      motherName: atual.motherName,
      gender: atual.gender,
      mobilePhone: atual.mobilePhone,
    };
  }

  test("aba pessoal grava e devolve o que foi enviado", async ({ api, clienteId }) => {
    const base = await identidade(api, clienteId);
    const salvo = await dadosDe<{ rg: string; email: string; referenceResponsible: string }>(
      await api.put(`/api/v1/clients/${clienteId}/personal-data`, {
        data: { ...base, rg: "MG-12.345.678", email: "teste@exemplo.com", referenceResponsible: "Irmã" },
      }),
    );
    expect(salvo.rg).toBe("MG-12.345.678");
    expect(salvo.email).toBe("teste@exemplo.com");
    expect(salvo.referenceResponsible).toBe("Irmã");
  });

  test("PUT é substituição: campo opcional omitido é APAGADO", async ({ api, clienteId }) => {
    const base = await identidade(api, clienteId);
    await dadosDe(
      await api.put(`/api/v1/clients/${clienteId}/personal-data`, {
        data: { ...base, rg: "MG-12.345.678", email: "teste@exemplo.com" },
      }),
    );
    await dadosDe(
      await api.put(`/api/v1/clients/${clienteId}/personal-data`, { data: { ...base, rg: "MG-99.999.999" } }),
    );

    const dados = await dadosDe<{ rg: string; email: string | null }>(
      await api.get(`/api/v1/clients/${clienteId}/personal-data`),
    );
    expect(dados.rg).toBe("MG-99.999.999");
    // Isto NÃO é defeito: PUT substitui a aba inteira, aqui como em PUT /clients/{id}.
    // O teste existe porque o comportamento surpreende — quem manda só o RG numa
    // tela de edição apaga o e-mail sem perceber. Se um dia a rota virar PATCH,
    // é este teste que precisa mudar junto, de propósito.
    expect(dados.email, "PUT deixou de substituir; se foi intencional, vire PATCH").toBeNull();
  });

  test("campo obrigatório de coluna NOT NULL sobrevive à omissão", async ({ api, clienteId }) => {
    // A contrapartida da regra acima: nationality, isWhatsapp e hasDisability são
    // NOT NULL no banco. Apagá-los como os demais opcionais daria erro de
    // integridade, então o service os preserva — e isso precisa continuar valendo.
    const base = await identidade(api, clienteId);
    await dadosDe(
      await api.put(`/api/v1/clients/${clienteId}/personal-data`, {
        data: { ...base, nationality: "Portuguesa", isWhatsapp: false },
      }),
    );
    const depois = await dadosDe<{ nationality: string; isWhatsapp: boolean }>(
      await api.put(`/api/v1/clients/${clienteId}/personal-data`, { data: base }),
    );
    expect(depois.nationality).toBe("Portuguesa");
    expect(depois.isWhatsapp).toBe(false);
  });

  test("aba pessoal sem campo obrigatório devolve 400 apontando cada um", async ({ api, clienteId }) => {
    const erros = await errosDe(
      await api.put(`/api/v1/clients/${clienteId}/personal-data`, { data: { rg: "MG-1" } }),
      400,
    );
    for (const campo of ["fullName", "birthDate", "cpf", "motherName", "gender", "mobilePhone"]) {
      expect(erros.map((e) => e.field), `faltou apontar ${campo}`).toContain(campo);
    }
  });

  test("dados profissionais gravam e voltam", async ({ api, clienteId }) => {
    await dadosDe(
      await api.put(`/api/v1/clients/${clienteId}/professional-data`, {
        data: { profession: "Costureira", ctps: "1234567", ctpsSeries: "0001-MG", inssPassword: "senha-inss" },
      }),
    );
    const dados = await dadosDe<{ profession: string; ctps: string }>(
      await api.get(`/api/v1/clients/${clienteId}/professional-data`),
    );
    expect(dados.profession).toBe("Costureira");
    expect(dados.ctps).toBe("1234567");
  });

  test("aba profissional sem a senha do INSS é aceita e mantém a gravada", async ({ api, clienteId }) => {
    // Já foi 400. Deixou de ser quando a senha saiu do GET: o formulário não a
    // recebe mais para devolver, então exigi-la na gravação tornaria a aba
    // impossível de salvar. Ausente agora significa "mantém a que está lá" —
    // o que clients.inss.spec.ts afirma pelo valor, e não só pelo status.
    await dadosDe(
      await api.put(`/api/v1/clients/${clienteId}/professional-data`, { data: { profession: "Pedreiro" } }),
    );
  });

  test("cliente inexistente devolve 404 nas duas abas", async ({ api }) => {
    expect((await api.get(`/api/v1/clients/${UUID_INEXISTENTE}/personal-data`)).status()).toBe(404);
    expect((await api.get(`/api/v1/clients/${UUID_INEXISTENTE}/professional-data`)).status()).toBe(404);
  });
});

test.describe("pagamentos do cliente (o que o INSS paga a ele)", () => {
  test("nasce PENDENTE e ignora data de pagamento enviada", async ({ api, clienteId }) => {
    const criado = await dadosDe<Pagamento>(
      await api.post(`/api/v1/clients/${clienteId}/payments`, {
        data: {
          description: "[api-test] Atrasados",
          amount: 1500.5,
          dueDate: "2030-01-10",
          paidDate: "2030-01-05",
        },
      }),
      201,
    );
    expect(criado.status).toBe("Pendente");
    // O DTO de criação não tem paidDate: quem já recebeu confirma com o PATCH.
    expect(criado.paidDate).toBeNull();
  });

  test("PATCH para PAGO sem data assume hoje", async ({ api, clienteId }) => {
    const criado = await dadosDe<Pagamento>(
      await api.post(`/api/v1/clients/${clienteId}/payments`, {
        data: { description: "[api-test] Parcela", amount: 100, dueDate: "2030-02-10" },
      }),
      201,
    );
    const pago = await dadosDe<Pagamento>(
      await api.patch(`/api/v1/clients/${clienteId}/payments/${criado.id}`, { data: { status: "Pago" } }),
    );
    expect(pago.status).toBe("Pago");
    expect(pago.paidDate, "marcado como pago sem data de crédito").toBe(new Date().toISOString().slice(0, 10));
  });

  test("PATCH para PAGO com data respeita a data enviada", async ({ api, clienteId }) => {
    const criado = await dadosDe<Pagamento>(
      await api.post(`/api/v1/clients/${clienteId}/payments`, {
        data: { description: "[api-test] Parcela 2", amount: 200, dueDate: "2030-03-10" },
      }),
      201,
    );
    const pago = await dadosDe<Pagamento>(
      await api.patch(`/api/v1/clients/${clienteId}/payments/${criado.id}`, {
        data: { status: "Pago", paidDate: "2030-03-08" },
      }),
    );
    expect(pago.paidDate).toBe("2030-03-08");
  });

  test("voltar para PENDENTE limpa a data de pagamento", async ({ api, clienteId }) => {
    const criado = await dadosDe<Pagamento>(
      await api.post(`/api/v1/clients/${clienteId}/payments`, {
        data: { description: "[api-test] Parcela 3", amount: 300, dueDate: "2030-04-10" },
      }),
      201,
    );
    await dadosDe(
      await api.patch(`/api/v1/clients/${clienteId}/payments/${criado.id}`, { data: { status: "Pago" } }),
    );
    const revertido = await dadosDe<Pagamento>(
      await api.patch(`/api/v1/clients/${clienteId}/payments/${criado.id}`, { data: { status: "Pendente" } }),
    );
    // Parcela pendente com data de crédito é contradição — o relatório somaria errado.
    expect(revertido.paidDate).toBeNull();
  });

  for (const [rotulo, valor] of [["zero", 0], ["negativo", -10]] as const) {
    test(`valor ${rotulo} devolve 400`, async ({ api, clienteId }) => {
      const erros = await errosDe(
        await api.post(`/api/v1/clients/${clienteId}/payments`, {
          data: { description: "[api-test] inválido", amount: valor, dueDate: "2030-05-10" },
        }),
        400,
      );
      expect(erros.map((e) => e.field)).toContain("amount");
    });
  }

  test("sem descrição ou sem vencimento devolve 400", async ({ api, clienteId }) => {
    const semDescricao = await errosDe(
      await api.post(`/api/v1/clients/${clienteId}/payments`, { data: { amount: 10, dueDate: "2030-05-10" } }),
      400,
    );
    expect(semDescricao.map((e) => e.field)).toContain("description");

    const semVencimento = await errosDe(
      await api.post(`/api/v1/clients/${clienteId}/payments`, { data: { description: "x", amount: 10 } }),
      400,
    );
    expect(semVencimento.map((e) => e.field)).toContain("dueDate");
  });

  test("lista e exclui", async ({ api, clienteId }) => {
    const criado = await dadosDe<Pagamento>(
      await api.post(`/api/v1/clients/${clienteId}/payments`, {
        data: { description: "[api-test] apagar", amount: 50, dueDate: "2030-06-10" },
      }),
      201,
    );
    const { itens } = await listaDe<Pagamento>(await api.get(`/api/v1/clients/${clienteId}/payments`));
    expect(itens.map((p) => p.id)).toContain(criado.id);

    expect((await api.delete(`/api/v1/clients/${clienteId}/payments/${criado.id}`)).status()).toBe(204);
    expect((await api.get(`/api/v1/clients/${clienteId}/payments/${criado.id}`)).status()).toBe(404);
  });
});
