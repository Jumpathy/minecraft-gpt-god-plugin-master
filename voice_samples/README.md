# Voice samples

Every clip is the same line, spoken by a local text-to-speech voice and then passed through the
plugin's own effect code (`GodVoiceFx`), exactly as the server does it:

> Mortal. I have watched you build upon my mountain. Kneel, and bring me an offering of gold, and I
> may yet forgive you.

Folder = the voice, file = the effect: `voice_samples/<voice>/<effect>.mp3`.

**Default now:** `kokoro-michael`. Its effect follows the god's mood: `ethereal` normally,
`commanding` when ominous, `demonic` when wrathful. The god can also pick a tone per line.

## Voices

| Folder | Engine | Voice | Notes |
|---|---|---|---|
| `piper-ryan-medium` | Piper (built in) | `en_US-ryan-medium` | The fallback when no Kokoro server runs. Nothing to install, about 60 MB. |
| `piper-ryan-high` | Piper (built in) | `en_US-ryan-high` | Same speaker, larger model (115 MB), slower to speak. |
| `piper-alan` | Piper (built in) | `en_GB-alan-medium` | British male. |
| `kokoro-onyx` | Kokoro (separate server) | `am_onyx` | Deep American male. |
| `kokoro-michael` | Kokoro | `am_michael` | American male. **The default.** |
| `kokoro-fenrir` | Kokoro | `am_fenrir` | American male, rougher. |
| `kokoro-george` | Kokoro | `bm_george` | British male. |

Kokoro is a newer model and usually sounds more human, but the plugin does not run it: you start a
local Kokoro server yourself (Kokoro-FastAPI, port 8880; see section 11.5 of the tutorial) and the
plugin talks to it. Piper runs inside the plugin: it downloads itself the first time it is needed,
and the god uses it whenever the Kokoro server does not answer. Both are free and run on your own machine; nothing is sent to OpenAI.

## Effects

| File | What it does |
|---|---|
| `none` | The plain voice, no effect. Listen to this first to judge the voice itself. |
| `majestic` | Lightest touch: a slightly larger speaker in a stone hall. |
| `divine` | Larger still, in a cathedral. |
| `booming` | A voice from the sky: big hall, heavy bass, rolling echo. |
| `commanding` | Close, loud and hard-edged. |
| `ethereal` | A shimmer an octave above and a long bright tail. |
| `demonic` | Deliberately inhuman: pitched down, doubled, growling. |
| `abyssal` | The most extreme: very deep, muffled, long dark tail. |

`majestic`, `divine`, `booming` and `commanding` keep the voice natural: they only make the speaker
larger (the recording is played slightly slower, so pitch and resonance drop together) and put it
in a bigger room. `demonic` and `abyssal` are meant to sound monstrous.

## Using your choice (config.yml)

A Piper voice:

```yaml
voice:
  tts:
    engine: piper
    piper-voice: en_US-ryan-high     # or en_US-ryan-medium, en_GB-alan-medium
    effect: divine                   # or auto to follow the god's personality
```

A Kokoro voice (the default; with a Kokoro server running on the same machine):

```yaml
voice:
  tts:
    engine: kokoro
    url: http://localhost:8880/v1/audio/speech
    model: kokoro
    voice: am_michael                # or am_onyx, am_fenrir, bm_george
    sample-rate: 24000
    effect: auto                     # by mood; see voice.tts.moods
```

## Regenerating

Put dry clips named `<voice>@<pace>.wav` in `build/voice-dry` (one per pace, see
`VoiceSamplesTest`), run `./gradlew test --tests '*VoiceSamplesTest'`, and convert
`build/voice-samples/<voice>/<effect>.wav` to MP3.
