package com.nitsutech.omnitv.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiSummaryEngine {
    private static final String TAG = "AiSummaryEngine";
    public static final String PREFS_NAME = "overlay_prefs";

    public static final String KEY_AI_PROVIDER = "ai_provider"; // 0: Gemini Direct, 1: OpenRouter
    public static final String KEY_GEMINI_KEY = "ai_gemini_key";
    public static final String KEY_GEMINI_MODEL = "ai_gemini_model";
    public static final String KEY_OPENROUTER_KEY = "vot_openrouter_key"; // Shared with VOT
    public static final String KEY_OPENROUTER_MODEL = "ai_openrouter_model";

    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite";
    public static final String DEFAULT_OPENROUTER_MODEL = "google/gemini-2.0-flash-001";
    public static final String KEY_CACHED_GEMINI_MODELS = "cached_gemini_models";

    public static List<String> getAvailableGeminiModels(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cached = prefs.getString(KEY_CACHED_GEMINI_MODELS, "");
        List<String> list = new ArrayList<>();
        if (!cached.isEmpty()) {
            String[] parts = cached.split(",");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                    list.add(trimmed);
                }
            }
        }
        if (list.isEmpty()) {
            list.add("gemini-3.5-flash-lite");
            list.add("gemini-3.1-flash-lite");
            list.add("gemini-2.5-flash");
            list.add("gemini-3.5-flash");
        }
        return list;
    }

    public static void fetchAvailableGeminiModelsAsync(Context context, Runnable onComplete) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(KEY_GEMINI_KEY, "").trim();
        if (apiKey.isEmpty()) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject obj = new JSONObject(sb.toString());
                    JSONArray models = obj.optJSONArray("models");
                    if (models != null) {
                        List<String> validModels = new ArrayList<>();
                        for (int i = 0; i < models.length(); i++) {
                            JSONObject m = models.getJSONObject(i);
                            String name = m.optString("name", "");
                            if (name.startsWith("models/")) name = name.substring("models/".length());
                            JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                            boolean canGenerate = false;
                            if (methods != null) {
                                for (int j = 0; j < methods.length(); j++) {
                                    if ("generateContent".equals(methods.optString(j))) {
                                        canGenerate = true;
                                        break;
                                    }
                                }
                            }
                            if (canGenerate && (name.contains("flash") || name.contains("pro") || name.contains("gemma"))) {
                                if (!name.contains("tts") && !name.contains("image") && !name.contains("robotics") && !name.contains("computer-use")) {
                                    validModels.add(name);
                                }
                            }
                        }

                        if (!validModels.isEmpty()) {
                            validModels.sort((a, b) -> {
                                if (a.contains("flash-lite") && !b.contains("flash-lite")) return -1;
                                if (!a.contains("flash-lite") && b.contains("flash-lite")) return 1;
                                return a.compareTo(b);
                            });

                            StringBuilder cacheSb = new StringBuilder();
                            for (int i = 0; i < validModels.size(); i++) {
                                if (i > 0) cacheSb.append(",");
                                cacheSb.append(validModels.get(i));
                            }
                            prefs.edit().putString(KEY_CACHED_GEMINI_MODELS, cacheSb.toString()).apply();
                            Log.d(TAG, "Cached " + validModels.size() + " live Gemini models from API");
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not fetch models dynamically: " + e.getMessage());
            }

            if (onComplete != null) {
                new Handler(Looper.getMainLooper()).post(onComplete);
            }
        });
    }

    public static final String DELIMITER_QUESTIONS = "---SUGGESTED_QUESTIONS---";
    public static final String DELIMITER_LENS_BOXES = "---LENS_BOXES---";
    public static final String DELIMITER_OBJECTS = "---DETECTED_OBJECTS---";

    private static final String SYSTEM_PROMPT =
            "Eres el asistente de inteligencia artificial de YouTube integrado en OmniTV para Smart TVs.\n"
            + "Tu objetivo es ayudar al usuario a comprender, resumir y explorar videos de YouTube y pantallas de TV de manera visual, clara y estructurada.\n\n"
            + "PAUTAS DE RESPUESTA:\n"
            + "1. Responde SIEMPRE en español conciso, claro y directo.\n"
            + "2. Estructura la información en puntos o tarjetas breves (1 o 2 oraciones por punto). No uses párrafos largos de texto corrido.\n"
            + "3. TIMESTAMPS: Cuando el usuario pida 'Puntos Clave', 'Momentos' o un resumen cronológico, inicia cada punto clave con su marca de tiempo exacta entre corchetes, por ejemplo:\n"
            + "   [01:23] Título o momento: explicación breve de lo que ocurre.\n"
            + "   [04:50] Otro punto relevante: detalle conciso.\n"
            + "   Si la transcripción no tiene marcas de tiempo, inicia cada punto con viñeta simple.\n"
            + "4. Al final de CADA una de tus respuestas, agrega OBLIGATORIAMENTE la línea exacta '" + DELIMITER_QUESTIONS + "' y justo debajo EXACTAMENTE 3 preguntas de seguimiento atractivas y relevantes para que el usuario pueda seguir explorando con su control remoto, una por línea numerada:\n"
            + "1. [Pregunta corta 1]\n"
            + "2. [Pregunta corta 2]\n"
            + "3. [Pregunta corta 3]\n\n"
            + "REGLA ESTRICTA DE NO REPETICIÓN:\n"
            + "- NUNCA repitas preguntas, temas o aspectos que el usuario ya haya preguntado o que ya hayas respondido en turnos anteriores de la conversación.\n"
            + "- Formula SIEMPRE 3 preguntas COMPLETAMENTE NUEVAS, frescas e intrigantes sobre aspectos aún no explorados del video o la escena.";

    public static String getSystemPrompt(String chatLang) {
        boolean isEnglish = "en".equalsIgnoreCase(chatLang);
        if (isEnglish) {
            return "You are the YouTube & TV AI assistant integrated into OmniTV for Smart TVs.\n"
                    + "Your goal is to help the user understand, summarize, and explore YouTube videos and TV screens in a visual, clear, and structured way.\n\n"
                    + "RESPONSE GUIDELINES:\n"
                    + "1. ALWAYS respond in clear, concise, and direct ENGLISH.\n"
                    + "2. Structure information into short bullet points or cards (1 or 2 sentences per point). Do not use long walls of text.\n"
                    + "3. TIMESTAMPS: When the user asks for 'Key Points', 'Moments', or chronological summaries, start each key point with its exact timestamp in brackets, e.g.:\n"
                    + "   [01:23] Title or moment: brief explanation.\n"
                    + "   [04:50] Another relevant point: concise detail.\n"
                    + "   If the transcript does not have timestamps, start each point with a simple bullet.\n"
                    + "4. At the very end of EVERY response, MUST include the exact line '" + DELIMITER_QUESTIONS + "' and right below EXACTLY 3 engaging follow-up questions for the user to explore with their TV remote, one per numbered line:\n"
                    + "1. [Short question 1]\n"
                    + "2. [Short question 2]\n"
                    + "3. [Short question 3]\n\n"
                    + "STRICT NON-REPETITION RULE:\n"
                    + "- NEVER repeat questions, topics, or aspects previously asked or answered in earlier turns.\n"
                    + "- Formulate 3 completely new, fresh questions about unexplored aspects.";
        } else {
            return SYSTEM_PROMPT;
        }
    }

    public static class ChatMessage {
        public final String role; // "user" or "model" / "assistant"
        public final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class AiPointItem {
        public final String timestampStr;
        public final long timestampMs;
        public final String text;
        public final boolean hasTimestamp;

        public AiPointItem(String timestampStr, long timestampMs, String text) {
            this.timestampStr = timestampStr;
            this.timestampMs = timestampMs;
            this.text = text != null ? text.trim() : "";
            this.hasTimestamp = timestampStr != null && !timestampStr.isEmpty() && timestampMs >= 0;
        }
    }

    public interface AiCallback {
        void onSuccess(String rawAnswer, List<AiPointItem> items, List<String> suggestedQuestions);
        void onError(String errorMessage);
    }

    public static class LensBoxItem {
        public final int ymin;
        public final int xmin;
        public final int ymax;
        public final int xmax;
        public final String originalText;
        public final String translatedText;
        public final String locationHint;

        public LensBoxItem(int ymin, int xmin, int ymax, int xmax, String originalText, String translatedText, String locationHint) {
            this.ymin = ymin;
            this.xmin = xmin;
            this.ymax = ymax;
            this.xmax = xmax;
            this.originalText = originalText != null ? originalText.trim() : "";
            this.translatedText = translatedText != null ? translatedText.trim() : "";
            this.locationHint = locationHint != null ? locationHint.trim() : "";
        }
    }

    public static class VisionResult {
        public final String rawAnswer;
        public final List<String> detectedObjects;
        public final List<LensBoxItem> lensBoxes;
        public final List<String> suggestedQuestions;

        public VisionResult(String rawAnswer, List<String> detectedObjects, List<LensBoxItem> lensBoxes, List<String> suggestedQuestions) {
            this.rawAnswer = rawAnswer != null ? rawAnswer.trim() : "";
            this.detectedObjects = detectedObjects != null ? detectedObjects : new ArrayList<>();
            this.lensBoxes = lensBoxes != null ? lensBoxes : new ArrayList<>();
            this.suggestedQuestions = suggestedQuestions != null ? suggestedQuestions : new ArrayList<>();
        }
    }

    public interface VisionCallback {
        void onSuccess(VisionResult result);
        void onError(String errorMessage);
    }

    private static AiSummaryEngine instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized AiSummaryEngine getInstance() {
        if (instance == null) {
            instance = new AiSummaryEngine();
        }
        return instance;
    }

    public void queryAi(Context context, String videoTitle, String transcript,
                        List<ChatMessage> conversationHistory, String currentQuestion,
                        AiCallback callback) {
        queryAi(context, videoTitle, transcript, conversationHistory, currentQuestion, "es", callback);
    }

    public void queryAi(Context context, String videoTitle, String transcript,
                        List<ChatMessage> conversationHistory, String currentQuestion,
                        String chatLang, AiCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int provider = prefs.getInt(KEY_AI_PROVIDER, 0); // Default: Gemini Direct

        executor.execute(() -> {
            try {
                if (provider == 0) {
                    callGeminiDirect(prefs, videoTitle, transcript, conversationHistory, currentQuestion, chatLang, callback);
                } else {
                    callOpenRouter(prefs, videoTitle, transcript, conversationHistory, currentQuestion, chatLang, callback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error executing AI query", e);
                postError(callback, "Error: " + e.getMessage());
            }
        });
    }

    private void callGeminiDirect(SharedPreferences prefs, String videoTitle, String transcript,
                                  List<ChatMessage> history, String currentQuestion,
                                  String chatLang, AiCallback callback) {
        String apiKey = prefs.getString(KEY_GEMINI_KEY, "").trim();
        if (apiKey.isEmpty()) {
            postError(callback, "⚠️ Clave de Gemini no configurada.\nConfigúrala en el menú o vía ADB.");
            return;
        }

        String model = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL).trim();
        if (model.isEmpty()) model = DEFAULT_GEMINI_MODEL;

        try {
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();

            // Generation config with zero thinking budget for instant TV response
            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.7);
            genConfig.put("maxOutputTokens", 2048);
            if (model.equals("gemini-2.5-flash") || model.equals("gemini-2.5-pro")) {
                JSONObject thinkingConfig = new JSONObject();
                thinkingConfig.put("thinkingBudget", 0);
                genConfig.put("thinkingConfig", thinkingConfig);
            }
            body.put("generationConfig", genConfig);

            // System instruction
            JSONObject systemInstruction = new JSONObject();
            JSONArray sysParts = new JSONArray();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", getSystemPrompt(chatLang));
            sysParts.put(sysPart);
            systemInstruction.put("parts", sysParts);
            body.put("systemInstruction", systemInstruction);

            // Contents array
            JSONArray contents = new JSONArray();

            // First turn: inject video context
            StringBuilder initialPrompt = new StringBuilder();
            if (videoTitle != null && !videoTitle.trim().isEmpty()) {
                initialPrompt.append("Título del video: \"").append(videoTitle.trim()).append("\"\n\n");
            }
            if (transcript != null && !transcript.trim().isEmpty()) {
                String safeTranscript = transcript;
                if (safeTranscript.length() > 80000) {
                    safeTranscript = safeTranscript.substring(0, 80000) + "\n...[transcripción truncada]";
                }
                initialPrompt.append("Transcripción del video:\n\"\"\"\n").append(safeTranscript).append("\n\"\"\"\n\n");
            }

            // If we have history, build turns
            if (history != null && !history.isEmpty()) {
                for (int i = 0; i < history.size(); i++) {
                    ChatMessage msg = history.get(i);
                    JSONObject turn = new JSONObject();
                    turn.put("role", "assistant".equals(msg.role) ? "model" : "user");
                    JSONArray parts = new JSONArray();
                    JSONObject part = new JSONObject();

                    if (i == 0 && "user".equals(msg.role)) {
                        part.put("text", initialPrompt.toString() + "Pregunta: " + msg.content);
                    } else {
                        part.put("text", msg.content);
                    }
                    parts.put(part);
                    turn.put("parts", parts);
                    contents.put(turn);
                }

                // Current question
                JSONObject curTurn = new JSONObject();
                curTurn.put("role", "user");
                JSONArray curParts = new JSONArray();
                JSONObject curPart = new JSONObject();
                StringBuilder qBuilder = new StringBuilder(currentQuestion);
                StringBuilder prevQuestions = new StringBuilder();
                for (ChatMessage m : history) {
                    if ("user".equals(m.role) && m.content != null && !m.content.trim().isEmpty()) {
                        if (prevQuestions.length() > 0) prevQuestions.append("; ");
                        prevQuestions.append(m.content.trim());
                    }
                }
                if (prevQuestions.length() > 0) {
                    qBuilder.append("\n\n(Instrucción importante para '").append(DELIMITER_QUESTIONS).append("': Las 3 preguntas sugeridas al final deben ser completamente nuevas y distintas a las ya consultadas: ")
                            .append(prevQuestions.toString())
                            .append(")");
                }
                curPart.put("text", qBuilder.toString());
                curParts.put(curPart);
                curTurn.put("parts", curParts);
                contents.put(curTurn);
            } else {
                // First question
                JSONObject turn = new JSONObject();
                turn.put("role", "user");
                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", initialPrompt.toString() + "Instrucción / Pregunta:\n" + currentQuestion);
                parts.put(part);
                turn.put("parts", parts);
                contents.put(turn);
            }

            body.put("contents", contents);

            byte[] jsonBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(jsonBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();

            if (responseCode != 200) {
                Log.e(TAG, "Gemini API error code: " + responseCode + " - " + sb.toString());
                String errMessage = parseGeminiError(sb.toString(), responseCode);
                postError(callback, errMessage);
                return;
            }

            JSONObject resJson = new JSONObject(sb.toString());
            JSONArray candidates = resJson.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject first = candidates.getJSONObject(0);
                JSONObject contentObj = first.optJSONObject("content");
                if (contentObj != null) {
                    JSONArray partsArr = contentObj.optJSONArray("parts");
                    if (partsArr != null && partsArr.length() > 0) {
                        String fullText = partsArr.getJSONObject(0).optString("text", "");
                        parseAndDeliver(fullText, callback);
                        return;
                    }
                }
            }

            postError(callback, "Gemini no devolvió texto en la respuesta.");
        } catch (Exception e) {
            Log.e(TAG, "Exception calling Gemini Direct", e);
            postError(callback, "Error de conexión con Gemini: " + e.getMessage());
        }
    }

    private void callOpenRouter(SharedPreferences prefs, String videoTitle, String transcript,
                                List<ChatMessage> history, String currentQuestion,
                                String chatLang, AiCallback callback) {
        String apiKey = prefs.getString(KEY_OPENROUTER_KEY, "").trim();
        if (apiKey.isEmpty()) {
            postError(callback, "⚠️ Clave de OpenRouter no configurada.\nConfigúrala en el menú o vía ADB.");
            return;
        }

        String model = prefs.getString(KEY_OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODEL).trim();
        if (model.isEmpty()) model = DEFAULT_OPENROUTER_MODEL;

        try {
            URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("HTTP-Referer", "https://github.com/ismael6499/omnitv");
            conn.setRequestProperty("X-Title", "OmniTV AI Summary");
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", model);

            JSONArray messages = new JSONArray();

            // System prompt
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", getSystemPrompt(chatLang));
            messages.put(sysMsg);

            // First turn: inject video context
            StringBuilder initialPrompt = new StringBuilder();
            if (videoTitle != null && !videoTitle.trim().isEmpty()) {
                initialPrompt.append("Título del video: \"").append(videoTitle.trim()).append("\"\n\n");
            }
            if (transcript != null && !transcript.trim().isEmpty()) {
                String safeTranscript = transcript;
                if (safeTranscript.length() > 60000) {
                    safeTranscript = safeTranscript.substring(0, 60000) + "\n...[transcripción truncada]";
                }
                initialPrompt.append("Transcripción del video:\n\"\"\"\n").append(safeTranscript).append("\n\"\"\"\n\n");
            }

            if (history != null && !history.isEmpty()) {
                for (int i = 0; i < history.size(); i++) {
                    ChatMessage msg = history.get(i);
                    JSONObject m = new JSONObject();
                    m.put("role", msg.role);
                    if (i == 0 && "user".equals(msg.role)) {
                        m.put("content", initialPrompt.toString() + "Pregunta: " + msg.content);
                    } else {
                        m.put("content", msg.content);
                    }
                    messages.put(m);
                }

                JSONObject cur = new JSONObject();
                cur.put("role", "user");
                StringBuilder qBuilder = new StringBuilder(currentQuestion);
                StringBuilder prevQuestions = new StringBuilder();
                for (ChatMessage m : history) {
                    if ("user".equals(m.role) && m.content != null && !m.content.trim().isEmpty()) {
                        if (prevQuestions.length() > 0) prevQuestions.append("; ");
                        prevQuestions.append(m.content.trim());
                    }
                }
                if (prevQuestions.length() > 0) {
                    qBuilder.append("\n\n(Instrucción importante para '").append(DELIMITER_QUESTIONS).append("': Las 3 preguntas sugeridas al final deben ser completamente nuevas y distintas a las ya consultadas: ")
                            .append(prevQuestions.toString())
                            .append(")");
                }
                cur.put("content", qBuilder.toString());
                messages.put(cur);
            } else {
                JSONObject m = new JSONObject();
                m.put("role", "user");
                m.put("content", initialPrompt.toString() + "Instrucción / Pregunta:\n" + currentQuestion);
                messages.put(m);
            }

            body.put("messages", messages);

            byte[] jsonBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(jsonBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();

            if (responseCode != 200) {
                Log.e(TAG, "OpenRouter API error: " + responseCode + " - " + sb.toString());
                postError(callback, "Error OpenRouter (" + responseCode + "): " + sb.toString());
                return;
            }

            JSONObject resJson = new JSONObject(sb.toString());
            JSONArray choices = resJson.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg != null) {
                    String fullText = msg.optString("content", "");
                    parseAndDeliver(fullText, callback);
                    return;
                }
            }

            postError(callback, "OpenRouter no devolvió contenido.");
        } catch (Exception e) {
            Log.e(TAG, "Exception calling OpenRouter", e);
            postError(callback, "Error de conexión con OpenRouter: " + e.getMessage());
        }
    }

    private void parseAndDeliver(String rawText, AiCallback callback) {
        String answer = rawText;
        List<String> questions = new ArrayList<>();

        if (rawText.contains(DELIMITER_QUESTIONS)) {
            int idx = rawText.indexOf(DELIMITER_QUESTIONS);
            answer = rawText.substring(0, idx).trim();
            String qBlock = rawText.substring(idx + DELIMITER_QUESTIONS.length()).trim();

            String[] lines = qBlock.split("\n");
            for (String l : lines) {
                String clean = l.trim().replaceAll("^[0-9]+[.)-]\\s*", "").trim();
                if (!clean.isEmpty()) {
                    questions.add(clean);
                }
            }
        }

        // Fallback default suggestions if AI didn't format delimiter properly
        if (questions.isEmpty()) {
            questions.add("¿Cuáles son las conclusiones principales?");
            questions.add("¿Qué detalles o ejemplos destacados dio?");
            questions.add("¿Qué recomendaciones o consejos brinda?");
        }

        final String finalAnswer = answer;
        final List<AiPointItem> items = parseContentItems(answer);
        final List<String> finalQuestions = questions;
        mainHandler.post(() -> callback.onSuccess(finalAnswer, items, finalQuestions));
    }

    public static List<AiPointItem> parseContentItems(String answer) {
        List<AiPointItem> items = new ArrayList<>();
        if (answer == null || answer.trim().isEmpty()) return items;

        java.util.regex.Pattern tsPattern = java.util.regex.Pattern.compile("(?:\\[|\\()?\\b(\\d{1,2}:\\d{2}(?::\\d{2})?)\\b(?:\\]|\\))?[\\s*:-]*(.*)$");
        String[] lines = answer.split("\n");
        StringBuilder currentBlock = new StringBuilder();
        String currentTs = null;
        long currentTsMs = -1;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (currentBlock.length() > 0) {
                    items.add(new AiPointItem(currentTs, currentTsMs, cleanMarkdown(currentBlock.toString().trim())));
                    currentBlock.setLength(0);
                    currentTs = null;
                    currentTsMs = -1;
                }
                continue;
            }

            boolean isBullet = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") || line.matches("^[0-9]+[.)-]\\s*.*");
            java.util.regex.Matcher m = tsPattern.matcher(line);

            if (m.find() && (isBullet || m.start() < 6)) {
                if (currentBlock.length() > 0) {
                    items.add(new AiPointItem(currentTs, currentTsMs, cleanMarkdown(currentBlock.toString().trim())));
                    currentBlock.setLength(0);
                }
                currentTs = m.group(1);
                currentTsMs = parseTimestampToMs(currentTs);
                String rest = m.group(2);
                currentBlock.append(rest != null ? rest.trim() : "");
            } else if (isBullet) {
                if (currentBlock.length() > 0) {
                    items.add(new AiPointItem(currentTs, currentTsMs, cleanMarkdown(currentBlock.toString().trim())));
                    currentBlock.setLength(0);
                    currentTs = null;
                    currentTsMs = -1;
                }
                currentBlock.append(line.replaceFirst("^[-*•0-9.)]+\\s*", ""));
            } else {
                if (currentBlock.length() > 0) currentBlock.append(" ");
                currentBlock.append(line);
            }
        }

        if (currentBlock.length() > 0) {
            items.add(new AiPointItem(currentTs, currentTsMs, cleanMarkdown(currentBlock.toString().trim())));
        }

        if (items.isEmpty()) {
            items.add(new AiPointItem(null, -1, cleanMarkdown(answer.trim())));
        }
        return items;
    }

    public static long parseTimestampToMs(String ts) {
        if (ts == null) return -1;
        try {
            String[] parts = ts.split(":");
            if (parts.length == 2) {
                long m = Long.parseLong(parts[0].trim());
                long s = Long.parseLong(parts[1].trim());
                return (m * 60 + s) * 1000L;
            } else if (parts.length == 3) {
                long h = Long.parseLong(parts[0].trim());
                long m = Long.parseLong(parts[1].trim());
                long s = Long.parseLong(parts[2].trim());
                return (h * 3600 + m * 60 + s) * 1000L;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static String cleanMarkdown(String text) {
        if (text == null) return "";
        return text.replace("**", "").replace("__", "").trim();
    }

    private String parseGeminiError(String errorJson, int code) {
        try {
            JSONObject obj = new JSONObject(errorJson);
            JSONObject err = obj.optJSONObject("error");
            if (err != null) {
                String msg = err.optString("message", "");
                if (code == 400 || code == 403) {
                    return "Error de clave API de Gemini (" + code + "): " + msg;
                }
                return "Gemini API (" + code + "): " + msg;
            }
        } catch (Exception ignored) {}
        return "Error (" + code + ") al contactar Gemini.";
    }

    private void postError(AiCallback callback, String errorMsg) {
        mainHandler.post(() -> callback.onError(errorMsg));
    }

    public void askAiVision(Context context, Bitmap screenshot, String prompt, String chatLang, VisionCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int provider = prefs.getInt(KEY_AI_PROVIDER, 0);

        executor.execute(() -> {
            try {
                if (provider == 0) {
                    callGeminiVision(prefs, screenshot, prompt, chatLang, callback);
                } else {
                    callOpenRouterVision(prefs, screenshot, prompt, chatLang, callback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error executing AI vision request", e);
                mainHandler.post(() -> callback.onError("Error de visión: " + e.getMessage()));
            }
        });
    }

    private void callGeminiVision(SharedPreferences prefs, Bitmap screenshot, String prompt, String chatLang, VisionCallback callback) {
        String apiKey = prefs.getString(KEY_GEMINI_KEY, "").trim();
        if (apiKey.isEmpty()) {
            mainHandler.post(() -> callback.onError("⚠️ Clave de Gemini no configurada.\nConfigúrala en el menú o vía ADB."));
            return;
        }

        String model = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL).trim();
        if (model.isEmpty()) model = DEFAULT_GEMINI_MODEL;

        try {
            String base64Data = bitmapToBase64Jpeg(screenshot);
            if (base64Data.isEmpty()) {
                mainHandler.post(() -> callback.onError("Error al codificar captura de pantalla."));
                return;
            }

            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();

            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.4);
            genConfig.put("maxOutputTokens", 2048);
            if (model.equals("gemini-2.5-flash") || model.equals("gemini-2.5-pro")) {
                JSONObject thinkingConfig = new JSONObject();
                thinkingConfig.put("thinkingBudget", 0);
                genConfig.put("thinkingConfig", thinkingConfig);
            }
            body.put("generationConfig", genConfig);

            JSONObject systemInstruction = new JSONObject();
            JSONArray sysParts = new JSONArray();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", getSystemPrompt(chatLang));
            sysParts.put(sysPart);
            systemInstruction.put("parts", sysParts);
            body.put("systemInstruction", systemInstruction);

            JSONArray contents = new JSONArray();
            JSONObject turn = new JSONObject();
            turn.put("role", "user");
            JSONArray parts = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);
            parts.put(textPart);

            JSONObject imgPart = new JSONObject();
            JSONObject inlineData = new JSONObject();
            inlineData.put("mimeType", "image/jpeg");
            inlineData.put("data", base64Data);
            imgPart.put("inlineData", inlineData);
            parts.put(imgPart);

            turn.put("parts", parts);
            contents.put(turn);
            body.put("contents", contents);

            byte[] jsonBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(jsonBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();

            if (responseCode != 200) {
                Log.e(TAG, "Gemini Vision error: " + responseCode + " - " + sb.toString());
                String errMessage = parseGeminiError(sb.toString(), responseCode);
                mainHandler.post(() -> callback.onError(errMessage));
                return;
            }

            JSONObject resJson = new JSONObject(sb.toString());
            JSONArray candidates = resJson.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject first = candidates.getJSONObject(0);
                JSONObject contentObj = first.optJSONObject("content");
                if (contentObj != null) {
                    JSONArray partsArr = contentObj.optJSONArray("parts");
                    if (partsArr != null && partsArr.length() > 0) {
                        String fullText = partsArr.getJSONObject(0).optString("text", "");
                        parseVisionResponse(fullText, callback);
                        return;
                    }
                }
            }

            mainHandler.post(() -> callback.onError("Gemini no devolvió respuesta para la imagen."));
        } catch (Exception e) {
            Log.e(TAG, "Exception in callGeminiVision", e);
            mainHandler.post(() -> callback.onError("Error al conectar con Gemini Visión: " + e.getMessage()));
        }
    }

    private void callOpenRouterVision(SharedPreferences prefs, Bitmap screenshot, String prompt, String chatLang, VisionCallback callback) {
        String apiKey = prefs.getString(KEY_OPENROUTER_KEY, "").trim();
        if (apiKey.isEmpty()) {
            mainHandler.post(() -> callback.onError("⚠️ Clave de OpenRouter no configurada.\nConfigúrala en el menú o vía ADB."));
            return;
        }

        String model = prefs.getString(KEY_OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODEL).trim();
        if (model.isEmpty()) model = DEFAULT_OPENROUTER_MODEL;

        try {
            String base64Data = bitmapToBase64Jpeg(screenshot);
            if (base64Data.isEmpty()) {
                mainHandler.post(() -> callback.onError("Error al codificar captura de pantalla."));
                return;
            }

            URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("HTTP-Referer", "https://github.com/ismael6499/omnitv");
            conn.setRequestProperty("X-Title", "OmniTV AI Vision");
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", model);

            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", getSystemPrompt(chatLang));
            messages.put(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            JSONArray contentArr = new JSONArray();

            JSONObject textObj = new JSONObject();
            textObj.put("type", "text");
            textObj.put("text", prompt);
            contentArr.put(textObj);

            JSONObject imgObj = new JSONObject();
            imgObj.put("type", "image_url");
            JSONObject imgUrlObj = new JSONObject();
            imgUrlObj.put("url", "data:image/jpeg;base64," + base64Data);
            imgObj.put("image_url", imgUrlObj);
            contentArr.put(imgObj);

            userMsg.put("content", contentArr);
            messages.put(userMsg);
            body.put("messages", messages);

            byte[] jsonBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(jsonBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();

            if (responseCode != 200) {
                Log.e(TAG, "OpenRouter Vision error: " + responseCode + " - " + sb.toString());
                mainHandler.post(() -> callback.onError("Error OpenRouter Visión (" + responseCode + "): " + sb.toString()));
                return;
            }

            JSONObject resJson = new JSONObject(sb.toString());
            JSONArray choices = resJson.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg != null) {
                    String fullText = msg.optString("content", "");
                    parseVisionResponse(fullText, callback);
                    return;
                }
            }

            mainHandler.post(() -> callback.onError("OpenRouter no devolvió contenido para la imagen."));
        } catch (Exception e) {
            Log.e(TAG, "Exception in callOpenRouterVision", e);
            mainHandler.post(() -> callback.onError("Error al conectar con OpenRouter Visión: " + e.getMessage()));
        }
    }

    private void parseVisionResponse(String rawText, VisionCallback callback) {
        String answer = rawText != null ? rawText.trim() : "";
        List<String> questions = new ArrayList<>();
        List<LensBoxItem> lensBoxes = new ArrayList<>();
        List<String> objects = new ArrayList<>();

        // 1. Extract suggested questions if present
        if (answer.contains(DELIMITER_QUESTIONS)) {
            int idx = answer.indexOf(DELIMITER_QUESTIONS);
            String qBlock = answer.substring(idx + DELIMITER_QUESTIONS.length()).trim();
            answer = answer.substring(0, idx).trim();

            String[] lines = qBlock.split("\n");
            for (String l : lines) {
                String clean = l.trim().replaceAll("^[0-9]+[.)-]\\s*", "").trim();
                if (!clean.isEmpty()) {
                    questions.add(clean);
                }
            }
        }

        // 2. Extract Lens boxes if present
        if (answer.contains(DELIMITER_LENS_BOXES)) {
            int idx = answer.indexOf(DELIMITER_LENS_BOXES);
            String boxBlock = answer.substring(idx + DELIMITER_LENS_BOXES.length()).trim();
            answer = answer.substring(0, idx).trim();

            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\]");
            String[] lines = boxBlock.split("\n");
            for (String l : lines) {
                l = l.trim();
                if (l.isEmpty()) continue;
                java.util.regex.Matcher m = p.matcher(l);
                if (m.find()) {
                    try {
                        int ymin = Integer.parseInt(m.group(1));
                        int xmin = Integer.parseInt(m.group(2));
                        int ymax = Integer.parseInt(m.group(3));
                        int xmax = Integer.parseInt(m.group(4));

                        String orig = "";
                        String trans = "";
                        String loc = "";

                        String[] parts = l.split("\\|");
                        for (String part : parts) {
                            part = part.trim();
                            if (part.toLowerCase().startsWith("original:")) {
                                orig = part.substring(9).trim();
                            } else if (part.toLowerCase().startsWith("translated:")) {
                                trans = part.substring(11).trim();
                            } else if (part.toLowerCase().startsWith("location:")) {
                                loc = part.substring(9).trim();
                            }
                        }

                        if (!trans.isEmpty()) {
                            // Filter 1: Ignore if translation is identical to original (already in target language)
                            String normOrig = orig.toLowerCase().replaceAll("[^a-z0-9]", "");
                            String normTrans = trans.toLowerCase().replaceAll("[^a-z0-9]", "");
                            if (!normOrig.isEmpty() && normOrig.equals(normTrans)) {
                                continue;
                            }

                            // Filter 2: Ignore timestamps (e.g. 03:10, 23:05, 18:27) or pure numbers/stats
                            if (orig.matches("^\\d{1,2}:\\d{2}(:\\d{2})?$") || trans.matches("^\\d{1,2}:\\d{2}(:\\d{2})?$")) {
                                continue;
                            }
                            if (orig.matches("^[0-9\\s.,kKmMbBpP%+-]+$") || trans.matches("^[0-9\\s.,kKmMbBpP%+-]+$")) {
                                continue;
                            }

                            // Filter 3: Ignore Android system UI notifications (e.g. wireless debugging, battery)
                            String lowerOrig = orig.toLowerCase();
                            String lowerTrans = trans.toLowerCase();
                            if (lowerOrig.contains("wireless debugging") || lowerTrans.contains("wireless debugging")
                                    || lowerOrig.contains("press & hold") || lowerTrans.contains("press & hold")) {
                                continue;
                            }

                            lensBoxes.add(new LensBoxItem(ymin, xmin, ymax, xmax, orig, trans, loc));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // 3. Extract detected objects if present
        if (answer.contains(DELIMITER_OBJECTS)) {
            int idx = answer.indexOf(DELIMITER_OBJECTS);
            String objBlock = answer.substring(idx + DELIMITER_OBJECTS.length()).trim();
            answer = answer.substring(0, idx).trim();

            String[] lines = objBlock.split("\n");
            for (String l : lines) {
                String clean = l.trim().replaceAll("^[-*•0-9.)]+\\s*", "").trim();
                if (!clean.isEmpty()) {
                    objects.add(clean);
                }
            }
        }

        if (questions.isEmpty()) {
            questions.add("¿Qué más detalles puedes explicar de esta imagen?");
            questions.add("¿Cuál es el contexto o significado principal?");
            questions.add("¿Qué recomendaciones o conclusiones ofrece?");
        }

        List<LensBoxItem> cleanBoxes = deduplicateLensBoxes(lensBoxes);
        VisionResult result = new VisionResult(answer, objects, cleanBoxes, questions);
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private static List<LensBoxItem> deduplicateLensBoxes(List<LensBoxItem> rawBoxes) {
        if (rawBoxes == null || rawBoxes.size() <= 1) return rawBoxes != null ? rawBoxes : new ArrayList<>();
        List<LensBoxItem> clean = new ArrayList<>();

        for (LensBoxItem candidate : rawBoxes) {
            String candOrig = candidate.originalText != null ? candidate.originalText.trim() : "";
            String candTrans = candidate.translatedText != null ? candidate.translatedText.trim() : "";
            if (candOrig.isEmpty() && candTrans.isEmpty()) continue;

            String candOrigNorm = candOrig.toLowerCase().replaceAll("[^a-z0-9]", "");
            String candTransNorm = candTrans.toLowerCase().replaceAll("[^a-z0-9]", "");

            int cY1 = (candidate.ymin + candidate.ymax) / 2;
            int cX1 = (candidate.xmin + candidate.xmax) / 2;

            boolean isDuplicate = false;

            for (int i = 0; i < clean.size(); i++) {
                LensBoxItem existing = clean.get(i);
                String existOrig = existing.originalText != null ? existing.originalText.trim() : "";
                String existTrans = existing.translatedText != null ? existing.translatedText.trim() : "";

                String existOrigNorm = existOrig.toLowerCase().replaceAll("[^a-z0-9]", "");
                String existTransNorm = existTrans.toLowerCase().replaceAll("[^a-z0-9]", "");

                int cY2 = (existing.ymin + existing.ymax) / 2;
                int cX2 = (existing.xmin + existing.xmax) / 2;

                int dy = Math.abs(cY1 - cY2);
                int dx = Math.abs(cX1 - cX2);

                boolean sameOrig = !candOrigNorm.isEmpty() && !existOrigNorm.isEmpty()
                        && (candOrigNorm.equals(existOrigNorm) || candOrigNorm.contains(existOrigNorm) || existOrigNorm.contains(candOrigNorm));
                boolean sameTrans = !candTransNorm.isEmpty() && !existTransNorm.isEmpty()
                        && (candTransNorm.equals(existTransNorm) || candTransNorm.contains(existTransNorm) || existTransNorm.contains(candTransNorm));

                int interYmin = Math.max(candidate.ymin, existing.ymin);
                int interXmin = Math.max(candidate.xmin, existing.xmin);
                int interYmax = Math.min(candidate.ymax, existing.ymax);
                int interXmax = Math.min(candidate.xmax, existing.xmax);

                boolean overlaps = (interYmin < interYmax) && (interXmin < interXmax);
                boolean spatiallyClose = (dy < 70 && dx < 140);

                if ((sameOrig || sameTrans) && (spatiallyClose || overlaps)) {
                    if (candTrans.length() > existTrans.length()) {
                        clean.set(i, candidate);
                    }
                    isDuplicate = true;
                    break;
                } else if (overlaps && dy < 45 && dx < 60) {
                    if (candTrans.length() > existTrans.length()) {
                        clean.set(i, candidate);
                    }
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                clean.add(candidate);
            }
        }
        return clean;
    }

    private static String bitmapToBase64Jpeg(Bitmap bitmap) {
        if (bitmap == null) return "";
        try {
            int origW = bitmap.getWidth();
            int origH = bitmap.getHeight();
            int targetW = 1280;
            int targetH = 720;
            if (origW > 0 && origH > 0) {
                float ratio = Math.min((float) targetW / origW, (float) targetH / origH);
                if (ratio < 1.0f) {
                    targetW = Math.round(origW * ratio);
                    targetH = Math.round(origH * ratio);
                } else {
                    targetW = origW;
                    targetH = origH;
                }
            }
            Bitmap scaled = (targetW != origW || targetH != origH)
                    ? Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                    : bitmap;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            if (scaled != bitmap) {
                scaled.recycle();
            }
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding bitmap to Base64", e);
            return "";
        }
    }
}
