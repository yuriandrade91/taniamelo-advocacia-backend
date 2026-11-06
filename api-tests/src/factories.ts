import { MARCA } from "./env.js";

/**
 * Geradores de massa. Tudo que a suíte cria é marcado e único.
 *
 * Único porque a suíte roda contra um banco que já tem dados e vai rodar de
 * novo amanhã: título repetido faria um teste enxergar o resíduo do anterior e
 * passar por engano. Marcado porque resíduo de teste precisa ser reconhecível
 * no meio de dado real, se a limpeza falhar.
 */

let sequencia = 0;
function sufixo(): string {
  sequencia += 1;
  return `${Date.now().toString(36)}-${sequencia}`;
}

/**
 * CPF válido gerado na hora.
 *
 * Precisa ser válido de verdade (o backend valida com @ValidCPF) e precisa ser
 * novo a cada execução, porque CPF é único na tabela — reusar um fixo faria a
 * segunda execução falhar com "CPF já cadastrado", que não é o que se testa.
 */
export function cpfValido(): string {
  const digitos = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10));
  digitos.push(digitoVerificador(digitos, 10));
  digitos.push(digitoVerificador(digitos, 11));
  return digitos.join("");
}

function digitoVerificador(digitos: number[], pesoInicial: number): number {
  const soma = digitos.reduce((acc, d, i) => acc + d * (pesoInicial - i), 0);
  const resto = (soma * 10) % 11;
  return resto === 10 ? 0 : resto;
}

/** CPF sintaticamente bem formado mas com dígito verificador errado. */
export function cpfInvalido(): string {
  const valido = cpfValido();
  const ultimo = Number(valido[10]);
  return valido.slice(0, 10) + ((ultimo + 1) % 10);
}

export function novoCliente(over: Record<string, unknown> = {}) {
  return {
    fullName: `${MARCA} Cliente ${sufixo()}`,
    birthDate: "1970-05-20",
    cpf: cpfValido(),
    motherName: "Maria de Teste",
    mobilePhone: "+5531999990000",
    inssPassword: "senha-inss-123",
    gender: "Feminino",
    benefit: "Aposentadoria por idade",
    situation: "Formulário preenchido",
    clientType: "Potencial",
    ...over,
  };
}

/**
 * Data/hora ISO no futuro.
 *
 * Sempre à frente porque a agenda recusa data no passado sem confirmação
 * explícita (422 PAST_DATE_NOT_CONFIRMED) — usar "agora" faria metade da suíte
 * falhar por um motivo que não é o testado.
 */
export function futuroIso(diasAFrente: number, hora: number, minuto = 0): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + diasAFrente);
  d.setUTCHours(hora, minuto, 0, 0);
  return d.toISOString();
}

export function passadoIso(diasAtras: number, hora = 10): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - diasAtras);
  d.setUTCHours(hora, 0, 0, 0);
  return d.toISOString();
}

/**
 * Janela de horário exclusiva desta chamada.
 *
 * Cada compromisso da suíte ganha um dia diferente, deslocando o offset. Sem
 * isso dois testes independentes conflitariam de verdade na agenda, e o teste
 * de conflito deixaria de significar alguma coisa.
 */
let diaOffset = 30;
export function janelaLivre(duracaoHoras = 1): { startAt: string; endAt: string } {
  diaOffset += 1;
  return {
    startAt: futuroIso(diaOffset, 14, 30),
    endAt: futuroIso(diaOffset, 14 + duracaoHoras, 30),
  };
}

export function novoCompromisso(over: Record<string, unknown> = {}) {
  const janela = janelaLivre();
  return {
    title: `${MARCA} Compromisso ${sufixo()}`,
    type: "Entrevista",
    ...janela,
    modality: "Presencial",
    clientName: "Fulano de Teste",
    ...over,
  };
}

export function novoEndereco(over: Record<string, unknown> = {}) {
  return {
    addressType: "Residencial",
    street: `Rua de Teste ${sufixo()}`,
    addressNumber: "100",
    neighborhood: "Centro",
    city: "Belo Horizonte",
    state: "MG",
    zipCode: "30000-000",
    ...over,
  };
}
