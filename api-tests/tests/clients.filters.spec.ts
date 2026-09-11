import { test, expect } from "../src/fixtures.js";
import { dadosDe, listaDe, errosDe } from "../src/envelope.js";
import { novoCliente } from "../src/factories.js";

/**
 * GET /clients — paginação e filtros.
 *
 * Os testes criam a própria massa: afirmar sobre o que já está no banco faria a
 * suíte depender de dados que alguém pode apagar amanhã.
 */

type Item = { clientId: string; fullName: string; cpf: string; situation: string; benefit: string; clientType: string };

let idVerificado = "";
let idPotencial = "";
let nomeUnico = "";

test.beforeAll(async ({ api }) => {
  nomeUnico = `[api-test] Zzfiltro ${Date.now().toString(36)}`;
  const verificado = await dadosDe<Item>(
    await api.post("/api/v1/clients", {
      data: novoCliente({
        fullName: `${nomeUnico} Verificado`,
        clientType: "Verificado",
        situation: "Análise documental",
        benefit: "Aposentadoria especial",
      }),
    }),
    201,
  );
  idVerificado = verificado.clientId;

  const potencial = await dadosDe<Item>(
    await api.post("/api/v1/clients", {
      data: novoCliente({
        fullName: `${nomeUnico} Potencial`,
        clientType: "Potencial",
        situation: "Formulário preenchido",
        benefit: "Aposentadoria por idade",
      }),
    }),
    201,
  );
  idPotencial = potencial.clientId;
});

test.afterAll(async ({ api }) => {
  for (const id of [idVerificado, idPotencial]) {
    if (id) await api.delete(`/api/v1/clients/${id}`);
  }
});

test.describe("paginação", () => {
  test("é 1-based e devolve o bloco pagination completo", async ({ api }) => {
    const { itens, pagina } = await listaDe<Item>(await api.get("/api/v1/clients?pageNumber=1&pageSize=5"));

    expect(pagina.pageNumber).toBe(1);
    expect(pagina.pageSize).toBe(5);
    expect(itens.length).toBeLessThanOrEqual(5);
    expect(pagina.totalRecords).toBeGreaterThanOrEqual(itens.length);
    expect(typeof pagina.hasNextPage).toBe("boolean");
    expect(pagina.hasPreviousPage, "página 1 não tem anterior").toBe(false);
  });

  test("página 0 ou negativa cai na primeira, sem erro", async ({ api }) => {
    for (const p of [0, -3]) {
      const { pagina } = await listaDe<Item>(await api.get(`/api/v1/clients?pageNumber=${p}`));
      expect(pagina.pageNumber, `pageNumber=${p} deveria virar a primeira página`).toBe(1);
    }
  });

  test("tamanho de página inválido cai no default", async ({ api }) => {
    const { pagina } = await listaDe<Item>(await api.get("/api/v1/clients?pageSize=0"));
    expect(pagina.pageSize).toBeGreaterThan(0);
  });

  test("pageSize acima do teto é cortado em 100, não recusado", async ({ api }) => {
    // `pageSize=100000` era aceito: uma requisição só bastava para a API montar a
    // base inteira em memória. O teto corta em vez de devolver 400 de propósito —
    // quem pediu demais continua navegando, com a paginação dizendo o total real.
    const { itens, pagina } = await listaDe<Item>(await api.get("/api/v1/clients?pageSize=100000"));

    expect(pagina.pageSize, "pageSize deveria ser cortado no teto de 100").toBe(100);
    expect(itens.length, "veio mais registro do que o teto permite").toBeLessThanOrEqual(100);
  });

  test("pageSize dentro do teto continua respeitado", async ({ api }) => {
    // O par do teste acima: se o teto virasse "sempre 100", quem pede 7 receberia
    // 100 e ninguém perceberia, porque o teste do teto sozinho continuaria passando.
    const { pagina } = await listaDe<Item>(await api.get("/api/v1/clients?pageSize=7"));
    expect(pagina.pageSize).toBe(7);
  });

  test("página muito além do fim devolve lista vazia, não 404", async ({ api }) => {
    const { itens } = await listaDe<Item>(await api.get("/api/v1/clients?pageNumber=99999&pageSize=10"));
    expect(itens).toEqual([]);
  });

  test("páginas não repetem registro", async ({ api }) => {
    const p1 = await listaDe<Item>(await api.get("/api/v1/clients?pageNumber=1&pageSize=3"));
    if (!p1.pagina.hasNextPage) test.skip(true, "base pequena demais para ter segunda página");
    const p2 = await listaDe<Item>(await api.get("/api/v1/clients?pageNumber=2&pageSize=3"));

    const ids1 = new Set(p1.itens.map((c) => c.clientId));
    // Sobreposição entre páginas é o sintoma clássico de ordenação instável.
    for (const item of p2.itens) {
      expect(ids1, "registro repetido entre páginas: a ordenação não é estável").not.toContain(item.clientId);
    }
  });
});

