package com.voicefork.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;
import android.graphics.Color;

import com.voicefork.android.audio.AudioEngine;
import com.voicefork.android.audio.WavUtil;
import com.voicefork.android.dsp.Presets;
import com.voicefork.android.soundboard.SoundboardManager;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements AudioEngine.Listener {
    private static final int REQ_PERMS = 1, REQ_IMPORT_SB = 2, REQ_IMPORT_MODEL = 3;
    private AudioEngine engine;
    private SoundboardManager soundboard;
    private TextView status, presetDesc, gateVal, gainVal, blendMixVal, aiText, aiModelStatus, makeText, aboutText;
    private ProgressBar levelIn;
    private SeekBar gateSeek, gainSeek, blendMix;
    private Button btnLive, btnRecord, btnPlayLast, btnShareLast, sbRecordBtn;
    private GridLayout presetGrid;
    private LinearLayout sbList;
    private Spinner blendA, blendB;
    private TranslationManager transManager;
    private TextView trStatus, trRecognized, trTranslated;
    private Spinner trLang;
    private Button trStart, trStop;
    private final View[] pages = new View[7];
    private final Button[] tabBtns = new Button[7];
    private String selectedPreset = "clean";
    private File lastOutput;
    private boolean sbRecActive = false;
    private AudioEngine sbRecorder; // reuse engine? easier separate simple rec

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { StrictMode.VmPolicy.Builder b = new StrictMode.VmPolicy.Builder(); StrictMode.setVmPolicy(b.build()); } catch (Exception ignored) {}
        setContentView(R.layout.activity_main);
        engine = new AudioEngine(this);
        soundboard = new SoundboardManager(this, AudioEngine.SR);
        bind();
        setupTabs();
        setupEffects();
        setupSoundboard();
        setupAiBlendMake();
        setupTranslate();
        requestPerms();
    }

    private void bind() {
        status = findViewById(R.id.status);
        pages[0]=findViewById(R.id.pageEffects); pages[1]=findViewById(R.id.pageSoundboard); pages[2]=findViewById(R.id.pageAi);
        pages[3]= findViewById(R.id.pageBlend); pages[4]= findViewById(R.id.pageMake); pages[5]= findViewById(R.id.pageAbout); pages[6]= findViewById(R.id.pageTranslate);
        tabBtns[0]=findViewById(R.id.tabEffects); tabBtns[1]=findViewById(R.id.tabSoundboard); tabBtns[2]=findViewById(R.id.tabAi);
        tabBtns[3]= findViewById(R.id.tabBlend); tabBtns[4]= findViewById(R.id.tabMake); tabBtns[5]= findViewById(R.id.tabAbout); tabBtns[6]= findViewById(R.id.tabTranslate);
        gateSeek = findViewById(R.id.gateSeek); gainSeek = findViewById(R.id.gainSeek);
        gateVal = findViewById(R.id.gateVal); gainVal = findViewById(R.id.gainVal);
        btnLive = findViewById(R.id.btnLive); btnRecord = findViewById(R.id.btnRecord);
        btnPlayLast = findViewById(R.id.btnPlayLast); btnShareLast = findViewById(R.id.btnShareLast);
        levelIn = findViewById(R.id.levelIn);
        sbList = findViewById(R.id.sbList); sbRecordBtn = findViewById(R.id.sbRecord);
        blendA = findViewById(R.id.blendA); blendB = findViewById(R.id.blendB); blendMix = findViewById(R.id.blendMix); blendMixVal = findViewById(R.id.blendMixVal);
        aiText = findViewById(R.id.aiText); aiModelStatus = findViewById(R.id.aiModelStatus);
        makeText = findViewById(R.id.makeText); aboutText = findViewById(R.id.aboutText);
        trStatus = findViewById(R.id.trStatus); trRecognized = findViewById(R.id.trRecognized); trTranslated = findViewById(R.id.trTranslated);
        trLang = findViewById(R.id.trLang); trStart = findViewById(R.id.trStart); trStop = findViewById(R.id.trStop);
    }

    private void setupTabs() {
        for (int i=0;i<tabBtns.length;i++) { final int idx=i; tabBtns[i].setOnClickListener(v -> showTab(idx)); }
        showTab(0);
    }
    private void showTab(int idx) {
        for (int i=0;i<pages.length;i++) {
            pages[i].setVisibility(i==idx?View.VISIBLE:View.GONE);
            tabBtns[i].setTextColor(getResources().getColor(i==idx?R.color.ink:R.color.muted));
        }
        if (idx==1) refreshSoundboard();
    }

    private void setupTranslate() {
        transManager = new TranslationManager(this, new TranslationManager.Callback() {
            @Override public void onRecognized(String text) { runOnUiThread(() -> trRecognized.setText("Heard: " + text)); }
            @Override public void onTranslated(String from, String to, String text) { runOnUiThread(() -> trTranslated.setText("\u2192 " + text)); }
            @Override public void onTtsStarted() { runOnUiThread(() -> trStatus.setText("Speaking...")); }
            @Override public void onTtsDone() { runOnUiThread(() -> trStatus.setText("Tap Start to speak.")); }
            @Override public void onError(String msg) { runOnUiThread(() -> { trStatus.setText("Error"); toast(msg); }); }
            @Override public void onStatus(String status) { runOnUiThread(() -> trStatus.setText(status)); }
        });
        String[] langs = {"Deutsch","Französisch","Spanisch","Italienisch","Portugiesisch","Niederländisch","Russisch","Japanisch","Koreanisch","Chinesisch","Arabisch","Hindi","Türkisch","Schwedisch","Norwegisch","Dänisch","Finnisch","Polnisch","Tschechisch","Griechisch"};
        String[] codes = {"de","fr","es","it","pt","nl","ru","ja","ko","zh","ar","hi","tr","sv","no","da","fi","pl","cs","el"};
        android.widget.ArrayAdapter<String> la = new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new java.util.ArrayList<>(java.util.Arrays.asList(langs)));
        la.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        trLang.setAdapter(la);
        trLang.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View v, int pos, long id) { transManager.setTargetLang(codes[pos]); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        transManager.setTargetLang(codes[0]);
        trStart.setOnClickListener(v -> { if (!hasRecordPerm()) { requestPerms(); return; } transManager.startListening(); });
        trStop.setOnClickListener(v -> transManager.stopListening());
    }

    private void setupEffects() {
        List<Presets.Info> presets = Presets.list();
        presetGrid.removeAllViews();
        for (Presets.Info p : presets) {
            Button b = new Button(this);
            b.setText(p.name);
            b.setAllCaps(false);
            b.setTextColor(getResources().getColor(R.color.ink));
            b.setBackgroundColor(getResources().getColor(p.key.equals(selectedPreset)?R.color.accent:R.color.raised));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0; lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(6,6,6,6); b.setLayoutParams(lp);
            b.setOnClickListener(v -> selectPreset(p.key));
            b.setTag(p.key);
            presetGrid.addView(b);
        }
        updatePresetDesc();
        gateSeek.setOnSeekBarChangeListener(simpleSeek(v -> { float db = v - 120; gateVal.setText(((int)db)+" dB"); engine.setGateDb(db); }));
        gainSeek.setOnSeekBarChangeListener(simpleSeek(v -> { float db = v - 20; gainVal.setText(((int)db)+" dB"); engine.setGainDb(db); }));
        gateSeek.setProgress(70); gainSeek.setProgress(20);
        engine.setGateDb(-50f); engine.setGainDb(0f);

        btnLive.setOnClickListener(v -> {
            if (!hasRecordPerm()) { requestPerms(); return; }
            if (engine.isLive()) { engine.stopLive(); btnLive.setText("Live preview"); }
            else { engine.startLive(); btnLive.setText("Stop live"); }
        });
        btnRecord.setOnClickListener(v -> {
            if (!hasRecordPerm()) { requestPerms(); return; }
            if (engine.isLive()) { toast("Stop live preview before recording."); return; }
            if (engine.isRecording()) {
                File out = newOutput("voicefork-" + selectedPreset);
                engine.stopRecordingAndProcess(out);
                btnRecord.setText("Record & Apply");
            } else {
                engine.startRecording();
                btnRecord.setText("Stop & Apply (" + Presets.get(selectedPreset).name + ")");
            }
        });
        btnPlayLast.setOnClickListener(v -> playLast());
        btnShareLast.setOnClickListener(v -> shareLast());
    }

    private void selectPreset(String key) {
        selectedPreset = key;
        engine.setPreset(key);
        for (int i=0;i<presetGrid.getChildCount();i++) {
            View c = presetGrid.getChildAt(i);
            if (c instanceof Button) c.setBackgroundColor(getResources().getColor(key.equals(c.getTag())?R.color.accent:R.color.raised));
        }
        updatePresetDesc();
    }
    private void updatePresetDesc() { presetDesc.setText(Presets.get(selectedPreset).description + "  •  11 presets, noise gate, gain & limiter — like Voicefork effects."); }

    private void setupSoundboard() {
        findViewById(R.id.sbImport).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("audio/*");
            startActivityForResult(i, REQ_IMPORT_SB);
        });
        sbRecordBtn.setOnClickListener(v -> {
            if (!hasRecordPerm()) { requestPerms(); return; }
            toast("Soundboard clip recording: use Record & Apply on Effects tab and save the WAV, then import it here. (Dedicated clip recorder simplified in this build.)");
        });
        refreshSoundboard();
    }
    private void refreshSoundboard() {
        sbList.removeAllViews();
        List<SoundboardManager.Clip> clips = soundboard.list();
        if (clips.isEmpty()) {
            TextView t = new TextView(this); t.setTextColor(getResources().getColor(R.color.muted));
            t.setText("No clips yet. Import a WAV/audio file."); sbList.addView(t); return;
        }
        for (SoundboardManager.Clip c : clips) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0,8,0,8);
            TextView name = new TextView(this); name.setText(c.name + "  (" + String.format(Locale.US,"%.1f", c.seconds) + " s)");
            name.setTextColor(getResources().getColor(R.color.ink)); name.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
            Button play = new Button(this); play.setText("Play"); play.setAllCaps(false);
            play.setOnClickListener(v -> { play.setEnabled(false); soundboard.play(c, () -> runOnUiThread(() -> play.setEnabled(true))); });
            Button del = new Button(this); del.setText("Delete"); del.setAllCaps(false);
            del.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete clip?").setMessage(c.name)
                    .setPositiveButton("Delete", (d,w)->{ soundboard.delete(c); refreshSoundboard(); }).setNegativeButton("Cancel", null).show());
            row.addView(name); row.addView(play); row.addView(del); sbList.addView(row);
        }
    }

    private void setupAiBlendMake() {
        aiText.setText("Voicefork's desktop AI voices use RVC (HuBERT + RMVPE/FCPE + voice models) for real-time conversion. " +
                "Full on-device RVC inference/training is not bundled in this Android build because it requires large ML runtimes and model conversion.\n\n" +
                "What's included here: the complete Voicefork-style DSP effect engine (11 presets), soundboard, recording/apply/share, and blend workflows. " +
                "You can browse community RVC voices on Hugging Face and import model files for future/advanced integration.");
        makeText.setText("Voicefork desktop can train a custom RVC voice from ~10 minutes of recordings. " +
                "On-device RVC training is not practical in this Android build. You can record clean WAV samples here and use them with the desktop Voicefork trainer, " +
                "or with compatible RVC tools, to create a voice model.\n\nResponsible use: only clone/use voices you have permission to use. Don't impersonate, deceive, or harass.");
        aboutText.setText("Voicefork Android — a mobile, downloadable APK inspired by Voicefork (https://github.com/dank-zz/voicefork).\n\n" +
                "Fully working on Android: 11 DSP presets (Clean, Deep, Chipmunk, Demon, Giant, Robot, Alien, Radio, Megaphone, Cave, 8-bit), " +
                "noise gate, gain, limiter, live mic preview (headphones recommended), record & apply, WAV export/share, soundboard import/play, and DSP blend.\n\n" +
                "Android-adapted/honest: desktop Voicefork's real-time RVC AI voices, HuggingFace in-app model workflow, on-device training, system virtual-mic routing (VB-CABLE), and global hotkeys depend on desktop/PyTorch/GPU capabilities and are not fully replicated here.\n\n" +
                "Privacy: audio is processed on-device. No account required. Use voices responsibly.\n\nCredits: inspired by Voicefork (MIT). DSP design ported from Voicefork's effects/presets.");
        findViewById(R.id.btnBrowseVoices).setOnClickListener(v -> openUrl("https://huggingface.co/models?search=rvc"));
        findViewById(R.id.btnImportModel).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT_MODEL);
        });
        findViewById(R.id.btnRecordSample).setOnClickListener(v -> { showTab(0); toast("Use Record & Apply on the Effects tab with Clean preset to export a clean sample."); });
        findViewById(R.id.btnOpenSource).setOnClickListener(v -> openUrl("https://github.com/dank-zz/voicefork"));

        // Blend
        List<String> names = new ArrayList<>();
        for (Presets.Info p : Presets.list()) names.add(p.name + "|" + p.key);
        ArrayAdapter<String> aa = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, toDisplay(names)) {
            @Override public View getView(int p, View c, android.view.ViewGroup parent){ View v=super.getView(p,c,parent); ((TextView)v).setTextColor(getResources().getColor(R.color.ink)); return v; }
            @Override public View getDropDownView(int p, View c, android.view.ViewGroup parent){ View v=super.getDropDownView(p,c,parent); ((TextView)v).setTextColor(getResources().getColor(R.color.ink)); v.setBackgroundColor(getResources().getColor(R.color.raised)); return v; }
        };
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        blendA.setAdapter(aa); blendB.setAdapter(aa);
        if (names.size()>1) blendB.setSelection(1);
        blendMix.setOnSeekBarChangeListener(simpleSeek(v -> blendMixVal.setText(v+"%")));
        blendMixVal.setText("50%");
        findViewById(R.id.btnBlendPreview).setOnClickListener(v -> doBlendRecord());
    }

    private List<String> toDisplay(List<String> nk){ List<String> o=new ArrayList<>(); for(String s:nk) o.add(s.split("\\|")[0]); return o; }
    private String keyAt(Spinner sp){ String disp=String.valueOf(sp.getSelectedItem()); for(Presets.Info p:Presets.list()) if(p.name.equals(disp)) return p.key; return "clean"; }

    private void doBlendRecord() {
        if (!hasRecordPerm()) { requestPerms(); return; }
        if (engine.isLive() || engine.isRecording()) { toast("Stop current recording/live first."); return; }
        final String a = keyAt(blendA), b = keyAt(blendB);
        final int mix = blendMix.getProgress();
        toast("Recording 5 seconds for blend… speak now.");
        // record raw then process blend
        new Thread(() -> {
            android.media.AudioRecord rec = null;
            try {
                int buf = Math.max(android.media.AudioRecord.getMinBufferSize(AudioEngine.SR, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT), 4096)*2;
                rec = new android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, AudioEngine.SR, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, buf);
                if (rec.getState()!=android.media.AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Mic unavailable.");
                rec.startRecording();
                runOnUiThread(() -> status.setText("Recording blend…"));
                int total = AudioEngine.SR * 5; short[] pcm = new short[2048]; float[] raw = new float[total]; int got=0;
                while (got < total) { int n=rec.read(pcm,0,Math.min(pcm.length,total-got)); if(n<=0) continue; for(int i=0;i<n;i++) raw[got+i]=pcm[i]/32768f; got+=n; }
                runOnUiThread(() -> status.setText("Applying blend…"));
                float gate = gateSeek.getProgress()-120, gain=gainSeek.getProgress()-20;
                float[] ya = AudioEngine.applyToSamples(java.util.Arrays.copyOf(raw,got), a, gate, gain);
                float[] yb = AudioEngine.applyToSamples(java.util.Arrays.copyOf(raw,got), b, gate, gain);
                float mb = mix/100f, ma=1f-mb;
                float[] y = new float[ya.length]; for(int i=0;i<y.length;i++){ float v=ma*ya[i]+mb*(i<yb.length?yb[i]:0f); y[i]=Math.max(-1f,Math.min(1f,v)); }
                File out = newOutput("voicefork-blend-"+a+"-"+b);
                WavUtil.writeWav(out, y, AudioEngine.SR);
                lastOutput = out;
                runOnUiThread(() -> { status.setText("Blend saved"); toast("Saved: " + out.getName()); onRecordingSaved(out); });
            } catch (Exception e) { runOnUiThread(() -> { status.setText("Ready"); toast("Blend failed: " + e.getMessage()); }); }
            finally { try { if(rec!=null){rec.stop();rec.release();} } catch(Exception ignored){} }
        }, "vf-blend").start();
    }

    private File newOutput(String base) {
        File dir = new File(getExternalFilesDir(null), "exports"); dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return new File(dir, base + "-" + ts + ".wav");
    }

    private void playLast() {
        if (lastOutput == null || !lastOutput.exists()) { toast("No processed recording yet. Use Record & Apply first."); return; }
        btnPlayLast.setEnabled(false);
        soundboard.play(new SoundboardManager.Clip("last", lastOutput, 0), () -> runOnUiThread(() -> btnPlayLast.setEnabled(true)));
    }
    private void shareLast() {
        if (lastOutput == null || !lastOutput.exists()) { toast("No processed recording yet."); return; }
        try {
            Uri uri = Uri.fromFile(lastOutput);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("audio/wav");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, "Voicefork Android export");
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Save/Share voice recording"));
        } catch (Exception e) { toast("Share failed: " + e.getMessage() + "\nFile: " + lastOutput.getAbsolutePath()); }
    }

    private void openUrl(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { toast(url); } }

    private boolean hasRecordPerm() { return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }
    private void requestPerms() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> need = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_MEDIA_AUDIO);
            } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_PERMS) {
            boolean mic = hasRecordPerm();
            if (!mic) toast("Microphone permission is required for voice changing.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData(); if (uri == null) return;
        if (requestCode == REQ_IMPORT_SB) {
            try {
                File tmp = copyUriToTemp(uri, "import.wav");
                String name = queryName(uri); if (name==null) name="clip";
                name = name.replaceFirst("(?i)\\.[^.]+$", "");
                soundboard.importWav(tmp, name);
                tmp.delete();
                refreshSoundboard(); toast("Imported clip.");
            } catch (Exception e) { toast("Import failed (use WAV for best results): " + e.getMessage()); }
        } else if (requestCode == REQ_IMPORT_MODEL) {
            String name = queryName(uri);
            aiModelStatus.setText("Selected model file: " + (name!=null?name:uri.toString()) + "\n\nThis build stores/selects model files but does not run real-time RVC inference on-device. Desktop Voicefork or an ONNX/TFLite RVC pipeline is required for full AI voice conversion.");
            toast("Model file selected (AI inference not bundled in this build).");
        }
    }

    private File copyUriToTemp(Uri uri, String fallback) throws Exception {
        File out = new File(getCacheDir(), fallback);
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(out)) {
            if (in==null) throw new IOException("Cannot open");
            byte[] buf = new byte[8192]; int r; while((r=in.read(buf))>0) fos.write(buf,0,r);
        }
        return out;
    }
    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c!=null && c.moveToFirst()) { int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i>=0) return c.getString(i); }
        } catch (Exception ignored) {}
        return null;
    }

    // AudioEngine.Listener
    @Override public void onState(String s) { status.setText(s); if (!"Recording…".equals(s) && !s.startsWith("Applying") && !s.startsWith("Recording blend")) { if (!engine.isRecording()) btnRecord.setText("Record & Apply"); if (!engine.isLive()) btnLive.setText("Live preview"); } }
    @Override public void onLevel(float inPeak, float outPeak) { levelIn.setProgress(Math.min(100, (int)(inPeak * 180))); }
    @Override public void onError(String msg) { toast(msg); }
    @Override public void onRecordingSaved(File wav) { lastOutput = wav; toast("Saved: " + wav.getAbsolutePath()); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private interface IntFn { void on(int v); }
    private SeekBar.OnSeekBarChangeListener simpleSeek(IntFn fn) {
        return new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f){ fn.on(p); }
            public void onStartTrackingTouch(SeekBar sb){} public void onStopTrackingTouch(SeekBar sb){}
        };
    }

    @Override protected void onDestroy() { super.onDestroy(); if (engine!=null) engine.stopLive(); if (transManager!=null) transManager.stop(); }
}
