package com.nebrionverse.voicecraft;

import java.util.*;

public final class LanguageProcessor {
    // Basic Hindi / Hinglish tokens. Extend freely.
    private static final Map<String, String> TOKENS = Map.ofEntries(
        Map.entry("मुझे", "me"), Map.entry("चाहिए", "need"), Map.entry("दे", "give"), Map.entry("दीजिए", "give"),
        Map.entry("क्रिएटिव", "creative"), Map.entry("सर्वाइवल", "survival"), Map.entry("डायमंड", "diamond"),
        Map.entry("तलवार", "sword"), Map.entry("सेब", "apple"), Map.entry("दिन", "day"), Map.entry("रात", "night"),
        Map.entry("बारिश", "rain"), Map.entry("एक", "1"), Map.entry("तीन", "3"), Map.entry("चार", "4"),
        Map.entry("पांच", "5"), Map.entry("दस", "10"), Map.entry("बीस", "20"), Map.entry("तीस", "30"),
        Map.entry("पचास", "50"), Map.entry("सौ", "100"),
        Map.entry("mujhe", "me"), Map.entry("chahiye", "need"), Map.entry("dedo", "give"), Map.entry("dijiye", "give"));

    private static final String[][] PHRASES = {
        {"game mode", "gamemode"}, {"armour", "armor"}, {"pick axe", "pickaxe"}, {"gapples", "golden apples"},
        {"gapple", "golden apple"}, {"god apple", "enchanted golden apple"}, {"xp bottle", "experience bottle"},
        {"nether right", "netherite"}, {"nether rite", "netherite"}, {"nether ite", "netherite"},
        {"sharp ness", "sharpness"}, {"un breaking", "unbreaking"}, {"projection", "protection"},
        {"spawn point", "spawnpoint"}, {"world spawn", "worldspawn"}, {"tool set", "tools"},
        {"a hundred", "one hundred"}, {"a thousand", "one thousand"}};

    private static final Map<String, Integer> UNIT = new HashMap<>(), TEEN = new HashMap<>(), TENS = new HashMap<>();
    static {
        String[] u = {"zero","one","two","three","four","five","six","seven","eight","nine"};
        String[] te = {"ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"};
        String[] tn = {"","","twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety"};
        for (int i = 0; i < 10; i++) { UNIT.put(u[i], i); TEEN.put(te[i], 10 + i); if (i >= 2) TENS.put(tn[i], i * 10); }
    }

    public static String normalize(String in) {
        String s = in.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("'s\\b", "").replace("'", "");
        s = s.replaceAll("[^\\p{L}\\p{M}\\p{N}_ ]", " ").replaceAll("\\s+", " ").trim();
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) sb.append(TOKENS.getOrDefault(w, w)).append(' ');
        s = " " + sb.toString().trim() + " ";
        for (String[] p : PHRASES) s = s.replace(" " + p[0] + " ", " " + p[1] + " ");
        return numbers(s.trim());
    }

    private static int kind(String t) {
        if (UNIT.containsKey(t)) return 1; if (TEEN.containsKey(t)) return 2; if (TENS.containsKey(t)) return 3;
        if (t.equals("hundred")) return 4; if (t.equals("thousand")) return 5; return 0;
    }
    private static int val(String t) {
        if (UNIT.containsKey(t)) return UNIT.get(t); if (TEEN.containsKey(t)) return TEEN.get(t); return TENS.getOrDefault(t, 0);
    }
    private static boolean continues(int last, int k) {
        return switch (last) {
            case 1, 2 -> k == 4 || k == 5;
            case 3 -> k == 1 || k == 5;
            case 4 -> k >= 1 && k != 4;
            case 5 -> k >= 1 && k != 5;
            default -> false;
        };
    }
    private static String numbers(String s) {
        List<String> out = new ArrayList<>();
        int total = 0, cur = 0, last = 0; boolean open = false;
        for (String t : s.split(" ")) {
            int k = kind(t);
            if (k == 0) {
                if (open) { out.add(String.valueOf(total + cur)); total = cur = last = 0; open = false; }
                out.add(t); continue;
            }
            if (open && !continues(last, k)) { out.add(String.valueOf(total + cur)); total = cur = 0; }
            open = true;
            switch (k) {
                case 4 -> cur = (cur == 0 ? 1 : cur) * 100;
                case 5 -> { total += (cur == 0 ? 1 : cur) * 1000; cur = 0; }
                default -> cur += val(t);
            }
            last = k;
        }
        if (open) out.add(String.valueOf(total + cur));
        return String.join(" ", out).trim();
    }
}
