package com.voicefork.android.dsp;

public class RingMod implements Effect {
    private final float freq, mix, sr;
    private double phase = 0.0;
    public RingMod(int sr, float freqHz, float mix) { this.sr = sr; this.freq = freqHz; this.mix = mix; }
    @Override public float[] process(float[] x) {
        float[] y = new float[x.length];
        double step = 2.0 * Math.PI * freq / sr;
        for (int i = 0; i < x.length; i++) {
            float mod = (float) Math.sin(phase);
            phase += step;
            if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI;
            y[i] = x[i] * (1f - mix + mix * mod);
        }
        return y;
    }
    @Override public void reset() { phase = 0.0; }
}
