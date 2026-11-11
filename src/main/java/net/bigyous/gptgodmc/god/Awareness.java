package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;

/**
 * What the god is aware of beyond the mortals' words, in a few lines for the people who run it (the God
 * Manager's mood page, the mod's screen, the mind viewer): what last moved its heart, the sky over its
 * world, and the memory that last came back to it. Each line starts with its label. Server thread.
 */
public final class Awareness {
    private Awareness() {
    }

    /** The longest one line may be. */
    public static final int LINE = 200;

    public static List<String> lines(Realm realm, long now) {
        List<String> out = new ArrayList<>();
        try {
            List<String> why = realm.memory().heart.whys(now, 2);
            if (!why.isEmpty()) out.add(Memory.clip("Moved by: " + String.join("; ", why), LINE));
            if (GodSettings.awarenessSky()) {
                String sky = Sky.now(realm.world());
                if (sky != null) out.add(sky);
            }
            String[] memory = GodSettings.awarenessMemories() ? Reminiscence.last(realm.id) : null;
            if (memory != null) {
                out.add(Memory.clip("Memory (" + Memory.age(now - Long.parseLong(memory[1])) + "): " + memory[0], LINE));
            }
        } catch (RuntimeException e) {
            // A line that cannot be read is simply not shown.
        }
        return out;
    }
}
