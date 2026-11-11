# Previews

**These are offline renders, not in-game screenshots.** The plugin's own code draws them while the
project is built and tested, with no Minecraft server running: the vessel's skeleton and outfits,
the renderer the god uses to see, the building generator and the particle choreography. In the game
the same shapes are made of block displays, real blocks and particles, so lighting and detail look
different, but the proportions, poses, layouts and patterns are the ones shown here.

To make them again from the project folder (JDK 17 or newer):

```
./gradlew previews
```

That runs the tests, which write the images to `build/test-renders/`, then copies them here, sorted
by what they show. It only adds or replaces images; this page is never touched.

- [The god's body](#the-gods-body-gods) (`gods/`)
- [Particles and rites](#particles-and-rites-particles) (`particles/`)
- [The true form](#the-true-form-true-form) (`true-form/`)
- [The gods' servants](#the-gods-servants-servants) (`servants/`)
- [Relics the gods make](#relics-the-gods-make-relics) (`relics/`)
- [How the god sees](#how-the-god-sees-vision) (`vision/`)
- [What the god builds](#what-the-god-builds-builds) (`builds/`)
- [Voice](#voice)
- [God Manager](#god-manager) (`manager/`)

---

## The god's body (`gods/`)

The vessel is a figure with a player's proportions, one display entity per body part, wearing the
plugin's own painted skin (players without its resource pack see it built from blocks), with a
glowing outline. By default it is `lordly`, one and a half times a player's height (2.7
blocks). It carries no name tag; its name is shown in the player list and in chat.

### Sizes

![The four named sizes beside a player-height silhouette](gods/vessel-sizes.png)

`vessel.size` in config.yml, or the god's own `vessel_size` power: **mortal** (1×, 1.8 blocks),
**lordly** (1.5×, 2.7 blocks, the default), **towering** (3×, 5.4 blocks) and **colossal** (6×, 10.8
blocks), each beside a grey figure as tall as a player. A number of player heights also works, up to
`vessel.max-size` (default 6).

### Looks and their skins

![The four looks in their own skins: front, three-quarter, back and a close-up](gods/vessel-skins.png)

`vessel.look` in config.yml, or `/god vessel look <name>`: **shade** (violet nebula flesh with pulsing
veins, a rune seal over the heart, a starry cape, amethyst ram horns — the default), **radiant**
(ivory and gold, white robes, a sunburst over the heart and a golden halo), **infernal** (crimson
flesh split by ember cracks, a molten mouth, dark horns, a cape smouldering at the hem) and **void** (a
living starfield with a spiral galaxy over the heart and a violet halo). `auto` picks one to suit the
god's personality. These are drawn with the exact atlases and UV mapping of the resource pack the
plugin sends players (`vessel.textures: custom`); the horns keep their block look on purpose.

![Four animation frames of each look, close up](gods/vessel-skin-frames.png)

The skins are animated (8 frames, blended): veins and seals pulse, embers flicker, stars twinkle, the
sunburst and the galaxy turn.

![The four texture atlases as they go into the pack](gods/vessel-atlases.png)

Each body part unwraps like a player-skin box (top and bottom, then right side, front, left side and
back), at twice a skin's pixel density.

![The four looks built from blocks](gods/vessel-looks.png)

What a player sees who declined the resource pack (or with `vessel.textures: vanilla`).

### Moods

![The six moods, three-quarter and back views](gods/vessel-moods.png)

Each mood changes posture, how high it floats, its outline and eye colour (the skin has its own
glowing eyes per mood) and its particles:
**calm**; **wrathful** (red outline and eyes, clenched stance, dark ember wings); **joyful** (gold
outline, arms open); **sorrowful** (pale blue, sunk low with its head bowed); **ominous** (dark
outline, cold blue eyes); **exalted** (risen high, arms spread, wings of light). Only the wing
particles are drawn here; the smoke, embers, souls, sparkles and beams moods add in game are not.

### Poses and the walk cycle

![Idle, walk, look, raise_arms, spread_arms, point and bow, each from four sides](gods/vessel-poses.png)

Each row is one pose seen from the front, three-quarter, side and back: standing idle, mid-stride,
turning its head to look at someone, and the gestures `raise_arms`, `spread_arms`, `point` and `bow`.
The cape hangs and swings with the body.

### Movement and gestures

![Levitating, flying, meditating and the slam, clap, roar, summon, wave, fold_arms and laugh gestures](gods/vessel-moves.png)

Top row three-quarter view, bottom row side view: levitating, flying (leaning forward), meditating
cross-legged, the two halves of `slam` (fists raised, then smashed down — in game this throws a
shockwave of rubble), `clap` (a thunderclap), `roar`, `summon`, `wave`, `fold_arms` and `laugh`.

---

## Particles and rites (`particles/`)

### Shapes

![Geometric primitives used by the particle choreography](particles/particles-shapes.png)

The building blocks every particle show is drawn from: circles, star polygons, spirals and helices,
rings and bursts.

### The `particles` power

![The shapes the god can paint with the particles power](particles/particles-shows.png)

What the god can paint on demand: burst, ring, spiral, vortex, pillar, sphere, beam, rain,
shockwave and crown, in any of its particle types, as a single flash or animated for up to 20
seconds.

### Marks

![The halo and curse marks around a player, seen from the side](particles/particles-marks.png)

The `mark` power: a **halo**, a small ring of golden light over a favored player's head, and a
**curse**, a dark cloud that hangs over a shunned one. Both follow the player for as long as the god
chooses.

### Rites

Each sheet shows key frames of one rite from start to finish. Rites play around powers (gifts,
healing, revival, blessings, curses, `empower`) or on their own through the `rite` power.

| | |
|---|---|
| ![Ascension rite](particles/rite-ascension.png) **ascension** | ![Anoint rite](particles/rite-anoint.png) **anoint** |
| ![Renewal rite](particles/rite-renewal.png) **renewal** | ![Resurrection rite](particles/rite-resurrection.png) **resurrection** |
| ![Blessing rite](particles/rite-blessing.png) **blessing** | ![Curse rite](particles/rite-curse.png) **curse** |
| ![Judgement rite](particles/rite-judgement.png) **judgement** | ![Seal rite](particles/rite-seal.png) **seal** |
| ![Arrival rite](particles/rite-arrival.png) **arrival** | ![Departure rite](particles/rite-departure.png) **departure** |

---

## The true form (`true-form/`)

![The god's true form: a particle wireframe of a turning hypercube inside counter-rotating rings](true-form/trueform-forms.png)

The god's rarest punishment: it unfolds into a wireframe of light — a four-dimensional cube turning
inside out within counter-rotating rings, a central eye and breathing spikes. In game, after a warning,
anyone who looks straight at it is struck by its gaze (`vessel.true-form.gaze`: `kill`, `smite` or `blind`).
The sheet shows it unfolding, the eye opening and the hypercube turning, with a 1.8-block player
for scale.

![A larger still of the open true form](true-form/trueform-hero.png)

A larger still of the open form.

![Top-down diagram of whose gaze counts](true-form/trueform-gaze.png)

Who its gaze kills, from above: players whose view cone contains the form with a clear line of
sight, within `vessel.true-form.range` (48 blocks) and `gaze-degrees` (14°). Players looking away or
behind blocks are spared; creative and spectator players are never affected.

![All six true forms side by side](true-form/trueform-angels.png)

The god can take four other true forms, in the manner of the angels of old (`form` on the tool,
`vessel.true-form.form` in config.yml): the ophanim (gold wheels within wheels, eyes set in their
faces, ringed with fire), the seraphim (six vast burning wings under two haloes; the two veiling its face
lift like a crown when its eye opens), the Watcher (a colossal eye that was never an animal's: a ball of void lit like an eclipse, an iris of
toothed wheels, and bar pupils: they wander
while it searches and slide into one point when it fixes on a player), and the pillar (a shaft of light
into the sky ringed by tilting gold wheels of eyes). They hang 14 to 16 blocks up and are 15 to 20 blocks
across. In game the sky also turns to a midnight storm for players nearby while one is revealed.
Wheels and the pillar's spine are block displays; eyes and feathers are flat textures from the god's
resource pack. Each sheet below shows the warning (eyes shut), the eyes open, a side view while it
watches someone else, and what players without the pack see (particle eyes and feathers).

![Ophanim](true-form/trueform-ophanim.png)
![Seraphim](true-form/trueform-seraphim.png)
![The colossal eye](true-form/trueform-eye.png)
![The pillar of eyes](true-form/trueform-pillar.png)

---

## How the god sees (`vision/`)

When the god uses its `look` power it receives a picture like these (640 × 360 for first-person,
512 × 512 for maps, 768 × 320 for panoramas). These come from small test scenes, so they are plain;
in game they show the real terrain and builds around the players.

| | |
|---|---|
| ![First-person view with the vessel and a player labelled](vision/pov.png) | ![The same view with x-ray](vision/pov-xray.png) |
| **eyes / vessel view.** Labels name every player and the vessel. | **x-ray.** Stone and dirt are removed: a winding cave tunnel and a diamond ore (cyan) show through the ground. |
| ![Top-down map with a player and a house labelled](vision/overhead.png) | ![360-degree panorama at night](vision/panorama-night.png) |
| **overhead.** A top-down map with coordinates, north marker, players and structures labelled (underground it becomes a cave slice). | **panorama.** 360° around a point, north-east-south-west, here at night, with a player's name and distance. |

---

## What the god builds (`builds/`)

The god's own procedural designs, each built in a style that suits it (`violet`, `divine`, `dark`,
`rustic`, `stone`). Shown here as placed blocks seen from a three-quarter view. In game it can also
place whole vanilla structures (villages, temples, outposts…) and schematics you add.

| | | |
|---|---|---|
| ![Temple, violet](builds/design-temple-violet.png) temple · violet | ![Temple, dark](builds/design-temple-dark.png) temple · dark | ![Temple, rustic](builds/design-temple-rustic.png) temple · rustic |
| ![Shrine](builds/design-shrine-violet.png) shrine | ![Cottage, violet](builds/design-cottage-violet.png) cottage · violet | ![Cottage, rustic](builds/design-cottage-rustic.png) cottage · rustic |
| ![Cottage, dark](builds/design-cottage-dark.png) cottage · dark | ![Prison, violet](builds/design-prison-violet.png) prison · violet | ![Prison, dark](builds/design-prison-dark.png) prison · dark |
| ![Prison, rustic](builds/design-prison-rustic.png) prison · rustic | ![Tower](builds/design-tower-violet.png) tower | ![Obelisk](builds/design-obelisk-violet.png) obelisk |
| ![Arena](builds/design-arena-violet.png) arena | ![Statue of the god](builds/design-statue-violet.png) statue of the god | ![Town of cottages around a shrine](builds/design-town-violet.png) town |

---

## The gods' servants (`servants/`)

![The five servants a god can summon: a seraph, a cinder imp, a wraith, a hierophant and a marble colossus](servants/servants.png)

What a god can summon to act for it. Each has its own body, painted from code like the god's own skin
and sent in the same resource pack: a **seraph** (angel) in robes that fall away into light, with four
wings of pearl and a sword of light; a **cinder imp** of cracked basalt with fire behind its teeth and
torn membrane wings; a **wraith** (shade) in a hood and robe cut from the night sky, with eyes of
soul-fire and a scythe; a **hierophant** (priest) in a golden mask and mitre, with a saint's disc, a sun
staff, an open book and its words turning in the air; and a **colossus** (guard) of gold-veined marble
round a core of light, with tower shield and halberd, standing on a turning circle of letters.
`servants/servant-<kind>.png` shows each from the front, three-quarter, back and side, in action and
close up; `servant-grips.png` looks closely at every hand that holds something; `servant-atlases.png`
is the painted texture of each.

Players who did not load the pack (and Bedrock players) see the vanilla mob underneath, wearing a few
blocks (`servants/servants-blocks.png`; the mobs there are rough stand-ins).

---

## Relics the gods make (`relics/`)

![Example relics painted by the plugin: a sword, a staff, an amulet, a tome, a crown, a vial, a wisp, a greatsword and a winged eye](relics/relic-samples.png)

With `creations.enabled` on, a god can forge a thing of its own design and give it to mortals or set it
afloat in the world. An artist model draws it as a map of materials (which texel is gold, which is gem,
which glows) and the plugin's painter does the shading, the darker far edge, the glints on metal, the
facets of gems and the animation, the same careful way every time. The sheet shows hand-written examples
of that input, each large, on sky and grass, at hotbar size, and its eight frames. `relic-proof.png` is
the sheet the artist is shown after each attempt, with rows and columns numbered so it can say what to fix.

Painting uses a stronger model at higher effort than the god's ordinary thoughts, so it is off by default
and warns operators when turned on. `/relics samples` adds these examples in game without calling any model.

![Relics as solid things, each from the front and turned three quarters: plain ones of even thickness with shaded sides, Skysplinter asleep and awake, and a vial of light](relics/relic-forms.png)

A relic is a solid thing in the hand. Most are the painting given an even thickness (top row), their
sides in the painter's own tones, a tone lighter on top where the light catches them. When a
god marks one as special, the artist sculpts it: broad parts may be thicker, rounded like a rod or a ball,
and the relic may have a second, awakened form on the same canvas. Narrow strokes on the diagonal stay
thin whatever the artist asks, since given depth they turn into stairs. Everything is lit and shaded by
the world like any vanilla item, darker at night, even what is painted as light: a pale blade with a white
heart reads as light without shining in the dark (bottom middle, and the vial bottom right). Skysplinter is the built-in
example: asleep it is only a hilt; a right-click kindles a blade of light. `relic-proof-two-forms.png` is
what the artist is shown for such a relic.

Relics can also be imbued (`imbue_relic`, `/relics imbue`): properties such as damage and speed, effects
when the bearer strikes, swings, uses it, kills or is hurt, attacks with their own look (a sweeping slash,
a crescent of light that flies ahead, a beam, a ground shockwave, a whirl, a throw that returns), light
carried through the dark, and waking and sleeping. Every power has a cost and one relic may hold only
`creations.power-budget` of it. Imbuing calls no model and costs nothing.

![Real client by day: Skysplinter asleep and awake beside other relics, from the resource pack alone (left) and drawn by the client mod (right)](relics/relic-forms-in-game.png)

Unlike the rest of this page, the `relic-in-game*`, `relic-forms-in-game` and `relic-held-*` pictures are
real screenshots: the client mod's game test shows the plugin's own relics in a real 26.3 client, once from
the resource pack alone (as a player without the mod sees them) and once drawn by the mod. Both look the
same: solid, with shaded sides. `relic-in-game.png` and `relic-in-game-night.png` put a vanilla iron sword
above them: relics darken at night exactly as much as it does.

![Skysplinter awake in the hand by day: an early build where light was forced bright (left), the resource pack now (middle), the client mod now (right)](relics/relic-held-before-after.png)

An early build forced what is painted as light to full brightness: a flat, pale staircase that ignored the
world's light (left; `relic-held-night-before-after.png` shows the same at night). Relics are now lit like
any item on both paths, and glow only when a power makes them (such as carried light).

---

## Voice

The god's voice cannot be shown as a picture. The tests write a clip of each voice effect
(`majestic`, `divine`, `booming`, `commanding`, `demonic`, `abyssal`, `ethereal`, `none`) to
`build/test-renders/voice-<effect>.wav` and `voice-speech-<effect>.wav`; `./gradlew previews` does
not copy them. Spoken examples of every voice and effect are in [`../voice_samples/`](../voice_samples/README.md).

---

## God Manager

`manager/manager-<page>.png` is a flat mock of each page of the operator menu (`/god manager`: main,
personality, mood, presence, command, settings, players, player), drawn by the tests from the same layout the plugin
uses. In game it is a chest menu with the normal Minecraft item icons.
