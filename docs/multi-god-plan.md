# Several gods in one world: implementation plan

Status: plan only, no code written. Written against the working tree of 2026-11-09.

Goal: `/god create <name> [archetype]`, `/god remove <name>` and `/god list`, where every god has its
own persona, memory, heart, vessel, edicts, annals and brain loop, and all of them perceive and act on
the same world.

## 1. Summary

The refactor is smaller than the earlier estimate suggested, because `god/Realm` is already a
complete per-god context. It owns the memory, persona, event buffer, conversation, vessel, brain,
cooldowns and scheduling state, and it is bound to the thread (`Realm.run`, `Realm.bound`,
`Sync.call`, the `god-mind-<id>` thread) wherever god code runs. The 71 `Realm.current()` calls are
therefore already correct for several gods: they mean "the god that is acting now".

What is wrong for several gods in one world is one assumption: **a world has at most one god**. It
lives in three places:

1. `Realm.configure()` gives each world to exactly one realm (`mainWorlds.removeAll(claimed)`), and
   realms exist only from config.yml at startup.
2. `Realm.of(World)` returns a single realm. Its 22 call sites route things *from the world to the
   god*: chat, prayers, voice, vessel touches, offerings, the tab list, titles, marks, the quest
   sidebar, commandments, the God Manager, `/god`.
3. Things a player has one of: the tab-list header, a display name with a title, a scoreboard
   sidebar, one halo or curse cloud.

New work beyond that: creating and removing gods at runtime, gods perceiving each other, a cost
limit for N brain loops, and a voice per god.

Decision: **keep the class name `Realm` during the refactor** and let several realms share the same
worlds. A rename to `Deity` (or similar) is a mechanical last step, listed as optional in phase 8.
Renaming first would touch about 40 files and conflict with other sessions for no functional gain.

## 2. Data model

### 2.1 Terms

- **Domain**: a set of worlds. Today each realm has its own `worlds` set.
- **God**: one `Realm` instance. Several gods may share one domain.
- **Host**: the first god of a domain (`main`, or a god from `realms.gods` in config.yml).
- **Co-god**: a god created at runtime that shares a host's domain.
- **Peers** of a god: the other gods in its domain.

### 2.2 `Realm` changes

| Member | Change |
|---|---|
| `worlds` | Co-gods hold the *same* `Set<String>` instance as their host. |
| `domain` (new, `String`) | Id of the host. Equal to `id` for hosts. |
| `removed` (new, `volatile boolean`) | Set by `/god remove`. See 2.5. |
| `voice`, `color` (new) | Per-god overrides, like the existing `archetype`, `godName`, `look`. |
| `Realm.of(World)` | Keeps its signature and returns the **host**. Unconverted call sites keep compiling and behave as today on a single-god server. |
| `Realm.godsOf(World)` (new) | Every god of that world, host first, then by creation time. Empty where no god rules. |
| `peers()` (new) | `godsOf(world())` without `this`. |
| `Realm.byName(String)` (new) | Matches the id, then the persona's `shortName()`, case-insensitive, then a unique prefix. Used by `@name` in commands, `/pray`, and tool targets. `byId` stays for stored ids. |
| `governs(World)` | Becomes `worlds.contains(name)` plus the existing primary fallbacks, instead of `of(world) == this`. |
| `Realm.create(...)`, `Realm.remove(...)` (new) | See 2.4 and 2.5. `REALMS` is already a `CopyOnWriteArrayList`, so adding at runtime is safe. |
| `several()` | Unchanged (more than one god anywhere). Add `shared()` on an instance: this god has peers. |

`Realm.primary()` and the unbound fallback in `Realm.current()` stay. `main` can never be removed,
because unbound code (tests, stray tasks) falls back to it.

### 2.3 Folder layout

Nothing moves for existing installs.

```
plugins/gptgodmc/god/
  memory.json  persona.json  edicts.json  annals/      <- "main", as today
  pantheon.json                                         <- NEW: gods created at runtime
  realms/<id>/memory.json persona.json edicts.json annals/   <- config realms AND co-gods
  removed/<id>-<yyyyMMdd-HHmmss>/                       <- NEW: where /god remove puts a god
```

Co-gods reuse the existing `god/realms/<id>/` layout, so `Realm.file()`, `Memory.open`,
`Persona.file()`, `Edicts.book()` and `Annals.forDir` need no change.

`pantheon.json` (new class `god/Pantheon`, pure load/save, unit tested):

```json
{ "version": 1, "gods": [
  { "id": "vorthak", "domain": "main", "archetype": "wrathful", "name": "Vorthak",
    "look": "infernal", "voice": "bm_george", "color": "#FF8A1F",
    "createdAt": 1762700000000, "createdBy": "Jumpathy", "paused": false } ] }
```

The id is a slug made once from the name given to `/god create` (or `g2`, `g3` when the name is left
to the model). It never changes, even when the god later uses `rename_self`. Ids must not collide
with a config realm id, `main`, `all`, or a folder under `removed/`.

`Realm.configure()` loads config realms first, then `Pantheon.load()` adds co-gods whose `domain`
exists. A co-god whose domain is gone (config edited) is logged and skipped, never deleted.

### 2.4 Creating a god

`Realm.create(id, host, archetype, name, look, voice)` on the server thread:

