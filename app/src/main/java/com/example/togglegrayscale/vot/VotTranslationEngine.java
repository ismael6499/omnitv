package com.example.togglegrayscale.vot;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VotTranslationEngine {
    private static final String TAG = "VotTranslationEngine";
    private static final String PREFS_NAME = "overlay_prefs";

    public static final String KEY_TRANSLATION_PROVIDER = "vot_translation_provider"; // 0: Google Free, 1: OpenRouter
    public static final String KEY_OPENROUTER_KEY = "vot_openrouter_key";
    public static final String KEY_OPENROUTER_MODEL = "vot_openrouter_model";
    public static final String KEY_TARGET_LANG = "vot_target_lang"; // default: "es"

    private static final Map<String, String> translationCache = new HashMap<>();

    public static void translateCues(Context context, List<VotCue> cues, String sourceLang, String targetLang) {
        if (cues == null || cues.isEmpty()) return;
        if (targetLang == null || targetLang.isEmpty()) targetLang = "es";

        // Check cache first
        List<VotCue> needed = new ArrayList<>();
        for (VotCue cue : cues) {
            if (cue.isTranslated) continue;
            synchronized (translationCache) {
                if (translationCache.containsKey(cue.originalText)) {
                    cue.translatedText = translationCache.get(cue.originalText);
                    cue.isTranslated = true;
                    continue;
                }
            }
            needed.add(cue);
        }

        if (needed.isEmpty()) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int provider = prefs.getInt(KEY_TRANSLATION_PROVIDER, 0); // 0: Google, 1: MyMemory, 2: OpenRouter
        String openRouterKey = prefs.getString(KEY_OPENROUTER_KEY, "").trim();

        if (provider == 2 && !openRouterKey.isEmpty()) {
            boolean success = translateWithOpenRouter(context, needed, sourceLang, targetLang, openRouterKey);
            if (success) return;
            Log.w(TAG, "OpenRouter translation failed, falling back to Google Translate");
        } else if (provider == 1) {
            translateWithMyMemory(needed, sourceLang, targetLang);
            return;
        }

        // Fallback / default: Google Translate free endpoint
        translateWithGoogle(needed, sourceLang, targetLang);
    }

    private static void translateWithMyMemory(List<VotCue> cues, String sourceLang, String targetLang) {
        if (sourceLang == null || sourceLang.isEmpty() || sourceLang.equalsIgnoreCase("auto")) {
            sourceLang = "en";
        }
        if (targetLang == null || targetLang.isEmpty()) {
            targetLang = "es";
        }
        String langpair = sourceLang.toLowerCase() + "|" + targetLang.toLowerCase();

        for (VotCue cue : cues) {
            try {
                String text = cue.originalText;
                String encoded = URLEncoder.encode(text, "UTF-8");
                String urlStr = "https://api.mymemory.translated.net/get?q=" + encoded 
                        + "&langpair=" + langpair + "&de=acostaagustin6499@gmail.com";

                String jsonStr = getHttp(urlStr);
                if (jsonStr != null && !jsonStr.isEmpty()) {
                    JSONObject root = new JSONObject(jsonStr);
                    JSONObject respData = root.optJSONObject("responseData");
                    if (respData != null) {
                        String translated = respData.optString("translatedText", "").trim();
                        if (translated.contains("&")) {
                            translated = android.text.Html.fromHtml(translated).toString().trim();
                        }
                        if (!translated.isEmpty() && !translated.startsWith("MYMEMORY WARNING")) {
                            cue.translatedText = translated;
                            cue.isTranslated = true;
                            synchronized (translationCache) {
                                translationCache.put(text, translated);
                            }
                            continue;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "MyMemory translation error for cue: " + cue.originalText, e);
            }

            // Fallback to Google for this specific cue if MyMemory fails
            translateWithGoogle(java.util.Collections.singletonList(cue), sourceLang, targetLang);
        }
    }

    private static void translateWithGoogle(List<VotCue> cues, String sourceLang, String targetLang) {
        if (sourceLang == null || sourceLang.isEmpty() || sourceLang.equalsIgnoreCase("auto")) {
            sourceLang = "auto";
        }

        for (VotCue cue : cues) {
            try {
                String text = cue.originalText;
                String encoded = URLEncoder.encode(text, "UTF-8");
                String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" 
                        + sourceLang + "&tl=" + targetLang + "&dt=t&q=" + encoded;

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
                            cue.translatedText = translated;
                            cue.isTranslated = true;
                            synchronized (translationCache) {
                                translationCache.put(text, translated);
                            }
                            continue;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Google translation error for cue: " + cue.originalText, e);
            }

            // If failed, fallback to original text
            cue.translatedText = cue.originalText;
            cue.isTranslated = true;
        }
    }

    private static boolean translateWithOpenRouter(Context context, List<VotCue> cues, String sourceLang, String targetLang, String apiKey) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String model = prefs.getString(KEY_OPENROUTER_MODEL, "deepseek/deepseek-chat");
            if (model.isEmpty()) model = "deepseek/deepseek-chat";

            String targetLangName = "natural Latin American Spanish";
            if ("en".equalsIgnoreCase(targetLang)) targetLangName = "natural conversational English";
            else if ("ko".equalsIgnoreCase(targetLang)) targetLangName = "natural conversational Korean";
            else if ("ja".equalsIgnoreCase(targetLang)) targetLangName = "natural conversational Japanese";

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Translate each numbered line from ").append(sourceLang)
                    .append(" to ").append(targetLangName).append(". Maintain sentence tone and natural spoken flow. Return ONLY the translations with line numbers (e.g. 1: translation):\n");

            for (int i = 0; i < cues.size(); i++) {
                promptBuilder.append(i + 1).append(": ").append(cues.get(i).originalText).append("\n");
            }

            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", "You are an expert film and TV voice-over dubbing translator. Translate text to " + targetLangName + ".");
            messages.put(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", promptBuilder.toString());
            messages.put(userMsg);

            body.put("messages", messages);
            body.put("temperature", 0.3);

            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("HTTP-Referer", "https://github.com/ismael6499/omnitv");
                conn.setRequestProperty("X-Title", "TV Control Hub VOT");

                byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder respSb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            respSb.append(line);
                        }
                        JSONObject respObj = new JSONObject(respSb.toString());
                        JSONArray choices = respObj.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            String reply = choices.getJSONObject(0).getJSONObject("message").getString("content");
                            parseOpenRouterLines(cues, reply);
                            return true;
                        }
                    }
                } else {
                    Log.w(TAG, "OpenRouter returned HTTP " + code);
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in translateWithOpenRouter", e);
        }
        return false;
    }

    private static void parseOpenRouterLines(List<VotCue> cues, String reply) {
        String[] lines = reply.split("\n");
        Map<Integer, String> lineMap = new HashMap<>();
        for (String l : lines) {
            l = l.trim();
            if (l.isEmpty()) continue;
            int colonIdx = l.indexOf(':');
            if (colonIdx > 0) {
                try {
                    String numStr = l.substring(0, colonIdx).replaceAll("[^0-9]", "");
                    int num = Integer.parseInt(numStr);
                    String text = l.substring(colonIdx + 1).trim();
                    lineMap.put(num, text);
                } catch (Exception ignored) {}
            }
        }

        for (int i = 0; i < cues.size(); i++) {
            VotCue cue = cues.get(i);
            String trans = lineMap.get(i + 1);
            if (trans != null && !trans.isEmpty()) {
                cue.translatedText = trans;
                cue.isTranslated = true;
                synchronized (translationCache) {
                    translationCache.put(cue.originalText, trans);
                }
            }
        }
    }

    private static String getHttp(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP GET failed: " + urlStr, e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
