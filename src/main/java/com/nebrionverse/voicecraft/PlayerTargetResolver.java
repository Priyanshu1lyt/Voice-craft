package com.nebrionverse.voicecraft;

import java.util.*;

public final class PlayerTargetResolver {
    public record Target(String selector, boolean broad, boolean other) { }
    public record Resolved(Target target, String rest, boolean explicit) { }
    private static final String[] BROAD = {"all players", "all the players", "every player", "everyone", "everybody", "all of us"};
    private static final Set<String> SELF = Set.of("me", "my", "myself", "mine");

    public static Resolved resolve(String text, List<String> online, String self) {
        String s = " " + text + " ";
        for (String b : BROAD)
            if (s.contains(" " + b + " ")) return new Resolved(new Target("@a", true, false), s.replace(" " + b + " ", " ").trim(), true);
        if (s.contains(" nearest player ")) return new Resolved(new Target("@p", false, true), s.replace(" nearest player ", " ").trim(), true);
        if (s.contains(" random player ")) return new Resolved(new Target("@r", false, true), s.replace(" random player ", " ").trim(), true);
        String[] tok = text.isBlank() ? new String[0] : text.split(" ");
        for (String name : online) {
            String ln = name.toLowerCase(Locale.ROOT);
            for (String w : tok)
                if (w.equals(ln) || (ln.length() >= 5 && Fuzzy.ratio(w, ln) >= 0.85))
                    return new Resolved(new Target(name, false, !name.equalsIgnoreCase(self)), s.replace(" " + w + " ", " ").trim(), true);
        }
        boolean me = false; List<String> keep = new ArrayList<>();
        for (String w : tok) { if (SELF.contains(w)) me = true; else keep.add(w); }
        return new Resolved(new Target("@s", false, false), String.join(" ", keep), me);
    }
}
