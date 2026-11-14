package net.bigyous.gptgod.client;

import java.nio.file.Path;

import com.mojang.blaze3d.platform.InputConstants;

import net.bigyous.gptgod.client.config.ConfigScreen;
import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.fx.ScreenFx;
import net.bigyous.gptgod.client.fx.SkyMood;
import net.bigyous.gptgod.client.hud.GodHud;
import net.bigyous.gptgod.client.hud.Overlays;
import net.bigyous.gptgod.client.net.Payloads;
import net.bigyous.gptgod.client.net.Protocol;
import net.bigyous.gptgod.client.state.GodState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The GPTGOD client mod. On a server running GPTGOD it says hello on gptgod:hello; once the plugin
 * welcomes it, it draws what the plugin sends. On any other server it stays silent and draws nothing.
 */
public final class GodClientMod implements ClientModInitializer {
    public static final String ID = "gptgod";

    public static ModConfig config = new ModConfig();
    public static final GodState STATE = new GodState();
    public static final ScreenFx SCREEN = new ScreenFx();
    public static final SkyMood SKY = new SkyMood();
    public static final net.bigyous.gptgod.client.fx.CameraFx CAMERA = new net.bigyous.gptgod.client.fx.CameraFx();
    public static final net.bigyous.gptgod.client.fx.WorldFx WORLD = new net.bigyous.gptgod.client.fx.WorldFx();
    public static final net.bigyous.gptgod.client.fx.PostFx POST = new net.bigyous.gptgod.client.fx.PostFx();
    public static final net.bigyous.gptgod.client.fx.SceneRunner SCENES = new net.bigyous.gptgod.client.fx.SceneRunner();
    /** How loud each god's voice is right now (subtitles; later the body's lips). */
    public static final net.bigyous.gptgod.client.state.VoiceLevel VOICE = new net.bigyous.gptgod.client.state.VoiceLevel();

