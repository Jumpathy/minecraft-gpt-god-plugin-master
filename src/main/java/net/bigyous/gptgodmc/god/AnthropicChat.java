package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageDeltaUsage;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.llm.LlmChat;
import net.bigyous.gptgodmc.god.llm.LlmReply;
import net.bigyous.gptgodmc.god.llm.ToolAnswer;

/**
 * A thought's conversation with Claude through the Anthropic SDK: prompt caching, adaptive thinking,
 * web search, the refusal fallback and images, exactly as {@link GodBrain#params} builds them.
 */
final class AnthropicChat implements LlmChat {
    private final AnthropicClient client;
    private final String model, system;
    private final List<MessageParam> messages = new ArrayList<>();

    AnthropicChat(AnthropicClient client, String model, String system) {
        this.client = client;
        this.model = model;
        this.system = system;
    }

    @Override
    public void user(String text) {
        messages.add(MessageParam.ofUser(text));
    }

    @Override
    public void user(String text, byte[] png) {
        if (png == null || png.length == 0) {
            user(text);
            return;
        }
        messages.add(MessageParam.ofUserOfContentBlockParams(List.of(
                ContentBlockParam.ofText(TextBlockParam.builder().text(text).build()),
                ContentBlockParam.ofImage(com.anthropic.models.messages.ImageBlockParam.builder()
                        .source(com.anthropic.models.messages.Base64ImageSource.builder()
                                .data(java.util.Base64.getEncoder().encodeToString(png))
                                .mediaType(com.anthropic.models.messages.Base64ImageSource.MediaType.IMAGE_PNG)
                                .build())
                        .build()))));
    }

    @Override
    public void assistant(LlmReply reply) {
        if (reply.turn instanceof MessageParam turn) messages.add(turn);
    }

    @Override
    public void toolResults(List<ToolAnswer> answers, String extraText) {
        List<ContentBlockParam> next = new ArrayList<>();
        for (ToolAnswer a : answers) {
            next.add(ContentBlockParam.ofToolResult(GodBrain.toResult(a.id(), a.result())));
        }
        if (extraText != null && !extraText.isBlank()) {
            next.add(ContentBlockParam.ofText(TextBlockParam.builder().text(extraText).build()));
        }
        messages.add(MessageParam.ofUserOfContentBlockParams(next));
    }

    private record Prepared(MessageCreateParams params) implements Request {
        @Override
        public String bodyJson() {
            try {
                return ObjectMappers.jsonMapper().writeValueAsString(params._body());
            } catch (Exception e) {
                GPTGOD.LOGGER.debug("Could not serialize a request", e);
                return null;
            }
        }

        @Override
        public List<String> betas() {
            return params._headers().values("anthropic-beta");
        }

        private List<TextBlockParam> blocks() {
            return params.system().map(s -> s.asTextBlockParams()).orElse(List.of());
        }

        @Override
        public String system() {
            List<TextBlockParam> sys = blocks();
            return sys.isEmpty() ? "" : sys.get(0).text();
        }

        @Override
        public String chronicle() {
            List<TextBlockParam> sys = blocks();
            return sys.size() > 1 ? sys.get(1).text() : "";
        }

        @Override
        public String toolsJson() {
            try {
                return ObjectMappers.jsonMapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(params.tools().orElse(List.of()));
            } catch (Exception e) {
                GPTGOD.LOGGER.debug("Could not serialize the tool list", e);
                return "";
            }
        }
    }

    @Override
    public Request prepare(String effort) {
        // A copy of the conversation as it stands: later turns must not change a request already built.
        return new Prepared(GodBrain.params(model, system, new ArrayList<>(messages), effort));
    }

    private record PendingCall(String id, String name, StringBuilder json) {
    }

