package com.nitsutech.omnitv.vot;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private String currentLanguage = "es";
    private float ttsVolume = 1.0f;
    private int duckingMode = 0; // 0: Duck 25%, 1: Mute video, 2: No ducking
    private boolean localVoiceOnly;
    private boolean localVoiceAvailable = true;
    private String selectedVoiceLabel = "";
    private int queuedUtterances;
    private static final class PendingCue {
        final VotCue cue;
        final long availableDurationMs;
        PendingCue(VotCue cue, long availableDurationMs) {
            this.cue = cue;
            this.availableDurationMs = availableDurationMs;
        }
    }
    private final List<PendingCue> pendingCues = new ArrayList<>();

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
            isInitialized = true;
            setLanguage(currentLanguage);
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
                    onUtteranceFinished();
                    if (speechListener != null) speechListener.onSpeechDone(utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    Log.w(TAG, "TTS onError: " + utteranceId);
                    onUtteranceFinished();
                    if (speechListener != null) speechListener.onSpeechError(utteranceId);
                }
            });

            flushPendingCues();

            Log.d(TAG, "TextToSpeech initialized successfully with language: " + currentLanguage);
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech, status: " + status);
        }
    }

    public void setLanguage(String langCode) {
        if (langCode == null || langCode.isEmpty()) langCode = "es";
        this.currentLanguage = langCode;
        if (tts == null || !isInitialized) return;
        Locale loc;
        if ("en".equalsIgnoreCase(langCode)) {
            loc = Locale.US;
        } else if ("ko".equalsIgnoreCase(langCode)) {
            loc = Locale.KOREA;
        } else if ("ja".equalsIgnoreCase(langCode)) {
            loc = Locale.JAPAN;
        } else {
            loc = new Locale("es", "US");
        }
        int res = tts.setLanguage(loc);
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            if ("en".equalsIgnoreCase(langCode)) loc = Locale.ENGLISH;
            else if ("es".equalsIgnoreCase(langCode)) loc = new Locale("es");
            tts.setLanguage(loc);
        }
        applyLocalVoiceIfRequested(loc);
        Log.d(TAG, "TTS language set to: " + loc);
    }

    public synchronized void setLocalVoiceOnly(boolean enabled) {
        localVoiceOnly = enabled;
        if (tts != null && isInitialized) setLanguage(currentLanguage);
    }

    public synchronized boolean isLocalVoiceOnly() { return localVoiceOnly; }

    public synchronized boolean isLocalVoiceAvailable() { return localVoiceAvailable; }

    public synchronized String getSelectedVoiceLabel() { return selectedVoiceLabel; }

    private void applyLocalVoiceIfRequested(Locale requestedLocale) {
        localVoiceAvailable = true;
        selectedVoiceLabel = "";
        if (!localVoiceOnly || tts == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            Set<Voice> voices = tts.getVoices();
            Voice best = null;
            for (Voice voice : voices) {
                Locale voiceLocale = voice.getLocale();
                if (voiceLocale == null || !voiceLocale.getLanguage().equalsIgnoreCase(requestedLocale.getLanguage())
                        || voice.isNetworkConnectionRequired()) continue;
                Set<String> features = voice.getFeatures();
                if (features != null && features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                if (best == null || ("es".equals(requestedLocale.getLanguage())
                        && "AR".equalsIgnoreCase(voiceLocale.getCountry()))) best = voice;
                if ("es".equals(requestedLocale.getLanguage()) && "AR".equalsIgnoreCase(voiceLocale.getCountry())) break;
            }
            if (best == null || tts.setVoice(best) != TextToSpeech.SUCCESS) {
                localVoiceAvailable = false;
                selectedVoiceLabel = "sin voz local";
            } else {
                Locale voiceLocale = best.getLocale();
                selectedVoiceLabel = voiceLocale.toLanguageTag();
            }
        } catch (Exception e) {
            localVoiceAvailable = false;
            selectedVoiceLabel = "sin voz local";
            Log.w(TAG, "Unable to select installed offline voice", e);
        }
    }

    public void setVolume(float volume) {
        this.ttsVolume = Math.max(0.1f, Math.min(1.0f, volume));
    }

    public void setDuckingMode(int mode) {
        this.duckingMode = mode;
        if (mode == 2) releaseAudioFocus();
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
        if (cue == null || cue.translatedText == null || cue.translatedText.isEmpty()) {
            return;
        }

        String speakableText = getSpeakableText(cue.translatedText);
        if (speakableText.isEmpty() || (localVoiceOnly && !localVoiceAvailable)) return;
        if (!isInitialized || tts == null) {
            // TTS initialization can take several seconds after process restart.
            // Keep cues already marked as spoken so they are not silently lost.
            if (tts != null) pendingCues.add(new PendingCue(cue, availableDurationMs));
            return;
        }

        // Keep each utterance in the TTS queue. A new subtitle must never cut
        // the previous one off; per-utterance rate still uses the configured ceiling.
        float speechRate = calculateAdaptiveRate(speakableText, availableDurationMs);

        // Request Audio Focus with Ducking (lowers background video volume)
        requestAudioDucking();

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "vot_cue_" + cue.id);
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume);
        // The platform's legacy per-utterance parameter name is "rate".
        params.putFloat("rate", speechRate);
        queuedUtterances++;
        int result = tts.speak(speakableText, TextToSpeech.QUEUE_ADD, params, "vot_cue_" + cue.id);
        if (result == TextToSpeech.ERROR) {
            queuedUtterances = Math.max(0, queuedUtterances - 1);
            if (queuedUtterances == 0) releaseAudioFocus();
        }
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
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume);
        queuedUtterances = 0;
        tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "vot_test_phrase");
    }

    public synchronized void stop() {
        pendingCues.clear();
        if (tts != null && isInitialized) {
            try {
                tts.stop();
            } catch (Exception ignored) {}
        }
        queuedUtterances = 0;
        releaseAudioFocus();
    }

    private synchronized void flushPendingCues() {
        if (!isInitialized || pendingCues.isEmpty()) return;
        List<PendingCue> ready = new ArrayList<>(pendingCues);
        pendingCues.clear();
        for (PendingCue pending : ready) {
            speakCue(pending.cue, pending.availableDurationMs);
        }
    }

    private static String getSpeakableText(String text) {
        if (text == null) return "";
        // Strip caption speaker markers/prefix ornaments (arrows, quotes,
        // music symbols, etc.) so TTS reads the words rather than the markup.
        String cleaned = text.trim().replaceFirst("^(?:[\\p{P}\\p{S}]\\s*)+", "").trim();
        if (cleaned.isEmpty() || cleaned.matches("(?s)^[\\p{P}\\p{S}\\s]+$")) return "";
        return cleaned;
    }

    private synchronized void onUtteranceFinished() {
        queuedUtterances = Math.max(0, queuedUtterances - 1);
        if (queuedUtterances == 0) releaseAudioFocus();
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
        float maxSpeechRate = Math.max(1.0f, baseSpeechRate);
        if (availableDurationMs <= 0) return maxSpeechRate;
        // Estimate words
        String[] words = text.split("\\s+");
        int count = words.length;
        double durationSec = availableDurationMs / 1000.0;
        // Standard comfortable rate is ~2.8 words/second at 1.0x
        double wordsPerSec = count / Math.max(0.8, durationSec);
        float targetRate = (float) (wordsPerSec / 2.8f);

        // Speak at normal speed unless the subtitle window requires acceleration.
        // The configured speech rate is a ceiling, never a minimum.
        return Math.min(maxSpeechRate, Math.max(1.0f, targetRate));
    }

    @SuppressWarnings("deprecation")
    private void requestAudioDucking() {
        if (audioManager == null || duckingMode == 2 || hasDuckedFocus) return;
        int gainType = duckingMode == 1
                ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();

                audioFocusRequest = new AudioFocusRequest.Builder(gainType)
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
                        gainType
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
