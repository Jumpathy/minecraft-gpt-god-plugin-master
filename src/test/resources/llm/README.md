# Stream samples for the OpenAI-compatible provider tests

Each `.sse` file is one streamed chat completion, as the server sends it. They were written by hand
to the wire formats the servers document (they are not captures from a live server):

- `openai-tool-calls.sse`: OpenAI style. Prose, then two tool calls streamed in pieces by index, the
  finish reason, a last chunk with usage (including cached tokens), `[DONE]`.
- `ollama-tool-calls.sse`: Ollama style. Reasoning in `reasoning`, each tool call whole in one chunk
  and both numbered index 0, finish reason `stop` despite the calls.
- `malformed-tool-json.sse`: a tool call cut off by the output limit, a comment line and a garbled chunk.
- `inline-think-text-call.sse`: a small model that thinks inline between think tags (cut across
  chunks) and writes its tool call into the prose; no `[DONE]`, no usage.

The `flavour-*.sse` files are the same thought (a whisper and a gift for Ashwyn) as each server
would stream it, with that server's own fields and habits:

- `flavour-openai.sse`: api.openai.com. `usage: null` on every chunk, `obfuscation`, `refusal`,
  `service_tier`, cached and reasoning token details in the last chunk.
- `flavour-lmstudio.sse`: LM Studio. `reasoning_content`, numeric call ids, `logprobs`, a `stats` object.
- `flavour-llamacpp.sse`: llama.cpp server. Arguments begun in the first tool chunk, `timings` at the end.
- `flavour-vllm.sse`: vLLM. The name and the arguments in separate chunks, running usage on every chunk.
- `flavour-openrouter.sse`: OpenRouter. `: OPENROUTER PROCESSING` comment lines, `provider`,
  `reasoning` and `reasoning_details`, `native_finish_reason`, usage (with `cost`) after the finish chunk.

`ProviderScenarioTest` serves them from a stub HTTP server and runs whole thoughts against it.

To add a real capture: run the server, `curl -N` a streaming request, and save the body here.
