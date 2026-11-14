package net.bigyous.gptgod.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgod.client.relic.Relics;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Relics drawn from art alone, with no resource pack. First a relic with a true shape (see trueShape).
 * Then the plugin's own painted relics, from the
 * very messages it sends (src/test/resources/protocol/relics), taken in the way network bytes are: all
 * of them as item displays by day, from the side and by night (the glowing texels stay bright), in the
 * hotbar and inventory, held by the player, and held by armour stands beside the game's own sword and
 * stick (the grip must match). Their animation is checked tick by tick. Then six of them in the showcase
 * (see showcase), the plugin's two-form relic among them. Then relics with a real form and a glow (see
 * forms). Then the mod's older sample relics (a sprite that is not square, an item frame, a dropped
 * item), and finally the art taken away again. One stack names a relic nobody sent art for: it must
 * look as vanilla shows it.
 *
 * With the environment variable GPTGOD_RELIC_PACK naming the plugin's resource pack (a zip), the run
 * ends with the same showcase drawn by the game alone from that pack, the mod holding no art: what
 * players without the mod see.
 */
public class RelicShotsTest implements FabricClientGameTest {
    /** The plugin's relics in the order they are shown: six above, five below. */
    private static final String[] PLUGIN = { "skysplinter", "skysplinter__on", "dawnbreaker", "starfall_greatsword", "sunspire",
            "codex_of_ash", "crown_of_the_first_king", "heart_of_the_tide", "vial_of_starlight", "lantern_wisp", "the_watcher" };
    /** Where the displays hang, in blocks from the player: how far ahead, and how big. */
    private static final double AHEAD = 3.9, SCALE = 1.4;
    /** The showcase: base item and relic. The two forms of one relic, two more blades, two trinkets. */
    private static final String[][] SHOWN = { { "iron_sword", "skysplinter" }, { "iron_sword", "skysplinter__on" },
            { "iron_sword", "dawnbreaker" }, { "iron_sword", "starfall_greatsword" }, { "amethyst_shard", "vial_of_starlight" },
            { "amethyst_shard", "heart_of_the_tide" } };

    private static String stack(String item, String relic) {
        return "minecraft:" + item + "[minecraft:item_model=\"gptgodmc:relic/" + relic + "\"]";
    }

    private static String display(String item, String relic, double left, double up, double ahead, double scale, String tags) {
        return "execute at @a run summon minecraft:item_display ^" + left + " ^" + up + " ^" + ahead + " {" + tags
                + "item:{id:\"minecraft:" + item + "\",count:1,components:{\"minecraft:item_model\":\"gptgodmc:relic/" + relic
                + "\"}},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:["
                + scale + "f," + scale + "f," + scale + "f]}}";
    }

    /** A small name tag at a place in the world, turned to whoever looks. */
    private static String label(String text, double x, double y, double z) {
        return "summon minecraft:text_display " + x + " " + y + " " + z + " {billboard:\"center\",text:\"" + text
                + "\",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.45f,0.45f,0.45f]}}";
    }

    /** Stands the player `left` and `ahead` of home and turns the eyes to a point given the same way (heights from the ground). */
    private static String lookFrom(double[] home, double left, double ahead, double atLeft, double atUp, double atAhead) {
        return "tp @a " + (home[0] + left) + " " + home[1] + " " + (home[2] + ahead) + " facing " + (home[0] + atLeft) + " "
                + (home[1] + atUp - 1.62) + " " + (home[2] + atAhead);
    }

    private static ItemModel model(Minecraft mc, String relic) {
        return mc.getModelManager().getItemModel(Identifier.fromNamespaceAndPath("gptgodmc", "relic/" + relic));
    }

    private static byte[] message(String relic) {
        try (InputStream in = RelicShotsTest.class.getResourceAsStream("/gptgod-relics/" + relic + ".json")) {
            return in.readAllBytes();
        } catch (IOException | NullPointerException e) {
            throw new AssertionError("the plugin's message for " + relic + " is not among the test's resources", e);
        }
    }

    /** The vanilla item the plugin makes a relic of this kind from. */
    private static String baseItem(byte[] message) {
        String kind = JsonParser.parseString(new String(message, StandardCharsets.UTF_8)).getAsJsonObject().get("kind").getAsString();
        return switch (kind) {
            case "blade" -> "iron_sword";
            case "staff" -> "stick";
            case "tome" -> "book";
            case "spirit" -> "paper";
            default -> "amethyst_shard";
        };
    }

