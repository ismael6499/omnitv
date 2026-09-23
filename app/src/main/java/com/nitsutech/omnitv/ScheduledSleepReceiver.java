package com.nitsutech.omnitv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ScheduledSleepReceiver extends BroadcastReceiver {
    private static final String TAG = "ScheduledSleepReceiver";
    public static final String ACTION_TRIGGER_SCHEDULED_SLEEP = "com.nitsutech.omnitv.ACTION_TRIGGER_SCHEDULED_SLEEP";
    public static final String ACTION_TRIGGER_SCHEDULED_SLEEP_LEGACY = "com.example.togglegrayscale.ACTION_TRIGGER_SCHEDULED_SLEEP";
    public static final String PREFS_NAME = "overlay_prefs";

    public static class SleepAlarm {
        public String id;
        public int hour;
        public int minute;
        public String days; // "1,2,3,4,5,6,7" (1=Monday .. 7=Sunday)
        public boolean enabled;
        public String skipDate; // "yyyy-MM-dd" or empty

        public SleepAlarm() {}

        public SleepAlarm(String id, int hour, int minute, String days, boolean enabled) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            this.hour = hour;
            this.minute = minute;
            this.days = days != null ? days : "1,2,3,4,5,6,7";
            this.enabled = enabled;
            this.skipDate = "";
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("hour", hour);
                obj.put("minute", minute);
                obj.put("days", days != null ? days : "1,2,3,4,5,6,7");
                obj.put("enabled", enabled);
                obj.put("skipDate", skipDate != null ? skipDate : "");
            } catch (Exception ignored) {}
            return obj;
        }

        public static SleepAlarm fromJson(JSONObject obj) {
            if (obj == null) return null;
            SleepAlarm a = new SleepAlarm();
            a.id = obj.optString("id", UUID.randomUUID().toString());
            a.hour = obj.optInt("hour", 23);
            a.minute = obj.optInt("minute", 30);
            a.days = obj.optString("days", "1,2,3,4,5,6,7");
            a.enabled = obj.optBoolean("enabled", true);
            a.skipDate = obj.optString("skipDate", "");
            return a;
        }
    }

    public static synchronized List<SleepAlarm> loadAlarms(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<SleepAlarm> list = new ArrayList<>();
        String jsonStr = prefs.getString("scheduled_sleep_alarms_json", "");
        if (jsonStr != null && !jsonStr.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    SleepAlarm a = SleepAlarm.fromJson(arr.getJSONObject(i));
                    if (a != null) list.add(a);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing scheduled_sleep_alarms_json", e);
            }
        }

        // Automatic migration from single legacy alarm
        if (list.isEmpty()) {
            boolean legacyEnabled = prefs.getBoolean("scheduled_sleep_enabled", false);
            int legacyHour = prefs.getInt("scheduled_sleep_hour", 23);
            int legacyMin = prefs.getInt("scheduled_sleep_minute", 30);
            String legacyDays = prefs.getString("scheduled_sleep_days", "1,2,3,4,5,6,7");
            String legacySkip = prefs.getString("scheduled_sleep_skip_date", "");

            SleepAlarm a1 = new SleepAlarm("alarm_1", legacyHour, legacyMin, legacyDays, legacyEnabled);
            a1.skipDate = legacySkip;
            list.add(a1);
            saveAlarms(context, list);
        }
        return list;
    }

    public static synchronized void saveAlarms(Context context, List<SleepAlarm> list) {
        if (context == null || list == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        boolean hasAnyActive = false;
        for (SleepAlarm a : list) {
            arr.put(a.toJson());
            if (a.enabled) hasAnyActive = true;
        }
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString("scheduled_sleep_alarms_json", arr.toString());
        ed.putBoolean("scheduled_sleep_enabled", hasAnyActive);
        if (!list.isEmpty()) {
            SleepAlarm first = list.get(0);
            ed.putInt("scheduled_sleep_hour", first.hour);
            ed.putInt("scheduled_sleep_minute", first.minute);
            ed.putString("scheduled_sleep_days", first.days);
            ed.putString("scheduled_sleep_skip_date", first.skipDate != null ? first.skipDate : "");
        }
        ed.apply();
    }

    public static class UpcomingAlarmResult {
        public final SleepAlarm alarm;
        public final Calendar calendar;

        public UpcomingAlarmResult(SleepAlarm alarm, Calendar calendar) {
            this.alarm = alarm;
            this.calendar = calendar;
        }
    }

    public static Calendar getNextAlarmCal(SleepAlarm alarm, boolean forceTomorrow) {
        return getNextAlarmCal(alarm, null, forceTomorrow);
    }

    public static Calendar getNextAlarmCal(SleepAlarm alarm, String lastExecStamp, boolean forceTomorrow) {
        if (alarm == null) return null;
        Set<Integer> activeDays = new HashSet<>();
        for (String d : (alarm.days != null ? alarm.days : "1,2,3,4,5,6,7").split(",")) {
            try { activeDays.add(Integer.parseInt(d.trim())); } catch (Exception ignored) {}
        }
        if (activeDays.isEmpty()) {
            for (int i = 1; i <= 7; i++) activeDays.add(i);
        }

        Calendar now = Calendar.getInstance();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.HOUR_OF_DAY, alarm.hour);
        cal.set(Calendar.MINUTE, alarm.minute);

        String todayStamp = String.format(Locale.US, "%04d-%02d-%02d %02d:%02d",
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
                alarm.hour, alarm.minute);

        boolean alreadyExecutedToday = (lastExecStamp != null && lastExecStamp.equals(todayStamp));
        boolean isCurrentMinute = (now.get(Calendar.HOUR_OF_DAY) == alarm.hour && now.get(Calendar.MINUTE) == alarm.minute);
        boolean timePassed = (cal.getTimeInMillis() <= now.getTimeInMillis());

        if (forceTomorrow || alreadyExecutedToday || (timePassed && !(!forceTomorrow && isCurrentMinute && !alreadyExecutedToday))) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

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

    public static UpcomingAlarmResult getNextEarliestAlarm(Context context, boolean forceTomorrow) {
        List<SleepAlarm> alarms = loadAlarms(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastExecStamp = prefs.getString("scheduled_sleep_last_executed_stamp", "");

        SleepAlarm earliestAlarm = null;
        Calendar earliestCal = null;

        for (SleepAlarm a : alarms) {
            if (!a.enabled) continue;
            Calendar cal = getNextAlarmCal(a, lastExecStamp, forceTomorrow);
            if (cal != null) {
                if (earliestCal == null || cal.getTimeInMillis() < earliestCal.getTimeInMillis()) {
                    earliestCal = cal;
                    earliestAlarm = a;
                }
            }
        }

        if (earliestAlarm != null && earliestCal != null) {
            return new UpcomingAlarmResult(earliestAlarm, earliestCal);
        }
        return null;
    }

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
                || ACTION_TRIGGER_SCHEDULED_SLEEP.equals(action)
                || ACTION_TRIGGER_SCHEDULED_SLEEP_LEGACY.equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            if (ACTION_TRIGGER_SCHEDULED_SLEEP.equals(action) || ACTION_TRIGGER_SCHEDULED_SLEEP_LEGACY.equals(action)) {
                String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                String lastExecStamp = prefs.getString("scheduled_sleep_last_executed_stamp", "");

                String targetAlarmId = intent.getStringExtra("alarm_id");
                int targetHour = intent.getIntExtra("alarm_hour", -1);
                int targetMinute = intent.getIntExtra("alarm_minute", -1);

                Calendar now = Calendar.getInstance();
                int currentHour = now.get(Calendar.HOUR_OF_DAY);
                int currentMinute = now.get(Calendar.MINUTE);
                int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
                int isoDay = dayOfWeek == Calendar.SUNDAY ? 7 : dayOfWeek - 1;

                List<SleepAlarm> alarms = loadAlarms(context);
                boolean shouldTrigger = false;
                SleepAlarm triggeredAlarm = null;

                for (SleepAlarm a : alarms) {
                    if (!a.enabled) continue;

                    boolean timeMatches = false;
                    if (targetAlarmId != null && targetAlarmId.equals(a.id)) {
                        timeMatches = true;
                    } else if (targetHour >= 0 && targetMinute >= 0) {
                        timeMatches = (a.hour == targetHour && a.minute == targetMinute);
                    } else {
                        int diffMinutes = Math.abs((currentHour * 60 + currentMinute) - (a.hour * 60 + a.minute));
                        timeMatches = (diffMinutes <= 2);
                    }
                    if (!timeMatches) continue;

                    String alarmStamp = String.format(Locale.US, "%s %02d:%02d", todayStr, a.hour, a.minute);
                    if (alarmStamp.equals(lastExecStamp)) {
                        Log.d(TAG, "Alarm " + a.hour + ":" + a.minute + " already executed today (" + lastExecStamp + "). Skipping.");
                        continue;
                    }

                    if (todayStr.equals(a.skipDate)) {
                        Log.d(TAG, "Alarm " + a.hour + ":" + a.minute + " skipped for today: " + todayStr);
                        a.skipDate = "";
                        saveAlarms(context, alarms);
                        continue;
                    }

                    String globalSkip = prefs.getString("scheduled_sleep_skip_date", "");
                    if (todayStr.equals(globalSkip)) {
                        Log.d(TAG, "Scheduled sleep globally skipped for today: " + todayStr);
                        prefs.edit().remove("scheduled_sleep_skip_date").apply();
                        continue;
                    }

                    Set<Integer> activeDays = new HashSet<>();
                    for (String d : (a.days != null ? a.days : "").split(",")) {
                        try { activeDays.add(Integer.parseInt(d.trim())); } catch (Exception ignored) {}
                    }
                    if (activeDays.contains(isoDay)) {
                        shouldTrigger = true;
                        triggeredAlarm = a;
                        break;
                    }
                }

                if (shouldTrigger && triggeredAlarm != null) {
                    String currentStamp = String.format(Locale.US, "%s %02d:%02d", todayStr, triggeredAlarm.hour, triggeredAlarm.minute);
                    Log.d(TAG, "Executing scheduled sleep action for alarm " + triggeredAlarm.hour + ":" + triggeredAlarm.minute + " (stamp=" + currentStamp + ")!");
                    prefs.edit().putString("scheduled_sleep_last_executed_stamp", currentStamp).apply();

                    if (ButtonMappingService.instance != null) {
                        Log.d(TAG, "Triggering scheduled power off directly on ButtonMappingService instance");
                        ButtonMappingService.instance.triggerScheduledPowerOff();
                    } else {
                        Log.d(TAG, "ButtonMappingService.instance is null, attempting startService fallback");
                        try {
                            Intent serviceIntent = new Intent(context, ButtonMappingService.class);
                            serviceIntent.setAction("ACTION_SCHEDULED_POWER_OFF");
                            context.startService(serviceIntent);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed startService due to background restrictions, fallback to direct power off", e);
                            try {
                                Runtime.getRuntime().exec("input keyevent 26");
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            // Always reschedule next earliest upcoming alarm without forcing all alarms to tomorrow
            scheduleNextAlarm(context, false);
        }
    }

    public static Calendar getNextUpcomingAlarmCal(Context context, boolean forceTomorrow) {
        UpcomingAlarmResult next = getNextEarliestAlarm(context, forceTomorrow);
        if (next != null) {
            return next.calendar;
        }
        Calendar fallback = Calendar.getInstance();
        fallback.add(Calendar.DAY_OF_YEAR, 1);
        return fallback;
    }

    public static String getNextAlarmDateStr(Context context) {
        UpcomingAlarmResult next = getNextEarliestAlarm(context, false);
        if (next != null && next.calendar != null) {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(next.calendar.getTime());
        }
        return "";
    }

    public static String getNextAlarmDisplayDateStr(Context context) {
        UpcomingAlarmResult next = getNextEarliestAlarm(context, false);
        if (next != null && next.calendar != null) {
            return new SimpleDateFormat("dd/MM", Locale.US).format(next.calendar.getTime());
        }
        return "";
    }

    public static String getActiveAlarmsSummary(Context context) {
        List<SleepAlarm> alarms = loadAlarms(context);
        List<String> times = new ArrayList<>();
        List<SleepAlarm> sorted = new ArrayList<>(alarms);
        java.util.Collections.sort(sorted, new java.util.Comparator<SleepAlarm>() {
            @Override
            public int compare(SleepAlarm o1, SleepAlarm o2) {
                return Integer.compare(o1.hour * 60 + o1.minute, o2.hour * 60 + o2.minute);
            }
        });
        for (SleepAlarm a : sorted) {
            if (a.enabled) {
                times.add(String.format(Locale.US, "%02d:%02d", a.hour, a.minute));
            }
        }
        if (times.isEmpty()) {
            return "OFF";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(times.get(i));
        }
        return sb.toString();
    }

    public static void scheduleNextAlarm(Context context) {
        scheduleNextAlarm(context, false);
    }

    public static void scheduleNextAlarm(Context context, boolean forceTomorrow) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ScheduledSleepReceiver.class);
        intent.setAction(ACTION_TRIGGER_SCHEDULED_SLEEP);

        UpcomingAlarmResult next = getNextEarliestAlarm(context, forceTomorrow);
        if (next == null || am == null) {
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    8899,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );
            if (am != null) am.cancel(pi);
            Log.d(TAG, "No enabled scheduled sleep alarms found. Alarm cancelled.");
            return;
        }

        intent.putExtra("alarm_id", next.alarm.id);
        intent.putExtra("alarm_hour", next.alarm.hour);
        intent.putExtra("alarm_minute", next.alarm.minute);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                8899,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Calendar now = Calendar.getInstance();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastExecStamp = prefs.getString("scheduled_sleep_last_executed_stamp", "");
        String currentMinuteStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());

        long triggerAt;
        boolean isCurrentMinute = (now.get(Calendar.HOUR_OF_DAY) == next.alarm.hour && now.get(Calendar.MINUTE) == next.alarm.minute);
        if (!forceTomorrow && isCurrentMinute && !currentMinuteStamp.equals(lastExecStamp)) {
            triggerAt = System.currentTimeMillis() + 3000;
        } else {
            triggerAt = next.calendar.getTimeInMillis();
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(triggerAt, pi);
                am.setAlarmClock(clockInfo, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
            Log.d(TAG, "Next scheduled sleep alarm set for: " + new Date(triggerAt) + " (" + String.format(Locale.US, "%02d:%02d", next.alarm.hour, next.alarm.minute) + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error setting alarm manager", e);
        }
    }
}
