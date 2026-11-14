package net.bigyous.gptgod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the "reskin" layers of scenes that wear a painted texture or a wash of colour ({@link FxReskin}):
 * a skin laid over the open faces of the chosen solid blocks, lit as the world lights them. The blocks
 * themselves are untouched, so it works whatever draws the world. (A reskin that only says "as" another
 * block needs nothing here: the server shows that to everyone.) Client thread only.
 */
public final class ReskinFx {
    public static final int MAX_LIVE = 16;
    /** Floats a corner takes: x, y, z, u, v, shade. */
    private static final int VERTEX = 6;
    /** How far the skin stands off the block, so it never flickers against it. */
    private static final float OFF = 0.004f;
    // The six faces: which way they look, their four corners (bottom left, top left, top right, bottom right as
    // seen from outside), and how bright the game draws a face looking that way.
    private static final int[][] WAY = { { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
    private static final float[][] CORNERS = { { 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 1, 1 }, { 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0 },
            { 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0 }, { 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1 }, { 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1 },
            { 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0 } };
    private static final float[] SHADE = { 1f, 0.5f, 0.8f, 0.8f, 0.6f, 0.6f };
    private static final float[] UV = { 0, 1, 0, 0, 1, 0, 1, 1 };

    private static final class Live {
        FxReskin spec;
        FieldParticle.Anchor anchor;
        float scale;
        int age;
        double scanned = -1;
        int scannedAt = -100;
        /** The skin's faces in world coordinates, and each face's light. */
        float[] faces = new float[0];
        int[] light = new int[0];
    }

    private final List<Live> live = new ArrayList<>();
    private final double[] vars = new double[FxExpr.VARS];
    private boolean drawing;

    public void clear() {
        live.clear();
    }

    public void add(FxReskin spec, FieldParticle.Anchor anchor, float scale) {
        if (spec.texture.isEmpty() && spec.color < 0) return;
        if (!drawing) {
            LevelRenderEvents.COLLECT_SUBMITS.register(this::submit);
            drawing = true;
        }
        if (live.size() >= MAX_LIVE) live.remove(0);
        Live l = new Live();
        l.spec = spec;
        l.anchor = anchor;
        l.scale = scale;
        live.add(l);
    }

    public void tick(ClientLevel level) {
        for (Iterator<Live> it = live.iterator(); it.hasNext();) {
            Live l = it.next();
            if (level == null || l.anchor.gone || ++l.age > l.spec.duration) {
                it.remove();
                continue;
            }
            double r = Math.min(FxReskin.MAX_RADIUS, l.spec.reach(l.age, vars) * l.scale);
            // Look again when its reach has changed, and now and then anyway: blocks are dug and placed.
            if (Math.abs(r - l.scanned) >= 0.4 || l.age - l.scannedAt >= 20) scan(level, l, r);
        }
    }

    private static void scan(ClientLevel level, Live l, double r) {
        l.scanned = r;
        l.scannedAt = l.age;
        FxReskin s = l.spec;
        int cx = Mth.floor(l.anchor.x + s.offset[0] * l.scale), cy = Mth.floor(l.anchor.y + s.offset[1] * l.scale),
                cz = Mth.floor(l.anchor.z + s.offset[2] * l.scale);
        int reach = (int) Math.ceil(r), up = s.ball ? reach : s.height;
        float[] faces = new float[256 * 4 * VERTEX];
        int[] light = new int[256];
        int n = 0, blocks = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), next = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach && blocks < s.limit; dx++) {
            for (int dz = -reach; dz <= reach && blocks < s.limit; dz++) {
                for (int dy = -up; dy <= up && blocks < s.limit; dy++) {
                    if (!s.inside(dx, dy, dz, r)) continue;
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (!s.picks(x, y, z)) continue;
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || !state.isSolidRender()) continue;
                    if (!s.changes(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath())) continue;
                    blocks++;
                    for (int f = 0; f < 6; f++) {
                        next.set(x + WAY[f][0], y + WAY[f][1], z + WAY[f][2]);
                        if (level.getBlockState(next).isSolidRender()) continue;
                        if (n == light.length) {
                            light = java.util.Arrays.copyOf(light, n * 2);
                            faces = java.util.Arrays.copyOf(faces, n * 2 * 4 * VERTEX);
                        }
                        light[n] = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, next), level.getBrightness(LightLayer.SKY, next));
                        for (int k = 0; k < 4; k++) {
                            int at = (n * 4 + k) * VERTEX;
                            faces[at] = x + CORNERS[f][k * 3] + WAY[f][0] * OFF;
                            faces[at + 1] = y + CORNERS[f][k * 3 + 1] + WAY[f][1] * OFF;
                            faces[at + 2] = z + CORNERS[f][k * 3 + 2] + WAY[f][2] * OFF;
                            faces[at + 3] = UV[k * 2];
                            faces[at + 4] = UV[k * 2 + 1];
                            faces[at + 5] = SHADE[f];
                        }
                        n++;
                    }
                }
            }
        }
        l.faces = java.util.Arrays.copyOf(faces, n * 4 * VERTEX);
        l.light = java.util.Arrays.copyOf(light, n);
    }

    private void submit(LevelRenderContext ctx) {
        if (live.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        PoseStack pose = ctx.poseStack();
        SubmitNodeCollector out = ctx.submitNodeCollector();
        for (Live l : live) {
            float[] d = l.faces;
            int[] light = l.light;
            if (light.length == 0) continue;
            float fade = (float) FxField.fade(l.age + partial, l.spec.duration);
            if (fade <= 0.01f) continue;
            // Positions are sent relative to the camera's own block, so far-off worlds lose no precision.
            double bx = Math.floor(cam.x), by = Math.floor(cam.y), bz = Math.floor(cam.z);
            pose.pushPose();
            pose.translate(bx - cam.x, by - cam.y, bz - cam.z);
            Identifier texture = FxTextures.frame(l.spec.texture);
            int rgb = l.spec.color < 0 ? 0xFFFFFF : l.spec.color;
            float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
            if (texture != null) {
                out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(texture), (p, buffer) -> {
                    for (int v = 0; v < light.length * 4; v++) {
                        int at = v * VERTEX;
                        float shade = d[at + 5];
                        buffer.addVertex(p, (float) (d[at] - bx), (float) (d[at + 1] - by), (float) (d[at + 2] - bz)).setColor(r * shade, g * shade, b * shade, fade)
                                .setUv(d[at + 3], d[at + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light[v / 4]).setNormal(p, 0, 1, 0);
                    }
                });
            } else {
                float alpha = fade * l.spec.strength;
                out.submitCustomGeometry(pose, SolidFx.WASH, (p, buffer) -> {
                    for (int v = 0; v < light.length * 4; v++) {
                        int at = v * VERTEX;
                        // A wash has no light of its own: it darkens with the block's light, roughly as the block does.
                        float lit = d[at + 5] * (0.25f + 0.75f * Math.max(LightCoordsUtil.block(light[v / 4]), LightCoordsUtil.sky(light[v / 4])) / 15f);
                        buffer.addVertex(p, (float) (d[at] - bx), (float) (d[at + 1] - by), (float) (d[at + 2] - bz)).setColor(r * lit, g * lit, b * lit, alpha);
                    }
                });
            }
            pose.popPose();
        }
    }
}