test.describe("filtros", () => {
  test("searchTerm encontra por parte do nome", async ({ api }) => {
    const { itens } = await listaDe<Item>(
      await api.get(`/api/v1/clients?searchTerm=${encodeURIComponent(nomeUnico)}`),
    );
    expect(itens.map((c) => c.clientId).sort()).toEqual([idVerificado, idPotencial].sort());
  });

  test("searchTerm encontra por CPF", async ({ api }) => {
    const cliente = await dadosDe<Item>(await api.get(`/api/v1/clients/${idPotencial}`));
    const { itens } = await listaDe<Item>(
      await api.get(`/api/v1/clients?searchTerm=${encodeURIComponent(cliente.cpf)}`),
    );
    expect(itens.map((c) => c.clientId)).toContain(idPotencial);
  });

  test("searchTerm ignora caixa e acento", async ({ api }) => {
    const { itens } = await listaDe<Item>(
      await api.get(`/api/v1/clients?searchTerm=${encodeURIComponent(nomeUnico.toUpperCase())}`),
    );
    expect(itens.map((c) => c.clientId)).toContain(idPotencial);
  });

  test("searchTerm sem resultado devolve lista vazia", async ({ api }) => {
    const { itens } = await listaDe<Item>(await api.get(`/api/v1/clients?searchTerm=zzz-nada-${Date.now()}`));
    expect(itens).toEqual([]);
  });

  test("clientType filtra pelo label", async ({ api }) => {
    const { itens } = await listaDe<Item>(
      await api.get(`/api/v1/clients?clientType=Verificado&searchTerm=${encodeURIComponent(nomeUnico)}`),
    );
    expect(itens.map((c) => c.clientId)).toEqual([idVerificado]);
  });

  test("clientType filtra pelo nome da constante", async ({ api }) => {
    const { itens } = await listaDe<Item>(
      await api.get(`/api/v1/clients?clientType=VERIFICADO&searchTerm=${encodeURIComponent(nomeUnico)}`),
    );
    expect(itens.map((c) => c.clientId)).toEqual([idVerificado]);
  });

  test("clientType repetido soma os dois tipos", async ({ api }) => {
    const { itens } = await listaDe<Item>(
      await api.get(
        `/api/v1/clients?clientType=Verificado&clientType=Potencial&searchTerm=${encodeURIComponent(nomeUnico)}`,
      ),
    );
    expect(itens.map((c) => c.clientId).sort()).toEqual([idVerificado, idPotencial].sort());
  });

  test("situation filtra", async ({ api }) => {
    const { itens } = await listaDe<Item>(
      await api.get(
        `/api/v1/clients?situation=${encodeURIComponent("Análise documental")}&searchTerm=${encodeURIComponent(nomeUnico)}`,
      ),
    );
    expect(itens.map((c) => c.clientId)).toEqual([idVerificado]);
  });

  test("benefitType filtra", async ({ api }) => {
    const { itens } = await listaDe<Item>(
      await api.get(
        `/api/v1/clients?benefitType=${encodeURIComponent("Aposentadoria especial")}&searchTerm=${encodeURIComponent(nomeUnico)}`,
      ),
    );
    expect(itens.map((c) => c.clientId)).toEqual([idVerificado]);
  });

  test("filtros se combinam com E, não com OU", async ({ api }) => {
    const { itens } = await listaDe<Item>(
      await api.get(
        `/api/v1/clients?clientType=Verificado&situation=${encodeURIComponent("Formulário preenchido")}` +
          `&searchTerm=${encodeURIComponent(nomeUnico)}`,
      ),
    );
    // Nenhum cliente é Verificado E está com formulário preenchido: com OU, viriam dois.
    expect(itens).toEqual([]);
  });

  test("createdFrom/createdTo aceitam data simples e timestamp", async ({ api }) => {
    const hoje = new Date().toISOString().slice(0, 10);
    for (const de of [hoje, `${hoje}T00:00:00Z`]) {
      const resposta = await api.get(
        `/api/v1/clients?createdFrom=${encodeURIComponent(de)}&searchTerm=${encodeURIComponent(nomeUnico)}`,
      );
      const { itens } = await listaDe<Item>(resposta);
      expect(itens.length, `createdFrom=${de} deveria achar os criados hoje`).toBe(2);
    }
  });

  test("janela de datas que exclui tudo devolve vazio", async ({ api }) => {
    const { itens } = await listaDe<Item>(
      await api.get(`/api/v1/clients?createdTo=2000-01-01&searchTerm=${encodeURIComponent(nomeUnico)}`),
    );
    expect(itens).toEqual([]);
  });

  test("data malformada devolve 400 nomeando o campo", async ({ api }) => {
    const erros = await errosDe(await api.get("/api/v1/clients?createdFrom=ontem"), 400);
    expect(erros.map((e) => e.field)).toContain("createdFrom");
  });

  test("enum inválido devolve 400 e não é ignorado em silêncio", async ({ api }) => {
    // Ignorar um filtro inválido devolveria a lista inteira para quem pediu um
    // recorte — pior que um erro, porque parece que funcionou.
    const erros = await errosDe(await api.get("/api/v1/clients?clientType=Inexistente"), 400);
    expect(erros.map((e) => e.field)).toContain("clientType");
  });
});
