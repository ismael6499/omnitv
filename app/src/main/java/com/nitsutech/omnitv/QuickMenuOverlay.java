package com.nitsutech.omnitv;

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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioManager;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class QuickMenuOverlay {

    private static final String TAG = "QuickMenuOverlay";
    private static final String PREFS_ORDER = "menu_order_prefs";
    private static final String KEY_ORDER = "item_order";
    private static final String[] ALL_ITEM_IDS = {
        "manage_apps", "timer", "blue_light", "clock", "dimmer", "grayscale",
        "cine_mode", "auto_pause", "screen_off", "system_menu",
        "google_home", "bluetooth", "system_info", "reboot",
        "pause_screen_off", "scheduled_sleep", "cycle_brightness", "mindful_delay", "still_watching",
        "night_schedule", "oled_saver", "vot", "trigger_ai_summary", "ai_summary", "trigger_translate", "translate", "button_combos",
        "config_mute", "config_youtube_190", "config_youtube_189",
        "developer_options", "language"
    };

    public static final int TOTAL_ACTIONS = 35;
    private static final String[] AI_PROVIDER_NAMES = {"Google Gemini Direct", "OpenRouter (Multi-model)"};
    private static final String[] AI_GEMINI_MODELS = {"gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-2.5-flash", "gemini-3.5-flash"};
    private static final String[] AI_OPENROUTER_MODELS = {
        "google/gemini-2.0-flash-001",
        "deepseek/deepseek-chat",
        "openai/gpt-4o-mini",
        "meta-llama/llama-3.3-70b-instruct:free"
    };

    private static final String[] VOT_SOURCE_LANGS = {"auto", "en", "ko", "ja"};
    private static final String[] VOT_SOURCE_LANG_NAMES = {"Auto (Detectar)", "Inglés (English)", "Coreano (한국어)", "Japonés (日本語)"};
    private static final String[] VOT_TARGET_LANGS = {"es", "en", "ko", "ja"};
    private static final String[] VOT_TARGET_LANG_NAMES = {"Español (Latam)", "Inglés (English)", "Coreano (한국어)", "Japonés (日本語)"};
    private static final float[] VOT_SPEECH_RATES = {1.0f, 1.15f, 1.3f, 1.45f};
    private static final String[] VOT_SPEECH_RATE_NAMES = {"Normal (1.0x)", "Rápido (1.15x)", "Muy Rápido (1.3x)", "Ultra (1.45x)"};
    private static final float[] VOT_TTS_VOLUMES = {1.0f, 0.85f, 0.70f, 0.50f};
    private static final String[] VOT_TTS_VOLUME_NAMES = {"100% (Máximo)", "85% (Equilibrado)", "70% (Medio)", "50% (Bajo)"};
    private static final int[] VOT_DUCKING_MODES = {0, 1, 2};
    private static final String[] VOT_DUCKING_MODE_NAMES = {"Atenuar Video (~25%)", "Silenciar Video al Hablar", "Sin Atenuación (Normal)"};
    private static final String[] VOT_PROVIDER_NAMES = {"Google (Gratuito)", "MyMemory (50k/día)", "OpenRouter IA"};
    private static final String[] VOT_OPENROUTER_MODELS = {
        "deepseek/deepseek-chat",
        "google/gemini-2.0-flash-001",
        "openai/gpt-4o-mini",
        "meta-llama/llama-3.3-70b-instruct:free",
        "mistralai/mistral-small-24b-instruct-2501"
    };

    private static final String[] TRANSLATE_TARGET_LANGS = {"Español", "English"};
    private static final String[] TRANSLATE_SOURCE_LANGS = {
        "Auto (Modo Clásico Original)",
        "Coreano (Hangul)",
        "Japonés (Kanji/Kana)",
        "Chino (Hanzi)",
        "Inglés / Otros",
        "Multi-Idioma (Paralelo JP+KO)"
    };

    private static final String[] STILL_WATCHING_POSITIONS = {"Arriba Izquierda", "Arriba Derecha", "Abajo Izquierda", "Abajo Derecha", "Centro"};
    private static final String[] STILL_WATCHING_ACTIONS = {"Pausar Video", "Pausar y Apagar Pantalla", "Enviar Tecla Atrás"};
    private static final String[] STILL_WATCHING_TONES = {"Clásico (800Hz)", "Ding-Dong (Campana)", "Grave (550Hz)", "Agudo (1050Hz)"};

    private static final String[] MINDFUL_MSG_OPTIONS = {
        "¿Realmente querés ver algo ahora? Esperá o volvé a Home.",
        "Pausa consciente: ¿Es una distracción o una decisión?",
        "Tomate un momento para respirar antes de entrar.",
        "Tiempo de espera activo para evitar el consumo compulsivo."
    };
    private static final String[] MINDFUL_CANCEL_ACTIONS = {
        "Ir a Pantalla Principal (Home)",
        "Enviar Tecla Atrás (Back)",
        "Apagar Pantalla (Negro)"
    };
    private static final String[] MINDFUL_POSITIONS = {
        "Centro", "Arriba Izquierda", "Arriba Derecha", "Abajo Izquierda", "Abajo Derecha"
    };
    private static final String[] MINDFUL_SESSION_NAMES = {
        "Por Tiempo Personalizado",
        "Solo mientras no salga a Home",
        "Hasta apagar la pantalla / TV",
        "Todo el día (hasta medianoche)"
    };

    private static final String[] CLOCK_SIZE_NAMES = {"Chico (12sp)", "Mediano (16sp)", "Grande (20sp)", "Extra Grande (24sp)"};
    private static final String[] CLOCK_COLOR_NAMES = {"Blanco", "Negro", "Amarillo", "Rojo", "Verde", "Azul"};
    private static final String[] CLOCK_BG_NAMES = {"Negro", "Gris Oscuro", "Azul Marino", "Ninguno (Transparente)"};
    private static final String[] CLOCK_ALPHA_NAMES = {"0% (Transparente)", "25%", "35%", "50%", "75%", "100% (Opaco)"};
    private static final String[] CLOCK_POSITION_NAMES = {"Arriba Derecha", "Arriba Izquierda", "Abajo Derecha", "Abajo Izquierda", "Centro"};
    private static final String[] BRIGHTNESS_HUD_FORMAT_NAMES = {
        "100% (1/5)",
        "100% Brillo (1/5)",
        "Brillo 100% (1/5)",
        "(1/5) 100%",
        "100%",
        "Nivel 1/5 (100%)"
    };
    private static final int[] CINE_TIMER_OPTIONS = {0, 30, 60, 90, 120, 150, 180};
    private static final int[] SCHEDULED_PROMPT_OPTIONS = {0, 30, 60, 120};

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
    private TextView btnDimmerDayAutoReset;
    private TextView btnGrayscaleDayAutoReset;
    private TextView btnDayResetStartDec, btnDayResetStartInc, txtDayResetStart;
    private TextView btnDayResetEndDec, btnDayResetEndInc, txtDayResetEnd;
    private TextView btnApplyBrightness;
    private LinearLayout panelButtonConfig;
    private LinearLayout panelAutoPause;

    private LinearLayout panelStillWatching;
    private TextView btnStillWatchingToggle;
    private TextView btnStillWatchingIntervalDec, btnStillWatchingIntervalInc, txtStillWatchingInterval;
    private TextView btnStillWatchingTimeoutDec, btnStillWatchingTimeoutInc, txtStillWatchingTimeout;
    private TextView btnStillWatchingBeepToggle;
    private TextView btnStillWatchingBeepIntervalDec, btnStillWatchingBeepIntervalInc, txtStillWatchingBeepInterval;
    private TextView btnStillWatchingBeepDelayDec, btnStillWatchingBeepDelayInc, txtStillWatchingBeepDelay;
    private TextView btnStillWatchingBeepVolDec, btnStillWatchingBeepVolInc, txtStillWatchingBeepVol;
    private TextView btnStillWatchingBeepTone;
    private TextView btnTestStillWatchingBeep;
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
    private TextView btnScheduledAlarmPrev;
    private TextView txtScheduledAlarmSelector;
    private TextView btnScheduledAlarmNext;
    private TextView btnAddScheduledAlarm;
    private TextView btnDeleteScheduledAlarm;
    private TextView btnScheduledSleepToggle;
    private TextView btnScheduledHourDec, txtScheduledHour, btnScheduledHourInc;
    private TextView btnScheduledMinDec, txtScheduledMin, btnScheduledMinInc;
    private TextView btnDay1, btnDay2, btnDay3, btnDay4, btnDay5, btnDay6, btnDay7;
    private TextView btnScheduledSkipNext;
    private TextView btnApplyScheduledSleep;
    private int selectedScheduledAlarmIndex = 0;

    private TextView btnAutoPauseMode;
    private LinearLayout layoutAutoPauseCustom;
    private TextView btnAutoPauseCountDec;
    private TextView txtAutoPauseCount;
    private TextView btnAutoPauseCountInc;
    private TextView btnAutoPauseBlackScreen;
    private TextView btnAutoDismissUpNext;

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
    private TextView btnConfigClick4;
    private TextView btnConfigLong;
    private TextView btnConfigDurationDec;
    private TextView btnConfigDurationInc;
    private TextView txtConfigDuration;
    private String configuringButton = null;

    // Action Picker Dialog Fields
    private FrameLayout dialogActionPickerOverlay;
    private TextView txtActionPickerTitle;
    private TextView txtActionPickerSubtitle;
    private LinearLayout containerActionPickerItems;
    private View lastActionPickerTriggerView = null;

    // Translate Config Fields
    private LinearLayout panelTranslateConfig;
    private TextView btnTranslateTargetLang;
    private TextView btnTranslateSourceLang;
    private TextView btnTranslateAutoPause;
    private TextView btnTranslateAutoResume;
    private TextView btnTranslateTopBar;
    private TextView btnTranslateBgAlphaDec, btnTranslateBgAlphaInc, txtTranslateBgAlpha;
    private TextView btnTranslateTextSizeDec, btnTranslateTextSizeInc, txtTranslateTextSize;
    private TextView btnTestTranslate, btnApplyTranslate;

    // Button Combos Fields
    private LinearLayout panelButtonCombos;
    private TextView btnCombosMasterToggle;
    private TextView btnComboMuteOk, btnComboMuteRight, btnComboMuteLeft, btnComboMuteUp, btnComboMuteDown;
    private TextView btnComboYoutube190Mute;
    private TextView btnComboInputOk;
    private TextView btnApplyCombos;
 
    // VOT Config Fields
    private LinearLayout panelVotConfig;
    private TextView btnVotMasterToggle;
    private TextView btnVotSourceLang;
    private TextView btnVotTargetLang;
    private TextView btnVotTtsVolume;
    private TextView btnVotDuckingMode;
    private TextView btnVotSubtitlesMode;
    private TextView btnVotSpeechRate;
    private TextView btnVotProvider;
    private TextView btnVotOpenrouterModel;
    private TextView btnVotOpenrouterKey;
    private TextView btnVotTestVoice;
    private TextView btnVotApply;

    // AI Summary Config Panel Fields
    private LinearLayout panelAiSummaryConfig;
    private TextView btnAiLaunchNow;
    private TextView btnAiProviderToggle;
    private TextView btnAiModelToggle;
    private TextView btnAiUiLangToggle;
    private TextView btnAiApiKeyConfig;
    private TextView btnAiApiHelp;
    private TextView btnAiApply;

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
    private TextView btnScheduledPromptToggle;

    // Cycle Brightness & OSD HUD Config Panel Fields
    private LinearLayout panelCycleBrightness;
    private TextView btnGotoHudConfig;
    private TextView btnCycleBrightnessNow;
    private TextView btnBrightnessHudFormat;
    private TextView btnBrightnessHudTextColor;
    private TextView btnBrightnessHudBgColor;
    private TextView btnBrightnessHudPosition;
    private TextView btnBrightnessHudAlphaDec, btnBrightnessHudAlphaInc, txtBrightnessHudAlpha;
    private TextView btnBrightnessHudTextAlphaDec, btnBrightnessHudTextAlphaInc, txtBrightnessHudTextAlpha;
    private TextView btnBrightnessHudSizeDec, btnBrightnessHudSizeInc, txtBrightnessHudSize;
    private TextView btnBrightnessHudPadDec, btnBrightnessHudPadInc, txtBrightnessHudPad;
    private TextView btnBrightnessHudXDec, btnBrightnessHudXInc, txtBrightnessHudX;
    private TextView btnBrightnessHudYDec, btnBrightnessHudYInc, txtBrightnessHudY;
    private TextView btnBrightnessHudDurDec, btnBrightnessHudDurInc, txtBrightnessHudDur;
    private TextView btnTestBrightnessHud;

    // Quick Brightness Slider Fields
    private LinearLayout panelQuickBrightnessSlider;
    private TextView btnGotoSliderConfig, btnGotoSliderFromCycle;
    private TextView btnSliderTestNow, btnSliderOrientation, btnSliderStep, btnSliderPerpAction, btnSliderPosition, btnSliderTimeout;

    private static final String[] SLIDER_ORIENTATIONS = {"Horizontal", "Vertical"};
    private static final String[] SLIDER_STEPS_LABELS = {"1% (Default)", "0.5%", "0.1%", "2%", "5%"};
    private static final float[] SLIDER_STEPS_VALUES = {1.0f, 0.5f, 0.1f, 2.0f, 5.0f};
    private static final String[] SLIDER_PERP_ACTIONS = {
        "Ciclar Niveles Guardados (Default)",
        "Saltos de 5%",
        "Saltos de 10%",
        "Saltos de 20%",
        "Extremos (1% y 100%)"
    };
    private static final String[] SLIDER_POSITIONS_H = {"Abajo Centro (Default)", "Centro", "Arriba Centro"};
    private static final String[] SLIDER_POSITIONS_V = {"Derecha (Default)", "Izquierda", "Centro"};
    private static final String[] SLIDER_TIMEOUTS_LABELS = {"3 segundos (Default)", "2 segundos", "4 segundos", "5 segundos"};
    private static final int[] SLIDER_TIMEOUTS_VALUES = {3000, 2000, 4000, 5000};

    // Mindful Delay (Espera Consciente) Fields
    private LinearLayout panelMindfulDelay;
    private TextView btnMindfulDelayToggle;
    private TextView btnMindfulDelayMinDec, btnMindfulDelayMinInc, txtMindfulDelayMin;
    private TextView btnMindfulDelaySecDec, btnMindfulDelaySecInc, txtMindfulDelaySec;
    private TextView btnMindfulDelayCancelAction;
    private TextView btnMindfulDelaySession;
    private LinearLayout rowMindfulSessHours, rowMindfulSessMins;
    private TextView btnMindfulDelaySessHourDec, btnMindfulDelaySessHourInc, txtMindfulDelaySessHour;
    private TextView btnMindfulDelaySessMinDec, btnMindfulDelaySessMinInc, txtMindfulDelaySessMin;
    private TextView btnMindfulDelayPos;
    private TextView btnMindfulDelayMsg;
    private TextView btnMindfulDelayBgAlphaDec, btnMindfulDelayBgAlphaInc, txtMindfulDelayBgAlpha;
    private TextView btnMindfulDelayTextSizeDec, btnMindfulDelayTextSizeInc, txtMindfulDelayTextSize;
    private TextView btnMindfulDelayPadDec, btnMindfulDelayPadInc, txtMindfulDelayPad;
    private TextView btnMindfulDelayXDec, btnMindfulDelayXInc, txtMindfulDelayX;
    private TextView btnMindfulDelayYDec, btnMindfulDelayYInc, txtMindfulDelayY;
    private TextView btnMindfulAppYoutube, btnMindfulAppNetflix, btnMindfulAppDisney;
    private TextView btnMindfulAppPrime, btnMindfulAppMax, btnMindfulAppStar;
    private TextView btnMindfulAppTwitch, btnMindfulAppTiktok, btnMindfulAppSmarttube;
    private TextView btnMindfulAppStremio, btnMindfulAppPlex;
    private TextView btnTestMindfulDelay, btnApplyMindfulDelay;


    // Custom Timer fields
    private TextView txtCustomHours;
    private TextView txtCustomMins;
    private int customHours = 0;
    private int customMins = 0;

    // SeekBar fields
    private SeekBar sliderBlueLight;
    private TextView txtBlueLightPct;

    private View menuDimmerFilter;
    private View menuBlueLightFilter;

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

    public void preWarm(Context ctx) {
        this.context = ctx;
        try {
            ensureViews();
            Log.d(TAG, "QuickMenuOverlay views pre-warmed successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error pre-warming QuickMenuOverlay", e);
        }
    }

    private void ensureViews() {
        if (rootView != null) return;
        Context locContext = I18n.getLocalizedContext(context);
        rootView = LayoutInflater.from(locContext != null ? locContext : context).inflate(R.layout.activity_quick_menu, null);

        menuDimmerFilter    = rootView.findViewById(R.id.menu_dimmer_filter);
        menuBlueLightFilter = rootView.findViewById(R.id.menu_blue_light_filter);

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
        btnAutoDismissUpNext   = rootView.findViewById(R.id.btn_auto_dismiss_up_next);

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
        btnGotoSliderConfig      = rootView.findViewById(R.id.btn_goto_slider_config);
        btnDimmerDayAutoReset    = rootView.findViewById(R.id.btn_dimmer_day_auto_reset);
        btnGrayscaleDayAutoReset = rootView.findViewById(R.id.btn_grayscale_day_auto_reset);
        btnDayResetStartDec      = rootView.findViewById(R.id.btn_day_reset_start_dec);
        txtDayResetStart         = rootView.findViewById(R.id.txt_day_reset_start);
        btnDayResetStartInc      = rootView.findViewById(R.id.btn_day_reset_start_inc);
        btnDayResetEndDec        = rootView.findViewById(R.id.btn_day_reset_end_dec);
        txtDayResetEnd           = rootView.findViewById(R.id.txt_day_reset_end);
        btnDayResetEndInc        = rootView.findViewById(R.id.btn_day_reset_end_inc);
        btnApplyBrightness       = rootView.findViewById(R.id.btn_apply_brightness);

        panelButtonConfig    = rootView.findViewById(R.id.panel_button_config);
        txtConfigTitle       = rootView.findViewById(R.id.txt_config_title);
        btnConfigClick1      = rootView.findViewById(R.id.btn_config_click_1);
        btnConfigClick2      = rootView.findViewById(R.id.btn_config_click_2);
        btnConfigClick3      = rootView.findViewById(R.id.btn_config_click_3);
        btnConfigClick4      = rootView.findViewById(R.id.btn_config_click_4);
        btnConfigLong        = rootView.findViewById(R.id.btn_config_long);
        btnConfigDurationDec = rootView.findViewById(R.id.btn_config_duration_dec);
        btnConfigDurationInc = rootView.findViewById(R.id.btn_config_duration_inc);
        txtConfigDuration    = rootView.findViewById(R.id.txt_config_duration);

        dialogActionPickerOverlay    = rootView.findViewById(R.id.dialog_action_picker_overlay);
        txtActionPickerTitle         = rootView.findViewById(R.id.txt_action_picker_title);
        txtActionPickerSubtitle      = rootView.findViewById(R.id.txt_action_picker_subtitle);
        containerActionPickerItems   = rootView.findViewById(R.id.container_action_picker_items);
        if (dialogActionPickerOverlay != null) {
            dialogActionPickerOverlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeActionPickerDialog();
                    if (lastActionPickerTriggerView != null) {
                        requestViewFocus(lastActionPickerTriggerView);
                    }
                }
            });
        }

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

        // Still Watching panel
        panelStillWatching           = rootView.findViewById(R.id.panel_still_watching);
        btnStillWatchingToggle       = rootView.findViewById(R.id.btn_still_watching_action_toggle);
        btnStillWatchingIntervalDec  = rootView.findViewById(R.id.btn_still_watching_interval_dec);
        btnStillWatchingIntervalInc  = rootView.findViewById(R.id.btn_still_watching_interval_inc);
        txtStillWatchingInterval     = rootView.findViewById(R.id.txt_still_watching_interval);
        btnStillWatchingTimeoutDec   = rootView.findViewById(R.id.btn_still_watching_timeout_dec);
        btnStillWatchingTimeoutInc   = rootView.findViewById(R.id.btn_still_watching_timeout_inc);
        txtStillWatchingTimeout      = rootView.findViewById(R.id.txt_still_watching_timeout);
        btnStillWatchingBeepToggle   = rootView.findViewById(R.id.btn_still_watching_beep_toggle);
        btnStillWatchingBeepIntervalDec = rootView.findViewById(R.id.btn_still_watching_beep_interval_dec);
        btnStillWatchingBeepIntervalInc = rootView.findViewById(R.id.btn_still_watching_beep_interval_inc);
        txtStillWatchingBeepInterval = rootView.findViewById(R.id.txt_still_watching_beep_interval);
        btnStillWatchingBeepDelayDec = rootView.findViewById(R.id.btn_still_watching_beep_delay_dec);
        btnStillWatchingBeepDelayInc = rootView.findViewById(R.id.btn_still_watching_beep_delay_inc);
        txtStillWatchingBeepDelay    = rootView.findViewById(R.id.txt_still_watching_beep_delay);
        btnStillWatchingBeepVolDec   = rootView.findViewById(R.id.btn_still_watching_beep_vol_dec);
        btnStillWatchingBeepVolInc   = rootView.findViewById(R.id.btn_still_watching_beep_vol_inc);
        txtStillWatchingBeepVol      = rootView.findViewById(R.id.txt_still_watching_beep_vol);
        btnStillWatchingBeepTone     = rootView.findViewById(R.id.btn_still_watching_beep_tone);
        btnTestStillWatchingBeep     = rootView.findViewById(R.id.btn_test_still_watching_beep);
        btnStillWatchingActionType   = rootView.findViewById(R.id.btn_still_watching_action_type);
        btnStillWatchingPosition     = rootView.findViewById(R.id.btn_still_watching_position);
        btnStillWatchingAlphaDec     = rootView.findViewById(R.id.btn_still_watching_alpha_dec);
        btnStillWatchingAlphaInc     = rootView.findViewById(R.id.btn_still_watching_alpha_inc);
        txtStillWatchingAlpha        = rootView.findViewById(R.id.txt_still_watching_alpha);
        btnStillWatchingSizeDec      = rootView.findViewById(R.id.btn_still_watching_size_dec);
        btnStillWatchingSizeInc      = rootView.findViewById(R.id.btn_still_watching_size_inc);
        txtStillWatchingSize         = rootView.findViewById(R.id.txt_still_watching_size);
        btnStillWatchingXDec         = rootView.findViewById(R.id.btn_still_watching_x_dec);
        btnStillWatchingXInc         = rootView.findViewById(R.id.btn_still_watching_x_inc);
        txtStillWatchingX            = rootView.findViewById(R.id.txt_still_watching_x);
        btnStillWatchingYDec         = rootView.findViewById(R.id.btn_still_watching_y_dec);
        btnStillWatchingYInc         = rootView.findViewById(R.id.btn_still_watching_y_inc);
        txtStillWatchingY            = rootView.findViewById(R.id.txt_still_watching_y);
        btnApplyStillWatching        = rootView.findViewById(R.id.btn_apply_still_watching);

        // Night schedule panel
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

        // OLED saver panel
        panelOledSaver               = rootView.findViewById(R.id.panel_oled_saver);
        btnOledSaverToggle           = rootView.findViewById(R.id.btn_oled_saver_toggle);
        btnOledMinutesDec            = rootView.findViewById(R.id.btn_oled_minutes_dec);
        txtOledMinutes               = rootView.findViewById(R.id.txt_oled_minutes);
        btnOledMinutesInc            = rootView.findViewById(R.id.btn_oled_minutes_inc);
        btnOledMode                  = rootView.findViewById(R.id.btn_oled_mode);
        btnApplyOledSaver            = rootView.findViewById(R.id.btn_apply_oled_saver);

        // Scheduled Sleep panel
        panelScheduledSleep          = rootView.findViewById(R.id.panel_scheduled_sleep);
        btnScheduledAlarmPrev        = rootView.findViewById(R.id.btn_scheduled_alarm_prev);
        txtScheduledAlarmSelector    = rootView.findViewById(R.id.txt_scheduled_alarm_selector);
        btnScheduledAlarmNext        = rootView.findViewById(R.id.btn_scheduled_alarm_next);
        btnAddScheduledAlarm         = rootView.findViewById(R.id.btn_add_scheduled_alarm);
        btnDeleteScheduledAlarm      = rootView.findViewById(R.id.btn_delete_scheduled_alarm);
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
        btnScheduledPromptToggle     = rootView.findViewById(R.id.btn_scheduled_prompt_toggle);
        btnApplyScheduledSleep       = rootView.findViewById(R.id.btn_apply_scheduled_sleep);

        panelCycleBrightness         = rootView.findViewById(R.id.panel_cycle_brightness);
        btnGotoHudConfig             = rootView.findViewById(R.id.btn_goto_hud_config);
        btnCycleBrightnessNow        = rootView.findViewById(R.id.btn_cycle_brightness_now);
        btnBrightnessHudFormat       = rootView.findViewById(R.id.btn_brightness_hud_format);
        btnBrightnessHudTextColor    = rootView.findViewById(R.id.btn_brightness_hud_text_color);
        btnBrightnessHudBgColor      = rootView.findViewById(R.id.btn_brightness_hud_bg_color);
        btnBrightnessHudPosition     = rootView.findViewById(R.id.btn_brightness_hud_position);
        btnBrightnessHudAlphaDec     = rootView.findViewById(R.id.btn_brightness_hud_alpha_dec);
        txtBrightnessHudAlpha        = rootView.findViewById(R.id.txt_brightness_hud_alpha);
        btnBrightnessHudAlphaInc     = rootView.findViewById(R.id.btn_brightness_hud_alpha_inc);
        btnBrightnessHudTextAlphaDec = rootView.findViewById(R.id.btn_brightness_hud_text_alpha_dec);
        txtBrightnessHudTextAlpha    = rootView.findViewById(R.id.txt_brightness_hud_text_alpha);
        btnBrightnessHudTextAlphaInc = rootView.findViewById(R.id.btn_brightness_hud_text_alpha_inc);
        btnBrightnessHudSizeDec      = rootView.findViewById(R.id.btn_brightness_hud_size_dec);
        txtBrightnessHudSize         = rootView.findViewById(R.id.txt_brightness_hud_size);
        btnBrightnessHudSizeInc      = rootView.findViewById(R.id.btn_brightness_hud_size_inc);
        btnBrightnessHudPadDec       = rootView.findViewById(R.id.btn_brightness_hud_pad_dec);
        txtBrightnessHudPad          = rootView.findViewById(R.id.txt_brightness_hud_pad);
        btnBrightnessHudPadInc       = rootView.findViewById(R.id.btn_brightness_hud_pad_inc);
        btnBrightnessHudXDec         = rootView.findViewById(R.id.btn_brightness_hud_x_dec);
        txtBrightnessHudX            = rootView.findViewById(R.id.txt_brightness_hud_x);
        btnBrightnessHudXInc         = rootView.findViewById(R.id.btn_brightness_hud_x_inc);
        btnBrightnessHudYDec         = rootView.findViewById(R.id.btn_brightness_hud_y_dec);
        txtBrightnessHudY            = rootView.findViewById(R.id.txt_brightness_hud_y);
        btnBrightnessHudYInc         = rootView.findViewById(R.id.btn_brightness_hud_y_inc);
        btnBrightnessHudDurDec       = rootView.findViewById(R.id.btn_brightness_hud_dur_dec);
        txtBrightnessHudDur          = rootView.findViewById(R.id.txt_brightness_hud_dur);
        btnBrightnessHudDurInc       = rootView.findViewById(R.id.btn_brightness_hud_dur_inc);
        btnTestBrightnessHud         = rootView.findViewById(R.id.btn_test_brightness_hud);
        btnGotoSliderFromCycle       = rootView.findViewById(R.id.btn_goto_slider_from_cycle);

        // Quick Brightness Slider panel
        panelQuickBrightnessSlider   = rootView.findViewById(R.id.panel_quick_brightness_slider);
        btnSliderTestNow             = rootView.findViewById(R.id.btn_slider_test_now);
        btnSliderOrientation         = rootView.findViewById(R.id.btn_slider_orientation);
        btnSliderStep                = rootView.findViewById(R.id.btn_slider_step);
        btnSliderPerpAction          = rootView.findViewById(R.id.btn_slider_perp_action);
        btnSliderPosition            = rootView.findViewById(R.id.btn_slider_position);
        btnSliderTimeout             = rootView.findViewById(R.id.btn_slider_timeout);

        panelMindfulDelay            = rootView.findViewById(R.id.panel_mindful_delay);
        btnMindfulDelayToggle        = rootView.findViewById(R.id.btn_mindful_delay_toggle);
        btnMindfulDelayMinDec        = rootView.findViewById(R.id.btn_mindful_delay_min_dec);
        txtMindfulDelayMin          = rootView.findViewById(R.id.txt_mindful_delay_min);
        btnMindfulDelayMinInc        = rootView.findViewById(R.id.btn_mindful_delay_min_inc);
        btnMindfulDelaySecDec        = rootView.findViewById(R.id.btn_mindful_delay_sec_dec);
        txtMindfulDelaySec          = rootView.findViewById(R.id.txt_mindful_delay_sec);
        btnMindfulDelaySecInc        = rootView.findViewById(R.id.btn_mindful_delay_sec_inc);
        btnMindfulDelayCancelAction  = rootView.findViewById(R.id.btn_mindful_delay_cancel_action);
        rowMindfulSessHours          = rootView.findViewById(R.id.row_mindful_sess_hours);
        rowMindfulSessMins           = rootView.findViewById(R.id.row_mindful_sess_mins);
        btnMindfulDelaySessHourDec   = rootView.findViewById(R.id.btn_mindful_delay_sess_hour_dec);
        txtMindfulDelaySessHour      = rootView.findViewById(R.id.txt_mindful_delay_sess_hour);
        btnMindfulDelaySessHourInc   = rootView.findViewById(R.id.btn_mindful_delay_sess_hour_inc);
        btnMindfulDelaySessMinDec    = rootView.findViewById(R.id.btn_mindful_delay_sess_min_dec);
        txtMindfulDelaySessMin       = rootView.findViewById(R.id.txt_mindful_delay_sess_min);
        btnMindfulDelaySessMinInc    = rootView.findViewById(R.id.btn_mindful_delay_sess_min_inc);
        btnMindfulDelayPos           = rootView.findViewById(R.id.btn_mindful_delay_pos);
        btnMindfulDelayMsg           = rootView.findViewById(R.id.btn_mindful_delay_msg);
        btnMindfulDelayBgAlphaDec    = rootView.findViewById(R.id.btn_mindful_delay_bg_alpha_dec);
        txtMindfulDelayBgAlpha       = rootView.findViewById(R.id.txt_mindful_delay_bg_alpha);
        btnMindfulDelayBgAlphaInc    = rootView.findViewById(R.id.btn_mindful_delay_bg_alpha_inc);
        btnMindfulDelayTextSizeDec   = rootView.findViewById(R.id.btn_mindful_delay_text_size_dec);
        txtMindfulDelayTextSize      = rootView.findViewById(R.id.txt_mindful_delay_text_size);
        btnMindfulDelayTextSizeInc   = rootView.findViewById(R.id.btn_mindful_delay_text_size_inc);
        btnMindfulDelayPadDec        = rootView.findViewById(R.id.btn_mindful_delay_pad_dec);
        txtMindfulDelayPad           = rootView.findViewById(R.id.txt_mindful_delay_pad);
        btnMindfulDelayPadInc        = rootView.findViewById(R.id.btn_mindful_delay_pad_inc);
        btnMindfulDelayXDec          = rootView.findViewById(R.id.btn_mindful_delay_x_dec);
        txtMindfulDelayX             = rootView.findViewById(R.id.txt_mindful_delay_x);
        btnMindfulDelayXInc          = rootView.findViewById(R.id.btn_mindful_delay_x_inc);
        btnMindfulDelayYDec          = rootView.findViewById(R.id.btn_mindful_delay_y_dec);
        txtMindfulDelayY             = rootView.findViewById(R.id.txt_mindful_delay_y);
        btnMindfulDelayYInc          = rootView.findViewById(R.id.btn_mindful_delay_y_inc);
        btnMindfulAppYoutube         = rootView.findViewById(R.id.btn_mindful_app_youtube);
        btnMindfulAppNetflix         = rootView.findViewById(R.id.btn_mindful_app_netflix);
        btnMindfulAppDisney          = rootView.findViewById(R.id.btn_mindful_app_disney);
        btnMindfulAppPrime           = rootView.findViewById(R.id.btn_mindful_app_prime);
        btnMindfulAppMax             = rootView.findViewById(R.id.btn_mindful_app_max);
        btnMindfulAppStar            = rootView.findViewById(R.id.btn_mindful_app_star);
        btnMindfulAppTwitch          = rootView.findViewById(R.id.btn_mindful_app_twitch);
        btnMindfulAppTiktok          = rootView.findViewById(R.id.btn_mindful_app_tiktok);
        btnMindfulAppSmarttube       = rootView.findViewById(R.id.btn_mindful_app_smarttube);
        btnMindfulAppStremio         = rootView.findViewById(R.id.btn_mindful_app_stremio);
        btnMindfulAppPlex            = rootView.findViewById(R.id.btn_mindful_app_plex);
        btnTestMindfulDelay          = rootView.findViewById(R.id.btn_test_mindful_delay);
        btnApplyMindfulDelay         = rootView.findViewById(R.id.btn_apply_mindful_delay);

        // Translate panel
        panelTranslateConfig         = rootView.findViewById(R.id.panel_translate_config);
        btnTranslateTargetLang       = rootView.findViewById(R.id.btn_translate_target_lang);
        btnTranslateSourceLang       = rootView.findViewById(R.id.btn_translate_source_lang);
        btnTranslateAutoPause        = rootView.findViewById(R.id.btn_translate_auto_pause);
        btnTranslateAutoResume       = rootView.findViewById(R.id.btn_translate_auto_resume);
        btnTranslateTopBar           = rootView.findViewById(R.id.btn_translate_top_bar);
        btnTranslateBgAlphaDec       = rootView.findViewById(R.id.btn_translate_bg_alpha_dec);
        txtTranslateBgAlpha          = rootView.findViewById(R.id.txt_translate_bg_alpha);
        btnTranslateBgAlphaInc       = rootView.findViewById(R.id.btn_translate_bg_alpha_inc);
        btnTranslateTextSizeDec      = rootView.findViewById(R.id.btn_translate_text_size_dec);
        txtTranslateTextSize         = rootView.findViewById(R.id.txt_translate_text_size);
        btnTranslateTextSizeInc      = rootView.findViewById(R.id.btn_translate_text_size_inc);
        btnTestTranslate             = rootView.findViewById(R.id.btn_test_translate);
        btnApplyTranslate            = rootView.findViewById(R.id.btn_apply_translate);

        // Button combos panel
        panelButtonCombos            = rootView.findViewById(R.id.panel_button_combos);
        btnCombosMasterToggle        = rootView.findViewById(R.id.btn_combos_master_toggle);
        btnComboMuteOk               = rootView.findViewById(R.id.btn_combo_mute_ok);
        btnComboMuteRight            = rootView.findViewById(R.id.btn_combo_mute_right);
        btnComboMuteLeft             = rootView.findViewById(R.id.btn_combo_mute_left);
        btnComboMuteUp               = rootView.findViewById(R.id.btn_combo_mute_up);
        btnComboMuteDown             = rootView.findViewById(R.id.btn_combo_mute_down);
        btnComboYoutube190Mute       = rootView.findViewById(R.id.btn_combo_youtube190_mute);
        btnComboInputOk              = rootView.findViewById(R.id.btn_combo_input_ok);
        btnApplyCombos               = rootView.findViewById(R.id.btn_apply_combos);

        // VOT panel
        panelVotConfig               = rootView.findViewById(R.id.panel_vot_config);
        btnVotMasterToggle           = rootView.findViewById(R.id.btn_vot_master_toggle);
        btnVotSourceLang             = rootView.findViewById(R.id.btn_vot_source_lang);
        btnVotTargetLang             = rootView.findViewById(R.id.btn_vot_target_lang);
        btnVotTtsVolume              = rootView.findViewById(R.id.btn_vot_tts_volume);
        btnVotDuckingMode            = rootView.findViewById(R.id.btn_vot_ducking_mode);
        btnVotSubtitlesMode          = rootView.findViewById(R.id.btn_vot_subtitles_mode);
        btnVotSpeechRate             = rootView.findViewById(R.id.btn_vot_speech_rate);
        btnVotProvider               = rootView.findViewById(R.id.btn_vot_provider);
        btnVotOpenrouterModel        = rootView.findViewById(R.id.btn_vot_openrouter_model);
        btnVotOpenrouterKey          = rootView.findViewById(R.id.btn_vot_openrouter_key);
        btnVotTestVoice              = rootView.findViewById(R.id.btn_vot_test_voice);
        btnVotApply                  = rootView.findViewById(R.id.btn_vot_apply);

        // AI Summary panel
        panelAiSummaryConfig         = rootView.findViewById(R.id.panel_ai_summary_config);
        btnAiLaunchNow               = rootView.findViewById(R.id.btn_ai_launch_now);
        btnAiProviderToggle          = rootView.findViewById(R.id.btn_ai_provider_toggle);
        btnAiModelToggle             = rootView.findViewById(R.id.btn_ai_model_toggle);
        btnAiUiLangToggle            = rootView.findViewById(R.id.btn_ai_ui_lang_toggle);
        btnAiApiKeyConfig            = rootView.findViewById(R.id.btn_ai_api_key_config);
        btnAiApiHelp                 = rootView.findViewById(R.id.btn_ai_api_help);
        btnAiApply                   = rootView.findViewById(R.id.btn_ai_apply);

        setupSubPanelListeners();
    }

    public void show(Context ctx) {
        if (isShowing()) {
            dismiss();
        }

        this.context = ctx;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        try {
            ensureViews();

            updateMenuInternalFilters();
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

            if (rootView.getParent() != null) {
                try {
                    windowManager.removeViewImmediate(rootView);
                } catch (Exception ignored) {}
            }
            if (!rootView.isAttachedToWindow()) {
                windowManager.addView(rootView, p);
                Log.d(TAG, "QuickMenuOverlay attached to WindowManager successfully.");
            }
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
                if (mHoldTickCount > 20) {
                    nextDelay = 20;
                } else if (mHoldTickCount > 10) {
                    nextDelay = 35;
                } else if (mHoldTickCount > 4) {
                    nextDelay = 50;
                } else {
                    nextDelay = 75;
                }
                mHoldHandler.postDelayed(this, nextDelay);
            }
        };
        mHoldHandler.postDelayed(mHoldRunnable, 180);
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
            closeSubPanels();
            configuringButton = null;
            isReorderMode = false;
            reorderSelectedIndex = -1;
            try {
                if (windowManager != null && rootView.isAttachedToWindow()) {
                    windowManager.removeView(rootView);
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

        if (dialogActionPickerOverlay != null && dialogActionPickerOverlay.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (action == KeyEvent.ACTION_DOWN) {
                    stopHoldRepeat();
                    closeActionPickerDialog();
                    if (lastActionPickerTriggerView != null) {
                        requestViewFocus(lastActionPickerTriggerView);
                    }
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                stopHoldRepeat();
                if (openSubPanel != null) {
                    closeSubPanels();
                    buildMenu();
                    return true;
                }
                if (isReorderMode) {
                    if (reorderSelectedIndex != -1) {
                        reorderSelectedIndex = -1;
                        buildMenu();
                        return true;
                    }
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
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    boolean handled = handleOverlayNavigation(keyCode, action, event);
                    if (handled) {
                        startHoldRepeat(keyCode, event);
                        return true;
                    }
                } else {
                    return true;
                }
            } else if (action == KeyEvent.ACTION_UP) {
                if (keyCode == mHoldingKeyCode) {
                    stopHoldRepeat();
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_DOWN) {
                stopHoldRepeat();
                if (event.getRepeatCount() == 0) {
                    boolean handled = handleOverlayNavigation(keyCode, action, event);
                    if (handled) {
                        return true;
                    }
                }
            }
        }

        if (rootView != null) {
            return rootView.dispatchKeyEvent(event);
        }
        return true;
    }

    private ViewGroup getActiveSubPanelGroup() {
        if (dialogActionPickerOverlay != null && dialogActionPickerOverlay.getVisibility() == View.VISIBLE) {
            return containerActionPickerItems;
        }
        if (openSubPanel == null) return menuContainer;
        switch (openSubPanel) {
            case "timer": return panelTimer;
            case "blue_light": return panelBlueLight;
            case "clock_config": return panelClockConfig;
            case "brightness_config": return panelBrightness;
            case "cycle_brightness_config": return panelCycleBrightness;
            case "quick_slider_config": return panelQuickBrightnessSlider;
            case "cine": return panelCine;
            case "auto_pause": return panelAutoPause;
            case "still_watching": return panelStillWatching;
            case "night_schedule": return panelNightSchedule;
            case "oled_saver": return panelOledSaver;
            case "scheduled_sleep": return panelScheduledSleep;
            case "mindful_delay_config": return panelMindfulDelay;
            case "translate_config": return panelTranslateConfig;
            case "button_combos_config": return panelButtonCombos;
            case "vot_config": return panelVotConfig;
            case "ai_summary_config": return panelAiSummaryConfig;
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

        // 0. Reorder Mode: When an item is grabbed (selected), D-Pad UP/DOWN moves it dynamically!
        if (isReorderMode && reorderSelectedIndex != -1 && openSubPanel == null) {
            String[] order = getMenuOrder();
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (reorderSelectedIndex > 0) {
                    String temp = order[reorderSelectedIndex];
                    order[reorderSelectedIndex] = order[reorderSelectedIndex - 1];
                    order[reorderSelectedIndex - 1] = temp;
                    saveMenuOrder(order);
                    reorderSelectedIndex--;
                    lastFocusedId = order[reorderSelectedIndex];
                    buildMenu();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (reorderSelectedIndex < order.length - 1) {
                    String temp = order[reorderSelectedIndex];
                    order[reorderSelectedIndex] = order[reorderSelectedIndex + 1];
                    order[reorderSelectedIndex + 1] = temp;
                    saveMenuOrder(order);
                    reorderSelectedIndex++;
                    lastFocusedId = order[reorderSelectedIndex];
                    buildMenu();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                reorderSelectedIndex = -1;
                buildMenu();
                return true;
            }
        }

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

        // 1b. Option cycling navigation with D-Pad Left / Right / Center
        if (handleOptionCyclingNavigation(current, keyCode)) {
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

    private boolean handleOptionCyclingNavigation(View current, int keyCode) {
        if (current == null) return false;
        if (dialogActionPickerOverlay != null && dialogActionPickerOverlay.getVisibility() == View.VISIBLE) {
            return false;
        }

        // 1. Remote button action configs
        if (current == btnConfigClick1 || current == btnConfigClick2 || current == btnConfigClick3 || current == btnConfigClick4 || current == btnConfigLong) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                showActionPickerDialog(current);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                adjustButtonConfigAction(current, -1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                adjustButtonConfigAction(current, 1);
                return true;
            }
            return false;
        }

        // 2. Button combo actions
        if (current == btnComboMuteOk || current == btnComboMuteRight || current == btnComboMuteLeft || current == btnComboMuteUp || current == btnComboMuteDown || current == btnComboYoutube190Mute || current == btnComboInputOk) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                showActionPickerDialog(current);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                adjustComboAction(current, -1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                adjustComboAction(current, 1);
                return true;
            }
            return false;
        }

        int delta;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            delta = -1;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            delta = 1;
        } else {
            return false;
        }
        if (current == btnCombosMasterToggle) {
            boolean cur = getOverlayPrefs().getBoolean("btn_combos_enabled", true);
            getOverlayPrefs().edit().putBoolean("btn_combos_enabled", !cur).apply();
            updateButtonCombosPanel();
            return true;
        }

        // 3. Modo Cine
        if (current == btnCineTimerConfig) {
            cycleCineTimer(delta);
            return true;
        }
        if (current == btnCineBlueLightConfig) {
            SharedPreferences cp = context.getSharedPreferences("cine_prefs", Context.MODE_PRIVATE);
            cp.edit().putBoolean("cine_blue_light", !cp.getBoolean("cine_blue_light", true)).apply();
            updateCineConfigPanel();
            return true;
        }
        if (current == btnCineDimmerConfig) {
            SharedPreferences cp = context.getSharedPreferences("cine_prefs", Context.MODE_PRIVATE);
            cp.edit().putBoolean("cine_dimmer", !cp.getBoolean("cine_dimmer", false)).apply();
            updateCineConfigPanel();
            return true;
        }

        // 4. Auto Pause
        if (current == btnAutoPauseMode) {
            cycleAutoPauseMode(delta);
            return true;
        }
        if (current == btnAutoPauseBlackScreen) {
            SharedPreferences op = getOverlayPrefs();
            boolean cur = op.getBoolean("auto_pause_black_screen", false);
            op.edit().putBoolean("auto_pause_black_screen", !cur).apply();
            updateAutoPauseConfigPanel();
            return true;
        }
        if (current == btnAutoDismissUpNext) {
            SharedPreferences op = getOverlayPrefs();
            boolean cur = op.getBoolean(ButtonMappingService.KEY_AUTO_DISMISS_UP_NEXT, true);
            op.edit().putBoolean(ButtonMappingService.KEY_AUTO_DISMISS_UP_NEXT, !cur).apply();
            updateAutoPauseConfigPanel();
            return true;
        }

        // 5. Clock Config
        if (current == btnClockTextColor) {
            cycleClockIntPref("clock_text_color_idx", CLOCK_COLOR_NAMES.length, delta);
            return true;
        }
        if (current == btnClockBgColor) {
            cycleClockIntPref("clock_bg_color_idx", CLOCK_BG_NAMES.length, delta);
            return true;
        }
        if (current == btnClockPosition) {
            cycleClockIntPref("clock_position_idx", CLOCK_POSITION_NAMES.length, delta);
            return true;
        }

        // 6. Cycle Brightness HUD
        if (current == btnBrightnessHudFormat) {
            cycleBrightnessHudIntPref("brightness_hud_format_idx", BRIGHTNESS_HUD_FORMAT_NAMES.length, delta);
            return true;
        }
        if (current == btnBrightnessHudTextColor) {
            cycleBrightnessHudIntPref("brightness_hud_text_color_idx", CLOCK_COLOR_NAMES.length, delta);
            return true;
        }
        if (current == btnBrightnessHudBgColor) {
            cycleBrightnessHudIntPref("brightness_hud_bg_color_idx", CLOCK_BG_NAMES.length, delta);
            return true;
        }
        if (current == btnBrightnessHudPosition) {
            cycleBrightnessHudIntPref("brightness_hud_position_idx", CLOCK_POSITION_NAMES.length, delta);
            return true;
        }

        // Quick Brightness Slider
        if (current == btnSliderOrientation) {
            cycleSliderIntPref("quick_slider_orientation", SLIDER_ORIENTATIONS.length, delta);
            return true;
        }
        if (current == btnSliderStep) {
            cycleSliderIntPref("quick_slider_step_idx", SLIDER_STEPS_LABELS.length, delta);
            return true;
        }
        if (current == btnSliderPerpAction) {
            cycleSliderIntPref("quick_slider_perp_action", SLIDER_PERP_ACTIONS.length, delta);
            return true;
        }
        if (current == btnSliderPosition) {
            int orientation = getOverlayPrefs().getInt("quick_slider_orientation", 0);
            int count = (orientation == 1) ? SLIDER_POSITIONS_V.length : SLIDER_POSITIONS_H.length;
            cycleSliderIntPref("quick_slider_pos_idx", count, delta);
            return true;
        }
        if (current == btnSliderTimeout) {
            cycleSliderIntPref("quick_slider_timeout_idx", SLIDER_TIMEOUTS_LABELS.length, delta);
            return true;
        }

        // 7. Still Watching
        if (current == btnStillWatchingBeepTone) {
            cycleStillWatchingBeepTone(delta);
            return true;
        }
        if (current == btnStillWatchingActionType) {
            cycleStillWatchingAction(delta);
            return true;
        }
        if (current == btnStillWatchingPosition) {
            cycleStillWatchingPosition(delta);
            return true;
        }
        if (current == btnStillWatchingToggle) {
            toggleOverlay(ButtonMappingService.KEY_STILL_WATCHING, "ACTION_TOGGLE_STILL_WATCHING");
            updateStillWatchingConfigPanel();
            buildMenu();
            return true;
        }
        if (current == btnStillWatchingBeepToggle) {
            SharedPreferences sp = getOverlayPrefs();
            boolean cur = sp.getBoolean(ButtonMappingService.KEY_STILL_WATCHING_BEEP, true);
            sp.edit().putBoolean(ButtonMappingService.KEY_STILL_WATCHING_BEEP, !cur).apply();
            sendServiceAction("ACTION_UPDATE_STILL_WATCHING");
            updateStillWatchingConfigPanel();
            return true;
        }

        // 8. OLED Saver Mode
        if (current == btnOledMode) {
            cycleOledMode(delta);
            return true;
        }
        if (current == btnOledSaverToggle) {
            toggleOverlay(ButtonMappingService.KEY_OLED_SAVER, "ACTION_TOGGLE_OLED_SAVER");
            updateOledSaverConfigPanel();
            buildMenu();
            return true;
        }

        // 9. Mindful Delay
        if (current == btnMindfulDelayCancelAction) {
            cycleMindfulCancelAction(delta);
            return true;
        }
        if (current == btnMindfulDelaySession) {
            cycleMindfulSession(delta);
            return true;
        }
        if (current == btnMindfulDelayPos) {
            cycleMindfulPos(delta);
            return true;
        }
        if (current == btnMindfulDelayMsg) {
            cycleMindfulMsg(delta);
            return true;
        }
        if (current == btnMindfulDelayToggle) {
            toggleOverlay(ButtonMappingService.KEY_MINDFUL_DELAY, "ACTION_TOGGLE_MINDFUL_DELAY");
            updateMindfulDelayConfigPanel();
            buildMenu();
            return true;
        }

        // 10. Translate Config
        if (current == btnTranslateTargetLang) {
            cycleTranslateTargetLang(delta);
            return true;
        }
        if (current == btnTranslateSourceLang) {
            cycleTranslateSourceLang(delta);
            return true;
        }
        if (current == btnTranslateAutoPause) {
            SharedPreferences op = getOverlayPrefs();
            boolean cur = op.getBoolean("translate_auto_pause", true);
            op.edit().putBoolean("translate_auto_pause", !cur).apply();
            updateTranslateConfigPanel();
            return true;
        }
        if (current == btnTranslateAutoResume) {
            SharedPreferences op = getOverlayPrefs();
            boolean cur = op.getBoolean("translate_auto_resume", true);
            op.edit().putBoolean("translate_auto_resume", !cur).apply();
            updateTranslateConfigPanel();
            return true;
        }
        if (current == btnTranslateTopBar) {
            SharedPreferences op = getOverlayPrefs();
            boolean cur = op.getBoolean("translate_show_top_bar", false);
            op.edit().putBoolean("translate_show_top_bar", !cur).apply();
            updateTranslateConfigPanel();
            return true;
        }

        // 11. Scheduled Sleep
        if (current == txtScheduledAlarmSelector) {
            cycleSelectedScheduledAlarm(delta);
            return true;
        }
        if (current == btnScheduledPromptToggle) {
            cycleScheduledPromptSec(delta);
            return true;
        }
        if (current == btnScheduledSleepToggle) {
            toggleSelectedScheduledAlarm();
            return true;
        }

        // 12. Night Schedule
        if (current == btnNightScheduleToggle) {
            toggleOverlay(ButtonMappingService.KEY_NIGHT_SCHEDULE, "ACTION_TOGGLE_NIGHT_SCHEDULE");
            updateNightScheduleConfigPanel();
            buildMenu();
            return true;
        }

        // 13. VOT Controls
        if (current == btnVotMasterToggle) {
            toggleVotMaster();
            return true;
        }
        if (current == btnVotSourceLang) {
            cycleVotSourceLang(delta);
            return true;
        }
        if (current == btnVotTargetLang) {
            cycleVotTargetLang(delta);
            return true;
        }
        if (current == btnVotTtsVolume) {
            cycleVotTtsVolume(delta);
            return true;
        }
        if (current == btnVotDuckingMode) {
            cycleVotDuckingMode(delta);
            return true;
        }
        if (current == btnVotSubtitlesMode) {
            cycleVotSubtitlesMode(delta);
            return true;
        }
        if (current == btnVotSpeechRate) {
            cycleVotSpeechRate(delta);
            return true;
        }
        if (current == btnVotProvider) {
            cycleVotProvider(delta);
            return true;
        }
        if (current == btnVotOpenrouterModel) {
            cycleVotOpenrouterModel(delta);
            return true;
        }
        if (current == btnVotOpenrouterKey) {
            handleVotOpenrouterKeyAction();
            return true;
        }
        if (current == btnAiProviderToggle) {
            cycleAiProvider(delta);
            return true;
        }
        if (current == btnAiModelToggle) {
            cycleAiModel(delta);
            return true;
        }
        if (current == btnAiUiLangToggle) {
            cycleAiUiLang(delta);
            return true;
        }
        if (current == btnAiApiKeyConfig) {
            handleAiApiKeyAction();
            return true;
        }

        return false;
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
                    updateMenuInternalFilters();
                    if (txtBlueLightPct != null) {
                        double displayPct = progress / 10.0;
                        txtBlueLightPct.setText((I18n.isSpanish(context) ? "Nivel: " : "Level: ") + (progress > 0 ? String.format(java.util.Locale.US, "%.1f%%", displayPct) : (I18n.isSpanish(context) ? "Desactivado" : "Disabled")));
                    }
                    if (btnApplyBlueLight != null) {
                        btnApplyBlueLight.setText(progress > 0 ? (I18n.isSpanish(context) ? "[ Desactivar Filtro ]" : "[ Disable Filter ]") : (I18n.isSpanish(context) ? "[ Activar Filtro ]" : "[ Enable Filter ]"));
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
            if (txtBrightnessPct != null) txtBrightnessPct.setText((I18n.isSpanish(context) ? "Nivel: " : "Level: ") + currentPct + "%");

            sliderBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (txtBrightnessPct != null) txtBrightnessPct.setText((I18n.isSpanish(context) ? "Nivel: " : "Level: ") + progress + "%");
                    Bundle b = new Bundle();
                    b.putInt("pct", progress);
                    sendServiceAction("ACTION_SET_DIMMER_BRIGHTNESS", b);
                    getOverlayPrefs().edit()
                            .putInt("dimmer_brightness_pct", progress)
                            .putFloat("dimmer_brightness_pct_float", (float) progress)
                            .apply();
                    updateMenuInternalFilters();
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
                    cycleCineTimer(1);
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
                    showActionPickerDialog(btnConfigClick1);
                }
            });
        }
        if (btnConfigClick2 != null) {
            btnConfigClick2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showActionPickerDialog(btnConfigClick2);
                }
            });
        }
        if (btnConfigClick3 != null) {
            btnConfigClick3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showActionPickerDialog(btnConfigClick3);
                }
            });
        }
        if (btnConfigClick4 != null) {
            btnConfigClick4.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showActionPickerDialog(btnConfigClick4);
                }
            });
        }
        if (btnConfigLong != null) {
            btnConfigLong.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showActionPickerDialog(btnConfigLong);
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

        // Shortcut from Dimmer panel to HUD config
        if (btnGotoHudConfig != null) {
            btnGotoHudConfig.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeSubPanels();
                    buildMenu();
                    if (panelCycleBrightness != null) panelCycleBrightness.setVisibility(View.VISIBLE);
                    openSubPanel = "cycle_brightness_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateBrightnessHudConfigPanel();
                    if (btnCycleBrightnessNow != null) btnCycleBrightnessNow.requestFocus();
                }
            });
        }

        // Shortcut from Dimmer panel to Slider config
        if (btnGotoSliderConfig != null) {
            btnGotoSliderConfig.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeSubPanels();
                    buildMenu();
                    if (panelQuickBrightnessSlider != null) panelQuickBrightnessSlider.setVisibility(View.VISIBLE);
                    openSubPanel = "quick_slider_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateQuickSliderConfigPanel();
                    if (btnSliderTestNow != null) btnSliderTestNow.requestFocus();
                }
            });
        }

        // Auto-Reset Diario al Encender (Dimmer Paso 1 & Grayscale a Color)
        if (btnDimmerDayAutoReset != null) {
            btnDimmerDayAutoReset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean("dimmer_day_auto_reset_enabled", true);
                    getOverlayPrefs().edit().putBoolean("dimmer_day_auto_reset_enabled", !cur).apply();
                    updateBrightnessConfigPanel();
                    btnDimmerDayAutoReset.requestFocus();
                }
            });
        }
        if (btnGrayscaleDayAutoReset != null) {
            btnGrayscaleDayAutoReset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean("grayscale_day_auto_reset_enabled", true);
                    getOverlayPrefs().edit().putBoolean("grayscale_day_auto_reset_enabled", !cur).apply();
                    updateBrightnessConfigPanel();
                    btnGrayscaleDayAutoReset.requestFocus();
                }
            });
        }
        setupAutoRepeatStepButton(btnDayResetStartDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref("dimmer_day_reset_start_hour", 8, step, 0, 23, null); updateBrightnessConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnDayResetStartInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref("dimmer_day_reset_start_hour", 8, step, 0, 23, null); updateBrightnessConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnDayResetEndDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref("dimmer_day_reset_end_hour", 19, step, 0, 23, null); updateBrightnessConfigPanel(); }
        });
        setupAutoRepeatStepButton(btnDayResetEndInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustIntPref("dimmer_day_reset_end_hour", 19, step, 0, 23, null); updateBrightnessConfigPanel(); }
        });

        // Cycle Brightness & OSD HUD listeners
        if (btnCycleBrightnessNow != null) {
            btnCycleBrightnessNow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleCycleBrightness();
                    btnCycleBrightnessNow.requestFocus();
                }
            });
        }
        if (btnBrightnessHudFormat != null) {
            btnBrightnessHudFormat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleBrightnessHudIntPref("brightness_hud_format_idx", BRIGHTNESS_HUD_FORMAT_NAMES.length);
                }
            });
        }
        if (btnBrightnessHudTextColor != null) {
            btnBrightnessHudTextColor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleBrightnessHudIntPref("brightness_hud_text_color_idx", CLOCK_COLOR_NAMES.length);
                }
            });
        }
        if (btnBrightnessHudBgColor != null) {
            btnBrightnessHudBgColor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleBrightnessHudIntPref("brightness_hud_bg_color_idx", CLOCK_BG_NAMES.length);
                }
            });
        }
        if (btnBrightnessHudPosition != null) {
            btnBrightnessHudPosition.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleBrightnessHudIntPref("brightness_hud_position_idx", CLOCK_POSITION_NAMES.length);
                }
            });
        }

        setupAutoRepeatStepButton(btnBrightnessHudAlphaDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_bg_alpha_pct", 35, step * 5, 0, 100); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudAlphaInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_bg_alpha_pct", 35, step * 5, 0, 100); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudTextAlphaDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_text_alpha_pct", 100, step * 5, 0, 100); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudTextAlphaInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_text_alpha_pct", 100, step * 5, 0, 100); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudSizeDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_size_sp", 16, step * 2, 10, 40); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudSizeInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_size_sp", 16, step * 2, 10, 40); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudPadDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_padding_dp", 12, step * 2, 0, 50); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudPadInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_padding_dp", 12, step * 2, 0, 50); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudXDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_pos_x_dp", 16, step * 2, 0, 500); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudXInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_pos_x_dp", 16, step * 2, 0, 500); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudYDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_pos_y_dp", 16, step * 2, 0, 500); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudYInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_pos_y_dp", 16, step * 2, 0, 500); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudDurDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_duration_ms", 2000, step * 250, 500, 10000); }
        });
        setupAutoRepeatStepButton(btnBrightnessHudDurInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustBrightnessHudIntPref("brightness_hud_duration_ms", 2000, step * 250, 500, 10000); }
        });

        if (btnTestBrightnessHud != null) {
            btnTestBrightnessHud.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendServiceAction("ACTION_SHOW_BRIGHTNESS_HUD");
                    btnTestBrightnessHud.requestFocus();
                }
            });
        }

        if (btnGotoSliderFromCycle != null) {
            btnGotoSliderFromCycle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeSubPanels();
                    buildMenu();
                    if (panelQuickBrightnessSlider != null) panelQuickBrightnessSlider.setVisibility(View.VISIBLE);
                    openSubPanel = "quick_slider_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateQuickSliderConfigPanel();
                    if (btnSliderTestNow != null) btnSliderTestNow.requestFocus();
                }
            });
        }

        // Quick Brightness Slider config listeners
        if (btnSliderTestNow != null) {
            btnSliderTestNow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                    sendServiceAction("ACTION_SHOW_BRIGHTNESS_SLIDER");
                }
            });
        }
        if (btnSliderOrientation != null) {
            btnSliderOrientation.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleSliderIntPref("quick_slider_orientation", SLIDER_ORIENTATIONS.length);
                }
            });
        }
        if (btnSliderStep != null) {
            btnSliderStep.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleSliderIntPref("quick_slider_step_idx", SLIDER_STEPS_LABELS.length);
                }
            });
        }
        if (btnSliderPerpAction != null) {
            btnSliderPerpAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleSliderIntPref("quick_slider_perp_action", SLIDER_PERP_ACTIONS.length);
                }
            });
        }
        if (btnSliderPosition != null) {
            btnSliderPosition.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int orientation = getOverlayPrefs().getInt("quick_slider_orientation", 0);
                    int count = (orientation == 1) ? SLIDER_POSITIONS_V.length : SLIDER_POSITIONS_H.length;
                    cycleSliderIntPref("quick_slider_pos_idx", count);
                }
            });
        }
        if (btnSliderTimeout != null) {
            btnSliderTimeout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleSliderIntPref("quick_slider_timeout_idx", SLIDER_TIMEOUTS_LABELS.length);
                }
            });
        }

        // Auto-pause listeners
        if (btnAutoPauseMode != null) {
            btnAutoPauseMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleAutoPauseMode(1);
                }
            });
        }
        if (btnAutoPauseCountDec != null) {
            btnAutoPauseCountDec.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences op = getOverlayPrefs();
                    int cur = op.getInt("auto_pause_playlist_count", 2);
                    if (cur > 1) {
                        int next = cur - 1;
                        op.edit().putInt("auto_pause_playlist_count", next).apply();
                        updateAutoPauseConfigPanel();
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
                    int cur = op.getInt("auto_pause_playlist_count", 2);
                    if (cur < 50) {
                        int next = cur + 1;
                        op.edit().putInt("auto_pause_playlist_count", next).apply();
                        updateAutoPauseConfigPanel();
                    }
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
        if (btnAutoDismissUpNext != null) {
            btnAutoDismissUpNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences op = getOverlayPrefs();
                    boolean cur = op.getBoolean(ButtonMappingService.KEY_AUTO_DISMISS_UP_NEXT, true);
                    op.edit().putBoolean(ButtonMappingService.KEY_AUTO_DISMISS_UP_NEXT, !cur).apply();
                    updateAutoPauseConfigPanel();
                    btnAutoDismissUpNext.requestFocus();
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

        if (btnStillWatchingBeepToggle != null) {
            btnStillWatchingBeepToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean(ButtonMappingService.KEY_STILL_WATCHING_BEEP, true);
                    getOverlayPrefs().edit().putBoolean(ButtonMappingService.KEY_STILL_WATCHING_BEEP, !cur).apply();
                    sendServiceAction("ACTION_UPDATE_STILL_WATCHING");
                    updateStillWatchingConfigPanel();
                    btnStillWatchingBeepToggle.requestFocus();
                }
            });
        }
        setupAutoRepeatStepButton(btnStillWatchingBeepIntervalDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_BEEP_INTERVAL, 10, step, 1, 60); }
        });
        setupAutoRepeatStepButton(btnStillWatchingBeepIntervalInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_BEEP_INTERVAL, 10, step, 1, 60); }
        });
        setupAutoRepeatStepButton(btnStillWatchingBeepDelayDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_BEEP_DELAY, 9, step, 0, 60); }
        });
        setupAutoRepeatStepButton(btnStillWatchingBeepDelayInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_BEEP_DELAY, 9, step, 0, 60); }
        });
        setupAutoRepeatStepButton(btnStillWatchingBeepVolDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_BEEP_VOLUME, 65, step, 1, 100); }
        });
        setupAutoRepeatStepButton(btnStillWatchingBeepVolInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) { adjustStillWatchingIntPref(ButtonMappingService.KEY_STILL_WATCHING_BEEP_VOLUME, 65, step, 1, 100); }
        });
        if (btnStillWatchingBeepTone != null) {
            btnStillWatchingBeepTone.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { cycleStillWatchingBeepTone(1); }
            });
        }
        if (btnTestStillWatchingBeep != null) {
            btnTestStillWatchingBeep.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    playStillWatchingBeepSound();
                    btnTestStillWatchingBeep.requestFocus();
                }
            });
        }

        if (btnStillWatchingActionType != null) {
            btnStillWatchingActionType.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { cycleStillWatchingAction(1); }
            });
        }
        if (btnStillWatchingPosition != null) {
            btnStillWatchingPosition.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { cycleStillWatchingPosition(1); }
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
                    cycleOledMode(1);
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
        if (btnScheduledAlarmPrev != null) {
            btnScheduledAlarmPrev.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleSelectedScheduledAlarm(-1);
                }
            });
        }
        if (txtScheduledAlarmSelector != null) {
            txtScheduledAlarmSelector.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleSelectedScheduledAlarm(1);
                }
            });
        }
        if (btnScheduledAlarmNext != null) {
            btnScheduledAlarmNext.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleSelectedScheduledAlarm(1);
                }
            });
        }
        if (btnAddScheduledAlarm != null) {
            btnAddScheduledAlarm.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    addScheduledAlarm();
                }
            });
        }
        if (btnDeleteScheduledAlarm != null) {
            btnDeleteScheduledAlarm.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    deleteScheduledAlarm();
                }
            });
        }
        if (btnScheduledSleepToggle != null) {
            btnScheduledSleepToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleSelectedScheduledAlarm();
                }
            });
        }
        setupAutoRepeatStepButton(btnScheduledHourDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustSelectedScheduledAlarmHour(step);
            }
        });
        setupAutoRepeatStepButton(btnScheduledHourInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustSelectedScheduledAlarmHour(step);
            }
        });
        setupAutoRepeatStepButton(btnScheduledMinDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustSelectedScheduledAlarmMinute(step);
            }
        });
        setupAutoRepeatStepButton(btnScheduledMinInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustSelectedScheduledAlarmMinute(step);
            }
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
                toggleSelectedScheduledAlarmDay(day);
            }
        };
        TextView[] dayArr = {btnDay1, btnDay2, btnDay3, btnDay4, btnDay5, btnDay6, btnDay7};
        for (TextView dBtn : dayArr) {
            if (dBtn != null) {
                dBtn.setOnClickListener(dayListener);
                dBtn.setOnFocusChangeListener(dayFocusListener);
            }
        }

        if (btnScheduledPromptToggle != null) {
            btnScheduledPromptToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleScheduledPromptSec(1);
                }
            });
        }
        if (btnScheduledSkipNext != null) {
            btnScheduledSkipNext.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleSelectedScheduledAlarmSkip();
                }
            });
        }
        if (btnApplyScheduledSleep != null) {
            btnApplyScheduledSleep.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleSelectedScheduledAlarm();
                }
            });
        }

        // Mindful Delay (Espera Consciente) Listeners
        if (btnMindfulDelayToggle != null) {
            btnMindfulDelayToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean(ButtonMappingService.KEY_MINDFUL_DELAY, false);
                    getOverlayPrefs().edit().putBoolean(ButtonMappingService.KEY_MINDFUL_DELAY, !cur).apply();
                    sendServiceAction("ACTION_UPDATE_MINDFUL_DELAY");
                    updateMindfulDelayConfigPanel();
                    buildMenu();
                }
            });
        }
        setupAutoRepeatStepButton(btnMindfulDelayMinDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustMindfulMinutes(step);
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayMinInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustMindfulMinutes(step);
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelaySecDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustMindfulSeconds(step);
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelaySecInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustMindfulSeconds(step);
            }
        });
        if (btnMindfulDelayCancelAction != null) {
            btnMindfulDelayCancelAction.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleMindfulCancelAction(1);
                }
            });
        }
        if (btnMindfulDelaySession != null) {
            btnMindfulDelaySession.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleMindfulSession(1);
                }
            });
        }
        setupAutoRepeatStepButton(btnMindfulDelaySessHourDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("mindful_delay_session_hours", 0, step, 0, 12, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelaySessHourInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("mindful_delay_session_hours", 0, step, 0, 12, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelaySessMinDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                int delta = (step < 0 ? -5 : 5) * Math.abs(step);
                adjustIntPref("mindful_delay_session_mins", 30, delta, 1, 59, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelaySessMinInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                int delta = (step < 0 ? -5 : 5) * Math.abs(step);
                adjustIntPref("mindful_delay_session_mins", 30, delta, 1, 59, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        if (btnMindfulDelayPos != null) {
            btnMindfulDelayPos.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleMindfulPos(1);
                }
            });
        }
        if (btnMindfulDelayMsg != null) {
            btnMindfulDelayMsg.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleMindfulMsg(1);
                }
            });
        }
        setupAutoRepeatStepButton(btnMindfulDelayBgAlphaDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                int delta = (step < 0 ? -5 : 5) * Math.abs(step);
                adjustIntPref("mindful_delay_bg_alpha_pct", 90, delta, 0, 100, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayBgAlphaInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                int delta = (step < 0 ? -5 : 5) * Math.abs(step);
                adjustIntPref("mindful_delay_bg_alpha_pct", 90, delta, 0, 100, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayTextSizeDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("mindful_delay_text_size_sp", 16, step, 12, 28, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayTextSizeInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("mindful_delay_text_size_sp", 16, step, 12, 28, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayPadDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("mindful_delay_pad_dp", 16, step, 8, 32, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayPadInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("mindful_delay_pad_dp", 16, step, 8, 32, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayXDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                int delta = (step < 0 ? -5 : 5) * Math.abs(step);
                adjustIntPref("mindful_delay_pos_x_dp", 0, delta, 0, 300, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayXInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                int delta = (step < 0 ? -5 : 5) * Math.abs(step);
                adjustIntPref("mindful_delay_pos_x_dp", 0, delta, 0, 300, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayYDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                int delta = (step < 0 ? -5 : 5) * Math.abs(step);
                adjustIntPref("mindful_delay_pos_y_dp", 0, delta, 0, 300, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnMindfulDelayYInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                int delta = (step < 0 ? -5 : 5) * Math.abs(step);
                adjustIntPref("mindful_delay_pos_y_dp", 0, delta, 0, 300, "ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });

        // App toggle listeners
        setupAppToggleListener(btnMindfulAppYoutube, "mindful_app_youtube", true);
        setupAppToggleListener(btnMindfulAppNetflix, "mindful_app_netflix", true);
        setupAppToggleListener(btnMindfulAppDisney, "mindful_app_disney", true);
        setupAppToggleListener(btnMindfulAppPrime, "mindful_app_prime", false);
        setupAppToggleListener(btnMindfulAppMax, "mindful_app_max", false);
        setupAppToggleListener(btnMindfulAppStar, "mindful_app_star", false);
        setupAppToggleListener(btnMindfulAppTwitch, "mindful_app_twitch", false);
        setupAppToggleListener(btnMindfulAppTiktok, "mindful_app_tiktok", false);
        setupAppToggleListener(btnMindfulAppSmarttube, "mindful_app_smarttube", false);
        setupAppToggleListener(btnMindfulAppStremio, "mindful_app_stremio", false);
        setupAppToggleListener(btnMindfulAppPlex, "mindful_app_plex", false);

        if (btnTestMindfulDelay != null) {
            btnTestMindfulDelay.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    sendServiceAction("ACTION_TEST_MINDFUL_DELAY");
                }
            });
        }
        if (btnApplyMindfulDelay != null) {
            btnApplyMindfulDelay.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    closeSubPanels();
                    buildMenu();
                }
            });
        }

        // Translate Panel Listeners
        if (btnTranslateTargetLang != null) {
            btnTranslateTargetLang.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleTranslateTargetLang(1);
                }
            });
        }
        if (btnTranslateSourceLang != null) {
            btnTranslateSourceLang.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleTranslateSourceLang(1);
                }
            });
        }
        if (btnTranslateAutoPause != null) {
            btnTranslateAutoPause.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean("translate_auto_pause", true);
                    getOverlayPrefs().edit().putBoolean("translate_auto_pause", !cur).apply();
                    updateTranslateConfigPanel();
                }
            });
        }
        if (btnTranslateAutoResume != null) {
            btnTranslateAutoResume.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean("translate_auto_resume", true);
                    getOverlayPrefs().edit().putBoolean("translate_auto_resume", !cur).apply();
                    updateTranslateConfigPanel();
                }
            });
        }
        if (btnTranslateTopBar != null) {
            btnTranslateTopBar.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean("translate_show_top_bar", false);
                    getOverlayPrefs().edit().putBoolean("translate_show_top_bar", !cur).apply();
                    updateTranslateConfigPanel();
                }
            });
        }
        setupAutoRepeatStepButton(btnTranslateBgAlphaDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("translate_bg_alpha_pct", 85, step * 5, 10, 100, null);
                updateTranslateConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnTranslateBgAlphaInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("translate_bg_alpha_pct", 85, step * 5, 10, 100, null);
                updateTranslateConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnTranslateTextSizeDec, -1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("translate_text_size_sp", 14, step, 9, 28, null);
                updateTranslateConfigPanel();
            }
        });
        setupAutoRepeatStepButton(btnTranslateTextSizeInc, 1, new StepAdjuster() {
            @Override public void adjust(int step) {
                adjustIntPref("translate_text_size_sp", 14, step, 9, 28, null);
                updateTranslateConfigPanel();
            }
        });
        if (btnTestTranslate != null) {
            btnTestTranslate.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dismiss();
                    sendServiceAction("ACTION_TRANSLATE_SCREEN");
                }
            });
        }
        if (btnApplyTranslate != null) {
            btnApplyTranslate.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    closeSubPanels();
                    buildMenu();
                }
            });
        }

        // Button Combos Listeners
        if (btnCombosMasterToggle != null) {
            btnCombosMasterToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean cur = getOverlayPrefs().getBoolean("btn_combos_enabled", true);
                    getOverlayPrefs().edit().putBoolean("btn_combos_enabled", !cur).apply();
                    updateButtonCombosPanel();
                }
            });
        }
        if (btnComboMuteOk != null) {
            btnComboMuteOk.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showActionPickerDialog(btnComboMuteOk);
                }
            });
        }
        if (btnComboMuteRight != null) {
            btnComboMuteRight.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showActionPickerDialog(btnComboMuteRight);
                }
            });
        }
        if (btnComboMuteLeft != null) {
            btnComboMuteLeft.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showActionPickerDialog(btnComboMuteLeft);
                }
            });
        }
        if (btnComboMuteUp != null) {
            btnComboMuteUp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showActionPickerDialog(btnComboMuteUp);
                }
            });
        }
        if (btnComboMuteDown != null) {
            btnComboMuteDown.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showActionPickerDialog(btnComboMuteDown);
                }
            });
        }
        if (btnComboYoutube190Mute != null) {
            btnComboYoutube190Mute.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showActionPickerDialog(btnComboYoutube190Mute);
                }
            });
        }
        if (btnComboInputOk != null) {
            btnComboInputOk.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showActionPickerDialog(btnComboInputOk);
                }
            });
        }
        if (btnApplyCombos != null) {
            btnApplyCombos.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    closeSubPanels();
                    buildMenu();
                }
            });
        }

        // VOT Listeners
        if (btnVotMasterToggle != null) {
            btnVotMasterToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleVotMaster();
                }
            });
        }
        if (btnVotSourceLang != null) {
            btnVotSourceLang.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleVotSourceLang(1);
                }
            });
        }
        if (btnVotTargetLang != null) {
            btnVotTargetLang.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleVotTargetLang(1);
                }
            });
        }
        if (btnVotTtsVolume != null) {
            btnVotTtsVolume.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleVotTtsVolume(1);
                }
            });
        }
        if (btnVotDuckingMode != null) {
            btnVotDuckingMode.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleVotDuckingMode(1);
                }
            });
        }
        if (btnVotSubtitlesMode != null) {
            btnVotSubtitlesMode.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleVotSubtitlesMode(1);
                }
            });
        }
        if (btnVotSpeechRate != null) {
            btnVotSpeechRate.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleVotSpeechRate(1);
                }
            });
        }
        if (btnVotProvider != null) {
            btnVotProvider.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleVotProvider(1);
                }
            });
        }
        if (btnVotOpenrouterModel != null) {
            btnVotOpenrouterModel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleVotOpenrouterModel(1);
                }
            });
        }
        if (btnVotOpenrouterKey != null) {
            btnVotOpenrouterKey.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    handleVotOpenrouterKeyAction();
                }
            });
        }
        if (btnVotTestVoice != null) {
            btnVotTestVoice.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    com.nitsutech.omnitv.vot.VotManager.getInstance(context).testVoiceAndDucking();
                }
            });
        }
        if (btnVotApply != null) {
            btnVotApply.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    closeSubPanels();
                    buildMenu();
                }
            });
        }

        // AI Summary panel listeners
        if (btnAiLaunchNow != null) {
            btnAiLaunchNow.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dismiss();
                    com.nitsutech.omnitv.ai.AiSummaryOverlay.getInstance().show(context);
                }
            });
        }
        if (btnAiProviderToggle != null) {
            btnAiProviderToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleAiProvider(1);
                }
            });
        }
        if (btnAiModelToggle != null) {
            btnAiModelToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleAiModel(1);
                }
            });
        }
        if (btnAiUiLangToggle != null) {
            btnAiUiLangToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cycleAiUiLang(1);
                }
            });
        }
        if (btnAiApiKeyConfig != null) {
            btnAiApiKeyConfig.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    handleAiApiKeyAction();
                }
            });
        }
        if (btnAiApiHelp != null) {
            btnAiApiHelp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    android.widget.Toast.makeText(context, I18n.get(context, R.string.ai_tip_toast), android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }
        if (btnAiApply != null) {
            btnAiApply.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    closeSubPanels();
                    buildMenu();
                }
            });
        }
    }

    private void toggleVotMaster() {
        SharedPreferences op = getOverlayPrefs();
        boolean cur = op.getBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_ENABLED, false);
        boolean next = !cur;
        com.nitsutech.omnitv.vot.VotManager.getInstance(context).setEnabled(next);
        updateVotConfigPanel();
        buildMenu();
    }

    private void cycleVotSourceLang(int delta) {
        SharedPreferences op = getOverlayPrefs();
        String cur = op.getString(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SOURCE_LANG, "auto");
        int idx = 0;
        for (int i = 0; i < VOT_SOURCE_LANGS.length; i++) {
            if (VOT_SOURCE_LANGS[i].equalsIgnoreCase(cur)) {
                idx = i;
                break;
            }
        }
        int next = (idx + delta + VOT_SOURCE_LANGS.length) % VOT_SOURCE_LANGS.length;
        op.edit().putString(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SOURCE_LANG, VOT_SOURCE_LANGS[next]).apply();
        com.nitsutech.omnitv.vot.VotManager.getInstance(context).loadPreferences();
        updateVotConfigPanel();
    }

    private void cycleVotSubtitlesMode(int delta) {
        SharedPreferences op = getOverlayPrefs();
        boolean showSubs = op.getBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SHOW_SUBTITLES, true);
        boolean bilingual = op.getBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_BILINGUAL, false);

        int state = 0;
        if (showSubs && bilingual) state = 2;
        else if (showSubs) state = 1;

        int next = (state + delta + 3) % 3;
        if (next == 0) {
            op.edit().putBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SHOW_SUBTITLES, false)
                     .putBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_BILINGUAL, false).apply();
        } else if (next == 1) {
            op.edit().putBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SHOW_SUBTITLES, true)
                     .putBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_BILINGUAL, false).apply();
        } else {
            op.edit().putBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SHOW_SUBTITLES, true)
                     .putBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_BILINGUAL, true).apply();
        }
        com.nitsutech.omnitv.vot.VotManager.getInstance(context).loadPreferences();
        updateVotConfigPanel();
    }

    private void cycleVotTargetLang(int delta) {
        SharedPreferences op = getOverlayPrefs();
        String cur = op.getString(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_TARGET_LANG, "es");
        int idx = 0;
        for (int i = 0; i < VOT_TARGET_LANGS.length; i++) {
            if (VOT_TARGET_LANGS[i].equalsIgnoreCase(cur)) {
                idx = i;
                break;
            }
        }
        int next = (idx + delta + VOT_TARGET_LANGS.length) % VOT_TARGET_LANGS.length;
        com.nitsutech.omnitv.vot.VotManager.getInstance(context).setTargetLanguage(VOT_TARGET_LANGS[next]);
        updateVotConfigPanel();
    }

    private void cycleVotTtsVolume(int delta) {
        SharedPreferences op = getOverlayPrefs();
        float cur = op.getFloat(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_TTS_VOLUME, 1.0f);
        int idx = 0;
        for (int i = 0; i < VOT_TTS_VOLUMES.length; i++) {
            if (Math.abs(VOT_TTS_VOLUMES[i] - cur) < 0.05f) {
                idx = i;
                break;
            }
        }
        int next = (idx + delta + VOT_TTS_VOLUMES.length) % VOT_TTS_VOLUMES.length;
        com.nitsutech.omnitv.vot.VotManager.getInstance(context).setTtsVolume(VOT_TTS_VOLUMES[next]);
        updateVotConfigPanel();
    }

    private void cycleVotDuckingMode(int delta) {
        SharedPreferences op = getOverlayPrefs();
        int cur = op.getInt(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_DUCKING_MODE, 0);
        int next = (cur + delta + VOT_DUCKING_MODES.length) % VOT_DUCKING_MODES.length;
        com.nitsutech.omnitv.vot.VotManager.getInstance(context).setDuckingMode(next);
        updateVotConfigPanel();
    }

    private void cycleVotSpeechRate(int delta) {
        SharedPreferences op = getOverlayPrefs();
        float cur = op.getFloat(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SPEECH_RATE, 1.15f);
        int idx = 1;
        for (int i = 0; i < VOT_SPEECH_RATES.length; i++) {
            if (Math.abs(VOT_SPEECH_RATES[i] - cur) < 0.05f) {
                idx = i;
                break;
            }
        }
        int next = (idx + delta + VOT_SPEECH_RATES.length) % VOT_SPEECH_RATES.length;
        op.edit().putFloat(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SPEECH_RATE, VOT_SPEECH_RATES[next]).apply();
        com.nitsutech.omnitv.vot.VotManager.getInstance(context).loadPreferences();
        updateVotConfigPanel();
    }

    private void cycleVotProvider(int delta) {
        SharedPreferences op = getOverlayPrefs();
        int cur = op.getInt(com.nitsutech.omnitv.vot.VotTranslationEngine.KEY_TRANSLATION_PROVIDER, 0);
        int next = (cur + delta + VOT_PROVIDER_NAMES.length) % VOT_PROVIDER_NAMES.length;
        op.edit().putInt(com.nitsutech.omnitv.vot.VotTranslationEngine.KEY_TRANSLATION_PROVIDER, next).apply();
        updateVotConfigPanel();
    }

    private void cycleVotOpenrouterModel(int delta) {
        SharedPreferences op = getOverlayPrefs();
        String cur = op.getString(com.nitsutech.omnitv.vot.VotTranslationEngine.KEY_OPENROUTER_MODEL, VOT_OPENROUTER_MODELS[0]);
        int idx = 0;
        for (int i = 0; i < VOT_OPENROUTER_MODELS.length; i++) {
            if (VOT_OPENROUTER_MODELS[i].equalsIgnoreCase(cur)) {
                idx = i;
                break;
            }
        }
        int next = (idx + delta + VOT_OPENROUTER_MODELS.length) % VOT_OPENROUTER_MODELS.length;
        op.edit().putString(com.nitsutech.omnitv.vot.VotTranslationEngine.KEY_OPENROUTER_MODEL, VOT_OPENROUTER_MODELS[next]).apply();
        updateVotConfigPanel();
    }

    private void handleVotOpenrouterKeyAction() {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence clipText = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (clipText != null && clipText.length() > 5) {
                    String key = clipText.toString().trim();
                    getOverlayPrefs().edit().putString(com.nitsutech.omnitv.vot.VotTranslationEngine.KEY_OPENROUTER_KEY, key).apply();
                    String masked = key.length() > 8 ? (key.substring(0, 4) + "..." + key.substring(key.length() - 4)) : "***";
                    android.widget.Toast.makeText(context, "🔑 Key pegada del portapapeles (" + masked + ")", android.widget.Toast.LENGTH_LONG).show();
                    updateVotConfigPanel();
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading clipboard", e);
        }
        String curKey = getOverlayPrefs().getString(com.nitsutech.omnitv.vot.VotTranslationEngine.KEY_OPENROUTER_KEY, "");
        if (!curKey.isEmpty()) {
            android.widget.Toast.makeText(context, "🔑 Key activa. Para cambiarla copia la nueva en el móvil y presiona OK, o usa ADB: adb shell am broadcast -a com.nitsutech.omnitv.SET_OPENROUTER_KEY --es key 'tu-key'", android.widget.Toast.LENGTH_LONG).show();
        } else {
            android.widget.Toast.makeText(context, "💡 Copia la API Key en el móvil (Google TV Remote) y presiona OK para pegar, o vía ADB: adb shell am broadcast -a com.nitsutech.omnitv.SET_OPENROUTER_KEY --es key 'tu-api-key'", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void updateVotConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        boolean enabled = op.getBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_ENABLED, false);

        if (btnVotMasterToggle != null) {
            btnVotMasterToggle.setText("   " + I18n.get(context, R.string.action_vot) + ":  [" + (enabled ? I18n.get(context, R.string.status_on) : I18n.get(context, R.string.status_off)) + "]");
            btnVotMasterToggle.setTextColor(enabled ? 0xFF4CAF50 : 0xFFFF6B6B);
        }

        String curLang = op.getString(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SOURCE_LANG, "auto");
        String[] sourceLangNames = I18n.getStringArray(context, R.array.vot_source_lang_names);
        String langName = sourceLangNames.length > 0 ? sourceLangNames[0] : "Auto";
        for (int i = 0; i < VOT_SOURCE_LANGS.length; i++) {
            if (VOT_SOURCE_LANGS[i].equalsIgnoreCase(curLang)) {
                if (i < sourceLangNames.length) langName = sourceLangNames[i];
                break;
            }
        }
        if (btnVotSourceLang != null) {
            btnVotSourceLang.setText(I18n.get(context, R.string.vot_source_lang, langName) + "  (◄ / ►)");
        }

        String curTargetLang = op.getString(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_TARGET_LANG, "es");
        String[] targetLangNames = I18n.getStringArray(context, R.array.vot_target_lang_names);
        String targetLangName = targetLangNames.length > 0 ? targetLangNames[0] : "Spanish";
        for (int i = 0; i < VOT_TARGET_LANGS.length; i++) {
            if (VOT_TARGET_LANGS[i].equalsIgnoreCase(curTargetLang)) {
                if (i < targetLangNames.length) targetLangName = targetLangNames[i];
                break;
            }
        }
        if (btnVotTargetLang != null) {
            btnVotTargetLang.setText(I18n.get(context, R.string.vot_target_lang, targetLangName) + "  (◄ / ►)");
        }

        float curVol = op.getFloat(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_TTS_VOLUME, 1.0f);
        String[] volNames = I18n.getStringArray(context, R.array.vot_tts_volume_names);
        String volName = volNames.length > 0 ? volNames[0] : "100%";
        for (int i = 0; i < VOT_TTS_VOLUMES.length; i++) {
            if (Math.abs(VOT_TTS_VOLUMES[i] - curVol) < 0.05f) {
                if (i < volNames.length) volName = volNames[i];
                break;
            }
        }
        if (btnVotTtsVolume != null) {
            btnVotTtsVolume.setText(I18n.get(context, R.string.vot_volume, volName) + "  (◄ / ►)");
        }

        int ducking = op.getInt(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_DUCKING_MODE, 0);
        String[] duckNames = I18n.getStringArray(context, R.array.vot_ducking_mode_names);
        String duckLabel = (ducking >= 0 && ducking < duckNames.length) ? duckNames[ducking] : (duckNames.length > 0 ? duckNames[0] : "");
        if (btnVotDuckingMode != null) {
            btnVotDuckingMode.setText(I18n.get(context, R.string.vot_ducking, duckLabel) + "  (◄ / ►)");
        }

        boolean showSubs = op.getBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SHOW_SUBTITLES, true);
        boolean bilingual = op.getBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_BILINGUAL, false);
        String subLabel = I18n.get(context, R.string.status_off);
        if (showSubs && bilingual) subLabel = "Bilingual";
        else if (showSubs) subLabel = targetLangName;
        if (btnVotSubtitlesMode != null) {
            btnVotSubtitlesMode.setText(I18n.get(context, R.string.vot_subtitles, subLabel) + "  (◄ / ►)");
        }

        float curRate = op.getFloat(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_SPEECH_RATE, 1.15f);
        String[] rateNames = I18n.getStringArray(context, R.array.vot_speech_rate_names);
        String rateLabel = rateNames.length > 1 ? rateNames[1] : "1.15x";
        for (int i = 0; i < VOT_SPEECH_RATES.length; i++) {
            if (Math.abs(VOT_SPEECH_RATES[i] - curRate) < 0.05f) {
                if (i < rateNames.length) rateLabel = rateNames[i];
                break;
            }
        }
        if (btnVotSpeechRate != null) {
            btnVotSpeechRate.setText(I18n.get(context, R.string.vot_rate, rateLabel) + "  (◄ / ►)");
        }

        int provider = op.getInt(com.nitsutech.omnitv.vot.VotTranslationEngine.KEY_TRANSLATION_PROVIDER, 0);
        String[] provNames = I18n.getStringArray(context, R.array.vot_provider_names);
        String provLabel = (provider >= 0 && provider < provNames.length) ? provNames[provider] : (provNames.length > 0 ? provNames[0] : "Google");
        if (btnVotProvider != null) {
            btnVotProvider.setText(I18n.get(context, R.string.vot_provider, provLabel) + "  (◄ / ►)");
        }

        String curModel = op.getString(com.nitsutech.omnitv.vot.VotTranslationEngine.KEY_OPENROUTER_MODEL, VOT_OPENROUTER_MODELS[0]);
        if (btnVotOpenrouterModel != null) {
            btnVotOpenrouterModel.setText(I18n.get(context, R.string.vot_model, curModel) + "  (◄ / ►)");
            btnVotOpenrouterModel.setVisibility(provider == 2 ? View.VISIBLE : View.GONE);
        }

        String curKey = op.getString(com.nitsutech.omnitv.vot.VotTranslationEngine.KEY_OPENROUTER_KEY, "").trim();
        if (btnVotOpenrouterKey != null) {
            if (!curKey.isEmpty()) {
                String masked = curKey.length() > 8 ? (curKey.substring(0, 4) + "••••" + curKey.substring(curKey.length() - 4)) : "OK";
                btnVotOpenrouterKey.setText(I18n.get(context, R.string.vot_key_set) + " (" + masked + ")");
                btnVotOpenrouterKey.setTextColor(0xFF81C784);
            } else {
                btnVotOpenrouterKey.setText(I18n.get(context, R.string.vot_key_unset));
                btnVotOpenrouterKey.setTextColor(0xFFFFB74D);
            }
            btnVotOpenrouterKey.setVisibility(provider == 2 ? View.VISIBLE : View.GONE);
        }
    }

    private void cycleAiProvider(int delta) {
        SharedPreferences op = getOverlayPrefs();
        int cur = op.getInt(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_AI_PROVIDER, 0);
        int next = (cur + delta + AI_PROVIDER_NAMES.length) % AI_PROVIDER_NAMES.length;
        op.edit().putInt(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_AI_PROVIDER, next).apply();
        updateAiSummaryConfigPanel();
    }

    private void cycleAiModel(int delta) {
        SharedPreferences op = getOverlayPrefs();
        int provider = op.getInt(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_AI_PROVIDER, 0);
        if (provider == 0) {
            java.util.List<String> models = com.nitsutech.omnitv.ai.AiSummaryEngine.getAvailableGeminiModels(context);
            String cur = op.getString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_GEMINI_MODEL, com.nitsutech.omnitv.ai.AiSummaryEngine.DEFAULT_GEMINI_MODEL);
            int idx = models.indexOf(cur);
            if (idx < 0) idx = 0;
            int next = (idx + delta + models.size()) % models.size();
            String selected = models.get(next);
            op.edit().putString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_GEMINI_MODEL, selected).apply();
            android.widget.Toast.makeText(context, "🤖 Modelo Gemini: " + selected, android.widget.Toast.LENGTH_SHORT).show();
        } else {
            String cur = op.getString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_OPENROUTER_MODEL, AI_OPENROUTER_MODELS[0]);
            int idx = 0;
            for (int i = 0; i < AI_OPENROUTER_MODELS.length; i++) {
                if (AI_OPENROUTER_MODELS[i].equalsIgnoreCase(cur)) {
                    idx = i;
                    break;
                }
            }
            int next = (idx + delta + AI_OPENROUTER_MODELS.length) % AI_OPENROUTER_MODELS.length;
            op.edit().putString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_OPENROUTER_MODEL, AI_OPENROUTER_MODELS[next]).apply();
        }
        updateAiSummaryConfigPanel();
    }

    private void handleAiApiKeyAction() {
        SharedPreferences op = getOverlayPrefs();
        int provider = op.getInt(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_AI_PROVIDER, 0);
        final String prefKey = provider == 0 ? com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_GEMINI_KEY
                                             : com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_OPENROUTER_KEY;
        final String name = provider == 0 ? "Gemini" : "OpenRouter";
        final String action = provider == 0 ? "com.nitsutech.omnitv.SET_GEMINI_KEY" : "com.nitsutech.omnitv.SET_OPENROUTER_KEY";

        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null && text.length() > 5) {
                    String cleanKey = text.toString().trim();
                    op.edit().putString(prefKey, cleanKey).apply();
                    String masked = cleanKey.length() > 8 ? (cleanKey.substring(0, 4) + "..." + cleanKey.substring(cleanKey.length() - 4)) : "***";
                    android.widget.Toast.makeText(context, "🔑 Clave de " + name + " pegada (" + masked + ")", android.widget.Toast.LENGTH_SHORT).show();
                    updateAiSummaryConfigPanel();
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pasting clipboard key: " + e.getMessage());
        }

        String existing = op.getString(prefKey, "").trim();
        if (!existing.isEmpty()) {
            android.widget.Toast.makeText(context, "ℹ️ Clave configurada: " + (existing.length() > 8 ? existing.substring(0, 4) + "..." + existing.substring(existing.length() - 4) : "***") + "\nPara cambiarla, copia la nueva y presiona OK.", android.widget.Toast.LENGTH_LONG).show();
        } else {
            android.widget.Toast.makeText(context, "💡 Copia la clave en el móvil (Google TV Remote) y presiona OK para pegar, o vía ADB: adb shell am broadcast -a " + action + " --es key 'tu-api-key'", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void cycleAiUiLang(int delta) {
        SharedPreferences op = getOverlayPrefs();
        String cur = op.getString("ai_ui_language", "es");
        String next = "es".equalsIgnoreCase(cur) ? "en" : "es";
        op.edit().putString("ai_ui_language", next).apply();
        String label = "es".equalsIgnoreCase(next) ? "Español" : "English";
        android.widget.Toast.makeText(context, "🌐 Idioma Interfaz IA: " + label, android.widget.Toast.LENGTH_SHORT).show();
        updateAiSummaryConfigPanel();
    }

    private void updateAiSummaryConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        int provider = op.getInt(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_AI_PROVIDER, 0);

        if (btnAiProviderToggle != null) {
            String pName = (provider >= 0 && provider < AI_PROVIDER_NAMES.length) ? AI_PROVIDER_NAMES[provider] : "Google Gemini Direct";
            btnAiProviderToggle.setText(I18n.get(context, R.string.ai_provider, pName) + "  (◄ / ►)");
        }

        if (btnAiModelToggle != null) {
            if (provider == 0) {
                java.util.List<String> models = com.nitsutech.omnitv.ai.AiSummaryEngine.getAvailableGeminiModels(context);
                String curModel = op.getString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_GEMINI_MODEL, com.nitsutech.omnitv.ai.AiSummaryEngine.DEFAULT_GEMINI_MODEL);
                int idx = models.indexOf(curModel);
                String pos = (idx >= 0) ? (" [" + (idx + 1) + "/" + models.size() + "]") : "";
                btnAiModelToggle.setText(I18n.get(context, R.string.ai_model, curModel + pos) + "  (◄ / ►)");
            } else {
                String curModel = op.getString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_OPENROUTER_MODEL, AI_OPENROUTER_MODELS[0]);
                btnAiModelToggle.setText(I18n.get(context, R.string.ai_model, curModel) + "  (◄ / ►)");
            }
        }

        if (btnAiUiLangToggle != null) {
            String lang = op.getString("ai_ui_language", I18n.getLanguage(context));
            String langLabel = "es".equalsIgnoreCase(lang) ? "Español" : "English";
            btnAiUiLangToggle.setText("   UI Language:  " + langLabel + "  (◄ / ►)");
        }

        if (btnAiApiKeyConfig != null) {
            String key;
            String prefix;
            if (provider == 0) {
                key = op.getString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_GEMINI_KEY, "").trim();
                prefix = "Gemini";
            } else {
                key = op.getString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_OPENROUTER_KEY, "").trim();
                prefix = "OpenRouter";
            }

            if (!key.isEmpty()) {
                String masked = key.length() > 8 ? (key.substring(0, 4) + "••••" + key.substring(key.length() - 4)) : "OK";
                btnAiApiKeyConfig.setText("   " + prefix + " " + I18n.get(context, R.string.ai_key_set) + " (" + masked + ")");
                btnAiApiKeyConfig.setTextColor(0xFF81C784);
            } else {
                btnAiApiKeyConfig.setText("   " + prefix + " " + I18n.get(context, R.string.ai_key_unset));
                btnAiApiKeyConfig.setTextColor(0xFFFFB74D);
            }
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
            if (isReorderMode && reorderSelectedIndex == i) {
                label = "↕️  " + label;
            }
            TextView btn = createBtn(label, color, d);

            if (isReorderMode && reorderSelectedIndex == i) {
                android.graphics.drawable.GradientDrawable grabBg = new android.graphics.drawable.GradientDrawable();
                grabBg.setColor(Color.argb(230, 230, 81, 0)); // Vibrant Amber/Orange
                grabBg.setCornerRadius(10 * d);
                grabBg.setStroke(Math.round(2 * d), Color.argb(255, 255, 215, 64)); // Gold Accent
                btn.setBackground(grabBg);
                btn.setTextColor(Color.WHITE);
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
            hint.setText(reorderSelectedIndex == -1 
                ? "Presioná OK en un ítem para moverlo" 
                : "Mové con ARRIBA/ABAJO. Presioná OK o ATRÁS para soltar.");
            hint.setTextColor(Color.argb(230, 255, 200, 80));
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
        closeActionPickerDialog();
        stopHoldRepeat();
        if (panelTimer != null) panelTimer.setVisibility(View.GONE);
        if (panelCine != null) panelCine.setVisibility(View.GONE);
        if (panelBlueLight != null) panelBlueLight.setVisibility(View.GONE);
        if (panelBrightness != null) panelBrightness.setVisibility(View.GONE);
        if (panelButtonConfig != null) panelButtonConfig.setVisibility(View.GONE);
        if (panelClockConfig != null) panelClockConfig.setVisibility(View.GONE);
        if (panelCycleBrightness != null) panelCycleBrightness.setVisibility(View.GONE);
        if (panelAutoPause != null) panelAutoPause.setVisibility(View.GONE);
        if (panelStillWatching != null) panelStillWatching.setVisibility(View.GONE);
        if (panelNightSchedule != null) panelNightSchedule.setVisibility(View.GONE);
        if (panelOledSaver != null) panelOledSaver.setVisibility(View.GONE);
        if (panelScheduledSleep != null) panelScheduledSleep.setVisibility(View.GONE);
        if (panelMindfulDelay != null) panelMindfulDelay.setVisibility(View.GONE);
        if (panelTranslateConfig != null) panelTranslateConfig.setVisibility(View.GONE);
        if (panelButtonCombos != null) panelButtonCombos.setVisibility(View.GONE);
        if (panelVotConfig != null) panelVotConfig.setVisibility(View.GONE);
        if (panelAiSummaryConfig != null) panelAiSummaryConfig.setVisibility(View.GONE);
        if (panelQuickBrightnessSlider != null) panelQuickBrightnessSlider.setVisibility(View.GONE);
        openSubPanel = null;
        configuringButton = null;
        if (menuContainer != null) {
            menuContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
    }

    private void handleReorderClick(int index) {
        String[] order = getMenuOrder();
        if (reorderSelectedIndex == index) {
            reorderSelectedIndex = -1;
        } else {
            reorderSelectedIndex = index;
            if (index >= 0 && index < order.length) {
                lastFocusedId = order[index];
            }
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
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override public void run() {
                        if (isShowing()) {
                            buildMenu();
                        }
                    }
                }, 100);
                buildMenu();
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
                    if (txtScheduledAlarmSelector != null) txtScheduledAlarmSelector.requestFocus();
                    else if (btnScheduledSleepToggle != null) btnScheduledSleepToggle.requestFocus();
                }
                break;
            case "cycle_brightness":
                if ("cycle_brightness_config".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelCycleBrightness != null) panelCycleBrightness.setVisibility(View.VISIBLE);
                    openSubPanel = "cycle_brightness_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateBrightnessHudConfigPanel();
                    if (btnCycleBrightnessNow != null) btnCycleBrightnessNow.requestFocus();
                }
                break;
            case "system_info":
                sendServiceAction("ACTION_SHOW_SYSTEM_INFO");
                dismiss();
                break;
            case "reboot":
                sendServiceAction("ACTION_REBOOT");
                dismiss();
                break;
            case "language": {
                String cur = I18n.getLanguage(context);
                String next = I18n.LANG_SPANISH.equalsIgnoreCase(cur) ? I18n.LANG_ENGLISH : I18n.LANG_SPANISH;
                I18n.setLanguage(context, next);
                String nextName = I18n.LANG_SPANISH.equalsIgnoreCase(next) ? "Español" : "English";
                android.widget.Toast.makeText(context, I18n.get(context, R.string.toast_language_changed, nextName), android.widget.Toast.LENGTH_SHORT).show();
                if (rootView != null && windowManager != null) {
                    try {
                        if (rootView.isAttachedToWindow()) windowManager.removeView(rootView);
                    } catch (Exception ignored) {}
                    rootView = null;
                }
                show(context);
                break;
            }
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
            case "mindful_delay":
                if ("mindful_delay_config".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelMindfulDelay != null) panelMindfulDelay.setVisibility(View.VISIBLE);
                    openSubPanel = "mindful_delay_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateMindfulDelayConfigPanel();
                    if (btnMindfulDelayToggle != null) btnMindfulDelayToggle.requestFocus();
                }
                break;
            case "trigger_translate":
                dismiss();
                sendServiceAction("ACTION_TRANSLATE_SCREEN");
                break;
            case "translate":
                if ("translate_config".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelTranslateConfig != null) panelTranslateConfig.setVisibility(View.VISIBLE);
                    openSubPanel = "translate_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateTranslateConfigPanel();
                    if (btnTranslateTargetLang != null) btnTranslateTargetLang.requestFocus();
                }
                break;
            case "button_combos":
                if ("button_combos_config".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelButtonCombos != null) panelButtonCombos.setVisibility(View.VISIBLE);
                    openSubPanel = "button_combos_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateButtonCombosPanel();
                    if (btnCombosMasterToggle != null) btnCombosMasterToggle.requestFocus();
                }
                break;
            case "vot":
                if ("vot_config".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelVotConfig != null) panelVotConfig.setVisibility(View.VISIBLE);
                    openSubPanel = "vot_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateVotConfigPanel();
                    if (btnVotMasterToggle != null) btnVotMasterToggle.requestFocus();
                }
            case "trigger_ai_summary":
                dismiss();
                com.nitsutech.omnitv.ai.AiSummaryOverlay.getInstance().show(context);
                break;
            case "ai_summary":
                if ("ai_summary_config".equals(openSubPanel)) {
                    closeSubPanels();
                    buildMenu();
                } else {
                    closeSubPanels();
                    buildMenu();
                    if (panelAiSummaryConfig != null) panelAiSummaryConfig.setVisibility(View.VISIBLE);
                    openSubPanel = "ai_summary_config";
                    menuContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    updateAiSummaryConfigPanel();
                    com.nitsutech.omnitv.ai.AiSummaryEngine.fetchAvailableGeminiModelsAsync(context, new Runnable() {
                        @Override public void run() {
                            if ("ai_summary_config".equals(openSubPanel)) {
                                updateAiSummaryConfigPanel();
                            }
                        }
                    });
                    if (btnAiLaunchNow != null) btnAiLaunchNow.requestFocus();
                }
                break;
            default:
                Log.w(TAG, "Unknown item: " + id);
        }
    }

    private String getLabelForId(String id, SharedPreferences op, SharedPreferences tp) {
        switch (id) {
            case "manage_apps":  return I18n.get(context, R.string.menu_manage_apps);
            case "timer":        return getTimerLabel(tp);
            case "blue_light": {
                int pct = op.getInt("blue_light_pct", 50);
                if (pct == 0) pct = 50;
                boolean active = op.getBoolean(ButtonMappingService.KEY_BLUE_LIGHT, false);
                double displayPct = pct / 10.0;
                String valStr = active ? String.format(java.util.Locale.US, "%.1f%%", displayPct) : I18n.get(context, R.string.status_off);
                return I18n.get(context, R.string.menu_blue_light, valStr);
            }
            case "clock":        return fmtToggle(I18n.get(context, R.string.menu_clock), op.getBoolean(ButtonMappingService.KEY_CLOCK,  false));
            case "dimmer": {
                int pct = op.getInt("dimmer_brightness_pct", 50);
                return fmtToggle(I18n.get(context, R.string.menu_dimmer, pct), op.getBoolean(ButtonMappingService.KEY_DIMMER, false));
            }
            case "grayscale":    return fmtToggle(I18n.get(context, R.string.menu_grayscale), isGrayscaleOn());
            case "cine_mode":    return fmtToggle(I18n.get(context, R.string.menu_cine_mode), op.getBoolean(ButtonMappingService.KEY_CINE_MODE, false));
            case "auto_pause": {
                int mode = op.getInt("auto_pause_mode", 0);
                String modeText;
                if (mode == 0) modeText = I18n.get(context, R.string.status_off);
                else if (mode == 1) modeText = I18n.get(context, R.string.mode_once);
                else if (mode == 2) modeText = I18n.get(context, R.string.mode_permanent);
                else {
                    int count = op.getInt("auto_pause_custom_count", 1);
                    modeText = I18n.get(context, R.string.mode_times, count);
                }
                return I18n.get(context, R.string.menu_auto_pause, modeText);
            }
            case "screen_off":   return I18n.get(context, R.string.menu_screen_off);
            case "system_menu":  return I18n.get(context, R.string.menu_system_menu);
            case "google_home":  return I18n.get(context, R.string.menu_google_home);
            case "bluetooth":    return I18n.get(context, R.string.menu_bluetooth);
            case "system_info":  return I18n.get(context, R.string.menu_system_info);
            case "reboot":       return I18n.get(context, R.string.menu_reboot);
            case "developer_options": return I18n.get(context, R.string.menu_developer_options);
            case "pause_screen_off": return I18n.get(context, R.string.menu_pause_screen_off);
            case "scheduled_sleep": return fmtScheduledSleep(op);
            case "cycle_brightness": {
                int pct = op.getInt("dimmer_brightness_pct", 50);
                return I18n.get(context, R.string.menu_cycle_brightness, pct);
            }
            case "still_watching": return fmtToggle(I18n.get(context, R.string.menu_still_watching), op.getBoolean(ButtonMappingService.KEY_STILL_WATCHING, false));
            case "mindful_delay": {
                boolean active = op.getBoolean(ButtonMappingService.KEY_MINDFUL_DELAY, false);
                int secs = op.getInt("mindful_delay_seconds", 60);
                String timeStr = (secs >= 60) ? (secs / 60) + "m" : (secs + "s");
                if (secs % 60 != 0 && secs >= 60) {
                    timeStr = (secs / 60) + "m " + (secs % 60) + "s";
                }
                return I18n.get(context, R.string.menu_mindful_delay, active ? timeStr : I18n.get(context, R.string.status_off));
            }
            case "night_schedule": return fmtToggle(I18n.get(context, R.string.menu_night_schedule), op.getBoolean(ButtonMappingService.KEY_NIGHT_SCHEDULE, false));
            case "oled_saver": return fmtToggle(I18n.get(context, R.string.menu_oled_saver), op.getBoolean(ButtonMappingService.KEY_OLED_SAVER, false));
            case "vot": {
                boolean on = op.getBoolean(com.nitsutech.omnitv.vot.VotManager.KEY_VOT_ENABLED, false);
                return fmtToggle(I18n.get(context, R.string.menu_vot), on);
            }
            case "trigger_ai_summary": return I18n.get(context, R.string.menu_trigger_ai_summary);
            case "ai_summary": return I18n.get(context, R.string.menu_ai_summary_config);
            case "trigger_translate": return I18n.get(context, R.string.menu_trigger_translate);
            case "translate": return I18n.get(context, R.string.menu_translate_config);
            case "button_combos": return I18n.get(context, R.string.menu_button_combos);
            case "config_mute":        return I18n.get(context, R.string.menu_config_mute);
            case "config_youtube_190": return I18n.get(context, R.string.menu_config_youtube_190);
            case "config_youtube_189": return I18n.get(context, R.string.menu_config_youtube_189);
            case "language": {
                String cur = I18n.getLanguage(context);
                String langName = I18n.LANG_SPANISH.equalsIgnoreCase(cur) ? "Español" : "English";
                return I18n.get(context, R.string.menu_language, langName);
            }
            default:             return id;
        }
    }

    private String fmtScheduledSleep(SharedPreferences op) {
        String summary = ScheduledSleepReceiver.getActiveAlarmsSummary(context);
        return I18n.get(context, R.string.menu_scheduled_sleep, summary);
    }

    private int getColorForId(String id) {
        if ("reboot".equals(id)) return 0xFFFF6B6B;
        if ("mindful_delay".equals(id) || "language".equals(id)) return 0xFF81D4FA;
        if ("ai_summary".equals(id) || "trigger_ai_summary".equals(id)) return 0xFF8AB4F8;
        return Color.WHITE;
    }

    private int getGroupForId(String id) {
        switch (id) {
            case "manage_apps": case "timer": case "blue_light":
            case "clock": case "dimmer": case "grayscale": case "cine_mode":
            case "cycle_brightness": case "auto_pause": case "mindful_delay": case "vot": case "trigger_ai_summary": case "ai_summary":
                return 1;
            case "screen_off": case "pause_screen_off": case "scheduled_sleep":
                return 2;
            case "google_home": case "bluetooth": case "system_info": case "reboot":
            case "system_menu": case "developer_options": case "language":
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
            return I18n.get(context, R.string.menu_timer_format, mins + " min");
        }
        return I18n.get(context, R.string.menu_timer_off);
    }

    private boolean isGrayscaleOn() {
        return ToggleUtils.isGrayscaleEnabled(context);
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
            btnCineBlueLightConfig.setText(I18n.get(context, R.string.cine_bl_prefix, cp.getBoolean("cine_blue_light", true) ? I18n.get(context, R.string.status_on) : I18n.get(context, R.string.status_off)));
        if (btnCineDimmerConfig != null)
            btnCineDimmerConfig.setText(I18n.get(context, R.string.cine_dimmer_prefix, cp.getBoolean("cine_dimmer", false) ? I18n.get(context, R.string.status_on) : I18n.get(context, R.string.status_off)));
        if (btnCineTimerConfig != null) {
            int t = cp.getInt("cine_timer", 0);
            String tLabel = t == 0 ? I18n.get(context, R.string.status_disabled) : (t < 60 ? t + " min" : (t / 60) + "h" + (t % 60 > 0 ? " " + (t % 60) + "m" : ""));
            btnCineTimerConfig.setText(I18n.get(context, R.string.cine_timer_prefix, tLabel) + "  (tap=change)");
        }
        if (btnApplyCine != null) {
            boolean active = getOverlayPrefs().getBoolean(ButtonMappingService.KEY_CINE_MODE, false);
            btnApplyCine.setText(active ? "[ " + I18n.get(context, R.string.status_disabled) + " " + I18n.get(context, R.string.action_cine_mode) + " ]" : I18n.get(context, R.string.cine_apply_btn));
        }
    }

    private void updateButtonConfigPanel() {
        if (configuringButton == null) return;

        SharedPreferences prefs = getOverlayPrefs();
        String btnName = configuringButton;
        
        int defClick1 = 0, defClick2 = 0, defClick3 = 0, defClick4 = 0, defLong = 0, defDur = 1000;
        String title = I18n.get(context, R.string.btn_config_title) + " ";
        if ("mute".equals(btnName)) {
            title += "Mute";
            defClick1 = 1; defClick2 = 7; defClick3 = 8; defClick4 = 23; defLong = 2; defDur = 1000;
        } else if ("youtube_190".equals(btnName)) {
            title += "YouTube (190)";
            defClick1 = 5; defClick2 = 4; defClick3 = 33; defClick4 = 0; defLong = 3; defDur = 2000;
        } else if ("youtube_189".equals(btnName)) {
            title += "YouTube (189)";
            defClick1 = 5; defClick2 = 0; defClick3 = 33; defClick4 = 0; defLong = 4; defDur = 2000;
        }

        if (txtConfigTitle != null) txtConfigTitle.setText(title);

        int actClick1 = prefs.getInt("btn_" + btnName + "_click_1_action", defClick1);
        int actClick2 = prefs.getInt("btn_" + btnName + "_click_2_action", defClick2);
        int actClick3 = prefs.getInt("btn_" + btnName + "_click_3_action", defClick3);
        int actClick4 = prefs.getInt("btn_" + btnName + "_click_4_action", defClick4);
        int actLong   = prefs.getInt("btn_" + btnName + "_long_action", defLong);
        int durationMs = prefs.getInt("btn_" + btnName + "_long_duration_ms", defDur);

        if (btnConfigClick1 != null) btnConfigClick1.setText(I18n.get(context, R.string.btn_config_single, getActionName(actClick1)));
        if (btnConfigClick2 != null) btnConfigClick2.setText(I18n.get(context, R.string.btn_config_double, getActionName(actClick2)));
        if (btnConfigClick3 != null) btnConfigClick3.setText(I18n.get(context, R.string.btn_config_triple, getActionName(actClick3)));
        if (btnConfigClick4 != null) btnConfigClick4.setText(I18n.get(context, R.string.btn_config_quad, getActionName(actClick4)));
        if (btnConfigLong != null)   btnConfigLong.setText(I18n.get(context, R.string.btn_config_long, getActionName(actLong)));
        
        if (txtConfigDuration != null) {
            double secs = durationMs / 1000.0;
            txtConfigDuration.setText(String.format(java.util.Locale.US, "%.1fs", secs));
        }
    }

    private String getActionName(int actionId) {
        String[] names = I18n.getActionNames(context);
        if (actionId >= 0 && actionId < names.length) {
            return names[actionId];
        }
        return "Unknown";
    }

    private void adjustButtonConfigAction(View view, int delta) {
        if (configuringButton == null) return;
        String key;
        int def;
        if (view == btnConfigClick1) {
            key = "btn_" + configuringButton + "_click_1_action";
            def = "mute".equals(configuringButton) ? 1 : 5;
        } else if (view == btnConfigClick2) {
            key = "btn_" + configuringButton + "_click_2_action";
            def = "mute".equals(configuringButton) ? 7 : ("youtube_190".equals(configuringButton) ? 4 : 0);
        } else if (view == btnConfigClick3) {
            key = "btn_" + configuringButton + "_click_3_action";
            def = "mute".equals(configuringButton) ? 8 : 33;
        } else if (view == btnConfigClick4) {
            key = "btn_" + configuringButton + "_click_4_action";
            def = "mute".equals(configuringButton) ? 23 : 0;
        } else if (view == btnConfigLong) {
            key = "btn_" + configuringButton + "_long_action";
            def = "mute".equals(configuringButton) ? 2 : ("youtube_190".equals(configuringButton) ? 3 : 4);
        } else {
            return;
        }
        int cur = getOverlayPrefs().getInt(key, def);
        cycleActionConfig(key, cur, delta);
        if (view != null) {
            view.requestFocus();
        }
    }

    private void adjustComboAction(View view, int delta) {
        String key;
        int def;
        if (view == btnComboMuteOk) {
            key = "combo_mute_ok_action";
            def = 23;
        } else if (view == btnComboMuteRight) {
            key = "combo_mute_right_action";
            def = 24;
        } else if (view == btnComboMuteLeft) {
            key = "combo_mute_left_action";
            def = 25;
        } else if (view == btnComboMuteUp) {
            key = "combo_mute_up_action";
            def = 0;
        } else if (view == btnComboMuteDown) {
            key = "combo_mute_down_action";
            def = 0;
        } else if (view == btnComboYoutube190Mute) {
            key = "combo_youtube190_mute_action";
            def = 0;
        } else if (view == btnComboInputOk) {
            key = "combo_input_ok_action";
            def = 0;
        } else {
            return;
        }
        int cur = getOverlayPrefs().getInt(key, def);
        cycleActionConfig(key, cur, delta);
        if (view != null) {
            view.requestFocus();
        }
    }

    private static class ActionEntry {
        final int id;
        final String name;
        ActionEntry(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private String getActionKeyForView(View view) {
        if (view == null) return null;
        if (view == btnConfigClick1 && configuringButton != null) return "btn_" + configuringButton + "_click_1_action";
        if (view == btnConfigClick2 && configuringButton != null) return "btn_" + configuringButton + "_click_2_action";
        if (view == btnConfigClick3 && configuringButton != null) return "btn_" + configuringButton + "_click_3_action";
        if (view == btnConfigClick4 && configuringButton != null) return "btn_" + configuringButton + "_click_4_action";
        if (view == btnConfigLong   && configuringButton != null) return "btn_" + configuringButton + "_long_action";
        if (view == btnComboMuteOk) return "combo_mute_ok_action";
        if (view == btnComboMuteRight) return "combo_mute_right_action";
        if (view == btnComboMuteLeft) return "combo_mute_left_action";
        if (view == btnComboMuteUp) return "combo_mute_up_action";
        if (view == btnComboMuteDown) return "combo_mute_down_action";
        if (view == btnComboYoutube190Mute) return "combo_youtube190_mute_action";
        if (view == btnComboInputOk) return "combo_input_ok_action";
        return null;
    }

    private int getDefaultActionForView(View view) {
        if (view == null) return 0;
        if (view == btnConfigClick1) return "mute".equals(configuringButton) ? 1 : 5;
        if (view == btnConfigClick2) return "mute".equals(configuringButton) ? 7 : ("youtube_190".equals(configuringButton) ? 4 : 0);
        if (view == btnConfigClick3) return "mute".equals(configuringButton) ? 8 : 33;
        if (view == btnConfigClick4) return "mute".equals(configuringButton) ? 23 : 0;
        if (view == btnConfigLong) return "mute".equals(configuringButton) ? 2 : ("youtube_190".equals(configuringButton) ? 3 : 4);
        if (view == btnComboMuteOk) return 23;
        if (view == btnComboMuteRight) return 24;
        if (view == btnComboMuteLeft) return 25;
        return 0;
    }

    private void showActionPickerDialog(View triggerView) {
        if (triggerView == null || dialogActionPickerOverlay == null || containerActionPickerItems == null) return;
        final String key = getActionKeyForView(triggerView);
        if (key == null) return;

        lastActionPickerTriggerView = triggerView;
        int def = getDefaultActionForView(triggerView);
        int currentActionId = getOverlayPrefs().getInt(key, def);

        if (txtActionPickerTitle != null) {
            txtActionPickerTitle.setText(I18n.get(context, R.string.dialog_select_action));
        }
        if (txtActionPickerSubtitle != null) {
            txtActionPickerSubtitle.setText(I18n.get(context, R.string.dialog_select_action_subtitle));
        }

        containerActionPickerItems.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;

        List<ActionEntry> entries = new ArrayList<>();
        String[] names = I18n.getActionNames(context);
        for (int i = 0; i < TOTAL_ACTIONS && i < names.length; i++) {
            entries.add(new ActionEntry(i, names[i]));
        }
        Collections.sort(entries, new Comparator<ActionEntry>() {
            @Override
            public int compare(ActionEntry a, ActionEntry b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        View viewToFocus = null;
        for (final ActionEntry entry : entries) {
            TextView tv = new TextView(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(42 * density)
            );
            lp.bottomMargin = Math.round(4 * density);
            tv.setLayoutParams(lp);
            tv.setFocusable(true);
            tv.setClickable(true);
            tv.setBackgroundResource(R.drawable.card_background);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(Math.round(14 * density), 0, Math.round(14 * density), 0);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

            boolean isCurrent = (entry.id == currentActionId);
            if (isCurrent) {
                tv.setText(entry.name + "  ✓");
                tv.setTextColor(Color.parseColor("#8AB4F8"));
                viewToFocus = tv;
            } else {
                tv.setText(entry.name);
                tv.setTextColor(Color.parseColor("#CCCCCC"));
            }

            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getOverlayPrefs().edit().putInt(key, entry.id).apply();
                    closeActionPickerDialog();
                    if (key.startsWith("combo_")) {
                        updateButtonCombosPanel();
                    } else {
                        updateButtonConfigPanel();
                    }
                    if (lastActionPickerTriggerView != null) {
                        requestViewFocus(lastActionPickerTriggerView);
                    }
                }
            });

            containerActionPickerItems.addView(tv);
        }

        dialogActionPickerOverlay.setVisibility(View.VISIBLE);

        final View targetFocus = (viewToFocus != null) ? viewToFocus :
            (containerActionPickerItems.getChildCount() > 0 ? containerActionPickerItems.getChildAt(0) : null);
        if (targetFocus != null) {
            targetFocus.post(new Runnable() {
                @Override
                public void run() {
                    requestViewFocus(targetFocus);
                }
            });
        }
    }

    private void closeActionPickerDialog() {
        if (dialogActionPickerOverlay != null) {
            dialogActionPickerOverlay.setVisibility(View.GONE);
        }
        if (containerActionPickerItems != null) {
            containerActionPickerItems.removeAllViews();
        }
    }

    private void cycleCineTimer(int delta) {
        SharedPreferences cp = context.getSharedPreferences("cine_prefs", Context.MODE_PRIVATE);
        int cur = cp.getInt("cine_timer", 0);
        int idx = 0;
        for (int i = 0; i < CINE_TIMER_OPTIONS.length; i++) {
            if (CINE_TIMER_OPTIONS[i] == cur) {
                idx = i;
                break;
            }
        }
        int nextIdx = (idx + delta) % CINE_TIMER_OPTIONS.length;
        if (nextIdx < 0) nextIdx += CINE_TIMER_OPTIONS.length;
        cp.edit().putInt("cine_timer", CINE_TIMER_OPTIONS[nextIdx]).apply();
        updateCineConfigPanel();
        if (btnCineTimerConfig != null) btnCineTimerConfig.requestFocus();
    }

    private void cycleAutoPauseMode(int delta) {
        SharedPreferences op = getOverlayPrefs();
        int cur = op.getInt("auto_pause_mode", 0);
        int next = (cur + delta) % 4;
        if (next < 0) next += 4;
        op.edit().putInt("auto_pause_mode", next).apply();
        updateAutoPauseConfigPanel();
        if (btnAutoPauseMode != null) btnAutoPauseMode.requestFocus();
        buildMenu();
    }

    private void cycleOledMode(int delta) {
        int cur = getOverlayPrefs().getInt(ButtonMappingService.KEY_OLED_MODE, 0);
        int next = (cur + delta) % 2;
        if (next < 0) next += 2;
        getOverlayPrefs().edit().putInt(ButtonMappingService.KEY_OLED_MODE, next).apply();
        updateOledSaverConfigPanel();
        sendServiceAction("ACTION_UPDATE_OLED_SAVER");
        if (btnOledMode != null) btnOledMode.requestFocus();
    }

    private void cycleStillWatchingBeepTone(int delta) {
        int cur = getOverlayPrefs().getInt(ButtonMappingService.KEY_STILL_WATCHING_BEEP_TONE, 0);
        int next = (cur + delta) % STILL_WATCHING_TONES.length;
        if (next < 0) next += STILL_WATCHING_TONES.length;
        getOverlayPrefs().edit().putInt(ButtonMappingService.KEY_STILL_WATCHING_BEEP_TONE, next).apply();
        sendServiceAction("ACTION_UPDATE_STILL_WATCHING");
        updateStillWatchingConfigPanel();
        playStillWatchingBeepSound();
        if (btnStillWatchingBeepTone != null) btnStillWatchingBeepTone.requestFocus();
    }

    private void cycleMindfulCancelAction(int delta) {
        int cur = getOverlayPrefs().getInt("mindful_delay_cancel_action", 0);
        int next = (cur + delta) % MINDFUL_CANCEL_ACTIONS.length;
        if (next < 0) next += MINDFUL_CANCEL_ACTIONS.length;
        getOverlayPrefs().edit().putInt("mindful_delay_cancel_action", next).apply();
        updateMindfulDelayConfigPanel();
        if (btnMindfulDelayCancelAction != null) btnMindfulDelayCancelAction.requestFocus();
    }

    private void cycleMindfulSession(int delta) {
        int cur = getOverlayPrefs().getInt("mindful_delay_session_mode", 0);
        int next = (cur + delta) % MINDFUL_SESSION_NAMES.length;
        if (next < 0) next += MINDFUL_SESSION_NAMES.length;
        getOverlayPrefs().edit().putInt("mindful_delay_session_mode", next).apply();
        updateMindfulDelayConfigPanel();
        if (btnMindfulDelaySession != null) btnMindfulDelaySession.requestFocus();
    }

    private void cycleMindfulPos(int delta) {
        int cur = getOverlayPrefs().getInt("mindful_delay_pos_idx", 0);
        int next = (cur + delta) % MINDFUL_POSITIONS.length;
        if (next < 0) next += MINDFUL_POSITIONS.length;
        getOverlayPrefs().edit().putInt("mindful_delay_pos_idx", next).apply();
        updateMindfulDelayConfigPanel();
        if (btnMindfulDelayPos != null) btnMindfulDelayPos.requestFocus();
    }

    private void cycleMindfulMsg(int delta) {
        int cur = getOverlayPrefs().getInt("mindful_delay_msg_idx", 0);
        int next = (cur + delta) % MINDFUL_MSG_OPTIONS.length;
        if (next < 0) next += MINDFUL_MSG_OPTIONS.length;
        getOverlayPrefs().edit().putInt("mindful_delay_msg_idx", next).apply();
        updateMindfulDelayConfigPanel();
        if (btnMindfulDelayMsg != null) btnMindfulDelayMsg.requestFocus();
    }

    private void cycleTranslateTargetLang(int delta) {
        int cur = getOverlayPrefs().getInt("translate_target_lang_idx", 0);
        int next = (cur + delta) % TRANSLATE_TARGET_LANGS.length;
        if (next < 0) next += TRANSLATE_TARGET_LANGS.length;
        getOverlayPrefs().edit().putInt("translate_target_lang_idx", next).apply();
        updateTranslateConfigPanel();
        if (btnTranslateTargetLang != null) btnTranslateTargetLang.requestFocus();
    }

    private void cycleTranslateSourceLang(int delta) {
        int cur = getOverlayPrefs().getInt("translate_source_lang_idx", 0);
        int next = (cur + delta) % TRANSLATE_SOURCE_LANGS.length;
        if (next < 0) next += TRANSLATE_SOURCE_LANGS.length;
        getOverlayPrefs().edit().putInt("translate_source_lang_idx", next).apply();
        updateTranslateConfigPanel();
        if (btnTranslateSourceLang != null) btnTranslateSourceLang.requestFocus();
    }

    private void cycleScheduledPromptSec(int delta) {
        SharedPreferences op = getOverlayPrefs();
        int cur = op.getInt("scheduled_sleep_prompt_sec", 60);
        int idx = 2;
        for (int i = 0; i < SCHEDULED_PROMPT_OPTIONS.length; i++) {
            if (SCHEDULED_PROMPT_OPTIONS[i] == cur) {
                idx = i;
                break;
            }
        }
        int nextIdx = (idx + delta) % SCHEDULED_PROMPT_OPTIONS.length;
        if (nextIdx < 0) nextIdx += SCHEDULED_PROMPT_OPTIONS.length;
        op.edit().putInt("scheduled_sleep_prompt_sec", SCHEDULED_PROMPT_OPTIONS[nextIdx]).apply();
        updateScheduledSleepConfigPanel();
        if (btnScheduledPromptToggle != null) btnScheduledPromptToggle.requestFocus();
        buildMenu();
    }

    private void cycleActionConfig(String configKey, int currentAction) {
        cycleActionConfig(configKey, currentAction, 1);
    }

    private void cycleActionConfig(String configKey, int currentAction, int delta) {
        int nextAction = (currentAction + delta) % TOTAL_ACTIONS;
        if (nextAction < 0) nextAction += TOTAL_ACTIONS;
        getOverlayPrefs().edit().putInt(configKey, nextAction).apply();
        if (configKey.startsWith("combo_")) {
            updateButtonCombosPanel();
        } else {
            updateButtonConfigPanel();
        }
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

        String[] colorNames = I18n.getStringArray(context, R.array.clock_color_names);
        String[] bgNames = I18n.getStringArray(context, R.array.clock_bg_names);
        String[] posNames = I18n.getStringArray(context, R.array.clock_position_names);

        if (btnClockTextColor != null) btnClockTextColor.setText(I18n.get(context, R.string.clock_text_color, colorNames.length > 0 ? colorNames[colorIdx % colorNames.length] : ""));
        if (btnClockBgColor != null) btnClockBgColor.setText(I18n.get(context, R.string.clock_bg_color, bgNames.length > 0 ? bgNames[bgIdx % bgNames.length] : ""));
        if (btnClockPosition != null) btnClockPosition.setText(I18n.get(context, R.string.clock_position, posNames.length > 0 ? posNames[posIdx % posNames.length] : ""));
        
        if (txtClockAlpha != null) txtClockAlpha.setText(alphaPct + "%");
        if (txtClockTextAlpha != null) txtClockTextAlpha.setText(textAlphaPct + "%");
        if (txtClockSize != null) txtClockSize.setText(sizeSp + "sp");
        if (txtClockPad != null) txtClockPad.setText(paddingDp + "dp");
        if (txtClockX != null) txtClockX.setText(posX + "dp");
        if (txtClockY != null) txtClockY.setText(posY + "dp");
        
        if (btnApplyClock != null) btnApplyClock.setText(active ? "[ " + I18n.get(context, R.string.status_disabled) + " " + I18n.get(context, R.string.action_clock) + " ]" : I18n.get(context, R.string.clock_apply_btn));
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
        cycleClockIntPref(key, totalOptions, 1);
    }

    private void cycleClockIntPref(String key, int totalOptions, int delta) {
        int cur = getOverlayPrefs().getInt(key, 0);
        int next = (cur + delta) % totalOptions;
        if (next < 0) next += totalOptions;
        getOverlayPrefs().edit().putInt(key, next).apply();
        updateClockConfigPanel();
        sendServiceAction("ACTION_UPDATE_CLOCK");
    }

    private void cycleCycleBrightness() {
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
            prefs.edit()
                    .putInt("dimmer_brightness_pct", next)
                    .putFloat("dimmer_brightness_pct_float", (float) next)
                    .apply();

            Bundle b = new Bundle();
            b.putInt("pct", next);
            sendServiceAction("ACTION_SET_DIMMER_BRIGHTNESS", b);

            Bundle bHud = new Bundle();
            bHud.putInt("pct", next);
            bHud.putInt("cur_idx", nextIdx + 1);
            bHud.putInt("total", levels.length);
            sendServiceAction("ACTION_SHOW_BRIGHTNESS_HUD", bHud);
        }
        updateBrightnessHudConfigPanel();
        buildMenu();
    }

    private void updateBrightnessHudConfigPanel() {
        SharedPreferences prefs = getOverlayPrefs();
        int formatIdx = prefs.getInt("brightness_hud_format_idx", 0);
        int colorIdx = prefs.getInt("brightness_hud_text_color_idx", 0);
        int bgIdx = prefs.getInt("brightness_hud_bg_color_idx", 0);
        int alphaPct = prefs.getInt("brightness_hud_bg_alpha_pct", 35);
        int textAlphaPct = prefs.getInt("brightness_hud_text_alpha_pct", 100);
        int posIdx = prefs.getInt("brightness_hud_position_idx", 0);
        int sizeSp = prefs.getInt("brightness_hud_size_sp", 16);
        int paddingDp = prefs.getInt("brightness_hud_padding_dp", 12);
        int posX = prefs.getInt("brightness_hud_pos_x_dp", 16);
        int posY = prefs.getInt("brightness_hud_pos_y_dp", 16);
        int durMs = prefs.getInt("brightness_hud_duration_ms", 2000);
        int curPct = prefs.getInt("dimmer_brightness_pct", 50);

        String[] formatNames = I18n.getStringArray(context, R.array.brightness_hud_format_names);
        String[] colorNames = I18n.getStringArray(context, R.array.clock_color_names);
        String[] bgNames = I18n.getStringArray(context, R.array.clock_bg_names);
        String[] posNames = I18n.getStringArray(context, R.array.clock_position_names);

        if (btnCycleBrightnessNow != null) {
            btnCycleBrightnessNow.setText("⚡  " + I18n.get(context, R.string.action_cycle_brightness) + "  [" + curPct + "%]");
        }
        if (btnBrightnessHudFormat != null) {
            btnBrightnessHudFormat.setText("   " + I18n.get(context, R.string.clock_format, formatNames.length > 0 ? formatNames[formatIdx % formatNames.length] : ""));
        }
        if (btnBrightnessHudTextColor != null) {
            btnBrightnessHudTextColor.setText(I18n.get(context, R.string.clock_text_color, colorNames.length > 0 ? colorNames[colorIdx % colorNames.length] : ""));
        }
        if (btnBrightnessHudBgColor != null) {
            btnBrightnessHudBgColor.setText(I18n.get(context, R.string.clock_bg_color, bgNames.length > 0 ? bgNames[bgIdx % bgNames.length] : ""));
        }
        if (btnBrightnessHudPosition != null) {
            btnBrightnessHudPosition.setText(I18n.get(context, R.string.clock_position, posNames.length > 0 ? posNames[posIdx % posNames.length] : ""));
        }
        if (txtBrightnessHudAlpha != null) txtBrightnessHudAlpha.setText(alphaPct + "%");
        if (txtBrightnessHudTextAlpha != null) txtBrightnessHudTextAlpha.setText(textAlphaPct + "%");
        if (txtBrightnessHudSize != null) txtBrightnessHudSize.setText(sizeSp + "sp");
        if (txtBrightnessHudPad != null) txtBrightnessHudPad.setText(paddingDp + "dp");
        if (txtBrightnessHudX != null) txtBrightnessHudX.setText(posX + "dp");
        if (txtBrightnessHudY != null) txtBrightnessHudY.setText(posY + "dp");
        if (txtBrightnessHudDur != null) {
            double secs = durMs / 1000.0;
            txtBrightnessHudDur.setText(String.format(java.util.Locale.US, "%.1fs", secs));
        }
        if (btnTestBrightnessHud != null) {
            btnTestBrightnessHud.setText(I18n.get(context, R.string.hud_test_button));
        }
    }

    private void adjustBrightnessHudIntPref(String key, int def, int delta, int min, int max) {
        int cur = getOverlayPrefs().getInt(key, def);
        int next = cur + delta;
        if (next < min) next = min;
        if (next > max) next = max;
        getOverlayPrefs().edit().putInt(key, next).apply();
        updateBrightnessHudConfigPanel();
        sendServiceAction("ACTION_SHOW_BRIGHTNESS_HUD");
    }

    private void cycleBrightnessHudIntPref(String key, int totalOptions) {
        cycleBrightnessHudIntPref(key, totalOptions, 1);
    }

    private void cycleBrightnessHudIntPref(String key, int totalOptions, int delta) {
        int cur = getOverlayPrefs().getInt(key, 0);
        int next = (cur + delta) % totalOptions;
        if (next < 0) next += totalOptions;
        getOverlayPrefs().edit().putInt(key, next).apply();
        updateBrightnessHudConfigPanel();
        sendServiceAction("ACTION_SHOW_BRIGHTNESS_HUD");
    }

    private void updateQuickSliderConfigPanel() {
        SharedPreferences prefs = getOverlayPrefs();
        int orientation = prefs.getInt("quick_slider_orientation", 0);
        int stepIdx = prefs.getInt("quick_slider_step_idx", 0);
        int perpIdx = prefs.getInt("quick_slider_perp_action", 0);
        int posIdx = prefs.getInt("quick_slider_pos_idx", 0);
        int timeoutIdx = prefs.getInt("quick_slider_timeout_idx", 0);

        String[] orientations = I18n.getStringArray(context, R.array.slider_orientations);
        String[] stepLabels = I18n.getStringArray(context, R.array.slider_steps_labels);
        String[] perpActions = I18n.getStringArray(context, R.array.slider_perp_actions);
        String[] posH = I18n.getStringArray(context, R.array.slider_positions_h);
        String[] posV = I18n.getStringArray(context, R.array.slider_positions_v);
        String[] timeouts = I18n.getStringArray(context, R.array.slider_timeouts_labels);

        if (btnSliderTestNow != null) {
            btnSliderTestNow.setText(I18n.get(context, R.string.slider_test_button));
        }
        if (btnSliderOrientation != null) {
            btnSliderOrientation.setText(I18n.get(context, R.string.slider_orientation, orientations.length > 0 ? orientations[orientation % orientations.length] : ""));
        }
        if (btnSliderStep != null) {
            btnSliderStep.setText("   " + (stepLabels.length > 0 ? stepLabels[stepIdx % stepLabels.length] : ""));
        }
        if (btnSliderPerpAction != null) {
            btnSliderPerpAction.setText(I18n.get(context, R.string.slider_perp_action, perpActions.length > 0 ? perpActions[perpIdx % perpActions.length] : ""));
        }
        if (btnSliderPosition != null) {
            String[] posNames = (orientation == 1) ? posV : posH;
            btnSliderPosition.setText(I18n.get(context, R.string.slider_position, posNames.length > 0 ? posNames[posIdx % posNames.length] : ""));
        }
        if (btnSliderTimeout != null) {
            btnSliderTimeout.setText(I18n.get(context, R.string.slider_timeout, timeouts.length > 0 ? timeouts[timeoutIdx % timeouts.length] : ""));
        }
    }

    private void cycleSliderIntPref(String key, int totalOptions) {
        cycleSliderIntPref(key, totalOptions, 1);
    }

    private void cycleSliderIntPref(String key, int totalOptions, int delta) {
        int cur = getOverlayPrefs().getInt(key, 0);
        int next = (cur + delta) % totalOptions;
        if (next < 0) next += totalOptions;
        getOverlayPrefs().edit().putInt(key, next).apply();
        updateQuickSliderConfigPanel();
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
            txtBrightnessPct.setText(I18n.get(context, R.string.dimmer_level_prefix, brightnessPct));
        }
        if (btnApplyBrightness != null) {
            btnApplyBrightness.setText(active ? "[ " + I18n.get(context, R.string.status_disabled) + " Dimmer ]" : "[ " + I18n.get(context, R.string.status_enabled) + " Dimmer ]");
        }

        boolean dimmerAutoReset = prefs.getBoolean("dimmer_day_auto_reset_enabled", true);
        if (btnDimmerDayAutoReset != null) {
            btnDimmerDayAutoReset.setText(I18n.get(context, R.string.dimmer_autoreset_step1, dimmerAutoReset ? "[" + I18n.get(context, R.string.status_enabled) + "]" : "[" + I18n.get(context, R.string.status_disabled) + "]"));
            btnDimmerDayAutoReset.setTextColor(dimmerAutoReset ? 0xFF81C784 : 0xFFE57373);
        }

        boolean grayscaleAutoReset = prefs.getBoolean("grayscale_day_auto_reset_enabled", true);
        if (btnGrayscaleDayAutoReset != null) {
            btnGrayscaleDayAutoReset.setText(I18n.get(context, R.string.dimmer_autoreset_bw, grayscaleAutoReset ? "[" + I18n.get(context, R.string.status_enabled) + "]" : "[" + I18n.get(context, R.string.status_disabled) + "]"));
            btnGrayscaleDayAutoReset.setTextColor(grayscaleAutoReset ? 0xFF81C784 : 0xFFE57373);
        }

        int startH = prefs.getInt("dimmer_day_reset_start_hour", 8);
        if (txtDayResetStart != null) {
            txtDayResetStart.setText(String.format(java.util.Locale.US, "%02d:00", startH));
        }

        int endH = prefs.getInt("dimmer_day_reset_end_hour", 19);
        if (txtDayResetEnd != null) {
            txtDayResetEnd.setText(String.format(java.util.Locale.US, "%02d:00", endH));
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
                tvLabel.setText("   " + (I18n.getLanguage(context).equalsIgnoreCase("es") ? "Nivel " : "Level ") + (i + 1) + ":");
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
                        getOverlayPrefs().edit()
                                .putInt("dimmer_brightness_pct", currentVal)
                                .putFloat("dimmer_brightness_pct_float", (float) currentVal)
                                .apply();
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
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        java.util.HashSet<String> validIds = new java.util.HashSet<>(java.util.Arrays.asList(ALL_ITEM_IDS));
        for (String s : loaded) {
            String trimmed = s.trim();
            if (validIds.contains(trimmed)) {
                set.add(trimmed);
            }
        }
        for (String id : ALL_ITEM_IDS) {
            set.add(id);
        }
        String[] result = set.toArray(new String[0]);
        if (result.length != loaded.length) {
            saveMenuOrder(result);
        }
        return result;
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
            txtBlueLightPct.setText(I18n.get(context, R.string.bl_level_prefix, active ? String.format(java.util.Locale.US, "%.1f%%", displayPct) : I18n.get(context, R.string.status_disabled)));
        }
        if (btnApplyBlueLight != null) {
            btnApplyBlueLight.setText(active ? I18n.get(context, R.string.bl_disable_btn) : "[ " + I18n.get(context, R.string.status_enabled) + " " + I18n.get(context, R.string.action_blue_light) + " ]");
        }
    }

    private void updateAutoPauseConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        int mode = op.getInt("auto_pause_mode", 0);
        int playlistCount = op.getInt("auto_pause_playlist_count", 2);
        boolean blackScreen = op.getBoolean("auto_pause_black_screen", false);

        String[] modes = I18n.getStringArray(context, R.array.autopause_modes);
        if (btnAutoPauseMode != null) {
            String modeStr = (mode >= 0 && mode < modes.length) ? modes[mode] : (modes.length > 0 ? modes[0] : "");
            btnAutoPauseMode.setText(I18n.get(context, R.string.autopause_mode, modeStr));
        }

        if (layoutAutoPauseCustom != null) {
            layoutAutoPauseCustom.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        }

        if (txtAutoPauseCount != null) {
            txtAutoPauseCount.setText(String.valueOf(playlistCount));
        }

        if (btnAutoPauseBlackScreen != null) {
            btnAutoPauseBlackScreen.setText(I18n.get(context, R.string.autopause_black_screen, blackScreen ? I18n.get(context, R.string.status_on) : I18n.get(context, R.string.status_off)));
        }

        if (btnAutoDismissUpNext != null) {
            boolean autoDismiss = op.getBoolean(ButtonMappingService.KEY_AUTO_DISMISS_UP_NEXT, true);
            btnAutoDismissUpNext.setText(I18n.get(context, R.string.autopause_dismiss_up_next, autoDismiss ? I18n.get(context, R.string.status_on) : I18n.get(context, R.string.status_off)));
        }
    }

    private void updateStillWatchingConfigPanel() {
        SharedPreferences prefs = getOverlayPrefs();
        boolean active = prefs.getBoolean(ButtonMappingService.KEY_STILL_WATCHING, false);
        int interval = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_INTERVAL, 30);
        int timeout = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_TIMEOUT, 30);
        boolean beepActive = prefs.getBoolean(ButtonMappingService.KEY_STILL_WATCHING_BEEP, true);
        int beepInterval = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_BEEP_INTERVAL, 10);
        int beepDelay = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_BEEP_DELAY, 9);
        int beepVol = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_BEEP_VOLUME, 65);
        int toneIdx = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_BEEP_TONE, 0);
        int actionIdx = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_ACTION, 0);
        int posIdx = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_POS, 0);
        int alpha = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_ALPHA, 85);
        int sizeSp = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_SIZE, 14);
        int posX = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_X, 16);
        int posY = prefs.getInt(ButtonMappingService.KEY_STILL_WATCHING_Y, 16);

        String[] tones = I18n.getStringArray(context, R.array.still_watching_tones);
        String[] actions = I18n.getStringArray(context, R.array.still_watching_actions);
        String[] positions = I18n.getStringArray(context, R.array.still_watching_positions);

        if (toneIdx < 0 || toneIdx >= tones.length) toneIdx = 0;
        if (actionIdx < 0 || actionIdx >= actions.length) actionIdx = 0;
        if (posIdx < 0 || posIdx >= positions.length) posIdx = 0;

        if (btnStillWatchingToggle != null) btnStillWatchingToggle.setText(I18n.get(context, R.string.status_prefix, active ? I18n.get(context, R.string.status_enabled) : I18n.get(context, R.string.status_disabled)));
        if (txtStillWatchingInterval != null) txtStillWatchingInterval.setText(interval + "m");
        if (txtStillWatchingTimeout != null) txtStillWatchingTimeout.setText(timeout + "s");
        if (btnStillWatchingBeepToggle != null) {
            btnStillWatchingBeepToggle.setText(I18n.get(context, R.string.still_watching_beep, beepActive ? "[ " + I18n.get(context, R.string.status_enabled) + " ]" : "[ " + I18n.get(context, R.string.status_disabled) + " ]"));
            btnStillWatchingBeepToggle.setTextColor(beepActive ? 0xFF4CAF50 : 0xFFFF5252);
        }
        if (txtStillWatchingBeepInterval != null) txtStillWatchingBeepInterval.setText(beepInterval + "s");
        if (txtStillWatchingBeepDelay != null) txtStillWatchingBeepDelay.setText(beepDelay + "s");
        if (txtStillWatchingBeepVol != null) txtStillWatchingBeepVol.setText(beepVol + "%");
        if (btnStillWatchingBeepTone != null) btnStillWatchingBeepTone.setText("   " + (tones.length > 0 ? tones[toneIdx] : ""));
        if (btnStillWatchingActionType != null) btnStillWatchingActionType.setText("   " + (actions.length > 0 ? actions[actionIdx] : ""));
        if (btnStillWatchingPosition != null) btnStillWatchingPosition.setText(I18n.get(context, R.string.clock_position, positions.length > 0 ? positions[posIdx] : ""));
        if (txtStillWatchingAlpha != null) txtStillWatchingAlpha.setText(alpha + "%");
        if (txtStillWatchingSize != null) txtStillWatchingSize.setText(sizeSp + "sp");
        if (txtStillWatchingX != null) txtStillWatchingX.setText(posX + "dp");
        if (txtStillWatchingY != null) txtStillWatchingY.setText(posY + "dp");

        if (btnApplyStillWatching != null) btnApplyStillWatching.setText(active ? I18n.get(context, R.string.still_watching_apply_disable) : I18n.get(context, R.string.still_watching_apply_enable));
    }

    private void updateNightScheduleConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        boolean active = op.getBoolean(ButtonMappingService.KEY_NIGHT_SCHEDULE, false);
        int start = op.getInt(ButtonMappingService.KEY_NIGHT_START, 22);
        int end = op.getInt(ButtonMappingService.KEY_NIGHT_END, 7);
        int blueLight = op.getInt(ButtonMappingService.KEY_NIGHT_BLUE_LIGHT, 40);
        int dimmer = op.getInt(ButtonMappingService.KEY_NIGHT_DIMMER, 50);

        if (btnNightScheduleToggle != null) btnNightScheduleToggle.setText(I18n.get(context, R.string.status_prefix, active ? I18n.get(context, R.string.status_enabled) : I18n.get(context, R.string.status_disabled)));
        if (txtNightStart != null) txtNightStart.setText(String.format(java.util.Locale.US, "%02d:00", start));
        if (txtNightEnd != null) txtNightEnd.setText(String.format(java.util.Locale.US, "%02d:00", end));
        if (txtNightBlueLight != null) txtNightBlueLight.setText(blueLight + "%");
        if (txtNightDimmer != null) txtNightDimmer.setText(dimmer + "%");
        if (btnApplyNightSchedule != null) btnApplyNightSchedule.setText(active ? I18n.get(context, R.string.night_apply_disable) : I18n.get(context, R.string.night_apply_enable));
    }

    private void updateOledSaverConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        boolean active = op.getBoolean(ButtonMappingService.KEY_OLED_SAVER, false);
        int mins = op.getInt(ButtonMappingService.KEY_OLED_MINUTES, 5);
        int mode = op.getInt(ButtonMappingService.KEY_OLED_MODE, 0);

        String modeText = (mode == 1) ? I18n.get(context, R.string.mode_black_screen) : I18n.get(context, R.string.mode_dimmer_95);

        if (btnOledSaverToggle != null) btnOledSaverToggle.setText(I18n.get(context, R.string.status_prefix, active ? I18n.get(context, R.string.status_enabled) : I18n.get(context, R.string.status_disabled)));
        if (txtOledMinutes != null) txtOledMinutes.setText(mins + "m");
        if (btnOledMode != null) btnOledMode.setText(I18n.get(context, R.string.oled_mode_prefix, modeText));
        if (btnApplyOledSaver != null) btnApplyOledSaver.setText(active ? I18n.get(context, R.string.oled_apply_disable) : I18n.get(context, R.string.oled_apply_enable));
    }

    private void cycleSelectedScheduledAlarm(int delta) {
        List<ScheduledSleepReceiver.SleepAlarm> alarms = ScheduledSleepReceiver.loadAlarms(context);
        if (alarms.isEmpty()) return;
        int size = alarms.size();
        selectedScheduledAlarmIndex = (selectedScheduledAlarmIndex + delta) % size;
        if (selectedScheduledAlarmIndex < 0) selectedScheduledAlarmIndex += size;
        updateScheduledSleepConfigPanel();
        buildMenu();
    }

    private void addScheduledAlarm() {
        List<ScheduledSleepReceiver.SleepAlarm> alarms = ScheduledSleepReceiver.loadAlarms(context);
        int nextHour = 23;
        int nextMin = 30;
        if (!alarms.isEmpty()) {
            ScheduledSleepReceiver.SleepAlarm last = alarms.get(alarms.size() - 1);
            nextHour = (last.hour + 1) % 24;
            nextMin = last.minute;
        }
        String newId = "alarm_" + System.currentTimeMillis();
        ScheduledSleepReceiver.SleepAlarm newAlarm = new ScheduledSleepReceiver.SleepAlarm(newId, nextHour, nextMin, "1,2,3,4,5,6,7", true);
        alarms.add(newAlarm);
        ScheduledSleepReceiver.saveAlarms(context, alarms);
        selectedScheduledAlarmIndex = alarms.size() - 1;
        sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
        updateScheduledSleepConfigPanel();
        buildMenu();
    }

    private void deleteScheduledAlarm() {
        List<ScheduledSleepReceiver.SleepAlarm> alarms = ScheduledSleepReceiver.loadAlarms(context);
        if (alarms.size() <= 1) return;
        if (selectedScheduledAlarmIndex >= 0 && selectedScheduledAlarmIndex < alarms.size()) {
            alarms.remove(selectedScheduledAlarmIndex);
            if (selectedScheduledAlarmIndex >= alarms.size()) {
                selectedScheduledAlarmIndex = alarms.size() - 1;
            }
            ScheduledSleepReceiver.saveAlarms(context, alarms);
            sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
            updateScheduledSleepConfigPanel();
            buildMenu();
            if (alarms.size() <= 1 && btnAddScheduledAlarm != null) {
                requestViewFocus(btnAddScheduledAlarm);
            }
        }
    }

    private void toggleSelectedScheduledAlarm() {
        List<ScheduledSleepReceiver.SleepAlarm> alarms = ScheduledSleepReceiver.loadAlarms(context);
        if (alarms.isEmpty()) return;
        if (selectedScheduledAlarmIndex < 0 || selectedScheduledAlarmIndex >= alarms.size()) {
            selectedScheduledAlarmIndex = 0;
        }
        ScheduledSleepReceiver.SleepAlarm a = alarms.get(selectedScheduledAlarmIndex);
        a.enabled = !a.enabled;
        getOverlayPrefs().edit().remove("scheduled_sleep_last_executed_stamp").apply();
        ScheduledSleepReceiver.saveAlarms(context, alarms);
        sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
        updateScheduledSleepConfigPanel();
        buildMenu();
    }

    private void adjustSelectedScheduledAlarmHour(int step) {
        List<ScheduledSleepReceiver.SleepAlarm> alarms = ScheduledSleepReceiver.loadAlarms(context);
        if (alarms.isEmpty()) return;
        if (selectedScheduledAlarmIndex < 0 || selectedScheduledAlarmIndex >= alarms.size()) {
            selectedScheduledAlarmIndex = 0;
        }
        ScheduledSleepReceiver.SleepAlarm a = alarms.get(selectedScheduledAlarmIndex);
        a.hour = (a.hour + step) % 24;
        if (a.hour < 0) a.hour += 24;
        a.enabled = true;
        getOverlayPrefs().edit().remove("scheduled_sleep_last_executed_stamp").apply();
        ScheduledSleepReceiver.saveAlarms(context, alarms);
        sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
        updateScheduledSleepConfigPanel();
        buildMenu();
    }

    private void adjustSelectedScheduledAlarmMinute(int step) {
        List<ScheduledSleepReceiver.SleepAlarm> alarms = ScheduledSleepReceiver.loadAlarms(context);
        if (alarms.isEmpty()) return;
        if (selectedScheduledAlarmIndex < 0 || selectedScheduledAlarmIndex >= alarms.size()) {
            selectedScheduledAlarmIndex = 0;
        }
        ScheduledSleepReceiver.SleepAlarm a = alarms.get(selectedScheduledAlarmIndex);
        a.minute = (a.minute + step) % 60;
        if (a.minute < 0) a.minute += 60;
        a.enabled = true;
        getOverlayPrefs().edit().remove("scheduled_sleep_last_executed_stamp").apply();
        ScheduledSleepReceiver.saveAlarms(context, alarms);
        sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
        updateScheduledSleepConfigPanel();
        buildMenu();
    }

    private void toggleSelectedScheduledAlarmDay(int day) {
        List<ScheduledSleepReceiver.SleepAlarm> alarms = ScheduledSleepReceiver.loadAlarms(context);
        if (alarms.isEmpty()) return;
        if (selectedScheduledAlarmIndex < 0 || selectedScheduledAlarmIndex >= alarms.size()) {
            selectedScheduledAlarmIndex = 0;
        }
        ScheduledSleepReceiver.SleepAlarm a = alarms.get(selectedScheduledAlarmIndex);
        java.util.Set<Integer> set = new java.util.HashSet<>();
        for (String d : (a.days != null ? a.days : "1,2,3,4,5,6,7").split(",")) {
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
        a.days = sb.toString();
        ScheduledSleepReceiver.saveAlarms(context, alarms);
        sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
        updateScheduledSleepConfigPanel();
        buildMenu();
    }

    private void toggleSelectedScheduledAlarmSkip() {
        List<ScheduledSleepReceiver.SleepAlarm> alarms = ScheduledSleepReceiver.loadAlarms(context);
        if (alarms.isEmpty()) return;
        if (selectedScheduledAlarmIndex < 0 || selectedScheduledAlarmIndex >= alarms.size()) {
            selectedScheduledAlarmIndex = 0;
        }
        ScheduledSleepReceiver.SleepAlarm a = alarms.get(selectedScheduledAlarmIndex);
        Calendar cal = ScheduledSleepReceiver.getNextAlarmCal(a, false);
        if (cal == null) return;
        String nextDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.getTime());
        if (nextDate.equals(a.skipDate) || (a.skipDate != null && !a.skipDate.isEmpty())) {
            a.skipDate = "";
        } else {
            a.skipDate = nextDate;
        }
        ScheduledSleepReceiver.saveAlarms(context, alarms);
        sendServiceAction("ACTION_UPDATE_SCHEDULED_SLEEP");
        updateScheduledSleepConfigPanel();
        buildMenu();
    }

    private void updateScheduledSleepConfigPanel() {
        List<ScheduledSleepReceiver.SleepAlarm> alarms = ScheduledSleepReceiver.loadAlarms(context);
        if (alarms.isEmpty()) {
            alarms.add(new ScheduledSleepReceiver.SleepAlarm("alarm_1", 23, 30, "1,2,3,4,5,6,7", false));
            ScheduledSleepReceiver.saveAlarms(context, alarms);
        }
        if (selectedScheduledAlarmIndex < 0) selectedScheduledAlarmIndex = 0;
        if (selectedScheduledAlarmIndex >= alarms.size()) selectedScheduledAlarmIndex = alarms.size() - 1;
        ScheduledSleepReceiver.SleepAlarm a = alarms.get(selectedScheduledAlarmIndex);

        if (txtScheduledAlarmSelector != null) {
            String status = a.enabled ? "ON" : "OFF";
            if (alarms.size() > 1) {
                txtScheduledAlarmSelector.setText(String.format(java.util.Locale.US, "◀  Alarm %d of %d: %02d:%02d (%s)  ▶",
                        selectedScheduledAlarmIndex + 1, alarms.size(), a.hour, a.minute, status));
            } else {
                txtScheduledAlarmSelector.setText(String.format(java.util.Locale.US, "Alarm 1 of 1: %02d:%02d (%s)",
                        a.hour, a.minute, status));
            }
        }

        if (btnAddScheduledAlarm != null) {
            btnAddScheduledAlarm.setText(I18n.get(context, R.string.scheduled_add_alarm));
        }

        if (btnDeleteScheduledAlarm != null) {
            btnDeleteScheduledAlarm.setText(I18n.get(context, R.string.scheduled_delete_alarm));
            btnDeleteScheduledAlarm.setVisibility(alarms.size() > 1 ? View.VISIBLE : View.GONE);
        }

        if (btnScheduledSleepToggle != null) {
            btnScheduledSleepToggle.setText("   Status:  " + (a.enabled ? "ENABLED" : "DISABLED"));
        }
        if (txtScheduledHour != null) {
            txtScheduledHour.setText(String.format(java.util.Locale.US, "%02d hs", a.hour));
        }
        if (txtScheduledMin != null) {
            txtScheduledMin.setText(String.format(java.util.Locale.US, "%02d min", a.minute));
        }

        java.util.Set<Integer> activeDays = new java.util.HashSet<>();
        for (String d : (a.days != null ? a.days : "1,2,3,4,5,6,7").split(",")) {
            try { activeDays.add(Integer.parseInt(d.trim())); } catch (Exception ignored) {}
        }

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

        Calendar cal = ScheduledSleepReceiver.getNextAlarmCal(a, false);
        String displayDateStr = "";
        if (cal != null) {
            displayDateStr = new java.text.SimpleDateFormat("dd/MM", java.util.Locale.US).format(cal.getTime());
        }
        boolean isSkipped = a.skipDate != null && !a.skipDate.isEmpty();

        if (btnScheduledSkipNext != null) {
            btnScheduledSkipNext.setText(isSkipped
                    ? "[ Next alarm skipped (" + displayDateStr + ") - Reactivate ]"
                    : "[ Skip next alarm (" + displayDateStr + ") ]");
        }

        SharedPreferences op = getOverlayPrefs();
        int promptSec = op.getInt("scheduled_sleep_prompt_sec", 60);
        String promptText = (promptSec == 0) ? "DISABLED" : promptSec + "s";
        if (btnScheduledPromptToggle != null) {
            btnScheduledPromptToggle.setText("   Pre-sleep warning prompt:  " + promptText);
        }

        if (btnApplyScheduledSleep != null) {
            btnApplyScheduledSleep.setText(a.enabled ? "[ Disable Selected Alarm ]" : "[ Enable Selected Alarm ]");
        }
    }

    public void updateMenuInternalFilters() {
        if (rootView == null) return;
        SharedPreferences op = getOverlayPrefs();
        boolean isDimmerOn = op.getBoolean(ButtonMappingService.KEY_DIMMER, false);
        boolean isBlueLightOn = op.getBoolean(ButtonMappingService.KEY_BLUE_LIGHT, false);

        if (menuDimmerFilter != null) {
            if (isDimmerOn) {
                int pct = op.getInt("dimmer_brightness_pct", 50);
                int alphaVal = (int) ((100 - pct) * 2.55);
                menuDimmerFilter.setBackgroundColor(Color.argb(alphaVal, 0, 0, 0));
                menuDimmerFilter.setVisibility(View.VISIBLE);
            } else {
                menuDimmerFilter.setVisibility(View.GONE);
            }
        }

        if (menuBlueLightFilter != null) {
            if (isBlueLightOn) {
                int pct = op.getInt("blue_light_pct", 50);
                if (pct == 0) pct = 50;
                int alpha = (int) ((pct / 1000.0f) * 150);
                if (alpha < 1) alpha = 1;
                menuBlueLightFilter.setBackgroundColor(Color.argb(alpha, 240, 120, 0));
                menuBlueLightFilter.setVisibility(View.VISIBLE);
            } else {
                menuBlueLightFilter.setVisibility(View.GONE);
            }
        }
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

    private void cycleStillWatchingAction(int delta) {
        int cur = getOverlayPrefs().getInt(ButtonMappingService.KEY_STILL_WATCHING_ACTION, 0);
        int next = (cur + delta) % STILL_WATCHING_ACTIONS.length;
        if (next < 0) next += STILL_WATCHING_ACTIONS.length;
        getOverlayPrefs().edit().putInt(ButtonMappingService.KEY_STILL_WATCHING_ACTION, next).apply();
        updateStillWatchingConfigPanel();
        sendServiceAction("ACTION_UPDATE_STILL_WATCHING");
        if (btnStillWatchingActionType != null) btnStillWatchingActionType.requestFocus();
    }

    private void cycleStillWatchingPosition(int delta) {
        int cur = getOverlayPrefs().getInt(ButtonMappingService.KEY_STILL_WATCHING_POS, 0);
        int next = (cur + delta) % STILL_WATCHING_POSITIONS.length;
        if (next < 0) next += STILL_WATCHING_POSITIONS.length;
        getOverlayPrefs().edit().putInt(ButtonMappingService.KEY_STILL_WATCHING_POS, next).apply();
        updateStillWatchingConfigPanel();
        sendServiceAction("ACTION_UPDATE_STILL_WATCHING");
        if (btnStillWatchingPosition != null) btnStillWatchingPosition.requestFocus();
    }

    private void setupAppToggleListener(final TextView btn, final String prefKey, final boolean def) {
        if (btn == null) return;
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean cur = getOverlayPrefs().getBoolean(prefKey, def);
                getOverlayPrefs().edit().putBoolean(prefKey, !cur).apply();
                sendServiceAction("ACTION_UPDATE_MINDFUL_DELAY");
                updateMindfulDelayConfigPanel();
            }
        });
    }

    private void adjustMindfulMinutes(int delta) {
        SharedPreferences op = getOverlayPrefs();
        int totalSecs = op.getInt("mindful_delay_seconds", 60);
        int m = totalSecs / 60;
        int s = totalSecs % 60;
        m += delta;
        if (m < 0) m = 0;
        if (m > 10) m = 10;
        int newTotal = m * 60 + s;
        if (newTotal < 5) newTotal = 5;
        op.edit().putInt("mindful_delay_seconds", newTotal).apply();
        sendServiceAction("ACTION_UPDATE_MINDFUL_DELAY");
        updateMindfulDelayConfigPanel();
        buildMenu();
    }

    private void adjustMindfulSeconds(int delta) {
        SharedPreferences op = getOverlayPrefs();
        int totalSecs = op.getInt("mindful_delay_seconds", 60);
        int m = totalSecs / 60;
        int s = totalSecs % 60;
        s += delta;
        if (s < 0) {
            if (m > 0) {
                m--;
                s = 59;
            } else {
                s = 5;
            }
        } else if (s > 59) {
            if (m < 10) {
                m++;
                s = 0;
            } else {
                s = 59;
            }
        }
        int newTotal = m * 60 + s;
        if (newTotal < 5) newTotal = 5;
        op.edit().putInt("mindful_delay_seconds", newTotal).apply();
        sendServiceAction("ACTION_UPDATE_MINDFUL_DELAY");
        updateMindfulDelayConfigPanel();
        buildMenu();
    }

    private void updateMindfulDelayConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        boolean enabled = op.getBoolean(ButtonMappingService.KEY_MINDFUL_DELAY, false);
        int secs = op.getInt("mindful_delay_seconds", 60);
        int cancelAction = op.getInt("mindful_delay_cancel_action", 0);
        int sessionMode = op.getInt("mindful_delay_session_mode", 0);
        int sessionHours = op.getInt("mindful_delay_session_hours", 0);
        int sessionMins = op.getInt("mindful_delay_session_mins", 30);
        int posIdx = op.getInt("mindful_delay_pos_idx", 0);
        int msgIdx = op.getInt("mindful_delay_msg_idx", 0);
        int bgAlpha = op.getInt("mindful_delay_bg_alpha_pct", 90);
        int textSize = op.getInt("mindful_delay_text_size_sp", 16);
        int pad = op.getInt("mindful_delay_pad_dp", 16);
        int posX = op.getInt("mindful_delay_pos_x_dp", 0);
        int posY = op.getInt("mindful_delay_pos_y_dp", 0);

        String[] cancelActions = I18n.getStringArray(context, R.array.mindful_cancel_actions);
        String[] sessionNames = I18n.getStringArray(context, R.array.mindful_session_names);
        String[] positions = I18n.getStringArray(context, R.array.mindful_positions);
        String[] msgOptions = I18n.getStringArray(context, R.array.mindful_msg_options);

        if (btnMindfulDelayToggle != null) {
            btnMindfulDelayToggle.setText(I18n.get(context, R.string.mindful_toggle, enabled ? "[ " + I18n.get(context, R.string.status_enabled) + " ]" : "[ " + I18n.get(context, R.string.status_disabled) + " ]"));
            btnMindfulDelayToggle.setTextColor(enabled ? 0xFF4CAF50 : 0xFFB0BEC5);
        }

        int m = secs / 60;
        int s = secs % 60;
        if (txtMindfulDelayMin != null) txtMindfulDelayMin.setText(m + " min");
        if (txtMindfulDelaySec != null) txtMindfulDelaySec.setText(String.format(java.util.Locale.US, "%02d s", s));

        if (btnMindfulDelayCancelAction != null) {
            String actName = (cancelAction >= 0 && cancelAction < cancelActions.length) ? cancelActions[cancelAction] : (cancelActions.length > 0 ? cancelActions[0] : "");
            btnMindfulDelayCancelAction.setText(I18n.get(context, R.string.mindful_cancel_action, actName));
        }

        if (btnMindfulDelaySession != null) {
            String sessName = (sessionMode >= 0 && sessionMode < sessionNames.length) ? sessionNames[sessionMode] : (sessionNames.length > 0 ? sessionNames[0] : "");
            btnMindfulDelaySession.setText(I18n.get(context, R.string.mindful_session_mode, sessName));
        }

        if (rowMindfulSessHours != null) rowMindfulSessHours.setVisibility(sessionMode == 0 ? View.VISIBLE : View.GONE);
        if (rowMindfulSessMins != null) rowMindfulSessMins.setVisibility(sessionMode == 0 ? View.VISIBLE : View.GONE);
        if (txtMindfulDelaySessHour != null) txtMindfulDelaySessHour.setText(sessionHours + " hs");
        if (txtMindfulDelaySessMin != null) txtMindfulDelaySessMin.setText(sessionMins + " min");

        if (btnMindfulDelayPos != null) {
            String posName = (posIdx >= 0 && posIdx < positions.length) ? positions[posIdx] : (positions.length > 0 ? positions[0] : "");
            btnMindfulDelayPos.setText(I18n.get(context, R.string.clock_position, posName));
        }

        if (btnMindfulDelayMsg != null) {
            String msgName = (msgIdx >= 0 && msgIdx < msgOptions.length) ? msgOptions[msgIdx] : (msgOptions.length > 0 ? msgOptions[0] : "");
            btnMindfulDelayMsg.setText("   \"" + (msgName.length() > 30 ? msgName.substring(0, 27) + "..." : msgName) + "\"");
        }

        if (txtMindfulDelayBgAlpha != null) txtMindfulDelayBgAlpha.setText(bgAlpha + "%");
        if (txtMindfulDelayTextSize != null) txtMindfulDelayTextSize.setText(textSize + "sp");
        if (txtMindfulDelayPad != null) txtMindfulDelayPad.setText(pad + "dp");
        if (txtMindfulDelayX != null) txtMindfulDelayX.setText(posX + "dp");
        if (txtMindfulDelayY != null) txtMindfulDelayY.setText(posY + "dp");

        updateAppButton(btnMindfulAppYoutube, "📺  YouTube", op.getBoolean("mindful_app_youtube", true));
        updateAppButton(btnMindfulAppNetflix, "🎬  Netflix", op.getBoolean("mindful_app_netflix", true));
        updateAppButton(btnMindfulAppDisney, "🏰  Disney+", op.getBoolean("mindful_app_disney", true));
        updateAppButton(btnMindfulAppPrime, "📦  Prime Video", op.getBoolean("mindful_app_prime", false));
        updateAppButton(btnMindfulAppMax, "🍿  Max (HBO)", op.getBoolean("mindful_app_max", false));
        updateAppButton(btnMindfulAppStar, "⭐  Star+", op.getBoolean("mindful_app_star", false));
        updateAppButton(btnMindfulAppTwitch, "🟣  Twitch", op.getBoolean("mindful_app_twitch", false));
        updateAppButton(btnMindfulAppTiktok, "🎵  TikTok", op.getBoolean("mindful_app_tiktok", false));
        updateAppButton(btnMindfulAppSmarttube, "🚀  SmartTube", op.getBoolean("mindful_app_smarttube", false));
        updateAppButton(btnMindfulAppStremio, "🎥  Stremio", op.getBoolean("mindful_app_stremio", false));
        updateAppButton(btnMindfulAppPlex, "🔮  Plex", op.getBoolean("mindful_app_plex", false));
    }

    private void updateAppButton(TextView btn, String name, boolean active) {
        if (btn == null) return;
        String flag = active ? I18n.get(context, R.string.yes_text) : I18n.get(context, R.string.no_text);
        btn.setText(name + ":   [ " + flag + " ]");
        btn.setTextColor(active ? 0xFF81D4FA : 0xFF888888);
    }

    private void updateTranslateConfigPanel() {
        SharedPreferences op = getOverlayPrefs();
        int targetIdx = op.getInt("translate_target_lang_idx", 0);
        int srcIdx = op.getInt("translate_source_lang_idx", 0);
        boolean autoPause = op.getBoolean("translate_auto_pause", true);
        boolean autoResume = op.getBoolean("translate_auto_resume", true);
        boolean showTopBar = op.getBoolean("translate_show_top_bar", false);
        int alpha = op.getInt("translate_bg_alpha_pct", 85);
        int size = op.getInt("translate_text_size_sp", 14);

        if (targetIdx < 0 || targetIdx >= TRANSLATE_TARGET_LANGS.length) targetIdx = 0;
        if (srcIdx < 0 || srcIdx >= TRANSLATE_SOURCE_LANGS.length) srcIdx = 0;

        if (btnTranslateTargetLang != null) btnTranslateTargetLang.setText(I18n.get(context, R.string.translate_target_lang, TRANSLATE_TARGET_LANGS[targetIdx]));
        if (btnTranslateSourceLang != null) btnTranslateSourceLang.setText("   Source Language:  " + TRANSLATE_SOURCE_LANGS[srcIdx]);
        if (btnTranslateAutoPause != null) btnTranslateAutoPause.setText("   Pause on Start:  " + (autoPause ? I18n.get(context, R.string.yes_text) : I18n.get(context, R.string.no_text)));
        if (btnTranslateAutoResume != null) btnTranslateAutoResume.setText("   Resume on OK:  " + (autoResume ? I18n.get(context, R.string.yes_text) : I18n.get(context, R.string.no_text)));
        if (btnTranslateTopBar != null) btnTranslateTopBar.setText(I18n.get(context, R.string.translate_top_bar, showTopBar ? I18n.get(context, R.string.status_on) : I18n.get(context, R.string.status_off)));
        if (txtTranslateBgAlpha != null) txtTranslateBgAlpha.setText(alpha + "%");
        if (txtTranslateTextSize != null) txtTranslateTextSize.setText(size + "sp");
    }

    private void updateButtonCombosPanel() {
        SharedPreferences op = getOverlayPrefs();
        boolean enabled = op.getBoolean("btn_combos_enabled", true);
        int muteOk = op.getInt("combo_mute_ok_action", 23);
        int muteRight = op.getInt("combo_mute_right_action", 24);
        int muteLeft = op.getInt("combo_mute_left_action", 25);
        int muteUp = op.getInt("combo_mute_up_action", 0);
        int muteDown = op.getInt("combo_mute_down_action", 0);
        int ytMute = op.getInt("combo_youtube190_mute_action", 0);
        int inputOk = op.getInt("combo_input_ok_action", 0);

        if (btnCombosMasterToggle != null) {
            btnCombosMasterToggle.setText(I18n.get(context, R.string.combos_master, enabled ? "[ " + I18n.get(context, R.string.status_enabled) + " ]" : "[ " + I18n.get(context, R.string.status_disabled) + " ]"));
            btnCombosMasterToggle.setTextColor(enabled ? 0xFF4CAF50 : 0xFFFF5252);
        }
        if (btnComboMuteOk != null) {
            btnComboMuteOk.setText(I18n.get(context, R.string.combo_mute_ok, muteOk == 0 ? "[ " + I18n.get(context, R.string.status_disabled) + " ]" : getActionName(muteOk)));
            btnComboMuteOk.setTextColor(muteOk == 0 ? 0xFF888888 : 0xFF81D4FA);
        }
        if (btnComboMuteRight != null) {
            btnComboMuteRight.setText(I18n.get(context, R.string.combo_mute_right, muteRight == 0 ? "[ " + I18n.get(context, R.string.status_disabled) + " ]" : getActionName(muteRight)));
            btnComboMuteRight.setTextColor(muteRight == 0 ? 0xFF888888 : 0xFF81D4FA);
        }
        if (btnComboMuteLeft != null) {
            btnComboMuteLeft.setText(I18n.get(context, R.string.combo_mute_left, muteLeft == 0 ? "[ " + I18n.get(context, R.string.status_disabled) + " ]" : getActionName(muteLeft)));
            btnComboMuteLeft.setTextColor(muteLeft == 0 ? 0xFF888888 : 0xFF81D4FA);
        }
        if (btnComboMuteUp != null) {
            btnComboMuteUp.setText(I18n.get(context, R.string.combo_mute_up, muteUp == 0 ? "[ " + I18n.get(context, R.string.status_disabled) + " ]" : getActionName(muteUp)));
            btnComboMuteUp.setTextColor(muteUp == 0 ? 0xFF888888 : 0xFF81D4FA);
        }
        if (btnComboMuteDown != null) {
            btnComboMuteDown.setText(I18n.get(context, R.string.combo_mute_down, muteDown == 0 ? "[ " + I18n.get(context, R.string.status_disabled) + " ]" : getActionName(muteDown)));
            btnComboMuteDown.setTextColor(muteDown == 0 ? 0xFF888888 : 0xFF81D4FA);
        }
        if (btnComboYoutube190Mute != null) {
            btnComboYoutube190Mute.setText(I18n.get(context, R.string.combo_yt_mute, ytMute == 0 ? "[ " + I18n.get(context, R.string.status_disabled) + " ]" : getActionName(ytMute)));
            btnComboYoutube190Mute.setTextColor(ytMute == 0 ? 0xFF888888 : 0xFF81D4FA);
        }
        if (btnComboInputOk != null) {
            btnComboInputOk.setText(I18n.get(context, R.string.combo_input_ok, inputOk == 0 ? "[ " + I18n.get(context, R.string.status_disabled) + " ]" : getActionName(inputOk)));
            btnComboInputOk.setTextColor(inputOk == 0 ? 0xFF888888 : 0xFF81D4FA);
        }
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

    public static void playStillWatchingBeepSound() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int volPct = 65;
                    int toneType = 0;
                    ButtonMappingService svc = ButtonMappingService.instance;
                    if (svc != null) {
                        SharedPreferences sp = svc.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE);
                        volPct = sp.getInt(ButtonMappingService.KEY_STILL_WATCHING_BEEP_VOLUME, 65);
                        toneType = sp.getInt(ButtonMappingService.KEY_STILL_WATCHING_BEEP_TONE, 0);
                    }
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
                } catch (Exception ignored) {}
            }
        }).start();
    }
}
