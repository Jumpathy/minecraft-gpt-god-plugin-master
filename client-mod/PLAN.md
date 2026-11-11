# GPTGOD client mod: plan

An optional Fabric mod for Minecraft 26.3 that makes the god look and feel bigger for Java players who
install it. Without the mod (vanilla Java or Bedrock through Geyser) the game is exactly what the plugin
gives today. The server decides everything; the mod only draws.

Decisions agreed with the user (2026-11-10):

| Question | Answer |
|---|---|
| Loaders | Fabric only. The loader layer stays thin so NeoForge can be added later. |
| Features | Everything: glowing particles + bloom, screen effects, HUD, sky moods, God Manager GUI, smoother god body, voice subtitles + direction, lip sync. Also: the god can compose its own client effects, and block-throwing effects get a smoother mod version. |
| Favor on the HUD | A vague tier only (despised / out of favor / unproven / favored / chosen, from `Memory.favorLabel`). Never the number. |
| First phase | Effects + HUD. |
| Intensity | Full by default, with sliders and a "no flashing" switch. |
| Shaders | Works with Sodium and Iris. When an Iris shader pack is on, the mod's bloom pass turns itself off and its particles are drawn brighter instead. |
| Distribution | A jar handed out to players (drop into `mods/`). |
| God Manager | A separate session (menu-redesign) is reworking the chest menu. The mod screen draws whatever pages the plugin's menu model describes, so it follows that redesign without changes. It comes last. |

## 1. Features

### Effects (phase 1 and 2)
- **Screen effects**: camera shake (with falloff by distance), colour flash, vignette, chromatic pulse
  and a short slow "heartbeat" zoom. Triggered by smite, the true-form gaze, meteors, earthquakes,
  explosions, the god's death, a god arriving or leaving, and divine judgement.
- **Glowing particles**: the mod's own particle types (registered with Fabric's particle API, textures in
  the mod jar): `glow_mote`, `ray`, `ring`, `shard`, `ember`, `rune`, `spark_trail`, `smoke_soft`. They are
  drawn additively into a separate glow buffer that a small bloom pass blurs and adds back, so the god's
  light really glows. With an Iris shader pack active the bloom pass is skipped; particles render as
  bright emissive sprites (shader packs already add their own bloom).
- **Enhancements, not replacements**: the plugin keeps sending its vanilla particles to everyone. For a
  player with the mod it also sends one short "effect" message (e.g. `rite judgement at X anchored to
  entity Y`), and the mod adds glow, light flashes and screen effects on top. Where vanilla particles and
  the mod's would double up badly, the plugin skips the vanilla ones for that player only (per effect,
  decided in the plugin, phase 2).
- **The god composes effects (`client_fx` power)**: a new god tool that takes a small effect description:
  emitters (shape: point, ring, sphere, column, spiral, beam, burst, wings, sigil; colour or gradient;
  count; speed; lifetime; glow; gravity; turbulence), optional screen effects, optional sound, optional
  debris. The plugin validates and clamps it, sends it to mod players, and turns it into the nearest
  vanilla particles (via `Spectacle`) for everyone else. The god also gets named presets ("nova",
  "halo_burst", "storm_eye", ...) so it does not have to write raw emitters every time.
- **Smooth debris (breaking the boundaries)**: block-throwing effects (Godslayer ground heave in
  `DeathPieces`, earthquakes, explosions/fling, meteors) send the mod a list of blocks (block state, start
  position, launch velocity, spin). The mod animates them every frame with gravity, tumbling, bounces,
  dust puffs and fading, and casts no real block changes. The plugin hides its display-entity version of
  that debris from mod players only; everyone else sees today's version. The god can add debris to
  `client_fx` too ("tear the ground up around me").
- **Sky and weather moods**: the god's mood tints sky, fog and clouds near it: wrathful = red-black
  sky and heavier fog, joyful = warm gold, sorrowful = pale blue and gentle fog, ominous = dark violet,
  exalted = bright white-gold, slain = grey and desaturated. Blended smoothly over a few seconds and
  stronger the closer you are to the god's domain. Purely visual.

