package net.bigyous.gptgodmc.god.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.script.ScriptHost.Call;
import net.bigyous.gptgodmc.god.script.ScriptHost.Outcome;
import net.bigyous.gptgodmc.god.script.ScriptHost.Placement;

/** The sandbox, the budgets, the marshalling and the handlers, with a host that only writes down what it is asked. */
class ScriptRunTest {

    /** Records every call; answers a few. */
    static class FakeHost implements ScriptHost {
        final List<List<Call>> visits = new ArrayList<>();
        final List<String> powers = new ArrayList<>();
        final List<Map<String, Object>> powerArgs = new ArrayList<>();
        int slept;
        String refuse;

        @Override
        public String block(String id) throws ScriptRefusal {
            String k = id.trim().toLowerCase().replace(' ', '_');
            if (k.equals("bedrock") || k.equals("nonsense")) throw new ScriptRefusal("Unknown block '" + id + "'.");
            return k;
        }

        @Override
        public List<Outcome> perform(List<Call> calls) {
            visits.add(List.copyOf(calls));
            List<Outcome> out = new ArrayList<>();
            for (Call c : calls) {
                if (c.fn().equals(refuse)) {
                    out.add(Outcome.no("the world says no"));
                    continue;
                }
                out.add(switch (c.fn()) {
                    case "world.block" -> Outcome.ok("stone");
                    case "world.top" -> Outcome.ok(64);
                    case "world.at" -> Outcome.ok(Map.of("x", 10, "y", 64, "z", -3));
                    case "players.list" -> Outcome.ok(List.of(player("Steve"), player("Alex")));
                    case "players.get" -> Outcome.ok("Nobody".equals(c.args().get(0)) ? null : player((String) c.args().get(0)));
                    case "command" -> Outcome.ok(Map.of("ok", true, "text", "ran " + c.args().get(0)));
                    case "world.spawn" -> Outcome.ok(List.of(1, 2, 3));
                    case "world.display" -> Outcome.ok(7);
                    case "entity.get" -> Outcome.ok(Map.of("type", "wolf", "x", 5.5, "y", 64, "z", 5.5, "health", 8, "id", c.args().get(0)));
                    case "world.info" -> Outcome.ok(Map.of("block", "oak_stairs", "state", "facing=north", "biome", "plains", "light", 12));
                    case "world.near" -> Outcome.ok(List.of(Map.of("type", "player", "name", "Steve"), Map.of("type", "cow")));
                    case "world.ray", "players.looking" -> Outcome.ok(Map.of("x", 1.5, "y", 64, "z", 2.5, "block", "stone", "face", "up"));
                    default -> Outcome.ok(null);
                });
            }
            return out;
        }

        static Map<String, Object> player(String name) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", name);
            p.put("x", 1.5);
            p.put("y", 70);
            p.put("z", 2.5);
            p.put("look", Map.of("x", 0, "y", 0, "z", 1));
            p.put("health", 20);
            return p;
        }

        @Override
        public Map<String, Object> power(String name, Map<String, Object> args) {
            powers.add(name);
            powerArgs.add(args);
            return Map.of("ok", true, "text", "used " + name);
        }

        @Override
        public void sleep(int ticks) throws InterruptedException {
            slept += ticks;
        }

        List<Call> all() {
            List<Call> out = new ArrayList<>();
            visits.forEach(out::addAll);
            return out;
        }

