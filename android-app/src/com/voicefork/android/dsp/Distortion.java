package com.voicefork.android.dsp;

public class Distortion implements Effect {
    private final float drive, out;
    public Distortion(float driveDb, float outDb) {
        this.drive = (float) Math.pow(10.0, driveDb / 20.0);
        this.out = (float) Math.pow(10.0, outDb / 20.0);
    }
    @Override public float[] process(float[] x) {
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) y[i] = (float) Math.tanh(x[i] * drive) * out;
        return y;
    }
}
