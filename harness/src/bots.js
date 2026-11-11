// Headless mineflayer player bots that act like players and record everything the god says or
// does to them in a rolling, seq-numbered event log.

import { EventEmitter } from 'node:events';
import { createRequire } from 'node:module';
import mineflayer from 'mineflayer';
import pathfinderPkg from 'mineflayer-pathfinder';
import { HarnessError, compileRegex, sleep } from './util.js';

export { HarnessError, compileRegex, sleep };

const { pathfinder, Movements, goals } = pathfinderPkg;
// Resolve mineflayer's own transitive deps relative to mineflayer, so hoisting changes don't matter.
const mfRequire = createRequire(import.meta.resolve('mineflayer'));
const { Vec3 } = mfRequire('vec3');
const loadChat = mfRequire('prismarine-chat');

// Read lazily so harness/.env (loaded by the entry points) and late env changes apply.
// Bots speak at most 26.1 (mineflayer 4.39.0 / minecraft-data 3.119.0). A 26.3 server needs
// ViaVersion + ViaBackwards so they can join; "auto" lets minecraft-protocol ping the server, which
// with ViaBackwards answers with 26.1's protocol.
export const DEFAULTS = {
  get host() {
    return process.env.GOD_MC_HOST || '127.0.0.1';
  },
  get port() {
    return Number(process.env.GOD_MC_PORT || 25565);
  },
  get version() {
    const v = process.env.GOD_MC_VERSION || 'auto';
    return v === 'auto' ? false : v;
  },
  auth: 'offline',
};

/**
 * 26.3 servers kick a client whose second position packet arrives before a serverbound
 * client_tick_end ("Invalid move player packet received"); vanilla clients send one at the end of
 * every tick since 1.21.2, mineflayer 4.39.0 never does (PrismarineJS/mineflayer#4189, fixed on
 * mineflayer master by #4137). This keeps at most one position packet per tick_end.
 */
function installTickEnd(bot) {
  const client = bot._client;
  const hasPacket = () => {
    try {
      return Object.values(bot.registry.protocol.play.toServer.types.packet[1][0].type[1].mappings).includes('tick_end');
    } catch {
      return false;
    }
  };
  if (client.__tickEnd || process.env.GOD_BOT_TICK_END === '0') return;
  client.__tickEnd = true;
  const write = client.write.bind(client);
  let moved = false;
  let enabled = null;
  const sendTickEnd = () => {
    if (client.state !== 'play') return;
    write('tick_end', {});
    moved = false;
  };
  client.write = (name, params) => {
    if (enabled === null && bot.registry) enabled = hasPacket();
    if (enabled) {
      if (name === 'position' || name === 'position_look') {
        if (moved) sendTickEnd();
        moved = true;
      } else if (name === 'tick_end') {
        moved = false;
      }
    }
    return write(name, params);
  };
  // physicsTick fires just before mineflayer sends this tick's position (synchronously, in the same
  // call): a microtask runs right after it, so tick_end follows the position like a vanilla client's.
  const mode = process.env.GOD_BOT_TICK_END || 'after';
  bot.on('physicsTick', () => {
    if (!enabled) return;
    if (mode === 'before') {
      if (moved) sendTickEnd();
    } else {
      queueMicrotask(sendTickEnd);
    }
  });
}

const LOG_CAP = 500;
const SPAWN_TIMEOUT_MS = 45_000;
const NOTABLE_ENTITIES = new Set(
  (process.env.BOT_NOTABLE_ENTITIES ||
    'block_display,text_display,item_display,interaction,lightning_bolt,tnt,evoker_fangs,falling_block')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean),
);
// Sounds that are pure noise for our purposes.
const NOISY_SOUND = /\.step$|\.swim$|\.splash$|\.ambient$|^block\..*\.(place|break|hit|fall)$|^entity\.(player|generic)\.(small_fall|big_fall|attack\.|hurt)|^item\.armor|^entity\.item\.pickup|^ui\./;
const BLOCK_WATCH_RADIUS = 12;
const PARTICLE_WATCH_RADIUS = 32;

// ---------- small helpers ----------

const r1 = (n) => (typeof n === 'number' ? Math.round(n * 10) / 10 : n);
const vec = (v) => (v ? { x: r1(v.x), y: r1(v.y), z: r1(v.z) } : null);
const fmtPos = (v) => (v ? `(${r1(v.x)}, ${r1(v.y)}, ${r1(v.z)})` : '(?)');

function withTimeout(promise, ms, label, onTimeout) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => {
      try {
        onTimeout?.();
      } catch {
        /* ignore */
      }
      reject(new HarnessError(`${label} timed out after ${Math.round(ms / 1000)}s`));
    }, ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

function parseCoords(args, label = 'target') {
  const { x, y, z } = args;
  if ([x, y, z].every((n) => typeof n === 'number' && Number.isFinite(n))) return new Vec3(x, y, z);
  throw new HarnessError(`${label}: provide numeric x, y and z`);
}

/** Collects items per key for `delayMs`, then calls flush(key, items). */
class Batcher {
  constructor(delayMs, flush) {
    this.delayMs = delayMs;
    this.flushFn = flush;
    this.pending = new Map();
    this.timer = null;
  }

  add(key, item) {
    if (!this.pending.has(key)) this.pending.set(key, []);
    this.pending.get(key).push(item);
    if (!this.timer) this.timer = setTimeout(() => this.flush(), this.delayMs);
  }

  flush() {
    const batch = this.pending;
    this.pending = new Map();
    this.timer = null;
    for (const [key, items] of batch) {
      try {
        this.flushFn(key, items);
      } catch (e) {
        console.error('[bots] batch flush failed:', e);
      }
    }
  }

  cancel() {
    clearTimeout(this.timer);
    this.timer = null;
    this.pending.clear();
  }
}

/** Per-name throttle: first hit logs after `firstDelay`, then at most once per `interval`, with totals. */
class Throttle {
  constructor(firstDelay, interval, flush) {
    Object.assign(this, { firstDelay, interval, flushFn: flush, state: new Map() });
  }

