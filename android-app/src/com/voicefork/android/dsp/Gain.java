package com.voicefork.android.dsp;

public class Gain implements Effect {
    private final float g;
    public Gain(float db) { this.g = (float) Math.pow(10.0, db / 20.0); }
    @Override public float[] process(float[] x) {
        if (Math.abs(g - 1f) < 1e-6f) return x;
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) y[i] = x[i] * g;
        return y;
    }
}
