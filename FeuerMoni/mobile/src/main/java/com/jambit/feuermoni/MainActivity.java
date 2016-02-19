package com.jambit.feuermoni;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jambit.feuermoni.ble.HeartrateBluetoothDevice;
import com.jambit.feuermoni.ble.MonitoringService;
import com.jambit.feuermoni.model.MonitoringStatus;
import com.jambit.feuermoni.model.VitalSigns;
import com.jambit.feuermoni.util.BackgroundThread;
import com.jambit.feuermoni.util.PermissionHelper;

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
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    /** The tag used for logging. */
    private static final String TAG = MainActivity.class.getSimpleName();

    /** Media Type used for POST requests */
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String BASE_URL = "http://192.168.232.112:8080/api/v1";
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

    private TextView heartRateTextView;
    private TextView stepsTextView;
    private Button searchBluetoothDevicesButton;
    private ListView devicesListView;
    private ArrayAdapter<HeartrateBluetoothDevice> devicesArrayAdapter;

    private MonitoringStatus monitoringStatus;

    private ScheduledExecutorService scheduler;
    private WearableConnection wearableConnection;

    private MonitoringService monitoringService;
    private boolean isBoundToServie = false;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "onServiceConnected()");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MonitoringService.MonitoringServiceBinder binder = (MonitoringService.MonitoringServiceBinder) service;
            monitoringService = binder.getService();
            isBoundToServie = true;

            monitoringService.heartrateObservable
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<Integer>() {
                        @Override
                        public void call(Integer integer) {
                            Log.d(TAG, "Observed new heart rate: " + integer);

                            if (monitoringStatus != null) {
                                monitoringStatus.updateHeartRate(integer);
                            }

                            updateHeartrate(integer);
                        }
                    });

            monitoringService.heartrateSensorStatusObservable
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<MonitoringService.SensorStatus>() {
                        @Override
                        public void call(MonitoringService.SensorStatus sensorStatus) {
                            if (sensorStatus == MonitoringService.SensorStatus.DISCONNECTED) {
                                if (monitoringStatus != null) {
                                    monitoringStatus.setVitalSigns(null);
                                }
                            }
                        }
                    });
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "onServiceDisconnected()");
            isBoundToServie = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wearableConnection = new WearableConnection(getApplicationContext());
        wearableConnection.setListener(new WearableConnection.Listener() {
            @Override
            public void onConnectionEstablished() {
                Log.d(TAG, "onConnectionEstablished()");

                loginButton.post(new Runnable() {
                    @Override
                    public void run() {
                        startMonitoringButton.setText(R.string.stop_monitoring_watch);
                    }
                });
            }

            @Override
            public void onConnectionLost() {
                Log.d(TAG, "onConnectionLost()");

                if (monitoringStatus != null) {
                    monitoringStatus.setVitalSigns(null);
                }
            }

            @Override
            public void onConnectionFailed() {
                Log.d(TAG, "onConnectionFailed()");

                if (monitoringStatus != null) {
                    monitoringStatus.setVitalSigns(null);
                }
            }

            @Override
            public void onVitalSignsReceived(final VitalSigns vitalSigns) {
                Log.d(TAG, "onVitalSignsReceived()");

                updateHeartrate(vitalSigns.heartRate);

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

                monitoringStatus.updateHeartRate(vitalSigns.heartRate);
                monitoringStatus.updateSteps(vitalSigns.stepCount);
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
                            startMonitoringButton.setText(R.string.start_monitoring_watch);
                        }
                    });
                } else {
                    if (monitoringStatus == null) {
                        Log.e(TAG, "ERROR: Are you logged in?");
                        return;
                    }

                    if (!PermissionHelper.hasBodySensorsPermission(MainActivity.this)) {
                        Log.e(TAG, "Cannot access body sensors!");
                        PermissionHelper.requestBodySensorsPermission(MainActivity.this);
                        monitoringStatus.setVitalSigns(null);
                        return;
                    }

                    wearableConnection.connect();
                }
            }
        });

        ffidTextView = (EditText) findViewById(R.id.ffid_textview);
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

        searchBluetoothDevicesButton = (Button) findViewById(R.id.send_message_button);
        searchBluetoothDevicesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isScanning(true);

                devicesArrayAdapter.clear();
                backgroundThread.post(new Runnable() {
                    @Override
                    public void run() {
                        if (PermissionHelper.requestLocationPermission(MainActivity.this)) {
                            if (!PermissionHelper.isBluetoothAvailable(getApplicationContext())) {
                                PermissionHelper.requestBluetoothPermission(MainActivity.this);
                                searchBluetoothDevicesButton.setEnabled(true);
                                searchBluetoothDevicesButton.setText(R.string.search_bluetooth_devices);
                                return;
                            }

                            if (!isBoundToServie) {
                                Toast.makeText(MainActivity.this, "Monitoring service is not bound!", Toast.LENGTH_LONG);
                                isScanning(false);
                                return;
                            }

                            monitoringService.startScanningForHeartrateDevices()
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(new Subscriber<BluetoothDevice>() {
                                        @Override
                                        public void onCompleted() {
                                            Log.d(TAG, "onCompleted() - BLE SCAN COMPLETE!");
                                            isScanning(false);
                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            Log.e(TAG, "Error observing heratrate devices! (" + e.getMessage() + ")");
                                            isScanning(false);
                                        }

                                        @Override
                                        public void onNext(BluetoothDevice bluetoothDevice) {
                                            devicesArrayAdapter.add(new HeartrateBluetoothDevice(bluetoothDevice, MainActivity.this));
                                        }
                                    });
                        }
                    }
                });
            }
        });

        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        devicesListView = (ListView) findViewById(R.id.bluetooth_devices_list);
        devicesListView.setAdapter(devicesArrayAdapter);
        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isBoundToServie) {
                    Toast.makeText(MainActivity.this, "Monitoring service is not bound!", Toast.LENGTH_LONG);
                    return;
                }

                HeartrateBluetoothDevice heartrateBluetoothDevice = devicesArrayAdapter.getItem(position);
                Log.d(TAG, "Item selected: " + heartrateBluetoothDevice + " (position: " + position + ")");
                monitoringService.observeHeartrate(heartrateBluetoothDevice);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.d(TAG, "onStart()");

        Intent intent = new Intent(MainActivity.this, MonitoringService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(TAG, "onStop()");

        if (isBoundToServie) {
            unbindService(serviceConnection);
            isBoundToServie = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopScheduler();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PermissionHelper.REQUEST_BODY_SENSORS_PERMISSION: {
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

    private void updateHeartrate(final int heartRate) {
        heartRateTextView.post(new Runnable() {
            @Override
            public void run() {
                heartRateTextView.setText(String.format(getString(R.string.rate), heartRate));
            }
        });
    }

    private void isScanning(boolean isScanning) {
        searchBluetoothDevicesButton.setEnabled(!isScanning);
        searchBluetoothDevicesButton.setText(isScanning ? R.string.searching : R.string.search_bluetooth_devices);
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
                        Log.e(TAG, "REQUEST FAILED! (CODE: " + response.code() + " - " + response.body().string());
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

        final MonitoringStatus result = new MonitoringStatus(ffIdText);
        result.status = MonitoringStatus.Status.CONNECTED;
        postStatus(result);
        result.status = MonitoringStatus.Status.NO_DATA;

        ffidTextView.setEnabled(false);
        loginButton.setText(R.string.logout);

        startMonitoringButton.setVisibility(View.VISIBLE);

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                postStatus(result);
            }
        }, 0, 5, TimeUnit.SECONDS);

        return result;
    }

    private void logout() {
        wearableConnection.disconnect();
        stopScheduler();

        if (monitoringStatus == null) {
            return;
        }

        monitoringStatus.setVitalSigns(null);
        monitoringStatus.status = MonitoringStatus.Status.DISCONNECTED;
        postStatus(monitoringStatus);

        loginButton.post(new Runnable() {
            @Override
            public void run() {
                ffidTextView.setEnabled(false);
                loginButton.setText(R.string.login);
                loginButton.setEnabled(true);

                startMonitoringButton.setVisibility(View.GONE);
            }
        });
    }
}
