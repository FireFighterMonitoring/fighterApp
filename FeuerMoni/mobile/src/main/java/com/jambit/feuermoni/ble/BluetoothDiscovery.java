package com.jambit.feuermoni.ble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
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

import rx.Observable;
import rx.exceptions.OnCompletedFailedException;
import rx.subjects.PublishSubject;

/**
 * Created by tschroep on 18.02.16.
 */
public class BluetoothDiscovery {
    public static final String TAG = BluetoothDiscovery.class.getSimpleName();

    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothLeScanner;

    private BackgroundThread backgroundThread = new BackgroundThread();

    public static String HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb";
    private UUID HEART_RATE_SERVICE_UUID = UUID.fromString(HEART_RATE_SERVICE);

    private Set<BluetoothDevice> compatibleScanRecords;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 3000;

    public BluetoothDiscovery(Context context) {
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    public Observable<BluetoothDevice> scan() {
        final PublishSubject<BluetoothDevice> heartrateDevicesSubject = PublishSubject.create();
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
                            heartrateDevicesSubject.onNext(device);
                        }

                        Log.d(TAG, "Set has: " + compatibleScanRecords.size() + " item(s)");
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                heartrateDevicesSubject.onError(new Throwable("BLE scan failed!"));
            }
        };

        compatibleScanRecords = new HashSet<>();

        List<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(HEART_RATE_SERVICE_UUID)).build());

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();

        // Stops scanning after a pre-defined scan period.
        backgroundThread.getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothLeScanner.stopScan(scanCallback);
                heartrateDevicesSubject.onCompleted();
            }
        }, SCAN_PERIOD);

        bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback);

        return heartrateDevicesSubject;
    }
}
