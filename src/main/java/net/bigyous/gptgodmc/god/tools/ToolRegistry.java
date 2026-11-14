package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Sync;

/** Every power the god has, in a fixed order (a stable tool list keeps the prompt cache warm). */
public final class ToolRegistry {
    private static final Gson GSON = new Gson();
    private static final Map<String, GodTool> TOOLS = new LinkedHashMap<>();
    private static List<ToolUnion> sdkTools;
    /** The descriptions sdkTools was built from; a change (e.g. new custom powers after /god reload) rebuilds it. */
    private static List<String> builtFrom;

    static {
        List<GodTool> all = new ArrayList<>();
        all.addAll(VoiceTools.all());
        all.addAll(KnowledgeTools.all());
        all.addAll(MemoryTools.all());
        all.addAll(BlessingTools.all());
        all.addAll(WrathTools.all());
        all.addAll(WorldTools.all());
        all.addAll(BuildTools.all());
        all.addAll(VesselTools.all());
        all.addAll(CustomPowerTools.all());
        all.addAll(RiteTools.all());
        all.addAll(DevotionTools.all());
        all.addAll(OperatorTools.all());
        all.addAll(MusicTools.all());
        // Last, so adding it did not reorder the tools before it (prompt cache).
        all.addAll(PantheonTools.all());
        // Appended after the rest, for the same reason.
        all.addAll(PurposeTools.all());
        all.addAll(LookTools.all());
        all.addAll(AuraTools.all());
        all.addAll(ApotheosisTools.all());
        // Free will (animations, handiwork, pastimes): appended last for the prompt cache.
        all.addAll(FreeWillTools.all());
        // Bonds between gods, holy days and servants: appended last for the prompt cache.
        all.addAll(LivingTools.all());
        // The god's own visual effects (client mod scenes): appended last for the prompt cache.
        all.addAll(FxTools.all());
        // Its style, library routines, bound effects and omens: appended last for the prompt cache.
        all.addAll(FxBrainTools.all());
        // The minimap: appended last for the prompt cache.
        all.addAll(MapTools.all());
        all.addAll(DropTools.all());
        // The forge (relics with painted art): appended last for the prompt cache.
        all.addAll(RelicTools.all());
        // The living land and oaths: appended last for the prompt cache.
        all.addAll(EcologyTools.all());
        all.addAll(OathTools.all());
        // WorldEdit, when the server has that plugin (always registered, so the list stays stable): appended last.
        all.addAll(WorldEditTools.all());
        // Textures the god paints for its effects: appended last for the prompt cache.
        all.addAll(FxPaintTools.all());
        // Its own say over its heart (feel): appended last for the prompt cache.
        all.addAll(HeartTools.all());
        // Its private mind (muse, reflect): appended last for the prompt cache.
        all.addAll(MindTools.all());
        // Programs it writes itself (script): appended last for the prompt cache.
        all.addAll(ScriptTools.all());
        // Worlds of its own making and the labyrinth: appended last for the prompt cache.
        all.addAll(DimensionTools.all());
        // Other plugins' gifts, when a server has them (ward, land_history, disguise): appended last.
        all.addAll(HookTools.all());
        // Its own relics in its own hand (armory, strike): appended last for the prompt cache.
        all.addAll(ArmoryTools.all());
        // Last: a statue copied from a mortal's own skin.
        all.addAll(StatueTools.all());
        // A web search whose answer other powers and scripts can use: appended last for the prompt cache.
        all.addAll(WebTools.all());
        // Where the nearest structure or biome lies (locate): appended last for the prompt cache.
        all.addAll(LocateTools.all());
        for (GodTool t : all) {
            TOOLS.put(t.name(), t);
        }
    }

    private ToolRegistry() {
    }

    public static Collection<GodTool> all() {
        return TOOLS.values();
    }

    public static GodTool get(String name) {
        return TOOLS.get(name);
    }

