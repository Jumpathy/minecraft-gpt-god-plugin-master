package net.bigyous.gptgodmc.god.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** The formula language: what it works out, what it refuses, and that the mod's copy is the same file. */
class FxExprTest {
    private static double[] vars(double t, double p, double i, double u, double n, double seed) {
        return new double[] { t, p, i, u, n, seed };
    }

    private static double eval(String text, double... v) throws Exception {
        return FxExpr.compile(text).eval(v.length == 0 ? new double[FxExpr.VARS] : v);
    }

    @Test
    void arithmeticFollowsTheUsualOrder() throws Exception {
        assertEquals(7, eval("1 + 2 * 3"));
        assertEquals(9, eval("(1 + 2) * 3"));
        assertEquals(-4, eval("-2 ^ 2"), "a minus sign applies after a power");
        assertEquals(512, eval("2 ^ 3 ^ 2"), "powers group from the right");
        assertEquals(0.5, eval("2 ^ -1"));
        assertEquals(1, eval("7 % 3"));
        assertEquals(2, eval("-1 % 3"), "a remainder is never negative");
        assertEquals(2.5, eval("10 / 4"));
        assertEquals(1, eval("3 > 2"));
        assertEquals(0, eval("3 <= 2"));
        assertEquals(1, eval("1 + 1 == 2"));
        assertEquals(5, eval("if(2 != 2, 4, 5)"));
        assertEquals(0.75, eval(".5 + .25"));
    }

    @Test
    void theNamesAndFunctionsWork() throws Exception {
        double[] v = vars(40, 0.4, 3, 0.25, 13, 9);
        assertEquals(40 + 0.4 + 3 + 0.25 + 13, eval("t + p + i + u + n", v), 1e-12);
        assertEquals(Math.PI * 3, eval("pi + TAU", v), 1e-12, "names are read in any case");
        assertEquals(Math.cos(0.25 * Math.PI * 2 * 3 + 40 * 0.05) * (1 + 0.25 * 6), eval("cos(u*tau*3 + t*0.05) * (1 + u*6)", v), 1e-12);
        assertEquals(Math.atan2(1, 2), eval("atan2(1, 2)"), 1e-12);
        assertEquals(3, eval("clamp(7, 1, 3)"));
        assertEquals(15, eval("mix(10, 20, 0.5)"));
        assertEquals(1, eval("step(2, 3)"));
        assertEquals(0.5, eval("smooth(0, 2, 1)"), 1e-12);
        assertEquals(0.5, eval("ease(0.5)"), 1e-12);
        assertEquals(0.25, eval("fract(3.25)"), 1e-12);
        assertEquals(8, eval("pow(2, 3) + min(4, 0) + max(-1, 0)"));
        assertEquals(1, eval("sign(5) * abs(-1) * floor(1.9) * ceil(0.1) * round(1.4)"));
        assertEquals(0, eval("sqrt(-4) + log(-1)"), "roots and logs of what has none are 0, never NaN");
    }

    @Test
    void randomStaysWithItsParticleAndNoiseIsSmooth() throws Exception {
        FxExpr r = FxExpr.compile("r"), other = FxExpr.compile("rand(1)");
        double a = r.eval(vars(0, 0, 5, 0, 10, 42)), later = r.eval(vars(99, 1, 5, 0, 10, 42));
        assertEquals(a, later, "the same at every tick");
        assertTrue(a >= 0 && a < 1);
        assertFalse(a == r.eval(vars(0, 0, 6, 0, 10, 42)), "another particle, another number");
        assertFalse(a == other.eval(vars(0, 0, 5, 0, 10, 42)), "rand(k) gives a second number");
        assertFalse(a == r.eval(vars(0, 0, 5, 0, 10, 43)), "another scene, another number");
        FxExpr noise = FxExpr.compile("noise(t * 0.1, i)");
        double before = noise.eval(vars(0, 0, 2, 0, 1, 7));
        double sum = 0;
        for (int t = 1; t <= 400; t++) {
            double now = noise.eval(vars(t, 0, 2, 0, 1, 7));
            assertTrue(now >= -1 && now <= 1);
            assertTrue(Math.abs(now - before) < 0.35, "no jumps: " + before + " to " + now);
            sum += Math.abs(now - before);
            before = now;
        }
        assertTrue(sum > 1, "but it does move");
    }

