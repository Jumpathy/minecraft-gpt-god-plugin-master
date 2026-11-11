package net.bigyous.gptgod.client.fx;

import java.util.Locale;
import java.util.Map;

/**
 * A formula the god writes for an effect, e.g. "cos(u*tau*3 + t*0.05) * (1 + u*6)". Pure. It is compiled once
 * into a short program for a stack machine and then worked out for every particle on every tick. A formula has
 * no loops, knows only the names listed here and can call nothing but maths, and its size is capped, so the
 * worst a formula can do is draw something ugly. One thread at a time may use an instance.
 *
 * This file is word for word the same in the plugin and in the client mod, apart from the package line
 * (FxExprTest checks it), so both work out the same numbers.
 */
public final class FxExpr {
    public static final int MAX_CHARS = 240;
    public static final int MAX_STEPS = 160;

    /** The values a formula may read, in the order callers fill them in. */
    public static final int T = 0, P = 1, I = 2, U = 3, N = 4, SEED = 5, VARS = 6;

    public static final String NAMES = "t (ticks since the layer began), p (its progress 0-1), i (this one's number, from 0), "
            + "n (how many), u (i/(n-1), 0-1), r (a random 0-1 that stays with this one; rand(k) gives others), pi, tau; "
            + "+ - * / % ^ and < > <= >= == != (1 or 0); sin cos tan asin acos atan atan2(y,x) sqrt abs sign floor ceil "
            + "round fract exp log pow(a,b) min(a,b) max(a,b) mod(a,b) clamp(x,lo,hi) mix(a,b,k) step(edge,x) "
            + "smooth(a,b,x) ease(x) noise(x,y,z) (-1 to 1, smooth) if(cond,a,b)";

    /** Thrown with a message meant for the god. */
    public static final class Invalid extends Exception {
        private static final long serialVersionUID = 1L;

        public Invalid(String message) {
            super(message);
        }
    }

    private static final int CONST = 0, VAR = 1, ADD = 2, SUB = 3, MUL = 4, DIV = 5, MOD = 6, POW = 7, NEG = 8, LT = 9, GT = 10,
            LE = 11, GE = 12, EQ = 13, NE = 14, SIN = 15, COS = 16, TAN = 17, ASIN = 18, ACOS = 19, ATAN = 20, ATAN2 = 21,
            SQRT = 22, ABS = 23, SIGN = 24, FLOOR = 25, CEIL = 26, ROUND = 27, FRACT = 28, EXP = 29, LOG = 30, MIN = 31,
            MAX = 32, CLAMP = 33, MIX = 34, STEP = 35, SMOOTH = 36, EASE = 37, NOISE = 38, RAND = 39, IF = 40;

    /** A function's step and how many values it takes (noise takes 1 to 3). */
    private static final Map<String, int[]> FUNCTIONS = Map.ofEntries(fn("sin", SIN, 1), fn("cos", COS, 1), fn("tan", TAN, 1),
            fn("asin", ASIN, 1), fn("acos", ACOS, 1), fn("atan", ATAN, 1), fn("atan2", ATAN2, 2), fn("sqrt", SQRT, 1),
            fn("abs", ABS, 1), fn("sign", SIGN, 1), fn("floor", FLOOR, 1), fn("ceil", CEIL, 1), fn("round", ROUND, 1),
            fn("fract", FRACT, 1), fn("exp", EXP, 1), fn("log", LOG, 1), fn("pow", POW, 2), fn("min", MIN, 2), fn("max", MAX, 2),
            fn("mod", MOD, 2), fn("clamp", CLAMP, 3), fn("mix", MIX, 3), fn("step", STEP, 2), fn("smooth", SMOOTH, 3),
            fn("smoothstep", SMOOTH, 3), fn("ease", EASE, 1), fn("noise", NOISE, 3), fn("rand", RAND, 1), fn("if", IF, 3));

    private static Map.Entry<String, int[]> fn(String name, int step, int takes) {
        return Map.entry(name, new int[] { step, takes });
    }

    private final String source;
    private final int[] steps;
    private final double[] numbers;
    private final double[] stack;
    private final boolean fixed;
    private final double value;

