package com.example.togglegrayscale.vot;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;

public class VotTtsEngine implements TextToSpeech.OnInitListener {
    private static final String TAG = "VotTtsEngine";

    private final Context context;
    private TextToSpeech tts;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean isInitialized = false;
    private boolean hasDuckedFocus = false;
    private float baseSpeechRate = 1.1f;
    private float basePitch = 1.0f;

    public interface SpeechListener {
        void onSpeechStart(String utteranceId);
        void onSpeechDone(String utteranceId);
        void onSpeechError(String utteranceId);
    }

    private SpeechListener speechListener;

    public VotTtsEngine(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        try {
            this.tts = new TextToSpeech(this.context, this);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TextToSpeech", e);
        }
    }

    public void setSpeechListener(SpeechListener listener) {
        this.speechListener = listener;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int res = tts.setLanguage(new Locale("es", "US"));
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                res = tts.setLanguage(new Locale("es", "ES"));
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    res = tts.setLanguage(new Locale("es"));
                }
            }
            tts.setSpeechRate(baseSpeechRate);
            tts.setPitch(basePitch);

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "TTS onStart: " + utteranceId);
                    if (speechListener != null) speechListener.onSpeechStart(utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "TTS onDone: " + utteranceId);
                    releaseAudioFocus();
                    if (speechListener != null) speechListener.onSpeechDone(utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    Log.w(TAG, "TTS onError: " + utteranceId);
                    releaseAudioFocus();
                    if (speechListener != null) speechListener.onSpeechError(utteranceId);
                }
            });

            isInitialized = true;
            Log.d(TAG, "TextToSpeech initialized successfully with Spanish language");
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech, status: " + status);
        }
    }

    public void setSpeechRate(float rate) {
        this.baseSpeechRate = Math.max(0.7f, Math.min(2.0f, rate));
        if (tts != null && isInitialized) {
            tts.setSpeechRate(this.baseSpeechRate);
        }
    }

    public void setPitch(float pitch) {
        this.basePitch = Math.max(0.5f, Math.min(1.5f, pitch));
        if (tts != null && isInitialized) {
            tts.setPitch(this.basePitch);
        }
    }

    public synchronized void speakCue(VotCue cue, long availableDurationMs) {
        if (!isInitialized || tts == null || cue == null || cue.translatedText == null || cue.translatedText.isEmpty()) {
            return;
        }

        // Calculate adaptive speech rate
        float speechRate = calculateAdaptiveRate(cue.translatedText, availableDurationMs);
        tts.setSpeechRate(speechRate);

        // Request Audio Focus with Ducking (lowers background video volume)
        requestAudioDucking();

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "vot_cue_" + cue.id);
        tts.speak(cue.translatedText, TextToSpeech.QUEUE_FLUSH, params, "vot_cue_" + cue.id);
    }

    public synchronized void speakTestPhrase(String phrase) {
        if (!isInitialized || tts == null) return;
        if (phrase == null || phrase.isEmpty()) {
            phrase = "Probando el doblaje de voz inteligente con reducción automática de volumen original.";
        }

        tts.setSpeechRate(baseSpeechRate);
        requestAudioDucking();

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "vot_test_phrase");
        tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "vot_test_phrase");
    }

    public synchronized void stop() {
        if (tts != null && isInitialized) {
            try {
                tts.stop();
            } catch (Exception ignored) {}
        }
        releaseAudioFocus();
    }

    public synchronized void shutdown() {
        stop();
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
        }
        isInitialized = false;
    }

    private float calculateAdaptiveRate(String text, long availableDurationMs) {
        if (availableDurationMs <= 0) return baseSpeechRate;
        // Estimate words
        String[] words = text.split("\\s+");
        int count = words.length;
        double durationSec = availableDurationMs / 1000.0;
        // Standard comfortable rate is ~2.8 words/second at 1.0x
        double wordsPerSec = count / Math.max(0.8, durationSec);
        float targetRate = (float) (wordsPerSec / 2.8f);

        // Clamp between baseSpeechRate and max 1.45x
        return Math.max(baseSpeechRate, Math.min(1.45f, targetRate));
    }

    @SuppressWarnings("deprecation")
    private void requestAudioDucking() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();

                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                            @Override
                            public void onAudioFocusChange(int focusChange) {}
                        })
                        .build();

                int res = audioManager.requestAudioFocus(audioFocusRequest);
                hasDuckedFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            } else {
                int res = audioManager.requestAudioFocus(
                        null,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                );
                hasDuckedFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting audio focus ducking", e);
        }
    }

    @SuppressWarnings("deprecation")
    private void releaseAudioFocus() {
        if (audioManager == null || !hasDuckedFocus) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus((AudioManager.OnAudioFocusChangeListener) null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing audio focus", e);
        } finally {
            hasDuckedFocus = false;
        }
    }
}
