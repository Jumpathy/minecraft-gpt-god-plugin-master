// The real Minecraft client as a camera.
//
// Runs the installed vanilla client (%APPDATA%\.minecraft\versions\<version>) directly with Java 25,
// with no launcher and no account: an offline name (default HarnessCam) against the local
// offline-mode sandbox, with its own game directory (harness/client/game), so the user's options,
// saves and accounts are never touched. The launcher's account files are never opened.
//
// The camera player is put in spectator mode and aimed entirely with server commands (tp x y z yaw
// pitch). Screenshots capture the game window with PrintWindow while it stays behind other windows:
// no focus, no keystrokes, no mouse. Windows only.

import { execFile, spawn } from 'node:child_process';
import { createWriteStream, existsSync } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import { HARNESS_DIR, findJava, offlineUuid } from './sandbox.js';
import { HarnessError, sleep } from './util.js';

const execFileP = promisify(execFile);
const WINDOW_PS1 = path.join(HARNESS_DIR, 'scripts', 'window.ps1');

export const CLIENT = {
  get mcDir() {
    return process.env.GOD_CLIENT_MCDIR || path.join(process.env.APPDATA || '', '.minecraft');
  },
  get version() {
    return process.env.GOD_CLIENT_VERSION || '26.3';
  },
  get name() {
    return process.env.GOD_CAMERA_NAME || 'HarnessCam';
  },
  get dir() {
    return process.env.GOD_CLIENT_DIR || path.join(HARNESS_DIR, 'client');
  },
  get width() {
    return Number(process.env.GOD_CLIENT_WIDTH || 1280);
  },
  get height() {
    return Number(process.env.GOD_CLIENT_HEIGHT || 720);
  },
  get xmx() {
    return process.env.GOD_CLIENT_XMX || '3G';
  },
};

export const OUT_DIR = () => process.env.GOD_OUT_DIR || path.join(HARNESS_DIR, 'out');

// options.txt keys for a repeatable, quiet camera (the rest stay at the game's defaults).
const CAMERA_OPTIONS = {
  pauseOnLostFocus: 'false',
  onboardAccessibility: 'false',
  skipMultiplayerWarning: 'true',
  joinedFirstServer: 'true',
  tutorialStep: 'none',
  realmsNotifications: 'false',
  fullscreen: 'false',
  narrator: '0',
  soundCategory_master: '0.0', // muted: the user may be sitting at this computer
  inactivityFpsLimit: '"minimized"', // keep rendering at full rate while unfocused
  maxFps: '60',
  enableVsync: 'false',
  renderDistance: '10',
  simulationDistance: '8',
  fov: '0.0',
  guiScale: '2',
  showSubtitles: 'false',
  lang: 'en_us',
  hideMatchedNames: 'true',
};

function ruleAllows(rules) {
  if (!rules) return true;
  let allowed = false;
  for (const r of rules) {
    let match = true;
    if (r.os?.name && r.os.name !== 'windows') match = false;
    if (r.os?.arch && !['x86_64', 'x64', 'amd64'].includes(r.os.arch)) match = false;
    if (r.features) match = false; // demo / custom resolution / quick play features are handled explicitly
    if (match) allowed = r.action === 'allow';
  }
  return allowed;
}

const fmt = (n) => Math.round(n * 100) / 100;

export class CameraClient {
  constructor({ sandbox, bridge }) {
    this.sandbox = sandbox; // ServerManager
    this.bridge = bridge;
    this.child = null;
    this.state = 'stopped';
    this.lastExit = null;
    this.windowMode = null;
  }

  get name() {
    return CLIENT.name;
  }

  get running() {
    return !!(this.child && this.child.exitCode === null && this.child.signalCode === null);
  }

  // ----- launching -----

