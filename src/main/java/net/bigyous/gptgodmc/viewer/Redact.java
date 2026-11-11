package net.bigyous.gptgodmc.viewer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps secrets off the mind viewer: the Anthropic API key, and every config value whose key names a
 * key, token, secret or password (and webhook URLs, which often carry one). Applied to every response
 * body the viewer sends, so nothing slips through a perception, a tool result or a prompt.
 */
public final class Redact {
    public static final String MASK = "[redacted]";
    /** Config keys whose values are secret (matched on the last segment, ignoring case). */
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i).*(api[-_]?key|token|secret|password|passwd|webhook[-_]?url|credential|auth)$");
    /** Anything shaped like an Anthropic or OpenAI key, or a bearer header. */
    private static final Pattern SHAPES = Pattern.compile(
            "sk-ant-[A-Za-z0-9_\\-]{8,}|sk-[A-Za-z0-9_\\-]{20,}|(?i:bearer)\\s+[A-Za-z0-9._\\-]{16,}");
    /** Shorter values are not masked one by one (too likely to hit ordinary words). */
    static final int MIN_SECRET = 6;

    private final List<String> secrets;

    private Redact(Collection<String> secrets) {
        Set<String> all = new LinkedHashSet<>();
        for (String s : secrets) {
            if (s == null) continue;
            String t = s.trim();
            if (t.length() < MIN_SECRET) continue;
            all.add(t);
            String escaped = jsonEscape(t);
            if (!escaped.equals(t)) all.add(escaped);
        }
        List<String> sorted = new ArrayList<>(all);
        // Longest first, so a secret containing another is masked whole.
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        this.secrets = sorted;
    }

    public static Redact of(Collection<String> secrets) {
        return new Redact(secrets);
    }

    /** True for a config key (dotted path) whose value must never be shown. */
    public static boolean isSecretKey(String path) {
        if (path == null) return false;
        String last = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return SECRET_KEY.matcher(last).matches();
    }

    /** The values of every secret key in a flattened config (dotted path → value). */
    public static List<String> secretsIn(Map<String, ?> flat) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : flat.entrySet()) {
            if (!isSecretKey(e.getKey())) continue;
            Object v = e.getValue();
            if (v instanceof Collection<?> c) {
                for (Object o : c) {
                    if (o != null) out.add(o.toString());
                }
            } else if (v != null && !(v instanceof Map)) {
                out.add(v.toString());
            }
        }
        return out;
    }

    /** The text with every known secret and key-shaped string masked. */
    public String apply(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        for (String s : secrets) {
            if (out.contains(s)) out = out.replace(s, MASK);
        }
        return SHAPES.matcher(out).replaceAll(MASK);
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                case '=' -> sb.append("\\u003d");
                case '\'' -> sb.append("\\u0027");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