    /**
     * Tool definitions for the Claude API. Rebuilt only when a description changes, so the list (and the
     * prompt cache) stays stable between thoughts.
     */
    public static synchronized List<ToolUnion> sdkTools() {
        List<String> descriptions = new ArrayList<>();
        TOOLS.values().forEach(t -> descriptions.add(t.description()));
        if (sdkTools == null || !descriptions.equals(builtFrom)) {
            List<ToolUnion> out = new ArrayList<>();
            int i = 0;
            for (GodTool t : TOOLS.values()) {
                Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
                t.schema().properties().forEach((k, v) -> props.putAdditionalProperty(k, JsonValue.from(v)));
                Tool tool = Tool.builder()
                        .name(t.name())
                        .description(descriptions.get(i++))
                        .inputSchema(Tool.InputSchema.builder()
                                .properties(props.build())
                                .required(t.schema().required())
                                .build())
                        .build();
                out.add(ToolUnion.ofTool(tool));
            }
            sdkTools = List.copyOf(out);
            builtFrom = descriptions;
        }
        return sdkTools;
    }

    /** Every power in provider-neutral form (name, description, argument schema), in the same fixed order. */
    public static List<net.bigyous.gptgodmc.god.llm.LlmTool> specs() {
        List<net.bigyous.gptgodmc.god.llm.LlmTool> out = new ArrayList<>();
        for (GodTool t : TOOLS.values()) {
            out.add(new net.bigyous.gptgodmc.god.llm.LlmTool(t.name(), t.description(), t.schema().properties(),
                    t.schema().required()));
        }
        return out;
    }

    /** Result plus a one-line description of the act for the god's memory. */
    public record Outcome(ToolResult result, String act) {
    }

    /**
     * Runs a tool from any thread. Main-thread tools are executed on the server thread.
     *
     * @param source "brain" for the god's own thoughts; anything else is recorded as an outside act.
     */
    public static Outcome execute(String name, JsonObject input, String source) {
        GodTool tool = TOOLS.get(name);
        ToolResult result;
        if (tool == null) {
            result = ToolResult.error("You have no power called '" + name + "'.");
        } else {
            Args args = new Args(input);
            // A god's power limits what it may do on its own; operators (/god tool, the menu) are not limited.
            String refused = "brain".equals(source) ? PantheonTools.refusal(name) : null;
            // After a mortal tried to manipulate it, its most dangerous powers are locked for a while.
            if (refused == null && "brain".equals(source)) refused = net.bigyous.gptgodmc.god.Guard.refusal(name);
            if (refused != null) {
                result = ToolResult.error(refused);
            } else try {
                result = tool.mainThread() ? Sync.call(() -> tool.handler().run(args), 15_000)
                        : tool.handler().run(args);
            } catch (ToolException e) {
                result = ToolResult.error(e.getMessage());
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Power " + name + " crashed", e);
                result = ToolResult.error("The power failed unexpectedly: " + e);
            }
        }
        if (!result.isError()) {
            try {
                VesselReactions.after(name, input);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Vessel reaction failed", e);
            }
            try {
                RiteTools.after(name, input);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Rite failed", e);
            }
            try {
                PantheonTools.witness(name, input, result);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Could not show the act to the other gods", e);
            }
            try {
                net.bigyous.gptgodmc.god.Signs.after(name, input); // the mortals it touched read who did it
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Sign failed", e);
            }
            try {
                FxBrainTools.afterPower(name, input); // an effect the god bound to this power (fx_bind)
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Bound effect failed", e);
            }
        }
        String act = describe(name, input, result);
        Journal.add(result.isError() ? Journal.Kind.ERROR : Journal.Kind.TOOL, act);
        if (!"brain".equals(source)) {
            Memory.get().recordOutsideAct(source, act);
        }
        return new Outcome(result, act);
    }

    static String describe(String name, JsonObject input, ToolResult result) {
        String args = input == null ? "{}" : GSON.toJson(input);
        if (args.length() > 300) args = args.substring(0, 300) + "…}";
        String text = result.text() == null ? "" : result.text().replace('\n', ' ');
        if (result.hasImage()) text = "(saw an image) " + text;
        if (text.length() > 220) text = text.substring(0, 220) + "…";
        return name + args + (result.isError() ? " ✗ " : " → ") + text;
    }
}
