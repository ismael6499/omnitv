package com.example.togglegrayscale;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import java.util.Date;
import java.util.Locale;

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

    private boolean isInputPressed = false;
    private boolean isInputLongPressTriggered = false;

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

    static final String KEY_NIGHT_SCHEDULE = "night_schedule";
    static final String KEY_NIGHT_START = "night_start";
    static final String KEY_NIGHT_END = "night_end";
    static final String KEY_NIGHT_BLUE_LIGHT = "night_blue_light";
    static final String KEY_NIGHT_DIMMER = "night_dimmer";

    static final String KEY_OLED_SAVER = "oled_saver";
    static final String KEY_OLED_MINUTES = "oled_minutes";
    static final String KEY_OLED_MODE = "oled_mode";

    private boolean isBlueLightActive = false;
    private View blueLightOverlayView = null;

    private boolean isClockActive = false;
    private View clockOverlayView = null;
    private TextView clockTextView = null;

    private boolean isDimmerActive = false;
    private View dimmerOverlayView = null;

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
            if (isStillWatchingActive) {
                showStillWatchingPrompt();
            }
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

    private View systemInfoOverlayView = null;

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
                }
                Log.d(TAG, name + " " + count + "-click executed. Running action: " + actionId);
                executeAction(actionId);
            }
        };

        ButtonState(String name, int defaultClick1, int defaultClick2, int defaultClick3, int defaultLong, int defaultDurationMs) {
            this.name = name;
            this.defaultClick1 = defaultClick1;
            this.defaultClick2 = defaultClick2;
            this.defaultClick3 = defaultClick3;
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
                if (clickCount == 3) {
                    clickTimeoutRunnable.run();
                } else {
                    handler.postDelayed(clickTimeoutRunnable, 300);
                }
            }
            isPressed = false;
            isLongPressTriggered = false;
        }
    }

    private final ButtonState muteState = new ButtonState("mute", 1, 7, 8, 2, 500);
    private final ButtonState youtube190State = new ButtonState("youtube_190", 5, 4, 3, 18, 500);
    private final ButtonState youtube189State = new ButtonState("youtube_189", 5, 0, 0, 4, 2000);

    private long lastAutoPauseTime = 0;
    private long lastCountdownDetectTime = 0;
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
            handler.postDelayed(this, 2000);
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

        long now = SystemClock.elapsedRealtime();
        if (now - lastAutoPauseTime < 20000) { // 20s cooldown
            return;
        }

        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        Log.d(TAG, "checkAutoPause: windows count=" + (windows != null ? windows.size() : "null"));
        if (windows == null || windows.isEmpty()) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            Log.d(TAG, "checkAutoPause: fallback root=" + (root != null ? root.getPackageName() : "null"));
            if (root != null) {
                processRootNode(root, mode, now);
                root.recycle();
            }
            return;
        }

        for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            Log.d(TAG, "checkAutoPause: window root=" + (root != null ? root.getPackageName() : "null"));
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

        Log.d(TAG, "processRootNode: pkg=" + pkg + ", texts count=" + info.texts.size() + ", isBarNearEnd=" + info.isProgressBarNearEnd);

        if (info.texts.isEmpty() && !info.isProgressBarNearEnd) {
            return false;
        }

        // Print texts for debugging
        Log.d(TAG, "checkAutoPause matched window: pkg=" + pkg + ", mode=" + mode + ", texts=" + info.texts);

        // 1. Check countdown
        if (checkAutoplayCountdown(info.texts)) {
            lastCountdownDetectTime = now;
            Log.d(TAG, "Auto pause: countdown detected!");
            triggerAutoPause();
            return true;
        }

        // 2. Check ProgressBar near end
        if (info.isProgressBarNearEnd) {
            Log.d(TAG, "Auto pause: ProgressBar near end detected!");
            triggerAutoPause();
            return true;
        }

        // 3. Check time text near end
        for (String text : info.texts) {
            if (checkTimeText(text)) {
                Log.d(TAG, "Auto pause: Time text near end detected: " + text);
                triggerAutoPause();
                return true;
            }
        }

        // 4. Check safety transition pause
        if (now - lastCountdownDetectTime < 15000) {
            for (String text : info.texts) {
                if (checkTimeTextAtStart(text)) {
                    Log.d(TAG, "Auto pause: Safety pause triggered for start of video: " + text);
                    triggerAutoPause();
                    return true;
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

        // Send KEYCODE_MEDIA_PAUSE
        try {
            if (audioManager != null) {
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PAUSE));
                Log.d(TAG, "Sent KEYCODE_MEDIA_PAUSE for auto pause");
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

        // Adjust mode/counters
        int mode = op.getInt("auto_pause_mode", 0);
        if (mode == 1) {
            op.edit().putInt("auto_pause_mode", 0).apply();
            Log.d(TAG, "Auto pause mode set to Disabled (was Once)");
        } else if (mode == 3) {
            int count = op.getInt("auto_pause_custom_count", 1);
            if (count > 1) {
                op.edit().putInt("auto_pause_custom_count", count - 1).apply();
                Log.d(TAG, "Auto pause count decremented to: " + (count - 1));
            } else {
                op.edit().putInt("auto_pause_mode", 0).putInt("auto_pause_custom_count", 0).apply();
                Log.d(TAG, "Auto pause count reached 0, mode set to Disabled");
            }
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
                setServiceInfo(info);
                Log.d(TAG, "AccessibilityServiceInfo configured programmatically.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set AccessibilityServiceInfo programmatically", e);
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
            }
        }, 500);

        // Start auto-pause checker
        handler.postDelayed(autoPauseCheckRunnable, 2000);

        // Start night schedule checker
        handler.postDelayed(nightScheduleCheckRunnable, 3000);

        // Register screen state receiver for auto-reset on wake
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenStateReceiver, filter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register screenStateReceiver", e);
        }
    }

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
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
            case "ACTION_REORDER_OVERLAYS": reorderOverlaysOnTop(); break;
            case "ACTION_UPDATE_SCHEDULED_SLEEP": ScheduledSleepReceiver.scheduleNextAlarm(this); break;
            case "ACTION_SCHEDULED_POWER_OFF": performPowerOffOrSleep(); break;
            case "ACTION_PAUSE_SCREEN_OFF":
            case "ACTION_PAUSE_AND_SCREEN_OFF": pauseMediaAndBlackScreen(); break;
            case "ACTION_OPEN_RECENTS":
                handler.postDelayed(new Runnable() {
                    @Override public void run() { performGlobalAction(GLOBAL_ACTION_RECENTS); }
                }, 300);
                break;
        }
    }

    public void reorderOverlaysOnTop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) return;
                    if (isBlueLightActive && blueLightOverlayView != null) {
                        try {
                            wm.removeView(blueLightOverlayView);
                            wm.addView(blueLightOverlayView, overlayMatchParams());
                        } catch (Exception ignored) {}
                    }
                    if (isDimmerActive && dimmerOverlayView != null) {
                        try {
                            wm.removeView(dimmerOverlayView);
                            wm.addView(dimmerOverlayView, overlayMatchParams());
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reordering overlays on top", e);
                }
            }
        });
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
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
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
        hideDimmerOverlay();
        dismissSystemInfoOverlay();
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
                    Log.d(TAG, "Still watching prompt shown");
                } catch (Exception e) { Log.e(TAG, "Error showing still watching prompt", e); }
            }
        });
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
}
