package com.example.togglegrayscale;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ScheduledSleepReceiver extends BroadcastReceiver {
    private static final String TAG = "ScheduledSleepReceiver";
    public static final String ACTION_TRIGGER_SCHEDULED_SLEEP = "com.example.togglegrayscale.ACTION_TRIGGER_SCHEDULED_SLEEP";
    public static final String PREFS_NAME = "toggle_grayscale_overlay_prefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || ACTION_TRIGGER_SCHEDULED_SLEEP.equals(action)) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("scheduled_sleep_enabled", false);

            if (ACTION_TRIGGER_SCHEDULED_SLEEP.equals(action) && enabled) {
                String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                String skipStr = prefs.getString("scheduled_sleep_skip_date", "");

                if (todayStr.equals(skipStr)) {
                    Log.d(TAG, "Scheduled sleep skipped for today: " + todayStr);
                    prefs.edit().remove("scheduled_sleep_skip_date").apply();
                } else {
                    Calendar cal = Calendar.getInstance();
                    int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1 = Sun, 2 = Mon, ..., 7 = Sat
                    // Convert Calendar dayOfWeek (1..7, Sun..Sat) to index (1..7, Mon..Sun)
                    int isoDay = dayOfWeek == Calendar.SUNDAY ? 7 : dayOfWeek - 1;
                    String daysStr = prefs.getString("scheduled_sleep_days", "1,2,3,4,5,6,7");
                    boolean dayActive = false;
                    for (String d : daysStr.split(",")) {
                        try {
                            if (Integer.parseInt(d.trim()) == isoDay) {
                                dayActive = true;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }

                    if (dayActive) {
                        Log.d(TAG, "Executing scheduled sleep action!");
                        Intent serviceIntent = new Intent(context, ButtonMappingService.class);
                        serviceIntent.setAction("ACTION_PAUSE_SCREEN_OFF");
                        context.startService(serviceIntent);
                    }
                }
            }

            // Always reschedule the next alarm occurrence
            scheduleNextAlarm(context);
        }
    }

    public static void scheduleNextAlarm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("scheduled_sleep_enabled", false);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, ScheduledSleepReceiver.class);
        intent.setAction(ACTION_TRIGGER_SCHEDULED_SLEEP);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                8899,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        if (!enabled || am == null) {
            if (am != null) am.cancel(pi);
            Log.d(TAG, "Scheduled sleep alarm cancelled.");
            return;
        }

        int targetHour = prefs.getInt("scheduled_sleep_hour", 23);
        int targetMin = prefs.getInt("scheduled_sleep_minute", 30);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.HOUR_OF_DAY, targetHour);
        cal.set(Calendar.MINUTE, targetMin);

        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        long triggerAt = cal.getTimeInMillis();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
            Log.d(TAG, "Next scheduled sleep alarm set for: " + new Date(triggerAt));
        } catch (Exception e) {
            Log.e(TAG, "Error setting alarm manager", e);
        }
    }
}