    private static Path configFile;
    public static KeyMapping hudKey;
    private static KeyMapping settingsKey;
    private static KeyMapping managerKey;
    private static ClientLevel lastLevel;
    private static boolean helloSent;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(ID).map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0");
    }

    @Override
    public void onInitializeClient() {
        configFile = FabricLoader.getInstance().getConfigDir().resolve("gptgod-client.json");
        config = ModConfig.load(configFile);

        PayloadTypeRegistry.clientboundPlay().register(Payloads.Main.TYPE, Payloads.Main.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Main.TYPE, Payloads.Main.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Hello.TYPE, Payloads.Hello.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Main.TYPE, (payload, context) -> receive(payload.data()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            helloSent = false;
            STATE.unlink();
            SCREEN.clear();
            sayHello(false);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            net.bigyous.gptgod.client.gui.GuiClient.disconnected();
            net.bigyous.gptgod.client.gui.GuideClient.disconnected();
            net.bigyous.gptgod.client.relic.Relics.clear();
            helloSent = false;
            STATE.unlink();
            SCREEN.clear();
            SKY.clearTarget();
        });

        KeyMapping.Category category = KeyMapping.Category.register(id("keys"));
        hudKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.gptgod.hud", InputConstants.KEY_H, category));
        settingsKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.gptgod.settings", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));

        managerKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.gptgod.manager", InputConstants.KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(GodClientMod::tick);

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("overlays"), Overlays::draw);
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, id("panel"), GodHud::draw);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, id("subtitles"),
                net.bigyous.gptgod.client.hud.Subtitles::draw);

        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(WORLD::submit);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> DemoCommand.register(dispatcher));
        net.bigyous.gptgod.client.puppet.Puppet.start(); // test-only remote control; does nothing without -Dgptgod.puppet.port
    }

    /**
     * Sends the hello: once per connection, again when again is true. Only to servers that registered
     * gptgod:hello, unless pinged (a ping proves the server runs GPTGOD).
     */
    public static void sayHello(boolean again) {
        sayHello(again, false);
    }

    static void sayHello(boolean again, boolean pinged) {
        if (helloSent && !again) return;
        if (Minecraft.getInstance().getConnection() == null) return;
        if (!pinged && !ClientPlayNetworking.canSend(Payloads.Hello.TYPE)) return;
        helloSent = true;
        ClientPlayNetworking.send(new Payloads.Hello(Protocol.hello(version(), config.features())));
    }

    /** Tells the plugin that one of a scene's triggers fired for this player (key: the plugin's name for it). */
    public static void triggered(String key) {
        if (key == null || key.isEmpty() || Minecraft.getInstance().getConnection() == null) return;
        if (!ClientPlayNetworking.canSend(Payloads.Main.TYPE)) return;
        ClientPlayNetworking.send(new Payloads.Main(("{\"t\":\"trigger\",\"key\":\"" + key + "\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static void receive(byte[] data) {
        if (net.bigyous.gptgod.client.gui.GuiClient.receive(data)) return; // the God Manager screen's messages
        if (net.bigyous.gptgod.client.relic.Relics.receive(data)) return; // a relic's art, or word that one is gone
        if (net.bigyous.gptgod.client.fx.FxTextures.receive(data)) return; // a texture a god painted for its effects
        if (net.bigyous.gptgod.client.gui.GuideClient.receive(data)) return; // the guide to the gods
        Protocol.Message m = Protocol.decode(data);
        long now = System.currentTimeMillis();
        switch (m) {
            case Protocol.Ping p -> sayHello(true, true);
            case Protocol.Welcome w -> {
                boolean was = STATE.linked();
                STATE.accept(w, now);
                if (!was) toast(w);
            }
            case Protocol.Screen s -> {
                if (STATE.has("fx")) SCREEN.add(s, you(), now);
            }
            case Protocol.Play p -> {
                if (STATE.has("fx")) SCENES.play(p.scene());
            }
            case Protocol.Stop s -> {
                if (s.id().isEmpty()) SCENES.clear();
                else SCENES.stop(s.id());
            }
            case Protocol.Tone t -> {
                if (STATE.has("fx") && t.at() != null) {
                    net.bigyous.gptgod.client.fx.SynthSounds.play(t.id(), t.at()[0], t.at()[1], t.at()[2], t.volume(), t.pitch());
                }
            }
            case Protocol.Say s -> {
                if (config.subtitles) net.bigyous.gptgod.client.hud.Subtitles.say(s, now);
            }
            case Protocol.Voice v -> {
                if (config.subtitles) net.bigyous.gptgod.client.hud.Subtitles.voice(v, now);
            }
            default -> STATE.accept(m, now);
        }
    }

    private static void toast(Protocol.Welcome w) {
        Minecraft mc = Minecraft.getInstance();
        if (w.accepted()) {
            SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("✦ The god sees you"), Component.literal("GPTGOD client effects are on."));
        } else if (w.protocol() != Protocol.VERSION) {
            SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("GPTGOD mod out of date"),
                    Component.literal("This server speaks protocol " + w.protocol() + "; the mod is off here."));
        }
    }

    static double[] you() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return new double[] { mc.player.getX(), mc.player.getEyeY(), mc.player.getZ() };
    }

    private static void tick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            // A new world: forget the old world's gods (the server sends the new world's).
            STATE.clear();
            SCREEN.clear();
            SCENES.clear();
            POST.clear();
            CAMERA.clear();
            WORLD.clear();
            SKY.clearTarget();
            net.bigyous.gptgod.client.hud.Subtitles.clear();
        }
        while (managerKey.consumeClick()) {
            net.bigyous.gptgod.client.gui.GuiClient.keyPressed();
        }
        while (settingsKey.consumeClick()) {
            mc.gui.setScreen(new ConfigScreen(mc.gui.screen()));
        }
        if (DemoCommand.openManager && mc.gui.screen() == null) {
            DemoCommand.openManager = false;
            net.bigyous.gptgod.client.gui.GuiClient.openSample();
        }
        if (DemoCommand.openGuide && mc.gui.screen() == null) {
            DemoCommand.openGuide = false;
            net.bigyous.gptgod.client.gui.GuideClient.openSample();
        }
        if (DemoCommand.openSettings && mc.gui.screen() == null) {
            DemoCommand.openSettings = false;
            mc.gui.setScreen(new ConfigScreen(null));
        }
        Protocol.God g = STATE.main();
        if (g != null && (STATE.has("sky") || DemoCommand.previewing())) {
            SKY.target(g.mood(), g.state(), g.pos(), g.here(), (float) config.sky);
        } else {
            SKY.clearTarget();
        }
        SKY.tick();
        SCENES.tick(mc);
        WORLD.tick(mc.level);
    }

    public static void saveConfig() {
        config.clamped();
        if (configFile != null) config.save(configFile);
    }

    /** Settings changed what we asked for: say hello again so the server sends the new set. */
    public static void featuresChanged() {
        if (helloSent) {
            STATE.unlink();
            sayHello(true);
        }
    }
}
