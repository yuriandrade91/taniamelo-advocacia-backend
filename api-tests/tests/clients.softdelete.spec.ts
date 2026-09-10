import { test, expect } from "../src/fixtures.js";
import { dadosDe, listaDe } from "../src/envelope.js";
import { novoCliente, novoEndereco } from "../src/factories.js";

/**
 * DELETE e PATCH /restore.
 *
 * A exclusão de cliente era física, com ON DELETE CASCADE em cinco tabelas: um
 * clique apagava endereços, entrevistas, arquivos, pagamentos e todo o histórico
 * de situação. Estes testes existem para que ela nunca volte a ser.
 */

type Cliente = { id: string; fullName: string; cpf: string };

test("exclusão tira das consultas mas o registro volta inteiro no restore", async ({ api }) => {
  const cliente = await dadosDe<Cliente>(await api.post("/api/v1/clients", { data: novoCliente() }), 201);

  // Sub-recursos, para provar que a exclusão não os leva junto.
  await dadosDe(await api.post(`/api/v1/clients/${cliente.id}/addresses`, { data: novoEndereco() }), 201);
  await dadosDe(
    await api.post(`/api/v1/clients/${cliente.id}/interviews`, { data: { content: "[api-test] entrevista" } }),
    201,
  );
  await dadosDe(await api.patch(`/api/v1/clients/${cliente.id}`, { data: { situation: "Análise documental" } }));

  const excluido = await api.delete(`/api/v1/clients/${cliente.id}`);
  expect(excluido.status()).toBe(200);

  expect((await api.get(`/api/v1/clients/${cliente.id}`)).status()).toBe(404);
  const { itens } = await listaDe<Cliente>(
    await api.get(`/api/v1/clients?searchTerm=${encodeURIComponent(cliente.fullName)}`),
  );
  expect(itens.map((c) => c.id), "cliente excluído apareceu na listagem").not.toContain(cliente.id);

  // Sub-recurso de cliente excluído também some — para a API ele não existe.
  expect((await api.get(`/api/v1/clients/${cliente.id}/addresses`)).status()).toBe(404);

  const restaurado = await api.patch(`/api/v1/clients/${cliente.id}/restore`);
  expect(restaurado.status()).toBe(200);

  const voltou = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${cliente.id}`));
  expect(voltou.id).toBe(cliente.id);

  // O ponto do teste: a ficha voltou INTEIRA. Se a exclusão fosse física, estas
  // três listas voltariam vazias e ninguém perceberia até precisar delas.
  const enderecos = await dadosDe<unknown[]>(await api.get(`/api/v1/clients/${cliente.id}/addresses`));
  expect(enderecos.length, "endereço não sobreviveu à exclusão").toBe(1);

  const entrevistas = await dadosDe<unknown[]>(await api.get(`/api/v1/clients/${cliente.id}/interviews`));
  expect(entrevistas.length, "entrevista não sobreviveu à exclusão").toBe(1);

  const historico = await dadosDe<unknown[]>(await api.get(`/api/v1/clients/${cliente.id}/situation-history`));
  expect(historico.length, "histórico de situação não sobreviveu à exclusão").toBeGreaterThan(0);

  await api.delete(`/api/v1/clients/${cliente.id}`);
});

test("o CPF de um cliente excluído fica livre para novo cadastro", async ({ api }) => {
  // Os UNIQUE de coluna viraram índice único parcial (WHERE deleted_at IS NULL)
  // na V12. Se a migration não tiver rodado, isto devolve 409 vindo do banco
  // enquanto a checagem da aplicação diz que o CPF está livre.
  const primeiro = await dadosDe<Cliente>(await api.post("/api/v1/clients", { data: novoCliente() }), 201);
  const cpf = (await dadosDe<Cliente>(await api.get(`/api/v1/clients/${primeiro.id}`))).cpf;

  await api.delete(`/api/v1/clients/${primeiro.id}`);

  const segundo = await api.post("/api/v1/clients", { data: novoCliente({ cpf }) });
  expect(
    segundo.status(),
    "recadastro com o CPF de um excluído falhou — índice único parcial ausente (migration V12)",
  ).toBe(201);

  const novo = await dadosDe<Cliente>(segundo, 201);
  await api.delete(`/api/v1/clients/${novo.id}`);
});

test("restore é idempotente em cliente ativo", async ({ api, clienteId }) => {
  const primeira = await api.patch(`/api/v1/clients/${clienteId}/restore`);
  const segunda = await api.patch(`/api/v1/clients/${clienteId}/restore`);
  // Quem clica duas vezes não deveria ver erro.
  expect(primeira.status()).toBe(200);
  expect(segunda.status()).toBe(200);
  expect((await api.get(`/api/v1/clients/${clienteId}`)).status()).toBe(200);
});

test("restore de id inexistente devolve 404", async ({ api }) => {
  const resposta = await api.patch("/api/v1/clients/11111111-2222-3333-4444-555555555555/restore");
  expect(resposta.status()).toBe(404);
});

test("excluir duas vezes devolve 404 na segunda", async ({ api }) => {
  const cliente = await dadosDe<Cliente>(await api.post("/api/v1/clients", { data: novoCliente() }), 201);
  expect((await api.delete(`/api/v1/clients/${cliente.id}`)).status()).toBe(200);
  expect((await api.delete(`/api/v1/clients/${cliente.id}`)).status()).toBe(404);
  await api.patch(`/api/v1/clients/${cliente.id}/restore`);
  await api.delete(`/api/v1/clients/${cliente.id}`);
});

test("editar cliente excluído devolve 404", async ({ api }) => {
  const cliente = await dadosDe<Cliente>(await api.post("/api/v1/clients", { data: novoCliente() }), 201);
  await api.delete(`/api/v1/clients/${cliente.id}`);

  expect((await api.patch(`/api/v1/clients/${cliente.id}`, { data: { situation: "Análise documental" } })).status()).toBe(404);
  expect((await api.put(`/api/v1/clients/${cliente.id}`, { data: novoCliente() })).status()).toBe(404);
});
