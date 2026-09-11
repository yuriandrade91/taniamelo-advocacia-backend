#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import newman from 'newman';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const manifestPath = path.join(root, 'postman', 'manifest.json');
const reportsRoot = path.resolve(process.env.POSTMAN_REPORT_DIR || path.join(root, 'postman', 'reports'));
const args = new Set(process.argv.slice(2));
const publicOnly = args.has('--public-only');
const allowNonDemo = args.has('--allow-non-demo') || process.env.POSTMAN_ALLOW_NON_DEMO === 'true';
const env = {
  baseUrl: process.env.API_BASE_URL || 'http://98.82.73.175:8080',
  tenantSlug: process.env.API_TENANT || 'demo',
  tenant: process.env.API_TENANT || 'demo',
  login: process.env.API_LOGIN || '',
  loginId: process.env.API_LOGIN || '',
  password: process.env.API_PASSWORD || ''
};

function fail(message) { console.error(`ERRO: ${message}`); process.exitCode = 2; }
if (!fs.existsSync(manifestPath)) fail(`manifesto ausente: ${manifestPath}`);
if (process.exitCode) process.exit();
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const selected = manifest.collections.filter(c => !publicOnly || c.public);
if (!selected.length) fail('nenhuma coleção selecionada');
for (const c of selected) {
  const file = path.resolve(root, 'postman', c.file);
  if (!fs.existsSync(file)) fail(`snapshot ausente para "${c.name}": ${file}`);
}
if (process.exitCode) process.exit();
const mutating = selected.some(c => c.mutable);
if (mutating) {
  if (!env.baseUrl) fail('API_BASE_URL é obrigatório');
  if (!env.tenantSlug) fail('API_TENANT é obrigatório');
  if (env.tenantSlug !== 'demo' && !allowNonDemo) fail(`tenant "${env.tenantSlug}" bloqueado; use demo ou --allow-non-demo conscientemente`);
  if (!env.login) fail('API_LOGIN é obrigatório para coleções autenticadas');
  if (!env.password) fail('API_PASSWORD é obrigatório para coleções autenticadas');
}
if (process.exitCode) process.exit();

fs.mkdirSync(reportsRoot, { recursive: true });
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const runDir = path.join(reportsRoot, stamp);
fs.mkdirSync(runDir, { recursive: true });
const summary = { startedAt: new Date().toISOString(), mode: publicOnly ? 'public-only' : 'all', tenant: env.tenantSlug, reports: runDir, collections: [] };

function slug(s) { return s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, ''); }
function runCollection(c) {
  const name = slug(c.name);
  return new Promise(resolve => {
    newman.run({
      collection: path.resolve(root, 'postman', c.file),
      envVar: Object.entries(env).map(([key, value]) => ({ key, value })),
      reporters: ['cli', 'json', 'junit'],
      reporter: { json: { export: path.join(runDir, `${name}.json`) }, junit: { export: path.join(runDir, `${name}.xml`) } },
      color: 'on',
      bail: false
    }, (error, run) => {
      const failures = run?.run?.failures?.length ?? (error ? 1 : 0);
      const executions = run?.run?.executions?.length ?? 0;
      resolve({ name: c.name, external: Boolean(c.external), public: Boolean(c.public), mutable: Boolean(c.mutable), executions, failures, status: error || failures ? 'failed' : 'passed', error: error?.message });
    });
  });
}

for (const collection of selected) {
  console.log(`\n=== ${collection.external ? '[EXTERNA] ' : ''}${collection.name} ===`);
  summary.collections.push(await runCollection(collection));
}
summary.finishedAt = new Date().toISOString();
summary.failed = summary.collections.filter(c => c.status === 'failed').length;
fs.writeFileSync(path.join(runDir, 'summary.json'), JSON.stringify(summary, null, 2) + '\n');
console.table(summary.collections.map(({ name, external, executions, failures, status }) => ({ collection: name, type: external ? 'external' : 'project', executions, failures, status })));
console.log(`Resumo: ${path.join(runDir, 'summary.json')}`);
if (summary.failed) process.exitCode = 1;
