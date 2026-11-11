# GPTGOD test harness

End-to-end testing of the GPTGOD plugin on this machine, for Paper / Minecraft **26.3**. One MCP server
(`god-harness`) and a few scripts that can:

- **run the sandbox server**: start it with Java 25, stop it cleanly, deploy the newest plugin jar, write the test
  settings, run console commands, tail and wait on the log;
- **put players in the world**: headless [mineflayer](https://github.com/PrismarineJS/mineflayer) bots that join,
  chat, `/pray`, dig, strike or touch the god's vessel, and log everything they perceive;
- **make the god act** through the plugin's debug bridge: status, journal, perception, memory, any power run directly
  (no API key needed), the god's own rendered views;
- **see the result in the real game**: the installed Minecraft 26.3 client joins as a spectator camera, is aimed with
  server teleports, and returns real screenshots.

Windows only (the camera uses Windows APIs; the rest is plain Node). Requires Node 22+.

## Setup (once)

```sh
cd harness
npm install
```

- **Sandbox server**: a Paper 26.3 folder with `paper.jar` and an `eula.txt` that *you* accepted. Default
  `<repo>/../minecraft/srv1`; set `GOD_SANDBOX_DIR` for another one. The harness never writes `eula.txt` and refuses to
  start a server whose EULA is not accepted.
- **Java 25**: found automatically (`C:\Program Files\Java\jdk-25*`, Eclipse Adoptium, `~/.gradle/jdks`, ...). A Java 8
  `JAVA_HOME` is ignored. Override with `GOD_JAVA`.
- **The plugin jar**: build it yourself (`./gradlew shadowJar`); `server_deploy` copies the newest
  `build/libs/gptgodmc-*.jar`. The harness never builds.
- **ViaVersion + ViaBackwards** in the sandbox (bots speak at most 26.1, see [Bots](#bots)):
  `node scripts/install-via.js` downloads the pinned official 5.12.2 releases from Modrinth (sha512-checked) into
  `plugins/`. It installs third-party plugins into the server, so run it only with the server owner's consent.
- **Minecraft 26.3** installed by the official launcher (play it once so the libraries and assets exist). The camera
  runs it directly, without the launcher and without any account.

### What the harness changes in the sandbox

`sandbox_configure` (also run by `server_deploy`) writes, and reports every change it makes:

| File | Setting | Why |
|---|---|---|
| `server.properties` | `online-mode=false` | bots and the camera use offline names |
| `server.properties` | `server-ip=127.0.0.1` | with offline mode, nobody on the network can join |
| `bukkit.yml` | `settings.connection-throttle: -1` | bots and the camera log in seconds apart |
| `plugins/gptgodmc/config.yml` | `bridge.enabled: true`, `bind: 127.0.0.1`, `port: 8765` | the debug bridge; local only, it can run console commands |
| `plugins/gptgodmc/config.yml` | `brain.mode` (only when asked) | `external` for tests that must not call the API |

The first change backs up the original as `server.properties.harness-backup`; copy it back to undo. Bots and the camera
are added to `whitelist.json` with their offline UUIDs (the whitelist stays on). `world_setup` changes the time,
weather and gamerules `advance_time`, `advance_weather`, `locator_bar`. The smoke test builds a 15×15 stone stage in the
sky near spawn (around x=16, y=119, z=-40).

## Quick start

```sh
node harness/run-scenario.js smoke
```

Stops a running sandbox (cleanly, through the bridge), deploys the newest jar with `brain.mode: external`, starts the
server, builds the stage, a bot joins, the vessel manifests next to it, the real client joins as the camera, takes a
screenshot of the vessel, runs a particle vortex, takes a second screenshot, checks against a control shot (vessel
vanished) that the vessel was really in frame, then closes the client and stops the server. About 2 minutes; screenshots
land in `harness/out/`. No API key is used.

From Claude Code the same chain is a handful of tools: `server_deploy` → `world_setup` → `bot_spawn` →
`god_tool vessel_go` → `camera_launch` → `camera_screenshot` → `god_tool particles` → `camera_screenshot` →
`camera_close` → `server_stop`.

## MCP server

The repo-root `.mcp.json` registers `god-harness` (`node harness/src/mcp-server.js`, started from the repo root;
approve it when Claude Code asks). A server it starts is its child process: when the MCP server exits it stops that
server cleanly first.

| Server and world | |
|---|---|
| `server_status` | folder, running (status ping), pid, owned by the harness or not, key properties, plugin jars, bridge settings and whether it answers |
| `server_deploy` | newest `build/libs/gptgodmc-*.jar` → `plugins/` (older ones removed), test settings, restart; `brainMode` optional |
| `server_start`, `server_stop`, `server_restart` | start with Java 25 and wait for "Done"; stop via stdin (own server) or the bridge (any server with the plugin); `force` kills |
| `server_command` | console command: via the bridge (returns feedback) or stdin |
| `server_log`, `server_wait_for_log` | tail `logs/latest.log` (regex filter); wait for a line after a cursor |
| `sandbox_configure` | write the test settings above |
| `world_setup` | noon, clear, cycles stopped, locator bar hidden; optional peaceful |

| Bots | |
|---|---|
| `bot_spawn` | join as `username` (whitelisted first; optional `op`, `gamemode`) |
| `bot_list`, `bot_disconnect`, `bot_status`, `bot_inventory`, `bot_nearby` | |
| `bot_say` | chat, or a command if it starts with `/` (`/pray ...`); returns the action's seq |
| `bot_teleport` | reliable placement with a server `tp` |
| `bot_goto`, `bot_follow`, `bot_stop` | pathfinding (see the walking limitation below) |
| `bot_look_at`, `bot_dig`, `bot_attack` | dig at coords or the nearest block of a type; attack an entity type, player or id |
| `bot_interact` | right-click (or attack) the nearest `interaction` entity, i.e. touch or strike the vessel |
| `bot_equip`, `bot_toss` | |
| `bot_events` | the bot's log (`since`, `limit`, `kinds`) |
| `bot_wait_for` | block until a new log entry matches a regex |

| God (debug bridge) | |
|---|---|
| `god_status`, `god_journal`, `god_memory`, `god_tools`, `god_perception` | reads (`god_perception consume=true` drains pending events) |
| `god_wait_for` | poll the journal until an entry of given kinds / regex appears |
| `god_tool` | run one power directly, e.g. `{name: "particles", input: {target: "vessel", shape: "vortex", particle: "end_rod"}}` |
| `god_wake`, `god_inject_event`, `god_console`, `god_reset_memory` | |
| `god_view` | the plugin's own ray-traced view (`eyes`/`vessel`/`overhead`/`panorama`/`orbit`), not the real client |

| Camera (the real client) | |
|---|---|
| `camera_launch` | start Minecraft 26.3 as `HarnessCam`, join the sandbox, spectator mode; 30-60 s |
| `camera_aim` | orbit the vessel or a player (`distance`, `angle`, `height`), or explicit `x,y,z` + `yaw/pitch` / `lookAt` |
| `camera_screenshot` | optional aim, then a real screenshot: returned as an image and saved to `harness/out/<time>-<label>.png` |
| `camera_status`, `camera_close` | |

Typical bot loop: note `latestSeq`, act (`bot_say "/pray ..."`, `god_tool ...`), then `bot_wait_for` since that seq.

### External god mode (Claude Code plays god)

With `brain.mode: external` the plugin never calls Claude; you are the god: `god_perception consume=true` (what the god
perceives), `god_tools` (powers and schemas), `god_tool` (act), `camera_screenshot` / `god_view` (look), bots
(`bot_wait_for`) to confirm what players experienced.

## How the camera works

The camera runs `%APPDATA%\.minecraft\versions\26.3` directly with Java 25: the classpath, JVM arguments and asset
index come from `26.3.json`, the game directory is `harness/client/game` (its own `options.txt`: muted, no pause on
lost focus, no onboarding screens, 1280×720), the player is an offline `HarnessCam` and `--quickPlayMultiplayer` joins
the sandbox. The launcher and its account files are never used or opened, and the user's own options and saves are
untouched.

Once joined, the camera is put in spectator mode and aimed only with server commands
(`tp HarnessCam x y z yaw pitch`). Screenshots are a `PrintWindow(PW_RENDERFULLCONTENT)` capture of the game window's
client area: the real rendered frame, taken while the window stays behind other windows. Nothing is typed or clicked;
no window is focused. (Posting an F2 key to the window does not work: 26.3 renders through SDL, whose window ignores
key messages while unfocused, and faking focus would make the game capture the mouse.)

The game window does appear when the client starts (it may flash in the taskbar); the harness then pushes it behind
other windows without activating it. Do not minimize it while testing: a minimized window renders nothing (the harness
restores it without focus before a capture).

## Scenarios

```sh
node harness/run-scenario.js                 # list
node harness/run-scenario.js smoke           # full chain, manages the server itself
node harness/run-scenario.js all             # bridge, vessel, powers against a running server
node harness/run-scenario.js prayer          # full loop with Claude: opt-in, spends API money
```

| Scenario | |
|---|---|
| `smoke` | the chain above; 17 steps; no API key |
| `bridge` | every bridge endpoint, a direct `whisper` reaching the bot, `omen`, `/event`, an overhead view, unknown-tool error |
| `vessel` | `vessel_go` to the bot, the bot sees the display entities, right-click and punch are journaled |
| `powers` | `give_item`, `heal`, `apply_effect`, `smite` warning, `omen`, `conjure` cage, `set_time`, each seen by the bot |
| `prayer` | `/pray`, then a journal `speech` and `cycle` within 90 s, and the speech reaching the bot |

Each step prints PASS/FAIL/SKIP/WARN (WARN = optional check); exit code 1 if anything failed. Bot name defaults to
`HarnessBot` (`--bot`).

**Full-loop tests spend real API money** and need the Anthropic key, which only you set: run the server in your own
terminal with `node harness/sandbox.js start` and type `gptgod setkey <key>` at its console, then
`sandbox_configure brainMode=live` and restart. The harness never reads, prints or passes the key.

`npm run selftest` (in `harness/`) checks the harness without any server: helpers, a mock bridge, the bridge client and
the MCP server over stdio against a fake sandbox folder.

### Command line

`node harness/sandbox.js status | configure [--brain-mode m] | deploy | start | stop [--force] | log [n] [--grep re] | cmd "<command>"`.
`start` and `deploy` keep the server in the foreground of that terminal (type console commands; Ctrl+C stops it
cleanly).

## Bots

mineflayer 4.39.0 (the newest release) speaks at most Minecraft **26.1**: its protocol data has no 26.3 (protocol 777).
26.3 support exists only as open community pull requests across minecraft-data, node-minecraft-protocol and mineflayer.
The harness therefore keeps the released mineflayer and the sandbox runs **ViaVersion + ViaBackwards**, which translate
between the 26.1 bots and the 26.3 server; the real client connects natively. The harness also sends the serverbound
`client_tick_end` packet that 26.3 requires and 4.39.0 never sends (without it a bot is kicked a second after joining,
PrismarineJS/mineflayer#4189).

Verified through ViaBackwards: join, chat, commands (`/pray`), whispers, titles, entity and particle tracking, the
vessel's display entities and text, touching and striking the vessel, inventory, health, effects, time and weather.

## Limitations

- **Walking on natural terrain is unreliable.** On flat or cleared ground bots walk, step down and pathfind normally.
  On some natural spots Paper keeps resetting a bot's movement (no log line; it comes from Paper's move checks), and
  `bot_goto` then times out and reports the number of server resets. Place bots with `bot_teleport`, or walk on flat
  ground. The cause is not identified yet: the bot's view of the world matches the server's block for block.
- The camera player `HarnessCam` is a real (spectator) player on the server; the plugin sees it join.
- One camera client at a time. Windows only. The window must stay visible (not minimized) to be captured.
- Bots and the camera are offline accounts, so the sandbox runs `online-mode=false`, bound to 127.0.0.1.
- `god_view` is the plugin's own renderer; only `camera_screenshot` shows what a player's client draws.
- Joining an offline server shows a "Chat messages can't be verified" toast for ~10 s; `camera_launch` waits it out.

## Baritone and client mods

Builds exist: Baritone v1.20.0 targets 26.3 (Fabric/Forge/NeoForge), Fabric Loader 0.20.1 and Fabric API 0.166.0
support 26.3. None is installed: Baritone automates the client's *own* player through chat commands typed into the
game, which here would need keystrokes into a focused window (or another control mod), and everything it would do for
these tests — players acting in the world — bots already do from the server's point of view. A purpose-built Fabric
client mod would help only for things the camera cannot do today (a free camera that is not a player in the world,
framebuffer captures at any size, HUD toggling); that would mean installing Fabric into the camera's game directory and
writing the mod.

## Environment

| Variable | Default | |
|---|---|---|
| `GOD_SANDBOX_DIR` | `<repo>/../minecraft/srv1` | sandbox server folder |
| `GOD_SERVER_JAR`, `GOD_SERVER_XMX` | `paper.jar`, `4G` | |
| `GOD_JAVA` | newest Java 25+ found | java.exe for the server and the client |
| `GOD_CLIENT_MCDIR`, `GOD_CLIENT_VERSION` | `%APPDATA%\.minecraft`, `26.3` | installed client |
| `GOD_CAMERA_NAME`, `GOD_CLIENT_WIDTH`, `GOD_CLIENT_HEIGHT`, `GOD_CLIENT_XMX` | `HarnessCam`, `1280`, `720`, `3G` | |
| `GOD_CLIENT_DIR` | `harness/client` | camera game dir, natives, log |
| `GOD_OUT_DIR` | `harness/out` | screenshots and views |
| `GOD_MC_HOST`, `GOD_MC_PORT`, `GOD_MC_VERSION` | sandbox address, `auto` | point bots at another server |
| `GOD_BRIDGE_URL`, `GOD_BRIDGE_TOKEN`, `GOD_BRIDGE_FILE` | `<sandbox>/plugins/gptgodmc/bridge.json` | bridge of a remote server |
| `BOT_NOTABLE_ENTITIES` | display/interaction entities, lightning, tnt, ... | entity types whose spawns bots log |
| `GOD_HARNESS_ENV` | `harness/.env` | alternative env file (see `.env.example`) |

`harness/out/`, `harness/client/` and `harness/.cache/` are git-ignored.
