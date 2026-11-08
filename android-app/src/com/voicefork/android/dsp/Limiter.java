package com.voicefork.android.dsp;

public class Limiter implements Effect {
    private final int sr;
    private final float ceiling;
    private final double releaseS;
    private float g = 1f;

    public Limiter(int sr, float ceilingDb, float releaseMs) {
        this.sr = sr;
        this.ceiling = (float) Math.pow(10.0, ceilingDb / 20.0);
        this.releaseS = releaseMs / 1000.0;
    }
    public Limiter(int sr) { this(sr, -1f, 80f); }

    @Override public float[] process(float[] x) {
        if (x.length == 0) return x;
        float peak = 0f;
        for (float v : x) { float a = Math.abs(v); if (a > peak) peak = a; }
        float target = peak > 0 ? Math.min(1f, ceiling / peak) : 1f;
        double t = target < g ? 0.0 : releaseS; // attack instant (0), release smoothed
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
    @Override public void reset() { g = 1f; }
}
