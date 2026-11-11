// Shared helpers for scenario scripts: step runner, schema-driven tool input, observation waits.

import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { sleep } from '../util.js';

export const HARNESS_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

export class SkipSignal extends Error {}
export class StepFailed extends Error {}

const color = process.stdout.isTTY && !process.env.NO_COLOR;
const paint = (code, s) => (color ? `\x1b[${code}m${s}\x1b[0m` : s);
const LABEL = {
  PASS: paint('32', 'PASS'),
  FAIL: paint('31', 'FAIL'),
  SKIP: paint('33', 'SKIP'),
  WARN: paint('33', 'WARN'),
};

/** Builds the `ctx` object handed to each scenario's run(). */
export function createContext({ bridge, bots, botName, host, port, sandbox, camera }) {
  const results = [];
  const ctx = {
    bridge,
    bots,
    sandbox,
    camera,
    botName,
    host,
    port,
    results,

    /**
     * Run one step. fn(s) may set s.detail. A failing step aborts the scenario unless optional.
     * Returns fn's return value.
     */
    async step(name, fn, { optional = false } = {}) {
      const s = { detail: '' };
      const t0 = Date.now();
      try {
        const value = await fn(s);
        record('PASS', name, Date.now() - t0, s.detail);
        return value;
      } catch (e) {
        if (e instanceof SkipSignal) {
          record('SKIP', name, Date.now() - t0, e.message);
          throw e;
        }
        const msg = e?.message || String(e);
        record(optional ? 'WARN' : 'FAIL', name, Date.now() - t0, [msg, s.detail].filter(Boolean).join(' | '));
        if (!optional) throw new StepFailed(msg);
        return undefined;
      }
    },

    skip(reason) {
      throw new SkipSignal(reason);
    },

    assert(cond, msg) {
      if (!cond) throw new Error(msg);
    },

    /** Spawn (or reuse) the scenario bot; against the local sandbox it is whitelisted first. */
    async ensureBot() {
      if (!host && sandbox) {
        const addr = await sandbox.address();
        await sandbox.allowPlayer(botName);
        return bots.spawn(botName, { host: addr.host, port: port ?? addr.port });
      }
      return bots.spawn(botName, { host, port });
    },

    /** Fetch /tools once and look up a tool by name (fails the step if missing). */
    async requireTool(name) {
      if (!ctx._tools) ctx._tools = (await bridge.tools()).tools ?? [];
      const t = ctx._tools.find((x) => x.name === name);
      if (!t) throw new Error(`god tool "${name}" not in /tools (available: ${ctx._tools.map((x) => x.name).join(', ') || 'none'})`);
      return t;
    },

    async hasTool(name) {
      if (!ctx._tools) ctx._tools = (await bridge.tools()).tools ?? [];
      return ctx._tools.some((x) => x.name === name);
    },

    /** POST /tool and fail unless ok && !isError. */
    async callTool(name, input, s) {
      const r = await bridge.tool(name, input);
      if (s) s.detail = `input=${JSON.stringify(input)} result=${short(r.result)}`;
      if (r.ok === false || r.isError) throw new Error(`${name} returned an error: ${short(r.result ?? r)}`);
      return r;
    },

    /** Wait for a bot log entry (seq > since) of the given kinds matching pattern. */
    async observe({ since, kinds, pattern = '.', timeoutSec = 10, username = botName }) {
      return bots.waitFor({ username, since, kinds, pattern, timeoutSec });
    },

    async saveView(type, png) {
      const dir = process.env.GOD_OUT_DIR || path.join(HARNESS_DIR, 'out');
      await mkdir(dir, { recursive: true });
      const file = path.join(dir, `${new Date().toISOString().replace(/[:.]/g, '-')}-${type}.png`);
      await writeFile(file, png);
      return file;
    },
  };

  function record(status, name, ms, detail) {
    results.push({ status, name, ms, detail });
    const d = detail ? ` — ${detail}` : '';
    console.log(`  ${LABEL[status]}  ${name} ${paint('2', `(${ms}ms)`)}${d}`);
  }

  return ctx;
}

export function short(v, max = 300) {
  const s = typeof v === 'string' ? v : JSON.stringify(v);
  return s && s.length > max ? s.slice(0, max) + '…' : s;
}

/** Poll fn() until it returns a truthy value or the timeout passes. */
export async function pollUntil(fn, { timeoutMs = 10_000, intervalMs = 500 } = {}) {
  const t0 = Date.now();
  let last;
  for (;;) {
    last = await fn();
    if (last) return last;
    if (Date.now() - t0 >= timeoutMs) return null;
    await sleep(intervalMs);
  }
}

