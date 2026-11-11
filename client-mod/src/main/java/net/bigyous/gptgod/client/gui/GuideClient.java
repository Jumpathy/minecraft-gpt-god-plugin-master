package net.bigyous.gptgod.client.gui;

import java.io.IOException;
import java.io.InputStream;

import net.minecraft.client.Minecraft;

/**
 * The guide's link to the plugin: keeps the last guide it sent and opens {@link GuideScreen} when the
 * plugin says so (the player used the book, or typed /guide). The plugin sends it only to players whose
 * hello asked for "guide"; everyone else reads the written book.
 */
public final class GuideClient {
    private GuideClient() {
    }

    static GuideProtocol.Guide guide;
    /** The chapter last read, kept while the game runs. */
    static int chapter;

    /** Handles a gptgod:main message if it is the guide. True when it was. */
    public static boolean receive(byte[] data) {
        if (!GuideProtocol.mine(data)) return false;
        GuideProtocol.Guide g = GuideProtocol.decode(data);
        if (g == null) return true;
        guide = g;
        if (chapter >= g.chapters().size()) chapter = 0;
        if (g.open()) show();
        return true;
    }

    static void show() {
        if (guide == null) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new GuideScreen(guide));
    }

    /** Opens the screen on the sample guide (an operator's copy), with no server (/godfx guide, screenshots). */
    public static void openSample() {
        try (InputStream in = GuideClient.class.getResourceAsStream("/gptgod-samples/guide-operator.json")) {
            if (in == null) return;
            GuideProtocol.Guide g = GuideProtocol.decode(in.readAllBytes());
            if (g == null) return;
            guide = g;
            chapter = 0;
            show();
        } catch (IOException e) {
            // no sample, no screen
        }
    }

    /** Turns to a chapter (tests and previews). */
    public static void chapter(int index) {
        chapter = Math.max(0, index);
    }

    /** Left the server: its guide goes with it. */
    public static void disconnected() {
        guide = null;
        chapter = 0;
    }
}
