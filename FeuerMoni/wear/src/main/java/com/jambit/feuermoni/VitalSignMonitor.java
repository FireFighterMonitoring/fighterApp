package com.jambit.feuermoni;

import android.app.NotificationManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.jambit.feuermoni.common.DataMapKeys;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by tschroep on 18.02.16.
 */
public class VitalSignMonitor {

    private static final String TAG = VitalSignMonitor.class.getSimpleName();

    private final Context context;

    private SensorManager sensorManager;

    private Sensor heartRateSensor;
    private Sensor stepCounterSensor;
    private SensorEventListener heartRateListener;
    private SensorEventListener stepCounterListener;
    private float heartrate;
    private float steps;

    private static GoogleApiClient googleApiClient;

    /** Access to the googleApiClient is synchronized through this executor. */
    private ExecutorService executorService;

    public VitalSignMonitor(Context context) {
        this.context = context;

        sensorManager = ((SensorManager)context.getSystemService(Context.SENSOR_SERVICE));
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if(heartRateSensor == null) {
            Log.d(TAG, "heart rate sensor is null");
        } else {
            Log.d(TAG, " HRM minDelay: " + heartRateSensor.getMinDelay() + " max: " + heartRateSensor.getMaxDelay());
            Log.d(TAG, " HRM reportingMode: " + heartRateSensor.getReportingMode());
        }

        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if(stepCounterSensor == null) {
            Log.d(TAG, "step counter sensor is null");
        }

        heartRateListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                heartrate = event.values[0];
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        sendUpdate();
                    }
                });
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                Log.d(TAG, "accuracy changed: " + accuracy);
            }
        };

        stepCounterListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                steps = event.values[0];
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        sendUpdate();
                    }
                });
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                Log.d(TAG, "step accuracy changed: " + accuracy);
            }
        };

        this.executorService = Executors.newCachedThreadPool();
    }

    public void startMonitoring() {
        Log.d(TAG, "startMonitoring()");

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                connectApiClient();

                sensorManager.registerListener(heartRateListener, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
                sensorManager.registerListener(stepCounterListener, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        });
    }

    public void stopMonitoring() {
        Log.d(TAG, "stopMonitoring()");

        if (googleApiClient == null) {
            Log.d(TAG, "Not connected...");
            return;
        }

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                sensorManager.unregisterListener(heartRateListener);
                sensorManager.unregisterListener(stepCounterListener);

                disconnectApiClient();
            }
        });
    }


    private void connectApiClient() {
        googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Log.d(TAG, "ConnectionCallback onConnected");
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.d(TAG, "ConnectionCallback onConnectionSuspended");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.d(TAG, "ConnectionCallback onConnectionFailed");
                    }
                })
                .build();
        googleApiClient.connect();
    }

    private void disconnectApiClient() {
        googleApiClient.disconnect();
        googleApiClient = null;
    }

    private void sendUpdate() {
//        Log.v(TAG, "In sendUpdate() - heart rate: " + heartrate + " steps: " + steps);

        if (googleApiClient == null || !googleApiClient.isConnected()) {
//            Log.d(TAG, "API client has been disconnected in the meantime...");
            return;
        }

        final DataMap resultDataMap = new DataMap();
        resultDataMap.putFloat(DataMapKeys.HEARTRATE_KEY, heartrate);
        resultDataMap.putFloat(DataMapKeys.STEPCOUNT_KEY, steps);

        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient);

        NodeApi.GetConnectedNodesResult nodesResult = nodes.await();
        List<Node> nodeList = nodesResult.getNodes();

        for (Node node : nodeList) {
            Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), "feuermoni", resultDataMap.toByteArray()).await();
        }
    }
}
