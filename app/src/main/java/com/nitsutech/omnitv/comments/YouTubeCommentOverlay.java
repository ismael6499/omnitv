package com.nitsutech.omnitv.comments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nitsutech.omnitv.ButtonMappingService;
import com.nitsutech.omnitv.MediaNotificationListener;
import com.nitsutech.omnitv.R;
import com.nitsutech.omnitv.I18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YouTubeCommentOverlay {
    private static final String TAG = "YouTubeCommentOverlay";
    private static YouTubeCommentOverlay instance;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
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

    public YouTubeCommentOverlay(Context context) {
        this.context = context;
    }

    public static synchronized YouTubeCommentOverlay getInstance(Context context) {
        if (instance == null) {
            instance = new YouTubeCommentOverlay(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void show() {
        if (isShowing) return;

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        overlayView = inflater.inflate(R.layout.view_comments_translator_overlay, null);

        bindViews();
        setupListeners();
        updateMenuInternalFilters();

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int widthPx = Math.round(560 * dm.density);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                widthPx,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.END | Gravity.TOP;

        try {
            windowManager.addView(overlayView, params);
            isShowing = true;
            Log.d(TAG, "YouTubeCommentOverlay added to WindowManager");

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

        overlayView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (isShowingReplies) {
                        isShowingReplies = false;
                        btnBackThreads.setVisibility(View.GONE);
                        renderCommentsList(mainComments);
                        return true;
                    }
                    dismiss();
                    return true;
                }
            }
            return false;
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
        try {
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root != null) {
                scrapeCommentsFromNode(root, extracted);
                root.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scraping screen comments", e);
        }

        mainComments.clear();
        mainComments.addAll(extracted);

        if (mainComments.isEmpty()) {
            textStatus.setText(I18n.get(context, R.string.comments_empty));
        } else {
            textStatus.setText(mainComments.size() + " " + I18n.get(context, R.string.comments_source_screen));
            renderCommentsList(mainComments);
        }
    }

    private void scrapeCommentsFromNode(AccessibilityNodeInfo node, List<YouTubeCommentItem> outList) {
        if (node == null) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String viewId = node.getViewIdResourceName();

        // Detect if text looks like a YouTube comment (paragraph with reasonable length, not button labels)
        if (text != null && text.length() > 10) {
            String str = text.toString().trim();
            if (!str.contains("OmniTV") && !str.startsWith("http") && !str.equals("SmartTube") && !str.equals("YouTube")) {
                boolean duplicate = false;
                for (YouTubeCommentItem c : outList) {
                    if (str.equals(c.originalText)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate && outList.size() < 30) {
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
        if (activeVideoId == null || activeVideoId.isEmpty()) {
            MediaNotificationListener.LiveVideoInfo info = MediaNotificationListener.getLiveVideoInfo(context);
            if (info != null && info.title != null && !info.title.isEmpty()) {
                activeVideoId = com.nitsutech.omnitv.vot.YouTubeCaptionFetcher.resolveVideoId(info.title);
            }
        }

        if (activeVideoId == null || activeVideoId.isEmpty()) {
            textStatus.setText(I18n.get(context, R.string.comments_empty));
            return;
        }

        YouTubeCommentFetcher.fetchVideoCommentsAsync(activeVideoId, null, new YouTubeCommentFetcher.CommentsCallback() {
            @Override
            public void onSuccess(List<YouTubeCommentItem> comments, String nextContinuationToken) {
                handler.post(() -> {
                    mainComments.clear();
                    mainComments.addAll(comments);
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
    }

    private void renderCommentsList(List<YouTubeCommentItem> list) {
        containerItems.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;

        for (int i = 0; i < list.size(); i++) {
            final YouTubeCommentItem item = list.get(i);
            final int index = i;

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackgroundResource(R.drawable.card_chip_content);
            card.setPadding(Math.round(14 * density), Math.round(12 * density), Math.round(14 * density), Math.round(12 * density));

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
            btnItemTranslate.setText((CharSequence) (item.isTranslated ? I18n.get(context, R.string.comments_btn_orig) : I18n.get(context, R.string.comments_btn_translate)));
            btnItemTranslate.setTextColor(0xFF81D4FA);
            btnItemTranslate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnItemTranslate.setPadding(Math.round(10 * density), Math.round(4 * density), Math.round(10 * density), Math.round(4 * density));
            btnItemTranslate.setFocusable(true);
            btnItemTranslate.setClickable(true);

            btnItemTranslate.setOnClickListener(v -> {
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
            });
            actionsRow.addView(btnItemTranslate);

            // Nested replies button
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
                actionsRow.addView(btnReplies);
            }

            card.addView(actionsRow);
            containerItems.addView(card);
        }
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
            boolean dimmerEnabled = prefs.getBoolean("dimmer_enabled", false);
            int dimmerBrightnessPct = prefs.getInt("dimmer_brightness_pct", 50);
            boolean blueLightEnabled = prefs.getBoolean("blue_light_enabled", false);
            int blueLightPct = prefs.getInt("blue_light_pct", 30);

            if (dimmerFilter != null) {
                if (dimmerEnabled) {
                    dimmerFilter.setVisibility(View.VISIBLE);
                    int alpha = Math.round((100 - dimmerBrightnessPct) * 2.55f);
                    dimmerFilter.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
                } else {
                    dimmerFilter.setVisibility(View.GONE);
                }
            }

            if (blueLightFilter != null) {
                if (blueLightEnabled) {
                    blueLightFilter.setVisibility(View.VISIBLE);
                    int alpha = Math.round(blueLightPct * 1.8f);
                    blueLightFilter.setBackgroundColor(Color.argb(alpha, 255, 140, 0));
                } else {
                    blueLightFilter.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying internal filter values", e);
        }
    }
}
