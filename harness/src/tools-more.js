// Tools added on top of the original set: real-client players (player_*), the spend guard (budget_*) and the
// session record (session_*). Called once from tools.js with its tool() registrar.

import path from 'node:path';
import { OUT_DIR } from './client.js';

export function defineMoreTools({ tool, z, managers, imageResult }) {
  const { players, budget, session, sandbox } = managers;
  const who = z.string().optional().describe('Player name (optional when exactly one real-client player runs)');
  const num = (d) => z.number().optional().describe(d);
  const bool = (d) => z.boolean().optional().describe(d);

  // ---------- real-client players ----------

  tool(
    'player_launch',
    'Start a real Minecraft 26.3 client (Fabric + the god\'s client mod, offline name, own game dir) and join the sandbox as a ' +
      'simulated player, driven through the mod\'s puppet port (in-process; no keystrokes, the window stays behind others). ' +
      'About 2 GB RAM and 40-90 s each; launch them one at a time. The server must run with online-mode=false.',
    {
      username: z.string().describe('Player name, 3-16 chars [A-Za-z0-9_]'),
      gamemode: z.enum(['survival', 'creative', 'adventure', 'spectator']).optional().describe('Set after joining (default: the server\'s)'),
      windowMode: z.enum(['background', 'normal']).optional().describe('background (default) pushes the window behind others'),
      timeoutSec: num('Default 240'),
      xmx: z.string().optional().describe('Client heap, default 2G'),
    },
    async (a) => players.launch(a),
  );

  tool('player_list', 'Real-client players: state, pid, puppet port.', {}, async () => players.list());

  tool('player_state', 'What the real client reports: position, look, health, food, gamemode, held item, open screen, what the crosshair is on, tab list, latest event seq.', { username: who }, async (a) =>
    players.get(a.username).call('GET', '/state'),
  );

  tool(
    'player_say',
    'Chat as the real-client player; text starting with "/" is sent as a command (e.g. "/pray give us rain"). Returns the event seq before sending, for player_wait_for.',
    { username: who, text: z.string().describe('Chat message or /command (max 256 chars)') },
    async (a) => {
      const p = players.get(a.username);
      const before = (await p.call('GET', '/state')).eventSeq;
      const r = await p.call('POST', '/chat', { text: a.text });
      if (session.active) await session.add(`player:${p.name}`, 'action', `says: ${a.text}`);
      return { ...r, sinceSeq: before };
    },
  );

  tool(
    'player_look',
    'Turn the player\'s view: yaw/pitch, or at a point x,y,z, or at the nearest entity (a player name or an entity type id such as minecraft:interaction = the god\'s vessel hitbox).',
    { username: who, yaw: num('Degrees, 0 = south'), pitch: num('Degrees, positive = down'), x: num('Look at X'), y: num('Look at Y'), z: num('Look at Z'), entity: z.string().optional().describe('Player name or entity type id'), radius: num('Entity search radius (default 32)') },
    async ({ username, ...body }) => players.get(username).call('POST', '/look', body),
  );

  tool(
    'player_move',
    'Hold movement keys for a number of client ticks (20 = 1 s), then release: forward/back/left/right/jump/sneak/sprint. wait=true answers when done.',
    { username: who, forward: bool('W'), back: bool('S'), left: bool('A'), right: bool('D'), jump: bool('Space'), sneak: bool('Shift'), sprint: bool('Ctrl'), ticks: z.number().int().describe('1..600'), wait: bool('Answer when the move ends') },
    async ({ username, ...body }) => players.get(username).call('POST', '/move', body),
  );

  tool(
    'player_goto',
    'Walk toward x,z by steering (turn, hold forward, jump when blocked); no pathfinding, so use it over open ground. Answers on arrival or timeout.',
    { username: who, x: z.number().describe('Target X'), z: z.number().describe('Target Z'), range: num('Stop within this many blocks (default 1.5)'), timeoutTicks: num('Default 400'), sprint: bool('Sprint') },
    async ({ username, ...body }) => players.get(username).call('POST', '/goto', body, { timeoutMs: 90_000 }),
  );

  tool('player_stop', 'Release every key and cancel goto/dig.', { username: who }, async (a) => players.get(a.username).call('POST', '/stop', {}));

  tool(
    'player_attack',
    'Left-click what the crosshair is on (entity: attack; block: start breaking). Aim first with player_look. times/intervalTicks repeat it.',
    { username: who, times: num('Default 1'), intervalTicks: num('Ticks between hits (default 12)') },
    async ({ username, ...body }) => players.get(username).call('POST', '/attack', body, { timeoutMs: 60_000 }),
  );

  tool('player_dig', 'Hold left-click on the looked-at block until it breaks or maxTicks pass.', { username: who, maxTicks: num('Default 200') }, async ({ username, ...body }) =>
    players.get(username).call('POST', '/dig', body, { timeoutMs: 60_000 }),
  );

  tool(
    'player_use',
    'Right-click like a player: the entity or block under the crosshair, else use the held item. ticks > 1 keeps it held (eating). sneak=true sneaks meanwhile (sneak + right-click the vessel with an item = an offering).',
    { username: who, ticks: num('Default 1'), sneak: bool('Sneak during the click'), hand: z.enum(['main', 'off']).optional().describe('Default main') },
    async ({ username, ...body }) => players.get(username).call('POST', '/use', body, { timeoutMs: 60_000 }),
  );

  tool('player_hotbar', 'Select a hotbar slot (0-8).', { username: who, slot: z.number().int().describe('0..8') }, async ({ username, ...body }) => players.get(username).call('POST', '/hotbar', body));

  tool('player_inventory', 'The real client\'s inventory.', { username: who }, async (a) => players.get(a.username).call('GET', '/inventory'));

  tool('player_screen', 'Close the open screen, or respawn.', { username: who, action: z.enum(['close', 'respawn']).describe('close | respawn') }, async ({ username, ...body }) =>
    players.get(username).call('POST', '/screen', body),
  );

  tool(
    'player_do',
    'Any other puppet request (see client-mod/.../puppet/): method + path + JSON body, e.g. POST /drop {"all":false}.',
    { username: who, method: z.enum(['GET', 'POST']).optional().describe('Default POST'), path: z.string().describe('e.g. /drop'), body: z.record(z.string(), z.any()).optional().describe('JSON body') },
    async (a) => players.get(a.username).call(a.method ?? 'POST', a.path, a.method === 'GET' ? undefined : (a.body ?? {})),
  );

  tool(
    'player_events',
    'What the real client perceived: chat, system messages, action bar, titles, subtitles, deaths, mod messages. since = a seq.',
    { username: who, since: num('Only events after this seq (default 0)'), limit: num('Default 100') },
    async (a) => players.get(a.username).call('GET', `/events?since=${a.since ?? 0}&limit=${a.limit ?? 100}`),
  );

  tool(
    'player_wait_for',
    'Block until the real client perceives something matching a regex (chat, titles, action bar...). since = a seq from player_say; default now.',
    { username: who, pattern: z.string().describe('Regex, case-insensitive'), since: num('Seq'), kinds: z.array(z.string()).optional().describe('Only these kinds'), timeoutSec: num('Default 30, max 600') },
    async ({ username, ...a }) => players.get(username).waitFor(a),
  );

  tool(
    'player_screenshot',
    'A real screenshot of what this player sees (the client\'s own framebuffer, written by the puppet; falls back to a window capture). Saved in the session folder when a session runs, else harness/out.',
    { username: who, label: z.string().optional().describe('File name label'), hideHud: bool('Hide the HUD'), settleMs: num('Wait first (default 300)') },
    async (a) => {
      const p = players.get(a.username);
      if (a.settleMs !== 0) await new Promise((r) => setTimeout(r, a.settleMs ?? 300));
      const label = `${p.name}-${a.label ?? 'view'}`;
      const file = session.shotPath(label) ?? path.join(OUT_DIR(), `${new Date().toISOString().replace(/[:.]/g, '-')}-${label.replace(/[^A-Za-z0-9_-]+/g, '_')}.png`);
      let shot;
      try {
        shot = await p.shot({ file, hideHud: a.hideHud });
      } catch (e) {
        shot = { ...(await p.screenshot({ label, settleMs: 0 })), fallback: `window capture (${e.message})` };
      }
      if (session.active) await session.add(`player:${p.name}`, 'screenshot', shot.file);
      return imageResult(shot.png, { file: shot.file, width: shot.width, height: shot.height, fallback: shot.fallback });
    },
  );

  tool('player_close', 'Quit a real-client player.', { username: z.string().describe('Player name') }, async (a) => players.close(a.username));

  // ---------- budget ----------

  tool(
    'budget_set',
    'Cap what the god\'s API calls may cost (USD) and start the watchdog: at stopAtUsd (default 87% of the cap) every god is paused and brain.mode is set to off. ' +
      'The ledger survives server restarts. reset=true forgets the spend recorded so far.',
    { capUsd: z.number().describe('The cap in USD'), stopAtUsd: num('Pause at this spend (default 0.87 x cap)'), reset: bool('Start the ledger at zero') },
    async (a) => budget.set(a),
  );

  tool('budget_status', 'The god\'s API spend so far against the cap (reads the plugin\'s counter now), token counts, and whether the guard tripped.', {}, async () => budget.status({ fresh: true }));

  // ---------- session record ----------

  tool('session_start', 'Start recording a merged timeline (god journal + bot and player events + your notes) under harness/out/session-<time>-<label>/.', { label: z.string().optional().describe('Short label') }, async (a) =>
    session.start(a),
  );

  tool('session_note', 'Add a driver note to the timeline (what you are about to test, a scenario heading).', { text: z.string().describe('The note') }, async (a) => session.note(a.text));

  tool(
    'session_report',
    'Write timeline.md and return the session summary: thoughts, powers used (with counts), vision use, speech, errors, cost. stop=true ends the recording.',
    { stop: bool('Stop recording afterwards'), perceptionChars: num('Characters of each perception kept in timeline.md (default 1500)') },
    async (a) => {
      const r = await session.report({ perceptionChars: a.perceptionChars });
      if (a.stop) await session.stop();
      return r;
    },
  );

  tool(
    'sandbox_info',
    'Which sandbox folder, server address and bridge this harness process works on, and its own pid (the daemon keeps bots, players and the server alive between calls).',
    {},
    async () => ({ ok: true, dir: sandbox.dir, address: await sandbox.address().catch(() => null), pid: process.pid, session: session.dir, uptimeSec: Math.round(process.uptime()) }),
  );
}
