package net.bigyous.gptgod.client.fx;

/**
 * The god's mood in the sky: the colours sky, fog and clouds are pulled toward, and how hard, near the
 * god. Pure. {@link #tick} eases the current look toward the target so changes take a few seconds;
 * {@link #sky}, {@link #fog} and {@link #cloud} blend a vanilla colour (r, g, b in 0..1) at a position.
 */
public final class SkyMood {
    /** One mood's look: sky, fog and cloud colours (RGB) and how strongly they win at full strength. */
    public record Look(int sky, int fog, int cloud, float mix, float cloudDark) {
    }

    public static final Look NONE = new Look(0, 0, 0, 0, 0);

    public static Look look(String mood, String state) {
        if ("slain".equals(state)) return new Look(0x4A4B52, 0x404146, 0x606066, 0.8f, 0.4f);
        if (mood == null) return NONE;
        return switch (mood) {
            case "wrathful" -> new Look(0x9A0F06, 0x5A0C06, 0x4A1510, 0.88f, 0.55f);
            case "joyful" -> new Look(0xFFC24A, 0xFFDD96, 0xFFF1CF, 0.6f, 0f);
            case "sorrowful" -> new Look(0x6F87A8, 0x8296B0, 0xA5B2C3, 0.7f, 0.25f);
            case "ominous" -> new Look(0x1E0A36, 0x150A24, 0x2A1E34, 0.88f, 0.6f);
            case "exalted" -> new Look(0xFFF1C0, 0xFFEFC0, 0xFFFFFF, 0.7f, 0f);
            default -> NONE;
        };
    }

    /** Full strength within this many blocks of the god's body, fading to nothing at FAR. */
    public static final double NEAR = 24;
    public static final double FAR = 128;
    /** Strength in the god's world while it has no body (formless, slain): a gentler, world-wide tint. */
    public static final float FORMLESS = 0.45f;

    private Look target = NONE;
    private double[] godPos;
    private boolean here;
    private float setting = 1;

    // The eased state.
    private float sr, sg, sb, fr, fg, fb, cr, cg, cb, mix, dark;

    public void target(String mood, String state, double[] godPos, boolean here, float setting) {
        this.target = look(mood, state);
        this.godPos = godPos;
        this.here = here;
        this.setting = Math.max(0, Math.min(1, setting));
    }

    public void clearTarget() {
        target = NONE;
        godPos = null;
        here = false;
    }

    /** Eases toward the target; call once a client tick (20 a second). Reaches ~95% in 3 seconds. */
    public void tick() {
        float k = 0.05f;
        Look t = target;
        float goalMix = t.mix() * setting;
        if (goalMix > 0) {
            // Move the colour only while the mood shows, so it fades out in its own colour.
            sr += (r(t.sky()) - sr) * k;
            sg += (g(t.sky()) - sg) * k;
            sb += (b(t.sky()) - sb) * k;
            fr += (r(t.fog()) - fr) * k;
            fg += (g(t.fog()) - fg) * k;
            fb += (b(t.fog()) - fb) * k;
            cr += (r(t.cloud()) - cr) * k;
            cg += (g(t.cloud()) - cg) * k;
            cb += (b(t.cloud()) - cb) * k;
            dark += (t.cloudDark() - dark) * k;
        }
        mix += (goalMix - mix) * k;
        if (mix < 0.002f && goalMix == 0) mix = 0;
    }

    /** Snap to the target at once (tests and previews). */
    public void settle() {
        for (int i = 0; i < 200; i++) tick();
    }

    public boolean active() {
        return mix > 0;
    }

    /** How strongly the mood shows at this position, 0..1 (times the look's mix). */
    public float strength(double x, double y, double z) {
        if (mix <= 0) return 0;
        if (godPos == null) return here ? mix * FORMLESS : 0;
        double dx = godPos[0] - x, dy = (godPos[1] - y) * 0.5, dz = godPos[2] - z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double s;
        if (d <= NEAR) s = 1;
        else if (d >= FAR) s = 0;
        else {
            double u = 1 - (d - NEAR) / (FAR - NEAR);
            s = u * u * (3 - 2 * u); // smoothstep
        }
        return (float) (mix * Math.max(s, here ? FORMLESS * 0.5 : 0));
    }

    public float[] sky(float r, float g, float b, double x, double y, double z) {
        float s = strength(x, y, z);
        return new float[] { lerp(r, sr, s), lerp(g, sg, s), lerp(b, sb, s) };
    }

    public float[] fog(float r, float g, float b, double x, double y, double z) {
        float s = strength(x, y, z);
        return new float[] { lerp(r, fr, s), lerp(g, fg, s), lerp(b, fb, s) };
    }

    /** Clouds are tinted and, for dark moods, darkened. */
    public float[] cloud(float r, float g, float b, double x, double y, double z) {
        float s = strength(x, y, z);
        float shade = 1 - dark * s;
        return new float[] { lerp(r, cr, s) * shade, lerp(g, cg, s) * shade, lerp(b, cb, s) * shade };
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float r(int c) {
        return ((c >> 16) & 255) / 255f;
    }

    private static float g(int c) {
        return ((c >> 8) & 255) / 255f;
    }

    private static float b(int c) {
        return (c & 255) / 255f;
    }
}
