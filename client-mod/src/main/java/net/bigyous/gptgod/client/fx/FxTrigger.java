package net.bigyous.gptgod.client.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A "trigger" layer of a scene: a condition the god wrote as a formula, watched for each mortal by their own
 * game (how near they are, whether they look at it, how fast they move). When it has held long enough it
 * fires: the layers under "then" play at once for that mortal, and the god is told ("tell"). Pure.
 * {@link #parse} never throws; {@link #check} is the strict reading, with messages meant for the god.
 *
 * This file is word for word the same in the plugin and in the client mod, apart from the package line.
 */
public final class FxTrigger {
    public static final int MAX_TICKS = 20 * 60;
    public static final int DEFAULT_TICKS = 200;
    public static final int MAX_HOLD = 200;
    /** The shortest wait before a trigger may fire again for the same mortal. */
    public static final int MIN_AGAIN = 20;
    public static final int MAX_THEN = 8;
    public static final int MAX_TELL = 80;
    public static final String KEY = "[a-z0-9]{1,24}";

    public final int delay, duration, hold, again;
    public final double[] offset;
    public final FxExpr when;
    /** What the god is told the mortal did ("looked into the eye"), or "". */
    public final String tell;
    /** Whether hearing of it wakes the god. */
    public final boolean wake;
    /** The layers that play for the mortal when it fires; may be empty. */
    public final JsonArray then;
    /** The server's name for this trigger in this showing, sent back when it fires; "" when none was given. */
    public final String key;

    private FxTrigger(int delay, int duration, int hold, int again, double[] offset, FxExpr when, String tell, boolean wake,
            JsonArray then, String key) {
        this.delay = delay;
        this.duration = duration;
        this.hold = hold;
        this.again = again;
        this.offset = offset;
        this.when = when;
        this.tell = tell;
        this.wake = wake;
        this.then = then;
        this.key = key;
    }

    /** Reads a layer leniently; null when its condition cannot be read. */
    public static FxTrigger parse(JsonObject o) {
        FxExpr when;
        try {
            when = FxField.formula(o.get("when"), null);
        } catch (FxExpr.Invalid e) {
            return null;
        }
        if (when == null) return null;
        JsonArray then = new JsonArray();
        JsonElement t = o.get("then");
        if (t != null && t.isJsonObject()) then.add(t);
        if (t != null && t.isJsonArray()) {
            for (JsonElement e : t.getAsJsonArray()) {
                if (then.size() >= MAX_THEN) break;
                // A trigger sets off no further triggers.
                if (e.isJsonObject() && !"trigger".equals(text(e.getAsJsonObject().get("type"), 16))) then.add(e);
            }
        }
        int again = (int) FxField.num(o.get("again"), 0, 0, MAX_TICKS);
        String key = text(o.get("key"), 24);
        JsonElement wake = o.get("wake");
        return new FxTrigger((int) FxField.num(o.get("delay"), 0, 0, MAX_TICKS),
                (int) FxField.num(o.get("duration"), DEFAULT_TICKS, 1, MAX_TICKS), (int) FxField.num(o.get("hold"), 1, 1, MAX_HOLD),
                again == 0 ? 0 : Math.max(MIN_AGAIN, again), FxField.vec(o.get("offset"), 64), when, tell(o.get("tell")),
                wake != null && wake.isJsonPrimitive() && wake.getAsJsonPrimitive().isBoolean() && wake.getAsBoolean(), then,
                key.matches(KEY) ? key : "");
    }

    /** The strict reading. The layers under "then" are checked by the caller, as a scene. */
    public static void check(JsonObject o) throws FxExpr.Invalid {
        JsonElement w = o.get("when");
        if (w == null || w.isJsonNull()) {
            throw new FxExpr.Invalid("a trigger needs \"when\": a formula that is not 0 when it should fire, e.g. "
                    + "\"d < 4\" (a mortal comes within 4 blocks) or \"(look > 0.97) * (d < 24)\" (they look straight at it "
                    + "from within 24 blocks)");
        }
        try {
            FxField.formula(w, null);
        } catch (FxExpr.Invalid bad) {
            throw new FxExpr.Invalid("when: " + bad.getMessage());
        }
        JsonElement t = o.get("then"), tell = o.get("tell");
        boolean does = t != null && (t.isJsonObject() || (t.isJsonArray() && !t.getAsJsonArray().isEmpty()));
        if (!does && tell(tell).isEmpty()) {
            throw new FxExpr.Invalid("a trigger needs \"then\" (layers that play for that mortal when it fires) or \"tell\" "
                    + "(what you are told they did, e.g. \"looked into the eye\"), or both");
        }
        if (t != null && t.isJsonArray() && t.getAsJsonArray().size() > MAX_THEN) {
            throw new FxExpr.Invalid("\"then\" holds at most " + MAX_THEN + " layers");
        }
        if (tell != null && tell.isJsonPrimitive() && tell.getAsString().trim().length() > MAX_TELL) {
            throw new FxExpr.Invalid("\"tell\" is at most " + MAX_TELL + " characters");
        }
    }

    private static String text(JsonElement e, int max) {
        if (e == null || !e.isJsonPrimitive()) return "";
        String s = e.getAsString().trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** The god's words, on one line and without anything a chat line could be dressed up with. */
    static String tell(JsonElement e) {
        StringBuilder out = new StringBuilder();
        for (char c : text(e, MAX_TELL).toCharArray()) {
            if (c >= ' ' && c != 0x7F && c != 0xA7) out.append(c);
        }
        return out.toString().trim();
    }

    /** One mortal's watch on a trigger. */
    public static final class Watch {
        private int held;
        /** Ticks since it last fired; -1 = it never has. */
        private int since = -1;

        /**
         * One step: vars already holds what is known of the mortal ({@link FxExpr#viewer}); t is the layer's tick and
         * ticks how many passed since the last step. Returns whether it fires now.
         */
        public boolean tick(FxTrigger trigger, double t, int ticks, double[] vars) {
            if (since >= 0) since = Math.min(1 << 20, since + ticks);
            vars[FxExpr.T] = t;
            vars[FxExpr.P] = Math.max(0, Math.min(1, t / trigger.duration));
            double v = trigger.when.eval(vars);
            held = !Double.isNaN(v) && v != 0 ? held + ticks : 0;
            if (held < trigger.hold) return false;
            if (since >= 0 && (trigger.again == 0 || since < trigger.again)) return false;
            since = 0;
            held = 0;
            return true;
        }
    }
}
