package net.bigyous.gptgod.client.relic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Transparency;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a relic from its art: the sprite as little stacked cubes ({@link RelicMesh}), handed to the
 * game's own item renderer as ordinary quads that use the relic's texture instead of the item atlas. So
 * hands, inventories, the ground, item frames and item displays all work as for any item, enchantment
 * glint included, and a relic is lit and shaded like any item: by the light where it is, darker at
 * night, its sides by the way they face. The sides take the colours the painter gave them
 * (RelicArt.walls), when it gave any. How the item sits in each place is borrowed from a vanilla item: a
 * stick for blades and staffs (item/handheld), paper for the rest (item/generated).
 */
final class RelicItemModel implements ItemModel {
    private static final Identifier HANDHELD = Identifier.withDefaultNamespace("stick");
    private static final Identifier GENERATED = Identifier.withDefaultNamespace("paper");
    /** Past this many quads held by all relics together they are thrown away and built again as needed. */
    private static final int QUAD_BUDGET = 300_000;
    private static int quadsHeld;

    private final RelicArt art;
    private final Identifier texture;
    private final @Nullable Identifier wallsTexture;
    private final Relics.Sheet sheet;
    private final Supplier<Vector3fc[]> extents;
    private final ItemQuads[] frames;
    /** The atlas sprite the frames were built with: every quad has to name one, though the picture comes from our texture. */
    private TextureAtlasSprite builtWith;

