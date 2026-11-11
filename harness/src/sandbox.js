// Local sandbox server control: start/stop Paper with a Java 25 runtime, console commands, log tail
// and wait, deploying the plugin jar, and writing the test settings.
//
// The sandbox folder is GOD_SANDBOX_DIR (default: <repo>/../minecraft/srv1). The harness never
// writes eula.txt; it refuses to start a server whose eula.txt does not already say eula=true.

import { execFile, spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { EventEmitter } from 'node:events';
import { existsSync } from 'node:fs';
import { copyFile, mkdir, open, readdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { HarnessError, compileRegex, sleep } from './util.js';

const execFileP = promisify(execFile);

export const HARNESS_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const REPO_ROOT = path.resolve(HARNESS_DIR, '..');

export const SANDBOX = {
  get dir() {
    return path.resolve(process.env.GOD_SANDBOX_DIR || path.join(REPO_ROOT, '..', 'minecraft', 'srv1'));
  },
  get jar() {
    return process.env.GOD_SERVER_JAR || 'paper.jar';
  },
  get xmx() {
    return process.env.GOD_SERVER_XMX || '4G';
  },
  get pluginGlob() {
    return /^gptgodmc-.*\.jar$/i;
  },
  get buildLibs() {
    return path.join(REPO_ROOT, 'build', 'libs');
  },
};

export const sandboxFile = (...p) => path.join(SANDBOX.dir, ...p);
export const bridgeFilePath = () => sandboxFile('plugins', 'gptgodmc', 'bridge.json');

// ---------- Java ----------

let javaCache = null;

async function javaMajor(exe) {
  try {
    const { stderr, stdout } = await execFileP(exe, ['-version'], { timeout: 15_000, windowsHide: true });
    const m = /version "(\d+)(?:\.(\d+))?/.exec(stderr + stdout);
    if (!m) return 0;
    const major = Number(m[1]);
    return major === 1 ? Number(m[2]) : major;
  } catch {
    return 0;
  }
}

/** Newest Java >= minMajor: $GOD_JAVA, then JDKs under Program Files and ~/.gradle/jdks, then PATH. */
export async function findJava(minMajor = 25) {
  if (javaCache && javaCache.major >= minMajor) return javaCache;
  const exe = process.platform === 'win32' ? 'java.exe' : 'java';
  const candidates = [];
  if (process.env.GOD_JAVA) candidates.push(process.env.GOD_JAVA);
  const roots = process.platform === 'win32'
    ? ['Java', 'Eclipse Adoptium', 'Microsoft', 'Zulu', 'Amazon Corretto', 'BellSoft'].map((r) => path.join(process.env.ProgramFiles || 'C:\\Program Files', r))
    : ['/usr/lib/jvm'];
  roots.push(path.join(os.homedir(), '.gradle', 'jdks'));
  for (const root of roots) {
    let dirs = [];
    try {
      dirs = await readdir(root);
    } catch {
      continue;
    }
    for (const d of dirs.sort().reverse()) {
      const p = path.join(root, d, 'bin', exe);
      if (existsSync(p)) candidates.push(p);
    }
  }
  if (process.env.JAVA_HOME) candidates.push(path.join(process.env.JAVA_HOME, 'bin', exe));
  candidates.push('java');
  const seen = [];
  let best = null;
  for (const c of candidates) {
    const major = await javaMajor(c);
    seen.push(`${c} (${major || '?'})`);
    if (major >= minMajor && (!best || major > best.major)) best = { exe: c, major };
    if (c === process.env.GOD_JAVA && major >= minMajor) break; // explicit choice wins
  }
  if (!best) throw new HarnessError(`No Java ${minMajor}+ found (Paper/Minecraft 26.3 need Java 25). Set GOD_JAVA to its java.exe. Checked: ${seen.join(', ')}`);
  const dir = path.dirname(best.exe);
  best.javaw = process.platform === 'win32' && existsSync(path.join(dir, 'javaw.exe')) ? path.join(dir, 'javaw.exe') : best.exe;
  javaCache = best;
  return best;
}

// ---------- small file formats ----------

/** key=value file (server.properties): read into a Map, keeping the raw lines for rewriting. */
export async function readProperties(file) {
  const text = await readFile(file, 'utf8');
  const map = new Map();
  for (const line of text.split(/\r?\n/)) {
    const m = /^([^#!=\s][^=]*)=(.*)$/.exec(line);
    if (m) map.set(m[1].trim(), m[2]);
  }
  return { text, map };
}

/** Sets keys in a properties file in place; returns {key: [old, new]} for keys that changed. */
export async function setProperties(file, values) {
  const { text } = await readProperties(file);
  const lines = text.split(/\r?\n/);
  const changes = {};
  const done = new Set();
  for (let i = 0; i < lines.length; i++) {
    const m = /^([^#!=\s][^=]*)=(.*)$/.exec(lines[i]);
    if (!m) continue;
    const key = m[1].trim();
    if (!(key in values)) continue;
    done.add(key);
    const v = String(values[key]);
    if (m[2] !== v) {
      changes[key] = [m[2], v];
      lines[i] = `${key}=${v}`;
    }
  }
  for (const [k, v] of Object.entries(values)) {
    if (done.has(k)) continue;
    changes[k] = [null, String(v)];
    lines.splice(lines.at(-1) === '' ? lines.length - 1 : lines.length, 0, `${k}=${v}`);
  }
  if (Object.keys(changes).length) await writeFile(file, lines.join(text.includes('\r\n') ? '\r\n' : '\n'));
  return changes;
}

/**
 * Sets `section.key: value` (two levels, two-space indent) in a YAML file without a YAML library,
 * keeping comments. Adds the key at the end of the section, or the section at the end of the file.
 */
export function setYaml2(text, section, key, value) {
  const eol = text.includes('\r\n') ? '\r\n' : '\n';
  const lines = text.split(/\r?\n/);
  const start = lines.findIndex((l) => l.replace(/\s+#.*$/, '').trimEnd() === `${section}:`);
  if (start < 0) {
    return { text: `${text.replace(/\s*$/, eol)}${section}:${eol}  ${key}: ${value}${eol}`, old: undefined };
  }
  let end = lines.length;
  for (let i = start + 1; i < lines.length; i++) {
    if (/^\S/.test(lines[i]) && !/^#/.test(lines[i])) {
      end = i;
      break;
    }
  }
  const re = new RegExp(`^  ${key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}:\\s*(.*?)(\\s+#.*)?$`);
  for (let i = start + 1; i < end; i++) {
    const m = re.exec(lines[i]);
    if (m) {
      const old = m[1];
      lines[i] = `  ${key}: ${value}${m[2] ?? ''}`;
      return { text: lines.join(eol), old };
    }
  }
  let insert = end;
  while (insert > start + 1 && lines[insert - 1].trim() === '') insert--;
  lines.splice(insert, 0, `  ${key}: ${value}`);
  return { text: lines.join(eol), old: undefined };
}

/** Java's UUID.nameUUIDFromBytes("OfflinePlayer:" + name): the UUID an offline-mode server assigns. */
export function offlineUuid(name) {
  const b = createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  b[6] = (b[6] & 0x0f) | 0x30;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = b.toString('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/** Reads one entry of a zip/jar file (stored or deflated) without external tools. */
export async function readZipEntry(file, name) {
  const buf = await readFile(file);
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65_557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new HarnessError(`${file} is not a zip file`);
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) break;
    const method = buf.readUInt16LE(p + 10);
    const csize = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const entry = buf.toString('utf8', p + 46, p + 46 + nameLen);
    if (entry === name) {
      const start = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
      const data = buf.subarray(start, start + csize);
      if (method === 0) return Buffer.from(data);
      if (method === 8) return (await import('node:zlib')).inflateRawSync(data);
      throw new HarnessError(`${name} in ${file} uses unsupported zip method ${method}`);
    }
    p += 46 + nameLen + extraLen + commentLen;
  }
  return null;
}

async function sha256(file) {
  return createHash('sha256').update(await readFile(file)).digest('hex');
}

function portOpen(port, host = '127.0.0.1', timeoutMs = 1500) {
  return new Promise((resolve) => {
    const s = net.connect({ port, host });
    const done = (v) => {
      s.destroy();
      resolve(v);
    };
    s.setTimeout(timeoutMs, () => done(false));
    s.once('connect', () => done(true));
    s.once('error', () => done(false));
  });
}

/** PID listening on a local TCP port (Windows netstat / lsof), or null. */
async function listeningPid(port) {
  try {
    if (process.platform === 'win32') {
      const { stdout } = await execFileP('netstat', ['-ano', '-p', 'tcp'], { windowsHide: true, timeout: 10_000 });
      for (const line of stdout.split(/\r?\n/)) {
        const m = /^\s*TCP\s+\S+:(\d+)\s+\S+\s+LISTENING\s+(\d+)/i.exec(line);
        if (m && Number(m[1]) === port) return Number(m[2]);
      }
      return null;
    }
    const { stdout } = await execFileP('lsof', ['-t', `-iTCP:${port}`, '-sTCP:LISTEN'], { timeout: 10_000 });
    return Number(stdout.trim().split('\n')[0]) || null;
  } catch {
    return null;
  }
}

async function processAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (e) {
    return e.code === 'EPERM';
  }
}

// ---------- the manager ----------

const LOG_RING = 3000;

export class ServerManager extends EventEmitter {
  constructor({ bridge } = {}) {
    super();
    this.bridge = bridge; // BridgeClient, used for console/stop when the harness does not own the process
    this.child = null;
    this.lines = []; // recent stdout lines of an owned server
    this.lineSeq = 0;
    this.lastExit = null;
  }

  get dir() {
    return SANDBOX.dir;
  }

  async properties() {
    const file = sandboxFile('server.properties');
    if (!existsSync(file)) return new Map();
    return (await readProperties(file)).map;
  }

  async port() {
    const p = Number((await this.properties()).get('server-port'));
    return Number.isFinite(p) && p > 0 ? p : 25565;
  }

  /** Address the bots and the camera client connect to. */
  async address() {
    const props = await this.properties();
    const ip = props.get('server-ip');
    return { host: ip && ip !== '0.0.0.0' ? ip : '127.0.0.1', port: await this.port() };
  }

  get owned() {
    return !!(this.child && this.child.exitCode === null && !this.child.killed);
  }

  async bridgeReachable() {
    if (!this.bridge) return false;
    try {
      await this.bridge.request('GET', '/status', { timeoutMs: 2500 });
      return true;
    } catch {
      return false;
    }
  }

  async ping() {
    const { host, port } = await this.address();
    if (!(await portOpen(port, host))) return null;
    try {
      const mcp = await import('minecraft-protocol');
      const ping = mcp.ping ?? mcp.default?.ping;
      const r = await Promise.race([
        new Promise((res, rej) => ping({ host, port, closeTimeout: 5000 }, (e, v) => (e ? rej(e) : res(v)))),
        sleep(6000).then(() => null),
      ]);
      if (!r) return { listening: true };
      const motd = typeof r.description === 'string' ? r.description : r.description?.text;
      return { listening: true, version: r.version?.name, protocol: r.version?.protocol, players: r.players?.online, maxPlayers: r.players?.max, motd };
    } catch (e) {
      return { listening: true, pingError: e.message };
    }
  }

  async pluginJars() {
    try {
      return (await readdir(sandboxFile('plugins'))).filter((f) => SANDBOX.pluginGlob.test(f));
    } catch {
      return [];
    }
  }

  async allPluginJars() {
    try {
      return (await readdir(sandboxFile('plugins'))).filter((f) => f.toLowerCase().endsWith('.jar'));
    } catch {
      return [];
    }
  }

  async status() {
    const dir = this.dir;
    const props = await this.properties();
    const ping = await this.ping();
    const port = await this.port();
    const pid = this.owned ? this.child.pid : ping ? await listeningPid(port) : null;
    const pick = (k) => props.get(k);
    let pluginConfig = null;
    const cfg = sandboxFile('plugins', 'gptgodmc', 'config.yml');
    if (existsSync(cfg)) {
      const t = await readFile(cfg, 'utf8');
      const sec = (s, k) => {
        const i = t.indexOf(`\n${s}:`);
        if (i < 0) return undefined;
        const m = new RegExp(`\\n  ${k}:\\s*([^\\n#]*)`).exec(t.slice(i, i + 4000));
        return m?.[1].trim();
      };
      pluginConfig = { bridgeEnabled: sec('bridge', 'enabled'), bridgeBind: sec('bridge', 'bind'), bridgePort: sec('bridge', 'port'), brainMode: sec('brain', 'mode') };
    }
    return {
      ok: true,
      dir,
      exists: existsSync(dir),
      serverJar: existsSync(sandboxFile(SANDBOX.jar)) ? SANDBOX.jar : null,
      eulaAccepted: await this.eulaAccepted(),
      running: !!ping,
      owned: this.owned,
      pid: pid ?? undefined,
      ping: ping ?? undefined,
      lastExit: this.lastExit ?? undefined,
      properties: {
        'online-mode': pick('online-mode'),
        'server-ip': pick('server-ip'),
        'server-port': pick('server-port'),
        'white-list': pick('white-list'),
        'level-name': pick('level-name'),
        'spawn-protection': pick('spawn-protection'),
      },
      plugins: await this.allPluginJars(),
      pluginConfig,
      bridgeReachable: ping ? await this.bridgeReachable() : false,
    };
  }

  async eulaAccepted() {
    try {
      return /^\s*eula\s*=\s*true\s*$/im.test(await readFile(sandboxFile('eula.txt'), 'utf8'));
    } catch {
      return false;
    }
  }

  // ----- start / stop -----

  async start({ timeoutSec = 180, xmx } = {}) {
    if (!existsSync(sandboxFile(SANDBOX.jar))) throw new HarnessError(`${SANDBOX.jar} not found in ${this.dir} (GOD_SANDBOX_DIR)`);
    if (!(await this.eulaAccepted())) {
      throw new HarnessError(`eula.txt in ${this.dir} does not say eula=true. The harness never accepts the EULA for you; accept it yourself first.`);
    }
    const { host, port } = await this.address();
    if (this.owned) return { ok: true, already: true, pid: this.child.pid };
    if (await portOpen(port, host)) {
      throw new HarnessError(`port ${port} is already in use (pid ${await listeningPid(port)}): a server not started by this harness is running. Stop it first (server_stop).`);
    }
    const java = await findJava(25);
    const args = [`-Xms1G`, `-Xmx${xmx || SANDBOX.xmx}`, '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', SANDBOX.jar, '--nogui'];
    const logStart = await this.logCursor();
    const child = spawn(java.exe, args, { cwd: this.dir, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
    this.child = child;
    this.lines = [];
    this.lastExit = null;
    const onData = (stream) => {
      let buf = '';
      stream.setEncoding('utf8');
      stream.on('data', (d) => {
        buf += d;
        let i;
        while ((i = buf.indexOf('\n')) >= 0) {
          const line = buf.slice(0, i).replace(/\r$/, '').replace(/\x1b\[[0-9;]*m/g, '');
          buf = buf.slice(i + 1);
          this.lines.push({ seq: ++this.lineSeq, line });
          if (this.lines.length > LOG_RING) this.lines.splice(0, this.lines.length - LOG_RING);
          this.emit('line', line);
        }
      });
    };
    onData(child.stdout);
    onData(child.stderr);
    child.on('exit', (code, signal) => {
      this.lastExit = { code, signal, at: new Date().toISOString() };
      this.emit('exit', this.lastExit);
    });
    child.on('error', (e) => this.emit('line', `[harness] failed to start java: ${e.message}`));

    const t0 = Date.now();
    const done = await this.waitForLog({ pattern: 'Done \\([0-9.,]+s\\)! For help', since: logStart, timeoutSec, stopOnExit: true });
    const elapsed = Math.round((Date.now() - t0) / 1000);
    if (!done.matched) {
      const tail = (await this.log({ lines: 30, since: logStart })).lines;
      throw new HarnessError(`server did not finish starting within ${timeoutSec}s${this.lastExit ? ` (java exited with ${this.lastExit.code})` : ''}`, { tail });
    }
    const pluginLines = (await this.log({ lines: 400, since: logStart, grep: 'gptgod|GPTGOD|Debug bridge|god-bridge|ERROR|Could not load' })).lines.slice(-25);
    return { ok: true, pid: child.pid, java: `${java.exe} (Java ${java.major})`, startedInSec: elapsed, address: `${host}:${port}`, pluginLog: pluginLines };
  }

  async stop({ timeoutSec = 90, force = false } = {}) {
    const port = await this.port();
    const { host } = await this.address();
    if (!this.owned && !(await portOpen(port, host))) return { ok: true, alreadyStopped: true };
    const t0 = Date.now();
    const ownChild = this.owned ? this.child : null;
    let method;
    if (ownChild) {
      method = 'stdin "stop"';
      this.child.stdin.write('stop\n');
    } else if (await this.bridgeReachable()) {
      method = 'bridge console "stop"';
      await this.bridge.console('stop').catch(() => {}); // the reply may never arrive
    } else if (force) {
      const pid = await listeningPid(port);
      if (!pid) throw new HarnessError(`nothing found listening on port ${port}`);
      method = `taskkill /F /PID ${pid} (NOT a clean stop: unsaved chunks since the last autosave are lost)`;
      if (process.platform === 'win32') await execFileP('taskkill', ['/F', '/PID', String(pid)], { windowsHide: true }).catch(() => {});
      else process.kill(pid, 'SIGKILL');
    } else {
      const pid = await listeningPid(port);
      throw new HarnessError(
        `the server on port ${port} (pid ${pid}) was not started by this harness and its debug bridge is not reachable, so it cannot be stopped cleanly from here. ` +
          'Type "stop" in its console window, or call server_stop with force=true to kill it (loses unsaved chunks).',
      );
    }
    // Wait for the process to exit (owned) or the port to close (not owned).
    const deadline = t0 + timeoutSec * 1000;
    for (;;) {
      if (ownChild ? ownChild.exitCode !== null || ownChild.signalCode !== null : !(await portOpen(port, host))) break;
      if (Date.now() > deadline) {
        if (ownChild) {
          ownChild.kill();
          throw new HarnessError(`server did not stop within ${timeoutSec}s; killed it`);
        }
        throw new HarnessError(`server still listening on ${port} after ${timeoutSec}s`);
      }
      await sleep(500);
    }
    // Paper releases the world lock a moment after the port closes.
    await sleep(1500);
    const exit = this.lastExit;
    this.child = null;
    return { ok: true, method, stoppedInSec: Math.round((Date.now() - t0) / 1000), exit: exit ?? undefined };
  }

  async restart(opts = {}) {
    const stopped = await this.stop(opts);
    const started = await this.start(opts);
    return { ok: true, stopped, started };
  }

  // ----- console -----

  /** Runs a console command: through the bridge when reachable (returns the command feedback), else via stdin. */
  async command(command, { via } = {}) {
    const cmd = String(command).replace(/^\//, '').trim();
    if (!cmd) throw new HarnessError('command is empty');
    if (via !== 'stdin' && (await this.bridgeReachable())) {
      const r = await this.bridge.console(cmd);
      return { ok: r.ok !== false, via: 'bridge', command: cmd, feedback: r.feedback ?? [] };
    }
    if (!this.owned) throw new HarnessError('no console: the server was not started by this harness and its debug bridge is not reachable');
    const from = this.lineSeq;
    this.child.stdin.write(cmd + '\n');
    await sleep(1200);
    const output = this.lines.filter((l) => l.seq > from).map((l) => l.line);
    return { ok: true, via: 'stdin', command: cmd, output };
  }

  // ----- log -----

  logFile() {
    return sandboxFile('logs', 'latest.log');
  }

  /** Opaque cursor (byte offset of logs/latest.log + its creation time) to wait for lines after "now". */
  async logCursor() {
    try {
      const st = await stat(this.logFile());
      return `${Math.round(st.birthtimeMs)}:${st.size}`;
    } catch {
      return '0:0';
    }
  }

  async _readFrom(cursor) {
    let st;
    try {
      st = await stat(this.logFile());
    } catch {
      return { text: '', cursor: '0:0' };
    }
    const [birth, off] = String(cursor ?? '0:0').split(':').map(Number);
    // A new latest.log (server restarted) or a shorter file: read it from the start.
    let from = Math.round(st.birthtimeMs) === birth && off <= st.size ? off : 0;
    if (from === st.size) return { text: '', cursor: `${Math.round(st.birthtimeMs)}:${st.size}` };
    const fh = await open(this.logFile(), 'r');
    try {
      const len = st.size - from;
      const buf = Buffer.alloc(len);
      await fh.read(buf, 0, len, from);
      return { text: buf.toString('utf8'), cursor: `${Math.round(st.birthtimeMs)}:${st.size}` };
    } finally {
      await fh.close();
    }
  }

  /** Last `lines` lines of logs/latest.log (optionally only after a cursor, optionally filtered by regex). */
  async log({ lines = 60, grep, since } = {}) {
    let text;
    if (since) {
      text = (await this._readFrom(since)).text;
    } else {
      try {
        const st = await stat(this.logFile());
        const fh = await open(this.logFile(), 'r');
        const len = Math.min(st.size, 512 * 1024);
        const buf = Buffer.alloc(len);
        await fh.read(buf, 0, len, st.size - len);
        await fh.close();
        text = buf.toString('utf8');
      } catch {
        return { ok: true, file: this.logFile(), lines: [], note: 'no logs/latest.log yet' };
      }
    }
    let all = text.split(/\r?\n/).filter(Boolean);
    if (grep) {
      const re = compileRegex(grep);
      all = all.filter((l) => re.test(l));
    }
    return { ok: true, file: this.logFile(), cursor: await this.logCursor(), lines: all.slice(-Math.max(1, Math.min(2000, lines))) };
  }

  /** Waits until a new line of logs/latest.log matches `pattern` (regex). since = cursor (default: now). */
  async waitForLog({ pattern, since, timeoutSec = 60, stopOnExit = false, signal } = {}) {
    if (!pattern) throw new HarnessError('pattern is required');
    const re = compileRegex(pattern);
    let cursor = since ?? (await this.logCursor());
    let carry = '';
    const t0 = Date.now();
    const exitedAtStart = this.child?.exitCode;
    for (;;) {
      const r = await this._readFrom(cursor);
      cursor = r.cursor;
      const text = carry + r.text;
      const parts = text.split(/\r?\n/);
      carry = parts.pop() ?? '';
      for (const line of parts) {
        if (re.test(line)) return { ok: true, matched: true, line, cursor, waitedMs: Date.now() - t0 };
      }
      if (signal?.aborted) return { ok: false, matched: false, cancelled: true, cursor };
      if (stopOnExit && this.child && this.child.exitCode !== null && exitedAtStart === null) {
        return { ok: false, matched: false, exited: this.lastExit, cursor };
      }
      if (Date.now() - t0 > timeoutSec * 1000) return { ok: false, matched: false, timedOut: true, pattern: String(pattern), cursor };
      await sleep(400);
    }
  }

  // ----- plugin deploy and settings -----

  /** Newest build/libs/gptgodmc-*.jar by modification time. */
  async newestPluginJar() {
    let files;
    try {
      files = (await readdir(SANDBOX.buildLibs)).filter((f) => SANDBOX.pluginGlob.test(f) && !/-(sources|javadoc|thin)\.jar$/i.test(f));
    } catch {
      throw new HarnessError(`no ${SANDBOX.buildLibs} folder: build the plugin first (./gradlew shadowJar)`);
    }
    if (!files.length) throw new HarnessError(`no gptgodmc-*.jar in ${SANDBOX.buildLibs}`);
    const withTime = await Promise.all(files.map(async (f) => ({ f, st: await stat(path.join(SANDBOX.buildLibs, f)) })));
    withTime.sort((a, b) => b.st.mtimeMs - a.st.mtimeMs);
    const { f, st } = withTime[0];
    return { file: path.join(SANDBOX.buildLibs, f), name: f, size: st.size, modified: st.mtime.toISOString() };
  }

  /** Copies the newest plugin jar into plugins/ (removing older gptgodmc jars) and restarts the server if it ran. */
  async deploy({ restart = true, configure = true, configureOptions = {} } = {}) {
    const jar = await this.newestPluginJar();
    const wasRunning = !!(await this.ping());
    let stopped;
    if (wasRunning) stopped = await this.stop();
    const pluginsDir = sandboxFile('plugins');
    await mkdir(pluginsDir, { recursive: true });
    const removed = [];
    for (const f of await this.pluginJars()) {
      if (f !== jar.name) {
        await rm(path.join(pluginsDir, f), { force: true });
        removed.push(f);
      }
    }
    await copyFile(jar.file, path.join(pluginsDir, jar.name));
    const deployed = { ...jar, sha256: await sha256(jar.file), to: path.join(pluginsDir, jar.name) };
    let configured;
    if (configure) configured = await this.configure({ ...configureOptions, jar: jar.file });
    let started;
    if (restart) started = await this.start();
    return { ok: true, deployed, removed, wasRunning, stopped, configured, started };
  }

  /**
   * Test settings. server.properties: online-mode=false (bots and the camera use offline accounts)
   * and server-ip=127.0.0.1 (unreachable from the network). bukkit.yml: connection-throttle -1.
   * plugins/gptgodmc/config.yml: bridge on 127.0.0.1, optional brain.mode. The first change backs
   * the original server.properties up to server.properties.harness-backup.
   */
  async configure({ brainMode, bridgePort = 8765, spawnProtection, jar, extraProperties = {} } = {}) {
    const changes = { 'server.properties': {}, 'bukkit.yml': {}, 'plugins/gptgodmc/config.yml': {} };
    const propsFile = sandboxFile('server.properties');
    if (!existsSync(propsFile)) throw new HarnessError(`no server.properties in ${this.dir}: start the server once first`);
    const backup = propsFile + '.harness-backup';
    if (!existsSync(backup)) {
      await copyFile(propsFile, backup);
      changes.backup = backup;
    }
    const props = { 'online-mode': 'false', 'server-ip': '127.0.0.1', ...extraProperties };
    if (spawnProtection !== undefined) props['spawn-protection'] = String(spawnProtection);
    changes['server.properties'] = await setProperties(propsFile, props);

    const bukkit = sandboxFile('bukkit.yml');
    if (existsSync(bukkit)) {
      const r = setYaml2(await readFile(bukkit, 'utf8'), 'settings', 'connection-throttle', '-1');
      if (String(r.old) !== '-1') {
        await writeFile(bukkit, r.text);
        changes['bukkit.yml']['settings.connection-throttle'] = [r.old ?? null, '-1'];
      }
    }

    const cfgDir = sandboxFile('plugins', 'gptgodmc');
    const cfg = path.join(cfgDir, 'config.yml');
    if (!existsSync(cfg)) {
      // First deploy: write the plugin's default config.yml (from the jar) so the bridge is on at first start.
      const source = jar ?? (await this.newestPluginJar()).file;
      await mkdir(cfgDir, { recursive: true });
      const defaults = await readZipEntry(source, 'config.yml');
      if (!defaults) throw new HarnessError(`no config.yml inside ${source}`);
      await writeFile(cfg, defaults);
      changes['plugins/gptgodmc/config.yml'].created = `default config.yml extracted from ${path.basename(source)}`;
    }
    let text = await readFile(cfg, 'utf8');
    const set = (section, key, value) => {
      const r = setYaml2(text, section, key, value);
      if (String(r.old) !== String(value)) changes['plugins/gptgodmc/config.yml'][`${section}.${key}`] = [r.old ?? null, value];
      text = r.text;
    };
    set('bridge', 'enabled', 'true');
    set('bridge', 'bind', '127.0.0.1');
    set('bridge', 'port', String(bridgePort));
    if (brainMode) set('brain', 'mode', brainMode);
    await writeFile(cfg, text);

    const running = !!(await this.ping());
    const changed = Object.keys(changes['server.properties']).length || Object.keys(changes['bukkit.yml']).length || Object.keys(changes['plugins/gptgodmc/config.yml']).length;
    return {
      ok: true,
      changes,
      note: running && changed ? 'the server is running: restart it (server_restart) for these settings to apply' : undefined,
    };
  }

  /**
   * Repeatable shots: fixed time and weather, cycles stopped (26.3 gamerules advance_time /
   * advance_weather), and the locator bar hidden from the HUD (gamerule locator_bar).
   */
  async worldSetup({ time = 'noon', weather = 'clear', freeze = true, peaceful = false, locatorBar = false } = {}) {
    const cmds = [
      `time set ${time}`,
      `weather ${weather}`,
      `gamerule advance_time ${!freeze}`,
      `gamerule advance_weather ${!freeze}`,
      `gamerule locator_bar ${!!locatorBar}`,
    ];
    if (peaceful) cmds.push('difficulty peaceful');
    const results = [];
    for (const c of cmds) {
      results.push(await this.command(c).catch((e) => ({ ok: false, command: c, error: e.message })));
    }
    return { ok: results.every((r) => r.ok !== false), results };
  }

  /** Lets an offline-mode player through the whitelist (offline UUID in whitelist.json + "whitelist reload"). */
  async allowPlayer(name) {
    const props = await this.properties();
    if (props.get('white-list') !== 'true') return { ok: true, whitelist: 'off' };
    const file = sandboxFile('whitelist.json');
    let list = [];
    try {
      list = JSON.parse(await readFile(file, 'utf8'));
    } catch {
      /* missing or empty */
    }
    const uuid = offlineUuid(name);
    if (list.some((e) => e.uuid === uuid)) return { ok: true, whitelist: 'already listed', uuid };
    list.push({ uuid, name });
    await writeFile(file, JSON.stringify(list, null, 2));
    let reload;
    if (await this.ping()) reload = await this.command('whitelist reload').catch((e) => ({ ok: false, error: e.message }));
    return { ok: true, whitelist: 'added', uuid, reload };
  }
}

export { processAlive };
