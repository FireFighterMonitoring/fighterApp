package com.jambit.feuermoni.ble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.jambit.feuermoni.util.BackgroundThread;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Created by tschroep on 18.02.16.
 */
public class BLEDiscovery {
    public interface Listener {
        void onDeviceDiscovered(BluetoothDevice device);
    }

    public static final String TAG = BLEDiscovery.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_LOACTION_PERMISSION = 1;

    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothLeScanner;

    private Context context;

    private boolean scanning;
    private BackgroundThread backgroundThread = new BackgroundThread();

    public static String HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb";
    private UUID HEART_RATE_SERVICE_UUID = UUID.fromString(HEART_RATE_SERVICE);

    private Set<BluetoothDevice> compatibleScanRecords;

    private Listener listener;


    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 3000;

    public BLEDiscovery(Context context) {
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        this.context = context;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void requestBluetoothPermission(Activity activity) {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    public boolean requestLocationPermission(Activity activity) {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(activity, "I need location permission!", Toast.LENGTH_LONG).show();

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOACTION_PERMISSION);
            }

            return false;
        } else {
            return true;
        }
    }

    public void scan(final boolean enable) {
        final ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                Log.d(TAG, "onScanResult() : " + result.getDevice().getName());

                ScanRecord scanRecord = result.getScanRecord();
                List<ParcelUuid> uuids = scanRecord.getServiceUuids();

                if (uuids == null) {
                    Log.d(TAG, "UUIDS are null");
                    return;
                }

                for (ParcelUuid uuid : uuids) {
                    Log.d(TAG, " - service uuid: " + uuid.toString());

                    if (uuid.getUuid().equals(HEART_RATE_SERVICE_UUID)) {
                        BluetoothDevice device = result.getDevice();
                        Log.i(TAG, "Found a Heart Rate Service on device: " + device.getName());
                        if (compatibleScanRecords.add(device)) {
                            if (listener != null) {
                                listener.onDeviceDiscovered(device);
                            }
                        }

                        Log.d(TAG, "Set has: " + compatibleScanRecords.size() + " item(s)");
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
            }
        };

        if (enable) {
            compatibleScanRecords = new HashSet<>();

            // Stops scanning after a pre-defined scan period.
            backgroundThread.getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;
            bluetoothLeScanner.startScan(scanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
        }
    }
}
