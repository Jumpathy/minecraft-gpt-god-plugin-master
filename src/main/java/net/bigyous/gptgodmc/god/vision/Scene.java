package net.bigyous.gptgodmc.god.vision;

import java.util.List;

/**
 * Everything the renderer needs, captured from the world on the server thread and then read
 * freely from a render thread.
 */
public final class Scene {
    /** Block access for the renderer. Coordinates are world block coordinates. */
    public interface Voxels {
        int UNLOADED = -1;

        /** Palette index (Material ordinal) at the block, or UNLOADED. */
        int block(int x, int y, int z);

        /** Max(sky light scaled by daylight, block light), 0-15. */
        int light(int x, int y, int z, double daylight);

        /** Highest non-air block y, or Integer.MIN_VALUE when unknown. */
        int highestY(int x, int z);

        int minY();

        int maxY();
    }

    /** A creature or player drawn as a box. */
    public record Box(double x, double y, double z, double halfWidth, double height, int rgb, String label,
            boolean glow, boolean player) {
    }

    /** A named place drawn on overhead maps. */
    public record Marker(double x, double z, String label) {
    }

    public enum Dimension {
        OVERWORLD, NETHER, END
    }

    public final Voxels voxels;
    public final Palette palette;
    public final List<Box> boxes;
    public final List<Marker> markers;
    public final Dimension dimension;
    /** 1 at noon, ~0.15 at midnight. */
    public final double daylight;
    /** 0 midday, 1 at sunrise/sunset: tints the horizon orange. */
    public final double sunsetGlow;
    public final boolean storm;

    public Scene(Voxels voxels, Palette palette, List<Box> boxes, List<Marker> markers, Dimension dimension,
            double daylight, double sunsetGlow, boolean storm) {
        this.voxels = voxels;
        this.palette = palette;
        this.boxes = boxes;
        this.markers = markers;
        this.dimension = dimension;
        this.daylight = daylight;
        this.sunsetGlow = sunsetGlow;
        this.storm = storm;
    }

    /** Daylight factor for a Minecraft time of day (0-24000). */
    public static double daylightFor(long time) {
        double t = time % 24000;
        if (t < 12000) return 1.0;
        if (t < 13800) return 1.0 - 0.85 * (t - 12000) / 1800.0;
        if (t < 22200) return 0.15;
        return 0.15 + 0.85 * (t - 22200) / 1800.0;
    }

    public static double sunsetFor(long time) {
        double t = time % 24000;
        double dusk = Math.max(0, 1 - Math.abs(t - 12800) / 1400.0);
        double dawn = Math.max(0, 1 - Math.abs(((t + 1000) % 24000) - 200) / 1400.0);
        return Math.max(dusk, dawn);
    }
}
