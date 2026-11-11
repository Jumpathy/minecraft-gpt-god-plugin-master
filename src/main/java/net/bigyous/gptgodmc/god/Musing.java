package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;

/**
 * The god's {@link Mind} at work: which of its private thoughts come back in a perception (only those
 * that bear on the moment; often none), and the reflection that distils its recent thoughts into new
 * beliefs, intentions and questions with the memory model, off-thread, after a thought now and then.
 */
public final class Musing {
    /** Characters of recalled thoughts in one perception (about 110 tokens at most). */
    static final int BUDGET = 420;
    /** A reflection needs at least this many thoughts with something in them since the last one. */
    static final int ENOUGH = 3;
    private static final Set<String> REFLECTING = ConcurrentHashMap.newKeySet();

    private Musing() {
    }

    /** "## On your mind" with the thoughts that bear on this moment, or "" (most thoughts: nothing to show). */
    public static String perceive(Realm realm, List<String> online, EventLogger.Drained drained, String trigger,
            boolean preview) {
        try {
            if (!GodSettings.mindEnabled() || Llm.lite()) return "";
            Memory m = realm.memory();
            long now = System.currentTimeMillis();
            synchronized (m) {
                if (m.mind.thoughts.isEmpty()) return "";
                StringBuilder text = new StringBuilder(trigger == null ? "" : trigger);
                drained.prayers().forEach(l -> text.append(' ').append(l));
                drained.speech().forEach(l -> text.append(' ').append(l));
                drained.events().forEach(l -> text.append(' ').append(l));
                drained.directives().forEach(l -> text.append(' ').append(l));
                List<Mind.Recalled> shown = Mind.recall(m.mind, new Mind.Cue(Perception.involved(m, online, drained),
                        text.toString(), Reminiscence.quiet(trigger)), GodSettings.mindRecall(), BUDGET, now);
                if (shown.isEmpty()) return "";
                if (!preview) Mind.surfaced(shown, now);
                int rest = m.mind.thoughts.size() - shown.size();
                return "## On your mind (your own private thoughts that bear on this moment"
                        + (rest > 0 ? "; " + rest + " more: reflect" : "") + ")\n" + Mind.render(shown, now) + "\n";
            }
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not recall the god's thoughts", e);
            return "";
        }
    }

    /** After a thought (the realm bound): reflect when enough has happened since the last reflection. */
    public static void afterThought() {
        Realm realm = Realm.current();
        try {
            int gapMinutes = GodSettings.mindReflectMinutes();
            if (!GodSettings.mindEnabled() || gapMinutes <= 0 || Llm.lite() || !GodClient.hasCredentials()) return;
            Memory m = realm.memory();
            long now = System.currentTimeMillis();
            List<Memory.CycleRecord> since = new ArrayList<>();
            String prompt;
            synchronized (m) {
                Mind.fade(m.mind, now, GodSettings.mindFadeMs());
                if (now - m.mind.reflectedAt < gapMinutes * 60_000L) return;
                for (Memory.CycleRecord r : m.recent) {
                    boolean something = (r.thought != null && !r.thought.isBlank()) || (r.acts != null && !r.acts.isEmpty());
                    if (r.at > m.mind.reflectedAt && something) since.add(r);
                }
                if (since.size() < ENOUGH || !REFLECTING.add(realm.id)) return;
                Persona p = realm.persona;
                prompt = Mind.reflectionPrompt(p == null ? "the god" : p.title(), m.mind, since, now);
                // Set now, so a failed reflection waits out the gap instead of retrying every thought.
                m.mind.reflectedAt = now;
            }
            try {
                Chronicler.executor.execute(Realm.bound(() -> reflect(realm, m, prompt)));
            } catch (RejectedExecutionException e) {
                REFLECTING.remove(realm.id);
            }
        } catch (RuntimeException e) {
            REFLECTING.remove(realm.id);
            GPTGOD.LOGGER.warn("Could not start a reflection", e);
        }
    }

    private static void reflect(Realm realm, Memory m, String prompt) {
        try {
            Mind.Reflection r = Mind.parseReflection(Chronicler.model.complete(prompt, 600, "reflecting"));
            if (r == null) return;
            String changed;
            synchronized (m) {
                changed = Mind.apply(m.mind, r, GodSettings.mindMaxThoughts(), System.currentTimeMillis());
            }
            m.save();
            Journal.add(Journal.Kind.INFO, "Reflected on its recent thoughts: " + changed);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Reflection failed", e);
        } finally {
            REFLECTING.remove(realm.id);
        }
    }
}
