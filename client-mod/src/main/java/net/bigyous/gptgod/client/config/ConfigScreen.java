package net.bigyous.gptgod.client.config;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import net.bigyous.gptgod.client.GodClientMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The mod's settings: effect strengths, "no flashing", the god panel, and plain-vanilla mode. */
public final class ConfigScreen extends Screen {
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Component.literal("GPTGOD effects"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig c = GodClientMod.config;
        int w = 150;
        int left = width / 2 - w - 5;
        int right = width / 2 + 5;
        int y = height / 6;
        addRenderableWidget(new Slider(left, y, w, "Screen shake", () -> c.shake, v -> c.shake = v));
        addRenderableWidget(new Slider(right, y, w, "Flashes", () -> c.flash, v -> c.flash = v));
        y += 24;
        addRenderableWidget(new Slider(left, y, w, "Vignettes", () -> c.vignette, v -> c.vignette = v));
        addRenderableWidget(new Slider(right, y, w, "Sky moods", () -> c.sky, v -> c.sky = v));
        y += 24;
        addRenderableWidget(toggle(left, y, w, () -> "No flashing: " + onOff(c.noFlashing), () -> c.noFlashing = !c.noFlashing));
        addRenderableWidget(toggle(right, y, w, () -> "God panel: " + word(c.hud.name()), () -> c.hud = c.hud.next()));
        y += 24;
        addRenderableWidget(toggle(left, y, w, () -> "Panel corner: " + word(c.corner.name()), () -> c.corner = c.corner.next()));
        addRenderableWidget(toggle(right, y, w, () -> "Plain vanilla look: " + onOff(c.vanilla), () -> {
            c.vanilla = !c.vanilla;
            GodClientMod.featuresChanged();
        }));
        y += 24;
        addRenderableWidget(toggle(left, y, w, () -> "Voice subtitles: " + onOff(c.subtitles), () -> {
            c.subtitles = !c.subtitles;
            if (!c.subtitles) net.bigyous.gptgod.client.hud.Subtitles.clear();
            GodClientMod.featuresChanged();
        }));
        y += 36;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 100, y, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.centeredText(font, title, width / 2, height / 6 - 22, 0xFFE8D8FF);
        g.centeredText(font, Component.literal("Only affects how the god looks to you. The server decides everything."),
                width / 2, height / 6 - 10, 0xFF9C94B0);
    }

    @Override
    public void onClose() {
        GodClientMod.saveConfig();
        minecraft.gui.setScreen(parent);
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    private static String word(String enumName) {
        String s = enumName.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
    }

    private static Button toggle(int x, int y, int w, java.util.function.Supplier<String> label, Runnable flip) {
        return Button.builder(Component.literal(label.get()), b -> {
            flip.run();
            b.setMessage(Component.literal(label.get()));
        }).bounds(x, y, w, 20).build();
    }

    private static final class Slider extends AbstractSliderButton {
        private final String label;
        private final DoubleConsumer set;

        Slider(int x, int y, int w, String label, DoubleSupplier get, DoubleConsumer set) {
            super(x, y, w, 20, Component.empty(), get.getAsDouble());
            this.label = label;
            this.set = set;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            set.accept(value);
        }
    }
}
