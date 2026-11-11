# Using a model other than Claude

GPTGOD is built for Claude and plays best on it. That stays the default, and nothing here changes
it. If you would rather use OpenAI, a model on your own machine, or a gateway such as OpenRouter,
set `llm.provider` in `plugins/gptgodmc/config.yml`.

| `llm.provider`      | Talks to                                                       | Needs                         |
|---------------------|----------------------------------------------------------------|-------------------------------|
| `anthropic`         | Claude, through the `anthropic:` section (default, unchanged)  | an Anthropic API key          |
| `openai`            | api.openai.com                                                 | an OpenAI API key             |
| `openai-compatible` | any server with an OpenAI-style `/v1/chat/completions`: Ollama, LM Studio, llama.cpp server, vLLM, OpenRouter | `base-url` and `model` |

After editing the file run `/god reload` (no restart needed). `/god status` shows the model in use;
the console banner at start shows the provider and whether the lite profile is on.

**How far this has been tested.** The OpenAI-compatible client is covered by unit tests, replayed
stream samples and a fake local server that plays a whole tool round trip. It has not yet been run
against a live OpenAI account or a real local model, so treat the first session as a trial and
watch `/god journal` and the mind viewer.

## Copy-paste setups

### OpenAI

```yaml
llm:
  provider: openai
  model: gpt-5-mini        # any chat model that supports tools
  memory-model: ""         # blank = the same model; a cheaper one is fine here
```

Then, in the **server console** (never in chat):

```
gptgod setkey <your OpenAI key>
```

The key is saved as `llm.api-key`. You can set the `OPENAI_API_KEY` environment variable instead.
Images from the god's `look` power are sent to the model (`vision: auto` means yes for OpenAI).

### Ollama (a model on your own machine)

1. Install Ollama and pull a model that supports tools, for example `ollama pull qwen3:8b`.
2. Give the model room. Ollama's default context window is only a few thousand tokens, and a
   prompt that does not fit is cut off without any error. Set it to at least 8192, for example
   with the `OLLAMA_CONTEXT_LENGTH=8192` environment variable before starting Ollama (or the
   context setting in the Ollama app; check the Ollama docs for your version).
3. In `config.yml`:

```yaml
llm:
  provider: openai-compatible
  base-url: http://localhost:11434/v1
  model: qwen3:8b            # exactly as `ollama list` shows it
  context-tokens: 8192       # the same number you gave Ollama
  timeout-seconds: 180       # the first reply also loads the model
  profile: auto              # auto picks lite for a local server
```

No key is needed. If Minecraft and Ollama run on different machines, use that machine's address
(`http://192.168.1.20:11434/v1`) and start Ollama with `OLLAMA_HOST=0.0.0.0`.

### LM Studio

1. Load a model that supports tool use, with a context length of 8192 or more (the slider shown
   when you load it).
2. Start the local server (Developer tab). It listens on port 1234.
3. In `config.yml`:

```yaml
llm:
  provider: openai-compatible
  base-url: http://localhost:1234/v1
  model: qwen/qwen3-8b       # the identifier LM Studio shows for the loaded model
  context-tokens: 8192
  timeout-seconds: 180
  profile: auto
```

### llama.cpp server, vLLM, OpenRouter

Same shape; only the address and the model name change.

```yaml
llm:
  provider: openai-compatible
  base-url: http://localhost:8080/v1     # llama.cpp: start it with --jinja so tools work
  # base-url: http://localhost:8000/v1   # vLLM: start it with tool calling enabled for your model
  # base-url: https://openrouter.ai/api/v1
  model: <the model's name on that server>
```

For OpenRouter (or any hosted gateway) set the key with `gptgod setkey <key>` in the console and
leave `profile: auto`: a remote address gets the full profile.

## Every option

```yaml
llm:
  provider: anthropic        # anthropic | openai | openai-compatible
  base-url: ""               # the server's address (blank for openai)
  api-key: ""                # set with `gptgod setkey`; never shown in game or in the mind viewer
  model: ""                  # the model that plays the god
  memory-model: ""           # background chores; blank = model
  max-output-tokens: 2000    # the longest one reply
  context-tokens: 0          # your model's context window; 0 = large enough
  timeout-seconds: 120       # wait for a reply to start, and between pieces of it
  vision: auto               # auto | on | off: can the model see images?
  temperature: -1            # -1 = the server's default
  reasoning-effort: ""       # low | medium | high for models that take it
  profile: auto              # auto | full | lite
  lite:
    max-steps: 3
    max-output-tokens: 1024
    perception-chars: 6000
    tools: []                # empty = the core set listed below
  price-input: 0             # USD per million tokens, if you want costs in the console
  price-output: 0
```

- `gptgod setkey <key>` (console only) saves the key for the provider in use. To set one for the
  other provider without switching: `gptgod setkey llm <key>` or `gptgod setkey anthropic <key>`.
- With several gods, each god's own model (`/god @name model <id>`, God Manager) only counts for
  the provider it belongs to: a Claude model picked for one god is ignored while OpenAI or a local
  server plays the gods, and comes back when you switch back.
- The non-secret options are also in the God Manager: Advanced, System tab.
- `brain.max-steps`, `brain.always-answer` and the rest of `brain:` apply to every provider.
  `anthropic.effort`, `anthropic.refusal-fallback` and `brain.web-search` only do something on Claude.

## What you give up outside Claude

