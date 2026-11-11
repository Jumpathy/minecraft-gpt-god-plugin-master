package net.bigyous.gptgod.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import net.bigyous.gptgod.client.GodClientMod;
import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.fx.DemoScenes;
import net.bigyous.gptgod.client.net.Protocol;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * End to end against a real Paper server running the GPTGOD plugin (with its debug bridge on and
 * brain.mode external). Runs only when the environment variable GPTGOD_E2E is set to
 * "host:port|bridgePort|bridgeToken", e.g. "127.0.0.1:25599|8799|e2e-test". The client joins, the mod
 * says hello, and the test makes the god act through the bridge and checks what the mod received.
 */
public class E2ETest implements FabricClientGameTest {
    private static String bridge;
    private static String token;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    static boolean enabled() {
        String e = System.getenv("GPTGOD_E2E");
        return e != null && !e.isBlank();
    }

    private static String post(String route, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(bridge + route)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        String body = res.body().length() > 400 ? res.body().substring(0, 400) + "…" : res.body();
        System.out.println("[gptgod-e2e] " + route + " " + json.substring(0, Math.min(90, json.length())) + " -> " + res.statusCode() + " " + body);
        return res.body();
    }

    private static String tool(String name, String input) throws Exception {
        return post("/tool", "{\"name\":\"" + name + "\",\"input\":" + input + "}");
    }

    private static void check(String what, boolean ok) {
        System.out.println("[gptgod-e2e] " + (ok ? "PASS " : "FAIL ") + what);
        if (!ok) throw new AssertionError(what);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!enabled()) return;
        try {
            run(ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void run(ClientGameTestContext ctx) throws Exception {
        String[] parts = System.getenv("GPTGOD_E2E").split("\\|");
        String address = parts[0];
        bridge = "http://127.0.0.1:" + parts[1];
        token = parts[2];

        ctx.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                new ServerData("gptgod e2e", address, ServerData.Type.OTHER), false, null));
        ctx.waitFor(mc -> mc.player != null && mc.level != null, 20 * 60);
        // The server offers the god's texture pack: accept it, as a player would.
        for (int i = 0; i < 10; i++) {
            ctx.waitTicks(10);
            if (ctx.tryClickScreenButton("gui.yes")) break;
        }
        ctx.waitTicks(100);
        String me = ctx.computeOnClient(mc -> mc.player.getGameProfile().name());
        post("/console", "{\"command\":\"time set noon\"}");
        post("/console", "{\"command\":\"gamemode creative " + me + "\"}");
        post("/console", "{\"command\":\"tp " + me + " 0.5 -60 0.5 0 0\"}");
        ctx.waitTicks(40);

        // 1. The handshake: the plugin welcomed us and granted features.
        check("mod is linked to the plugin (hello → welcome)", ctx.computeOnClient(mc -> GodClientMod.STATE.linked()));
        check("fx, hud and sky were granted", ctx.computeOnClient(mc -> GodClientMod.STATE.has("fx") && GodClientMod.STATE.has("hud")
                && GodClientMod.STATE.has("sky")));

        // 2. The god's state reaches the HUD.
        tool("vessel_go", "{\"target\":\"0.5 -60 8.5\",\"mode\":\"teleport\"}");
        ctx.waitTicks(120);
        Protocol.God god = ctx.computeOnClient(mc -> GodClientMod.STATE.main());
        check("the god's state arrived", god != null);
        System.out.println("[gptgod-e2e] god = " + god);
        ctx.runOnClient(mc -> GodClientMod.config.hud = ModConfig.HudMode.FULL);
        ctx.waitTicks(5);
        ctx.takeScreenshot("e2e-1-vessel-and-hud");

        // 3. Its mood changes the sky.
        tool("vessel_mood", "{\"mood\":\"wrathful\"}");
        ctx.waitTicks(100);
        Protocol.God wrath = ctx.computeOnClient(mc -> GodClientMod.STATE.main());
        check("the mood change arrived (wrathful)", wrath != null && "wrathful".equals(wrath.mood()));
        check("the sky mood is active", ctx.computeOnClient(mc -> GodClientMod.SKY.active()));
        ctx.takeScreenshot("e2e-2-wrathful-sky");
        ctx.runOnClient(mc -> GodClientMod.config.hud = ModConfig.HudMode.AUTO);

        // 4. The god casts a scene it "wrote": the plugin validates it and sends it to us.
        String layers = com.google.gson.JsonParser.parseString(DemoScenes.ALL.get("judgement")).getAsJsonObject().get("layers").toString();
        String cast = tool("fx_cast", "{\"at\":\"0.5 -60 10.5\",\"scene\":" + quote(layers) + ",\"save_as\":\"judgement\",\"note\":\"for the faithless\"}");
        check("fx_cast succeeded and counted us as a full viewer", cast.contains("1 mortal(s) see it in full"));
        ctx.waitTicks(10);
        check("the scene is running on the client", ctx.computeOnClient(mc -> GodClientMod.SCENES.count() > 0));
        ctx.waitTicks(50);
        ctx.takeScreenshot("e2e-3-fx-cast-judgement");
        check("fake blocks were launched", ctx.computeOnClient(mc -> GodClientMod.WORLD.pieces() > 0));
        ctx.waitTicks(80);

        // 5. It replays it from its library, as a private vision for us.
        String replay = tool("fx_replay", "{\"name\":\"judgement\",\"at\":\"0.5 -60 10.5\",\"only\":" + quote(me) + "}");
        check("fx_replay found the saved effect", replay.contains("Only " + me + " sees it"));
        ctx.waitTicks(10);
        check("the replayed scene is running", ctx.computeOnClient(mc -> GodClientMod.SCENES.count() > 0));
        ctx.waitTicks(60);

        // 6. A bad scene is refused by the plugin with a reason for the god.
        String bad = tool("fx_cast", "{\"at\":\"0.5 -60 10.5\",\"scene\":\"[{\\\"type\\\":\\\"laser\\\"}]\"}");
        check("an invalid scene is refused with an explanation", bad.contains("unknown type"));

        // 7. A built-in power: screen effects from the plugin's hooks.
        tool("earthquake", "{\"target\":\"0.5 -60 4.5\",\"radius\":8,\"seconds\":3}");
        ctx.waitTicks(15);
        check("the earthquake shakes the screen", ctx.computeOnClient(mc -> GodClientMod.SCREEN.busy()));
        ctx.takeScreenshot("e2e-4-earthquake");
        ctx.waitTicks(60);

        tool("vessel_mood", "{\"mood\":\"calm\"}");
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> mc.disconnectFromWorld(net.minecraft.network.chat.Component.literal("e2e done")));
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
        ctx.waitTicks(5);
        System.out.println("[gptgod-e2e] ALL PASSED");
    }
}
