/**
 * Configuração da suíte, lida do ambiente.
 *
 * Falha alto e cedo quando falta credencial: sem isto o primeiro teste morreria
 * com "401", que manda procurar o defeito na API em vez de no `.env`.
 */
function obrigatoria(nome: string): string {
  const valor = process.env[nome];
  if (!valor) {
    throw new Error(
      `Variável ${nome} não definida. Copie api-tests/.env.example para .env e preencha ` +
        `(ou exporte no shell). Ver api-tests/README.md.`,
    );
  }
  return valor;
}

export const env = {
  baseURL: process.env.API_BASE_URL ?? "http://98.82.73.175:8080",
  tenant: process.env.API_TENANT ?? "demo",
  login: obrigatoria("API_LOGIN"),
  password: obrigatoria("API_PASSWORD"),
  /** Segundo escritório, só para provar isolamento. Ausente = testes de vazamento pulados. */
  tenantSecundario: process.env.API_TENANT_SECUNDARIO,
  /**
   * Usuário STAFF do mesmo escritório, para provar que a autorização por papel
   * recusa de verdade. Ausente = testes de papel pulados com aviso: um usuário
   * a mais no banco é decisão do escritório, não da suíte.
   */
  loginStaff: process.env.API_LOGIN_STAFF,
  passwordStaff: process.env.API_PASSWORD_STAFF,
};

/** Prefixo que marca tudo que a suíte cria, para resíduo ser reconhecível no banco. */
export const MARCA = "[api-test]";
