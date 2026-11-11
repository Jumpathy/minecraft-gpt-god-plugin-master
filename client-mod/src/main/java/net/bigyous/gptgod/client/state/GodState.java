package net.bigyous.gptgod.client.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.bigyous.gptgod.client.net.Protocol;

/**
 * What the server has told us about the gods of the world we are in. Pure; touched only on the client
 * thread. Cleared on disconnect and world change (the server then sends it all again).
 */
public final class GodState {
    private final Map<String, Protocol.God> gods = new LinkedHashMap<>();
    private final Map<String, Protocol.Standing> standing = new LinkedHashMap<>();
    private final Map<String, Protocol.Quest> quests = new LinkedHashMap<>();
    private boolean linked;
    private List<String> features = List.of();
    private long updatedAt;

    public void accept(Protocol.Message m, long now) {
        switch (m) {
            case Protocol.Welcome w -> {
                clear();
                linked = w.accepted();
                features = w.features();
            }
            case Protocol.God g -> gods.put(g.realm(), g);
            case Protocol.Standing s -> standing.put(s.realm(), s);
            case Protocol.Quest q -> {
                if (q.title() == null) quests.remove(q.realm());
                else quests.put(q.realm(), q);
            }
            default -> {
                return;
            }
        }
        updatedAt = now;
    }

    public void clear() {
        gods.clear();
        standing.clear();
        quests.clear();
    }

    public void unlink() {
        clear();
        linked = false;
        features = List.of();
    }

    public boolean linked() {
        return linked;
    }

    public boolean has(String feature) {
        return linked && features.contains(feature);
    }

    public long updatedAt() {
        return updatedAt;
    }

    /** The god that rules this world (the first marked here), else any god we heard of, else null. */
    public Protocol.God main() {
        for (Protocol.God g : gods.values()) {
            if (g.here()) return g;
        }
        return gods.isEmpty() ? null : gods.values().iterator().next();
    }

    public List<Protocol.God> gods() {
        return new ArrayList<>(gods.values());
    }

    public Protocol.Standing standing(String realm) {
        return standing.get(realm);
    }

    public Protocol.Quest quest(String realm) {
        return quests.get(realm);
    }
}
