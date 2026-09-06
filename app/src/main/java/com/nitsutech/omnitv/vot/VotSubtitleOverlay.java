package com.nitsutech.omnitv.vot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class VotSubtitleOverlay {
    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler;
    private View overlayView;
    private TextView tvTranslated;
    private TextView tvOriginal;
    private boolean isShowing = false;
    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    public VotSubtitleOverlay(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void showCue(final VotCue cue, final boolean showBilingual, final long durationMs) {
        if (cue == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                ensureViewCreated();
                if (overlayView == null) return;

                tvTranslated.setText(cue.translatedText != null && !cue.translatedText.isEmpty() ? cue.translatedText : cue.originalText);

                if (showBilingual && cue.originalText != null && !cue.originalText.isEmpty() && !cue.originalText.equals(cue.translatedText)) {
                    tvOriginal.setText(cue.originalText);
                    tvOriginal.setVisibility(View.VISIBLE);
                } else {
                    tvOriginal.setVisibility(View.GONE);
                }

                if (!isShowing) {
                    try {
                        WindowManager.LayoutParams params = createLayoutParams();
                        windowManager.addView(overlayView, params);
                        isShowing = true;
                    } catch (Exception ignored) {}
                }

                mainHandler.removeCallbacks(hideRunnable);
                long hideAfter = Math.max(2000, durationMs > 0 ? durationMs : cue.durationMs);
                mainHandler.postDelayed(hideRunnable, hideAfter);
            }
        });
    }

    public void hide() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                mainHandler.removeCallbacks(hideRunnable);
                if (isShowing && overlayView != null) {
                    try {
                        windowManager.removeView(overlayView);
                    } catch (Exception ignored) {}
                    isShowing = false;
                }
            }
        });
    }

    private void ensureViewCreated() {
        if (overlayView != null) return;

        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);

        // Container styling: rounded dark background
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * density);
        bg.setColor(Color.parseColor("#E60A0A0E")); // 90% dark slate
        bg.setStroke((int) (1.5f * density), Color.parseColor("#44FFFFFF")); // subtle white border
        root.setBackground(bg);

        int padH = (int) (18 * density);
        int padV = (int) (10 * density);
        root.setPadding(padH, padV, padH, padV);

        // Original text (bilingual mode)
        tvOriginal = new TextView(context);
        tvOriginal.setTextColor(Color.parseColor("#B0BEC5")); // silver / muted
        tvOriginal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvOriginal.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        tvOriginal.setGravity(Gravity.CENTER);
        tvOriginal.setShadowLayer(3f, 1f, 1f, Color.BLACK);
        root.addView(tvOriginal);

        // Translated text
        tvTranslated = new TextView(context);
        tvTranslated.setTextColor(Color.parseColor("#FFFDD0")); // soft cream white
        tvTranslated.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        tvTranslated.setTypeface(Typeface.DEFAULT_BOLD);
        tvTranslated.setGravity(Gravity.CENTER);
        tvTranslated.setShadowLayer(4f, 1.5f, 1.5f, Color.BLACK);
        root.addView(tvTranslated);

        overlayView = root;
    }

    private WindowManager.LayoutParams createLayoutParams() {
        float density = context.getResources().getDisplayMetrics().density;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = (int) (42 * density); // 42dp from bottom edge
        return params;
    }
}
