package net.bigyous.gptgodmc.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;

/** Text to speech: a local Kokoro server (default) or any OpenAI-compatible /v1/audio/speech server, or the built-in Piper. */
final class Synthesizer {
    /** Raw 16-bit mono PCM and its sample rate. */
    record Pcm(short[] samples, int sampleRate) {
    }

    private static final String PIPER_RELEASE = "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/";
    private static final String VOICES = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private Path piperExe;
    private Path piperModel;
    private int piperRate = 22050;
    private boolean piperBroken = false;

    /** Piper's length_scale for an unhurried, solemn delivery (1.0 is the voice's normal pace). */
    static final double SOLEMN = 1.12;

    /**
     * @param pace factor on spoken duration, from {@link GodVoiceFx#pace}: below 1 the engine talks
     *             faster, because the effect chain will slow the recording down again
     */
    Pcm synthesize(String text, double pace) throws IOException {
        String engine = GodSettings.ttsEngine();
        return switch (engine) {
            case "off" -> null;
            case "kokoro", "openai-compatible", "http", "openai" -> httpOrPiper(text, pace);
            default -> piper(text, pace);
        };
    }

    private volatile long lastFallbackWarning = 0;

    /** The speech server, or the built-in Piper when it does not answer (voice.tts.fallback-to-piper). */
    private Pcm httpOrPiper(String text, double pace) throws IOException {
        try {
            return http(text, pace);
        } catch (IOException e) {
            if (!GodSettings.ttsFallbackToPiper()) throw e;
            long now = System.currentTimeMillis();
            if (now - lastFallbackWarning > 10 * 60_000L) {
                lastFallbackWarning = now;
                GPTGOD.LOGGER.warn("The speech server at " + GodSettings.ttsUrl() + " did not answer ("
                        + e.getMessage() + "); speaking with the built-in Piper voice instead. Start your "
                        + "Kokoro server, or set voice.tts.engine: piper.");
            }
            return piper(text, pace);
        }
    }

    static String lengthScale(double pace) {
        return String.format(Locale.ROOT, "%.3f", SOLEMN * Math.max(0.5, Math.min(1.5, pace)));
    }

    // ---- Piper ---------------------------------------------------------------------------------

    private synchronized void preparePiper() throws IOException {
        if (piperExe != null && piperModel != null) return;
        if (piperBroken) throw new IOException("Piper is unavailable (see earlier errors)");
        Path base = GPTGOD.get().getDataFolder().toPath().resolve("piper");
        String configured = GodSettings.piperPath();
        if (configured != null && !configured.isBlank()) {
            piperExe = Path.of(configured);
        } else {
            piperExe = findExecutable(base);
            if (piperExe == null) {
                if (!GodSettings.ttsAutoDownload()) {
                    piperBroken = true;
                    throw new IOException("Piper is not installed. Set voice.tts.piper-path or enable auto-download.");
                }
                String asset = piperAsset();
                Path archive = base.resolve(asset);
                Downloads.fetch(PIPER_RELEASE + asset, archive);
                if (asset.endsWith(".zip")) {
                    Downloads.unzip(archive, base);
                } else {
                    Downloads.untarGz(archive, base);
                }
                Files.deleteIfExists(archive);
                piperExe = findExecutable(base);
                if (piperExe == null) {
                    piperBroken = true;
                    throw new IOException("Piper downloaded but its executable was not found in " + base);
                }
            }
        }
        String voice = GodSettings.piperVoice();
        if (voice.toLowerCase(Locale.ROOT).endsWith(".onnx")) {
            piperModel = Path.of(voice);
        } else {
            piperModel = base.resolve("voices").resolve(voice + ".onnx");
            Path json = base.resolve("voices").resolve(voice + ".onnx.json");
            if (!Files.exists(piperModel) || !Files.exists(json)) {
                if (!GodSettings.ttsAutoDownload()) {
                    throw new IOException("Piper voice " + voice + " is missing from " + piperModel.getParent());
                }
                String url = voiceUrl(voice);
                Downloads.fetch(url + ".json", json);
                Downloads.fetch(url, piperModel);
            }
        }
        Path json = Path.of(piperModel + ".json");
        if (Files.exists(json)) {
            try {
                JsonObject cfg = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
                piperRate = cfg.getAsJsonObject("audio").get("sample_rate").getAsInt();
            } catch (RuntimeException e) {
                piperRate = 22050;
            }
        }
    }