  async buildLaunch({ host, port }) {
    const mc = CLIENT.mcDir;
    const version = CLIENT.version;
    const vjson = path.join(mc, 'versions', version, `${version}.json`);
    const vjar = path.join(mc, 'versions', version, `${version}.jar`);
    if (!existsSync(vjson) || !existsSync(vjar)) {
      throw new HarnessError(`Minecraft ${version} is not installed at ${path.dirname(vjson)}. Install it once with the official launcher (play it once), or set GOD_CLIENT_VERSION / GOD_CLIENT_MCDIR.`);
    }
    const v = JSON.parse(await readFile(vjson, 'utf8'));
    if (v.inheritsFrom) throw new HarnessError(`${version} inherits from ${v.inheritsFrom}; only plain vanilla versions are supported`);
    const cp = [];
    const missing = [];
    for (const lib of v.libraries ?? []) {
      if (!ruleAllows(lib.rules)) continue;
      const art = lib.downloads?.artifact;
      if (!art?.path) continue;
      const p = path.join(mc, 'libraries', art.path);
      if (!existsSync(p)) missing.push(art.path);
      else if (!cp.includes(p)) cp.push(p);
    }
    if (missing.length) throw new HarnessError(`client libraries missing (start ${version} once from the launcher to download them): ${missing.slice(0, 5).join(', ')}`);
    cp.push(vjar);

    const assetsDir = path.join(mc, 'assets');
    const assetIndex = v.assetIndex?.id ?? v.assets;
    if (!existsSync(path.join(assetsDir, 'indexes', `${assetIndex}.json`))) throw new HarnessError(`asset index ${assetIndex} missing in ${assetsDir}`);

    const java = await findJava(v.javaVersion?.majorVersion ?? 25);
    const base = CLIENT.dir;
    const gameDir = path.join(base, 'game');
    const natives = path.join(base, 'natives');
    await mkdir(gameDir, { recursive: true });
    await mkdir(natives, { recursive: true });

    const vars = {
      natives_directory: natives,
      launcher_name: 'gptgod-harness',
      launcher_version: '0.2.0',
      classpath: cp.join(path.delimiter),
    };
    const sub = (s) => s.replace(/\$\{([a-z_]+)\}/g, (m, k) => (k in vars ? vars[k] : m));
    const jvm = [`-Xms1G`, `-Xmx${CLIENT.xmx}`];
    for (const a of v.arguments?.jvm ?? []) {
      if (typeof a === 'string') jvm.push(sub(a));
      else if (ruleAllows(a.rules)) jvm.push(...[a.value].flat().map(sub));
    }
    // The launcher's log4j config (v.logging) prints XML events for the launcher to parse; the
    // game's built-in config prints plain text and writes <gameDir>/logs/latest.log, so it is left out.

    const uuid = offlineUuid(this.name).replace(/-/g, '');
    const game = [
      '--username', this.name,
      '--version', version,
      '--gameDir', gameDir,
      '--assetsDir', assetsDir,
      '--assetIndex', String(assetIndex),
      '--uuid', uuid,
      '--accessToken', '0',
      '--versionType', 'release',
      '--width', String(CLIENT.width),
      '--height', String(CLIENT.height),
      '--quickPlayMultiplayer', `${host}:${port}`,
    ];
    return { java, args: [...jvm, v.mainClass, ...game], gameDir, version };
  }

  async writeOptions(gameDir) {
    const file = path.join(gameDir, 'options.txt');
    let lines = [];
    try {
      lines = (await readFile(file, 'utf8')).split(/\r?\n/).filter(Boolean);
    } catch {
      /* first launch */
    }
    const seen = new Set();
    lines = lines.map((l) => {
      const k = l.slice(0, l.indexOf(':'));
      if (k in CAMERA_OPTIONS) {
        seen.add(k);
        return `${k}:${CAMERA_OPTIONS[k]}`;
      }
      return l;
    });
    for (const [k, v] of Object.entries(CAMERA_OPTIONS)) if (!seen.has(k)) lines.push(`${k}:${v}`);
    await writeFile(file, lines.join('\n') + '\n');
  }

