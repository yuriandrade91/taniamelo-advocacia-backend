#!/usr/bin/env node
// Lista as rotas do backend que nenhuma coleção Postman exercita.
//
// A pergunta "todo endpoint tem e2e?" só é respondível comparando as duas
// listas, e à mão ela envelhece no dia seguinte: rota nova entra sem teste e
// ninguém percebe. Sai com código 1 quando falta alguém, para servir de porta
// no CI.
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const backend = path.resolve(root, '..');
const controllers = path.join(backend, 'src/main/java/com/lawfirm/law/firm/controller');

function rotasDoBackend() {
  const rotas = new Set();
  for (const arquivo of fs.readdirSync(controllers).filter(f => f.endsWith('.java'))) {
    const fonte = fs.readFileSync(path.join(controllers, arquivo), 'utf8');
    const raizMatch = fonte.match(/@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]+)"/);
    const raiz = raizMatch ? raizMatch[1] : '';
    const mapeamentos = fonte.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping\s*(\([\s\S]*?\))?/g);
    for (const m of mapeamentos) {
      const args = m[2] || '';
      // @PostMapping("/x"), @PostMapping(path = "/x", consumes = ...) e @GetMapping
      const caminho = args.match(/(?:path|value)\s*=\s*"([^"]*)"/) || args.match(/^\(\s*"([^"]*)"/);
      rotas.add(`${m[1].toUpperCase()} ${normalizar((raiz + (caminho ? caminho[1] : '')) || '/')}`);
    }
  }
  return rotas;
}

// Tira host, query e identificadores para que `/clients/{clientId}` e
// `/clients/{{clientId}}` virem a mesma chave.
function normalizar(bruto) {
  let p = bruto.split('?')[0];
  const i = p.indexOf('/api/');
  if (i >= 0) p = p.slice(i);
  return p
    .replace(/\{\{[^}]*\}\}/g, '{x}')
    .replace(/\{[^/{}]*\}/g, '{x}')
    .replace(/\/[0-9a-fA-F]{8}-[0-9a-fA-F-]{20,}/g, '/{x}')
    .replace(/\/\d+(?=\/|$)/g, '/{x}')
    .replace(/\/$/, '');
}

function rotasDasColecoes() {
  const dir = path.join(root, 'postman', 'collections');
  const cobertas = new Map();
  for (const arquivo of fs.readdirSync(dir).filter(f => f.endsWith('.json'))) {
    const colecao = JSON.parse(fs.readFileSync(path.join(dir, arquivo), 'utf8'));
    const visitar = (itens) => {
      for (const item of itens || []) {
        if (item.item) { visitar(item.item); continue; }
        const req = item.request;
        if (!req) continue;
        const bruto = typeof req.url === 'string' ? req.url : (req.url?.raw ?? '');
        const chave = `${(req.method || 'GET').toUpperCase()} ${normalizar(bruto)}`;
        if (!cobertas.has(chave)) cobertas.set(chave, []);
        cobertas.get(chave).push(arquivo);
      }
    };
    visitar(colecao.item);
  }
  return cobertas;
}

// Importável pelo runner (que recusa rodar com rota descoberta) e executável
// à mão. Devolve o apanhado, sem imprimir nem derrubar o processo: quem chama
// decide o que fazer com a falta.
export function apurarCobertura() {
  const rotas = [...rotasDoBackend()].sort();
  const cobertas = rotasDasColecoes();
  return { rotas, cobertas, descobertas: rotas.filter(r => !cobertas.has(r)) };
}

// Só imprime e falha quando chamado como comando; sob `import`, nada acontece.
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { rotas, descobertas } = apurarCobertura();
  console.log(`rotas no backend: ${rotas.length}`);
  console.log(`exercitadas por alguma coleção: ${rotas.length - descobertas.length}`);
  if (!descobertas.length) {
    console.log('nenhuma rota sem e2e');
  } else {
    console.log(`\nSEM e2e (${descobertas.length}):`);
    for (const r of descobertas) console.log(`  ${r}`);
    process.exitCode = 1;
  }
}
