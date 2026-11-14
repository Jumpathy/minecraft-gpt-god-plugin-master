package net.bigyous.gptgod.client.relic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.bigyous.gptgod.client.GodClientMod;
import net.bigyous.gptgod.client.fx.FxSolid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a relic's true shape ({@link RelicShape}) where the item is: each part in its material's pixel tile
 * (repeated, texel for texel, as the plugin lays it) or, without one, its own colour, lit by the world's
 * light like any item (the lightmap), darker at night. The shape's colours already carry the scenes' sun
 * shading, so the game's own shading by direction is left out rather than laid on top. Faces may be wound
 * either way, so both sides are drawn.
 */
final class RelicShapeRenderer implements SpecialModelRenderer<Object> {
    /** The game's entity shader with the lightmap, without overlay or shading by direction, seen from both sides. */
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(GodClientMod.id("pipeline/relic_shape")).withShaderDefine("ALPHA_CUTOUT", 0.1F).withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING").withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build());
    private static final Identifier WHITE = GodClientMod.id("relic_white");
    private static boolean whiteMade;

    /** The faces that wear one texture: where they start in faces, and the texture (WHITE for plain colour). */
    private record Group(Identifier texture, int[] starts, boolean tiled) {
    }

    private final float[] faces;
    private final List<Group> groups = new ArrayList<>();
    private final Map<Identifier, RenderType> types = new LinkedHashMap<>();
    private final Vector3fc[] corners;

    /** @param tiles each tile name the shape's faces wear, as the texture it was registered as */
    RelicShapeRenderer(RelicShape.Model shape, Map<String, Identifier> tiles) {
        this.faces = shape.faces();
        Map<Identifier, List<Integer>> by = new LinkedHashMap<>();
        for (int f = 0; f < shape.size(); f++) {
            String name = shape.textures()[f];
            Identifier texture = name == null ? null : tiles.get(name);
            by.computeIfAbsent(texture == null ? WHITE : texture, k -> new ArrayList<>()).add(f * FxSolid.FLOATS);
        }
        by.forEach((texture, starts) -> groups.add(new Group(texture, starts.stream().mapToInt(Integer::intValue).toArray(),
                !texture.equals(WHITE))));
        float[] b = RelicShape.bounds(faces);
        corners = new Vector3fc[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f(b[(i & 1) == 0 ? 0 : 3] + 0.5f, b[(i & 2) == 0 ? 1 : 4] + 0.5f, b[(i & 4) == 0 ? 2 : 5] + 0.5f);
        }
    }

    Supplier<Vector3fc[]> extents() {
        return () -> corners;
    }

    /** The shapes' render type over a texture: a material's tile, or one white texel (made the first time it is needed). */
    private RenderType type(Identifier texture) {
        RenderType type = types.get(texture);
        if (type == null) {
            if (texture.equals(WHITE) && !whiteMade) {
                NativeImage white = new NativeImage(1, 1, false);
                white.setPixel(0, 0, 0xFFFFFFFF);
                Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "gptgod relic white", white));
                whiteMade = true;
            }
            type = RenderType.create("gptgod_relic_shape",
                    RenderSetup.builder(PIPELINE).withTexture("Sampler0", texture).useLightmap().setOutline(RenderSetup.OutlineProperty.NONE)
                            .createRenderSetup());
            types.put(texture, type);
        }
        return type;
    }

    @Override
    public void submit(@Nullable Object argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            int overlayCoords, boolean hasFoil, int outlineColor) {
        for (Group group : groups) {
            submitNodeCollector.submitCustomGeometry(poseStack, type(group.texture()), (pose, buffer) -> {
                Vector3f a = new Vector3f(), b = new Vector3f(), n = new Vector3f();
                int v = FxSolid.VERTEX;
                for (int f : group.starts()) {
                    // The face's normal, for the shader. The item's square is 0..1, its centre at 0.5.
                    a.set(faces[f + v] - faces[f], faces[f + v + 1] - faces[f + 1], faces[f + v + 2] - faces[f + 2]);
                    b.set(faces[f + 2 * v] - faces[f], faces[f + 2 * v + 1] - faces[f + 1], faces[f + 2 * v + 2] - faces[f + 2]);
                    a.cross(b, n);
                    if (n.lengthSquared() > 1e-12f) n.normalize();
                    else n.set(0, 0, 1);
                    for (int k = 0; k < 4; k++) {
                        int at = f + k * v;
                        buffer.addVertex(pose, faces[at] + 0.5f, faces[at + 1] + 0.5f, faces[at + 2] + 0.5f)
                                .setColor(clamp(faces[at + 3]), clamp(faces[at + 4]), clamp(faces[at + 5]), clamp(faces[at + 6]))
                                .setUv(group.tiled() ? faces[at + 7] : 0.5f, group.tiled() ? faces[at + 8] : 0.5f)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(pose, n.x, n.y, n.z);
                    }
                }
            });
        }
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (Vector3fc c : corners) output.accept(c);
    }

    @Override
    public @Nullable Object extractArgument(ItemStack stack) {
        return null;
    }
}