    @Override
    public LlmReply send(Request request, Listener listener) {
        MessageCreateParams params = ((Prepared) request).params();
        LlmReply reply = new LlmReply();
        MessageAccumulator whole = MessageAccumulator.create();
        boolean wholeOk = true;
        Map<Long, PendingCall> calls = new HashMap<>();
        Map<Long, StringBuilder> searches = new HashMap<>();

        try (StreamResponse<RawMessageStreamEvent> response = client.messages().createStreaming(params)) {
            Iterator<RawMessageStreamEvent> events = response.stream().iterator();
            while (events.hasNext()) {
                RawMessageStreamEvent event = events.next();
                listener.token();
                if (wholeOk) {
                    try {
                        whole.accumulate(event);
                    } catch (RuntimeException e) {
                        wholeOk = false;
                    }
                }
                if (event.isMessageStart()) {
                    Usage u = event.asMessageStart().message().usage();
                    reply.billed = true;
                    reply.in = u.inputTokens();
                    reply.out = u.outputTokens();
                    reply.cacheRead = u.cacheReadInputTokens().orElse(0L);
                    reply.cacheWrite = u.cacheCreationInputTokens().orElse(0L);
                } else if (event.isContentBlockStart()) {
                    var start = event.asContentBlockStart();
                    if (start.contentBlock().isToolUse()) {
                        ToolUseBlock tool = start.contentBlock().asToolUse();
                        calls.put(start.index(), new PendingCall(tool.id(), tool.name(), new StringBuilder()));
                    } else if (start.contentBlock().isServerToolUse()) {
                        searches.put(start.index(), new StringBuilder());
                    } else if (start.contentBlock().isWebSearchToolResult()) {
                        // results go back to Claude as they are; nothing to show here
                    } else {
                        listener.open(start.index(), start.contentBlock().isThinking());
                    }
                } else if (event.isContentBlockDelta()) {
                    var delta = event.asContentBlockDelta();
                    RawContentBlockDelta d = delta.delta();
                    if (d.isInputJson()) {
                        PendingCall call = calls.get(delta.index());
                        if (call != null) call.json().append(d.asInputJson().partialJson());
                        StringBuilder search = searches.get(delta.index());
                        if (search != null) search.append(d.asInputJson().partialJson());
                    } else if (d.isText()) {
                        listener.delta(delta.index(), d.asText().text());
                    } else if (d.isThinking()) {
                        listener.delta(delta.index(), d.asThinking().thinking());
                    }
                } else if (event.isContentBlockStop()) {
                    long index = event.asContentBlockStop().index();
                    StringBuilder search = searches.remove(index);
                    if (search != null) {
                        JsonObject q = GodBrain.parseInput(search.toString());
                        listener.search(q != null && q.has("query") ? q.get("query").getAsString()
                                : search.toString());
                    }
                    PendingCall call = calls.remove(index);
                    if (call != null) {
                        // The call is complete: carry it out now, while the rest is still being written.
                        listener.call(call.id(), call.name(), call.json().toString());
                    }
                    listener.close(index);
                } else if (event.isMessageDelta()) {
                    var delta = event.asMessageDelta();
                    delta.delta().stopReason().ifPresent(r -> stop(reply, r));
                    MessageDeltaUsage u = delta.usage();
                    reply.out = u.outputTokens();
                    reply.in = u.inputTokens().orElse(reply.in);
                    reply.cacheRead = u.cacheReadInputTokens().orElse(reply.cacheRead);
                    reply.cacheWrite = u.cacheCreationInputTokens().orElse(reply.cacheWrite);
                    reply.searches = u.serverToolUse().map(s -> s.webSearchRequests()).orElse(reply.searches);
                } else if (event.isMessageStop()) {
                    reply.finished = true;
                }
            }
        } catch (AnthropicServiceException e) {
            reply.error = LlmReply.Error.service(e.statusCode(), String.valueOf(e.body()));
        } catch (AnthropicIoException e) {
            reply.error = LlmReply.Error.network(e.getMessage());
        } catch (RuntimeException e) {
            reply.error = LlmReply.Error.broken(e.toString());
        }

        if (reply.finished && wholeOk) {
            try {
                Message message = whole.message();
                reply.refusalDetail = message.stopDetails()
                        .map(d -> " (" + d.category().map(Object::toString).orElse("?") + ")").orElse("");
                reply.responseJson = () -> {
                    try {
                        return ObjectMappers.jsonMapper().writeValueAsString(message);
                    } catch (Exception e) {
                        GPTGOD.LOGGER.debug("Could not serialize a reply", e);
                        return null;
                    }
                };
                reply.turn = message.toParam();
                reply.replayable = true;
            } catch (RuntimeException e) {
                // A reply served by the refusal-fallback model can carry a block this SDK version
                // cannot replay.
                reply.turn = null;
                reply.replayable = false;
            }
        }
        return reply;
    }

    private static void stop(LlmReply reply, StopReason r) {
        reply.stopText = r.toString();
        if (StopReason.END_TURN.equals(r)) reply.stop = LlmReply.Stop.END_TURN;
        else if (StopReason.TOOL_USE.equals(r)) reply.stop = LlmReply.Stop.TOOL_USE;
        else if (StopReason.PAUSE_TURN.equals(r)) reply.stop = LlmReply.Stop.PAUSE_TURN;
        else if (StopReason.REFUSAL.equals(r)) reply.stop = LlmReply.Stop.REFUSAL;
        else if (StopReason.MAX_TOKENS.equals(r)) reply.stop = LlmReply.Stop.MAX_TOKENS;
        else reply.stop = LlmReply.Stop.OTHER;
    }
}
