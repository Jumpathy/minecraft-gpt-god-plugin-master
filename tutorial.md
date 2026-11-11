# Setting up GPTGOD, step by step

> **Faster:** the setup script does steps 1 to 5 and 11 for you and checks the result. See
> [setup/TUTORIAL.md](setup/TUTORIAL.md). This guide is the long, manual version.

This guide assumes you have never run a Minecraft server before. It is written for **Windows**
first; Linux and macOS differences are in the notes under each step. Plan on about 30 minutes.

What you will end up with: a Minecraft **Paper 26.3** server on your computer, with a god that
players can pray to, that answers, remembers and acts — and, if you want, speaks aloud.

You will need:

- A computer that can run a Minecraft server (4 GB of free memory is comfortable; more for voice).
- **Minecraft Java Edition 26.3** to join and test.
- An **Anthropic account** with some credit (the god's mind is paid per use — see
  [costs.md](costs.md); a few dollars is plenty to try it).
- The plugin file `gptgodmc-0.2.0.jar` (from whoever gave you this, or build it yourself — see the
  end of [readme.md](readme.md)).

Contents

1. [Install Java 25](#1-install-java-25)
2. [Download Paper and make a server folder](#2-download-paper-and-make-a-server-folder)
3. [First start and the EULA](#3-first-start-and-the-eula)
4. [Install the plugin](#4-install-the-plugin)
5. [Start again](#5-start-again)
6. [Get an Anthropic API key](#6-get-an-anthropic-api-key)
7. [Give the key to the plugin](#7-give-the-key-to-the-plugin)
8. [Join and test](#8-join-and-test)
9. [Choose a personality](#9-choose-a-personality)
10. [The god's body](#10-the-gods-body)
11. [Optional: voice](#11-optional-voice)
12. [Useful settings](#12-useful-settings)
13. [Keeping costs down](#13-keeping-costs-down)
14. [Updating](#14-updating)
15. [Troubleshooting](#15-troubleshooting)
16. [Where the files live](#16-where-the-files-live)

---

## 1. Install Java 25

Paper 26.3 needs **Java 25**.

1. Go to <https://adoptium.net/> and download **Temurin 25** (the `.msi` installer for Windows x64).
2. Run it. On the "Custom Setup" page, make sure **Set JAVA_HOME variable** and **Add to PATH** are
   turned on.
3. Open a new **Command Prompt** (press Windows key, type `cmd`, Enter) and type:

   ```
   java -version
   ```

   It should say `25` somewhere in the first line. If it shows an older number, another Java is
   first on your PATH: uninstall it or restart your computer and try again.

> **Linux:** install Temurin 25 from your package manager or Adoptium's repository
> (`sudo apt install temurin-25-jdk` after adding their repo), then `java -version`.
> **macOS:** download the `.pkg` from Adoptium, or `brew install --cask temurin@25`.

## 2. Download Paper and make a server folder

1. Make a new, empty folder, for example `C:\minecraft-server`.
2. Go to <https://papermc.io/downloads/paper>, pick version **26.3**, and download the jar.
   Put it in your folder. The file name contains a build number (for example
   `paper-26.3-<build>.jar`); rename it to **`paper.jar`** to make the next step easier.
3. In the folder, create a text file named **`start.bat`** (make sure Windows does not call it
   `start.bat.txt` — turn on *View → File name extensions* in Explorer) containing:

   ```
   java -Xms2G -Xmx4G -jar paper.jar --nogui
   pause
   ```

   `-Xmx4G` gives the server 4 GB of memory. Use `-Xmx6G` if you will use voice.

> **Linux / macOS:** create `start.sh` instead:
>
> ```
> #!/bin/sh
> java -Xms2G -Xmx4G -jar paper.jar --nogui
> ```
>
> then `chmod +x start.sh` and run it with `./start.sh`.

## 3. First start and the EULA

1. Double-click `start.bat`. A black window opens, prints some lines and stops, saying you need to
   agree to the EULA.
2. A file called **`eula.txt`** has appeared in the folder. Read Minecraft's End User License
   Agreement at the link inside it (<https://aka.ms/MinecraftEULA>). If you agree to it, open
   `eula.txt` in Notepad, change `eula=false` to `eula=true`, and save. (This is your agreement with
   Mojang; nobody else can accept it for you.)
3. Run `start.bat` again. The server generates a world. When you see a line ending in
   `Done (…)! For help, type "help"`, it is running.
4. Type `stop` in that window and press Enter to shut it down.

That black window is the **server console**. You will type some commands there later. Commands typed
in the console have **no** leading slash.

## 4. Install the plugin

Copy **`gptgodmc-0.2.0.jar`** into the **`plugins`** folder inside your server folder.

## 5. Start again

Run `start.bat`. The first time, Paper downloads the libraries the plugin needs (the Anthropic SDK
and the Whisper speech library) from Maven Central, so this start takes a little longer and needs an
internet connection. Then you will see the plugin's banner:

```
==============================================
 GPTGOD 0.2.0 — an omniscient god for this world
 Mind: claude-sonnet-5-5 (effort auto), mode live
 God: not yet born (archetype: random)
 API key: MISSING. Run `gptgod setkey <key>` in this console, or set ANTHROPIC_API_KEY.
 Get a key at https://console.anthropic.com/
==============================================
```

You may also see `Simple Voice Chat is not installed: the god will not hear voices or speak aloud.`
That is fine — voice is optional (step 11).

The plugin has created `plugins/gptgodmc/config.yml`, with every setting explained in comments.

## 6. Get an Anthropic API key

1. Go to <https://console.anthropic.com/> and sign up or log in.
2. Add credit under **Billing** (a few dollars is enough to try).
3. **Set a spending limit** under **Limits** (for example $20 a month), so a busy server can never
   surprise you. This is the only hard cap on cost — the plugin itself does not stop spending.
4. Under **API Keys**, create a key and copy it. It starts with `sk-ant-`. Treat it like a password:
   anyone with it can spend your credit.

## 7. Give the key to the plugin

In the **server console** (the black window — **not** the in-game chat, where commands can be
logged and seen), type:

```
gptgod setkey sk-ant-your-key-here
```

It answers `API key saved to config.yml. The god can think.` The key is stored in
`plugins/gptgodmc/config.yml`, so do not share that file.

> **Alternative:** instead of saving it in the config, set an environment variable named
> `ANTHROPIC_API_KEY` before starting the server. On Windows, add a line
> `set ANTHROPIC_API_KEY=sk-ant-...` at the top of `start.bat`; on Linux/macOS add
> `export ANTHROPIC_API_KEY=sk-ant-...` to `start.sh`. A key in `config.yml` wins over the variable.

While you are in the console, make yourself an operator so you can use the admin commands:

```
op YourMinecraftName
```

## 8. Join and test

1. Start Minecraft **26.3**, choose **Multiplayer → Add Server**, address **`localhost`** (or this
   computer's address if you play from another computer on your network), and join.
2. The god wakes up as soon as the first player arrives. The first time, it is "born": it invents
   its own name and temperament.
3. Type in chat:

   ```
   /pray hello, is anyone there?
   ```

   You will see `You pray: …`, hear a chime, and see **✦ <its name> turns its gaze upon you ✦**
   above your hotbar. A second or two later the god answers.
4. Try saying its name, or a word like *god* or *help*, in normal chat. It notices.
5. Type `/god status` to see who it is, which model it uses, whether it is thinking, and any error.
   `/god journal` shows its recent thoughts and acts, and `/god help` lists every admin command.

Its name is shown at the top of the player list (hold **Tab**), with its epithet and what it is
doing, and before everything it says in chat.

The god remembers. Each player has a favor score, notes and a list of dated deeds (kills, prayers,
offerings, quests, how they treated its body). When you speak to it, it is shown your record before
it answers, so a player it despises may get silence. Old events are condensed to keep its memory
small, but nothing is lost: the full record is in `plugins/gptgodmc/god/annals/` (kept 90 days by
default, `memory.annals-retention-days`) and the god can search it. It chose its own name and may
change it, at most once a day.

If nothing happens, see [Troubleshooting](#15-troubleshooting).

### Giving the god orders

What players ask is only a request: the god may refuse. As an operator you have two ways to steer it:

- `/god say <text>` — guidance it follows as coming from the server owner, but it owes you no report.
- `/god command <text>` — a **binding** command, e.g. `/god command reveal your true form to all players`.
  It wakes the god at once; it must carry it out and then reports back to you in chat. `/god commands`
  lists the open ones and `/god command cancel <n|all>` withdraws them. If it has still not done it
  after 3 thoughts (`operator.command-retries`), you are told and the command is dropped.
  Commands can also be typed in the server console.

**The God Manager** is a chest menu that does the same with clicks: type `/god manager` (or
`/god manager item` for a nether star that opens it when right-clicked). Its pages let you choose a
personality, impose a mood for 10 minutes (`operator.mood-minutes`), summon or dismiss its body,
reveal its true form, give one-click or typed binding commands, and switch features on or off.
**Players & memories** lets you choose how it feels about each player (despise them, make them
its chosen, or anything between), plant a note or a deed it will believe, and erase or wipe what
it remembers about them. Only operators can open it.

> **Another plugin already uses `/god`?** (EssentialsX does.) The console then says
> `Another plugin owns /god; use /gptgod (or gptgodmc:god) for this plugin.` Use **`/gptgod`**
> everywhere this guide says `/god`.

## 9. Choose a personality

The god's personality comes from an *archetype*. The default is `random`. The choices are:

| Archetype | What it is like |
|---|---|
| `benevolent` | Warm guardian, generous, rarely punishes |
| `chill` | Laid-back, funny, mostly leaves you alone |
| `trickster` | Pranks, riddles, funny punishments |
| `evil` | Menacing, offers bargains with a price |
| `wrathful` | Stern god of law; demands worship, punishes harshly |
| `cosmic` | Strange alien intelligence |

- **Change it now:** `/god persona wrathful`. A new god is born with a fresh memory, and the choice
  is saved in the config.
- **See the list:** `/god persona list`. **A random new god:** `/god rebirth`.
- **In the config:** set `personality.archetype` in `plugins/gptgodmc/config.yml`. You can also
  set `god.name`, add to its description with `god.persona` (e.g. `"obsessed with cats"`), give it
  standing orders with `personality.briefing` (e.g. `"Never destroy spawn."`), edit an archetype's
  dials, or write your own archetype.

## 10. The god's body

By default the god has **no body**: it watches and acts unseen, and materializes in a particle storm
when it chooses to (usually when summoned or angered). You can also move its body yourself:

| Command | What happens |
|---|---|
| `/god vessel summon` | Its body appears next to you (or `summon <player>` / `summon <x y z>`) |
| `/god vessel follow` | It follows you around |
| `/god vessel fly <player>` | It flies there through the air |
| `/god vessel rise 10` | It floats 10 blocks up (0–40) |
| `/god vessel mood wrathful` | calm, wrathful, joyful, sorrowful, ominous, exalted |
| `/god vessel gesture slam` | raise_arms, spread_arms, point, beckon, judge, bow, cast, slam, clap, summon, roar, laugh, wave, nod, shake_head, fold_arms, meditate |
| `/god vessel look radiant` | shade, radiant, infernal, void, or auto |
| `/god vessel dismiss` | Its body dissolves |

Or tell it: `/god command appear before Steve and welcome him`.

The body has no name over its head; players learn who it is from its words and the player list.

Settings under `vessel:` in the config: `size` (`mortal` = a player's height, `lordly` = 1.5×, the
default, 2.7 blocks; `towering` = 3×; `colossal` = 6×; or a number of player heights), `max-size`
(the largest the god may make itself with its `vessel_size` power, default 6), `look`, `cape`,
`wings`, `outline`, `glow-color`, `manifest-on-wake` (`true` = its body is there from the start),
`offerings` (players can sneak + right-click it with an item, or use `/offer`), and the **true form**
(see step 12). Pictures of the looks, moods and gestures are in [previews/](previews/README.md).

**Its skin.** The body wears textures the plugin paints itself (glowing veins and a rune seal for
shade, gold and a sunburst for radiant, ember cracks for infernal, a starfield for void), and they
move. When you join, Minecraft asks whether to download *"The god's true appearance"*: click **Yes**.
That small pack sits on top of any pack the server already uses. If you say no, or the download
fails, you see the same body built from blocks instead. Nothing else changes.

The plugin hands the pack out from its own little web server on port **8770**:

- Playing on your own PC or LAN: nothing to do.
- On a rented server: open port 8770 (TCP) in the host's panel, like the game port. If your host only
  gives you certain ports, put one of them in `vessel.resource-pack.port`.
- Can't open a port? Upload `plugins/gptgodmc/resourcepack/gptgodmc-vessel.zip` somewhere with a
  direct download link and put the link in `vessel.resource-pack.url`.
- Don't want it at all: `vessel.textures: vanilla`.

If the console says a player *"could not load the vessel's resource pack"*, the address it shows is
the one their game tried; make sure it is reachable from their computer. Settings changes here need a
server restart.

## 11. Optional: voice

With **Simple Voice Chat** the god hears players' microphones and speaks aloud. Everything runs on
your server — no extra account, no cloud service, and no cost per use.

### 11.1 Install Simple Voice Chat

1. **On the server:** download Simple Voice Chat for **Paper/Bukkit**, version for Minecraft 26.3,
   from <https://modrinth.com/plugin/simple-voice-chat>, and put it in `plugins/`.
2. **On every player's computer:** they must install the Simple Voice Chat **mod** for their client
   (it needs a mod loader such as Fabric or NeoForge — the Modrinth page explains how). Without the
   mod a player can still play and pray, but cannot talk to or hear the god.
3. Restart the server. The console should now say
   `Voice enabled: listening with local transcription, speaking with kokoro TTS.`

### 11.2 Open the voice port

Simple Voice Chat uses its own **UDP** port, by default **24454** (set in
`plugins/voicechat/voicechat-server.properties`). If players join from outside your network, forward
**UDP 24454** on your router and allow it in your firewall (Windows asks the first time Java opens it
— allow it on private networks). For players on the same computer or network you usually need to do
nothing.

### 11.3 What downloads on first use

The first time someone speaks, and the first time the god speaks, the plugin downloads what it needs
and logs `Downloading <url> → <file>` with progress in megabytes:

| What | Where it goes | Size | Setting |
|---|---|---|---|
| Whisper speech-recognition model | `plugins/gptgodmc/whisper/` | `base.en` 142 MB (`tiny.en` 75 MB, `small.en` 466 MB) | `voice.transcription.whisper-model` |
| Piper (the fallback text-to-speech) and its voice, only if the Kokoro server does not answer | `plugins/gptgodmc/piper/` | about 90 MB together | `voice.tts.piper-voice` (default `en_US-ryan-medium`) |

When Whisper is ready the console says `Local Whisper ready (ggml-base.en.bin)`. The very first
reply may take a little longer while these download. For non-English servers use `base` or `small`
(without `.en`) and set `god.language`.

### 11.4 Choose how it sounds

Set `voice.tts.effect` in the config (then `/god reload`):

| Effect | Sounds like |
|---|---|
| `majestic` | the lightest touch: a big man in a stone hall |
| `divine` | larger still, in a cathedral |
| `booming` | a voice from the sky: huge, echoing, chest-shaking |
| `commanding` | close, loud, hard-edged, gives orders |
| `demonic` | low, growling, doubled an octave down (inhuman) |
| `abyssal` | very deep and muffled, from far below (inhuman) |
| `ethereal` | airy, shimmering, many-voiced (inhuman) |
| `none` | the plain text-to-speech voice |
| `auto` (default) | follows the god's mood: ethereal normally, commanding when ominous, demonic when wrathful |

With `auto`, the god's mood picks the effect (`voice.tts.moods` in the config), and the god may
choose a tone for any one line itself, for example commanding for an order or demonic for a threat.
Wrath powers (smite, meteor, earthquake, fling, explode) make it wrathful for about 20 seconds.

The first four keep a natural human voice and only make it larger: it is played slower to deepen it
(the speech engine talks faster to compensate, so the pace stays normal) and placed in a vast echoing
hall with a low rumble. Listen to the options in [`voice_samples/`](voice_samples/README.md).

`voice.tts.volume` sets loudness; players have their own **God** slider in the voice-chat settings.

### 11.5 The speech engine: Kokoro (default) and Piper

The god's default voice is Kokoro's `am_michael`. Kokoro runs as a small local server next to
Minecraft; the plugin sends it the god's words and plays what comes back. The simplest way is
**Kokoro-FastAPI** with Docker (see its project page, github.com/remsky/Kokoro-FastAPI, for the
current image; the CPU image needs no graphics card and listens on port 8880):

```
docker run -d --restart unless-stopped -p 8880:8880 ghcr.io/remsky/kokoro-fastapi-cpu:latest
```

The defaults already point at it:

```yaml
voice:
  tts:
    engine: kokoro
    url: http://localhost:8880/v1/audio/speech
    model: kokoro
    voice: am_michael          # or am_onyx, am_fenrir, bm_george, ...
    sample-rate: 24000
    fallback-to-piper: true
```

If the Kokoro server is not running, the console warns (at most every 10 minutes) and the god speaks
with the built-in Piper voice instead, which downloads itself and needs nothing else. To use Piper
only, set `engine: piper`. Nothing here uses OpenAI: the plugin talks to whatever server is at `url`,
here your own.
Likewise, `voice.transcription.engine: server` sends speech to a compatible transcription server at
`voice.transcription.server-url` instead of running Whisper inside the plugin.

To turn voice parts off: `voice.tts.engine: off` (silent god) or `voice.transcription.engine: off`
(deaf god).

## 12. Useful settings

All in `plugins/gptgodmc/config.yml`; run `/god reload` after editing (realm changes need a restart).

**How often it thinks**

| Setting | Default | Meaning |
|---|---|---|
| `brain.heartbeat-seconds` | 40 | Looks at the world this often while things are happening |
| `brain.min-gap-seconds` | 8 | Never thinks more often than this (except when addressed directly) |
| `brain.idle-seconds` | 270 | Muses on its own this often when nothing happens (0 = never) |
| `brain.max-steps` | 6 | Most rounds of acting in one thought |
| `brain.attention-words` | god, lord, pray, help… | Words in chat that make it look at once |
| `brain.wake-on-any-chat` | false | `true` = every chat message makes it look (livelier, costlier) |
| `brain.reaction-delay-ms` | 350 | Pause before answering, so a player can finish typing |

**Its mind**

| Setting | Default | Meaning |
|---|---|---|
| `anthropic.model` | `claude-sonnet-5-5` | `claude-haiku-5-5` ≈ 20× cheaper, `claude-opus-5-5` most thoughtful |
| `anthropic.effort` | `auto` | `auto` thinks hard only for judgment calls (requests, several people, commands, deaths); or fix `low`, `medium`, `high` |

Change them live with `/god model claude-haiku-5-5` and `/god effort medium`.

**Operator commands and the tab list**

| Setting | Default | Meaning |
|---|---|---|
| `operator.command-retries` | 3 | Thoughts a `/god command` may stay unfinished before it is dropped and you are told |
| `operator.mood-minutes` | 10 | How long a mood set in the God Manager lasts |
| `god.tab-list` | true | Show the god's name, epithet and state at the top of the player list |

**Limits on destructive powers**

| Setting | Default | Meaning |
|---|---|---|
| `powers.commands` | true | `false` = it cannot run raw commands |
| `powers.command-blocklist` | stop, op, ban, whitelist, … | Commands it may never run |
| `powers.explosions-break-blocks` | true | `false` = explosions and meteors don't break blocks |
| `powers.max-explosion-power` | 8 | 4 is TNT |
| `powers.max-block-changes` | 2000 | Most blocks one act may change |
| `powers.max-entities-per-spawn` | 12 | Most creatures per summon |
| `powers.forbidden-entities` | ender_dragon, wither | Creatures it may not summon |
| `powers.custom-powers` | true | Your own powers from `custom-powers:` (not checked against the blocklist) |
| `powers.rites` | true | Particle ceremonies around powers |
| `building.max-blocks` / `blocks-per-tick` | 250000 / 1500 | Build size limit and speed |

**The true form** — its deadliest act. After a warning, anyone who looks straight at it dies.

| Setting | Default | Meaning |
|---|---|---|
| `vessel.true-form.enabled` | true | `false` = it can never do it |
| `vessel.true-form.lethal` | true | `false` = looking only blinds and terrifies |
| `vessel.true-form.warning-seconds` | 4 | Warning before the gaze kills |
| `vessel.true-form.max-seconds` | 20 | Longest it can hold the form |
| `vessel.true-form.cooldown-seconds` | 600 | Wait before it can do it again |
| `vessel.true-form.gaze-degrees` / `range` | 14 / 48 | How directly, and from how far, looking counts |

## 13. Keeping costs down

The god only thinks while someone is online. The quickest savings:

- Use `anthropic.model: claude-haiku-5-5` (about 20× cheaper, a bit less perceptive).
- Use `anthropic.effort: low` (faster and cheaper, but it misreads conversations more).
- Raise `brain.heartbeat-seconds` (e.g. 90) and `brain.idle-seconds`, or set `idle-seconds: 0`.
- Leave `brain.wake-on-any-chat: false`.
- **Set a monthly limit in the Anthropic console.**

What it actually costs is printed in the **server console** (`[God usage]` lines) and saved to
`plugins/gptgodmc/usage.csv`; type `gptgod usage` in the console for the session total. None of it
is shown in game. Details and estimates: [costs.md](costs.md).

## 14. Updating

1. Type `stop` in the console.
2. Replace the old `gptgodmc-*.jar` in `plugins/` with the new one (delete the old file).
3. Start the server. New settings are added to your `config.yml` automatically; your values,
   the god's identity and its memory are kept.

To start completely fresh, `/god forget` wipes its memory, and `/god rebirth` creates a new god.

## 15. Troubleshooting

| What you see | What it means / what to do |
|---|---|
| Console: `API key: MISSING. Run \`gptgod setkey <key>\`…` or in game: `[GPTGOD] No Anthropic API key yet.` | Do [step 7](#7-give-the-key-to-the-plugin). |
| `The god slumbers: no Anthropic API key.` | Same: no key is set. |
| `/god journal` shows `Claude rejected the API key (401)` | The key is wrong or was revoked. Make a new one and run `gptgod setkey` again. The god pauses for 10 minutes after this, but setting a key resumes it at once. |
| `Claude is busy or rate limited (429)` (or 529, 5xx) | Too many requests or no credit left. It rests for a minute and retries. Check your credit and limits in the Anthropic console. |
| `Could not reach Claude (…); retrying in 30s.` | The server has no internet connection to the API. |
| `/pray` says `…but the god slumbers.` | The god has not woken up in this world yet (it wakes when a player arrives). Try `/god wake`. Your prayer is kept and it will see it. |
| Prayers get no answer, but no error | Check `/god status`: thoughts may be paused (`/god resume`), or `brain.enabled` / `brain.mode` changed in the config. |
| `Your words fall into silence. No god watches over this world.` | You are in a world the god does not rule (see `realms:` in the config). |
| `Patience. Your last prayer still echoes.` | Prayers are limited to one every 5 seconds per player. |
| `Only operators may command the god.` | Run `op YourName` in the console (or grant `gptgodmc.admin`). |
| `Your command #n (…) went unfulfilled after … thought(s); it has been dropped.` | The god did not close it within `operator.command-retries` thoughts. Check `/god journal`, word it differently, or check whether that power is turned off in the config. |
| `/god` opens another plugin's command, or console says `Another plugin owns /god` | Use `/gptgod …` instead. |
| `For safety, set the key from the server console` | `setkey` only works in the console, not in game. |
| `Simple Voice Chat is not installed` | Install it (step 11), or ignore it if you don't want voice. |
| `Could not hook into Simple Voice Chat; voice features are off.` | Your Simple Voice Chat version doesn't match; update it to the latest for 26.3. |
| `Transcription failed: Local Whisper could not start…` | The Whisper model download failed or the native library could not load. Check internet access, delete the half-downloaded file in `plugins/gptgodmc/whisper/`, and restart. Or set `voice.transcription.engine: off`. |
| `The god's voice failed: …` / `Piper is unavailable` / `HTTP 404 for …` | Piper or its voice could not be downloaded or run. Check internet access, the `voice.tts.piper-voice` name, or point `voice.tts.piper-path` at a Piper you installed yourself. |
| Players hear nothing | They need the Simple Voice Chat **mod**, UDP 24454 must be reachable, and their **God** volume slider must be up. |
| `/god usage` in game says costs are kept out of the game | By design. Type `gptgod usage` in the console. |
| Anything else | `/god status` (shows the last error) and `/god journal 30`. |

## 16. Where the files live

Everything is inside `plugins/gptgodmc/`:

| Path | What it is |
|---|---|
| `config.yml` | All settings (and the API key, if you used `setkey`) |
| `god/persona.json` | Who the god is (name, epithet, personality) |
| `god/memory.json` | Its ledger, quests, works, recent acts and chronicle |
| `god/edicts.json` | Open binding commands (`/god command`) |
| `god/realms/<id>/` | The same for each extra god, if you use realms |
| `usage.csv` | One row per API request: tokens and cost |
| `views/` | Pictures saved with `/god view` (or all of them with `vision.save-renders: true`) |
| `schematics/` | Your own `.schem` / `.nbt` files for the god to build |
| `whisper/` | The downloaded speech-recognition model |
| `piper/` | The downloaded text-to-speech program and voices |
| `worlds/` | Map folders for the optional round mode |
| `bridge.json` | Access token for the developer bridge (only when `bridge.enabled: true`) |
