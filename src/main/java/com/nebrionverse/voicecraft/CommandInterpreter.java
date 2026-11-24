package com.nebrionverse.voicecraft;

import com.nebrionverse.voicecraft.EnchantmentManager.Ench;
import com.nebrionverse.voicecraft.PlayerTargetResolver.*;
import com.nebrionverse.voicecraft.RegistryIndex.Match;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import java.util.*;
import java.util.regex.*;

public final class CommandInterpreter {
    public record Ctx(String raw, String text, List<String> players, String self, double stt) { }
    public record Plan(List<String> commands, double confidence, boolean needsConfirm, boolean broad, String category, String error, String note) {
        static Plan error(String m) { return new Plan(List.of(), 0, false, false, "error", m, null); }
        static Plan of(String cat, double conf, boolean confirm, boolean broad, String... cmds) {
            return new Plan(List.of(cmds), conf, confirm, broad, cat, null, null);
        }
        static Plan of(String cat, double conf, boolean confirm, boolean broad, List<String> cmds, String note) {
            return new Plan(cmds, conf, confirm, broad, cat, null, note);
        }
    }
    interface Rule { Plan parse(Ctx c) throws Exception; }
    record Named(String category, Rule rule) { }

    public static final List<String> CATEGORIES = List.of("raw", "chat", "world", "gamemode", "teleport", "effects", "destructive", "summon", "inventory");

    /** Extend by adding a rule here. First rule that returns non-null wins. */
    private static final List<Named> RULES = List.of(
        new Named("raw", CommandInterpreter::raw), new Named("chat", CommandInterpreter::say),
        new Named("world", CommandInterpreter::weather), new Named("world", CommandInterpreter::time),
        new Named("gamemode", CommandInterpreter::gamemode), new Named("world", CommandInterpreter::difficulty),
        new Named("teleport", CommandInterpreter::tp), new Named("effects", CommandInterpreter::xp),
        new Named("effects", CommandInterpreter::effect), new Named("destructive", CommandInterpreter::clear),
        new Named("destructive", CommandInterpreter::kill), new Named("world", CommandInterpreter::spawnpoint),
        new Named("summon", CommandInterpreter::summon), new Named("inventory", CommandInterpreter::give));

    public static Plan interpret(Ctx c) {
        VoiceCraftConfig cfg = VoiceCraftConfig.get();
        for (Named n : RULES) {
            if (cfg.disabledCategories.contains(n.category())) continue;
            try { Plan p = n.rule().parse(c); if (p != null) return p; }
            catch (Exception e) { VoiceCraftClient.LOG.warn("Rule failed", e); return Plan.error("I couldn't work out that command."); }
        }
        return Plan.error("I didn't understand: \"" + c.raw() + "\"");
    }

    // ---------- helpers ----------
    static boolean has(String t, String w) { return (" " + t + " ").contains(" " + w + " "); }
    static boolean any(String t, String... ws) { for (String w : ws) if (has(t, w)) return true; return false; }
    static String strip(String t, Collection<String> drop) {
        StringBuilder sb = new StringBuilder();
        for (String w : t.split(" ")) if (!w.isEmpty() && !drop.contains(w)) sb.append(w).append(' ');
        return sb.toString().trim();
    }
    static Integer firstInt(String t) { Matcher m = Pattern.compile("\\b\\d+\\b").matcher(t); return m.find() ? Integer.valueOf(m.group()) : null; }
    static String pick(String t, String... options) {
        for (String o : options) for (String w : t.split(" ")) if (Fuzzy.wordSim(w, o) >= 0.86) return o;
        return null;
    }
    static boolean confirmFor(Resolved r) { return r.target().broad() || r.target().other(); }

