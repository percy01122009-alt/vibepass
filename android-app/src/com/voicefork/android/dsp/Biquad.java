package com.voicefork.android.dsp;

public class Biquad implements Effect {
    private final float b0, b1, b2, a1, a2;
    private float z1 = 0f, z2 = 0f; // direct form II transposed state

    public Biquad(int sr, String kind, float freq, float q, float gainDb) {
        double w0 = 2.0 * Math.PI * freq / sr;
        double cos = Math.cos(w0), sin = Math.sin(w0);
        double alpha = sin / (2.0 * q);
        double A = Math.pow(10.0, gainDb / 40.0);
        double bb0, bb1, bb2, aa0, aa1, aa2;
        switch (kind) {
            case "lowpass":
                bb0 = (1 - cos) / 2; bb1 = 1 - cos; bb2 = (1 - cos) / 2;
                aa0 = 1 + alpha; aa1 = -2 * cos; aa2 = 1 - alpha; break;
            case "highpass":
                bb0 = (1 + cos) / 2; bb1 = -(1 + cos); bb2 = (1 + cos) / 2;
                aa0 = 1 + alpha; aa1 = -2 * cos; aa2 = 1 - alpha; break;
            case "bandpass":
                bb0 = alpha; bb1 = 0.0; bb2 = -alpha;
                aa0 = 1 + alpha; aa1 = -2 * cos; aa2 = 1 - alpha; break;
            case "peak":
                bb0 = 1 + alpha * A; bb1 = -2 * cos; bb2 = 1 - alpha * A;
                aa0 = 1 + alpha / A; aa1 = -2 * cos; aa2 = 1 - alpha / A; break;
            default:
                throw new IllegalArgumentException("unknown filter kind " + kind);
        }
        b0 = (float)(bb0 / aa0); b1 = (float)(bb1 / aa0); b2 = (float)(bb2 / aa0);
        a1 = (float)(aa1 / aa0); a2 = (float)(aa2 / aa0);
    }
    public Biquad(int sr, String kind, float freq, float q) { this(sr, kind, freq, q, 0f); }
    public Biquad(int sr, String kind, float freq) { this(sr, kind, freq, 0.707f, 0f); }

    @Override public float[] process(float[] x) {
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            float in = x[i];
            float out = b0 * in + z1;
            z1 = b1 * in - a1 * out + z2;
            z2 = b2 * in - a2 * out;
            y[i] = out;
        }
        return y;
    }
    @Override public void reset() { z1 = 0f; z2 = 0f; }
}
