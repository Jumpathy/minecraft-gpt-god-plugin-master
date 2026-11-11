package net.bigyous.gptgod.client.hud;

/** The subtitle's timing, wrapping and "where is the speaker" maths. Pure, so it is unit tested. */
public final class SubtitleMath {
    private SubtitleMath() {
    }

    /** How long a word takes to fade in. */
    public static final int WORD_FADE_MS = 160;
    public static final int FADE_IN_MS = 120;
    public static final int FADE_OUT_MS = 500;
    /** The reveal ends a little before the voice does, so the last word is read while it is said. */
    public static final float REVEAL_SHARE = 0.9f;

    /**
     * When each word starts to appear, in ms from the start of a line that takes ms to say. Words take
     * time in proportion to their length, and a pause follows punctuation, like speech.
     */
    public static int[] revealTimes(String[] words, int ms) {
        int[] at = new int[words.length];
        if (words.length == 0) return at;
        int[] weight = new int[words.length];
        long total = 0;
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            weight[i] = w.length() + 1;
            if (!w.isEmpty() && ".,;:!?…".indexOf(w.charAt(w.length() - 1)) >= 0) weight[i] += 3;
            total += weight[i];
        }
        long before = 0;
        double span = Math.max(0, ms) * (double) REVEAL_SHARE;
        for (int i = 0; i < words.length; i++) {
            at[i] = (int) Math.round(span * before / total);
            before += weight[i];
        }
        return at;
    }

    /** How many words have started to appear. */
    public static int revealed(int[] times, long elapsed) {
        int n = 0;
        while (n < times.length && times[n] <= elapsed) n++;
        return n;
    }

    /** A word's opacity, 0..1. */
    public static float wordAlpha(int revealAt, long elapsed) {
        if (elapsed < revealAt) return 0;
        return Math.min(1f, (elapsed - revealAt) / (float) WORD_FADE_MS);
    }

    /** How long a finished line stays readable before it fades: longer for longer lines, 1.2 to 4 s. */
    public static int lingerMs(int chars) {
        return Math.max(1200, Math.min(4000, 900 + chars * 22));
    }

    /** The whole subtitle's opacity: a quick fade in, held while spoken and lingering, then a fade out. */
    public static float lineAlpha(long elapsed, long speakMs, long lingerMs) {
        if (elapsed < 0) return 0;
        long end = speakMs + lingerMs;
        if (elapsed >= end + FADE_OUT_MS) return 0;
        if (elapsed > end) return 1f - (elapsed - end) / (float) FADE_OUT_MS;
        return Math.min(1f, elapsed / (float) FADE_IN_MS);
    }

    public static boolean over(long elapsed, long speakMs, long lingerMs) {
        return elapsed >= speakMs + lingerMs + FADE_OUT_MS;
    }

    /** Greedy word wrap: the row each word lands on. A word wider than the row gets a row of its own. */
    public static int[] wrap(int[] widths, int space, int maxWidth) {
        int[] row = new int[widths.length];
        int r = 0;
        int used = 0;
        for (int i = 0; i < widths.length; i++) {
            int need = used == 0 ? widths[i] : used + space + widths[i];
            if (used > 0 && need > maxWidth) {
                r++;
                need = widths[i];
            }
            used = need;
            row[i] = r;
        }
        return row;
    }

    /** The first row on screen when at most maxRows show and the voice has reached currentRow. */
    public static int firstRow(int currentRow, int rows, int maxRows) {
        if (rows <= maxRows) return 0;
        // Whole pages, like captions: the rows do not creep, they are replaced.
        int page = currentRow / maxRows * maxRows;
        return Math.max(0, Math.min(page, rows - 1));
    }

    public enum Where {
        /** On screen: no marker. */
        AHEAD, LEFT, RIGHT, ABOVE, BELOW
    }

    /** yaw: degrees the speaker is to the right of where you look (-180..180). pitch: degrees below. */
    public record Bearing(float yaw, float pitch, Where where) {
    }

    /**
     * Where a speaker is, seen from the camera. Angles are Minecraft's (yaw 0 = south, growing to the
     * right; pitch positive = down). vfov is the vertical field of view in degrees.
     */
    public static Bearing bearing(double[] eye, float yawDeg, float pitchDeg, double[] target, float vfovDeg,
            float aspect) {
        double dx = target[0] - eye[0];
        double dy = target[1] - eye[1];
        double dz = target[2] - eye[2];
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1e-4 && Math.abs(dy) < 1e-4) return new Bearing(0, 0, Where.AHEAD);
        float yaw = flat < 1e-4 ? 0 : wrap((float) Math.toDegrees(Math.atan2(-dx, dz)) - yawDeg);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat)) - pitchDeg;
        float v = Math.max(10, Math.min(170, vfovDeg));
        float halfV = v / 2;
        float halfH = (float) Math.toDegrees(Math.atan(Math.tan(Math.toRadians(halfV)) * Math.max(0.2f, aspect)));
        Where where;
        if (Math.abs(yaw) > halfH) where = yaw > 0 ? Where.RIGHT : Where.LEFT;
        else if (pitch < -halfV) where = Where.ABOVE;
        else if (pitch > halfV) where = Where.BELOW;
        else where = Where.AHEAD;
        return new Bearing(yaw, pitch, where);
    }

    /** How far the subtitle leans toward the speaker's side: -1 (left) .. 1 (right), 0 straight ahead. */
    public static float nudge(float yaw) {
        float a = Math.abs(yaw);
        // Fully to the side at 90 degrees; someone right behind you pulls it back toward the middle.
        float amount = a <= 90 ? a / 90f : 1f - 0.5f * (a - 90) / 90f;
        return Math.signum(yaw) * amount;
    }

    public static float wrap(float degrees) {
        float d = degrees % 360;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;
    }
}
