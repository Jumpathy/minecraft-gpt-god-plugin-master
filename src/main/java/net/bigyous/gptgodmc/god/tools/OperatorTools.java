package net.bigyous.gptgodmc.god.tools;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import net.bigyous.gptgodmc.god.Edicts;

/** Answering to the power above the god: closing the operator's binding commands. */
final class OperatorTools {
    private OperatorTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("command_done",
                "Close one of the BINDING COMMANDS from the server's operator (shown in your perception with a "
                        + "number). Call it once you have carried the command out (outcome done), or when it truly "
                        + "cannot be done (outcome impossible: a power the server has disabled, a mortal who is not "
                        + "here); your report is passed to the operator word for word, so write it plainly. Mortal "
                        + "requests are not commands and never need this.",
                Schema.object()
                        .integer("number", "The command's number, e.g. 3 for #3.", true, 1, 1_000_000)
                        .choice("outcome", "done = carried out; impossible = it cannot be done.", true,
                                List.of("done", "impossible"))
                        .str("report", "One or two plain sentences for the operator: what you did, or why it "
                                + "cannot be done.", true),
                a -> {
                    int number = a.integer("number", 0, 0, 1_000_000);
                    String outcome = a.str("outcome").toLowerCase(Locale.ROOT);
                    if (!outcome.equals("done") && !outcome.equals("impossible")) {
                        throw new ToolException("outcome must be done or impossible.");
                    }
                    String report = Edicts.clean(a.str("report"));
                    Edicts.Edict e = Edicts.book().find(number);
                    if (e == null) {
                        List<Edicts.Edict> open = Edicts.book().pending();
                        throw new ToolException("There is no pending command #" + number + ". " + (open.isEmpty()
                                ? "No operator commands are waiting."
                                : "Waiting: " + open.stream().map(x -> "#" + x.number).collect(Collectors.joining(", "))
                                        + "."));
                    }
                    if (e.standing()) {
                        return ToolResult.ok("Command #" + number + " is a standing command: it stays in force until "
                                + "it lapses, and needs no report. Keep obeying it.");
                    }
                    Edicts.resolve(number, outcome.equals("impossible"), report);
                    return ToolResult.ok("Command #" + number + " is closed as " + outcome
                            + "; the operator has your report.");
                }));
    }
}