    @Test
    void whatNeverChangesIsWorkedOutOnce() throws Exception {
        assertTrue(FxExpr.compile("2 * pi + sin(1)").fixed());
        assertTrue(FxExpr.of(3).fixed());
        assertEquals(3, FxExpr.of(3).eval(new double[FxExpr.VARS]));
        assertFalse(FxExpr.compile("t").fixed());
        assertFalse(FxExpr.compile("r").fixed());
        assertFalse(FxExpr.compile("noise(1)").fixed(), "noise depends on the scene's seed");
    }

    @Test
    void resultsAreKeptInBounds() throws Exception {
        double[] v = new double[FxExpr.VARS];
        assertEquals(96, FxExpr.compile("1 / 0").eval(v, -96, 96, 0), "infinity is clamped");
        assertEquals(7, FxExpr.compile("0 / 0").eval(v, -96, 96, 7), "what is not a number becomes the fallback");
        assertEquals(-96, FxExpr.compile("-1000000000").eval(v, -96, 96, 0));
    }

    @Test
    void mistakesAreExplainedToTheGod() {
        for (String[] bad : new String[][] { { "", "empty" }, { "foo + 1", "name 'foo'" }, { "zap(1)", "function 'zap'" },
                { "sin(1, 2)", "sin takes 1 value" }, { "mix(1, 2)", "mix takes 3 values" }, { "(1 + 2", "')' is missing" },
                { "1 +", "missing at the end" }, { "1 $ 2", "cannot read '$'" }, { "1.2.3", "not a number" },
                { "2 2", "cannot read '2'" }, { "x".repeat(FxExpr.MAX_CHARS + 1), "too long" } }) {
            FxExpr.Invalid e = assertThrows(FxExpr.Invalid.class, () -> FxExpr.compile(bad[0]), bad[0]);
            assertTrue(e.getMessage().contains(bad[1]), bad[0] + " -> " + e.getMessage());
        }
        // A formula that is short to write but long to work out is refused too.
        StringBuilder deep = new StringBuilder("t");
        for (int k = 0; k < 70; k++) deep.append("+t");
        assertThrows(FxExpr.Invalid.class, () -> FxExpr.compile(deep + deep.toString().replace('t', 'i')));
    }

    @Test
    void theHelpListsWhatAFormulaMayUse() throws Exception {
        for (String name : new String[] { "sin", "cos", "tan", "asin", "acos", "atan", "atan2", "sqrt", "abs", "sign", "floor",
                "ceil", "round", "fract", "exp", "log", "pow", "min", "max", "mod", "clamp", "mix", "step", "smooth", "ease",
                "noise", "rand", "if", "pi", "tau" }) {
            assertTrue(FxExpr.NAMES.contains(name), name);
        }
    }

    /** The mod works out the same formulas with the same code: its copies differ only in the package line. */
    @Test
    void theModsCopiesAreTheSameFiles() throws Exception {
        for (String file : new String[] { "FxExpr", "FxField", "FxSolid" }) {
            String ours = body(Path.of("src/main/java/net/bigyous/gptgodmc/god/fx", file + ".java"));
            String theirs = body(Path.of("client-mod/src/main/java/net/bigyous/gptgod/client/fx", file + ".java"));
            assertEquals(ours, theirs, file + ".java differs between the plugin and the client mod: copy the changed one over");
        }
    }

    private static String body(Path file) throws Exception {
        String text = Files.readString(file).replace("\r\n", "\n");
        assertTrue(text.startsWith("package "), file.toString());
        return text.substring(text.indexOf('\n'));
    }
}
