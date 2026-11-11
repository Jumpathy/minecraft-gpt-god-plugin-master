package net.bigyous.gptgodmc.god.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.LambdaFunction;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptStackElement;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import net.bigyous.gptgodmc.god.script.ScriptApi.Fn;
import net.bigyous.gptgodmc.god.script.ScriptHost.Call;
import net.bigyous.gptgodmc.god.script.ScriptHost.Outcome;
import net.bigyous.gptgodmc.god.script.ScriptHost.Placement;

/**
 * One script, from its first line to the end of its last handler. Pure: it knows the interpreter (Mozilla
 * Rhino, interpreted, with only the safe standard objects, so a script can name no Java class) and a
 * {@link ScriptHost}, nothing of the server.
 * <p>
 * {@link #run()} executes the program; if it left handlers (on, every), {@link #serve()} then answers events
 * given to {@link #offer} until their time is up. Both must be called on the same thread, never the server's.
 * Everything a script spends, in both, comes out of one {@link ScriptBudget}.
 */
public final class ScriptRun {
    /**
     * @param ok        it reached its end without a mistake or a stop
     * @param log       what it logged
     * @param error     what went wrong, phrased for the god, or null
     * @param line      the line it went wrong on, or 0
     * @param listeners handlers still alive
     * @param spent     what it used of its budget
     */
    public record Result(boolean ok, String log, String error, int line, int listeners, String spent) {
    }

    private static final String SOURCE = "script";
    private static final Object KEY = new Object();
    private static final Object VOID = new Object();
    /** Deferred calls kept at most before the world is visited anyway. */
    private static final int MAX_QUEUE = 100;

    /** The sandbox: interpreted only, no Java access, every few thousand steps reported to the budget. */
    private static final ContextFactory SANDBOX = new ContextFactory() {
        @Override
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setInterpretedMode(true);
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setInstructionObserverThreshold(2000);
            cx.setMaximumInterpreterStackDepth(300);
            cx.setClassShutter(name -> false);
            return cx;
        }

        @Override
        protected boolean hasFeature(Context cx, int feature) {
            if (feature == Context.FEATURE_ENHANCED_JAVA_ACCESS) return false;
            return super.hasFeature(cx, feature);
        }

