#!/usr/bin/env bash
# GPTGOD easy setup for Linux.
#
# Sets up a GPTGOD Minecraft server, from nothing or on top of the server you have:
#   0. No server yet? Gets Java 25 (Adoptium) and Paper (PaperMC) into a new folder, writes
#      start.sh and asks you to accept the Minecraft EULA.
#   1. Copies the GPTGOD plugin jar into your server's plugins folder.
#   2. Downloads the Simple Voice Chat server plugin (from Modrinth).
#   3. Starts the Kokoro voice server in Docker (the god's speaking voice).
#   4. Pre-downloads the Whisper speech-recognition model (so the god can hear players).
#   5. Optionally pre-installs Piper, the backup voice.
#   6. Gets the players' Fabric mods into for-players/ plus a zip for friends, and can install
#      them into Minecraft on this computer.
#   7. Starts the server and shows how to give the god its Anthropic API key (never asked for here).
#   8. Checks everything and prints a checklist: green = fine, red = fix this (with the fix).
#
# This is a plain, readable script. Nothing is hidden or encoded. Every download is shown (full
# URL) and asked about before it starts. It does not need root; run it as the user that runs your
# Minecraft server. Run it as often as you like: finished steps are detected and skipped.
#
# Usage:  bash setup-linux.sh [--dry-run] [--check] [--new-server] [--yes] [--server <folder>] [--jar <file>]
#   --dry-run      show every step and the exact commands, but change and download nothing
#   --check        only the read-only checklist (installs and changes nothing)
#   --cpu          run the Kokoro voice on the CPU even if a graphics card could run it
#   --new-server   make a new server (Java + Paper) without asking first
#   --accept-eula  accept the Minecraft EULA (https://aka.ms/MinecraftEULA) without asking; only if
#                  you have read it and agree (--yes alone never accepts it)
#   --yes          accept the suggested answer to every question
#   --server       your Minecraft server folder (the one with server.properties)
#   --jar          the GPTGOD jar to install (default: newest gptgodmc-*.jar next to this folder)

set -u
set -o pipefail

DRY_RUN=0
CHECK=0
FORCE_CPU=0
NEW_SERVER=0
ACCEPT_EULA=0
YES=0
SERVER_DIR=""
PLUGIN_JAR=""

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=1 ;;
        --check) CHECK=1 ;;
        --cpu) FORCE_CPU=1 ;;
        --new-server) NEW_SERVER=1 ;;
        --accept-eula) ACCEPT_EULA=1 ;;
        --yes|-y) YES=1 ;;
        --server) SERVER_DIR="${2:-}"; shift ;;
        --jar) PLUGIN_JAR="${2:-}"; shift ;;
        -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
        *) echo "Unknown option: $1 (try --help)"; exit 2 ;;
    esac
    shift
done

# ---------------------------------------------------------------------------------------------
#  Everything this script may contact or run. These match the plugin's own defaults
#  (src/main/resources/config.yml, voice/Transcriber.java, voice/Synthesizer.java).
# ---------------------------------------------------------------------------------------------
KOKORO_NAME="kokoro-tts"
KOKORO_PORT=8880
# Kokoro-FastAPI images on GitHub Container Registry (github.com/remsky/Kokoro-FastAPI), pinned.
KOKORO_CPU_IMAGE="ghcr.io/remsky/kokoro-fastapi-cpu:v0.10.0"          # any machine, ~1.5 GB
KOKORO_GPU_IMAGE="ghcr.io/remsky/kokoro-fastapi-gpu:v0.10.0"          # NVIDIA GTX 900 to RTX 40 (CUDA 12.6; arm64 too)
KOKORO_GPU128_IMAGE="ghcr.io/remsky/kokoro-fastapi-gpu:v0.10.0-cu128" # NVIDIA RTX 50 (CUDA 12.8, amd64)
KOKORO_ROCM_IMAGE="ghcr.io/remsky/kokoro-fastapi-rocm:v0.10.0"        # AMD (ROCm, experimental, amd64)
KOKORO_VOICE="am_michael"
WHISPER_URL_BASE="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-"
PIPER_RELEASE="https://github.com/rhasspy/piper/releases/download/2023.11.14-2/"
PIPER_VOICES_BASE="https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/"
MODRINTH_API="https://api.modrinth.com/v2/project/simple-voice-chat/version"
DOCKER_DOCS="https://docs.docker.com/engine/install/"
VOICECHAT_MOD="https://modrinth.com/mod/simple-voice-chat"
WHISPER_MODELS="tiny.en base.en small.en base small"

# ---------------------------------------------------------------------------------------------
#  Small helpers
# ---------------------------------------------------------------------------------------------
if [ -t 1 ]; then
    C_CYAN=$'\033[36m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'
    C_GRAY=$'\033[90m'; C_MAGENTA=$'\033[35m'; C_OFF=$'\033[0m'
else
    C_CYAN=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_GRAY=""; C_MAGENTA=""; C_OFF=""
fi
say()  { printf '%s\n' "$*"; }
step() { printf '\n%s=== %s ===%s\n' "$C_CYAN" "$*" "$C_OFF"; }
note() { printf '  %s%s%s\n' "$C_GRAY" "$*" "$C_OFF"; }
warn() { printf '  %s! %s%s\n' "$C_YELLOW" "$*" "$C_OFF"; }
done_() { printf '  %sOK %s%s\n' "$C_GREEN" "$*" "$C_OFF"; }
did()  { [ "$DRY_RUN" = 1 ] || done_ "$@"; }   # result of a change; a dry run already said "would"
dry()  { printf '  %s[dry run] would %s%s\n' "$C_MAGENTA" "$*" "$C_OFF"; }

if [ "$DRY_RUN" = 1 ]; then DONE_STATE="WOULD DO"; else DONE_STATE="READY"; fi
REPORT=()
report() { REPORT+=("$1|$2|$3"); }

# ask_yes_no "question" y|n  -> exit status 0 for yes
ask_yes_no() {
    local q="$1" def="$2" hint answer
    if [ "$def" = y ]; then hint="[Y/n]"; else hint="[y/N]"; fi
    if [ "$YES" = 1 ]; then
        say "  $q $hint -> $([ "$def" = y ] && echo yes || echo no) (--yes)"
        [ "$def" = y ]; return
    fi
    while true; do
        read -r -p "  $q $hint " answer || answer=""
        answer="$(printf '%s' "$answer" | tr '[:upper:]' '[:lower:]')"
        case "$answer" in
            "") [ "$def" = y ]; return ;;
            y|yes) return 0 ;;
            n|no) return 1 ;;
            *) say "  Please type y or n." ;;
        esac
    done
}

# ask_text "question" "default"  -> answer on stdout
ask_text() {
    local q="$1" def="$2" answer
    if [ "$YES" = 1 ]; then
        say "  $q [$def] -> $def (--yes)" >&2
        printf '%s' "$def"; return
    fi
    read -r -p "  $q [$def] " answer || answer=""
    if [ -z "$answer" ]; then answer="$def"; fi
    printf '%s' "$answer"
}

# Show the URL and target, ask, then download to "<file>.part" and rename when complete.
download() {
    local url="$1" target="$2" size="${3:-}"
    say ""
    say "  Download: $url"
    say "  Save to:  $target"
    [ -n "$size" ] && say "  Size:     about $size"
    ask_yes_no "Download this file?" y || return 1
    if [ "$DRY_RUN" = 1 ]; then dry "download it"; return 1; fi
    mkdir -p "$(dirname "$target")" || return 1
    local part="$target.part"
    if command -v curl >/dev/null 2>&1; then
        curl -fL --proto '=https' --tlsv1.2 -A gptgodmc-setup --progress-bar -o "$part" "$url"
    elif command -v wget >/dev/null 2>&1; then
        wget --https-only -U gptgodmc-setup -O "$part" "$url"
    else
        warn "Neither curl nor wget is installed; cannot download."
        return 1
    fi
    local rc=$?
    if [ $rc -ne 0 ]; then rm -f "$part"; warn "Download failed."; return 1; fi
    mv -f "$part" "$target"
    done_ "Downloaded $(basename "$target")"
}

# Run a command, printing it first. In a dry run only print it.
run_shown() {
    say "  > $*"
    if [ "$DRY_RUN" = 1 ]; then dry "run the command above"; return 0; fi
    "$@"
}

# Move an old file into <server>/gptgod-setup-backups with the date added (never deletes it).
backup_file() {
    local file="$1" dest
    dest="$SERVER_DIR/gptgod-setup-backups/$(basename "$file").$(date +%Y%m%d-%H%M%S)"
    if [ "$DRY_RUN" = 1 ]; then dry "move $file -> $dest (backup)"; return; fi
    mkdir -p "$SERVER_DIR/gptgod-setup-backups"
    mv -f "$file" "$dest"
    note "Old file kept as $dest"
}

copy_shown() {
    if [ "$DRY_RUN" = 1 ]; then dry "copy $1 -> $2"; return; fi
    mkdir -p "$(dirname "$2")" && cp -f "$1" "$2"
}

sha256_of() { sha256sum "$1" | cut -d' ' -f1; }

# Reads one simple "key: value" line from the plugin's config.yml. Only these keys are ever read:
# whisper-model and piper-voice (never the API key).
config_value() {
    local key="$1" def="$2" v=""
    if [ -f "$CONFIG_FILE" ]; then
        v="$(grep -E "^[[:space:]]*$key:" "$CONFIG_FILE" | head -n1 | sed -E "s/^[[:space:]]*$key:[[:space:]]*\"?([^\"#[:space:]]+).*/\1/")"
    fi
    printf '%s' "${v:-$def}"
}

port_listening() {
    local port="$1"
    if command -v ss >/dev/null 2>&1; then
        ss -ltnH 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]$port\$"
    else
        (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null
    fi
}

kokoro_answers() {
    command -v curl >/dev/null 2>&1 || return 1
    curl -fsS --max-time 5 "http://127.0.0.1:$KOKORO_PORT/v1/audio/voices" 2>/dev/null | grep -q "$KOKORO_VOICE"
}

# ---------------------------------------------------------------------------------------------
#  Making a new server, and the players' mods (official sources only)
# ---------------------------------------------------------------------------------------------
MC_VERSION="26.3"
PAPER_API="https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest"
case "$(uname -m)" in aarch64|arm64) JAVA_ARCH=aarch64 ;; *) JAVA_ARCH=x64 ;; esac
ADOPTIUM_API="https://api.adoptium.net/v3/assets/latest/25/hotspot?os=linux&architecture=$JAVA_ARCH&image_type=jre"
FABRIC_INSTALLERS="https://meta.fabricmc.net/v2/versions/installer"
FABRIC_LOADERS="https://meta.fabricmc.net/v2/versions/loader/$MC_VERSION"
MODRINTH_BASE="https://api.modrinth.com/v2"
EULA_URL="https://aka.ms/MinecraftEULA"
# Mods for players (Fabric). Their required dependencies are looked up on Modrinth too.
PLAYER_MODS="fabric-api simple-voice-chat modmenu"

