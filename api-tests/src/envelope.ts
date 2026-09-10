import { expect, type APIResponse } from "@playwright/test";

/**
 * Toda resposta da API vem no mesmo envelope:
 * `{ success, data, pagination, errors }`.
 *
 * Os helpers abaixo existem para que a asserção seja sobre o envelope inteiro, e
 * não só sobre o status. Um 200 com `success: false` já aconteceu em API que
 * ninguém testou assim.
 */

export type Pagination = {
  pageNumber: number;
  pageSize: number;
  totalRecords: number;
  totalPages: number;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
};

export type ApiError = { field?: string; message?: string; code?: string };

export type Envelope<T> = {
  success: boolean;
  data: T;
  pagination: Pagination | null;
  errors: ApiError[] | null;
};

/** Sucesso: status esperado, `success: true`, `errors` vazio. Devolve o `data`. */
export async function dadosDe<T>(resposta: APIResponse, statusEsperado = 200): Promise<T> {
  const corpo = (await corpoOuTexto(resposta)) as Envelope<T>;
  expect(
    resposta.status(),
    `esperado ${statusEsperado}, veio ${resposta.status()}: ${JSON.stringify(corpo)}`,
  ).toBe(statusEsperado);
  expect(corpo.success, `success deveria ser true: ${JSON.stringify(corpo)}`).toBe(true);
  expect(corpo.errors ?? []).toEqual([]);
  return corpo.data;
}

/** Lista paginada: devolve `data` e `pagination` juntos, porque a asserção quase sempre usa os dois. */
export async function listaDe<T>(
  resposta: APIResponse,
  statusEsperado = 200,
): Promise<{ itens: T[]; pagina: Pagination }> {
  const itens = await dadosDe<T[]>(resposta, statusEsperado);
  expect(Array.isArray(itens), "data deveria ser uma lista").toBe(true);
  const corpo = (await corpoOuTexto(resposta)) as Envelope<T[]>;
  expect(corpo.pagination, "resposta paginada sem bloco pagination").not.toBeNull();
  return { itens, pagina: corpo.pagination! };
}

/**
 * Erro: status esperado e envelope de erro preenchido. Devolve os erros para
 * quem quiser afirmar `code` ou `field`.
 *
 * Afirmar só o status não basta: 400 com `errors: []` deixa quem consome sem
 * saber o que corrigir, e é um defeito de contrato tão real quanto o status errado.
 */
export async function errosDe(resposta: APIResponse, statusEsperado: number): Promise<ApiError[]> {
  const corpo = (await corpoOuTexto(resposta)) as Envelope<unknown>;
  expect(
    resposta.status(),
    `esperado ${statusEsperado}, veio ${resposta.status()}: ${JSON.stringify(corpo)}`,
  ).toBe(statusEsperado);
  expect(corpo.success).toBe(false);
  expect(corpo.errors ?? [], "erro sem detalhe no envelope").not.toEqual([]);
  return corpo.errors!;
}

/** Confere que existe um erro com o código dado, dizendo quais vieram quando não existe. */
export function temCodigo(erros: ApiError[], codigo: string) {
  expect(
    erros.map((e) => e.code),
    `esperado o código ${codigo}`,
  ).toContain(codigo);
}

/** Confere que existe erro apontando um campo específico. */
export function temCampo(erros: ApiError[], campo: string) {
  expect(
    erros.map((e) => e.field),
    `esperado erro no campo ${campo}`,
  ).toContain(campo);
}

/**
 * Corpo como JSON; se não for JSON, falha citando o texto.
 *
 * É o caso de 401 devolvido como HTML pelo Spring em vez do envelope — um
 * defeito real que já aconteceu neste projeto, e que um `.json()` cru
 * esconderia atrás de "Unexpected token <".
 */
async function corpoOuTexto(resposta: APIResponse): Promise<unknown> {
  const texto = await resposta.text();
  try {
    return JSON.parse(texto);
  } catch {
    throw new Error(
      `Resposta não é JSON (status ${resposta.status()}). A API deve responder sempre no ` +
        `envelope padrão, inclusive em erro. Corpo recebido:\n${texto.slice(0, 400)}`,
    );
  }
}