    private Pcm piper(String text, double pace) throws IOException {
        preparePiper();
        ProcessBuilder pb = new ProcessBuilder(piperExe.toString(), "--model", piperModel.toString(), "--output_raw",
                "--length_scale", lengthScale(pace), "--sentence_silence", "0.35");
        pb.directory(piperExe.getParent().toFile());
        Map<String, String> env = pb.environment();
        String dir = piperExe.getParent().toString();
        env.merge("LD_LIBRARY_PATH", dir, (a, b) -> b + ":" + a);
        env.merge("DYLD_LIBRARY_PATH", dir, (a, b) -> b + ":" + a);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process proc = pb.start();
        try (OutputStream stdin = proc.getOutputStream()) {
            stdin.write((text.replace('\n', ' ') + "\n").getBytes(StandardCharsets.UTF_8));
        }
        byte[] raw;
        try (InputStream out = proc.getInputStream()) {
            raw = out.readAllBytes();
        }
        try {
            if (!proc.waitFor(60, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new IOException("Piper timed out");
            }
        } catch (InterruptedException e) {
            proc.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
        if (raw.length < 2) {
            throw new IOException("Piper produced no audio (exit " + proc.exitValue() + ")");
        }
        return new Pcm(littleEndianShorts(raw), piperRate);
    }

    private static Path findExecutable(Path base) throws IOException {
        if (!Files.isDirectory(base)) return null;
        String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "piper.exe" : "piper";
        try (Stream<Path> walk = Files.walk(base, 3)) {
            return walk.filter(p -> p.getFileName().toString().equals(exe) && Files.isRegularFile(p)).findFirst()
                    .orElse(null);
        }
    }

    private static String piperAsset() throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        if (os.contains("win")) return "piper_windows_amd64.zip";
        if (os.contains("mac")) return arm ? "piper_macos_aarch64.tar.gz" : "piper_macos_x64.tar.gz";
        if (os.contains("linux")) {
            if (arm) return "piper_linux_aarch64.tar.gz";
            if (arch.contains("arm")) return "piper_linux_armv7l.tar.gz";
            return "piper_linux_x86_64.tar.gz";
        }
        throw new IOException("No Piper build for " + os + "/" + arch + "; set voice.tts.piper-path");
    }

    /** en_US-ryan-medium → .../en/en_US/ryan/medium/en_US-ryan-medium.onnx */
    static String voiceUrl(String voice) throws IOException {
        String[] parts = voice.split("-");
        if (parts.length < 3) throw new IOException("Piper voice names look like en_US-ryan-medium");
        String lang = parts[0];
        String quality = parts[parts.length - 1];
        String name = String.join("-", List.of(parts).subList(1, parts.length - 1));
        String family = lang.contains("_") ? lang.substring(0, lang.indexOf('_')) : lang;
        return VOICES + family + "/" + lang + "/" + name + "/" + quality + "/" + voice + ".onnx";
    }

    // ---- OpenAI-compatible HTTP ----------------------------------------------------------------

    private Pcm http(String text, double pace) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", GodSettings.ttsModel());
        body.addProperty("input", text);
        body.addProperty("voice", GodSettings.ttsVoice());
        body.addProperty("response_format", "pcm");
        // "speed" is part of the OpenAI speech API (0.25 to 4), so compatible servers accept it.
        if (Math.abs(pace - 1) > 0.01) {
            body.addProperty("speed", Math.round(100.0 / Math.max(0.5, Math.min(1.5, pace))) / 100.0);
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(GodSettings.ttsUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        String key = GodSettings.ttsKey();
        if (key != null && !key.isBlank()) req.header("Authorization", "Bearer " + key);
        try {
            HttpResponse<byte[]> res = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                throw new IOException("TTS server returned " + res.statusCode() + ": "
                        + new String(res.body(), 0, Math.min(300, res.body().length), StandardCharsets.UTF_8));
            }
            return new Pcm(littleEndianShorts(res.body()), GodSettings.ttsSampleRate());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    static short[] littleEndianShorts(byte[] raw) {
        short[] s = new short[raw.length / 2];
        for (int i = 0; i < s.length; i++) {
            s[i] = (short) ((raw[2 * i] & 0xFF) | (raw[2 * i + 1] << 8));
        }
        return s;
    }

    static byte[] wav(short[] pcm, int rate) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length * 2);
        int dataLen = pcm.length * 2;
        writeAscii(out, "RIFF");
        writeInt(out, 36 + dataLen);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, rate);
        writeInt(out, rate * 2);
        writeShort(out, 2);
        writeShort(out, 16);
        writeAscii(out, "data");
        writeInt(out, dataLen);
        for (short v : pcm) writeShort(out, v);
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream o, String s) {
        o.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }
}
