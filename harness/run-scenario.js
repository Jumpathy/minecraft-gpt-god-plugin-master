#!/usr/bin/env node
// Scripted scenarios.
//   node run-scenario.js smoke                 full chain on the local sandbox: starts and stops the server itself
//   node run-scenario.js <name|all> [--bot Name] [--host H] [--port P]   against a running server
// "all" runs bridge, vessel and powers; smoke and prayer (spends API money) only run when named.
// Prints PASS/FAIL/SKIP per step; exits 1 if any step failed.

import './src/env.js'; // optional harness/.env, loaded before anything reads process.env
import { readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';
import { BotManager } from './src/bots.js';
import { BridgeClient } from './src/bridge.js';
import { CameraClient } from './src/client.js';
import { ServerManager } from './src/sandbox.js';
import { createContext, SkipSignal, StepFailed } from './src/scenarios/lib.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SCENARIO_DIR = path.join(HERE, 'src', 'scenarios');
const ORDER = ['smoke', 'bridge', 'vessel', 'powers', 'prayer'];
const IN_ALL = ['bridge', 'vessel', 'powers']; // smoke manages the server itself; prayer costs API money

process.on('unhandledRejection', (e) => console.error('[run-scenario] unhandled rejection:', e));

async function loadScenarios() {
  const files = (await readdir(SCENARIO_DIR)).filter((f) => f.endsWith('.js') && f !== 'lib.js');
  const out = {};
  for (const f of files) {
    const mod = await import(pathToFileURL(path.join(SCENARIO_DIR, f)).href);
    if (typeof mod.run === 'function') out[path.basename(f, '.js')] = mod;
  }
  return out;
}

const { values: opts, positionals } = parseArgs({
  allowPositionals: true,
  options: {
    bot: { type: 'string', default: process.env.BOT_NAME || 'HarnessBot' },
    host: { type: 'string' },
    port: { type: 'string' },
    help: { type: 'boolean', short: 'h' },
  },
});

const scenarios = await loadScenarios();
const names = Object.keys(scenarios).sort((a, b) => (ORDER.indexOf(a) + 1 || 99) - (ORDER.indexOf(b) + 1 || 99));
const wanted = positionals[0];

if (opts.help || !wanted || (wanted !== 'all' && !scenarios[wanted])) {
  if (wanted && wanted !== 'all' && !scenarios[wanted]) console.error(`unknown scenario "${wanted}"\n`);
  console.log('usage: node run-scenario.js <scenario|all> [--bot Name] [--host H] [--port P]\n\nscenarios:');
  for (const n of names) console.log(`  ${n.padEnd(8)} ${scenarios[n].description ?? ''}`);
  process.exit(opts.help ? 0 : 2);
}

const bots = new BotManager();
const bridge = new BridgeClient();
const sandbox = new ServerManager({ bridge });
const camera = new CameraClient({ sandbox, bridge });
const ctx = createContext({
  bridge,
  bots,
  sandbox,
  camera,
  botName: opts.bot,
  host: opts.host,
  port: opts.port ? Number(opts.port) : undefined,
});

const toRun = wanted === 'all' ? names.filter((n) => IN_ALL.includes(n)) : [wanted];
const summary = [];
for (const name of toRun) {
  console.log(`\n== ${name}: ${scenarios[name].description ?? ''}`);
  const before = ctx.results.length;
  let outcome = 'ok';
  try {
    await scenarios[name].run(ctx);
  } catch (e) {
    if (e instanceof SkipSignal) outcome = 'skipped';
    else if (e instanceof StepFailed) outcome = 'aborted';
    else {
      outcome = 'crashed';
      console.log(`  FAIL  scenario crashed — ${e?.stack || e}`);
      ctx.results.push({ status: 'FAIL', name: 'scenario crashed' });
    }
  }
  const mine = ctx.results.slice(before);
  const count = (st) => mine.filter((r) => r.status === st).length;
  const line = `${count('PASS')} passed, ${count('FAIL')} failed, ${count('SKIP')} skipped, ${count('WARN')} warnings`;
  console.log(`== ${name}: ${line}${outcome === 'aborted' ? ' (stopped at first failure)' : ''}`);
  summary.push({ name, failed: count('FAIL') > 0, line, outcome });
}

await bots.disconnectAll();
if (camera.running) await camera.close().catch(() => {});
if (sandbox.owned) await sandbox.stop().catch(() => {}); // never leave a server without its console

if (toRun.length > 1) {
  console.log('\n== summary');
  for (const s of summary) console.log(`  ${s.failed ? 'FAIL' : s.outcome === 'skipped' ? 'SKIP' : 'PASS'}  ${s.name.padEnd(8)} ${s.line}`);
}
process.exitCode = summary.some((s) => s.failed) ? 1 : 0;
// Let stdout flush, then exit even if a socket lingers.
setTimeout(() => process.exit(), 300).unref();
