package net.bigyous.gptgod.test;

import net.bigyous.gptgod.client.gui.GuideClient;
import net.bigyous.gptgod.client.gui.GuideScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Opens the guide screen on the sample (an operator's copy, the protocol's golden file) in a real client
 * and saves a screenshot of a few chapters. Runs only with the environment variable GPTGOD_GUIDE set
 * (the other shot tests then skip), so the guide can be checked alone.
 */
public class GuideShotsTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (System.getenv("GPTGOD_GUIDE") == null) return;
        ctx.getInput().resizeWindow(1920, 1080);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("time set noon");
            scale(ctx, 3);
            ctx.runOnClient(mc -> GuideClient.openSample());
            ctx.waitForScreen(GuideScreen.class);
            String[][] chapters = { { "0", "01-what-the-gods-are" }, { "1", "02-speaking" }, { "3", "03-oaths" },
                    { "5", "04-living-land" }, { "8", "05-quick-reference" }, { "9", "06-op-starting" },
                    { "12", "07-op-commanding" }, { "15", "08-op-config" } };
            for (String[] c : chapters) {
                ctx.runOnClient(mc -> GuideClient.chapter(Integer.parseInt(c[0])));
                shot(ctx, "guide-" + c[1]);
            }
            // Hovering a command: it can be typed into chat with a click.
            ctx.runOnClient(mc -> GuideClient.chapter(1));
            double s = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
            int[] size = ctx.computeOnClient(mc -> new int[] { mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight() });
            int w = Math.min(size[0] - 12, 600), h = Math.min(size[1] - 12, 340);
            int x0 = (size[0] - w) / 2, y0 = (size[1] - h) / 2;
            ctx.getInput().setCursorPos((x0 + 128 + 60) * s, (y0 + 18 + 12) * s);
            shot(ctx, "guide-09-hover-command");
            ctx.getInput().setCursorPos(5, 5);
            scale(ctx, 4);
            ctx.runOnClient(mc -> GuideClient.chapter(11));
            shot(ctx, "guide-10-op-gods-scale4");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
        }
    }

    private static void scale(ClientGameTestContext ctx, int scale) {
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
        ctx.waitTicks(3);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.waitTicks(4);
        ctx.takeScreenshot(name);
    }
}
