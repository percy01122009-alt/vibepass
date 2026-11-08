package com.voicefork.android.dsp;

import java.util.Arrays;

public class PitchShift implements Effect {
    private static final int MAX_CHUNK = 4096;
    private final float ratio;
    private final int W, minDelay, N, mask;
    private final float[] buf;
    private int t = 0;
    private double phase = 0.0;
    private final double inc;

    public PitchShift(int sr, float ratio, float windowMs) {
        this.ratio = ratio;
        this.W = Math.max(64, (int)(sr * windowMs / 1000f));
        this.minDelay = 2;
        int size = 1;
        while (size < W + minDelay + MAX_CHUNK + 4) size <<= 1;
        this.N = size;
        this.mask = size - 1;
        this.buf = new float[size];
        this.inc = (1.0 - ratio) / W;
    }
    public PitchShift(int sr, float ratio) { this(sr, ratio, 45f); }

    @Override public float[] process(float[] x) {
        if (Math.abs(ratio - 1f) < 1e-6f) return x;
        if (x.length > MAX_CHUNK) {
            float[] out = new float[x.length];
            for (int i = 0; i < x.length; i += MAX_CHUNK) {
                int end = Math.min(i + MAX_CHUNK, x.length);
                float[] chunk = Arrays.copyOfRange(x, i, end);
                float[] pc = process(chunk);
                System.arraycopy(pc, 0, out, i, pc.length);
            }
            return out;
        }
        int n = x.length;
        // write
        for (int i = 0; i < n; i++) buf[(t + i) & mask] = x[i];
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double ph = (phase + inc * (i + 1)) % 1.0;
            if (ph < 0) ph += 1.0;
            double sum = 0.0;
            for (int tap = 0; tap < 2; tap++) {
                double p = (ph + 0.5 * tap) % 1.0;
                double pos = (t + i) - (minDelay + p * W);
                int i0 = (int) Math.floor(pos);
                double frac = pos - i0;
                float s0 = buf[i0 & mask];
                float s1 = buf[(i0 + 1) & mask];
                double sample = s0 + (s1 - s0) * frac;
                double w = Math.sin(Math.PI * p);
                sum += sample * w * w;
            }
            out[i] = (float) sum;
        }
        if (n > 0) {
            double last = (phase + inc * n) % 1.0;
            if (last < 0) last += 1.0;
            phase = last;
        }
        t += n;
        return out;
    }
    @Override public void reset() { Arrays.fill(buf, 0f); t = 0; phase = 0.0; }
}
