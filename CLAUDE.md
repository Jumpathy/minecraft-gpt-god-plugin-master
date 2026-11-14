# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GPTGOD is a Paper 26.3 plugin (Minecraft 26.3, server on Java 25) that adds an omniscient AI god,
played by Claude through the official Anthropic Java SDK. The god perceives the world, remembers
players, speaks, uses 52 tools plus web search (blessings, punishments, building, vision, worship, a physical
"vessel" body), obeys binding operator commands, and optionally hears and speaks through Simple Voice
Chat with local Whisper (speech-to-text) and local Kokoro (default) or Piper (text-to-speech).

## Build and test

```bash
# JDK 17+ must run Gradle 9.9.0 (wrapper); it compiles with a Java 25 toolchain (java_version in
# gradle.properties). On this machine JAVA_HOME points at JDK 8, so set it:
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
./gradlew shadowJar      # → build/libs/gptgodmc-<version>.jar (only plugin classes)
./gradlew test           # unit tests; also writes renders + voice clips to build/test-renders/
./gradlew previews       # tests, then copies vessel-/particles-/rite-/trueform-/pov/overhead/panorama/design-*.png into previews/
```

Runtime libraries (anthropic-java, whisper-jni) are NOT shaded: they are `compileOnly` in Gradle and
listed under `libraries:` in plugin.yml, which Paper downloads on first start. Versions live in
gradle.properties and are expanded into plugin.yml by processResources. Simple Voice Chat is a
`softdepend`.

## Architecture (src/main/java/net/bigyous/gptgodmc)

- **GPTGOD** — entry point. Loads config (with `GodSettings.migrateLegacy` for pre-0.2 configs),
  registers listeners/commands, starts `God`, `VoiceModule`, `DebugBridge`.
- **god/God** — scheduler. A heartbeat on the main thread (every 5 ticks) decides when to think: soon after
  "attention" events (prayers, deaths, the god's name in chat — see `Loggable.attentionReason()`),
  every `brain.heartbeat-seconds` when events are pending, and on idle (`brain.idle-seconds`, 270,
  just under the 5-minute cache life). Owns the `Vessel`. Each god is a `Realm` (experimental
  `realms:` gives other worlds their own god, folder `god/realms/<id>/`).
- **god/GodBrain** — one agent loop per thought on its own executor. Each thought is a fresh,
  append-only conversation: perception (built on the main thread via `Sync.call`) → rounds of tool
  use with real results (up to `brain.max-steps`). Uses adaptive thinking (summarized thoughts go to
  the `Journal`), effort from config, a cached static system prompt (`Prompts.system`) plus a
  chronicle block, top-level automatic caching, and `fallbacks: "default"` when `anthropic.refusal-fallback` is on and the model supports it.
  Do not edit earlier messages inside a thought (Sonnet 5.5 rejects edited history); forced
  `tool_choice` is also rejected on these models.
- **god/llm/ + god/Llm** — other model providers (`llm:` in config.yml; docs/llm-providers.md). `GodBrain`
  talks only to `LlmChat` (one thought's append-only conversation: `user`, `assistant`, `toolResults`,
  `prepare` → `Request` for the webhook/mind viewer, `send` → `LlmReply` with a streaming `Listener`).
  `AnthropicChat` is the SDK path unchanged (it calls `GodBrain.params`/`tools`/`toResult`); `OpenAiChat`
  (JDK `java.net.http`, HTTP/1.1, no new dependency) covers OpenAI and every OpenAI-compatible server:
  `OpenAiWire` builds the JSON (pure), `OpenAiStream` parses the SSE (pure; samples in
  src/test/resources/llm), `ToolCheck` is strict argument checking, `ThinkSplitter` splits inline think tags,
  `LitePerception` cuts a perception by section. `Llm` reads the settings, picks the provider, builds chats
  (`Llm.open`), answers the memory model (`Llm.complete`, used by `Chronicler`) and decides the lite profile
  (`Llm.lite()`: `LitePrompt`, `Llm.LITE_TOOLS`, `llm.lite.*`; strict calls with one repair retry per thought).
  `GodSettings.model()/memoryModel()/maxSteps()/perceptionCharBudget()` and `GodClient.hasCredentials()`
  defer to it when the provider is not anthropic; a god's own model counts only for its provider (`Llm.fits`).
  Unknown-price models log tokens without a cost (`llm.price-input/output` → `TokenTracker.setFallbackPrice`).
- **god/Memory** — persisted to `plugins/gptgodmc/god/memory.json`: per-player favor, notes, dated
  deeds (`memory.deeds-kept`), a summary, last words and last seen; quests, the god's works, recent
  cycle records (`RECENT_KEEP` 12; the oldest `RECENT_FOLD` 6 are folded by `Chronicler` with the
  cheap memory model into the chronicle and per-player summaries). `EventLogger.remember` feeds
  `noticeEvent`, which records deeds. Perception shows a full dossier for players involved in the
  moment (`memory.max-dossiers`, `dossier-deeds`) and one line for the rest.
- **god/Annals** — the complete log, JSONL per month in `<god folder>/annals/YYYY-MM.jsonl`, written
  on a daemon thread, pruned after `memory.annals-retention-days`. Read by the `recall` and
  `search_annals` tools (`god/tools/MemoryTools`).
- **god/GodNames** — validates god names (invented and otherworldly; no human names, famous gods or
  plain words) for the memory model's birth name, the fallback tables and `rename_self`
  (`memory.rename-cooldown-hours`).
- **god/Persona** — persisted to `god/persona.json`. Built from `personality.archetypes` in
  config.yml (description, speech, voice-effect, dials, likes/dislikes); name/epithet/temperament are
  invented by the memory model.
- **god/Perception** — the text snapshot the god sees each thought (must run on the main thread).
  `build(trigger, drained, preview)`: `preview = true` (DebugBridge GET /perception) uses
  `Edicts.peek()` so pending operator commands are not counted as seen.
- **god/Sky** — one optional line under "## The world" (`Sky.line`, at most `Sky.CAP` characters): the moon's
  phase on a clear night, how long it has rained or stormed, rain that stopped in the last 10 minutes; silent
  on a plain day and outside the overworld (`awareness.sky`). `Sky.Log` dates each turn of the weather (listener + every look).
  Pure parts in `SkyTest`; `CostEstimateTest` measures the sky line and the living land together and fails
  above `AWARENESS_BUDGET` (1,800 characters).
- **god/Edicts** — binding operator commands (`/god command`, God Manager), per realm in
  `edicts.json`. Rendered as "BINDING COMMANDS" in perception; the god closes each with
  `command_done` (`god/tools/OperatorTools`), and the report goes to the issuer. Unresolved after
  `operator.command-retries` thoughts → dropped with a notice; a nudge wakes the god after 45 s.
  Standing commands (God Manager moods, `operator.mood-minutes`) expire instead. Only
  `gptgodmc.admin` holders and the console may issue them. `/god say` stays a non-binding directive
  (`SystemInjectionLoggable`); mortals' requests may be refused (end of `Prompts.system`).
- **god/TabList** — the god's name, epithet and state in the player-list header/footer (`god.tab-list`),
  refreshed every 5 s, sent only on change. The vessel has no nameplate; the name appears here and in chat.
- **god/Heart** — lasting feelings (wrath, joy, sorrow, dread, glory, 0–100, each with its own half-life),
  kept in `Memory.heart`. `Memory.noticeEvent` calls `Heart.moved` (insults, praise, offerings, strikes,
  deaths, weighted by favor) and acts call `Heart.acted` (vessel_mood feeds a feeling, calm halves all).
  The strongest feeling above `Heart.SHOWS` is the vessel's fallback mood (`Vessel.followHeart`, checked
  every 5 s and when a timed mood ends) and the voice's mood when formless; Perception shows "## Your heart".
