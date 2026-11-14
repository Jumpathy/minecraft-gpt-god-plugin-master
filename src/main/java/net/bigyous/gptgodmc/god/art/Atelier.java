package net.bigyous.gptgodmc.god.art;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodClient;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Llm;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;

/**
 * The gods' artist. A god says what it wants made; this asks a strong model, thinking hard, to draw it,
 * and it works the way a person does: it paints (the `paint` tool, a map of materials for
 * {@link SpritePainter}), is shown the result ({@link SpriteSheet#proof}) with a teacher's notes
 * ({@link SpriteLint}), and paints again until it is right or its attempts are spent. The last sprite
 * that painted cleanly is the work.
 *
 * <p>This is a separate conversation from the god's own thoughts, on its own model and effort
 * ({@link ArtSettings}): the god keeps thinking cheaply and only the drawing is expensive. Blocking; call
 * it off the server thread. Its tokens are logged under the purpose "relic".
 */
public final class Atelier {
    private Atelier() {
    }

    /**
     * What to draw.
     *
     * @param name what the thing is called
     * @param kind blade, staff, trinket, tome or spirit (see {@link #KINDS})
     * @param look the god's own description of it
     * @param god  a line about the god that asks (name, nature, colors), so the art suits it; may be blank
     * @param special  the god holds this relic to be special: the artist may sculpt its form (thickness,
     *                 rounding, films of light) rather than leave it a sprite of even thickness
     * @param twoForms it needs a second, awakened form (its powers wake it)
     */
    public record Brief(String name, String kind, String look, String god, boolean special, boolean twoForms) {
        public Brief(String name, String kind, String look, String god) {
            this(name, kind, look, god, false, false);
        }
    }

    /** Set by whoever commissions on this thread: told (sprites painted so far, attempts allowed) before each step. */
    public static final ThreadLocal<java.util.function.BiConsumer<Integer, Integer>> WATCH = new ThreadLocal<>();

    /** The finished work: the sprite as written, painted, how many attempts it took, and the artist's last words. */
    public record Made(Sprite sprite, Art art, int attempts, String said) {
    }

    /** Why nothing was made, phrased for the god. */
    public static final class Failed extends Exception {
        private static final long serialVersionUID = 1L;

        public Failed(String message) {
            super(message);
        }
    }

    /** Whoever does the drawing (a seam so tests can stand in for the model). */
    @FunctionalInterface
    public interface Artist {
        Made make(Brief brief) throws Failed;
    }

    public static volatile Artist artist = Atelier::ask;

    /** The kinds of thing, with what each is to the artist. */
    public static final Map<String, String> KINDS = kinds();

