package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.GodSettings.CustomPower;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;

/**
 * Powers the server owner writes in config.yml (custom-powers): lists of commands with placeholders
 * that the god fills in. The commands are trusted, so the blocklist does not apply.
 */
final class CustomPowerTools {
    /** {player}, {world}, {god}, {text}, or {x}/{y}/{z} with an optional offset like {y+40}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(?:([xyz])([+-]\\d{1,6})?|(player|world|god|text))\\}");
    private static final Pattern COORD = Pattern.compile("\\{[xyz](?:[+-]\\d{1,6})?\\}");

    private CustomPowerTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("custom_power", CustomPowerTools::description,
                Schema.object()
                        .str("power", "The name of one of this server's custom powers listed above.", true)
                        .str("target", "Who or where, if the power needs it: " + Targets.TARGET_HELP + ".", false)
                        .str("text", "Words the power uses (a message or title), if its description mentions them.",
                                false),
                CustomPowerTools::run));
    }

    static String description() {
        if (!GodSettings.customPowersAllowed()) {
            return "Use one of this server's custom powers. The server has turned custom powers off.";
        }
        List<CustomPower> powers = GodSettings.customPowers();
        if (powers.isEmpty()) {
            return "Use one of this server's custom powers. None are configured on this server yet.";
        }
        StringBuilder sb = new StringBuilder("Use one of the powers the keepers of this server made for you:");
        for (CustomPower p : powers) {
            sb.append("\n- ").append(p.name()).append(": ").append(p.description());
            if (p.cooldownSeconds() > 0) {
                sb.append(" (once every ").append(p.cooldownSeconds()).append("s)");
            }
        }
        return sb.toString();
    }

    private static ToolResult run(Args a) throws ToolException {
        if (!GodSettings.customPowersAllowed()) {
            return ToolResult.error("The server has turned custom powers off.");
        }
        List<CustomPower> powers = GodSettings.customPowers();
        String wanted = a.str("power").trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        CustomPower power = null;
        List<String> names = new ArrayList<>();
        for (CustomPower p : powers) {
            names.add(p.name());
            if (p.name().equals(wanted)) power = p;
        }
        if (power == null) {
            return ToolResult.error(names.isEmpty() ? "No custom powers are configured on this server."
                    : "No custom power '" + wanted + "'. Yours: " + String.join(", ", names) + ".");
        }
        long now = System.currentTimeMillis();
        Long last = Realm.current().cooldowns.get(power.name());
        if (last != null && now - last < power.cooldownSeconds() * 1000L) {
            long left = (power.cooldownSeconds() * 1000L - (now - last) + 999) / 1000;
            return ToolResult.error(power.name() + " is still gathering strength; " + left + "s left.");
        }

        Location where = null;
        String player = null;
        if (a.has("target")) {
            String t = a.str("target");
            where = Targets.location(t);
            try {
                Player p = Targets.player(t);
                player = p.getName();
                where = p.getLocation();
            } catch (ToolException ignored) {
                // a place, not a mortal
            }
        }
        Persona persona = Persona.current();
        Context ctx = new Context(player,
                where == null ? null : new int[] { where.getBlockX(), where.getBlockY(), where.getBlockZ() },
                where == null ? null : where.getWorld().getKey().asString(),
                persona == null ? "God" : persona.shortName(), a.str("text", ""));

        List<String> commands = new ArrayList<>();
        for (String template : power.commands()) {
            String cmd = fill(template, ctx);
            // Absolute coordinates belong to the target's world, wherever the console stands.
            if (ctx.world() != null && COORD.matcher(template).find() && !cmd.startsWith("execute")
                    && !cmd.startsWith("/execute")) {
                cmd = "execute in " + ctx.world() + " run " + (cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
            commands.add(cmd);
        }
        Realm.current().cooldowns.put(power.name(), now);
        WorldTools.CommandReport report = WorldTools.dispatch(commands, false,
                ctx.world() == null ? WorldTools::routeToWorld : c -> c);
        String head = "You invoke " + power.name() + (player != null ? " on " + player
                : where != null ? " at " + Targets.describe(where) : "") + ".\n";
        return report.anyFailed() ? ToolResult.error(head + report.text()) : ToolResult.ok(head + report.text());
    }

    /** What the placeholders stand for in one use of a power. Missing values are null. */
    record Context(String player, int[] xyz, String world, String god, String text) {
    }

    /** Fills a command's placeholders; a placeholder the power needs but did not get is an error for the god. */
    static String fill(String template, Context ctx) throws ToolException {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value;
            if (m.group(1) != null) {
                if (ctx.xyz() == null) {
                    throw new ToolException("This power needs a target (a mortal or a place).");
                }
                int axis = "xyz".indexOf(m.group(1));
                int offset = m.group(2) == null ? 0 : Integer.parseInt(m.group(2).replace("+", ""));
                value = Integer.toString(ctx.xyz()[axis] + offset);
            } else {
                value = switch (m.group(3)) {
                    case "player" -> {
                        if (ctx.player() == null) {
                            throw new ToolException("This power needs a mortal as its target.");
                        }
                        yield ctx.player();
                    }
                    case "world" -> {
                        if (ctx.world() == null) {
                            throw new ToolException("This power needs a target (a mortal or a place).");
                        }
                        yield ctx.world();
                    }
                    case "god" -> sanitize(ctx.god());
                    default -> sanitize(ctx.text());
                };
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The god's free words, made safe to drop inside a command or a JSON string. */
    static String sanitize(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("[\"'\\\\{}\\[\\]§\\r\\n\\t]", " ").replaceAll("\\s+", " ").trim();
        return clean.length() > 120 ? clean.substring(0, 120).trim() : clean;
    }
}
