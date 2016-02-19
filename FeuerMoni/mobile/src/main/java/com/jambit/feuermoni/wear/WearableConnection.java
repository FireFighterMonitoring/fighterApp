package com.jambit.feuermoni.wear;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.jambit.feuermoni.common.DataMapKeys;
import com.jambit.feuermoni.backend.model.VitalSigns;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by tschroep on 17.02.16.
 */
public class WearableConnection implements MessageApi.MessageListener {

    public interface Listener {
        void onConnectionEstablished();

        void onConnectionLost();

        void onConnectionFailed();

        void onVitalSignsReceived(VitalSigns vitalSigns);
    }

    private static final String TAG = WearableConnection.class.getSimpleName();

    /** Google API client, used to connect to Wearable devices... */
    private GoogleApiClient apiClient;

    private ExecutorService executorService;

    private Listener listener;

    private Context context;

    public WearableConnection(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
    }

    public boolean isConnected() {
        return apiClient != null;
    }

    public void connect() {
        this.executorService.execute(new Runnable() {
            @Override
            public void run() {
                openWearableConnection();
            }
        });
    }

    public void disconnect() {
        if (!isConnected()) {
            return;
        }

        this.executorService.execute(new Runnable() {
            @Override
            public void run() {
                stopMonitoring();
                closeWearableConnection();
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());
        final float heartrate = dataMap.getFloat(DataMapKeys.HEARTRATE_KEY);
        final float steps = dataMap.getFloat(DataMapKeys.STEPCOUNT_KEY);

        Log.v(TAG, "A message has been received! heartrate: " + heartrate + " steps: " + steps);

        if (listener != null) {
            listener.onVitalSignsReceived(new VitalSigns((int) heartrate, (int) steps));
        }
    }

    /**
     * Opens the connection to a wearable device.
     */
    private void openWearableConnection() {
        Log.v(TAG, "openWearableConnection()");
        apiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    public void onConnected(Bundle bundle) {
                        Log.d(TAG, "ConnectionCallback onConnected()...");

                        if (listener != null) {
                            listener.onConnectionEstablished();
                        }

                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                startMonitoring();
                            }
                        });
                    }

                    public void onConnectionSuspended(int i) {
                        Log.d(TAG, "ConnectionCallback onConnectionSuspended()");

                        if (listener != null) {
                            listener.onConnectionLost();
                        }
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.d(TAG, "ConnectionCallback onConnectionFailed()");

                        if (listener != null) {
                            listener.onConnectionFailed();
                        }
                    }
                }).build();
        apiClient.connect();

        Wearable.MessageApi.addListener(apiClient, this);
    }

    private void closeWearableConnection() {
        Log.v(TAG, "closeWearableConnection()");
        apiClient.disconnect();
        apiClient = null;

        if (listener != null) {
            listener.onConnectionLost();
        }
    }

    private void startMonitoring() {
        Log.v(TAG, "startMonitoring()");
        broadcastMessagePath(DataMapKeys.START_MONITORING_COMMAND);
    }

    private void stopMonitoring() {
        Log.v(TAG, "stopMonitoring()");
        broadcastMessagePath(DataMapKeys.STOP_MONITORING_COMMAND);
    }

    public void broadcastMessagePath(final String messagePath) {
        Log.d(TAG, "broadcastMessagePath(" + messagePath + ")");

        if (apiClient != null && !(apiClient.isConnected() || apiClient.isConnecting())) {
            apiClient.blockingConnect(DataMapKeys.CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);
        }

        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(apiClient);

        NodeApi.GetConnectedNodesResult nodesResult = nodes.await();
        List<Node> nodeList = nodesResult.getNodes();


        for (Node node : nodeList) {
            Log.v(TAG, "Sending: " + messagePath + " to: " + node.getId());
            Wearable.MessageApi.sendMessage(apiClient, node.getId(), messagePath, null).await();
        }
    }
}
