// End-to-end smoke test of the whole chain on this machine, with no API key:
// sandbox server -> deploy the plugin -> a bot joins -> the vessel manifests next to it (bridge) ->
// the real client joins as a camera -> screenshot shows the vessel -> a particle power -> second
// screenshot -> everything stops. Screenshots land in harness/out/.

import { readFile } from 'node:fs/promises';
import { decodePng, meanBrightness, regionDiff } from '../png.js';
import { sleep } from '../util.js';
import { pollUntil, short } from './lib.js';

export const description = 'server + plugin deploy, bot, vessel, real-client screenshots, particle power, clean stop (no API key)';

// A small stone stage in the sky near spawn, so the shots do not depend on the terrain.
const STAGE = { x: 16, y: 120, z: -40, r: 7 };
// Where the vessel sits in frame when the camera orbits it (camera looks at vessel y + 1.5).
const CENTER = { x: 0.38, y: 0.25, w: 0.24, h: 0.6 };

export async function run(ctx) {
  const { bridge, bots, botName, sandbox, camera } = ctx;
  if (!sandbox || !camera) throw new Error('smoke needs the sandbox and camera (run it through run-scenario.js)');
  const shots = [];
  try {
    const jar = await ctx.step('newest plugin jar in build/libs', async (s) => {
      const j = await sandbox.newestPluginJar();
      s.detail = `${j.name}, ${j.size} bytes, built ${j.modified}`;
      return j;
    });

    await ctx.step('stop a running sandbox server, if any', async (s) => {
      const r = await sandbox.stop();
      s.detail = r.alreadyStopped ? 'was not running' : `stopped via ${r.method} in ${r.stoppedInSec}s`;
    });

    await ctx.step(`deploy ${jar.name} with the test settings (brain.mode external) and start`, async (s) => {
      const r = await sandbox.deploy({ restart: true, configureOptions: { brainMode: 'external' } });
      const lines = r.started?.pluginLog ?? [];
      const errors = lines.filter((l) => /ERROR|Could not load|Error occurred while enabling/i.test(l) && /gptgod|GPTGOD/i.test(l));
      ctx.assert(!errors.length, `plugin errors at startup: ${short(errors)}`);
      ctx.assert(lines.some((l) => /Debug bridge listening on 127\.0\.0\.1/.test(l)), `no "Debug bridge listening on 127.0.0.1" line; plugin log: ${short(lines)}`);
      s.detail = `started in ${r.started.startedInSec}s with ${r.started.java}; settings changed: ${short(r.configured?.changes)}`;
    });

    await ctx.step('bridge answers /status (no API key needed)', async (s) => {
      const st = await pollUntil(() => bridge.status().catch(() => null), { timeoutMs: 15_000 });
      ctx.assert(st, 'bridge did not answer within 15s');
      ctx.assert(st.mode === 'external', `brain mode is ${st.mode}, expected external`);
      s.detail = `mode=${st.mode} keyConfigured=${st.keyConfigured} persona=${st.persona?.name ?? '-'} journalSeq=${st.journalSeq}`;
    });

    await ctx.step('repeatable world: noon, clear, cycles stopped; build the stage', async (s) => {
      const w = await sandbox.worldSetup({ time: 'noon', weather: 'clear', freeze: true });
      ctx.assert(w.ok, `world setup failed: ${short(w.results)}`);
      const { x, y, z, r } = STAGE;
      // Nobody is online yet, so the stage's chunks are not loaded: force-load them (released at the end).
      const fl = await sandbox.command(`forceload add ${x - r} ${z - r} ${x + r} ${z + r}`);
      ctx.assert(fl.ok !== false, `forceload: ${short(fl)}`);
      const filled = [];
      for (const cmd of [`fill ${x - r} ${y} ${z - r} ${x + r} ${y + 12} ${z + r} air`, `fill ${x - r} ${y - 1} ${z - r} ${x + r} ${y - 1} ${z + r} smooth_stone`]) {
        const c = await pollUntil(async () => {
          const res = await sandbox.command(cmd);
          const text = (res.feedback ?? []).join(' ');
          return /not loaded/i.test(text) ? null : { ...res, text };
        }, { timeoutMs: 15_000, intervalMs: 500 });
        ctx.assert(c, `${cmd}: the chunks never loaded`);
        ctx.assert(c.ok !== false && /filled|No blocks were filled/i.test(c.text), `${cmd}: ${short(c)}`);
        filled.push(c.text);
      }
      s.detail = `stage ${2 * r + 1}x${2 * r + 1} at y=${y - 1} around (${x}, ${z}): ${filled.join('; ')}`;
    });

    await ctx.step(`bot ${botName} joins and stands on the stage`, async (s) => {
      const r = await ctx.ensureBot();
      const tp = await sandbox.command(`tp ${botName} ${STAGE.x + 3.5} ${STAGE.y} ${STAGE.z + 0.5} 90 0`);
      ctx.assert(tp.ok !== false, `tp failed: ${short(tp)}`);
      const there = await pollUntil(() => {
        const p = bots.status(botName).position;
        return p && Math.abs(p.x - (STAGE.x + 3.5)) < 1 && Math.abs(p.y - STAGE.y) < 1.5 ? p : null;
      }, { timeoutMs: 8000 });
      ctx.assert(there, `bot did not arrive on the stage: ${JSON.stringify(bots.status(botName).position)}`);
      s.detail = `joined at ${JSON.stringify(r.position)}, now ${JSON.stringify(there)}`;
    });

    await ctx.step('vessel_go manifests the vessel next to the bot', async (s) => {
      await ctx.requireTool('vessel_go');
      await ctx.callTool('vessel_go', { target: `${STAGE.x} ${STAGE.y} ${STAGE.z}`, mode: 'teleport' }, s);
      const st = await bridge.status();
      ctx.assert(st.vessel?.manifested, `vessel not manifested: ${short(st.vessel)}`);
      const me = bots.status(botName).position;
      const d = Math.hypot(st.vessel.x - me.x, st.vessel.z - me.z);
      ctx.assert(d < 8, `vessel is ${d.toFixed(1)} blocks from the bot`);
      s.detail = `vessel at (${st.vessel.x}, ${st.vessel.y}, ${st.vessel.z}), ${d.toFixed(1)} blocks from ${botName}`;
    });

    await ctx.step('the bot sees the vessel (block_display + interaction entities)', async (s) => {
      const seen = await pollUntil(() => {
        const n = bots.nearby(botName, { radius: 10 });
        return n.counts.block_display > 0 && n.counts.interaction > 0 ? n : null;
      }, { timeoutMs: 10_000 });
      ctx.assert(seen, `nearby: ${short(bots.nearby(botName, { radius: 16 }).counts)}`);
      s.detail = JSON.stringify(seen.counts);
    });

    await ctx.step('the real client joins as the camera (spectator)', async (s) => {
      const r = await camera.launch({ windowMode: 'background' });
      ctx.assert(r.setup?.every((x) => x.ok !== false), `spectator setup failed: ${short(r.setup)}`);
      s.detail = `${r.name} on ${r.version}, joined in ${r.joinedInSec}s; window "${r.window?.title}" ${r.window?.clientWidth}x${r.window?.clientHeight}, ` +
        `foreground now=${r.window?.foreground}, took the foreground=${r.focus?.tookForeground}, gave it back=${r.focus?.gaveBack}`;
    });

    const shot1 = await ctx.step('screenshot 1 shows the vessel', async (s) => {
      await camera.aim({ target: 'vessel', distance: 7, angle: 35, height: 1.5 });
      const shot = await camera.screenshot({ label: 'smoke-1-vessel', settleMs: 3000 });
      const img = decodePng(shot.png);
      const b = meanBrightness(img);
      ctx.assert(img.width >= 640 && img.height >= 360, `screenshot is ${img.width}x${img.height}`);
      ctx.assert(b > 20, `screenshot is (almost) black: mean brightness ${b.toFixed(1)}`);
      shots.push(shot.file);
      s.detail = `${shot.file} (${img.width}x${img.height}, brightness ${b.toFixed(0)})`;
      return { ...shot, img };
    });

    await ctx.step('particles power runs (vortex of end rods around the vessel)', async (s) => {
      await ctx.requireTool('particles');
      const since = bots.latestSeq();
      await ctx.callTool('particles', { target: 'vessel', shape: 'vortex', particle: 'end_rod', size: 4, seconds: 10 }, s);
      const w = await ctx.observe({ since, kinds: ['particle'], pattern: 'end_rod', timeoutSec: 8 });
      s.detail += w.matched ? ` | bot saw: ${short(w.entry.text, 100)}` : ' | (bot reported no end_rod particles)';
    });

    const shot2 = await ctx.step('screenshot 2 during the particle show', async (s) => {
      const shot = await camera.screenshot({ label: 'smoke-2-particles', settleMs: 1500 });
      const img = decodePng(shot.png);
      shots.push(shot.file);
      const d = regionDiff(shot1.img, img, CENTER);
      s.detail = `${shot.file}; center changed vs screenshot 1: ${(d.changedFraction * 100).toFixed(1)}% of pixels`;
      return { ...shot, img, diff: d };
    });

    await ctx.step('screenshot 2 differs from screenshot 1 around the vessel (particles visible)', async (s) => {
      const d = shot2.diff;
      ctx.assert(d.changedFraction > 0.01, `only ${(d.changedFraction * 100).toFixed(2)}% of the center changed`);
      s.detail = `${(d.changedFraction * 100).toFixed(1)}% changed, mean diff ${d.meanDiff.toFixed(1)}`;
    }, { optional: true });

    await ctx.step('control: without the vessel the center of the frame changes (the vessel was in shot 1)', async (s) => {
      await sleep(9000); // let the particle show end
      await ctx.callTool('vessel_vanish', {}, s);
      await sleep(1500);
      const ctl = await camera.screenshot({ label: 'smoke-3-control-no-vessel', settleMs: 1000 });
      const img = decodePng(ctl.png);
      const d = regionDiff(shot1.img, img, CENTER);
      ctx.assert(d.changedFraction > 0.03, `the frame center barely changed without the vessel (${(d.changedFraction * 100).toFixed(2)}%): the vessel may not be in shot 1`);
      s.detail = `${(d.changedFraction * 100).toFixed(1)}% of the center changed (${ctl.file})`;
    }, { optional: true });
  } finally {
    // Everything stops, whatever happened above.
    await ctx.step('camera client closes', async (s) => {
      const r = await camera.close();
      s.detail = r.alreadyStopped ? 'was not running' : `${r.method} in ${r.closedInSec}s (exit ${r.exit?.code})`;
    }, { optional: true }).catch(() => {});
    await ctx.step(`bot ${botName} leaves`, async () => {
      await bots.disconnectAll();
    }, { optional: true }).catch(() => {});
    await ctx.step('release the stage force-load', async () => {
      const { x, z, r } = STAGE;
      if (await sandbox.bridgeReachable()) await sandbox.command(`forceload remove ${x - r} ${z - r} ${x + r} ${z + r}`);
    }, { optional: true }).catch(() => {});
    await ctx.step('server stops cleanly', async (s) => {
      const r = await sandbox.stop();
      s.detail = r.alreadyStopped ? 'was not running' : `${r.method} in ${r.stoppedInSec}s (exit ${r.exit?.code ?? '?'})`;
    }).catch(() => {});
    if (shots.length) console.log(`\n  screenshots:\n${shots.map((f) => `    ${f}`).join('\n')}`);
  }
}
