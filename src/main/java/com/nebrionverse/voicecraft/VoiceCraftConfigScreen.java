package com.nebrionverse.voicecraft;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.*;

public class VoiceCraftConfigScreen extends Screen {
    private final Screen parent;
    private final VoiceCraftConfig c = VoiceCraftConfig.get();
    private final List<String> devices = MicrophoneManager.devices();
    private int catIdx = 0;
    private boolean testing = false;

    public VoiceCraftConfigScreen(Screen parent) { super(Text.literal("VoiceCraft AI Settings")); this.parent = parent; }

    private static Text onOff(String l, boolean v) { return Text.literal(l + ": " + (v ? "ON" : "OFF")); }
    private ButtonWidget toggle(int x, int y, String l, Supplier<Boolean> g, Consumer<Boolean> s) {
        return ButtonWidget.builder(onOff(l, g.get()), b -> { s.accept(!g.get()); b.setMessage(onOff(l, g.get())); })
                .dimensions(x, y, 200, 20).build();
    }
    private ButtonWidget cycle(int x, int y, Supplier<String> label, Runnable next) {
        return ButtonWidget.builder(Text.literal(label.get()), b -> { next.run(); b.setMessage(Text.literal(label.get())); })
                .dimensions(x, y, 200, 20).build();
    }
    private String dev() { String d = c.micDevice.isEmpty() ? "System default" : c.micDevice; return "Mic: " + (d.length() > 24 ? d.substring(0, 24) : d); }
    private String cat() { return CommandInterpreter.CATEGORIES.get(catIdx); }

    @Override protected void init() {
        int L = width / 2 - 205, R = width / 2 + 5, y = 30, s = 24;
        addDrawableChild(toggle(L, y, "Microphone", () -> c.micEnabled, v -> c.micEnabled = v));
        addDrawableChild(cycle(L, y + s, this::dev, () -> c.micDevice = devices.get((devices.indexOf(c.micDevice) + 1) % devices.size())));
        addDrawableChild(cycle(L, y + 2 * s, () -> "Recording: " + (c.toggleMode ? "Toggle" : "Push-to-talk"), () -> c.toggleMode = !c.toggleMode));
        addDrawableChild(cycle(L, y + 3 * s, () -> "Language: " + (c.language.equals("en") ? "English (+Hinglish)" : "Hindi"),
                () -> { c.language = c.language.equals("en") ? "hi" : "en"; VoiceCraftClient.MANAGER.preload(); }));
        addDrawableChild(cycle(L, y + 4 * s, () -> "Execution: " + (c.quickMode ? "Quick" : "Always confirm"), () -> c.quickMode = !c.quickMode));
        addDrawableChild(toggle(L, y + 5 * s, "Broad-target commands", () -> c.allowBroad, v -> c.allowBroad = v));
        addDrawableChild(new Slider(L, y + 6 * s, "Mic sensitivity", 0.5f, 3f, c.micGain, v -> c.micGain = v));
        addDrawableChild(new Slider(L, y + 7 * s, "Min recognition confidence", 0.1f, 0.95f, c.sttMinConfidence, v -> c.sttMinConfidence = v));

        addDrawableChild(toggle(R, y, "HUD", () -> c.hudVisible, v -> c.hudVisible = v));
        addDrawableChild(cycle(R, y + s, () -> "HUD position: " + new String[]{"Top-left", "Top-right", "Bottom-left", "Bottom-right"}[c.hudCorner],
                () -> c.hudCorner = (c.hudCorner + 1) % 4));
        addDrawableChild(new Slider(R, y + 2 * s, "HUD scale", 0.5f, 2f, c.hudScale, v -> c.hudScale = v));
        addDrawableChild(new Slider(R, y + 3 * s, "HUD opacity", 0.1f, 1f, c.hudOpacity, v -> c.hudOpacity = v));
        addDrawableChild(new Slider(R, y + 4 * s, "Auto-run confidence", 0.5f, 1f, c.autoConfidence, v -> c.autoConfidence = v));
        ButtonWidget[] enabled = new ButtonWidget[1];
        addDrawableChild(cycle(R, y + 5 * s, () -> "Category: " + cat(), () -> {
            catIdx = (catIdx + 1) % CommandInterpreter.CATEGORIES.size();
            if (enabled[0] != null) enabled[0].setMessage(onOff("Category enabled", !c.disabledCategories.contains(cat())));
        }));
        enabled[0] = addDrawableChild(ButtonWidget.builder(onOff("Category enabled", !c.disabledCategories.contains(cat())), b -> {
            if (!c.disabledCategories.remove(cat())) c.disabledCategories.add(cat());
            b.setMessage(onOff("Category enabled", !c.disabledCategories.contains(cat())));
        }).dimensions(R, y + 6 * s, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Test microphone"), b -> {
            try {
                if (testing) { VoiceCraftClient.MANAGER.mic().stop(); testing = false; }
                else { testing = VoiceCraftClient.MANAGER.mic().start(x -> { }, () -> { }); }
            } catch (Throwable t) { ErrorHandler.report(t); }
        }).dimensions(R, y + 7 * s, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(width / 2 - 100, height - 28, 200, 20).build());
    }

    @Override public void render(DrawContext ctx, int mx, int my, float d) {
        super.render(ctx, mx, my, d);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        int bar = (int) (200 * Math.min(1f, MicrophoneManager.level * 4));
        ctx.fill(width / 2 - 100, height - 42, width / 2 + 100, height - 36, 0xFF222222);
        ctx.fill(width / 2 - 100, height - 42, width / 2 - 100 + bar, height - 36, 0xFF55FF55);
    }
    @Override public void removed() { VoiceCraftClient.MANAGER.mic().stop(); VoiceCraftConfig.save(); }
    @Override public void close() { client.setScreen(parent); }

    private static class Slider extends SliderWidget {
        private final String label; private final float min, max; private final Consumer<Float> set;
        Slider(int x, int y, String label, float min, float max, float cur, Consumer<Float> set) {
            super(x, y, 200, 20, Text.empty(), (cur - min) / (max - min));
            this.label = label; this.min = min; this.max = max; this.set = set; updateMessage();
        }
        @Override protected void updateMessage() { setMessage(Text.literal(label + ": " + String.format("%.2f", min + (max - min) * value))); }
        @Override protected void applyValue() { set.accept((float) (min + (max - min) * value)); }
    }
}
