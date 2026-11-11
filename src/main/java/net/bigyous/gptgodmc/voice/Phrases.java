package net.bigyous.gptgodmc.voice;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a line into parts to be spoken one after another, so the first words can play while the rest
 * is still being synthesized. The first part is kept short (it decides how soon the god is heard);
 * later parts are whole sentences joined up to a comfortable length. Pure, so it is unit tested.
 */
final class Phrases {
    /** A line this short is spoken in one piece. */
    static final int WHOLE = 60;
    /** The first part is cut at a clause mark if its sentence runs longer than this. */
    static final int FIRST_MAX = 80;
    /** Later parts gather sentences up to this length. */
    static final int REST_MAX = 140;

    private Phrases() {
    }

    /** A string field read out of JSON that is still being written: what there is of it, and whether it is finished. */
    record Partial(String text, boolean closed) {
    }

    /**
     * Reads a string field from incomplete JSON (a tool call still streaming in), e.g. the message of
     * {"player":"Ash","message":"Rise, and be he. Null when the field has not begun.
     */
    static Partial partialField(String json, String field) {
        if (json == null) return null;
        int k = json.indexOf("\"" + field + "\"");
        if (k < 0) return null;
        int i = k + field.length() + 2;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != ':') return null;
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null;
        i++;
        StringBuilder out = new StringBuilder();
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') return new Partial(out.toString(), true);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 >= json.length()) break; // an escape cut in half
            char e = json.charAt(++i);
            switch (e) {
                case 'n', 'r', 't' -> out.append(' ');
                case 'u' -> {
                    if (i + 4 >= json.length()) return new Partial(out.toString(), false);
                    try {
                        out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                    } catch (NumberFormatException ex) {
                        // a broken escape: skip it
                    }
                    i += 4;
                }
                default -> out.append(e);
            }
        }
        return new Partial(out.toString(), false);
    }

    /**
     * The opening phrase of a line that is still being written, once it is certain (the text already
     * runs past it), or of a finished line. Null while it cannot be known yet.
     */
    static String opening(Partial p) {
        if (p == null || p.text().isBlank()) return null;
        List<String> parts = split(p.text());
        if (parts.isEmpty()) return null;
        if (p.closed()) return parts.get(0);
        return parts.size() >= 2 ? parts.get(0) : null;
    }

    static List<String> split(String text) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return List.of();
        if (t.length() <= WHOLE) return List.of(t);
        List<String> sentences = new ArrayList<>(List.of(t.split("(?<=[.!?…][\"')\\]]?)\\s+")));

        // The opening: one sentence, cut at a comma if long, joined to the next if it is only a word.
        String first = sentences.remove(0);
        if (first.length() > FIRST_MAX) {
            int cut = clauseEnd(first);
            if (cut > 0) {
                sentences.add(0, first.substring(cut).trim());
                first = first.substring(0, cut).trim();
            }
        } else if (first.length() < 15 && !sentences.isEmpty()
                && first.length() + 1 + sentences.get(0).length() <= FIRST_MAX) {
            first = first + " " + sentences.remove(0);
        }
        List<String> out = new ArrayList<>();
        out.add(first);

        StringBuilder part = new StringBuilder();
        for (String s : sentences) {
            if (part.length() > 0 && part.length() + 1 + s.length() > REST_MAX) {
                out.add(part.toString());
                part.setLength(0);
            }
            if (part.length() > 0) part.append(' ');
            part.append(s);
        }
        if (part.length() > 0) out.add(part.toString());
        return out;
    }

    /** Just past the last clause mark (", " "; " ": " "— ") that leaves a first part of 20 to FIRST_MAX characters, or -1. */
    private static int clauseEnd(String s) {
        int best = -1;
        for (int i = 20; i < Math.min(FIRST_MAX, s.length() - 1); i++) {
            char c = s.charAt(i);
            if ((c == ',' || c == ';' || c == ':' || c == '—') && s.charAt(i + 1) == ' ') best = i + 1;
        }
        return best;
    }
}
