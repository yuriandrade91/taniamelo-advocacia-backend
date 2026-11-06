import { test, expect } from "../src/fixtures.js";
import { dadosDe, listaDe } from "../src/envelope.js";

/**
 * Arquivos do cliente: documentos e simulações de CNIS.
 *
 * Maior superfície de risco técnico da API — multipart, MIME, storage externo —
 * e a única família de rotas que não é JSON puro.
 */

type Arquivo = {
  id: string;
  originalFilename: string;
  documentType?: string;
  mimeType?: string;
  fileSizeBytes?: number;
  downloadUrl?: string;
  isPrincipal?: boolean;
};

const UUID_INEXISTENTE = "11111111-2222-3333-4444-555555555555";

/** PDF mínimo de verdade: precisa ter o cabeçalho %PDF- para o tipo bater com o conteúdo. */
const PDF = Buffer.from(
  "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
    "2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n",
  "utf-8",
);
/** PNG de 1x1 pixel, para o caso de imagem. */
const PNG = Buffer.from(
  "89504e470d0a1a0a0000000d4948445200000001000000010806000000" +
    "1f15c4890000000a49444154789c6300010000050001" +
    "0d0a2db40000000049454e44ae426082",
  "hex",
);

/**
 * Monta o corpo do upload.
 *
 * O contrato pede DUAS partes: `files` (N arquivos) e `metadata` (uma lista JSON
 * com um objeto por arquivo, na mesma ordem). FormData é usado porque a mesma
 * chave se repete quando há vários arquivos — o objeto simples do Playwright não
 * expressa isso.
 */
function uploadDocumentos(arquivos: Array<{ nome: string; tipo: string; bytes: Buffer }>, metadados: unknown[]) {
  const form = new FormData();
  for (const a of arquivos) {
    form.append("files", new Blob([new Uint8Array(a.bytes)], { type: a.tipo }), a.nome);
  }
  form.append(
    "metadata",
    new Blob([JSON.stringify(metadados)], { type: "application/json" }),
    "metadata.json",
  );
  return { multipart: form };
}

function uploadSimulacoes(arquivos: Array<{ nome: string; tipo: string; bytes: Buffer }>, metadados: unknown[]) {
  const form = new FormData();
  for (const a of arquivos) {
    form.append("files", new Blob([new Uint8Array(a.bytes)], { type: a.tipo }), a.nome);
  }
  form.append(
    "metadata",
    new Blob([JSON.stringify(metadados)], { type: "application/json" }),
    "metadata.json",
  );
  return { multipart: form };
}