// ---------- schema-driven tool input ----------

const ROLE_PATTERNS = [
  ['player', /^(player|players|target|who|recipient|victim|username|playername|player_name)$/i],
  ['message', /^(message|text|content|words|speech|say|line|msg)$/i],
  ['item', /^(item|material|item_name|itemname|item_id)$/i],
  ['count', /^(count|amount|quantity|qty|stack)$/i],
  ['effect', /^(effect|potion|effect_name|status)$/i],
  ['duration', /^(duration|seconds|secs|duration_seconds|time_seconds|length)$/i],
  ['durationTicks', /^(ticks|duration_ticks)$/i],
  ['level', /^(amplifier|level|strength|potency)$/i],
  ['power', /^(power|intensity|severity|magnitude)$/i],
  ['time', /^(time|when|time_of_day|phase|daytime)$/i],
  ['radius', /^(radius|size|range)$/i],
];

/**
 * Build a plausible input object for a god tool from its JSON schema plus hints such as
 * {player, message, item, count, effect, duration, level, power, time, words: ['cage']}.
 * Explicit `fixed` values always win. Optional props are only filled when a hint targets them.
 */
export function buildInput(toolDef, hints = {}, fixed = {}) {
  const schema = toolDef?.input_schema ?? toolDef?.inputSchema ?? {};
  const props = schema.properties ?? {};
  const required = new Set(schema.required ?? []);
  const words = (hints.words ?? []).map((w) => String(w).toLowerCase());
  const out = {};
  for (const [key, ps] of Object.entries(props)) {
    if (key in fixed) continue;
    let v;
    if (key in hints && key !== 'words') v = hints[key];
    if (v === undefined) {
      const role = ROLE_PATTERNS.find(([, re]) => re.test(key))?.[0];
      if (role === 'durationTicks' && hints.duration !== undefined) v = hints.duration * 20;
      else if (role && hints[role] !== undefined) v = hints[role];
    }
    if (Array.isArray(ps.enum)) {
      const exact = v === undefined ? undefined : ps.enum.find((e) => String(e).toLowerCase() === String(v).toLowerCase());
      const match = exact ?? ps.enum.find((e) => words.some((w) => String(e).toLowerCase().includes(w)));
      v = match ?? (required.has(key) ? ps.enum[0] : undefined);
    } else if (v === undefined && words.length && (ps.type === 'string' || !ps.type) && /^(mode|kind|type|form|shape|style|what|structure|variant|preset)$/i.test(key)) {
      v = words[0];
    }
    if (v === undefined && required.has(key)) v = defaultFor(ps);
    if (v !== undefined) out[key] = coerce(v, ps);
  }
  return { ...out, ...fixed };
}

function defaultFor(ps) {
  if (ps.default !== undefined) return ps.default;
  switch (ps.type) {
    case 'number':
    case 'integer':
      return ps.minimum ?? 1;
    case 'boolean':
      return false;
    case 'array':
      return [];
    case 'object':
      return {};
    default:
      return '';
  }
}

function coerce(v, ps) {
  const t = Array.isArray(ps.type) ? ps.type[0] : ps.type;
  if ((t === 'number' || t === 'integer') && typeof v === 'string' && v.trim() !== '' && !Number.isNaN(Number(v))) return Number(v);
  if (t === 'string' && typeof v === 'number') return String(v);
  if (t === 'array' && !Array.isArray(v)) return [v];
  return v;
}

// ---------- text correlation ----------

export function words(s) {
  return new Set(
    String(s ?? '')
      .replace(/§./g, '')
      .toLowerCase()
      .split(/[^\p{L}\p{N}']+/u)
      .filter((w) => w.length >= 4),
  );
}

/** True when `text` plausibly contains (part of) `speech`. */
export function overlaps(speech, text) {
  const a = words(speech);
  const b = words(text);
  if (!a.size) {
    const needle = String(speech ?? '').replace(/§./g, '').trim().toLowerCase();
    return !!needle && String(text ?? '').toLowerCase().includes(needle);
  }
  let hit = 0;
  for (const w of a) if (b.has(w)) hit++;
  return hit >= Math.min(3, a.size);
}

/** Kinds through which the god's speech can reach a player. */
export const SPEECH_KINDS = ['system', 'chat', 'title', 'subtitle', 'actionbar', 'display_text', 'bossbar'];
/** Kinds that count as "the bot noticed something happen". */
export const OBSERVABLE_KINDS = [
  ...SPEECH_KINDS,
  'entity_spawn',
  'sound',
  'particle',
  'blocks',
  'effect',
  'health',
  'damage',
  'teleport',
  'weather',
  'time',
  'inventory',
];
