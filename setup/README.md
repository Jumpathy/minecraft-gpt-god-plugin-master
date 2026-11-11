# GPTGOD easy setup

Scripts that set up a complete GPTGOD server on **Windows** and **Linux**: Java, the Paper server,
the god, its voice, and the mods players need. At the end they check everything.

**Never run a server before? Read [TUTORIAL.md](TUTORIAL.md):** it goes step by step from zero.

| What | Windows | Linux |
|------|---------|-------|
| Set everything up | Double-click **`setup-windows.bat`** | `bash setup-linux.sh` |
| Only check (changes nothing) | Double-click **`check-windows.bat`** | `bash setup-linux.sh --check` |
| See what setup would do | `setup-windows.bat --dry-run` | `bash setup-linux.sh --dry-run` |

You answer a few plain questions; Enter picks the suggested answer. Each step can be skipped, and
finished steps are detected, so running it again is safe.

## Options

The options are the same for both scripts.

| Option | Meaning |
|---|---|
| `--dry-run` | Show every step, address and command; change and download nothing. |
| `--check` | Only the read-only checklist. |
| `--new-server` | Make a new server without asking first. |
| `--cpu` | Run the Kokoro voice on the CPU even if an NVIDIA card could run it. |
| `--yes` | Take the suggested answer to every question. It never accepts the EULA. |
| `--accept-eula` | Accept the Minecraft EULA without asking. Use it only if you have read it and agree. |
| `--server <folder>` | Your server folder. Windows also takes `-ServerDir`. |
| `--jar <file>` | The GPTGOD jar to install. Windows also takes `-PluginJar`. |

---

## What it does, step by step

1. **Your server.** Asks whether you already have a server.
   - **No:** it creates a new folder (suggested `GPTGOD-server` in your home folder).
   - **Yes:** you give the folder that has `server.properties`.

   It also checks whether *this* server is running: Paper keeps `world/session.lock` open while it
   runs. On Linux it looks for a Java process working in that folder. If the game port is busy but
   this server isn't running, another program holds the port, and the script says so.
   - **1b. Java 25 and Paper**, for a new server or a folder without Paper:
     - Gets the Temurin 25 runtime from Adoptium, unpacked into `<server>/java`, so nothing is
       installed system-wide.
     - Gets Paper 26.3 from PaperMC, saved as `paper.jar`. Both are checked against the SHA-256
       their publisher lists.
     - Writes `start.bat` / `start.sh` with the memory you choose. An existing start script is
       kept.
   - **1c. EULA.** Shows a small window with the Minecraft EULA link and "I agree" / "I do not
     agree" (on Linux: zenity on a desktop, else a typed `yes`). `eula=true` is written only after
     you agree.
2. **GPTGOD plugin.** Copies the newest `gptgodmc-*.jar` it finds (in this folder, the folder
   above, or `build/libs/`) into `plugins/`.
   - If that exact jar is there already, it does nothing.
   - An older GPTGOD jar is moved to `gptgod-setup-backups/`, never deleted.
   - While the server runs, the jar goes to `plugins/update/` instead, and Paper swaps it in at
     the next start.
3. **Simple Voice Chat** server plugin, the newest Paper build from Modrinth, checked against
   Modrinth's SHA-512.
4. **Kokoro voice server**, Kokoro-FastAPI in a Docker container named `kokoro-tts`:
   - It listens on `127.0.0.1:8880` (this machine only) with `--restart unless-stopped`.
   - **GPU by default when it will work:** an NVIDIA card answers `nvidia-smi`, and Docker can
     reach it. On Windows that means Docker Desktop on WSL 2; on Linux, an `nvidia` runtime or
     NVIDIA CDI devices (the NVIDIA Container Toolkit). The container then runs `--gpus all`.
     RTX 50 cards (compute capability 12) get the CUDA 12.8 build.
   - If the GPU container can't start, it removes it and starts the CPU version.
   - Otherwise the CPU image, with a suggested `--cpus` limit, and the reason printed (for
     example `No NVIDIA graphics card found: using the CPU version`). Intel and AMD integrated
     graphics aren't used, because Kokoro-FastAPI's GPU image is CUDA-only.
   - On Linux with an AMD card and ROCm (`/dev/kfd`), it offers the experimental ROCm image
     (default no).
   - `--cpu` forces the CPU image.
   - An existing container is kept. If it runs on the CPU and a usable GPU is found, the script
     asks before replacing it; `--yes` never replaces it, and if the GPU version fails the CPU
     container is recreated. Otherwise it only offers `docker update`.
   - It never installs Docker; if Docker is missing, it explains how to get it.
