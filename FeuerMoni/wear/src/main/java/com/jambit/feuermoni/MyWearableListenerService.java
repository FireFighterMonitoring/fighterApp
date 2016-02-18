package com.jambit.feuermoni;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.jambit.feuermoni.common.DataMapKeys;

/**
 * Created by tschroep on 16.02.16.
 */
public class MyWearableListenerService extends WearableListenerService {
    private String TAG = MyWearableListenerService.class.getSimpleName();

    private static final int NOTIFICATION_ID = 100;

    private VitalSignMonitor vitalSignMonitor;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        vitalSignMonitor = new VitalSignMonitor(getApplicationContext());
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String messagePath = messageEvent.getPath();
        Log.d(TAG, "onMessageReceived() - messagePath: " + messagePath);
        if (messagePath.equalsIgnoreCase(DataMapKeys.START_MONITORING_COMMAND)) {
            showNotification();
            vitalSignMonitor.startMonitoring();
        } else if (messagePath.equalsIgnoreCase(DataMapKeys.STOP_MONITORING_COMMAND)) {
            vitalSignMonitor.stopMonitoring();
            hideNotification();
        } else {
            super.onMessageReceived(messageEvent);
        }
    }

    private void showNotification() {
        // Build the intent to display our custom notification
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent notificationPendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Create the ongoing notification
        Notification.Builder notificationBuilder =
                new Notification.Builder(this)
                        .setSmallIcon(R.mipmap.ic_whatshot_black_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.notification_running))
                        .setOngoing(true)
                        .extend(new Notification.WearableExtender()
                                        .setDisplayIntent(notificationPendingIntent)
                        );

        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notificationBuilder.build());

    }

    private void hideNotification() {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
    }
}
