<#
.SYNOPSIS
    GPTGOD easy setup for Windows.

.DESCRIPTION
    Sets up a GPTGOD Minecraft server, from nothing or on top of the server you have:
      0. If you have no server: gets Java 25 (Adoptium) and Paper (PaperMC) into a new folder,
         writes start.bat and asks you to accept the Minecraft EULA (a window with the link).
      1. Copies the GPTGOD plugin jar into your server's plugins folder.
      2. Downloads the Simple Voice Chat server plugin (from Modrinth).
      3. Starts the Kokoro voice server in Docker (the god's speaking voice).
      4. Pre-downloads the Whisper speech-recognition model (so the god can hear players).
      5. Optionally pre-installs Piper, the backup voice.
      6. Gets the players' Fabric mods (Fabric installer, Fabric API, Simple Voice Chat, Mod Menu)
         into for-players\ plus a zip for friends, and can install them into Minecraft on this PC.
      7. Starts the server and shows how to give the god its Anthropic API key (this script never
         asks for it).
      7. Checks everything the server needs and prints a checklist: green = fine, red = fix this,
         with the fix for each red line (ports, firewall, API key, voice, texture pack, ...).

    This is a plain, readable script. Nothing is hidden or encoded. Every download is shown
    (full URL) and asked about before it starts. It never needs administrator rights.
    Run it as many times as you like: finished steps are detected and skipped.

    Start it by double-clicking setup-windows.bat, or from PowerShell:
        powershell -ExecutionPolicy Bypass -File setup.ps1 [-DryRun] [-Check] [-Yes] [-ServerDir <folder>]
    Only the checklist, nothing installed: double-click check-windows.bat (or add -Check / --check).

.PARAMETER DryRun
    Show every step and the exact commands, but change nothing and download nothing.
    (Same as --dry-run.)

.PARAMETER Check
    Skip the setup steps and only run the read-only checklist. (Same as --check.)

.PARAMETER Cpu
    Run the Kokoro voice on the CPU even if an NVIDIA graphics card could run it. (Same as --cpu.)

.PARAMETER NewServer
    Make a new server (Java + Paper) without asking first. (Same as --new-server.)

.PARAMETER AcceptEula
    Accept the Minecraft EULA (https://aka.ms/MinecraftEULA) without the window. Only use this if
    you have read it and agree. --yes alone never accepts it. (Same as --accept-eula.)

.PARAMETER Yes
    Accept the suggested answer to every question. (Same as --yes.)

.PARAMETER ServerDir
    Your Minecraft server folder (the one with server.properties).

.PARAMETER PluginJar
    The GPTGOD jar to install. Default: the newest gptgodmc-*.jar found next to this folder.
#>
[CmdletBinding()]
param(
    [switch]$DryRun,
    [switch]$Check,
    [switch]$Cpu,
    [switch]$NewServer,
    [switch]$AcceptEula,
    [switch]$Yes,
    [string]$ServerDir = "",
    [string]$PluginJar = "",
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Rest
)

# Also accept the Linux-style flags, so both scripts take the same options.
foreach ($a in $Rest) {
    switch ($a) {
        "--dry-run" { $DryRun = $true }
        "--check"   { $Check = $true }
        "--cpu"     { $Cpu = $true }
        "--new-server"  { $NewServer = $true }
        "--accept-eula" { $AcceptEula = $true }
        "--yes"     { $Yes = $true }
        "-y"        { $Yes = $true }
        default     { Write-Host "Unknown option: $a (known: -DryRun / --dry-run, -Check / --check, -Cpu / --cpu, -NewServer / --new-server, -AcceptEula / --accept-eula, -Yes / --yes, -ServerDir, -PluginJar)"; exit 2 }
    }
}

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # the progress bar makes Invoke-WebRequest very slow on PowerShell 5.1
# Windows PowerShell 5.1 may default to old TLS versions; every download here uses HTTPS with TLS 1.2.
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ---------------------------------------------------------------------------------------------
#  Everything this script may contact or run. These match the plugin's own defaults
#  (src/main/resources/config.yml, voice/Transcriber.java, voice/Synthesizer.java).
# ---------------------------------------------------------------------------------------------
$KokoroName      = "kokoro-tts"
$KokoroPort      = 8880
# Kokoro-FastAPI images on GitHub Container Registry (github.com/remsky/Kokoro-FastAPI), pinned.
$KokoroCpuImage    = "ghcr.io/remsky/kokoro-fastapi-cpu:v0.10.0"        # any PC, ~1.5 GB
$KokoroGpuImage    = "ghcr.io/remsky/kokoro-fastapi-gpu:v0.10.0"        # NVIDIA GTX 900 to RTX 40 (CUDA 12.6), ~4.6 GB
$KokoroGpu128Image = "ghcr.io/remsky/kokoro-fastapi-gpu:v0.10.0-cu128"  # NVIDIA RTX 50 (CUDA 12.8)
$KokoroVoice     = "am_michael"
$WhisperUrlBase  = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-"
$PiperRelease    = "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/"
$PiperVoicesBase = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/"
$ModrinthApi     = "https://api.modrinth.com/v2/project/simple-voice-chat/version"
$DockerPage      = "https://www.docker.com/products/docker-desktop/"
$VoiceChatMod    = "https://modrinth.com/mod/simple-voice-chat"
$WhisperModels   = [ordered]@{
    "tiny.en"  = "75 MB, fastest, English only"
    "base.en"  = "142 MB, the plugin's default, English only"
    "small.en" = "466 MB, understands better, slower, English only"
    "base"     = "142 MB, many languages"
    "small"    = "466 MB, many languages, better"
}

$Report = New-Object System.Collections.Generic.List[object]
$DONE_STATE = if ($DryRun) { "WOULD DO" } else { "READY" }

# ---------------------------------------------------------------------------------------------
#  Small helpers
# ---------------------------------------------------------------------------------------------
function Say([string]$text) { Write-Host $text }
function Step([string]$text) { Write-Host ""; Write-Host "=== $text ===" -ForegroundColor Cyan }
function Note([string]$text) { Write-Host "  $text" -ForegroundColor DarkGray }
function Warn([string]$text) { Write-Host "  ! $text" -ForegroundColor Yellow }
function Done([string]$text) { Write-Host "  OK $text" -ForegroundColor Green }
# For results of changes: in a dry run they did not happen, so say nothing (DryNote already said "would").
function Did([string]$text) { if (-not $DryRun) { Done $text } }
function DryNote([string]$text) { Write-Host "  [dry run] would $text" -ForegroundColor Magenta }

function Add-Report([string]$what, [string]$state, [string]$detail) {
    $Report.Add([pscustomobject]@{ Part = $what; State = $state; Detail = $detail })
}

function Ask-YesNo([string]$question, [bool]$default) {
    $hint = if ($default) { "[Y/n]" } else { "[y/N]" }
    if ($Yes) { Say "  $question $hint -> $(if ($default) { 'yes' } else { 'no' }) (--yes)"; return $default }
    while ($true) {
        $answer = (Read-Host "  $question $hint").Trim().ToLower()
        if ($answer -eq "") { return $default }
        if ($answer -in @("y", "yes")) { return $true }
        if ($answer -in @("n", "no")) { return $false }
        Say "  Please type y or n."
    }
}

function Ask-Text([string]$question, [string]$default) {
    if ($Yes) { Say "  $question [$default] -> $default (--yes)"; return $default }
    $answer = (Read-Host "  $question [$default]").Trim().Trim('"')
    if ($answer -eq "") { return $default }
    return $answer
}

function Pause-ForUser([string]$text) {
    if ($Yes) { return }
    Read-Host "  $text" | Out-Null
}

# Show the URL and the target, ask, then download to "<file>.part" and rename when complete.
function Get-Download([string]$url, [string]$target, [string]$size) {
    Say ""
    Say "  Download: $url"
    Say "  Save to:  $target"
    if ($size) { Say "  Size:     about $size" }
    if (-not (Ask-YesNo "Download this file?" $true)) { return $false }
    if ($DryRun) { DryNote "download it"; return $false }
    $dir = Split-Path -Parent $target
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $part = "$target.part"
    try {
        Invoke-WebRequest -Uri $url -OutFile $part -UseBasicParsing -UserAgent "gptgodmc-setup"
        Move-Item -Force $part $target
        Done "Downloaded $(Split-Path -Leaf $target)"
        return $true
    } catch {
        if (Test-Path $part) { Remove-Item -Force $part }
        Warn "Download failed: $($_.Exception.Message)"
        return $false
    }
}

# Run a program, printing the exact command first. In a dry run only print it.
function Invoke-Shown([string]$exe, [string[]]$arguments) {
    Say "  > $exe $($arguments -join ' ')"
    if ($DryRun) { DryNote "run the command above"; return 0 }
    & $exe @arguments | Out-Host
    return $LASTEXITCODE
}

# Copy a file (in a dry run only say so).
function Copy-Shown([string]$source, [string]$target) {
    if ($DryRun) { DryNote "copy $source -> $target"; return }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -Force $source $target
}

# Move an old file into <server>\gptgod-setup-backups with the date added (never deletes it).
function Backup-File([string]$file, [string]$serverDir) {
    $backups = Join-Path $serverDir "gptgod-setup-backups"
    $dest = Join-Path $backups ((Split-Path -Leaf $file) + "." + (Get-Date -Format "yyyyMMdd-HHmmss"))
    if ($DryRun) { DryNote "move $file -> $dest (backup)"; return }
    New-Item -ItemType Directory -Force -Path $backups | Out-Null
    Move-Item -Force $file $dest
    Note "Old file kept as $dest"
}

function Get-Sha256([string]$file) { return (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLower() }

# Reads one simple "key: value" line from the plugin's config.yml. Only these keys are ever read:
# whisper-model and piper-voice (never the API key).
function Get-ConfigValue([string]$configFile, [string]$key, [string]$default) {
    if (-not (Test-Path $configFile)) { return $default }
    foreach ($line in Get-Content -LiteralPath $configFile) {
        if ($line -match "^\s*$([regex]::Escape($key)):\s*`"?([^`"#\s]+)") { return $Matches[1] }
    }
    return $default
}

function Test-ServerRunning([string]$serverDir) {
    $port = 25565
    $props = Join-Path $serverDir "server.properties"
    if (Test-Path $props) {
        foreach ($line in Get-Content -LiteralPath $props) {
            if ($line -match "^\s*server-port\s*=\s*(\d+)") { $port = [int]$Matches[1] }
        }
    }
    $level = "world"
    if (Test-Path $props) {
        foreach ($line in Get-Content -LiteralPath $props) { if ($line -match "^\s*level-name\s*=\s*(.+)$") { $level = $Matches[1].Trim() } }
    }
    $listening = $false
    try {
        $listening = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction Stop).Count -gt 0
    } catch {
        $listening = $false
    }
    # A running server keeps <world>\session.lock open. Opening it for a moment without sharing
    # (read-only, closed at once, nothing written) fails exactly when this folder's server runs.
    # So a busy port without that lock belongs to some other program (often another server).
    $lock = Join-Path $serverDir "$level\session.lock"
    $locked = $false
    if (Test-Path -LiteralPath $lock) {
        try { $fs = [IO.File]::Open($lock, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None); $fs.Close() }
        catch { $locked = $true }
    }
    $running = $listening -and ($locked -or -not (Test-Path -LiteralPath $lock)) -and (Test-Path -LiteralPath (Join-Path $serverDir "logs"))
    return [pscustomobject]@{ Running = $running; Port = $port; PortTaken = ($listening -and -not $running) }
}

function Test-KokoroVoices {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$KokoroPort/v1/audio/voices" -UseBasicParsing -TimeoutSec 5
        return ($r.StatusCode -eq 200 -and $r.Content -match $KokoroVoice)
    } catch {
        return $false
    }
}

# Which Kokoro to run. The GPU image is CUDA-only (NVIDIA). It is used when an NVIDIA card answers
# nvidia-smi and Docker Desktop runs on the WSL 2 engine (which passes the GPU through). Intel and
# AMD graphics are not used by Kokoro-FastAPI on Windows: they get the CPU image.
function Get-KokoroPlan {
    $onCpu = [pscustomobject]@{ Gpu = $false; Image = $KokoroCpuImage; Card = ""; Why = "" }
    if ($Cpu) { $onCpu.Why = "You chose --cpu"; return $onCpu }
    $smi = Get-Command nvidia-smi -ErrorAction SilentlyContinue
    if (-not $smi) { $onCpu.Why = "No NVIDIA graphics card found"; return $onCpu }
    $name = @(cmd.exe /c "`"$($smi.Source)`" --query-gpu=name --format=csv,noheader 2>nul") | Where-Object { $_ } | Select-Object -First 1
    if ($LASTEXITCODE -ne 0 -or -not $name) { $onCpu.Why = "nvidia-smi is installed but no NVIDIA card answered (update the NVIDIA driver?)"; return $onCpu }
    $name = $name.Trim()
    $kernel = (cmd.exe /c "docker info --format {{.KernelVersion}} 2>nul" | Out-String).Trim()
    if ($kernel -notmatch "microsoft|WSL") {
        $onCpu.Why = "$name found, but Docker Desktop is not using the WSL 2 engine, which the GPU needs (Docker Desktop > Settings > General)"
        return $onCpu
    }
    # RTX 50-series (compute capability 12) needs the CUDA 12.8 build.
    $capText = @(cmd.exe /c "`"$($smi.Source)`" --query-gpu=compute_cap --format=csv,noheader 2>nul") | Select-Object -First 1
    $cap = 0.0
    [void][double]::TryParse(("$capText").Trim(), [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$cap)
    $image = if ($cap -ge 12 -or $name -match "RTX 50\d\d") { $KokoroGpu128Image } else { $KokoroGpuImage }
    return [pscustomobject]@{ Gpu = $true; Image = $image; Card = $name; Why = "" }
}

function Test-ImageIsGpu([string]$image, [string]$deviceRequests) {
    return ($image -match "kokoro-fastapi-(gpu|rocm)") -or ($deviceRequests -and $deviceRequests -ne "null" -and $deviceRequests -ne "[]")
}

# Pull an image and start the Kokoro container from it. Returns $true when "docker run" worked.
function Start-KokoroContainer([string]$image, [string[]]$extra) {
    if ((Invoke-Shown "docker" @("pull", $image)) -ne 0) { Warn "docker pull failed."; return $false }
    $runArgs = @("run", "-d", "--name", $KokoroName, "--restart", "unless-stopped", "-p", "127.0.0.1:${KokoroPort}:$KokoroPort") + $extra + @($image)
    if ((Invoke-Shown "docker" $runArgs) -eq 0) { return $true }
    & docker rm -f $KokoroName *> $null    # a half-created container would block the next try
    return $false
}

# Speak one test sentence and time it. Returns $null if Kokoro does not answer.
function Measure-KokoroSpeech {
    try {
        $body = @{ model = "kokoro"; input = "Mortals, hear me. I am awake, and I am listening."; voice = $KokoroVoice; response_format = "wav" } | ConvertTo-Json
        $sw = [Diagnostics.Stopwatch]::StartNew()
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$KokoroPort/v1/audio/speech" -Method Post -Body $body -ContentType "application/json" -UseBasicParsing -TimeoutSec 120
        $sw.Stop()
        $audio = [Math]::Max(0, $r.RawContentLength - 44) / 48000.0   # 24 kHz, 16-bit, mono
        return [pscustomobject]@{ Seconds = $sw.Elapsed.TotalSeconds; Audio = $audio }
    } catch { return $null }
}

# ---------------------------------------------------------------------------------------------
#  Making a new server, and the players' mods (official sources only)
# ---------------------------------------------------------------------------------------------
$McVersion        = "26.3"
$PaperApi         = "https://fill.papermc.io/v3/projects/paper/versions/$McVersion/builds/latest"
$JavaArch         = if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "aarch64" } else { "x64" }
$AdoptiumApi      = "https://api.adoptium.net/v3/assets/latest/25/hotspot?os=windows&architecture=$JavaArch&image_type=jre"
$FabricInstallers = "https://meta.fabricmc.net/v2/versions/installer"
$FabricLoaders    = "https://meta.fabricmc.net/v2/versions/loader/$McVersion"
$ModrinthBase     = "https://api.modrinth.com/v2"
$EulaUrl          = "https://aka.ms/MinecraftEULA"
# Mods for players (Fabric). Their required dependencies are looked up on Modrinth too.
$PlayerMods = [ordered]@{
    "fabric-api"        = "Fabric API (needed by the other mods)"
    "simple-voice-chat" = "Simple Voice Chat (hear the god and talk to it)"
    "modmenu"           = "Mod Menu (adds a 'Mods' button, to check what loaded)"
}

# Small JSON lookups (no files): the address is printed; in a dry run nothing is sent.
function Get-Json([string]$url) {
    Note "Lookup: $url"
    if ($DryRun) { return $null }
    try {
        return Invoke-RestMethod -Uri $url -UseBasicParsing -UserAgent "gptgodmc-setup" -TimeoutSec 30 | ForEach-Object { $_ }
    } catch {
        Warn "No answer from $url : $($_.Exception.Message)"
        return $null
    }
}

# Get-Download, then compare the file's hash with the one the publisher lists; delete it on mismatch.
function Get-VerifiedDownload([string]$url, [string]$target, [string]$size, [string]$algorithm, [string]$expected) {
    if (-not (Get-Download $url $target $size)) { return $false }
    $actual = (Get-FileHash -Algorithm $algorithm -LiteralPath $target).Hash.ToLower()
    if ($expected -and $actual -ne $expected.ToLower()) {
        Remove-Item -Force $target
        Warn "The $algorithm checksum does not match what the publisher lists. Deleted the file."
        return $false
    }
    if ($expected) { Done "$algorithm checksum matches the publisher's." }
    return $true
}

# The newest Modrinth file of a project for a loader and Minecraft version, plus its required
# dependencies (each resolved the same way). Returns objects with Project, Name, File (filename, url,
# hashes, size).
function Resolve-ModrinthMods([string[]]$projects, [string]$loader, [string]$mc) {
    $seen = @{}
    $out = New-Object System.Collections.Generic.List[object]
    $queue = New-Object System.Collections.Generic.Queue[string]
    foreach ($p in $projects) { $queue.Enqueue($p) }
    while ($queue.Count -gt 0) {
        $p = $queue.Dequeue()
        if ($seen.ContainsKey($p)) { continue }
        $seen[$p] = $true
        $q = "?loaders=" + [uri]::EscapeDataString("[`"$loader`"]") + "&game_versions=" + [uri]::EscapeDataString("[`"$mc`"]")
        $versions = @(Get-Json "$ModrinthBase/project/$p/version$q")
        if ($versions.Count -eq 0 -or -not $versions[0]) { if (-not $DryRun) { Warn "No $loader build of '$p' for $mc on Modrinth." }; continue }
        $v = $versions[0]
        $seen[$v.project_id] = $true
        $file = @($v.files | Where-Object { $_.primary }) + @($v.files) | Select-Object -First 1
        if ($file.url -notmatch "^https://cdn\.modrinth\.com/" -or $file.filename -match '[\\/]') { Warn "Unexpected file from Modrinth for '$p'; skipped."; continue }
        $out.Add([pscustomobject]@{ Project = $p; Name = $v.name; File = $file })
        foreach ($d in @($v.dependencies)) {
            if ($d.dependency_type -eq "required" -and $d.project_id -and -not $seen.ContainsKey($d.project_id)) { $queue.Enqueue($d.project_id) }
        }
    }
    return $out
}

# The Minecraft EULA must be accepted by the person running the server, so it is always asked:
# a small window with the link (or a typed "yes" where no window can open). --accept-eula skips it.
function Confirm-Eula {
    if ($AcceptEula) { Say "  EULA accepted with --accept-eula."; return $true }
    if ($DryRun) { DryNote "show the EULA window ($EulaUrl) and write eula=true only if you click 'I agree'"; return $false }
    try {
        Add-Type -AssemblyName System.Windows.Forms
        Add-Type -AssemblyName System.Drawing
        $form = New-Object System.Windows.Forms.Form
        $form.Text = "Minecraft EULA"
        $form.Size = New-Object System.Drawing.Size(520, 250)
        $form.StartPosition = "CenterScreen"
        $form.TopMost = $true
        $form.FormBorderStyle = "FixedDialog"
        $form.MaximizeBox = $false
        $form.MinimizeBox = $false
        $label = New-Object System.Windows.Forms.Label
        $label.Text = "To run a Minecraft server you must agree to Mojang's End User License Agreement (EULA).`n`nPlease read it first (the link opens in your browser). Clicking 'I agree' writes eula=true into eula.txt in your server folder."
        $label.Location = New-Object System.Drawing.Point(15, 15)
        $label.Size = New-Object System.Drawing.Size(480, 80)
        $link = New-Object System.Windows.Forms.LinkLabel
        $link.Text = "Read the EULA: $EulaUrl"
        $link.Location = New-Object System.Drawing.Point(15, 105)
        $link.Size = New-Object System.Drawing.Size(480, 25)
        $link.Add_LinkClicked({ Start-Process $EulaUrl })
        $agreeButton = New-Object System.Windows.Forms.Button
        $agreeButton.Text = "I agree"
        $agreeButton.Location = New-Object System.Drawing.Point(270, 150)
        $agreeButton.Size = New-Object System.Drawing.Size(105, 32)
        $agreeButton.DialogResult = [System.Windows.Forms.DialogResult]::Yes
        $declineButton = New-Object System.Windows.Forms.Button
        $declineButton.Text = "I do not agree"
        $declineButton.Location = New-Object System.Drawing.Point(385, 150)
        $declineButton.Size = New-Object System.Drawing.Size(105, 32)
        $declineButton.DialogResult = [System.Windows.Forms.DialogResult]::No
        $form.Controls.AddRange(@($label, $link, $agreeButton, $declineButton))
        $form.AcceptButton = $agreeButton
        $form.CancelButton = $declineButton
        Say "  A window asks you about the Minecraft EULA (it may be behind this one)."
        return ($form.ShowDialog() -eq [System.Windows.Forms.DialogResult]::Yes)
    } catch {
        Say "  Read the Minecraft EULA: $EulaUrl"
        $answer = (Read-Host "  Type yes if you agree to it").Trim().ToLower()
        return ($answer -eq "yes")
    }
}

function Find-JavaIn([string]$dir) {
    if (-not (Test-Path -LiteralPath $dir)) { return $null }
    $f = Get-ChildItem -LiteralPath $dir -Filter "java.exe" -File -Recurse -Depth 3 -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\bin\\java\.exe$' } | Select-Object -First 1
    if ($f) { return $f.FullName } else { return $null }
}

function Get-JavaMajor([string]$exe) {
    if (-not $exe -or -not (Test-Path -LiteralPath $exe)) { return 0 }
    $t = (cmd.exe /c "`"$exe`" -version 2>&1") -join " "
    if ($t -match 'version "1\.(\d+)') { return [int]$Matches[1] }
    if ($t -match 'version "(\d+)') { return [int]$Matches[1] }
    return 0
}

# "key=value" from a .properties / eula.txt file.
function Get-PropValue([string]$file, [string]$key, [string]$default) {
    if (-not (Test-Path -LiteralPath $file)) { return $default }
    foreach ($line in Get-Content -LiteralPath $file) {
        if ($line -match "^\s*$([regex]::Escape($key))\s*=\s*(.*)$") { $v = $Matches[1].Trim(); if ($v) { return $v } }
    }
    return $default
}

# ---------------------------------------------------------------------------------------------
#  Start
# ---------------------------------------------------------------------------------------------
Say ""
Say "GPTGOD easy setup (Windows)"
Say "---------------------------"
Say "This sets up the god's voice and checks the plugin is in place. Every download is shown"
Say "with its full address and asked about first. Press Ctrl+C at any time to stop."
if ($DryRun) { Say ""; Say "DRY RUN: nothing will be changed or downloaded; you will see what would happen." }
if ($Check) { Say ""; Say "CHECK ONLY: nothing will be installed or changed; this only looks and reports." }

$SetupDir = $PSScriptRoot
$RepoDir = Split-Path -Parent $SetupDir

# ---- 1. Server folder -----------------------------------------------------------------------
Step "1. Your Minecraft server"
$IsNewServer = $false
$hereHasServer = Test-Path (Join-Path (Get-Location) "server.properties")
if (-not $ServerDir -and -not $Check -and ($NewServer -or -not $hereHasServer)) {
    if ($NewServer) {
        $IsNewServer = $true
    } else {
        Say "  Do you already have a Minecraft server folder? If not, this makes a new one for you:"
        Say "  Java 25 and Paper $McVersion (about 115 MB of downloads, no admin rights, nothing system-wide)."
        $IsNewServer = -not (Ask-YesNo "Do you already have a server?" $false)
    }
}
if ($IsNewServer) {
    $answer = Ask-Text "Where should the new server go?" (Join-Path $HOME "GPTGOD-server")
    $ServerDir = [IO.Path]::GetFullPath($answer)
    if (Test-Path -LiteralPath $ServerDir) {
        Note "That folder exists already; anything set up there is kept."
    } elseif ($DryRun) {
        DryNote "create $ServerDir"
    } else {
        New-Item -ItemType Directory -Path $ServerDir | Out-Null
        Done "Created $ServerDir"
    }
} else {
    $defaultServer = if ($ServerDir) { $ServerDir } elseif ($hereHasServer) { (Get-Location).Path } else { "" }
    while ($true) {
        if ($ServerDir -and $Yes) { $answer = $ServerDir }
        elseif ($Yes -and -not $defaultServer) { Say "  With --yes, pass the server folder (-ServerDir <folder>) or --new-server."; exit 2 }
        else {
            Say "  Which folder is your Minecraft server in? It is the folder that has server.properties"
            Say "  and a plugins folder. Tip: you can drag the folder into this window."
            $answer = Ask-Text "Server folder" $defaultServer
        }
        if ($answer -and (Test-Path -LiteralPath $answer -PathType Container)) {
            $ServerDir = (Resolve-Path -LiteralPath $answer).Path
            if (-not (Test-Path (Join-Path $ServerDir "server.properties"))) {
                Warn "There is no server.properties in $ServerDir. (A new server makes it on first start.)"
                if (-not (Ask-YesNo "Use this folder anyway?" $false)) { if ($Yes) { exit 2 }; continue }
            }
            break
        }
        Warn "That folder does not exist: '$answer'"
        if ($Yes) { exit 2 }
    }
}
$PluginsDir = Join-Path $ServerDir "plugins"
$DataDir = Join-Path $PluginsDir "gptgodmc"
$ConfigFile = Join-Path $DataDir "config.yml"
Done "Server folder: $ServerDir"
if (-not (Test-Path $PluginsDir) -and -not $Check) {
    if ($DryRun) { DryNote "create $PluginsDir" } else { New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null; Done "Created $PluginsDir" }
}
$state = Test-ServerRunning $ServerDir
$ServerRunning = $state.Running
if ($state.PortTaken) {
    Warn "Port $($state.Port) is in use by another program (often another Minecraft server on this PC),"
    Warn "so this server cannot start while that one runs. Stop the other one first, or change"
    Warn "server-port in server.properties (its voice, texture-pack and viewer ports would clash too)."
}
if ($ServerRunning) {
    Warn "This server is RUNNING (port $($state.Port))."
    Note "Plugin jars will go into plugins\update\ so the server swaps them in at its next start"
    Note "(replacing a jar under a running server breaks it)."
} elseif (-not $state.PortTaken) {
    Note "The server is not running (nothing on port $($state.Port)). Good."
}

# Steps 2-8 change things; --check skips straight to the final check.
if (-not $Check) {

# ---- Java, Paper, start script, EULA (a new server, or a folder without Paper) ---------------
$ServerJava = Find-JavaIn (Join-Path $ServerDir "java")
if (-not $ServerJava) {
    $c = Get-Command java -ErrorAction SilentlyContinue
    if ($c -and (Get-JavaMajor $c.Source) -ge 25) { $ServerJava = $c.Source }
}
$paperJar = Get-ChildItem -LiteralPath $ServerDir -Filter "*.jar" -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "paper" } | Select-Object -First 1
$makeServer = $IsNewServer
if (-not $IsNewServer -and -not $paperJar -and -not $ServerRunning) {
    Warn "There is no Paper server jar in this folder."
    $makeServer = Ask-YesNo "Set up Paper $McVersion in this folder?" $true
}
if ($makeServer) {
    Step "1b. Java 25 and Paper $McVersion"
    if ($ServerJava) {
        Done "Java 25: $ServerJava"
    } else {
        Note "Paper $McVersion needs Java 25. This gets the free Temurin 25 runtime from Adoptium (Eclipse"
        Note "Foundation) and keeps it inside the server folder, so nothing is installed on Windows itself."
        $javaDir = Join-Path $ServerDir "java"
        $asset = @(Get-Json $AdoptiumApi) | Select-Object -First 1
        if ($asset -and $asset.binary.package.link -match "^https://github\.com/adoptium/") {
            $pkg = $asset.binary.package
            $zip = Join-Path $env:TEMP ("gptgod-setup-" + $pkg.name)
            if (Get-VerifiedDownload $pkg.link $zip ("{0:N0} MB" -f ($pkg.size / 1MB)) "SHA256" $pkg.checksum) {
                Expand-Archive -LiteralPath $zip -DestinationPath $javaDir -Force
                Remove-Item -Force $zip
                $ServerJava = Find-JavaIn $javaDir
                if ($ServerJava) { Done "Java $($asset.version.semver) is in $javaDir" }
            }
        } elseif ($DryRun) {
            Say "  Then download the Java 25 zip it names (github.com/adoptium, ~58 MB), check its SHA-256, unpack into $javaDir"
        } elseif ($JavaArch -eq "aarch64") {
            Warn "Adoptium has no Java 25 build for Windows on ARM yet. Install a Java 25 for ARM yourself (e.g. Microsoft Build of OpenJDK)."
        } else {
            Warn "Could not get Java 25 automatically. Install Temurin 25 from https://adoptium.net/ and run this again."
        }
    }
    $paperTarget = Join-Path $ServerDir "paper.jar"
    if ($paperJar) {
        Done "Paper is here: $($paperJar.Name)"
    } else {
        Note "Paper is the Minecraft server program (from PaperMC, papermc.io)."
        $build = Get-Json $PaperApi
        $dl = if ($build) { $build.downloads.'server:default' } else { $null }
        if ($dl -and $dl.url -match "^https://([a-z0-9-]+\.)?papermc\.io/") {
            if (Get-VerifiedDownload $dl.url $paperTarget ("{0:N0} MB" -f ($dl.size / 1MB)) "SHA256" $dl.checksums.sha256) {
                Done "Paper $McVersion build $($build.id) saved as paper.jar"
            }
        } elseif ($DryRun) {
            Say "  Then download the Paper jar it names (fill-data.papermc.io, ~55 MB), check its SHA-256, save as $paperTarget"
        } else {
            Warn "Could not get Paper. Download it from https://papermc.io/downloads/paper into $ServerDir and run this again."
        }
    }
    $starter = Join-Path $ServerDir "start.bat"
    if (Test-Path $starter) {
        Done "start.bat is here (kept as it is)"
    } else {
        $totalGb = 8
        try { $totalGb = [Math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB) } catch { }
        $suggestRam = if ($totalGb -ge 16) { "6G" } elseif ($totalGb -ge 8) { "4G" } else { "2G" }
        Note "This PC has about $totalGb GB of memory. 4G is enough for a few friends; 6G with voice."
        $ram = ""
        while ($ram -notmatch '^\d+[GM]$') { $ram = (Ask-Text "Memory for the server (like 4G)" $suggestRam).ToUpper() }
        $javaCmd = "java"
        if ($ServerJava -and $ServerJava.StartsWith($ServerDir, [StringComparison]::OrdinalIgnoreCase)) { $javaCmd = "`"%~dp0" + $ServerJava.Substring($ServerDir.Length).TrimStart('\') + "`"" }
        elseif ($ServerJava) { $javaCmd = "`"$ServerJava`"" }
        elseif ($DryRun) { $javaCmd = "`"%~dp0java\<jre folder>\bin\java.exe`"" }
        $lines = @(
            "@echo off",
            "rem Starts the Minecraft server (written by GPTGOD setup). Stop it by typing: stop",
            "cd /d `"%~dp0`"",
            "$javaCmd -Xms$ram -Xmx$ram -jar paper.jar --nogui",
            "pause")
        if ($DryRun) { DryNote "write $starter :"; $lines | ForEach-Object { Note "    $_" } }
        else { Set-Content -LiteralPath $starter -Value $lines -Encoding ASCII; Done "Wrote start.bat (double-click it to start the server)" }
    }
    Add-Report "Paper server" $DONE_STATE "$ServerDir (start.bat)"
}
$eulaFile = Join-Path $ServerDir "eula.txt"
if ((Test-Path (Join-Path $ServerDir "paper.jar")) -or $makeServer -or $paperJar) {
    if ((Get-PropValue $eulaFile "eula" "") -ne "true") {
        Step "1c. The Minecraft EULA"
        if (Confirm-Eula) {
            $lines = @(
                "#By changing the setting below to TRUE you are indicating your agreement to our EULA ($EulaUrl).",
                "#Accepted by the server owner in GPTGOD setup on $(Get-Date -Format 'yyyy-MM-dd HH:mm')",
                "eula=true")
            Set-Content -LiteralPath $eulaFile -Value $lines -Encoding ASCII
            Done "EULA accepted (eula.txt)"
            Add-Report "EULA" "READY" "accepted"
        } elseif ($DryRun) {
            Add-Report "EULA" "WOULD DO" "ask you"
        } else {
            Warn "Not accepted. The server will not start until eula.txt says eula=true."
            Add-Report "EULA" "NOT READY" "not accepted"
        }
    }
}

# ---- 2. GPTGOD plugin jar -------------------------------------------------------------------
Step "2. The GPTGOD plugin"
if (-not $PluginJar) {
    $candidates = @()
    foreach ($dir in @($SetupDir, $RepoDir, (Join-Path $RepoDir "build\libs"))) {
        if (Test-Path $dir) {
            $candidates += Get-ChildItem -LiteralPath $dir -Filter "gptgodmc-*.jar" -File |
                Where-Object { $_.Name -notmatch "-(sources|javadoc|plain)\.jar$" }
        }
    }
    $newest = $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($newest) { $PluginJar = $newest.FullName }
}
$existingGod = @(Get-ChildItem -LiteralPath $PluginsDir -Filter "gptgodmc*.jar" -File -ErrorAction SilentlyContinue)
if (-not $PluginJar -or -not (Test-Path -LiteralPath $PluginJar)) {
    if ($existingGod.Count -gt 0) {
        Done "Already installed: $($existingGod[0].Name) (no newer jar found next to this script)"
        Add-Report "GPTGOD plugin" "READY" $existingGod[0].Name
    } else {
        Warn "No gptgodmc-*.jar found next to this script."
        Note "Put the jar you downloaded into the setup folder (or build it with gradlew shadowJar), then run this again."
        Add-Report "GPTGOD plugin" "NOT READY" "jar not found; copy gptgodmc-<version>.jar into plugins\"
    }
} else {
    $jarName = Split-Path -Leaf $PluginJar
    $jarHash = Get-Sha256 $PluginJar
    Say "  Jar to install: $PluginJar"
    Note "SHA-256: $jarHash"
    $same = $existingGod | Where-Object { (Get-Sha256 $_.FullName) -eq $jarHash }
    if ($same) {
        Done "This exact jar is already installed ($($same[0].Name))."
        Add-Report "GPTGOD plugin" "READY" $same[0].Name
    } elseif (Ask-YesNo "Install $jarName into the server's plugins folder?" $true) {
        if ($ServerRunning) {
            # Paper replaces plugins\<name>.jar with plugins\update\<name>.jar on the next start.
            $targetName = if ($existingGod.Count -gt 0) { $existingGod[0].Name } else { $jarName }
            $target = Join-Path $PluginsDir "update\$targetName"
            if ($existingGod.Count -eq 0) { $target = Join-Path $PluginsDir $jarName }
            Copy-Shown $PluginJar $target
            Did "Placed at $target - restart the server to load it."
            Add-Report "GPTGOD plugin" $DONE_STATE "loads at the next server restart"
        } else {
            foreach ($old in $existingGod) { Backup-File $old.FullName $ServerDir }
            Copy-Shown $PluginJar (Join-Path $PluginsDir $jarName)
            Did "Installed plugins\$jarName"
            Add-Report "GPTGOD plugin" $DONE_STATE $jarName
        }
    } else {
        Add-Report "GPTGOD plugin" "SKIPPED" "you chose not to install it"
    }
}

# ---- 3. Simple Voice Chat --------------------------------------------------------------------
Step "3. Simple Voice Chat (lets players hear and talk to the god)"
Note "This free server plugin carries voice in game. The god works without it, in text only."
$existingSvc = @(Get-ChildItem -LiteralPath $PluginsDir -Filter "voicechat*.jar" -File -ErrorAction SilentlyContinue)
if ($existingSvc.Count -gt 0) { Note "Found: $($existingSvc[0].Name)" }
$wantSvc = Ask-YesNo $(if ($existingSvc.Count -gt 0) { "Check Modrinth for a newer Simple Voice Chat?" } else { "Download Simple Voice Chat from Modrinth?" }) ($existingSvc.Count -eq 0)
if (-not $wantSvc) {
    if ($existingSvc.Count -gt 0) { Add-Report "Simple Voice Chat" "READY" $existingSvc[0].Name }
    else { Add-Report "Simple Voice Chat" "SKIPPED" "no voice in game until it is installed" }
} else {
    $mcVersion = "26.3"
    $paperJar = Get-ChildItem -LiteralPath $ServerDir -Filter "paper-*.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($paperJar -and $paperJar.Name -match "^paper-(\d+\.\d+(\.\d+)?)") { $mcVersion = $Matches[1] }
    $mcVersion = Ask-Text "Minecraft version of your server" $mcVersion
    $query = "?loaders=" + [uri]::EscapeDataString('["paper"]') + "&game_versions=" + [uri]::EscapeDataString("[`"$mcVersion`"]")
    $apiUrl = $ModrinthApi + $query
    Say ""
    Say "  Ask Modrinth for the newest version: $apiUrl"
    if ($DryRun) {
        DryNote "ask that address (no download in a dry run), then download the jar it names into plugins\"
        Add-Report "Simple Voice Chat" "WOULD DO" "ask Modrinth, then download"
    } elseif (-not (Ask-YesNo "Contact Modrinth?" $true)) {
        Add-Report "Simple Voice Chat" "SKIPPED" "you chose not to contact Modrinth"
    } else {
        try {
            # ForEach-Object unrolls the JSON array (PowerShell 5.1 returns it as one object).
            $versions = @(Invoke-RestMethod -Uri $apiUrl -UseBasicParsing -UserAgent "gptgodmc-setup" -TimeoutSec 30 | ForEach-Object { $_ })
        } catch {
            $versions = @()
            Warn "Modrinth did not answer: $($_.Exception.Message)"
        }
        if ($versions.Count -eq 0) {
            Warn "No Simple Voice Chat build for Paper $mcVersion was found."
            Note "Get it by hand from https://modrinth.com/plugin/simple-voice-chat"
            Add-Report "Simple Voice Chat" $(if ($existingSvc.Count -gt 0) { "READY" } else { "NOT READY" }) "no build found for $mcVersion"
        } else {
            $v = $versions[0]
            $file = @($v.files | Where-Object { $_.primary }) + @($v.files) | Select-Object -First 1
            Say "  Newest: $($v.name) ($($file.filename), published $($v.date_published))"
            if ($existingSvc | Where-Object { $_.Name -eq $file.filename }) {
                Done "You already have this version."
                Add-Report "Simple Voice Chat" "READY" $file.filename
            } elseif ($file.url -notmatch "^https://cdn\.modrinth\.com/") {
                Warn "Unexpected download address $($file.url); not downloading."
                Add-Report "Simple Voice Chat" "NOT READY" "unexpected download address"
            } else {
                $tmp = Join-Path $env:TEMP ("gptgod-setup-" + $file.filename)
                if (Get-Download $file.url $tmp ("{0:N1} MB" -f ($file.size / 1MB))) {
                    $sha512 = (Get-FileHash -Algorithm SHA512 -LiteralPath $tmp).Hash.ToLower()
                    if ($sha512 -ne $file.hashes.sha512) {
                        Remove-Item -Force $tmp
                        Warn "The file's SHA-512 does not match what Modrinth lists. Deleted it."
                        Add-Report "Simple Voice Chat" "NOT READY" "checksum mismatch"
                    } else {
                        Done "SHA-512 matches Modrinth's."
                        if ($ServerRunning -and $existingSvc.Count -gt 0) {
                            $target = Join-Path $PluginsDir ("update\" + $existingSvc[0].Name)
                        } else {
                            foreach ($old in $existingSvc) { Backup-File $old.FullName $ServerDir }
                            $target = Join-Path $PluginsDir $file.filename
                        }
                        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
                        Move-Item -Force $tmp $target
                        Done "Installed $target"
                        Add-Report "Simple Voice Chat" "READY" "$($file.filename)$(if ($ServerRunning) { ' (after restart)' })"
                    }
                } else {
                    Add-Report "Simple Voice Chat" $(if ($existingSvc.Count -gt 0) { "READY" } else { "NOT READY" }) "not downloaded"
                }
            }
        }
    }
}
Note "Players must install the Simple Voice Chat MOD in their own game: $VoiceChatMod"
Note "Voice uses UDP port 24454; open or forward it if players connect from outside your network."

# ---- 4. Kokoro voice server (Docker) ---------------------------------------------------------
Step "4. The god's voice (Kokoro, runs in Docker)"
Note "Kokoro makes the god's speech. It runs in a Docker container on this PC, only reachable from"
Note "this PC (127.0.0.1:$KokoroPort). Without it the god speaks with the built-in Piper voice."
$kokoroOk = $false
if (-not (Ask-YesNo "Set up the Kokoro voice server?" $true)) {
    Add-Report "Kokoro voice" "SKIPPED" "the god will use the Piper backup voice"
} elseif (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Warn "Docker is not installed."
    Say "  Docker Desktop is free for personal use. Install it, start it once, then run this setup again."
    Say "  Official download page: $DockerPage"
    Note "(Installing Docker Desktop asks for administrator rights; this script never does.)"
    if (Ask-YesNo "Open the Docker download page in your browser?" $true) {
        if ($DryRun) { DryNote "open $DockerPage" } else { Start-Process $DockerPage }
    }
    Add-Report "Kokoro voice" "NOT READY" "install Docker Desktop, then run setup again"
} else {
    $dockerReady = $false
    for ($try = 0; $try -lt 3; $try++) {
        & docker info *> $null
        if ($LASTEXITCODE -eq 0) { $dockerReady = $true; break }
        Warn "Docker is installed but not running."
        Say "  Open Docker Desktop from the Start menu and wait until it says 'Engine running'."
        if ($Yes) { break }
        Pause-ForUser "Then press Enter here to try again"
    }
    if (-not $dockerReady) {
        Add-Report "Kokoro voice" "NOT READY" "start Docker Desktop, then run setup again"
    } else {
        Done "Docker is running."
        $cores = [Environment]::ProcessorCount
        $suggest = if ($cores -ge 4) { [Math]::Min(6, $cores - 2) } else { 0 }
        $plan = Get-KokoroPlan
        if ($plan.Gpu) { Done "$($plan.Card) found and Docker can reach it: using the GPU version (fast)." }
        else { Note "$($plan.Why): using the CPU version (slower, fine for a few players)." }
        $exists = $false
        & docker container inspect $KokoroName *> $null
        if ($LASTEXITCODE -eq 0) { $exists = $true }
        if ($exists) {
            $info = (& docker container inspect $KokoroName --format "{{.State.Running}}|{{.HostConfig.RestartPolicy.Name}}|{{.HostConfig.NanoCpus}}|{{.Config.Image}}|{{json .HostConfig.DeviceRequests}}")
            $parts = $info.Split("|")
            $cpuNow = [double]$parts[2] / 1e9
            $isGpu = Test-ImageIsGpu $parts[3] $parts[4]
            Done "Container '$KokoroName' already exists (image $($parts[3]), on the $(if ($isGpu) { 'GPU' } else { 'CPU' }))."
            Note "Running: $($parts[0]); restart policy: $($parts[1]); CPU limit: $(if ($cpuNow -gt 0) { $cpuNow } else { 'none' })"
            $switched = $false
            if (-not $isGpu -and $plan.Gpu) {
                Say "  Your Kokoro runs on the CPU, but this PC's graphics card can run it much faster."
                Note "Switching downloads the GPU version (about 4.6 GB) and replaces the '$KokoroName' container."
                Note "Nothing is lost: the voices are built into the image. If the GPU version fails, the CPU one comes back."
                # Never replaced without a person saying yes (--yes keeps the existing container).
                if (Ask-YesNo "Replace it with the GPU version?" (-not $Yes)) {
                    if ((Invoke-Shown "docker" @("pull", $plan.Image)) -eq 0) {
                        [void](Invoke-Shown "docker" @("rm", "-f", $KokoroName))
                        $runArgs = @("run", "-d", "--name", $KokoroName, "--restart", "unless-stopped", "-p", "127.0.0.1:${KokoroPort}:$KokoroPort", "--gpus", "all", $plan.Image)
                        if ((Invoke-Shown "docker" $runArgs) -eq 0) {
                            $switched = $true
                            Did "Kokoro now runs on the GPU."
                        } else {
                            Warn "The GPU version could not start. Putting the CPU version back."
                            & docker rm -f $KokoroName *> $null
                            $back = @("run", "-d", "--name", $KokoroName, "--restart", "unless-stopped", "-p", "127.0.0.1:${KokoroPort}:$KokoroPort")
                            if ($cpuNow -gt 0) { $back += @("--cpus", "$cpuNow") }
                            [void](Invoke-Shown "docker" ($back + @($parts[3])))
                        }
                    } else { Warn "docker pull failed; keeping the CPU version." }
                }
            }
            if (-not $switched) {
                if ($parts[0] -ne "true") {
                    if (Ask-YesNo "Start it?" $true) { [void](Invoke-Shown "docker" @("start", $KokoroName)) }
                }
                $updateArgs = @()
                if ($parts[1] -ne "unless-stopped" -and (Ask-YesNo "Make it start by itself whenever Docker starts?" $true)) {
                    $updateArgs += @("--restart", "unless-stopped")
                }
                if (-not $isGpu -and $suggest -gt 0 -and $cpuNow -eq 0) {
                    Note "On the CPU, Kokoro can use every core while it speaks, which can make Minecraft (and the voice) stutter."
                    $n = Ask-Text "Limit it to how many CPU cores? (0 = no limit; you have $cores)" "$suggest"
                    if ($n -match "^\d+(\.\d+)?$" -and [double]$n -gt 0) { $updateArgs += @("--cpus", $n) }
                }
                if ($updateArgs.Count -gt 0) {
                    if ((Invoke-Shown "docker" (@("update") + $updateArgs + @($KokoroName))) -eq 0) { Did "Updated $KokoroName (no download, no restart needed)." }
                }
            }
        } else {
            $cpuArgs = @()
            function Ask-CpuLimit {
                if ($suggest -le 0) { return @() }
                Note "On the CPU, Kokoro can use every core while it speaks, which can make Minecraft (and the voice) stutter."
                $n = Ask-Text "Limit it to how many CPU cores? (0 = no limit; you have $cores)" "$suggest"
                if ($n -match "^\d+(\.\d+)?$" -and [double]$n -gt 0) { return @("--cpus", $n) }
                return @()
            }
            if (-not $plan.Gpu) { $cpuArgs = Ask-CpuLimit }
            $port = Get-NetTCPConnection -State Listen -LocalPort $KokoroPort -ErrorAction SilentlyContinue
            if ($port) {
                Warn "Port $KokoroPort is already used by another program, so Kokoro cannot start there."
                Add-Report "Kokoro voice" "NOT READY" "port $KokoroPort is in use by another program"
            } else {
                Say ""
                Say "  Image: $($plan.Image)"
                Say "  From:  https://$($plan.Image.Split(':')[0])  (Kokoro-FastAPI, github.com/remsky/Kokoro-FastAPI)"
                Note "Docker downloads it once (about $(if ($plan.Gpu) { '4.6' } else { '1.5' }) GB) and keeps it."
                if (Ask-YesNo "Download and start the Kokoro voice server?" $true) {
                    $extra = if ($plan.Gpu) { @("--gpus", "all") } else { $cpuArgs }
                    $ok = Start-KokoroContainer $plan.Image $extra
                    if (-not $ok -and $plan.Gpu) {
                        Warn "The GPU version could not start (Docker could not reach the card). Using the CPU version instead (slower)."
                        $ok = Start-KokoroContainer $KokoroCpuImage (Ask-CpuLimit)
                    }
                    if ($ok -and -not $DryRun) { Done "Started $KokoroName." }
                }
            }
        }
        # Wait for it to answer (it loads its voice model for up to a minute after starting).
        if (-not $DryRun) {
            Say "  Checking the voice server..."
            for ($i = 0; $i -lt 30; $i++) {
                if (Test-KokoroVoices) { $kokoroOk = $true; break }
                Start-Sleep -Seconds 3
            }
        } else {
            $kokoroOk = Test-KokoroVoices
        }
    }
}

# ---- 5. Whisper (hearing) --------------------------------------------------------------------
Step "5. The god's hearing (Whisper speech recognition)"
Note "The plugin downloads this model by itself the first time someone speaks. Getting it now"
Note "means the first words are understood at once, and it works on servers without internet."
$configModel = Get-ConfigValue $ConfigFile "whisper-model" "base.en"
if (Ask-YesNo "Download the speech-recognition model now?" $true) {
    Say "  Sizes:"
    foreach ($k in $WhisperModels.Keys) { Say ("    {0,-9} {1}" -f $k, $WhisperModels[$k]) }
    $model = ""
    while (-not $WhisperModels.Contains($model)) {
        $model = Ask-Text "Which one?" $configModel
        if (-not $WhisperModels.Contains($model)) { Warn "Pick one of: $($WhisperModels.Keys -join ', ')"; if ($Yes) { $model = "base.en" } }
    }
    $target = Join-Path $DataDir "whisper\ggml-$model.bin"
    if ((Test-Path $target) -and (Get-Item $target).Length -gt 1MB) {
        Done "Already downloaded: $target"
        Add-Report "Whisper ($model)" "READY" "already downloaded"
    } elseif (Get-Download ($WhisperUrlBase + "$model.bin") $target ($WhisperModels[$model].Split(",")[0])) {
        Add-Report "Whisper ($model)" "READY" "downloaded"
    } else {
        Add-Report "Whisper ($model)" $(if ($DryRun) { "WOULD DO" } else { "LATER" }) $(if ($DryRun) { "download $model" } else { "the plugin downloads it on first use" })
    }
    if ($model -ne $configModel) {
        Warn "Your config uses '$configModel'. To use '$model', set voice.transcription.whisper-model: $model"
        Note "in plugins\gptgodmc\config.yml and run /god reload (this script never edits your config)."
    }
} else {
    Add-Report "Whisper" "LATER" "the plugin downloads it on first use"
}

# ---- 6. Piper (backup voice) ----------------------------------------------------------------
Step "6. Backup voice (Piper)"
Note "If Kokoro is not running, the god speaks with Piper. The plugin installs Piper by itself the"
Note "first time it is needed. Pre-installing only helps on a server without internet."
$piperDir = Join-Path $DataDir "piper"
$piperExe = Get-ChildItem -LiteralPath $piperDir -Filter "piper.exe" -File -Recurse -Depth 3 -ErrorAction SilentlyContinue | Select-Object -First 1
$piperVoice = Get-ConfigValue $ConfigFile "piper-voice" "en_US-ryan-medium"
if ($piperExe) {
    Done "Piper is already installed: $($piperExe.FullName)"
    Add-Report "Piper backup voice" "READY" "installed"
} elseif ($piperVoice -notmatch "^[a-z]{2}(_[A-Z]{2})?-.+-[a-z_]+$") {
    Note "Your config uses a custom Piper voice ($piperVoice); skipping."
    Add-Report "Piper backup voice" "LATER" "custom voice in config"
} elseif (Ask-YesNo "Pre-install Piper now?" $false) {
    $zip = Join-Path $env:TEMP "gptgod-setup-piper_windows_amd64.zip"
    if (Get-Download ($PiperRelease + "piper_windows_amd64.zip") $zip "22 MB") {
        Expand-Archive -LiteralPath $zip -DestinationPath $piperDir -Force
        Remove-Item -Force $zip
        Done "Unpacked Piper into $piperDir"
    }
    # Same address the plugin builds (Synthesizer.voiceUrl): <lang family>/<lang>/<name>/<quality>/<voice>.onnx
    $p = $piperVoice.Split("-")
    $voiceUrl = $PiperVoicesBase + $p[0].Split("_")[0] + "/" + $p[0] + "/" + (($p[1..($p.Length - 2)]) -join "-") + "/" + $p[-1] + "/" + $piperVoice + ".onnx"
    $voiceFile = Join-Path $piperDir "voices\$piperVoice.onnx"
    if (-not (Test-Path $voiceFile)) {
        [void](Get-Download ($voiceUrl + ".json") ($voiceFile + ".json") "5 KB")
        [void](Get-Download $voiceUrl $voiceFile "60 MB")
    }
    $piperExe = Get-ChildItem -LiteralPath $piperDir -Filter "piper.exe" -File -Recurse -Depth 3 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($piperExe -and (Test-Path $voiceFile) -and (Test-Path "$voiceFile.json")) {
        Add-Report "Piper backup voice" "READY" "pre-installed ($piperVoice)"
    } else {
        Add-Report "Piper backup voice" $(if ($DryRun) { "SKIPPED" } else { "LATER" }) "not complete; the plugin installs it when first needed"
    }
} else {
    Add-Report "Piper backup voice" "LATER" "the plugin installs it when first needed"
}

# ---- 7. Mods for players (Fabric) -----------------------------------------------------------
Step "7. Mods for players (Fabric)"
Note "Players on Minecraft Java add Fabric and a few mods to hear the god. This puts them in one folder"
Note "and one zip you can send to friends: $ServerDir\for-players"
if (Ask-YesNo "Get the player mods ready?" $true) {
    $fp = Join-Path $ServerDir "for-players"
    $fpMods = Join-Path $fp "mods"
    $installerFile = Join-Path $fp "fabric-installer.jar"
    $loaderVer = ""
    $loader = @(Get-Json $FabricLoaders) | Where-Object { $_.loader.stable } | Select-Object -First 1
    if ($loader) { $loaderVer = $loader.loader.version; Note "Fabric Loader for ${McVersion}: $loaderVer" }
    if (Test-Path $installerFile) {
        Done "Fabric installer already here"
    } else {
        $inst = @(Get-Json $FabricInstallers) | Where-Object { $_.stable } | Select-Object -First 1
        if ($inst -and $inst.url -match "^https://maven\.fabricmc\.net/") {
            $sha = (Get-Json "$($inst.url).sha256")
            [void](Get-VerifiedDownload $inst.url $installerFile "1 MB" "SHA256" ([string]$sha).Trim())
        } elseif ($DryRun) {
            Say "  Then download the Fabric installer it names (maven.fabricmc.net, ~1 MB) and check its SHA-256"
        }
    }
    Say "  Mods: $(($PlayerMods.Values) -join '; ')"
    $mods = @(Resolve-ModrinthMods @($PlayerMods.Keys) "fabric" $McVersion)
    if ($DryRun) { Say "  Then download each mod and its required mods from cdn.modrinth.com into $fpMods, checking each SHA-512" }
    $wanted = @()
    foreach ($m in $mods) {
        $target = Join-Path $fpMods $m.File.filename
        $wanted += $m.File.filename
        if (Test-Path $target) { Done "Already here: $($m.File.filename)"; continue }
        Say "  $($m.Name)"
        [void](Get-VerifiedDownload $m.File.url $target ("{0:N1} MB" -f ($m.File.size / 1MB)) "SHA512" $m.File.hashes.sha512)
    }
    # The optional GPTGOD client mod, only a finished build (the source tree may hold a stub).
    $clientMod = @()
    foreach ($dir in @($SetupDir, (Join-Path $RepoDir "client-mod\build\libs"))) {
        if (Test-Path $dir) { $clientMod += Get-ChildItem -LiteralPath $dir -Filter "gptgod-client-*.jar" -File | Where-Object { $_.Name -notmatch "-(sources|dev)\.jar$" -and $_.Length -gt 20KB } }
    }
    $clientMod = $clientMod | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($clientMod) {
        $wanted += $clientMod.Name
        Copy-Shown $clientMod.FullName (Join-Path $fpMods $clientMod.Name)
        Did "Added the GPTGOD client mod ($($clientMod.Name)): glowing effects and a god HUD"
    } else {
        Note "The GPTGOD client mod (optional extra effects) is not included: no finished build was found."
    }
    # Older versions this script put in for-players\mods go to for-players\old (never deleted).
    if (-not $DryRun -and (Test-Path $fpMods) -and $wanted.Count -gt 0) {
        foreach ($old in Get-ChildItem -LiteralPath $fpMods -Filter "*.jar" -File | Where-Object { $wanted -notcontains $_.Name }) {
            New-Item -ItemType Directory -Force -Path (Join-Path $fp "old") | Out-Null
            Move-Item -Force $old.FullName (Join-Path $fp "old")
        }
    }
    $howTo = @(
        "How to join the GPTGOD server with voice (Minecraft Java $McVersion)",
        "",
        "1. Install Fabric: double-click fabric-installer.jar (it needs Java; if it does not open, get it",
        "   from https://adoptium.net/). Choose Client, Minecraft $McVersion, press Install.",
        "2. Press Windows+R, type %appdata%\.minecraft and press Enter. Open (or make) the folder 'mods'",
        "   and copy every file from this zip's mods folder into it.",
        "3. Open the Minecraft Launcher, pick the profile 'fabric-loader-$McVersion', press Play.",
        "4. On the title screen press 'Mods': Simple Voice Chat must be in the list.",
        "5. Multiplayer > Add Server > the address the host gave you. Accept the texture pack when asked",
        "   (it is the god's appearance). Press V in game for voice chat settings.",
        "",
        "Without these mods you can still join and play; you just will not hear the god's voice.",
        "Mods from: Fabric (fabricmc.net) and Modrinth (modrinth.com).")
    if ($DryRun) {
        DryNote "write $fp\HOW-TO-JOIN.txt and zip everything into $fp\GPTGOD-player-mods.zip"
        Add-Report "Player mods" "WOULD DO" "$fp"
    } elseif (Test-Path $fpMods) {
        Set-Content -LiteralPath (Join-Path $fp "HOW-TO-JOIN.txt") -Value $howTo -Encoding ASCII
        $zipItems = @((Join-Path $fp "HOW-TO-JOIN.txt"), $fpMods)
        if (Test-Path $installerFile) { $zipItems += $installerFile }
        Compress-Archive -Path $zipItems -DestinationPath (Join-Path $fp "GPTGOD-player-mods.zip") -Force
        Done "Send friends this zip: $fp\GPTGOD-player-mods.zip"
        Add-Report "Player mods" "READY" "for-players\GPTGOD-player-mods.zip"
    } else {
        Add-Report "Player mods" "NOT READY" "nothing downloaded"
    }

    # The same mods for Minecraft on this PC.
    $mcDir = Join-Path $env:APPDATA ".minecraft"
    if (-not (Test-Path $mcDir)) {
        Note "Minecraft Java is not installed on this PC (no .minecraft folder), so nothing is installed here."
    } elseif (Ask-YesNo "Also install Fabric and these mods into Minecraft on this PC?" $true) {
        $toolJava = if ($ServerJava) { $ServerJava } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
        if (-not $toolJava -and -not $DryRun) {
            Warn "Java is needed to run the Fabric installer; skipped."
        } else {
            $instArgs = @("-jar", $installerFile, "client", "-dir", $mcDir, "-mcversion", $McVersion)
            if ($loaderVer) { $instArgs += @("-loader", $loaderVer) }
            if (-not (Test-Path (Join-Path $mcDir "launcher_profiles.json"))) { $instArgs += "-noprofile" }
            if ($DryRun -or (Test-Path $installerFile)) { [void](Invoke-Shown $(if ($toolJava) { $toolJava } else { "java" }) $instArgs) }
            $mcMods = Join-Path $mcDir "mods"
            $backup = Join-Path $mcDir "mods-backup-gptgod"
            foreach ($jar in @(Get-ChildItem -LiteralPath $fpMods -Filter "*.jar" -File -ErrorAction SilentlyContinue)) {
                $target = Join-Path $mcMods $jar.Name
                if (Test-Path $target) { continue }
                # An older version of the same mod would clash: move it aside first.
                $stem = ($jar.Name -replace '[-_+]?\d.*$', '')
                foreach ($old in @(Get-ChildItem -LiteralPath $mcMods -Filter "$stem*.jar" -File -ErrorAction SilentlyContinue)) {
                    if (($old.Name -replace '[-_+]?\d.*$', '') -eq $stem) {
                        if ($DryRun) { DryNote "move $($old.Name) to $backup (older version)" }
                        else { New-Item -ItemType Directory -Force -Path $backup | Out-Null; Move-Item -Force $old.FullName $backup; Note "Moved older $($old.Name) to $backup" }
                    }
                }
                Copy-Shown $jar.FullName $target
            }
            if ($DryRun) { DryNote "copy the mods into $mcMods" }
            Did "Installed. In the Minecraft Launcher pick 'fabric-loader-$McVersion' and press Play."
            Add-Report "Mods on this PC" $DONE_STATE "launcher profile fabric-loader-$McVersion"
        }
    }
} else {
    Add-Report "Player mods" "SKIPPED" "players can still join, but will not hear the god"
}

# ---- 8. Start the server, API key -----------------------------------------------------------
Step "8. Start the server and give the god its mind (API key)"
Say "  The god thinks with Claude, which needs an Anthropic API key: https://console.anthropic.com/ > API Keys."
Say "  (Add a little credit and set a monthly limit there too.) This setup never asks for your key"
Say "  and never writes it anywhere: you type it into the SERVER window yourself, as shown below."
Note "Other AI providers: see the 'Choosing the AI' section of setup\TUTORIAL.md."
if ($env:ANTHROPIC_API_KEY) { Note "ANTHROPIC_API_KEY is set in this window (its value was not read or shown)." }
$starter = @("start.bat", "run.bat", "launch.bat") | ForEach-Object { Join-Path $ServerDir $_ } | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($DryRun -and -not $starter -and $makeServer) { $starter = Join-Path $ServerDir "start.bat" }
$started = $false
$now = Test-ServerRunning $ServerDir
if ($now.Running) {
    Note "The server is already running."
    $started = $true
} elseif ($now.PortTaken -and -not $DryRun) {
    Warn "Not starting it: port $($now.Port) is used by another program (see step 1)."
} elseif (-not $starter) {
    Note "Start your server the way you usually do."
} elseif ((Get-PropValue $eulaFile "eula" "") -ne "true" -and -not $DryRun) {
    Warn "The EULA is not accepted, so the server cannot start yet."
} elseif (Ask-YesNo "Start the server now? It opens in its own window." $true) {
    Say ""
    Say "  WINDOWS MAY ASK whether Java may use networks: tick BOTH 'Private' and 'Public', then Allow."
    Say "  (Ticking only one is what locks friends out. The final check below tells you if this happened.)"
    if ($DryRun) {
        DryNote "start $starter in a new window and wait for 'Done (' in logs\latest.log (up to 5 minutes)"
    } else {
        $t0 = Get-Date
        Start-Process -FilePath $starter -WorkingDirectory $ServerDir
        $log = Join-Path $ServerDir "logs\latest.log"
        Say "  Waiting for the server to finish starting (the first start downloads libraries: 1-3 minutes)..."
        for ($i = 0; $i -lt 100; $i++) {
            Start-Sleep -Seconds 3
            if ((Test-Path $log) -and (Get-Item $log).LastWriteTime -ge $t0) {
                $text = Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue
                if ($text -match "Done \(") { $started = $true; break }
                if ($text -match "agree to the EULA") { Warn "The server stopped: the EULA is not accepted."; break }
            }
            if ($i % 10 -eq 9) { Note "still starting... ($([int]((Get-Date) - $t0).TotalSeconds) s)" }
        }
        if ($started) { Done "The server is up." } else { Warn "It has not finished starting yet. Look at the server window for errors." }
    }
}
Say ""
Say "  In the SERVER window (the black console, NOT the game chat) type these two lines:"
Say "      gptgod setkey <your key>"
Say "      op <your Minecraft name>"
Say "  The first gives the god its mind; the second lets you use /god manager in game."
if ($started -and -not $Yes) { Pause-ForUser "Press Enter here once you have typed them (the check below confirms it)" }
Add-Report "API key" "YOU" "type 'gptgod setkey <key>' in the server console"

} # end of: if (-not $Check)

# ---- What this run did ----------------------------------------------------------------------
if (-not $Check -and $kokoroOk -and -not ($Report | Where-Object { $_.Part -eq "Kokoro voice" })) {
    Add-Report "Kokoro voice" "READY" "http://127.0.0.1:$KokoroPort, voice $KokoroVoice"
}
if ($Report.Count -gt 0) {
    Say ""
    Say "What this run did"
    Say "-----------------"
    foreach ($r in $Report) {
        $color = switch ($r.State) { "READY" { "Green" } "NOT READY" { "Red" } "YOU" { "Yellow" } default { "Gray" } }
        Write-Host ("  {0,-20} {1,-10} {2}" -f $r.Part, $r.State, $r.Detail) -ForegroundColor $color
    }
    Say "  READY = done.  LATER = the plugin does it by itself.  YOU = a step only you can do."
    if ($DryRun) { Say "  WOULD DO = what a real run would do." }
}

# =============================================================================================
#  9. Final check. Read-only: it looks at files, ports, the server log and the firewall, and
#  changes nothing. Every red line comes with its fix. Run it alone with check-windows.bat.
# =============================================================================================
Step "9. Final check"
Note "Looking at your server folder, its last log, open ports, Docker and the Windows firewall..."

$Checks = New-Object System.Collections.Generic.List[object]
# State: OK (green), FIX (red: something to do), WARN (yellow: worth a look), INFO (gray)
function Add-Check([string]$state, [string]$what, [string]$detail, [string[]]$fix) {
    $Checks.Add([pscustomobject]@{ State = $state; What = $what; Detail = $detail; Fix = $fix })
}

# "key: value" under "section:" in a YAML file (one level). Only reads the keys it is asked for:
# ports, bind addresses and on/off switches. Never the API key.
function Get-SectionValue([string]$file, [string]$section, [string]$key, [string]$default) {
    if (-not (Test-Path -LiteralPath $file)) { return $default }
    $inSection = $false
    $indent = -1
    foreach ($line in Get-Content -LiteralPath $file) {
        if ($line -match '^\s*(#.*)?$') { continue }
        $lead = $line.Length - $line.TrimStart().Length
        if ($inSection) {
            if ($lead -le $indent) { $inSection = $false }
            elseif ($line -match "^\s*$([regex]::Escape($key)):\s*`"?([^`"#\s]+)") { return $Matches[1] }
        }
        if (-not $inSection -and $line -match "^\s*$([regex]::Escape($section)):\s*(#.*)?$") { $inSection = $true; $indent = $lead }
    }
    return $default
}


function Test-TcpListen([int]$port) {
    try { return @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction Stop).Count -gt 0 } catch { return $false }
}
function Test-UdpListen([int]$port) {
    try { return @(Get-NetUDPEndpoint -LocalPort $port -ErrorAction Stop).Count -gt 0 } catch { return $false }
}

$LogFile = Join-Path $ServerDir "logs\latest.log"
$Log = @()
if (Test-Path -LiteralPath $LogFile) { $Log = @(Get-Content -LiteralPath $LogFile -ErrorAction SilentlyContinue) }
function LogHas([string]$text) { return @($Log | Where-Object { $_.Contains($text) }).Count -gt 0 }

$GamePort  = [int](Get-PropValue (Join-Path $ServerDir "server.properties") "server-port" "25565")
$RunState  = Test-ServerRunning $ServerDir
$Running   = $RunState.Running
$PackPort  = [int](Get-SectionValue $ConfigFile "resource-pack" "port" "8770")
$ViewerOn  = (Get-SectionValue $ConfigFile "viewer" "enabled" "true") -eq "true"
$ViewerPort = [int](Get-SectionValue $ConfigFile "viewer" "port" "4673")
$ViewerBind = Get-SectionValue $ConfigFile "viewer" "bind" "127.0.0.1"
$VoicePort = [int](Get-PropValue (Join-Path $PluginsDir "voicechat\voicechat-server.properties") "port" "24454")
$GeyserJar = Get-ChildItem -LiteralPath $PluginsDir -Filter "Geyser*.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1
$GeyserPort = 19132
if ($GeyserJar) {
    $gcfg = Get-ChildItem -LiteralPath $PluginsDir -Directory -Filter "Geyser*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($gcfg) { $GeyserPort = [int](Get-SectionValue (Join-Path $gcfg.FullName "config.yml") "bedrock" "port" "19132") }
}
$NeedsRun = "start the server, then run this check again"

# -- Java ------------------------------------------------------------------------------------
$JavaExe = $null
if ($Running) {
    try {
        $procId = (Get-NetTCPConnection -State Listen -LocalPort $GamePort -ErrorAction Stop | Select-Object -First 1).OwningProcess
        $JavaExe = (Get-Process -Id $procId -ErrorAction Stop).Path
        # Only trust it if the port really belongs to Java (another program may hold the port).
        if ($JavaExe -notmatch '\\javaw?\.exe$') { $JavaExe = $null }
    } catch { $JavaExe = $null }
}
if (-not $JavaExe) { $JavaExe = Find-JavaIn (Join-Path $ServerDir "java") }
if (-not $JavaExe) { $c = Get-Command java -ErrorAction SilentlyContinue; if ($c) { $JavaExe = $c.Source } }
if (-not $JavaExe) {
    Add-Check "FIX" "Java 25" "Java was not found" @("Install Temurin 25 from https://adoptium.net/ (tick 'Add to PATH'), then open a new window.")
} else {
    $major = Get-JavaMajor $JavaExe
    $how = if ($Running) { "the server runs" } elseif ($JavaExe.StartsWith($ServerDir, [StringComparison]::OrdinalIgnoreCase)) { "in the server folder:" } else { "first on PATH:" }
    if ($major -ge 25) { Add-Check "OK" "Java 25" "Java $major ($how $JavaExe)" @() }
    else { Add-Check "FIX" "Java 25" "Java $major found ($how $JavaExe); Paper 26.3 needs Java 25" @("Install Temurin 25 from https://adoptium.net/ and use it in your start script.") }
}

# -- Server files ----------------------------------------------------------------------------
$paper = Get-ChildItem -LiteralPath $ServerDir -Filter "*.jar" -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "paper" } | Select-Object -First 1
if ($paper) { Add-Check "OK" "Paper server" $paper.Name @() }
else { Add-Check "WARN" "Paper server" "no paper*.jar in the server folder" @("Download Paper 26.3 from https://papermc.io/downloads/paper (fine to ignore if your start script uses another name).") }

$eula = Get-PropValue (Join-Path $ServerDir "eula.txt") "eula" ""
if ($eula -eq "true") { Add-Check "OK" "EULA" "accepted" @() }
elseif ($eula -eq "") { Add-Check "INFO" "EULA" "no eula.txt yet" @("Start the server once; it creates eula.txt.") }
else { Add-Check "FIX" "EULA" "not accepted, so the server stops at once" @("Open eula.txt, read the EULA at the link inside, and change eula=false to eula=true.") }

# -- Running and fully started ---------------------------------------------------------------
if ($Running) {
    if (LogHas "Done (") { Add-Check "OK" "Server running" "port $GamePort, fully started" @() }
    else { Add-Check "WARN" "Server running" "port $GamePort is open but the log has no 'Done (' line yet" @("It is still starting. Wait for 'Done (...)! For help, type help' in the console before joining (the first start downloads libraries and takes longer).") }
} elseif ($RunState.PortTaken) {
    Add-Check "FIX" "Server running" "not running, and port $GamePort is taken by another program" @("Another server on this PC may hold it. Stop that one, or set another server-port in server.properties.")
} else {
    Add-Check "INFO" "Server running" "not running now (nothing on port $GamePort)" @("Some checks below need it running: start it, then run check-windows.bat again.")
}

# -- The plugin ------------------------------------------------------------------------------
$godJar = Get-ChildItem -LiteralPath $PluginsDir -Filter "gptgodmc*.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1
$pendingUpdates = @(Get-ChildItem -LiteralPath (Join-Path $PluginsDir "update") -Filter "*.jar" -File -ErrorAction SilentlyContinue)
if (-not $godJar) { Add-Check "FIX" "GPTGOD plugin" "no gptgodmc jar in plugins\" @("Run setup-windows.bat, or copy gptgodmc-<version>.jar into the plugins folder.") }
elseif (LogHas "an omniscient god for this world") { Add-Check "OK" "GPTGOD plugin" "$($godJar.Name), loaded at the last start" @() }
elseif ($Log.Count -gt 0 -and (LogHas "Done (")) { Add-Check "FIX" "GPTGOD plugin" "$($godJar.Name) is there but did not load at the last start" @("Look in logs\latest.log for lines with 'gptgodmc' and 'ERROR'. Usual causes: not Java 25, not Paper 26.3, or the server was started before the jar was added (restart it).") }
else { Add-Check "INFO" "GPTGOD plugin" "$($godJar.Name) (not started with it yet)" @($NeedsRun) }
if ($pendingUpdates.Count -gt 0) { Add-Check "INFO" "Waiting updates" "$($pendingUpdates.Count) jar(s) in plugins\update" @("Restart the server to load them.") }

# -- API key (from the log banner; the key itself is never read) -------------------------------
$keyFound = $Log | Where-Object { $_ -match "API key: found in (.+)$" } | Select-Object -Last 1
if (LogHas "API key saved to config.yml") { Add-Check "OK" "API key" "set with gptgod setkey" @() }
elseif ($keyFound) { Add-Check "OK" "API key" ("found in " + ([regex]::Match($keyFound, "API key: found in (.+)$").Groups[1].Value)) @() }
elseif (LogHas "API key: MISSING") { Add-Check "FIX" "API key" "the server says MISSING: the god cannot think" @("Get a key at https://console.anthropic.com/ (API Keys), then type in the SERVER CONSOLE:", "    gptgod setkey <your key>") }
else { Add-Check "INFO" "API key" "unknown until the server has started with the plugin" @($NeedsRun) }

# -- Voice chat --------------------------------------------------------------------------------
$svcJar = Get-ChildItem -LiteralPath $PluginsDir -Filter "voicechat*.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $svcJar) {
    Add-Check "WARN" "Voice chat plugin" "Simple Voice Chat is not installed (the god is text only)" @("Run setup-windows.bat and say yes to Simple Voice Chat.")
} else {
    if (LogHas "Voice enabled:") { Add-Check "OK" "Voice chat plugin" "$($svcJar.Name), the god can hear and speak" @() }
    elseif (LogHas "Simple Voice Chat is not installed") { Add-Check "FIX" "Voice chat plugin" "$($svcJar.Name) is there but was not loaded at the last start" @("Restart the server.") }
    else { Add-Check "INFO" "Voice chat plugin" $svcJar.Name @() }
    if ($Running) {
        if (Test-UdpListen $VoicePort) { Add-Check "OK" "Voice port" "UDP $VoicePort open" @() }
        else { Add-Check "FIX" "Voice port" "nothing on UDP $VoicePort" @("Look in logs\latest.log for 'voicechat' errors; another program may use the port.") }
    }
}

# -- Kokoro ------------------------------------------------------------------------------------
$kInfo = ""
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $kInfo = (cmd.exe /c "docker container inspect $KokoroName --format `"{{.Config.Image}}|{{json .HostConfig.DeviceRequests}}`" 2>nul" | Out-String).Trim()
}
$kMode = "CPU"
if ($kInfo) { $kp = $kInfo.Split("|"); if (Test-ImageIsGpu $kp[0] $kp[1]) { $kMode = "GPU" } }
if (Test-KokoroVoices) {
    $speed = ""
    $state = "OK"
    $fix = @()
    if (-not $DryRun) {
        $m = Measure-KokoroSpeech
        if (-not $m) { $state = "WARN"; $speed = ", but a test sentence failed"; $fix = @("See: docker logs $KokoroName") }
        else {
            $speed = ", made {0:N1} s of speech in {1:N1} s" -f $m.Audio, $m.Seconds
            if ($m.Audio -gt 0 -and $m.Seconds -gt $m.Audio) {
                $state = "WARN"
                $fix = @("Slower than real time, so the god's voice will lag. Close other heavy programs, give Kokoro more CPU cores", "(docker update --cpus <n> $KokoroName), or use an NVIDIA graphics card (run setup again).")
            }
        }
    }
    Add-Check $state "Kokoro voice" "on the $kMode, answers at 127.0.0.1:$KokoroPort ($KokoroVoice)$speed" $fix
    $planNow = Get-KokoroPlan
    if ($kMode -eq "CPU" -and $planNow.Gpu) {
        Add-Check "WARN" "Kokoro GPU" "$($planNow.Card) could run the voice much faster" @("Run setup-windows.bat again and say yes to 'Replace it with the GPU version?'")
    } elseif ($kMode -eq "CPU" -and $planNow.Why -match "found, but") {
        Add-Check "INFO" "Kokoro GPU" "not used" @("$($planNow.Why).")
    }
} else {
    if ($kInfo) { Add-Check "FIX" "Kokoro voice" "container $KokoroName ($kMode) exists but does not answer" @("Start Docker Desktop, then: docker start $KokoroName  (it needs about a minute; see docker logs $KokoroName)") }
    else { Add-Check "WARN" "Kokoro voice" "not set up: the god speaks with the plainer Piper voice" @("Optional. Run setup-windows.bat and say yes to Kokoro (needs Docker Desktop).") }
}

# -- Whisper -----------------------------------------------------------------------------------
$wm = Get-ConfigValue $ConfigFile "whisper-model" "base.en"
if (Test-Path (Join-Path $DataDir "whisper\ggml-$wm.bin")) { Add-Check "OK" "Whisper ($wm)" "downloaded" @() }
else { Add-Check "INFO" "Whisper ($wm)" "not downloaded yet; the plugin gets it the first time someone speaks" @() }

# -- Texture pack (the god's skin) ------------------------------------------------------------
$textures = Get-ConfigValue $ConfigFile "textures" "custom"
$packUrl = Get-SectionValue $ConfigFile "resource-pack" "url" ""
$packSend = Get-SectionValue $ConfigFile "resource-pack" "send" "true"
$packFails = @($Log | Where-Object { $_.Contains("could not load the vessel's resource pack") }).Count
if ($textures -ne "custom" -or $packSend -eq "false" -or $packUrl) {
    Add-Check "INFO" "Texture pack" "not served by the plugin (vessel.textures / resource-pack settings)" @()
} elseif ($Running) {
    if (Test-TcpListen $PackPort) { Add-Check "OK" "Texture pack" "served on TCP $PackPort" @() }
    else { Add-Check "FIX" "Texture pack" "nothing on TCP $PackPort, so players see the block body" @("Look for 'Vessel resource pack' in logs\latest.log; another program may use port $PackPort (change vessel.resource-pack.port).") }
}
if ($packFails -gt 0) {
    Add-Check "WARN" "Texture pack" "$packFails player download(s) failed at the last start" @("Players must reach TCP $PackPort on the address they join with: allow it in the firewall and forward it like the game port,", "or set vessel.resource-pack.public-host. A player who pressed 'No' sees the block body; see TUTORIAL.md.")
}

# -- Mind viewer -------------------------------------------------------------------------------
if ($ViewerOn -and $Running) {
    if (Test-TcpListen $ViewerPort) {
        if ($ViewerBind -eq "127.0.0.1" -or $ViewerBind -eq "localhost") { Add-Check "OK" "Mind viewer" "open http://127.0.0.1:$ViewerPort/ in a browser on this PC" @() }
        else { Add-Check "WARN" "Mind viewer" "reachable from the network (viewer.bind $ViewerBind)" @("Anyone with its token can read and edit the god's mind. Keep viewer.bind 127.0.0.1 unless you need it.") }
    } else { Add-Check "WARN" "Mind viewer" "nothing on TCP $ViewerPort" @("Look for 'Mind viewer' in logs\latest.log; another program may use the port (viewer.port).") }
}

# -- Bedrock (Geyser) --------------------------------------------------------------------------
if ($GeyserJar) {
    if (-not $Running) { Add-Check "INFO" "Bedrock (Geyser)" "$($GeyserJar.Name), UDP $GeyserPort" @($NeedsRun) }
    elseif (Test-UdpListen $GeyserPort) { Add-Check "OK" "Bedrock (Geyser)" "UDP $GeyserPort open for Bedrock players" @() }
    else { Add-Check "FIX" "Bedrock (Geyser)" "nothing on UDP $GeyserPort" @("Look for 'Geyser' errors in logs\latest.log.") }
}

# -- Other plugins -----------------------------------------------------------------------------
if (Get-ChildItem -LiteralPath $PluginsDir -Filter "EssentialsX*.jar" -File -ErrorAction SilentlyContinue) {
    Add-Check "INFO" "EssentialsX" "found: it has its own /god (god mode)" @("GPTGOD takes /god back at start; Essentials' god mode moves to /egod.")
}
if (Get-ChildItem -LiteralPath $PluginsDir -Filter "LuckPerms*.jar" -File -ErrorAction SilentlyContinue) {
    Add-Check "INFO" "LuckPerms" "found" @("Let a non-op admin command the god:  lp user <name> permission set gptgodmc.admin true")
}

# -- Windows firewall --------------------------------------------------------------------------
# The trap: when Windows first asks "allow Java?", only the network type you tick is allowed. A
# network marked Public (and many VPN / tunnel adapters are) then silently blocks every player.
try {
    $publicNets = @(Get-NetConnectionProfile -ErrorAction Stop | Where-Object { $_.NetworkCategory -eq "Public" })
    $fwPublicOn = (Get-NetFirewallProfile -Name Public -ErrorAction Stop).Enabled -eq $true
    if (-not $JavaExe) {
        Add-Check "INFO" "Windows firewall" "Java not found, so not checked" @()
    } else {
        $javaNorm = $JavaExe.ToLower()
        $rules = @(Get-NetFirewallApplicationFilter -ErrorAction Stop |
            Where-Object { $_.Program -and ([Environment]::ExpandEnvironmentVariables($_.Program)).ToLower() -eq $javaNorm } |
            Get-NetFirewallRule | Where-Object { $_.Enabled -eq "True" -and $_.Direction -eq "Inbound" })
        $blocks = @($rules | Where-Object { $_.Action -eq "Block" })
        $allowPublic = @($rules | Where-Object { $_.Action -eq "Allow" -and ($_.Profile.ToString() -match "Any|Public") })
        $netNames = ($publicNets | ForEach-Object { $_.InterfaceAlias }) -join ", "
        $allowCmd = "New-NetFirewallRule -DisplayName 'Minecraft server (Java)' -Direction Inbound -Program '$JavaExe' -Action Allow -Profile Any"
        if ($blocks.Count -gt 0) {
            Add-Check "FIX" "Windows firewall" ("BLOCKS Java with rule(s): " + (($blocks | ForEach-Object { $_.DisplayName } | Select-Object -Unique) -join ", ")) @(
                "Players cannot connect while this is on (a block rule beats any allow rule). Open PowerShell as administrator and run:",
                "    Get-NetFirewallApplicationFilter -Program '$JavaExe' | Get-NetFirewallRule | Where-Object Action -eq Block | Disable-NetFirewallRule",
                "    $allowCmd")
        } elseif ($fwPublicOn -and $publicNets.Count -gt 0 -and $allowPublic.Count -eq 0) {
            Add-Check "FIX" "Windows firewall" "Java is not allowed on Public networks, and these are Public: $netNames" @(
                "Players arriving through them are blocked. Open PowerShell as administrator and run:",
                "    $allowCmd",
                "(Or, at home only: Settings > Network & internet > your network > Network profile: Private.)")
        } elseif (@($rules | Where-Object { $_.Action -eq "Allow" }).Count -eq 0) {
            Add-Check "WARN" "Windows firewall" "no rule allows $JavaExe yet" @("Windows asks the first time the server starts; tick both Private and Public. Or run as administrator:", "    $allowCmd")
        } else {
            Add-Check "OK" "Windows firewall" "allows this Java$(if ($publicNets.Count -gt 0) { ' (Public networks too)' })" @()
        }
    }
} catch {
    Add-Check "INFO" "Windows firewall" "could not be read ($($_.Exception.Message))" @()
}

# -- Internet / DNS ----------------------------------------------------------------------------
# A DNS lookup only (nothing is sent to Anthropic). Filters (Pi-hole, NextDNS, router "safe
# browsing", antivirus web shields) answer 0.0.0.0 or nothing for domains they block.
try {
    $ips = @(Resolve-DnsName -Name "api.anthropic.com" -Type A -DnsOnly -ErrorAction Stop | Where-Object { $_.IPAddress } | ForEach-Object { $_.IPAddress })
    $bad = @($ips | Where-Object { $_ -match '^(0\.|127\.|10\.|192\.168\.)' })
    if ($ips.Count -eq 0 -or $bad.Count -gt 0) {
        Add-Check "FIX" "Internet (DNS)" "api.anthropic.com resolves to '$($ips -join ', ')': a DNS filter blocks it" @("Allow api.anthropic.com in your Pi-hole / NextDNS / router filter / antivirus web shield, or use another DNS server.")
    } else { Add-Check "OK" "Internet (DNS)" "api.anthropic.com found" @() }
} catch {
    Add-Check "FIX" "Internet (DNS)" "api.anthropic.com cannot be looked up" @("Check the internet connection. If other sites work, a DNS filter (Pi-hole, NextDNS, router, antivirus) blocks it: allow api.anthropic.com.")
}

# -- Print -------------------------------------------------------------------------------------
Say ""
Say "Checklist"
Say "---------"
foreach ($c in $Checks) {
    $color = switch ($c.State) { "OK" { "Green" } "FIX" { "Red" } "WARN" { "Yellow" } default { "Gray" } }
    $tag = switch ($c.State) { "OK" { "[ OK ]" } "FIX" { "[FIX!]" } "WARN" { "[WARN]" } default { "[info]" } }
    Write-Host ("  {0} {1,-18} {2}" -f $tag, $c.What, $c.Detail) -ForegroundColor $color
    if ($c.State -ne "OK") { foreach ($f in $c.Fix) { if ($f) { Write-Host "                            $f" } } }
}
$toFix = @($Checks | Where-Object { $_.State -eq "FIX" }).Count
$toLook = @($Checks | Where-Object { $_.State -eq "WARN" }).Count
Say ""
if ($toFix -eq 0) { Write-Host "  Nothing red. $(if ($toLook) { "$toLook yellow line(s) are optional or worth a look." })" -ForegroundColor Green }
else { Write-Host "  $toFix red line(s) to fix (each says how), then run check-windows.bat again." -ForegroundColor Red }
Say ""
Say "Letting friends join from other places: forward these ports on your router to this PC (see TUTORIAL.md):"
Say "  TCP $GamePort (game)   TCP $PackPort (god's texture pack)   UDP $VoicePort (voice chat)$(if ($GeyserJar) { "   UDP $GeyserPort (Bedrock)" })"
Say "  Never forward $ViewerPort (mind viewer) or $KokoroPort (Kokoro): they are for this PC only."
Say ""
Say "Players: the voice chat MOD is installed in each player's own game: $VoiceChatMod"
if (-not $Check) { Say "Next: start (or restart) the server, run  gptgod setkey <key>  in its console, join, and type  /pray hello" }
if ($DryRun) { Say ""; Say "That was a dry run. Nothing was changed. Run without --dry-run to do it." }
