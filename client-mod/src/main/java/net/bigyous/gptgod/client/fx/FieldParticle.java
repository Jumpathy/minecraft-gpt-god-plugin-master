package net.bigyous.gptgod.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;

/**
 * One particle of a "field" layer: every tick the god's formulas ({@link FxField}) say where it is and how
 * it looks, and the game glides it between those places. Drawn like {@link GlowParticle}, from the same
 * sprite sheet. It ignores blocks.
 */
public final class FieldParticle extends SingleQuadParticle {
    /** Where a scene's formula layers stand; a scene that is stopped takes its field particles with it. */
    public static final class Anchor {
        public double x, y, z;
        public boolean gone;
    }

    // Scratch for the formulas: particles are only ticked on the client thread, one at a time.
    private static final double[] VARS = new double[FxExpr.VARS];
    private static final double[] OUT = new double[FxField.OUT];

    private final FxField field;
    private final Anchor anchor;
    private final int index, count, born;
    private final long seed;
    private final float scale;
    private final float u0, v0;
    private final boolean glows, ray;

    /**
     * @param index which particle of count this is
     * @param born  the layer's tick at which it appeared
     */
    FieldParticle(ClientLevel level, FxField field, Anchor anchor, int index, int count, long seed, float scale, int born) {
        super(level, anchor.x, anchor.y, anchor.z, null);
        this.field = field;
        this.anchor = anchor;
        this.index = index;
        this.count = count;
        this.seed = seed;
        this.scale = scale;
        this.born = born;
        this.lifetime = Math.max(1, field.duration - born);
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1;
        Scene.Sprite sprite = Scene.Sprite.MOTE;
        for (Scene.Sprite s : Scene.Sprite.values()) {
            if (s.name().equalsIgnoreCase(field.sprite)) sprite = s;
        }
        int i = sprite.ordinal();
        this.u0 = (i % 4) / 4f;
        this.v0 = (i / 4) / 2f;
        this.ray = sprite == Scene.Sprite.RAY;
        this.glows = field.glow >= 0.5f;
        if (sprite != Scene.Sprite.RAY && sprite != Scene.Sprite.MOTE) {
            this.roll = this.oRoll = random.nextFloat() * 6.2832f;
        }
        place(born);
        xo = x;
        yo = y;
        zo = z;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (anchor.gone || age++ >= lifetime) {
            remove();
            return;
        }
        place(born + age);
    }

    private void place(int t) {
        field.at(index, count, t, seed, VARS, OUT);
        x = anchor.x + (field.offset[0] + OUT[FxField.X]) * scale;
        y = anchor.y + (field.offset[1] + OUT[FxField.Y]) * scale;
        z = anchor.z + (field.offset[2] + OUT[FxField.Z]) * scale;
        int rgb = field.color(OUT[FxField.SHADE]);
        rCol = ((rgb >> 16) & 255) / 255f;
        gCol = ((rgb >> 8) & 255) / 255f;
        bCol = (rgb & 255) / 255f;
        // Additive light piles up fast where particles overlap, so each glowing one is kept faint (as GlowParticle does).
        alpha = (float) OUT[FxField.ALPHA] * (glows ? 0.3f + 0.25f * (1 - field.glow) : 1f);
        quadSize = Math.max(0.005f, (float) OUT[FxField.SIZE] * scale);
        painted = FxTextures.get(field.texture) != null;
    }

    @Override
    public float getQuadSize(float partial) {
        return quadSize;
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return ray ? FacingCameraMode.LOOKAT_Y : FacingCameraMode.LOOKAT_XYZ;
    }

    /** One layer for each painted frame's texture and way of blending (a handful at most). */
    private static final java.util.Map<String, Layer> PAINTED = new java.util.HashMap<>();
    private boolean painted;

    @Override
    protected Layer getLayer() {
        net.minecraft.resources.Identifier texture = FxTextures.frame(field.texture);
        painted = texture != null;
        if (!painted) return glows ? GlowParticle.GLOW : GlowParticle.SOFT;
        return PAINTED.computeIfAbsent(texture + (glows ? "+" : "-"),
                k -> new Layer(false, texture, glows ? GlowParticle.GLOW_PIPELINE : GlowParticle.SOFT_PIPELINE));
    }

    @Override
    protected float getU0() {
        return painted ? 0 : u0;
    }

    @Override
    protected float getU1() {
        return painted ? 1 : u0 + 0.25f;
    }

    @Override
    protected float getV0() {
        return painted ? 0 : v0;
    }

    @Override
    protected float getV1() {
        return painted ? 1 : v0 + 0.5f;
    }

    @Override
    protected int getLightCoords(float partial) {
        return 0xF000F0;
    }
}
