package net.bigyous.gptgod.client.fx;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.bigyous.gptgod.client.GodClientMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * One particle of a scene: drawn from the mod's own sprite sheet, fully lit, with colour and size that
 * change over its life. Glowing ones are blended additively (light adds up, like fire); the rest blend
 * normally (smoke, shards). It ignores blocks, so effects never snag on terrain.
 */
public final class GlowParticle extends SingleQuadParticle {
    public static final Identifier SHEET = GodClientMod.id("textures/particle/sheet.png");

    /** Additive, depth-tested but not depth-writing, so glows never hide each other. */
    public static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(RenderPipeline
            .builder(RenderPipelines.PARTICLE_SNIPPET).withLocation(GodClientMod.id("pipeline/glow_particle"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).build());
    public static final RenderPipeline SOFT_PIPELINE = RenderPipelines.register(RenderPipeline
            .builder(RenderPipelines.PARTICLE_SNIPPET).withLocation(GodClientMod.id("pipeline/soft_particle"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).build());

    // Drawn in the solid pass on purpose: translucent particle layers need the game's OIT pipelines.
    static final Layer GLOW = new Layer(false, SHEET, GLOW_PIPELINE);
    static final Layer SOFT = new Layer(false, SHEET, SOFT_PIPELINE);

    private final Scene.Particles spec;
    private final float u0, v0;
    private final float size0, size1;
    private final float swirl;
    private final double cx, cz;
    private final float seed;

    GlowParticle(ClientLevel level, double x, double y, double z, Emitters.Spawn s, Scene.Particles spec, float scale,
            double cx, double cz) {
        super(level, x, y, z, null);
        this.spec = spec;
        this.xd = s.vx();
        this.yd = s.vy();
        this.zd = s.vz();
        this.swirl = s.swirl();
        this.cx = cx;
        this.cz = cz;
        this.lifetime = Math.max(1, s.life());
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1;
        int i = spec.sprite().ordinal();
        this.u0 = (i % 4) / 4f;
        this.v0 = (i / 4) / 2f;
        this.size0 = spec.size0() * scale;
        this.size1 = spec.size1() * scale;
        this.seed = random.nextFloat() * 100;
        if (spec.sprite() != Scene.Sprite.RAY && spec.sprite() != Scene.Sprite.MOTE) {
            this.roll = this.oRoll = random.nextFloat() * 6.2832f;
        }
        look(0);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd -= spec.gravity();
        if (spec.turbulence() > 0) {
            float t = spec.turbulence();
            xd += Math.sin(age * 0.31 + seed) * t * 0.5;
            yd += Math.sin(age * 0.23 + seed * 1.7) * t * 0.3;
            zd += Math.cos(age * 0.27 + seed * 0.6) * t * 0.5;
        }
        if (swirl != 0) {
            // Turn around the scene's vertical axis.
            double dx = x - cx, dz = z - cz;
            double cos = Math.cos(swirl), sin = Math.sin(swirl);
            x = cx + dx * cos - dz * sin;
            z = cz + dx * sin + dz * cos;
        }
        x += xd;
        y += yd;
        z += zd;
        float keep = 1 - spec.drag();
        xd *= keep;
        yd *= keep;
        zd *= keep;
        if (spec.sprite() == Scene.Sprite.SHARD || spec.sprite() == Scene.Sprite.RUNE) roll += 0.08f;
        look(age / (float) lifetime);
    }

    private void look(float t) {
        int a = spec.color0(), b = spec.color1();
        rCol = lerp(((a >> 16) & 255), ((b >> 16) & 255), t) / 255f;
        gCol = lerp(((a >> 8) & 255), ((b >> 8) & 255), t) / 255f;
        bCol = lerp((a & 255), (b & 255), t) / 255f;
        // Quick fade in, long fade out.
        float in = Math.min(1f, t / 0.12f);
        float out = t > 0.55f ? 1 - (t - 0.55f) / 0.45f : 1f;
        // Additive light piles up fast where particles overlap, so each glowing one is kept faint.
        alpha = Math.max(0, in * out * spec.alpha() * (spec.glow() >= 0.5f ? 0.3f + 0.25f * (1 - spec.glow()) : 1f));
        quadSize = Math.max(0.005f, lerp(size0, size1, t) * (1 + spec.grow() * t));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public float getQuadSize(float partial) {
        return quadSize;
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return spec.sprite() == Scene.Sprite.RAY ? FacingCameraMode.LOOKAT_Y : FacingCameraMode.LOOKAT_XYZ;
    }

    @Override
    protected Layer getLayer() {
        return spec.glow() >= 0.5f ? GLOW : SOFT;
    }

    @Override
    protected float getU0() {
        return u0;
    }

    @Override
    protected float getU1() {
        return u0 + 0.25f;
    }

    @Override
    protected float getV0() {
        return v0;
    }

    @Override
    protected float getV1() {
        return v0 + 0.5f;
    }

    @Override
    protected int getLightCoords(float partial) {
        return 0xF000F0;
    }
}
