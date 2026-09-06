package com.example.togglegrayscale.vot;

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

        if ("com.example.togglegrayscale.SET_OPENROUTER_KEY".equals(action)) {
            String key = intent.getStringExtra("key");
            if (key != null) {
                key = key.trim();
                prefs.edit().putString(VotTranslationEngine.KEY_OPENROUTER_KEY, key).apply();
                String masked = key.length() > 8 ? (key.substring(0, 4) + "..." + key.substring(key.length() - 4)) : "***";
                Toast.makeText(context, "🔑 OpenRouter Key guardada (" + masked + ")", Toast.LENGTH_LONG).show();
                Log.d(TAG, "OpenRouter API Key updated successfully via broadcast");
            }
        } else if ("com.example.togglegrayscale.SET_OPENROUTER_MODEL".equals(action)) {
            String model = intent.getStringExtra("model");
            if (model != null) {
                model = model.trim();
                prefs.edit().putString(VotTranslationEngine.KEY_OPENROUTER_MODEL, model).apply();
                Toast.makeText(context, "🤖 OpenRouter Modelo: " + model, Toast.LENGTH_LONG).show();
                Log.d(TAG, "OpenRouter Model updated successfully via broadcast: " + model);
            }
        }
    }
}
