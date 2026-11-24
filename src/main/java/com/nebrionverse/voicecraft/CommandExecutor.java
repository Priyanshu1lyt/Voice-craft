package com.nebrionverse.voicecraft;

import com.nebrionverse.voicecraft.CommandInterpreter.Plan;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import java.util.concurrent.*;

public final class CommandExecutor {
    private static final ScheduledExecutorService POOL = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "VoiceCraft-Exec"); t.setDaemon(true); return t; });
    private static volatile long sentAt; private static volatile boolean awaiting;

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!awaiting || overlay || System.currentTimeMillis() - sentAt > 4000) return;
            awaiting = false;
            TextColor col = msg.getStyle().getColor();
            boolean err = col != null && col.getRgb() == Formatting.RED.getColorValue();
            String s = msg.getString();
            VoiceCraftHUD.set(err ? VoiceCraftHUD.State.FAILED : VoiceCraftHUD.State.SUCCESS, s.length() > 80 ? s.substring(0, 80) : s);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (awaiting && System.currentTimeMillis() - sentAt > 4000) {
                awaiting = false;
                VoiceCraftHUD.set(VoiceCraftHUD.State.SENT, "No reply from server (result unconfirmed)");
            }
        });
    }

    /** Sends through the normal chat/command path. The server validates permissions; nothing is bypassed. */
    public static void run(Plan p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        VoiceCraftHUD.set(VoiceCraftHUD.State.SENT, p.commands().toArray(new String[0]));
        long delay = 0;
        for (int i = 0; i < p.commands().size(); i++) {
            String cmd = p.commands().get(i); boolean last = i == p.commands().size() - 1;
            POOL.schedule(() -> mc.execute(() -> {
                var h = mc.getNetworkHandler();
                if (h == null) { VoiceCraftHUD.set(VoiceCraftHUD.State.ERROR, "Not connected to a world or server"); return; }
                if (last) { sentAt = System.currentTimeMillis(); awaiting = true; }
                h.sendChatCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }), delay, TimeUnit.MILLISECONDS);
            delay += 200;
        }
    }
}
