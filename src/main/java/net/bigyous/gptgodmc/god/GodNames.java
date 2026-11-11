package net.bigyous.gptgodmc.god;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a god may call itself: an invented, otherworldly name (Azaeleus, Vhal'myr, Oruun), never an
 * ordinary human name, a famous deity, a well-known character, or a plain word like "God". Used for
 * the names the memory model invents, the fallback tables, and the god's own rename_self.
 */
public final class GodNames {
    private GodNames() {
    }

    private static Set<String> set(String... words) {
        return Set.copyOf(java.util.Arrays.asList(words));
    }

    /** Letters with at most two inner apostrophes or hyphens; it must start and end with a letter. */
    private static final java.util.regex.Pattern SHAPE = java.util.regex.Pattern
            .compile("^[A-Za-z](?:[A-Za-z]|['-](?=[A-Za-z]))*$");

    /** Common human first names (several languages) and the default Minecraft skins. */
    private static final Set<String> HUMAN = set(
            "aaron", "adam", "adrian", "aiden", "alan", "albert", "alex", "alexander", "alexandra", "alfred", "alice",
            "alicia", "alison", "amanda", "amber", "amy", "andrea", "andrew", "andy", "angela", "anna", "anne", "annie",
            "anthony", "antonio", "arthur", "ashley", "audrey", "austin", "barbara", "ben", "benjamin", "beth", "betty",
            "bill", "billy", "bob", "bobby", "brad", "brandon", "brenda", "brian", "bruce", "bryan", "carl", "carlos",
            "carol", "caroline", "catherine", "charles", "charlie", "charlotte", "chris", "christian", "christine",
            "christopher", "claire", "colin", "connor", "craig", "daniel", "danny", "dave", "david", "dennis", "derek",
            "diana", "diane", "dominic", "donald", "donna", "doris", "dorothy", "doug", "douglas", "dylan", "eddie",
            "edward", "elena", "eli", "elijah", "elizabeth", "ella", "ellie", "emily", "emma", "eric", "erik", "ethan",
            "eva", "evan", "felix", "frank", "fred", "gary", "george", "gerald", "grace", "greg", "gregory", "hannah",
            "harold", "harry", "heather", "helen", "henry", "ian", "isaac", "isabel", "isabella", "jack", "jacob",
            "jake", "james", "jamie", "jane", "janet", "jason", "jeff", "jeffrey", "jennifer", "jenny", "jeremy",
            "jerry", "jessica", "jim", "jimmy", "joan", "joe", "joel", "john", "johnny", "jon", "jonathan", "jordan",
            "jose", "joseph", "josh", "joshua", "joyce", "juan", "judith", "judy", "julia", "julie", "justin", "karen",
            "kate", "katie", "keith", "kelly", "ken", "kenneth", "kevin", "kim", "kyle", "larry", "laura", "lauren",
            "leo", "leon", "liam", "lily", "linda", "lisa", "logan", "louis", "lucas", "lucy", "luke", "lynn",
            "madison", "marc", "margaret", "maria", "marie", "mark", "martin", "mary", "mason", "matt", "matthew",
            "max", "megan", "melissa", "mia", "michael", "michelle", "mike", "mohammed", "muhammad", "nancy", "nathan",
            "neil", "nicholas", "nick", "nicole", "noah", "oliver", "olivia", "oscar", "owen", "pam", "pamela",
            "patricia", "patrick", "paul", "peter", "phil", "philip", "rachel", "ralph", "randy", "raymond", "rebecca",
            "richard", "rick", "rob", "robert", "roger", "ronald", "rose", "ruby", "russell", "ruth", "ryan", "sam",
            "samantha", "samuel", "sandra", "sara", "sarah", "scott", "sean", "sharon", "shirley", "simon", "sophia",
            "sophie", "stephanie", "stephen", "steve", "steven", "susan", "taylor", "teresa", "terry", "thomas", "tim",
            "timothy", "tina", "todd", "tom", "tommy", "tony", "tyler", "victor", "victoria", "vincent", "walter",
            "wayne", "william", "willie", "zach", "zachary", "zoe", "hans", "pierre", "jean", "luca", "marco",
            "giovanni", "pedro", "pablo", "diego", "miguel", "carmen", "lucia", "sofia", "ivan", "olga", "dmitri",
            "sergei", "yuki", "kenji", "hiro", "wei", "li", "chen", "raj", "priya", "arjun", "fatima", "ali", "omar",
            "ahmed", "kai", "zuri", "efe", "makena", "ari", "sunny", "noor", "ari", "kari", "sari", "ari");

    /** Famous gods, holy figures and well-known characters (no borrowing). */
    private static final Set<String> FAMOUS = set(
            "zeus", "hera", "poseidon", "hades", "athena", "apollo", "artemis", "ares", "aphrodite", "hermes",
            "hephaestus", "dionysus", "demeter", "kronos", "cronus", "gaia", "uranus", "jupiter", "juno", "mars",
            "venus", "mercury", "neptune", "pluto", "saturn", "odin", "thor", "loki", "freya", "freyja", "frigg",
            "baldur", "tyr", "heimdall", "ra", "re", "osiris", "isis", "horus", "anubis", "set", "seth", "thoth",
            "amun", "bastet", "sekhmet", "brahma", "vishnu", "shiva", "krishna", "rama", "kali", "ganesha",
            "indra", "buddha", "allah", "yahweh", "jehovah", "jesus", "christ", "elohim", "satan", "lucifer",
            "beelzebub", "baal", "moloch", "mammon", "asmodeus", "gabriel", "raphael", "uriel", "metatron",
            "azrael", "marduk", "ishtar", "inanna", "tiamat", "quetzalcoatl", "tlaloc", "huitzilopochtli",
            "amaterasu", "susanoo", "izanagi", "izanami", "cthulhu", "nyarlathotep", "azathoth", "yog-sothoth",
            "sauron", "morgoth", "melkor", "gandalf", "voldemort", "dumbledore", "thanos", "galactus", "darkseid",
            "aslan", "kratos", "ganon", "ganondorf", "zelda", "link", "mario", "bowser", "sephiroth", "herobrine",
            "notch", "jeb", "dinnerbone", "vecna", "tiamat", "bahamut", "lolth", "arceus", "palutena", "zenos");

    /** Plain words that are not names at all. */
    private static final Set<String> WORDS = set(
            "god", "gods", "goddess", "deity", "lord", "lady", "king", "queen", "master", "creator", "maker",
            "divine", "holy", "almighty", "admin", "server", "operator", "player", "claude", "anthropic", "gpt",
            "chatgpt", "ai", "bot", "sky", "sun", "moon", "star", "devil", "demon", "angel", "spirit", "father",
            "mother", "unknown", "nobody", "none", "null", "test");

    /** Fallback names when the memory model is unavailable: {name, epithet}, eight or more per archetype. */
    static final Map<String, String[][]> FALLBACK = Map.of(
            "benevolent", new String[][] { { "Sethael", "the Gentle Dawn" }, { "Azaeleus", "Warden of the Hearth" },
                    { "Elurien", "Who Mends What Breaks" }, { "Ilyssa", "the Patient Light" },
                    { "Oruun", "Keeper of Green Things" }, { "Thaliel", "the Open Hand" },
                    { "Myrrhael", "Shepherd of the Lost" }, { "Vaelith", "the Morning Bell" },
                    { "Caelune", "the Quiet Shelter" } },
            "chill", new String[][] { { "Lumo", "Who Naps Among Stars" }, { "Dezzarel", "the Unbothered" },
                    { "Oolan", "Drifter of Warm Tides" }, { "Saevi", "the Long Afternoon" },
                    { "Brisul", "Who Lets It Ride" }, { "Nuvaro", "the Lazy Comet" },
                    { "Pellioth", "Keeper of Good Vibes" }, { "Yaelo", "the Slow River" },
                    { "Toruvin", "Who Shrugs at Storms" } },
            "trickster", new String[][] { { "Pip'ra", "Who Rearranges Things" }, { "Quillex", "the Laughing Shadow" },
                    { "Zibbareth", "Lord of Misplaced Spoons" }, { "Vexalune", "the Grinning Riddle" },
                    { "Nix'tar", "Who Swaps the Signs" }, { "Kelaquor", "the Crooked Coin" },
                    { "Jestrael", "Teller of Upside Tales" }, { "Wyx", "the Hidden Card" },
                    { "Murrolin", "the Wink in the Dark" } },
            "evil", new String[][] { { "Malgrath", "the Hollow King" }, { "Vexis", "Who Feeds on Promises" },
                    { "Nythraxis", "the Velvet Rot" }, { "Morvayne", "Keeper of Broken Oaths" },
                    { "Szael'vor", "the Smiling Ash" }, { "Ulgareth", "Who Waits Below" },
                    { "Drevoth", "the Cold Bargain" }, { "Xarneth", "Lord of the Last Coin" },
                    { "Kaelmorr", "the Patient Hunger" } },
            "wrathful", new String[][] { { "Ozrael", "the Iron Judgment" }, { "Vhalmyr", "the Hammer of Law" },
                    { "Korvaun", "Who Weighs Every Deed" }, { "Thurasch", "the Thundering Gate" },
                    { "Aedrakon", "the Unbending" }, { "Brannoth", "Keeper of the Tablets" },
                    { "Zerathul", "the Burning Verdict" }, { "Ghoravel", "the Stern Mountain" },
                    { "Uthrane", "Who Does Not Forgive" } },
            "cosmic", new String[][] { { "Ylthe", "the Geometry Between" }, { "Oruun", "the Folded Hour" },
                    { "Vhe'zaal", "Who Counts the Stars" }, { "Ixomere", "the Silent Lattice" },
                    { "Quaelith", "the Seventh Angle" }, { "Thessarune", "the Patient Void" },
                    { "Zuuloth", "Who Hums in Prime Numbers" }, { "Aelyx", "the Outer Spiral" },
                    { "Nomereth", "the Unlit Pattern" } });

    /** For archetypes without a table of their own (e.g. ones the operator wrote). */
    static final String[][] DEFAULT_FALLBACK = { { "Vael'thorn", "the Violet Watcher" },
            { "Sythe", "Keeper of the Amethyst Hour" }, { "Azaeleus", "the Ever-Watching" },
            { "Iltheris", "Who Walks the Clouds" }, { "Oruun", "the Deep Bell" }, { "Sethael", "the Pale Flame" },
            { "Myrvane", "the Hidden Star" }, { "Kaelithor", "the Silver Wake" } };

    /** {name, epithet} options for an archetype. */
    static String[][] fallbacks(String archetype) {
        String key = archetype == null ? "" : archetype.toLowerCase(Locale.ROOT);
        return FALLBACK.getOrDefault(key, DEFAULT_FALLBACK);
    }

    /**
     * Why this name will not do, or null when it is a fine god's name.
     *
     * @param taken names already in use (online players, mortals the god knows); compared case-insensitively
     */
    public static String problem(String name, Collection<String> taken) {
        if (name == null || name.isBlank()) return "A name needs letters.";
        String n = name.trim();
        if (n.contains(" ")) return "One word only: no spaces in the name (an epithet can carry more words).";
        if (!SHAPE.matcher(n).matches()) {
            return "Use letters only, with at most an apostrophe or hyphen between letters (like Vhal'myr); "
                    + "no digits or symbols.";
        }
        int letters = n.replaceAll("[^A-Za-z]", "").length();
        if (letters < 2 || letters > 24) return "A name needs 2 to 24 letters.";
        if (n.length() - letters > 2) return "At most two apostrophes or hyphens.";
        String key = n.toLowerCase(Locale.ROOT);
        String bare = key.replaceAll("['-]", "");
        if (WORDS.contains(key) || WORDS.contains(bare) || bare.startsWith("god") && bare.length() <= 5) {
            return "\"" + n + "\" is a word, not a name. Coin an otherworldly name of your own.";
        }
        if (HUMAN.contains(key) || HUMAN.contains(bare)) {
            return "\"" + n + "\" is an ordinary mortal name. A god's name should sound invented and otherworldly.";
        }
        if (FAMOUS.contains(key) || FAMOUS.contains(bare)) {
            return "\"" + n + "\" belongs to another god or a famous character. Coin a name of your own.";
        }
        if (taken != null) {
            for (String t : taken) {
                if (t != null && t.equalsIgnoreCase(n)) return "A mortal here already bears the name " + t + ".";
            }
        }
        return null;
    }

    /** For a name the server's operator chose: one word of letters, 2 to 24 long. Null when fine. */
    public static String operatorProblem(String name) {
        if (name == null || name.isBlank()) return "A name needs letters.";
        String n = name.trim();
        if (n.contains(" ")) return "One word only: no spaces in the name.";
        if (!SHAPE.matcher(n).matches()) {
            return "Letters only, with at most an apostrophe or hyphen between letters (like Vhal'myr).";
        }
        int letters = n.replaceAll("[^A-Za-z]", "").length();
        if (letters < 2 || letters > 24) return "A name needs 2 to 24 letters.";
        return null;
    }

    public static boolean valid(String name, Collection<String> taken) {
        return problem(name, taken) == null;
    }

    /** Examples quoted to the memory model and to the god. */
    public static final List<String> EXAMPLES = List.of("Azaeleus", "Vhalmyr", "Oruun", "Sethael", "Ilyssa");
}
