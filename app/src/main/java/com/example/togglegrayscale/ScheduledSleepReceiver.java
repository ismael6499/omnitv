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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ScheduledSleepReceiver extends BroadcastReceiver {
    private static final String TAG = "ScheduledSleepReceiver";
    public static final String ACTION_TRIGGER_SCHEDULED_SLEEP = "com.example.togglegrayscale.ACTION_TRIGGER_SCHEDULED_SLEEP";
    public static final String PREFS_NAME = "overlay_prefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)
                || ACTION_TRIGGER_SCHEDULED_SLEEP.equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("scheduled_sleep_enabled", false);

            if (ACTION_TRIGGER_SCHEDULED_SLEEP.equals(action) && enabled) {
                String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                String currentStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
                String skipStr = prefs.getString("scheduled_sleep_skip_date", "");
                String lastExecStamp = prefs.getString("scheduled_sleep_last_executed_stamp", "");

                if (todayStr.equals(skipStr)) {
                    Log.d(TAG, "Scheduled sleep skipped for today: " + todayStr);
                    prefs.edit().remove("scheduled_sleep_skip_date").apply();
                } else if (currentStamp.equals(lastExecStamp)) {
                    Log.d(TAG, "Scheduled sleep already executed for stamp (" + currentStamp + "). Skipping duplicate execution.");
                } else {
                    Calendar cal = Calendar.getInstance();
                    int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
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
                        prefs.edit().putString("scheduled_sleep_last_executed_stamp", currentStamp).apply();
                        Intent serviceIntent = new Intent(context, ButtonMappingService.class);
                        serviceIntent.setAction("ACTION_SCHEDULED_POWER_OFF");
                        context.startService(serviceIntent);
                    }
                }
            }

            // Reschedule the next alarm occurrence for tomorrow/next active day
            scheduleNextAlarm(context, true);
        }
    }

    public static Calendar getNextUpcomingAlarmCal(Context context, boolean forceTomorrow) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int targetHour = prefs.getInt("scheduled_sleep_hour", 23);
        int targetMin = prefs.getInt("scheduled_sleep_minute", 30);
        String daysStr = prefs.getString("scheduled_sleep_days", "1,2,3,4,5,6,7");
        Set<Integer> activeDays = new HashSet<>();
        for (String d : daysStr.split(",")) {
            try { activeDays.add(Integer.parseInt(d.trim())); } catch (Exception ignored) {}
        }

        Calendar now = Calendar.getInstance();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.HOUR_OF_DAY, targetHour);
        cal.set(Calendar.MINUTE, targetMin);

        // Check if target is instant test trigger for current minute
        if (!forceTomorrow && now.get(Calendar.HOUR_OF_DAY) == targetHour && now.get(Calendar.MINUTE) == targetMin) {
            return cal;
        }

        if (forceTomorrow || cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        // Loop forward up to 7 days until finding an active day
        for (int i = 0; i < 7; i++) {
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int isoDay = dayOfWeek == Calendar.SUNDAY ? 7 : dayOfWeek - 1;
            if (activeDays.contains(isoDay)) {
                break;
            }
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        return cal;
    }

    public static String getNextAlarmDateStr(Context context) {
        Calendar cal = getNextUpcomingAlarmCal(context, false);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    public static String getNextAlarmDisplayDateStr(Context context) {
        Calendar cal = getNextUpcomingAlarmCal(context, false);
        return new SimpleDateFormat("dd/MM", Locale.US).format(cal.getTime());
    }

    public static void scheduleNextAlarm(Context context) {
        scheduleNextAlarm(context, false);
    }

    public static void scheduleNextAlarm(Context context, boolean forceTomorrow) {
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
            Log.d(TAG, "Scheduled sleep alarm cancelled because enabled=false.");
            return;
        }

        int targetHour = prefs.getInt("scheduled_sleep_hour", 23);
        int targetMin = prefs.getInt("scheduled_sleep_minute", 30);
        Calendar now = Calendar.getInstance();

        long triggerAt;
        if (!forceTomorrow && now.get(Calendar.HOUR_OF_DAY) == targetHour && now.get(Calendar.MINUTE) == targetMin) {
            triggerAt = System.currentTimeMillis() + 3000;
        } else {
            Calendar cal = getNextUpcomingAlarmCal(context, forceTomorrow);
            triggerAt = cal.getTimeInMillis();
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // setAlarmClock guarantees high-priority exact CPU wakeup from Doze/Sleep mode
                AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(triggerAt, pi);
                am.setAlarmClock(clockInfo, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