    /** The frame of its strip a relic shows right now, as the game's own item renderer is told it. */
    private static int frame(Minecraft mc, String relic) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("gptgodmc", "relic/" + relic));
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, mc.level, null, 0);
        int frame = -1;
        for (Object part : (List<?>) state.getModelIdentity()) {
            if (part instanceof Integer i) frame = i;
        }
        return frame;
    }

    private static void shot(ClientGameTestContext ctx, String name, int ticks) {
        ctx.waitTicks(ticks);
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTicks(2);
        ctx.takeScreenshot(name);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (System.getenv("GPTGOD_E2E") != null || System.getenv("GPTGOD_VIDEO") != null || System.getenv("GPTGOD_GUIDE") != null) return; // the end-to-end run has its own test
        ctx.getInput().resizeWindow(1920, 1080);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("weather clear");
            sp.getServer().runCommand("execute as @a at @s align xyz run tp @s ~0.5 ~ ~0.5 0 0");
            ctx.waitTicks(10);
            // Before any art: a relic stack is whatever the resource packs make of it (nothing, here).
            ItemModel vanilla = ctx.computeOnClient(mc -> model(mc, "demo_blade"));
            trueShape(ctx, sp);
            sp.getServer().runCommand("kill @e[type=!minecraft:player]");
            sp.getServer().runCommand("clear @a");
            ctx.runOnClient(mc -> Relics.clear());
            if (System.getenv("GPTGOD_RELIC_SHAPE_ONLY") != null) return;
            pluginRelics(ctx, sp, vanilla);
            sp.getServer().runCommand("kill @e[type=!minecraft:player]");
            sp.getServer().runCommand("clear @a");
            showcase(ctx, sp, "relic-real", true);
            sp.getServer().runCommand("kill @e[type=!minecraft:player]");
            sp.getServer().runCommand("clear @a");
            ctx.runOnClient(mc -> Relics.clear());
            forms(ctx, sp);
            sp.getServer().runCommand("kill @e[type=!minecraft:player]");
            sp.getServer().runCommand("clear @a");
            ctx.runOnClient(mc -> Relics.clear());
            samples(ctx, sp, vanilla);

            String pack = System.getenv("GPTGOD_RELIC_PACK");
            if (pack != null && !pack.isBlank()) {
                sp.getServer().runCommand("kill @e[type=!minecraft:player]");
                sp.getServer().runCommand("clear @a");
                sp.getServer().runCommand("execute at @a run fill ^-1 ^ ^4 ^-1 ^1 ^4 minecraft:air");
                loadPack(ctx, pack);
                showcase(ctx, sp, "relic-pack", false);
            }
        }
    }

    /**
     * Makes the game load a resource pack as a player would in the pack screen, with the mod holding no
     * relic art, and checks that the showcase's relics now come from the pack and not the missing model.
     */
    private static void loadPack(ClientGameTestContext ctx, String zip) {
        ctx.runOnClient(mc -> {
            Relics.clear();
            Path folder = mc.getResourcePackDirectory();
            try {
                Files.createDirectories(folder);
                Files.copy(Path.of(zip), folder.resolve("gptgod-relic-pack.zip"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new AssertionError("could not copy the pack " + zip, e);
            }
            PackRepository packs = mc.getResourcePackRepository();
            packs.reload();
            if (!packs.addPack("file/gptgod-relic-pack.zip")) throw new AssertionError("the game does not see the pack " + zip);
            mc.options.updateResourcePacks(packs); // as the pack screen does: this starts the reload
        });
        ctx.waitTicks(5);
        ctx.waitFor(mc -> mc.gui.overlay() == null, 20 * 120);
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> {
            ItemModel missing = model(mc, "nobody_sent_this");
            for (String[] shown : SHOWN) {
                ItemModel fromPack = model(mc, shown[1]);
                if (fromPack == missing) throw new AssertionError(shown[1] + ": the pack gives the game no model for it");
                if (fromPack.getClass().getName().startsWith("net.bigyous.")) throw new AssertionError(shown[1] + " is still drawn by the mod");
            }
        });
    }

    /** Waits for the tick on which a relic's strip comes round to its first frame, so two pictures can be taken at the same point of it. */
    private static void awaitFirstFrame(ClientGameTestContext ctx, String relic) {
        for (int i = 0; i < 60 && ctx.computeOnClient(mc -> frame(mc, relic)) == 0; i++) ctx.waitTick();
        for (int i = 0; i < 60 && ctx.computeOnClient(mc -> frame(mc, relic)) != 0; i++) ctx.waitTick();
    }

    /**
     * Six relics the same way twice over, once drawn by the mod from the plugin's art and once by the game
     * from the plugin's resource pack: as item displays from the front, from three quarters and from the
     * side, with close looks at the two-form relic, the trinkets and the plain blades, by day and by night;
     * the two-form relic in the hand, asleep and awake, by day and by night; held by armour stands beside
     * the game's own sword; and as icons in the hotbar and the inventory.
     */
    private static void showcase(ClientGameTestContext ctx, TestSingleplayerContext sp, String name, boolean modDraws) {
        double[] at = ctx.computeOnClient(mc -> new double[] { mc.player.getX(), mc.player.getY(), mc.player.getZ() });
        String home = "tp @a " + at[0] + " " + at[1] + " " + at[2];
        double ahead = 4.2;
        for (int i = 0; i < SHOWN.length; i++) {
            double left = (2.5 - i) * 1.7;
            sp.getServer().runCommand(display(SHOWN[i][0], SHOWN[i][1], left, 1.9, ahead, 1.5, ""));
            sp.getServer().runCommand(label(SHOWN[i][1], at[0] + left, at[1] + 0.85, at[2] + ahead));
        }
        // The game's own iron sword above Dawnbreaker, shown the same way: relics should be lit like it.
        sp.getServer().runCommand("execute at @a run summon minecraft:item_display ^0.85 ^3.5 ^" + ahead
                + " {item:{id:\"minecraft:iron_sword\",count:1},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                + "translation:[0f,0f,0f],scale:[1.5f,1.5f,1.5f]}}");
        sp.getServer().runCommand(label("vanilla iron_sword", at[0] + 0.85, at[1] + 4.45, at[2] + ahead));
        int fov = ctx.computeOnClient(mc -> mc.options.fov().get());
        ctx.runOnClient(mc -> {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        });
        int n = 0;
        for (String time : new String[] { "day", "night" }) {
            sp.getServer().runCommand("time set " + (time.equals("day") ? "noon" : "midnight"));
            sp.getServer().runCommand(home + " 0 0");
            shot(ctx, String.format("%s-%02d-front-%s", name, ++n, time), 25);
            sp.getServer().runCommand(lookFrom(at, 4.2, 0.6, 0.5, 1.9, ahead));
            shot(ctx, String.format("%s-%02d-three-quarters-%s", name, ++n, time), 10);
            sp.getServer().runCommand(lookFrom(at, 6.3, ahead - 0.7, 3.4, 1.9, ahead));
            shot(ctx, String.format("%s-%02d-side-%s", name, ++n, time), 10);
            ctx.runOnClient(mc -> mc.options.fov().set(40));
            sp.getServer().runCommand(lookFrom(at, 5.6, 2.2, 3.4, 1.9, ahead));
            shot(ctx, String.format("%s-%02d-skysplinter-close-%s", name, ++n, time), 10);
            sp.getServer().runCommand(lookFrom(at, -5.6, 2.2, -3.4, 1.9, ahead));
            shot(ctx, String.format("%s-%02d-trinkets-close-%s", name, ++n, time), 10);
            ctx.runOnClient(mc -> mc.options.fov().set(50));
            sp.getServer().runCommand(lookFrom(at, 2.0, 2.3, 0, 1.9, ahead));
            shot(ctx, String.format("%s-%02d-blades-close-%s", name, ++n, time), 10);
            ctx.runOnClient(mc -> mc.options.fov().set(fov));
        }

        // In the hand, turned away from the displays: asleep, then awake (the server swaps the stack's item_model).
        String asleep = stack("iron_sword", "skysplinter"), awake = stack("iron_sword", "skysplinter__on");
        sp.getServer().runCommand(home + " 180 0");
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + asleep);
        sp.getServer().runCommand("item replace entity @a weapon.offhand with " + stack("amethyst_shard", "vial_of_starlight"));
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        shot(ctx, String.format("%s-%02d-hand-asleep-night", name, ++n), 25);
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + awake);
        shot(ctx, String.format("%s-%02d-hand-awake-night", name, ++n), 20);
        sp.getServer().runCommand("time set noon");
        shot(ctx, String.format("%s-%02d-hand-awake-day", name, ++n), 25);
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + asleep);
        shot(ctx, String.format("%s-%02d-hand-asleep-day", name, ++n), 20);
        // The game's own iron sword in the same hand, by day and by night, to compare.
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:iron_sword");
        shot(ctx, String.format("%s-%02d-hand-vanilla-sword-day", name, ++n), 20);
        sp.getServer().runCommand("time set midnight");
        shot(ctx, String.format("%s-%02d-hand-vanilla-sword-night", name, ++n), 25);
        sp.getServer().runCommand("time set noon");
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + asleep);
        if (modDraws) {
            // At the same point of the animation, so the two pictures can be laid over each other: the hilt
            // must be on the same pixels in both forms.
            ctx.runOnClient(mc -> mc.gui.toastManager().clear());
            ctx.waitTicks(20);
            awaitFirstFrame(ctx, "skysplinter");
            ctx.takeScreenshot(String.format("%s-%02d-hand-asleep-day-same-frame", name, ++n));
            sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + awake);
            ctx.waitFor(mc -> Identifier.fromNamespaceAndPath("gptgodmc", "relic/skysplinter__on")
                    .equals(mc.player.getMainHandItem().get(DataComponents.ITEM_MODEL)));
            ctx.waitTick();
            ctx.runOnClient(mc -> {
                FirstPersonHandsAndItemsRenderState hands = new FirstPersonHandsAndItemsRenderState();
                mc.player.firstPersonHandsAndItems().extractRenderState(mc.player, 1f, hands);
                Identifier held = hands.mainHandItem.get(DataComponents.ITEM_MODEL);
                if (held == null || !held.getPath().equals("relic/skysplinter__on") || hands.mainHandHeight < 0.95f) {
                    throw new AssertionError("the awakened form is not shown at once: " + held + ", hand at " + hands.mainHandHeight);
                }
            });
            awaitFirstFrame(ctx, "skysplinter__on");
            ctx.takeScreenshot(String.format("%s-%02d-hand-awake-day-same-frame", name, ++n));
        }

        // The grip beside the game's own sword: armour stands seen from their right.
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
        sp.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
        String[] held = { null, "skysplinter", "skysplinter__on", "dawnbreaker", "starfall_greatsword" };
        for (int i = 0; i < held.length; i++) {
            double x = at[0] + (i - 2) * 1.3, z = at[2] - 3.6;
            String item = "id:\"minecraft:iron_sword\",count:1"
                    + (held[i] == null ? "" : ",components:{\"minecraft:item_model\":\"gptgodmc:relic/" + held[i] + "\"}");
            sp.getServer().runCommand("summon minecraft:armor_stand " + x + " " + at[1] + " " + z
                    + " {ShowArms:1b,NoBasePlate:1b,Rotation:[-90f,0f],equipment:{mainhand:{" + item + "}}}");
            sp.getServer().runCommand(label(held[i] == null ? "vanilla iron_sword" : held[i], x, at[1] + 2.15, z));
        }
        shot(ctx, String.format("%s-%02d-grip-beside-vanilla", name, ++n), 15);

        // The icons: all six in the hotbar and in the inventory, at night so a glow spilling out of a slot would show.
        sp.getServer().runCommand("time set midnight");
        for (int i = 0; i < SHOWN.length; i++) {
            sp.getServer().runCommand("item replace entity @a hotbar." + i + " with " + stack(SHOWN[i][0], SHOWN[i][1]));
            sp.getServer().runCommand("item replace entity @a inventory." + (i * 2) + " with " + stack(SHOWN[i][0], SHOWN[i][1]));
        }
        sp.getServer().runCommand("item replace entity @a weapon.offhand with " + stack("iron_sword", "skysplinter__on"));
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        shot(ctx, String.format("%s-%02d-hotbar-night", name, ++n), 20);
        ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
        shot(ctx, String.format("%s-%02d-inventory-night", name, ++n), 5);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        if (!modDraws) iconsAreFlat(ctx);
        sp.getServer().runCommand("clear @a");
        sp.getServer().runCommand("time set noon");
        sp.getServer().runCommand(home + " 0 0");
    }

    /**
     * What the game picks from the pack for each way of showing: in an inventory slot the flat icon (no
     * deeper than the game's own flat items, and no wider than its square), elsewhere the solid model,
     * which stays within the square too (the pack draws no glow).
     */
    private static void iconsAreFlat(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            for (String[] shown : SHOWN) {
                ItemStack stack = new ItemStack(shown[0].equals("iron_sword") ? Items.IRON_SWORD : Items.AMETHYST_SHARD);
                stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("gptgodmc", "relic/" + shown[1]));
                Object[] gui = picked(mc, stack, ItemDisplayContext.GUI), world = picked(mc, stack, ItemDisplayContext.FIXED);
                float[] icon = (float[]) gui[1], solid = (float[]) world[1];
                if (gui[0] == world[0]) throw new AssertionError(shown[1] + ": the inventory shows the same model as the world");
                if (icon[5] - icon[2] > 1 / 16f + 1e-4f || icon[0] < -0.5001f || icon[3] > 0.5001f) {
                    throw new AssertionError(shown[1] + ": its icon is not the flat sprite: " + Arrays.toString(icon));
                }
                // No sheets of glow: the solid model stays within the item's square.
                if (solid[3] - solid[0] > 1.0001f) throw new AssertionError(shown[1] + ": something reaches past the square " + Arrays.toString(solid));
            }
        });
    }

    /** The model the game picks for a stack shown one way, and the box it fills (min x, y, z, max x, y, z, in blocks, display transform applied). */
    private static Object[] picked(Minecraft mc, ItemStack stack, ItemDisplayContext context) {
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, context, mc.level, null, 0);
        float[] box = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
        state.visitExtents(v -> {
            box[0] = Math.min(box[0], v.x());
            box[1] = Math.min(box[1], v.y());
            box[2] = Math.min(box[2], v.z());
            box[3] = Math.max(box[3], v.x());
            box[4] = Math.max(box[4], v.y());
            box[5] = Math.max(box[5], v.z());
        });
        List<?> identity = (List<?>) state.getModelIdentity();
        return new Object[] { identity.size() > 1 ? identity.get(1) : identity.get(0), box };
    }

    /**
     * Gravecall, the plugin's scythe, which carries a true shape, beside the same relic without it (drawn
     * from its picture as before) and the game's own stick: in item frames on a wall and as item displays
     * (as the god holds relics), from the front, three quarters and the side, by day and by night; in the
     * hand, first person and from behind and in front; and in the hotbar, where it is the flat picture.
     */
    private static void trueShape(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        double[] at = ctx.computeOnClient(mc -> new double[] { mc.player.getX(), mc.player.getY(), mc.player.getZ() });
        String home = "tp @a " + at[0] + " " + at[1] + " " + at[2];
        byte[] message = message("gravecall");
        JsonObject plain = JsonParser.parseString(new String(message, StandardCharsets.UTF_8)).getAsJsonObject();
        plain.remove("shape");
        plain.addProperty("id", "gravecall_voxels");
        byte[] voxels = plain.toString().getBytes(StandardCharsets.UTF_8);
        ctx.runOnClient(mc -> {
            ItemModel missing = model(mc, "gravecall");
            if (!Relics.receive(message) || !Relics.receive(voxels)) throw new AssertionError("gravecall was not taken as a relic message");
            if (model(mc, "gravecall") == missing || model(mc, "gravecall_voxels") == missing) throw new AssertionError("gravecall has no model");
        });

        // A stone wall to the south with three frames on it; in front of it, higher up, three item displays.
        int bx = (int) Math.floor(at[0]), by = (int) Math.floor(at[1]), bz = (int) Math.floor(at[2]);
        String wall = (bx - 4) + " " + by + " " + (bz + 6) + " " + (bx + 4) + " " + (by + 4) + " " + (bz + 6);
        sp.getServer().runCommand("fill " + wall + " minecraft:stone");
        String[] shown = { "gravecall", null, "gravecall_voxels" };
        for (int i = 0; i < shown.length; i++) {
            int dx = 2 - i * 2; // to the left (east) first
            String item = "id:\"minecraft:stick\",count:1"
                    + (shown[i] == null ? "" : ",components:{\"minecraft:item_model\":\"gptgodmc:relic/" + shown[i] + "\"}");
            sp.getServer().runCommand("summon minecraft:item_frame " + (bx + dx) + " " + (by + 1) + " " + (bz + 5)
                    + " {Facing:2b,Fixed:1b,Invulnerable:1b,Item:{" + item + "}}");
            String name = shown[i] == null ? "vanilla stick" : shown[i].equals("gravecall") ? "true shape" : "voxels";
            sp.getServer().runCommand(label(name, bx + dx + 0.5, by + 0.75, bz + 5.4));
            sp.getServer().runCommand("execute at @a run summon minecraft:item_display ^" + dx + " ^3.2 ^3.6 {item:{" + item + "},"
                    + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.6f,1.6f,1.6f]}}");
        }
        int fov = ctx.computeOnClient(mc -> mc.options.fov().get());
        ctx.runOnClient(mc -> {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        });
        int n = 0;
        for (String time : new String[] { "day", "night" }) {
            sp.getServer().runCommand("time set " + (time.equals("day") ? "noon" : "midnight"));
            sp.getServer().runCommand(home + " 0 0");
            shot(ctx, String.format("relic-shape-%02d-front-%s", ++n, time), 25);
            sp.getServer().runCommand(lookFrom(at, 3.6, 1.2, 0, 2.4, 4.4));
            shot(ctx, String.format("relic-shape-%02d-three-quarters-%s", ++n, time), 10);
            sp.getServer().runCommand(lookFrom(at, 6.0, 3.4, 0, 2.6, 3.8));
            shot(ctx, String.format("relic-shape-%02d-side-%s", ++n, time), 10);
            ctx.runOnClient(mc -> mc.options.fov().set(35));
            sp.getServer().runCommand(lookFrom(at, 3.4, 2.0, 1.2, 1.5, 5.6));
            shot(ctx, String.format("relic-shape-%02d-frames-three-quarters-%s", ++n, time), 10);
            sp.getServer().runCommand(lookFrom(at, 2.8, 1.6, 0.6, 3.2, 3.6));
            shot(ctx, String.format("relic-shape-%02d-displays-three-quarters-close-%s", ++n, time), 10);
            ctx.runOnClient(mc -> mc.options.fov().set(fov));
        }

        // Dropped on the grass, the true shape on the left and its voxels on the right, seen from close above.
        for (int i = 0; i < 2; i++) {
            String relic = i == 0 ? "gravecall" : "gravecall_voxels";
            sp.getServer().runCommand("summon minecraft:item " + (at[0] + (i == 0 ? 0.45 : -0.45)) + " " + at[1] + " " + (at[2] + 1.4)
                    + " {PickupDelay:32767,Age:-32768,Motion:[0d,0d,0d],Item:{id:\"minecraft:stick\",count:1,components:"
                    + "{\"minecraft:item_model\":\"gptgodmc:relic/" + relic + "\"}}}");
        }
        ctx.runOnClient(mc -> mc.options.fov().set(45));
        for (String time : new String[] { "day", "night" }) {
            sp.getServer().runCommand("time set " + (time.equals("day") ? "noon" : "midnight"));
            sp.getServer().runCommand(lookFrom(at, 0, 0.2, 0, 0.1, 1.4));
            shot(ctx, String.format("relic-shape-%02d-dropped-%s", ++n, time), time.equals("day") ? 20 : 25);
        }
        ctx.runOnClient(mc -> mc.options.fov().set(fov));
        sp.getServer().runCommand("kill @e[type=minecraft:item]");

        // In the hand, turned away from the wall: the true shape, then its voxels, then the game's stick.
        sp.getServer().runCommand("time set noon");
        sp.getServer().runCommand(home + " 180 0");
        String scythe = stack("stick", "gravecall"), plainScythe = stack("stick", "gravecall_voxels");
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + scythe);
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        shot(ctx, String.format("relic-shape-%02d-hand-day", ++n), 25);
        sp.getServer().runCommand("time set midnight");
        shot(ctx, String.format("relic-shape-%02d-hand-night", ++n), 25);
        sp.getServer().runCommand("time set noon");
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + plainScythe);
        shot(ctx, String.format("relic-shape-%02d-hand-voxels-day", ++n), 25);
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:stick");
        shot(ctx, String.format("relic-shape-%02d-hand-vanilla-stick-day", ++n), 20);
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + scythe);
        for (CameraType camera : new CameraType[] { CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT }) {
            ctx.runOnClient(mc -> mc.options.setCameraType(camera));
            String way = camera == CameraType.THIRD_PERSON_BACK ? "behind" : "front";
            sp.getServer().runCommand("time set noon");
            shot(ctx, String.format("relic-shape-%02d-third-person-%s-day", ++n, way), 20);
            sp.getServer().runCommand("time set midnight");
            shot(ctx, String.format("relic-shape-%02d-third-person-%s-night", ++n, way), 25);
        }
        sp.getServer().runCommand("time set noon");
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + plainScythe);
        shot(ctx, String.format("relic-shape-%02d-third-person-front-voxels-day", ++n), 20);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        // The hotbar: the true shape, its voxels and the stick, all flat pictures there.
        sp.getServer().runCommand("item replace entity @a hotbar.0 with " + scythe);
        sp.getServer().runCommand("item replace entity @a hotbar.1 with " + plainScythe);
        sp.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:stick");
        sp.getServer().runCommand("item replace entity @a weapon.offhand with " + scythe);
        shot(ctx, String.format("relic-shape-%02d-hotbar", ++n), 15);
        ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
        shot(ctx, String.format("relic-shape-%02d-inventory", ++n), 5);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        sp.getServer().runCommand("fill " + wall + " minecraft:air");
        sp.getServer().runCommand(home + " 0 0");
    }

    private static void pluginRelics(ClientGameTestContext ctx, TestSingleplayerContext sp, ItemModel vanilla) {
        // Where the player stands, looking south: east is to the left, the displays hang to the south.
        double[] at = ctx.computeOnClient(mc -> new double[] { mc.player.getX(), mc.player.getY(), mc.player.getZ() });
        String home = "tp @a " + at[0] + " " + at[1] + " " + at[2];
        String[] items = new String[PLUGIN.length];
        for (int i = 0; i < PLUGIN.length; i++) {
            String relic = PLUGIN[i];
            byte[] message = message(relic);
            items[i] = baseItem(message);
            ctx.runOnClient(mc -> {
                if (!Relics.receive(message)) throw new AssertionError(relic + " was not taken as a relic message");
                if (model(mc, relic) == vanilla) throw new AssertionError(relic + ": its art is not used for its model");
            });
            // Six in the upper row, five in the lower, each with its name under it.
            boolean upper = i < 6;
            double left = upper ? (2.5 - i) * 1.6 : (8 - i) * 1.6, up = upper ? 3.1 : 1.25;
            sp.getServer().runCommand(display(items[i], relic, left, up, AHEAD, SCALE, ""));
            sp.getServer().runCommand(label(relic, at[0] + left, at[1] + up - 0.95, at[2] + AHEAD));
        }
        ctx.runOnClient(mc -> {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        });
        shot(ctx, "relic-plugin-01-row-day", 20);

        // Their strips are 8 frames of 3 ticks: each frame in turn, each for exactly its 3 ticks.
        int[] frames = new int[49];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = ctx.computeOnClient(mc -> frame(mc, "the_watcher"));
            ctx.waitTick();
        }
        int changes = 0, held = -1;
        for (int i = 1; i < frames.length; i++) {
            if (frames[i] == frames[i - 1]) {
                if (held >= 0) held++;
                continue;
            }
            if (frames[i] != (frames[i - 1] + 1) % 8 || (held >= 0 && held != 3)) {
                throw new AssertionError("relic frames out of step: " + Arrays.toString(frames));
            }
            changes++;
            held = 1;
        }
        if (changes < 15) throw new AssertionError("relic animation too slow or stuck: " + Arrays.toString(frames));

        // From the side every relic is a thin slab; then the two spirits and their soft halos up close.
        sp.getServer().runCommand(lookFrom(at, 4.6, -0.4, 0.3, 2.2, AHEAD));
        shot(ctx, "relic-plugin-02-row-from-the-side", 10);
        sp.getServer().runCommand(lookFrom(at, -5.0, 2.2, -2.4, 1.25, AHEAD));
        shot(ctx, "relic-plugin-03-spirits-close", 10);
        sp.getServer().runCommand(home + " 0 0");

        sp.getServer().runCommand("time set midnight");
        shot(ctx, "relic-plugin-04-row-night", 25);
        sp.getServer().runCommand("time set noon");

        // The hotbar full of them and all of them again in the inventory; a blade in each hand.
        for (int i = 0; i < PLUGIN.length; i++) {
            int other = PLUGIN.length - 1 - i;
            if (i < 9) sp.getServer().runCommand("item replace entity @a hotbar." + i + " with " + stack(items[i + 2], PLUGIN[i + 2]));
            sp.getServer().runCommand("item replace entity @a inventory." + (i * 2) + " with " + stack(items[other], PLUGIN[other]));
        }
        sp.getServer().runCommand("item replace entity @a weapon.offhand with " + stack("iron_sword", "starfall_greatsword"));
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        shot(ctx, "relic-plugin-05-blades-first-person", 20);
        ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
        shot(ctx, "relic-plugin-06-inventory", 5);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));

        // The player seen from in front, a blade in each hand.
        int fov = ctx.computeOnClient(mc -> mc.options.fov().get());
        ctx.runOnClient(mc -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.options.fov().set(40);
            mc.gui.hud.toggle();
        });
        shot(ctx, "relic-plugin-07-blades-third-person", 10);
        ctx.runOnClient(mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.fov().set(fov);
        });

        // The grip, beside the game's own: armour stands seen from their right, on the other side of the player.
        sp.getServer().runCommand(home + " 180 0");
        String[][] inHand = { { "iron_sword", null }, { "iron_sword", "dawnbreaker" }, { "iron_sword", "starfall_greatsword" },
                { "stick", null }, { "stick", "sunspire" } };
        for (int i = 0; i < inHand.length; i++) {
            double x = at[0] + (i - 2) * 1.3, z = at[2] - 3.6;
            String item = "id:\"minecraft:" + inHand[i][0] + "\",count:1"
                    + (inHand[i][1] == null ? "" : ",components:{\"minecraft:item_model\":\"gptgodmc:relic/" + inHand[i][1] + "\"}");
            sp.getServer().runCommand("summon minecraft:armor_stand " + x + " " + at[1] + " " + z
                    + " {ShowArms:1b,NoBasePlate:1b,Rotation:[-90f,0f],equipment:{mainhand:{" + item + "}}}");
            sp.getServer().runCommand(label(inHand[i][1] == null ? "vanilla " + inHand[i][0] : inHand[i][1], x, at[1] + 2.15, z));
        }
        shot(ctx, "relic-plugin-08-grip-beside-vanilla", 15);

        // And a staff in the hand, a tome in the other.
        sp.getServer().runCommand(home + " 0 0");
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + stack("stick", "sunspire"));
        sp.getServer().runCommand("item replace entity @a weapon.offhand with " + stack("book", "codex_of_ash"));
        ctx.runOnClient(mc -> mc.gui.hud.toggle());
        shot(ctx, "relic-plugin-09-staff-first-person", 10);
    }

    /**
     * Relics with a real form: the sample shield and the pale sword (in both its forms) as item displays
     * seen from the front, from three quarters, from the side and from behind, by day and by night; the
     * sword in the hand; with the game's improved transparency on (the film round the blade is
     * see-through); and the sword changing form in the hand, which must show at once.
     */
    private static void forms(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        double[] at = ctx.computeOnClient(mc -> new double[] { mc.player.getX(), mc.player.getY(), mc.player.getZ() });
        String home = "tp @a " + at[0] + " " + at[1] + " " + at[2] + " 0 0";
        double ahead = 3.5;
        ctx.runOnClient(mc -> Relics.demo());
        String[][] shown = { { "amethyst_shard", "demo_shield" }, { "iron_sword", "demo_lightblade" }, { "iron_sword", "demo_lightblade__on" } };
        for (int i = 0; i < shown.length; i++) {
            double left = (1 - i) * 2.4;
            sp.getServer().runCommand(display(shown[i][0], shown[i][1], left, 1.9, ahead, 2.0, ""));
            sp.getServer().runCommand(label(shown[i][1], at[0] + left, at[1] + 0.55, at[2] + ahead));
        }
        Runnable hud = () -> ctx.runOnClient(mc -> mc.gui.hud.toggle());
        ctx.runOnClient(mc -> {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        });
        shot(ctx, "relic-form-01-front-day", 20);
        sp.getServer().runCommand(lookFrom(at, 3.3, 0.5, 0, 1.9, ahead));
        shot(ctx, "relic-form-02-three-quarters-day", 10);
        sp.getServer().runCommand(lookFrom(at, 5.2, ahead - 0.7, 0, 1.9, ahead));
        shot(ctx, "relic-form-03-side-day", 10);
        sp.getServer().runCommand(lookFrom(at, -1.2, ahead + 3.2, 0, 1.9, ahead));
        shot(ctx, "relic-form-04-from-behind-day", 10);

        sp.getServer().runCommand("time set midnight");
        sp.getServer().runCommand(home);
        shot(ctx, "relic-form-05-front-night", 25);
        sp.getServer().runCommand(lookFrom(at, 3.3, 0.5, 0, 1.9, ahead));
        shot(ctx, "relic-form-06-three-quarters-night", 10);
        sp.getServer().runCommand(lookFrom(at, 5.2, ahead - 0.7, 0, 1.9, ahead));
        shot(ctx, "relic-form-07-side-night", 10);
        sp.getServer().runCommand(home);

        // In the hand: the pale sword, the shield in the other.
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + stack("iron_sword", "demo_lightblade"));
        sp.getServer().runCommand("item replace entity @a weapon.offhand with " + stack("amethyst_shard", "demo_shield"));
        hud.run();
        shot(ctx, "relic-form-08-hand-night", 15);
        sp.getServer().runCommand("time set noon");
        shot(ctx, "relic-form-09-hand-day", 25);

        // The game's improved transparency draws see-through things its own way: the film must survive it.
        sp.getServer().runCommand("time set midnight");
        ctx.runOnClient(mc -> mc.options.improvedTransparency().set(true));
        shot(ctx, "relic-form-10-improved-transparency-night", 30);
        ctx.runOnClient(mc -> mc.options.improvedTransparency().set(false));
        ctx.waitTicks(20);

        // The relic awakens in the hand: the server swaps the stack's item_model for the second form's.
        sp.getServer().runCommand("item replace entity @a weapon.mainhand with " + stack("iron_sword", "demo_lightblade__on"));
        ctx.waitFor(mc -> Identifier.fromNamespaceAndPath("gptgodmc", "relic/demo_lightblade__on")
                .equals(mc.player.getMainHandItem().get(DataComponents.ITEM_MODEL)));
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            FirstPersonHandsAndItemsRenderState hands = new FirstPersonHandsAndItemsRenderState();
            mc.player.firstPersonHandsAndItems().extractRenderState(mc.player, 1f, hands);
            Identifier held = hands.mainHandItem.get(DataComponents.ITEM_MODEL);
            if (held == null || !held.getPath().equals("relic/demo_lightblade__on") || hands.mainHandHeight < 0.95f) {
                throw new AssertionError("the new form is not shown at once: " + held + ", hand at " + hands.mainHandHeight);
            }
        });
        shot(ctx, "relic-form-11-awakened-in-hand-night", 3);
        sp.getServer().runCommand("time set noon");
    }

    private static void samples(ClientGameTestContext ctx, TestSingleplayerContext sp, ItemModel vanilla) {
        List<String> give = ctx.computeOnClient(mc -> Relics.demo());
        ctx.runOnClient(mc -> {
            if (model(mc, "demo_blade") == vanilla) throw new AssertionError("the relic's art is not used for its model");
            if (model(mc, "nobody_sent_this") != vanilla) throw new AssertionError("a relic without art is not left to the game");
            if (model(mc, "dawnbreaker") != vanilla) throw new AssertionError("a forgotten relic is still drawn from its art");
        });

        // The commands /godfx relics offers fill the hotbar: blade (in hand), orb, staff, and the three with a form.
        for (String command : give) sp.getServer().runCommand(command.substring(1).replace("@s", "@a"));
        sp.getServer().runCommand("item replace entity @a weapon.offhand with " + stack("paper", "demo_orb"));
        sp.getServer().runCommand("item replace entity @a hotbar.6 with " + stack("paper", "demo_orb") + " 16");
        sp.getServer().runCommand("item replace entity @a hotbar.7 with " + stack("amethyst_shard", "nobody_sent_this"));
        sp.getServer().runCommand("item replace entity @a hotbar.8 with minecraft:iron_sword");
        sp.getServer().runCommand("item replace entity @a inventory.0 with " + stack("iron_sword", "demo_blade")
                .replace("]", ",minecraft:enchantment_glint_override=true]"));
        // In front of the player: the three relics as item displays (the staff turned to show its edge)...
        sp.getServer().runCommand(display("iron_sword", "demo_blade", 2.2, 1.9, 3.2, 1.4, ""));
        sp.getServer().runCommand(display("paper", "demo_orb", 0, 2.7, 3.2, 1.4, "billboard:\"center\","));
        sp.getServer().runCommand(display("stick", "demo_staff", -2.2, 1.9, 3.2, 1.4, "Rotation:[50f,0f],"));
        // ...an orb in an item frame on a pillar, and a staff dropped on the ground.
        sp.getServer().runCommand("execute at @a run fill ^-1 ^ ^4 ^-1 ^1 ^4 minecraft:smooth_stone");
        sp.getServer().runCommand("execute at @a run summon minecraft:item_frame ^-1 ^1 ^3 {Facing:2b,Item:{id:\"minecraft:paper\",count:1,"
                + "components:{\"minecraft:item_model\":\"gptgodmc:relic/demo_orb\"}}}");
        sp.getServer().runCommand("execute at @a run summon minecraft:item ^1.2 ^0.2 ^4 {PickupDelay:32767,Item:{id:\"minecraft:stick\",count:1,"
                + "components:{\"minecraft:item_model\":\"gptgodmc:relic/demo_staff\"}}}");
        shot(ctx, "relic-01-day", 30);
        shot(ctx, "relic-02-day-later-frame", 5);

        ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
        shot(ctx, "relic-03-inventory", 5);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));

        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        shot(ctx, "relic-04-third-person", 5);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        sp.getServer().runCommand("time set midnight");
        shot(ctx, "relic-05-night", 20);

        // The art goes away (the server said relic_gone, or we left): back to what the packs say.
        sp.getServer().runCommand("time set noon");
        ctx.runOnClient(mc -> Relics.clear());
        ctx.runOnClient(mc -> {
            if (model(mc, "demo_blade") != vanilla) throw new AssertionError("a forgotten relic is still drawn from its art");
        });
        shot(ctx, "relic-06-art-gone", 20);
    }
}
