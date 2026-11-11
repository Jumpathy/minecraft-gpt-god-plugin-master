package net.bigyous.gptgodmc.god.vision;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.WaterMob;
import org.bukkit.util.BoundingBox;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.StructureManager;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.Perception;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Sync;
import net.bigyous.gptgodmc.god.tools.Targets;
import net.bigyous.gptgodmc.god.tools.ToolException;
import net.bigyous.gptgodmc.god.vessel.Vessel;

/** The god's eyes: renders views of the world as PNG images. */
public final class Vision {
    public enum View {
        EYES, VESSEL, OVERHEAD, PANORAMA, ORBIT;

        public static View parse(String s) throws ToolException {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ToolException("Unknown view '" + s + "'. Use eyes, vessel, overhead, panorama or orbit.");
            }
        }
    }

    public record Request(View view, String target, boolean xray, int radius, String layer) {
    }

    public record Result(byte[] png, String caption) {
    }

    private record Captured(Scene scene, View view, Renderer.Camera camera, int cx, int cy, int cz, int radius,
            Renderer.OverheadMode mode, String caption) {
    }

    private static final int POV_DIST = 96;
    private static final int XRAY_DIST = 64;

    private Vision() {
    }

    /** Captures on the server thread, renders on the calling thread. Do not call from the server thread. */
    public static Result render(Request req) throws ToolException {
        Captured c = Sync.call(() -> capture(req), 8000);
        BufferedImage img;
        switch (c.view()) {
            case OVERHEAD -> img = Renderer.overhead(c.scene(), c.cx(), c.cy(), c.cz(), c.radius(), 512, c.mode());
            case PANORAMA -> img = Renderer.panorama(c.scene(), c.camera().x(), c.camera().y(), c.camera().z(), 768,
                    320, req.xray() ? XRAY_DIST : 80, req.xray());
            default -> img = Renderer.perspective(c.scene(), c.camera(), 640, 360,
                    req.xray() ? XRAY_DIST : POV_DIST, req.xray());
        }
        byte[] png = encode(img);
        save(png, c.view().name().toLowerCase(Locale.ROOT));
        return new Result(png, c.caption());
    }

    private static Captured capture(Request req) throws ToolException {
        Palette palette = Palette.get();
        View view = req.view();
        Player viewer = null;
        boolean excludeVessel = false;
        Renderer.Camera cam = null;
        Location center;
        String caption;

        switch (view) {
            case EYES -> {
                if (req.target() == null || req.target().isBlank()) {
                    throw new ToolException("The 'eyes' view needs a player as target.");
                }
                viewer = Targets.player(req.target());
                Location eye = viewer.getEyeLocation();
                cam = new Renderer.Camera(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), 70);
                center = eye;
                caption = String.format("First-person view through %s's eyes from %s, facing %s, looking %s.",
                        viewer.getName(), Targets.describe(eye), Perception.facing(eye.getYaw()), pitchWord(eye.getPitch()));
            }
            case VESSEL -> {
                Vessel vessel = God.vessel();
                Location body = vessel.location();
                if (body == null) {
                    throw new ToolException("Your vessel is not manifested; use vessel_go first.");
                }
                Location eye = body.clone().add(0, vessel.eyeHeight(), 0);
                double yaw = body.getYaw();
                double pitch = 12;
                if (req.target() != null && !req.target().isBlank()) {
                    Location t = Targets.location(req.target());
                    double[] yp = Renderer.lookAt(eye.getX(), eye.getY(), eye.getZ(), t.getX(), t.getY() + 1, t.getZ());
                    yaw = yp[0];
                    pitch = yp[1];
                }
                cam = new Renderer.Camera(eye.getX(), eye.getY(), eye.getZ(), yaw, pitch, 75);
                center = eye;
                excludeVessel = true;
                caption = String.format("View from your vessel's eyes at %s, facing %s.", Targets.describe(eye),
                        Perception.facing((float) yaw));
            }
            case ORBIT -> {
                String target = req.target() == null || req.target().isBlank() ? "vessel" : req.target();
                Location focus = Targets.location(target);
                int dist = Math.max(4, Math.min(40, req.radius() <= 0 ? 10 : req.radius()));
                double yaw = 225;
                Player focusPlayer = null;
                try {
                    focusPlayer = Targets.player(target);
                    yaw = focusPlayer.getLocation().getYaw();
                } catch (ToolException ignored) {
                    // not a player: view from the south-west
                }
                double[] back = Renderer.forward(yaw, 0);
                Location aim = focus.clone().add(0, 1.2, 0);
                Location want = aim.clone().add(-back[0] * dist, dist * 0.55, -back[2] * dist);
                Location camLoc = collide(aim, want);
                double[] yp = Renderer.lookAt(camLoc.getX(), camLoc.getY(), camLoc.getZ(), aim.getX(), aim.getY(), aim.getZ());
                cam = new Renderer.Camera(camLoc.getX(), camLoc.getY(), camLoc.getZ(), yp[0], yp[1], 70);
                center = focus;
                caption = String.format("Third-person view of %s from %d blocks %s of it.", target,
                        Math.round(camLoc.distance(aim)), Perception.facing((float) (yp[0] + 180)));
            }
            case PANORAMA -> {
                String target = req.target() == null || req.target().isBlank() ? "vessel" : req.target();
                Location at;
                try {
                    at = Targets.player(target).getEyeLocation();
                } catch (ToolException e) {
                    at = Targets.location(target).clone().add(0, 1.6, 0);
                }
                cam = new Renderer.Camera(at.getX(), at.getY(), at.getZ(), 0, 0, 0);
                center = at;
                caption = String.format("360° panorama from %s. Left to right: north, east, south, west, north; "
                        + "top is 60° up, bottom is 75° down.", Targets.describe(at));
            }
            default -> { // OVERHEAD
                String target = req.target() == null || req.target().isBlank() ? "vessel" : req.target();
                center = Targets.location(target);
                caption = "";
            }
        }

        World world = center.getWorld();
        int radius;
        Renderer.OverheadMode mode = Renderer.OverheadMode.SURFACE;
        if (view == View.OVERHEAD) {
            radius = Math.max(8, Math.min(128, req.radius() <= 0 ? 48 : req.radius()));
            String layer = req.layer() == null ? "auto" : req.layer().toLowerCase(Locale.ROOT);
            if (req.xray()) {
                mode = Renderer.OverheadMode.XRAY;
            } else if (layer.equals("cave") || (layer.equals("auto") && isUnderground(center))) {
                mode = Renderer.OverheadMode.CAVE;
            }
            caption = String.format("Top-down map centered on %s %s, north is up, radius %d blocks (%.2f blocks per "
                    + "pixel), %s. Corner labels are x,z coordinates; faint lines are chunk borders.",
                    req.target() == null ? "your vessel" : req.target(), Targets.describe(center), radius,
                    2.0 * radius / 512, switch (mode) {
                        case SURFACE -> "showing the surface";
                        case CAVE -> "a slice at the target's level (bright = open floor, dark = rock walls)";
                        case XRAY -> "X-RAY: stone, dirt and natural terrain removed; ores, chests, spawners and "
                                + "built blocks remain";
                    });
        } else {
            radius = req.xray() ? XRAY_DIST : view == View.PANORAMA ? 80 : POV_DIST;
        }

        SnapshotVoxels voxels = SnapshotVoxels.capture(world, center.getBlockX(), center.getBlockZ(), radius);
        List<Scene.Box> boxes = boxes(world, center, radius, viewer, excludeVessel, view != View.OVERHEAD);
        List<Scene.Marker> markers = new ArrayList<>();
        for (String name : StructureManager.getStructures()) {
            Location c = StructureManager.getStructure(name).getLocation();
            if (c.getWorld() != null && c.getWorld().equals(world)) {
                markers.add(new Scene.Marker(c.getX(), c.getZ(), name));
            }
        }
        Scene.Dimension dim = switch (world.getEnvironment()) {
            case NETHER -> Scene.Dimension.NETHER;
            case THE_END -> Scene.Dimension.END;
            default -> Scene.Dimension.OVERWORLD;
        };
        Scene scene = new Scene(voxels, palette, boxes, markers, dim, Scene.daylightFor(world.getTime()),
                Scene.sunsetFor(world.getTime()), world.hasStorm());

        String legend = " Legend: blocks use Minecraft map colors with dark edge lines; players are bright boxes/dots "
                + "with name labels; red = hostile mobs, green = animals, grey = villagers/golems, yellow = items; "
                + "violet = your vessel." + (req.xray() && view != View.OVERHEAD
                        ? " X-RAY: natural stone and dirt are invisible; ores glow in their colors (cyan diamond, "
                                + "green emerald, yellow gold, orange copper, red redstone, blue lapis), chests are "
                                + "orange, spawners magenta."
                        : "")
                + " Grey haze = unloaded or distant terrain.";
        return new Captured(scene, view, cam, center.getBlockX(), center.getBlockY(), center.getBlockZ(), radius, mode,
                caption + legend);
    }

    private static List<Scene.Box> boxes(World world, Location center, int radius, Player viewer,
            boolean excludeVessel, boolean labelMobs) {
        List<Scene.Box> out = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, radius, Math.min(radius, 64), radius)) {
            if (e instanceof Display || e instanceof Interaction || e.equals(viewer)) continue;
            if (e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
            BoundingBox bb = e.getBoundingBox();
            double half = Math.max(0.15, bb.getWidthX() / 2);
            double height = Math.max(0.25, bb.getHeight());
            int color;
            String label = null;
            boolean player = false;
            if (e instanceof Player p) {
                color = playerColor(p.getName());
                label = p.getName();
                player = true;
            } else if (e instanceof Enemy) {
                color = 0xD03030;
            } else if (e instanceof org.bukkit.entity.AbstractVillager || e instanceof org.bukkit.entity.Golem) {
                color = 0xC8C8C8;
            } else if (e instanceof Animals || e instanceof WaterMob || e instanceof Ageable) {
                color = 0x58C858;
            } else if (e instanceof Item) {
                color = 0xF0E040;
            } else if (e instanceof Vehicle) {
                color = 0x8B5A2B;
            } else if (e instanceof LivingEntity) {
                color = 0xC8C8C8;
            } else {
                continue;
            }
            if (!player && e.customName() != null) {
                label = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(e.customName());
            } else if (!player && labelMobs && e instanceof LivingEntity
                    && e.getLocation().distanceSquared(center) < 20 * 20) {
                label = e.getType().getKey().getKey();
            }
            Location l = e.getLocation();
            out.add(new Scene.Box(l.getX(), l.getY(), l.getZ(), half, height, color, label, false, player));
        }
        Vessel vessel = God.vessel();
        Location v = vessel.location();
        if (!excludeVessel && v != null && v.getWorld().equals(world) && v.distance(center) <= radius * 1.5) {
            Persona persona = Persona.current();
            out.add(new Scene.Box(v.getX(), v.getY(), v.getZ(), 0.45 * vessel.scale(), vessel.eyeHeight() + 0.5,
                    0xB24BF3, (persona == null ? "GOD" : persona.shortName()) + " (YOU)", true, false));
        }
        return out;
    }

    static int playerColor(String name) {
        int[] colors = { 0xFFD23F, 0x3FD2FF, 0xFF7AE0, 0x7CFF6B, 0xFFA03F, 0xFFFFFF, 0x9DB4FF, 0xFF5E5E };
        return colors[Math.floorMod(name.hashCode(), colors.length)];
    }

    private static boolean isUnderground(Location l) {
        return l.getBlock().getLightFromSky() < 4 && l.getBlockY() < l.getWorld().getHighestBlockYAt(l) - 3;
    }

    /** Pulls a third-person camera in front of any wall between it and the subject. */
    private static Location collide(Location from, Location to) {
        double dist = from.distance(to);
        double[] d = { (to.getX() - from.getX()) / dist, (to.getY() - from.getY()) / dist, (to.getZ() - from.getZ()) / dist };
        double reach = dist;
        for (double t = 0.5; t <= dist; t += 0.25) {
            Location probe = from.clone().add(d[0] * t, d[1] * t, d[2] * t);
            if (probe.getBlock().getType().isOccluding()) {
                reach = Math.max(1.5, t - 0.6);
                break;
            }
        }
        return from.clone().add(d[0] * reach, d[1] * reach, d[2] * reach);
    }

    private static String pitchWord(float pitch) {
        if (pitch > 45) return "steeply down";
        if (pitch > 15) return "down";
        if (pitch < -45) return "steeply up";
        if (pitch < -15) return "up";
        return "ahead";
    }

    static byte[] encode(BufferedImage img) throws ToolException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ToolException("Could not encode the image: " + e.getMessage());
        }
    }

    static void save(byte[] png, String kind) {
        if (!GPTGOD.get().getConfig().getBoolean("vision.save-renders", false)) {
            return;
        }
        try {
            Path dir = GPTGOD.get().getDataFolder().toPath().resolve("views");
            Files.createDirectories(dir);
            String name = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date()) + "-"
                    + kind + ".png";
            Files.write(dir.resolve(name), png);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save a render", e);
        }
    }
}
