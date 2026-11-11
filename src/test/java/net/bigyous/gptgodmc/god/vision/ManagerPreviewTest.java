package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.gui.MenuIcons;
import net.bigyous.gptgodmc.gui.MenuLayout;
import net.bigyous.gptgodmc.gui.MenuModel;
import net.bigyous.gptgodmc.gui.MenuModel.Icon;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Page;
import net.bigyous.gptgodmc.gui.MenuModel.State;
import net.bigyous.gptgodmc.gui.MenuSamples;

/**
 * A flat mock of each God Manager page (9 columns × 6 rows, each slot coloured like its icon and
 * labelled with its name) at build/test-renders/manager-<page>.png, to check the layout offline.
 */
class ManagerPreviewTest {
    static final File OUT = new File("build/test-renders");
    static final int CELL_W = 132;
    static final int CELL_H = 84;
    static final int TOP = 26;
    static final int GAP = 4;

    @Test
    void writeEveryPage() throws Exception {
        OUT.mkdirs();
        State s = MenuSamples.sample();
        for (Page page : Page.values()) {
            State shown = page == Page.PERSONALITY
                    ? s.withConfirm(MenuModel.Action.of(MenuModel.Kind.PERSONA, "evil")
                            .confirming("A new evil god replaces the current one."))
                    : s;
            File f = new File(OUT, "manager-" + page.id() + ".png");
            ImageIO.write(render(MenuLayout.build(page, shown)), "png", f);
            assertTrue(f.length() > 0);
        }
    }

    /** Every painted icon at 4x with its key: build/test-renders/menu-icons.png. */
    @Test
    void writeIconSheet() throws Exception {
        OUT.mkdirs();
        int cols = 7, cell = 160, pad = 12;
        List<String> keys = MenuIcons.KEYS;
        int rows = (keys.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cell, rows * cell + 30, BufferedImage.TYPE_INT_RGB);
        fill(img, 0, 0, img.getWidth(), img.getHeight(), 0x8B8B8B);
        PixelFont.draw(img, "GOD MANAGER ICONS", 8, 8, 0xFFFFFF, 2);
        for (int i = 0; i < keys.size(); i++) {
            int x = (i % cols) * cell, y = 30 + (i / cols) * cell;
            fill(img, x + 4, y + 4, cell - 8, cell - 8, 0xC6C6C6);
            blit(img, MenuIcons.image(keys.get(i)), x + (cell - 128) / 2, y + pad, 4);
            PixelFont.draw(img, keys.get(i).replace('_', ' '), x + 10, y + cell - 22, 0x202020, 1);
        }
        File f = new File(OUT, "menu-icons.png");
        ImageIO.write(img, "png", f);
        assertTrue(f.length() > 0);
    }

    /** Draws an ARGB image scaled by s (nearest), blending its alpha over the background. */
    static void blit(BufferedImage dst, BufferedImage src, int x0, int y0, int s) {
        for (int y = 0; y < src.getHeight() * s; y++) {
            for (int x = 0; x < src.getWidth() * s; x++) {
                int dx = x0 + x, dy = y0 + y;
                if (dx < 0 || dy < 0 || dx >= dst.getWidth() || dy >= dst.getHeight()) continue;
                int c = src.getRGB(x / s, y / s);
                int a = c >>> 24;
                if (a == 0) continue;
                dst.setRGB(dx, dy, a == 255 ? c & 0xFFFFFF : mix(c & 0xFFFFFF, dst.getRGB(dx, dy) & 0xFFFFFF, 1 - a / 255.0));
            }
        }
    }