5. **Whisper model**, saved to `plugins/gptgodmc/whisper/ggml-<size>.bin`, the file the plugin
   looks for.
6. **Piper backup voice** (off by default; the plugin installs it by itself when needed).
7. **Mods for players**, in `<server>/for-players/`:
   - The Fabric installer (from maven.fabricmc.net, SHA-256 checked).
   - From Modrinth, SHA-512 checked: Fabric API, Simple Voice Chat (Fabric), Mod Menu, and
     whatever those require (Placeholder API at the moment).
   - The GPTGOD client mod, only if a finished build is next to the script.
   - `HOW-TO-JOIN.txt`, and all of it zipped as `GPTGOD-player-mods.zip`.
   - Optionally the same mods for Minecraft on this computer: it runs the Fabric installer
     (`client -dir <.minecraft> -mcversion 26.3`) and copies the mods into `.minecraft/mods`,
     moving older versions of the same mods to `.minecraft/mods-backup-gptgod/`.
8. **Start + API key.** Offers to start the server, in its own window on Windows or in
   tmux/screen on Linux, and waits for `Done (`. It tells you to type `gptgod setkey <key>` and
   `op <name>` in the server console. **The scripts never ask for your key, never read it and
   never write it anywhere.** The API-key check reads only the plugin's startup banner in
   `logs/latest.log` (`API key: found in …` / `MISSING`).
9. **Final check.** A read-only checklist, also run alone by `--check` / `check-windows.bat`. Each
   line is green `[ OK ]`, red `[FIX!]` (with the fix printed under it), yellow `[WARN]` or gray
   `[info]`. It covers:
   - Java 25, the Paper jar, the EULA.
   - Running and fully started (`Done (` in the log).
   - The plugin loaded, and the API key.
   - The voice chat plugin and UDP 24454, Whisper.
   - Kokoro: whether it runs on the CPU or GPU, and how long one test sentence took compared
     with its length. It turns yellow when slower than real time, and points out an unused
     NVIDIA card.
   - The texture-pack server (TCP 8770) and failed pack downloads, the mind viewer (TCP 4673,
     warns if not local-only).
   - Geyser (UDP 19132), EssentialsX and LuckPerms notes.
   - **The Windows firewall:** block rules for the server's `java.exe`, and whether Java is allowed
     on networks Windows calls Public. It prints the admin commands for you to run; it never
     changes the firewall. On Linux it prints ufw/firewalld commands.
   - **DNS:** whether `api.anthropic.com` resolves or is filtered.
   - It ends with which ports to forward for friends, and which never to forward.

Ports, the voice-chat port and Geyser's port are read from your config files. The config readers
pick out only the lines they need (ports, bind addresses, on/off switches, the Whisper model and
Piper voice), never the API key. The scripts never edit `config.yml`.

## Every address it contacts

File downloads are shown with their full URL and asked about first. Small JSON lookups are
printed as `Lookup: <url>`. In a dry run, nothing is downloaded or looked up; only the local Kokoro
and DNS checks run.

