package net.bigyous.gptgod.test;

import net.bigyous.gptgod.client.DemoCommand;
import net.bigyous.gptgod.client.GodClientMod;
import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.fx.DemoScenes;
import net.bigyous.gptgod.client.fx.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Records each demo scene as a frame per tick (20 a second) for tools/make_videos.py to turn into
 * videos. Runs only when the environment variable GPTGOD_VIDEO is set (optionally to a comma-separated
 * list of scene names).
 */
public class VideoTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        String want = System.getenv("GPTGOD_VIDEO");
        if (want == null || want.isBlank()) return;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("weather clear");
            sp.getServer().runCommand("gamemode spectator @a");
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> {
                GodClientMod.STATE.unlink();
                GodClientMod.config.hud = ModConfig.HudMode.HIDDEN;
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                // A little above the ground, looking slightly up at where the scenes play.
                mc.player.setXRot(-8);
            });
            ctx.waitTicks(10);
            for (String name : DemoScenes.ALL.keySet()) {
                if (!want.equals("1") && !want.equals("all") && !("," + want + ",").contains("," + name + ",")) continue;
                int life = Scene.decode(com.google.gson.JsonParser.parseString(DemoScenes.ALL.get(name)).getAsJsonObject()).life();
                ctx.runOnClient(mc -> {
                    GodClientMod.SCENES.clear();
                    GodClientMod.WORLD.clear();
                    GodClientMod.POST.clear();
                    GodClientMod.SCREEN.clear();
                    GodClientMod.CAMERA.clear();
                });
                ctx.waitTicks(30);
                ctx.runOnClient(mc -> DemoCommand.playScene(DemoScenes.ALL.get(name), 16));
                int frames = Math.min(240, life + 50);
                // A screenshot costs the game a tick or two, so each frame is named by the tick it shows
                // and the encoder holds it until the next one.
                long start = ctx.computeOnClient(mc -> mc.level.getGameTime());
                long last = -1;
                while (true) {
                    long t = ctx.computeOnClient(mc -> mc.level.getGameTime()) - start;
                    if (t >= frames) break;
                    if (t == last) {
                        ctx.waitTick();
                        continue;
                    }
                    last = t;
                    ctx.takeScreenshot(String.format("video-%s-%04d", name, t));
                }
            }
            ctx.runOnClient(mc -> {
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            });
        }
    }
}
