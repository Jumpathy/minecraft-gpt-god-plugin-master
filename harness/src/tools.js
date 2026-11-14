// Every tool of the god-harness, defined once. Entry points: daemon.js (the long-running HTTP + MCP process)
// and mcp-server.js (stdio: forwards to the daemon, or runs the tools in-process with --inprocess).
//   server_* / sandbox_* / world_*  the local Paper sandbox (GOD_SANDBOX_DIR, default <repo>/../minecraft/srv1)
//   bot_*                           headless mineflayer player bots
//   player_*                        real Minecraft clients (Fabric + the god's client mod) driven through the mod's puppet port
//   god_*                           the plugin's debug bridge (works without an API key), budget_* the spend guard
//   camera_*                        the real Minecraft client as a camera (screenshots)

import './env.js'; // optional harness/.env (GOD_MC_HOST, GOD_BRIDGE_URL, ...)
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { z } from 'zod';
import { BotManager } from './bots.js';
import { BridgeClient } from './bridge.js';
import { Budget } from './budget.js';
import { CameraClient, OUT_DIR } from './client.js';
import { PlayerManager } from './players.js';
import { ServerManager } from './sandbox.js';
import { Session } from './session.js';
import { errorInfo } from './util.js';

const VIEWS_DIR = process.env.GOD_VIEWS_DIR || OUT_DIR();

export const log = (...a) => console.error('[god-harness]', ...a);


const bots = new BotManager();
const bridge = new BridgeClient();
const sandbox = new ServerManager({ bridge });
const camera = new CameraClient({ sandbox, bridge });

const players = new PlayerManager({ sandbox, bridge });
const budget = new Budget({ sandbox, bridge, log });
const session = new Session({ sandbox, bridge, bots, players, budget });

export const managers = { bots, bridge, sandbox, camera, players, budget, session };

export const INSTRUCTIONS =
'End-to-end test harness for the GPTGOD Minecraft plugin (Paper 26.3) on this machine. ' +
      'Server: server_status / server_deploy (copies the newest build/libs jar, writes the test settings, restarts) / ' +
      'server_start / server_stop / server_command / server_log / server_wait_for_log; world_setup freezes time and weather ' +
      'for repeatable shots. Bots (bot_*): headless players; each keeps a seq-numbered log of what it observes (chat, god ' +
      'whispers, titles, damage, effects, the vessel\'s display entities...). Loop: note latestSeq, act (bot_say "/pray ..."), ' +
      'then bot_wait_for since that seq. God (god_*): the plugin\'s debug bridge: status, journal, perception, god_tool runs ' +
      'a power directly (no API key needed), god_view renders the god\'s own view. Camera (camera_*): the real Minecraft ' +
      'client in spectator mode, aimed by server teleports; camera_screenshot returns a real in-game screenshot. ' +
      'Players (player_*): real Minecraft 26.3 clients with the Fabric client mod, driven in-process through its puppet ' +
      'port (no keystrokes): player_launch, player_say, player_look, player_goto, player_attack, player_use, player_screenshot, ' +
      'player_events / player_wait_for. Budget: budget_set caps the API spend of the god and pauses it at the cap; budget_status ' +
      'shows the spend. session_start / session_report record a merged timeline of what players did and what the god thought, ' +
      'said and did. ' +
      'Typical session: server_deploy, world_setup, bot_spawn, god_tool vessel_go, camera_launch, camera_screenshot, ' +
      'camera_close, server_stop. "username" may be omitted when exactly one bot exists.';

/** Every tool: {name, description, shape (zod raw shape), fn(args, extra)}. */
export const registry = [];

// ---------- helpers ----------

const json = (v) => JSON.stringify(v, (_k, val) => (typeof val === 'bigint' ? Number(val) : val));

function textResult(result) {
  const isError = result && typeof result === 'object' && result.ok === false;
  return { content: [{ type: 'text', text: json(result) }], ...(isError ? { isError: true } : {}) };
}

function errorResult(e) {
  return { content: [{ type: 'text', text: json({ ok: false, ...errorInfo(e) }) }], isError: true };
}

