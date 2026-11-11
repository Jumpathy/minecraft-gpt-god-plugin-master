// Vessel test: summon the god's physical vessel next to the bot, see it, touch it, check the journal.

import { sleep } from '../util.js';
import { pollUntil, short } from './lib.js';

export const description = 'vessel_go teleports the vessel to the bot; bot sees display entities; right-click is journaled';

const NEAR = 10;

export async function run(ctx) {
  const { bridge, bots, botName } = ctx;

  await ctx.step(`spawn bot ${botName}`, async (s) => {
    const r = await ctx.ensureBot();
    s.detail = `at ${JSON.stringify(r.position)}`;
  });

  await ctx.step('vessel_go {target: bot, mode: teleport}', async (s) => {
    await ctx.requireTool('vessel_go');
    await ctx.callTool('vessel_go', { target: botName, mode: 'teleport' }, s);
  });

  await ctx.step(`bot sees block_display entities within ${NEAR} blocks`, async (s) => {
    const seen = await pollUntil(() => {
      const n = bots.nearby(botName, { radius: NEAR, type: 'block_display' });
      return n.total > 0 ? n : null;
    }, { timeoutMs: 15_000 });
    ctx.assert(seen, `no block_display within ${NEAR} blocks after 15s; nearby: ${short(bots.nearby(botName, { radius: 32 }).counts)}`);
    const all = bots.nearby(botName, { radius: NEAR });
    s.detail = `nearest ${seen.entities[0].distance}m; within ${NEAR}m: ${JSON.stringify(all.counts)}`;
  });

  await ctx.step('/status reports the vessel manifested near the bot', async (s) => {
    const st = await bridge.status();
    ctx.assert(st.vessel?.manifested, `vessel.manifested is ${st.vessel?.manifested}`);
    const me = bots.status(botName).position;
    const v = st.vessel;
    const d = Math.hypot(v.x - me.x, v.y - me.y, v.z - me.z);
    s.detail = `vessel at (${v.x}, ${v.y}, ${v.z}) in ${v.world}, ${d.toFixed(1)}m from bot, following=${v.following ?? '-'}`;
    ctx.assert(d <= NEAR + 6, `vessel is ${d.toFixed(1)}m from the bot`);
  }, { optional: true });

  const interaction = await ctx.step('bot sees the vessel interaction entity', async (s) => {
    const n = await pollUntil(() => {
      const r = bots.nearby(botName, { radius: NEAR + 6, type: 'interaction' });
      return r.total > 0 ? r : null;
    }, { timeoutMs: 5000 });
    ctx.assert(n, 'no interaction entity nearby');
    s.detail = `#${n.entities[0].id} at ${n.entities[0].distance}m`;
    return n.entities[0];
  });

  const head = await bridge.journalHead();

  await ctx.step('right-click the vessel', async (s) => {
    const r = await bots.interact(botName, { entityId: interaction.id, action: 'right_click' });
    ctx.assert(r.ok, r.error ?? 'interact failed');
    s.detail = `touched #${r.target.id} from ${r.target.distance}m`;
  });

  await ctx.step('god journal records the touch', async (s) => {
    // An entry that names the bot AND mentions the touch.
    const pattern = `/(?=.*${botName})(?=.*(touch|interact|click|vessel))/is`;
    const w = await bridge.waitForJournal({ since: head, pattern, timeoutMs: 10_000 });
    ctx.assert(w.matched, `no journal entry matching ${pattern} after the right-click; recent: ${short(w.recentEntries)}`);
    s.detail = `[${w.entry.kind}] ${short(w.entry.text, 160)}`;
  });

  await ctx.step('punch the vessel (attack) is journaled', async (s) => {
    // VesselListener debounces touches and strikes together (3 s per player): wait it out.
    await sleep(3500);
    const since = await bridge.journalHead();
    const r = await bots.interact(botName, { entityId: interaction.id, action: 'attack' });
    ctx.assert(r.ok, r.error ?? 'attack failed');
    const w = await bridge.waitForJournal({ since, pattern: `/(?=.*${botName})(?=.*(strike|struck|hit|punch|attack|vessel))/is`, timeoutMs: 10_000 });
    ctx.assert(w.matched, 'no journal entry after punching the vessel');
    s.detail = `[${w.entry.kind}] ${short(w.entry.text, 160)}`;
  }, { optional: true });
}