- **The `feel` power** (`tools/HeartTools`, last in `ToolRegistry`; `Heart.choose`) — what the god feels about
  the sky, the land, a deed or a memory is its own choice, never the server's: faint (15, its own), stirring
  (40, its body shows it), strong (70) or gone. Do not add automatic feelings for such things; the server only
  shows the facts (the `Sky` line, the living land, `DeedWatch`'s alerts) and `Prompts.system` tells it to
  decide by its nature and its past choices. The same prompt says survival is no offense: `Senses.hunger` /
  `hurt` / `need` put "(hungry)", "(badly hurt)" after a mortal's health and food, and `DeedWatch` words a
  needy mortal's animal kills as "most likely for food" without waking the god.
- **god/Avatars + AvatarWatch** — how mortals look (`awareness.avatars`): `AvatarWatch` fetches each skin once from
  textures.minecraft.net (only that host, 64 KB max, background thread), `Avatars` (pure) reads it with no model call:
  hair, a hood or mask, the top and trousers by colour name, an unhuman face, a cape, and bare skin found by comparing
  torso and legs with the face's own colour (`Bare`: chest, legs, underwear, NAKED); the skin colour itself is never
  named. Armor covers it. Perception adds "looks: ..." to each mortal's line; a changed look or a naked mortal is told
  to the god as a non-waking event (avatars.json keeps each mortal's last skin). How it takes it is its own choice
  (`Prompts.system`). `AvatarsTest`; `AvatarsPreviewTest` draws build/test-renders/avatars.png.
- **god/Reminiscence** — "## A memory surfaces" (`awareness.memories`): on quiet thoughts only
  (`Reminiscence.quiet`), at most once per `awareness.memory-gap-minutes`, the server picks one old memory
  that fits the moment with no model call: an older deed of a mortal who is here, a deed some whole days ago
  at this hour (`ANNIVERSARIES`), a mortal standing by one of the god's works, a favoured mortal unseen for
  3+ days. `candidates` and `pick` are pure (`ReminiscenceTest`); the last twelve are not repeated (kept in
  memory only). `god/Awareness.lines` gives the God Manager and the mind viewer what moved it, the sky and the
  last surfaced memory (`MenuModel.State.aware`, `aware_lines` in the mod's `god` message, `sky` / `surfaced`
  in `MindProbe.memory`); the switches are in `Advanced` (Memory tab).
- **god/DeedWatch + WorldDeed** — the listener that turns what survival/adventure mortals do (break, place,
  fell, plant, harvest, kill, breed, tame, fish, pray, offer, die; "enter" from a 1 s timer) into pure
  `WorldDeed`s (act, kind, tags like animal/villager/golem/pet/log/crop/sapling/ore) and hands each to every god
  of that world: its land ledger and its oaths. It also logs villager/golem killings, raids (`RaidTriggerEvent`,
  `RaidStopEvent` win/loss) and slaughter/felling sprees (`Ecology.Spree`), gated by `ecology.alerts`.
- **god/Ecology + LandSense** — the living land (`ecology:`). `Ecology` is pure: `Land` + `describe` (animals,
  tamed, village and golems, ~crops and % ripe, canopy, "a forest with its trees cut away"), the `Ledger` in
  `Memory.land` (64-block cells, per mortal per `Ecology.WHATS`, fading with `ecology.footprint-half-life-hours`,
  400 cells max) with `render` (the cells round mortals and verdicts: woods stripped, herds culled, villager
  blood). `LandSense` (server thread) samples the surface heightmap every 2 blocks within `ecology.radius` and
  builds "## The living land around mortals" (one line per group of mortals within 48 blocks, then their mark);
  `survey_area` adds a "Living land" line. Power `tend` (`tools/EcologyTools`): flourish (ripen, bone-meal
  saplings, grow babies, love mode) or blight (crops to age 0, saplings to dead bushes, animals poisoned and
  slowed, none die), spread as a ring over a few seconds. `EcologyTest`.
- **god/Oaths** — oaths sworn before the god (`oaths:`; pure, `Memory.oaths`, `oathCounter`, and
  `Subject.oathsKept/oathsBroken` kept for ever). `/swear [god,] <words>` (`commands/SwearCommand`, aliases
  vow/oaths; alone lists your oaths) makes a pending oath and an `OathLoggable` (PRAYER category, wakes the god).
  `witness_oath` accepts (terms, minutes, optional `Clause`: never/must + act from `Oaths.ACTS` + target + count +
  place/radius) or refuses; `resolve_oath` judges kept/broken/released (`tools/OathTools`). `Oaths.observe` turns
  deeds into BREACH (retold at most every 10 min) and FULFILLED notices, `Oaths.tick` into UNHEARD (after
  `oaths.answer-minutes`), TERM_ENDED and CLOSED_BY_TIME (24 h after the term, judged by the watch); `DeedWatch.tell`
  logs them to the god and messages the swearer. The server never punishes or rewards. Perception shows
  "## Oaths sworn before you"; dossiers show "oaths: kept N, broken N (FORSWORN)". `OathsTest`.
- **god/Mind + Musing** — the god's private mind (`mind:`): beliefs, intentions and questions in `Memory.mind`
  (`Mind.State`: thoughts m1.., `reflectedAt`). `Mind` is pure. It is never shown whole: `Mind.recall(state, Cue)`
  scores each thought against the moment (3 per involved mortal it is about or names, 1 per shared telling word,
  at most 3; `RELEVANT` = 2) and returns at most `mind.recall` within `Musing.BUDGET` chars; when nothing scores
  and the trigger is quiet (`Reminiscence.quiet`), one thought not shown for 30 min drifts back. `Musing.perceive`
  (called in `Perception.build`, skipped for the lite profile) prints "## On your mind" only when something came,
  and marks it surfaced unless previewing. A thought fades `mind.fade-days` after it was last written or relevant;
  past `mind.max-thoughts` the least used goes (reflection's before the god's own). Powers `muse` (hold, rewrite
  by id, let_go) and `reflect` (read all, or about a subject) in `tools/MindTools`. `Musing.afterThought` (end of
  `GodBrain.cycle`) asks the memory model, on the chronicler's executor, to distil the cycle records since the
  last reflection (`Mind.reflectionPrompt` / `parseReflection` / `apply`: at most 3 adds, revises, drops) when
  `mind.reflect-minutes` have passed and at least 3 records have a thought or act; purpose "reflecting". `MindTest`.
- **god/Devotion, Offerings, Titles, Marks, Rites** — worship: commandments (`decree`), titles
  (`bestow_title`), halo/curse marks (`mark`), offerings (sneak + right-click the vessel, `/offer`;
  logged as an event, no tool), and particle rites (`rite`, `empower`, and rites played after powers).
- **gui/** — the God Manager chest menu (`/god manager|gui|menu`, `/god manager item` = nether star).
  A tree (`MenuModel.Page`: each page has one `parent`, `depth()`, and `serverWide`): the HUB (every god on
  the server, Create a god, Quick switches = SETTINGS, Server config = ADVANCED with 8 tabs, help) → each
  god's page MAIN (rows: Personality / Nature (DIALS: `Persona.dials`, saves persona.json, rewrites the
  prompt cache once) / Might & Mind; Mood & heart / Presence / Looks / Voice; Commands / Players & memories /
  Wake; Pause / Turn off / Erase or Remove) → its pages (PLAYERS → PLAYER). Opens on MAIN with one god, on
  the HUB with several. Back (slot 45) always goes to the parent, Home (48) to the hub on pages 2+ deep,
  titles are breadcrumbs ("Vaelor › Mood & heart"). Server-wide settings live only on server pages
  (`NavigationTest`). `MenuLayout` (slots, presets, click decisions) and `MenuModel` are pure and unit
  tested; `GodManager` turns them into items, cancels every click/drag in the menu and requires
  `gptgodmc.admin`. `MenuIcons` paints 32x32 icons (Icon.art) into the vessel pack as gptgodmc:menu/<key>
  item models, set with item_model only on menu copies for players who loaded the pack (vanilla items
  never change; Bedrock and pack-less players see the vanilla item). `ManagerPreviewTest` draws flat
  build/test-renders/manager-<page>.png and menu-icons.png; `ChestPreviewTest` (needs the local 26.3
  client jar, reads its textures at test time) draws chest-<page>.png / chest-<page>-vanilla.png as the
  game shows them (copied to previews/manager/). The Players pages edit
  `Memory` directly (`setFavor`, `removeNote`/`removeDeed`, `clearNotes`/`clearDeeds`, `forget`); never the annals.
- **EventLogger + loggables/** — thread-safe event buffer. Loggables return `null` from `getLog()` to
  skip, may `combine()` with recent ones, and declare a `category()` (EVENT/SPEECH/PRAYER/DIRECTIVE).
- **god/tools/** — `ToolRegistry` holds every `GodTool` (name, description, `Schema`, handler).
  `sync` tools run on the main thread through `Sync.call`; `async` tools (e.g. `look`) do their own
  main-thread capture. Handlers throw `ToolException` with a message phrased for the god; results
  go back to Claude, including images. Keep the tool list order stable (prompt cache); descriptions
  may be live (`Supplier`), and `sdkTools()` rebuilds only when one changes. `CustomPowerTools`
  turns `custom-powers:` in config.yml into the `custom_power` tool (placeholders like {player},
  {y+40}, {text}). `VesselReactions` makes the vessel turn, gesture and flare into a mood after
  wrath and blessing powers.
- **god/Godslayer** — the god-killing blade (PDC-tagged netherite sword, tier byte 1, or 2 for the
  Ascended Godslayer that kills in one blow, `/godslayer ascended`; shaped recipe, `/godslayer`,
  `/god godslayer`, `/god revive`). `VesselListener.onStrike` hands its blows to `Godslayer.strike`;
  wounds live in `Memory.mortality` (`god/Mortality`, pure: health = `godslayer.health` − wounds,
  slow healing, slain state, return timing) so death survives restarts and `/god forget`. Slain:
  `God.beat`/`acknowledge`, `Edicts` nudges, `/pray` and `Vessel.manifest` are gated; `TabList` shows
  SLAIN. `god/DeathShow` is the pure 90-frame death timeline (wound, collapse, burst, afterglow; variants
  supernova/unraveling/shattering/abyssal picked by archetype, then look; dots, cues, bolts, fireworks),
  played by `Godslayer.tickDeaths` inside `Spectacle`'s shared particle budget. Its solid pieces are
  `god/DeathPieces` (pure: a look and a pose per frame; ground heave from copied surface blocks, end-rod
  pillars, per-variant pieces) spawned and interpolated by `DeathStage` (BlockDisplay / ItemDisplay
  `gptgodmc:deathshow/<key>` for pack holders with block or text fallbacks, non-persistent);
  `DeathArt` paints their textures into the vessel pack; `DeathShowTest` renders
  build/test-renders/deathshow-*.png; `/godslayer preview <variant>` plays one harmlessly. Loot
  (`god/GodslayerLoot` parses `godslayer.loot`), boss bar and held-blade particles are in `Godslayer`. `GodslayerArt` paints the blade texture; its `files()` go into the
  vessel pack, and `item_model` is set only once the served pack holds them.
- **god/living/** — gods that live in the world; `Living` is the entry point (enable/disable from `GPTGOD`,
  timers, `perceive` called from `Perception`). `Bonds` (pure, `Memory.bonds`): each god's regard for each
  peer (-100..100, why, changes) and the pact both share (alliance, truce 1 h, war); `Rivalry` moves it from
  peers' acts (`PantheonTools.witness` → `Rivalry.witness`), jealousy over shared devotees, pact actions and
  the operator's `operatorSet` (`/bonds`, God Manager Bonds page). `HolyDays` (pure, `god/holydays.json`,
  server-wide): festival/trial/hunt/raid/omen/war/rite with god-written rules, `counts` (deeds in `ACTS`
  scored by `LivingListener`) and god-awarded points; `HolyDayRunner` runs bars, titles, raid waves,
  quarries, omen signs, war sides (`/side`), ends and rewards, and tells the gods the results.
  `WorldMood` (pure) + `WorldMoodRunner` (every 10 s): the strongest living heart in a world shapes
  weather (≥10 min apart), time drift, Darkness pulses, bold night mobs, crop growth, ambience.
  `ServantKind`/`ServantRig`/`ServantRules` (pure) + `Servants`: a vanilla mob (AI off, steered; hitbox
  and health) with rigs riding a root display, orders, strikes, preaching; all non-persistent and
  PDC-tagged `gptgodmc:servant`, swept at enable and on `EntitiesLoadEvent`. Two looks, picked per player
  every 10 ticks (`Servant.refreshViewers`, `VesselPackHost.has`): pack holders see the servant's own
  painted body and not the mob (`Player#hideEntity`), striking it through an Interaction hitbox whose
  blows `LivingListener.onStrikeBody` passes on with `player.attack(mob)`; everyone else (no pack, Bedrock,
  `servants.textures: false`) sees the mob wearing the block rig (`ServantRig`, angel wings reuse
  `DemigodWings`). The painted body: `ServantBody` (pure geometry and motion per kind: seraph, cinder imp,
  wraith, hierophant, colossus; boxes, two-sided planes, and glows = Billboard.VERTICAL lights that must
  sit on the body's axis, because the client applies a billboard's offset in the camera's frame),
  `ServantSkin` (per-kind atlas layout, 32 texels per block, 8 frames), `ServantTextures` (canvas and
  brushes) + `ServantPaintHoly`/`ServantPaintDark` (the pictures, ARGB: alpha cuts wings and cloaks and
  fades hems and mist), `ServantPack` (gptgodmc:servant/<kind>/<piece> items and one-box or one-plane
  models, added by `VesselPack.files()`). Client-mod
  hook: an invisible paper ItemDisplay rider whose custom_data has `gptgodmc:servant`, `servant_id`,
  `servant_god`, `servant_mob`. Tools (last in `ToolRegistry`, `tools/LivingTools` + `HolyDayTools`):
  regard, pact, sabotage, joint_blessing, proclaim, holy_day, summon_servant, command_servant. Config:
  `rivalry.*`, `events.*`, `world-mood.*`, `servants.*` (`LivingSettings`). `ServantPreviewTest` draws
  build/test-renders/servants.png, servant-<kind>.png and servant-atlases.png (the painted bodies, with
  its own small renderer `ServantRender`: alpha, planes, glows), servants-blocks.png (the fallback) and
  holydays-bars.png; `./gradlew previews` copies them to previews/servants/.
- **god/art/** — relics: things a god makes with their own painted pixel art (`creations:` in config.yml,
  OFF by default; `ArtSettings`). The craft lives in code, the composition in a model: `Sprite` is a map of
  materials as an artist writes it (a legend of letter -> material such as `gold`, `gem #2aa6e0`,
  `glow #ffd86a`, plus rows of letters; pure, with errors phrased for the artist), `Pigment` the materials
  and their five-tone ramps (tinted ones are built from any color with hue-shifted shadows and lights),
  `SpritePainter` the painter (one light, flat ramp tones, a darker far edge, metal glints, gem facets,
  wood grain, flickering flame, twinkling void; 8 frames; a second `lit` layer of what shines; optional
  halo for spirits; deterministic), `SpriteLint` a teacher's notes, `SpriteSheet` the proof sheet,
  `Samples` hand-written examples (quoted in the artist's prompt, painted by the tests). A sprite is also a
  solid: a legend entry may say `thick N` (texels front to back, 0..12; unsaid = 1 on a 16 canvas, 2 on 24/32),
  `round` (thickest along a patch's middle: rods, balls) or `flat` (a film), and `alt_rows` is a second,
  awakened form on the same canvas (`Sprite.alt()`); `Art.depth` holds each texel's thickness (a narrow
  stroke on the diagonal, at least 6 long, stays at the usual thickness: `SpritePainter.flattened`, said in
  `SpriteLint`) and `Art.walls` the colours of each texel's walls (UP/DOWN/WEST/EAST: a tone lighter on
  top, else the face's tone; the game and the mod shade faces themselves). `RelicForm` (pure) turns art
  into boxes (`bars`), the pack's item model (`model` with a `Look`: solids with walls from a `__walls`
  texture, then films; null above `MAX_BARS`, then the flat model) and the three-quarter view (`turned`).
  Relics are lit like any item on both paths: no `light_emission`, no full-bright, no halo, light painted
  in a material included (the user: a relic that shone with no shadows looked pasted onto the world; glow
  only as a deliberate power effect). `RelicPack` writes `<id>` and `<id>__walls` textures, the solid model,
  a flat `<id>__icon`, and an item definition that selects the icon for `display_context` gui.
  A special relic may also carry a true shape (`Sprite.shape`/`altShape`, "shape"/"alt_shape" in its sprite
  JSON): parts in the scene-solid language (`god/fx/FxSolid`: lathe, tube, prism, ball, box, quads) in sixteenths
  of the picture's square; `RelicShape` (pure) checks and normalizes it (still, matter, blocky unless a part says
  `"round": true`, max 12 parts / 1600 faces, inside the square), builds it (`TICK`, size 1/16, -0.5 shift) and draws
  front + three-quarter views for the artist's proof (a bad shape fails the attempt) and the forge's picture.
  Every part is made of a `material` (legend words; else its first colour; iron) and wears that material's 16x16
  repeating pixel tile (`RelicTiles`: wood grain, brushed metal, gem facets... from the material's ramp), named
  `m_<material>` in its `texture`; `RelicShape.texel` lays it from the part's unplaced frame, texels the size of the
  picture's own (canvas/16 per sixteenth), quads on the picture's grid (`RelicShape.model(normalized, canvas)`).
  `ModRelics` sends it as `shape` per form with `textures` (name -> base64 PNG); the client mod lays the same u, v
  and draws it instead of the voxel mesh outside the GUI.
  The pack and inventory icons keep the picture. `Samples.SCYTHE` (Gravecall) is the example; `RelicShapeTest`. Plain relics stay the painting
  at even thickness; the artist sculpts, and draws two forms, only when the commission says the relic is
  special (`Atelier.Brief.special/twoForms`). `Atelier` is the
  artist: a separate conversation on its own model and effort (`creations.model`, default
  claude-opus-5-5, must be Opus- or Fable-class; `creations.effort` high/xhigh/max, lower is raised) with
  one tool, `paint`, whose result is the proof sheet as an image plus notes; it paints, looks and corrects
  up to `creations.max-attempts` times, and its tokens are logged under the purpose "relic". Anthropic
  provider only. `Atelier.artist` is a seam for tests. `Relics` keeps them (god/relics/<id>.json: the sprite and
  `powers`; art is repainted on load), makes the items (`Relics.dress`: name, lore, item_model
  gptgodmc:relic/<id>, or <id>__on while awake, PDC `relic`, `relic_rev`, `relic_on`; a blade is an
  unbreakable iron sword) and enforces the limits (per god per day, cooldown, total). Imbuing:
  `RelicPowers` (pure) is the language, one power a line, `<when>: <what> [| cooldown N] [| when on|off]
  [| chance P]` (when = stat, held, on_hit, on_swing, on_use, on_kill, on_hurt; what = properties, effects
  such as ignite/potion/heal, movement such as dash/blink/leap, the attacks slash/wave/beam/slam/spin/throw,
  light, aura, trail, sound, `fx <a saved FxLibrary effect>`, toggle, and `script <a saved script> [{json
  args}]`, which runs one of the maker's saved programs through `tools/RelicScripts` with args.bearer,
  args.target, args.at, args.aim, args.look and args.relic, on `RelicScripts.limits` (a moment: no handlers,
  2 s, 4 power() uses, 256 blocks), at least `RelicPowers.SCRIPT_REST` s apart, one per bearer, `MAX_RUNNING`
  on the server, behind the script power's own switches; mistakes go to the journal); every number is clamped, every power
  has a cost and a relic's total must fit `creations.power-budget` (12; 0 = no imbuing); `RelicPowers.HELP`
  is the text gods read. `RelicImbue` (a listener and a 5-tick timer started by `RelicsModule`) carries
  them out: attribute modifiers and a per-relic cooldown group on the item, hit/hurt/kill/swing/use
  events, the attacks drawn in dust (no block is ever changed; the `striking` flag keeps a power's own
  damage from setting powers off again), toggle (a right-click wakes it; once awake a click acts and sneak
  + click puts it to sleep), carried light as LIGHT blocks shown with `sendBlockChange` only, and held
  copies re-dressed when their `relic_rev` is stale (imbuing bumps `rev`);
  `RelicPack` adds their files to `VesselPack.files()` (item/handheld or item/generated over an animated
  strip; published art is always square); `RelicShrines` floats them in the world (item displays, kept
  places in relics/shrines.json, hidden from players with neither pack nor mod); `RelicsModule` is the
  entry point (enabled before the pack is first built) and, `creations.pack-push-seconds` after a change,
  calls `VesselPackHost.republish` (rebuilds the zip and re-offers it; skipped for mod players). Tools
  (last in `ToolRegistry`, `tools/RelicTools`): forge_relic (async, blocks for the artist, returns the
  picture; `special`, `powers`), give_relic, show_relic, imbue_relic (replaces a relic's powers; free).
  `/relics` (`RelicCommands`, admin): list, on/off, give, show, dismiss, delete, samples (adds the examples
  without calling any model; Skysplinter is the sculpted, two-form, imbued one), forge, powers, imbue. Turning it on (command, God
  Manager "Painted relics" row, or at start) prints `ArtSettings.warning()`: it burns usage fast. Client
  mod: `client/ModRelics` sends each relic's art (feature `relics`, message `relic`: base64 PNG strips
  `png` and `lit` (what is painted as shining; the mod no longer draws it brighter), `depth`, base64 of w*h thickness bytes, and `walls`, a strip of 2w x 2h quarters UP,
  DOWN, WEST, EAST; a second form goes as its own message under `<id>__on`) so the mod draws relics itself, solid and lit by the world, with no resource-pack reload; the
  mod side is client-mod `client/relic/`. `RelicPreviewTest` draws build/test-renders/relic-samples.png,
  relic-forms.png (front and three-quarter views), relic-proof.png and relic-proof-two-forms.png, and
  writes relic-messages/*.json and relic-pack.zip for the mod's tests; `RelicFormsTest` covers form and
  powers.
- **god/music/** — `Song` (pure: note text like `C4 E4+G4/2 R` → note-block steps and ticks, unit tested)
  and `Music` (plays songs on a 1-tick timer, keeps `songs.json`, makes PDC-tagged discs; right-click
  plays, on a jukebox plays from it). Tools `compose_song` / `play_song` in `god/tools/MusicTools`.
- **Web search** — `GodBrain.tools()` adds Anthropic's `web_search_20260209` server tool after the god's
  powers when `brain.web-search` is on (not with a custom base-url); `pause_turn` replies are sent back
  to continue, searches are journaled and billed at $0.01 each in the usage line.
  The `search_web` power (`tools/WebTools`, last in `ToolRegistry`) is the same search as a result the god can
  chain: one request to the memory model (haiku when the provider is not anthropic; needs an Anthropic key) with
  the search tool (plus web fetch when given `url`), answer capped at `WebTools.MAX_ANSWER`. Scripts call it as
  `web.search(query[, focus])`, `web.read(url[, focus])` or `power('search_web', …)` (one power use each), so a
  program can search and then act on the answer. Chaining in scripts: `power()` returns `{ok, text, image}` with
  up to `ScriptWorld.MAX_RESULT` (2000) characters, `powers()` lists the usable names, and the last picture a power
  made (look, map...) is attached to the script's own result so the god sees it.
- **Words and acts agree** — `Prompts.system` says decide, then answer, then act. `god/Coherence` (pure, one per thought in
  `GodBrain.carryOut`) remembers what the god said to whom (whisper, vessel_say "Heard by"); the first later power whose
  arguments name one of those mortals gets its own words quoted back in its result, so it can own a contradiction
  ("eat something" then `heal`). `CoherenceTest`.
- **What a live test taught (2026-11-13, harness/out/session-*)** — rules in `Prompts.system`, checked by `PromptsTest`:
  "Choose who hears you" (open questions get open answers: announce / vessel_say, whisper for private matters),
  "Speak like a living mind, not a motif" (no line after line from the same few images, no quoting its own history as
  catchphrases), "Mortals see only what your powers do" (never claim an act no power did this turn), and a goal is a
  thing made or changed in the world ("never a goal": watching, counting, judging). `judge` shows the judged mortal a
  sign of which way their favor moved, never why (`Signs.favorLine`). `revive` on a mortal who already respawned says
  it was not the god's doing. `take_item` counts slots by kind (`BlessingTools.takeFrom`, the held slot first): a
  used, enchanted or renamed item was "not there" for the exact-stack removal it used before.
- **The `locate` power** (`tools/LocateTools`, last in `ToolRegistry`, async) — the nearest structure or biome from a
  mortal, the vessel or a point, as direction, distance and coordinates; words are matched leniently against the
  server's registries ("village" searches every village kind, at most 8 kinds, 6 s in all, each search its own
  `Sync.call`). The structure radius counts cells of the structure's own placement grid, not chunks (16 = thousands
  of blocks for villages, a few hundred for mineshafts). `LocateToolsTest`.
- **Finishing commands** — if a reply ends while a one-shot binding command is still open, `GodBrain`
  appends a user note asking it to finish in the same thought (at most twice); `Edicts.NUDGE_MS` is
  15 s; `God.showWorking` puts "… is working on your command #N" in the issuer's action bar while it
  thinks. `transform_structure` also takes the god's own works (`Builder.recolor`, even mid-build) and
  `build` takes `material` to build all full blocks in one block.
- **god/Spectacle** — particle shows (`particles` tool), meteors and earthquakes, sharing one
  2-tick timer with a particle budget and caps on concurrent effects.
- **god/FxStyle, FxRoutines, FxLibrary + tools/FxBrainTools** — how the god authors its own presentation on
  top of `FxScenes`/`FxShow`/`FxTools` (scenes for client-mod players, dust sketch for the rest). `FxStyle`
  (`<god folder>/style.json`; pure except `of`/`born`/`save`/`currentMood`): palette, favourite sprites/shapes,
  motion words, sky colour per mood, HUD accent, sigil; drawn at birth in `Persona.rebirth` by
  `Chronicler.draftStyle` (read leniently by `FxStyle.drafted`, else `fallback(archetype)`), changed with
  `fx_style`, sent to mod players as `style` in the `god` message (`ModMessages.god(view, style)`). Scenes may
  use tokens `$primary $secondary $accent $shadow $hud $mood` (and `$sigil` as glyph text): `FxScenes.check`
  accepts them, they are stored unresolved and `FxBrainTools.styled` resolves them when a scene is shown.
  `library.json` entries are `fx` (layers; may carry `bound`: powers) or `routine` (`steps` of
  `{tool, args, delay_ticks}`, `FxRoutines.check`: 12 steps, 200 ticks per step, 400 in all, never
  library_run/library_save/library_forget/command_done). Tools, last in `ToolRegistry`: `fx_style`,
  `library_save`, `library_run` (not a main-thread tool: sleeps between steps on the brain thread and runs
  each through `ToolRegistry.execute(…, "brain")`; scheduled instead when called from the server thread),
  `library_read`, `library_forget`, `fx_bind` (`ToolRegistry.execute` calls `FxBrainTools.afterPower` after
  every successful power; target from `FxRoutines.targetOf`), `fx_omen` (a line/sigil for one mortal: mod
  message `omen` for players with the `omen` feature, else action bar; not the older world `omen` power).
  Tests: `FxBrainTest`; fixtures `god-style.json`, `omen.json`.
- **god/fx/** — the dynamic part of the effect language: two scene layer types the god writes with its own
  maths instead of picking from lists (the client is only ever sent data, never code). `FxExpr` is the formula
  language (pure: compiled to a small stack program; names t p i u n r, pi, tau; maths functions, `noise`,
  `rand(k)`, `if`; capped at 240 characters / 160 steps; `FxExpr.NAMES` is what the god reads). `FxField` is the
  `field` layer (up to 1500 particles whose x, y, z, size, alpha and shade are formulas, colour stops) and
  `FxSolid` the `solid` layer (form box | ball | tube | lathe | prism | quads, matter or `light`, up to 64
  copies moved by x/y/z/scale/yaw/pitch/roll/alpha formulas, 4000 faces a layer); each has a lenient `parse`
  (clamps, never throws) and a strict `check` (messages for the god). These three files are copied word for
  word into the mod (`client-mod/.../fx/`, only the package line differs; `FxExprTest` fails if they drift:
  edit one side and copy it over). `FxFormulas` is the plugin's glue, called from `FxScenes.check`/`plain`/
  `length`: its `HELP` is part of the `fx_cast` description, and it sketches both layers in dust for players
  without the mod. Mod: `Scene.Field`/`Scene.Solid`, `FieldParticle` (repositioned every tick, one shared
  `Anchor` per running scene so stopping a scene removes them), `SolidFx` (rebuilt every frame; matter has its
  own depth-writing pipeline, light uses `WorldFx.LIGHT`), demo scenes galaxy/henge/orrery (`/godfx scene`).
  `FxFormulasTest` reads those demos from `DemoScenes.java`, writes the golden `scene-formulas.json` (env
  `FX_FIXTURES_WRITE=1`) and draws build/test-renders/fx-formulas.png; the mod's `FormulaTest` decodes it.
  Painted textures: `god/FxPaint` (per god `textures.json`, 24 at most) keeps sprites the god writes itself with
  `fx_paint` (`tools/FxPaintTools`; the relic `Sprite` letter map painted by `SpritePainter`, no model called,
  outline none unless said); a solid or field layer names one with `"texture"` (`"tile"` repeats it; `FxSolid`
  carries u, v per corner, `VERTEX` = 9 floats). `FxTools.show` refuses an unpainted name; `FxShow.play` calls
  `FxPaint.bind`, which sends each mod viewer the `texture` message once per revision (id `<realm>.<name>`,
  base64 PNG strip of 8 frames) and rewrites the names. Mod: `FxTextures` (decode, one repeating texture a
  frame, 48 pictures), `SolidFx` draws painted solids with `RenderTypes.entityTranslucent`/`eyes`, `FieldParticle`
  uses the picture as its sprite; demo `idol` uses the two samples `texture-veins/sigil.json` that `FxPaintTest`
  writes (it also draws build/test-renders/fx-textures.png). Players without the mod see plain colour.
  Reskins: the `reskin` layer (`fx/FxReskin`, the fourth file shared word for word with the mod) makes some blocks
  round a place look like something else for a while; the world never changes. Which: `blocks` (kinds; none = any
  solid block), inside a disc (`height`) or `shape` sphere of `radius` (a formula of t and p, at most 16, so it can
  creep), and of those the `density` share, picked per block by `FxExpr.hash` of its coordinates so every viewer and
  the server agree. As what: `as` another block (`god/FxReskinShow`, started from `FxShow.play`: `sendBlockChange`
  to viewers, only to a block as solid as the real one, the truth sent again when the layer ends or the scene is
  cancelled; if a texture or colour is given too, only players without the mod get the `as` block), a painted
  `texture`, or a `color` wash with `strength` (mod: `ReskinFx`, a skin over the open faces of the chosen solid
  blocks, lit with the world's light; rescanned as its reach changes and every 20 ticks). Demo `blight`.
  `FxReskinTest` covers the picking; the mod's `FormulaTest.reskinsDecode` the decoding.
  What only a watcher's own game can work out (still data, never code): formulas may read the viewer, `d`, `look`,
  `speed`, `ex ey ez` (`FxExpr.viewer`, filled by the mod's `Viewer` per layer place; `FxExpr.nobody` on the server,
  and a reskin's radius never sees them, so every viewer and the server agree on its blocks). A field is `free`
  when it gives any of `ax ay az vx vy vz drag bounce life`: its particles then keep their motion (`FxField.birth`/
  `step`/`look`, state per particle in `FieldParticle`, blocks through `Viewer.Blocks`; names `px py pz vx vy vz a k`);
  the dust sketch shows only where they are born. The `trigger` layer (`fx/FxTrigger`, the fifth file shared word for
  word): `when` (a formula), `hold`, `again`, `then` (up to 8 layers, checked as a scene by `FxFormulas.then`; no
  trigger, texture or style token inside), `tell`, `wake`. The mod's `SceneRunner` watches it per player, plays `then`
  at once and reports `{"t":"trigger","key"}` on gptgod:main; `god/FxTriggers` keys each trigger per showing
  (`arm`, from `FxShow.play`), believes a client only for a trigger it was shown, in time and no oftener than
  `again`, watches it itself for players without the mod (`Plain`, every 2 ticks) and logs "<name> <tell>" to the
  casting god (waking it only with `wake`). Size is a particle's width in blocks (the mod halves it for the quad);
  every field particle shimmers on its own clock. Demo `wake`; `FxLiveTest`; the mod's
  `FormulaTest.triggersAndFreeParticlesDecode`.
- **god/FxSeal + FxSealShow** — the `seal` scene layer: flat emblems of light (magic circles, glyphs) that are real
  surfaces in the world, not particles, and need neither the mod nor the pack. `FxSeal` (pure; server only, not shared
  with the mod, whose scene parser skips the unknown type) is the language and geometry: plane ground | upright | facing
  (or tilt + yaw), radius, spin, appear/fade, a `scale` formula, and up to 16 parts (ring with dashes/arc, polygon, star,
  spokes, curve from `rho` or `x`/`y` `FxExpr` formulas, runes in the enchanting-table / illager / plain font, marks,
  motes drifting up; each with colour, width, glow halo, own spin, delay, copies + orbit). `frame(age)` returns every
  Line/Glyph/Mote with counts that never change, so each keeps its entity; `check` refuses above `MAX_PIECES` (500).
  `FxSealShow` (from `FxShow.play`, ended in its `cancel`) draws each stroke as a TextDisplay holding one space: its
  background is a flat full-bright quad (5 x 10 text pixels, see the constants) stretched by the transformation; a
  text display shows only its front, so tilted seals get a back copy of strokes and letters (not halos). Poses go out
  every `STEP` (4) ticks with interpolation, only for pieces that changed; a seal that follows a mortal rides them through
  a root display; non-persistent, at most `MAX_ALIVE` (1800) on the server (`refusal` in `FxTools.show`). `FxSealTest`
  draws build/test-renders/fx-seals.png. Not yet seen in a live game.
- **god/hooks/** — the WorldEdit hook: the `worldedit` power (`tools/WorldEditTools`), always registered, working
  only when the WorldEdit or FastAsyncWorldEdit plugin is enabled (softdepend; `worldedit.enabled`,
  `worldedit.max-blocks` 20000). `WorldEditHook` names no WorldEdit class and loads `WorldEditBridge` (all
  `com.sk89q` code, compileOnly `worldedit_version`) by name; `WorldEditRules` is the pure part (requests, size
  limits, directions, forbidden blocks; `WorldEditRulesTest`). Actions set, replace, walls, faces, hollow,
  sphere, cylinder, pyramid, line, generate, smooth, stack, move, undo run through an EditSession with
  maxBlocks and a mask that never writes into a mortal's structure (`Builder.mortalStructureAt`); the last 5
  acts per realm can be undone; refused at initiative `watch`. WorldEdit's own // commands stay on
  `powers.command-blocklist`. Compile-checked only: never run against a live WorldEdit.
- **god/worlds/** — worlds the gods make. `GodWorlds` (entry point, enabled from `GPTGOD` after `Living`): real
  Bukkit worlds named god_<name> (kinds void, flat, normal, nether, end; `VoidGenerator`, also what
  `GPTGOD.getDefaultWorldGenerator` hands Multiverse as `gptgodmc:void`), listed in plugins/gptgodmc/worlds.json and
  loaded again at start, at most `worlds.max` (4); with Multiverse-Core they are imported by command (`mv import`,
  `worlds.multiverse-import`); `unmake` unloads and forgets a world and never deletes its folder. A `sealed` world
  cannot be changed by mortals (break, place, buckets, explosions, fire; creative keepers excepted). `Maze` (pure:
  a perfect maze by a self-avoiding walk, sizes small 9 / medium 15 / large 23 / vast 31 cells, the way out is the
  cell farthest from the start; `plan` turns it into walls). `Labyrinths` is the punishment: the sealed void world
  god_labyrinth, one maze per sentence on its own 256-block plot (floor, 5-high walls in a `Style`, a barrier lid,
  gold at the heart), raised 6000 blocks a tick; the mortal is put in adventure mode and returned to their own
  place and mode when they reach the gold or after `labyrinth.max-minutes` (20) at most; death returns them to
  the start, pearls and chorus fruit are refused, sentences persist in labyrinth.json, and the god is told how
  each ends (a `GenericEventLoggable` to the casting realm). Who is held and for how long is told in the
  perception ("## Your labyrinth", `Labyrinths.perceive`), never in the power's description: minutes counting
  down there rewrote the whole ~65k-token prompt cache on every thought ($0.17 each in a live test). Keep
  anything that changes by the minute out of live tool descriptions. Tools (`tools/DimensionTools`, last in
  `ToolRegistry`): `labyrinth` (cast, release) and `make_world` (create, send, list, unmake). `MazeTest` draws
  build/test-renders/labyrinth.png. The god's other powers still act only in its own realm's world.
- **god/script/ + the `script` power** — programs the god writes itself (`scripts:` in config.yml, on by default;
  `tools/ScriptTools`, last in `ToolRegistry`). The interpreter is Mozilla Rhino (`rhino_version`, a `libraries:`
  entry, not shaded) and `god/script/` is pure (no Bukkit; `ScriptRunTest` runs it against a fake host):
  `ScriptRun` is one script (interpreted mode, `initSafeStandardObjects`, a ClassShutter that denies everything, so
  no Java class is reachable; typed arrays removed and `repeat`/`padStart`/`padEnd`/`fill`/`Array.from` rewritten
  in JavaScript so the step counter sees their loops), `ScriptApi` the only names a script sees and the `HELP`
  text in the tool description (world.block/top/at/set/fill/lightning/explode/spawn/particle/sound/time/weather,
  players.list/get/effect/title/tell/push/teleport, power, command, wait, log, on, every, off, args),
  `ScriptValues` the marshalling (only null, Boolean, Double, String, List, Map cross; never a Java object),
  `ScriptBudget`/`ScriptLimits` the budgets (steps, seconds of computing, wait ticks, host calls, power() uses,
  commands, blocks, creatures + bolts, handlers and their lifetime, handler calls; each clamped in the record so
  config cannot switch one off; spending past one throws `ScriptStop`, an Error no script can catch), `ScriptHost`
  the interface to the world. Calls that return nothing (`Kind.DO`) and blocks are queued and carried out in one
  visit to the server thread at the next `wait`, value-returning call or the end, so everything between two waits
  happens in one tick; a refusal becomes a JavaScript error naming the line. `tools/ScriptWorld` is the Paper
  host: every changing call first passes `gate(<the matching power>)` (`PantheonTools.refusal` + `Guard.refusal`),
  blocks become ONE work through `Builder.build` then `Builder.extend` (mortal structures refused, `unbuild`
  undoes it, containers and unbreakable blocks left alone, loaded chunks only), `world.explode`/`world.spawn`/
  `power()` go through `ToolRegistry.execute(…, "brain")` (never `ScriptApi.NOT_POWERS`), `command()` through
  `WorldTools.dispatch` with the blocklist. Do not add a host call that is stronger than an existing power; give
  it a gate. The program runs on its own daemon thread (`god-script-<realm>`), never the server's; the god's
  thought waits for the main part, then the same thread serves `on`/`every` handlers (`ScriptRun.serve`, fed by
  `tools/ScriptEvents`: places every 5 ticks for 'enter', hit, break, chat with `Guard.clean`) until
  `scripts.listener-seconds`, and the god is told what they logged by a non-waking event. Refused at
  initiative `watch`, when scripts are off, and while the guard locks `run_commands`. Saved scripts
  (`save_as`, kept only if the run succeeded): `god/ScriptLibrary`, `scripts.json` in the god's folder, the args
  given at saving are the parameters, a later run's args replace them one by one (`name` + `args`); a routine
  step may be `script`. Its index in the tool description is sorted by name so it stays cache-stable.
  Relics run saved scripts too (`tools/RelicScripts`, see god/art/): keep `ScriptTools.enabled`/`limits`,
  `ScriptWorld(label)` and `ScriptLibrary.find`/`merged`/`used` as they are, or update it with them.
- **The god's own relics in hand** — `god/art/Armory` (relics a god keeps for itself, `god/relics/armory.json`
  god id -> relic ids, max 8, only its own or the samples; `weapon(relic, awake)` -> `VesselHeld.Weapon`: grip and
  point from the art (`ends`), length by kind, plain vanilla stand-in by kind and name words), `vessel/VesselHeld`
  (the right hand: an ItemDisplay of the relic for viewers with the pack or the mod and one of the stand-in for
  the rest, placed every tick from the RIGHT_ARM frame by `transform` (grip in the fist, `POINT` ahead and a
  little down in arm space, ITEM_FLIP undone); a dust trail from the point while a blow is armed; `arm(blow,
  ticks)` + an animation's `hit` cue -> `hit(...)` hands the blade's world grip/point to the blow; hooked into
  `Vessel` like the wings rig: tick, teleport, entities, despawn/clearRefs, showTo, danceCues), `Choreo` cues
  may say `hit` (`Cue.hit`, `Choreo.strikes`), `Choreo.WEAPON_STYLES` (slash, overhead, thrust, reap, whirl)
  plus `draw_weapon`, and `god/art/Strikes` (one sequence per god: pursue up to 12 s (follow or goTo), turn,
  play the animation, land on the hit cue when within `reach` or the blade's segment; damage = the relic's
  attack stat or its kind's weight x style x body size, + `RelicImbue.strikeEffects` (its on_hit powers that need
  no mortal bearer), capped `MAX_BLOW`; the god hears each blow as a non-waking event). Tools (last in
  `ToolRegistry`, `tools/ArmoryTools`): `armory` (draw, sheathe, keep, drop, list) and `strike` (behind the
  smite gate; style = a weapon animation or the god's own saved animation with a hit cue); forge_relic `keep`.
  `ArmoryTest`; `WeaponPreviewTest` draws build/test-renders/weapon-*.png (the vessel swinging real relics).
  `ScriptToolsTest` covers the library and the tool. What a script makes it may steer, and nothing else:
  `world.spawn` (direct, same gate/forbidden list/count limit as spawn_entity) and `world.display` (block, item
  or text display entities: non-persistent, removed after `seconds`, at most `scripts.max-display-seconds`,
  budget `scripts.max-displays`) return ids kept per run in `ScriptWorld.made`; `entity.get/move/teleport/push/
  target/set/remove` take only those ids (a creature walks by pathfinder, retried for a few ticks because a mob
  made this tick finds no path; a shape glides by teleport duration; a block display is centred so it turns
  about its middle). Queries: `world.info` (block, state, biome, light, claim), `world.ray`, `players.looking`,
  `world.near`. `ScriptLimits` keeps an 11-argument constructor for `RelicScripts`.
- **god/hooks/ (more)** — optional plugins (`hooks:` in config.yml; all soft dependencies, nothing required;
  `Hooks.enable` logs "Plugin hooks: ..." after start). `Lands` answers whose land a place is from WorldGuard
  regions, GriefPrevention claims and Towny towns (first that claims it; a reader that throws is skipped);
  `ScriptWorld.place` leaves claimed blocks alone when `hooks.respect-claims`. `Wards` (pure rules + wards.json
  in the data folder, swept every 30 s) raises temporary WorldGuard regions named `god_<realm>_<name>` with
  effects from `Wards.EFFECTS`, never over claimed land (the whole square is sampled), and never touches a
  region without that prefix. `LandHistory` (pure `summarize`) reads CoreProtect and can take one mortal's
  changes back. `Disguises` shows a mortal as a creature through LibsDisguises. WorldGuard and CoreProtect
  are compile-checked (`WorldGuardBridge`, `CoreProtectBridge`, loaded by name; `worldguard_version`,
  `coreprotect_version`, compileOnly); GriefPrevention, Towny and LibsDisguises are reached by reflection
  through the other plugin's class loader, with names checked against their published jars. Powers
  (`tools/HookTools`, always registered): `ward` (who, raise, lift, list; raise is gated like conjure),
  `land_history` (not a main-thread power; rollback gated like unbuild and needs a player), `disguise` (gated
  like apply_effect, ends by timer). Without the plugin each description is one sentence saying so.
  `HooksTest` runs with none installed. WorldGuard and EssentialsX also define /god; `commands/GodLabel` takes the
  bare label back once all plugins have loaded (ServerLoadEvent, first tick, 5 s later; `operator.claim-god-command`,
  default true), leaving theirs at /worldguard:god and /essentials:god (/egod).
- **god/vision/** — `Vision` captures ChunkSnapshots + entities on the main thread, `Renderer`
  ray-traces off-thread (perspective, panorama, overhead surface/cave/x-ray) using `Palette` (block
  map colors + overrides); labels use the built-in `PixelFont` (works headless). The minimap (`map` tool,
  `god/tools/MapTools`, last in `ToolRegistry`): `Minimap` is pure (a mortal's `Level` from the cover over
  their head: surface, or a slice at their Y when indoors/underground; `group` into panels per level and
  place; `Frame` projection; `place` for labels; `useful` for unasked maps; drawing on `Renderer.terrain`),
  `MapView` reads the world (`capture` on the main thread, `draw` off it). `vision.auto-minimap`
  (off | when-useful | always) attaches one to the perception: `MapView.auto` in `GodBrain.cycle`, sent with
  `LlmChat.user(text, png)` when `LlmChat.sees()`. `MinimapTest` renders build/test-renders/minimap-*.png.
  Every block is drawn as a cube in its map color, so a look with a camera (eyes, vessel, orbit) also names
  what it shows: `Sightings` (pure; `SightingsTest`) casts 128x72 of the picture's rays up to 48 blocks and
  lists the built kinds they meet (never the land: `Sightings.natural`), nearest first, with count, distance,
  left/centre/right and "too thin to show" for kinds the palette does not draw (torches); `Vision.named`
  appends it to the caption with the text of signs in view (read on the server thread, through `Guard.clean`).
  An orbit whose spot is walled in stays outside instead of putting the camera inside the walls. `see_build`
  with no placement on record (`BuildView.NoRecord`) answers with the mortal's eyes view, not a refusal.
- **god/build/** — `Plan` (relative block placements, rotation), `Style` palettes, `Designs`
  (procedural buildings), `Blueprints` (vanilla structures/templates, user schematics in
  `plugins/gptgodmc/schematics/`, Sponge `.schem` via `Nbt`), `Builder` (places N blocks per tick,
  refuses to overlap player structures, keeps undo data). `Site.settle` (called by the `build` power for
  designs that stand beside their target) searches the footprint for built blocks that are on no record; with
  3 or more it moves the site (further from the target, then to either side; `Site.shifts`, `SiteTest`) and
  the result says so, or refuses when nowhere near is free; `over: true` builds on the spot anyway.
- **god/vessel/** — the god's body. `VesselModel` is a pure-math rig with player-skin proportions
  (units are skin pixels; bones for torso, head, arms, legs, cape) plus target poses built from a
  `Motion` (walk cycle, levitation, flight lean, breathing, head look, mood posture, and timed
  gestures listed in `GESTURE_TICKS`, e.g. slam/clap/summon with `impactTick`) and particle wing
  geometry. `VesselLook` dresses it per body region (shade/radiant/infernal/void presets, horns or
  halo, outline and aura colors). `VesselMood` (calm/wrathful/joyful/sorrowful/ominous/exalted)
  sets rise height, glow and eye overrides and wings. Each `Part` names a `VesselSkin.Piece` (head,
  torso, arm…; horns are untextured on purpose). Custom skins (`vessel.textures: custom`, default):
  `VesselSkin` unwraps each piece like a player-skin box into one atlas layout (Face s/t ↔ box point,
  Minecraft's default face UVs), `VesselTextures` paints an 8-frame animated atlas per look from code
  (noise, Voronoi embers, sigils; deterministic), `VesselPack` writes the pack (pack format 97: items/,
  models/item/vessel/<look>/<piece>.json one-box models, textures/item/vessel/<look>.png + .mcmeta;
  fixed zip timestamps so the SHA-1 is stable), and `VesselPackHost` builds it off-thread at enable,
  saves `resourcepack/gptgodmc-vessel.zip`, serves it over HTTP (`vessel.resource-pack.port` 8770, URL
  host = the player's virtual host unless `public-host`/`url`), pushes it with
  `Player#addResourcePack(PACK_ID…)` (stacks with the server pack) and tracks SUCCESSFULLY_LOADED.
  `Vessel` spawns one BlockDisplay per part and, when the pack is ready, an ItemDisplay twin
  (`item_model` gptgodmc:vessel/<look>/<piece>, `VesselModel.toItem`: centered + a right rotation of
  half a turn that cancels the client's own); twins are `setVisibleByDefault(false)` and `showTo`
  shows each player either the skin or the blocks (`refreshViewer` on join / pack status). Mood eyes
  swap the eye item to `eye_<mood>`. `Vessel` teleports all pieces together every 4 ticks, and only re-sends the transforms of bones that moved
  (interpolated). It walks, flies (`vessel_go` mode fly, soaring over terrain), floats at an ordered
  altitude, and draws mood particles, wings and gesture effects (slam shockwave, clap boom). It starts
  formless; `manifest` tears it in through a rift (`vessel.arrival: rift`, default; `storm` = the old
  ARRIVAL rite): `Rift` (pure: `Timeline` crack/open/climax/seal/shut, `ARRIVAL` and `departure(walk)`, size
  for a body, torn `shape`, `Palette` by the look's outline hue) opens a little behind the spot and stands
  still there, facing the way the god walks out (toward the nearest player within 128 blocks; it never turns
  toward viewers, by request): `RiftShow` sends mod players `RiftScenes` (the mod's rift mesh turned by its
  "yaw", which mods before the yaw support ignore), pack players a painted plane (`RiftTextures`/`RiftPack`,
  gptgodmc:rift/<palette>, Billboard.FIXED at that yaw, stretched open and shut), everyone else dust edges; at the climax the body forms in the rift and walks out
  (`emergeGoal`, slow pace). `vanish` in rift mode is the reverse (`depart`: `departing` counts as formless,
  it walks into a rift behind it, `swallowed` at the climax; not for a slain god); `RiftPreviewTest` renders
  rift-textures/arrival/departure/palettes.png. Size is in player heights
  (`VesselModel.SIZES`: mortal 1, lordly 1.5 default, towering 3, colossal 6; `vessel.size`, capped by
  `vessel.max-size`; legacy `vessel.scale` is still read by `GodSettings.vesselSize`); the god resizes
  it with `vessel_size`. `TrueForm`/`TrueFormShow` are `vessel_true_form`: a `Form` (hypercube,
  ophanim, seraphim, eye, pillar; `vessel.true-form.form`, auto = by archetype) and a `Gaze` for
  survival/adventure players who look (`vessel.true-form.gaze`: kill, smite = knockback + effects, at most half
  a heart and a death guard, or blind; legacy `lethal` is migrated; the god may only kill where gaze is kill).
  `AngelForms` is the pure geometry (particles + display-entity sprites, fallback particles for players
  without the pack; each `Form` has its own `rise` above the ground; block sprites name a `box`, the pack's textured
  box (gold, ember, light) shown to pack viewers in place of the vanilla block; soft `glow` billboards use
  partial alpha; the eye takes a `focus` that shuts its bar pupils on the player it has fixed on, and `TrueFormShow.dart` makes it flick between players). While it
  lasts `TrueFormShow` gives nearby players a midnight storm sky (`vessel.true-form.darken-sky`, per-player
  time/weather, restored on fold), harmless lightning, a ground shockwave, a camera jolt, sounds behind
  them and `ModFx` screen effects for client-mod players, who are also sent `TrueFormScenes` (pure `FxScenes`
  JSON per form: a looping aura with bloom/rays/haloes/beams, an eye-opening scene, a struck scene) directly,
  not through `FxShow`, so plain players get no duplicate dust; `TrueFormTextures`/`TrueFormPack` add gptgodmc:trueform/<key> flat models to the pack;
  `AngelFormsPreviewTest` renders build/test-renders/trueform-<form>.png and trueform-angels.png. No name tag (`refreshNameplate` is a no-op); a
  TextDisplay speech bubble, an Interaction hitbox (`VesselListener` turns touches/strikes into
  prayers, sneak + right-click with an item into offerings). Entities are non-persistent and
  PDC-tagged. `VesselPreviewTest` renders it to build/test-renders/vessel-*.png with the same atlases
  and UVs as the pack (vessel-skins, vessel-skin-frames, vessel-atlases; vessel-looks is the block
  fallback); `VesselPackTest` checks the layout, models and hash stability.
- **voice/** — `VoiceModule` is the only entry point and never references voicechat classes;
  `VoicechatBridge` (loaded reflectively) handles mic packets and playback. `Transcriber` runs
  whisper.cpp via whisper-jni (model auto-download) or an OpenAI-compatible server; `Synthesizer`
  (lines are cut by `Phrases` and synthesized part by part, each handed to playback when ready; `Speech`
  passes its chat/title/bubble as a `show` runnable that `VoiceModule` runs when the voice starts or after
  `voice.tts.text-wait-ms`, at once if no listener is connected)
  talks to a local Kokoro/OpenAI-compatible TTS server (default `kokoro`, `am_michael`) and falls back to
  Piper (auto-install); `VoiceTone` picks the effect per line (the god's `tone` on whisper/announce/vessel_say,
  else `voice.tts.effect`, else the vessel mood via `voice.tts.moods`); `GodVoiceFx` applies it.
  Client-mod subtitles: `Speech` makes a `client/ModVoice.Line` per spoken line and calls its `show()` inside the
  `show` runnable (a `say` message to players with the `subs` feature, who then get no vanilla proclamation title);
  it is also the line's `VoiceCue`, told as each part starts playing, so `Loudness` (pure: 10 loudness values a
  second) rides in `say` for the first part and in `voice` messages for the rest.
- **bridge/DebugBridge** — optional HTTP API (`bridge.enabled`) used by `harness/` and for
  `brain.mode: external`, where an outside agent plays the god (GET /perception, POST /tool).
- **viewer/** — the mind viewer (`viewer.enabled`, on by default, port 4673, localhost by default; token in
  viewer.json otherwise). `MindViewer.sync()` starts/stops/restarts it live on `/god reload` and the God
  Manager toggle (no server restart). `MindTrace` (pure ring buffer: last 50 thoughts per god, prompts/tools stored once by hash,
  40 images; every request body split into params/system/tools/messages plus Claude's reply, images and
  signatures shortened; blobs capped at 48M chars) is fed by `GodBrain` (`trace.*` calls; a no-op `Thought.NONE` when off); `TraceJson` shapes
  it; `MindViewer` serves `src/main/resources/viewer/index.html` + `/api/*` (polling) and passes every
  body through `Redact` (API key, every config key/token/secret/webhook value). Edits are POST
  `/api/edit` with the page's per-start CSRF header, applied by `god/MindEdits` (player favor/notes/deeds/
  summary/title, chronicle, commandments, goals, heart, persona → system prompt, briefing, edicts, wake,
  pause, raw memory/persona JSON); live state comes from `god/MindProbe`. `EventFeed` (pure) records every logged event once per world event
  (copies per god grouped by `EventLogger.log(world, …)`) and each god's state: pending / seen in thought #N /
  heard mid-thought / merged / dropped (hooks in `EventLogger` and `GodBrain`), shown in the World events tab. `ViewerPreviewTest` writes
  build/test-renders/viewer.html (sample data, opens without a server).
- **commands/** — `GodCommand` (`/god`, alias `/gptgod`; subcommands in `SUBS`, `/god help` text in
  `HELP`; `setkey` and the `usage` totals work only from the console: token counts and costs are never
  shown in game, only in the console `[God usage]` lines and `usage.csv` via `UsageLog`; the one
  exception is the client mod's God Manager screen, see below), plus
  `PrayCommand`, `OfferCommand`, `CommandmentsCommand`, `SwearCommand`. The API key comes from `anthropic.api-key`
  (`gptgod setkey`) or `ANTHROPIC_API_KEY`.
- **God Manager screen (client mod)** — operators with the mod (feature `gui`) get a real screen instead of
  the chest menu: `/god manager`, the star, or the mod's key G (`/god manager chest` and sneak + star keep
  the chest). Plugin: `client/ModGui` (incoming gptgod:main, per-player subscription, pushes the shown
  part every 10 ticks only when changed, checks `gptgodmc.admin` on every message), `client/ModGuiRequests`
  (pure: parsing, 8 KB / 20 a second limits, the `ACTIONS` table mapping each action to a chest
  `MenuModel.Kind` and its confirmation: none, yes, or the god's typed name), `client/ModGuiMessages` (pure
  builders), `gui/ModGuiViews` (state from `GodManager.snapshot`/`mortals`, `Advanced`, `MenuLayout`),
  `gui/ModGuiActions` (validates per field, then runs the chest menu's own `GodManager.perform` / `/god`
  code; ports and restart-only settings are read-only), `gui/Feedback` (replies that would go to chat are
  returned in the action's result or shown in the open screen), `god/Costs` (spend per god in 10 s buckets
  for 24 h; the screen's Costs page, hidden by `client-mod.show-costs: false`), `vessel/VesselHeads` (16x16
  head face per look). A slain god's Overview offers Revive (action `revive`): `god/ReviveRite` opens a
  particle rift in front of the operator for ~3 s with a rising hum, then calls `Godslayer.revive` and sends
  the vessel to step out of it (`ReviveRiteTest` renders build/test-renders/rite-revive.png). Mod: `client-mod/.../gui/` (`GuiProtocol`, `GuiModel` pure; `Ui` immediate-mode
  widgets; `ManagerScreen` + `GodPages`, `MortalsPage`, `ServerPages`). Golden files `gui-*.json` /
  `req-*.json` in client-mod/src/test/resources/protocol are checked by `ModGuiTest` (rewrite with env
  `GUI_FIXTURES_WRITE=1`) and `GuiTest`, and ship in the mod jar as sample data (`/godfx manager`).
  Plan: client-mod/GODMANAGER_PLAN.md.
- **guide/** — the guide to the gods (`guide:` in config.yml, on by default). `Guide` (pure) builds the
  content for a reader (`Context`: operator?, the gods of their world, `Feature`s switched off in config):
  chapters of `Para`/`Heading`/`Cmd` blocks, mortal chapters first (what the gods are, speaking, worship,
  oaths, powers, the living land, many gods, the Godslayer, quick reference), then 7 operator chapters
  (API key, God Manager, creating/removing gods and realms, binding commands, watching, more commands,
  config sections). `GuideBook` (pure) lays it out as written-book pages with the vanilla font's widths
  (`advance`, checked against the client jar's ascii.png), 14 lines of 112 px, a title page, clickable
  contents (`ClickEvent.changePage`) and each chapter on a fresh page. `Guides` (listener + `/guide`
  executor): gives the PDC-tagged WRITTEN_BOOK (`gptgodmc:guide`, `guide_rev`) once per player (mark
  `gptgodmc:guide_given` on the player, so it survives restarts) the first time they stand in a world with
  a god (join or world change), never while they carry one; using it rewrites it for its reader and opens
  it (`Player#openBook`), or for mod players with feature `guide` sends `client/ModGuide.message` (one
  `guide` message with every chapter) so the mod's `gui/GuideScreen` shows it (`GuideProtocol` pure,
  `GuideClient`; a click on a command types it into chat; `/godfx guide` opens the sample). `/guide`
  (alias `/tutorial`) and `/god tutorial` reopen it for anyone: /god has no plugin.yml permission so that
  subcommand reaches mortals, and `GodCommand.onCommand` checks `gptgodmc.admin` for every other one.
  Operators: `/guide <player>`. Golden files `guide-player.json` / `guide-operator.json` (written by
  `GuideTest`, decoded by the mod's `GuideTest`); `GuidePreviewTest` draws build/test-renders/
  guide-book-*.png and .txt; the mod's `GuideShotsTest` (env `GPTGOD_GUIDE=1 ./gradlew runClientGameTest`)
  screenshots the screen. `GuideTest.everyCommandItNamesExists` fails if the guide names a /god
  subcommand not in `SUBS` or a command not in plugin.yml.
- **RoundSystem / WorldManager** — the optional island round mode (`rounds.enabled`). When rounds are
  off, `WorldManager.getCurrentWorld()` is the server's main world.

## Common patterns

### Adding a god power
1. Add a `GodTool.sync(...)` (or `async`) to the right `*Tools` class with a clear description and
   a `Schema` (every property needs a description; `ToolRegistryTest` checks this).
2. Resolve names with `Targets` (players, nicknames, structures, "vessel", coordinates).
3. Return `ToolResult.ok(...)` describing what happened, or throw `ToolException` with guidance.

### Adding an event the god notices
Create a loggable extending `BaseLoggable`, return the text from `getLog()` (null to skip), override
`combine` to merge repeats, `attentionReason()` to wake the god early, and log it from
`LoggableEventHandler` via `EventLogger.log(() -> new MyLoggable(event))`.

### Debugging
- `/god status`, `/god journal [n]` (thoughts, acts, errors), `/god usage`.
- `debug.log-requests: true` prints every perception; `debug.webhook-url` posts raw requests/responses.
- `/god tool <power> <json>` runs a power without Claude; `/god view …` saves a render.
- `bridge.enabled: true` + `harness/` for end-to-end tests: an MCP server (`god-harness`) that runs a
  Paper 26.3 sandbox, deploys the jar, joins mineflayer bots, drives the bridge and takes screenshots
  with a real client (Windows, Node 22+; see harness/README.md). The tools live in one daemon
  (`harness/src/daemon.js`, port 8790; `node harness/call.js <tool> key=value` from a shell, the MCP entry
  forwards to it; sandbox folder from `harness/.env`). Live-god runs: `player_*` are real 26.3 clients with
  Fabric and the client mod, driven in-process through the mod's puppet port (`client-mod/.../client/puppet/`,
  inert unless the JVM property `gptgod.puppet.port` is set); `budget_set` caps API spend and pauses the god
  at the cap; `session_start` / `session_report` write a merged timeline of players and god to `harness/out/`.

## Constraints

- World access only on the main thread (`Sync.call` from other threads; never block the main thread
  on the brain).
- Keep the system prompt and tool list stable between thoughts (prompt caching).
- Model ids: default `claude-sonnet-5-5`, memory `claude-haiku-5-5`; pricing table in `TokenTracker`.
