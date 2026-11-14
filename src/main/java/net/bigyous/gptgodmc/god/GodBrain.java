package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageDeltaUsage;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.llm.LlmChat;
import net.bigyous.gptgodmc.god.llm.LlmReply;
import net.bigyous.gptgodmc.god.llm.LlmTool;
import net.bigyous.gptgodmc.god.llm.ToolAnswer;
import net.bigyous.gptgodmc.god.llm.ToolCheck;
import net.bigyous.gptgodmc.god.tools.ToolException;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.god.tools.ToolResult;
import net.bigyous.gptgodmc.utils.DebugWebhook;
import net.bigyous.gptgodmc.viewer.MindTrace;

/**
 * One god's mind. Each thought is a fresh, append-only conversation: perception in, then rounds of
 * tool use with real results until the god is done. Memory between thoughts lives in
 * {@link Memory}, rendered into the perception.
 *
 * Replies are streamed, and every power is carried out the moment its tool call has fully arrived —
 * the god speaks while Claude is still writing the rest of its answer. Words aimed at the god while
 * it is busy are slipped into the same thought before its next step.
 */
public final class GodBrain {
    private final Realm realm;
    private final ExecutorService mind;
    private final AtomicBoolean thinking = new AtomicBoolean(false);
    /** What it said to whom in the current thought, so its acts are checked against its words. */
    private Coherence coherence = new Coherence();
    private volatile long backoffUntil = 0;
    private volatile String lastError = null;
    private volatile long lastThoughtEnded = 0;
    /** The thought being recorded for the mind viewer (NONE when it is off). */
    private volatile MindTrace.Thought trace = MindTrace.Thought.NONE;

    public GodBrain(Realm realm) {
        this.realm = realm;
        this.mind = Executors.newSingleThreadExecutor(work -> {
            // Everything on this thread happens as this realm's god.
            Thread t = new Thread(() -> {
                realm.bindThread();
                work.run();
            }, "god-mind-" + realm.id);
            t.setDaemon(true);
            return t;
        });
    }

    public boolean isThinking() {
        return thinking.get();
    }

    public long lastThoughtEnded() {
        return lastThoughtEnded;
    }

    public boolean isBackingOff() {
        return System.currentTimeMillis() < backoffUntil;
    }

    public String lastError() {
        return lastError;
    }

    public void clearBackoff() {
        backoffUntil = 0;
    }

    /** Starts a thought on the mind thread; ignored if one is already running. */
    public boolean think(String trigger) {
        if (!thinking.compareAndSet(false, true)) {
            return false;
        }
        TabList.refreshSoon();
        showThinking(true);
        try {
            var body = realm.vesselIfCreated();
            if (body != null) body.ponder(trigger); // its body shows the working while its mind runs
        } catch (RuntimeException e) {
            // no vessel
        }
        mind.execute(() -> {
            try {
                cycle(trigger);
            } catch (RuntimeException e) {
                lastError = e.toString();
                GPTGOD.LOGGER.error("The god's thought failed", e);
                Journal.add(Journal.Kind.ERROR, "Thought failed: " + e);
                trace.note("error", 0, null, "Thought failed: " + e);
                trace.end("failed: " + e);
            } finally {
                trace.end("ended");
                trace = MindTrace.Thought.NONE;
                lastThoughtEnded = System.currentTimeMillis();
                thinking.set(false);
                TabList.refreshSoon();
                showThinking(false);
            }
        });
        return true;
    }

    /** Thinking dots over the vessel's head, if it has one. */
    private void showThinking(boolean on) {
        try {
            var v = realm.vesselIfCreated();
            if (v != null) v.setThinking(on);
        } catch (RuntimeException e) {
            // no vessel: nothing to show
        }
    }

    /** Runs work on the mind thread (e.g. creating the persona) without starting a thought. */
    public void submit(Runnable work) {
        mind.execute(work);
    }

    public void shutdown() {
        mind.shutdownNow();
    }

