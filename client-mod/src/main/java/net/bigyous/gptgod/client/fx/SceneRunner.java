package net.bigyous.gptgod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.bigyous.gptgod.client.GodClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Plays scenes: every client tick it advances each running scene's timeline and lets its layers act
 * (spawn particles, start screen effects, tint the sky, play sounds). Client thread only. A shared
 * budget keeps any number of scenes from flooding the particle engine.
 */
public final class SceneRunner {
    public static final int MAX_SCENES = 24;
    /** Particles all scenes together may spawn in one tick. */
    public static final int TICK_BUDGET = 900;

    private static final class Running {
        final Scene scene;
        final Random rnd;
        int tick;
        double x, y, z;
        /** Where its formula layers (fields, solids) stand; marked gone when the scene is stopped. */
        final FieldParticle.Anchor anchor = new FieldParticle.Anchor();
        /** How many particles of each field layer have been made so far. */
        final java.util.Map<Scene.Layer, Integer> made = new java.util.IdentityHashMap<>();
        /** This player's watch on each of its triggers. */
        final java.util.Map<Scene.Layer, FxTrigger.Watch> watches = new java.util.IdentityHashMap<>();
        final long seed;

        Running(Scene s, double x, double y, double z) {
            scene = s;
            rnd = new Random(s.seed() == 0 ? System.nanoTime() : s.seed());
            seed = rnd.nextInt(1 << 30);
            this.x = anchor.x = x;
            this.y = anchor.y = y;
            this.z = anchor.z = z;
        }
    }

    private final List<Running> running = new ArrayList<>();
    private final SolidFx solids = new SolidFx();
    private final ReskinFx reskins = new ReskinFx();
    private float skyR, skyG, skyB, skyStrength;
    private final double[] vars = new double[FxExpr.VARS];
    /** Scenes that triggers set off this tick, started once the running ones have been advanced. */
    private final List<Scene> fired = new ArrayList<>();

    public int count() {
        return running.size();
    }

    /** Starts a scene. One with the id of a running scene replaces it. Returns false if it has no place. */
    public boolean play(Scene s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        double[] at = s.at();
        Entity anchor = s.follow() >= 0 ? mc.level.getEntity(s.follow()) : null;
        if (anchor != null) at = new double[] { anchor.getX(), anchor.getY(), anchor.getZ() };
        if (at == null) return false;
        if (!s.id().isEmpty()) stop(s.id());
        if (running.size() >= MAX_SCENES) running.remove(0).anchor.gone = true;
        running.add(new Running(s, at[0], at[1], at[2]));
        return true;
    }

    public void stop(String id) {
        running.removeIf(r -> {
            if (!r.scene.id().equals(id)) return false;
            r.anchor.gone = true;
            return true;
        });
    }

    public void clear() {
        running.forEach(r -> r.anchor.gone = true);
        running.clear();
        solids.clear();
        reskins.clear();
        skyStrength = 0;
    }

    /** The scenes' sky tint right now (0..1), for the sky layers. */
    public float skyStrength() {
        return skyStrength;
    }

    public float[] skyColor() {
        return new float[] { skyR, skyG, skyB };
    }

