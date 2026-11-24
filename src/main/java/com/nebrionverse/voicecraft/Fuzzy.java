package com.nebrionverse.voicecraft;

public final class Fuzzy {
    public static int lev(String a, String b) {
        int[] p = new int[b.length() + 1], c = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) p[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            c[0] = i;
            for (int j = 1; j <= b.length(); j++)
                c[j] = Math.min(Math.min(c[j - 1] + 1, p[j] + 1), p[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = p; p = c; c = t;
        }
        return p[b.length()];
    }
    public static double ratio(String a, String b) {
        int m = Math.max(a.length(), b.length());
        return m == 0 ? 1 : 1.0 - (double) lev(a, b) / m;
    }
    public static String phon(String s) {
        s = s.toLowerCase().replace("ph", "f").replace("ck", "k").replaceAll("c(?=[aou])", "k")
             .replace("c", "s").replace("z", "s").replace("q", "k").replace("x", "ks").replaceAll("(.)\\1+", "$1");
        if (s.length() < 2) return s;
        return s.charAt(0) + s.substring(1).replaceAll("[aeiouyhw]", "");
    }
    public static double wordSim(String a, String b) {
        if (a.equals(b)) return 1;
        if (a.equals(b + "s") || b.equals(a + "s")) return 0.98;
        if (a.length() >= 3 && b.startsWith(a)) return 0.9;
        if (a.length() >= 4 && b.length() >= 4 && phon(a).equals(phon(b))) return 0.86;
        double r = ratio(a, b);
        return r >= 0.7 ? r * 0.95 : 0;
    }
}
