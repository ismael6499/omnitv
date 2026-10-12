package com.nitsutech.omnitv.translator;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.nitsutech.omnitv.ai.AiSummaryEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI Engine for the Text Translator: contextual batch translation and Lens (vision) text extraction,
 * using the AI provider and model configured in OmniTV (overlay_prefs).
 */
public class TextTranslatorEngine {
    private static final String TAG = "TextTranslatorEngine";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ListCallback {
        void onSuccess(List<String> result);
        void onError(String error);
    }

    public static boolean hasApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AiSummaryEngine.PREFS_NAME, Context.MODE_PRIVATE);
        int provider = prefs.getInt(AiSummaryEngine.KEY_AI_PROVIDER, 0);
        String key = provider == 1
                ? prefs.getString(AiSummaryEngine.KEY_OPENROUTER_KEY, "")
                : prefs.getString(AiSummaryEngine.KEY_GEMINI_KEY, "");
        return !key.trim().isEmpty();
    }

    public static String languageName(String code) {
        switch (code) {
            case "es": return "Spanish";
            case "en": return "English";
            case "ko": return "Korean";
            case "ja": return "Japanese";
            case "pt-BR": return "Brazilian Portuguese";
            default: return "English";
        }
    }

    private static final String TRANSLATE_SYSTEM_PROMPT =
            "You are a professional Smart TV subtitle and UI translator. You understand idioms, internet slang, "
                    + "colloquial expressions and YouTube thumbnail wording, and adapt Korean, Japanese, Chinese and other languages "
                    + "into natural, contextual text in the target language. Keep emojis, names and numbers. "
                    + "Respond ONLY with a JSON array of strings, same length and order as the input array, no markdown.";

    /** Translates a batch of texts keeping order. Results are returned on the main thread. */
    public static void translateBatch(final Context context, final List<String> texts, final String targetLang, final ListCallback callback) {
        executor.execute(() -> {
            try {
                JSONArray input = new JSONArray();
                for (String t : texts) input.put(t);
                String prompt = "Translate every string of this JSON array into " + languageName(targetLang)
                        + ". If a string is already in the target language, return it unchanged.\n" + input.toString();
                String answer = callConfiguredAi(context, TRANSLATE_SYSTEM_PROMPT, prompt, null, 8192);
                JSONArray arr = parseJsonArray(answer);
                final List<String> out = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    out.add(i < arr.length() ? arr.optString(i, texts.get(i)) : texts.get(i));
                }
                mainHandler.post(() -> callback.onSuccess(out));
            } catch (Exception e) {
                Log.e(TAG, "translateBatch failed", e);
                final String msg = e.getMessage() != null ? e.getMessage() : "error";
                mainHandler.post(() -> callback.onError(msg));
            }
        });
    }

    private static final String LENS_SYSTEM_PROMPT =
            "You are an OCR and visual text reader for Smart TV screens. Read ALL visible text in the image, "
                    + "including stylized or artistic typography, thumbnails, signs and burned-in subtitles, in any language. "
                    + "Ignore any app named OmniTV. Group text into logical blocks ordered top to bottom, left to right. "
                    + "Respond ONLY with a JSON array of strings (original text of each block), no markdown.";

    /** Extracts visible text blocks from a clean screenshot using the multimodal model. */
    public static void extractTextFromImage(final Context context, final Bitmap bitmap, final ListCallback callback) {
        executor.execute(() -> {
            try {
                String answer = callConfiguredAi(context, LENS_SYSTEM_PROMPT, "Extract all text blocks from this screenshot.", bitmap, 4096);
                JSONArray arr = parseJsonArray(answer);
                final List<String> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isEmpty()) out.add(s);
                }
                mainHandler.post(() -> callback.onSuccess(out));
            } catch (Exception e) {
                Log.e(TAG, "extractTextFromImage failed", e);
                final String msg = e.getMessage() != null ? e.getMessage() : "error";
                mainHandler.post(() -> callback.onError(msg));
            }
        });
    }

    private static JSONArray parseJsonArray(String answer) throws Exception {
        String s = answer == null ? "" : answer.trim();
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start < 0 || end <= start) throw new Exception("Invalid AI response");
        return new JSONArray(s.substring(start, end + 1));
    }

    private static String callConfiguredAi(Context context, String systemPrompt, String prompt,
                                          Bitmap image, int maxTokens) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(AiSummaryEngine.PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getInt(AiSummaryEngine.KEY_AI_PROVIDER, 0) != 1)
            return callGemini(context, systemPrompt, prompt, image, maxTokens);

        String apiKey = prefs.getString(AiSummaryEngine.KEY_OPENROUTER_KEY, "").trim();
        if (apiKey.isEmpty()) throw new Exception("NO_API_KEY");
        boolean vision = image != null;
        String prefKey = vision ? AiSummaryEngine.KEY_OPENROUTER_VISION_MODEL : AiSummaryEngine.KEY_OPENROUTER_MODEL;
        String fallback = vision ? AiSummaryEngine.DEFAULT_OPENROUTER_VISION_MODEL : AiSummaryEngine.DEFAULT_OPENROUTER_MODEL;
        String model = AiSummaryEngine.resolveOpenRouterModel(prefs, prefs.getString(prefKey, fallback).trim(), vision);
        if (model.isEmpty()) model = fallback;
        return callOpenRouter(context, prefs, apiKey, model, systemPrompt, prompt, image, maxTokens, new HashSet<>());
    }

    private static String callOpenRouter(Context context, SharedPreferences prefs, String apiKey, String model,
                                         String systemPrompt, String prompt, Bitmap image, int maxTokens,
                                         Set<String> attemptedModels) throws Exception {
        attemptedModels.add(model);
        String modelKey = image == null ? AiSummaryEngine.KEY_OPENROUTER_MODEL : AiSummaryEngine.KEY_OPENROUTER_VISION_MODEL;
        boolean automatic = AiSummaryEngine.OPENROUTER_AUTO_FREE_BEST.equals(prefs.getString(modelKey, ""));
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            if (image == null) {
                userMessage.put("content", prompt);
            } else {
                JSONArray content = new JSONArray();
                content.put(new JSONObject().put("type", "text").put("text", prompt));
                Bitmap scaled = image;
                if (image.getWidth() > 1920) {
                    int height = Math.round(image.getHeight() * (1920f / image.getWidth()));
                    scaled = Bitmap.createScaledBitmap(image, 1920, height, true);
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, bytes);
                String base64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
                content.put(new JSONObject().put("type", "image_url").put("image_url",
                        new JSONObject().put("url", "data:image/jpeg;base64," + base64)));
                userMessage.put("content", content);
            }
            messages.put(userMessage);
            body.put("messages", messages);

            URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("HTTP-Referer", "https://github.com/ismael6499/omnitv");
            conn.setRequestProperty("X-Title", image == null ? "OmniTV Text Translator" : "OmniTV Text Translator Lens");
            conn.setConnectTimeout(automatic ? 15000 : 25000);
            // Automatic free-model fallback must not leave Lens/translation
            // stuck on one unresponsive provider for a full minute per try.
            conn.setReadTimeout(automatic ? 20000 : 60000);
            conn.setDoOutput(true);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = conn.getOutputStream()) { os.write(payload); }

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder response = new StringBuilder();
            if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line).append('\n');
            }
            if (code != 200) {
                if (automatic && AiSummaryEngine.shouldRetryOpenRouterWithNextFreeModel(code, response.toString())) {
                    if (AiSummaryEngine.isOpenRouterEndpointRejection(code, response.toString()))
                        AiSummaryEngine.rememberRejectedOpenRouterModel(prefs, model);
                    AiSummaryEngine.OpenRouterModel next = AiSummaryEngine.getNextFreeOpenRouterModel(
                            prefs, image != null, attemptedModels);
                    if (next != null) {
                        Log.w(TAG, "OpenRouter Text Translator rejected " + model + "; retrying with " + next.id);
                        conn.disconnect();
                        conn = null;
                        return callOpenRouter(context, prefs, apiKey, next.id, systemPrompt, prompt,
                                image, maxTokens, attemptedModels);
                    }
                    throw new Exception("OpenRouter no pudo usar los modelos gratuitos disponibles. Elegí otro modelo o pulsa «Reintentar modelos rechazados» en la configuración.");
                }
                throw new Exception("OpenRouter HTTP " + code + ": " + response);
            }
            JSONObject result = new JSONObject(response.toString());
            JSONArray choices = result.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new Exception("OpenRouter devolvió una respuesta vacía.");
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) throw new Exception("OpenRouter devolvió una respuesta vacía.");
            String content = message.optString("content", "");
            if (content.trim().isEmpty()) throw new Exception("OpenRouter devolvió una respuesta vacía.");
            // Validate the structured response inside the model attempt so an
            // automatic selection can fall through to another free model when
            // a model returns prose/markdown instead of the requested JSON.
            parseJsonArray(content);
            return content;
        } catch (Exception e) {
            AiSummaryEngine.OpenRouterModel next = automatic
                    ? AiSummaryEngine.getNextFreeOpenRouterModel(prefs, image != null, attemptedModels) : null;
            if (next != null) {
                Log.w(TAG, "OpenRouter Text Translator request failed for " + model + "; retrying with " + next.id, e);
                return callOpenRouter(context, prefs, apiKey, next.id, systemPrompt, prompt,
                        image, maxTokens, attemptedModels);
            }
            throw e;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String callGemini(Context context, String systemPrompt, String prompt, Bitmap image, int maxTokens) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(AiSummaryEngine.PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(AiSummaryEngine.KEY_GEMINI_KEY, "").trim();
        if (apiKey.isEmpty()) throw new Exception("NO_API_KEY");
        String model = prefs.getString(AiSummaryEngine.KEY_GEMINI_MODEL, AiSummaryEngine.DEFAULT_GEMINI_MODEL).trim();
        if (model.isEmpty()) model = AiSummaryEngine.DEFAULT_GEMINI_MODEL;

        JSONObject body = new JSONObject();
        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.2);
        genConfig.put("maxOutputTokens", maxTokens);
        if (model.equals("gemini-2.5-flash") || model.equals("gemini-2.5-pro")) {
            genConfig.put("thinkingConfig", new JSONObject().put("thinkingBudget", 0));
        }
        body.put("generationConfig", genConfig);
        body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))));

        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        if (image != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Bitmap scaled = image;
            if (image.getWidth() > 1920) {
                int h = Math.round(image.getHeight() * (1920f / image.getWidth()));
                scaled = Bitmap.createScaledBitmap(image, 1920, h, true);
            }
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            parts.put(new JSONObject().put("inlineData", new JSONObject().put("mimeType", "image/jpeg").put("data", b64)));
        }
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));

        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(25000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        conn.disconnect();
        if (code != 200) {
            Log.e(TAG, "Gemini error " + code + ": " + sb);
            throw new Exception("HTTP " + code);
        }
        JSONObject res = new JSONObject(sb.toString());
        JSONArray candidates = res.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) throw new Exception("Empty response");
        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        if (content == null) throw new Exception("Empty response");
        JSONArray rParts = content.optJSONArray("parts");
        StringBuilder text = new StringBuilder();
        if (rParts != null) {
            for (int i = 0; i < rParts.length(); i++) text.append(rParts.getJSONObject(i).optString("text", ""));
        }
        return text.toString();
    }
}
