package net.bigyous.gptgod.client.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import net.bigyous.gptgod.client.GodClientMod;
import net.bigyous.gptgod.client.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * The God Manager screen's link to the plugin: takes the gui_* messages, keeps the {@link GuiModel}, and
 * sends the screen's requests. The screen opens only when the server says so (gui_open), which it does
 * only for operators: the key merely asks.
 */
public final class GuiClient {
    private GuiClient() {
    }

    public static final GuiModel MODEL = new GuiModel();
    /** Showing sample data (/godfx manager, the screenshot test): nothing is sent, every action "works". */
    private static boolean offline;
    private static long askedAt;

    /** Handles a gptgod:main message if it is one of the screen's. True when it was. */
    public static boolean receive(byte[] data) {
        if (!GuiProtocol.mine(data)) return false;
        Object m = GuiProtocol.decode(data);
        if (m == null) return true;
        if (offline) return true;
        long now = System.currentTimeMillis();
        boolean asked = now - askedAt < 5000;
        if (m instanceof GuiProtocol.Result r && asked && !r.ok() && !(Minecraft.getInstance().gui.screen() instanceof ManagerScreen)) {
            // The key was pressed and the server said no (not an operator).
            askedAt = 0;
            toast("God Manager", r.error() == null ? "The server refused." : r.error());
            return true;
        }
        if (MODEL.accept(m, now) != null) show();
        return true;
    }

    /** The key: asks the server to open the screen (it does so only for operators). */
    public static void keyPressed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return;
        if (offline) {
            show();
            return;
        }
        if (!GodClientMod.STATE.has("gui")) {
            if (GodClientMod.STATE.linked()) toast("God Manager", "This server's GPTGOD plugin has no manager screen.");
            return;
        }
        askedAt = System.currentTimeMillis();
        send(GuiProtocol.open(MODEL.id()));
    }

    private static void show() {
        Minecraft mc = Minecraft.getInstance();
        if (MODEL.history.isEmpty()) MODEL.history.addAll(GodClientMod.config.commandHistory);
        if (!(mc.gui.screen() instanceof ManagerScreen)) mc.gui.setScreen(new ManagerScreen());
    }

    private static void toast(String title, String text) {
        SystemToast.add(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal(title), Component.literal(text));
    }

    static void send(byte[] message) {
        if (offline || Minecraft.getInstance().getConnection() == null) return;
        if (!ClientPlayNetworking.canSend(Payloads.Main.TYPE)) return;
        ClientPlayNetworking.send(new Payloads.Main(message));
    }

    /** Asks the plugin to do something; the answer is shown at `slot`. args: key, value, key, value... */
    public static void act(String slot, String realm, String action, String confirm, String... args) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) map.put(args[i], args[i + 1]);
        long now = System.currentTimeMillis();
        String id = MODEL.ask(slot, action, now);
        if (offline) {
            MODEL.accept(GuiSamples.answer(id, action, map, confirm), now);
            return;
        }
        send(GuiProtocol.act(id, realm, action, map, confirm));
    }

    static void subscribe(String realm, String part, Map<String, String> args) {
        send(GuiProtocol.sub(MODEL.id(), realm, part, args));
    }

    static void closed() {
        send(GuiProtocol.close());
    }

    /** A command was sent: keep it for Up/Down, also across restarts. */
    static void remember(String command) {
        MODEL.remember(command);
        GodClientMod.config.commandHistory = new java.util.ArrayList<>(MODEL.history);
        GodClientMod.saveConfig();
    }

    /** Left the server: forget its gods. */
    public static void disconnected() {
        offline = false;
        MODEL.reset();
    }

    /** Opens the screen on sample data, with no server involved (/godfx manager and the screenshot test). */
    public static void openSample() {
        offline = true;
        MODEL.reset();
        GuiSamples.fill(MODEL);
        show();
    }

    public static boolean offline() {
        return offline;
    }
}
