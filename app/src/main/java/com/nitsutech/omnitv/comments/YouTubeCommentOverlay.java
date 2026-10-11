package com.nitsutech.omnitv.comments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nitsutech.omnitv.ButtonMappingService;
import com.nitsutech.omnitv.MediaNotificationListener;
import com.nitsutech.omnitv.R;
import com.nitsutech.omnitv.I18n;

import java.util.ArrayList;
import java.util.List;

public class YouTubeCommentOverlay {
    private static final String TAG = "YouTubeCommentOverlay";
    private static YouTubeCommentOverlay instance;

    private Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;

    // UI elements
    private TextView btnSource;
    private TextView btnLang;
    private TextView btnTranslateAll;
    private TextView btnBackThreads;
    private TextView textStatus;
    private ScrollView scrollList;
    private LinearLayout containerItems;
    private View dimmerFilter;
    private View blueLightFilter;

    // State
    private int currentSourceMode = 0; // 0: Screen, 1: All Video
    private String currentTargetLang = "es"; // "es", "en", "orig"
    private final List<YouTubeCommentItem> mainComments = new ArrayList<>();
    private final List<YouTubeCommentItem> currentReplies = new ArrayList<>();
    private boolean isShowingReplies = false;
    private String activeVideoId = null;

    // Pagination / Infinite Scroll
    private String nextContinuationToken = null;
    private boolean isLoadingMore = false;
    private TextView btnLoadMore = null;

    public YouTubeCommentOverlay(Context context) {
        this.context = context;
    }

    public static synchronized YouTubeCommentOverlay getInstance(Context context) {
        if (instance == null) {
            instance = new YouTubeCommentOverlay(context);
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

        LayoutInflater inflater = LayoutInflater.from(com.nitsutech.omnitv.I18n.getLocalizedContext(context));
        overlayView = inflater.inflate(R.layout.view_comments_translator_overlay, null);

        bindViews();
        setupListeners();
        updateMenuInternalFilters();

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
            Log.d(TAG, "YouTubeCommentOverlay added to WindowManager");

            // Dismiss when clicking outside (on the scrim)
            View rootScrim = overlayView.findViewById(R.id.comments_overlay_root);
            if (rootScrim != null) {
                rootScrim.setOnClickListener(v -> dismiss());
            }
            View drawerPanel = overlayView.findViewById(R.id.comments_panel_drawer);
            if (drawerPanel != null) {
                drawerPanel.setOnClickListener(v -> {}); // prevent clicks inside drawer from dismissing
            }

            // Resolve current video ID
            MediaNotificationListener.LiveVideoInfo info = MediaNotificationListener.getLiveVideoInfo(context);
            if (info != null && info.mediaId != null && !info.mediaId.isEmpty()) {
                activeVideoId = MediaNotificationListener.extractCleanVideoId(info.mediaId);
            }
            if (activeVideoId == null || activeVideoId.isEmpty()) {
                activeVideoId = com.nitsutech.omnitv.vot.YouTubeCaptionFetcher.getCachedVideoId(info != null ? info.title : "");
            }

            // Start by loading screen comments by default
            loadCommentsForCurrentMode();

            // Initial focus
            overlayView.postDelayed(() -> {
                if (btnSource != null) btnSource.requestFocus();
            }, 100);

        } catch (Exception e) {
            Log.e(TAG, "Error displaying YouTubeCommentOverlay", e);
            dismiss();
        }
    }