| Feature                         | Claude | OpenAI | OpenAI-compatible |
|---------------------------------|--------|--------|-------------------|
| All powers, streamed and acted on as they arrive | yes | yes | yes (full profile) |
| Prompt caching                  | yes, explicit | automatic on OpenAI's side | depends on the server |
| Thinking shown in the journal and mind viewer | yes | no (not sent by this API) | when the server sends it (`reasoning`, `reasoning_content`, or inline think tags) |
| Effort chosen per thought (`anthropic.effort`) | yes | no; `llm.reasoning-effort` is one fixed value | no |
| Web search                      | yes    | no     | no                |
| Refusal fallback                | yes    | no     | no                |
| Images from `look`              | yes    | yes    | only with `vision: on` and a vision model; otherwise the model gets the caption and a note |
| Costs in the console            | yes    | only if you set `price-input` / `price-output` | same |

Token counts are logged for every provider (console `[God usage]` lines and `usage.csv`) when the
server reports them. A cost appears only when a price is known. Nothing about usage is ever shown
in game. The per-god daily budget (`pantheon.budget-per-god`) counts only priced calls, so it does
not limit a model with no price.

## The lite profile (small local models, around 7B)

The full god has about 70 powers, a long rulebook and a perception that can reach 25,000
characters. A 7B model cannot hold that: it forgets instructions, picks the wrong tool or writes
broken tool calls. `profile: lite` (chosen automatically when the server is on this machine or
your local network) trades reach for reliability:

- **Fifteen core powers**: `whisper`, `announce`, `vessel_say`, `give_item`, `heal`,
  `apply_effect`, `smite`, `set_quest`, `resolve_quest`, `remember`, `judge`, `vessel_go`,
  `vessel_vanish`, `vessel_mood`, `command_done`. Change the list with `llm.lite.tools` (any power
  names from `/god tools`); keep it short. The list is fixed within a session.
- **A short rulebook** (about a fifth of the full one) that keeps the god's personality, dials and
  your `personality.briefing`.
- **A small perception** (`llm.lite.perception-chars`, default 6000). Binding commands, prayers and
  the conversation always stay; scenery (structures, works, other gods, pastimes) goes first, and
  long lists keep their newest lines.
- **Fewer steps** per thought (`llm.lite.max-steps`, default 3) and shorter replies
  (`llm.lite.max-output-tokens`, default 1024). Power descriptions are shortened too.
- **Strict tool calls.** Arguments must be one JSON object that fits the power's schema. Harmless
  slips are mended (a code fence, `"5"` for `5`, a trailing comma, a call written out as text).
  Anything else is refused with the reason and the expected arguments, and the model gets one
  retry per thought; a second malformed call ends the thought. A cut-off call is never completed
  by guessing.

With the defaults the lite prompt, tools, perception and reply fit in an 8192-token context.

### What works worse on small models

- **Following the persona.** A 7B model drifts toward a generic helpful voice, grants requests too
  easily and forgets grudges. Dials at the extremes survive best.
- **Tool calls.** Expect some malformed or pointless calls, wrong item ids, and replies written as
  plain text instead of `whisper`. The retry catches part of it. A temperature around 0.3 to 0.7
  usually helps.
- **Several people talking.** Small models mix up who said what. Keep `brain.wake-on-any-chat` off.
- **Binding commands.** Simple ones ("give Ashwyn a diamond sword") work; anything that needs
  building, commands or several steps is beyond the lite powers and will be reported impossible
  or left open until it expires.
- **No building, vision, particles, rites, music, goals or free-will pastimes** in lite: those
  powers are not offered. `/god tool <power> <json>` and the God Manager still run them by hand.
- **Speed.** On a CPU or a small GPU a thought can take 20 to 60 seconds. Raise
  `timeout-seconds`, and consider raising `brain.min-gap-seconds` and `brain.idle-seconds` so
  thoughts do not queue up.
- **Memory chores.** Naming the god and condensing memories use the same small model unless you
  set `memory-model`. If it cannot produce the JSON asked for, the plugin falls back to a name
  from its own table and keeps the old chronicle.

### Recommended minimums

| What you want                                   | Model size                                  |
|-------------------------------------------------|---------------------------------------------|
| A god that answers prayers and hands out gifts and punishments (lite) | 7B to 8B, built for tool calling, 8k context |
| Lite with a steadier persona and fewer broken calls | 12B to 14B                                |
| The full profile (`profile: full`): building, vision, goals | 30B or more with a 32k context, or a hosted model; set `context-tokens` to the real window |
| The god as designed                              | Claude (the default), or a large hosted model |

Below 7B, or with a model that was not trained for tool calling, the god will mostly stay silent
or talk to itself. Quantised builds at 4 bits (Q4) are fine; lower than that hurts tool calls first.

## Troubleshooting

- **"connection refused (is the server running?)"** in `/god journal`: the address is wrong or the
  server is not started. From the Minecraft machine, `curl <base-url>/models` should list models.
- **404 or "model not found"**: `llm.model` must match the server's name for it exactly.
- **The god ignores half its perception or its rules**: the context window is smaller than the
  prompt. Raise it on the server and set `llm.context-tokens` to the same number.
- **Nothing happens, then a timeout**: the model is loading or too slow; raise `timeout-seconds`.
- **It only thinks and never speaks**: the model is answering in text. Use a model with tool
  support, lower the temperature, and keep the lite profile.
- **400 errors mentioning tools or a parameter**: the server's tool support may be off (llama.cpp
  needs `--jinja`; vLLM needs its tool-call options), or the model has none.
- The mind viewer (`viewer.enabled`, http://127.0.0.1:4673/) shows every request and reply for any
  provider, including what the model wrote before a refused call.
