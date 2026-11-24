package com.nebrionverse.voicecraft;

import javax.sound.sampled.LineUnavailableException;

public final class ErrorHandler {
    public static void report(Throwable t) {
        VoiceCraftClient.LOG.error("VoiceCraft error", t);
        VoiceCraftHUD.set(VoiceCraftHUD.State.ERROR, message(t));
    }
    static String message(Throwable t) {
        if (t instanceof SpeechToTextEngine.MissingModelException m) return "Speech model missing. Put it in: " + m.dir;
        if (t instanceof LineUnavailableException || t instanceof IllegalArgumentException || t instanceof SecurityException)
            return "Microphone unavailable or blocked (check OS microphone permission)";
        if (t instanceof LinkageError) return "Speech engine failed to load (Vosk native library)";
        return "Voice error: " + t.getMessage();
    }
}
