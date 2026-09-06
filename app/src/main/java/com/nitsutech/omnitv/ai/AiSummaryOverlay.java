package com.nitsutech.omnitv.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nitsutech.omnitv.MediaNotificationListener;
import com.nitsutech.omnitv.R;
import com.nitsutech.omnitv.vot.VotCue;
import com.nitsutech.omnitv.vot.VotTrack;
import com.nitsutech.omnitv.vot.YouTubeCaptionFetcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    // Persistent conversation state per video
    private static class ChatTurn {
        final boolean isUser;
        final String text;
        final List<AiSummaryEngine.AiPointItem> items;
        final List<String> suggestedQuestions;

        ChatTurn(boolean isUser, String text, List<AiSummaryEngine.AiPointItem> items, List<String> suggestedQuestions) {
            this.isUser = isUser;
            this.text = text;
            this.items = items;
            this.suggestedQuestions = suggestedQuestions;
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
    private final ExecutorService fetcherExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        setupKeyListeners();
        detectVideoAndLoadTranscript(context);

        try {
            windowManager.addView(overlayView, params);
            isShowing = true;
            Log.d(TAG, "AiSummaryOverlay displayed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error adding AiSummaryOverlay to WindowManager", e);
        }
    }

    public synchronized void hide() {
        if (!isShowing || overlayView == null || windowManager == null) return;
        try {
            windowManager.removeView(overlayView);
        } catch (Exception e) {
            Log.e(TAG, "Error removing AiSummaryOverlay", e);
        }
        overlayView = null;
        isShowing = false;
        currentThinkingView = null;
        // Keep conversationHistory, recordedTurns, lastVideoTitle, and lastTranscript
        // so if the user re-opens the assistant on the same video, the conversation continues!
        Log.d(TAG, "AiSummaryOverlay hidden (conversation state retained for: " + lastVideoTitle + ")");
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
                    hide();
                    return true;
                }
            }
            return false;
        });
    }

    public boolean onKeyEvent(KeyEvent event) {
        if (!isShowing || overlayView == null) return false;

        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                hide();
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

        boolean handled = overlayView.dispatchKeyEvent(event);
        if (handled) return true;

        if (action == KeyEvent.ACTION_DOWN) {
            int direction = -1;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) direction = View.FOCUS_DOWN;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) direction = View.FOCUS_UP;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) direction = View.FOCUS_LEFT;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) direction = View.FOCUS_RIGHT;

            if (direction != -1) {
                View current = overlayView.findFocus();
                if (current != null) {
                    View next = current.focusSearch(direction);
                    if (next != null && next != current) {
                        next.requestFocus();
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

    private String resolveCurrentTitle(Context context) {
        String title = MediaNotificationListener.activeTitle;
        if (title == null || title.trim().isEmpty()) {
            title = com.nitsutech.omnitv.vot.VotManager.getInstance(context).getCurrentVideoTitle();
        }
        if (title == null || title.trim().isEmpty()) {
            title = "Video actual en pantalla";
        }
        return title.trim();
    }

    private String resolveCurrentVideoId(Context context, String title) {
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
        conversationHistory.clear();
        recordedTurns.clear();
        lastSuggestedQuestions.clear();
        if (containerAiChips != null) containerAiChips.removeAllViews();
        if (textAiWelcome != null) textAiWelcome.setVisibility(View.VISIBLE);
        if (layoutSuggestedSection != null) layoutSuggestedSection.setVisibility(View.GONE);
        if (btnAiReset != null) btnAiReset.setVisibility(View.GONE);
        saveConversationToPrefs(context);
        Toast.makeText(context, "🔄 Conversación reiniciada", Toast.LENGTH_SHORT).show();
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

                List<String> suggested = new ArrayList<>();
                if (obj.has("suggestedQuestions")) {
                    JSONArray qArr = obj.getJSONArray("suggestedQuestions");
                    for (int j = 0; j < qArr.length(); j++) {
                        suggested.add(qArr.getString(j));
                    }
                }

                recordedTurns.add(new ChatTurn(isUser, text, items, suggested));
                conversationHistory.add(new AiSummaryEngine.ChatMessage(isUser ? "user" : "assistant", text));
            }

            if (savedQJson != null && !savedQJson.isEmpty()) {
                JSONArray qArr = new JSONArray(savedQJson);
                for (int j = 0; j < qArr.length(); j++) {
                    lastSuggestedQuestions.add(qArr.getString(j));
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
        lastVideoTitle = detectedTitle;
        lastVideoId = detectedId;
        lastTranscript = "";
        currentTranscript = "";
        conversationHistory.clear();
        recordedTurns.clear();
        lastSuggestedQuestions.clear();
        if (btnAiReset != null) btnAiReset.setVisibility(View.GONE);
        saveConversationToPrefs(context);

        textStatus.setText("⏳ Buscando subtítulos con " + providerName + "...");

        fetcherExecutor.execute(() -> {
            String transcript = "";
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
                        if (track == null) {
                            track = YouTubeCaptionFetcher.fetchTrack(videoId, "en");
                        }
                        if (track == null) {
                            track = YouTubeCaptionFetcher.fetchTrack(videoId, "auto");
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
            mainHandler.post(() -> {
                if (!isShowing) return;
                currentTranscript = finalTranscript;
                lastTranscript = finalTranscript;
                saveConversationToPrefs(context);
                if (!finalTranscript.isEmpty()) {
                    int wordCount = finalTranscript.split("\\s+").length;
                    textStatus.setText("✨ " + providerName + " • Transcripción cargada (" + wordCount + " palabras)");
                } else {
                    textStatus.setText("✨ " + providerName + " • Listo (analizando por título)");
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
                appendChips(context, turn.items, turn.text, false);
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
            tvThinking.setText("✨ Analizando video...");
            tvThinking.setTextColor(0xFFCCCCCC);
            tvThinking.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);

            thinkingCard.addView(miniProgress);
            thinkingCard.addView(tvThinking);
            containerAiChips.addView(thinkingCard);
            currentThinkingView = thinkingCard;

            scrollContent.post(() -> centerViewInScrollView(thinkingCard));
        }

        AiSummaryEngine.getInstance().queryAi(context, currentVideoTitle, currentTranscript,
                new ArrayList<>(conversationHistory), cleanQuestion, new AiSummaryEngine.AiCallback() {
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
                tvText.setText("•  " + item.text);
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

    private void renderSuggestedQuestions(Context context, List<String> suggestedQuestions) {
        if (layoutSuggestedSection == null) return;

        layoutSuggestedSection.setVisibility(View.GONE);
        btnSuggested1.setVisibility(View.GONE);
        btnSuggested2.setVisibility(View.GONE);
        btnSuggested3.setVisibility(View.GONE);
        btnSuggested1.setAlpha(0f);
        btnSuggested2.setAlpha(0f);
        btnSuggested3.setAlpha(0f);

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