        List<Call> named(String fn) {
            return all().stream().filter(c -> c.fn().equals(fn)).toList();
        }
    }

    private static ScriptLimits limits(int instructions, int seconds) {
        ScriptLimits d = ScriptLimits.DEFAULT;
        return new ScriptLimits(instructions, seconds, d.waitTicks(), d.hostCalls(), d.powerCalls(), d.commands(), d.blocks(),
                d.entities(), d.listeners(), 2, d.events(), 64);
    }

    private static ScriptRun.Result run(String code) {
        return run(code, new FakeHost());
    }

    private static ScriptRun.Result run(String code, ScriptHost host) {
        return new ScriptRun(code, Map.of(), limits(200_000, 5), host).run();
    }

    // ---- The sandbox -----------------------------------------------------------------------------------

    @Test
    void aScriptCanNameNoJavaClass() {
        for (String attempt : List.of(
                "java.lang.System.exit(1)",
                "Packages.java.lang.Runtime.getRuntime().exec('calc')",
                "new java.io.File('.').listFiles()",
                "importPackage(java.io)",
                "importClass(java.lang.Thread)",
                "JavaImporter(java.util)",
                "JavaAdapter(java.lang.Runnable, {run: function(){}})",
                "getClass('java.lang.System')",
                "load('x.js')",
                "print('x')",
                "require('fs')",
                "readFile('config.yml')",
                "quit()")) {
            ScriptRun.Result r = run(attempt);
            assertFalse(r.ok(), attempt);
            assertTrue(r.error().contains("not defined") || r.error().contains("is not a function"), attempt + " -> " + r.error());
        }
    }

    @Test
    void whatItIsGivenLeadsToNoJavaObjectEither() {
        // The classic ways out of an interpreter: through a function's constructor, a host function, an error.
        ScriptRun.Result r = run("""
                var found = [];
                var probes = [log, world, world.set, players.list, args, this, (function(){return this})(),
                              log.constructor, log.constructor('return this')(), new Error('x'), players.list()];
                for (var i = 0; i < probes.length; i++) {
                  var p = probes[i];
                  if (p == null) continue;
                  if (typeof p.getClass === 'function') found.push('getClass on ' + i);
                  if (typeof p.class !== 'undefined') found.push('class on ' + i);
                  if (typeof p.javaException !== 'undefined') found.push('javaException on ' + i);
                }
                try { null.x } catch (e) { if (e.javaException || e.rhinoException) found.push('exception object'); }
                try { world.set(1, 2, 3, 'nonsense') } catch (e) { if (e.javaException || e.rhinoException) found.push('host exception'); }
                var g = log.constructor('return this')();
                for (var name of ['java', 'javax', 'Packages', 'org', 'com', 'JavaAdapter', 'JavaImporter', 'getClass', 'ArrayBuffer', 'Int8Array'])
                  if (typeof g[name] !== 'undefined') found.push(name);
                log(found.length ? found.join(', ') : 'nothing');
                """);
        assertTrue(r.ok(), r.error());
        assertEquals("nothing", r.log());
    }

    @Test
    void anEndlessLoopIsStoppedByTheStepLimit() {
        ScriptRun.Result r = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> run("var n = 0;\nwhile (true) { n++; }"));
        assertFalse(r.ok());
        assertTrue(r.error().contains("more than 200,000 steps"), r.error());
        assertEquals(2, r.line());
        assertTrue(r.error().startsWith("Stopped at line 2"), r.error());
    }

    @Test
    void anEndlessLoopIsStoppedByTheClock() {
        long t0 = System.nanoTime();
        ScriptRun.Result r = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> new ScriptRun("var s = ''; for (var i = 0; i < 2000; i++) s += 'xxxxxxxxxx'; while (true) { s.split('x').join('y'); }",
                        Map.of(), limits(50_000_000, 1), new FakeHost()).run());
        double took = (System.nanoTime() - t0) / 1e9;
        assertFalse(r.ok());
        assertTrue(r.error().contains("computed for more than 1 seconds"), r.error());
        assertTrue(took >= 0.9 && took < 10, "took " + took);
    }

    @Test
    void noScriptCanCatchAStopOrRunCodeAfterIt() {
        FakeHost host = new FakeHost();
        ScriptRun.Result r = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> run("""
                try { while (true) {} }
                catch (e) { log('caught'); while (true) {} }
                finally { log('finally'); world.lightning(0, 0, 0); }
                """, host));
        assertFalse(r.ok());
        assertEquals("", r.log());
        assertTrue(host.all().isEmpty());
    }

    @Test
    void endlessRecursionAndRunawayBuiltinsEndToo() {
        assertFalse(assertTimeoutPreemptively(Duration.ofSeconds(20), () -> run("function f() { return f() + 1; } f();")).ok());
        for (String hog : List.of("'x'.repeat(1e9)", "'x'.padStart(1e9)", "new Array(1e9).fill(0)", "Array.from({length: 1e9})",
                "var s = 'x'; while (true) s += s;", "/(a+)+b/.test('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')")) {
            ScriptRun.Result r = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> run(hog), hog);
            assertFalse(r.ok(), hog);
        }
        // The rewritten builtins still behave.
        ScriptRun.Result r = run("log('ab'.repeat(3), '7'.padStart(3, '0'), 'x'.padEnd(3, '-') + '|', new Array(3).fill(1).join(''),"
                + " Array.from('abc').length, Array.from({length: 2}, function(v, i) { return i * 2; }).join())");
        assertTrue(r.ok(), r.error());
        assertEquals("ababab 007 x--| 111 3 0,2", r.log());
    }

    @Test
    void anOverlongProgramIsRefusedUnrun() {
        ScriptRun.Result r = run("log(1);\n" + "//".repeat(ScriptLimits.MAX_CODE));
        assertFalse(r.ok());
        assertTrue(r.error().contains("too long"));
        assertEquals("", r.log());
    }

    // ---- Mistakes come back with their line ---------------------------------------------------------------

    @Test
    void mistakesNameTheirLineAndShowIt() {
        ScriptRun.Result syntax = run("var a = 1;\nvar b = ;\nlog(a);");
        assertFalse(syntax.ok());
        assertEquals(2, syntax.line());
        assertTrue(syntax.error().startsWith("Line 2: "), syntax.error());

        ScriptRun.Result missing = run("var a = 1;\n\nlog(a);\nlog(nothere + 1);");
        assertEquals(4, missing.line());
        assertTrue(missing.error().contains("nothere"), missing.error());
        assertTrue(missing.error().contains("4 | log(nothere + 1);"), missing.error());
        assertEquals("1", missing.log(), "what ran before the mistake is still reported");

        ScriptRun.Result thrown = run("function f() {\n  throw new Error('my own');\n}\nf();");
        assertEquals(2, thrown.line());
        assertTrue(thrown.error().contains("my own"), thrown.error());

        ScriptRun.Result badBlock = run("var y = 64;\nworld.set(1, y, 2, 'nonsense');");
        assertEquals(2, badBlock.line());
        assertTrue(badBlock.error().contains("world.set: Unknown block 'nonsense'"), badBlock.error());

        ScriptRun.Result badArgs = run("world.set(1, 2);");
        assertTrue(badArgs.error().contains("world.set: it is written world.set(x, y, z, block)"), badArgs.error());
        ScriptRun.Result notNumber = run("world.set('a', 2, 3, 'stone');");
        assertTrue(notNumber.error().contains("x must be a number"), notNumber.error());
    }

    @Test
    void aRefusalFromTheWorldIsAnErrorTheScriptMayCatch() {
        FakeHost host = new FakeHost();
        host.refuse = "world.particle";
        // Deferred: it surfaces at the next visit to the world, naming the line that asked.
        ScriptRun.Result r = run("world.particle('flame', 1, 2, 3);\nlog('queued');\nwait(1);\nlog('not reached');", host);
        assertFalse(r.ok());
        assertEquals("queued", r.log());
        assertTrue(r.error().contains("world.particle (line 1): the world says no"), r.error());
        assertEquals(3, r.line());

        ScriptRun.Result caught = run("world.particle('flame', 1, 2, 3);\ntry { wait(1); } catch (e) { log('caught: ' + e.message); }", host);
        assertTrue(caught.ok(), caught.error());
        assertEquals("caught: world.particle (line 1): the world says no", caught.log());

        // At the very end of a script the line is still known.
        ScriptRun.Result last = run("log('a');\nworld.particle('flame', 1, 2, 3);", host);
        assertFalse(last.ok());
        assertEquals(2, last.line());
    }

    // ---- Marshalling ---------------------------------------------------------------------------------------

    @Test
    void valuesCrossAsPlainCopies() {
        FakeHost host = new FakeHost();
        ScriptRun run = new ScriptRun("""
                var c = world.at('vessel');
                log(c.x + c.y + c.z, typeof c, world.block(1, 2, 3), world.top(4.7, -0.2));
                var all = players.list();
                log(all.length, all[1].name, all[0].look.z, Array.isArray(all), players.get('Nobody') === null);
                all[0].name = 'changed';
                log(players.list()[0].name);
                log(args.height * 2, args.names.join('+'), args.deep.a.b, typeof args.missing);
                world.sound('block.bell.use', 1.5, 2, 3, undefined, 2);
                world.particle('flame', 1, 2, 3, 10, 0.5, '#ff0000');
                players.tell('Steve', 'hi ' + 5);
                log({a: 1, b: [true, null, 'x', 1.25]}, 3, 2.5, 'text', null);
                return [1, 2];
                """, Map.of("height", 4, "names", List.of("a", "b"), "deep", Map.of("a", Map.of("b", "c"))), limits(200_000, 5), host);
        ScriptRun.Result r = run.run();
        assertTrue(r.ok(), r.error());
        assertEquals("""
                71 object stone 64
                2 Alex 1 true true
                Steve
                8 a+b c undefined
                {a: 1, b: [true, null, "x", 1.25]} 3 2.5 text null
                -> [1, 2]""", r.log());
        // Asks reach the host with plain values: numbers as doubles, text as String, missing as null.
        Call top = host.named("world.top").get(0);
        assertEquals(List.of(4.7, -0.2), top.args());
        Call sound = host.named("world.sound").get(0);
        List<Object> expected = new ArrayList<>(List.of("block.bell.use", 1.5, 2.0, 3.0));
        expected.add(null);
        expected.add(2.0);
        assertEquals(expected, sound.args());
        assertEquals(8, sound.line());
        assertEquals(List.of("Steve", "hi 5"), host.named("players.tell").get(0).args());
        for (Call c : host.all()) for (Object a : c.args()) assertPlain(a);
        assertTrue(r.spent().contains("world/players calls"), r.spent());
    }

    private static void assertPlain(Object v) {
        if (v == null || v instanceof String || v instanceof Double || v instanceof Boolean || v instanceof Placement) return;
        if (v instanceof List<?> l) {
            l.forEach(ScriptRunTest::assertPlain);
            return;
        }
        if (v instanceof Map<?, ?> m) {
            m.values().forEach(ScriptRunTest::assertPlain);
            return;
        }
        throw new AssertionError("not a plain value: " + v.getClass());
    }

    @Test
    void callsBetweenTwoWaitsGoInOneVisitAndBlocksGoTogether() {
        FakeHost host = new FakeHost();
        ScriptRun.Result r = run("""
                for (var i = 0; i < 5; i++) world.set(i, 64.9, -0.5, 'Oak Planks');
                world.set(0, 64, -1, 'stone');
                world.particle('flame', 0, 65, 0);
                wait(10);
                world.fill(0, 70, 0, 1, 71, 1, 'glass');
                world.lightning(0, 72, 0, true);
                """, host);
        assertTrue(r.ok(), r.error());
        assertEquals(10, host.slept);
        assertEquals(2, host.visits.size());
        assertEquals(List.of("world.place", "world.particle"), host.visits.get(0).stream().map(Call::fn).toList());
        List<Object> first = host.visits.get(0).get(0).args();
        assertEquals(5, first.size(), "the same block set twice is one block");
        assertEquals(new Placement(0, 64, -1, "stone"), first.get(0));
        assertEquals(new Placement(4, 64, -1, "oak_planks"), first.get(4));
        assertEquals(List.of("world.place", "world.lightning"), host.visits.get(1).stream().map(Call::fn).toList());
        assertEquals(8, host.visits.get(1).get(0).args().size());
        assertTrue(r.spent().contains("13 blocks"), r.spent());
        assertTrue(r.spent().contains("10 ticks waited"), r.spent());
    }

    @Test
    void powersAndCommandsGoThroughTheHost() {
        FakeHost host = new FakeHost();
        ScriptRun.Result r = run("""
                var a = power('Heal', {player: 'Steve'});
                log(a.ok, a.text);
                log(world.explode(1.2, 64, -3.5, 3, true).text);
                log(command('say hello').text);
                try { power('script', {code: 'log(1)'}); } catch (e) { log(e.message); }
                try { power('command_done', {}); } catch (e) { log(e.message); }
                try { power('library_run', {name: 'x'}); } catch (e) { log(e.message); }
                try { power('heal', 'Steve'); } catch (e) { log(e.message); }
                """, host);
        assertTrue(r.ok(), r.error());
        assertEquals(List.of("heal", "explode"), host.powers);
        assertEquals(Map.of("player", "Steve"), host.powerArgs.get(0));
        assertEquals(Map.of("target", "1 64 -4", "power", 3.0, "fire", true), host.powerArgs.get(1));
        assertEquals("""
                true used heal
                used explode
                ran say hello
                power: 'script' cannot be used from inside a script
                power: 'command_done' cannot be used from inside a script
                power: 'library_run' cannot be used from inside a script
                power: args must be an object, like {player: 'Steve'}""", r.log());
    }

    @Test
    void whatAScriptMakesItCanSteerAndWhatItAsksItSees() {
        FakeHost host = new FakeHost();
        ScriptRun.Result r = run("""
                var wolves = world.spawn('wolf', 5, 64, 5, 3);
                log(wolves.length, wolves[2]);
                entity.set(wolves[0], {name: 'Fang', glowing: true});
                entity.target(wolves[0], 'Steve');
                entity.move(wolves[1], 9, 64, 9, 1.4);
                var w = entity.get(wolves[0]);
                log(w.type, w.health, w.id);
                var rune = world.display('text', 0.5, 70, 0.5, {text: 'KNEEL', scale: [2, 2, 2], glow: '#ff0000', seconds: 20});
                entity.move(rune, 0.5, 80, 0.5, 40);
                entity.set(rune, {yaw: 90, ticks: 10});
                entity.teleport(rune, 1, 2, 3);
                entity.push(wolves[2], 0, 1, 0);
                entity.remove(rune);
                var here = world.info(1, 64, 2);
                log(here.block, here.state, here.biome, here.light);
                var seen = players.looking('Steve', 20);
                log(seen.block, seen.face, world.ray(0, 70, 0, 0, -1, 0).y);
                log(world.near(0, 64, 0, 8).length, world.near(0, 64, 0, 8, 'cow')[1].type);
                """, host);
        assertTrue(r.ok(), r.error());
        assertEquals("3 3\nwolf 8 1\noak_stairs facing=north plains 12\nstone up 64\n2 cow", r.log());
        assertEquals(List.of("wolf", 5.0, 64.0, 5.0, 3.0), host.named("world.spawn").get(0).args());
        Call display = host.named("world.display").get(0);
        assertEquals("text", display.args().get(0));
        assertEquals(Map.of("text", "KNEEL", "scale", List.of(2.0, 2.0, 2.0), "glow", "#ff0000", "seconds", 20.0), display.args().get(4));
        assertEquals(List.of(1.0, Map.of("name", "Fang", "glowing", true)), host.named("entity.set").get(0).args());
        assertEquals(List.of(7.0, Map.of("yaw", 90.0, "ticks", 10.0)), host.named("entity.set").get(1).args());
        assertEquals(List.of(1.0, "Steve"), host.named("entity.target").get(0).args());
        assertEquals(List.of(7.0, 0.5, 80.0, 0.5, 40.0), host.named("entity.move").get(1).args());
        assertEquals(1, host.named("entity.remove").size());
        assertTrue(r.spent().contains("3 creatures or bolts"), r.spent());
        assertTrue(r.spent().contains("1 shape"), r.spent());
    }

    // ---- Budgets -------------------------------------------------------------------------------------------

    private static ScriptRun.Result with(ScriptLimits l, String code, FakeHost host) {
        return assertTimeoutPreemptively(Duration.ofSeconds(20), () -> new ScriptRun(code, Map.of(), l, host).run());
    }

    @Test
    void eachBudgetStopsTheScriptAndKeepsWhatWasDone() {
        ScriptLimits d = ScriptLimits.DEFAULT;

        FakeHost host = new FakeHost();
        ScriptRun.Result r = with(new ScriptLimits(d.instructions(), 5, 600, 5, 24, 60, 2000, 12, 4, 2, 400, 64),
                "for (var i = 0; i < 50; i++) {\n  world.particle('flame', i, 64, 0);\n}", host);
        assertFalse(r.ok());
        assertTrue(r.error().contains("more than 5 world and players calls"), r.error());
        assertEquals(2, r.line());
        assertEquals(5, host.named("world.particle").size(), "the calls within the budget were carried out");
        assertTrue(r.error().contains("already done stays done"), r.error());

        host = new FakeHost();
        r = with(new ScriptLimits(d.instructions(), 5, 600, 400, 24, 60, 10, 12, 4, 2, 400, 64),
                "for (var i = 0; i < 50; i++) world.set(i, 64, 0, 'stone');", host);
        assertTrue(r.error().contains("more than 10 blocks"), r.error());
        assertEquals(10, host.named("world.place").get(0).args().size());

        host = new FakeHost();
        r = with(new ScriptLimits(d.instructions(), 5, 600, 400, 24, 60, 10, 12, 4, 2, 400, 64),
                "try { world.fill(0, 0, 0, 9, 9, 9, 'stone'); } catch (e) { log(e.message); }\nworld.fill(0, 0, 0, 1, 1, 1, 'stone');", host);
        assertTrue(r.ok(), r.error());
        assertTrue(r.log().contains("that is 1000 blocks, and this script may set 10 more"), r.log());
        assertEquals(8, host.named("world.place").get(0).args().size());

        host = new FakeHost();
        r = with(new ScriptLimits(d.instructions(), 5, 600, 400, 24, 60, 2000, 3, 4, 2, 400, 64),
                "for (var i = 0; i < 9; i++) world.lightning(i, 64, 0);", host);
        assertTrue(r.error().contains("more than 3 creatures and lightning bolts"), r.error());
        assertEquals(3, host.named("world.lightning").size());

        host = new FakeHost();
        r = with(new ScriptLimits(d.instructions(), 5, 600, 400, 24, 60, 2000, 3, 4, 2, 400, 64), "world.spawn('zombie', 0, 64, 0, 4);", host);
        assertTrue(r.error().contains("more than 3 creatures"), r.error());
        assertTrue(host.named("world.spawn").isEmpty(), "a spawn past the budget never reaches the world");

        host = new FakeHost();
        r = with(new ScriptLimits(d.instructions(), 5, 600, 400, 24, 60, 2000, 12, 4, 2, 400, 3),
                "for (var i = 0; i < 9; i++) world.display('block', i, 70, 0, {block: 'glass'});", host);
        assertTrue(r.error().contains("more than 3 shapes"), r.error());
        assertEquals(3, host.named("world.display").size());

        host = new FakeHost();
        r = with(new ScriptLimits(d.instructions(), 5, 600, 400, 2, 60, 2000, 12, 4, 2, 400, 64),
                "for (var i = 0; i < 9; i++) power('heal', {player: 'Steve'});", host);
        assertTrue(r.error().contains("power() more than 2 times"), r.error());
        assertEquals(2, host.powers.size());

        host = new FakeHost();
        r = with(new ScriptLimits(d.instructions(), 5, 600, 400, 24, 3, 2000, 12, 4, 2, 400, 64),
                "for (var i = 0; i < 9; i++) command('say ' + i);", host);
        assertTrue(r.error().contains("more than 3 commands"), r.error());
        assertEquals(3, host.named("command").size());

        host = new FakeHost();
        r = with(new ScriptLimits(d.instructions(), 5, 30, 400, 24, 60, 2000, 12, 4, 2, 400, 64), "while (true) wait(20);", host);
        assertTrue(r.error().contains("waited more than 30 ticks"), r.error());
        assertEquals(20, host.slept);

        host = new FakeHost();
        r = with(new ScriptLimits(d.instructions(), 5, 600, 400, 24, 60, 2000, 12, 2, 2, 400, 64), """
                on('chat', function(e) {});
                every(20, function() {});
                try { on('hit', function(e) {}); } catch (e) { log(e.message); }
                """, host);
        assertTrue(r.ok(), r.error());
        assertEquals("on: at most 2 handlers at once (off(id) ends one)", r.log());
        assertEquals(2, r.listeners());
    }

    @Test
    void limitsCannotBeSwitchedOffByConfig() {
        ScriptLimits wild = new ScriptLimits(Integer.MAX_VALUE, 99999, 999999, Integer.MAX_VALUE, 99999, 99999, Integer.MAX_VALUE, 99999,
                999, 999999, Integer.MAX_VALUE, 99999);
        assertEquals(50_000_000, wild.instructions());
        assertEquals(60, wild.seconds());
        assertEquals(16, wild.listeners());
        assertEquals(1800, wild.listenerSeconds());
        assertEquals(512, wild.displays());
        assertEquals(512, wild.displays());
        ScriptLimits low = new ScriptLimits(-5, -5, -5, -5, -5, -5, -5, -5, -5, -5, -5, -5);
        assertEquals(10_000, low.instructions());
        assertEquals(1, low.seconds());
        assertEquals(0, low.blocks());
        assertNotNull(ScriptLimits.DEFAULT.describe());
    }

    // ---- Handlers ------------------------------------------------------------------------------------------

    @Test
    void handlersAnswerEventsAfterTheLastLineThenEnd() {
        FakeHost host = new FakeHost();
        ScriptRun run = new ScriptRun("""
                var seen = 0;
                var id = on('chat', {player: 'steve'}, function(e) {
                  seen++;
                  log(e.player + ' said ' + e.text);
                  players.tell(e.player, 'heard');
                  if (seen == 2) off(id);
                });
                log('listening', id);
                """, Map.of(), limits(200_000, 5), host);
        ScriptRun.Result first = run.run();
        assertTrue(first.ok(), first.error());
        assertEquals("listening 1", first.log());
        assertEquals(1, first.listeners());
        assertTrue(run.wants("chat"));
        assertFalse(run.wants("hit"));
        assertEquals("chat", run.listening());

        run.offer("chat", Map.of("player", "Alex", "text", "not for you"));
        run.offer("chat", Map.of("player", "Steve", "text", "one"));
        run.offer("hit", Map.of("player", "Steve", "damage", 2));
        run.offer("chat", Map.of("player", "Steve", "text", "two"));
        run.offer("chat", Map.of("player", "Steve", "text", "three"));
        ScriptRun.Result after = assertTimeoutPreemptively(Duration.ofSeconds(10), run::serve);
        assertTrue(after.ok(), after.error());
        assertEquals("Steve said one\nSteve said two", after.log());
        assertEquals(2, host.named("players.tell").size());
        assertEquals(0, run.listeners());
    }

    @Test
    void enterFiresOnceEachTimeAMortalComesIn() {
        ScriptRun run = new ScriptRun("""
                var n = 0;
                on('enter', {x: 0, y: 64, z: 0, r: 5}, function(e) { n++; log(e.player + ' entered #' + n); if (n == 2) off(1); });
                try { on('enter', function(e) {}); } catch (e) { log(e.message); }
                try { on('sneeze', function(e) {}); } catch (e) { log(e.message); }
                """, Map.of(), limits(200_000, 5), new FakeHost());
        ScriptRun.Result first = run.run();
        assertTrue(first.ok(), first.error());
        assertTrue(first.log().contains("'enter' needs a place"), first.log());
        assertTrue(first.log().contains("there is no event 'sneeze'"), first.log());
        assertTrue(run.wants("move"));
        run.offer("move", Map.of("player", "Steve", "x", 30.0, "y", 64.0, "z", 0.0)); // outside
        run.offer("move", Map.of("player", "Steve", "x", 3.0, "y", 64.0, "z", 0.0)); // in
        run.offer("move", Map.of("player", "Steve", "x", 2.0, "y", 64.0, "z", 1.0)); // still in: no second call
        run.offer("move", Map.of("player", "Steve", "x", 9.0, "y", 64.0, "z", 0.0)); // out
        run.offer("move", Map.of("player", "Steve", "x", 1.0, "y", 65.0, "z", 0.0)); // in again
        ScriptRun.Result after = assertTimeoutPreemptively(Duration.ofSeconds(10), run::serve);
        assertEquals("Steve entered #1\nSteve entered #2", after.log());
    }

    @Test
    void handlersEndWhenTheirTimeIsUpAndShareTheBudget() {
        // Two seconds of life (see limits()): every() fires a few times, then serve() returns by itself.
        FakeHost host = new FakeHost();
        ScriptRun run = new ScriptRun("var n = 0; every(4, function() { n++; world.particle('flame', n, 64, 0); });", Map.of(),
                limits(200_000, 5), host);
        assertTrue(run.run().ok());
        long t0 = System.currentTimeMillis();
        ScriptRun.Result after = assertTimeoutPreemptively(Duration.ofSeconds(10), run::serve);
        long took = System.currentTimeMillis() - t0;
        assertTrue(after.ok(), after.error());
        assertTrue(took >= 1500 && took < 5000, "took " + took);
        int fired = host.named("world.particle").size();
        assertTrue(fired >= 5 && fired <= 11, "fired " + fired);

        // A handler that loops for ever ends the whole script, and says where.
        ScriptRun loop = new ScriptRun("on('hit', function(e) {\n  while (true) {}\n});", Map.of(), limits(200_000, 5), new FakeHost());
        assertTrue(loop.run().ok());
        loop.offer("hit", Map.of("player", "Steve"));
        ScriptRun.Result stopped = assertTimeoutPreemptively(Duration.ofSeconds(10), loop::serve);
        assertFalse(stopped.ok());
        assertTrue(stopped.error().startsWith("Stopped at line 2"), stopped.error());
        assertEquals(0, loop.listeners());

        // So does one whose calls run past the event budget.
        ScriptLimits d = ScriptLimits.DEFAULT;
        ScriptRun many = new ScriptRun("on('hit', function(e) { log('hit'); });", Map.of(),
                new ScriptLimits(d.instructions(), 5, 600, 400, 24, 60, 2000, 12, 4, 2, 3, 64), new FakeHost());
        assertTrue(many.run().ok());
        for (int i = 0; i < 6; i++) many.offer("hit", Map.of("player", "Steve"));
        ScriptRun.Result capped = assertTimeoutPreemptively(Duration.ofSeconds(10), many::serve);
        assertFalse(capped.ok());
        assertEquals("hit\nhit\nhit", capped.log());
        assertTrue(capped.error().contains("called more than 3 times"), capped.error());
    }

    @Test
    void aStopFromOutsideEndsAWaitingScript() throws Exception {
        ScriptHost slow = new FakeHost() {
            @Override
            public void sleep(int ticks) throws InterruptedException {
                Thread.sleep(60_000);
            }
        };
        ScriptRun run = new ScriptRun("log('before');\nwait(100);\nlog('after');", Map.of(), limits(200_000, 5), slow);
        ScriptRun.Result[] out = new ScriptRun.Result[1];
        Thread t = new Thread(() -> out[0] = run.run());
        t.start();
        Thread.sleep(300);
        run.stop("the keepers stopped it");
        t.join(5000);
        assertFalse(t.isAlive());
        assertFalse(out[0].ok());
        assertEquals("before", out[0].log());
        assertTrue(out[0].error().contains("the keepers stopped it"), out[0].error());
        assertNull(out[0].error().contains("stays done") ? "a stop from outside flushes nothing" : null);
    }

    @Test
    void theHelpNamesEverythingAScriptCanCall() {
        for (ScriptApi.Fn f : ScriptApi.ALL) {
            String bare = f.name();
            assertTrue(ScriptApi.HELP.contains(bare + "("), bare + " is not taught");
            assertNotNull(ScriptApi.find(bare));
        }
        for (String e : ScriptApi.EVENTS) assertTrue(ScriptApi.HELP.contains("'" + e + "'"), e);
        // The example in the help runs as written.
        String example = ScriptApi.HELP.substring(ScriptApi.HELP.indexOf("Example:\n") + "Example:\n".length());
        FakeHost host = new FakeHost();
        ScriptRun.Result r = run(example, host);
        assertTrue(r.ok(), r.error());
        assertEquals(30, host.named("world.place").stream().mapToInt(c -> c.args().size()).sum());
        assertEquals(1, host.named("world.lightning").size());
        assertEquals(24, host.slept);
    }
}