    /** Everything a perception tells of, as lines (for deciding whether a minimap would help). */
    private static List<String> happened(EventLogger.Drained d) {
        List<String> out = new java.util.ArrayList<>(d.events());
        d.prayers().forEach(p -> out.add("prayer: " + p));
        d.directives().forEach(p -> out.add("[operator] " + p));
        d.speech().forEach(p -> out.add("said: " + p));
        return out;
    }

    private record Snapshot(String text, String digest, EventLogger.Taken taken, EffortPicker.Choice effort,
            net.bigyous.gptgodmc.god.vision.MapView.Pending map) {
    }

    // ---- One thought ---------------------------------------------------------------------------

    private void cycle(String trigger) {
        if (!GodClient.hasCredentials()) {
            Journal.add(Journal.Kind.ERROR, Llm.anthropic()
                    ? "No API key: the god cannot think. Run `gptgod setkey <key>` in the console."
                    : "The god cannot think: " + Llm.notReady());
            return;
        }
        long began = System.currentTimeMillis();
        Persona persona = Persona.loadOrCreate();
        realm.vessel().refreshNameplateLater();

        Snapshot snap;
        try {
            snap = Sync.call(() -> {
                EventLogger.Taken taken = EventLogger.take();
                try {
                    EventLogger.Drained d = EventLogger.render(taken);
                    String text = Perception.build(trigger, d);
                    return new Snapshot(text, digest(d), taken, pickEffort(d),
                            net.bigyous.gptgodmc.god.vision.MapView.auto(happened(d)));
                } catch (RuntimeException e) {
                    EventLogger.restore(taken);
                    throw e;
                }
            }, 10_000);
        } catch (ToolException e) {
            Journal.add(Journal.Kind.ERROR, "Could not perceive the world: " + e.getMessage());
            return;
        }
        int number = realm.thoughts.incrementAndGet();
        net.bigyous.gptgodmc.viewer.EventFeed.seen(snap.taken().loggables(), number, 0, false);
        Journal.add(Journal.Kind.CYCLE, "Thinking (" + trigger + ") · effort " + snap.effort().effort() + ": "
                + snap.effort().reason());
        Journal.add(Journal.Kind.PERCEPTION, snap.text());

        String model = GodSettings.model();
        // Small local models get the short prompt, the core powers and a perception cut to fit (llm.profile).
        boolean lite = Llm.lite();
        String system = lite ? LitePrompt.system(persona) : Prompts.system(persona);
        LlmChat chat = Llm.open(model, system);
        if (chat == null) {
            EventLogger.restore(snap.taken());
            net.bigyous.gptgodmc.viewer.EventFeed.restored(snap.taken().loggables());
            Journal.add(Journal.Kind.ERROR, "The god cannot think: " + Llm.notReady());
            return;
        }
        String seen = Llm.fitPerception(snap.text(), system);
        // Who spoke to it (named it, prayed, asked) before this thought: with always-answer each gets words back.
        String addressed = GodSettings.alwaysAnswer()
                ? addressedBy(snap.taken(), realm.memory(), GodSettings.silenceBelowFavor())
                : "";
        String firstUser = addressed.isEmpty() ? seen : seen + "\n\n[Mortals addressed you ("
                + addressed + "). Answer each of them in words this time (whisper, vessel_say or announce), even if "
                + "only to refuse, mock or tell them to wait. Silence is not an answer.]";
        // A minimap sent unasked (vision.auto-minimap), for a model that can see.
        net.bigyous.gptgodmc.god.vision.Vision.Result map = chat.sees()
                ? net.bigyous.gptgodmc.god.vision.MapView.finish(snap.map()) : null;
        if (map != null) {
            firstUser = firstUser + "\n\n" + map.caption();
            chat.user(firstUser, map.png());
        } else {
            chat.user(firstUser);
        }
        trace = MindTrace.begin(realm.id, number, trigger, model, snap.effort().effort(), snap.effort().reason(),
                firstUser);

        Memory.CycleRecord record = new Memory.CycleRecord();
        coherence = new Coherence();
        record.at = System.currentTimeMillis();
        record.trigger = trigger;
        record.perceived = snap.digest();
        StringBuilder thoughts = new StringBuilder();

        int maxSteps = GodSettings.maxSteps();
        boolean answered = false;
        long firstActAfter = -1;
        // Words that arrived mid-thought and were added to the request now being sent.
        EventLogger.Taken interjected = null;
        int pressed = 0;
        boolean answerPressed = false;
        // Lite profile: a malformed tool call may be sent again once per thought.
        boolean repaired = false;
        for (int step = 0; step < maxSteps; step++) {
            LlmChat.Request request = chat.prepare(snap.effort().effort());
            String requestId = UUID.randomUUID().toString();
            webhook("request", model, requestId, request::bodyJson);
            if (step == 0) tracePrompts(trace, request);
            MindTrace.Request traced = trace.request(step + 1, model, snap.effort().effort());
            traceBody(trace, traced, request);

            Reply reply = stream(chat, request, model, "thought #" + number + " step " + (step + 1), record, thoughts,
                    traced, step + 1);
            if (reply.acts > 0 && firstActAfter < 0) {
                firstActAfter = reply.firstActAt - began;
            }
            if (!reply.finished && reply.acts == 0) {
                // Nothing came of this request; whoever spoke mid-thought must be heard next time.
                if (interjected != null) {
                    EventLogger.restore(interjected);
                    net.bigyous.gptgodmc.viewer.EventFeed.restored(interjected.loggables());
                    God.requestAttention("mortals addressed you while you were busy", true);
                }
                break;
            }
            answered = true;
            interjected = null;
            if (reply.llm.responseJson != null) {
                webhook("response", model, requestId, reply.llm.responseJson);
            }
            if (reply.finished) {
                lastError = null;
            }
            if (reply.stop == LlmReply.Stop.REFUSAL) {
                Journal.add(Journal.Kind.ERROR, cap(Llm.label()) + " declined this thought" + reply.refusalDetail());
                trace.note("error", step + 1, null, cap(Llm.label()) + " declined this thought"
                        + reply.refusalDetail());
                break;
            }
            if (reply.results.isEmpty() && reply.finished && reply.replayable
                    && reply.stop == LlmReply.Stop.PAUSE_TURN && step < maxSteps - 1) {
                // A web search ran long and the turn paused: send it back as it is so it carries on.
                chat.assistant(reply.llm);
                continue;
            }
            if (!addressed.isEmpty() && !answerPressed && reply.results.isEmpty() && reply.finished
                    && reply.replayable && reply.stop == LlmReply.Stop.END_TURN && step < maxSteps - 1
                    && !spoke(record)) {
                // It meant to stay silent; with always-answer it must still say something.
                answerPressed = true;
                chat.assistant(reply.llm);
                chat.user(("[" + addressed + " addressed you and you have not answered. Answer "
                        + "now in words (whisper, vessel_say or announce): even one short line to refuse or dismiss "
                        + "them. Silence is not an answer.]"));
                Journal.add(Journal.Kind.INFO, "Pressed to answer " + addressed);
                trace.note("note", step + 1, "pressed to answer", "Pressed to answer " + addressed);
                continue;
            }
            if (reply.results.isEmpty() && reply.finished && reply.replayable
                    && reply.stop == LlmReply.Stop.END_TURN && step < maxSteps - 1 && pressed < 2) {
                // It stopped while a binding command is still open (often after one way failed). Ask it
                // to finish in this same thought rather than waiting for the next one.
                List<Integer> open = openCommands();
                if (!open.isEmpty()) {
                    pressed++;
                    chat.assistant(reply.llm);
                    String nums = open.stream().map(n -> "#" + n).reduce((x, y) -> x + ", " + y).orElse("");
                    chat.user(("[Binding command " + nums + " is still open and the operator "
                            + "is waiting. Finish it now, in this thought: if one way failed, use another (for "
                            + "example run_commands with fill ... replace). Then close it with command_done, or "
                            + "close it as impossible with the reason.]"));
                    Journal.add(Journal.Kind.INFO, "Pressed to finish binding command " + nums + " in this thought");
                    trace.note("note", step + 1, "pressed to finish", "Pressed to finish binding command " + nums);
                    continue;
                }
            }
            if (reply.results.isEmpty() || !reply.finished) {
                break;
            }
            if (!reply.replayable) {
                // A reply served by the refusal-fallback model can carry a block this SDK version
                // cannot replay; the god has already performed its acts, so the thought ends here.
                Journal.add(Journal.Kind.INFO, "Answered by the fallback model; ending this thought after its acts.");
                trace.note("info", step + 1, null, "Answered by the fallback model; ending after its acts.");
                break;
            }
            if (reply.stop != LlmReply.Stop.TOOL_USE || step == maxSteps - 1) {
                break;
            }
            if (lite && reply.malformed > 0) {
                if (repaired) {
                    Journal.add(Journal.Kind.INFO, "The model sent a malformed tool call again; ending this thought.");
                    trace.note("info", step + 1, null, "Malformed tool call again; ending this thought.");
                    break;
                }
                repaired = true;
            }
            chat.assistant(reply.llm);
            StringBuilder heard = new StringBuilder();
            interjected = interject(heard, record, step + 2);
            chat.toolResults(reply.results, heard.toString());
        }

        if (!answered) {
            // The model never answered: give the events back so the next thought still sees them.
            EventLogger.restore(snap.taken());
            net.bigyous.gptgodmc.viewer.EventFeed.restored(snap.taken().loggables());
            Journal.add(Journal.Kind.CYCLE, "Thought abandoned; events kept for next time");
            trace.end("abandoned: " + cap(Llm.label()) + " never answered; events kept for next time");
            return;
        }
        String t = thoughts.toString().trim();
        record.thought = t.length() > 300 ? t.substring(0, 300) + "…" : t;
        Memory.get().record(record);
        Memory.get().save();
        Musing.afterThought();
        long total = System.currentTimeMillis() - began;
        Journal.add(Journal.Kind.CYCLE, "Done (" + record.acts.size() + " act(s)"
                + (firstActAfter >= 0 ? ", first after " + firstActAfter + " ms" : "") + ", " + total + " ms in all)");
        trace.end("done: " + record.acts.size() + " act(s)"
                + (firstActAfter >= 0 ? ", first after " + firstActAfter + " ms" : "") + ", " + total + " ms");
    }