/** Sends MCP progress notifications if the client asked for them (keeps long waits alive). */
function progressReporter(extra, label) {
  const token = extra?._meta?.progressToken;
  if (token === undefined) return undefined;
  return (elapsedMs, totalMs) => {
    extra
      .sendNotification({
        method: 'notifications/progress',
        params: { progressToken: token, progress: elapsedMs, total: totalMs, message: `${label}: ${Math.round(elapsedMs / 1000)}s` },
      })
      .catch(() => {});
  };
}

function tool(name, description, shape, fn) {
  registry.push({ name, description, shape, fn });
}

/** Runs a tool and always returns an MCP result ({content, isError?}). */
export async function runTool(name, args, extra) {
  const t = registry.find((x) => x.name === name);
  if (!t) return errorResult(new Error(`unknown tool ${name}`));
  try {
    const parsed = z.object(t.shape).parse(args ?? {});
    const out = await t.fn(parsed, extra);
    if (out && Array.isArray(out.content)) return out; // already an MCP result
    return textResult(out ?? { ok: true });
  } catch (e) {
    return errorResult(e);
  }
}

const username = z.string().optional().describe('Bot username (optional when exactly one bot exists)');
const num = (d) => z.number().optional().describe(d);
const coords = {
  x: num('Block X'),
  y: num('Block Y'),
  z: num('Block Z'),
};

// ---------- bot tools ----------

tool(
  'bot_spawn',
  'Connect a new offline-mode player bot (mineflayer, speaks Minecraft 26.1; the 26.3 sandbox needs ViaVersion + ViaBackwards) ' +
    'to the server (default GOD_MC_HOST:GOD_MC_PORT, else the sandbox address) and wait until it spawns. Adds it to the ' +
    'sandbox whitelist first. Optionally op it and/or set its gamemode via the bridge console.',
  {
    username: z.string().describe('Player name, 3-16 chars [A-Za-z0-9_]'),
    host: z.string().optional().describe('Server host (default GOD_MC_HOST env or localhost)'),
    port: z.number().int().optional().describe('Server port (default GOD_MC_PORT env or 25565)'),
    version: z.string().optional().describe('Protocol version (default: auto, i.e. 26.1 through ViaBackwards)'),
    op: z.boolean().optional().describe('Run "op <name>" via the bridge console after spawning'),
    gamemode: z.enum(['survival', 'creative', 'adventure', 'spectator']).optional().describe('Set gamemode via the bridge console after spawning'),
  },
  async (a) => {
    let host = a.host ?? process.env.GOD_MC_HOST;
    let port = a.port ?? (process.env.GOD_MC_PORT ? Number(process.env.GOD_MC_PORT) : undefined);
    let whitelist;
    if (!host) {
      const addr = await sandbox.address();
      host = addr.host;
      port = port ?? addr.port;
      whitelist = await sandbox.allowPlayer(a.username).catch((e) => ({ ok: false, error: e.message }));
    }
    const res = await bots.spawn(a.username, { host, port, version: a.version });
    if (whitelist) res.whitelist = whitelist;
    const setup = [];
    const run = async (cmd) => {
      try {
        const r = await bridge.console(cmd);
        setup.push({ command: cmd, ok: r.ok !== false, feedback: r.feedback });
      } catch (e) {
        setup.push({ command: cmd, ok: false, error: e.message });
      }
    };
    if (a.op) await run(`op ${a.username}`);
    if (a.gamemode) await run(`gamemode ${a.gamemode} ${a.username}`);
    return setup.length ? { ...res, setup } : res;
  },
);

tool('bot_list', 'List all bots with state, position, health and latest event seq.', {}, async () => ({
  ok: true,
  latestSeq: bots.latestSeq(),
  bots: bots.list(),
}));

tool('bot_disconnect', 'Disconnect a bot and forget it.', { username: z.string() }, async (a) => bots.disconnect(a.username));

tool(
  'bot_say',
  'Send chat as the bot. Text starting with "/" is sent as a command (e.g. "/pray please send rain"). Returns the seq of the action so you can bot_wait_for replies since it.',
  { username, text: z.string().describe('Chat message or /command') },
  async (a) => bots.say(a.username, a.text),
);

