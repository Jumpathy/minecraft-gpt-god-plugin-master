package net.bigyous.gptgod.test;

import net.bigyous.gptgod.client.DemoCommand;
import net.bigyous.gptgod.client.GodClientMod;
import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.fx.DemoScenes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Starts a real client in a test world, shows a pretend god in each mood, each effect and scene, saves screenshots. */
public class ShotsTest implements FabricClientGameTest {
    /** The god's spoken lines as subtitles: each kind caught mid-sentence, a long line, and the settings. */
    private static void subtitles(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> GodClientMod.config.hud = ModConfig.HudMode.HIDDEN);
        String[][] shots = { { "whisper", "2300" }, { "announce", "3000" }, { "vessel", "1500" }, { "vessel", "3300" },
                { "long", "5200" }, { "long", "9500" } };
        for (String[] shot : shots) {
            ctx.runOnClient(mc -> {
                net.bigyous.gptgod.client.hud.Subtitles.clear();
                net.bigyous.gptgod.client.SubtitleDemo.play(shot[0], System.currentTimeMillis(), Long.parseLong(shot[1]) - 150);
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot("subs-" + shot[0] + "-" + shot[1]);
        }
        ctx.runOnClient(mc -> net.bigyous.gptgod.client.hud.Subtitles.clear());
        ctx.runOnClient(mc -> mc.gui.setScreen(new net.bigyous.gptgod.client.config.ConfigScreen(null)));
        ctx.waitTicks(3);
        ctx.takeScreenshot("subs-settings");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(2);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (System.getenv("GPTGOD_E2E") != null || System.getenv("GPTGOD_VIDEO") != null || System.getenv("GPTGOD_GUIDE") != null) return; // the end-to-end run has its own test
        boolean scenesOnly = Boolean.getBoolean("gptgod.scenesOnly");
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("weather clear");
            ctx.waitTicks(20);
            if (Boolean.getBoolean("gptgod.subsOnly")) {
                subtitles(ctx);
                return;
            }
            if (!scenesOnly) {
                subtitles(ctx);
                ctx.takeScreenshot("00-vanilla");
                for (String mood : new String[] { "calm", "wrathful", "joyful", "sorrowful", "ominous", "exalted", "slain" }) {
                    ctx.runOnClient(mc -> {
                        DemoCommand.pretendGod(mood, true);
                        GodClientMod.config.hud = ModConfig.HudMode.FULL;
                    });
                    ctx.waitTicks(3);
                    ctx.runOnClient(mc -> GodClientMod.SKY.settle());
                    ctx.waitTicks(5);
                    ctx.takeScreenshot("sky-" + mood);
                }
                ctx.runOnClient(mc -> GodClientMod.config.hud = ModConfig.HudMode.COMPACT);
                ctx.waitTicks(3);
                ctx.takeScreenshot("hud-compact");
                for (String fx : new String[] { "smite", "trueform", "meteor", "slain", "vignette", "pulse" }) {
                    ctx.runOnClient(mc -> {
                        GodClientMod.SCREEN.clear();
                        DemoCommand.play(fx, System.currentTimeMillis());
                    });
                    ctx.waitTicks(4);
                    ctx.takeScreenshot("fx-" + fx);
                }
            }
            // Scenes in the effect language, with the HUD out of the way.
            ctx.runOnClient(mc -> {
                GodClientMod.STATE.unlink();
                GodClientMod.SCREEN.clear();
                GodClientMod.SKY.clearTarget();
                GodClientMod.config.hud = ModConfig.HudMode.HIDDEN;
                GodClientMod.config.shake = 0;
            });
            ctx.waitTicks(80);
            for (String name : DemoScenes.ALL.keySet()) {
                ctx.runOnClient(mc -> {
                    GodClientMod.SCENES.clear();
                    DemoCommand.playScene(DemoScenes.ALL.get(name), 14);
                });
                int at = 0;
                for (int next : new int[] { 12, 24, 40, 60 }) {
                    ctx.waitTicks(next - at);
                    at = next;
                    ctx.takeScreenshot("scene-" + name + "-t" + next);
                }
                ctx.waitTicks(70);
            }
        }
    }
}