    private FxExpr(String source, int[] steps, double[] numbers, int depth, boolean fixed) {
        this.source = source;
        this.steps = steps;
        this.numbers = numbers;
        this.stack = new double[Math.max(1, depth)];
        this.fixed = fixed;
        this.value = fixed ? run(new double[VARS]) : 0;
    }

    /** A formula that is always this number. */
    public static FxExpr of(double v) {
        return new FxExpr(Double.toString(v), new int[] { CONST, 0 }, new double[] { v }, 1, true);
    }

    public static FxExpr compile(String text) throws Invalid {
        if (text == null || text.isBlank()) throw new Invalid("a formula is empty");
        if (text.length() > MAX_CHARS) throw new Invalid("a formula is too long (at most " + MAX_CHARS + " characters)");
        Parser p = new Parser(text.toLowerCase(Locale.ROOT));
        p.expr();
        p.space();
        if (p.at < p.s.length()) throw p.bad("I cannot read '" + p.s.charAt(p.at) + "'");
        return new FxExpr(text.trim(), java.util.Arrays.copyOf(p.steps, p.count), java.util.Arrays.copyOf(p.numbers, p.numberCount),
                p.deepest, !p.varies);
    }

    public String source() {
        return source;
    }

    /** Whether it is the same number whatever the time and whichever particle. */
    public boolean fixed() {
        return fixed;
    }

    /** vars holds T, P, I, U, N and SEED. The result may be infinite or not a number. */
    public double eval(double[] vars) {
        return fixed ? value : run(vars);
    }

    /** As {@link #eval(double[])}, kept within lo..hi; what is not a number becomes the fallback. */
    public double eval(double[] vars, double lo, double hi, double fallback) {
        double v = fixed ? value : run(vars);
        return Double.isNaN(v) ? fallback : Math.max(lo, Math.min(hi, v));
    }

    private double run(double[] vars) {
        double[] s = stack;
        int sp = 0;
        for (int at = 0; at < steps.length; at += 2) {
            int arg = steps[at + 1];
            switch (steps[at]) {
                case CONST -> s[sp++] = numbers[arg];
                case VAR -> s[sp++] = vars[arg];
                case ADD -> s[--sp - 1] += s[sp];
                case SUB -> s[--sp - 1] -= s[sp];
                case MUL -> s[--sp - 1] *= s[sp];
                case DIV -> s[--sp - 1] /= s[sp];
                case MOD -> {
                    sp--;
                    s[sp - 1] = s[sp] == 0 ? 0 : s[sp - 1] - s[sp] * Math.floor(s[sp - 1] / s[sp]);
                }
                case POW -> {
                    sp--;
                    s[sp - 1] = Math.pow(s[sp - 1], s[sp]);
                }
                case NEG -> s[sp - 1] = -s[sp - 1];
                case LT -> s[--sp - 1] = s[sp - 1] < s[sp] ? 1 : 0;
                case GT -> s[--sp - 1] = s[sp - 1] > s[sp] ? 1 : 0;
                case LE -> s[--sp - 1] = s[sp - 1] <= s[sp] ? 1 : 0;
                case GE -> s[--sp - 1] = s[sp - 1] >= s[sp] ? 1 : 0;
                case EQ -> s[--sp - 1] = s[sp - 1] == s[sp] ? 1 : 0;
                case NE -> s[--sp - 1] = s[sp - 1] != s[sp] ? 1 : 0;
                case SIN -> s[sp - 1] = Math.sin(s[sp - 1]);
                case COS -> s[sp - 1] = Math.cos(s[sp - 1]);
                case TAN -> s[sp - 1] = Math.tan(s[sp - 1]);
                case ASIN -> s[sp - 1] = Math.asin(Math.max(-1, Math.min(1, s[sp - 1])));
                case ACOS -> s[sp - 1] = Math.acos(Math.max(-1, Math.min(1, s[sp - 1])));
                case ATAN -> s[sp - 1] = Math.atan(s[sp - 1]);
                case ATAN2 -> {
                    sp--;
                    s[sp - 1] = Math.atan2(s[sp - 1], s[sp]);
                }
                case SQRT -> s[sp - 1] = Math.sqrt(Math.max(0, s[sp - 1]));
                case ABS -> s[sp - 1] = Math.abs(s[sp - 1]);
                case SIGN -> s[sp - 1] = Math.signum(s[sp - 1]);
                case FLOOR -> s[sp - 1] = Math.floor(s[sp - 1]);
                case CEIL -> s[sp - 1] = Math.ceil(s[sp - 1]);
                case ROUND -> s[sp - 1] = Math.rint(s[sp - 1]);
                case FRACT -> s[sp - 1] = s[sp - 1] - Math.floor(s[sp - 1]);
                case EXP -> s[sp - 1] = Math.exp(s[sp - 1]);
                case LOG -> s[sp - 1] = s[sp - 1] <= 0 ? 0 : Math.log(s[sp - 1]);
                case MIN -> {
                    sp--;
                    s[sp - 1] = Math.min(s[sp - 1], s[sp]);
                }
                case MAX -> {
                    sp--;
                    s[sp - 1] = Math.max(s[sp - 1], s[sp]);
                }
                case CLAMP -> {
                    sp -= 2;
                    s[sp - 1] = Math.max(s[sp], Math.min(s[sp + 1], s[sp - 1]));
                }
                case MIX -> {
                    sp -= 2;
                    s[sp - 1] = s[sp - 1] + (s[sp] - s[sp - 1]) * s[sp + 1];
                }
                case STEP -> {
                    sp--;
                    s[sp - 1] = s[sp] >= s[sp - 1] ? 1 : 0;
                }
                case SMOOTH -> {
                    sp -= 2;
                    double span = s[sp] - s[sp - 1];
                    s[sp - 1] = span == 0 ? (s[sp + 1] >= s[sp] ? 1 : 0) : ease((s[sp + 1] - s[sp - 1]) / span);
                }
                case EASE -> s[sp - 1] = ease(s[sp - 1]);
                case NOISE -> {
                    sp -= 2;
                    s[sp - 1] = noise(s[sp - 1], s[sp], s[sp + 1], (long) vars[SEED]);
                }
                case RAND -> s[sp - 1] = hash((long) vars[I], (long) Math.floor(s[sp - 1]), 77, (long) vars[SEED]);
                case IF -> {
                    sp -= 2;
                    s[sp - 1] = s[sp - 1] != 0 ? s[sp] : s[sp + 1];
                }
                default -> throw new IllegalStateException("step " + steps[at]);
            }
        }
        return s[0];
    }

