#!/usr/bin/env node
// Call a harness tool on the running daemon from a shell:
//   node harness/call.js --list [filter]
//   node harness/call.js <tool> ['{"json":"args"}' | key=value ...]
//   node harness/call.js --start            start the daemon detached (log: harness/out/daemon.log) if it is down
//   node harness/call.js --stop             stop it (it stops the server it started, bots and clients first)
// GOD_HARNESS_PORT (default 8790). With --start, GOD_SANDBOX_DIR chooses the sandbox the daemon works on.

import { spawn } from 'node:child_process';
import { mkdirSync, openSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.GOD_HARNESS_PORT || 8790);
const BASE = `http://127.0.0.1:${PORT}`;

const health = () => fetch(`${BASE}/health`, { signal: AbortSignal.timeout(2000) }).then((r) => r.json(), () => null);

export async function ensureDaemon() {
  let h = await health();
  if (h) return h;
  const outDir = path.join(HERE, 'out');
  mkdirSync(outDir, { recursive: true });
  const logFd = openSync(path.join(outDir, 'daemon.log'), 'a');
  const child = spawn(process.execPath, [path.join(HERE, 'src', 'daemon.js'), '--port', String(PORT)], { detached: true, stdio: ['ignore', logFd, logFd], windowsHide: true, cwd: path.dirname(HERE) });
  child.unref();
  for (let i = 0; i < 40 && !h; i++) {
    await new Promise((r) => setTimeout(r, 250));
    h = await health();
  }
  if (!h) throw new Error(`the daemon did not start; see ${path.join(outDir, 'daemon.log')}`);
  return h;
}

async function main() {
  const [cmd, ...rest] = process.argv.slice(2);
  if (!cmd || cmd === '--help') {
    console.log('usage: node harness/call.js --list [filter] | --start | --stop | <tool> [json | key=value ...]');
    return;
  }
  if (cmd === '--start') return console.log(JSON.stringify(await ensureDaemon()));
  if (cmd === '--stop') {
    const r = await fetch(`${BASE}/api/shutdown`, { method: 'POST' }).then((x) => x.json(), () => ({ ok: true, note: 'not running' }));
    return console.log(JSON.stringify(r));
  }
  if (cmd === '--list') {
    const { tools } = await fetch(`${BASE}/api/tools`).then((r) => r.json());
    for (const t of tools) {
      if (rest[0] && !t.name.includes(rest[0])) continue;
      const args = Object.keys(t.inputSchema.properties ?? {}).join(', ');
      console.log(`${t.name}(${args})\n    ${t.description.slice(0, 160)}`);
    }
    return;
  }
  let args = {};
  if (rest.length === 1 && rest[0].trim().startsWith('{')) args = JSON.parse(rest[0]);
  else {
    for (const kv of rest) {
      const i = kv.indexOf('=');
      if (i < 0) throw new Error(`argument "${kv}" is not key=value`);
      const raw = kv.slice(i + 1);
      let v = raw;
      if (/^(true|false|null|-?\d+(\.\d+)?)$/.test(raw) || /^[[{]/.test(raw)) v = JSON.parse(raw);
      args[kv.slice(0, i)] = v;
    }
  }
  const res = await fetch(`${BASE}/api/${cmd}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(args) }).catch((e) => {
    throw new Error(`the harness daemon is not reachable on ${BASE} (node harness/call.js --start): ${e.cause?.code ?? e.message}`);
  });
  const out = await res.json();
  console.log(typeof out.result === 'string' ? out.result : JSON.stringify(out.result ?? out, null, process.env.GOD_CALL_COMPACT ? 0 : 1));
  if (out.isError) process.exitCode = 1;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error(e.message);
    process.exit(1);
  });
}
