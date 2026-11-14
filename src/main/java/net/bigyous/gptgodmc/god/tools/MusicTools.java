package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.music.Music;
import net.bigyous.gptgodmc.god.music.Song;

/** The god as composer: songs played with note-block sounds, and discs mortals can keep and play. */
final class MusicTools {
    private MusicTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("compose_song",
                        "Compose a short ORIGINAL song played with note-block sounds: a hymn for a blessing, a dirge "
                                + "for the dead, a mocking jingle, or a tune in the spirit of something a mortal "
                                + "mentioned (search the web first with web_search or search_web if you do not know it, then turn what you learn into notes). Never transcribe an "
                                + "existing copyrighted song note for note; evoke its style instead. Optionally give "
                                + "mortals a music disc of it (they right-click to play it, or use a jukebox) and/or "
                                + "play it now at a target. Note blocks span F#3 to F#5.",
                        Schema.object()
                                .str("title", "The song's name, shown on the disc.", true)
                                .str("melody", "Space-separated notes, one beat each: C4 D4 E4 F#4 Bb3; chords "
                                        + "C4+E4+G4; rests R; lengths /2 (half beat), *2 (two beats), e.g. "
                                        + "'E4 D4 C4*2 R G4/2 G4/2 C5*2'. At most 256 tokens.", true)
                                .str("bass", "Optional second line in the same notation, played under the melody "
                                        + "on the bass.", false)
                                .choice("instrument", "Instrument for the melody. Default harp.", false,
                                        Song.INSTRUMENTS)
                                .integer("tempo", "Beats per minute, 40-240. Default 110.", false, 40, 240)
                                .str("give_to", "Optional: a player name, several separated by commas, or 'all'; each "
                                        + "receives a disc of the song.", false)
                                .str("play_at", "Optional: play it now. Where: " + Targets.TARGET_HELP + ".", false),
                        MusicTools::compose),
                GodTool.sync("play_song",
                        "Play one of your songs again at a target, or stop all your music. Songs you know: "
                                + "the titles you composed (see compose_song).",
                        Schema.object()
                                .str("title", "The song's title, or 'stop' to silence all music.", true)
                                .str("at", "Where: " + Targets.TARGET_HELP + ". Default your vessel.", false)
                                .str("give_to", "Optional: also give these mortals a disc of it.", false),
                        MusicTools::playAgain));
    }

    private static ToolResult compose(Args a) throws ToolException {
        Persona p = Persona.current();
        Song song = new Song(a.str("title"), a.str("melody"), a.str("bass", null), a.str("instrument", "harp"),
                a.integer("tempo", 110, 40, 240), p == null ? null : p.shortName()).cleaned();
        try {
            song.notes();
        } catch (IllegalArgumentException e) {
            throw new ToolException(e.getMessage());
        }
        Music.keep(song);
        StringBuilder sb = new StringBuilder("Composed '" + song.title() + "' (" + song.notes().size() + " notes, "
                + song.length() / 20 + " s, " + song.instrument() + ").");
        deliver(song, a, sb);
        if (a.has("play_at")) {
            Location at = Targets.location(a.str("play_at"));
            Music.play(song, () -> at, "god:" + at.toVector());
            sb.append(" It plays at ").append(Targets.describe(at)).append('.');
        }
        return ToolResult.ok(sb.toString());
    }

    private static ToolResult playAgain(Args a) throws ToolException {
        String title = a.str("title");
        if (title.equalsIgnoreCase("stop")) {
            return ToolResult.ok("Silence: " + Music.stopAll() + " song(s) stopped.");
        }
        Song song = Music.find(title);
        if (song == null) {
            throw new ToolException("You have no song called '" + title + "'. Yours: " + Music.titles()
                    + ". Compose one with compose_song.");
        }
        Location at = Targets.location(a.str("at", "vessel"));
        Music.play(song, () -> at, "god:" + at.toVector());
        StringBuilder sb = new StringBuilder("'" + song.title() + "' plays at " + Targets.describe(at) + ".");
        deliver(song, a, sb);
        return ToolResult.ok(sb.toString());
    }

    private static void deliver(Song song, Args a, StringBuilder sb) throws ToolException {
        if (!a.has("give_to")) return;
        List<String> given = new ArrayList<>();
        for (String name : a.str("give_to").split(",")) {
            if (name.isBlank()) continue;
            for (Player pl : Targets.players(name.trim())) {
                pl.getInventory().addItem(Music.disc(song)).values()
                        .forEach(left -> pl.getWorld().dropItemNaturally(pl.getLocation(), left));
                given.add(pl.getName());
            }
        }
        if (!given.isEmpty()) sb.append(" Discs given to ").append(String.join(", ", given)).append('.');
    }
}
