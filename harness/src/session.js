// A test session's record: one merged, time-ordered timeline of what the simulated players did and perceived and
// what the god perceived, thought, did and said, written to harness/out/session-<stamp>-<label>/.
//   timeline.jsonl  every entry as JSON ({t, src, kind, text})
//   timeline.md     the same, readable (written by report())
//   shots/          screenshots taken with session-aware tools
// Sources: the god's journal (bridge GET /journal), every bot's event log, every real client's puppet event log,
// and notes the test driver adds (session_note). Polled every 2 s; nothing here costs API money.

import { appendFile, mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { OUT_DIR } from './client.js';
import { HarnessError } from './util.js';

const BOT_KINDS = new Set(['chat', 'system', 'actionbar', 'title', 'subtitle', 'display_text', 'bossbar', 'death', 'respawn', 'damage', 'effect', 'teleport', 'gamemode', 'weather', 'kicked', 'action', 'inventory']);
const clip = (s, n) => (s.length > n ? `${s.slice(0, n)} … [+${s.length - n} chars]` : s);

export class Session {
  constructor({ sandbox, bridge, bots, players, budget }) {
    Object.assign(this, { sandbox, bridge, bots, players, budget });
    this.dir = null;
    this.timer = null;
    this.entries = [];
    this.cursors = { journal: 0, bots: 0, players: new Map() };
    this.startedAt = null;
    this.busy = false;
  }

  get active() {
    return !!this.timer;
  }

  async start({ label = 'e2e', fromNow = true } = {}) {
    if (this.active) await this.stop();
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const safe = String(label).replace(/[^A-Za-z0-9_-]+/g, '_').slice(0, 40) || 'e2e';
    this.dir = path.join(OUT_DIR(), `session-${stamp}-${safe}`);
    await mkdir(path.join(this.dir, 'shots'), { recursive: true });
    this.entries = [];
    this.startedAt = Date.now();
    this.cursors = { journal: 0, bots: 0, players: new Map() };
    if (fromNow) {
      this.cursors.journal = await this.bridge.journalHead().catch(() => 0);
      this.cursors.bots = this.bots.latestSeq?.() ?? 0;
    }
    this.costAtStart = this.budget.spent();
    this.timer = setInterval(() => this.poll().catch(() => {}), 2000);
    this.timer.unref?.();
    await this.add('driver', 'note', `session "${label}" started`);
    return { ok: true, dir: this.dir };
  }

  async add(src, kind, text, t = Date.now()) {
    const e = { t, src, kind, text: String(text ?? '') };
    this.entries.push(e);
    if (this.dir) await appendFile(path.join(this.dir, 'timeline.jsonl'), JSON.stringify(e) + '\n').catch(() => {});
    return e;
  }

  async note(text) {
    if (!this.dir) throw new HarnessError('no session (session_start first)');
    await this.add('driver', 'note', text);
    return { ok: true };
  }

  shotPath(label) {
    if (!this.dir) return null;
    const stamp = new Date().toISOString().slice(11, 19).replace(/:/g, '');
    const safe = String(label ?? 'shot').replace(/[^A-Za-z0-9_-]+/g, '_').slice(0, 50) || 'shot';
    return path.join(this.dir, 'shots', `${stamp}-${safe}.png`);
  }

  async poll() {
    if (this.busy || !this.dir) return;
    this.busy = true;
    try {
      // the god's journal
      for (let i = 0; i < 5; i++) {
        const j = await this.bridge.journal({ since: this.cursors.journal, limit: 200 }).catch(() => null);
        if (!j?.entries?.length) break;
        for (const e of j.entries) {
          this.cursors.journal = Math.max(this.cursors.journal, e.seq);
          await this.add('god', e.kind, e.text, e.time);
        }
        if (!j.more) break;
      }
      // bots
      const fresh = [];
      for (const h of this.bots.bots.values()) for (const e of h.log) if (e.seq > this.cursors.bots) fresh.push([h.username, e]);
      fresh.sort((a, b) => a[1].seq - b[1].seq);
      for (const [name, e] of fresh) {
        this.cursors.bots = Math.max(this.cursors.bots, e.seq);
        if (!BOT_KINDS.has(e.kind)) continue;
        await this.add(`bot:${name}`, e.kind, e.text, Date.parse(e.t) || Date.now());
      }
      // real clients
      for (const p of this.players.all()) {
        if (!p.puppetReady) continue;
        const since = this.cursors.players.get(p.name) ?? 0;
        const r = await p.call('GET', `/events?since=${since}&limit=300`).catch(() => null);
        for (const e of r?.events ?? []) {
          this.cursors.players.set(p.name, Math.max(this.cursors.players.get(p.name) ?? 0, e.seq));
          await this.add(`player:${p.name}`, e.kind, e.text);
        }
      }
    } finally {
      this.busy = false;
    }
  }

  async stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    await this.poll().catch(() => {});
    return { ok: true, dir: this.dir };
  }

  /** Writes timeline.md and returns counts: thoughts, powers used, speech, vision use, cost. */
  async report({ perceptionChars = 1500 } = {}) {
    if (!this.dir) throw new HarnessError('no session (session_start first)');
    await this.poll().catch(() => {});
    const sorted = [...this.entries].sort((a, b) => a.t - b.t);
    const t0 = this.startedAt;
    const rel = (t) => {
      const s = Math.max(0, Math.round((t - t0) / 1000));
      return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
    };
    const tools = {};
    const stats = { cycles: 0, thoughts: 0, speech: 0, errors: 0, playerLines: 0 };
    const lines = [`# Session timeline`, '', `Started ${new Date(t0).toISOString()}. Times are minutes:seconds since the start.`, ''];
    for (const e of sorted) {
      let text = e.text.replace(/\r/g, '');
      if (e.src === 'god') {
        if (e.kind === 'cycle') stats.cycles++;
        if (e.kind === 'thought') stats.thoughts++;
        if (e.kind === 'speech') stats.speech++;
        if (e.kind === 'error') stats.errors++;
        if (e.kind === 'tool') {
          const name = /^([a-z_0-9]+)/i.exec(text.replace(/^[^a-z]*/i, ''))?.[1] ?? '?';
          tools[name] = (tools[name] ?? 0) + 1;
        }
        if (e.kind === 'perception') text = clip(text, perceptionChars);
        const block = text.includes('\n') ? `\n\n${text.split('\n').map((l) => `    ${l}`).join('\n')}\n` : ` ${text}`;
        lines.push(`- \`${rel(e.t)}\` **GOD ${e.kind}**${block}`);
      } else if (e.src === 'driver') {
        lines.push('', `### \`${rel(e.t)}\` ${text}`, '');
      } else {
        stats.playerLines++;
        lines.push(`- \`${rel(e.t)}\` ${e.src} _${e.kind}_: ${clip(text.replace(/\n/g, ' ⏎ '), 400)}`);
      }
    }
    const b = await this.budget.status({ fresh: true }).catch(() => null);
    const summary = {
      ok: true,
      dir: this.dir,
      timeline: path.join(this.dir, 'timeline.md'),
      minutes: Math.round((Date.now() - t0) / 6000) / 10,
      entries: sorted.length,
      ...stats,
      powersUsed: Object.fromEntries(Object.entries(tools).sort((a, b2) => b2[1] - a[1])),
      visionUses: Object.entries(tools).filter(([k]) => /^(look|see_build|map|survey_area|locate|vessel_look|panorama)/.test(k)).reduce((n, [, v]) => n + v, 0),
      costUsd: b ? Math.round((b.spentUsd - (this.costAtStart ?? 0)) * 10000) / 10000 : undefined,
      totalSpentUsd: b?.spentUsd,
    };
    lines.push('', '## Summary', '', '```json', JSON.stringify(summary, null, 2), '```', '');
    await writeFile(summary.timeline, lines.join('\n'));
    return summary;
  }
}