  add(key, amount, extra) {
    let s = this.state.get(key);
    if (!s) this.state.set(key, (s = { count: 0, last: 0, timer: null, extra: null }));
    s.count += amount;
    if (!s.extra || (extra && extra.dist < s.extra.dist)) s.extra = extra;
    if (!s.timer) {
      const wait = Math.max(this.firstDelay, s.last + this.interval - Date.now());
      s.timer = setTimeout(() => {
        const { count, extra: ex } = s;
        Object.assign(s, { count: 0, extra: null, timer: null, last: Date.now() });
        try {
          this.flushFn(key, count, ex);
        } catch (e) {
          console.error('[bots] throttle flush failed:', e);
        }
      }, wait);
    }
  }

  cancel() {
    for (const s of this.state.values()) clearTimeout(s.timer);
    this.state.clear();
  }
}

// ---------- one bot ----------

class BotHandle {
  constructor(manager, username, opts) {
    this.manager = manager;
    this.username = username;
    this.opts = opts;
    this.log = [];
    this.state = 'connecting';
    this.endReason = null;
    this.spawnedAt = null;
    this.bot = null;
    this.ChatMessage = null;
    this.movements = null;
    this.following = null;
    this.displayTexts = new Map(); // text_display entity id -> last text
    this.timers = new Set();
    this.closing = false;
    this.serverResets = 0; // server position packets (teleports and movement corrections)
  }

  push(kind, text, data) {
    const entry = { seq: this.manager._nextSeq(), t: new Date().toISOString(), kind, text: String(text) };
    if (data) Object.assign(entry, data);
    this.log.push(entry);
    if (this.log.length > LOG_CAP) this.log.splice(0, this.log.length - LOG_CAP);
    this.manager.emit('entry', this.username, entry);
    return entry;
  }

  get connected() {
    return this.state === 'online';
  }

  /** Best-effort plain text from a chat component (JSON string, NBT object or ChatMessage). */
  text(raw) {
    if (raw == null) return '';
    if (typeof raw === 'object' && typeof raw.toMotd === 'function') return raw.toString(); // already a ChatMessage
    const CM = this.ChatMessage;
    if (CM) {
      try {
        return CM.fromNotch(raw).toString();
      } catch {
        try {
          return new CM(raw).toString();
        } catch {
          /* fall through */
        }
      }
    }
    return typeof raw === 'string' ? raw : JSON.stringify(raw);
  }

  distanceTo(pos) {
    const me = this.bot?.entity?.position;
    return me && pos ? me.distanceTo(pos) : Infinity;
  }

  setTimer(fn, ms) {
    const t = setTimeout(() => {
      this.timers.delete(t);
      fn();
    }, ms);
    this.timers.add(t);
    return t;
  }

  dispose() {
    for (const t of this.timers) clearTimeout(t);
    this.timers.clear();
    this.batchers?.forEach((b) => b.cancel());
  }
}

// ---------- manager ----------

export class BotManager extends EventEmitter {
  constructor(defaults = {}) {
    super();
    this.setMaxListeners(100);
    this.defaults = defaults; // per-manager overrides; DEFAULTS (env) fill the rest at connect time
    this.bots = new Map();
    this.seq = 0;
    this._spawnChain = Promise.resolve(); // logins are serialized (Paper's connection throttle)
    this._pending = new Map(); // username -> in-flight spawn promise
  }

  _nextSeq() {
    return ++this.seq;
  }

  _get(username) {
    if (!username) {
      if (this.bots.size === 1) return [...this.bots.values()][0];
      throw new HarnessError(
        this.bots.size ? `username required (bots: ${[...this.bots.keys()].join(', ')})` : 'no bots spawned',
      );
    }
    const h = this.bots.get(username);
    if (!h) throw new HarnessError(`no bot named ${username} (bots: ${[...this.bots.keys()].join(', ') || 'none'})`);
    return h;
  }

  _online(username) {
    const h = this._get(username);
    if (!h.connected || !h.bot?.entity) {
      throw new HarnessError(`bot ${h.username} is not online (state: ${h.state}${h.endReason ? `, reason: ${h.endReason}` : ''})`);
    }
    return h;
  }

  // ----- lifecycle -----

  /** Spawn (or reuse) a bot. Resolves once it has spawned in the world. */
  spawn(username, opts = {}) {
    if (!/^[A-Za-z0-9_]{3,16}$/.test(username || '')) {
      throw new HarnessError('username must be 3-16 chars of letters, digits or underscore');
    }
    if (this._pending.has(username)) return this._pending.get(username); // same spawn already in flight
    const existing = this.bots.get(username);
    if (existing?.state === 'online') return Promise.resolve({ ok: true, already: true, ...this._summary(existing) });
    const run = () => this._connectWithRetry(username, opts, this.bots.get(username));
    const p = this._spawnChain.then(run, run).finally(() => this._pending.delete(username));
    this._pending.set(username, p);
    this._spawnChain = p.catch(() => {});
    return p;
  }

  async _connectWithRetry(username, opts, previous) {
    try {
      return await this._connect(username, opts, previous);
    } catch (e) {
      // Paper kicks rapid re-logins from the same IP ("Connection throttled"); wait it out once.
      if (/throttl/i.test(e.message)) {
        await sleep(4500);
        return this._connect(username, opts, this.bots.get(username));
      }
      throw e;
    }
  }

