package com.nitsutech.omnitv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import java.util.Locale;

public class I18n {

    public static final String KEY_APP_LANGUAGE = "app_language";
    public static final String LANG_ENGLISH = "en";
    public static final String LANG_SPANISH = "es";
    public static final String LANG_SYSTEM = "system";

    private static final String PREFS_NAME = "overlay_prefs";

    private static final int[] ACTION_RES_IDS = {
        R.string.action_none,
        R.string.action_mute,
        R.string.action_grayscale,
        R.string.action_black_screen,
        R.string.action_google_home,
        R.string.action_youtube,
        R.string.action_netflix,
        R.string.action_bluetooth,
        R.string.action_quick_menu,
        R.string.action_blue_light,
        R.string.action_clock,
        R.string.action_dimmer,
        R.string.action_cine_mode,
        R.string.action_system_info,
        R.string.action_reboot,
        R.string.action_system_settings,
        R.string.action_screen_mirror,
        R.string.action_recents,
        R.string.action_pause_screen_off,
        R.string.action_dimmer_down,
        R.string.action_dimmer_up,
        R.string.action_cycle_brightness,
        R.string.action_still_watching,
        R.string.action_translate_screen,
        R.string.action_frame_hud,
        R.string.action_forward_frame,
        R.string.action_backward_frame,
        R.string.action_developer_options,
        R.string.action_cycle_brightness_reverse,
        R.string.action_quick_slider,
        R.string.action_smarttube,
        R.string.action_vot,
        R.string.action_ai_summary,
        R.string.action_youtube_music
    };

    public static String getLanguage(Context context) {
        if (context == null) return LANG_ENGLISH;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_APP_LANGUAGE, LANG_ENGLISH);
    }

    public static boolean isSpanish(Context context) {
        return LANG_SPANISH.equalsIgnoreCase(getLanguage(context));
    }

    public static void setLanguage(Context context, String lang) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_APP_LANGUAGE, lang != null ? lang : LANG_ENGLISH).apply();
    }

    public static Context getLocalizedContext(Context context) {
        if (context == null) return null;
        String lang = getLanguage(context);
        if (LANG_SYSTEM.equals(lang)) {
            return context;
        }

        Locale targetLocale = new Locale(lang);
        Locale.setDefault(targetLocale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(targetLocale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = targetLocale;
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            return context;
        }
    }

    public static String get(Context context, int resId, Object... args) {
        if (context == null) return "";
        try {
            Context locCtx = getLocalizedContext(context);
            if (args != null && args.length > 0) {
                return locCtx.getString(resId, args);
            }
            return locCtx.getString(resId);
        } catch (Exception e) {
            try {
                if (args != null && args.length > 0) {
                    return context.getString(resId, args);
                }
                return context.getString(resId);
            } catch (Exception e2) {
                return "";
            }
        }
    }

    public static String[] getActionNames(Context context) {
        String[] names = new String[ACTION_RES_IDS.length];
        for (int i = 0; i < ACTION_RES_IDS.length; i++) {
            names[i] = get(context, ACTION_RES_IDS[i]);
        }
        return names;
    }

    public static String[] getStringArray(Context context, int arrayResId) {
        if (context == null) return new String[0];
        try {
            Context locCtx = getLocalizedContext(context);
            return locCtx.getResources().getStringArray(arrayResId);
        } catch (Exception e) {
            try {
                return context.getResources().getStringArray(arrayResId);
            } catch (Exception e2) {
                return new String[0];
            }
        }
    }
}
