package com.jambit.feuermoni;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends WearableActivity {

    /** Key used to identify the heartrate value in a DataMap */
    private static final String HEARTRATE_KEY = "com.jambit.feuermoni.key.heartrate";
    private static final String STEPCOUNT_KEY = "com.jambit.feuermoni.key.stepcount";

    private static final long CONNECTION_TIME_OUT_MS = 100;

    private static final String TAG = MainActivity.class.getSimpleName();

    private TextView mTextView;

    private SensorManager sensorManager;

    private Sensor heartRateSensor;
    private Sensor stepCounterSensor;
    private SensorEventListener heartRateListener;
    private SensorEventListener stepCounterListener;
    private float heartrate;
    private float steps;

    private static GoogleApiClient googleApiClient;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        setAmbientEnabled();

        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });

        sensorManager = ((SensorManager)getSystemService(SENSOR_SERVICE));
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if(heartRateSensor == null) {
            Log.d(TAG, "heart rate sensor is null");
        }

        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if(stepCounterSensor == null) {
            Log.d(TAG, "step counter sensor is null");
        }

        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor aSensor : sensors) {
            Log.d(TAG, aSensor.getName() + ": " + aSensor.getStringType());
        }

        heartRateListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                heartrate = event.values[0];
                if (mTextView != null) {
                    mTextView.setText("Rate: " + event.values[0]);
                }
                Log.d(TAG, "sensor event: " + event.accuracy + " = " + event.values[0]);

                sendUpdate();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                Log.d(TAG, "accuracy changed: " + accuracy);
            }
        };

        stepCounterListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                Log.d(TAG, "Steps: " + event.accuracy + " = " + event.values[0]);
                steps = event.values[0];

                sendUpdate();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                Log.d(TAG, "step accuracy changed: " + accuracy);
            }
        };

        this.executorService = Executors.newCachedThreadPool();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onCreate()");
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.d(TAG, "onStart() -> Register listener!");

        sensorManager.registerListener(heartRateListener, this.heartRateSensor, 3);
        sensorManager.registerListener(stepCounterListener, this.stepCounterSensor, 3);

        connectApiClient();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(TAG, "onStop() -> unregisterListener()");
        sensorManager.unregisterListener(heartRateListener);
        sensorManager.unregisterListener(stepCounterListener);

        disconnectApiClient();
    }

    private void connectApiClient() {
        googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
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
        Log.v(TAG, "In doReply() - heart rate: " + heartrate + " steps: " + steps);

        final DataMap resultDataMap = new DataMap();
        resultDataMap.putFloat(HEARTRATE_KEY, heartrate);
        resultDataMap.putFloat(STEPCOUNT_KEY, steps);

        if (googleApiClient != null && !(googleApiClient.isConnected() || googleApiClient.isConnecting())) {
            googleApiClient.blockingConnect(CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);
        }

        this.executorService.execute(new Runnable() {
            @Override
            public void run() {
                PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient);

                NodeApi.GetConnectedNodesResult nodesResult = nodes.await();
                List<Node> nodeList = nodesResult.getNodes();

                for (Node node : nodeList) {
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), "feuermoni", resultDataMap.toByteArray()).await();
                }
            }
        });
    }
}
