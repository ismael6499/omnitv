package com.example.togglegrayscale;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

public class SleepTimerService extends Service {

    private static final String TAG = "SleepTimerService";
    public static final String PREFS_NAME = "sleep_timer_prefs";
    public static final String KEY_END_TIME = "end_time_ms";

    private CountDownTimer countDownTimer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (action == null) return START_NOT_STICKY;
        if ("ACTION_START_TIMER".equals(action)) {
            startTimer(intent.getIntExtra("minutes", 30));
        } else if ("ACTION_CANCEL_TIMER".equals(action)) {
            cancelTimer();
        }
        return START_STICKY;
    }

    private void startTimer(int minutes) {
        if (countDownTimer != null) countDownTimer.cancel();
        long durationMs = (long) minutes * 60 * 1000;
        long endTime    = System.currentTimeMillis() + durationMs;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putLong(KEY_END_TIME, endTime).apply();
        Log.d(TAG, "Sleep timer started: " + minutes + " min");
        countDownTimer = new CountDownTimer(durationMs, 60000) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() {
                Log.d(TAG, "Sleep timer finished. Pausing media.");
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) {
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                    am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PAUSE));
                }
                Intent i = new Intent(SleepTimerService.this, ButtonMappingService.class);
                i.setAction("ACTION_SCHEDULED_POWER_OFF");
                startService(i);
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_END_TIME).apply();
                stopSelf();
            }
        }.start();
    }

    private void cancelTimer() {
        if (countDownTimer != null) { countDownTimer.cancel(); countDownTimer = null; }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_END_TIME).apply();
        Log.d(TAG, "Sleep timer cancelled.");
        stopSelf();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}