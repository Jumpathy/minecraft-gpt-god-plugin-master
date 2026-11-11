// Bridge smoke test: every read endpoint answers, a direct god power reaches a bot, views render.

import { isPng } from '../bridge.js';
import { buildInput, pollUntil, short, SPEECH_KINDS, OBSERVABLE_KINDS } from './lib.js';

export const description = 'bridge endpoints reachable; a direct whisper/omen reaches the bot; overhead view renders a PNG';

export async function run(ctx) {
  const { bridge, bots, botName } = ctx;

  await ctx.step('bridge config resolves', async (s) => {
    const r = await bridge.resolve();
    s.detail = `${r.baseUrl} (from ${r.source})`;
  });

  const status = await ctx.step('GET /status', async (s) => {
    const st = await bridge.status();
    ctx.assert(typeof st.awake === 'boolean', `status.awake missing: ${short(st)}`);
    ctx.assert(Array.isArray(st.players), 'status.players is not an array');
    s.detail = `mode=${st.mode} model=${st.model} awake=${st.awake} paused=${st.paused} keyConfigured=${st.keyConfigured} persona=${st.persona?.name ?? '?'} journalSeq=${st.journalSeq}`;
    return st;
  });

  const tools = await ctx.step('GET /tools lists god powers', async (s) => {
    const t = (await bridge.tools()).tools;
    ctx.assert(Array.isArray(t) && t.length > 0, 'no tools returned');
    ctx.assert(t.every((x) => x.name && x.input_schema), 'every tool needs name + input_schema');
    s.detail = `${t.length}: ${t.map((x) => x.name).join(', ')}`;
    ctx._tools = t;
    return t;
  });

  await ctx.step('GET /journal', async (s) => {
    const j = await bridge.journal({ limit: 5 });
    ctx.assert(Array.isArray(j.entries), 'entries is not an array');
    ctx.assert(typeof j.latestSeq === 'number', 'latestSeq is not a number');
    s.detail = `latestSeq=${j.latestSeq}, kinds=${[...new Set(j.entries.map((e) => e.kind))].join(',') || '-'}`;
  });

  await ctx.step('GET /memory', async (s) => {
    const m = await bridge.memory();
    ctx.assert(m && typeof m === 'object', 'memory is not an object');
    s.detail = `keys: ${Object.keys(m).join(', ')}`;
  });

  await ctx.step('GET /perception (consume=false)', async (s) => {
    const p = await bridge.perception({ consume: false });
    ctx.assert(typeof p.text === 'string', 'perception.text is not a string');
    s.detail = `${p.text.length} chars`;
  });

  await ctx.step(`spawn bot ${botName}`, async (s) => {
    const r = await ctx.ensureBot();
    s.detail = `${r.already ? 'reused' : 'spawned'} at ${JSON.stringify(r.position)}`;
  });

  await ctx.step('bot appears in /status players', async (s) => {
    const st = await pollUntil(async () => {
      const x = await bridge.status();
      return x.players?.some((p) => p.name === botName) ? x : null;
    });
    ctx.assert(st, `${botName} not listed in /status players within 10s`);
    const p = st.players.find((x) => x.name === botName);
    s.detail = `${p.world} (${p.x}, ${p.y}, ${p.z}) hp=${p.health} ${p.gamemode}`;
  });

  await ctx.step('POST /console runs a command', async (s) => {
    const r = await bridge.console('list');
    ctx.assert(r.ok !== false, `console returned ${short(r)}`);
    ctx.assert(Array.isArray(r.feedback), 'feedback is not an array');
    s.detail = short(r.feedback.join(' / '), 160) || '(no feedback)';
  });

  // Direct power: prefer whisper (checkable by token), fall back to omen (any visible effect).
  const hasWhisper = tools.some((t) => t.name === 'whisper');
  if (hasWhisper) {
    await ctx.step('god_tool whisper reaches the bot', async (s) => {
      const token = `harness-${Math.random().toString(36).slice(2, 8)}`;
      const def = tools.find((t) => t.name === 'whisper');
      const input = buildInput(def, { player: botName, message: `Mortal, the word is ${token}.` });
      const since = bots.latestSeq();
      await ctx.callTool('whisper', input, s);
      const w = await ctx.observe({ since, kinds: SPEECH_KINDS, pattern: token, timeoutSec: 15 });
      ctx.assert(w.matched, `bot never saw the token ${token} (input ${JSON.stringify(input)}); recent: ${short(w.recentSinceStart)}`);
      s.detail = `${w.entry.kind}: ${short(w.entry.text, 140)}`;
    });
  }
  if (!hasWhisper || tools.some((t) => t.name === 'omen')) {
    await ctx.step('god_tool omen is visible to the bot', async (s) => {
      const def = await ctx.requireTool('omen');
      const input = buildInput(def, { player: botName, message: 'A sign for the harness', words: ['thunder', 'lightning'] });
      const since = bots.latestSeq();
      await ctx.callTool('omen', input, s);
      const w = await ctx.observe({ since, kinds: OBSERVABLE_KINDS, timeoutSec: 10 });
      ctx.assert(w.matched, `no observable effect within 10s (input ${JSON.stringify(input)})`);
      s.detail = `${w.entry.kind}: ${short(w.entry.text, 140)}`;
    }, { optional: hasWhisper });
  }

  await ctx.step('god_tool journals the call', async (s) => {
    const j = await bridge.journal({ since: status.journalSeq ?? 0, limit: 200 });
    const hit = j.entries.find((e) => e.kind === 'tool');
    ctx.assert(hit, `no 'tool' journal entry since seq ${status.journalSeq}`);
    s.detail = short(hit.text, 160);
  }, { optional: true });

  await ctx.step('POST /event accepted', async () => {
    const r = await bridge.event(`[harness] ${botName} is running the bridge smoke test`);
    ctx.assert(r.ok !== false, `event returned ${short(r)}`);
  });

  await ctx.step('GET /view overhead around the bot returns a PNG', async (s) => {
    const { png, caption } = await bridge.view({ type: 'overhead', target: botName, radius: 16 });
    ctx.assert(isPng(png), `not a PNG (${png.length} bytes, starts ${png.subarray(0, 8).toString('hex')})`);
    const file = await ctx.saveView('overhead', png);
    s.detail = `${png.length} bytes → ${file}; caption: ${short(caption, 120)}`;
  });

  await ctx.step('unknown tool is reported as an error', async (s) => {
    try {
      const r = await bridge.tool('definitely_not_a_god_power', {});
      ctx.assert(r.isError || r.ok === false, `expected isError, got ${short(r)}`);
      s.detail = `isError: ${short(r.result, 100)}`;
    } catch (e) {
      ctx.assert(e.status >= 400 && e.status < 500, `expected a 4xx, got ${e.message}`);
      s.detail = e.message;
    }
  });
}
