package net.bigyous.gptgodmc.loggables;

/** Words or gestures explicitly directed at the god: /pray, touching or striking its vessel. */
public class PrayerLoggable extends BaseLoggable {
    private final String text;

    private PrayerLoggable(String text) {
        this.text = text;
    }

    /** What the god's guard makes of a prayer's words (null for gestures, or when the guard is off). */
    private net.bigyous.gptgodmc.god.Guard.Verdict verdict;

    public static PrayerLoggable prayer(String player, String message) {
        PrayerLoggable p = new PrayerLoggable(String.format("%s prays to you: \"%s\"", player,
                net.bigyous.gptgodmc.god.Guard.clean(message)));
        p.verdict = net.bigyous.gptgodmc.god.Guard.note(player, message, true);
        return p;
    }

    public static PrayerLoggable touchedVessel(String player) {
        return new PrayerLoggable(player + " reached out and touched your vessel");
    }

    public static PrayerLoggable struckVessel(String player, String weapon) {
        return new PrayerLoggable(String.format("%s STRUCK your vessel%s!", player, weapon == null ? "" : " with " + weapon));
    }

    @Override
    public String getLog() {
        return text + net.bigyous.gptgodmc.god.Guard.warning(verdict);
    }

    @Override
    public Category category() {
        return Category.PRAYER;
    }

    @Override
    public String attentionReason() {
        return text;
    }
}
