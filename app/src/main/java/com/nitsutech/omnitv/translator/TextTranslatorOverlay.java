package com.nitsutech.omnitv.translator;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nitsutech.omnitv.ButtonMappingService;
import com.nitsutech.omnitv.I18n;
import com.nitsutech.omnitv.MediaNotificationListener;
import com.nitsutech.omnitv.R;
import com.nitsutech.omnitv.comments.YouTubeCommentFetcher;
import com.nitsutech.omnitv.comments.YouTubeCommentItem;
import com.nitsutech.omnitv.comments.YouTubeCommentTranslator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Text Translator: universal side-drawer translator for any app.
 * Origins: UI Text (accessibility tree), Visual OCR (ML Kit), Comments (YouTube/SmartTube), Lens AI (Gemini vision).
 * RULE: OmniTV's own UI (com.nitsutech.omnitv) is always excluded from every origin.
 */
public class TextTranslatorOverlay {
    private static final String TAG = "TextTranslatorOverlay";
    private static final String OWN_PACKAGE = "com.nitsutech.omnitv";
    private static final String PREFS_NAME = "overlay_prefs";
    private static final String PREF_ENGINE = "tt_engine";
    private static final String PREF_LANG = "tt_lang";

    public static final int SRC_UI = 0, SRC_OCR = 1, SRC_COMMENTS = 2, SRC_LENS = 3;
    private static final String[] LANGS = {"es", "en", "ko", "ja", "pt-BR"};

    private static TextTranslatorOverlay instance;

    private Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;

    // UI elements
    private final TextView[] srcButtons = new TextView[4];
    private TextView btnEngine;
    private TextView btnLang;
    private TextView btnTranslateAll;
    private View btnClose;
    private TextView textStatus;
    private ScrollView scrollList;
    private LinearLayout containerItems;
    private View dimmerFilter;
    private View blueLightFilter;

    // State
    private int currentSource = SRC_UI;
    private int currentEngine = 0; // 0: Standard, 1: AI Engine
    private String currentTargetLang = "es";
    private final List<YouTubeCommentItem> mainItems = new ArrayList<>();
    private final List<YouTubeCommentItem> replyItems = new ArrayList<>();
    private boolean isShowingReplies = false;
    private int mainReturnIndex = 0;
    private int mainReturnScrollY = 0;
    private String activeVideoId = null;
    private int loadGeneration = 0;
    private final Map<YouTubeCommentItem, TextView[]> cardViews = new HashMap<>();

    // Comments pagination
    private String nextContinuationToken = null;
    private boolean isLoadingMore = false;
    private TextView btnLoadMore = null;
    private int preserveScrollY = -1;
    private boolean suppressCenter = false;

