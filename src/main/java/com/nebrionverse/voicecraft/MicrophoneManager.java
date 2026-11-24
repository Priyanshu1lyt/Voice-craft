package com.nebrionverse.voicecraft;

import javax.sound.sampled.*;
import java.util.*;
import java.util.function.Consumer;

public class MicrophoneManager {
    public static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    public static volatile float level;
    private volatile boolean recording;

    public boolean isRecording() { return recording; }
    public void stop() { recording = false; }

    public static List<String> devices() {
        List<String> l = new ArrayList<>(); l.add("");
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (Mixer.Info mi : AudioSystem.getMixerInfo())
            try { if (AudioSystem.getMixer(mi).isLineSupported(info)) l.add(mi.getName()); } catch (Exception ignored) { }
        return l;
    }

    private TargetDataLine open() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        String name = VoiceCraftConfig.get().micDevice;
        TargetDataLine line = null;
        if (!name.isEmpty())
            for (Mixer.Info mi : AudioSystem.getMixerInfo())
                if (mi.getName().equals(name) && AudioSystem.getMixer(mi).isLineSupported(info)) {
                    line = (TargetDataLine) AudioSystem.getMixer(mi).getLine(info); break;
                }
        if (line == null) line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT, 16000);
        return line;
    }

    public synchronized boolean start(Consumer<byte[]> sink, Runnable onClosed) throws LineUnavailableException {
        if (recording) return false;
        TargetDataLine line = open();
        recording = true;
        Thread th = new Thread(() -> {
            byte[] buf = new byte[3200];
            try {
                line.start();
                while (recording) {
                    int n = line.read(buf, 0, buf.length);
                    if (n > 0) { byte[] c = gain(buf, n); level = rms(c); sink.accept(c); }
                }
            } catch (Throwable t) { ErrorHandler.report(t); }
            finally {
                line.stop(); line.close(); level = 0; recording = false;
                try { onClosed.run(); } catch (Throwable t) { ErrorHandler.report(t); }
            }
        }, "VoiceCraft-Mic");
        th.setDaemon(true); th.start();
        return true;
    }

    private static byte[] gain(byte[] b, int n) {
        float g = VoiceCraftConfig.get().micGain;
        byte[] o = new byte[n];
        for (int i = 0; i + 1 < n; i += 2) {
            int s = (short) ((b[i] & 0xFF) | (b[i + 1] << 8));
            s = Math.max(-32768, Math.min(32767, Math.round(s * g)));
            o[i] = (byte) s; o[i + 1] = (byte) (s >> 8);
        }
        return o;
    }
    private static float rms(byte[] b) {
        double sum = 0; int c = 0;
        for (int i = 0; i + 1 < b.length; i += 2) { int s = (short) ((b[i] & 0xFF) | (b[i + 1] << 8)); sum += (double) s * s; c++; }
        return c == 0 ? 0 : (float) (Math.sqrt(sum / c) / 32768.0);
    }
}
