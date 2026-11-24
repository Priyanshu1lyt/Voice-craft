package com.nebrionverse.voicecraft;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.vosk.*;
import java.nio.file.*;
import java.util.*;

public class SpeechToTextEngine {
    public record Result(String text, double confidence) { }
    public static class MissingModelException extends Exception {
        public final Path dir;
        public MissingModelException(Path d) { super("Missing model: " + d); dir = d; }
    }
    private static final Map<String, Model> MODELS = new HashMap<>();
    private Recognizer rec;
    private final StringBuilder text = new StringBuilder();
    private double confSum; private int confN;

    public static Path modelDir(String lang) {
        return FabricLoader.getInstance().getConfigDir().resolve("voicecraft_ai/models/" + lang);
    }
    public static synchronized Model model(String lang) throws Exception {
        Model m = MODELS.get(lang);
        if (m == null) {
            Path p = modelDir(lang);
            if (!Files.isDirectory(p)) throw new MissingModelException(p);
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            m = new Model(p.toString());
            MODELS.put(lang, m);
        }
        return m;
    }
    public static synchronized void closeAll() { MODELS.values().forEach(Model::close); MODELS.clear(); }

    public synchronized void begin(String lang) throws Exception {
        abort();
        rec = new Recognizer(model(lang), 16000f);
        rec.setWords(true);
        text.setLength(0); confSum = 0; confN = 0;
    }
    public synchronized void abort() { if (rec != null) { rec.close(); rec = null; } }

    private void collect(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (o.has("text") && !o.get("text").getAsString().isBlank()) text.append(o.get("text").getAsString()).append(' ');
        if (o.has("result")) for (JsonElement e : o.getAsJsonArray("result")) {
            confSum += e.getAsJsonObject().get("conf").getAsDouble(); confN++;
        }
    }
    public synchronized String feed(byte[] d) {
        if (rec == null) return "";
        if (rec.acceptWaveForm(d, d.length)) { collect(rec.getResult()); return text.toString().trim(); }
        String p = JsonParser.parseString(rec.getPartialResult()).getAsJsonObject().get("partial").getAsString();
        return (text + p).trim();
    }
    public synchronized Result finish() {
        if (rec == null) return new Result("", 0);
        collect(rec.getFinalResult());
        rec.close(); rec = null;
        return new Result(text.toString().trim(), confN == 0 ? 0.7 : confSum / confN);
    }
}
