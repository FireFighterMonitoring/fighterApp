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

import com.jambit.feuermoni.backend.BackendConnection;
import com.jambit.feuermoni.backend.model.VitalSigns;
import com.jambit.feuermoni.ble.HeartrateBluetoothDevice;
import com.jambit.feuermoni.ble.MonitoringService;
import com.jambit.feuermoni.util.BackgroundThread;
import com.jambit.feuermoni.util.PermissionHelper;
import com.jambit.feuermoni.wear.WearableConnection;

import java.util.List;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    /** The tag used for logging. */
    private static final String TAG = MainActivity.class.getSimpleName();

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

                            if (BackendConnection.getInstance().isLoggedIn()) {
                                BackendConnection.getInstance().getMonitoringStatus().updateHeartRate(integer);
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
                                if (BackendConnection.getInstance().isLoggedIn()) {
                                    BackendConnection.getInstance().getMonitoringStatus().setVitalSigns(null);
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

                if (BackendConnection.getInstance().isLoggedIn()) {
                    BackendConnection.getInstance().getMonitoringStatus().setVitalSigns(null);
                }
            }

            @Override
            public void onConnectionFailed() {
                Log.d(TAG, "onConnectionFailed()");

                if (BackendConnection.getInstance().isLoggedIn()) {
                    BackendConnection.getInstance().getMonitoringStatus().setVitalSigns(null);
                }
            }

            @Override
            public void onVitalSignsReceived(final VitalSigns vitalSigns) {
                Log.d(TAG, "onVitalSignsReceived()");

                updateHeartrate(vitalSigns.heartRate);
                updateStepCount(vitalSigns.stepCount);

                if (!BackendConnection.getInstance().isLoggedIn()) {
                    Log.e(TAG, "Data was received from wearable but not logged in - this shouldn't happen!");
                    return;
                }

                BackendConnection.getInstance().getMonitoringStatus().updateHeartRate(vitalSigns.heartRate);
                BackendConnection.getInstance().getMonitoringStatus().updateSteps(vitalSigns.stepCount);
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
                    if (!BackendConnection.getInstance().isLoggedIn()) {
                        Log.e(TAG, "ERROR: Are you logged in?");
                        return;
                    }

                    if (!PermissionHelper.hasBodySensorsPermission(MainActivity.this)) {
                        Log.e(TAG, "Cannot access body sensors!");
                        PermissionHelper.requestBodySensorsPermission(MainActivity.this);
                        if (BackendConnection.getInstance().isLoggedIn()) {
                            BackendConnection.getInstance().getMonitoringStatus().setVitalSigns(null);
                        }

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
                if (BackendConnection.getInstance().isLoggedIn()) {
                    logout();
                } else {
                    login();
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
    protected void onResume() {
        super.onResume();
        updateLoginStatus(BackendConnection.getInstance().isLoggedIn());

        if (BackendConnection.getInstance().isLoggedIn()) {
            updateHeartrate(BackendConnection.getInstance().getMonitoringStatus().getVitalSigns().heartRate);
            updateHeartrate(BackendConnection.getInstance().getMonitoringStatus().getVitalSigns().stepCount);
        }
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
        }
    }

    private void isScanning(boolean isScanning) {
        searchBluetoothDevicesButton.setEnabled(!isScanning);
        searchBluetoothDevicesButton.setText(isScanning ? R.string.searching : R.string.search_bluetooth_devices);
    }

    private void login() {
        String ffIdText = ffidTextView.getText().toString();

        if (ffIdText == null || ffIdText.isEmpty()) {
            Toast.makeText(MainActivity.this, "Invalid ffId", Toast.LENGTH_LONG).show();
            return;
        }

        BackendConnection.getInstance().login(ffIdText);
        updateLoginStatus(true);
    }

    private void logout() {
        wearableConnection.disconnect();
        monitoringService.stopObservingHeartrate();
        BackendConnection.getInstance().logout();

        updateLoginStatus(false);
    }

    private void updateLoginStatus(final boolean isLoggedIn) {
        loginButton.post(new Runnable() {
            @Override
            public void run() {
                if (isLoggedIn) {
                    ffidTextView.setText(BackendConnection.getInstance().getMonitoringStatus().ffId);
                }
                ffidTextView.setEnabled(!isLoggedIn);

                loginButton.setText(isLoggedIn ? R.string.logout : R.string.login);
                startMonitoringButton.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void updateHeartrate(final int heartRate) {
        heartRateTextView.post(new Runnable() {
            @Override
            public void run() {
                heartRateTextView.setText(String.format(getString(R.string.rate), heartRate));
            }
        });
    }

    private void updateStepCount(final int stepCount) {
        stepsTextView.post(new Runnable() {
            @Override
            public void run() {
                stepsTextView.setText(String.format(getString(R.string.steps), stepCount));
            }
        });
    }
}
