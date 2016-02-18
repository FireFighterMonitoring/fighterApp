package com.jambit.feuermoni;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jambit.feuermoni.model.MonitoringStatus;
import com.jambit.feuermoni.model.VitalSigns;
import com.jambit.feuermoni.util.BackgroundThread;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    /** The tag used for logging. */
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int MY_PERMISSIONS_REQUEST_BODY_SENSORS = 42;

    /** Media Type used for POST requests */
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String BASE_URL = "http://192.168.234.222:8080/api/v1";
//    private static final String BASE_URL = "http://192.168.178.144:3000";
    private static final String REST_PATH_DATA = "/data";

    /** HTTP client */
    private final OkHttpClient client = new OkHttpClient();

    private BackgroundThread backgroundThread = new BackgroundThread();

    /** TextView to change the ffId */
    private EditText ffidTextView;

    /** Button to trigger connection to a wearable device. */
    private Button startMonitoringButton;

    /** Button to login to the FeuerMoni backend service. */
    private Button loginButton;

    private View vitalSignsLayout;
    private TextView heartRateTextView;
    private TextView stepsTextView;
    private Button sendMessageButton;

    private MonitoringStatus monitoringStatus;

    private ScheduledExecutorService scheduler;
    private WearableConnection wearableConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wearableConnection = new WearableConnection(getApplicationContext());
        wearableConnection.setListener(new WearableConnection.Listener() {
            @Override
            public void onConnectionEstablished() {
                Log.d(TAG, "onConnectionEstablished()");
                monitoringStatus.status = MonitoringStatus.Status.OK;

                loginButton.post(new Runnable() {
                    @Override
                    public void run() {
                        vitalSignsLayout.setVisibility(View.VISIBLE);
                        startMonitoringButton.setText(R.string.stop_monitoring);
                    }
                });
            }

            @Override
            public void onConnectionLost() {
                Log.d(TAG, "onConnectionLost()");
                monitoringStatus.status = MonitoringStatus.Status.NO_DATA;
                monitoringStatus.vitalSigns = null;
            }

            @Override
            public void onConnectionFailed() {
                Log.d(TAG, "onConnectionFailed()");
                monitoringStatus.status = MonitoringStatus.Status.NO_DATA;
                monitoringStatus.vitalSigns = null;
            }

            @Override
            public void onVitalSignsReceived(final VitalSigns vitalSigns) {
                Log.d(TAG, "onVitalSignsReceived()");

                heartRateTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        heartRateTextView.setText(String.format(getString(R.string.rate), (int) vitalSigns.heartRate));
                    }
                });

                stepsTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        stepsTextView.setText(String.format(getString(R.string.steps), (int) vitalSigns.stepCount));
                    }
                });

                if (monitoringStatus == null) {
                    Log.e(TAG, "Data was received from wearable but monitoring status is null - this shouldn't happen!");
                    return;
                }

                monitoringStatus.vitalSigns = vitalSigns;
            }
        });

        startMonitoringButton = (Button) findViewById(R.id.start_monitoring_button);
        startMonitoringButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Connect Watch button pressed!");
                if (wearableConnection.isConnected()) {
                    wearableConnection.disconnect();
                    loginButton.post(new Runnable() {
                        @Override
                        public void run() {
                            vitalSignsLayout.setVisibility(View.GONE);
                            startMonitoringButton.setText(R.string.start_monitoring);
                        }
                    });
                } else {
                    if (monitoringStatus == null) {
                        Log.e(TAG, "ERROR: Are you logged in?");
                        return;
                    }

                    if (!hasBodySensorsPermission(MainActivity.this)) {
                        Log.e(TAG, "Cannot access body sensors!");
                        requestBodySensorsPermission(MainActivity.this);
                        monitoringStatus.status = MonitoringStatus.Status.NO_DATA;
                        monitoringStatus.vitalSigns = null;
                        return;
                    }

                    wearableConnection.connect();
                }
            }
        });

        ffidTextView = (EditText) findViewById(R.id.ffid_textview);
        vitalSignsLayout = findViewById(R.id.vital_signs_layout);
        heartRateTextView = (TextView) findViewById(R.id.heartrate_textview);
        stepsTextView = (TextView) findViewById(R.id.steps_textview);
        loginButton = (Button) findViewById(R.id.login_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (monitoringStatus == null) {
                    monitoringStatus = login();
                } else {
                    logout();
                    monitoringStatus = null;
                }
            }
        });

        SensorManager sensorManager = ((SensorManager)getSystemService(SENSOR_SERVICE));
        Sensor heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if(heartRateSensor == null) {
            Log.d(TAG, "heart rate sensor is null");
        }

        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor aSensor : sensors) {
            Log.d(TAG, aSensor.getName() + ": " + aSensor.getStringType());
        }

        sendMessageButton = (Button) findViewById(R.id.send_message_button);
        sendMessageButton.setOnClickListener(new View.OnClickListener() {
            BackgroundThread thread = new BackgroundThread();
            @Override
            public void onClick(View v) {
                thread.post(new Runnable() {
                    @Override
                    public void run() {
                        wearableConnection.broadcastMessagePath("TOBITEST");
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopScheduler();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_BODY_SENSORS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "BODY_SENSORS permission was granted!");
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Without BODY_SENSORS permission this app won't work!", Toast.LENGTH_LONG).show();
                }
                return;
            }

            // other 'case' lines to check for other permissions this app might request
        }
    }

    /**
     * Posts JSON data to the FireMoni backend service
     */
    private void postStatus(MonitoringStatus status) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        final String postBody = gson.toJson(status);

        final Request request = new Request.Builder()
                .url(BASE_URL + REST_PATH_DATA)
                .post(RequestBody.create(MEDIA_TYPE_JSON, postBody))
                .build();
        backgroundThread.post(new Runnable() {
            @Override
            public void run() {
                Response response = null;

                try {
                    Log.d(TAG, "POSTing JSON: " + postBody + " to Host: " + BASE_URL);
                    response = client.newCall(request).execute();

                    if (!response.isSuccessful()) {
                        Log.e(TAG, "REQUEST FAILED!");
                    } else {
                        try {
                            Log.d(TAG, response.body().string());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean hasBodySensorsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBodySensorsPermission(Activity activity) {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BODY_SENSORS)) {
                Toast.makeText(activity, "I need permission to access body sensors!", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, MY_PERMISSIONS_REQUEST_BODY_SENSORS);
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, MY_PERMISSIONS_REQUEST_BODY_SENSORS);
            }
        }
    }

    private void stopScheduler() {
        if (scheduler != null) {
            Log.d(TAG, "stopping scheduler");
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private MonitoringStatus login() {
        stopScheduler();

        String ffIdText = ffidTextView.getText().toString();

        if (ffIdText == null || ffIdText.isEmpty()) {
            Toast.makeText(MainActivity.this, "Invalid ffId", Toast.LENGTH_LONG).show();
            return null;
        }

        MonitoringStatus result = new MonitoringStatus(ffIdText);

        ffidTextView.setEnabled(false);
        loginButton.setText(R.string.logout);

        vitalSignsLayout.setVisibility(View.VISIBLE);
        startMonitoringButton.setVisibility(View.VISIBLE);

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                postStatus(monitoringStatus);
            }
        }, 0, 5, TimeUnit.SECONDS);

        return result;
    }

    private void logout() {
        wearableConnection.disconnect();
        stopScheduler();

        monitoringStatus.status = MonitoringStatus.Status.DISCONNECTED;
        postStatus(monitoringStatus);

        loginButton.post(new Runnable() {
            @Override
            public void run() {
                ffidTextView.setEnabled(false);
                loginButton.setText(R.string.login);
                loginButton.setEnabled(true);

                vitalSignsLayout.setVisibility(View.GONE);
                startMonitoringButton.setVisibility(View.GONE);
            }
        });
    }
}
