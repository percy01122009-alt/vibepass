package com.voicefork.android.dsp;

public interface Effect {
    float[] process(float[] x);
    default void reset() {}
}
