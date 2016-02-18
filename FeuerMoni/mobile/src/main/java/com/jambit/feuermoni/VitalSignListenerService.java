package com.jambit.feuermoni;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by tschroep on 18.02.16.
 */
public class VitalSignListenerService extends WearableListenerService {

    private static final String TAG = VitalSignListenerService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy()");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String messagePath = messageEvent.getPath();
        Log.d(TAG, "onMessageReceived() - messagePath: " + messagePath);
    }
}