    /** Who addressed the god among these events ("Jumpathy spoke; Ashwyn prays: ..."), or "". */
    static String addressedBy(EventLogger.Taken taken, Memory memory, int silenceAtOrBelow) {
        if (taken == null) return "";
        java.util.LinkedHashSet<String> who = new java.util.LinkedHashSet<>();
        for (net.bigyous.gptgodmc.loggables.Loggable l : taken.loggables()) {
            if (l == null) continue;
            net.bigyous.gptgodmc.loggables.Loggable.Category c = l.category();
            if (c != net.bigyous.gptgodmc.loggables.Loggable.Category.SPEECH
                    && c != net.bigyous.gptgodmc.loggables.Loggable.Category.PRAYER) {
                continue;
            }
            String reason = l.attentionReason();
            if (reason == null || reason.isBlank()) continue;
            String r = reason.trim();
            // Mortals it truly loathes may still be met with silence.
            String speaker = r.split("[\\s:,]", 2)[0];
            if (memory != null && !speaker.isEmpty() && memory.favorOf(speaker) <= silenceAtOrBelow) continue;
            who.add(r.length() > 80 ? r.substring(0, 80) + "…" : r);
        }
        String all = String.join("; ", who);
        return all.length() > 300 ? all.substring(0, 300) + "…" : all;
    }

