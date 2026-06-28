package com.example.togglegrayscale;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Intent;
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

    private boolean isInputPressed = false;
    private boolean isInputLongPressTriggered = false;

    static final String OVERLAY_PREFS = "overlay_prefs";
    static final String KEY_BLUE_LIGHT = "blue_light";
    static final String KEY_CLOCK = "clock";
    static final String KEY_DIMMER = "dimmer";
    static final String KEY_CINE_MODE = "cine_mode";

    private boolean isBlueLightActive = false;
    private View blueLightOverlayView = null;

    private boolean isClockActive = false;
    private View clockOverlayView = null;
    private TextView clockTextView = null;

    private boolean isDimmerActive = false;
    private View dimmerOverlayView = null;

    private View systemInfoOverlayView = null;

    private int clockShowRetries = 0;
    private int blueLightShowRetries = 0;
    private int dimmerShowRetries = 0;

    private final Runnable clockUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockText();
            handler.postDelayed(this, 60000);
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

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Service connected and ready to intercept keys");
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        final SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (prefs.getBoolean(KEY_BLUE_LIGHT, false)) showBlueLightOverlay();
                if (prefs.getBoolean(KEY_CLOCK, true)) showClockOverlay();
                if (prefs.getBoolean(KEY_DIMMER, false)) showDimmerOverlay();
            }
        }, 500);
    }

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
            case "ACTION_TOGGLE_DIMMER": toggleDimmer(); break;
            case "ACTION_TOGGLE_CINE_MODE": toggleCineMode(); break;
            case "ACTION_SHOW_SYSTEM_INFO": showSystemInfoOverlay(); break;
            case "ACTION_REBOOT": rebootDevice(); break;
            case "ACTION_OPEN_AUDIO": openAudioSettings(); break;
            case "ACTION_OPEN_SCREEN_MIRROR": openScreenMirror(); break;
            case "ACTION_OPEN_RECENTS":
                handler.postDelayed(new Runnable() {
                    @Override public void run() { performGlobalAction(GLOBAL_ACTION_RECENTS); }
                }, 300);
                break;
        }
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
            case 15: // Salida de Audio
                openAudioSettings();
                break;
            case 16: // Pantalla Espejo
                openScreenMirror();
                break;
            case 17: // Recientes
                handler.postDelayed(new Runnable() {
                    @Override public void run() { performGlobalAction(GLOBAL_ACTION_RECENTS); }
                }, 300);
                break;
            case 18: // Pausar y Apagar Pantalla
                pauseMediaAndBlackScreen();
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
            Intent intent = new Intent(this, QuickMenuActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            Log.d(TAG, "Successfully opened Quick Menu");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open Quick Menu", e);
        }
    }

    private void openBluetoothSettings() {
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
        int currentPct = op.getInt("blue_light_pct", 0);
        int nextPct = currentPct == 0 ? 30 : 0; // Default toggle a 30%
        setBlueLightPct(nextPct);
    }

    private void setBlueLightLevel(int level) {
        // Compatibilidad para atrás
        int pct = level == 1 ? 20 : level == 2 ? 45 : level == 3 ? 70 : 0;
        setBlueLightPct(pct);
    }

    private void setBlueLightPct(int pct) {
        SharedPreferences op = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE);
        op.edit().putInt("blue_light_pct", pct).apply();
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
                    
                    if (blueLightOverlayView != null) {
                        wm.removeView(blueLightOverlayView);
                        blueLightOverlayView = null;
                    }

                    // Calculamos alpha basado en % (máximo 150 para que no tape completamente la pantalla)
                    int alpha = (int) ((pct / 100.0f) * 150);
                    if (alpha < 10) alpha = 10; // Nivel mínimo muy suave
                    
                    blueLightOverlayView = new View(ButtonMappingService.this);
                    blueLightOverlayView.setBackgroundColor(Color.argb(alpha, 240, 120, 0)); // Tono naranja cálido y suave
                    
                    wm.addView(blueLightOverlayView, overlayMatchParams());
                    blueLightShowRetries = 0; // Reset retries on success
                    getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_BLUE_LIGHT, true).apply();
                    Log.d(TAG, "Blue light overlay shown pct: " + pct + "% (alpha=" + alpha + ")");
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
        int pct = op.getInt("blue_light_pct", 30);
        showBlueLightOverlayPct(pct == 0 ? 30 : pct);
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
                    clockShowRetries = 0; // Reset retries on success
                    getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_CLOCK, true).apply();
                    handler.postDelayed(clockUpdateRunnable, 60000);
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
                    dimmerOverlayView = new View(ButtonMappingService.this);
                    dimmerOverlayView.setBackgroundColor(Color.argb(128, 0, 0, 0));
                    wm.addView(dimmerOverlayView, overlayMatchParams());
                    dimmerShowRetries = 0; // Reset retries on success
                    getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DIMMER, true).apply();
                    Log.d(TAG, "Dimmer overlay shown");
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
                int pct = getSharedPreferences(OVERLAY_PREFS, MODE_PRIVATE).getInt("blue_light_pct", 30);
                showBlueLightOverlayPct(pct == 0 ? 30 : pct);
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

    private void openAudioSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SOUND_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Opened audio settings");
        } catch (Exception e) { Log.e(TAG, "Failed to open audio settings", e); }
    }

    private void openScreenMirror() {
        String[] actions = {"android.settings.CAST_SETTINGS", Settings.ACTION_DISPLAY_SETTINGS};
        for (String action : actions) {
            try {
                Intent intent = new Intent(action);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d(TAG, "Opened screen mirror via: " + action);
                return;
            } catch (Exception ignored) {}
        }
        Log.e(TAG, "Failed to open screen mirror settings");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        Log.d(TAG, "onKeyEvent: keyCode=" + keyCode + ", action=" + action);

        // 1. If black screen is active, intercept wake-up logic first
        if (isBlackScreenActive) {
            // Allow volume and mute keys to pass through to the system
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                    || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                    || keyCode == 140) {
                return false;
            }

            // Special case: if the Netflix button (190) or YouTube button (189) is released after triggering the black screen,
            // consume it and do NOT wake up.
            if (keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == 190) {
                if (action == KeyEvent.ACTION_UP) {
                    Log.d(TAG, "YouTube 190 button UP (long press release) ignored to keep screen black.");
                    youtube190State.isPressed = false;
                    youtube190State.isLongPressTriggered = false;
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_2 || keyCode == 189) {
                if (action == KeyEvent.ACTION_UP) {
                    Log.d(TAG, "YouTube 189 button UP (long press release) ignored to keep screen black.");
                    youtube189State.isPressed = false;
                    youtube189State.isLongPressTriggered = false;
                }
                return true;
            }

            if (action == KeyEvent.ACTION_UP) {
                Log.d(TAG, "Wake up key released (" + keyCode + "). Dismissing black screen.");
                dismissBlackScreen();
            }
            return true; // Consume both DOWN and UP of any wake-up keys
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

        return super.onKeyEvent(event);
    }
}
