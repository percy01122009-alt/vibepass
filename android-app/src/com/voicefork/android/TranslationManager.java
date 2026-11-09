package com.voicefork.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TranslationManager implements RecognitionListener {
    public interface Callback {
        void onRecognized(String text);
        void onTranslated(String fromLang, String toLang, String text);
        void onTtsStarted();
        void onTtsDone();
        void onError(String msg);
        void onStatus(String status);
    }

    private final Activity act;
    private final Callback cb;
    private TextToSpeech tts;
    private SpeechRecognizer speech;
    private String targetLang = "de";
    private String sourceLang = "auto";
    private boolean listening = false;
    private boolean translating = false;
    private static final String TAG = "VF-Translate";

    public TranslationManager(Activity act, Callback cb) {
        this.act = act;
        this.cb = cb;
        try {
            initTts();
        } catch (Exception e) {
            Log.e(TAG, "TTS init failed", e);
        }
        try {
            if (SpeechRecognizer.isRecognitionAvailable(act)) {
                speech = SpeechRecognizer.createSpeechRecognizer(act);
                speech.setRecognitionListener(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "SpeechRecognizer init failed", e);
            speech = null;
        }
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(act, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.forLanguageTag(targetLang));
                    tts.setPitch(1.0f);
                    tts.setSpeechRate(0.95f);
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) { cb.onTtsStarted(); }
                        @Override public void onDone(String utteranceId) { cb.onTtsDone(); }
                        @Override public void onError(String utteranceId) { cb.onError("TTS error"); }
                    });
                } else {
                    cb.onError("TTS init failed");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "TextToSpeech constructor failed", e);
            tts = null;
        }
    }

    public void setTargetLang(String lang) { targetLang = lang; if (tts != null) tts.setLanguage(Locale.forLanguageTag(lang)); }
    public void setSourceLang(String lang) { sourceLang = lang; }
    public String getTargetLang() { return targetLang; }

    public void startListening() {
        if (speech == null) { cb.onError("Speech recognition not available on this device."); return; }
        if (listening) return;
        listening = true;
        cb.onStatus("Listening...");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000);
        speech.startListening(intent);
    }

    public void stopListening() {
        if (speech != null) speech.stopListening();
        listening = false;
    }

    public void translateAndSpeak(String text) {
        if (translating) return;
        translating = true;
        cb.onStatus("Translating...");
        new Thread(() -> {
            try {
                String translated = translate(text, sourceLang, targetLang);
                translating = false;
                if (translated != null && !translated.isEmpty()) {
                    cb.onTranslated(sourceLang, targetLang, translated);
                    speak(translated);
                } else {
                    translating = false;
                    cb.onError("Translation returned empty");
                }
            } catch (Exception e) {
                translating = false;
                cb.onError("Translation failed: " + e.getMessage());
            }
        }, "vf-translate").start();
    }

    private String translate(String text, String from, String to) {
        try {
            String url = "https://libretranslate.de/translate";
            String body = "{\"q\":\"" + escapeJson(text) + "\",\"source\":\"" + from + "\",\"target\":\"" + to + "\"}";
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setConnectTimeout(2000);
            c.setReadTimeout(4000);
            try (java.io.OutputStream os = c.getOutputStream()) { os.write(body.getBytes("UTF-8")); }
            if (c.getResponseCode() >= 200 && c.getResponseCode() < 300) {
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()))) {
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    String resp = sb.toString();
                    int i = resp.indexOf("\"translatedText\"");
                    if (i > 0) {
                        int s = resp.indexOf("\"", i + 17) + 1;
                        int e = resp.indexOf("\"", s);
                        if (e > s) return resp.substring(s, e);
                    }
                }
            }
        } catch (Exception ignored) {}
        return text;
    }

    private void speak(String text) {
        if (tts == null) return;
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vf-" + System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "TTS speak failed", e);
        }
    }

    public void stop() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speech != null) speech.destroy();
        listening = false; translating = false;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    @Override public void onReadyForSpeech(Bundle params) { cb.onStatus("Listening..."); }
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { cb.onStatus("Translating..."); }
    @Override public void onError(int error) {
        listening = false;
        cb.onError("Speech recognition error: " + error);
    }
    @Override public void onResults(Bundle results) {
        listening = false;
        if (results != null && results.containsKey("results_recognized")) {
            List<String> matches = results.getStringArrayList("results_recognized");
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                cb.onRecognized(text);
                translateAndSpeak(text);
                return;
            }
        }
        cb.onError("No speech detected.");
    }
    @Override public void onPartialResults(Bundle results) {
        if (results != null && results.containsKey("results_recognized")) {
            List<String> matches = results.getStringArrayList("results_recognized");
            if (matches != null && !matches.isEmpty()) {
                cb.onStatus("Heard: " + matches.get(0));
            }
        }
    }
    @Override public void onEvent(int eventType, Bundle params) {}
}