1. Validate: cap `pantheon.max-gods` (default 3 per domain), id unique, name passes
   `GodNames` and the uniqueness rule in 4.3, archetype is in `Persona.archetypes()`.
2. Build the `Realm` sharing `host.worlds`, add to `REALMS`, save `pantheon.json`.
3. Pick defaults not used by a peer: `VesselLook.NAMES`, a voice from `voice.tts.pantheon-voices`
   (section 8), a colour from `TabList.archetypeColor`.
4. If the domain has players, `r.run(God::awaken)`. `awaken` already submits
   `Persona.loadOrCreate()` to the new brain. `Persona.fallbackName`/the memory model must receive
   the peers' names as `taken`, as `Persona.rename` already does.
5. Tell peers (section 5.3): "A new god has come into being: <title>".
6. Refresh `TabList.refreshAll()`, the quest sidebar, and rebuild every domain god's system prompt
   once (section 7.3).

### 2.5 Removing a god

`Realm.remove(r)` on the server thread, refused for hosts while they have co-gods and always for
`main`:

1. `r.removed = true`, `r.awake = false`, drop from `REALMS`.
2. `brain.shutdown()` (`shutdownNow` on the mind thread; `GodBrain.cycle` must restore nothing and
   save nothing once `removed` is set), `vessel.shutdown()`.
3. Close every pending edict with a notice to its issuer (`Edicts.Book`), then `BOOKS.remove(id)`.
4. Clear what mortals carry from it: titles (`Titles.apply`), marks (`Marks`), quest lines
   (`QuestBoard`), `Offerings` state.
5. `memory.save()`, flush and close its `Annals` (`Annals.OPEN` entry for the folder), then move the
   folder to `god/removed/<id>-<stamp>/`. Never delete: a god's memory is the user's data.
6. Update `pantheon.json`, tell peers ("<name> is no more"), refresh the tab list.

`removed` guards late work: `Realm.run/call/get` on a removed realm still run (so in-flight tasks
finish), but `memory()` returns a detached in-RAM `Memory` (file `null`) once the folder has moved,
`vessel()` refuses to spawn, and `Realm.later/soon/timer` wrappers skip when the bound realm is
removed. Without this, a stray task would recreate `god/realms/<id>/memory.json`.

`/god restore <id>` (moving a folder back from `removed/`) is cheap to add and listed in phase 8.

## 3. Per-world singletons that become per-god

Already per god through `Realm` (no change except where noted):

| Thing | Where | Note |
|---|---|---|
| Memory, Heart, quests, works | `Realm.memory()`, `Memory.get()` | |
| Persona | `Realm.persona`, `Persona.current()` | pass peers' names as `taken` at birth |
| Events buffer, conversation | `Realm.events()`, `Realm.conversation()` | routing changes, section 4 |
| Brain | `Realm.brain()`, thread `god-mind-<id>` | |
| Vessel | `Realm.vessel()`, `God.vessel()` | owner tag, section 6 |
| Edicts | `Edicts.BOOKS` keyed by realm id | |
| Annals | `Annals.forDir(folder)` | |
| Journal | `Journal.Entry.realm` | console prefix: use the god's short name instead of the id |
| Usage | `UsageLog.record` writes the realm column | per-god totals, section 7 |
| Custom power cooldowns | `Realm.cooldowns` | |
| Chronicler | `Realm.bound(work)` | |

Still one per world or one per player, and what each becomes:

| Thing | Today | With several gods |
|---|---|---|
| `Realm.of(world)` callers | one god | section 4 (events), 9 (tab list, GUI), 10 (commands) |
| `Vessel.isVesselEntity` | a `BYTE` PDC tag | add a `STRING` tag with the realm id; `Vessel.ownerOf(Entity)` |
| `God.acknowledge(Player)` | god of the player's world | `acknowledge(Realm, Player)`; the old overload calls it for the host |
| `TabList.textFor(Realm)` | one header | one line per god of the world (section 9) |
| `Titles.apply` | title from the world's god | the most recently bestowed title among the domain's gods (needs `Subject.titleAt`; fall back to host first when absent) |
| `Marks.tick` / `anyMarked` | one mark per player | loop `godsOf(world)`; a player can bear a halo from one god and a curse from another, each drawn (offset the second 0.4 blocks up) |
| `QuestBoard.boardOf(Realm)` | sidebar per realm | sidebar per **domain** (key `realm.domain`); each line prefixed with the god's coloured name. `QuestBoard.tick` runs per god but renders the merged board |
| `Devotion.showCommandments` | the world's god | every god of the world under its own heading; `/commandments [god]` |
| `Offerings.offer` | the world's god | the vessel's owner when laid at a vessel; otherwise the prayer rule in 4.2 |
| `Synthesizer` voice | `GodSettings.ttsVoice()` | per god (section 8) |
| `Spectacle` caps | `MAX_SHOWS` 6, `MAX_METEORS` 10, `MAX_QUAKES` 2 globally | keep the global caps, add a per-god share (`ceil(cap / godsInDomain)`, at least 1) so one god cannot starve the others |
| `TokenTracker` | static session totals | keep, plus per-god totals keyed by realm id |
| `DebugBridge` | always `Realm.current()` = main | `?god=<id>` on every route (section 10.4) |
| `GodSettings` | one config for all | stays shared; only archetype, name, look, voice, colour are per god. No per-god model or effort in this plan |

