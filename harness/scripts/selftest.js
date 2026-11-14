#!/usr/bin/env node
// Offline self-test: no Minecraft server needed. Starts a mock debug bridge, exercises the bridge
// client, then drives the MCP server over stdio (initialize, tools/list, a few tools/call).
//   node scripts/selftest.js

import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { mkdir, mkdtemp, readdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { BridgeClient, BridgeError, isPng } from '../src/bridge.js';
import { decodePng } from '../src/png.js';
import { offlineUuid, setYaml2 } from '../src/sandbox.js';

const HARNESS_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const TOKEN = 'selftest-token';
const PNG_1PX = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64');

let failures = 0;
const check = (cond, name, detail = '') => {
  if (!cond) failures++;
  console.log(`  ${cond ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
};

// ---------- mock bridge ----------

const journal = [{ seq: 1, time: new Date().toISOString(), kind: 'info', text: 'mock god awakens' }];
const requests = [];
const addJournal = (kind, text) => journal.push({ seq: journal.length + 1, time: new Date().toISOString(), kind, text });

const mock = createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  let body = '';
  for await (const c of req) body += c;
  const input = body ? JSON.parse(body) : undefined;
  requests.push({ method: req.method, path: url.pathname, query: Object.fromEntries(url.searchParams), body: input });
  const send = (code, obj) => {
    res.writeHead(code, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(obj));
  };
  if (req.headers.authorization !== `Bearer ${TOKEN}`) return send(401, { error: 'bad token' });
  const route = `${req.method} ${url.pathname}`;
  switch (route) {
    case 'GET /status':
      return send(200, {
        awake: true, paused: false, mode: 'live', model: 'mock', effort: 'low', thinking: false, keyConfigured: false,
        persona: { name: 'Mockos', epithet: 'the Untested' },
        vessel: { manifested: false, world: 'world', x: 0, y: 64, z: 0, following: null },
        players: [], usage: { calls: 0, inputTokens: 0, outputTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0, estimatedCostUsd: 0 },
        journalSeq: journal.length,
      });
    case 'GET /journal': {
      const since = Number(url.searchParams.get('since') ?? 0);
      const limit = Number(url.searchParams.get('limit') ?? 50);
      return send(200, { entries: journal.filter((e) => e.seq > since).slice(0, limit), latestSeq: journal.length });
    }
    case 'GET /perception':
      return send(200, { text: 'nothing happens' });
    case 'GET /tools':
      return send(200, { tools: [{ name: 'whisper', description: 'whisper', input_schema: { type: 'object', properties: { player: { type: 'string' }, message: { type: 'string' } }, required: ['player', 'message'] } }] });
    case 'GET /memory':
      return send(200, { chronicle: '', ledger: {}, quests: [], recent: [] });
    case 'POST /wake':
      return send(200, { ok: true, queued: true });
    case 'POST /tool':
      if (input.name === 'whisper') {
        addJournal('tool', `whisper ${JSON.stringify(input.input)}`);
        return send(200, { ok: true, isError: false, result: 'whispered' });
      }
      if (input.name === 'boom') return send(200, { ok: true, isError: true, result: 'the power fizzles' });
      return send(400, { error: `unknown tool ${input.name}` });
    case 'POST /event':
      addJournal('perception', input.text);
      return send(200, { ok: true });
    case 'POST /console':
      return send(200, { ok: true, feedback: [`ran: ${input.command}`] });
    case 'POST /memory/reset':
      return send(200, { ok: true });
    case 'GET /view':
      if (!url.searchParams.get('type')) return send(400, { error: 'type required' });
      res.writeHead(200, {
        'Content-Type': 'image/png',
        // Java URLEncoder style: spaces as '+', literal '+' as %2B
        'X-View-Caption': 'overhead+view+of+Bot1+r%3D16+%2B+legend%3A+red%3Dplayer',
      });
      return res.end(PNG_1PX);
    default:
      return send(404, { error: `no route ${route}` });
  }
});

await new Promise((r) => mock.listen(0, '127.0.0.1', r));
const port = mock.address().port;
const tmp = await mkdtemp(path.join(os.tmpdir(), 'god-harness-selftest-'));
const bridgeFile = path.join(tmp, 'bridge.json');
const viewsDir = path.join(tmp, 'views');
// A fake sandbox: no paper.jar, no eula, and a port nothing listens on, so nothing real is touched.
const sandboxDir = path.join(tmp, 'sandbox');
await mkdir(sandboxDir, { recursive: true });
await writeFile(path.join(sandboxDir, 'server.properties'), 'server-ip=127.0.0.1\r\nserver-port=9\r\nonline-mode=false\r\nwhite-list=false\r\n');

try {
  // ---------- pure helpers ----------
  console.log('== helpers');
  check(offlineUuid('Notch') === 'b50ad385-829d-3141-a216-7e7d7539ba7f', 'offline UUID matches Java nameUUIDFromBytes', offlineUuid('Notch'));
  const y = setYaml2('a: 1\r\nbridge:\r\n  enabled: false # comment\r\n  port: 1\r\nz: 2\r\n', 'bridge', 'enabled', 'true');
  check(y.old === 'false' && y.text.includes('  enabled: true # comment\r\n'), 'setYaml2 replaces a value, keeps comment and CRLF');
  const y2 = setYaml2(y.text, 'bridge', 'bind', '127.0.0.1');
  check(/port: 1\r\n {2}bind: 127\.0\.0\.1\r\nz: 2/.test(y2.text), 'setYaml2 appends a key at the end of its section');
  const onePx = decodePng(PNG_1PX);
  check(onePx.width === 1 && onePx.height === 1, 'decodePng reads a 1x1 PNG');

  // ---------- bridge client ----------
  console.log('== bridge client (mock bridge on port ' + port + ')');
  const client = new BridgeClient({ file: bridgeFile, url: null });
  client.envUrl = null; // ignore any GOD_BRIDGE_URL in the environment

  try {
    await client.status();
    check(false, 'missing bridge.json is reported');
  } catch (e) {
    check(e instanceof BridgeError && e.code === 'NO_CONFIG', 'missing bridge.json is reported', e.message.slice(0, 80));
  }

  await writeFile(bridgeFile, JSON.stringify({ port, token: TOKEN }));
  const st = await client.status();
  check(st.persona?.name === 'Mockos', 'bridge.json picked up lazily; GET /status', `mode=${st.mode}`);
  check(Array.isArray((await client.tools()).tools), 'GET /tools');
  check((await client.journal({ since: 0, limit: 10 })).latestSeq >= 1, 'GET /journal');
  check(typeof (await client.perception({ consume: true })).text === 'string', 'GET /perception?consume=true');
  check(requests.at(-1).query.consume === 'true', 'consume flag sent as "true"');
  check((await client.memory()).quests !== undefined, 'GET /memory');
  check((await client.wake('test directive')).queued === true, 'POST /wake');
  check(requests.at(-1).body.directive === 'test directive', 'wake directive in body');
  check((await client.tool('whisper', { player: 'Bot1', message: 'hi' })).ok === true, 'POST /tool ok');
  check((await client.tool('boom', {})).isError === true, 'POST /tool isError passthrough');
  try {
    await client.tool('nope', {});
    check(false, 'HTTP 400 surfaces as BridgeError');
  } catch (e) {
    check(e.status === 400 && /unknown tool nope/.test(e.message), 'HTTP 400 surfaces as BridgeError', e.message);
  }
  await client.console('/give Bot1 diamond 1');
  check(requests.at(-1).body.command === 'give Bot1 diamond 1', 'console strips leading slash');
  check((await client.event('something happened')).ok, 'POST /event');
  check((await client.resetMemory()).ok, 'POST /memory/reset');
  const view = await client.view({ type: 'overhead', target: 'Bot1', radius: 16, xray: false });
  check(isPng(view.png), 'GET /view returns PNG bytes', `${view.png.length} bytes`);
  check(view.caption === 'overhead view of Bot1 r=16 + legend: red=player', 'X-View-Caption decoded', view.caption);
  check(requests.at(-1).query.xray === 'false' && requests.at(-1).query.radius === '16', 'view query params');

  setTimeout(() => addJournal('speech', 'Kneel, mortal.'), 1200);
  const w = await client.waitForJournal({ kinds: ['speech'], timeoutMs: 5000, pollMs: 200 });
  check(w.matched && w.entry.text === 'Kneel, mortal.', 'waitForJournal sees a new speech entry', `${w.waitedMs}ms`);
  const w2 = await client.waitForJournal({ kinds: ['speech'], timeoutMs: 600, pollMs: 200 });
  check(!w2.matched && w2.timedOut, 'waitForJournal times out cleanly');

  const badToken = new BridgeClient({ url: `http://127.0.0.1:${port}`, token: 'wrong' });
  try {
    await badToken.status();
    check(false, 'wrong token → HTTP 401');
  } catch (e) {
    check(e.code === 'HTTP_401', 'wrong token → HTTP 401', e.message);
  }
  const closedPort = await new Promise((r) => {
    const s = createServer().listen(0, '127.0.0.1', () => {
      const p = s.address().port;
      s.close(() => r(p));
    });
  });
  const dead = new BridgeClient({ url: `http://127.0.0.1:${closedPort}`, token: 'x', timeoutMs: 3000 });
  try {
    await dead.status();
    check(false, 'unreachable bridge is reported');
  } catch (e) {
    check(e.code === 'UNREACHABLE' || e.code === 'TIMEOUT', 'unreachable bridge is reported', e.message);
  }

  // ---------- MCP server over stdio ----------
  console.log('== MCP server over stdio');
  // Hermetic env: no real .env, no remote bridge, bots pointed at a closed local port.
  const env = {
    ...process.env,
    GOD_HARNESS_ENV: path.join(tmp, 'no-such.env'),
    GOD_BRIDGE_FILE: bridgeFile,
    GOD_VIEWS_DIR: viewsDir,
    GOD_MC_HOST: '127.0.0.1',
    GOD_MC_PORT: '9',
    GOD_SANDBOX_DIR: sandboxDir,
    GOD_OUT_DIR: viewsDir,
  };
  delete env.GOD_BRIDGE_URL;
  delete env.GOD_BRIDGE_TOKEN;
  const child = spawn(process.execPath, [path.join(HARNESS_DIR, 'src', 'mcp-server.js'), '--inprocess'], {
    cwd: path.resolve(HARNESS_DIR, '..'),
    env,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  let stderr = '';
  child.stderr.on('data', (d) => (stderr += d));
  const pending = new Map();
  const badLines = [];
  let buf = '';
  child.stdout.on('data', (d) => {
    buf += d;
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      if (!line) continue;
      let msg;
      try {
        msg = JSON.parse(line);
      } catch {
        badLines.push(line);
        continue;
      }
      if (msg.jsonrpc !== '2.0') badLines.push(line);
      if (msg.id !== undefined && pending.has(msg.id)) {
        pending.get(msg.id)(msg);
        pending.delete(msg.id);
      }
    }
  });
  let nextId = 1;
  const rpc = (method, params, timeoutMs = 30_000) =>
    new Promise((resolve, reject) => {
      const id = nextId++;
      const t = setTimeout(() => reject(new Error(`${method} timed out; stderr: ${stderr.slice(-500)}`)), timeoutMs);
      pending.set(id, (m) => {
        clearTimeout(t);
        resolve(m);
      });
      child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
    });
  const call = async (name, args = {}, timeoutMs) => (await rpc('tools/call', { name, arguments: args }, timeoutMs)).result;
  const parsed = (r) => JSON.parse(r.content.find((c) => c.type === 'text').text);

  const init = await rpc('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'selftest', version: '0' } });
  check(init.result?.serverInfo?.name === 'god-harness', 'initialize', `protocol ${init.result?.protocolVersion}`);
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');

  const list = await rpc('tools/list', {});
  const names = list.result.tools.map((t) => t.name).sort();
  const expected = [
    'bot_spawn', 'bot_list', 'bot_disconnect', 'bot_say', 'bot_goto', 'bot_follow', 'bot_stop', 'bot_look_at', 'bot_dig',
    'bot_attack', 'bot_interact', 'bot_status', 'bot_inventory', 'bot_nearby', 'bot_events', 'bot_wait_for', 'god_status',
    'god_journal', 'god_perception', 'god_tools', 'god_memory', 'god_wake', 'god_tool', 'god_inject_event', 'god_console',
    'god_reset_memory', 'god_view', 'bot_teleport', 'server_status', 'server_start', 'server_stop', 'server_restart',
    'server_command', 'server_log', 'server_wait_for_log', 'sandbox_configure', 'server_deploy', 'world_setup',
    'camera_launch', 'camera_aim', 'camera_screenshot', 'camera_status', 'camera_close',
  ];
  const missing = expected.filter((n) => !names.includes(n));
  check(!missing.length, `tools/list has all required tools (${names.length} total)`, missing.length ? `missing ${missing}` : names.join(' '));
  const godTool = list.result.tools.find((t) => t.name === 'god_tool');
  check(godTool.inputSchema?.properties?.input?.type === 'object', 'god_tool.input is an object schema');

  const gs = await call('god_status');
  check(!gs.isError && parsed(gs).persona?.name === 'Mockos', 'god_status via MCP');
  const gt = await call('god_tool', { name: 'whisper', input: { player: 'Bot1', message: 'hello' } });
  check(!gt.isError && parsed(gt).result === 'whispered', 'god_tool whisper via MCP');
  const gb = await call('god_tool', { name: 'boom', input: {} });
  check(gb.isError === true, 'god_tool isError → MCP isError');
  const gu = await call('god_tool', { name: 'nope' });
  check(gu.isError === true && /unknown tool/.test(parsed(gu).error), 'bridge HTTP error → MCP isError', parsed(gu).error);
  const gc = await call('god_console', { command: 'time set night' });
  check(!gc.isError && parsed(gc).feedback?.[0] === 'ran: time set night', 'god_console via MCP');
  const gv = await call('god_view', { type: 'overhead', target: 'Bot1', radius: 16 });
  const img = gv.content?.find((c) => c.type === 'image');
  check(img?.mimeType === 'image/png' && isPng(Buffer.from(img.data, 'base64')), 'god_view returns image content');
  const saved = parsed(gv).saved;
  check(saved && (await readdir(viewsDir)).includes(path.basename(saved)), 'god_view saved the PNG', saved);
  const gw = await call('god_wait_for', { kinds: ['speech'], timeoutSec: 1 });
  check(gw.isError === true && parsed(gw).timedOut === true, 'god_wait_for times out as an error result');

  const bl = await call('bot_list');
  check(!bl.isError && Array.isArray(parsed(bl).bots), 'bot_list via MCP');
  const bs = await call('bot_status', {});
  check(bs.isError === true && /no bots/.test(parsed(bs).error), 'bot_status without bots → structured error', parsed(bs).error);
  const bw = await call('bot_wait_for', { pattern: 'anything', timeoutSec: 0.5 });
  check(bw.isError === true && parsed(bw).timedOut === true, 'bot_wait_for times out cleanly');
  const bad = await call('bot_wait_for', { pattern: '([', timeoutSec: 0.5 });
  check(bad.isError === true && /Invalid regex/.test(parsed(bad).error), 'bad regex → structured error');
  // Spawning against a closed local port (GOD_MC_PORT=9): nothing is listening, so this only exercises the error path.
  const sp = await call('bot_spawn', { username: 'SelfTestBot' }, 60_000);
  check(sp.isError === true && /failed to spawn/.test(parsed(sp).error), 'bot_spawn to a closed port → structured error', parsed(sp).error);
  const ss = await call('server_status');
  check(!ss.isError && parsed(ss).running === false && parsed(ss).eulaAccepted === false && parsed(ss).dir === sandboxDir, 'server_status on a fake sandbox', `dir=${parsed(ss).dir}`);
  const sst = await call('server_start');
  check(sst.isError === true && /paper\.jar not found|eula/.test(parsed(sst).error), 'server_start refuses without paper.jar / eula', parsed(sst).error);
  const sl = await call('server_log', { lines: 5 });
  check(!sl.isError && Array.isArray(parsed(sl).lines), 'server_log without a log file');
  const cs = await call('camera_status');
  check(!cs.isError && parsed(cs).running === false, 'camera_status (not running)');
  const csh = await call('camera_screenshot', { label: 'x' });
  check(csh.isError === true && /not online/.test(parsed(csh).error), 'camera_screenshot without a client → structured error', parsed(csh).error);
  const cl = await call('camera_launch', {}, 60_000);
  check(cl.isError === true && /not running/.test(parsed(cl).error), 'camera_launch refuses when the server is down', parsed(cl).error);

  const alive = await rpc('ping', {});
  check(alive.result !== undefined, 'server still alive after failures');
  check(!badLines.length, 'stdout carried only JSON-RPC', badLines.length ? badLines[0].slice(0, 120) : '');

  child.stdin.end();
  const exited = await new Promise((r) => {
    const t = setTimeout(() => r(false), 8000);
    child.on('exit', () => {
      clearTimeout(t);
      r(true);
    });
  });
  check(exited, 'server exits when stdin closes');
  if (!exited) child.kill();
} finally {
  mock.close();
  await rm(tmp, { recursive: true, force: true });
}

console.log(failures ? `\n${failures} check(s) FAILED` : '\nall checks passed');
process.exit(failures ? 1 : 0);