  /**
   * Launches the client and joins the sandbox as the camera. Opening a game window is unavoidable;
   * windowMode 'background' (default) pushes it behind other windows without activating it,
   * 'normal' leaves it where Windows put it. (A hidden or minimized window renders nothing to capture.)
   */
  async launch({ timeoutSec = 180, windowMode = 'background', spectator = true } = {}) {
    if (process.platform !== 'win32') throw new HarnessError('the camera client is implemented for Windows only');
    if (this.running) return { ok: true, already: true, pid: this.child.pid, state: this.state };
    const st = await this.sandbox.status();
    if (!st.running) throw new HarnessError('the sandbox server is not running (server_start first)');
    if (st.properties['online-mode'] !== 'false') {
      throw new HarnessError('the sandbox has online-mode=true; the camera joins with an offline name and needs online-mode=false (sandbox_configure, then server_restart)');
    }
    const { host, port } = await this.sandbox.address();
    const allow = await this.sandbox.allowPlayer(this.name);
    const launch = await this.buildLaunch({ host, port });
    await this.writeOptions(launch.gameDir);
    await mkdir(CLIENT.dir, { recursive: true });
    const logFile = path.join(CLIENT.dir, 'client.log');
    const out = createWriteStream(logFile, { flags: 'w' });
    const logCursor = await this.sandbox.logCursor();
    // The window the user is working in, to hand the foreground back if the game takes it.
    const prevForeground = (await this.helper('foreground').catch(() => null))?.hwnd ?? 0;
    // 26.3 renders through SDL3, which reads hints from the environment: do not activate the window.
    const env = { ...process.env, SDL_WINDOW_ACTIVATE_WHEN_SHOWN: '0', SDL_WINDOW_ACTIVATE_WHEN_RAISED: '0' };
    const child = spawn(launch.java.javaw, launch.args, { cwd: launch.gameDir, env, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: false });
    child.stdout.pipe(out);
    child.stderr.pipe(out);
    this.child = child;
    this.state = 'starting';
    this.lastExit = null;
    child.on('exit', (code, signal) => {
      this.lastExit = { code, signal, at: new Date().toISOString() };
      this.state = 'stopped';
    });

    const t0 = Date.now();
    const deadline = t0 + timeoutSec * 1000;
    // 1. the window
    let win = null;
    while (Date.now() < deadline && this.running) {
      win = await this.window('find').catch(() => null);
      if (win?.ok) break;
      await sleep(1000);
    }
    if (!this.running) throw new HarnessError(`client exited during startup (code ${this.lastExit?.code}); see ${logFile}`, { tail: await this.logTail(20) });
    if (!win?.ok) throw new HarnessError(`no game window after ${timeoutSec}s; see ${logFile}`);
    const focus = { tookForeground: false, gaveBack: false };
    const placeWindow = async () => {
      if (windowMode !== 'normal') win = await this.window('show', { Mode: windowMode }).catch(() => win);
      if (win?.foreground && prevForeground) {
        focus.tookForeground = true;
        const g = await this.window('giveback', { Prev: prevForeground }).catch(() => null);
        if (g?.gaveBack) focus.gaveBack = true;
        if (g?.ok) win = g;
      }
    };
    await placeWindow();
    this.windowMode = windowMode;
    // 2. the join, seen in the server log
    this.state = 'joining';
    const remaining = Math.max(5, Math.round((deadline - Date.now()) / 1000));
    const joined = await this.sandbox.waitForLog({ pattern: `${this.name}( ?\\[[^\\]]*\\])? (joined the game|logged in with entity id)`, since: logCursor, timeoutSec: remaining });
    if (!joined.matched) {
      const kick = (await this.sandbox.log({ since: logCursor, grep: `${this.name}|lost connection|disconnect`, lines: 10 })).lines;
      throw new HarnessError(`camera did not join within ${timeoutSec}s; see ${logFile}`, { serverLog: kick, clientLog: await this.logTail(15) });
    }
    this.state = 'online';
    const joinedAt = Date.now();
    // Let the world load before the first command.
    await sleep(3000);
    const setup = [];
    if (spectator) setup.push(await this.sandbox.command(`gamemode spectator ${this.name}`).catch((e) => ({ ok: false, error: e.message })));
    // Joining an offline server shows a "Chat messages can't be verified" toast for ~10 s: let it fade
    // so the first screenshot is clean.
    const toastGone = joinedAt + 12_000 - Date.now();
    if (toastGone > 0) await sleep(toastGone);
    // Re-assert the window placement: joining can raise it.
    await placeWindow();
    return {
      ok: true,
      pid: child.pid,
      name: this.name,
      version: launch.version,
      java: `${launch.java.javaw} (Java ${launch.java.major})`,
      gameDir: launch.gameDir,
      log: logFile,
      whitelist: allow,
      window: win,
      focus,
      joinedInSec: Math.round((Date.now() - t0) / 1000),
      setup,
    };
  }

