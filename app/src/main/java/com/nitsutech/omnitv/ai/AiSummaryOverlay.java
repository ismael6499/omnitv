package com.nitsutech.omnitv.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nitsutech.omnitv.ButtonMappingService;
import com.nitsutech.omnitv.MediaNotificationListener;
import com.nitsutech.omnitv.R;
import com.nitsutech.omnitv.vot.VotCue;
import com.nitsutech.omnitv.vot.VotTrack;
import com.nitsutech.omnitv.vot.YouTubeCaptionFetcher;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class AiSummaryOverlay {
    private static final String TAG = "AiSummaryOverlay";
    private static AiSummaryOverlay instance;

    private View overlayView;
    private WindowManager windowManager;
    private boolean isShowing = false;

    private TextView textVideoTitle;
    private TextView textStatus;
    private ScrollView scrollContent;
    private TextView textAiWelcome;
    private LinearLayout containerAiChips;
    private LinearLayout layoutSuggestedSection;
    private LinearLayout containerSuggested;
    private TextView btnSuggested1;
    private TextView btnSuggested2;
    private TextView btnSuggested3;
    private View currentThinkingView;
    private TextView btnAiReset;
    private TextView btnAiMic;
    private TextView btnAiPlayPause;
    private TextView btnAiChatLang;
    private TextView btnPillVisionScan;

    // Vision Panel views
    private LinearLayout panelAiVisionOptions;
    private TextView txtVisionHeader;
    private TextView btnVisionOptTranslate;
    private TextView btnVisionTargetLang;
    private TextView btnVisionOptObjects;
    private TextView btnVisionOptPlaces;
    private TextView btnVisionClose;
    private FrameLayout lensOverlayWindowView = null;
    private List<AiSummaryEngine.LensBoxItem> activeLensBoxes = new ArrayList<>();
    private final List<View> renderedLensBadgeViews = new ArrayList<>();
    private int selectedLensBadgeIndex = -1;

    // Internal Filters
    private View aiMenuDimmerFilter;
    private View aiMenuBlueLightFilter;

    // Language state
    private String uiLanguage = "es";
    private String chatLanguage = "es";
    private boolean visionTargetLangIsEnglish = true;
    private View currentListeningCard;
    private TextView textListeningStatus;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private final Set<String> askedQuestionsSet = new HashSet<>();

    // Persistent conversation state per video
    private static class ChatTurn {
        final boolean isUser;
        final String text;
        final List<AiSummaryEngine.AiPointItem> items;
        final List<String> suggestedQuestions;
        final List<AiSummaryEngine.LensBoxItem> lensBoxes;

        ChatTurn(boolean isUser, String text, List<AiSummaryEngine.AiPointItem> items, List<String> suggestedQuestions, List<AiSummaryEngine.LensBoxItem> lensBoxes) {
            this.isUser = isUser;
            this.text = text;
            this.items = items;
            this.suggestedQuestions = suggestedQuestions;
            this.lensBoxes = lensBoxes != null ? lensBoxes : Collections.emptyList();
        }

        ChatTurn(boolean isUser, String text, List<AiSummaryEngine.AiPointItem> items, List<String> suggestedQuestions) {
            this(isUser, text, items, suggestedQuestions, null);
        }
    }

    private String lastVideoTitle = "";
    private String lastVideoId = "";
    private String lastTranscript = "";
    private final List<AiSummaryEngine.ChatMessage> conversationHistory = new ArrayList<>();
    private final List<ChatTurn> recordedTurns = new ArrayList<>();
    private List<String> lastSuggestedQuestions = new ArrayList<>();

    private String currentVideoTitle = "";
    private String currentVideoId = "";
    private String currentTranscript = "";
    private String currentTrackLang = "";
    private final ExecutorService fetcherExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler mHoldHandler = new Handler(Looper.getMainLooper());
    private int mHoldingKeyCode = -1;
    private int mHoldTickCount = 0;
    private Runnable mHoldRunnable;

    public static synchronized AiSummaryOverlay getInstance() {
        if (instance == null) {
            instance = new AiSummaryOverlay();
        }
        return instance;
    }

    public synchronized void show(Context context) {
        if (isShowing) {
            hide();
            return;
        }

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater = LayoutInflater.from(context);
        overlayView = inflater.inflate(R.layout.view_ai_summary_overlay, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        bindViews(context);
        updateInternalFilters(context);
        updatePlayPauseButtonState();
        applyUiLanguage(context);
        setupKeyListeners();
        detectVideoAndLoadTranscript(context);

        try {
            windowManager.addView(overlayView, params);
            isShowing = true;
            mainHandler.removeCallbacks(videoPollRunnable);
            mainHandler.postDelayed(videoPollRunnable, 1500);
            Log.d(TAG, "AiSummaryOverlay displayed successfully");

            if (isLensOverlayShowing()) {
                renderLensBadgesIntoContainer(lensOverlayWindowView, activeLensBoxes, context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding AiSummaryOverlay to WindowManager", e);
        }
    }

    public synchronized void hideOnlyDrawer() {
        if (!isShowing || overlayView == null || windowManager == null) return;
        stopHoldRepeat();
        mainHandler.removeCallbacks(videoPollRunnable);
        stopVoiceInput();
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        try {
            windowManager.removeView(overlayView);
        } catch (Exception e) {
            Log.e(TAG, "Error removing AiSummaryOverlay", e);
        }
        overlayView = null;
        isShowing = false;
        currentThinkingView = null;
        currentListeningCard = null;
        textListeningStatus = null;

        // Re-render lens badges without the 540dp drawer offset so they expand full-screen
        if (isLensOverlayShowing() && ButtonMappingService.instance != null) {
            renderLensBadgesIntoContainer(lensOverlayWindowView, activeLensBoxes, ButtonMappingService.instance);
        }
        Log.d(TAG, "AiSummaryOverlay drawer closed, lens overlay kept on screen");
    }

    public synchronized void hide() {
        hideOnlyDrawer();
        hideLensOverlay();
    }

    public void handleBackPress(Context context) {
        if (isListening) {
            stopVoiceInput();
            return;
        }
        if (panelAiVisionOptions != null && panelAiVisionOptions.getVisibility() == View.VISIBLE) {
            panelAiVisionOptions.setVisibility(View.GONE);
            if (btnPillVisionScan != null) btnPillVisionScan.requestFocus();
            return;
        }
        if (isLensOverlayShowing()) {
            // Case 1: Close assistant menu, keep Lens overlay active on screen!
            hideOnlyDrawer();
            boolean isEn = "en".equalsIgnoreCase(uiLanguage);
            Toast.makeText(context, isEn ? "🔤 Press Back to return to AI menu" : "🔤 Presiona Atrás para volver al menú de IA", Toast.LENGTH_SHORT).show();
        } else {
            // Case 3: Close assistant menu and return to video
            hide();
        }
    }

    public boolean isLensOverlayShowing() {
        return lensOverlayWindowView != null && lensOverlayWindowView.isAttachedToWindow() && lensOverlayWindowView.getVisibility() == View.VISIBLE;
    }

    public synchronized void showLensOverlay(List<AiSummaryEngine.LensBoxItem> boxes, Context context) {
        if (context == null || boxes == null || boxes.isEmpty()) return;
        activeLensBoxes = new ArrayList<>(boxes);

        if (windowManager == null) {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }

        if (lensOverlayWindowView == null) {
            lensOverlayWindowView = new FrameLayout(context);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            renderLensBadgesIntoContainer(lensOverlayWindowView, activeLensBoxes, context);
            try {
                windowManager.addView(lensOverlayWindowView, lp);
                Log.d(TAG, "lensOverlayWindowView added to WindowManager (" + activeLensBoxes.size() + " badges)");
            } catch (Exception e) {
                Log.e(TAG, "Error adding lensOverlayWindowView", e);
            }
        } else {
            lensOverlayWindowView.removeAllViews();
            renderLensBadgesIntoContainer(lensOverlayWindowView, activeLensBoxes, context);
            lensOverlayWindowView.setVisibility(View.VISIBLE);
        }
    }

    public synchronized void hideLensOverlay() {
        selectedLensBadgeIndex = -1;
        renderedLensBadgeViews.clear();
        if (lensOverlayWindowView != null && windowManager != null) {
            try {
                if (lensOverlayWindowView.isAttachedToWindow()) {
                    windowManager.removeView(lensOverlayWindowView);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing lensOverlayWindowView", e);
            }
            lensOverlayWindowView = null;
            Log.d(TAG, "lensOverlayWindowView removed from WindowManager");
        }
    }

    public synchronized void toggleLensOverlay(List<AiSummaryEngine.LensBoxItem> boxes, Context context) {
        if (isLensOverlayShowing()) {
            hideLensOverlay();
            boolean isEn = "en".equalsIgnoreCase(uiLanguage);
            Toast.makeText(context, isEn ? "👁️ Screen translations hidden" : "👁️ Traducciones en pantalla ocultas", Toast.LENGTH_SHORT).show();
        } else {
            List<AiSummaryEngine.LensBoxItem> target = (boxes != null && !boxes.isEmpty()) ? boxes : activeLensBoxes;
            showLensOverlay(target, context);
            boolean isEn = "en".equalsIgnoreCase(uiLanguage);
            Toast.makeText(context, isEn ? "👁️ Screen translations active" : "👁️ Traducciones en pantalla activadas", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isShowing() {
        return isShowing;
    }

    private void bindViews(Context context) {
        textVideoTitle = overlayView.findViewById(R.id.text_ai_video_title);
        textStatus = overlayView.findViewById(R.id.text_ai_status);
        scrollContent = overlayView.findViewById(R.id.scroll_ai_content);
        textAiWelcome = overlayView.findViewById(R.id.text_ai_welcome);
        containerAiChips = overlayView.findViewById(R.id.container_ai_chips);
        layoutSuggestedSection = overlayView.findViewById(R.id.layout_suggested_section);
        containerSuggested = overlayView.findViewById(R.id.container_suggested_questions);
        btnSuggested1 = overlayView.findViewById(R.id.btn_suggested_1);
        btnSuggested2 = overlayView.findViewById(R.id.btn_suggested_2);
        btnSuggested3 = overlayView.findViewById(R.id.btn_suggested_3);

        View btnClose = overlayView.findViewById(R.id.btn_ai_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> hide());
        }

        btnAiReset = overlayView.findViewById(R.id.btn_ai_reset);
        if (btnAiReset != null) {
            btnAiReset.setOnClickListener(v -> resetConversation(context));
            btnAiReset.setVisibility(!recordedTurns.isEmpty() ? View.VISIBLE : View.GONE);
        }

        btnAiMic = overlayView.findViewById(R.id.btn_ai_mic);
        if (btnAiMic != null) {
            btnAiMic.setOnClickListener(v -> toggleVoiceInput(context));
        }

        btnAiPlayPause = overlayView.findViewById(R.id.btn_ai_play_pause);
        if (btnAiPlayPause != null) {
            btnAiPlayPause.setOnClickListener(v -> {
                boolean nowPlaying = MediaNotificationListener.togglePlayPause(context);
                btnAiPlayPause.setText(nowPlaying ? "⏸️" : "▶️");
            });
        }

        btnAiChatLang = overlayView.findViewById(R.id.btn_ai_chat_lang);
        if (btnAiChatLang != null) {
            btnAiChatLang.setOnClickListener(v -> {
                String nextLang = "es".equalsIgnoreCase(chatLanguage) ? "en" : "es";
                setChatLanguage(nextLang);
                Toast.makeText(context, "🌐 " + ("es".equalsIgnoreCase(nextLang) ? "Idioma del chat: Español" : "Chat language: English"), Toast.LENGTH_SHORT).show();
            });
        }

        // Vision Subpanel Views
        panelAiVisionOptions = overlayView.findViewById(R.id.panel_ai_vision_options);
        txtVisionHeader = overlayView.findViewById(R.id.txt_vision_header);
        btnVisionOptTranslate = overlayView.findViewById(R.id.btn_vision_opt_translate);
        btnVisionTargetLang = overlayView.findViewById(R.id.btn_vision_target_lang);
        btnVisionOptObjects = overlayView.findViewById(R.id.btn_vision_opt_objects);
        btnVisionOptPlaces = overlayView.findViewById(R.id.btn_vision_opt_places);
        btnVisionClose = overlayView.findViewById(R.id.btn_vision_close);

        aiMenuDimmerFilter = overlayView.findViewById(R.id.ai_menu_dimmer_filter);
        aiMenuBlueLightFilter = overlayView.findViewById(R.id.ai_menu_blue_light_filter);

        btnPillVisionScan = overlayView.findViewById(R.id.btn_pill_vision_scan);
        if (btnPillVisionScan != null) {
            btnPillVisionScan.setOnClickListener(v -> {
                if (panelAiVisionOptions != null) {
                    boolean isVis = panelAiVisionOptions.getVisibility() == View.VISIBLE;
                    panelAiVisionOptions.setVisibility(isVis ? View.GONE : View.VISIBLE);
                    if (!isVis && btnVisionOptTranslate != null) {
                        btnVisionOptTranslate.requestFocus();
                    }
                }
            });
        }

        if (btnVisionClose != null) {
            btnVisionClose.setOnClickListener(v -> {
                if (panelAiVisionOptions != null) {
                    panelAiVisionOptions.setVisibility(View.GONE);
                }
                if (btnPillVisionScan != null) {
                    btnPillVisionScan.requestFocus();
                }
            });
        }

        if (btnVisionTargetLang != null) {
            btnVisionTargetLang.setOnClickListener(v -> {
                visionTargetLangIsEnglish = !visionTargetLangIsEnglish;
                btnVisionTargetLang.setText(visionTargetLangIsEnglish ? "🌐 Destino: EN" : "🌐 Destino: ES");
            });
        }

        if (btnVisionOptTranslate != null) {
            btnVisionOptTranslate.setOnClickListener(v -> {
                if (panelAiVisionOptions != null) panelAiVisionOptions.setVisibility(View.GONE);
                boolean isEn = "en".equalsIgnoreCase(uiLanguage);
                String targetName = visionTargetLangIsEnglish ? "English" : "Español";
                String nonTargetDesc = visionTargetLangIsEnglish
                        ? "text in foreign languages (such as Spanish, Korean, Japanese, French, German, Chinese, etc.)"
                        : "texto que NO esté en español (como inglés, coreano, japonés, francés, etc.)";
                String prompt = "Target language for translation: " + targetName + ".\n"
                        + "Analyze this TV screen frame and identify only " + nonTargetDesc + " visible in video thumbnails, signs, graphics, titles, or subtitles.\n"
                        + "Translate detected text into " + targetName + " accurately.\n"
                        + "CRITICAL FILTERING & EXCLUSION RULES:\n"
                        + "1. ONLY detect and translate text that is in a DIFFERENT language than " + targetName + ". If text is already in " + targetName + ", DO NOT detect or output it!\n"
                        + "2. STRICTLY EXCLUDE and DO NOT output bounding boxes for:\n"
                        + "   - Text already written in " + targetName + ".\n"
                        + "   - Timestamps, clocks, video durations, resolutions (e.g. 03:10, 23:05, 18:27, 4K, 1080p).\n"
                        + "   - System UI elements, WiFi, Bluetooth, battery, or notifications (e.g. 'wireless debugging').\n"
                        + "   - View counts or dates (e.g. '301K views', '1 year ago').\n"
                        + "3. The translated text MUST be an actual translation in " + targetName + ", never repeat the original text.\n"
                        + "4. Group related words on the same line into a single bounding box.\n"
                        + "Present a clear, structured table/list readable on a TV screen with: Location, Original Text, and Translated Text.\n"
                        + "Then, you MUST output the exact delimiter:\n"
                        + AiSummaryEngine.DELIMITER_LENS_BOXES + "\n"
                        + "followed by one line per detected text in format:\n"
                        + "[ymin,xmin,ymax,xmax] | Original: <text> | Translated: <text> | Location: <location>\n"
                        + "where ymin, xmin, ymax, xmax are normalized integers from 0 to 1000 representing the exact bounding box on screen.\n"
                        + AiSummaryEngine.DELIMITER_QUESTIONS + "\n"
                        + "1. [Follow-up question 1]\n"
                        + "2. [Follow-up question 2]\n"
                        + "3. [Follow-up question 3]";

                captureScreenAndExecuteVision(context, prompt, isEn ? "Translate Screen (Lens)" : "Traducir Pantalla (Lens)");
            });
        }

        if (btnVisionOptObjects != null) {
            btnVisionOptObjects.setOnClickListener(v -> {
                if (panelAiVisionOptions != null) panelAiVisionOptions.setVisibility(View.GONE);
                boolean isEn = "en".equalsIgnoreCase(uiLanguage);
                String langName = chatLanguage.equals("es") ? "español" : "English";
                String prompt = "Analyze this TV screen capture. Identify 3 to 6 key objects, items, devices, vehicles, products, or characters visible in the scene.\n"
                        + "Provide a brief description of each object in " + langName + " structured for a TV screen.\n"
                        + "Then you MUST output the exact line:\n"
                        + AiSummaryEngine.DELIMITER_OBJECTS + "\n"
                        + "followed by the names of the detected objects, one per line:\n"
                        + "- Object 1\n"
                        + "- Object 2\n"
                        + AiSummaryEngine.DELIMITER_QUESTIONS + "\n"
                        + "1. [Follow-up question 1]\n"
                        + "2. [Follow-up question 2]\n"
                        + "3. [Follow-up question 3]";

                captureScreenAndExecuteVision(context, prompt, isEn ? "Analyze Objects" : "Analizar Objetos");
            });
        }

        if (btnVisionOptPlaces != null) {
            btnVisionOptPlaces.setOnClickListener(v -> {
                if (panelAiVisionOptions != null) panelAiVisionOptions.setVisibility(View.GONE);
                boolean isEn = "en".equalsIgnoreCase(uiLanguage);
                String langName = chatLanguage.equals("es") ? "español" : "English";
                String prompt = "Analyze this TV screen capture. Identify the location, setting, landmark, city, country, or any famous people, celebrities, or public figures visible in the scene.\n"
                        + "Provide details and context in " + langName + " structured for a TV screen.\n"
                        + AiSummaryEngine.DELIMITER_QUESTIONS + "\n"
                        + "1. [Follow-up question 1]\n"
                        + "2. [Follow-up question 2]\n"
                        + "3. [Follow-up question 3]";

                captureScreenAndExecuteVision(context, prompt, isEn ? "Identify Places / People" : "Identificar Lugar / Personas");
            });
        }

        View root = overlayView.findViewById(R.id.ai_overlay_root);
        if (root != null) {
            root.setOnClickListener(v -> hide());
        }

        View drawer = overlayView.findViewById(R.id.ai_panel_drawer);
        if (drawer != null) {
            drawer.setOnClickListener(v -> { /* Consume clicks inside drawer */ });
        }

        // Quick Preset Pills (Top)
        View btnSummary = overlayView.findViewById(R.id.btn_pill_summary);
        if (btnSummary != null) {
            btnSummary.setOnClickListener(v -> executePrompt(context, "Haz un resumen completo del video estructurado en puntos clave.", false));
        }

        View btnKeyPoints = overlayView.findViewById(R.id.btn_pill_key_points);
        if (btnKeyPoints != null) {
            btnKeyPoints.setOnClickListener(v -> executePrompt(context, "¿Cuáles son los puntos y argumentos clave explicados en este video?", false));
        }

        View btnConclusions = overlayView.findViewById(R.id.btn_pill_conclusions);
        if (btnConclusions != null) {
            btnConclusions.setOnClickListener(v -> executePrompt(context, "¿Cuáles son las conclusiones finales y consejos que da el autor?", false));
        }

        View btnMoments = overlayView.findViewById(R.id.btn_pill_moments);
        if (btnMoments != null) {
            btnMoments.setOnClickListener(v -> executePrompt(context, "Detalla los momentos o temas más importantes del video con marcas de tiempo si están disponibles.", false));
        }

        // Suggested Follow-up Buttons (Bottom)
        btnSuggested1.setOnClickListener(v -> executePrompt(context, btnSuggested1.getText().toString(), false));
        btnSuggested2.setOnClickListener(v -> executePrompt(context, btnSuggested2.getText().toString(), false));
        btnSuggested3.setOnClickListener(v -> executePrompt(context, btnSuggested3.getText().toString(), false));

        btnSuggested1.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) centerViewInScrollView(v); });
        btnSuggested2.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) centerViewInScrollView(v); });
        btnSuggested3.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) centerViewInScrollView(v); });

        // Initial focus on summary pill
        if (btnSummary != null) {
            btnSummary.requestFocus();
        }
    }

    private void setupKeyListeners() {
        if (overlayView == null) return;
        overlayView.setFocusableInTouchMode(true);
        overlayView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    handleBackPress(overlayView != null ? overlayView.getContext() : ButtonMappingService.instance);
                    return true;
                }
            }
            return false;
        });
    }

    private void startHoldRepeat(final int keyCode) {
        stopHoldRepeat();
        mHoldingKeyCode = keyCode;
        mHoldTickCount = 0;

        mHoldRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isShowing || mHoldingKeyCode != keyCode || overlayView == null) return;
                mHoldTickCount++;

                handleDpadNavigation(keyCode);

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

    private void handleDpadNavigation(int keyCode) {
        if (!isShowing || overlayView == null) return;
        View current = overlayView.findFocus();
        int direction = (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) ? View.FOCUS_DOWN : View.FOCUS_UP;
        if (current != null) {
            View next = current.focusSearch(direction);
            if (next != null && next != current) {
                next.requestFocus();
                centerViewInScrollView(next);
            } else if (scrollContent != null) {
                int scrollDelta = (int) (120 * overlayView.getResources().getDisplayMetrics().density);
                scrollContent.smoothScrollBy(0, keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? scrollDelta : -scrollDelta);
            }
        }
    }

    public boolean onKeyEvent(KeyEvent event) {
        if (!isShowing || overlayView == null) return false;

        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == 231 || keyCode == KeyEvent.KEYCODE_ASSIST || keyCode == 219 || keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == 84) {
            if (action == KeyEvent.ACTION_DOWN) {
                toggleVoiceInput(overlayView.getContext());
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                handleBackPress(overlayView != null ? overlayView.getContext() : ButtonMappingService.instance);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_DOWN) {
                View focused = overlayView.findFocus();
                if (focused != null && focused.isClickable()) {
                    focused.performClick();
                    return true;
                }
            }
        }

        // Hold repeat for UP/DOWN keys
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    startHoldRepeat(keyCode);
                }
            } else if (action == KeyEvent.ACTION_UP) {
                stopHoldRepeat();
            }
        }

        if (action == KeyEvent.ACTION_DOWN) {
            View current = overlayView.findFocus();
            View headerBar = overlayView.findViewById(R.id.container_ai_header_bar);
            View actionPills = overlayView.findViewById(R.id.container_action_pills);

            // DPAD_RIGHT: Forward section progression (Header -> Pills -> Chat -> Suggested)
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (current != null) {
                    // In Header Bar: navigate right inside header; if at the end of header, jump to Action Pills!
                    if (isViewInside(current, headerBar)) {
                        if (headerBar instanceof ViewGroup) {
                            ViewGroup headerGroup = (ViewGroup) headerBar;
                            int currentIdx = headerGroup.indexOfChild(current);
                            View nextFocusable = null;
                            for (int i = currentIdx + 1; i < headerGroup.getChildCount(); i++) {
                                View child = headerGroup.getChildAt(i);
                                if (child.getVisibility() == View.VISIBLE && child.isFocusable()) {
                                    nextFocusable = child;
                                    break;
                                }
                            }
                            if (nextFocusable != null) {
                                nextFocusable.requestFocus();
                                return true;
                            }
                        }
                        View target = btnPillVisionScan != null && btnPillVisionScan.getVisibility() == View.VISIBLE
                                ? btnPillVisionScan
                                : overlayView.findViewById(R.id.btn_pill_summary);
                        if (target != null) {
                            target.requestFocus();
                            return true;
                        }
                    }
                    // In Action Pills: navigate right inside pills; if at the end of pills, jump down to Chat conversation!
                    else if (isViewInside(current, actionPills)) {
                        if (actionPills instanceof ViewGroup) {
                            ViewGroup pillsGroup = (ViewGroup) actionPills;
                            int currentIdx = pillsGroup.indexOfChild(current);
                            View nextFocusable = null;
                            for (int i = currentIdx + 1; i < pillsGroup.getChildCount(); i++) {
                                View child = pillsGroup.getChildAt(i);
                                if (child.getVisibility() == View.VISIBLE && child.isFocusable()) {
                                    nextFocusable = child;
                                    break;
                                }
                            }
                            if (nextFocusable != null) {
                                nextFocusable.requestFocus();
                                return true;
                            }
                        }
                        if (containerAiChips != null && containerAiChips.getChildCount() > 0) {
                            View firstCard = containerAiChips.getChildAt(0);
                            firstCard.requestFocus();
                            centerViewInScrollView(firstCard);
                            return true;
                        } else if (btnSuggested1 != null && btnSuggested1.getVisibility() == View.VISIBLE) {
                            btnSuggested1.requestFocus();
                            centerViewInScrollView(btnSuggested1);
                            return true;
                        }
                    }
                    // In Chat Conversation: Jump down to Suggested Questions!
                    else if (isViewInside(current, containerAiChips)) {
                        if (layoutSuggestedSection != null && layoutSuggestedSection.getVisibility() == View.VISIBLE && btnSuggested1 != null) {
                            btnSuggested1.requestFocus();
                            centerViewInScrollView(btnSuggested1);
                            return true;
                        }
                    }
                }
            }

            // DPAD_LEFT: Backward section progression (Suggested -> Chat -> Action Pills -> Header)
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (current != null) {
                    // In Header Bar: navigate left inside header
                    if (isViewInside(current, headerBar)) {
                        if (headerBar instanceof ViewGroup) {
                            ViewGroup headerGroup = (ViewGroup) headerBar;
                            int currentIdx = headerGroup.indexOfChild(current);
                            View prevFocusable = null;
                            for (int i = currentIdx - 1; i >= 0; i--) {
                                View child = headerGroup.getChildAt(i);
                                if (child.getVisibility() == View.VISIBLE && child.isFocusable()) {
                                    prevFocusable = child;
                                    break;
                                }
                            }
                            if (prevFocusable != null) {
                                prevFocusable.requestFocus();
                                return true;
                            }
                        }
                        return true;
                    }
                    // In Action Pills: navigate left inside pills row; ONLY when no more buttons to the left, jump up to Header Bar!
                    else if (isViewInside(current, actionPills)) {
                        if (actionPills instanceof ViewGroup) {
                            ViewGroup pillsGroup = (ViewGroup) actionPills;
                            int currentIdx = pillsGroup.indexOfChild(current);
                            View prevFocusable = null;
                            for (int i = currentIdx - 1; i >= 0; i--) {
                                View child = pillsGroup.getChildAt(i);
                                if (child.getVisibility() == View.VISIBLE && child.isFocusable()) {
                                    prevFocusable = child;
                                    break;
                                }
                            }
                            if (prevFocusable != null) {
                                prevFocusable.requestFocus();
                                return true;
                            }
                        }
                        // No more buttons to the left in Action Pills row -> jump up to Header Bar!
                        View target = overlayView.findViewById(R.id.btn_ai_close);
                        if (target == null || target.getVisibility() != View.VISIBLE) {
                            target = btnAiMic;
                        }
                        if (target != null) {
                            target.requestFocus();
                            return true;
                        }
                    }
                    // In Suggested Questions: Jump backward to last chat card!
                    else if (isViewInside(current, containerSuggested)) {
                        if (containerAiChips != null && containerAiChips.getChildCount() > 0) {
                            View lastCard = containerAiChips.getChildAt(containerAiChips.getChildCount() - 1);
                            lastCard.requestFocus();
                            centerViewInScrollView(lastCard);
                            return true;
                        } else {
                            View target = btnPillVisionScan != null && btnPillVisionScan.getVisibility() == View.VISIBLE
                                    ? btnPillVisionScan
                                    : overlayView.findViewById(R.id.btn_pill_summary);
                            if (target != null) {
                                target.requestFocus();
                                scrollContent.smoothScrollTo(0, 0);
                                return true;
                            }
                        }
                    }
                    // Anywhere in Chat Content: 1 click jumps straight up to Action Pills!
                    else if (isViewInside(current, scrollContent)) {
                        View target = btnPillVisionScan != null && btnPillVisionScan.getVisibility() == View.VISIBLE
                                ? btnPillVisionScan
                                : overlayView.findViewById(R.id.btn_pill_summary);
                        if (target != null) {
                            target.requestFocus();
                            scrollContent.smoothScrollTo(0, 0);
                            return true;
                        }
                    }
                }
            }

            // Vertical DPAD navigation between main containers
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (current != null) {
                    if (isViewInside(current, headerBar)) {
                        View target = btnPillVisionScan != null ? btnPillVisionScan : overlayView.findViewById(R.id.btn_pill_summary);
                        if (target != null) {
                            target.requestFocus();
                            return true;
                        }
                    } else if (isViewInside(current, actionPills)) {
                        if (containerAiChips != null && containerAiChips.getChildCount() > 0) {
                            View firstCard = containerAiChips.getChildAt(0);
                            firstCard.requestFocus();
                            centerViewInScrollView(firstCard);
                            return true;
                        } else if (btnSuggested1 != null && btnSuggested1.getVisibility() == View.VISIBLE) {
                            btnSuggested1.requestFocus();
                            centerViewInScrollView(btnSuggested1);
                            return true;
                        }
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (current != null) {
                    if (isViewInside(current, actionPills)) {
                        View target = btnAiMic != null ? btnAiMic : overlayView.findViewById(R.id.btn_ai_close);
                        if (target != null) {
                            target.requestFocus();
                            return true;
                        }
                    } else if (isViewInside(current, scrollContent)) {
                        View upNeighbor = current.focusSearch(View.FOCUS_UP);
                        if (upNeighbor == null || !isViewInside(upNeighbor, scrollContent)) {
                            View target = btnPillVisionScan != null ? btnPillVisionScan : overlayView.findViewById(R.id.btn_pill_summary);
                            if (target != null) {
                                target.requestFocus();
                                scrollContent.smoothScrollTo(0, 0);
                                return true;
                            }
                        }
                    }
                }
            }
        }

        boolean handled = overlayView.dispatchKeyEvent(event);
        if (handled) return true;

        if (action == KeyEvent.ACTION_DOWN) {
            View current = overlayView.findFocus();
            int direction = -1;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) direction = View.FOCUS_DOWN;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) direction = View.FOCUS_UP;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) direction = View.FOCUS_LEFT;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) direction = View.FOCUS_RIGHT;

            if (direction != -1) {
                if (current != null) {
                    View next = current.focusSearch(direction);
                    if (next != null && next != current) {
                        next.requestFocus();
                        centerViewInScrollView(next);
                        return true;
                    }
                } else {
                    View first = overlayView.findViewById(R.id.btn_pill_summary);
                    if (first != null) {
                        first.requestFocus();
                        return true;
                    }
                }
            }
        }

        return true;
    }

    private boolean isViewInside(View child, View parent) {
        if (child == null || parent == null) return false;
        View p = child;
        while (p != null) {
            if (p == parent) return true;
            if (p.getParent() instanceof View) {
                p = (View) p.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    private void updateInternalFilters(Context context) {
        if (overlayView == null || context == null) return;
        SharedPreferences prefs = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE);
        boolean isDimmerActive = prefs.getBoolean("is_dimmer_active", false);
        int dimmerPct = prefs.getInt("dimmer_brightness_pct", 100);
        boolean isBlueLightActive = prefs.getBoolean("is_blue_light_active", false);
        int blueLightPct = prefs.getInt("blue_light_pct", 0);

        if (aiMenuDimmerFilter != null) {
            if (isDimmerActive && dimmerPct < 100) {
                int alphaVal = (int) ((100 - dimmerPct) * 2.55);
                aiMenuDimmerFilter.setBackgroundColor(Color.argb(alphaVal, 0, 0, 0));
                aiMenuDimmerFilter.setVisibility(View.VISIBLE);
            } else {
                aiMenuDimmerFilter.setVisibility(View.GONE);
            }
        }

        if (aiMenuBlueLightFilter != null) {
            if (isBlueLightActive && blueLightPct > 0) {
                int alpha = (int) ((blueLightPct / 1000.0f) * 150);
                aiMenuBlueLightFilter.setBackgroundColor(Color.argb(alpha, 240, 120, 0));
                aiMenuBlueLightFilter.setVisibility(View.VISIBLE);
            } else {
                aiMenuBlueLightFilter.setVisibility(View.GONE);
            }
        }
    }

    private void updatePlayPauseButtonState() {
        if (btnAiPlayPause != null) {
            boolean isPlaying = MediaNotificationListener.isMediaPlaying();
            btnAiPlayPause.setText(isPlaying ? "⏸️" : "▶️");
        }
    }

    private void applyUiLanguage(Context context) {
        if (context == null || overlayView == null) return;
        SharedPreferences prefs = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE);
        uiLanguage = prefs.getString("ai_ui_language", "es");
        boolean isEn = "en".equalsIgnoreCase(uiLanguage);

        if (textAiWelcome != null) {
            textAiWelcome.setText(isEn
                    ? "👋 Hello! I am your TV Assistant.\nSelect an action or ask anything by voice."
                    : "👋 ¡Hola! Soy tu Asistente para TV.\nSelecciona una acción o pregunta con tu voz.");
        }

        View btnSummary = overlayView.findViewById(R.id.btn_pill_summary);
        if (btnSummary instanceof Button) {
            ((Button) btnSummary).setText(isEn ? "📝 Summarize Video" : "📝 Resumir Video");
        }
        View btnKeyPoints = overlayView.findViewById(R.id.btn_pill_key_points);
        if (btnKeyPoints instanceof Button) {
            ((Button) btnKeyPoints).setText(isEn ? "💡 Key Points" : "💡 Puntos Clave");
        }
        View btnConclusions = overlayView.findViewById(R.id.btn_pill_conclusions);
        if (btnConclusions instanceof Button) {
            ((Button) btnConclusions).setText(isEn ? "🎯 Conclusions" : "🎯 Conclusiones");
        }
        View btnMoments = overlayView.findViewById(R.id.btn_pill_moments);
        if (btnMoments instanceof Button) {
            ((Button) btnMoments).setText(isEn ? "⏱️ Key Moments" : "⏱️ Momentos Clave");
        }
        if (btnPillVisionScan != null) {
            btnPillVisionScan.setText(isEn ? "👁️ Analyze Screen (Lens)" : "👁️ Analizar Pantalla (Lens)");
        }

        if (txtVisionHeader != null) {
            txtVisionHeader.setText(isEn ? "🔍 Visual Analysis & Lens" : "🔍 Análisis Visual y Lens");
        }
        if (btnVisionOptTranslate != null) {
            btnVisionOptTranslate.setText(isEn ? "🔤 Translate Text (Lens)" : "🔤 Detectar y Traducir Texto");
        }
        if (btnVisionTargetLang != null) {
            btnVisionTargetLang.setText(isEn
                    ? (visionTargetLangIsEnglish ? "🌐 Target: EN" : "🌐 Target: ES")
                    : (visionTargetLangIsEnglish ? "🌐 Destino: EN" : "🌐 Destino: ES"));
        }
        if (btnVisionOptObjects != null) {
            btnVisionOptObjects.setText(isEn ? "🎯 Analyze Objects" : "🎯 Analizar Objetos");
        }
        if (btnVisionOptPlaces != null) {
            btnVisionOptPlaces.setText(isEn ? "📍 Identify Places / People" : "📍 Identificar Lugar o Personas");
        }
        if (btnVisionClose != null) {
            btnVisionClose.setText(isEn ? "✕ Cancel" : "✕ Cancelar");
        }

        if (btnAiReset != null) {
            btnAiReset.setText(isEn ? "🔄 New chat" : "🔄 Nuevo chat");
        }
        if (btnAiMic != null) {
            btnAiMic.setText(isListening
                    ? (isEn ? "🔴 Listening..." : "🔴 Escuchando...")
                    : (isEn ? "🎙️ Talk" : "🎙️ Hablar"));
        }
    }

    private String detectLanguage(String trackLang, String transcript, String title) {
        if (trackLang != null) {
            String tl = trackLang.trim().toLowerCase();
            if (tl.startsWith("es")) return "es";
            if (!tl.isEmpty() && !tl.equals("auto") && !tl.equals("unknown")) return "en";
        }
        String sample = "";
        if (transcript != null && transcript.length() > 50) {
            sample = transcript.substring(0, Math.min(transcript.length(), 600)).toLowerCase();
        } else if (title != null) {
            sample = title.toLowerCase();
        }
        if (!sample.isEmpty()) {
            int esCount = 0;
            String[] esWords = {" de ", " la ", " el ", " en ", " y ", " que ", " los ", " del ", " las ", " por ", " un ", " para ", " con ", " no ", " una "};
            for (String w : esWords) {
                if (sample.contains(w)) esCount++;
            }
            if (esCount >= 2) return "es";
        }
        return "en";
    }

    private void setChatLanguage(String lang) {
        if (lang == null || (!lang.equalsIgnoreCase("es") && !lang.equalsIgnoreCase("en"))) {
            lang = "es";
        }
        this.chatLanguage = lang.toLowerCase();
        if (btnAiChatLang != null) {
            btnAiChatLang.setText("es".equalsIgnoreCase(this.chatLanguage) ? "🌐 ES" : "🌐 EN");
        }
    }

    private void captureScreenAndExecuteVision(Context context, String prompt, String actionName) {
        if (overlayView == null) return;
        ButtonMappingService service = ButtonMappingService.instance;
        if (service == null) {
            Toast.makeText(context, "Servicio de accesibilidad no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide overlay temporarily to capture the true screen content behind it
        overlayView.setVisibility(View.INVISIBLE);
        mainHandler.postDelayed(() -> {
            service.captureScreenForVision(new ButtonMappingService.ScreenCaptureCallback() {
                @Override
                public void onCaptured(Bitmap bitmap) {
                    mainHandler.post(() -> {
                        if (overlayView != null) overlayView.setVisibility(View.VISIBLE);
                        if (bitmap != null) {
                            executeVisionPrompt(context, bitmap, prompt, actionName);
                        } else {
                            Toast.makeText(context, "No se pudo obtener captura de pantalla", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        if (overlayView != null) overlayView.setVisibility(View.VISIBLE);
                        Toast.makeText(context, "Error de captura: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }, 120);
    }

    private void executeVisionPrompt(Context context, Bitmap screenshot, String prompt, String actionName) {
        if (context == null || screenshot == null) return;

        float density = context.getResources().getDisplayMetrics().density;
        int p14 = (int) (14 * density);
        int mb10 = (int) (10 * density);

        if (textAiWelcome != null) textAiWelcome.setVisibility(View.GONE);
        addUserQuestionCard(context, actionName);
        recordedTurns.add(new ChatTurn(true, actionName, null, null));

        if (layoutSuggestedSection != null) {
            layoutSuggestedSection.setVisibility(View.GONE);
        }

        // Inline thinking indicator
        if (containerAiChips != null) {
            if (currentThinkingView != null) {
                containerAiChips.removeView(currentThinkingView);
            }
            LinearLayout thinkingCard = new LinearLayout(context);
            thinkingCard.setOrientation(LinearLayout.HORIZONTAL);
            thinkingCard.setGravity(Gravity.CENTER_VERTICAL);
            thinkingCard.setBackgroundResource(R.drawable.card_chip_content);
            thinkingCard.setPadding(p14, p14, p14, p14);
            LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tParams.bottomMargin = mb10;
            thinkingCard.setLayoutParams(tParams);

            ProgressBar miniProgress = new ProgressBar(context);
            miniProgress.setIndeterminate(true);
            int pSize = (int) (22 * density);
            LinearLayout.LayoutParams progParams = new LinearLayout.LayoutParams(pSize, pSize);
            progParams.rightMargin = (int) (12 * density);
            miniProgress.setLayoutParams(progParams);

            TextView tvThinking = new TextView(context);
            boolean isEn = "en".equalsIgnoreCase(uiLanguage);
            tvThinking.setText(isEn ? "✨ Analyzing screen..." : "✨ Analizando pantalla...");
            tvThinking.setTextColor(0xFFCCCCCC);
            tvThinking.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);

            thinkingCard.addView(miniProgress);
            thinkingCard.addView(tvThinking);
            containerAiChips.addView(thinkingCard);
            currentThinkingView = thinkingCard;

            scrollContent.post(() -> centerViewInScrollView(thinkingCard));
        }

        AiSummaryEngine.getInstance().askAiVision(context, screenshot, prompt, chatLanguage, new AiSummaryEngine.VisionCallback() {
            @Override
            public void onSuccess(AiSummaryEngine.VisionResult result) {
                if (!isShowing) return;

                if (currentThinkingView != null && containerAiChips != null) {
                    containerAiChips.removeView(currentThinkingView);
                    currentThinkingView = null;
                }

                conversationHistory.add(new AiSummaryEngine.ChatMessage("user", actionName));
                conversationHistory.add(new AiSummaryEngine.ChatMessage("assistant", result.rawAnswer));
                recordedTurns.add(new ChatTurn(false, result.rawAnswer, null, result.suggestedQuestions, result.lensBoxes));
                lastSuggestedQuestions = new ArrayList<>(result.suggestedQuestions != null ? result.suggestedQuestions : Collections.emptyList());

                if (btnAiReset != null) btnAiReset.setVisibility(View.VISIBLE);
                saveConversationToPrefs(context);

                appendChips(context, null, result.rawAnswer, true, result.lensBoxes);

                if (result.lensBoxes != null && !result.lensBoxes.isEmpty()) {
                    showLensOverlay(result.lensBoxes, context);
                }

                if (result.detectedObjects != null && !result.detectedObjects.isEmpty()) {
                    appendObjectExplorePills(context, result.detectedObjects);
                }

                renderSuggestedQuestions(context, result.suggestedQuestions);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isShowing) return;

                if (currentThinkingView != null && containerAiChips != null) {
                    containerAiChips.removeView(currentThinkingView);
                    currentThinkingView = null;
                }

                if (containerAiChips != null) {
                    LinearLayout errCard = new LinearLayout(context);
                    errCard.setOrientation(LinearLayout.VERTICAL);
                    errCard.setBackgroundResource(R.drawable.card_chip_content);
                    errCard.setPadding(p14, p14, p14, p14);
                    TextView tvErr = new TextView(context);
                    tvErr.setText("❌ " + errorMessage);
                    tvErr.setTextColor(0xFFFF6B6B);
                    tvErr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                    errCard.addView(tvErr);
                    containerAiChips.addView(errCard);
                    scrollContent.post(() -> centerViewInScrollView(errCard));
                }
            }
        });
    }

    private void renderLensBadgesIntoContainer(FrameLayout container, List<AiSummaryEngine.LensBoxItem> boxes, Context ctx) {
        if (container == null || boxes == null || boxes.isEmpty() || ctx == null) return;
        container.removeAllViews();
        renderedLensBadgeViews.clear();
        selectedLensBadgeIndex = -1;

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        List<Rect> renderedRects = new ArrayList<>();

        for (AiSummaryEngine.LensBoxItem box : boxes) {
            if (box.translatedText == null || box.translatedText.trim().isEmpty()) continue;

            int topPx = Math.round((box.ymin / 1000.0f) * screenH);
            int leftPx = Math.round((box.xmin / 1000.0f) * screenW);
            int boxWidth = Math.round(((box.xmax - box.xmin) / 1000.0f) * screenW);

            // Safe clamping to physical display boundaries
            leftPx = Math.max((int) (10 * dm.density), Math.min(leftPx, screenW - (int) (160 * dm.density)));
            topPx = Math.max((int) (10 * dm.density), Math.min(topPx, screenH - (int) (48 * dm.density)));

            // Skip only near-identical coordinate duplicates so adjacent translations are preserved
            boolean collides = false;
            for (Rect r : renderedRects) {
                if (Math.abs(r.left - leftPx) < (int) (8 * dm.density) && Math.abs(r.top - topPx) < (int) (8 * dm.density)) {
                    collides = true;
                    break;
                }
            }
            if (collides) continue;
            renderedRects.add(new Rect(leftPx, topPx, leftPx + Math.max(boxWidth, (int) (60 * dm.density)), topPx + (int) (28 * dm.density)));

            LinearLayout badge = new LinearLayout(ctx);
            badge.setOrientation(LinearLayout.VERTICAL);
            badge.setGravity(Gravity.CENTER_VERTICAL);
            int padH = (int) (8 * dm.density);
            int padV = (int) (3 * dm.density);
            badge.setPadding(padH, padV, padH, padV);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            lp.leftMargin = leftPx;
            lp.topMargin = topPx;
            badge.setLayoutParams(lp);

            if (boxWidth > (int) (40 * dm.density)) {
                badge.setMinimumWidth(Math.min(boxWidth, (int) (400 * dm.density)));
            }

            TextView tvTrans = new TextView(ctx);
            tvTrans.setText(box.translatedText);
            tvTrans.setTextColor(0xFFFFFFFF);
            tvTrans.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            tvTrans.setTypeface(tvTrans.getTypeface(), Typeface.BOLD);
            badge.addView(tvTrans);

            updateBadgeVisualState(badge, false, dm);
            renderedLensBadgeViews.add(badge);
            container.addView(badge);
        }
    }

    private void updateBadgeVisualState(View badge, boolean isSelected, DisplayMetrics dm) {
        if (badge == null || dm == null) return;
        GradientDrawable bg = new GradientDrawable();
        if (isSelected) {
            bg.setColor(0xFF181824);
            bg.setCornerRadius(8 * dm.density);
            bg.setStroke((int) (2.5f * dm.density), 0xFFFFFFFF);
            badge.setBackground(bg);
            badge.setScaleX(1.08f);
            badge.setScaleY(1.08f);
            badge.setElevation(30f);
            if (badge instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) badge;
                for (int c = 0; c < vg.getChildCount(); c++) {
                    View child = vg.getChildAt(c);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(0xFFFFFFFF);
                    }
                }
            }
        } else {
            bg.setColor(0xEE1E1E2E);
            bg.setCornerRadius(6 * dm.density);
            bg.setStroke((int) (1.5f * dm.density), 0xFF8AB4F8);
            badge.setBackground(bg);
            badge.setScaleX(1.0f);
            badge.setScaleY(1.0f);
            badge.setElevation(2f);
            if (badge instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) badge;
                for (int c = 0; c < vg.getChildCount(); c++) {
                    View child = vg.getChildAt(c);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(0xFFE0E0E0);
                    }
                }
            }
        }
    }

    public void selectLensBadge(int index, Context ctx) {
        if (renderedLensBadgeViews.isEmpty() || ctx == null) return;
        if (index < 0 || index >= renderedLensBadgeViews.size()) return;

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

        if (selectedLensBadgeIndex >= 0 && selectedLensBadgeIndex < renderedLensBadgeViews.size()) {
            View prev = renderedLensBadgeViews.get(selectedLensBadgeIndex);
            updateBadgeVisualState(prev, false, dm);
        }

        selectedLensBadgeIndex = index;
        View selected = renderedLensBadgeViews.get(index);
        updateBadgeVisualState(selected, true, dm);

        selected.bringToFront();
        if (lensOverlayWindowView != null) {
            lensOverlayWindowView.requestLayout();
            lensOverlayWindowView.invalidate();
        }
    }

    public boolean handleLensOverlayKeyEvent(KeyEvent event) {
        if (!isLensOverlayShowing() || renderedLensBadgeViews.isEmpty() || lensOverlayWindowView == null) {
            return false;
        }

        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (keyCode != KeyEvent.KEYCODE_DPAD_UP
                && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                && keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                && keyCode != KeyEvent.KEYCODE_DPAD_CENTER
                && keyCode != KeyEvent.KEYCODE_ENTER) {
            return false;
        }

        if (action != KeyEvent.ACTION_DOWN) {
            return true;
        }

        Context ctx = lensOverlayWindowView.getContext();
        int numBadges = renderedLensBadgeViews.size();

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            return true;
        }

        if (selectedLensBadgeIndex < 0 || selectedLensBadgeIndex >= numBadges) {
            selectLensBadge(0, ctx);
            return true;
        }

        View currentBadge = renderedLensBadgeViews.get(selectedLensBadgeIndex);
        int[] currLoc = new int[2];
        currentBadge.getLocationOnScreen(currLoc);
        int currCenterX = currLoc[0] + currentBadge.getWidth() / 2;
        int currCenterY = currLoc[1] + currentBadge.getHeight() / 2;

        int bestTargetIndex = -1;
        float bestScore = Float.MAX_VALUE;

        for (int i = 0; i < numBadges; i++) {
            if (i == selectedLensBadgeIndex) continue;
            View target = renderedLensBadgeViews.get(i);
            int[] tgtLoc = new int[2];
            target.getLocationOnScreen(tgtLoc);
            int tgtCenterX = tgtLoc[0] + target.getWidth() / 2;
            int tgtCenterY = tgtLoc[1] + target.getHeight() / 2;

            int dx = tgtCenterX - currCenterX;
            int dy = tgtCenterY - currCenterY;

            boolean isEligible = false;
            float score = Float.MAX_VALUE;

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (dx > 10) {
                    isEligible = true;
                    score = dx + Math.abs(dy) * 2.0f;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (dx < -10) {
                    isEligible = true;
                    score = (-dx) + Math.abs(dy) * 2.0f;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (dy > 10) {
                    isEligible = true;
                    score = dy + Math.abs(dx) * 1.5f;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (dy < -10) {
                    isEligible = true;
                    score = (-dy) + Math.abs(dx) * 1.5f;
                }
            }

            if (isEligible && score < bestScore) {
                bestScore = score;
                bestTargetIndex = i;
            }
        }

        if (bestTargetIndex == -1) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                bestTargetIndex = (selectedLensBadgeIndex + 1) % numBadges;
            } else {
                bestTargetIndex = (selectedLensBadgeIndex - 1 + numBadges) % numBadges;
            }
        }

        selectLensBadge(bestTargetIndex, ctx);
        return true;
    }

    private void appendObjectExplorePills(Context context, List<String> objects) {
        if (containerAiChips == null || objects == null || objects.isEmpty()) return;
        float density = context.getResources().getDisplayMetrics().density;
        int mb6 = (int) (6 * density);
        int p8 = (int) (8 * density);
        int p12 = (int) (12 * density);
        boolean isEn = "en".equalsIgnoreCase(uiLanguage);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowLp.topMargin = mb6;
        rowLp.bottomMargin = mb6;
        row.setLayoutParams(rowLp);

        TextView label = new TextView(context);
        label.setText(isEn ? "🎯 Explore detected objects:" : "🎯 Explorar objetos detectados:");
        label.setTextColor(0xFFAAAAAA);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        label.setPadding(0, 0, 0, (int) (4 * density));
        row.addView(label);

        for (String obj : objects) {
            Button pill = new Button(context);
            pill.setText("🔍 " + (isEn ? "Learn more about: " : "Saber más de: ") + obj);
            pill.setTextColor(0xFFFFFFFF);
            pill.setBackgroundResource(R.drawable.pill_youtube_tv);
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            pill.setFocusable(true);
            pill.setPadding(p12, p8, p12, p8);
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            pillLp.bottomMargin = (int) (4 * density);
            pill.setLayoutParams(pillLp);

            pill.setOnClickListener(v -> {
                String q = isEn
                        ? "Tell me more details, context, and facts about: " + obj
                        : "Cuéntame más detalles, historia y contexto sobre: " + obj;
                executePrompt(context, q, false);
            });
            row.addView(pill);
        }

        containerAiChips.addView(row);
        scrollContent.post(() -> centerViewInScrollView(row));
    }

    public void onVideoChanged(String newTitle, String newMediaId) {
        mainHandler.post(() -> {
            if (newTitle == null || newTitle.trim().isEmpty()) return;
            if (isShowing) {
                if (!isSameVideo(newTitle, newMediaId, currentVideoTitle, currentVideoId)) {
                    Log.d(TAG, "onVideoChanged event: video switched to '" + newTitle + "' [id: " + newMediaId + "]");
                    Context ctx = overlayView != null ? overlayView.getContext() : ButtonMappingService.instance;
                    if (ctx != null) {
                        detectVideoAndLoadTranscript(ctx);
                    }
                }
            }
        });
    }

    private final Runnable videoPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isShowing) return;
            Context ctx = overlayView != null ? overlayView.getContext() : ButtonMappingService.instance;
            if (ctx != null) {
                String liveTitle = resolveCurrentTitle(ctx);
                String liveId = resolveCurrentVideoId(ctx, liveTitle);
                if (liveTitle != null && !liveTitle.isEmpty() && !liveTitle.equals("Video actual en pantalla")) {
                    if (!isSameVideo(liveTitle, liveId, currentVideoTitle, currentVideoId)) {
                        Log.d(TAG, "Poller detected new video: '" + liveTitle + "' [id: " + liveId + "] (was: '" + currentVideoTitle + "')");
                        detectVideoAndLoadTranscript(ctx);
                    }
                }
            }
            mainHandler.postDelayed(this, 1500);
        }
    };

    private String resolveCurrentTitle(Context context) {
        MediaNotificationListener.LiveVideoInfo info = MediaNotificationListener.getLiveVideoInfo(context);
        String title = info != null ? info.title : "";
        if (title == null || title.trim().isEmpty()) {
            title = MediaNotificationListener.activeTitle;
        }
        if (title == null || title.trim().isEmpty()) {
            title = com.nitsutech.omnitv.vot.VotManager.getInstance(context).getCurrentVideoTitle();
        }
        if (title == null || title.trim().isEmpty()) {
            title = "Video actual en pantalla";
        }
        return title.trim();
    }

    private String resolveCurrentVideoId(Context context, String title) {
        MediaNotificationListener.LiveVideoInfo info = MediaNotificationListener.getLiveVideoInfo(context);
        if (info != null && info.mediaId != null && !info.mediaId.trim().isEmpty()) {
            String cleanId = MediaNotificationListener.extractCleanVideoId(info.mediaId);
            if (cleanId != null && !cleanId.isEmpty()) {
                return cleanId;
            }
        }
        String mediaId = MediaNotificationListener.activeMediaId;
        String cleanId = MediaNotificationListener.extractCleanVideoId(mediaId);
        if (cleanId != null && !cleanId.isEmpty()) {
            return cleanId;
        }

        try {
            String votId = com.nitsutech.omnitv.vot.VotManager.getInstance(context).getCurrentVideoId();
            cleanId = MediaNotificationListener.extractCleanVideoId(votId);
            if (cleanId != null && !cleanId.isEmpty()) {
                return cleanId;
            }
        } catch (Exception ignored) {}

        try {
            String cachedId = com.nitsutech.omnitv.vot.YouTubeCaptionFetcher.getCachedVideoId(title);
            if (cachedId != null && !cachedId.trim().isEmpty()) {
                return cachedId.trim();
            }
        } catch (Exception ignored) {}

        return "";
    }

    private boolean isSameVideo(String newTitle, String newVideoId, String oldTitle, String oldVideoId) {
        // 1. Video ID match (most reliable)
        if (isValidVideoId(newVideoId) && isValidVideoId(oldVideoId)) {
            if (newVideoId.equalsIgnoreCase(oldVideoId)) {
                return true;
            }
        }

        // 2. Title matching
        if (newTitle != null && oldTitle != null && !newTitle.trim().isEmpty() && !oldTitle.trim().isEmpty()) {
            if (newTitle.equalsIgnoreCase(oldTitle)) {
                return true;
            }
            String normNew = normalizeTitle(newTitle);
            String normOld = normalizeTitle(oldTitle);
            if (!normNew.isEmpty() && normNew.equals(normOld)) {
                return true;
            }
            if (normNew.length() >= 6 && normOld.length() >= 6) {
                if (normNew.contains(normOld) || normOld.contains(normNew)) {
                    return true;
                }
            }
        }

        // 3. Fallback: if newVideoId matches oldVideoId and not empty
        if (newVideoId != null && !newVideoId.isEmpty() && newVideoId.equalsIgnoreCase(oldVideoId)) {
            return true;
        }

        return false;
    }

    private boolean isValidVideoId(String id) {
        return id != null && id.trim().matches("^[a-zA-Z0-9_-]{11}$");
    }

    private String normalizeTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase()
                .replaceAll("\\[.*?\\]", " ")
                .replaceAll("\\(.*?\\)", " ")
                .replaceAll("[•·|\\-–—_#]", " ")
                .replaceAll("[^a-z0-9áéíóúüñ\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void resetConversation(Context context) {
        stopVoiceInput();
        conversationHistory.clear();
        recordedTurns.clear();
        lastSuggestedQuestions.clear();
        askedQuestionsSet.clear();
        if (containerAiChips != null) containerAiChips.removeAllViews();
        if (textAiWelcome != null) textAiWelcome.setVisibility(View.VISIBLE);
        if (layoutSuggestedSection != null) layoutSuggestedSection.setVisibility(View.GONE);
        if (btnAiReset != null) btnAiReset.setVisibility(View.GONE);
        hideLensOverlay();
        String defaultLang = detectLanguage(currentTrackLang, currentTranscript, currentVideoTitle);
        setChatLanguage(defaultLang);
        saveConversationToPrefs(context);
        boolean isEn = "en".equalsIgnoreCase(uiLanguage);
        Toast.makeText(context, isEn ? "🔄 Conversation reset" : "🔄 Conversación reiniciada", Toast.LENGTH_SHORT).show();
    }

    private void saveConversationToPrefs(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(AiSummaryEngine.PREFS_NAME, Context.MODE_PRIVATE);
            JSONArray turnsArray = new JSONArray();
            for (ChatTurn turn : recordedTurns) {
                JSONObject obj = new JSONObject();
                obj.put("isUser", turn.isUser);
                obj.put("text", turn.text);
                if (turn.items != null && !turn.items.isEmpty()) {
                    JSONArray itemsArr = new JSONArray();
                    for (AiSummaryEngine.AiPointItem item : turn.items) {
                        JSONObject itemObj = new JSONObject();
                        itemObj.put("text", item.text);
                        itemObj.put("timestampStr", item.timestampStr);
                        itemObj.put("timestampMs", item.timestampMs);
                        itemObj.put("hasTimestamp", item.hasTimestamp);
                        itemsArr.put(itemObj);
                    }
                    obj.put("items", itemsArr);
                }
                if (turn.lensBoxes != null && !turn.lensBoxes.isEmpty()) {
                    JSONArray lbArr = new JSONArray();
                    for (AiSummaryEngine.LensBoxItem lb : turn.lensBoxes) {
                        JSONObject lbObj = new JSONObject();
                        lbObj.put("ymin", lb.ymin);
                        lbObj.put("xmin", lb.xmin);
                        lbObj.put("ymax", lb.ymax);
                        lbObj.put("xmax", lb.xmax);
                        lbObj.put("originalText", lb.originalText != null ? lb.originalText : "");
                        lbObj.put("translatedText", lb.translatedText != null ? lb.translatedText : "");
                        lbObj.put("location", lb.locationHint != null ? lb.locationHint : "");
                        lbArr.put(lbObj);
                    }
                    obj.put("lensBoxes", lbArr);
                }
                if (turn.suggestedQuestions != null && !turn.suggestedQuestions.isEmpty()) {
                    JSONArray qArr = new JSONArray();
                    for (String q : turn.suggestedQuestions) {
                        qArr.put(q);
                    }
                    obj.put("suggestedQuestions", qArr);
                }
                turnsArray.put(obj);
            }

            JSONArray lastQArr = new JSONArray();
            for (String q : lastSuggestedQuestions) {
                lastQArr.put(q);
            }

            prefs.edit()
                    .putString("ai_last_video_title", lastVideoTitle != null ? lastVideoTitle : "")
                    .putString("ai_last_video_id", lastVideoId != null ? lastVideoId : "")
                    .putString("ai_last_transcript", lastTranscript != null ? lastTranscript : "")
                    .putString("ai_saved_conversation", turnsArray.toString())
                    .putString("ai_saved_suggested_questions", lastQArr.toString())
                    .apply();
            Log.d(TAG, "Conversation saved to SharedPreferences (" + recordedTurns.size() + " turns) for video: " + lastVideoTitle + " [id: " + lastVideoId + "]");
        } catch (Exception e) {
            Log.e(TAG, "Error saving conversation to SharedPreferences", e);
        }
    }

    private boolean loadConversationFromPrefs(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(AiSummaryEngine.PREFS_NAME, Context.MODE_PRIVATE);
            String savedTitle = prefs.getString("ai_last_video_title", "");
            String savedId = prefs.getString("ai_last_video_id", "");
            String savedTranscript = prefs.getString("ai_last_transcript", "");
            String savedJson = prefs.getString("ai_saved_conversation", "");
            String savedQJson = prefs.getString("ai_saved_suggested_questions", "");

            if (savedJson == null || savedJson.isEmpty() || "[]".equals(savedJson)) {
                return false;
            }

            lastVideoTitle = savedTitle;
            lastVideoId = savedId;
            lastTranscript = savedTranscript;

            recordedTurns.clear();
            conversationHistory.clear();
            lastSuggestedQuestions.clear();

            JSONArray turnsArray = new JSONArray(savedJson);
            for (int i = 0; i < turnsArray.length(); i++) {
                JSONObject obj = turnsArray.getJSONObject(i);
                boolean isUser = obj.optBoolean("isUser", false);
                String text = obj.optString("text", "");

                List<AiSummaryEngine.AiPointItem> items = new ArrayList<>();
                if (obj.has("items")) {
                    JSONArray itemsArr = obj.getJSONArray("items");
                    for (int j = 0; j < itemsArr.length(); j++) {
                        JSONObject itemObj = itemsArr.getJSONObject(j);
                        String itemText = itemObj.optString("text", "");
                        String tStr = itemObj.optString("timestampStr", null);
                        long tMs = itemObj.optLong("timestampMs", -1);
                        items.add(new AiSummaryEngine.AiPointItem(tStr, tMs, itemText));
                    }
                }

                List<AiSummaryEngine.LensBoxItem> lensBoxes = new ArrayList<>();
                if (obj.has("lensBoxes")) {
                    JSONArray lbArr = obj.getJSONArray("lensBoxes");
                    for (int j = 0; j < lbArr.length(); j++) {
                        JSONObject lbObj = lbArr.getJSONObject(j);
                        int ymin = lbObj.optInt("ymin", 0);
                        int xmin = lbObj.optInt("xmin", 0);
                        int ymax = lbObj.optInt("ymax", 0);
                        int xmax = lbObj.optInt("xmax", 0);
                        String orig = lbObj.optString("originalText", "");
                        String trans = lbObj.optString("translatedText", "");
                        String loc = lbObj.optString("location", "");
                        lensBoxes.add(new AiSummaryEngine.LensBoxItem(ymin, xmin, ymax, xmax, orig, trans, loc));
                    }
                }

                List<String> suggested = new ArrayList<>();
                if (obj.has("suggestedQuestions")) {
                    JSONArray qArr = obj.getJSONArray("suggestedQuestions");
                    for (int j = 0; j < qArr.length(); j++) {
                        suggested.add(qArr.getString(j));
                    }
                }

                recordedTurns.add(new ChatTurn(isUser, text, items, suggested, lensBoxes));
                conversationHistory.add(new AiSummaryEngine.ChatMessage(isUser ? "user" : "assistant", text));
            }

            if (savedQJson != null && !savedQJson.isEmpty()) {
                JSONArray qArr = new JSONArray(savedQJson);
                for (int j = 0; j < qArr.length(); j++) {
                    lastSuggestedQuestions.add(qArr.getString(j));
                }
            }

            askedQuestionsSet.clear();
            for (ChatTurn turn : recordedTurns) {
                if (turn.isUser && turn.text != null && !turn.text.trim().isEmpty()) {
                    askedQuestionsSet.add(normalizeTitle(turn.text));
                }
            }

            Log.d(TAG, "Successfully loaded conversation from SharedPreferences (" + recordedTurns.size() + " turns) for video: " + lastVideoTitle + " [id: " + lastVideoId + "]");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error loading conversation from SharedPreferences", e);
            return false;
        }
    }

    private void detectVideoAndLoadTranscript(Context context) {
        String detectedTitle = resolveCurrentTitle(context);
        String detectedId = resolveCurrentVideoId(context, detectedTitle);
        currentVideoTitle = detectedTitle;
        currentVideoId = detectedId;
        textVideoTitle.setText("📺 " + currentVideoTitle);

        SharedPreferences prefs = context.getSharedPreferences(AiSummaryEngine.PREFS_NAME, Context.MODE_PRIVATE);
        int provider = prefs.getInt(AiSummaryEngine.KEY_AI_PROVIDER, 0);
        String model = prefs.getString(AiSummaryEngine.KEY_GEMINI_MODEL, AiSummaryEngine.DEFAULT_GEMINI_MODEL);
        String providerName = provider == 1 ? "OpenRouter" : ("Gemini (" + model + ")");

        // Try loading saved conversation from SharedPreferences if in-memory list is empty
        if (recordedTurns.isEmpty()) {
            loadConversationFromPrefs(context);
        }

        // Check if it is the SAME video as the previous conversation
        boolean isSame = isSameVideo(detectedTitle, detectedId, lastVideoTitle, lastVideoId) && !recordedTurns.isEmpty();

        if (isSame) {
            Log.d(TAG, "Reopening same video (" + detectedTitle + " [id: " + detectedId + "]): restoring " + recordedTurns.size() + " conversation turns");
            currentTranscript = lastTranscript;
            if (isValidVideoId(lastVideoId) && !isValidVideoId(currentVideoId)) {
                currentVideoId = lastVideoId;
            }
            restoreSavedConversation(context);
            if (btnAiReset != null) btnAiReset.setVisibility(View.VISIBLE);
            textStatus.setText("✨ " + providerName + " • Conversación reanudada (" + (conversationHistory.size() / 2) + " respuestas)");
            return;
        }

        // Different video: clean slate
        Log.d(TAG, "New video detected (" + detectedTitle + " [id: " + detectedId + "]): resetting previous AI conversation history");
        hideLensOverlay();
        lastVideoTitle = detectedTitle;
        lastVideoId = detectedId;
        lastTranscript = "";
        currentTranscript = "";
        conversationHistory.clear();
        recordedTurns.clear();
        lastSuggestedQuestions.clear();
        askedQuestionsSet.clear();
        if (btnAiReset != null) btnAiReset.setVisibility(View.GONE);
        saveConversationToPrefs(context);

        textStatus.setText("⏳ Buscando subtítulos con " + providerName + "...");

        fetcherExecutor.execute(() -> {
            String transcript = "";
            String trackLang = "";
            try {
                // First check if VotManager already has a track for this video
                VotTrack cachedTrack = com.nitsutech.omnitv.vot.VotManager.getInstance(context).getCurrentTrack();
                if (cachedTrack != null && cachedTrack.cues != null && !cachedTrack.cues.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (VotCue cue : cachedTrack.cues) {
                        if (cue.originalText != null && !cue.originalText.trim().isEmpty()) {
                            sb.append(cue.originalText.trim()).append(" ");
                        }
                    }
                    transcript = sb.toString().trim();
                    if (cachedTrack.languageCode != null) {
                        trackLang = cachedTrack.languageCode;
                    }
                    if (isValidVideoId(cachedTrack.videoId)) {
                        currentVideoId = cachedTrack.videoId;
                        lastVideoId = cachedTrack.videoId;
                    }
                }

                if (transcript.isEmpty()) {
                    String videoId = YouTubeCaptionFetcher.resolveVideoId(currentVideoTitle);
                    if (videoId != null && !videoId.trim().isEmpty()) {
                        currentVideoId = videoId;
                        lastVideoId = videoId;
                        VotTrack track = YouTubeCaptionFetcher.fetchTrack(videoId, "es");
                        if (track != null) {
                            trackLang = "es";
                        } else {
                            track = YouTubeCaptionFetcher.fetchTrack(videoId, "en");
                            if (track != null) {
                                trackLang = "en";
                            }
                        }
                        if (track == null) {
                            track = YouTubeCaptionFetcher.fetchTrack(videoId, "auto");
                            if (track != null && track.languageCode != null) {
                                trackLang = track.languageCode;
                            }
                        }
                        if (track != null && track.cues != null && !track.cues.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (VotCue cue : track.cues) {
                                if (cue.originalText != null && !cue.originalText.trim().isEmpty()) {
                                    sb.append(cue.originalText.trim()).append(" ");
                                }
                            }
                            transcript = sb.toString().trim();
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not fetch transcript for video: " + currentVideoTitle, e);
            }

            final String finalTranscript = transcript;
            final String finalTrackLang = trackLang;
            mainHandler.post(() -> {
                if (!isShowing) return;
                currentTranscript = finalTranscript;
                lastTranscript = finalTranscript;
                currentTrackLang = finalTrackLang;
                saveConversationToPrefs(context);

                // Auto-detect default chat language for this new video
                String detectedChatLang = detectLanguage(finalTrackLang, finalTranscript, currentVideoTitle);
                setChatLanguage(detectedChatLang);

                boolean isEn = "en".equalsIgnoreCase(uiLanguage);
                if (!finalTranscript.isEmpty()) {
                    int wordCount = finalTranscript.split("\\s+").length;
                    textStatus.setText("✨ " + providerName + " • " + (isEn ? "Transcript loaded (" : "Transcripción cargada (") + wordCount + (isEn ? " words)" : " palabras)"));
                } else {
                    textStatus.setText("✨ " + providerName + " • " + (isEn ? "Ready (analyzing by title)" : "Listo (analizando por título)"));
                }
            });
        });
    }

    private void restoreSavedConversation(Context context) {
        if (containerAiChips == null) return;
        containerAiChips.removeAllViews();
        if (textAiWelcome != null) textAiWelcome.setVisibility(View.GONE);

        for (ChatTurn turn : recordedTurns) {
            if (turn.isUser) {
                addUserQuestionCard(context, turn.text);
            } else {
                appendChips(context, turn.items, turn.text, false, turn.lensBoxes);
            }
        }

        if (btnAiReset != null) {
            btnAiReset.setVisibility(!recordedTurns.isEmpty() ? View.VISIBLE : View.GONE);
        }

        renderSuggestedQuestions(context, lastSuggestedQuestions);

        // Auto-scroll to bottom of conversation
        scrollContent.post(() -> scrollContent.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void addUserQuestionCard(Context context, String questionText) {
        float density = context.getResources().getDisplayMetrics().density;
        int p14 = (int) (14 * density);
        int p10 = (int) (10 * density);
        int mb8 = (int) (8 * density);

        LinearLayout userCard = new LinearLayout(context);
        userCard.setOrientation(LinearLayout.VERTICAL);
        userCard.setBackgroundResource(R.drawable.pill_youtube_tv);
        userCard.setPadding(p14, p10, p14, p10);
        LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        userParams.topMargin = mb8;
        userParams.bottomMargin = mb8;
        userCard.setLayoutParams(userParams);

        TextView tvUser = new TextView(context);
        tvUser.setText("💬 " + questionText);
        tvUser.setTextColor(0xFF8AB4F8);
        tvUser.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tvUser.setTypeface(tvUser.getTypeface(), android.graphics.Typeface.BOLD);
        userCard.addView(tvUser);
        containerAiChips.addView(userCard);
    }

    public void executeCustomQuery(Context context, String question) {
        mainHandler.postDelayed(() -> {
            if (isShowing) {
                executePrompt(context, question, false);
            }
        }, 500);
    }

    private void executePrompt(Context context, String question, boolean isNewConversation) {
        if (isNewConversation) {
            conversationHistory.clear();
            recordedTurns.clear();
            if (containerAiChips != null) containerAiChips.removeAllViews();
            if (textAiWelcome != null) textAiWelcome.setVisibility(View.GONE);
        }

        // Clean number prefix from question if clicked from suggested pills
        String cleanQuestion = question.replaceAll("^[0-9]+[.)-]\\s*", "").trim();
        if (cleanQuestion.isEmpty()) return;

        askedQuestionsSet.add(normalizeTitle(cleanQuestion));

        float density = context.getResources().getDisplayMetrics().density;
        int p14 = (int) (14 * density);
        int mb10 = (int) (10 * density);

        if (containerAiChips != null) {
            if (textAiWelcome != null) textAiWelcome.setVisibility(View.GONE);
            addUserQuestionCard(context, cleanQuestion);
            recordedTurns.add(new ChatTurn(true, cleanQuestion, null, null));
        }

        // Hide suggested questions while thinking
        if (layoutSuggestedSection != null) {
            layoutSuggestedSection.setVisibility(View.GONE);
        }

        // Inline Thinking Chip with Progress Indicator
        if (containerAiChips != null) {
            if (currentThinkingView != null) {
                containerAiChips.removeView(currentThinkingView);
            }
            LinearLayout thinkingCard = new LinearLayout(context);
            thinkingCard.setOrientation(LinearLayout.HORIZONTAL);
            thinkingCard.setGravity(Gravity.CENTER_VERTICAL);
            thinkingCard.setBackgroundResource(R.drawable.card_chip_content);
            thinkingCard.setPadding(p14, p14, p14, p14);
            LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tParams.bottomMargin = mb10;
            thinkingCard.setLayoutParams(tParams);

            ProgressBar miniProgress = new ProgressBar(context);
            miniProgress.setIndeterminate(true);
            int pSize = (int) (22 * density);
            LinearLayout.LayoutParams progParams = new LinearLayout.LayoutParams(pSize, pSize);
            progParams.rightMargin = (int) (12 * density);
            miniProgress.setLayoutParams(progParams);

            TextView tvThinking = new TextView(context);
            boolean isEn = "en".equalsIgnoreCase(uiLanguage);
            tvThinking.setText(isEn ? "✨ Analyzing video..." : "✨ Analizando video...");
            tvThinking.setTextColor(0xFFCCCCCC);
            tvThinking.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);

            thinkingCard.addView(miniProgress);
            thinkingCard.addView(tvThinking);
            containerAiChips.addView(thinkingCard);
            currentThinkingView = thinkingCard;

            scrollContent.post(() -> centerViewInScrollView(thinkingCard));
        }

        AiSummaryEngine.getInstance().queryAi(context, currentVideoTitle, currentTranscript,
                new ArrayList<>(conversationHistory), cleanQuestion, chatLanguage, new AiSummaryEngine.AiCallback() {
                    @Override
                    public void onSuccess(String rawAnswer, List<AiSummaryEngine.AiPointItem> items, List<String> suggestedQuestions) {
                        if (!isShowing) return;

                        if (currentThinkingView != null && containerAiChips != null) {
                            containerAiChips.removeView(currentThinkingView);
                            currentThinkingView = null;
                        }

                        // Save turns to history and persistent memory
                        conversationHistory.add(new AiSummaryEngine.ChatMessage("user", cleanQuestion));
                        conversationHistory.add(new AiSummaryEngine.ChatMessage("assistant", rawAnswer));
                        recordedTurns.add(new ChatTurn(false, rawAnswer, items, suggestedQuestions));
                        lastSuggestedQuestions = new ArrayList<>(suggestedQuestions != null ? suggestedQuestions : Collections.emptyList());

                        if (btnAiReset != null) btnAiReset.setVisibility(View.VISIBLE);
                        saveConversationToPrefs(context);

                        appendChips(context, items, rawAnswer, true);
                        renderSuggestedQuestions(context, suggestedQuestions);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!isShowing) return;

                        if (currentThinkingView != null && containerAiChips != null) {
                            containerAiChips.removeView(currentThinkingView);
                            currentThinkingView = null;
                        }

                        if (containerAiChips != null) {
                            LinearLayout errCard = new LinearLayout(context);
                            errCard.setOrientation(LinearLayout.VERTICAL);
                            errCard.setBackgroundResource(R.drawable.card_chip_content);
                            errCard.setPadding(p14, p14, p14, p14);
                            TextView tvErr = new TextView(context);
                            tvErr.setText("❌ " + errorMessage);
                            tvErr.setTextColor(0xFFFF6B6B);
                            tvErr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                            errCard.addView(tvErr);
                            containerAiChips.addView(errCard);
                            scrollContent.post(() -> centerViewInScrollView(errCard));
                        }
                    }
                });
    }

    private void appendChips(Context context, List<AiSummaryEngine.AiPointItem> items, String fallbackRawAnswer, boolean autoFocusFirst) {
        appendChips(context, items, fallbackRawAnswer, autoFocusFirst, null);
    }

    private void appendChips(Context context, List<AiSummaryEngine.AiPointItem> items, String fallbackRawAnswer, boolean autoFocusFirst, List<AiSummaryEngine.LensBoxItem> lensBoxes) {
        if (containerAiChips == null) return;
        if (textAiWelcome != null) textAiWelcome.setVisibility(View.GONE);

        if ((items == null || items.isEmpty()) && (fallbackRawAnswer == null || fallbackRawAnswer.trim().isEmpty())) {
            return;
        }

        List<AiSummaryEngine.AiPointItem> renderList = items;
        if (renderList == null || renderList.isEmpty()) {
            renderList = new ArrayList<>();
            renderList.add(new AiSummaryEngine.AiPointItem(null, -1, fallbackRawAnswer));
        }

        float density = context.getResources().getDisplayMetrics().density;
        int p14 = (int) (14 * density);
        int p8 = (int) (8 * density);
        int p3 = (int) (3 * density);
        int mb10 = (int) (10 * density);
        int mb6 = (int) (6 * density);
        boolean hasLens = lensBoxes != null && !lensBoxes.isEmpty();

        View firstNewCard = null;

        for (int i = 0; i < renderList.size(); i++) {
            AiSummaryEngine.AiPointItem item = renderList.get(i);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_chip_content);
            card.setPadding(p14, p14, p14, p14);
            card.setFocusable(true);
            card.setClickable(true);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.bottomMargin = mb10;
            card.setLayoutParams(cardParams);

            if (hasLens && i == 0) {
                // Interactive Lens header banner
                LinearLayout lensHeader = new LinearLayout(context);
                lensHeader.setOrientation(LinearLayout.HORIZONTAL);
                lensHeader.setGravity(Gravity.CENTER_VERTICAL);
                lensHeader.setBackgroundResource(R.drawable.badge_timestamp);
                lensHeader.setPadding(p8, p3, p8, p3);
                LinearLayout.LayoutParams lensParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lensParams.bottomMargin = mb6;
                lensHeader.setLayoutParams(lensParams);

                boolean isEn = "en".equalsIgnoreCase(uiLanguage);
                TextView tvLensBadge = new TextView(context);
                tvLensBadge.setText(isEn
                        ? "👁️ Lens: " + lensBoxes.size() + " translations (OK to toggle)"
                        : "👁️ Lens: " + lensBoxes.size() + " traducciones (OK para activar/ocultar)");
                tvLensBadge.setTextColor(0xFF8AB4F8);
                tvLensBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                tvLensBadge.setTypeface(tvLensBadge.getTypeface(), Typeface.BOLD);
                lensHeader.addView(tvLensBadge);
                card.addView(lensHeader);

                card.setOnClickListener(v -> toggleLensOverlay(lensBoxes, context));
            }

            if (item.hasTimestamp) {
                // Top header row with timestamp badge
                LinearLayout headerRow = new LinearLayout(context);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                headerParams.bottomMargin = mb6;
                headerRow.setLayoutParams(headerParams);

                TextView badge = new TextView(context);
                badge.setText("⏱️ " + item.timestampStr);
                badge.setBackgroundResource(R.drawable.badge_timestamp);
                badge.setPadding(p8, p3, p8, p3);
                badge.setTextColor(0xFF8AB4F8);
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                badge.setTypeface(badge.getTypeface(), android.graphics.Typeface.BOLD);

                TextView hint = new TextView(context);
                hint.setText("   •   Presiona OK para reproducir aquí");
                hint.setTextColor(0xFF9AA0A6);
                hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);

                headerRow.addView(badge);
                headerRow.addView(hint);
                card.addView(headerRow);

                TextView tvText = new TextView(context);
                tvText.setText(item.text);
                tvText.setTextColor(0xFFFFFFFF);
                tvText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                tvText.setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, context.getResources().getDisplayMetrics()), 1.0f);
                card.addView(tvText);

                card.setOnClickListener(v -> {
                    MediaNotificationListener.seekToPosition(item.timestampMs);
                    Toast.makeText(context, "⏩ Saltando a " + item.timestampStr + "...", Toast.LENGTH_SHORT).show();
                });
            } else {
                TextView tvText = new TextView(context);
                tvText.setText(hasLens ? item.text : "•  " + item.text);
                tvText.setTextColor(0xFFEEEEEE);
                tvText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                tvText.setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, context.getResources().getDisplayMetrics()), 1.0f);
                card.addView(tvText);
            }

            card.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    centerViewInScrollView(v);
                }
            });

            containerAiChips.addView(card);
            if (firstNewCard == null) {
                firstNewCard = card;
            }
        }

        if (autoFocusFirst && firstNewCard != null) {
            final View target = firstNewCard;
            mainHandler.post(() -> {
                target.requestFocus();
                centerViewInScrollView(target);
            });
        }
    }

    private void renderSuggestedQuestions(Context context, List<String> rawSuggestedQuestions) {
        if (layoutSuggestedSection == null) return;

        layoutSuggestedSection.setVisibility(View.GONE);
        btnSuggested1.setVisibility(View.GONE);
        btnSuggested2.setVisibility(View.GONE);
        btnSuggested3.setVisibility(View.GONE);
        btnSuggested1.setAlpha(0f);
        btnSuggested2.setAlpha(0f);
        btnSuggested3.setAlpha(0f);

        List<String> suggestedQuestions = filterDeduplicatedQuestions(rawSuggestedQuestions);

        if (suggestedQuestions == null || suggestedQuestions.isEmpty()) return;

        layoutSuggestedSection.setVisibility(View.VISIBLE);

        final int count = suggestedQuestions.size();
        if (count >= 1) btnSuggested1.setText("1. " + suggestedQuestions.get(0));
        if (count >= 2) btnSuggested2.setText("2. " + suggestedQuestions.get(1));
        if (count >= 3) btnSuggested3.setText("3. " + suggestedQuestions.get(2));

        // Staggered progressive appearance: questions appear one by one
        mainHandler.postDelayed(() -> {
            if (!isShowing) return;
            btnSuggested1.setVisibility(View.VISIBLE);
            btnSuggested1.animate().alpha(1f).setDuration(250).start();
        }, 350);

        if (count >= 2) {
            mainHandler.postDelayed(() -> {
                if (!isShowing) return;
                btnSuggested2.setVisibility(View.VISIBLE);
                btnSuggested2.animate().alpha(1f).setDuration(250).start();
            }, 700);
        }

        if (count >= 3) {
            mainHandler.postDelayed(() -> {
                if (!isShowing) return;
                btnSuggested3.setVisibility(View.VISIBLE);
                btnSuggested3.animate().alpha(1f).setDuration(250).start();
            }, 1050);
        }
    }

    private List<String> filterDeduplicatedQuestions(List<String> rawList) {
        if (rawList == null) return Collections.emptyList();
        List<String> filtered = new ArrayList<>();
        for (String q : rawList) {
            if (q == null || q.trim().isEmpty()) continue;
            String clean = q.replaceAll("^[0-9]+[.)-]\\s*", "").trim();
            String norm = normalizeTitle(clean);
            if (norm.isEmpty()) continue;

            boolean isDuplicate = false;
            for (String asked : askedQuestionsSet) {
                if (norm.equals(asked)) {
                    isDuplicate = true;
                    break;
                }
                if (norm.length() >= 8 && asked.length() >= 8) {
                    if (norm.contains(asked) || asked.contains(norm)) {
                        isDuplicate = true;
                        break;
                    }
                }
            }
            if (!isDuplicate) {
                for (String added : filtered) {
                    String normAdded = normalizeTitle(added);
                    if (norm.equals(normAdded)) {
                        isDuplicate = true;
                        break;
                    }
                }
            }
            if (!isDuplicate) {
                filtered.add(clean);
            }
        }
        return filtered;
    }

    private void toggleVoiceInput(Context context) {
        if (isListening) {
            stopVoiceInput();
        } else {
            startVoiceInput(context);
        }
    }

    private void stopVoiceInput() {
        if (!isListening) return;
        isListening = false;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
        }
        updateMicButtonState(false);
        removeListeningCard();
    }

    private void startVoiceInput(Context context) {
        if (isListening) return;

        if (context.checkCallingOrSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "⚠️ Permiso de micrófono requerido (RECORD_AUDIO)", Toast.LENGTH_LONG).show();
            Log.w(TAG, "RECORD_AUDIO permission not granted");
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "⚠️ Reconocimiento de voz no disponible en este sistema", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.getApplicationContext());
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "SpeechRecognizer onReadyForSpeech");
                    updateListeningCardText("🎙️ Escuchando... Habla ahora al micrófono");
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "SpeechRecognizer onBeginningOfSpeech");
                    updateListeningCardText("🎙️ Detectando voz...");
                }

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "SpeechRecognizer onEndOfSpeech");
                    updateListeningCardText("⏳ Procesando pregunta...");
                }

                @Override
                public void onError(int error) {
                    Log.w(TAG, "SpeechRecognizer onError: " + error);
                    isListening = false;
                    updateMicButtonState(false);
                    removeListeningCard();

                    String msg;
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            msg = "🎙️ No se detectó ninguna pregunta. Presiona [🎙️ Hablar] para reintentar.";
                            break;
                        case SpeechRecognizer.ERROR_AUDIO:
                            msg = "🎙️ Error de audio en el micrófono.";
                            break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            msg = "🎙️ Permiso de micrófono insuficiente.";
                            break;
                        default:
                            msg = "🎙️ Escucha finalizada (código " + error + ").";
                            break;
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    updateMicButtonState(false);
                    removeListeningCard();

                    ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        String recognized = matches.get(0).trim();
                        if (!recognized.isEmpty()) {
                            Log.d(TAG, "Speech recognition result: " + recognized);
                            executePrompt(context, recognized, false);
                        }
                    } else {
                        Toast.makeText(context, "🎙️ No se reconoció texto. Prueba hablar más cerca.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults != null ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        String partial = matches.get(0).trim();
                        if (!partial.isEmpty()) {
                            updateListeningCardText("🎙️ \"" + partial + "...\"");
                        }
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

            isListening = true;
            updateMicButtonState(true);
            showListeningCard(context);
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "SpeechRecognizer started listening successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing or starting SpeechRecognizer", e);
            isListening = false;
            updateMicButtonState(false);
            removeListeningCard();
            Toast.makeText(context, "Error al iniciar micrófono: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMicButtonState(boolean listening) {
        if (btnAiMic != null) {
            boolean isEn = "en".equalsIgnoreCase(uiLanguage);
            if (listening) {
                btnAiMic.setText(isEn ? "🔴 Listening..." : "🔴 Escuchando...");
                btnAiMic.setTextColor(0xFFFF6B6B);
            } else {
                btnAiMic.setText(isEn ? "🎙️ Talk" : "🎙️ Hablar");
                btnAiMic.setTextColor(0xFF8AB4F8);
            }
        }
    }

    private void showListeningCard(Context context) {
        if (containerAiChips == null) return;
        removeListeningCard();

        float density = context.getResources().getDisplayMetrics().density;
        int p14 = (int) (14 * density);
        int mb10 = (int) (10 * density);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.card_chip_content);
        card.setPadding(p14, p14, p14, p14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = mb10;
        card.setLayoutParams(params);

        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        int pSize = (int) (22 * density);
        LinearLayout.LayoutParams progParams = new LinearLayout.LayoutParams(pSize, pSize);
        progParams.rightMargin = (int) (12 * density);
        spinner.setLayoutParams(progParams);

        TextView tv = new TextView(context);
        tv.setText("🎙️ Escuchando... Habla al micrófono del control remoto");
        tv.setTextColor(0xFF8AB4F8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);

        card.addView(spinner);
        card.addView(tv);

        containerAiChips.addView(card);
        currentListeningCard = card;
        textListeningStatus = tv;

        scrollContent.post(() -> centerViewInScrollView(card));
    }

    private void updateListeningCardText(String text) {
        mainHandler.post(() -> {
            if (textListeningStatus != null && currentListeningCard != null) {
                textListeningStatus.setText(text);
            }
        });
    }

    private void removeListeningCard() {
        if (currentListeningCard != null && containerAiChips != null) {
            containerAiChips.removeView(currentListeningCard);
            currentListeningCard = null;
            textListeningStatus = null;
        }
    }

    private void centerViewInScrollView(View v) {
        if (scrollContent == null || v == null) return;
        int[] vLoc = new int[2];
        int[] svLoc = new int[2];
        v.getLocationOnScreen(vLoc);
        scrollContent.getLocationOnScreen(svLoc);
        int targetY = vLoc[1] - svLoc[1] + scrollContent.getScrollY() - (scrollContent.getHeight() - v.getHeight()) / 2;
        scrollContent.smoothScrollTo(0, Math.max(0, targetY));
    }
}
