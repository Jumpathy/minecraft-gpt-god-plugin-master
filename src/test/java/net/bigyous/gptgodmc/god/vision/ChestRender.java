package net.bigyous.gptgodmc.god.vision;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.gui.MenuIcons;
import net.bigyous.gptgodmc.gui.MenuModel;
import net.bigyous.gptgodmc.gui.MenuModel.Icon;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;

/**
 * Draws a God Manager page as the game shows it: the 6-row chest background, each item from its real model
 * and textures, the Minecraft font, the enchantment glint and a hovered tooltip. Everything is read at test
 * time from the local Minecraft client jar (nothing from it is copied into the repository); painted icons
 * come from {@link MenuIcons}, as a player with the plugin's pack sees them.
 */
final class ChestRender implements AutoCloseable {
    static final File JAR = new File(System.getenv().getOrDefault("APPDATA", ""), ".minecraft/versions/26.3/26.3.jar");
    /** GUI scale: 4 screen pixels per GUI pixel (a 32px painted icon then maps 2:1). */
    static final int S = 4;
    static final int GUI_W = 176, GUI_H = 222, TIP_W = 210;

    private final ZipFile zip;
    private final Map<String, BufferedImage> textures = new HashMap<>();
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<String, BufferedImage> items = new HashMap<>();

    private record Glyph(BufferedImage sheet, int x, int y, int cell, int cellH, double scale, int ascent, int width) {
        int advance() {
            return (int) Math.round(width * scale) + 1;
        }
    }

    ChestRender() throws IOException {
        zip = new ZipFile(JAR);
        loadFont();
    }

    static boolean available() {
        return JAR.isFile();
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    // ---- Assets -------------------------------------------------------------------------------------

    private JsonObject json(String path) {
        ZipEntry e = zip.getEntry("assets/minecraft/" + path);
        if (e == null) return null;
        try (InputStream in = zip.getInputStream(e)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            return null;
        }
    }

    /** A texture ("block/stone"), cut to its first frame when animated. Null when missing. */
    BufferedImage texture(String path) {
        String p = path.replace("minecraft:", "");
        return textures.computeIfAbsent(p, k -> {
            ZipEntry e = zip.getEntry("assets/minecraft/textures/" + k + ".png");
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) {
                BufferedImage img = ImageIO.read(in);
                BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                argb.getGraphics().drawImage(img, 0, 0, null);
                if (k.startsWith("block/") || k.startsWith("item/")) {
                    if (argb.getHeight() > argb.getWidth()) argb = argb.getSubimage(0, 0, argb.getWidth(), argb.getWidth());
                }
                return argb;
            } catch (IOException ex) {
                return null;
            }
        });
    }

    private void loadFont() {
        JsonObject def = json("font/include/default.json");
        if (def == null) return;
        for (JsonElement el : def.getAsJsonArray("providers")) {
            JsonObject p = el.getAsJsonObject();
            if (!p.has("type") || !"bitmap".equals(p.get("type").getAsString()) || !p.has("file")) continue;
            BufferedImage sheet = texture(p.get("file").getAsString().replace("minecraft:", "").replace(".png", ""));
            if (sheet == null) continue;
            JsonArray rows = p.getAsJsonArray("chars");
            int cols = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
            int cell = sheet.getWidth() / cols;
            int cellH = sheet.getHeight() / rows.size();
            int height = p.has("height") ? p.get("height").getAsInt() : 8;
            int ascent = p.get("ascent").getAsInt();
            double scale = height / (double) cellH;
            for (int r = 0; r < rows.size(); r++) {
                int[] cps = rows.get(r).getAsString().codePoints().toArray();
                for (int c = 0; c < cps.length; c++) {
                    if (cps[c] == 0 || glyphs.containsKey(cps[c])) continue;
                    int w = 0;
                    for (int x = 0; x < cell; x++) {
                        for (int y = 0; y < cellH; y++) {
                            if ((sheet.getRGB(c * cell + x, r * cellH + y) >>> 24) != 0) w = x + 1;
                        }
                    }
                    glyphs.put(cps[c], new Glyph(sheet, c * cell, r * cellH, cell, cellH, scale, ascent, w));
                }
            }
        }
    }

    // ---- Text ---------------------------------------------------------------------------------------

