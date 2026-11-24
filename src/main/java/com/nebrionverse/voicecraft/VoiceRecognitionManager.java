package com.nebrionverse.voicecraft;

import com.nebrionverse.voicecraft.CommandInterpreter.*;
import com.nebrionverse.voicecraft.VoiceCraftHUD.State;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VoiceRecognitionManager {
    private final MicrophoneManager mic = new MicrophoneManager();
    private final SpeechToTextEngine stt = new SpeechToTextEngine();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile List<String> players = List.of();
    private volatile String self = "";

    public MicrophoneManager mic() { return mic; }

    public void preload() {
        String lang = VoiceCraftConfig.get().language;
        CompletableFuture.runAsync(() -> { try { SpeechToTextEngine.model(lang); } catch (Throwable t) { VoiceCraftClient.LOG.warn("Model preload: {}", t.getMessage()); } });
    }

    public void start(MinecraftClient mc) {
        VoiceCraftConfig cfg = VoiceCraftConfig.get();
        if (!cfg.micEnabled) { VoiceCraftHUD.set(State.ERROR, "Microphone is disabled in settings"); return; }
        if (mc.getNetworkHandler() == null || mc.player == null) return;
        if (!busy.compareAndSet(false, true)) return;
        try {
            players = mc.getNetworkHandler().getPlayerList().stream().map(e -> e.getProfile().name()).toList();
            self = mc.player.getName().getString();
            stt.begin(cfg.language);
            VoiceCraftHUD.set(State.MIC);
            boolean ok = mic.start(chunk -> {
                String p = stt.feed(chunk);
                if (!p.isBlank()) VoiceCraftHUD.set(State.LISTENING, p);
            }, this::finish);
            if (!ok) busy.set(false);
        } catch (Throwable t) { stt.abort(); busy.set(false); ErrorHandler.report(t); }
    }

    public void stop() { mic.stop(); }

    private void finish() {
        VoiceCraftConfig cfg = VoiceCraftConfig.get();
        try {
            VoiceCraftHUD.set(State.UNDERSTANDING);
            var r = stt.finish();
            busy.set(false);
            if (r.text().isBlank()) { VoiceCraftHUD.set(State.ERROR, "Didn't catch that. Hold the key and speak."); return; }
            String said = "YOU SAID: " + r.text().toUpperCase();
            VoiceCraftHUD.set(State.UNDERSTANDING, said);
            if (r.confidence() < cfg.sttMinConfidence) {
                VoiceCraftHUD.set(State.ERROR, said, "Low confidence (" + Math.round(r.confidence() * 100) + "%). Please repeat."); return;
            }
            String norm = LanguageProcessor.normalize(r.text());
            if (ConfirmationManager.has()) {
                if (ConfirmationManager.isYes(norm)) { confirm(); return; }
                if (ConfirmationManager.isNo(norm)) { cancel(); return; }
                ConfirmationManager.clear();
            }
            Plan p = CommandInterpreter.interpret(new Ctx(r.text(), norm, players, self, r.confidence()));
            if (p.error() != null) { VoiceCraftHUD.set(State.ERROR, said, p.error()); return; }
            if (p.broad() && !cfg.allowBroad) { VoiceCraftHUD.set(State.ERROR, "Broad-target commands are disabled"); return; }
            boolean auto = cfg.quickMode && !p.needsConfirm() && p.confidence() >= cfg.autoConfidence;
            if (auto) { CommandExecutor.run(p); return; }
            ConfirmationManager.set(p);
            List<String> lines = new ArrayList<>(List.of(said));
            lines.addAll(p.commands());
            if (p.note() != null) lines.add("Note: " + p.note());
            lines.add("Confidence " + Math.round(p.confidence() * 100) + "%. Say YES, or press ] to run / [ to cancel");
            VoiceCraftHUD.set(State.READY, lines.toArray(new String[0]));
        } catch (Throwable t) { busy.set(false); ErrorHandler.report(t); }
    }

    public void confirm() { Plan p = ConfirmationManager.take(); if (p != null) CommandExecutor.run(p); }
    public void cancel() { if (ConfirmationManager.take() != null) VoiceCraftHUD.set(State.ERROR, "Cancelled"); }

    public void shutdown() { mic.stop(); stt.abort(); ConfirmationManager.clear(); VoiceCraftHUD.idle(); busy.set(false); }
}