    private static Map<String, String> kinds() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("blade", "a weapon held in the hand: draw it on the diagonal, grip at the bottom left, point at the top right");
        m.put("staff", "a staff, wand, tool or rod held in the hand: on the diagonal, foot at the bottom left, head at the "
                + "top right");
        m.put("trinket", "a small precious thing shown upright: an amulet, ring, crown, mask, vial, idol, key");
        m.put("tome", "a book, tablet or scroll shown upright");
        m.put("spirit", "a small being that floats in the world, seen flat from the front: a face or an eye first; its "
                + "lights spill a soft halo");
        return m;
    }

    /** True when lights in this kind spill a halo (it is seen flat in the world, never held). */
    public static boolean halo(String kind) {
        return "spirit".equals(kind);
    }

    // ---- The craft, as told to the artist --------------------------------------------------------------

    private static volatile String system;

    /** The artist's standing instructions: how to draw here, what makes it good, and worked examples. */
    public static String system() {
        String s = system;
        if (s == null) {
            s = buildSystem();
            system = s;
        }
        return s;
    }

    private static String buildSystem() {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are the pixel artist of a god in a Minecraft world. The god describes a thing it wants made, \
                and you draw it as a small sprite. Your drawing becomes a real thing in the game: an item in a \
                player's hand and hotbar, or a spirit floating in the world. It is seen 16 to 32 texels across, \
                so it has to read at a glance at that size.

                # How you draw

                You do not choose pixel colors. You call `paint` with a map of materials: a legend that gives \
                each letter a material, and rows of letters that place them. The painter then does the part of \
                the craft that is the same every time: it shades every shape from one light, darkens the edge \
                that faces away from it, puts a hard highlight and a passing glint on metal, a lit facet, a dark \
                facet and a sparkle in gems, grain along wood, and it animates whatever burns or shines. Your \
                part is the part that needs an eye: the silhouette, the proportions, which material goes where, \
                and where the one bright thing sits.

                After each `paint` you are shown the result: large on a dark ground with its rows and columns \
                numbered, on sky and grass, at hotbar size, and its animation frames, with a few notes. Look at \
                it as a stranger would, seeing it for a second in a hotbar. Then either paint again (send the \
                whole sprite, corrected) or stop. Expect to revise: a first attempt nearly always has a part too \
                thin to shade, a patch that does not read, or something a texel out of place. Stop when another \
                change would not make it clearer.

                # The sprite

                - "size": 16, 24 or 32 (a square). 16 sits beside vanilla items and every texel counts; use 24 \
                or 32 when the thing needs more shape than 16 can hold. A spirit may give "width" and "height" \
                instead (each 8 to 32) when it is taller than wide.
                - "legend": an object from one letter to a material.
                  Materials with their own look: gold, silver, steel, iron, bronze, copper, obsidian, stone, \
                marble, bone, wood, darkwood, leather, flame, frost, void (night sky with stars), soul (cold fire).
                  Materials that take a color you choose: cloth, gem, glow, hide (skin, fur, scales, feathers). \
                Write the color after the name: "gem #2aa6e0", "glow #ffd86a". A bare color such as "#3a2a66" \
                is plain matte paint for anything else.
                  A tone word after it (deep, dark, base, light, bright) fixes that letter to one flat tone. \
                Use it only where you must, such as "#05040a deep" for the black of an eye; everywhere else \
                leave the shading to the painter.
                - "rows": exactly as many strings as the canvas is tall, each exactly as many characters as it \
                is wide. "." is empty.
                - "accents" (optional): rows of the same shape that push single texels: "+" one tone lighter, \
                "-" one tone darker, "*" a sparkle that twinkles, "." no change.
                - "light" (optional): "top-left" (the default), "top" or "top-right".
                - "outline" (optional): "soft" (the default: far edges are shaded darker), "dark" (a dark line \
                round the whole thing, for pale things that would vanish against sky), or "none".

                # Its form, for special relics

                Every sprite is also a solid thing: from the side it has thickness. Ordinarily leave that \
                alone, and the whole thing is as thick as any Minecraft item. When the commission says the \
                relic is special, sculpt it, with words after a material in the legend:
                - "thick N": how thick that material is front to back, in texels (0 to 12). A guard thicker \
                than its blade, a pommel thicker than its grip, a heavy head on a slim shaft. The usual \
                thickness is 1 on a 16 canvas and 2 on a 24 or 32 one.
                - "round": the material is thickest along the middle of each patch and thins toward its edges, \
                so a band three wide becomes a rod and a disc becomes a ball: "gold thick 5 round".
                - "flat": no thickness at all, a film, for flames, rays and light that should not be a slab.
                Keep it believable. Thickness near the width of a part looks turned or cast; much more is a \
                plank on edge. A stroke only a few texels across that runs on the diagonal stays at the usual \
                thickness whatever you write, because given depth each of its rows stands out from the next like a \
                stair: give thickness to broad parts and to parts that run straight up, down or across. In the \
                world everything is lit and shaded like any other item, glow and flame too: their bright \
                colours, a pale heart and a deeper rim, are what say that they shine. \
                The picture you are shown includes the thing turned three quarters, so you can \
                judge it as an object.

                A special relic may also have a true shape: real geometry that players with the client mod see in \
                place of the picture given thickness, so a blade can curve and a staff be round. Draw the \
                picture first and get it right (it is still every inventory icon, and what everyone else \
                holds), then trace it in parts. \
                """).append(RelicShape.HELP).append("""
                 You are shown the shape \
                from the front and turned three quarters under the picture; front-on it should cover the picture. \
                With two forms, "alt_shape" is the shape awake. Leave the shape out of plain relics.

                When the commission asks for two forms, also give "alt_rows" (and "alt_accents" if you use \
                accents): a second drawing on the same canvas with the same legend. "rows" is the thing \
                asleep, as it is first held; "alt_rows" is the thing awake: a hilt with its blade of light \
                lit, a shut eye open, a furled banner flying. Whatever is in both forms must sit on exactly \
                the same texels in both, because it changes in the bearer's hand. The asleep form may be small \
                in its canvas; it is the awake form that should fill it. But in first person only the upper \
                right of a held thing is in view, so let the asleep part reach at least two fifths of the way \
                up the diagonal, or its bearer will barely see what they hold.

                # What makes it good

                - Silhouette first. Filled with one flat color, would it still be known for what it is? If not, \
                change the shape before anything else. Empty space inside and around the thing is part of the \
                drawing.
                - Fill the canvas. The thing should come within a texel or two of the edge along its longest \
                side. A small drawing in the middle of a big canvas is the most common mistake.
                - Held things lie on the diagonal: grip or foot at the bottom left, point or head at the top \
                right, the way Minecraft's swords and tools do.
                - Thickness is what gets shaded. Three texels across a blade or a limb gives a lit edge, a flat \
                and a dark edge. Two gives light and dark. One texel is for chains, strings and cracks only.
                - Three or four materials, each in its own clear place, look designed. One material looks \
                unfinished; six look busy. Keep each material in patches large enough to be seen as that material.
                - One light. A single gem, glow, flame or sparkle gives the eye a place to land. Put it where \
                the thing's meaning is: the pommel, the heart, the eye. Two can work; five cancel each other.
                - Proportion carries character: a heavy head on a thin shaft, a thin chain and a big stone, a \
                broad blade and a short grip. Even proportions look generic.
                - A little asymmetry looks made by hand. Mirror symmetry suits crowns, masks and faces; most \
                other things are better with one side different.
                - A spirit is a face first: eyes placed with care, then a body that says what it is. Leave the \
                eyes dark and flat so they read from far away.
                - Suit the god. The brief says who asks and what its colors and nature are. A relic of a god of \
                embers should not look like one from a god of the deep.

                # What to avoid

                - Stray single texels and checkerboard speckle: they read as noise.
                - Drawing your own outline or your own highlights with extra dark and light letters. The painter \
                does edges and light; doing it twice makes mud.
                - Filling the whole square, or a shape that touches all four edges.
                - Letters, numbers or words in the picture.
                - Tiny details that need more texels than you have. At 16, a face is two eyes; at 32, it can \
                have a brow.

                # Examples

                These were written by hand. Study how few materials each uses and how the shape does the work.
                """);
        for (Samples.Sample sample : Samples.ALL) {
            sb.append("\n").append(sample.name()).append(" (").append(sample.kind()).append("). ").append(sample.note())
                    .append("\n").append(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                            .toJson(sample.json()))
                    .append("\n");
        }
        sb.append("""

                # Finishing

                When the picture is right, answer with one short line saying what you made, and do not call \
                `paint` again. The last sprite you painted is the one that is kept.
                """);
        return sb.toString();
    }

    /** The first message of a commission. */
    static String commission(Brief b, int attempts) {
        String what = KINDS.getOrDefault(b.kind(), KINDS.get("trinket"));
        String form = !b.special() ? ""
                : "\nThis relic is special to it: sculpt its form (thickness, rounding, films of light)."
                        + (b.twoForms() ? " It has two forms: draw it asleep in \"rows\" and awake in \"alt_rows\"." : "");
        return (b.god() == null || b.god().isBlank() ? "" : b.god().trim() + "\n\n") + "It asks you to make:\n" + "Name: "
                + b.name() + "\nKind: " + b.kind() + " (" + what + ")\nIn its own words: " + b.look() + form
                + "\n\nYou may paint up to " + attempts + " times. Begin.";
    }

    // ---- Asking the model ------------------------------------------------------------------------------

    private static Tool paintTool() {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        props.putAdditionalProperty("size", JsonValue.from(Map.of("type", "integer", "description",
                "Canvas side in texels for a square sprite: 16, 24 or 32.")));
        props.putAdditionalProperty("width", JsonValue.from(Map.of("type", "integer", "description",
                "Canvas width (8 to 32), instead of size, for a sprite that is not square.")));
        props.putAdditionalProperty("height", JsonValue.from(Map.of("type", "integer", "description",
                "Canvas height (8 to 32), instead of size.")));
        props.putAdditionalProperty("legend", JsonValue.from(Map.of("type", "object", "description",
                "One letter to one material each, e.g. {\"s\": \"steel\", \"r\": \"gem #d0203c\"}.",
                "additionalProperties", Map.of("type", "string"))));
        props.putAdditionalProperty("rows", JsonValue.from(Map.of("type", "array", "description",
                "The sprite, top row first: one string per row, one letter per texel, '.' for empty.", "items",
                Map.of("type", "string"))));
        props.putAdditionalProperty("accents", JsonValue.from(Map.of("type", "array", "description",
                "Optional rows of the same shape: '+' lighter, '-' darker, '*' sparkle, '.' no change.", "items",
                Map.of("type", "string"))));
        props.putAdditionalProperty("alt_rows", JsonValue.from(Map.of("type", "array", "description",
                "Only for a relic with two forms: the thing awake, on the same canvas with the same legend.", "items",
                Map.of("type", "string"))));
        props.putAdditionalProperty("alt_accents", JsonValue.from(Map.of("type", "array", "description",
                "Optional accents for alt_rows.", "items", Map.of("type", "string"))));
        props.putAdditionalProperty("shape", JsonValue.from(Map.of("type", "array", "description",
                "Only for a special relic: its true shape, a list of solid parts (see your instructions).", "items",
                Map.of("type", "object"))));
        props.putAdditionalProperty("alt_shape", JsonValue.from(Map.of("type", "array", "description",
                "Only with alt_rows: the true shape awake.", "items", Map.of("type", "object"))));
        props.putAdditionalProperty("light", JsonValue.from(Map.of("type", "string", "description",
                "Where the light comes from.", "enum", List.of("top-left", "top", "top-right"))));
        props.putAdditionalProperty("outline", JsonValue.from(Map.of("type", "string", "description",
                "How edges are drawn.", "enum", List.of("soft", "dark", "none"))));
        return Tool.builder().name("paint")
                .description("Paint a sprite from a map of materials and see the result. Send the whole sprite every "
                        + "time; the last one painted without an error is the one kept.")
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(List.of("legend", "rows"))
                        .build())
                .build();
    }

    /** One attempt, judged: the picture and notes for the artist, or the flaw that stopped it. */
    record Attempt(Sprite sprite, Art art, String text, byte[] proof, boolean error) {
    }

    /** Paints what the artist sent and writes what it is told back. */
    static Attempt attempt(JsonObject input, String kind, int left) {
        return attempt(input, kind, left, false);
    }

    /** @param twoForms the commission asked for a second, awakened form */
    static Attempt attempt(JsonObject input, String kind, int left, boolean twoForms) {
        Sprite sprite;
        try {
            sprite = Sprite.parse(input);
        } catch (Sprite.Flaw e) {
            return new Attempt(null, null, e.getMessage() + " Nothing was painted; send the whole sprite again.", null,
                    true);
        }
        Art art = SpritePainter.paint(sprite, halo(kind));
        Sprite awake = sprite.alt();
        Art artAwake = awake == null ? null : SpritePainter.paint(awake, halo(kind));
        // With two forms it is the awake one that carries the design and should fill the canvas.
        List<String> notes = new ArrayList<>(SpriteLint.notes(awake == null ? sprite : awake, kind));
        if (awake != null) {
            int shared = 0, moved = 0;
            for (int y = 0; y < sprite.h(); y++) {
                for (int x = 0; x < sprite.w(); x++) {
                    if (sprite.cells()[y][x] == 0) continue;
                    shared++;
                    if (awake.cells()[y][x] != sprite.cells()[y][x]) moved++;
                }
            }
            if (moved * 4 > shared) {
                notes.add(moved + " of the asleep form's " + shared + " texels are different or missing in the awake "
                        + "form. What is in both must sit on the same texels, or the thing will jump in the hand.");
            }
        } else if (twoForms) {
            notes.add(0, "The commission asks for two forms and this has one: add \"alt_rows\", the thing awake, on "
                    + "the same canvas.");
        }
        StringBuilder sb = new StringBuilder("Painted, ").append(sprite.w()).append("x").append(sprite.h())
                .append(". The picture shows it large on dark (rows and columns numbered), on sky and grass, turned ")
                .append("three quarters, at hotbar size, and its frames.")
                .append(awake == null ? "" : " Above is the thing asleep; below, the same for it awake.");
        if (notes.isEmpty()) sb.append(" No notes.");
        else {
            sb.append(" Notes:");
            for (String n : notes) sb.append("\n- ").append(n);
        }
        sb.append(left > 0 ? "\nYou may paint " + left + " more time" + (left == 1 ? "" : "s")
                + ". If it is right, say so in one line and stop."
                : "\nThat was your last attempt: this sprite is kept. Say in one line what you made.");
        java.awt.image.BufferedImage proof = SpriteSheet.proof(art);
        if (artAwake != null) proof = SpriteSheet.stacked(proof, SpriteSheet.proof(artAwake));
        // A true shape is checked strictly: a mistake in it is a mistake in the attempt.
        for (JsonArray shape : new JsonArray[] { sprite.shape(), awake == null ? null : awake.shape() }) {
            if (shape == null) continue;
            try {
                proof = SpriteSheet.stacked(proof, RelicShape.picture(RelicShape.model(RelicShape.normalized(shape), sprite.w()), 150,
                        0x17111F));
            } catch (RelicShape.Flaw e) {
                return new Attempt(null, null, "The picture painted, but its shape cannot be built: " + e.getMessage()
                        + " Send the whole sprite again with the shape mended (or without it).", null, true);
            }
        }
        if (sprite.shape() != null) sb.append(" Below them, its true shape from the front and turned three quarters"
                + (awake != null && awake.shape() != null ? ", asleep then awake." : "."));
        return new Attempt(sprite, art, sb.toString(), SpriteSheet.png(proof), false);
    }

    /** Asks the configured model (Claude, through the Anthropic SDK). */
    static Made ask(Brief brief) throws Failed {
        if (GPTGOD.get() == null) throw new Failed("There is no artist here.");
        if (!Llm.anthropic()) {
            throw new Failed("The forge needs Claude: relics are painted through the Anthropic provider only.");
        }
        boolean gateway = GodSettings.baseUrl() != null && !GodSettings.baseUrl().isBlank();
        String model = ArtSettings.model(), effort = ArtSettings.effort();
        String problem = ArtSettings.modelProblem(model, gateway);
        if (problem != null) throw new Failed("The forge is cold: " + problem);
        AnthropicClient client = GodClient.get();
        if (client == null) throw new Failed("The forge is cold: there is no API key.");

        int attempts = ArtSettings.attempts();
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.ofUser(commission(brief, attempts)));
        Sprite kept = null;
        Art art = null;
        int painted = 0;
        String said = "";
        // One request per attempt, plus one for its closing line.
        for (int turn = 0; turn <= attempts; turn++) {
            java.util.function.BiConsumer<Integer, Integer> watching = WATCH.get();
            if (watching != null) {
                try {
                    watching.accept(painted, attempts);
                } catch (RuntimeException e) {
                    // the work goes on unseen
                }
            }
            MessageCreateParams.Builder b = MessageCreateParams.builder().model(model).maxTokens(20_000)
                    .systemOfTextBlockParams(List.of(TextBlockParam.builder().text(system())
                            .cacheControl(CacheControlEphemeral.builder().build()).build()))
                    .tools(List.of(ToolUnion.ofTool(paintTool()))).messages(new ArrayList<>(messages))
                    .thinking(ThinkingConfigAdaptive.builder().build())
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(effort)).build())
                    // Each attempt re-reads the ones before it from the cache.
                    .cacheControl(CacheControlEphemeral.builder().build());
            if (GodSettings.refusalFallback() && GodClient.supportsDefaultFallback(model) && !gateway) {
                b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
                b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
            }
            Message reply;
            try {
                reply = send(client, b.build());
            } catch (AnthropicServiceException e) {
                if (kept != null) break;
                throw new Failed("The artist could not be reached (" + e.statusCode() + "). Try again later.");
            } catch (AnthropicIoException e) {
                if (kept != null) break;
                throw new Failed("The artist could not be reached. Try again later.");
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("The artist's reply broke off", e);
                if (kept != null) break;
                throw new Failed("The artist's hand failed. Try again later.");
            }
            GodClient.recordUsage(model, "relic", reply.usage());
            List<ContentBlockParam> results = new ArrayList<>();
            for (ContentBlock block : reply.content()) {
                if (block.isText()) said = block.asText().text().trim();
                if (!block.isToolUse()) continue;
                ToolUseBlock call = block.asToolUse();
                Attempt a;
                if (!call.name().equals("paint")) {
                    a = new Attempt(null, null, "There is no tool called " + call.name() + "; use paint.", null, true);
                } else if (painted >= attempts) {
                    a = new Attempt(null, null, "Your attempts are spent; the last sprite painted is kept.", null, true);
                } else {
                    painted++;
                    a = attempt(input(call), brief.kind(), attempts - painted, brief.twoForms());
                    if (!a.error()) {
                        kept = a.sprite();
                        art = a.art();
                    }
                }
                results.add(ContentBlockParam.ofToolResult(result(call.id(), a)));
            }
            if (results.isEmpty()) break; // it has stopped painting (or was cut off): what is kept stands
            try {
                messages.add(reply.toParam());
            } catch (RuntimeException e) {
                break; // a reply this SDK cannot send back; keep what there is
            }
            messages.add(MessageParam.ofUserOfContentBlockParams(results));
        }
        if (kept == null) {
            throw new Failed("The artist made nothing that could be kept" + (said.isBlank() ? "." : ": " + clip(said, 200)));
        }
        return new Made(kept, art, painted, clip(said, 240));
    }

    /** Streams one request and returns the whole reply (streaming keeps a long, thoughtful answer from timing out). */
    private static Message send(AnthropicClient client, MessageCreateParams params) {
        MessageAccumulator whole = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> response = client.messages().createStreaming(params)) {
            response.stream().forEach(whole::accumulate);
        }
        return whole.message();
    }

    /** The arguments of a tool call as JSON (an empty object when they cannot be read). */
    private static JsonObject input(ToolUseBlock call) {
        try {
            JsonElement e = JsonParser.parseString(ObjectMappers.jsonMapper().writeValueAsString(call._input()));
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static ToolResultBlockParam result(String id, Attempt a) {
        ToolResultBlockParam.Builder b = ToolResultBlockParam.builder().toolUseId(id).isError(a.error());
        if (a.proof() == null) return b.content(a.text()).build();
        return b.content(ToolResultBlockParam.Content.ofBlocks(List.of(
                ToolResultBlockParam.Content.Block.ofText(TextBlockParam.builder().text(a.text()).build()),
                ToolResultBlockParam.Content.Block.ofImage(Base64ImageSource.builder()
                        .data(Base64.getEncoder().encodeToString(a.proof()))
                        .mediaType(Base64ImageSource.MediaType.IMAGE_PNG).build()))))
                .build();
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
