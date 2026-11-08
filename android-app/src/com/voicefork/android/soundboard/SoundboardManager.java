package com.voicefork.android.soundboard;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import com.voicefork.android.audio.WavUtil;

import java.io.*;
import java.util.*;

public class SoundboardManager {
    public static class Clip { public final String name; public final File file; public final float seconds;
        public Clip(String n, File f, float s){name=n;file=f;seconds=s;} }

    private final File dir;
    private final int sr;
    public SoundboardManager(Context ctx, int sr) {
        this.sr = sr;
        this.dir = new File(ctx.getExternalFilesDir(null), "sounds");
        dir.mkdirs();
    }
    public File getDir() { return dir; }

    public List<Clip> list() {
        File[] files = dir.listFiles((d,n) -> n.toLowerCase(Locale.US).endsWith(".wav"));
        if (files == null) return Collections.emptyList();
        Arrays.sort(files, (a,b) -> a.getName().compareToIgnoreCase(b.getName()));
        List<Clip> out = new ArrayList<>();
        for (File f : files) {
            try {
                float[] s = WavUtil.readWav(f, sr);
                String name = f.getName().replaceFirst("(?i)\\.wav$", "");
                out.add(new Clip(name, f, s.length/(float)sr));
            } catch (Exception ignored) {}
        }
        return out;
    }

    public Clip importWav(File src, String desiredName) throws Exception {
        String base = safe(desiredName);
        File dst = unique(base);
        float[] samples = WavUtil.readWav(src, sr);
        if (samples.length > sr * 60) samples = Arrays.copyOf(samples, sr * 60);
        WavUtil.writeWav(dst, samples, sr);
        return new Clip(dst.getName().replaceFirst("(?i)\\.wav$",""), dst, samples.length/(float)sr);
    }

    public void play(final Clip clip, final Runnable onDone) {
        new Thread(() -> {
            AudioTrack track = null;
            try {
                float[] s = WavUtil.readWav(clip.file, sr);
                int buf = Math.max(AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096);
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(buf).setTransferMode(AudioTrack.MODE_STREAM).build();
                track.play();
                short[] pcm = new short[2048];
                int i = 0;
                while (i < s.length) {
                    int n = Math.min(pcm.length, s.length - i);
                    for (int j=0;j<n;j++) { float c=Math.max(-1f,Math.min(1f,s[i+j])); pcm[j]=(short)(c*32767); }
                    track.write(pcm, 0, n);
                    i += n;
                }
                try { track.stop(); } catch (Exception ignored) {}
            } catch (Exception ignored) {}
            finally { try { if (track!=null) track.release(); } catch(Exception ignored) {} if (onDone!=null) onDone.run(); }
        }, "sb-play").start();
    }

    public boolean delete(Clip c) { return c.file.delete(); }
    public Clip rename(Clip c, String newName) throws Exception {
        File dst = unique(safe(newName));
        if (!c.file.renameTo(dst)) throw new IOException("Rename failed");
        return new Clip(dst.getName().replaceFirst("(?i)\\.wav$",""), dst, c.seconds);
    }
    private String safe(String n){ String s=n.replaceAll("[\\\\/:*?\"<>|]", "").trim(); if(s.length()>40) s=s.substring(0,40); if(s.isEmpty()) s="clip"; return s; }
    private File unique(String base){ File f=new File(dir, base+".wav"); int n=2; while(f.exists()){ f=new File(dir, base+" "+n+".wav"); n++; } return f; }
}