tool(
  'bot_goto',
  'Walk to coordinates (x,y,z; or x,z only) or to a player using the pathfinder. Does not dig or build unless allowDig.',
  {
    username,
    ...coords,
    player: z.string().optional().describe('Walk to this player instead of coordinates'),
    range: num('Acceptable distance from the goal (default 1.5)'),
    timeoutSec: num('Give up after this many seconds (default 60)'),
    allowDig: z.boolean().optional().describe('Allow breaking blocks on the way (default false)'),
  },
  async (a) => bots.goto(a.username, a),
);

tool(
  'bot_follow',
  'Continuously follow a player until bot_stop.',
  { username, player: z.string(), range: num('Follow distance (default 2)') },
  async (a) => bots.follow(a.username, a),
);

tool('bot_stop', 'Stop moving/following/digging.', { username }, async (a) => bots.stop(a.username));

tool(
  'bot_teleport',
  'Teleport a bot with a server command (reliable, unlike walking on some natural terrain): to x,y,z (+ optional yaw/pitch) or to a player.',
  { username, ...coords, yaw: num('Yaw in degrees (0 = south)'), pitch: num('Pitch in degrees'), player: z.string().optional().describe('Teleport to this player instead') },
  async (a) => {
    const name = a.username ?? bots.list()[0]?.username;
    if (!name) throw new Error('no bots spawned');
    let cmd;
    if (a.player) cmd = `tp ${name} ${a.player}`;
    else if ([a.x, a.y, a.z].every((n) => typeof n === 'number')) {
      cmd = `tp ${name} ${a.x} ${a.y} ${a.z}${typeof a.yaw === 'number' ? ` ${a.yaw} ${a.pitch ?? 0}` : ''}`;
    } else throw new Error('provide x, y, z or player');
    const r = await sandbox.command(cmd);
    return { ok: r.ok !== false, command: cmd, feedback: r.feedback ?? r.output };
  },
);

tool(
  'bot_look_at',
  "Turn the bot's head to look at a player (eye height) or a point.",
  { username, player: z.string().optional(), ...coords },
  async (a) => bots.lookAt(a.username, a),
);

tool(
  'bot_dig',
  'Break a block: at x,y,z, or the nearest block of a type within radius. Walks into reach if needed and equips the best tool.',
  {
    username,
    ...coords,
    block: z.string().optional().describe('Block type to find, e.g. "oak_log"'),
    radius: num('Search radius for block type (default 16)'),
    timeoutSec: num('Dig timeout (default 30)'),
  },
  async (a) => bots.dig(a.username, a),
);

tool(
  'bot_attack',
  'Punch the nearest entity of a type (e.g. "zombie", "interaction"), a player by name, or an entity id. Approaches first.',
  {
    username,
    player: z.string().optional(),
    type: z.string().optional().describe('Entity type name, e.g. "cow", "block_display", "interaction"'),
    entityId: z.number().int().optional(),
    radius: num('Search radius (default 16)'),
    times: z.number().int().optional().describe('Number of hits, 1-20 (default 1)'),
  },
  async (a) => bots.attack(a.username, a),
);

tool(
  'bot_interact',
  "Right-click (or attack) an entity, by default the nearest 'interaction' entity — i.e. touch the god's vessel. Right-click sends interact_at + interact like a vanilla client.",
  {
    username,
    type: z.string().optional().describe('Entity type (default "interaction")'),
    entityId: z.number().int().optional(),
    player: z.string().optional(),
    radius: num('Search radius (default 16)'),
    action: z.enum(['right_click', 'attack']).optional().describe('Default right_click'),
  },
  async (a) => bots.interact(a.username, a),
);

tool(
  'bot_equip',
  'Equip an inventory item to hand/off-hand/armor slot.',
  {
    username,
    item: z.string().describe('Item name, e.g. "diamond_sword"'),
    destination: z.enum(['hand', 'off-hand', 'head', 'torso', 'legs', 'feet']).optional(),
  },
  async (a) => bots.equip(a.username, a),
);

