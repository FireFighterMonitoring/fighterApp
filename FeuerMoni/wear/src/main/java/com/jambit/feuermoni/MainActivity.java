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

import java.util.List;

public class MainActivity extends WearableActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private TextView mTextView;

    private SensorManager sensorManager;

    private Sensor heartRateSensor;
    private Sensor stepCounterSensor;

    private SensorEventListener heartRateListener;
    private SensorEventListener stepCounterListener;

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
                if (mTextView != null) {
                    mTextView.setText("Rate: " + event.values[0]);
                }
                Log.d(TAG, "sensor event: " + event.accuracy + " = " + event.values[0]);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                Log.d(TAG, "accuracy changed: " + accuracy);

            }
        };

        stepCounterListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                Log.d(TAG, "step sensor event: " + event.accuracy + " = " + event.values[0]);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                Log.d(TAG, "step accuracy changed: " + accuracy);
            }
        };
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
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(TAG, "onStop() -> unregisterListener()");
        sensorManager.unregisterListener(heartRateListener);
        sensorManager.unregisterListener(stepCounterListener);
    }
}
