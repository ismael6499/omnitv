package com.example.togglegrayscale;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.view.Display;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;

public class ButtonMappingService extends AccessibilityService {

    public static ButtonMappingService instance = null;

    private static final String TAG = "ButtonMappingService";
    private static final long BLACK_SCREEN_LONG_PRESS_MS = 2000;
    private final Handler handler = new Handler();
    private AudioManager audioManager;

    private boolean isBlackScreenActive = false;
    private View blackOverlayView = null;
    private boolean isDismissingBlackScreenKey = false;
    private int blackScreenDismissKeyCode = 0;

    private boolean isScheduledSleepPromptActive = false;
    private View scheduledSleepOverlayView = null;
    private int scheduledSleepCountdownSeconds = 60;
    private Runnable scheduledSleepCountdownRunnable = null;

    private boolean isInputPressed = false;
    private boolean isInputLongPressTriggered = false;
    private boolean isDismissingComboKey = false;
    private boolean isTranslationOverlayActive = false;
    private View translationOverlayView = null;

    static final String OVERLAY_PREFS = "overlay_prefs";
    static final String KEY_BLUE_LIGHT = "blue_light";
    static final String KEY_CLOCK = "clock";
    static final String KEY_DIMMER = "dimmer";
    static final String KEY_CINE_MODE = "cine_mode";
    static final String KEY_STILL_WATCHING = "still_watching";
    static final String KEY_STILL_WATCHING_INTERVAL = "still_watching_interval";
    static final String KEY_STILL_WATCHING_TIMEOUT = "still_watching_timeout";
    static final String KEY_STILL_WATCHING_ACTION = "still_watching_action";
    static final String KEY_STILL_WATCHING_POS = "still_watching_pos";
    static final String KEY_STILL_WATCHING_ALPHA = "still_watching_alpha";
    static final String KEY_STILL_WATCHING_SIZE = "still_watching_size";
    static final String KEY_STILL_WATCHING_PAD = "still_watching_pad";
    static final String KEY_STILL_WATCHING_X = "still_watching_x";
    static final String KEY_STILL_WATCHING_Y = "still_watching_y";
    static final String KEY_STILL_WATCHING_BEEP = "still_watching_beep";
    static final String KEY_STILL_WATCHING_BEEP_DELAY = "still_watching_beep_delay";
    static final String KEY_STILL_WATCHING_BEEP_INTERVAL = "still_watching_beep_interval";
    static final String KEY_STILL_WATCHING_BEEP_VOLUME = "still_watching_beep_volume";
    static final String KEY_STILL_WATCHING_BEEP_TONE = "still_watching_beep_tone";

    static final String KEY_NIGHT_SCHEDULE = "night_schedule";
    static final String KEY_NIGHT_START = "night_start";
    static final String KEY_NIGHT_END = "night_end";
    static final String KEY_NIGHT_BLUE_LIGHT = "night_blue_light";
    static final String KEY_NIGHT_DIMMER = "night_dimmer";

    static final String KEY_OLED_SAVER = "oled_saver";
    static final String KEY_OLED_MINUTES = "oled_minutes";
    static final String KEY_OLED_MODE = "oled_mode";

    static final String KEY_MINDFUL_DELAY = "mindful_delay_enabled";

    private boolean isMindfulDelayActive = false;
    private View mindfulDelayOverlayView = null;
    private TextView txtMindfulDelayTimer = null;
    private TextView txtMindfulDelayMsg = null;
    private TextView txtMindfulDelayAppName = null;
    private int mindfulRemainingSeconds = 0;
    private Runnable mindfulCountdownRunnable = null;
    private String currentMindfulAppKey = null;
    private String currentMindfulAppName = null;
    private final java.util.Map<String, Long> authorizedSessions = new java.util.HashMap<>();

    private static final String[] MINDFUL_MSG_OPTIONS = {
        "¿Realmente querés ver algo ahora? Esperá o volvé a Home.",
        "Pausa consciente: ¿Es una distracción o una decisión?",
        "Tomate un momento para respirar antes de entrar.",
        "Tiempo de espera activo para evitar el consumo compulsivo."
    };

    private static final String[] MINDFUL_SESSION_NAMES = {
        "Por Tiempo Personalizado",
        "Solo mientras no salga a Home",
        "Hasta apagar la pantalla / TV",
        "Todo el día (hasta medianoche)"
    };

    private static final String[][] MINDFUL_APPS = {
        {"youtube", "YouTube", "mindful_app_youtube", "com.google.android.youtube.tv", "com.google.android.youtube"},
        {"netflix", "Netflix", "mindful_app_netflix", "com.netflix.ninja", "com.netflix.mediaclient"},
        {"disney", "Disney+", "mindful_app_disney", "com.disney.disneyplus"},
        {"prime", "Prime Video", "mindful_app_prime", "com.amazon.amazonvideo.livingroom", "com.amazon.avod"},
        {"max", "Max (HBO)", "mindful_app_max", "com.wbd.stream", "com.hbo.hbonow"},
        {"star", "Star+", "mindful_app_star", "com.disney.starplus"},
        {"twitch", "Twitch", "mindful_app_twitch", "tv.twitch.android.app", "tv.twitch.android.viewer"},
        {"tiktok", "TikTok", "mindful_app_tiktok", "com.tiktok.tv"},
        {"smarttube", "SmartTube", "mindful_app_smarttube", "com.teamsmart.videomanager.tv", "com.liskovsoft.videomanager"},
        {"stremio", "Stremio", "mindful_app_stremio", "com.stremio.one"},
        {"plex", "Plex", "mindful_app_plex", "com.plexapp.android"}
    };

    private boolean isBlueLightActive = false;
    private View blueLightOverlayView = null;

    private boolean isClockActive = false;
    private View clockOverlayView = null;
    private TextView clockTextView = null;

    private boolean isDimmerActive = false;
    private View dimmerOverlayView = null;

    private View brightnessHudOverlayView = null;
    private TextView brightnessHudTextView = null;
    private final Runnable hideBrightnessHudRunnable = new Runnable() {
        @Override
        public void run() {
            hideBrightnessHudOverlay();
        }
    };

    private boolean isStillWatchingActive = false;
    private boolean isStillWatchingPromptActive = false;
    private boolean isDismissingStillWatchingKey = false;
    private int stillWatchingDismissKeyCode = 0;
    private boolean isDismissingQuickMenuKey = false;
    private View stillWatchingOverlayView = null;
    private int stillWatchingCountdownSeconds = 30;
    private long cachedStillWatchingIntervalMs = 30 * 60 * 1000L;

    private boolean isOledSaverActive = false;
    private boolean isOledSaverPromptActive = false;
    private View oledSaverOverlayView = null;
    private long cachedOledSaverDelayMs = 5 * 60 * 1000L;

    private final Runnable oledSaverRunnable = new Runnable() {
        @Override
        public void run() {
            if (isOledSaverActive) {
                showOledSaverOverlay();
            }
        }
    };