# Small JSON lookups (no files): the address is printed; in a dry run nothing is sent.
# json_get <url> <python expression on j (the parsed JSON)>  -> the expression's lines on stdout
json_get() {
    note "Lookup: $1" >&2
    [ "$DRY_RUN" = 1 ] && return 1
    command -v curl >/dev/null 2>&1 || { warn "curl is needed for this step." >&2; return 1; }
    command -v "${PYTHON:-python3}" >/dev/null 2>&1 || { warn "python3 is needed to read the answer (install python3)." >&2; return 1; }
    curl -fsS --proto '=https' --tlsv1.2 -A gptgodmc-setup --max-time 30 "$1" |
        "${PYTHON:-python3}" -I -c 'import json, sys
j = json.load(sys.stdin)
r = eval(sys.argv[1], {"j": j})
for x in (r if isinstance(r, list) else [r]):
    print(x)' "$2"
}

# download, then compare the file's hash with the one the publisher lists; delete it on mismatch.
verified_download() {   # url target size algorithm(sha256|sha512) expected
    download "$1" "$2" "$3" || return 1
    local actual
    actual="$("$4sum" "$2" | cut -d' ' -f1)"
    if [ -n "$5" ] && [ "$actual" != "$5" ]; then
        rm -f "$2"
        warn "The $4 checksum does not match what the publisher lists. Deleted the file."
        return 1
    fi
    [ -n "$5" ] && done_ "$4 checksum matches the publisher's."
    return 0
}

