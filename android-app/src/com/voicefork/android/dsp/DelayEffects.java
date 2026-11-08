package com.voicefork.android.dsp;

import java.util.Arrays;

public class DelayEffects {
    static class FeedbackLine {
        final int d;
        final float g;
        final float damp;
        final float[] hist;
        float zi = 0f; // one-pole state for damp: y = (1-damp)*x + damp*y_prev

        FeedbackLine(int d, float g, float damp) {
            this.d = Math.max(1, d);
            this.g = g;
            this.damp = damp;
            this.hist = new float[this.d];
        }
        // returns [delayed, w_all] packed? easier to return two arrays via holder
        static class Result { float[] delayed; float[] wAll; Result(float[] d, float[] w){delayed=d;wAll=w;} }
        Result run(float[] x) {
            int n = x.length;
            float[] delayed = new float[n];
            float[] wAll = new float[n];
            int i = 0;
            while (i < n) {
                int m = Math.min(d, n - i);
                // dly = hist[:m]
                for (int j = 0; j < m; j++) {
                    float dly = hist[j];
                    float fb = dly;
                    if (damp > 0f) {
                        fb = (1f - damp) * dly + damp * zi;
                        zi = fb;
                    }
                    float w = x[i + j] + g * fb;
                    delayed[i + j] = dly;
                    wAll[i + j] = w;
                }
                // shift hist left by m and append w's
                if (m < d) {
                    System.arraycopy(hist, m, hist, 0, d - m);
                }
                for (int j = 0; j < m; j++) hist[d - m + j] = wAll[i + j];
                i += m;
            }
            return new Result(delayed, wAll);
        }
        void reset() { Arrays.fill(hist, 0f); zi = 0f; }
    }

    public static class Echo implements Effect {
        private final FeedbackLine line;
        private final float mix;
        public Echo(int sr, float delayMs, float feedback, float mix, float damping) {
            int d = Math.max(1, Math.round(sr * delayMs / 1000f));
            this.line = new FeedbackLine(d, feedback, damping);
            this.mix = mix;
        }
        @Override public float[] process(float[] x) {
            float[] delayed = line.run(x).delayed;
            float[] y = new float[x.length];
            for (int i = 0; i < x.length; i++) y[i] = x[i] + delayed[i] * mix;
            return y;
        }
        @Override public void reset() { line.reset(); }
    }

    public static class Reverb implements Effect {
        private static final float[] COMBS_MS = {29.7f, 37.1f, 41.1f, 43.7f};
        private static final float[] ALLPASS_MS = {5.0f, 1.7f};
        private final FeedbackLine[] combs;
        private final FeedbackLine[] aps;
        private final float[] apG;
        private final float mix;
        public Reverb(int sr, float decayS, float mix, float damping) {
            this.mix = mix;
            combs = new FeedbackLine[COMBS_MS.length];
            for (int k = 0; k < COMBS_MS.length; k++) {
                int d = Math.max(1, Math.round(sr * COMBS_MS[k] / 1000f));
                float g = (float) Math.pow(10.0, -3.0 * d / (decayS * sr));
                combs[k] = new FeedbackLine(d, g, damping);
            }
            aps = new FeedbackLine[ALLPASS_MS.length];
            apG = new float[ALLPASS_MS.length];
            for (int k = 0; k < ALLPASS_MS.length; k++) {
                int d = Math.max(1, Math.round(sr * ALLPASS_MS[k] / 1000f));
                apG[k] = 0.7f;
                aps[k] = new FeedbackLine(d, apG[k], 0f);
            }
        }
        @Override public float[] process(float[] x) {
            int n = x.length;
            float[] wet = new float[n];
            for (FeedbackLine c : combs) {
                float[] dly = c.run(x).delayed;
                for (int i = 0; i < n; i++) wet[i] += dly[i];
            }
            float scale = 0.25f;
            for (int i = 0; i < n; i++) wet[i] *= scale;
            for (int k = 0; k < aps.length; k++) {
                FeedbackLine.Result r = aps[k].run(wet);
                float g = apG[k];
                for (int i = 0; i < n; i++) wet[i] = -g * r.wAll[i] + r.delayed[i];
            }
            float[] y = new float[n];
            for (int i = 0; i < n; i++) y[i] = x[i] * (1f - mix) + wet[i] * mix;
            return y;
        }
        @Override public void reset() { for (FeedbackLine c : combs) c.reset(); for (FeedbackLine a : aps) a.reset(); }
    }
}