tool(
  'bot_toss',
  'Drop an item from the inventory (whole stack unless count given).',
  { username, item: z.string(), count: z.number().int().optional() },
  async (a) => bots.toss(a.username, a),
);

tool(
  'bot_status',
  'Position, health, food, dimension, gamemode, held item, effects, time of day, weather, scoreboard sidebar.',
  { username },
  async (a) => bots.status(a.username),
);

tool('bot_inventory', 'List inventory items, held item, off-hand and armor.', { username }, async (a) => bots.inventory(a.username));

tool(
  'bot_nearby',
  'List entities near the bot (type, name, distance, position; text_display text if known), nearest first.',
  {
    username,
    radius: num('Radius in blocks (default 32)'),
    type: z.string().optional().describe('Only this entity type, e.g. "block_display"'),
    limit: z.number().int().optional().describe('Max entities (default 40)'),
  },
  async (a) => bots.nearby(a.username, a),
);

const kinds = z
  .array(z.string())
  .optional()
  .describe(
    'Filter by kind: chat, system, actionbar, title, subtitle, display_text, bossbar, sidebar, death, spawn, respawn, health, damage, ' +
      'effect, effect_end, inventory, teleport, gamemode, world, time, weather, entity_spawn, entity_gone, blocks, particle, sound, player, ' +
      'kicked, end, error, info, action',
  );

tool(
  'bot_events',
  "Read a bot's event log (all bots if username omitted). With since: the first `limit` entries after that seq; without: the last `limit` entries.",
  { username, since: z.number().int().optional(), limit: z.number().int().optional().describe('Default 50'), kinds },
  async (a) => bots.events(a),
);

tool(
  'bot_wait_for',
  "Block until a new bot log entry matches a regex (case-insensitive; '/body/flags' also accepted). Default since = now. " +
    "Ignores the bot's own 'action' entries and chat echoes unless kinds says otherwise. Key primitive: bot_say '/pray ...' then wait.",
  {
    pattern: z.string().describe('Regex matched against entry text, e.g. "whisper|thunder" or "." for anything'),
    username,
    since: z.number().int().optional().describe('Only entries with seq greater than this (default: current latest seq)'),
    timeoutSec: num('Default 30, max 600'),
    kinds,
    excludeSelf: z.boolean().optional().describe("Skip the bot's own chat echoes (default true)"),
  },
  async (a, extra) =>
    bots.waitFor({ ...a, signal: extra?.signal, onProgress: progressReporter(extra, 'bot_wait_for') }),
);

// ---------- god (bridge) tools ----------

tool('god_status', 'GET /status: awake/paused/mode/model, persona, vessel, players, token usage, journalSeq.', {}, async () => ({
  ok: true,
  ...(await bridge.status()),
}));

tool(
  'god_journal',
  "GET /journal: the god's journal (perception, thought, tool, speech, error, info, usage, cycle entries).",
  { since: z.number().int().optional(), limit: z.number().int().optional() },
  async (a) => ({ ok: true, ...(await bridge.journal(a)) }),
);

tool(
  'god_wait_for',
  'Poll the god journal until a new entry matches (kinds and/or regex pattern). Default since = current journal head.',
  {
    kinds: z.array(z.enum(['perception', 'thought', 'tool', 'speech', 'error', 'info', 'usage', 'cycle'])).optional(),
    pattern: z.string().optional(),
    since: z.number().int().optional(),
    timeoutSec: num('Default 60, max 600'),
  },
  async (a, extra) =>
    bridge.waitForJournal({
      since: a.since,
      kinds: a.kinds,
      pattern: a.pattern,
      timeoutMs: Math.min(600, a.timeoutSec ?? 60) * 1000,
      signal: extra?.signal,
      onProgress: progressReporter(extra, 'god_wait_for'),
    }),
);

tool(
  'god_perception',
  "GET /perception: what the god currently perceives. consume=true drains pending events (use when YOU play god in external mode).",
  { consume: z.boolean().optional() },
  async (a) => ({ ok: true, ...(await bridge.perception({ consume: !!a.consume })) }),
);

