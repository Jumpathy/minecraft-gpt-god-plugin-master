# Your own GPTGOD server, from zero

This guide is for someone who has **never run a Minecraft server**. The setup script does nearly
everything: it downloads Java and the server, installs the god, gets the voice and the players'
mods ready, starts the server, and then checks that it all works.

Plan on a few minutes of answering questions, plus download time (about 300 MB without the voice
server, 1.8 GB with it).

Each step ends with **What you should see**. If you see something else, look in
[Troubleshooting](#troubleshooting).

Contents

1. [What you need](#1-what-you-need)
2. [Run the setup](#2-run-the-setup)
3. [Accept the Minecraft EULA](#3-accept-the-minecraft-eula)
4. [The god's voice (optional)](#4-the-gods-voice-optional)
5. [Mods for players](#5-mods-for-players)
6. [The server starts](#6-the-server-starts)
7. [Give the god its mind (API key)](#7-give-the-god-its-mind-api-key)
8. [Make yourself an admin](#8-make-yourself-an-admin)
9. [Join and pray](#9-join-and-pray)
10. [The God Manager](#10-the-god-manager)
11. [Watch the god think (mind viewer)](#11-watch-the-god-think-mind-viewer)
12. [Let friends join](#12-let-friends-join)
13. [Check everything, any time](#13-check-everything-any-time)
14. [Choosing the AI](#choosing-the-ai)
15. [Extras: EssentialsX, LuckPerms, Bedrock players](#15-extras-essentialsx-luckperms-bedrock-players)
16. [Troubleshooting](#troubleshooting)
17. [Where everything is, and how to undo it](#17-where-everything-is-and-how-to-undo-it)

---

## 1. What you need

- A **Windows** or **Linux** computer that stays on while people play. 8 GB of memory is enough.
  With 16 GB or more you can comfortably run the voice server too.
- **Minecraft: Java Edition 26.3**, to join and test.
- An **Anthropic account** with a little credit. The god's mind is paid per use; a few dollars
  is plenty to try it (see [costs.md](../costs.md)).
- The GPTGOD download: a folder that contains this `setup` folder and the plugin jar
  (`gptgodmc-0.2.0.jar`).

You don't need to install Java or anything else first. The setup gets what's missing.

**What you should see:** a `setup` folder containing `setup-windows.bat`, `check-windows.bat`,
`setup-linux.sh`, `setup.ps1` and this file.

## 2. Run the setup

- **Windows:** double-click **`setup-windows.bat`**.
- **Linux:** open a terminal in the `setup` folder and run:

  ```bash
  bash setup-linux.sh
  ```

The script asks plain questions. **Pressing Enter picks the suggested answer**, shown in
capitals (`[Y/n]` means Enter = yes). Before every download it shows the full address and asks.

The first question is **"Do you already have a server?"** Press Enter for **no**. It then makes a
new server folder (suggested: `GPTGOD-server` in your user folder) and puts these in it:

- **Java 25**, from Adoptium. It goes inside the server folder, so nothing is installed on your
  system and no admin password is needed.
- **Paper 26.3**, the Minecraft server program, from PaperMC, saved as `paper.jar`.
- **`start.bat`** (Windows) or **`start.sh`** (Linux), to start the server later. It asks how much
  memory to give the server; the suggested amount is fine.
- The **GPTGOD plugin** and the **Simple Voice Chat** server plugin, in `plugins/`.

Every download is checked against the checksum its publisher lists.

> Want to see what it would do first, without changing anything? Run it with `--dry-run`
> (on Windows: open a terminal in the folder and type `setup-windows.bat --dry-run`).

**What you should see:** lines like `OK Paper 26.3 build 248 saved as paper.jar` and
`SHA256 checksum matches the publisher's.`

## 3. Accept the Minecraft EULA

Every Minecraft server needs its owner to accept Mojang's EULA (End User License Agreement). Only
you can do that, so the script always asks. On Windows, a small window appears; it may be behind
the setup window. Click the link to read the EULA, then click **I agree**. On Linux you get the
same window on a desktop, or you type `yes` in the terminal.

**What you should see:** `OK EULA accepted (eula.txt)`. If you click "I do not agree", the setup
continues, but the server can't start until you accept.

## 4. The god's voice (optional)

The script asks about three voice parts. The suggested answers are good:

| Question | What it is | Suggested |
|---|---|---|
| Simple Voice Chat | The server plugin that carries voice in game. | Yes |
| Kokoro voice server | The god's natural speaking voice. It runs in **Docker** (free): a 1.5 GB download once, or 4 to 6 GB for the graphics-card version. | Yes, if you have Docker |
| Speech-recognition model | Lets the god understand players who speak (about 142 MB). | Yes |
| Piper backup voice | The plugin installs it by itself when needed. | No |

Without Docker the god still speaks, with the plainer built-in Piper voice. To get Kokoro later,
install **Docker Desktop** (Windows, https://www.docker.com/products/docker-desktop/) or Docker
Engine (Linux, https://docs.docker.com/engine/install/), start it, and run the setup again.

**Graphics card or processor?** The script picks this by itself:

- **NVIDIA graphics card** (GeForce GTX 900 series or newer, including RTX 50): it uses Kokoro's
  **GPU version**, which speaks many times faster. It needs a current NVIDIA driver, and:
  - **Windows:** Docker Desktop on its WSL 2 engine (the default; *Settings → General → Use the
    WSL 2 based engine*).
  - **Linux:** the NVIDIA Container Toolkit
    (https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html).

  If the GPU version can't start, the script uses the CPU version instead and tells you why.
- **No NVIDIA card:** it uses the **CPU version**, which runs on any computer but is slower.
  Kokoro's GPU version is CUDA-only, so it can't use **Intel or AMD integrated graphics**, or AMD
  cards on Windows. The script says so plainly: `No NVIDIA graphics card found: using the CPU
  version`.
- **AMD graphics card on Linux:** Kokoro has an *experimental* AMD (ROCm) version. If ROCm drivers
  are installed, the script offers it (default no).
- **Mac with Apple Silicon:** Kokoro can use the Mac's GPU only when run directly, without Docker
  (its project page explains `start-gpu_mac.sh`). These scripts don't cover macOS.

To force the CPU version, run the setup with `--cpu`.

On the CPU, the script suggests a **CPU limit** for Kokoro (for example 6 cores). Keep it: without
one, Kokoro can grab every core while the god speaks, and the game stutters.

If you already have a CPU Kokoro and a usable NVIDIA card turns up later, the setup asks before
replacing it with the GPU version. It never replaces it on its own.

**What you should see:** `OK Started kokoro-tts.` and, near the end, a line like
`Kokoro voice  on the GPU, ... made 3.3 s of speech in 0.4 s`. If it takes longer than the speech
lasts, the line turns yellow: the voice will lag (see [Troubleshooting](#other-problems)).

## 5. Mods for players

To hear the god's voice, players on Minecraft Java add **Fabric** (a mod loader) and a few mods:
Fabric API, Simple Voice Chat, and Mod Menu, which adds a "Mods" button so you can check what
loaded. Players who skip this can still join and play; they just don't hear the voice.

The script puts everything in **`for-players`** inside your server folder, together with
**`GPTGOD-player-mods.zip`** for friends. That zip has a `HOW-TO-JOIN.txt` inside.

If Minecraft is installed on this computer, it offers to install them for you as well. Say yes: it
runs the official Fabric installer and copies the mods into your `.minecraft/mods` folder. Older
versions of the same mods are moved to `mods-backup-gptgod`, never deleted.

**What you should see:** `OK Send friends this zip: ...\for-players\GPTGOD-player-mods.zip`.

## 6. The server starts

The script asks **"Start the server now?"** Press Enter.

- **Windows:** a black window opens. That's the **server console**; keep it open while people
  play. **Windows Firewall may ask whether Java may use networks: tick BOTH "Private networks" and
  "Public networks", then click Allow.** If you tick only one, friends can't join; see
  [Troubleshooting](#players-cant-connect-the-windows-firewall).
- **Linux:** it starts in the background in `tmux` (or `screen`). The script tells you how to open
  the console: `tmux attach -t gptgod-server`. To leave it running, press Ctrl+B, then D.

The first start takes one to three minutes, because the server downloads the plugin's libraries.
The script waits for it.

**What you should see:** `OK The server is up.` The console's last lines include
`Done (…)! For help, type "help"` and the GPTGOD banner, which says `API key: MISSING`. That's
expected; step 7 fixes it.

## 7. Give the god its mind (API key)

1. Go to https://console.anthropic.com/ and sign in.
2. Under **Billing**, add a little credit.
3. Under **Limits**, set a monthly spending limit (for example $20). This is the only hard cap on
   cost.
4. Under **API Keys**, create a key and copy it. It starts with `sk-ant-`. Treat it like a
   password.
5. Click into the **server console** (the black window, **not** the game chat) and type:

   ```
   gptgod setkey sk-ant-your-key-here
   ```

The setup never asks for your key and never sees it; you type it straight into the server.

**What you should see:** `API key saved to config.yml. The god can think.`

## 8. Make yourself an admin

In the same console, type your Minecraft name:

```
op YourMinecraftName
```

**What you should see:** `Made YourMinecraftName a server operator`.

Then press Enter in the setup window. It runs its final check (section 13). Every line should be
green or gray.

## 9. Join and pray

1. Open the Minecraft Launcher. If you installed the mods, choose the profile
   **fabric-loader-26.3** (next to the Play button), then press **Play**.
2. If you installed Mod Menu, press **Mods** on the title screen: **Simple Voice Chat** should be in
   the list.
3. Choose **Multiplayer → Add Server**, address **`localhost`**, **Done**, then join.
4. A message offers **"The god's true appearance"**, a small texture pack for the god's body. Press
   **Yes**.
5. Type in chat:

   ```
   /pray hello, is anyone there?
   ```

**What you should see:** `You pray: …`, a chime, and **✦ (its name) turns its gaze upon you ✦**
above your hotbar. A moment later, the god answers. The first time, it is "born" and invents its
own name. Hold **Tab** to see its name at the top of the player list. With voice chat working,
press **V** for its settings; you'll hear the god speak.

## 10. The God Manager

Type `/god manager` in game; you need to be an operator, as in step 8. A chest menu opens, where
you can choose the god's personality, mood and look, give it binding commands, change settings and
see what it thinks of each player.

`/god status` shows whether it is thinking, and any error. `/god help` lists every admin command.

**What you should see:** a chest menu of glowing items. Click one to open its page. If it says
you lack permission, do step 8 first.

## 11. Watch the god think (mind viewer)

On the server's computer, open **http://127.0.0.1:4673/** in a browser. You see each thought live:
what the god perceived, what it decided, what each request cost, and what it remembers. You can edit
its memory there too.

It works only on that computer, on purpose: don't open or forward port 4673 to the internet.

**What you should see:** a page with the god's name and a list of its recent thoughts.

## 12. Let friends join

**On the same Wi-Fi or network:** friends join with this computer's local address.

- **Windows:** in a terminal, type `ipconfig` and use the `IPv4 Address`, for example
  `192.168.1.20`.
- **Linux:** run `hostname -I`.

**From the internet:** on your router, forward these ports to this computer. Search the web for
"port forwarding" plus your router's brand. Friends then join with your public address (search
"what is my IP").

| Port | Type | What for | Needed? |
|---|---|---|---|
| 25565 | TCP | The game | Yes |
| 8770 | TCP | The god's texture pack (its skin) | Yes, or they see a block body |
| 24454 | UDP | Voice chat | For voice |
| 19132 | UDP | Bedrock players (only with Geyser, section 15) | Optional |
| 4673 | TCP | Mind viewer | **Never forward** |
| 8880 | TCP | Kokoro voice server | **Never forward** |

If you can't forward ports (some internet providers, dorms and mobile hotspots don't allow it),
use a tunnel service or a small rented server (VPS). Forward the same ports there, and if players
reach the server through a different address than this computer's, set
`vessel.resource-pack.public-host` in `plugins/gptgodmc/config.yml` to the address they join with.

Send friends **`for-players/GPTGOD-player-mods.zip`**. It has `HOW-TO-JOIN.txt` inside.

**What you should see:** a friend joins, and the god notices them.

## 13. Check everything, any time

- **Windows:** double-click **`check-windows.bat`**.
- **Linux:** run `bash setup-linux.sh --check`.

It installs and changes nothing. It reads the server folder, the server's last log, the open ports,
Docker, the firewall and DNS, and prints a checklist:

```
  [ OK ] GPTGOD plugin      gptgodmc-0.2.0.jar, loaded at the last start
  [FIX!] API key            the server says MISSING: the god cannot think
                            Get a key at https://console.anthropic.com/ (API Keys), then type in the SERVER CONSOLE:
                                gptgod setkey <your key>
```

Green `[ OK ]` is fine, red `[FIX!]` says exactly what to do, yellow `[WARN]` is worth a look, and
gray `[info]` is for your information. Many checks need the server running, so start it first.

<a id="choosing-the-ai"></a>
## 14. Choosing the AI

By default the god thinks with **Claude**, from Anthropic. That needs the API key from step 7, and
the default model is set in `plugins/gptgodmc/config.yml` (`anthropic:` section). This is the setup
the scripts prepare and check.

You can use another AI instead: **OpenAI**, or a model running on your own computer (**Ollama**,
**LM Studio**, llama.cpp, vLLM) or a gateway such as OpenRouter. Set `llm.provider` in
`plugins/gptgodmc/config.yml` and type `/god reload`; no restart is needed. For example, for a
model in Ollama:

```yaml
llm:
  provider: openai-compatible
  base-url: http://localhost:11434/v1
  model: qwen3:8b
  context-tokens: 8192
```

A local server needs no key. For OpenAI, set `provider: openai` and type `gptgod setkey <your key>`
in the server console. Small local models (around 7B) get a simpler god automatically: about
fifteen core powers and a short rulebook. Copy-paste setups for each server, what each provider
cannot do, and how big a model you need are in
[docs/llm-providers.md](../docs/llm-providers.md). This is new and has only been tested against
stand-in servers, so try it on a test world first.

## 15. Extras: EssentialsX, LuckPerms, Bedrock players

- **EssentialsX** has its own `/god` (god mode). GPTGOD takes `/god` back once all plugins have
  loaded, so Essentials' god mode moves to `/egod`. If you set `operator.claim-god-command: false`,
  use **`/gptgod`** instead, which works the same.
- **LuckPerms:** to let someone who isn't an operator use the admin commands, type
  `lp user <name> permission set gptgodmc.admin true` in the console.
- **Bedrock players** (phones, consoles, Windows Bedrock) can join through **Geyser** and
  **Floodgate** (https://geysermc.org/). Geyser uses UDP port 19132. Bedrock players always see the
  god's block body and can't use voice chat. The check shows whether Geyser's port is open.

The setup doesn't install these; their own sites explain how.

## Troubleshooting

Start with the check (section 13): it catches most of these by itself and prints the fix.

### "Can't connect" right after starting

The server isn't ready until the console says `Done (…)! For help, type "help"`. The first start
takes longer, because it downloads libraries. Wait for that line, then try again.

If the check says **"port 25565 is taken by another program"**, another server is already running
on this computer. Stop it first. Two servers on one computer also fight over the voice,
texture-pack and viewer ports.

### Players can't connect: the Windows firewall

This is the most common problem. When Java first starts, Windows asks which networks it may use.
If only "Private" was ticked, every player who arrives through a network Windows calls **Public**
is silently blocked. VPN and tunnel adapters, and many new Wi-Fi networks, count as Public. A
rejected prompt can also leave a **block** rule, and a block rule beats any allow rule.

The check shows `Windows firewall ... BLOCKS Java` or `not allowed on Public networks`, and prints
the exact commands with your Java's path filled in. To run them, press Start, type **PowerShell**,
right-click it, choose **Run as administrator**, and paste:

```powershell
# Turn off rules that block this Java (the check prints your exact path):
Get-NetFirewallApplicationFilter -Program 'C:\path\to\java.exe' | Get-NetFirewallRule | Where-Object Action -eq Block | Disable-NetFirewallRule
# Allow this Java on every network type:
New-NetFirewallRule -DisplayName 'Minecraft server (Java)' -Direction Inbound -Program 'C:\path\to\java.exe' -Action Allow -Profile Any
```

The setup never changes firewall settings itself; you stay in control. At home only, you can
instead mark your network as Private: **Settings → Network & internet → your network → Network
profile type: Private**.

On **Linux**, the check prints the matching `ufw` or `firewalld` commands. On a rented server
(VPS), also open the ports in your provider's control panel.

### The god never answers, and the check says "a DNS filter blocks it"

Some networks filter domain names: Pi-hole, NextDNS, router "safe browsing" or parental controls,
school or work networks, and antivirus "web shields". If **`api.anthropic.com`** is blocked, the
god can't think. Allow that domain in the filter, or use another DNS server.

The same applies to players. If friends join with a **domain name** (like `myserver.example.com`)
and their network filters it, they see "Unknown host". They can join with the IP address instead,
or allow the domain in their filter.

### No voice: the voice chat mod isn't actually loaded

- On the title screen, press **Mods** (from Mod Menu). **Simple Voice Chat** must be listed. If
  there is no Mods button at all, Minecraft started without Fabric. In the launcher, choose the
  profile **fabric-loader-26.3**, not "Latest release".
- The mod files must be in the `mods` folder of the Minecraft installation you launch, and match
  Minecraft **26.3**. Fabric API must be there too.
- In game, press **V**. If the voice chat settings don't open, the mod isn't loaded.
- If the mod works but you hear nothing from the god, the check shows whether the server's voice
  plugin loaded and UDP port 24454 is open. For players outside your network, that port must be
  forwarded too.

### The texture-pack prompt (the god looks like blocks)

When you join, Minecraft asks about **"The god's true appearance"**. Press **Yes**. If you pressed
No, or never saw the question:

- In **Multiplayer**, select the server, press **Edit**, set **Server Resource Packs** to
  **Prompt** or **Enabled**, then rejoin.
- If you pressed Yes but the download failed, port **8770** isn't reachable from where you are.
  Allow and forward it like the game port. The check counts these failures. If players join
  through a tunnel or a different address, set `vessel.resource-pack.public-host`.

Players who decline still play normally; they just see the god built from blocks.

### Other problems

| Problem | Fix |
|---|---|
| `/god` turns on god mode | EssentialsX owns `/god`; use `/gptgod`. |
| The god's voice sounds robotic | Kokoro isn't running, so the god uses Piper. Start Docker, or run `docker start kokoro-tts`. |
| The god's voice is choppy, or starts late | The check shows how fast Kokoro speaks. If it says "slower than real time", the computer is too busy. Close heavy programs, run `docker update --cpus <n> kokoro-tts` with more cores (but leave some for Minecraft), or use an NVIDIA card (run the setup again). On a busy CPU, an occasional stutter is normal. |
| "Java 21 found; Paper 26.3 needs Java 25" | Run the setup again: for a new server it puts Java 25 inside the server folder. |
| The server stops at once | `eula.txt` must say `eula=true` (run the setup again to accept it). |
| Anything else | `/god status` in game, `logs/latest.log` in the server folder, and the long guide [tutorial.md](../tutorial.md). |

## 17. Where everything is, and how to undo it

Everything lives in your server folder, apart from the Kokoro container and the mods in
`.minecraft`:

| What | Where |
|---|---|
| Server, world, Java, plugins | the server folder (for example `GPTGOD-server`) |
| The god's settings (and your API key) | `plugins/gptgodmc/config.yml`. Keep it private. |
| Mods for friends | `for-players/` |
| Files the setup replaced | `gptgod-setup-backups/` in the server folder, and `.minecraft/mods-backup-gptgod/` |
| Kokoro | a Docker container named `kokoro-tts` |

To remove it all: stop the server, delete the server folder, and run `docker rm -f kokoro-tts` (and
`docker rmi ghcr.io/remsky/kokoro-fastapi-cpu:v0.10.0`, or `kokoro-fastapi-gpu:v0.10.0`, to free
the space). Remove the mods from
`.minecraft/mods`, and delete the `fabric-loader-26.3` profile in the launcher.

For every setting, see [tutorial.md](../tutorial.md) and the comments in `config.yml`. To see
exactly what the setup does and contacts, see [README.md](README.md).
