# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GPTGOD is a Minecraft Bukkit plugin that integrates GPT as an AI "god" entity that observes player actions and interacts with the game world. It uses OpenAI's APIs for chat completion, text-to-speech, and voice transcription to create an interactive deity that rewards or punishes players based on their behavior.

## Build Commands

```bash
# Build the plugin (creates a fat jar with all dependencies)
./gradlew shadowJar

# Output location
# build/libs/gptgodmc-<version>.jar

# Clean build artifacts
./gradlew clean
```

## Development Setup

1. Place OpenAI API key in `plugins/gptgodmc/config.yml` after first run
2. Requires Paper MC server 1.20.4
3. Requires Simple Voice Chat plugin (voicechat-api 2.4.11) as a dependency
4. Java 17 is required

## Architecture

### Core Game Loop (`GameLoop.java`)

The heart of the plugin runs on a configurable timer (default 40 seconds):

1. **Event Collection**: EventLogger accumulates player actions as "Loggables"
2. **Token Management**: Events are culled to fit within GPT's token limit
3. **Dual API Calls**:
   - Action API: Receives game events, responds with function calls to affect the world
   - Speech API: Receives same events, responds with communication to players
4. **Function Execution**: GPT's function calls are dispatched via GptActions

### Loggables System (`loggables/`)

Events implement the `Loggable` interface with three key methods:
- `getLog()`: Returns string representation for GPT
- `combine(Loggable)`: Merges similar events to reduce tokens (e.g., multiple item pickups)
- `getTokens()`: Pre-calculated token count for efficient culling

**Important**: All loggables extend `BaseLoggable` which handles timestamp tracking and token calculation. When creating new loggable types, follow this pattern.

Examples: `ChatLoggable`, `AttackLoggable`, `DeathLoggable`, `CraftLoggable`

### Event Flow

```
Player Action → LoggableEventHandler → EventLogger.addLoggable()
  → Automatic combination with recent similar events
  → Stored in TreeSet sorted by timestamp
  → GameLoop.GPTTask runs periodically
  → Events culled to fit token budget
  → Sent to GPT via GptAPI
  → GPT responds with function calls
  → GptActions.processResponse() executes functions on main thread
  → Actions logged back to EventLogger
```

### GPT Functions (`GPT/GptActions.java`)

GPT can execute these functions:
- **Communication**: `whisper`, `announce`
- **Rewards**: `giveItem`, `summonSupplyChest`, `revive`, `teleport`
- **Punishments**: `smite`, `detonateStructure`
- **Objectives**: `setObjective`, `clearObjective` (displayed on scoreboard)
- **World Manipulation**: `spawnEntity`, `transformStructure`
- **Meta**: `command` (GPT describes desired events, which are converted to Minecraft commands)

**Tool Selection**: Action API randomly selects 3 non-speech tools per cycle to prevent overwhelming GPT with options.

### Token Budget Management

Critical for preventing API errors and managing costs:

1. `staticTokens` = base prompt + personality (calculated once at startup)
2. `nonLogTokens` = staticTokens + summary tokens + tool definition tokens
3. `EventLogger.cull(maxTokens - nonLogTokens)` removes oldest events until budget met
4. Each Loggable pre-calculates its token count using `GPTUtils.countTokens()`

When adding new features that add context, always account for token usage.

### Personality System (`GPT/Personality.java`)

At startup, GPT generates a random personality based on:
- Randomly selected liked/disliked behaviors from `config.yml`'s `potentialBehaviors` list
- Number of liked/disliked behaviors controlled by config
- Personality is persistent for the entire game session
- Affects how GPT interprets events and chooses actions

### World Management

- **RoundSystem**: When enabled, dead players enter spectator mode; when all die, world resets
- **WorldManager**: Handles loading/unloading custom worlds from `plugins/gptgodmc/worlds/`
- **StructureManager**: Tracks named structures that GPT can reference by name
  - Structures marked by players during gameplay
  - GPT can teleport to, transform, or detonate structures

### Voice Integration (`VoiceMonitorPlugin.java`)

Integrates with Simple Voice Chat:
- Captures player voice audio via voicechat-api
- Buffers PCM audio in `PlayerAudioBuffer`
- Converts to WAV via `PCMtoWAV`
- Transcribes via OpenAI Whisper API (`Transcription.java`)
- Logs transcribed speech as `UserInputLoggable`

## Game Modes (`enums/GptGameMode.java`)

- **SANDBOX**: Free-form play, GPT observes and interacts
- **DEATHMATCH**: Team-based mode with Red/Blue teams tracked via scoreboard

## Configuration Notes

Key config options in `config.yml`:
- `openAiKey`: Required for plugin to function
- `rate`: Seconds between GPT calls (min 1, recommended 20+, default 40)
- `gpt-model-name`: OpenAI model to use (default gpt-4o)
- `gpt-model-token-limit`: Must match chosen model's limit
- `Rounds`: Enable/disable round system with world resets
- `startingWorld`: World to load from `plugins/gptgodmc/worlds/`
- `gamemode`: SANDBOX or DEATHMATCH
- `tts`: Enable OpenAI text-to-speech for GPT's voice
- `objectiveDecay`: Minutes before objectives expire

## Adding Dependencies

Add to both `dependencies` and `shadowJar.dependencies` in `build.gradle`:

```groovy
dependencies {
    implementation 'group:artifact:version'
}

shadowJar {
    dependencies {
        include(dependency('group:.*'))
    }
}
```

## Common Patterns

### Adding a New Loggable Event

1. Create class in `loggables/` extending `BaseLoggable`
2. Implement constructor that calls `super(instant)` with event timestamp
3. Override `getLog()` to return GPT-friendly description
4. Override `combine(Loggable)` if events can be merged
5. Register event listener in `LoggableEventHandler.java`

### Adding a New GPT Function

1. Create lambda `Function<String>` in `GptActions.java`
2. Parse JSON args using Gson
3. Schedule work on main thread via `Bukkit.getScheduler().runTask()`
4. Add `GPTActionLoggable` to log what GPT did
5. Add to `functionMap` with `GptFunction` descriptor including clear parameter descriptions
6. Add function name to `speechActionKeys`, `persistentActionKeys`, or leave in general pool

### Debugging GPT Calls

- Check logs for "POSTING" messages showing full request body
- Use `/try [function name] [JSON args]` console command to test functions without GPT
- Set `enabled: false` in config to disable GPT calls while testing other features
- Use `GptAPI.checkRequestBody()` for debugging without sending requests

## Important Constraints

- All GPT function execution must run on Bukkit's main thread (use scheduler)
- Token counting happens eagerly to avoid API rate limit errors
- Speech and action APIs are separate to prevent GPT from only communicating or only acting
- The `command` function is always included in action tools (persistent) to give GPT creative freedom
- Structure names and player names are case-sensitive in GPT function calls
