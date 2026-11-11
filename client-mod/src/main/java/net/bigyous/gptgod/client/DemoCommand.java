package net.bigyous.gptgod.client;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.bigyous.gptgod.client.fx.ScreenFx;
import net.bigyous.gptgod.client.net.Protocol;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * /godfx: client-only commands to try the mod anywhere (also singleplayer), with no server involved.
 * demo plays an effect the way the plugin would, sky and hud show a pretend god, relics loads sample
 * relic art, settings opens the settings, clear stops the pretending.
 */
public final class DemoCommand {
    private DemoCommand() {
    }

    static final List<String> EFFECTS = List.of("smite", "trueform", "meteor", "quake", "slain", "wound", "wrath",
            "arrival", "shake", "flash", "vignette", "pulse");
    static final List<String> MOODS = List.of("calm", "wrathful", "joyful", "sorrowful", "ominous", "exalted", "slain");

    private static boolean previewing;
    static boolean openSettings;
    /** /godfx manager: open the God Manager screen on sample data once the chat screen has closed. */
    static boolean openManager;
    /** /godfx guide: open the guide screen on the sample (an operator's copy) once the chat screen has closed. */
    static boolean openGuide;

    public static boolean previewing() {
        return previewing;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(ClientCommands.literal("godfx")
                .then(ClientCommands.literal("demo").then(ClientCommands.argument("effect", StringArgumentType.word())
                        .suggests((c, b) -> {
                            EFFECTS.forEach(b::suggest);
                            return b.buildFuture();
                        }).executes(c -> demo(c, StringArgumentType.getString(c, "effect")))))
                .then(ClientCommands.literal("sky").then(ClientCommands.argument("mood", StringArgumentType.word())
                        .suggests((c, b) -> {
                            MOODS.forEach(b::suggest);
                            return b.buildFuture();
                        }).executes(c -> pretend(c, StringArgumentType.getString(c, "mood")))))
                .then(ClientCommands.literal("scene").then(ClientCommands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> {
                            net.bigyous.gptgod.client.fx.DemoScenes.ALL.keySet().forEach(b::suggest);
                            return b.buildFuture();
                        }).executes(c -> scene(c, net.bigyous.gptgod.client.fx.DemoScenes.ALL.get(StringArgumentType.getString(c, "name"))))))
                .then(ClientCommands.literal("json").then(ClientCommands.argument("scene", StringArgumentType.greedyString())
                        .executes(c -> scene(c, StringArgumentType.getString(c, "scene")))))
                .then(ClientCommands.literal("say").then(ClientCommands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> {
                            SubtitleDemo.KINDS.forEach(b::suggest);
                            return b.buildFuture();
                        }).executes(c -> {
                            if (SubtitleDemo.play(StringArgumentType.getString(c, "kind"), System.currentTimeMillis(), 0)) return 1;
                            c.getSource().sendError(Component.literal("Try: " + String.join(", ", SubtitleDemo.KINDS)));
                            return 0;
                        })))
                .then(ClientCommands.literal("sound").then(ClientCommands.argument("id", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            net.bigyous.gptgod.client.fx.SoundBank.ids().forEach(b::suggest);
                            return b.buildFuture();
                        }).executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            String id = StringArgumentType.getString(c, "id").trim();
                            if (mc.player == null || !net.bigyous.gptgod.client.fx.SynthSounds.play(id, mc.player.getX(), mc.player.getEyeY(),
                                    mc.player.getZ(), 1, 1)) {
                                c.getSource().sendError(Component.literal("Unknown sound. Try: " + String.join(", ", net.bigyous.gptgod.client.fx.SoundBank.ids())));
                                return 0;
                            }
                            return 1;
                        })))
                .then(ClientCommands.literal("hud").executes(c -> pretend(c, "wrathful")))
                .then(ClientCommands.literal("relics").executes(DemoCommand::relics))
                .then(ClientCommands.literal("clear").executes(DemoCommand::clear))
                .then(ClientCommands.literal("guide").executes(c -> {
                    openGuide = true;
                    return 1;
                }))
                .then(ClientCommands.literal("manager").executes(c -> {
                    openManager = true;
                    return 1;
                }))
                .then(ClientCommands.literal("settings").executes(c -> {
                    openSettings = true;
                    return 1;
                })));
    }

    private static int demo(CommandContext<FabricClientCommandSource> c, String effect) {
        if (!play(effect, System.currentTimeMillis())) {
            c.getSource().sendError(Component.literal("Unknown effect. Try: " + String.join(", ", EFFECTS)));
            return 0;
        }
        c.getSource().sendFeedback(Component.literal("✦ " + effect));
        return 1;
    }

    /** The same effects the plugin sends (see the plugin's ModFx), at full strength on you. */
    public static boolean play(String effect, long now) {
        ScreenFx s = GodClientMod.SCREEN;
        switch (effect) {
            case "smite" -> {
                s.add(ScreenFx.Kind.FLASH, 0.9f, 500, 0xFFF4D6, now);
                s.add(ScreenFx.Kind.SHAKE, 0.8f, 800, 0xFFFFFF, now);
                s.add(ScreenFx.Kind.VIGNETTE, 0.8f, 3000, 0x8A0010, now);
            }
            case "trueform" -> {
                s.add(ScreenFx.Kind.PULSE, 0.8f, 2000, 0xFFE9A8, now);
                s.add(ScreenFx.Kind.SHAKE, 0.4f, 1500, 0xFFFFFF, now);
            }
            case "meteor" -> {
                s.add(ScreenFx.Kind.SHAKE, 1f, 1200, 0xFFFFFF, now);
                s.add(ScreenFx.Kind.FLASH, 0.6f, 400, 0xFFB050, now);
            }
            case "quake" -> s.add(ScreenFx.Kind.SHAKE, 0.55f, 5000, 0xFFFFFF, now);
            case "slain" -> {
                s.add(ScreenFx.Kind.FLASH, 1f, 1500, 0xFFFFFF, now);
                s.add(ScreenFx.Kind.SHAKE, 1f, 2500, 0xFFFFFF, now);
                s.add(ScreenFx.Kind.VIGNETTE, 0.6f, 8000, 0x1A0F24, now);
            }
            case "wound" -> {
                s.add(ScreenFx.Kind.SHAKE, 0.35f, 400, 0xFFFFFF, now);
                s.add(ScreenFx.Kind.FLASH, 0.3f, 200, 0xFF3040, now);
            }
            case "wrath" -> {
                s.add(ScreenFx.Kind.SHAKE, 0.7f, 600, 0xFFFFFF, now);
                s.add(ScreenFx.Kind.FLASH, 0.5f, 250, 0xE8F0FF, now);
            }
            case "arrival" -> s.add(ScreenFx.Kind.PULSE, 0.5f, 1500, 0xD9A6FF, now);
            case "shake" -> s.add(ScreenFx.Kind.SHAKE, 1f, 1500, 0xFFFFFF, now);
            case "flash" -> s.add(ScreenFx.Kind.FLASH, 1f, 800, 0xFFFFFF, now);
            case "vignette" -> s.add(ScreenFx.Kind.VIGNETTE, 1f, 3000, 0x8A0010, now);
            case "pulse" -> s.add(ScreenFx.Kind.PULSE, 1f, 2000, 0xFFE9A8, now);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Loads the sample relics' art as if the plugin had sent it; each line fills in the command that hands one over. */
    private static int relics(CommandContext<FabricClientCommandSource> c) {
        c.getSource().sendFeedback(Component.literal("✦ Sample relics loaded. Click one for the command that gives it (needs cheats):"));
        for (String give : net.bigyous.gptgod.client.relic.Relics.demo()) {
            c.getSource().sendFeedback(Component.literal("  " + give)
                    .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(give))));
        }
        return 1;
    }

    private static int scene(CommandContext<FabricClientCommandSource> c, String json) {
        if (json == null || !playScene(json, 8)) {
            c.getSource().sendError(Component.literal("Not a scene. Names: " + String.join(", ", net.bigyous.gptgod.client.fx.DemoScenes.ALL.keySet())));
            return 0;
        }
        return 1;
    }

    /** Plays scene JSON a few blocks in front of the player. */
    public static boolean playScene(String json, double ahead) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (!o.has("at") && !o.has("follow")) {
                var look = mc.player.getLookAngle();
                com.google.gson.JsonArray at = new com.google.gson.JsonArray();
                at.add(mc.player.getX() + look.x * ahead);
                at.add(mc.player.getY());
                at.add(mc.player.getZ() + look.z * ahead);
                o.add("at", at);
            }
            return GodClientMod.SCENES.play(net.bigyous.gptgod.client.fx.Scene.decode(o));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int pretend(CommandContext<FabricClientCommandSource> c, String mood) {
        if (!MOODS.contains(mood)) {
            c.getSource().sendError(Component.literal("Unknown mood. Try: " + String.join(", ", MOODS)));
            return 0;
        }
        pretendGod(mood, true);
        c.getSource().sendFeedback(Component.literal("✦ Pretending a " + mood + " god stands before you. /godfx clear to stop."));
        return 1;
    }

    /** Fills the state with a pretend god a few blocks in front of the player. */
    public static void pretendGod(String mood, boolean nearby) {
        previewing = true;
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        double[] pos = null;
        if (nearby && mc.player != null) {
            var look = mc.player.getLookAngle();
            pos = new double[] { mc.player.getX() + look.x * 6, mc.player.getY() + 1, mc.player.getZ() + look.z * 6 };
        }
        boolean slain = "slain".equals(mood);
        GodClientMod.STATE.accept(new Protocol.Welcome(Protocol.VERSION, "demo", List.of("fx", "hud", "sky")), now);
        GodClientMod.STATE.accept(new Protocol.God("demo", "Vel'Azhar", "the Unquiet Flame", "wrathful",
                slain ? 0x8A8A90 : 0xFF5A30, slain ? "calm" : mood,
                new Protocol.Heart(mood.equals("wrathful") ? 78 : 12, mood.equals("joyful") ? 70 : 5,
                        mood.equals("sorrowful") ? 64 : 8, mood.equals("ominous") ? 58 : 14,
                        mood.equals("exalted") ? 81 : 20),
                slain ? "slain" : "walks", !slain, slain ? 0 : 62, 100, pos, true), now);
        GodClientMod.STATE.accept(new Protocol.Standing("demo", "favored", "Flamebearer", "gold", "halo"), now);
        GodClientMod.STATE.accept(new Protocol.Quest("demo", "Vel'Azhar", "Raise a tower of gold on the eastern hill", 35),
                now);
    }

    private static int clear(CommandContext<FabricClientCommandSource> c) {
        previewing = false;
        GodClientMod.STATE.unlink();
        GodClientMod.SCREEN.clear();
        GodClientMod.SCENES.clear();
        GodClientMod.POST.clear();
        GodClientMod.CAMERA.clear();
        GodClientMod.WORLD.clear();
        GodClientMod.SKY.clearTarget();
        net.bigyous.gptgod.client.hud.Subtitles.clear();
        GodClientMod.sayHello(true);
        c.getSource().sendFeedback(Component.literal("✦ Back to what the server says."));
        return 1;
    }
}
