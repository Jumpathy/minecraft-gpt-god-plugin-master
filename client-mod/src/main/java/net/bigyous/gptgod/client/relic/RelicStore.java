package net.bigyous.gptgod.client.relic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The relic art this client holds, by relic id, oldest first. Pure. Every change comes back as a
 * {@link Change}, so whoever owns the textures can make and free them to match.
 */
public final class RelicStore {
    /** At most this many relics are kept; past it the one that has gone longest without new art is dropped. */
    public static final int MAX = 128;

    /** fresh: ids whose art is new or repainted. gone: ids that no longer have any. */
    public record Change(List<String> fresh, List<String> gone) {
        public static final Change NONE = new Change(List.of(), List.of());

        public boolean isEmpty() {
            return fresh.isEmpty() && gone.isEmpty();
        }
    }

    private final Map<String, RelicArt> arts = new LinkedHashMap<>();

    public Change accept(RelicProtocol.Message message) {
        return switch (message) {
            case RelicProtocol.Art a -> put(a.art());
            case RelicProtocol.Gone g -> remove(g.id());
            case null, default -> Change.NONE;
        };
    }

    /** Keeps this art. The same painting sent again (same rev) changes nothing; any other rev replaces it. */
    public Change put(RelicArt art) {
        if (art == null) return Change.NONE;
        RelicArt old = arts.remove(art.id());
        if (old != null && old.rev() == art.rev()) {
            arts.put(old.id(), old);
            return Change.NONE;
        }
        arts.put(art.id(), art);
        List<String> gone = new ArrayList<>();
        Iterator<String> oldest = arts.keySet().iterator();
        while (arts.size() > MAX && oldest.hasNext()) {
            gone.add(oldest.next());
            oldest.remove();
        }
        return new Change(List.of(art.id()), gone);
    }

    public Change remove(String id) {
        return arts.remove(id) == null ? Change.NONE : new Change(List.of(), List.of(id));
    }

    /** Forgets everything (the server is gone). */
    public Change clear() {
        Change all = new Change(List.of(), List.copyOf(arts.keySet()));
        arts.clear();
        return all;
    }

    public RelicArt get(String id) {
        return arts.get(id);
    }

    public int size() {
        return arts.size();
    }

    /** The ids held, oldest first. */
    public List<String> ids() {
        return List.copyOf(arts.keySet());
    }
}
