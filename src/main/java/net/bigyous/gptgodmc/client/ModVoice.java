package net.bigyous.gptgodmc.client;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModMessages.GodView;
import net.bigyous.gptgodmc.god.ClientViews;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.voice.Loudness;
import net.bigyous.gptgodmc.voice.VoiceCue;
import net.bigyous.gptgodmc.voice.VoiceModule;

/**
 * The god's spoken lines for client-mod players with the "subs" feature: a "say" message the moment the
 * text is shown (so it stays in step with the voice), and a "voice" message when each further part of
 * the line starts playing. Nothing here changes what players without the mod get.
 */
public final class ModVoice {
    public static final String WHISPER = "whisper";
    public static final String ANNOUNCE = "announce";
    public static final String VESSEL = "vessel";

    private ModVoice() {
    }

    /** Whether this player gets subtitles from the mod (so a duplicate vanilla title can be skipped). */
    public static boolean subtitled(Player p) {
        return ModLink.has(p, ModSessions.SUBS);
    }

    /**
     * One line about to be spoken by the current realm's god. Server thread. Never null: when no listener
     * has the mod it simply does nothing.
     *
     * @param from where the speaker stands (the vessel), or null for a voice in the mind
     */
    public static Line line(String kind, String text, String tone, List<Player> listeners, Location from) {
        boolean any = false;
        for (Player p : listeners) {
            if (subtitled(p)) {
                any = true;
                break;
            }
        }
        if (!any) return Line.SILENT;
        String realm = "main";
        String name = null;
        int color = 0xC77DFF;
        String effect = tone;
        try {
            Realm r = Realm.current();
            realm = r.id;
            GodView g = ClientViews.god(r, null);
            name = g.name();
            color = g.color();
            effect = VoiceModule.effectNow(tone);
        } catch (RuntimeException e) {
            // no realm bound (tests): a nameless voice in the default colour
        }
        double[] at = from == null ? null : new double[] { from.getX(), from.getY(), from.getZ() };
        return new Line(realm, name, color, kind, effect, text, List.copyOf(listeners), at);
    }

    /** A line's link to the mod players who hear it. */
    public static final class Line implements VoiceCue {
        static final Line SILENT = new Line(null, null, 0, null, null, "", List.of(), null);

        private final String realm;
        private final String name;
        private final int color;
        private final String kind;
        private final String tone;
        private final String text;
        private final List<Player> listeners;
        private final double[] from;
        private boolean shown;
        private float[] firstEnv;
        private int firstMs;
        private int firstChars;

        private Line(String realm, String name, int color, String kind, String tone, String text,
                List<Player> listeners, double[] from) {
            this.realm = realm;
            this.name = name;
            this.color = color;
            this.kind = kind;
            this.tone = tone;
            this.text = text;
            this.listeners = listeners;
            this.from = from;
        }

        /** Call from the line's show runnable (server thread): sends the subtitle. */
        public void show() {
            if (listeners.isEmpty()) return;
            float[] env;
            int ms;
            synchronized (this) {
                if (shown) return;
                shown = true;
                env = firstEnv;
                ms = env == null ? Loudness.estimateMs(text) : Loudness.scaleMs(firstMs, firstChars, text.length());
            }
            send(ModMessages.say(realm, name, color, text, tone, kind, from, ms, env));
        }

        @Override
        public void part(float[] envelope, int ms, int chars) {
            if (listeners.isEmpty()) return;
            synchronized (this) {
                if (!shown) {
                    // The first part: its loudness travels inside the say that is about to go out.
                    if (firstEnv == null) {
                        firstEnv = envelope;
                        firstMs = ms;
                        firstChars = chars;
                        return;
                    }
                }
            }
            com.google.gson.JsonObject message = ModMessages.voice(realm, ms, envelope);
            try {
                if (Bukkit.isPrimaryThread()) send(message);
                else Bukkit.getScheduler().runTask(GPTGOD.get(), () -> send(message));
            } catch (RuntimeException e) {
                // shutting down
            }
        }

        private void send(com.google.gson.JsonObject message) {
            for (Player p : listeners) {
                if (p.isOnline()) ModLink.send(p, ModSessions.SUBS, message);
            }
        }
    }
}