    public void dismiss() {
        if (!isShowing || overlayView == null) return;
        try {
            if (windowManager != null) {
                windowManager.removeView(overlayView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing YouTubeCommentOverlay", e);
        } finally {
            overlayView = null;
            isShowing = false;
            isShowingReplies = false;
            nextContinuationToken = null;
            isLoadingMore = false;
        }
    }

    private void bindViews() {
        btnSource = overlayView.findViewById(R.id.comments_btn_source);
        btnLang = overlayView.findViewById(R.id.comments_btn_lang);
        btnTranslateAll = overlayView.findViewById(R.id.comments_btn_translate_all);
        btnBackThreads = overlayView.findViewById(R.id.comments_btn_back_threads);
        textStatus = overlayView.findViewById(R.id.comments_text_status);
        scrollList = overlayView.findViewById(R.id.comments_scroll_list);
        containerItems = overlayView.findViewById(R.id.comments_container_items);
        dimmerFilter = overlayView.findViewById(R.id.comments_menu_dimmer_filter);
        blueLightFilter = overlayView.findViewById(R.id.comments_menu_blue_light_filter);

        View btnClose = overlayView.findViewById(R.id.comments_btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }

    private void setupListeners() {
        btnSource.setOnClickListener(v -> {
            currentSourceMode = (currentSourceMode == 0) ? 1 : 0;
            updateSourceButtonLabel();
            loadCommentsForCurrentMode();
        });

        btnLang.setOnClickListener(v -> {
            if ("es".equals(currentTargetLang)) {
                currentTargetLang = "en";
            } else if ("en".equals(currentTargetLang)) {
                currentTargetLang = "orig";
            } else {
                currentTargetLang = "es";
            }
            updateLangButtonLabel();
            refreshDisplayedComments();
        });

        btnTranslateAll.setOnClickListener(v -> translateAllCurrentComments());

        btnBackThreads.setOnClickListener(v -> {
            isShowingReplies = false;
            btnBackThreads.setVisibility(View.GONE);
            renderCommentsList(mainComments);
        });
    }

    private void updateSourceButtonLabel() {
        if (currentSourceMode == 0) {
            btnSource.setText(I18n.get(context, R.string.comments_source_screen));
        } else {
            btnSource.setText(I18n.get(context, R.string.comments_source_all));
        }
    }

    private void updateLangButtonLabel() {
        if ("es".equals(currentTargetLang)) {
            btnLang.setText(I18n.get(context, R.string.comments_lang_es));
        } else if ("en".equals(currentTargetLang)) {
            btnLang.setText(I18n.get(context, R.string.comments_lang_en));
        } else {
            btnLang.setText(I18n.get(context, R.string.comments_lang_orig));
        }
    }

    private void loadCommentsForCurrentMode() {
        isShowingReplies = false;
        nextContinuationToken = null;
        isLoadingMore = false;
        btnBackThreads.setVisibility(View.GONE);
        textStatus.setText(I18n.get(context, R.string.comments_loading));
        containerItems.removeAllViews();

        if (currentSourceMode == 0) {
            loadCommentsFromScreen();
        } else {
            loadCommentsFromAllVideo();
        }
    }

    private void loadCommentsFromScreen() {
        ButtonMappingService svc = ButtonMappingService.instance;
        if (svc == null) {
            textStatus.setText(I18n.get(context, R.string.comments_empty));
            return;
        }

        List<YouTubeCommentItem> extracted = new ArrayList<>();
        String extractedTitle = null;

        try {
            List<AccessibilityWindowInfo> windows = svc.getWindows();
            if (windows != null && !windows.isEmpty()) {
                for (AccessibilityWindowInfo win : windows) {
                    if (win == null) continue;
                    // Skip our own accessibility overlay windows
                    if (win.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
                    AccessibilityNodeInfo root = win.getRoot();
                    if (root != null) {
                        CharSequence pkg = root.getPackageName();
                        if (pkg != null && !pkg.toString().equals("com.nitsutech.omnitv")) {
                            scrapeCommentsFromNode(root, extracted);
                            if (extractedTitle == null) {
                                extractedTitle = findVideoTitleInNode(root);
                            }
                        }
                        root.recycle();
                    }
                }
            } else {
                AccessibilityNodeInfo root = svc.getRootInActiveWindow();
                if (root != null) {
                    CharSequence pkg = root.getPackageName();
                    if (pkg != null && !pkg.toString().equals("com.nitsutech.omnitv")) {
                        scrapeCommentsFromNode(root, extracted);
                        extractedTitle = findVideoTitleInNode(root);
                    }
                    root.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scraping screen comments", e);
        }

        // Cache title if found on screen
        if (extractedTitle != null && (activeVideoId == null || activeVideoId.isEmpty())) {
            activeVideoId = com.nitsutech.omnitv.vot.YouTubeCaptionFetcher.resolveVideoId(extractedTitle);
        }

        mainComments.clear();
        mainComments.addAll(extracted);

        if (mainComments.isEmpty()) {
            Log.d(TAG, "No comments found on screen, automatically switching to All Video mode");
            currentSourceMode = 1;
            updateSourceButtonLabel();
            loadCommentsFromAllVideo();
        } else {
            textStatus.setText(mainComments.size() + " " + I18n.get(context, R.string.comments_source_screen));
            renderCommentsList(mainComments);
        }
    }

    private String findVideoTitleInNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence pkg = node.getPackageName();
        if (pkg != null && pkg.toString().equals("com.nitsutech.omnitv")) return null;

        CharSequence text = node.getText();
        if (text != null) {
            String str = text.toString().trim();
            if (str.length() > 15 && !str.contains("@") && !str.startsWith("http")
                    && !str.contains("OmniTV") && !str.contains("SmartTube")
                    && !str.contains("Comment") && !str.contains("Comentario")) {
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

    private void scrapeCommentsFromNode(AccessibilityNodeInfo node, List<YouTubeCommentItem> outList) {
        if (node == null) return;
        CharSequence pkg = node.getPackageName();
        if (pkg != null && pkg.toString().equals("com.nitsutech.omnitv")) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        // Detect if text looks like a YouTube comment (paragraph with reasonable length, not button labels)
        if (text != null && text.length() > 8) {
            String str = text.toString().trim();
            if (!str.contains("OmniTV") && !str.startsWith("http") && !str.equals("SmartTube") && !str.equals("YouTube")
                    && !str.equals("Translate All") && !str.equals("Traducir Todos")
                    && !str.equals("Comment Translator") && !str.equals("Traductor de Comentarios")) {
                boolean duplicate = false;
                for (YouTubeCommentItem c : outList) {
                    if (str.equals(c.originalText)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate && outList.size() < 40) {
                    YouTubeCommentItem item = new YouTubeCommentItem();
                    item.commentId = "screen_" + outList.size();
                    item.originalText = str;
                    item.authorName = (desc != null && !desc.toString().equals(str)) ? desc.toString() : "Viewer";
                    outList.add(item);
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                scrapeCommentsFromNode(child, outList);
                child.recycle();
            }
        }
    }

    private void loadCommentsFromAllVideo() {
        textStatus.setText(I18n.get(context, R.string.comments_loading));
        nextContinuationToken = null;
        isLoadingMore = false;

        executor.execute(() -> {
            String vId = activeVideoId;
            if (vId == null || vId.isEmpty()) {
                MediaNotificationListener.LiveVideoInfo info = MediaNotificationListener.getLiveVideoInfo(context);
                if (info != null && info.mediaId != null && !info.mediaId.isEmpty()) {
                    vId = MediaNotificationListener.extractCleanVideoId(info.mediaId);
                }
                if (vId == null || vId.isEmpty()) {
                    if (info != null && info.title != null && !info.title.isEmpty()) {
                        vId = com.nitsutech.omnitv.vot.YouTubeCaptionFetcher.resolveVideoId(info.title);
                    }
                }
                activeVideoId = vId;
            }

            final String resolvedId = vId;
            if (resolvedId == null || resolvedId.isEmpty()) {
                handler.post(() -> textStatus.setText(I18n.get(context, R.string.comments_empty)));
                return;
            }

            YouTubeCommentFetcher.fetchVideoCommentsAsync(resolvedId, null, new YouTubeCommentFetcher.CommentsCallback() {
                @Override
                public void onSuccess(List<YouTubeCommentItem> comments, String nextToken) {
                    handler.post(() -> {
                        mainComments.clear();
                        mainComments.addAll(comments);
                        nextContinuationToken = nextToken;
                        if (mainComments.isEmpty()) {
                            textStatus.setText(I18n.get(context, R.string.comments_empty));
                        } else {
                            textStatus.setText(mainComments.size() + " " + I18n.get(context, R.string.comments_source_all));
                            renderCommentsList(mainComments);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    handler.post(() -> textStatus.setText(I18n.get(context, R.string.comments_empty)));
                }
            });
        });
    }

    private void loadMoreComments() {
        if (isLoadingMore || nextContinuationToken == null || nextContinuationToken.isEmpty()) return;
        isLoadingMore = true;
        if (btnLoadMore != null) {
            btnLoadMore.setText(I18n.get(context, R.string.comments_loading));
        }

        YouTubeCommentFetcher.fetchVideoCommentsAsync(null, nextContinuationToken, new YouTubeCommentFetcher.CommentsCallback() {
            @Override
            public void onSuccess(List<YouTubeCommentItem> newComments, String nextToken) {
                handler.post(() -> {
                    isLoadingMore = false;
                    nextContinuationToken = nextToken;
                    if (newComments != null && !newComments.isEmpty()) {
                        mainComments.addAll(newComments);
                        textStatus.setText(mainComments.size() + " " + I18n.get(context, R.string.comments_source_all));
                        renderCommentsList(mainComments);
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

    private void renderCommentsList(List<YouTubeCommentItem> list) {
        containerItems.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;

        for (int i = 0; i < list.size(); i++) {
            final YouTubeCommentItem item = list.get(i);
            final int index = i;

            final LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(Math.round(14 * density), Math.round(12 * density), Math.round(14 * density), Math.round(12 * density));

            // Default state background
            final GradientDrawable defaultBg = new GradientDrawable();
            defaultBg.setColor(0xFF1B1D24);
            defaultBg.setCornerRadius(10 * density);
            defaultBg.setStroke(Math.round(1 * density), 0x33FFFFFF);

            final GradientDrawable focusedBg = new GradientDrawable();
            focusedBg.setColor(0xFF2C3246);
            focusedBg.setCornerRadius(10 * density);
            focusedBg.setStroke(Math.round(2 * density), 0xFF8AB4F8);

            card.setBackground(defaultBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = Math.round(10 * density);
            card.setLayoutParams(lp);

            // Author Header
            TextView tvAuthor = new TextView(context);
            String authorStr = (item.authorName != null && !item.authorName.isEmpty()) ? item.authorName : "Viewer";
            if (item.publishedTimeText != null && !item.publishedTimeText.isEmpty()) {
                authorStr += " • " + item.publishedTimeText;
            }
            tvAuthor.setText(authorStr);
            tvAuthor.setTextColor(0xFF8AB4F8);
            tvAuthor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvAuthor.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(tvAuthor);

            // Comment Text
            final TextView tvBody = new TextView(context);
            tvBody.setText(getCommentTextForLanguage(item));
            tvBody.setTextColor(0xFFEEEEEE);
            tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvBody.setPadding(0, Math.round(4 * density), 0, Math.round(8 * density));
            card.addView(tvBody);

            // Action row inside card: [ Translate / Original ] and if replies > 0 [ 💬 Replies ]
            LinearLayout actionsRow = new LinearLayout(context);
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setGravity(Gravity.CENTER_VERTICAL);

            final TextView btnItemTranslate = new TextView(context);
            btnItemTranslate.setBackgroundResource(R.drawable.pill_youtube_tv);
            btnItemTranslate.setText(item.isTranslated ? I18n.get(context, R.string.comments_btn_orig) : I18n.get(context, R.string.comments_btn_translate));
            btnItemTranslate.setTextColor(0xFF81D4FA);
            btnItemTranslate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnItemTranslate.setPadding(Math.round(10 * density), Math.round(4 * density), Math.round(10 * density), Math.round(4 * density));
            btnItemTranslate.setFocusable(false); // Let card handle click & OK navigation cleanly
            btnItemTranslate.setClickable(false);

            actionsRow.addView(btnItemTranslate);

            // Nested replies button (can be clicked via D-Pad or separate indicator)
            if (item.replyCount > 0 && item.replyContinuationToken != null) {
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

                btnReplies.setOnClickListener(v -> openReplyThread(item));
                btnReplies.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) centerViewInScrollView(v);
                });
                actionsRow.addView(btnReplies);
            }

            card.addView(actionsRow);

            // Card click toggles translation directly!
            card.setOnClickListener(v -> toggleCommentTranslation(item, tvBody, btnItemTranslate));

            card.setOnFocusChangeListener((v, hasFocus) -> {
                card.setBackground(hasFocus ? focusedBg : defaultBg);
                if (hasFocus) {
                    centerViewInScrollView(card);
                }
            });

            containerItems.addView(card);
        }

        // Add [ ⬇️ Load More Comments ] button at the bottom if more comments exist
        if (!isShowingReplies && nextContinuationToken != null && !nextContinuationToken.isEmpty()) {
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

            LinearLayout.LayoutParams lpMore = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lpMore.topMargin = Math.round(8 * density);
            lpMore.bottomMargin = Math.round(20 * density);
            btnLoadMore.setLayoutParams(lpMore);

            btnLoadMore.setOnClickListener(v -> loadMoreComments());
            btnLoadMore.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    centerViewInScrollView(btnLoadMore);
                    // Automatic load-on-scroll when reaching the bottom!
                    loadMoreComments();
                }
            });

            containerItems.addView(btnLoadMore);
        }
    }

    private void toggleCommentTranslation(final YouTubeCommentItem item, final TextView tvBody, final TextView btnItemTranslate) {
        if (item.isTranslated) {
            item.isTranslated = false;
            tvBody.setText(item.originalText);
            btnItemTranslate.setText(I18n.get(context, R.string.comments_btn_translate));
        } else {
            String target = "orig".equals(currentTargetLang) ? "es" : currentTargetLang;
            btnItemTranslate.setText(I18n.get(context, R.string.comments_translating));
            YouTubeCommentTranslator.translateAsync(item.originalText, target, new YouTubeCommentTranslator.TranslationCallback() {
                @Override
                public void onSuccess(String translatedText) {
                    handler.post(() -> {
                        item.translatedText = translatedText;
                        item.isTranslated = true;
                        item.targetLanguage = target;
                        tvBody.setText(translatedText);
                        btnItemTranslate.setText(I18n.get(context, R.string.comments_btn_orig));
                    });
                }

                @Override
                public void onError(String error) {
                    handler.post(() -> btnItemTranslate.setText(I18n.get(context, R.string.comments_btn_translate)));
                }
            });
        }
    }

    private void centerViewInScrollView(View v) {
        if (scrollList == null || v == null) return;
        int[] vLoc = new int[2];
        int[] svLoc = new int[2];
        v.getLocationOnScreen(vLoc);
        scrollList.getLocationOnScreen(svLoc);
        int targetY = vLoc[1] - svLoc[1] + scrollList.getScrollY() - (scrollList.getHeight() - v.getHeight()) / 2;
        scrollList.smoothScrollTo(0, Math.max(0, targetY));
    }

    private void openReplyThread(final YouTubeCommentItem parentItem) {
        if (parentItem.replyContinuationToken == null) return;
        textStatus.setText(I18n.get(context, R.string.comments_loading));
        containerItems.removeAllViews();
        isShowingReplies = true;
        btnBackThreads.setVisibility(View.VISIBLE);

        YouTubeCommentFetcher.fetchRepliesAsync(parentItem.replyContinuationToken, new YouTubeCommentFetcher.CommentsCallback() {
            @Override
            public void onSuccess(List<YouTubeCommentItem> replies, String nextContinuationToken) {
                handler.post(() -> {
                    currentReplies.clear();
                    // Add parent comment as first highlighted item
                    currentReplies.add(parentItem);
                    currentReplies.addAll(replies);
                    textStatus.setText(replies.size() + " " + I18n.get(context, R.string.comments_show_replies));
                    renderCommentsList(currentReplies);
                    if (btnBackThreads != null) btnBackThreads.requestFocus();
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> textStatus.setText(I18n.get(context, R.string.comments_empty)));
            }
        });
    }

    private void translateAllCurrentComments() {
        final List<YouTubeCommentItem> targetList = isShowingReplies ? currentReplies : mainComments;
        if (targetList.isEmpty()) return;

        final String target = "orig".equals(currentTargetLang) ? "es" : currentTargetLang;
        textStatus.setText(I18n.get(context, R.string.comments_translating));

        YouTubeCommentTranslator.translateBatchAsync(targetList, target, () -> {
            handler.post(() -> {
                textStatus.setText(targetList.size() + " " + (isShowingReplies ? "Replies" : "Comments"));
                renderCommentsList(targetList);
            });
        });
    }

    private void refreshDisplayedComments() {
        final List<YouTubeCommentItem> targetList = isShowingReplies ? currentReplies : mainComments;
        if ("orig".equals(currentTargetLang)) {
            for (YouTubeCommentItem item : targetList) {
                item.isTranslated = false;
            }
            renderCommentsList(targetList);
        } else {
            translateAllCurrentComments();
        }
    }

    private String getCommentTextForLanguage(YouTubeCommentItem item) {
        if ("orig".equals(currentTargetLang) || !item.isTranslated) {
            return item.originalText;
        }
        return item.getDisplayText();
    }

    private void updateMenuInternalFilters() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE);
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

    private void showMainComments() {
        isShowingReplies = false;
        if (btnBackThreads != null) {
            btnBackThreads.setVisibility(View.GONE);
        }
        renderCommentsList(mainComments);
    }

    public boolean onKeyEvent(KeyEvent event) {
        if (!isShowing || overlayView == null) return false;
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (isShowingReplies) {
                    showMainComments();
                } else {
                    dismiss();
                }
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

        // Section jumping between Controls Row and Comments List
        if (action == KeyEvent.ACTION_DOWN) {
            View current = overlayView.findFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (current != null && (current == btnSource || current == btnLang || current == btnTranslateAll || current == btnBackThreads)) {
                    View target = findFirstFocusable(containerItems);
                    if (target != null) {
                        target.requestFocus();
                        centerViewInScrollView(target);
                        return true;
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (current != null && isViewInside(current, scrollList)) {
                    View upNeighbor = current.focusSearch(View.FOCUS_UP);
                    if (upNeighbor == null || !isViewInside(upNeighbor, scrollList)) {
                        if (btnSource != null) {
                            btnSource.requestFocus();
                            return true;
                        }
                    }
                }
            }
        }

        return overlayView.dispatchKeyEvent(event);
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
