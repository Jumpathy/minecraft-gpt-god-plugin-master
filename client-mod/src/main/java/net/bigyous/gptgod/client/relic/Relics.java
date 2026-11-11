package net.bigyous.gptgod.client.relic;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.bigyous.gptgod.client.GodClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TickableTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The relics this client draws itself. The plugin sends each relic's art on gptgod:main; a stack whose
 * item_model is gptgodmc:relic/&lt;id&gt; is then drawn from that art, at once and with no resource pack
 * (see ModelManagerMixin). Relics the plugin has not sent art for are left to the resource packs.
 */
public final class Relics {
    private Relics() {
    }

    /** The item_model ids the plugin gives relic stacks: gptgodmc:relic/&lt;id&gt;. */
    public static final String MODEL_NAMESPACE = "gptgodmc", MODEL_PATH = "relic/";

    private static final RelicStore STORE = new RelicStore();
    /** Read while the game draws; changed only on the client thread. */
    private static final Map<String, RelicItemModel> MODELS = new ConcurrentHashMap<>();
    /**
     * Each relic's textures are registered as gptgod:relic/&lt;slot&gt; and gptgod:relic_walls/&lt;slot&gt;,
     * slots being reused, so the game never holds more than RelicStore.MAX of each of our texture names
     * however many relics come and go.
     */
    private static final Map<String, Integer> SLOTS = new HashMap<>();
    private static final BitSet TAKEN = new BitSet();

    /** The strip as a texture the game can draw; it also counts client ticks for the animation. */
    static final class Sheet extends DynamicTexture implements TickableTexture {
        private int age;

        Sheet(String id, NativeImage image) {
            super(() -> "gptgod relic " + id, image);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }

        @Override
        public void tick() {
            age++;
        }

        int age() {
            return age;
        }
    }

    /** The colours of a relic's sides, as the painter gave them (RelicArt.walls). */
    private static final class Walls extends DynamicTexture {
        Walls(String id, NativeImage image) {
            super(() -> "gptgod relic walls " + id, image);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }

    /** Handles a gptgod:main message if it is about a relic. True when it was (even if it had to be dropped). */
    public static boolean receive(byte[] data) {
        RelicProtocol.Message message = RelicProtocol.decode(data);
        if (message == null) return false;
        onClientThread(() -> apply(STORE.accept(message)));
        return true;
    }

    /** Left the server: forget its relics and free their textures. */
    public static void clear() {
        onClientThread(() -> apply(STORE.clear()));
    }

    /** The model to draw for this item_model id, or null to leave it to the game (not a relic, or no art yet). */
    public static ItemModel model(Identifier itemModel) {
        if (MODELS.isEmpty() || !MODEL_NAMESPACE.equals(itemModel.getNamespace())) return null;
        String path = itemModel.getPath();
        return path.startsWith(MODEL_PATH) ? MODELS.get(path.substring(MODEL_PATH.length())) : null;
    }

    /**
     * Whether the stack in a hand has only changed between forms of one relic (starbrand and
     * starbrand__on, say) that we draw. Then the new form shows at once, without the hand dipping as it
     * does when a different item is taken up.
     */
    public static boolean changedForm(ItemStack shown, ItemStack next) {
        if (MODELS.isEmpty() || shown == next || shown.isEmpty() || !ItemStack.isSameItem(shown, next)) return false;
        Identifier before = shown.get(DataComponents.ITEM_MODEL), after = next.get(DataComponents.ITEM_MODEL);
        if (before == null || after == null || before.equals(after) || model(after) == null) return false;
        return MODEL_NAMESPACE.equals(before.getNamespace()) && before.getPath().startsWith(MODEL_PATH) && RelicProtocol
                .relicOf(before.getPath().substring(MODEL_PATH.length())).equals(RelicProtocol.relicOf(after.getPath().substring(MODEL_PATH.length())));
    }

    /**
     * /godfx relics: loads the sample relics as if the plugin had sent them. Returns, for each, a command
     * that puts it in your hand (it needs cheats; the art is only on this client).
     */
    public static List<String> demo() {
        List<String> give = new ArrayList<>();
        for (RelicArt art : RelicSamples.all()) {
            apply(STORE.put(art));
            String base = switch (art.kind()) {
                case BLADE -> "iron_sword";
                case STAFF -> "stick";
                case TOME -> "book";
                case SPIRIT -> "paper";
                case TRINKET -> "amethyst_shard";
            };
            give.add("/give @s minecraft:" + base + "[minecraft:item_model=\"" + MODEL_NAMESPACE + ":" + MODEL_PATH + art.id()
                    + "\",minecraft:item_name=\"" + art.name() + "\"]");
        }
        return give;
    }

    /** Every relic builds its quads afresh the next time it is drawn (they had grown past their budget). */
    static void forgetQuads() {
        MODELS.values().forEach(RelicItemModel::forgetQuads);
    }

    private static void onClientThread(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) task.run();
        else mc.execute(task);
    }

    /** Makes the textures and models follow the store. */
    private static void apply(RelicStore.Change change) {
        for (String id : change.gone()) drop(id);
        for (String id : change.fresh()) {
            RelicArt art = STORE.get(id);
            if (art == null) continue;
            try {
                show(art);
            } catch (RuntimeException | LinkageError e) {
                // Art that cannot become a texture (or be drawn at all on this machine) is as good as never
                // sent: the resource packs' model shows.
                STORE.remove(id);
                drop(id);
            }
        }
    }

    private static void show(RelicArt art) {
        Integer slot = SLOTS.get(art.id());
        if (slot == null) {
            slot = TAKEN.nextClearBit(0);
            TAKEN.set(slot);
            SLOTS.put(art.id(), slot);
        }
        Identifier name = GodClientMod.id("relic/" + slot), wallsName = GodClientMod.id("relic_walls/" + slot);
        Sheet sheet = new Sheet(art.id(), image(art.pixels(), art.w(), art.h() * art.frames()));
        // Registering under a name that is taken closes the texture that had it (the relic's older painting).
        Minecraft.getInstance().getTextureManager().register(name, sheet);
        if (art.walls() == null) {
            Minecraft.getInstance().getTextureManager().release(wallsName);
        } else {
            Minecraft.getInstance().getTextureManager().register(wallsName,
                    new Walls(art.id(), image(art.walls(), 2 * art.w(), 2 * art.h() * art.wallFrames())));
        }
        MODELS.put(art.id(), new RelicItemModel(art, name, art.walls() == null ? null : wallsName, sheet));
    }

    private static NativeImage image(int[] argb, int width, int height) {
        NativeImage image = new NativeImage(width, height, false);
        try {
            for (int y = 0, at = 0; y < height; y++) {
                for (int x = 0; x < width; x++) image.setPixel(x, y, argb[at++]);
            }
            return image;
        } catch (RuntimeException e) {
            image.close();
            throw e;
        }
    }

    private static void drop(String id) {
        MODELS.remove(id);
        Integer slot = SLOTS.remove(id);
        if (slot == null) return;
        TAKEN.clear(slot);
        Minecraft.getInstance().getTextureManager().release(GodClientMod.id("relic/" + slot));
        Minecraft.getInstance().getTextureManager().release(GodClientMod.id("relic_walls/" + slot));
    }
}