    private final Runnable nightScheduleCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkAndApplyNightSchedule();
            handler.postDelayed(this, 60000);
        }
    };

    private final Runnable stillWatchingIntervalRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isStillWatchingActive) return;

            // 1. Check if black screen is active
            if (isBlackScreenActive) {
                Log.d(TAG, "Still watching skipped: Black Screen is active.");
                handler.postDelayed(this, cachedStillWatchingIntervalMs);
                return;
            }

            // 2. Check if device screen is interactive (screen on vs standby/off)
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isInteractive()) {
                    Log.d(TAG, "Still watching skipped: Screen is not interactive (turned off / standby).");
                    handler.postDelayed(this, cachedStillWatchingIntervalMs);
                    return;
                }
            } catch (Exception ignored) {}

            // 3. Check if media / video is actively playing
            boolean isPlaying = false;
            try {
                if (audioManager != null && audioManager.isMusicActive()) {
                    isPlaying = true;
                }
            } catch (Exception ignored) {}

            if (!isPlaying) {
                Log.d(TAG, "Still watching skipped: No video/media is actively playing.");
                handler.postDelayed(this, cachedStillWatchingIntervalMs);
                return;
            }

            showStillWatchingPrompt();
        }
    };

    private final Runnable stillWatchingCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (isStillWatchingPromptActive && stillWatchingOverlayView != null) {
                stillWatchingCountdownSeconds--;
                if (stillWatchingCountdownSeconds <= 0) {
                    onStillWatchingTimeoutExpired();
                } else {
                    updateStillWatchingPromptText();
                    handler.postDelayed(this, 1000);
                }
            }
        }
    };

    private final Runnable stillWatchingBeepRunnable = new Runnable() {
        @Override
        public void run() {
            if (isStillWatchingPromptActive && !isBlackScreenActive) {
                try {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm != null && !pm.isInteractive()) return;
                } catch (Exception ignored) {}

                SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                boolean beepEnabled = prefs.getBoolean(KEY_STILL_WATCHING_BEEP, true);
                if (beepEnabled) {
                    playStillWatchingBeep();
                    int intervalSec = prefs.getInt(KEY_STILL_WATCHING_BEEP_INTERVAL, 10);
                    if (intervalSec < 1) intervalSec = 1;
                    handler.postDelayed(this, intervalSec * 1000L);
                }
            }
        }
    };

    private View systemInfoOverlayView = null;
    private View frameStepHudView = null;
    private boolean isFrameStepHudActive = false;

    private int clockShowRetries = 0;
    private int blueLightShowRetries = 0;
    private int dimmerShowRetries = 0;

    private final Runnable clockUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockText();
            handler.postDelayed(this, 1000);
        }
    };

    private class ButtonState {
        final String name;
        final int defaultClick1;
        final int defaultClick2;
        final int defaultClick3;
        final int defaultClick4;
        final int defaultLong;
        final int defaultDurationMs;

        boolean isPressed = false;
        boolean isLongPressTriggered = false;
        int clickCount = 0;

        final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPressed) {
                    isLongPressTriggered = true;
                    SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    int actionId = prefs.getInt("btn_" + name + "_long_action", defaultLong);
                    Log.d(TAG, name + " long press detected! Running action: " + actionId);
                    executeAction(actionId);
                }
            }
        };

        final Runnable clickTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                int count = clickCount;
                clickCount = 0;
                SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                int actionId = 0;
                if (count == 1) {
                    actionId = prefs.getInt("btn_" + name + "_click_1_action", defaultClick1);
                } else if (count == 2) {
                    actionId = prefs.getInt("btn_" + name + "_click_2_action", defaultClick2);
                } else if (count == 3) {
                    actionId = prefs.getInt("btn_" + name + "_click_3_action", defaultClick3);
                } else if (count >= 4) {
                    actionId = prefs.getInt("btn_" + name + "_click_4_action", defaultClick4);
                }
                Log.d(TAG, name + " " + count + "-click executed. Running action: " + actionId);
                executeAction(actionId);
            }
        };

        ButtonState(String name, int defaultClick1, int defaultClick2, int defaultClick3, int defaultClick4, int defaultLong, int defaultDurationMs) {
            this.name = name;
            this.defaultClick1 = defaultClick1;
            this.defaultClick2 = defaultClick2;
            this.defaultClick3 = defaultClick3;
            this.defaultClick4 = defaultClick4;
            this.defaultLong = defaultLong;
            this.defaultDurationMs = defaultDurationMs;
        }

        void onDown() {
            if (!isPressed) {
                isPressed = true;
                isLongPressTriggered = false;
                SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                int durationMs = prefs.getInt("btn_" + name + "_long_duration_ms", defaultDurationMs);
                Log.d(TAG, name + " DOWN, starting long-press timer with duration: " + durationMs);
                handler.postDelayed(longPressRunnable, durationMs);
            }
        }

        void onUp() {
            handler.removeCallbacks(longPressRunnable);
            if (!isLongPressTriggered && isPressed) {
                clickCount++;
                handler.removeCallbacks(clickTimeoutRunnable);
                if (clickCount == 4) {
                    clickTimeoutRunnable.run();
                } else {
                    handler.postDelayed(clickTimeoutRunnable, 300);
                }
            }
            isPressed = false;
            isLongPressTriggered = false;
        }

        void cancel() {
            handler.removeCallbacks(longPressRunnable);
            handler.removeCallbacks(clickTimeoutRunnable);
            clickCount = 0;
            isPressed = false;
            isLongPressTriggered = false;
        }
    }

    private final ButtonState muteState = new ButtonState("mute", 1, 7, 8, 23, 2, 500);
    private final ButtonState youtube190State = new ButtonState("youtube_190", 5, 4, 3, 0, 18, 500);
    private final ButtonState youtube189State = new ButtonState("youtube_189", 5, 0, 0, 0, 4, 2000);

    private long lastAutoPauseTime = 0;
    private long lastCountdownDetectTime = 0;
    private boolean isLastPlaylistItem = false;
    private String lastSeenMediaTitle = null;
    private long lastMediaTitleSetTime = 0;
    private static final java.util.regex.Pattern TIME_PATTERN = 
        java.util.regex.Pattern.compile("(\\d?\\d:\\d\\d(:\\d\\d)?)\\s*(/|of|de)\\s*(\\d?\\d:\\d\\d(:\\d\\d)?)", java.util.regex.Pattern.CASE_INSENSITIVE);

    private final Runnable autoPauseCheckRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                checkAutoPause();
            } catch (Exception e) {
                Log.e(TAG, "Error in autoPauseCheckRunnable", e);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private static class ScreenInfo {
        final java.util.ArrayList<String> texts = new java.util.ArrayList<>();
        boolean isProgressBarNearEnd = false;
    }

    private void checkAutoPause() {
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int mode = op.getInt("auto_pause_mode", 0);
        if (mode == 0) return; // Disabled
        Log.d(TAG, "checkAutoPause tick: mode=" + mode);
        checkUpNextDismissal();

        long now = SystemClock.elapsedRealtime();
        if (now - lastAutoPauseTime < 15000) { // 15s cooldown
            return;
        }

        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                processRootNode(root, mode, now);
                root.recycle();
            }
            return;
        }

        for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) {
                boolean processed = processRootNode(root, mode, now);
                root.recycle();
                if (processed) {
                    break;
                }
            }
        }
    }

    private boolean processRootNode(AccessibilityNodeInfo root, int mode, long now) {
        CharSequence pkgSeq = root.getPackageName();
        if (pkgSeq == null) return false;
        String pkg = pkgSeq.toString();

        if (isExcludedPackage(pkg)) return false;

        boolean isKnownStreaming = pkg.contains("youtube")
                || pkg.contains("netflix")
                || pkg.contains("disney")
                || pkg.contains("smarttube")
                || pkg.contains("amazonvideo")
                || pkg.contains("primevideo")
                || pkg.contains("max")
                || pkg.contains("stremio")
                || pkg.contains("plex")
                || pkg.contains("paramountplus");

        boolean musicActive = (audioManager != null && audioManager.isMusicActive());
        
        if (!isKnownStreaming && !musicActive) {
            return false;
        }

        ScreenInfo info = new ScreenInfo();
        scanScreen(root, info);
        Log.d(TAG, "processRootNode: pkg=" + pkg + ", mode=" + mode + ", isMusic=" + musicActive + ", texts=" + info.texts);

        if (info.texts.isEmpty() && !info.isProgressBarNearEnd) {
            return false;
        }

        // Check playlist progress and auto-detect total count (e.g. "1/2", "5/5", "10 / 10", "12 de 12", "Watch Later • 5/5")
        for (String text : info.texts) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(/|de|of)\\s*(\\d+)").matcher(text);
            if (m.find()) {
                try {
                    int curIdx = Integer.parseInt(m.group(1));
                    int total = Integer.parseInt(m.group(3));
                    if (total > 0) {
                        getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putInt("auto_pause_playlist_count", total).apply();
                        Log.d(TAG, "Auto pause: Auto-detected playlist total: " + total + " (cur=" + curIdx + ")");
                    }
                    if (total > 0 && curIdx >= total) {
                        isLastPlaylistItem = true;
                        Log.d(TAG, "Auto pause: Playlist is at last item (" + curIdx + "/" + total + ")");
                    } else if (total > 0 && curIdx < total) {
                        isLastPlaylistItem = false;
                    }
                } catch (Exception ignored) {}
            }
            java.util.regex.Matcher mVids = java.util.regex.Pattern.compile("(\\d+)\\s+(videos?|elementos?)").matcher(text);
            if (mVids.find()) {
                try {
                    int total = Integer.parseInt(mVids.group(1));
                    if (total > 0 && total <= 500) {
                        getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putInt("auto_pause_playlist_count", total).apply();
                        Log.d(TAG, "Auto pause: Auto-detected playlist header count: " + total + " videos");
                    }
                } catch (Exception ignored) {}
            }
        }

        // 1. Check countdown
        if (checkAutoplayCountdown(info.texts)) {
            lastCountdownDetectTime = now;
            Log.d(TAG, "Auto pause: countdown detected! mode=" + mode + ", isLastItem=" + isLastPlaylistItem);
            if (mode == 1 || mode == 3 || mode == 4 || (mode == 2 && isLastPlaylistItem)) {
                triggerAutoPause();
                isLastPlaylistItem = false;
                return true;
            }
        }

        // 2. Check ProgressBar near end
        if (info.isProgressBarNearEnd) {
            Log.d(TAG, "Auto pause: ProgressBar near end detected!");
            if (mode == 1 || mode == 3 || mode == 4 || (mode == 2 && isLastPlaylistItem)) {
                triggerAutoPause();
                isLastPlaylistItem = false;
                return true;
            }
        }

        // 3. Check time text near end
        for (String text : info.texts) {
            if (checkTimeText(text)) {
                Log.d(TAG, "Auto pause: Time text near end detected: " + text);
                if (mode == 1 || mode == 3 || mode == 4 || (mode == 2 && isLastPlaylistItem)) {
                    triggerAutoPause();
                    isLastPlaylistItem = false;
                    return true;
                }
            }
        }

        // 4. Check safety transition pause
        if (now - lastCountdownDetectTime < 15000) {
            for (String text : info.texts) {
                if (checkTimeTextAtStart(text)) {
                    Log.d(TAG, "Auto pause: Safety pause triggered for start of video: " + text);
                    if (mode == 1 || mode == 3 || mode == 4 || (mode == 2 && isLastPlaylistItem)) {
                        triggerAutoPause();
                        isLastPlaylistItem = false;
                        return true;
                    }
                }
            }
        }

        // 5. Track video title change across playlist / recommendations
        String candidateTitle = null;
        for (String text : info.texts) {
            if (text != null && text.length() >= 6 && !text.contains(":") && !text.matches(".*\\d+/\\d+.*") && !checkKeywords(text)) {
                candidateTitle = text;
                break;
            }
        }
        if (candidateTitle != null) {
            if (lastSeenMediaTitle == null) {
                lastSeenMediaTitle = candidateTitle;
                lastMediaTitleSetTime = now;
            } else if (!lastSeenMediaTitle.equalsIgnoreCase(candidateTitle)) {
                if (now - lastMediaTitleSetTime > 12000) {
                    Log.d(TAG, "Auto pause: Title change detected: '" + lastSeenMediaTitle + "' -> '" + candidateTitle + "'");
                    lastSeenMediaTitle = candidateTitle;
                    lastMediaTitleSetTime = now;
                    if (mode == 1 || mode == 4 || (mode == 2 && isLastPlaylistItem)) {
                        triggerAutoPause();
                        isLastPlaylistItem = false;
                        return true;
                    } else if (mode == 3) {
                        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                        int count = op.getInt("auto_pause_custom_count", 1);
                        if (count <= 1) {
                            triggerAutoPause();
                            return true;
                        } else {
                            op.edit().putInt("auto_pause_custom_count", count - 1).apply();
                        }
                    }
                } else {
                    lastSeenMediaTitle = candidateTitle;
                }
            }
        }
        
        return false;
    }

    private boolean isExcludedPackage(String pkg) {
        if (pkg == null) return true;
        return pkg.equals("com.example.togglegrayscale")
                || pkg.equals("com.google.android.tvlauncher")
                || pkg.equals("com.google.android.apps.tv.launcherx")
                || pkg.equals("com.android.launcher")
                || pkg.equals("com.android.tv.settings")
                || pkg.equals("com.google.android.tv.settings")
                || pkg.equals("com.android.systemui")
                || pkg.equals("com.google.android.inputmethod.latin")
                || pkg.equals("com.android.providers.media")
                || pkg.equals("android");
    }

    private boolean isLauncherPackage(String pkg) {
        if (pkg == null) return false;
        return pkg.equals("com.google.android.tvlauncher")
                || pkg.equals("com.google.android.apps.tv.launcherx")
                || pkg.equals("com.android.launcher");
    }

    private int playlistVideosPlayed = 0;

    public static final String KEY_AUTO_DISMISS_UP_NEXT = "auto_dismiss_up_next";
    private long currentVideoDuration = 0;
    private long lastDismissUpNextTime = 0;

    public void onStreamingVideoChanged(String pkg, String title, String artist, long duration) {
        lastDismissUpNextTime = 0; // Reset dismissal on video change
        if (duration > 0) {
            currentVideoDuration = duration;
            getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putLong("last_known_video_duration", duration).apply();
        }
        if (title == null || title.isEmpty()) return;
        Log.d(TAG, "onStreamingVideoChanged: pkg=" + pkg + ", title='" + title + "', artist='" + artist + "', dur=" + duration);
        
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int mode = op.getInt("auto_pause_mode", 0);
        if (mode == 0) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastAutoPauseTime < 8000) return;

        if (lastSeenMediaTitle == null) {
            lastSeenMediaTitle = title;
            lastMediaTitleSetTime = now;
            playlistVideosPlayed = 1;
            Log.d(TAG, "Initial video registered from MediaSession: " + title + " (Video 1 of playlist)");
            return;
        }

        if (!lastSeenMediaTitle.equalsIgnoreCase(title)) {
            playlistVideosPlayed++;
            Log.d(TAG, "Video transition confirmed via MediaSession! ('" + lastSeenMediaTitle + "' -> '" + title + "'). Videos count in session: " + playlistVideosPlayed);
            lastSeenMediaTitle = title;
            lastMediaTitleSetTime = now;

            if (mode == 1) { // Al terminar video actual (1 sola vez)
                Log.d(TAG, "Executing Auto-Pause (Mode 1: 1 sola vez)");
                MediaNotificationListener.pauseAllActiveMedia(this);
                triggerAutoPause();
            } else if (mode == 2) { // Al terminar Lista (N videos)
                int totalInList = op.getInt("auto_pause_playlist_count", 2);
                if (playlistVideosPlayed > totalInList) {
                    Log.d(TAG, "Executing Auto-Pause on playlist end! (List had " + totalInList + " videos, extra video started: " + title + ")");
                    MediaNotificationListener.pauseAllActiveMedia(this);
                    triggerAutoPause();
                    playlistVideosPlayed = 0; // Reset for next playlist
                } else {
                    Log.d(TAG, "Playlist video " + playlistVideosPlayed + "/" + totalInList + " started normally, continuing playback.");
                }
            } else if (mode == 3) { // Permanente (Cada cambio de video)
                Log.d(TAG, "Executing Auto-Pause (Mode 3: Permanente)");
                MediaNotificationListener.pauseAllActiveMedia(this);
                triggerAutoPause();
            }
        }
    }

    private long lastPlaybackPosition = 0;
    private long lastPlaybackPositionSetTime = 0;

    public void onStreamingPlaybackStateChanged(String pkg, int state, long position) {
        if (pkg == null || (!pkg.contains("youtube") && !pkg.contains("smarttube"))) return;
        lastPlaybackPosition = position;
        lastPlaybackPositionSetTime = SystemClock.elapsedRealtime();

        // If the user rewinds/seeks back before the final 22s, unlock dismissal immediately
        if (currentVideoDuration > 30000 && position < (currentVideoDuration - 22000)) {
            lastDismissUpNextTime = 0;
        }

        checkUpNextDismissal();
    }

    private void checkUpNextDismissal() {
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean autoDismiss = op.getBoolean(KEY_AUTO_DISMISS_UP_NEXT, true);
        if (!autoDismiss) return;

        if (currentVideoDuration <= 0) {
            currentVideoDuration = op.getLong("last_known_video_duration", 0);
        }
        if (currentVideoDuration <= 30000) return;

        long curPos = lastPlaybackPosition;
        if (lastPlaybackPositionSetTime > 0) {
            curPos += (SystemClock.elapsedRealtime() - lastPlaybackPositionSetTime);
        }

        // Trigger dismissal during final 17.5 seconds (ensures card is already mounted on screen)
        if (curPos >= (currentVideoDuration - 17500) && curPos < (currentVideoDuration - 1000)) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastDismissUpNextTime > 25000) {
                lastDismissUpNextTime = now;
                Log.d(TAG, "Triggering automatic Up Next card dismissal at pos=" + curPos + "/" + currentVideoDuration);
                dismissUpNextCard();
            }
        }
    }

    private void dismissUpNextCard() {
        try {
            boolean sent = performGlobalAction(GLOBAL_ACTION_BACK);
            Log.d(TAG, "Sent GLOBAL_ACTION_BACK for Up Next card dismissal: success=" + sent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send hardware back key", e);
        }
    }

    private void sendMediaPause() {
        try {
            if (audioManager != null) {
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PAUSE));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send media pause", e);
        }
    }

    private void scanScreen(AccessibilityNodeInfo node, ScreenInfo info) {
        if (node == null) return;

        // Check SeekBar/ProgressBar range info
        if (node.getClassName() != null) {
            String className = node.getClassName().toString();
            if (className.contains("SeekBar") || className.contains("ProgressBar")) {
                AccessibilityNodeInfo.RangeInfo range = node.getRangeInfo();
                if (range != null) {
                    float max = range.getMax();
                    float current = range.getCurrent();
                    if (max > 0) {
                        if (max == 100.0f || max == 1.0f) {
                            float ratio = current / max;
                            if (ratio > 0.985f && ratio < 1.0f) {
                                info.isProgressBarNearEnd = true;
                            }
                        } else if (max > 100.0f) {
                            float remaining = max - current;
                            if (remaining > 0 && remaining <= 10) {
                                info.isProgressBarNearEnd = true;
                            }
                        }
                    }
                }
            }
        }

        // Collect texts
        CharSequence txtSeq = node.getText();
        if (txtSeq != null && txtSeq.length() > 0) {
            info.texts.add(txtSeq.toString().toLowerCase(Locale.ROOT));
        }
        CharSequence descSeq = node.getContentDescription();
        if (descSeq != null && descSeq.length() > 0) {
            info.texts.add(descSeq.toString().toLowerCase(Locale.ROOT));
        }

        // Recurse children
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                scanScreen(child, info);
                child.recycle();
            }
        }
    }

    private boolean checkTimeText(String str) {
        if (str == null || str.isEmpty()) return false;
        java.util.regex.Matcher m = TIME_PATTERN.matcher(str);
        if (m.find()) {
            String currentStr = m.group(1);
            String totalStr = m.group(4);
            int currentSecs = parseTimeToSeconds(currentStr);
            int totalSecs = parseTimeToSeconds(totalStr);
            if (currentSecs > 0 && totalSecs > 0) {
                int remaining = totalSecs - currentSecs;
                if (remaining >= 2 && remaining <= 12) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkTimeTextAtStart(String str) {
        if (str == null || str.isEmpty()) return false;
        java.util.regex.Matcher m = TIME_PATTERN.matcher(str);
        if (m.find()) {
            String currentStr = m.group(1);
            int currentSecs = parseTimeToSeconds(currentStr);
            if (currentSecs >= 0 && currentSecs <= 3) {
                return true;
            }
        }
        return false;
    }

    private boolean checkAutoplayCountdown(java.util.ArrayList<String> texts) {
        boolean hasMediaKeyword = false;
        boolean hasCountdownDigit = false;
        
        for (String text : texts) {
            // Direct match with regex
            if (text.matches(".*(siguiente|next|autoplay|reproducir|comienza|starts?|canción|cancion)\\s*(en|in)?\\s*\\d+\\s*(s|seg|segundos|seconds)?.*")) {
                Log.d(TAG, "Autoplay countdown matched regex: " + text);
                return true;
            }
            
            if (text.contains("cancelar") || text.contains("cancel") 
                    || text.contains("reproducir ahora") || text.contains("play now")) {
                Log.d(TAG, "Autoplay countdown matched cancel/play now button: " + text);
                return true;
            }
            
            if (text.contains("siguiente") || text.contains("next") 
                    || text.contains("canción") || text.contains("cancion")
                    || text.contains("reproducir") || text.contains("play")) {
                hasMediaKeyword = true;
            }
            
            String trimmed = text.trim();
            if (trimmed.length() >= 1 && trimmed.length() <= 2) {
                try {
                    int val = Integer.parseInt(trimmed);
                    if (val >= 1 && val <= 15) {
                        hasCountdownDigit = true;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        
        if (hasMediaKeyword && hasCountdownDigit) {
            Log.d(TAG, "Autoplay countdown matched keyword + digit combination");
            return true;
        }
        
        return false;
    }

    private boolean checkKeywords(String str) {
        if (str == null || str.isEmpty()) return false;
        String lower = str.toLowerCase(Locale.ROOT);
        return lower.contains("siguiente episodio")
                || lower.contains("next episode")
                || lower.contains("siguiente video")
                || lower.contains("next video")
                || lower.contains("reproducir siguiente")
                || lower.contains("play next")
                || (lower.contains("siguiente en") && (lower.contains("segundos") || lower.contains("seg")))
                || (lower.contains("next in") && (lower.contains("seconds") || lower.contains("sec")))
                || lower.contains("comenzará en")
                || lower.contains("starts in");
    }

    private int parseTimeToSeconds(String timeStr) {
        if (timeStr == null) return -1;
        String[] parts = timeStr.split(":");
        try {
            if (parts.length == 2) {
                int mins = Integer.parseInt(parts[0].trim());
                int secs = Integer.parseInt(parts[1].trim());
                return mins * 60 + secs;
            } else if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0].trim());
                int mins = Integer.parseInt(parts[1].trim());
                int secs = Integer.parseInt(parts[2].trim());
                return hours * 3600 + mins * 60 + secs;
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private void triggerAutoPause() {
        lastAutoPauseTime = SystemClock.elapsedRealtime();
        Log.d(TAG, "Executing Auto Pause!");

        // Send KEYCODE_MEDIA_PAUSE with retry
        try {
            if (audioManager != null) {
                long eventTime = SystemClock.uptimeMillis();
                audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
                Log.d(TAG, "Sent KEYCODE_MEDIA_PAUSE for auto pause");

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (audioManager != null && audioManager.isMusicActive()) {
                                long t = SystemClock.uptimeMillis();
                                audioManager.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                                audioManager.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                                Log.d(TAG, "Sent fallback KEYCODE_MEDIA_PLAY_PAUSE for auto pause");
                            }
                        } catch (Exception ignored) {}
                    }
                }, 250);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send media pause key", e);
        }

        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);

        // Turn screen black if configured
        boolean turnBlack = op.getBoolean("auto_pause_black_screen", false);
        if (turnBlack) {
            showBlackScreen();
        }

        // Adjust mode/counters: Only mode 1 (1 sola vez) disables itself
        int mode = op.getInt("auto_pause_mode", 0);
        if (mode == 1) {
            op.edit().putInt("auto_pause_mode", 0).apply();
            Log.d(TAG, "Auto pause mode set to Disabled after execution (was Mode 1: 1 sola vez)");
        } else {
            Log.d(TAG, "Auto pause mode preserved in mode: " + mode);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Service connected and ready to intercept keys");
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
                info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
                setServiceInfo(info);
                Log.d(TAG, "AccessibilityServiceInfo configured programmatically.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set AccessibilityServiceInfo programmatically", e);
        }

        try {
            android.service.notification.NotificationListenerService.requestRebind(
                    new ComponentName(this, MediaNotificationListener.class));
            Log.d(TAG, "Requested rebind for MediaNotificationListener");
        } catch (Exception e) {
            Log.e(TAG, "Failed to requestRebind for MediaNotificationListener", e);
        }

        final SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (prefs.getBoolean(KEY_BLUE_LIGHT, false)) showBlueLightOverlay();
                if (prefs.getBoolean(KEY_CLOCK, true)) showClockOverlay();
                if (prefs.getBoolean(KEY_DIMMER, false)) showDimmerOverlay();
                if (prefs.getBoolean(KEY_STILL_WATCHING, false)) startStillWatchingTimer();
                if (prefs.getBoolean(KEY_OLED_SAVER, false)) startOledSaverTimer();
                if (prefs.getBoolean("scheduled_sleep_enabled", false)) {
                    ScheduledSleepReceiver.scheduleNextAlarm(ButtonMappingService.this);
                }
            }
        }, 500);

        // Start auto-pause checker
        handler.postDelayed(autoPauseCheckRunnable, 2000);

        // Start night schedule checker
        handler.postDelayed(nightScheduleCheckRunnable, 3000);

        // Register screen state and service actions receiver
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction("com.example.togglegrayscale.ACTION_TRANSLATE_SCREEN");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(screenStateReceiver, filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register screenStateReceiver", e);
        }
    }

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if ("com.example.togglegrayscale.ACTION_TRANSLATE_SCREEN".equals(intent.getAction())) {
                triggerScreenTranslation();
                return;
            }
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                Log.d(TAG, "Screen ON detected! Performing auto-reset of temporary modes.");
                SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                if (prefs.getBoolean(KEY_CINE_MODE, false)) {
                    prefs.edit().putBoolean(KEY_CINE_MODE, false).apply();
                    if (isBlueLightActive) hideBlueLightOverlay();
                    if (isDimmerActive) hideDimmerOverlay();
                }
                resetStillWatchingTimerOnKeyPress();
                resetOledSaverTimerOnKeyPress();
                int sessMode = prefs.getInt("mindful_delay_session_mode", 0);
                if (sessMode == 2 && !authorizedSessions.isEmpty()) {
                    authorizedSessions.clear();
                    Log.d(TAG, "Screen ON detected! Cleared screen-based mindful sessions.");
                }
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand received action: " + action);
            handleAction(action, intent.getExtras());
        }
        return START_STICKY;
    }

    public void handleAction(String action, android.os.Bundle extras) {
        Log.d(TAG, "handleAction: " + action);
        switch (action) {
            case "ACTION_SHOW_BLACK_SCREEN": showBlackScreen(); break;
            case "ACTION_TOGGLE_GRAYSCALE": ToggleUtils.toggleGrayscale(getApplicationContext()); break;
            case "ACTION_TOGGLE_BLUE_LIGHT": toggleBlueLight(); break;
            case "ACTION_SET_BLUE_LIGHT_LEVEL":
                int lvl = extras != null ? extras.getInt("level", 0) : 0;
                setBlueLightLevel(lvl);
                break;
            case "ACTION_SET_BLUE_LIGHT_PCT":
                int pct = extras != null ? extras.getInt("pct", 0) : 0;
                setBlueLightPct(pct);
                break;
            case "ACTION_TOGGLE_CLOCK": toggleClock(); break;
            case "ACTION_UPDATE_CLOCK":
                if (isClockActive) {
                    hideClockOverlay();
                    showClockOverlay();
                }
                break;
            case "ACTION_SET_DIMMER_BRIGHTNESS":
                if (extras != null) {
                    int pVal = extras.getInt("pct", 50);
                    setDimmerBrightness(pVal);
                }
                break;
            case "ACTION_TOGGLE_DIMMER": toggleDimmer(); break;
            case "ACTION_TOGGLE_STILL_WATCHING": toggleStillWatching(); break;
            case "ACTION_UPDATE_STILL_WATCHING": updateStillWatching(); break;
            case "ACTION_TEST_STILL_WATCHING_BEEP": playStillWatchingBeep(); break;
            case "ACTION_TOGGLE_NIGHT_SCHEDULE":
            case "ACTION_UPDATE_NIGHT_SCHEDULE":
                checkAndApplyNightSchedule();
                break;
            case "ACTION_TOGGLE_OLED_SAVER":
            case "ACTION_UPDATE_OLED_SAVER":
                updateOledSaver();
                break;
            case "ACTION_TOGGLE_CINE_MODE": toggleCineMode(); break;
            case "ACTION_SHOW_SYSTEM_INFO": showSystemInfoOverlay(); break;
            case "ACTION_REBOOT": rebootDevice(); break;
            case "ACTION_OPEN_SYSTEM": openSystemSettings(); break;
            case "ACTION_OPEN_BLUETOOTH": openBluetoothSettings(); break;
            case "ACTION_OPEN_DEVELOPER_OPTIONS": openDeveloperOptions(); break;
            case "ACTION_CYCLE_BRIGHTNESS": cycleBrightness(); break;
            case "ACTION_SHOW_BRIGHTNESS_HUD": {
                if (extras != null) {
                    int hudPct = extras.getInt("pct", -1);
                    int curIdx = extras.getInt("cur_idx", 1);
                    int total = extras.getInt("total", 1);
                    if (hudPct < 0) {
                        SharedPreferences sp = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                        hudPct = sp.getInt("dimmer_brightness_pct", 50);
                    }
                    showBrightnessHud(hudPct, curIdx, total);
                } else {
                    SharedPreferences sp = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    int cur = sp.getInt("dimmer_brightness_pct", 50);
                    String levelsStr = sp.getString("brightness_levels_list", "80,50,20");
                    String[] parts = levelsStr.split(",");
                    int closestIdx = 0;
                    if (parts.length > 0) {
                        int minDiff = Integer.MAX_VALUE;
                        for (int i = 0; i < parts.length; i++) {
                            try {
                                int lVal = Integer.parseInt(parts[i].trim());
                                int diff = Math.abs(cur - lVal);
                                if (diff < minDiff) { minDiff = diff; closestIdx = i; }
                            } catch (Exception ignored) {}
                        }
                    }
                    showBrightnessHud(cur, closestIdx + 1, parts.length > 0 ? parts.length : 1);
                }
                break;
            }
            case "ACTION_REORDER_OVERLAYS": reorderOverlaysOnTop(); break;
            case "ACTION_UPDATE_SCHEDULED_SLEEP": ScheduledSleepReceiver.scheduleNextAlarm(this); break;
            case "ACTION_SCHEDULED_POWER_OFF":
                SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                int promptSec = op.getInt("scheduled_sleep_prompt_sec", 60);
                if (promptSec > 0) {
                    showScheduledSleepPrompt(promptSec);
                } else {
                    performPowerOffOrSleep();
                }
                break;
            case "ACTION_PAUSE_SCREEN_OFF":
            case "ACTION_PAUSE_AND_SCREEN_OFF": pauseMediaAndBlackScreen(); break;
            case "ACTION_TEST_MINDFUL_DELAY":
                showMindfulDelayOverlay("YouTube (Prueba)", "test", 10);
                break;
            case "ACTION_TRIGGER_TRANSLATE":
            case "ACTION_TRANSLATE_SCREEN":
                triggerScreenTranslation();
                break;
            case "ACTION_UPDATE_MINDFUL_DELAY":
                // Preference state refreshed
                break;
            case "ACTION_OPEN_RECENTS":
                handler.postDelayed(new Runnable() {
                    @Override public void run() { performGlobalAction(GLOBAL_ACTION_RECENTS); }
                }, 300);
                break;
        }
    }

    public void reorderOverlaysOnTop() {
        // Obsolete: Filter overlays remain attached continuously to prevent full-screen brightness flashes.
        // QuickMenuOverlay handles internal menu dimming via menu_dimmer_filter and menu_blue_light_filter.
    }

    private final Runnable inputLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isInputPressed) {
                Log.d(TAG, "Input long press detected! Opening Bluetooth settings...");
                isInputLongPressTriggered = true;
                openBluetoothSettings();
            }
        }
    };

    private void executeAction(int actionId) {
        Log.d(TAG, "Executing action ID: " + actionId);
        switch (actionId) {
            case 0: // Ninguna
                break;
            case 1: // Silenciar Audio
                if (audioManager != null) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                }
                break;
            case 2: // Escala de Grises (B/N)
                ToggleUtils.toggleGrayscale(getApplicationContext());
                break;
            case 3: // Apagar Pantalla (Negro)
                showBlackScreen();
                break;
            case 4: // Google Home Panel
                openGoogleHome();
                break;
            case 5: // YouTube
                launchYouTube();
                break;
            case 6: // Netflix
                launchNetflix();
                break;
            case 7: // Auriculares Bluetooth
                openBluetoothSettings();
                break;
            case 8: // Menú de Acciones
                openQuickMenu();
                break;
            case 9: // Filtro Luz Azul
                toggleBlueLight();
                break;
            case 10: // Reloj en Pantalla
                toggleClock();
                break;
            case 11: // Dimmer de Pantalla
                toggleDimmer();
                break;
            case 12: // Modo Cine
                toggleCineMode();
                break;
            case 13: // Info del Sistema
                showSystemInfoOverlay();
                break;
            case 14: // Reiniciar Chromecast
                rebootDevice();
                break;
            case 15: // Ajustes del Sistema
                openSystemSettings();
                break;
            case 18: // Pausar y Apagar Pantalla
                pauseMediaAndBlackScreen();
                break;
            case 19: // Bajar Brillo (Dimmer)
                adjustBrightness(-10);
                break;
            case 20: // Subir Brillo (Dimmer)
                adjustBrightness(10);
                break;
            case 21: // Ciclar Brillo
                cycleBrightness();
                break;
            case 22: // ¿Sigues viendo? (Inactividad)
                toggleStillWatching();
                break;
            case 23: // Traducir Pantalla (CTS)
                triggerScreenTranslation();
                break;
            case 24: // Control Cuadro por Cuadro (HUD)
                toggleFrameStepHud();
                break;
            case 25: // Avanzar 1 Frame (YouTube)
                stepVideoFrame(1);
                break;
            case 26: // Retroceder 1 Frame (YouTube)
                stepVideoFrame(-1);
                break;
            case 27: // Opciones de Desarrollador
                launchDeveloperOptions();
                break;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int eventType = event.getEventType();
        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq != null) {
            String pkg = pkgSeq.toString();
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                handleForegroundPackageChanged(pkg);
            }
            if (pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("smarttube") || pkg.contains("disney")) {
                Log.d(TAG, "Streaming AccEvent: pkg=" + pkg + ", type=0x" + Integer.toHexString(eventType) 
                        + ", class=" + event.getClassName() + ", text=" + event.getText() 
                        + ", desc=" + event.getContentDescription() 
                        + ", items=" + event.getCurrentItemIndex() + "/" + event.getItemCount());
                
                // Auto-detect playlist count from event text/desc
                if (event.getText() != null) {
                    for (CharSequence cs : event.getText()) {
                        if (cs != null) {
                            String str = cs.toString();
                            java.util.regex.Matcher mVids = java.util.regex.Pattern.compile("(\\d+)\\s+(videos?|elementos?)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(str);
                            if (mVids.find()) {
                                try {
                                    int total = Integer.parseInt(mVids.group(1));
                                    if (total > 0 && total <= 500) {
                                        getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putInt("auto_pause_playlist_count", total).apply();
                                        Log.d(TAG, "Auto-detected playlist count from event text: " + total);
                                    }
                                } catch (Exception ignored) {}
                            }
                            java.util.regex.Matcher mIdx = java.util.regex.Pattern.compile("(\\d+)\\s*(/|de|of)\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(str);
                            if (mIdx.find()) {
                                try {
                                    int total = Integer.parseInt(mIdx.group(3));
                                    if (total > 0 && total <= 500) {
                                        getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putInt("auto_pause_playlist_count", total).apply();
                                        Log.d(TAG, "Auto-detected playlist count from index text: " + total);
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                // Trigger auto pause check immediately on relevant streaming changes
                if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED 
                        || eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        || eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
                        || eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                    checkAutoPause();
                }
            }
        }
    }

    private void handleForegroundPackageChanged(String pkg) {
        if (pkg == null) return;
        // Never clear sessions or trigger mindful delay for our own menu or system overlays
        if (pkg.equals("com.example.togglegrayscale") || pkg.equals("com.android.systemui") || pkg.equals("com.google.android.inputmethod.latin")) {
            return;
        }

        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean enabled = op.getBoolean(KEY_MINDFUL_DELAY, false);
        if (!enabled) return;

        // If user returns to launcher, and session mode is "Solo mientras no salga a Home" (1), clear sessions
        if (isLauncherPackage(pkg)) {
            int sessionMode = op.getInt("mindful_delay_session_mode", 0);
            if (sessionMode == 1 && !authorizedSessions.isEmpty()) {
                authorizedSessions.clear();
                Log.d(TAG, "User returned to Home/Launcher. Cleared mindful sessions.");
            }
            return;
        }

        // Match package against monitored streaming apps
        for (String[] appDef : MINDFUL_APPS) {
            String appKey = appDef[0];
            String appName = appDef[1];
            String prefKey = appDef[2];
            boolean isAppMonitored = op.getBoolean(prefKey, (appKey.equals("youtube") || appKey.equals("netflix") || appKey.equals("disney")));
            if (!isAppMonitored) continue;

            boolean pkgMatches = false;
            for (int i = 3; i < appDef.length; i++) {
                if (pkg.equalsIgnoreCase(appDef[i]) || pkg.contains(appDef[i])) {
                    pkgMatches = true;
                    break;
                }
            }

            if (pkgMatches) {
                if (isAppSessionAuthorized(appKey)) {
                    Log.d(TAG, "Mindful Delay: App " + appName + " is already authorized.");
                    return;
                }
                if (isMindfulDelayActive && appKey.equals(currentMindfulAppKey)) {
                    return;
                }
                int secs = op.getInt("mindful_delay_seconds", 60);
                showMindfulDelayOverlay(appName, appKey, secs);
                break;
            }
        }
    }

    private boolean isAppSessionAuthorized(String appKey) {
        if (appKey == null) return false;
        Long expiry = authorizedSessions.get(appKey);
        if (expiry == null) return false;
        return System.currentTimeMillis() < expiry;
    }

    private void grantAppSession(String appKey) {
        if (appKey == null) return;
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int mode = op.getInt("mindful_delay_session_mode", 0);
        long durationMs;
        if (mode == 0) { // Por Tiempo Personalizado
            int hours = op.getInt("mindful_delay_session_hours", 0);
            int mins = op.getInt("mindful_delay_session_mins", 30);
            int totalMins = hours * 60 + mins;
            if (totalMins < 1) totalMins = 1;
            durationMs = totalMins * 60L * 1000L;
        } else if (mode == 1) { // Solo mientras no salga a Home
            durationMs = 24L * 3600L * 1000L;
        } else if (mode == 2) { // Hasta apagar pantalla / TV
            durationMs = 24L * 3600L * 1000L;
        } else { // Todo el día
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            durationMs = Math.max(60000L, cal.getTimeInMillis() - System.currentTimeMillis());
        }
        authorizedSessions.put(appKey, System.currentTimeMillis() + durationMs);
        Log.d(TAG, "Granted mindful session for " + appKey + ", mode=" + mode + ", durationMs=" + durationMs);
    }

    private void showMindfulDelayOverlay(final String appName, final String appKey, final int durationSeconds) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    dismissMindfulDelayOverlay();
                    WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (windowManager == null) return;

                    SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    int posIdx = op.getInt("mindful_delay_pos_idx", 0); // 0 = Centro
                    int msgIdx = op.getInt("mindful_delay_msg_idx", 0);
                    int bgAlphaPct = op.getInt("mindful_delay_bg_alpha_pct", 90);
                    int textSizeSp = op.getInt("mindful_delay_text_size_sp", 16);
                    int paddingDp = op.getInt("mindful_delay_pad_dp", 16);
                    int posX = op.getInt("mindful_delay_pos_x_dp", 0);
                    int posY = op.getInt("mindful_delay_pos_y_dp", 0);

                    currentMindfulAppKey = appKey;
                    currentMindfulAppName = appName;
                    mindfulRemainingSeconds = durationSeconds;

                    // Root container
                    android.widget.FrameLayout root = new android.widget.FrameLayout(ButtonMappingService.this);
                    root.setFocusable(true);
                    root.setClickable(true);

                    // Inner card
                    android.widget.LinearLayout card = new android.widget.LinearLayout(ButtonMappingService.this);
                    card.setOrientation(android.widget.LinearLayout.VERTICAL);
                    card.setGravity(Gravity.CENTER_HORIZONTAL);
                    
                    float density = getResources().getDisplayMetrics().density;
                    int padPx = Math.round(paddingDp * density);
                    card.setPadding(padPx + 20, padPx + 16, padPx + 20, padPx + 18);

                    // Card background with rounded corners & alpha
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setCornerRadius(16 * density);
                    int alpha = Math.round((bgAlphaPct / 100f) * 255);
                    gd.setColor(Color.argb(alpha, 20, 24, 33));
                    gd.setStroke(Math.round(1.5f * density), Color.argb(180, 129, 212, 250)); // Light blue subtle border
                    card.setBackground(gd);

                    // Title
                    TextView txtTitle = new TextView(ButtonMappingService.this);
                    txtTitle.setText("⏳  Espera Consciente");
                    txtTitle.setTextColor(0xFF81D4FA);
                    txtTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp + 2);
                    txtTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    txtTitle.setGravity(Gravity.CENTER);
                    card.addView(txtTitle);

                    // App target
                    txtMindfulDelayAppName = new TextView(ButtonMappingService.this);
                    txtMindfulDelayAppName.setText("Accediendo a: " + appName);
                    txtMindfulDelayAppName.setTextColor(0xFFE0E0E0);
                    txtMindfulDelayAppName.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                    txtMindfulDelayAppName.setGravity(Gravity.CENTER);
                    android.widget.LinearLayout.LayoutParams lpApp = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    lpApp.topMargin = Math.round(6 * density);
                    card.addView(txtMindfulDelayAppName, lpApp);

                    // Countdown timer
                    txtMindfulDelayTimer = new TextView(ButtonMappingService.this);
                    txtMindfulDelayTimer.setText(formatMindfulTime(mindfulRemainingSeconds));
                    txtMindfulDelayTimer.setTextColor(0xFFFFD54F); // Warm amber
                    txtMindfulDelayTimer.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp + 18);
                    txtMindfulDelayTimer.setTypeface(Typeface.DEFAULT_BOLD);
                    txtMindfulDelayTimer.setGravity(Gravity.CENTER);
                    android.widget.LinearLayout.LayoutParams lpTimer = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    lpTimer.topMargin = Math.round(10 * density);
                    lpTimer.bottomMargin = Math.round(10 * density);
                    card.addView(txtMindfulDelayTimer, lpTimer);

                    // Motivator message
                    txtMindfulDelayMsg = new TextView(ButtonMappingService.this);
                    String msg = (msgIdx >= 0 && msgIdx < MINDFUL_MSG_OPTIONS.length) ? MINDFUL_MSG_OPTIONS[msgIdx] : MINDFUL_MSG_OPTIONS[0];
                    txtMindfulDelayMsg.setText(msg);
                    txtMindfulDelayMsg.setTextColor(0xFFB0BEC5);
                    txtMindfulDelayMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp - 2);
                    txtMindfulDelayMsg.setGravity(Gravity.CENTER);
                    card.addView(txtMindfulDelayMsg);

                    // Action hint button
                    TextView txtHint = new TextView(ButtonMappingService.this);
                    txtHint.setText("← Presioná ATRÁS para volver a Home");
                    txtHint.setTextColor(0xFFFF8A80); // Soft red/coral
                    txtHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp - 1);
                    txtHint.setTypeface(Typeface.DEFAULT_BOLD);
                    txtHint.setGravity(Gravity.CENTER);
                    android.widget.LinearLayout.LayoutParams lpHint = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    lpHint.topMargin = Math.round(14 * density);
                    card.addView(txtHint, lpHint);

                    // Add card to root
                    android.widget.FrameLayout.LayoutParams lpCard = new android.widget.FrameLayout.LayoutParams(
                            Math.round(420 * density), android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
                    
                    int gravity = Gravity.CENTER;
                    if (posIdx == 1) gravity = Gravity.TOP | Gravity.START;
                    else if (posIdx == 2) gravity = Gravity.TOP | Gravity.END;
                    else if (posIdx == 3) gravity = Gravity.BOTTOM | Gravity.START;
                    else if (posIdx == 4) gravity = Gravity.BOTTOM | Gravity.END;

                    lpCard.gravity = gravity;
                    lpCard.leftMargin = Math.round(posX * density) + Math.round(24 * density);
                    lpCard.rightMargin = Math.round(posX * density) + Math.round(24 * density);
                    lpCard.topMargin = Math.round(posY * density) + Math.round(24 * density);
                    lpCard.bottomMargin = Math.round(posY * density) + Math.round(24 * density);
                    root.addView(card, lpCard);

                    // Window layout params
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                    );

                    windowManager.addView(root, params);
                    mindfulDelayOverlayView = root;
                    isMindfulDelayActive = true;

                    // Immediately pause background video/audio playback and repeat
                    sendMediaPause();
                    handler.postDelayed(new Runnable() { @Override public void run() { if (isMindfulDelayActive) sendMediaPause(); } }, 250);
                    handler.postDelayed(new Runnable() { @Override public void run() { if (isMindfulDelayActive) sendMediaPause(); } }, 600);
                    handler.postDelayed(new Runnable() { @Override public void run() { if (isMindfulDelayActive) sendMediaPause(); } }, 1200);

                    // Start countdown loop
                    mindfulCountdownRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (!isMindfulDelayActive) return;
                            if (audioManager != null && audioManager.isMusicActive()) {
                                sendMediaPause();
                            }
                            mindfulRemainingSeconds--;
                            if (txtMindfulDelayTimer != null) {
                                txtMindfulDelayTimer.setText(formatMindfulTime(mindfulRemainingSeconds));
                            }
                            if (mindfulRemainingSeconds <= 0) {
                                grantAppSession(currentMindfulAppKey);
                                dismissMindfulDelayOverlay();
                                Toast.makeText(getApplicationContext(), "✓ Acceso autorizado a " + appName, Toast.LENGTH_SHORT).show();
                            } else {
                                handler.postDelayed(this, 1000);
                            }
                        }
                    };
                    handler.postDelayed(mindfulCountdownRunnable, 1000);

                    Log.d(TAG, "Mindful Delay Overlay displayed for " + appName + " (" + durationSeconds + "s)");
                } catch (Exception e) {
                    Log.e(TAG, "Error displaying Mindful Delay Overlay", e);
                }
            }
        });
    }

    private String formatMindfulTime(int totalSecs) {
        if (totalSecs < 0) totalSecs = 0;
        int m = totalSecs / 60;
        int s = totalSecs % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void cancelMindfulDelayAndGoHome() {
        Log.d(TAG, "User cancelled mindful wait. Executing cancel action.");
        dismissMindfulDelayOverlay();
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int cancelAction = op.getInt("mindful_delay_cancel_action", 0);
        if (cancelAction == 0) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        } else if (cancelAction == 1) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        } else if (cancelAction == 2) {
            pauseMediaAndBlackScreen();
        }
    }

    private void dismissMindfulDelayOverlay() {
        if (mindfulCountdownRunnable != null) {
            handler.removeCallbacks(mindfulCountdownRunnable);
            mindfulCountdownRunnable = null;
        }
        if (!isMindfulDelayActive || mindfulDelayOverlayView == null) {
            isMindfulDelayActive = false;
            return;
        }
        final View v = mindfulDelayOverlayView;
        mindfulDelayOverlayView = null;
        isMindfulDelayActive = false;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm != null && v != null) wm.removeView(v);
                    Log.d(TAG, "Mindful Delay overlay dismissed.");
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        dismissBlackScreen();
        hideBlueLightOverlay();
        hideClockOverlay();
        hideBrightnessHudOverlay();
        hideDimmerOverlay();
        dismissSystemInfoOverlay();
        dismissMindfulDelayOverlay();
        stopStillWatchingTimer();
        stopOledSaverTimer();
        try {
            unregisterReceiver(screenStateReceiver);
        } catch (Exception ignored) {}
        handler.removeCallbacks(nightScheduleCheckRunnable);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showBlackScreen() {
        if (isBlackScreenActive) return;
        dismissStillWatchingPrompt();

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (windowManager == null) return;

                    blackOverlayView = new View(ButtonMappingService.this);
                    blackOverlayView.setBackgroundColor(Color.BLACK);

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                    | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                            PixelFormat.OPAQUE
                    );

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    }

                    windowManager.addView(blackOverlayView, params);
                    isBlackScreenActive = true;
                    Log.d(TAG, "Black screen overlay added successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Error showing black screen overlay", e);
                }
            }
        });
    }

    private void dismissBlackScreen() {
        if (!isBlackScreenActive || blackOverlayView == null) return;

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (windowManager != null && blackOverlayView != null) {
                        windowManager.removeView(blackOverlayView);
                    }
                    Log.d(TAG, "Black screen overlay removed successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Error dismissing black screen overlay", e);
                } finally {
                    blackOverlayView = null;
                    isBlackScreenActive = false;
                }
            }
        });
    }

    private void pauseMediaAndBlackScreen() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PAUSE));
                Log.d(TAG, "Sent KEYCODE_MEDIA_PAUSE to system");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to dispatch media pause key event", e);
        }
        showBlackScreen();
    }

    private void showScheduledSleepPrompt(final int seconds) {
        if (isScheduledSleepPromptActive) return;
        isScheduledSleepPromptActive = true;
        scheduledSleepCountdownSeconds = seconds;

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) return;
                    dismissScheduledSleepPromptViewOnly();

                    TextView tv = new TextView(ButtonMappingService.this);
                    tv.setTextColor(Color.WHITE);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                    tv.setPadding(48, 36, 48, 36);
                    tv.setGravity(Gravity.CENTER);
                    tv.setBackgroundColor(Color.argb(235, 18, 18, 32));
                    tv.setLineSpacing(6, 1.0f);
                    scheduledSleepOverlayView = tv;
                    updateScheduledSleepPromptText();

                    WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                            PixelFormat.TRANSLUCENT
                    );
                    p.gravity = Gravity.CENTER;

                    wm.addView(scheduledSleepOverlayView, p);

                    scheduledSleepCountdownRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (!isScheduledSleepPromptActive) return;
                            scheduledSleepCountdownSeconds--;
                            if (scheduledSleepCountdownSeconds <= 0) {
                                dismissScheduledSleepPrompt();
                                performPowerOffOrSleep();
                            } else {
                                updateScheduledSleepPromptText();
                                handler.postDelayed(this, 1000);
                            }
                        }
                    };
                    handler.postDelayed(scheduledSleepCountdownRunnable, 1000);
                    Log.d(TAG, "Scheduled sleep prompt shown with " + seconds + "s countdown");
                } catch (Exception e) {
                    Log.e(TAG, "Error showing scheduled sleep prompt", e);
                }
            }
        });
    }

    private void updateScheduledSleepPromptText() {
        if (scheduledSleepOverlayView instanceof TextView) {
            TextView tv = (TextView) scheduledSleepOverlayView;
            tv.setText("⏰  Apagado Programado\n\nEl dispositivo se apagará en " + scheduledSleepCountdownSeconds + " s\n\nPresiona cualquier botón del control para cancelar");
        }
    }

    private void dismissScheduledSleepPromptViewOnly() {
        if (scheduledSleepOverlayView != null) {
            final View v = scheduledSleepOverlayView;
            scheduledSleepOverlayView = null;
            try {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (wm != null) wm.removeView(v);
            } catch (Exception ignored) {}
        }
    }

    private void dismissScheduledSleepPrompt() {
        isScheduledSleepPromptActive = false;
        if (scheduledSleepCountdownRunnable != null) {
            handler.removeCallbacks(scheduledSleepCountdownRunnable);
            scheduledSleepCountdownRunnable = null;
        }
        dismissScheduledSleepPromptViewOnly();
    }

    private void performPowerOffOrSleep() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Executing Power Off / Standby: Pausing media and triggering system power off...");
                try {
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    if (am != null) {
                        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PAUSE));
                    }
                } catch (Exception ignored) {}

                // Attempt system standby power off
                boolean success = false;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); // 8 = Standby/Sleep
                    Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) result: " + success);
                }

                if (!success) {
                    try {
                        Runtime.getRuntime().exec("input keyevent 26"); // KEYCODE_POWER
                        Log.d(TAG, "Executed 'input keyevent 26' via Runtime");
                    } catch (Exception e) {
                        Log.w(TAG, "Failed input keyevent 26, falling back to GLOBAL_ACTION_POWER_DIALOG", e);
                        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
                    }
                }
            }
        });
    }

    private void launchNetflix() {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.netflix.ninja");
            if (launchIntent == null) {
                launchIntent = getPackageManager().getLaunchIntentForPackage("com.netflix.mediaclient");
            }
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                Log.d(TAG, "Successfully started Netflix");
            } else {
                Log.w(TAG, "Netflix launch intent not found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch Netflix", e);
        }
    }

    private void launchYouTube() {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube.tv");
            if (launchIntent == null) {
                launchIntent = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
            }
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                Log.d(TAG, "Successfully started YouTube");
            } else {
                Log.w(TAG, "YouTube launch intent not found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch YouTube", e);
        }
    }

    private void openGoogleHome() {
        try {
            Intent intent = new Intent("com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Successfully opened Google Home Panel");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open Google Home Panel", e);
        }
    }

    private void openQuickMenu() {
        try {
            QuickMenuOverlay.getInstance().toggle(this);
            Log.d(TAG, "Toggled Quick Menu Overlay");
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle Quick Menu Overlay", e);
        }
    }

    private void openBluetoothSettings() {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.android.tv.settings", "com.android.tv.settings.accessories.AccessoriesActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Successfully opened Bluetooth Settings (AccessoriesActivity)");
        } catch (Exception e0) {
            Log.w(TAG, "Failed AccessoriesActivity, trying AddAccessoryActivity...", e0);
            try {
                Intent intent = new Intent();
                intent.setClassName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d(TAG, "Successfully opened Bluetooth Settings (Add Accessory)");
            } catch (Exception e1) {
                Log.w(TAG, "Failed to open AddAccessoryActivity, trying general Bluetooth settings...", e1);
                try {
                    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Log.d(TAG, "Successfully opened Bluetooth Settings (Standard)");
                } catch (Exception e2) {
                    Log.w(TAG, "Failed standard Bluetooth settings, trying TV Settings activity...", e2);
                    try {
                        Intent intent = new Intent();
                        intent.setClassName("com.android.tv.settings", "com.android.tv.settings.connectivity.setup.ConnectBluetoothActivity");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        Log.d(TAG, "Successfully opened Bluetooth Settings (ConnectBluetoothActivity)");
                    } catch (Exception e3) {
                        Log.w(TAG, "Failed ConnectBluetoothActivity, trying general settings...", e3);
                        try {
                            Intent intent = new Intent(Settings.ACTION_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            Log.d(TAG, "Successfully opened General Settings");
                        } catch (Exception e4) {
                            Log.e(TAG, "Failed to open any settings activity", e4);
                        }
                    }
                }
            }
        }
    }

    // ── Overlay helpers ──────────────────────────────────────────────────────

    private WindowManager.LayoutParams overlayMatchParams() {
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
    }

    // ── Blue light filter ────────────────────────────────────────────────────

    private void toggleBlueLight() {
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean active = op.getBoolean(KEY_BLUE_LIGHT, false);
        if (active) {
            setBlueLightPct(0);
        } else {
            int lastPct = op.getInt("blue_light_pct", 50);
            if (lastPct == 0) lastPct = 50;
            setBlueLightPct(lastPct);
        }
    }

    private void setBlueLightLevel(int level) {
        // Compatibilidad para atrás
        int pct = level == 1 ? 200 : level == 2 ? 450 : level == 3 ? 700 : 0;
        setBlueLightPct(pct);
    }

    private void setBlueLightPct(int pct) {
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        if (pct > 0) {
            op.edit().putInt("blue_light_pct", pct).apply();
        }
        if (pct == 0) {
            op.edit().putBoolean(KEY_BLUE_LIGHT, false).apply();
            hideBlueLightOverlay();
        } else {
            op.edit().putBoolean(KEY_BLUE_LIGHT, true).apply();
            showBlueLightOverlayPct(pct);
        }
    }

    private void showBlueLightOverlayPct(final int pct) {
        isBlueLightActive = true;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) { isBlueLightActive = false; return; }
                    
                    // Calculamos alpha basado en % de 1000 (máximo 150 para que no tape completamente la pantalla)
                    int alpha = (int) ((pct / 1000.0f) * 150);
                    if (alpha < 1) alpha = 1;
                    
                    if (blueLightOverlayView != null) {
                        blueLightOverlayView.setBackgroundColor(Color.argb(alpha, 240, 120, 0));
                    } else {
                        blueLightOverlayView = new View(ButtonMappingService.this);
                        blueLightOverlayView.setBackgroundColor(Color.argb(alpha, 240, 120, 0)); // Tono naranja cálido y suave
                        wm.addView(blueLightOverlayView, overlayMatchParams());
                    }
                    blueLightShowRetries = 0; // Reset retries on success
                    getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_BLUE_LIGHT, true).apply();
                    Log.d(TAG, "Blue light overlay shown pct: " + pct + " (alpha=" + alpha + ")");
                } catch (Exception e) {
                    isBlueLightActive = false;
                    Log.e(TAG, "Error showing blue light overlay", e);
                    if (blueLightShowRetries < 5) {
                        blueLightShowRetries++;
                        Log.w(TAG, "Retrying to show blue light in 1s (attempt " + blueLightShowRetries + ")");
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                  showBlueLightOverlayPct(pct);
                            }
                        }, 1000);
                    }
                }
            }
        });
    }

    private void showBlueLightOverlay() {
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int pct = op.getInt("blue_light_pct", 50);
        showBlueLightOverlayPct(pct == 0 ? 50 : pct);
    }

    private void hideBlueLightOverlay() {
        if (!isBlueLightActive || blueLightOverlayView == null) return;
        isBlueLightActive = false;
        final View v = blueLightOverlayView;
        blueLightOverlayView = null;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm != null) wm.removeView(v);
                    getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_BLUE_LIGHT, false).apply();
                } catch (Exception e) { Log.e(TAG, "Error hiding blue light overlay", e); }
            }
        });
    }

    // ── Clock overlay ────────────────────────────────────────────────────────

    private void toggleClock() {
        if (isClockActive) hideClockOverlay(); else showClockOverlay();
    }

    private void showClockOverlay() {
        if (isClockActive) return;
        isClockActive = true;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) { isClockActive = false; return; }

                    SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    int colorIdx = prefs.getInt("clock_text_color_idx", 0);
                    int bgIdx = prefs.getInt("clock_bg_color_idx", 0);
                    int alphaPct = prefs.getInt("clock_bg_alpha_pct", 1);
                    int textAlphaPct = prefs.getInt("clock_text_alpha_pct", 100);
                    int posIdx = prefs.getInt("clock_position_idx", 0);

                    int sizeSp = prefs.getInt("clock_size_sp", 10);
                    int paddingDp = prefs.getInt("clock_padding_dp", 3);
                    int xDp = prefs.getInt("clock_pos_x_dp", 16);
                    int yDp = prefs.getInt("clock_pos_y_dp", 16);

                    int[] textColors = {0xFFFFFFFF, 0xFF000000, 0xFFFFFF00, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF};
                    int rawColor = textColors[colorIdx >= 0 && colorIdx < textColors.length ? colorIdx : 0];
                    int textAlphaVal = (int) (textAlphaPct * 2.55);
                    int textColor = (rawColor & 0x00FFFFFF) | (textAlphaVal << 24);

                    int[][] bgRGBs = {
                        {0, 0, 0},
                        {80, 80, 80},
                        {15, 15, 40}
                    };

                    int bgColor;
                    if (bgIdx == 3 || alphaPct == 0) {
                        bgColor = Color.TRANSPARENT;
                    } else {
                        int alphaVal = (int) (alphaPct * 2.55);
                        int[] rgb = bgRGBs[bgIdx >= 0 && bgIdx < bgRGBs.length ? bgIdx : 0];
                        bgColor = Color.argb(alphaVal, rgb[0], rgb[1], rgb[2]);
                    }

                    int[] positions = {
                        Gravity.TOP | Gravity.END,
                        Gravity.TOP | Gravity.START,
                        Gravity.BOTTOM | Gravity.END,
                        Gravity.BOTTOM | Gravity.START,
                        Gravity.CENTER
                    };
                    int gravity = positions[posIdx >= 0 && posIdx < positions.length ? posIdx : 0];

                    android.util.DisplayMetrics dm = getResources().getDisplayMetrics();

                    // Convert padding, x offset, and y offset from dp to pixels
                    int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, paddingDp, dm);
                    int paddingPxHalf = paddingPx / 2;

                    int offsetX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, xDp, dm);
                    int offsetY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, yDp, dm);

                    TextView tv = new TextView(ButtonMappingService.this);
                    tv.setTextColor(textColor);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                    tv.setPadding(paddingPx, paddingPxHalf, paddingPx, paddingPxHalf);
                    tv.setBackgroundColor(bgColor);

                    clockTextView = tv;
                    clockOverlayView = tv;
                    updateClockText();

                    WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                            PixelFormat.TRANSLUCENT
                    );
                    p.gravity = gravity;
                    p.x = offsetX;
                    p.y = offsetY;

                    wm.addView(clockOverlayView, p);

                    if (isDimmerActive && dimmerOverlayView != null) {
                        try {
                            wm.removeView(dimmerOverlayView);
                        } catch (Exception ignored) {}
                        try {
                            wm.addView(dimmerOverlayView, overlayMatchParams());
                        } catch (Exception ignored) {}
                    }

                    clockShowRetries = 0; // Reset retries on success
                    getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_CLOCK, true).apply();
                    handler.postDelayed(clockUpdateRunnable, 1000);
                    Log.d(TAG, "Clock overlay shown");
                } catch (Exception e) {
                    isClockActive = false;
                    Log.e(TAG, "Error showing clock overlay", e);
                    if (clockShowRetries < 5) {
                        clockShowRetries++;
                        Log.w(TAG, "Retrying to show clock in 1s (attempt " + clockShowRetries + ")");
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                showClockOverlay();
                            }
                        }, 1000);
                    }
                }
            }
        });
    }

    private void hideClockOverlay() {
        if (!isClockActive || clockOverlayView == null) return;
        isClockActive = false;
        handler.removeCallbacks(clockUpdateRunnable);
        final View v = clockOverlayView;
        clockOverlayView = null;
        clockTextView = null;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm != null) wm.removeView(v);
                    getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_CLOCK, false).apply();
                } catch (Exception e) { Log.e(TAG, "Error hiding clock overlay", e); }
            }
        });
    }

    private void updateClockText() {
        if (clockTextView == null) return;
        clockTextView.setText(DateFormat.getTimeFormat(this).format(new Date()));
    }

    // ── Dimmer overlay ───────────────────────────────────────────────────────

    private void toggleDimmer() {
        if (isDimmerActive) hideDimmerOverlay(); else showDimmerOverlay();
    }

    private void showDimmerOverlay() {
        if (isDimmerActive) return;
        isDimmerActive = true;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) { isDimmerActive = false; return; }
                    
                    SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    int brightnessPct = prefs.getInt("dimmer_brightness_pct", 50);
                    int alphaVal = (int) ((100 - brightnessPct) * 2.55);

                    dimmerOverlayView = new View(ButtonMappingService.this);
                    dimmerOverlayView.setBackgroundColor(Color.argb(alphaVal, 0, 0, 0));
                    wm.addView(dimmerOverlayView, overlayMatchParams());
                    dimmerShowRetries = 0; // Reset retries on success
                    getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DIMMER, true).apply();
                    Log.d(TAG, "Dimmer overlay shown at " + brightnessPct + "% brightness");
                } catch (Exception e) {
                    isDimmerActive = false;
                    Log.e(TAG, "Error showing dimmer overlay", e);
                    if (dimmerShowRetries < 5) {
                        dimmerShowRetries++;
                        Log.w(TAG, "Retrying to show dimmer in 1s (attempt " + dimmerShowRetries + ")");
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                showDimmerOverlay();
                            }
                        }, 1000);
                    }
                }
            }
        });
    }

    public void setDimmerBrightness(final int pct) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                prefs.edit().putInt("dimmer_brightness_pct", pct).apply();
                if (isDimmerActive && dimmerOverlayView != null) {
                    int alphaVal = (int) ((100 - pct) * 2.55);
                    dimmerOverlayView.setBackgroundColor(Color.argb(alphaVal, 0, 0, 0));
                } else if (!isDimmerActive) {
                    showDimmerOverlay();
                }
            }
        });
    }

    private void adjustBrightness(final int delta) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                int cur = prefs.getInt("dimmer_brightness_pct", 50);
                int next = cur + delta;
                if (next < 0) next = 0;
                if (next > 100) next = 100;
                prefs.edit().putInt("dimmer_brightness_pct", next).apply();
                Log.d(TAG, "Adjusted brightness to: " + next + "%");
                if (isDimmerActive && dimmerOverlayView != null) {
                    int alphaVal = (int) ((100 - next) * 2.55);
                    dimmerOverlayView.setBackgroundColor(Color.argb(alphaVal, 0, 0, 0));
                } else {
                    showDimmerOverlay();
                }

                String levelsStr = prefs.getString("brightness_levels_list", "80,50,20");
                String[] parts = levelsStr.split(",");
                int closestIdx = 0;
                int totalLevels = parts.length;
                if (totalLevels > 0) {
                    int minDiff = Integer.MAX_VALUE;
                    for (int i = 0; i < parts.length; i++) {
                        try {
                            int lVal = Integer.parseInt(parts[i].trim());
                            int diff = Math.abs(next - lVal);
                            if (diff < minDiff) {
                                minDiff = diff;
                                closestIdx = i;
                            }
                        } catch (Exception ignored) {}
                    }
                }
                showBrightnessHud(next, closestIdx + 1, totalLevels > 0 ? totalLevels : 1);
            }
        });
    }

    private void cycleBrightness() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                int cur = prefs.getInt("dimmer_brightness_pct", 50);
                String levelsStr = prefs.getString("brightness_levels_list", "80,50,20");
                
                String[] parts = levelsStr.split(",");
                if (parts.length == 0) return;
                
                int[] levels = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    try {
                        levels[i] = Integer.parseInt(parts[i].trim());
                    } catch (Exception e) {
                        levels[i] = 50;
                    }
                }
                
                int closestIdx = 0;
                int minDiff = Math.abs(cur - levels[0]);
                for (int i = 1; i < levels.length; i++) {
                    int diff = Math.abs(cur - levels[i]);
                    if (diff < minDiff) {
                        minDiff = diff;
                        closestIdx = i;
                    }
                }
                
                int nextIdx = (closestIdx + 1) % levels.length;
                int next = levels[nextIdx];
                
                prefs.edit().putInt("dimmer_brightness_pct", next).apply();
                Log.d(TAG, "Cycled brightness from " + cur + "% to " + next + "%");
                if (isDimmerActive && dimmerOverlayView != null) {
                    int alphaVal = (int) ((100 - next) * 2.55);
                    dimmerOverlayView.setBackgroundColor(Color.argb(alphaVal, 0, 0, 0));
                } else {
                    showDimmerOverlay();
                }
                showBrightnessHud(next, nextIdx + 1, levels.length);
            }
        });
    }

    // ── Brightness Indicator HUD overlay ──────────────────────────────────────

    public void showBrightnessHud(final int currentPct, final int currentLevelIdx, final int totalLevels) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    boolean enabled = prefs.getBoolean("brightness_hud_enabled", true);
                    if (!enabled) return;

                    handler.removeCallbacks(hideBrightnessHudRunnable);

                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) return;

                    int formatIdx = prefs.getInt("brightness_hud_format_idx", 0);
                    int colorIdx = prefs.getInt("brightness_hud_text_color_idx", 0);
                    int bgIdx = prefs.getInt("brightness_hud_bg_color_idx", 0);
                    int alphaPct = prefs.getInt("brightness_hud_bg_alpha_pct", 35);
                    int textAlphaPct = prefs.getInt("brightness_hud_text_alpha_pct", 100);
                    int posIdx = prefs.getInt("brightness_hud_position_idx", 0);

                    int sizeSp = prefs.getInt("brightness_hud_size_sp", 16);
                    int paddingDp = prefs.getInt("brightness_hud_padding_dp", 12);
                    int xDp = prefs.getInt("brightness_hud_pos_x_dp", 16);
                    int yDp = prefs.getInt("brightness_hud_pos_y_dp", 16);
                    int durationMs = prefs.getInt("brightness_hud_duration_ms", 2000);

                    String text;
                    int curLvl = Math.max(1, currentLevelIdx);
                    int totLvls = Math.max(1, totalLevels);
                    switch (formatIdx) {
                        case 1:
                            text = currentPct + "% Brillo (" + curLvl + "/" + totLvls + ")";
                            break;
                        case 2:
                            text = "Brillo " + currentPct + "% (" + curLvl + "/" + totLvls + ")";
                            break;
                        case 3:
                            text = "(" + curLvl + "/" + totLvls + ") " + currentPct + "%";
                            break;
                        case 4:
                            text = currentPct + "%";
                            break;
                        case 5:
                            text = "Nivel " + curLvl + "/" + totLvls + " (" + currentPct + "%)";
                            break;
                        case 0:
                        default:
                            text = currentPct + "% (" + curLvl + "/" + totLvls + ")";
                            break;
                    }

                    float brightnessFactor = Math.max(0.12f, currentPct / 100.0f);

                    int[] textColors = {0xFFFFFFFF, 0xFF000000, 0xFFFFFF00, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF};
                    int rawColor = textColors[colorIdx >= 0 && colorIdx < textColors.length ? colorIdx : 0];
                    int textAlphaVal = (int) (textAlphaPct * 2.55);

                    int r = Color.red(rawColor);
                    int g = Color.green(rawColor);
                    int b = Color.blue(rawColor);
                    if (isBlueLightActive) {
                        int bluePct = prefs.getInt("blue_light_pct", 50);
                        b = (int) (b * (1.0f - (bluePct / 100.0f) * 0.7f));
                    }
                    int dimmedR = Math.round(r * brightnessFactor);
                    int dimmedG = Math.round(g * brightnessFactor);
                    int dimmedB = Math.round(b * brightnessFactor);
                    int textColor = Color.argb(textAlphaVal, dimmedR, dimmedG, dimmedB);

                    int[][] bgRGBs = {
                        {0, 0, 0},
                        {80, 80, 80},
                        {15, 15, 40}
                    };

                    int bgColor;
                    if (bgIdx == 3 || alphaPct == 0) {
                        bgColor = Color.TRANSPARENT;
                    } else {
                        int alphaVal = (int) (alphaPct * 2.55);
                        int[] rgb = bgRGBs[bgIdx >= 0 && bgIdx < bgRGBs.length ? bgIdx : 0];
                        int bgR = Math.round(rgb[0] * brightnessFactor);
                        int bgG = Math.round(rgb[1] * brightnessFactor);
                        int bgB = Math.round(rgb[2] * brightnessFactor);
                        if (isBlueLightActive) {
                            int bluePct = prefs.getInt("blue_light_pct", 50);
                            bgB = (int) (bgB * (1.0f - (bluePct / 100.0f) * 0.7f));
                        }
                        bgColor = Color.argb(alphaVal, bgR, bgG, bgB);
                    }

                    int[] positions = {
                        Gravity.TOP | Gravity.END,
                        Gravity.TOP | Gravity.START,
                        Gravity.BOTTOM | Gravity.END,
                        Gravity.BOTTOM | Gravity.START,
                        Gravity.CENTER
                    };
                    int gravity = positions[posIdx >= 0 && posIdx < positions.length ? posIdx : 0];

                    android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                    int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, paddingDp, dm);
                    int paddingPxHalf = paddingPx / 2;
                    int offsetX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, xDp, dm);
                    int offsetY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, yDp, dm);

                    if (brightnessHudOverlayView == null) {
                        TextView tv = new TextView(ButtonMappingService.this);
                        tv.setText(text);
                        tv.setTextColor(textColor);
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
                        tv.setTypeface(Typeface.DEFAULT_BOLD);
                        tv.setPadding(paddingPx, paddingPxHalf, paddingPx, paddingPxHalf);
                        tv.setBackgroundColor(bgColor);

                        brightnessHudTextView = tv;
                        brightnessHudOverlayView = tv;

                        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                                PixelFormat.TRANSLUCENT
                        );
                        p.gravity = gravity;
                        p.x = offsetX;
                        p.y = offsetY;

                        wm.addView(brightnessHudOverlayView, p);
                    } else {
                        if (brightnessHudTextView != null) {
                            brightnessHudTextView.setText(text);
                            brightnessHudTextView.setTextColor(textColor);
                            brightnessHudTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
                            brightnessHudTextView.setPadding(paddingPx, paddingPxHalf, paddingPx, paddingPxHalf);
                            brightnessHudTextView.setBackgroundColor(bgColor);
                        }
                        WindowManager.LayoutParams p = (WindowManager.LayoutParams) brightnessHudOverlayView.getLayoutParams();
                        if (p != null) {
                            p.gravity = gravity;
                            p.x = offsetX;
                            p.y = offsetY;
                            wm.updateViewLayout(brightnessHudOverlayView, p);
                        }
                    }

                    handler.postDelayed(hideBrightnessHudRunnable, durationMs);
                } catch (Exception e) {
                    Log.e(TAG, "Error showing brightness HUD overlay", e);
                }
            }
        });
    }

    private void hideBrightnessHudOverlay() {
        if (brightnessHudOverlayView == null) return;
        final View v = brightnessHudOverlayView;
        brightnessHudOverlayView = null;
        brightnessHudTextView = null;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm != null) wm.removeView(v);
                } catch (Exception e) {
                    Log.e(TAG, "Error hiding brightness HUD overlay", e);
                }
            }
        });
    }

    private void hideDimmerOverlay() {
        if (!isDimmerActive || dimmerOverlayView == null) return;
        isDimmerActive = false;
        final View v = dimmerOverlayView;
        dimmerOverlayView = null;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm != null) wm.removeView(v);
                    getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DIMMER, false).apply();
                } catch (Exception e) { Log.e(TAG, "Error hiding dimmer overlay", e); }
            }
        });
    }

    // ── ¿Sigues viendo? (Inactividad) ───────────────────────────────────────

    private void toggleStillWatching() {
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean nextState = !prefs.getBoolean(KEY_STILL_WATCHING, false);
        prefs.edit().putBoolean(KEY_STILL_WATCHING, nextState).apply();
        if (nextState) {
            startStillWatchingTimer();
        } else {
            stopStillWatchingTimer();
        }
    }

    private void updateStillWatching() {
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_STILL_WATCHING, false);
        if (enabled) {
            startStillWatchingTimer();
        } else {
            stopStillWatchingTimer();
        }
    }

    private void startStillWatchingTimer() {
        stopStillWatchingTimer();
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int intervalMin = prefs.getInt(KEY_STILL_WATCHING_INTERVAL, 30);
        cachedStillWatchingIntervalMs = intervalMin * 60 * 1000L;
        isStillWatchingActive = true;
        handler.postDelayed(stillWatchingIntervalRunnable, cachedStillWatchingIntervalMs);
        Log.d(TAG, "Still watching timer started for " + intervalMin + " minutes.");
    }

    private void stopStillWatchingTimer() {
        isStillWatchingActive = false;
        handler.removeCallbacks(stillWatchingIntervalRunnable);
        dismissStillWatchingPrompt();
    }

    private void resetStillWatchingTimerOnKeyPress() {
        if (isStillWatchingPromptActive) {
            dismissStillWatchingPrompt();
        }
        if (isStillWatchingActive) {
            handler.removeCallbacks(stillWatchingIntervalRunnable);
            handler.postDelayed(stillWatchingIntervalRunnable, cachedStillWatchingIntervalMs);
        }
    }

    private void showStillWatchingPrompt() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) return;
                    dismissStillWatchingPrompt();

                    SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    stillWatchingCountdownSeconds = prefs.getInt(KEY_STILL_WATCHING_TIMEOUT, 30);
                    int pos = prefs.getInt(KEY_STILL_WATCHING_POS, 0);
                    int alpha = prefs.getInt(KEY_STILL_WATCHING_ALPHA, 85);
                    int textSizeSp = prefs.getInt(KEY_STILL_WATCHING_SIZE, 14);
                    int padDp = prefs.getInt(KEY_STILL_WATCHING_PAD, 14);
                    int offsetX = prefs.getInt(KEY_STILL_WATCHING_X, 16);
                    int offsetY = prefs.getInt(KEY_STILL_WATCHING_Y, 16);

                    float density = getResources().getDisplayMetrics().density;

                    TextView tv = new TextView(ButtonMappingService.this);
                    tv.setTextColor(Color.WHITE);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                    int padPxHorizontal = Math.round((padDp + 4) * density);
                    int padPxVertical = Math.round(padDp * density);
                    tv.setPadding(padPxHorizontal, padPxVertical, padPxHorizontal, padPxVertical);

                    int bgAlphaPx = (int) (alpha * 2.55);
                    tv.setBackgroundColor(Color.argb(bgAlphaPx, 20, 24, 33));

                    stillWatchingOverlayView = tv;
                    isStillWatchingPromptActive = true;

                    updateStillWatchingPromptText();

                    WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT
                    );

                    switch (pos) {
                        case 0: p.gravity = Gravity.TOP | Gravity.START; break;
                        case 1: p.gravity = Gravity.TOP | Gravity.END; break;
                        case 2: p.gravity = Gravity.BOTTOM | Gravity.START; break;
                        case 3: p.gravity = Gravity.BOTTOM | Gravity.END; break;
                        case 4: p.gravity = Gravity.CENTER; break;
                        default: p.gravity = Gravity.TOP | Gravity.START; break;
                    }

                    p.x = Math.round(offsetX * density);
                    p.y = Math.round(offsetY * density);

                    wm.addView(stillWatchingOverlayView, p);
                    handler.postDelayed(stillWatchingCountdownRunnable, 1000);

                    boolean beepEnabled = prefs.getBoolean(KEY_STILL_WATCHING_BEEP, true);
                    if (beepEnabled) {
                        int beepDelay = prefs.getInt(KEY_STILL_WATCHING_BEEP_DELAY, 9);
                        if (beepDelay <= 0) {
                            playStillWatchingBeep();
                            int beepInterval = prefs.getInt(KEY_STILL_WATCHING_BEEP_INTERVAL, 10);
                            if (beepInterval < 1) beepInterval = 1;
                            handler.postDelayed(stillWatchingBeepRunnable, beepInterval * 1000L);
                        } else {
                            handler.postDelayed(stillWatchingBeepRunnable, beepDelay * 1000L);
                        }
                    }

                    Log.d(TAG, "Still watching prompt shown");
                } catch (Exception e) { Log.e(TAG, "Error showing still watching prompt", e); }
            }
        });
    }

    private void playStillWatchingBeep() {
        if (!isStillWatchingPromptActive || isBlackScreenActive) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return;
        } catch (Exception ignored) {}

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    int volPct = prefs.getInt(KEY_STILL_WATCHING_BEEP_VOLUME, 65);
                    int toneType = prefs.getInt(KEY_STILL_WATCHING_BEEP_TONE, 0);
                    if (volPct < 1) volPct = 1;
                    if (volPct > 100) volPct = 100;

                    int sampleRate = 44100;
                    short[] buffer;
                    int numSamples;
                    int durationMs;

                    if (toneType == 1) { // Ding-Dong (Doble Chime)
                        int dur1 = 180;
                        int gap = 35;
                        int dur2 = 250;
                        durationMs = dur1 + gap + dur2;
                        numSamples = (sampleRate * durationMs) / 1000;
                        buffer = new short[numSamples];

                        int samples1 = (sampleRate * dur1) / 1000;
                        int gapSamples = (sampleRate * gap) / 1000;
                        int samples2 = (sampleRate * dur2) / 1000;
                        int fade1 = (sampleRate * 20) / 1000;
                        int fade2 = (sampleRate * 25) / 1000;

                        double amp = 32767.0 * (volPct / 100.0);
                        double freq1 = 659.25; // E5
                        double freq2 = 880.00; // A5

                        // Tone 1
                        for (int i = 0; i < samples1; i++) {
                            double angle = 2.0 * Math.PI * i * freq1 / sampleRate;
                            double s = Math.sin(angle);
                            double gain = 1.0;
                            if (i < fade1) gain = (double) i / fade1;
                            else if (i > samples1 - fade1) gain = (double) (samples1 - i) / fade1;
                            buffer[i] = (short) (s * amp * gain);
                        }
                        // Gap (silence)
                        for (int i = samples1; i < samples1 + gapSamples; i++) {
                            buffer[i] = 0;
                        }
                        // Tone 2
                        int offset = samples1 + gapSamples;
                        for (int i = 0; i < samples2 && (offset + i) < numSamples; i++) {
                            double angle = 2.0 * Math.PI * i * freq2 / sampleRate;
                            double s = Math.sin(angle);
                            double gain = 1.0;
                            if (i < fade2) gain = (double) i / fade2;
                            else if (i > samples2 - fade2) gain = (double) (samples2 - i) / fade2;
                            buffer[offset + i] = (short) (s * amp * gain);
                        }
                    } else {
                        double freq;
                        if (toneType == 2) { // Grave (550Hz)
                            freq = 550.0;
                            durationMs = 420;
                        } else if (toneType == 3) { // Agudo (1050Hz)
                            freq = 1050.0;
                            durationMs = 350;
                        } else { // Clásico (800Hz)
                            freq = 800.0;
                            durationMs = 400;
                        }

                        numSamples = (sampleRate * durationMs) / 1000;
                        buffer = new short[numSamples];
                        int fadeSamples = (sampleRate * 30) / 1000;
                        double amplitude = 32767.0 * (volPct / 100.0);

                        for (int i = 0; i < numSamples; i++) {
                            double angle = 2.0 * Math.PI * i * freq / sampleRate;
                            double sample = Math.sin(angle);
                            double gain = 1.0;
                            if (i < fadeSamples) gain = (double) i / fadeSamples;
                            else if (i > numSamples - fadeSamples) gain = (double) (numSamples - i) / fadeSamples;
                            buffer[i] = (short) (sample * amplitude * gain);
                        }
                    }

                    int minBufferSize = AudioTrack.getMinBufferSize(
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );
                    int bufSize = Math.max(minBufferSize, numSamples * 2);

                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();

                    AudioFormat audioFormat = new AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build();

                    AudioTrack track = new AudioTrack(
                            audioAttributes,
                            audioFormat,
                            bufSize,
                            AudioTrack.MODE_STATIC,
                            AudioManager.AUDIO_SESSION_ID_GENERATE
                    );

                    track.write(buffer, 0, numSamples);
                    track.play();

                    try {
                        Thread.sleep(durationMs + 60);
                    } catch (InterruptedException ignored) {}

                    track.stop();
                    track.release();
                    Log.d(TAG, "Still watching beep played successfully via AudioTrack.");
                } catch (Exception e) {
                    Log.e(TAG, "Error playing still watching beep via AudioTrack, fallback to ToneGenerator", e);
                    try {
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try { tg.release(); } catch (Exception ignored) {}
                            }
                        }, 350);
                    } catch (Exception ex) {
                        Log.e(TAG, "ToneGenerator fallback failed", ex);
                    }
                }
            }
        }).start();
    }

    private void updateStillWatchingPromptText() {
        if (stillWatchingOverlayView instanceof TextView) {
            TextView tv = (TextView) stillWatchingOverlayView;
            tv.setText("📺 ¿Sigues viendo?\nPresiona OK o Atrás para continuar (" + stillWatchingCountdownSeconds + "s)");
        }
    }

    private void dismissStillWatchingPrompt() {
        isStillWatchingPromptActive = false;
        handler.removeCallbacks(stillWatchingCountdownRunnable);
        handler.removeCallbacks(stillWatchingBeepRunnable);
        if (stillWatchingOverlayView == null) return;
        final View v = stillWatchingOverlayView;
        stillWatchingOverlayView = null;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm != null) wm.removeView(v);
                } catch (Exception ignored) {}
            }
        });
    }

    private void onStillWatchingTimeoutExpired() {
        dismissStillWatchingPrompt();
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int action = prefs.getInt(KEY_STILL_WATCHING_ACTION, 0);
        Log.d(TAG, "Still watching timeout expired! Executing timeout action: " + action + " and deactivating feature.");

        // Automatically turn off still_watching so it does not loop while user is inactive/asleep
        prefs.edit().putBoolean(KEY_STILL_WATCHING, false).apply();
        stopStillWatchingTimer();

        if (action == 0) {
            pauseMedia();
        } else if (action == 1) {
            pauseMediaAndBlackScreen();
        } else if (action == 2) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    private void pauseMedia() {
        try {
            if (audioManager != null) {
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PAUSE));
            }
        } catch (Exception e) { Log.e(TAG, "Failed to pause media", e); }
    }

    // ── Night Schedule & OLED Saver ──────────────────────────────────────────

    private void checkAndApplyNightSchedule() {
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_NIGHT_SCHEDULE, false);
        if (!enabled) return;

        int start = prefs.getInt(KEY_NIGHT_START, 22);
        int end = prefs.getInt(KEY_NIGHT_END, 7);
        int targetBlueLight = prefs.getInt(KEY_NIGHT_BLUE_LIGHT, 40);
        int targetDimmer = prefs.getInt(KEY_NIGHT_DIMMER, 50);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);

        boolean isNightTime;
        if (start < end) {
            isNightTime = (hour >= start && hour < end);
        } else {
            isNightTime = (hour >= start || hour < end);
        }

        if (isNightTime) {
            if (!isBlueLightActive && targetBlueLight > 0) {
                setBlueLightPct(targetBlueLight);
            }
            if (!isDimmerActive && targetDimmer > 0) {
                setDimmerBrightness(targetDimmer);
            }
        }
    }

    private void updateOledSaver() {
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_OLED_SAVER, false);
        if (enabled) {
            startOledSaverTimer();
        } else {
            stopOledSaverTimer();
        }
    }

    private void startOledSaverTimer() {
        stopOledSaverTimer();
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int mins = prefs.getInt(KEY_OLED_MINUTES, 5);
        cachedOledSaverDelayMs = mins * 60 * 1000L;
        isOledSaverActive = true;
        handler.postDelayed(oledSaverRunnable, cachedOledSaverDelayMs);
        Log.d(TAG, "OLED Saver timer started for " + mins + " minutes.");
    }

    private void stopOledSaverTimer() {
        isOledSaverActive = false;
        handler.removeCallbacks(oledSaverRunnable);
        dismissOledSaverOverlay();
    }

    private void showOledSaverOverlay() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) return;
                    dismissOledSaverOverlay();

                    SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    int mode = prefs.getInt(KEY_OLED_MODE, 0);

                    View v = new View(ButtonMappingService.this);
                    if (mode == 1) {
                        v.setBackgroundColor(Color.BLACK);
                    } else {
                        v.setBackgroundColor(Color.argb((int)(255 * 0.95), 0, 0, 0));
                    }

                    oledSaverOverlayView = v;
                    isOledSaverPromptActive = true;

                    WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT
                    );

                    wm.addView(oledSaverOverlayView, p);
                    Log.d(TAG, "OLED Saver overlay displayed.");
                } catch (Exception e) { Log.e(TAG, "Failed to show OLED saver overlay", e); }
            }
        });
    }

    private void dismissOledSaverOverlay() {
        if (oledSaverOverlayView != null) {
            final View v = oledSaverOverlayView;
            oledSaverOverlayView = null;
            isOledSaverPromptActive = false;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                        if (wm != null) wm.removeView(v);
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void resetOledSaverTimerOnKeyPress() {
        if (isOledSaverPromptActive) {
            dismissOledSaverOverlay();
        }
        if (isOledSaverActive) {
            handler.removeCallbacks(oledSaverRunnable);
            handler.postDelayed(oledSaverRunnable, cachedOledSaverDelayMs);
        }
    }

    // ── System info overlay ──────────────────────────────────────────────────

    private void showSystemInfoOverlay() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) return;
                    dismissSystemInfoOverlay();
                    String ip = getLocalIpAddress();
                    ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                    ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
                    if (am != null) am.getMemoryInfo(mem);
                    long freeRam = mem.availMem / (1024 * 1024);
                    long totalRam = mem.totalMem / (1024 * 1024);
                    long uptimeMs = SystemClock.elapsedRealtime();
                    long uptimeH = uptimeMs / 3600000;
                    long uptimeM = (uptimeMs % 3600000) / 60000;
                    String info = "  IP local:  " + ip + "\n"
                            + "  RAM:  " + freeRam + " MB libre / " + totalRam + " MB total\n"
                            + "  Android " + Build.VERSION.RELEASE + "  (API " + Build.VERSION.SDK_INT + ")\n"
                            + "  Encendido hace:  " + uptimeH + "h " + uptimeM + "m\n"
                            + "  Modelo:  " + Build.MODEL;
                    TextView tv = new TextView(ButtonMappingService.this);
                    tv.setText(info);
                    tv.setTextColor(Color.WHITE);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    tv.setTypeface(Typeface.MONOSPACE);
                    tv.setPadding(40, 32, 40, 32);
                    tv.setBackgroundColor(Color.argb(230, 15, 15, 28));
                    tv.setLineSpacing(8, 1.0f);
                    systemInfoOverlayView = tv;
                    WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                            PixelFormat.TRANSLUCENT
                    );
                    p.gravity = Gravity.CENTER;
                    wm.addView(systemInfoOverlayView, p);
                    handler.postDelayed(new Runnable() {
                        @Override public void run() { dismissSystemInfoOverlay(); }
                    }, 6000);
                    Log.d(TAG, "System info overlay shown");
                } catch (Exception e) { Log.e(TAG, "Error showing system info overlay", e); }
            }
        });
    }

    private void dismissSystemInfoOverlay() {
        if (systemInfoOverlayView == null) return;
        final View v = systemInfoOverlayView;
        systemInfoOverlayView = null;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm != null) wm.removeView(v);
                } catch (Exception ignored) {}
            }
        });
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return String.format(Locale.US, "%d.%d.%d.%d",
                            ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
                }
            }
        } catch (Exception ignored) {}
        return "N/D";
    }

    // ── Modo Cine ────────────────────────────────────────────────────────────

    private void toggleCineMode() {
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean isCineActive = op.getBoolean(KEY_CINE_MODE, false);
        SharedPreferences cp = getSharedPreferences("cine_prefs", MODE_PRIVATE);
        
        if (isCineActive) {
            // Activar overlays de Cine
            if (cp.getBoolean("cine_blue_light", true) && !isBlueLightActive) {
                int pct = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).getInt("blue_light_pct", 50);
                showBlueLightOverlayPct(pct == 0 ? 50 : pct);
            }
            if (cp.getBoolean("cine_dimmer", false) && !isDimmerActive) showDimmerOverlay();
            int timerMins = cp.getInt("cine_timer", 0);
            if (timerMins > 0) {
                Intent i = new Intent(this, SleepTimerService.class);
                i.setAction("ACTION_START_TIMER");
                i.putExtra("minutes", timerMins);
                startService(i);
            }
            Log.d(TAG, "Cine mode activated overlays");
        } else {
            // Desactivar overlays de Cine
            if (isBlueLightActive) hideBlueLightOverlay();
            if (isDimmerActive) hideDimmerOverlay();
            Intent i = new Intent(this, SleepTimerService.class);
            i.setAction("ACTION_CANCEL_TIMER");
            startService(i);
            Log.d(TAG, "Cine mode deactivated overlays");
        }
    }

    // ── Reboot / Audio / Mirror ───────────────────────────────────────────────

    private void rebootDevice() {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.android.tv.settings", "com.android.tv.settings.system.RestartActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Reboot via RestartActivity");
        } catch (Exception e) {
            Log.w(TAG, "RestartActivity not found, trying power dialog", e);
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        }
    }

    private void openSystemSettings() {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Successfully opened System Settings (MainSettings)");
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    private void openDeveloperOptions() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Successfully opened Developer Options via standard Action");
        } catch (Exception e) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.android.tv.settings", "com.android.tv.settings.system.development.DevelopmentActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d(TAG, "Successfully opened Developer Options via DevelopmentActivity class");
            } catch (Exception e2) {
                Log.e(TAG, "Failed to open Developer Options", e2);
            }
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        // 0. Frame Step HUD Key Interception:
        if (isFrameStepHudActive) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (action == KeyEvent.ACTION_DOWN) {
                    stepVideoFrame(-1);
                    updateFrameStepHudFeedback("◄ Retroceder Frame (-1)");
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (action == KeyEvent.ACTION_DOWN) {
                    stepVideoFrame(1);
                    updateFrameStepHudFeedback("Avanzar Frame (+1) ►");
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                if (action == KeyEvent.ACTION_DOWN) {
                    sendMediaPlayPause();
                    updateFrameStepHudFeedback("⏯️ Play / Pausa");
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (action == KeyEvent.ACTION_DOWN) {
                    dismissFrameStepHud();
                }
                return true;
            }
        }

        // 0. Translation Overlay key interception:
        if (isTranslationOverlayActive) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (action == KeyEvent.ACTION_DOWN) {
                    dismissTranslationOverlay();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                if (action == KeyEvent.ACTION_DOWN) {
                    dismissTranslationOverlay();
                    SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    if (prefs.getBoolean("translate_auto_resume", true)) {
                        sendMediaPlay();
                    }
                }
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                dismissTranslationOverlay();
            }
            return true;
        }

        // 0. Zero-Delay Button Combos Detection
        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean combosEnabled = prefs.getBoolean("btn_combos_enabled", true);
        if (combosEnabled) {
            // Combo: MUTE + (OK / YouTube)
            if (muteState.isPressed) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    int actionId = prefs.getInt("combo_mute_ok_action", 23); // default Traducir Pantalla
                    if (actionId > 0) {
                        if (action == KeyEvent.ACTION_DOWN) {
                            muteState.cancel();
                            isDismissingComboKey = true;
                            executeAction(actionId);
                        }
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int actionId = prefs.getInt("combo_mute_right_action", 24);
                    if (actionId > 0) {
                        if (action == KeyEvent.ACTION_DOWN) {
                            muteState.cancel();
                            isDismissingComboKey = true;
                            executeAction(actionId);
                        }
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    int actionId = prefs.getInt("combo_mute_left_action", 25);
                    if (actionId > 0) {
                        if (action == KeyEvent.ACTION_DOWN) {
                            muteState.cancel();
                            isDismissingComboKey = true;
                            executeAction(actionId);
                        }
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == 190) {
                    int actionId = prefs.getInt("combo_youtube190_mute_action", 0);
                    if (actionId > 0) {
                        if (action == KeyEvent.ACTION_DOWN) {
                            muteState.cancel();
                            youtube190State.cancel();
                            isDismissingComboKey = true;
                            executeAction(actionId);
                        }
                        return true;
                    }
                }
            }

            // Combo: YOUTUBE (190) + MUTE
            if (youtube190State.isPressed) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || keyCode == 140) {
                    int actionId = prefs.getInt("combo_youtube190_mute_action", 0);
                    if (actionId > 0) {
                        if (action == KeyEvent.ACTION_DOWN) {
                            youtube190State.cancel();
                            muteState.cancel();
                            isDismissingComboKey = true;
                            executeAction(actionId);
                        }
                        return true;
                    }
                }
            }

            // Combo: TV INPUT + OK
            if (isInputPressed) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    int actionId = prefs.getInt("combo_input_ok_action", 0);
                    if (actionId > 0) {
                        if (action == KeyEvent.ACTION_DOWN) {
                            isInputPressed = false;
                            isInputLongPressTriggered = false;
                            handler.removeCallbacks(inputLongPressRunnable);
                            isDismissingComboKey = true;
                            executeAction(actionId);
                        }
                        return true;
                    }
                }
            }
        }

        if (isDismissingComboKey) {
            if (action == KeyEvent.ACTION_UP) {
                isDismissingComboKey = false;
            }
            return true;
        }

        // 0. Mindful Delay key interception: Back/Home cancels and goes to Home; all other keys are blocked
        if (isMindfulDelayActive) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
                if (action == KeyEvent.ACTION_DOWN) {
                    cancelMindfulDelayAndGoHome();
                }
                return true;
            }
            return true;
        }

        // Reset still watching & OLED saver inactivity timers only when features are active (and prompt is not active)
        if (action == KeyEvent.ACTION_DOWN) {
            if (isStillWatchingActive && !isStillWatchingPromptActive) {
                resetStillWatchingTimerOnKeyPress();
            }
            if (isOledSaverActive && !isOledSaverPromptActive) {
                resetOledSaverTimerOnKeyPress();
            }
        }

        // 0. Intercept prompt response keys for ¿Sigues viendo?
        if (isStillWatchingPromptActive || isDismissingStillWatchingKey) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (isStillWatchingPromptActive) {
                        isDismissingStillWatchingKey = true;
                        stillWatchingDismissKeyCode = keyCode;
                        dismissStillWatchingPrompt();
                        startStillWatchingTimer();
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    if (isDismissingStillWatchingKey && (keyCode == stillWatchingDismissKeyCode || stillWatchingDismissKeyCode == 0)) {
                        isDismissingStillWatchingKey = false;
                        stillWatchingDismissKeyCode = 0;
                    }
                }
                return true;
            }
        }

        // 0. Intercept keypresses while Scheduled Sleep Prompt is active (cancels auto power off ONLY on Back or OK/Enter)
        if (isScheduledSleepPromptActive) {
            if (keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                if (action == KeyEvent.ACTION_DOWN) {
                    Log.d(TAG, "Key pressed (" + keyCode + ") while Scheduled Sleep prompt active. Cancelling power off!");
                    dismissScheduledSleepPrompt();
                    handler.post(new Runnable() {
                        @Override public void run() {
                            try {
                                Toast.makeText(getApplicationContext(), "⏰ Apagado programado cancelado hoy", Toast.LENGTH_SHORT).show();
                            } catch (Exception ignored) {}
                        }
                    });
                }
                return true; // Consume key press cleanly
            }
        }

        // 1. If black screen is active or dismissing black screen key
        if (isBlackScreenActive || isDismissingBlackScreenKey) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (isBlackScreenActive) {
                    isDismissingBlackScreenKey = true;
                    blackScreenDismissKeyCode = keyCode;
                    dismissBlackScreen();

                    // Reset all button state machines so next press starts completely fresh
                    youtube190State.isPressed = false;
                    youtube190State.isLongPressTriggered = false;
                    youtube189State.isPressed = false;
                    youtube189State.isLongPressTriggered = false;
                    muteState.isPressed = false;
                    muteState.isLongPressTriggered = false;
                    isInputPressed = false;
                    isInputLongPressTriggered = false;

                    // If user pressed YouTube, Home (Casita), or TV Input while screen was black,
                    // do NOT consume the key press so the app/home launches naturally!
                    if (keyCode == KeyEvent.KEYCODE_HOME
                            || keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == 190
                            || keyCode == KeyEvent.KEYCODE_BUTTON_2 || keyCode == 189
                            || keyCode == KeyEvent.KEYCODE_TV_INPUT || keyCode == 178) {
                        return false; // Pass key to system to open app/home
                    }
                }
            } else if (action == KeyEvent.ACTION_UP) {
                if (isDismissingBlackScreenKey && (keyCode == blackScreenDismissKeyCode || blackScreenDismissKeyCode == 0)) {
                    isDismissingBlackScreenKey = false;
                    blackScreenDismissKeyCode = 0;
                    youtube190State.isPressed = false;
                    youtube190State.isLongPressTriggered = false;
                    youtube189State.isPressed = false;
                    youtube189State.isLongPressTriggered = false;
                    muteState.isPressed = false;
                    muteState.isLongPressTriggered = false;
                    isInputPressed = false;
                    isInputLongPressTriggered = false;
                }
            }
            return true; // Consume wake-up keypresses (DOWN & UP) so screen wakes up cleanly
        }

        // 2. Intercept Netflix button (KEYCODE_BUTTON_3 / 190) - Physically YouTube on user's remote
        if (keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == 190) {
            if (action == KeyEvent.ACTION_DOWN) {
                youtube190State.onDown();
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                youtube190State.onUp();
                return true;
            }
        }

        // 3. Intercept YouTube button (KEYCODE_BUTTON_2 / 189)
        if (keyCode == KeyEvent.KEYCODE_BUTTON_2 || keyCode == 189) {
            if (action == KeyEvent.ACTION_DOWN) {
                youtube189State.onDown();
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                youtube189State.onUp();
                return true;
            }
        }

        // 4. Mute keys: VOLUME_MUTE (164), MUTE (140)
        if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || keyCode == 140) {
            if (action == KeyEvent.ACTION_DOWN) {
                muteState.onDown();
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                muteState.onUp();
                return true;
            }
        }

        // 5. Intercept TV Input button (KEYCODE_TV_INPUT / 178)
        if (keyCode == KeyEvent.KEYCODE_TV_INPUT || keyCode == 178) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (!isInputPressed) {
                    isInputPressed = true;
                    isInputLongPressTriggered = false;
                    Log.d(TAG, "Input button DOWN, starting timer...");
                    handler.postDelayed(inputLongPressRunnable, BLACK_SCREEN_LONG_PRESS_MS);
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                Log.d(TAG, "Input button UP. Long press triggered? " + isInputLongPressTriggered);
                handler.removeCallbacks(inputLongPressRunnable);

                if (!isInputLongPressTriggered && isInputPressed) {
                    Log.d(TAG, "Short press detected, letting Input event pass through");
                    isInputPressed = false;
                    isInputLongPressTriggered = false;
                    return false;
                }

                isInputPressed = false;
                isInputLongPressTriggered = false;
                return true;
            }
        }

        // 6. Intercept keys if QuickMenuOverlay is active (or dismissing via BACK)
        if (QuickMenuOverlay.getInstance().isShowing() || isDismissingQuickMenuKey) {
            if (isDismissingQuickMenuKey && keyCode == KeyEvent.KEYCODE_BACK) {
                if (action == KeyEvent.ACTION_UP) {
                    isDismissingQuickMenuKey = false;
                }
                return true;
            }
            boolean wasShowing = QuickMenuOverlay.getInstance().isShowing();
            QuickMenuOverlay.getInstance().onKeyEvent(event);
            if (wasShowing && !QuickMenuOverlay.getInstance().isShowing() && keyCode == KeyEvent.KEYCODE_BACK) {
                isDismissingQuickMenuKey = true;
            }
            return true;
        }

        return super.onKeyEvent(event);
    }

    public static class TranslatedBlock {
        public Rect box;
        public String originalText;
        public String translatedText;
        public String sourceLanguageCode;

        public TranslatedBlock(Rect box, String originalText) {
            this(box, originalText, null);
        }

        public TranslatedBlock(Rect box, String originalText, String sourceLanguageCode) {
            this.box = new Rect(box);
            this.originalText = originalText;
            this.translatedText = originalText;
            this.sourceLanguageCode = sourceLanguageCode;
        }
    }

    private void sendMediaPlay() {
        try {
            if (audioManager != null) {
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PLAY));
                Log.d(TAG, "Sent KEYCODE_MEDIA_PLAY to system");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send media play", e);
        }
    }

    private void stepVideoFrame(final int direction) {
        try {
            if (audioManager != null) {
                int keyStep = direction > 0 ? KeyEvent.KEYCODE_MEDIA_STEP_FORWARD : KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD;
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyStep));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   keyStep));

                int keyFwd = direction > 0 ? KeyEvent.KEYCODE_MEDIA_FAST_FORWARD : KeyEvent.KEYCODE_MEDIA_REWIND;
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyFwd));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   keyFwd));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in audioManager media step", e);
        }

        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                findAndScrollVideoPlayer(root, direction > 0);
                root.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in accessibility node scroll", e);
        }
    }

    private boolean findAndScrollVideoPlayer(AccessibilityNodeInfo node, boolean forward) {
        if (node == null) return false;
        CharSequence className = node.getClassName();
        if (className != null) {
            String c = className.toString();
            if (c.contains("SeekBar") || c.contains("ProgressBar") || c.contains("Timeline") || c.contains("PlayerView") || c.contains("VideoSurfaceView") || c.contains("RecyclerView") || c.contains("ScrollView")) {
                int action = forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                if ((node.getActions() & action) != 0) {
                    node.performAction(action);
                    return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean scrolled = findAndScrollVideoPlayer(child, forward);
                child.recycle();
                if (scrolled) return true;
            }
        }
        return false;
    }

    private void launchDeveloperOptions() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Successfully opened Developer Options via standard Action");
        } catch (Exception e) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.android.tv.settings", "com.android.tv.settings.system.development.DevelopmentActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d(TAG, "Successfully opened Developer Options via DevelopmentActivity class");
            } catch (Exception e2) {
                Log.e(TAG, "Failed to open Developer Options", e2);
            }
        }
    }

    private void sendMediaPlayPause() {
        try {
            if (audioManager != null) {
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in sendMediaPlayPause", e);
        }
    }

    public void toggleFrameStepHud() {
        if (isFrameStepHudActive) {
            dismissFrameStepHud();
        } else {
            showFrameStepHud();
        }
    }

    private void showFrameStepHud() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    dismissFrameStepHud();
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) return;

                    float density = getResources().getDisplayMetrics().density;

                    LinearLayout root = new LinearLayout(ButtonMappingService.this);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setGravity(Gravity.CENTER);
                    root.setPadding(Math.round(24 * density), Math.round(14 * density), Math.round(24 * density), Math.round(14 * density));

                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setColor(Color.argb(240, 18, 22, 30));
                    bg.setCornerRadius(20 * density);
                    bg.setStroke(Math.round(1.5f * density), 0xFF81D4FA);
                    root.setBackground(bg);

                    // Row 1: Action controls
                    LinearLayout rowControls = new LinearLayout(ButtonMappingService.this);
                    rowControls.setOrientation(LinearLayout.HORIZONTAL);
                    rowControls.setGravity(Gravity.CENTER);

                    TextView btnLeft = new TextView(ButtonMappingService.this);
                    btnLeft.setText("  ◄◄  -1 Frame  ");
                    btnLeft.setTextColor(0xFF81D4FA);
                    btnLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    btnLeft.setTypeface(Typeface.DEFAULT_BOLD);
                    rowControls.addView(btnLeft);

                    TextView btnMid = new TextView(ButtonMappingService.this);
                    btnMid.setText("   |   ⏯️ Play / Pausa   |   ");
                    btnMid.setTextColor(0xFFFFFFFF);
                    btnMid.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    btnMid.setTypeface(Typeface.DEFAULT_BOLD);
                    rowControls.addView(btnMid);

                    TextView btnRight = new TextView(ButtonMappingService.this);
                    btnRight.setText("  +1 Frame  ►►  ");
                    btnRight.setTextColor(0xFF81D4FA);
                    btnRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    btnRight.setTypeface(Typeface.DEFAULT_BOLD);
                    rowControls.addView(btnRight);

                    root.addView(rowControls);

                    // Row 2: Status / Instruction
                    TextView statusTv = new TextView(ButtonMappingService.this);
                    statusTv.setId(1001);
                    statusTv.setText("🎞️ Presiona ◄ / ► para mover cuadro por cuadro  •  [ Atrás: Salir ]");
                    statusTv.setTextColor(0xFFB0BEC5);
                    statusTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    statusTv.setGravity(Gravity.CENTER);
                    statusTv.setPadding(0, Math.round(6 * density), 0, 0);
                    root.addView(statusTv);

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                    );
                    params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    params.y = Math.round(36 * density);

                    wm.addView(root, params);
                    frameStepHudView = root;
                    isFrameStepHudActive = true;
                    Log.d(TAG, "Frame Step HUD displayed.");
                } catch (Exception e) {
                    Log.e(TAG, "Error displaying Frame Step HUD", e);
                }
            }
        });
    }

    private void updateFrameStepHudFeedback(final String msg) {
        if (frameStepHudView != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        TextView statusTv = frameStepHudView.findViewById(1001);
                        if (statusTv != null) {
                            statusTv.setText("🎞️ " + msg + "  •  [ Atrás: Salir ]");
                            statusTv.setTextColor(0xFFFFEB3B);
                        }
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void dismissFrameStepHud() {
        if (frameStepHudView != null) {
            final View v = frameStepHudView;
            frameStepHudView = null;
            isFrameStepHudActive = false;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                        if (wm != null) wm.removeView(v);
                        Log.d(TAG, "Frame Step HUD dismissed.");
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    public void triggerScreenTranslation() {
        if (isTranslationOverlayActive) {
            dismissTranslationOverlay();
        }

        QuickMenuOverlay.getInstance().dismiss();
        captureAndTranslateScreen();
    }

    private void captureAndTranslateScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(getApplicationContext(), "Traducción requiere Android 11+", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        boolean autoPause = prefs.getBoolean("translate_auto_pause", true);
        if (autoPause) {
            sendMediaPause();
        }

        Toast.makeText(getApplicationContext(), "🌐 Capturando y traduciendo pantalla...", Toast.LENGTH_SHORT).show();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult result) {
                            try {
                                HardwareBuffer hardwareBuffer = result.getHardwareBuffer();
                                Bitmap rawBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.getColorSpace());
                                hardwareBuffer.close();

                                if (rawBitmap == null) {
                                    Toast.makeText(getApplicationContext(), "Error al obtener captura", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Bitmap bitmap = rawBitmap.copy(Bitmap.Config.ARGB_8888, true);
                                rawBitmap.recycle();

                                processScreenshotForTranslation(bitmap);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing screenshot bitmap", e);
                                Toast.makeText(getApplicationContext(), "Error procesando imagen", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            Log.e(TAG, "takeScreenshot failed: " + errorCode);
                            Toast.makeText(getApplicationContext(), "No se pudo capturar la pantalla (" + errorCode + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error initiating takeScreenshot", e);
                }
            }
        }, 400);
    }

    private static boolean containsKorean(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || block == Character.UnicodeBlock.HANGUL_JAMO
                    || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsJapaneseKana(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsChineseHanzi(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCyrillic(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CYRILLIC
                    || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPunctuationOrSymbol(char c) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
        return b == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || b == Character.UnicodeBlock.SUPPLEMENTAL_PUNCTUATION
                || b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || b == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || b == Character.UnicodeBlock.EMOTICONS
                || b == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS
                || b == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS;
    }

    private static boolean isPureLatinOrSpanish(String s) {
        if (s == null) return true;
        for (char c : s.toCharArray()) {
            if (c > 0x024F && !isPunctuationOrSymbol(c)) {
                return false;
            }
        }
        return true;
    }

    private static String detectBlockLanguage(String txt, int userSrcLangIdx) {
        if (userSrcLangIdx == 1) return TranslateLanguage.KOREAN;
        if (userSrcLangIdx == 2) return TranslateLanguage.JAPANESE;
        if (userSrcLangIdx == 3) return TranslateLanguage.CHINESE;
        if (userSrcLangIdx == 4) return TranslateLanguage.ENGLISH;

        if (containsKorean(txt)) return TranslateLanguage.KOREAN;
        if (containsJapaneseKana(txt)) return TranslateLanguage.JAPANESE;
        if (containsChineseHanzi(txt)) return TranslateLanguage.CHINESE;
        if (containsCyrillic(txt)) return TranslateLanguage.RUSSIAN;
        return TranslateLanguage.JAPANESE;
    }

    private static boolean isTranslatableBlock(String txt, int srcLangIdx) {
        if (txt == null) return false;
        String trimmed = txt.trim();
        if (trimmed.length() <= 1) return false;

        // 1. Skip pure numbers, timestamps, durations like 0:39, 4:53, 01:40, 1080p, 4k, 60fps
        if (trimmed.matches("^(?:\\d{1,2}:\\d{2}(?::\\d{2})?|\\d+[kKmMbB]?|\\d+\\s*(?:fps|p|k|hz)|\\W+)$")) {
            return false;
        }

        // 2. If user specifically selected English (4), allow English/Latin
        if (srcLangIdx == 4) {
            return true;
        }

        // 3. If text is 100% pure Latin/Spanish/English without foreign characters, DO NOT translate or draw boxes!
        if (isPureLatinOrSpanish(trimmed)) {
            return false;
        }

        // 4. Must contain actual Korean, Japanese, Chinese, or Cyrillic characters
        return containsKorean(trimmed) || containsJapaneseKana(trimmed) || containsChineseHanzi(trimmed) || containsCyrillic(trimmed);
    }

    private static List<TranslatedBlock> mergeAndDeduplicateBlocks(List<TranslatedBlock> rawBlocks) {
        if (rawBlocks == null || rawBlocks.size() <= 1) return rawBlocks != null ? rawBlocks : new ArrayList<TranslatedBlock>();

        // 1. Sort top-to-bottom, left-to-right
        Collections.sort(rawBlocks, new Comparator<TranslatedBlock>() {
            @Override public int compare(TranslatedBlock a, TranslatedBlock b) {
                if (Math.abs(a.box.top - b.box.top) > 25) {
                    return Integer.compare(a.box.top, b.box.top);
                }
                return Integer.compare(a.box.left, b.box.left);
            }
        });

        // 2. Merge vertically stacked lines of the same card/paragraph
        List<TranslatedBlock> merged = new ArrayList<>();
        for (TranslatedBlock block : rawBlocks) {
            boolean mergedIntoExisting = false;
            for (TranslatedBlock existing : merged) {
                boolean sameLang = (block.sourceLanguageCode != null && block.sourceLanguageCode.equals(existing.sourceLanguageCode));
                if (sameLang) {
                    int verticalGap = block.box.top - existing.box.bottom;
                    int xOverlap = Math.min(block.box.right, existing.box.right) - Math.max(block.box.left, existing.box.left);

                    // If adjacent vertically (gap <= 40px) and horizontally aligned
                    if (verticalGap >= -25 && verticalGap <= 40 && xOverlap > -30) {
                        existing.box.union(block.box);
                        existing.originalText = existing.originalText + " " + block.originalText;
                        mergedIntoExisting = true;
                        break;
                    }
                }
            }
            if (!mergedIntoExisting) {
                merged.add(block);
            }
        }
        resolveBoxCollisions(merged);
        return merged;
    }

    private static void resolveBoxCollisions(List<TranslatedBlock> blocks) {
        if (blocks == null || blocks.size() <= 1) return;
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                TranslatedBlock a = blocks.get(i);
                TranslatedBlock b = blocks.get(j);
                int xOverlap = Math.min(a.box.right, b.box.right) - Math.max(a.box.left, b.box.left);
                if (xOverlap > 0) {
                    if (b.box.top < a.box.bottom + 8 && b.box.top >= a.box.top) {
                        int shift = (a.box.bottom + 8) - b.box.top;
                        b.box.top += shift;
                        b.box.bottom += shift;
                    }
                }
            }
        }
    }

    private void processScreenshotForTranslation(final Bitmap bitmap) {
        if (bitmap == null) return;
        try {
            final SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
            final int srcLangIdx = prefs.getInt("translate_source_lang_idx", 0);
            final InputImage image = InputImage.fromBitmap(bitmap, 0);

            if (srcLangIdx == 5) { // Multi-Idioma Paralelo JP + KO
                TextRecognizer recJa = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
                TextRecognizer recKo = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());

                final Task<Text> taskJa = recJa.process(image);
                final Task<Text> taskKo = recKo.process(image);

                Tasks.whenAllComplete(taskJa, taskKo)
                        .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<List<Task<?>>>() {
                            @Override
                            public void onSuccess(List<Task<?>> tasks) {
                                bitmap.recycle();
                                Text textJa = taskJa.isSuccessful() ? taskJa.getResult() : null;
                                Text textKo = taskKo.isSuccessful() ? taskKo.getResult() : null;
                                handleMultiOcrSuccess(textJa, textKo, srcLangIdx);
                            }
                        })
                        .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                bitmap.recycle();
                                Log.e(TAG, "Parallel OCR recognition failed", e);
                                Toast.makeText(getApplicationContext(), "Error en reconocimiento OCR", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                TextRecognizer recognizer;
                if (srcLangIdx == 1) {
                    recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
                } else if (srcLangIdx == 2) {
                    recognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
                } else if (srcLangIdx == 3) {
                    recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
                } else if (srcLangIdx == 4) {
                    recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                } else {
                    // Auto / Modo Clásico: El motor original basado en Japanese OCR
                    recognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
                }

                recognizer.process(image)
                        .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text visionText) {
                                bitmap.recycle();
                                handleClassicOcrSuccess(visionText, srcLangIdx);
                            }
                        })
                        .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                bitmap.recycle();
                                Log.e(TAG, "OCR recognition failed", e);
                                Toast.makeText(getApplicationContext(), "Error en reconocimiento OCR", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting OCR", e);
            bitmap.recycle();
        }
    }

    private void handleClassicOcrSuccess(Text visionText, int srcLangIdx) {
        if (visionText == null || visionText.getTextBlocks().isEmpty()) {
            Toast.makeText(getApplicationContext(), "🔍 No se detectó texto en pantalla", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<TranslatedBlock> rawBlocks = new ArrayList<>();
        int koreanCount = 0;
        int japaneseCount = 0;
        int chineseCount = 0;

        for (Text.TextBlock textBlock : visionText.getTextBlocks()) {
            for (Text.Line line : textBlock.getLines()) {
                String txt = line.getText();
                Rect box = line.getBoundingBox();
                if (txt != null && box != null) {
                    String trimmed = txt.trim();
                    if (!isTranslatableBlock(trimmed, srcLangIdx)) continue;
                    if (box.left < 130 && box.width() < 180) continue; // Skip sidebar navigation

                    if (containsJapaneseKana(trimmed)) japaneseCount++;
                    else if (containsKorean(trimmed)) koreanCount++;
                    else if (containsChineseHanzi(trimmed)) chineseCount++;

                    rawBlocks.add(new TranslatedBlock(box, trimmed));
                }
            }
        }

        if (rawBlocks.isEmpty()) {
            Toast.makeText(getApplicationContext(), "🔍 No se detectó texto extranjero para traducir", Toast.LENGTH_SHORT).show();
            return;
        }

        List<TranslatedBlock> mergedBlocks = mergeAndDeduplicateBlocks(rawBlocks);

        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int targetLangIdx = prefs.getInt("translate_target_lang_idx", 0);
        String targetLangCode = targetLangIdx == 0 ? TranslateLanguage.SPANISH : TranslateLanguage.ENGLISH;
        String targetLangName = targetLangIdx == 0 ? "Español" : "English";

        String detectedLangCode = TranslateLanguage.JAPANESE;
        if (srcLangIdx == 1) detectedLangCode = TranslateLanguage.KOREAN;
        else if (srcLangIdx == 2) detectedLangCode = TranslateLanguage.JAPANESE;
        else if (srcLangIdx == 3) detectedLangCode = TranslateLanguage.CHINESE;
        else if (srcLangIdx == 4) detectedLangCode = TranslateLanguage.ENGLISH;
        else {
            if (koreanCount > 0 && koreanCount >= japaneseCount) detectedLangCode = TranslateLanguage.KOREAN;
            else if (japaneseCount > 0) detectedLangCode = TranslateLanguage.JAPANESE;
            else if (chineseCount > 0) detectedLangCode = TranslateLanguage.CHINESE;
            else detectedLangCode = TranslateLanguage.JAPANESE;
        }

        translateClassicBlocks(mergedBlocks, detectedLangCode, targetLangCode, targetLangName);
    }

    private void translateClassicBlocks(final List<TranslatedBlock> blocks, final String srcLangCode, final String targetLangCode, final String targetLangName) {
        String srcName = new Locale(srcLangCode).getDisplayLanguage(new Locale("es", "ES"));
        if (srcName == null || srcName.isEmpty() || srcName.equals(srcLangCode)) {
            if ("ko".equals(srcLangCode)) srcName = "Coreano";
            else if ("ja".equals(srcLangCode)) srcName = "Japonés";
            else if ("zh".equals(srcLangCode)) srcName = "Chino";
            else if ("fr".equals(srcLangCode)) srcName = "Francés";
            else if ("pt".equals(srcLangCode)) srcName = "Portugués";
            else if ("en".equals(srcLangCode)) srcName = "Inglés";
            else srcName = srcLangCode.toUpperCase(Locale.ROOT);
        } else {
            srcName = Character.toUpperCase(srcName.charAt(0)) + srcName.substring(1);
        }
        final String finalSrcName = srcName;

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(srcLangCode)
                .setTargetLanguage(targetLangCode)
                .build();

        final Translator translator = Translation.getClient(options);
        com.google.mlkit.common.model.DownloadConditions conditions = new com.google.mlkit.common.model.DownloadConditions.Builder().build();

        final int total = blocks.size();
        final int[] completedCount = {0};

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        for (final TranslatedBlock block : blocks) {
                            translator.translate(block.originalText)
                                    .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<String>() {
                                        @Override
                                        public void onSuccess(String translated) {
                                            block.translatedText = translated;
                                            completedCount[0]++;
                                            if (completedCount[0] == total) {
                                                showTranslationOverlay(blocks, finalSrcName, targetLangName);
                                            }
                                        }
                                    })
                                    .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                                        @Override
                                        public void onFailure(Exception e) {
                                            completedCount[0]++;
                                            if (completedCount[0] == total) {
                                                showTranslationOverlay(blocks, finalSrcName, targetLangName);
                                            }
                                        }
                                    });
                        }
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Failed to download translation model", e);
                        Toast.makeText(getApplicationContext(), "Error al cargar modelo de " + finalSrcName, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleMultiOcrSuccess(Text textJa, Text textKo, int srcLangIdx) {
        final List<TranslatedBlock> rawBlocks = new ArrayList<>();

        if (textJa != null) {
            for (Text.TextBlock textBlock : textJa.getTextBlocks()) {
                for (Text.Line line : textBlock.getLines()) {
                    String txt = line.getText();
                    Rect box = line.getBoundingBox();
                    if (txt != null && box != null) {
                        String trimmed = txt.trim();
                        if (!isTranslatableBlock(trimmed, srcLangIdx)) continue;
                        if (box.left < 130 && box.width() < 180) continue;
                        if (containsKorean(trimmed)) continue;

                        if (containsJapaneseKana(trimmed)) {
                            rawBlocks.add(new TranslatedBlock(box, trimmed, TranslateLanguage.JAPANESE));
                        } else if (containsChineseHanzi(trimmed)) {
                            rawBlocks.add(new TranslatedBlock(box, trimmed, TranslateLanguage.CHINESE));
                        } else if (containsCyrillic(trimmed)) {
                            rawBlocks.add(new TranslatedBlock(box, trimmed, TranslateLanguage.RUSSIAN));
                        }
                    }
                }
            }
        }

        if (textKo != null) {
            for (Text.TextBlock textBlock : textKo.getTextBlocks()) {
                for (Text.Line line : textBlock.getLines()) {
                    String txt = line.getText();
                    Rect box = line.getBoundingBox();
                    if (txt != null && box != null) {
                        String trimmed = txt.trim();
                        if (!isTranslatableBlock(trimmed, srcLangIdx)) continue;
                        if (box.left < 130 && box.width() < 180) continue;
                        if (containsKorean(trimmed)) {
                            rawBlocks.add(new TranslatedBlock(box, trimmed, TranslateLanguage.KOREAN));
                        }
                    }
                }
            }
        }

        if (rawBlocks.isEmpty()) {
            Toast.makeText(getApplicationContext(), "🔍 No se detectó texto extranjero para traducir", Toast.LENGTH_SHORT).show();
            return;
        }

        List<TranslatedBlock> mergedBlocks = mergeAndDeduplicateBlocks(rawBlocks);

        SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        int targetLangIdx = prefs.getInt("translate_target_lang_idx", 0);
        String targetLangCode = targetLangIdx == 0 ? TranslateLanguage.SPANISH : TranslateLanguage.ENGLISH;
        String targetLangName = targetLangIdx == 0 ? "Español" : "English";

        translateMultiLanguageBlocks(mergedBlocks, targetLangCode, targetLangName);
    }

    private void translateMultiLanguageBlocks(final List<TranslatedBlock> blocks, final String targetLangCode, final String targetLangName) {
        if (blocks == null || blocks.isEmpty()) return;

        final Map<String, List<TranslatedBlock>> langGroups = new HashMap<>();
        for (TranslatedBlock b : blocks) {
            String src = b.sourceLanguageCode != null ? b.sourceLanguageCode : TranslateLanguage.JAPANESE;
            if (!langGroups.containsKey(src)) {
                langGroups.put(src, new ArrayList<TranslatedBlock>());
            }
            langGroups.get(src).add(b);
        }

        final int totalBlocks = blocks.size();
        final int[] completedBlocks = {0};

        for (Map.Entry<String, List<TranslatedBlock>> entry : langGroups.entrySet()) {
            final String srcLang = entry.getKey();
            final List<TranslatedBlock> groupBlocks = entry.getValue();

            TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(srcLang)
                    .setTargetLanguage(targetLangCode)
                    .build();

            final Translator translator = Translation.getClient(options);
            com.google.mlkit.common.model.DownloadConditions conditions = new com.google.mlkit.common.model.DownloadConditions.Builder().build();

            translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            for (final TranslatedBlock block : groupBlocks) {
                                translator.translate(block.originalText)
                                        .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<String>() {
                                             @Override
                                             public void onSuccess(String translated) {
                                                 block.translatedText = translated;
                                                 completedBlocks[0]++;
                                                 if (completedBlocks[0] == totalBlocks) {
                                                     showTranslationOverlay(blocks, "Auto", targetLangName);
                                                 }
                                             }
                                         })
                                        .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                                             @Override
                                             public void onFailure(Exception e) {
                                                 completedBlocks[0]++;
                                                 if (completedBlocks[0] == totalBlocks) {
                                                     showTranslationOverlay(blocks, "Auto", targetLangName);
                                                 }
                                             }
                                         });
                            }
                        }
                    })
                    .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Failed downloading translation model for " + srcLang, e);
                            for (TranslatedBlock b : groupBlocks) {
                                completedBlocks[0]++;
                            }
                            if (completedBlocks[0] == totalBlocks) {
                                showTranslationOverlay(blocks, "Auto", targetLangName);
                            }
                        }
                    });
        }
    }

    private void showTranslationOverlay(final List<TranslatedBlock> blocks, final String srcLangName, final String targetLangName) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    dismissTranslationOverlay();
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) return;

                    SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
                    int alphaPct = prefs.getInt("translate_bg_alpha_pct", 85);
                    int textSizeSp = prefs.getInt("translate_text_size_sp", 14);
                    boolean showTopBar = prefs.getBoolean("translate_show_top_bar", false);

                    float density = getResources().getDisplayMetrics().density;
                    int padPx = Math.round(8 * density);
                    int bgAlpha = Math.round((alphaPct / 100f) * 255);

                    android.widget.FrameLayout root = new android.widget.FrameLayout(ButtonMappingService.this);
                    root.setFocusable(true);
                    root.setClickable(true);

                    root.setBackgroundColor(Color.argb(50, 0, 0, 0));

                    if (showTopBar) {
                        TextView bottomPill = new TextView(ButtonMappingService.this);
                        bottomPill.setText("✨ Google Lens  ➔  " + targetLangName + "   [ OK: Reanudar  |  Atrás: Cerrar ]");
                        bottomPill.setTextColor(0xFFFFFFFF);
                        bottomPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                        bottomPill.setTypeface(Typeface.DEFAULT_BOLD);
                        bottomPill.setGravity(Gravity.CENTER);
                        bottomPill.setPadding(Math.round(20 * density), Math.round(10 * density), Math.round(20 * density), Math.round(10 * density));

                        android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
                        pillBg.setColor(Color.argb(235, 18, 22, 30));
                        pillBg.setCornerRadius(22 * density);
                        pillBg.setStroke(Math.round(1.5f * density), 0xFF81D4FA);
                        bottomPill.setBackground(pillBg);

                        android.widget.FrameLayout.LayoutParams lpBottom = new android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                        );
                        lpBottom.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                        lpBottom.bottomMargin = Math.round(28 * density);
                        root.addView(bottomPill, lpBottom);
                    }

                    for (TranslatedBlock block : blocks) {
                        Rect box = block.box;
                        if (box == null || block.translatedText == null || block.translatedText.trim().isEmpty()) continue;

                        int length = block.translatedText.length();
                        float finalSizeSp = textSizeSp;
                        if (length > 35 && finalSizeSp > 12) {
                            finalSizeSp = 12;
                        }
                        if (length > 65 && finalSizeSp > 11) {
                            finalSizeSp = 11;
                        }

                        TextView blockTv = new TextView(ButtonMappingService.this);
                        blockTv.setText(block.translatedText);
                        blockTv.setTextColor(0xFFFFFFFF);
                        blockTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, finalSizeSp);
                        blockTv.setTypeface(Typeface.DEFAULT_BOLD);
                        blockTv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        blockTv.setShadowLayer(3f, 0f, 1f, 0xFF000000);
                        blockTv.setPadding(padPx, Math.round(4 * density), padPx, Math.round(4 * density));
                        blockTv.setLineSpacing(0, 1.15f);

                        android.graphics.drawable.GradientDrawable blockBg = new android.graphics.drawable.GradientDrawable();
                        blockBg.setColor(Color.argb(bgAlpha, 14, 18, 26));
                        blockBg.setCornerRadius(6 * density);
                        blockBg.setStroke(Math.round(1f * density), Color.argb(60, 255, 255, 255));
                        blockTv.setBackground(blockBg);

                        int minW = Math.round(50 * density);
                        int minH = Math.round(24 * density);
                        int targetW = Math.max(box.width() + padPx * 2, minW);
                        int targetH = Math.max(box.height() + padPx, minH);

                        android.widget.FrameLayout.LayoutParams lpBlock = new android.widget.FrameLayout.LayoutParams(targetW, targetH);
                        lpBlock.leftMargin = Math.max(0, box.left - padPx);
                        lpBlock.topMargin = Math.max(0, box.top - Math.round(2 * density));
                        root.addView(blockTv, lpBlock);
                    }

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                    );

                    wm.addView(root, params);
                    translationOverlayView = root;
                    isTranslationOverlayActive = true;
                    Log.d(TAG, "Translation overlay displayed with " + blocks.size() + " blocks.");
                } catch (Exception e) {
                    Log.e(TAG, "Error showing translation overlay", e);
                }
            }
        });
    }

    private void dismissTranslationOverlay() {
        if (translationOverlayView != null) {
            final View v = translationOverlayView;
            translationOverlayView = null;
            isTranslationOverlayActive = false;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                        if (wm != null) wm.removeView(v);
                        Log.d(TAG, "Translation overlay dismissed.");
                    } catch (Exception ignored) {}
                }
            });
        }
    }
}
