package com.jambit.feuermoni;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by tschroep on 16.02.16.
 */
public class MyWearableListenerService extends WearableListenerService {
    private static GoogleApiClient googleApiClient;
    private String LOG_TAG = "MyWearableListenerService";
    private static final long CONNECTION_TIME_OUT_MS = 100;
    private ExecutorService executorService;

    private static final String COUNT_KEY = "de.schroepf.key.count";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equalsIgnoreCase("accumulator")) {
            // This would start a new activity every time a message is received:
//            Intent intent = new Intent(this, MainActivity.class);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(intent);
            DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());
            int number = dataMap.getInt(COUNT_KEY);
            int resultNumber = number + 1;

            DataMap resultDataMap = new DataMap();
            resultDataMap.putInt(COUNT_KEY, resultNumber);
            Log.d(LOG_TAG, "Will respond: " + resultNumber);
            reply(messageEvent.getPath(), messageEvent.getSourceNodeId(), resultDataMap.toByteArray());
        } else {
            super.onMessageReceived(messageEvent);
        }
    }

    private void reply(final String path, final String nodeId, final byte[] bytes) {
        googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Log.d(LOG_TAG, "ConnectionCallback onConnected");
                        doReply(path, nodeId, bytes);
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.d(LOG_TAG, "ConnectionCallback onConnectionSuspended");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.d(LOG_TAG, "ConnectionCallback onConnectionFailed");
                    }
                })
                .build();
        googleApiClient.connect();

    }

    private void doReply(final String path, final String nodeId, final byte[] bytes) {
        Log.v(LOG_TAG, "In reply()");
        Log.v(LOG_TAG, "Path: " + path);

        if (googleApiClient != null && !(googleApiClient.isConnected() || googleApiClient.isConnecting())) {
            googleApiClient.blockingConnect(CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);
        }

        this.executorService = Executors.newCachedThreadPool();
        this.executorService.execute(new Runnable() {
            @Override
            public void run() {
                Wearable.MessageApi.sendMessage(googleApiClient, nodeId, path, bytes).await();
            }
        });

    }
}
