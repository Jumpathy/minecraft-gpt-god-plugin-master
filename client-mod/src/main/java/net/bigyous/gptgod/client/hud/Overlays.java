package net.bigyous.gptgod.client.hud;

import net.bigyous.gptgod.client.GodClientMod;
import net.bigyous.gptgod.client.fx.ScreenFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draws the screen effects over the world: colour flash, coloured vignette and the expanding pulse ring. */
public final class Overlays {
    private Overlays() {
    }

    static final Identifier VIGNETTE = GodClientMod.id("textures/gui/vignette.png");
    static final Identifier PULSE = GodClientMod.id("textures/gui/pulse.png");

    private static long frameAt = -1;
    private static ScreenFx.Frame frame = ScreenFx.Frame.NONE;

    /** This frame's summed screen effects (computed once per millisecond, shared with the camera shake). */
    public static ScreenFx.Frame current() {
        long now = System.currentTimeMillis();
        if (now != frameAt) {
            frameAt = now;
            frame = GodClientMod.SCREEN.busy() ? GodClientMod.SCREEN.frame(now, GodClientMod.config) : ScreenFx.Frame.NONE;
        }
        return frame;
    }

    private static long cameraAt = -1;
    private static net.bigyous.gptgod.client.fx.CameraFx.Frame camera = net.bigyous.gptgod.client.fx.CameraFx.Frame.NONE;

    /** This frame's camera takeover (zoom, roll, bars, gaze), shared by the camera hook and the bars. */
    public static net.bigyous.gptgod.client.fx.CameraFx.Frame camera() {
        long now = System.currentTimeMillis();
        if (now != cameraAt) {
            cameraAt = now;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!GodClientMod.CAMERA.busy() || mc.player == null) {
                camera = net.bigyous.gptgod.client.fx.CameraFx.Frame.NONE;
            } else {
                net.minecraft.client.Camera c = mc.gameRenderer.mainCamera();
                camera = GodClientMod.CAMERA.frame(now, GodClientMod.config,
                        new double[] { c.position().x, c.position().y, c.position().z }, c.yRot(), c.xRot());
            }
        }
        return camera;
    }

    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        float bars = camera().bars();
        if (bars > 0.002f) {
            int bar = Math.round(h * bars);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        ScreenFx.Frame f = current();
        if (f.idle()) return;
        if (f.vignetteAlpha() > 0.003f) {
            g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, w, h, 256, 256, 256, 256,
                    argb(f.vignetteAlpha(), f.vignetteColor()));
        }
        if (f.pulseAlpha() > 0.003f) {
            int size = (int) (Math.max(w, h) * (0.25f + 1.35f * f.pulseRadius()));
            g.blit(RenderPipelines.GUI_TEXTURED, PULSE, (w - size) / 2, (h - size) / 2, 0, 0, size, size, 256, 256,
                    256, 256, argb(f.pulseAlpha(), f.pulseColor()));
            g.fill(0, 0, w, h, argb(f.pulseAlpha() * 0.18f, f.pulseColor()));
        }
        if (f.flashAlpha() > 0.003f) {
            g.fill(0, 0, w, h, argb(f.flashAlpha(), f.flashColor()));
        }
    }

    static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | (rgb & 0xFFFFFF);
    }
}