  async logTail(n = 30) {
    try {
      return (await readFile(path.join(CLIENT.dir, 'client.log'), 'utf8')).split(/\r?\n/).filter(Boolean).slice(-n);
    } catch {
      return [];
    }
  }

  async window(action, extra = {}) {
    if (!this.running) throw new HarnessError('the camera client is not running (camera_launch first)');
    return this.helper(action, { ProcessId: this.child.pid, ...extra });
  }

  /** Runs scripts/window.ps1 (no window needed for -Action foreground). */
  async helper(action, extra = {}) {
    const args = ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', WINDOW_PS1, '-Action', action];
    for (const [k, v] of Object.entries(extra)) args.push(`-${k}`, String(v));
    const { stdout } = await execFileP('powershell.exe', args, { windowsHide: true, timeout: 60_000, maxBuffer: 1 << 20 });
    const text = stdout.trim().split(/\r?\n/).pop();
    try {
      return JSON.parse(text);
    } catch {
      throw new HarnessError(`window helper returned: ${stdout.slice(0, 300)}`);
    }
  }

  async close({ timeoutSec = 30 } = {}) {
    if (!this.running) return { ok: true, alreadyStopped: true, lastExit: this.lastExit ?? undefined };
    const t0 = Date.now();
    let method = 'WM_CLOSE';
    try {
      await this.window('close');
    } catch {
      method = 'kill';
      this.child.kill();
    }
    while (this.running && Date.now() - t0 < timeoutSec * 1000) await sleep(500);
    if (this.running) {
      method += ' then kill';
      this.child.kill();
      await sleep(1000);
    }
    this.state = 'stopped';
    return { ok: true, method, closedInSec: Math.round((Date.now() - t0) / 1000), exit: this.lastExit ?? undefined };
  }

  status() {
    return {
      ok: true,
      running: this.running,
      state: this.running ? this.state : 'stopped',
      pid: this.running ? this.child.pid : undefined,
      name: this.name,
      version: CLIENT.version,
      gameDir: path.join(CLIENT.dir, 'game'),
      windowMode: this.windowMode,
      lastExit: this.lastExit ?? undefined,
    };
  }

  // ----- aiming -----

  /** Where a target is: "vessel", a player name, or {x,y,z}. Returns {x,y,z,world}. */
  async locate(target) {
    if (target && typeof target === 'object' && Number.isFinite(target.x)) return target;
    const st = await this.bridge.status();
    if (target === 'vessel' || target === undefined) {
      const v = st.vessel;
      if (!v?.manifested) throw new HarnessError('the vessel is not manifested (god_tool vessel_go first)');
      return { x: v.x, y: v.y, z: v.z, world: v.world };
    }
    const p = st.players?.find((pl) => pl.name.toLowerCase() === String(target).toLowerCase());
    if (!p) throw new HarnessError(`no online player ${target} (online: ${st.players?.map((x) => x.name).join(', ') || 'none'})`);
    return { x: p.x, y: p.y, z: p.z, world: p.world };
  }

