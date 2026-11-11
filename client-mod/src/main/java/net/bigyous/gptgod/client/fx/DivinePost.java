package net.bigyous.gptgod.client.fx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;

import net.bigyous.gptgod.client.GodClientMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Runs {@link PostFx} on the GPU: while any full-screen effect is active it asks the game for the
 * gptgod:divine post effect and rewrites that pass's uniform block each frame. Render thread only.
 */
public final class DivinePost {
    private DivinePost() {
    }

    public static final Identifier ID = GodClientMod.id("divine");
    public static final String UNIFORMS = "GodFx";
    public static final int BYTES = PostFx.FLOATS * 4;

    private static GpuBuffer buffer;
    private static final ByteBuffer DATA = MemoryUtil.memAlloc(BYTES).order(ByteOrder.nativeOrder());

    /** A buffer we may rewrite every frame, in place of the fixed one the game makes for the pass. */
    public static GpuBuffer createBuffer() {
        buffer = RenderSystem.getDevice().createBuffer(() -> "gptgod divine fx", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                (long) BYTES);
        write(new float[] { 0.5f, 0.5f, 0, 0, 0.07f, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 0.5f, 0.5f, 0, 0 });
        return buffer;
    }

    private static void write(float[] u) {
        if (buffer == null || buffer.isClosed()) return;
        DATA.clear();
        for (float f : u) DATA.putFloat(f);
        DATA.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), DATA);
    }

    /** Called once a frame, after the game has listed the post effects it wants. */
    public static void frame(List<Identifier> requested) {
        PostFx fx = GodClientMod.POST;
        if (!fx.busy() || ShaderPacks.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            fx.clear();
            return;
        }
        float[] u = fx.frame(System.currentTimeMillis(), GodClientMod.config, DivinePost::project);
        if (!PostFx.visible(u)) return;
        if (buffer == null || buffer.isClosed()) {
            // The pass is not loaded yet: requesting it makes the game load it (and us make the buffer).
            requested.add(ID);
            return;
        }
        write(u);
        requested.add(ID);
    }

    /** A world point as screen uv (0..1, y up as the shader samples it), or null behind the camera. */
    static float[] project(double[] p) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 point = new Vec3(p[0], p[1], p[2]);
        Vec3 to = point.subtract(cam.position());
        org.joml.Vector3fc fwd = cam.forwardVector();
        if (to.x * fwd.x() + to.y * fwd.y() + to.z * fwd.z() <= 0.05) return null;
        Vec3 ndc = mc.gameRenderer.projectPointToScreen(point);
        return new float[] { (float) (ndc.x * 0.5 + 0.5), (float) (ndc.y * 0.5 + 0.5) };
    }
}
