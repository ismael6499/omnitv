package com.nitsutech.omnitv.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
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

    private static final String SYSTEM_PROMPT =
            "Eres el asistente de inteligencia artificial de YouTube integrado en OmniTV para Smart TVs.\n"
            + "Tu objetivo es ayudar al usuario a comprender, resumir y explorar videos de YouTube de manera visual, clara y estructurada.\n\n"
            + "PAUTAS DE RESPUESTA:\n"
            + "1. Responde SIEMPRE en español conciso, claro y directo.\n"
            + "2. Estructura la información en puntos o tarjetas breves (1 o 2 oraciones por punto). No uses párrafos largos de texto corrido.\n"
            + "3. TIMESTAMPS: Cuando el usuario pida 'Puntos Clave', 'Momentos' o un resumen cronológico, inicia cada punto clave con su marca de tiempo exacta entre corchetes, por ejemplo:\n"
            + "   [01:23] Título o momento: explicación breve de lo que ocurre.\n"
            + "   [04:50] Otro punto relevante: detalle conciso.\n"
            + "   Si la transcripción no tiene marcas de tiempo, inicia cada punto con viñeta simple.\n"
            + "4. Al final de CADA una de tus respuestas, agrega OBLIGATORIAMENTE la línea exacta '" + DELIMITER_QUESTIONS + "' y justo debajo EXACTAMENTE 3 preguntas de seguimiento atractivas y relevantes para que el usuario pueda seguir explorando el video con su control remoto, una por línea numerada:\n"
            + "1. [Pregunta corta 1]\n"
            + "2. [Pregunta corta 2]\n"
            + "3. [Pregunta corta 3]\n\n"
            + "REGLA ESTRICTA DE NO REPETICIÓN:\n"
            + "- NUNCA repitas preguntas, temas o aspectos que el usuario ya haya preguntado o que ya hayas respondido en turnos anteriores de la conversación.\n"
            + "- Formula SIEMPRE 3 preguntas COMPLETAMENTE NUEVAS, frescas e intrigantes sobre aspectos aún no explorados del video (detalles específicos, curiosidades, explicaciones técnicas, implicaciones futuras, etc.).";

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
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int provider = prefs.getInt(KEY_AI_PROVIDER, 0); // Default: Gemini Direct

        executor.execute(() -> {
            try {
                if (provider == 0) {
                    callGeminiDirect(prefs, videoTitle, transcript, conversationHistory, currentQuestion, callback);
                } else {
                    callOpenRouter(prefs, videoTitle, transcript, conversationHistory, currentQuestion, callback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error executing AI query", e);
                postError(callback, "Error: " + e.getMessage());
            }
        });
    }

    private void callGeminiDirect(SharedPreferences prefs, String videoTitle, String transcript,
                                  List<ChatMessage> history, String currentQuestion,
                                  AiCallback callback) {
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
            sysPart.put("text", SYSTEM_PROMPT);
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
                                AiCallback callback) {
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
            sysMsg.put("content", SYSTEM_PROMPT);
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
}