  _connect(username, opts, previous) {
    const o = {
      host: opts.host ?? this.defaults.host ?? DEFAULTS.host,
      port: Number(opts.port ?? this.defaults.port ?? DEFAULTS.port),
      version: opts.version ?? this.defaults.version ?? DEFAULTS.version,
      auth: DEFAULTS.auth,
    };
    const h = new BotHandle(this, username, o);
    if (previous) {
      previous.dispose();
      h.log = previous.log; // keep history across reconnects
    }
    this.bots.set(username, h);
    h.push('info', `connecting to ${o.host}:${o.port} (${o.version || 'auto version'})`);

    return new Promise((resolve, reject) => {
      let settled = false;
      const fail = (msg) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        h.state = 'failed';
        h.endReason = msg;
        try {
          h.bot?.end?.('spawn failed');
        } catch {
          /* ignore */
        }
        h.dispose();
        const hint = /unsupported protocol|not supported|version/i.test(msg)
          ? ' (bots speak at most Minecraft 26.1: a 26.3 server needs ViaVersion + ViaBackwards, see harness/scripts/install-via.js)'
          : /whitelist|white-listed/i.test(msg)
            ? ' (the sandbox whitelist is on: bot_spawn adds bots to it when it manages the sandbox)'
            : '';
        reject(new HarnessError(`bot ${username} failed to spawn: ${msg}${hint}`, { recent: h.log.slice(-5) }));
      };
      const timer = setTimeout(() => fail(`no spawn within ${SPAWN_TIMEOUT_MS / 1000}s`), SPAWN_TIMEOUT_MS);

      let bot;
      try {
        bot = mineflayer.createBot({
          host: o.host,
          port: o.port,
          username,
          version: o.version,
          auth: o.auth,
          respawn: true, // auto-respawn on death
          hideErrors: true,
          logErrors: false,
          checkTimeoutInterval: 60_000,
        });
      } catch (e) {
        fail(e.message);
        return;
      }
      h.bot = bot;
      installTickEnd(bot);
      bot.loadPlugin(pathfinder);
      this._wire(h, {
        onFirstSpawn: () => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          resolve({ ok: true, ...this._summary(h) });
        },
        onEarlyEnd: (reason) => fail(reason),
      });
    });
  }

  async disconnect(username) {
    const h = this._get(username);
    h.closing = true;
    const bot = h.bot;
    if (bot && h.state !== 'disconnected' && h.state !== 'failed') {
      await new Promise((res) => {
        const t = setTimeout(res, 3000);
        bot.once('end', () => {
          clearTimeout(t);
          res();
        });
        try {
          bot.quit('harness disconnect');
        } catch {
          clearTimeout(t);
          res();
        }
      });
    }
    h.dispose();
    this.bots.delete(h.username);
    return { ok: true, disconnected: h.username, lastEvents: h.log.slice(-5) };
  }

  async disconnectAll() {
    await Promise.all([...this.bots.keys()].map((u) => this.disconnect(u).catch(() => {})));
  }

  list() {
    return [...this.bots.values()].map((h) => this._summary(h));
  }

  _summary(h) {
    const b = h.bot;
    const online = h.connected && b?.entity;
    return {
      username: h.username,
      state: h.state,
      server: `${h.opts.host}:${h.opts.port}`,
      endReason: h.endReason ?? undefined,
      position: online ? vec(b.entity.position) : undefined,
      health: online ? r1(b.health) : undefined,
      gamemode: online ? b.game?.gameMode : undefined,
      dimension: online ? b.game?.dimension : undefined,
      following: h.following ?? undefined,
      events: h.log.length,
      latestSeq: h.log.at(-1)?.seq ?? 0,
    };
  }

  // ----- event wiring -----

  _wire(h, { onFirstSpawn, onEarlyEnd }) {
    const bot = h.bot;
    const me = h.username;
    // Every listener is wrapped so a bad packet can never crash the process.
    const on = (emitter, event, fn) =>
      emitter.on(event, (...args) => {
        try {
          fn(...args);
        } catch (e) {
          console.error(`[bots] ${me} ${event} handler error:`, e);
        }
      });

    let lastPos = null;
    let lastGame = { gameMode: null, dimension: null };
    let lastTime = null;
    let lastHealth = null;
    let lastFood = null;
    let invSnapshot = null;
    let lastSidebar = '';
    const bossTitles = new Map();

    // --- coalescers ---
    const entityBatch = new Batcher(300, (key, items) => {
      const [verb, type] = key.split(':');
      items.sort((a, b) => a.dist - b.dist);
      const n = items[0];
      const where = Number.isFinite(n.dist) ? `, nearest ${r1(n.dist)}m at ${fmtPos(n.pos)}` : '';
      const label = items.length === 1 ? `${type} #${n.id}` : `${items.length}× ${type}`;
      h.push(verb === 'spawn' ? 'entity_spawn' : 'entity_gone', `${label} ${verb === 'spawn' ? 'appeared' : 'despawned'}${where}`, {
        entityType: type,
        count: items.length,
        nearest: Number.isFinite(n.dist) ? r1(n.dist) : null,
        ids: items.slice(0, 30).map((i) => i.id),
      });
    });
    const blockBatch = new Batcher(400, (_key, items) => {
      const placed = {};
      const removed = {};
      for (const { from, to } of items) {
        placed[to] = (placed[to] || 0) + 1;
        removed[from] = (removed[from] || 0) + 1;
      }
      const fmt = (m) =>
        Object.entries(m)
          .sort((a, b) => b[1] - a[1])
          .map(([k, v]) => `${k}×${v}`)
          .join(', ');
      h.push('blocks', `${items.length} block(s) changed within ${BLOCK_WATCH_RADIUS}m: now ${fmt(placed)} (was ${fmt(removed)})`, {
        count: items.length,
        placed,
        removed,
      });
    });
    const invBatch = new Batcher(300, () => {
      const now = countInventory(bot);
      if (!invSnapshot) {
        invSnapshot = now;
        return;
      }
      const diff = [];
      for (const name of new Set([...Object.keys(now), ...Object.keys(invSnapshot)])) {
        const d = (now[name] || 0) - (invSnapshot[name] || 0);
        if (d) diff.push(`${d > 0 ? '+' : ''}${d} ${name}`);
      }
      invSnapshot = now;
      if (diff.length) h.push('inventory', `inventory: ${diff.join(', ')}`, { changes: diff });
    });
    const particleThrottle = new Throttle(1000, 15_000, (name, count, ex) =>
      h.push('particle', `particles: ${count}× ${name}${ex ? ` nearest ${r1(ex.dist)}m at ${fmtPos(ex.pos)}` : ''}`, { particle: name, count }),
    );
    const soundThrottle = new Throttle(200, 5000, (name, count, ex) =>
      h.push('sound', `sound ${name}${count > 1 ? ` ×${count}` : ''}${ex ? ` ${r1(ex.dist)}m away` : ''}`, { sound: name }),
    );
    const sidebarBatch = new Batcher(500, () => {
      const sb = sidebarSnapshot(bot, h);
      const key = JSON.stringify(sb);
      if (key === lastSidebar) return;
      lastSidebar = key;
      if (!sb) h.push('sidebar', 'sidebar cleared');
      else h.push('sidebar', `sidebar "${sb.title}": ${sb.lines.join(' | ') || '(empty)'}`, { sidebar: sb });
    });
    h.batchers = [entityBatch, blockBatch, invBatch, particleThrottle, soundThrottle, sidebarBatch];

    // --- lifecycle ---
    // Plugins (inventory, etc.) are injected asynchronously; wire what depends on them here.
    on(bot, 'inject_allowed', () => {
      h.ChatMessage = loadChat(bot.registry);
      on(bot.inventory, 'updateSlot', () => {
        if (h.spawnedAt) invBatch.add('inv', 1);
      });
    });

    on(bot, 'login', () => h.push('info', `logged in as ${bot.username}`));

    on(bot, 'spawn', () => {
      const pos = bot.entity?.position;
      if (!h.spawnedAt) {
        h.spawnedAt = Date.now();
        h.state = 'online';
        const m = new Movements(bot);
        m.canDig = false;
        m.allow1by1towers = false;
        m.scafoldingBlocks = [];
        h.movements = m;
        bot.pathfinder.setMovements(m);
        bot.pathfinder.thinkTimeout = 10_000;
        lastGame = { gameMode: bot.game?.gameMode, dimension: bot.game?.dimension };
        h.push('spawn', `spawned at ${fmtPos(pos)} in ${bot.game?.dimension} (${bot.game?.gameMode})`, { position: vec(pos) });
        h.setTimer(() => invBatch.add('inv', 1), 1500); // inventory baseline
        onFirstSpawn();
      } else {
        h.push('respawn', `respawned at ${fmtPos(pos)} in ${bot.game?.dimension}`, { position: vec(pos) });
      }
      lastPos = pos?.clone();
    });

    on(bot, 'kicked', (reason, loggedIn) => {
      const text = h.text(reason);
      h.endReason = `kicked: ${text}`;
      h.push('kicked', `kicked${loggedIn ? '' : ' during login'}: ${text}`);
    });

    on(bot, 'error', (err) => {
      const msg = err?.code ? `${err.code} ${err.message}` : String(err?.message || err);
      h.push('error', msg);
      if (!h.spawnedAt) onEarlyEnd(msg);
    });

    on(bot, 'end', (reason) => {
      const wasOnline = h.state === 'online';
      h.state = 'disconnected';
      h.endReason = h.endReason || `connection ended: ${reason}`;
      h.following = null;
      h.push('end', `disconnected: ${h.endReason}`);
      if (!h.spawnedAt) onEarlyEnd(h.endReason);
      else if (wasOnline && !h.closing) console.error(`[bots] ${me} disconnected: ${h.endReason}`);
      h.dispose();
    });

    // --- chat & text ---
    on(bot, 'message', (jsonMsg, position, sender) => {
      const text = jsonMsg.toString();
      if (!text.trim()) return;
      if (position === 'game_info') return h.push('actionbar', text);
      if (position === 'chat') {
        let from = null;
        if (sender) from = Object.values(bot.players).find((p) => p.uuid === sender)?.username ?? null;
        if (!from) from = /^<([^>]+)>/.exec(text)?.[1] ?? null;
        return h.push('chat', text, { from });
      }
      // Some servers relay player chat as system messages; keep those classified as chat.
      const relayed = /^<([A-Za-z0-9_]{3,16})> /.exec(text)?.[1];
      if (relayed && bot.players[relayed]) return h.push('chat', text, { from: relayed });
      h.push('system', text);
    });

    for (const [packet, kind] of [
      ['set_title_text', 'title'],
      ['set_title_subtitle', 'subtitle'],
    ]) {
      on(bot._client, packet, (p) => {
        const text = h.text(p.text);
        if (text.trim()) h.push(kind, text);
      });
    }

    on(bot._client, 'death_combat_event', (p) => {
      if (p.playerId !== undefined && bot.entity && p.playerId !== bot.entity.id) return;
      h.push('death', `death message: ${h.text(p.message)}`);
    });

    // text_display contents (the vessel may speak through floating text). Debounced so a
    // typewriter effect logs only the settled text.
    const textKeyIndex = () => bot.registry.entitiesByName.text_display?.metadataKeys?.indexOf('text') ?? -1;
    const pendingText = new Map();
    on(bot._client, 'entity_metadata', (p) => {
      const ent = bot.entities[p.entityId];
      if (!ent || ent.name !== 'text_display') return;
      const idx = textKeyIndex();
      const m = p.metadata?.find((e) => e.key === idx);
      if (!m) return;
      const text = h.text(m.value).trim();
      if (!text || text === h.displayTexts.get(ent.id)) return;
      clearTimeout(pendingText.get(ent.id));
      pendingText.set(
        ent.id,
        h.setTimer(() => {
          pendingText.delete(ent.id);
          if (h.displayTexts.get(ent.id) === text) return;
          h.displayTexts.set(ent.id, text);
          const dist = h.distanceTo(ent.position);
          h.push('display_text', `text_display #${ent.id} (${r1(dist)}m): ${text}`, { entityId: ent.id, distance: r1(dist) });
        }, 700),
      );
    });

    on(bot, 'bossBarCreated', (bar) => {
      const title = h.text(bar.title);
      bossTitles.set(bar.entityUUID, title);
      h.push('bossbar', `bossbar shown: ${title}`);
    });
    on(bot, 'bossBarUpdated', (bar) => {
      const title = h.text(bar.title);
      if (bossTitles.get(bar.entityUUID) === title) return;
      bossTitles.set(bar.entityUUID, title);
      h.push('bossbar', `bossbar changed: ${title}`);
    });
    on(bot, 'bossBarDeleted', (bar) => {
      bossTitles.delete(bar.entityUUID);
      h.push('bossbar', `bossbar removed: ${h.text(bar.title)}`);
    });

    for (const ev of ['scoreboardCreated', 'scoreboardDeleted', 'scoreboardTitleChanged', 'scoreUpdated', 'scoreRemoved', 'scoreboardPosition', 'teamUpdated']) {
      on(bot, ev, () => sidebarBatch.add('sb', 1));
    }

    // --- body ---
    on(bot, 'health', () => {
      const hp = r1(bot.health);
      const food = bot.food;
      if (lastHealth !== null && hp !== lastHealth) {
        const d = r1(hp - lastHealth);
        h.push('health', `health ${lastHealth} → ${hp} (${d > 0 ? '+' : ''}${d}), food ${food}`, { health: hp, food, delta: d });
      } else if (lastFood !== null && food !== lastFood) {
        h.push('health', `food ${lastFood} → ${food}`, { health: hp, food });
      }
      lastHealth = hp;
      lastFood = food;
    });

    on(bot, 'death', () => h.push('death', `${me} died at ${fmtPos(bot.entity?.position)}`, { position: vec(bot.entity?.position) }));

    on(bot, 'entityHurt', (entity, source) => {
      if (entity !== bot.entity) return;
      const src = source ? source.username || source.name : null;
      h.push('damage', `took damage${src ? ` from ${src}` : ''}`, { source: src });
    });

    const effectName = (effect) => bot.registry.effects?.[effect.id]?.name ?? `effect#${effect.id}`;
    on(bot, 'entityEffect', (entity, effect) => {
      if (entity !== bot.entity) return;
      h.push('effect', `effect ${effectName(effect)} ${effect.amplifier + 1} for ${Math.round(effect.duration / 20)}s`, {
        effect: effectName(effect),
        amplifier: effect.amplifier,
        durationTicks: effect.duration,
      });
    });
    on(bot, 'entityEffectEnd', (entity, effect) => {
      if (entity === bot.entity) h.push('effect_end', `effect ${effectName(effect)} ended`, { effect: effectName(effect) });
    });

    on(bot, 'physicsTick', () => {
      if (bot.entity) lastPos = bot.entity.position.clone();
    });
    on(bot._client, 'position', () => {
      h.serverResets++;
    });
    on(bot, 'forcedMove', () => {
      const pos = bot.entity?.position;
      if (!pos || !h.spawnedAt) return;
      const d = lastPos ? pos.distanceTo(lastPos) : Infinity;
      if (d > 3) h.push('teleport', `teleported ${Number.isFinite(d) ? r1(d) + 'm ' : ''}to ${fmtPos(pos)}`, { position: vec(pos), distance: r1(d) });
      lastPos = pos.clone();
    });

    on(bot, 'game', () => {
      const g = { gameMode: bot.game?.gameMode, dimension: bot.game?.dimension };
      if (h.spawnedAt) {
        if (g.gameMode !== lastGame.gameMode) h.push('gamemode', `gamemode ${lastGame.gameMode} → ${g.gameMode}`);
        if (g.dimension !== lastGame.dimension) h.push('world', `dimension ${lastGame.dimension} → ${g.dimension}`);
      }
      lastGame = g;
    });

    on(bot, 'time', () => {
      const t = { time: bot.time.time, age: bot.time.age };
      if (lastTime && h.spawnedAt) {
        const jump = t.time - lastTime.time - (t.age - lastTime.age);
        if (Math.abs(jump) > 100) {
          h.push('time', `time of day changed to ${bot.time.timeOfDay} (${bot.time.isDay ? 'day' : 'night'})`, { timeOfDay: bot.time.timeOfDay });
        }
      }
      lastTime = t;
    });

    on(bot, 'rain', () => {
      if (h.spawnedAt) h.push('weather', bot.isRaining ? 'it started raining' : 'the rain stopped', { raining: bot.isRaining });
    });

    // --- world around the bot ---
    on(bot, 'entitySpawn', (e) => {
      if (e === bot.entity || !NOTABLE_ENTITIES.has(e.name)) return;
      entityBatch.add(`spawn:${e.name}`, { id: e.id, dist: h.distanceTo(e.position), pos: e.position.clone() });
    });
    on(bot, 'entityGone', (e) => {
      if (e === bot.entity) return;
      if (e.name === 'text_display') h.displayTexts.delete(e.id);
      if (h.following && e.username === h.following) h.push('info', `lost sight of followed player ${h.following}`);
      if (!NOTABLE_ENTITIES.has(e.name)) return;
      entityBatch.add(`gone:${e.name}`, { id: e.id, dist: h.distanceTo(e.position), pos: e.position.clone() });
    });

    on(bot, 'blockUpdate', (oldBlock, newBlock) => {
      if (!h.spawnedAt || !newBlock || !bot.entity) return;
      if (oldBlock && oldBlock.name === newBlock.name) return;
      if (h.distanceTo(newBlock.position) > BLOCK_WATCH_RADIUS) return;
      blockBatch.add('blocks', { from: oldBlock?.name ?? 'unknown', to: newBlock.name });
    });

    on(bot, 'particle', (p) => {
      if (!h.spawnedAt) return;
      const dist = h.distanceTo(p.position);
      if (dist > PARTICLE_WATCH_RADIUS) return;
      particleThrottle.add(p.name || `particle#${p.id}`, p.count || 1, { dist, pos: p.position });
    });

    on(bot, 'soundEffectHeard', (name, position) => {
      if (!h.spawnedAt || NOISY_SOUND.test(name)) return;
      soundThrottle.add(name.replace(/^minecraft:/, ''), 1, { dist: h.distanceTo(position), pos: position });
    });

    on(bot, 'playerJoined', (p) => {
      if (h.spawnedAt && Date.now() - h.spawnedAt > 2000 && p.username !== me) h.push('player', `${p.username} joined`);
    });
    on(bot, 'playerLeft', (p) => {
      if (h.spawnedAt && p.username !== me) h.push('player', `${p.username} left`);
    });
  }

  // ----- actions -----

  say(username, text) {
    const h = this._online(username);
    if (!text || !String(text).trim()) throw new HarnessError('text is empty');
    h.bot.chat(String(text));
    const entry = h.push('action', `said: ${text}`);
    return { ok: true, sent: String(text), seq: entry.seq };
  }

  _playerEntity(h, name) {
    const p = h.bot.players[name] ?? Object.values(h.bot.players).find((pl) => pl.username.toLowerCase() === String(name).toLowerCase());
    if (!p) throw new HarnessError(`player ${name} is not online`);
    if (!p.entity) throw new HarnessError(`player ${p.username} is online but not within the bot's view distance`);
    return p.entity;
  }

  async _pathTo(h, goal, timeoutMs, label) {
    const bot = h.bot;
    try {
      await withTimeout(bot.pathfinder.goto(goal), timeoutMs, label, () => bot.pathfinder.stop());
      return { reached: true };
    } catch (e) {
      bot.pathfinder.setGoal(null);
      return { reached: false, reason: e.message };
    }
  }

  async goto(username, args = {}) {
    const h = this._online(username);
    const bot = h.bot;
    const { player, range = 1.5, timeoutSec = 60, allowDig = false } = args;
    let goal;
    let target;
    if (player) {
      const ent = this._playerEntity(h, player);
      goal = new goals.GoalNear(ent.position.x, ent.position.y, ent.position.z, Math.max(range, 2));
      target = { player, position: vec(ent.position) };
    } else if (typeof args.y !== 'number' && typeof args.x === 'number' && typeof args.z === 'number') {
      goal = new goals.GoalNearXZ(args.x, args.z, range);
      target = { x: args.x, z: args.z };
    } else {
      const v = parseCoords(args, 'goto');
      goal = new goals.GoalNear(v.x, v.y, v.z, range);
      target = vec(v);
    }
    h.following = null;
    h.movements.canDig = !!allowDig;
    bot.pathfinder.setMovements(h.movements);
    const start = bot.entity.position.clone();
    const t0 = Date.now();
    h.push('action', `goto ${JSON.stringify(target)}`);
    const resets0 = h.serverResets;
    const res = await this._pathTo(h, goal, timeoutSec * 1000, 'goto');
    const pos = bot.entity?.position;
    const resets = h.serverResets - resets0;
    let reason = res.reason;
    if (!res.reached && resets >= 10) {
      reason =
        `${reason}; the server reset the bot's movement ${resets} times on the way. Known limitation: behind ViaBackwards, ` +
        'Paper 26.3 silently rejects bot moves on some natural terrain (flat or cleared ground works). Use bot_teleport, or walk on a flat area.';
    }
    return {
      ok: res.reached,
      reached: res.reached,
      reason,
      serverResets: resets,
      target,
      position: vec(pos),
      traveled: pos ? r1(pos.distanceTo(start)) : undefined,
      elapsedMs: Date.now() - t0,
    };
  }

  follow(username, { player, range = 2 } = {}) {
    const h = this._online(username);
    const ent = this._playerEntity(h, player);
    h.movements.canDig = false;
    h.bot.pathfinder.setMovements(h.movements);
    h.bot.pathfinder.setGoal(new goals.GoalFollow(ent, range), true);
    h.following = ent.username;
    h.push('action', `following ${ent.username}`);
    return { ok: true, following: ent.username, distance: r1(h.distanceTo(ent.position)) };
  }

  stop(username) {
    const h = this._online(username);
    const bot = h.bot;
    bot.pathfinder.stop();
    bot.pathfinder.setGoal(null);
    bot.clearControlStates();
    try {
      bot.stopDigging();
    } catch {
      /* not digging */
    }
    const was = h.following;
    h.following = null;
    h.push('action', 'stopped');
    return { ok: true, stopped: true, wasFollowing: was ?? undefined, position: vec(bot.entity.position) };
  }

  async lookAt(username, args = {}) {
    const h = this._online(username);
    let point;
    if (args.player) {
      const ent = this._playerEntity(h, args.player);
      point = ent.position.offset(0, ent.height ?? 1.62, 0);
    } else {
      point = parseCoords(args, 'look_at');
    }
    await h.bot.lookAt(point, true);
    return { ok: true, lookingAt: vec(point), yaw: r1(h.bot.entity.yaw), pitch: r1(h.bot.entity.pitch) };
  }

  async dig(username, args = {}) {
    const h = this._online(username);
    const bot = h.bot;
    const { block: blockName, radius = 16, timeoutSec = 30 } = args;
    let block;
    if (blockName) {
      const ids = Object.values(bot.registry.blocksByName)
        .filter((b) => b.name === blockName || b.name === blockName.replace(/^minecraft:/, ''))
        .map((b) => b.id);
      if (!ids.length) throw new HarnessError(`unknown block type ${blockName}`);
      block = bot.findBlock({ matching: ids, maxDistance: radius });
      if (!block) throw new HarnessError(`no ${blockName} within ${radius} blocks`);
    } else {
      block = bot.blockAt(parseCoords(args, 'dig'));
      if (!block) throw new HarnessError('that block is not loaded');
    }
    if (block.name === 'air' || block.name === 'cave_air' || block.name === 'void_air') {
      throw new HarnessError(`block at ${fmtPos(block.position)} is air`);
    }
    const t0 = Date.now();
    h.push('action', `dig ${block.name} at ${fmtPos(block.position)}`);
    if (!bot.canDigBlock(block)) {
      const res = await this._pathTo(h, new goals.GoalLookAtBlock(block.position, bot.world, { reach: 4 }), 20_000, 'approach block');
      if (!res.reached) return { ok: false, error: `could not reach ${block.name}: ${res.reason}`, position: vec(block.position) };
      block = bot.blockAt(block.position);
    }
    const tool = bot.pathfinder.bestHarvestTool(block);
    if (tool) await bot.equip(tool, 'hand').catch(() => {});
    try {
      await withTimeout(bot.dig(block, true), timeoutSec * 1000, 'dig', () => bot.stopDigging());
    } catch (e) {
      return { ok: false, error: e.message, block: block.name, position: vec(block.position) };
    }
    const now = bot.blockAt(block.position);
    return { ok: true, dug: block.name, position: vec(block.position), nowIs: now?.name, elapsedMs: Date.now() - t0 };
  }

  _findEntity(h, { player, type, entityId, radius = 16 }) {
    const bot = h.bot;
    if (player) return this._playerEntity(h, player);
    if (entityId !== undefined) {
      const e = bot.entities[entityId];
      if (!e) throw new HarnessError(`no entity with id ${entityId} in view`);
      return e;
    }
    if (!type) throw new HarnessError('provide player, type or entityId');
    const want = String(type).replace(/^minecraft:/, '');
    const e = bot.nearestEntity((ent) => ent !== bot.entity && ent.name === want && h.distanceTo(ent.position) <= radius);
    if (!e) throw new HarnessError(`no ${want} within ${radius} blocks`);
    return e;
  }

  async _approach(h, ent, within) {
    if (h.distanceTo(ent.position) <= within) return { reached: true };
    const p = ent.position;
    return this._pathTo(h, new goals.GoalNear(p.x, p.y, p.z, Math.max(1, within - 1)), 20_000, 'approach');
  }

  describeEntity(h, e) {
    return {
      id: e.id,
      type: e.name,
      kind: e.type,
      name: e.username ?? entityCustomName(h, e) ?? undefined,
      distance: r1(h.distanceTo(e.position)),
      position: vec(e.position),
      text: e.name === 'text_display' ? h.displayTexts.get(e.id) ?? undefined : undefined,
    };
  }

  async attack(username, args = {}) {
    const h = this._online(username);
    const bot = h.bot;
    const times = Math.max(1, Math.min(20, args.times ?? 1));
    const ent = this._findEntity(h, args);
    const target = this.describeEntity(h, ent);
    h.push('action', `attack ${target.name ?? target.type} #${ent.id} ×${times}`);
    const appr = await this._approach(h, ent, 3);
    if (!appr.reached && h.distanceTo(ent.position) > 3.5) return { ok: false, error: `could not reach target: ${appr.reason}`, target };
    let hits = 0;
    for (let i = 0; i < times; i++) {
      if (!bot.entities[ent.id]) break;
      await bot.lookAt(ent.position.offset(0, (ent.height ?? 1) * 0.8, 0), true);
      bot.attack(ent);
      hits++;
      if (i < times - 1) await sleep(650); // attack cooldown
    }
    const still = bot.entities[ent.id];
    return { ok: true, hits, target, targetStillPresent: !!still, targetDistance: still ? r1(h.distanceTo(still.position)) : undefined };
  }

  /** Right-click (default, vanilla interact_at + interact) or attack an entity, e.g. the vessel's 'interaction'. */
  async interact(username, args = {}) {
    const h = this._online(username);
    const bot = h.bot;
    const action = args.action ?? 'right_click';
    const ent = this._findEntity(h, { ...args, type: args.type ?? 'interaction' });
    const target = this.describeEntity(h, ent);
    h.push('action', `${action} ${target.name ?? target.type} #${ent.id}`);
    const appr = await this._approach(h, ent, 2.5);
    if (!appr.reached && h.distanceTo(ent.position) > 3.5) return { ok: false, error: `could not reach target: ${appr.reason}`, target };
    const center = ent.position.offset(0, Math.max(0.1, (ent.height || 1) / 2), 0);
    await bot.lookAt(center, true);
    if (action === 'attack') {
      bot.attack(ent);
    } else if (action === 'right_click') {
      await bot.activateEntityAt(ent, center);
      await bot.activateEntity(ent);
      bot.swingArm('right');
    } else {
      throw new HarnessError(`unknown action ${action} (use right_click or attack)`);
    }
    return { ok: true, action, target: { ...target, distance: r1(h.distanceTo(ent.position)) } };
  }

  async equip(username, { item, destination = 'hand' } = {}) {
    const h = this._online(username);
    const it = findItem(h.bot, item);
    if (!it) throw new HarnessError(`no ${item} in inventory`);
    await h.bot.equip(it, destination);
    h.push('action', `equipped ${it.name} to ${destination}`);
    return { ok: true, equipped: it.name, destination };
  }

  async toss(username, { item, count } = {}) {
    const h = this._online(username);
    const it = findItem(h.bot, item);
    if (!it) throw new HarnessError(`no ${item} in inventory`);
    if (count) await h.bot.toss(it.type, null, Math.min(count, countInventory(h.bot)[it.name] || 0));
    else await h.bot.tossStack(it);
    h.push('action', `tossed ${count ?? it.count} ${it.name}`);
    return { ok: true, tossed: it.name, count: count ?? it.count };
  }

  inventory(username) {
    const h = this._online(username);
    const bot = h.bot;
    const slot = (i) => bot.inventory.slots[i];
    const brief = (it) => (it ? { name: it.name, count: it.count, displayName: it.displayName } : null);
    return {
      ok: true,
      items: bot.inventory.items().map((it) => ({ slot: it.slot, name: it.name, count: it.count, custom: it.customName ?? undefined })),
      totals: countInventory(bot),
      held: brief(bot.heldItem),
      offhand: brief(slot(45)),
      armor: { head: brief(slot(5)), chest: brief(slot(6)), legs: brief(slot(7)), feet: brief(slot(8)) },
    };
  }

  status(username) {
    const h = this._get(username);
    const bot = h.bot;
    const base = this._summary(h);
    if (!h.connected || !bot?.entity) return { ok: true, ...base };
    return {
      ok: true,
      ...base,
      alive: bot.isAlive,
      food: bot.food,
      saturation: r1(bot.foodSaturation),
      oxygen: bot.oxygenLevel,
      xpLevel: bot.experience?.level,
      heldItem: bot.heldItem ? `${bot.heldItem.name} x${bot.heldItem.count}` : null,
      yaw: r1(bot.entity.yaw),
      pitch: r1(bot.entity.pitch),
      onGround: bot.entity.onGround,
      timeOfDay: bot.time?.timeOfDay,
      raining: bot.isRaining,
      effects: Object.values(bot.entity.effects || {}).map((e) => ({
        effect: bot.registry.effects?.[e.id]?.name ?? e.id,
        amplifier: e.amplifier,
        durationTicks: e.duration,
      })),
      pathfinding: bot.pathfinder.isMoving(),
      sidebar: sidebarSnapshot(bot, h) ?? undefined,
      players: Object.keys(bot.players),
    };
  }

  nearby(username, { radius = 32, type, limit = 40 } = {}) {
    const h = this._online(username);
    const bot = h.bot;
    const want = type ? String(type).replace(/^minecraft:/, '') : null;
    const list = Object.values(bot.entities)
      .filter((e) => e !== bot.entity && e.position && (!want || e.name === want || (want === 'player' && e.type === 'player')))
      .map((e) => this.describeEntity(h, e))
      .filter((e) => e.distance <= radius)
      .sort((a, b) => a.distance - b.distance);
    const counts = {};
    for (const e of list) counts[e.type] = (counts[e.type] || 0) + 1;
    return { ok: true, total: list.length, counts, entities: list.slice(0, limit) };
  }

  // ----- event log -----

  latestSeq() {
    return this.seq;
  }

  /** With `since`: the first `limit` entries after it. Without: the last `limit` entries. */
  events({ username, since, limit = 50, kinds } = {}) {
    const handles = username ? [this._get(username)] : [...this.bots.values()];
    const kindSet = kinds?.length ? new Set(kinds) : null;
    let all = [];
    for (const h of handles) {
      for (const e of h.log) {
        if (since !== undefined && e.seq <= since) continue;
        if (kindSet && !kindSet.has(e.kind)) continue;
        all.push(handles.length > 1 ? { bot: h.username, ...e } : e);
      }
    }
    all.sort((a, b) => a.seq - b.seq);
    const total = all.length;
    all = since !== undefined ? all.slice(0, limit) : all.slice(-limit);
    return { ok: true, latestSeq: this.seq, count: all.length, more: total > all.length, entries: all };
  }

  /**
   * Wait until a log entry with seq > `since` (default: now) matches `pattern`.
   * By default skips the bot's own 'action' entries and its own chat echoes.
   */
  waitFor({ pattern, flags = 'i', username, since, timeoutSec = 30, kinds, excludeSelf = true, signal, onProgress } = {}) {
    if (pattern === undefined || pattern === null || pattern === '') throw new HarnessError('pattern is required');
    const re = compileRegex(pattern, flags);
    if (username) this._get(username);
    const kindSet = kinds?.length ? new Set(kinds) : null;
    const startSeq = since ?? this.seq;
    const timeoutMs = Math.max(0.1, Math.min(600, timeoutSec)) * 1000;
    const test = (uname, e) => {
      if (e.seq <= startSeq) return false;
      if (username && uname !== username) return false;
      if (kindSet ? !kindSet.has(e.kind) : e.kind === 'action') return false;
      if (excludeSelf && e.kind === 'chat' && e.from === uname) return false;
      re.lastIndex = 0;
      return re.test(e.text);
    };
    for (const h of this.bots.values()) {
      const hit = h.log.find((e) => test(h.username, e));
      if (hit) return Promise.resolve({ ok: true, matched: true, bot: h.username, entry: hit, waitedMs: 0 });
    }
    const t0 = Date.now();
    return new Promise((resolve) => {
      let progressTimer = null;
      const finish = (result) => {
        clearTimeout(timer);
        clearInterval(progressTimer);
        this.off('entry', onEntry);
        signal?.removeEventListener?.('abort', onAbort);
        resolve({ ...result, waitedMs: Date.now() - t0 });
      };
      const onEntry = (uname, e) => {
        if (test(uname, e)) finish({ ok: true, matched: true, bot: uname, entry: e });
      };
      const onAbort = () => finish({ ok: false, matched: false, cancelled: true });
      const timer = setTimeout(() => {
        const recent = this.events({ username, since: startSeq, limit: 1000 }).entries.slice(-10);
        finish({ ok: false, matched: false, timedOut: true, pattern: String(pattern), latestSeq: this.seq, recentSinceStart: recent });
      }, timeoutMs);
      if (onProgress) {
        progressTimer = setInterval(() => {
          try {
            onProgress(Date.now() - t0, timeoutMs);
          } catch {
            /* ignore */
          }
        }, 5000);
      }
      this.on('entry', onEntry);
      if (signal) {
        if (signal.aborted) onAbort();
        else signal.addEventListener('abort', onAbort, { once: true });
      }
    });
  }
}

