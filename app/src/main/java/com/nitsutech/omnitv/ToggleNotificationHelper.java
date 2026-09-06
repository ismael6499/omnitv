package com.nitsutech.omnitv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ToggleNotificationHelper {

    private static final String CHANNEL_ID = "grayscale_toggle_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static void showPersistentNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "TV Control Hub",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("TV Control Hub quick controls");
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Use FLAG_IMMUTABLE for API 31+
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setContentTitle("TV Control Hub")
                .setContentText("Centro de control rápido para Android TV")
                .setSmallIcon(android.R.drawable.ic_menu_manage) // Using system icon
                .setOngoing(true) // Persistent
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
