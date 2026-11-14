// Real Minecraft clients as simulated players.
//
// Each player is the installed Minecraft 26.3 client started with Fabric Loader, Fabric API and the god's client
// mod (so it sees what a mod player sees: scenes, subtitles, omens, relics), under an offline name, with its own
// game directory (harness/client/players/<name>/game). It is driven through the mod's "puppet" port: a
// localhost-only HTTP server inside the client that exists only when the JVM property gptgod.puppet.port is set
// (see client-mod/.../puppet/). The puppet moves the player, chats, runs commands, clicks and takes framebuffer
// screenshots in-process: no keystroke or mouse input is ever sent and the window is never focused.
//
// Mods come from harness/client/mods/*.jar (the puppet build of the client mod goes there; Fabric API is copied
// from %APPDATA%/.minecraft/mods when missing). The Fabric profile is the newest
// %APPDATA%/.minecraft/versions/fabric-loader-*-<version> (or GOD_FABRIC_PROFILE).

import { randomBytes } from 'node:crypto';
import { existsSync } from 'node:fs';
import { copyFile, mkdir, readFile, readdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { CLIENT, CameraClient } from './client.js';
import { HarnessError, compileRegex, sleep } from './util.js';

export const MODS_DIR = () => process.env.GOD_PLAYER_MODS || path.join(CLIENT.dir, 'mods');

function mavenPath(name) {
  // group:artifact:version[:classifier]
  const [group, artifact, version, classifier] = name.split(':');
  const file = `${artifact}-${version}${classifier ? `-${classifier}` : ''}.jar`;
  return path.join(...group.split('.'), artifact, version, file);
}

export class PlayerClient extends CameraClient {
  constructor({ sandbox, bridge, name, port, xmx = '2G' }) {
    super({ sandbox, bridge });
    this._name = name;
    this._xmx = xmx;
    this.port = port;
    this.token = randomBytes(12).toString('hex');
    this.puppetReady = false;
  }

  get name() {
    return this._name;
  }

  get baseDir() {
    return path.join(CLIENT.dir, 'players', this._name);
  }

  get xmx() {
    return this._xmx;
  }

  async extraLaunch({ mc, version }) {
    const versions = path.join(mc, 'versions');
    let profile = process.env.GOD_FABRIC_PROFILE;
    if (!profile) {
      const found = (await readdir(versions)).filter((d) => d.startsWith('fabric-loader-') && d.endsWith(`-${version}`)).sort();
      profile = found.pop();
    }
    const pjson = profile && path.join(versions, profile, `${profile}.json`);
    if (!pjson || !existsSync(pjson)) {
      throw new HarnessError(`no Fabric Loader profile for ${version} in ${versions}: install Fabric for ${version} with the official Fabric installer (or set GOD_FABRIC_PROFILE)`);
    }
    const p = JSON.parse(await readFile(pjson, 'utf8'));
    const cp = [];
    const missing = [];
    for (const lib of p.libraries ?? []) {
      const file = path.join(mc, 'libraries', mavenPath(lib.name));
      if (existsSync(file)) cp.push(file);
      else missing.push(lib.name);
    }
    if (missing.length) throw new HarnessError(`Fabric libraries missing (start the ${profile} profile once from the launcher): ${missing.slice(0, 4).join(', ')}`);

    // mods: exactly what harness/client/mods holds
    const modsSrc = MODS_DIR();
    await mkdir(modsSrc, { recursive: true });
    let jars = (await readdir(modsSrc)).filter((f) => f.endsWith('.jar'));
    if (!jars.some((f) => f.startsWith('fabric-api'))) {
      const userMods = path.join(mc, 'mods');
      const api = existsSync(userMods) ? (await readdir(userMods)).filter((f) => f.startsWith('fabric-api') && f.endsWith('.jar')).sort().pop() : null;
      if (!api) throw new HarnessError(`no fabric-api jar in ${modsSrc} or ${userMods}`);
      await copyFile(path.join(userMods, api), path.join(modsSrc, api));
      jars.push(api);
    }
    if (!jars.some((f) => f.startsWith('gptgod-client'))) {
      throw new HarnessError(`no gptgod-client*.jar in ${modsSrc}: build the client mod (client-mod: ./gradlew build) and copy its jar there; it must be a build that contains the puppet package`);
    }
    const modsDst = path.join(this.baseDir, 'game', 'mods');
    await rm(modsDst, { recursive: true, force: true });
    await mkdir(modsDst, { recursive: true });
    for (const f of jars) await copyFile(path.join(modsSrc, f), path.join(modsDst, f));

    const jvm = (p.arguments?.jvm ?? []).filter((a) => typeof a === 'string');
    jvm.push(`-Dgptgod.puppet.port=${this.port}`, `-Dgptgod.puppet.token=${this.token}`);
    return { cp, jvm, mainClass: p.mainClass, mods: jars };
  }

  /** One puppet request. Throws HarnessError with the puppet's own message on {ok:false}. */
  async call(method, route, body, { timeoutMs = 40_000 } = {}) {
    if (!this.running) throw new HarnessError(`player ${this.name} is not running (player_launch first)`);
    let res;
    try {
      res = await fetch(`http://127.0.0.1:${this.port}${route}`, {
        method,
        headers: { Authorization: `Bearer ${this.token}`, ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (e) {
      throw new HarnessError(`puppet of ${this.name} did not answer ${method} ${route}: ${e.cause?.code ?? e.message}`);
    }
    const text = await res.text();
    let json;
    try {
      json = JSON.parse(text);
    } catch {
      throw new HarnessError(`puppet of ${this.name} returned non-JSON for ${route} (${res.status}): ${text.slice(0, 200)}`);
    }
    if (json.ok === false || !res.ok) throw new HarnessError(`${this.name} ${route}: ${json.error ?? res.status}`, { status: res.status });
    return json;
  }

  async launchPlayer({ timeoutSec = 240, windowMode = 'background', gamemode } = {}) {
    const r = await this.launch({ timeoutSec, windowMode, spectator: false });
    if (r.already) return { ...r, port: this.port };
    // the puppet answers once the client is in the world
    const deadline = Date.now() + 60_000;
    let state = null;
    while (Date.now() < deadline && this.running) {
      state = await this.call('GET', '/state', undefined, { timeoutMs: 5000 }).catch(() => null);
      if (state?.inWorld) break;
      await sleep(1000);
    }
    if (!state?.inWorld) throw new HarnessError(`${this.name} joined, but its puppet port ${this.port} did not report inWorld; see ${path.join(this.baseDir, 'client.log')}`, { tail: await this.logTail(25) });
    this.puppetReady = true;
    const setup = [];
    if (gamemode) setup.push(await this.sandbox.command(`gamemode ${gamemode} ${this.name}`).catch((e) => ({ ok: false, error: e.message })));
    return { ok: true, name: this.name, pid: r.pid, port: this.port, joinedInSec: r.joinedInSec, gameDir: r.gameDir, log: r.log, window: r.window, setup, state };
  }

  /** A framebuffer screenshot through the puppet (what this player sees, HUD included unless hideHud). */
  async shot({ file, hideHud = false }) {
    await mkdir(path.dirname(file), { recursive: true });
    const r = await this.call('POST', '/screenshot', { path: file, hideHud });
    const png = await readFile(r.path ?? file);
    return { ok: true, file: r.path ?? file, png, width: r.width, height: r.height };
  }

  async waitFor({ pattern, flags = 'i', since, kinds, timeoutSec = 30 }) {
    const re = compileRegex(pattern, flags);
    const kindSet = kinds?.length ? new Set(kinds) : null;
    let cursor = since ?? (await this.call('GET', '/events?since=999999999&limit=1')).latest ?? 0;
    const deadline = Date.now() + Math.min(600, timeoutSec) * 1000;
    while (Date.now() < deadline) {
      const r = await this.call('GET', `/events?since=${cursor}&limit=200`);
      for (const e of r.events ?? []) {
        cursor = Math.max(cursor, e.seq);
        if (kindSet && !kindSet.has(e.kind)) continue;
        if (re.test(e.text ?? '')) return { ok: true, matched: true, entry: e, latest: r.latest };
      }
      await sleep(700);
    }
    return { ok: true, matched: false, latest: cursor, timedOutAfterSec: timeoutSec };
  }
}

export class PlayerManager {
  constructor({ sandbox, bridge }) {
    this.sandbox = sandbox;
    this.bridge = bridge;
    this.players = new Map();
    this.nextPort = Number(process.env.GOD_PUPPET_PORT || 8801);
  }

  all() {
    return [...this.players.values()].filter((p) => p.running);
  }

  get(name) {
    if (name) {
      const p = [...this.players.values()].find((x) => x.name.toLowerCase() === String(name).toLowerCase());
      if (!p) throw new HarnessError(`no real-client player ${name} (running: ${this.all().map((x) => x.name).join(', ') || 'none'}; player_launch first)`);
      return p;
    }
    const live = this.all();
    if (live.length === 1) return live[0];
    throw new HarnessError(live.length ? `username is required (players: ${live.map((x) => x.name).join(', ')})` : 'no real-client player is running (player_launch first)');
  }

  async launch({ username, gamemode, windowMode, timeoutSec, xmx }) {
    if (!/^[A-Za-z0-9_]{3,16}$/.test(username ?? '')) throw new HarnessError('username must be 3-16 chars [A-Za-z0-9_]');
    let p = this.players.get(username.toLowerCase());
    if (!p || !p.running) {
      p = new PlayerClient({ sandbox: this.sandbox, bridge: this.bridge, name: username, port: this.nextPort++, xmx });
      this.players.set(username.toLowerCase(), p);
    }
    return p.launchPlayer({ gamemode, windowMode, timeoutSec });
  }

  list() {
    return { ok: true, players: [...this.players.values()].map((p) => ({ ...p.status(), port: p.port, puppetReady: p.puppetReady })) };
  }

  async close(name, opts) {
    const p = this.get(name);
    await p.call('POST', '/quit', {}, { timeoutMs: 3000 }).catch(() => {});
    await sleep(1500);
    const r = await p.close(opts);
    this.players.delete(p.name.toLowerCase());
    return r;
  }

  async closeAll(opts) {
    for (const p of [...this.players.values()]) if (p.running) await this.close(p.name, opts).catch(() => {});
  }
}
