# What the god costs to run

The god's mind is Claude, reached through Anthropic's API, which is billed per **token** (a token is
roughly ¾ of an English word, or about 4 characters). Everything else — the body, particles, builds,
vision rendering and the voice (Whisper and Piper run on your own server) — costs nothing per use.

**Everything on this page is an ESTIMATE**, calculated from the plugin's real prompt, power list and
a representative perception by counting characters. None of these figures were measured from the
API. Your real numbers are printed by the plugin itself — see
[Where the real numbers are](#where-the-real-numbers-are).

**Set a monthly spending limit** in the [Anthropic Console](https://console.anthropic.com/) (Limits).
That is the only hard cap; the plugin does not stop spending on its own.

- [What a "thought" is](#what-a-thought-is)
- [What one request contains](#what-one-request-contains)
- [How prompt caching changes the bill](#how-prompt-caching-changes-the-bill)
- [Measured estimates](#measured-estimates) — sizes, tokens per request, cost per thought, scenarios
- [Reading the scenarios](#reading-the-scenarios)
- [Knobs that trade cost for liveliness](#knobs-that-trade-cost-for-liveliness)
- [Where the real numbers are](#where-the-real-numbers-are)

---

## What a "thought" is

The god does not run all the time. It **thinks** in short bursts. Each thought starts with a
*perception* — a text snapshot of the world — and ends when the god has finished acting. Within one
thought it may make several **requests** ("messages") to Claude: one to decide what to do, and
another after each round of powers to see their results and decide whether to do more (up to
`brain.max-steps`, default 6). A thought in which it uses no power is one request; a thought in which
it speaks and acts is usually two.

A thought starts (see `God.java`) when:

| Trigger | When | Setting |
|---|---|---|
| Spoken to directly: `/pray`, its name or an attention word in chat, touching its body, an operator's `/god say`, `/god wake` or `/god command` | ≈ 0.35 s later | `brain.reaction-delay-ms`, `brain.attention-words`, `brain.wake-on-any-chat` |
| Something notable (a death, a great deed…) | after ≈ 1.5 s, but not sooner than the minimum gap | `brain.min-gap-seconds` (8) |
| Anything at all happened since it last looked (mining, crafting, chat…) | every heartbeat | `brain.heartbeat-seconds` (40) |
| Nothing happened, players are online | every idle period | `brain.idle-seconds` (270; 0 = never) |

Important consequences:

- **Players who are actively playing nearly always leave something pending** (every broken block is
  an event), so while anyone plays, the god looks about **every 40 seconds — about 90 times an
  hour** — plus extra looks when spoken to.
- **The god sleeps when nobody is in its worlds**, so an empty server costs nothing.
- `/god pause` stops all thinking.
- An open binding command (`/god command`) that the god has not finished asks it to look again
  after 45 seconds, at most `operator.command-retries` (3) times.
- Only one thought runs at a time. After an error it rests (20 seconds to 10 minutes depending on the
  error) instead of retrying in a loop.

There is also a cheap background job: once the god has more than 12 recent thoughts on record, the
**memory model** (`anthropic.memory-model`, default `claude-haiku-5-5`) condenses the oldest 6 into
its long-term chronicle and into each involved player's summary — roughly one small request every
6 thoughts. The memory model is also used once when a god is born, to invent its name.

## What one request contains

| Part | Changes | Cached? |
|---|---|---|
| **Power definitions** — every power's name, description and JSON schema | only when you edit custom powers | yes |
| **Standing instructions** (system prompt) — who the god is, its personality, how to act | only when the god is reborn | yes (explicit cache breakpoint) |
| **Chronicle** — its long-term memory (at most ~350 words) | when it is condensed | written fresh each thought |
| **Perception** — the world, players, its ledger, quests, recent acts, events and chat | every thought | written fresh each thought |
| Previous steps of this thought (its reply, power results, images) | within a thought | yes, via automatic caching |
| **Output** — its thinking, words and power calls | every request | — (billed as output) |

Thinking: the god uses adaptive thinking at the effort you set (`anthropic.effort`, default `auto`:
`low` for routine looks, `medium` when someone simply talks to it, `high` for requests and other
judgment calls). The estimates below assume `low`; `high` thoughts cost noticeably more (roughly 1.5
to 3 times), so with `auto` expect answered prayers to cost more than shown and quiet looks about the same.
Thinking tokens are billed as **output**, the most expensive kind, which is why `low` is the default.

Images: when the god uses `look`, the picture (640 × 360, 512 × 512 or 768 × 320 pixels) adds about
300–350 input tokens to the following requests of that thought.

## How prompt caching changes the bill

The request puts the static part first (power definitions, then the standing instructions) and marks
it for caching; the whole conversation within a thought is cached too (`GodBrain.params`). The
plugin uses the default cache lifetime, **5 minutes**, which restarts every time the cache is read.

- A **cache read** costs a tenth of the normal input price (or less); a **cache write** costs 1.25×.
- Each thought **reads** the static prefix (≈30,000 tokens) from cache and **writes** only the new
  part (chronicle + perception, a few thousand tokens).
- While players are active the god looks every 40 seconds, well inside 5 minutes, so the cache stays
  **warm** and the static part costs a tenth of its full price (about $0.006 a request with Sonnet,
  which is still more than half of a quiet look).
- If nothing happens, the next look comes after `brain.idle-seconds` = 270 s, just inside the cache
  lifetime, so idle looks normally find the cache **warm**. Raising `idle-seconds` to 300 or more
  makes each idle look find the cache **cold** and pay to write the whole prefix again; a cold thought
  costs 3½–8× a warm one (see the tables below). 0 removes idle looks entirely.
- Changing the model, the god's persona, or the custom powers (then `/god reload`) changes the prefix
  and causes one cold thought.

## Measured estimates

Regenerate the block below with:

```
./gradlew test --tests '*CostEstimateTest'
```

then replace everything between the two `MEASURED` markers with the contents of
`build/test-renders/cost-estimate.md`. The test (`src/test/java/net/bigyous/gptgodmc/god/CostEstimateTest.java`)
builds the real system prompt for each archetype in `config.yml`, serializes the real power
definitions exactly as they are sent (including the default custom powers), builds a quiet and a busy
perception in the same format the plugin uses, and prices them with the table in
`utils/TokenTracker`. The assumed values (output sizes, requests per thought, thoughts per hour in
each scenario) are written out in the block.

<!-- BEGIN MEASURED (generated by CostEstimateTest; do not edit by hand) -->

<!-- Generated by CostEstimateTest. ESTIMATES from character counts, not API measurements. -->
Measured from the code with 92 powers. Method: prose ÷ 4.0 characters per token, JSON tool definitions ÷ 3.5, plus an assumed 350 tokens the API adds when tools are present. Images: width × height ÷ 750.

#### The pieces of one request

| Piece | Characters | ≈ Tokens | Cached? |
|---|---:|---:|---|
| Standing instructions (system prompt, longest archetype) | 16,975 | 4,244 | yes — same every thought |
| Power definitions (92 powers, with the default custom powers) + tool-use overhead | 88,894 | 25,748 | yes — same every thought |
| **Static prefix total** | 105,869 | 29,992 | read from cache |
| Chronicle, new world (empty) | 70 | 18 | written each thought |
| Chronicle, full (≈350 words, its cap) | 2,476 | 619 | written each thought |
| Perception, quiet (1 player, few events) | 2,310 | 578 | written each thought |
| Perception, busy (6 players, ~25 events, chat, prayers) | 9,971 | 2,493 | written each thought |
| …of which the sky line and the living land, quiet (1 group of players) | 440 | 110 | written each thought |
| …of which the sky line and the living land, busy (3 groups and their marks) | 948 | 237 | written each thought |

Whole request JSON for a busy thought: 115,971 characters.

System prompt by archetype (characters): benevolent 16,504, chill 16,730, trickster 16,643, evil 16,975, wrathful 16,742, cosmic 16,641.

Largest power definitions (characters of JSON): `fx_cast` 6,499, `vessel_true_form` 2,619, `proclaim` 2,576, `imbue_relic` 2,410, `forge_relic` 2,322, `witness_oath` 2,252; average 966.

#### Tokens per request (one "message" to Claude)

Assumed output (thinking + words + power calls, effort `low`): 120 for a look that ends in silence, 400 for a request that speaks and acts, 150 for the follow-up after the power results (≈150 tokens of results). A `look` image adds ≈307 tokens.

| Request | Cache read | Cache write | Fresh input | Output |
|---|---:|---:|---:|---:|
| 1st request, cache warm (busy perception) | 29,992 | 3,112 | 10 | 400 |
| 1st request, cache cold (busy perception) | 0 | 33,104 | 10 | 400 |
| Follow-up after power results | 33,104 | 550 | 10 | 150 |
| Extra step that looked at an image | 33,654 | 517 | 10 | 400 |
| Quiet look ending in silence, warm | 29,992 | 1,197 | 10 | 120 |
| Quiet look ending in silence, cold | 0 | 31,189 | 10 | 120 |

#### Cost per thought (USD)

| Thought | claude-sonnet-5-5 | claude-haiku-5-5 | claude-opus-5-5 |
|---|---:|---:|---:|
| Quiet look, does nothing (warm cache) | $0.0102 | $0.0005 | $0.0144 |
| Quiet look, does nothing (cold cache) | $0.0792 | $0.0040 | $0.1584 |
| Answers a prayer: speaks + acts, then ends (warm) | $0.0273 | $0.0014 | $0.0420 |
| Answers a prayer (cold cache) | $0.0963 | $0.0048 | $0.1860 |
| Big moment: acts, looks at an image, acts again (warm) | $0.0394 | $0.0020 | $0.0594 |

Prices used (USD per million tokens: input / output / cache read / cache write), from `utils/TokenTracker`: claude-sonnet-5-5 $2.00 / $10.00 / $0.20 / $2.500; claude-haiku-5-5 $0.10 / $0.50 / $0.01 / $0.125; claude-opus-5-5 $4.00 / $20.00 / $0.20 / $5.000.

#### Scenarios (per hour of play, then per month at 3 hours a day)

| Scenario | Thoughts per hour (mix) | Memory-model folds | claude-sonnet-5-5 per hour | claude-haiku-5-5 per hour | claude-opus-5-5 per hour | claude-sonnet-5-5 per month |
|---|---|---:|---:|---:|---:|---:|
| Players online but idle (only idle-seconds looks, every 270 s, cache warm) | 13: 13 silent looks (0 cold), 0 answers (0 cold), 0 big moments | 2 | $0.13 | $0.01 | $0.19 | $12 |
| Quiet server, 1–2 players playing | 75: 55 silent looks (0 cold), 20 answers (2 cold), 0 big moments | 12 | $1.25 | $0.07 | $1.93 | $113 |
| Lively evening, 5–8 players | 120: 50 silent looks (0 cold), 55 answers (0 cold), 15 big moments | 20 | $2.61 | $0.14 | $3.93 | $235 |

One fold of the chronicle by the memory model (claude-haiku-5-5): ≈1,700 input + ≈550 output tokens ≈ $0.0004.

<!-- END MEASURED -->

## Reading the scenarios

How a per-thought cost is put together, using the default model **claude-sonnet-5-5** (per million
tokens: input $2, output $10, cache read $0.20, cache write $2.50) and the "answers a prayer, warm"
row of the measured block as the example (token counts as measured when this page was written):

```
request 1: 29,992 cache read  × $0.20/M = $0.00600
            3,112 cache write × $2.50/M = $0.00778
               10 fresh input × $2/M    = $0.00002
              400 output      × $10/M   = $0.00400
request 2: 33,104 cache read  × $0.20/M = $0.00662
              550 cache write × $2.50/M = $0.00138
               10 fresh input × $2/M    = $0.00002
              150 output      × $10/M   = $0.00150
                                   total ≈ $0.0273 per thought
```

A scenario's hourly cost is *(number of thoughts of each kind) × (cost of that kind)* plus the
memory model's folds (one per 6 thoughts, a fraction of a cent each). For example, the quiet server:
55 silent looks × $0.0102 + 18 warm answers × $0.0273 + 2 cold answers × $0.0963 + 12 folds × $0.0004
≈ $0.56 + $0.49 + $0.19 + $0.005 ≈ **$1.25 an hour**.

The "players online but idle" row is the default `idle-seconds` of 270: about 13 looks an hour, each
finding the cache still warm (13 × $0.0102 ≈ $0.13 with Sonnet). If you raise `idle-seconds` to 300
or more, every idle look finds the cache cold and costs about $0.079 instead: 12 × $0.0792 ≈ $0.95
an hour.

The monthly column assumes **3 hours of play a day for 30 days** (90 hours). Scale it to your
server: a server that is busy 6 hours a day costs about twice the figure shown; one that is only
played at weekends, about a third.

Things that make real costs higher than these estimates:

- A bigger world: more players, more structures, longer ledgers and chat make the perception longer
  (capped by `brain.perception-char-budget`).
- A chatty or meddling god (its dials) that acts in most thoughts, or many multi-step thoughts with
  images (`look`).
- `anthropic.effort: high` (much more thinking than `low`, billed as output; `auto`, the default, uses it only for judgment calls).
- Several gods (EXPERIMENTAL realms): each costs as much as one god.
- `brain.wake-on-any-chat: true`.

Things that make them lower: quiet players, `idle-seconds: 0`, a longer heartbeat, a cheaper model.

## Knobs that trade cost for liveliness

| Setting | Cheaper | Livelier | Effect on cost |
|---|---|---|---|
| `anthropic.model` | `claude-haiku-5-5` | `claude-opus-5-5` | The biggest lever: Haiku is about 20× cheaper than Sonnet, Opus about 2× dearer. |
| `anthropic.effort` | `low` | `high` (`auto`, the default, mixes them) | More thinking = more output tokens. |
| `brain.heartbeat-seconds` | 90–120 | 20–40 | Routine looks per hour while people play = 3600 ÷ this. |
| `brain.idle-seconds` | 0 | 120–270 | Looks when nothing happens; at 300 or more each one finds the cache cold. |
| `brain.min-gap-seconds` | 15+ | 8 | Caps how often notable events can trigger looks. |
| `brain.wake-on-any-chat` | `false` | `true` | `true` makes every chat line start a thought. |
| `brain.max-steps` | 3 | 6–12 | Caps the requests one thought may make. |
| `brain.perception-char-budget` | lower | 24000 | Caps how long a perception gets on a busy server. |
| Vision (`look`) | — | — | Each image ≈ 300–350 tokens in the following requests; the god decides when to look. |
| Voice | free | free | Whisper and Piper run on your server; no API cost. |
| Rites, particles, body, builds | free | free | Drawn by the server; no API cost. |

You can switch live: `/god model claude-haiku-5-5`, `/god effort low`, `/god pause`.

## Where the real numbers are

The plugin records the token counts the API reports for **every request**. They are **never shown in
game, not even to operators** — typing `/god usage` in game only tells you where to look.

- **Server console** — one line per request (turn off with `debug.log-usage: false`):

  ```
  [God usage] thought #12 step 2 · sonnet-5-5 · in 1,234 (cache read 9,876, write 0) · out 321 · $0.0123 · session $1.2345
  ```

  `in` is fresh (uncached) input; cache reads and writes are listed separately; `out` includes
  thinking. With several gods the line starts with `<realm>`. A `+` after the session total means
  some requests used a model with no known price.
- **`plugins/gptgodmc/usage.csv`** — the same, one row per request, for spreadsheets (turn off with
  `debug.usage-file: false`). Columns:

  ```
  time,realm,model,purpose,input,cache_read,cache_write,output,cost_usd,session_total_usd
  ```

  `purpose` is e.g. `thought #12 step 2` or `folding the chronicle`. Add up `cost_usd` for any
  period you like.
- **`gptgod usage`** typed in the console — the session totals: requests, input (fresh / cache read /
  cache write), output and estimated cost.

The dollar amounts are the plugin's own calculation from the price table in
`src/main/java/net/bigyous/gptgodmc/utils/TokenTracker.java`; your Anthropic invoice is the final
word. Session totals reset when the server restarts; `usage.csv` keeps growing.
