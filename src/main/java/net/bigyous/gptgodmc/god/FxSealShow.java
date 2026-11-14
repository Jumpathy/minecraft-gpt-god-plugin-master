package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * Shows the {@code seal} layers of a scene as flat surfaces in the world. Every stroke is a text display holding
 * one space: its background is a flat, full-bright quad of any colour, which the transformation stretches along
 * the stroke; letters and symbols are text displays lying in the same plane. Nothing is a particle and nothing
 * needs the client mod or the resource pack. Pieces glide between poses sent every {@link #STEP} ticks, and only
 * those that moved are sent again. Entities are non-persistent. Server thread only.
 */
public final class FxSealShow {
    /** Ticks between poses; the client glides from one to the next. */
    public static final int STEP = 4;
    /** Display entities all running seals may use together. */
    public static final int MAX_ALIVE = 1800;
    // The background of a text display holding one space, unscaled: 5 x 10 text pixels of 1/40 block,
    // from x = -2 to 3 pixels and y = 0 to 10 (DisplayRenderer.TextDisplayRenderer).
    static final float QUAD_W = 0.125f, QUAD_H = 0.25f, PIVOT_X = 0.0125f, PIVOT_Y = 0.125f;
    /** A letter is about 8 text pixels tall. */
    static final float LETTER = 0.2f;
    private static final String TAG = "seal";
    private static int alive;

    private final List<Layer> layers = new ArrayList<>();
    private final Location at;
    private final Player follow;
    private final Player only;
    private int age = -2;

    private static final class Layer {
        FxSeal spec;
        boolean born, gone;
        Vector3f u, v, n;
        Quaternionf front, back;
        BlockDisplay root;
        float sink;
        final List<TextDisplay> all = new ArrayList<>();
        final List<TextDisplay> lines = new ArrayList<>(), lineBacks = new ArrayList<>();
        final List<TextDisplay> glyphs = new ArrayList<>(), glyphBacks = new ArrayList<>();
        final List<TextDisplay> motes = new ArrayList<>();
        final java.util.Map<TextDisplay, float[]> sent = new java.util.IdentityHashMap<>();
    }

    private FxSealShow(Location at, Player follow, Player only) {
        this.at = at;
        this.follow = follow;
        this.only = only;
    }

    public static int alive() {
        return alive;
    }

    /** Why these seals cannot be shown now (words for the god), or null. */
    public static String refusal(JsonArray scene, double scale) {
        int want = 0;
        for (JsonElement e : scene) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("type") && "seal".equals(o.get("type").getAsString())) want += FxSeal.parse(o, scale).pieces();
        }
        if (want == 0 || alive + want <= MAX_ALIVE) return null;
        return "Too many seals are already drawn in the world for this one (" + want + " strokes; " + (MAX_ALIVE - alive)
                + " left). Wait for some to fade, or stop one with fx_stop.";
    }

    /** The seals of a scene, or null when it has none. */
    public static FxSealShow of(JsonArray scene, Location at, Entity follow, double scale, Player only, boolean loop) {
        FxSealShow show = new FxSealShow(new Location(at.getWorld(), at.getX(), at.getY(), at.getZ()),
                follow instanceof Player p ? p : null, only);
        for (JsonElement e : scene) {
            JsonObject o = e.getAsJsonObject();
            if (!o.has("type") || !"seal".equals(o.get("type").getAsString())) continue;
            FxSeal spec = FxSeal.parse(o, scale);
            if (spec.parts.isEmpty()) continue;
            spec.endless = loop;
            Layer l = new Layer();
            l.spec = spec;
            show.layers.add(l);
        }
        return show.layers.isEmpty() ? null : show;
    }

    /** Call every 2 ticks while the scene runs. */
    public void tick() {
        age += 2;
        for (Layer l : layers) {
            if (l.gone) continue;
            int local = age - l.spec.delay;
            if (local < 0) continue;
            if (!l.born) {
                l.born = true;
                if (alive + l.spec.pieces() > MAX_ALIVE) {
                    l.gone = true;
                    continue;
                }
                spawn(l);
                pose(l, l.spec.frame(local), 0);
            }
            if (!l.spec.endless && local >= l.spec.duration) {
                remove(l);
                continue;
            }
            if (follow != null && l.root != null && l.root.isValid() && l.root.getVehicle() == null && follow.isOnline()
                    && !follow.isDead()) {
                follow.addPassenger(l.root);
            }
            if ((local & ~1) % STEP == 0) pose(l, l.spec.frame(local + STEP), STEP);
        }
    }

    public void end() {
        for (Layer l : layers) remove(l);
    }

    private void remove(Layer l) {
        if (l.gone) return;
        l.gone = true;
        alive -= l.all.size();
        for (TextDisplay d : l.all) d.remove();
        if (l.root != null) l.root.remove();
        l.all.clear();
        l.sent.clear();
    }

    private Location centre() {
        if (follow != null && follow.isOnline() && follow.getWorld().equals(at.getWorld())) {
            Location f = follow.getLocation();
            return new Location(f.getWorld(), f.getX(), f.getY(), f.getZ());
        }
        return at.clone();
    }

    // ---- Making the pieces ------------------------------------------------------------------------------

    private void spawn(Layer l) {
        FxSeal s = l.spec;
        Location c = centre();
        World w = c.getWorld();
        double yaw = s.yaw;
        if (s.facing) yaw = facingYaw(c);
        float[][] basis = basis(s.tilt, yaw);
        l.u = new Vector3f(basis[0]);
        l.v = new Vector3f(basis[1]);
        l.n = new Vector3f(basis[2]);
        l.front = orientation(l.u, l.v, l.n);
        l.back = orientation(new Vector3f(l.u).negate(), l.v, new Vector3f(l.n).negate());
        if (follow != null) {
            l.root = w.spawn(c, BlockDisplay.class, b -> {
                setup(b);
                b.setTeleportDuration(2);
            });
            // Riders sit at the top of the mortal they ride; the seal is drawn from their feet.
            l.sink = follow.addPassenger(l.root) ? (float) follow.getHeight() : 0;
        }
        FxSeal.Frame f = s.frame(0);
        boolean two = s.twoFaced();
        for (FxSeal.Line line : f.lines()) {
            l.lines.add(quad(l, c, line.argb()));
            l.lineBacks.add(two && !line.halo() ? quad(l, c, line.argb()) : null);
        }
        for (FxSeal.Glyph g : f.glyphs()) {
            l.glyphs.add(letter(l, c, g.text(), g.font(), g.argb(), false));
            if (two) l.glyphBacks.add(letter(l, c, g.text(), g.font(), g.argb(), false));
        }
        for (FxSeal.Mote m : f.motes()) {
            TextDisplay d = letter(l, c, m.text(), m.font(), m.argb(), true);
            l.motes.add(d);
        }
        alive += l.all.size();
    }

    private TextDisplay quad(Layer l, Location c, int argb) {
        TextDisplay d = c.getWorld().spawn(c, TextDisplay.class, t -> {
            setup(t);
            t.text(Component.text(" "));
            t.setBackgroundColor(Color.fromARGB(argb));
            t.setTransformation(hidden());
        });
        keep(l, d, true);
        return d;
    }

    private TextDisplay letter(Layer l, Location c, String text, String font, int argb, boolean mote) {
        Key key = font.equals("runes") ? Key.key("minecraft", "alt") : font.equals("illager") ? Key.key("minecraft", "illageralt")
                : Key.key("minecraft", "default");
        TextDisplay d = c.getWorld().spawn(c, TextDisplay.class, t -> {
            setup(t);
            t.text(Component.text(text).font(key).color(TextColor.color(argb & 0xFFFFFF)));
            t.setBackgroundColor(Color.fromARGB(0));
            t.setTextOpacity((byte) (argb >>> 24));
            t.setTransformation(hidden());
            if (mote) {
                t.setBillboard(Display.Billboard.CENTER);
                t.setTeleportDuration(STEP);
            }
        });
        keep(l, d, !mote);
        return d;
    }

    private void keep(Layer l, TextDisplay d, boolean rides) {
        l.all.add(d);
        if (only != null) only.showEntity(GPTGOD.get(), d);
        if (rides && l.root != null) l.root.addPassenger(d);
    }

    private void setup(Display d) {
        d.getPersistentDataContainer().set(new NamespacedKey(GPTGOD.get(), TAG), PersistentDataType.BYTE, (byte) 1);
        d.setPersistent(false);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setShadowRadius(0);
        d.setShadowStrength(0);
        d.setViewRange(2.5f);
        if (only != null) d.setVisibleByDefault(false);
        if (d instanceof TextDisplay t) {
            t.setShadowed(false);
            t.setSeeThrough(false);
            t.setDefaultBackground(false);
            t.setLineWidth(4000);
        }
    }

    private double facingYaw(Location c) {
        Player nearest = null;
        double best = 128 * 128;
        for (Player p : c.getWorld().getPlayers()) {
            if (p.equals(follow)) continue;
            double d = p.getLocation().distanceSquared(c);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) return follow != null ? follow.getLocation().getYaw() : 0;
        Location p = nearest.getLocation();
        return Math.toDegrees(Math.atan2(-(p.getX() - c.getX()), p.getZ() - c.getZ()));
    }

    // ---- Posing them ------------------------------------------------------------------------------------

    private void pose(Layer l, FxSeal.Frame f, int glide) {
        FxSeal s = l.spec;
        // A seal on the ground floats a hair above it, so it is never lost in the floor.
        float ox = (float) s.offset[0], oy = (float) s.offset[1] - l.sink + (s.tilt < 20 ? 0.04f : 0), oz = (float) s.offset[2];
        for (int i = 0; i < f.lines().size() && i < l.lines.size(); i++) {
            FxSeal.Line line = f.lines().get(i);
            send(l, l.lines.get(i), stroke(l.u, l.v, l.n, l.front, ox, oy, oz, line, false), glide);
            if (l.lineBacks.get(i) != null) send(l, l.lineBacks.get(i), stroke(l.u, l.v, l.n, l.back, ox, oy, oz, line, true), glide);
        }
        for (int i = 0; i < f.glyphs().size() && i < l.glyphs.size(); i++) {
            FxSeal.Glyph g = f.glyphs().get(i);
            send(l, l.glyphs.get(i), letter(l.u, l.v, l.n, l.front, ox, oy, oz, g, false), glide);
            opacity(l.glyphs.get(i), g.argb());
            if (i < l.glyphBacks.size()) {
                send(l, l.glyphBacks.get(i), letter(l.u, l.v, l.n, l.back, ox, oy, oz, g, true), glide);
                opacity(l.glyphBacks.get(i), g.argb());
            }
        }
        if (l.motes.isEmpty()) return;
        Location c = centre();
        for (int i = 0; i < f.motes().size() && i < l.motes.size(); i++) {
            FxSeal.Mote m = f.motes().get(i);
            TextDisplay d = l.motes.get(i);
            if (!d.isValid()) continue;
            float k = (float) m.size() / LETTER;
            send(l, d, new Transformation(new Vector3f(-PIVOT_X * k, -PIVOT_Y * k, 0), new Quaternionf(), new Vector3f(k, k, k),
                    new Quaternionf()), glide);
            opacity(d, m.argb());
            double x = s.offset[0] + l.u.x * m.x() + l.v.x * m.y() + l.n.x * m.h();
            double y = s.offset[1] + l.u.y * m.x() + l.v.y * m.y() + l.n.y * m.h() + 0.1;
            double z = s.offset[2] + l.u.z * m.x() + l.v.z * m.y() + l.n.z * m.h();
            d.teleport(new Location(c.getWorld(), c.getX() + x, c.getY() + y, c.getZ() + z));
        }
    }

    private static void opacity(TextDisplay d, int argb) {
        byte want = (byte) Math.max(26, argb >>> 24);
        if (d.getTextOpacity() != want) d.setTextOpacity(want);
    }

    private static void send(Layer l, TextDisplay d, Transformation t, int glide) {
        float[] now = { t.getTranslation().x, t.getTranslation().y, t.getTranslation().z, t.getLeftRotation().x, t.getLeftRotation().y,
                t.getLeftRotation().z, t.getLeftRotation().w, t.getScale().x, t.getScale().y, t.getScale().z };
        float[] was = l.sent.get(d);
        if (was != null) {
            boolean same = true;
            for (int i = 0; i < now.length && same; i++) same = Math.abs(now[i] - was[i]) < 1e-4f;
            if (same) return;
        }
        l.sent.put(d, now);
        if (!d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(glide);
        d.setTransformation(t);
    }

    // ---- The mathematics (pure) -------------------------------------------------------------------------

    /**
     * The plane's axes in the world for a tilt (0 = lying on the ground, 90 = standing) and a Minecraft yaw
     * (the way a standing seal faces): right, up-the-plane, and the normal its front shows to.
     */
    static float[][] basis(double tilt, double yaw) {
        double psi = Math.toRadians(yaw), a = Math.toRadians(90 - Math.max(0, Math.min(90, tilt)));
        double fx = -Math.sin(psi), fz = Math.cos(psi);
        // right = up x facing
        double ux = fz, uz = -fx;
        double cos = Math.cos(a), sin = Math.sin(a);
        return new float[][] { { (float) ux, 0, (float) uz }, { (float) (-fx * sin), (float) cos, (float) (-fz * sin) },
                { (float) (fx * cos), (float) sin, (float) (fz * cos) } };
    }

    static Quaternionf orientation(Vector3f u, Vector3f v, Vector3f n) {
        return new Quaternionf().setFromNormalized(new Matrix3f(u.x, u.y, u.z, v.x, v.y, v.z, n.x, n.y, n.z));
    }

    private static Transformation hidden() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0, 0, 0), new Quaternionf());
    }

    /** Where a stroke's quad goes: stretched from a to b (with square caps), lying in the plane. */
    static Transformation stroke(Vector3f u, Vector3f v, Vector3f n, Quaternionf face, float ox, float oy, float oz, FxSeal.Line line,
            boolean back) {
        double dx = line.bx() - line.ax(), dy = line.by() - line.ay();
        double length = Math.hypot(dx, dy), width = line.width();
        if (width < 1e-4 || length + width < 1e-4) {
            float cx = (float) ((line.ax() + line.bx()) / 2), cy = (float) ((line.ay() + line.by()) / 2);
            return new Transformation(at(u, v, n, ox, oy, oz, cx, cy, (float) line.lift()), new Quaternionf(face), new Vector3f(0, 0, 0),
                    new Quaternionf());
        }
        // A stroke has square caps so corners close; a halo is see-through, and overlapping caps would show as blots.
        double cap = line.halo() ? 0 : width;
        double angle = Math.atan2(dy, dx);
        // The back face looks at the same strokes from behind: mirrored left to right.
        Quaternionf q = new Quaternionf(face).rotateZ((float) (back ? Math.PI - angle : angle));
        float sx = (float) ((length + cap) / QUAD_W), sy = (float) (width / QUAD_H);
        Vector3f c = at(u, v, n, ox, oy, oz, (float) ((line.ax() + line.bx()) / 2), (float) ((line.ay() + line.by()) / 2),
                (float) line.lift() * (back ? -1 : 1));
        c.sub(q.transform(new Vector3f(PIVOT_X * sx, PIVOT_Y * sy, 0)));
        return new Transformation(c, q, new Vector3f(sx, sy, 1), new Quaternionf());
    }

    static Transformation letter(Vector3f u, Vector3f v, Vector3f n, Quaternionf face, float ox, float oy, float oz, FxSeal.Glyph g,
            boolean back) {
        float k = (float) g.size() / LETTER;
        Quaternionf q = new Quaternionf(face).rotateZ((float) (back ? -g.angle() : g.angle()));
        Vector3f c = at(u, v, n, ox, oy, oz, (float) g.x(), (float) g.y(), back ? -0.07f : 0.07f);
        c.sub(q.transform(new Vector3f(PIVOT_X * k, PIVOT_Y * k, 0)));
        return new Transformation(c, q, new Vector3f(k, k, k), new Quaternionf());
    }

    private static Vector3f at(Vector3f u, Vector3f v, Vector3f n, float ox, float oy, float oz, float x, float y, float lift) {
        return new Vector3f(ox + u.x * x + v.x * y + n.x * lift, oy + u.y * x + v.y * y + n.y * lift, oz + u.z * x + v.z * y + n.z * lift);
    }
}
