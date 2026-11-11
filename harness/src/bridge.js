// Client for the GPTGOD plugin's local HTTP debug bridge.
//
// Config resolution (first match wins):
//   1. GOD_BRIDGE_URL + GOD_BRIDGE_TOKEN env vars (or harness/.env) — the way to reach a remote server
//   2. GOD_BRIDGE_FILE env var (path to a bridge.json)
//   3. <sandbox>/plugins/gptgodmc/bridge.json (sandbox = GOD_SANDBOX_DIR, default <repo>/../minecraft/srv1)
// The discovery file is re-read whenever it changes, so the harness can start before the server.

import { readFile, stat } from 'node:fs/promises';
import { bridgeFilePath } from './sandbox.js';
import { compileRegex, sleep } from './util.js';
const DEFAULT_TIMEOUT_MS = 15_000;

export class BridgeError extends Error {
  constructor(message, { status, body, code, method, route } = {}) {
    super(message);
    this.name = 'BridgeError';
    this.status = status; // HTTP status, if a response arrived
    this.body = body; // parsed error body, if any
    this.code = code; // e.g. NO_CONFIG, UNREACHABLE, TIMEOUT, HTTP_401
    this.method = method;
    this.route = route;
  }

  toJSON() {
    return { error: this.message, code: this.code, status: this.status, body: this.body };
  }
}

export class BridgeClient {
  constructor({ url, token, file, timeoutMs } = {}) {
    this.envUrl = url ?? process.env.GOD_BRIDGE_URL ?? null;
    this.envToken = token ?? process.env.GOD_BRIDGE_TOKEN ?? null;
    this.fixedFile = file ?? null;
    this.timeoutMs = timeoutMs ?? DEFAULT_TIMEOUT_MS;
    this._cache = null; // { mtimeMs, size, baseUrl, token }
  }

  /** bridge.json path: explicit, else GOD_BRIDGE_FILE, else the sandbox's (read lazily so env changes apply). */
  get file() {
    return this.fixedFile ?? process.env.GOD_BRIDGE_FILE ?? bridgeFilePath();
  }

  set file(v) {
    this.fixedFile = v;
    this._cache = null;
  }

  /** Resolve {baseUrl, token, source}. Throws BridgeError(NO_CONFIG) if nothing is available. */
  async resolve() {
    if (this.envUrl) {
      if (!this.envToken) {
        throw new BridgeError('GOD_BRIDGE_URL is set but GOD_BRIDGE_TOKEN is not', { code: 'NO_CONFIG' });
      }
      return { baseUrl: this.envUrl.replace(/\/+$/, ''), token: this.envToken, source: 'env' };
    }
    let st;
    try {
      st = await stat(this.file);
    } catch {
      this._cache = null;
      throw new BridgeError(
        `Bridge discovery file not found: ${this.file}. Is the server running with bridge.enabled=true? ` +
          '(or set GOD_BRIDGE_URL + GOD_BRIDGE_TOKEN / GOD_BRIDGE_FILE)',
        { code: 'NO_CONFIG' },
      );
    }
    if (!this._cache || this._cache.mtimeMs !== st.mtimeMs || this._cache.size !== st.size) {
      let parsed;
      try {
        parsed = JSON.parse(await readFile(this.file, 'utf8'));
      } catch (e) {
        throw new BridgeError(`Could not parse ${this.file}: ${e.message}`, { code: 'NO_CONFIG' });
      }
      if (!parsed || !parsed.port || !parsed.token) {
        throw new BridgeError(`${this.file} must contain {"port": <n>, "token": "..."}`, { code: 'NO_CONFIG' });
      }
      const host = parsed.host || '127.0.0.1';
      this._cache = { mtimeMs: st.mtimeMs, size: st.size, baseUrl: `http://${host}:${parsed.port}`, token: parsed.token };
    }
    return { baseUrl: this._cache.baseUrl, token: this._cache.token, source: this.file };
  }