tool('god_tools', "GET /tools: the god's powers with their input schemas.", {}, async () => ({ ok: true, ...(await bridge.tools()) }));

tool('god_memory', "GET /memory: the god's chronicle, ledger, quests and recent memory.", {}, async () => ({
  ok: true,
  ...(await bridge.memory()),
}));

tool(
  'god_wake',
  'POST /wake: trigger a god cycle now, optionally with a directive.',
  { directive: z.string().optional() },
  async (a) => bridge.wake(a.directive),
);

tool(
  'god_tool',
  'POST /tool: run a god power directly, e.g. name="smite" input={"player":"Bot1","power":1,"mode":"warning"}. See god_tools for names/schemas.',
  {
    name: z.string(),
    input: z.record(z.string(), z.any()).optional().describe('Tool input object'),
  },
  async (a) => {
    const r = await bridge.tool(a.name, a.input ?? {});
    return r.isError ? { ...r, ok: false } : r;
  },
);

tool(
  'god_inject_event',
  "POST /event: inject an observed event into the god's log (it will perceive it next cycle).",
  { text: z.string() },
  async (a) => bridge.event(a.text),
);

tool(
  'god_console',
  'POST /console: run a server console command (no leading slash needed), e.g. "give Bot1 diamond 1", "time set night".',
  { command: z.string() },
  async (a) => bridge.console(a.command),
);

tool('god_reset_memory', "POST /memory/reset: wipe the god's memory.", {}, async () => bridge.resetMemory());

