package net.bigyous.gptgodmc.god.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A listener that writes down everything a stream tells it, in order. */
class Recorder implements LlmChat.Listener {
    final List<String> log = new ArrayList<>();
    final List<String[]> calls = new ArrayList<>();
    final StringBuilder prose = new StringBuilder();
    final StringBuilder thinking = new StringBuilder();
    private final java.util.Map<Long, Boolean> kinds = new java.util.HashMap<>();
    int tokens;

    @Override
    public void token() {
        tokens++;
    }

    @Override
    public void open(long block, boolean isThinking) {
        kinds.put(block, isThinking);
        log.add("open " + block + (isThinking ? " thinking" : " text"));
    }

    @Override
    public void delta(long block, String text) {
        (Boolean.TRUE.equals(kinds.get(block)) ? thinking : prose).append(text);
    }

    @Override
    public void close(long block) {
        log.add("close " + block);
    }

    @Override
    public void call(String id, String name, String json) {
        calls.add(new String[] { id, name, json });
        log.add("call " + name);
    }

    @Override
    public void search(String query) {
        log.add("search " + query);
    }

    /** Feeds a recorded stream from src/test/resources/llm/ line by line. */
    static LlmReply replay(String sample, Recorder into, List<String> textCallNames) throws IOException {
        OpenAiStream stream = new OpenAiStream(into, textCallNames);
        try (InputStream in = Recorder.class.getResourceAsStream("/llm/" + sample);
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null;) stream.line(line);
        }
        stream.end();
        return stream.reply();
    }
}
