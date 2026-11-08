package com.voicefork.android.dsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Chain implements Effect {
    public final List<Effect> effects = new ArrayList<>();
    public Chain() {}
    public Chain(List<Effect> fx) { effects.addAll(fx); }
    public Chain(Effect... fx) { effects.addAll(Arrays.asList(fx)); }
    @Override public float[] process(float[] x) {
        for (Effect fx : effects) x = fx.process(x);
        return x;
    }
    @Override public void reset() { for (Effect fx : effects) fx.reset(); }
}
