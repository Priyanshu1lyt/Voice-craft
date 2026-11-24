package com.nebrionverse.voicecraft;

import com.nebrionverse.voicecraft.EnchantmentManager.Ench;
import net.minecraft.item.*;
import java.util.*;
import java.util.stream.Collectors;

public final class CommandGenerator {
    /**
     * 1.21.5+ component format. For 1.21.1 use  [enchantments={levels:{"id":n}}]
     * For 1.20.1 use NBT after the id:  {Enchantments:[{id:"minecraft:sharpness",lvl:5s}]}
     */
    public static String enchantComponent(Item item, Map<Ench, Integer> m) {
        if (m.isEmpty()) return "";
        String key = item == Items.ENCHANTED_BOOK ? "stored_enchantments" : "enchantments";
        return "[" + key + "={" + m.entrySet().stream()
            .map(e -> "\"" + e.getKey().id() + "\":" + e.getValue()).collect(Collectors.joining(",")) + "}]";
    }
}
