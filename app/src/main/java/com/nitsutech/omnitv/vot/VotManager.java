package com.nitsutech.omnitv.vot;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VotManager {
    private static final String TAG = "VotManager";
    private static final String PREFS_NAME = "overlay_prefs";

    public static final String KEY_VOT_ENABLED = "vot_enabled";
    public static final String KEY_VOT_SOURCE_LANG = "vot_source_lang"; // "auto", "en", "ko", "ja"
    public static final String KEY_VOT_TARGET_LANG = "vot_target_lang"; // "es", "en", "ko", "ja"
    public static final String KEY_VOT_DUCKING_MODE = "vot_ducking_mode"; // 0: Duck 25%, 1: Mute/Pause, 2: Sin atenuación
    public static final String KEY_VOT_TTS_VOLUME = "vot_tts_volume"; // 1.0f, 0.85f, 0.70f, 0.50f
    public static final String KEY_VOT_SHOW_SUBTITLES = "vot_show_subtitles";
    public static final String KEY_VOT_BILINGUAL = "vot_bilingual";
    public static final String KEY_VOT_SPEECH_RATE = "vot_speech_rate"; // 1.0f, 1.15f, 1.3f

    private static VotManager instance;

    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private final VotTtsEngine ttsEngine;
    private final VotSubtitleOverlay subtitleOverlay;

    private boolean isEnabled = false;
    private boolean showSubtitles = true;
    private boolean isBilingual = false;
    private String sourceLang = "auto";
    private String targetLang = "es";
    private int duckingMode = 0;
    private float ttsVolume = 1.0f;

    private VotTrack currentTrack;
    private String currentVideoId;
    private String currentVideoTitle;
    private int currentPlaybackState = PlaybackState.STATE_NONE;
    private long lastReportedPositionMs = 0;
    private long lastReportedPositionTime = 0;
    private boolean isFetchingTrack = false;
    private boolean isPreTranslating = false;

    private final Runnable tickerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isEnabled || currentPlaybackState != PlaybackState.STATE_PLAYING) {
                return;
            }
            onTick();
            mainHandler.postDelayed(this, 250);
        }
    };

    public static synchronized VotManager getInstance(Context context) {
        if (instance == null) {
            instance = new VotManager(context.getApplicationContext());
        }
        return instance;
    }

    private VotManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newFixedThreadPool(2);
        this.ttsEngine = new VotTtsEngine(context);
        this.subtitleOverlay = new VotSubtitleOverlay(context);
        loadPreferences();
    }

    public void loadPreferences() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isEnabled = prefs.getBoolean(KEY_VOT_ENABLED, false);
        showSubtitles = prefs.getBoolean(KEY_VOT_SHOW_SUBTITLES, true);
        isBilingual = prefs.getBoolean(KEY_VOT_BILINGUAL, false);
        sourceLang = prefs.getString(KEY_VOT_SOURCE_LANG, "auto");
        targetLang = prefs.getString(KEY_VOT_TARGET_LANG, "es");
        duckingMode = prefs.getInt(KEY_VOT_DUCKING_MODE, 0);
        ttsVolume = prefs.getFloat(KEY_VOT_TTS_VOLUME, 1.0f);
        float rate = prefs.getFloat(KEY_VOT_SPEECH_RATE, 1.1f);
        ttsEngine.setSpeechRate(rate);
        ttsEngine.setLanguage(targetLang);
        ttsEngine.setDuckingMode(duckingMode);
        ttsEngine.setVolume(ttsVolume);
        Log.d(TAG, "Preferences loaded: enabled=" + isEnabled + ", src=" + sourceLang + ", tgt=" + targetLang + ", subs=" + showSubtitles + ", rate=" + rate + ", duck=" + duckingMode + ", vol=" + ttsVolume);
    }

    public synchronized void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_VOT_ENABLED, enabled).apply();

        if (!enabled) {
            stop();
        } else if (currentPlaybackState == PlaybackState.STATE_PLAYING) {
            startTicker();
            if (currentTrack == null && currentVideoTitle != null) {
                triggerTrackFetch(currentVideoTitle, null);
            }
        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public String getTargetLanguage() {
        return targetLang;
    }

    public synchronized void setTargetLanguage(String lang) {
        if (lang == null || lang.isEmpty()) lang = "es";
        this.targetLang = lang;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VOT_TARGET_LANG, lang).apply();
        ttsEngine.setLanguage(lang);
        if (currentTrack != null) {
            currentTrack.invalidateTranslations();
        }
    }

    public int getDuckingMode() {
        return duckingMode;
    }

    public synchronized void setDuckingMode(int mode) {
        this.duckingMode = mode;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_VOT_DUCKING_MODE, mode).apply();
        ttsEngine.setDuckingMode(mode);
    }

    public float getTtsVolume() {
        return ttsVolume;
    }

    public synchronized void setTtsVolume(float volume) {
        this.ttsVolume = Math.max(0.1f, Math.min(1.0f, volume));
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_VOT_TTS_VOLUME, this.ttsVolume).apply();
        ttsEngine.setVolume(this.ttsVolume);
    }

    public synchronized void onVideoChanged(String pkg, String title, String artist, long duration) {
        if (title == null || title.isEmpty()) return;
        if (currentVideoTitle != null && currentVideoTitle.equalsIgnoreCase(title)) {
            return;
        }

        Log.d(TAG, "Video changed in " + pkg + ": title='" + title + "', artist='" + artist + "'");
        currentVideoTitle = title;
        currentVideoId = null;
        currentTrack = null;
        stop();

        if (isEnabled) {
            triggerTrackFetch(title, artist);
        }
    }

    public synchronized void onPlaybackStateChanged(String pkg, int state, long positionMs) {
        long now = SystemClock.elapsedRealtime();
        long prevPos = getEstimatedPositionMs();
        this.currentPlaybackState = state;
        this.lastReportedPositionMs = positionMs;
        this.lastReportedPositionTime = now;

        // Detect seek / jump
        if (Math.abs(positionMs - prevPos) > 2500) {
            Log.d(TAG, "Detected playback seek to " + positionMs + "ms (was " + prevPos + "ms)");
            ttsEngine.stop();
            subtitleOverlay.hide();
            if (currentTrack != null) {
                currentTrack.syncPosition(positionMs);
            }
        }

        if (state == PlaybackState.STATE_PLAYING) {
            if (isEnabled) {
                startTicker();
            }
        } else {
            mainHandler.removeCallbacks(tickerRunnable);
            ttsEngine.stop();
            subtitleOverlay.hide();
        }
    }

    private void startTicker() {
        mainHandler.removeCallbacks(tickerRunnable);
        mainHandler.post(tickerRunnable);
    }

    private void stop() {
        mainHandler.removeCallbacks(tickerRunnable);
        ttsEngine.stop();
        subtitleOverlay.hide();
    }

    private synchronized void onTick() {
        if (currentTrack == null || !isEnabled) return;

        long curPos = getEstimatedPositionMs();
        VotCue nextCue = currentTrack.getNextCueToSpeak(curPos);
        if (nextCue != null) {
            nextCue.isSpoken = true;
            Log.d(TAG, "Triggering VOT cue " + nextCue.id + " at " + curPos + "ms: " + nextCue.translatedText);

            ttsEngine.speakCue(nextCue, nextCue.durationMs);

            if (showSubtitles) {
                subtitleOverlay.showCue(nextCue, isBilingual, nextCue.durationMs);
            }
        }

        // Check lookahead for untranslated cues (next 45 seconds)
        triggerPreTranslation(curPos);
    }

    private long getEstimatedPositionMs() {
        if (currentPlaybackState == PlaybackState.STATE_PLAYING && lastReportedPositionTime > 0) {
            long elapsed = SystemClock.elapsedRealtime() - lastReportedPositionTime;
            return lastReportedPositionMs + elapsed;
        }
        return lastReportedPositionMs;
    }

    private void triggerTrackFetch(final String title, final String artist) {
        if (isFetchingTrack) return;
        isFetchingTrack = true;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String query = title;
                    if (artist != null && !artist.trim().isEmpty() && !title.toLowerCase().contains(artist.toLowerCase())) {
                        query = title + " " + artist;
                    }

                    Log.d(TAG, "Resolving video ID for query: " + query);
                    String videoId = YouTubeCaptionFetcher.resolveVideoId(query);
                    if (videoId != null) {
                        currentVideoId = videoId;
                        Log.d(TAG, "Resolved video ID: " + videoId + ", fetching caption track (preferred: " + sourceLang + ")...");
                        VotTrack track = YouTubeCaptionFetcher.fetchTrack(videoId, sourceLang);
                        if (track != null) {
                            Log.d(TAG, "Successfully loaded track with " + track.size() + " cues!");
                            // Pre-translate first 10 cues immediately
                            List<VotCue> initialCues = track.getUpcomingUntranslatedCues(getEstimatedPositionMs(), 10, 60000);
                            VotTranslationEngine.translateCues(context, initialCues, sourceLang, targetLang);

                            synchronized (VotManager.this) {
                                currentTrack = track;
                                currentTrack.syncPosition(getEstimatedPositionMs());
                            }
                        } else {
                            Log.w(TAG, "No subtitle track available for video: " + videoId);
                        }
                    } else {
                        Log.w(TAG, "Could not resolve video ID for: " + query);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in triggerTrackFetch", e);
                } finally {
                    isFetchingTrack = false;
                }
            }
        });
    }

    private void triggerPreTranslation(final long curPos) {
        if (isPreTranslating || currentTrack == null) return;

        final List<VotCue> untranslated = currentTrack.getUpcomingUntranslatedCues(curPos, 8, 45000);
        if (untranslated.isEmpty()) return;

        isPreTranslating = true;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    VotTranslationEngine.translateCues(context, untranslated, sourceLang, targetLang);
                } catch (Exception e) {
                    Log.e(TAG, "Error in triggerPreTranslation", e);
                } finally {
                    isPreTranslating = false;
                }
            }
        });
    }

    public void testVoiceAndDucking() {
        String phrase = "Doblaje de voz de prueba con atenuación de audio en OmniTV.";
        if ("en".equalsIgnoreCase(targetLang)) {
            phrase = "This is a voice dubbing and audio ducking test in OmniTV.";
        } else if ("ko".equalsIgnoreCase(targetLang)) {
            phrase = "OmniTV의 음성 더빙 및 오디오 더킹 테스트입니다.";
        } else if ("ja".equalsIgnoreCase(targetLang)) {
            phrase = "OmniTVの音声吹き替えとオーディオダッキングのテストです。";
        }
        ttsEngine.speakTestPhrase(phrase);
    }

    public void destroy() {
        stop();
        ttsEngine.shutdown();
        subtitleOverlay.hide();
        executor.shutdown();
        instance = null;
    }
}