    RelicItemModel(RelicArt art, Identifier texture, @Nullable Identifier wallsTexture, Relics.Sheet sheet) {
        this.art = art;
        this.texture = texture;
        this.wallsTexture = wallsTexture;
        this.sheet = sheet;
        this.frames = new ItemQuads[art.frames()];
        float[] b = RelicMesh.bounds(art);
        Vector3fc[] corners = new Vector3fc[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f(b[(i & 1) == 0 ? 0 : 3], b[(i & 2) == 0 ? 1 : 4], b[(i & 4) == 0 ? 2 : 5]);
        }
        this.extents = () -> corners;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
            @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        output.appendModelIdentityElement(this);
        int frame = art.frameAt(sheet.age());
        Borrowed place;
        ItemQuads quads;
        try {
            place = Borrowed.from(art.kind().handheld() ? HANDHELD : GENERATED, item, resolver, displayContext, level, owner, seed);
            quads = quads(frame, place.particle());
        } catch (RuntimeException e) {
            return; // a relic must never take the game down with it: it is simply not drawn
        }
        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        if (item.hasFoil()) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            output.setAnimated();
            output.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
        }
        layer.setExtents(extents);
        layer.setUsesBlockLight(place.blockLight());
        if (place.particle() != null) layer.setParticleMaterial(place.particle());
        layer.setItemTransform(place.transform());
        layer.setQuads(quads);
        if (art.frames() > 1) {
            // Inventories keep a picture of each item; this makes them take a new one as the frames go by.
            output.setAnimated();
            output.appendModelIdentityElement(frame);
        }
    }

    /** Drops the quads built so far; they are built again when next drawn. */
    void forgetQuads() {
        java.util.Arrays.fill(frames, null);
    }

    /** One frame's quads, built the first time it is shown (and again if the game reloads its atlases). */
    private ItemQuads quads(int frame, Material.@Nullable Baked borrowed) {
        TextureAtlasSprite sprite = borrowed != null ? borrowed.sprite()
                : Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).missingSprite();
        if (sprite != builtWith) {
            forgetQuads();
            builtWith = sprite;
        }
        ItemQuads built = frames[frame];
        if (built == null) {
            if (quadsHeld > QUAD_BUDGET) {
                Relics.forgetQuads();
                quadsHeld = 0;
            }
            // The game's own item render types, pointed at the relic's texture (and at its walls for the sides).
            boolean translucent = art.translucent();
            BakedQuad.MaterialInfo faces = material(sprite, translucent, texture);
            BakedQuad.MaterialInfo sides = wallsTexture == null ? faces : material(sprite, translucent, wallsTexture);
            List<BakedQuad> quads = new ArrayList<>();
            for (RelicMesh.Face face : RelicMesh.build(art, frame)) {
                float[] b = RelicMesh.box(art, face);
                boolean side = face.side() != RelicMesh.Side.FRONT && face.side() != RelicMesh.Side.BACK;
                if (side && wallsTexture != null) {
                    quads.add(quad(direction(face.side()), b, sides, (x, y) -> RelicMesh.wallUv(art, face, frame, x, y)));
                } else {
                    quads.add(quad(direction(face.side()), b, faces, (x, y) -> RelicMesh.uv(art, face, frame, x, y)));
                }
            }
            quadsHeld += quads.size();
            built = frames[frame] = ItemQuads.split(quads);
        }
        return built;
    }

    private static BakedQuad.MaterialInfo material(TextureAtlasSprite sprite, boolean translucent, Identifier texture) {
        RenderType type = translucent ? RenderTypes.itemTranslucent(texture) : RenderTypes.itemCutout(texture);
        RenderType glint = translucent ? RenderTypes.itemTranslucentGlint(texture) : RenderTypes.itemCutoutGlint(texture);
        return new BakedQuad.MaterialInfo(sprite,
                ChunkSectionLayer.byTransparency(translucent ? Transparency.TRANSLUCENT : Transparency.TRANSPARENT), type, glint, glint,
                -1, null, 0);
    }

    private static Direction direction(RelicMesh.Side side) {
        return switch (side) {
            case FRONT -> Direction.SOUTH;
            case BACK -> Direction.NORTH;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case LEFT -> Direction.WEST;
            case RIGHT -> Direction.EAST;
        };
    }

    /** Where on its texture a corner at block position x, y takes its colour. */
    private interface UvAt {
        float[] at(float x, float y);
    }

    /** A flat box (min x, y, z then max x, y, z) as a quad looking one way. */
    private static BakedQuad quad(Direction direction, float[] b, BakedQuad.MaterialInfo material, UvAt uvAt) {
        Vector3f min = new Vector3f(b[0], b[1], b[2]), max = new Vector3f(b[3], b[4], b[5]);
        // The corners in the order the game winds a face looking this way, so it is the outside that shows.
        FaceInfo corners = FaceInfo.fromFacing(direction);
        Vector3f[] at = new Vector3f[4];
        long[] uv = new long[4];
        for (int i = 0; i < 4; i++) {
            at[i] = corners.getVertexInfo(i).select(min, max);
            float[] t = uvAt.at(at[i].x, at[i].y);
            uv[i] = UVPair.pack(t[0], t[1]);
        }
        return new BakedQuad(at[0], at[1], at[2], at[3], uv[0], uv[1], uv[2], uv[3], direction, material);
    }

    /**
     * What a relic takes from a vanilla item's model for one way of showing it: where it sits, how it is
     * lit in inventories, and the sprite its crumbs are cut from.
     */
    private record Borrowed(ItemTransform transform, boolean blockLight, Material.@Nullable Baked particle) {
        private static final ItemDisplayContext[] CONTEXTS = ItemDisplayContext.values();
        private static ItemModel handheldFrom, generatedFrom;
        private static final Borrowed[] handheld = new Borrowed[CONTEXTS.length], generated = new Borrowed[CONTEXTS.length];

        /** Asked of the lender once per way of showing; asked again when a resource reload replaces the lender. */
        static Borrowed from(Identifier lender, ItemStack item, ItemModelResolver resolver, ItemDisplayContext context,
                @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            ItemModel model = Minecraft.getInstance().getModelManager().getItemModel(lender);
            boolean hand = lender == HANDHELD;
            Borrowed[] known = hand ? handheld : generated;
            if (model != (hand ? handheldFrom : generatedFrom)) {
                java.util.Arrays.fill(known, null);
                if (hand) handheldFrom = model;
                else generatedFrom = model;
            }
            Borrowed b = known[context.ordinal()];
            if (b == null) {
                Probe probe = new Probe();
                model.update(probe, item, resolver, context, level, owner, seed);
                b = known[context.ordinal()] = new Borrowed(probe.seen.transform, probe.seen.blockLight, probe.seen.particle);
            }
            return b;
        }
    }

    /** A render state that only notes what a model sets on its layer. */
    private static final class Probe extends ItemStackRenderState {
        final Seen seen = new Seen(this);

        @Override
        public LayerRenderState newLayer() {
            return seen;
        }
    }

    private static final class Seen extends ItemStackRenderState.LayerRenderState {
        ItemTransform transform = ItemTransform.NO_TRANSFORM;
        boolean blockLight;
        Material.Baked particle;

        Seen(ItemStackRenderState owner) {
            owner.super();
        }

        @Override
        public void setItemTransform(ItemTransform transform) {
            this.transform = transform;
        }

        @Override
        public void setUsesBlockLight(boolean usesBlockLight) {
            this.blockLight = usesBlockLight;
        }

        @Override
        public void setParticleMaterial(Material.Baked particleMaterial) {
            this.particle = particleMaterial;
        }
    }
}
