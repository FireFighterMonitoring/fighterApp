package com.jambit.feuermoni.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.UUID;

/**
 * Created by tschroep on 18.02.16.
 */
public class BluetoothDeviceWrapper {
    private static final String TAG = BluetoothDeviceWrapper.class.getSimpleName();

    private final BluetoothDevice device;
    private final Context context;

    private BluetoothGatt deviceGatt;
    private BluetoothGattCharacteristic heartRateCharacteristic;
    private BluetoothGattCharacteristic bodySensorLocationCharacteristic;

    private UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private UUID HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private UUID BODY_SENSOR_LOCATION_UUID = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb");

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                deviceGatt = gatt;
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" + gatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
            }
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService service : gatt.getServices()) {
                    Log.d(TAG, "onServicesDiscovered() - Service: " + service.getUuid());

                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {

                        if (characteristic.getUuid().equals(HEART_RATE_MEASUREMENT_UUID)) {
                            Log.d(TAG, " -- detected HEART RATE MEASUREMENT characteristic!");
                            heartRateCharacteristic = characteristic;
                            enableGattNotifications(heartRateCharacteristic);
                        } else if (characteristic.getUuid().equals(BODY_SENSOR_LOCATION_UUID)) {
                            Log.d(TAG, " -- detected BODY SENSOR LOCATION characteristic!");
                            bodySensorLocationCharacteristic = characteristic;
                        } else {
                            Log.d(TAG, " -- UNKNOWN characteristic: " + characteristic.getUuid());
                        }
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onCharacteristicRead()");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            int flag = characteristic.getProperties();
            int format;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
        }
    };

    public BluetoothDeviceWrapper(BluetoothDevice device, Context context) {
        this.device = device;
        this.context = context;
    }

    public void connect() {
        device.connectGatt(context, false, gattCallback);
    }

    @Override
    public String toString() {
        return device.getName() + " [" + device.getAddress() + "]";
    }

    private void enableGattNotifications(BluetoothGattCharacteristic characteristic) {
        deviceGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        deviceGatt.writeDescriptor(descriptor);
    }
}