| What | Address | Step |
|------|---------|------|
| Java 25 (lookup) | `https://api.adoptium.net/v3/assets/latest/25/hotspot?os=<windows\|linux>&architecture=<x64\|aarch64>&image_type=jre` | 1b |
| Java 25 (file) | `https://github.com/adoptium/temurin25-binaries/releases/download/...` (refused unless on `github.com/adoptium/`) | 1b |
| Paper (lookup) | `https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/latest` | 1b |
| Paper (file) | `https://fill-data.papermc.io/v1/objects/...` (refused unless on `papermc.io`) | 1b |
| EULA link (opened in your browser only if you click it) | `https://aka.ms/MinecraftEULA` | 1c |
| Simple Voice Chat, Paper (lookup) | `https://api.modrinth.com/v2/project/simple-voice-chat/version?loaders=["paper"]&game_versions=["26.3"]` | 3 |
| Modrinth files | `https://cdn.modrinth.com/data/...` (anything else is refused) | 3, 7 |
| Kokoro image | Pulled by Docker from GitHub Container Registry, pinned to v0.10.0: `ghcr.io/remsky/kokoro-fastapi-cpu:v0.10.0`, `ghcr.io/remsky/kokoro-fastapi-gpu:v0.10.0` (NVIDIA, CUDA 12.6), `ghcr.io/remsky/kokoro-fastapi-gpu:v0.10.0-cu128` (RTX 50), `ghcr.io/remsky/kokoro-fastapi-rocm:v0.10.0` (AMD, Linux, experimental). Project: https://github.com/remsky/Kokoro-FastAPI | 4 |
| Docker download page / install guide (only if Docker is missing) | https://www.docker.com/products/docker-desktop/ , https://docs.docker.com/engine/install/ | 4 |
| Whisper model | `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-<size>.bin` | 5 |
| Piper program and voice | `https://github.com/rhasspy/piper/releases/download/2023.11.14-2/...`, `https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/...` | 6 |
| Fabric (lookups) | `https://meta.fabricmc.net/v2/versions/loader/26.3`, `https://meta.fabricmc.net/v2/versions/installer` | 7 |
| Fabric installer (file + checksum) | `https://maven.fabricmc.net/net/fabricmc/fabric-installer/<version>/fabric-installer-<version>.jar` (+ `.sha256`) | 7 |
| Player mods (lookups) | `https://api.modrinth.com/v2/project/<fabric-api\|simple-voice-chat\|modmenu\|dependency>/version?loaders=["fabric"]&game_versions=["26.3"]` | 7 |
| DNS lookup only (no connection) | `api.anthropic.com` | 9 |
| Your own machine | `http://127.0.0.1:8880/v1/audio/voices` and `/v1/audio/speech` (Kokoro) | 4, 9 |

The Fabric installer itself contacts Fabric's and Mojang's servers to set up the launcher profile,
as it does when you run it by hand. The Whisper, Piper and voice addresses are the ones the plugin
itself uses (`voice/Transcriber.java`, `voice/Synthesizer.java`).

## Admin / root

Neither script needs administrator or root rights. Java, Paper and the mods go into folders you
own: the server folder and your `.minecraft`.

- **Windows:** `setup-windows.bat` and `check-windows.bat` run `setup.ps1` with
  `-ExecutionPolicy Bypass`. That applies to that one PowerShell process only, so Windows will run
  an unsigned script you downloaded; no system setting is changed. The firewall fix the check may
  print needs an administrator PowerShell, which you open and run yourself.
- **Linux:** run it as the user that runs the server, not root (it warns you). If your user can't
  use Docker yet, it offers `sudo docker` for the Docker commands of that run only, and explains
  why. Firewall commands are printed, not run.

## Check it yourself

These are plain text scripts, not a program, so you can see everything they do.

1. **Read them.** `setup.ps1` and `setup-linux.sh` contain no encoded or compressed code. Every
   address is listed in blocks near the top. The two `.bat` files are a few lines that start
   `setup.ps1`.
2. **Dry run.** `--dry-run` prints every command and download without doing any of them.
3. **Scan them.** Upload the files to https://www.virustotal.com, which checks them with about 70
   antivirus engines.
4. **Compare checksums** with `SHA256SUMS` in this folder (or the one published with the release):

   Windows (PowerShell):

   ```powershell
   Get-FileHash -Algorithm SHA256 setup.ps1, setup-windows.bat, check-windows.bat, setup-linux.sh
   ```

   Linux:

   ```bash
   sha256sum -c SHA256SUMS
   ```

   `Get-FileHash` prints the hashes in capitals; that's fine, compare them ignoring case.

### Regenerating SHA256SUMS

After any edit to the scripts, run this from this folder (Linux, macOS or Git Bash):

```bash
sha256sum setup-linux.sh setup-windows.bat check-windows.bat setup.ps1 > SHA256SUMS
```

`.gitattributes` here pins the line endings (LF for `.sh`, CRLF for `.bat` and `.ps1`), so every
checkout has the same bytes and the same hashes.

## No .exe on purpose

An unsigned `.exe` trips Windows SmartScreen and antivirus warnings, and nobody can read what's
inside it. A plain script avoids both problems.