    private final Handler longClickHandler = new Handler(Looper.getMainLooper());
    private boolean isLongClickTriggered = false;
    private final Handler mHoldHandler = new Handler(Looper.getMainLooper());
    private int mHoldingKeyCode = -1;
    private int mHoldTickCount = 0;
    private Runnable mHoldRunnable;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            if (overlayView == null) return;
            View current = overlayView.findFocus();
            if (current != null && current.isLongClickable()) {
                isLongClickTriggered = true;
                current.performLongClick();
            }
        }
    };

    public TextTranslatorOverlay(Context context) {
        this.context = context;
    }

    public static synchronized TextTranslatorOverlay getInstance(Context context) {
        if (instance == null) {
            instance = new TextTranslatorOverlay(context);
        } else if (context != null) {
            instance.context = context;
        }
        return instance;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public synchronized void show() {
        show(null);
    }

    public synchronized void show(Context serviceContext) {
        if (serviceContext != null) {
            this.context = serviceContext;
        }
        if (isShowing) return;

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentEngine = prefs.getInt(PREF_ENGINE, 0);
        currentTargetLang = prefs.getString(PREF_LANG, "es");
        if (!isSupportedTargetLanguage(currentTargetLang)) {
            // Migrate the former "original" destination to Spanish, its previous effective target.
            currentTargetLang = "es";
            prefs.edit().putString(PREF_LANG, currentTargetLang).apply();
        }

        LayoutInflater inflater = LayoutInflater.from(I18n.getLocalizedContext(context));
        overlayView = inflater.inflate(R.layout.view_text_translator_overlay, null);

        bindViews();
        setupListeners();
        updateMenuInternalFilters();
        updateEngineButtonLabel();
        updateLangButtonLabel();

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        try {
            if (overlayView.getParent() != null) {
                try {
                    windowManager.removeViewImmediate(overlayView);
                } catch (Exception ignored) {}
            }
            windowManager.addView(overlayView, params);
            isShowing = true;

            View rootScrim = overlayView.findViewById(R.id.tt_overlay_root);
            if (rootScrim != null) {
                rootScrim.setOnClickListener(v -> dismiss());
            }
            View drawerPanel = overlayView.findViewById(R.id.tt_panel_drawer);
            if (drawerPanel != null) {
                drawerPanel.setOnClickListener(v -> {});
            }

            // Default origin is UI Text (native accessible, lightweight, no comments auto-loaded)
            resolveVideoIdFromMedia();
            currentSource = SRC_UI;
            updateSourceButtons();
            loadCurrentSource();

            overlayView.postDelayed(() -> {
                if (srcButtons[currentSource] != null) srcButtons[currentSource].requestFocus();
            }, 100);
        } catch (Exception e) {
            Log.e(TAG, "Error displaying TextTranslatorOverlay", e);
            dismiss();
        }
    }

    public void dismiss() {
        loadGeneration++;
        stopHoldRepeat();
        longClickHandler.removeCallbacks(longClickRunnable);
        isLongClickTriggered = false;
        if (!isShowing || overlayView == null) return;
        try {
            if (scrollList != null) {
                scrollList.clearFocus();
            }
            if (windowManager != null) {
                windowManager.removeView(overlayView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing TextTranslatorOverlay", e);
        } finally {
            overlayView = null;
            scrollList = null;
            containerItems = null;
            isShowing = false;
            isShowingReplies = false;
            nextContinuationToken = null;
            isLoadingMore = false;
        }
    }

    private void bindViews() {
        srcButtons[SRC_UI] = overlayView.findViewById(R.id.tt_src_ui);
        srcButtons[SRC_OCR] = overlayView.findViewById(R.id.tt_src_ocr);
        srcButtons[SRC_COMMENTS] = overlayView.findViewById(R.id.tt_src_comments);
        srcButtons[SRC_LENS] = overlayView.findViewById(R.id.tt_src_lens);
        btnEngine = overlayView.findViewById(R.id.tt_btn_engine);
        btnLang = overlayView.findViewById(R.id.tt_btn_lang);
        btnTranslateAll = overlayView.findViewById(R.id.tt_btn_translate_all);
        textStatus = overlayView.findViewById(R.id.tt_text_status);
        scrollList = overlayView.findViewById(R.id.tt_scroll_list);
        containerItems = overlayView.findViewById(R.id.tt_container_items);
        dimmerFilter = overlayView.findViewById(R.id.tt_menu_dimmer_filter);
        blueLightFilter = overlayView.findViewById(R.id.tt_menu_blue_light_filter);

        btnClose = overlayView.findViewById(R.id.tt_btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }

    private void setupListeners() {
        for (int i = 0; i < srcButtons.length; i++) {
            final int mode = i;
            srcButtons[i].setOnClickListener(v -> {
                currentSource = mode;
                updateSourceButtons();
                loadCurrentSource();
            });
        }

        btnEngine.setOnClickListener(v -> {
            currentEngine = (currentEngine == 0) ? 1 : 0;
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_ENGINE, currentEngine).apply();
            updateEngineButtonLabel();
            if (currentEngine == 1 && !TextTranslatorEngine.hasApiKey(context)) {
                Toast.makeText(context, I18n.get(context, R.string.tt_ai_no_key), Toast.LENGTH_SHORT).show();
            }
        });

        btnLang.setOnClickListener(v -> {
            int idx = 0;
            for (int i = 0; i < LANGS.length; i++) if (LANGS[i].equals(currentTargetLang)) idx = i;
            currentTargetLang = LANGS[(idx + 1) % LANGS.length];
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(PREF_LANG, currentTargetLang).apply();
            updateLangButtonLabel();
            refreshDisplayedItems();
        });

        btnTranslateAll.setOnClickListener(v -> translateAllCurrentItems());

    }

    private void updateSourceButtons() {
        for (int i = 0; i < srcButtons.length; i++) {
            boolean selected = (i == currentSource);
            if (i == SRC_LENS) {
                srcButtons[i].setText(I18n.get(context, R.string.tt_src_lens) + " · " + getSelectedAiModelLabel());
            }
            srcButtons[i].setTextColor(selected ? 0xFF8AB4F8 : 0xFFFFFFFF);
            srcButtons[i].setAlpha(selected ? 1f : 0.75f);
            if (selected) {
                srcButtons[i].setPaintFlags(srcButtons[i].getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            } else {
                srcButtons[i].setPaintFlags(srcButtons[i].getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            }
        }
    }

    private void updateEngineButtonLabel() {
        if (currentEngine == 0) {
            btnEngine.setText(I18n.get(context, R.string.tt_engine_standard));
            btnEngine.setTextColor(0xFFA5D6A7);
        } else {
            btnEngine.setText(I18n.get(context, R.string.tt_engine_ai));
            btnEngine.setTextColor(0xFFCE93D8);
        }
    }

    private String getSelectedAiModelLabel() {
        SharedPreferences prefs = context.getSharedPreferences(com.nitsutech.omnitv.ai.AiSummaryEngine.PREFS_NAME, Context.MODE_PRIVATE);
        int provider = prefs.getInt(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_AI_PROVIDER, 0);
        if (provider == 1) {
            String model = prefs.getString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_OPENROUTER_MODEL,
                    com.nitsutech.omnitv.ai.AiSummaryEngine.DEFAULT_OPENROUTER_MODEL).trim();
            return model.isEmpty() ? com.nitsutech.omnitv.ai.AiSummaryEngine.DEFAULT_OPENROUTER_MODEL : model;
        }
        String model = prefs.getString(com.nitsutech.omnitv.ai.AiSummaryEngine.KEY_GEMINI_MODEL,
                com.nitsutech.omnitv.ai.AiSummaryEngine.DEFAULT_GEMINI_MODEL).trim();
        return model.isEmpty() ? com.nitsutech.omnitv.ai.AiSummaryEngine.DEFAULT_GEMINI_MODEL : model;
    }

    private void updateLangButtonLabel() {
        int res;
        switch (currentTargetLang) {
            case "en": res = R.string.comments_lang_en; break;
            case "ko": res = R.string.tt_lang_ko; break;
            case "ja": res = R.string.tt_lang_ja; break;
            case "pt-BR": res = R.string.tt_lang_pt_br; break;
            default: res = R.string.comments_lang_es; break;
        }
        btnLang.setText(I18n.get(context, res));
    }

    private boolean isSupportedTargetLanguage(String language) {
        for (String supportedLanguage : LANGS) {
            if (supportedLanguage.equals(language)) return true;
        }
        return false;
    }

    private boolean isValidVideoId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }

    // ------------------------------------------------------------------------------------------
    // Loading per origin
    // ------------------------------------------------------------------------------------------

    private void loadCurrentSource() {
        final int gen = ++loadGeneration;
        isShowingReplies = false;
        nextContinuationToken = null;
        isLoadingMore = false;
        mainItems.clear();
        replyItems.clear();
        containerItems.removeAllViews();

        switch (currentSource) {
            case SRC_UI:
                textStatus.setText(I18n.get(context, R.string.tt_scanning));
                loadUiText(gen);
                break;
            case SRC_OCR:
                textStatus.setText(I18n.get(context, R.string.tt_scanning));
                captureCleanScreen(gen, bitmap -> runOcr(gen, bitmap));
                break;
            case SRC_COMMENTS:
                textStatus.setText(I18n.get(context, R.string.comments_loading));
                loadComments(gen);
                break;
            case SRC_LENS:
                textStatus.setText(I18n.get(context, R.string.tt_scanning));
                if (!TextTranslatorEngine.hasApiKey(context)) {
                    textStatus.setText(I18n.get(context, R.string.tt_ai_no_key));
                    return;
                }
                captureCleanScreen(gen, bitmap -> runLens(gen, bitmap));
                break;
        }
    }

    private interface BitmapConsumer {
        void accept(Bitmap bitmap);
    }

    /**
     * Captures the background app with OmniTV's translator overlay hidden (View.INVISIBLE),
     * so the drawer itself is never captured/translated.
     */
    private void captureCleanScreen(final int gen, final BitmapConsumer consumer) {
        final ButtonMappingService svc = ButtonMappingService.instance;
        if (svc == null || overlayView == null) {
            textStatus.setText(I18n.get(context, R.string.tt_capture_error));
            return;
        }
        final View ov = overlayView;
        ov.setVisibility(View.INVISIBLE);
        // Give SurfaceFlinger time to composite a frame without the drawer
        handler.postDelayed(() -> svc.captureScreenForVision(new ButtonMappingService.ScreenCaptureCallback() {
            @Override
            public void onCaptured(Bitmap bitmap) {
                ov.setVisibility(View.VISIBLE);
                if (gen != loadGeneration || !isShowing) return;
                consumer.accept(bitmap);
            }

            @Override
            public void onError(String error) {
                ov.setVisibility(View.VISIBLE);
                if (gen != loadGeneration || !isShowing) return;
                textStatus.setText(I18n.get(context, R.string.tt_capture_error));
            }
        }), 250);
    }

    private void runOcr(final int gen, Bitmap bitmap) {
        TextOcrEngine.recognize(bitmap, new TextOcrEngine.OcrCallback() {
            @Override
            public void onResult(List<String> blocks) {
                handler.post(() -> {
                    if (gen != loadGeneration || !isShowing) return;
                    applyTextBlocks(blocks, I18n.get(context, R.string.tt_card_ocr));
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    if (gen != loadGeneration || !isShowing) return;
                    textStatus.setText(I18n.get(context, R.string.tt_no_text));
                });
            }
        });
    }

    private void runLens(final int gen, Bitmap bitmap) {
        TextTranslatorEngine.extractTextFromImage(context, bitmap, new TextTranslatorEngine.ListCallback() {
            @Override
            public void onSuccess(List<String> result) {
                if (gen != loadGeneration || !isShowing) return;
                applyTextBlocks(result, I18n.get(context, R.string.tt_card_lens));
            }

            @Override
            public void onError(String error) {
                if (gen != loadGeneration || !isShowing) return;
                textStatus.setText("NO_API_KEY".equals(error) ? I18n.get(context, R.string.tt_ai_no_key) : I18n.get(context, R.string.tt_ai_error));
            }
        });
    }

    private void applyTextBlocks(List<String> blocks, String label) {
        mainItems.clear();
        for (int i = 0; i < blocks.size(); i++) {
            YouTubeCommentItem item = new YouTubeCommentItem();
            item.commentId = "tt_" + i;
            item.originalText = blocks.get(i);
            item.authorName = label;
            mainItems.add(item);
        }
        if (mainItems.isEmpty()) {
            textStatus.setText(I18n.get(context, R.string.tt_no_text));
        } else {
            textStatus.setText(String.format(I18n.get(context, R.string.tt_items_count), mainItems.size()));
        }
        renderList(mainItems, -1);
    }

    // ---- UI Text (native accessibility tree) ----

    private void loadUiText(final int gen) {
        final ButtonMappingService svc = ButtonMappingService.instance;
        if (svc == null) {
            textStatus.setText(I18n.get(context, R.string.tt_no_text));
            return;
        }
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        try {
            List<AccessibilityWindowInfo> windows = svc.getWindows();
            if (windows != null && !windows.isEmpty()) {
                for (AccessibilityWindowInfo win : windows) {
                    if (win == null) continue;
                    // Exclude our own accessibility overlays (Text Translator, Quick Menu, Dimmer, Clock, HUDs)
                    if (win.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
                    AccessibilityNodeInfo root = win.getRoot();
                    if (root != null) {
                        collectNodeTexts(root, texts);
                        root.recycle();
                    }
                }
            } else {
                AccessibilityNodeInfo root = svc.getRootInActiveWindow();
                if (root != null) {
                    collectNodeTexts(root, texts);
                    root.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scraping UI text", e);
        }
        List<String> list = new ArrayList<>(texts);
        applyTextBlocks(list, I18n.get(context, R.string.tt_card_ui));
    }

    private void collectNodeTexts(AccessibilityNodeInfo node, LinkedHashSet<String> out) {
        if (node == null || out.size() >= 150) return;
        CharSequence pkg = node.getPackageName();
        if (pkg != null && pkg.toString().equals(OWN_PACKAGE)) return;

        if (node.isVisibleToUser()) {
            CharSequence text = node.getText();
            if (text == null || text.toString().trim().isEmpty()) text = node.getContentDescription();
            if (text != null) {
                String s = text.toString().trim();
                if (s.length() >= 2 && !s.matches("[\\d\\W]*")) out.add(s);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectNodeTexts(child, out);
                child.recycle();
            }
        }
    }

    // ---- Comments (YouTube / SmartTube via InnerTube) ----

    private void resolveVideoIdFromMedia() {
        activeVideoId = null;
        try {
            MediaNotificationListener.LiveVideoInfo info = MediaNotificationListener.getLiveVideoInfo(context);
            if (info != null && info.mediaId != null && !info.mediaId.isEmpty()) {
                activeVideoId = MediaNotificationListener.extractCleanVideoId(info.mediaId);
            }
            if (!isValidVideoId(activeVideoId) && info != null && info.title != null) {
                activeVideoId = com.nitsutech.omnitv.vot.YouTubeCaptionFetcher.getCachedVideoId(info.title);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving video id", e);
        }
    }

    private void loadComments(final int gen) {
        // Fallback: scrape a title from the foreground app (OmniTV nodes excluded) to resolve the video ID
        final String screenTitle = isValidVideoId(activeVideoId) ? null : findScreenTitle();

        executor.execute(() -> {
            String vId = activeVideoId;
            if (!isValidVideoId(vId)) {
                MediaNotificationListener.LiveVideoInfo info = MediaNotificationListener.getLiveVideoInfo(context);
                if (info != null && info.title != null && !info.title.isEmpty()) {
                    vId = com.nitsutech.omnitv.vot.YouTubeCaptionFetcher.resolveVideoId(info.title);
                }
                if (!isValidVideoId(vId) && screenTitle != null) {
                    vId = com.nitsutech.omnitv.vot.YouTubeCaptionFetcher.resolveVideoId(screenTitle);
                }
                activeVideoId = vId;
            }

            if (!isValidVideoId(vId)) {
                handler.post(() -> {
                    if (gen == loadGeneration && isShowing) textStatus.setText(I18n.get(context, R.string.comments_empty));
                });
                return;
            }

            YouTubeCommentFetcher.fetchVideoCommentsAsync(vId, null, new YouTubeCommentFetcher.CommentsCallback() {
                @Override
                public void onSuccess(List<YouTubeCommentItem> comments, String nextToken) {
                    handler.post(() -> {
                        if (gen != loadGeneration || !isShowing) return;
                        mainItems.clear();
                        mainItems.addAll(comments);
                        nextContinuationToken = nextToken;
                        if (mainItems.isEmpty()) {
                            textStatus.setText(I18n.get(context, R.string.comments_empty));
                        } else {
                            textStatus.setText(String.format(I18n.get(context, R.string.tt_comments_count), mainItems.size()));
                        }
                        renderList(mainItems, -1);
                    });
                }

                @Override
                public void onError(String error) {
                    handler.post(() -> {
                        if (gen == loadGeneration && isShowing) textStatus.setText(I18n.get(context, R.string.comments_empty));
                    });
                }
            });
        });
    }

    private String findScreenTitle() {
        ButtonMappingService svc = ButtonMappingService.instance;
        if (svc == null) return null;
        try {
            List<AccessibilityWindowInfo> windows = svc.getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo win : windows) {
                    if (win == null || win.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
                    AccessibilityNodeInfo root = win.getRoot();
                    if (root != null) {
                        String t = findVideoTitleInNode(root);
                        root.recycle();
                        if (t != null) return t;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding screen title", e);
        }
        return null;
    }

    private String findVideoTitleInNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence pkg = node.getPackageName();
        if (pkg != null && pkg.toString().equals(OWN_PACKAGE)) return null;

        CharSequence text = node.getText();
        if (text != null) {
            String str = text.toString().trim();
            if (str.length() > 15 && !str.contains("@") && !str.startsWith("http")
                    && !str.contains("SmartTube") && !str.contains("Comment") && !str.contains("Comentario")) {
                return str;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String t = findVideoTitleInNode(child);
                child.recycle();
                if (t != null) return t;
            }
        }
        return null;
    }

    private void loadMoreComments() {
        if (isLoadingMore || nextContinuationToken == null || nextContinuationToken.isEmpty()) return;
        isLoadingMore = true;
        final int gen = loadGeneration;
        if (btnLoadMore != null) {
            btnLoadMore.setText(I18n.get(context, R.string.comments_loading));
        }

        YouTubeCommentFetcher.fetchVideoCommentsAsync(null, nextContinuationToken, new YouTubeCommentFetcher.CommentsCallback() {
            @Override
            public void onSuccess(List<YouTubeCommentItem> newComments, String nextToken) {
                handler.post(() -> {
                    isLoadingMore = false;
                    if (gen != loadGeneration || !isShowing) return;
                    nextContinuationToken = nextToken;
                    if (newComments != null && !newComments.isEmpty()) {
                        int firstNew = mainItems.size();
                        mainItems.addAll(newComments);
                        textStatus.setText(String.format(I18n.get(context, R.string.tt_comments_count), mainItems.size()));
                        preserveScrollY = scrollList.getScrollY();
                        renderList(mainItems, firstNew);
                    } else if (btnLoadMore != null) {
                        btnLoadMore.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    isLoadingMore = false;
                    if (btnLoadMore != null) {
                        btnLoadMore.setText(I18n.get(context, R.string.comments_load_more));
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------------------------

    private void renderList(List<YouTubeCommentItem> list, int focusIndex) {
        containerItems.removeAllViews();
        cardViews.clear();
        btnLoadMore = null;
        float density = context.getResources().getDisplayMetrics().density;
        View toFocus = null;

        for (int i = 0; i < list.size(); i++) {
            final YouTubeCommentItem item = list.get(i);
            final boolean isThreadParent = isShowingReplies && !replyItems.isEmpty() && item == replyItems.get(0);

            final LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(Math.round(14 * density), Math.round(12 * density), Math.round(14 * density), Math.round(12 * density));

            final GradientDrawable defaultBg = new GradientDrawable();
            defaultBg.setColor(0xFF1B1D24);
            defaultBg.setCornerRadius(10 * density);
            defaultBg.setStroke(Math.round(1 * density), 0x33FFFFFF);

            final GradientDrawable focusedBg = new GradientDrawable();
            focusedBg.setColor(0xFF2C3246);
            focusedBg.setCornerRadius(10 * density);
            focusedBg.setStroke(Math.round(2 * density), 0xFF8AB4F8);

            final GradientDrawable parentBg = new GradientDrawable();
            parentBg.setColor(0xFF202A38);
            parentBg.setCornerRadius(10 * density);
            parentBg.setStroke(Math.round(2 * density), 0xFF80CBC4);

            final GradientDrawable parentFocusedBg = new GradientDrawable();
            parentFocusedBg.setColor(0xFF293849);
            parentFocusedBg.setCornerRadius(10 * density);
            parentFocusedBg.setStroke(Math.round(3 * density), 0xFF80CBC4);

            card.setBackground(isThreadParent ? parentBg : defaultBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Math.round(10 * density);
            card.setLayoutParams(lp);

            if (isThreadParent) {
                TextView tvThreadParent = new TextView(context);
                tvThreadParent.setText(I18n.get(context, R.string.tt_thread_parent));
                tvThreadParent.setTextColor(0xFF80CBC4);
                tvThreadParent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvThreadParent.setTypeface(Typeface.DEFAULT_BOLD);
                tvThreadParent.setPadding(0, 0, 0, Math.round(5 * density));
                card.addView(tvThreadParent);
            }

            TextView tvAuthor = new TextView(context);
            String authorStr = (item.authorName != null && !item.authorName.isEmpty()) ? item.authorName : I18n.get(context, R.string.tt_viewer);
            if (item.publishedTimeText != null && !item.publishedTimeText.isEmpty()) {
                authorStr += " • " + item.publishedTimeText;
            }
            tvAuthor.setText(authorStr);
            tvAuthor.setTextColor(0xFF8AB4F8);
            tvAuthor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvAuthor.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(tvAuthor);

            final TextView tvBody = new TextView(context);
            tvBody.setText(getTextForLanguage(item));
            tvBody.setTextColor(0xFFEEEEEE);
            tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvBody.setPadding(0, Math.round(4 * density), 0, Math.round(8 * density));
            card.addView(tvBody);

            LinearLayout actionsRow = new LinearLayout(context);
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setGravity(Gravity.CENTER_VERTICAL);

            final TextView btnItemTranslate = new TextView(context);
            btnItemTranslate.setBackgroundResource(R.drawable.pill_youtube_tv);
            btnItemTranslate.setText(hasTranslationForCurrentLanguage(item) ? I18n.get(context, R.string.comments_btn_orig) : I18n.get(context, R.string.comments_btn_translate));
            btnItemTranslate.setTextColor(0xFF81D4FA);
            btnItemTranslate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnItemTranslate.setPadding(Math.round(10 * density), Math.round(4 * density), Math.round(10 * density), Math.round(4 * density));
            btnItemTranslate.setFocusable(false);
            btnItemTranslate.setClickable(false);
            actionsRow.addView(btnItemTranslate);

            cardViews.put(item, new TextView[]{tvBody, btnItemTranslate});

            if (!isThreadParent && item.replyCount > 0 && item.replyContinuationToken != null) {
                final TextView btnReplies = new TextView(context);
                btnReplies.setBackgroundResource(R.drawable.pill_youtube_tv);
                btnReplies.setText(String.format(I18n.get(context, R.string.comments_show_replies), item.replyCount));
                btnReplies.setTextColor(0xFFFFEB3B);
                btnReplies.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                btnReplies.setPadding(Math.round(10 * density), Math.round(4 * density), Math.round(10 * density), Math.round(4 * density));
                btnReplies.setFocusable(true);
                btnReplies.setClickable(true);
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rLp.setMarginStart(Math.round(8 * density));
                btnReplies.setLayoutParams(rLp);

                btnReplies.setTag(card);   // D-Pad Left returns to the card
                card.setTag(btnReplies);   // D-Pad Right goes to the replies button

                btnReplies.setOnClickListener(v -> openReplyThread(item));
                btnReplies.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) centerViewInScrollView(card);
                });
                actionsRow.addView(btnReplies);
            }

            card.addView(actionsRow);

            card.setOnClickListener(v -> {
                if (!isThreadParent && item.replyCount > 0 && item.replyContinuationToken != null) {
                    openReplyThread(item);
                }
            });
            card.setOnLongClickListener(v -> {
                toggleItemTranslation(item);
                return true;
            });
            card.setOnFocusChangeListener((v, hasFocus) -> {
                card.setBackground(hasFocus
                        ? (isThreadParent ? parentFocusedBg : focusedBg)
                        : (isThreadParent ? parentBg : defaultBg));
                if (hasFocus && !suppressCenter) centerViewInScrollView(card);
            });

            containerItems.addView(card);
            if (i == focusIndex) toFocus = card;
        }

        if (currentSource == SRC_COMMENTS && !isShowingReplies && nextContinuationToken != null && !nextContinuationToken.isEmpty()) {
            btnLoadMore = new TextView(context);
            btnLoadMore.setBackgroundResource(R.drawable.pill_youtube_tv);
            btnLoadMore.setText(I18n.get(context, R.string.comments_load_more));
            btnLoadMore.setTextColor(0xFF81D4FA);
            btnLoadMore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnLoadMore.setTypeface(Typeface.DEFAULT_BOLD);
            btnLoadMore.setGravity(Gravity.CENTER);
            btnLoadMore.setPadding(Math.round(16 * density), Math.round(10 * density), Math.round(16 * density), Math.round(10 * density));
            btnLoadMore.setFocusable(true);
            btnLoadMore.setClickable(true);

            LinearLayout.LayoutParams lpMore = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpMore.topMargin = Math.round(8 * density);
            lpMore.bottomMargin = Math.round(20 * density);
            btnLoadMore.setLayoutParams(lpMore);

            final TextView more = btnLoadMore;
            btnLoadMore.setOnClickListener(v -> loadMoreComments());
            btnLoadMore.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    centerViewInScrollView(more); // loading requires pressing OK (no auto-load, avoids runaway scroll)
                }
            });
            containerItems.addView(btnLoadMore);
        }

        if (toFocus != null) {
            final View f = toFocus;
            final int keepY = preserveScrollY;
            preserveScrollY = -1;
            if (keepY >= 0) {
                // Load-more: keep the current scroll position, no jump or fast scrolling
                scrollList.post(() -> {
                    scrollList.scrollTo(0, keepY);
                    suppressCenter = true;
                    f.requestFocus();
                    suppressCenter = false;
                });
            } else {
                f.post(f::requestFocus);
            }
        }
    }

    private String getTextForLanguage(YouTubeCommentItem item) {
        if (!hasTranslationForCurrentLanguage(item)) {
            return item.originalText;
        }
        return item.getDisplayText();
    }

    private boolean hasTranslationForCurrentLanguage(YouTubeCommentItem item) {
        return item.isTranslated && item.translatedText != null && !item.translatedText.isEmpty()
                && effectiveTarget().equalsIgnoreCase(item.targetLanguage != null ? item.targetLanguage : "");
    }

    private void updateCardViews(YouTubeCommentItem item) {
        TextView[] views = cardViews.get(item);
        if (views == null) return;
        views[0].setText(getTextForLanguage(item));
        views[1].setText(hasTranslationForCurrentLanguage(item) ? I18n.get(context, R.string.comments_btn_orig) : I18n.get(context, R.string.comments_btn_translate));
    }

    private void centerViewInScrollView(final View v) {
        if (scrollList == null || v == null) return;
        scrollList.post(() -> {
            if (scrollList == null || v == null) return;
            int[] vLoc = new int[2];
            int[] svLoc = new int[2];
            v.getLocationOnScreen(vLoc);
            scrollList.getLocationOnScreen(svLoc);
            int targetY = vLoc[1] - svLoc[1] + scrollList.getScrollY() - (scrollList.getHeight() - v.getHeight()) / 2;
            scrollList.smoothScrollTo(0, Math.max(0, targetY));
        });
    }

    // ------------------------------------------------------------------------------------------
    // Replies
    // ------------------------------------------------------------------------------------------

    private void openReplyThread(final YouTubeCommentItem parentItem) {
        if (parentItem.replyContinuationToken == null && parentItem.replies.isEmpty()) return;
        if (!isShowingReplies) {
            mainReturnIndex = getFocusedMainItemIndex();
            mainReturnScrollY = scrollList != null ? scrollList.getScrollY() : 0;
        }
        isShowingReplies = true;
        if (!parentItem.replies.isEmpty()) {
            showReplyItems(parentItem, parentItem.replies);
            return;
        }
        final int gen = loadGeneration;
        textStatus.setText(I18n.get(context, R.string.comments_loading));
        containerItems.removeAllViews();

        YouTubeCommentFetcher.fetchRepliesAsync(parentItem.replyContinuationToken, new YouTubeCommentFetcher.CommentsCallback() {
            @Override
            public void onSuccess(List<YouTubeCommentItem> replies, String nextToken) {
                handler.post(() -> {
                    if (gen != loadGeneration || !isShowing) return;
                    parentItem.replies.clear();
                    parentItem.replies.addAll(replies);
                    showReplyItems(parentItem, parentItem.replies);
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    if (gen == loadGeneration && isShowing) textStatus.setText(I18n.get(context, R.string.comments_empty));
                });
            }
        });
    }

    private void showReplyItems(YouTubeCommentItem parentItem, List<YouTubeCommentItem> replies) {
        replyItems.clear();
        replyItems.add(parentItem);
        replyItems.addAll(replies);
        textStatus.setText(String.format(I18n.get(context, R.string.tt_replies_count), replies.size()));
        renderList(replyItems, 0);
    }

    private int getFocusedMainItemIndex() {
        if (containerItems == null) return mainReturnIndex;
        View focused = overlayView != null ? overlayView.findFocus() : null;
        View child = focused;
        while (child != null && child.getParent() != containerItems) {
            if (!(child.getParent() instanceof View)) break;
            child = (View) child.getParent();
        }
        int index = containerItems.indexOfChild(child);
        return index >= 0 ? Math.min(index, Math.max(0, mainItems.size() - 1)) : mainReturnIndex;
    }

    private void showMainItems() {
        isShowingReplies = false;
        textStatus.setText(String.format(I18n.get(context, currentSource == SRC_COMMENTS ? R.string.tt_comments_count : R.string.tt_items_count), mainItems.size()));
        preserveScrollY = mainReturnScrollY;
        renderList(mainItems, mainReturnIndex);
    }

    // ------------------------------------------------------------------------------------------
    // Translation (Standard vs AI Engine)
    // ------------------------------------------------------------------------------------------

    private String effectiveTarget() {
        return currentTargetLang;
    }

    private void toggleItemTranslation(final YouTubeCommentItem item) {
        if (hasTranslationForCurrentLanguage(item)) {
            item.isTranslated = false;
            updateCardViews(item);
            return;
        }
        TextView[] views = cardViews.get(item);
        if (views != null) views[1].setText(I18n.get(context, R.string.comments_translating));
        List<YouTubeCommentItem> single = new ArrayList<>();
        single.add(item);
        translateItems(single, () -> updateCardViews(item));
    }

    private void translateAllCurrentItems() {
        final List<YouTubeCommentItem> target = isShowingReplies ? replyItems : mainItems;
        if (target.isEmpty()) return;
        textStatus.setText(I18n.get(context, R.string.comments_translating));
        translateItems(target, () -> {
            textStatus.setText(String.format(I18n.get(context, currentSource == SRC_COMMENTS ? (isShowingReplies ? R.string.tt_replies_count : R.string.tt_comments_count) : R.string.tt_items_count), target.size()));
            for (YouTubeCommentItem it : target) updateCardViews(it);
        });
    }

    private void refreshDisplayedItems() {
        final List<YouTubeCommentItem> target = isShowingReplies ? replyItems : mainItems;
        for (YouTubeCommentItem item : target) updateCardViews(item);
    }

    /** Translates the given items with the selected engine and runs onDone on the main thread. */
    private void translateItems(final List<YouTubeCommentItem> items, final Runnable onDone) {
        final String target = effectiveTarget();
        if (currentEngine == 0) {
            YouTubeCommentTranslator.translateBatchAsync(items, target, () -> handler.post(onDone));
            return;
        }

        if (!TextTranslatorEngine.hasApiKey(context)) {
            Toast.makeText(context, I18n.get(context, R.string.tt_ai_no_key), Toast.LENGTH_SHORT).show();
            handler.post(onDone);
            return;
        }
        final List<String> texts = new ArrayList<>();
        for (YouTubeCommentItem it : items) texts.add(it.originalText != null ? it.originalText : "");
        final int gen = loadGeneration;
        TextTranslatorEngine.translateBatch(context, texts, target, new TextTranslatorEngine.ListCallback() {
            @Override
            public void onSuccess(List<String> result) {
                if (!isShowing) return;
                for (int i = 0; i < items.size() && i < result.size(); i++) {
                    YouTubeCommentItem it = items.get(i);
                    it.translatedText = result.get(i);
                    it.isTranslated = true;
                    it.targetLanguage = target;
                }
                onDone.run();
            }

            @Override
            public void onError(String error) {
                if (!isShowing) return;
                Toast.makeText(context, I18n.get(context, "NO_API_KEY".equals(error) ? R.string.tt_ai_no_key : R.string.tt_ai_error), Toast.LENGTH_SHORT).show();
                onDone.run();
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // Dimmer / Blue light sync (Frame 0, no flashes)
    // ------------------------------------------------------------------------------------------

    private void updateMenuInternalFilters() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isDimmerActive = prefs.getBoolean("dimmer", false)
                    || prefs.getBoolean("night_dimmer", false)
                    || (ButtonMappingService.instance != null && ButtonMappingService.instance.isDimmerActive());
            int dimmerPct = prefs.getInt("dimmer_brightness_pct", 50);

            boolean isBlueLightActive = prefs.getBoolean("blue_light", false)
                    || prefs.getBoolean("night_blue_light", false)
                    || (ButtonMappingService.instance != null && ButtonMappingService.instance.isBlueLightActive());
            int blueLightPct = prefs.getInt("blue_light_pct", 50);
            if (blueLightPct == 0) blueLightPct = 50;

            if (dimmerFilter != null) {
                if (isDimmerActive && dimmerPct < 100) {
                    dimmerFilter.setVisibility(View.VISIBLE);
                    int alpha = (int) ((100 - dimmerPct) * 2.55);
                    dimmerFilter.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
                } else {
                    dimmerFilter.setVisibility(View.GONE);
                }
            }

            if (blueLightFilter != null) {
                if (isBlueLightActive && blueLightPct > 0) {
                    blueLightFilter.setVisibility(View.VISIBLE);
                    int alpha = (int) ((blueLightPct / 1000.0f) * 150);
                    blueLightFilter.setBackgroundColor(Color.argb(alpha, 240, 120, 0));
                } else {
                    blueLightFilter.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying internal filter values", e);
        }
    }

    // ------------------------------------------------------------------------------------------
    // D-Pad navigation
    // ------------------------------------------------------------------------------------------

    private final java.util.Set<Integer> consumedDownKeys = new java.util.HashSet<>();

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

    public boolean onKeyEvent(KeyEvent event) {
        if (!isShowing || overlayView == null) return false;
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                stopHoldRepeat();
                if (isShowingReplies) {
                    showMainItems();
                } else {
                    dismiss();
                }
            }
            return true;
        }

        View current = overlayView.findFocus();

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (action == KeyEvent.ACTION_DOWN) {
                stopHoldRepeat();
                if (event.getRepeatCount() == 0) {
                    isLongClickTriggered = false;
                    longClickHandler.removeCallbacks(longClickRunnable);
                    longClickHandler.postDelayed(longClickRunnable, 500);
                }
            } else if (action == KeyEvent.ACTION_UP) {
                longClickHandler.removeCallbacks(longClickRunnable);
                if (!isLongClickTriggered && current != null && current.isClickable()) {
                    current.performClick();
                }
                isLongClickTriggered = false;
            }
            return true;
        }

        boolean isNavKey = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;

        if (isNavKey) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    handleDpadNavigation(keyCode);
                    startHoldRepeat(keyCode);
                }
            } else if (action == KeyEvent.ACTION_UP) {
                if (keyCode == mHoldingKeyCode) {
                    stopHoldRepeat();
                }
            }
            return true;
        }

        boolean dispatched = overlayView.dispatchKeyEvent(event);
        if (dispatched) return true;

        return false;
    }

    private void handleDpadNavigation(int keyCode) {
        if (!isShowing || overlayView == null) return;
        View current = overlayView.findFocus();
        if (current == null) {
            if (srcButtons[currentSource] != null) {
                srcButtons[currentSource].requestFocus();
            }
            return;
        }

        // 1. Current is in list of items/comments
        boolean inList = isViewInside(current, containerItems);
        if (inList) {
            View card = findDirectCardChild(current);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: {
                    if (current != null && current != card) {
                        if (card != null) card.requestFocus();
                    }
                    return;
                }
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    if (card != null && card.getTag() instanceof View) {
                        ((View) card.getTag()).requestFocus();
                    }
                    return;
                }
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    int idx = card != null ? containerItems.indexOfChild(card) : -1;
                    int next = idx + 1;
                    while (next >= 0 && next < containerItems.getChildCount() && !containerItems.getChildAt(next).isFocusable()) {
                        next++;
                    }
                    if (next >= 0 && next < containerItems.getChildCount()) {
                        View nextView = containerItems.getChildAt(next);
                        nextView.requestFocus();
                        centerViewInScrollView(nextView);
                    }
                    return;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    int idx = card != null ? containerItems.indexOfChild(card) : -1;
                    int next = idx - 1;
                    while (next >= 0 && next < containerItems.getChildCount() && !containerItems.getChildAt(next).isFocusable()) {
                        next--;
                    }
                    if (next >= 0 && next < containerItems.getChildCount()) {
                        View nextView = containerItems.getChildAt(next);
                        nextView.requestFocus();
                        centerViewInScrollView(nextView);
                    } else {
                        // Reached top of list: focus top action pills
                        if (btnEngine != null) {
                            btnEngine.requestFocus();
                        } else if (srcButtons[currentSource] != null) {
                            srcButtons[currentSource].requestFocus();
                        }
                        if (scrollList != null) scrollList.smoothScrollTo(0, 0);
                    }
                    return;
                }
            }
        }

        // 2. Current is in Header Bar (Close button)
        if (current == btnClose) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (srcButtons[currentSource] != null) {
                    srcButtons[currentSource].requestFocus();
                } else if (srcButtons[0] != null) {
                    srcButtons[0].requestFocus();
                }
            }
            return;
        }

        // 3. Current is in Source Pills row: [UI, OCR, Comments, Lens]
        for (int i = 0; i < srcButtons.length; i++) {
            if (current == srcButtons[i]) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (i > 0 && srcButtons[i - 1] != null) {
                        srcButtons[i - 1].requestFocus();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (i < srcButtons.length - 1 && srcButtons[i + 1] != null) {
                        srcButtons[i + 1].requestFocus();
                    } else if (btnClose != null) {
                        btnClose.requestFocus();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (btnEngine != null && btnEngine.getVisibility() == View.VISIBLE) {
                        btnEngine.requestFocus();
                    } else if (btnLang != null && btnLang.getVisibility() == View.VISIBLE) {
                        btnLang.requestFocus();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (btnClose != null) {
                        btnClose.requestFocus();
                    }
                }
                return;
            }
        }

        // 4. Current is in Action Pills row: [btnEngine, btnLang, btnTranslateAll]
        java.util.List<View> actionPills = new java.util.ArrayList<>();
        if (btnEngine != null && btnEngine.getVisibility() == View.VISIBLE) actionPills.add(btnEngine);
        if (btnLang != null && btnLang.getVisibility() == View.VISIBLE) actionPills.add(btnLang);
        if (btnTranslateAll != null && btnTranslateAll.getVisibility() == View.VISIBLE) actionPills.add(btnTranslateAll);

        int actionIdx = actionPills.indexOf(current);
        if (actionIdx >= 0) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (actionIdx > 0) {
                    actionPills.get(actionIdx - 1).requestFocus();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (actionIdx < actionPills.size() - 1) {
                    actionPills.get(actionIdx + 1).requestFocus();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (srcButtons[currentSource] != null) {
                    srcButtons[currentSource].requestFocus();
                } else if (srcButtons[0] != null) {
                    srcButtons[0].requestFocus();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                View target = findFirstFocusable(containerItems);
                if (target != null) {
                    target.requestFocus();
                    centerViewInScrollView(target);
                }
            }
            return;
        }

        // Fallback standard focusSearch
        int direction = View.FOCUS_DOWN;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) direction = View.FOCUS_UP;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) direction = View.FOCUS_LEFT;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) direction = View.FOCUS_RIGHT;

        View next = current.focusSearch(direction);
        if (next != null && next != current && isViewInside(next, overlayView)) {
            next.requestFocus();
            if (isViewInside(next, containerItems)) {
                centerViewInScrollView(next);
            }
        }
    }

    private View findDirectCardChild(View v) {
        if (v == null || containerItems == null) return null;
        View p = v;
        while (p != null && p.getParent() != containerItems) {
            if (p.getParent() instanceof View) {
                p = (View) p.getParent();
            } else {
                return null;
            }
        }
        return p;
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

    private View findFirstFocusable(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        if (view.isFocusable()) return view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View found = findFirstFocusable(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