  /**
   * Aims the camera with server commands. Either an explicit position (x,y,z + yaw/pitch, or + lookAt),
   * or orbit a target: from `distance` blocks away at compass `angle` (0 = from the south, looking north;
   * 90 = from the west), `height` blocks above the target, looking at the target + `lookHeight`.
   */
  async aim({ x, y, z, yaw, pitch, target, lookAt, distance = 8, angle = 0, height = 3, lookHeight = 1.5 } = {}) {
    let pos;
    let look;
    if ([x, y, z].every(Number.isFinite)) {
      pos = { x, y, z };
      if (lookAt) look = await this.locate(lookAt);
    } else {
      const t = await this.locate(target ?? 'vessel');
      const a = (angle * Math.PI) / 180;
      // Minecraft: yaw 0 looks south (+z). A camera south of the target (angle 0) sits at +z and looks north.
      pos = { x: t.x - Math.sin(a) * distance, y: t.y + height, z: t.z + Math.cos(a) * distance };
      look = { x: t.x, y: t.y + lookHeight, z: t.z };
    }
    if (look) {
      const dx = look.x - pos.x;
      const dy = look.y - pos.y;
      const dz = look.z - pos.z;
      yaw = (Math.atan2(-dx, dz) * 180) / Math.PI;
      pitch = (-Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
    }
    yaw = Number.isFinite(yaw) ? yaw : 0;
    pitch = Number.isFinite(pitch) ? pitch : 0;
    const cmd = `tp ${this.name} ${fmt(pos.x)} ${fmt(pos.y)} ${fmt(pos.z)} ${fmt(yaw)} ${fmt(pitch)}`;
    const r = await this.sandbox.command(cmd);
    if (r.ok === false) throw new HarnessError(`teleport failed: ${JSON.stringify(r)}`);
    return { ok: true, command: cmd, position: { x: fmt(pos.x), y: fmt(pos.y), z: fmt(pos.z) }, yaw: fmt(yaw), pitch: fmt(pitch), feedback: r.feedback ?? r.output };
  }

  // ----- screenshots -----

  /**
   * Captures the game window's client area with PrintWindow(PW_RENDERFULLCONTENT): the real rendered
   * frame, taken while the window stays behind other windows, without focus or input. (Posting an F2
   * key does not work: 26.3's SDL window ignores key messages while it is unfocused.)
   * Saved to harness/out/<stamp>-<label>.png; returns {file, png, width, height, meanBrightness}.
   */
  async screenshot({ label = 'shot', settleMs = 1500 } = {}) {
    if (!this.running || this.state !== 'online') throw new HarnessError(`the camera client is not online (state ${this.state}); camera_launch first`);
    let win = await this.window('find');
    if (win.minimized || !win.visible) {
      // A minimized window renders nothing; restore it without activating it.
      win = await this.window('show', { Mode: this.windowMode === 'normal' ? 'normal' : 'background' });
      await sleep(1000);
    }
    if (settleMs > 0) await sleep(settleMs);
    const outDir = OUT_DIR();
    await mkdir(outDir, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const safe = String(label).replace(/[^A-Za-z0-9_-]+/g, '_').slice(0, 60) || 'shot';
    const file = path.join(outDir, `${stamp}-${safe}.png`);
    const r = await this.window('capture', { Out: file });
    if (!r.ok) throw new HarnessError(r.error ?? 'capture failed');
    const png = await readFile(file);
    const warning = r.meanBrightness < 3 ? 'the capture is (almost) black: the window may be minimized or covered by a fullscreen app' : undefined;
    return { ok: true, file, png, width: r.clientWidth, height: r.clientHeight, meanBrightness: r.meanBrightness, warning };
  }

}