// ---------- world-reading helpers ----------

function countInventory(bot) {
  const totals = {};
  for (const it of bot.inventory?.items?.() ?? []) totals[it.name] = (totals[it.name] || 0) + it.count;
  const off = bot.inventory?.slots?.[45];
  if (off) totals[off.name] = (totals[off.name] || 0) + off.count;
  return totals;
}

function findItem(bot, name) {
  if (!name) throw new HarnessError('item name is required');
  const want = String(name).replace(/^minecraft:/, '').toLowerCase();
  return bot.inventory.items().find((it) => it.name === want || it.displayName?.toLowerCase() === want) ?? null;
}

function entityCustomName(h, e) {
  const keys = h.bot.registry.entitiesByName[e.name]?.metadataKeys;
  const idx = keys ? keys.indexOf('custom_name') : 2;
  const raw = e.metadata?.[idx];
  if (!raw) return null;
  const text = h.text(raw).trim();
  return text || null;
}

function sidebarSnapshot(bot, h) {
  const sb = bot.scoreboard?.sidebar;
  if (!sb) return null;
  const title = h.text(sb.title ?? sb.name).replace(/§./g, '');
  // ScoreBoard.items is already sorted by score, and displayName folds in team prefix/suffix.
  const lines = (sb.items || []).map((it) => {
    let label = it.name;
    try {
      label = it.displayName?.toString?.() ?? it.name;
    } catch {
      /* keep raw name */
    }
    return label.replace(/§./g, '').trim() || it.name;
  });
  return { title, lines };
}
