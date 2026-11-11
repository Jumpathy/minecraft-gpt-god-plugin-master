package net.bigyous.gptgod.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * The God Manager screen's widgets, in one flat dark style: panels, buttons, chips, switches, sliders,
 * text boxes, scrolling lists, heads, bars and tooltips. Immediate mode: a page draws its widgets every
 * frame and each call answers "was I clicked?", so pages hold no widget objects. Clicks and keys arrive
 * from the screen between frames and are used up by the next frame.
 */
public final class Ui {
    public static final int BG = 0xF0121217, PANEL = 0xFF1A1A21, PANEL2 = 0xFF23232C, HOVER = 0xFF2E2E3A,
            LINE = 0xFF363642, TEXT = 0xFFE8E8EE, DIM = 0xFF9A9AA8, FAINT = 0xFF666672, ACCENT = 0xFFB070FF,
            OK = 0xFF6FE08A, BAD = 0xFFFF6464, WARN = 0xFFFFC04A;
    public static final int NORMAL = 0, PRIMARY = 1, DANGER = 2;

    public GuiGraphicsExtractor g;
    public Font font;
    public int mx, my;
    public long now;
    private float partial;

    // Input since the last frame.
    private MouseButtonEvent press;
    private boolean pressLeft, pressRight, released, down;
    private double wheel;
    public boolean enter, keyUp, keyDown;
    private boolean tab;

    private String active;
    private float activeValue;
    private String dropped;
    private float droppedValue;
    private final Map<String, EditBox> boxes = new HashMap<>();
    private final List<String> seen = new ArrayList<>();
    private String focus;
    private boolean boxClicked;
    private final Map<String, Double> scroll = new HashMap<>();
    private int clipX0, clipY0, clipX1, clipY1;
    private boolean clipped;
    private List<String> tip;
    private String armed;
    private long armedAt;

    // ---- Frame and input -------------------------------------------------------------------------

    public void begin(GuiGraphicsExtractor graphics, Font f, int mouseX, int mouseY, float a) {
        g = graphics;
        font = f;
        mx = mouseX;
        my = mouseY;
        partial = a;
        now = System.currentTimeMillis();
        seen.clear();
        tip = null;
        boxClicked = false;
        clipped = false;
        if (armed != null && now - armedAt > 4000) armed = null;
    }

    public void end() {
        if (tip != null) drawTip();
        if (tab && !seen.isEmpty()) {
            int i = seen.indexOf(focus);
            focus = seen.get((i + 1) % seen.size());
        }
        // A click on nothing takes the focus away; a box that is no longer shown cannot keep it.
        if (press != null && !boxClicked) focus = null;
        if (focus != null && !seen.contains(focus)) focus = null;
        for (Map.Entry<String, EditBox> e : boxes.entrySet()) e.getValue().setFocused(e.getKey().equals(focus));
        press = null;
        pressLeft = pressRight = released = enter = keyUp = keyDown = tab = false;
        dropped = null;
        wheel = 0;
    }

