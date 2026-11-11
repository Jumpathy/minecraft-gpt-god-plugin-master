package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Holy days the gods proclaim: festivals, trials, holy hunts, raids on a mortal they dislike, omens that
 * foretell a great act, and holy wars between two gods where mortals pick a side. The god writes the
 * rules: a title, what is asked, which deeds count for points (any of {@link #ACTS}), the rewards and the
 * consequences; it can also award points itself for anything it judges. Announced ahead with a countdown,
 * scored while running, and the results go back to the god to reward and punish. Server-wide, kept in
 * plugins/gptgodmc/god/holydays.json. Pure (no Bukkit), so it is unit tested.
 */
public final class HolyDays {
    public enum Kind {
        FESTIVAL("festival", "✦"), TRIAL("trial", "⚖"), HUNT("hunt", "➶"), RAID("raid", "⚔"), OMEN("omen", "☄"),
        WAR("war", "⚔"), RITE("rite", "✧");

        public final String id;
        public final String icon;

        Kind(String id, String icon) {
            this.id = id;
            this.icon = icon;
        }

        public static Kind parse(String s) {
            if (s == null) return null;
            String v = s.trim().toLowerCase(Locale.ROOT).replace("holy_", "").replace("holy ", "");
            for (Kind k : values()) {
                if (k.id.equals(v)) return k;
            }
            return switch (v) {
                case "feast", "celebration", "games" -> FESTIVAL;
                case "contest", "challenge", "test", "custom" -> TRIAL;
                case "holy_war", "crusade", "battle" -> WAR;
                case "omens", "prophecy", "portent" -> OMEN;
                case "ceremony" -> RITE;
                default -> null;
            };
        }
    }

    /** Deeds that can count for points, with their weight (points per deed). */
    public static final Map<String, Double> ACTS = new LinkedHashMap<>();
    static {
        ACTS.put("hostile_kills", 1.0);
        ACTS.put("animal_kills", 0.5);
        ACTS.put("player_kills", 3.0);
        ACTS.put("rival_kills", 4.0);
        ACTS.put("deaths", -2.0);
        ACTS.put("prayers", 1.0);
        ACTS.put("offerings", 2.0);
        ACTS.put("blocks_placed", 0.1);
        ACTS.put("blocks_mined", 0.05);
        ACTS.put("crops_harvested", 0.25);
        ACTS.put("fish_caught", 1.0);
        ACTS.put("animals_bred", 1.0);
        ACTS.put("quarry_kill", 15.0);
        ACTS.put("survive", 10.0);
    }

    /** Beasts a holy hunt may set loose. */
    public static final List<String> QUARRIES = List.of("ravager", "hoglin", "polar_bear", "goat", "skeleton_horse",
            "wolf", "spider", "camel", "panda", "strider");

    public static final class Day {
        public int id;
        public String kind;
        /** Realm id and name of the god that proclaimed it. */
        public String god;
        public String godName;
        /** Holy war: the other god. */
        public String rival;
        public String rivalName;
        public String title;
        public String rules;
        public List<String> counts = new ArrayList<>();
        public String reward;
        public String consequence;
        /** Raid: the mortal raided. Hunt: who it is sent near (optional). */
        public String target;
        public String quarry;
        /** Holy war: whether killing the other side counts. */
        public boolean pvp;
        public long proclaimedAt;
        public long startsAt;
        public long endsAt;
        /** announced | running | ended | cancelled */
        public String status = "announced";
        public Map<String, Double> scores = new LinkedHashMap<>();
        /** Holy war: mortal → realm id of the side they chose. */
        public Map<String, String> sides = new LinkedHashMap<>();
        /** Holy war: points given to a side as a whole (by realm id). */
        public Map<String, Double> sidePoints = new LinkedHashMap<>();
        public String outcome;
        public int wave;
        public long nextBeat;

        public Kind kind() {
            Kind k = Kind.parse(kind);
            return k == null ? Kind.TRIAL : k;
        }

        public boolean live() {
            return "announced".equals(status) || "running".equals(status);
        }

        public boolean running() {
            return "running".equals(status);
        }

        public boolean involves(String realmId) {
            return realmId != null && (realmId.equalsIgnoreCase(god) || realmId.equalsIgnoreCase(rival));
        }

        public boolean counts(String act) {
            return counts != null && counts.contains(act);
        }
    }

    public int version = 1;
    public int counter;
    public List<Day> days = new ArrayList<>();
    /** Realm id → when it last proclaimed a holy day. */
    public Map<String, Long> lastProclaimed = new LinkedHashMap<>();
    /** Realm id → when it was last reminded that a holy day is due. */
    public Map<String, Long> lastNudged = new LinkedHashMap<>();

    static final int ENDED_KEPT = 12;

    // ---- Proclaiming ----------------------------------------------------------------------------------

    /** The plan for a holy day, as the god asked for it (validated by {@link #refusal}). */
    public record Request(Kind kind, String god, String godName, String title, String rules, List<String> counts,
            String reward, String consequence, String target, String quarry, String rival, String rivalName,
            boolean pvp, int leadMinutes, int minutes) {
    }

    /** Why this holy day may not be proclaimed now, or null if it may. */
    public synchronized String refusal(Request r, int maxPerGod, int maxTotal, long now) {
        if (r.kind() == null) return "Name the kind of holy day: festival, trial, hunt, raid, omen, war or rite.";
        if (r.title() == null || r.title().isBlank()) return "Give the holy day a title.";
        int mine = 0, all = 0;
        for (Day d : days) {
            if (!d.live()) continue;
            all++;
            if (d.involves(r.god())) mine++;
            if (r.kind() == Kind.RAID && d.kind() == Kind.RAID && r.target() != null
                    && r.target().equalsIgnoreCase(d.target)) {
                return r.target() + " is already under a raid.";
            }
            if (r.kind() == Kind.WAR && d.kind() == Kind.WAR && d.involves(r.god()) && d.involves(r.rival())) {
                return "You are already at holy war with " + r.rivalName() + ".";
            }
        }
        if (mine >= maxPerGod) {
            return "You already have " + mine + " holy day" + (mine == 1 ? "" : "s") + " proclaimed; let one end (or "
                    + "cancel it) first.";
        }
        if (all >= maxTotal) return "The calendar is full: " + all + " holy days are already proclaimed in this world.";
        if (r.kind() == Kind.RAID && (r.target() == null || r.target().isBlank())) return "Name the mortal to raid.";
        if (r.kind() == Kind.WAR && (r.rival() == null || r.rival().isBlank())) {
            return "Name the god you wage holy war on.";
        }
        if (r.kind() == Kind.WAR && r.rival().equalsIgnoreCase(r.god())) return "You cannot war on yourself.";
        if (r.counts() != null) {
            for (String c : r.counts()) {
                if (!ACTS.containsKey(c)) return "'" + c + "' is not a deed that can be counted. Choose from: "
                        + String.join(", ", ACTS.keySet()) + ".";
            }
        }
        return null;
    }

    public synchronized Day proclaim(Request r, long now) {
        Day d = new Day();
        d.id = ++counter;
        d.kind = r.kind().id;
        d.god = r.god();
        d.godName = r.godName();
        d.rival = r.rival();
        d.rivalName = r.rivalName();
        d.title = clip(r.title(), 48);
        d.rules = clip(r.rules(), 240);
        d.counts = r.counts() == null ? new ArrayList<>() : new ArrayList<>(r.counts());
        if (d.kind() == Kind.RAID && !d.counts.contains("survive")) d.counts.add("survive");
        if (d.kind() == Kind.HUNT && !d.counts.contains("quarry_kill")) d.counts.add("quarry_kill");
        if (d.kind() == Kind.WAR && r.pvp() && !d.counts.contains("rival_kills")) d.counts.add("rival_kills");
        d.reward = clip(r.reward(), 160);
        d.consequence = clip(r.consequence(), 160);
        d.target = r.target();
        d.quarry = r.quarry();
        d.pvp = r.pvp();
        d.proclaimedAt = now;
        d.startsAt = now + Math.max(0, r.leadMinutes()) * 60_000L;
        d.endsAt = d.startsAt + Math.max(1, r.minutes()) * 60_000L;
        d.status = d.startsAt <= now ? "running" : "announced";
        days.add(d);
        lastProclaimed.put(r.god().toLowerCase(Locale.ROOT), now);
        if (r.rival() != null) lastProclaimed.put(r.rival().toLowerCase(Locale.ROOT), now);
        return d;
    }

    // ---- Running ----------------------------------------------------------------------------------------

    /** What changed for a day at this moment. */
    public enum Step {
        NOTHING, STARTED, ENDED
    }

    /** Moves a day along its timeline. */
    public synchronized Step step(Day d, long now) {
        if ("announced".equals(d.status) && now >= d.startsAt) {
            d.status = "running";
            return Step.STARTED;
        }
        if ("running".equals(d.status) && now >= d.endsAt) {
            end(d, null, now);
            return Step.ENDED;
        }
        return Step.NOTHING;
    }

    /** Ends a day now and writes its outcome. A war's yielding side (realm id) loses. */
    public synchronized void end(Day d, String yielded, long now) {
        if (!d.live()) return;
        d.status = "ended";
        d.endsAt = Math.min(d.endsAt, now);
        d.outcome = outcome(d, yielded);
        trim();
    }

    public synchronized void cancel(Day d, long now) {
        if (!d.live()) return;
        d.status = "cancelled";
        d.endsAt = now;
        d.outcome = "cancelled";
        trim();
    }

    private void trim() {
        int ended = 0;
        for (int i = days.size() - 1; i >= 0; i--) {
            if (days.get(i).live()) continue;
            if (++ended > ENDED_KEPT) days.remove(i);
        }
    }

    /** Credits a deed to a mortal in every running day that counts it. Returns the days credited. */
    public synchronized List<Day> credit(String player, String act, double times, java.util.Collection<String> realms) {
        List<Day> hit = new ArrayList<>();
        Double w = ACTS.get(act);
        if (w == null || player == null) return hit;
        for (Day d : days) {
            if (!d.running() || !d.counts(act)) continue;
            if (realms != null && realms.stream().noneMatch(d::involves)) continue;
            if (d.kind() == Kind.WAR && !d.sides.containsKey(player)) continue;
            if (d.kind() == Kind.RAID && act.equals("survive") && !player.equalsIgnoreCase(d.target)) continue;
            add(d, player, w * times);
            hit.add(d);
        }
        return hit;
    }

    /** The god's own award (may be negative). "side:<realm id>" gives points to a war side. */
    public synchronized double award(Day d, String who, double points) {
        double p = Math.max(-50, Math.min(50, points));
        if (who.startsWith("side:")) {
            String side = who.substring(5).toLowerCase(Locale.ROOT);
            d.sidePoints.merge(side, p, Double::sum);
            return d.sidePoints.get(side);
        }
        return add(d, who, p);
    }

    private static double add(Day d, String player, double p) {
        double v = round(d.scores.getOrDefault(player, 0.0) + p);
        d.scores.put(player, v);
        return v;
    }

    /** Joins a mortal to a holy war's side (or moves them; their points stay their own). */
    public synchronized String join(Day d, String player, String realmId) {
        if (d.kind() != Kind.WAR || !d.live()) return "There is no holy war to join.";
        if (!d.involves(realmId)) return "That god is not part of this holy war.";
        String had = d.sides.put(player, realmId.toLowerCase(Locale.ROOT));
        if (had != null && !had.equals(realmId.toLowerCase(Locale.ROOT))) {
            // A turncoat's points do not follow them.
            d.scores.remove(player);
        }
        return null;
    }

    /** A war side's total: its mortals' points plus what was given to the side. */
    public synchronized double sideTotal(Day d, String realmId) {
        String id = realmId == null ? "" : realmId.toLowerCase(Locale.ROOT);
        double sum = d.sidePoints.getOrDefault(id, 0.0);
        for (Map.Entry<String, String> e : d.sides.entrySet()) {
            if (e.getValue().equals(id)) sum += d.scores.getOrDefault(e.getKey(), 0.0);
        }
        return round(sum);
    }

    /** Mortals by points, best first. */
    public synchronized List<Map.Entry<String, Double>> ranking(Day d) {
        List<Map.Entry<String, Double>> out = new ArrayList<>(d.scores.entrySet());
        out.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return out;
    }

    /** The realm id of a war's winner (null for a draw), given who yielded. */
    public synchronized String warWinner(Day d, String yielded) {
        if (d.kind() != Kind.WAR) return null;
        if (yielded != null) return yielded.equalsIgnoreCase(d.god) ? lower(d.rival) : lower(d.god);
        double a = sideTotal(d, d.god), b = sideTotal(d, d.rival);
        if (Math.abs(a - b) < 0.01) return null;
        return a > b ? lower(d.god) : lower(d.rival);
    }

    String outcome(Day d, String yielded) {
        if (d.kind() == Kind.WAR) {
            String w = warWinner(d, yielded);
            String score = d.godName + " " + fmt(sideTotal(d, d.god)) + " – " + fmt(sideTotal(d, d.rival)) + " "
                    + d.rivalName;
            if (yielded != null) return (yielded.equalsIgnoreCase(d.god) ? d.godName : d.rivalName) + " yielded ("
                    + score + ")";
            if (w == null) return "a draw (" + score + ")";
            return (w.equalsIgnoreCase(d.god) ? d.godName : d.rivalName) + " won (" + score + ")";
        }
        List<Map.Entry<String, Double>> r = ranking(d);
        if (r.isEmpty()) return "no mortal earned a point";
        List<String> top = new ArrayList<>();
        for (int i = 0; i < Math.min(3, r.size()); i++) top.add(r.get(i).getKey() + " " + fmt(r.get(i).getValue()));
        return "top: " + String.join(", ", top);
    }

    // ---- Reading -----------------------------------------------------------------------------------------

    public synchronized Day find(int id) {
        for (Day d : days) {
            if (d.id == id) return d;
        }
        return null;
    }

    public synchronized List<Day> live() {
        List<Day> out = new ArrayList<>();
        for (Day d : days) {
            if (d.live()) out.add(d);
        }
        return out;
    }

    public synchronized List<Day> liveFor(String realmId) {
        List<Day> out = new ArrayList<>();
        for (Day d : days) {
            if (d.live() && d.involves(realmId)) out.add(d);
        }
        return out;
    }

    /** The war two gods are fighting, or null. */
    public synchronized Day warBetween(String a, String b) {
        for (Day d : days) {
            if (d.live() && d.kind() == Kind.WAR && d.involves(a) && d.involves(b)) return d;
        }
        return null;
    }

    /** True when a holy day is due by the server's cadence (none proclaimed for that long). */
    public synchronized boolean due(String realmId, double cadenceHours, long now) {
        if (cadenceHours <= 0) return false;
        String id = realmId.toLowerCase(Locale.ROOT);
        long every = (long) (cadenceHours * 3_600_000L);
        long last = Math.max(lastProclaimed.getOrDefault(id, 0L), lastNudged.getOrDefault(id, 0L));
        if (last == 0) {
            // A new god gets a quiet first stretch before its first reminder.
            lastNudged.put(id, now);
            return false;
        }
        return now - last >= every && liveFor(realmId).isEmpty();
    }

    public synchronized void nudged(String realmId, long now) {
        lastNudged.put(realmId.toLowerCase(Locale.ROOT), now);
    }

    /** "begins in 2:05" / "4:10 left" / "3 h 20 m left". */
    public static String countdown(Day d, long now) {
        if ("announced".equals(d.status)) return "begins in " + clock(d.startsAt - now);
        if (d.running()) return clock(d.endsAt - now) + " left";
        return d.status;
    }

    public static String clock(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        if (s >= 3600) return (s / 3600) + " h " + ((s % 3600) / 60) + " m";
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** How far through its countdown (announced) or its run (running) a day is, 0..1, for the boss bar. */
    public static float progress(Day d, long now) {
        if ("announced".equals(d.status)) {
            long span = Math.max(1, d.startsAt - d.proclaimedAt);
            return clamp01(1f - (d.startsAt - now) / (float) span);
        }
        long span = Math.max(1, d.endsAt - d.startsAt);
        return clamp01((d.endsAt - now) / (float) span);
    }

    /** The boss bar text everyone sees. */
    public synchronized String bar(Day d, long now) {
        StringBuilder sb = new StringBuilder(d.kind().icon).append(' ').append(d.title);
        if (d.kind() == Kind.WAR) {
            sb.append(" — ").append(d.godName).append(' ').append(fmt(sideTotal(d, d.god))).append(" : ")
                    .append(fmt(sideTotal(d, d.rival))).append(' ').append(d.rivalName);
        } else if (d.kind() == Kind.RAID) {
            sb.append(" upon ").append(d.target);
        }
        sb.append(" — ").append(countdown(d, now));
        if (d.running() && d.kind() != Kind.WAR && d.kind() != Kind.OMEN) {
            List<Map.Entry<String, Double>> r = ranking(d);
            if (!r.isEmpty()) sb.append(" — top: ").append(r.get(0).getKey()).append(' ').append(fmt(r.get(0).getValue()));
        }
        return sb.toString();
    }

    /** Lines for one god's perception (short). */
    public synchronized String perceive(String realmId, long now) {
        StringBuilder sb = new StringBuilder();
        for (Day d : days) {
            if (!d.involves(realmId)) continue;
            boolean recent = !d.live() && now - d.endsAt < 30 * 60_000L;
            if (!d.live() && !recent) continue;
            sb.append("- #").append(d.id).append(' ').append(d.kind().id).append(" \"").append(d.title).append("\"");
            if (!realmId.equalsIgnoreCase(d.god)) sb.append(" (proclaimed by ").append(d.godName).append(")");
            if (d.live()) {
                sb.append(", ").append(countdown(d, now));
                if (d.target != null && !d.target.isBlank()) sb.append(", on ").append(d.target);
                if (d.kind() == Kind.WAR) {
                    sb.append(", ").append(d.godName).append(' ').append(fmt(sideTotal(d, d.god))).append(" vs ")
                            .append(d.rivalName).append(' ').append(fmt(sideTotal(d, d.rival)));
                    List<String> mine = new ArrayList<>();
                    for (Map.Entry<String, String> e : d.sides.entrySet()) {
                        if (e.getValue().equalsIgnoreCase(realmId)) mine.add(e.getKey());
                    }
                    sb.append(", your side: ").append(mine.isEmpty() ? "nobody yet (/side)" : String.join(", ", mine));
                } else {
                    List<Map.Entry<String, Double>> r = ranking(d);
                    if (!r.isEmpty()) {
                        List<String> top = new ArrayList<>();
                        for (int i = 0; i < Math.min(3, r.size()); i++) top.add(r.get(i).getKey() + " " + fmt(r.get(i).getValue()));
                        sb.append(", scores: ").append(String.join(", ", top));
                    }
                }
                if (!d.counts.isEmpty()) sb.append(", counts: ").append(String.join("/", d.counts));
            } else {
                sb.append(", ").append(d.status).append(": ").append(d.outcome);
                if (d.reward != null && !d.reward.isBlank()) sb.append(" — you promised: ").append(d.reward);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---- Helpers ------------------------------------------------------------------------------------------

    public static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    static String clip(String s, int max) {
        String t = s == null ? "" : s.replace('\n', ' ').trim();
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }
}
