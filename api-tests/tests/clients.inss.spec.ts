import { test, expect } from "../src/fixtures.js";
import { dadosDe, errosDe } from "../src/envelope.js";
import { novoCliente } from "../src/factories.js";

/**
 * Senha do INSS: só sai por rota própria, e a edição não a apaga.
 *
 * Ela voltava no corpo de `GET /clients/{id}` — quer dizer, em toda abertura de
 * ficha, indo junto para log de acesso, cache de navegador e print de tela de
 * quem só queria conferir um telefone. Estes testes afirmam as duas metades da
 * mudança: que ela saiu de onde não devia estar, e que continua acessível a
 * quem precisa dela.
 */

type Cliente = { clientId: string; fullName: string; inssPassword?: string };
type Profissional = { profession?: string; inssPassword?: string };
type SenhaInss = { inssPassword: string };

const UUID_INEXISTENTE = "11111111-2222-3333-4444-555555555555";
const SENHA = "senha-inss-original";

test.describe("senha do INSS", () => {
  let id = "";

  test.beforeAll(async ({ api }) => {
    const criado = await dadosDe<Cliente>(
      await api.post("/api/v1/clients", { data: novoCliente({ inssPassword: SENHA }) }),
      201,
    );
    id = criado.clientId;
  });

  test.afterAll(async ({ api }) => {
    if (id) await api.delete(`/api/v1/clients/${id}`);
  });

  test("não volta em GET /clients/{id}", async ({ api }) => {
    const cliente = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${id}`));

    // `toBeUndefined` e não `not.toBe(SENHA)`: o campo tem que sumir do corpo,
    // não vir mascarado. Vazio ainda diria ao frontend que ele existe ali.
    expect(cliente.inssPassword, "a senha do INSS voltou na ficha do cliente").toBeUndefined();
  });

  test("não volta na aba de dados profissionais", async ({ api }) => {
    const aba = await dadosDe<Profissional>(await api.get(`/api/v1/clients/${id}/professional-data`));
    expect(aba.inssPassword, "a senha do INSS voltou na aba profissional").toBeUndefined();
  });

  test("sai pela rota própria, com o valor cadastrado", async ({ api }) => {
    const dados = await dadosDe<SenhaInss>(await api.get(`/api/v1/clients/${id}/inss-password`));
    expect(dados.inssPassword).toBe(SENHA);
  });

  test("id inexistente devolve 404, não 200 com senha vazia", async ({ api }) => {
    await errosDe(await api.get(`/api/v1/clients/${UUID_INEXISTENTE}/inss-password`), 404);
  });

  test("PUT sem o campo mantém a senha gravada", async ({ api }) => {
    // O ponto mais frágil da mudança: o formulário não recebe mais a senha no
    // GET, então ele salva sem ela. Se ausente significasse "apaga", editar o
    // telefone de um cliente destruiria o acesso dele ao INSS — em silêncio.
    const antes = await dadosDe<Cliente>(await api.get(`/api/v1/clients/${id}`));
    const semSenha = { ...antes, fullName: "[api-test] Editado sem a senha" };
    delete (semSenha as Record<string, unknown>).inssPassword;

    await dadosDe<Cliente>(await api.put(`/api/v1/clients/${id}`, { data: semSenha }));

    const depois = await dadosDe<SenhaInss>(await api.get(`/api/v1/clients/${id}/inss-password`));
    expect(depois.inssPassword, "editar o cliente sem enviar a senha apagou a senha").toBe(SENHA);
  });

  test("PUT da aba profissional sem o campo mantém a senha", async ({ api }) => {
    await dadosDe<Profissional>(
      await api.put(`/api/v1/clients/${id}/professional-data`, {
        data: { profession: "Costureira", ctps: "1234567", ctpsSeries: "0001-MG" },
      }),
    );

    const depois = await dadosDe<SenhaInss>(await api.get(`/api/v1/clients/${id}/inss-password`));
    expect(depois.inssPassword).toBe(SENHA);
  });

  test("PUT com o campo preenchido troca a senha", async ({ api }) => {
    // O par do teste acima: se "ausente mantém" virasse "sempre mantém", a senha
    // ficaria impossível de trocar e os dois testes de manutenção seguiriam verdes.
    const nova = "senha-inss-trocada";
    await dadosDe<Profissional>(
      await api.put(`/api/v1/clients/${id}/professional-data`, {
        data: { profession: "Costureira", ctps: "1234567", ctpsSeries: "0001-MG", inssPassword: nova },
      }),
    );

    const depois = await dadosDe<SenhaInss>(await api.get(`/api/v1/clients/${id}/inss-password`));
    expect(depois.inssPassword).toBe(nova);
  });
});
