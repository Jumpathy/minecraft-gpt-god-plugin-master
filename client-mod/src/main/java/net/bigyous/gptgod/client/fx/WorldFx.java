package net.bigyous.gptgod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.bigyous.gptgod.client.GodClientMod;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the solid parts of scenes in the world: light geometry ({@link Meshes}), fake blocks
 * ({@link Illusions}) and words of light. Ticked on the client thread, drawn once a frame.
 */
public final class WorldFx {
    public static final int MAX_MESHES = 48;
    public static final int MAX_PIECES = 600;
    public static final int MAX_GLYPHS = 16;

    /** Additive light, seen from both sides, never hiding what is behind it. */
    static final RenderPipeline LIGHT_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(GodClientMod.id("pipeline/light_mesh")).withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).withCull(false).build());
    static final RenderType LIGHT = RenderType.create("gptgod_light_mesh",
            RenderSetup.builder(LIGHT_PIPELINE).setOitPipelines(RenderPipelines.OIT_LIGHTNING).createRenderSetup());

    private static final class LiveMesh {
        Meshes.Spec spec;
        double x, y, z;
        int follow = -1;
        int age;
    }

    private static final class LiveGlyph {
        Scene.Glyph glyph;
        double x, y, z;
        float scale;
        int age;
    }

    private final List<LiveMesh> meshes = new ArrayList<>();
    private final List<Illusions.Piece> pieces = new ArrayList<>();
    private final List<LiveGlyph> glyphs = new ArrayList<>();

    public void clear() {
        meshes.clear();
        pieces.clear();
        glyphs.clear();
    }

    public int pieces() {
        return pieces.size();
    }

    public void addMesh(Scene.Mesh m, double x, double y, double z, float scale, int follow, long seed) {
        Meshes.Kind kind = Meshes.Kind.of(m.kind());
        if (kind == null) return;
        if (meshes.size() >= MAX_MESHES) meshes.remove(0);
        LiveMesh l = new LiveMesh();
        double[] to = m.to() == null ? null : new double[] { m.to()[0] * scale, m.to()[1] * scale, m.to()[2] * scale };
        l.spec = new Meshes.Spec(kind, m.radius() * scale, m.height() * scale, m.width() * scale, to, m.count(), m.color0(), m.color1(),
                m.alpha() * (float) (0.4 + 0.6 * GodClientMod.config.bloom), seed, m.spin(), m.duration(), m.yaw());
        l.x = x + m.offset()[0] * scale;
        l.y = y + m.offset()[1] * scale;
        l.z = z + m.offset()[2] * scale;
        l.follow = follow;
        meshes.add(l);
    }

    public void addGlyph(Scene.Glyph g, double x, double y, double z, float scale) {
        if (glyphs.size() >= MAX_GLYPHS) glyphs.remove(0);
        LiveGlyph l = new LiveGlyph();
        l.glyph = g;
        l.x = x + g.offset()[0] * scale;
        l.y = y + g.offset()[1] * scale + 2.2 * scale;
        l.z = z + g.offset()[2] * scale;
        l.scale = scale;
        glyphs.add(l);
    }

    /** Tears blocks loose (as an illusion): the listed ones, or count picked from the ground around the scene. */
    public void launch(ClientLevel level, Scene.Debris d, double x, double y, double z, float scale, Random rnd) {
        Illusions.Mode mode = Illusions.Mode.of(d.mode());
        double radius = d.radius() * scale;
        List<Object[]> picked = new ArrayList<>();
        for (Scene.Chunk c : d.blocks()) {
            Identifier id = Identifier.tryParse(c.block().contains("[") ? c.block().substring(0, c.block().indexOf('[')) : c.block());
            if (id == null) continue;
            BlockState state = BuiltInRegistries.BLOCK.getOptional(id).map(b -> b.defaultBlockState()).orElse(null);
            if (state == null || state.getRenderShape() != RenderShape.MODEL) continue;
            picked.add(new Object[] { state, x + c.x() * scale, y + c.y() * scale, z + c.z() * scale });
        }
        int want = Math.min(d.count(), (int) (Scene.MAX_DEBRIS * GodClientMod.config.particles));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < want * 3 && picked.size() < want + d.blocks().size(); i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = radius * Math.sqrt(rnd.nextDouble());
            int bx = Mth.floor(x + Math.cos(a) * r), bz = Mth.floor(z + Math.sin(a) * r);
            // The surface near the scene's height: the first solid block with open air above it.
            for (int by = Mth.floor(y) + 4; by >= Mth.floor(y) - 6; by--) {
                BlockState state = level.getBlockState(pos.set(bx, by, bz));
                if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
                if (!level.getBlockState(pos.set(bx, by + 1, bz)).isAir()) break;
                picked.add(new Object[] { state, bx + 0.5, (double) by + 1, bz + 0.5 });
                break;
            }
        }
        for (Object[] p : picked) {
            if (pieces.size() >= MAX_PIECES) pieces.remove(0);
            int life = d.life() + rnd.nextInt(Math.max(1, d.life() / 4));
            pieces.add(Illusions.launch(p[0], mode, (double) p[1], (double) p[2], (double) p[3], x, z, d.speed() * scale, d.up() * scale,
                    d.spin(), d.gravity(), life, d.height() * scale * (0.5 + 0.5 * rnd.nextDouble()), rnd));
        }
    }

    public void tick(ClientLevel level) {
        if (level == null) {
            clear();
            return;
        }
        for (Iterator<LiveMesh> it = meshes.iterator(); it.hasNext();) {
            LiveMesh m = it.next();
            if (m.follow >= 0) {
                var e = level.getEntity(m.follow);
                if (e != null) {
                    m.x = e.getX();
                    m.y = e.getY();
                    m.z = e.getZ();
                }
            }
            if (++m.age > m.spec.duration()) it.remove();
        }
        for (Iterator<Illusions.Piece> it = pieces.iterator(); it.hasNext();) {
            Illusions.Piece p = it.next();
            p.tick();
            if (p.done()) it.remove();
        }
        for (Iterator<LiveGlyph> it = glyphs.iterator(); it.hasNext();) {
            LiveGlyph g = it.next();
            g.y += g.glyph.rise();
            if (++g.age > g.glyph.duration()) it.remove();
        }
    }

    /** Draws everything for this frame. */
    public void submit(LevelRenderContext ctx) {
        if (meshes.isEmpty() && pieces.isEmpty() && glyphs.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        PoseStack pose = ctx.poseStack();
        SubmitNodeCollector out = ctx.submitNodeCollector();

        for (LiveMesh m : meshes) {
            Meshes.Quads q = Meshes.build(m.spec, m.age + partial);
            if (q.quads == 0) continue;
            pose.pushPose();
            pose.translate(m.x - cam.x, m.y - cam.y, m.z - cam.z);
            out.submitCustomGeometry(pose, LIGHT, (p, buffer) -> {
                float[] d = q.data;
                for (int i = 0, n = q.quads * 4; i < n; i++) {
                    int at = i * 7;
                    buffer.addVertex(p, d[at], d[at + 1], d[at + 2]).setColor(d[at + 3], d[at + 4], d[at + 5], d[at + 6]);
                }
            });
            pose.popPose();
        }

        for (Illusions.Piece piece : pieces) {
            float size = piece.size(partial);
            if (size <= 0.01f) continue;
            double x = Mth.lerp(partial, piece.ox, piece.x), y = Mth.lerp(partial, piece.oy, piece.y), z = Mth.lerp(partial, piece.oz, piece.z);
            MovingBlockRenderState state = new MovingBlockRenderState();
            BlockPos at = BlockPos.containing(x, y + 0.5, z);
            state.blockPos = at;
            state.randomSeedPos = at;
            state.blockState = (BlockState) piece.block;
            state.biome = level.getBiome(at);
            state.cardinalLighting = level.cardinalLighting();
            state.lightEngine = level.getLightEngine();
            pose.pushPose();
            pose.translate(x - cam.x, y - cam.y + 0.5, z - cam.z);
            pose.rotateDegrees(Axis.YP, Mth.lerp(partial, piece.oYaw, piece.yaw));
            pose.rotateDegrees(Axis.XP, Mth.lerp(partial, piece.oPitch, piece.pitch));
            pose.rotateDegrees(Axis.ZP, Mth.lerp(partial, piece.oRoll, piece.roll));
            pose.scale(size, size, size);
            pose.translate(-0.5, -0.5, -0.5);
            out.submitMovingBlock(pose, state, 0);
            pose.popPose();
        }

        Font font = mc.font;
        for (LiveGlyph g : glyphs) {
            float t = (g.age + partial) / Math.max(1, g.glyph.duration());
            float alpha = Math.max(0, Math.min(1, Math.min(t / 0.1f, (1 - t) / 0.25f)));
            int a = Math.round(alpha * 255);
            if (a < 8) continue;
            pose.pushPose();
            pose.translate(g.x - cam.x, g.y - cam.y + g.glyph.rise() * partial, g.z - cam.z);
            pose.mulPose(new org.joml.Matrix4f().rotation(mc.gameRenderer.mainCamera().rotation()));
            float s = 0.04f * g.glyph.size() * g.scale;
            pose.scale(s, -s, s);
            Component text = Component.literal(g.glyph.text());
            float w = font.width(text);
            out.submitText(pose, -w / 2f, 0, text.getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH, 0xF000F0,
                    a << 24 | g.glyph.color(), 0, a << 24 | Meshes.mix(g.glyph.color(), 0x000000, 0.75));
            pose.popPose();
        }
    }
}
