package com.nitsutech.omnitv.comments;

import android.util.Log;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class YouTubeCommentTranslator {
    private static final String TAG = "YouTubeCommentTranslator";
    private static final Map<String, String> translationCache = Collections.synchronizedMap(new HashMap<>());

    /** Hangul, kana and CJK ideographs that YouTube titles use around celebrity pairings. */
    private static final String CJK_LETTER =
            "\\u1100-\\u11FF\\u3130-\\u318F\\uA960-\\uA97F\\uAC00-\\uD7AF"
                    + "\\u3040-\\u30FF\\u3400-\\u9FFF\\uF900-\\uFAFF";

    /**
     * Dedicated “times / pairing” glyphs. Google Translate’s Korean pipeline treats them as a hard
     * stop (e.g. {@code 제나×원이} becomes only “jenna”), so they must not be sent as-is.
     * Leave the mark when both neighbors are digits ({@code 10×10}).
     */
    private static final Pattern PAIRING_GLYPH = Pattern.compile(
            "(?<![0-9])\\s*[×✕✖✗✘ⅹＸｘ⋅]\\s*(?![0-9])");

    /** Latin x/X between CJK letters, with or without spaces ({@code 제나x원이}, {@code 제나 x 원이}). */
    private static final Pattern LATIN_X_WITH_CJK = Pattern.compile(
            "(?<=[" + CJK_LETTER + "])\\s*[xX]\\s*(?=[" + CJK_LETTER + "\\p{L}])"
                    + "|(?<=[" + CJK_LETTER + "\\p{L}])\\s*[xX]\\s*(?=[" + CJK_LETTER + "])");

    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onError(String error);
    }

    public interface BatchProgressCallback {
        void onItemTranslated(YouTubeCommentItem item, int completed, int total);
    }

    public static void translateAsync(final String text, final String targetLang, final TranslationCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onSuccess("");
            return;
        }

        String cacheKey = targetLang + "::" + text.trim();
        if (translationCache.containsKey(cacheKey)) {
            if (callback != null) callback.onSuccess(translationCache.get(cacheKey));
            return;
        }

        new Thread(() -> {
            try {
                String translated = translateWithGoogle(text.trim(), targetLang);
                if (translated != null && !translated.isEmpty()) {
                    translationCache.put(cacheKey, translated);
                    if (callback != null) callback.onSuccess(translated);
                    return;
                }
                if (callback != null) callback.onSuccess(text);
            } catch (Exception e) {
                Log.e(TAG, "Error in translateAsync", e);
                if (callback != null) callback.onError(e.getMessage());
            }
        }).start();
    }

    public static void translateBatchAsync(final List<YouTubeCommentItem> items, final String targetLang, final Runnable onComplete) {
        translateBatchAsync(items, targetLang, null, onComplete);
    }

    public static void translateBatchAsync(final List<YouTubeCommentItem> items, final String targetLang,
                                           final BatchProgressCallback progressCallback, final Runnable onComplete) {
        if (items == null || items.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        new Thread(() -> {
            int completed = 0;
            for (YouTubeCommentItem item : items) {
                if (item == null || item.originalText == null || item.originalText.trim().isEmpty()) continue;
                String cacheKey = targetLang + "::" + item.originalText.trim();
                if (translationCache.containsKey(cacheKey)) {
                    item.translatedText = translationCache.get(cacheKey);
                    item.isTranslated = true;
                    item.targetLanguage = targetLang;
                    completed++;
                    if (progressCallback != null) progressCallback.onItemTranslated(item, completed, items.size());
                    continue;
                }

                try {
                    String translated = translateWithGoogle(item.originalText.trim(), targetLang);
                    if (translated != null && !translated.isEmpty()) {
                        item.translatedText = translated;
                        item.isTranslated = true;
                        item.targetLanguage = targetLang;
                        translationCache.put(cacheKey, translated);
                        completed++;
                        if (progressCallback != null) progressCallback.onItemTranslated(item, completed, items.size());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error translating item batch", e);
                }
            }
            if (onComplete != null) onComplete.run();
        }).start();
    }

    /**
     * Korean titles often join celebrity names with × (or a look-alike x). Google Translate
     * drops everything after that mark; rewriting it as {@code &} keeps the full sentence.
     */
    public static String prepareForGoogleTranslate(String text) {
        if (text == null || text.isEmpty()) return text;
        String prepared = PAIRING_GLYPH.matcher(text).replaceAll(" & ");
        prepared = LATIN_X_WITH_CJK.matcher(prepared).replaceAll(" & ");
        return prepared.replaceAll("\\s+", " ").trim();
    }

    private static String translateWithGoogle(String text, String targetLang) throws Exception {
        String encoded = URLEncoder.encode(prepareForGoogleTranslate(text), "UTF-8");
        String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                + targetLang + "&dt=t&q=" + encoded;
        String jsonStr = getHttp(urlStr);
        if (jsonStr == null || jsonStr.isEmpty()) return null;
        JSONArray root = new JSONArray(jsonStr);
        JSONArray parts = root.optJSONArray(0);
        if (parts == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONArray item = parts.optJSONArray(i);
            if (item != null) {
                String piece = item.optString(0, "");
                if (!piece.isEmpty()) sb.append(piece);
            }
        }
        String translated = sb.toString().trim();
        return translated.isEmpty() ? null : translated;
    }

    private static String getHttp(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP GET error for " + urlStr, e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