    int textWidth(String s) {
        int w = 0;
        for (int cp : s.codePoints().toArray()) w += advance(cp);
        return w;
    }

    private int advance(int cp) {
        if (cp == ' ') return 4;
        Glyph g = glyphs.get(cp);
        return g == null ? 6 : g.advance();
    }

    /** Draws text at GUI coordinates (x, y = top of the line). */
    void text(BufferedImage img, String s, double x, double y, int rgb, boolean shadow) {
        if (shadow) text(img, s, x + 1, y + 1, (rgb & 0xFCFCFC) >> 2, false);
        double pen = x;
        for (int cp : s.codePoints().toArray()) {
            Glyph g = glyphs.get(cp);
            if (cp != ' ' && g != null) {
                double top = y + 7 - g.ascent();
                for (int gy = 0; gy < g.cellH(); gy++) {
                    for (int gx = 0; gx < g.cell(); gx++) {
                        int a = g.sheet().getRGB(g.x() + gx, g.y() + gy) >>> 24;
                        if (a == 0) continue;
                        int px0 = (int) Math.round((pen + gx * g.scale()) * S);
                        int py0 = (int) Math.round((top + gy * g.scale()) * S);
                        int px1 = (int) Math.round((pen + (gx + 1) * g.scale()) * S);
                        int py1 = (int) Math.round((top + (gy + 1) * g.scale()) * S);
                        fill(img, px0, py0, px1 - px0, py1 - py0, rgb);
                    }
                }
            }
            pen += advance(cp);
        }
    }

    // ---- Items --------------------------------------------------------------------------------------

    /** An icon's item at 16 GUI pixels (16*S screen pixels), as this viewer sees it. */
    BufferedImage item(Icon icon, boolean pack) {
        String key = (pack && icon.art() != null ? "art:" + icon.art() : icon.material().name()) + (icon.glow() ? "+" : "");
        return items.computeIfAbsent(key, k -> {
            BufferedImage base;
            if (pack && icon.art() != null) {
                base = scaled(MenuIcons.image(icon.art()), 16 * S);
            } else {
                String id = icon.material().name().toLowerCase(Locale.ROOT);
                try {
                    base = vanilla(id);
                } catch (RuntimeException ex) {
                    System.err.println("[ChestRender] " + id + ": " + ex);
                    base = new BufferedImage(16 * S, 16 * S, BufferedImage.TYPE_INT_ARGB);
                }
            }
            return icon.glow() ? glint(base) : base;
        });
    }

    private BufferedImage vanilla(String id) {
        switch (id) {
            case "player_head" -> {
                BufferedImage skin = texture("entity/player/wide/steve");
                return head(skin);
            }
            case "wither_skeleton_skull" -> {
                return head(texture("entity/skeleton/wither_skeleton"));
            }
            default -> {
            }
        }
        JsonObject def = json("items/" + id + ".json");
        String model = def == null ? "item/" + id : firstModel(def.get("model"));
        if (model == null) model = "item/" + id;
        Map<String, String> tex = new HashMap<>();
        List<String> chain = new ArrayList<>();
        String m = model.replace("minecraft:", "");
        for (int depth = 0; m != null && depth < 12; depth++) {
            chain.add(m);
            JsonObject j = json("models/" + m + ".json");
            if (j == null) break;
            if (j.has("textures")) {
                for (Map.Entry<String, JsonElement> e : j.getAsJsonObject("textures").entrySet()) {
                    JsonElement v = e.getValue();
                    String t = v.isJsonPrimitive() ? v.getAsString()
                            : v.isJsonObject() && v.getAsJsonObject().has("sprite")
                                    ? v.getAsJsonObject().get("sprite").getAsString() : null;
                    if (t != null) tex.putIfAbsent(e.getKey(), t);
                }
            }
            m = j.has("parent") && j.get("parent").isJsonPrimitive()
                    ? j.get("parent").getAsString().replace("minecraft:", "") : null;
        }
        boolean flat = tex.containsKey("layer0") || chain.stream().anyMatch(c -> c.equals("item/generated")
                || c.equals("item/handheld"));
        if (flat) {
            BufferedImage out = new BufferedImage(16 * S, 16 * S, BufferedImage.TYPE_INT_ARGB);
            for (String layer : List.of("layer0", "layer1", "layer2")) {
                BufferedImage t = resolve(tex, layer);
                if (t != null) out.getGraphics().drawImage(scaled(t, 16 * S), 0, 0, null);
            }
            return out;
        }
        BufferedImage all = first(tex, "all", "side", "particle", "texture", "end");
        BufferedImage top = first(tex, "top", "end", "all", "up", "particle", "texture", "side");
        BufferedImage side = first(tex, "side", "all", "north", "particle", "texture", "front");
        BufferedImage front = first(tex, "front", "north", "side", "all", "particle", "texture");
        if (top == null) top = all;
        if (side == null) side = all;
        if (front == null) front = side;
        if (top == null) return new BufferedImage(16 * S, 16 * S, BufferedImage.TYPE_INT_ARGB);
        return cube(top, front, side);
    }

