package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.DeathPieces.Kind;
import net.bigyous.gptgodmc.god.DeathPieces.Piece;
import net.bigyous.gptgodmc.god.DeathPieces.Pose;
import net.bigyous.gptgodmc.god.vessel.VesselPackHost;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * Plays the solid pieces of one death ({@link DeathPieces}) as display entities: spawns each on its
 * first frame, moves it every frame with a two-tick interpolation, and removes it after its last.
 * Textured pieces exist twice, the pack's version shown to players with the pack and a block or text
 * fallback to the rest (like the vessel's body). Entities are not saved with the world. Server thread.
 */
final class DeathStage {
    /** One piece in the world: what everyone (or pack holders) sees, and the fallback. */
    private static final class Live {
        final Piece piece;
        Display main;
        Display fallback;
        Pose last;

        Live(Piece piece) {
            this.piece = piece;
        }
    }

    private final Location ground;
    private final List<Live> live = new ArrayList<>();
    private final boolean skins;

    private static final Quaternionf ITEM_FLIP = new Quaternionf().rotationY((float) Math.PI);

    DeathStage(DeathShow.Variant variant, Location ground, double h) {
        this.ground = ground.clone();
        this.skins = Godslayer.packHas("assets/gptgodmc/items/deathshow/corona.json");
        World w = ground.getWorld();
        int bx = ground.getBlockX(), bz = ground.getBlockZ();
        double gy = ground.getY();
        DeathPieces.Ground real = (dx, dz) -> {
            Block b = w.getHighestBlockAt(bx + dx, bz + dz);
            Material m = b.getType();
            if (!m.isSolid() || !m.isOccluding() || Math.abs(b.getY() + 1 - gy) > 6) return null;
            return new DeathPieces.Tile(b.getBlockData().getAsString(), b.getY() + 1 - gy);
        };
        for (Piece p : DeathPieces.build(variant, h, ground.getX() - bx, ground.getZ() - bz, real)) {
            live.add(new Live(p));
        }
    }

    /** Moves every piece to frame f (spawning and removing as their lives begin and end). */
    void frame(int f) {
        for (Live l : live) {
            Piece p = l.piece;
            if (!p.alive(f)) {
                if (l.main != null || l.fallback != null) remove(l);
                continue;
            }
            Pose pose = p.at(f);
            if (l.main == null && l.fallback == null) {
                spawn(l, pose);
                l.last = pose;
                continue;
            }
            if (pose.equals(l.last)) continue;
            l.last = pose;
            move(l.main, p, pose, false);
            move(l.fallback, p, pose, true);
        }
    }

    /** Removes everything that is left. */
    void clear() {
        for (Live l : live) remove(l);
    }

    private static void remove(Live l) {
        if (l.main != null) l.main.remove();
        if (l.fallback != null) l.fallback.remove();
        l.main = null;
        l.fallback = null;
    }

    private void spawn(Live l, Pose pose) {
        Piece p = l.piece;
        switch (p.kind()) {
            case BLOCK -> l.main = block(p, p.look(), pose);
            case TEXT -> l.main = text(p, p.look(), pose);
            case SKIN -> {
                if (skins) l.main = item(p, pose);
                String fb = p.fallback();
                if (fb != null) {
                    if (fb.startsWith("text:")) l.fallback = text(p, fb.substring(5), pose);
                    else if (fb.startsWith("glow:")) l.fallback = glowColumn(p, fb.substring(5), pose);
                    else l.fallback = block(p, fb, pose);
                }
                if (l.main != null) l.main.setVisibleByDefault(false);
                if (l.fallback != null) l.fallback.setVisibleByDefault(false);
                for (Player viewer : ground.getWorld().getPlayers()) {
                    boolean has = skins && VesselPackHost.has(viewer);
                    if (has && l.main != null) viewer.showEntity(GPTGOD.get(), l.main);
                    if (!has && l.fallback != null) viewer.showEntity(GPTGOD.get(), l.fallback);
                }
            }
        }
    }

    private void dress(Display d, Piece p) {
        d.setPersistent(false);
        d.setViewRange(4f);
        d.setTeleportDuration(0);
        if (p.bright()) d.setBrightness(new Display.Brightness(15, 15));
        if (p.glow()) {
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(p.rgb()));
        }
        if (p.billboard()) d.setBillboard(Display.Billboard.CENTER);
    }

    private BlockDisplay block(Piece p, String look, Pose pose) {
        BlockData data;
        try {
            data = Bukkit.createBlockData(look);
        } catch (IllegalArgumentException e) {
            data = Material.GLASS.createBlockData();
        }
        BlockData chosen = data;
        return ground.getWorld().spawn(ground, BlockDisplay.class, d -> {
            dress(d, p);
            d.setBlock(chosen);
            d.setTransformation(transform(pose, Kind.BLOCK));
        });
    }

    private ItemDisplay item(Piece p, Pose pose) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.editMeta(m -> m.setItemModel(NamespacedKey.fromString(DeathArt.modelId(p.look()))));
        return ground.getWorld().spawn(ground, ItemDisplay.class, d -> {
            dress(d, p);
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(transform(pose, Kind.SKIN));
        });
    }

    private TextDisplay text(Piece p, String text, Pose pose) {
        return ground.getWorld().spawn(ground, TextDisplay.class, d -> {
            dress(d, p);
            d.text(Component.text(text, TextColor.color(p.rgb())));
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setTransformation(transform(pose, Kind.TEXT));
        });
    }

    /**
     * A column of soft colored light without the pack: the translucent background of an empty text
     * display, turned to face each viewer. No texture needed, and no outline.
     */
    private TextDisplay glowColumn(Piece p, String hex, Pose pose) {
        int rgb = Integer.parseInt(hex, 16);
        return ground.getWorld().spawn(ground, TextDisplay.class, d -> {
            dress(d, p);
            d.text(Component.text(" "));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBackgroundColor(Color.fromARGB(110, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255));
            d.setShadowed(false);
            d.setTransformation(columnTransform(pose));
        });
    }

    /** An empty text display's background is about 0.15 wide and 0.275 tall, sitting on its origin. */
    static Transformation columnTransform(Pose p) {
        float w = (float) Math.max(1e-4, p.hidden() ? 1e-4 : p.sx() * 0.5);
        float h = (float) Math.max(1e-4, p.hidden() ? 1e-4 : p.sy());
        return new Transformation(new Vector3f((float) p.x(), (float) (p.y() - h / 2), (float) p.z()),
                new Quaternionf(), new Vector3f(w / 0.15f, h / 0.275f, 1), new Quaternionf());
    }

    private static void move(Display d, Piece p, Pose pose, boolean fallback) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(2);
        if (fallback && p.fallback() != null && p.fallback().startsWith("glow:")) {
            d.setTransformation(columnTransform(pose));
            return;
        }
        Kind kind = p.kind();
        if (kind == Kind.SKIN) {
            kind = fallback ? (p.fallback() != null && p.fallback().startsWith("text:") ? Kind.TEXT : Kind.BLOCK)
                    : Kind.SKIN;
        }
        d.setTransformation(transform(pose, kind));
    }

    /** The display transformation that puts a piece's center at its pose. */
    static Transformation transform(Pose p, Kind kind) {
        float sx = (float) Math.max(1e-4, p.sx()), sy = (float) Math.max(1e-4, p.sy()),
                sz = (float) Math.max(1e-4, p.sz());
        if (p.hidden()) sx = sy = sz = 1e-4f;
        Quaternionf q = new Quaternionf().rotateY((float) p.yaw()).rotateX((float) p.pitch())
                .rotateZ((float) p.roll());
        Vector3f center = new Vector3f((float) p.x(), (float) p.y(), (float) p.z());
        Vector3f scale = new Vector3f(sx, sy, sz);
        return switch (kind) {
            case BLOCK -> {
                // A block's model spans 0..1 from its corner: shift it so its middle lands on the center.
                Vector3f half = q.transform(new Vector3f(scale).mul(0.5f));
                yield new Transformation(center.sub(half), q, scale, new Quaternionf());
            }
            case SKIN -> new Transformation(center, q, scale, ITEM_FLIP);
            // Text sits above its origin; scaled up so a rune is about a block tall at size 1.
            case TEXT -> new Transformation(center.sub(0, 0.25f * sy * 4, 0), q,
                    new Vector3f(sx * 4, sy * 4, 1), new Quaternionf());
        };
    }
}
