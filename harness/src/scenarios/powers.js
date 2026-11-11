// Powers test: call god tools directly through the bridge and check the bot can perceive each effect.

import { sleep } from '../util.js';
import { buildInput, pollUntil, short, OBSERVABLE_KINDS } from './lib.js';

export const description = 'give_item, heal, apply_effect, smite warning, omen, conjure cage, set_time — each visible to the bot';

export async function run(ctx) {
  const { bridge, bots, botName } = ctx;

  await ctx.step(`spawn bot ${botName}`, async (s) => {
    const r = await ctx.ensureBot();
    s.detail = `at ${JSON.stringify(r.position)}`;
  });

  await ctx.step('reset bot (survival, clear inventory and effects)', async () => {
    for (const cmd of [`gamemode survival ${botName}`, `clear ${botName}`, `effect clear ${botName}`]) {
      const r = await bridge.console(cmd);
      ctx.assert(r.ok !== false, `${cmd}: ${short(r)}`);
    }
    await sleep(500);
  });

  // Each power: call it with a schema-derived input, then wait for the bot to observe the effect.
  const power = (label, name, hints, fixed, check) =>
    ctx.step(label, async (s) => {
      const def = await ctx.requireTool(name);
      const input = buildInput(def, { player: botName, ...hints }, fixed);
      const since = bots.latestSeq();
      const before = bots.status(botName);
      await ctx.callTool(name, input, s);
      const call = s.detail;
      const seen = await check(since, before);
      s.detail = `${seen} | ${call}`;
    });

  await power('give_item diamond → in inventory', 'give_item', {}, { player: botName, item: 'diamond', count: 1 }, async (since) => {
    const w = await ctx.observe({ since, kinds: ['inventory'], pattern: 'diamond', timeoutSec: 10 });
    ctx.assert(w.matched, 'no inventory change mentioning diamond within 10s');
    const totals = bots.inventory(botName).totals;
    ctx.assert((totals.diamond ?? 0) >= 1, `inventory has no diamond: ${JSON.stringify(totals)}`);
    return `diamond x${totals.diamond}`;
  });

  await ctx.step('hurt the bot a little (console /damage)', async (s) => {
    const before = bots.status(botName).health;
    const since = bots.latestSeq();
    await bridge.console(`damage ${botName} 6`);
    const w = await ctx.observe({ since, kinds: ['health'], timeoutSec: 5 });
    ctx.assert(w.matched, 'health did not change after /damage');
    s.detail = `${before} → ${bots.status(botName).health}`;
  }, { optional: true });

  await power('heal → health goes up', 'heal', {}, { player: botName }, async (since, before) => {
    const hp0 = before.health;
    if (hp0 >= 20) return 'bot was already at full health (nothing to observe)';
    const w = await ctx.observe({ since, kinds: ['health'], pattern: '\\(\\+', timeoutSec: 10 });
    ctx.assert(w.matched, `health did not increase within 10s (was ${hp0})`);
    return w.entry.text;
  });

  await power(
    'apply_effect speed → effect on the bot',
    'apply_effect',
    {},
    { player: botName, effect: 'speed', seconds: 30, level: 1 },
    async (since) => {
      const w = await ctx.observe({ since, kinds: ['effect'], pattern: 'speed', timeoutSec: 10 });
      ctx.assert(w.matched, 'no speed effect within 10s');
      return w.entry.text;
    },
  );

  await power('smite (warning) → visible strike', 'smite', {}, { player: botName, power: 1, mode: 'warning' }, async (since) => {
    const w = await ctx.observe({
      since,
      kinds: ['entity_spawn', 'sound', 'particle', 'damage', 'health', 'system', 'title', 'subtitle', 'actionbar'],
      timeoutSec: 10,
    });
    ctx.assert(w.matched, 'bot observed nothing within 10s');
    await sleep(1000);
    const st = bots.status(botName);
    ctx.assert(st.state === 'online', `bot is ${st.state} after a warning smite`);
    return `${w.entry.kind}: ${short(w.entry.text, 100)}; hp=${st.health}`;
  });

  await power('omen → visible sign', 'omen', {}, { target: botName, kind: 'storm' }, async (since) => {
    const w = await ctx.observe({ since, kinds: OBSERVABLE_KINDS, timeoutSec: 10 });
    ctx.assert(w.matched, 'bot observed nothing within 10s');
    return `${w.entry.kind}: ${short(w.entry.text, 100)}`;
  });

  await power('conjure cage → blocks appear around the bot', 'conjure', {}, { shape: 'cage', material: 'glass', target: botName }, async (since) => {
    const w = await ctx.observe({ since, kinds: ['blocks', 'entity_spawn'], timeoutSec: 10 });
    ctx.assert(w.matched, 'no block changes or spawned entities near the bot within 10s');
    return `${w.entry.kind}: ${short(w.entry.text, 140)}`;
  });

  await power('set_time night → time changes', 'set_time', {}, { time: 'night' }, async (since) => {
    const w = await ctx.observe({ since, kinds: ['time'], timeoutSec: 10 });
    ctx.assert(w.matched, 'bot saw no time-of-day jump within 10s');
    const t = await pollUntil(() => bots.status(botName).timeOfDay, { timeoutMs: 1000 });
    return `${w.entry.text} (timeOfDay=${t})`;
  });

  await ctx.step('cleanup (time set day, clear effects)', async () => {
    await bridge.console('time set day');
    await bridge.console(`effect clear ${botName}`);
  }, { optional: true });
}
