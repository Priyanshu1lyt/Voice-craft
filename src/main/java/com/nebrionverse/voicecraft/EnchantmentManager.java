package com.nebrionverse.voicecraft;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.*;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import java.util.*;

public final class EnchantmentManager {
    public record Ench(RegistryEntry<Enchantment> entry, Identifier id, String name, String[] words, int max) { }
    public record Extracted(String rest, LinkedHashMap<Ench, Integer> found) { }
    private volatile List<Ench> all = List.of(), byLength = List.of();

    public void build(Registry<Enchantment> reg) {
        List<Ench> l = new ArrayList<>();
        reg.streamEntries().forEach(ref -> {
            String n = LanguageProcessor.normalize(ref.value().description().getString());
            l.add(new Ench(ref, ref.registryKey().getValue(), n, n.split(" "), ref.value().getMaxLevel()));
        });
        all = l;
        byLength = l.stream().sorted(Comparator.comparingInt((Ench e) -> -e.words().length)).toList();
    }

    /** Pulls "<enchant> [level]" out of the text. Missing level means the enchantment's max level. */
    public Extracted extract(String text) {
        List<String> t = new ArrayList<>(Arrays.asList(text.trim().split(" ")));
        LinkedHashMap<Ench, Integer> found = new LinkedHashMap<>();
        for (Ench e : byLength) {
            int n = e.words().length;
            for (int i = 0; i + n <= t.size(); i++) {
                double s = 0;
                for (int k = 0; k < n; k++) s += Fuzzy.wordSim(t.get(i + k), e.words()[k]);
                if (s / n < 0.88) continue;
                int level = e.max();
                for (int k = 0; k < n; k++) t.remove(i);
                if (i < t.size() && t.get(i).matches("\\d{1,3}")) { level = Integer.parseInt(t.get(i)); t.remove(i); }
                found.put(e, level);
                break;
            }
        }
        return new Extracted(String.join(" ", t), found);
    }

    /** "Enchanted X" with no specifics: every compatible, mutually-compatible, non-curse enchantment at max level. */
    public LinkedHashMap<Ench, Integer> defaultsFor(Item item) {
        ItemStack stack = new ItemStack(item);
        LinkedHashMap<Ench, Integer> out = new LinkedHashMap<>();
        for (Ench e : all) {
            if (e.id().getPath().contains("curse") || !e.entry().value().isAcceptableItem(stack)) continue;
            boolean ok = true;
            for (Ench o : out.keySet()) if (!Enchantment.canBeCombined(e.entry(), o.entry())) ok = false;
            if (ok) out.put(e, e.max());
        }
        return out;
    }
}