    // ---------- rules ----------
    private static Plan raw(Ctx c) {
        for (String p : new String[]{"run command ", "command ", "slash "})
            if (c.text().startsWith(p) && c.text().length() > p.length())
                return Plan.of("raw", Math.min(c.stt(), 0.7), true, false, "/" + c.text().substring(p.length()).trim());
        return null;
    }
    private static Plan say(Ctx c) {
        String r = c.raw().trim();
        if (r.toLowerCase().startsWith("say ") && r.length() > 4) return Plan.of("chat", c.stt(), false, false, "/say " + r.substring(4).trim());
        return null;
    }
    private static Plan weather(Ctx c) {
        String t = c.text();
        if (any(t, "give", "gimme") || !any(t, "weather", "rain", "raining", "rainy", "storm", "thunder", "thunderstorm", "sunny", "sun")) return null;
        String v = any(t, "stop", "end", "sunny", "sun", "clear") ? "clear" : any(t, "thunder", "thunderstorm", "storm") ? "thunder" : "rain";
        return Plan.of("world", c.stt(), false, false, "/weather " + v);
    }
    private static Plan time(Ctx c) {
        String t = c.text();
        if (any(t, "give", "gimme")) return null;
        String v = null;
        if (has(t, "midnight")) v = "midnight"; else if (has(t, "noon")) v = "noon";
        else if (any(t, "night", "nighttime")) v = "night"; else if (any(t, "day", "daytime", "morning")) v = "day";
        else if (has(t, "sunrise")) v = "23000"; else if (any(t, "sunset", "evening", "dusk")) v = "13000";
        Integer n = firstInt(t);
        if (v == null && has(t, "time") && n != null) v = String.valueOf(n);
        if (v == null || !(has(t, "time") || any(t, "set", "make", "change", "turn", "skip", "it"))) return null;
        return Plan.of("world", c.stt(), false, false, "/time set " + v);
    }
    private static Plan gamemode(Ctx c) {
        String t = c.text();
        String mode = pick(t, "creative", "survival", "adventure", "spectator");
        if (mode == null || any(t, "give", "gimme", "need") || !(has(t, "gamemode") || any(t, "set", "switch", "change", "make", "put", "go"))) return null;
        Resolved r = PlayerTargetResolver.resolve(t, c.players(), c.self());
        return Plan.of("gamemode", c.stt(), confirmFor(r), r.target().broad(), "/gamemode " + mode + " " + r.target().selector());
    }
    private static Plan difficulty(Ctx c) {
        if (!has(c.text(), "difficulty")) return null;
        String d = pick(c.text(), "peaceful", "easy", "normal", "hard");
        return d == null ? Plan.error("Which difficulty: peaceful, easy, normal or hard?") : Plan.of("world", c.stt(), true, false, "/difficulty " + d);
    }
    private static Plan tp(Ctx c) {
        String t = c.text();
        if (!any(t, "teleport", "tp", "teleporting")) return null;
        String body = strip(t, Set.of("teleport", "tp", "teleporting", "please", "can", "you", "could"));
        String padded = " " + body + " ", left = "", right = body;
        int i = padded.indexOf(" to ");
        if (i >= 0) { left = padded.substring(0, i).trim(); right = padded.substring(i + 4).trim(); }
        Resolved who = PlayerTargetResolver.resolve(left, c.players(), c.self());
        Matcher m = Pattern.compile("(\\d+) (\\d+) (\\d+)").matcher(right);
        if (m.find()) return Plan.of("teleport", c.stt(), confirmFor(who), who.target().broad(),
                "/tp " + who.target().selector() + " " + m.group(1) + " " + m.group(2) + " " + m.group(3));
        Resolved dest = PlayerTargetResolver.resolve(right, c.players(), c.self());
        if (!dest.explicit()) return Plan.error("Player not found: " + right);
        if (dest.target().broad()) return Plan.error("I can't teleport to everyone.");
        return Plan.of("teleport", c.stt(), confirmFor(who), who.target().broad(), "/tp " + who.target().selector() + " " + dest.target().selector());
    }
    private static Plan xp(Ctx c) {
        String t = c.text();
        if (!any(t, "level", "levels", "xp", "experience", "exp") || any(t, "bottle", "bottles")) return null;
        Integer n = firstInt(t);
        if (n == null) return null;
        Resolved r = PlayerTargetResolver.resolve(t, c.players(), c.self());
        String unit = any(t, "points", "point") ? "points" : "levels";
        String cmd = has(t, "set") ? "set " + r.target().selector() + " " + n
                : "add " + r.target().selector() + " " + (any(t, "remove", "take", "subtract") ? -n : n);
        return Plan.of("effects", c.stt(), confirmFor(r), r.target().broad(), "/experience " + cmd + " " + unit);
    }
    private static Plan effect(Ctx c) {
        String t = c.text();
        boolean timed = any(t, "seconds", "second", "secs", "sec", "minutes", "minute");
        boolean word = any(t, "effect", "effects");
        if (!word && !timed) return null;
        Resolved r = PlayerTargetResolver.resolve(t, c.players(), c.self());
        String sel = r.target().selector();
        if (word && any(t, "clear", "remove", "cancel", "stop"))
            return Plan.of("effects", c.stt(), confirmFor(r), r.target().broad(), "/effect clear " + sel);
        if (!VoiceCraftClient.EFFECTS.ready()) return null;
        String body = strip(r.rest(), Set.of("give", "gimme", "apply", "add", "effect", "effects", "of", "level", "for", "a", "an", "the", "please", "with"));
        int secs = 60;
        Matcher m = Pattern.compile("(\\d+) (seconds|second|secs|sec|minutes|minute)").matcher(body);
        if (m.find()) { secs = Integer.parseInt(m.group(1)) * (m.group(2).startsWith("min") ? 60 : 1); body = body.replace(m.group(), "").trim(); }
        int amp = 0;
        Matcher lv = Pattern.compile("\\b(\\d+)\\b").matcher(body);
        if (lv.find()) { amp = Math.max(0, Integer.parseInt(lv.group(1)) - 1); body = body.replace(lv.group(), "").trim(); }
        var u = RegistryIndex.unique(VoiceCraftClient.EFFECTS.search(body, 5), 0.7);
        if (u.isEmpty()) return (!word) ? null : Plan.error("Unknown effect: " + body);
        return Plan.of("effects", Math.min(c.stt(), u.get().score()), confirmFor(r), r.target().broad(),
                "/effect give " + sel + " " + u.get().entry().id() + " " + Math.min(secs, 1_000_000) + " " + amp + " true");
    }
    private static Plan clear(Ctx c) {
        String t = c.text();
        if (!(any(t, "clear", "empty", "wipe", "delete") && any(t, "inventory", "items", "everything"))) return null;
        Resolved r = PlayerTargetResolver.resolve(t, c.players(), c.self());
        return Plan.of("destructive", c.stt(), true, r.target().broad(), "/clear " + r.target().selector());
    }
    private static Plan kill(Ctx c) {
        String t = c.text();
        if (!any(t, "kill", "suicide")) return null;
        Resolved r = PlayerTargetResolver.resolve(t, c.players(), c.self());
        if (!r.explicit() && !has(t, "suicide")) return Plan.error("Who should I kill? Say \"kill me\" or a player name.");
        return Plan.of("destructive", c.stt(), true, r.target().broad(), "/kill " + r.target().selector());
    }
    private static Plan spawnpoint(Ctx c) {
        String t = c.text();
        if (!any(t, "spawnpoint", "worldspawn") || !any(t, "set", "make", "change")) return null;
        if (has(t, "worldspawn")) return Plan.of("world", c.stt(), true, false, "/setworldspawn");
        Resolved r = PlayerTargetResolver.resolve(t, c.players(), c.self());
        return Plan.of("world", c.stt(), confirmFor(r), r.target().broad(), "/spawnpoint " + r.target().selector());
    }
    private static Plan summon(Ctx c) {
        String t = c.text();
        if (!any(t, "summon", "spawn") || has(t, "egg") || any(t, "spawnpoint", "worldspawn") || !VoiceCraftClient.ENTITIES.ready()) return null;
        Integer n = firstInt(t);
        String body = strip(t, Set.of("summon", "spawn", "a", "an", "the", "some", "me", "please", "here", "near", "of")).replaceAll("\\b\\d+\\b", "").trim();
        var m = VoiceCraftClient.ENTITIES.search(body, 5);
        var u = RegistryIndex.unique(m, 0.7);
        if (u.isEmpty()) return Plan.error(m.isEmpty() ? "Unknown mob: " + body : "Did you mean: " + RegistryIndex.names(m, 4) + "?");
        int count = n == null ? 1 : n;
        if (count > 10) return Plan.error("I'll summon at most 10 at a time.");
        List<String> cmds = new ArrayList<>();
        for (int i = 0; i < count; i++) cmds.add("/summon " + u.get().entry().id() + " ~ ~ ~");
        return Plan.of("summon", Math.min(c.stt(), u.get().score()), count > 3, false, cmds, null);
    }

