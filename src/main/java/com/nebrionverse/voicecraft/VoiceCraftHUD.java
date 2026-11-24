package com.nebrionverse.voicecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import java.util.*;

public final class VoiceCraftHUD {
    public enum State {
        IDLE("", 0xFFFFFFFF, false), MIC("MICROPHONE ACTIVE", 0xFFFF5555, true), LISTENING("LISTENING...", 0xFFFFAA00, true),
        UNDERSTANDING("UNDERSTANDING COMMAND...", 0xFF55FFFF, true), READY("COMMAND READY", 0xFF55FF55, true),
        SENT("COMMAND SENT", 0xFF55FFFF, false), SUCCESS("SUCCESS", 0xFF55FF55, false),
        FAILED("COMMAND FAILED", 0xFFFF5555, false), ERROR("ERROR", 0xFFFF5555, false);
        final String header; final int color; final boolean sticky;
        State(String h, int c, boolean s) { header = h; color = c; sticky = s; }
    }
    private static volatile State state = State.IDLE;
    private static volatile List<String> lines = List.of();
    private static volatile long since;

    public static void set(State s, String... l) { lines = List.of(l); state = s; since = System.currentTimeMillis(); }
    public static void idle() { state = State.IDLE; }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        VoiceCraftConfig cfg = VoiceCraftConfig.get();
        State s = state;
        if (!cfg.hudVisible || s == State.IDLE) return;
        long age = System.currentTimeMillis() - since;
        if (!s.sticky && age > 5000) { state = State.IDLE; return; }
        if (s == State.READY && age > 30000) { ConfirmationManager.clear(); state = State.IDLE; return; }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        List<String> all = new ArrayList<>();
        all.add(s.header); all.addAll(lines);
        int w = 0;
        for (String l : all) w = Math.max(w, tr.getWidth(l));
        w += 22;
        int h = all.size() * 11 + 8;
        float sc = cfg.hudScale;
        int sw = (int) (ctx.getScaledWindowWidth() / sc), sh = (int) (ctx.getScaledWindowHeight() / sc);
        int x = cfg.hudCorner % 2 == 0 ? 6 : sw - w - 6;
        int y = cfg.hudCorner < 2 ? 6 : sh - h - 40;

        var m = ctx.getMatrices();
        m.pushMatrix(); m.scale(sc, sc);
        ctx.fill(x, y, x + w, y + h, ((int) (cfg.hudOpacity * 255)) << 24);
        if (s == State.MIC || s == State.LISTENING) {
            int pulse = (int) (140 + 115 * Math.abs(Math.sin(System.currentTimeMillis() / 250.0)));
            ctx.fill(x + 4, y + 5, x + 9, y + 10, 0xFF000000 | (pulse << 16));
            int bar = (int) ((w - 8) * Math.min(1f, MicrophoneManager.level * 4));
            ctx.fill(x + 4, y + h - 3, x + 4 + bar, y + h - 1, 0xFF55FF55);
        }
        for (int i = 0; i < all.size(); i++)
            ctx.drawTextWithShadow(tr, all.get(i), x + (i == 0 ? 14 : 8), y + 4 + i * 11, i == 0 ? s.color : 0xFFFFFFFF);
        m.popMatrix();
    }
}
