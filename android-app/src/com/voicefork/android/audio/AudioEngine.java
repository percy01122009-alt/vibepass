package com.voicefork.android.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import com.voicefork.android.dsp.Chain;
import com.voicefork.android.dsp.Presets;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AudioEngine {
    public interface Listener {
        void onState(String state);
        void onLevel(float inPeak, float outPeak);
        void onError(String msg);
        void onRecordingSaved(File wav);
    }

    public static final int SR = 48000;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean liveRunning = new AtomicBoolean(false);
    private final AtomicBoolean recordArmed = new AtomicBoolean(false);
    private final AtomicReference<Chain> chainRef = new AtomicReference<>();
    private Thread liveThread, recordThread;
    private volatile String preset = "clean";
    private volatile float gateDb = -50f, gainDb = 0f;
    private File recordOutput;

    public AudioEngine(Listener listener) {
        this.listener = listener;
        rebuildChain();
    }

    public synchronized void setPreset(String name) { preset = name; rebuildChain(); }
    public synchronized void setGateDb(float db) { gateDb = db; rebuildChain(); }
    public synchronized void setGainDb(float db) { gainDb = db; rebuildChain(); }
    public String getPreset() { return preset; }
    private void rebuildChain() { chainRef.set(Presets.buildChain(preset, SR, gateDb, gainDb)); }

    public boolean isLive() { return liveRunning.get(); }

    public void startLive() {
        if (liveRunning.get()) return;
        liveRunning.set(true);
        rebuildChain();
        liveThread = new Thread(this::liveLoop, "vf-live");
        liveThread.start();
    }
    public void stopLive() { liveRunning.set(false); }

    public void recordAndApply(File output) {
        if (recordThread != null && recordThread.isAlive()) return;
        recordOutput = output;
        rebuildChain();
        recordThread = new Thread(this::recordLoop, "vf-record");
        recordThread.start();
    }

    private void liveLoop() {
        AudioRecord rec = null; AudioTrack track = null;
        try {
            int inBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int outBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int buf = Math.max(inBuf, outBuf) * 2;
            int samples = Math.max(256, buf / 4);
            rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(buf).setTransferMode(AudioTrack.MODE_STREAM).build();
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Mic unavailable. Check microphone permission.");
            rec.startRecording(); track.play();
            postState("Live");
            short[] pcm = new short[samples];
            float[] fin = new float[samples];
            short[] outPcm = new short[samples];
            long lastLevel = 0;
            while (liveRunning.get()) {
                int n = rec.read(pcm, 0, pcm.length);
                if (n <= 0) continue;
                float inPeak = 0f;
                for (int i = 0; i < n; i++) { fin[i] = pcm[i] / 32768f; float a = Math.abs(fin[i]); if (a > inPeak) inPeak = a; }
                float[] x = n == fin.length ? fin : java.util.Arrays.copyOf(fin, n);
                float[] y = chainRef.get().process(x);
                float outPeak = 0f;
                if (outPcm.length < y.length) outPcm = new short[y.length];
                for (int i = 0; i < y.length; i++) {
                    float c = Math.max(-1f, Math.min(1f, y[i]));
                    float a = Math.abs(c); if (a > outPeak) outPeak = a;
                    outPcm[i] = (short)(c * 32767);
                }
                track.write(outPcm, 0, y.length);
                long now = System.currentTimeMillis();
                if (now - lastLevel > 80) { lastLevel = now; float ip=inPeak, op=outPeak; main.post(() -> listener.onLevel(ip, op)); }
            }
        } catch (Exception e) {
            postError(e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            try { if (rec != null) { rec.stop(); rec.release(); } } catch (Exception ignored) {}
            try { if (track != null) { track.stop(); track.release(); } } catch (Exception ignored) {}
            liveRunning.set(false);
            main.post(() -> listener.onLevel(0,0));
            postState("Ready");
        }
    }

    private void recordLoop() {
        AudioRecord rec = null;
        try {
            int buf = Math.max(AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), SR / 2) * 2;
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf);
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Mic unavailable.");
            postState("Recording… tap Stop to finish");
            rec.startRecording();
            java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream(SR * 4);
            short[] pcm = new short[2048];
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
                int n = rec.read(pcm, 0, pcm.length);
                if (n <= 0) continue;
                float peak = 0f;
                for (int i = 0; i < n; i++) { raw.write(pcm[i] & 0xFF); raw.write((pcm[i] >> 8) & 0xFF); float a=Math.abs(pcm[i]/32768f); if(a>peak) peak=a; }
                float p=peak; main.post(() -> listener.onLevel(p, 0));
                // stop flag via interruption handled by caller; also we use a simple approach: UI calls stopRecord()
            }
            // not reached in this simple design
        } catch (Exception e) {
            postError(e.getMessage());
        } finally {
            try { if (rec != null) { rec.stop(); rec.release(); } } catch (Exception ignored) {}
            postState("Ready");
        }
    }

    // Better record flow: start, collect, stop+process
    private final AtomicBoolean recActive = new AtomicBoolean(false);
    private java.io.ByteArrayOutputStream recBuf;
    private Thread recThread;
    public void startRecording() {
        if (recActive.get() || liveRunning.get()) return;
        recActive.set(true); recBuf = new java.io.ByteArrayOutputStream(SR * 4);
        recThread = new Thread(() -> {
            AudioRecord rec = null;
            try {
                int buf = Math.max(AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096) * 2;
                rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf);
                if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Mic unavailable. Grant microphone permission.");
                rec.startRecording(); postState("Recording…");
                short[] pcm = new short[2048];
                while (recActive.get()) {
                    int n = rec.read(pcm, 0, pcm.length);
                    if (n <= 0) continue;
                    float peak = 0f;
                    synchronized (recBuf) { for (int i=0;i<n;i++){ recBuf.write(pcm[i]&0xFF); recBuf.write((pcm[i]>>8)&0xFF); float a=Math.abs(pcm[i]/32768f); if(a>peak) peak=a; } }
                    float p=peak; main.post(() -> listener.onLevel(p,0));
                }
            } catch (Exception e) { postError(e.getMessage()); recActive.set(false); }
            finally { try { if (rec!=null){rec.stop();rec.release();} } catch(Exception ignored){} }
        }, "vf-rec");
        recThread.start();
    }
    public boolean isRecording() { return recActive.get(); }
    public void stopRecordingAndProcess(File output) {
        if (!recActive.get()) return;
        recActive.set(false);
        File out = output;
        new Thread(() -> {
            try { if (recThread!=null) recThread.join(1500); } catch (Exception ignored) {}
            try {
                byte[] bytes;
                synchronized (recBuf) { bytes = recBuf.toByteArray(); }
                if (bytes.length < 100) { postError("Recording was too short."); postState("Ready"); return; }
                int n = bytes.length / 2; float[] fin = new float[n];
                for (int i=0;i<n;i++) { int lo = bytes[i*2]&0xFF; int hi = bytes[i*2+1]; short s = (short)((hi<<8)|lo); fin[i]=s/32768f; }
                postState("Applying " + Presets.get(preset).name + "…");
                rebuildChain();
                float[] y = chainRef.get().process(fin);
                WavUtil.writeWav(out, y, SR);
                postState("Saved");
                File f = out; main.post(() -> listener.onRecordingSaved(f));
            } catch (Exception e) { postError("Save failed: " + e.getMessage()); postState("Ready"); }
        }, "vf-process").start();
    }

    public static float[] applyToSamples(float[] in, String preset, float gateDb, float gainDb) {
        Chain c = Presets.buildChain(preset, SR, gateDb, gainDb);
        return c.process(in);
    }

    private void postState(String s) { main.post(() -> listener.onState(s)); }
    private void postError(String s) { main.post(() -> { listener.onError(s); listener.onState("Ready"); }); }
}