    /** 0 below 0, 1 above 1, a smooth S between. */
    public static double ease(double x) {
        if (!(x > 0)) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }

    /** A number 0..1 that depends only on what it is given. */
    public static double hash(long x, long y, long z, long seed) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ seed * 0x27D4EB2F165667C5L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Smooth noise, -1..1: the same place always gives the same value, and near places give near values. */
    public static double noise(double x, double y, double z, long seed) {
        if (Double.isNaN(x + y + z) || Double.isInfinite(x + y + z)) return 0;
        double fx = Math.floor(x), fy = Math.floor(y), fz = Math.floor(z);
        long ix = (long) fx, iy = (long) fy, iz = (long) fz;
        double ux = ease(x - fx), uy = ease(y - fy), uz = ease(z - fz);
        double sum = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
            double w = (dx == 1 ? ux : 1 - ux) * (dy == 1 ? uy : 1 - uy) * (dz == 1 ? uz : 1 - uz);
            sum += w * hash(ix + dx, iy + dy, iz + dz, seed);
        }
        return sum * 2 - 1;
    }

    // ---- Reading a formula ----------------------------------------------------------------------------

    private static final class Parser {
        final String s;
        int at;
        int[] steps = new int[32];
        int count;
        double[] numbers = new double[8];
        int numberCount;
        int depth, deepest;
        boolean varies;

        Parser(String s) {
            this.s = s;
        }

        Invalid bad(String what) {
            return new Invalid(what + " in the formula \"" + s.trim() + "\". A formula may use: " + NAMES + ".");
        }

        void space() {
            while (at < s.length() && Character.isWhitespace(s.charAt(at))) at++;
        }

        boolean eat(String token) {
            space();
            if (!s.startsWith(token, at)) return false;
            at += token.length();
            return true;
        }

        /** Adds a step; grows is how the stack changes (+1 for a value, -1 for a step that joins two). */
        void step(int code, int arg, int grows) throws Invalid {
            if (count >= MAX_STEPS * 2) throw new Invalid("the formula \"" + s.trim() + "\" is too long: make it simpler");
            if (count + 2 > steps.length) steps = java.util.Arrays.copyOf(steps, steps.length * 2);
            steps[count++] = code;
            steps[count++] = arg;
            depth += grows;
            deepest = Math.max(deepest, depth);
        }

        void number(double v) throws Invalid {
            if (numberCount == numbers.length) numbers = java.util.Arrays.copyOf(numbers, numbers.length * 2);
            numbers[numberCount] = v;
            step(CONST, numberCount++, 1);
        }

        void expr() throws Invalid {
            sum();
            space();
            int code = eat("<=") ? LE : eat(">=") ? GE : eat("==") ? EQ : eat("!=") ? NE : eat("<") ? LT : eat(">") ? GT : -1;
            if (code < 0) return;
            sum();
            step(code, 0, -1);
        }

        void sum() throws Invalid {
            term();
            while (true) {
                if (eat("+")) {
                    term();
                    step(ADD, 0, -1);
                } else if (eat("-")) {
                    term();
                    step(SUB, 0, -1);
                } else {
                    return;
                }
            }
        }

        void term() throws Invalid {
            unary();
            while (true) {
                if (eat("*")) {
                    unary();
                    step(MUL, 0, -1);
                } else if (eat("/")) {
                    unary();
                    step(DIV, 0, -1);
                } else if (eat("%")) {
                    unary();
                    step(MOD, 0, -1);
                } else {
                    return;
                }
            }
        }

        void unary() throws Invalid {
            if (eat("-")) {
                unary();
                step(NEG, 0, 0);
            } else if (eat("+")) {
                unary();
            } else {
                primary();
                if (eat("^")) {
                    unary();
                    step(POW, 0, -1);
                }
            }
        }

        void primary() throws Invalid {
            space();
            if (at >= s.length()) throw bad("Something is missing at the end");
            char c = s.charAt(at);
            if (c == '(') {
                at++;
                expr();
                if (!eat(")")) throw bad("A ')' is missing");
                return;
            }
            if (Character.isDigit(c) || c == '.') {
                int from = at;
                while (at < s.length() && (Character.isDigit(s.charAt(at)) || s.charAt(at) == '.')) at++;
                try {
                    number(Double.parseDouble(s.substring(from, at)));
                } catch (NumberFormatException e) {
                    throw bad("'" + s.substring(from, at) + "' is not a number");
                }
                return;
            }
            if (!Character.isLetter(c)) throw bad("I cannot read '" + c + "'");
            int from = at;
            while (at < s.length() && (Character.isLetterOrDigit(s.charAt(at)) || s.charAt(at) == '_')) at++;
            String name = s.substring(from, at);
            if (eat("(")) {
                call(name);
                return;
            }
            switch (name) {
                case "pi" -> number(Math.PI);
                case "tau" -> number(Math.PI * 2);
                case "t" -> value(T);
                case "p" -> value(P);
                case "i" -> value(I);
                case "u" -> value(U);
                case "n" -> value(N);
                case "r" -> {
                    number(0);
                    varies = true;
                    step(RAND, 0, 0);
                }
                default -> throw bad("I do not know the name '" + name + "'");
            }
        }

        void value(int slot) throws Invalid {
            varies = true;
            step(VAR, slot, 1);
        }

        void call(String name) throws Invalid {
            int[] f = FUNCTIONS.get(name);
            if (f == null) throw bad("I do not know the function '" + name + "'");
            int given = 0;
            if (!eat(")")) {
                do {
                    expr();
                    given++;
                } while (eat(","));
                if (!eat(")")) throw bad("A ')' is missing after " + name + "(");
            }
            int takes = f[1];
            if (f[0] == NOISE && given >= 1 && given <= 3) {
                for (; given < 3; given++) number(0);
            }
            if (given != takes) throw bad(name + " takes " + takes + " value" + (takes == 1 ? "" : "s") + ", not " + given);
            if (f[0] == NOISE || f[0] == RAND) varies = true;
            step(f[0], 0, 1 - takes);
        }
    }
}
