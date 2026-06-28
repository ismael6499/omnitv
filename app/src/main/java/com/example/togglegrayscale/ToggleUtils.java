package com.example.togglegrayscale;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class ToggleUtils {

    public static void toggleGrayscale(Context context) {
        ContentResolver resolver = context.getContentResolver();
        try {
            int daltonizerEnabled = Settings.Secure.getInt(
                    resolver,
                    "accessibility_display_daltonizer_enabled",
                    0);

            if (daltonizerEnabled == 0) {
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", 1);
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer", 0);
            } else {
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", 0);
            }
        } catch (SecurityException e) {
            Toast.makeText(context, "Please grant WRITE_SECURE_SETTINGS via ADB", Toast.LENGTH_LONG).show();
            Log.e("ToggleUtils", "SecurityException: WRITE_SECURE_SETTINGS not granted", e);
        }
    }
}
