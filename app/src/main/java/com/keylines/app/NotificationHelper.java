package com.keylines.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.os.Build;

/**
 * Created by chrismack on 2017-09-26.
 */

public class NotificationHelper extends ContextWrapper
{
    public static final String KEYLINES_CHANNEL = "keylines_channel";

    private NotificationManager manager;

    public NotificationHelper(Context base) {
        super(base);
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(KEYLINES_CHANNEL,
                    "Keylines display controller", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setLightColor(Color.GREEN);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            getManager().createNotificationChannel(channel);
        } else {
            //  No action required for pre-Oreo devices
        }
    }

    public Notification.Builder getNotification(String title, String content, String hideShowActionText, PendingIntent hideShowIntent, PendingIntent killIntent) {
        Notification.Action hideShowAction = new Notification.Action.Builder(null, hideShowActionText, hideShowIntent).build();
        Notification.Action killAction = new Notification.Action.Builder(null, getString(R.string.notification_turn_off), killIntent).build();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(getApplicationContext(), KEYLINES_CHANNEL)
                    .setContentTitle(title)
                    .setContentText(content)
                    .addAction(hideShowAction)
                    .addAction(killAction)
                    .setSmallIcon(R.drawable.ic_key_24)
                    .setAutoCancel(true);
        } else {
            return new Notification.Builder(getApplicationContext())
                    .setContentTitle(title)
                    .setContentText(content)
                    .addAction(hideShowAction)
                    .addAction(killAction)
                    .setSmallIcon(R.drawable.ic_key_24)
                    .setAutoCancel(true);
        }
    }

    private NotificationManager getManager() {
        if (manager == null) {
            manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return manager;
    }
}