    public void press(MouseButtonEvent e) {
        press = e;
        if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            pressLeft = true;
            down = true;
        } else if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            pressRight = true;
        }
    }

    public void release() {
        released = true;
        down = false;
    }

    public void wheel(double dy) {
        wheel += dy;
    }

    /** A key. True when a text box used it (so the screen does nothing else with it). */
    public boolean key(KeyEvent e) {
        EditBox box = focus == null ? null : boxes.get(focus);
        if (box == null) return false;
        if (e.isEscape()) {
            focus = null;
            return true;
        }
        if (e.isConfirmation()) {
            enter = true;
            return true;
        }
        if (e.isUp()) keyUp = true;
        else if (e.isDown()) keyDown = true;
        else if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) tab = true;
        else box.keyPressed(e);
        return true;
    }

    public boolean typed(CharacterEvent e) {
        EditBox box = focus == null ? null : boxes.get(focus);
        return box != null && box.charTyped(e);
    }

    public boolean typing() {
        return focus != null;
    }

    public void focus(String id) {
        focus = id;
        boxClicked = true; // a click elsewhere this frame does not take it away again
    }

    /** Whether a box with this id was drawn this frame. */
    public boolean has(String id) {
        return seen.contains(id);
    }

    public boolean focused(String id) {
        return id.equals(focus);
    }

    // ---- Geometry --------------------------------------------------------------------------------

    private boolean inClip(int x, int y) {
        return !clipped || (x >= clipX0 && x < clipX1 && y >= clipY0 && y < clipY1);
    }

    public boolean hover(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h && inClip(mx, my);
    }

    /** A left click inside the rectangle; used up, so nothing drawn later sees it. */
    public boolean clicked(int x, int y, int w, int h) {
        if (!pressLeft || press == null) return false;
        int px = (int) press.x(), py = (int) press.y();
        if (px < x || px >= x + w || py < y || py >= y + h || !inClip(px, py)) return false;
        pressLeft = false;
        return true;
    }

    public boolean rightClicked(int x, int y, int w, int h) {
        if (!pressRight || press == null) return false;
        int px = (int) press.x(), py = (int) press.y();
        if (px < x || px >= x + w || py < y || py >= y + h || !inClip(px, py)) return false;
        pressRight = false;
        return true;
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---- Text ------------------------------------------------------------------------------------

    public void text(String s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    public void small(String s, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75f, 0.75f);
        g.text(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    public int width(String s) {
        return font.width(s);
    }

    public int smallWidth(String s) {
        return (int) Math.ceil(font.width(s) * 0.75f);
    }

    /** The text cut to fit w pixels, with an ellipsis. */
    public String fit(String s, int w) {
        if (s == null) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
    }

    public String fitSmall(String s, int w) {
        return fit(s, (int) (w / 0.75f));
    }

    /** Word-wrapped text; returns the height it took. maxLines 0 = no limit. */
    public int wrap(String s, int x, int y, int w, int color, int maxLines) {
        if (s == null || s.isEmpty()) return 0;
        List<FormattedCharSequence> lines = font.split(Component.literal(s), Math.max(20, w));
        int n = maxLines > 0 ? Math.min(maxLines, lines.size()) : lines.size();
        for (int i = 0; i < n; i++) g.text(font, lines.get(i), x, y + i * 10, color, false);
        return n * 10;
    }

    public int wrapSmall(String s, int x, int y, int w, int color, int maxLines) {
        if (s == null || s.isEmpty()) return 0;
        List<FormattedCharSequence> lines = font.split(Component.literal(s), Math.max(20, (int) (w / 0.75f)));
        int n = maxLines > 0 ? Math.min(maxLines, lines.size()) : lines.size();
        for (int i = 0; i < n; i++) {
            g.pose().pushMatrix();
            g.pose().translate(x, y + i * 8);
            g.pose().scale(0.75f, 0.75f);
            g.text(font, lines.get(i), 0, 0, color, false);
            g.pose().popMatrix();
        }
        return n * 8;
    }

    /** How tall wrapSmall would be. */
    public int smallHeight(String s, int w, int maxLines) {
        if (s == null || s.isEmpty()) return 0;
        int n = font.split(Component.literal(s), Math.max(20, (int) (w / 0.75f))).size();
        return (maxLines > 0 ? Math.min(maxLines, n) : n) * 8;
    }

    // ---- Panels and buttons ------------------------------------------------------------------------

    public void panel(int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    public void frame(int x, int y, int w, int h, int color) {
        g.outline(x, y, w, h, color);
    }

    /** A section heading with a thin rule; returns the y below it. */
    public int heading(String s, int x, int y, int w) {
        small(s.toUpperCase(java.util.Locale.ROOT), x, y + 1, DIM);
        int tw = smallWidth(s.toUpperCase(java.util.Locale.ROOT));
        g.fill(x + tw + 4, y + 4, x + w, y + 5, LINE);
        return y + 11;
    }

    public boolean button(String label, int x, int y, int w, int h, int style, boolean enabled) {
        boolean hot = enabled && hover(x, y, w, h);
        int base = style == PRIMARY ? 0xFF4A2F78 : style == DANGER ? 0xFF5A2226 : PANEL2;
        int lit = style == PRIMARY ? 0xFF6540A6 : style == DANGER ? 0xFF7E2C32 : HOVER;
        g.fill(x, y, x + w, y + h, hot ? lit : base);
        g.outline(x, y, w, h, hot ? (style == DANGER ? BAD : ACCENT) : LINE);
        String s = fit(label, w - 4);
        text(s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, enabled ? TEXT : FAINT);
        if (enabled && clicked(x, y, w, h)) {
            click();
            return true;
        }
        return false;
    }

    public boolean button(String label, int x, int y, int w, int h) {
        return button(label, x, y, w, h, NORMAL, true);
    }

    /** A button that asks once more before it answers true: the first click arms it for a few seconds. */
    public boolean confirm(String id, String label, int x, int y, int w, int h) {
        boolean isArmed = id.equals(armed);
        if (isArmed) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(now / 150.0);
            g.fill(x, y, x + w, y + h, 0xFF000000 | (int) (0x7E * pulse + 0x30) << 16 | 0x2026);
            g.outline(x, y, w, h, BAD);
            String s = fit("Confirm?", w - 4);
            text(s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
            if (clicked(x, y, w, h)) {
                click();
                armed = null;
                return true;
            }
            return false;
        }
        if (button(label, x, y, w, h, DANGER, true)) {
            armed = id;
            armedAt = now;
        }
        return false;
    }

    /** Arms a confirm button as if it had been clicked once. */
    public void arm(String id) {
        armed = id;
        armedAt = System.currentTimeMillis();
    }

    /** A small pill; selected ones are filled with the accent (or their own colour). Returns true when clicked. */
    public boolean chip(String label, int x, int y, boolean selected, int color) {
        int w = chipWidth(label);
        boolean hot = hover(x, y, w, 13);
        int c = color == -1 ? ACCENT : 0xFF000000 | color;
        g.fill(x, y, x + w, y + 13, selected ? mix(PANEL2, c, 0.45f) : hot ? HOVER : PANEL2);
        g.outline(x, y, w, 13, selected ? c : hot ? DIM : LINE);
        text(label, x + 5, y + 3, selected ? 0xFFFFFFFF : color == -1 ? TEXT : c);
        if (clicked(x, y, w, 13)) {
            click();
            return true;
        }
        return false;
    }

    public int chipWidth(String label) {
        return font.width(label) + 10;
    }

    /** An on/off switch, 22 x 11. Returns true when clicked. */
    public boolean toggle(int x, int y, boolean on, boolean enabled) {
        g.fill(x, y, x + 22, y + 11, on ? 0xFF2E6B3E : 0xFF3A3A44);
        g.outline(x, y, 22, 11, on ? OK : LINE);
        int kx = on ? x + 12 : x + 2;
        g.fill(kx, y + 2, kx + 8, y + 9, enabled ? 0xFFFFFFFF : FAINT);
        if (enabled && clicked(x, y, 22, 11)) {
            click();
            return true;
        }
        return false;
    }

    /**
     * A slider over whole numbers. Shows the value being dragged; returns the value let go of (once), or
     * null. marker (or Integer.MIN_VALUE) draws a notch, e.g. where an archetype set a dial.
     */
    public Integer slider(String id, int x, int y, int w, int min, int max, int value, int color, int marker) {
        int h = 11;
        if (pressLeft && press != null && press.x() >= x - 2 && press.x() < x + w + 2 && press.y() >= y && press.y() < y + h
                && inClip((int) press.x(), (int) press.y())) {
            pressLeft = false;
            active = id;
        }
        float shown = value;
        Integer result = null;
        if (id.equals(active)) {
            float t = Math.max(0, Math.min(1, (mx - x - 3) / (float) Math.max(1, w - 6)));
            activeValue = Math.round(min + t * (max - min));
            shown = activeValue;
            if (!down) {
                active = null;
                if (Math.round(activeValue) != value) result = Math.round(activeValue);
            }
        }
        float t = (shown - min) / Math.max(1, max - min);
        g.fill(x, y + 4, x + w, y + 7, 0xFF0C0C10);
        int fill = x + 3 + Math.round(t * (w - 6));
        g.fill(x, y + 4, fill, y + 7, 0xFF000000 | color);
        if (marker != Integer.MIN_VALUE) {
            int m = x + 3 + Math.round((marker - min) / (float) Math.max(1, max - min) * (w - 6));
            g.fill(m, y + 2, m + 1, y + 9, DIM);
        }
        boolean hot = id.equals(active) || hover(x, y, w, h);
        g.fill(fill - 2, y + 1, fill + 3, y + 10, hot ? 0xFFFFFFFF : 0xFFD0D0DA);
        return result;
    }

    /** The value a slider shows while it is being dragged, else `value`. */
    public int sliding(String id, int value) {
        return id.equals(active) ? Math.round(activeValue) : value;
    }

    // ---- Text boxes --------------------------------------------------------------------------------

    /** A text box that lives as long as a page keeps drawing it. */
    public EditBox box(String id, int x, int y, int w, int h, String hint, int maxLength) {
        EditBox b = boxes.get(id);
        if (b == null) {
            b = new EditBox(font, x + 4, y + (h - 8) / 2, w - 8, 9, Component.empty());
            b.setBordered(false);
            b.setMaxLength(maxLength);
            b.setTextColor(TEXT);
            b.setCanLoseFocus(true);
            boxes.put(id, b);
        }
        seen.add(id);
        boolean focused = id.equals(focus);
        g.fill(x, y, x + w, y + h, 0xFF0D0D11);
        g.outline(x, y, w, h, focused ? ACCENT : hover(x, y, w, h) ? DIM : LINE);
        b.setX(x + 4);
        b.setY(y + (h - 8) / 2);
        b.setWidth(w - 8);
        b.setHint(Component.literal(hint == null ? "" : hint).withColor(FAINT));
        b.setFocused(focused);
        if (press != null && pressLeft && press.x() >= x && press.x() < x + w && press.y() >= y && press.y() < y + h
                && inClip((int) press.x(), (int) press.y())) {
            pressLeft = false;
            boxClicked = true;
            focus = id;
            b.setFocused(true);
            b.onClick(press, false);
        }
        b.extractRenderState(g, mx, my, partial);
        return b;
    }

    public String value(String id) {
        EditBox b = boxes.get(id);
        return b == null ? "" : b.getValue();
    }

    public void set(String id, String value) {
        EditBox b = boxes.get(id);
        if (b != null) {
            b.setValue(value == null ? "" : value);
            b.moveCursorToEnd(false);
        }
    }

    /** True on the frame Enter was pressed in this box. */
    public boolean submitted(String id) {
        return enter && id.equals(focus);
    }

    // ---- Scrolling ---------------------------------------------------------------------------------

    /** Starts a scrolling area and returns how far it is scrolled; draw the content shifted up by that. */
    public int scrollBegin(String id, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
        clipX0 = x;
        clipY0 = y;
        clipX1 = x + w;
        clipY1 = y + h;
        clipped = true;
        return (int) Math.round(scroll.getOrDefault(id, 0.0));
    }

    /** Ends the area: takes the wheel, clamps to the content's height and draws the scroll bar. */
    public void scrollEnd(String id, int x, int y, int w, int h, int contentHeight) {
        g.disableScissor();
        clipped = false;
        double at = scroll.getOrDefault(id, 0.0);
        if (wheel != 0 && hover(x, y, w, h)) {
            at -= wheel * 18;
            wheel = 0;
        }
        int max = Math.max(0, contentHeight - h);
        at = Math.max(0, Math.min(max, at));
        scroll.put(id, at);
        if (max > 0) {
            int bar = Math.max(12, h * h / contentHeight);
            int by = y + (int) ((h - bar) * (at / max));
            g.fill(x + w - 2, y, x + w, y + h, 0x40000000);
            g.fill(x + w - 2, by, x + w, by + bar, 0xFF5A5A6A);
        }
    }

    public double scrolled(String id) {
        return scroll.getOrDefault(id, 0.0);
    }

    public void scrollTo(String id, double at) {
        scroll.put(id, Math.max(0, at));
    }

    // ---- Heads, bars, tooltips -------------------------------------------------------------------

    /** A square image of size x size pixels drawn `px` screen pixels per pixel. */
    public void image(int[] pixels, int size, int x, int y, int px) {
        if (pixels == null || pixels.length < size * size) return;
        for (int j = 0; j < size; j++) {
            int run = 0;
            for (int i = 1; i <= size; i++) {
                // Runs of one colour become one rectangle.
                if (i < size && pixels[j * size + i] == pixels[j * size + run]) continue;
                g.fill(x + run * px, y + j * px, x + i * px, y + (j + 1) * px, pixels[j * size + run]);
                run = i;
            }
        }
    }

    /** A player's face (their skin when they are online, the default skin otherwise). */
    public void playerHead(String name, int x, int y, int size) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(name);
        PlayerFaceExtractor.extractRenderState(g, info != null ? info.getSkin()
                : DefaultPlayerSkin.get(java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                x, y, size);
    }

    public void bar(int x, int y, int w, int h, float value, int color) {
        g.fill(x, y, x + w, y + h, 0xFF0C0C10);
        int fw = Math.round(w * Math.max(0, Math.min(1, value)));
        if (fw > 0) g.fill(x, y, x + fw, y + h, 0xFF000000 | color);
    }

    /** Shows these lines beside the mouse at the end of the frame. */
    public void tip(List<String> lines) {
        tip = lines;
    }

    public void tipIf(int x, int y, int w, int h, String text) {
        if (text != null && !text.isEmpty() && hover(x, y, w, h)) tip = List.of(text);
    }

    private void drawTip() {
        List<FormattedCharSequence> rows = new ArrayList<>();
        for (String line : tip) {
            int color = TEXT;
            String s = line;
            if (s.startsWith("§") && s.length() > 1) { // our own markers, never the server's: §d dim, §a accent, §b bad
                color = s.charAt(1) == 'd' ? DIM : s.charAt(1) == 'b' ? BAD : ACCENT;
                s = s.substring(2);
            }
            rows.addAll(font.split(Component.literal(s).withColor(color & 0xFFFFFF), 200));
        }
        g.setTooltipForNextFrame(font, rows, mx, my);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }
}