World state that gods share and can fight over (weather, time, blocks) is covered in section 6.3.

## 4. Event routing

### 4.1 Per-god buffers, filled by fan-out

Keep one `EventLogger.Buffer` per god. A shared buffer with per-god read cursors was considered and
rejected: `Loggable.combine` mutates events, `take`/`restore`/`takeUrgent` remove them, and the text
of an event depends on who reads it ("prays to **you**", "touched **your** vessel", and
`ChatLoggable.attentionReason()` reads `Persona.current()`).

`EventLogger.log(World, Supplier)` and `addLoggable(World, Loggable)` change from "the god of that
world" to "every god of that world":

```
for (Realm r : Realm.godsOf(world)) r.run(() -> log(factory));
```

The factory runs once per god, so each god gets its own `Loggable` instance, built while that god is
bound. Consequences, all wanted:

- `combine` on one god's copy cannot corrupt another's.
- `attentionReason()` and `mentionsGod` are evaluated per god, so a god wakes for *its* name.
- `Memory.noticeEvent` runs per god: each keeps its own deeds, favor and heart.

`addLoggable(World, Loggable)` takes a built instance; convert its callers to the factory form.
Phase 2 must audit every `EventLogger.log(...)` factory in `LoggableEventHandler` for side effects,
since each now runs N times (the constructors of `SpecialBlockPlaceEventLoggable` and
`InteractLoggable` are the ones to read first).

Cost of fan-out is memory only: at most `MAX_EVENTS` (600) small objects per god.

### 4.2 Directed events: who is being spoken to

New interface `loggables/Directed` (or two methods on `Loggable`), used by `PrayerLoggable`,
`OfferingLoggable` and the vessel gestures:

