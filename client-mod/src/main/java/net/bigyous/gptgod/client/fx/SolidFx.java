package net.bigyous.gptgod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.bigyous.gptgod.client.GodClientMod;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the "solid" layers of scenes: geometry the god designed ({@link FxSolid}), rebuilt every frame from
 * its formulas. Matter hides what is behind it; light is drawn like the other light geometry. Ticked on the
 * client thread, drawn once a frame.
 */
public final class SolidFx {
    public static final int MAX_SOLIDS = 32;

    /** Flat-coloured matter: blended (so it can fade in and out) but written to the depth buffer. */
    static final RenderPipeline MATTER_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(GodClientMod.id("pipeline/solid_mesh")).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)).withCull(false).build());
    static final RenderType MATTER = RenderType.create("gptgod_solid_mesh",
            RenderSetup.builder(MATTER_PIPELINE).setOitPipelines(RenderPipelines.OIT_DEBUG_QUADS).createRenderSetup());

    /** A wash of colour over blocks: blended, tested against depth but not written to it, so it never hides what is drawn after. */
    static final RenderPipeline WASH_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(GodClientMod.id("pipeline/block_wash")).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).withCull(false).build());
    static final RenderType WASH = RenderType.create("gptgod_block_wash",
            RenderSetup.builder(WASH_PIPELINE).setOitPipelines(RenderPipelines.OIT_DEBUG_QUADS).createRenderSetup());

    private static final class Live {
        FxSolid spec;
        FieldParticle.Anchor anchor;
        float scale;
        long seed;
        int age;
    }

    private final List<Live> live = new ArrayList<>();
    private final double[] vars = new double[FxExpr.VARS];
    private boolean drawing;

    public int count() {
        return live.size();
    }

    public void clear() {
        live.clear();
    }

    public void add(FxSolid spec, FieldParticle.Anchor anchor, float scale, long seed) {
        if (!drawing) {
            LevelRenderEvents.COLLECT_SUBMITS.register(this::submit);
            drawing = true;
        }
        if (live.size() >= MAX_SOLIDS) live.remove(0);
        Live l = new Live();
        l.spec = spec;
        l.anchor = anchor;
        l.scale = scale;
        l.seed = seed;
        live.add(l);
    }

    public void tick() {
        for (Iterator<Live> it = live.iterator(); it.hasNext();) {
            Live l = it.next();
            if (l.anchor.gone || ++l.age > l.spec.duration) it.remove();
        }
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
            // Light is kept fainter when the viewer turned bloom down, like the other light geometry.
            double strength = l.spec.light ? 0.4 + 0.6 * GodClientMod.config.bloom : 1;
            Viewer.fill(vars, l.anchor.x + l.spec.offset[0] * l.scale, l.anchor.y + l.spec.offset[1] * l.scale,
                    l.anchor.z + l.spec.offset[2] * l.scale, l.scale);
            float[] d = l.spec.build(l.age + partial, l.seed, l.scale, strength, vars);
            if (d.length == 0) continue;
            pose.pushPose();
            pose.translate(l.anchor.x - cam.x, l.anchor.y - cam.y, l.anchor.z - cam.z);
            Identifier texture = FxTextures.frame(l.spec.texture);
            if (texture != null) {
                // A painted solid: the game's own textured types, fully lit, tinted by the solid's colour and shading.
                RenderType type = l.spec.light ? RenderTypes.eyes(texture) : RenderTypes.entityTranslucent(texture);
                out.submitCustomGeometry(pose, type, (p, buffer) -> {
                    for (int at = 0; at < d.length; at += FxSolid.VERTEX) {
                        buffer.addVertex(p, d[at], d[at + 1], d[at + 2]).setColor(d[at + 3], d[at + 4], d[at + 5], d[at + 6])
                                .setUv(d[at + 7], d[at + 8]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(p, 0, 1, 0);
                    }
                });
            } else {
                out.submitCustomGeometry(pose, l.spec.light ? WorldFx.LIGHT : MATTER, (p, buffer) -> {
                    for (int at = 0; at < d.length; at += FxSolid.VERTEX) {
                        buffer.addVertex(p, d[at], d[at + 1], d[at + 2]).setColor(d[at + 3], d[at + 4], d[at + 5], d[at + 6]);
                    }
                });
            }
            pose.popPose();
        }
    }
}