tool(
  'god_view',
  'GET /view: the plugin\'s own ray-traced render of what the god sees (not the real client; see camera_screenshot), ' +
    'returned as an image and saved under harness/out/. ' +
    'type: eyes | vessel | overhead | panorama | orbit. target: player name, structure name, "x,y,z" or "vessel".',
  {
    type: z.enum(['eyes', 'vessel', 'overhead', 'panorama', 'orbit']),
    target: z.string().optional(),
    xray: z.boolean().optional(),
    radius: z.number().int().min(8).max(128).optional().describe('overhead only, 8-128'),
  },
  async (a) => {
    const { png, caption } = await bridge.view(a);
    await mkdir(VIEWS_DIR, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const file = path.join(VIEWS_DIR, `${stamp}-${a.type}.png`);
    await writeFile(file, png);
    return {
      content: [
        { type: 'image', data: png.toString('base64'), mimeType: 'image/png' },
        { type: 'text', text: json({ ok: true, caption, saved: file, bytes: png.length }) },
      ],
    };
  },
);

// ---------- sandbox server ----------

const imageResult = (png, meta) => ({
  content: [
    { type: 'image', data: png.toString('base64'), mimeType: 'image/png' },
    { type: 'text', text: json({ ok: true, ...meta }) },
  ],
});

tool(
  'server_status',
  'Sandbox status: folder, running (status ping), pid, whether this harness owns the process, key server.properties values, ' +
    'plugin jars, the GPTGOD bridge settings and whether the bridge answers.',
  {},
  async () => sandbox.status(),
);

tool(
  'server_start',
  'Start the sandbox Paper server with a Java 25 runtime (child of this MCP server, no console window) and wait for "Done". ' +
    'Refuses if eula.txt is not already accepted (the harness never accepts it) or if something else holds the port.',
  { timeoutSec: num('Startup timeout (default 180)') },
  async (a) => sandbox.start({ timeoutSec: a.timeoutSec }),
);

tool(
  'server_stop',
  'Stop the sandbox cleanly: "stop" on stdin when this harness started it, else "stop" through the GPTGOD bridge console. ' +
    'force=true kills a server that offers neither (loses chunks since the last autosave). Bots and the camera leave first.',
  { force: z.boolean().optional(), timeoutSec: num('Default 90') },
  async (a) => {
    if (camera.running) await camera.close().catch(() => {});
    await bots.disconnectAll().catch(() => {});
    return sandbox.stop({ force: !!a.force, timeoutSec: a.timeoutSec });
  },
);

tool('server_restart', 'Stop then start the sandbox (bots and the camera leave first).', { timeoutSec: num('Startup timeout (default 180)') }, async (a) => {
  if (camera.running) await camera.close().catch(() => {});
  await bots.disconnectAll().catch(() => {});
  return sandbox.restart({ timeoutSec: a.timeoutSec });
});

tool(
  'server_command',
  'Run a console command (no leading slash): through the bridge (returns its feedback) when the plugin is up, else on stdin ' +
    '(returns the log lines that follow). 26.3 renamed some gamerules, e.g. advance_time / advance_weather.',
  { command: z.string(), via: z.enum(['auto', 'stdin']).optional().describe('auto (default) or stdin') },
  async (a) => sandbox.command(a.command, { via: a.via === 'stdin' ? 'stdin' : undefined }),
);

tool(
  'server_log',
  'Tail logs/latest.log of the sandbox (optionally only lines matching a regex). Returns a cursor for server_wait_for_log.',
  { lines: z.number().int().optional().describe('Default 60, max 2000'), grep: z.string().optional().describe('Regex filter') },
  async (a) => sandbox.log({ lines: a.lines, grep: a.grep }),
);

tool(
  'server_wait_for_log',
  'Wait until a new line of logs/latest.log matches a regex. since: a cursor from server_log / a previous wait (default: now).',
  { pattern: z.string(), since: z.string().optional(), timeoutSec: num('Default 60, max 600') },
  async (a, extra) =>
    sandbox.waitForLog({ pattern: a.pattern, since: a.since, timeoutSec: Math.min(600, a.timeoutSec ?? 60), signal: extra?.signal }),
);

tool(
  'sandbox_configure',
  'Write the test settings: server.properties online-mode=false (offline bots and camera) + server-ip=127.0.0.1 (unreachable ' +
    'from the network), bukkit.yml connection-throttle -1, plugins/gptgodmc/config.yml bridge on 127.0.0.1:8765 and optional ' +
    'brain.mode. Backs up the original server.properties once (server.properties.harness-backup). Restart to apply.',
  {
    brainMode: z.enum(['live', 'external', 'off']).optional().describe('live = Claude plays god (costs API money, needs the key); external = no API calls'),
    spawnProtection: z.number().int().optional().describe('server.properties spawn-protection (e.g. 0 so bots can dig at spawn)'),
  },
  async (a) => sandbox.configure({ brainMode: a.brainMode, spawnProtection: a.spawnProtection }),
);

tool(
  'server_deploy',
  'Deploy the newest build/libs/gptgodmc-*.jar (by modification time) into plugins/ (older gptgodmc jars removed), write the ' +
    'test settings (sandbox_configure) and (re)start the server. Does not build: run ./gradlew shadowJar first.',
  {
    restart: z.boolean().optional().describe('Start/restart the server afterwards (default true)'),
    brainMode: z.enum(['live', 'external', 'off']).optional().describe('Also set brain.mode'),
  },
  async (a) => {
    if (camera.running) await camera.close().catch(() => {});
    await bots.disconnectAll().catch(() => {});
    return sandbox.deploy({ restart: a.restart !== false, configureOptions: { brainMode: a.brainMode } });
  },
);

tool(
  'world_setup',
  'Repeatable conditions: set the time (default noon), clear the weather, stop the daylight and weather cycles ' +
    '(gamerules advance_time / advance_weather), hide the locator bar (gamerule locator_bar), optionally peaceful difficulty.',
  {
    time: z.string().optional().describe('time set argument: noon, day, night, midnight or ticks (default noon)'),
    weather: z.enum(['clear', 'rain', 'thunder']).optional().describe('Default clear'),
    freeze: z.boolean().optional().describe('Stop the time/weather cycles (default true)'),
    peaceful: z.boolean().optional().describe('difficulty peaceful (no hostile mobs in shots)'),
    locatorBar: z.boolean().optional().describe('Keep the locator bar (player dots) in the HUD (default false = hidden)'),
  },
  async (a) => sandbox.worldSetup(a),
);

// ---------- camera (the real client) ----------

const aimShape = {
  target: z.string().optional().describe('"vessel" (default) or a player name to orbit and look at'),
  distance: num('Orbit: blocks from the target (default 8)'),
  angle: num('Orbit: compass angle around the target in degrees; 0 = camera south of it looking north, 90 = west (default 0)'),
  height: num('Orbit: blocks above the target (default 3)'),
  lookHeight: num('Orbit: look at target y + this (default 1.5)'),
  x: num('Explicit camera X (with y, z: overrides the orbit)'),
  y: num('Explicit camera Y'),
  z: num('Explicit camera Z'),
  yaw: num('Explicit yaw in degrees (0 = south, 90 = west)'),
  pitch: num('Explicit pitch in degrees (positive = down)'),
  lookAt: z.string().optional().describe('With explicit x,y,z: look at "vessel" or a player instead of yaw/pitch'),
};

tool(
  'camera_launch',
  'Launch the installed Minecraft 26.3 client (no launcher, no account: offline name GOD_CAMERA_NAME, default HarnessCam; own ' +
    'game dir harness/client/game) and join the sandbox in spectator mode. Opens a 1280x720 game window: it may flash in the ' +
    'taskbar when created; the harness then pushes it behind other windows without activating it (windowMode background). ' +
    'No keystrokes or mouse input are ever sent. The server must run with online-mode=false. Takes 30-60 s.',
  {
    windowMode: z.enum(['background', 'normal']).optional().describe('background (default) or normal'),
    timeoutSec: num('Default 180'),
  },
  async (a) => camera.launch({ windowMode: a.windowMode, timeoutSec: a.timeoutSec }),
);

tool(
  'camera_aim',
  'Aim the camera with a server teleport: orbit the vessel or a player (distance/angle/height), or explicit x,y,z + yaw/pitch.',
  aimShape,
  async (a) => camera.aim(a),
);

tool(
  'camera_screenshot',
  'Take a real screenshot of the game window (PrintWindow capture; works while the window is behind others, no focus). ' +
    'Optionally aim first (same arguments as camera_aim). Returned as an image and saved to harness/out/<time>-<label>.png. ' +
    'settleMs waits for chunks and entity interpolation after a teleport (default 1500, 2000 after aiming; more after a long jump).',
  {
    label: z.string().optional().describe('File name label'),
    settleMs: num('Wait before capturing'),
    aim: z.boolean().optional().describe('Aim first using the aim arguments (default: only if any is given)'),
    ...aimShape,
  },
  async (a) => {
    const aimArgs = {};
    for (const k of Object.keys(aimShape)) if (a[k] !== undefined) aimArgs[k] = a[k];
    let aimed;
    if (a.aim === true || (a.aim !== false && Object.keys(aimArgs).length)) aimed = await camera.aim(aimArgs);
    const shot = await camera.screenshot({ label: a.label, settleMs: a.settleMs ?? (aimed ? 2000 : 1500) });
    return imageResult(shot.png, { file: shot.file, width: shot.width, height: shot.height, meanBrightness: shot.meanBrightness, warning: shot.warning, aimed });
  },
);

tool('camera_status', 'Camera client state (running, pid, name, game dir, last exit).', {}, async () => camera.status());

tool('camera_close', 'Close the camera client cleanly (WM_CLOSE to its window; killed after 30 s).', {}, async () => camera.close());

// ---------- more tools (players, budget, session) ----------

import { defineMoreTools } from './tools-more.js';
defineMoreTools({ tool, z, managers, imageResult, username, num });

// ---------- shutdown ----------

let shuttingDown = false;
export async function shutdown(reason) {
  if (shuttingDown) return;
  shuttingDown = true;
  log(`shutting down (${reason})`);
  const force = setTimeout(() => process.exit(0), sandbox.owned ? 60_000 : 5000);
  force.unref();
  budget.stop();
  await session.stop().catch(() => {});
  await bots.disconnectAll().catch(() => {});
  await players.closeAll({ timeoutSec: 10 }).catch(() => {});
  if (camera.running) await camera.close({ timeoutSec: 10 }).catch(() => {});
  // A server this process started would lose its console with us: stop it cleanly (it saves the world).
  if (sandbox.owned) await sandbox.stop({ timeoutSec: 50 }).catch(() => {});
  process.exit(0);
}
