# God Manager screen (client mod): plan

A real screen for operators who run the GPTGOD client mod. It does everything the chest menu does and
more, with text boxes, sliders, search and live data. Players without the mod (vanilla Java, Bedrock)
keep the chest menu exactly as it is. The server stays the source of truth: every action is checked
(`gptgodmc.admin`), validated and carried out in the plugin, and the screen only shows the answer.

## Decisions (agreed 2026-11-10)

| Question | Answer |
|---|---|
| Layout | Left sidebar (Pantheon with each god's head, New god, Server pages) + tabs across the selected god's page. Simple Voice Chat style: dark panels, tidy lists, icon buttons. |
| Features in the first version | Command console, live journal feed, mortals editor, sliders and searchable config. All four. |
| God heads | The head of the god's own vessel skin, with a mood-coloured ring. |
| Opening | Key `G` (rebindable), `/god manager` and the nether-star item open the screen for mod users. `/god manager chest` still opens the chest menu. |
| Costs | **New:** an API cost tracker in the screen (last minute, hour, day, session, all time). |
| Kept out of the screen | The API key. Restart-only and port settings are shown read-only. Raw prompts and perception stay in the web mind viewer. |
| Dangerous actions | Erasing or removing a god: type the god's name. Other dangerous actions (forget a player, clear notes, reveal true form): the button turns into Confirm / Cancel in place. |
| Libraries | No UI library. Mod Menu as an optional integration. |
| Live data | While the screen is open the server pushes changes for the page you are on (about twice a second, only when something changed). It stops when the screen closes. |

Two points where I chose something slightly different from what was picked, and why:

- **Heads.** Instead of reading the head from the resource pack (which fails for anyone who declined the
  pack), the plugin sends each god's head face as a 16×16 image taken from the same skin atlas the pack
  uses (about 1 KB per god, sent once per look). It matches the body exactly and always works.
- **Costs.** CLAUDE.md says costs are never shown in game. This screen becomes the one exception: only
  for `gptgodmc.admin` holders with the mod, and switchable off with `client-mod.show-costs` (default
  on). Chat, the chest menu and the journal still never show costs. CLAUDE.md gets updated to say so.

## Screens

```
+-----------+---------------------------------------------------+
| PANTHEON  | Vaelor › Commands                        [<] [x]  |
| (o) Vaelor| Overview Commands Mortals Nature Body Mind Journal |
| (o) Morth +---------------------------------------------------+
| + New god | [ type a command…                       ] [Send]  |
|-----------| Presets: Speak · Bless · Quest · Appear · …       |
| SERVER    | Pending                                           |
|  Switches |  #12 Build a temple        seen 1/3          [x]  |
|  Config   |  #13 (standing) wrathful   8 min left        [x]  |
|  Costs    | Recent reports                                    |
+-----------+---------------------------------------------------+
```

**Sidebar.** Every god with its head, name in its colour, and a small state dot (awake, thinking,
paused, off, slain). Hover: a card with epithet, archetype, power, mood, the five heart bars, state,
body (formless / walks / true form), pending commands, health when wounded. Click: that god's page.
Below: New god, and the server pages (Quick switches, Config, Costs).

**Pantheon** (the screen's home when there are several gods): the same gods as large cards.

**A god's tabs**

| Tab | What it holds |
|---|---|
| Overview | Big head, name, epithet, state, mood, heart bars, body, health. Buttons: Wake now, Pause/Resume, Turn off/on, Summon to me, Vanish. Last few journal lines and pending commands at a glance. |
| Commands | The console: a text box that keeps focus; Enter sends a binding command; Up/Down recalls history (kept in the mod's config, last 50); the server's refusal appears under the box. Preset buttons. Pending commands live, with status and Cancel. Recent reports (the god's `command_done` answers to you). |
| Mortals | Search box + list with player heads, favor tier colour, online dot. Selecting one opens the editor beside the list: favor as a slider (-10..10), title, summary, notes and deeds as lists with delete buttons and an "add" text box each; Clear notes / Clear deeds / Forget (confirm in place). "Open a record" box for a name the god has never met. |
| Nature | The ten dials as sliders with the archetype's value marked, Reset. Below: the archetypes with descriptions; choosing one is a rebirth (type the god's name to confirm). |
| Body | Mood buttons and "calm its heart"; presence (summon, fly to me, follow me, vanish, true form, end true form); looks as a grid (name + head preview of each look); voice list, effect list, Hear it now. |
| Mind | Power (lesser/greater/supreme), model, effort, each with "server default". Remove / Erase this god (type its name). |
| Journal | Live feed of thoughts, acts, speech, errors and notes, newest at the bottom, auto-scroll unless you scrolled up. Filter box and kind toggles. Never perceptions or prompts. |

**Server pages**

| Page | What it holds |
|---|---|
| New god | Name box (empty = it names itself), archetype picker, power picker, Create. Errors appear under the field that caused them, with the server's own words (an invalid name from `GodNames.operatorProblem`, "Unknown archetype…", too many gods, round mode on, missing permission). |
| Quick switches | The ten on/off switches as toggles. |
| Config | Every setting of the chest menu's Server config, as one searchable list grouped by the 8 tabs: toggles, number boxes with − / + and a slider, dropdowns. Out-of-range or unparsable values are refused by the server and shown under the field. Restart-only and port settings are read-only with a note. |
| Costs | Spend in the last minute, hour, 24 hours, this session and all time; per god; requests and tokens; a bar chart of the last 60 minutes. |

**Moving around.** Back (button, Esc, mouse-back) returns to the previous page and only closes the
screen from the first page. Ctrl+1…7 switch tabs, Ctrl+F jumps to the search box, Tab moves between
fields. The screen does not pause the game and remembers the last god and tab. Nothing is drawn on the
HUD when it is closed.

**Feedback.** Each action button shows a small spinner until the server answers, then the server's
message beside it (green) or the error under the field (red). Messages the plugin would have put in
chat while the screen is open appear as a line at the bottom of the screen instead.

## Protocol additions

Feature name `gui` in the handshake (`ModSessions.SERVER_FEATURES` gains it; the mod lists it). The
plugin starts listening on `gptgod:main` for client messages. All messages are JSON with `t`.

Client → server (only from players with the `gui` feature and `gptgodmc.admin`; at most 8 KB each and
20 a second per player, more is dropped; every string clipped):

| `t` | Fields | Meaning |
|---|---|---|
| `gui_open` | `id` | The key was pressed: may I open? The server answers `gui_open` or `gui_result` with an error. |
| `gui_sub` | `id`, `realm`, `part`, `args` | Show me this part and keep it fresh (replaces the previous subscription). `args`: mortal name, search text, config tab, journal `since`. |
| `gui_act` | `id`, `realm`, `action`, `args`, `confirm` | Do something. `confirm` carries the typed name or `true` for dangerous actions. |
| `gui_close` | | The screen closed: stop pushing. |

Server → client:

| `t` | Fields | Meaning |
|---|---|---|
| `gui_open` | `realm`, `several`, `costs` | Open the screen (answer to the key, `/god manager`, the star). |
| `gui_data` | `part`, `realm`, part fields | One part of the state: `catalog` (archetypes, looks, voices, effects, presets, switches; once), `pantheon` (every god: id, name, epithet, archetype, colour, power, state, thinking, mood, heart, body, pending count, health, look, removable), `head` (look, 16×16 pixels), `god`, `commands`, `mortals` (a page of 50 + total), `mortal`, `nature`, `body`, `mind`, `journal` (entries after `since`, at most 30), `config` (one tab's options and values), `costs`. Each stays under 32 KB. |
| `gui_result` | `id`, `ok`, `field`, `error`, `message`, `needs` | The answer to one `gui_act`/`gui_open`. `field` names the input the error belongs to. `needs: "name"` or `"confirm"` when a confirmation is missing. |
| `gui_note` | `text`, `level` | A message that would otherwise go to chat while the screen is open. |

Actions (`gui_act.action`) map one to one onto what the chest menu does, through the same code:
`wake`, `pause`, `dormant`, `mood`, `calm_heart`, `summon`, `fly_to`, `follow`, `vanish`, `true_form`,
`end_true_form`, `look`, `voice`, `voice_effect`, `voice_sample`, `dial` (key, value or reset),
`power`, `model`, `effort`, `rebirth`, `remove_god`, `create_god` (name, archetype, power), `command`
(text), `preset`, `cancel_command`, `favor` (player, value), `note_add`, `deed_add`, `note_del`,
`deed_del`, `notes_clear`, `deeds_clear`, `forget`, `title_set`, `summary_set`, `switch` (key),
`config_set` (key, value).

## Plugin side

New files (no behaviour change for the chest menu):

- `client/ModGuiMessages` (pure): builds every `gui_*` message from plain records; tested against the
  golden files.
- `client/ModGuiRequests` (pure): parses and limits client messages (size, rate, clipping, known
  actions), so hostile input never reaches the action code.
- `gui/ModGuiActions`: carries actions out. It lives in the `gui` package so it calls the chest menu's
  own methods (`GodManager.perform`, `snapshot`, `mortals`, `dials`, `resolveName`, `MenuLayout`
  presets and favor rules, `Advanced` options and clamping, `GodCommand.create/remove/rebirth/runPower`,
  `Edicts.issue`, `GodNames.operatorProblem`). Checks `gptgodmc.admin` on every message, and the
  pantheon permission for creating and removing gods.
- `gui/Feedback`: collects what `GodManager.say` / `GodCommand.msg` would send to chat during an action
  so it goes into the `gui_result` instead (and into `gui_note` while the screen is open).
- `client/ModGui`: the Bukkit glue: incoming channel, per-player subscription, the twice-a-second push
  (hash per part, sent only on change), `/god manager` and star hand-off.
- `god/Costs` (pure): per-god spend in one-minute buckets for 24 hours, session and all-time totals
  (all time is summed from `usage.csv` at start, off the main thread).
- `god/vessel/VesselHeads`: the 16×16 head face of a look, cut from `VesselTextures`' atlas.

Small additive edits in files other sessions own (re-read right before editing): `ModSessions`
(feature `gui`), `ModLink.enable` (one call), `GodManager.say` and `GodCommand.msg` (one line each for
`Feedback`), `GodCommand.manager` and `GodManager.onInteract` (mod users get the screen;
`/god manager chest` forces the chest), `UsageLog.record` (one call to `Costs`), two `GodManager`
helpers made package-visible, `config.yml` (`client-mod.show-costs`), CLAUDE.md.

## Mod side

All under `client-mod/src/main/java/net/bigyous/gptgod/client/gui/`:

- `GuiProtocol` (pure): decodes `gui_*` messages into records (clamped, clipped), encodes requests.
- `GuiModel` (pure): the screen's state: parts received, navigation history (back stack), pending
  requests by id with their field, results and inline errors, command history, filters. Unit tested.
- `kit/`: the widgets in one visual style: `Panel`, `TabBar`, `Sidebar`, `ListView` (scrolling,
  wheel, drag bar), `TextBox` (on vanilla `EditBox`), `Slider`, `Toggle`, `Dropdown`, `IconButton`,
  `ConfirmButton`, `HeadIcon`, `Bars`, `Tooltip`, `Toasts`.
- `ManagerScreen` + one class per page (`OverviewPage`, `CommandsPage`, `MortalsPage`, `NaturePage`,
  `BodyPage`, `MindPage`, `JournalPage`, `PantheonPage`, `CreatePage`, `SwitchesPage`, `ConfigPage`,
  `CostsPage`).
- `GuiSamples`: sample data for the game test and for `/godfx manager` (opens the screen offline).
- Small additive edits: `GodClientMod` (key `G`, route `gui_*` messages), `ModConfig` (feature `gui`,
  command history), `fabric.mod.json` (Mod Menu entry point), `build.gradle` (Mod Menu compile-only, if
  a 26.3 build exists; otherwise the entry is left out and noted).

## Phases

1. **Protocol and plugin.** Messages, requests, actions, feedback capture, costs, heads; golden
   fixtures; plugin tests. `/god manager` hand-off.
2. **Screen shell.** Widget kit, sidebar with heads and hover cards, tabs, back stack, Pantheon,
   Overview, Commands console. First screenshots.
3. **Editing pages.** Mortals, Nature, Body, Mind, New god.
4. **Server pages and feeds.** Journal, Quick switches, Config with search, Costs, Mod Menu.
5. **Together.** Both builds green, a run against a local test server if the dev client can join it,
   screenshots of every page, hand-over note.

Screenshots are sent after phases 2, 3 and 4. At the end, a screenshot of every page is copied into
`previews/client-mod/manager/`.

## Tests

- **Shared fixtures** in `client-mod/src/test/resources/protocol/`: one golden file per `gui_data`
  part, `gui_open`, `gui_result` (ok, field error, needs name), `gui_note`, and each request type. The
  plugin's tests check it writes exactly these and parses the request files; the mod's tests check it
  decodes the data files and writes exactly the request files.
- **Plugin unit tests:** no message without the `gui` feature; rate and size limits; unknown actions and
  oversized strings refused; every action name maps to a chest-menu `Kind` (so none can drift); name
  confirmation rules; config values clamped by the same `Advanced` rules; `Costs` windows; head image
  size and determinism.
- **Mod unit tests:** decoding with hostile values; back stack; request/result matching and inline
  errors landing on the right field; command history; journal merging and filters; config search.
- **Client game test:** opens the screen with `GuiSamples` on every page (including an error under the
  New god name field, a hover card, a confirm-in-place and a type-the-name box) and saves screenshots.
- Existing chest menu tests keep passing unchanged.

## Status (2026-11-10)

Built: everything above except the points below. Both builds' tests pass (plugin `ModGuiTest`,
`CostsTest`; mod `GuiTest`), and `ManagerShotsTest` opens the screen in a real client on sample data and
saves a screenshot of every page (copied to `previews/client-mod/manager/`).

Not done:
- **Not yet run against a live server.** The screen is tested on sample data (the protocol's golden
  files) and the plugin side by unit tests; the two have not been joined in one session with a real
  operator. Try `/god manager` with the mod on a test server before relying on it.
- **Mod Menu** is left out: no Mod Menu release for 26.3 could be confirmed. The key (G) and
  `/godfx settings` open the screens.
- "Recent reports" on the Commands tab are read from the journal ("Command #N done / impossible /
  cancelled"), so a command dropped after too many thoughts shows only in chat.
- The pages live in `GodPages`, `MortalsPage` and `ServerPages` and the widgets in one `Ui` class
  (immediate mode), rather than one class per page and widget as first planned.

Try it without a server: `/godfx manager` opens the screen on sample data in any world.

## Added 2026-11-10: revive

A slain god's Overview shows one button, **Revive**. Action `revive` (no chest-menu equivalent; it is
`/god revive` with a rite): `god/ReviveRite` tears a rift open four blocks in front of the operator
(pale jagged edges, a dark violet heart, motes drawn in) to a rising charge for about three seconds, then
bursts (boom, gateway, thunder; flash, shake and pulse for mod players). At the burst the god is revived
by `Godslayer.revive` and, where bodies are on, its vessel is sent to step out where the rift was.
Particles and sounds only. Not yet seen in game: `previews/rite-revive.png` shows the rift's shape over
time, `previews/client-mod/manager/21-revive.png` the button.
