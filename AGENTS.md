# AGENTS.md

This file provides guidance to Codex (claude.ai/code) when working with code in this repository.

## Project Overview

GPTGOD is a Paper 1.20.4 plugin that adds an omniscient AI god, played by Codex through the official
Anthropic Java SDK. The god perceives the world, remembers players, speaks, uses ~40 tools (blessings,
punishments, building, vision, a physical "vessel" body), and optionally hears and speaks through
Simple Voice Chat with local Whisper (speech-to-text) and Piper (text-to-speech).

## Build and test

```bash
# JDK 17+ must run Gradle (on this machine JAVA_HOME points at JDK 8, so set it):
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
./gradlew shadowJar      # → build/libs/gptgodmc-<version>.jar (only plugin classes)
./gradlew test           # unit tests; also writes renders + voice clips to build/test-renders/
```

Runtime libraries (anthropic-java, whisper-jni) are NOT shaded: they are `compileOnly` in Gradle and
listed under `libraries:` in plugin.yml, which Paper downloads on first start. Versions live in
gradle.properties and are expanded into plugin.yml by processResources. Simple Voice Chat is a
`softdepend`.

## Architecture (src/main/java/net/bigyous/gptgodmc)

- **GPTGOD** — entry point. Loads config (with `GodSettings.migrateLegacy` for pre-0.2 configs),
  registers listeners/commands, starts `God`, `VoiceModule`, `DebugBridge`.
- **god/God** — scheduler. A 1 s heartbeat on the main thread decides when to think: soon after
  "attention" events (prayers, deaths, the god's name in chat — see `Loggable.attentionReason()`),
  every `brain.heartbeat-seconds` when events are pending, and on idle. Owns the `Vessel`.
- **god/GodBrain** — one agent loop per thought on its own executor. Each thought is a fresh,
  append-only conversation: perception (built on the main thread via `Sync.call`) → rounds of tool
  use with real results (up to `brain.max-steps`). Uses adaptive thinking (summarized thoughts go to
  the `Journal`), effort from config, a cached static system prompt (`Prompts.system`) plus a
  chronicle block, top-level automatic caching, and `fallbacks: "default"` on Sonnet 5.5.
  Do not edit earlier messages inside a thought (Sonnet 5.5 rejects edited history); forced
  `tool_choice` is also rejected on these models.
- **god/Memory** — persisted to `plugins/gptgodmc/god/memory.json`: per-player favor and notes,
  quests, the god's works, recent cycle records, and the chronicle (old records folded by
  `Chronicler` with the cheap memory model). Rendered into every perception.
- **god/Persona** — persisted to `god/persona.json`. Built from `personality.archetypes` in
  config.yml (description, speech, voice-effect, dials, likes/dislikes); name/epithet/temperament are
  invented by the memory model.
- **god/Perception** — the text snapshot the god sees each thought (must run on the main thread).
- **EventLogger + loggables/** — thread-safe event buffer. Loggables return `null` from `getLog()` to
  skip, may `combine()` with recent ones, and declare a `category()` (EVENT/SPEECH/PRAYER/DIRECTIVE).
- **god/tools/** — `ToolRegistry` holds every `GodTool` (name, description, `Schema`, handler).
  `sync` tools run on the main thread through `Sync.call`; `async` tools (e.g. `look`) do their own
  main-thread capture. Handlers throw `ToolException` with a message phrased for the god; results
  go back to Codex, including images. Keep the tool list order stable (prompt cache); descriptions
  may be live (`Supplier`), and `sdkTools()` rebuilds only when one changes. `CustomPowerTools`
  turns `custom-powers:` in config.yml into the `custom_power` tool (placeholders like {player},
  {y+40}, {text}). `VesselReactions` makes the vessel turn, gesture and flare into a mood after
  wrath and blessing powers.
- **god/Spectacle** — particle shows (`particles` tool), meteors and earthquakes, sharing one
  2-tick timer with a particle budget and caps on concurrent effects.
- **god/vision/** — `Vision` captures ChunkSnapshots + entities on the main thread, `Renderer`
  ray-traces off-thread (perspective, panorama, overhead surface/cave/x-ray) using `Palette` (block
  map colors + overrides); labels use the built-in `PixelFont` (works headless).
- **god/build/** — `Plan` (relative block placements, rotation), `Style` palettes, `Designs`
  (procedural buildings), `Blueprints` (vanilla structures/templates, user schematics in
  `plugins/gptgodmc/schematics/`, Sponge `.schem` via `Nbt`), `Builder` (places N blocks per tick,
  refuses to overlap player structures, keeps undo data).
- **god/vessel/** — the god's body. `VesselModel` is a pure-math rig with player-skin proportions
  (units are skin pixels; bones for torso, head, arms, legs, cape) plus target poses built from a
  `Motion` (walk cycle, levitation, flight lean, breathing, head look, mood posture, and timed
  gestures listed in `GESTURE_TICKS`, e.g. slam/clap/summon with `impactTick`) and particle wing
  geometry. `VesselLook` dresses it per body region (shade/radiant/infernal/void presets, horns or
  halo, outline and aura colors). `VesselMood` (calm/wrathful/joyful/sorrowful/ominous/exalted)
  sets rise height, glow and eye overrides and wings. `Vessel` spawns one BlockDisplay per part,
  teleports them together every 4 ticks, and only re-sends the transforms of bones that moved
  (interpolated). It walks, flies (`vessel_go` mode fly, soaring over terrain), floats at an ordered
  altitude, and draws mood particles, wings and gesture effects (slam shockwave, clap boom). TextDisplay name/speech bubble, an Interaction hitbox (`VesselListener` turns
  touches/strikes into prayers). Entities are non-persistent and PDC-tagged. `VesselPreviewTest`
  renders it to build/test-renders/vessel-*.png.
- **voice/** — `VoiceModule` is the only entry point and never references voicechat classes;
  `VoicechatBridge` (loaded reflectively) handles mic packets and playback. `Transcriber` runs
  whisper.cpp via whisper-jni (model auto-download) or an OpenAI-compatible server; `Synthesizer`
  runs Piper (auto-install) or an OpenAI-compatible TTS server; `GodVoiceFx` applies the voice preset.
- **bridge/DebugBridge** — optional HTTP API (`bridge.enabled`) used by `harness/` and for
  `brain.mode: external`, where an outside agent plays the god (GET /perception, POST /tool).
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
- `/god tool <power> <json>` runs a power without Codex; `/god view …` saves a render.
- `bridge.enabled: true` + `harness/` for bot-driven tests.

## Constraints

- World access only on the main thread (`Sync.call` from other threads; never block the main thread
  on the brain).
- Keep the system prompt and tool list stable between thoughts (prompt caching).
- Model ids: default `Codex-sonnet-5-5`, memory `Codex-haiku-5-5`; pricing table in `TokenTracker`.