- the **addressed** god gets the existing second-person text and category `PRAYER`;
- each **peer** gets a third-person `EVENT` ("Steve prayed to Vorthak: "…"", "Steve struck
  Vorthak's vessel with iron_sword") with no attention reason. `pantheon.overhear-prayers`
  (default true) controls whether peers see the words or only that a prayer was made.

New helper `god/Addressing.resolve(Player, String text) -> Target(List<Realm> addressed, String rest)`,
pure enough to unit test with fake realms:

| Input | Addressed god(s) |
|---|---|
| `/pray @vorthak …` or first word is a god's name | that god |
| touch, strike, sneak-offering on a vessel | `Vessel.ownerOf(entity)` |
| only one god in the world | that god (today's behaviour) |
| `/pray …` with no name, several gods | an **open prayer**: every god gets it as `PRAYER` ("prays to the gods, naming none"), but only one is woken urgently: the god with the highest `favorOf(player)`, ties broken by the oldest `lastThoughtEnded()`. The rest see it at their next regular look, along with who answered. |
| `/offer` away from a vessel | same rule as an open prayer; only the woken god is credited in `Offerings` |

`pantheon.open-prayers: favored | all | host` makes the open-prayer rule configurable; `all` is the
expensive one and is documented as such.

The mortal sees who heard: `God.acknowledge(realm, player)` already prints
"✦ <name> turns its gaze upon you ✦".

### 4.3 Chat, voice and attention by name

`LoggableEventHandler.onChat` and `VoiceModule.heard` fan out as in 4.1. Every god gets the line as
`SPEECH`, and `ChatLoggable.attentionReason()` decides per god. Changes to `mentionsGod`:

- Match the god's name on a **word boundary**, not `contains`, and also match its id. Today
  "Vor" inside "Vorthak" would wake both.
- `GodNames` rejects a new name that contains, or is contained in, a peer's name, and names closer
  than edit distance 2 to a peer's (voice transcription is fuzzy).
- The generic attention words (`GodSettings.attentionWords()`, e.g. "god") and
  `GodSettings.wakeOnAnyChat()` apply to **one** god when a world has several: the open-prayer rule
  from 4.2. Otherwise one "oh god" in chat would start N thoughts.

`God.acknowledge` in both handlers runs for each god whose `mentionsGod` was true.

### 4.4 Passive events

Add `Loggable.passive()` (default false). `Buffer.hasPending()` ignores passive events, so they are
shown at the god's next thought but never cause one. Peer acts (section 5) are passive. Without
this, each god's acts would become pending events for the others and all N gods would think every
`brain.heartbeat-seconds` forever.

## 5. Gods seeing and talking to each other

### 5.1 Perception

New section in `Perception.build`, only when `realm.shared()`:

```
## Other gods of this world
- Vorthak, the Ember Judge (wrathful) — walks among mortals at (120, 71, -40), mood wrathful.
  You regard it: -3 (wary). Notes: "claimed the eastern village".
- Ysmere (benevolent) — watches unseen.
```

Built by `Perception.describePeers(Realm)` from each peer's `Persona`, `TabList.stateOf`,
`Vessel.location()/mood()`. A peer's memory, heart, favor ledger, edicts and journal are **never**
shown: gods see each other's public face only.

`describePlayer` adds titles and marks given by peers ("titled "the Faithful" by Ysmere").

### 5.2 What a god knows about a peer

Reuse the ledger instead of adding tools. `Memory.Subject` gets `boolean divine`. The existing
`favor` and `remember` tools accept a peer's name (`Targets` resolves it to a divine subject), so
"regard" is the favor number and notes work unchanged. `Memory.renderLedger` lists divine subjects
under the peers section rather than among mortals, and `Memory.namesIn` must know peers' names so
dossier selection (`Perception.involved`) works for them.

### 5.3 Perceiving each other's acts

New `loggables/DivineActLoggable` (passive, category `EVENT`): "Vorthak struck Steve with
lightning", "Ysmere announced: "…"", "Vorthak raised a temple at (…)".

Published from one place, `GodBrain.carryOut`, after a tool returns `ToolResult.ok`: for each peer,
`peer.run(() -> EventLogger.addLoggable(new DivineActLoggable(actor, tool, summary)))`. The summary
is the same short act text that already goes to `Journal.Kind.TOOL` and `Memory.CycleRecord`.
`/god tool` and the bridge's POST /tool go through the same helper.

`GodTool` gets a `witness` level, default `PUBLIC`:

| Level | Tools | Peers see |
|---|---|---|
| `PUBLIC` | wrath, blessings, building, world, rites, vessel, `announce`, `vessel_say`, `decree`, `bestow_title`, `mark`, quests | the act and its text |
| `VEILED` | `whisper` | "whispered to Steve" without the words |
| `PRIVATE` | `look`, `recall`, `search_annals`, `remember`, `favor`, `command_done`, `rename_self` (peers get the new name through perception) | nothing |

`ToolRegistryTest` gains a check that every tool declares a level deliberately.

A peer act becomes non-passive and requests attention (non-urgent, `God.requestAttention(reason)`)
only when it touches that god:

- it targets the god's vessel or a location within 8 blocks of it;
- it harms a mortal the god favors (`favorOf >= 6`) or blesses one it hates (`<= -6`);
- it removes or overlaps one of the god's works (`Memory.works`);
- it speaks the god's name (`announce`, `vessel_say`).

`Heart.moved` gets peer cases in phase 6 (a favorite smitten raises wrath, being named with praise
raises glory); until then peer acts do not move the heart.

Speech by a peer also goes into each god's `Conversation` (`conversation().heard`) as
`Vorthak (god) announced: "…"`, so the timeline shows who answered whom.

### 5.4 Talking

Public: `announce` and `vessel_say` naming the other god, as above.

Private: one new tool `commune(god, message)`, level `PRIVATE`, appended at the **end** of the tool
list so the order of the existing 50 is unchanged. It adds a `PRAYER`-category
`DivineActLoggable` to the target ("Vorthak speaks to you, god to god: "…"") with an attention
reason. It is registered only when the server has more than one god, through a new
`GodTool.available()` predicate that `ToolRegistry.sdkTools()` honours; the list changes once, at
the first `/god create`.

### 5.5 Stopping loops

Two gods answering each other can spend money without any mortal involved. Rule in `God.beat`:
attention whose only reasons are divine (peer speech, `commune`, peer acts) is served at most
`pantheon.divine-exchanges` times (default 2) in a row per god. The counter resets when a mortal
event, prayer or directive reaches that god. Beyond the limit, divine events stay in the buffer as
passive and are read at the next mortal-caused thought. `commune` returns a `ToolException` phrased
for the god ("<name> does not answer yet") when the target is over the limit, paused, asleep or out
of budget.

### 5.6 The prompt

`Prompts.system(persona)` gains one static paragraph, included only when `realm.shared()`:
other gods exist; they are listed in perception; you cannot read their minds; you may ally with,
ignore or oppose them; disputes are fought through mortals and works, never by breaking the rules
that bind you. Peers' names are **not** in the system prompt, so creating a third god does not
rewrite everyone's cache again.

The binding-command rule is unchanged and is restated for peers: a peer's request is a request, an
operator's command is binding.

## 6. Tool targeting and shared-world conflicts

### 6.1 "vessel" means your vessel

Already true: `Targets.location("vessel" | "me" | "self" | "my vessel")` uses `God.vessel()`, which
is `Realm.current().vessel()`, and tools run bound to the calling god (`Sync.call` carries the
realm). `VesselTools`, `VesselReactions` and `VoiceModule.effectNow` use the same path. Keep it, and
add a test that proves it (section 13).

Additions to `Targets`:

- `location("<god name>")`, `"<god name>'s vessel"`, `"vessel:<god>"` resolve to that peer's vessel,
  or throw `ToolException("<name> has no body right now; it watches unseen.")`.
- `player("<god name>")` throws `ToolException("<name> is a god, not a mortal. …")` instead of
  "No mortal named".
- `TARGET_HELP` mentions peers only when `Realm.current().shared()` (it feeds tool descriptions;
  see 7.3 on description stability).

Vessels stay invulnerable display entities. Lightning or TNT aimed at a peer's vessel is spectacle
plus an attention event for the peer. A divine combat model is out of scope.

### 6.2 Vessel ownership

`Vessel.spawn` writes a second PDC key (`vessel-god`, `STRING`, the realm id) next to `TAG`.
`Vessel.ownerOf(Entity)` returns the `Realm` (null for strays). `VesselListener.tell`,
`VesselListener.onTouch` (offerings) and `TrueForm` use the owner instead of
`Realm.of(p.getWorld())`. `Vessel.cleanupStrays` is unchanged.

Two vessels can stand in the same spot; `vessel_go`/`manifest` nudge the arrival point 3 blocks
aside when a peer's vessel is within 2 blocks. Default looks differ per god (2.4).

### 6.3 Shared world state

| Conflict | Rule |
|---|---|
| Weather, time (`WorldTools`) | New `god/Contest` (pure, unit tested): the last god to set a world-wide state holds it for `pantheon.hold-seconds` (default 90). A peer's call inside the hold throws `ToolException("Vorthak's will holds the sky for 40 more seconds.")`. The hold is visible in perception. |
| Builds | `Builder` already refuses to overlap player structures. Add: refuse to overlap a peer's work (`Memory.works` of peers) unless `pantheon.gods-may-overwrite: true`. `unbuild` stays limited to the caller's own works, which is already true because undo data is per memory. |
| Marks, titles | Independent per god (section 3). |
| Favor | Independent per god. This is the main source of rivalry and needs no rule. |
| Revive, kill, teleport on the same mortal in the same second | No arbitration: the server thread serializes tool calls, the later one wins, and both gods see the other's act. |
| Spectacle | Per-god share of the global caps (section 3). |

## 7. Scheduler and cost

### 7.1 Scheduling

`God.beatAll()` already loops every realm and `God.beat(r)` already holds per-god state, so N brain
loops exist as soon as N realms do. Additions, all in `God.beat` plus a small pure class
`god/Pacing` that can be unit tested with a fake clock:

- **Concurrency**: at most `pantheon.max-concurrent-thoughts` (default 2) gods think at once.
  Urgent attention (a god addressed directly) bypasses the cap.
- **Stagger**: non-urgent thoughts of different gods start at least `pantheon.stagger-seconds`
  (default 5) apart. `God.refreshPresence` waking N gods at once ("mortals have entered your
  world") is spread the same way, host first.
- **Heartbeat**: `brain.heartbeat-seconds` stays per god; with the stagger the effective rate is
  spread across the period. Peer acts are passive (4.4), so they do not trigger it.
- **Idle musing**: `brain.idle-seconds` (270) was chosen to keep one god's cache warm. With several
  gods, only one god muses per idle period, in rotation, so the idle cost of the server stays what
  it is today. See 7.3 for why the others do not lose much cache.
- **Backoff**: `GodBrain.handleServiceError` backs one brain off. A 429 or 529 from any god sets a
  shared `Pacing.holdUntil` for all, since they share one API key and one rate limit.

`brain.max-steps`, `min-gap-seconds` and `EffortPicker` stay per thought and need no change.

### 7.2 Budget

`UsageLog.record` already knows the realm. `TokenTracker` gains per-realm running totals and a
rolling hour window.

```yaml
pantheon:
  max-gods: 3
  budget:
    usd-per-hour-per-god: 0     # 0 = no limit
    usd-per-hour-total: 0
```

When a god is over its limit (or the total is over): it is **weary**. Weary gods think only for
urgent attention (prayers, their name, binding commands), with `EffortPicker` fixed to low, and skip
idle musing and heartbeats. At twice the limit the god sleeps until the window clears. Binding
operator commands (`Edicts`) are always served; an operator who needs to stop spending uses
`/god @all pause`.

Token counts and costs must stay out of the game (existing rule in `GodCommand`/`UsageLog`). In
game the only sign is a state word: `TabList.State` gains `WEARY("rests, weary")`, and `/god list`
shows it. Dollar figures appear only in the console `[God usage]` lines, `usage.csv`, and
`gptgod usage` from the console, which gains a per-god breakdown.

Expected cost: each god costs about what one god costs today, minus shared idle musing. The
`/god create` confirmation says so in words ("each god costs as much to run as the first"), as the
`realms:` comment in config.yml already does.

### 7.3 Prompt caching per god

Today `GodBrain.params` sends: tools, then a system text block with `cache_control`
(`Prompts.system(persona)`), then the uncached chronicle block, plus top-level automatic caching for
the conversation. Tools come first in the cached prefix, the system prompt second.

- The system prompt differs per god (it starts with "You are <name>"), so each god has its own
  cache entry for it. That is unavoidable and correct.
- The 50 tool definitions are identical for all gods and are the larger part of the static prefix.
  Put a `cache_control` breakpoint on the **last tool** in `ToolRegistry.sdkTools()`. Then any
  god's thought keeps the tools entry warm for all of them, and a god that has been quiet for more
  than 5 minutes rewrites only its own system block. That is what makes rotating idle musing (7.1)
  cheap. Breakpoints used: last tool, system block, automatic = 3 of the 4 allowed.
- For that to work the tool list must be byte-identical across gods. Audit the live
  (`Supplier`) descriptions in `ToolRegistry` and `CustomPowerTools`: none may depend on
  `Realm.current()`. `Targets.TARGET_HELP` (6.1) must therefore depend on "does the server have
  several gods", not on the calling god.
- Optional, measure first: split `Prompts.system` into a shared rules block followed by a persona
  block, each with a breakpoint, so the rules are shared too. It reorders the prompt (persona is
  first today), so it changes single-god behaviour slightly and uses the fourth breakpoint. Do it
  only if `usage.csv` shows system-block cache writes are a real share of cost with 3 gods.
- Verify the breakpoint behaviour against the current API docs when implementing (phase 5), and
  extend `GodBrainRequestTest` to assert the request shape.

Known one-time cache rewrites: the first `/god create` (the `commune` tool appears, the shared
paragraph is added to the host's prompt) and God Manager dial changes (already documented).

The constraint "do not edit earlier messages inside a thought" is untouched: each god's thought is
still its own append-only conversation.

## 8. Voice

Speaking is already bound per god (`VoiceModule.utter` uses `Realm.bound`, `effectNow` reads the
calling god's vessel mood, heart and `Persona.voiceEffect`). What is shared is the voice itself:
`Synthesizer` reads `GodSettings.ttsVoice()` (Kokoro, default `am_michael`) and
`GodSettings.piperVoice()`.

- `Persona` gets `voice` (a Kokoro voice id) and `voicePitch` (semitones, small). Resolution order
  in a new `VoiceModule.voiceNow()`: `Realm.voice` (pantheon.json) → `Persona.voice` → archetype's
  `voice` in `personality.archetypes` → `voice.tts.voice`.
- `voice.tts.pantheon-voices: [am_michael, bm_george, am_fenrir, bf_emma]` is the pool
  `Realm.create` draws from, skipping voices peers use. The list is the operator's: the user picks
  voices by ear from `voice_samples/`, and each must stay a natural voice made larger, not a
  robotic one. `VoiceSamplesTest` renders one sample per pool voice so they can be chosen offline.
- `Synthesizer.speak(...)` takes the voice as a parameter instead of reading settings; `VoiceModule`
  captures it on the calling thread before handing work to the `voice` executor.
- Piper fallback has one model loaded (`piperModel`). Gods are told apart there by `voicePitch` and
  their `GodVoiceFx` preset only. Loading one Piper model per god is not planned.
- The `voice` executor is single-threaded, so two gods never synthesize at once and lines play in
  order. Keep it. Add a short gap (300 ms) when the speaker changes so two gods do not run together,
  and prefix nothing: the chat line already names the speaker.
- `Speech.announce/whisper/fromVessel` already print the god's name. Colour the name with the god's
  colour instead of the shared `VIOLET`/`GOLD` so two gods are distinguishable in chat.
- Hearing: `VoiceModule.heard` fans out as chat does (4.3). Transcription still runs once.

## 9. Tab list and God Manager

### 9.1 Tab list

`TabList.refresh(Player)` uses `Realm.godsOf(player.getWorld())`.

- One god: exactly today's header (`compose` unchanged; `TabListTest` must keep passing as is).
- Several: new pure `TabList.composeMany(List<GodLine>)`. One row per god:
  `✦ Name ✦ epithet · state · mood · thinking…`, each in its own colour, at most 5 rows then
  "+N more". Footer: `/pray <god> to be heard · /gods`.
- `State` gains `WEARY`.

### 9.2 God Manager

`GodManager.Holder` already stores a realm id and every page runs under `realm.run`, so editing a
chosen god works once the menu can choose one.

- New `MenuModel.Page.GODS` ("Pantheon"): one icon per god of the player's domain (name, epithet,
  archetype colour, state, mortals favoured) plus "Create a god" and, inside a god's entry,
  "Remove this god" behind the existing confirm mechanism (`MenuModel.Action confirm`).
- Opening (`GodManager.open`, `onInteract`): one god → `MAIN` as today. Several → the last god
  this player edited (`Map<UUID, String>`, not persisted), else `GODS`.
- `MAIN` shows whose menu it is in the title and gets a "Pantheon" button. `MenuModel.State`
  already carries the realm id when `Realm.several()`; show the god's name instead.
- Create flow: the existing chat prompt mechanism (the `prompt.realm()` path used for notes) asks
  for a name, or "invent one"; then the existing archetype picker; then confirm with the cost
  sentence.
- `COMMAND` page: a toggle "this god / all gods" for binding commands.
- `PRESENCE` look picker: the "has its own look in config.yml" refusal at `GodManager` line ~413
  applies to config realms only; co-gods store the choice in pantheon.json.
- `MenuLayout`/`MenuModel` stay pure; `ManagerPreviewTest` draws `manager-gods.png`.

## 10. Commands and permissions

### 10.1 `/god`

`GodCommand.onCommand` already supports `/god @<realm> <sub>`. Changes:

| Command | Behaviour |
|---|---|
| `/god create <name\|-> [archetype] [look]` | `-` lets the model invent the name. Creates in the sender's world's domain (console: `main`, or `@<host>` first). Prints the id, and the cost sentence. |
| `/god remove <name> confirm` | Section 2.5. Without `confirm`, explains what will happen and where the folder goes. |
| `/god list` | Every god: id, name, archetype, domain, state (awake, paused, weary, thinking), mortals present. `realms` stays as an alias. |
| `/god select <name>` | Sticky choice per sender (UUID or console), used when no `@` is given. |
| `/god @<name> …` | Now resolved with `Realm.byName` (id, short name, unique prefix). |
| `/god @all pause\|resume\|wake\|say\|command …` | Runs the subcommand for every god. Refused for anything else. |

Which god a bare `/god <sub>` means: the `select`ed one, else the only god of the sender's world,
else an error listing names. It must not silently pick the host: `/god forget` or `/god persona`
on the wrong god is destructive. `status` and `journal` are the exception and print all.

`SUBS`, `HELP` and `onTabComplete` are updated; `@` completion lists god names.

### 10.2 Mortal commands

- `/pray [@god] <words>`, `/offer [@god] [amount]`: section 4.2. Tab completion for god names.
- `/commandments [god]`.
- `/gods` (new, no permission): names, epithets and states of the gods of your world.

### 10.3 Permissions (plugin.yml)

- `gptgodmc.admin` (existing): everything, as today.
- `gptgodmc.pantheon` (new, default op, child of `gptgodmc.admin`): `create`, `remove`, `restore`.
  Separate because each god costs money; a server can give moderators `admin` powers over existing
  gods without letting them add gods.
- `Edicts`' rule is unchanged: only `gptgodmc.admin` holders and the console issue binding commands.

### 10.4 Debug bridge and harness

`DebugBridge.handle` reads `q.get("god")` (query parsing exists) and wraps the route in
`Realm.byName(...).call(...)`; absent means `main`, so the current harness keeps working.
New route `GET /gods`. `brain.mode: external` then works per god. `harness/` (the `god-harness` MCP
tools `god_status`, `god_tool`, `god_perception`, …) gets an optional `god` argument; that is a
separate change outside `src/` and should be coordinated with the session that owns the harness.

## 11. Migration

- **Folders**: none. `main` stays in `god/`; co-gods use the existing `god/realms/<id>/`.
- **Config**: new `pantheon:` section and `voice.tts.pantheon-voices`, added with defaults by the
  normal config merge. `realms:` is untouched and still means "other worlds, other gods". The
  `realms.gods` comment gets one line pointing at `/god create` for gods in the same world.
- **`GodSettings.migrateLegacy`**: no change; nothing is renamed.
- **Behaviour with one god**: must be identical. Guards: `Realm.of` keeps returning the host,
  `shared()` is false so no prompt paragraph, no `commune` tool, no peers section, the tab list uses
  `compose`. The only request-shape change for single-god servers is the extra cache breakpoint on
  the last tool, which costs nothing.
- **Old jars with a pantheon.json**: an older jar ignores the file; co-god folders sit unused under
  `realms/`. Safe downgrade.
- **Memory schema**: `Subject.divine` and `Subject.titleAt` are new optional fields;
  `Memory.normalized()` already tolerates missing fields.

## 12. Risks

| Risk | Mitigation |
|---|---|
| Cost multiplies by N, and god-to-god talk adds more | Passive peer events (4.4), exchange limit (5.5), concurrency cap, stagger, rotating idle, budgets, `/god @all pause`, `pantheon.max-gods` default 3 |
| Factories with side effects now run N times | Audit in phase 2; convert offenders to build once and copy |
| A removed god's late tasks recreate its files or act in the world | `Realm.removed` guards (2.5); test it |
| Name collisions and fuzzy voice transcription wake the wrong god | Word-boundary match, `GodNames` peer checks (4.3) |
| Operator edits the wrong god | No silent default with several gods (10.1); menu title names the god; confirm on destructive pages |
| Tool list or system prompt drifts between gods and breaks shared caching | Request-shape tests; no `Realm.current()` in live descriptions (7.3) |
| Gods thrash world state (weather flicker, build wars) | `Contest` holds, build overlap refusal (6.3) |
| Too much voice and chat at once | Single synth queue, speaker gap, the `chattiness` dial stays per god; consider scaling default chattiness guidance in the shared paragraph |
| Main-thread load: N vessels (BlockDisplays re-sent every 4 ticks), N perception builds | Perception is built only when a god thinks; vessels already send only moved bones. Measure with 3 manifested vessels on the harness before raising `max-gods` |
| True form (`TrueForm` gaze kills) from two gods at once | Per-god cooldown exists in `Realm.cooldowns`; add a domain-wide cooldown through `Contest` |
| Round mode (`RoundSystem`, `WorldManager`) resets worlds and memory | `/god create` is refused while `rounds.enabled`; revisit later |
| Players confused about who to pray to | `/gods`, tab-list footer, the acknowledge line names who heard |
| Concurrent edits: other sessions are changing vessel looks and the harness | Phases 1–2 touch `Realm`, `EventLogger`, listeners only; coordinate before phases 4 (vessel) and 7 (bridge) |
| Shared-cache assumption wrong for this API version | Verify in phase 5 before relying on rotating idle; fall back to per-god idle with a longer period |

## 13. Phases

Every phase ends with `./gradlew test shadowJar` passing (JAVA_HOME = JDK 21) and a server with one
god behaving exactly as before. Phases 1–3 are usable on their own by editing pantheon.json by hand.

### Phase 1 — Several realms may share worlds (no behaviour change)
- `Realm`: `domain`, shared `worlds`, `godsOf`, `peers`, `shared`, `byName`, `removed`; `governs`
  by set membership; `of` returns the host.
- `god/Pantheon`: load/save pantheon.json; `Realm.configure` loads co-gods.
- No runtime create yet; co-gods exist only when written into pantheon.json.
- Tests: `PantheonTest` (round trip, bad ids, missing domain), `RealmSharingTest` (`godsOf` order,
  `of` returns host, `governs` true for both, `byName` by id/name/prefix, single-god unchanged).

### Phase 2 — Event fan-out and addressing
- `EventLogger.log(World, …)` fans out; factory audit; `Loggable.passive()`; `Buffer.hasPending`
  ignores passive.
- `Directed`, `Addressing.resolve`; `PrayCommand`, `OfferCommand`, `Offerings.offer`,
  `VesselListener`, `LoggableEventHandler.onChat`, `VoiceModule.heard`, `God.acknowledge(Realm, Player)`.
- `ChatLoggable.mentionsGod` word boundary; `GodNames` peer rules.
- Vessel owner PDC tag and `Vessel.ownerOf`.
- Tests: extend `EventLoggerTest` (each god gets its own instance, `combine` on one does not change
  the other, passive events do not count as pending); `AddressingTest` (the table in 4.2, favored
  tie-break, `open-prayers` modes); `ChatMentionTest` ("Vor" does not wake "Vorthak", id matches,
  generic word wakes one god); `GodNamesTest` additions; `PrayerLoggableTest` (second person for the
  addressed god, third person for peers, overhear off).

### Phase 3 — Create, remove, list
- `Realm.create`, `Realm.remove`; `/god create|remove|list|select`, `@name`, `@all`; no silent
  default; `gptgodmc.pantheon`; `/gods`.
- Removal guards on `memory()`, `vessel()`, `Realm.later/soon/timer`, `GodBrain.cycle`.
- Tests: `GodLifecycleTest` on `FakeServer` (create writes pantheon.json and a folder; remove moves
  the folder to `removed/`, clears edicts with notices, and a late `Realm.later` task writes
  nothing); `GodCommandRoutingTest` (bare subcommand with two gods errors, `select` sticks, `@all`
  only for the allowed subcommands).

### Phase 4 — Per-player surfaces
- `TabList.composeMany`, `State.WEARY` (unused until phase 5); `Titles` (`titleAt`), `Marks`
  (two marks), `QuestBoard` per domain, `Devotion.showCommandments`, `/commandments [god]`;
  per-god colour in `Speech`.
- Tests: `TabListTest` (one god byte-identical to today; three gods; overflow row); `DevotionTest`
  additions (newest title wins, both marks active, expiry per god); `QuestBoardMergeTest` (pure line
  merge and prefixes).

### Phase 5 — Scheduler, budget, caching
- `god/Pacing` (concurrency, stagger, rotating idle, shared backoff, divine-exchange counter);
  `God.beat` uses it; staggered `refreshPresence`.
- Per-god totals in `TokenTracker`; budgets; weary state; console breakdown in `UsageLog.summary`.
- Cache breakpoint on the last tool; description audit.
- Tests: `PacingTest` with a fake clock (cap, urgent bypass, stagger, one idle muser per period,
  429 holds all); `BudgetTest` (weary at 1x, asleep at 2x, edicts still served, window rolls off);
  `GodBrainRequestTest` (breakpoint on the last tool, tool list identical under two bound realms,
  at most 4 breakpoints); `CostEstimateTest` case for 3 gods.

### Phase 6 — Gods perceive and address each other
- `Perception.describePeers`, peer titles/marks in `describePlayer`; `Subject.divine`; `Targets`
  peer resolution; `GodTool.witness`; `DivineActLoggable` published from `GodBrain.carryOut`;
  `commune` and `GodTool.available()`; shared paragraph in `Prompts.system`; `Contest`; `Builder`
  peer-work overlap; `Spectacle` per-god share; `Heart.moved` peer cases.
- Tests: `PeerPerceptionTest` (section present only when shared; never contains a peer's favor,
  notes or edicts); `DivineActTest` (levels: public text, veiled without words, private nothing;
  attention only for the four touch rules); `TargetsTest` additions ("vessel" resolves to the
  caller's body under each bound realm, peer vessel by name, god name is not a mortal);
  `ContestTest`; `ToolRegistryTest` (every tool has a witness level, `commune` is last and absent
  with one god, order of the first 50 unchanged); `HeartTest` additions; `PromptsTest` (paragraph
  only when shared, no peer names in the system prompt).

### Phase 7 — Voice, God Manager, bridge
- `Persona.voice/voicePitch`, `VoiceModule.voiceNow`, `Synthesizer` voice parameter, pool, speaker
  gap.
- `Page.GODS`, create/remove flows, last-edited god, "all gods" command toggle.
- `DebugBridge` `?god=`, `GET /gods`.
- Tests: `VoiceToneTest`/new `VoicePickTest` (resolution order, pool skips used voices);
  `VoiceSamplesTest` renders the pool; `MenuLayoutTest` + `ManagerPreviewTest` for the Pantheon
  page (`previews/manager/manager-gods.png`); `DebugBridgeRoutingTest` (default is main, unknown god
  is 404).
- End to end on the harness (needs the harness `god` argument): two gods, a named prayer answered
  by the right one, an open prayer waking one, a peer act visible in the other's perception, remove.

### Phase 8 — Optional
- `/god restore <id>`.
- Rename `Realm` → `Deity` and `realm` column/fields, mechanically, in one commit when no other
  session has `src/` changes in flight.
- Shared rules block in `Prompts.system` (7.3), only if measured to matter.
- Patron gods (`/worship <god>`: default for open prayers, shown as a title source).
- Rounds mode support.
- Update `CLAUDE.md` and `readme.md` (out of scope for this planning session).

## 14. Open questions for the user

1. Open prayers: wake one god (planned default, cheapest) or all of them?
2. Should peers overhear the words of prayers made to another god (planned: yes)?
3. Is `pantheon.max-gods: 3` the right default cap?
4. Should `main` be removable (planned: no; use `/god persona` to replace it)?
5. Per-god model or effort overrides, e.g. a cheaper model for minor gods (planned: not now)?