  /** Low-level request. Returns the fetch Response (already checked for 2xx). */
  async _fetch(method, route, { query, body, timeoutMs } = {}) {
    const { baseUrl, token } = await this.resolve();
    let url = baseUrl + route;
    if (query) {
      const qs = new URLSearchParams();
      for (const [k, v] of Object.entries(query)) if (v !== undefined && v !== null) qs.set(k, String(v));
      const s = qs.toString();
      if (s) url += '?' + s;
    }
    const ms = timeoutMs ?? this.timeoutMs;
    const headers = { Authorization: `Bearer ${token}`, Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let res;
    try {
      res = await fetch(url, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(ms),
      });
    } catch (e) {
      // Force a re-read of bridge.json next time (server may have restarted with a new port/token).
      this._cache = null;
      if (e.name === 'TimeoutError' || e.name === 'AbortError') {
        throw new BridgeError(`${method} ${route} timed out after ${ms}ms`, { code: 'TIMEOUT', method, route });
      }
      const cause = e.cause?.code || e.cause?.message || e.message;
      const remote = !isLoopback(baseUrl);
      const hint = remote
        ? 'Is the server running with bridge.enabled=true and bridge.bind=0.0.0.0, and is the port open in its firewall?'
        : 'Is the Minecraft server running?';
      throw new BridgeError(`Cannot reach god bridge at ${baseUrl} (${cause}). ${hint}`, {
        code: 'UNREACHABLE',
        method,
        route,
      });
    }
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      let parsed = null;
      try {
        parsed = JSON.parse(text);
      } catch {
        /* not JSON */
      }
      if (res.status === 401 || res.status === 403) this._cache = null;
      const detail = parsed?.error ?? (text.slice(0, 300) || res.statusText);
      throw new BridgeError(`${method} ${route} → HTTP ${res.status}: ${detail}`, {
        status: res.status,
        body: parsed ?? text,
        code: `HTTP_${res.status}`,
        method,
        route,
      });
    }
    return res;
  }

  async request(method, route, opts = {}) {
    const res = await this._fetch(method, route, opts);
    const text = await res.text();
    if (!text) return {};
    try {
      return JSON.parse(text);
    } catch {
      throw new BridgeError(`${method} ${route} returned non-JSON: ${text.slice(0, 200)}`, {
        status: res.status,
        code: 'BAD_JSON',
        method,
        route,
      });
    }
  }

  status() {
    return this.request('GET', '/status');
  }

  journal({ since, limit } = {}) {
    return this.request('GET', '/journal', { query: { since, limit } });
  }

  perception({ consume = false } = {}) {
    return this.request('GET', '/perception', { query: { consume: consume ? 'true' : 'false' } });
  }

  tools() {
    return this.request('GET', '/tools');
  }

  memory() {
    return this.request('GET', '/memory');
  }

  wake(directive) {
    return this.request('POST', '/wake', { body: directive ? { directive } : {} });
  }

  tool(name, input = {}, { timeoutMs } = {}) {
    return this.request('POST', '/tool', { body: { name, input }, timeoutMs: timeoutMs ?? 30_000 });
  }

  event(text) {
    return this.request('POST', '/event', { body: { text } });
  }

  console(command) {
    return this.request('POST', '/console', { body: { command: String(command).replace(/^\//, '') } });
  }

  resetMemory() {
    return this.request('POST', '/memory/reset', { body: {} });
  }

  /**
   * Render a "god vision" image. params: {type, target?, xray?, radius?}.
   * Returns {png: Buffer, caption: string, contentType}.
   */
  async view({ type, target, xray, radius, timeoutMs } = {}) {
    const query = { type, target, xray: xray === undefined ? undefined : xray ? 'true' : 'false', radius };
    const res = await this._fetch('GET', '/view', { query, timeoutMs: timeoutMs ?? 60_000 });
    const contentType = res.headers.get('content-type') || '';
    const png = Buffer.from(await res.arrayBuffer());
    if (!contentType.includes('image/png')) {
      throw new BridgeError(`GET /view returned ${contentType || 'no content type'} instead of image/png`, {
        status: res.status,
        body: png.toString('utf8', 0, Math.min(png.length, 300)),
        code: 'BAD_CONTENT_TYPE',
        method: 'GET',
        route: '/view',
      });
    }
    const rawCaption = res.headers.get('x-view-caption') || '';
    let caption = rawCaption;
    try {
      caption = decodeURIComponent(rawCaption.replace(/\+/g, ' '));
    } catch {
      /* keep raw */
    }
    return { png, caption, contentType };
  }

  /** Current journal head (seq of the newest entry). */
  async journalHead() {
    const st = await this.status();
    if (typeof st.journalSeq === 'number') return st.journalSeq;
    return (await this.journal({ limit: 1 })).latestSeq ?? 0;
  }

  /**
   * Poll /journal until an entry with seq > since matches (kinds and/or pattern).
   * Transient bridge errors are retried until the timeout.
   */
  async waitForJournal({ since, kinds, pattern, flags = 'i', timeoutMs = 60_000, pollMs = 1000, signal, onProgress } = {}) {
    const re = pattern ? compileRegex(pattern, flags) : null;
    const kindSet = kinds?.length ? new Set(kinds) : null;
    const t0 = Date.now();
    let cursor = since ?? (await this.journalHead());
    const startSeq = cursor;
    const recent = [];
    let lastError = null;
    let lastProgress = t0;
    for (;;) {
      try {
        let res;
        do {
          res = await this.journal({ since: cursor, limit: 200 });
          const entries = res.entries ?? [];
          for (const e of entries) {
            if (e.seq <= cursor) continue;
            cursor = e.seq;
            recent.push(e);
            if (recent.length > 10) recent.shift();
            if (kindSet && !kindSet.has(e.kind)) continue;
            if (re && !re.test(e.text ?? '')) continue;
            return { ok: true, matched: true, entry: e, startSeq, waitedMs: Date.now() - t0 };
          }
          // Page forward while full pages keep arriving.
          if (entries.length < 200) break;
        } while (Date.now() - t0 < timeoutMs);
        lastError = null;
      } catch (e) {
        lastError = e.message;
      }
      const waited = Date.now() - t0;
      if (signal?.aborted) return { ok: false, matched: false, cancelled: true, startSeq, waitedMs: waited };
      if (waited >= timeoutMs) {
        return { ok: false, matched: false, timedOut: true, startSeq, cursor, waitedMs: waited, lastError, recentEntries: recent };
      }
      if (onProgress && Date.now() - lastProgress >= 5000) {
        lastProgress = Date.now();
        try {
          onProgress(waited, timeoutMs);
        } catch {
          /* ignore */
        }
      }
      await sleep(Math.min(pollMs, timeoutMs - waited));
    }
  }
}

function isLoopback(url) {
  try {
    const h = new URL(url).hostname;
    return h === 'localhost' || h === '[::1]' || h === '::1' || h.startsWith('127.');
  } catch {
    return false;
  }
}

export const PNG_MAGIC =Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

export function isPng(buf) {
  return Buffer.isBuffer(buf) && buf.length > PNG_MAGIC.length && buf.subarray(0, 8).equals(PNG_MAGIC);
}
