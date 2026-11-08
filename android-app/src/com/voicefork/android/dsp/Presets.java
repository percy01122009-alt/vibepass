package com.voicefork.android.dsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Presets {
    public static class Info {
        public final String key, name, description;
        public Info(String key, String name, String description) { this.key=key; this.name=name; this.description=description; }
    }

    private static final Map<String, Info> INFO = new LinkedHashMap<>();
    static {
        INFO.put("clean",   new Info("clean","Clean","Your normal voice (pass-through)"));
        INFO.put("deep",    new Info("deep","Deep","Lower, bigger voice"));
        INFO.put("chipmunk",new Info("chipmunk","Chipmunk","Fast and squeaky"));
        INFO.put("demon",   new Info("demon","Demon","Very low, gritty, dark"));
        INFO.put("giant",   new Info("giant","Giant","Huge voice in a big hall"));
        INFO.put("robot",   new Info("robot","Robot","Metallic robot"));
        INFO.put("alien",   new Info("alien","Alien","Warbly sci-fi alien"));
        INFO.put("radio",   new Info("radio","Radio","Walkie-talkie / old radio"));
        INFO.put("megaphone",new Info("megaphone","Megaphone","Loud, honky megaphone"));
        INFO.put("cave",    new Info("cave","Cave","Echoing cave"));
        INFO.put("8bit",    new Info("8bit","8-bit","Crunchy retro console"));
    }
    public static List<Info> list() { return new ArrayList<>(INFO.values()); }
    public static Info get(String key) { Info i = INFO.get(key); return i!=null?i:INFO.get("clean"); }

    private static List<Effect> presetEffects(String name, int sr) {
        switch (name) {
            case "deep": return Arrays.asList(
                    new PitchShift(sr, 0.78f),
                    new Biquad(sr, "peak", 140f, 0.8f, 3f));
            case "chipmunk": return Arrays.asList(
                    new PitchShift(sr, 1.6f, 30f),
                    new Biquad(sr, "highpass", 150f));
            case "demon": return Arrays.asList(
                    new PitchShift(sr, 0.6f, 55f),
                    new Distortion(10f, -4f),
                    new Biquad(sr, "lowpass", 3500f),
                    new DelayEffects.Reverb(sr, 1.5f, 0.18f, 0.35f));
            case "giant": return Arrays.asList(
                    new PitchShift(sr, 0.65f, 55f),
                    new DelayEffects.Reverb(sr, 2.5f, 0.3f, 0.35f));
            case "robot": return Arrays.asList(
                    new RingMod(sr, 50f, 1.0f),
                    new DelayEffects.Echo(sr, 11f, 0.55f, 0.6f, 0.0f));
            case "alien": return Arrays.asList(
                    new PitchShift(sr, 1.25f),
                    new RingMod(sr, 230f, 0.5f),
                    new DelayEffects.Echo(sr, 90f, 0.35f, 0.3f, 0.3f));
            case "radio": return Arrays.asList(
                    new Biquad(sr, "highpass", 450f),
                    new Biquad(sr, "lowpass", 2800f),
                    new Distortion(14f, -8f),
                    new Gain(4f));
            case "megaphone": return Arrays.asList(
                    new Biquad(sr, "bandpass", 1500f, 0.9f),
                    new Distortion(18f, -6f),
                    new Gain(6f));
            case "cave": return Arrays.asList(
                    new DelayEffects.Echo(sr, 280f, 0.45f, 0.4f, 0.4f),
                    new DelayEffects.Reverb(sr, 2.0f, 0.35f, 0.35f));
            case "8bit": return Arrays.asList(
                    new Bitcrush(sr, 5, 7000),
                    new Biquad(sr, "highpass", 200f));
            case "clean": default: return new ArrayList<>();
        }
    }

    public static Chain buildChain(String name, int sr, float gateDb, float gainDb) {
        List<Effect> chain = new ArrayList<>();
        if (Math.abs(gainDb) > 0.001f) chain.add(new Gain(gainDb));
        if (gateDb > -120f) chain.add(new NoiseGate(sr, gateDb, 3f, 150f));
        chain.addAll(presetEffects(name, sr));
        chain.add(new Limiter(sr, -1f, 80f));
        return new Chain(chain);
    }
}