        @Override
        protected void observeInstructionCount(Context cx, int count) {
            Object run = cx.getThreadLocal(KEY);
            if (run instanceof ScriptRun r) r.observe(count);
        }
    };

    /** Standard objects whose work is one unbounded native allocation: not given to scripts. */
    private static final List<String> REMOVED = List.of("ArrayBuffer", "SharedArrayBuffer", "DataView", "Int8Array",
            "Uint8Array", "Uint8ClampedArray", "Int16Array", "Uint16Array", "Int32Array", "Uint32Array", "Float32Array",
            "Float64Array", "BigInt64Array", "BigUint64Array", "Atomics", "WeakRef", "FinalizationRegistry");

    /**
     * The same reason: these standard functions loop or allocate natively, where the step counter cannot see.
     * Rewritten in the script's own language, they are counted like any other loop.
     */
    private static final String SAFER = "(function(){"
            + "var def=function(o,n,f){Object.defineProperty(o,n,{value:f,writable:true,configurable:true,enumerable:false});};"
            + "var rep=function(s,n){n=Math.floor(n);if(!(n>=0)||n*s.length>100000)throw new RangeError('that text would be too long');"
            + "var o='';for(var i=0;i<n;i++)o+=s;return o;};"
            + "var pad=function(s,n,p){p=p===undefined?' ':String(p);n=Math.floor(n);if(!p.length||!(n>s.length))return '';"
            + "return rep(p,Math.ceil((n-s.length)/p.length)).slice(0,n-s.length);};"
            + "def(String.prototype,'repeat',function(n){return rep(String(this),n);});"
            + "def(String.prototype,'padStart',function(n,p){var s=String(this);return pad(s,n,p)+s;});"
            + "def(String.prototype,'padEnd',function(n,p){var s=String(this);return s+pad(s,n,p);});"
            + "def(Array.prototype,'fill',function(v,s,e){var n=this.length>>>0;"
            + "s=s===undefined?0:s<0?Math.max(n+s,0):Math.min(s,n);e=e===undefined?n:e<0?Math.max(n+e,0):Math.min(e,n);"
            + "for(var i=s;i<e;i++)this[i]=v;return this;});"
            + "def(Array,'from',function(src,fn){var o=[],i=0;if(src==null)throw new TypeError('Array.from needs a value');"
            + "if(typeof src[Symbol.iterator]==='function'){for(var v of src){o.push(fn?fn(v,i):v);i++;}}"
            + "else{var n=src.length>>>0;for(;i<n;i++)o.push(fn?fn(src[i],i):src[i]);}return o;});"
            + "})();";

    private record Pos(int x, int y, int z) {
    }

    private static final class Listener {
        final int id;
        final String event;
        final Map<String, Object> filter;
        final Function fn;
        /** every(): milliseconds between calls; 0 for on(). */
        final long everyMs;
        long due;
        /** enter: the mortals inside the region now (lower-case names). */
        final Set<String> inside = new HashSet<>();

        Listener(int id, String event, Map<String, Object> filter, Function fn, long everyMs) {
            this.id = id;
            this.event = event;
            this.filter = filter;
            this.fn = fn;
            this.everyMs = everyMs;
        }
    }

    private record Event(Listener listener, Map<String, Object> data) {
    }

    private final String code;
    private final Map<String, Object> args;
    private final ScriptLimits limits;
    private final ScriptHost host;
    private final ScriptBudget budget;

    private final StringBuilder log = new StringBuilder();
    private boolean logFull;
    private final Map<Pos, String> pending = new LinkedHashMap<>();
    private int pendingLine;
    private final List<Call> queue = new ArrayList<>();
    private final Map<String, String> blockIds = new LinkedHashMap<>();
    private int failedLine;

    private final Map<Integer, Listener> listeners = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Event> events = new LinkedBlockingQueue<>(64);
    private int nextId;
    private volatile long listenUntil;
    private volatile boolean stopped;
    private volatile String stopReason = "it was stopped";
    private volatile Thread runner;
    private Scriptable scope;

    public ScriptRun(String code, Map<String, Object> args, ScriptLimits limits, ScriptHost host) {
        this.code = code == null ? "" : code;
        this.args = args == null ? Map.of() : args;
        this.limits = limits;
        this.host = host;
        this.budget = new ScriptBudget(limits);
    }

    // ---- Running ---------------------------------------------------------------------------------------

    /** Executes the program. Never throws: a mistake or a stop is in the result. */
    public Result run() {
        runner = Thread.currentThread();
        if (code.isBlank()) return new Result(false, "", "The script is empty.", 0, 0, budget.spent());
        if (code.length() > ScriptLimits.MAX_CODE) {
            return new Result(false, "", "The script is too long (" + code.length() + " characters; at most "
                    + ScriptLimits.MAX_CODE + "). Keep the data small and let loops do the work.", 0, 0, budget.spent());
        }
        Context cx = SANDBOX.enterContext();
        Result r;
        try {
            cx.putThreadLocal(KEY, this);
            r = attempt(() -> {
                ScriptableObject top = cx.initSafeStandardObjects();
                for (String name : REMOVED) top.delete(name);
                cx.evaluateString(top, SAFER, "builtins", 1, null);
                install(cx, top);
                scope = top;
                // Wrapped in a function so a top-level `return` works; the first line stays line 1.
                Object value = cx.evaluateString(top, "(function(){" + code + "\n})()", SOURCE, 1, null);
                Object plain = ScriptValues.toJava(value);
                if (plain != null && !(plain instanceof Function)) say("-> " + ScriptValues.show(plain));
                flush();
            });
        } finally {
            cx.removeThreadLocal(KEY);
            Context.exit();
        }
        if (!r.ok()) {
            listeners.clear();
        } else {
            long now = System.currentTimeMillis();
            listenUntil = now + limits.listenerSeconds() * 1000L;
            for (Listener l : listeners.values()) l.due = now + l.everyMs;
        }
        return new Result(r.ok(), r.log(), r.error(), r.line(), listeners.size(), r.spent());
    }

    /**
     * Answers events until the handlers' time is up, none is left, a budget runs out, a handler makes a
     * mistake, or {@link #stop}. Returns what was logged since {@link #run()}.
     */
    public Result serve() {
        int mark = log.length();
        runner = Thread.currentThread();
        Result bad = null;
        if (scope != null && !listeners.isEmpty()) {
            Context cx = SANDBOX.enterContext();
            try {
                cx.putThreadLocal(KEY, this);
                while (bad == null && !stopped && !listeners.isEmpty()) {
                    long now = System.currentTimeMillis();
                    if (now >= listenUntil) break;
                    Listener timer = null;
                    long next = listenUntil;
                    for (Listener l : listeners.values()) {
                        if (l.everyMs > 0 && l.due < next) {
                            next = l.due;
                            timer = l;
                        }
                    }
                    Event e;
                    if (timer != null && next <= now) {
                        timer.due = now + timer.everyMs;
                        e = new Event(timer, Map.of());
                    } else {
                        try {
                            e = events.poll(Math.max(1, next - now), TimeUnit.MILLISECONDS);
                        } catch (InterruptedException ex) {
                            break;
                        }
                    }
                    if (e == null || !listeners.containsKey(e.listener().id)) continue;
                    Event event = e;
                    Result r = attempt(() -> {
                        budget.event();
                        Object data = event.listener().everyMs > 0 ? Undefined.instance : ScriptValues.toJs(cx, scope, event.data());
                        event.listener().fn.call(cx, scope, scope, new Object[] { data });
                        flush();
                    });
                    if (!r.ok()) bad = r;
                }
            } finally {
                cx.removeThreadLocal(KEY);
                Context.exit();
            }
        }
        listeners.clear();
        events.clear();
        String said = log.substring(Math.min(mark, log.length())).trim();
        if (bad != null) return new Result(false, said, bad.error(), bad.line(), 0, budget.spent());
        return new Result(true, said, null, 0, 0, budget.spent());
    }

    private interface Body {
        void run();
    }

    /** Runs a piece of the script and turns whatever ends it into a result. */
    private Result attempt(Body body) {
        budget.resume();
        try {
            body.run();
            return new Result(true, log.toString().trim(), null, 0, 0, budget.spent());
        } catch (ScriptStop s) {
            boolean ours = stopped;
            String rest = "";
            if (!ours) {
                // What it had asked for before the stop is still carried out; nothing new is.
                try {
                    flush();
                } catch (RuntimeException | ScriptStop e) {
                    rest = " Its last few calls were refused.";
                }
            }
            return failed(s.line(), (s.line() > 0 ? "Stopped at line " + s.line() + ": " : "Stopped: ") + s.getMessage() + "."
                    + (ours ? "" : " What it had already done stays done." + rest));
        } catch (RhinoException e) {
            int line = lineOf(e);
            if (line == 0) line = failedLine;
            String what = e.details();
            if (what == null || what.isBlank()) what = e.getClass().getSimpleName();
            return failed(line, (line > 0 ? "Line " + line + ": " : "") + what);
        } catch (StackOverflowError e) {
            return failed(0, "The script called itself too deeply.");
        } catch (OutOfMemoryError e) {
            pending.clear();
            queue.clear();
            return failed(0, "The script used too much memory.");
        } catch (RuntimeException e) {
            return failed(0, "The script could not run: " + e);
        } finally {
            budget.pause();
            pending.clear();
            queue.clear();
        }
    }

    private Result failed(int line, String error) {
        String shown = error;
        if (line > 0) {
            String[] lines = code.split("\n", -1);
            if (line <= lines.length && !lines[line - 1].isBlank()) {
                String src = lines[line - 1].trim();
                shown += "\n  " + line + " | " + (src.length() > 160 ? src.substring(0, 159) + "…" : src);
            }
        }
        return new Result(false, log.toString().trim(), shown, line, 0, budget.spent());
    }

    private void observe(int count) {
        try {
            if (stopped) throw new ScriptStop(stopReason);
            budget.steps(count);
        } catch (ScriptStop s) {
            throw s.at(line());
        }
    }

    /** The script line running now, or 0. */
    private int line() {
        try {
            return lineOf(new EvaluatorException(""));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int lineOf(RhinoException e) {
        if (SOURCE.equals(e.sourceName()) && e.lineNumber() > 0) return e.lineNumber();
        try {
            for (ScriptStackElement el : e.getScriptStack()) {
                if (SOURCE.equals(el.fileName) && el.lineNumber > 0) return el.lineNumber;
            }
        } catch (RuntimeException ignored) {
            // no stack to read
        }
        return 0;
    }

    // ---- What a script can call ------------------------------------------------------------------------

    private void install(Context cx, ScriptableObject top) {
        for (Fn fn : ScriptApi.ALL) {
            String local = fn.name();
            Scriptable holder = top;
            int dot = local.indexOf('.');
            if (dot > 0) {
                String space = local.substring(0, dot);
                Object o = top.get(space, top);
                if (!(o instanceof Scriptable)) {
                    o = cx.newObject(top);
                    top.put(space, top, o);
                }
                holder = (Scriptable) o;
                local = local.substring(dot + 1);
            }
            holder.put(local, holder, new LambdaFunction(top, local, fn.min(), (c, s, t, a) -> call(c, fn, a)));
        }
        // console.log is what most programs reach for first.
        Scriptable console = cx.newObject(top);
        console.put("log", console, top.get("log", top));
        top.put("console", top, console);
        top.put("args", top, ScriptValues.toJs(cx, top, args));
    }

    private Object call(Context cx, Fn fn, Object[] raw) {
        try {
            if (stopped) throw new ScriptStop(stopReason);
            List<Object> a = new ArrayList<>(raw.length);
            for (Object o : raw) a.add(ScriptValues.toJava(o));
            // Arguments left out at the end are simply absent (but log shows a null it is given).
            while (!fn.name().equals("log") && !a.isEmpty() && a.get(a.size() - 1) == null) a.remove(a.size() - 1);
            if (a.size() < fn.min()) throw new ScriptRefusal("it is written " + fn.sig());
            Object out = dispatch(fn, a);
            return out == VOID ? Undefined.instance : ScriptValues.toJs(cx, scope, out);
        } catch (ScriptRefusal | IllegalArgumentException e) {
            throw ScriptRuntime.constructError("Error", fn.name() + ": " + e.getMessage());
        } catch (ScriptStop s) {
            throw s.at(line());
        }
    }

    private Object dispatch(Fn fn, List<Object> a) throws ScriptRefusal {
        switch (fn.name()) {
            case "log" -> {
                List<String> parts = new ArrayList<>();
                for (Object o : a) parts.add(ScriptValues.show(o));
                say(String.join(" ", parts));
                return VOID;
            }
            case "wait" -> {
                int ticks = Math.max(1, Math.min(1200, ScriptValues.whole(a, 0, "ticks")));
                budget.waited(ticks);
                flush();
                budget.pause();
                try {
                    host.sleep(ticks);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ScriptStop(stopReason);
                } finally {
                    budget.resume();
                }
                return VOID;
            }
            case "on" -> {
                return (double) listen(a);
            }
            case "every" -> {
                if (!(a.get(1) instanceof Function f)) throw new ScriptRefusal("the second argument must be a function");
                int ticks = Math.max(2, Math.min(1200, ScriptValues.whole(a, 0, "ticks")));
                return (double) add(new Listener(0, "every", Map.of(), f, ticks * 50L));
            }
            case "off" -> {
                listeners.remove((int) ScriptValues.num(a, 0, "id"));
                return VOID;
            }
            case "world.set" -> {
                int x = ScriptValues.whole(a, 0, "x"), y = ScriptValues.whole(a, 1, "y"), z = ScriptValues.whole(a, 2, "z");
                set(x, y, z, block(ScriptValues.text(a, 3, "block")));
                return VOID;
            }
            case "world.fill" -> {
                int x1 = ScriptValues.whole(a, 0, "x1"), y1 = ScriptValues.whole(a, 1, "y1"), z1 = ScriptValues.whole(a, 2, "z1");
                int x2 = ScriptValues.whole(a, 3, "x2"), y2 = ScriptValues.whole(a, 4, "y2"), z2 = ScriptValues.whole(a, 5, "z2");
                String block = block(ScriptValues.text(a, 6, "block"));
                long volume = (Math.abs((long) x2 - x1) + 1) * (Math.abs((long) y2 - y1) + 1) * (Math.abs((long) z2 - z1) + 1);
                if (volume > budget.blocksLeft()) {
                    throw new ScriptRefusal("that is " + volume + " blocks, and this script may set " + budget.blocksLeft()
                            + " more (" + limits.blocks() + " in all)");
                }
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                    for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, block);
                return VOID;
            }
            case "world.explode" -> {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("target", spot(a, 0));
                if (ScriptValues.has(a, 3)) p.put("power", ScriptValues.num(a, 3, "power"));
                if (ScriptValues.has(a, 4)) p.put("fire", ScriptValues.flag(a, 4));
                return power("explode", p);
            }
            case "power" -> {
                Object given = a.size() > 1 ? a.get(1) : null;
                if (given != null && !(given instanceof Map)) throw new ScriptRefusal("args must be an object, like {player: 'Steve'}");
                @SuppressWarnings("unchecked")
                Map<String, Object> p = given == null ? new LinkedHashMap<>() : (Map<String, Object>) given;
                return power(ScriptValues.text(a, 0, "name").toLowerCase(Locale.ROOT), p);
            }
            default -> {
                budget.hostCall();
                if (fn.name().equals("command")) budget.command();
                if (fn.name().equals("world.lightning")) budget.entities(1);
                if (fn.name().equals("world.spawn")) {
                    budget.entities(ScriptValues.has(a, 4) ? (int) Math.max(1, Math.min(1000, ScriptValues.num(a, 4, "count"))) : 1);
                }
                if (fn.name().equals("world.display")) budget.displays(1);
                queue.add(new Call(fn.name(), Collections.unmodifiableList(a), line()));
                if (fn.kind() == ScriptApi.Kind.DO) {
                    if (queue.size() >= MAX_QUEUE) flush();
                    return VOID;
                }
                List<Outcome> outs = flush();
                return outs.get(outs.size() - 1).value();
            }
        }
    }

    private static String spot(List<Object> a, int from) throws ScriptRefusal {
        return ScriptValues.whole(a, from, "x") + " " + ScriptValues.whole(a, from + 1, "y") + " " + ScriptValues.whole(a, from + 2, "z");
    }

    private Map<String, Object> power(String name, Map<String, Object> p) throws ScriptRefusal {
        if (ScriptApi.NOT_POWERS.contains(name)) throw new ScriptRefusal("'" + name + "' cannot be used from inside a script");
        budget.powerCall();
        if (name.equals("spawn_entity")) {
            Object n = p.get("count");
            budget.entities(n instanceof Double d ? (int) Math.max(1, Math.min(1000, d)) : 1);
        }
        flush();
        budget.pause();
        try {
            return host.power(name, p);
        } finally {
            budget.resume();
        }
    }

    private String block(String id) throws ScriptRefusal {
        String known = blockIds.get(id);
        if (known == null) {
            known = host.block(id);
            if (blockIds.size() < 256) blockIds.put(id, known);
        }
        return known;
    }

    private void set(int x, int y, int z, String block) {
        if (pending.isEmpty()) pendingLine = line();
        Pos at = new Pos(x, y, z);
        if (!pending.containsKey(at)) budget.blocks(1);
        pending.put(at, block);
    }

    private void say(String line) {
        if (logFull) return;
        if (log.length() + line.length() > ScriptLimits.MAX_LOG) {
            log.append(line, 0, Math.max(0, ScriptLimits.MAX_LOG - log.length())).append("\n… (the log is full)");
            logFull = true;
            return;
        }
        log.append(line).append('\n');
    }

    /** Visits the world once with everything asked since the last visit. Throws a script error for the first refusal. */
    private List<Outcome> flush() {
        if (!pending.isEmpty()) {
            List<Object> placements = new ArrayList<>(pending.size());
            pending.forEach((p, b) -> placements.add(new Placement(p.x(), p.y(), p.z(), b)));
            pending.clear();
            queue.add(0, new Call("world.place", placements, pendingLine));
        }
        if (queue.isEmpty()) return List.of();
        List<Call> batch = new ArrayList<>(queue);
        queue.clear();
        List<Outcome> outs;
        budget.pause();
        try {
            outs = host.perform(batch);
        } finally {
            budget.resume();
        }
        if (outs == null || outs.size() != batch.size()) throw new IllegalStateException("the world gave no answer");
        for (int i = 0; i < outs.size(); i++) {
            String refused = outs.get(i).refused();
            if (refused == null) continue;
            Call c = batch.get(i);
            failedLine = c.line();
            String name = c.fn().equals("world.place") ? "world.set" : c.fn();
            int here = line();
            throw ScriptRuntime.constructError("Error", name + (c.line() > 0 && c.line() != here ? " (line " + c.line() + ")" : "")
                    + ": " + refused);
        }
        return outs;
    }

    // ---- Handlers --------------------------------------------------------------------------------------

    private int listen(List<Object> a) throws ScriptRefusal {
        String event = ScriptValues.text(a, 0, "event").toLowerCase(Locale.ROOT);
        if (!ScriptApi.EVENTS.contains(event)) {
            throw new ScriptRefusal("there is no event '" + event + "'. Events: " + String.join(", ", ScriptApi.EVENTS)
                    + " (and every(ticks, fn) for time)");
        }
        if (!(a.get(a.size() - 1) instanceof Function fn)) throw new ScriptRefusal("the last argument must be a function");
        Map<String, Object> filter = new LinkedHashMap<>();
        if (a.size() >= 3 && a.get(1) instanceof Map<?, ?> given) {
            List<Object> xyz = new ArrayList<>();
            for (String k : List.of("x", "y", "z", "r")) xyz.add(given.get(k));
            boolean region = xyz.get(0) != null || xyz.get(1) != null || xyz.get(2) != null;
            if (region) {
                filter.put("x", ScriptValues.num(xyz, 0, "filter.x"));
                filter.put("y", ScriptValues.num(xyz, 1, "filter.y"));
                filter.put("z", ScriptValues.num(xyz, 2, "filter.z"));
                filter.put("r", Math.max(1, Math.min(64, ScriptValues.num(xyz, 3, "filter.r", 4))));
            }
            if (given.get("player") instanceof String p && !p.isBlank()) filter.put("player", p.trim());
        }
        if (event.equals("enter") && !filter.containsKey("x")) {
            throw new ScriptRefusal("'enter' needs a place: on('enter', {x: 10, y: 64, z: -3, r: 5}, fn)");
        }
        return add(new Listener(0, event, filter, fn, 0));
    }

    private int add(Listener l) throws ScriptRefusal {
        if (limits.listeners() == 0) throw new ScriptRefusal("handlers are turned off on this server");
        if (listeners.size() >= limits.listeners()) {
            throw new ScriptRefusal("at most " + limits.listeners() + " handlers at once (off(id) ends one)");
        }
        Listener made = new Listener(++nextId, l.event, l.filter, l.fn, l.everyMs);
        made.due = System.currentTimeMillis() + made.everyMs;
        listeners.put(made.id, made);
        return made.id;
    }

    /**
     * Something happened in the world. Any thread; cheap. "move" (a mortal's place, sent now and then) is
     * what 'enter' handlers are worked out from; the others are hit, break, chat. data holds "player" and,
     * where it has a place, x y z.
     */
    public void offer(String event, Map<String, Object> data) {
        if (stopped || listeners.isEmpty()) return;
        boolean move = event.equals("move");
        for (Listener l : listeners.values()) {
            if (l.everyMs > 0 || !l.event.equals(move ? "enter" : event)) continue;
            if (move) {
                // Fires once each time a mortal comes in from outside (or is found inside at first).
                Object named = l.filter.get("player");
                String who = String.valueOf(data.get("player")).toLowerCase(Locale.ROOT);
                if (named != null && !String.valueOf(named).equalsIgnoreCase(who)) continue;
                boolean in = Boolean.TRUE.equals(inRegionOnly(l.filter, data));
                synchronized (l.inside) {
                    boolean was = l.inside.contains(who);
                    if (!in) l.inside.remove(who);
                    if (!in || was) continue;
                    l.inside.add(who);
                }
            } else if (!matches(l.filter, data)) {
                continue;
            }
            events.offer(new Event(l, Map.copyOf(data)));
        }
    }

    /** Whether any handler listens for this ("move" asks about 'enter'). */
    public boolean wants(String event) {
        if (stopped) return false;
        String e = event.equals("move") ? "enter" : event;
        for (Listener l : listeners.values()) if (l.event.equals(e)) return true;
        return false;
    }

    static boolean matches(Map<String, Object> filter, Map<String, Object> data) {
        Object player = filter.get("player");
        if (player != null && !String.valueOf(player).equalsIgnoreCase(String.valueOf(data.get("player")))) return false;
        Boolean in = inRegionOnly(filter, data);
        return in == null || in;
    }

    /** Inside the filter's region; null when the filter has none. */
    private static Boolean inRegionOnly(Map<String, Object> filter, Map<String, Object> data) {
        if (!(filter.get("x") instanceof Double fx)) return null;
        if (!(data.get("x") instanceof Number x) || !(data.get("y") instanceof Number y) || !(data.get("z") instanceof Number z)) {
            return false;
        }
        double dx = x.doubleValue() - fx, dy = y.doubleValue() - (Double) filter.get("y"), dz = z.doubleValue() - (Double) filter.get("z");
        double r = (Double) filter.get("r");
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    // ---- From outside ----------------------------------------------------------------------------------

    /** Ends the script as soon as it can be: at its next step, call or wait. Any thread. */
    public void stop(String reason) {
        if (reason != null && !reason.isBlank()) stopReason = reason;
        stopped = true;
        Thread t = runner;
        if (t != null && t != Thread.currentThread()) t.interrupt();
    }

    public boolean stopped() {
        return stopped;
    }

    /** Handlers alive now. */
    public int listeners() {
        return listeners.size();
    }

    /** "chat, enter, every" */
    public String listening() {
        Set<String> names = new TreeSet<>();
        for (Listener l : listeners.values()) names.add(l.event);
        return String.join(", ", names);
    }

    /** Seconds the handlers still have. */
    public int secondsLeft() {
        return (int) Math.max(0, (listenUntil - System.currentTimeMillis()) / 1000);
    }

    public ScriptBudget budget() {
        return budget;
    }
}
