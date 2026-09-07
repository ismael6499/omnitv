package com.nitsutech.omnitv;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class ToggleUtils {

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

    public static void setGrayscale(Context context, boolean enable) {
        ContentResolver resolver = context.getContentResolver();
        try {
            if (enable) {
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer", 0);
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", 1);
                Log.d("ToggleUtils", "Grayscale set to enabled");
            } else {
                Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", 0);
                Log.d("ToggleUtils", "Grayscale set to disabled (color)");
            }
        } catch (SecurityException e) {
            Toast.makeText(context, "Por favor concede permisos WRITE_SECURE_SETTINGS vía ADB", Toast.LENGTH_LONG).show();
            Log.e("ToggleUtils", "SecurityException: WRITE_SECURE_SETTINGS not granted", e);
        }
    }
}