    // ---------- give ----------
    private static final String[] ARMOR = {"helmet", "chestplate", "leggings", "boots"};
    private static final String[] TOOLS = {"sword", "pickaxe", "axe", "shovel", "hoe"};
    private static final Set<String> FILLER = Set.of("give", "gimme", "need", "want", "get", "gave", "a", "an", "the", "some", "of",
            "please", "with", "i", "im", "can", "you", "could", "would", "like", "to", "for", "me", "my");
    private record Seg(String q, LinkedHashMap<Ench, Integer> ench) { }

    private static Plan give(Ctx c) {
        String t = c.text();
        if (!any(t, "give", "gimme", "need", "want", "get", "gave")) return null;
        if (!VoiceCraftClient.ITEMS.ready()) return Plan.error("Item list not loaded yet. Join a world first.");
        Resolved r = PlayerTargetResolver.resolve(t, c.players(), c.self());

        List<Seg> segs = new ArrayList<>();
        for (String part : r.rest().split("\\b(?:and|then|also)\\b|,")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            var ex = VoiceCraftClient.ENCH.extract(part);
            String q = strip(ex.rest(), FILLER);
            if (q.isBlank() || q.matches("[\\d ]+")) { if (!segs.isEmpty()) segs.get(segs.size() - 1).ench().putAll(ex.found()); continue; }
            segs.add(new Seg(q, new LinkedHashMap<>(ex.found())));
        }
        if (segs.isEmpty()) return Plan.error("What item do you want?");

        List<String> cmds = new ArrayList<>(), notes = new ArrayList<>();
        double conf = c.stt();
        String sel = r.target().selector();

        for (Seg s : segs) {
            List<String> w = new ArrayList<>(Arrays.asList(s.q().split(" ")));
            int n = 1; boolean stack = false;
            for (Iterator<String> it = w.iterator(); it.hasNext(); ) {
                String x = it.next();
                if (x.matches("\\d+")) { n = Math.max(1, Integer.parseInt(x)); it.remove(); }
                else if (x.equals("stack") || x.equals("stacks")) { stack = true; it.remove(); }
                else if (Set.of("set", "full", "complete", "whole").contains(x)) it.remove();
            }
            boolean enchantedWord = w.contains("enchanted");
            String q = String.join(" ", w);
            boolean armor = w.contains("armor") && Collections.disjoint(w, Set.of("horse", "wolf", "stand", "trim", "smithing"));
            boolean tools = w.contains("tools") || w.contains("tool");
            List<Item> items = new ArrayList<>();
            boolean setMode = armor || tools;

            if (setMode) {
                String mat = strip(q, Set.of("armor", "tools", "tool", "enchanted"));
                if (mat.isBlank()) return Plan.error("Which material? For example \"iron armor\".");
                for (String piece : armor ? ARMOR : TOOLS) {
                    var u = RegistryIndex.unique(VoiceCraftClient.ITEMS.search(mat + " " + piece, 5), 0.8);
                    if (u.isPresent()) { items.add(u.get().entry().value()); conf = Math.min(conf, u.get().score()); }
                    else notes.add("No " + mat + " " + piece + " found");
                }
                if (items.isEmpty()) return Plan.error("I couldn't find " + mat + (armor ? " armor." : " tools."));
            } else {
                List<Match<Item>> m = VoiceCraftClient.ITEMS.search(q, 6);
                var u = RegistryIndex.unique(m, 0.62);
                if (u.isEmpty() && enchantedWord) {
                    m = VoiceCraftClient.ITEMS.search(strip(q, Set.of("enchanted")), 6);
                    u = RegistryIndex.unique(m, 0.62);
                }
                if (u.isEmpty()) {
                    if (m.isEmpty() || m.get(0).score() < 0.5) return Plan.error("I couldn't find an item called \"" + q + "\".");
                    return Plan.error("Did you mean: " + RegistryIndex.names(m, 4) + "? Say it again more specifically.");
                }
                if (enchantedWord && u.get().entry().name().contains("enchanted")) enchantedWord = false;
                items.add(u.get().entry().value());
                conf = Math.min(conf, u.get().score());
            }

            for (Item item : items) {
                ItemStack proto = new ItemStack(item);
                String itemId = Registries.ITEM.getId(item).toString();
                Map<Ench, Integer> src = (s.ench().isEmpty() && enchantedWord) ? VoiceCraftClient.ENCH.defaultsFor(item) : s.ench();
                LinkedHashMap<Ench, Integer> use = new LinkedHashMap<>();
                for (var e : src.entrySet()) {
                    Ench en = e.getKey();
                    if (item != Items.ENCHANTED_BOOK && !en.entry().value().isAcceptableItem(proto)) {
                        if (setMode) { notes.add(en.name() + " skipped on " + itemId); continue; }
                        return Plan.error(en.name() + " can't be applied to " + itemId + ".");
                    }
                    if (e.getValue() > en.max()) return Plan.error(en.name() + " only goes up to level " + en.max() + ".");
                    use.put(en, e.getValue());
                }
                List<Ench> ks = new ArrayList<>(use.keySet());
                for (int a = 0; a < ks.size(); a++) for (int b = a + 1; b < ks.size(); b++)
                    if (!Enchantment.canBeCombined(ks.get(a).entry(), ks.get(b).entry()))
                        return Plan.error(ks.get(a).name() + " and " + ks.get(b).name() + " can't be combined.");
                int max = proto.getMaxCount();
                int count = setMode ? 1 : (stack ? n * max : n);
                if (count > max * 36) return Plan.error("That's more than fits in an inventory.");
                cmds.add("/give " + sel + " " + itemId + CommandGenerator.enchantComponent(item, use) + " " + count);
            }
        }
        return Plan.of("inventory", conf, confirmFor(r), r.target().broad(), cmds, notes.isEmpty() ? null : String.join("; ", notes));
    }
}
