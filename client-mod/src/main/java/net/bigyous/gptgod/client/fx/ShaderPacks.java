package net.bigyous.gptgod.client.fx;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether an Iris shader pack is drawing the world. Then the mod's own full-screen pass stays off (shader
 * packs replace the game's pipeline and bring their own bloom); particles and the rest still work.
 */
public final class ShaderPacks {
    private ShaderPacks() {
    }

    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
    private static long checkedAt;
    private static boolean active;

    public static boolean active() {
        if (!IRIS) return false;
        long now = System.currentTimeMillis();
        if (now - checkedAt > 1000) {
            checkedAt = now;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                active = (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
            } catch (ReflectiveOperationException | RuntimeException e) {
                active = false;
            }
        }
        return active;
    }
}
