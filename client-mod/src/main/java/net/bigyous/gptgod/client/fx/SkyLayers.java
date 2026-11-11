package net.bigyous.gptgod.client.fx;

import org.joml.Vector3f;
import org.joml.Vector4f;

import net.bigyous.gptgod.client.GodClientMod;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;

/** Hooks {@link SkyMood} into the world's environment attributes (positional, so it fades with distance). */
public final class SkyLayers {
    private SkyLayers() {
    }

    public static void add(EnvironmentAttributeSystem.Builder b) {
        SkyMood sky = GodClientMod.SKY;
        b.addPositionalLayer(EnvironmentAttributes.SKY_COLOR, (base, pos, interp) -> {
            float[] c = sky.active() ? sky.sky(base.x(), base.y(), base.z(), pos.x, pos.y, pos.z)
                    : new float[] { base.x(), base.y(), base.z() };
            return scene(c, 1f);
        });
        b.addPositionalLayer(EnvironmentAttributes.FOG_COLOR, (base, pos, interp) -> {
            float[] c = sky.active() ? sky.fog(base.x(), base.y(), base.z(), pos.x, pos.y, pos.z)
                    : new float[] { base.x(), base.y(), base.z() };
            return scene(c, 0.85f);
        });
        b.addPositionalLayer(EnvironmentAttributes.CLOUD_COLOR, (base, pos, interp) -> {
            if (!sky.active() && GodClientMod.SCENES.skyStrength() <= 0) return base;
            float[] c = sky.cloud(base.x(), base.y(), base.z(), pos.x, pos.y, pos.z);
            Vector3f t = scene(c, 0.8f);
            return new Vector4f(t.x, t.y, t.z, base.w());
        });
    }

    /** A running scene's sky tint goes over the mood. */
    private static Vector3f scene(float[] c, float weight) {
        float s = GodClientMod.SCENES.skyStrength() * weight;
        if (s <= 0) return new Vector3f(c[0], c[1], c[2]);
        float[] k = GodClientMod.SCENES.skyColor();
        return new Vector3f(c[0] + (k[0] - c[0]) * s, c[1] + (k[1] - c[1]) * s, c[2] + (k[2] - c[2]) * s);
    }
}
