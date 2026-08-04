package com.example.togglegrayscale;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class QuickMenuOverlay {

    private static final String TAG = "QuickMenuOverlay";
    private static final String PREFS_ORDER = "menu_order_prefs";
    private static final String KEY_ORDER = "item_order";
    private static final String[] ALL_ITEM_IDS = {
        "manage_apps", "timer", "blue_light", "clock", "dimmer", "grayscale",
        "cine_mode", "auto_pause", "screen_off", "system_menu",
        "google_home", "bluetooth", "system_info", "reboot",
        "pause_screen_off", "scheduled_sleep", "cycle_brightness", "still_watching",
        "night_schedule", "oled_saver",
        "config_mute", "config_youtube_190", "config_youtube_189",
        "developer_options"
    };

    private static final String[] ACTION_NAMES = {
        "Ninguna",
        "Silenciar Audio",
        "Escala de Grises (B/N)",
        "Apagar Pantalla (Negro)",
        "Google Home Panel",
        "YouTube",
        "Netflix",
        "Auriculares Bluetooth",
        "Menú de Acciones",
        "Filtro Luz Azul",
        "Reloj en Pantalla",
        "Dimmer de Pantalla",
        "Modo Cine",
        "Info del Sistema",
        "Reiniciar Chromecast",
        "Ajustes del Sistema",
        "Pantalla Espejo",
        "Recientes",
        "Pausar y Apagar Pantalla",
        "Bajar Brillo (Dimmer)",
        "Subir Brillo (Dimmer)",
        "Ciclar Brillo",
        "¿Sigues viendo?",
        "Opciones de Desarrollador"
    };

    private static final String[] STILL_WATCHING_POSITIONS = {"Arriba Izquierda", "Arriba Derecha", "Abajo Izquierda", "Abajo Derecha", "Centro"};
    private static final String[] STILL_WATCHING_ACTIONS = {"Pausar Video", "Pausar y Apagar Pantalla", "Enviar Tecla Atrás"};

    private static final String[] CLOCK_SIZE_NAMES = {"Chico (12sp)", "Mediano (16sp)", "Grande (20sp)", "Extra Grande (24sp)"};
    private static final String[] CLOCK_COLOR_NAMES = {"Blanco", "Negro", "Amarillo", "Rojo", "Verde", "Azul"};
    private static final String[] CLOCK_BG_NAMES = {"Negro", "Gris Oscuro", "Azul Marino", "Ninguno (Transparente)"};
    private static final String[] CLOCK_ALPHA_NAMES = {"0% (Transparente)", "25%", "35%", "50%", "75%", "100% (Opaco)"};
    private static final String[] CLOCK_POSITION_NAMES = {"Arriba Derecha", "Arriba Izquierda", "Abajo Derecha", "Abajo Izquierda", "Centro"};

    private static QuickMenuOverlay instance;

    private Context context;
    private WindowManager windowManager;
    private View rootView;

    private LinearLayout menuContainer;
    private LinearLayout panelTimer;
    private LinearLayout panelCine;
    private LinearLayout panelBlueLight;
    private TextView btnApplyBlueLight;
    private LinearLayout panelBrightness;
    private SeekBar sliderBrightness;
    private TextView txtBrightnessPct;
    private LinearLayout containerBrightnessLevels;
    private TextView btnAddBrightnessLevel;
    private TextView btnApplyBrightness;
    private LinearLayout panelButtonConfig;
    private LinearLayout panelAutoPause;

    private LinearLayout panelStillWatching;
    private TextView btnStillWatchingToggle;
    private TextView btnStillWatchingIntervalDec, btnStillWatchingIntervalInc, txtStillWatchingInterval;
    private TextView btnStillWatchingTimeoutDec, btnStillWatchingTimeoutInc, txtStillWatchingTimeout;
    private TextView btnStillWatchingActionType;
    private TextView btnStillWatchingPosition;
    private TextView btnStillWatchingAlphaDec, btnStillWatchingAlphaInc, txtStillWatchingAlpha;
    private TextView btnStillWatchingSizeDec, btnStillWatchingSizeInc, txtStillWatchingSize;
    private TextView btnStillWatchingXDec, btnStillWatchingXInc, txtStillWatchingX;
    private TextView btnStillWatchingYDec, btnStillWatchingYInc, txtStillWatchingY;
    private TextView btnApplyStillWatching;

    private LinearLayout panelNightSchedule;
    private TextView btnNightScheduleToggle;
    private TextView btnNightStartDec, txtNightStart, btnNightStartInc;
    private TextView btnNightEndDec, txtNightEnd, btnNightEndInc;
    private TextView btnNightBlueLightDec, txtNightBlueLight, btnNightBlueLightInc;
    private TextView btnNightDimmerDec, txtNightDimmer, btnNightDimmerInc;
    private TextView btnApplyNightSchedule;

    private LinearLayout panelOledSaver;
    private TextView btnOledSaverToggle;
    private TextView btnOledMinutesDec, txtOledMinutes, btnOledMinutesInc;
    private TextView btnOledMode;
    private TextView btnApplyOledSaver;

    private LinearLayout panelScheduledSleep;
    private TextView btnScheduledSleepToggle;
    private TextView btnScheduledHourDec, txtScheduledHour, btnScheduledHourInc;
    private TextView btnScheduledMinDec, txtScheduledMin, btnScheduledMinInc;
    private TextView btnDay1, btnDay2, btnDay3, btnDay4, btnDay5, btnDay6, btnDay7;
    private TextView btnScheduledSkipNext;
    private TextView btnApplyScheduledSleep;

    private TextView btnAutoPauseMode;
    private LinearLayout layoutAutoPauseCustom;
    private TextView btnAutoPauseCountDec;
    private TextView txtAutoPauseCount;
    private TextView btnAutoPauseCountInc;
    private TextView btnAutoPauseBlackScreen;

    private TextView btnCancelTimer;
    private TextView btnCineBlueLightConfig;
    private TextView btnCineDimmerConfig;
    private TextView btnCineTimerConfig;
    private TextView btnApplyCine;

    // Button Config Panel Fields
    private TextView txtConfigTitle;
    private TextView btnConfigClick1;
    private TextView btnConfigClick2;
    private TextView btnConfigClick3;
    private TextView btnConfigLong;
    private TextView btnConfigDurationDec;
    private TextView btnConfigDurationInc;
    private TextView txtConfigDuration;
    private String configuringButton = null;

    // Clock Config Panel Fields
    private LinearLayout panelClockConfig;
    private TextView btnClockTextColor;
    private TextView btnClockBgColor;
    private TextView btnClockAlphaDec;
    private TextView btnClockAlphaInc;
    private TextView txtClockAlpha;
    private TextView btnClockTextAlphaDec;
    private TextView btnClockTextAlphaInc;
    private TextView txtClockTextAlpha;
    private TextView btnClockPosition;
    private TextView btnClockSizeDec;
    private TextView btnClockSizeInc;
    private TextView txtClockSize;
    private TextView btnClockPadDec;
    private TextView btnClockPadInc;
    private TextView txtClockPad;
    private TextView btnClockXDec;
    private TextView btnClockXInc;
    private TextView txtClockX;
    private TextView btnClockYDec;
    private TextView btnClockYInc;
    private TextView txtClockY;
    private TextView btnApplyClock;

    // Custom Timer fields
    private TextView txtCustomHours;
    private TextView txtCustomMins;
    private int customHours = 0;
    private int customMins = 0;

    // SeekBar fields
    private SeekBar sliderBlueLight;
    private TextView txtBlueLightPct;

    private boolean isReorderMode = false;
    private int reorderSelectedIndex = -1;
    private String openSubPanel = null;
    private String lastFocusedId = null;

    public static synchronized QuickMenuOverlay getInstance() {
        if (instance == null) {
            instance = new QuickMenuOverlay();
        }
        return instance;
    }

    public boolean isShowing() {
        return rootView != null && rootView.isAttachedToWindow();
    }

    public void toggle(Context context) {
        if (isShowing()) {
            dismiss();
        } else {
            show(context);
        }
    }

    public void show(Context ctx) {
        if (isShowing()) {
            dismiss();
        }

        this.context = ctx;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        try {
            rootView = LayoutInflater.from(context).inflate(R.layout.activity_quick_menu, null);

            SharedPreferences op = getOverlayPrefs();
            if (op.getBoolean(ButtonMappingService.KEY_DIMMER, false)) {
                int brightnessPct = op.getInt("dimmer_brightness_pct", 50);
                int alphaVal = (int) ((100 - brightnessPct) * 2.55);
                rootView.setBackgroundColor(Color.argb(alphaVal, 0, 0, 0));
            }

            // Bind backdrop to dismiss on click outside panel
            rootView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });

            menuContainer          = rootView.findViewById(R.id.menu_container);
            panelTimer             = rootView.findViewById(R.id.panel_timer);
            panelCine              = rootView.findViewById(R.id.panel_cine);
            panelBlueLight         = rootView.findViewById(R.id.panel_blue_light);

            btnCancelTimer         = rootView.findViewById(R.id.btn_cancel_timer);
            btnCineBlueLightConfig = rootView.findViewById(R.id.btn_cine_blue_light_config);
            btnCineDimmerConfig    = rootView.findViewById(R.id.btn_cine_dimmer_config);
            btnCineTimerConfig     = rootView.findViewById(R.id.btn_cine_timer_config);
            btnApplyCine           = rootView.findViewById(R.id.btn_apply_cine);
            btnApplyBlueLight      = rootView.findViewById(R.id.btn_apply_blue_light);

            panelAutoPause         = rootView.findViewById(R.id.panel_auto_pause);
            btnAutoPauseMode       = rootView.findViewById(R.id.btn_auto_pause_mode);
            layoutAutoPauseCustom  = rootView.findViewById(R.id.layout_auto_pause_custom);
            btnAutoPauseCountDec   = rootView.findViewById(R.id.btn_auto_pause_count_dec);
            txtAutoPauseCount      = rootView.findViewById(R.id.txt_auto_pause_count);
            btnAutoPauseCountInc   = rootView.findViewById(R.id.btn_auto_pause_count_inc);
            btnAutoPauseBlackScreen = rootView.findViewById(R.id.btn_auto_pause_black_screen);

            txtCustomHours         = rootView.findViewById(R.id.txt_custom_hours);
            txtCustomMins          = rootView.findViewById(R.id.txt_custom_mins);

            sliderBlueLight        = rootView.findViewById(R.id.slider_blue_light);
            if (sliderBlueLight != null) {
                sliderBlueLight.setMax(800);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    sliderBlueLight.setKeyProgressIncrement(1);
                }
            }
            txtBlueLightPct        = rootView.findViewById(R.id.txt_blue_light_pct);

            panelBrightness      = rootView.findViewById(R.id.panel_brightness);
            sliderBrightness     = rootView.findViewById(R.id.slider_brightness);
            txtBrightnessPct     = rootView.findViewById(R.id.txt_brightness_pct);
            containerBrightnessLevels = rootView.findViewById(R.id.container_brightness_levels);
            btnAddBrightnessLevel    = rootView.findViewById(R.id.btn_add_brightness_level);
            btnApplyBrightness       = rootView.findViewById(R.id.btn_apply_brightness);

            panelButtonConfig    = rootView.findViewById(R.id.panel_button_config);
            txtConfigTitle       = rootView.findViewById(R.id.txt_config_title);
            btnConfigClick1      = rootView.findViewById(R.id.btn_config_click_1);
            btnConfigClick2      = rootView.findViewById(R.id.btn_config_click_2);
            btnConfigClick3      = rootView.findViewById(R.id.btn_config_click_3);
            btnConfigLong        = rootView.findViewById(R.id.btn_config_long);
            btnConfigDurationDec = rootView.findViewById(R.id.btn_config_duration_dec);
            btnConfigDurationInc = rootView.findViewById(R.id.btn_config_duration_inc);
            txtConfigDuration    = rootView.findViewById(R.id.txt_config_duration);

            panelClockConfig     = rootView.findViewById(R.id.panel_clock_config);
            btnClockTextColor    = rootView.findViewById(R.id.btn_clock_text_color);
            btnClockBgColor      = rootView.findViewById(R.id.btn_clock_bg_color);
            btnClockAlphaDec     = rootView.findViewById(R.id.btn_clock_alpha_dec);
            btnClockAlphaInc     = rootView.findViewById(R.id.btn_clock_alpha_inc);
            txtClockAlpha        = rootView.findViewById(R.id.txt_clock_alpha);
            btnClockTextAlphaDec = rootView.findViewById(R.id.btn_clock_text_alpha_dec);
            btnClockTextAlphaInc = rootView.findViewById(R.id.btn_clock_text_alpha_inc);
            txtClockTextAlpha    = rootView.findViewById(R.id.txt_clock_text_alpha);
            btnClockPosition     = rootView.findViewById(R.id.btn_clock_position);
            btnClockSizeDec      = rootView.findViewById(R.id.btn_clock_size_dec);
            btnClockSizeInc      = rootView.findViewById(R.id.btn_clock_size_inc);
            txtClockSize         = rootView.findViewById(R.id.txt_clock_size);
            btnClockPadDec       = rootView.findViewById(R.id.btn_clock_pad_dec);
            btnClockPadInc       = rootView.findViewById(R.id.btn_clock_pad_inc);
            txtClockPad          = rootView.findViewById(R.id.txt_clock_pad);
            btnClockXDec         = rootView.findViewById(R.id.btn_clock_x_dec);
            btnClockXInc         = rootView.findViewById(R.id.btn_clock_x_inc);
            txtClockX            = rootView.findViewById(R.id.txt_clock_x);
            btnClockYDec         = rootView.findViewById(R.id.btn_clock_y_dec);
            btnClockYInc         = rootView.findViewById(R.id.btn_clock_y_inc);
            txtClockY            = rootView.findViewById(R.id.txt_clock_y);
            btnApplyClock        = rootView.findViewById(R.id.btn_apply_clock);

            panelStillWatching          = rootView.findViewById(R.id.panel_still_watching);
            btnStillWatchingToggle       = rootView.findViewById(R.id.btn_still_watching_action_toggle);
            btnStillWatchingIntervalDec  = rootView.findViewById(R.id.btn_still_watching_interval_dec);
            txtStillWatchingInterval     = rootView.findViewById(R.id.txt_still_watching_interval);
            btnStillWatchingIntervalInc  = rootView.findViewById(R.id.btn_still_watching_interval_inc);
            btnStillWatchingTimeoutDec   = rootView.findViewById(R.id.btn_still_watching_timeout_dec);
            txtStillWatchingTimeout      = rootView.findViewById(R.id.txt_still_watching_timeout);
            btnStillWatchingTimeoutInc   = rootView.findViewById(R.id.btn_still_watching_timeout_inc);
            btnStillWatchingActionType   = rootView.findViewById(R.id.btn_still_watching_action_type);
            btnStillWatchingPosition     = rootView.findViewById(R.id.btn_still_watching_position);
            btnStillWatchingAlphaDec     = rootView.findViewById(R.id.btn_still_watching_alpha_dec);
            txtStillWatchingAlpha        = rootView.findViewById(R.id.txt_still_watching_alpha);
            btnStillWatchingAlphaInc     = rootView.findViewById(R.id.btn_still_watching_alpha_inc);
            btnStillWatchingSizeDec      = rootView.findViewById(R.id.btn_still_watching_size_dec);
            txtStillWatchingSize         = rootView.findViewById(R.id.txt_still_watching_size);
            btnStillWatchingSizeInc      = rootView.findViewById(R.id.btn_still_watching_size_inc);
            btnStillWatchingXDec         = rootView.findViewById(R.id.btn_still_watching_x_dec);
            txtStillWatchingX            = rootView.findViewById(R.id.txt_still_watching_x);
            btnStillWatchingXInc         = rootView.findViewById(R.id.btn_still_watching_x_inc);
            btnStillWatchingYDec         = rootView.findViewById(R.id.btn_still_watching_y_dec);
            txtStillWatchingY            = rootView.findViewById(R.id.txt_still_watching_y);
            btnStillWatchingYInc         = rootView.findViewById(R.id.btn_still_watching_y_inc);
            btnApplyStillWatching        = rootView.findViewById(R.id.btn_apply_still_watching);

            panelNightSchedule           = rootView.findViewById(R.id.panel_night_schedule);
            btnNightScheduleToggle       = rootView.findViewById(R.id.btn_night_schedule_toggle);
            btnNightStartDec             = rootView.findViewById(R.id.btn_night_start_dec);
            txtNightStart                = rootView.findViewById(R.id.txt_night_start);
            btnNightStartInc             = rootView.findViewById(R.id.btn_night_start_inc);
            btnNightEndDec               = rootView.findViewById(R.id.btn_night_end_dec);
            txtNightEnd                  = rootView.findViewById(R.id.txt_night_end);
            btnNightEndInc               = rootView.findViewById(R.id.btn_night_end_inc);
            btnNightBlueLightDec         = rootView.findViewById(R.id.btn_night_blue_light_dec);
            txtNightBlueLight            = rootView.findViewById(R.id.txt_night_blue_light);
            btnNightBlueLightInc         = rootView.findViewById(R.id.btn_night_blue_light_inc);
            btnNightDimmerDec            = rootView.findViewById(R.id.btn_night_dimmer_dec);
            txtNightDimmer               = rootView.findViewById(R.id.txt_night_dimmer);
            btnNightDimmerInc            = rootView.findViewById(R.id.btn_night_dimmer_inc);
            btnApplyNightSchedule        = rootView.findViewById(R.id.btn_apply_night_schedule);

            panelOledSaver               = rootView.findViewById(R.id.panel_oled_saver);
            btnOledSaverToggle           = rootView.findViewById(R.id.btn_oled_saver_toggle);
            btnOledMinutesDec            = rootView.findViewById(R.id.btn_oled_minutes_dec);
            txtOledMinutes               = rootView.findViewById(R.id.txt_oled_minutes);
            btnOledMinutesInc            = rootView.findViewById(R.id.btn_oled_minutes_inc);
            btnOledMode                  = rootView.findViewById(R.id.btn_oled_mode);
            btnApplyOledSaver            = rootView.findViewById(R.id.btn_apply_oled_saver);

            panelScheduledSleep          = rootView.findViewById(R.id.panel_scheduled_sleep);
            btnScheduledSleepToggle      = rootView.findViewById(R.id.btn_scheduled_sleep_toggle);
            btnScheduledHourDec          = rootView.findViewById(R.id.btn_scheduled_hour_dec);
            txtScheduledHour             = rootView.findViewById(R.id.txt_scheduled_hour);
            btnScheduledHourInc          = rootView.findViewById(R.id.btn_scheduled_hour_inc);
            btnScheduledMinDec           = rootView.findViewById(R.id.btn_scheduled_min_dec);
            txtScheduledMin              = rootView.findViewById(R.id.txt_scheduled_min);
            btnScheduledMinInc           = rootView.findViewById(R.id.btn_scheduled_min_inc);
            btnDay1                      = rootView.findViewById(R.id.btn_day_1);
            btnDay2                      = rootView.findViewById(R.id.btn_day_2);
            btnDay3                      = rootView.findViewById(R.id.btn_day_3);
            btnDay4                      = rootView.findViewById(R.id.btn_day_4);
            btnDay5                      = rootView.findViewById(R.id.btn_day_5);
            btnDay6                      = rootView.findViewById(R.id.btn_day_6);
            btnDay7                      = rootView.findViewById(R.id.btn_day_7);
            btnScheduledSkipNext         = rootView.findViewById(R.id.btn_scheduled_skip_next);
            btnApplyScheduledSleep       = rootView.findViewById(R.id.btn_apply_scheduled_sleep);

            setupSubPanelListeners();
            buildMenu();

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            );

            windowManager.addView(rootView, p);
            sendServiceAction("ACTION_REORDER_OVERLAYS");
            Log.d(TAG, "QuickMenuOverlay attached to WindowManager successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error displaying QuickMenuOverlay", e);
            dismiss();
        }
    }

    private final android.os.Handler mHoldHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int mHoldingKeyCode = -1;
    private int mHoldTickCount = 0;
    private Runnable mHoldRunnable;

    private void startHoldRepeat(final int keyCode, final KeyEvent event) {
        stopHoldRepeat();
        mHoldingKeyCode = keyCode;
        mHoldTickCount = 0;

        mHoldRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isShowing() || mHoldingKeyCode != keyCode) return;
                mHoldTickCount++;
                KeyEvent synthEvent = new KeyEvent(
                    android.os.SystemClock.uptimeMillis(),
                    android.os.SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    mHoldTickCount
                );
                handleOverlayNavigation(keyCode, KeyEvent.ACTION_DOWN, synthEvent);

                int nextDelay;
                if (mHoldTickCount > 25) {
                    nextDelay = 35;
                } else if (mHoldTickCount > 15) {
                    nextDelay = 60;
                } else if (mHoldTickCount > 8) {
                    nextDelay = 100;
                } else {
                    nextDelay = 160;
                }
                mHoldHandler.postDelayed(this, nextDelay);
            }
        };
        mHoldHandler.postDelayed(mHoldRunnable, 300);
    }

    private void stopHoldRepeat() {
        mHoldingKeyCode = -1;
        mHoldTickCount = 0;
        if (mHoldRunnable != null) {
            mHoldHandler.removeCallbacks(mHoldRunnable);
            mHoldRunnable = null;
        }
    }

    public void dismiss() {
        stopHoldRepeat();
        if (rootView != null) {
            final View v = rootView;
            rootView = null;
            openSubPanel = null;
            configuringButton = null;
            isReorderMode = false;
            reorderSelectedIndex = -1;
            try {
                if (windowManager != null) {
                    windowManager.removeView(v);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing QuickMenuOverlay view", e);
            }
        }
    }

    public boolean onKeyEvent(KeyEvent event) {
        if (!isShowing()) return false;

        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                stopHoldRepeat();
                if (openSubPanel != null) {
                    closeSubPanels();
                    buildMenu();
                    return true;
                }
                if (isReorderMode) {
                    isReorderMode = false;
                    reorderSelectedIndex = -1;
                    buildMenu();
                    return true;
                }
                dismiss();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    boolean handled = handleOverlayNavigation(keyCode, action, event);
                    if (handled) {
                        startHoldRepeat(keyCode, event);
                        return true;
                    } else if (openSubPanel != null && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                        startHoldRepeat(keyCode, event);
                    }
                } else {
                    return true;
                }
            } else if (action == KeyEvent.ACTION_UP) {
                if (keyCode == mHoldingKeyCode) {
                    stopHoldRepeat();
                }
            }
        }

        if (rootView != null) {
            return rootView.dispatchKeyEvent(event);
        }
        return true;
    }

    private ViewGroup getActiveSubPanelGroup() {
        if (openSubPanel == null) return menuContainer;
        switch (openSubPanel) {
            case "timer": return panelTimer;
            case "blue_light": return panelBlueLight;
            case "clock_config": return panelClockConfig;
            case "brightness_config": return panelBrightness;
            case "cine": return panelCine;
            case "auto_pause": return panelAutoPause;
            case "still_watching": return panelStillWatching;
            case "night_schedule": return panelNightSchedule;
            case "oled_saver": return panelOledSaver;
            case "scheduled_sleep": return panelScheduledSleep;
            default: return panelButtonConfig;
        }
    }

    private static class ViewRow {
        int y;
        java.util.List<View> views = new java.util.ArrayList<>();
    }

    private boolean handleOverlayNavigation(int keyCode, int action, KeyEvent event) {
        if (action != KeyEvent.ACTION_DOWN) return true;
        if (rootView == null) return false;

        ViewGroup container = getActiveSubPanelGroup();
        if (container == null || container.getVisibility() != View.VISIBLE) return false;

        View current = rootView.findFocus();

        // 1. Special handling for SeekBar progress adjustments with holding acceleration
        if (current instanceof SeekBar && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            SeekBar sb = (SeekBar) current;
            int repeat = event.getRepeatCount();
            int step;
            if (sb.getMax() > 100) {
                if (repeat > 15) step = 15;
                else if (repeat > 8) step = 8;
                else if (repeat > 3) step = 3;
                else step = 1;
            } else {
                if (repeat > 10) step = 4;
                else if (repeat > 4) step = 2;
                else step = 1;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                sb.setProgress(Math.max(0, sb.getProgress() - step));
            } else {
                sb.setProgress(Math.min(sb.getMax(), sb.getProgress() + step));
            }
            return true;
        }

        // 2. Gather focusable views in active container
        java.util.List<View> focusables = new java.util.ArrayList<>();
        findFocusableChildren(container, focusables);
        if (focusables.isEmpty()) return false;

        // 3. Group focusables into visual rows by Y-screen position
        java.util.List<ViewRow> rows = new java.util.ArrayList<>();
        for (View v : focusables) {
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            int y = loc[1];
            ViewRow matchedRow = null;
            for (ViewRow r : rows) {
                if (Math.abs(r.y - y) <= 18) {
                    matchedRow = r;
                    break;
                }
            }
            if (matchedRow == null) {
                matchedRow = new ViewRow();
                matchedRow.y = y;
                rows.add(matchedRow);
            }
            matchedRow.views.add(v);
        }

        // Sort rows vertically top-to-bottom
        java.util.Collections.sort(rows, new java.util.Comparator<ViewRow>() {
            @Override
            public int compare(ViewRow r1, ViewRow r2) {
                return Integer.compare(r1.y, r2.y);
            }
        });

        // Sort views in each row horizontally left-to-right
        for (ViewRow r : rows) {
            java.util.Collections.sort(r.views, new java.util.Comparator<View>() {
                @Override
                public int compare(View v1, View v2) {
                    int[] l1 = new int[2];
                    int[] l2 = new int[2];
                    v1.getLocationOnScreen(l1);
                    v2.getLocationOnScreen(l2);
                    return Integer.compare(l1[0], l2[0]);
                }
            });
        }

        int rIdx = -1, cIdx = -1;
        if (current != null) {
            for (int r = 0; r < rows.size(); r++) {
                int c = rows.get(r).views.indexOf(current);
                if (c >= 0) {
                    rIdx = r;
                    cIdx = c;
                    break;
                }
            }
        }

        if (rIdx < 0 || cIdx < 0) {
            if (!rows.isEmpty() && !rows.get(0).views.isEmpty()) {
                requestViewFocus(rows.get(0).views.get(0));
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int nextR = (rIdx + 1) % rows.size();
            ViewRow targetRow = rows.get(nextR);
            View bestView = findClosestViewByX(targetRow.views, current);
            requestViewFocus(bestView);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            int nextR = (rIdx - 1 + rows.size()) % rows.size();
            ViewRow targetRow = rows.get(nextR);
            View bestView = findClosestViewByX(targetRow.views, current);
            requestViewFocus(bestView);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            ViewRow curRow = rows.get(rIdx);
            if (cIdx < curRow.views.size() - 1) {
                requestViewFocus(curRow.views.get(cIdx + 1));
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            ViewRow curRow = rows.get(rIdx);
            if (cIdx > 0) {
                requestViewFocus(curRow.views.get(cIdx - 1));
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (current != null && event.getRepeatCount() > 0) {
                current.performClick();
                return true;
            }
        }

        return false;
    }

    private View findClosestViewByX(java.util.List<View> targetViews, View current) {
        if (targetViews == null || targetViews.isEmpty()) return null;
        if (current == null) return targetViews.get(0);
        int[] curLoc = new int[2];
        current.getLocationOnScreen(curLoc);
        int curCenterX = curLoc[0] + current.getWidth() / 2;

        View best = targetViews.get(0);
        int minDiff = Integer.MAX_VALUE;
        for (View v : targetViews) {
            int[] vLoc = new int[2];
            v.getLocationOnScreen(vLoc);
            int vCenterX = vLoc[0] + v.getWidth() / 2;
            int diff = Math.abs(vCenterX - curCenterX);
            if (diff < minDiff) {
                minDiff = diff;
                best = v;
            }
        }
        return best;
    }

    private void requestViewFocus(View v) {
        if (v == null) return;
        v.requestFocus();
        android.view.ViewParent parent = v.getParent();
        while (parent != null) {
            if (parent instanceof android.widget.ScrollView) {
                final android.widget.ScrollView sv = (android.widget.ScrollView) parent;
                int[] vLoc = new int[2];
                int[] svLoc = new int[2];
                v.getLocationOnScreen(vLoc);
                sv.getLocationOnScreen(svLoc);
                int relativeY = vLoc[1] - svLoc[1] + sv.getScrollY();
                int targetY = Math.max(0, relativeY - (sv.getHeight() - v.getHeight()) / 2);
                sv.smoothScrollTo(0, targetY);
                break;
            }
            parent = parent.getParent();
        }
    }

    private void findFocusableChildren(ViewGroup group, java.util.List<View> out) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.isFocusable()) {
                out.add(child);
            }
            if (child instanceof ViewGroup && child.getVisibility() == View.VISIBLE) {
                findFocusableChildren((ViewGroup) child, out);
            }
        }
    }

    private void focusSubPanel(String subPanelId) {
        if (subPanelId == null) return;
        switch (subPanelId) {
            case "timer":
                if (panelTimer != null) {
                    View v = panelTimer.findViewById(R.id.btn_5m);
                    if (v != null) v.requestFocus();
                }
                break;
            case "blue_light":
                if (sliderBlueLight != null) sliderBlueLight.requestFocus();
                break;
            case "clock_config":
                if (btnClockTextColor != null) btnClockTextColor.requestFocus();
                break;
            case "brightness_config":
                if (sliderBrightness != null) sliderBrightness.requestFocus();
                break;
            case "cine":
                if (btnCineBlueLightConfig != null) btnCineBlueLightConfig.requestFocus();
                break;
            case "auto_pause":
                if (btnAutoPauseMode != null) btnAutoPauseMode.requestFocus();
                break;
            case "still_watching":
                if (btnStillWatchingToggle != null) btnStillWatchingToggle.requestFocus();
                break;
            case "night_schedule":
                if (btnNightScheduleToggle != null) btnNightScheduleToggle.requestFocus();
                break;
            case "oled_saver":
                if (btnOledSaverToggle != null) btnOledSaverToggle.requestFocus();
                break;
            default:
                if (panelButtonConfig != null && panelButtonConfig.getVisibility() == View.VISIBLE) {
                    if (btnConfigClick1 != null) btnConfigClick1.requestFocus();
                }
                break;
        }
    }

    private SharedPreferences getOverlayPrefs() {
        return context.getSharedPreferences(ButtonMappingService.OVERLAY_PREFS, Context.MODE_PRIVATE);
    }

    private void sendServiceAction(String action) {
        sendServiceAction(action, null);
    }

    private void sendServiceAction(String action, Bundle extras) {
        ButtonMappingService service = ButtonMappingService.instance;
        if (service != null) {
            service.handleAction(action, extras);
        } else {
            Intent i = new Intent(context, ButtonMappingService.class);
            i.setAction(action);
            if (extras != null) i.putExtras(extras);
            context.startService(i);
        }
    }

    private void setupSubPanelListeners() {
        // Preset Timers
        int[] timerIds  = {R.id.btn_5m, R.id.btn_15m, R.id.btn_30m, R.id.btn_45m, R.id.btn_1h, R.id.btn_2h, R.id.btn_2h30m, R.id.btn_3h};
        int[] timerMins = {5, 15, 30, 45, 60, 120, 150, 180};
        for (int i = 0; i < timerIds.length; i++) {
            final int mins = timerMins[i];
            View v = rootView.findViewById(timerIds[i]);
            if (v != null) v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { startTimer(mins); }
            });
        }

        // Custom Timer controls
        View btnHDec = rootView.findViewById(R.id.btn_hours_dec);
        View btnHInc = rootView.findViewById(R.id.btn_hours_inc);
        View btnMDec = rootView.findViewById(R.id.btn_mins_dec);
        View btnMInc = rootView.findViewById(R.id.btn_mins_inc);
        View btnStartCustom = rootView.findViewById(R.id.btn_start_custom_timer);

        if (btnHDec != null) btnHDec.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (customHours > 0) { customHours--; updateCustomTimerUI(); } }
        });
        if (btnHInc != null) btnHInc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (customHours < 24) { customHours++; updateCustomTimerUI(); } }
        });
        if (btnMDec != null) btnMDec.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (customMins >= 1) { customMins--; updateCustomTimerUI(); } }
        });
        if (btnMInc != null) btnMInc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (customMins <= 58) { customMins++; updateCustomTimerUI(); } }
        });
        if (btnStartCustom != null) btnStartCustom.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int totalMins = (customHours * 60) + customMins;
                if (totalMins > 0) startTimer(totalMins);
            }
        });

        if (btnCancelTimer != null) {
            btnCancelTimer.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = new Intent(context, SleepTimerService.class);
                    i.setAction("ACTION_CANCEL_TIMER");
                    context.startService(i);
                    closeSubPanels();
                    buildMenu();
                }
            });
        }

        // SeekBar (Blue light filter)
        if (sliderBlueLight != null) {
            SharedPreferences op = getOverlayPrefs();
            int currentPct = op.getInt("blue_light_pct", 50);
            if (currentPct == 0) currentPct = 50;
            sliderBlueLight.setProgress(currentPct);

            sliderBlueLight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    Bundle b = new Bundle();
                    b.putInt("pct", progress);
                    sendServiceAction("ACTION_SET_BLUE_LIGHT_PCT", b);
                    if (progress > 0) {
                        getOverlayPrefs().edit().putInt("blue_light_pct", progress).apply();
                    }
                    if (txtBlueLightPct != null) {
                        double displayPct = progress / 10.0;
                        txtBlueLightPct.setText("Nivel: " + (progress > 0 ? String.format(java.util.Locale.US, "%.1f%%", displayPct) : "Desactivado"));
                    }
                    if (btnApplyBlueLight != null) {
                        btnApplyBlueLight.setText(progress > 0 ? "[ Desactivar Filtro ]" : "[ Activar Filtro ]");
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (btnApplyBlueLight != null) {
            btnApplyBlueLight.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences prefs = getOverlayPrefs();
                    boolean active = prefs.getBoolean(ButtonMappingService.KEY_BLUE_LIGHT, false);
                    if (active) {
                        Bundle b = new Bundle();
                        b.putInt("pct", 0);
                        sendServiceAction("ACTION_SET_BLUE_LIGHT_PCT", b);
                    } else {
                        int lastPct = prefs.getInt("blue_light_pct", 50);
                        if (lastPct == 0) lastPct = 50;
                        Bundle b = new Bundle();
                        b.putInt("pct", lastPct);
                        sendServiceAction("ACTION_SET_BLUE_LIGHT_PCT", b);
                    }
                    updateBlueLightConfigPanel();
                    btnApplyBlueLight.requestFocus();
                    buildMenu();
                }
            });
        }

        // SeekBar (Brightness / Dimmer)
        if (sliderBrightness != null) {
            SharedPreferences op = getOverlayPrefs();
            int currentPct = op.getInt("dimmer_brightness_pct", 50);
            sliderBrightness.setProgress(currentPct);
            if (txtBrightnessPct != null) txtBrightnessPct.setText("Nivel: " + currentPct + "%");

            sliderBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (txtBrightnessPct != null) txtBrightnessPct.setText("Nivel: " + progress + "%");
                    Bundle b = new Bundle();
                    b.putInt("pct", progress);
                    sendServiceAction("ACTION_SET_DIMMER_BRIGHTNESS", b);
                    getOverlayPrefs().edit().putInt("dimmer_brightness_pct", progress).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (btnAddBrightnessLevel != null) {
            btnAddBrightnessLevel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences prefs = getOverlayPrefs();
                    String listStr = prefs.getString("brightness_levels_list", "80,50,20");
                    String[] parts = listStr.split(",");
                    if (parts.length < 8) {
                        String lastVal = parts.length > 0 ? parts[parts.length - 1] : "50";
                        String newList = listStr + "," + lastVal;
                        prefs.edit().putString("brightness_levels_list", newList).apply();
                        updateBrightnessConfigPanel();
                        btnAddBrightnessLevel.requestFocus();
                    } else {
                        android.widget.Toast.makeText(context, "Máximo 8 niveles de brillo admitidos", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        if (btnApplyBrightness != null) {
            btnApplyBrightness.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleOverlay(ButtonMappingService.KEY_DIMMER, "ACTION_TOGGLE_DIMMER");
                    updateBrightnessConfigPanel();
                    btnApplyBrightness.requestFocus();
                }
            });
        }

        // Cine Config panel
        if (btnCineBlueLightConfig != null) {
            btnCineBlueLightConfig.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SharedPreferences cp = context.getSharedPreferences("cine_prefs", Context.MODE_PRIVATE);
                    cp.edit().putBoolean("cine_blue_light", !cp.getBoolean("cine_blue_light", true)).apply();
                    updateCineConfigPanel();
                }
            });
        }
        if (btnCineDimmerConfig != null) {
            btnCineDimmerConfig.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SharedPreferences cp = context.getSharedPreferences("cine_prefs", Context.MODE_PRIVATE);
                    cp.edit().putBoolean("cine_dimmer", !cp.getBoolean("cine_dimmer", false)).apply();
                    updateCineConfigPanel();
                }
            });
        }
        if (btnCineTimerConfig != null) {
            btnCineTimerConfig.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SharedPreferences cp = context.getSharedPreferences("cine_prefs", Context.MODE_PRIVATE);
                    int cur  = cp.getInt("cine_timer", 0);
                    int next = cur == 0 ? 30 : cur == 30 ? 60 : cur == 60 ? 90 : cur == 90 ? 120 : cur == 120 ? 150 : cur == 150 ? 180 : 0;
                    cp.edit().putInt("cine_timer", next).apply();
                    updateCineConfigPanel();
                }
            });
        }
        if (btnApplyCine != null) {
            btnApplyCine.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SharedPreferences op = getOverlayPrefs();
                    boolean cur = op.getBoolean(ButtonMappingService.KEY_CINE_MODE, false);
                    op.edit().putBoolean(ButtonMappingService.KEY_CINE_MODE, !cur).apply();
                    sendServiceAction("ACTION_TOGGLE_CINE_MODE");
                    closeSubPanels();
                    buildMenu();
                }
            });
        }
        
        // Button Config Panel Listeners
        if (btnConfigClick1 != null) {
            btnConfigClick1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (configuringButton == null) return;
                    String key = "btn_" + configuringButton + "_click_1_action";
                    int def = "mute".equals(configuringButton) ? 1 : 5;
                    int cur = getOverlayPrefs().getInt(key, def);
                    cycleActionConfig(key, cur);
                }
            });
        }
        if (btnConfigClick2 != null) {
            btnConfigClick2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (configuringButton == null) return;
                    String key = "btn_" + configuringButton + "_click_2_action";
                    int def = "mute".equals(configuringButton) ? 7 : ("youtube_190".equals(configuringButton) ? 4 : 0);
                    int cur = getOverlayPrefs().getInt(key, def);
                    cycleActionConfig(key, cur);
                }
            });
        }
        if (btnConfigClick3 != null) {
            btnConfigClick3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (configuringButton == null) return;
                    String key = "btn_" + configuringButton + "_click_3_action";
                    int def = "mute".equals(configuringButton) ? 8 : 0;
                    int cur = getOverlayPrefs().getInt(key, def);
                    cycleActionConfig(key, cur);
                }
            });
        }
        if (btnConfigLong != null) {
            btnConfigLong.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (configuringButton == null) return;
                    String key = "btn_" + configuringButton + "_long_action";
                    int def = "mute".equals(configuringButton) ? 2 : ("youtube_190".equals(configuringButton) ? 3 : 4);
                    int cur = getOverlayPrefs().getInt(key, def);
                    cycleActionConfig(key, cur);
                }
            });
        }
        if (btnConfigDurationDec != null) {
            btnConfigDurationDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustDuration(-500); }
            });
        }
        if (btnConfigDurationInc != null) {
            btnConfigDurationInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustDuration(500); }
            });
        }

        // Clock Config Panel Listeners
        if (btnClockSizeDec != null) {
            btnClockSizeDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_size_sp", 16, -1, 8, 72); }
            });
        }
        if (btnClockSizeInc != null) {
            btnClockSizeInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_size_sp", 16, 1, 8, 72); }
            });
        }
        if (btnClockPadDec != null) {
            btnClockPadDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_padding_dp", 12, -1, 0, 100); }
            });
        }
        if (btnClockPadInc != null) {
            btnClockPadInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_padding_dp", 12, 1, 0, 100); }
            });
        }
        if (btnClockXDec != null) {
            btnClockXDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_pos_x_dp", 16, -2, 0, 500); }
            });
        }
        if (btnClockXInc != null) {
            btnClockXInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_pos_x_dp", 16, 2, 0, 500); }
            });
        }
        if (btnClockYDec != null) {
            btnClockYDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_pos_y_dp", 16, -2, 0, 500); }
            });
        }
        if (btnClockYInc != null) {
            btnClockYInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_pos_y_dp", 16, 2, 0, 500); }
            });
        }
        if (btnClockTextColor != null) {
            btnClockTextColor.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { cycleClockIntPref("clock_text_color_idx", CLOCK_COLOR_NAMES.length); }
            });
        }
        if (btnClockBgColor != null) {
            btnClockBgColor.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { cycleClockIntPref("clock_bg_color_idx", CLOCK_BG_NAMES.length); }
            });
        }
        if (btnClockAlphaDec != null) {
            btnClockAlphaDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_bg_alpha_pct", 35, -5, 0, 100); }
            });
        }
        if (btnClockAlphaInc != null) {
            btnClockAlphaInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_bg_alpha_pct", 35, 5, 0, 100); }
            });
        }
        if (btnClockTextAlphaDec != null) {
            btnClockTextAlphaDec.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_text_alpha_pct", 100, -5, 0, 100); }
            });
        }
        if (btnClockTextAlphaInc != null) {
            btnClockTextAlphaInc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { adjustClockIntPref("clock_text_alpha_pct", 100, 5, 0, 100); }
            });
        }
        if (btnClockPosition != null) {
            btnClockPosition.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { cycleClockIntPref("clock_position_idx", CLOCK_POSITION_NAMES.length); }
            });
        }
        if (btnApplyClock != null) {
            btnApplyClock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleOverlay(ButtonMappingService.KEY_CLOCK, "ACTION_TOGGLE_CLOCK");
                    updateClockConfigPanel();
                    buildMenu();
                }
            });
        }

        // Auto-pause listeners
        if (btnAutoPauseMode != null) {
            btnAutoPauseMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences op = getOverlayPrefs();
                    int cur = op.getInt("auto_pause_mode", 0);
                    int next = (cur + 1) % 4;
                    op.edit().putInt("auto_pause_mode", next).apply();
                    updateAutoPauseConfigPanel();
                    btnAutoPauseMode.requestFocus();
                    buildMenu();
                }
            });
        }
        if (btnAutoPauseCountDec != null) {
            btnAutoPauseCountDec.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences op = getOverlayPrefs();
                    int cur = op.getInt("auto_pause_custom_count", 1);
                    if (cur > 1) {
                        int next = cur - 1;
                        op.edit().putInt("auto_pause_custom_count", next).apply();
                        updateAutoPauseConfigPanel();
                        buildMenu();
                    }
                    btnAutoPauseCountDec.requestFocus();
                }
            });
        }
        if (btnAutoPauseCountInc != null) {
            btnAutoPauseCountInc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences op = getOverlayPrefs();
                    int cur = op.getInt("auto_pause_custom_count", 1);
                    int next = cur + 1;
                    op.edit().putInt("auto_pause_custom_count", next).apply();
                    updateAutoPauseConfigPanel();
                    buildMenu();
                    btnAutoPauseCountInc.requestFocus();
                }
            });
        }
        if (btnAutoPauseBlackScreen != null) {
            btnAutoPauseBlackScreen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences op = getOverlayPrefs();
                    boolean cur = op.getBoolean("auto_pause_black_screen", false);
                    op.edit().putBoolean("auto_pause_black_screen", !cur).apply();
                    updateAutoPauseConfigPanel();
                    btnAutoPauseBlackScreen.requestFocus();
                }
            });
        }

        // Still Watching listeners
        if (btnStillWatchingToggle != null) {
            btnStillWatchingToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleOverlay(ButtonMappingService.KEY_STILL_WATCHING, "ACTION_TOGGLE_STILL_WATCHING");
                    updateStillWatchingConfigPanel();
                    buildMenu();
                }
            });
        }
        setupAutoRepeatStepButton(btnStillWatchingIntervalDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_INTERVAL, 30, step, 1, 500); }
        });
        setupAutoRepeatStepButton(btnStillWatchingIntervalInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_INTERVAL, 30, step, 1, 500); }
        });
        setupAutoRepeatStepButton(btnStillWatchingTimeoutDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_TIMEOUT, 30, step, 1, 300); }
        });
        setupAutoRepeatStepButton(btnStillWatchingTimeoutInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_TIMEOUT, 30, step, 1, 300); }
        });
        if (btnStillWatchingActionType != null) {
            btnStillWatchingActionType.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { cycleStillWatchingAction(); }
            });
        }
        if (btnStillWatchingPosition != null) {
            btnStillWatchingPosition.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { cycleStillWatchingPosition(); }
            });
        }
        setupAutoRepeatStepButton(btnStillWatchingAlphaDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_ALPHA, 85, step, 1, 100); }
        });
        setupAutoRepeatStepButton(btnStillWatchingAlphaInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_ALPHA, 85, step, 1, 100); }
        });

        setupAutoRepeatStepButton(btnStillWatchingSizeDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_SIZE, 14, step, 8, 36); }
        });
        setupAutoRepeatStepButton(btnStillWatchingSizeInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_SIZE, 14, step, 8, 36); }
        });

        setupAutoRepeatStepButton(btnStillWatchingXDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_X, 16, step, 0, 500); }
        });
        setupAutoRepeatStepButton(btnStillWatchingXInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_X, 16, step, 0, 500); }
        });

        setupAutoRepeatStepButton(btnStillWatchingYDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_Y, 16, step, 0, 500); }
        });
        setupAutoRepeatStepButton(btnStillWatchingYInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_Y, 16, step, 0, 500); }
        });
        if (btnApplyStillWatching != null) {
            btnApplyStillWatching.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleOverlay(ButtonMappingService.KEY_STILL_WATCHING, "ACTION_TOGGLE_STILL_WATCHING");
                    updateStillWatchingConfigPanel();
                    buildMenu();
                }
            });
        }

        // Night Schedule Listeners
        if (btnNightScheduleToggle != null) {
            btnNightScheduleToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleOverlay(ButtonMappingService.KEY_NIGHT_SCHEDULE, "ACTION_TOGGLE_NIGHT_SCHEDULE");
                    updateNightScheduleConfigPanel();
                    buildMenu();
                }
            });
        }
        setupAutoRepeatStepButton(btnNightStartDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_NIGHT_START, 22, step, 0, 23, "ACTION_UPDATE_NIGHT_SCHEDULE"); updateNightScheduleConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnNightStartInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_NIGHT_START, 22, step, 0, 23, "ACTION_UPDATE_NIGHT_SCHEDULE"); updateNightScheduleConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnNightEndDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_NIGHT_END, 7, step, 0, 23, "ACTION_UPDATE_NIGHT_SCHEDULE"); updateNightScheduleConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnNightEndInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_NIGHT_END, 7, step, 0, 23, "ACTION_UPDATE_NIGHT_SCHEDULE"); updateNightScheduleConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnNightBlueLightDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_NIGHT_BLUE_LIGHT, 40, step, 0, 80, "ACTION_UPDATE_NIGHT_SCHEDULE"); updateNightScheduleConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnNightBlueLightInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_NIGHT_BLUE_LIGHT, 40, step, 0, 80, "ACTION_UPDATE_NIGHT_SCHEDULE"); updateNightScheduleConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnNightDimmerDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_NIGHT_DIMMER, 50, step, 0, 95, "ACTION_UPDATE_NIGHT_SCHEDULE"); updateNightScheduleConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnNightDimmerInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_NIGHT_DIMMER, 50, step, 0, 95, "ACTION_UPDATE_NIGHT_SCHEDULE"); updateNightScheduleConfigPanel(); }
        });
        if (btnApplyNightSchedule != null) {
            btnApplyNightSchedule.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleOverlay(ButtonMappingService.KEY_NIGHT_SCHEDULE, "ACTION_TOGGLE_NIGHT_SCHEDULE");
                    updateNightScheduleConfigPanel();
                    buildMenu();
                }
            });
        }

        // OLED Saver Listeners
        if (btnOledSaverToggle != null) {
            btnOledSaverToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleOverlay(ButtonMappingService.KEY_OLED_SAVER, "ACTION_TOGGLE_OLED_SAVER");
                    updateOledSaverConfigPanel();
                    buildMenu();
                }
            });
        }
        setupAutoRepeatStepButton(btnOledMinutesDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_OLED_MINUTES, 5, step, 1, 60, "ACTION_UPDATE_OLED_SAVER"); updateOledSaverConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnOledMinutesInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref(ButtonMappingService.KEY_OLED_MINUTES, 5, step, 1, 60, "ACTION_UPDATE_OLED_SAVER"); updateOledSaverConfigPanel(); }
        });
        if (btnOledMode != null) {
            btnOledMode.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int cur = getOverlayPrefs().getInt(ButtonMappingService.KEY_OLED_MODE, 0);
                    int next = (cur + 1) % 2;
                    getOverlayPrefs().edit().putInt(ButtonMappingService.KEY_OLED_MODE, next).apply();
                    updateOledSaverConfigPanel();
                    sendServiceAction("ACTION_UPDATE_OLED_SAVER");
                }
            });
        }
        if (btnApplyOledSaver != null) {
            btnApplyOledSaver.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleOverlay(ButtonMappingService.KEY_OLED_SAVER, "ACTION_TOGGLE_OLED_SAVER");
                    updateOledSaverConfigPanel();
                    buildMenu();
                }
            });
        }

        // Scheduled Sleep Listeners
        if (btnScheduledSleepToggle != null) {
            btnScheduledSleepToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean("scheduled_sleep_enabled", false);
                    getOverlayPrefs().edit().putBoolean("scheduled_sleep_enabled", !cur).apply();
                    sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
                    updateScheduledSleepConfigPanel();
                    buildMenu();
                }
            });
        }
        setupAutoRepeatStepButton(btnScheduledHourDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref("scheduled_sleep_hour", 23, step, 0, 23, "ACTION_UPDATE_SCHEDULED_SLEEP"); updateScheduledSleepConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnScheduledHourInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref("scheduled_sleep_hour", 23, step, 0, 23, "ACTION_UPDATE_SCHEDULED_SLEEP"); updateScheduledSleepConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnScheduledMinDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref("scheduled_sleep_minute", 30, step, 0, 59, "ACTION_UPDATE_SCHEDULED_SLEEP"); updateScheduledSleepConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnScheduledMinInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref("scheduled_sleep_minute", 30, step, 0, 59, "ACTION_UPDATE_SCHEDULED_SLEEP"); updateScheduledSleepConfigPanel(); }
        });

        View.OnFocusChangeListener dayFocusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                updateScheduledSleepConfigPanel();
            }
        };
        View.OnClickListener dayListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int day = 1;
                if (v == btnDay1) day = 1;
                else if (v == btnDay2) day = 2;
                else if (v == btnDay3) day = 3;
                else if (v == btnDay4) day = 4;
                else if (v == btnDay5) day = 5;
                else if (v == btnDay6) day = 6;
                else if (v == btnDay7) day = 7;
                toggleScheduledSleepDay(day);
            }
        };
        TextView[] dayArr = {btnDay1, btnDay2, btnDay3, btnDay4, btnDay5, btnDay6, btnDay7};
        for (TextView dBtn : dayArr) {
            if (dBtn != null) {
                dBtn.setOnClickListener(dayListener);
                dBtn.setOnFocusChangeListener(dayFocusListener);
            }
        }

        if (btnScheduledSkipNext != null) {
            btnScheduledSkipNext.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    SharedPreferences op = getOverlayPrefs();
                    String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
                    String skipStr = op.getString("scheduled_sleep_skip_date", "");
                    if (todayStr.equals(skipStr)) {
                        op.edit().remove("scheduled_sleep_skip_date").apply();
                    } else {
                        op.edit().putString("scheduled_sleep_skip_date", todayStr).apply();
                    }
                    sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
                    updateScheduledSleepConfigPanel();
                    buildMenu();
                }
            });
        }
        if (btnApplyScheduledSleep != null) {
            btnApplyScheduledSleep.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean("scheduled_sleep_enabled", false);
                    getOverlayPrefs().edit().putBoolean("scheduled_sleep_enabled", !cur).apply();
                    sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
                    updateScheduledSleepConfigPanel();
                    buildMenu();
                }
            });
        }
    }

    private void updateCustomTimerUI() {
        if (txtCustomHours != null) txtCustomHours.setText(customHours + "h");
        if (txtCustomMins != null) txtCustomMins.setText(String.format(java.util.Locale.US, "%02dm", customMins));
    }

    private void buildMenu() {
        if (menuContainer == null) return;
        menuContainer.removeAllViews();
        float d = context.getResources().getDisplayMetrics().density;
        String[] order = getMenuOrder();
        SharedPreferences op = getOverlayPrefs();
        SharedPreferences tp = context.getSharedPreferences(SleepTimerService.PREFS_NAME, Context.MODE_PRIVATE);

        int prevGroup = -1;
        for (int i = 0; i < order.length; i++) {
            String id = order[i];
            int group = getGroupForId(id);
            if (prevGroup != -1 && prevGroup != group) addDivider(d);
            prevGroup = group;

            String label = getLabelForId(id, op, tp);
            int color    = getColorForId(id);
            TextView btn = createBtn(label, color, d);

            if (isReorderMode && reorderSelectedIndex == i) {
                btn.setBackgroundColor(Color.argb(210, 180, 100, 0));
            }
            final int idx = i; final String iid = id;
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (isReorderMode) handleReorderClick(idx);
                    else {
                        lastFocusedId = iid;
                        handleItemClick(iid);
                    }
                }
            });
            btn.setTag(id);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) btn.getLayoutParams();
            lp.bottomMargin = Math.round(8 * d);
            btn.setLayoutParams(lp);
            menuContainer.addView(btn);
        }

        addDivider(d);
        String reorderLabel = isReorderMode ? "OK  Terminar Reordenamiento" : "=  Reordenar Menu";
        int reorderColor    = isReorderMode ? 0xFF4CAF50 : 0xFFAAAAAA;
        TextView reorderBtn = createBtn(reorderLabel, reorderColor, d);
        reorderBtn.setGravity(Gravity.CENTER);
        reorderBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isReorderMode = !isReorderMode;
                if (!isReorderMode) reorderSelectedIndex = -1;
                if (isReorderMode) closeSubPanels();
                buildMenu();
            }
        });
        LinearLayout.LayoutParams rlp = (LinearLayout.LayoutParams) reorderBtn.getLayoutParams();
        rlp.bottomMargin = 0;
        reorderBtn.setLayoutParams(rlp);
        menuContainer.addView(reorderBtn);

        if (isReorderMode) {
            TextView hint = new TextView(context);
            hint.setText(reorderSelectedIndex == -1 ? "Selecciona un item para moverlo" : "Ahora selecciona donde colocarlo");
            hint.setTextColor(Color.argb(200, 255, 200, 80));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, Math.round(6 * d), 0, 0);
            menuContainer.addView(hint);
        }

        if (openSubPanel == null && menuContainer.getChildCount() > 0) {
            if (lastFocusedId != null) {
                View target = menuContainer.findViewWithTag(lastFocusedId);
                if (target != null) {
                    target.requestFocus();
                } else {
                    menuContainer.getChildAt(0).requestFocus();
                }
            } else {
                menuContainer.getChildAt(0).requestFocus();
            }
        }
    }

    private void closeSubPanels() {
        stopHoldRepeat();
        if (panelTimer != null) panelTimer.setVisibility(View.GONE);
        if (panelCine != null) panelCine.setVisibility(View.GONE);
        if (panelBlueLight != null) panelBlueLight.setVisibility(View.GONE);
        if (panelBrightness != null) panelBrightness.setVisibility(View.GONE);
        if (panelButtonConfig != null) panelButtonConfig.setVisibility(View.GONE);
        if (panelClockConfig != null) panelClockConfig.setVisibility(View.GONE);
        if (panelAutoPause != null) panelAutoPause.setVisibility(View.GONE);
        if (panelStillWatching != null) panelStillWatching.setVisibility(View.GONE);
        if (panelNightSchedule != null) panelNightSchedule.setVisibility(View.GONE);
        if (panelOledSaver != null) panelOledSaver.setVisibility(View.GONE);
        if (panelScheduledSleep != null) panelScheduledSleep.setVisibility(View.GONE);
        openSubPanel = null;
        configuringButton = null;
        if (menuContainer != null) {
            menuContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
    }

    private void handleReorderClick(int index) {
        String[] order = getMenuOrder();
        if (reorderSelectedIndex == -1) {
            reorderSelectedIndex = index;
        } else if (reorderSelectedIndex == index) {
            reorderSelectedIndex = -1;
        } else {
            String temp = order[reorderSelectedIndex];
            order[reorderSelectedIndex] = order[index];
            order[index] = temp;
            saveMenuOrder(order);
            reorderSelectedIndex = -1;
        }
        buildMenu();
    }

    private void handleItemClick(String id) {
        switch (id) {
            case "manage_apps":
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) { Log.e(TAG, "manage_apps", e); }
                dismiss();
                break;
            case "timer":
                if ("timer".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelTimer != null) panelTimer.setVisibility(View.VISIBLE);
                    openSubPanel = "timer";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateTimerCancelButton();
                    View f = panelTimer != null ? panelTimer.findViewById(R.id.btn_5m) : null;
                    if (f != null) f.requestFocus();
                }
                break;
            case "blue_light":
                if ("blue_light".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelBlueLight != null) panelBlueLight.setVisibility(View.VISIBLE);
                    openSubPanel = "blue_light";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateBlueLightConfigPanel();
                    if (sliderBlueLight != null) sliderBlueLight.requestFocus();
                }
                break;
            case "clock":
                if ("clock_config".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelClockConfig != null) panelClockConfig.setVisibility(View.VISIBLE);
                    openSubPanel = "clock_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateClockConfigPanel();
                    if (btnClockTextColor != null) btnClockTextColor.requestFocus();
                }
                break;
            case "dimmer":
                SharedPreferences dPrefs = getOverlayPrefs();
                boolean isDimmerOn = dPrefs.getBoolean(ButtonMappingService.KEY_DIMMER, false);
                if (!isDimmerOn) {
                    sendServiceAction("ACTION_TOGGLE_DIMMER");
                }
                if ("brightness_config".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelBrightness != null) panelBrightness.setVisibility(View.VISIBLE);
                    openSubPanel = "brightness_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateBrightnessConfigPanel();
                    if (sliderBrightness != null) sliderBrightness.requestFocus();
                }
                break;
            case "grayscale":
                sendServiceAction("ACTION_TOGGLE_GRAYSCALE");
                dismiss();
                break;
            case "cine_mode":
                if ("cine".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelCine != null) panelCine.setVisibility(View.VISIBLE);
                    openSubPanel = "cine";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateCineConfigPanel();
                    if (btnCineBlueLightConfig != null) btnCineBlueLightConfig.requestFocus();
                }
                break;
            case "auto_pause":
                if ("auto_pause".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelAutoPause != null) panelAutoPause.setVisibility(View.VISIBLE);
                    openSubPanel = "auto_pause";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateAutoPauseConfigPanel();
                    if (btnAutoPauseMode != null) btnAutoPauseMode.requestFocus();
                }
                break;
            case "screen_off":
                sendServiceAction("ACTION_SHOW_BLACK_SCREEN");
                dismiss();
                break;
            case "system_menu":
                try {
                    Intent intent = new Intent();
                    intent.setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception ignored) {}
                }
                dismiss();
                break;
            case "google_home":
                try {
                    Intent intent = new Intent("com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) { Log.e(TAG, "google_home", e); }
                dismiss();
                break;
            case "bluetooth":
                launchBluetoothSettings();
                dismiss();
                break;
            case "developer_options":
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent();
                        intent.setClassName("com.android.tv.settings", "com.android.tv.settings.system.development.DevelopmentActivity");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e2) {}
                }
                dismiss();
                break;
            case "pause_screen_off":
                sendServiceAction("ACTION_PAUSE_AND_SCREEN_OFF");
                dismiss();
                break;
            case "scheduled_sleep":
                if ("scheduled_sleep".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelScheduledSleep != null) panelScheduledSleep.setVisibility(View.VISIBLE);
                    openSubPanel = "scheduled_sleep";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateScheduledSleepConfigPanel();
                    if (btnScheduledSleepToggle != null) btnScheduledSleepToggle.requestFocus();
                }
                break;
            case "cycle_brightness": {
                SharedPreferences prefs = getOverlayPrefs();
                int cur = prefs.getInt("dimmer_brightness_pct", 50);
                String levelsStr = prefs.getString("brightness_levels_list", "80,50,20");
                String[] parts = levelsStr.split(",");
                if (parts.length > 0) {
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
                    
                    Bundle b = new Bundle();
                    b.putInt("pct", next);
                    sendServiceAction("ACTION_SET_DIMMER_BRIGHTNESS", b);
                }
                buildMenu();
                break;
            }
            case "system_info":
                sendServiceAction("ACTION_SHOW_SYSTEM_INFO");
                dismiss();
                break;
            case "reboot":
                sendServiceAction("ACTION_REBOOT");
                dismiss();
                break;
            case "config_mute":
            case "config_youtube_190":
            case "config_youtube_189":
                String btnKey = id.substring("config_".length());
                if (id.equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    configuringButton = btnKey;
                    if (panelButtonConfig != null) panelButtonConfig.setVisibility(View.VISIBLE);
                    openSubPanel = id;
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateButtonConfigPanel();
                    if (btnConfigClick1 != null) btnConfigClick1.requestFocus();
                }
                break;
            case "still_watching":
                if ("still_watching".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelStillWatching != null) panelStillWatching.setVisibility(View.VISIBLE);
                    openSubPanel = "still_watching";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateStillWatchingConfigPanel();
                    if (btnStillWatchingToggle != null) btnStillWatchingToggle.requestFocus();
                }
                break;
            case "night_schedule":
                if ("night_schedule".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelNightSchedule != null) panelNightSchedule.setVisibility(View.VISIBLE);
                    openSubPanel = "night_schedule";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateNightScheduleConfigPanel();
                    if (btnNightScheduleToggle != null) btnNightScheduleToggle.requestFocus();
                }
                break;
            case "oled_saver":
                if ("oled_saver".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelOledSaver != null) panelOledSaver.setVisibility(View.VISIBLE);
                    openSubPanel = "oled_saver";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateOledSaverConfigPanel();
                    if (btnOledSaverToggle != null) btnOledSaverToggle.requestFocus();
                }
                break;
            default:
                Log.w(TAG, "Unknown item: " + id);
        }
    }

    private String getLabelForId(String id, SharedPreferences op, SharedPreferences tp) {
        switch (id) {
            case "manage_apps":  return "Apps  Administrar Apps";
            case "timer":        return getTimerLabel(tp);
            case "blue_light": {
                int pct = op.getInt("blue_light_pct", 50);
                if (pct == 0) pct = 50;
                boolean active = op.getBoolean(ButtonMappingService.KEY_BLUE_LIGHT, false);
                double displayPct = pct / 10.0;
                return "Luz Azul  Filtro Luz Azul  [" + (active ? String.format(java.util.Locale.US, "%.1f%%", displayPct) : "OFF") + "]";
            }
            case "clock":        return fmtToggle("Reloj  Reloj en Pantalla  (tap=configurar)", op.getBoolean(ButtonMappingService.KEY_CLOCK,  false));
            case "dimmer": {
                int pct = op.getInt("dimmer_brightness_pct", 50);
                return fmtToggle("Noche  Dimmer de Pantalla (" + pct + "%)", op.getBoolean(ButtonMappingService.KEY_DIMMER, false));
            }
            case "grayscale":    return fmtToggle("B/N  Escala de Grises",      isGrayscaleOn());
            case "cine_mode":    return fmtToggle("Cine  Modo Cine",            op.getBoolean(ButtonMappingService.KEY_CINE_MODE, false));
            case "auto_pause": {
                int mode = op.getInt("auto_pause_mode", 0);
                String modeText;
                if (mode == 0) modeText = "OFF";
                else if (mode == 1) modeText = "Una vez";
                else if (mode == 2) modeText = "Permanente";
                else {
                    int count = op.getInt("auto_pause_custom_count", 1);
                    modeText = count + " veces";
                }
                return "Pausa  Auto Pausa de Video  [" + modeText + "]";
            }
            case "screen_off":   return "Sleep  Apagar Pantalla";
            case "system_menu":  return "System  Ajustes del Sistema";
            case "google_home":  return "Home  Google Home Panel";
            case "bluetooth":    return "BT  Auriculares Bluetooth";
            case "system_info":  return "Info  Info del Sistema";
            case "reboot":       return "Reiniciar Chromecast";
            case "developer_options": return "Dev  Opciones de Desarrollo";
            case "pause_screen_off": return "Sleep  Pausar y Apagar Pantalla";
            case "scheduled_sleep": return fmtScheduledSleep(op);
            case "cycle_brightness": {
                int pct = op.getInt("dimmer_brightness_pct", 50);
                return "Brillo  Ciclar Brillo  [" + pct + "%]";
            }
            case "still_watching": return fmtToggle("📺  ¿Sigues viendo?", op.getBoolean(ButtonMappingService.KEY_STILL_WATCHING, false));
            case "night_schedule": return fmtToggle("🌙  Horario Nocturno", op.getBoolean(ButtonMappingService.KEY_NIGHT_SCHEDULE, false));
            case "oled_saver": return fmtToggle("🛡️  Protector OLED (Burn-In)", op.getBoolean(ButtonMappingService.KEY_OLED_SAVER, false));
            case "config_mute":        return "Config  Configurar Botón Mute";
            case "config_youtube_190": return "Config  Configurar YouTube (190)";
            case "config_youtube_189": return "Config  Configurar YouTube (189)";
            default:             return id;
        }
    }

    private String fmtScheduledSleep(SharedPreferences op) {
        boolean enabled = op.getBoolean("scheduled_sleep_enabled", false);
        if (!enabled) {
            return "⏰  Apagado Programado   [OFF]";
        }
        String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        String skipStr = op.getString("scheduled_sleep_skip_date", "");
        if (todayStr.equals(skipStr)) {
            return "⏰  Apagado Programado   [Salteado hoy]";
        }
        int h = op.getInt("scheduled_sleep_hour", 23);
        int m = op.getInt("scheduled_sleep_minute", 30);
        String timeStr = String.format(java.util.Locale.US, "%02d:%02d", h, m);
        return "⏰  Apagado Programado   [" + timeStr + "]";
    }

    private int getColorForId(String id) { return "reboot".equals(id) ? 0xFFFF6B6B : Color.WHITE; }

    private int getGroupForId(String id) {
        switch (id) {
            case "manage_apps": case "timer": case "blue_light":
            case "clock": case "dimmer": case "grayscale": case "cine_mode":
            case "cycle_brightness": case "auto_pause":
                return 1;
            case "screen_off": case "pause_screen_off": case "scheduled_sleep":
                return 2;
            case "google_home": case "bluetooth": case "system_info": case "reboot":
            case "system_menu": case "developer_options":
                return 3;
            default:
                return 4;
        }
    }

    private String getTimerLabel(SharedPreferences tp) {
        long endTime   = tp.getLong(SleepTimerService.KEY_END_TIME, 0);
        long remaining = endTime - System.currentTimeMillis();
        if (endTime > 0 && remaining > 0) {
            int mins = (int)(remaining / 60000);
            return "Timer  Sleep Timer - " + mins + " min restantes";
        }
        return "Timer  Sleep Timer  (tap para abrir)";
    }

    private boolean isGrayscaleOn() {
        try { return Settings.Secure.getInt(context.getContentResolver(), "accessibility_display_daltonizer_enabled", 0) != 0; }
        catch (Exception e) { return false; }
    }

    private String fmtToggle(String label, boolean on) { return label + "   " + (on ? "[ON]" : "[OFF]"); }

    private TextView createBtn(String label, int color, float d) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        int h = Math.round(52 * d), pad = Math.round(16 * d);
        tv.setPadding(pad, 0, pad, 0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setBackgroundResource(R.drawable.card_background);
        tv.setFocusable(true); tv.setClickable(true);
        tv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        return tv;
    }

    private void addDivider(float d) {
        View divider = new View(context);
        divider.setBackgroundColor(Color.argb(60, 255, 255, 255));
        int m = Math.round(6 * d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(d)));
        lp.topMargin = m; lp.bottomMargin = m;
        divider.setLayoutParams(lp);
        menuContainer.addView(divider);
    }

    private void updateTimerCancelButton() {
        SharedPreferences tp = context.getSharedPreferences(SleepTimerService.PREFS_NAME, Context.MODE_PRIVATE);
        long endTime = tp.getLong(SleepTimerService.KEY_END_TIME, 0);
        boolean active = endTime > 0 && (endTime - System.currentTimeMillis()) > 0;
        if (btnCancelTimer != null) btnCancelTimer.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void updateCineConfigPanel() {
        SharedPreferences cp = context.getSharedPreferences("cine_prefs", Context.MODE_PRIVATE);
        if (btnCineBlueLightConfig != null)
            btnCineBlueLightConfig.setText("   Filtro Azul:  " + (cp.getBoolean("cine_blue_light", true) ? "ON" : "OFF"));
        if (btnCineDimmerConfig != null)
            btnCineDimmerConfig.setText("   Dimmer:  " + (cp.getBoolean("cine_dimmer", false) ? "ON" : "OFF"));
        if (btnCineTimerConfig != null) {
            int t = cp.getInt("cine_timer", 0);
            String tLabel = t == 0 ? "Desactivado" : (t < 60 ? t + " min" : (t / 60) + "h" + (t % 60 > 0 ? " " + (t % 60) + "m" : ""));
            btnCineTimerConfig.setText("   Timer:  " + tLabel + "  (tap=cambiar)");
        }
        if (btnApplyCine != null) {
            boolean active = getOverlayPrefs().getBoolean(ButtonMappingService.KEY_CINE_MODE, false);
            btnApplyCine.setText(active ? "[ Desactivar Modo Cine ]" : "[ Activar Modo Cine ]");
        }
    }

    private void updateButtonConfigPanel() {
        if (configuringButton == null) return;

        SharedPreferences prefs = getOverlayPrefs();
        String btnName = configuringButton;
        
        int defClick1 = 0, defClick2 = 0, defClick3 = 0, defLong = 0, defDur = 1000;
        String title = "Configurar Botón: ";
        if ("mute".equals(btnName)) {
            title += "Mute";
            defClick1 = 1; defClick2 = 7; defClick3 = 8; defLong = 2; defDur = 1000;
        } else if ("youtube_190".equals(btnName)) {
            title += "YouTube (190)";
            defClick1 = 5; defClick2 = 4; defClick3 = 0; defLong = 3; defDur = 2000;
        } else if ("youtube_189".equals(btnName)) {
            title += "YouTube (189)";
            defClick1 = 5; defClick2 = 0; defClick3 = 0; defLong = 4; defDur = 2000;
        }

        if (txtConfigTitle != null) txtConfigTitle.setText(title);

        int actClick1 = prefs.getInt("btn_" + btnName + "_click_1_action", defClick1);
        int actClick2 = prefs.getInt("btn_" + btnName + "_click_2_action", defClick2);
        int actClick3 = prefs.getInt("btn_" + btnName + "_click_3_action", defClick3);
        int actLong   = prefs.getInt("btn_" + btnName + "_long_action", defLong);
        int durationMs = prefs.getInt("btn_" + btnName + "_long_duration_ms", defDur);

        if (btnConfigClick1 != null) btnConfigClick1.setText("   Click Simple:  " + getActionName(actClick1));
        if (btnConfigClick2 != null) btnConfigClick2.setText("   Doble Click:  " + getActionName(actClick2));
        if (btnConfigClick3 != null) btnConfigClick3.setText("   Triple Click:  " + getActionName(actClick3));
        if (btnConfigLong != null)   btnConfigLong.setText("   Sostenido:  " + getActionName(actLong));
        
        if (txtConfigDuration != null) {
            double secs = durationMs / 1000.0;
            txtConfigDuration.setText(String.format(java.util.Locale.US, "%.1fs", secs));
        }
    }

    private String getActionName(int actionId) {
        if (actionId >= 0 && actionId < ACTION_NAMES.length) {
            return ACTION_NAMES[actionId];
        }
        return "Desconocido";
    }

    private void cycleActionConfig(String configKey, int currentAction) {
        int nextAction = (currentAction + 1) % ACTION_NAMES.length;
        getOverlayPrefs().edit().putInt(configKey, nextAction).apply();
        updateButtonConfigPanel();
    }

    private void adjustDuration(int delta) {
        if (configuringButton == null) return;
        String key = "btn_" + configuringButton + "_long_duration_ms";
        int def = "mute".equals(configuringButton) ? 1000 : 2000;
        int cur = getOverlayPrefs().getInt(key, def);
        int next = cur + delta;
        if (next < 500) next = 500;
        if (next > 5000) next = 5000;
        getOverlayPrefs().edit().putInt(key, next).apply();
        updateButtonConfigPanel();
    }

    private void updateClockConfigPanel() {
        SharedPreferences prefs = getOverlayPrefs();
        int colorIdx = prefs.getInt("clock_text_color_idx", 0);
        int bgIdx = prefs.getInt("clock_bg_color_idx", 0);
        int alphaPct = prefs.getInt("clock_bg_alpha_pct", 35);
        int textAlphaPct = prefs.getInt("clock_text_alpha_pct", 100);
        int posIdx = prefs.getInt("clock_position_idx", 0);
        
        int sizeSp = prefs.getInt("clock_size_sp", 16);
        int paddingDp = prefs.getInt("clock_padding_dp", 12);
        int posX = prefs.getInt("clock_pos_x_dp", 16);
        int posY = prefs.getInt("clock_pos_y_dp", 16);
        
        boolean active = prefs.getBoolean(ButtonMappingService.KEY_CLOCK, false);

        if (btnClockTextColor != null) btnClockTextColor.setText("   Color Letra:  " + CLOCK_COLOR_NAMES[colorIdx % CLOCK_COLOR_NAMES.length]);
        if (btnClockBgColor != null) btnClockBgColor.setText("   Color Fondo:  " + CLOCK_BG_NAMES[bgIdx % CLOCK_BG_NAMES.length]);
        if (btnClockPosition != null) btnClockPosition.setText("   Posición Base:  " + CLOCK_POSITION_NAMES[posIdx % CLOCK_POSITION_NAMES.length]);
        
        if (txtClockAlpha != null) txtClockAlpha.setText(alphaPct + "%");
        if (txtClockTextAlpha != null) txtClockTextAlpha.setText(textAlphaPct + "%");
        if (txtClockSize != null) txtClockSize.setText(sizeSp + "sp");
        if (txtClockPad != null) txtClockPad.setText(paddingDp + "dp");
        if (txtClockX != null) txtClockX.setText(posX + "dp");
        if (txtClockY != null) txtClockY.setText(posY + "dp");
        
        if (btnApplyClock != null) btnApplyClock.setText(active ? "[ Desactivar Reloj ]" : "[ Activar Reloj ]");
    }

    private void adjustClockIntPref(String key, int def, int delta, int min, int max) {
        int cur = getOverlayPrefs().getInt(key, def);
        int next = cur + delta;
        if (next < min) next = min;
        if (next > max) next = max;
        getOverlayPrefs().edit().putInt(key, next).apply();
        updateClockConfigPanel();
        sendServiceAction("ACTION_UPDATE_CLOCK");
    }

    private void cycleClockIntPref(String key, int totalOptions) {
        int cur = getOverlayPrefs().getInt(key, 0);
        int next = (cur + 1) % totalOptions;
        getOverlayPrefs().edit().putInt(key, next).apply();
        updateClockConfigPanel();
        sendServiceAction("ACTION_UPDATE_CLOCK");
    }

    private void updateBrightnessConfigPanel() {
        updateBrightnessConfigPanel(-1);
    }

    private void updateBrightnessConfigPanel(final int targetFocusRowIndex) {
        SharedPreferences prefs = getOverlayPrefs();
        int brightnessPct = prefs.getInt("dimmer_brightness_pct", 50);
        boolean active = prefs.getBoolean(ButtonMappingService.KEY_DIMMER, false);

        if (sliderBrightness != null) {
            sliderBrightness.setProgress(brightnessPct);
        }
        if (txtBrightnessPct != null) {
            txtBrightnessPct.setText("Nivel: " + brightnessPct + "%");
        }
        if (btnApplyBrightness != null) {
            btnApplyBrightness.setText(active ? "[ Desactivar Dimmer ]" : "[ Activar Dimmer ]");
        }

        View viewToFocus = null;

        if (containerBrightnessLevels != null) {
            containerBrightnessLevels.removeAllViews();

            String listStr = prefs.getString("brightness_levels_list", "80,50,20");
            final String[] parts = listStr.split(",");
            float d = context.getResources().getDisplayMetrics().density;

            for (int i = 0; i < parts.length; i++) {
                final int index = i;
                int val = 50;
                try { val = Integer.parseInt(parts[i].trim()); } catch (Exception ignored) {}
                final int levelVal = val;

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = Math.round(4 * d);
                row.setLayoutParams(rowLp);

                final TextView tvVal = new TextView(context);

                TextView tvLabel = new TextView(context);
                tvLabel.setText("   Nivel " + (i + 1) + ":");
                tvLabel.setTextColor(0xFFCCCCCC);
                tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                tvLabel.setPadding(Math.round(4 * d), 0, 0, 0);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                tvLabel.setLayoutParams(labelLp);
                row.addView(tvLabel);

                TextView btnApply = new TextView(context);
                btnApply.setText("✓");
                btnApply.setTextColor(0xFF4CAF50);
                btnApply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                btnApply.setGravity(Gravity.CENTER);
                btnApply.setBackgroundResource(R.drawable.card_background);
                btnApply.setFocusable(true);
                btnApply.setClickable(true);
                LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(Math.round(36 * d), Math.round(36 * d));
                applyLp.rightMargin = Math.round(4 * d);
                btnApply.setLayoutParams(applyLp);
                btnApply.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int currentVal = 50;
                        try { currentVal = Integer.parseInt(parts[index].trim()); } catch (Exception ignored) {}
                        Bundle b = new Bundle();
                        b.putInt("pct", currentVal);
                        sendServiceAction("ACTION_SET_DIMMER_BRIGHTNESS", b);
                        getOverlayPrefs().edit().putInt("dimmer_brightness_pct", currentVal).apply();
                        if (sliderBrightness != null) {
                            sliderBrightness.setProgress(currentVal);
                        }
                        if (txtBrightnessPct != null) {
                            txtBrightnessPct.setText("Nivel: " + currentVal + "%");
                        }
                        btnApply.requestFocus();
                    }
                });
                row.addView(btnApply);

                final TextView btnDec = new TextView(context);
                btnDec.setText("-");
                btnDec.setTextColor(0xFFFFFFFF);
                btnDec.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                btnDec.setGravity(Gravity.CENTER);
                btnDec.setBackgroundResource(R.drawable.card_background);
                btnDec.setFocusable(true);
                btnDec.setClickable(true);
                LinearLayout.LayoutParams decLp = new LinearLayout.LayoutParams(Math.round(36 * d), Math.round(36 * d));
                btnDec.setLayoutParams(decLp);
                btnDec.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int currentVal = 50;
                        try { currentVal = Integer.parseInt(parts[index].trim()); } catch (Exception ignored) {}
                        int newVal = Math.max(0, currentVal - 1);
                        parts[index] = String.valueOf(newVal);
                        saveBrightnessLevels(parts);
                        tvVal.setText(newVal + "%");
                        btnDec.requestFocus();
                    }
                });
                row.addView(btnDec);

                tvVal.setText(levelVal + "%");
                tvVal.setTextColor(0xFFFFFFFF);
                tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                tvVal.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(Math.round(60 * d), LinearLayout.LayoutParams.WRAP_CONTENT);
                tvVal.setLayoutParams(valLp);
                row.addView(tvVal);

                final TextView btnInc = new TextView(context);
                btnInc.setText("+");
                btnInc.setTextColor(0xFFFFFFFF);
                btnInc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                btnInc.setGravity(Gravity.CENTER);
                btnInc.setBackgroundResource(R.drawable.card_background);
                btnInc.setFocusable(true);
                btnInc.setClickable(true);
                LinearLayout.LayoutParams incLp = new LinearLayout.LayoutParams(Math.round(36 * d), Math.round(36 * d));
                incLp.rightMargin = Math.round(4 * d);
                btnInc.setLayoutParams(incLp);
                btnInc.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int currentVal = 50;
                        try { currentVal = Integer.parseInt(parts[index].trim()); } catch (Exception ignored) {}
                        int newVal = Math.min(100, currentVal + 1);
                        parts[index] = String.valueOf(newVal);
                        saveBrightnessLevels(parts);
                        tvVal.setText(newVal + "%");
                        btnInc.requestFocus();
                    }
                });
                row.addView(btnInc);

                TextView btnDel = new TextView(context);
                btnDel.setText("✕");
                btnDel.setTextColor(0xFFFF5252);
                btnDel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                btnDel.setGravity(Gravity.CENTER);
                btnDel.setBackgroundResource(R.drawable.card_background);
                btnDel.setFocusable(true);
                btnDel.setClickable(true);
                LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(Math.round(36 * d), Math.round(36 * d));
                btnDel.setLayoutParams(delLp);
                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharedPreferences p = getOverlayPrefs();
                        String lStr = p.getString("brightness_levels_list", "80,50,20");
                        String[] currParts = lStr.split(",");
                        if (currParts.length > 2) {
                            StringBuilder sb = new StringBuilder();
                            boolean first = true;
                            for (int k = 0; k < currParts.length; k++) {
                                if (k == index) continue;
                                if (!first) sb.append(",");
                                sb.append(currParts[k].trim());
                                first = false;
                            }
                            p.edit().putString("brightness_levels_list", sb.toString()).apply();
                            int nextTargetIdx = Math.min(index, currParts.length - 2);
                            updateBrightnessConfigPanel(nextTargetIdx);
                        } else {
                            android.widget.Toast.makeText(context, "Mínimo 2 niveles de brillo requeridos", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                row.addView(btnDel);

                if (i == targetFocusRowIndex) {
                    viewToFocus = btnDel;
                }

                containerBrightnessLevels.addView(row);
            }

            if (viewToFocus != null) {
                final View fv = viewToFocus;
                fv.post(new Runnable() {
                    @Override
                    public void run() { fv.requestFocus(); }
                });
            } else if (targetFocusRowIndex >= 0 && btnAddBrightnessLevel != null) {
                btnAddBrightnessLevel.requestFocus();
            }
        }
    }

    private void saveBrightnessLevels(String[] levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(levels[i]);
        }
        getOverlayPrefs().edit().putString("brightness_levels_list", sb.toString()).apply();
    }

    private String[] getMenuOrder() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ORDER, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_ORDER, null);
        if (saved == null || saved.isEmpty()) return ALL_ITEM_IDS.clone();
        String[] loaded = saved.split(",");
        if (loaded.length == ALL_ITEM_IDS.length) return loaded;
        return ALL_ITEM_IDS.clone();
    }

    private void saveMenuOrder(String[] order) {
        context.getSharedPreferences(PREFS_ORDER, Context.MODE_PRIVATE).edit().putString(KEY_ORDER, TextUtils.join(",", order)).apply();
    }

    private void startTimer(int minutes) {
        Intent intent = new Intent(context, SleepTimerService.class);
        intent.setAction("ACTION_START_TIMER");
        intent.putExtra("minutes", minutes);
        context.startService(intent);
        dismiss();
    }

    private void toggleOverlay(String prefKey, String action) {
        boolean current = getOverlayPrefs().getBoolean(prefKey, false);
        sendServiceAction(action);
        getOverlayPrefs().edit().putBoolean(prefKey, !current).apply();
    }

    private void launchBluetoothSettings() {
        try {
            Intent intent = new Intent("android.settings.SLICE_SETTINGS");
            intent.putExtra("slice_uri", "content://com.google.android.tv.btservices.settings.sliceprovider/general");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception eSlice) {
            try {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e0) {
                try {
                    Intent intent = new Intent();
                    intent.setClassName("com.android.tv.settings", "com.android.tv.settings.accessories.AccessoriesActivity");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e1) {
                    try {
                        Intent intent = new Intent();
                        intent.setClassName("com.android.tv.settings", "com.android.tv.settings.accessories.AccessoryActivity");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e2) {
                        try {
                            Intent intent = new Intent();
                            intent.setClassName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        } catch (Exception e3) {
                            try {
                                Intent intent = new Intent();
                                intent.setClassName("com.android.tv.settings", "com.android.tv.settings.connectivity.setup.ConnectBluetoothActivity");
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                            } catch (Exception e4) {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateBlueLightConfigPanel() {
        SharedPreferences prefs = getOverlayPrefs();
        int pct = prefs.getInt("blue_light_pct", 50);
        if (pct == 0) pct = 50;
        boolean active = prefs.getBoolean(ButtonMappingService.KEY_BLUE_LIGHT, false);

        if (sliderBlueLight != null) {
            sliderBlueLight.setProgress(pct);
        }
        if (txtBlueLightPct != null) {
            double displayPct = pct / 10.0;
            txtBlueLightPct.setText("Nivel: " + (active ? String.format(java.util.Locale.US, "%.1f%%", displayPct) : "Desactivado"));
        }
        if (btnApplyBlueLight != null) {
            btnApplyBlueLight.setText(active ? "[ Desactivar Filtro ]" : "[ Activar Filtro ]");
        }
    }

    private void updateAutoPauseConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        int mode = op.getInt("auto_pause_mode", 0);
        int count = op.getInt("auto_pause_custom_count", 1);
        boolean blackScreen = op.getBoolean("auto_pause_black_screen", false);

        if (btnAutoPauseMode != null) {
            String modeStr;
            switch (mode) {
                case 0: modeStr = "Desactivado"; break;
                case 1: modeStr = "Solo una vez"; break;
                case 2: modeStr = "Activado permanente"; break;
                case 3: modeStr = "Personalizado"; break;
                default: modeStr = "Desactivado"; break;
            }
            btnAutoPauseMode.setText("   Modo de Auto Pausa:  " + modeStr);
        }

        if (layoutAutoPauseCustom != null) {
            layoutAutoPauseCustom.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);
        }

        if (txtAutoPauseCount != null) {
            txtAutoPauseCount.setText(String.valueOf(count));
        }

        if (btnAutoPauseBlackScreen != null) {
            btnAutoPauseBlackScreen.setText("   Apagar Pantalla al Pausar:  " + (blackScreen ? "ON" : "OFF"));
        }
    }

    private void updateStillWatchingConfigPanel() {
        SharedPreferences prefs = getOverlayPrefs();
        boolean active = prefs.getBoolean(ButtonMappingService.KEY_STILL_WATCHING, false);
        int interval = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_INTERVAL, 30);
        int timeout = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_TIMEOUT, 30);
        int actionIdx = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_ACTION, 0);
        int posIdx = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_POS, 0);
        int alpha = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_ALPHA, 85);
        int sizeSp = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_SIZE, 14);
        int posX = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_X, 16);
        int posY = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_Y, 16);

        if (actionIdx < 0 || actionIdx >= STILL_WATCHING_ACTIONS.length) actionIdx = 0;
        if (posIdx < 0 || posIdx >= STILL_WATCHING_POSITIONS.length) posIdx = 0;

        if (btnStillWatchingToggle != null) btnStillWatchingToggle.setText("   Estado:  " + (active ? "ACTIVADO" : "DESACTIVADO"));
        if (txtStillWatchingInterval != null) txtStillWatchingInterval.setText(interval + "m");
        if (txtStillWatchingTimeout != null) txtStillWatchingTimeout.setText(timeout + "s");
        if (btnStillWatchingActionType != null) btnStillWatchingActionType.setText("   Acción Inactividad:  " + STILL_WATCHING_ACTIONS[actionIdx]);
        if (btnStillWatchingPosition != null) btnStillWatchingPosition.setText("   Posición:  " + STILL_WATCHING_POSITIONS[posIdx]);
        if (txtStillWatchingAlpha != null) txtStillWatchingAlpha.setText(alpha + "%");
        if (txtStillWatchingSize != null) txtStillWatchingSize.setText(sizeSp + "sp");
        if (txtStillWatchingX != null) txtStillWatchingX.setText(posX + "dp");
        if (txtStillWatchingY != null) txtStillWatchingY.setText(posY + "dp");

        if (btnApplyStillWatching != null) btnApplyStillWatching.setText(active ? "[ Desactivar ¿Sigues viendo? ]" : "[ Activar ¿Sigues viendo? ]");
    }

    private void updateNightScheduleConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        boolean active = op.getBoolean(ButtonMappingService.KEY_NIGHT_SCHEDULE, false);
        int start = op.getInt(ButtonMappingService.KEY_NIGHT_START, 22);
        int end = op.getInt(ButtonMappingService.KEY_NIGHT_END, 7);
        int blueLight = op.getInt(ButtonMappingService.KEY_NIGHT_BLUE_LIGHT, 40);
        int dimmer = op.getInt(ButtonMappingService.KEY_NIGHT_DIMMER, 50);

        if (btnNightScheduleToggle != null) btnNightScheduleToggle.setText("   Estado:  " + (active ? "ACTIVADO" : "DESACTIVADO"));
        if (txtNightStart != null) txtNightStart.setText(String.format(java.util.Locale.US, "%02d:00", start));
        if (txtNightEnd != null) txtNightEnd.setText(String.format(java.util.Locale.US, "%02d:00", end));
        if (txtNightBlueLight != null) txtNightBlueLight.setText(blueLight + "%");
        if (txtNightDimmer != null) txtNightDimmer.setText(dimmer + "%");
        if (btnApplyNightSchedule != null) btnApplyNightSchedule.setText(active ? "[ Desactivar Horario Nocturno ]" : "[ Activar Horario Nocturno ]");
    }

    private void updateOledSaverConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        boolean active = op.getBoolean(ButtonMappingService.KEY_OLED_SAVER, false);
        int mins = op.getInt(ButtonMappingService.KEY_OLED_MINUTES, 5);
        int mode = op.getInt(ButtonMappingService.KEY_OLED_MODE, 0);

        String modeText = (mode == 1) ? "Pantalla Negra" : "Dimmer 95%";

        if (btnOledSaverToggle != null) btnOledSaverToggle.setText("   Estado:  " + (active ? "ACTIVADO" : "DESACTIVADO"));
        if (txtOledMinutes != null) txtOledMinutes.setText(mins + "m");
        if (btnOledMode != null) btnOledMode.setText("   Modo Protector:  " + modeText);
        if (btnApplyOledSaver != null) btnApplyOledSaver.setText(active ? "[ Desactivar Protector OLED ]" : "[ Activar Protector OLED ]");
    }

    private void updateScheduledSleepConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        boolean active = op.getBoolean("scheduled_sleep_enabled", false);
        int hour = op.getInt("scheduled_sleep_hour", 23);
        int min = op.getInt("scheduled_sleep_minute", 30);
        String daysStr = op.getString("scheduled_sleep_days", "1,2,3,4,5,6,7");
        java.util.Set<Integer> activeDays = new java.util.HashSet<>();
        for (String d : daysStr.split(",")) {
            try { activeDays.add(Integer.parseInt(d.trim())); } catch (Exception ignored) {}
        }

        if (btnScheduledSleepToggle != null) btnScheduledSleepToggle.setText("   Estado:  " + (active ? "ACTIVADO" : "DESACTIVADO"));
        if (txtScheduledHour != null) txtScheduledHour.setText(String.format(java.util.Locale.US, "%02d hs", hour));
        if (txtScheduledMin != null) txtScheduledMin.setText(String.format(java.util.Locale.US, "%02d min", min));

        TextView[] dayBtns = {btnDay1, btnDay2, btnDay3, btnDay4, btnDay5, btnDay6, btnDay7};
        for (int i = 0; i < 7; i++) {
            if (dayBtns[i] != null) {
                boolean isOn = activeDays.contains(i + 1);
                boolean hasFocus = dayBtns[i].hasFocus();

                if (hasFocus) {
                    dayBtns[i].setTextColor(0xFFFFFFFF);
                    dayBtns[i].setBackgroundColor(0xFFFFB74D); // Bright Amber/Orange cursor highlight
                } else if (isOn) {
                    dayBtns[i].setTextColor(0xFF4CAF50);
                    dayBtns[i].setBackgroundColor(Color.argb(80, 76, 175, 80));
                } else {
                    dayBtns[i].setTextColor(0xFF888888);
                    dayBtns[i].setBackgroundColor(Color.argb(40, 255, 255, 255));
                }
            }
        }

        String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        String skipStr = op.getString("scheduled_sleep_skip_date", "");
        boolean isSkipped = todayStr.equals(skipStr);

        if (btnScheduledSkipNext != null) {
            btnScheduledSkipNext.setText(isSkipped ? "[ Próxima alarma salteada (Reactivar) ]" : "[ Saltear próxima alarma ]");
        }
        if (btnApplyScheduledSleep != null) {
            btnApplyScheduledSleep.setText(active ? "[ Desactivar Apagado Programado ]" : "[ Activar Apagado Programado ]");
        }
    }

    private void toggleScheduledSleepDay(int day) {
        SharedPreferences prefs = getOverlayPrefs();
        String daysStr = prefs.getString("scheduled_sleep_days", "1,2,3,4,5,6,7");
        java.util.Set<Integer> set = new java.util.HashSet<>();
        for (String d : daysStr.split(",")) {
            try { set.add(Integer.parseInt(d.trim())); } catch (Exception ignored) {}
        }
        if (set.contains(day)) {
            if (set.size() > 1) set.remove(day);
        } else {
            set.add(day);
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 1; i <= 7; i++) {
            if (set.contains(i)) {
                if (!first) sb.append(",");
                sb.append(i);
                first = false;
            }
        }
        prefs.edit().putString("scheduled_sleep_days", sb.toString()).apply();
        sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
        updateScheduledSleepConfigPanel();
    }

    private interface StepAdjuster {
        void adjust(int step);
    }

    private void setupAutoRepeatStepButton(final View view, final int dir, final StepAdjuster adjuster) {
        if (view == null) return;

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjuster.adjust(dir * 1);
                view.requestFocus();
            }
        });

        view.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        int repeat = event.getRepeatCount();
                        if (repeat > 0) {
                            int step = 1;
                            if (repeat > 20) step = 10;
                            else if (repeat > 8) step = 5;
                            adjuster.adjust(dir * step);
                            view.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    private void cycleStillWatchingAction() {
        int cur = getOverlayPrefs().getInt(ButtonMappingService.KEY_STILL_WATCHING_ACTION, 0);
        int next = (cur + 1) % STILL_WATCHING_ACTIONS.length;
        getOverlayPrefs().edit().putInt(ButtonMappingService.KEY_STILL_WATCHING_ACTION, next).apply();
        updateStillWatchingConfigPanel();
        sendServiceAction("ACTION_UPDATE_STILL_WATCHING");
    }

    private void cycleStillWatchingPosition() {
        int cur = getOverlayPrefs().getInt(ButtonMappingService.KEY_STILL_WATCHING_POS, 0);
        int next = (cur + 1) % STILL_WATCHING_POSITIONS.length;
        getOverlayPrefs().edit().putInt(ButtonMappingService.KEY_STILL_WATCHING_POS, next).apply();
        updateStillWatchingConfigPanel();
        sendServiceAction("ACTION_UPDATE_STILL_WATCHING");
    }

    private void adjustIntPref(String key, int def, int delta, int min, int max, String actionName) {
        int cur = getOverlayPrefs().getInt(key, def);
        int next = cur + delta;
        if (next < min) next = min;
        if (next > max) next = max;
        getOverlayPrefs().edit().putInt(key, next).apply();
        if (actionName != null) {
            sendServiceAction(actionName);
        }
    }

    private void adjustStillWatchingIntPref(String key, int def, int delta, int min, int max) {
        adjustIntPref(key, def, delta, min, max, "ACTION_UPDATE_STILL_WATCHING");
        updateStillWatchingConfigPanel();
    }
}
