package net.bigyous.gptgod.test;

import net.bigyous.gptgod.client.gui.GuiClient;
import net.bigyous.gptgod.client.gui.GuiModel;
import net.bigyous.gptgod.client.gui.ManagerScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Opens the God Manager screen on sample data in a real client and saves a screenshot of every page,
 * plus the moments worth seeing: a hover card, an error under a field, a typed-name confirmation, a
 * button asking "Confirm?" and a refused config value.
 */
public class ManagerShotsTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (System.getenv("GPTGOD_E2E") != null || System.getenv("GPTGOD_VIDEO") != null || System.getenv("GPTGOD_GUIDE") != null) return; // the end-to-end run has its own test
        ctx.getInput().resizeWindow(1920, 1080);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("time set noon");
            scale(ctx, 3);
            ctx.runOnClient(mc -> GuiClient.openSample());
            ctx.waitForScreen(ManagerScreen.class);
            GuiModel m = GuiClient.MODEL;
            shot(ctx, "manager-01-pantheon");

            // The hover card of a god in the sidebar.
            int[] at = ctx.computeOnClient(mc -> ((ManagerScreen) mc.gui.screen()).marks.get("side.main"));
            double s = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
            ctx.getInput().setCursorPos((at[0] + 70) * s, (at[1] + 10) * s);
            shot(ctx, "manager-02-hover-card");
            ctx.getInput().setCursorPos(5, 5);

            String[][] pages = { { GuiModel.OVERVIEW, "03-overview" }, { GuiModel.COMMANDS, "04-commands" },
                    { GuiModel.MORTALS, "05-mortals" }, { GuiModel.NATURE, "06-nature" }, { GuiModel.BODY, "07-body" },
                    { GuiModel.MIND, "08-mind" }, { GuiModel.JOURNAL, "09-journal" }, { GuiModel.CREATE, "10-new-god" },
                    { GuiModel.SWITCHES, "11-switches" }, { GuiModel.CONFIG, "12-config" }, { GuiModel.COSTS, "13-costs" } };
            for (String[] page : pages) {
                ctx.runOnClient(mc -> m.go(page[0], "main"));
                shot(ctx, "manager-" + page[1]);
            }

            // An invalid god name: the server's own words under the name box.
            ctx.runOnClient(mc -> {
                m.go(GuiModel.CREATE, null);
                screen(mc).type("c.name", "Zeus");
                GuiClient.act("create", null, "create_god", null, "name", "Zeus", "archetype", "random", "power", "greater");
            });
            shot(ctx, "manager-14-new-god-error");

            // Erasing a god: its name must be typed.
            ctx.runOnClient(mc -> {
                m.go(GuiModel.MIND, "main");
                screen(mc).type("end.name", "Vael");
                GuiClient.act("end", "main", "rebirth", "Vael", "archetype", "random");
            });
            shot(ctx, "manager-15-type-the-name");

            // Forgetting a mortal: the button asks once more.
            ctx.runOnClient(mc -> {
                m.go(GuiModel.MORTALS, "main");
                screen(mc).ui().scrollTo("m.editor", 9999);
                screen(mc).arm("m.fg.Korvath");
            });
            shot(ctx, "manager-16-confirm-in-place");

            // A config value out of range: refused by the server, shown under the setting.
            ctx.runOnClient(mc -> {
                m.go(GuiModel.CONFIG, null);
                screen(mc).type("cfg.search", "heartbeat");
            });
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> {
                screen(mc).type("cfg.box.brain.heartbeat-seconds", "9999");
                GuiClient.act("cfg.brain.heartbeat-seconds", null, "config_set", null, "key", "brain.heartbeat-seconds",
                        "value", "9999");
            });
            shot(ctx, "manager-17-config-error");

            // A slain god: its page offers to revive it.
            ctx.runOnClient(mc -> {
                m.go(GuiModel.OVERVIEW, "morth");
                GuiClient.act("ov", "morth", "revive", null);
            });
            shot(ctx, "manager-21-revive");

            // The same screen on a large GUI scale (480 x 270 GUI pixels): everything must still fit.
            scale(ctx, 4);
            for (String[] page : new String[][] { { GuiModel.COMMANDS, "18-commands-scale4" },
                    { GuiModel.MORTALS, "19-mortals-scale4" }, { GuiModel.BODY, "20-body-scale4" } }) {
                ctx.runOnClient(mc -> m.go(page[0], "main"));
                shot(ctx, "manager-" + page[1]);
            }
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
        }
    }

    private static ManagerScreen screen(net.minecraft.client.Minecraft mc) {
        return (ManagerScreen) mc.gui.screen();
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
