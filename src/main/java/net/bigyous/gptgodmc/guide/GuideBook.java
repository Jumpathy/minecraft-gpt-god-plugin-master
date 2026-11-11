package net.bigyous.gptgodmc.guide;

import java.util.ArrayList;
import java.util.List;

import net.bigyous.gptgodmc.guide.Guide.Block;
import net.bigyous.gptgodmc.guide.Guide.Book;
import net.bigyous.gptgodmc.guide.Guide.Chapter;
import net.bigyous.gptgodmc.guide.Guide.Cmd;
import net.bigyous.gptgodmc.guide.Guide.GodLine;
import net.bigyous.gptgodmc.guide.Guide.Heading;
import net.bigyous.gptgodmc.guide.Guide.Para;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Lays the guide out as written-book pages. Pure: lines are wrapped with the vanilla font's own widths
 * ({@link #width}), so the client never wraps them again and no page overflows its 14 lines. Page 1 is the
 * title page, then the contents (each chapter a link to its page), then one chapter after another, each
 * starting on a fresh page.
 */
public final class GuideBook {
    private GuideBook() {
    }

    /** Pixels of text a book line holds (114), less a little in case a client font differs. */
    public static final int WIDTH = 112;
    public static final int LINES = 14;
    /** Written books hold at most 100 pages. */
    public static final int MAX_PAGES = 100;
    /** Where contents titles start: room for a two-digit page number and its gap. */
    private static final int CONTENTS_INDENT = 16;

    public enum Style {
        TEXT(-1, false), TITLE(0x5A189A, true), HEADING(0x3C096C, true), CMD(0x14457A, false),
        DIM(0x4A4A4A, false), LINK(0x1F3F94, false);

        public final int color;
        public final boolean bold;

        Style(int color, boolean bold) {
            this.color = color;
            this.bold = bold;
        }
    }

    /** color -1 = the style's; link = the page it turns to (1-based), 0 = none. */
    public record Run(String text, Style style, int color, int link) {
        Run(String text, Style style) {
            this(text, style, -1, 0);
        }
    }

    public record Line(List<Run> runs) {
        static final Line BLANK = new Line(List.of());

        public String text() {
            StringBuilder sb = new StringBuilder();
            runs.forEach(r -> sb.append(r.text()));
            return sb.toString();
        }

        public int width() {
            int w = 0;
            for (Run r : runs) w += GuideBook.width(r.text(), r.style().bold);
            return w;
        }

        boolean blank() {
            return runs.isEmpty() || text().isBlank();
        }
    }

    public record Page(List<Line> lines) {
        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Line l : lines) sb.append(l.text()).append('\n');
            return sb.toString();
        }
    }

    // ---- Font --------------------------------------------------------------------------------------

    /** How far the vanilla font moves for one character (glyph plus its one-pixel gap); bold adds one. */
    public static int advance(char c) {
        return switch (c) {
            case ' ', 'I', 't', '[', ']', '(', ')', '{', '}', '"', '*' -> 4;
            case '!', '.', ',', ':', ';', '|', 'i', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'f', 'k', '<', '>' -> 5;
            case '@', '~' -> 7;
            default -> c < 0x7F ? 6 : 8; // other scripts: wider than most, so a line is never too long
        };
    }

    public static int width(String s, boolean bold) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += advance(s.charAt(i)) + (bold ? 1 : 0);
        return w;
    }

    /** Word-wraps text into lines of at most width pixels (a word longer than a line is cut). */
    public static List<String> wrap(String text, int width, boolean bold) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            String tried = line.isEmpty() ? word : line + " " + word;
            if (width(tried, bold) <= width) {
                line.setLength(0);
                line.append(tried);
                continue;
            }
            if (!line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
            }
            while (width(word, bold) > width) {
                int cut = 1;
                while (cut < word.length() && width(word.substring(0, cut + 1), bold) <= width) cut++;
                // Rather after a | / . or - than in the middle of a name.
                for (int k = cut; k > cut / 2; k--) {
                    if ("|/.-".indexOf(word.charAt(k - 1)) >= 0) {
                        cut = k;
                        break;
                    }
                }
                out.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            line.append(word);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private static List<Line> lines(String text, Style style) {
        return lines(text, style, -1);
    }

    private static List<Line> lines(String text, Style style, int color) {
        List<Line> out = new ArrayList<>();
        for (String s : wrap(text, WIDTH, style.bold)) out.add(new Line(List.of(new Run(s, style, color, 0))));
        return out;
    }

    /** Spaces in front of a line so it sits in the middle of the page. */
    private static Line centered(String text, Style style, int color) {
        int pad = Math.max(0, (WIDTH - width(text, style.bold)) / 2 / advance(' '));
        return new Line(List.of(new Run(" ".repeat(pad), Style.TEXT), new Run(text, style, color, 0)));
    }

    private static List<Line> centeredLines(String text, Style style, int color) {
        List<Line> out = new ArrayList<>();
        for (String s : wrap(text, WIDTH, style.bold)) out.add(centered(s, style, color));
        return out;
    }

    // ---- Layout ------------------------------------------------------------------------------------

    /** Every page of the book, at most {@link #MAX_PAGES}. */
    public static List<Page> pages(Book book) {
        List<List<Page>> bodies = new ArrayList<>();
        for (Chapter c : book.chapters()) bodies.add(chapter(c));
        // The contents' length does not depend on the page numbers it shows (they are at most 3 digits).
        int contentsPages = contents(book, bodies, 0).size();
        List<Page> out = new ArrayList<>();
        out.add(title(book));
        out.addAll(contents(book, bodies, 2 + contentsPages));
        for (List<Page> body : bodies) out.addAll(body);
        return out.size() > MAX_PAGES ? List.copyOf(out.subList(0, MAX_PAGES)) : List.copyOf(out);
    }

    /** Where each chapter starts (1-based), in book order. */
    public static List<Integer> starts(Book book) {
        List<List<Page>> bodies = new ArrayList<>();
        for (Chapter c : book.chapters()) bodies.add(chapter(c));
        int at = 2 + contents(book, bodies, 0).size();
        List<Integer> out = new ArrayList<>();
        for (List<Page> body : bodies) {
            out.add(at);
            at += body.size();
        }
        return out;
    }

    private static Page title(Book book) {
        List<Line> l = new ArrayList<>();
        l.add(Line.BLANK);
        l.addAll(centeredLines(book.title(), Style.TITLE, -1));
        l.add(Line.BLANK);
        List<GodLine> gods = book.gods();
        if (gods.isEmpty()) {
            l.addAll(centeredLines("No god watches this world yet.", Style.DIM, -1));
        } else {
            l.addAll(centeredLines(gods.size() == 1 ? "The god of this world:" : "The gods of this world:",
                    Style.DIM, -1));
            for (GodLine g : gods.subList(0, Math.min(3, gods.size()))) {
                l.addAll(centeredLines(g.name() + (g.slain() ? " (slain)" : ""), Style.HEADING, ink(g.color())));
                if (g.epithet() != null && !g.epithet().isBlank() && gods.size() < 3) {
                    l.addAll(centeredLines(g.epithet(), Style.DIM, -1));
                }
            }
            if (gods.size() > 3) l.addAll(centeredLines("and " + (gods.size() - 3) + " more", Style.DIM, -1));
        }
        l.add(Line.BLANK);
        l.addAll(centeredLines("Turn the page for the contents.", Style.DIM, -1));
        if (book.operator()) {
            while (l.size() < LINES - 1) l.add(Line.BLANK);
            l.addAll(centeredLines("Operator's edition", Style.DIM, -1));
        }
        return new Page(fit(l));
    }

    private static List<Page> contents(Book book, List<List<Page>> bodies, int firstChapterPage) {
        List<List<Line>> units = new ArrayList<>();
        units.add(List.of(new Line(List.of(new Run("Contents", Style.TITLE))), Line.BLANK));
        int page = firstChapterPage;
        boolean opShown = false;
        for (int i = 0; i < book.chapters().size(); i++) {
            Chapter c = book.chapters().get(i);
            if (c.op() && !opShown) {
                opShown = true;
                units.add(List.of(Line.BLANK, new Line(List.of(new Run("For operators", Style.HEADING)))));
            }
            String number = String.valueOf(Math.max(page, 1));
            // A fixed indent, so the wrapping (and the contents' length) never depends on the numbers.
            String lead = number + (number.length() == 1 ? "  " : " ");
            List<Line> entry = new ArrayList<>();
            List<String> words = wrap(c.title(), WIDTH - CONTENTS_INDENT, false);
            for (int k = 0; k < words.size(); k++) {
                String front = k == 0 ? lead : " ".repeat(CONTENTS_INDENT / advance(' '));
                entry.add(new Line(List.of(new Run(front, Style.DIM),
                        new Run(words.get(k), Style.LINK, -1, Math.max(page, 1)))));
            }
            units.add(entry);
            page += bodies.get(i).size();
        }
        return flow(units);
    }

    private static List<Page> chapter(Chapter c) {
        List<List<Line>> units = new ArrayList<>();
        List<Line> head = new ArrayList<>(lines(c.title(), Style.TITLE));
        head.add(Line.BLANK);
        List<Block> blocks = c.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            List<Line> unit = new ArrayList<>(i == 0 ? head : List.of());
            Block b = blocks.get(i);
            unit.addAll(block(b));
            // A heading stays with what follows it.
            while (b instanceof Heading && i + 1 < blocks.size()) {
                b = blocks.get(++i);
                unit.addAll(block(b));
            }
            units.add(unit);
        }
        if (blocks.isEmpty()) units.add(head);
        return flow(units);
    }

    private static List<Line> block(Block b) {
        List<Line> out = new ArrayList<>();
        switch (b) {
            case Para p -> {
                out.addAll(lines(p.text(), Style.TEXT));
                out.add(Line.BLANK);
            }
            case Heading h -> out.addAll(lines(h.text(), Style.HEADING));
            case Cmd c -> {
                out.addAll(lines(c.cmd(), Style.CMD));
                out.addAll(lines(c.text(), Style.DIM));
                out.add(Line.BLANK);
            }
        }
        return out;
    }

    /** Units of at most this many lines (a command and its line, a heading and a short text) are never split. */
    private static final int KEEP = 5;

    /**
     * Fills pages with units of lines: a short unit that does not fit what is left of a page starts a new
     * one; a longer one (a paragraph) is split, unless that would leave a single line behind. Blank lines
     * never start or end a page.
     */
    private static List<Page> flow(List<List<Line>> units) {
        List<Page> pages = new ArrayList<>();
        List<Line> page = new ArrayList<>();
        for (List<Line> unit : units) {
            int used = trimmed(unit).size(), left = LINES - page.size();
            // Plain paragraphs may run on to the next page; commands, headings and titles stay whole.
            boolean whole = used <= KEEP && unit.stream().anyMatch(l -> l.runs().stream()
                    .anyMatch(r -> r.style() != Style.TEXT && !r.text().isBlank()));
            if (!page.isEmpty() && used > left && (whole || left < 2)) {
                pages.add(new Page(fit(page)));
                page = new ArrayList<>();
            }
            for (Line line : unit) {
                if (page.isEmpty() && line.blank()) continue;
                if (page.size() == LINES) {
                    pages.add(new Page(fit(page)));
                    page = new ArrayList<>();
                    if (line.blank()) continue;
                }
                page.add(line);
            }
        }
        if (!trimmed(page).isEmpty()) pages.add(new Page(fit(page)));
        return pages;
    }

    private static List<Line> trimmed(List<Line> lines) {
        int end = lines.size();
        while (end > 0 && lines.get(end - 1).blank()) end--;
        int start = 0;
        while (start < end && lines.get(start).blank()) start++;
        return lines.subList(start, end);
    }

    /** Drops trailing blank lines (leading ones only exist on the title page, where they push it down). */
    private static List<Line> fit(List<Line> lines) {
        int end = lines.size();
        while (end > 0 && lines.get(end - 1).blank()) end--;
        return List.copyOf(lines.subList(0, Math.min(LINES, end)));
    }

    /** A god's colour darkened enough to read on the book's cream paper. */
    static int ink(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        double k = lum > 0.45 ? 0.45 / lum : 1.0;
        return (int) (r * k) << 16 | (int) (g * k) << 8 | (int) (b * k);
    }

    // ---- To the game -------------------------------------------------------------------------------

    /** The pages as book components (black is the book's own colour, so plain text gets none). */
    public static List<Component> components(List<Page> pages) {
        List<Component> out = new ArrayList<>();
        for (Page p : pages) {
            TextComponent.Builder page = Component.text();
            for (int i = 0; i < p.lines().size(); i++) {
                if (i > 0) page.append(Component.newline());
                for (Run r : p.lines().get(i).runs()) page.append(run(r));
            }
            out.add(page.build());
        }
        return out;
    }

    private static Component run(Run r) {
        TextComponent.Builder c = Component.text().content(r.text());
        int color = r.color() >= 0 ? r.color() : r.style().color;
        if (color >= 0) c.color(TextColor.color(color));
        if (r.style().bold) c.decoration(TextDecoration.BOLD, true);
        if (r.link() > 0) {
            c.clickEvent(ClickEvent.changePage(r.link()));
            c.hoverEvent(HoverEvent.showText(Component.text("Turn to page " + r.link())));
        }
        return c.build();
    }
}