### HUD (phase 1)
- A small panel (top-left by default, can be moved or hidden with a key): the god's name and epithet in
  its archetype colour, its current mood with an icon, the five heart feelings (wrath, joy, sorrow, dread,
  glory) as thin bars, its state (thinking, slain, absent), and its health while wounded by a Godslayer.
- Your standing: favor tier word + icon, your title (from `Titles`), and marks (halo/curse).
- The mission bar (today's `PurposeBar` boss bar) is drawn by the mod in the god's style instead; the
  plugin stops sending the boss bar to mod players.
- Several gods (realms) at once: the panel shows the god of the world you are in, plus a compact list.

### Voice (phase 3)
- **Subtitles**: the god's spoken lines as styled subtitles (name colour, tone, a soft reveal timed to
  the voice). The plugin already decides when a line is shown (`Speech` / `VoiceModule`); it sends the
  same moment to the mod.
- **Direction**: a marker at the screen edge pointing at the speaker (vessel or the god's position), and
  subtitles placed on that side.
- **Lip sync**: while the voice plays, the plugin sends a coarse loudness envelope (10 values a second)
  per line; the mod moves the vessel's jaw/face glow with it (needs phase 4's body).

### The god's body (phase 4)
- For mod players, the mod draws the vessel itself instead of the ~20 display entities: the same skin
  textures (the item models already in the plugin's resource pack), with every bone interpolated every
  frame, emissive eyes and glow, smooth wings and true forms.
- The plugin stays in charge of the pose: it already computes each bone's transform (`VesselModel`); it
  sends those transforms to mod players near the vessel instead of showing them the display entities
  (`Vessel.showTo` already decides per player). The mod interpolates between server updates (curves,
  not straight lines) so it looks smooth at any frame rate.
- True forms and demigod wings follow the same approach.

### God Manager screen (phase 5)
- A proper screen (tabs, sliders, toggles, player list with heads) for players with `gptgodmc.admin`.
- Data-driven: the plugin sends the current menu page as data (title, buttons with icon/label/lore/
  state, sliders), the mod draws it, and clicks go back as "clicked button X" messages that the plugin
  handles with the same code as chest clicks. So the redesign in the separate session carries over, and
  permission checks stay in the plugin. Without the mod, the chest menu works as before.

### Mod settings
A config screen (Mod Menu if installed, and a key binding): shake, flash, bloom, particle amount, sky
mood strength, HUD on/off and position, subtitles on/off, "no flashing" (turns flashes into soft fades),
and "use the plain vanilla look" (the mod announces fewer features so the server treats you as vanilla).

## 1b. The god's effects toolset (the heart of phase 2)

The god is not limited to canned effects. It gets tools to compose and run its own visuals on mod
players' clients, live, the same way it composes songs today.

**Tools**
- `fx_cast`: play a *scene* (described below) at a place, on a player, on its vessel, or following an
  entity. Optional `save_as` keeps it under a name.
- `fx_replay`: play a saved scene or a built-in preset by name, with overrides (place, scale, colours).
- `fx_stop`: stop a running scene by its handle (or all of its scenes).
- The tool description lists the layer types, sprites and presets, so the god simply has the toolset.
  Saved scenes live in `<god folder>/fx.json` and are listed in the tool description (live `Supplier`).

**A scene** is JSON: where it is, how long it lasts, and a list of layers, each with its own `delay` and
`duration` (in ticks), so a scene is a timeline. Layer types:

| Layer | What it draws |
|---|---|
| `particles` | An emitter. `shape` (point, ring, disc, sphere, column, spiral, helix, vortex, beam, line, burst, wings, sigil, rain), `sprite` (mote, spark, ember, rune, smoke, star, shard, ray), `count` or `rate`, `radius`, `height`, `motion` (outward, inward, up, fall, orbit, swirl, still), `speed`, `gravity`, `drag`, `turbulence`, `size` and `color` as start→end gradients over each particle's life, `glow` |
| `beam` | A pillar or ray of light between two points (or to the sky), with width, colour, flicker |
| `shockwave` | A ring that expands along the ground or as a sphere |
| `dome` | A translucent shell (shield, eclipse, bubble) that grows, holds and fades |
| `debris` | Blocks torn loose and flung: `pick` (ground under the scene, radius, count) or explicit blocks, launch speed, spin, gravity, bounce. Smooth on the client; never changes the world |
| `screen` | Shake, flash, vignette, pulse (as in phase 1) |
| `sky` | Tint sky/fog/clouds for a while, on top of the mood |
| `sound` | A vanilla sound key, volume, pitch |
| `synth` | A sound the god designs: up to 6 voices, each a wave (sine, triangle, saw, square, noise), pitch start→end, volume envelope (attack, hold, release), vibrato, plus echo. Rendered to audio by the mod and played at the scene's place. `fallback` names the vanilla sound others hear |
| `glyph` | Floating text or runes in the world (the god's own words, as light) |

Scenes can `follow` an entity, be `scale`d, and loop until stopped (auras, storms).

**Safety and fallback**
- The plugin validates every scene (schema, clamps: at most 16 layers, 4,000 live particles, 150 debris
  blocks, 60 s unless looping with a handle; max 8 running scenes per god) and applies cooldowns.
- Everyone without the mod gets a vanilla version built by the plugin from the same scene: particle
  layers through `Spectacle`'s shapes with the nearest vanilla particle and colour, beams as end-rod
  lines, shockwaves as dust rings, debris as today's block displays, sounds as they are. The god is told
  how many mortals saw the full version and how many the plain one.
- The mod clamps again on its side and has a particle budget, so no scene can freeze a client.

## 1c. Alive, not static: the god authors the look

The mod is a renderer; the god decides what it renders. Nothing below is fixed per effect or the same
for every player.

- **A style of its own.** Each god has a `style` it writes itself (`fx_style` tool; first drafted by the
  memory model at birth from its persona, saved in `<god folder>/style.json`): palette, favourite
  sprites and shapes, motion character (slow and heavy, sharp and electric...), its sky look per mood,
  its HUD accent and sigil. Every built-in effect (rites, smite, arrival, death) is drawn *through* the
  style, mood, heart levels and a random seed, so two gods never look alike and one god never repeats
  itself exactly. The god may restyle itself whenever it changes.
- **A growing spellbook.** Scenes the god saves (`fx_cast ... save_as`) become its own named effects,
  listed back to it in perception-free tool text. It can refine them, chain them, and attach one to a
  power ("when I smite, play `ashen_verdict`"; `fx_bind` tool), so the mod's effects are increasingly
  ones this god invented on this server.
- **A library in its brain.** The spellbook is `<god folder>/library.json`, one per god: each entry has a
  name, a kind (`fx` scene or `routine` = a saved sequence of powers and scenes with delays, replayed as
  one act), the god's own note on what it is for, use count and last use. Tools: `library_save`,
  `library_run`, `library_forget`; the most used and most recent entries are listed to the god each
  thought (a short index; `library_read` returns a full entry so it can refine it). It survives
  restarts and is editable in the mind viewer.
- **Personal.** Scenes and HUD messages can target one mortal: private visions, an omen line or sigil on
  one player's panel (`omen` layer), a sky only they see, a whisper trail leading somewhere. Built-in
  effects are also tinted by the viewer's standing (the chosen see warmth where the despised see dread).
- **Unprompted.** The system prompt tells the god these are its senses of presentation, to use on its
  own initiative (idle thoughts included): weather for its mood, a sign for a prayer it liked, a
  warning before wrath. No tool is required by any power; all of it is the god's choice.
- **Never required.** Players without the mod get the vanilla rendering of the same decisions.

## 1d. Beyond particles: the divine renderer (agreed 2026-11-10)

Veil has no official build past 1.21.1 and Photon's 26.x builds are NeoForge-only, so the mod has its
own renderer on the game's GPU API (custom pipelines, its own shaders, the built-in post chain). No
extra mod to install. The scene language grows these layer types; each has a vanilla fallback or is
simply skipped for players without the mod.

| Layer | What it does |
|---|---|
| `post` | Full-screen shader passes the god mixes and animates: bloom, shockwave distortion that bends the world around a point, heat haze, radial blur, chromatic aberration, colour grading (desaturate, tint, invert, contrast), god rays from a point, film grain, pixelate |
| `mesh` | Real 3D geometry: solid light beams, forking lightning, domes with rim glow, ribbons and trails, rifts/portals with animated surfaces, ground sigils as decals, tendrils, rings, shards. Each takes a `surface` (a built-in or a formula) |
| `illusion` | Client-only fake blocks: ground cracks, terrain rising into pillars or floating away, structures crumbling, flying debris with smooth physics. The server's world never changes |
| `sky` (extended) | Eclipse, extra moons, a colossal eye or the god's sigil in the sky, star fields, aurora, falling stars |
| `camera` | Zoom punch, slow roll, letterbox bars, a brief pull of the view toward a point. Short, capped, and off under "no flashing"/reduced motion |
| `surface` / `shader` | The god's own shader maths (below) |

**The god's shaders.** Two levels:
- *Formulas (default)*: expressions over `uv`, `pos`, `time`, `dist`, `noise()`, `fbm()`, `sin`, `mix`,
  `smoothstep`, colour constructors. No loops or branches; compiled by the mod into a fixed shader
  template, so cost is bounded and it cannot fail in ways that matter. Used for mesh surfaces, sky
  objects and custom `post` passes.
- *Raw GLSL (allowed, gated)*: a fragment-shader body. Three gates before anyone sees it:
  1. a static checker in the plugin (size limit, no `while`, `for` only with a small constant bound, no
     `discard`-only shaders, no extensions/includes, only the uniforms and samplers we provide);
  2. a quick review by the stronger model (`fx.shader-review-model`, default the main brain model at high
     effort) that answers safe/unsafe with a reason, cached by the shader's hash so a saved shader is
     reviewed once;
  3. on the client: a compile test with automatic fallback to a plain surface, and a frame-time watchdog
     that disables a shader which costs too much and tells the plugin, which tells the god.
  Reviewed shaders are stored in the god's library like any other effect.

Phase 2 is therefore split: **2a** particles, tools, library (done first); **2b** `post` shaders and
bloom; **2c** `mesh` geometry; **2d** illusions and debris; **2e** sky and camera; **2f** formulas, then
raw GLSL with its gates.

## 2. Architecture

```
client-mod/                      separate Gradle build (Fabric Loom), not part of the plugin build
  src/main/java/net/bigyous/gptgod/client/
    GodClientMod                 entry point (ClientModInitializer)
    net/   Protocol, Payloads, Handshake, Messages (decode JSON → records)
    state/ GodState (per realm: name, mood, heart, health, quest...), PlayerStanding
    fx/    ScreenFx (shake, flash, vignette), Bloom (glow buffer + blur), Particles (types + factories),
           Emitters (client_fx shapes), Debris (block physics), SkyMood
    hud/   GodHud, Subtitles, SpeakerArrow
    body/  VesselRenderer (phase 4)
    gui/   ManagerScreen (phase 5)
    config/ ModConfig (JSON in config/gptgod-client.json), ConfigScreen
  src/test/java/...              plain JUnit tests for the pure parts (protocol decode, emitter maths,
                                 debris physics, sky blending, config)
  src/gametest/java/...          Fabric client game tests that drive a real client and take screenshots

src/main/java/net/bigyous/gptgodmc/client/   (plugin side, small)
  ModLink        registers the channels, tracks which players have the mod and which features,
                 send(...) is a no-op for everyone else
  ModMessages    builds the JSON payloads (pure, unit tested)
  ModFx          one-line hooks called from Spectacle, Rites, TrueFormShow, Godslayer, Speech...
```

Rules:
- The mod never changes the world and never decides anything; it draws what the server tells it.
- Every plugin hook goes through `ModLink.send(player, ...)`, which returns at once for players without
  the mod. Geyser/Floodgate players never handshake, so they never get a packet.
- Nothing in the plugin depends on the mod being present; the mod works with any plugin version that
  speaks a compatible protocol, and shows nothing on servers without GPTGOD.

## 3. Protocol

Two plugin-message channels (custom payloads):

| Channel | Direction | Use |
|---|---|---|
| `gptgod:hello` | client → server | handshake: `{"protocol": 1, "mod": "0.1.0", "features": ["fx","hud","sky","subs","body","gui","debris"]}` |
| `gptgod:main` | both | every other message |

Each `gptgod:main` payload is UTF-8 JSON: `{"t": "<type>", ...fields}`. JSON keeps both sides
independent and easy to debug, and these messages are small and rare. The one high-rate stream (phase
4's bone transforms) uses a compact binary layout on its own channel `gptgod:body`.

Handshake:
1. On join, Fabric registers the mod's channels (`minecraft:register`), and the mod sends `hello`.
2. The plugin answers `{"t":"welcome","protocol":1,"plugin":"0.2.0","features":[...the subset it will
   send...]}` and marks the player as a mod user. Features the client did not list (or turned off in its
   settings) are never sent.
3. If the protocol major versions differ, the plugin answers `{"t":"welcome","protocol":N,"features":[]}`
   and treats the player as vanilla; the mod shows a one-time toast "GPTGOD mod is out of date for this
   server".
4. Quitting clears the player's entry. The plugin re-sends the full state (`god`, `standing`, `quest`)
   right after `welcome` and whenever the player changes world.

Server → client messages (phase they arrive in):

| `t` | Fields | Phase |
|---|---|---|
| `welcome` | protocol, plugin version, features | 1 |
| `god` | realm id, name, epithet, archetype, colour, mood, heart {wrath, joy, sorrow, dread, glory}, state (present/formless/slain/thinking), health/max, vessel entity id, position | 1 (sent on change, at most every 0.5 s) |
| `standing` | realm id, tier, title, marks | 1 |
| `quest` | realm id, title, progress 0–100, or none | 1 |
| `screen` | kind (shake/flash/vignette/pulse), intensity, duration ticks, colour, source position (for falloff) | 1 |
| `fx` | kind (rite, smite, meteor, earthquake, explosion, arrival, death, aura...), id, position, anchor entity id, seed, scale, colour | 2 |
| `emit` | the `client_fx` description: list of emitters + optional screen + debris | 2 |
| `debris` | list of {block state, x, y, z, vx, vy, vz, spin}, lifetime, seed | 2 |
| `say` | realm, name, color, text, tone, kind (whisper, announce, vessel), from [x,y,z] (absent = in the mind), ms (expected spoken length), env (loudness 0..1, 10 a second, of the first spoken part; optional). Feature `subs`. Done | 3 |
| `voice` | realm, ms, env: a further part of the line being spoken starts playing now (the level follows it). Feature `subs`. Done | 3 |
| `gui` | page data / close | 5 |
| `relic` | id, rev, name, kind (blade, staff, trinket, tome, spirit), w, h, frames, ticks, png (base64 RGBA strip, w x h*frames, frames top to bottom), lit (optional, same size: laid over the picture; drawn like every other texel, a relic is lit and shaded like any item), depth (optional, base64 of w*h bytes: each texel's thickness front to back in texels, 0..12, 0 = a film with no thickness; without it the relic is 1/16 block thick), walls (optional, base64 PNG: per frame a 2w x 2h square whose quarters are each texel's side colours seen from above, below, left and right; 1..frames of them stacked; without it a side has its face's colour). The mod draws stacks whose item_model is `gptgodmc:relic/<id>` from this art, with no resource pack. A second form is its own art named `<id>__on`; the mod shows the change in the hand at once. Feature `relics`. Done | relics |
| `relic_gone` | id: forget that relic's art. Feature `relics`. Done | relics |

Client → server: `hello`, `gui_click` (page, button id), `gui_close`.

Limits the plugin enforces: at most 32 KB per message, at most 40 messages a second per player, emitters
and debris capped (e.g. 2,000 particles, 150 blocks per effect), all numbers clamped. The mod also clamps
everything it receives and drops unknown message types and fields, so newer plugins never crash older
mods (minor protocol additions are just new optional fields).

Shared fixtures: `client-mod/src/test/resources/protocol/*.json` are golden examples of every message.
The plugin's tests check that `ModMessages` produces exactly these; the mod's tests check it decodes
them. That keeps both sides in sync without sharing code.

## 4. Build setup

- `client-mod/` is its own Gradle build (own `settings.gradle`, wrapper, `gradle.properties`); the plugin
  build does not include it.
- Fabric Loom (`net.fabricmc.fabric-loom`, the unobfuscated-Minecraft plugin for 26.x), Minecraft 26.3,
  Fabric Loader 0.19.x+, Fabric API 0.16x+26.3, Java 25 toolchain. Versions in `gradle.properties`.
- Mod id `gptgod`, jar `gptgod-client-<version>.jar`. `fabric.mod.json` marks it client-only
  (`"environment": "client"`), so it is harmless if someone drops it on a server.
- Optional integrations as soft dependencies detected at runtime: Mod Menu (config screen), Iris (turn off
  bloom when a shader pack is active), Sodium (no special code; tested with it).
- Commands: `./gradlew build` (jar in `client-mod/build/libs/`), `./gradlew test`,
  `./gradlew runClientGameTest` (screenshots into `client-mod/build/screenshots/`), `./gradlew runClient`
  for a manual dev client.

## 5. Phases

1. **Skeleton, handshake, HUD, screen effects, sky moods.** Mod project, protocol, settings screen;
   plugin `ModLink` + `ModMessages` + hooks for god state, standing, quest bar and screen effects on
   smite / true-form gaze / meteor / earthquake / god death; sky mood; HUD. A debug command in the mod
   (`/godfx demo <effect>`) plays any effect locally for testing.
2. **The effects engine and the god's toolset.** The scene language and its renderer in the mod (glow
   particles, beams, shockwaves, domes, debris, glyphs, bloom, Iris detection); the `fx_cast` /
   `fx_replay` / `fx_stop` god tools with presets, saved scenes and the vanilla fallback; then the
   existing powers (`Rites`, `Spectacle`, `TrueFormShow`, `Godslayer`/`DeathShow`, demigod wings,
   earthquake/explosion/meteor debris) are re-expressed as scenes sent to mod players.
3. **Voice subtitles and direction (done).** `say` hook in `Speech`/`VoiceModule`, subtitles, speaker arrow,
   loudness envelope.
4. **Smooth god body.** `gptgod:body` stream from `Vessel` for mod players near it, client renderer with
   the pack's item models, emissive glow, lip sync, true forms and demigod wings.
5. **God Manager screen.** Data-driven page messages from the (redesigned) menu model, click messages
   back, chest menu untouched for everyone else.

Each phase ends with: both builds green, a jar you can try, screenshots, and a short note to the parent
session.

## 6. Testing

- **Mod unit tests** (JUnit, no Minecraft): protocol decoding against the golden fixtures, clamping of
  hostile values, emitter shapes, debris physics, sky blending, config load/save.
- **Plugin unit tests**: `ModMessages` against the same fixtures; `ModLink` sends nothing to players
  without a handshake or with a mismatched protocol; feature filtering; rate limits.
- **Client game tests** (Fabric client gametest API): start a real 26.3 client in a test world, play each
  effect through the mod's demo path, and save screenshots (`client-mod/build/screenshots/`) that are
  copied to `previews/client-mod/` for review.
- **End to end** (needs a server the user agrees to use; not `srv1`): Paper with the plugin, a Fabric dev
  client with the mod and a vanilla client side by side; `/god tool client_fx ...` and the existing
  powers; check the vanilla client still sees the old effects and the mod client sees the new ones.
- Bedrock/vanilla safety is checked by tests: no message is ever sent before a valid `hello`.

## 7. Installing (for players)

1. Install Fabric Loader for Minecraft 26.3 (fabricmc.net installer, or a launcher like Prism/Modrinth).
2. Put `fabric-api-<version>+26.3.jar` and `gptgod-client-<version>.jar` into the `mods` folder.
3. Optional: Mod Menu for the settings screen, Sodium/Iris work alongside.
4. Join the server. A toast "The god sees you" confirms the handshake.
