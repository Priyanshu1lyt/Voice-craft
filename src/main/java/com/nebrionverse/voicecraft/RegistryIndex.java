package com.nebrionverse.voicecraft;

import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;
import java.util.function.Function;

public final class RegistryIndex<T> {
    public record Entry<T>(T value, Identifier id, String name, String[] nameWords, String[] idWords) { }
    public record Match<T>(Entry<T> entry, double score) { }
    private volatile List<Entry<T>> entries = List.of();

    public boolean ready() { return !entries.isEmpty(); }

    public void build(Registry<T> reg, Function<T, Text> nameFn) {
        List<Entry<T>> out = new ArrayList<>();
        for (T v : reg) {
            Identifier id = reg.getId(v);
            if (id == null || id.getPath().equals("air")) continue;
            String name;
            try { name = LanguageProcessor.normalize(nameFn.apply(v).getString()); } catch (Throwable e) { name = ""; }
            out.add(new Entry<>(v, id, name, name.isEmpty() ? new String[0] : name.split(" "), id.getPath().replace('/', '_').split("_")));
        }
        entries = out;
    }

    public List<Match<T>> search(String q, int limit) {
        q = q.trim();
        if (q.isEmpty()) return List.of();
        String[] qw = q.split(" ");
        Set<String> variants = new LinkedHashSet<>(List.of(q));
        if (q.endsWith("ies")) variants.add(q.substring(0, q.length() - 3) + "y");
        if (q.endsWith("es")) variants.add(q.substring(0, q.length() - 2));
        if (q.endsWith("s")) variants.add(q.substring(0, q.length() - 1));
        List<Match<T>> out = new ArrayList<>();
        for (Entry<T> e : entries) {
            double s = score(qw, variants, e);
            if (s >= 0.5) out.add(new Match<>(e, s));
        }
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private double score(String[] qw, Set<String> variants, Entry<T> e) {
        String idJoined = String.join(" ", e.idWords());
        if (variants.contains(e.name()) || variants.contains(idJoined)) return 1.0;
        return Math.max(words(qw, e.nameWords()), words(qw, e.idWords()) * 0.97);
    }
    private double words(String[] qw, String[] nw) {
        if (nw.length == 0) return 0;
        double sum = 0;
        for (String q : qw) { double b = 0; for (String n : nw) b = Math.max(b, Fuzzy.wordSim(q, n)); sum += b; }
        double pen = nw.length > qw.length ? Math.max(0.7, 1 - 0.04 * (nw.length - qw.length)) : 1;
        return sum / qw.length * pen;
    }

    /** A confident, unambiguous match or empty (caller then asks the player to clarify). */
    public static <T> Optional<Match<T>> unique(List<Match<T>> m, double min) {
        if (m.isEmpty() || m.get(0).score() < min) return Optional.empty();
        if (m.get(0).score() >= 0.999) return (m.size() > 1 && m.get(1).score() >= 0.999) ? Optional.empty() : Optional.of(m.get(0));
        if (m.size() > 1 && m.get(0).score() - m.get(1).score() < 0.08) return Optional.empty();
        return Optional.of(m.get(0));
    }
    public static <T> String names(List<Match<T>> m, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, m.size()); i++) { if (i > 0) sb.append(", "); sb.append(m.get(i).entry().id()); }
        return sb.toString();
    }
}
