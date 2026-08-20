package com.example.togglegrayscale;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class ToggleUtils {

    public static boolean isGrayscaleSupported() {
        // Google TV Streamer (kirkwood, Android 14) hardware HDMI pipeline disables AOSP SurfaceFlinger Daltonizer
        String model = Build.MODEL != null ? Build.MODEL : "";
        String device = Build.DEVICE != null ? Build.DEVICE : "";
        return !(model.toLowerCase().contains("streamer") || device.toLowerCase().contains("kirkwood"));
    }

    public static boolean isGrayscaleEnabled(Context context) {
        try {
            ContentResolver resolver = context.getContentResolver();
            int enabled = Settings.Secure.getInt(resolver, "accessibility_display_daltonizer_enabled", 0);
            int mode = Settings.Secure.getInt(resolver, "accessibility_display_daltonizer", -1);
            return enabled != 0 && mode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void toggleGrayscale(Context context) {
        ContentResolver resolver = context.getContentResolver();
        try {
            if (!isGrayscaleSupported()) {
                Toast.makeText(context, "⚠️ El firmware de Google TV Streamer (Android 14) no soporta Modo Gris por hardware HDMI", Toast.LENGTH_LONG).show();
                Log.w("ToggleUtils", "Grayscale mode toggled on Google TV Streamer (unsupported by OS hardware HDMI pipeline)");
            }

            boolean active = isGrayscaleEnabled(context);

            if (!active) {
                // Set daltonizer = 0 (Monochromacy / Grayscale) FIRST before setting enabled = 1
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer", 0);
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", 1);
                Log.d("ToggleUtils", "Grayscale enabled (daltonizer=0, enabled=1)");
            } else {
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", 0);
                Log.d("ToggleUtils", "Grayscale disabled (enabled=0)");
            }
        } catch (SecurityException e) {
            Toast.makeText(context, "Por favor concede permisos WRITE_SECURE_SETTINGS vía ADB", Toast.LENGTH_LONG).show();
            Log.e("ToggleUtils", "SecurityException: WRITE_SECURE_SETTINGS not granted", e);
        }
    }
}
