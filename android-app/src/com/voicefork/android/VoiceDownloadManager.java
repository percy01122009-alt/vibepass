package com.voicefork.android;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class VoiceDownloadManager {
    public static class Voice {
        public final String name, repo, path, url, description;
        public Voice(String n, String r, String p, String u, String d) {
            name=n; repo=r; path=p; url=u; description=d;
        }
    }

    public interface Callback {
        void onDownloadStart(String name);
        void onDownloadProgress(int percent);
        void onDownloadComplete(Voice v, File file);
        void onError(String msg);
    }

    private final Context ctx;
    private final Callback cb;
    private static final String TAG = "VF-VoiceDL";

    // Curated RVC voice models from Hugging Face (community voices)
    public static final List<Voice> KNOWN_VOICES = new ArrayList<>();
    static {
        KNOWN_VOICES.add(new Voice("Nekochu RVC VCTK Sample 1", "Nekochu/RVC-VCTK_Voice-sample", "female-1.pth", "https://huggingface.co/Nekochu/RVC-VCTK_Voice-sample/resolve/main/female-1.pth", "Free starter female voice"));
        KNOWN_VOICES.add(new Voice("Nekochu RVC VCTK Sample 2", "Nekochu/RVC-VCTK_Voice-sample", "male-1.pth", "https://huggingface.co/Nekochu/RVC-VCTK_Voice-sample/resolve/main/male-1.pth", "Free starter male voice"));
        KNOWN_VOICES.add(new Voice("Nekochu RVC VCTK Sample 3", "Nekochu/RVC-VCTK_Voice-sample", "female-2.pth", "https://huggingface.co/Nekochu/RVC-VCTK_Voice-sample/resolve/main/female-2.pth", "Free starter female voice 2"));
    }

    public VoiceDownloadManager(Context ctx, Callback cb) { this.ctx = ctx; this.cb = cb; }

    public List<Voice> listVoices() { return new ArrayList<>(KNOWN_VOICES); }

    public void downloadVoice(Voice voice) {
        cb.onDownloadStart(voice.name);
        new Thread(() -> {
            try {
                File dir = new File(ctx.getExternalFilesDir(null), "voices");
                dir.mkdirs();
                File out = new File(dir, voice.name.replaceAll("[^a-zA-Z0-9._-]", "_") + ".pth");
                URL url = new URL(voice.url);
                URLConnection c = url.openConnection();
                int total = c.getContentLength();
                try (InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int read, done = 0;
                    while ((read = in.read(buf)) > 0) {
                        fos.write(buf, 0, read);
                        done += read;
                        if (total > 0) {
                            int pct = (int)(done * 100f / total);
                            final int p = pct;
                            new android.os.Handler(ctx.getMainLooper()).post(() -> cb.onDownloadProgress(p));
                        }
                    }
                }
                new android.os.Handler(ctx.getMainLooper()).post(() -> cb.onDownloadComplete(voice, out));
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                new android.os.Handler(ctx.getMainLooper()).post(() -> cb.onError("Download failed: " + e.getMessage()));
            }
        }, "vf-voicedl").start();
    }

    public List<File> listDownloaded() {
        File dir = new File(ctx.getExternalFilesDir(null), "voices");
        if (!dir.exists()) return new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".pth") || n.endsWith(".onnx"));
        List<File> out = new ArrayList<>();
        if (files != null) for (File f : files) out.add(f);
        return out;
    }
}