    private static String firstModel(JsonElement el) {
        if (el == null) return null;
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("type") && o.get("type").getAsString().endsWith("model") && o.has("model")
                    && o.get("model").isJsonPrimitive()) {
                return o.get("model").getAsString();
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                String m = firstModel(e.getValue());
                if (m != null) return m;
            }
        } else if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                String m = firstModel(e);
                if (m != null) return m;
            }
        }
        return null;
    }

    private BufferedImage resolve(Map<String, String> tex, String key) {
        String v = tex.get(key);
        for (int i = 0; v != null && v.startsWith("#") && i < 8; i++) v = tex.get(v.substring(1));
        return v == null ? null : texture(v);
    }

    private BufferedImage first(Map<String, String> tex, String... keys) {
        for (String k : keys) {
            BufferedImage t = resolve(tex, k);
            if (t != null) return t;
        }
        return null;
    }

    /** A head from a 64x32/64x64 skin: face, top and side, plus the hat layer over each. */
    private BufferedImage head(BufferedImage skin) {
        if (skin == null) return new BufferedImage(16 * S, 16 * S, BufferedImage.TYPE_INT_ARGB);
        BufferedImage top = layered(skin, 8, 0, 40, 0), front = layered(skin, 8, 8, 40, 8),
                side = layered(skin, 0, 8, 32, 8);
        return cube(top, front, side, 0.5);
    }

    private static BufferedImage layered(BufferedImage skin, int x, int y, int hx, int hy) {
        BufferedImage out = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(skin.getSubimage(x, y, 8, 8), 0, 0, null);
        if (skin.getWidth() >= hx + 8) out.getGraphics().drawImage(skin.getSubimage(hx, hy, 8, 8), 0, 0, null);
        return out;
    }

    private BufferedImage cube(BufferedImage top, BufferedImage left, BufferedImage right) {
        return cube(top, left, right, 0.625);
    }

    /**
     * A block as the inventory draws it: the cube turned 30° about X and 225° about Y, scaled, top lit
     * fully, the left face at 80% and the right at 60%.
     */
    private static BufferedImage cube(BufferedImage top, BufferedImage left, BufferedImage right, double scale) {
        int n = 16 * S;
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double unit = scale * 16 * S;
        // Corner (x, y, z) in -0.5..0.5 → screen.
        java.util.function.Function<double[], double[]> p = v -> {
            double a = Math.toRadians(225), b = Math.toRadians(30);
            double x1 = v[0] * Math.cos(a) + v[2] * Math.sin(a);
            double z1 = -v[0] * Math.sin(a) + v[2] * Math.cos(a);
            double y2 = v[1] * Math.cos(b) - z1 * Math.sin(b);
            return new double[] { n / 2.0 + x1 * unit, n / 2.0 - y2 * unit };
        };
        // Visible faces: the top (y = +0.5) and the two sides facing the viewer; find them by screen position.
        double[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        List<double[][]> visible = new ArrayList<>();
        for (double[] sd : sides) {
            // Normal after rotation: its z decides visibility.
            double a = Math.toRadians(225), b = Math.toRadians(30);
            double z1 = -sd[0] * Math.sin(a) + sd[1] * Math.cos(a);
            double zv = z1 * Math.cos(b);
            if (zv <= 0) continue;
            // The face's top-left, top-right and bottom-left corners, seen from outside.
            double[] tl, tr, bl;
            if (sd[0] != 0) {
                double x = sd[0] * 0.5;
                tl = new double[] { x, 0.5, sd[0] * 0.5 };
                tr = new double[] { x, 0.5, -sd[0] * 0.5 };
                bl = new double[] { x, -0.5, sd[0] * 0.5 };
            } else {
                double z = sd[1] * 0.5;
                tl = new double[] { -sd[1] * 0.5, 0.5, z };
                tr = new double[] { sd[1] * 0.5, 0.5, z };
                bl = new double[] { -sd[1] * 0.5, -0.5, z };
            }
            visible.add(new double[][] { p.apply(tl), p.apply(tr), p.apply(bl) });
        }
        visible.sort((u, v) -> Double.compare(u[0][0] + u[1][0], v[0][0] + v[1][0]));
        for (int i = 0; i < visible.size(); i++) {
            double[][] f = visible.get(i);
            face(g, i == 0 ? left : right, f[0], f[1], f[2], i == 0 ? 0.8 : 0.6);
        }
        double[] t0 = p.apply(new double[] { -0.5, 0.5, -0.5 }), t1 = p.apply(new double[] { 0.5, 0.5, -0.5 }),
                t2 = p.apply(new double[] { -0.5, 0.5, 0.5 });
        face(g, top, t0, t1, t2, 1.0);
        g.dispose();
        return out;
    }

    private static void face(Graphics2D g, BufferedImage tex, double[] o, double[] u, double[] v, double light) {
        int w = tex.getWidth(), h = tex.getHeight();
        BufferedImage shaded = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = tex.getRGB(x, y);
                int r = (int) (((c >> 16) & 255) * light), gr = (int) (((c >> 8) & 255) * light),
                        b = (int) ((c & 255) * light);
                shaded.setRGB(x, y, (c & 0xFF000000) | (r << 16) | (gr << 8) | b);
            }
        }
        AffineTransform t = new AffineTransform((u[0] - o[0]) / w, (u[1] - o[1]) / w, (v[0] - o[0]) / h,
                (v[1] - o[1]) / h, o[0], o[1]);
        // A hair larger, so neighbouring faces meet without seams.
        t.concatenate(AffineTransform.getScaleInstance(1.01, 1.01));
        g.drawImage(shaded, t, null);
    }

    private BufferedImage glint(BufferedImage base) {
        BufferedImage glint = texture("misc/enchanted_glint_item");
        int n = base.getWidth();
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int c = base.getRGB(x, y);
                if ((c >>> 24) == 0) continue;
                int gl = 0x8040CC;
                if (glint != null) {
                    int gx = Math.floorMod((x + y / 2) * glint.getWidth() / (n * 2), glint.getWidth());
                    int gy = Math.floorMod((y - x / 3) * glint.getHeight() / (n * 2), glint.getHeight());
                    gl = glint.getRGB(gx, gy);
                }
                double k = 0.55;
                int r = Math.min(255, ((c >> 16) & 255) + (int) (((gl >> 16) & 255) * k));
                int g2 = Math.min(255, ((c >> 8) & 255) + (int) (((gl >> 8) & 255) * k));
                int b = Math.min(255, (c & 255) + (int) ((gl & 255) * k));
                out.setRGB(x, y, (c & 0xFF000000) | (r << 16) | (g2 << 8) | b);
            }
        }
        return out;
    }

    static BufferedImage scaled(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) out.setRGB(x, y, src.getRGB(x * src.getWidth() / size, y * src.getHeight() / size));
        }
        return out;
    }

    // ---- The chest ----------------------------------------------------------------------------------

    /** The whole screen: the chest with the player's (empty) inventory, and the tooltip of `hover`. */
    BufferedImage page(Menu menu, boolean pack, int hover) {
        int w = (GUI_W + TIP_W) * S, h = GUI_H * S;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, w, h, 0x15141A);
        BufferedImage bg = texture("gui/container/generic_54");
        if (bg != null) {
            int rowsH = MenuModel.ROWS * 18 + 17;
            blit(img, bg.getSubimage(0, 0, GUI_W, rowsH), 0, 0);
            blit(img, bg.getSubimage(0, 126, GUI_W, 96), 0, rowsH);
        }
        text(img, menu.title(), 8, 6, 0x404040, false);
        text(img, "Inventory", 8, GUI_H - 94, 0x404040, false);
        for (int slot = 0; slot < MenuModel.SIZE; slot++) {
            Icon icon = menu.at(slot);
            if (icon == null) continue;
            int x = 8 + (slot % 9) * 18, y = 18 + (slot / 9) * 18;
            blit(img, item(icon, pack), x * S, y * S, 1);
        }
        Icon hovered = menu.at(hover);
        if (hovered != null) {
            int x = 8 + (hover % 9) * 18, y = 18 + (hover / 9) * 18;
            overlay(img, x * S, y * S, 16 * S, 16 * S, 0x80FFFFFF);
            // The tooltip beside the chest (in game it follows the mouse), so no slot is hidden.
            tooltip(img, hovered, GUI_W + 12, Math.max(6, y - 4));
        }
        return img;
    }

    private void tooltip(BufferedImage img, Icon icon, int x, int y) {
        List<String> lines = new ArrayList<>();
        lines.add(icon.name());
        lines.addAll(icon.lore());
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, textWidth(l));
        int th = 8 + (lines.size() - 1) * 10 + (lines.size() > 1 ? 2 : 0);
        int maxX = GUI_W + TIP_W - 4;
        if (x + tw + 4 > maxX) x = Math.max(4, maxX - tw - 4);
        if (y + th + 4 > GUI_H) y = GUI_H - th - 4;
        if (y < 4) y = 4;
        // The vanilla tooltip: near-black purple, a violet frame fading down.
        fill(img, (x - 3) * S, (y - 4) * S, (tw + 6) * S, S, 0x100010);
        fill(img, (x - 3) * S, (y + th + 3) * S, (tw + 6) * S, S, 0x100010);
        fill(img, (x - 4) * S, (y - 3) * S, (tw + 8) * S, (th + 6) * S, 0x100010);
        for (int i = 0; i < th + 4; i++) {
            int c = mix(0x5000FF, 0x28007F, i / (double) (th + 3));
            fill(img, (x - 3) * S, (y - 3 + 1 + i) * S, S, S, c);
            fill(img, (x + tw + 2) * S, (y - 3 + 1 + i) * S, S, S, c);
        }
        fill(img, (x - 3) * S, (y - 3) * S, (tw + 6) * S, S, 0x5000FF);
        fill(img, (x - 3) * S, (y + th + 2) * S, (tw + 6) * S, S, 0x28007F);
        int ly = y;
        for (int i = 0; i < lines.size(); i++) {
            text(img, lines.get(i), x, ly, i == 0 ? icon.rgb() : 0xAAAAAA, true);
            ly += i == 0 ? 12 : 10;
        }
    }

    private static void blit(BufferedImage dst, BufferedImage src, int x0, int y0) {
        for (int y = 0; y < src.getHeight() * S; y++) {
            for (int x = 0; x < src.getWidth() * S; x++) {
                int c = src.getRGB(x / S, y / S);
                if ((c >>> 24) > 0) dst.setRGB(x0 * S + x, y0 * S + y, c | 0xFF000000);
            }
        }
    }

    private static void blit(BufferedImage dst, BufferedImage src, int x0, int y0, int unused) {
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int dx = x0 + x, dy = y0 + y;
                if (dx < 0 || dy < 0 || dx >= dst.getWidth() || dy >= dst.getHeight()) continue;
                int c = src.getRGB(x, y), a = c >>> 24;
                if (a == 0) continue;
                dst.setRGB(dx, dy, 0xFF000000 | (a == 255 ? c : mix(c & 0xFFFFFF, dst.getRGB(dx, dy), 1 - a / 255.0)));
            }
        }
    }

    private static void overlay(BufferedImage img, int x0, int y0, int w, int h, int argb) {
        double a = (argb >>> 24) / 255.0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) img.setRGB(x, y, 0xFF000000 | mix(img.getRGB(x, y), argb, a));
        }
    }

    static void fill(BufferedImage img, int x, int y, int w, int h, int rgb) {
        for (int i = Math.max(0, x); i < x + w && i < img.getWidth(); i++) {
            for (int j = Math.max(0, y); j < y + h && j < img.getHeight(); j++) img.setRGB(i, j, 0xFF000000 | rgb);
        }
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
