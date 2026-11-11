// Prayer test (needs a live Claude API key): /pray, then wait for the god to think and answer the bot.

import { sleep } from '../util.js';
import { overlaps, short, SPEECH_KINDS } from './lib.js';

export const description = '/pray → journal shows a cycle and speech → the speech reaches the bot (needs the API key and brain.mode live; spends API money)';

const ANSWER_TIMEOUT_MS = 90_000;

export async function run(ctx) {
  const { bridge, bots, botName } = ctx;

  await ctx.step('god is configured to answer', async (s) => {
    const st = await bridge.status();
    if (!st.keyConfigured) {
      ctx.skip('no Claude API key configured (/status keyConfigured=false). Only you set it: run the server in your own terminal (node harness/sandbox.js start) and type "gptgod setkey <key>" at its console. Full-loop tests spend real API money.');
    }
    if (st.mode !== 'live') {
      ctx.skip(`god is in "${st.mode}" mode, so nothing will answer the prayer by itself (sandbox_configure brainMode=live, then restart)`);
    }
    s.detail = `mode=${st.mode} model=${st.model} awake=${st.awake} paused=${st.paused}`;
    if (st.paused) s.detail += ' (paused — the prayer may not be answered)';
  });

  await ctx.step(`spawn bot ${botName}`, async (s) => {
    const r = await ctx.ensureBot();
    s.detail = `at ${JSON.stringify(r.position)}`;
  });

  const head = await bridge.journalHead();
  const prayerSeq = bots.latestSeq();
  const prayer = `Great one, I am ${botName}. Please speak to me and tell me what you want from me.`;
  const t0 = Date.now();
  const remaining = () => Math.max(1000, ANSWER_TIMEOUT_MS - (Date.now() - t0));

  await ctx.step('/pray command is accepted', async (s) => {
    bots.say(botName, `/pray ${prayer}`);
    const bad = await ctx.observe({
      since: prayerSeq,
      kinds: ['system'],
      pattern: 'unknown (or incomplete )?command|do not have permission|usage: /pray',
      timeoutSec: 3,
    });
    ctx.assert(!bad.matched, `server rejected /pray: ${bad.entry?.text}`);
    s.detail = `prayed (journal head ${head}, bot seq ${prayerSeq})`;
  });

  const speech = await ctx.step(`god speaks within ${ANSWER_TIMEOUT_MS / 1000}s (journal 'speech')`, async (s) => {
    const w = await bridge.waitForJournal({ since: head, kinds: ['speech'], timeoutMs: remaining() });
    if (!w.matched) {
      const got = bots.events({ username: botName, since: prayerSeq, kinds: SPEECH_KINDS }).entries;
      const errors = (await bridge.journal({ since: head, limit: 200 })).entries.filter((e) => e.kind === 'error');
      throw new Error(
        `no speech entry in the journal; journal errors: ${short(errors.map((e) => e.text)) || 'none'}; ` +
          `bot received: ${short(got.map((e) => `[${e.kind}] ${e.text}`)) || 'nothing'}`,
      );
    }
    s.detail = short(w.entry.text, 160);
    return w.entry;
  });

  await ctx.step('journal shows a cycle', async (s) => {
    const w = await bridge.waitForJournal({ since: head, kinds: ['cycle'], timeoutMs: 15_000 });
    ctx.assert(w.matched, 'no cycle entry in the journal after the prayer');
    s.detail = short(w.entry.text, 160);
  });

  await ctx.step('the god speech reaches the bot', async (s) => {
    const deadline = Date.now() + 15_000;
    for (;;) {
      const got = bots.events({ username: botName, since: prayerSeq, kinds: SPEECH_KINDS, limit: 500 }).entries.filter(
        (e) => !(e.kind === 'chat' && e.from === botName),
      );
      const hit = got.find((e) => overlaps(speech.text, e.text));
      if (hit) {
        s.detail = `[${hit.kind}] ${short(hit.text, 160)}`;
        return;
      }
      if (Date.now() > deadline) {
        throw new Error(
          `bot saw nothing resembling the speech "${short(speech.text, 120)}"; it received: ${short(got.map((e) => `[${e.kind}] ${e.text}`)) || 'nothing'}`,
        );
      }
      await sleep(500);
    }
  });
}
