package com.voicefork.android.dsp;

public class NoiseGate implements Effect {
    private final int sr;
    private final float threshold;
    private final double attackS, releaseS;
    private float g = 0f;

    public NoiseGate(int sr, float thresholdDb, float attackMs, float releaseMs) {
        this.sr = sr;
        this.threshold = (float) Math.pow(10.0, thresholdDb / 20.0);
        this.attackS = attackMs / 1000.0;
        this.releaseS = releaseMs / 1000.0;
    }
    public NoiseGate(int sr, float thresholdDb) { this(sr, thresholdDb, 3f, 150f); }

    @Override public float[] process(float[] x) {
        if (x.length == 0) return x;
        double sum = 0;
        for (float v : x) sum += v * v;
        float rms = (float) Math.sqrt(sum / x.length);
        float target = rms >= threshold ? 1f : 0f;
        // opening is fast direction for gate
        double t = target > g ? attackS : releaseS;
        double a = t > 0 ? Math.exp(-x.length / (sr * t)) : 0.0;
        float newG = (float)(target + (g - target) * a);
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            float ramp = g + (newG - g) * ((float)i / x.length);
            y[i] = x[i] * ramp;
        }
        g = newG;
        return y;
    }
    @Override public void reset() { g = 0f; }
}
