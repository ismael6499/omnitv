package com.nitsutech.omnitv.comments;

import android.content.Context;
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

public class YouTubeCommentTranslator {
    private static final String TAG = "YouTubeCommentTranslator";
    private static final Map<String, String> translationCache = Collections.synchronizedMap(new HashMap<>());

    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onError(String error);
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
                String encoded = URLEncoder.encode(text.trim(), "UTF-8");
                String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                        + targetLang + "&dt=t&q=" + encoded;

                String jsonStr = getHttp(urlStr);
                if (jsonStr != null && !jsonStr.isEmpty()) {
                    JSONArray root = new JSONArray(jsonStr);
                    JSONArray parts = root.optJSONArray(0);
                    if (parts != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length(); i++) {
                            JSONArray item = parts.optJSONArray(i);
                            if (item != null) {
                                String piece = item.optString(0, "");
                                if (!piece.isEmpty()) sb.append(piece);
                            }
                        }
                        String translated = sb.toString().trim();
                        if (!translated.isEmpty()) {
                            translationCache.put(cacheKey, translated);
                            if (callback != null) callback.onSuccess(translated);
                            return;
                        }
                    }
                }
                if (callback != null) callback.onSuccess(text);
            } catch (Exception e) {
                Log.e(TAG, "Error in translateAsync", e);
                if (callback != null) callback.onError(e.getMessage());
            }
        }).start();
    }

    public static void translateBatchAsync(final List<YouTubeCommentItem> items, final String targetLang, final Runnable onComplete) {
        if (items == null || items.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        new Thread(() -> {
            for (YouTubeCommentItem item : items) {
                if (item == null || item.originalText == null || item.originalText.trim().isEmpty()) continue;
                String cacheKey = targetLang + "::" + item.originalText.trim();
                if (translationCache.containsKey(cacheKey)) {
                    item.translatedText = translationCache.get(cacheKey);
                    item.isTranslated = true;
                    item.targetLanguage = targetLang;
                    continue;
                }

                try {
                    String encoded = URLEncoder.encode(item.originalText.trim(), "UTF-8");
                    String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                            + targetLang + "&dt=t&q=" + encoded;

                    String jsonStr = getHttp(urlStr);
                    if (jsonStr != null && !jsonStr.isEmpty()) {
                        JSONArray root = new JSONArray(jsonStr);
                        JSONArray parts = root.optJSONArray(0);
                        if (parts != null) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < parts.length(); i++) {
                                JSONArray part = parts.optJSONArray(i);
                                if (part != null) {
                                    String piece = part.optString(0, "");
                                    if (!piece.isEmpty()) sb.append(piece);
                                }
                            }
                            String translated = sb.toString().trim();
                            if (!translated.isEmpty()) {
                                item.translatedText = translated;
                                item.isTranslated = true;
                                item.targetLanguage = targetLang;
                                translationCache.put(cacheKey, translated);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error translating item batch", e);
                }
            }
            if (onComplete != null) onComplete.run();
        }).start();
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
