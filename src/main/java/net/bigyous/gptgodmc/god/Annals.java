package net.bigyous.gptgodmc.god;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * The god's complete, permanent record: every event its log received and every act it performed,
 * one JSON object per line in god/annals/YYYY-MM.jsonl (per realm). Appends are buffered in memory
 * and written by a background thread every few seconds; {@link #flushAll()} writes what is left at
 * shutdown. Searching reads the files backwards (newest first) and stops at a limit.
 */
public final class Annals {
    /** One line of the annals. */
    public record Entry(long at, String kind, List<String> players, String text) {
    }

    /** What a search found, newest first; {@code more} is true when further matches exist. */
    public record Found(List<Entry> entries, boolean more) {
    }

    private static final int MAX_TEXT = 1500;
    private static final int FLUSH_AT = 256;
    private static final long FLUSH_DELAY_MS = 3000;
    /** Lines a single search may read before giving up (keeps a search well under a second). */
    private static final int MAX_SCAN = 400_000;
    private static final int RAM_KEEP = 4000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Gson GSON = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private static final Map<Path, Annals> OPEN = new ConcurrentHashMap<>();
    private static ScheduledExecutorService flusher;

    /** Where the files go; null keeps entries in RAM only (unit tests, no server). */
    private final Path dir;
    private final ZoneId zone;
    private final List<Entry> pending = new ArrayList<>();
    private final Deque<Entry> ram = new ArrayDeque<>();
    private final Object writeLock = new Object();
    private boolean flushScheduled;
    /** Set when its god was removed: nothing more is written to its folder. */
    private volatile boolean closed;
    private long lastPrune;

    Annals(Path dir, ZoneId zone) {
        this.dir = dir;
        this.zone = zone;
    }

    /** The annals kept in this folder (one instance per folder, so a /god forget keeps the same log). */
    static Annals forDir(Path dir) {
        if (dir == null) return new Annals(null, ZoneId.systemDefault());
        return OPEN.computeIfAbsent(dir.toAbsolutePath().normalize(), d -> new Annals(d, ZoneId.systemDefault()));
    }

    /** The current god's annals. */
    public static Annals get() {
        return Memory.get().annals();
    }

    // ---- Writing -------------------------------------------------------------------------------

    public void append(String kind, Collection<String> players, String text) {
        append(System.currentTimeMillis(), kind, players, text);
    }

    void append(long at, String kind, Collection<String> players, String text) {
        if (closed || text == null || text.isBlank() || !GodSettings.annalsEnabled()) return;
        String t = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > MAX_TEXT) t = t.substring(0, MAX_TEXT) + "…";
        Entry e = new Entry(at, kind == null ? "event" : kind, players == null ? List.of() : List.copyOf(players), t);
        boolean schedule;
        long delay;
        synchronized (this) {
            if (dir == null) {
                ram.addLast(e);
                while (ram.size() > RAM_KEEP) ram.removeFirst();
                return;
            }
            pending.add(e);
            schedule = !flushScheduled || pending.size() >= FLUSH_AT;
            delay = pending.size() >= FLUSH_AT ? 0 : FLUSH_DELAY_MS;
            flushScheduled = true;
        }
        if (schedule) scheduleFlush(delay);
    }

    private void scheduleFlush(long delayMs) {
        try {
            executor().schedule(this::flushQuietly, delayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            // Shutting down: flushAll() takes care of what is left.
        }
    }

    private static synchronized ScheduledExecutorService executor() {
        if (flusher == null || flusher.isShutdown()) {
            flusher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "god-annals");
                t.setDaemon(true);
                return t;
            });
        }
        return flusher;
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not write the annals", e);
        }
    }

    /** Writes buffered entries to their month's file. */
    public void flush() {
        if (dir == null) return;
        synchronized (writeLock) {
            List<Entry> batch;
            synchronized (this) {
                batch = new ArrayList<>(pending);
                pending.clear();
                flushScheduled = false;
            }
            if (!batch.isEmpty()) {
                Map<String, StringBuilder> byFile = new LinkedHashMap<>();
                for (Entry e : batch) {
                    byFile.computeIfAbsent(fileName(e.at()), k -> new StringBuilder()).append(toJson(e)).append('\n');
                }
                try {
                    Files.createDirectories(dir);
                    for (Map.Entry<String, StringBuilder> f : byFile.entrySet()) {
                        try (BufferedWriter w = Files.newBufferedWriter(dir.resolve(f.getKey()), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                            w.write(f.getValue().toString());
                        }
                    }
                } catch (IOException e) {
                    GPTGOD.LOGGER.warn("Could not write the annals in " + dir + ": " + e.getMessage());
                }
            }
            long now = System.currentTimeMillis();
            if (now - lastPrune > 3_600_000L) {
                lastPrune = now;
                prune(now, GodSettings.annalsRetentionDays());
            }
        }
    }

    /** Writes what is buffered for this folder and stops writing there (its god was removed). */
    static void close(Path dir) {
        Annals a = OPEN.remove(dir.toAbsolutePath().normalize());
        if (a == null) return;
        try {
            a.flush();
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not write the annals", e);
        }
        a.closed = true;
    }

    /** Writes every god's buffered entries now and stops the writer thread (plugin shutdown). */
    public static void flushAll() {
        for (Annals a : OPEN.values()) {
            try {
                a.flush();
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Could not write the annals", e);
            }
        }
        synchronized (Annals.class) {
            if (flusher != null) flusher.shutdownNow();
            flusher = null;
        }
    }

    /** Deletes month files whose every entry is older than the retention window (0 days = keep all). */
    int prune(long now, int days) {
        if (dir == null || days <= 0 || !Files.isDirectory(dir)) return 0;
        long cutoff = now - days * 86_400_000L;
        int deleted = 0;
        for (Path f : monthFiles()) {
            YearMonth month = monthOf(f);
            if (month == null) continue;
            long end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
            if (end <= cutoff) {
                try {
                    Files.deleteIfExists(f);
                    deleted++;
                } catch (IOException e) {
                    GPTGOD.LOGGER.warn("Could not delete old annals " + f + ": " + e.getMessage());
                }
            }
        }
        return deleted;
    }

    String fileName(long at) {
        return YearMonth.from(Instant.ofEpochMilli(at).atZone(zone)) + ".jsonl";
    }

    private static YearMonth monthOf(Path f) {
        String n = f.getFileName().toString();
        try {
            return YearMonth.parse(n.substring(0, n.length() - ".jsonl".length()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Month files, newest first. */
    private List<Path> monthFiles() {
        if (dir == null || !Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().matches("\\d{4}-\\d{2}\\.jsonl"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private String toJson(Entry e) {
        JsonObject o = new JsonObject();
        o.addProperty("at", e.at());
        o.addProperty("time", STAMP.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(e.at()), zone)));
        o.addProperty("kind", e.kind());
        JsonArray p = new JsonArray();
        e.players().forEach(p::add);
        o.add("players", p);
        o.addProperty("text", e.text());
        return GSON.toJson(o);
    }

    private static Entry parse(String line) {
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            List<String> players = new ArrayList<>();
            if (o.has("players") && o.get("players").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("players")) players.add(el.getAsString());
            }
            return new Entry(o.get("at").getAsLong(), o.has("kind") ? o.get("kind").getAsString() : "event", players,
                    o.has("text") ? o.get("text").getAsString() : "");
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- Searching -----------------------------------------------------------------------------

    /**
     * Newest matching entries first. Every word of {@code query} must appear in the text (or player
     * names); {@code player} must be among the entry's players or named in its text; {@code kind}
     * matches exactly; entries older than {@code since} (epoch ms, 0 = any) are not considered.
     * Safe off the main thread; touches only files and this god's buffer.
     */
    public Found search(String query, String player, String kind, long since, int limit) {
        int max = Math.max(1, limit);
        List<String> words = new ArrayList<>();
        if (query != null) {
            for (String w : query.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (!w.isBlank()) words.add(w);
            }
        }
        String who = player == null || player.isBlank() ? null : player.trim().toLowerCase(Locale.ROOT);
        String k = kind == null || kind.isBlank() ? null : kind.trim().toLowerCase(Locale.ROOT);
        int days = GodSettings.annalsRetentionDays();
        long floor = Math.max(since, days > 0 ? System.currentTimeMillis() - days * 86_400_000L : 0);

        List<Entry> buffered;
        Map<Path, Long> lengths = new LinkedHashMap<>();
        synchronized (writeLock) {
            synchronized (this) {
                buffered = new ArrayList<>(dir == null ? ram : pending);
            }
            for (Path f : monthFiles()) {
                try {
                    lengths.put(f, Files.size(f));
                } catch (IOException e) {
                    // vanished: skip it
                }
            }
        }
        List<Entry> out = new ArrayList<>();
        Matcher m = new Matcher(words, who, k, floor, max + 1, out);
        for (int i = buffered.size() - 1; i >= 0 && !m.done; i--) {
            m.offer(buffered.get(i));
        }
        for (Map.Entry<Path, Long> f : lengths.entrySet()) {
            if (m.done) break;
            YearMonth month = monthOf(f.getKey());
            if (month != null && month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() <= floor) {
                break; // this file and every older one are before the window
            }
            readBackwards(f.getKey(), f.getValue(), m);
        }
        boolean more = out.size() > max;
        return new Found(more ? List.copyOf(out.subList(0, max)) : List.copyOf(out), more);
    }

    /** Collects matches; {@code done} once enough were found, the window ended, or the scan ran long. */
    private static final class Matcher {
        final List<String> words;
        final String who;
        final String kind;
        final long floor;
        final int want;
        final List<Entry> out;
        int scanned;
        boolean done;

        Matcher(List<String> words, String who, String kind, long floor, int want, List<Entry> out) {
            this.words = words;
            this.who = who;
            this.kind = kind;
            this.floor = floor;
            this.want = want;
            this.out = out;
        }

        void offer(Entry e) {
            if (done || e == null) return;
            if (++scanned > MAX_SCAN) {
                done = true;
                return;
            }
            if (e.at() < floor) {
                done = true; // entries are in time order: everything further back is older still
                return;
            }
            if (kind != null && !kind.equals(e.kind())) return;
            String text = e.text().toLowerCase(Locale.ROOT);
            if (who != null && e.players().stream().noneMatch(p -> p.equalsIgnoreCase(who)) && !text.contains(who)) {
                return;
            }
            String hay = words.isEmpty() ? "" : text + " " + String.join(" ", e.players()).toLowerCase(Locale.ROOT);
            for (String w : words) {
                if (!hay.contains(w)) return;
            }
            out.add(e);
            if (out.size() >= want) done = true;
        }
    }

    /** Feeds the file's lines to the matcher from the last line to the first, in 64 KB blocks. */
    private static void readBackwards(Path file, long length, Matcher m) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long pos = Math.min(length, raf.length());
            byte[] tail = new byte[0];
            while (pos > 0 && !m.done) {
                int n = (int) Math.min(65_536, pos);
                pos -= n;
                byte[] data = new byte[n + tail.length];
                raf.seek(pos);
                raf.readFully(data, 0, n);
                System.arraycopy(tail, 0, data, n, tail.length);
                int end = data.length;
                for (int i = data.length - 1; i >= 0 && !m.done; i--) {
                    if (data[i] == '\n') {
                        emit(data, i + 1, end, m);
                        end = i;
                    }
                }
                tail = Arrays.copyOf(data, end);
            }
            if (!m.done && tail.length > 0) emit(tail, 0, tail.length, m);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not read the annals " + file + ": " + e.getMessage());
        }
    }

    private static void emit(byte[] data, int from, int to, Matcher m) {
        if (to <= from) return;
        String line = new String(data, from, to - from, StandardCharsets.UTF_8).trim();
        if (line.isEmpty()) return;
        // Cheap checks before parsing JSON: the time sits at the start of every line we write.
        if (line.startsWith("{\"at\":")) {
            int comma = line.indexOf(',', 6);
            try {
                long at = Long.parseLong(line.substring(6, comma < 0 ? line.length() : comma));
                if (at < m.floor) {
                    m.done = true;
                    return;
                }
            } catch (RuntimeException e) {
                // fall through to the full parse
            }
            String lower = line.toLowerCase(Locale.ROOT);
            boolean maybe = (m.who == null || lower.contains(m.who)) && (m.kind == null || lower.contains(m.kind));
            for (String w : m.words) {
                // Quotes and backslashes are escaped in the file; leave those words to the full check.
                if (w.indexOf('"') < 0 && w.indexOf('\\') < 0) maybe &= lower.contains(w);
            }
            if (!maybe) {
                if (++m.scanned > MAX_SCAN) m.done = true;
                return;
            }
        }
        m.offer(parse(line));
    }
}
