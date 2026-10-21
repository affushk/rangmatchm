package com.altafshaikh.nooredua;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Window;
import android.view.WindowManager;

import java.util.Locale;
import java.util.UUID;
import java.util.Set;

public class MainActivity extends Activity {
    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String voiceMode = "qari";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 16, 13));
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        webView.addJavascriptInterface(new NoorBridge(this), "NoorNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("file".equalsIgnoreCase(scheme)) return false;
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });

        initTts();
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("ar", "SA"));
                applyVoiceProfile();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) { speechDone(); }
                    @Override public void onError(String utteranceId) { speechDone(); }
                });
            }
        });
    }

    private void applyVoiceProfile() {
        if (tts == null) return;
        try {
            Voice best = null;
            int bestScore = Integer.MIN_VALUE;
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                for (Voice v : voices) {
                    Locale loc = v.getLocale();
                    if (loc == null || !"ar".equalsIgnoreCase(loc.getLanguage())) continue;
                    int score = v.getQuality() * 10;
                    String country = loc.getCountry();
                    if ("SA".equalsIgnoreCase(country)) score += 140;
                    else if ("AE".equalsIgnoreCase(country) || "QA".equalsIgnoreCase(country)) score += 90;
                    else if ("EG".equalsIgnoreCase(country)) score += 60;
                    if (v.isNetworkConnectionRequired()) score += 22;
                    String name = v.getName() == null ? "" : v.getName().toLowerCase(Locale.US);
                    if (name.contains("male")) score += 35;
                    if (name.contains("premium") || name.contains("neural") || name.contains("wavenet")) score += 55;
                    if (score > bestScore) { bestScore = score; best = v; }
                }
            }
            if (best != null) tts.setVoice(best);
            else tts.setLanguage(new Locale("ar", "SA"));
        } catch (Exception ignored) {
            tts.setLanguage(new Locale("ar", "SA"));
        }

        if ("teacher".equals(voiceMode)) {
            tts.setSpeechRate(0.86f);
            tts.setPitch(1.00f);
        } else if ("slow".equals(voiceMode)) {
            tts.setSpeechRate(0.68f);
            tts.setPitch(0.96f);
        } else {
            tts.setSpeechRate(0.78f);
            tts.setPitch(0.92f);
        }
    }

    private void speechDone() {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(
                "if(window.noorNativeSpeechDone){window.noorNativeSpeechDone();}", null);
        });
    }

    private void handleBack() {
        String js = "(function(){" +
                "var s=document.getElementById('settingsSheet');" +
                "if(s&&s.classList.contains('open')){s.classList.remove('open');document.body.style.overflow='';return true;}" +
                "var r=document.getElementById('reader');" +
                "if(r&&r.classList.contains('open')){r.classList.remove('open');document.body.style.overflow='';return true;}" +
                "return false;})()";
        webView.evaluateJavascript(js, value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    public class NoorBridge {
        private final Context context;
        NoorBridge(Context context) { this.context = context; }

        @JavascriptInterface
        public void haptic(boolean strong) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator()) return;
                long ms = strong ? 22 : 10;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, strong ? 115 : 75));
                } else {
                    v.vibrate(ms);
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void share(String title, String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title);
                send.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, "Share Noor-e-Dua"));
            });
        }

        @JavascriptInterface
        public void speak(String text) {
            runOnUiThread(() -> {
                if (!ttsReady || tts == null) {
                    speechDone();
                    return;
                }
                String id = "noor-" + UUID.randomUUID();
                tts.stop();
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
            });
        }

        @JavascriptInterface
        public void setVoiceMode(String mode) {
            runOnUiThread(() -> {
                if ("teacher".equals(mode) || "slow".equals(mode) || "qari".equals(mode)) {
                    voiceMode = mode;
                    applyVoiceProfile();
                }
            });
        }

        @JavascriptInterface
        public void stopSpeak() {
            runOnUiThread(() -> {
                if (tts != null) tts.stop();
                speechDone();
            });
        }
    }
}
