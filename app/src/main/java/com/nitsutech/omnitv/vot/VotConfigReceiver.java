package com.nitsutech.omnitv.vot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

public class VotConfigReceiver extends BroadcastReceiver {
    private static final String TAG = "VotConfigReceiver";
    private static final String PREFS_NAME = "overlay_prefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if ("com.nitsutech.omnitv.SET_OPENROUTER_KEY".equals(action) || "com.example.togglegrayscale.SET_OPENROUTER_KEY".equals(action)) {
            String key = intent.getStringExtra("key");
            if (key != null) {
                key = key.trim();
                prefs.edit().putString(VotTranslationEngine.KEY_OPENROUTER_KEY, key).apply();
                String masked = key.length() > 8 ? (key.substring(0, 4) + "..." + key.substring(key.length() - 4)) : "***";
                Toast.makeText(context, "🔑 OpenRouter Key guardada (" + masked + ")", Toast.LENGTH_LONG).show();
                Log.d(TAG, "OpenRouter API Key updated successfully via broadcast");
            }
        } else if ("com.nitsutech.omnitv.SET_OPENROUTER_MODEL".equals(action) || "com.example.togglegrayscale.SET_OPENROUTER_MODEL".equals(action)) {
            String model = intent.getStringExtra("model");
            if (model != null) {
                model = model.trim();
                prefs.edit().putString(VotTranslationEngine.KEY_OPENROUTER_MODEL, model).apply();
                Toast.makeText(context, "🤖 OpenRouter Modelo: " + model, Toast.LENGTH_LONG).show();
                Log.d(TAG, "OpenRouter Model updated successfully via broadcast: " + model);
            }
        } else if ("com.nitsutech.omnitv.SET_GEMINI_KEY".equals(action)) {
            String key = intent.getStringExtra("key");
            if (key != null) {
                key = key.trim();
                prefs.edit().putString("ai_gemini_key", key).apply();
                String masked = key.length() > 8 ? (key.substring(0, 4) + "..." + key.substring(key.length() - 4)) : "***";
                Toast.makeText(context, "🔑 Gemini API Key guardada (" + masked + ")", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Gemini API Key updated successfully via broadcast");
            }
        } else if ("com.nitsutech.omnitv.SET_AI_PROVIDER".equals(action)) {
            int provider = intent.getIntExtra("provider", 0);
            prefs.edit().putInt("ai_provider", provider).apply();
            String name = provider == 1 ? "OpenRouter" : "Google Gemini Directo";
            Toast.makeText(context, "🤖 Proveedor de IA: " + name, Toast.LENGTH_SHORT).show();
        } else if ("com.nitsutech.omnitv.SET_AI_MODEL".equals(action)) {
            String model = intent.getStringExtra("model");
            if (model != null) {
                model = model.trim();
                prefs.edit().putString("ai_gemini_model", model).putString("ai_model", model).apply();
                Toast.makeText(context, "🤖 Modelo de IA: " + model, Toast.LENGTH_SHORT).show();
            }
        } else if ("com.nitsutech.omnitv.OPEN_AI_SUMMARY".equals(action)) {
            com.nitsutech.omnitv.ButtonMappingService svc = com.nitsutech.omnitv.ButtonMappingService.instance;
            if (svc != null) {
                svc.handleAction("ACTION_OPEN_AI_SUMMARY", null);
            } else {
                com.nitsutech.omnitv.ai.AiSummaryOverlay.getInstance().show(context);
            }
        } else if ("com.nitsutech.omnitv.QUERY_AI_SUMMARY".equals(action)) {
            String q = intent.getStringExtra("query");
            if (q != null && !q.trim().isEmpty()) {
                com.nitsutech.omnitv.ai.AiSummaryOverlay.getInstance().executeCustomQuery(context, q.trim());
            }
        }
    }
}