test.describe("documentos", () => {
  test("sobe, lista, baixa com os mesmos bytes e exclui", async ({ api, clienteId }) => {
    const criados = await dadosDe<Arquivo[]>(
      await api.post(
        `/api/v1/clients/${clienteId}/files/documents`,
        uploadDocumentos([{ nome: "rg.pdf", tipo: "application/pdf", bytes: PDF }], [
          { documentType: "IDENTIFICACAO_SEGURADO", notes: "[api-test]" },
        ]),
      ),
      201,
    );
    expect(criados).toHaveLength(1);
    const arquivo = criados[0];
    expect(arquivo.originalFilename).toBe("rg.pdf");
    expect(arquivo.mimeType).toBe("application/pdf");
    expect(arquivo.fileSizeBytes).toBe(PDF.length);
    expect(arquivo.downloadUrl, "sem downloadUrl a tela precisa montar a URL na mão").toBeTruthy();

    const { itens } = await listaDe<Arquivo>(await api.get(`/api/v1/clients/${clienteId}/files/documents`));
    expect(itens.map((f) => f.id)).toContain(arquivo.id);

    const download = await api.get(`/api/v1/clients/${clienteId}/files/${arquivo.id}/download`);
    expect(download.status()).toBe(200);
    // Bytes iguais aos que subiram: é o que prova que o storage não corrompeu nada.
    expect(Buffer.compare(Buffer.from(await download.body()), PDF), "download veio diferente do upload").toBe(0);

    expect((await api.delete(`/api/v1/clients/${clienteId}/files/${arquivo.id}`)).status()).toBe(204);
    expect((await api.get(`/api/v1/clients/${clienteId}/files/documents/${arquivo.id}`)).status()).toBe(404);
  });

  test("sobe vários numa requisição só", async ({ api, clienteId }) => {
    // O cadastro anexa um lote de uma vez; um POST por arquivo não teria transação comum.
    const criados = await dadosDe<Arquivo[]>(
      await api.post(
        `/api/v1/clients/${clienteId}/files/documents`,
        uploadDocumentos(
          [
            { nome: "a.pdf", tipo: "application/pdf", bytes: PDF },
            { nome: "b.png", tipo: "image/png", bytes: PNG },
          ],
          [{ documentType: "IDENTIFICACAO_SEGURADO" }, { documentType: "VINCULO_TEMPO_CONTRIBUICAO" }],
        ),
      ),
      201,
    );
    expect(criados.length).toBe(2);
    for (const f of criados) await api.delete(`/api/v1/clients/${clienteId}/files/${f.id}`);
  });

  test("sem documentType devolve 400", async ({ api, clienteId }) => {
    const resposta = await api.post(
      `/api/v1/clients/${clienteId}/files/documents`,
      uploadDocumentos([{ nome: "x.pdf", tipo: "application/pdf", bytes: PDF }], [{ notes: "sem tipo" }]),
    );
    expect(resposta.status()).toBe(400);
  });

  test("metadados em quantidade diferente dos arquivos devolve 400", async ({ api, clienteId }) => {
    // As duas listas são pareadas por posição: tamanhos diferentes significam
    // que algum arquivo ficaria sem tipo, ou um tipo sem arquivo.
    const resposta = await api.post(
      `/api/v1/clients/${clienteId}/files/documents`,
      uploadDocumentos(
        [{ nome: "a.pdf", tipo: "application/pdf", bytes: PDF }],
        [{ documentType: "IDENTIFICACAO_SEGURADO" }, { documentType: "OUTROS" }],
      ),
    );
    expect(resposta.status()).toBeLessThan(500);
    expect(resposta.status()).toBeGreaterThanOrEqual(400);
  });

  test("MIME não permitido é recusado", async ({ api, clienteId }) => {
    const resposta = await api.post(
      `/api/v1/clients/${clienteId}/files/documents`,
      uploadDocumentos([{ nome: "malicioso.exe", tipo: "application/x-msdownload", bytes: Buffer.from("MZ") }], [
        { documentType: "IDENTIFICACAO_SEGURADO" },
      ]),
    );
    // Aceitar qualquer binário num sistema que guarda documento de terceiro é
    // problema de segurança, não de usabilidade.
    expect([400, 415], `MIME proibido devolveu ${resposta.status()}`).toContain(resposta.status());
  });

  test("PATCH atualiza os metadados sem trocar o arquivo", async ({ api, clienteId }) => {
    const [arquivo] = await dadosDe<Arquivo[]>(
      await api.post(
        `/api/v1/clients/${clienteId}/files/documents`,
        uploadDocumentos([{ nome: "doc.pdf", tipo: "application/pdf", bytes: PDF }], [{ documentType: "IDENTIFICACAO_SEGURADO" }]),
      ),
      201,
    );
    try {
      const atualizado = await dadosDe<Arquivo>(
        await api.patch(`/api/v1/clients/${clienteId}/files/documents/${arquivo.id}`, {
          data: { documentType: "Outros", notes: "[api-test] reclassificado" },
        }),
      );
      expect(atualizado.documentType).toBe("Outros");
      expect(atualizado.originalFilename, "o PATCH trocou o arquivo, não só os metadados").toBe("doc.pdf");
    } finally {
      await api.delete(`/api/v1/clients/${clienteId}/files/${arquivo.id}`);
    }
  });

  test("arquivo de outro cliente devolve 404", async ({ api, clienteId }) => {
    const [arquivo] = await dadosDe<Arquivo[]>(
      await api.post(
        `/api/v1/clients/${clienteId}/files/documents`,
        uploadDocumentos([{ nome: "doc.pdf", tipo: "application/pdf", bytes: PDF }], [{ documentType: "IDENTIFICACAO_SEGURADO" }]),
      ),
      201,
    );
    try {
      expect((await api.get(`/api/v1/clients/${UUID_INEXISTENTE}/files/${arquivo.id}/download`)).status()).toBe(404);
    } finally {
      await api.delete(`/api/v1/clients/${clienteId}/files/${arquivo.id}`);
    }
  });

  test("download de id inexistente devolve 404", async ({ api, clienteId }) => {
    expect((await api.get(`/api/v1/clients/${clienteId}/files/${UUID_INEXISTENTE}/download`)).status()).toBe(404);
  });
});

test.describe("simulações de CNIS", () => {
  test("só aceita PDF", async ({ api, clienteId }) => {
    const png = await api.post(
      `/api/v1/clients/${clienteId}/files/simulations`,
      uploadSimulacoes([{ nome: "sim.png", tipo: "image/png", bytes: PNG }], [{ notes: "[api-test]" }]),
    );
    // Documento aceita imagem (foto de RG); simulação é sempre extrato em PDF.
    expect([400, 415], `simulação em PNG devolveu ${png.status()}`).toContain(png.status());
  });

  test("marcar uma como principal desmarca a anterior", async ({ api, clienteId }) => {
    const [primeira] = await dadosDe<Arquivo[]>(
      await api.post(
        `/api/v1/clients/${clienteId}/files/simulations`,
        uploadSimulacoes([{ nome: "sim1.pdf", tipo: "application/pdf", bytes: PDF }], [{ notes: "[api-test] 1" }]),
      ),
      201,
    );
    const [segunda] = await dadosDe<Arquivo[]>(
      await api.post(
        `/api/v1/clients/${clienteId}/files/simulations`,
        uploadSimulacoes([{ nome: "sim2.pdf", tipo: "application/pdf", bytes: PDF }], [{ notes: "[api-test] 2" }]),
      ),
      201,
    );

    try {
      await dadosDe(await api.patch(`/api/v1/clients/${clienteId}/files/simulations/${primeira.id}/principal`));
      await dadosDe(await api.patch(`/api/v1/clients/${clienteId}/files/simulations/${segunda.id}/principal`));

      const { itens } = await listaDe<Arquivo>(await api.get(`/api/v1/clients/${clienteId}/files/simulations`));
      const principais = itens.filter((f) => f.isPrincipal);
      // Duas simulações principais é estado inválido: a tela não saberia qual usar.
      expect(principais.map((f) => f.id)).toEqual([segunda.id]);
    } finally {
      for (const f of [primeira, segunda]) await api.delete(`/api/v1/clients/${clienteId}/files/${f.id}`);
    }
  });
});
