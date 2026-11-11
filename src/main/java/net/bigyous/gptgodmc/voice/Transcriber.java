package net.bigyous.gptgodmc.voice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonParser;

import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperSamplingStrategy;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;

/** Speech to text: whisper.cpp on this machine (default), or an OpenAI-compatible server. */
final class Transcriber {
    private static final String MODELS = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-";
    private static final Set<String> HALLUCINATIONS = Set.of("", "you", "thank you.", "thanks for watching!",
            "thank you for watching.", "[blank_audio]", "(music)", "[music]", "(silence)", "bye.", ".");

    private WhisperJNI whisper;
    private WhisperContext context;
    private boolean broken = false;

    /** pcm: 48 kHz mono from Simple Voice Chat. Returns null when nothing intelligible was said. */
    synchronized String transcribe(short[] pcm48k) throws IOException {
        if (pcm48k.length < 48_000 * 0.4) {
            return null;
        }
        String engine = GodSettings.transcriptionEngine();
        String text = switch (engine) {
            case "off" -> null;
            case "server", "openai-compatible", "http" -> remote(pcm48k);
            default -> local(pcm48k);
        };
        if (text == null) return null;
        text = text.replaceAll("\\s+", " ").trim();
        return HALLUCINATIONS.contains(text.toLowerCase(Locale.ROOT)) ? null : text;
    }

    private String local(short[] pcm48k) throws IOException {
        if (broken) return null;
        if (context == null) {
            try {
                Path model = GPTGOD.get().getDataFolder().toPath().resolve("whisper")
                        .resolve("ggml-" + GodSettings.whisperModel() + ".bin");
                if (!Files.exists(model)) {
                    if (!GodSettings.whisperAutoDownload()) {
                        broken = true;
                        throw new IOException("Whisper model missing: " + model);
                    }
                    Downloads.fetch(MODELS + GodSettings.whisperModel() + ".bin", model);
                }
                WhisperJNI.loadLibrary(msg -> GPTGOD.LOGGER.debug("[whisper] " + msg));
                WhisperJNI.setLibraryLogger(msg -> GPTGOD.LOGGER.debug("[whisper] " + msg));
                whisper = new WhisperJNI();
                context = whisper.init(model);
                GPTGOD.LOGGER.info("Local Whisper ready (" + model.getFileName() + ")");
            } catch (IOException | RuntimeException | UnsatisfiedLinkError e) {
                broken = true;
                throw new IOException("Local Whisper could not start: " + e.getMessage(), e);
            }
        }
        float[] samples = GodVoiceFx.resample(GodVoiceFx.toFloat(pcm48k), 48_000, 16_000);
        WhisperFullParams params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
        String lang = GodSettings.language();
        params.language = GodSettings.whisperModel().endsWith(".en") ? "en" : lang;
        params.nThreads = GodSettings.whisperThreads();
        params.noContext = true;
        params.singleSegment = false;
        params.printProgress = false;
        params.printRealtime = false;
        params.printTimestamps = false;
        params.suppressBlank = true;
        params.suppressNonSpeechTokens = true;
        int rc = whisper.full(context, params, samples, samples.length);
        if (rc != 0) {
            throw new IOException("whisper.cpp returned " + rc);
        }
        StringBuilder sb = new StringBuilder();
        int segments = whisper.fullNSegments(context);
        for (int i = 0; i < segments; i++) {
            sb.append(whisper.fullGetSegmentText(context, i));
        }
        return sb.toString();
    }

    private String remote(short[] pcm48k) throws IOException {
        short[] pcm16 = GodVoiceFx.toShort(GodVoiceFx.resample(GodVoiceFx.toFloat(pcm48k), 48_000, 16_000));
        byte[] wav = Synthesizer.wav(pcm16, 16_000);
        String boundary = "----god" + UUID.randomUUID();
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n"
                + GodSettings.transcriptionServerModel() + "\r\n--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\n" + GodSettings.language()
                + "\r\n--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] h = head.getBytes(StandardCharsets.UTF_8);
        byte[] t = tail.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[h.length + wav.length + t.length];
        System.arraycopy(h, 0, body, 0, h.length);
        System.arraycopy(wav, 0, body, h.length, wav.length);
        System.arraycopy(t, 0, body, h.length + wav.length, t.length);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(GodSettings.transcriptionServerUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        String key = GodSettings.transcriptionServerKey();
        if (key != null && !key.isBlank()) req.header("Authorization", "Bearer " + key);
        try {
            HttpResponse<String> res = HttpClient.newHttpClient().send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IOException("Transcription server returned " + res.statusCode() + ": " + res.body());
            }
            return JsonParser.parseString(res.body()).getAsJsonObject().get("text").getAsString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    synchronized void close() {
        if (context != null) {
            try {
                context.close();
            } catch (RuntimeException ignored) {
                // native cleanup is best effort
            }
            context = null;
        }
    }
}