    public void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        Viewer.update(mc);
        solids.tick();
        reskins.tick(level);
        if (level == null || running.isEmpty()) {
            running.forEach(r -> r.anchor.gone = true);
            running.clear();
            skyStrength *= 0.9f;
            if (skyStrength < 0.01f) skyStrength = 0;
            return;
        }
        int budget = (int) (TICK_BUDGET * GodClientMod.config.particles);
        float sr = 0, sg = 0, sb = 0, ss = 0;
        long now = System.currentTimeMillis();
        Iterator<Running> it = running.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            Scene s = r.scene;
            if (s.follow() >= 0) {
                Entity e = level.getEntity(s.follow());
                if (e != null) {
                    r.x = e.getX();
                    r.y = e.getY();
                    r.z = e.getZ();
                }
            }
            r.anchor.x = r.x;
            r.anchor.y = r.y;
            r.anchor.z = r.z;
            for (Scene.Layer layer : s.layers()) {
                int t = r.tick - layer.delay();
                if (t < 0) continue;
                switch (layer) {
                    case Scene.Field f -> {
                        if (t > f.duration()) continue;
                        // A field's particles are made once (over a few ticks when the budget is short) and then
                        // live as long as the layer; fewer of them when the viewer turned particles down.
                        int count = Math.max(1, (int) (f.spec().count * Math.min(1, GodClientMod.config.particles)));
                        int made = r.made.getOrDefault(layer, 0);
                        int n = Math.min(budget, count - made);
                        if (n <= 0) continue;
                        budget -= n;
                        for (int i = made; i < made + n; i++) {
                            mc.particleEngine.add(new FieldParticle(level, f.spec(), r.anchor, i, count, r.seed, s.scale(), t));
                        }
                        r.made.put(layer, made + n);
                    }
                    case Scene.Trigger g -> {
                        if (t > g.duration()) continue;
                        FxTrigger spec = g.spec();
                        Viewer.fill(vars, r.x + spec.offset[0] * s.scale(), r.y + spec.offset[1] * s.scale(),
                                r.z + spec.offset[2] * s.scale(), s.scale());
                        if (!r.watches.computeIfAbsent(layer, k -> new FxTrigger.Watch()).tick(spec, t, 1, vars)) continue;
                        if (!spec.then.isEmpty()) {
                            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                            com.google.gson.JsonArray at = new com.google.gson.JsonArray();
                            at.add(r.x);
                            at.add(r.y);
                            at.add(r.z);
                            o.add("at", at);
                            o.addProperty("scale", s.scale());
                            o.add("layers", spec.then);
                            fired.add(Scene.decode(o));
                        }
                        GodClientMod.triggered(spec.key);
                    }
                    case Scene.Reskin reskin -> {
                        if (t == 0) reskins.add(reskin.spec(), r.anchor, s.scale());
                    }
                    case Scene.Solid solid -> {
                        if (t == 0) solids.add(solid.spec(), r.anchor, s.scale(), r.seed);
                    }
                    case Scene.Particles p -> {
                        if (t > p.duration()) continue;
                        int n = Math.min(budget, Emitters.due(p, t, r.rnd));
                        budget -= n;
                        for (int i = 0; i < n; i++) {
                            Emitters.Spawn sp = Emitters.spawn(p, i, n, t, s.scale(), r.rnd);
                            mc.particleEngine.add(new GlowParticle(level, r.x + sp.x(), r.y + sp.y(), r.z + sp.z(), sp, p,
                                    s.scale(), r.x + p.offset()[0] * s.scale(), r.z + p.offset()[2] * s.scale()));
                        }
                    }
                    case Scene.Mesh m -> {
                        if (t == 0) GodClientMod.WORLD.addMesh(m, r.x, r.y, r.z, s.scale(), s.follow(), r.rnd.nextLong());
                    }
                    case Scene.Screen sc -> {
                        if (t != 0) continue;
                        ScreenFx.Kind kind = ScreenFx.Kind.of(sc.kind());
                        if (kind == null || mc.player == null) continue;
                        double d = Math.sqrt(mc.player.distanceToSqr(r.x, r.y, r.z));
                        float strength = sc.intensity() * (float) ScreenFx.falloff(d, 64 * Math.max(1, s.scale()));
                        GodClientMod.SCREEN.add(kind, strength, sc.duration() * 50L, sc.color(), now);
                    }
                    case Scene.Post post -> {
                        if (t != 0) continue;
                        double[] at = { r.x + post.offset()[0] * s.scale(), r.y + post.offset()[1] * s.scale() + 1,
                                r.z + post.offset()[2] * s.scale() };
                        double d = mc.player == null ? 0 : Math.sqrt(mc.player.distanceToSqr(r.x, r.y, r.z));
                        float strength = post.intensity() * (float) ScreenFx.falloff(d, 96 * Math.max(1, s.scale()));
                        GodClientMod.POST.add(PostFx.Kind.of(post.kind()), strength, post.duration() * 50L, post.color(), at, now);
                    }
                    case Scene.Camera cam -> {
                        if (t != 0 || mc.player == null) continue;
                        double d = Math.sqrt(mc.player.distanceToSqr(r.x, r.y, r.z));
                        float amount = cam.amount() * (float) ScreenFx.falloff(d, 64 * Math.max(1, s.scale()));
                        double[] at = { r.x + cam.offset()[0] * s.scale(), r.y + cam.offset()[1] * s.scale() + 1.5,
                                r.z + cam.offset()[2] * s.scale() };
                        GodClientMod.CAMERA.add(CameraFx.Kind.of(cam.kind()), amount, cam.duration() * 50L, at, now);
                    }
                    case Scene.Sky sky -> {
                        if (t > sky.duration()) continue;
                        float u = t / (float) sky.duration();
                        float env = Math.max(0, Math.min(1, Math.min(u / 0.15f, (1 - u) / 0.25f))) * sky.strength();
                        if (env > 0) {
                            sr += ((sky.color() >> 16) & 255) / 255f * env;
                            sg += ((sky.color() >> 8) & 255) / 255f * env;
                            sb += (sky.color() & 255) / 255f * env;
                            ss += env;
                        }
                    }
                    case Scene.Sound snd -> {
                        if (t != 0) continue;
                        Identifier id = Identifier.tryParse(snd.key());
                        if (SoundBank.has(snd.key())) {
                            SynthSounds.play(snd.key(), r.x, r.y, r.z, snd.volume(), snd.pitch());
                        } else if (id != null) {
                            level.playLocalSound(r.x, r.y, r.z, SoundEvent.createVariableRangeEvent(id), SoundSource.AMBIENT,
                                    snd.volume(), snd.pitch(), true);
                        }
                    }
                    case Scene.Tone tone -> {
                        if (t == 0) SynthSounds.play(SynthSounds.render(tone.patch()), r.x, r.y, r.z, tone.volume(), tone.pitch());
                    }
                    case Scene.Debris d -> {
                        if (t == 0) GodClientMod.WORLD.launch(level, d, r.x, r.y, r.z, s.scale(), r.rnd);
                    }
                    case Scene.Glyph g -> {
                        if (t == 0) GodClientMod.WORLD.addGlyph(g, r.x, r.y, r.z, s.scale());
                    }
                }
            }
            r.tick++;
            if (r.tick > s.life()) {
                if (s.loop() && !s.id().isEmpty()) {
                    r.tick = 0;
                    r.made.clear();
                } else {
                    it.remove();
                }
            }
        }
        if (!fired.isEmpty()) {
            List<Scene> start = new ArrayList<>(fired);
            fired.clear();
            start.forEach(this::play);
        }
        if (ss > 0) {
            skyR = sr / ss;
            skyG = sg / ss;
            skyB = sb / ss;
        }
        skyStrength = Math.min(1, ss) * (float) GodClientMod.config.sky;
    }
}
