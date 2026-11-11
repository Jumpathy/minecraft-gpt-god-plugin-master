package net.bigyous.gptgodmc.god.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * Values crossing between a script and the host. Out of a script come only plain values (null, Boolean,
 * Double, String, List, Map; a function stays a function for on() and every()); into a script go only
 * copies of plain values. No Java object is ever handed to a script.
 */
public final class ScriptValues {
    private ScriptValues() {
    }

    static final int MAX_DEPTH = 6;
    static final int MAX_ITEMS = 5000;

    /** A script's value as a plain Java one. */
    public static Object toJava(Object v) {
        return toJava(v, 0);
    }

    private static Object toJava(Object v, int depth) {
        if (v == null || v == Undefined.instance || v == Scriptable.NOT_FOUND) return null;
        if (v instanceof CharSequence s) return s.toString();
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean) return v;
        if (v instanceof Function) return v;
        if (depth >= MAX_DEPTH) throw new IllegalArgumentException("that value is nested too deep to pass");
        if (v instanceof NativeArray a) {
            long n = a.getLength();
            if (n > MAX_ITEMS) throw new IllegalArgumentException("that list is too long to pass (" + n + " items)");
            List<Object> out = new ArrayList<>((int) n);
            for (int i = 0; i < n; i++) out.add(toJava(a.get(i, a), depth + 1));
            return out;
        }
        if (v instanceof Scriptable s) {
            Map<String, Object> out = new LinkedHashMap<>();
            Object[] ids = s.getIds();
            if (ids.length > MAX_ITEMS) throw new IllegalArgumentException("that object is too large to pass");
            for (Object id : ids) {
                Object value = id instanceof Number n ? s.get(n.intValue(), s) : s.get(String.valueOf(id), s);
                out.put(id instanceof Number n ? String.valueOf(n.intValue()) : String.valueOf(id), toJava(value, depth + 1));
            }
            return out;
        }
        return String.valueOf(v);
    }

    /** A plain Java value as a script's own (objects and arrays are fresh copies). */
    public static Object toJs(Context cx, Scriptable scope, Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean || v instanceof String) return v;
        if (v instanceof Map<?, ?> m) {
            Scriptable o = cx.newObject(scope);
            for (Map.Entry<?, ?> e : m.entrySet()) {
                ScriptableObject.putProperty(o, String.valueOf(e.getKey()), toJs(cx, scope, e.getValue()));
            }
            return o;
        }
        if (v instanceof List<?> l) {
            Object[] items = new Object[l.size()];
            for (int i = 0; i < items.length; i++) items[i] = toJs(cx, scope, l.get(i));
            return cx.newArray(scope, items);
        }
        return String.valueOf(v);
    }

    /** How a value reads in the log: text as it is, numbers without a needless ".0", the rest as JSON. */
    public static String show(Object v) {
        if (v instanceof String s) return s;
        StringBuilder sb = new StringBuilder();
        json(sb, v);
        return sb.toString();
    }

    private static void json(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Double d) {
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append(d.longValue());
            else sb.append(String.format(Locale.ROOT, "%.4f", d).replaceAll("0+$", "").replaceAll("\\.$", ""));
        } else if (v instanceof String s) {
            sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(e.getKey()).append(": ");
                json(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(", ");
                json(sb, l.get(i));
            }
            sb.append(']');
        } else if (v instanceof Function) {
            sb.append("[function]");
        } else {
            sb.append(v);
        }
    }

    // ---- Reading a call's arguments (for hosts) ----------------------------------------------------------

    public static boolean has(List<Object> args, int i) {
        return i < args.size() && args.get(i) != null;
    }

    public static double num(List<Object> args, int i, String what) throws ScriptRefusal {
        Object v = i < args.size() ? args.get(i) : null;
        if (v instanceof Double d && !d.isNaN() && !d.isInfinite()) return d;
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                // falls through
            }
        }
        throw new ScriptRefusal(what + " must be a number (got " + show(v) + ")");
    }

    public static double num(List<Object> args, int i, String what, double fallback) throws ScriptRefusal {
        return has(args, i) ? num(args, i, what) : fallback;
    }

    /** A whole number: block coordinates round down, as the game does. */
    public static int whole(List<Object> args, int i, String what) throws ScriptRefusal {
        double d = num(args, i, what);
        if (Math.abs(d) > 30_000_000) throw new ScriptRefusal(what + " is outside the world (" + show(d) + ")");
        return (int) Math.floor(d);
    }

    public static String text(List<Object> args, int i, String what) throws ScriptRefusal {
        Object v = i < args.size() ? args.get(i) : null;
        if (v instanceof String s && !s.isBlank()) return s.trim();
        if (v instanceof Double) return show(v);
        throw new ScriptRefusal(what + " must be text (got " + show(v) + ")");
    }

    public static String text(List<Object> args, int i, String what, String fallback) throws ScriptRefusal {
        return has(args, i) ? text(args, i, what) : fallback;
    }

    public static boolean flag(List<Object> args, int i) {
        Object v = i < args.size() ? args.get(i) : null;
        return v instanceof Boolean b ? b : v instanceof Double d ? d != 0 : v instanceof String s && s.equalsIgnoreCase("true");
    }
}