    /** Whether this thought has spoken to anyone yet. */
    private static boolean spoke(Memory.CycleRecord record) {
        for (String act : record.acts) {
            if (act.startsWith("whisper") || act.startsWith("announce") || act.startsWith("vessel_say")) return true;
        }
        return false;
    }

    /** Numbers of one-shot binding commands still waiting for command_done. */
    private List<Integer> openCommands() {
        try {
            return realm.get(() -> Edicts.book().pending().stream().filter(e -> !e.standing())
                    .map(e -> e.number).toList());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * Prayers, directives and speech naming the god that arrived while it was acting are appended
     * to the next request (after the tool results), so it can answer in the same thought.
     */
    private EventLogger.Taken interject(StringBuilder content, Memory.CycleRecord record, int step) {
        EventLogger.Taken urgent = EventLogger.takeUrgent();
        if (urgent.loggables().isEmpty()) {
            return null;
        }
        net.bigyous.gptgodmc.viewer.EventFeed.seen(urgent.loggables(), realm.thoughts.get(), trace.id, true);
        EventLogger.Drained d = EventLogger.render(urgent);
        StringBuilder sb = new StringBuilder(
                GodSettings.alwaysAnswer()
                        ? "[While you were acting, mortals addressed you. Answer each of them now in words, even briefly.]"
                        : "[While you were acting, mortals addressed you. Answer now if it merits an answer.]");
        d.directives().forEach(line -> sb.append("\nOperator directive: ").append(line));
        d.prayers().forEach(line -> sb.append("\nPrayer: ").append(line));
        d.speech().forEach(line -> sb.append("\nSpoken: ").append(line));
        d.events().forEach(line -> sb.append('\n').append(line));
        content.append(sb);
        Journal.add(Journal.Kind.PERCEPTION, sb.toString());
        trace.note("interject", step, null, sb.toString());

        String more = digest(d);
        if (!more.isBlank()) {
            String joined = record.perceived == null || record.perceived.isBlank() ? more : record.perceived + "; " + more;
            record.perceived = joined.length() > 500 ? joined.substring(0, 500) + "…" : joined;
        }
        // Those words are being answered now; no separate thought is needed for them.
        if (realm.events().hasPending()) realm.attentionUrgent = false;
        else God.attentionServed(realm);
        return urgent;
    }

    // ---- One streamed reply --------------------------------------------------------------------

    /** What one request amounted to. */
    private static final class Reply {
        /** One result per tool call, in order. */
        final List<ToolAnswer> results = new ArrayList<>();
        /** What the provider reported: stop reason, usage, and the turn to replay. */
        LlmReply llm = new LlmReply();
        LlmReply.Stop stop = LlmReply.Stop.END_TURN;
        /** The reply arrived whole. */
        boolean finished;
        /** The assistant's turn can be sent back in the next request. */
        boolean replayable;
        /** Powers carried out. */
        int acts;
        long firstActAt;
        /** Tool calls refused for arguments that were not valid (cut off, or unfit in the lite profile). */
        int malformed;

        String refusalDetail() {
            return llm.refusalDetail == null ? "" : llm.refusalDetail;
        }
    }

    private record PendingCall(String id, String name, StringBuilder json) {
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Sends one request through the provider's chat and acts on the reply as it streams: prose and
     * thinking go to the journal and the mind viewer, and each power is carried out the moment its
     * call has fully arrived.
     */
    private Reply stream(LlmChat chat, LlmChat.Request request, String model, String purpose,
            Memory.CycleRecord record, StringBuilder thoughts, MindTrace.Request traced, int step) {
        Reply reply = new Reply();
        MindTrace.Thought trace = this.trace;
        Map<Long, MindTrace.Item> live = new HashMap<>();
        String failure = null;
        Map<Long, StringBuilder> prose = new HashMap<>();

        LlmReply got = chat.send(request, new LlmChat.Listener() {
            @Override
            public void token() {
                trace.firstToken(traced);
            }

            @Override
            public void open(long block, boolean thinking) {
                prose.put(block, new StringBuilder());
                live.put(block, trace.open(thinking ? "thinking" : "text", step));
            }

            @Override
            public void delta(long block, String text) {
                prose.computeIfAbsent(block, k -> new StringBuilder()).append(text);
                trace.append(live.get(block), text);
            }

            @Override
            public void close(long block) {
                trace.close(live.remove(block));
                StringBuilder said = prose.remove(block);
                if (said != null && !said.toString().isBlank()) {
                    String text = said.toString().trim();
                    Journal.add(Journal.Kind.THOUGHT, text);
                    thoughts.append(text).append(' ');
                }
            }

            @Override
            public void call(String id, String name, String json) {
                // The call is complete: carry it out now, while the rest is still being written.
                carryOut(new PendingCall(id, name, new StringBuilder(json)), reply, record, trace, step);
            }

            @Override
            public void search(String query) {
                Journal.add(Journal.Kind.TOOL, "web_search{\"query\":\"" + query + "\"}");
                record.acts.add("web_search: " + query);
                trace.note("search", step, "web_search", query);
            }
        });
        reply.llm = got;
        reply.stop = got.stop;
        reply.finished = got.finished;
        reply.replayable = got.replayable;
        if (got.error != null) {
            String why = got.error.message();
            switch (got.error.kind()) {
                case SERVICE -> {
                    handleServiceError(got.error.status(), got.error.body());
                    failure = lastError;
                }
                case NETWORK -> {
                    failure = "network: " + why;
                    lastError = "network: " + why;
                    backoffUntil = System.currentTimeMillis() + 30_000;
                    Journal.add(Journal.Kind.ERROR, "Could not reach " + Llm.label() + " (" + why
                            + "); retrying in 30s.");
                }
                default -> {
                    lastError = why;
                    backoffUntil = System.currentTimeMillis() + 20_000;
                    Journal.add(Journal.Kind.ERROR, "The reply broke off (" + why + "); retrying in 20s.");
                    failure = why;
                }
            }
        }
        long in = got.in, out = got.out, cacheRead = got.cacheRead, cacheWrite = got.cacheWrite;
        long searched = got.searches;
        if (failure != null) trace.note("error", step, null, failure);
        double tracedCost = net.bigyous.gptgodmc.utils.TokenTracker.costOf(model, in, out, cacheRead, cacheWrite);
        trace.usage(traced, in, out, cacheRead, cacheWrite, searched,
                tracedCost < 0 ? -1 : tracedCost + searched * net.bigyous.gptgodmc.utils.TokenTracker.WEB_SEARCH_USD,
                got.stopText, reply.finished, failure);

        if (got.billed) {
            if (searched > 0) {
                net.bigyous.gptgodmc.utils.TokenTracker.addCost(searched * net.bigyous.gptgodmc.utils.TokenTracker.WEB_SEARCH_USD);
                purpose = purpose + " + " + searched + " web search" + (searched == 1 ? "" : "es") + " ($"
                        + String.format(java.util.Locale.ROOT, "%.2f", searched * 0.01) + ")";
            }
            UsageLog.record(model, purpose, in, out, cacheRead, cacheWrite);
        }
        if (got.responseJson != null && trace != MindTrace.Thought.NONE) {
            try {
                String json = got.responseJson.get();
                if (json != null) trace.responseBody(traced, json);
            } catch (Exception e) {
                GPTGOD.LOGGER.debug("Mind viewer could not record a reply", e);
            }
        }
        return reply;
    }

    /** Runs one power and keeps its result for the next request. */
    private void carryOut(PendingCall call, Reply reply, Memory.CycleRecord record, MindTrace.Thought trace,
            int step) {
        // The lite profile reads arguments strictly: one JSON object that fits the power's schema.
        boolean strict = Llm.lite();
        JsonObject input = strict ? ToolCheck.parse(call.json().toString()) : parseInput(call.json().toString());
        if (input == null) {
            trace.note("error", step, call.name(), "Ignored a cut-off call to " + call.name() + ": " + call.json());
            // Cut off (e.g. by the output limit) or malformed: never run a half-specified power.
            reply.results.add(new ToolAnswer(call.id(), call.name(), ToolResult.error(
                    "Invalid input: the arguments were not a complete JSON object. "
                            + "Call the power again with complete arguments.")));
            reply.malformed++;
            Journal.add(Journal.Kind.ERROR, "Ignored a cut-off call to " + call.name());
            return;
        }
        String unfit = strict ? unfit(call.name(), input) : null;
        if (unfit != null) {
            trace.note("error", step, call.name(), "Refused a call to " + call.name() + ": " + unfit);
            reply.results.add(new ToolAnswer(call.id(), call.name(), ToolResult.error(unfit)));
            reply.malformed++;
            Journal.add(Journal.Kind.ERROR, "Refused a call to " + call.name() + ": " + unfit);
            return;
        }
        MindTrace.Item traced = trace.tool(step, call.name(), input.toString());
        ToolRegistry.Outcome outcome = ToolRegistry.execute(call.name(), input, "brain");
        trace.result(traced, outcome.result().text(), outcome.result().isError(),
                outcome.result().hasImage() ? outcome.result().png() : null);
        if (reply.acts++ == 0) {
            reply.firstActAt = System.currentTimeMillis();
        }
        record.acts.add(outcome.act());
        ToolResult result = outcome.result();
        // An act on a mortal it spoke to in this thought quotes its own words back, once.
        String note = coherence.after(call.name(), input, result.text(), result.isError());
        if (note != null) {
            result = result.hasImage() ? ToolResult.image(result.text() + note, result.png())
                    : ToolResult.ok(result.text() + note);
        }
        reply.results.add(new ToolAnswer(call.id(), call.name(), result));
    }

    /**
     * Lite profile: why this call cannot run as sent (a power it was not offered, or arguments that do
     * not fit the schema), phrased so the model can send it again correctly; null when it is fit.
     */
    static String unfit(String name, JsonObject input) {
        List<LlmTool> offered = Llm.tools();
        for (LlmTool t : offered) {
            if (!t.name().equals(name)) continue;
            String problem = ToolCheck.problem(t, input);
            return problem == null ? null
                    : "Invalid input for " + name + ": " + problem + ". It takes " + ToolCheck.hint(t)
                            + ". Call it again with corrected arguments.";
        }
        return "You have no power called '" + name + "'. Your powers: "
                + String.join(", ", offered.stream().map(LlmTool::name).toList()) + ".";
    }

    /**
     * Parses streamed tool arguments. Anything other than one complete JSON object gives null; no
     * arguments at all means an empty object.
     */
    static JsonObject parseInput(String json) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement tree = JsonParser.parseString(json);
            return tree.isJsonObject() ? tree.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** How hard this thought thinks (anthropic.effort, or chosen from what woke it with auto). Server thread. */
    static EffortPicker.Choice pickEffort(EventLogger.Drained d) {
        try {
            Persona p = Persona.current();
            boolean command = !d.directives().isEmpty() || !Edicts.book().pending().isEmpty();
            Memory mem = Memory.get();
            return EffortPicker.choose(GodSettings.effort(), new EffortPicker.Signals(d.prayers(), d.speech(),
                    d.events(), command, p == null ? null : p.shortName(), mem::favorOf));
        } catch (RuntimeException e) {
            return new EffortPicker.Choice(EffortPicker.fixed(GodSettings.effort()), "default");
        }
    }

    static MessageCreateParams params(String model, String system, List<MessageParam> messages) {
        return params(model, system, messages, EffortPicker.fixed(GodSettings.effort()));
    }

    static MessageCreateParams params(String model, String system, List<MessageParam> messages, String effort) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(GodSettings.maxOutputTokens())
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder().text(system)
                                .cacheControl(CacheControlEphemeral.builder().build()).build(),
                        TextBlockParam.builder().text(Prompts.chronicle()).build()))
                .tools(tools())
                .messages(messages)
                .thinking(ThinkingConfigAdaptive.builder().display(ThinkingConfigAdaptive.Display.SUMMARIZED).build())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(EffortPicker.fixed(effort))).build())
                // Cache the growing conversation within a thought, so each tool round re-reads it cheaply.
                .cacheControl(CacheControlEphemeral.builder().build());
        if (GodSettings.refusalFallback() && GodClient.supportsDefaultFallback(model)
                && (GodSettings.baseUrl() == null || GodSettings.baseUrl().isBlank())) {
            b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return b.build();
    }

    /** The god's powers, plus web search when enabled (a server tool: Anthropic runs the search). */
    static List<com.anthropic.models.messages.ToolUnion> tools() {
        List<com.anthropic.models.messages.ToolUnion> powers = ToolRegistry.sdkTools();
        if (Llm.lite()) {
            // The lite profile on Claude: the same core powers a small model gets, and no web search.
            java.util.Set<String> core = Llm.liteToolNames();
            return powers.stream().filter(t -> t.isTool() && core.contains(t.asTool().name())).toList();
        }
        if (!GodSettings.webSearch() || (GodSettings.baseUrl() != null && !GodSettings.baseUrl().isBlank())) {
            return powers;
        }
        List<com.anthropic.models.messages.ToolUnion> all = new ArrayList<>(powers);
        all.add(com.anthropic.models.messages.ToolUnion.ofWebSearchTool20260209(
                com.anthropic.models.messages.WebSearchTool20260209.builder()
                        .maxUses(GodSettings.webSearchMaxUses()).build()));
        return all;
    }

    static ToolResultBlockParam toResult(String id, ToolResult r) {
        ToolResultBlockParam.Builder b = ToolResultBlockParam.builder().toolUseId(id).isError(r.isError());
        if (r.hasImage()) {
            b.content(ToolResultBlockParam.Content.ofBlocks(List.of(
                    ToolResultBlockParam.Content.Block.ofText(TextBlockParam.builder().text(r.text()).build()),
                    ToolResultBlockParam.Content.Block.ofImage(Base64ImageSource.builder()
                            .data(Base64.getEncoder().encodeToString(r.png()))
                            .mediaType(Base64ImageSource.MediaType.IMAGE_PNG)
                            .build()))));
        } else {
            b.content(r.text() == null || r.text().isBlank() ? "done" : r.text());
        }
        return b.build();
    }

    private void handleServiceError(int status, String body) {
        String who = cap(Llm.label());
        lastError = status + ": " + body;
        if (status == 401 || status == 403) {
            backoffUntil = System.currentTimeMillis() + 10 * 60_000;
            Journal.add(Journal.Kind.ERROR, who + " rejected the API key (" + status + "). Fix it with `gptgod setkey "
                    + "<key>` in the console. Pausing thoughts for 10 minutes.");
        } else if (status == 429 || status == 529 || status >= 500) {
            backoffUntil = System.currentTimeMillis() + 60_000;
            Journal.add(Journal.Kind.ERROR, who + " is busy or rate limited (" + status + "); resting for a minute.");
        } else {
            backoffUntil = System.currentTimeMillis() + 20_000;
            Journal.add(Journal.Kind.ERROR, who + " refused the request (" + status + "): " + body);
        }
    }

    /** A short phrase of what this perception contained, for the god's memory. */
    private static String digest(EventLogger.Drained d) {
        List<String> parts = new ArrayList<>();
        for (String p : d.prayers()) {
            parts.add(stripTime(p));
        }
        for (int i = Math.max(0, d.speech().size() - 3); i < d.speech().size(); i++) {
            parts.add(stripTime(d.speech().get(i)));
        }
        if (!d.events().isEmpty()) {
            parts.add(d.events().size() + " other event(s)");
        }
        String s = String.join("; ", parts);
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }

    private static String stripTime(String line) {
        int i = line.indexOf("] ");
        return i > 0 ? line.substring(i + 2) : line;
    }

    /** Gives the mind viewer the whole body of one request, exactly as sent. */
    private static void traceBody(MindTrace.Thought trace, MindTrace.Request traced, LlmChat.Request request) {
        if (trace == MindTrace.Thought.NONE) return;
        try {
            trace.requestBody(traced, request.bodyJson(), request.betas());
        } catch (Exception e) {
            GPTGOD.LOGGER.debug("Mind viewer could not record a request", e);
        }
    }

    /** Gives the mind viewer the system prompt, chronicle and tool list this thought was sent. */
    private static void tracePrompts(MindTrace.Thought trace, LlmChat.Request request) {
        if (trace == MindTrace.Thought.NONE) return;
        try {
            trace.prompts(request.system(), request.chronicle(), request.toolsJson());
        } catch (Exception e) {
            GPTGOD.LOGGER.debug("Mind viewer could not record the prompts", e);
        }
    }

    private static void webhook(String type, String model, String requestId,
            java.util.function.Supplier<String> json) {
        if (GodSettings.webhookUrl() == null || GodSettings.webhookUrl().isBlank()) {
            return;
        }
        try {
            String body = json.get();
            if (body != null) DebugWebhook.send(type, Llm.webhookName(), model, requestId, body);
        } catch (Exception e) {
            GPTGOD.LOGGER.debug("Debug webhook serialization failed", e);
        }
    }
}
