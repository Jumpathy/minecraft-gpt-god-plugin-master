#!/usr/bin/env node
// Command line for the local sandbox server (same code as the MCP server_* tools).
//   node sandbox.js status
//   node sandbox.js configure [--brain-mode external|live|off] [--spawn-protection 0]
//   node sandbox.js deploy [--no-restart]       newest build/libs/gptgodmc-*.jar -> plugins/, restart
//   node sandbox.js start | run                 start: wait for "Done", then keep running in this console
//                                               (Ctrl+C or "stop" stops it); run = same
//   node sandbox.js stop [--force]              clean stop through the bridge (or kill with --force)
//   node sandbox.js log [n] [--grep re]
//   node sandbox.js cmd "<console command>"     through the bridge
// Sandbox folder: GOD_SANDBOX_DIR (default <repo>/../minecraft/srv1).

import './src/env.js';
import { parseArgs } from 'node:util';
import { createInterface } from 'node:readline';
import { BridgeClient } from './src/bridge.js';
import { ServerManager } from './src/sandbox.js';

const { values: o, positionals } = parseArgs({
  allowPositionals: true,
  options: {
    'brain-mode': { type: 'string' },
    'spawn-protection': { type: 'string' },
    'no-restart': { type: 'boolean' },
    force: { type: 'boolean' },
    grep: { type: 'string' },
  },
});
const [cmd, ...rest] = positionals;
const bridge = new BridgeClient();
const server = new ServerManager({ bridge });
const print = (v) => console.log(JSON.stringify(v, null, 2));

async function foreground() {
  server.on('line', (l) => console.log(l));
  const r = await server.start();
  console.error(`[sandbox] started (pid ${r.pid}) in ${r.startedInSec}s; type console commands, "stop" or Ctrl+C to stop`);
  const rl = createInterface({ input: process.stdin });
  rl.on('line', (line) => server.child?.stdin.write(line + '\n'));
  let stopping = false;
  process.on('SIGINT', async () => {
    if (stopping) return;
    stopping = true;
    await server.stop().catch((e) => console.error(e.message));
    process.exit(0);
  });
  server.on('exit', (e) => {
    console.error(`[sandbox] server exited (${e.code})`);
    process.exit(e.code ?? 0);
  });
}

try {
  switch (cmd) {
    case 'status':
      print(await server.status());
      break;
    case 'configure':
      print(await server.configure({ brainMode: o['brain-mode'], spawnProtection: o['spawn-protection'] }));
      break;
    case 'deploy': {
      // A deployed server started from this CLI keeps running in this console.
      const r = await server.deploy({ restart: false, configureOptions: { brainMode: o['brain-mode'] } });
      print(r);
      if (!o['no-restart']) await foreground();
      break;
    }
    case 'start':
    case 'run':
      await foreground();
      break;
    case 'stop':
      print(await server.stop({ force: !!o.force }));
      break;
    case 'log':
      for (const l of (await server.log({ lines: Number(rest[0] ?? 60), grep: o.grep })).lines) console.log(l);
      break;
    case 'cmd':
      print(await server.command(rest.join(' ')));
      break;
    default:
      console.log('usage: node sandbox.js status | configure | deploy | start | stop [--force] | log [n] [--grep re] | cmd "<command>"');
      process.exitCode = 2;
  }
} catch (e) {
  console.error('error:', e.message);
  if (e.details) console.error(JSON.stringify(e.details, null, 2));
  process.exitCode = 1;
}
