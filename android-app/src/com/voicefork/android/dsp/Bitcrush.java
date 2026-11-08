package com.voicefork.android.dsp;

public class Bitcrush implements Effect {
    private final float levels;
    private final int hold;
    private float held = 0f;
    private int phase = 0;
    public Bitcrush(int sr, int bits, int targetSr) {
        this.levels = (float) Math.pow(2.0, Math.max(1, bits) - 1);
        this.hold = Math.max(1, Math.round((float) sr / Math.max(1, targetSr)));
    }
    @Override public float[] process(float[] x) {
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            if (phase == 0) held = Math.round(x[i] * levels) / levels;
            y[i] = held;
            phase = (phase + 1) % hold;
        }
        return y;
    }
    @Override public void reset() { held = 0f; phase = 0; }
}
