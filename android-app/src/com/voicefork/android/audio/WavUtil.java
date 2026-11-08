package com.voicefork.android.audio;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WavUtil {
    public static void writeWav(File file, float[] samples, int sr) throws IOException {
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            int dataLen = samples.length * 2;
            ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            h.put("RIFF".getBytes()); h.putInt(36 + dataLen); h.put("WAVE".getBytes());
            h.put("fmt ".getBytes()); h.putInt(16); h.putShort((short)1); h.putShort((short)1);
            h.putInt(sr); h.putInt(sr * 2); h.putShort((short)2); h.putShort((short)16);
            h.put("data".getBytes()); h.putInt(dataLen);
            fos.write(h.array());
            ByteBuffer b = ByteBuffer.allocate(Math.min(dataLen, 8192)).order(ByteOrder.LITTLE_ENDIAN);
            for (float s : samples) {
                if (!b.hasRemaining()) { b.flip(); fos.write(b.array(), 0, b.limit()); b.clear(); }
                float c = Math.max(-1f, Math.min(1f, s));
                b.putShort((short)(c * 32767));
            }
            if (b.position() > 0) { b.flip(); fos.write(b.array(), 0, b.limit()); }
        }
    }

    public static float[] readWav(File file, int targetSr) throws IOException {
        try (FileInputStream fis = new FileInputStream(file); BufferedInputStream in = new BufferedInputStream(fis)) {
            byte[] hdr = readFully(in, 12);
            if (hdr == null || !"RIFF".equals(new String(hdr,0,4)) || !"WAVE".equals(new String(hdr,8,4)))
                throw new IOException("Not a WAV file");
            int sr = 44100, channels = 1, bits = 16;
            byte[] data = null;
            while (true) {
                byte[] ch = readFully(in, 8);
                if (ch == null) break;
                String id = new String(ch,0,4);
                int len = ByteBuffer.wrap(ch,4,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if ("fmt ".equals(id)) {
                    byte[] fmt = readFully(in, Math.min(len, 32));
                    if (fmt != null && fmt.length >= 16) {
                        ByteBuffer fb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN);
                        fb.getShort(); channels = fb.getShort(); sr = fb.getInt();
                        fb.getInt(); fb.getShort(); bits = fb.getShort();
                    }
                    if (len > 32) skip(in, len - 32);
                } else if ("data".equals(id)) {
                    data = readFully(in, len);
                } else {
                    skip(in, len);
                }
                if ((len & 1) == 1) skip(in, 1);
            }
            if (data == null) throw new IOException("No data chunk");
            int bytesPerSample = bits / 8;
            int frameCount = data.length / (bytesPerSample * channels);
            float[] mono = new float[frameCount];
            ByteBuffer db = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < frameCount; i++) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) {
                    float v = 0f;
                    if (bits == 16) v = db.getShort() / 32768f;
                    else if (bits == 8) v = (db.get() & 0xFF - 128) / 128f;
                    else if (bits == 32) v = db.getInt() / 2147483648f;
                    else { skipBytes(db, bytesPerSample); v = 0f; }
                    sum += v;
                }
                mono[i] = sum / channels;
            }
            if (sr != targetSr && mono.length > 0) mono = resample(mono, sr, targetSr);
            return mono;
        }
    }

    public static float[] resample(float[] x, int src, int dst) {
        if (src == dst || x.length == 0) return x;
        int n = Math.max(1, (int)Math.round((long)x.length * dst / src));
        float[] y = new float[n];
        double ratio = (double)(x.length - 1) / Math.max(1, n - 1);
        for (int i = 0; i < n; i++) {
            double pos = i * ratio;
            int i0 = (int)pos; int i1 = Math.min(i0 + 1, x.length - 1);
            float frac = (float)(pos - i0);
            y[i] = x[i0] + (x[i1] - x[i0]) * frac;
        }
        return y;
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        if (n <= 0) return new byte[0];
        byte[] b = new byte[n]; int off = 0;
        while (off < n) { int r = in.read(b, off, n - off); if (r < 0) return off == 0 ? null : java.util.Arrays.copyOf(b, off); off += r; }
        return b;
    }
    private static void skip(InputStream in, int n) throws IOException { long left = n; while (left > 0) { long r = in.skip(left); if (r <= 0) { if (in.read() < 0) break; r = 1; } left -= r; } }
    private static void skipBytes(ByteBuffer b, int n) { b.position(Math.min(b.limit(), b.position()+n)); }
}