# Newest Modrinth file per project for a loader + Minecraft version, plus required dependencies.
# Prints lines: filename<TAB>url<TAB>sha512<TAB>size<TAB>name
resolve_modrinth() {   # loader mc project...
    local loader="$1" mc="$2" p line deps
    shift 2
    local queue=("$@") seen=" "
    while [ ${#queue[@]} -gt 0 ]; do
        p="${queue[0]}"; queue=("${queue[@]:1}")
        case "$seen" in *" $p "*) continue ;; esac
        seen="$seen$p "
        line="$(json_get "$MODRINTH_BASE/project/$p/version?loaders=%5B%22$loader%22%5D&game_versions=%5B%22$mc%22%5D" \
            '([("\t".join([f["filename"], f["url"], f["hashes"]["sha512"], str(f["size"]), v["name"], v["project_id"]]) + "\t" + ",".join(d["project_id"] for d in v["dependencies"] if d["dependency_type"] == "required" and d.get("project_id"))) for v in j[:1] for f in ([x for x in v["files"] if x["primary"]] or v["files"])[:1]])')" || continue
        [ -z "$line" ] && { warn "No $loader build of '$p' for $mc on Modrinth."; continue; }
        IFS=$'\t' read -r f_name f_url f_sha f_size v_name v_id deps <<< "$line"
        seen="$seen$v_id "
        case "$f_name" in */*|..*) warn "Unexpected file from Modrinth for '$p'; skipped."; continue ;; esac
        [[ "$f_url" == https://cdn.modrinth.com/* ]] || { warn "Unexpected address from Modrinth for '$p'; skipped."; continue; }
        printf '%s\t%s\t%s\t%s\t%s\n' "$f_name" "$f_url" "$f_sha" "$f_size" "$v_name"
        if [ -n "$deps" ]; then
            IFS=',' read -r -a more <<< "$deps"
            queue+=("${more[@]}")
        fi
    done
}

# The Minecraft EULA must be accepted by the person running the server, so it is always asked:
# a window with the link on a desktop (zenity), or a typed "yes". --accept-eula skips it.
confirm_eula() {
    if [ "$ACCEPT_EULA" = 1 ]; then say "  EULA accepted with --accept-eula."; return 0; fi
    if [ "$DRY_RUN" = 1 ]; then dry "ask you to accept the EULA ($EULA_URL) and write eula=true only if you agree"; return 1; fi
    if command -v zenity >/dev/null 2>&1 && [ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]; then
        say "  A window asks you about the Minecraft EULA."
        zenity --question --title="Minecraft EULA" --width=460 --ok-label="I agree" --cancel-label="I do not agree" \
            --text="To run a Minecraft server you must agree to Mojang's End User License Agreement (EULA).\n\nRead it first: <a href=\"$EULA_URL\">$EULA_URL</a>\n\nClicking 'I agree' writes eula=true into eula.txt in your server folder." 2>/dev/null
        return
    fi
    local answer
    say "  Read the Minecraft EULA: $EULA_URL"
    read -r -p "  Type yes if you agree to it: " answer || answer=""
    [ "$(printf '%s' "$answer" | tr '[:upper:]' '[:lower:]')" = yes ]
}

find_java_in() { find "$1" -maxdepth 4 -type f -path '*/bin/java' 2>/dev/null | head -n1; }

java_major() {
    local t
    [ -n "$1" ] && [ -x "$1" ] || { echo 0; return; }
    t="$("$1" -version 2>&1 | head -n1)"
    if [[ "$t" =~ version\ \"1\.([0-9]+) ]]; then echo "${BASH_REMATCH[1]}"
    elif [[ "$t" =~ version\ \"([0-9]+) ]]; then echo "${BASH_REMATCH[1]}"
    else echo 0; fi
}

prop_value() {   # file key default
    local v=""
    [ -f "$1" ] && v="$(grep -E "^[[:space:]]*$2[[:space:]]*=" "$1" | head -n1 | sed -E 's/^[^=]*=[[:space:]]*//; s/[[:space:]]*$//')"
    printf '%s' "${v:-$3}"
}

# "key: value" under "section:" in a YAML file (one level). Only reads the keys it is asked for:
# ports, bind addresses and on/off switches. Never the API key.
section_value() {   # file section key default
    local v=""
    [ -f "$1" ] && v="$(awk -v sec="$2" -v key="$3" '
        /^[[:space:]]*(#|$)/ { next }
        { match($0, /^[[:space:]]*/); lead = RLENGTH; line = $0; sub(/^[[:space:]]*/, "", line) }
        insec && lead <= ind { insec = 0 }
        insec && index(line, key ":") == 1 { val = substr(line, length(key) + 2); sub(/^[[:space:]]*"?/, "", val); sub(/["#[:space:]].*$/, "", val); print val; exit }
        !insec && line ~ ("^" sec ":[[:space:]]*(#.*)?$") { insec = 1; ind = lead }
    ' "$1")"
    printf '%s' "${v:-$4}"
}

# The Java process whose working folder is this server (that is how a running server is told apart
# from another program on the same port). Prints its pid.
server_pid() {
    local d c
    for d in /proc/[0-9]*; do
        c="$(cat "$d/comm" 2>/dev/null)" || continue
        [ "$c" = java ] || continue
        [ "$(readlink "$d/cwd" 2>/dev/null)" = "$1" ] && { basename "$d"; return 0; }
    done
    return 1
}

udp_listening() {
    command -v ss >/dev/null 2>&1 || return 2
    ss -lunH 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]$1\$"
}

# ---------------------------------------------------------------------------------------------
#  Kokoro on the GPU or the CPU
# ---------------------------------------------------------------------------------------------
DOCKER=(docker)   # step 4 may switch this to (sudo docker)
TOOLKIT_DOCS="https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html"

# Decides which Kokoro to run. Sets PLAN_GPU (nvidia|rocm|""), PLAN_IMAGE, PLAN_CARD, PLAN_WHY and
# PLAN_ARGS (extra docker run arguments). The NVIDIA image is CUDA-only and needs Docker to reach the
# card (NVIDIA Container Toolkit). AMD cards have an experimental ROCm image (asked, default no).
# Intel graphics are not used by Kokoro-FastAPI: they get the CPU image.
kokoro_plan() {
    PLAN_GPU=""; PLAN_IMAGE="$KOKORO_CPU_IMAGE"; PLAN_CARD=""; PLAN_WHY=""; PLAN_ARGS=()
    if [ "$FORCE_CPU" = 1 ]; then PLAN_WHY="You chose --cpu"; return; fi
    if command -v nvidia-smi >/dev/null 2>&1; then
        PLAN_CARD="$(nvidia-smi --query-gpu=name --format=csv,noheader 2>/dev/null | head -n1)"
        if [ -z "$PLAN_CARD" ]; then PLAN_WHY="nvidia-smi is installed but no NVIDIA card answered (driver problem?)"; return; fi
        # Docker reaches NVIDIA cards through the NVIDIA Container Toolkit (a runtime or CDI devices).
        if ! "${DOCKER[@]}" info --format '{{json .Runtimes}}' 2>/dev/null | grep -q nvidia &&
           ! ls /etc/cdi/nvidia*.yaml /var/run/cdi/nvidia*.yaml >/dev/null 2>&1; then
            PLAN_WHY="$PLAN_CARD found, but Docker cannot use it yet: install the NVIDIA Container Toolkit ($TOOLKIT_DOCS) and run this again"
            return
        fi
        local cap
        cap="$(nvidia-smi --query-gpu=compute_cap --format=csv,noheader 2>/dev/null | head -n1 | cut -d. -f1)"
        PLAN_GPU=nvidia; PLAN_ARGS=(--gpus all); PLAN_IMAGE="$KOKORO_GPU_IMAGE"
        # RTX 50-series (compute capability 12) needs the CUDA 12.8 build (amd64 only).
        if { [ "${cap:-0}" -ge 12 ] 2>/dev/null || [[ "$PLAN_CARD" =~ RTX\ 50[0-9][0-9] ]]; } && [ "$JAVA_ARCH" = x64 ]; then PLAN_IMAGE="$KOKORO_GPU128_IMAGE"; fi
        return
    fi
    if [ -e /dev/kfd ] && [ "$JAVA_ARCH" = x64 ]; then
        PLAN_CARD="AMD (ROCm)"
        PLAN_WHY="No NVIDIA card found. An AMD card with ROCm drivers is present; Kokoro's AMD version is experimental"
        return
    fi
    PLAN_WHY="No NVIDIA graphics card found"
}

is_gpu_container() {   # image deviceRequests
    [[ "$1" == *kokoro-fastapi-gpu* || "$1" == *kokoro-fastapi-rocm* ]] && return 0
    [ -n "$2" ] && [ "$2" != null ] && [ "$2" != "[]" ]
}

# Pull an image and start the Kokoro container from it; exit 0 when "docker run" worked.
start_kokoro() {   # image extra-args...
    local image="$1"; shift
    run_shown "${DOCKER[@]}" pull "$image" || { warn "docker pull failed."; return 1; }
    if run_shown "${DOCKER[@]}" run -d --name "$KOKORO_NAME" --restart unless-stopped \
        -p "127.0.0.1:$KOKORO_PORT:$KOKORO_PORT" "$@" "$image"; then return 0; fi
    "${DOCKER[@]}" rm -f "$KOKORO_NAME" >/dev/null 2>&1   # a half-created container would block the next try
    return 1
}

# Speak one test sentence and time it. Prints "<seconds made> <seconds of audio>".
measure_kokoro() {
    command -v curl >/dev/null 2>&1 || return 1
    local out
    out="$(curl -fsS --max-time 120 -o /dev/null -w '%{time_total} %{size_download}' -H 'Content-Type: application/json' \
        -d "{\"model\":\"kokoro\",\"input\":\"Mortals, hear me. I am awake, and I am listening.\",\"voice\":\"$KOKORO_VOICE\",\"response_format\":\"wav\"}" \
        "http://127.0.0.1:$KOKORO_PORT/v1/audio/speech")" || return 1
    awk -v t="${out% *}" -v b="${out#* }" 'BEGIN { a = (b - 44) / 48000; if (a < 0) a = 0; printf "%.1f %.1f\n", t, a }'
}

# ---------------------------------------------------------------------------------------------
#  Start
# ---------------------------------------------------------------------------------------------
say ""
say "GPTGOD easy setup (Linux)"
say "-------------------------"
say "This sets up the god's voice and checks the plugin is in place. Every download is shown"
say "with its full address and asked about first. Press Ctrl+C at any time to stop."
[ "$DRY_RUN" = 1 ] && { say ""; say "DRY RUN: nothing will be changed or downloaded; you will see what would happen."; }
[ "$CHECK" = 1 ] && { say ""; say "CHECK ONLY: nothing will be installed or changed; this only looks and reports."; }

if [ "$(id -u)" = 0 ]; then
    warn "You are running this as root. Files it creates would belong to root, and a server running"
    warn "as a normal user could not use them. Run it as the user that runs the Minecraft server."
    ask_yes_no "Continue as root anyway?" n || exit 1
fi
command -v curl >/dev/null 2>&1 || command -v wget >/dev/null 2>&1 || warn "curl or wget is needed for downloads (install one with your package manager)."

SETUP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SETUP_DIR")"

# ---- 1. Server folder -----------------------------------------------------------------------
step "1. Your Minecraft server"
IS_NEW=0
here_has_server=0; [ -f ./server.properties ] && here_has_server=1
if [ -z "$SERVER_DIR" ] && [ "$CHECK" = 0 ] && { [ "$NEW_SERVER" = 1 ] || [ "$here_has_server" = 0 ]; }; then
    if [ "$NEW_SERVER" = 1 ]; then
        IS_NEW=1
    else
        say "  Do you already have a Minecraft server folder? If not, this makes a new one for you:"
        say "  Java 25 and Paper $MC_VERSION (about 115 MB of downloads, no root, nothing system-wide)."
        ask_yes_no "Do you already have a server?" n || IS_NEW=1
    fi
fi
if [ "$IS_NEW" = 1 ]; then
    answer="$(ask_text "Where should the new server go?" "$HOME/gptgod-server")"
    answer="${answer/#\~/$HOME}"
    case "$answer" in /*) SERVER_DIR="$answer" ;; *) SERVER_DIR="$(pwd)/$answer" ;; esac
    SERVER_DIR="${SERVER_DIR%/}"
    if [ -d "$SERVER_DIR" ]; then note "That folder exists already; anything set up there is kept."
    elif [ "$DRY_RUN" = 1 ]; then dry "create $SERVER_DIR"
    else mkdir -p "$SERVER_DIR" && done_ "Created $SERVER_DIR"; fi
else
    default_server="$SERVER_DIR"
    [ -z "$default_server" ] && [ "$here_has_server" = 1 ] && default_server="$(pwd)"
    while true; do
        if [ "$YES" = 1 ]; then
            [ -z "$default_server" ] && { say "  With --yes, pass the server folder (--server <folder>) or --new-server."; exit 2; }
            answer="$default_server"
        else
            say "  Which folder is your Minecraft server in? It is the folder that has server.properties"
            say "  and a plugins folder."
            answer="$(ask_text "Server folder" "$default_server")"
        fi
        answer="${answer/#\~/$HOME}"
        if [ -n "$answer" ] && [ -d "$answer" ]; then
            SERVER_DIR="$(cd "$answer" && pwd)"
            if [ ! -f "$SERVER_DIR/server.properties" ]; then
                warn "There is no server.properties in $SERVER_DIR. (A new server makes it on first start.)"
                if ! ask_yes_no "Use this folder anyway?" n; then [ "$YES" = 1 ] && exit 2; continue; fi
            fi
            break
        fi
        warn "That folder does not exist: '$answer'"
        [ "$YES" = 1 ] && exit 2
    done
fi
PLUGINS_DIR="$SERVER_DIR/plugins"
DATA_DIR="$PLUGINS_DIR/gptgodmc"
CONFIG_FILE="$DATA_DIR/config.yml"
done_ "Server folder: $SERVER_DIR"
if [ ! -d "$PLUGINS_DIR" ] && [ "$CHECK" = 0 ]; then
    if [ "$DRY_RUN" = 1 ]; then dry "create $PLUGINS_DIR"; else mkdir -p "$PLUGINS_DIR" && done_ "Created $PLUGINS_DIR"; fi
fi
server_port="$(prop_value "$SERVER_DIR/server.properties" server-port 25565)"
SERVER_RUNNING=0; PORT_TAKEN=0
if server_pid "$SERVER_DIR" >/dev/null; then SERVER_RUNNING=1
elif port_listening "$server_port"; then PORT_TAKEN=1; fi
if [ "$PORT_TAKEN" = 1 ]; then
    warn "Port $server_port is in use by another program (often another Minecraft server on this machine),"
    warn "so this server cannot start while that one runs. Stop the other one first, or change"
    warn "server-port in server.properties (its voice, texture-pack and viewer ports would clash too)."
fi
if [ "$SERVER_RUNNING" = 1 ]; then
    warn "This server is RUNNING (port $server_port)."
    note "Plugin jars will go into plugins/update/ so the server swaps them in at its next start"
    note "(replacing a jar under a running server breaks it)."
elif [ "$PORT_TAKEN" = 0 ]; then
    note "The server is not running (nothing on port $server_port). Good."
fi

# Steps 2-8 change things; --check skips straight to the final check.
if [ "$CHECK" = 0 ]; then

# ---- Java, Paper, start script, EULA (a new server, or a folder without Paper) ---------------
SERVER_JAVA="$(find_java_in "$SERVER_DIR/java")"
if [ -z "$SERVER_JAVA" ] && command -v java >/dev/null 2>&1 && [ "$(java_major "$(command -v java)")" -ge 25 ]; then
    SERVER_JAVA="$(command -v java)"
fi
paper_jar=""
for f in "$SERVER_DIR"/*paper*.jar; do [ -f "$f" ] && { paper_jar="$f"; break; }; done
MAKE_SERVER="$IS_NEW"
if [ "$IS_NEW" = 0 ] && [ -z "$paper_jar" ] && [ "$SERVER_RUNNING" = 0 ]; then
    warn "There is no Paper server jar in this folder."
    if ask_yes_no "Set up Paper $MC_VERSION in this folder?" y; then MAKE_SERVER=1; fi
fi
if [ "$MAKE_SERVER" = 1 ]; then
    step "1b. Java 25 and Paper $MC_VERSION"
    if [ -n "$SERVER_JAVA" ]; then
        done_ "Java 25: $SERVER_JAVA"
    else
        note "Paper $MC_VERSION needs Java 25. This gets the free Temurin 25 runtime from Adoptium (Eclipse"
        note "Foundation) and keeps it inside the server folder, so nothing is installed system-wide."
        java_dir="$SERVER_DIR/java"
        if info="$(json_get "$ADOPTIUM_API" '"\t".join([j[0]["binary"]["package"][k] if k != "size" else str(j[0]["binary"]["package"][k]) for k in ("name", "link", "checksum", "size")]) if j else ""')" && [ -n "$info" ]; then
            IFS=$'\t' read -r j_name j_link j_sum j_size <<< "$info"
            if [[ "$j_link" == https://github.com/adoptium/* ]]; then
                tmp="$(mktemp -d)/$j_name"
                if verified_download "$j_link" "$tmp" "$((j_size / 1048576)) MB" sha256 "$j_sum"; then
                    mkdir -p "$java_dir" && tar -xzf "$tmp" -C "$java_dir" && rm -f "$tmp"
                    SERVER_JAVA="$(find_java_in "$java_dir")"
                    [ -n "$SERVER_JAVA" ] && done_ "Java 25 is in $java_dir"
                fi
            fi
        elif [ "$DRY_RUN" = 1 ]; then
            say "  Then download the Java 25 archive it names (github.com/adoptium, ~60 MB), check its SHA-256, unpack into $java_dir"
        else
            warn "Could not get Java 25 automatically. Install Temurin 25 (https://adoptium.net/) and run this again."
        fi
    fi
    if [ -n "$paper_jar" ]; then
        done_ "Paper is here: $(basename "$paper_jar")"
    else
        note "Paper is the Minecraft server program (from PaperMC, papermc.io)."
        if info="$(json_get "$PAPER_API" '"\t".join([str(j["id"]), j["downloads"]["server:default"]["url"], j["downloads"]["server:default"]["checksums"]["sha256"], str(j["downloads"]["server:default"]["size"])])')" && [ -n "$info" ]; then
            IFS=$'\t' read -r p_build p_url p_sum p_size <<< "$info"
            if [[ "$p_url" =~ ^https://([a-z0-9-]+\.)?papermc\.io/ ]]; then
                verified_download "$p_url" "$SERVER_DIR/paper.jar" "$((p_size / 1048576)) MB" sha256 "$p_sum" &&
                    done_ "Paper $MC_VERSION build $p_build saved as paper.jar"
            else
                warn "Unexpected download address from PaperMC ($p_url); not downloading."
            fi
        elif [ "$DRY_RUN" = 1 ]; then
            say "  Then download the Paper jar it names (fill-data.papermc.io, ~55 MB), check its SHA-256, save as $SERVER_DIR/paper.jar"
        else
            warn "Could not get Paper. Download it from https://papermc.io/downloads/paper into $SERVER_DIR and run this again."
        fi
    fi
    starter="$SERVER_DIR/start.sh"
    if [ -f "$starter" ]; then
        done_ "start.sh is here (kept as it is)"
    else
        total_gb=$(( $(awk '/^MemTotal:/ {print $2}' /proc/meminfo 2>/dev/null || echo 8388608) / 1048576 ))
        if [ "$total_gb" -ge 16 ]; then suggest_ram=6G; elif [ "$total_gb" -ge 8 ]; then suggest_ram=4G; else suggest_ram=2G; fi
        note "This machine has about $total_gb GB of memory. 4G is enough for a few friends; 6G with voice."
        ram=""
        while ! [[ "$ram" =~ ^[0-9]+[GM]$ ]]; do ram="$(ask_text "Memory for the server (like 4G)" "$suggest_ram" | tr '[:lower:]' '[:upper:]')"; done
        java_cmd="java"
        if [ -n "$SERVER_JAVA" ] && [[ "$SERVER_JAVA" == "$SERVER_DIR"/* ]]; then java_cmd="./${SERVER_JAVA#"$SERVER_DIR"/}"
        elif [ -n "$SERVER_JAVA" ]; then java_cmd="$SERVER_JAVA"
        elif [ "$DRY_RUN" = 1 ]; then java_cmd="./java/<jre folder>/bin/java"; fi
        script_text="#!/bin/sh
# Starts the Minecraft server (written by GPTGOD setup). Stop it by typing: stop
cd \"\$(dirname \"\$0\")\" || exit 1
exec \"$java_cmd\" -Xms$ram -Xmx$ram -jar paper.jar --nogui"
        if [ "$DRY_RUN" = 1 ]; then dry "write $starter :"; printf '%s\n' "$script_text" | sed 's/^/      /'
        else printf '%s\n' "$script_text" > "$starter" && chmod +x "$starter" && done_ "Wrote start.sh (run ./start.sh to start the server)"; fi
    fi
    report "Paper server" "$DONE_STATE" "$SERVER_DIR (start.sh)"
fi
eula_file="$SERVER_DIR/eula.txt"
if [ -f "$SERVER_DIR/paper.jar" ] || [ "$MAKE_SERVER" = 1 ] || [ -n "$paper_jar" ]; then
    if [ "$(prop_value "$eula_file" eula "")" != true ]; then
        step "1c. The Minecraft EULA"
        if confirm_eula; then
            printf '%s\n' "#By changing the setting below to TRUE you are indicating your agreement to our EULA ($EULA_URL)." \
                "#Accepted by the server owner in GPTGOD setup on $(date '+%Y-%m-%d %H:%M')" "eula=true" > "$eula_file"
            done_ "EULA accepted (eula.txt)"
            report "EULA" "READY" "accepted"
        elif [ "$DRY_RUN" = 1 ]; then
            report "EULA" "WOULD DO" "ask you"
        else
            warn "Not accepted. The server will not start until eula.txt says eula=true."
            report "EULA" "NOT READY" "not accepted"
        fi
    fi
fi

# ---- 2. GPTGOD plugin jar -------------------------------------------------------------------
step "2. The GPTGOD plugin"
if [ -z "$PLUGIN_JAR" ]; then
    # The newest gptgodmc-*.jar next to this folder (not -sources/-javadoc/-plain jars).
    for f in "$SETUP_DIR"/gptgodmc-*.jar "$REPO_DIR"/gptgodmc-*.jar "$REPO_DIR"/build/libs/gptgodmc-*.jar; do
        [ -f "$f" ] || continue
        case "$f" in *-sources.jar|*-javadoc.jar|*-plain.jar) continue ;; esac
        if [ -z "$PLUGIN_JAR" ] || [ "$f" -nt "$PLUGIN_JAR" ]; then PLUGIN_JAR="$f"; fi
    done
fi
existing_god=()
for f in "$PLUGINS_DIR"/gptgodmc*.jar; do [ -f "$f" ] && existing_god+=("$f"); done
if [ -z "$PLUGIN_JAR" ] || [ ! -f "$PLUGIN_JAR" ]; then
    if [ ${#existing_god[@]} -gt 0 ]; then
        done_ "Already installed: $(basename "${existing_god[0]}") (no newer jar found next to this script)"
        report "GPTGOD plugin" "READY" "$(basename "${existing_god[0]}")"
    else
        warn "No gptgodmc-*.jar found next to this script."
        note "Put the jar you downloaded into the setup folder (or build it with ./gradlew shadowJar), then run this again."
        report "GPTGOD plugin" "NOT READY" "jar not found; copy gptgodmc-<version>.jar into plugins/"
    fi
else
    jar_name="$(basename "$PLUGIN_JAR")"
    jar_hash="$(sha256_of "$PLUGIN_JAR")"
    say "  Jar to install: $PLUGIN_JAR"
    note "SHA-256: $jar_hash"
    same=""
    for f in "${existing_god[@]+"${existing_god[@]}"}"; do
        [ "$(sha256_of "$f")" = "$jar_hash" ] && same="$f"
    done
    if [ -n "$same" ]; then
        done_ "This exact jar is already installed ($(basename "$same"))."
        report "GPTGOD plugin" "READY" "$(basename "$same")"
    elif ask_yes_no "Install $jar_name into the server's plugins folder?" y; then
        if [ "$SERVER_RUNNING" = 1 ] && [ ${#existing_god[@]} -gt 0 ]; then
            # Paper replaces plugins/<name>.jar with plugins/update/<name>.jar on the next start.
            target="$PLUGINS_DIR/update/$(basename "${existing_god[0]}")"
            copy_shown "$PLUGIN_JAR" "$target"
            did "Placed at $target - restart the server to load it."
            report "GPTGOD plugin" "$DONE_STATE" "loads at the next server restart"
        else
            for f in "${existing_god[@]+"${existing_god[@]}"}"; do backup_file "$f"; done
            copy_shown "$PLUGIN_JAR" "$PLUGINS_DIR/$jar_name"
            did "Installed plugins/$jar_name"
            report "GPTGOD plugin" "$DONE_STATE" "$jar_name"
        fi
    else
        report "GPTGOD plugin" "SKIPPED" "you chose not to install it"
    fi
fi

# ---- 3. Simple Voice Chat --------------------------------------------------------------------
step "3. Simple Voice Chat (lets players hear and talk to the god)"
note "This free server plugin carries voice in game. The god works without it, in text only."
existing_svc=""
for f in "$PLUGINS_DIR"/voicechat*.jar; do [ -f "$f" ] && { existing_svc="$f"; break; }; done
[ -n "$existing_svc" ] && note "Found: $(basename "$existing_svc")"
if [ -n "$existing_svc" ]; then q="Check Modrinth for a newer Simple Voice Chat?"; d=n; else q="Download Simple Voice Chat from Modrinth?"; d=y; fi
svc_state_if_none() { if [ -n "$existing_svc" ]; then echo READY; else echo "NOT READY"; fi; }
if ! ask_yes_no "$q" "$d"; then
    if [ -n "$existing_svc" ]; then report "Simple Voice Chat" "READY" "$(basename "$existing_svc")"
    else report "Simple Voice Chat" "SKIPPED" "no voice in game until it is installed"; fi
else
    mc_version="26.3"
    paper_jar=""
    for f in "$SERVER_DIR"/paper-*.jar; do [ -f "$f" ] && { paper_jar="$f"; break; }; done
    if [ -n "$paper_jar" ]; then
        v="$(basename "$paper_jar" | sed -nE 's/^paper-([0-9]+\.[0-9]+(\.[0-9]+)?).*/\1/p')"
        [ -n "$v" ] && mc_version="$v"
    fi
    mc_version="$(ask_text "Minecraft version of your server" "$mc_version")"
    api_url="$MODRINTH_API?loaders=%5B%22paper%22%5D&game_versions=%5B%22$mc_version%22%5D"
    say ""
    say "  Ask Modrinth for the newest version: $api_url"
    if [ "$DRY_RUN" = 1 ]; then
        dry "ask that address (no download in a dry run), then download the jar it names into plugins/"
        report "Simple Voice Chat" "WOULD DO" "ask Modrinth, then download"
    elif ! command -v curl >/dev/null 2>&1; then
        warn "curl is needed to ask Modrinth. Get the plugin by hand: https://modrinth.com/plugin/simple-voice-chat"
        report "Simple Voice Chat" "$(svc_state_if_none)" "install curl or download by hand"
    elif ! ask_yes_no "Contact Modrinth?" y; then
        report "Simple Voice Chat" "SKIPPED" "you chose not to contact Modrinth"
    else
        json="$(curl -fsS --proto '=https' --tlsv1.2 -A gptgodmc-setup --max-time 30 "$api_url")" || json=""
        # Pick the newest version's primary file: filename, url, sha512, size, version name.
        info=""
        if [ -n "$json" ] && command -v python3 >/dev/null 2>&1; then
            info="$(printf '%s' "$json" | python3 -I -c '
import json, sys
v = json.load(sys.stdin)
if v:
    files = [f for f in v[0]["files"] if f.get("primary")] or v[0]["files"]
    f = files[0]
    print("\t".join([f["filename"], f["url"], f["hashes"]["sha512"], str(f["size"]), v[0]["name"]]))
')"
        elif [ -n "$json" ] && command -v jq >/dev/null 2>&1; then
            info="$(printf '%s' "$json" | jq -r 'if length > 0 then .[0] as $v | ([$v.files[] | select(.primary)] + $v.files)[0] | [.filename, .url, .hashes.sha512, (.size|tostring), $v.name] | @tsv else empty end')"
        elif [ -n "$json" ]; then
            warn "python3 or jq is needed to read Modrinth's answer."
        fi
        if [ -z "$info" ]; then
            warn "No Simple Voice Chat build for Paper $mc_version was found (or Modrinth did not answer)."
            note "Get it by hand from https://modrinth.com/plugin/simple-voice-chat"
            report "Simple Voice Chat" "$(svc_state_if_none)" "no build found for $mc_version"
        else
            IFS=$'\t' read -r svc_file svc_url svc_sha512 svc_size svc_name <<< "$info"
            say "  Newest: $svc_name ($svc_file)"
            case "$svc_file" in */*|..*) svc_file="" ;; esac   # never accept a path as a file name
            if [ -n "$existing_svc" ] && [ "$(basename "$existing_svc")" = "$svc_file" ]; then
                done_ "You already have this version."
                report "Simple Voice Chat" "READY" "$svc_file"
            elif [ -z "$svc_file" ] || [[ "$svc_url" != https://cdn.modrinth.com/* ]]; then
                warn "Unexpected answer from Modrinth ($svc_url); not downloading."
                report "Simple Voice Chat" "$(svc_state_if_none)" "unexpected download address"
            else
                tmp="$(mktemp -d)/$svc_file"
                if download "$svc_url" "$tmp" "$((svc_size / 1048576)) MB"; then
                    if [ "$(sha512sum "$tmp" | cut -d' ' -f1)" != "$svc_sha512" ]; then
                        rm -f "$tmp"
                        warn "The file's SHA-512 does not match what Modrinth lists. Deleted it."
                        report "Simple Voice Chat" "$(svc_state_if_none)" "checksum mismatch"
                    else
                        done_ "SHA-512 matches Modrinth's."
                        if [ "$SERVER_RUNNING" = 1 ] && [ -n "$existing_svc" ]; then
                            target="$PLUGINS_DIR/update/$(basename "$existing_svc")"
                        else
                            [ -n "$existing_svc" ] && backup_file "$existing_svc"
                            target="$PLUGINS_DIR/$svc_file"
                        fi
                        mkdir -p "$(dirname "$target")" && mv -f "$tmp" "$target"
                        done_ "Installed $target"
                        report "Simple Voice Chat" "READY" "$svc_file$([ "$SERVER_RUNNING" = 1 ] && echo ' (after restart)')"
                    fi
                else
                    report "Simple Voice Chat" "$(svc_state_if_none)" "not downloaded"
                fi
            fi
        fi
    fi
fi
note "Players must install the Simple Voice Chat MOD in their own game: $VOICECHAT_MOD"
note "Voice uses UDP port 24454; open it in your firewall if players connect from outside."

# ---- 4. Kokoro voice server (Docker) ---------------------------------------------------------
step "4. The god's voice (Kokoro, runs in Docker)"
note "Kokoro makes the god's speech. It runs in a Docker container on this machine, only reachable"
note "from this machine (127.0.0.1:$KOKORO_PORT). Without it the god speaks with the built-in Piper voice."
KOKORO_OK=0
KOKORO_REPORTED=0
DOCKER=(docker)
if ! ask_yes_no "Set up the Kokoro voice server?" y; then
    report "Kokoro voice" "SKIPPED" "the god will use the Piper backup voice"; KOKORO_REPORTED=1
elif ! command -v docker >/dev/null 2>&1; then
    warn "Docker is not installed."
    say "  Install Docker Engine for your distribution, then run this setup again:"
    say "  Official guide: $DOCKER_DOCS"
    note "(Installing Docker needs root/sudo; this script never installs it for you.)"
    report "Kokoro voice" "NOT READY" "install Docker, then run setup again"; KOKORO_REPORTED=1
else
    docker_ok=0
    if docker info >/dev/null 2>&1; then
        docker_ok=1
    elif docker info 2>&1 | grep -qi "permission denied"; then
        warn "Docker is installed, but your user may not use it yet."
        say "  Fix it once (needs sudo, then log out and back in):  sudo usermod -aG docker $USER"
        say "  Or let this run use 'sudo docker' for the Docker commands only. sudo is needed because"
        say "  the Docker service belongs to root; nothing else in this script uses sudo."
        if ask_yes_no "Use 'sudo docker' for this run?" n; then
            DOCKER=(sudo docker)
            if [ "$DRY_RUN" = 1 ] || sudo docker info >/dev/null 2>&1; then docker_ok=1; fi
        fi
    else
        warn "Docker is installed but not running. Start it with:  sudo systemctl start docker"
    fi
    if [ "$docker_ok" = 0 ]; then
        report "Kokoro voice" "NOT READY" "Docker is not usable yet; run setup again after fixing it"; KOKORO_REPORTED=1
    else
        done_ "Docker is usable."
        cores="$(nproc 2>/dev/null || echo 2)"
        if [ "$cores" -ge 4 ]; then suggest=$((cores - 2)); [ "$suggest" -gt 6 ] && suggest=6; else suggest=0; fi
        ask_cpus() {
            [ "$suggest" -gt 0 ] || return 0
            # stdout is the answer, so the explanation goes to stderr (still shown on screen)
            note "On the CPU, Kokoro can use every core while it speaks, which can make Minecraft (and the voice) stutter." >&2
            local n
            n="$(ask_text "Limit it to how many CPU cores? (0 = no limit; you have $cores)" "$suggest")"
            if [[ "$n" =~ ^[0-9]+(\.[0-9]+)?$ ]] && [ "$n" != 0 ]; then printf '%s' "$n"; fi
        }
        kokoro_plan
        if [ "$PLAN_GPU" = nvidia ]; then
            done_ "$PLAN_CARD found and Docker can reach it: using the GPU version (fast)."
        elif [ "$PLAN_CARD" = "AMD (ROCm)" ]; then
            note "$PLAN_WHY (needs ROCm drivers on this Linux machine)."
            if ask_yes_no "Try the experimental AMD (ROCm) version? (No = the CPU version)" n; then
                PLAN_GPU=rocm; PLAN_IMAGE="$KOKORO_ROCM_IMAGE"; PLAN_ARGS=(--device=/dev/kfd --device=/dev/dri)
            else
                note "Using the CPU version (slower, fine for a few players)."
            fi
        else
            note "$PLAN_WHY: using the CPU version (slower, fine for a few players)."
        fi
        if "${DOCKER[@]}" container inspect "$KOKORO_NAME" >/dev/null 2>&1; then
            IFS='|' read -r k_running k_restart k_nanocpus k_image k_dev <<< \
                "$("${DOCKER[@]}" container inspect "$KOKORO_NAME" --format '{{.State.Running}}|{{.HostConfig.RestartPolicy.Name}}|{{.HostConfig.NanoCpus}}|{{.Config.Image}}|{{json .HostConfig.DeviceRequests}}')"
            if is_gpu_container "$k_image" "$k_dev"; then k_mode=GPU; else k_mode=CPU; fi
            done_ "Container '$KOKORO_NAME' already exists (image $k_image, on the $k_mode)."
            if [ "${k_nanocpus:-0}" = 0 ]; then cpu_now="none"; else cpu_now="$(awk "BEGIN{print $k_nanocpus/1e9}")"; fi
            note "Running: $k_running; restart policy: $k_restart; CPU limit: $cpu_now"
            switched=0
            if [ "$k_mode" = CPU ] && [ -n "$PLAN_GPU" ]; then
                say "  Your Kokoro runs on the CPU, but this machine's graphics card can run it much faster."
                note "Switching downloads the GPU version (several GB) and replaces the '$KOKORO_NAME' container."
                note "Nothing is lost: the voices are built into the image. If the GPU version fails, the CPU one comes back."
                # Never replaced without a person saying yes (--yes keeps the existing container).
                if [ "$YES" = 1 ]; then replace_default=n; else replace_default=y; fi
                if ask_yes_no "Replace it with the GPU version?" "$replace_default"; then
                    if run_shown "${DOCKER[@]}" pull "$PLAN_IMAGE"; then
                        run_shown "${DOCKER[@]}" rm -f "$KOKORO_NAME"
                        if run_shown "${DOCKER[@]}" run -d --name "$KOKORO_NAME" --restart unless-stopped \
                            -p "127.0.0.1:$KOKORO_PORT:$KOKORO_PORT" "${PLAN_ARGS[@]}" "$PLAN_IMAGE"; then
                            switched=1; did "Kokoro now runs on the GPU."
                        else
                            warn "The GPU version could not start. Putting the CPU version back."
                            "${DOCKER[@]}" rm -f "$KOKORO_NAME" >/dev/null 2>&1
                            back=(); [ "$cpu_now" != none ] && back=(--cpus "$cpu_now")
                            run_shown "${DOCKER[@]}" run -d --name "$KOKORO_NAME" --restart unless-stopped \
                                -p "127.0.0.1:$KOKORO_PORT:$KOKORO_PORT" "${back[@]+"${back[@]}"}" "$k_image"
                        fi
                    else
                        warn "docker pull failed; keeping the CPU version."
                    fi
                fi
            fi
            if [ "$switched" = 0 ]; then
                if [ "$k_running" != true ] && ask_yes_no "Start it?" y; then run_shown "${DOCKER[@]}" start "$KOKORO_NAME"; fi
                update_args=()
                if [ "$k_restart" != unless-stopped ] && ask_yes_no "Make it start by itself whenever Docker starts?" y; then
                    update_args+=(--restart unless-stopped)
                fi
                if [ "$k_mode" = CPU ] && [ "$suggest" -gt 0 ] && [ "$cpu_now" = none ]; then
                    n="$(ask_cpus)"; [ -n "$n" ] && update_args+=(--cpus "$n")
                fi
                if [ ${#update_args[@]} -gt 0 ]; then
                    run_shown "${DOCKER[@]}" update "${update_args[@]}" "$KOKORO_NAME" && did "Updated $KOKORO_NAME (no download, no restart needed)."
                fi
            fi
        else
            extra=()
            if [ -n "$PLAN_GPU" ]; then extra=("${PLAN_ARGS[@]}")
            else n="$(ask_cpus)"; [ -n "$n" ] && extra=(--cpus "$n"); fi
            if port_listening "$KOKORO_PORT"; then
                warn "Port $KOKORO_PORT is already used by another program, so Kokoro cannot start there."
                report "Kokoro voice" "NOT READY" "port $KOKORO_PORT is in use by another program"; KOKORO_REPORTED=1
            else
                say ""
                say "  Image: $PLAN_IMAGE"
                say "  From:  https://${PLAN_IMAGE%%:*}  (Kokoro-FastAPI, github.com/remsky/Kokoro-FastAPI)"
                if [ -n "$PLAN_GPU" ]; then gb="4 to 6"; else gb=1.5; fi
                note "Docker downloads it once (about $gb GB) and keeps it."
                if ask_yes_no "Download and start the Kokoro voice server?" y; then
                    if start_kokoro "$PLAN_IMAGE" "${extra[@]+"${extra[@]}"}"; then
                        did "Started $KOKORO_NAME."
                    elif [ -n "$PLAN_GPU" ]; then
                        warn "The GPU version could not start (Docker could not reach the card). Using the CPU version instead (slower)."
                        n="$(ask_cpus)"; extra=(); [ -n "$n" ] && extra=(--cpus "$n")
                        start_kokoro "$KOKORO_CPU_IMAGE" "${extra[@]+"${extra[@]}"}" && did "Started $KOKORO_NAME (CPU)."
                    fi
                fi
            fi
        fi
        # Wait for it to answer (it loads its voice model for up to a minute after starting).
        if [ "$DRY_RUN" = 1 ]; then
            kokoro_answers && KOKORO_OK=1
        else
            say "  Checking the voice server..."
            for _ in $(seq 1 30); do
                if kokoro_answers; then KOKORO_OK=1; break; fi
                sleep 3
            done
        fi
    fi
fi

# ---- 5. Whisper (hearing) --------------------------------------------------------------------
step "5. The god's hearing (Whisper speech recognition)"
note "The plugin downloads this model by itself the first time someone speaks. Getting it now"
note "means the first words are understood at once, and it works on servers without internet."
config_model="$(config_value whisper-model base.en)"
whisper_size() {
    case "$1" in
        tiny.en) echo "75 MB, fastest, English only" ;;
        base.en) echo "142 MB, the plugin's default, English only" ;;
        small.en) echo "466 MB, understands better, slower, English only" ;;
        base) echo "142 MB, many languages" ;;
        small) echo "466 MB, many languages, better" ;;
    esac
}
if ask_yes_no "Download the speech-recognition model now?" y; then
    say "  Sizes:"
    for m in $WHISPER_MODELS; do printf '    %-9s %s\n' "$m" "$(whisper_size "$m")"; done
    model=""
    while [ -z "$(whisper_size "$model")" ]; do
        model="$(ask_text "Which one?" "$config_model")"
        if [ -z "$(whisper_size "$model")" ]; then warn "Pick one of: $WHISPER_MODELS"; [ "$YES" = 1 ] && model=base.en; fi
    done
    target="$DATA_DIR/whisper/ggml-$model.bin"
    if [ -f "$target" ] && [ "$(wc -c < "$target")" -gt 1048576 ]; then
        done_ "Already downloaded: $target"
        report "Whisper ($model)" "READY" "already downloaded"
    elif download "$WHISPER_URL_BASE$model.bin" "$target" "$(whisper_size "$model" | cut -d, -f1)"; then
        report "Whisper ($model)" "READY" "downloaded"
    elif [ "$DRY_RUN" = 1 ]; then
        report "Whisper ($model)" "WOULD DO" "download $model"
    else
        report "Whisper ($model)" "LATER" "the plugin downloads it on first use"
    fi
    if [ "$model" != "$config_model" ]; then
        warn "Your config uses '$config_model'. To use '$model', set voice.transcription.whisper-model: $model"
        note "in plugins/gptgodmc/config.yml and run /god reload (this script never edits your config)."
    fi
else
    report "Whisper" "LATER" "the plugin downloads it on first use"
fi

# ---- 6. Piper (backup voice) ----------------------------------------------------------------
step "6. Backup voice (Piper)"
note "If Kokoro is not running, the god speaks with Piper. The plugin installs Piper by itself the"
note "first time it is needed. Pre-installing only helps on a server without internet."
piper_dir="$DATA_DIR/piper"
find_piper() { find "$piper_dir" -maxdepth 3 -type f -name piper 2>/dev/null | head -n1; }
piper_voice="$(config_value piper-voice en_US-ryan-medium)"
case "$(uname -m)" in
    x86_64|amd64) piper_asset="piper_linux_x86_64.tar.gz" ;;
    aarch64|arm64) piper_asset="piper_linux_aarch64.tar.gz" ;;
    armv7l|armv7*) piper_asset="piper_linux_armv7l.tar.gz" ;;
    *) piper_asset="" ;;
esac
if [ -n "$(find_piper)" ]; then
    done_ "Piper is already installed: $(find_piper)"
    report "Piper backup voice" "READY" "installed"
elif [ -z "$piper_asset" ]; then
    note "No Piper build for this processor ($(uname -m)); skipping."
    report "Piper backup voice" "NOT READY" "no build for $(uname -m)"
elif ! [[ "$piper_voice" =~ ^[a-z]{2}(_[A-Z]{2})?-.+-[a-z_]+$ ]]; then
    note "Your config uses a custom Piper voice ($piper_voice); skipping."
    report "Piper backup voice" "LATER" "custom voice in config"
elif ask_yes_no "Pre-install Piper now?" n; then
    archive="$(mktemp -d)/$piper_asset"
    if download "$PIPER_RELEASE$piper_asset" "$archive" "25 MB"; then
        mkdir -p "$piper_dir" && tar -xzf "$archive" -C "$piper_dir" && rm -f "$archive" && done_ "Unpacked Piper into $piper_dir"
    fi
    # Same address the plugin builds (Synthesizer.voiceUrl): <lang family>/<lang>/<name>/<quality>/<voice>.onnx
    lang="${piper_voice%%-*}"
    quality="${piper_voice##*-}"
    name="${piper_voice#*-}"; name="${name%-*}"
    voice_url="$PIPER_VOICES_BASE${lang%%_*}/$lang/$name/$quality/$piper_voice.onnx"
    voice_file="$piper_dir/voices/$piper_voice.onnx"
    if [ ! -f "$voice_file" ]; then
        download "$voice_url.json" "$voice_file.json" "5 KB"
        download "$voice_url" "$voice_file" "60 MB"
    fi
    if [ -n "$(find_piper)" ] && [ -f "$voice_file" ] && [ -f "$voice_file.json" ]; then
        report "Piper backup voice" "READY" "pre-installed ($piper_voice)"
    elif [ "$DRY_RUN" = 1 ]; then
        report "Piper backup voice" "WOULD DO" "pre-install $piper_voice"
    else
        report "Piper backup voice" "LATER" "not complete; the plugin installs it when first needed"
    fi
else
    report "Piper backup voice" "LATER" "the plugin installs it when first needed"
fi

# ---- 7. Mods for players (Fabric) -----------------------------------------------------------
step "7. Mods for players (Fabric)"
note "Players on Minecraft Java add Fabric and a few mods to hear the god. This puts them in one folder"
note "and one zip you can send to friends: $SERVER_DIR/for-players"
if ask_yes_no "Get the player mods ready?" y; then
    fp="$SERVER_DIR/for-players"
    fp_mods="$fp/mods"
    installer_file="$fp/fabric-installer.jar"
    loader_ver="$(json_get "$FABRIC_LOADERS" '[x["loader"]["version"] for x in j if x["loader"]["stable"]][:1]')" || loader_ver=""
    [ -n "$loader_ver" ] && note "Fabric Loader for $MC_VERSION: $loader_ver"
    if [ -f "$installer_file" ]; then
        done_ "Fabric installer already here"
    elif inst_url="$(json_get "$FABRIC_INSTALLERS" '[x["url"] for x in j if x["stable"]][:1]')" && [[ "$inst_url" == https://maven.fabricmc.net/* ]]; then
        note "Lookup: $inst_url.sha256"
        inst_sha="$(curl -fsS --proto '=https' --tlsv1.2 -A gptgodmc-setup --max-time 30 "$inst_url.sha256" | tr -d '[:space:]')" || inst_sha=""
        verified_download "$inst_url" "$installer_file" "1 MB" sha256 "$inst_sha"
    elif [ "$DRY_RUN" = 1 ]; then
        say "  Then download the Fabric installer it names (maven.fabricmc.net, ~1 MB) and check its SHA-256"
    fi
    say "  Mods: Fabric API (needed by the others); Simple Voice Chat (hear the god); Mod Menu (a 'Mods' button to check what loaded)"
    # shellcheck disable=SC2086  # PLAYER_MODS is a fixed list of project names
    mod_lines="$(resolve_modrinth fabric "$MC_VERSION" $PLAYER_MODS)"
    [ "$DRY_RUN" = 1 ] && say "  Then download each mod and its required mods from cdn.modrinth.com into $fp_mods, checking each SHA-512"
    wanted=" "
    while IFS=$'\t' read -r m_file m_url m_sha m_size m_name; do
        [ -z "$m_file" ] && continue
        wanted="$wanted$m_file "
        if [ -f "$fp_mods/$m_file" ]; then done_ "Already here: $m_file"; continue; fi
        say "  $m_name"
        verified_download "$m_url" "$fp_mods/$m_file" "$((m_size / 1024)) KB" sha512 "$m_sha"
    done <<< "$mod_lines"
    # The optional GPTGOD client mod, only a finished build (the source tree may hold a stub).
    client_mod=""
    for f in "$SETUP_DIR"/gptgod-client-*.jar "$REPO_DIR"/client-mod/build/libs/gptgod-client-*.jar; do
        [ -f "$f" ] || continue
        case "$f" in *-sources.jar|*-dev.jar) continue ;; esac
        [ "$(wc -c < "$f")" -gt 20480 ] || continue
        if [ -z "$client_mod" ] || [ "$f" -nt "$client_mod" ]; then client_mod="$f"; fi
    done
    if [ -n "$client_mod" ]; then
        wanted="$wanted$(basename "$client_mod") "
        copy_shown "$client_mod" "$fp_mods/$(basename "$client_mod")"
        did "Added the GPTGOD client mod ($(basename "$client_mod")): glowing effects and a god HUD"
    else
        note "The GPTGOD client mod (optional extra effects) is not included: no finished build was found."
    fi
    # Older versions this script put in for-players/mods go to for-players/old (never deleted).
    if [ "$DRY_RUN" = 0 ] && [ -d "$fp_mods" ] && [ "$wanted" != " " ]; then
        for f in "$fp_mods"/*.jar; do
            [ -f "$f" ] || continue
            case "$wanted" in *" $(basename "$f") "*) ;; *) mkdir -p "$fp/old" && mv -f "$f" "$fp/old/" ;; esac
        done
    fi
    if [ "$DRY_RUN" = 1 ]; then
        dry "write $fp/HOW-TO-JOIN.txt and zip everything into $fp/GPTGOD-player-mods.zip"
        report "Player mods" "WOULD DO" "$fp"
    elif [ -d "$fp_mods" ]; then
        cat > "$fp/HOW-TO-JOIN.txt" <<EOF
How to join the GPTGOD server with voice (Minecraft Java $MC_VERSION)

1. Install Fabric: double-click fabric-installer.jar (it needs Java; if it does not open, get it
   from https://adoptium.net/). Choose Client, Minecraft $MC_VERSION, press Install.
2. Open your Minecraft folder: on Windows press Windows+R, type %appdata%\\.minecraft, Enter;
   on Mac ~/Library/Application Support/minecraft; on Linux ~/.minecraft.
   Open (or make) the folder 'mods' and copy every file from this zip's mods folder into it.
3. Open the Minecraft Launcher, pick the profile 'fabric-loader-$MC_VERSION', press Play.
4. On the title screen press 'Mods': Simple Voice Chat must be in the list.
5. Multiplayer > Add Server > the address the host gave you. Accept the texture pack when asked
   (it is the god's appearance). Press V in game for voice chat settings.

Without these mods you can still join and play; you just will not hear the god's voice.
Mods from: Fabric (fabricmc.net) and Modrinth (modrinth.com).
EOF
        zip_out="$fp/GPTGOD-player-mods.zip"
        rm -f "$zip_out"
        zip_items=(HOW-TO-JOIN.txt mods); [ -f "$installer_file" ] && zip_items+=(fabric-installer.jar)
        if command -v zip >/dev/null 2>&1; then (cd "$fp" && zip -qr "$zip_out" "${zip_items[@]}")
        elif command -v python3 >/dev/null 2>&1; then (cd "$fp" && python3 -I -m zipfile -c "$zip_out" "${zip_items[@]}")
        else warn "zip or python3 is needed to make the zip; send the for-players folder instead."; fi
        [ -f "$zip_out" ] && done_ "Send friends this zip: $zip_out"
        report "Player mods" "READY" "for-players/GPTGOD-player-mods.zip"
    else
        report "Player mods" "NOT READY" "nothing downloaded"
    fi

    # The same mods for Minecraft on this computer.
    mc_dir="$HOME/.minecraft"
    if [ ! -d "$mc_dir" ]; then
        note "Minecraft Java is not installed for this user (no ~/.minecraft), so nothing is installed here."
    elif ask_yes_no "Also install Fabric and these mods into Minecraft on this computer?" y; then
        tool_java="${SERVER_JAVA:-$(command -v java || true)}"
        if [ -z "$tool_java" ] && [ "$DRY_RUN" = 0 ]; then
            warn "Java is needed to run the Fabric installer; skipped."
        else
            inst_args=(-jar "$installer_file" client -dir "$mc_dir" -mcversion "$MC_VERSION")
            [ -n "$loader_ver" ] && inst_args+=(-loader "$loader_ver")
            [ -f "$mc_dir/launcher_profiles.json" ] || inst_args+=(-noprofile)
            if [ "$DRY_RUN" = 1 ] || [ -f "$installer_file" ]; then run_shown "${tool_java:-java}" "${inst_args[@]}"; fi
            mc_mods="$mc_dir/mods"
            backup="$mc_dir/mods-backup-gptgod"
            for jar in "$fp_mods"/*.jar; do
                [ -f "$jar" ] || continue
                name="$(basename "$jar")"
                [ -f "$mc_mods/$name" ] && continue
                # An older version of the same mod would clash: move it aside first.
                stem="$(printf '%s' "$name" | sed -E 's/[-_+]?[0-9].*$//')"
                for old in "$mc_mods/$stem"*.jar; do
                    [ -f "$old" ] || continue
                    [ "$(basename "$old" | sed -E 's/[-_+]?[0-9].*$//')" = "$stem" ] || continue
                    if [ "$DRY_RUN" = 1 ]; then dry "move $(basename "$old") to $backup (older version)"
                    else mkdir -p "$backup" && mv -f "$old" "$backup/" && note "Moved older $(basename "$old") to $backup"; fi
                done
                copy_shown "$jar" "$mc_mods/$name"
            done
            [ "$DRY_RUN" = 1 ] && dry "copy the mods into $mc_mods"
            did "Installed. In the Minecraft Launcher pick 'fabric-loader-$MC_VERSION' and press Play."
            report "Mods on this PC" "$DONE_STATE" "launcher profile fabric-loader-$MC_VERSION"
        fi
    fi
else
    report "Player mods" "SKIPPED" "players can still join, but will not hear the god"
fi

# ---- 8. Start the server, API key -----------------------------------------------------------
step "8. Start the server and give the god its mind (API key)"
say "  The god thinks with Claude, which needs an Anthropic API key: https://console.anthropic.com/ > API Keys."
say "  (Add a little credit and set a monthly limit there too.) This setup never asks for your key"
say "  and never writes it anywhere: you type it into the SERVER console yourself, as shown below."
note "Other AI providers: see the 'Choosing the AI' section of setup/TUTORIAL.md."
[ -n "${ANTHROPIC_API_KEY:-}" ] && note "ANTHROPIC_API_KEY is set in this shell (its value was not read or shown)."
starter=""
for f in start.sh run.sh launch.sh; do [ -f "$SERVER_DIR/$f" ] && { starter="$SERVER_DIR/$f"; break; }; done
[ "$DRY_RUN" = 1 ] && [ -z "$starter" ] && [ "${MAKE_SERVER:-0}" = 1 ] && starter="$SERVER_DIR/start.sh"
started=0
console_hint="the server console"
if server_pid "$SERVER_DIR" >/dev/null; then
    note "The server is already running."; started=1
elif port_listening "$server_port" && [ "$DRY_RUN" = 0 ]; then
    warn "Not starting it: port $server_port is used by another program (see step 1)."
elif [ -z "$starter" ]; then
    note "Start your server the way you usually do."
elif [ "$(prop_value "$eula_file" eula "")" != true ] && [ "$DRY_RUN" = 0 ]; then
    warn "The EULA is not accepted, so the server cannot start yet."
elif command -v tmux >/dev/null 2>&1 || command -v screen >/dev/null 2>&1; then
    if ask_yes_no "Start the server now (in the background, inside tmux/screen)?" y; then
        if command -v tmux >/dev/null 2>&1; then
            run_shown tmux new-session -d -s gptgod-server -c "$SERVER_DIR" "$starter"
            console_hint="the server console (open it with: tmux attach -t gptgod-server ; leave it with Ctrl+B then D)"
        else
            run_shown screen -dmS gptgod-server sh -c "cd '$SERVER_DIR' && '$starter'"
            console_hint="the server console (open it with: screen -r gptgod-server ; leave it with Ctrl+A then D)"
        fi
        if [ "$DRY_RUN" = 0 ]; then
            say "  Waiting for the server to finish starting (the first start downloads libraries: 1-3 minutes)..."
            t0="$(date +%s)"
            for i in $(seq 1 100); do
                sleep 3
                log="$SERVER_DIR/logs/latest.log"
                if [ -f "$log" ] && [ "$(stat -c %Y "$log" 2>/dev/null || echo 0)" -ge "$t0" ]; then
                    grep -q "Done (" "$log" && { started=1; break; }
                    grep -q "agree to the EULA" "$log" && { warn "The server stopped: the EULA is not accepted."; break; }
                fi
                [ $((i % 10)) = 0 ] && note "still starting... ($(( $(date +%s) - t0 )) s)"
            done
            if [ "$started" = 1 ]; then done_ "The server is up."; else warn "It has not finished starting yet: look at $console_hint"; fi
        fi
    fi
else
    note "Start it in another terminal:  cd '$SERVER_DIR' && $starter"
    note "(Install tmux and run this again to have it started in the background for you.)"
fi
say ""
say "  In $console_hint (NOT the game chat) type these two lines:"
say "      gptgod setkey <your key>"
say "      op <your Minecraft name>"
say "  The first gives the god its mind; the second lets you use /god manager in game."
if [ "$started" = 1 ] && [ "$YES" = 0 ]; then read -r -p "  Press Enter here once you have typed them (the check below confirms it) " _ || true; fi
report "API key" "YOU" "type 'gptgod setkey <key>' in the server console"

fi # end of: if [ "$CHECK" = 0 ]

# ---- What this run did ----------------------------------------------------------------------
if [ "$CHECK" = 0 ] && [ "${KOKORO_OK:-0}" = 1 ] && [ "${KOKORO_REPORTED:-0}" = 0 ]; then
    report "Kokoro voice" "READY" "http://127.0.0.1:$KOKORO_PORT, voice $KOKORO_VOICE"
fi
if [ ${#REPORT[@]} -gt 0 ]; then
    say ""
    say "What this run did"
    say "-----------------"
    for row in "${REPORT[@]}"; do
        IFS='|' read -r part state detail <<< "$row"
        case "$state" in READY) c="$C_GREEN" ;; "NOT READY") c="$C_RED" ;; YOU) c="$C_YELLOW" ;; *) c="$C_GRAY" ;; esac
        printf '  %s%-20s %-10s %s%s\n' "$c" "$part" "$state" "$detail" "$C_OFF"
    done
    say "  READY = done.  LATER = the plugin does it by itself.  YOU = a step only you can do."
    [ "$DRY_RUN" = 1 ] && say "  WOULD DO = what a real run would do."
fi

# =============================================================================================
#  9. Final check. Read-only: it looks at files, ports, the server log and Docker, and changes
#  nothing. Every red line comes with its fix. Run it alone with:  bash setup-linux.sh --check
# =============================================================================================
step "9. Final check"
note "Looking at your server folder, its last log, open ports and Docker..."
CHECKS=()
# state: OK (green), FIX (red: something to do), WARN (yellow: worth a look), INFO (gray)
check() { local st="$1" what="$2" detail="$3"; shift 3; local fix; fix="$(printf '%s\n' "$@")"; CHECKS+=("$st"$'\x1f'"$what"$'\x1f'"$detail"$'\x1f'"$fix"); }
log_file="$SERVER_DIR/logs/latest.log"
log_has() { [ -f "$log_file" ] && grep -qF -- "$1" "$log_file"; }
needs_run="start the server, then run this check again"

game_port="$(prop_value "$SERVER_DIR/server.properties" server-port 25565)"
pid="$(server_pid "$SERVER_DIR" || true)"
running=0; [ -n "$pid" ] && running=1
pack_port="$(section_value "$CONFIG_FILE" resource-pack port 8770)"
viewer_on="$(section_value "$CONFIG_FILE" viewer enabled true)"
viewer_port="$(section_value "$CONFIG_FILE" viewer port 4673)"
viewer_bind="$(section_value "$CONFIG_FILE" viewer bind 127.0.0.1)"
voice_port="$(prop_value "$PLUGINS_DIR/voicechat/voicechat-server.properties" port 24454)"
geyser_jar=""; for f in "$PLUGINS_DIR"/Geyser*.jar; do [ -f "$f" ] && { geyser_jar="$f"; break; }; done
geyser_port=19132
if [ -n "$geyser_jar" ]; then
    for d in "$PLUGINS_DIR"/Geyser*/; do [ -f "$d/config.yml" ] && geyser_port="$(section_value "$d/config.yml" bedrock port 19132)"; break; done
fi

# Java
java_exe=""
[ -n "$pid" ] && java_exe="$(readlink "/proc/$pid/exe" 2>/dev/null || true)"
[ -z "$java_exe" ] && java_exe="$(find_java_in "$SERVER_DIR/java")"
[ -z "$java_exe" ] && java_exe="$(command -v java || true)"
if [ -z "$java_exe" ]; then
    check FIX "Java 25" "Java was not found" "Install Temurin 25 (https://adoptium.net/), or run setup with --new-server to put one in the server folder."
else
    jm="$(java_major "$java_exe")"
    if [ "$jm" -ge 25 ]; then check OK "Java 25" "Java $jm ($java_exe)"
    else check FIX "Java 25" "Java $jm found ($java_exe); Paper 26.3 needs Java 25" "Install Temurin 25 (https://adoptium.net/) and use it in your start script."; fi
fi
# Server files
paper=""; for f in "$SERVER_DIR"/*paper*.jar; do [ -f "$f" ] && { paper="$f"; break; }; done
if [ -n "$paper" ]; then check OK "Paper server" "$(basename "$paper")"
else check WARN "Paper server" "no paper*.jar in the server folder" "Download Paper 26.3 from https://papermc.io/downloads/paper (fine to ignore if your start script uses another name)."; fi
eula="$(prop_value "$SERVER_DIR/eula.txt" eula "")"
if [ "$eula" = true ]; then check OK "EULA" "accepted"
elif [ -z "$eula" ]; then check INFO "EULA" "no eula.txt yet" "Run this setup (it asks), or start the server once; it creates eula.txt."
else check FIX "EULA" "not accepted, so the server stops at once" "Open eula.txt, read the EULA at the link inside, and change eula=false to eula=true."; fi
# Running
if [ "$running" = 1 ]; then
    if log_has "Done ("; then check OK "Server running" "port $game_port, fully started"
    else check WARN "Server running" "running but the log has no 'Done (' line yet" "It is still starting. Wait for 'Done (...)! For help, type help' in the console before joining (the first start downloads libraries)."; fi
elif port_listening "$game_port"; then
    check FIX "Server running" "not running, and port $game_port is taken by another program" "Another server may hold it. Stop that one, or set another server-port in server.properties."
else
    check INFO "Server running" "not running now (nothing on port $game_port)" "Some checks below need it running: start it, then run this check again."
fi
# Plugin
god_jar=""; for f in "$PLUGINS_DIR"/gptgodmc*.jar; do [ -f "$f" ] && { god_jar="$f"; break; }; done
if [ -z "$god_jar" ]; then check FIX "GPTGOD plugin" "no gptgodmc jar in plugins/" "Run this setup, or copy gptgodmc-<version>.jar into the plugins folder."
elif log_has "an omniscient god for this world"; then check OK "GPTGOD plugin" "$(basename "$god_jar"), loaded at the last start"
elif log_has "Done ("; then check FIX "GPTGOD plugin" "$(basename "$god_jar") is there but did not load at the last start" "Look in logs/latest.log for lines with 'gptgodmc' and 'ERROR'. Usual causes: not Java 25, not Paper 26.3, or the jar was added after the start (restart)."
else check INFO "GPTGOD plugin" "$(basename "$god_jar") (not started with it yet)" "$needs_run"; fi
n_updates=0; for f in "$PLUGINS_DIR"/update/*.jar; do [ -f "$f" ] && n_updates=$((n_updates + 1)); done
[ "$n_updates" -gt 0 ] && check INFO "Waiting updates" "$n_updates jar(s) in plugins/update" "Restart the server to load them."
# API key, from the log banner; the key itself is never read
if log_has "API key saved to config.yml"; then check OK "API key" "set with gptgod setkey"
elif log_has "API key: found in"; then check OK "API key" "$(grep -F "API key: found in" "$log_file" | tail -n1 | sed 's/.*API key: //')"
elif log_has "API key: MISSING"; then check FIX "API key" "the server says MISSING: the god cannot think" "Get a key at https://console.anthropic.com/ (API Keys), then type in the SERVER CONSOLE:" "    gptgod setkey <your key>"
else check INFO "API key" "unknown until the server has started with the plugin" "$needs_run"; fi
# Voice chat
svc_jar=""; for f in "$PLUGINS_DIR"/voicechat*.jar; do [ -f "$f" ] && { svc_jar="$f"; break; }; done
if [ -z "$svc_jar" ]; then
    check WARN "Voice chat plugin" "Simple Voice Chat is not installed (the god is text only)" "Run this setup and say yes to Simple Voice Chat."
else
    if log_has "Voice enabled:"; then check OK "Voice chat plugin" "$(basename "$svc_jar"), the god can hear and speak"
    elif log_has "Simple Voice Chat is not installed"; then check FIX "Voice chat plugin" "$(basename "$svc_jar") is there but was not loaded at the last start" "Restart the server."
    else check INFO "Voice chat plugin" "$(basename "$svc_jar")"; fi
    if [ "$running" = 1 ]; then
        udp_listening "$voice_port"; rc=$?
        if [ $rc = 0 ]; then check OK "Voice port" "UDP $voice_port open"
        elif [ $rc = 1 ]; then check FIX "Voice port" "nothing on UDP $voice_port" "Look in logs/latest.log for 'voicechat' errors; another program may use the port."; fi
    fi
fi
# Kokoro
k_info="$("${DOCKER[@]}" container inspect "$KOKORO_NAME" --format '{{.Config.Image}}|{{json .HostConfig.DeviceRequests}}' 2>/dev/null || true)"
k_mode=CPU; [ -n "$k_info" ] && is_gpu_container "${k_info%%|*}" "${k_info#*|}" && k_mode=GPU
if kokoro_answers; then
    st=OK; speed=""; fixes=()
    if [ "$DRY_RUN" = 0 ]; then
        if m="$(measure_kokoro)"; then
            made="${m% *}"; audio="${m#* }"
            speed=", made $audio s of speech in $made s"
            if awk -v t="$made" -v a="$audio" 'BEGIN { exit !(a > 0 && t > a) }'; then
                st=WARN
                fixes+=("Slower than real time, so the god's voice will lag. Close other heavy programs, give Kokoro more CPU cores"
                        "(docker update --cpus <n> $KOKORO_NAME), or use an NVIDIA graphics card (run setup again).")
            fi
        else st=WARN; speed=", but a test sentence failed"; fixes+=("See: docker logs $KOKORO_NAME"); fi
    fi
    check "$st" "Kokoro voice" "on the $k_mode, answers at 127.0.0.1:$KOKORO_PORT ($KOKORO_VOICE)$speed" "${fixes[@]+"${fixes[@]}"}"
    kokoro_plan
    if [ "$k_mode" = CPU ] && [ "$PLAN_GPU" = nvidia ]; then
        check WARN "Kokoro GPU" "$PLAN_CARD could run the voice much faster" "Run this setup again and say yes to 'Replace it with the GPU version?'"
    elif [ "$k_mode" = CPU ] && [ -n "$PLAN_CARD" ] && [ -n "$PLAN_WHY" ] && [ "$FORCE_CPU" = 0 ]; then
        check INFO "Kokoro GPU" "not used" "$PLAN_WHY."
    fi
elif [ -n "$k_info" ]; then
    check FIX "Kokoro voice" "container $KOKORO_NAME ($k_mode) exists but does not answer" "docker start $KOKORO_NAME  (it needs about a minute; see docker logs $KOKORO_NAME)"
else
    check WARN "Kokoro voice" "not set up: the god speaks with the plainer Piper voice" "Optional. Run this setup and say yes to Kokoro (needs Docker)."
fi
# Whisper
wm="$(config_value whisper-model base.en)"
if [ -f "$DATA_DIR/whisper/ggml-$wm.bin" ]; then check OK "Whisper ($wm)" "downloaded"
else check INFO "Whisper ($wm)" "not downloaded yet; the plugin gets it the first time someone speaks"; fi
# Texture pack
textures="$(config_value textures custom)"
pack_url="$(section_value "$CONFIG_FILE" resource-pack url "")"
pack_send="$(section_value "$CONFIG_FILE" resource-pack send true)"
pack_fails=0; [ -f "$log_file" ] && pack_fails="$(grep -cF "could not load the vessel's resource pack" "$log_file")"
if [ "$textures" != custom ] || [ "$pack_send" = false ] || [ -n "$pack_url" ]; then
    check INFO "Texture pack" "not served by the plugin (vessel.textures / resource-pack settings)"
elif [ "$running" = 1 ]; then
    if port_listening "$pack_port"; then check OK "Texture pack" "served on TCP $pack_port"
    else check FIX "Texture pack" "nothing on TCP $pack_port, so players see the block body" "Look for 'Vessel resource pack' in logs/latest.log; another program may use port $pack_port (vessel.resource-pack.port)."; fi
fi
[ "$pack_fails" -gt 0 ] && check WARN "Texture pack" "$pack_fails player download(s) failed at the last start" \
    "Players must reach TCP $pack_port on the address they join with: open it in the firewall and forward it like the game port," \
    "or set vessel.resource-pack.public-host. A player who pressed 'No' sees the block body; see TUTORIAL.md."
# Mind viewer
if [ "$viewer_on" = true ] && [ "$running" = 1 ]; then
    if port_listening "$viewer_port"; then
        case "$viewer_bind" in 127.0.0.1|localhost) check OK "Mind viewer" "open http://127.0.0.1:$viewer_port/ in a browser on this machine (or through an SSH tunnel)" ;;
            *) check WARN "Mind viewer" "reachable from the network (viewer.bind $viewer_bind)" "Anyone with its token can read and edit the god's mind. Keep viewer.bind 127.0.0.1 unless you need it." ;; esac
    else check WARN "Mind viewer" "nothing on TCP $viewer_port" "Look for 'Mind viewer' in logs/latest.log; another program may use the port (viewer.port)."; fi
fi
# Bedrock (Geyser)
if [ -n "$geyser_jar" ]; then
    if [ "$running" = 0 ]; then check INFO "Bedrock (Geyser)" "$(basename "$geyser_jar"), UDP $geyser_port" "$needs_run"
    else
        udp_listening "$geyser_port"; rc=$?
        if [ $rc = 0 ]; then check OK "Bedrock (Geyser)" "UDP $geyser_port open for Bedrock players"
        elif [ $rc = 1 ]; then check FIX "Bedrock (Geyser)" "nothing on UDP $geyser_port" "Look for 'Geyser' errors in logs/latest.log."; fi
    fi
fi
# Other plugins
for f in "$PLUGINS_DIR"/EssentialsX*.jar; do [ -f "$f" ] && { check INFO "EssentialsX" "found: it has its own /god (god mode)" "If /god opens Essentials' god mode, use /gptgod instead."; break; }; done
for f in "$PLUGINS_DIR"/LuckPerms*.jar; do [ -f "$f" ] && { check INFO "LuckPerms" "found" "Let a non-op admin command the god:  lp user <name> permission set gptgodmc.admin true"; break; }; done
# Firewall (only shown; changing it needs root, so you run these yourself)
ports_tcp="$game_port $pack_port"; ports_udp="$voice_port"; [ -n "$geyser_jar" ] && ports_udp="$ports_udp $geyser_port"
if command -v ufw >/dev/null 2>&1; then
    cmds="sudo ufw allow $game_port/tcp; sudo ufw allow $pack_port/tcp; sudo ufw allow $voice_port/udp"
    [ -n "$geyser_jar" ] && cmds="$cmds; sudo ufw allow $geyser_port/udp"
    check INFO "Firewall (ufw)" "if ufw is on, players need TCP $ports_tcp and UDP $ports_udp" "    $cmds"
elif command -v firewall-cmd >/dev/null 2>&1; then
    cmds="sudo firewall-cmd --permanent --add-port=$game_port/tcp --add-port=$pack_port/tcp --add-port=$voice_port/udp"
    [ -n "$geyser_jar" ] && cmds="$cmds --add-port=$geyser_port/udp"
    check INFO "Firewall (firewalld)" "if firewalld is on, players need TCP $ports_tcp and UDP $ports_udp" "    $cmds && sudo firewall-cmd --reload"
fi
check INFO "Cloud / VPS" "a hosted server also has the provider's own firewall" "Open TCP $ports_tcp and UDP $ports_udp there too (security group / firewall in the provider's panel)."
# Internet / DNS: a lookup only (nothing is sent to Anthropic)
ips="$(getent ahostsv4 api.anthropic.com 2>/dev/null | awk '{print $1}' | sort -u | tr '\n' ' ')"
if [ -z "$ips" ] && ! command -v getent >/dev/null 2>&1; then
    check INFO "Internet (DNS)" "getent is missing, not checked"
elif [ -z "$ips" ]; then
    check FIX "Internet (DNS)" "api.anthropic.com cannot be looked up" "Check the internet connection. If other sites work, a DNS filter (Pi-hole, NextDNS, router) blocks it: allow api.anthropic.com."
elif printf '%s' "$ips" | grep -Eq '(^| )(0\.|127\.|10\.|192\.168\.)'; then
    check FIX "Internet (DNS)" "api.anthropic.com resolves to $ips: a DNS filter blocks it" "Allow api.anthropic.com in your Pi-hole / NextDNS / router filter, or use another DNS server."
else
    check OK "Internet (DNS)" "api.anthropic.com found"
fi

say ""
say "Checklist"
say "---------"
to_fix=0; to_look=0
for row in "${CHECKS[@]}"; do
    # Fields are separated by \x1f (the fix may hold several lines)
    sep=$'\x1f'
    st="${row%%"$sep"*}"; rest="${row#*"$sep"}"
    what="${rest%%"$sep"*}"; rest="${rest#*"$sep"}"
    detail="${rest%%"$sep"*}"; fix="${rest#*"$sep"}"
    case "$st" in
        OK) c="$C_GREEN"; tag="[ OK ]" ;; FIX) c="$C_RED"; tag="[FIX!]"; to_fix=$((to_fix + 1)) ;;
        WARN) c="$C_YELLOW"; tag="[WARN]"; to_look=$((to_look + 1)) ;; *) c="$C_GRAY"; tag="[info]" ;;
    esac
    printf '  %s%s %-18s %s%s\n' "$c" "$tag" "$what" "$detail" "$C_OFF"
    if [ "$st" != OK ]; then
        while IFS= read -r line; do [ -n "$line" ] && printf '                            %s\n' "$line"; done <<< "$fix"
    fi
done
say ""
if [ "$to_fix" = 0 ]; then printf '  %sNothing red.%s%s\n' "$C_GREEN" "$([ "$to_look" -gt 0 ] && echo " $to_look yellow line(s) are optional or worth a look.")" "$C_OFF"
else printf '  %s%s red line(s) to fix (each says how), then run: bash setup-linux.sh --check%s\n' "$C_RED" "$to_fix" "$C_OFF"; fi
say ""
say "Letting friends join from other places: forward these ports on your router to this machine (see TUTORIAL.md):"
say "  TCP $game_port (game)   TCP $pack_port (god's texture pack)   UDP $voice_port (voice chat)$([ -n "$geyser_jar" ] && echo "   UDP $geyser_port (Bedrock)")"
say "  Never forward $viewer_port (mind viewer) or $KOKORO_PORT (Kokoro): they are for this machine only."
say ""
say "Players: the voice chat MOD is installed in each player's own game: $VOICECHAT_MOD"
[ "$CHECK" = 0 ] && say "Next: start (or restart) the server, run  gptgod setkey <key>  in its console, join, and type  /pray hello"
[ "$DRY_RUN" = 1 ] && { say ""; say "That was a dry run. Nothing was changed. Run without --dry-run to do it."; }
exit 0
