// The spend guard: caps what the god's own API calls may cost during a test.
//
// The plugin reports its estimated cost since the server started (bridge GET /status, usage.estimatedCostUsd).
// The guard polls it, keeps a ledger in <sandbox>/plugins/gptgodmc/harness-budget.json that survives server and
// harness restarts (a restart resets the plugin's counter, so the ledger carries the earlier spend), and when the
// spend reaches `stopAtUsd` it pauses every god ("gptgod pause") and sets brain.mode off so a restart cannot resume it.
// A thought already in flight still finishes, so stopAtUsd defaults to a little under the cap.

import { readFile, writeFile } from 'node:fs/promises';
import { sandboxFile } from './sandbox.js';
import { HarnessError } from './util.js';

const round = (n) => Math.round(n * 10000) / 10000;

export class Budget {
  constructor({ sandbox, bridge, log = () => {} }) {
    this.sandbox = sandbox;
    this.bridge = bridge;
    this.log = log;
    this.timer = null;
    this.state = null; // {capUsd, stopAtUsd, baseUsd, lastSeenUsd, tripped, samples: []}
    this.lastUsage = null;
    this.lastError = null;
  }

  file() {
    return sandboxFile('plugins', 'gptgodmc', 'harness-budget.json');
  }

  async load() {
    if (this.state) return this.state;
    try {
      this.state = JSON.parse(await readFile(this.file(), 'utf8'));
    } catch {
      this.state = { capUsd: 0, stopAtUsd: 0, baseUsd: 0, lastSeenUsd: 0, tripped: null };
    }
    return this.state;
  }

  async save() {
    await writeFile(this.file(), JSON.stringify(this.state, null, 2)).catch(() => {});
  }

  spent() {
    return this.state ? round(this.state.baseUsd + this.state.lastSeenUsd) : 0;
  }

  /** Sets the cap and starts the watchdog. reset=true forgets the spend recorded so far. */
  async set({ capUsd, stopAtUsd, reset = false, pollMs = 4000 } = {}) {
    if (!(capUsd > 0)) throw new HarnessError('capUsd must be > 0');
    const s = await this.load();
    if (reset) Object.assign(s, { baseUsd: 0, lastSeenUsd: 0, tripped: null });
    s.capUsd = capUsd;
    s.stopAtUsd = stopAtUsd > 0 ? Math.min(stopAtUsd, capUsd) : round(capUsd * 0.87);
    if (s.tripped && this.spent() < s.stopAtUsd) s.tripped = null;
    await this.save();
    this.stop();
    this.timer = setInterval(() => this.poll().catch((e) => (this.lastError = e.message)), pollMs);
    this.timer.unref?.();
    await this.poll().catch((e) => (this.lastError = e.message));
    return this.status();
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  /** Reads the plugin's counter once; trips the guard when the spend reaches stopAtUsd. */
  async poll() {
    const s = await this.load();
    const st = await this.bridge.status();
    const usage = st.usage ?? {};
    const cost = Number(usage.estimatedCostUsd ?? 0);
    this.lastUsage = usage;
    this.lastError = null;
    if (cost + 1e-9 < s.lastSeenUsd) s.baseUsd = round(s.baseUsd + s.lastSeenUsd); // the server restarted: its counter began again
    if (cost !== s.lastSeenUsd) {
      s.lastSeenUsd = cost;
      await this.save();
    }
    if (s.capUsd > 0 && !s.tripped && this.spent() >= s.stopAtUsd) await this.trip(`spent $${this.spent()} of the $${s.capUsd} cap`);
    return this.spent();
  }

  async trip(reason) {
    const s = this.state;
    s.tripped = { at: new Date().toISOString(), reason, spentUsd: this.spent() };
    this.log(`BUDGET: ${reason}; pausing the god`);
    const done = [];
    done.push(await this.bridge.console('gptgod pause').then((r) => r.feedback ?? r.output ?? 'paused', (e) => `pause failed: ${e.message}`));
    await this.sandbox.configure({ brainMode: 'off' }).then(() => done.push('brain.mode off written'), (e) => done.push(`config: ${e.message}`));
    done.push(await this.bridge.console('gptgod reload').then((r) => r.feedback ?? r.output ?? 'reloaded', (e) => `reload failed: ${e.message}`));
    s.tripped.actions = done;
    await this.save();
  }

  async status({ fresh = false } = {}) {
    const s = await this.load();
    if (fresh) await this.poll().catch((e) => (this.lastError = e.message));
    return {
      ok: true,
      capUsd: s.capUsd,
      stopAtUsd: s.stopAtUsd,
      spentUsd: this.spent(),
      remainingUsd: s.capUsd > 0 ? round(s.capUsd - this.spent()) : null,
      sinceServerStartUsd: round(s.lastSeenUsd),
      earlierRunsUsd: round(s.baseUsd),
      watching: !!this.timer,
      tripped: s.tripped ?? undefined,
      usage: this.lastUsage ?? undefined,
      lastError: this.lastError ?? undefined,
      ledger: this.file(),
    };
  }
}
