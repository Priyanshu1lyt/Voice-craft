package com.nebrionverse.voicecraft;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;

public class VoiceCraftConfig {
    public boolean micEnabled = true, toggleMode = false, quickMode = true, allowBroad = true, hudVisible = true;
    public String language = "en", micDevice = "";
    public float micGain = 1f, sttMinConfidence = 0.45f, autoConfidence = 0.80f, hudScale = 1f, hudOpacity = 0.8f;
    public int hudCorner = 0; // 0 TL, 1 TR, 2 BL, 3 BR
    public Set<String> disabledCategories = new HashSet<>();

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("voicecraft_ai.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static VoiceCraftConfig inst;

    public static synchronized VoiceCraftConfig get() {
        if (inst == null) {
            try { if (Files.exists(FILE)) inst = GSON.fromJson(Files.readString(FILE), VoiceCraftConfig.class); }
            catch (Exception e) { VoiceCraftClient.LOG.warn("Bad config, using defaults", e); }
            if (inst == null) inst = new VoiceCraftConfig();
        }
        return inst;
    }
    public static synchronized void save() {
        try { Files.writeString(FILE, GSON.toJson(get())); } catch (Exception e) { VoiceCraftClient.LOG.warn("Config save failed", e); }
    }
}