    static BufferedImage render(Menu menu) {
        int w = 9 * CELL_W + 10 * GAP;
        int h = TOP + MenuModel.ROWS * CELL_H + (MenuModel.ROWS + 1) * GAP;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        fill(img, 0, 0, w, h, 0xC6C6C6);
        PixelFont.draw(img, menu.title().replace("·", "-"), GAP + 2, 8, 0xFFFFFF, 2);
        for (int slot = 0; slot < MenuModel.SIZE; slot++) {
            int x = GAP + (slot % 9) * (CELL_W + GAP);
            int y = TOP + GAP + (slot / 9) * (CELL_H + GAP);
            Icon icon = menu.at(slot);
            if (icon == null) {
                fill(img, x, y, CELL_W, CELL_H, 0x8B8B8B);
                continue;
            }
            boolean pane = icon.material().name().endsWith("STAINED_GLASS_PANE");
            int rgb = color(icon.material());
            fill(img, x, y, CELL_W, CELL_H, pane ? mix(rgb, 0x8B8B8B, 0.55) : rgb);
            if (icon.glow()) {
                outline(img, x, y, CELL_W, CELL_H, 0xFF4DFF);
                outline(img, x + 1, y + 1, CELL_W - 2, CELL_H - 2, 0xFF4DFF);
            }
            if (pane) continue;
            if (icon.art() != null) {
                // As a player with the pack sees it: the painted icon, 2x, at the cell's right.
                blit(img, MenuIcons.image(icon.art()), x + CELL_W - 66, y + CELL_H - 66, 2);
            }
            int line = 0;
            for (String l : lines(icon.name(), 20, 4)) {
                PixelFont.draw(img, l, x + 4, y + 5 + line * 10, icon.rgb(), 1);
                line++;
            }
            String kind = icon.material().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            for (String l : lines(kind, 20, 1)) {
                PixelFont.draw(img, l, x + 4, y + CELL_H - 12, 0xDDDDDD, 1);
            }
        }
        return img;
    }

    static List<String> lines(String text, int width, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.replace("·", "-").replace("\"", "'").split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word.length() > width ? word.substring(0, width) : word);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out.size() > max ? out.subList(0, max) : out;
    }

    static final Map<String, Integer> DYES = Map.ofEntries(Map.entry("WHITE", 0xE9ECEC), Map.entry("ORANGE", 0xF07613),
            Map.entry("MAGENTA", 0xBD44B3), Map.entry("LIGHT_BLUE", 0x3AAFD9), Map.entry("YELLOW", 0xF8C527),
            Map.entry("LIME", 0x70B919), Map.entry("PINK", 0xED8DAC), Map.entry("GRAY", 0x3E4447),
            Map.entry("LIGHT_GRAY", 0x8E8E86), Map.entry("CYAN", 0x158991), Map.entry("PURPLE", 0x792AAC),
            Map.entry("BLUE", 0x35399D), Map.entry("BROWN", 0x724728), Map.entry("GREEN", 0x546D1B),
            Map.entry("RED", 0xA12722), Map.entry("BLACK", 0x141519));

    static int color(Material m) {
        String n = m.name();
        for (String prefix : List.of("LIGHT_BLUE", "LIGHT_GRAY")) {
            if (n.startsWith(prefix + "_")) return DYES.get(prefix);
        }
        int cut = n.indexOf('_');
        if (cut > 0 && DYES.containsKey(n.substring(0, cut)) && !n.equals("GRAY_DYE")) {
            return DYES.get(n.substring(0, cut));
        }
        return switch (n) {
            case "NETHER_STAR" -> 0xD9D2F2;
            case "ARROW" -> 0x6B5A45;
            case "BARRIER" -> 0x7A1010;
            case "WRITABLE_BOOK", "BOOK" -> 0x7A4E2A;
            case "COMPARATOR" -> 0x5E5E5E;
            case "BELL" -> 0xC9A227;
            case "PLAYER_HEAD" -> 0x9C7A57;
            case "FEATHER" -> 0xBFBFBF;
            case "LEAD" -> 0x6B4F33;
            case "GLASS" -> 0x9FC6CC;
            case "CRYING_OBSIDIAN" -> 0x3A1466;
            case "OBSIDIAN" -> 0x1E1630;
            case "OAK_SIGN" -> 0x9C7F4E;
            case "GOLDEN_APPLE" -> 0xD4A520;
            case "IRON_SWORD" -> 0x7F8A93;
            case "ARMOR_STAND" -> 0x6F5434;
            case "MAP" -> 0xB8A47A;
            case "ENDER_EYE" -> 0x2E7D6B;
            case "PAPER" -> 0xBDBDB0;
            case "CLOCK" -> 0xB08A1E;
            case "GRAY_DYE" -> 0x555555;
            default -> 0x777777;
        };
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static void fill(BufferedImage img, int x, int y, int w, int h, int rgb) {
        for (int i = x; i < x + w && i < img.getWidth(); i++) {
            for (int j = y; j < y + h && j < img.getHeight(); j++) {
                img.setRGB(i, j, rgb);
            }
        }
    }

    static void outline(BufferedImage img, int x, int y, int w, int h, int rgb) {
        for (int i = x; i < x + w; i++) {
            img.setRGB(i, y, rgb);
            img.setRGB(i, y + h - 1, rgb);
        }
        for (int j = y; j < y + h; j++) {
            img.setRGB(x, j, rgb);
            img.setRGB(x + w - 1, j, rgb);
        }
    }
}
