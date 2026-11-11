package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** anthropic.effort: auto thinks hard only for judgment calls. */
class EffortPickerTest {
    static final Map<String, Integer> FAVOR = Map.of("Korvath", -8, "Ashwyn", 7);

    static EffortPicker.Choice pick(List<String> prayers, List<String> speech, List<String> events, boolean command) {
        return EffortPicker.choose("auto", new EffortPicker.Signals(prayers, speech, events, command, "Thessaril",
                n -> FAVOR.getOrDefault(n, 0)));
    }

    @Test
    void aQuietLookIsLow() {
        assertEquals("low", pick(List.of(), List.of(), List.of("[40s ago] Steve mined 14 stone"), false).effort());
        assertEquals("low", pick(List.of(), List.of("[3s ago] Steve said in chat: \"nice house\""), List.of(), false)
                .effort());
    }

    @Test
    void aSimpleGreetingIsMedium() {
        assertEquals("medium", pick(List.of("[2s ago] TonoCatMeow prays to you: \"hi\""), List.of(), List.of(), false)
                .effort());
        assertEquals("medium", pick(List.of(), List.of("[2s ago] Steve said in chat: \"Thessaril, are you there?\""),
                List.of(), false).effort());
    }

    @Test
    void requestsAndJudgmentCallsAreHigh() {
        assertEquals("high", pick(List.of("[1s ago] TonoCatMeow prays to you: \"can i please have a red cat with 32 cod\""),
                List.of(), List.of(), false).effort());
        assertEquals("high", pick(List.of(), List.of("[1s ago] Steve said in chat: \"god give me diamonds\""),
                List.of(), false).effort());
        assertEquals("high", pick(List.of(), List.of(), List.of(), true).effort(), "operator command");
        assertEquals("high", pick(List.of(), List.of(), List.of("[5s ago] DEATH: Steve was slain by Alex"), false)
                .effort());
        assertEquals("high", pick(List.of("[2s ago] TonoCatMeow prays to you: \"hi\""),
                List.of("[4s ago] Jumpathy said in chat: \"was napping is crazy\""), List.of(), false).effort(),
                "two people talking");
        assertEquals("high", pick(List.of("[2s ago] Korvath prays to you: \"hello\""), List.of(), List.of(), false)
                .effort(), "a despised mortal");
    }

    @Test
    void aFixedLevelWins() {
        assertEquals("low", EffortPicker.choose("low", new EffortPicker.Signals(
                List.of("[1s ago] A prays to you: \"give me gold\""), List.of(), List.of(), true, null, n -> 0)).effort());
        assertEquals("high", EffortPicker.fixed("auto"));
        assertEquals("high", EffortPicker.fixed("hgih"));
        assertEquals("medium", EffortPicker.fixed("Medium"));
    }
}
